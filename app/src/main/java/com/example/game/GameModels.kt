package com.example.game

import androidx.annotation.DrawableRes
import androidx.annotation.StringRes
import androidx.compose.runtime.Immutable
import com.example.R

enum class GamePhase {
    MENU,
    LAUNCHING,
    PLAYING,
    PAUSED,
    GAME_OVER,
}

enum class WeatherBiome {
    CLEAR_MIST,
    RAINY,
    SNOWY,
}

enum class CreatureSkin(
    val id: String,
    @StringRes val nameRes: Int,
    @StringRes val descRes: Int,
    val priceCoins: Int,
    val maxHp: Int,
    val agilityBonus: Float,
    @DrawableRes val menuDrawableRes: Int,
    val menuBgColorHex: Long,
) {
    CROW(
        id = "crow",
        nameRes = R.string.skin_crow_name,
        descRes = R.string.skin_crow_desc,
        priceCoins = 0,
        maxHp = 1,
        agilityBonus = 0f,
        menuDrawableRes = R.drawable.menu_illustration,
        menuBgColorHex = 0xFFECE4D1,
    ),
    FALCON(
        id = "falcon",
        nameRes = R.string.skin_falcon_name,
        descRes = R.string.skin_falcon_desc,
        priceCoins = 450,
        maxHp = 2,
        agilityBonus = 0.08f,
        menuDrawableRes = R.drawable.menu_falcon_1795305236205,
        menuBgColorHex = 0xFFEEE2CA,
    ),
    EAGLE(
        id = "eagle",
        nameRes = R.string.skin_eagle_name,
        descRes = R.string.skin_eagle_desc,
        priceCoins = 1200,
        maxHp = 3,
        agilityBonus = 0.14f,
        menuDrawableRes = R.drawable.menu_eagle_1795305236780,
        menuBgColorHex = 0xFFEFE3CB,
    ),
    PEACOCK(
        id = "peacock",
        nameRes = R.string.skin_peacock_name,
        descRes = R.string.skin_peacock_desc,
        priceCoins = 2800,
        maxHp = 4,
        agilityBonus = 0.18f,
        menuDrawableRes = R.drawable.menu_peacock_1795305235603,
        menuBgColorHex = 0xFFEFE3CD,
    ),
    BABY_DRAGON(
        id = "baby_dragon",
        nameRes = R.string.skin_dragon_name,
        descRes = R.string.skin_dragon_desc,
        priceCoins = 6000,
        maxHp = 5,
        agilityBonus = 0.25f,
        menuDrawableRes = R.drawable.menu_dragon_1795305235229,
        menuBgColorHex = 0xFFEFE3CD,
    );

    companion object {
        fun fromId(id: String): CreatureSkin = entries.firstOrNull { it.id == id } ?: CROW
    }
}

enum class UpgradeType(
    @StringRes val nameRes: Int,
    @StringRes val descRes: Int,
    val costs: List<Int>,
) {
    MAGNET(
        nameRes = R.string.upgrade_magnet_name,
        descRes = R.string.upgrade_magnet_desc,
        costs = listOf(75, 180, 340, 560, 880),
    ),
    BOUNTY(
        nameRes = R.string.upgrade_bounty_name,
        descRes = R.string.upgrade_bounty_desc,
        costs = listOf(90, 210, 400, 660, 1020),
    ),
    AGILITY(
        nameRes = R.string.upgrade_agility_name,
        descRes = R.string.upgrade_agility_desc,
        costs = listOf(65, 160, 300, 500, 780),
    );

    fun nextCost(currentLevel: Int): Int? = costs.getOrNull(currentLevel)
}

enum class ObstacleKind {
    GNARLED_PILLAR,
    OVERHANGING_BOUGH,
    ARCH_PILLAR,
    ARCH_LINTEL,
    THORN_BARRIER,
    SHADOW_CROW,
    SNOWMAN,
    ICE_SPIRE,
}

class Obstacle3D(
    var id: Long,
    var kind: ObstacleKind,
    var minX: Float,
    var maxX: Float,
    var minY: Float,
    var maxY: Float,
    var z: Float,
    var halfDepth: Float = 0.34f,
    var lateralVelX: Float = 0f,
    var seed: Int = 0,
    var passed: Boolean = false,
    var nearMissAwarded: Boolean = false,
    var wingGrazed: Boolean = false,
)

class Coin3D(
    var id: Long,
    var x: Float,
    var y: Float,
    var z: Float,
    var spinPhase: Float = 0f,
)

