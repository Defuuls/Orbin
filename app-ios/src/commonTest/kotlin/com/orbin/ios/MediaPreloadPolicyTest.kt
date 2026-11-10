package com.orbin.ios

import com.orbin.core.model.MediaAttachment
import com.orbin.core.model.MediaType
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertSame

class MediaPreloadPolicyTest {
    @Test
    fun onlyTheNextPlayableSafeUncoveredClipIsSelected() {
        val files =
            listOf(
                file("current"),
                file("still", type = MediaType.IMAGE),
                file("spoiler").copy(isSpoiler = true),
                file("unsafe").copy(sourceUrl = "http://example.com/unsafe.mp4"),
                file("unknown", extension = "swf", type = MediaType.UNKNOWN),
                file("next", extension = "mp3", type = MediaType.AUDIO),
                file("later"),
            )

        assertEquals(files[5].preloadKey(), nextMediaPreloadTarget(files, 0, true, true))
        assertNull(nextMediaPreloadTarget(files, 0, false, true))
        assertNull(nextMediaPreloadTarget(files, -1, true, true))
        assertNull(nextMediaPreloadTarget(files, files.size, true, true))
        assertNull(nextMediaPreloadTarget(files, files.lastIndex, true, true))
    }

    @Test
    fun unavailableWebMIsSkippedWithoutHidingTheNextNativeClip() {
        val files = listOf(file("current"), file("webm", extension = ".WeBm"), file("native"))

        assertEquals(files[1].preloadKey(), nextMediaPreloadTarget(files, 0, true, true))
        assertEquals(files[2].preloadKey(), nextMediaPreloadTarget(files, 0, true, false))
    }

    @Test
    fun rapidPagingAndSettingsOffReleaseExactlyOneUpcomingResource() {
        val created = mutableListOf<Resource>()
        val released = mutableListOf<Resource>()
        val owner = owner(created, released)
        val first = key("first")
        val second = key("second")

        owner.prepare(first)
        owner.prepare(first)
        assertEquals(1, created.size, "Recomposition must reuse the same preloader")
        owner.prepare(second)
        assertEquals(listOf(created[0]), released)
        assertEquals(second, owner.key)
        owner.clear()
        owner.clear()
        assertEquals(created, released, "Disabling preload or leaving the viewport releases each resource once")
        assertNull(owner.key)
    }

    @Test
    fun promotionDoesNotDisposeTheResourceNowOwnedByTheVisiblePlayer() {
        val created = mutableListOf<Resource>()
        val released = mutableListOf<Resource>()
        val owner = owner(created, released)
        val next = key("next")
        owner.prepare(next)

        val promoted = owner.claim(next)
        assertSame(created.single(), promoted)
        assertEquals(next, promoted?.key)
        assertNull(owner.key)
        owner.prepare(key("later"))
        owner.clear()
        assertEquals(listOf(created[1]), released)
        assertNull(owner.claim(next), "A promoted resource cannot be claimed twice")
    }

    @Test
    fun inspectingPreparationRetainsOwnershipUntilItIsPromoted() {
        val created = mutableListOf<Resource>()
        val released = mutableListOf<Resource>()
        val owner = owner(created, released)
        val next = key("next")
        owner.prepare(next)

        val inspected = owner.peek(next)
        assertSame(created.single(), inspected)
        assertEquals(next, owner.key)
        assertNull(owner.peek(key("other")))
        assertEquals(emptyList(), released)
        assertSame(inspected, owner.claim(next))
        assertNull(owner.peek(next))
        owner.clear()
        assertEquals(emptyList(), released, "The visible player owns the promoted resource")
    }

    @Test
    fun identicalAttachmentIdsAcrossSourcesCannotClaimOrReuseEachOthersResources() {
        val created = mutableListOf<Resource>()
        val released = mutableListOf<Resource>()
        val owner = owner(created, released)
        val one = file("same-id").copy(sourceUrl = "https://first.example/clip.mp4").preloadKey()!!
        val other = file("same-id").copy(sourceUrl = "https://second.example/clip.mp4").preloadKey()!!
        owner.prepare(one)

        assertNull(owner.claim(other))
        assertEquals(one, owner.key)
        owner.prepare(other)
        assertEquals(listOf(created[0]), released)
        assertEquals(other, owner.key)
    }

    @Test
    fun changingTheRendererForTheSameIdAndUrlReleasesTheOldPreparation() {
        val created = mutableListOf<Resource>()
        val released = mutableListOf<Resource>()
        val owner = owner(created, released)
        val native = key("clip")
        val webm = native.copy(extension = "webm")
        owner.prepare(native)
        owner.prepare(webm)

        assertEquals(listOf(created[0]), released)
        assertEquals(webm, owner.key)
    }

    private fun owner(
        created: MutableList<Resource>,
        released: MutableList<Resource>,
    ) = SingleUpcomingMedia(
        create = { key -> Resource(key).also(created::add) },
        release = released::add,
    )

    private fun key(id: String): MediaPreloadKey = file(id).preloadKey()!!

    private class Resource(
        val key: MediaPreloadKey,
    )

    private fun file(
        id: String,
        extension: String = "mp4",
        type: MediaType = MediaType.VIDEO,
    ) = MediaAttachment(
        id = id,
        originalFileName = "$id.$extension",
        extension = extension,
        type = type,
        sourceUrl = "https://example.com/$id.$extension",
        thumbnailUrl = "https://example.com/$id.jpg",
    )
}
