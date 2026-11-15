package com.orbin.feature.settings

import android.content.Context
import com.orbin.core.model.AppSettings
import com.orbin.core.model.BackupBoardRef
import com.orbin.core.model.BackupBookmark
import com.orbin.core.model.BackupDocument
import com.orbin.core.model.BackupSavedSearch
import com.orbin.core.model.BoardId
import com.orbin.core.model.Bookmark
import com.orbin.core.model.ProviderId
import com.orbin.core.model.SavedSearch
import com.orbin.core.model.SearchContentType
import com.orbin.core.model.SearchFilters
import com.orbin.core.model.ThreadId
import com.orbin.core.model.ThreadKey
import com.orbin.core.model.decodeBackupDocument
import com.orbin.core.model.portableExport
import com.orbin.core.model.toBackupHistory
import com.orbin.core.model.toHistoryEntry
import com.orbin.data.backup.PortableBackupArchive
import com.orbin.data.util.defaultFileSystem
import com.orbin.domain.repository.BoardPreferencesRepository
import com.orbin.domain.repository.BookmarkRepository
import com.orbin.domain.repository.HistoryRepository
import com.orbin.domain.repository.ManagedMediaFiles
import com.orbin.domain.repository.MediaLibraryRepository
import com.orbin.domain.repository.SearchRepository
import com.orbin.domain.repository.SettingsRepository
import com.orbin.domain.repository.ThreadMemoryRepository
import com.orbin.domain.repository.restorePortableSettings
import com.orbin.provider.api.ProviderRegistry
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.first
import kotlinx.serialization.json.Json
import okio.Path.Companion.toPath
import javax.inject.Inject
import kotlin.random.Random

