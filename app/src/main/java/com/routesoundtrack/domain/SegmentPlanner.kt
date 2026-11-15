package com.routesoundtrack.domain

import com.routesoundtrack.domain.model.Cue
import com.routesoundtrack.domain.model.CueKind
import com.routesoundtrack.domain.model.Density
import com.routesoundtrack.domain.model.RoutePlan
import com.routesoundtrack.domain.model.Settings

/** 초기값. 시연 후 조정한다. */
data class PlannerParams(
    val startKm: Double = 5.0,
    val frequentIntervalKm: Double = 15.0,
    val occasionalIntervalKm: Double = 30.0,
    val arrivalKm: Double = 10.0,
    val arrivalEtaSec: Int = 480,
    val shortRouteKm: Double = 10.0,
    val longRouteKm: Double = 30.0,
    val minSilenceKm: Double = 3.0,
    val minSilenceMs: Long = 180_000,
)

/** fun interface: 메서드가 하나뿐인 인터페이스. 테스트에서 람다로 가짜 계획기를 만들 수 있다. */
fun interface Planner {
    fun plan(totalKm: Double, settings: Settings, seed: Long): RoutePlan
}

/** 경로 길이와 설정으로 재생 지점(큐) 목록을 만든다. */
class SegmentPlanner(private val p: PlannerParams = PlannerParams()) : Planner {

    override fun plan(totalKm: Double, settings: Settings, seed: Long): RoutePlan {
        if (settings.density == Density.OFF || totalKm <= 0.0) return RoutePlan(totalKm, emptyList(), seed)

        val cues = mutableListOf<Cue>()
        var id = 0
        if (totalKm >= p.shortRouteKm) cues += Cue(id++, p.startKm, CueKind.START)
        if (totalKm >= p.longRouteKm) {
            val interval = intervalKm(settings)
            val limit = totalKm - p.arrivalKm - interval / 2
            var km = p.startKm + interval
            while (km <= limit + 1e-9) {
                cues += Cue(id++, km, CueKind.MID)
                km += interval
            }
        }
        // 도착 곡의 실제 시점은 ETA로 정한다. 여기 위치는 미리보기용 예상값이다.
        cues += Cue(id, (totalKm - p.arrivalKm).coerceAtLeast(totalKm / 2), CueKind.ARRIVAL)
        return RoutePlan(totalKm, cues, seed)
    }

    fun intervalKm(settings: Settings): Double {
        val base = if (settings.density == Density.FREQUENT) p.frequentIntervalKm else p.occasionalIntervalKm
        return base * Policy.intervalMultiplier(settings.preset)
    }
}
