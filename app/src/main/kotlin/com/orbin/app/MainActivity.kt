package com.orbin.app

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.compose.rememberNavController
import com.orbin.app.navigation.OrbinNavHost
import com.orbin.core.designsystem.theme.ThemeMode
import com.orbin.core.model.AppThemeMode
import dagger.hilt.android.AndroidEntryPoint

/**
 * Single-activity host. Sets up the splash screen, edge-to-edge layout, and the Compose content,
 * theming the whole tree from persisted settings.
 */
@AndroidEntryPoint
class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        installSplashScreen()
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)

        setContent {
            val viewModel: MainViewModel = hiltViewModel()
            val settings by viewModel.settings.collectAsStateWithLifecycle()

            com.orbin.core.designsystem.theme.OrbinTheme(
                themeMode = settings.themeMode.toDesignSystem(),
                dynamicColor = settings.dynamicColor,
                amoled = settings.amoled,
                fontScale = settings.fontScale,
            ) {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background,
                ) {
                    val navController = rememberNavController()
                    OrbinNavHost(navController = navController)
                }
            }
        }
    }
}

private fun AppThemeMode.toDesignSystem(): ThemeMode =
    when (this) {
        AppThemeMode.SYSTEM -> ThemeMode.SYSTEM
        AppThemeMode.LIGHT -> ThemeMode.LIGHT
        AppThemeMode.DARK -> ThemeMode.DARK
    }
