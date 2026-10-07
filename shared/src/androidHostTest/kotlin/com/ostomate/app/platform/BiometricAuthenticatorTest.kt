package com.ostomate.app.platform

import android.content.Context
import android.os.Build
import androidx.biometric.BiometricManager
import androidx.test.core.app.ApplicationProvider
import org.junit.Assert.assertNotEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

// Asks the real androidx BiometricManager rather than asserting constants: the bug was the
// library rejecting STRONG | DEVICE_CREDENTIAL on API 28-29 before checking enrollment,
// which the app mapped to Failed, so every locked count edit was silently dropped.
@RunWith(RobolectricTestRunner::class)
class BiometricAuthenticatorTest {
    private fun canAuthenticate(): Int =
        BiometricManager.from(ApplicationProvider.getApplicationContext<Context>())
            .canAuthenticate(authenticatorsFor(Build.VERSION.SDK_INT))

    @Test
    @Config(sdk = [29])
    fun android10CombinationIsSupported() {
        assertNotEquals(BiometricManager.BIOMETRIC_ERROR_UNSUPPORTED, canAuthenticate())
    }

    @Test
    @Config(sdk = [34])
    fun android14CombinationIsSupported() {
        assertNotEquals(BiometricManager.BIOMETRIC_ERROR_UNSUPPORTED, canAuthenticate())
    }
}
