package com.routesoundtrack.domain

import com.routesoundtrack.domain.model.Cue
import com.routesoundtrack.domain.model.CueKind
import com.routesoundtrack.domain.model.DriveContext
import com.routesoundtrack.domain.model.DriveSnapshot
import com.routesoundtrack.domain.model.Mood
import com.routesoundtrack.domain.model.MoodMode
import com.routesoundtrack.domain.model.Preset
import com.routesoundtrack.domain.model.Road
import com.routesoundtrack.domain.model.Settings

/** 프리셋·설정에 따른 규칙 모음. */
class Policy(val settings: Settings, private val p: PlannerParams = PlannerParams()) {

    /** 고속도로 정책: 장거리 프리셋만 고속 구간에서 재생한다. 나머지는 큐를 미룬다. */
    fun allowsNow(ctx: DriveContext): Boolean =
        ctx.road != Road.HIGHWAY || settings.preset == Preset.LONG_HAUL

    /** 도착 판정: ETA가 있으면 8분 이하, 없으면 남은 거리 10 km 이하. */
    fun isArrivalDue(s: DriveSnapshot): Boolean {
        s.etaSec?.let { return it <= p.arrivalEtaSec }
        s.remainingKm?.let { return it <= p.arrivalKm }
        return false
    }

    /**
     * 무드 선택 순서: 위에서부터 처음 맞는 규칙을 쓴다.
     * 각성 단계: 고속도로 → 각성형(신스웨이브), 장거리 프리셋에서 90분 넘게 연속 주행 중이면 → 파워(EDM).
     * 파워는 시내·정체에서 자동으로 고르지 않는다(사용자가 직접 고를 때만).
     */
    fun moodFor(cue: Cue, ctx: DriveContext): Mood = when {
        cue.kind == CueKind.ARRIVAL -> Mood.ARRIVAL
        settings.moodMode == MoodMode.CHILL -> Mood.CHILL
        settings.moodMode == MoodMode.GROOVE -> Mood.GROOVE
        settings.moodMode == MoodMode.UPBEAT -> Mood.UPBEAT
        settings.moodMode == MoodMode.HYPE -> Mood.HYPE
        settings.preset == Preset.FAMILY -> Mood.CHILL
        ctx.night -> Mood.CHILL
        ctx.road == Road.HIGHWAY ->
            if (settings.preset == Preset.LONG_HAUL && ctx.longDrive) Mood.HYPE else Mood.UPBEAT
        ctx.road != null -> Mood.CHILL
        else -> if (settings.preset == Preset.LONG_HAUL) Mood.UPBEAT else Mood.CHILL
    }

    /** 곡이 끝난 뒤 최소 무음: 3 km 또는 3분 중 먼저 오는 쪽. */
    fun silenceDone(sinceKm: Double, sinceMs: Long, nowKm: Double, nowMs: Long): Boolean =
        nowKm - sinceKm >= p.minSilenceKm || nowMs - sinceMs >= p.minSilenceMs

    val volume: Float get() = if (settings.preset == Preset.FAMILY) 0.6f else 0.8f
    val fadeOutMs: Int get() = if (settings.preset == Preset.FAMILY) 6_000 else 3_000
    val duckGain: Float get() = 0.3f

    companion object {
        fun intervalMultiplier(preset: Preset): Double = when (preset) {
            Preset.LONG_HAUL -> 1.5
            Preset.TRIP -> 1.0
            Preset.FAMILY -> 1.3
        }
    }
}
