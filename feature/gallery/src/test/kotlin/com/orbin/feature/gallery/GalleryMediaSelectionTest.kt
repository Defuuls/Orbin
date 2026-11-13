package com.orbin.feature.gallery

import com.google.common.truth.Truth.assertThat
import com.orbin.core.model.MediaAttachment
import com.orbin.core.model.MediaType
import kotlinx.collections.immutable.toImmutableList
import org.junit.Test

class GalleryMediaSelectionTest {
    private val files = List(4) { file("reply-$it", "https://site.example/thumb-$it.jpg") }

    @Test
    fun attachmentIdentityWinsOverItsOldIndexAndPreview() {
        assertThat(galleryInitialPage(files, "reply-2", files[0].thumbnailUrl, 0)).isEqualTo(2)
    }

    @Test
    fun lynxCatalogIdentityFallsBackOnlyToItsUniqueExactThumbnail() {
        assertThat(galleryInitialPage(files, "catalog-id", files[2].thumbnailUrl, 0)).isEqualTo(2)
        assertThat(galleryInitialPage(files, "catalog-id", "thumb-2.jpg", 0)).isEqualTo(-1)
        assertThat(galleryInitialPage(files + files[2], "catalog-id", files[2].thumbnailUrl, 0)).isEqualTo(-1)
    }

    @Test
    fun missingExplicitSelectionIsUnavailableButLegacyIndexOnlyEntryIsPreserved() {
        assertThat(galleryInitialPage(files, "removed", null, 0)).isEqualTo(-1)
        assertThat(galleryInitialPage(files, null, "https://missing.example/file.jpg", 0)).isEqualTo(-1)
        assertThat(galleryInitialPage(files, null, null, 3)).isEqualTo(3)
        assertThat(galleryInitialPage(listOf(files[0].copy(thumbnailUrl = "")), "catalog-id", "", 0)).isEqualTo(-1)
    }

    @Test
    fun downloadRestIncludesEarlierAndLaterFilesForFirstMiddleAndLastSelections() {
        val ready =
            GalleryContentState.Ready(
                files.toImmutableList(),
                files.toImmutableList(),
                files.indices.toImmutableList(),
            )
        listOf(0, 1, 3).forEach { current ->
            assertThat(galleryRemainingMedia(ready, current))
                .containsExactlyElementsIn(
                    files.filterIndexed { index, _ ->
                        index !=
                            current
                    },
                ).inOrder()
        }
        assertThat(galleryRemainingMedia(ready, 8)).isEmpty()
    }

    @Test
    fun videoViewerMapsBackToTheCompleteThreadBeforeDownloadingOtherFiles() {
        val ready =
            GalleryContentState.Ready(
                listOf(files[1], files[3]).toImmutableList(),
                files.toImmutableList(),
                listOf(1, 3).toImmutableList(),
            )
        assertThat(galleryRemainingMedia(ready, 1)).containsExactly(files[0], files[1], files[2]).inOrder()
    }

    private fun file(
        id: String,
        preview: String,
    ) = MediaAttachment(
        id = id,
        originalFileName = "$id.jpg",
        extension = "jpg",
        type = MediaType.IMAGE,
        sourceUrl = "https://site.example/$id.jpg",
        thumbnailUrl = preview,
    )
}
