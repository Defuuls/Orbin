package com.orbin.data.repository

import com.google.common.truth.Truth.assertThat
import com.orbin.core.model.DownloadOrganization
import org.junit.Test

class DownloadOrganizationTest {
    @Test
    fun `flat organization never produces a subfolder`() {
        assertThat(buildRelativeDir(DownloadOrganization.FLAT, "g", 123L, "Some thread")).isEmpty()
    }

    @Test
    fun `by board uses only the board id`() {
        assertThat(buildRelativeDir(DownloadOrganization.BY_BOARD, "g", 123L, "Some thread")).isEqualTo("g/")
    }

    @Test
    fun `by board then thread nests thread under board`() {
        val result = buildRelativeDir(DownloadOrganization.BY_BOARD_THEN_THREAD, "g", 123L, "Some thread")
        assertThat(result).isEqualTo("g/123 - Some thread/")
    }

    @Test
    fun `by thread omits the board`() {
        val result = buildRelativeDir(DownloadOrganization.BY_THREAD, "g", 123L, "Some thread")
        assertThat(result).isEqualTo("123 - Some thread/")
    }

    @Test
    fun `missing thread title falls back to just the thread id`() {
        val result = buildRelativeDir(DownloadOrganization.BY_THREAD, "g", 123L, threadTitle = null)
        assertThat(result).isEqualTo("123/")
    }

    @Test
    fun `missing context yields no subfolder even when organization asks for one`() {
        assertThat(buildRelativeDir(DownloadOrganization.BY_BOARD, boardId = null, threadId = null, threadTitle = null))
            .isEmpty()
        assertThat(buildRelativeDir(DownloadOrganization.BY_THREAD, boardId = "g", threadId = null, threadTitle = null))
            .isEmpty()
    }

    @Test
    fun `by board then thread with only a board falls back to board-only folder`() {
        val result =
            buildRelativeDir(DownloadOrganization.BY_BOARD_THEN_THREAD, "g", threadId = null, threadTitle = null)
        assertThat(result).isEqualTo("g/")
    }

    @Test
    fun `path separators in a thread title cannot smuggle in extra subfolders`() {
        val result = buildRelativeDir(DownloadOrganization.BY_THREAD, "g", 123L, "a/../../etc/passwd")
        assertThat(result).doesNotContain("../")
        assertThat(result.count { it == '/' }).isEqualTo(1)
    }
}
