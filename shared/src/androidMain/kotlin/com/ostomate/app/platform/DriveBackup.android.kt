package com.ostomate.app.platform

import android.app.Activity
import android.content.Context
import androidx.activity.ComponentActivity
import androidx.activity.result.ActivityResult
import androidx.activity.result.ActivityResultLauncher
import androidx.activity.result.IntentSenderRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.work.BackoffPolicy
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.NetworkType
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import com.google.android.gms.auth.api.identity.AuthorizationRequest
import com.google.android.gms.auth.api.identity.AuthorizationResult
import com.google.android.gms.auth.api.identity.Identity
import com.google.android.gms.common.api.Scope
import com.google.android.gms.tasks.Task
import com.ostomate.app.data.drive.DriveBackupRunResult
import com.ostomate.app.data.drive.DriveBackupRunner
import com.ostomate.app.domain.drive.shouldRetry
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import org.koin.core.component.KoinComponent
import org.koin.core.component.inject
import java.util.UUID
import java.util.concurrent.TimeUnit
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

/** Only the files and folders this app creates; nothing else in the user's Drive. */
private const val DRIVE_FILE_SCOPE = "https://www.googleapis.com/auth/drive.file"
private const val WORK_NAME = "daily-drive-backup"
private const val RETRY_BACKOFF_MINUTES = 15L

/**
 * Google authorization through Play services' Identity AuthorizationClient, and the daily
 * WorkManager job. Kept thin: what a run does is [DriveBackupRunner], in common code.
 */
class AndroidDriveBackupPlatform(private val context: Context) : DriveBackupPlatform {
    override val isSupported: Boolean = true

    private val request: AuthorizationRequest =
        AuthorizationRequest.builder()
            .setRequestedScopes(listOf(Scope(DRIVE_FILE_SCOPE)))
            .build()

    override suspend fun authorizeSilently(): DriveAuthorization =
        guarded {
            val result = Identity.getAuthorizationClient(context).authorize(request).await()
            if (result.hasResolution()) DriveAuthorization.NeedsUserInteraction else tokenOf(result)
        }

    override suspend fun authorizeInteractively(): DriveAuthorization =
        withContext(Dispatchers.Main) {
            guarded {
                val activity = CurrentActivityHolder.activity ?: return@guarded DriveAuthorization.Failed
                val client = Identity.getAuthorizationClient(activity)
                val result = client.authorize(request).await()
                val pendingIntent = result.pendingIntent
                when {
                    !result.hasResolution() -> tokenOf(result)
                    pendingIntent == null -> DriveAuthorization.Failed
                    else -> {
                        val consent = activity.launchForResult(IntentSenderRequest.Builder(pendingIntent).build())
                        if (consent.resultCode != Activity.RESULT_OK) {
                            DriveAuthorization.Cancelled
                        } else {
                            tokenOf(client.getAuthorizationResultFromIntent(consent.data))
                        }
                    }
                }
            }
        }

    override fun scheduleDaily() {
        val work =
            PeriodicWorkRequestBuilder<DriveBackupWorker>(1, TimeUnit.DAYS)
                .setConstraints(Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build())
                .setBackoffCriteria(BackoffPolicy.EXPONENTIAL, RETRY_BACKOFF_MINUTES, TimeUnit.MINUTES)
                .build()
        WorkManager.getInstance(context)
            .enqueueUniquePeriodicWork(WORK_NAME, ExistingPeriodicWorkPolicy.KEEP, work)
    }

    override fun cancelDaily() {
        WorkManager.getInstance(context).cancelUniqueWork(WORK_NAME)
    }

    private fun tokenOf(result: AuthorizationResult): DriveAuthorization =
        result.accessToken?.let { DriveAuthorization.Authorized(it) } ?: DriveAuthorization.Failed

    // Play services reports failure as exceptions of several types (ApiException, and others
    // from a broken Play services install); none of them may crash Settings or the job.
    @Suppress("TooGenericExceptionCaught")
    private inline fun guarded(block: () -> DriveAuthorization): DriveAuthorization =
        try {
            block()
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            DriveAuthorization.Failed
        }
}

/** One daily run. Retries only a network failure, at most twice; everything else waits a day. */
class DriveBackupWorker(
    context: Context,
    params: WorkerParameters,
) : CoroutineWorker(context, params),
    KoinComponent {
    private val runner: DriveBackupRunner by inject()

    override suspend fun doWork(): Result {
        val failure = (runner.run() as? DriveBackupRunResult.Failed)?.reason
        return if (shouldRetry(failure, runAttemptCount)) Result.retry() else Result.success()
    }
}

private suspend fun <T> Task<T>.await(): T =
    suspendCancellableCoroutine { continuation ->
        addOnSuccessListener { continuation.resume(it) }
        addOnFailureListener { continuation.resumeWithException(it) }
        addOnCanceledListener { continuation.cancel() }
    }

/**
 * Shows Google's consent screen and waits for its answer. Registered on the fly rather than in
 * onCreate, so the Activity needs no Drive-specific code; if the Activity is destroyed while
 * the screen is up (rotation), the wait ends as a cancellation and the user can try again.
 */
private suspend fun ComponentActivity.launchForResult(request: IntentSenderRequest): ActivityResult =
    suspendCancellableCoroutine { continuation ->
        lateinit var launcher: ActivityResultLauncher<IntentSenderRequest>
        val destroyed =
            LifecycleEventObserver { _, event ->
                if (event == Lifecycle.Event.ON_DESTROY && continuation.isActive) {
                    launcher.unregister()
                    continuation.resume(ActivityResult(Activity.RESULT_CANCELED, null))
                }
            }
        launcher =
            activityResultRegistry.register(
                "drive-backup-auth-${UUID.randomUUID()}",
                ActivityResultContracts.StartIntentSenderForResult(),
            ) { result ->
                lifecycle.removeObserver(destroyed)
                launcher.unregister()
                if (continuation.isActive) continuation.resume(result)
            }
        lifecycle.addObserver(destroyed)
        continuation.invokeOnCancellation {
            // May run on the cancelling thread; lifecycle observers are main-thread only.
            runOnUiThread {
                lifecycle.removeObserver(destroyed)
                launcher.unregister()
            }
        }
        launcher.launch(request)
    }
