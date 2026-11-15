package com.routesoundtrack.data

import android.content.Context
import com.routesoundtrack.domain.model.Density
import com.routesoundtrack.domain.model.MoodMode
import com.routesoundtrack.domain.model.Preset
import com.routesoundtrack.domain.model.Settings

/** 설정을 기기 안 SharedPreferences에 저장한다. 별도 DB나 서버는 쓰지 않는다. */
class SettingsStore(context: Context) {
    private val prefs = context.getSharedPreferences("settings", Context.MODE_PRIVATE)

    fun load(): Settings = Settings(
        preset = enumOr(prefs.getString(KEY_PRESET, null), Preset.TRIP),
        density = enumOr(prefs.getString(KEY_DENSITY, null), Density.FREQUENT),
        moodMode = enumOr(prefs.getString(KEY_MOOD_MODE, null), MoodMode.AUTO),
        autoStart = prefs.getBoolean(KEY_AUTO_START, false),
    )

    fun save(s: Settings) {
        prefs.edit()
            .putString(KEY_PRESET, s.preset.name)
            .putString(KEY_DENSITY, s.density.name)
            .putString(KEY_MOOD_MODE, s.moodMode.name)
            .putBoolean(KEY_AUTO_START, s.autoStart)
            .apply()
    }

    private inline fun <reified T : Enum<T>> enumOr(value: String?, default: T): T =
        value?.let { runCatching { enumValueOf<T>(it) }.getOrNull() } ?: default

    private companion object {
        const val KEY_PRESET = "preset"
        const val KEY_DENSITY = "density"
        const val KEY_MOOD_MODE = "mood_mode"
        const val KEY_AUTO_START = "auto_start"
    }
}
