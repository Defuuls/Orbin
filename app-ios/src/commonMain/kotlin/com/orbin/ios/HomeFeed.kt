package com.orbin.ios

import com.orbin.core.model.AppSettings
import com.orbin.core.model.CatalogRequest
import com.orbin.core.model.FeedSort
import com.orbin.core.model.FeedThreadLimit
import com.orbin.core.model.HomeFeedMode
import com.orbin.core.model.MediaType
import com.orbin.core.model.ProviderId
import com.orbin.core.model.comparator
import com.orbin.core.model.isPermanentlyFiltered
import com.orbin.domain.feed.BoardFeed
import com.orbin.domain.feed.SubscribedFeedLoader
import com.orbin.domain.repository.BoardPreferencesRepository
import com.orbin.provider.api.ImageBoardProvider
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.sync.withPermit
import kotlinx.coroutines.withContext

/** A source outage is distinct from an empty/filtered directory, including during partial loading. */
internal data class HomeFeedDirectory(
    val boards: List<SiteBoard> = emptyList(),
    val completed: Set<ProviderId> = emptySet(),
    val loading: Set<ProviderId> = emptySet(),
    val failures: Map<ProviderId, String> = emptyMap(),
)

data class HomeFeedContentPolicy(
    val hideNsfw: Boolean,
    val coverViolent: Boolean,
)

