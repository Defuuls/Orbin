package com.orbin.feature.settings

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Scaffold
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.orbin.core.designsystem.component.ModernSmallTopAppBar

/** Thread watch alerts and the quiet hours that suppress them. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsNotificationsScreen(
    onBack: () -> Unit,
    viewModel: SettingsViewModel = hiltViewModel(),
) {
    val settings by viewModel.settings.collectAsStateWithLifecycle()

    Scaffold(
        topBar = {
            ModernSmallTopAppBar(
                title = "Notifications",
                navigationIcon = Icons.AutoMirrored.Filled.ArrowBack,
                onNavigationClick = onBack,
            )
        },
    ) { padding ->
        Column(
            modifier =
                Modifier
                    .fillMaxSize()
                    .padding(padding)
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = 12.dp),
        ) {
            SwitchRow(
                "Thread watch notifications",
                settings.threadWatchNotificationsEnabled,
                viewModel::setThreadWatchNotifications,
                supporting = "Get notified when watched threads have new replies",
            )
            if (settings.threadWatchNotificationsEnabled) {
                TextFieldRow(
                    label = "Quiet hours start",
                    value = settings.quietHoursStart,
                    supporting = "HH:MM format (24-hour), leave empty to disable",
                    onValueChange = viewModel::setQuietHoursStart,
                )
                TextFieldRow(
                    label = "Quiet hours end",
                    value = settings.quietHoursEnd,
                    supporting = "HH:MM format (24-hour), leave empty to disable",
                    onValueChange = viewModel::setQuietHoursEnd,
                )
            }
        }
    }
}
