package com.routesoundtrack.data

import com.routesoundtrack.data.sdk.RouteInfo
import com.routesoundtrack.data.sdk.VehicleFast
import com.routesoundtrack.data.sdk.VehicleSlow
import com.routesoundtrack.domain.model.DriveSnapshot

/**
 * SDK에서 읽은 값들을 DriveSnapshot 하나로 합친다.
 * android.* 를 쓰지 않으므로 JVM 단위 테스트로 검증한다.
 *
 * - 진행 거리 = 경로를 처음 본 순간의 남은 거리 − 지금 남은 거리
 * - 재탐색(남은 거리가 한 번에 2 km 넘게 늘어남)이면 총거리를 다시 잡고 진행 거리는 이어 간다
 * - 속도: SDK에 속도 값이 없어서 최근 30초 동안 움직인 거리로 추정한다
 */
class SnapshotAssembler {
    private var totalKm: Double? = null
    private var lastRemainingKm: Double? = null
    private var tripAtStartKm: Double? = null
    private val samples = ArrayDeque<Pair<Long, Double>>()   // (시각 ms, 이동 거리 km)

    fun assemble(timeMs: Long, route: RouteInfo?, fast: VehicleFast?, slow: VehicleSlow?): DriveSnapshot {
        val night = night(slow)
        if (route == null) {
            clear()
            return DriveSnapshot(
                timeMs = timeMs,
                routeActive = false,
                totalKm = null,
                progressKm = null,
                remainingKm = null,
                etaSec = null,
                speedKmh = null,
                brakePressed = fast?.brakePressed,
                night = night,
                drivingTimeMin = slow?.drivingTimeMin,
            )
        }

        val remaining = route.remainingKm
        if (remaining != null) {
            val total = totalKm
            val last = lastRemainingKm
            if (total == null) {
                totalKm = remaining
            } else if (last != null && remaining > last + REROUTE_JUMP_KM) {
                totalKm = (total - last) + remaining          // 지금까지 온 거리 + 새 남은 거리
            }
            lastRemainingKm = remaining
        }

        val trip = fast?.tripDistanceKm
        if (trip != null && tripAtStartKm == null) tripAtStartKm = trip
        val tripStart = tripAtStartKm
        val total = totalKm
        val progress = when {
            total != null && remaining != null -> (total - remaining).coerceAtLeast(0.0)
            trip != null && tripStart != null -> (trip - tripStart).coerceAtLeast(0.0)
            else -> null
        }

        return DriveSnapshot(
            timeMs = timeMs,
            routeActive = true,
            totalKm = total,
            progressKm = progress,
            remainingKm = remaining,
            etaSec = route.etaSec,
            speedKmh = estimateSpeed(timeMs, progress),
            brakePressed = fast?.brakePressed,
            night = night,
            drivingTimeMin = slow?.drivingTimeMin,
            destination = route.destinationName,
        )
    }

    /** 최근 SPEED_WINDOW_MS 동안의 이동 거리 ÷ 시간. 5초 넘게 쌓이기 전에는 null. */
    private fun estimateSpeed(timeMs: Long, distanceKm: Double?): Double? {
        if (distanceKm == null) return null
        samples.addLast(timeMs to distanceKm)
        while (samples.size > 2 && timeMs - samples.first().first > SPEED_WINDOW_MS) samples.removeFirst()
        val (t0, d0) = samples.first()
        val dtMs = timeMs - t0
        if (dtMs < MIN_SPEED_SPAN_MS) return null
        return ((distanceKm - d0) / (dtMs / 3_600_000.0)).coerceAtLeast(0.0)
    }

    /** 하향등이나 미등이 켜져 있으면 야간. 둘 다 모르면 null. */
    private fun night(slow: VehicleSlow?): Boolean? {
        val low = slow?.lowBeamOn
        val tail = slow?.tailLampOn
        if (low == null && tail == null) return null
        return low == true || tail == true
    }

    private fun clear() {
        totalKm = null
        lastRemainingKm = null
        tripAtStartKm = null
        samples.clear()
    }

    companion object {
        const val REROUTE_JUMP_KM = 2.0
        const val SPEED_WINDOW_MS = 30_000L
        const val MIN_SPEED_SPAN_MS = 5_000L
    }
}
