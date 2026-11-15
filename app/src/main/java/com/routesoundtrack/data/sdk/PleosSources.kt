package com.routesoundtrack.data.sdk

import ai.pleos.playground.navi.constants.NaviErrorCode
import ai.pleos.playground.navi.constants.NaviMode
import ai.pleos.playground.navi.constants.NavigationStatus
import ai.pleos.playground.navi.data.DestinationArrivedInfo
import ai.pleos.playground.navi.data.DestinationInfo
import ai.pleos.playground.navi.data.DrivingInfo
import ai.pleos.playground.navi.data.DrivingState
import ai.pleos.playground.navi.data.RouteStartInfo
import ai.pleos.playground.navi.data.RouteStateInfo
import ai.pleos.playground.navi.helper.NaviHelper
import ai.pleos.playground.navi.helper.listener.NaviHelperEventListener
import ai.pleos.playground.vehicle.Vehicle
import ai.pleos.playground.vehicle.constant.control.DrivingModeType
import ai.pleos.playground.vehicle.constant.control.LightState
import android.content.Context
import android.util.Log
import kotlin.coroutines.resume
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withTimeoutOrNull

private const val TAG = "RouteSoundtrack"

/**
 * Pleos NaviHelper SDK 어댑터 (권한: pleos.car.permission.NAVI_ROUTE).
 *
 * NaviHelper의 getXxxInfo()는 값을 돌려주지 않고, 결과를 리스너 콜백(onXxxInfo)으로 보낸다.
 * 그래서 콜백으로 받은 마지막 값을 저장해 두고, readRoute()는 새 값을 요청한 뒤 잠깐 기다렸다가 저장된 값을 돌려준다.
 * 단위(에뮬레이터 확인): remainDistance·distance = m, remainTime·duration = 초.
 */
class PleosNaviSource(context: Context) : NaviDataSource {
    private val helper = NaviHelper(context.applicationContext)
    private val connected = CompletableDeferred<Boolean>()

    // 콜백은 SDK 스레드에서 오므로 @Volatile로 다른 스레드에서도 최신 값이 보이게 한다.
    @Volatile private var routing = false
    @Volatile private var destination: DestinationInfo? = null
    @Volatile private var driving: DrivingState? = null
    @Volatile private var routeStateReply: CompletableDeferred<Unit>? = null
    private var lastMode: NaviMode? = null   // 로그용

    private val listener = object : NaviHelperEventListener {
        override fun onRouteStarted(routeStartInfo: RouteStartInfo) {
            Log.d(TAG, "navi route started: $routeStartInfo")
            routing = true
        }

        override fun onRouteCancelled() {
            Log.d(TAG, "navi route cancelled")
            clearRoute()
        }

        override fun onDestinationArrived(destinationArrivedInfo: DestinationArrivedInfo) {
            Log.d(TAG, "navi arrived: $destinationArrivedInfo")
            clearRoute()
        }

        override fun onNavigationStatus(status: NavigationStatus) {
            Log.d(TAG, "navi status: $status")
            when (status) {
                NavigationStatus.ROUTE_DRIVE -> routing = true
                NavigationStatus.INACTIVE, NavigationStatus.FREE_DRIVE -> clearRoute()
                else -> Unit                       // INITIALIZING: 그대로 둔다
            }
        }

        // isRouting은 "경로 탐색 중"이라서 안내 여부는 naviMode로 판단한다(모의 주행 포함).
        override fun onRouteStateInfo(routeStateInfo: RouteStateInfo) {
            val mode = routeStateInfo.naviMode
            if (mode != lastMode) {
                Log.d(TAG, "navi mode: $lastMode -> $mode")
                lastMode = mode
            }
            if (mode == NaviMode.ROUTE_GUIDING || mode == NaviMode.SIMULATION_GUIDING) routing = true else clearRoute()
            routeStateReply?.complete(Unit)
        }

        override fun onDestinationInfo(destinationInfo: DestinationInfo) {
            if (destination == null) Log.d(TAG, "navi destination: $destinationInfo")
            destination = destinationInfo
        }

        override fun onDrivingInfo(drivingInfo: DrivingInfo) {
            driving = drivingInfo.destination
        }

        override fun onError(errorCode: NaviErrorCode) {
            Log.w(TAG, "navi error: $errorCode")
        }
    }

    init {
        runCatching {
            helper.initialize(
                {
                    Log.d(TAG, "NaviHelper connected")
                    helper.addListener(listener)
                    connected.complete(true)
                },
                { e ->
                    Log.w(TAG, "NaviHelper unavailable: $e")
                    connected.complete(false)
                },
            )
        }.onFailure {
            Log.w(TAG, "NaviHelper init failed: $it")
            connected.complete(false)
        }
    }

