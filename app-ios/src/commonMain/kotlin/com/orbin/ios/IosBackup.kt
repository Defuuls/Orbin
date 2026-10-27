package com.orbin.ios

import com.orbin.core.model.AppSettings
import com.orbin.core.model.BackupBoardRef
import com.orbin.core.model.BackupBookmark
import com.orbin.core.model.BackupDocument
import com.orbin.core.model.BoardId
import com.orbin.core.model.Bookmark
import com.orbin.core.model.FormFactor
import com.orbin.core.model.ProviderId
import com.orbin.core.model.ThreadId
import com.orbin.core.model.ThreadKey
import com.orbin.domain.repository.BoardPreferencesRepository
import com.orbin.domain.repository.BookmarkRepository
import com.orbin.domain.repository.SettingsRepository
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.serialization.json.Json
import kotlin.time.Clock

/**
 * The platform half of a backup: hand a file to the share sheet (Save to Files, AirDrop, …) and
 * pick one to restore. Each returns once the reader is done with the sheet.
 */
interface BackupFiles {
    /** Offers [contents] as a file named [fileName]; true when the reader saved or sent it. */
    suspend fun share(
        fileName: String,
        contents: String,
    ): Boolean

    /** The text of a backup file the reader picked, or null when they cancelled. */
    suspend fun pick(): String?
}

/** What the settings rows say about the last export or import. */
sealed interface BackupState {
    data object Idle : BackupState

    data object Working : BackupState

    data object Exported : BackupState

    data class Imported(
        val boards: Int,
        val bookmarks: Int,
    ) : BackupState

    data class Failed(
        val message: String,
    ) : BackupState
}

/**
 * Export and import in Android's backup format ([BackupDocument]), so a file exported on either
 * platform restores on the other. It restores what Android's `BackupService` restores, the same
 * way: settings, then followed and favourite boards and bookmarks added — never removed — so an
 * import merges into what is here. Saved searches are Android's alone; iOS keeps and exports none,
 * and skips them on import. The app lock is never restored, as on Android: a backup file must not
 * weaken, or unexpectedly turn on, this device's lock.
 */
