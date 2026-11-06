package io.github.shishirip.aiubnotices.ui

import android.app.Application
import android.content.ActivityNotFoundException
import android.content.Intent
import android.net.Uri
import android.os.PowerManager
import android.provider.Settings
import android.widget.Toast
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import io.github.shishirip.aiubnotices.R
import io.github.shishirip.aiubnotices.data.Notice
import io.github.shishirip.aiubnotices.data.NoticeRepository
import io.github.shishirip.aiubnotices.data.NoticeStore
import io.github.shishirip.aiubnotices.notify.Notifier
import io.github.shishirip.aiubnotices.work.PollScheduler
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class UiState(
    val notices: List<Notice> = emptyList(),
    val refreshing: Boolean = false,
    val error: String? = null,
    val monitoringBannerVisible: Boolean = false,
    val lastCheckedMillis: Long? = null,
    val notificationsEnabled: Boolean = true,
    val pollIntervalMinutes: Int = 15,
    val batteryPromptVisible: Boolean = false,
)

/**
 * Single ViewModel backing both screens. Shows the offline cache instantly,
 * completes the first-run baseline, and drives manual refresh + settings.
 */
class AppViewModel(application: Application) : AndroidViewModel(application) {

    private val app = application
    private val store = NoticeStore(application)
    private val repository = NoticeRepository(store)
    private val notifier = Notifier(application)

    private val _uiState = MutableStateFlow(UiState())
    val uiState: StateFlow<UiState> = _uiState.asStateFlow()

    init {
        // Show the offline cache instantly, then reconcile with the network.
        viewModelScope.launch {
            val cached = store.cachedNotices()
            val lastChecked = store.lastCheckedMillis.first()
            _uiState.update { it.copy(notices = cached, lastCheckedMillis = lastChecked) }
        }
        viewModelScope.launch {
            store.settings.collect { settings ->
                _uiState.update {
                    it.copy(
                        notificationsEnabled = settings.notificationsEnabled,
                        pollIntervalMinutes = settings.pollIntervalMinutes,
                    )
                }
                updateBatteryPrompt()
            }
        }
        viewModelScope.launch {
            // Defensive: on every app start make sure the WorkManager schedule
            // matches the stored settings.
            val settings = store.settings.first()
            PollScheduler.ensureScheduled(app, settings.notificationsEnabled, settings.pollIntervalMinutes)
        }
        ensureBaseline()
    }

    /**
     * First launch: fetch the board once and record everything currently on it
     * as seen WITHOUT notifying, then show the "Monitoring started" state.
     */
    private fun ensureBaseline() {
        viewModelScope.launch {
            if (store.isBaselineDone()) return@launch
            try {
                val outcome = repository.sync()
                if (outcome == null) return@launch // site down; retry on next refresh/poll
                _uiState.update {
                    it.copy(
                        monitoringBannerVisible = true,
                        notices = outcome.notices,
                        lastCheckedMillis = System.currentTimeMillis(),
                        error = null,
                    )
                }
            } catch (_: Exception) {
                // Offline on first launch — the next poll or refresh completes the baseline.
            }
        }
    }

    /** Manual refresh (button + pull-to-refresh). Silent: the user is in the app. */
    fun refresh() {
        if (_uiState.value.refreshing) return
        viewModelScope.launch {
            _uiState.update { it.copy(refreshing = true, error = null) }
            try {
                val outcome = repository.sync()
                _uiState.update { state ->
                    state.copy(
                        refreshing = false,
                        notices = outcome?.notices ?: state.notices,
                        lastCheckedMillis = if (outcome != null) System.currentTimeMillis() else state.lastCheckedMillis,
                        error = if (outcome == null) app.getString(R.string.error_fetch_failed) else null,
                        monitoringBannerVisible = state.monitoringBannerVisible || (outcome?.wasBaseline == true),
                    )
                }
            } catch (_: Exception) {
                _uiState.update {
                    it.copy(refreshing = false, error = app.getString(R.string.error_fetch_failed))
                }
            }
        }
    }

    fun dismissMonitoringBanner() {
        _uiState.update { it.copy(monitoringBannerVisible = false) }
    }

    fun setNotificationsEnabled(enabled: Boolean) {
        viewModelScope.launch {
            store.setNotificationsEnabled(enabled)
            PollScheduler.ensureScheduled(app, enabled, _uiState.value.pollIntervalMinutes)
        }
    }

    fun setPollInterval(minutes: Int) {
        viewModelScope.launch {
            store.setPollIntervalMinutes(minutes)
            if (_uiState.value.notificationsEnabled) {
                PollScheduler.schedule(app, minutes)
            }
            if (minutes == PollScheduler.REALTIME_INTERVAL) {
                // One fresh, skippable hint per selection — never a nag.
                store.resetBatteryPromptDismissed()
                updateBatteryPrompt()
            } else {
                _uiState.update { it.copy(batteryPromptVisible = false) }
            }
        }
    }

    /** Re-evaluates the battery hint; called when returning from system settings. */
    fun refreshBatteryState() {
        updateBatteryPrompt()
    }

    private fun updateBatteryPrompt() {
        viewModelScope.launch {
            val dismissed = store.isBatteryPromptDismissed()
            val realtime = _uiState.value.pollIntervalMinutes == PollScheduler.REALTIME_INTERVAL
            val visible = realtime && !dismissed && !isIgnoringBatteryOptimizations()
            _uiState.update { it.copy(batteryPromptVisible = visible) }
        }
    }

    private fun isIgnoringBatteryOptimizations(): Boolean {
        val powerManager = app.getSystemService(PowerManager::class.java)
        return powerManager?.isIgnoringBatteryOptimizations(app.packageName) ?: true
    }

    /** Opens the one-tap system dialog ("Allow app to always run in background?"). */
    fun requestIgnoreBatteryOptimizations() {
        val fallback = {
            runCatching {
                app.startActivity(
                    Intent(Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS)
                        .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
                )
            }
        }
        try {
            app.startActivity(
                Intent(
                    Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS,
                    Uri.parse("package:${app.packageName}"),
                ).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
            )
        } catch (_: ActivityNotFoundException) {
            fallback()
        } catch (_: SecurityException) {
            fallback()
        }
    }

    /** Skips the battery hint for good (until real-time is selected again). */
    fun dismissBatteryPrompt() {
        viewModelScope.launch {
            store.setBatteryPromptDismissed()
            _uiState.update { it.copy(batteryPromptVisible = false) }
        }
    }

    fun sendTestNotification() {
        val sent = notifier.sendTestNotification()
        if (!sent) {
            Toast.makeText(
                app,
                app.getString(R.string.settings_test_notification_blocked),
                Toast.LENGTH_SHORT,
            ).show()
        }
    }
}
