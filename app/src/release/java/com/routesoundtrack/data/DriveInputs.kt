package com.routesoundtrack.data

import android.content.Context
import com.routesoundtrack.data.sdk.PleosNaviSource
import com.routesoundtrack.data.sdk.PleosVehicleSource
import com.routesoundtrack.data.sdk.SdkDriveInput

/** 릴리스 빌드용 입력: 항상 Pleos SDK. (시나리오 시뮬레이터는 디버그 빌드에만 있다.) */
object DriveInputs {
    fun create(context: Context): DriveInput =
        SdkDriveInput(PleosNaviSource(context), PleosVehicleSource(context))
}
