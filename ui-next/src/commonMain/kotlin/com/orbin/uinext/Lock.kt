package com.orbin.uinext

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.orbin.uinext.resources.Res
import com.orbin.uinext.resources.next_lock_hint
import com.orbin.uinext.resources.next_lock_title
import com.orbin.uinext.resources.next_lock_unlock
import com.orbin.uinext.resources.next_lock_unlocking
import com.orbin.uinext.tokens.ReaderType
import org.jetbrains.compose.resources.stringResource

/**
 * What covers the app while the app lock is on: why, the last attempt's [message] if it failed,
 * and a way to try again ([onUnlock]) unless an attempt is [unlocking] now. The caller draws it
 * over everything, on the theme's background.
 */
@Composable
fun LockScreen(
    message: String?,
    unlocking: Boolean,
    onUnlock: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val failed = !message.isNullOrBlank()
    Column(
        modifier =
            modifier
                .fillMaxSize()
                .background(reader.background)
                .windowInsetsPadding(WindowInsets.safeDrawing)
                .verticalScroll(rememberScrollState())
                .padding(24.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        OrbinLogo(size = 72.dp, tint = reader.accent, contentDescription = null)
        Text(
            text = stringResource(Res.string.next_lock_title),
            style = ReaderType.stateTitle.withReaderFont().copy(fontWeight = FontWeight.SemiBold),
            color = reader.ink,
            textAlign = TextAlign.Center,
            modifier = Modifier.padding(top = 20.dp).semantics { heading() },
        )
        Text(
            text = message ?: stringResource(Res.string.next_lock_hint),
            modifier = Modifier.padding(top = 8.dp),
            color = if (failed) reader.error else reader.inkMuted,
            style = ReaderType.stateBody.withReaderFont(),
            textAlign = TextAlign.Center,
        )
        if (!unlocking) {
            OrbinFilledButton(
                label = stringResource(Res.string.next_lock_unlock),
                onClick = onUnlock,
                modifier = Modifier.padding(top = 24.dp),
            )
        } else {
            Text(
                stringResource(Res.string.next_lock_unlocking),
                style = ReaderType.body.withReaderFont(),
                color = reader.inkMuted,
                modifier = Modifier.padding(top = 24.dp),
            )
        }
    }
}
