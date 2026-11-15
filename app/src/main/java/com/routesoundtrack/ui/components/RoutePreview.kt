package com.routesoundtrack.ui.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.em
import androidx.compose.ui.unit.sp
import com.routesoundtrack.R
import com.routesoundtrack.domain.model.CueKind
import com.routesoundtrack.domain.model.Mood
import com.routesoundtrack.session.UiState
import com.routesoundtrack.ui.densityLabel
import com.routesoundtrack.ui.etaText
import com.routesoundtrack.ui.formatKm
import com.routesoundtrack.ui.formatKmShort
import com.routesoundtrack.ui.narrow.fixedMood
import com.routesoundtrack.ui.presetLabel
import com.routesoundtrack.ui.theme.PleosType
import com.routesoundtrack.ui.theme.LocalGlassColors
import com.routesoundtrack.ui.theme.MoodColors
import com.routesoundtrack.ui.theme.SoftIcons
import kotlin.math.hypot

/*
 * "경로 미리보기" 화면 조각들 (시안 "경로 · 설정"의 왼쪽): 노을 산 그림 위 목적지, 재생 지점 요약 카드.
 * 가로 화면(WideRoute)과 세로 1단(NarrowSettings)이 같이 쓴다.
 */

/**
 * 목적지 그림: 하늘 그라디언트 + 해 + 산 세 겹 + 아래쪽 어둡게 → 흰 글자로 목적지. 지도가 아니라 분위기 그림이다.
 * onOpenDrive: 사운드트랙이 켜져 있을 때만 준다. 오른쪽 아래에 "주행 화면으로" 버튼이 나온다.
 */
@Composable
fun RouteHero(ui: UiState, onOpenDrive: (() -> Unit)?, modifier: Modifier = Modifier, inset: Dp = 44.dp) {
    val night = LocalGlassColors.current.night
    Box(modifier.clip(RoundedCornerShape(48.dp)).drawBehind { drawLandscape(night) }) {
        Row(
            Modifier.align(Alignment.BottomStart).fillMaxWidth().padding(start = inset, end = inset - 4.dp, bottom = inset - 4.dp),
            verticalAlignment = Alignment.Bottom,
            horizontalArrangement = Arrangement.spacedBy(24.dp),
        ) {
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Text(
                    if (ui.hasRoute) ui.destination.ifBlank { stringResource(R.string.destination_unknown) } else stringResource(R.string.no_route),
                    style = PleosType.TitleL, color = Color.White,
                    maxLines = 1, overflow = TextOverflow.Ellipsis,
                )
                Text(
                    if (ui.hasRoute) stringResource(R.string.hero_guiding, formatKm(ui.remainingKm)) else stringResource(R.string.no_route_detail),
                    style = PleosType.LabelM, color = Color.White.copy(alpha = 0.88f), maxLines = 2,
                )
            }
            if (onOpenDrive != null) {
                Surface(
                    onClick = onOpenDrive,
                    shape = RoundedCornerShape(28.dp),
                    color = Color(0xFF1C1C22).copy(alpha = 0.62f),
                    contentColor = Color.White,
                    border = BorderStroke(1.5.dp, Color.White.copy(alpha = 0.28f)),
                ) {
                    Row(
                        Modifier.height(88.dp).padding(horizontal = 40.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                    ) {
                        Text(stringResource(R.string.open_drive_long), style = PleosType.BodyS, fontWeight = PleosType.Strong, maxLines = 1)
                        Icon(SoftIcons.ArrowRight, contentDescription = null, modifier = Modifier.size(26.dp))
                    }
                }
            }
        }
    }
}

/** 산 능선: (x%, y%) 점들. 각 층 높이 안에서의 비율이다(시안 clip-path 값 그대로). */
private val FarRidge = listOf(0 to 62, 12 to 40, 22 to 52, 34 to 22, 44 to 38, 52 to 14, 63 to 36, 72 to 26, 84 to 48, 93 to 34, 100 to 46)
private val MidRidge = listOf(0 to 48, 9 to 34, 20 to 56, 31 to 30, 42 to 50, 55 to 24, 68 to 52, 80 to 38, 91 to 58, 100 to 44)
private val NearRidge = listOf(0 to 40, 14 to 56, 27 to 36, 40 to 58, 52 to 44, 66 to 62, 78 to 46, 90 to 60, 100 to 50)

