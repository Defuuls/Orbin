package com.orbin.app.command

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.orbin.uinext.Command
import com.orbin.uinext.CommandSheet
import com.orbin.uinext.NextTheme

/**
 * The command surface as it appears over whatever you were reading.
 *
 * Keeps the sheet in `:ui-next` free of any knowledge of routes: it is handed labels and hands
 * back an id, and this maps that id to a destination or an action.
 */
@Composable
fun CommandHost(
    onSelect: (CommandTarget) -> Unit,
    onDismiss: () -> Unit,
    viewModel: CommandViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val byId = state.results.associateBy { it.commandId() }
    NextTheme {
        CommandSheet(
            query = state.query,
            results = state.results.map { it.toCommand() },
            onQueryChange = viewModel::onQueryChange,
            onSelect = { command ->
                byId[command.id]?.let { target ->
                    viewModel.reset()
                    // Locking is not a destination and must work whatever is behind the sheet, so
                    // it is served here instead of being handed to navigation.
                    if (target is CommandTarget.Act && target.action == CommandAction.LOCK_NOW) {
                        viewModel.lockNow()
                        onDismiss()
                    } else {
                        onSelect(target)
                    }
                }
            },
            onDismiss = {
                viewModel.reset()
                onDismiss()
            },
        )
    }
}

/**
 * Stable across a query change, and unique across kinds — a board and a setting can share a label,
 * and a thread title is not unique at all.
 */
internal fun CommandTarget.commandId(): String =
    when (this) {
        is CommandTarget.OpenBoard -> "board:$provider/$board"
        is CommandTarget.OpenThread -> "thread:$provider/$board/$thread"
        is CommandTarget.OpenSetting -> "setting:$settingId"
        is CommandTarget.Go -> "go:${destination.name}"
        is CommandTarget.Act -> if (query.isEmpty()) "do:${action.name}" else "do:${action.name}:$query"
    }

private fun CommandTarget.toCommand() =
    Command(
        label = label,
        kind = kind,
        hint = hint.takeIf { it.isNotBlank() },
        id = commandId(),
    )
