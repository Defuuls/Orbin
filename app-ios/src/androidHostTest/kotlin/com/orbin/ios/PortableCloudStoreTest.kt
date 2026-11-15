package com.orbin.ios

import com.orbin.core.model.AccentTheme
import com.orbin.core.model.BackupBoardRef
import com.orbin.core.model.BackupBookmark
import com.orbin.core.model.BoardId
import com.orbin.core.model.Bookmark
import com.orbin.core.model.HistoryEntry
import com.orbin.core.model.MediaLibraryRecord
import com.orbin.core.model.ProviderId
import com.orbin.core.model.ThreadAvailability
import com.orbin.core.model.ThreadId
import com.orbin.core.model.ThreadKey
import com.orbin.domain.repository.InMemoryMediaLibraryRepository
import com.orbin.domain.repository.InMemoryThreadMemoryRepository
import com.orbin.domain.repository.ManagedMediaFiles
import com.orbin.provider.api.ProviderMetadata
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import okio.ByteString.Companion.encodeUtf8
import org.junit.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class PortableCloudStoreTest {
    private val key = ThreadKey(ProviderId("fourchan"), BoardId("g"), ThreadId(42))

    @Test
    fun sessionModeDeletesOnlyPreviousCloudHistoryAndWithholdsMemory() =
        runTest {
            val fixture = Fixture(backgroundScope)
            fixture.history.record(
                HistoryEntry(
                    key,
                    "Private previous title",
                    thumbnailUrl = "https://4chan.org/old.jpg",
                    lastVisitedMillis = 1,
                ),
            )
            fixture.memory.update(key) { it.copy(anchorPostId = 45, seenPostIds = setOf(42, 45)) }
            val first = fixture.store.snapshot()
            val oldHistory = first.single { it.id.startsWith("history/") }
            val oldMemory = first.single { it.id.startsWith("memory/") }
            fixture.settings.setHistorySessionOnly(true)
            fixture.history.clear()
            val sessionVisit = key.copy(thread = ThreadId(99))
            fixture.history.record(HistoryEntry(sessionVisit, "Never export this session visit", lastVisitedMillis = 2))
            val privateSnapshot = fixture.store.snapshot()
            val deletion = privateSnapshot.single { it.id.startsWith("history/") }
            assertEquals(oldHistory.id, deletion.id)
            assertTrue(deletion.deleted)
            assertTrue(deletion.revision > oldHistory.revision)
            val identity = Json.parseToJsonElement(deletion.payload).toString()
            assertFalse(identity.contains("title"))
            assertFalse(identity.contains("thumbnail"))
            assertFalse(identity.contains("lastVisited"))
            assertFalse(identity.contains("lastRead"))
            assertTrue(privateSnapshot.none { it.id.startsWith("memory/") })
            assertTrue(
                fixture.revisions.value.items
                    .contains(oldMemory),
            )
            fixture.history.clear()
            fixture.settings.setHistorySessionOnly(false)
            val restored = fixture.store.snapshot()
            assertEquals(oldMemory, restored.single { it.id == oldMemory.id })
            assertEquals(deletion, restored.single { it.id == oldHistory.id })
        }

    @Test
    fun concurrentLocalEditCompetesAboveTheFetchedRevision() =
        runTest {
            val fixture = Fixture(backgroundScope)
            fixture.bookmarks.addBookmark(Bookmark(key, "Original", createdAtMillis = 1))
            val original = fixture.store.snapshot().single { it.id.startsWith("bookmark/") }
            val remote =
                original.copy(
                    revision = original.revision + 10,
                    origin = "other-device",
                    payload =
                        Json.encodeToString(
                            Json.decodeFromString<BackupBookmark>(original.payload).copy(title = "Remote"),
                        ),
                )
            fixture.bookmarks.addBookmark(Bookmark(key, "Edited while fetching", createdAtMillis = 1))
            fixture.store.apply(listOf(remote))
            assertEquals("Edited while fetching", fixture.bookmarks.getBookmark(key)?.title)
            val local = fixture.store.snapshot().single { it.id == original.id }
            assertTrue(local.revision > remote.revision)
            assertEquals("Edited while fetching", Json.decodeFromString<BackupBookmark>(local.payload).title)
        }

    @Test
    fun aMissingManagedFileDoesNotDeleteTheLibraryIdentityFromCloud() =
        runTest {
            val fixture = Fixture(backgroundScope)
            fixture.library.put(
                MediaLibraryRecord(
                    id = "saved-1",
                    providerId = "fourchan",
                    fileName = "photo.jpg",
                    storageKey = "missing.media",
                ),
            )
            val first = fixture.store.snapshot().single { it.id.startsWith("media/") }
            assertFalse(first.deleted)
            assertNull(first.assetPath)
            val next = fixture.store.snapshot().single { it.id == first.id }
            assertEquals(first.revision, next.revision)
            assertFalse(next.deleted)
        }

    @Test
    fun aRemoteBookmarkKeepsUnreadAndAvailabilityAndADeletedRecordIsRemoved() =
        runTest {
            val fixture = Fixture(backgroundScope)
            fixture.bookmarks.addBookmark(Bookmark(key, "Saved", createdAtMillis = 1))
            val first = fixture.store.snapshot().single { it.id.startsWith("bookmark/") }
            val value =
                Json.decodeFromString<BackupBookmark>(first.payload).copy(
                    lastSeenReplyCount = 2,
                    latestReplyCount = 8,
                    availability = ThreadAvailability.ARCHIVED,
                    isThreadDead = true,
                    statusCheckedAtMillis = 55,
                )
            val remote =
                first.copy(
                    revision = first.revision + 10,
                    origin = "other-device",
                    payload = Json.encodeToString(value),
                )
            fixture.store.apply(listOf(remote))
            assertEquals(6, fixture.bookmarks.getBookmark(key)?.unreadCount)
            assertEquals(ThreadAvailability.ARCHIVED, fixture.bookmarks.getBookmark(key)?.availability)
            fixture.store.apply(listOf(remote.copy(revision = remote.revision + 1, deleted = true)))
            assertNull(fixture.bookmarks.getBookmark(key))
        }

    @Test
    fun cloudPreferencesCannotEnableHistoryOrWeakenLocalPrivacy() =
        runTest {
            val fixture = Fixture(backgroundScope)
            fixture.settings.setHideNsfwBoards(true)
            fixture.settings.setHistorySessionOnly(true)
            fixture.settings.setSaveBrowsingHistory(false)
            fixture.settings.setCoverViolentMedia(true)
            fixture.settings.setDeepMediaScan(true)
            val first = fixture.store.snapshot().single { it.id.startsWith("preferences/") }
            val remotePrefs =
                fixture.settings.settings.value.copy(
                    hideNsfwBoards = false,
                    historySessionOnly = false,
                    saveBrowsingHistory = true,
                    accentTheme = AccentTheme.TEAL,
                    coverViolentMedia = false,
                    deepMediaScan = false,
                )
            fixture.store.apply(
                listOf(first.copy(revision = first.revision + 10, payload = Json.encodeToString(remotePrefs))),
            )
            assertTrue(fixture.settings.settings.value.hideNsfwBoards)
            assertTrue(fixture.settings.settings.value.historySessionOnly)
            assertFalse(fixture.settings.settings.value.saveBrowsingHistory)
            assertTrue(fixture.settings.settings.value.coverViolentMedia)
            assertTrue(fixture.settings.settings.value.deepMediaScan)
            assertEquals(AccentTheme.TEAL, fixture.settings.settings.value.accentTheme)
        }

    @Test
    fun anUnknownProviderIsDeferredWithoutDeletionOrBlockingKnownData() =
        runTest {
            val fixture = Fixture(backgroundScope)
            fixture.store.snapshot()
            val ref = BackupBoardRef("future-provider", "g")
            val unknown =
                CloudSyncItem(
                    "follow/${"future-provider/g".encodeUtf8().base64Url()}",
                    500,
                    "other-device",
                    Json.encodeToString(ref),
                )
            fixture.store.apply(listOf(unknown))
            assertTrue(unknown.id in fixture.revisions.value.deferredIds)
            repeat(2) {
                assertTrue(fixture.store.snapshot().none { it.id == unknown.id })
                assertEquals(
                    unknown,
                    fixture.revisions.value.items
                        .single { it.id == unknown.id },
                )
            }
            assertTrue(
                fixture.boards.subscribed.value[ProviderId("future-provider")]
                    .isNullOrEmpty(),
            )
        }

    @Test
    fun equalRevisionMediaCanRepairAMissingOwnedFile() =
        runTest {
            val fixture = Fixture(backgroundScope)
            fixture.library.put(
                MediaLibraryRecord("photo", "fourchan", fileName = "photo.jpg", storageKey = "original.media"),
            )
            fixture.files.present += "original.media"
            val original = fixture.store.snapshot().single { it.id.startsWith("media/") }
            fixture.files.present.clear()
            val missing = fixture.store.snapshot().single { it.id == original.id }
            assertEquals(original.revision, missing.revision)
            assertNull(missing.assetPath)
            fixture.store.apply(listOf(original.copy(assetPath = "/inbox/photo")))
            val repaired = checkNotNull(fixture.library.get("photo"))
            assertTrue(fixture.files.contains(repaired.storageKey))
            assertTrue(repaired.storageKey != "original.media")
            assertEquals(
                original.revision,
                fixture.store
                    .snapshot()
                    .single { it.id == original.id }
                    .revision,
            )
        }

    @Test
    fun failedOwnedFileCleanupStaysDurableAndRetriesWithoutResurrectingTheRecord() =
        runTest {
            val fixture = Fixture(backgroundScope)
            fixture.library.put(
                MediaLibraryRecord("photo", "fourchan", fileName = "photo.jpg", storageKey = "original.media"),
            )
            fixture.files.present += "original.media"
            val original = fixture.store.snapshot().single { it.id.startsWith("media/") }
            fixture.files.refuseRemoval = true
            assertTrue(
                runCatching {
                    fixture.store.apply(listOf(original.copy(revision = original.revision + 10, deleted = true)))
                }.isFailure,
            )
            assertNull(fixture.library.get("photo"))
            assertTrue("original.media" in fixture.revisions.value.pendingFileDeletes)
            fixture.files.refuseRemoval = false
            val next = fixture.store.snapshot().single { it.id == original.id }
            assertTrue(next.deleted)
            assertFalse(fixture.files.contains("original.media"))
            assertTrue(
                fixture.revisions.value.pendingFileDeletes
                    .isEmpty(),
            )
        }

    private class Fixture(
        scope: CoroutineScope,
    ) {
        val settings = FakeSettings()
        val boards = FakeBoardPreferences()
        val bookmarks = FakeBookmarks()
        val history = FakeHistory()
        val memory = InMemoryThreadMemoryRepository()
        val library = InMemoryMediaLibraryRepository()
        val revisions = Revisions()
        val files = RecordingFiles()
        private val backup =
            IosBackup(
                settings,
                boards,
                bookmarks,
                listOf(ProviderId("fourchan")),
                object : BackupFiles {
                    override suspend fun share(
                        fileName: String,
                        contents: String,
                    ) = false

                    override suspend fun pick(): String? = null
                },
                scope,
                "test",
                { true },
                history = history,
                threadMemory = memory,
                mediaLibrary = library,
            )
        val store =
            PortableCloudStore(
                backup,
                CloudRepositories(settings, boards, bookmarks, history, memory, library),
                files,
                object : CloudAssetFiles {
                    override suspend fun localPath(storageKey: String): String? =
                        "/managed/$storageKey".takeIf { files.contains(storageKey) }

                    override suspend fun validateIncoming(
                        path: String,
                        expectedBytes: Long?,
                    ) = Unit

                    override suspend fun discardIncoming(path: String) = Unit
                },
                revisions,
                listOf(ProviderMetadata(ProviderId("fourchan"), "4chan", "https://4chan.org")),
                { true },
                { 100 },
            )
    }

    private class Revisions : CloudRevisionStore {
        var value = CloudRevisionState("this-device")

        override suspend fun read() = value

        override suspend fun write(state: CloudRevisionState) {
            value = state
        }
    }

    private class RecordingFiles : ManagedMediaFiles {
        val present = mutableSetOf<String>()
        var refuseRemoval = false

        override suspend fun contains(storageKey: String) = storageKey in present

        override suspend fun size(storageKey: String): Long? = null

        override suspend fun importFile(
            storageKey: String,
            sourcePath: String,
        ) {
            present += storageKey
        }

        override suspend fun exportFile(
            storageKey: String,
            destinationPath: String,
        ) = false

        override suspend fun remove(storageKey: String) {
            check(!refuseRemoval)
            present -= storageKey
        }
    }
}
