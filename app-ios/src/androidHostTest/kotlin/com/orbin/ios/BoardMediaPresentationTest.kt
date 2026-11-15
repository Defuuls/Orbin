package com.orbin.ios

import com.orbin.core.model.BoardId
import com.orbin.core.model.MediaAttachment
import com.orbin.core.model.MediaType
import com.orbin.core.model.ProviderId
import com.orbin.core.model.ThreadId
import com.orbin.core.model.ThreadKey
import com.orbin.domain.media.AllMediaItem
import com.orbin.uinext.MediaKind
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotEquals

class BoardMediaPresentationTest {
    @Test
    fun boardWallCellsKeepSourceIdentityAndPropagateEveryActualAttachmentType() {
        val file = MediaAttachment("file", "saved.webm", "webm", MediaType.VIDEO, "https://example.org/file", "")
        val item =
            AllMediaItem(file, ThreadKey(ProviderId("fourchan"), BoardId("g"), ThreadId(7)), "Technology", "Thread")
        listOf(
            MediaType.IMAGE to MediaKind.IMAGE,
            MediaType.ANIMATED_IMAGE to MediaKind.IMAGE,
            MediaType.VIDEO to MediaKind.VIDEO,
            MediaType.AUDIO to MediaKind.AUDIO,
            MediaType.UNKNOWN to MediaKind.UNKNOWN,
        ).forEach { (type, kind) ->
            val cell = item.copy(attachment = file.copy(type = type)).toBoardMediaCell("/g/")
            assertEquals(kind, cell.kind)
            assertEquals("fourchan/g/7/file", cell.id)
            assertEquals("fourchan /g/", cell.board)
        }
        assertNotEquals(
            item.toBoardMediaCell("/g/").id,
            item.copy(key = item.key.copy(provider = ProviderId("bbwchan"))).toBoardMediaCell("/g/").id,
        )
    }
}
