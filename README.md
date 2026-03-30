# SpotiByeAds

> ⚠️ **WARNING**: This application was completely **vibe-coded** by an AI. Expect bugs, edge case failures, and perhaps some code that doesn't make perfect sense! Use at your own risk.

An Android app that automatically detects and skips Spotify advertisements by:

1. Listening to Spotify's media notifications via `NotificationListenerService`
2. Detecting when the track title equals `"Advertisement"`
3. Killing Spotify's background processes
4. Relaunching Spotify
5. Sending a `KEYCODE_MEDIA_PLAY` command to resume playback

## How to Install & Use

1. **Build and Install** the APK on your Android device (e.g., using Android Studio or `./gradlew assembleDebug`).
2. **Open SpotiByeAds** → tap **"Grant Permission"** → enable **SpotiByeAds Ad Detection** in the system settings.
3. **Play Spotify** — the app monitors in the background. When an ad plays, it will automatically force-stop Spotify, relaunch it, and send a play command.
4. **Check the Activity Log** in the app to see detected ads and actions taken.

> **Note**: The app includes an enable/disable toggle to quickly pause ad-skipping without needing to revoke notification access in system settings.
