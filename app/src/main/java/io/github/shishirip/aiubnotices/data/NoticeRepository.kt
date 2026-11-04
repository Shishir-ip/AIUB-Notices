package io.github.shishirip.aiubnotices.data

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL

/**
 * Fetches the AIUB notice board and runs sync cycles: fetch → diff against the
 * seen set → persist state (and hand new notices to the caller for notifying).
 *
 * Politeness rules: exactly one HTTP request per cycle, custom User-Agent,
 * 15s connect/read timeouts.
 */
class NoticeRepository(private val store: NoticeStore) {

    data class SyncOutcome(
        /** Everything currently on the page (page order). */
        val notices: List<Notice>,
        /** Notices that were not in the seen set before this cycle. */
        val newNotices: List<Notice>,
        /** True when this cycle was the very first one (silent baseline). */
        val wasBaseline: Boolean,
    )

    /**
     * Downloads and parses https://www.aiub.edu/category/notices.
     *
     * @throws IOException on network errors or when the HTTP status is not 200 —
     *   callers decide whether the cycle should be retried or skipped.
     */
    suspend fun fetchNotices(): List<Notice> = withContext(Dispatchers.IO) {
        val connection = URL(NoticeParser.NOTICES_URL).openConnection() as HttpURLConnection
        try {
            connection.requestMethod = "GET"
            connection.connectTimeout = CONNECT_TIMEOUT_MS
            connection.readTimeout = READ_TIMEOUT_MS
            connection.instanceFollowRedirects = true
            connection.setRequestProperty("User-Agent", USER_AGENT)
            connection.setRequestProperty("Accept", "text/html,application/xhtml+xml")

            val status = connection.responseCode
            if (status != HttpURLConnection.HTTP_OK) {
                throw IOException("Unexpected HTTP $status from ${NoticeParser.NOTICES_URL}")
            }

            val body = connection.inputStream.bufferedReader(Charsets.UTF_8).use { it.readText() }
            NoticeParser.parse(body)
        } finally {
            connection.disconnect()
        }
    }

    /**
     * Runs one poll cycle.
     *
     * @param announce invoked with the newly found notices (before they are
     *   recorded as seen) — used to post notifications. The manual in-app
     *   refresh does not announce anything because the user is already looking
     *   at the list.
     * @return the cycle result, or null when the cycle was skipped because the
     *   page parsed to zero notices (site down or layout changed). Never crashes
     *   on site changes; [IOException]s propagate to the caller for retry logic.
     */
    suspend fun sync(announce: suspend (List<Notice>) -> Unit = {}): SyncOutcome? {
        val notices = fetchNotices()
        if (notices.isEmpty()) return null

        val wasBaseline = !store.isBaselineDone()
        val seen = store.seenUrls()
        val newNotices = if (wasBaseline) emptyList() else notices.filterNot { it.url in seen }

        if (newNotices.isNotEmpty()) announce(newNotices)

        store.markSeen(notices.map { it.url })
        store.cacheNotices(notices.take(CACHE_SIZE))
        store.setLastChecked(System.currentTimeMillis())
        if (wasBaseline) store.markBaselineDone()

        return SyncOutcome(notices = notices, newNotices = newNotices, wasBaseline = wasBaseline)
    }

    companion object {
        /** Be a good citizen: identify the app to the server. */
        const val USER_AGENT = "AIUBNoticesApp/1.0 (student project)"

        private const val CONNECT_TIMEOUT_MS = 15_000
        private const val READ_TIMEOUT_MS = 15_000
        private const val CACHE_SIZE = 30
    }
}
