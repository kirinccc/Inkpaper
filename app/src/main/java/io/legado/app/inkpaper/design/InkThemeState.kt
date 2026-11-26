package io.legado.app.inkpaper.design

import android.content.Context
import androidx.core.content.edit
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import splitties.init.appCtx

enum class InkThemePreset(
    val key: String,
    val label: String,
    val isDark: Boolean,
) {
    PAPER("paper", "素纸", false),
    MIST("mist", "冷雾", false),
    INK_NIGHT("ink_night", "墨夜", true),
    AMOLED("amoled", "纯黑", true);

    companion object {
        fun fromKey(key: String?): InkThemePreset =
            entries.firstOrNull { it.key == key } ?: PAPER
    }
}

data class InkUiPrefs(
    val followSystem: Boolean = true,
    val selectedPreset: InkThemePreset = InkThemePreset.PAPER,
    val darkPreset: InkThemePreset = InkThemePreset.INK_NIGHT,
    val bookshelfGrid: Boolean = true,
    val showContinueCard: Boolean = true,
)

object InkThemeController {

    private const val PREFS_NAME = "inkpaper_prefs"
    private const val KEY_FOLLOW_SYSTEM = "follow_system"
    private const val KEY_SELECTED_PRESET = "selected_preset"
    private const val KEY_DARK_PRESET = "dark_preset"
    private const val KEY_BOOKSHELF_GRID = "bookshelf_grid"
    private const val KEY_SHOW_CONTINUE_CARD = "show_continue_card"

    private val sp by lazy {
        appCtx.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
    }

    private val _prefs = MutableStateFlow(loadPrefs())
    val prefs: StateFlow<InkUiPrefs> = _prefs.asStateFlow()

    private fun loadPrefs(): InkUiPrefs {
        val selected = InkThemePreset.fromKey(sp.getString(KEY_SELECTED_PRESET, InkThemePreset.PAPER.key))
        val dark = InkThemePreset.fromKey(sp.getString(KEY_DARK_PRESET, InkThemePreset.INK_NIGHT.key))
            .takeIf { it.isDark } ?: InkThemePreset.INK_NIGHT
        return InkUiPrefs(
            followSystem = sp.getBoolean(KEY_FOLLOW_SYSTEM, true),
            selectedPreset = selected,
            darkPreset = dark,
            bookshelfGrid = sp.getBoolean(KEY_BOOKSHELF_GRID, true),
            showContinueCard = sp.getBoolean(KEY_SHOW_CONTINUE_CARD, true),
        )
    }

    fun selectPreset(preset: InkThemePreset) {
        _prefs.update { current ->
            val updated = current.copy(
                followSystem = false,
                selectedPreset = preset,
                darkPreset = if (preset.isDark) preset else current.darkPreset,
            )
            sp.edit {
                putBoolean(KEY_FOLLOW_SYSTEM, false)
                putString(KEY_SELECTED_PRESET, preset.key)
                if (preset.isDark) {
                    putString(KEY_DARK_PRESET, preset.key)
                }
            }
            updated
        }
    }

    fun setFollowSystem(enabled: Boolean) {
        _prefs.update { current ->
            sp.edit { putBoolean(KEY_FOLLOW_SYSTEM, enabled) }
            current.copy(followSystem = enabled)
        }
    }

    fun setBookshelfGrid(grid: Boolean) {
        _prefs.update { current ->
            sp.edit { putBoolean(KEY_BOOKSHELF_GRID, grid) }
            current.copy(bookshelfGrid = grid)
        }
    }

    fun setShowContinueCard(show: Boolean) {
        _prefs.update { current ->
            sp.edit { putBoolean(KEY_SHOW_CONTINUE_CARD, show) }
            current.copy(showContinueCard = show)
        }
    }

    fun resolvePreset(systemDark: Boolean): InkThemePreset {
        val current = _prefs.value
        return if (current.followSystem) {
            if (systemDark) current.darkPreset else InkThemePreset.PAPER
        } else {
            current.selectedPreset
        }
    }
}
