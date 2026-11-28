package com.reiux.lumen.ui.screens

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.widget.Toast
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.foundation.background
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.layout.wrapContentWidth
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.automirrored.rounded.OpenInNew
import androidx.compose.material.icons.rounded.AutoAwesome
import androidx.compose.material.icons.rounded.DoneAll
import androidx.compose.material.icons.rounded.MoreVert
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.MaterialShapes
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.material3.toShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.reiux.lumen.R
import com.reiux.lumen.data.model.WhatsNewAccent
import com.reiux.lumen.data.model.WhatsNewCatalog
import com.reiux.lumen.data.model.WhatsNewItem
import com.reiux.lumen.data.model.WhatsNewRelease
import com.reiux.lumen.ui.components.DialogBlurEffect
import com.reiux.lumen.ui.components.ExpressiveButton
import com.reiux.lumen.ui.components.ExpressiveIconButton
import com.reiux.lumen.ui.components.ExpressiveTextButton
import com.reiux.lumen.ui.components.ExpressiveTonalButton
import com.reiux.lumen.ui.components.GroupPosition
import com.reiux.lumen.ui.components.LocalFoldPosture
import com.reiux.lumen.ui.components.TabletopSplitLayout
import com.reiux.lumen.ui.components.animatedGroupedCardShape
import com.reiux.lumen.ui.components.groupPositionOf
import com.reiux.lumen.ui.components.groupedCardShape
import com.reiux.lumen.ui.components.rememberExpressivePressed
import com.reiux.lumen.ui.components.rememberHapticClick
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

private const val GITHUB_RELEASES_URL = "https://github.com/Reiux/lumen-android/releases"

