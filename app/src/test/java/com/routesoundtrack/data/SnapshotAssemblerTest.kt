package com.routesoundtrack.data

import com.routesoundtrack.data.sdk.RouteInfo
import com.routesoundtrack.data.sdk.VehicleFast
import com.routesoundtrack.data.sdk.VehicleSlow
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class SnapshotAssemblerTest {

    private fun route(remainingKm: Double?, etaSec: Int? = 1_800) = RouteInfo(remainingKm, etaSec, "판교")

    @Test
    fun `경로가 없으면 routeActive false이고 거리 값은 모두 null`() {
        val s = SnapshotAssembler().assemble(0, null, VehicleFast(12.0, true), null)
        assertFalse(s.routeActive)
        assertNull(s.totalKm)
        assertNull(s.progressKm)
        assertEquals(true, s.brakePressed)
    }

    @Test
    fun `진행 거리 = 처음 본 남은 거리 - 지금 남은 거리`() {
        val a = SnapshotAssembler()
        val first = a.assemble(0, route(50.0), null, null)
        assertEquals(50.0, first.totalKm!!, 1e-9)
        assertEquals(0.0, first.progressKm!!, 1e-9)

        val later = a.assemble(60_000, route(48.5), null, null)
        assertEquals(50.0, later.totalKm!!, 1e-9)
        assertEquals(1.5, later.progressKm!!, 1e-9)
        assertEquals("판교", later.destination)
    }

    @Test
    fun `재탐색으로 남은 거리가 늘면 총거리를 다시 잡고 진행 거리는 이어 간다`() {
        val a = SnapshotAssembler()
        a.assemble(0, route(50.0), null, null)
        a.assemble(1_000, route(40.0), null, null)        // 10 km 진행
        val s = a.assemble(2_000, route(45.0), null, null) // 재탐색: 5 km 늘어남
        assertEquals(55.0, s.totalKm!!, 1e-9)
        assertEquals(10.0, s.progressKm!!, 1e-9)
    }

    @Test
    fun `남은 거리를 모르면 트립 거리 차이로 진행 거리를 계산한다`() {
        val a = SnapshotAssembler()
        a.assemble(0, route(null), VehicleFast(100.0, false), null)
        val s = a.assemble(1_000, route(null), VehicleFast(103.0, false), null)
        assertEquals(3.0, s.progressKm!!, 1e-9)
        assertNull(s.totalKm)
    }

    @Test
    fun `속도는 최근 이동 거리로 추정한다 - 10초에 250 m면 90 km_h`() {
        val a = SnapshotAssembler()
        assertNull(a.assemble(0, route(50.0), null, null).speedKmh)          // 아직 표본 부족
        val s = a.assemble(10_000, route(49.75), null, null)
        assertEquals(90.0, s.speedKmh!!, 1e-6)
    }

    @Test
    fun `하향등이나 미등이 켜지면 야간, 둘 다 모르면 null`() {
        val a = SnapshotAssembler()
        assertTrue(a.assemble(0, route(50.0), null, VehicleSlow("NORMAL", true, false, 10)).night!!)
        assertFalse(a.assemble(0, route(50.0), null, VehicleSlow("NORMAL", false, false, 10)).night!!)
        assertNull(a.assemble(0, route(50.0), null, VehicleSlow(null, null, null, null)).night)
    }
}
