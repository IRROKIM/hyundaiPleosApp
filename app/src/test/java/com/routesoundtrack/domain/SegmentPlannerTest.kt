package com.routesoundtrack.domain

import com.routesoundtrack.domain.model.Density
import com.routesoundtrack.domain.model.Preset
import com.routesoundtrack.domain.model.Settings
import org.junit.Assert.assertEquals
import org.junit.Test

class SegmentPlannerTest {
    private val planner = SegmentPlanner()

    private fun plan(totalKm: Double, preset: Preset, density: Density) =
        planner.plan(totalKm, Settings(preset = preset, density = density), seed = 0L)

    @Test
    fun `TC-04 음악 밀도를 바꾸면 큐 수가 바뀐다`() {   // 백틱: 공백·한글이 들어간 함수 이름
        assertEquals(8, plan(120.0, Preset.TRIP, Density.FREQUENT).cues.size)    // 시작1 + 중간6 + 도착1
        assertEquals(5, plan(120.0, Preset.TRIP, Density.OCCASIONAL).cues.size)  // 시작1 + 중간3 + 도착1
        assertEquals(0, plan(120.0, Preset.TRIP, Density.OFF).cues.size)
    }

    @Test
    fun `시연 경로 50 km - 여행은 20 km, 장거리는 27_5 km에 중간 큐`() {
        assertEquals(listOf(5.0, 20.0), plan(50.0, Preset.TRIP, Density.FREQUENT).playable.map { it.atKm })
        assertEquals(listOf(5.0, 27.5), plan(50.0, Preset.LONG_HAUL, Density.FREQUENT).playable.map { it.atKm })
    }

    @Test
    fun `10 km 미만 짧은 경로는 도착 곡만`() {
        val p = plan(8.0, Preset.TRIP, Density.FREQUENT)
        assertEquals(0, p.playable.size)
        assertEquals(1, p.cues.size)
    }
}
