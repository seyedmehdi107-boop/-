package com.example.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.VolumeOff
import androidx.compose.material.icons.automirrored.filled.VolumeUp
import androidx.compose.material.icons.filled.Air
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.GraphicEq
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.material3.ripple
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import com.example.R
import com.example.ui.theme.CrimsonInk
import com.example.ui.theme.InkBlack
import com.example.ui.theme.InkCharcoal
import com.example.ui.theme.InkMist
import com.example.ui.theme.InkPale
import com.example.ui.theme.InkSketch
import com.example.ui.theme.ParchmentPaper
import com.example.ui.theme.ParchmentSurface
import com.example.ui.theme.ParchmentWarm

@Composable
fun InkFrameCard(
    modifier: Modifier = Modifier,
    backgroundColor: Color = ParchmentSurface.copy(alpha = 0.94f),
    accentCrimson: Boolean = false,
    content: @Composable ColumnScope.() -> Unit,
) {
    val borderCol = if (accentCrimson) CrimsonInk else InkBlack
    Column(
        modifier = modifier
            .background(color = backgroundColor, shape = RoundedCornerShape(6.dp))
            .drawBehind {
                val strokeMain = 2.2.dp.toPx()
                val strokeThin = 1.dp.toPx()
                val inset = 4.dp.toPx()
                // Outer hand-inked border
                drawRoundRect(
                    color = borderCol,
                    topLeft = Offset.Zero,
                    size = size,
                    cornerRadius = CornerRadius(6.dp.toPx()),
                    style = Stroke(width = strokeMain),
                )
                // Inner offset sketch border
                drawRoundRect(
                    color = borderCol.copy(alpha = 0.45f),
                    topLeft = Offset(inset, inset),
                    size = Size(size.width - inset * 2f, size.height - inset * 2f),
                    cornerRadius = CornerRadius(3.dp.toPx()),
                    style = Stroke(width = strokeThin),
                )
                // Subtle diagonal corner hatch marks
                val h = 10.dp.toPx()
                drawLine(borderCol.copy(alpha = 0.35f), Offset(0f, h), Offset(h, 0f), strokeWidth = strokeThin)
                drawLine(
                    borderCol.copy(alpha = 0.35f),
                    Offset(size.width - h, size.height),
                    Offset(size.width, size.height - h),
                    strokeWidth = strokeThin,
                )
            }
            .padding(horizontal = 16.dp, vertical = 14.dp),
        content = content,
    )
}

@Composable
fun InkPrimaryButton(
    text: String,
    icon: ImageVector,
    onClick: () -> Unit,
    testTag: String,
    modifier: Modifier = Modifier,
    filled: Boolean = true,
) {
    val bgColor = if (filled) InkBlack else ParchmentSurface.copy(alpha = 0.92f)
    val contentColor = if (filled) ParchmentSurface else InkBlack
    val interactionSource = remember { MutableInteractionSource() }

    Box(
        modifier = modifier
            .minimumInteractiveComponentSize()
            .clip(RoundedCornerShape(6.dp))
            .background(bgColor)
            .drawBehind {
                val inset = 3.5.dp.toPx()
                drawRoundRect(
                    color = if (filled) ParchmentPaper.copy(alpha = 0.32f) else InkBlack,
                    topLeft = Offset(inset, inset),
                    size = Size(size.width - inset * 2f, size.height - inset * 2f),
                    cornerRadius = CornerRadius(4.dp.toPx()),
                    style = Stroke(width = if (filled) 1.2.dp.toPx() else 2.dp.toPx()),
                )
                if (!filled) {
                    drawRoundRect(
                        color = InkCharcoal.copy(alpha = 0.45f),
                        topLeft = Offset.Zero,
                        size = size,
                        cornerRadius = CornerRadius(6.dp.toPx()),
                        style = Stroke(width = 1.dp.toPx()),
                    )
                }
            }
            .clickable(
                interactionSource = interactionSource,
                indication = ripple(color = if (filled) ParchmentPaper else InkBlack),
                onClick = onClick,
            )
            .padding(horizontal = 18.dp, vertical = 12.dp)
            .testTag(testTag),
        contentAlignment = Alignment.Center,
    ) {
        CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Rtl) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.Center,
            ) {
                Icon(
                    imageVector = icon,
                    contentDescription = null,
                    tint = contentColor,
                    modifier = Modifier.size(22.dp),
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = text,
                    style = MaterialTheme.typography.labelLarge,
                    color = contentColor,
                )
            }
        }
    }
}

@Composable
fun InkIconButton(
    icon: ImageVector,
    contentDescription: String,
    onClick: () -> Unit,
    testTag: String,
    modifier: Modifier = Modifier,
    tint: Color = InkBlack,
) {
    IconButton(
        onClick = onClick,
        modifier = modifier
            .minimumInteractiveComponentSize()
            .size(48.dp)
            .background(ParchmentSurface.copy(alpha = 0.88f), RoundedCornerShape(6.dp))
            .drawBehind {
                drawRoundRect(
                    color = InkBlack,
                    topLeft = Offset.Zero,
                    size = size,
                    cornerRadius = CornerRadius(6.dp.toPx()),
                    style = Stroke(width = 1.8.dp.toPx()),
                )
            }
            .testTag(testTag),
    ) {
        Icon(
            imageVector = icon,
            contentDescription = contentDescription,
            tint = tint,
            modifier = Modifier.size(22.dp),
        )
    }
}

