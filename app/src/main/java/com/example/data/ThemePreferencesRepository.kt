package com.example.data

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.emptyPreferences
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.example.ui.theme.AppThemeMode
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.map
import java.io.IOException

val Context.zScanDataStore: DataStore<Preferences> by preferencesDataStore(name = "zscan_user_preferences")

data class UserSettingsState(
    val themeMode: AppThemeMode = AppThemeMode.SYSTEM,
    val layoutMode: DocumentLayoutMode = DocumentLayoutMode.GRID,
    val sortOption: DocumentSortOption = DocumentSortOption.NEWEST_FIRST,
    val autoCropPaper: Boolean = true
)

class ThemePreferencesRepository(private val dataStore: DataStore<Preferences>) {

    private object Keys {
        val THEME_MODE = stringPreferencesKey("theme_mode")
        val LAYOUT_MODE = stringPreferencesKey("layout_mode")
        val SORT_OPTION = stringPreferencesKey("sort_option")
        val AUTO_CROP_PAPER = booleanPreferencesKey("auto_crop_paper")
    }

    val userSettingsFlow: Flow<UserSettingsState> = dataStore.data
        .catch { exception ->
            if (exception is IOException) {
                emit(emptyPreferences())
            } else {
                throw exception
            }
        }
        .map { prefs ->
            val themeMode = prefs[Keys.THEME_MODE]?.let {
                runCatching { AppThemeMode.valueOf(it) }.getOrNull()
            } ?: AppThemeMode.SYSTEM

            val layoutMode = prefs[Keys.LAYOUT_MODE]?.let {
                runCatching { DocumentLayoutMode.valueOf(it) }.getOrNull()
            } ?: DocumentLayoutMode.GRID

            val sortOption = prefs[Keys.SORT_OPTION]?.let {
                runCatching { DocumentSortOption.valueOf(it) }.getOrNull()
            } ?: DocumentSortOption.NEWEST_FIRST

            val autoCropPaper = prefs[Keys.AUTO_CROP_PAPER] ?: true

            UserSettingsState(
                themeMode = themeMode,
                layoutMode = layoutMode,
                sortOption = sortOption,
                autoCropPaper = autoCropPaper
            )
        }

    suspend fun setThemeMode(mode: AppThemeMode) {
        dataStore.edit { prefs ->
            prefs[Keys.THEME_MODE] = mode.name
        }
    }

    suspend fun setLayoutMode(mode: DocumentLayoutMode) {
        dataStore.edit { prefs ->
            prefs[Keys.LAYOUT_MODE] = mode.name
        }
    }

    suspend fun setSortOption(option: DocumentSortOption) {
        dataStore.edit { prefs ->
            prefs[Keys.SORT_OPTION] = option.name
        }
    }

    suspend fun setAutoCropPaper(enabled: Boolean) {
        dataStore.edit { prefs ->
            prefs[Keys.AUTO_CROP_PAPER] = enabled
        }
    }
}
