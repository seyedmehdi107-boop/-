package com.example.game

import android.app.Application
import androidx.compose.runtime.MutableLongState
import androidx.compose.runtime.mutableLongStateOf
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.R
import com.example.audio.GameAudioEngine
import com.example.audio.SoundEffect
import com.example.data.GamePreferencesRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.ceil
import kotlin.math.cos
import kotlin.math.hypot
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sin
import kotlin.random.Random

class GameViewModel(application: Application) : AndroidViewModel(application) {

    private val appContext = application.applicationContext
    private val repository = GamePreferencesRepository(appContext)
    private val audioEngine = GameAudioEngine()

    val world = FlightWorldState()
    val frameTick: MutableLongState = mutableLongStateOf(0L)

    private val _uiState = MutableStateFlow(GameUiState())
    val uiState: StateFlow<GameUiState> = _uiState.asStateFlow()

    private val rng = Random(System.currentTimeMillis())
    private var nextId = 1000L
    private var distanceAccumulator = 0f
    private var spawnDistanceAccumulator = 0f
    private var bonusScoreAccumulator = 0
    private var flapSoundCooldown = 0f
    private var prevGapX = 0f
    private var prevGapY = 0f
    private var waveCounter = 0

    // Discrete flight HUD mirrors to avoid unnecessary Compose recompositions at 120 FPS
    private var hudScore = 0
    private var hudHp = 1
    private var hudMaxHp = 1
    private var hudRunCoins = 0
    private var hudDistance = 0
    private var hudNearMiss = 0
    private var hudMagnetSecs = 0
    private var hudNearMissTimer = 0f
    private var hudLastNearMissBonus = 0
    private var hudWeatherBannerText: String? = null
    private var weatherBannerTimer = 0f
    private var hudBonusHpTimer = 0f
    private var nextHpRewardDistance = 450

    companion object {
        const val CROW_Z = 1.65f
        const val FAR_Z = 20.5f
        const val CORRIDOR_HALF_X = 0.88f
        const val CORRIDOR_HALF_Y = 0.68f
        private const val LAUNCH_DURATION = 1.05f
        private const val BASE_SPEED = 6.2f
        private const val MAX_SPEED = 12.8f
        const val RAIN_DISTANCE_THRESHOLD = 850
        const val SNOW_DISTANCE_THRESHOLD = 1900
        private const val FIRST_HP_REWARD_DIST = 450
        private const val HP_REWARD_INTERVAL_DIST = 550
    }

    init {
        initWorldScenery()
        viewModelScope.launch(Dispatchers.Default) {
            world.spriteAtlas = InkSpriteAtlas.load(appContext)
            frameTick.longValue++
        }
        viewModelScope.launch {
            repository.gameDataFlow.collect { stored ->
                if (world.phase == GamePhase.MENU) {
                    world.equippedSkin = stored.equippedSkin
                }
                _uiState.update { state ->
                    state.copy(
                        bestScore = stored.bestScore,
                        bestDistance = stored.bestDistance,
                        totalFlights = stored.totalFlights,
                        totalCoins = stored.coins,
                        equippedSkin = stored.equippedSkin,
                        unlockedSkins = stored.unlockedSkins,
                        magnetLevel = stored.magnetLevel,
                        bountyLevel = stored.bountyLevel,
                        agilityLevel = stored.agilityLevel,
                        maxHp = if (state.phase == GamePhase.MENU) stored.equippedSkin.maxHp else state.maxHp,
                        hp = if (state.phase == GamePhase.MENU) stored.equippedSkin.maxHp else state.hp,
                        sfxEnabled = stored.sfxEnabled,
                        ambientEnabled = stored.ambientEnabled,
                        masterVolume = stored.masterVolume,
                    )
                }
                audioEngine.updateSettings(
                    sfxEnabled = stored.sfxEnabled,
                    ambientEnabled = stored.ambientEnabled,
                    masterVolume = stored.masterVolume,
                )
            }
        }
    }

    fun onStartLaunch() {
        audioEngine.playSfx(SoundEffect.UI_TAP)
        audioEngine.playSfx(SoundEffect.LAUNCH_CAW)
        audioEngine.setFlightIntensity(0.45f)

        val currentUi = _uiState.value
        val skin = currentUi.equippedSkin

        distanceAccumulator = 0f
        spawnDistanceAccumulator = 0f
        bonusScoreAccumulator = 0
        flapSoundCooldown = 0.2f
        prevGapX = 0f
        prevGapY = 0f
        waveCounter = 0

        hudScore = 0
        hudHp = skin.maxHp
        hudMaxHp = skin.maxHp
        hudRunCoins = 0
        hudDistance = 0
        hudNearMiss = 0
        hudMagnetSecs = 0
        hudNearMissTimer = 0f
        hudWeatherBannerText = null
        weatherBannerTimer = 0f
        hudBonusHpTimer = 0f
        nextHpRewardDistance = FIRST_HP_REWARD_DIST

        world.phase = GamePhase.LAUNCHING
        world.equippedSkin = skin
        world.weather = WeatherBiome.CLEAR_MIST
        world.weatherBlend = 0f
        world.launchProgress = 0f
        world.crowX = 0.28f
        world.crowY = 0.56f
        world.targetX = 0f
        world.targetY = -0.04f
        world.crowVelX = 0f
        world.crowVelY = 0f
        world.cameraX = 0.14f
        world.cameraY = 0.30f
        world.bankAngle = -0.18f
        world.pitchAngle = 0.25f
        world.flapPhase = 0f
        world.speed = BASE_SPEED + skin.agilityBonus * 1.5f
        world.invulnTimer = 0f
        world.magnetPowerTimer = 0f
        world.hitBannerTimer = 0f
        world.screenShake = 0f
        world.dangerLevel = 0f

        world.obstacles.clear()
        world.coins.clear()
        world.magnets.clear()
        world.particles.clear()
        initWorldScenery()

        spawnNextWave(z = 11.5f, currentDistance = 0, weather = WeatherBiome.CLEAR_MIST)
        spawnNextWave(z = 17.8f, currentDistance = 20, weather = WeatherBiome.CLEAR_MIST)

        val isDragon = skin == CreatureSkin.BABY_DRAGON
        for (idx in 0 until 10) {
            world.particles.add(
                FeatherParticle(
                    x = 0.28f + (rng.nextFloat() - 0.5f) * 0.2f,
                    y = 0.52f + (rng.nextFloat() - 0.5f) * 0.15f,
                    vx = (rng.nextFloat() - 0.5f) * 0.6f,
                    vy = -0.2f - rng.nextFloat() * 0.5f,
                    angle = rng.nextFloat() * 360f,
                    vAngle = (rng.nextFloat() - 0.5f) * 260f,
                    size = 0.7f + rng.nextFloat() * 0.6f,
                    life = 0.9f + idx * 0.05f,
                    maxLife = 1.2f,
                    isEmber = isDragon,
                )
            )
        }

        _uiState.update {
            it.copy(
                phase = GamePhase.LAUNCHING,
                weather = WeatherBiome.CLEAR_MIST,
                weatherBannerText = null,
                hp = skin.maxHp,
                maxHp = skin.maxHp,
                distanceMeters = 0,
                score = 0,
                runCoins = 0,
                nearMissCount = 0,
                isNewRecord = false,
                magnetSecondsLeft = 0,
                nearMissBannerTimer = 0f,
                hitBannerTimer = 0f,
                bonusHpBannerTimer = 0f,
                showSoundDialog = false,
                showShopDialog = false,
            )
        }
        frameTick.longValue++
    }

