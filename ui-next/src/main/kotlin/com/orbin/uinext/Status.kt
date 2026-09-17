package com.orbin.uinext

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.orbin.uinext.tokens.NextSpace
import com.orbin.uinext.tokens.NextType

/** Centered quiet spinner for in-pane loading (Search / Onboarding boards). */
@Composable
fun NextLoading(modifier: Modifier = Modifier) {
    val label = stringResource(R.string.next_status_loading)
    Box(
        modifier = modifier.fillMaxSize().semantics { contentDescription = label },
        contentAlignment = Alignment.Center,
    ) {
        CircularProgressIndicator(
            modifier = Modifier.size(28.dp),
            color = next.accent,
            trackColor = next.hairline,
            strokeWidth = 2.5.dp,
        )
    }
}

/** Centered empty copy without Material icons or buttons. */
@Composable
fun NextEmpty(
    message: String,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier.fillMaxSize().padding(NextSpace.gutter),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(
            text = message,
            style = NextType.body,
            color = next.muted,
            textAlign = TextAlign.Center,
        )
    }
}

/** Centered error copy with optional Retry InlineAction. */
@Composable
fun NextError(
    message: String,
    modifier: Modifier = Modifier,
    onRetry: (() -> Unit)? = null,
) {
    Column(
        modifier = modifier.fillMaxSize().padding(NextSpace.gutter),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(
            text = message,
            style = NextType.body,
            color = next.muted,
            textAlign = TextAlign.Center,
        )
        if (onRetry != null) {
            InlineAction(
                label = stringResource(R.string.next_status_retry),
                accent = true,
                onClick = onRetry,
                modifier = Modifier.padding(top = 12.dp),
            )
        }
    }
}

/**
 * Settings-style On/Off control. Prefer this over Material Switch so preference rows match the
 * grouped Settings language (word toggles, not thumb tracks).
 */
@Composable
fun NextToggle(
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    modifier: Modifier = Modifier,
) {
    InlineAction(
        label =
            if (checked) {
                stringResource(R.string.next_toggle_on)
            } else {
                stringResource(R.string.next_toggle_off)
            },
        accent = checked,
        selected = checked,
        onClick = { onCheckedChange(!checked) },
        modifier = modifier,
    )
}
