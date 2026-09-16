package com.orbin.app

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.orbin.uinext.InlineAction
import com.orbin.uinext.NextTheme
import com.orbin.uinext.next
import com.orbin.uinext.tokens.NextType

/**
 * Shown instead of the app when the running build is older than one that has already run here.
 *
 * There is no "continue anyway" — drawn in the Next language.
 */
@Composable
fun DowngradeBlockedScreen(
    currentVersionCode: Int,
    highestVersionCode: Int,
    onClose: () -> Unit,
    modifier: Modifier = Modifier,
) {
    NextTheme {
        Column(
            modifier =
                modifier
                    .fillMaxSize()
                    .verticalScroll(rememberScrollState())
                    .padding(24.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            Text(
                stringResource(R.string.downgrade_blocked_title),
                style = NextType.title2,
                color = next.ink,
            )
            Text(
                stringResource(R.string.downgrade_blocked_explanation),
                style = NextType.body,
                color = next.muted,
            )
            Text(
                stringResource(
                    R.string.downgrade_blocked_versions,
                    currentVersionCode,
                    highestVersionCode,
                ),
                style = NextType.body,
                color = next.muted,
            )
            Text(
                stringResource(R.string.downgrade_blocked_next_step, highestVersionCode),
                style = NextType.body,
                color = next.muted,
            )
            InlineAction(
                label = stringResource(R.string.downgrade_blocked_close),
                accent = true,
                onClick = onClose,
            )
        }
    }
}
