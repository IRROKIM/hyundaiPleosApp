package com.routesoundtrack

import android.app.Application
import com.routesoundtrack.audio.SoundtrackService
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch

class App : Application() {
    lateinit var container: AppContainer
        private set

    override fun onCreate() {
        super.onCreate()
        container = AppContainer(this)
        // 주행 세션이 시작될 때만 미디어 서비스를 띄운다. 세션이 끝나면 서비스가 스스로 내려간다.
        container.scope.launch {
            container.session.ui.map { it.running }.distinctUntilChanged().collect { running ->
                if (running) SoundtrackService.start(this@App)
            }
        }
    }
}
