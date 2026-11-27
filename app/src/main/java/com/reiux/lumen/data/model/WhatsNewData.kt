package com.reiux.lumen.data.model

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.InsertDriveFile
import androidx.compose.material.icons.rounded.*
import androidx.compose.ui.graphics.vector.ImageVector

enum class WhatsNewAccent {
    PRIMARY,
    SECONDARY,
    TERTIARY,
    ERROR_SOFT
}

data class WhatsNewItem(
    val id: String,
    val title: String,
    val summary: String,
    val detail: String,
    val icon: ImageVector,
    val accent: WhatsNewAccent
)

data class WhatsNewRelease(
    val version: String,
    val headerLabel: String,
    val items: List<WhatsNewItem>
)

object WhatsNewCatalog {
    val releases: List<WhatsNewRelease> = listOf(
        WhatsNewRelease(
            version = "1.3.4",
            headerLabel = "v1.3.4",
            items = listOf(
                WhatsNewItem(
                    id = "v134_m3e",
                    title = "More Material 3 Expressive Polish",
                    summary = "Upgraded to the official Material 3 Expressive library and updated the remaining screens and controls.",
                    detail = "Upgraded to the official Jetpack Compose Material 3 Expressive APIs and finished bringing the remaining screens, segmented lists, dialogs, switches, and loading indicators up to the same expressive standard as the rest of the app.",
                    icon = Icons.Rounded.AutoAwesome,
                    accent = WhatsNewAccent.PRIMARY
                ),
                WhatsNewItem(
                    id = "v134_pause_resume",
                    title = "Pause, Resume & Per-Item Retry",
                    summary = "Pause uploads mid-file in 512 KiB chunks, control from notifications, or retry failed items.",
                    detail = "Uploads now run in 512 KiB resumable chunks so pausing mid-file keeps your progress intact. You can also pause or resume straight from the backup notification and tap any failed queue item to retry it on its own.",
                    icon = Icons.Rounded.PauseCircle,
                    accent = WhatsNewAccent.SECONDARY
                ),
                WhatsNewItem(
                    id = "v134_per_folder_delete",
                    title = "Per-Folder Auto-Delete",
                    summary = "Enable \"Delete after backup\" for individual folders instead of all folders at once.",
                    detail = "You can now turn on \"Delete after backup\" for specific folders like Screenshots or Downloads while leaving Camera Roll untouched, instead of applying it globally.",
                    icon = Icons.Rounded.DeleteOutline,
                    accent = WhatsNewAccent.ERROR_SOFT
                ),
                WhatsNewItem(
                    id = "v134_gps_dedup",
                    title = "GPS Metadata & Dedup Fix",
                    summary = "Keeps original GPS coordinates intact and prevents already-backed-up photos from duplicating.",
                    detail = "Lumen now requests Media Location access and reads original MediaStore streams so EXIF GPS coordinates are preserved during upload and photos already in Google Photos are skipped properly.",
                    icon = Icons.Rounded.LocationOn,
                    accent = WhatsNewAccent.TERTIARY
                ),
                WhatsNewItem(
                    id = "v134_qs_shortcuts",
                    title = "Quick Settings Tile & Shortcuts",
                    summary = "Toggle backups from Android Quick Settings or long-press the app icon for shortcuts.",
                    detail = "Add the Lumen Quick Settings tile to start or pause backups from your shade, or long-press the Lumen launcher icon to jump directly into Sync Folders or Pick Media.",
                    icon = Icons.Rounded.Bolt,
                    accent = WhatsNewAccent.PRIMARY
                ),
                WhatsNewItem(
                    id = "v134_adaptive_ui",
                    title = "Tablet, Foldable & Landscape Polish",
                    summary = "Better layouts on unfolded foldables, tablets, and short landscape screens with cutout and keyboard fixes.",
                    detail = "Adjusted two-pane breakpoints so unfolded foldables have room to breathe, capped overly wide layouts on large tablets, added display cutout and keyboard insets across screens and dialogs, and preserved pending Share Sheet uploads when folding or rotating.",
                    icon = Icons.Rounded.Devices,
                    accent = WhatsNewAccent.SECONDARY
                ),
                WhatsNewItem(
                    id = "v134_updater_reliability",
                    title = "Updater, Account Picker & Background Fixes",
                    summary = "Fixed update prompts on app open, manual picker account selection, and background watcher stability.",
                    detail = "Fixed in-app auto-update checks being delayed by the background worker, fixed Pick Media and Pick Files ignoring the account chosen in the upload dialog, wired Android 16+ Live Update notifications, fixed background job rescheduling, and made seasonal snow and bats run smoothly without resetting.",
                    icon = Icons.Rounded.SystemUpdate,
                    accent = WhatsNewAccent.TERTIARY
                )
            )
        ),
        WhatsNewRelease(
            version = "1.3.3",
            headerLabel = "v1.3.3",
            items = listOf(
                WhatsNewItem(
                    id = "v133_device_folders",
                    title = "Device Folders",
                    summary = "Browse local media folders with thumbnails, counts, media badges, and one-tap backup.",
                    detail = "See photo and video folders already on your phone with preview thumbnails, item counts, Photos/Videos/Media badges, sorting options, and a quick toggle for auto-backup.",
                    icon = Icons.Rounded.FolderCopy,
                    accent = WhatsNewAccent.PRIMARY
                ),
                WhatsNewItem(
                    id = "v133_album_sync",
                    title = "Sync Existing Photos to Albums",
                    summary = "Match previously uploaded photos and sort them into each folder's assigned album.",
                    detail = "Photos that were already uploaded to Google Photos can now be matched and added to their folder's assigned album either automatically or whenever you run an album sync.",
                    icon = Icons.Rounded.PhotoAlbum,
                    accent = WhatsNewAccent.SECONDARY
                ),
                WhatsNewItem(
                    id = "v133_share_security",
                    title = "Share Previews & Log Redaction",
                    summary = "Preview shared files, guard SD/USB volume paths, and redact paths in exported logs.",
                    detail = "Added file name previews with Switch/Ignore prompts when sharing into Lumen, extra checks to avoid SD/USB volume collisions, and automatic redaction of local file paths and album names in shared diagnostic reports.",
                    icon = Icons.Rounded.Shield,
                    accent = WhatsNewAccent.TERTIARY
                )
            )
        ),
        WhatsNewRelease(
            version = "1.3.2",
            headerLabel = "v1.3.2",
            items = listOf(
                WhatsNewItem(
                    id = "v132_google_sans",
                    title = "Google Sans Flex & Brand Motion",
                    summary = "Switched to Google Sans Flex typography with interactive logo springs and seasonal headers.",
                    detail = "Updated typography across the app to Google Sans Flex, added playful spring animations to the Lumen logo, and introduced seasonal header icons.",
                    icon = Icons.Rounded.Palette,
                    accent = WhatsNewAccent.PRIMARY
                ),
                WhatsNewItem(
                    id = "v132_translations",
                    title = "Indonesian & Weblate Translations",
                    summary = "Added Bahasa Indonesia, Android 13+ per-app language picker, and Weblate support.",
                    detail = "Lumen now supports Bahasa Indonesia alongside Android 13+ per-app language settings, with ongoing community translations hosted on Weblate.",
                    icon = Icons.Rounded.Translate,
                    accent = WhatsNewAccent.SECONDARY
                ),
                WhatsNewItem(
                    id = "v132_reliability",
                    title = "Captive Portal Wi-Fi & Large Batches",
                    summary = "Better Wi-Fi validation, smoother 1,000+ file progress updates, and active account safety.",
                    detail = "Wi-Fi only mode now checks for validated internet access to handle captive portals cleanly, large batches of 1,000+ files update progress more smoothly, and active accounts are protected while uploads are running.",
                    icon = Icons.Rounded.Wifi,
                    accent = WhatsNewAccent.TERTIARY
                )
            )
        ),
        WhatsNewRelease(
            version = "1.3.1",
            headerLabel = "v1.3.1",
            items = listOf(
                WhatsNewItem(
                    id = "v131_hotfix",
                    title = "Background & Service Hotfix",
                    summary = "Fixed a foreground service race crash on fast cache-hit backups and added an Android 12+ expedited fallback.",
                    detail = "Fixed a foreground service race crash when backups finish quickly from cache, added an expedited WorkManager fallback for Android 12+ background runs, and improved live network detection and updater reliability.",
                    icon = Icons.Rounded.Build,
                    accent = WhatsNewAccent.SECONDARY
                )
            )
        ),
        WhatsNewRelease(
            version = "1.3.0",
            headerLabel = "v1.3.0",
            items = listOf(
                WhatsNewItem(
                    id = "v130_share_sheet",
                    title = "System Share Sheet",
                    summary = "Send photos and videos straight to Lumen from your gallery or file manager via Android's share menu.",
                    detail = "Added \"Upload to Photos\" to Android's system share sheet so you can send photos and videos directly from your gallery or file manager without switching apps first.",
                    icon = Icons.Rounded.Share,
                    accent = WhatsNewAccent.PRIMARY
                ),
                WhatsNewItem(
                    id = "v130_predictive_back",
                    title = "Predictive Back & Motion",
                    summary = "Full Android 14/15 predictive back gestures across overlays and dialogs with spring transitions.",
                    detail = "Added full Android 14 and 15 predictive back gesture support across overlays and dialogs, paired with smooth spring-driven transitions.",
                    icon = Icons.Rounded.SwipeRight,
                    accent = WhatsNewAccent.TERTIARY
                )
            )
        ),
        WhatsNewRelease(
            version = "1.2.1",
            headerLabel = "v1.2.1",
            items = listOf(
                WhatsNewItem(
                    id = "v121_album_fixes",
                    title = "Album Creation & Dedup Fixes",
                    summary = "Fixed first-time album creation flags and ensured deduplicated files still land in their assigned album.",
                    detail = "Fixed an issue where newly created albums were flagged as missing on first run, ensured deduplicated files still get added to their assigned album, and stopped silent timeline fallbacks when album creation fails.",
                    icon = Icons.Rounded.PhotoAlbum,
                    accent = WhatsNewAccent.SECONDARY
                )
            )
        ),
        WhatsNewRelease(
            version = "1.2.0",
            headerLabel = "v1.2.0",
            items = listOf(
                WhatsNewItem(
                    id = "v120_eta_filters",
                    title = "Live ETA & Activity Filters",
                    summary = "Live upload speed and time-remaining estimates on Backup, plus animated filters on Recent Activity.",
                    detail = "Added live upload speed and time-remaining estimates directly on the Backup card, along with animated status filter chips (All, Uploading, Failed, Done) in Recent Activity.",
                    icon = Icons.Rounded.Speed,
                    accent = WhatsNewAccent.PRIMARY
                ),
                WhatsNewItem(
                    id = "v120_swipe_accounts",
                    title = "Swipe to Switch Accounts & Countdown",
                    summary = "Swipe the account card to switch Google accounts, plus live rate-limit countdowns and full-width pickers.",
                    detail = "You can now swipe the account card on Backup to jump between linked Google accounts with spring haptics, alongside live rate-limit countdowns and full-width pickers.",
                    icon = Icons.Rounded.SwapHoriz,
                    accent = WhatsNewAccent.SECONDARY
                )
            )
        ),
        WhatsNewRelease(
            version = "1.1.4",
            headerLabel = "v1.1.4",
            items = listOf(
                WhatsNewItem(
                    id = "v114_pick_files",
                    title = "Choose from Files & Startup Updates",
                    summary = "Added the Storage Access Framework file picker so original filenames stay intact, plus better launch update checks.",
                    detail = "Added the Storage Access Framework file picker so picked media keeps its original filename instead of generic MediaStore IDs, along with improved on-launch update checks.",
                    icon = Icons.AutoMirrored.Rounded.InsertDriveFile,
                    accent = WhatsNewAccent.TERTIARY
                )
            )
        ),
        WhatsNewRelease(
            version = "1.1.3",
            headerLabel = "v1.1.3",
            items = listOf(
                WhatsNewItem(
                    id = "v113_fixes",
                    title = "Settings & Update Throttling Fixes",
                    summary = "Cleaned up Settings rendering, hardened the release build workflow, and throttled background update checks.",
                    detail = "Polished Settings UI rendering, hardened the release build workflow, and added throttling so update checks don't fire unnecessarily.",
                    icon = Icons.Rounded.Tune,
                    accent = WhatsNewAccent.SECONDARY
                )
            )
        ),
        WhatsNewRelease(
            version = "1.1.2",
            headerLabel = "v1.1.2",
            items = listOf(
                WhatsNewItem(
                    id = "v112_security_tablet",
                    title = "Encrypted Storage & Tablet Onboarding",
                    summary = "Added Android KeyStore account encryption, commit recovery safety, auto-update toggle, and 2-pane onboarding.",
                    detail = "Added Android KeyStore encrypted storage for linked accounts, commit recovery with circuit-breaker safety, an auto-update toggle, and a 2-pane onboarding layout for landscape and tablets.",
                    icon = Icons.Rounded.Lock,
                    accent = WhatsNewAccent.PRIMARY
                )
            )
        ),
        WhatsNewRelease(
            version = "1.1.1",
            headerLabel = "v1.1.1",
            items = listOf(
                WhatsNewItem(
                    id = "v111_release_signing",
                    title = "Dedicated Release Signing",
                    summary = "Switched official builds to a permanent release signing key so future updates install cleanly.",
                    detail = "Switched official builds over to a dedicated release signing key so future app updates install smoothly without package conflicts.",
                    icon = Icons.Rounded.VerifiedUser,
                    accent = WhatsNewAccent.TERTIARY
                )
            )
        ),
        WhatsNewRelease(
            version = "1.1.0",
            headerLabel = "v1.1.0",
            items = listOf(
                WhatsNewItem(
                    id = "v110_updater",
                    title = "In-App Updates",
                    summary = "Automatic background checks for GitHub releases with notifications and an in-app changelog installer sheet.",
                    detail = "Added automatic background checks for new GitHub releases with update notifications and an in-app changelog sheet that downloads and installs updates directly.",
                    icon = Icons.Rounded.SystemUpdate,
                    accent = WhatsNewAccent.PRIMARY
                ),
                WhatsNewItem(
                    id = "v110_fast_indexing",
                    title = "Faster Folder Indexing & Subfolders",
                    summary = "Rewrote folder scanning with MediaStore fast paths, live file counts, and a Cancel Scan option.",
                    detail = "Rewrote folder scanning using MediaStore fast paths with live file counts as indexing progresses and a Cancel Scan action to keep large folders responsive.",
                    icon = Icons.Rounded.Bolt,
                    accent = WhatsNewAccent.SECONDARY
                )
            )
        ),
        WhatsNewRelease(
            version = "1.0.2",
            headerLabel = "v1.0.2",
            items = listOf(
                WhatsNewItem(
                    id = "v102_logs_presets",
                    title = "Redesigned Logs, Presets & Seasons",
                    summary = "3-tab diagnostic log viewer with plain-language error notes, social folder presets, and seasonal themes.",
                    detail = "Introduced the 3-tab diagnostic log viewer with plain-language error explanations, quick presets for WhatsApp, Instagram, Telegram, TikTok, and Reddit folders, and seasonal themes.",
                    icon = Icons.Rounded.BugReport,
                    accent = WhatsNewAccent.TERTIARY
                )
            )
        ),
        WhatsNewRelease(
            version = "1.0.1",
            headerLabel = "v1.0.1",
            items = listOf(
                WhatsNewItem(
                    id = "v101_stability",
                    title = "Stability & Scanner Fixes",
                    summary = "Fixed Android 14+ service crashes, Sync Folders tap concurrency issues, trash exclusions, and notification alignment.",
                    detail = "Fixed Android 14+ foreground service crashes, rapid-tap concurrency crashes on Sync Folders, trash and hidden folder exclusions during scanning, and notification alignment on custom ROMs.",
                    icon = Icons.Rounded.CheckCircle,
                    accent = WhatsNewAccent.SECONDARY
                )
            )
        ),
        WhatsNewRelease(
            version = "1.0.0",
            headerLabel = "v1.0.0",
            items = listOf(
                WhatsNewItem(
                    id = "v100_initial",
                    title = "Initial Release",
                    summary = "Direct original-quality Google Photos backups with dedup, multi-account support, and Material 3 Expressive design.",
                    detail = "First official release of Lumen for Android, bringing direct original-quality Google Photos backups with local and remote deduplication, multi-account support, and a Material 3 Expressive UI.",
                    icon = Icons.Rounded.CloudUpload,
                    accent = WhatsNewAccent.PRIMARY
                )
            )
        )
    )

    val latestReleaseItemIds: Set<String>
        get() = releases.firstOrNull()?.items?.map { it.id }?.toSet().orEmpty()

    fun allItemIds(): Set<String> = releases.flatMap { it.items }.map { it.id }.toSet()

    fun unreadItemIds(readIds: Set<String>): Set<String> = latestReleaseItemIds - readIds

    fun hasUnread(readIds: Set<String>): Boolean = unreadItemIds(readIds).isNotEmpty()

    fun isItemUnread(itemId: String, readIds: Set<String>): Boolean =
        itemId in latestReleaseItemIds && itemId !in readIds

    fun normalizeBaseVersion(rawVersion: String): String =
        rawVersion.substringBefore("-").removePrefix("v").removePrefix("V").trim()

    fun shouldShowPostUpdateSheet(
        currentAppVersion: String,
        lastSeenVersion: String?,
        hasCompletedOnboarding: Boolean
    ): Boolean {
        if (!hasCompletedOnboarding) return false
        val currentBase = normalizeBaseVersion(currentAppVersion)
        if (currentBase.isBlank()) return false
        if (lastSeenVersion.isNullOrBlank()) return true
        return normalizeBaseVersion(lastSeenVersion) != currentBase
    }
}