    fun onReturnToMenu() {
        audioEngine.playSfx(SoundEffect.UI_TAP)
        audioEngine.setFlightIntensity(0.20f)
        world.phase = GamePhase.MENU
        world.weather = WeatherBiome.CLEAR_MIST
        world.weatherBlend = 0f
        world.obstacles.clear()
        world.coins.clear()
        world.magnets.clear()
        world.particles.clear()
        world.screenShake = 0f
        world.dangerLevel = 0f
        world.invulnTimer = 0f
        world.magnetPowerTimer = 0f
        world.hitBannerTimer = 0f
        _uiState.update {
            it.copy(
                phase = GamePhase.MENU,
                weather = WeatherBiome.CLEAR_MIST,
                weatherBannerText = null,
                magnetSecondsLeft = 0,
            )
        }
        frameTick.longValue++
    }

    fun onPauseGame() {
        if (world.phase == GamePhase.PLAYING) {
            audioEngine.playSfx(SoundEffect.UI_TAP)
            audioEngine.setFlightIntensity(0.15f)
            world.phase = GamePhase.PAUSED
            _uiState.update { it.copy(phase = GamePhase.PAUSED) }
        }
    }

    fun onResumeGame() {
        if (world.phase == GamePhase.PAUSED) {
            audioEngine.playSfx(SoundEffect.UI_TAP)
            world.phase = GamePhase.PLAYING
            _uiState.update { it.copy(phase = GamePhase.PLAYING) }
        }
    }

    fun openSoundDialog(show: Boolean) {
        audioEngine.playSfx(SoundEffect.UI_TAP)
        _uiState.update { it.copy(showSoundDialog = show) }
    }

    fun openShopDialog(show: Boolean) {
        audioEngine.playSfx(SoundEffect.UI_TAP)
        _uiState.update { it.copy(showShopDialog = show) }
    }

    fun onSkinAction(skin: CreatureSkin) {
        val state = _uiState.value
        val wasUnlocked = skin in state.unlockedSkins
        val canEquipOrBuy = wasUnlocked || state.totalCoins >= skin.priceCoins
        if (!canEquipOrBuy) return

        // Optimistic immediate sync so both menu & flight reflect the new creature instantly
        world.equippedSkin = skin
        _uiState.update { s ->
            if (wasUnlocked) {
                s.copy(equippedSkin = skin, maxHp = skin.maxHp, hp = skin.maxHp)
            } else {
                s.copy(
                    equippedSkin = skin,
                    unlockedSkins = s.unlockedSkins + skin,
                    totalCoins = (s.totalCoins - skin.priceCoins).coerceAtLeast(0),
                    maxHp = skin.maxHp,
                    hp = skin.maxHp,
                )
            }
        }
        audioEngine.playSfx(if (wasUnlocked) SoundEffect.UI_TAP else SoundEffect.PURCHASE)

        viewModelScope.launch {
            repository.purchaseOrEquipSkin(skin)
        }
    }

    fun onUpgradeAction(type: UpgradeType) {
        val s = _uiState.value
        val curLvl = when (type) {
            UpgradeType.MAGNET -> s.magnetLevel
            UpgradeType.BOUNTY -> s.bountyLevel
            UpgradeType.AGILITY -> s.agilityLevel
        }
        val cost = type.nextCost(curLvl) ?: return
        if (s.totalCoins < cost) return

        _uiState.update { prev ->
            prev.copy(
                totalCoins = (prev.totalCoins - cost).coerceAtLeast(0),
                magnetLevel = if (type == UpgradeType.MAGNET) curLvl + 1 else prev.magnetLevel,
                bountyLevel = if (type == UpgradeType.BOUNTY) curLvl + 1 else prev.bountyLevel,
                agilityLevel = if (type == UpgradeType.AGILITY) curLvl + 1 else prev.agilityLevel,
            )
        }
        audioEngine.playSfx(SoundEffect.PURCHASE)

        viewModelScope.launch {
            repository.purchaseUpgrade(type)
        }
    }

    fun toggleQuickMute() {
        val current = _uiState.value
        val anyOn = current.sfxEnabled || current.ambientEnabled
        val next = !anyOn
        viewModelScope.launch {
            repository.setSfxEnabled(next)
            repository.setAmbientEnabled(next)
        }
        if (next) audioEngine.playSfx(SoundEffect.UI_TAP)
    }

    fun setSfxEnabled(enabled: Boolean) {
        viewModelScope.launch { repository.setSfxEnabled(enabled) }
        if (enabled) audioEngine.playSfx(SoundEffect.UI_TAP)
    }

    fun setAmbientEnabled(enabled: Boolean) {
        viewModelScope.launch { repository.setAmbientEnabled(enabled) }
    }

    fun setMasterVolume(volume: Float) {
        viewModelScope.launch { repository.setMasterVolume(volume) }
    }

    fun steerByDrag(deltaNormX: Float, deltaNormY: Float) {
        if (world.phase != GamePhase.PLAYING) return
        val s = _uiState.value
        val agilityBoost = 1.0f + s.agilityLevel * 0.08f + world.equippedSkin.agilityBonus * 0.5f
        world.targetX = (world.targetX + deltaNormX * 1.40f * agilityBoost).coerceIn(-CORRIDOR_HALF_X, CORRIDOR_HALF_X)
        world.targetY = (world.targetY + deltaNormY * 1.30f * agilityBoost).coerceIn(-CORRIDOR_HALF_Y, CORRIDOR_HALF_Y)

        if (deltaNormY < -0.025f && flapSoundCooldown <= 0f) {
            audioEngine.playSfx(SoundEffect.WING_FLAP)
            flapSoundCooldown = 0.28f
        }
    }