private fun DrawScope.drawLandscape(night: Boolean) {
    val w = size.width
    val h = size.height
    val sky = if (night) {
        arrayOf(0f to Color(0xFF2E3266), 0.38f to Color(0xFF4A447E), 0.70f to Color(0xFF7A5A92), 1f to Color(0xFF4A3E68))
    } else {
        arrayOf(0f to Color(0xFFDCD3FF), 0.34f to Color(0xFFF6CDE6), 0.62f to Color(0xFFFFC9AE), 1f to Color(0xFFFFB497))
    }
    drawRect(Brush.verticalGradient(*sky))
    // 해(야간은 달): 그림 높이 기준 크기, 오른쪽 위
    val sun = Offset(w * 0.744f, h * 0.448f)
    val r = h * 0.19f
    val sunStops = if (night) {
        arrayOf(0f to Color(0xF2F0ECFF), 0.34f to Color(0xE6EBE4FF), 0.60f to Color(0x61BFA8FF), 1f to Color(0x00BFA8FF))
    } else {
        arrayOf(0f to Color(0xFFFFFAEC), 0.34f to Color(0xF2FFF0D6), 0.62f to Color(0x66FFD6B4), 1f to Color(0x00FFD6B4))
    }
    drawCircle(Brush.radialGradient(*sunStops, center = sun, radius = r), r, sun)
    val (far, mid, near) = if (night) Triple(Color(0xFF5A4E86), Color(0xFF43396C), Color(0xFF2C2648))
    else Triple(Color(0xFFB79BD8), Color(0xFF8E78C0), Color(0xFF5E5194))
    drawRidge(FarRidge, h * 0.655f, far)
    drawRidge(MidRidge, h * 0.517f, mid)
    drawRidge(NearRidge, h * 0.379f, near)
    // 글자가 읽히도록 아래쪽을 어둡게
    val shadeTop = h * (1f - 0.448f)
    drawRect(
        Brush.verticalGradient(listOf(Color(0x000A0A10), Color(0x9E0A0A10)), startY = shadeTop, endY = h),
        topLeft = Offset(0f, shadeTop),
    )
}

/** 산 한 층: 그림 바닥에서 layerH 높이의 띠 안에 능선을 그리고 아래를 채운다. */
private fun DrawScope.drawRidge(points: List<Pair<Int, Int>>, layerH: Float, color: Color) {
    val top = size.height - layerH
    val path = Path().apply {
        points.forEachIndexed { i, (x, y) ->
            val px = size.width * x / 100f
            val py = top + layerH * y / 100f
            if (i == 0) moveTo(px, py) else lineTo(px, py)
        }
        lineTo(size.width, size.height)
        lineTo(0f, size.height)
        close()
    }
    drawPath(path, color)
}

/**
 * 재생 지점 요약 카드: "재생 지점 5곳 · 여행 · 가끔", 지점 목록 한 줄, 총 거리 · 지점 수 · 도착까지, 오른쪽에 경로 썸네일.
 * showThumb = false: 좁은 창에서 썸네일을 뺀다.
 */
@Composable
fun RouteSummaryCard(ui: UiState, modifier: Modifier = Modifier, showThumb: Boolean = true, thumb: Dp = 240.dp) {
    val cs = MaterialTheme.colorScheme
    val glass = LocalGlassColors.current
    val shape = RoundedCornerShape(48.dp)
    val cues = ui.plan?.cues.orEmpty()
    Row(
        modifier.clip(shape).background(glass.card).border(1.5.dp, glass.line, shape).padding(start = 44.dp, top = 36.dp, end = 36.dp, bottom = 36.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(32.dp),
    ) {
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(22.dp)) {
            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Text(
                    if (cues.isEmpty()) stringResource(R.string.no_route_cues)
                    else stringResource(R.string.route_cues_title, cues.size, stringResource(presetLabel(ui.settings.preset)), stringResource(densityLabel(ui.settings.density))),
                    style = PleosType.BodyM, fontWeight = PleosType.Strong,
                )
                if (cues.isNotEmpty()) Text(cueLine(ui), style = PleosType.LabelS, color = cs.onSurfaceVariant)
            }
            Box(Modifier.fillMaxWidth().height(1.5.dp).background(glass.line))
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(24.dp)) {
                Stat(if (ui.hasRoute) "${formatKm(ui.totalKm)} km" else "—", stringResource(R.string.stat_total), Modifier.weight(1f))
                Stat(stringResource(R.string.cue_count_short, cues.size), stringResource(R.string.stat_cues), Modifier.weight(1f))
                Stat(ui.etaMin?.takeIf { ui.hasRoute }?.let { etaText(it) } ?: "—", stringResource(R.string.label_eta), Modifier.weight(1f))
            }
        }
        if (showThumb) {
            Box(Modifier.size(thumb).clip(RoundedCornerShape(36.dp)).background(glass.chip), contentAlignment = Alignment.Center) {
                RouteThumb(ui, Modifier.size(thumb * (200f / 240f)))
            }
        }
    }
}

