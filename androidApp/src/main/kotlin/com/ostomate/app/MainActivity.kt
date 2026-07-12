package com.ostomate.app

import android.Manifest
import android.content.Intent
import android.os.Build
import android.os.Bundle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.fragment.app.FragmentActivity
import androidx.lifecycle.lifecycleScope
import com.ostomate.app.data.ChangeEventRepository
import com.ostomate.app.data.diagnostics.DeepLinkEntryPoint
import com.ostomate.app.platform.CurrentActivityHolder
import com.ostomate.app.platform.DeepLinkBus
import com.ostomate.app.platform.LastCrashStore
import kotlinx.coroutines.launch
import org.koin.android.ext.android.inject

// FragmentActivity (not ComponentActivity) because BiometricPrompt requires it.
class MainActivity : FragmentActivity() {

    private val repository: ChangeEventRepository by inject()

    private val notificationPermission =
        registerForActivityResult(ActivityResultContracts.RequestPermission()) { /* result unused in spike */ }

    override fun onCreate(savedInstanceState: Bundle?) {
        LastCrashStore.install(this)
        super.onCreate(savedInstanceState)
        CurrentActivityHolder.activity = this
        enableEdgeToEdge()
        setContent {
            App()
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            notificationPermission.launch(Manifest.permission.POST_NOTIFICATIONS)
        }
        // Only handle on a fresh start. On recreation (rotation, permission dialog,
        // process-death restore) the same launch intent is re-delivered and must not re-log.
        if (savedInstanceState == null) {
            handleDeepLink(intent, DeepLinkEntryPoint.ANDROID_ON_CREATE, savedInstanceStateWasNull = true)
        }
    }

    override fun onDestroy() {
        if (CurrentActivityHolder.activity === this) {
            CurrentActivityHolder.activity = null
        }
        super.onDestroy()
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        handleDeepLink(intent, DeepLinkEntryPoint.ANDROID_ON_NEW_INTENT, savedInstanceStateWasNull = null)
    }

    private fun handleDeepLink(
        intent: Intent?,
        entryPoint: DeepLinkEntryPoint,
        savedInstanceStateWasNull: Boolean?,
    ) {
        val uri = intent?.data?.toString() ?: return
        // Consume the link so a later Activity recreation cannot replay the same scan.
        intent.data = null
        lifecycleScope.launch {
            val outcome = repository.handleDeepLink(uri, entryPoint, savedInstanceStateWasNull)
            DeepLinkBus.post(outcome)
        }
    }
}
