package com.orbin.app.pulse

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.orbin.app.R
import com.orbin.core.model.stream.StreamIds
import com.orbin.core.model.stream.StreamLens
import com.orbin.feature.home.SubscribedFeedUiState
import com.orbin.feature.home.SubscribedFeedViewModel
import com.orbin.feature.thread.ThreadUiState
import com.orbin.feature.thread.ThreadViewModel
import com.orbin.uipulse.PulseLineUi
import java.util.Locale

/** The subscribed feed as a Pulse stream. Opening a line opens that thread. */
@Composable
internal fun PulseHomeRoute(
    onOpenThread: (provider: String, board: String, thread: Long, title: String) -> Unit,
    onOpenLens: (StreamLens) -> Unit,
    viewModel: SubscribedFeedViewModel = hiltViewModel(),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val success = state as? SubscribedFeedUiState.Success
    val lines = remember(success) { success?.let { feedLines(it.boards, it.loadedAtMillis) }.orEmpty() }
    val summary =
        when (val current = state) {
            SubscribedFeedUiState.Loading -> stringResource(R.string.pulse_loading)
            is SubscribedFeedUiState.Error -> current.message
            is SubscribedFeedUiState.Success ->
                if (lines.isEmpty()) {
                    stringResource(R.string.pulse_empty_feed)
                } else {
                    pluralStringResource(R.plurals.pulse_summary_threads, lines.size, lines.size)
                }
        }
    PulseShell(
        PulseScreen(
            title = stringResource(R.string.pulse_lens_home),
            summary = summary,
            lines = lines,
            selectedLens = StreamLens.Home,
            showBack = false,
            onLineClick = { id -> openThreadLine(id, lines, onOpenThread) },
            onRefresh = viewModel::refresh,
            onBack = {},
            onOpenLens = onOpenLens,
        ),
    )
}

/** One thread as a Pulse stream, opening post first. */
@Composable
internal fun PulseThreadRoute(
    onBack: () -> Unit,
    onOpenLens: (StreamLens) -> Unit,
    viewModel: ThreadViewModel = hiltViewModel(),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val anonymous = stringResource(R.string.pulse_anonymous)
    val mediaFormat = stringResource(R.string.pulse_media_count)
    val success = state as? ThreadUiState.Success
    val lines =
        remember(success, anonymous, mediaFormat) {
            val words = PulseThreadWords(anonymous) { count -> mediaFormat.format(Locale.getDefault(), count) }
            success?.let { threadLines(it.thread, System.currentTimeMillis(), words) }.orEmpty()
        }
    val summary =
        when (val current = state) {
            ThreadUiState.Loading -> stringResource(R.string.pulse_loading)
            is ThreadUiState.Error -> current.message
            ThreadUiState.Blocked -> stringResource(R.string.pulse_blocked)
            is ThreadUiState.Success -> pluralStringResource(R.plurals.pulse_summary_posts, lines.size, lines.size)
        }
    PulseShell(
        PulseScreen(
            title = stringResource(R.string.pulse_thread),
            summary = summary,
            lines = lines,
            selectedLens = null,
            showBack = true,
            onLineClick = {},
            onRefresh = viewModel::refresh,
            onBack = onBack,
            onOpenLens = onOpenLens,
        ),
    )
}

private fun openThreadLine(
    id: String,
    lines: List<PulseLineUi>,
    onOpenThread: (String, String, Long, String) -> Unit,
) {
    val ref = StreamIds.parseThread(id) ?: return
    val title = lines.firstOrNull { it.id == id }?.title.orEmpty()
    onOpenThread(ref.provider, ref.board, ref.thread, title)
}
