package com.orbin.feature.settings

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.orbin.uinext.NextTheme
import com.orbin.uinext.SettingItem
import com.orbin.uinext.SettingKind
import com.orbin.uinext.SettingsScreen

/**
 * Every setting on one screen, wired to the same [SettingsViewModel] the seven category screens use.
 *
 * Toggles flip in place and choices open under their own row, so most of the list is editable
 * without going anywhere. The remainder — tags, quiet hours, timeouts, the download folder, the
 * cache limit, font size and the user agent — need a keyboard, a time picker or a file picker, and
 * those go to the category screen that already has the right editor. That is a smaller set than
 * the seven screens it replaces, and the trip is now the exception rather than the rule.
 */
@Composable
fun NextSettingsScreen(
    onOpenSection: (SettingsSection) -> Unit,
    onOpenCommands: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: SettingsViewModel = hiltViewModel(),
) {
    val settings by viewModel.settings.collectAsStateWithLifecycle()
    var expanded by rememberSaveable { mutableStateOf<String?>(null) }

    val model = remember(settings) { buildSettings(settings, viewModel) }

    NextTheme {
        SettingsScreen(
            groups = model.groups,
            subtitle = "${model.count} of them, in one list",
            expandedId = expanded,
            onSearch = onOpenCommands,
            onActivate = { item ->
                when (item.kind) {
                    SettingKind.TOGGLE -> model.toggle(item.id)
                    // Pressing an open choice closes it again, so a row is never a one-way door.
                    SettingKind.CHOICE -> expanded = if (expanded == item.id) null else item.id
                    SettingKind.LINK -> onOpenSection(item.section())
                }
            },
            onSelectOption = { item, index ->
                model.choose(item.id, index)
                expanded = null
            },
            modifier = modifier,
        )
    }
}

/**
 * Which category screen owns a link row's editor.
 *
 * Explicit rather than derived from the row's group, because the groups here are the reader's
 * waypoints and the sections are the old screens: they mostly agree, and where they do not, the
 * editor's location wins.
 */
private fun SettingItem.section(): SettingsSection =
    when (id) {
        "hiddenTags", "mutedTags" -> SettingsSection.CONTENT
        "quietStart", "quietEnd" -> SettingsSection.NOTIFICATIONS
        "fontScale" -> SettingsSection.APPEARANCE
        "userAgent", "connectTimeout", "readTimeout" -> SettingsSection.PRIVACY
        "cacheLimit", "downloadFolder" -> SettingsSection.STORAGE
        else -> SettingsSection.ADVANCED
    }
