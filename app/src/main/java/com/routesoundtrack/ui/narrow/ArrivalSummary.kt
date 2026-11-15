package com.routesoundtrack.ui.narrow

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.routesoundtrack.R
import com.routesoundtrack.session.UiState
import com.routesoundtrack.ui.feelLabel
import com.routesoundtrack.ui.formatKm
import com.routesoundtrack.ui.theme.PleosType
import com.routesoundtrack.ui.theme.LocalGlassColors
import com.routesoundtrack.ui.theme.MoodColors
import kotlin.math.max

/**
 * 도착 요약 (시안 "도착 요약 · 세로"). 경로를 끝까지 달려 세션이 끝나면(ui.finished) 한 번 보여 준다.
 * 오늘 들은 곡 수, 경로 위 재생 구간 막대, 감정별 칩, 곡 목록. "다음 감정 고르기"를 누르면 메인으로 간다.
 * 주행이 끝난 뒤의 화면이라 스크롤을 허용한다.
 */
@OptIn(ExperimentalLayoutApi::class)   // FlowRow(줄바꿈 Row)는 아직 실험 API다
@Composable
fun ArrivalSummary(ui: UiState, onDone: () -> Unit, modifier: Modifier = Modifier) {
    val cs = MaterialTheme.colorScheme
    val glass = LocalGlassColors.current
    val played = ui.played
    BoxWithConstraints(modifier.fillMaxSize()) {
        val pad = if (maxWidth >= 700.dp) 48.dp else 32.dp
        val compact = maxHeight < 1000.dp
        // 오른쪽 위 장식: 도착(민트 + 하늘·레몬)과 고요해요(라벤더 + 핑크) 메시 번짐. 야간은 옅게.
        val k = if (glass.night) 0.55f else 1f
        Canvas(Modifier.matchParentSize().clearAndSetSemantics { }) {
            val a = MoodColors.arrivalMesh
            val big = Offset(size.width - 110.dp.toPx(), 150.dp.toPx())
            val rb = 230.dp.toPx()
            drawCircle(Brush.radialGradient(0f to a.base.copy(alpha = 0.85f * k), 0.55f to a.base.copy(alpha = 0.35f * k), 1f to Color.Transparent, center = big, radius = rb), rb, big)
            softSpot(a.mixA.copy(alpha = 0.85f * k), big + Offset(-0.32f * rb, 0.24f * rb), 0.56f * rb)
            softSpot(a.mixB.copy(alpha = 0.80f * k), big + Offset(0.24f * rb, -0.28f * rb), 0.48f * rb)
            val q = MoodColors.quietMesh
            val small = Offset(size.width - 290.dp.toPx(), 250.dp.toPx())
            val rs = 130.dp.toPx()
            drawCircle(Brush.radialGradient(0f to q.base.copy(alpha = 0.80f * k), 0.55f to q.base.copy(alpha = 0.30f * k), 1f to Color.Transparent, center = small, radius = rs), rs, small)
            softSpot(q.mixB.copy(alpha = 0.80f * k), small + Offset(0.28f * rs, 0.32f * rs), 0.6f * rs)
        }
        Column(Modifier.fillMaxSize().padding(pad), verticalArrangement = Arrangement.spacedBy(if (compact) 20.dp else 28.dp)) {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(stringResource(R.string.summary_kicker), style = PleosType.LabelS, color = cs.onSurfaceVariant)
                Text(stringResource(R.string.phase_ended), style = if (compact) PleosType.HeadlineS else PleosType.HeadlineL)
                Text(
                    stringResource(R.string.summary_route, ui.destination.ifBlank { stringResource(R.string.destination_unknown) }, formatKm(ui.totalKm)),
                    style = PleosType.LabelM,
                    color = cs.onSurfaceVariant,
                )
            }

            Column(
                Modifier.fillMaxWidth().glassCard(glass, RoundedCornerShape(48.dp)).padding(if (compact) 28.dp else 36.dp),
                verticalArrangement = Arrangement.spacedBy(18.dp),
            ) {
                if (played.isEmpty()) {
                    Text(stringResource(R.string.summary_none), style = PleosType.TitleM)
                } else {
                    Row(verticalAlignment = Alignment.Bottom) {
                        Text(stringResource(R.string.summary_count, played.size), style = PleosType.TitleL, modifier = Modifier.alignByBaseline())
                        Text(stringResource(R.string.summary_count_tail), style = PleosType.LabelM, color = cs.onSurfaceVariant, modifier = Modifier.alignByBaseline())
                    }
                }
                PlayedBar(ui)
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    Text(stringResource(R.string.summary_from), style = PleosType.LabelS, color = cs.onSurfaceVariant)
                    Text(stringResource(R.string.cue_end), style = PleosType.LabelS, color = cs.onSurfaceVariant)
                }
                if (played.isNotEmpty()) {
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(10.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        for (mood in played.map { it.mood }.distinct()) {
                            Row(
                                Modifier.height(44.dp).clip(RoundedCornerShape(percent = 50)).background(cs.onSurface.copy(alpha = 0.06f)).padding(horizontal = 16.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(8.dp),
                            ) {
                                MoodDot(mood, 14.dp)
                                Text(stringResource(feelLabel(mood)), style = PleosType.LabelS)
                            }
                        }
                    }
                }
            }

            Column(Modifier.weight(1f).fillMaxWidth().verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                for (p in played) {
                    Row(
                        Modifier.fillMaxWidth().heightIn(min = 64.dp).padding(horizontal = 8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(16.dp),
                    ) {
                        MoodDot(p.mood, 44.dp)
                        Text(stringResource(feelLabel(p.mood)), modifier = Modifier.weight(1f), style = PleosType.BodyS, fontWeight = PleosType.Strong, maxLines = 1)
                        Text(stringResource(R.string.summary_at, formatKm(p.startKm)), style = PleosType.LabelS, color = cs.onSurfaceVariant, maxLines = 1)
                    }
                }
            }

            Surface(
                onClick = onDone,
                modifier = Modifier.fillMaxWidth().height(92.dp),
                shape = RoundedCornerShape(percent = 50),
                color = cs.primary,
                contentColor = cs.onPrimary,
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Text(stringResource(R.string.summary_again), style = PleosType.BodyL, fontWeight = PleosType.Strong)
                }
            }
        }
    }
}

