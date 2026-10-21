# Lumen

**Unlimited, original-quality Google Photos backup for Android.**

![Downloads](https://img.shields.io/github/downloads/Reiux/lumen-android/total?style=for-the-badge)
![License](https://img.shields.io/github/license/Reiux/lumen-android?style=for-the-badge)
[![Android](https://img.shields.io/badge/android-14%20%7C%2015%20%7C%2016-3DDC84?style=for-the-badge&logo=android&logoColor=white)](https://developer.android.com)

> [!NOTE]
> **No Root or Xposed Required.** Lumen runs on stock Android devices (Android 8.0+) without root access, unlocked bootloaders, or custom ROMs.

Lumen is an open-source Android app that backs up your photos and videos to Google Photos in full original quality — without taking up any of your Google One account storage space.

Everything is sent directly from your phone to Google Photos. There are no middleman servers, no paid subscriptions, and no tracking.

---

## Security & Account Privacy

**We never see or store your Google password, and we have zero access to your Google Account.**

When you sign in, Google gives Lumen an **OAuth token**. Think of an OAuth token like a temporary hotel key card:
- It **only** gives the app permission to drop photos into your Google Photos library.
- It **cannot** be used to read your emails, access your personal Google Drive, or change your password.
- All uploads travel directly from your phone to Google (`photos.googleapis.com`). Nothing passes through any third-party server.

---

## Features

- **Free Unlimited Original Quality** — Uploads photos and videos in full resolution using Pixel device request headers so they don't count against your Google storage.
- **Direct & Private** — Direct phone-to-Google transfer. No external servers or analytics.
- **Material 3 Expressive Design** — Built with Jetpack Compose featuring fluid spring animations (`Spring.DampingRatioMediumBouncy`), shape-morphing controls, and customizable accent palettes (Dynamic Monet, Prism Rainbow, Matrix Hacker Green, Ocean Blue, Emerald Green, Sunset Coral, Royal Purple).
- **Adaptive Foldable & Tablet UI** — Clean 2-pane side-by-side layouts for tablets, foldables, and landscape screens.
- **Android 16 Live Status Bar Chips** — Status bar progress chips (`1/100 • 45%`) with real-time transfer speeds (`MB/s`).
- **Smart Duplicate Prevention** — Uses a fast local SHA-1 hash cache and Google Photos cloud checks so the app never uploads duplicate media.
- **Automatic Background Sync** — Uploads automatically when your phone is connected to Wi-Fi or charging.
- **Dev Tools & Self-Diagnostics** — In-app diagnostic tests, OEM background autostart guides (MIUI/HyperOS, OneUI, ColorOS), and a real-time live log viewer.
- **Hidden Easter Eggs** — Contains 3 hidden Easter Eggs throughout the app for curious users to discover.

---

## Supported Media Formats

<details>
<summary>Show supported media formats</summary>

| Category | Formats |
|---|---|
| Images | JPEG, PNG, WEBP, HEIC, GIF, RAW (DNG) |
| Videos | MP4, MOV, WEBM, MKV, 3GP, AVI |

</details>

---

## Requirements

- Android 8.0 (API 26) or newer
- Active Google Account
- Wi-Fi or Cellular network connection

---

## Download

Download the latest APK from the [Releases](https://github.com/Reiux/lumen-android/releases) page:
- `lumen-release.apk` (Production build)
- `lumen-dev-debug.apk` (Developer build with debug tools)

---

## Building from Source

```bash
git clone https://github.com/Reiux/lumen-android.git
cd lumen-android

# Build Debug and Release APKs
env -u ANDROID_PREFS_ROOT ./gradlew assembleDebug assembleRelease
```

---

## Credits & Acknowledgments

- **Developer**: Created by **[Reiux](https://github.com/Reiux)**.
- **Core Logic & Protocol**: Special thanks to **[xob0t/gotohp](https://github.com/xob0t/gotohp)**, creator of the reverse-engineered Google Photos upload logic, protocol handling, and authentication engine.

---

## Disclaimer

Lumen is an independent open-source application and is not affiliated with, endorsed by, or sponsored by Google LLC. Users use this app and services at their own discretion and risk.

---

## License

Distributed under the [Apache License 2.0](LICENSE).
