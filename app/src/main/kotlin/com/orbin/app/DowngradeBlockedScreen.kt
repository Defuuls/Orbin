package com.orbin.app

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp

/**
 * Shown instead of the app when the running build is older than one that has already run here.
 *
 * There is no "continue anyway" — that is the whole point of the screen, and unlike
 * [SafeModeScreen] there is nothing to recover, only a fact to state. It says which build is
 * running and which one is expected, because "this version can't run" is useless to someone who
 * has to go and find the right APK.
 */
@Composable
fun DowngradeBlockedScreen(
    currentVersionCode: Int,
    highestVersionCode: Int,
    onClose: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Surface(modifier = modifier.fillMaxSize()) {
        Column(
            modifier =
                Modifier
                    .fillMaxSize()
                    .verticalScroll(rememberScrollState())
                    .padding(24.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            Text(
                stringResource(R.string.downgrade_blocked_title),
                style = MaterialTheme.typography.headlineSmall,
            )
            Text(
                stringResource(R.string.downgrade_blocked_explanation),
                style = MaterialTheme.typography.bodyMedium,
            )
            Text(
                stringResource(
                    R.string.downgrade_blocked_versions,
                    currentVersionCode,
                    highestVersionCode,
                ),
                style = MaterialTheme.typography.bodyMedium,
            )
            Text(
                stringResource(R.string.downgrade_blocked_next_step, highestVersionCode),
                style = MaterialTheme.typography.bodyMedium,
            )

            Button(onClick = onClose) { Text(stringResource(R.string.downgrade_blocked_close)) }
        }
    }
}