/** 가운데 색에서 바깥으로 투명해지는 작은 번짐 하나. */
private fun androidx.compose.ui.graphics.drawscope.DrawScope.softSpot(color: Color, at: Offset, r: Float) {
    drawCircle(Brush.radialGradient(listOf(color, Color.Transparent), center = at, radius = r), r, at)
}

/** 경로 막대(출발 → 도착) 위에 실제로 곡이 나온 구간을 곡 성격 색으로 칠한다. 아주 짧은 구간도 보이게 최소 폭을 둔다. */
@Composable
private fun PlayedBar(ui: UiState) {
    val night = LocalGlassColors.current.night
    val track = MaterialTheme.colorScheme.onSurface.copy(alpha = if (night) 0.10f else 0.07f)
    val total = max(ui.totalKm, ui.played.maxOfOrNull { it.endKm } ?: 0.0)
    Canvas(Modifier.fillMaxWidth().height(28.dp).clearAndSetSemantics { }) {
        val r = CornerRadius(size.height / 2)
        drawRoundRect(track, cornerRadius = r)
        if (total <= 0.0) return@Canvas   // return@Canvas: 이 람다만 빠져나간다(Java의 람다 안 return과 같음)
        val minW = size.width * 0.025f
        for (p in ui.played) {
            val left = (p.startKm / total * size.width).toFloat()
            val w = max(minW, ((p.endKm - p.startKm) / total * size.width).toFloat())
            drawRoundRect(MoodColors.dot(p.mood, night), topLeft = Offset(left.coerceAtMost(size.width - w), 0f), size = Size(w, size.height), cornerRadius = r)
        }
    }
}