    fun stepFrame(rawDt: Float) {
        val dt = rawDt.coerceIn(0.001f, 0.035f)
        if (flapSoundCooldown > 0f) {
            flapSoundCooldown = (flapSoundCooldown - dt).coerceAtLeast(0f)
        }

        when (world.phase) {
            GamePhase.MENU -> {
                world.envTime += dt
                world.flapPhase += dt * 2.4f
                stepMotesInPlace(dt, 1.4f)
                stepCloudsInPlace(dt, 0.4f)
                return // MainMenuScreen does not need 120Hz tick recompositions
            }

            GamePhase.LAUNCHING -> {
                world.envTime += dt
                world.launchProgress = (world.launchProgress + dt / LAUNCH_DURATION).coerceAtMost(1f)
                val inv = 1f - world.launchProgress
                val ease = 1f - inv * inv * inv

                world.crowX = 0.28f * (1f - ease)
                world.crowY = 0.56f * (1f - ease) - sin(world.launchProgress * PI).toFloat() * 0.18f
                world.cameraX = world.crowX * 0.55f
                world.cameraY = world.crowY * 0.55f + (1f - ease) * 0.18f
                world.bankAngle = -0.22f * (1f - ease)
                world.pitchAngle = 0.30f * sin(world.launchProgress * PI).toFloat()
                world.flapPhase += dt * 16f

                stepParticlesInPlace(dt)
                stepMotesInPlace(dt, BASE_SPEED * 0.7f)
                stepCloudsInPlace(dt, BASE_SPEED * 0.12f)

                if (world.launchProgress >= 0.45f && world.launchProgress - dt / LAUNCH_DURATION < 0.45f) {
                    audioEngine.playSfx(SoundEffect.WING_FLAP)
                }

                if (world.launchProgress >= 1f) {
                    world.phase = GamePhase.PLAYING
                    world.crowX = 0f
                    world.crowY = 0f
                    world.targetX = 0f
                    world.targetY = 0f
                    world.cameraX = 0f
                    world.cameraY = 0f
                    world.bankAngle = 0f
                    world.pitchAngle = 0f
                    _uiState.update { it.copy(phase = GamePhase.PLAYING) }
                }
                frameTick.longValue++
            }

            GamePhase.PLAYING -> {
                stepPlaying(dt)
                frameTick.longValue++
            }

            GamePhase.PAUSED -> Unit

            GamePhase.GAME_OVER -> {
                world.envTime += dt
                world.screenShake = (world.screenShake - dt * 2.5f).coerceAtLeast(0f)
                stepParticlesInPlace(dt)
                stepWeatherParticlesInPlace(dt)
                frameTick.longValue++
            }
        }
    }

