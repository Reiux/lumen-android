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

        val folders = getFoldersForAccount(null)

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

    fun parseFoldersJson(jsonStr: String?): List<BackupFolder>? {
        if (jsonStr.isNullOrEmpty()) return null
        return try {
            val list = mutableListOf<BackupFolder>()
            val array = JSONArray(jsonStr)
            for (i in 0 until array.length()) {
                val obj = array.getJSONObject(i)
                val modeStr = obj.optString("targetAlbumMode", "NONE")
                val mode = try { AlbumTargetMode.valueOf(modeStr) } catch (_: Exception) { AlbumTargetMode.NONE }
                val customName = if (obj.has("customAlbumName")) obj.getString("customAlbumName") else null
                val customNickname = if (obj.has("customAlbumNickname")) obj.getString("customAlbumNickname") else null
                val accountEmail = if (obj.has("accountEmail")) obj.getString("accountEmail") else null
                list.add(
                    BackupFolder(
                        path = obj.getString("path"),
                        displayName = obj.getString("displayName"),
                        enabled = obj.optBoolean("enabled", true),
                        includeSubfolders = obj.optBoolean("includeSubfolders", false),
                        targetAlbumMode = mode,
                        customAlbumName = customName,
                        customAlbumNickname = customNickname,
                        accountEmail = accountEmail
                    )
                )
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

    fun getAllFolders(): List<BackupFolder> {
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

    fun saveFolders(folders: List<BackupFolder>) {
        val json = serializeFoldersJson(folders)
        prefs.edit().putString(KEY_FOLDERS, json).apply()
        _settings.value = _settings.value.copy(folders = folders)
    }

    private fun matchesAccount(email1: String?, email2: String?): Boolean {
        if (email1.isNullOrBlank() && email2.isNullOrBlank()) return true
        if (email1.isNullOrBlank() || email2.isNullOrBlank()) return false
        return email1.equals(email2, ignoreCase = true)
    }

    fun toggleFolder(path: String, enabled: Boolean) = toggleFolder(path, null, enabled)
    fun toggleFolder(path: String, accountEmail: String?, enabled: Boolean) {
        val current = getAllFolders()
        val updated = current.map {
            if (it.path == path && matchesAccount(it.accountEmail, accountEmail)) it.copy(enabled = enabled) else it
        }
        saveFolders(updated)
    }

    fun addFolder(folder: BackupFolder) {
        val current = getAllFolders().toMutableList()
        val index = current.indexOfFirst {
            it.path == folder.path && matchesAccount(it.accountEmail, folder.accountEmail)
        }
        if (index >= 0) {
            current[index] = folder
        } else {
            current.add(folder)
        }
        saveFolders(current)
    }

    fun removeFolder(path: String) = removeFolder(path, null)
    fun removeFolder(path: String, accountEmail: String?) {
        val current = getAllFolders()
        val updated = current.filterNot {
            it.path == path && matchesAccount(it.accountEmail, accountEmail)
        }
        saveFolders(updated)
    }

    fun setIncludeSubfolders(path: String, includeSubfolders: Boolean) = setIncludeSubfolders(path, null, includeSubfolders)
    fun setIncludeSubfolders(path: String, accountEmail: String?, includeSubfolders: Boolean) {
        val current = getAllFolders()
        val updated = current.map {
            if (it.path == path && matchesAccount(it.accountEmail, accountEmail)) it.copy(includeSubfolders = includeSubfolders) else it
        }
        saveFolders(updated)
    }

    fun getFoldersForAccount(email: String?): List<BackupFolder> {
        val all = getAllFolders()
        if (email.isNullOrBlank()) {
            return all.filter { it.accountEmail.isNullOrBlank() }
        }
        return all.filter { it.accountEmail.isNullOrBlank() || it.accountEmail.equals(email, ignoreCase = true) }
    }

    fun saveFoldersForAccount(email: String?, folders: List<BackupFolder>) {
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

    fun addFolderForAccount(email: String?, folder: BackupFolder) {
        val f = if (!email.isNullOrBlank() && folder.accountEmail.isNullOrBlank()) folder.copy(accountEmail = email) else folder
        addFolder(f)
    }

    fun toggleFolderForAccount(email: String?, path: String, enabled: Boolean) {
        val current = getAllFolders()
        val updated = current.map {
            if (it.path == path && (email.isNullOrBlank() || it.accountEmail.equals(email, ignoreCase = true))) {
                it.copy(enabled = enabled)
            } else it
        }
        saveFolders(updated)
    }

    fun removeFolderForAccount(email: String?, path: String) {
        val current = getAllFolders()
        val updated = current.filterNot {
            it.path == path && (email.isNullOrBlank() || it.accountEmail.equals(email, ignoreCase = true))
        }
        saveFolders(updated)
    }

    fun setIncludeSubfoldersForAccount(email: String?, path: String, includeSubfolders: Boolean) {
        val current = getAllFolders()
        val updated = current.map {
            if (it.path == path && (email.isNullOrBlank() || it.accountEmail.equals(email, ignoreCase = true))) {
                it.copy(includeSubfolders = includeSubfolders)
            } else it
        }
        saveFolders(updated)
    }

    fun updateFolderForAccount(email: String?, folder: BackupFolder) = addFolderForAccount(email, folder)

    fun getFolderWatermarkId(email: String?, folderPath: String): Long {
        val emailKey = email?.lowercase()?.trim() ?: "default"
        val key = "watermark_id_${emailKey}_${folderPath.hashCode()}"
        return prefs.getLong(key, 0L)
    }

    fun saveFolderWatermarkId(email: String?, folderPath: String, watermarkId: Long) {
        val emailKey = email?.lowercase()?.trim() ?: "default"
        val key = "watermark_id_${emailKey}_${folderPath.hashCode()}"
        prefs.edit().putLong(key, watermarkId).apply()
    }

    private val _recentAlbumsFlow = MutableStateFlow<List<com.reiux.lumen.data.network.GoogleAlbum>>(emptyList())
    val recentAlbumsFlow: StateFlow<List<com.reiux.lumen.data.network.GoogleAlbum>> = _recentAlbumsFlow.asStateFlow()

    fun getAlbumMediaKey(email: String, albumTitle: String): String? {
        val key = "album_key_${email.lowercase().trim()}_${albumTitle.lowercase().trim().replace(Regex("\\s+"), "_")}"
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

    fun saveAlbumMediaKey(email: String, albumTitle: String, mediaKey: String) {
        val cleanEmail = email.lowercase().trim()
        val key = "album_key_${cleanEmail}_${albumTitle.lowercase().trim().replace(Regex("\\s+"), "_")}"
        prefs.edit().putString(key, mediaKey).apply()
        
        // Also save to recent albums list
        val recentsKey = "recent_albums_$cleanEmail"
        val existingJson = prefs.getString(recentsKey, "[]") ?: "[]"
        try {
            val array = JSONArray(existingJson)
            val newList = mutableListOf<Triple<String, String, String>>()
            for (i in 0 until array.length()) {
                val obj = array.getJSONObject(i)
                val t = obj.optString("title")
                val mk = obj.optString("mediaKey")
                val acc = obj.optString("accountEmail", cleanEmail)
                if (t.isNotEmpty() && mk.isNotEmpty() && mk != mediaKey && t != albumTitle) {
                    newList.add(Triple(t, mk, acc))
                }
            }
            newList.add(0, Triple(albumTitle, mediaKey, cleanEmail)) // add to top
            val newArray = JSONArray()
            newList.take(20).forEach { (t, mk, acc) -> // keep top 20
                val obj = JSONObject()
                obj.put("title", t)
                obj.put("mediaKey", mk)
                obj.put("accountEmail", acc)
                newArray.put(obj)
            }
            prefs.edit().putString(recentsKey, newArray.toString()).apply()
            _recentAlbumsFlow.value = getRecentAlbums(cleanEmail)
        } catch (e: Exception) {
            LumenLogger.w("Storage") { "Failed to update recent albums: ${e.message}" }
        }
    }

    fun getRecentAlbums(email: String): List<com.reiux.lumen.data.network.GoogleAlbum> {
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
                val acc = if (obj.has("accountEmail") && obj.getString("accountEmail").isNotBlank()) {
                    obj.getString("accountEmail").lowercase().trim()
                } else if (cleanEmail.isNotBlank()) {
                    cleanEmail
                } else null
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

    fun markAlbumMissing(email: String, keyOrTitle: String, missing: Boolean = true) {
        val cleanEmail = email.lowercase().trim()
        val recentsKey = "recent_albums_$cleanEmail"
        val existingJson = prefs.getString(recentsKey, "[]") ?: "[]"
        try {
            val array = JSONArray(existingJson)
            val newArray = JSONArray()
            for (i in 0 until array.length()) {
                val obj = array.getJSONObject(i)
                val t = obj.optString("title")
                val mk = obj.optString("mediaKey")
                if (t == keyOrTitle || mk == keyOrTitle) {
                    obj.put("isMissing", missing)
                }
                newArray.put(obj)
            }
            prefs.edit().putString(recentsKey, newArray.toString()).apply()
            _recentAlbumsFlow.value = getRecentAlbums(email)
            LumenLogger.i("Storage") { "Marked album '$keyOrTitle' as missing=$missing for $email" }
        } catch (e: Exception) {
            LumenLogger.w("Storage") { "Failed to mark album as missing: ${e.message}" }
        }
    }

    fun getAllRecentAlbums(emails: Set<String>): List<com.reiux.lumen.data.network.GoogleAlbum> {
        val all = mutableListOf<com.reiux.lumen.data.network.GoogleAlbum>()
        val allKeys = prefs.all.keys.filter { it.startsWith("recent_albums_") }
        val targetKeys = (emails.map { "recent_albums_${it.lowercase().trim()}" } + allKeys).toSet()

        for (k in targetKeys) {
            val emailFromKey = k.removePrefix("recent_albums_").trim()
            val existingJson = prefs.getString(k, "[]") ?: "[]"
            try {
                val array = JSONArray(existingJson)
                for (i in 0 until array.length()) {
                    val obj = array.getJSONObject(i)
                    val t = obj.optString("title")
                    val mk = obj.optString("mediaKey")
                    val acc = if (obj.has("accountEmail") && obj.getString("accountEmail").isNotBlank()) {
                        obj.getString("accountEmail").lowercase().trim()
                    } else if (emailFromKey.isNotBlank()) {
                        emailFromKey
                    } else null
                    val isMissing = obj.optBoolean("isMissing", false)
                    if (t.isNotEmpty() && mk.isNotEmpty()) {
                        all.add(com.reiux.lumen.data.network.GoogleAlbum(mediaKey = mk, title = t, accountEmail = acc, isMissing = isMissing))
                    }
                }
            } catch (e: Exception) {
                LumenLogger.w("Storage") { "Failed to parse recent albums for key $k: ${e.message}" }
            }
        }
        return all.distinctBy { "${it.accountEmail}:${it.mediaKey}" }
    }

    fun removeRecentAlbum(email: String, keyOrTitle: String) {
        val recentsKey = "recent_albums_${email.lowercase().trim()}"
        val existingJson = prefs.getString(recentsKey, "[]") ?: "[]"
        try {
            val array = JSONArray(existingJson)
            val newArray = JSONArray()
            for (i in 0 until array.length()) {
                val obj = array.getJSONObject(i)
                val t = obj.optString("title")
                val mk = obj.optString("mediaKey")
                if (t != keyOrTitle && mk != keyOrTitle) {
                    newArray.put(obj)
                }
            }
            prefs.edit().putString(recentsKey, newArray.toString()).apply()
            _recentAlbumsFlow.value = getRecentAlbums(email)
            LumenLogger.i("Storage") { "Removed album '$keyOrTitle' from recent albums for $email" }
        } catch (e: Exception) {
            LumenLogger.w("Storage") { "Failed to remove album from recents: ${e.message}" }
        }
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
