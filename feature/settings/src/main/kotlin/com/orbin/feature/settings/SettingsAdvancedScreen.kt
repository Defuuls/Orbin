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

private val CONNECT_TIMEOUTS_SECONDS = listOf(10L, 15L, 30L, 60L)
private val READ_TIMEOUTS_SECONDS = listOf(15L, 30L, 60L, 120L)

/**
 * Network internals for constrained or unusual connections. Split out from Privacy & Network
 * because the defaults suit almost everyone — this page exists for the exception, not the rule.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsAdvancedScreen(
    onBack: () -> Unit,
    viewModel: SettingsViewModel = hiltViewModel(),
) {
    val settings by viewModel.settings.collectAsStateWithLifecycle()

    Scaffold(
        topBar = {
            ModernSmallTopAppBar(
                title = "Advanced",
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
            TextFieldRow(
                label = "Custom user agent",
                value = settings.userAgent,
                supporting = "Sent with every request. Leave empty to use Orbin's default.",
                onValueChange = viewModel::setUserAgent,
            )
            ChipChoiceRow(
                label = "Connect timeout",
                values = CONNECT_TIMEOUTS_SECONDS,
                selected = settings.connectTimeoutSeconds,
                text = { "$it s" },
                onChange = viewModel::setConnectTimeout,
            )
            ChipChoiceRow(
                label = "Read timeout",
                values = READ_TIMEOUTS_SECONDS,
                selected = settings.readTimeoutSeconds,
                text = { "$it s" },
                onChange = viewModel::setReadTimeout,
            )
            SwitchRow(
                "Check certificate revocation",
                !settings.disableOcspChecking,
                viewModel::setCertificateRevocationChecks,
                supporting =
                    "Asks each certificate authority whether a site's certificate has been revoked. " +
                        "Off by default: the check is slow and many networks block it, which shows up " +
                        "as sites failing to load rather than as a warning.",
            )
            SupportingNote(
                "Timeouts and revocation checking are applied when the network client is built, " +
                    "so changes to them take effect the next time Orbin starts.",
            )
        }
    }
}
