package com.reiux.lumen.util

import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Typeface
import android.os.Build
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import com.reiux.lumen.R
import java.io.File

object RomDetector {

    fun isStockOrAosp(context: Context): Boolean {
        // 1. Direct file probe: Google Sans Flex or regular Google Sans on disk
        if (hasGoogleSansFont()) {
            return true
        }

        // 2. Check for Google Pixel or AOSP launcher (LineageOS, crDroid, PixelOS, etc.)
        val pm = context.packageManager
        val homeIntent = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_HOME)
        val resolveInfo = pm.resolveActivity(homeIntent, PackageManager.MATCH_DEFAULT_ONLY)
        val launcherPkg = resolveInfo?.activityInfo?.packageName ?: ""

        val isAospLauncher = launcherPkg == "com.android.launcher3" ||
                launcherPkg == "com.google.android.apps.nexuslauncher" ||
                launcherPkg.startsWith("org.lineageos.") ||
                launcherPkg.startsWith("com.google.android.")

        if (isAospLauncher) {
            return true
        }

        // 3. Fallback OEM check: If OEM skin properties exist and no AOSP fonts were found
        val oemProps = listOf(
            "ro.miui.ui.version.name",       // Xiaomi HyperOS / MIUI
            "ro.build.version.oneui",        // Samsung One UI
            "ro.build.version.emui",         // Huawei / Honor
            "ro.build.version.opporom",      // Oppo ColorOS
            "ro.vivo.os.version"             // Vivo Funtouch/OriginOS
        )
        for (prop in oemProps) {
            if (getSystemProperty(prop).isNotBlank()) {
                return false
            }
        }

        val manufacturer = Build.MANUFACTURER.lowercase()
        val isOemManufacturer = manufacturer.contains("samsung") ||
                manufacturer.contains("huawei") ||
                manufacturer.contains("honor") ||
                manufacturer.contains("oppo") ||
                manufacturer.contains("realme") ||
                manufacturer.contains("vivo")

        if (isOemManufacturer) {
            return false
        }

        return false
    }

    fun hasGoogleSansFont(): Boolean {
        return SystemFontProvider.findFlexFontFile() != null ||
                SystemFontProvider.findRegularFontFile() != null ||
                SystemFontProvider.findRoundedFontFile() != null
    }

    private fun getSystemProperty(key: String): String {
        return try {
            val clazz = Class.forName("android.os.SystemProperties")
            val getMethod = clazz.getMethod("get", String::class.java)
            getMethod.invoke(null, key) as? String ?: ""
        } catch (_: Exception) {
            ""
        }
    }
}

object SystemFontProvider {
    val candidateDirs = listOf(
        File("/product/fonts"),
        File("/system/fonts"),
        File("/system_ext/fonts"),
        File("/vendor/fonts")
    )

    private var checkedFlex = false
    private var cachedFlexFile: File? = null

    private var checkedRegular = false
    private var cachedRegularFile: File? = null

    private var checkedRounded = false
    private var cachedRoundedFile: File? = null

    private var resolvedFamily = false
    private var cachedFamily: FontFamily? = null

    private var resolvedRoundedFamily = false
    private var cachedRoundedFamily: FontFamily? = null

    private fun findFontFromXml(keyword: String): File? {
        val xmlFiles = listOf(
            File("/system/etc/fonts.xml"),
            File("/etc/fonts.xml"),
            File("/product/etc/fonts_customization.xml"),
            File("/product/etc/fonts.xml"),
            File("/system_ext/etc/fonts.xml")
        )
        for (xml in xmlFiles) {
            if (xml.exists() && xml.canRead()) {
                try {
                    xml.useLines { lines ->
                        for (line in lines) {
                            if (line.contains(keyword, ignoreCase = true)) {
                                val regex = Regex("([a-zA-Z0-9_-]+\\.ttf)")
                                val match = regex.find(line)
                                if (match != null) {
                                    val filename = match.value
                                    for (dir in candidateDirs) {
                                        val f = File(dir, filename)
                                        if (f.exists() && f.canRead()) return f
                                    }
                                }
                            }
                        }
                    }
                } catch (_: Exception) {}
            }
        }
        return null
    }

