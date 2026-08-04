package com.example.decisionjournal.data

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.content.pm.PackageManager
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import dagger.hilt.android.qualifiers.ApplicationContext
import androidx.work.Data
import androidx.work.ExistingWorkPolicy
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.Worker
import androidx.work.WorkerParameters
import java.util.concurrent.TimeUnit
import javax.inject.Inject

private const val CHANNEL_ID = "review-reminders"
private const val WORK_PREFIX = "decision-review-"
private const val DECISION_ID = "decisionId"

class ReviewReminderScheduler @Inject constructor(@ApplicationContext private val context: Context) {
    fun scheduleOrCancel(decisionId: Long, reviewDate: Long?) {
        val workManager = WorkManager.getInstance(context)
        val name = WORK_PREFIX + decisionId
        workManager.cancelUniqueWork(name)
        if (reviewDate == null || reviewDate <= System.currentTimeMillis()) return
        val request = OneTimeWorkRequestBuilder<ReviewReminderWorker>()
            .setInitialDelay(reviewDate - System.currentTimeMillis(), TimeUnit.MILLISECONDS)
            .setInputData(Data.Builder().putLong(DECISION_ID, decisionId).build())
            .build()
        workManager.enqueueUniqueWork(name, ExistingWorkPolicy.REPLACE, request)
    }

    fun cancel(decisionId: Long) = WorkManager.getInstance(context).cancelUniqueWork(WORK_PREFIX + decisionId)
}

class ReviewReminderWorker(context: Context, params: WorkerParameters) : Worker(context, params) {
    override fun doWork(): Result {
        val manager = applicationContext.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        manager.createNotificationChannel(NotificationChannel(CHANNEL_ID, "复盘提醒", NotificationManager.IMPORTANCE_DEFAULT))
        if (android.os.Build.VERSION.SDK_INT >= 33 && ContextCompat.checkSelfPermission(applicationContext, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) return Result.success()
        val id = inputData.getLong(DECISION_ID, 0L).toInt()
        val notification = NotificationCompat.Builder(applicationContext, CHANNEL_ID)
            .setSmallIcon(android.R.drawable.ic_dialog_info)
            .setContentTitle("回看提醒")
            .setContentText("有一个决定到了复盘时间")
            .setAutoCancel(true)
            .setPriority(NotificationCompat.PRIORITY_DEFAULT)
            .build()
        NotificationManagerCompat.from(applicationContext).notify(id, notification)
        return Result.success()
    }
}
