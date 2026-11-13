package com.orbin.app

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.orbin.uinext.NextTheme
import com.orbin.uinext.OrbinFilledButton
import com.orbin.uinext.OrbinLogo
import com.orbin.uinext.reader
import com.orbin.uinext.tokens.ReaderType
import com.orbin.uinext.withReaderFont

/**
 * Shown instead of the app when the running build is older than one that has already run here.
 *
 * There is no "continue anyway" — drawn in Coral chrome.
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
                    .background(reader.background)
                    .windowInsetsPadding(WindowInsets.safeDrawing)
                    .verticalScroll(rememberScrollState())
                    .padding(24.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            OrbinLogo(size = 48.dp, contentDescription = null)
            Text(
                stringResource(R.string.downgrade_blocked_title),
                style = ReaderType.stateTitle.withReaderFont().copy(fontWeight = FontWeight.SemiBold),
                color = reader.ink,
                modifier = Modifier.semantics { heading() },
            )
            Text(
                stringResource(R.string.downgrade_blocked_explanation),
                style = ReaderType.stateBody.withReaderFont(),
                color = reader.inkMuted,
            )
            Text(
                stringResource(
                    R.string.downgrade_blocked_versions,
                    currentVersionCode,
                    highestVersionCode,
                ),
                style = ReaderType.body.withReaderFont(),
                color = reader.inkMuted,
            )
            Text(
                stringResource(R.string.downgrade_blocked_next_step, highestVersionCode),
                style = ReaderType.stateBody.withReaderFont(),
                color = reader.inkMuted,
            )
            OrbinFilledButton(
                label = stringResource(R.string.downgrade_blocked_close),
                onClick = onClose,
                modifier = Modifier.fillMaxWidth(),
            )
        }
    }
}
