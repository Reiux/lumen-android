package com.reiux.lumen.data.repository

import android.content.Context
import android.content.SharedPreferences
import com.reiux.lumen.data.model.AccountBlock
import com.reiux.lumen.data.model.AlbumTargetMode
import com.reiux.lumen.data.model.AuthFailureKind
import com.reiux.lumen.data.model.BackupFolder
import com.reiux.lumen.data.model.BackupSettings
import com.reiux.lumen.util.LumenLogger
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import org.json.JSONArray
import org.json.JSONObject
import java.security.MessageDigest
import java.security.SecureRandom
import java.util.Base64

class SettingsRepository(
    context: Context,
    sharedPreferencesOverride: SharedPreferences? = null
) {
    private val prefs: SharedPreferences = sharedPreferencesOverride ?: context.getSharedPreferences("lumen_settings", Context.MODE_PRIVATE)
    private val prefsLock = Any()

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
        val deleteAfterBackup = prefs.getBoolean(KEY_DELETE_AFTER_BACKUP, false)
        val enableBlurEffects = prefs.getBoolean(KEY_ENABLE_BLUR_EFFECTS, true)
        val autoSyncAlbums = prefs.getBoolean(KEY_AUTO_SYNC_ALBUMS, true)
        val hasShownAccountSwipeNudge = prefs.getBoolean(KEY_HAS_SHOWN_ACCOUNT_SWIPE_NUDGE, false)

        val folders = getAllFolders()

        LumenLogger.d("Storage") {
            "Loaded settings: autoBackup=$autoBackup wifiOnly=$wifiOnly chargingOnly=$chargingOnly storageSaver=$storageSaver useQuota=$useQuota threads=$threads preset='$colorPreset' folders=${folders.size} deleteAfterBackup=$deleteAfterBackup enableBlurEffects=$enableBlurEffects autoSyncAlbums=$autoSyncAlbums"
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
            deleteAfterBackup = deleteAfterBackup,
            enableBlurEffects = enableBlurEffects,
            autoSyncAlbums = autoSyncAlbums,
            hasShownAccountSwipeNudge = hasShownAccountSwipeNudge,
            folders = folders
        )
    }

    fun setAutoCheckUpdates(enabled: Boolean) = synchronized(prefsLock) {
        val old = _settings.value.autoCheckUpdates
        prefs.edit().putBoolean(KEY_AUTO_CHECK_UPDATES, enabled).apply()
        LumenLogger.d("Storage") { "SharedPreferences write key=$KEY_AUTO_CHECK_UPDATES" }
        LumenLogger.i("Settings") { "autoCheckUpdates changed: old=$old -> new=$enabled" }
        _settings.update { it.copy(autoCheckUpdates = enabled) }
    }

    fun updateDeleteAfterBackup(enabled: Boolean) = synchronized(prefsLock) {
        val old = _settings.value.deleteAfterBackup
        prefs.edit().putBoolean(KEY_DELETE_AFTER_BACKUP, enabled).apply()
        LumenLogger.d("Storage") { "SharedPreferences write key=$KEY_DELETE_AFTER_BACKUP" }
        LumenLogger.i("Settings") { "deleteAfterBackup changed: old=$old -> new=$enabled" }
        _settings.update { it.copy(deleteAfterBackup = enabled) }
    }

    fun updateEnableBlurEffects(enabled: Boolean) = synchronized(prefsLock) {
        val old = _settings.value.enableBlurEffects
        prefs.edit().putBoolean(KEY_ENABLE_BLUR_EFFECTS, enabled).apply()
        LumenLogger.d("Storage") { "SharedPreferences write key=$KEY_ENABLE_BLUR_EFFECTS" }
        LumenLogger.i("Settings") { "enableBlurEffects changed: old=$old -> new=$enabled" }
        _settings.update { it.copy(enableBlurEffects = enabled) }
    }

    fun updateAutoSyncAlbums(enabled: Boolean) = synchronized(prefsLock) {
        val old = _settings.value.autoSyncAlbums
        prefs.edit().putBoolean(KEY_AUTO_SYNC_ALBUMS, enabled).apply()
        LumenLogger.d("Storage") { "SharedPreferences write key=$KEY_AUTO_SYNC_ALBUMS" }
        LumenLogger.i("Settings") { "autoSyncAlbums changed: old=$old -> new=$enabled" }
        _settings.update { it.copy(autoSyncAlbums = enabled) }
    }

    fun markAccountSwipeNudgeShown() = synchronized(prefsLock) {
        if (_settings.value.hasShownAccountSwipeNudge) return
        prefs.edit().putBoolean(KEY_HAS_SHOWN_ACCOUNT_SWIPE_NUDGE, true).apply()
        _settings.update { it.copy(hasShownAccountSwipeNudge = true) }
    }

    fun updateDeveloperMode(unlocked: Boolean) = synchronized(prefsLock) {
        val old = _settings.value.developerModeUnlocked
        prefs.edit().putBoolean(KEY_DEV_MODE, unlocked).apply()
        LumenLogger.d("Storage") { "SharedPreferences write key=$KEY_DEV_MODE" }
        LumenLogger.i("Settings") { "developerModeUnlocked changed: old=$old -> new=$unlocked" }
        _settings.update { it.copy(developerModeUnlocked = unlocked) }
    }

    fun updateSaveLogs(enabled: Boolean) = synchronized(prefsLock) {
        val old = _settings.value.saveLogsEnabled
        prefs.edit().putBoolean(KEY_SAVE_LOGS, enabled).apply()
        LumenLogger.d("Storage") { "SharedPreferences write key=$KEY_SAVE_LOGS" }
        LumenLogger.i("Settings") { "saveLogsEnabled changed: old=$old -> new=$enabled" }
        _settings.update { it.copy(saveLogsEnabled = enabled) }
    }

    fun updateAutoBackup(enabled: Boolean) = synchronized(prefsLock) {
        val old = _settings.value.autoBackupEnabled
        prefs.edit().putBoolean(KEY_AUTO_BACKUP, enabled).apply()
        LumenLogger.d("Storage") { "SharedPreferences write key=$KEY_AUTO_BACKUP" }
        LumenLogger.i("Settings") { "autoBackupEnabled changed: old=$old -> new=$enabled" }
        _settings.update { it.copy(autoBackupEnabled = enabled) }
    }

    fun updateWifiOnly(enabled: Boolean) = synchronized(prefsLock) {
        val old = _settings.value.wifiOnly
        prefs.edit().putBoolean(KEY_WIFI_ONLY, enabled).apply()
        LumenLogger.d("Storage") { "SharedPreferences write key=$KEY_WIFI_ONLY" }
        LumenLogger.i("Settings") { "wifiOnly changed: old=$old -> new=$enabled" }
        _settings.update { it.copy(wifiOnly = enabled) }
    }

    fun updateChargingOnly(enabled: Boolean) = synchronized(prefsLock) {
        val old = _settings.value.chargingOnly
        prefs.edit().putBoolean(KEY_CHARGING_ONLY, enabled).apply()
        LumenLogger.d("Storage") { "SharedPreferences write key=$KEY_CHARGING_ONLY" }
        LumenLogger.i("Settings") { "chargingOnly changed: old=$old -> new=$enabled" }
        _settings.update { it.copy(chargingOnly = enabled) }
    }

    fun updateStorageSaver(enabled: Boolean) = synchronized(prefsLock) {
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
        _settings.update {
            it.copy(
                storageSaverQuality = enabled,
                useQuota = newUseQuota
            )
        }
    }

    fun updateUseQuota(enabled: Boolean) = synchronized(prefsLock) {
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
        _settings.update {
            it.copy(
                useQuota = enabled,
                storageSaverQuality = newStorageSaver
            )
        }
    }

    fun updateUploadThreads(threads: Int) = synchronized(prefsLock) {
        val old = _settings.value.uploadThreads
        val safeThreads = threads.coerceIn(1, 20)
        prefs.edit().putInt(KEY_UPLOAD_THREADS, safeThreads).apply()
        LumenLogger.d("Storage") { "SharedPreferences write key=$KEY_UPLOAD_THREADS" }
        LumenLogger.i("Settings") { "uploadThreads changed: old=$old -> new=$safeThreads" }
        _settings.update { it.copy(uploadThreads = safeThreads) }
    }

    fun updateColorPreset(preset: String) = synchronized(prefsLock) {
        val old = _settings.value.colorPreset
        prefs.edit().putString(KEY_COLOR_PRESET, preset).apply()
        LumenLogger.d("Storage") { "SharedPreferences write key=$KEY_COLOR_PRESET" }
        LumenLogger.i("Settings") { "colorPreset changed: old='$old' -> new='$preset'" }
        _settings.update { it.copy(colorPreset = preset) }
    }

    fun updateNotifications(enabled: Boolean) = synchronized(prefsLock) {
        val old = _settings.value.notificationsEnabled
        prefs.edit().putBoolean(KEY_NOTIFICATIONS, enabled).apply()
        LumenLogger.d("Storage") { "SharedPreferences write key=$KEY_NOTIFICATIONS" }
        LumenLogger.i("Settings") { "notificationsEnabled changed: old=$old -> new=$enabled" }
        _settings.update { it.copy(notificationsEnabled = enabled) }
    }

    fun updateHasCompletedOnboarding(completed: Boolean) = synchronized(prefsLock) {
        val old = _settings.value.hasCompletedOnboarding
        prefs.edit().putBoolean(KEY_HAS_COMPLETED_ONBOARDING, completed).apply()
        LumenLogger.d("Storage") { "SharedPreferences write key=$KEY_HAS_COMPLETED_ONBOARDING" }
        LumenLogger.i("Settings") { "hasCompletedOnboarding changed: old=$old -> new=$completed" }
        _settings.update { it.copy(hasCompletedOnboarding = completed) }
    }

    fun updateForcedSeason(season: String) = synchronized(prefsLock) {
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
        _settings.update {
            it.copy(
                forcedSeason = season,
                unlockedSpookyTheme = unlockedSpooky,
                unlockedXmasTheme = unlockedXmas,
                unlockedNewYrTheme = unlockedNewYr
            )
        }
    }

    fun getLastSeenDarkState(): Boolean? = synchronized(prefsLock) {
        return if (prefs.contains(KEY_LAST_SEEN_DARK)) {
            prefs.getBoolean(KEY_LAST_SEEN_DARK, false)
        } else null
    }

    fun setLastSeenDarkState(isDark: Boolean) = synchronized(prefsLock) {
        prefs.edit().putBoolean(KEY_LAST_SEEN_DARK, isDark).apply()
    }

    fun getBoldChoiceDate(): String? = synchronized(prefsLock) {
        return prefs.getString(KEY_BOLD_CHOICE_DATE, null)
    }

    fun setBoldChoiceDate(dateStr: String) = synchronized(prefsLock) {
        prefs.edit().putString(KEY_BOLD_CHOICE_DATE, dateStr).apply()
    }

    fun recordRealUploads(count: Int): Pair<Long, Int?> = synchronized(prefsLock) {
        val oldTotal = prefs.getLong(KEY_LIFETIME_UPLOADS, 0L)
        val newTotal = oldTotal + count
        val editor = prefs.edit().putLong(KEY_LIFETIME_UPLOADS, newTotal)

        var milestoneTriggered: Int? = null
        if (oldTotal < 100 && newTotal >= 100 && !prefs.getBoolean(KEY_MILESTONE_100_SHOWN, false)) {
            milestoneTriggered = 100
            editor.putBoolean(KEY_MILESTONE_100_SHOWN, true)
        } else if (oldTotal < 1000 && newTotal >= 1000 && !prefs.getBoolean(KEY_MILESTONE_1000_SHOWN, false)) {
            milestoneTriggered = 1000
            editor.putBoolean(KEY_MILESTONE_1000_SHOWN, true)
        } else if (oldTotal < 10000 && newTotal >= 10000 && !prefs.getBoolean(KEY_MILESTONE_10000_SHOWN, false)) {
            milestoneTriggered = 10000
            editor.putBoolean(KEY_MILESTONE_10000_SHOWN, true)
        }
        editor.apply()

        return Pair(newTotal, milestoneTriggered)
    }

    fun getLateNightDate(): String? = synchronized(prefsLock) {
        return prefs.getString(KEY_LATE_NIGHT_DATE, null)
    }

    fun setLateNightDate(dateStr: String) = synchronized(prefsLock) {
        prefs.edit().putString(KEY_LATE_NIGHT_DATE, dateStr).apply()
    }

    fun isSeasonalGreetingShown(seasonName: String, year: Int): Boolean = synchronized(prefsLock) {
        val key = "seasonal_greeting_${seasonName}_$year"
        return prefs.getBoolean(key, false)
    }

    fun setSeasonalGreetingShown(seasonName: String, year: Int) = synchronized(prefsLock) {
        val key = "seasonal_greeting_${seasonName}_$year"
        prefs.edit().putBoolean(key, true).apply()
    }

    private fun JSONObject.optNullableString(key: String): String? {
        if (!has(key) || isNull(key)) return null
        val v = optString(key)
        return if (v.isEmpty() || v == "null") null else v
    }

    fun parseFoldersJson(jsonStr: String?): List<BackupFolder>? {
        if (jsonStr.isNullOrEmpty()) return null
        return try {
            val list = mutableListOf<BackupFolder>()
            val array = JSONArray(jsonStr)
            for (i in 0 until array.length()) {
                try {
                    val obj = array.getJSONObject(i)
                    val path = obj.optNullableString("path")?.trim()
                    val displayName = obj.optNullableString("displayName")?.trim()
                    if (path.isNullOrEmpty() || displayName.isNullOrEmpty()) continue

                    val modeStr = obj.optNullableString("targetAlbumMode") ?: "NONE"
                    val mode = try { AlbumTargetMode.valueOf(modeStr) } catch (_: Exception) { AlbumTargetMode.NONE }
                    val customName = obj.optNullableString("customAlbumName")
                    val customNickname = obj.optNullableString("customAlbumNickname")
                    val accountEmail = obj.optNullableString("accountEmail")
                    val isMissingAlbum = obj.optBoolean("isMissingAlbum", false)
                    list.add(
                        BackupFolder(
                            path = path,
                            displayName = displayName,
                            enabled = obj.optBoolean("enabled", true),
                            includeSubfolders = obj.optBoolean("includeSubfolders", false),
                            targetAlbumMode = mode,
                            customAlbumName = customName,
                            customAlbumNickname = customNickname,
                            accountEmail = accountEmail,
                            isMissingAlbum = isMissingAlbum
                        )
                    )
                } catch (itemErr: Exception) {
                    LumenLogger.w("Storage") { "Skipping malformed folder entry at index $i: ${itemErr.message}" }
                }
            }
            list
        } catch (_: Exception) {
            null
        }
    }

    fun serializeFoldersJson(folders: List<BackupFolder>): String {
        val array = JSONArray()
        for (f in folders) {
            val obj = JSONObject()
            obj.put("path", f.path)
            obj.put("displayName", f.displayName)
            obj.put("enabled", f.enabled)
            obj.put("includeSubfolders", f.includeSubfolders)
            obj.put("targetAlbumMode", f.targetAlbumMode.name)
            obj.put("isMissingAlbum", f.isMissingAlbum)
            if (f.customAlbumName != null) {
                obj.put("customAlbumName", f.customAlbumName)
            }
            if (f.customAlbumNickname != null) {
                obj.put("customAlbumNickname", f.customAlbumNickname)
            }
            if (f.accountEmail != null) {
                obj.put("accountEmail", f.accountEmail)
            }
            array.put(obj)
        }
        return array.toString()
    }

    fun getAllFolders(): List<BackupFolder> = synchronized(prefsLock) {
        val json = prefs.getString(KEY_FOLDERS, null)
        val parsed = parseFoldersJson(json)
        if (parsed != null) return parsed

        return listOf(
            BackupFolder("/sdcard/DCIM/Camera", "Camera Roll", true),
            BackupFolder("/sdcard/Pictures/Screenshots", "Screenshots", false),
            BackupFolder("/sdcard/Pictures", "Pictures", false),
            BackupFolder("/sdcard/Download", "Downloads", false)
        )
    }

    fun saveFolders(folders: List<BackupFolder>) = synchronized(prefsLock) {
        val json = serializeFoldersJson(folders)
        prefs.edit().putString(KEY_FOLDERS, json).apply()
        _settings.update { it.copy(folders = folders) }
    }

    private fun matchesAccount(email1: String?, email2: String?): Boolean {
        if (email1.isNullOrBlank() && email2.isNullOrBlank()) return true
        if (email1.isNullOrBlank() || email2.isNullOrBlank()) return false
        return email1.equals(email2, ignoreCase = true)
    }

    private fun matchesFolderPath(path1: String, path2: String): Boolean {
        return Companion.matchesFolderPath(path1, path2)
    }

    fun toggleFolder(path: String, enabled: Boolean) = toggleFolder(path, null, enabled)
    fun toggleFolder(path: String, accountEmail: String?, enabled: Boolean) = synchronized(prefsLock) {
        val current = getAllFolders()
        val updated = current.map {
            val matches = if (accountEmail.isNullOrBlank()) {
                matchesFolderPath(it.path, path)
            } else {
                matchesFolderPath(it.path, path) && matchesAccount(it.accountEmail, accountEmail)
            }
            if (matches) it.copy(enabled = enabled) else it
        }
        saveFolders(updated)
    }

    @JvmOverloads
    fun addFolder(folder: BackupFolder, previousAccountEmail: String? = null) = synchronized(prefsLock) {
        val current = getAllFolders().toMutableList()
        if (previousAccountEmail != null && !matchesAccount(previousAccountEmail, folder.accountEmail)) {
            current.removeAll {
                matchesFolderPath(it.path, folder.path) && matchesAccount(it.accountEmail, previousAccountEmail)
            }
        }
        val index = current.indexOfFirst {
            matchesFolderPath(it.path, folder.path) && matchesAccount(it.accountEmail, folder.accountEmail)
        }
        if (index >= 0) {
            current[index] = folder
        } else {
            current.add(folder)
        }
        saveFolders(current)
    }

    fun removeFolder(path: String) = removeFolder(path, null)
    fun removeFolder(path: String, accountEmail: String?) = synchronized(prefsLock) {
        val current = getAllFolders()
        val updated = current.filterNot {
            matchesFolderPath(it.path, path) && matchesAccount(it.accountEmail, accountEmail)
        }
        saveFolders(updated)
    }

    fun setIncludeSubfolders(path: String, includeSubfolders: Boolean) = setIncludeSubfolders(path, null, includeSubfolders)
    fun setIncludeSubfolders(path: String, accountEmail: String?, includeSubfolders: Boolean) = synchronized(prefsLock) {
        val current = getAllFolders()
        val updated = current.map {
            if (matchesFolderPath(it.path, path) && matchesAccount(it.accountEmail, accountEmail)) it.copy(includeSubfolders = includeSubfolders) else it
        }
        saveFolders(updated)
    }

    fun getFoldersForAccount(email: String?): List<BackupFolder> = synchronized(prefsLock) {
        val all = getAllFolders()
        if (email.isNullOrBlank()) {
            return all.filter { it.accountEmail.isNullOrBlank() }
        }
        return all.filter { it.accountEmail.isNullOrBlank() || it.accountEmail.equals(email, ignoreCase = true) }
    }

    fun saveFoldersForAccount(email: String?, folders: List<BackupFolder>) = synchronized(prefsLock) {
        val all = getAllFolders().toMutableList()
        if (!email.isNullOrBlank()) {
            all.removeAll { !it.accountEmail.isNullOrBlank() && it.accountEmail.equals(email, ignoreCase = true) }
            val formatted = folders.map { if (it.accountEmail.isNullOrBlank()) it.copy(accountEmail = email) else it }
            all.addAll(formatted)
        } else {
            all.removeAll { it.accountEmail.isNullOrBlank() }
            all.addAll(folders)
        }
        saveFolders(all)
    }

    @JvmOverloads
    fun addFolderForAccount(email: String?, folder: BackupFolder, previousAccountEmail: String? = null) = synchronized(prefsLock) {
        val f = if (!email.isNullOrBlank() && folder.accountEmail.isNullOrBlank()) folder.copy(accountEmail = email) else folder
        addFolder(f, previousAccountEmail)
    }

    fun toggleFolderForAccount(email: String?, path: String, enabled: Boolean) = synchronized(prefsLock) {
        val current = getAllFolders()
        val updated = current.map {
            if (matchesFolderPath(it.path, path) && (email.isNullOrBlank() || it.accountEmail.equals(email, ignoreCase = true))) {
                it.copy(enabled = enabled)
            } else it
        }
        saveFolders(updated)
    }

    fun removeFolderForAccount(email: String?, path: String) = synchronized(prefsLock) {
        val current = getAllFolders()
        val updated = current.filterNot {
            matchesFolderPath(it.path, path) && (email.isNullOrBlank() || it.accountEmail.equals(email, ignoreCase = true))
        }
        saveFolders(updated)
    }

    fun setIncludeSubfoldersForAccount(email: String?, path: String, includeSubfolders: Boolean) = synchronized(prefsLock) {
        val current = getAllFolders()
        val updated = current.map {
            if (matchesFolderPath(it.path, path) && (email.isNullOrBlank() || it.accountEmail.equals(email, ignoreCase = true))) {
                it.copy(includeSubfolders = includeSubfolders)
            } else it
        }
        saveFolders(updated)
    }

    @JvmOverloads
    fun updateFolderForAccount(email: String?, folder: BackupFolder, previousAccountEmail: String? = null) =
        addFolderForAccount(email, folder, previousAccountEmail)

    fun getFolderWatermarkId(email: String?, folderPath: String): Long = synchronized(prefsLock) {
        val emailKey = email?.lowercase()?.trim() ?: "default"
        val normalizedKey = "watermark_id_${emailKey}_${normalizeFolderPath(folderPath).hashCode()}"
        val savedNormalized = prefs.getLong(normalizedKey, 0L)
        if (savedNormalized > 0L) return savedNormalized
        val legacyKey = "watermark_id_${emailKey}_${folderPath.hashCode()}"
        return prefs.getLong(legacyKey, 0L)
    }

    fun saveFolderWatermarkId(email: String?, folderPath: String, watermarkId: Long) = synchronized(prefsLock) {
        val emailKey = email?.lowercase()?.trim() ?: "default"
        val normalizedKey = "watermark_id_${emailKey}_${normalizeFolderPath(folderPath).hashCode()}"
        val legacyKey = "watermark_id_${emailKey}_${folderPath.hashCode()}"
        prefs.edit()
            .putLong(normalizedKey, watermarkId)
            .putLong(legacyKey, watermarkId)
            .apply()
    }

    private val _recentAlbumsFlow = MutableStateFlow<List<com.reiux.lumen.data.network.GoogleAlbum>>(emptyList())
    val recentAlbumsFlow: StateFlow<List<com.reiux.lumen.data.network.GoogleAlbum>> = _recentAlbumsFlow.asStateFlow()

    private fun albumKeyPrefName(cleanEmail: String, albumTitle: String): String {
        return "album_key_${cleanEmail}_${albumTitle.lowercase().trim().replace(Regex("\\s+"), "_")}"
    }

    private fun clearAlbumKeysMatching(cleanEmail: String, titlesOrKeys: Set<String>, editor: SharedPreferences.Editor) {
        val prefix = "album_key_${cleanEmail}_"
        val targetsLower = titlesOrKeys.filter { it.isNotBlank() }.map { it.trim() }
        for (target in targetsLower) {
            editor.remove(albumKeyPrefName(cleanEmail, target))
        }
        for ((prefKey, prefVal) in prefs.all) {
            if (prefKey.startsWith(prefix)) {
                val strVal = prefVal as? String
                if (strVal != null && targetsLower.any { it == strVal || it.equals(strVal, ignoreCase = true) }) {
                    editor.remove(prefKey)
                }
            }
        }
    }

    fun getAlbumMediaKey(email: String, albumTitle: String): String? = synchronized(prefsLock) {
        val cleanEmail = email.lowercase().trim()
        val key = albumKeyPrefName(cleanEmail, albumTitle)
        val saved = prefs.getString(key, null)
        if (saved != null) {
            val keyRegex = Regex("AF1Qip[A-Za-z0-9_-]+")
            if (!saved.startsWith("AF1Qip") && !keyRegex.matches(saved)) {
                prefs.edit().remove(key).apply()
                return null
            }
        }
        return saved
    }

    @JvmOverloads
    fun saveAlbumMediaKey(
        email: String,
        albumTitle: String,
        mediaKey: String,
        previousAccountEmail: String? = null
    ) = synchronized(prefsLock) {
        if (!previousAccountEmail.isNullOrBlank() && !previousAccountEmail.equals(email, ignoreCase = true)) {
            removeRecentAlbum(previousAccountEmail, mediaKey)
            removeRecentAlbum(previousAccountEmail, albumTitle)
        }

        val cleanEmail = email.lowercase().trim()
        val key = albumKeyPrefName(cleanEmail, albumTitle)
        val keyRegex = Regex("AF1Qip[A-Za-z0-9_-]+")
        val isRealMediaKey = mediaKey.startsWith("AF1Qip") || keyRegex.matches(mediaKey)
        if (isRealMediaKey) {
            prefs.edit().putString(key, mediaKey).apply()
        } else {
            prefs.edit().remove(key).apply()
        }

        // Also save to recent albums list while preserving isMissing and coexisting title/nickname entries
        val recentsKey = "recent_albums_$cleanEmail"
        val existingJson = prefs.getString(recentsKey, "[]") ?: "[]"
        try {
            val array = JSONArray(existingJson)
            val newList = mutableListOf<com.reiux.lumen.data.network.GoogleAlbum>()
            for (i in 0 until array.length()) {
                val obj = array.getJSONObject(i)
                val t = obj.optString("title")
                val mk = obj.optString("mediaKey")
                val acc = obj.optNullableString("accountEmail")?.lowercase()?.trim() ?: cleanEmail
                val existingMissing = obj.optBoolean("isMissing", false)
                if (t.isNotEmpty() && mk.isNotEmpty() && !t.equals(albumTitle, ignoreCase = true)) {
                    val resolvedMissing = if (isRealMediaKey && mk == mediaKey) false else existingMissing
                    newList.add(
                        com.reiux.lumen.data.network.GoogleAlbum(
                            mediaKey = mk,
                            title = t,
                            accountEmail = acc,
                            isMissing = resolvedMissing
                        )
                    )
                }
            }
            newList.add(
                0,
                com.reiux.lumen.data.network.GoogleAlbum(
                    mediaKey = mediaKey,
                    title = albumTitle,
                    accountEmail = cleanEmail,
                    isMissing = false
                )
            )
            val newArray = JSONArray()
            newList.take(20).forEach { album ->
                val obj = JSONObject()
                obj.put("title", album.title)
                obj.put("mediaKey", album.mediaKey)
                obj.put("accountEmail", album.accountEmail ?: cleanEmail)
                obj.put("isMissing", album.isMissing)
                newArray.put(obj)
            }
            prefs.edit().putString(recentsKey, newArray.toString()).apply()
            _recentAlbumsFlow.value = getRecentAlbums(cleanEmail)
        } catch (e: Exception) {
            LumenLogger.w("Storage") { "Failed to update recent albums: ${e.message}" }
        }

        if (isRealMediaKey) {
            val currentFolders = getAllFolders()
            var changed = false
            val clearedFolders = currentFolders.map { f ->
                val sameAccount = matchesAccount(f.accountEmail, cleanEmail)
                val matchesAlbum = f.customAlbumName == mediaKey ||
                    f.customAlbumName.equals(albumTitle, ignoreCase = true) ||
                    f.customAlbumNickname.equals(albumTitle, ignoreCase = true) ||
                    (f.targetAlbumMode == AlbumTargetMode.AUTO_FOLDER_NAME && f.displayName.equals(albumTitle, ignoreCase = true))
                if (sameAccount && f.isMissingAlbum && matchesAlbum) {
                    changed = true
                    f.copy(isMissingAlbum = false)
                } else f
            }
            if (changed) {
                saveFolders(clearedFolders)
            }
        }
    }

    fun getRecentAlbums(email: String): List<com.reiux.lumen.data.network.GoogleAlbum> = synchronized(prefsLock) {
        val cleanEmail = email.lowercase().trim()
        val recentsKey = "recent_albums_$cleanEmail"
        val existingJson = prefs.getString(recentsKey, "[]") ?: "[]"
        val list = mutableListOf<com.reiux.lumen.data.network.GoogleAlbum>()
        try {
            val array = JSONArray(existingJson)
            for (i in 0 until array.length()) {
                val obj = array.getJSONObject(i)
                val t = obj.optString("title")
                val mk = obj.optString("mediaKey")
                val isMissing = obj.optBoolean("isMissing", false)
                val acc = obj.optNullableString("accountEmail")?.lowercase()?.trim()
                    ?: cleanEmail.ifBlank { null }
                if (t.isNotEmpty() && mk.isNotEmpty()) {
                    list.add(com.reiux.lumen.data.network.GoogleAlbum(mediaKey = mk, title = t, accountEmail = acc, isMissing = isMissing))
                }
            }
        } catch (e: Exception) {
            LumenLogger.w("Storage") { "Failed to parse recent albums: ${e.message}" }
        }
        _recentAlbumsFlow.value = list
        return list
    }

    fun markAlbumMissing(email: String, keyOrTitle: String, missing: Boolean = true) = synchronized(prefsLock) {
        val cleanEmail = email.lowercase().trim()
        val recentsKey = "recent_albums_$cleanEmail"
        val existingJson = prefs.getString(recentsKey, "[]") ?: "[]"
        val matchedIdentifiers = mutableSetOf(keyOrTitle)
        try {
            val array = JSONArray(existingJson)
            val newArray = JSONArray()
            for (i in 0 until array.length()) {
                val obj = array.getJSONObject(i)
                val t = obj.optString("title")
                val mk = obj.optString("mediaKey")
                if (t.equals(keyOrTitle, ignoreCase = true) || mk == keyOrTitle) {
                    obj.put("isMissing", missing)
                    if (t.isNotEmpty()) matchedIdentifiers.add(t)
                    if (mk.isNotEmpty()) matchedIdentifiers.add(mk)
                }
                newArray.put(obj)
            }
            val editor = prefs.edit().putString(recentsKey, newArray.toString())
            if (missing) {
                clearAlbumKeysMatching(cleanEmail, matchedIdentifiers, editor)
            }
            editor.apply()
            _recentAlbumsFlow.value = getRecentAlbums(email)
            LumenLogger.i("Storage") { "Marked album '$keyOrTitle' as missing=$missing for $email" }
        } catch (e: Exception) {
            if (missing) {
                val editor = prefs.edit()
                clearAlbumKeysMatching(cleanEmail, matchedIdentifiers, editor)
                editor.apply()
            }
            LumenLogger.w("Storage") { "Failed to mark album as missing: ${e.message}" }
        }

        val currentFolders = getAllFolders()
        var foldersChanged = false
        val updatedFolders = currentFolders.map { f ->
            val sameAccount = matchesAccount(f.accountEmail, cleanEmail)
            val matchesAlbum = matchedIdentifiers.any { id ->
                f.customAlbumName.equals(id, ignoreCase = true) ||
                    f.customAlbumNickname.equals(id, ignoreCase = true) ||
                    (f.targetAlbumMode == AlbumTargetMode.AUTO_FOLDER_NAME && f.displayName.equals(id, ignoreCase = true))
            }
            if (sameAccount && matchesAlbum && f.isMissingAlbum != missing) {
                foldersChanged = true
                f.copy(isMissingAlbum = missing)
            } else f
        }
        if (foldersChanged) {
            saveFolders(updatedFolders)
        }
    }

    fun getAllRecentAlbums(emails: Set<String>): List<com.reiux.lumen.data.network.GoogleAlbum> = synchronized(prefsLock) {
        val targetKeys = emails
            .asSequence()
            .map { it.lowercase().trim() }
            .filter { it.isNotEmpty() }
            .map { "recent_albums_$it" }
            .toSet()
        if (targetKeys.isEmpty()) return emptyList()

        val all = mutableListOf<com.reiux.lumen.data.network.GoogleAlbum>()

        for (k in targetKeys) {
            val emailFromKey = k.removePrefix("recent_albums_").trim()
            val existingJson = prefs.getString(k, "[]") ?: "[]"
            try {
                val array = JSONArray(existingJson)
                for (i in 0 until array.length()) {
                    val obj = array.getJSONObject(i)
                    val t = obj.optString("title")
                    val mk = obj.optString("mediaKey")
                    val acc = obj.optNullableString("accountEmail")?.lowercase()?.trim()
                        ?: emailFromKey.ifBlank { null }
                    val isMissing = obj.optBoolean("isMissing", false)
                    if (t.isNotEmpty() && mk.isNotEmpty()) {
                        all.add(com.reiux.lumen.data.network.GoogleAlbum(mediaKey = mk, title = t, accountEmail = acc, isMissing = isMissing))
                    }
                }
            } catch (e: Exception) {
                LumenLogger.w("Storage") { "Failed to parse recent albums for key $k: ${e.message}" }
            }
        }
        return all.distinctBy { "${it.accountEmail}:${it.mediaKey}:${it.title.lowercase()}" }
    }

    fun removeRecentAlbum(email: String, keyOrTitle: String) = synchronized(prefsLock) {
        val cleanEmail = email.lowercase().trim()
        val recentsKey = "recent_albums_$cleanEmail"
        val existingJson = prefs.getString(recentsKey, "[]") ?: "[]"
        val removedIdentifiers = mutableSetOf(keyOrTitle)
        try {
            val array = JSONArray(existingJson)
            val newArray = JSONArray()
            for (i in 0 until array.length()) {
                val obj = array.getJSONObject(i)
                val t = obj.optString("title")
                val mk = obj.optString("mediaKey")
                if (!t.equals(keyOrTitle, ignoreCase = true) && mk != keyOrTitle) {
                    newArray.put(obj)
                } else {
                    if (t.isNotEmpty()) removedIdentifiers.add(t)
                    if (mk.isNotEmpty()) removedIdentifiers.add(mk)
                }
            }
            val editor = prefs.edit().putString(recentsKey, newArray.toString())
            clearAlbumKeysMatching(cleanEmail, removedIdentifiers, editor)
            editor.apply()
            _recentAlbumsFlow.value = getRecentAlbums(email)
            LumenLogger.i("Storage") { "Removed album '$keyOrTitle' from recent albums for $email" }
        } catch (e: Exception) {
            val editor = prefs.edit()
            clearAlbumKeysMatching(cleanEmail, removedIdentifiers, editor)
            editor.apply()
            LumenLogger.w("Storage") { "Failed to remove album from recents: ${e.message}" }
        }
    }

    fun unlockPrismTheme() = synchronized(prefsLock) {
        prefs.edit().putBoolean(KEY_UNLOCKED_PRISM, true).apply()
        _settings.update { it.copy(unlockedPrismTheme = true) }
        updateColorPreset("Prism")
    }

    fun unlockMatrixTheme() = synchronized(prefsLock) {
        prefs.edit().putBoolean(KEY_UNLOCKED_MATRIX, true).apply()
        _settings.update { it.copy(unlockedMatrixTheme = true) }
        updateColorPreset("Matrix")
    }

    fun unlockSeasonalTheme(seasonName: String) = synchronized(prefsLock) {
        val key = when (seasonName.uppercase()) {
            "HALLOWEEN" -> KEY_UNLOCKED_SPOOKY
            "CHRISTMAS" -> KEY_UNLOCKED_XMAS
            "NEW_YEAR" -> KEY_UNLOCKED_NEWYR
            else -> return
        }
        prefs.edit().putBoolean(key, true).apply()
        _settings.update {
            it.copy(
                unlockedSpookyTheme = prefs.getBoolean(KEY_UNLOCKED_SPOOKY, false),
                unlockedXmasTheme = prefs.getBoolean(KEY_UNLOCKED_XMAS, false),
                unlockedNewYrTheme = prefs.getBoolean(KEY_UNLOCKED_NEWYR, false)
            )
        }
    }

    fun updateHasAcknowledgedAccountRisk(acknowledged: Boolean) = synchronized(prefsLock) {
        prefs.edit().putBoolean(KEY_HAS_ACKNOWLEDGED_ACCOUNT_RISK, acknowledged).apply()
        LumenLogger.i("Settings") { "hasAcknowledgedAccountRisk changed: $acknowledged" }
        _settings.update { it.copy(hasAcknowledgedAccountRisk = acknowledged) }
    }

    fun getLastUpdateCheckMillis(): Long = synchronized(prefsLock) {
        return prefs.getLong(KEY_LAST_UPDATE_CHECK_MILLIS, 0L)
    }

    fun setLastUpdateCheckMillis(millis: Long) = synchronized(prefsLock) {
        prefs.edit().putLong(KEY_LAST_UPDATE_CHECK_MILLIS, millis).apply()
    }

    /** Version string of the last release we posted an update notification for. */
    fun getLastNotifiedUpdateVersion(): String? = synchronized(prefsLock) {
        return prefs.getString(KEY_LAST_NOTIFIED_UPDATE_VERSION, null)
    }

    fun setLastNotifiedUpdateVersion(version: String) = synchronized(prefsLock) {
        prefs.edit().putString(KEY_LAST_NOTIFIED_UPDATE_VERSION, version).apply()
    }

    fun getAccountBlock(email: String): AccountBlock? = synchronized(prefsLock) {
        val key = "account_block_${email.lowercase()}"
        val valStr = prefs.getString(key, null) ?: return null
        return try {
            val block = if (valStr.startsWith("{")) {
                val obj = JSONObject(valStr)
                val kindStr = obj.optString("kind", "NONE")
                val kind = AuthFailureKind.valueOf(kindStr)
                val ts = obj.optLong("timestamp", System.currentTimeMillis())
                val retryAfterTs = if (obj.has("retryAfterTimestamp") && !obj.isNull("retryAfterTimestamp")) {
                    obj.getLong("retryAfterTimestamp")
                } else null
                val rawDetail = obj.optNullableString("rawDetail")
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

    fun cleanStaleAccountBlocks(validAccountEmails: Set<String>) = synchronized(prefsLock) {
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

    fun setAccountBlock(block: AccountBlock) = synchronized(prefsLock) {
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

    fun clearAccountBlock(email: String) = synchronized(prefsLock) {
        val key = "account_block_${email.lowercase()}"
        prefs.edit().remove(key).apply()
        LumenLogger.i("Auth") { "Cleared account block for $email" }
    }

    fun removeAccountData(email: String) = synchronized(prefsLock) {
        val cleanEmail = email.lowercase().trim()
        if (cleanEmail.isEmpty()) return

        val currentFolders = getAllFolders()
        val remainingFolders = currentFolders.filterNot {
            it.accountEmail?.trim()?.equals(cleanEmail, ignoreCase = true) == true
        }
        if (remainingFolders.size != currentFolders.size) {
            saveFolders(remainingFolders)
        }

        val albumPrefix = "album_key_${cleanEmail}_"
        val watermarkPrefix = "watermark_id_${cleanEmail}_"
        val recentsKey = "recent_albums_$cleanEmail"
        val blockKey = "account_block_$cleanEmail"
        val rawLowerBlockKey = "account_block_${email.lowercase()}"

        val editor = prefs.edit()
        for (key in prefs.all.keys.toList()) {
            if (key.startsWith(albumPrefix) ||
                key.startsWith(watermarkPrefix) ||
                key == recentsKey ||
                key == blockKey ||
                key == rawLowerBlockKey
            ) {
                editor.remove(key)
            }
        }
        editor.remove(recentsKey)
        editor.remove(blockKey)
        editor.apply()

        _recentAlbumsFlow.value = emptyList()
        LumenLogger.i("Settings") { "Purged persisted settings data for removed account ${LumenLogger.redact(cleanEmail)}" }
    }

    fun getOrCreateInternalLaunchToken(): String = synchronized(prefsLock) {
        val existing = prefs.getString(KEY_INTERNAL_LAUNCH_TOKEN, null)
        if (!existing.isNullOrBlank() && existing.length >= 32) {
            return existing
        }
        val bytes = ByteArray(24)
        SecureRandom().nextBytes(bytes)
        val created = Base64.getUrlEncoder().withoutPadding().encodeToString(bytes)
        prefs.edit().putString(KEY_INTERNAL_LAUNCH_TOKEN, created).apply()
        return created
    }

    fun isValidInternalLaunchToken(candidate: String?): Boolean = synchronized(prefsLock) {
        if (candidate.isNullOrBlank()) return false
        val expected = prefs.getString(KEY_INTERNAL_LAUNCH_TOKEN, null)
        if (expected.isNullOrBlank()) return false
        return MessageDigest.isEqual(
            expected.toByteArray(Charsets.UTF_8),
            candidate.toByteArray(Charsets.UTF_8)
        )
    }

    companion object {
        fun normalizeFolderPath(path: String): String {
            return path.trim()
                .replace('\\', '/')
                .removePrefix("/storage/emulated/0/")
                .removePrefix("/sdcard/")
                .trimEnd('/')
                .lowercase()
        }

        fun matchesFolderPath(path1: String, path2: String): Boolean {
            if (path1 == path2) return true
            return normalizeFolderPath(path1) == normalizeFolderPath(path2)
        }

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
        private const val KEY_DELETE_AFTER_BACKUP = "delete_after_backup"
        private const val KEY_ENABLE_BLUR_EFFECTS = "enable_blur_effects"
        private const val KEY_AUTO_SYNC_ALBUMS = "auto_sync_albums"
        private const val KEY_HAS_SHOWN_ACCOUNT_SWIPE_NUDGE = "has_shown_account_swipe_nudge"
        private const val KEY_LAST_SEEN_DARK = "last_seen_dark"
        private const val KEY_BOLD_CHOICE_DATE = "last_bold_choice_date"
        private const val KEY_LIFETIME_UPLOADS = "lifetime_uploads"
        private const val KEY_MILESTONE_100_SHOWN = "milestone_100_shown"
        private const val KEY_MILESTONE_1000_SHOWN = "milestone_1000_shown"
        private const val KEY_MILESTONE_10000_SHOWN = "milestone_10000_shown"
        private const val KEY_LATE_NIGHT_DATE = "last_late_night_date"
        private const val KEY_LAST_UPDATE_CHECK_MILLIS = "last_update_check_millis"
        private const val KEY_LAST_NOTIFIED_UPDATE_VERSION = "last_notified_update_version"
        private const val KEY_FOLDERS = "folders"
        private const val KEY_INTERNAL_LAUNCH_TOKEN = "internal_launch_token"
    }
}
