package com.orbin.uinext.components

import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import com.orbin.uinext.tokens.OrbinRadius
import com.orbin.uinext.tokens.OrbinType

/**
 * Modern Material 3 text field for forms.
 */
@Composable
fun NextTextField(
    value: String,
    onValueChange: (String) -> Unit,
    modifier: Modifier = Modifier,
    label: String? = null,
    placeholder: String? = null,
    enabled: Boolean = true,
    readOnly: Boolean = false,
    singleLine: Boolean = true,
    keyboardOptions: KeyboardOptions = KeyboardOptions.Default,
    keyboardActions: KeyboardActions = KeyboardActions.Default,
    leading: @Composable (() -> Unit)? = null,
    trailing: @Composable (() -> Unit)? = null,
) {
    val shape = RoundedCornerShape(OrbinRadius.control)
    OutlinedTextField(
        value = value,
        onValueChange = onValueChange,
        modifier =
            modifier
                .readerTextEntry()
                .fillMaxWidth()
                .semantics {
                    if (label != null) contentDescription = label
                },
        enabled = enabled,
        readOnly = readOnly,
        textStyle = OrbinType.body.copy(color = next.ink),
        label = label?.let { { Text(it, style = OrbinType.sectionHeader) } },
        placeholder = placeholder?.let { { Text(it, style = OrbinType.body, color = next.faint) } },
        leadingIcon = leading,
        trailingIcon = trailing,
        singleLine = singleLine,
        keyboardOptions = keyboardOptions,
        keyboardActions = keyboardActions,
        shape = shape,
        colors =
            OutlinedTextFieldDefaults.colors(
                focusedContainerColor = reader.surfaceVariant,
                unfocusedContainerColor = reader.surfaceVariant,
                disabledContainerColor = reader.surfaceVariant,
                focusedBorderColor = next.accent,
                unfocusedBorderColor = next.hairline,
                focusedTextColor = next.ink,
                unfocusedTextColor = next.ink,
                cursorColor = next.accent,
            ),
    )
}
