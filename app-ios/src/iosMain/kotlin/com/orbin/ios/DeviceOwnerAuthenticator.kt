package com.orbin.ios

import kotlinx.coroutines.suspendCancellableCoroutine
import platform.LocalAuthentication.LAContext
import platform.LocalAuthentication.LAPolicyDeviceOwnerAuthentication
import kotlin.coroutines.resume

/**
 * Face ID or Touch ID, falling back to the device passcode, through LocalAuthentication. Android
 * asks for strong biometrics only; iOS's own apps let the passcode stand in, and so does this, so
 * the lock works on a device without Face ID or Touch ID too.
 */
class DeviceOwnerAuthenticator : Authenticator {
    override suspend fun authenticate(): AuthResult =
        suspendCancellableCoroutine { continuation ->
            val context = LAContext()
            continuation.invokeOnCancellation { context.invalidate() }
            context.evaluatePolicy(
                LAPolicyDeviceOwnerAuthentication,
                localizedReason = UNLOCK_REASON,
            ) { success, error ->
                continuation.resume(
                    if (success) {
                        AuthResult.Success
                    } else {
                        AuthResult.Failed(error?.localizedDescription ?: FALLBACK_FAILURE)
                    },
                )
            }
        }
}

/** What the Touch ID and passcode prompts say; Face ID shows the app's NSFaceIDUsageDescription. */
private const val UNLOCK_REASON = "Unlock Orbin"

private const val FALLBACK_FAILURE = "Unlock was canceled. Try again."