class IosBackup(
    private val settings: SettingsRepository,
    private val boardPreferences: BoardPreferencesRepository,
    private val bookmarks: BookmarkRepository,
    private val providers: List<ProviderId>,
    private val files: BackupFiles,
    private val scope: CoroutineScope,
    private val appVersion: String,
    private val isAuthorized: () -> Boolean,
    private val now: () -> String = { Clock.System.now().toString() },
) {
    private val _state = MutableStateFlow<BackupState>(BackupState.Idle)
    val state: StateFlow<BackupState> = _state.asStateFlow()
    private var pending: Job? = null
    private var generation = 0L

    /** Writes a backup and opens the share sheet for it. */
    fun export() =
        perform {
            val contents = exportToJson()
            requireAuthorization()
            if (files.share(fileName(), contents)) BackupState.Exported else BackupState.Idle
        }

    /** Asks for a backup file and restores it. */
    fun import() =
        perform {
            val text = files.pick() ?: return@perform BackupState.Idle
            requireAuthorization()
            importFromJson(text).fold(
                onSuccess = { BackupState.Imported(boards = it.first, bookmarks = it.second) },
                onFailure = { BackupState.Failed(it.message ?: "Not an Orbin backup") },
            )
        }

    suspend fun exportToJson(): String {
        val document =
            BackupDocument(
                exportedAt = now(),
                exportedByAppVersion = appVersion,
                settings = settings.settings.first(),
                subscribedBoards = providers.flatMap { it.refs(subscribed = true) },
                favoriteBoards = providers.flatMap { it.refs(subscribed = false) },
                bookmarks = bookmarks.observeBookmarks().first().map { it.toBackup() },
            )
        return json.encodeToString(BackupDocument.serializer(), document)
    }

    /** Restores [backupJson]; the result counts the boards and bookmarks restored. */
    suspend fun importFromJson(backupJson: String): Result<Pair<Int, Int>> =
        runCatching {
            requireAuthorization()
            require(backupJson.length <= MAX_BACKUP_JSON_CHARS) {
                "Backup file is too large to import."
            }
            val document = json.decodeFromString(BackupDocument.serializer(), backupJson)
            require(document.formatVersion <= BackupDocument.CURRENT_FORMAT_VERSION) {
                "This backup was written by a newer version of Orbin (format ${document.formatVersion})."
            }
            restoreSettings(document.settings)
            val known = providers.map { it.value }.toSet()
            val subscribed =
                document.subscribedBoards.filter {
                    it.providerId in known && isValidBoardSlug(it.boardId)
                }
            subscribed.forEach {
                requireAuthorization()
                boardPreferences.setSubscribedBoard(
                    ProviderId(it.providerId),
                    BoardId(it.boardId),
                    true,
                )
            }
            val favourites =
                document.favoriteBoards.filter {
                    it.providerId in known && isValidBoardSlug(it.boardId)
                }
            favourites.forEach {
                requireAuthorization()
                boardPreferences.setFavoriteBoard(
                    ProviderId(it.providerId),
                    BoardId(it.boardId),
                    true,
                )
            }
            val restored =
                document.bookmarks.filter {
                    it.providerId in known && isValidBoardSlug(it.boardId) && it.threadId > 0
                }
            restored.forEach {
                requireAuthorization()
                bookmarks.addBookmark(it.toBookmark())
            }
            subscribed.size to restored.size
        }.onFailure { if (it is CancellationException) throw it }

    private fun perform(block: suspend () -> BackupState) {
        if (_state.value == BackupState.Working) return
        if (!isAuthorized()) return
        val session = ++generation
        _state.value = BackupState.Working
        pending =
            scope.launch {
                try {
                    requireAuthorization()
                    val result =
                        runCatching { block() }
                            .onFailure { if (it is CancellationException) throw it }
                            .getOrElse { BackupState.Failed(it.message ?: "Something went wrong") }
                    if (session == generation) _state.value = result
                } finally {
                    if (session == generation && _state.value == BackupState.Working) _state.value = BackupState.Idle
                }
            }
    }

    /** Relocking revokes this operation even if the owner unlocks again before a callback arrives. */
    fun cancelPending() {
        generation++
        pending?.cancel()
        pending = null
        _state.value = BackupState.Idle
    }

    private suspend fun requireAuthorization() {
        currentCoroutineContext().ensureActive()
        check(isAuthorized()) { "Unlock Orbin before using backups" }
    }

    private suspend fun ProviderId.refs(subscribed: Boolean): List<BackupBoardRef> {
        val boards =
            if (subscribed) {
                boardPreferences.observeSubscribedBoards(
                    this,
                )
            } else {
                boardPreferences.observeFavoriteBoards(this)
            }
        return boards.first().map { BackupBoardRef(providerId = value, boardId = it.value) }
    }

    // The same fields Android's BackupService restores, less the app lock.
    private suspend fun restoreSettings(restored: AppSettings) =
        with(settings) {
            requireAuthorization()
            setHideNsfwBoards(restored.hideNsfwBoards)
            requireAuthorization()
            setCoverViolentMedia(restored.coverViolentMedia)
            requireAuthorization()
            setDeepMediaScan(restored.deepMediaScan)
            requireAuthorization()
            setThemeMode(restored.themeMode)
            requireAuthorization()
            setColorTheme(restored.colorTheme)
            requireAuthorization()
            setAmoled(restored.amoled)
            requireAuthorization()
            setFeedColumns(FormFactor.FOLDABLE, restored.unfoldedFeedColumns)
            requireAuthorization()
            setFeedColumns(FormFactor.TABLET, restored.tabletFeedColumns)
            requireAuthorization()
            setTabletCatalogColumns(restored.tabletCatalogColumns)
            requireAuthorization()
            if (restored.activeProviderId.isNotBlank()) setActiveProviderId(ProviderId(restored.activeProviderId))
            requireAuthorization()
            setOnboardingCompleted(restored.onboardingCompleted)
        }

    private fun fileName(): String = "orbin-backup-${now().take(DATE_LENGTH)}.json"

    private companion object {
        const val DATE_LENGTH = 10 // yyyy-MM-dd
        const val MAX_BACKUP_JSON_CHARS = 5 * 1024 * 1024
        const val MAX_BOARD_SLUG_LENGTH = 32
        val VALID_BOARD_SLUG = Regex("""^[A-Za-z0-9_-]{1,$MAX_BOARD_SLUG_LENGTH}$""")

        fun isValidBoardSlug(value: String): Boolean = VALID_BOARD_SLUG.matches(value)

        val json =
            Json {
                prettyPrint = true
                ignoreUnknownKeys = true
                encodeDefaults = true
            }
    }
}

private fun Bookmark.toBackup(): BackupBookmark =
    BackupBookmark(
        providerId = key.provider.value,
        boardId = key.board.value,
        threadId = key.thread.value,
        title = title,
        thumbnailUrl = thumbnailUrl,
        createdAtMillis = createdAtMillis,
        isWatched = isWatched,
        lastSeenReplyCount = lastSeenReplyCount,
    )

private fun BackupBookmark.toBookmark(): Bookmark =
    Bookmark(
        key = ThreadKey(ProviderId(providerId), BoardId(boardId), ThreadId(threadId)),
        title = title,
        thumbnailUrl = thumbnailUrl,
        createdAtMillis = createdAtMillis,
        isWatched = isWatched,
        lastSeenReplyCount = lastSeenReplyCount,
    )
