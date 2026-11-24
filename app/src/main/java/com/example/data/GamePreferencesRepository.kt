package com.example.data

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.floatPreferencesKey
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.core.stringSetPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.example.game.CreatureSkin
import com.example.game.UpgradeType
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

private val Context.gameDataStore: DataStore<Preferences> by preferencesDataStore(name = "crow_flight_prefs")

data class StoredGameData(
    val bestScore: Int = 0,
    val bestDistance: Int = 0,
    val totalFlights: Int = 0,
    val coins: Int = 0,
    val equippedSkin: CreatureSkin = CreatureSkin.CROW,
    val unlockedSkins: Set<CreatureSkin> = setOf(CreatureSkin.CROW),
    val magnetLevel: Int = 0,
    val bountyLevel: Int = 0,
    val agilityLevel: Int = 0,
    val sfxEnabled: Boolean = true,
    val ambientEnabled: Boolean = true,
    val masterVolume: Float = 0.85f,
)

class GamePreferencesRepository(private val context: Context) {

    private object Keys {
        val BEST_SCORE = intPreferencesKey("best_score")
        val BEST_DISTANCE = intPreferencesKey("best_distance")
        val TOTAL_FLIGHTS = intPreferencesKey("total_flights")
        val COINS = intPreferencesKey("coins_balance")
        val EQUIPPED_SKIN = stringPreferencesKey("equipped_skin")
        val UNLOCKED_SKINS = stringSetPreferencesKey("unlocked_skins")
        val MAGNET_LEVEL = intPreferencesKey("upgrade_magnet_level")
        val BOUNTY_LEVEL = intPreferencesKey("upgrade_bounty_level")
        val AGILITY_LEVEL = intPreferencesKey("upgrade_agility_level")
        val SFX_ENABLED = booleanPreferencesKey("sfx_enabled")
        val AMBIENT_ENABLED = booleanPreferencesKey("ambient_enabled")
        val MASTER_VOLUME = floatPreferencesKey("master_volume")
    }

    val gameDataFlow: Flow<StoredGameData> = context.gameDataStore.data.map { prefs ->
        val unlockedIds = prefs[Keys.UNLOCKED_SKINS] ?: setOf(CreatureSkin.CROW.id)
        val unlocked = unlockedIds.map { CreatureSkin.fromId(it) }.toSet() + CreatureSkin.CROW
        val equipped = CreatureSkin.fromId(prefs[Keys.EQUIPPED_SKIN] ?: CreatureSkin.CROW.id)
        StoredGameData(
            bestScore = prefs[Keys.BEST_SCORE] ?: 0,
            bestDistance = prefs[Keys.BEST_DISTANCE] ?: 0,
            totalFlights = prefs[Keys.TOTAL_FLIGHTS] ?: 0,
            coins = prefs[Keys.COINS] ?: 0,
            equippedSkin = if (equipped in unlocked) equipped else CreatureSkin.CROW,
            unlockedSkins = unlocked,
            magnetLevel = (prefs[Keys.MAGNET_LEVEL] ?: 0).coerceIn(0, 5),
            bountyLevel = (prefs[Keys.BOUNTY_LEVEL] ?: 0).coerceIn(0, 5),
            agilityLevel = (prefs[Keys.AGILITY_LEVEL] ?: 0).coerceIn(0, 5),
            sfxEnabled = prefs[Keys.SFX_ENABLED] ?: true,
            ambientEnabled = prefs[Keys.AMBIENT_ENABLED] ?: true,
            masterVolume = prefs[Keys.MASTER_VOLUME] ?: 0.85f,
        )
    }

    suspend fun recordFlightResult(score: Int, distanceMeters: Int, coinsEarned: Int) {
        context.gameDataStore.edit { prefs ->
            val prevBest = prefs[Keys.BEST_SCORE] ?: 0
            if (score > prevBest) {
                prefs[Keys.BEST_SCORE] = score
            }
            val prevDist = prefs[Keys.BEST_DISTANCE] ?: 0
            if (distanceMeters > prevDist) {
                prefs[Keys.BEST_DISTANCE] = distanceMeters
            }
            prefs[Keys.TOTAL_FLIGHTS] = (prefs[Keys.TOTAL_FLIGHTS] ?: 0) + 1
            prefs[Keys.COINS] = (prefs[Keys.COINS] ?: 0) + coinsEarned.coerceAtLeast(0)
        }
    }

    suspend fun purchaseOrEquipSkin(skin: CreatureSkin): Boolean {
        var success = false
        context.gameDataStore.edit { prefs ->
            val unlocked = (prefs[Keys.UNLOCKED_SKINS] ?: setOf(CreatureSkin.CROW.id)).toMutableSet()
            unlocked.add(CreatureSkin.CROW.id)
            if (skin.id in unlocked) {
                prefs[Keys.EQUIPPED_SKIN] = skin.id
                success = true
            } else {
                val balance = prefs[Keys.COINS] ?: 0
                if (balance >= skin.priceCoins) {
                    prefs[Keys.COINS] = balance - skin.priceCoins
                    unlocked.add(skin.id)
                    prefs[Keys.UNLOCKED_SKINS] = unlocked
                    prefs[Keys.EQUIPPED_SKIN] = skin.id
                    success = true
                }
            }
        }
        return success
    }

    suspend fun purchaseUpgrade(type: UpgradeType): Boolean {
        var success = false
        context.gameDataStore.edit { prefs ->
            val key = when (type) {
                UpgradeType.MAGNET -> Keys.MAGNET_LEVEL
                UpgradeType.BOUNTY -> Keys.BOUNTY_LEVEL
                UpgradeType.AGILITY -> Keys.AGILITY_LEVEL
            }
            val currentLevel = (prefs[key] ?: 0).coerceIn(0, 5)
            val cost = type.nextCost(currentLevel) ?: return@edit
            val balance = prefs[Keys.COINS] ?: 0
            if (balance >= cost) {
                prefs[Keys.COINS] = balance - cost
                prefs[key] = currentLevel + 1
                success = true
            }
        }
        return success
    }

    suspend fun setSfxEnabled(enabled: Boolean) {
        context.gameDataStore.edit { prefs -> prefs[Keys.SFX_ENABLED] = enabled }
    }

    suspend fun setAmbientEnabled(enabled: Boolean) {
        context.gameDataStore.edit { prefs -> prefs[Keys.AMBIENT_ENABLED] = enabled }
    }

    suspend fun setMasterVolume(volume: Float) {
        context.gameDataStore.edit { prefs -> prefs[Keys.MASTER_VOLUME] = volume.coerceIn(0f, 1f) }
    }
}
