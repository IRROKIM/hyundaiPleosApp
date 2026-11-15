package com.routesoundtrack.domain

import com.routesoundtrack.domain.model.DriveContext
import com.routesoundtrack.domain.model.DriveSnapshot
import com.routesoundtrack.domain.model.Road

/**
 * 속도 흐름으로 시내·정체·고속도로를 판정한다.
 * 진입·해제 조건을 다르게 둔 히스테리시스로, 짧은 감속·가속에 상태가 흔들리지 않게 한다.
 */
class ContextClassifier {
    private var highway = false
    private var jam = false
    private var hwEnterSince: Long? = null
    private var hwExitSince: Long? = null
    private var jamEnterSince: Long? = null
    private var jamExitSince: Long? = null
    private val brakePresses = ArrayDeque<Long>()
    private var lastBrake = false

    fun update(s: DriveSnapshot): DriveContext {
        val night = s.night == true
        val longDrive = (s.drivingTimeMin ?: 0) >= LONG_DRIVE_MIN
        val t = s.timeMs
        trackBrake(s.brakePressed, t)
        val v = s.speedKmh ?: return DriveContext(road = null, night = night, longDrive = longDrive)
        updateHighway(v, t, s.highwayHint)
        updateJam(v, t)
        val road = when {
            highway -> Road.HIGHWAY
            jam -> Road.JAM
            else -> Road.CITY
        }
        return DriveContext(road, night, longDrive)
    }

    private fun updateHighway(v: Double, t: Long, hint: Boolean?) {
        if (hint != null) {
            highway = hint
            return
        }
        if (!highway) {
            hwEnterSince = if (v >= HW_ENTER_KMH) (hwEnterSince ?: t) else null
            val since = hwEnterSince
            if (since != null && t - since >= HW_ENTER_MS) {
                highway = true
                hwExitSince = null
            }
        } else {
            hwExitSince = if (v < HW_EXIT_KMH) (hwExitSince ?: t) else null
            val since = hwExitSince
            if (since != null && t - since >= HW_EXIT_MS) {
                highway = false
                hwEnterSince = null
            }
        }
    }

    private fun updateJam(v: Double, t: Long) {
        if (!jam) {
            jamEnterSince = if (v < JAM_ENTER_KMH) (jamEnterSince ?: t) else null
            val since = jamEnterSince
            val need = if (brakePresses.size >= 3) JAM_ENTER_FAST_MS else JAM_ENTER_MS
            if (since != null && t - since >= need) {
                jam = true
                jamExitSince = null
            }
        } else {
            jamExitSince = if (v >= JAM_EXIT_KMH) (jamExitSince ?: t) else null
            val since = jamExitSince
            if (since != null && t - since >= JAM_EXIT_MS) {
                jam = false
                jamEnterSince = null
            }
        }
    }

    private fun trackBrake(pressed: Boolean?, t: Long) {
        val now = pressed == true
        if (now && !lastBrake) brakePresses.addLast(t)
        lastBrake = now
        while (brakePresses.isNotEmpty() && t - brakePresses.first() > 60_000) brakePresses.removeFirst()
    }

    companion object {
        const val HW_ENTER_KMH = 80.0
        const val HW_ENTER_MS = 60_000L
        const val HW_EXIT_KMH = 60.0
        const val HW_EXIT_MS = 120_000L
        const val JAM_ENTER_KMH = 20.0
        const val JAM_ENTER_MS = 90_000L
        const val JAM_ENTER_FAST_MS = 30_000L
        const val JAM_EXIT_KMH = 30.0
        const val JAM_EXIT_MS = 60_000L
        const val LONG_DRIVE_MIN = 90      // 이 시간 넘게 연속 주행하면 졸음 위험이 커진다고 보고 파워 단계를 허용
    }
}
