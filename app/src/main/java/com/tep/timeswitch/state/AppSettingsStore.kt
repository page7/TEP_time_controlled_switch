package com.tep.timeswitch.state

import android.content.Context
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

/** 主题模式(设置页「主题」)。 */
enum class ThemeMode { SYSTEM, LIGHT, DARK }

/**
 * App 设置持久化(SharedPreferences 最小实现,同 LastDeviceStore 风格)。
 * 目前仅主题模式,后续设置项追加于此。
 */
class AppSettingsStore(context: Context) {

    private val prefs = context.getSharedPreferences("app_settings", Context.MODE_PRIVATE)

    private val _themeMode = MutableStateFlow(
        runCatching { ThemeMode.valueOf(prefs.getString(KEY_THEME, ThemeMode.SYSTEM.name)!!) }
            .getOrDefault(ThemeMode.SYSTEM),
    )
    val themeMode: StateFlow<ThemeMode> = _themeMode

    fun setThemeMode(mode: ThemeMode) {
        _themeMode.value = mode
        prefs.edit().putString(KEY_THEME, mode.name).apply()
    }

    companion object {
        private const val KEY_THEME = "theme_mode"
    }
}
