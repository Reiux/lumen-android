package com.reiux.lumen.data.repository

import android.content.Context
import android.content.SharedPreferences
import com.reiux.lumen.data.model.BackupFolder
import com.reiux.lumen.data.model.BackupSettings
import com.reiux.lumen.util.LumenLogger
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import org.json.JSONArray
import org.json.JSONObject

class SettingsRepository(context: Context) {
    private val prefs: SharedPreferences = context.getSharedPreferences("lumen_settings", Context.MODE_PRIVATE)

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
        val unlockedPrism = prefs.getBoolean(KEY_UNLOCKED_PRISM, false)
        val unlockedMatrix = prefs.getBoolean(KEY_UNLOCKED_MATRIX, false)

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
                        enabled = obj.optBoolean("enabled", true)
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
            unlockedPrismTheme = unlockedPrism,
            unlockedMatrixTheme = unlockedMatrix,
            folders = folders
        )
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

    private fun saveFolders(folders: List<BackupFolder>) {
        val array = JSONArray()
        for (f in folders) {
            val obj = JSONObject()
            obj.put("path", f.path)
            obj.put("displayName", f.displayName)
            obj.put("enabled", f.enabled)
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

    companion object {
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
        private const val KEY_UNLOCKED_PRISM = "unlocked_prism_theme"
        private const val KEY_UNLOCKED_MATRIX = "unlocked_matrix_theme"
        private const val KEY_FOLDERS = "folders"
    }
}