@Composable
fun InkDivider(modifier: Modifier = Modifier) {
    Box(
        modifier = modifier
            .fillMaxWidth()
            .height(8.dp)
            .drawBehind {
                val y = size.height * 0.5f
                val path = Path().apply {
                    moveTo(0f, y)
                    lineTo(size.width * 0.46f, y - 0.8f)
                    lineTo(size.width, y + 0.5f)
                }
                drawPath(
                    path = path,
                    color = InkSketch.copy(alpha = 0.42f),
                    style = Stroke(width = 1.4.dp.toPx(), cap = StrokeCap.Round),
                )
            }
    )
}

@Composable
fun SoundSettingsDialog(
    sfxEnabled: Boolean,
    ambientEnabled: Boolean,
    masterVolume: Float,
    onSfxChange: (Boolean) -> Unit,
    onAmbientChange: (Boolean) -> Unit,
    onVolumeChange: (Float) -> Unit,
    onDismiss: () -> Unit,
) {
    Dialog(onDismissRequest = onDismiss) {
        CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Rtl) {
            InkFrameCard(
                modifier = Modifier
                    .widthIn(max = 380.dp)
                    .fillMaxWidth()
                    .testTag("sound_settings_dialog"),
                backgroundColor = ParchmentSurface,
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween,
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            imageVector = if (sfxEnabled || ambientEnabled) {
                                Icons.AutoMirrored.Filled.VolumeUp
                            } else {
                                Icons.AutoMirrored.Filled.VolumeOff
                            },
                            contentDescription = null,
                            tint = InkBlack,
                            modifier = Modifier.size(24.dp),
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = stringResource(R.string.sound_settings),
                            style = MaterialTheme.typography.titleLarge,
                            color = InkBlack,
                        )
                    }
                    IconButton(
                        onClick = onDismiss,
                        modifier = Modifier
                            .minimumInteractiveComponentSize()
                            .testTag("close_sound_dialog_button"),
                    ) {
                        Icon(
                            imageVector = Icons.Default.Close,
                            contentDescription = stringResource(R.string.close_label),
                            tint = InkBlack,
                        )
                    }
                }

                Spacer(modifier = Modifier.height(8.dp))
                InkDivider()
                Spacer(modifier = Modifier.height(10.dp))

                // SFX Toggle
                SoundToggleRow(
                    icon = Icons.Default.GraphicEq,
                    title = stringResource(R.string.sfx_label),
                    subtitle = stringResource(R.string.sfx_description),
                    checked = sfxEnabled,
                    onCheckedChange = onSfxChange,
                    testTag = "sfx_switch",
                )

                Spacer(modifier = Modifier.height(12.dp))

                // Wind Ambience Toggle
                SoundToggleRow(
                    icon = Icons.Default.Air,
                    title = stringResource(R.string.ambient_label),
                    subtitle = stringResource(R.string.ambient_description),
                    checked = ambientEnabled,
                    onCheckedChange = onAmbientChange,
                    testTag = "ambient_switch",
                )

                Spacer(modifier = Modifier.height(16.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        text = stringResource(R.string.volume_label),
                        style = MaterialTheme.typography.titleMedium,
                        color = InkBlack,
                    )
                    Text(
                        text = "${(masterVolume * 100).toInt()}٪",
                        style = MaterialTheme.typography.labelLarge,
                        color = InkSketch,
                    )
                }

                Slider(
                    value = masterVolume,
                    onValueChange = onVolumeChange,
                    valueRange = 0f..1f,
                    colors = SliderDefaults.colors(
                        thumbColor = InkBlack,
                        activeTrackColor = InkCharcoal,
                        inactiveTrackColor = InkPale,
                    ),
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("master_volume_slider"),
                )

                Spacer(modifier = Modifier.height(12.dp))

                InkPrimaryButton(
                    text = stringResource(R.string.close_label),
                    icon = Icons.Default.Close,
                    onClick = onDismiss,
                    testTag = "sound_done_button",
                    modifier = Modifier.fillMaxWidth(),
                )
            }
        }
    }
}

@Composable
private fun SoundToggleRow(
    icon: ImageVector,
    title: String,
    subtitle: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    testTag: String,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(ParchmentWarm.copy(alpha = 0.5f), RoundedCornerShape(6.dp))
            .padding(horizontal = 12.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        Row(
            modifier = Modifier.weight(1f),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = InkCharcoal,
                modifier = Modifier.size(22.dp),
            )
            Spacer(modifier = Modifier.width(10.dp))
            Column {
                Text(
                    text = title,
                    style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                    color = InkBlack,
                )
                Text(
                    text = subtitle,
                    style = MaterialTheme.typography.bodyMedium,
                    color = InkMist,
                )
            }
        }
        Spacer(modifier = Modifier.width(8.dp))
        Switch(
            checked = checked,
            onCheckedChange = onCheckedChange,
            colors = SwitchDefaults.colors(
                checkedThumbColor = ParchmentSurface,
                checkedTrackColor = InkBlack,
                uncheckedThumbColor = InkCharcoal,
                uncheckedTrackColor = ParchmentWarm,
                uncheckedBorderColor = InkSketch,
            ),
            modifier = Modifier.testTag(testTag),
        )
    }
}
