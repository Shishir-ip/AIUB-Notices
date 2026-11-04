package io.github.shishirip.aiubnotices.work

import android.content.Context
import androidx.work.BackoffPolicy
import androidx.work.Constraints
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.NetworkType
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkRequest
import java.util.concurrent.TimeUnit

/**
 * Owns the single unique periodic poll job.
 *
 * Periodic work is never expedited (`setExpedited(false)` is the explicit
 * default for periodic requests), so the OS is free to defer it under Doze /
 * battery saver — notifications may arrive late, which is accepted by design.
 */
object PollScheduler {

    private const val UNIQUE_WORK_NAME = "aiub_notice_poll"

    /** Android's minimum for periodic work. */
    const val MIN_INTERVAL_MINUTES = 15

    fun schedule(context: Context, intervalMinutes: Int) {
        val clamped = maxOf(MIN_INTERVAL_MINUTES, intervalMinutes)
        val request = PeriodicWorkRequestBuilder<NoticePollWorker>(clamped.toLong(), TimeUnit.MINUTES)
            .setConstraints(
                Constraints.Builder()
                    .setRequiredNetworkType(NetworkType.CONNECTED)
                    .build(),
            )
            .setBackoffCriteria(
                BackoffPolicy.EXPONENTIAL,
                WorkRequest.MIN_BACKOFF_MILLIS,
                TimeUnit.MILLISECONDS,
            )
            .build()

        WorkManager.getInstance(context).enqueueUniquePeriodicWork(
            UNIQUE_WORK_NAME,
            ExistingPeriodicWorkPolicy.UPDATE,
            request,
        )
    }

    fun cancel(context: Context) {
        WorkManager.getInstance(context).cancelUniqueWork(UNIQUE_WORK_NAME)
    }

    /** Reconciles the schedule with the user's settings (idempotent). */
    fun ensureScheduled(context: Context, notificationsEnabled: Boolean, intervalMinutes: Int) {
        if (notificationsEnabled) {
            schedule(context, intervalMinutes)
        } else {
            cancel(context)
        }
    }
}
