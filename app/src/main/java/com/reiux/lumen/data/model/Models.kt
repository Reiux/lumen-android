package com.reiux.lumen.data.model

data class GoogleAccount(
    val email: String,
    val credentialString: String,
    val displayName: String? = null,
    val avatarPath: String? = null,
    val androidId: String = "",
    val masterToken: String = ""
)

data class BackupFolder(
    val path: String,
    val displayName: String,
    val enabled: Boolean = true,
    val includeSubfolders: Boolean = false
)

data class BackupSettings(
    val autoBackupEnabled: Boolean = true,
    val wifiOnly: Boolean = true,
    val chargingOnly: Boolean = false,
    val storageSaverQuality: Boolean = false,
    val useQuota: Boolean = false,
    val uploadThreads: Int = 2,
    val colorPreset: String = "Dynamic",
    val notificationsEnabled: Boolean = true,
    val saveLogsEnabled: Boolean = false,
    val developerModeUnlocked: Boolean = false,
    val hasCompletedOnboarding: Boolean = false,
    val hasAcknowledgedAccountRisk: Boolean = false,
    val unlockedPrismTheme: Boolean = false,
    val unlockedMatrixTheme: Boolean = false,
    val unlockedSpookyTheme: Boolean = false,
    val unlockedXmasTheme: Boolean = false,
    val unlockedNewYrTheme: Boolean = false,
    val forcedSeason: String = "NONE",
    val folders: List<BackupFolder> = listOf(
        BackupFolder("/sdcard/DCIM/Camera", "Camera Roll", true),
        BackupFolder("/sdcard/Pictures/Screenshots", "Screenshots", false),
        BackupFolder("/sdcard/Pictures", "Pictures", false),
        BackupFolder("/sdcard/Download", "Downloads", false)
    )
)

enum class UploadStatus {
    QUEUED,
    HASHING,
    CHECKING,
    UPLOADING,
    COMMITTING,
    COMPLETED,
    FAILED,
    SKIPPED
}

data class UploadItem(
    val id: String,
    val filePath: String,
    val fileName: String,
    val fileSize: Long,
    val status: UploadStatus = UploadStatus.QUEUED,
    val bytesUploaded: Long = 0L,
    val error: String? = null,
    val mediaKey: String? = null
)

enum class AuthFailureKind {
    NONE,
    TOKEN_EXPIRED,
    SIGN_IN_REQUIRED,
    ACCOUNT_RESTRICTED,
    RATE_LIMITED,
    TRANSIENT
}

data class AccountBlock(
    val kind: AuthFailureKind,
    val timestamp: Long = System.currentTimeMillis(),
    val accountEmail: String,
    val retryAfterTimestamp: Long? = null,
    val rawDetail: String? = null
)
