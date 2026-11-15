package com.routesoundtrack.ui.narrow

import android.view.HapticFeedbackConstants
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.em
import androidx.compose.ui.unit.sp
import com.routesoundtrack.R
import com.routesoundtrack.domain.model.CueKind
import com.routesoundtrack.domain.model.Mood
import com.routesoundtrack.domain.model.MoodMode
import com.routesoundtrack.domain.model.Road
import com.routesoundtrack.session.Phase
import com.routesoundtrack.session.UiState
import com.routesoundtrack.ui.etaText
import com.routesoundtrack.ui.feelLabel
import com.routesoundtrack.ui.formatDuration
import com.routesoundtrack.ui.formatKm
import com.routesoundtrack.ui.formatKmShort
import com.routesoundtrack.ui.roadLabel
import com.routesoundtrack.ui.theme.PleosType
import com.routesoundtrack.ui.theme.LocalGlassColors
import com.routesoundtrack.ui.theme.LocalStatusColors
import com.routesoundtrack.ui.theme.MoodColors
import com.routesoundtrack.ui.theme.SoftIcons
import kotlin.math.exp
import kotlin.math.min

/**
 * 주행 화면 (세로 1단, 시안 musicBar_01). 내비 옆 좁은 창에서 한눈에 읽히도록:
 * - 위: 글래스 카드에 "다음 곡까지 N km"(가장 큰 글자), 진행 곡선(창 높이가 충분할 때만), 재생 지점 칩
 * - 아래: 2×2 카드 — 지금 도로 · 지금 곡 · 도착까지 · 사운드트랙 끄기
 * 상태(시안 "주행 중 상태"): 경로 대기(원을 눌렀는데 목적지 없음) · 고속 구간 대기 · 도착 곡 재생 중은 큰 숫자 대신 문장으로 보여 준다.
 * 계속 움직이는 효과는 없다. 값은 1초 틱마다 바뀔 뿐이다(Pleos 주행 중 애니메이션 금지).
 * onBack: 정차 중에 메인의 "주행 화면" 버튼으로 열었을 때만 준다. 그때는 "주행 중" 칩 자리에 "메인" 버튼이 나온다.
 */
@Composable
fun DrivePortrait(ui: UiState, onStop: () -> Unit, modifier: Modifier = Modifier, onBack: (() -> Unit)? = null) {
    val glass = LocalGlassColors.current
    BoxWithConstraints(modifier.fillMaxSize().softGlow(glass)) {
        val compact = maxHeight < 1000.dp
        val pad = if (maxWidth >= 700.dp) 48.dp else 32.dp
        val gap = if (compact) 16.dp else 20.dp
        val pillH = if (compact) 160.dp else 200.dp
        val valueSize = if (compact) PleosType.TitleL else PleosType.HeadlineS   // 카드의 큰 값 글자 (Pleos 중요 글자 32 dp 이상)
        Column(Modifier.fillMaxSize().padding(pad), verticalArrangement = Arrangement.spacedBy(gap)) {
            NextCueCard(ui, compact, onBack, Modifier.weight(1f).fillMaxWidth())
            Row(Modifier.fillMaxWidth().height(pillH), horizontalArrangement = Arrangement.spacedBy(gap)) {
                RoadPill(ui, valueSize, Modifier.weight(1f).fillMaxHeight())
                NowPill(ui, valueSize, Modifier.weight(1f).fillMaxHeight())
            }
            Row(Modifier.fillMaxWidth().height(pillH), horizontalArrangement = Arrangement.spacedBy(gap)) {
                EtaPill(ui, valueSize, Modifier.weight(1f).fillMaxHeight())
                StopPill(onStop, Modifier.weight(1f).fillMaxHeight())
            }
        }
    }
}

// ---- 위: 다음 곡 카드 --------------------------------------------------------

