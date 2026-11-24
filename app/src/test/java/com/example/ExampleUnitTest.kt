package com.example

import com.example.game.CreatureSkin
import com.example.game.FlightWorldState
import com.example.game.GamePhase
import com.example.game.GameUiState
import com.example.game.GameViewModel
import com.example.game.MagnetPickup3D
import com.example.game.Obstacle3D
import com.example.game.ObstacleKind
import com.example.game.UpgradeType
import com.example.game.WeatherBiome
import com.example.game.doesRectHitObstacleShape
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ExampleUnitTest {
    @Test
    fun initialGameStateDefaultsAreValid() {
        val state = GameUiState()
        assertEquals(GamePhase.MENU, state.phase)
        assertEquals(CreatureSkin.CROW, state.equippedSkin)
        assertEquals(1, state.maxHp)
        assertEquals(0, state.score)
        assertEquals(0, state.bestScore)
        assertEquals(WeatherBiome.CLEAR_MIST, state.weather)
        assertEquals(0, state.magnetSecondsLeft)
        assertFalse(state.isNewRecord)
    }

    @Test
    fun skinsScaleInPriceAndHealthAscending() {
        val skins = CreatureSkin.entries
        assertEquals(listOf(CreatureSkin.CROW, CreatureSkin.FALCON, CreatureSkin.EAGLE, CreatureSkin.PEACOCK, CreatureSkin.BABY_DRAGON), skins)
        for (i in 1 until skins.size) {
            assertTrue(skins[i].priceCoins > skins[i - 1].priceCoins)
            assertTrue(skins[i].maxHp > skins[i - 1].maxHp)
        }
        assertEquals(1, CreatureSkin.CROW.maxHp)
        assertEquals(5, CreatureSkin.BABY_DRAGON.maxHp)
        assertEquals(450, CreatureSkin.FALCON.priceCoins)
        assertEquals(6000, CreatureSkin.BABY_DRAGON.priceCoins)
        assertTrue(GameViewModel.RAIN_DISTANCE_THRESHOLD >= 800)
        assertTrue(GameViewModel.SNOW_DISTANCE_THRESHOLD > GameViewModel.RAIN_DISTANCE_THRESHOLD)
    }

    @Test
    fun upgradeCostsAreStrictAndCappedAtLevel5() {
        UpgradeType.entries.forEach { upgrade ->
            assertEquals(5, upgrade.costs.size)
            assertEquals(null, upgrade.nextCost(5))
            for (lvl in 1 until 5) {
                assertTrue(upgrade.costs[lvl] > upgrade.costs[lvl - 1])
            }
        }
    }

    @Test
    fun obstacle3DBoundsAndWinterObstaclesAreConsistent() {
        val obs = Obstacle3D(
            id = 1L,
            kind = ObstacleKind.GNARLED_PILLAR,
            minX = -0.3f,
            maxX = 0.3f,
            minY = -0.9f,
            maxY = 0.9f,
            z = 12f,
        )
        assertEquals(0.6f, obs.maxX - obs.minX, 0.001f)
        assertEquals(1.8f, obs.maxY - obs.minY, 0.001f)
        assertTrue(ObstacleKind.SNOWMAN in ObstacleKind.entries)
        assertTrue(ObstacleKind.ICE_SPIRE in ObstacleKind.entries)
    }

    @Test
    fun flightWorldAndMagnetDefaultsAreValid() {
        val world = FlightWorldState()
        assertEquals(GamePhase.MENU, world.phase)
        assertEquals(WeatherBiome.CLEAR_MIST, world.weather)
        assertEquals(0f, world.magnetPowerTimer, 0.001f)

        val magnet = MagnetPickup3D(id = 42L, x = 0.1f, y = -0.1f, z = 14f)
        assertEquals(14f, magnet.z, 0.001f)
        assertEquals(0f, magnet.spinPhase, 0.001f)
    }

    @Test
    fun shapeMatchedCollidersRespectSilhouettes() {
        val snowman = Obstacle3D(1L, ObstacleKind.SNOWMAN, -0.34f, 0.34f, -0.10f, 0.94f, 1.65f)
        // Center belly hits, empty corner beside hat/head does NOT hit
        assertTrue(doesRectHitObstacleShape(0f, 0.70f, 0.038f, 0.034f, snowman))
        assertFalse(doesRectHitObstacleShape(-0.26f, -0.02f, 0.038f, 0.034f, snowman))

        val icicle = Obstacle3D(2L, ObstacleKind.ICE_SPIRE, -0.24f, 0.24f, -0.95f, 0.15f, 1.65f)
        // Near narrow tip (y=0.10f) off-center misses, near wide base (y=-0.85f) hits
        assertFalse(doesRectHitObstacleShape(0.18f, 0.10f, 0.038f, 0.034f, icicle))
        assertTrue(doesRectHitObstacleShape(0.18f, -0.85f, 0.038f, 0.034f, icicle))

        val shadowCrow = Obstacle3D(3L, ObstacleKind.SHADOW_CROW, -0.20f, 0.20f, -0.08f, 0.08f, 1.65f)
        assertFalse(doesRectHitObstacleShape(0.14f, 0f, 0.038f, 0.034f, shadowCrow))
        assertTrue(doesRectHitObstacleShape(0.02f, 0f, 0.038f, 0.034f, shadowCrow))
    }
}
