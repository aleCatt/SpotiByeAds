# Proguard rules for SpotiByeAds

# Keep the notification listener service
-keep class com.spotibyeads.app.service.SpotifyNotificationListener { *; }

# Compose
-dontwarn androidx.compose.**
