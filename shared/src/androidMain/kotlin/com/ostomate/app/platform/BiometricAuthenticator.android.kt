package com.ostomate.app.platform

import android.os.Build
import androidx.biometric.BiometricManager
import androidx.biometric.BiometricManager.Authenticators.BIOMETRIC_STRONG
import androidx.biometric.BiometricManager.Authenticators.BIOMETRIC_WEAK
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

/**
 * androidx.biometric rejects BIOMETRIC_STRONG | DEVICE_CREDENTIAL on API 28-29 as
 * BIOMETRIC_ERROR_UNSUPPORTED, before it looks at enrollment, so the lock silently blocked
 * every count edit on Android 9-10. WEAK | DEVICE_CREDENTIAL is the only combination with a
 * PIN fallback there; every other version keeps STRONG.
 */
internal fun authenticatorsFor(sdkInt: Int): Int =
    if (sdkInt == Build.VERSION_CODES.P || sdkInt == Build.VERSION_CODES.Q) {
        BIOMETRIC_WEAK or DEVICE_CREDENTIAL
    } else {
        BIOMETRIC_STRONG or DEVICE_CREDENTIAL
    }

actual class BiometricAuthenticator : BiometricAuth {
    private val authenticators = authenticatorsFor(Build.VERSION.SDK_INT)

    override fun authenticate(
        reason: String,
        onResult: (BiometricResult) -> Unit,
    ) {
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

        val prompt =
            BiometricPrompt(
                activity,
                ContextCompat.getMainExecutor(activity),
                object : BiometricPrompt.AuthenticationCallback() {
                    override fun onAuthenticationSucceeded(result: BiometricPrompt.AuthenticationResult) {
                        onResult(BiometricResult.Success)
                    }

                    override fun onAuthenticationError(
                        errorCode: Int,
                        errString: CharSequence,
                    ) {
                        onResult(BiometricResult.Failed)
                    }
                },
            )
        // No negative button: DEVICE_CREDENTIAL provides the fallback action.
        val promptInfo =
            BiometricPrompt.PromptInfo.Builder()
                .setTitle(reason)
                .setAllowedAuthenticators(authenticators)
                .build()
        prompt.authenticate(promptInfo)
    }
}
