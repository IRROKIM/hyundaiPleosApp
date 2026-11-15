package com.routesoundtrack.ui.narrow

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.em
import androidx.compose.ui.unit.sp
import com.routesoundtrack.R
import com.routesoundtrack.session.UiState
import com.routesoundtrack.ui.MainViewModel
import com.routesoundtrack.ui.components.RouteHero
import com.routesoundtrack.ui.components.RouteSummaryCard
import com.routesoundtrack.ui.components.SettingsCard
import com.routesoundtrack.ui.theme.PleosType
import com.routesoundtrack.ui.theme.MoodColors
import com.routesoundtrack.ui.theme.SoftIcons

/**
 * 세로 1단의 "경로 미리보기" 화면 (시안 "경로 · 설정"을 한 줄로 쌓음). 메인의 "경로 · 설정" 버튼으로 들어오고 "메인"으로 돌아간다.
 * 위에서부터 목적지 그림 · 재생 지점 요약 · 설정 카드. 정차 중에만 바꿀 수 있다(SettingsCard가 잠근다). 주행 화면이 아니라 스크롤을 허용한다.
 * onOpenDrive: 사운드트랙이 켜져 있을 때만 준다(그림 위 "주행 화면으로" 버튼).
 */
@Composable
fun NarrowSettings(ui: UiState, vm: MainViewModel, onBack: () -> Unit, onOpenDrive: (() -> Unit)?, modifier: Modifier = Modifier) {
    BoxWithConstraints(modifier.fillMaxSize()) {
        val pad = if (maxWidth >= 700.dp) 48.dp else 32.dp
        val roomy = maxWidth >= 760.dp
        val heroH = if (maxHeight < 1000.dp) 300.dp else 420.dp
        Column(
            Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(pad),
            verticalArrangement = Arrangement.spacedBy(24.dp),
        ) {
            Row(Modifier.fillMaxWidth().height(76.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(24.dp)) {
                PillButton(stringResource(R.string.back_main), onBack, SoftIcons.Back)
                Text(stringResource(R.string.route_preview), style = PleosType.TitleL, maxLines = 1)
                if (roomy) {
                    Spacer(Modifier.weight(1f))
                    DriveStateChip(ui)
                }
            }
            RouteHero(ui, onOpenDrive, Modifier.fillMaxWidth().height(heroH), inset = 36.dp)
            RouteSummaryCard(ui, Modifier.fillMaxWidth(), showThumb = false)
            SettingsCard(ui, vm::setPreset, vm::setDensity, vm::setMoodMode, vm::setAutoStart, vm::toggle, Modifier.fillMaxWidth())
        }
    }
}

/** "정차 중"(민트 점) / "주행 중"(코랄 점) 상태 칩. 주행 중이면 설정이 잠긴다는 신호다. */
@Composable
internal fun DriveStateChip(ui: UiState, height: androidx.compose.ui.unit.Dp = 56.dp) {
    StateChip(
        label = stringResource(if (ui.locked) R.string.driving_chip else R.string.state_parked),
        dot = if (ui.locked) MoodColors.upbeatMesh.dot else MoodColors.arrivalMesh.dot,
        height = height,
    )
}
