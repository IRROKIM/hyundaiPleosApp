package com.routesoundtrack

import android.app.Application
import com.routesoundtrack.data.DriveInput
import com.routesoundtrack.data.DriveInputs
import com.routesoundtrack.data.SettingsStore
import com.routesoundtrack.data.sdk.UxRestrictionSource
import com.routesoundtrack.session.DriveSession
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob

/** 수동 DI: 앱 전체에서 쓰는 객체를 이 한 곳에서 만들고 연결한다. */
class AppContainer(app: Application) {
    val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    val settingsStore = SettingsStore(app)
    val input: DriveInput = DriveInputs.create(app)    // Pleos SDK (디버그 빌드는 시뮬레이터로 바꿀 수 있다)
    val uxRestrictions = UxRestrictionSource(app)
    val session = DriveSession(app, scope, settingsStore, input, uxRestrictions.restricted)
}
