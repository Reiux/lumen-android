# Lumen

Lumen is an open-source Android app for backing up your photos and videos to Google Photos in full original quality without consuming your Google One storage quota.

It sends uploads directly from your phone to Google Photos with no middleman servers, no tracking, and no paid subscriptions.

---

## Features

- **Free Unlimited Backup**: Uploads photos and videos in original quality using Pixel device headers.
- **Direct & Private**: Communicates directly with Google servers. No third-party servers, proxies, or analytics.
- **Material 3 Expressive UI**: Built with Jetpack Compose featuring spring animations, shape-morphing controls, and customizable accent palettes.
- **Adaptive 2-Pane Layouts**: Designed for phones, foldables, and tablets in both portrait and landscape modes.
- **Android 16 Live Progress**: Status bar progress chips with real-time transfer speeds and item counts.
- **Smart Duplicate Prevention**: Local SHA-1 hash cache combined with Google Photos cloud checks to avoid uploading duplicate media.
- **Background Sync**: Automatic background uploads when connected to Wi-Fi or charging.
- **Dev Diagnostics**: Built-in self-tests, OEM autostart background guides (MIUI/HyperOS, OneUI, ColorOS), and real-time live log viewer.

---

## Getting Started

### Download APK
Grab the latest APK from the [Releases](https://github.com/Reiux/lumen-android/releases) page:
- `lumen-release.apk` (Production build)
- `lumen-dev-debug.apk` (Developer build with debug tools)

### Building from Source

**Prerequisites:**
- JDK 21
- Android SDK 36 (Android 16)

```bash
git clone https://github.com/Reiux/lumen-android.git
cd lumen-android

# Build Debug and Release APKs
env -u ANDROID_PREFS_ROOT ./gradlew assembleDebug assembleRelease
```

---

## Tech Stack

- **UI**: Jetpack Compose, Material 3 Expressive, Compose Navigation
- **Architecture**: MVVM, Kotlin Coroutines, StateFlow
- **Network**: OkHttp 4, Protobuf Lite
- **Background Work**: AndroidX WorkManager, Foreground Services

---

## License

Distributed under the MIT License. See `LICENSE` for details.
