package com.orbin.data.update

import android.content.ActivityNotFoundException
import android.content.Context
import android.content.SharedPreferences
import androidx.core.content.edit
import com.orbin.core.common.dispatchers.ApplicationScope
import com.orbin.core.common.dispatchers.Dispatcher
import com.orbin.core.common.dispatchers.OrbinDispatcher
import com.orbin.core.common.result.OrbinResult
import com.orbin.core.model.AppUpdateState
import com.orbin.core.model.UpdateStatus
import com.orbin.domain.repository.AppUpdater
import com.orbin.domain.repository.UpdateRepository
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.io.IOException
import java.net.SocketTimeoutException
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.math.roundToInt

/**
 * Checks for, downloads, verifies and installs updates, and remembers the reader's choices.
 *
 * An APK reaches the system installer only after it has matched the release's published SHA-256
 * *and* [ApkInstaller.verify] has confirmed it is a newer Orbin signed with the running build's
 * key. The installer still asks the reader to confirm; nothing installs silently.
 */
@Singleton
class AppUpdaterImpl
    @Inject
    constructor(
        @ApplicationContext context: Context,
        private val updateRepository: UpdateRepository,
        private val downloader: ApkDownloader,
        private val installer: ApkInstaller,
        @ApplicationScope private val scope: CoroutineScope,
        @Dispatcher(OrbinDispatcher.IO) private val ioDispatcher: CoroutineDispatcher,
    ) : AppUpdater {
        private val prefs: SharedPreferences = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        private val _state = MutableStateFlow<AppUpdateState>(AppUpdateState.Idle)
        private val _checkOnLaunch = MutableStateFlow(prefs.getBoolean(KEY_CHECK_ON_LAUNCH, true))
        private var downloadJob: Job? = null
        private var downloaded: File? = null

        override val state: StateFlow<AppUpdateState> = _state.asStateFlow()
        override val checkOnLaunch: StateFlow<Boolean> = _checkOnLaunch.asStateFlow()

        override fun setCheckOnLaunch(enabled: Boolean) {
            prefs.edit { putBoolean(KEY_CHECK_ON_LAUNCH, enabled) }
            _checkOnLaunch.value = enabled
        }

        override suspend fun checkOnLaunch(currentVersionName: String) {
            // An update that installed leaves its APK behind; the next launch is the first moment
            // it is certainly no longer needed.
            if (_state.value == AppUpdateState.Idle) {
                withContext(ioDispatcher) { installer.updatesDir.deleteRecursively() }
            }
            val now = System.currentTimeMillis()
            if (!shouldCheckOnLaunch(_checkOnLaunch.value, prefs.getLong(KEY_LAST_CHECK, 0L), now)) return
            val result = updateRepository.checkForUpdate(currentVersionName)
            // A failed check is retried on the next launch rather than waiting out the interval.
            if (result !is OrbinResult.Success) return
            prefs.edit { putLong(KEY_LAST_CHECK, now) }
            val release = result.data as? UpdateStatus.Available ?: return
            if (release.tag == prefs.getString(KEY_DISMISSED_TAG, null)) return
            if (_state.value == AppUpdateState.Idle) _state.value = AppUpdateState.Available(release)
        }

        override fun offer(release: UpdateStatus.Available) {
            if (_state.value !is AppUpdateState.Downloading) _state.value = AppUpdateState.Available(release)
        }

        override fun update(release: UpdateStatus.Available) {
            val apkUrl = release.apkUrl
            val checksumUrl = release.checksumUrl
            if (apkUrl == null || checksumUrl == null) {
                _state.value = AppUpdateState.Failed(release, "This release has no app to install")
                return
            }
            downloadJob?.cancel()
            _state.value = AppUpdateState.Downloading(release, null)
            downloadJob =
                scope.launch(ioDispatcher) {
                    try {
                        installer.updatesDir.deleteRecursively()
                        var shownPercent = -1
                        val target = File(installer.updatesDir, "${release.tag}.apk")
                        val apk =
                            downloader.download(apkUrl, checksumUrl, target) {
                                // One emission per percent, not per 64 KB buffer.
                                val percent = it?.let { fraction -> (fraction * PERCENT).roundToInt() } ?: -1
                                if (percent != shownPercent) {
                                    shownPercent = percent
                                    _state.value = AppUpdateState.Downloading(release, it)
                                }
                            }
                        installer.verify(apk)
                        downloaded = apk
                        handOff(release, apk)
                    } catch (cancelled: CancellationException) {
                        throw cancelled
                    } catch (
                        @Suppress("TooGenericExceptionCaught") error: Exception,
                    ) {
                        _state.value = AppUpdateState.Failed(release, error.readerMessage())
                    }
                }
        }

        override fun cancel() {
            downloadJob?.cancel()
            downloadJob = null
            _state.value = AppUpdateState.Idle
        }

        override fun dismiss() {
            (_state.value as? AppUpdateState.Available)?.let { available ->
                prefs.edit { putString(KEY_DISMISSED_TAG, available.release.tag) }
            }
            _state.value = AppUpdateState.Idle
        }

        override fun openInstallPermissionSettings() {
            try {
                installer.openInstallPermissionSettings()
            } catch (_: ActivityNotFoundException) {
                // Some builds of Android hide the per-app screen; the installer asks for the
                // permission itself when Install is tapped.
            }
        }

        override fun installDownloaded() {
            val waiting = _state.value as? AppUpdateState.NeedsInstallPermission ?: return
            val apk = downloaded?.takeIf(File::exists)
            if (apk == null) {
                // The cache was cleared while the reader was in system settings.
                update(waiting.release)
                return
            }
            handOff(waiting.release, apk)
        }

        private fun handOff(
            release: UpdateStatus.Available,
            apk: File,
        ) {
            if (installer.canInstall()) {
                try {
                    installer.install(apk)
                    _state.value = AppUpdateState.Idle
                } catch (_: ActivityNotFoundException) {
                    _state.value = AppUpdateState.Failed(release, "This device has no app installer")
                }
            } else {
                _state.value = AppUpdateState.NeedsInstallPermission(release)
            }
        }

        private companion object {
            const val PREFS_NAME = "orbin_updates"
            const val KEY_CHECK_ON_LAUNCH = "check_on_launch"
            const val KEY_LAST_CHECK = "last_check_millis"
            const val KEY_DISMISSED_TAG = "dismissed_tag"
            const val PERCENT = 100
        }
    }

/** Launch checks run at most this often, so opening the app is not a GitHub request every time. */
internal const val LAUNCH_CHECK_INTERVAL_MS = 24 * 60 * 60 * 1000L

/**
 * Whether a launch should look for an update. A last check in the future means the clock moved
 * back, which would otherwise suppress the check for as long as it takes to catch up.
 */
internal fun shouldCheckOnLaunch(
    enabled: Boolean,
    lastCheckMillis: Long,
    nowMillis: Long,
): Boolean = enabled && (nowMillis - lastCheckMillis >= LAUNCH_CHECK_INTERVAL_MS || lastCheckMillis > nowMillis)

private fun Exception.readerMessage(): String =
    when (this) {
        is UpdateVerificationException -> message ?: "The update couldn't be verified"
        is SocketTimeoutException -> "The download timed out"
        is IOException -> "Couldn't download the update. Check your connection and try again"
        else -> "The update failed"
    }
