package com.orbin.app.navigation

import com.google.common.truth.Truth.assertThat
import com.orbin.core.model.BoardId
import com.orbin.core.model.ProviderId
import com.orbin.core.model.ThreadId
import com.orbin.core.model.ThreadKey
import com.orbin.uinext.screens.ThreadCollectionRow
import org.junit.Test

class ThreadCollectionsTest {
    @Test
    fun sameThreadNumberOnDifferentSitesKeepsItsIdentityAndSourceLabel() {
        val oneKey = ThreadKey(ProviderId("fourchan"), BoardId("g"), ThreadId(7))
        val twoKey = ThreadKey(ProviderId("bbwchan"), BoardId("g"), ThreadId(7))
        val one = CollectionEntry(oneKey, oneKey.toCollectionRow("First"))
        val two = CollectionEntry(twoKey, twoKey.toCollectionRow("Second"))
        val byRow = listOf(one, two).associateBy { it.row.id }

        assertThat(byRow).hasSize(2)
        assertThat(byRow[two.row.id]?.key).isEqualTo(two.key)
        assertThat(one.row.source).isEqualTo("4chan · /g/")
        assertThat(two.row.source).isEqualTo("BBWChan · /g/")
        var opened: ThreadKey? = null
        val onOpen: (ThreadCollectionRow) -> Unit = { row -> opened = byRow[row.id]?.key }
        onOpen(two.row)
        assertThat(opened).isEqualTo(twoKey)
    }
}
