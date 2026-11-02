package com.reiux.lumen.ui.components

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.reiux.lumen.data.model.GoogleAccount
import java.io.File

@Composable
fun AccountAvatar(
    account: GoogleAccount?,
    emailFallback: String? = null,
    size: Dp = 36.dp,
    modifier: Modifier = Modifier
) {
    val email = account?.email ?: emailFallback ?: ""
    val initial = email.take(1).uppercase()
    val avatarFile = account?.avatarPath?.let { File(it) }

    if (avatarFile != null && avatarFile.exists()) {
        AsyncImage(
            model = avatarFile,
            contentDescription = "Account avatar",
            contentScale = ContentScale.Crop,
            modifier = modifier
                .size(size)
                .clip(CircleShape)
        )
    } else {
        Surface(
            shape = CircleShape,
            color = MaterialTheme.colorScheme.primaryContainer,
            modifier = modifier.size(size)
        ) {
            Box(contentAlignment = Alignment.Center) {
                Text(
                    text = initial.ifBlank { "?" },
                    fontSize = if (size < 32.dp) 11.sp else 14.sp,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onPrimaryContainer
                )
            }
        }
    }
}
