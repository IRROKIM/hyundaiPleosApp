package com.routesoundtrack.data.sdk

/*
 * 실차(Pleos Connect SDK) 데이터 소스 인터페이스.
 * 구현은 PleosNaviSource(NaviHelper)와 PleosVehicleSource(Vehicle)다.
 * 모르는 값·실패는 null로 돌려준다. 상위 계층은 null만 처리하면 된다.
 */

/** 내비 경로. 목적지가 없으면 RouteInfo 자체가 null이다. */
data class RouteInfo(
    val remainingKm: Double?,
    val etaSec: Int?,              // 도착까지 남은 시간(초)
    val destinationName: String?,
)

data class VehicleFast(val tripDistanceKm: Double?, val brakePressed: Boolean?)

data class VehicleSlow(
    val driveMode: String?,        // NORMAL · ECO · SPORT
    val lowBeamOn: Boolean?,
    val tailLampOn: Boolean?,
    val drivingTimeMin: Int?,      // 현재 트립의 주행 시간(분)
)

interface NaviDataSource {
    suspend fun readRoute(): RouteInfo?        // 목적지가 없으면 null
}

interface VehicleDataSource {
    suspend fun readFast(): VehicleFast?       // 1초 주기
    suspend fun readSlow(): VehicleSlow?       // 10초 주기
}