    private fun stepPlaying(dt: Float) {
        val uiSnapshot = _uiState.value
        val targetSpeed = (BASE_SPEED + hudDistance * 0.0036f).coerceAtMost(MAX_SPEED)
        world.speed += (targetSpeed - world.speed) * min(1f, dt * 2f)
        audioEngine.setFlightIntensity((world.speed - BASE_SPEED) / (MAX_SPEED - BASE_SPEED) * 0.7f + 0.3f)

        val stiffness = 14.0f + uiSnapshot.agilityLevel * 1.4f + world.equippedSkin.agilityBonus * 5f
        val newVelX = (world.targetX - world.crowX) * stiffness
        val newVelY = (world.targetY - world.crowY) * stiffness

        world.crowVelX = newVelX
        world.crowVelY = newVelY
        world.crowX = (world.crowX + newVelX * dt).coerceIn(-CORRIDOR_HALF_X, CORRIDOR_HALF_X)
        world.crowY = (world.crowY + newVelY * dt).coerceIn(-CORRIDOR_HALF_Y, CORRIDOR_HALF_Y)

        world.cameraX += (world.crowX * 0.68f - world.cameraX) * min(1f, dt * 8.0f)
        world.cameraY += (world.crowY * 0.60f - world.cameraY) * min(1f, dt * 8.0f)

        val targetBank = (newVelX * 0.24f).coerceIn(-0.65f, 0.65f)
        world.bankAngle += (targetBank - world.bankAngle) * min(1f, dt * 11f)

        val targetPitch = (-newVelY * 0.20f).coerceIn(-0.42f, 0.42f)
        world.pitchAngle += (targetPitch - world.pitchAngle) * min(1f, dt * 10f)

        val flapRate = 7.8f + (-newVelY * 2.2f).coerceIn(-2f, 5.5f) + (world.speed - BASE_SPEED) * 0.4f
        world.flapPhase += dt * flapRate
        world.envTime += dt

        world.invulnTimer = (world.invulnTimer - dt).coerceAtLeast(0f)
        world.magnetPowerTimer = (world.magnetPowerTimer - dt).coerceAtLeast(0f)
        world.hitBannerTimer = (world.hitBannerTimer - dt).coerceAtLeast(0f)
        world.screenShake = (world.screenShake - dt * 2.4f).coerceAtLeast(0f)

        if (hudNearMissTimer > 0f) hudNearMissTimer = (hudNearMissTimer - dt).coerceAtLeast(0f)
        if (hudBonusHpTimer > 0f) hudBonusHpTimer = (hudBonusHpTimer - dt).coerceAtLeast(0f)
        if (weatherBannerTimer > 0f) {
            weatherBannerTimer = (weatherBannerTimer - dt).coerceAtLeast(0f)
            if (weatherBannerTimer <= 0f) hudWeatherBannerText = null
        }

        val deltaDist = world.speed * dt * 2.2f
        distanceAccumulator += deltaDist
        spawnDistanceAccumulator += world.speed * dt
        val newDistance = distanceAccumulator.toInt()
        val scoreMult = 1f + uiSnapshot.bountyLevel * 0.15f

        stepParticlesInPlace(dt)
        if (rng.nextFloat() < dt * 10f) {
            val side = if (rng.nextBoolean()) 1f else -1f
            world.particles.add(
                FeatherParticle(
                    x = world.crowX + side * 0.16f,
                    y = world.crowY + sin(world.flapPhase) * 0.03f,
                    vx = side * 0.08f,
                    vy = 0.12f,
                    angle = rng.nextFloat() * 360f,
                    vAngle = (rng.nextFloat() - 0.5f) * 140f,
                    size = 0.48f,
                    life = 0.46f,
                    maxLife = 0.46f,
                    isDrop = world.equippedSkin != CreatureSkin.BABY_DRAGON,
                    isEmber = world.equippedSkin == CreatureSkin.BABY_DRAGON,
                    isGoldSpark = world.equippedSkin == CreatureSkin.PEACOCK && rng.nextFloat() < 0.45f,
                )
            )
        }

        // TIGHT TORSO-ONLY HITBOX (wings touching obstacles NEVER lose HP!)
        val bodyHalfW = 0.038f
        val bodyHalfH = 0.034f
        val bodyHalfD = 0.16f
        val wingHalfW = 0.23f
        val wingHalfH = 0.062f

        var bodyHit = false
        var closestDanger = 0f

        for (i in world.obstacles.size - 1 downTo 0) {
            val obs = world.obstacles[i]
            if (obs.kind == ObstacleKind.SHADOW_CROW) {
                obs.minX += obs.lateralVelX * dt
                obs.maxX += obs.lateralVelX * dt
                if (obs.minX < -1.1f || obs.maxX > 1.1f) {
                    obs.lateralVelX = -obs.lateralVelX
                    obs.minX += obs.lateralVelX * dt
                    obs.maxX += obs.lateralVelX * dt
                }
            }
            obs.z -= world.speed * dt

            if (obs.z > CROW_Z && obs.z < CROW_Z + 5.5f) {
                if (doesRectHitObstacleShape(world.crowX, world.crowY, bodyHalfW + 0.04f, bodyHalfH + 0.04f, obs)) {
                    closestDanger = max(closestDanger, 1f - ((obs.z - CROW_Z) / 5.5f))
                }
            }

            if (abs(obs.z - CROW_Z) < (obs.halfDepth + bodyHalfD)) {
                if (doesRectHitObstacleShape(world.crowX, world.crowY, bodyHalfW, bodyHalfH, obs)) {
                    bodyHit = true
                } else if (!obs.wingGrazed && doesRectHitObstacleShape(world.crowX, world.crowY, wingHalfW, wingHalfH, obs)) {
                    obs.wingGrazed = true
                    bonusScoreAccumulator += (5 * scoreMult).toInt()
                    if (flapSoundCooldown <= 0f) {
                        audioEngine.playSfx(SoundEffect.WING_FLAP)
                        flapSoundCooldown = 0.18f
                    }
                    repeat(3) {
                        world.particles.add(
                            FeatherParticle(
                                x = world.crowX + (if (world.crowX < (obs.minX + obs.maxX) * 0.5f) 0.16f else -0.16f),
                                y = world.crowY,
                                vx = (rng.nextFloat() - 0.5f) * 0.8f,
                                vy = -0.25f + rng.nextFloat() * 0.4f,
                                angle = rng.nextFloat() * 360f,
                                vAngle = (rng.nextFloat() - 0.5f) * 360f,
                                size = 0.6f,
                                life = 0.6f,
                                maxLife = 0.6f,
                            )
                        )
                    }
                }
            }

            if (!obs.passed && obs.z < CROW_Z - obs.halfDepth) {
                obs.passed = true
                bonusScoreAccumulator += (10 * scoreMult).toInt()
                if (!obs.nearMissAwarded) {
                    val dx = when {
                        world.crowX < obs.minX -> obs.minX - world.crowX
                        world.crowX > obs.maxX -> world.crowX - obs.maxX
                        else -> 0f
                    }
                    val dy = when {
                        world.crowY < obs.minY -> obs.minY - world.crowY
                        world.crowY > obs.maxY -> world.crowY - obs.maxY
                        else -> 0f
                    }
                    if (hypot(dx, dy) < 0.22f) {
                        obs.nearMissAwarded = true
                        hudNearMiss += 1
                        val pts = (15 * scoreMult).toInt()
                        bonusScoreAccumulator += pts
                        hudLastNearMissBonus = pts
                        hudNearMissTimer = 1.1f
                        audioEngine.playSfx(SoundEffect.NEAR_MISS)
                    }
                }
            }

            if (obs.z <= -0.6f) {
                world.obstacles.removeAt(i)
            }
        }
        world.dangerLevel = closestDanger

        // Step 3D Magnet Pickups
        for (i in world.magnets.size - 1 downTo 0) {
            val mag = world.magnets[i]
            mag.z -= world.speed * dt
            mag.spinPhase += dt * 4.2f
            if (abs(mag.z - CROW_Z) < 0.48f && hypot(world.crowX - mag.x, world.crowY - mag.y) < 0.20f) {
                world.magnetPowerTimer = 8.0f + uiSnapshot.magnetLevel * 1.5f
                audioEngine.playSfx(SoundEffect.PURCHASE)
                repeat(8) {
                    world.particles.add(
                        FeatherParticle(
                            x = mag.x,
                            y = mag.y,
                            vx = (rng.nextFloat() - 0.5f) * 1.3f,
                            vy = (rng.nextFloat() - 0.5f) * 1.3f,
                            angle = 0f,
                            vAngle = 0f,
                            size = 0.6f,
                            life = 0.5f,
                            maxLife = 0.5f,
                            isMagnetSpark = true,
                        )
                    )
                }
                world.magnets.removeAt(i)
            } else if (mag.z <= -0.4f) {
                world.magnets.removeAt(i)
            }
        }

        // Step 3D Coins — strong automatic full-corridor pull when Magnet power-up is active!
        val magnetActive = world.magnetPowerTimer > 0f
        val pullRadius = if (magnetActive) 2.2f else (0.16f + uiSnapshot.magnetLevel * 0.085f)
        val pullMaxZ = if (magnetActive) CROW_Z + 9.5f else CROW_Z + 2.8f
        val coinValue = 5 + uiSnapshot.bountyLevel * 2

        for (i in world.coins.size - 1 downTo 0) {
            val coin = world.coins[i]
            coin.z -= world.speed * dt
            coin.spinPhase += dt * 5.2f

            val dist2d = hypot(world.crowX - coin.x, world.crowY - coin.y)
            if (coin.z < pullMaxZ && coin.z > CROW_Z - 0.6f && dist2d < pullRadius) {
                val pullSpeed = if (magnetActive) 10.5f else (7.0f + uiSnapshot.magnetLevel * 2.2f)
                val pull = min(1f, dt * pullSpeed)
                coin.x += (world.crowX - coin.x) * pull
                coin.y += (world.crowY - coin.y) * pull
                if (magnetActive && coin.z > CROW_Z) {
                    coin.z += (CROW_Z - coin.z) * min(1f, dt * 4.5f)
                }
            }

            if (abs(coin.z - CROW_Z) < 0.44f && hypot(world.crowX - coin.x, world.crowY - coin.y) < 0.17f) {
                hudRunCoins += coinValue
                bonusScoreAccumulator += (15 * scoreMult).toInt()
                audioEngine.playSfx(SoundEffect.COIN_PICKUP)
                repeat(4) {
                    world.particles.add(
                        FeatherParticle(
                            x = coin.x,
                            y = coin.y,
                            vx = (rng.nextFloat() - 0.5f) * 1.1f,
                            vy = (rng.nextFloat() - 0.5f) * 1.1f,
                            angle = 0f,
                            vAngle = 0f,
                            size = 0.55f,
                            life = 0.45f,
                            maxLife = 0.45f,
                            isGoldSpark = true,
                        )
                    )
                }
                world.coins.removeAt(i)
            } else if (coin.z <= -0.4f) {
                world.coins.removeAt(i)
            }
        }

        // Update Weather Biome only after long-distance journey milestones!
        val totalScore = (newDistance * scoreMult).toInt() + bonusScoreAccumulator
        val targetWeather = when {
            newDistance >= SNOW_DISTANCE_THRESHOLD -> WeatherBiome.SNOWY
            newDistance >= RAIN_DISTANCE_THRESHOLD -> WeatherBiome.RAINY
            else -> WeatherBiome.CLEAR_MIST
        }
        if (targetWeather != world.weather) {
            world.weather = targetWeather
            hudWeatherBannerText = when (targetWeather) {
                WeatherBiome.RAINY -> appContext.getString(R.string.weather_rain_toast)
                WeatherBiome.SNOWY -> appContext.getString(R.string.weather_snow_toast)
                WeatherBiome.CLEAR_MIST -> null
            }
            weatherBannerTimer = 3.0f
        }
        val targetBlend = when (world.weather) {
            WeatherBiome.CLEAR_MIST -> 0f
            WeatherBiome.RAINY -> 1f
            WeatherBiome.SNOWY -> 2f
        }
        world.weatherBlend += (targetBlend - world.weatherBlend) * min(1f, dt * 0.75f)

        // Spawn next obstacle wave
        val waveSpacing = (7.0f - (world.speed - BASE_SPEED) * 0.22f).coerceAtLeast(4.7f)
        if (spawnDistanceAccumulator >= waveSpacing) {
            spawnDistanceAccumulator = 0f
            spawnNextWave(z = FAR_Z, currentDistance = newDistance, weather = world.weather)
        }

        // Step scenery
        stepPropsInPlace(dt)
        stepCloudsInPlace(dt, world.speed * 0.16f)
        stepMotesInPlace(dt, world.speed)
        stepWeatherParticlesInPlace(dt)

        // Body collision handling
        if (bodyHit && world.invulnTimer <= 0f) {
            hudHp -= 1
            if (hudHp <= 0) {
                handleGameOver(finalScore = totalScore, finalDistance = newDistance)
                return
            } else {
                world.invulnTimer = 1.45f
                world.hitBannerTimer = 1.4f
                world.screenShake = 0.72f
                audioEngine.playSfx(SoundEffect.HIT_DAMAGE)
                repeat(10) { i ->
                    val a = (i / 10f) * 2f * PI.toFloat()
                    world.particles.add(
                        FeatherParticle(
                            x = world.crowX,
                            y = world.crowY,
                            vx = cos(a) * 0.9f,
                            vy = sin(a) * 0.9f,
                            angle = rng.nextFloat() * 360f,
                            vAngle = 300f,
                            size = 0.7f,
                            life = 0.8f,
                            maxLife = 0.8f,
                            isEmber = world.equippedSkin == CreatureSkin.BABY_DRAGON,
                        )
                    )
                }
            }
        }

        // Automatic Bonus HP Reward on good distance progress!
        if (newDistance >= nextHpRewardDistance) {
            nextHpRewardDistance += HP_REWARD_INTERVAL_DIST
            val maxAllowedHp = world.equippedSkin.maxHp + 2
            if (hudHp < maxAllowedHp) {
                hudHp += 1
                hudMaxHp = max(hudMaxHp, hudHp)
                hudBonusHpTimer = 2.6f
                audioEngine.playSfx(SoundEffect.BONUS_HP)
                repeat(10) { i ->
                    val a = (i / 10f) * 2f * PI.toFloat()
                    world.particles.add(
                        FeatherParticle(
                            x = world.crowX,
                            y = world.crowY,
                            vx = cos(a) * 0.75f,
                            vy = sin(a) * 0.75f - 0.15f,
                            angle = 0f,
                            vAngle = 0f,
                            size = 0.65f,
                            life = 0.65f,
                            maxLife = 0.65f,
                            isGoldSpark = true,
                        )
                    )
                }
            }
        }

        hudScore = totalScore
        hudDistance = newDistance
        val newMagnetSecs = ceil(world.magnetPowerTimer).toInt().coerceAtLeast(0)
        hudMagnetSecs = newMagnetSecs

        // Sync lightweight HUD only when discrete HUD values change!
        val curUi = _uiState.value
        if (curUi.score != hudScore ||
            curUi.distanceMeters != hudDistance ||
            curUi.hp != hudHp ||
            curUi.maxHp != hudMaxHp ||
            curUi.runCoins != hudRunCoins ||
            curUi.magnetSecondsLeft != hudMagnetSecs ||
            curUi.weather != world.weather ||
            curUi.weatherBannerText != hudWeatherBannerText ||
            (curUi.nearMissBannerTimer > 0f) != (hudNearMissTimer > 0f) ||
            (curUi.hitBannerTimer > 0f) != (world.hitBannerTimer > 0f) ||
            (curUi.bonusHpBannerTimer > 0f) != (hudBonusHpTimer > 0f)
        ) {
            _uiState.update { s ->
                s.copy(
                    score = hudScore,
                    hp = hudHp,
                    maxHp = hudMaxHp,
                    distanceMeters = hudDistance,
                    runCoins = hudRunCoins,
                    nearMissCount = hudNearMiss,
                    magnetSecondsLeft = hudMagnetSecs,
                    weather = world.weather,
                    weatherBannerText = hudWeatherBannerText,
                    nearMissBannerTimer = hudNearMissTimer,
                    lastNearMissBonus = hudLastNearMissBonus,
                    hitBannerTimer = world.hitBannerTimer,
                    bonusHpBannerTimer = hudBonusHpTimer,
                )
            }
        }
    }

