package io.github.shishirip.aiubnotices.data

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import org.json.JSONArray
import org.json.JSONObject

private val Context.dataStore: DataStore<Preferences> by preferencesDataStore(name = "aiub_notices")

/**
 * All persisted state: the set of already-seen notice URLs, the cached notice
 * list (for offline use), user settings, and the baseline flag.
 */
class NoticeStore(private val context: Context) {

    data class Settings(
        val notificationsEnabled: Boolean = true,
        val pollIntervalMinutes: Int = DEFAULT_POLL_INTERVAL_MINUTES,
    )

    private companion object {
        val SEEN_URLS = stringPreferencesKey("seen_urls")
        val BASELINE_DONE = booleanPreferencesKey("baseline_done")
        val NOTIFICATIONS_ENABLED = booleanPreferencesKey("notifications_enabled")
        val POLL_INTERVAL_MINUTES = intPreferencesKey("poll_interval_minutes")
        val CACHED_NOTICES = stringPreferencesKey("cached_notices")
        val LAST_CHECKED_MILLIS = longPreferencesKey("last_checked_millis")
        val BATTERY_PROMPT_DISMISSED = booleanPreferencesKey("battery_prompt_dismissed")

        /** Seen-set size cap; oldest entries are pruned first. */
        const val MAX_SEEN_URLS = 500
        const val DEFAULT_POLL_INTERVAL_MINUTES = 15
    }

    val settings: Flow<Settings> = context.dataStore.data.map { prefs ->
        Settings(
            notificationsEnabled = prefs[NOTIFICATIONS_ENABLED] ?: true,
            pollIntervalMinutes = prefs[POLL_INTERVAL_MINUTES] ?: DEFAULT_POLL_INTERVAL_MINUTES,
        )
    }

    val lastCheckedMillis: Flow<Long?> = context.dataStore.data.map { prefs ->
        prefs[LAST_CHECKED_MILLIS]
    }

    suspend fun seenUrls(): Set<String> =
        (context.dataStore.data.first()[SEEN_URLS].orEmpty())
            .split('\n')
            .map { it.trim() }
            .filter { it.isNotEmpty() }
            .toSet()

    /**
     * Records [urls] as seen. The list keeps insertion order so the oldest
     * entries can be pruned; the set never grows beyond [MAX_SEEN_URLS].
     */
    suspend fun markSeen(urls: Collection<String>) {
        if (urls.isEmpty()) return
        context.dataStore.edit { prefs ->
            val existing = prefs[SEEN_URLS].orEmpty().split('\n').filter { it.isNotBlank() }
            val merged = (existing + urls.map { it.trim() }).distinct().takeLast(MAX_SEEN_URLS)
            prefs[SEEN_URLS] = merged.joinToString("\n")
        }
    }

    /**
     * True until the very first successful sync. The first cycle records
     * everything currently on the board as seen *without* notifying
     * (the baseline).
     */
    suspend fun isBaselineDone(): Boolean =
        context.dataStore.data.first()[BASELINE_DONE] ?: false

    suspend fun markBaselineDone() {
        context.dataStore.edit { prefs -> prefs[BASELINE_DONE] = true }
    }

    suspend fun setNotificationsEnabled(enabled: Boolean) {
        context.dataStore.edit { prefs -> prefs[NOTIFICATIONS_ENABLED] = enabled }
    }

    suspend fun setPollIntervalMinutes(minutes: Int) {
        context.dataStore.edit { prefs -> prefs[POLL_INTERVAL_MINUTES] = minutes }
    }

    /** True when the user skipped the real-time battery hint. */
    suspend fun isBatteryPromptDismissed(): Boolean =
        context.dataStore.data.first()[BATTERY_PROMPT_DISMISSED] ?: false

    suspend fun setBatteryPromptDismissed() {
        context.dataStore.edit { prefs -> prefs[BATTERY_PROMPT_DISMISSED] = true }
    }

    /** Called when real-time is (re)selected: the hint gets one fresh chance. */
    suspend fun resetBatteryPromptDismissed() {
        context.dataStore.edit { prefs -> prefs[BATTERY_PROMPT_DISMISSED] = false }
    }

    suspend fun setLastChecked(millis: Long) {
        context.dataStore.edit { prefs -> prefs[LAST_CHECKED_MILLIS] = millis }
    }

    /** Persists the latest notice list so the app is usable fully offline. */
    suspend fun cacheNotices(notices: List<Notice>) {
        val array = JSONArray()
        notices.forEach { notice ->
            array.put(
                JSONObject()
                    .put("title", notice.title)
                    .put("description", notice.description)
                    .put("date", notice.dateText)
                    .put("url", notice.url),
            )
        }
        context.dataStore.edit { prefs -> prefs[CACHED_NOTICES] = array.toString() }
    }

    suspend fun cachedNotices(): List<Notice> {
        val raw = context.dataStore.data.first()[CACHED_NOTICES].orEmpty()
        if (raw.isBlank()) return emptyList()
        return runCatching {
            val array = JSONArray(raw)
            (0 until array.length()).mapNotNull { index ->
                val obj = array.optJSONObject(index) ?: return@mapNotNull null
                Notice(
                    title = obj.optString("title"),
                    description = obj.optString("description"),
                    dateText = obj.optString("date"),
                    url = obj.optString("url"),
                )
            }
        }.getOrDefault(emptyList())
    }
}