    fun findFlexFontFile(): File? {
        if (checkedFlex) return cachedFlexFile
        checkedFlex = true

        val flexDirectNames = listOf(
            "GoogleSansFlex-Regular.ttf",
            "GoogleSansFlex-Regular",
            "GoogleSansFlex.ttf",
            "GoogleSansFlex",
            "GoogleSansFlex-VariableFont_GRAD,ROND,opsz,slnt,wdth,wght.ttf",
            "GoogleSans-Flex.ttf",
            "GoogleSans-Flex-Regular.ttf",
            "GoogleSansFlex_wght.ttf",
            "GoogleSansFlex-VF.ttf",
            "GoogleSansVariable.ttf",
            "GoogleSans-Variable.ttf"
        )
        for (dir in candidateDirs) {
            for (name in flexDirectNames) {
                val f = File(dir, name)
                if (f.exists() && f.canRead()) {
                    cachedFlexFile = f
                    return f
                }
            }
            if (dir.exists() && dir.canRead()) {
                try {
                    val files = dir.listFiles()
                    if (files != null) {
                        val match = files.firstOrNull { file ->
                            val n = file.name
                            (n.contains("GoogleSansFlex", ignoreCase = true) ||
                                    (n.contains("GoogleSans", ignoreCase = true) && n.contains("Flex", ignoreCase = true)) ||
                                    (n.contains("GoogleSans", ignoreCase = true) && n.contains("Variable", ignoreCase = true))) &&
                                    !n.contains("Clock", ignoreCase = true)
                        }
                        if (match != null && match.canRead()) {
                            cachedFlexFile = match
                            return match
                        }
                    }
                } catch (_: Exception) {}
            }
        }

        // Fallback: check fonts.xml references
        val fromXml = findFontFromXml("GoogleSansFlex") ?: findFontFromXml("GoogleSans-Flex")
        if (fromXml != null) {
            cachedFlexFile = fromXml
            return fromXml
        }

        return null
    }

    fun findRegularFontFile(): File? {
        if (checkedRegular) return cachedRegularFile
        checkedRegular = true

        val regularDirectNames = listOf(
            "GoogleSans-Regular.ttf",
            "GoogleSans-Regular",
            "GoogleSansMedium.ttf",
            "GoogleSansMedium",
            "GoogleSans.ttf",
            "GoogleSans",
            "ProductSans-Regular.ttf",
            "ProductSans.ttf",
            "ProductSans-Medium.ttf"
        )
        for (dir in candidateDirs) {
            for (name in regularDirectNames) {
                val f = File(dir, name)
                if (f.exists() && f.canRead()) {
                    cachedRegularFile = f
                    return f
                }
            }
            if (dir.exists() && dir.canRead()) {
                try {
                    val files = dir.listFiles()
                    if (files != null) {
                        val match = files.firstOrNull { file ->
                            val n = file.name
                            (n.contains("GoogleSans", ignoreCase = true) || n.contains("ProductSans", ignoreCase = true)) &&
                                    !n.contains("Clock", ignoreCase = true) &&
                                    !n.contains("Flex", ignoreCase = true)
                        }
                        if (match != null && match.canRead()) {
                            cachedRegularFile = match
                            return match
                        }
                    }
                } catch (_: Exception) {}
            }
        }

        val fromXml = findFontFromXml("GoogleSans-Regular") ?: findFontFromXml("ProductSans-Regular")
        if (fromXml != null) {
            cachedRegularFile = fromXml
            return fromXml
        }

        return null
    }

    fun findRoundedFontFile(): File? {
        if (checkedRounded) return cachedRoundedFile
        checkedRounded = true
        val roundedDirectNames = listOf(
            "GoogleSansRounded-Bold.ttf",
            "GoogleSansRounded-Bold",
            "GoogleSansRounded-Medium.ttf",
            "GoogleSansRounded-Medium",
            "GoogleSansRounded-Regular.ttf",
            "GoogleSansRounded-Regular",
            "GoogleSans-Rounded.ttf",
            "GoogleSansRounded.ttf",
            "GoogleSans-Rounded-Regular.ttf"
        )
        for (dir in candidateDirs) {
            for (name in roundedDirectNames) {
                val f = File(dir, name)
                if (f.exists() && f.canRead()) {
                    cachedRoundedFile = f
                    return f
                }
            }
            if (dir.exists() && dir.canRead()) {
                try {
                    val files = dir.listFiles()
                    if (files != null) {
                        val match = files.firstOrNull { file ->
                            val n = file.name
                            n.contains("GoogleSansRounded", ignoreCase = true) ||
                                    n.contains("GoogleSans-Rounded", ignoreCase = true)
                        }
                        if (match != null && match.canRead()) {
                            cachedRoundedFile = match
                            return match
                        }
                    }
                } catch (_: Exception) {}
            }
        }

        val fromXml = findFontFromXml("GoogleSansRounded") ?: findFontFromXml("GoogleSans-Rounded")
        if (fromXml != null) {
            cachedRoundedFile = fromXml
            return fromXml
        }

        return null
    }

