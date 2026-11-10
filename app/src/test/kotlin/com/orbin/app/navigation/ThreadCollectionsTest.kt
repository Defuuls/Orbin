package com.orbin.app.navigation

import com.google.common.truth.Truth.assertThat
import com.orbin.core.model.BoardId
import com.orbin.core.model.ProviderId
import com.orbin.core.model.ThreadId
import com.orbin.core.model.ThreadKey
import org.junit.Test

class ThreadCollectionsTest {
    @Test
    fun sameThreadNumberOnDifferentSitesKeepsItsIdentityAndSourceLabel() {
        val one = CollectionEntry(ThreadKey(ProviderId("fourchan"), BoardId("g"), ThreadId(7)), "First")
        val two = CollectionEntry(ThreadKey(ProviderId("bbwchan"), BoardId("g"), ThreadId(7)), "Second")
        val byRow = listOf(one, two).associateBy { it.row.id }

        assertThat(byRow).hasSize(2)
        assertThat(byRow[two.row.id]?.key).isEqualTo(two.key)
        assertThat(one.row.source).isEqualTo("4chan · /g/")
        assertThat(two.row.source).isEqualTo("BBWChan · /g/")
    }
}
