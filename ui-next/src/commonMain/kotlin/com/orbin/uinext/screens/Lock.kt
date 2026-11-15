package com.orbin.uinext.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.widthIn
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
import com.orbin.uinext.OrbinCard
import com.orbin.uinext.OrbinLogo
import com.orbin.uinext.components.ErrorNote
import com.orbin.uinext.components.OrbinFilledButton
import com.orbin.uinext.components.reader
import com.orbin.uinext.components.withReaderFont
import com.orbin.uinext.resources.Res
import com.orbin.uinext.resources.next_lock_hint
import com.orbin.uinext.resources.next_lock_title
import com.orbin.uinext.resources.next_lock_unlock
import com.orbin.uinext.resources.next_lock_unlocking
import com.orbin.uinext.tokens.OrbinType
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
    Box(
        modifier =
            modifier
                .fillMaxSize()
                .background(reader.background)
                .windowInsetsPadding(WindowInsets.safeDrawing)
                .padding(20.dp),
        contentAlignment = Alignment.Center,
    ) {
        OrbinCard(Modifier.widthIn(max = 480.dp).fillMaxWidth()) {
            Column(
                Modifier.fillMaxWidth().verticalScroll(rememberScrollState()).padding(24.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                OrbinLogo(size = 64.dp, contentDescription = null)
                Text(
                    text = stringResource(Res.string.next_lock_title),
                    style = OrbinType.stateTitle.withReaderFont().copy(fontWeight = FontWeight.ExtraBold),
                    color = reader.ink,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.semantics { heading() },
                )
                val hint = message ?: stringResource(Res.string.next_lock_hint)
                if (failed) {
                    ErrorNote(hint, style = OrbinType.stateBody.withReaderFont(), textAlign = TextAlign.Center)
                } else {
                    Text(
                        text = hint,
                        color = reader.inkMuted,
                        style = OrbinType.stateBody.withReaderFont(),
                        textAlign = TextAlign.Center,
                    )
                }
                if (!unlocking) {
                    OrbinFilledButton(
                        label = stringResource(Res.string.next_lock_unlock),
                        onClick = onUnlock,
                        modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
                    )
                } else {
                    Text(
                        stringResource(Res.string.next_lock_unlocking),
                        style = OrbinType.readerBody.withReaderFont(),
                        color = reader.inkMuted,
                        modifier = Modifier.padding(top = 8.dp),
                    )
                }
            }
        }
    }
}
