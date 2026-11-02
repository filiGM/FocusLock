# FocusLock

Android app that limits and locks TikTok, Facebook, Instagram and YouTube, and shows how long you spend on them.

## What it does

- Tracks the foreground time of each chosen app (default: TikTok, Facebook, Instagram, YouTube, plus their Lite versions).
- Daily limit per app (default **30 min**).
- When the limit is reached, a full-screen lock appears over the app and the app is stopped.
- The app stays locked for a set time (default **3 hours**), then you get a short window (default **10 min**). When the window ends it locks again for another 3 hours.
- All three numbers (limit, locked time, window) are editable in the app. You can add or remove any app.
- Stats screen: time per app today, total today, last 7 days by day and by app.
- While an app is locked you cannot switch FocusLock off or edit the rules.
- Restarts itself after the phone reboots. Limits reset at midnight.

## Build the APK (pick one way)

### A. Android Studio (easiest on a PC)
1. Install Android Studio (https://developer.android.com/studio).
2. File > Open > choose the `FocusLock` folder. Wait for the Gradle sync to finish.
3. Menu **Build > Build Bundle(s) / APK(s) > Build APK(s)**.
4. Click "locate" in the popup. File: `app/build/outputs/apk/debug/app-debug.apk`.

### B. Command line (PC with JDK 17 + Android SDK)
```
cd FocusLock
gradle wrapper --gradle-version 8.5     # once, creates ./gradlew
./gradlew assembleDebug                 # Windows: gradlew.bat assembleDebug
```
APK: `app/build/outputs/apk/debug/app-debug.apk`

### C. No PC needed (GitHub Actions, works from a phone browser)
1. Create a free GitHub account and a new repository.
2. Upload all files of this folder (keep the `.github/workflows/build.yml` path).
3. Open the **Actions** tab > **Build APK** > **Run workflow**.
4. When it turns green, open the run and download the artifact **FocusLock-APK** (a zip containing `app-debug.apk`).

## Install on your phone
1. Copy `app-debug.apk` to the phone and tap it.
2. Allow "Install unknown apps" for your file manager/browser when asked. If Play Protect warns, choose "Install anyway" (it is a debug build you made yourself).

## First-time setup in the app
Tap the four permission buttons, then switch **Monitoring and locking** on.
- **Usage access**: find FocusLock, turn on.
- **Display over other apps**: turn on (this is what lets the lock screen appear on top of TikTok etc.).
- **Notifications**: Allow.
- **Battery optimization**: choose "Don't optimize"/"Unrestricted" so Android does not stop it.

Tecno / Infinix / itel / Xiaomi / Samsung phones are aggressive about killing background apps. Also do these:
- Settings > Apps > FocusLock > Battery > **Unrestricted**.
- Turn on **Autostart** for FocusLock (phone manager / security app).
- Allow **Pop-up windows while running in background** if the phone has that option.
- In the recent-apps screen, lock/pin FocusLock so "clear all" does not close it.

## Test quickly
Set rules to 1 / 1 / 1 minutes, open TikTok for a minute, and confirm the lock screen appears. Then set your real values (30 / 180 / 10).

## Honest limits
- Android does not allow a normal app to force-close another app. FocusLock covers it with the lock screen and stops its background process. If you remove permissions, uninstall, or force-stop FocusLock, the lock stops working. It is a self-discipline tool, not a parental-control lock.
- Android keeps detailed usage history for only about a week.
- Usage is measured as time the app is on screen. Facebook/Instagram/TikTok inside other apps' browsers are not counted.
- Package names are for the common Play Store versions. If your version differs, use **Choose apps to lock**.
