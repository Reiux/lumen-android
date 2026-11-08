# Lumen 1.3.0

We crossed 1,000 downloads with this release. Huge thanks to everyone using the app, reporting bugs, and helping test builds.

Special shoutouts:
- @ra-rear for suggesting the system share sheet integration.
- @woallin for bug reports and helpful enhancement suggestions.
- @omersusin for featuring Lumen on his Telegram channel (popMODS).

---

### What's New
- System Share Sheet: You can now send photos and videos straight to Lumen from your gallery or file manager via Android's share menu ("Upload to Photos").
- Predictive Back & M3E Motion: Implemented full Android 14/15 predictive back gesture navigation across all screens and dialogs with Material 3 Expressive spring physics.
- Background Window Blur: Added hardware Gaussian blur behind dialogs and bottom sheets on supported Android 12+ (API 31+) devices.
- Release Notes Markdown: In-app update changelogs now support inline `code` spans and horizontal dividers.

---

### Bug Fixes & Improvements
- Remote Dedup Throttling: Enforced a 60ms sequential pacing mutex on remote hash checks to prevent burst QPS spikes and 429 rate-limit errors against Google's servers.
- Rate Limit Lockout: Reduced the default 429 backoff penalty from 15 minutes down to 30 seconds when Google does not provide a `Retry-After` header.
- Landscape Dialog Gestures: Fixed an issue on tablets and landscape phones where dialogs closed immediately on touch-down when starting a back swipe from the screen bezel.
- Dialog Sizing: Fixed dialog card layout so cards maintain proper margin constraints instead of stretching edge-to-edge on wide screens.
- Animation Tuning: Restored the original bouncy spring animation specs for dialog entrances and dismissals.
- Dev Tools Cleanup: Nuked redundant simulation toggles in Dev Tools and removed the old diagnostics bug shortcut from the backup screen.
- Target SDK: Bumped compileSdk and targetSdk to Android 17 (API 37).

---

### Security Hardening
- In-App APK Updates: Restricted update downloads strictly to official HTTPS GitHub domains (`github.com` and `objects.githubusercontent.com`) and added package archive integrity and package name matching checks before invoking the system installer.
- Network Security Configuration: Linked a strict network security config that disallows cleartext HTTP traffic globally and limits user CA trust strictly to debug builds.
- Master Token Storage & Backup Isolation: Set `android:allowBackup="false"` and explicitly excluded AndroidX crypto keysets, encrypted token preferences, and database files from cloud backups and device transfers.
- Scoped FileProvider Paths: Removed broad root path wildcards in `file_paths.xml` and restricted access strictly to dedicated subdirectories (`updates/`, `exports/`, `avatars/`).
- Log Export Scrubbing: Stripped raw Google auth HTTP response bodies from error traces and expanded regex redaction to scrub session tokens (`SID`, `LSID`, `oauth_token`, `access_token`, `refresh_token`), auth payloads, and email addresses.
- Release Code Minification: Enabled R8 shrinking and ProGuard optimization on release builds with keep rules for Protobuf models and core libraries.
- BroadcastReceiver Protection: Added `RECEIVE_BOOT_COMPLETED` permission gating to `BootReceiver` and implemented an explicit whitelist check on incoming intent actions.
- WebView Hardening: Disabled file scheme access, content access, and geolocation in `GoogleSignInWebViewDialog`, and enforced strict exact and subdomain matching (`*.google.com`, `*.googleusercontent.com`) to prevent domain spoofing.
