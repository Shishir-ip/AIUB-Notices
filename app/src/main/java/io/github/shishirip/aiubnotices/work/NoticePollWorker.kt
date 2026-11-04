package io.github.shishirip.aiubnotices.work

import android.content.Context
import android.util.Log
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import io.github.shishirip.aiubnotices.data.NoticeRepository
import io.github.shishirip.aiubnotices.data.NoticeStore
import io.github.shishirip.aiubnotices.notify.Notifier
import kotlinx.coroutines.flow.first

/**
 * One background poll cycle: fetch the notice board, notify about new notices,
 * and persist state. Designed to never crash on site changes — network errors
 * are retried with exponential backoff, empty/renamed pages just skip a cycle.
 */
class NoticePollWorker(
    appContext: Context,
    params: WorkerParameters,
) : CoroutineWorker(appContext, params) {

    override suspend fun doWork(): Result {
        val store = NoticeStore(applicationContext)
        val repository = NoticeRepository(store)
        val notifier = Notifier(applicationContext)

        val settings = store.settings.first()

        val outcome = try {
            repository.sync { newNotices ->
                if (settings.notificationsEnabled) {
                    notifier.notifyNewNotices(newNotices)
                }
            }
        } catch (e: Exception) {
            Log.w(TAG, "Poll failed (attempt $runAttemptCount): ${e.message}")
            return if (runAttemptCount < MAX_ATTEMPTS) Result.retry() else Result.failure()
        }

        if (outcome == null) {
            // Page fetched but zero notices parsed — the site is probably down
            // or the layout changed. Skip this cycle; touch nothing.
            Log.w(TAG, "Poll skipped: zero notices parsed")
            return Result.success()
        }

        Log.i(
            TAG,
            "Poll ok: ${outcome.notices.size} notices, ${outcome.newNotices.size} new" +
                if (outcome.wasBaseline) " (baseline recorded)" else "",
        )
        return Result.success()
    }

    private companion object {
        const val TAG = "NoticePollWorker"
        const val MAX_ATTEMPTS = 3
    }
}
