package com.routesoundtrack.data.sim

import com.routesoundtrack.domain.model.DriveSnapshot
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt

/** 거리 기준 키프레임: 이 지점에서의 속도. brakePerMin은 직전 키프레임부터 이 지점까지의 브레이크 빈도. */
data class Keyframe(val km: Double, val kmh: Double, val brakePerMin: Double = 0.0)

data class Scenario(
    val name: String,
    val destinationName: String,
    val totalKm: Double,
    val keyframes: List<Keyframe>,
    val silentRate: Double,     // 곡이 없을 때 배속
    val playingRate: Double,    // 곡이 나올 때 배속 (음악은 늘 실시간)
)

/**
 * 시나리오를 따라 가상의 차를 움직인다.
 * 디버그 빌드 전용 입력이다. 에뮬레이터 없이 화면·소리를 확인할 때 SDK 대신 쓴다.
 */
class ScenarioSimulator(private val sc: Scenario) {
    var progressKm = 0.0
        private set
    var timeMs = 0L
        private set

    val finished: Boolean get() = progressKm >= sc.totalKm - 1e-6

    fun speedAt(km: Double): Double {
        val k = sc.keyframes
        if (km <= k.first().km) return k.first().kmh
        for (i in 1 until k.size) {
            if (km <= k[i].km) {
                val a = k[i - 1]
                val b = k[i]
                val f = (km - a.km) / (b.km - a.km)
                return a.kmh + (b.kmh - a.kmh) * f
            }
        }
        return k.last().kmh
    }

    private fun brakeRateAt(km: Double): Double {
        val k = sc.keyframes
        for (i in 1 until k.size) if (km <= k[i].km) return k[i].brakePerMin
        return 0.0
    }

    /** 0 km/h에서 멈춰 버리지 않도록 최소 5 km/h로 움직인다. */
    private fun moveSpeed(km: Double): Double = max(speedAt(km), MIN_MOVE_KMH)

    /** 시뮬레이션 시간을 dtMs만큼 진행한다. */
    fun step(dtMs: Long) {
        timeMs += dtMs
        var left = dtMs
        while (left > 0 && !finished) {
            val d = min(left, 1_000L)
            progressKm = min(sc.totalKm, progressKm + moveSpeed(progressKm) * d / 3_600_000.0)
            left -= d
        }
    }

    /** 남은 키프레임 속도로 계산한 도착 예정 시간(초). */
    fun etaSec(): Int {
        var km = progressKm
        var sec = 0.0
        while (km < sc.totalKm - 1e-9) {
            val step = min(0.1, sc.totalKm - km)
            sec += step / moveSpeed(km) * 3600
            km += step
        }
        return sec.roundToInt()
    }

    private fun brakePressed(): Boolean {
        val rate = brakeRateAt(progressKm)
        if (rate <= 0.0) return false
        val periodMs = (60_000 / rate).toLong().coerceAtLeast(1)
        return timeMs % periodMs < 2_000
    }

    fun snapshot(): DriveSnapshot = DriveSnapshot(
        timeMs = timeMs,
        routeActive = !finished,
        totalKm = sc.totalKm,
        progressKm = progressKm,
        remainingKm = sc.totalKm - progressKm,
        etaSec = etaSec(),
        speedKmh = if (finished) 0.0 else moveSpeed(progressKm),
        brakePressed = brakePressed(),
        night = false,
        drivingTimeMin = (timeMs / 60_000).toInt(),
        destination = sc.destinationName,
    )

    companion object {
        const val MIN_MOVE_KMH = 5.0
    }
}
