package com.orbin.ios

import com.orbin.core.model.MediaAttachment
import com.orbin.core.model.MediaType
import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class PlayerTest {
    @Test
    fun videoAndAudioTheSystemPlayerHandlesPlayInTheViewer() {
        assertTrue(file("mp4", MediaType.VIDEO).playsInApp)
        assertTrue(file(".MOV", MediaType.VIDEO).playsInApp, "any case, with or without the dot")
        assertTrue(file("mp3", MediaType.AUDIO).playsInApp)
    }

    @Test
    fun webmAndImagesDoNot() {
        assertFalse(file("webm", MediaType.VIDEO).playsInApp, "AVFoundation has no WebM")
        assertFalse(file("mp4", MediaType.IMAGE).playsInApp, "only video and audio are played")
        assertFalse(file("swf", MediaType.UNKNOWN).playsInApp)
    }

    private fun file(
        extension: String,
        type: MediaType,
    ) = MediaAttachment(
        id = "1",
        originalFileName = "clip$extension",
        extension = extension,
        type = type,
        sourceUrl = "https://i.example/1$extension",
        thumbnailUrl = "https://i.example/1s.jpg",
    )
}
