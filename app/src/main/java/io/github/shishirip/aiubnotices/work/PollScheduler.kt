package io.github.shishirip.aiubnotices.work

import android.content.Context
import androidx.work.BackoffPolicy
import androidx.work.Constraints
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.ExistingWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkRequest
import java.util.UUID
import java.util.concurrent.TimeUnit

/**
 * Owns the background poll jobs.
 *
 * Two modes:
 *  - **Periodic**: a unique `PeriodicWorkRequest` every [MIN_INTERVAL_MINUTES]+
 *    minutes (WorkManager's floor for periodic work).
 *  - **Real-time** ([REALTIME_INTERVAL]): a self-rescheduling chain of unique
 *    `OneTimeWorkRequest`s that runs roughly once a minute, so notifications
 *    land as fast as the OS allows. The worker re-reads settings on every run
 *    and only re-enqueues while real-time is still selected, so the chain stops
 *    by itself when the user switches modes. Enqueues are deduplicated against
 *    pending work, so overlapping scheduling can never grow the chain.
 *
 * Best effort by design: the OS (Doze, battery saver, OEM killers) may still
 * defer checks. Work is never expedited (`setExpedited(false)` is the explicit
 * default for these requests); disabling battery optimization makes real-time
 * mode far more reliable, which is what the Settings prompt offers.
 */
object PollScheduler {

    private const val PERIODIC_WORK_NAME = "aiub_notice_poll"
    private const val REALTIME_WORK_NAME = "aiub_notice_realtime"

    /** Sentinel for real-time mode. */
    const val REALTIME_INTERVAL = 0

    /** Android's minimum for periodic work. */
    const val MIN_INTERVAL_MINUTES = 15

    /** Delay between two real-time checks. */
    private const val REALTIME_DELAY_MINUTES = 1L

    private val constraints = Constraints.Builder()
        .setRequiredNetworkType(NetworkType.CONNECTED)
        .build()

    /** Switches the schedule to [intervalMinutes] (0 = real-time). */
    fun schedule(context: Context, intervalMinutes: Int) {
        if (intervalMinutes == REALTIME_INTERVAL) {
            cancelPeriodic(context)
            startRealtimeChain(context)
        } else {
            cancelRealtime(context)
            schedulePeriodic(context, maxOf(MIN_INTERVAL_MINUTES, intervalMinutes))
        }
    }

    fun cancelAll(context: Context) {
        WorkManager.getInstance(context).apply {
            cancelUniqueWork(PERIODIC_WORK_NAME)
            cancelUniqueWork(REALTIME_WORK_NAME)
        }
    }

    /** Reconciles the schedule with the user's settings (idempotent). */
    fun ensureScheduled(context: Context, notificationsEnabled: Boolean, intervalMinutes: Int) {
        if (notificationsEnabled) {
            schedule(context, intervalMinutes)
        } else {
            cancelAll(context)
        }
    }

    private fun schedulePeriodic(context: Context, intervalMinutes: Int) {
        val request = PeriodicWorkRequestBuilder<NoticePollWorker>(intervalMinutes.toLong(), TimeUnit.MINUTES)
            .setConstraints(constraints)
            .setBackoffCriteria(
                BackoffPolicy.EXPONENTIAL,
                WorkRequest.MIN_BACKOFF_MILLIS,
                TimeUnit.MILLISECONDS,
            )
            .build()

        WorkManager.getInstance(context).enqueueUniquePeriodicWork(
            PERIODIC_WORK_NAME,
            ExistingPeriodicWorkPolicy.UPDATE,
            request,
        )
    }

    /** Cancels any existing chain and starts a fresh one with an immediate first check. */
    private fun startRealtimeChain(context: Context) {
        val workManager = WorkManager.getInstance(context)
        workManager.cancelUniqueWork(REALTIME_WORK_NAME)
        enqueueNextRealtimePoll(workManager)
    }

    /**
     * Keeps the real-time chain alive; called by the worker at the end of each
     * run. If another chain link is already scheduled (excluding the running
     * one), this is a no-op — so extra enqueues collapse instead of piling up.
     */
    fun scheduleNextRealtimePoll(context: Context, currentWorkId: UUID) {
        val workManager = WorkManager.getInstance(context)
        val alreadyPending = runCatching {
            workManager.getWorkInfosForUniqueWork(REALTIME_WORK_NAME).get()
                .any { it.id != currentWorkId && !it.state.isFinished }
        }.getOrDefault(false)
        if (alreadyPending) return
        enqueueNextRealtimePoll(workManager)
    }

    private fun enqueueNextRealtimePoll(workManager: WorkManager) {
        val request = OneTimeWorkRequestBuilder<NoticePollWorker>()
            .setInitialDelay(REALTIME_DELAY_MINUTES, TimeUnit.MINUTES)
            .setConstraints(constraints)
            .setBackoffCriteria(
                BackoffPolicy.EXPONENTIAL,
                WorkRequest.MIN_BACKOFF_MILLIS,
                TimeUnit.MILLISECONDS,
            )
            .build()
        // Non-expedited by default (Doze-respecting). APPEND_OR_REPLACE appends
        // after the currently running chain link, or starts a fresh chain when
        // the previous one failed or was cancelled.
        workManager.enqueueUniqueWork(
            REALTIME_WORK_NAME,
            ExistingWorkPolicy.APPEND_OR_REPLACE,
            request,
        )
    }

    private fun cancelPeriodic(context: Context) {
        WorkManager.getInstance(context).cancelUniqueWork(PERIODIC_WORK_NAME)
    }

    private fun cancelRealtime(context: Context) {
        WorkManager.getInstance(context).cancelUniqueWork(REALTIME_WORK_NAME)
    }
}