    override suspend fun readRoute(): RouteInfo? {
        if (withTimeoutOrNull(CONNECT_WAIT_MS) { connected.await() } != true) return null
        val reply = CompletableDeferred<Unit>()
        routeStateReply = reply
        runCatching {
            helper.getRouteStateInfo()
            helper.getDestinationInfo()
        }.onFailure { Log.w(TAG, "navi request failed: $it") }
        withTimeoutOrNull(REPLY_WAIT_MS) { reply.await() }
        return currentRoute()
    }

    /** 저장된 값으로 RouteInfo를 만든다. 주행 정보(onDrivingInfo)가 더 자주 갱신되므로 먼저 쓴다. */
    private fun currentRoute(): RouteInfo? {
        if (!routing) return null
        val drv = driving
        val dest = destination
        val remainM = drv?.distance ?: dest?.remainDistance
        val remainSec = drv?.duration ?: dest?.remainTime
        return RouteInfo(
            remainingKm = remainM?.takeIf { it >= 0 }?.let { it / 1000.0 },
            etaSec = remainSec?.takeIf { it >= 0 },
            destinationName = (dest?.name ?: drv?.name)?.takeIf { it.isNotBlank() },
        )
    }

    private fun clearRoute() {
        routing = false
        destination = null
        driving = null
    }

    private companion object {
        const val CONNECT_WAIT_MS = 3_000L
        const val REPLY_WAIT_MS = 500L
    }
}

/**
 * Pleos Vehicle SDK 어댑터 (권한: pleos.car.permission.CAR_DRIVE, CAR_LIGHTS).
 * Vehicle SDK의 getter는 결과를 onComplete·onFailed 콜백으로 준다. awaitValue()로 감싸 suspend 함수처럼 쓴다.
 * 단위(확인 필요): 트립 거리 = km, 트립 주행 시간 = 초로 본다.
 */
class PleosVehicleSource(context: Context) : VehicleDataSource {
    private val vehicle = Vehicle(context.applicationContext)
    private val ready = runCatching { vehicle.initialize() }
        .onFailure { Log.w(TAG, "Vehicle SDK init failed: $it") }
        .isSuccess

    override suspend fun readFast(): VehicleFast? {
        if (!ready) return null
        val trip = awaitValue<Float> { ok, fail -> vehicle.getOdometer().getTripCurrentDrivingDistance(ok, fail) }
        val brake = awaitValue<Boolean> { ok, fail -> vehicle.getBrake().getCurrentBrakePedalStatus(ok, fail) }
        if (trip == null && brake == null) return null
        return VehicleFast(tripDistanceKm = trip?.toDouble(), brakePressed = brake)
    }

    override suspend fun readSlow(): VehicleSlow? {
        if (!ready) return null
        val mode = awaitValue<DrivingModeType> { ok, fail -> vehicle.getDrivingMode().getCurrentDrivingModeState(ok, fail) }
        val lowBeam = awaitValue<LightState> { ok, fail -> vehicle.getLight().getCurrentLowBeamLightState(ok, fail) }
        val tail = awaitValue<LightState> { ok, fail -> vehicle.getLight().getCurrentTailLampLightState(ok, fail) }
        val tripSec = awaitValue<Int> { ok, fail -> vehicle.getOdometer().getTripCurrentDrivingTime(ok, fail) }
        return VehicleSlow(
            driveMode = mode?.name,
            lowBeamOn = lowBeam?.let { it == LightState.ON },
            tailLampOn = tail?.let { it == LightState.ON },
            drivingTimeMin = tripSec?.let { it / 60 },
        )
    }

    private companion object {
        const val CALL_TIMEOUT_MS = 500L
    }

    /**
     * 콜백형 SDK 호출 하나를 기다린다. 성공하면 값, 실패·시간 초과·예외면 null.
     * suspendCancellableCoroutine: 콜백이 올 때까지 코루틴을 멈췄다가 resume()으로 다시 깨운다.
     */
    private suspend fun <T> awaitValue(call: (onComplete: (T?) -> Unit, onFailed: (Exception) -> Unit) -> Unit): T? =
        withTimeoutOrNull(CALL_TIMEOUT_MS) {
            suspendCancellableCoroutine<T?> { cont ->
                runCatching {
                    call(
                        { value -> if (cont.isActive) cont.resume(value) },
                        { _ -> if (cont.isActive) cont.resume(null) },
                    )
                }.onFailure { if (cont.isActive) cont.resume(null) }
            }
        }
}
