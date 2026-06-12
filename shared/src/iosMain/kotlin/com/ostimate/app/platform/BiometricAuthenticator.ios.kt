package com.ostimate.app.platform

import kotlinx.cinterop.ExperimentalForeignApi
import platform.LocalAuthentication.LAContext
import platform.LocalAuthentication.LAPolicyDeviceOwnerAuthentication
import platform.darwin.dispatch_async
import platform.darwin.dispatch_get_main_queue

actual class BiometricAuthenticator {

    @OptIn(ExperimentalForeignApi::class)
    actual fun authenticate(reason: String, onResult: (BiometricResult) -> Unit) {
        val context = LAContext()
        // Face ID / Touch ID with passcode fallback.
        if (!context.canEvaluatePolicy(LAPolicyDeviceOwnerAuthentication, error = null)) {
            onResult(BiometricResult.NotEnrolled)
            return
        }
        context.evaluatePolicy(LAPolicyDeviceOwnerAuthentication, localizedReason = reason) { success, _ ->
            dispatch_async(dispatch_get_main_queue()) {
                onResult(if (success) BiometricResult.Success else BiometricResult.Failed)
            }
        }
    }
}
