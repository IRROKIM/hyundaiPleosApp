package com.routesoundtrack.ui.wide

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.em
import androidx.compose.ui.unit.sp
import com.routesoundtrack.R
import com.routesoundtrack.domain.model.CueKind
import com.routesoundtrack.domain.model.Mood
import com.routesoundtrack.domain.model.MoodMode
import com.routesoundtrack.session.UiState
import com.routesoundtrack.ui.narrow.BlobField
import com.routesoundtrack.ui.narrow.DottedIconCircle
import com.routesoundtrack.ui.narrow.PillButton
import com.routesoundtrack.ui.narrow.PlayingBar
import com.routesoundtrack.ui.narrow.fixedMood
import com.routesoundtrack.ui.narrow.routeLine
import com.routesoundtrack.ui.theme.PleosType
import com.routesoundtrack.ui.theme.LocalGlassColors
import com.routesoundtrack.ui.theme.MoodColors
import com.routesoundtrack.ui.theme.SoftIcons
import kotlin.math.roundToInt

/**
 * 가로(앱 창 1개) 메인 "지금 당신의 감정은 어디로 향하고 있나요?" (시안 Main · 1682×1210 기준).
 * 왼쪽 위 제목, 오른쪽에 감정 원 4개가 흩어져 있고, 아래에 오늘 경로(눈금 막대)와 경로 미리보기/재생 중 막대.
 * 동작은 세로 메인(MoodHome)과 같다: 원을 누르면 그 색의 곡이 바로 나오고, 켜진 뒤 다른 원을 누르면 바뀐다.
 * 창 크기가 시안과 다르면 원 위치는 비율로, 원 크기는 짧은 쪽 비율로 맞춘다.
 */