@Composable
fun WhatsNewScreen(
    readItemIds: Set<String>,
    onMarkItemRead: (String) -> Unit,
    onMarkAllRead: () -> Unit,
    onNavigateBack: () -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val foldPosture = LocalFoldPosture.current
    val hasUnread = WhatsNewCatalog.hasUnread(readItemIds)
    var menuExpanded by remember { mutableStateOf(false) }
    var selectedItemId by rememberSaveable { mutableStateOf<String?>(null) }
    val selectedItem = remember(selectedItemId) {
        val id = selectedItemId ?: return@remember null
        WhatsNewCatalog.releases.firstNotNullOfOrNull { release ->
            release.items.firstOrNull { it.id == id }?.let { release to it }
        }
    }

    Surface(
        modifier = modifier.fillMaxSize(),
        color = MaterialTheme.colorScheme.surface
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .windowInsetsPadding(WindowInsets.safeDrawing)
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .wrapContentWidth(Alignment.CenterHorizontally)
                    .widthIn(max = 720.dp)
                    .fillMaxWidth()
                    .padding(horizontal = 8.dp, vertical = 4.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                ExpressiveIconButton(onClick = onNavigateBack) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Rounded.ArrowBack,
                        contentDescription = stringResource(R.string.nav_back),
                        tint = MaterialTheme.colorScheme.onSurface
                    )
                }

                Box {
                    ExpressiveIconButton(
                        onClick = { menuExpanded = true },
                        colors = IconButtonDefaults.iconButtonColors()
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.MoreVert,
                            contentDescription = stringResource(R.string.action_more_options),
                            tint = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }

                    DropdownMenu(
                        expanded = menuExpanded,
                        onDismissRequest = { menuExpanded = false },
                        shape = RoundedCornerShape(20.dp),
                        containerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
                        tonalElevation = 6.dp
                    ) {
                        DropdownMenuItem(
                            text = { Text(stringResource(R.string.whats_new_mark_all_read)) },
                            leadingIcon = {
                                Icon(
                                    imageVector = Icons.Rounded.DoneAll,
                                    contentDescription = null,
                                    modifier = Modifier.size(20.dp)
                                )
                            },
                            enabled = hasUnread,
                            onClick = {
                                menuExpanded = false
                                onMarkAllRead()
                            },
                            modifier = Modifier.height(48.dp)
                        )
                        DropdownMenuItem(
                            text = { Text(stringResource(R.string.whats_new_view_github)) },
                            leadingIcon = {
                                Icon(
                                    imageVector = Icons.AutoMirrored.Rounded.OpenInNew,
                                    contentDescription = null,
                                    modifier = Modifier.size(20.dp)
                                )
                            },
                            onClick = {
                                menuExpanded = false
                                openReleasesUrl(context)
                            },
                            modifier = Modifier.height(48.dp)
                        )
                    }
                }
            }

            BoxWithConstraints(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth()
            ) {
                if (foldPosture.isTabletop && maxHeight >= 520.dp) {
                    TabletopSplitLayout(
                        modifier = Modifier.fillMaxSize(),
                        topContent = {
                            Box(
                                modifier = Modifier
                                    .align(Alignment.Center)
                                    .widthIn(max = 720.dp)
                                    .fillMaxSize()
                                    .padding(horizontal = 24.dp),
                                contentAlignment = Alignment.CenterStart
                            ) {
                                Column(
                                    verticalArrangement = Arrangement.spacedBy(8.dp)
                                ) {
                                    WhatsNewCatalog.releases.firstOrNull()?.let { latestRelease ->
                                        Surface(
                                            shape = RoundedCornerShape(100.dp),
                                            color = MaterialTheme.colorScheme.primaryContainer
                                        ) {
                                            Text(
                                                text = latestRelease.headerLabel,
                                                style = MaterialTheme.typography.labelMedium,
                                                fontWeight = FontWeight.Bold,
                                                color = MaterialTheme.colorScheme.onPrimaryContainer,
                                                modifier = Modifier.padding(horizontal = 12.dp, vertical = 4.dp)
                                            )
                                        }
                                    }
                                    Text(
                                        text = stringResource(R.string.whats_new_title),
                                        style = MaterialTheme.typography.headlineLargeEmphasized,
                                        color = MaterialTheme.colorScheme.onSurface
                                    )
                                    Text(
                                        text = stringResource(R.string.whats_new_subtitle),
                                        style = MaterialTheme.typography.bodyMedium,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                            }
                        },
                        bottomContent = {
                            LazyColumn(
                                modifier = Modifier
                                    .align(Alignment.TopCenter)
                                    .widthIn(max = 720.dp)
                                    .fillMaxSize(),
                                contentPadding = PaddingValues(
                                    start = 16.dp,
                                    end = 16.dp,
                                    top = 8.dp,
                                    bottom = 28.dp
                                ),
                                verticalArrangement = Arrangement.spacedBy(20.dp)
                            ) {
                                whatsNewReleaseAndFooterItems(
                                    readItemIds = readItemIds,
                                    context = context,
                                    onItemClick = { itemId ->
                                        onMarkItemRead(itemId)
                                        selectedItemId = itemId
                                    }
                                )
                            }
                        }
                    )
                } else {
                    LazyColumn(
                        modifier = Modifier
                            .fillMaxSize()
                            .wrapContentWidth(Alignment.CenterHorizontally)
                            .widthIn(max = 720.dp),
                        contentPadding = PaddingValues(
                            start = 16.dp,
                            end = 16.dp,
                            top = 8.dp,
                            bottom = 28.dp
                        ),
                        verticalArrangement = Arrangement.spacedBy(20.dp)
                    ) {
                        item(key = "header") {
                            Column(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(horizontal = 4.dp),
                                verticalArrangement = Arrangement.spacedBy(4.dp)
                            ) {
                                Text(
                                    text = stringResource(R.string.whats_new_title),
                                    style = MaterialTheme.typography.headlineLargeEmphasized,
                                    color = MaterialTheme.colorScheme.onSurface
                                )
                                Text(
                                    text = stringResource(R.string.whats_new_subtitle),
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }

                        whatsNewReleaseAndFooterItems(
                            readItemIds = readItemIds,
                            context = context,
                            onItemClick = { itemId ->
                                onMarkItemRead(itemId)
                                selectedItemId = itemId
                            }
                        )
                    }
                }
            }
        }
    }

    selectedItem?.let { (release, item) ->
        WhatsNewDetailBottomSheet(
            release = release,
            item = item,
            onDismiss = { selectedItemId = null }
        )
    }
}

private fun LazyListScope.whatsNewReleaseAndFooterItems(
    readItemIds: Set<String>,
    context: Context,
    onItemClick: (String) -> Unit
) {
    WhatsNewCatalog.releases.forEachIndexed { releaseIndex, release ->
        val releaseHasUnread = release.items.any {
            WhatsNewCatalog.isItemUnread(it.id, readItemIds)
        }
        val showNewBadge = releaseIndex == 0 && releaseHasUnread

        item(key = "release_${release.version}") {
            val motionScheme = MaterialTheme.motionScheme
            Column(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 4.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Text(
                        text = release.headerLabel,
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.primary
                    )

                    AnimatedVisibility(
                        visible = showNewBadge,
                        enter = fadeIn(motionScheme.fastEffectsSpec()) +
                            scaleIn(motionScheme.fastSpatialSpec()),
                        exit = fadeOut(motionScheme.fastEffectsSpec()) +
                            scaleOut(motionScheme.fastSpatialSpec())
                    ) {
                        Surface(
                            shape = RoundedCornerShape(100.dp),
                            color = MaterialTheme.colorScheme.primaryContainer
                        ) {
                            Text(
                                text = stringResource(R.string.whats_new_badge_new),
                                style = MaterialTheme.typography.labelSmall,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.onPrimaryContainer,
                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp)
                            )
                        }
                    }
                }

                Column(
                    modifier = Modifier.fillMaxWidth(),
                    verticalArrangement = Arrangement.spacedBy(3.dp)
                ) {
                    release.items.forEachIndexed { index, item ->
                        WhatsNewItemRow(
                            item = item,
                            position = groupPositionOf(index, release.items.size),
                            isUnread = WhatsNewCatalog.isItemUnread(item.id, readItemIds),
                            onClick = { onItemClick(item.id) }
                        )
                    }
                }
            }
        }
    }

    item(key = "footer") {
        ExpressiveTonalButton(
            onClick = { openReleasesUrl(context) },
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 4.dp)
        ) {
            Icon(
                imageVector = Icons.AutoMirrored.Rounded.OpenInNew,
                contentDescription = null,
                modifier = Modifier.size(18.dp)
            )
            Spacer(modifier = Modifier.width(8.dp))
            Text(
                text = stringResource(R.string.whats_new_view_github),
                style = MaterialTheme.typography.labelLarge,
                fontWeight = FontWeight.SemiBold
            )
        }
    }
}