/** Explicit platform-state wiring keeps catalog scheduling and cache behavior independently testable. */
@Suppress("LongParameterList")
internal class HomeFeed(
    private val providers: List<ImageBoardProvider>,
    private val preferences: BoardPreferencesRepository,
    settings: Flow<AppSettings>,
    private val followed: StateFlow<Set<FollowedBoard>>,
    private val directory: StateFlow<HomeFeedDirectory>,
    private val source: StateFlow<ProviderId?>,
    private val firstRun: StateFlow<FirstRunState>,
    private val scope: CoroutineScope,
    private val catalogDispatcher: CoroutineDispatcher = Dispatchers.Default,
    private val isActive: () -> Boolean,
) {
    private val _mode = MutableStateFlow(HomeFeedMode.FOLLOWING)
    val mode: StateFlow<HomeFeedMode> = _mode.asStateFlow()
    private val _content = MutableStateFlow<Load<List<FeedThread>>>(Load.Loading)
    val content: StateFlow<Load<List<FeedThread>>> = _content.asStateFlow()
    private val _isLoadingMore = MutableStateFlow(false)
    val isLoadingMore: StateFlow<Boolean> = _isLoadingMore.asStateFlow()
    private val _canLoadMore = MutableStateFlow(false)
    val canLoadMore: StateFlow<Boolean> = _canLoadMore.asStateFlow()
    private val _completedDiscoveryCount = MutableStateFlow(0)
    val completedDiscoveryCount: StateFlow<Int> = _completedDiscoveryCount.asStateFlow()
    private val _contentPolicy = MutableStateFlow(HomeFeedContentPolicy(hideNsfw = true, coverViolent = true))
    val contentPolicy: StateFlow<HomeFeedContentPolicy> = _contentPolicy.asStateFlow()
    private val loader = SubscribedFeedLoader()
    private val providerById = providers.associateBy { it.metadata.id }
    private val cache = mutableMapOf<FollowedBoard, CachedHomeBoard>()
    private val epochs = mutableMapOf<ProviderId, Int>()
    private val publication = Mutex()
    private val catalogGate = Semaphore(SubscribedFeedLoader.MAX_CONCURRENT_BOARD_LOADS)
    private var limits: Map<FollowedBoard, FeedThreadLimit> = emptyMap()
    private var limitObservation: Job? = null
    private var request: HomeFeedRequest? = null
    private var work: Job? = null
    private var discoveryLimit = DISCOVERY_PAGE_SIZE
    private var selectedSource: ProviderId? = null
    private var catalogLoading = false
    private var actualSettings: AppSettings? = null

    init {
        scope.launch {
            settings.collect { current ->
                val previous = actualSettings
                actualSettings = current
                val policyChanged = previous != null && previous.contentPolicy() != current.contentPolicy()
                if (policyChanged) invalidate()
                if (isActive() && (previous == null || policyChanged)) load()
            }
        }
        scope.launch { followed.collect { if (isActive()) load() } }
        scope.launch {
            firstRun.map { it.initialized && !it.needed }.distinctUntilChanged().collect {
                if (it && isActive()) load()
            }
        }
        scope.launch {
            var previousBoards: List<SiteBoard>? = null
            directory.collect { index ->
                if (index.boards != previousBoards) {
                    previousBoards = index.boards
                    observeLimits(index.boards)
                }
                if (isActive()) load()
            }
        }
    }

    /** Retained for older callers; discovery is no longer a Home content source. */
    fun selectMode(
        @Suppress("UNUSED_PARAMETER") value: HomeFeedMode,
    ) = Unit

    fun loadMore() {
        if (_mode.value != HomeFeedMode.FOR_YOU || !_canLoadMore.value || catalogLoading) return
        discoveryLimit += DISCOVERY_PAGE_SIZE
        load()
    }

    /** A changed content/privacy preference cannot revive an older cached media presentation. */
    fun invalidate() {
        work?.cancel()
        request = null
        cache.clear()
        _content.value = Load.Loading
    }

    fun load(force: Boolean = false) {
        val setup = firstRun.value
        if (!setup.initialized || setup.needed && !setup.completing) {
            work?.cancel()
            request = null
            _content.value = Load.Ready(emptyList())
            catalogLoading = false
            _isLoadingMore.value = false
            _canLoadMore.value = false
            return
        }
        if (actualSettings == null) {
            _content.value = Load.Loading
            return
        }
        val next = selection(force) ?: return
        startRequest(next)
    }

    private fun startRequest(next: HomeFeedRequest) {
        if (next == request && (_content.value is Load.Ready || work?.isActive == true)) {
            val cached = available(next)
            if (cached.isNotEmpty()) _content.value = present(cached, next.boards.size)
            updateProgress()
            return
        }
        work?.cancel()
        request = next
        if (next.boards.isEmpty()) {
            _contentPolicy.value = next.policy
            _content.value = Load.Ready(emptyList())
            catalogLoading = false
            updateProgress()
            return
        }
        val cached = available(next)
        _content.value = if (cached.isEmpty()) Load.Loading else present(cached, next.boards.size)
        if (cached.isNotEmpty()) _contentPolicy.value = next.policy
        catalogLoading = true
        updateProgress()
        work =
            scope.launch {
                try {
                    coroutineScope {
                        next.boards.map { board -> async { fetch(board, next) } }.awaitAll()
                    }
                } finally {
                    if (request === next) {
                        catalogLoading = false
                        if (next.mode == HomeFeedMode.FOR_YOU) _completedDiscoveryCount.value = next.boards.size
                        updateProgress()
                    }
                }
            }
    }

    private fun selection(force: Boolean): HomeFeedRequest? {
        val selected = source.value
        if (selectedSource != selected) {
            selectedSource = selected
            discoveryLimit = DISCOVERY_PAGE_SIZE
            _completedDiscoveryCount.value = 0
        }
        val currentSettings = actualSettings ?: return null
        val candidates = candidates(_mode.value, selected, currentSettings.hideNsfwBoards)
        val index = directory.value
        val relevant = relevantSources()
        if (candidates.isEmpty() && relevant.any { it in index.loading || it in index.failures }) {
            work?.cancel()
            request = null
            catalogLoading = false
            _canLoadMore.value = false
            _content.value =
                if (relevant.any { it in index.loading }) {
                    Load.Loading
                } else {
                    Load.Failed(relevant.firstNotNullOf { index.failures[it] })
                }
            _contentPolicy.value = currentSettings.contentPolicy()
            updateProgress()
            return null
        }
        val wanted = if (_mode.value == HomeFeedMode.FOR_YOU) candidates.take(discoveryLimit) else candidates
        _canLoadMore.value = _mode.value == HomeFeedMode.FOR_YOU && candidates.size > wanted.size
        if (force) relevant.forEach { epochs[it] = (epochs[it] ?: 0) + 1 }
        return HomeFeedRequest(_mode.value, wanted, limits, epochs.toMap(), currentSettings.contentPolicy())
    }

    private suspend fun fetch(
        board: SiteBoard,
        selection: HomeFeedRequest,
    ) {
        val key = board.key
        if (cache[key]?.matches(selection, key) == true) return
        val loaded =
            catalogGate.withPermit {
                // Provider decorators resume in their caller's context; their media cover and
                // the loader's permanent text filter must not compete with native UI work.
                withContext(catalogDispatcher) {
                    loader
                        .load(
                            listOf(board.board),
                            selection.limits[key]?.let { mapOf(board.board.id to it) }.orEmpty(),
                        ) { id ->
                            providerById.getValue(board.provider).getCatalog(CatalogRequest(board.provider, id))
                        }.single()
                }
            }
        currentCoroutineContext().ensureActive()
        publication.withLock {
            if (request !== selection) return@withLock
            val previous = cache[key]?.takeIf { it.policy == selection.policy }?.feed
            val kept = if (loaded.failed && previous != null) previous.copy(failed = true) else loaded
            cache[key] =
                CachedHomeBoard(kept, selection.limits[key], selection.epochs[key.provider] ?: 0, selection.policy)
            _contentPolicy.value = selection.policy
            _content.value = present(available(selection), selection.boards.size)
            updateProgress()
        }
    }

    private fun available(selection: HomeFeedRequest): List<BoardFeed> =
        selection.boards.mapNotNull { board ->
            cache[board.key]?.takeIf { it.matches(selection, board.key) }?.feed
        }

    private fun candidates(
        mode: HomeFeedMode,
        source: ProviderId?,
        hideNsfw: Boolean,
    ): List<SiteBoard> {
        val eligible =
            directory.value.boards.filter {
                (source == null || it.provider == source) &&
                    !(hideNsfw && it.board.isNsfw) &&
                    !it.board.isPermanentlyFiltered()
            }
        if (mode == HomeFeedMode.FOLLOWING) {
            return eligible
                .filter { it.key in followed.value }
                .sortedWith(compareBy({ it.provider.value }, { it.board.id.value }))
        }
        val perSite =
            providers.map { provider ->
                val sorted = eligible.filter { it.provider == provider.metadata.id }.sortedBy { it.board.id.value }
                val (saved, other) = sorted.partition { it.key in followed.value }
                buildList {
                    repeat(maxOf(saved.size, other.size)) { index ->
                        saved.getOrNull(index)?.let(::add)
                        other.getOrNull(index)?.let(::add)
                    }
                }
            }
        return buildList {
            repeat(
                perSite.maxOfOrNull { it.size } ?: 0,
            ) { index -> perSite.forEach { it.getOrNull(index)?.let(::add) } }
        }
    }

    private fun present(
        feeds: List<BoardFeed>,
        expected: Int,
    ): Load<List<FeedThread>> {
        val hasVideo = feeds.any { feed -> feed.threads.any { it.hasFeedVideo() } }
        val failed = feeds.any { it.failed } || relevantSources().any { it in directory.value.failures }
        val completedBatch = feeds.isNotEmpty() && feeds.size == expected
        val unavailableVideos = failed && !hasVideo
        if (completedBatch && unavailableVideos) {
            return Load.Failed("Couldn't load the feed. Try again.")
        }
        return Load.Ready(
            feeds
                .flatMap { feed -> feed.threads.map { FeedThread(it.key.provider, it) } }
                .sortedWith(compareBy(FeedSort.ACTIVITY.comparator()) { it.thread }),
        )
    }

    private fun relevantSources(): Set<ProviderId> =
        if (_mode.value == HomeFeedMode.FOLLOWING) {
            followed.value.filter { source.value == null || it.provider == source.value }.mapTo(
                mutableSetOf(),
            ) { it.provider }
        } else {
            providers.filter { source.value == null || it.metadata.id == source.value }.mapTo(
                mutableSetOf(),
            ) { it.metadata.id }
        }

    private fun updateProgress() {
        val hasVideo =
            (_content.value as? Load.Ready)?.value.orEmpty().any { entry ->
                entry.thread.originalPost.attachments
                    .any { it.type == MediaType.VIDEO && it.sourceUrl.isNotBlank() }
            }
        val waitingForDirectory =
            !hasVideo && !_canLoadMore.value && relevantSources().any { it in directory.value.loading }
        _isLoadingMore.value = catalogLoading || waitingForDirectory
    }

    private fun observeLimits(index: List<SiteBoard>) {
        limitObservation?.cancel()
        limitObservation =
            scope.launch {
                combine(
                    providers.map { provider ->
                        preferences
                            .observeFeedThreadLimits(
                                provider.metadata.id,
                                index.filter { it.provider == provider.metadata.id }.mapTo(
                                    mutableSetOf(),
                                ) { it.board.id },
                            ).map { values -> values.mapKeys { FollowedBoard(provider.metadata.id, it.key) } }
                    },
                ) { values -> values.fold(emptyMap<FollowedBoard, FeedThreadLimit>()) { all, next -> all + next } }
                    .collect { latest ->
                        if (latest != limits) {
                            limits = latest
                            if (isActive()) load()
                        }
                    }
            }
    }

    companion object {
        const val DISCOVERY_PAGE_SIZE = 8
    }
}

private val SiteBoard.key: FollowedBoard get() = FollowedBoard(provider, board.id)

private fun AppSettings.contentPolicy() = HomeFeedContentPolicy(hideNsfwBoards, coverViolentMedia)

private fun com.orbin.core.model.CatalogThread.hasFeedVideo() =
    originalPost.attachments.any { it.type == MediaType.VIDEO && it.sourceUrl.isNotBlank() }

private data class HomeFeedRequest(
    val mode: HomeFeedMode,
    val boards: List<SiteBoard>,
    val limits: Map<FollowedBoard, FeedThreadLimit>,
    val epochs: Map<ProviderId, Int>,
    val policy: HomeFeedContentPolicy,
)

private data class CachedHomeBoard(
    val feed: BoardFeed,
    val limit: FeedThreadLimit?,
    val epoch: Int,
    val policy: HomeFeedContentPolicy,
) {
    fun matches(
        request: HomeFeedRequest,
        key: FollowedBoard,
    ): Boolean =
        limit == request.limits[key] && epoch == (request.epochs[key.provider] ?: 0) && policy == request.policy
}
