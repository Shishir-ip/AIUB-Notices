package io.github.shishirip.aiubnotices.notify

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import io.github.shishirip.aiubnotices.R
import io.github.shishirip.aiubnotices.data.Notice
import io.github.shishirip.aiubnotices.data.NoticeParser

/**
 * Posts heads-up notifications for new notices, grouped behind a summary
 * notification when several arrive in the same cycle.
 */
class Notifier(private val context: Context) {

    companion object {
        const val CHANNEL_ID = "aiub_notices"
        private const val GROUP_KEY = "io.github.shishirip.aiubnotices.NEW_NOTICES"
        private const val SUMMARY_NOTIFICATION_ID = 1001

        /** Cap per poll so a backlog never spams the user. */
        private const val MAX_NOTIFICATIONS_PER_POLL = 5
    }

    fun ensureChannel() {
        val channel = NotificationChannel(
            CHANNEL_ID,
            context.getString(R.string.notification_channel_name),
            NotificationManager.IMPORTANCE_HIGH,
        ).apply {
            description = context.getString(R.string.notification_channel_description)
        }
        context.getSystemService(NotificationManager::class.java).createNotificationChannel(channel)
    }

    /**
     * Notifies about [notices] (max [MAX_NOTIFICATIONS_PER_POLL]). With more
     * than one notice, children are grouped under a single summary.
     */
    fun notifyNewNotices(notices: List<Notice>) {
        if (notices.isEmpty()) return
        if (!areNotificationsEnabled()) return
        ensureChannel()

        val manager = NotificationManagerCompat.from(context)
        val selected = notices.take(MAX_NOTIFICATIONS_PER_POLL)
        val grouped = selected.size > 1

        selected.forEach { notice ->
            val notification = buildNoticeNotification(
                notice = notice,
                grouped = grouped,
                alertBehavior = NotificationCompat.GROUP_ALERT_SUMMARY,
            )
            runCatching { manager.notify(notice.url.hashCode(), notification) }
        }

        if (grouped) {
            runCatching { manager.notify(SUMMARY_NOTIFICATION_ID, buildSummaryNotification(selected)) }
        }
    }

    /** Posts a fake notice so users can verify notifications work end-to-end. */
    fun sendTestNotification(): Boolean {
        if (!areNotificationsEnabled()) return false
        ensureChannel()
        val sample = Notice(
            title = "Test notification",
            description = "If you can read this, AIUB Notices notifications are working.",
            dateText = java.time.LocalDate.now().format(java.time.format.DateTimeFormatter.ofPattern("dd MMM yyyy")),
            url = NoticeParser.NOTICES_URL,
        )
        runCatching {
            NotificationManagerCompat.from(context).notify(
                sample.url.hashCode(),
                buildNoticeNotification(sample, grouped = false, alertBehavior = NotificationCompat.GROUP_ALERT_ALL),
            )
        }
        return true
    }

    fun areNotificationsEnabled(): Boolean =
        NotificationManagerCompat.from(context).areNotificationsEnabled()

    private fun buildNoticeNotification(
        notice: Notice,
        grouped: Boolean,
        alertBehavior: Int,
    ): Notification {
        val text = listOf(notice.dateText, notice.description)
            .filter { it.isNotBlank() }
            .joinToString(" · ")

        val builder = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_notification)
            .setContentTitle(notice.title)
            .setContentText(text)
            .setStyle(NotificationCompat.BigTextStyle().bigText(text))
            .setContentIntent(openUrlIntent(notice.url, notice.url.hashCode()))
            .setAutoCancel(true)
            .setCategory(NotificationCompat.CATEGORY_EVENT)
            .setGroup(GROUP_KEY)
            .setGroupAlertBehavior(alertBehavior)

        if (grouped) builder.setGroupSummary(false)
        return builder.build()
    }

    private fun buildSummaryNotification(notices: List<Notice>): Notification {
        val inboxStyle = NotificationCompat.InboxStyle().also { style ->
            notices.forEach { notice -> style.addLine(notice.title) }
        }
        return NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_notification)
            .setContentTitle(context.getString(R.string.notification_summary_title, notices.size))
            .setContentText(context.getString(R.string.notification_summary_text))
            .setStyle(inboxStyle.setBigContentTitle(context.getString(R.string.notification_summary_title, notices.size)))
            .setContentIntent(openUrlIntent(NoticeParser.NOTICES_URL, SUMMARY_NOTIFICATION_ID))
            .setAutoCancel(true)
            .setCategory(NotificationCompat.CATEGORY_EVENT)
            .setGroup(GROUP_KEY)
            .setGroupSummary(true)
            .setGroupAlertBehavior(NotificationCompat.GROUP_ALERT_SUMMARY)
            .build()
    }

    /** Opens [url] in the browser. PendingIntent is always FLAG_IMMUTABLE. */
    private fun openUrlIntent(url: String, requestCode: Int): PendingIntent {
        val intent = Intent(Intent.ACTION_VIEW, Uri.parse(url))
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        return PendingIntent.getActivity(
            context,
            requestCode,
            intent,
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )
    }
}