@Composable
private fun Stat(value: String, label: String, modifier: Modifier) {
    Column(modifier, verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Text(value, style = PleosType.TitleM, maxLines = 1)
        Text(label, style = PleosType.LabelS, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1)
    }
}

/** "시작 곡 5 km · 중간 곡 35 · 65 · 95 km · 도착 곡 도착 8분 전" */
@Composable
private fun cueLine(ui: UiState): String {
    val cues = ui.plan?.cues.orEmpty()
    val parts = mutableListOf<String>()
    cues.firstOrNull { it.kind == CueKind.START }?.let { parts += stringResource(R.string.detail_start, formatKmShort(it.atKm)) }
    val mids = cues.filter { it.kind == CueKind.MID }
    if (mids.isNotEmpty()) parts += stringResource(R.string.detail_mid, mids.joinToString(" · ") { formatKmShort(it.atKm) })
    if (cues.any { it.kind == CueKind.ARRIVAL }) parts += stringResource(R.string.detail_arrival)
    return parts.joinToString(" · ")
}

/** 시안의 경로 썸네일 선(200×200 기준 꼭짓점). 실제 지도가 아니라 재생 지점의 순서를 보여 주는 그림이다. */
private val ThumbLine = listOf(28f to 160f, 60f to 120f, 52f to 78f, 92f to 52f, 132f to 74f, 124f to 118f, 162f to 140f, 176f to 96f)

/** 경로 썸네일: 출발점(잉크색) → 재생 지점(곡 성격 색) → 도착(민트). 점은 거리 비율대로 선 위에 놓는다. */
@Composable
private fun RouteThumb(ui: UiState, modifier: Modifier) {
    val ink = MaterialTheme.colorScheme.onSurface
    val night = LocalGlassColors.current.night
    val fixed = fixedMood(ui.settings.moodMode)
    val total = ui.totalKm
    val cues = ui.plan?.cues.orEmpty()
    Canvas(modifier.clearAndSetSemantics { }) {
        val k = size.width / 200f
        val pts = ThumbLine.map { (x, y) -> Offset(x * k, y * k) }
        val path = Path().apply {
            moveTo(pts[0].x, pts[0].y)
            for (p in pts.drop(1)) lineTo(p.x, p.y)
        }
        drawPath(path, ink, style = Stroke(5f * k, cap = StrokeCap.Round, join = StrokeJoin.Round))
        drawCircle(ink, 8f * k, pts[0])
        if (total <= 0.0) return@Canvas
        // 선 길이 비율로 위치 찾기
        val seg = pts.zipWithNext { a, b -> hypot(b.x - a.x, b.y - a.y) }
        val len = seg.sum()
        fun at(f: Float): Offset {
            var d = (f.coerceIn(0f, 1f)) * len
            for (i in seg.indices) {
                if (d <= seg[i] || i == seg.lastIndex) {
                    val t = if (seg[i] == 0f) 0f else (d / seg[i]).coerceIn(0f, 1f)
                    return pts[i] + (pts[i + 1] - pts[i]) * t
                }
                d -= seg[i]
            }
            return pts.last()
        }
        for (c in cues) {
            val arrival = c.kind == CueKind.ARRIVAL
            val mood: Mood? = if (arrival) Mood.ARRIVAL else fixed
            val p = if (arrival) pts.last() else at((c.atKm / total).toFloat())
            drawCircle(MoodColors.dot(mood, night), (if (arrival) 12f else 11f) * k, p)
        }
    }
}
