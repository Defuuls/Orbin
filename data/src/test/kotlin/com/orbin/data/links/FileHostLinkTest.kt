package com.orbin.data.links

import com.google.common.truth.Truth.assertThat
import com.orbin.core.model.LinkStatus
import org.junit.Test

class FileHostLinkTest {
    @Test
    fun `parses gofile download links`() {
        val link = FileHostLink.parse("https://gofile.io/d/Ab12Cd")
        assertThat(link).isEqualTo(FileHostLink.GoFile(contentId = "Ab12Cd"))
    }

    @Test
    fun `parses mega file links`() {
        val link = FileHostLink.parse("https://mega.nz/file/a1B2c3D4#keykeykey")
        assertThat(link).isEqualTo(FileHostLink.Mega(handle = "a1B2c3D4", isFolder = false))
    }

    @Test
    fun `parses mega folder links`() {
        val link = FileHostLink.parse("https://mega.nz/folder/a1B2c3D4#keykeykey")
        assertThat(link).isEqualTo(FileHostLink.Mega(handle = "a1B2c3D4", isFolder = true))
    }

    @Test
    fun `parses legacy mega links`() {
        assertThat(FileHostLink.parse("https://mega.co.nz/#!a1B2c3D4!key"))
            .isEqualTo(FileHostLink.Mega(handle = "a1B2c3D4", isFolder = false))
        assertThat(FileHostLink.parse("https://mega.nz/#F!a1B2c3D4!key"))
            .isEqualTo(FileHostLink.Mega(handle = "a1B2c3D4", isFolder = true))
    }

    @Test
    fun `parses fast-file links`() {
        val url = "https://fast-file.ru/uc2m4nxvglqq/file.zip"
        assertThat(FileHostLink.parse(url)).isEqualTo(FileHostLink.FastFile(url))
    }

    @Test
    fun `rejects unsupported hosts and unrecognized paths`() {
        assertThat(FileHostLink.parse("https://example.com/d/Ab12Cd")).isNull()
        assertThat(FileHostLink.parse("https://gofile.io/welcome")).isNull()
        assertThat(FileHostLink.parse("https://mega.nz/pro")).isNull()
        assertThat(FileHostLink.parse("https://not-fast-file.ru.evil.com/x")).isNull()
    }

    @Test
    fun `mega node object means the link is alive`() {
        val body = """[{"s":1024,"at":"encrypted-attributes","msd":0}]"""
        assertThat(LinkProbeResponses.megaStatus(body)).isEqualTo(LinkStatus.VALID)
    }

    @Test
    fun `mega not-found and takedown codes mean the link is dead`() {
        assertThat(LinkProbeResponses.megaStatus("[-9]")).isEqualTo(LinkStatus.INVALID)
        assertThat(LinkProbeResponses.megaStatus("-9")).isEqualTo(LinkStatus.INVALID)
        assertThat(LinkProbeResponses.megaStatus("[-16]")).isEqualTo(LinkStatus.INVALID)
    }

    @Test
    fun `unexpected mega answers stay unknown`() {
        assertThat(LinkProbeResponses.megaStatus("[-3]")).isEqualTo(LinkStatus.UNKNOWN)
        assertThat(LinkProbeResponses.megaStatus("not json")).isEqualTo(LinkStatus.UNKNOWN)
        assertThat(LinkProbeResponses.megaStatus("[]")).isEqualTo(LinkStatus.UNKNOWN)
    }

    @Test
    fun `gofile ok and protected content are alive`() {
        assertThat(LinkProbeResponses.gofileStatus("""{"status":"ok","data":{}}"""))
            .isEqualTo(LinkStatus.VALID)
        assertThat(LinkProbeResponses.gofileStatus("""{"status":"error-passwordRequired"}"""))
            .isEqualTo(LinkStatus.VALID)
    }

    @Test
    fun `gofile not-found is dead and other errors stay unknown`() {
        assertThat(LinkProbeResponses.gofileStatus("""{"status":"error-notFound"}"""))
            .isEqualTo(LinkStatus.INVALID)
        assertThat(LinkProbeResponses.gofileStatus("""{"status":"error-auth"}"""))
            .isEqualTo(LinkStatus.UNKNOWN)
        assertThat(LinkProbeResponses.gofileStatus("<html>oops</html>")).isEqualTo(LinkStatus.UNKNOWN)
    }

    @Test
    fun `fast-file status codes decide the obvious cases`() {
        assertThat(LinkProbeResponses.fastFileStatus(404, "")).isEqualTo(LinkStatus.INVALID)
        assertThat(LinkProbeResponses.fastFileStatus(410, "")).isEqualTo(LinkStatus.INVALID)
        assertThat(LinkProbeResponses.fastFileStatus(503, "")).isEqualTo(LinkStatus.UNKNOWN)
        assertThat(LinkProbeResponses.fastFileStatus(200, "<html>Download file.zip</html>"))
            .isEqualTo(LinkStatus.VALID)
    }

    @Test
    fun `fast-file dead-page copy marks the link dead`() {
        assertThat(LinkProbeResponses.fastFileStatus(200, "<html>Файл не найден</html>"))
            .isEqualTo(LinkStatus.INVALID)
        assertThat(LinkProbeResponses.fastFileStatus(200, "<html>File was deleted by owner</html>"))
            .isEqualTo(LinkStatus.INVALID)
    }
}
