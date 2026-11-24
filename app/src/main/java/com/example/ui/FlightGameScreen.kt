package com.example.ui

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.VolumeOff
import androidx.compose.material.icons.automirrored.filled.VolumeUp
import androidx.compose.material.icons.filled.Bolt
import androidx.compose.material.icons.filled.EmojiEvents
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.FavoriteBorder
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.MonetizationOn
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Replay
import androidx.compose.material.icons.filled.Storefront
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.State
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import com.example.R
import com.example.game.FlightWorldState
import com.example.game.GamePhase
import com.example.game.GameUiState
import com.example.game.GameViewModel
import com.example.game.InkFlightCanvas
import com.example.game.WeatherBiome
import com.example.ui.theme.CrimsonInk
import com.example.ui.theme.InkBlack
import com.example.ui.theme.InkCharcoal
import com.example.ui.theme.InkMist
import com.example.ui.theme.InkSketch
import com.example.ui.theme.ParchmentPaper
import com.example.ui.theme.ParchmentSurface
import kotlin.math.min

private val HudCoinGold = Color(0xFFC59432)
private val MagnetBlue = Color(0xFF31607A)

@Composable
fun FlightGameScreen(
    state: GameUiState,
    world: FlightWorldState,
    frameTick: State<Long>,
    onDragSteer: (dxNorm: Float, dyNorm: Float) -> Unit,
    onPause: () -> Unit,
    onResume: () -> Unit,
    onPlayAgain: () -> Unit,
    onReturnToMenu: () -> Unit,
    onQuickMuteToggle: () -> Unit,
    onOpenSoundSettings: () -> Unit,
    onOpenShop: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val isMuted = !state.sfxEnabled && !state.ambientEnabled

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(ParchmentPaper)
            .pointerInput(state.phase) {
                if (state.phase == GamePhase.PLAYING) {
                    detectDragGestures { change, dragAmount ->
                        change.consume()
                        val minDim = min(size.width, size.height).toFloat().coerceAtLeast(1f)
                        onDragSteer(dragAmount.x / (minDim * 0.44f), dragAmount.y / (minDim * 0.44f))
                    }
                }
            },
    ) {
        InkFlightCanvas(
            world = world,
            frameTick = frameTick,
            modifier = Modifier.fillMaxSize(),
        )

        if (state.phase == GamePhase.PLAYING || state.phase == GamePhase.LAUNCHING) {
            CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Rtl) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .systemBarsPadding()
                        .padding(horizontal = 16.dp, vertical = 12.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.Top,
                ) {
                    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        Column(
                            modifier = Modifier
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
                                .padding(horizontal = 12.dp, vertical = 8.dp),
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Text(
                                    text = "${stringResource(R.string.score_label)}: ",
                                    style = MaterialTheme.typography.labelMedium,
                                    color = InkSketch,
                                )
                                Text(
                                    text = state.score.toString(),
                                    style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Bold),
                                    color = InkBlack,
                                    modifier = Modifier.testTag("hud_score_value"),
                                )
                                Spacer(modifier = Modifier.width(10.dp))
                                Icon(
                                    imageVector = Icons.Default.MonetizationOn,
                                    contentDescription = null,
                                    tint = HudCoinGold,
                                    modifier = Modifier.size(16.dp),
                                )
                                Spacer(modifier = Modifier.width(3.dp))
                                Text(
                                    text = "+${state.runCoins}",
                                    style = MaterialTheme.typography.labelLarge,
                                    color = InkBlack,
                                )
                            }

                            Spacer(modifier = Modifier.height(3.dp))

                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(8.dp),
                            ) {
                                Row(horizontalArrangement = Arrangement.spacedBy(2.dp)) {
                                    for (i in 0 until state.maxHp) {
                                        Icon(
                                            imageVector = if (i < state.hp) Icons.Default.Favorite else Icons.Default.FavoriteBorder,
                                            contentDescription = null,
                                            tint = if (i < state.hp) CrimsonInk else InkMist,
                                            modifier = Modifier.size(15.dp),
                                        )
                                    }
                                }

                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Icon(
                                        imageVector = Icons.Default.EmojiEvents,
                                        contentDescription = null,
                                        tint = if (state.score > state.bestScore && state.score > 0) CrimsonInk else InkMist,
                                        modifier = Modifier.size(13.dp),
                                    )
                                    Spacer(modifier = Modifier.width(3.dp))
                                    Text(
                                        text = "${maxOf(state.bestScore, state.score)}",
                                        style = MaterialTheme.typography.labelMedium,
                                        color = if (state.score > state.bestScore && state.score > 0) CrimsonInk else InkMist,
                                    )
                                }

                                val weatherProgressText = when (state.weather) {
                                    WeatherBiome.CLEAR_MIST -> stringResource(
                                        R.string.distance_to_rain_label,
                                        state.distanceMeters,
                                        GameViewModel.RAIN_DISTANCE_THRESHOLD,
                                    )
                                    WeatherBiome.RAINY -> stringResource(
                                        R.string.distance_to_snow_label,
                                        state.distanceMeters,
                                        GameViewModel.SNOW_DISTANCE_THRESHOLD,
                                    )
                                    WeatherBiome.SNOWY -> stringResource(
                                        R.string.biome_snow_active,
                                        state.distanceMeters,
                                    )
                                }
                                Text(
                                    text = weatherProgressText,
                                    style = MaterialTheme.typography.labelSmall,
                                    color = InkSketch,
                                )
                            }
                        }

                        // Active Magnet Power-Up countdown pill
                        AnimatedVisibility(
                            visible = state.magnetSecondsLeft > 0,
                            enter = fadeIn() + scaleIn(),
                            exit = fadeOut(),
                        ) {
                            Row(
                                modifier = Modifier
                                    .background(MagnetBlue, RoundedCornerShape(6.dp))
                                    .padding(horizontal = 10.dp, vertical = 4.dp),
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                Icon(Icons.Default.Bolt, contentDescription = null, tint = ParchmentSurface, modifier = Modifier.size(15.dp))
                                Spacer(Modifier.width(4.dp))
                                Text(
                                    text = stringResource(R.string.magnet_active_label, state.magnetSecondsLeft),
                                    style = MaterialTheme.typography.labelMedium,
                                    color = ParchmentSurface,
                                )
                            }
                        }
                    }

                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        InkIconButton(
                            icon = if (isMuted) Icons.AutoMirrored.Filled.VolumeOff else Icons.AutoMirrored.Filled.VolumeUp,
                            contentDescription = stringResource(R.string.cd_mute_toggle),
                            onClick = onQuickMuteToggle,
                            testTag = "game_mute_button",
                        )
                        if (state.phase == GamePhase.PLAYING) {
                            InkIconButton(
                                icon = Icons.Default.Pause,
                                contentDescription = stringResource(R.string.cd_pause_button),
                                onClick = onPause,
                                testTag = "pause_button",
                            )
                        }
                    }
                }
            }

            // Weather shift, body-hit, and near-miss banners
            Column(
                modifier = Modifier
                    .align(Alignment.TopCenter)
                    .systemBarsPadding()
                    .padding(top = 96.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                AnimatedVisibility(
                    visible = state.weatherBannerText != null,
                    enter = fadeIn() + scaleIn(),
                    exit = fadeOut(),
                ) {
                    Box(
                        modifier = Modifier
                            .background(InkCharcoal, RoundedCornerShape(4.dp))
                            .padding(horizontal = 14.dp, vertical = 5.dp),
                    ) {
                        Text(
                            text = state.weatherBannerText ?: "",
                            style = MaterialTheme.typography.labelLarge,
                            color = ParchmentSurface,
                        )
                    }
                }

                AnimatedVisibility(
                    visible = state.bonusHpBannerTimer > 0f,
                    enter = fadeIn() + scaleIn(),
                    exit = fadeOut(),
                ) {
                    Box(
                        modifier = Modifier
                            .background(CrimsonInk, RoundedCornerShape(4.dp))
                            .padding(horizontal = 12.dp, vertical = 4.dp),
                    ) {
                        Text(
                            text = stringResource(R.string.bonus_hp_toast),
                            style = MaterialTheme.typography.labelLarge,
                            color = ParchmentSurface,
                        )
                    }
                }

                AnimatedVisibility(
                    visible = state.hitBannerTimer > 0f,
                    enter = fadeIn() + scaleIn(),
                    exit = fadeOut(),
                ) {
                    Box(
                        modifier = Modifier
                            .background(InkBlack, RoundedCornerShape(4.dp))
                            .padding(horizontal = 12.dp, vertical = 4.dp),
                    ) {
                        Text(
                            text = stringResource(R.string.hit_toast, state.hp),
                            style = MaterialTheme.typography.labelLarge,
                            color = ParchmentSurface,
                        )
                    }
                }

                AnimatedVisibility(
                    visible = state.nearMissBannerTimer > 0f,
                    enter = fadeIn() + scaleIn(),
                    exit = fadeOut(),
                ) {
                    Box(
                        modifier = Modifier
                            .background(CrimsonInk, RoundedCornerShape(4.dp))
                            .padding(horizontal = 12.dp, vertical = 4.dp),
                    ) {
                        Text(
                            text = stringResource(R.string.near_miss_toast, state.lastNearMissBonus),
                            style = MaterialTheme.typography.labelLarge,
                            color = ParchmentSurface,
                        )
                    }
                }
            }

            AnimatedVisibility(
                visible = state.phase == GamePhase.PLAYING && state.distanceMeters < 45,
                enter = fadeIn(),
                exit = fadeOut(),
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .navigationBarsPadding()
                    .padding(bottom = 26.dp, start = 16.dp, end = 16.dp),
            ) {
                Box(
                    modifier = Modifier
                        .background(ParchmentSurface.copy(alpha = 0.88f), RoundedCornerShape(6.dp))
                        .drawBehind {
                            drawRoundRect(
                                color = InkCharcoal.copy(alpha = 0.55f),
                                cornerRadius = CornerRadius(6.dp.toPx()),
                                style = Stroke(width = 1.2.dp.toPx()),
                            )
                        }
                        .padding(horizontal = 14.dp, vertical = 8.dp),
                ) {
                    Text(
                        text = stringResource(R.string.control_hint),
                        style = MaterialTheme.typography.bodyMedium,
                        color = InkCharcoal,
                        textAlign = TextAlign.Center,
                    )
                }
            }
        }

        // Pause Overlay
        AnimatedVisibility(
            visible = state.phase == GamePhase.PAUSED,
            enter = fadeIn(),
            exit = fadeOut(),
            modifier = Modifier.fillMaxSize(),
        ) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(ParchmentPaper.copy(alpha = 0.72f))
                    .systemBarsPadding()
                    .padding(24.dp),
                contentAlignment = Alignment.Center,
            ) {
                CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Rtl) {
                    InkFrameCard(
                        modifier = Modifier
                            .widthIn(max = 360.dp)
                            .fillMaxWidth()
                            .testTag("pause_card"),
                    ) {
                        Text(
                            text = stringResource(R.string.paused_title),
                            style = MaterialTheme.typography.headlineMedium,
                            color = InkBlack,
                            textAlign = TextAlign.Center,
                            modifier = Modifier.fillMaxWidth(),
                        )
                        Spacer(modifier = Modifier.height(6.dp))
                        InkDivider()
                        Spacer(modifier = Modifier.height(14.dp))
                        InkPrimaryButton(
                            text = stringResource(R.string.resume_button),
                            icon = Icons.Default.PlayArrow,
                            onClick = onResume,
                            testTag = "resume_button",
                            modifier = Modifier.fillMaxWidth(),
                        )
                        Spacer(modifier = Modifier.height(10.dp))
                        InkPrimaryButton(
                            text = stringResource(R.string.sound_settings),
                            icon = Icons.Default.Tune,
                            onClick = onOpenSoundSettings,
                            testTag = "pause_sound_button",
                            filled = false,
                            modifier = Modifier.fillMaxWidth(),
                        )
                        Spacer(modifier = Modifier.height(10.dp))
                        InkPrimaryButton(
                            text = stringResource(R.string.back_to_menu_button),
                            icon = Icons.Default.Home,
                            onClick = onReturnToMenu,
                            testTag = "pause_menu_button",
                            filled = false,
                            modifier = Modifier.fillMaxWidth(),
                        )
                    }
                }
            }
        }

        // Game Over Overlay
        AnimatedVisibility(
            visible = state.phase == GamePhase.GAME_OVER,
            enter = fadeIn() + scaleIn(initialScale = 0.94f),
            exit = fadeOut(),
            modifier = Modifier.fillMaxSize(),
        ) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(ParchmentPaper.copy(alpha = 0.76f))
                    .systemBarsPadding()
                    .padding(24.dp),
                contentAlignment = Alignment.Center,
            ) {
                CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Rtl) {
                    InkFrameCard(
                        modifier = Modifier
                            .widthIn(max = 380.dp)
                            .fillMaxWidth()
                            .testTag("game_over_card"),
                        accentCrimson = state.isNewRecord,
                    ) {
                        if (state.isNewRecord) {
                            Box(
                                modifier = Modifier
                                    .align(Alignment.CenterHorizontally)
                                    .background(CrimsonInk, RoundedCornerShape(4.dp))
                                    .padding(horizontal = 12.dp, vertical = 4.dp)
                                    .testTag("new_record_badge"),
                            ) {
                                Text(
                                    text = stringResource(R.string.new_record_badge),
                                    style = MaterialTheme.typography.labelLarge,
                                    color = ParchmentSurface,
                                )
                            }
                            Spacer(modifier = Modifier.height(8.dp))
                        }

                        Text(
                            text = stringResource(R.string.game_over_title),
                            style = MaterialTheme.typography.headlineMedium,
                            color = InkBlack,
                            textAlign = TextAlign.Center,
                            modifier = Modifier.fillMaxWidth(),
                        )

                        Spacer(modifier = Modifier.height(10.dp))
                        InkDivider()
                        Spacer(modifier = Modifier.height(12.dp))

                        StatRow(label = stringResource(R.string.score_label), value = state.score.toString(), highlight = state.isNewRecord)
                        Spacer(modifier = Modifier.height(8.dp))
                        StatRow(label = stringResource(R.string.high_score_label), value = state.bestScore.toString(), highlight = true)
                        Spacer(modifier = Modifier.height(8.dp))
                        StatRow(label = stringResource(R.string.coins_earned_label), value = "+${state.runCoins}")
                        Spacer(modifier = Modifier.height(8.dp))
                        StatRow(label = stringResource(R.string.distance_label), value = "${state.distanceMeters}m")

                        Spacer(modifier = Modifier.height(16.dp))

                        InkPrimaryButton(
                            text = stringResource(R.string.play_again_button),
                            icon = Icons.Default.Replay,
                            onClick = onPlayAgain,
                            testTag = "play_again_button",
                            modifier = Modifier.fillMaxWidth(),
                        )

                        Spacer(modifier = Modifier.height(8.dp))

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                        ) {
                            InkPrimaryButton(
                                text = stringResource(R.string.shop_button),
                                icon = Icons.Default.Storefront,
                                onClick = onOpenShop,
                                testTag = "game_over_shop_button",
                                filled = false,
                                modifier = Modifier.weight(1f),
                            )
                            InkPrimaryButton(
                                text = stringResource(R.string.back_to_menu_button),
                                icon = Icons.Default.Home,
                                onClick = onReturnToMenu,
                                testTag = "game_over_menu_button",
                                filled = false,
                                modifier = Modifier.weight(1f),
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun StatRow(
    label: String,
    value: String,
    highlight: Boolean = false,
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(text = label, style = MaterialTheme.typography.bodyLarge, color = InkSketch)
        Text(
            text = value,
            style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Bold),
            color = if (highlight) CrimsonInk else InkBlack,
        )
    }
}
