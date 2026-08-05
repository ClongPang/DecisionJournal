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
import com.example.decisionjournal.data.model.Decision
import com.example.decisionjournal.data.model.ReminderState
import dagger.hilt.EntryPoint
import dagger.hilt.InstallIn
import dagger.hilt.android.EntryPointAccessors
import dagger.hilt.components.SingletonComponent

internal const val REVIEW_REMINDER_CHANNEL_ID = "review-reminders"
private const val WORK_PREFIX = "decision-review-"
private const val DECISION_ID = "decisionId"
private const val SCHEDULED_REVIEW_DATE = "scheduledReviewDate"
private const val SCHEDULED_REMINDER_AT = "scheduledReminderAt"
private const val NOTIFICATION_TAG_PREFIX = "decision-review-"
private const val NOTIFICATION_ID = 0

internal fun reviewNotificationTag(decisionId: Long): String = NOTIFICATION_TAG_PREFIX + decisionId

/**
 * A cancelled WorkManager request can already be running when a decision is edited.  Bind the
 * request to the exact review date so an older request can never notify after a reschedule,
 * clear, or follow-up review.
 */
internal fun isCurrentReviewReminder(
    decision: Decision?,
    scheduledReviewDate: Long,
    scheduledReminderAt: Long = 0L,
): Boolean = decision?.reviewDate != null &&
    when {
        // Releases before the stable date token can still deliver once after upgrade.
        scheduledReviewDate <= 0L -> true
        decision.reviewDate != scheduledReviewDate -> false
        // v8 had a date token only. As soon as v9 has reconciled the record to an evening
        // reminder, reject that old midnight task even if cancellation raced its execution.
        scheduledReminderAt <= 0L -> decision.reminderAt == null
        else -> decision.reminderAt == scheduledReminderAt
    }

@EntryPoint
@InstallIn(SingletonComponent::class)
interface ReminderWorkerDependencies {
    fun decisionDao(): DecisionDao
}

class ReviewReminderScheduler @Inject constructor(@ApplicationContext private val context: Context) {
    fun scheduleOrCancel(decisionId: Long, reviewDate: Long?, reminderAt: Long?): ReminderState {
        require(decisionId > 0L) { "决定 ID 无效" }
        val workManager = WorkManager.getInstance(context)
        val name = WORK_PREFIX + decisionId
        workManager.cancelUniqueWork(name)
        cancelDeliveredNotification(decisionId)
        if (reviewDate == null || reminderAt == null || reminderAt <= System.currentTimeMillis()) return ReminderState.NOT_APPLICABLE
        notificationAvailability(context)?.let { return it }
        val request = OneTimeWorkRequestBuilder<ReviewReminderWorker>()
            .setInitialDelay(reminderAt - System.currentTimeMillis(), TimeUnit.MILLISECONDS)
            .setInputData(
                Data.Builder()
                    .putLong(DECISION_ID, decisionId)
                    .putLong(SCHEDULED_REVIEW_DATE, reviewDate)
                    .putLong(SCHEDULED_REMINDER_AT, reminderAt)
                    .build(),
            )
            .build()
        workManager.enqueueUniqueWork(name, ExistingWorkPolicy.REPLACE, request)
        return ReminderState.SCHEDULED
    }

    /** Returns the reason a future reminder cannot be delivered, or null when it is usable. */
    fun notificationAvailability(): ReminderState? = notificationAvailability(context)

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

private fun notificationAvailability(context: Context): ReminderState? {
    if (android.os.Build.VERSION.SDK_INT >= 33 && ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) {
        return ReminderState.PERMISSION_REQUIRED
    }
    if (!NotificationManagerCompat.from(context).areNotificationsEnabled()) {
        return ReminderState.NOTIFICATIONS_DISABLED
    }
    val manager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
    manager.createNotificationChannel(NotificationChannel(REVIEW_REMINDER_CHANNEL_ID, "复盘提醒", NotificationManager.IMPORTANCE_DEFAULT))
    if (manager.getNotificationChannel(REVIEW_REMINDER_CHANNEL_ID)?.importance == NotificationManager.IMPORTANCE_NONE) {
        return ReminderState.CHANNEL_DISABLED
    }
    return null
}

class ReviewReminderWorker(context: Context, params: WorkerParameters) : CoroutineWorker(context, params) {
    override suspend fun doWork(): Result {
        val decisionId = inputData.getLong(DECISION_ID, 0L)
        val scheduledReviewDate = inputData.getLong(SCHEDULED_REVIEW_DATE, 0L)
        val scheduledReminderAt = inputData.getLong(SCHEDULED_REMINDER_AT, 0L)
        if (decisionId <= 0L) return Result.failure()
        return try {
            val dao = EntryPointAccessors.fromApplication(
                applicationContext,
                ReminderWorkerDependencies::class.java,
            ).decisionDao()
            // Cancellation is asynchronous; verify both the source record and the exact date
            // immediately before posting. This rejects an already-running stale request.
            if (isStopped || !isCurrentReviewReminder(dao.getById(decisionId), scheduledReviewDate, scheduledReminderAt)) {
                return Result.success()
            }
            notificationAvailability(applicationContext)?.let { unavailable ->
                dao.updateReminderState(decisionId, unavailable)
                return Result.success()
            }
            val notifications = NotificationManagerCompat.from(applicationContext)
            val notification = NotificationCompat.Builder(applicationContext, REVIEW_REMINDER_CHANNEL_ID)
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
