package com.reiux.lumen.data.repository

import android.content.Context
import android.content.SharedPreferences
import com.reiux.lumen.data.model.AccountBlock
import com.reiux.lumen.data.model.AuthFailureKind
import com.reiux.lumen.data.model.BackupFolder
import com.reiux.lumen.data.model.BackupSettings
import com.reiux.lumen.util.LumenLogger
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import org.json.JSONArray
import org.json.JSONObject

class SettingsRepository(
    context: Context,
    sharedPreferencesOverride: SharedPreferences? = null
) {
    private val prefs: SharedPreferences = sharedPreferencesOverride ?: context.getSharedPreferences("lumen_settings", Context.MODE_PRIVATE)

    private val _settings = MutableStateFlow(loadSettings())
    val settings: StateFlow<BackupSettings> = _settings.asStateFlow()

    private fun loadSettings(): BackupSettings {
        LumenLogger.d("Storage") { "Reading settings from SharedPreferences key='lumen_settings'" }
        val autoBackup = prefs.getBoolean(KEY_AUTO_BACKUP, true)
        val wifiOnly = prefs.getBoolean(KEY_WIFI_ONLY, true)
        val chargingOnly = prefs.getBoolean(KEY_CHARGING_ONLY, false)
        val storageSaver = prefs.getBoolean(KEY_STORAGE_SAVER, false)
        val useQuota = prefs.getBoolean(KEY_USE_QUOTA, false)
        val threads = prefs.getInt(KEY_UPLOAD_THREADS, 2)
        val colorPreset = prefs.getString(KEY_COLOR_PRESET, "Dynamic") ?: "Dynamic"
        val notifications = prefs.getBoolean(KEY_NOTIFICATIONS, true)
        val saveLogs = prefs.getBoolean(KEY_SAVE_LOGS, false)
        val devMode = prefs.getBoolean(KEY_DEV_MODE, false)
        val hasCompletedOnboarding = prefs.getBoolean(KEY_HAS_COMPLETED_ONBOARDING, false)
        val hasAcknowledgedRisk = prefs.getBoolean(KEY_HAS_ACKNOWLEDGED_ACCOUNT_RISK, false)
        val unlockedPrism = prefs.getBoolean(KEY_UNLOCKED_PRISM, false)
        val unlockedMatrix = prefs.getBoolean(KEY_UNLOCKED_MATRIX, false)
        val unlockedSpooky = prefs.getBoolean(KEY_UNLOCKED_SPOOKY, false)
        val unlockedXmas = prefs.getBoolean(KEY_UNLOCKED_XMAS, false)
        val unlockedNewYr = prefs.getBoolean(KEY_UNLOCKED_NEWYR, false)
        val forcedSeason = prefs.getString(KEY_FORCED_SEASON, "NONE") ?: "NONE"
        val autoCheckUpdates = prefs.getBoolean(KEY_AUTO_CHECK_UPDATES, true)

        val foldersJson = prefs.getString(KEY_FOLDERS, null)
        val folders = if (!foldersJson.isNullOrEmpty()) {
            val list = mutableListOf<BackupFolder>()
            val array = JSONArray(foldersJson)
            for (i in 0 until array.length()) {
                val obj = array.getJSONObject(i)
                list.add(
                    BackupFolder(
                        path = obj.getString("path"),
                        displayName = obj.getString("displayName"),
                        enabled = obj.optBoolean("enabled", true),
                        includeSubfolders = obj.optBoolean("includeSubfolders", false)
                    )
                )
            }
            list
        } else {
            listOf(
                BackupFolder("/sdcard/DCIM/Camera", "Camera Roll", true),
                BackupFolder("/sdcard/Pictures/Screenshots", "Screenshots", false),
                BackupFolder("/sdcard/Pictures", "Pictures", false),
                BackupFolder("/sdcard/Download", "Downloads", false)
            )
        }

        LumenLogger.d("Storage") {
            "Loaded settings: autoBackup=$autoBackup wifiOnly=$wifiOnly chargingOnly=$chargingOnly storageSaver=$storageSaver useQuota=$useQuota threads=$threads preset='$colorPreset' folders=${folders.size}"
        }

        return BackupSettings(
            autoBackupEnabled = autoBackup,
            wifiOnly = wifiOnly,
            chargingOnly = chargingOnly,
            storageSaverQuality = storageSaver,
            useQuota = useQuota,
            uploadThreads = threads,
            colorPreset = colorPreset,
            notificationsEnabled = notifications,
            saveLogsEnabled = saveLogs,
            developerModeUnlocked = devMode,
            hasCompletedOnboarding = hasCompletedOnboarding,
            hasAcknowledgedAccountRisk = hasAcknowledgedRisk,
            unlockedPrismTheme = unlockedPrism,
            unlockedMatrixTheme = unlockedMatrix,
            unlockedSpookyTheme = unlockedSpooky,
            unlockedXmasTheme = unlockedXmas,
            unlockedNewYrTheme = unlockedNewYr,
            forcedSeason = forcedSeason,
            autoCheckUpdates = autoCheckUpdates,
            folders = folders
        )
    }

    fun setAutoCheckUpdates(enabled: Boolean) {
        val old = _settings.value.autoCheckUpdates
        prefs.edit().putBoolean(KEY_AUTO_CHECK_UPDATES, enabled).apply()
        LumenLogger.d("Storage") { "SharedPreferences write key=$KEY_AUTO_CHECK_UPDATES" }
        LumenLogger.i("Settings") { "autoCheckUpdates changed: old=$old -> new=$enabled" }
        _settings.value = _settings.value.copy(autoCheckUpdates = enabled)
    }

    fun updateDeveloperMode(unlocked: Boolean) {
        val old = _settings.value.developerModeUnlocked
        prefs.edit().putBoolean(KEY_DEV_MODE, unlocked).apply()
        LumenLogger.d("Storage") { "SharedPreferences write key=$KEY_DEV_MODE" }
        LumenLogger.i("Settings") { "developerModeUnlocked changed: old=$old -> new=$unlocked" }
        _settings.value = _settings.value.copy(developerModeUnlocked = unlocked)
    }

    fun updateSaveLogs(enabled: Boolean) {
        val old = _settings.value.saveLogsEnabled
        prefs.edit().putBoolean(KEY_SAVE_LOGS, enabled).apply()
        LumenLogger.d("Storage") { "SharedPreferences write key=$KEY_SAVE_LOGS" }
        LumenLogger.i("Settings") { "saveLogsEnabled changed: old=$old -> new=$enabled" }
        _settings.value = _settings.value.copy(saveLogsEnabled = enabled)
    }

    fun updateAutoBackup(enabled: Boolean) {
        val old = _settings.value.autoBackupEnabled
        prefs.edit().putBoolean(KEY_AUTO_BACKUP, enabled).apply()
        LumenLogger.d("Storage") { "SharedPreferences write key=$KEY_AUTO_BACKUP" }
        LumenLogger.i("Settings") { "autoBackupEnabled changed: old=$old -> new=$enabled" }
        _settings.value = _settings.value.copy(autoBackupEnabled = enabled)
    }

    fun updateWifiOnly(enabled: Boolean) {
        val old = _settings.value.wifiOnly
        prefs.edit().putBoolean(KEY_WIFI_ONLY, enabled).apply()
        LumenLogger.d("Storage") { "SharedPreferences write key=$KEY_WIFI_ONLY" }
        LumenLogger.i("Settings") { "wifiOnly changed: old=$old -> new=$enabled" }
        _settings.value = _settings.value.copy(wifiOnly = enabled)
    }

    fun updateChargingOnly(enabled: Boolean) {
        val old = _settings.value.chargingOnly
        prefs.edit().putBoolean(KEY_CHARGING_ONLY, enabled).apply()
        LumenLogger.d("Storage") { "SharedPreferences write key=$KEY_CHARGING_ONLY" }
        LumenLogger.i("Settings") { "chargingOnly changed: old=$old -> new=$enabled" }
        _settings.value = _settings.value.copy(chargingOnly = enabled)
    }

    fun updateStorageSaver(enabled: Boolean) {
        val oldSaver = _settings.value.storageSaverQuality
        val oldQuota = _settings.value.useQuota
        val editor = prefs.edit().putBoolean(KEY_STORAGE_SAVER, enabled)
        var newUseQuota = oldQuota
        if (enabled && newUseQuota) {
            newUseQuota = false
            editor.putBoolean(KEY_USE_QUOTA, false)
            LumenLogger.d("Storage") { "SharedPreferences write key=$KEY_USE_QUOTA value=false (exclusive mutex with storage saver)" }
        }
        editor.apply()
        LumenLogger.d("Storage") { "SharedPreferences write key=$KEY_STORAGE_SAVER" }
        LumenLogger.i("Settings") { "storageSaver changed: old=$oldSaver -> new=$enabled, useQuota: old=$oldQuota -> new=$newUseQuota" }
        _settings.value = _settings.value.copy(
            storageSaverQuality = enabled,
            useQuota = newUseQuota
        )
    }

    fun updateUseQuota(enabled: Boolean) {
        val oldQuota = _settings.value.useQuota
        val oldSaver = _settings.value.storageSaverQuality
        val editor = prefs.edit().putBoolean(KEY_USE_QUOTA, enabled)
        var newStorageSaver = oldSaver
        if (enabled && newStorageSaver) {
            newStorageSaver = false
            editor.putBoolean(KEY_STORAGE_SAVER, false)
            LumenLogger.d("Storage") { "SharedPreferences write key=$KEY_STORAGE_SAVER value=false (exclusive mutex with use quota)" }
        }
        editor.apply()
        LumenLogger.d("Storage") { "SharedPreferences write key=$KEY_USE_QUOTA" }
        LumenLogger.i("Settings") { "useQuota changed: old=$oldQuota -> new=$enabled, storageSaver: old=$oldSaver -> new=$newStorageSaver" }
        _settings.value = _settings.value.copy(
            useQuota = enabled,
            storageSaverQuality = newStorageSaver
        )
    }

    fun updateUploadThreads(threads: Int) {
        val old = _settings.value.uploadThreads
        val safeThreads = threads.coerceIn(1, 20)
        prefs.edit().putInt(KEY_UPLOAD_THREADS, safeThreads).apply()
        LumenLogger.d("Storage") { "SharedPreferences write key=$KEY_UPLOAD_THREADS" }
        LumenLogger.i("Settings") { "uploadThreads changed: old=$old -> new=$safeThreads" }
        _settings.value = _settings.value.copy(uploadThreads = safeThreads)
    }

    fun updateColorPreset(preset: String) {
        val old = _settings.value.colorPreset
        prefs.edit().putString(KEY_COLOR_PRESET, preset).apply()
        LumenLogger.d("Storage") { "SharedPreferences write key=$KEY_COLOR_PRESET" }
        LumenLogger.i("Settings") { "colorPreset changed: old='$old' -> new='$preset'" }
        _settings.value = _settings.value.copy(colorPreset = preset)
    }

    fun updateNotifications(enabled: Boolean) {
        val old = _settings.value.notificationsEnabled
        prefs.edit().putBoolean(KEY_NOTIFICATIONS, enabled).apply()
        LumenLogger.d("Storage") { "SharedPreferences write key=$KEY_NOTIFICATIONS" }
        LumenLogger.i("Settings") { "notificationsEnabled changed: old=$old -> new=$enabled" }
        _settings.value = _settings.value.copy(notificationsEnabled = enabled)
    }

    fun updateHasCompletedOnboarding(completed: Boolean) {
        val old = _settings.value.hasCompletedOnboarding
        prefs.edit().putBoolean(KEY_HAS_COMPLETED_ONBOARDING, completed).apply()
        LumenLogger.d("Storage") { "SharedPreferences write key=$KEY_HAS_COMPLETED_ONBOARDING" }
        LumenLogger.i("Settings") { "hasCompletedOnboarding changed: old=$old -> new=$completed" }
        _settings.value = _settings.value.copy(hasCompletedOnboarding = completed)
    }

    fun updateForcedSeason(season: String) {
        val old = _settings.value.forcedSeason
        val editor = prefs.edit().putString(KEY_FORCED_SEASON, season)

        var unlockedSpooky = _settings.value.unlockedSpookyTheme
        var unlockedXmas = _settings.value.unlockedXmasTheme
        var unlockedNewYr = _settings.value.unlockedNewYrTheme

        when (season.uppercase()) {
            "HALLOWEEN" -> {
                editor.putBoolean(KEY_UNLOCKED_SPOOKY, true)
                unlockedSpooky = true
            }
            "CHRISTMAS" -> {
                editor.putBoolean(KEY_UNLOCKED_XMAS, true)
                unlockedXmas = true
            }
            "NEW_YEAR" -> {
                editor.putBoolean(KEY_UNLOCKED_NEWYR, true)
                unlockedNewYr = true
            }
        }
        editor.apply()

        LumenLogger.d("Storage") { "SharedPreferences write key=$KEY_FORCED_SEASON value=$season" }
        LumenLogger.i("Settings") { "forcedSeason changed: old='$old' -> new='$season'" }
        _settings.value = _settings.value.copy(
            forcedSeason = season,
            unlockedSpookyTheme = unlockedSpooky,
            unlockedXmasTheme = unlockedXmas,
            unlockedNewYrTheme = unlockedNewYr
        )
    }

    fun getLastSeenDarkState(): Boolean? {
        return if (prefs.contains(KEY_LAST_SEEN_DARK)) {
            prefs.getBoolean(KEY_LAST_SEEN_DARK, false)
        } else null
    }

    fun setLastSeenDarkState(isDark: Boolean) {
        prefs.edit().putBoolean(KEY_LAST_SEEN_DARK, isDark).apply()
    }

    fun getBoldChoiceDate(): String? {
        return prefs.getString(KEY_BOLD_CHOICE_DATE, null)
    }

    fun setBoldChoiceDate(dateStr: String) {
        prefs.edit().putString(KEY_BOLD_CHOICE_DATE, dateStr).apply()
    }

    fun recordRealUploads(count: Int): Pair<Long, Int?> {
        val oldTotal = prefs.getLong(KEY_LIFETIME_UPLOADS, 0L)
        val newTotal = oldTotal + count
        prefs.edit().putLong(KEY_LIFETIME_UPLOADS, newTotal).apply()

        var milestoneTriggered: Int? = null
        if (oldTotal < 100 && newTotal >= 100 && !prefs.getBoolean(KEY_MILESTONE_100_SHOWN, false)) {
            milestoneTriggered = 100
            prefs.edit().putBoolean(KEY_MILESTONE_100_SHOWN, true).apply()
        } else if (oldTotal < 1000 && newTotal >= 1000 && !prefs.getBoolean(KEY_MILESTONE_1000_SHOWN, false)) {
            milestoneTriggered = 1000
            prefs.edit().putBoolean(KEY_MILESTONE_1000_SHOWN, true).apply()
        } else if (oldTotal < 10000 && newTotal >= 10000 && !prefs.getBoolean(KEY_MILESTONE_10000_SHOWN, false)) {
            milestoneTriggered = 10000
            prefs.edit().putBoolean(KEY_MILESTONE_10000_SHOWN, true).apply()
        }

        return Pair(newTotal, milestoneTriggered)
    }

    fun getLateNightDate(): String? {
        return prefs.getString(KEY_LATE_NIGHT_DATE, null)
    }

    fun setLateNightDate(dateStr: String) {
        prefs.edit().putString(KEY_LATE_NIGHT_DATE, dateStr).apply()
    }

    fun isSeasonalGreetingShown(seasonName: String, year: Int): Boolean {
        val key = "seasonal_greeting_${seasonName}_$year"
        return prefs.getBoolean(key, false)
    }

    fun setSeasonalGreetingShown(seasonName: String, year: Int) {
        val key = "seasonal_greeting_${seasonName}_$year"
        prefs.edit().putBoolean(key, true).apply()
    }

    fun toggleFolder(path: String, enabled: Boolean) {
        val old = _settings.value.folders.firstOrNull { it.path == path }?.enabled
        val updated = _settings.value.folders.map {
            if (it.path == path) it.copy(enabled = enabled) else it
        }
        LumenLogger.i("Settings") { "toggleFolder: path='$path' old=$old -> new=$enabled" }
        saveFolders(updated)
    }

    fun addFolder(folder: BackupFolder) {
        val current = _settings.value.folders.toMutableList()
        if (current.none { it.path == folder.path }) {
            current.add(folder)
            LumenLogger.i("Settings") { "addFolder: path='${folder.path}' displayName='${folder.displayName}' enabled=${folder.enabled}" }
            saveFolders(current)
        } else {
            LumenLogger.d("Settings") { "addFolder ignored: folder '${folder.path}' already in list" }
        }
    }

    fun removeFolder(path: String) {
        val oldFolder = _settings.value.folders.firstOrNull { it.path == path }
        val updated = _settings.value.folders.filterNot { it.path == path }
        LumenLogger.i("Settings") { "removeFolder: path='$path' displayName='${oldFolder?.displayName}'" }
        saveFolders(updated)
    }

    fun setIncludeSubfolders(path: String, includeSubfolders: Boolean) {
        val old = _settings.value.folders.firstOrNull { it.path == path }?.includeSubfolders
        val updated = _settings.value.folders.map {
            if (it.path == path) it.copy(includeSubfolders = includeSubfolders) else it
        }
        LumenLogger.i("Settings") { "setIncludeSubfolders: path='$path' old=$old -> new=$includeSubfolders" }
        saveFolders(updated)
    }

    private fun saveFolders(folders: List<BackupFolder>) {
        val array = JSONArray()
        for (f in folders) {
            val obj = JSONObject()
            obj.put("path", f.path)
            obj.put("displayName", f.displayName)
            obj.put("enabled", f.enabled)
            obj.put("includeSubfolders", f.includeSubfolders)
            array.put(obj)
        }
        prefs.edit().putString(KEY_FOLDERS, array.toString()).apply()
        LumenLogger.d("Storage") { "SharedPreferences write key=$KEY_FOLDERS count=${folders.size}" }
        _settings.value = _settings.value.copy(folders = folders)
    }

    fun unlockPrismTheme() {
        prefs.edit().putBoolean(KEY_UNLOCKED_PRISM, true).apply()
        _settings.value = _settings.value.copy(unlockedPrismTheme = true)
        updateColorPreset("Prism")
    }

    fun unlockMatrixTheme() {
        prefs.edit().putBoolean(KEY_UNLOCKED_MATRIX, true).apply()
        _settings.value = _settings.value.copy(unlockedMatrixTheme = true)
        updateColorPreset("Matrix")
    }

    fun unlockSeasonalTheme(seasonName: String) {
        val key = when (seasonName.uppercase()) {
            "HALLOWEEN" -> KEY_UNLOCKED_SPOOKY
            "CHRISTMAS" -> KEY_UNLOCKED_XMAS
            "NEW_YEAR" -> KEY_UNLOCKED_NEWYR
            else -> return
        }
        prefs.edit().putBoolean(key, true).apply()
        _settings.value = _settings.value.copy(
            unlockedSpookyTheme = prefs.getBoolean(KEY_UNLOCKED_SPOOKY, false),
            unlockedXmasTheme = prefs.getBoolean(KEY_UNLOCKED_XMAS, false),
            unlockedNewYrTheme = prefs.getBoolean(KEY_UNLOCKED_NEWYR, false)
        )
    }

    fun updateHasAcknowledgedAccountRisk(acknowledged: Boolean) {
        prefs.edit().putBoolean(KEY_HAS_ACKNOWLEDGED_ACCOUNT_RISK, acknowledged).apply()
        LumenLogger.i("Settings") { "hasAcknowledgedAccountRisk changed: $acknowledged" }
        _settings.value = _settings.value.copy(hasAcknowledgedAccountRisk = acknowledged)
    }

    fun getLastUpdateCheckMillis(): Long {
        return prefs.getLong(KEY_LAST_UPDATE_CHECK_MILLIS, 0L)
    }

    fun setLastUpdateCheckMillis(millis: Long) {
        prefs.edit().putLong(KEY_LAST_UPDATE_CHECK_MILLIS, millis).apply()
    }

    fun getAccountBlock(email: String): AccountBlock? {
        val key = "account_block_${email.lowercase()}"
        val valStr = prefs.getString(key, null) ?: return null
        return try {
            val block = if (valStr.startsWith("{")) {
                val obj = JSONObject(valStr)
                val kindStr = obj.optString("kind", "NONE")
                val kind = AuthFailureKind.valueOf(kindStr)
                val ts = obj.optLong("timestamp", System.currentTimeMillis())
                val retryAfterTs = if (obj.has("retryAfterTimestamp")) obj.getLong("retryAfterTimestamp") else null
                val rawDetail = if (obj.has("rawDetail")) obj.getString("rawDetail") else null
                AccountBlock(kind, ts, email, retryAfterTs, rawDetail)
            } else {
                val parts = valStr.split("|")
                val kind = AuthFailureKind.valueOf(parts[0])
                val ts = parts[1].toLong()
                val retryAfterTs = parts.getOrNull(2)?.toLongOrNull()?.let { if (it == -1L) null else it }
                val rawDetail = parts.getOrNull(3)?.ifEmpty { null }
                AccountBlock(kind, ts, email, retryAfterTs, rawDetail)
            }

            if (isBlockExpired(block, System.currentTimeMillis())) {
                clearAccountBlock(email)
                null
            } else {
                block
            }
        } catch (_: Exception) {
            null
        }
    }

    fun cleanStaleAccountBlocks(validAccountEmails: Set<String>) {
        val validNormalized = validAccountEmails.map { it.lowercase() }.toSet()
        val allKeys = prefs.all.keys.toSet()
        for (key in allKeys) {
            if (key.startsWith("account_block_")) {
                val emailInKey = key.removePrefix("account_block_")
                if (emailInKey !in validNormalized) {
                    clearAccountBlock(emailInKey)
                    LumenLogger.i("Settings") { "Removed stale account block for $emailInKey" }
                }
            }
        }
    }

    fun setAccountBlock(block: AccountBlock) {
        val key = "account_block_${block.accountEmail.lowercase()}"
        val obj = JSONObject()
        obj.put("kind", block.kind.name)
        obj.put("timestamp", block.timestamp)
        obj.put("accountEmail", block.accountEmail)
        if (block.retryAfterTimestamp != null) obj.put("retryAfterTimestamp", block.retryAfterTimestamp)
        if (block.rawDetail != null) obj.put("rawDetail", block.rawDetail)
        prefs.edit().putString(key, obj.toString()).apply()
        LumenLogger.w("Auth") { "Persisted account block for ${block.accountEmail}: ${block.kind}" }
    }

    fun clearAccountBlock(email: String) {
        val key = "account_block_${email.lowercase()}"
        prefs.edit().remove(key).apply()
        LumenLogger.i("Auth") { "Cleared account block for $email" }
    }

    companion object {
        fun isBlockExpired(block: AccountBlock, nowMillis: Long = System.currentTimeMillis()): Boolean {
            if (block.kind == AuthFailureKind.RATE_LIMITED && block.retryAfterTimestamp != null) {
                return nowMillis >= block.retryAfterTimestamp
            }
            return false
        }

        private const val KEY_AUTO_BACKUP = "auto_backup"
        private const val KEY_WIFI_ONLY = "wifi_only"
        private const val KEY_CHARGING_ONLY = "charging_only"
        private const val KEY_STORAGE_SAVER = "storage_saver"
        private const val KEY_USE_QUOTA = "use_quota"
        private const val KEY_UPLOAD_THREADS = "upload_threads"
        private const val KEY_COLOR_PRESET = "color_preset"
        private const val KEY_NOTIFICATIONS = "notifications"
        private const val KEY_SAVE_LOGS = "save_logs"
        private const val KEY_DEV_MODE = "developer_mode_unlocked"
        private const val KEY_HAS_COMPLETED_ONBOARDING = "has_completed_onboarding"
        private const val KEY_HAS_ACKNOWLEDGED_ACCOUNT_RISK = "has_acknowledged_account_risk"
        private const val KEY_UNLOCKED_PRISM = "unlocked_prism_theme"
        private const val KEY_UNLOCKED_MATRIX = "unlocked_matrix_theme"
        private const val KEY_UNLOCKED_SPOOKY = "unlocked_spooky_theme"
        private const val KEY_UNLOCKED_XMAS = "unlocked_xmas_theme"
        private const val KEY_UNLOCKED_NEWYR = "unlocked_newyr_theme"
        private const val KEY_FORCED_SEASON = "forced_season"
        private const val KEY_AUTO_CHECK_UPDATES = "auto_check_updates"
        private const val KEY_LAST_SEEN_DARK = "last_seen_dark"
        private const val KEY_BOLD_CHOICE_DATE = "last_bold_choice_date"
        private const val KEY_LIFETIME_UPLOADS = "lifetime_uploads"
        private const val KEY_MILESTONE_100_SHOWN = "milestone_100_shown"
        private const val KEY_MILESTONE_1000_SHOWN = "milestone_1000_shown"
        private const val KEY_MILESTONE_10000_SHOWN = "milestone_10000_shown"
        private const val KEY_LATE_NIGHT_DATE = "last_late_night_date"
        private const val KEY_LAST_UPDATE_CHECK_MILLIS = "last_update_check_millis"
        private const val KEY_FOLDERS = "folders"
    }
}
