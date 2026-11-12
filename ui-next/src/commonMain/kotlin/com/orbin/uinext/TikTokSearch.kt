package com.orbin.uinext

import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.orbin.uinext.resources.Res
import com.orbin.uinext.resources.next_search_idle_hint
import com.orbin.uinext.resources.next_search_no_matches
import com.orbin.uinext.resources.next_search_query_label
import com.orbin.uinext.resources.social_settings_back
import com.orbin.uinext.resources.tiktok_search_all
import com.orbin.uinext.resources.tiktok_search_submit
import com.orbin.uinext.tokens.NextType
import org.jetbrains.compose.resources.stringResource

/** Compact reader search over the actual source-qualified cross-site catalog results. */
@Composable
internal fun TikTokSearchScreen(
    query: String,
    onQueryChange: (String) -> Unit,
    onSearch: () -> Unit,
    state: SearchState,
    onOpenRow: (SearchRow) -> Unit,
    modifier: Modifier,
    onBack: (() -> Unit)?,
) {
    var selectedSource by rememberSaveable { mutableStateOf<String?>(null) }
    val rows = (state as? SearchState.Results)?.rows.orEmpty()
    val sources =
        remember(rows) {
            rows.mapNotNull { it.searchSource }.distinct().sortedBy { if (it == "fourchan") "" else it }
        }
    LaunchedEffect(sources) {
        if (selectedSource !in sources) selectedSource = null
    }
    val visibleRows = remember(rows, selectedSource) { filterSearchRows(rows, selectedSource) }

    Column(
        modifier =
            modifier
                .fillMaxSize()
                .background(next.background)
                .windowInsetsPadding(
                    WindowInsets.safeDrawing.only(
                        if (LocalReaderFramePresent.current) {
                            WindowInsetsSides.Horizontal
                        } else {
                            WindowInsetsSides.Top +
                                WindowInsetsSides.Horizontal
                        },
                    ),
                ),
    ) {
        SearchNavigationBar(query, onQueryChange, onSearch, onBack)
        if (state is SearchState.Results && sources.isNotEmpty()) {
            SearchSourceTabs(sources, selectedSource) { selectedSource = it }
        }
        Hairline()
        Box(Modifier.weight(1f)) {
            SearchResults(
                state = if (state is SearchState.Results) SearchState.Results(visibleRows) else state,
                onOpenRow = onOpenRow,
            )
        }
    }
}

@Composable
private fun SearchNavigationBar(
    query: String,
    onQueryChange: (String) -> Unit,
    onSearch: () -> Unit,
    onBack: (() -> Unit)?,
) {
    val keyboard = LocalSoftwareKeyboardController.current
    val submit = {
        keyboard?.hide()
        onSearch()
    }
    BoxWithConstraints(Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 4.dp)) {
        val stackAction = maxWidth < 360.dp && LocalDensity.current.fontScale >= 1.6f
        Column {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                if (onBack != null && !LocalReaderFramePresent.current) {
                    NextIconAction(
                        ReaderIcons.Back,
                        stringResource(Res.string.social_settings_back),
                        onBack,
                        tint = next.ink,
                    )
                }
                SchematicSearch(
                    value = query,
                    onValueChange = onQueryChange,
                    placeholder = stringResource(Res.string.next_search_query_label),
                    onSearch = submit,
                    modifier = Modifier.weight(1f),
                )
                if (!stackAction) SearchSubmit(submit)
            }
            if (stackAction) {
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) { SearchSubmit(submit) }
            }
        }
    }
}

@Composable
private fun SearchSubmit(onSearch: () -> Unit) {
    Box(
        Modifier
            .heightIn(min = MIN_TOUCH_TARGET)
            .widthIn(min = 64.dp)
            .nextClickable(role = Role.Button, onClick = onSearch)
            .padding(horizontal = 8.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            stringResource(Res.string.tiktok_search_submit),
            style = NextType.body.copy(fontWeight = FontWeight.SemiBold),
            color = reader.accent,
        )
    }
}

@Composable
private fun SearchSourceTabs(
    sources: List<String>,
    selected: String?,
    onSelect: (String?) -> Unit,
) {
    Row(
        Modifier
            .fillMaxWidth()
            .horizontalScroll(rememberScrollState())
            .selectableGroup(),
    ) {
        val tabs = listOf(null to stringResource(Res.string.tiktok_search_all)) + sources.map { it to sourceLabel(it) }
        tabs.forEach { (source, label) ->
            val active = source == selected
            Column(
                Modifier
                    .selectable(selected = active, role = Role.Tab, onClick = { onSelect(source) })
                    .padding(horizontal = 20.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Box(Modifier.heightIn(min = MIN_TOUCH_TARGET), contentAlignment = Alignment.Center) {
                    Text(
                        label,
                        style = NextType.body.copy(fontWeight = if (active) FontWeight.SemiBold else FontWeight.Normal),
                        color = if (active) next.ink else next.muted,
                    )
                }
                Box(
                    Modifier
                        .widthIn(min = 32.dp)
                        .height(2.dp)
                        .background(if (active) next.ink else Color.Transparent),
                )
            }
        }
    }
}

@Composable
private fun SearchResults(
    state: SearchState,
    onOpenRow: (SearchRow) -> Unit,
) {
    when (state) {
        SearchState.Idle -> NextEmpty(stringResource(Res.string.next_search_idle_hint))
        SearchState.Loading -> NextLoading()
        is SearchState.Error -> NextError(state.message)
        is SearchState.Results ->
            if (state.rows.isEmpty()) {
                NextEmpty(stringResource(Res.string.next_search_no_matches))
            } else {
                Box(Modifier.fillMaxSize()) {
                    val wideWindow = readerRecordUsesWideText()
                    LazyColumn(
                        modifier = Modifier.fillMaxSize().testTag(ReaderSearchTags.LIST),
                        contentPadding = PaddingValues(bottom = bottomInset()),
                    ) {
                        items(state.rows, key = { it.id }) { row ->
                            SearchResultRow(row, wideWindow, onClick = { onOpenRow(row) })
                        }
                    }
                }
            }
    }
}

object ReaderSearchTags {
    const val LIST = "reader-search-results"

    fun row(id: String): String = "reader-search-row:$id"
}

@Composable
private fun SearchResultRow(
    row: SearchRow,
    wideWindow: Boolean,
    onClick: () -> Unit,
) {
    ReaderRecordRow(
        title = row.title,
        source = row.board,
        detail = row.snippet,
        wideWindow = wideWindow,
        onOpen = onClick,
        modifier = Modifier.testTag(ReaderSearchTags.row(row.id)),
    )
}

/** A source tab filters complete qualified identities without coalescing equal thread numbers. */
internal fun filterSearchRows(
    rows: List<SearchRow>,
    source: String?,
): List<SearchRow> = if (source == null) rows else rows.filter { it.searchSource == source }

private val SearchRow.searchSource: String?
    get() {
        val parts = id.split('/')
        if (parts.size < 3 || parts.take(3).any(String::isBlank)) return null
        return when (val source = parts[0].lowercase()) {
            "4chan" -> "fourchan"
            else -> source
        }
    }
