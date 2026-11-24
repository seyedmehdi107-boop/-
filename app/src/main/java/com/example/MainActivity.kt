package com.example

import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.compose.animation.Crossfade
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Modifier
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.game.GamePhase
import com.example.game.GameViewModel
import com.example.ui.FlightGameScreen
import com.example.ui.MainMenuScreen
import com.example.ui.ShopDialog
import com.example.ui.SoundSettingsDialog
import com.example.ui.theme.CrowFlightTheme

class MainActivity : ComponentActivity() {

    private val gameViewModel: GameViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        requestHighRefreshRate120Hz()
        enableEdgeToEdge(
            statusBarStyle = SystemBarStyle.light(
                android.graphics.Color.TRANSPARENT,
                android.graphics.Color.TRANSPARENT,
            ),
            navigationBarStyle = SystemBarStyle.light(
                android.graphics.Color.TRANSPARENT,
                android.graphics.Color.TRANSPARENT,
            ),
        )
        setContent {
            CrowFlightTheme {
                CrowFlightApp(viewModel = gameViewModel)
            }
        }
    }

    private fun requestHighRefreshRate120Hz() {
        runCatching {
            val attrs = window.attributes
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                val modes = display?.supportedModes
                val bestMode = modes
                    ?.filter { it.physicalWidth == (display?.mode?.physicalWidth ?: it.physicalWidth) }
                    ?.maxByOrNull { it.refreshRate }
                    ?: modes?.maxByOrNull { it.refreshRate }
                if (bestMode != null) {
                    attrs.preferredDisplayModeId = bestMode.modeId
                }
            }
            attrs.preferredRefreshRate = 120f
            window.attributes = attrs
        }
    }

    override fun onResume() {
        super.onResume()
        gameViewModel.resumeAudio()
    }

    override fun onPause() {
        super.onPause()
        gameViewModel.onPauseGame()
        gameViewModel.pauseAudio()
    }
}

@Composable
fun CrowFlightApp(
    viewModel: GameViewModel,
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()

    LaunchedEffect(Unit) {
        var lastNanos = 0L
        while (true) {
            withFrameNanos { frameTimeNanos ->
                if (lastNanos != 0L) {
                    val dt = (frameTimeNanos - lastNanos) / 1_000_000_000f
                    viewModel.stepFrame(dt)
                }
                lastNanos = frameTimeNanos
            }
        }
    }

    BackHandler(enabled = state.showSoundDialog || state.showShopDialog || state.phase != GamePhase.MENU) {
        when {
            state.showShopDialog -> viewModel.openShopDialog(false)
            state.showSoundDialog -> viewModel.openSoundDialog(false)
            state.phase == GamePhase.PLAYING -> viewModel.onPauseGame()
            state.phase == GamePhase.PAUSED -> viewModel.onResumeGame()
            state.phase == GamePhase.GAME_OVER -> viewModel.onReturnToMenu()
            state.phase == GamePhase.LAUNCHING -> viewModel.onReturnToMenu()
        }
    }

    Crossfade(
        targetState = state.phase == GamePhase.MENU,
        animationSpec = tween(durationMillis = 320),
        modifier = Modifier.fillMaxSize(),
        label = "menu_game_crossfade",
    ) { isMenu ->
        if (isMenu) {
            MainMenuScreen(
                state = state,
                onPlayClick = viewModel::onStartLaunch,
                onOpenShop = { viewModel.openShopDialog(true) },
                onOpenSoundSettings = { viewModel.openSoundDialog(true) },
                onQuickMuteToggle = viewModel::toggleQuickMute,
            )
        } else {
            FlightGameScreen(
                state = state,
                world = viewModel.world,
                frameTick = viewModel.frameTick,
                onDragSteer = viewModel::steerByDrag,
                onPause = viewModel::onPauseGame,
                onResume = viewModel::onResumeGame,
                onPlayAgain = viewModel::onStartLaunch,
                onReturnToMenu = viewModel::onReturnToMenu,
                onQuickMuteToggle = viewModel::toggleQuickMute,
                onOpenSoundSettings = { viewModel.openSoundDialog(true) },
                onOpenShop = { viewModel.openShopDialog(true) },
            )
        }
    }

    if (state.showShopDialog) {
        ShopDialog(
            state = state,
            onSkinAction = viewModel::onSkinAction,
            onUpgradeAction = viewModel::onUpgradeAction,
            onDismiss = { viewModel.openShopDialog(false) },
        )
    }

    if (state.showSoundDialog) {
        SoundSettingsDialog(
            sfxEnabled = state.sfxEnabled,
            ambientEnabled = state.ambientEnabled,
            masterVolume = state.masterVolume,
            onSfxChange = viewModel::setSfxEnabled,
            onAmbientChange = viewModel::setAmbientEnabled,
            onVolumeChange = viewModel::setMasterVolume,
            onDismiss = { viewModel.openSoundDialog(false) },
        )
    }
}
