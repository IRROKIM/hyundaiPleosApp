package com.routesoundtrack.data.sim

import android.content.Context
import android.os.SystemClock
import com.routesoundtrack.data.DriveInput
import com.routesoundtrack.data.sdk.RouteInfo
import com.routesoundtrack.domain.model.DriveSnapshot

/**
 * 내장 시나리오로 주행하는 입력 (디버그 빌드 전용).
 * 곡이 없는 구간은 빨리 감고(silentRate), 곡이 나오는 동안은 실시간(playingRate)으로 진행한다.
 */
class ScenarioDriveInput(context: Context) : DriveInput {
    private val scenario = ScenarioLoader.load(context, PATH)
    private val initialEtaSec = ScenarioSimulator(scenario).etaSec()
    private var sim = ScenarioSimulator(scenario)
    private var lastReal = SystemClock.elapsedRealtime()

    override val tickMs = 250L

    override suspend fun readRoute(): RouteInfo =
        RouteInfo(remainingKm = scenario.totalKm, etaSec = initialEtaSec, destinationName = scenario.destinationName)

    override fun reset() {
        sim = ScenarioSimulator(scenario)
        lastReal = SystemClock.elapsedRealtime()
    }

    override suspend fun read(playing: Boolean): DriveSnapshot {
        val now = SystemClock.elapsedRealtime()
        val rate = if (playing) scenario.playingRate else scenario.silentRate
        sim.step(((now - lastReal) * rate).toLong())
        lastReal = now
        return sim.snapshot()
    }

    private companion object {
        const val PATH = "scenarios/city_highway_jam.json"
    }
}
