package com.example.ui

import androidx.compose.animation.Crossfade
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.VolumeOff
import androidx.compose.material.icons.automirrored.filled.VolumeUp
import androidx.compose.material.icons.filled.EmojiEvents
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Storefront
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.ui.AbsoluteAlignment
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import com.example.R
import com.example.game.CreatureSkin
import com.example.game.GameUiState
import com.example.ui.theme.CrimsonInk
import com.example.ui.theme.InkBlack
import com.example.ui.theme.InkCharcoal
import com.example.ui.theme.InkMist
import com.example.ui.theme.InkSketch
import com.example.ui.theme.ParchmentSurface
import kotlin.math.cos
import kotlin.math.sin

@Composable
fun MainMenuScreen(
    state: GameUiState,
    onPlayClick: () -> Unit,
    onOpenShop: () -> Unit,
    onOpenSoundSettings: () -> Unit,
    onQuickMuteToggle: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val infiniteTransition = rememberInfiniteTransition(label = "menu_atmosphere")
    val pulse by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = 6.28318f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 6000),
            repeatMode = RepeatMode.Restart,
        ),
        label = "mote_phase",
    )

    val menuBg = Color(state.equippedSkin.menuBgColorHex)

    BoxWithConstraints(
        modifier = modifier
            .fillMaxSize()
            .background(menuBg),
    ) {
        val isWide = maxWidth >= 600.dp

        if (isWide) {
            Row(
                modifier = Modifier
                    .fillMaxSize()
                    .systemBarsPadding(),
            ) {
                Box(
                    modifier = Modifier
                        .weight(0.48f)
                        .fillMaxHeight()
                        .padding(horizontal = 28.dp, vertical = 20.dp),
                    contentAlignment = Alignment.Center,
                ) {
                    MenuControlPanel(
                        state = state,
                        onPlayClick = onPlayClick,
                        onOpenShop = onOpenShop,
                        onOpenSoundSettings = onOpenSoundSettings,
                        onQuickMuteToggle = onQuickMuteToggle,
                        compact = false,
                    )
                }

                Box(
                    modifier = Modifier
                        .weight(0.52f)
                        .fillMaxHeight(),
                    contentAlignment = AbsoluteAlignment.BottomRight,
                ) {
                    MenuArtworkLayer(
                        equippedSkin = state.equippedSkin,
                        bgColor = menuBg,
                        pulse = pulse,
                        contentScale = ContentScale.Fit,
                        alignment = AbsoluteAlignment.BottomRight,
                        modifier = Modifier.fillMaxSize(),
                    )
                }
            }
        } else {
            Box(modifier = Modifier.fillMaxSize()) {
                MenuArtworkLayer(
                    equippedSkin = state.equippedSkin,
                    bgColor = menuBg,
                    pulse = pulse,
                    contentScale = ContentScale.Fit,
                    alignment = AbsoluteAlignment.BottomRight,
                    modifier = Modifier
                        .fillMaxWidth()
                        .fillMaxHeight(0.75f)
                        .align(AbsoluteAlignment.BottomRight),
                )

                // Soft vertical parchment blend at top edge of illustration
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(225.dp)
                        .align(AbsoluteAlignment.TopLeft)
                        .background(
                            Brush.verticalGradient(
                                colors = listOf(
                                    menuBg,
                                    menuBg.copy(alpha = 0.86f),
                                    Color.Transparent,
                                )
                            )
                        )
                )

                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .systemBarsPadding()
                        .padding(horizontal = 20.dp, vertical = 14.dp),
                    contentAlignment = AbsoluteAlignment.TopLeft,
                ) {
                    MenuControlPanel(
                        state = state,
                        onPlayClick = onPlayClick,
                        onOpenShop = onOpenShop,
                        onOpenSoundSettings = onOpenSoundSettings,
                        onQuickMuteToggle = onQuickMuteToggle,
                        compact = true,
                    )
                }
            }
        }
    }
}

@Composable
private fun MenuArtworkLayer(
    equippedSkin: CreatureSkin,
    bgColor: Color,
    pulse: Float,
    contentScale: ContentScale,
    alignment: Alignment,
    modifier: Modifier = Modifier,
) {
    Box(modifier = modifier) {
        Crossfade(
            targetState = equippedSkin,
            animationSpec = tween(durationMillis = 380),
            label = "menu_skin_crossfade",
        ) { skin ->
            Image(
                painter = painterResource(id = skin.menuDrawableRes),
                contentDescription = stringResource(R.string.cd_menu_artwork),
                contentScale = contentScale,
                alignment = alignment,
                modifier = Modifier.fillMaxSize(),
            )
        }

        // Feather-soft edge blend into menuBg
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(34.dp)
                .align(Alignment.TopCenter)
                .background(
                    Brush.verticalGradient(
                        colors = listOf(bgColor, Color.Transparent)
                    )
                )
        )

        // Subtle atmospheric drifting ink motes
        Canvas(modifier = Modifier.fillMaxSize()) {
            for (i in 0..14) {
                val baseX = ((i * 97) % 100) / 100f * size.width
                val baseY = ((i * 53) % 100) / 100f * size.height
                val dx = sin(pulse + i * 0.9f) * 14f
                val dy = cos(pulse * 0.7f + i * 1.1f) * 18f
                drawCircle(
                    color = InkCharcoal.copy(alpha = 0.14f + (i % 3) * 0.06f),
                    radius = (1.8f + (i % 3) * 0.9f),
                    center = Offset(baseX + dx, baseY + dy),
                )
            }
        }
    }
}

