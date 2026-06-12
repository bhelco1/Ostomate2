package com.ostimate.app.platform

import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import androidx.core.app.NotificationCompat
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.Worker
import androidx.work.WorkerParameters
import androidx.work.workDataOf
import java.util.concurrent.TimeUnit

actual class Notifier(private val context: Context) {

    actual fun schedule(delaySeconds: Int, title: String, body: String) {
        val request = OneTimeWorkRequestBuilder<ReminderWorker>()
            .setInitialDelay(delaySeconds.toLong(), TimeUnit.SECONDS)
            .setInputData(workDataOf(KEY_TITLE to title, KEY_BODY to body))
            .build()
        WorkManager.getInstance(context).enqueue(request)
    }

    companion object {
        const val KEY_TITLE = "title"
        const val KEY_BODY = "body"
        const val CHANNEL_ID = "reminders"
    }
}

class ReminderWorker(context: Context, params: WorkerParameters) : Worker(context, params) {
    override fun doWork(): Result {
        val manager =
            applicationContext.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        manager.createNotificationChannel(
            NotificationChannel(
                Notifier.CHANNEL_ID,
                "Reminders",
                NotificationManager.IMPORTANCE_DEFAULT,
            )
        )
        val notification = NotificationCompat.Builder(applicationContext, Notifier.CHANNEL_ID)
            // TODO(Phase 1): real notification icon
            .setSmallIcon(android.R.drawable.ic_dialog_info)
            .setContentTitle(inputData.getString(Notifier.KEY_TITLE))
            .setContentText(inputData.getString(Notifier.KEY_BODY))
            .setAutoCancel(true)
            .build()
        manager.notify(id.hashCode(), notification)
        return Result.success()
    }
}
