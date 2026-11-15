package com.reiux.lumen.ui.components

import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.AccountCircle
import androidx.compose.material.icons.outlined.CloudUpload
import androidx.compose.material.icons.outlined.FolderCopy
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material.icons.rounded.AccountCircle
import androidx.compose.material.icons.rounded.CloudUpload
import androidx.compose.material.icons.rounded.FolderCopy
import androidx.compose.material.icons.rounded.Settings
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationRail
import androidx.compose.material3.NavigationRailItem
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.text.font.FontWeight
import com.reiux.lumen.util.LumenLogger

enum class NavDestination(
    val title: String,
    val selectedIcon: ImageVector,
    val unselectedIcon: ImageVector
) {
    BACKUP("Backup", Icons.Rounded.CloudUpload, Icons.Outlined.CloudUpload),
    FOLDERS("Folders", Icons.Rounded.FolderCopy, Icons.Outlined.FolderCopy),
    ACCOUNTS("Accounts", Icons.Rounded.AccountCircle, Icons.Outlined.AccountCircle),
    SETTINGS("Settings", Icons.Rounded.Settings, Icons.Outlined.Settings);

    val icon: ImageVector get() = selectedIcon
}

@Composable
fun ExpressiveNavBar(
    selectedDestination: NavDestination,
    onDestinationSelected: (NavDestination) -> Unit,
    modifier: Modifier = Modifier
) {
    NavigationBar(
        modifier = modifier.fillMaxWidth(),
        containerColor = MaterialTheme.colorScheme.surfaceContainer
    ) {
        NavDestination.entries.forEach { destination ->
            val selected = destination == selectedDestination
            val hapticClick = rememberHapticClick(HapticFeedbackType.TextHandleMove) {
                LumenLogger.i("NavBar", "Selected tab: ${destination.title}")
                onDestinationSelected(destination)
            }

            NavigationBarItem(
                selected = selected,
                alwaysShowLabel = false,
                icon = {
                    Icon(
                        imageVector = if (selected) destination.selectedIcon else destination.unselectedIcon,
                        contentDescription = destination.title
                    )
                },
                label = {
                    Text(
                        text = destination.title,
                        fontWeight = if (selected) FontWeight.Bold else FontWeight.Medium
                    )
                },
                onClick = hapticClick
            )
        }
    }
}

@Composable
fun ExpressiveNavRail(
    selectedDestination: NavDestination,
    onDestinationSelected: (NavDestination) -> Unit,
    modifier: Modifier = Modifier
) {
    NavigationRail(
        modifier = modifier,
        containerColor = MaterialTheme.colorScheme.surfaceContainer
    ) {
        NavDestination.entries.forEach { destination ->
            val selected = destination == selectedDestination
            val hapticClick = rememberHapticClick(HapticFeedbackType.TextHandleMove) {
                LumenLogger.i("NavRail", "Selected tab: ${destination.title}")
                onDestinationSelected(destination)
            }

            NavigationRailItem(
                selected = selected,
                alwaysShowLabel = false,
                icon = {
                    Icon(
                        imageVector = if (selected) destination.selectedIcon else destination.unselectedIcon,
                        contentDescription = destination.title
                    )
                },
                label = {
                    Text(
                        text = destination.title,
                        fontWeight = if (selected) FontWeight.Bold else FontWeight.Medium
                    )
                },
                onClick = hapticClick
            )
        }
    }
}
