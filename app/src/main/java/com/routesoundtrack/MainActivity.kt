package com.routesoundtrack

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.routesoundtrack.ui.MainScreen
import com.routesoundtrack.ui.MainViewModel
import com.routesoundtrack.ui.theme.RouteSoundtrackTheme
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val session = (application as App).container.session
        setContent {
            RouteSoundtrackTheme {
                val vm: MainViewModel = viewModel(factory = MainViewModel.factory(session))
                MainScreen(vm)
            }
        }
        // 화면이 보이는 동안에만 5초마다 내비 경로를 읽어 미리보기(목적지·재생 지점)를 갱신한다.
        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                while (true) {
                    session.refreshRoute()
                    delay(ROUTE_REFRESH_MS)
                }
            }
        }
    }

    private companion object {
        const val ROUTE_REFRESH_MS = 5_000L
    }
}
