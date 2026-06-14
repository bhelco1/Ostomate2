package com.ostomate.app.platform

sealed interface BiometricResult {
    data object Success : BiometricResult

    data object Failed : BiometricResult

    /**
     * Device has no biometric or credential enrolled. v1 behavior, carried over:
     * callers treat this as a graceful auto-unlock.
     */
    data object NotEnrolled : BiometricResult
}

expect class BiometricAuthenticator {
    /** Shows the platform auth prompt. [onResult] is invoked on the main thread. */
    fun authenticate(
        reason: String,
        onResult: (BiometricResult) -> Unit,
    )
}
