package com.orbin.app.update

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.res.stringResource
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.orbin.app.R
import com.orbin.data.crypto.LocalDataNotice
import com.orbin.data.crypto.LocalDataNotices

/**
 * Tells the reader, once, when the database key could not be kept: either their local data was
 * reset (with a way to the backup import in Settings) or this session is not being saved.
 */
@Composable
internal fun LocalDataNoticeDialog(
    visible: Boolean,
    onOpenSettings: () -> Unit,
) {
    val notice by LocalDataNotices.current.collectAsStateWithLifecycle()
    if (!visible) return
    when (notice) {
        null -> Unit
        LocalDataNotice.RESET ->
            UpdateCard(
                title = stringResource(R.string.local_data_reset_title),
                message = stringResource(R.string.local_data_reset_message),
                dismissLabel = stringResource(R.string.update_action_close),
                onDismiss = LocalDataNotices::acknowledge,
                confirmLabel = stringResource(R.string.update_action_open_settings),
                onConfirm = {
                    LocalDataNotices.acknowledge()
                    onOpenSettings()
                },
            )
        LocalDataNotice.NOT_SAVED ->
            UpdateCard(
                title = stringResource(R.string.local_data_not_saved_title),
                message = stringResource(R.string.local_data_not_saved_message),
                dismissLabel = stringResource(R.string.update_action_close),
                onDismiss = LocalDataNotices::acknowledge,
            )
    }
}
