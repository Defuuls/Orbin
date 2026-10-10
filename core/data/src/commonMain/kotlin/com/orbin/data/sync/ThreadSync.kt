package com.orbin.data.sync

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import com.orbin.data.database.dao.BookmarkDao
import com.orbin.data.database.dao.HistoryDao
import com.orbin.data.database.entity.BookmarkEntity
import com.orbin.data.database.entity.HistoryEntity
import com.orbin.domain.repository.ThreadSyncRepository
import com.orbin.domain.repository.ThreadSyncState
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.serialization.SerializationException
import kotlinx.serialization.json.Json
import kotlin.time.Clock

/**
 * Keeps thread progress the same on every device that points at one WebDAV folder: which threads
 * were read, where reading stopped, and which are bookmarked or watched. Android and iOS both run
 * this, over the same database.
 *
 * Each [sync] reads the file, merges it with this device and with what the two last agreed on
 * ([mergeSync]), writes the result to the database and, if it changed, back to the file. The
 * agreed state is kept here so a removal on one device removes on the others.
 *
 * The account is kept in the app's private settings file. The sync file itself is plain JSON on the
 * reader's own server, sent only over https.
 */
class ThreadSync(
    private val history: HistoryDao,
    private val bookmarks: BookmarkDao,
    private val preferences: DataStore<Preferences>,
    private val remote: WebDavSyncFile,
    private val now: () -> Long = { Clock.System.now().toEpochMilliseconds() },
) : ThreadSyncRepository {
    private val json =
        Json {
            ignoreUnknownKeys = true
            encodeDefaults = true
        }
    private val running = Mutex()

    override val state: Flow<ThreadSyncState> =
        preferences.data.map { prefs ->
            ThreadSyncState(
                folderUrl = prefs[FOLDER].orEmpty(),
                username = prefs[USERNAME].orEmpty(),
                hasPassword = !prefs[PASSWORD].isNullOrEmpty(),
                lastSyncedMillis = prefs[LAST_SYNCED],
                error = prefs[LAST_ERROR],
            )
        }

    override suspend fun setAccount(
        folderUrl: String?,
        username: String?,
        password: String?,
    ) {
        preferences.edit { prefs ->
            folderUrl?.let { prefs[FOLDER] = it.trim() }
            username?.let { prefs[USERNAME] = it.trim() }
            password?.let { prefs[PASSWORD] = it }
            // A different folder is a different file: nothing agreed with the old one carries over.
            if (folderUrl != null) prefs.remove(BASE)
            prefs.remove(LAST_ERROR)
        }
    }

    override suspend fun sync() {
        if (running.isLocked) return
        running.withLock {
            val account =
                preferences.data
                    .first()
                    .account()
                    ?.takeIf { it.isUsable } ?: return
            val error =
                try {
                    syncWith(account)
                    null
                } catch (failure: SyncException) {
                    failure.message
                }
            preferences.edit { prefs ->
                if (error == null) {
                    prefs[LAST_SYNCED] = now()
                    prefs.remove(LAST_ERROR)
                } else {
                    prefs[LAST_ERROR] = error
                }
            }
        }
    }

    private suspend fun syncWith(account: SyncAccount) {
        val base = preferences.data.first()[BASE]?.let(::decode)
        repeat(MAX_ATTEMPTS) {
            val localVisits = history.all()
            val localBookmarks = bookmarks.all()
            val local =
                SyncDocument(
                    visits = localVisits.map { it.toSynced() },
                    bookmarks = localBookmarks.map { it.toSynced() },
                )
            val file = remote.read(account)
            val theirs = file?.text?.let { decode(it) ?: throw SyncException("The sync file is damaged") }
            val merged = mergeSync(base, local, theirs)
            applyLocally(localVisits, localBookmarks, merged)
            val written =
                merged == theirs ||
                    remote.write(account, json.encodeToString(SyncDocument.serializer(), merged), file?.etag)
            if (written) {
                preferences.edit { it[BASE] = json.encodeToString(SyncDocument.serializer(), merged) }
                return
            }
            // Another device wrote in between: read its version and merge again.
        }
        throw SyncException("Another device kept changing the sync file; try again")
    }

    private suspend fun applyLocally(
        visits: List<HistoryEntity>,
        marks: List<BookmarkEntity>,
        merged: SyncDocument,
    ) {
        val visitByKey = visits.associateBy { SyncKey(it.provider, it.board, it.thread) }
        merged.visits.forEach { visit ->
            val existing = visitByKey[visit.key]
            if (existing?.toSynced() != visit) {
                history.upsert(
                    HistoryEntity(
                        provider = visit.provider,
                        board = visit.board,
                        thread = visit.thread,
                        title = visit.title,
                        thumbnailUrl = visit.thumbnailUrl,
                        lastVisitedMillis = visit.lastVisitedMillis,
                        lastReadPostId = visit.lastReadPostId,
                        // The offset into a post is this screen's; another device's post starts at its top.
                        lastReadOffsetPx =
                            existing?.lastReadOffsetPx?.takeIf { existing.lastReadPostId == visit.lastReadPostId } ?: 0,
                    ),
                )
            }
        }
        val keptVisits = merged.visits.mapTo(HashSet()) { it.key }
        visitByKey.keys.filterNot { it in keptVisits }.forEach { history.deleteByKey(it.provider, it.board, it.thread) }

        val markByKey = marks.associateBy { SyncKey(it.provider, it.board, it.thread) }
        merged.bookmarks.forEach { mark ->
            val existing = markByKey[mark.key]
            if (existing?.toSynced() != mark) {
                bookmarks.upsert(
                    BookmarkEntity(
                        provider = mark.provider,
                        board = mark.board,
                        thread = mark.thread,
                        title = mark.title,
                        thumbnailUrl = mark.thumbnailUrl,
                        createdAtMillis = mark.createdAtMillis,
                        isWatched = mark.watched,
                        lastSeenReplyCount = mark.lastSeenReplyCount,
                        // What the site last said is this device's to learn; until then, nothing is new.
                        latestReplyCount = maxOf(existing?.latestReplyCount ?: 0, mark.lastSeenReplyCount),
                        isThreadDead = existing?.isThreadDead ?: false,
                    ),
                )
            }
        }
        val keptMarks = merged.bookmarks.mapTo(HashSet()) { it.key }
        markByKey.keys.filterNot { it in keptMarks }.forEach { bookmarks.deleteByKey(it.provider, it.board, it.thread) }
    }

    private fun decode(text: String): SyncDocument? =
        try {
            json.decodeFromString(SyncDocument.serializer(), text)
        } catch (_: SerializationException) {
            null
        } catch (_: IllegalArgumentException) {
            null
        }

    private fun Preferences.account(): SyncAccount? {
        val folder = this[FOLDER]?.takeIf { it.isNotBlank() } ?: return null
        return SyncAccount(folder, this[USERNAME].orEmpty(), this[PASSWORD].orEmpty())
    }

    private companion object {
        val FOLDER = stringPreferencesKey("sync_folder_url")
        val USERNAME = stringPreferencesKey("sync_username")
        val PASSWORD = stringPreferencesKey("sync_password")
        val BASE = stringPreferencesKey("sync_agreed_state")
        val LAST_SYNCED = longPreferencesKey("sync_last_synced")
        val LAST_ERROR = stringPreferencesKey("sync_last_error")
        const val MAX_ATTEMPTS = 3
    }
}

private fun HistoryEntity.toSynced() =
    SyncedVisit(provider, board, thread, title, thumbnailUrl, lastVisitedMillis, lastReadPostId)

private fun BookmarkEntity.toSynced() =
    SyncedBookmark(provider, board, thread, title, thumbnailUrl, createdAtMillis, isWatched, lastSeenReplyCount)
