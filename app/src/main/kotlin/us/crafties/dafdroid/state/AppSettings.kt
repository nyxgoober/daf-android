package us.crafties.dafdroid.state

import android.content.Context
import androidx.compose.ui.graphics.Color

enum class ThemeMode { SYSTEM, LIGHT, DARK }

enum class AccentColor(val label: String, val seed: Color) {
    BLUE("Blue", Color(0xFF7C9CFF)),
    GREEN("Green", Color(0xFF6FCF97)),
    PURPLE("Purple", Color(0xFFB18AFF)),
    ORANGE("Orange", Color(0xFFFFA45C)),
    PINK("Pink", Color(0xFFFF8FB1)),
    RED("Red", Color(0xFFFF6B6B)),
}

data class AppSettings(
    val themeMode: ThemeMode = ThemeMode.SYSTEM,
    // Disables the slowly-rotating gradient blobs behind the track info sheet —
    // that animation runs continuously while the sheet is open, which costs real
    // battery/GPU on low-power devices, so it's opt-out rather than always-on.
    val animatedBackground: Boolean = true,
    val accentColor: AccentColor = AccentColor.BLUE,
)

object SettingsStore {
    private const val PREFS_NAME = "settings"
    private const val KEY_THEME_MODE = "theme_mode"
    private const val KEY_ANIMATED_BACKGROUND = "animated_background"
    private const val KEY_ACCENT_COLOR = "accent_color"

    private fun prefs(context: Context) = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    fun load(context: Context): AppSettings {
        val p = prefs(context)
        val themeMode = runCatching { ThemeMode.valueOf(p.getString(KEY_THEME_MODE, null) ?: "") }
            .getOrDefault(ThemeMode.SYSTEM)
        val accentColor = runCatching { AccentColor.valueOf(p.getString(KEY_ACCENT_COLOR, null) ?: "") }
            .getOrDefault(AccentColor.BLUE)
        val animatedBackground = p.getBoolean(KEY_ANIMATED_BACKGROUND, true)
        return AppSettings(themeMode, animatedBackground, accentColor)
    }

    fun save(context: Context, settings: AppSettings) {
        prefs(context).edit()
            .putString(KEY_THEME_MODE, settings.themeMode.name)
            .putBoolean(KEY_ANIMATED_BACKGROUND, settings.animatedBackground)
            .putString(KEY_ACCENT_COLOR, settings.accentColor.name)
            .apply()
    }
}
