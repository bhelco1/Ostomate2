package com.ostimate.app.platform

import androidx.biometric.BiometricManager
import androidx.biometric.BiometricManager.Authenticators.BIOMETRIC_STRONG
import androidx.biometric.BiometricManager.Authenticators.DEVICE_CREDENTIAL
import androidx.biometric.BiometricPrompt
import androidx.core.content.ContextCompat
import androidx.fragment.app.FragmentActivity
import java.lang.ref.WeakReference

/**
 * BiometricPrompt needs the foreground FragmentActivity; the androidApp shell registers
 * it here from onCreate/onDestroy. TODO(Phase 1): replace with activity-scoped DI.
 */
object CurrentActivityHolder {
    private var ref: WeakReference<FragmentActivity>? = null
    var activity: FragmentActivity?
        get() = ref?.get()
        set(value) {
            ref = value?.let { WeakReference(it) }
        }
}

actual class BiometricAuthenticator {

    private val authenticators = BIOMETRIC_STRONG or DEVICE_CREDENTIAL

    actual fun authenticate(reason: String, onResult: (BiometricResult) -> Unit) {
        val activity = CurrentActivityHolder.activity
        if (activity == null) {
            onResult(BiometricResult.Failed)
            return
        }

        when (BiometricManager.from(activity).canAuthenticate(authenticators)) {
            BiometricManager.BIOMETRIC_SUCCESS -> Unit // proceed to prompt
            BiometricManager.BIOMETRIC_ERROR_NONE_ENROLLED,
            BiometricManager.BIOMETRIC_ERROR_NO_HARDWARE,
            BiometricManager.BIOMETRIC_ERROR_HW_UNAVAILABLE,
            -> {
                onResult(BiometricResult.NotEnrolled)
                return
            }
            else -> {
                onResult(BiometricResult.Failed)
                return
            }
        }

        val prompt = BiometricPrompt(
            activity,
            ContextCompat.getMainExecutor(activity),
            object : BiometricPrompt.AuthenticationCallback() {
                override fun onAuthenticationSucceeded(result: BiometricPrompt.AuthenticationResult) {
                    onResult(BiometricResult.Success)
                }

                override fun onAuthenticationError(errorCode: Int, errString: CharSequence) {
                    onResult(BiometricResult.Failed)
                }
            },
        )
        // No negative button: DEVICE_CREDENTIAL provides the fallback action.
        val promptInfo = BiometricPrompt.PromptInfo.Builder()
            .setTitle(reason)
            .setAllowedAuthenticators(authenticators)
            .build()
        prompt.authenticate(promptInfo)
    }
}