@Composable
private fun MenuControlPanel(
    state: GameUiState,
    onPlayClick: () -> Unit,
    onOpenShop: () -> Unit,
    onOpenSoundSettings: () -> Unit,
    onQuickMuteToggle: () -> Unit,
    compact: Boolean,
) {
    val isMuted = !state.sfxEnabled && !state.ambientEnabled

    CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Rtl) {
        Column(
            modifier = Modifier
                .widthIn(max = if (compact) 256.dp else 360.dp)
                .fillMaxWidth(),
            horizontalAlignment = Alignment.Start,
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column {
                    Text(
                        text = stringResource(R.string.app_name),
                        style = if (compact) MaterialTheme.typography.displaySmall else MaterialTheme.typography.displayLarge,
                        color = InkBlack,
                    )
                    Text(
                        text = stringResource(state.equippedSkin.nameRes),
                        style = MaterialTheme.typography.bodyLarge,
                        color = InkSketch,
                    )
                }
                CoinBalanceChip(
                    coins = state.totalCoins,
                    modifier = Modifier.testTag("menu_coins_chip"),
                )
            }

            Spacer(modifier = Modifier.height(if (compact) 10.dp else 16.dp))

            // High Score & Equipped HP Parchment Card
            InkFrameCard(
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("high_score_card"),
                backgroundColor = ParchmentSurface.copy(alpha = 0.90f),
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween,
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            imageVector = Icons.Default.EmojiEvents,
                            contentDescription = null,
                            tint = if (state.bestScore > 0) CrimsonInk else InkSketch,
                            modifier = Modifier.size(20.dp),
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            text = stringResource(R.string.high_score_label),
                            style = MaterialTheme.typography.labelLarge,
                            color = InkCharcoal,
                        )
                    }
                    Text(
                        text = state.bestScore.toString(),
                        style = MaterialTheme.typography.headlineMedium.copy(fontWeight = FontWeight.Bold),
                        color = if (state.bestScore > 0) CrimsonInk else InkBlack,
                        modifier = Modifier.testTag("best_score_value"),
                    )
                }

                Spacer(modifier = Modifier.height(5.dp))
                InkDivider()
                Spacer(modifier = Modifier.height(5.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        repeat(state.equippedSkin.maxHp) {
                            Icon(
                                imageVector = Icons.Default.Favorite,
                                contentDescription = null,
                                tint = CrimsonInk,
                                modifier = Modifier.size(13.dp),
                            )
                        }
                        Spacer(modifier = Modifier.width(4.dp))
                        Text(
                            text = stringResource(R.string.hp_label, state.equippedSkin.maxHp),
                            style = MaterialTheme.typography.labelMedium,
                            color = InkMist,
                        )
                    }
                    Text(
                        text = "${stringResource(R.string.best_distance_label)}: ${state.bestDistance}m",
                        style = MaterialTheme.typography.labelMedium,
                        color = InkMist,
                    )
                }
            }

            Spacer(modifier = Modifier.height(if (compact) 10.dp else 14.dp))

            // Primary Play Button
            InkPrimaryButton(
                text = stringResource(R.string.play_button),
                icon = Icons.Default.PlayArrow,
                onClick = onPlayClick,
                testTag = "play_button",
                filled = true,
                modifier = Modifier.fillMaxWidth(),
            )

            Spacer(modifier = Modifier.height(8.dp))

            // Shop & Upgrades Button
            InkPrimaryButton(
                text = stringResource(R.string.shop_button),
                icon = Icons.Default.Storefront,
                onClick = onOpenShop,
                testTag = "shop_button",
                filled = false,
                modifier = Modifier.fillMaxWidth(),
            )

            Spacer(modifier = Modifier.height(8.dp))

            // Sound Settings Row
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                InkPrimaryButton(
                    text = stringResource(R.string.sound_settings),
                    icon = Icons.Default.Tune,
                    onClick = onOpenSoundSettings,
                    testTag = "sound_settings_button",
                    filled = false,
                    modifier = Modifier.weight(1f),
                )

                InkIconButton(
                    icon = if (isMuted) Icons.AutoMirrored.Filled.VolumeOff else Icons.AutoMirrored.Filled.VolumeUp,
                    contentDescription = stringResource(R.string.cd_mute_toggle),
                    onClick = onQuickMuteToggle,
                    testTag = "quick_mute_button",
                )
            }
        }
    }
}