    private fun handleGameOver(finalScore: Int, finalDistance: Int) {
        val prevBest = _uiState.value.bestScore
        val isRecord = finalScore > prevBest
        audioEngine.playSfx(SoundEffect.COLLISION)
        if (isRecord && finalScore > 0) {
            audioEngine.playSfx(SoundEffect.NEW_RECORD)
        }
        audioEngine.setFlightIntensity(0.18f)

        world.phase = GamePhase.GAME_OVER
        world.screenShake = 1.0f
        world.dangerLevel = 0f

        val isDragon = world.equippedSkin == CreatureSkin.BABY_DRAGON
        for (i in 0 until 24) {
            val angle = (i / 24f) * 2f * PI.toFloat() + rng.nextFloat() * 0.3f
            val spd = 0.6f + rng.nextFloat() * 1.4f
            world.particles.add(
                FeatherParticle(
                    x = world.crowX,
                    y = world.crowY,
                    vx = cos(angle) * spd,
                    vy = sin(angle) * spd - 0.25f,
                    angle = rng.nextFloat() * 360f,
                    vAngle = (rng.nextFloat() - 0.5f) * 420f,
                    size = 0.75f + rng.nextFloat() * 0.8f,
                    life = 1.45f,
                    maxLife = 1.45f,
                    isDrop = i % 3 == 0,
                    isEmber = isDragon && i % 2 == 0,
                )
            )
        }

        viewModelScope.launch {
            repository.recordFlightResult(finalScore, finalDistance, hudRunCoins)
        }

        _uiState.update { s ->
            s.copy(
                phase = GamePhase.GAME_OVER,
                hp = 0,
                score = finalScore,
                distanceMeters = finalDistance,
                runCoins = hudRunCoins,
                nearMissCount = hudNearMiss,
                bestScore = max(s.bestScore, finalScore),
                bestDistance = max(s.bestDistance, finalDistance),
                isNewRecord = isRecord && finalScore > 0,
                magnetSecondsLeft = 0,
                weatherBannerText = null,
            )
        }
        frameTick.longValue++
    }

