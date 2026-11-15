package com.routesoundtrack.session

import com.routesoundtrack.data.sdk.RouteInfo
import kotlinx.coroutines.delay

/**
 * 내비 경로 안내가 시작·종료되는 순간을 알아낸다.
 * 경로 정보를 천천히(5초마다) 다시 읽어 "없음 → 있음", "있음 → 없음" 변화만 알린다.
 * 사용자가 "안내 시작 시 자동 시작"을 켜 둔 동안에만 돌린다(자동 재생 금지 규정 + 백그라운드 폴링 최소화).
 *
 * @param readRoute 경로를 한 번 읽는 함수. 목적지가 없으면 null을 돌려준다 (예: DriveInput::readRoute)
 */
class RouteWatcher(
    private val readRoute: suspend () -> RouteInfo?,
    private val onRouteStarted: (RouteInfo) -> Unit,
    private val onRouteEnded: () -> Unit,
) {
    private var hadRoute = false

    /** 경로를 한 번 읽고, 없음 → 있음 / 있음 → 없음으로 바뀐 때만 콜백을 부른다. */
    suspend fun check() {
        val route = readRoute()
        if (route != null && !hadRoute) {
            hadRoute = true
            onRouteStarted(route)
        } else if (route == null && hadRoute) {
            hadRoute = false
            onRouteEnded()
        }
    }

    /** 취소될 때까지 주기적으로 확인한다. */
    suspend fun watch(periodMs: Long = PERIOD_MS) {
        while (true) {
            check()
            delay(periodMs)
        }
    }

    companion object {
        const val PERIOD_MS = 5_000L
    }
}
