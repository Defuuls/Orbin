package com.orbin.uinext.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import com.orbin.uinext.resources.Res
import com.orbin.uinext.resources.next_confirm_cancel
import com.orbin.uinext.resources.reader_open_thread_link
import com.orbin.uinext.resources.reader_thread_link_hint
import com.orbin.uinext.resources.reader_thread_link_invalid
import com.orbin.uinext.resources.reader_thread_link_label
import com.orbin.uinext.resources.reader_thread_link_open
import org.jetbrains.compose.resources.stringResource

/** Explicit paste input; a host validates its registered sources before navigating. */
@Composable
fun OpenThreadLinkDialog(
    onDismiss: () -> Unit,
    onOpen: (String) -> Boolean,
) {
    var draft by rememberSaveable { mutableStateOf("") }
    var invalid by rememberSaveable { mutableStateOf(false) }
    val keyboard = LocalSoftwareKeyboardController.current
    val submit = {
        if (onOpen(draft.trim())) {
            keyboard?.hide()
            onDismiss()
        } else {
            invalid = true
        }
    }
    OrbinDialog(onDismissRequest = onDismiss) {
        OrbinDialogTitle(stringResource(Res.string.reader_open_thread_link))
        NextTextField(
            value = draft,
            onValueChange = {
                draft = it
                invalid = false
            },
            label = stringResource(Res.string.reader_thread_link_label),
            placeholder = stringResource(Res.string.reader_thread_link_hint),
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Uri, imeAction = ImeAction.Go),
            keyboardActions = KeyboardActions(onGo = { submit() }),
        )
        if (invalid) {
            ErrorNote(stringResource(Res.string.reader_thread_link_invalid))
        }
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
            OrbinTextButton(stringResource(Res.string.next_confirm_cancel), onDismiss)
            OrbinFilledButton(stringResource(Res.string.reader_thread_link_open), submit, enabled = draft.isNotBlank())
        }
    }
}