    private fun spawnNextWave(z: Float, currentDistance: Int, weather: WeatherBiome) {
        waveCounter++
        val gapX = (prevGapX + (rng.nextFloat() - 0.5f) * 1.15f).coerceIn(-0.56f, 0.56f)
        val gapY = (prevGapY + (rng.nextFloat() - 0.5f) * 0.85f).coerceIn(-0.36f, 0.36f)
        prevGapX = gapX
        prevGapY = gapY

        val difficulty = (currentDistance / 1900f).coerceIn(0f, 1f)
        val gapHalfW = 0.39f - difficulty * 0.085f
        val gapHalfH = 0.34f - difficulty * 0.065f

        if (weather == WeatherBiome.SNOWY && rng.nextFloat() < 0.62f) {
            // Winter biome obstacles: Hand-drawn Ink Snowmen & Ice Spires matched to sprite aspect ratios!
            val snowmanSide = if (gapX >= 0f) -0.45f - rng.nextFloat() * 0.22f else 0.45f + rng.nextFloat() * 0.22f
            world.obstacles.add(
                Obstacle3D(
                    id = nextId++,
                    kind = ObstacleKind.SNOWMAN,
                    minX = snowmanSide - 0.34f,
                    maxX = snowmanSide + 0.34f,
                    minY = -0.10f,
                    maxY = 0.94f,
                    z = z,
                    seed = rng.nextInt(9999),
                )
            )
            if (rng.nextFloat() < 0.72f) {
                val icicleX = -snowmanSide * 0.95f
                world.obstacles.add(
                    Obstacle3D(
                        id = nextId++,
                        kind = ObstacleKind.ICE_SPIRE,
                        minX = icicleX - 0.24f,
                        maxX = icicleX + 0.24f,
                        minY = -0.95f,
                        maxY = 0.15f,
                        z = z + 0.3f,
                        seed = rng.nextInt(9999),
                    )
                )
            }
        } else {
            val pattern = rng.nextInt(if (currentDistance >= 180) 5 else 4)
            when (pattern) {
                0 -> {
                    val leftEdge = (gapX - gapHalfW).coerceAtLeast(-1.15f)
                    val rightEdge = (gapX + gapHalfW).coerceAtMost(1.15f)
                    if (leftEdge > -0.92f) {
                        val treeW = 0.68f
                        world.obstacles.add(Obstacle3D(nextId++, ObstacleKind.GNARLED_PILLAR, leftEdge - treeW, leftEdge, -0.95f, 0.95f, z, seed = rng.nextInt(9999)))
                        if (leftEdge - treeW > -1.10f) {
                            world.obstacles.add(Obstacle3D(nextId++, ObstacleKind.GNARLED_PILLAR, -1.35f, leftEdge - treeW + 0.12f, -0.95f, 0.95f, z, seed = rng.nextInt(9999)))
                        }
                    }
                    if (rightEdge < 0.92f) {
                        val treeW = 0.68f
                        world.obstacles.add(Obstacle3D(nextId++, ObstacleKind.GNARLED_PILLAR, rightEdge, rightEdge + treeW, -0.95f, 0.95f, z, seed = rng.nextInt(9999)))
                        if (rightEdge + treeW < 1.10f) {
                            world.obstacles.add(Obstacle3D(nextId++, ObstacleKind.GNARLED_PILLAR, rightEdge + treeW - 0.12f, 1.35f, -0.95f, 0.95f, z, seed = rng.nextInt(9999)))
                        }
                    }
                }

                1 -> {
                    val topEdge = (gapY - gapHalfH).coerceAtLeast(-0.85f)
                    val bottomEdge = (gapY + gapHalfH).coerceAtMost(0.85f)
                    if (topEdge > -0.68f) {
                        world.obstacles.add(Obstacle3D(nextId++, ObstacleKind.OVERHANGING_BOUGH, -1.15f, 1.15f, -0.98f, topEdge, z, seed = rng.nextInt(9999)))
                    }
                    if (bottomEdge < 0.68f) {
                        world.obstacles.add(Obstacle3D(nextId++, ObstacleKind.THORN_BARRIER, -1.15f, 1.15f, bottomEdge, 0.98f, z, seed = rng.nextInt(9999)))
                    }
                }

                2 -> {
                    val leftEdge = (gapX - gapHalfW * 0.95f).coerceAtLeast(-0.85f)
                    val rightEdge = (gapX + gapHalfW * 0.95f).coerceAtMost(0.85f)
                    val topEdge = (gapY - gapHalfH * 0.95f).coerceAtLeast(-0.62f)
                    world.obstacles.add(Obstacle3D(nextId++, ObstacleKind.ARCH_PILLAR, -1.25f, leftEdge, -0.95f, 0.95f, z, seed = rng.nextInt(9999)))
                    world.obstacles.add(Obstacle3D(nextId++, ObstacleKind.ARCH_PILLAR, rightEdge, 1.25f, -0.95f, 0.95f, z, seed = rng.nextInt(9999)))
                    world.obstacles.add(Obstacle3D(nextId++, ObstacleKind.ARCH_LINTEL, leftEdge - 0.05f, rightEdge + 0.05f, -0.95f, topEdge, z, seed = rng.nextInt(9999)))
                }

                3 -> {
                    val blockX = if (gapX >= 0f) -0.35f - rng.nextFloat() * 0.25f else 0.35f + rng.nextFloat() * 0.25f
                    world.obstacles.add(Obstacle3D(nextId++, ObstacleKind.GNARLED_PILLAR, blockX - 0.25f, blockX + 0.25f, -0.95f, 0.95f, z, seed = rng.nextInt(9999)))
                    if (difficulty > 0.3f) {
                        val boughSide = -blockX
                        world.obstacles.add(
                            Obstacle3D(
                                nextId++,
                                ObstacleKind.OVERHANGING_BOUGH,
                                if (boughSide > 0f) 0.1f else -1.2f,
                                if (boughSide > 0f) 1.2f else -0.1f,
                                -0.95f,
                                -0.12f,
                                z + 0.4f,
                                seed = rng.nextInt(9999),
                            )
                        )
                    }
                }

                else -> {
                    val birdY = (gapY + (if (gapY > 0f) -0.32f else 0.32f)).coerceIn(-0.52f, 0.52f)
                    val birdX = (rng.nextFloat() - 0.5f) * 0.8f
                    val dir = if (rng.nextBoolean()) 1f else -1f
                    world.obstacles.add(
                        Obstacle3D(
                            id = nextId++,
                            kind = ObstacleKind.SHADOW_CROW,
                            minX = birdX - 0.17f,
                            maxX = birdX + 0.17f,
                            minY = birdY - 0.08f,
                            maxY = birdY + 0.08f,
                            z = z,
                            halfDepth = 0.26f,
                            lateralVelX = dir * (0.42f + difficulty * 0.32f),
                            seed = rng.nextInt(9999),
                        )
                    )
                    val sidePillarX = if (gapX > 0f) -0.78f else 0.78f
                    world.obstacles.add(Obstacle3D(nextId++, ObstacleKind.ARCH_PILLAR, sidePillarX - 0.21f, sidePillarX + 0.21f, -0.95f, 0.95f, z, seed = rng.nextInt(9999)))
                }
            }
        }

        // Occasionally spawn a 3D Magnet Power-Up inside the safe flight path!
        if (waveCounter % 4 == 2 && world.magnets.isEmpty() && world.magnetPowerTimer <= 1.5f) {
            world.magnets.add(
                MagnetPickup3D(
                    id = nextId++,
                    x = gapX.coerceIn(-0.68f, 0.68f),
                    y = gapY.coerceIn(-0.48f, 0.48f),
                    z = z + 1.4f,
                )
            )
        } else {
            // Spawn trail of 2-3 collectible coins inside safe gap
            val coinCount = 2 + rng.nextInt(2)
            for (c in 0 until coinCount) {
                world.coins.add(
                    Coin3D(
                        id = nextId++,
                        x = (gapX + (rng.nextFloat() - 0.5f) * 0.20f).coerceIn(-0.78f, 0.78f),
                        y = (gapY + (rng.nextFloat() - 0.5f) * 0.16f).coerceIn(-0.55f, 0.55f),
                        z = z + c * 1.1f,
                        spinPhase = c * 0.8f,
                    )
                )
            }
        }
    }

