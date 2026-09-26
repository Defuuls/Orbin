package com.orbin.core.ui.device

import android.content.Context
import android.content.pm.PackageManager
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext
import com.orbin.core.model.FormFactor

/**
 * What kind of device this is, from what it reports about itself rather than from the current
 * window, so a foldable stays a foldable when folded and a tablet in split screen stays a tablet.
 *
 * A hinge-angle sensor marks a foldable; every Android foldable that folds its screen reports one.
 * Otherwise a smallest width of 600dp, the size at which Android itself treats a screen as a
 * tablet's, marks a tablet.
 */
fun Context.formFactor(): FormFactor =
    when {
        packageManager.hasSystemFeature(PackageManager.FEATURE_SENSOR_HINGE_ANGLE) -> FormFactor.FOLDABLE
        resources.configuration.smallestScreenWidthDp >= TABLET_SMALLEST_WIDTH_DP -> FormFactor.TABLET
        else -> FormFactor.PHONE
    }

/** [formFactor] for this composition's context; it cannot change while the app runs. */
@Composable
fun rememberFormFactor(): FormFactor {
    val context = LocalContext.current
    return remember(context) { context.formFactor() }
}

private const val TABLET_SMALLEST_WIDTH_DP = 600