/** Portable metadata and streamed ZIP archives; platform sheets own the final picked/shared file. */
class BackupService
    @Inject
    constructor(
        private val settingsRepository: SettingsRepository,
        private val boardPreferences: BoardPreferencesRepository,
        private val bookmarkRepository: BookmarkRepository,
        private val searchRepository: SearchRepository,
        private val registry: ProviderRegistry,
        private val historyRepository: HistoryRepository? = null,
        private val threadMemoryRepository: ThreadMemoryRepository? = null,
        private val mediaLibraryRepository: MediaLibraryRepository? = null,
        private val managedMediaFiles: ManagedMediaFiles? = null,
        @ApplicationContext private val context: Context? = null,
    ) {
        suspend fun exportToJson(appVersionName: String): String =
            json.encodeToString(BackupDocument.serializer(), exportDocument(appVersionName))

        suspend fun exportDocument(appVersionName: String): BackupDocument {
            val providers = registry.all().map { it.metadata.id }
            val document =
                BackupDocument(
                    exportedAt = timestamp(),
                    exportedByAppVersion = appVersionName,
                    settings = settingsRepository.settings.first().portableExport(),
                    subscribedBoards = providers.flatMap { it.refs(subscribed = true) },
                    favoriteBoards = providers.flatMap { it.refs(subscribed = false) },
                    bookmarks = bookmarkRepository.observeBookmarks().first().map { it.toBackup() },
                    savedSearches = searchRepository.observeSavedSearches().first().map { it.toBackup() },
                    history =
                        historyRepository
                            ?.observeAllEntries()
                            ?.first()
                            .orEmpty()
                            .map { it.toBackupHistory() },
                    threadMemory = threadMemoryRepository?.observeAll()?.first().orEmpty(),
                    savedMedia = mediaLibraryRepository?.observeAll()?.first().orEmpty(),
                )
            return document
        }

        /**
         * Restores [backupJson]. By default boards and bookmarks are added, never removed, so
         * importing merges into whatever is already subscribed rather than destroying it; when
         * [mode] is [RestoreMode.REPLACE], each collection is replaced atomically.
         */
        suspend fun importFromJson(
            backupJson: String,
            mode: RestoreMode = RestoreMode.MERGE,
        ): Result<BackupSummary> =
            runCatching {
                require(backupJson.length <= MAX_BACKUP_JSON_CHARS) {
                    "Backup file is too large to import."
                }
                val document = json.decodeBackupDocument(backupJson)
                require(document.formatVersion <= BackupDocument.CURRENT_FORMAT_VERSION) {
                    "This backup was written by a newer version of Orbin " +
                        "(format ${document.formatVersion})."
                }

                importDocument(document, mode).getOrThrow()
            }.onFailure { if (it is CancellationException) throw it }

        suspend fun importDocument(
            document: BackupDocument,
            mode: RestoreMode = RestoreMode.MERGE,
        ): Result<BackupSummary> =
            runCatching {
                val accepted = validateDocument(document)
                restoreSettings(accepted.settings)
                restoreCollections(accepted, mode)
                restoreReadingState(document)
                BackupSummary(
                    exportedAt = document.exportedAt,
                    subscribedBoards = accepted.subscribed.size,
                    favoriteBoards = accepted.favorites.size,
                    bookmarks = accepted.bookmarks.size,
                    savedSearches = accepted.searches.size,
                    skippedUnknownProvider = accepted.unknown,
                )
            }.onFailure { if (it is CancellationException) throw it }

        /** Decode, count and source validation finish before restoring any preferences or collections. */
        private fun validateDocument(document: BackupDocument): ValidatedBackup {
            require(document.formatVersion in 1..BackupDocument.CURRENT_FORMAT_VERSION) { "Unsupported backup format" }
            require(document.history.size <= MAX_READING_RECORDS && document.threadMemory.size <= MAX_READING_RECORDS) {
                "Too many backup records"
            }
            val subscribed =
                document.subscribedBoards
                    .filter { isValidBoardSlug(it.boardId) }
                    .partition { registry.get(ProviderId(it.providerId)) != null }
            val favorites =
                document.favoriteBoards
                    .filter { isValidBoardSlug(it.boardId) }
                    .partition { registry.get(ProviderId(it.providerId)) != null }
            val bookmarks =
                document.bookmarks
                    .filter { isValidBoardSlug(it.boardId) && it.threadId > 0 }
                    .partition { registry.get(ProviderId(it.providerId)) != null }
            // Saved searches are not provider-scoped; their local IDs are deliberately regenerated.
            val searches =
                document.savedSearches
                    .filter { it.boardId?.let(::isValidBoardSlug) ?: true }
                    .map { it.toSavedSearch() }
            val settings =
                document.settings.copy(
                    activeProviderId =
                        document.settings.activeProviderId
                            .takeIf { registry.get(ProviderId(it)) != null }
                            .orEmpty(),
                )
            return ValidatedBackup(
                settings,
                subscribed.first,
                favorites.first,
                bookmarks.first.map { it.toBookmark() },
                searches,
                subscribed.second.size + favorites.second.size + bookmarks.second.size,
            )
        }

        private suspend fun restoreCollections(
            accepted: ValidatedBackup,
            mode: RestoreMode,
        ) {
            if (mode == RestoreMode.REPLACE) {
                val subscribed = accepted.subscribed.groupBy({ ProviderId(it.providerId) }, { BoardId(it.boardId) })
                val favorites = accepted.favorites.groupBy({ ProviderId(it.providerId) }, { BoardId(it.boardId) })
                registry.all().map { it.metadata.id }.forEach { source ->
                    boardPreferences.replaceSubscribedBoards(source, subscribed[source].orEmpty().toSet())
                    boardPreferences.replaceFavoriteBoards(source, favorites[source].orEmpty().toSet())
                }
                bookmarkRepository.replaceBookmarks(accepted.bookmarks)
                searchRepository.replaceSavedSearches(accepted.searches)
            } else {
                accepted.subscribed.forEach { ref ->
                    boardPreferences.setSubscribedBoard(ProviderId(ref.providerId), BoardId(ref.boardId), true)
                }
                accepted.favorites.forEach { ref ->
                    boardPreferences.setFavoriteBoard(ProviderId(ref.providerId), BoardId(ref.boardId), true)
                }
                accepted.bookmarks.forEach { bookmarkRepository.addBookmark(it) }
                accepted.searches.forEach { searchRepository.saveSearch(it) }
            }
        }

        private suspend fun restoreReadingState(document: BackupDocument) {
            document.history.filter { validThreadKey(it.key) }.forEach { row ->
                val source = registry.get(row.key.provider)?.metadata
                val ownThumbnail = row.thumbnailUrl?.takeIf { source?.isOwnMediaUrl(it) == true }
                historyRepository?.record(row.copy(thumbnailUrl = ownThumbnail).toHistoryEntry())
            }
            document.threadMemory.filter { validThreadKey(it.key) }.forEach { memory ->
                threadMemoryRepository?.update(memory.key) { memory }
            }
        }

        private fun validThreadKey(key: ThreadKey): Boolean =
            registry.get(key.provider) != null && isValidBoardSlug(key.board.value) && key.thread.value > 0

        private data class ValidatedBackup(
            val settings: AppSettings,
            val subscribed: List<BackupBoardRef>,
            val favorites: List<BackupBoardRef>,
            val bookmarks: List<Bookmark>,
            val searches: List<SavedSearch>,
            val unknown: Int,
        )

        /** The actual Settings export uses ZIP, including owned media bytes. */
        suspend fun createArchive(appVersionName: String): String {
            val root =
                checkNotNull(
                    context,
                ) { "Backup files are unavailable" }.cacheDir.resolve("orbin-backups").apply { mkdirs() }
            val suffix = Random.nextLong().toString(RANDOM_NAME_RADIX)
            val file = root.resolve("orbin-backup-${System.currentTimeMillis()}-$suffix.zip")
            var completed = false
            try {
                archive().export(file.absolutePath.toPath(), exportDocument(appVersionName))
                completed = true
                return file.absolutePath
            } finally {
                if (!completed) file.delete()
            }
        }

        suspend fun importFromFile(
            path: String,
            mode: RestoreMode = RestoreMode.MERGE,
        ): Result<BackupSummary> =
            runCatching {
                val restored =
                    archive().restore(
                        path.toPath(),
                        acceptsProvider = { registry.get(ProviderId(it)) != null },
                        restoreMetadata = { importDocument(it, mode).getOrThrow() },
                    )
                restored.metadata.copy(mediaFiles = restored.mediaFiles)
            }.onFailure { if (it is CancellationException) throw it }

        fun discardArchive(path: String) {
            defaultFileSystem.delete(path.toPath(), mustExist = false)
        }

        private fun archive(): PortableBackupArchive =
            PortableBackupArchive(
                checkNotNull(mediaLibraryRepository) { "Saved media storage is unavailable" },
                checkNotNull(managedMediaFiles) { "Saved files are unavailable" },
                checkNotNull(context)
                    .cacheDir
                    .resolve("orbin-backup-work")
                    .absolutePath
                    .toPath(),
            )

        suspend fun importBackup(
            backupJson: String,
            mode: RestoreMode = RestoreMode.MERGE,
        ): Result<BackupSummary> = importFromJson(backupJson, mode)

        private suspend fun ProviderId.refs(subscribed: Boolean): List<BackupBoardRef> {
            val boards =
                if (subscribed) {
                    boardPreferences.observeSubscribedBoards(this).first()
                } else {
                    boardPreferences.observeFavoriteBoards(this).first()
                }
            return boards.map { BackupBoardRef(providerId = value, boardId = it.value) }
        }

        private suspend fun restoreSettings(settings: AppSettings) {
            settingsRepository.restorePortableSettings(settings)
            settingsRepository.setOnboardingCompleted(true)
        }

        private fun SavedSearch.toBackup(): BackupSavedSearch =
            BackupSavedSearch(
                text = text,
                boardId = board?.value,
                mediaOnly = filters.mediaOnly,
                minReplies = filters.minReplies,
                includeNsfw = filters.includeNsfw,
                contentTypes = filters.contentTypes.map { it.name },
                createdAtMillis = createdAtMillis,
            )

        private fun BackupSavedSearch.toSavedSearch(): SavedSearch =
            SavedSearch(
                text = text,
                board = boardId?.let(::BoardId),
                filters =
                    SearchFilters(
                        mediaOnly = mediaOnly,
                        minReplies = minReplies,
                        includeNsfw = includeNsfw,
                        // Drop any content type this build does not know, rather than failing the
                        // whole import over one unrecognised value from a newer backup.
                        contentTypes =
                            contentTypes
                                .mapNotNull { name ->
                                    SearchContentType.entries.firstOrNull { it.name == name }
                                }.toSet(),
                    ),
                createdAtMillis = createdAtMillis,
            )

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
                latestReplyCount = latestReplyCount,
                isThreadDead = isThreadDead,
                availability = availability,
                statusCheckedAtMillis = statusCheckedAtMillis,
            )

        private fun BackupBookmark.toBookmark(): Bookmark =
            Bookmark(
                key = ThreadKey(ProviderId(providerId), BoardId(boardId), ThreadId(threadId)),
                title = title,
                // A backup file can come from anyone: keep a thumbnail only if it points at the
                // provider's own https media, so an import can't make the app fetch a tracker URL.
                thumbnailUrl =
                    thumbnailUrl?.takeIf { url ->
                        registry.get(ProviderId(providerId))?.metadata?.isOwnMediaUrl(url) == true
                    },
                createdAtMillis = createdAtMillis,
                isWatched = isWatched,
                lastSeenReplyCount = lastSeenReplyCount,
                latestReplyCount = latestReplyCount,
                isThreadDead = isThreadDead,
                availability = availability,
                statusCheckedAtMillis = statusCheckedAtMillis,
            )

        private fun timestamp(): String =
            java.time.Instant
                .now()
                .toString()

        private companion object {
            const val RANDOM_NAME_RADIX = 16
            const val MAX_READING_RECORDS = 100_000
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

/** What an import actually restored, for the confirmation shown to the user. */
data class BackupSummary(
    val exportedAt: String,
    val subscribedBoards: Int,
    val favoriteBoards: Int,
    val bookmarks: Int,
    val savedSearches: Int,
    /** Board refs and bookmarks skipped because their provider is no longer registered in this build. */
    val skippedUnknownProvider: Int = 0,
    val mediaFiles: Int = 0,
)

enum class RestoreMode {
    MERGE,
    REPLACE,
}