    private fun initWorldScenery() {
        world.groundProps.clear()
        for (idx in 0 until 26) {
            val side = if (idx % 2 == 0) 1f else -1f
            world.groundProps.add(
                GroundProp(
                    id = idx.toLong(),
                    x = side * (0.70f + (idx % 5) * 0.30f),
                    z = 1.1f + (idx / 26f) * FAR_Z,
                    height = 0.25f + (idx % 4) * 0.14f,
                    seed = idx * 313,
                    isGnarledTree = idx % 3 == 0,
                )
            )
        }

        world.skyClouds.clear()
        for (idx in 0 until 8) {
            val side = if (idx % 2 == 0) -1f else 1f
            world.skyClouds.add(
                InkCloud(
                    x = side * (0.4f + (idx % 4) * 0.55f),
                    y = -0.68f - (idx % 3) * 0.16f,
                    z = 3.5f + (idx / 8f) * (FAR_Z - 3.5f),
                    width = 0.55f + (idx % 3) * 0.25f,
                    driftSpeed = if (idx % 2 == 0) 0.06f else -0.05f,
                    seed = idx * 179,
                )
            )
        }

        world.inkMotes.clear()
        for (i in 0 until 32) {
            world.inkMotes.add(
                InkMote(
                    x = (rng.nextFloat() - 0.5f) * 2.5f,
                    y = (rng.nextFloat() - 0.5f) * 1.9f,
                    z = 1.4f + rng.nextFloat() * (FAR_Z - 2f),
                    size = 1.6f + rng.nextFloat() * 3.5f,
                    speedFactor = 0.7f + rng.nextFloat() * 0.65f,
                )
            )
        }

        world.weatherParticles.clear()
        for (i in 0 until 75) {
            world.weatherParticles.add(
                WeatherParticle(
                    x = rng.nextFloat(),
                    y = rng.nextFloat(),
                    speedY = 0.6f + rng.nextFloat() * 0.8f,
                    speedX = -0.15f + rng.nextFloat() * 0.1f,
                    length = 0.025f + rng.nextFloat() * 0.03f,
                    phase = rng.nextFloat() * 6.28f,
                )
            )
        }
    }

    private fun stepPropsInPlace(dt: Float) {
        for (i in 0 until world.groundProps.size) {
            val prop = world.groundProps[i]
            prop.z -= world.speed * dt
            if (prop.z < -0.5f) {
                val side = if (rng.nextBoolean()) 1f else -1f
                prop.x = side * (0.75f + rng.nextFloat() * 1.55f)
                prop.z = FAR_Z + rng.nextFloat() * 2f
                prop.height = 0.24f + rng.nextFloat() * 0.58f
                prop.seed = rng.nextInt(10000)
                prop.isGnarledTree = rng.nextFloat() < 0.45f
            }
        }
    }

    private fun stepCloudsInPlace(dt: Float, forwardSpeed: Float) {
        for (i in 0 until world.skyClouds.size) {
            val c = world.skyClouds[i]
            c.x += c.driftSpeed * dt
            if (c.x > 2.4f) c.x = -2.4f else if (c.x < -2.4f) c.x = 2.4f
            c.z -= forwardSpeed * dt
            if (c.z < 1.5f) {
                c.x = (rng.nextFloat() - 0.5f) * 3.6f
                c.z = FAR_Z
            }
        }
    }

