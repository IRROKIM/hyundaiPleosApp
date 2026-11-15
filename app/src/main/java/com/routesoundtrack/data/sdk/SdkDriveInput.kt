package com.routesoundtrack.data.sdk

import android.os.SystemClock
import com.routesoundtrack.data.DriveInput
import com.routesoundtrack.data.SnapshotAssembler
import com.routesoundtrack.domain.model.DriveSnapshot

/**
 * Pleos SDK로 주행 값을 읽는 입력.
 * 1초마다 내비 경로·트립 거리·브레이크를 읽고, 느리게 바뀌는 값(조명·주행 모드·주행 시간)은 10초마다 읽는다.
 */
class SdkDriveInput(
    private val navi: NaviDataSource,
    private val vehicle: VehicleDataSource,
) : DriveInput {
    override val tickMs = 1_000L

    private var assembler = SnapshotAssembler()
    private var slow: VehicleSlow? = null
    private var tick = 0
    private var startMs = SystemClock.elapsedRealtime()

    override suspend fun readRoute(): RouteInfo? = navi.readRoute()

    override fun reset() {
        assembler = SnapshotAssembler()
        slow = null
        tick = 0
        startMs = SystemClock.elapsedRealtime()
    }

    override suspend fun read(playing: Boolean): DriveSnapshot {
        if (tick++ % SLOW_EVERY_TICKS == 0) slow = vehicle.readSlow()
        val route = navi.readRoute()
        val fast = vehicle.readFast()
        return assembler.assemble(SystemClock.elapsedRealtime() - startMs, route, fast, slow)
    }

    private companion object {
        const val SLOW_EVERY_TICKS = 10
    }
}
