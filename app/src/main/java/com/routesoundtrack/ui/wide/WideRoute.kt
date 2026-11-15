package com.routesoundtrack.ui.wide

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
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
import com.routesoundtrack.ui.narrow.DriveStateChip
import com.routesoundtrack.ui.narrow.PillButton
import com.routesoundtrack.ui.theme.PleosType
import com.routesoundtrack.ui.theme.SoftIcons

/**
 * 가로(앱 창 1개) "경로 미리보기" (시안 Route · 1682×1210 기준).
 * 왼쪽: 목적지 그림 + 재생 지점 요약(썸네일 포함), 오른쪽: 설정 카드(켜기 버튼이 맨 아래). 주행 중이면 설정이 잠긴다.
 * onOpenDrive: 사운드트랙이 켜져 있을 때만 준다(그림 위 "주행 화면으로" 버튼).
 */
@Composable
fun WideRoute(ui: UiState, vm: MainViewModel, onBack: () -> Unit, onOpenDrive: (() -> Unit)?, modifier: Modifier = Modifier) {
    BoxWithConstraints(modifier.fillMaxSize()) {
        val tall = maxHeight >= 1000.dp
        Column(Modifier.fillMaxSize().padding(56.dp), verticalArrangement = Arrangement.spacedBy(32.dp)) {
            Row(Modifier.fillMaxWidth().height(76.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(28.dp)) {
                PillButton(stringResource(R.string.back_main), onBack, SoftIcons.Back)
                Text(stringResource(R.string.route_preview), style = PleosType.TitleL, maxLines = 1)
                Spacer(Modifier.weight(1f))
                DriveStateChip(ui)
            }
            Row(Modifier.weight(1f).fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(40.dp)) {
                Column(Modifier.weight(900f).fillMaxHeight(), verticalArrangement = Arrangement.spacedBy(24.dp)) {
                    RouteHero(ui, onOpenDrive, Modifier.weight(1f).fillMaxWidth())
                    RouteSummaryCard(ui, Modifier.fillMaxWidth(), thumb = if (tall) 240.dp else 168.dp)
                }
                SettingsCard(
                    ui, vm::setPreset, vm::setDensity, vm::setMoodMode, vm::setAutoStart, vm::toggle,
                    Modifier.weight(630f).fillMaxHeight(),
                    fill = true,
                )
            }
        }
    }
}