class MagnetPickup3D(
    var id: Long,
    var x: Float,
    var y: Float,
    var z: Float,
    var spinPhase: Float = 0f,
)

class GroundProp(
    var id: Long,
    var x: Float,
    var z: Float,
    var height: Float,
    var seed: Int,
    var isGnarledTree: Boolean,
)

class InkCloud(
    var x: Float,
    var y: Float,
    var z: Float,
    var width: Float,
    var driftSpeed: Float,
    var seed: Int,
)

class InkMote(
    var x: Float,
    var y: Float,
    var z: Float,
    var size: Float,
    var speedFactor: Float,
)

class WeatherParticle(
    var x: Float,
    var y: Float,
    var speedY: Float,
    var speedX: Float,
    var length: Float,
    var phase: Float,
)

class FeatherParticle(
    var x: Float,
    var y: Float,
    var vx: Float,
    var vy: Float,
    var angle: Float,
    var vAngle: Float,
    var size: Float,
    var life: Float,
    var maxLife: Float,
    var isDrop: Boolean = false,
    var isEmber: Boolean = false,
    var isGoldSpark: Boolean = false,
    var isMagnetSpark: Boolean = false,
)

/**
 * Mutable high-frequency simulation state read directly by InkFlightCanvas at up to 120 FPS
 * to eliminate GC allocations and prevent Compose HUD recomposition during flight.
 */
class FlightWorldState {
    @Volatile
    var spriteAtlas: InkSpriteAtlas? = null

    var phase: GamePhase = GamePhase.MENU
    var equippedSkin: CreatureSkin = CreatureSkin.CROW
    var weather: WeatherBiome = WeatherBiome.CLEAR_MIST
    var weatherBlend: Float = 0f // 0=clear, 1=rain, 2=snow

    var launchProgress: Float = 0f
    var crowX: Float = 0f
    var crowY: Float = 0f
    var targetX: Float = 0f
    var targetY: Float = 0f
    var crowVelX: Float = 0f
    var crowVelY: Float = 0f
    var cameraX: Float = 0f
    var cameraY: Float = 0f
    var bankAngle: Float = 0f
    var pitchAngle: Float = 0f
    var flapPhase: Float = 0f
    var envTime: Float = 0f
    var speed: Float = 6.1f

    var invulnTimer: Float = 0f
    var magnetPowerTimer: Float = 0f
    var hitBannerTimer: Float = 0f
    var screenShake: Float = 0f
    var dangerLevel: Float = 0f

    val obstacles = ArrayList<Obstacle3D>(32)
    val coins = ArrayList<Coin3D>(32)
    val magnets = ArrayList<MagnetPickup3D>(8)
    val groundProps = ArrayList<GroundProp>(28)
    val skyClouds = ArrayList<InkCloud>(10)
    val inkMotes = ArrayList<InkMote>(36)
    val weatherParticles = ArrayList<WeatherParticle>(90)
    val particles = ArrayList<FeatherParticle>(48)
}

/**
 * Discrete UI/HUD state observed by Compose screens (only updates when values change).
 */
@Immutable
data class GameUiState(
    val phase: GamePhase = GamePhase.MENU,
    val weather: WeatherBiome = WeatherBiome.CLEAR_MIST,
    val weatherBannerText: String? = null,
    val hp: Int = 1,
    val maxHp: Int = 1,
    val distanceMeters: Int = 0,
    val score: Int = 0,
    val runCoins: Int = 0,
    val totalCoins: Int = 0,
    val nearMissCount: Int = 0,
    val bestScore: Int = 0,
    val bestDistance: Int = 0,
    val totalFlights: Int = 0,
    val isNewRecord: Boolean = false,
    val magnetSecondsLeft: Int = 0,
    val nearMissBannerTimer: Float = 0f,
    val lastNearMissBonus: Int = 0,
    val hitBannerTimer: Float = 0f,
    val bonusHpBannerTimer: Float = 0f,
    val equippedSkin: CreatureSkin = CreatureSkin.CROW,
    val unlockedSkins: Set<CreatureSkin> = setOf(CreatureSkin.CROW),
    val magnetLevel: Int = 0,
    val bountyLevel: Int = 0,
    val agilityLevel: Int = 0,
    val sfxEnabled: Boolean = true,
    val ambientEnabled: Boolean = true,
    val masterVolume: Float = 0.85f,
    val showSoundDialog: Boolean = false,
    val showShopDialog: Boolean = false,
)