@Composable
private fun WhatsNewItemRow(
    item: WhatsNewItem,
    position: GroupPosition,
    isUnread: Boolean,
    onClick: () -> Unit
) {
    val interactionSource = remember { MutableInteractionSource() }
    val coroutineScope = rememberCoroutineScope()
    val isGesturePressed by rememberExpressivePressed(interactionSource)
    var clickPulse by remember { mutableStateOf(false) }

    val shape = animatedGroupedCardShape(
        position = position,
        pressed = isGesturePressed || clickPulse
    )

    val hapticClick = rememberHapticClick(HapticFeedbackType.TextHandleMove) {
        coroutineScope.launch {
            clickPulse = true
            delay(180L)
            clickPulse = false
        }
        onClick()
    }

    val motionScheme = MaterialTheme.motionScheme
    val (badgeContainerColor, badgeContentColor) = whatsNewAccentColors(item.accent)

    Surface(
        onClick = hapticClick,
        modifier = Modifier.fillMaxWidth(),
        shape = shape,
        color = MaterialTheme.colorScheme.surfaceContainerLow,
        interactionSource = interactionSource
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 14.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            Surface(
                modifier = Modifier.size(44.dp),
                shape = RoundedCornerShape(14.dp),
                color = badgeContainerColor
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(
                        imageVector = item.icon,
                        contentDescription = null,
                        tint = badgeContentColor,
                        modifier = Modifier.size(22.dp)
                    )
                }
            }

            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(2.dp)
            ) {
                Text(
                    text = item.title,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.onSurface
                )
                Text(
                    text = item.summary,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )
            }

            AnimatedVisibility(
                visible = isUnread,
                enter = fadeIn(motionScheme.fastEffectsSpec()) +
                    scaleIn(motionScheme.fastSpatialSpec()),
                exit = fadeOut(motionScheme.fastEffectsSpec()) +
                    scaleOut(motionScheme.fastSpatialSpec())
            ) {
                Box(
                    modifier = Modifier
                        .size(8.dp)
                        .clip(CircleShape)
                        .background(MaterialTheme.colorScheme.primary)
                )
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun WhatsNewDetailBottomSheet(
    release: WhatsNewRelease,
    item: WhatsNewItem,
    onDismiss: () -> Unit
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val foldPosture = LocalFoldPosture.current
    val screenHeightDp = LocalConfiguration.current.screenHeightDp.dp
    val (badgeContainerColor, badgeContentColor) = whatsNewAccentColors(item.accent)

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        shape = RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp),
        containerColor = MaterialTheme.colorScheme.surfaceContainerLow
    ) {
        DialogBlurEffect()
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .then(
                    if (foldPosture.isTabletop) {
                        Modifier.heightIn(max = foldPosture.tabletopBottomSheetMaxHeight(screenHeightDp))
                    } else {
                        Modifier
                    }
                )
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 24.dp)
                .padding(top = 8.dp, bottom = 28.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Surface(
                modifier = Modifier.size(72.dp),
                shape = MaterialShapes.Cookie9Sided.toShape(),
                color = badgeContainerColor
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(
                        imageVector = item.icon,
                        contentDescription = null,
                        tint = badgeContentColor,
                        modifier = Modifier.size(34.dp)
                    )
                }
            }

            Spacer(modifier = Modifier.height(14.dp))

            Surface(
                shape = RoundedCornerShape(100.dp),
                color = MaterialTheme.colorScheme.surfaceContainerHighest
            ) {
                Text(
                    text = release.headerLabel,
                    style = MaterialTheme.typography.labelMedium,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 4.dp)
                )
            }

            Spacer(modifier = Modifier.height(12.dp))

            Text(
                text = item.title,
                style = MaterialTheme.typography.headlineSmallEmphasized,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurface,
                textAlign = TextAlign.Center
            )

            Spacer(modifier = Modifier.height(16.dp))

            Column(
                modifier = Modifier.fillMaxWidth()
            ) {
                Text(
                    text = item.detail,
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.fillMaxWidth()
                )
            }

            Spacer(modifier = Modifier.height(24.dp))

            ExpressiveButton(
                onClick = onDismiss,
                modifier = Modifier.fillMaxWidth()
            ) {
                Text(
                    text = stringResource(R.string.whats_new_close),
                    style = MaterialTheme.typography.labelLarge,
                    fontWeight = FontWeight.SemiBold
                )
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PostUpdateWhatsNewSheet(
    release: WhatsNewRelease,
    onSeeAll: () -> Unit,
    onDismiss: () -> Unit
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val foldPosture = LocalFoldPosture.current
    val screenHeightDp = LocalConfiguration.current.screenHeightDp.dp

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        shape = RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp),
        containerColor = MaterialTheme.colorScheme.surfaceContainerLow
    ) {
        DialogBlurEffect()
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .then(
                    if (foldPosture.isTabletop) {
                        Modifier.heightIn(max = foldPosture.tabletopBottomSheetMaxHeight(screenHeightDp))
                    } else {
                        Modifier
                    }
                )
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 24.dp)
                .padding(top = 8.dp, bottom = 24.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Surface(
                modifier = Modifier.size(64.dp),
                shape = MaterialShapes.SoftBurst.toShape(),
                color = MaterialTheme.colorScheme.primaryContainer
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(
                        imageVector = Icons.Rounded.AutoAwesome,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onPrimaryContainer,
                        modifier = Modifier.size(30.dp)
                    )
                }
            }

            Spacer(modifier = Modifier.height(14.dp))

            Text(
                text = stringResource(R.string.whats_new_sheet_title, release.headerLabel),
                style = MaterialTheme.typography.headlineSmallEmphasized,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurface,
                textAlign = TextAlign.Center
            )

            Spacer(modifier = Modifier.height(4.dp))

            Text(
                text = stringResource(R.string.whats_new_sheet_subtitle),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center
            )

            Spacer(modifier = Modifier.height(18.dp))

            Column(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(3.dp)
            ) {
                release.items.forEachIndexed { index, item ->
                    val (badgeContainerColor, badgeContentColor) = whatsNewAccentColors(item.accent)
                    Surface(
                        modifier = Modifier.fillMaxWidth(),
                        shape = groupedCardShape(groupPositionOf(index, release.items.size)),
                        color = MaterialTheme.colorScheme.surfaceContainer
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 14.dp, vertical = 12.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            Surface(
                                modifier = Modifier.size(40.dp),
                                shape = RoundedCornerShape(12.dp),
                                color = badgeContainerColor
                            ) {
                                Box(contentAlignment = Alignment.Center) {
                                    Icon(
                                        imageVector = item.icon,
                                        contentDescription = null,
                                        tint = badgeContentColor,
                                        modifier = Modifier.size(20.dp)
                                    )
                                }
                            }

                            Column(
                                modifier = Modifier.weight(1f),
                                verticalArrangement = Arrangement.spacedBy(2.dp)
                            ) {
                                Text(
                                    text = item.title,
                                    style = MaterialTheme.typography.titleSmall,
                                    fontWeight = FontWeight.SemiBold,
                                    color = MaterialTheme.colorScheme.onSurface
                                )
                                Text(
                                    text = item.summary,
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(20.dp))

            ExpressiveButton(
                onClick = onDismiss,
                modifier = Modifier.fillMaxWidth()
            ) {
                Text(
                    text = stringResource(R.string.whats_new_close),
                    style = MaterialTheme.typography.labelLarge,
                    fontWeight = FontWeight.SemiBold
                )
            }

            Spacer(modifier = Modifier.height(4.dp))

            ExpressiveTextButton(
                onClick = onSeeAll
            ) {
                Text(
                    text = stringResource(R.string.whats_new_see_all),
                    style = MaterialTheme.typography.labelLarge,
                    fontWeight = FontWeight.SemiBold
                )
            }
        }
    }
}

@Composable
private fun whatsNewAccentColors(accent: WhatsNewAccent): Pair<Color, Color> = when (accent) {
    WhatsNewAccent.PRIMARY ->
        MaterialTheme.colorScheme.primaryContainer to MaterialTheme.colorScheme.onPrimaryContainer
    WhatsNewAccent.SECONDARY ->
        MaterialTheme.colorScheme.secondaryContainer to MaterialTheme.colorScheme.onSecondaryContainer
    WhatsNewAccent.TERTIARY ->
        MaterialTheme.colorScheme.tertiaryContainer to MaterialTheme.colorScheme.onTertiaryContainer
    WhatsNewAccent.ERROR_SOFT ->
        MaterialTheme.colorScheme.errorContainer to MaterialTheme.colorScheme.onErrorContainer
}

private fun openReleasesUrl(context: Context) {
    try {
        val intent = Intent(Intent.ACTION_VIEW, Uri.parse(GITHUB_RELEASES_URL))
        context.startActivity(intent)
    } catch (_: Exception) {
        Toast.makeText(
            context,
            context.getString(R.string.toast_cannot_open_browser),
            Toast.LENGTH_SHORT
        ).show()
    }
}
