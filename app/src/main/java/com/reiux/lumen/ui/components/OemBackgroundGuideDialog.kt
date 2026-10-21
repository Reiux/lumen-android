package com.reiux.lumen.ui.components

import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.OpenInNew
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.Info
import androidx.compose.material.icons.rounded.PhoneAndroid
import androidx.compose.material.icons.rounded.Settings
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.reiux.lumen.util.OemIntentHelper

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun OemBackgroundGuideDialog(
    onDismiss: () -> Unit
) {
    val context = LocalContext.current
    val detectedBrand = remember { OemIntentHelper.detectCurrentBrand() }
    var selectedBrand by remember { mutableStateOf(detectedBrand) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    imageVector = Icons.Rounded.PhoneAndroid,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(26.dp)
                )
                Spacer(modifier = Modifier.width(10.dp))
                Text(
                    text = "Keep App Running in Background",
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold
                )
            }
        },
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState())
            ) {
                Text(
                    text = "Many phone brands (such as Xiaomi, Samsung, and OnePlus) kill background tasks when apps are swiped away from Recents unless battery restrictions are turned off.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )

                Spacer(modifier = Modifier.height(14.dp))

                // Selectable Brand Chips
                Text(
                    text = "Select Device Brand",
                    style = MaterialTheme.typography.labelMedium,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.primary
                )
                Spacer(modifier = Modifier.height(6.dp))

                FlowRow(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    OemIntentHelper.Brand.values().forEach { brand ->
                        FilterChip(
                            selected = selectedBrand == brand,
                            onClick = { selectedBrand = brand },
                            label = {
                                Text(
                                    text = if (brand == detectedBrand) "${brand.displayName} (Detected)" else brand.displayName,
                                    fontSize = 12.sp,
                                    fontWeight = if (brand == detectedBrand) FontWeight.Bold else FontWeight.Normal
                                )
                            }
                        )
                    }
                }

                Spacer(modifier = Modifier.height(16.dp))

                // Brand Specific Instruction Card
                Surface(
                    shape = RoundedCornerShape(16.dp),
                    color = MaterialTheme.colorScheme.surfaceContainerHighest,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(modifier = Modifier.padding(14.dp)) {
                        Text(
                            text = "Instructions for ${selectedBrand.displayName}",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.primary
                        )
                        Spacer(modifier = Modifier.height(10.dp))

                        val steps = getInstructionsForBrand(selectedBrand)
                        steps.forEachIndexed { index, step ->
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(vertical = 4.dp),
                                verticalAlignment = Alignment.Top
                            ) {
                                Surface(
                                    shape = RoundedCornerShape(8.dp),
                                    color = MaterialTheme.colorScheme.primaryContainer,
                                    modifier = Modifier.size(22.dp)
                                ) {
                                    Box(contentAlignment = Alignment.Center) {
                                        Text(
                                            text = "${index + 1}",
                                            style = MaterialTheme.typography.labelSmall,
                                            fontWeight = FontWeight.Bold,
                                            color = MaterialTheme.colorScheme.onPrimaryContainer
                                        )
                                    }
                                }
                                Spacer(modifier = Modifier.width(10.dp))
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        text = step.first,
                                        style = MaterialTheme.typography.bodyMedium,
                                        fontWeight = FontWeight.SemiBold,
                                        color = MaterialTheme.colorScheme.onSurface
                                    )
                                    Text(
                                        text = step.second,
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                            }
                        }
                    }
                }

                Spacer(modifier = Modifier.height(14.dp))

                ExpressiveTonalButton(
                    onClick = { OemIntentHelper.openAutoStartSettings(context) },
                    modifier = Modifier.fillMaxWidth(),
                    contentPadding = PaddingValues(horizontal = 16.dp, vertical = 10.dp)
                ) {
                    Icon(Icons.Rounded.Settings, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("Open Device Settings / App Info")
                }

                Spacer(modifier = Modifier.height(8.dp))

                TextButton(
                    onClick = {
                        val browserIntent = Intent(Intent.ACTION_VIEW, Uri.parse("https://dontkillmyapp.com/"))
                        context.startActivity(browserIntent)
                    },
                    modifier = Modifier.align(Alignment.CenterHorizontally)
                ) {
                    Icon(Icons.AutoMirrored.Rounded.OpenInNew, contentDescription = null, modifier = Modifier.size(16.dp))
                    Spacer(modifier = Modifier.width(6.dp))
                    Text("View full guide on DontKillMyApp.com", fontSize = 12.sp)
                }
            }
        },
        confirmButton = {
            ExpressiveButton(
                onClick = onDismiss,
                contentPadding = PaddingValues(horizontal = 20.dp, vertical = 10.dp)
            ) {
                Text("Got It")
            }
        },
        shape = RoundedCornerShape(28.dp),
        containerColor = MaterialTheme.colorScheme.surfaceContainerHigh
    )
}

private fun getInstructionsForBrand(brand: OemIntentHelper.Brand): List<Pair<String, String>> {
    return when (brand) {
        OemIntentHelper.Brand.XIAOMI -> listOf(
            "Enable Autostart" to "Open App Info > Permissions > Autostart (or Security app > Autostart) and turn ON for Lumen.",
            "Set Battery Saver to No Restrictions" to "Open App Info > Battery Saver > Select 'No restrictions' instead of MIUI Battery Saver.",
            "Lock in Recents" to "Open Recent Apps, long press the Lumen window, and tap the Padlock icon so cleaner tasks won't kill it."
        )
        OemIntentHelper.Brand.SAMSUNG -> listOf(
            "Set Battery to Unrestricted" to "Open Settings > Apps > Lumen > Battery > Choose 'Unrestricted' instead of Optimized.",
            "Add to Never Sleeping Apps" to "Open Device Care > Battery > Background usage limits > 'Never sleeping apps' > Tap '+' and add Lumen."
        )
        OemIntentHelper.Brand.ONEPLUS, OemIntentHelper.Brand.OPPO_REALME -> listOf(
            "Allow Background & Auto-launch" to "Open App Info > Battery usage > Turn ON 'Allow background activity' and 'Allow auto-launch'.",
            "Turn Off Battery Optimization" to "Open Settings > Battery > More settings > Optimize battery use > Select Lumen > 'Don't optimize'.",
            "Lock in Recents" to "Open Recent Apps, tap the three dots on Lumen card, and select 'Lock'."
        )
        OemIntentHelper.Brand.HUAWEI_HONOR -> listOf(
            "Configure App Launch" to "Open Settings > Battery > App launch > Find Lumen > Turn off 'Manage automatically'.",
            "Enable Manual Switches" to "Toggle ON 'Auto-launch', 'Secondary launch', and 'Run in background'."
        )
        OemIntentHelper.Brand.VIVO -> listOf(
            "Enable Autostart" to "Open Settings > More settings > Applications > Autostart > Turn ON for Lumen.",
            "High Background Power" to "Open Settings > Battery > High background power consumption > Allow Lumen."
        )
        OemIntentHelper.Brand.ASUS -> listOf(
            "Auto-start Manager" to "Open Settings > Battery > PowerMaster (or Mobile Manager) > Auto-start manager > Allow Lumen."
        )
        OemIntentHelper.Brand.PIXEL_AOSP -> listOf(
            "App Battery Usage" to "Open Settings > Apps > Lumen > App battery usage > Select 'Unrestricted'.",
            "Standard AOSP Optimization" to "Pixel UI and stock Android follow Google standards and run background sync reliably once battery optimization is allowed."
        )
    }
}
