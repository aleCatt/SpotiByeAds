package com.spotibyeads.app

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.provider.Settings
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.systemBars
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.spotibyeads.app.service.AdSkipLog
import com.spotibyeads.app.service.SpotifyNotificationListener
import com.spotibyeads.app.ui.theme.SpotiByeAdsTheme
import rikka.shizuku.Shizuku

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            SpotiByeAdsTheme {
                SpotiByeAdsApp()
            }
        }
    }
}

// ── Main screen ──────────────────────────────────────────────────────

@Composable
fun SpotiByeAdsApp() {
    val context = LocalContext.current

    // Observe shared state from the service
    val events by AdSkipLog.events.collectAsState()
    val isConnected by AdSkipLog.isServiceConnected.collectAsState()
    val adsSkipped by AdSkipLog.adsSkipped.collectAsState()

    val hasPermission = remember { mutableStateOf(isNotificationAccessGranted(context)) }
    val hasShizuku = remember { mutableStateOf(checkShizukuPermission()) }
    val isEnabled = remember {
        mutableStateOf(
            context.getSharedPreferences("spotibyeads", Context.MODE_PRIVATE)
                .getBoolean("enabled", true)
        )
    }

    // Re-check permission periodically (user may grant it from Settings)
    LaunchedEffect(Unit) {
        while (true) {
            hasPermission.value = isNotificationAccessGranted(context)
            hasShizuku.value = checkShizukuPermission()
            kotlinx.coroutines.delay(2000)
        }
    }

    Surface(
        modifier = Modifier.fillMaxSize(),
        color = Color(0xFF0A0A0A)
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .windowInsetsPadding(WindowInsets.systemBars)
                .padding(horizontal = 20.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Spacer(modifier = Modifier.height(32.dp))

            // ── Header ───────────────────────────────────────────────
            Text(
                text = "SpotiByeAds",
                fontSize = 30.sp,
                fontWeight = FontWeight.Bold,
                color = Color(0xFF1DB954)
            )
            Text(
                text = "Automatic Spotify ad skipper",
                fontSize = 14.sp,
                color = Color(0xFF888888),
                modifier = Modifier.padding(top = 4.dp)
            )

            Spacer(modifier = Modifier.height(28.dp))

            // ── Status card ──────────────────────────────────────────
            StatusCard(
                hasPermission = hasPermission.value,
                hasShizuku = hasShizuku.value,
                isConnected = isConnected,
                isEnabled = isEnabled.value,
                onGrantPermission = {
                    context.startActivity(Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS))
                },
                onRequestShizuku = {
                    try {
                        if (Shizuku.pingBinder()) {
                            Shizuku.requestPermission(1)
                        } else {
                            AdSkipLog.log("❌ Shizuku Manager is not running.")
                        }
                    } catch (e: Exception) {
                        AdSkipLog.log("❌ Shizuku error: ${e.message}")
                    }
                },
                onToggle = { enabled ->
                    isEnabled.value = enabled
                    context.getSharedPreferences("spotibyeads", Context.MODE_PRIVATE)
                        .edit().putBoolean("enabled", enabled).apply()

                    if (enabled) {
                        AdSkipLog.log("⚡ Ad skipping enabled")
                    } else {
                        AdSkipLog.log("⏸ Ad skipping disabled")
                    }
                }
            )

            Spacer(modifier = Modifier.height(14.dp))

            // ── Stats card ───────────────────────────────────────────
            StatsCard(adsSkipped = adsSkipped)

            Spacer(modifier = Modifier.height(14.dp))

            // ── Activity log ─────────────────────────────────────────
            Text(
                text = "ACTIVITY LOG",
                color = Color(0xFF666666),
                fontSize = 11.sp,
                fontWeight = FontWeight.SemiBold,
                letterSpacing = 1.sp,
                modifier = Modifier
                    .align(Alignment.Start)
                    .padding(start = 4.dp, bottom = 6.dp)
            )

            EventLogCard(
                events = events,
                modifier = Modifier.weight(1f)
            )

            Spacer(modifier = Modifier.height(16.dp))
        }
    }
}

// ── Status card ──────────────────────────────────────────────────────

