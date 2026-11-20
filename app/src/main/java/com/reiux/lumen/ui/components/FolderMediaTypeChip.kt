package com.reiux.lumen.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Image
import androidx.compose.material.icons.rounded.PermMedia
import androidx.compose.material.icons.rounded.Videocam
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.reiux.lumen.R
import com.reiux.lumen.data.model.BackupFolder
import com.reiux.lumen.data.repository.SettingsRepository
import com.reiux.lumen.util.LocalMediaFolder

enum class FolderMediaKind {
    PHOTOS,
    VIDEOS,
    MEDIA
}

fun resolveFolderMediaKind(imageCount: Int, videoCount: Int): FolderMediaKind? {
    return when {
        imageCount > 0 && videoCount > 0 -> FolderMediaKind.MEDIA
        imageCount > 0 -> FolderMediaKind.PHOTOS
        videoCount > 0 -> FolderMediaKind.VIDEOS
        else -> null
    }
}

fun resolveFolderMediaKindForPath(
    folder: BackupFolder,
    discoveredFolders: List<LocalMediaFolder>
): FolderMediaKind? {
    if (discoveredFolders.isEmpty()) return null
    val normTarget = SettingsRepository.normalizeFolderPath(folder.path)
    if (normTarget.isEmpty()) return null

    var totalImages = 0
    var totalVideos = 0
    for (discovered in discoveredFolders) {
        val normDiscovered = SettingsRepository.normalizeFolderPath(discovered.path)
        val matches = if (folder.includeSubfolders) {
            normDiscovered == normTarget || normDiscovered.startsWith("$normTarget/")
        } else {
            normDiscovered == normTarget
        }
        if (matches) {
            totalImages += discovered.imageCount
            totalVideos += discovered.videoCount
        }
    }
    return resolveFolderMediaKind(totalImages, totalVideos)
}

@Composable
fun FolderMediaTypeChip(
    kind: FolderMediaKind,
    enabled: Boolean = true,
    compact: Boolean = false,
    modifier: Modifier = Modifier
) {
    val (icon, labelRes) = when (kind) {
        FolderMediaKind.PHOTOS -> Icons.Rounded.Image to R.string.tag_media_photos
        FolderMediaKind.VIDEOS -> Icons.Rounded.Videocam to R.string.tag_media_videos
        FolderMediaKind.MEDIA -> Icons.Rounded.PermMedia to R.string.tag_media_mixed
    }

    val containerColor = if (enabled) {
        MaterialTheme.colorScheme.secondaryContainer
    } else {
        MaterialTheme.colorScheme.surfaceContainerHighest
    }
    val contentColor = if (enabled) {
        MaterialTheme.colorScheme.onSecondaryContainer
    } else {
        MaterialTheme.colorScheme.onSurfaceVariant
    }

    Surface(
        modifier = modifier,
        shape = RoundedCornerShape(50),
        color = containerColor
    ) {
        Row(
            modifier = Modifier.padding(
                horizontal = if (compact) 8.dp else 10.dp,
                vertical = if (compact) 2.dp else 6.dp
            ),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = contentColor,
                modifier = Modifier.size(if (compact) 12.dp else 14.dp)
            )
            Text(
                text = stringResource(labelRes),
                style = if (compact) {
                    MaterialTheme.typography.labelSmall.copy(fontSize = 10.sp)
                } else {
                    MaterialTheme.typography.labelMedium
                },
                fontWeight = if (compact) FontWeight.Bold else FontWeight.SemiBold,
                color = contentColor,
                maxLines = 1
            )
        }
    }
}
