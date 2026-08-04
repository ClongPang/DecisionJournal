package com.example.decisionjournal.data

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import dagger.hilt.android.qualifiers.ApplicationContext
import androidx.work.Data
import androidx.work.CoroutineWorker
import androidx.work.ExistingWorkPolicy
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import java.util.concurrent.TimeUnit
import javax.inject.Inject
import com.example.decisionjournal.MainActivity
import com.example.decisionjournal.EXTRA_REMINDER_DECISION_ID
import com.example.decisionjournal.data.local.DecisionDao
import dagger.hilt.EntryPoint
import dagger.hilt.InstallIn
import dagger.hilt.android.EntryPointAccessors
import dagger.hilt.components.SingletonComponent

private const val CHANNEL_ID = "review-reminders"
private const val WORK_PREFIX = "decision-review-"
private const val DECISION_ID = "decisionId"
private const val NOTIFICATION_TAG_PREFIX = "decision-review-"
private const val NOTIFICATION_ID = 0

internal fun reviewNotificationTag(decisionId: Long): String = NOTIFICATION_TAG_PREFIX + decisionId

@EntryPoint
@InstallIn(SingletonComponent::class)
interface ReminderWorkerDependencies {
    fun decisionDao(): DecisionDao
}

class ReviewReminderScheduler @Inject constructor(@ApplicationContext private val context: Context) {
    fun scheduleOrCancel(decisionId: Long, reviewDate: Long?) {
        require(decisionId > 0L) { "决定 ID 无效" }
        val workManager = WorkManager.getInstance(context)
        val name = WORK_PREFIX + decisionId
        workManager.cancelUniqueWork(name)
        cancelDeliveredNotification(decisionId)
        if (reviewDate == null || reviewDate <= System.currentTimeMillis()) return
        if (android.os.Build.VERSION.SDK_INT >= 33 && ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) {
            throw IllegalStateException("通知权限未开启")
        }
        val manager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        manager.createNotificationChannel(NotificationChannel(CHANNEL_ID, "复盘提醒", NotificationManager.IMPORTANCE_DEFAULT))
        if (!NotificationManagerCompat.from(context).areNotificationsEnabled()) {
            throw IllegalStateException("通知已被系统关闭")
        }
        if (manager.getNotificationChannel(CHANNEL_ID)?.importance == NotificationManager.IMPORTANCE_NONE) {
            throw IllegalStateException("复盘提醒频道已被关闭")
        }
        val request = OneTimeWorkRequestBuilder<ReviewReminderWorker>()
            .setInitialDelay(reviewDate - System.currentTimeMillis(), TimeUnit.MILLISECONDS)
            .setInputData(Data.Builder().putLong(DECISION_ID, decisionId).build())
            .build()
        workManager.enqueueUniqueWork(name, ExistingWorkPolicy.REPLACE, request)
    }

    fun cancel(decisionId: Long) {
        try {
            WorkManager.getInstance(context).cancelUniqueWork(WORK_PREFIX + decisionId)
        } finally {
            // A delivered notification can otherwise survive an edit or a delete.
            cancelDeliveredNotification(decisionId)
        }
    }

    private fun cancelDeliveredNotification(decisionId: Long) {
        val notifications = NotificationManagerCompat.from(context)
        notifications.cancel(reviewNotificationTag(decisionId), NOTIFICATION_ID)
        // Clear notifications posted by releases before stable tags were introduced.
        notifications.cancel(decisionId.hashCode())
    }
}

class ReviewReminderWorker(context: Context, params: WorkerParameters) : CoroutineWorker(context, params) {
    override suspend fun doWork(): Result {
        val decisionId = inputData.getLong(DECISION_ID, 0L)
        if (decisionId <= 0L) return Result.failure()
        if (android.os.Build.VERSION.SDK_INT >= 33 && ContextCompat.checkSelfPermission(applicationContext, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) return Result.success()
        val notifications = NotificationManagerCompat.from(applicationContext)
        if (!notifications.areNotificationsEnabled()) return Result.success()

        return try {
            val dao = EntryPointAccessors.fromApplication(
                applicationContext,
                ReminderWorkerDependencies::class.java,
            ).decisionDao()
            // Cancellation is asynchronous; verify the source record immediately before posting.
            if (isStopped || dao.getById(decisionId) == null) return Result.success()
            val manager = applicationContext.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            manager.createNotificationChannel(NotificationChannel(CHANNEL_ID, "复盘提醒", NotificationManager.IMPORTANCE_DEFAULT))
            if (manager.getNotificationChannel(CHANNEL_ID)?.importance == NotificationManager.IMPORTANCE_NONE) return Result.success()
            val notification = NotificationCompat.Builder(applicationContext, CHANNEL_ID)
                .setSmallIcon(android.R.drawable.ic_dialog_info)
                .setContentTitle("回看提醒")
                .setContentText("有一个决定到了复盘时间")
                .setContentIntent(
                    PendingIntent.getActivity(
                        applicationContext,
                        decisionId.hashCode(),
                        Intent(applicationContext, MainActivity::class.java).putExtra(EXTRA_REMINDER_DECISION_ID, decisionId),
                        PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
                    ),
                )
                .setAutoCancel(true)
                .setPriority(NotificationCompat.PRIORITY_DEFAULT)
                .build()
            if (isStopped) return Result.success()
            notifications.notify(reviewNotificationTag(decisionId), NOTIFICATION_ID, notification)
            Result.success()
        } catch (_: SecurityException) {
            // Permission or channel state can change after WorkManager starts the worker.
            Result.success()
        } catch (_: Exception) {
            Result.retry()
        }
    }
}