@Composable
private fun StatusCard(
    hasPermission: Boolean,
    hasShizuku: Boolean,
    isConnected: Boolean,
    isEnabled: Boolean,
    onGrantPermission: () -> Unit,
    onRequestShizuku: () -> Unit,
    onToggle: (Boolean) -> Unit,
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(containerColor = Color(0xFF161616))
    ) {
        Column(modifier = Modifier.padding(20.dp)) {

            // Permission row
            StatusRow(
                isActive = hasPermission,
                label = if (hasPermission) "Notification access granted" else "Notification access required"
            )

            if (!hasPermission) {
                Spacer(modifier = Modifier.height(12.dp))
                Button(
                    onClick = onGrantPermission,
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF1DB954)),
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text("Grant Permission", fontWeight = FontWeight.SemiBold)
                }
            }

            HorizontalDivider(
                modifier = Modifier.padding(vertical = 14.dp),
                color = Color(0xFF2A2A2A)
            )

            // Shizuku row
            StatusRow(
                isActive = hasShizuku,
                label = if (hasShizuku) "Shizuku connected" else "Shizuku access required"
            )

            if (!hasShizuku) {
                Spacer(modifier = Modifier.height(12.dp))
                Button(
                    onClick = onRequestShizuku,
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF1DB954)),
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text("Grant Shizuku Access", fontWeight = FontWeight.SemiBold)
                }
            }

            HorizontalDivider(
                modifier = Modifier.padding(vertical = 14.dp),
                color = Color(0xFF2A2A2A)
            )

            // Listener row
            StatusRow(
                isActive = isConnected,
                label = if (isConnected) "Listener active" else "Listener inactive"
            )

            HorizontalDivider(
                modifier = Modifier.padding(vertical = 14.dp),
                color = Color(0xFF2A2A2A)
            )

            // Toggle row
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text(
                    text = "Ad skipping",
                    color = Color.White,
                    fontSize = 15.sp,
                    fontWeight = FontWeight.Medium
                )
                Switch(
                    checked = isEnabled,
                    onCheckedChange = onToggle,
                    colors = SwitchDefaults.colors(
                        checkedThumbColor = Color.White,
                        checkedTrackColor = Color(0xFF1DB954),
                        uncheckedThumbColor = Color(0xFF888888),
                        uncheckedTrackColor = Color(0xFF333333)
                    )
                )
            }
        }
    }
}

@Composable
private fun StatusRow(isActive: Boolean, label: String) {
    val dotColor by animateColorAsState(
        targetValue = if (isActive) Color(0xFF1DB954) else Color(0xFF555555),
        animationSpec = tween(400),
        label = "dot"
    )

    Row(verticalAlignment = Alignment.CenterVertically) {
        Box(
            modifier = Modifier
                .size(9.dp)
                .clip(CircleShape)
                .background(dotColor)
        )
        Spacer(modifier = Modifier.width(10.dp))
        Text(text = label, color = Color(0xFFCCCCCC), fontSize = 14.sp)
    }
}

// ── Stats card ───────────────────────────────────────────────────────

@Composable
private fun StatsCard(adsSkipped: Int) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(containerColor = Color(0xFF161616))
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .background(
                    Brush.horizontalGradient(
                        colors = listOf(
                            Color(0xFF1DB954).copy(alpha = 0.12f),
                            Color(0xFF161616)
                        )
                    )
                )
                .padding(20.dp)
        ) {
            Column {
                Text(
                    text = "ADS SKIPPED",
                    color = Color(0xFF666666),
                    fontSize = 11.sp,
                    fontWeight = FontWeight.SemiBold,
                    letterSpacing = 1.sp,
                )
                Text(
                    text = "$adsSkipped",
                    color = Color(0xFF1DB954),
                    fontSize = 40.sp,
                    fontWeight = FontWeight.Bold
                )
            }
        }
    }
}

// ── Event log card ───────────────────────────────────────────────────

@Composable
private fun EventLogCard(
    events: List<com.spotibyeads.app.service.LogEntry>,
    modifier: Modifier = Modifier,
) {
    Card(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(containerColor = Color(0xFF161616))
    ) {
        if (events.isEmpty()) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(24.dp),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = "No events yet.\nPlay Spotify to start monitoring.",
                    color = Color(0xFF444444),
                    fontSize = 14.sp,
                    textAlign = TextAlign.Center,
                    lineHeight = 22.sp
                )
            }
        } else {
            LazyColumn(
                modifier = Modifier.padding(14.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                items(events, key = { it.timestamp }) { entry ->
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.Top
                    ) {
                        Text(
                            text = entry.formattedTime,
                            color = Color(0xFF444444),
                            fontSize = 11.sp,
                            modifier = Modifier.width(56.dp)
                        )
                        Text(
                            text = entry.message,
                            color = Color(0xFFBBBBBB),
                            fontSize = 13.sp
                        )
                    }
                }
            }
        }
    }
}

// ── Helpers ──────────────────────────────────────────────────────────

private fun isNotificationAccessGranted(context: Context): Boolean {
    val flat = Settings.Secure.getString(
        context.contentResolver,
        "enabled_notification_listeners"
    ) ?: return false
    val cn = ComponentName(context, SpotifyNotificationListener::class.java)
    return flat.contains(cn.flattenToString())
}

private fun checkShizukuPermission(): Boolean {
    return try {
        Shizuku.pingBinder() && Shizuku.checkSelfPermission() == android.content.pm.PackageManager.PERMISSION_GRANTED
    } catch (e: Exception) {
        false
    }
}
