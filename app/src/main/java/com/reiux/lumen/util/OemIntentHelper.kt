package com.reiux.lumen.util

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.provider.Settings

object OemIntentHelper {

    enum class Brand(val displayName: String) {
        XIAOMI("Xiaomi / HyperOS"),
        SAMSUNG("Samsung (One UI)"),
        ONEPLUS("OnePlus (OxygenOS)"),
        OPPO_REALME("Oppo / Realme (ColorOS)"),
        HUAWEI_HONOR("Huawei / Honor (EMUI)"),
        VIVO("Vivo / iQOO (Funtouch OS)"),
        ASUS("Asus (ZenUI)"),
        PIXEL_AOSP("Google Pixel / AOSP")
    }

    fun detectCurrentBrand(): Brand {
        val manufacturer = Build.MANUFACTURER.lowercase()
        return when {
            manufacturer.contains("xiaomi") || manufacturer.contains("redmi") || manufacturer.contains("poco") -> Brand.XIAOMI
            manufacturer.contains("samsung") -> Brand.SAMSUNG
            manufacturer.contains("huawei") || manufacturer.contains("honor") -> Brand.HUAWEI_HONOR
            manufacturer.contains("oneplus") -> Brand.ONEPLUS
            manufacturer.contains("oppo") || manufacturer.contains("realme") -> Brand.OPPO_REALME
            manufacturer.contains("vivo") || manufacturer.contains("iqoo") -> Brand.VIVO
            manufacturer.contains("asus") -> Brand.ASUS
            else -> Brand.PIXEL_AOSP
        }
    }

    fun openAutoStartSettings(context: Context): Boolean {
        val manufacturer = Build.MANUFACTURER.lowercase()
        val candidateIntents = mutableListOf<Intent>()

        when {
            manufacturer.contains("xiaomi") || manufacturer.contains("redmi") || manufacturer.contains("poco") -> {
                candidateIntents.add(Intent().setComponent(ComponentName("com.miui.securitycenter", "com.miui.permcenter.autostart.AutoStartManagementActivity")))
                candidateIntents.add(Intent("miui.intent.action.OP_AUTO_START").addCategory(Intent.CATEGORY_DEFAULT))
                candidateIntents.add(Intent().setComponent(ComponentName("com.miui.powerkeeper", "com.miui.powerkeeper.ui.HiddenAppsConfigActivity")))
            }
            manufacturer.contains("huawei") || manufacturer.contains("honor") -> {
                candidateIntents.add(Intent().setComponent(ComponentName("com.huawei.systemmanager", "com.huawei.systemmanager.startupmgr.ui.StartupNormalAppListActivity")))
                candidateIntents.add(Intent().setComponent(ComponentName("com.huawei.systemmanager", "com.huawei.systemmanager.optimize.process.ProtectActivity")))
                candidateIntents.add(Intent().setComponent(ComponentName("com.huawei.systemmanager", "com.huawei.systemmanager.appcontrol.activity.StartupAppControlActivity")))
            }
            manufacturer.contains("oppo") || manufacturer.contains("realme") || manufacturer.contains("oneplus") -> {
                candidateIntents.add(Intent().setComponent(ComponentName("com.coloros.safecenter", "com.coloros.safecenter.permission.startup.StartupAppListActivity")))
                candidateIntents.add(Intent().setComponent(ComponentName("com.coloros.safecenter", "com.coloros.safecenter.startupapp.StartupAppListActivity")))
                candidateIntents.add(Intent().setComponent(ComponentName("com.oppo.safe", "com.oppo.safe.permission.startup.StartupAppListActivity")))
                candidateIntents.add(Intent().setComponent(ComponentName("com.oplus.battery", "com.oplus.battery.AppBatteryManagerActivity")))
            }
            manufacturer.contains("vivo") || manufacturer.contains("iqoo") -> {
                candidateIntents.add(Intent().setComponent(ComponentName("com.vivo.permissionmanager", "com.vivo.permissionmanager.activity.PurviewTabActivity")))
                candidateIntents.add(Intent().setComponent(ComponentName("com.iqoo.secure", "com.iqoo.secure.ui.phoneoptimize.AddWhiteListActivity")))
            }
            manufacturer.contains("samsung") -> {
                candidateIntents.add(Intent().setComponent(ComponentName("com.samsung.android.lool", "com.samsung.android.sm.battery.ui.BatteryActivity")))
                candidateIntents.add(Intent().setComponent(ComponentName("com.samsung.android.sm", "com.samsung.android.sm.ui.battery.BatteryActivity")))
            }
            manufacturer.contains("asus") -> {
                candidateIntents.add(Intent().setComponent(ComponentName("com.asus.mobilemanager", "com.asus.mobilemanager.autostart.AutoStartActivity")))
            }
        }

        // Try direct OEM activities
        for (intent in candidateIntents) {
            try {
                intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                context.startActivity(intent)
                return true
            } catch (_: Exception) {}
        }

        // Fallback to Application Details Settings
        return try {
            val appDetailsIntent = Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
                data = Uri.parse("package:${context.packageName}")
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            context.startActivity(appDetailsIntent)
            true
        } catch (_: Exception) {
            false
        }
    }
}
