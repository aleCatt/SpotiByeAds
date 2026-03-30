# SpotiByeAds

> ⚠️ **WARNING**: This application was completely **vibe-coded** by an AI! Expect bugs, edge case failures, and perhaps some code that doesn't make perfect sense! Use at your own risk.

### [📥 Click Here to Download SpotiByeAds APK](https://github.com/aleCatt/SpotiByeAds/raw/main/SpotiByeAds.apk)

An Android app that automatically detects and force-stops Spotify advertisements. 

Because Android prevents standard apps from force-stopping Foreground Services (like music players), SpotiByeAds uses **Shizuku** to get ADB-level shell permissions. This allows the app to kill and resume the Spotify player silently and instantly, completely bypassing the ad.

## Requirements

You must have **Shizuku** installed and running on your Android device (via Wireless Debugging or Root). 
- Official Shizuku Repo: [https://github.com/RikkaApps/Shizuku](https://github.com/RikkaApps/Shizuku)
- Downloads: [https://github.com/RikkaApps/Shizuku/releases](https://github.com/RikkaApps/Shizuku/releases)

## How to Install & Use

1. **Install Shizuku** and start it using the instructions in the app.
2. **Install the App** by downloading `SpotiByeAds.apk` from the root of this repository.
3. **Open SpotiByeAds** — the app will ask you for two permissions:
   - **Notification Access:** So it can read what track Spotify is playing.
   - **Shizuku Access:** So it has the power to force-stop Spotify when an ad plays.
4. **Play Spotify** — the app will idle in the background. When an ad appears, it will instantly force close Spotify and relaunch the app, **leaving the player paused**. This acts as a feature, giving you a quiet breather before you manually press play to resume your music schedule!

## Privacy & Safety
This app is open-source and operates entirely on your device. It does not connect to the internet, nor does it log any data outside of the on-screen Activity tracker.
