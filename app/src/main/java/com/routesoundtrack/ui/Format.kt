package com.routesoundtrack.ui

import androidx.annotation.StringRes
import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import com.routesoundtrack.R
import com.routesoundtrack.domain.model.Density
import com.routesoundtrack.domain.model.Mood
import com.routesoundtrack.domain.model.MoodMode
import com.routesoundtrack.domain.model.Preset
import com.routesoundtrack.domain.model.Road
import kotlin.math.roundToInt

fun formatKm(km: Double): String = if (km >= 100) "%.0f".format(km) else "%.1f".format(km)

/** 5.0 → "5", 27.5 → "27.5" */
fun formatKmShort(km: Double): String = if (km % 1.0 == 0.0) "%.0f".format(km) else "%.1f".format(km)

fun formatDuration(sec: Double): String {
    val s = sec.roundToInt()
    return "%d:%02d".format(s / 60, s % 60)
}

@StringRes fun moodLabel(mood: Mood): Int = when (mood) {
    Mood.CHILL -> R.string.mood_chill
    Mood.GROOVE -> R.string.mood_groove
    Mood.UPBEAT -> R.string.mood_upbeat
    Mood.HYPE -> R.string.mood_hype
    Mood.ARRIVAL -> R.string.mood_arrival
}

@StringRes fun roadLabel(road: Road?): Int = when (road) {
    Road.CITY -> R.string.road_city
    Road.JAM -> R.string.road_jam
    Road.HIGHWAY -> R.string.road_highway
    null -> R.string.road_unknown
}

@StringRes fun presetLabel(p: Preset): Int = when (p) {
    Preset.LONG_HAUL -> R.string.preset_long_haul
    Preset.TRIP -> R.string.preset_trip
    Preset.FAMILY -> R.string.preset_family
}

@StringRes fun densityLabel(d: Density): Int = when (d) {
    Density.FREQUENT -> R.string.density_frequent
    Density.OCCASIONAL -> R.string.density_occasional
    Density.OFF -> R.string.density_off
}

@StringRes fun moodModeLabel(m: MoodMode): Int = when (m) {
    MoodMode.AUTO -> R.string.mood_mode_auto
    MoodMode.CHILL -> R.string.mood_mode_chill
    MoodMode.GROOVE -> R.string.mood_mode_groove
    MoodMode.UPBEAT -> R.string.mood_mode_upbeat
    MoodMode.HYPE -> R.string.mood_mode_hype
}

/** 메인 감정 원·주행 화면에 쓰는 감정 문구 (고요해요·편안해요·설레요·신나요·도착 곡). */
@StringRes fun feelLabel(mood: Mood): Int = when (mood) {
    Mood.CHILL -> R.string.feel_quiet
    Mood.GROOVE -> R.string.feel_groove
    Mood.UPBEAT -> R.string.feel_upbeat
    Mood.HYPE -> R.string.feel_hype
    Mood.ARRIVAL -> R.string.feel_arrival
}

/** 도착까지 남은 시간: 60분 미만은 "N분", 이상은 "N시간 M분". */
@Composable
fun etaText(min: Int): String =
    if (min < 60) stringResource(R.string.eta_min, min) else stringResource(R.string.eta_hour_min, min / 60, min % 60)