@Composable
fun WideHome(
    ui: UiState,
    onPick: (MoodMode) -> Unit,
    onStop: () -> Unit,
    onOpenDrive: () -> Unit,
    onOpenSettings: () -> Unit,
    onOpenTracks: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val cs = MaterialTheme.colorScheme
    BoxWithConstraints(modifier.fillMaxSize()) {
        val scale = minOf(maxWidth / 1682.dp, maxHeight / 1210.dp)
        val core = (260.dp * scale).coerceIn(170.dp, 260.dp)
        BlobField(
            ui = ui,
            core = core,
            wordStyle = if (core >= 210.dp) PleosType.TitleL else PleosType.TitleM,
            place = { it.screen },
            onPick = onPick,
            modifier = Modifier.fillMaxSize(),
            genreSpacing = 0.1.em,
        )

        Row(
            Modifier.fillMaxWidth().padding(start = 72.dp, end = 72.dp, top = 40.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            Text("Route Soundtrack", style = PleosType.BodyM, fontWeight = PleosType.Strong, color = cs.onSurfaceVariant, maxLines = 1)
            Spacer(Modifier.weight(1f))
            PillButton(stringResource(R.string.open_tracks), onOpenTracks, SoftIcons.List)
            PillButton(stringResource(R.string.open_settings), onOpenSettings, SoftIcons.Route)
        }

        Column(Modifier.padding(start = 72.dp, top = 156.dp).width(540.dp), verticalArrangement = Arrangement.spacedBy(22.dp)) {
            Text(stringResource(R.string.feel_title), style = PleosType.HeadlineL)
            Text(
                stringResource(if (ui.running) R.string.feel_hint_playing_wide else R.string.feel_hint),
                style = PleosType.LabelM, color = cs.onSurfaceVariant,
            )
        }

        Row(
            Modifier.align(Alignment.BottomStart).fillMaxWidth().padding(start = 72.dp, end = 72.dp, bottom = 48.dp).height(112.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(48.dp),
        ) {
            TodayRoute(ui, Modifier.weight(1f))
            if (ui.running) {
                PlayingBar(ui, onStop, onOpenDrive, Modifier.weight(1f), dotSize = 28.dp, titleStyle = PleosType.BodyM)
            } else {
                PreviewPill(onOpenSettings)
            }
        }
    }
}

/** 오늘 경로: "오늘 경로 강릉 경포해변 120 km · …" 한 줄 + 경로를 48칸으로 나눈 눈금. 재생 지점 칸은 곡 성격 색으로 길게. */
@Composable
private fun TodayRoute(ui: UiState, modifier: Modifier) {
    val cs = MaterialTheme.colorScheme
    val glass = LocalGlassColors.current
    Column(modifier, verticalArrangement = Arrangement.spacedBy(16.dp)) {
        Row(verticalAlignment = Alignment.Bottom, horizontalArrangement = Arrangement.spacedBy(16.dp)) {
            Text(stringResource(R.string.today_label), style = PleosType.LabelS, color = cs.onSurfaceVariant, maxLines = 1, modifier = Modifier.alignByBaseline())
            Text(
                if (ui.hasRoute) ui.destination.ifBlank { stringResource(R.string.destination_unknown) } else stringResource(R.string.no_route),
                style = PleosType.BodyS, fontWeight = PleosType.Strong, maxLines = 1, modifier = Modifier.alignByBaseline(),
            )
            Text(
                routeLine(ui), style = PleosType.LabelM, color = cs.onSurfaceVariant, maxLines = 1, overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f, fill = false).alignByBaseline(),
            )
        }
        val marks = cueTicks(ui)
        Row(
            Modifier.fillMaxWidth().height(44.dp).clearAndSetSemantics { },
            verticalAlignment = Alignment.Bottom,
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            for (i in 0 until TICKS) {
                val c = marks[i]
                Box(
                    Modifier
                        .width(if (c != null) 6.dp else 3.dp)
                        .height(if (c != null) 44.dp else 20.dp)
                        .clip(RoundedCornerShape(3.dp))
                        .background(c ?: glass.tick)
                )
            }
        }
    }
}

private const val TICKS = 48

/** 눈금 칸 번호 → 그 칸의 재생 지점 색. 도착 곡은 민트, 나머지는 고른 곡 성격 색(자동이면 라일락 회색). */
@Composable
private fun cueTicks(ui: UiState): Map<Int, androidx.compose.ui.graphics.Color> {
    val night = LocalGlassColors.current.night
    val total = ui.totalKm
    if (total <= 0.0) return emptyMap()
    val fixed = fixedMood(ui.settings.moodMode)
    return ui.plan?.cues.orEmpty().associate { c ->   // associate: 목록을 (키 to 값) 맵으로 바꾼다
        val km = if (c.kind == CueKind.ARRIVAL) ui.arrivalKm ?: c.atKm else c.atKm
        val i = (km / total * (TICKS - 1)).roundToInt().coerceIn(0, TICKS - 1)
        i to MoodColors.dot(if (c.kind == CueKind.ARRIVAL) Mood.ARRIVAL else fixed, night)
    }
}

/** 꺼져 있을 때 오른쪽 아래: "경로 미리보기 · 재생 지점과 설정 보기". */
@Composable
private fun PreviewPill(onOpenSettings: () -> Unit) {
    val cs = MaterialTheme.colorScheme
    val glass = LocalGlassColors.current
    Surface(
        onClick = onOpenSettings,
        shape = RoundedCornerShape(percent = 50),
        color = glass.pill,
        contentColor = cs.onSurface,
        border = BorderStroke(1.5.dp, glass.line),
    ) {
        Row(
            Modifier.height(96.dp).padding(start = 12.dp, end = 32.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(18.dp),
        ) {
            DottedIconCircle(SoftIcons.Route, 72.dp, 28.dp)
            Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text(stringResource(R.string.route_preview), style = PleosType.BodyS, fontWeight = PleosType.Strong, maxLines = 1)
                Text(stringResource(R.string.route_preview_sub), style = PleosType.LabelS, color = cs.onSurfaceVariant, maxLines = 1)
            }
        }
    }
}
