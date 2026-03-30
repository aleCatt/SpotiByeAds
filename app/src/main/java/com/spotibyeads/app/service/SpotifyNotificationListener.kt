package com.spotibyeads.app.service

import android.app.ActivityManager
import android.app.Notification
import android.content.Context
import android.content.Intent
import android.media.AudioManager
import android.service.notification.NotificationListenerService
import android.service.notification.StatusBarNotification
import android.util.Log
import android.view.KeyEvent
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

// ── Data & shared state ─────────────────────────────────────────────

data class LogEntry(val timestamp: Long, val message: String) {
    val formattedTime: String
        get() = SimpleDateFormat("HH:mm:ss", Locale.getDefault()).format(Date(timestamp))
}

/**
 * Singleton event bus shared between the service and the UI.
 * Lives in the same process, so plain in-memory state is fine.
 */
object AdSkipLog {
    private val _events = MutableStateFlow<List<LogEntry>>(emptyList())
    val events: StateFlow<List<LogEntry>> = _events.asStateFlow()

    private val _isServiceConnected = MutableStateFlow(false)
    val isServiceConnected: StateFlow<Boolean> = _isServiceConnected.asStateFlow()

    private val _adsSkipped = MutableStateFlow(0)
    val adsSkipped: StateFlow<Int> = _adsSkipped.asStateFlow()

    fun log(message: String) {
        val entry = LogEntry(System.currentTimeMillis(), message)
        // Newest first, cap at 100
        _events.update { current -> (listOf(entry) + current).take(100) }
    }

    fun setServiceConnected(connected: Boolean) {
        _isServiceConnected.value = connected
    }

    fun incrementAdsSkipped() {
        _adsSkipped.update { it + 1 }
    }
}

// ── Notification listener service ───────────────────────────────────

class SpotifyNotificationListener : NotificationListenerService() {

    private val scope = CoroutineScope(Dispatchers.Main + SupervisorJob())
    private var isProcessing = false

    companion object {
        private const val TAG = "SpotiByeAds"
        private const val SPOTIFY_PACKAGE = "com.spotify.music"
        private const val AD_TITLE = "Advertisement"
        private const val KILL_DELAY_MS = 1500L
        private const val RELAUNCH_DELAY_MS = 2000L
    }

    // ── Lifecycle ────────────────────────────────────────────────────

    override fun onListenerConnected() {
        super.onListenerConnected()
        AdSkipLog.setServiceConnected(true)
        AdSkipLog.log("🟢 Service connected — listening for ads")
        Log.d(TAG, "Notification listener connected")
    }

    override fun onListenerDisconnected() {
        super.onListenerDisconnected()
        AdSkipLog.setServiceConnected(false)
        AdSkipLog.log("🔴 Service disconnected")
        Log.d(TAG, "Notification listener disconnected")
    }

    override fun onDestroy() {
        super.onDestroy()
        scope.cancel()
    }

    // ── Notification callback ────────────────────────────────────────

    override fun onNotificationPosted(sbn: StatusBarNotification?) {
        sbn ?: return
        if (sbn.packageName != SPOTIFY_PACKAGE) return
        if (isProcessing) return

        // Check if ad-skipping is enabled
        val prefs = getSharedPreferences("spotibyeads", Context.MODE_PRIVATE)
        if (!prefs.getBoolean("enabled", true)) return

        val extras = sbn.notification.extras ?: return
        val title = extras.getCharSequence(Notification.EXTRA_TITLE)?.toString() ?: return

        Log.d(TAG, "Spotify notification: title=\"$title\"")

        if (title == AD_TITLE) {
            AdSkipLog.log("🎯 Ad detected: \"$title\"")
            skipAd()
        }
    }

    // ── Ad-skip pipeline ─────────────────────────────────────────────

    private fun skipAd() {
        if (isProcessing) return
        isProcessing = true

        scope.launch {
            try {
                // 1. Kill Spotify background processes
                AdSkipLog.log("⏹ Stopping Spotify…")
                val am = getSystemService(Context.ACTIVITY_SERVICE) as ActivityManager
                am.killBackgroundProcesses(SPOTIFY_PACKAGE)

                // 2. Wait for process cleanup
                delay(KILL_DELAY_MS)

                // 3. Relaunch Spotify
                AdSkipLog.log("🔄 Relaunching Spotify…")
                val launchIntent = packageManager.getLaunchIntentForPackage(SPOTIFY_PACKAGE)
                if (launchIntent != null) {
                    launchIntent.addFlags(
                        Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
                    )
                    startActivity(launchIntent)
                } else {
                    AdSkipLog.log("❌ Spotify not found on this device")
                    return@launch
                }

                // 4. Wait for Spotify to initialise
                delay(RELAUNCH_DELAY_MS)

                // 5. Send play command
                AdSkipLog.log("▶ Sending play command…")
                sendPlayCommand()

                AdSkipLog.incrementAdsSkipped()
                AdSkipLog.log("✅ Ad skipped successfully!")
                Log.i(TAG, "Ad skipped")
            } catch (e: Exception) {
                AdSkipLog.log("❌ Error: ${e.message}")
                Log.e(TAG, "Skip failed", e)
            } finally {
                isProcessing = false
            }
        }
    }

    private fun sendPlayCommand() {
        val audioManager = getSystemService(Context.AUDIO_SERVICE) as AudioManager
        val down = KeyEvent(KeyEvent.ACTION_DOWN, KeyEvent.KEYCODE_MEDIA_PLAY)
        val up = KeyEvent(KeyEvent.ACTION_UP, KeyEvent.KEYCODE_MEDIA_PLAY)
        audioManager.dispatchMediaKeyEvent(down)
        audioManager.dispatchMediaKeyEvent(up)
    }
}