@Composable
private fun NextCueCard(ui: UiState, compact: Boolean, onBack: (() -> Unit)?, modifier: Modifier) {
    val glass = LocalGlassColors.current
    val waiting = !ui.hasRoute
    Column(
        modifier.glassCard(glass, RoundedCornerShape(48.dp)).padding(if (compact) 28.dp else 36.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        CardHeader(ui, onBack)
        BigValue(
            ui,
            numSize = if (compact) 120.sp else 150.sp,
            unit = PleosType.TitleL,
            sentence = if (compact) PleosType.TitleL else PleosType.HeadlineM,
        )
        // 남는 높이: 평소에는 진행 곡선, 경로 대기 중에는 "준비된 첫 곡" 원. 100 dp도 안 남는 낮은 창에서는 비운다.
        BoxWithConstraints(Modifier.weight(1f).fillMaxWidth(), contentAlignment = Alignment.Center) {
            if (waiting) {
                val blob = minOf(maxHeight, 300.dp)
                if (blob >= 160.dp) WaitingBlob(ui, blob)
            } else if (maxHeight >= 100.dp) {
                RouteCurve(ui, Modifier.fillMaxSize())
            }
        }
        if (!waiting) CueChips(ui, if (compact) 72.dp else 84.dp)
    }
}

@Composable
private fun CardHeader(ui: UiState, onBack: (() -> Unit)?) {
    val cs = MaterialTheme.colorScheme
    Row(verticalAlignment = Alignment.Top, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(stringResource(cardHead(ui)), style = PleosType.BodyS, fontWeight = PleosType.Strong, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text(cueCaption(ui), style = PleosType.LabelS, color = cs.onSurfaceVariant, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
        if (onBack != null) {
            PillButton(stringResource(R.string.back_main), onBack, SoftIcons.Back)
        } else {
            Row(
                Modifier.height(48.dp).clip(RoundedCornerShape(percent = 50)).background(cs.onSurface.copy(alpha = 0.06f)).padding(horizontal = 20.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Box(Modifier.size(10.dp).clip(CircleShape).background(stateDot(ui)))
                Text(stringResource(R.string.driving_chip), style = PleosType.LabelS, color = cs.onSurfaceVariant)
            }
        }
    }
}

/** 카드 머리글: 경로 대기 · 도착 곡 · 다음 곡까지. */
internal fun cardHead(ui: UiState): Int = when {
    !ui.hasRoute -> R.string.armed_waiting
    ui.phase == Phase.ARRIVAL || ui.phase == Phase.ARRIVAL_WAIT -> R.string.arrival_head
    else -> R.string.next_cue
}

/** 주행 중 칩의 작은 점: 재생 중·대기 = 민트, 경로 대기 = 노랑, 고속 구간 대기 = 고속도로 색. */
@Composable
internal fun stateDot(ui: UiState): Color {
    val night = LocalGlassColors.current.night
    return when {
        !ui.hasRoute -> MoodColors.dot(Mood.HYPE, night)
        ui.phase == Phase.DEFERRED -> LocalStatusColors.current.highway
        else -> MoodColors.dot(Mood.ARRIVAL, night)
    }
}

/** "35 km 지점 · 설레요" 같은 다음 큐 설명. 곡 성격을 직접 골랐으면 감정 문구도 붙인다. */
@Composable
internal fun cueCaption(ui: UiState): String {
    val mood = fixedMood(ui.settings.moodMode)
    if (!ui.hasRoute) return mood?.let { stringResource(feelLabel(it)) }.orEmpty()
    val cue = ui.nextCue ?: return ""
    val where = when (cue.kind) {
        CueKind.START -> stringResource(R.string.cue_cap_start, formatKmShort(cue.atKm))
        CueKind.MID -> stringResource(R.string.cue_cap_mid, formatKmShort(cue.atKm))
        CueKind.ARRIVAL -> return stringResource(R.string.cue_cap_arrival)
    }
    return if (mood == null) where else "$where · ${stringResource(feelLabel(mood))}"
}

/**
 * 가장 큰 글자: 다음 곡까지 남은 km(가이드 단계보다 큰 숫자 = PleosType.display). 거리를 말할 수 없는 단계는 문장(+ 한 줄 설명)으로 보여 준다.
 * unit = "km" 글자 단계, sentence = 문장 단계.
 */
@Composable
internal fun BigValue(ui: UiState, numSize: TextUnit, unit: TextStyle, sentence: TextStyle) {
    val words: Pair<Int, Int?>? = when {             // (큰 문장, 설명) 또는 null = 숫자를 보여 준다
        !ui.hasRoute -> R.string.drive_wait_title to R.string.drive_wait_detail
        ui.phase == Phase.DEFERRED -> R.string.deferred_title to R.string.phase_deferred_detail
        ui.phase == Phase.ARRIVAL -> R.string.phase_arrival_wait to R.string.arrival_detail
        ui.phase == Phase.ARRIVAL_WAIT -> R.string.phase_arrival_wait to null
        ui.phase == Phase.ENDED -> R.string.phase_ended to null
        ui.phase == Phase.OFF -> R.string.phase_off to null
        ui.nextCueInKm == null -> R.string.phase_no_more to null
        else -> null
    }
    if (words != null) {
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(stringResource(words.first), style = sentence)
            words.second?.let { detail ->
                Text(stringResource(detail), style = PleosType.LabelM, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
        return
    }
    Row(verticalAlignment = Alignment.Bottom, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        Text(
            formatKm(ui.nextCueInKm ?: 0.0),
            style = PleosType.display(numSize),
            letterSpacing = (-0.03).em,
            modifier = Modifier.alignByBaseline(),
        )
        Text(stringResource(R.string.unit_km), style = unit, modifier = Modifier.alignByBaseline())
    }
}

/** 경로 대기: 고른 곡 성격의 원을 보여 주며 "첫 곡 준비 완료". 자동이면 중간 로우파이 색으로. */
@Composable
internal fun WaitingBlob(ui: UiState, size: Dp) {
    val mood = fixedMood(ui.settings.moodMode)
    SoftBlob(
        mesh = MoodColors.mesh(mood ?: Mood.GROOVE),
        size = size,
        title = stringResource(if (mood != null) feelLabel(mood) else R.string.mood_mode_auto),
        caption = stringResource(R.string.ready_first),
    )
}

/**
 * 진행 곡선: 0 km → 총거리. 봉우리 = 재생 지점, 칠한 부분 = 지나온 구간, 점 = 현재 위치.
 * 숫자를 읽히려는 그래프가 아니라 "어디쯤에서 음악이 나오는지"를 흘끗 보는 용도다.
 */
@Composable
internal fun RouteCurve(ui: UiState, modifier: Modifier) {
    val plan = ui.plan ?: return
    val total = ui.totalKm
    if (total <= 0.0) return
    val glass = LocalGlassColors.current
    val grid = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.14f)
    // 봉우리 위치와 높이(0..1). 도착 곡은 ETA로 움직이므로 추정 위치를 쓴다.
    val peaks = plan.cues.map { c ->
        val km = if (c.kind == CueKind.ARRIVAL) ui.arrivalKm ?: c.atKm else c.atKm
        val h = when (c.kind) { CueKind.START -> 0.65; CueKind.MID -> 0.9; CueKind.ARRIVAL -> 0.75 }
        km to h   // to: 두 값을 Pair로 묶는다
    }
    Canvas(modifier.clearAndSetSemantics { }) {
        val w = size.width
        val base = size.height - 12.dp.toPx()
        val amp = base - 26.dp.toPx()
        val sigma = total * 0.055
        fun x(km: Double) = (km / total * w).toFloat().coerceIn(0f, w)
        fun y(km: Double): Float {
            var v = 0.0
            for ((pk, ph) in peaks) v += ph * exp(-((km - pk) / sigma).let { it * it })   // 구조 분해: Pair를 (pk, ph)로 나눠 받는다
            return base - (min(v, 1.0) * amp).toFloat()
        }

        // 재생 지점 세로선: 둥근 점선
        val dots = PathEffect.dashPathEffect(floatArrayOf(2.dp.toPx(), 10.dp.toPx()))
        for ((pk, _) in peaks) {
            drawLine(grid, Offset(x(pk), 8.dp.toPx()), Offset(x(pk), base), 1.5.dp.toPx(), cap = StrokeCap.Round, pathEffect = dots)
        }

        val now = ui.progressKm.coerceIn(0.0, total)
        val line = Path()
        val area = Path().apply { moveTo(0f, base) }
        val steps = 240
        for (i in 0..steps) {
            val km = total * i / steps
            if (i == 0) line.moveTo(x(km), y(km)) else line.lineTo(x(km), y(km))
            if (km <= now) area.lineTo(x(km), y(km))
        }
        area.lineTo(x(now), y(now))
        area.lineTo(x(now), base)
        area.close()
        drawPath(area, glass.area)
        drawPath(line, glass.curve, style = Stroke(6.dp.toPx(), cap = StrokeCap.Round, join = StrokeJoin.Round))

        val c = Offset(x(now), y(now))
        drawCircle(glass.area, 24.dp.toPx(), c)
        drawCircle(glass.curve, 11.dp.toPx(), c)
        drawCircle(glass.dotRing, 11.dp.toPx(), c, style = Stroke(4.dp.toPx()))
    }
}

/** 재생 지점 칩: 지난 곳(옅은 면) · 지금 곡(채움) · 다음(굵은 테두리, 미룬 곳은 점선) · 나머지. 6개가 넘으면 다음 지점 주변 6개만. */
@Composable
internal fun CueChips(ui: UiState, maxChip: Dp, labelStyle: TextStyle = PleosType.BodyS) {
    val plan = ui.plan ?: return
    val cs = MaterialTheme.colorScheme
    val cues = plan.cues.sortedBy { if (it.kind == CueKind.ARRIVAL) Double.MAX_VALUE else it.atKm }
    val playingId: Int? = when (ui.phase) {
        Phase.ARRIVAL -> plan.arrivalCue?.id
        Phase.PLAYING -> plan.playable.filter { it.id in ui.consumedCueIds }.maxByOrNull { it.atKm }?.id
        else -> null
    }
    val nextIdx = cues.indexOfFirst { it.id == ui.nextCue?.id }.coerceAtLeast(0)
    val from = if (cues.size <= 6) 0 else (nextIdx - 1).coerceIn(0, cues.size - 6)
    val shown = cues.drop(from).take(6)
    val arrivalLabel = stringResource(R.string.cue_end)
    BoxWithConstraints(Modifier.fillMaxWidth()) {
        val chip = minOf(maxChip, (maxWidth - 12.dp * (shown.size - 1)) / shown.size.coerceAtLeast(1))
        Row(Modifier.fillMaxWidth().clearAndSetSemantics { }, horizontalArrangement = Arrangement.SpaceBetween) {
            for (cue in shown) {
                val label = if (cue.kind == CueKind.ARRIVAL) arrivalLabel else formatKmShort(cue.atKm)
                val playing = cue.id == playingId
                val held = cue.id == ui.deferredCueId
                val next = cue.id == ui.nextCue?.id
                val done = !playing && !held && cue.id in ui.consumedCueIds
                val bg = when {
                    playing -> cs.primary
                    done -> cs.onSurface.copy(alpha = 0.06f)
                    else -> Color.Transparent
                }
                val fg = when { playing -> cs.onPrimary; done -> cs.onSurfaceVariant; else -> cs.onSurface }
                Box(
                    Modifier
                        .size(chip)
                        .alpha(if (done) 0.8f else 1f)
                        .clip(CircleShape)
                        .background(bg)
                        .then(
                            when {
                                playing || done -> Modifier
                                held -> Modifier.dashedCircle(cs.onSurface)
                                next -> Modifier.border(BorderStroke(3.dp, cs.onSurface), CircleShape)
                                else -> Modifier.border(BorderStroke(1.5.dp, cs.onSurface.copy(alpha = 0.14f)), CircleShape)
                            }
                        ),
                    contentAlignment = Alignment.Center,
                ) {
                    Text(label, style = labelStyle, fontWeight = PleosType.Strong, color = fg, maxLines = 1)
                }
            }
        }
    }
}

/** 점선 원 테두리: 고속 구간이라 미뤄 둔 재생 지점. */
private fun Modifier.dashedCircle(color: Color): Modifier = drawBehind {
    val stroke = 3.dp.toPx()
    drawCircle(
        color,
        radius = size.minDimension / 2 - stroke / 2,
        style = Stroke(stroke, pathEffect = PathEffect.dashPathEffect(floatArrayOf(8.dp.toPx(), 6.dp.toPx()))),
    )
}

// ---- 아래: 2×2 카드 ----------------------------------------------------------

/**
 * 글래스 카드 + 두 가지 색 기운(시안 v3 메시): tintA는 오른쪽 위, tintB는 왼쪽 아래에서 번진다.
 * 라벨이 있는 왼쪽 위는 비워 두어 보조 글자 대비(4.5:1)를 지킨다. 라벨 → 큰 값 → 보조 줄 순서로 위아래에 붙인다.
 */
@Composable
internal fun InfoPill(
    tintA: Color,
    tintB: Color,
    modifier: Modifier,
    radius: Dp = 48.dp,
    padding: PaddingValues = PaddingValues(horizontal = 28.dp, vertical = 24.dp),
    content: @Composable ColumnScope.() -> Unit,
) {
    val glass = LocalGlassColors.current
    val shape = RoundedCornerShape(radius)
    val a = if (glass.night) 0.30f else 0.55f
    val b = if (glass.night) 0.22f else 0.38f
    Column(
        modifier
            .clip(shape)
            .background(glass.glass)
            .drawBehind {
                drawRect(Brush.radialGradient(listOf(tintA.copy(alpha = a), Color.Transparent), center = Offset(size.width, 0f), radius = size.width * 0.75f))
                drawRect(Brush.radialGradient(listOf(tintB.copy(alpha = b), Color.Transparent), center = Offset(0f, size.height), radius = size.width * 0.7f))
            }
            .border(1.5.dp, glass.glassLine, shape)
            .padding(padding),
        verticalArrangement = Arrangement.SpaceBetween,
        content = content,
    )
}

@Composable
private fun PillLabel(text: String) {
    Text(text, style = PleosType.LabelS, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1, overflow = TextOverflow.Ellipsis)
}

@Composable
private fun PillValue(text: String, style: TextStyle, modifier: Modifier = Modifier) {
    Text(text, modifier = modifier, style = style, maxLines = 1)
}

@Composable
private fun PillSub(text: String, modifier: Modifier = Modifier) {
    Text(text, modifier = modifier, style = PleosType.LabelS, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1, overflow = TextOverflow.Ellipsis)
}

@Composable
private fun RoadPill(ui: UiState, valueSize: TextStyle, modifier: Modifier) {
    val dot = roadDot(ui.road)
    val (tintA, tintB) = roadTint(ui.road)   // 구조 분해: Pair를 두 변수로 나눠 받는다
    val label = stringResource(R.string.label_road) + if (ui.night) " · " + stringResource(R.string.night) else ""
    InfoPill(tintA, tintB, modifier) {
        PillLabel(label)
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            Box(Modifier.size(18.dp).clip(CircleShape).background(dot))
            PillValue(stringResource(roadLabel(ui.road)), valueSize)
        }
        PillSub(if (ui.phase == Phase.DEFERRED) stringResource(R.string.silent_hold) else "")
    }
}

/** 지금 도로 카드의 색 기운: 시내 = 하늘·연보라, 고속도로 = 파랑·페리윙클, 정체 = 살구·버터, 모름 = 라일락 회색 (점은 상태색 그대로). */
internal fun roadTint(road: Road?): Pair<Color, Color> = when (road) {
    Road.CITY -> Color(0xFFA6DCF7) to Color(0xFFB3B6FF)
    Road.HIGHWAY -> Color(0xFF8EC5FF) to Color(0xFF9FB2FF)
    Road.JAM -> Color(0xFFFFB988) to Color(0xFFFFE07A)
    null -> MoodColors.autoMesh.base to MoodColors.autoMesh.mixA
}

/** 도로 상태 점 색. */
@Composable
internal fun roadDot(road: Road?): Color {
    val status = LocalStatusColors.current
    return when (road) {
        Road.CITY -> status.city
        Road.JAM -> status.jam
        Road.HIGHWAY -> status.highway
        null -> MaterialTheme.colorScheme.outline
    }
}

/** 지금 곡. 안내 음성이 나오는 동안에는 값을 40%로 흐리고 "안내 음성 우선" 칩을 띄운다. */
@Composable
private fun NowPill(ui: UiState, valueSize: TextStyle, modifier: Modifier) {
    val cs = MaterialTheme.colorScheme
    val np = ui.nowPlaying
    val mesh = MoodColors.mesh(np?.mood)   // 곡이 없으면 라일락 회색
    InfoPill(mesh.base, mesh.mixB, modifier) {
        if (ui.ducked) {
            Row(
                Modifier.height(36.dp).clip(RoundedCornerShape(percent = 50)).background(cs.primary).padding(horizontal = 14.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                Icon(SoftIcons.Speaker, contentDescription = null, modifier = Modifier.size(18.dp), tint = cs.onPrimary)
                Text(stringResource(R.string.ducked_short), style = PleosType.LabelS, color = cs.onPrimary, maxLines = 1)
            }
        } else {
            PillLabel(stringResource(if (np != null) R.string.now_playing else R.string.label_now))
        }
        val dim = Modifier.alpha(if (ui.ducked) 0.4f else 1f)
        PillValue(stringResource(if (np != null) feelLabel(np.mood) else R.string.silent), valueSize, dim)
        val sub = when {
            ui.ducked -> stringResource(R.string.ducked_level)
            np != null -> "${np.title} · ${formatDuration(np.durationSec)}"
            !ui.hasRoute -> stringResource(R.string.ready_first)
            ui.phase == Phase.DEFERRED -> stringResource(R.string.silent_hold)
            else -> stringResource(R.string.silent_next)
        }
        PillSub(sub, dim)
    }
}

@Composable
private fun EtaPill(ui: UiState, valueSize: TextStyle, modifier: Modifier) {
    InfoPill(MoodColors.arrivalMesh.base, MoodColors.arrivalMesh.mixA, modifier) {
        PillLabel(stringResource(R.string.label_eta))
        PillValue(if (ui.hasRoute) "${formatKm(ui.remainingKm)} ${stringResource(R.string.unit_km)}" else "—", valueSize)
        PillSub(
            when {
                !ui.hasRoute -> stringResource(R.string.no_route)
                else -> ui.etaMin?.let { etaText(it) } ?: ""
            }
        )
    }
}

/** 사운드트랙 끄기. 자동 재생 금지 규정의 반대편: 끄는 것도 사용자가 언제든 한 번에 할 수 있어야 한다. */
@Composable
private fun StopPill(onStop: () -> Unit, modifier: Modifier) {
    val cs = MaterialTheme.colorScheme
    val glass = LocalGlassColors.current
    val view = LocalView.current
    Surface(
        onClick = {
            view.performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP)
            onStop()
        },
        modifier = modifier,
        shape = RoundedCornerShape(48.dp),
        color = glass.glass,
        contentColor = cs.onSurface,
        border = BorderStroke(1.5.dp, glass.glassLine),
    ) {
        Column(
            Modifier.fillMaxSize().padding(horizontal = 28.dp, vertical = 24.dp),
            verticalArrangement = Arrangement.SpaceBetween,
        ) {
            StopOrb(60.dp, 22.dp)
            Text(stringResource(R.string.session_stop), style = PleosType.BodyS, fontWeight = PleosType.Strong, maxLines = 1)
        }
    }
}

/** 끄기 버튼 원: 설레요 메시(피치 → 코랄 → 마젠타핑크), 왼쪽 위가 밝다. 가운데 잉크색 정지 네모. */
@Composable
internal fun StopOrb(size: Dp, square: Dp) {
    val m = MoodColors.upbeatMesh
    Box(
        Modifier
            .size(size)
            .clip(CircleShape)
            .drawBehind {
                drawRect(
                    Brush.radialGradient(
                        0f to Color(0xFFFFE8D6), 0.3f to m.mixA, 0.62f to m.base, 1f to m.mixB,
                        center = Offset(this.size.width * 0.34f, this.size.height * 0.30f),
                        radius = this.size.minDimension * 0.8f,
                    )
                )
            }
            .clearAndSetSemantics { },
        contentAlignment = Alignment.Center,
    ) {
        Box(Modifier.size(square).clip(RoundedCornerShape(square * 0.25f)).background(MoodColors.blobInk))
    }
}
