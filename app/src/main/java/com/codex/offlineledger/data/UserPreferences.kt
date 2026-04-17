package com.codex.offlineledger.data

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.codex.offlineledger.domain.CurrencyUnit
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

private val Context.dataStore: DataStore<Preferences> by preferencesDataStore(name = "user_prefs")

enum class ThemeMode { SYSTEM, LIGHT, DARK }

class UserPreferences(private val context: Context) {

    private val themeModeKey = stringPreferencesKey("theme_mode")
    private val unitAssetsKey = stringPreferencesKey("unit_assets")
    private val unitGiftsKey = stringPreferencesKey("unit_gifts")

    val themeMode: Flow<ThemeMode> = context.dataStore.data.map { prefs ->
        prefs[themeModeKey]?.let { runCatching { ThemeMode.valueOf(it) }.getOrNull() } ?: ThemeMode.SYSTEM
    }

    val assetsUnit: Flow<CurrencyUnit> = context.dataStore.data.map { prefs ->
        prefs[unitAssetsKey]?.let { runCatching { CurrencyUnit.valueOf(it) }.getOrNull() } ?: CurrencyUnit.THOUSAND
    }

    val giftsUnit: Flow<CurrencyUnit> = context.dataStore.data.map { prefs ->
        prefs[unitGiftsKey]?.let { runCatching { CurrencyUnit.valueOf(it) }.getOrNull() } ?: CurrencyUnit.THOUSAND
    }

    suspend fun setThemeMode(mode: ThemeMode) {
        context.dataStore.edit { it[themeModeKey] = mode.name }
    }

    suspend fun setAssetsUnit(unit: CurrencyUnit) {
        context.dataStore.edit { it[unitAssetsKey] = unit.name }
    }

    suspend fun setGiftsUnit(unit: CurrencyUnit) {
        context.dataStore.edit { it[unitGiftsKey] = unit.name }
    }

    suspend fun clear() {
        context.dataStore.edit { it.clear() }
    }
}
