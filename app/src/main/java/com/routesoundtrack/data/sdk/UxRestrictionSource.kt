package com.routesoundtrack.data.sdk

import android.car.Car
import android.car.drivingstate.CarUxRestrictionsManager
import android.content.Context
import android.content.pm.PackageManager
import android.util.Log
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * 주행 규제 상태 (Pleos 주행 규제 개발 가이드: CarUxRestrictionsManager 기준으로 UX를 제한한다).
 * restricted 값: true = 주행 중이라 조작을 줄여야 함, false = 제한 없음, null = 알 수 없음(차량이 아닌 기기).
 * 차량용 클래스(android.car)는 일반 기기에 없으므로, 차량일 때만 건드리고 실패는 모두 null로 둔다.
 */
class UxRestrictionSource(context: Context) {
    private val _restricted = MutableStateFlow<Boolean?>(null)
    val restricted: StateFlow<Boolean?> = _restricted.asStateFlow()

    init {
        if (context.packageManager.hasSystemFeature(PackageManager.FEATURE_AUTOMOTIVE)) {
            runCatching {
                val car = Car.createCar(context)
                val manager = car.getCarManager(Car.CAR_UX_RESTRICTION_SERVICE) as CarUxRestrictionsManager
                _restricted.value = manager.currentCarUxRestrictions?.isRequiresDistractionOptimization
                manager.registerListener { r -> _restricted.value = r.isRequiresDistractionOptimization }
            }.onFailure { Log.w("RouteSoundtrack", "UX restrictions unavailable: $it") }
        }
    }
}
