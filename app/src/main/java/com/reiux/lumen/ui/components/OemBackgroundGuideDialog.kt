package com.reiux.lumen.ui.components

import android.content.Intent
import android.net.Uri
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
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
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.OpenInNew
import androidx.compose.material.icons.rounded.PhoneAndroid
import androidx.compose.material.icons.rounded.Settings
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.scale
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.DialogProperties
import com.reiux.lumen.R
import com.reiux.lumen.util.OemIntentHelper

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun OemBackgroundGuideDialog(
    onDismiss: () -> Unit
) {
    val context = LocalContext.current
    val detectedBrand = remember { OemIntentHelper.detectCurrentBrand() }
    var selectedBrand by remember { mutableStateOf(detectedBrand) }

    val configuration = LocalConfiguration.current
    val isWideLayout = configuration.orientation == android.content.res.Configuration.ORIENTATION_LANDSCAPE || configuration.screenWidthDp >= 600

    PredictiveAlertDialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false),
        modifier = Modifier
            .widthIn(min = 340.dp, max = if (isWideLayout) 750.dp else 520.dp)
            .padding(horizontal = 16.dp),
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
                    text = stringResource(R.string.oem_guide_dialog_title),
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
                    text = stringResource(R.string.oem_guide_dialog_desc),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )

                Spacer(modifier = Modifier.height(14.dp))

                // Selectable Brand Chips
                Text(
                    text = stringResource(R.string.oem_guide_select_brand),
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
                        ExpressiveFilterChip(
                            selected = selectedBrand == brand,
                            onClick = { selectedBrand = brand },
                            label = {
                                Text(
                                    text = if (brand == detectedBrand) stringResource(R.string.oem_guide_brand_detected, brand.displayName) else brand.displayName,
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
                            text = stringResource(R.string.oem_guide_brand_instructions, selectedBrand.displayName),
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.primary
                        )
                        Spacer(modifier = Modifier.height(10.dp))

                        val steps = getInstructionsForBrand(context, selectedBrand)
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
                    Text(stringResource(R.string.oem_guide_open_settings))
                }

                Spacer(modifier = Modifier.height(8.dp))

                TextButton(
                    onClick = {
                        try {
                            val browserIntent = Intent(Intent.ACTION_VIEW, Uri.parse("https://dontkillmyapp.com/"))
                            context.startActivity(browserIntent)
                        } catch (_: Exception) {
                            android.widget.Toast.makeText(
                                context,
                                context.getString(R.string.toast_cannot_open_browser),
                                android.widget.Toast.LENGTH_SHORT
                            ).show()
                        }
                    },
                    modifier = Modifier.align(Alignment.CenterHorizontally)
                ) {
                    Icon(Icons.AutoMirrored.Rounded.OpenInNew, contentDescription = null, modifier = Modifier.size(16.dp))
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(stringResource(R.string.oem_guide_view_full), fontSize = 12.sp)
                }
            }
        },
        confirmButton = {
            ExpressiveButton(
                onClick = onDismiss,
                contentPadding = PaddingValues(horizontal = 20.dp, vertical = 10.dp)
            ) {
                Text(stringResource(R.string.btn_got_it))
            }
        },
        shape = RoundedCornerShape(28.dp),
        containerColor = MaterialTheme.colorScheme.surfaceContainerHigh
    )
}

private fun getInstructionsForBrand(context: android.content.Context, brand: OemIntentHelper.Brand): List<Pair<String, String>> {
    return when (brand) {
        OemIntentHelper.Brand.XIAOMI -> listOf(
            context.getString(R.string.oem_step_xiaomi_1_title) to context.getString(R.string.oem_step_xiaomi_1_desc),
            context.getString(R.string.oem_step_xiaomi_2_title) to context.getString(R.string.oem_step_xiaomi_2_desc),
            context.getString(R.string.oem_step_xiaomi_3_title) to context.getString(R.string.oem_step_xiaomi_3_desc)
        )
        OemIntentHelper.Brand.SAMSUNG -> listOf(
            context.getString(R.string.oem_step_samsung_1_title) to context.getString(R.string.oem_step_samsung_1_desc),
            context.getString(R.string.oem_step_samsung_2_title) to context.getString(R.string.oem_step_samsung_2_desc)
        )
        OemIntentHelper.Brand.OPPO_REALME -> listOf(
            context.getString(R.string.oem_step_coloros_1_title) to context.getString(R.string.oem_step_coloros_1_desc),
            context.getString(R.string.oem_step_coloros_2_title) to context.getString(R.string.oem_step_coloros_2_desc),
            context.getString(R.string.oem_step_coloros_3_title) to context.getString(R.string.oem_step_coloros_3_desc)
        )
        OemIntentHelper.Brand.HUAWEI_HONOR -> listOf(
            context.getString(R.string.oem_step_magicos_1_title) to context.getString(R.string.oem_step_magicos_1_desc),
            context.getString(R.string.oem_step_magicos_2_title) to context.getString(R.string.oem_step_magicos_2_desc)
        )
        OemIntentHelper.Brand.VIVO -> listOf(
            context.getString(R.string.oem_step_originos_1_title) to context.getString(R.string.oem_step_originos_1_desc),
            context.getString(R.string.oem_step_originos_2_title) to context.getString(R.string.oem_step_originos_2_desc)
        )
        OemIntentHelper.Brand.ASUS -> listOf(
            context.getString(R.string.oem_step_asus_1_title) to context.getString(R.string.oem_step_asus_1_desc)
        )
        OemIntentHelper.Brand.PIXEL_AOSP -> listOf(
            context.getString(R.string.oem_step_aosp_1_title) to context.getString(R.string.oem_step_aosp_1_desc),
            context.getString(R.string.oem_step_aosp_2_title) to context.getString(R.string.oem_step_aosp_2_desc)
        )
    }
}
