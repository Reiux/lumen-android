# Lumen

**Unlimited, full-quality photo and video backup for your phone.**

![Downloads](https://img.shields.io/github/downloads/Reiux/lumen-android/total?style=for-the-badge)
![License](https://img.shields.io/github/license/Reiux/lumen-android?style=for-the-badge)
[![Android](https://img.shields.io/badge/android-14%20%7C%2015%20%7C%2016-3DDC84?style=for-the-badge&logo=android&logoColor=white)](https://developer.android.com)

> [!NOTE]
> **Works on any regular Android phone.** You do not need root, special phone modifications, or custom software to use Lumen.

Lumen is an open-source Android app that backs up your photos and videos to Google Photos in full original quality without filling up your Google account storage space.

Normally, storing full-resolution photos counts against your Google storage limit. Lumen sends your uploads directly from your phone to Google Photos using request headers that tell Google the files are coming from an eligible Pixel device, so they store for free in full quality.

Everything travels directly from your phone to Google. There are no middleman servers, no hidden fees, and no ads or tracking.

---

## Security & Privacy

**We never see or store your Google password, and we cannot access your Google Account.**

When you sign in, Google gives Lumen an **OAuth token**. Think of an OAuth token like a temporary hotel key card:
- It **only** gives Lumen permission to drop photos into your Google Photos library.
- It **cannot** be used to read your emails, view your private Google Drive files, or change your password.
- All uploads go straight from your phone to Google (`photos.googleapis.com`). No third-party server ever touches your files or account.

---

## Features

- **Free Unlimited Backup** — Save all your camera photos and videos in full original resolution without using up your Google storage quota.
- **Direct & Private** — Files go straight from your device to Google Photos. No external servers, middleman proxies, or analytics.
- **Modern & Responsive UI** — Built with Jetpack Compose and Material 3, featuring smooth animations, shape-morphing buttons, and customizable color themes.
- **Works on Phones, Foldables & Tablets** — Designed to automatically adjust into clean side-by-side screens on larger displays and landscape orientation.
- **Live Upload Progress** — Shows clear status bar chips with real-time transfer speeds and progress while uploading.
- **Smart Duplicate Prevention** — Checks your photos locally and against Google Photos before uploading so you never waste data uploading the same file twice.
- **Automatic Background Backup** — Optionally pauses and resumes uploads automatically when connected to Wi-Fi or when your phone is plugged into a charger.
- **Troubleshooting & Tools** — Built-in diagnostics, background battery guides for Xiaomi, Samsung, and OnePlus phones, and live activity logs.
- **Hidden Easter Eggs** — Contains 3 hidden Easter Eggs throughout the app for curious users to discover.

---

## Supported Media Formats

Supports all standard photo and video formats recognized by Google Photos:

<details>
<summary>Show supported media formats</summary>

| Category | Formats |
|---|---|
| Images | JPEG, PNG, WEBP, HEIC, GIF, RAW (DNG) |
| Videos | MP4, MOV, WEBM, MKV, 3GP, AVI |

</details>

---

## Requirements

- Android 8.0 or newer
- Active Google Account
- Wi-Fi or Mobile Data connection

---

## Download

Grab the latest version from the [Releases](https://github.com/Reiux/lumen-android/releases) page:
- `lumen-release.apk` — Standard app for everyday use
- `lumen-dev-debug.apk` — Developer build with live logging tools

---

## Building from Source

For developers who want to inspect or build the project from scratch:

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

## Credits & Acknowledgments

- **Developer**: Created by **[Reiux](https://github.com/Reiux)**.
- **Core Protocol Logic**: Special thanks to **[xob0t/gotohp](https://github.com/xob0t/gotohp)** for reverse-engineering the Google Photos upload protocol and authentication engine.

---

## Disclaimer

Lumen is an independent open-source project and is not affiliated with, endorsed by, or sponsored by Google LLC. Users use this app and services at their own discretion and risk.

---

## License

Distributed under the [Apache License 2.0](LICENSE).
