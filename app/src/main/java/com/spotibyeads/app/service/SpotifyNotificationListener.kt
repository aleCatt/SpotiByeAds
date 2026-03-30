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
import rikka.shizuku.Shizuku
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

    private var lastLoggedTitle = ""

    override fun onNotificationPosted(sbn: StatusBarNotification?) {
        sbn ?: return
        if (sbn.packageName != SPOTIFY_PACKAGE) return
        if (isProcessing) return

        // Check if ad-skipping is enabled
        val prefs = getSharedPreferences("spotibyeads", Context.MODE_PRIVATE)
        if (!prefs.getBoolean("enabled", true)) return

        val extras = sbn.notification.extras ?: return
        val title = extras.getCharSequence(Notification.EXTRA_TITLE)?.toString() ?: "Unknown Title"
        val text = extras.getCharSequence(Notification.EXTRA_TEXT)?.toString() ?: "Unknown Text"

        // Log any title change to the UI so we can see what Spotify is sending!
        if (title != lastLoggedTitle) {
            AdSkipLog.log("🎵 Spotify update: Title='$title' | Text='$text'")
            lastLoggedTitle = title
        }

        // Some ads put "Advertisement" in the title, others put it in the text. Let's check both!
        // We use .contains() because the text can be "Advertisement • 2 of 2"
        val isAd = title.contains("Advertisement", ignoreCase = true) || 
                   text.contains("Advertisement", ignoreCase = true) ||
                   (title.equals("Spotify", ignoreCase = true) && text.contains("Spotify", ignoreCase = true))

        if (isAd) {
            AdSkipLog.log("🎯 Ad detected! Triggering skip...")
            skipAd()
        }
    }

    // ── Ad-skip pipeline ─────────────────────────────────────────────

    private fun skipAd() {
        if (isProcessing) return
        isProcessing = true

        scope.launch {
            try {
                // 1. Kill Spotify via Shizuku
                AdSkipLog.log("⏹ Force-stopping Spotify via Shizuku…")
                
                if (Shizuku.pingBinder() && Shizuku.checkSelfPermission() == android.content.pm.PackageManager.PERMISSION_GRANTED) {
                    runShizukuShell("am force-stop $SPOTIFY_PACKAGE")
                } else {
                    AdSkipLog.log("❌ Shizuku not connected or permitted! Please check app.")
                    return@launch
                }

                // 2. Wait for process cleanup
                delay(KILL_DELAY_MS)

                // 3. Relaunch Spotify via Shizuku (bypasses Android background constraints)
                AdSkipLog.log("🔄 Relaunching Spotify…")
                runShizukuShell("monkey -p $SPOTIFY_PACKAGE -c android.intent.category.LAUNCHER 1")

                // 4. Wait for Spotify to initialise
                delay(RELAUNCH_DELAY_MS)

                // 5. Send play command via Shizuku (Keycode 126 = MEDIA_PLAY)
                AdSkipLog.log("▶ Sending play command…")
                runShizukuShell("input keyevent 126")

                AdSkipLog.incrementAdsSkipped()
                AdSkipLog.log("✅ Ad skipped successfully!")
                Log.i(TAG, "Ad skipped")
            } catch (e: Exception) {
                if (e is SecurityException && e.message?.contains("Shizuku") == true) {
                    AdSkipLog.log("❌ Shizuku Permission Denied!")
                } else {
                    AdSkipLog.log("❌ Error: ${e.message}")
                }
                Log.e(TAG, "Skip failed", e)
            } finally {
                isProcessing = false
            }
        }
    }

    private fun runShizukuShell(command: String) {
        val method = rikka.shizuku.Shizuku::class.java.getDeclaredMethod(
            "newProcess",
            Array<String>::class.java,
            Array<String>::class.java,
            String::class.java
        )
        method.isAccessible = true
        val process = method.invoke(
            null,
            arrayOf("sh", "-c", command),
            null,
            null
        ) as Process
        process.waitFor()
    }
}
