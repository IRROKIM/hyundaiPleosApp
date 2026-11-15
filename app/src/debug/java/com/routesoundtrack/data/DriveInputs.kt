package com.routesoundtrack.data

import android.content.Context
import com.routesoundtrack.data.sdk.PleosNaviSource
import com.routesoundtrack.data.sdk.PleosVehicleSource
import com.routesoundtrack.data.sdk.SdkDriveInput
import com.routesoundtrack.data.sim.ScenarioDriveInput

/**
 * 디버그 빌드용 입력 선택. (릴리스 빌드에는 src/release/의 같은 이름 파일이 쓰이고, 항상 SDK다.)
 * USE_SIMULATOR를 true로 바꾸면 에뮬레이터·내비 없이 내장 시나리오(50 km)로 주행한다.
 */
object DriveInputs {
    private const val USE_SIMULATOR = false

    fun create(context: Context): DriveInput =
        if (USE_SIMULATOR) ScenarioDriveInput(context)
        else SdkDriveInput(PleosNaviSource(context), PleosVehicleSource(context))
}
