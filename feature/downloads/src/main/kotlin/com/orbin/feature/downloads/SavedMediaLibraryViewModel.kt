package com.orbin.feature.downloads

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.media.MediaMetadataRetriever
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.orbin.core.model.Board
import com.orbin.core.model.MediaLibraryRecord
import com.orbin.core.model.MediaType
import com.orbin.core.model.ProviderId
import com.orbin.core.model.isCoveredInLibrary
import com.orbin.core.model.isVisibleInLibrary
import com.orbin.domain.repository.BoardRepository
import com.orbin.domain.repository.DownloadRepository
import com.orbin.domain.repository.ManagedMediaFiles
import com.orbin.domain.repository.MediaLibraryRepository
import com.orbin.domain.repository.SettingsRepository
import com.orbin.uinext.SavedMediaOperation
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import kotlinx.coroutines.withContext
import java.io.File
import java.util.UUID
import javax.inject.Inject

internal data class LibraryEntry(
    val record: MediaLibraryRecord,
    val covered: Boolean,
)

internal data class SavedMediaShare(
    val files: List<File>,
    val mimeType: String,
)

internal data class SavedMediaLocalFile(
    val record: MediaLibraryRecord,
    val file: File,
)

/** Every operation reads owned bytes, so opening and sharing keep working without a network. */
@OptIn(ExperimentalCoroutinesApi::class)
@HiltViewModel
class SavedMediaLibraryViewModel
    @Inject
    constructor(
        private val library: MediaLibraryRepository,
        private val files: ManagedMediaFiles,
        private val downloads: DownloadRepository,
        private val settings: SettingsRepository,
        private val boards: BoardRepository,
        @ApplicationContext private val context: Context,
    ) : ViewModel() {
        private val allRecords = library.observeAll()
        internal val preferences =
            settings.settings.stateIn(
                viewModelScope,
                SharingStarted.WhileSubscribed(5_000),
                com.orbin.core.model.AppSettings.Default,
            )
        internal val records =
            allRecords
                .flatMapLatest { records ->
                    val providers = records.map { it.providerId }.distinct()
                    val directory: Flow<Map<String, List<Board>>> =
                        if (providers.isEmpty()) {
                            flowOf(emptyMap())
                        } else {
                            combine(
                                providers.map { id -> boards.observeBoards(ProviderId(id)) },
                            ) { lists -> providers.zip(lists.toList()).toMap() }
                        }
                    combine(directory, settings.settings) { byProvider, policy ->
                        val visible =
                            records.filter { record ->
                                val board = byProvider[record.providerId]?.firstOrNull { it.id.value == record.boardId }
                                record.isVisibleInLibrary(policy, board)
                            }
                        visible.map { record ->
                            val board = byProvider[record.providerId]?.firstOrNull { it.id.value == record.boardId }
                            LibraryEntry(record, record.isCoveredInLibrary(policy, board))
                        }
                    }
                }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

        private val mutableOperation = MutableStateFlow<SavedMediaOperation?>(null)
        internal val operation = mutableOperation.asStateFlow()
        private val mutableOpen = MutableStateFlow<SavedMediaLocalFile?>(null)
        internal val openFile = mutableOpen.asStateFlow()
        private val mutableShares = MutableSharedFlow<SavedMediaShare>()
        internal val shares = mutableShares.asSharedFlow()
        private val previewSlots = Semaphore(2)
        private val previews = File(context.cacheDir, "library-previews/${UUID.randomUUID()}")
        private val viewer = File(context.cacheDir, "library-viewer/${UUID.randomUUID()}")

        init {
            viewModelScope.launch(Dispatchers.IO) {
                // Shares must remain readable while the receiving app consumes its granted URI.
                File(context.cacheDir, "shared-media")
                    .listFiles()
                    ?.filter {
                        System.currentTimeMillis() - it.lastModified() > SHARE_LIFETIME_MS
                    }?.forEach { it.deleteRecursively() }
            }
        }

        internal fun open(id: String) =
            runBatch("Opening saved file", listOf(id)) { record ->
                val target = stage(record, viewer)
                withContext(Dispatchers.Main) {
                    mutableOpen.value?.file?.delete()
                    mutableOpen.value = SavedMediaLocalFile(record, target)
                }
                true
            }

        internal fun close() {
            val previous = mutableOpen.value
            mutableOpen.value = null
            viewModelScope.launch(Dispatchers.IO) { previous?.file?.delete() }
        }

        internal fun share(ids: List<String>) {
            if (mutableOperation.value?.running == true) return
            val staged = mutableListOf<File>()
            val types = mutableSetOf<String>()
            val directory = File(context.cacheDir, "shared-media/${UUID.randomUUID()}")
            runBatch("Preparing files to share", ids, onComplete = { successes, _ ->
                if (successes > 0) {
                    mutableShares.emit(SavedMediaShare(staged.toList(), types.singleOrNull() ?: "*/*"))
                } else {
                    directory.deleteRecursively()
                }
            }) { record ->
                staged += stage(record, directory)
                types += record.mimeType
                true
            }
        }

        internal fun export(ids: List<String>) =
            runBatch("Saving files to device", ids) { record ->
                val directory = File(context.cacheDir, "library-export/${UUID.randomUUID()}")
                try {
                    val target = stage(record, directory)
                    downloads.saveFile(target.absolutePath, record.fileName, record.mimeType)
                } finally {
                    directory.deleteRecursively()
                }
            }

        internal fun delete(ids: List<String>) =
            runBatch("Deleting saved files", ids) { record ->
                val others = allRecords.first().filterNot { it.id == record.id }
                listOfNotNull(record.storageKey, record.thumbnailStorageKey).distinct().forEach { key ->
                    if (others.none { it.storageKey == key || it.thumbnailStorageKey == key }) files.remove(key)
                }
                library.remove(record.id)
                true
            }

        internal fun shareFailed() {
            mutableOperation.value =
                SavedMediaOperation("Share unavailable", 0, 0, "No app could receive these files.", running = false)
        }

        /** Decode one bounded local preview; encrypted originals never become image-loader cache entries. */
        internal suspend fun preview(record: MediaLibraryRecord): File? =
            previewSlots.withPermit {
                withContext(Dispatchers.IO) {
                    val folder =
                        File(
                            previews,
                            UUID.nameUUIDFromBytes(record.id.toByteArray()).toString(),
                        ).apply { mkdirs() }
                    val image = File(folder, "preview.jpg")
                    if (image.isFile) return@withContext image
                    val source = File(folder, "source")
                    try {
                        val key = record.thumbnailStorageKey ?: record.storageKey
                        if (!files.exportFile(key, source.absolutePath)) return@withContext null
                        val bitmap =
                            if (record.mediaType == MediaType.VIDEO && record.thumbnailStorageKey == null) {
                                MediaMetadataRetriever().let { retriever ->
                                    try {
                                        retriever.setDataSource(source.absolutePath)
                                        retriever.getScaledFrameAtTime(
                                            0,
                                            MediaMetadataRetriever.OPTION_CLOSEST_SYNC,
                                            VIDEO_THUMBNAIL_EDGE,
                                            VIDEO_THUMBNAIL_EDGE,
                                        )
                                    } finally {
                                        retriever.release()
                                    }
                                }
                            } else {
                                val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
                                BitmapFactory.decodeFile(source.absolutePath, bounds)
                                val sample =
                                    generateSequence(1) { it * 2 }.first {
                                        maxOf(bounds.outWidth, bounds.outHeight) / it <=
                                            IMAGE_THUMBNAIL_EDGE
                                    }
                                BitmapFactory.decodeFile(
                                    source.absolutePath,
                                    BitmapFactory.Options().apply { inSampleSize = sample },
                                )
                            }
                        bitmap ?: return@withContext null
                        try {
                            image.outputStream().use { output ->
                                bitmap.compress(Bitmap.CompressFormat.JPEG, THUMBNAIL_JPEG_QUALITY, output)
                            }
                        } finally {
                            bitmap.recycle()
                        }
                        image
                    } catch (
                        cancelled: CancellationException,
                    ) {
                        throw cancelled
                    } catch (_: Exception) {
                        image.delete()
                        null
                    } finally {
                        source.delete()
                    }
                }
            }

        private suspend fun stage(
            record: MediaLibraryRecord,
            folder: File,
        ): File =
            withContext(Dispatchers.IO) {
                val name =
                    record.fileName
                        .replace(
                            Regex("[^\\p{L}\\p{N}._ -]"),
                            "_",
                        ).take(MAX_STAGED_FILENAME_LENGTH)
                        .trim('.', ' ')
                        .ifBlank { "saved-file" }
                val target = File(File(folder, UUID.randomUUID().toString()).apply { mkdirs() }, name)
                check(files.exportFile(record.storageKey, target.absolutePath)) { "Saved file is missing" }
                target
            }

        private fun runBatch(
            label: String,
            ids: List<String>,
            onComplete: suspend (Int, Int) -> Unit = { _, _ -> },
            action: suspend (MediaLibraryRecord) -> Boolean,
        ) {
            if (mutableOperation.value?.running == true || ids.isEmpty()) return
            val keys = ids.distinct()
            mutableOperation.value = SavedMediaOperation(label, 0, keys.size)
            viewModelScope.launch {
                var successes = 0
                keys.forEachIndexed { index, id ->
                    // Re-evaluate visible identity at action time; hidden or removed entries never export.
                    val record = records.value.firstOrNull { it.record.id == id }?.record
                    val succeeded =
                        try {
                            record != null && action(record)
                        } catch (
                            cancelled: CancellationException,
                        ) {
                            throw cancelled
                        } catch (_: Exception) {
                            false
                        }
                    if (succeeded) successes++
                    mutableOperation.value = SavedMediaOperation(label, index + 1, keys.size)
                }
                val failures = keys.size - successes
                try {
                    onComplete(successes, failures)
                } catch (
                    cancelled: CancellationException,
                ) {
                    throw cancelled
                } catch (_: Exception) {
                    shareFailed()
                    return@launch
                }
                mutableOperation.value =
                    SavedMediaOperation(
                        label,
                        keys.size,
                        keys.size,
                        "$successes completed" +
                            if (failures >
                                0
                            ) {
                                "; $failures unavailable or failed. Try again."
                            } else {
                                "."
                            },
                        running = false,
                    )
            }
        }

        override fun onCleared() {
            previews.deleteRecursively()
            viewer.deleteRecursively()
            super.onCleared()
        }

        private companion object {
            const val SHARE_LIFETIME_MS = 86_400_000L
            const val VIDEO_THUMBNAIL_EDGE = 320
            const val IMAGE_THUMBNAIL_EDGE = 640
            const val THUMBNAIL_JPEG_QUALITY = 85
            const val MAX_STAGED_FILENAME_LENGTH = 150
        }
    }
