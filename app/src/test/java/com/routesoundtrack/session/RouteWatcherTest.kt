package com.routesoundtrack.session

import com.routesoundtrack.data.sdk.RouteInfo
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Test

class RouteWatcherTest {

    @Test
    fun `경로가 생기면 시작을, 사라지면 종료를 한 번씩만 알린다`() = runTest {
        var route: RouteInfo? = null                      // 테스트에서 마음대로 바꾸는 가짜 내비 경로
        val log = mutableListOf<String>()
        val watcher = RouteWatcher({ route }, onRouteStarted = { log += "start:${it.destinationName}" }, onRouteEnded = { log += "end" })

        watcher.check()                                   // 경로 없음
        route = RouteInfo(12.0, 900, "판교")
        watcher.check()                                   // 없음 → 있음
        watcher.check()                                   // 계속 있음: 다시 알리지 않는다
        route = null
        watcher.check()                                   // 있음 → 없음
        watcher.check()

        assertEquals(listOf("start:판교", "end"), log)
    }
}
