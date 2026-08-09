package com.orbin.feature.settings

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import com.orbin.core.designsystem.component.ModernListItem
import com.orbin.core.designsystem.component.ModernSmallTopAppBar
import com.orbin.core.ui.state.EmptyView

/** Searches every setting's label across every sub-screen and deep-links to the matching one. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsSearchScreen(
    onBack: () -> Unit,
    onOpenSection: (SettingsSection) -> Unit,
) {
    var query by remember { mutableStateOf("") }
    val focusManager = LocalFocusManager.current
    val focusRequester = remember { FocusRequester() }
    val results =
        remember(query) {
            if (query.isBlank()) emptyList() else settingsSearchIndex.filter { it.matches(query) }
        }

    LaunchedEffect(Unit) { focusRequester.requestFocus() }

    Scaffold(
        topBar = {
            ModernSmallTopAppBar(
                title = "Search settings",
                navigationIcon = Icons.AutoMirrored.Filled.ArrowBack,
                onNavigationClick = onBack,
            )
        },
    ) { padding ->
        Column(modifier = Modifier.fillMaxSize().padding(padding).padding(horizontal = 12.dp)) {
            OutlinedTextField(
                value = query,
                onValueChange = { query = it },
                label = { Text("Search") },
                singleLine = true,
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                keyboardActions = KeyboardActions(onSearch = { focusManager.clearFocus() }),
                modifier = Modifier.fillMaxWidth().padding(top = 8.dp).focusRequester(focusRequester),
            )

            Box(modifier = Modifier.weight(1f).fillMaxWidth()) {
                when {
                    query.isBlank() ->
                        EmptyView("Search for a setting by name", Modifier.fillMaxSize())
                    results.isEmpty() ->
                        EmptyView("No settings match \"$query\"", Modifier.fillMaxSize())
                    else ->
                        LazyColumn {
                            items(results, key = { "${it.section}/${it.label}" }) { entry ->
                                ModernListItem(
                                    title = entry.label,
                                    subtitle = entry.section.title,
                                    onClick = { onOpenSection(entry.section) },
                                    modifier = Modifier.padding(vertical = 2.dp),
                                )
                            }
                        }
                }
            }
        }
    }
}