    private fun stepMotesInPlace(dt: Float, speed: Float) {
        for (i in 0 until world.inkMotes.size) {
            val m = world.inkMotes[i]
            m.z -= speed * m.speedFactor * dt
            if (m.z < 0.8f) {
                m.x = (rng.nextFloat() - 0.5f) * 2.5f
                m.y = (rng.nextFloat() - 0.5f) * 1.9f
                m.z = FAR_Z
            }
        }
    }

    private fun stepWeatherParticlesInPlace(dt: Float) {
        if (world.weatherBlend < 0.05f) return
        val isSnow = world.weatherBlend >= 1.25f
        for (i in 0 until world.weatherParticles.size) {
            val wp = world.weatherParticles[i]
            wp.phase += dt * 3.5f
            if (isSnow) {
                wp.y += wp.speedY * 0.34f * dt
                wp.x += (sin(wp.phase) * 0.09f - 0.04f) * dt
            } else {
                wp.y += wp.speedY * 2.1f * dt
                wp.x += wp.speedX * 1.4f * dt
            }
            if (wp.y > 1.05f) {
                wp.y = -0.05f
                wp.x = rng.nextFloat()
            }
            if (wp.x < -0.05f) wp.x += 1.1f else if (wp.x > 1.05f) wp.x -= 1.1f
        }
    }

    private fun stepParticlesInPlace(dt: Float) {
        for (i in world.particles.size - 1 downTo 0) {
            val p = world.particles[i]
            p.life -= dt
            if (p.life <= 0f) {
                world.particles.removeAt(i)
            } else {
                p.x += p.vx * dt
                p.y += p.vy * dt + 0.12f * dt
                p.vy += 0.55f * dt
                p.angle += p.vAngle * dt
            }
        }
    }

    fun resumeAudio() = audioEngine.onResume()
    fun pauseAudio() = audioEngine.onPause()

    override fun onCleared() {
        super.onCleared()
        audioEngine.release()
    }
}

/**
 * Exact shape-matched collider test against the visible silhouette of each obstacle kind:
 * - SNOWMAN: 3 stacked snowballs + top hat (empty corner air beside head/hat never collides)
 * - ICE_SPIRE: triangular crystal taper from base to tip
 * - GNARLED_PILLAR: flared roots + solid central trunk + upper branch crown
 * - SHADOW_CROW: central torso only (wingtips don't unfairly deal body damage)
 * - ARCH_PILLAR / ARCH_LINTEL / OVERHANGING_BOUGH / THORN_BARRIER: exact rectangular bounds
 */
internal fun doesRectHitObstacleShape(
    rx: Float,
    ry: Float,
    halfW: Float,
    halfH: Float,
    obs: Obstacle3D,
): Boolean {
    val rMinX = rx - halfW
    val rMaxX = rx + halfW
    val rMinY = ry - halfH
    val rMaxY = ry + halfH

    if (rMaxX <= obs.minX || rMinX >= obs.maxX || rMaxY <= obs.minY || rMinY >= obs.maxY) {
        return false
    }

    val w = (obs.maxX - obs.minX).coerceAtLeast(0.01f)
    val h = (obs.maxY - obs.minY).coerceAtLeast(0.01f)
    val cx = (obs.minX + obs.maxX) * 0.5f
    val cy = (obs.minY + obs.maxY) * 0.5f

    return when (obs.kind) {
        ObstacleKind.SNOWMAN -> {
            val hitBottom = doesRectHitEllipse(rMinX, rMinY, rMaxX, rMaxY, cx, obs.maxY - h * 0.23f, w * 0.38f, h * 0.23f)
            val hitMid = doesRectHitEllipse(rMinX, rMinY, rMaxX, rMaxY, cx, obs.maxY - h * 0.56f, w * 0.28f, h * 0.16f)
            val hitHead = doesRectHitEllipse(rMinX, rMinY, rMaxX, rMaxY, cx, obs.maxY - h * 0.79f, w * 0.20f, h * 0.12f)
            val hatHalfW = w * 0.16f
            val hitHat = rMaxX > cx - hatHalfW && rMinX < cx + hatHalfW && rMinY < obs.minY + h * 0.16f
            hitBottom || hitMid || hitHead || hitHat
        }

        ObstacleKind.ICE_SPIRE -> {
            val closestY = ry.coerceIn(obs.minY, obs.maxY)
            val hangingFromTop = obs.minY < -0.5f
            val tFromTip = if (hangingFromTop) {
                ((obs.maxY - closestY) / h).coerceIn(0f, 1f)
            } else {
                ((closestY - obs.minY) / h).coerceIn(0f, 1f)
            }
            val spireHalfW = (w * 0.5f) * (0.14f + 0.78f * tFromTip)
            abs(rx - cx) < spireHalfW + halfW
        }

        ObstacleKind.GNARLED_PILLAR -> {
            val closestY = ry.coerceIn(obs.minY, obs.maxY)
            val tFromBottom = ((obs.maxY - closestY) / h).coerceIn(0f, 1f)
            val treeHalfW = when {
                tFromBottom < 0.18f -> w * 0.42f // Flared roots
                tFromBottom < 0.65f -> w * 0.26f // Central solid trunk
                else -> w * 0.36f                // Upper gnarled branches
            }
            abs(rx - cx) < treeHalfW + halfW
        }

        ObstacleKind.SHADOW_CROW -> {
            // Only the central crow torso (not open outer wingtips)
            val torsoHalfW = w * 0.22f
            val torsoHalfH = h * 0.36f
            rMaxX > cx - torsoHalfW && rMinX < cx + torsoHalfW && rMaxY > cy - torsoHalfH && rMinY < cy + torsoHalfH
        }

        ObstacleKind.ARCH_PILLAR,
        ObstacleKind.ARCH_LINTEL,
        ObstacleKind.OVERHANGING_BOUGH,
        ObstacleKind.THORN_BARRIER -> true
    }
}

private fun doesRectHitEllipse(
    rMinX: Float,
    rMinY: Float,
    rMaxX: Float,
    rMaxY: Float,
    ex: Float,
    ey: Float,
    rx: Float,
    ry: Float,
): Boolean {
    val nearestX = ex.coerceIn(rMinX, rMaxX)
    val nearestY = ey.coerceIn(rMinY, rMaxY)
    val dx = (nearestX - ex) / rx.coerceAtLeast(0.001f)
    val dy = (nearestY - ey) / ry.coerceAtLeast(0.001f)
    return (dx * dx + dy * dy) <= 1f
}