    fun getGoogleSansFamily(): FontFamily? {
        if (resolvedFamily) return cachedFamily
        resolvedFamily = true

        // Priority 1: Google Sans Flex Variable Font
        val flexFile = findFlexFontFile()
        if (flexFile != null) {
            try {
                val tf = Typeface.createFromFile(flexFile)
                if (tf != null) {
                    val fam = FontFamily(tf)
                    cachedFamily = fam
                    return fam
                }
            } catch (_: Exception) {}
        }

        // Priority 2: Google Sans Static
        val regularFile = findRegularFontFile()
        if (regularFile != null) {
            try {
                val tf = Typeface.createFromFile(regularFile)
                if (tf != null) {
                    val fam = FontFamily(tf)
                    cachedFamily = fam
                    return fam
                }
            } catch (_: Exception) {}
        }

        // Priority 3: System named typeface
        val systemNames = listOf("google-sans", "google-sans-medium", "google-sans-flex", "product-sans")
        for (name in systemNames) {
            try {
                val tf = Typeface.create(name, Typeface.NORMAL)
                if (tf != null && tf != Typeface.DEFAULT) {
                    val fam = FontFamily(tf)
                    cachedFamily = fam
                    return fam
                }
            } catch (_: Exception) {}
        }

        // Priority 4: Bundled Google Sans Flex inside APK
        try {
            val fam = FontFamily(Font(R.font.google_sans_flex))
            cachedFamily = fam
            return fam
        } catch (_: Exception) {}

        return null
    }

    fun getGoogleSansRoundedFamily(): FontFamily? {
        if (resolvedRoundedFamily) return cachedRoundedFamily
        resolvedRoundedFamily = true

        // Priority 1: Dedicated Google Sans Rounded font file if available on the system
        val roundedFile = findRoundedFontFile()
        if (roundedFile != null) {
            try {
                val tf = Typeface.createFromFile(roundedFile)
                if (tf != null) {
                    val fam = FontFamily(tf)
                    cachedRoundedFamily = fam
                    return fam
                }
            } catch (_: Exception) {}
        }

        // Priority 2: Google Sans Flex Variable Font with 'ROND' 100 variation axis applied
        val flexFile = findFlexFontFile()
        if (flexFile != null) {
            try {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                    val tf = Typeface.Builder(flexFile)
                        .setFontVariationSettings("'ROND' 100")
                        .build()
                    if (tf != null) {
                        val fam = FontFamily(tf)
                        cachedRoundedFamily = fam
                        return fam
                    }
                } else {
                    val tf = Typeface.createFromFile(flexFile)
                    if (tf != null) {
                        val fam = FontFamily(tf)
                        cachedRoundedFamily = fam
                        return fam
                    }
                }
            } catch (_: Exception) {}
        }

        // Priority 3: Fallback to system-named font if recognized
        val systemRoundedNames = listOf("google-sans-rounded", "google-sans-rounded-medium")
        for (name in systemRoundedNames) {
            try {
                val tf = Typeface.create(name, Typeface.NORMAL)
                if (tf != null && tf != Typeface.DEFAULT) {
                    val fam = FontFamily(tf)
                    cachedRoundedFamily = fam
                    return fam
                }
            } catch (_: Exception) {}
        }

        // Priority 4: Bundled Google Sans Flex with 'ROND' 100 inside APK
        try {
            val fam = FontFamily(Font(R.font.google_sans_flex_round))
            cachedRoundedFamily = fam
            return fam
        } catch (_: Exception) {}

        // Fallback to regular Google Sans
        val fallback = getGoogleSansFamily()
        cachedRoundedFamily = fallback
        return fallback
    }
}
