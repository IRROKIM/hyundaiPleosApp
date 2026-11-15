package com.routesoundtrack.ui

import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.routesoundtrack.session.UiState
import com.routesoundtrack.ui.components.LocalMoodRipple
import com.routesoundtrack.ui.components.MoodRipple
import com.routesoundtrack.ui.components.MoodRippleLayer
import com.routesoundtrack.ui.narrow.ArrivalSummary
import com.routesoundtrack.ui.narrow.DrivePortrait
import com.routesoundtrack.ui.narrow.MoodHome
import com.routesoundtrack.ui.narrow.NarrowSettings
import com.routesoundtrack.ui.narrow.NarrowTrackList
import com.routesoundtrack.ui.narrow.screenBackdrop
import com.routesoundtrack.ui.theme.LocalGlassColors
import com.routesoundtrack.ui.wide.WideDrive
import com.routesoundtrack.ui.wide.WideHome
import com.routesoundtrack.ui.wide.WideRoute
import com.routesoundtrack.ui.wide.WideTracks

/**
 * 기본 사용 모습은 "내비와 우리 앱을 나란히": Pleos 앱 창이 2개일 때 우리 앱은 세로 창(폭 628–840 dp)에 들어간다.
 * 그래서 세로 1단(narrow)이 주 화면이다. 창이 1개(가로, 폭 900 dp 이상)일 때는 가로 화면(wide)을 쓴다.
 * 창 위치·크기는 사용자와 시스템이 정한다. 앱은 주어진 폭에 맞출 뿐이다(Pleos Display Layout).
 */
@Composable
fun MainScreen(vm: MainViewModel) {
    val ui by vm.state.collectAsStateWithLifecycle()   // by: 위임. ui.xxx로 바로 읽는다
    Surface(modifier = Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
        // 모든 화면 맨 뒤: 주간 오프화이트 / 야간 어스름 보라 + 옅은 번짐 (디자인 시안 v3)
        BoxWithConstraints(Modifier.fillMaxSize().screenBackdrop(LocalGlassColors.current)) {
            // 감정 원을 누르면 그 곡 색이 파동처럼 번진다. 바탕 위·화면 내용 아래에 그려 글자를 가리지 않는다
            val ripple = remember { MoodRipple() }
            MoodRippleLayer(ripple, night = LocalGlassColors.current.night, enabled = !ui.locked, modifier = Modifier.fillMaxSize())
            CompositionLocalProvider(LocalMoodRipple provides ripple) {
                Pages(ui, vm, wide = maxWidth >= 900.dp)
            }
        }
    }
}

/**
 * 화면은 다섯 가지이고 세로·가로가 같은 흐름을 쓴다 (시안 "메인 → 경로 · 설정 / 곡 목록", "주행", "도착 요약").
 * - 메인 "지금 당신의 감정은 어디로 향하고 있나요?": 원을 누르면 그 색 곡으로 켜고 첫 곡을 바로 튼다. 켜진 뒤 정차 중이면 메인에 남아 "재생 중" 상태
 * - 경로 미리보기(경로 · 설정) · 곡 목록: 메인의 버튼으로 들어가고 "메인"으로 돌아온다
 * - 주행 화면: 켜져 있고 (주행 중 | 목적지 없음 | 사용자가 "주행 화면"을 누름)일 때. 주행 중이면 자동으로 바뀐다
 * - 도착 요약: 경로 끝까지 도착하면 한 번
 */
private enum class Page { HOME, ROUTE, TRACKS }

@Composable
private fun Pages(ui: UiState, vm: MainViewModel, wide: Boolean) {
    var page by rememberSaveable { mutableStateOf(Page.HOME) }              // 화면 회전·창 크기 변경에도 유지
    var driveOpen by rememberSaveable { mutableStateOf(false) }             // 정차 중에 "주행 화면" 버튼으로 열었는가
    var summaryClosed by rememberSaveable { mutableStateOf(false) }         // 도착 요약을 닫았는가
    LaunchedEffect(ui.running) { if (!ui.running) driveOpen = false }       // 끄면 주행 화면을 닫는다
    LaunchedEffect(ui.finished) { if (!ui.finished) summaryClosed = false } // 새 경로가 생기면 다음 도착 때 다시 보여 준다

    val home = { page = Page.HOME }
    val openDrive = { driveOpen = true }
    // 주행 중(잠금)이거나 목적지가 아직 없으면(경로 대기) 주행 화면을 강제로 보여 주고, 메인으로 돌아가는 버튼은 숨긴다
    val forced = ui.locked || !ui.hasRoute
    // 감정 원을 누른 직후에는 색 파동이 퍼질 동안(0.9초) 메인에 머문 뒤 넘어간다. 주행 중 잠금이면 기다리지 않는다
    val hold = LocalMoodRipple.current.spreading && !ui.locked
    when {
        ui.running && ((forced && !hold) || driveOpen) -> {
            val back: (() -> Unit)? = if (forced) null else { { driveOpen = false } }
            if (wide) WideDrive(ui, onStop = vm::toggle, onBack = back) else DrivePortrait(ui, onStop = vm::toggle, onBack = back)
        }
        ui.finished && !summaryClosed -> ArrivalSummary(ui, onDone = { summaryClosed = true; page = Page.HOME })
        page == Page.ROUTE -> {
            val drive = if (ui.running) openDrive else null
            if (wide) WideRoute(ui, vm, onBack = home, onOpenDrive = drive) else NarrowSettings(ui, vm, onBack = home, onOpenDrive = drive)
        }
        page == Page.TRACKS -> if (wide) WideTracks(ui, onBack = home) else NarrowTrackList(ui, onBack = home)
        wide -> WideHome(
            ui,
            onPick = vm::startWithMood,
            onStop = vm::toggle,
            onOpenDrive = openDrive,
            onOpenSettings = { page = Page.ROUTE },
            onOpenTracks = { page = Page.TRACKS },
        )
        else -> MoodHome(
            ui,
            onPick = vm::startWithMood,
            onStop = vm::toggle,
            onOpenDrive = openDrive,
            onOpenSettings = { page = Page.ROUTE },
            onOpenTracks = { page = Page.TRACKS },
        )
    }
}
