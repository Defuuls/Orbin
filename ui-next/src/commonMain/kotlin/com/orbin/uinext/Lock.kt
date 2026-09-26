package com.orbin.uinext

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.orbin.uinext.resources.Res
import com.orbin.uinext.resources.next_lock_continue_without
import com.orbin.uinext.resources.next_lock_hint
import com.orbin.uinext.resources.next_lock_title
import com.orbin.uinext.resources.next_lock_unlock
import com.orbin.uinext.resources.next_lock_unlocking
import com.orbin.uinext.tokens.NextType
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
    onContinueWithoutLock: (() -> Unit)? = null,
) {
    Column(
        modifier = modifier.fillMaxSize().padding(24.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(stringResource(Res.string.next_lock_title), style = NextType.title2, color = next.ink)
        Text(
            text = message ?: stringResource(Res.string.next_lock_hint),
            modifier = Modifier.padding(top = 8.dp),
            color = next.muted,
            style = NextType.body,
            textAlign = TextAlign.Center,
        )
        if (!unlocking) {
            InlineAction(
                label = stringResource(Res.string.next_lock_unlock),
                accent = true,
                onClick = onUnlock,
                modifier = Modifier.padding(top = 16.dp),
            )
        } else {
            Text(
                stringResource(Res.string.next_lock_unlocking),
                style = NextType.body,
                color = next.muted,
                modifier = Modifier.padding(top = 16.dp),
            )
        }
        if (onContinueWithoutLock != null) {
            InlineAction(label = stringResource(Res.string.next_lock_continue_without), onClick = onContinueWithoutLock)
        }
    }
}
