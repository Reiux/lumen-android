package com.reiux.lumen.data.model

data class UpdateInfo(
    val versionName: String,
    val releaseNotes: String,
    val downloadUrl: String,
    val isUpdateAvailable: Boolean
)
