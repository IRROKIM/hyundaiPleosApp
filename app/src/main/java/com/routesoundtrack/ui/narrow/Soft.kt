package com.routesoundtrack.ui.narrow

import android.view.HapticFeedbackConstants
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.routesoundtrack.domain.model.Mood
import com.routesoundtrack.domain.model.MoodMode
import com.routesoundtrack.ui.theme.GlassColors
import com.routesoundtrack.ui.theme.PleosType
import com.routesoundtrack.ui.theme.LocalGlassColors
import com.routesoundtrack.ui.theme.MoodColors
import com.routesoundtrack.ui.theme.MoodMesh

/*
 * 화면들이 같이 쓰는 "부드러운" 표현들 (디자인 시안 v3).
 * 모두 그라디언트만으로 그린다(blur 없음, minSdk 28).
 */

/** 화면 바탕: 주간은 오프화이트, 야간은 어스름 보라 세로 그라디언트 + 옅은 번짐 여러 개. 모든 화면 맨 뒤에 깐다. */
fun Modifier.screenBackdrop(glass: GlassColors): Modifier = drawBehind {
    drawRect(Brush.verticalGradient(listOf(glass.bgTop, glass.bgBottom)))
    for (w in glass.washes) {
        drawRect(
            Brush.radialGradient(
                listOf(w.color, Color.Transparent),
                center = Offset(size.width * w.x, size.height * w.y),
                radius = size.width * w.radius,
            )
        )
    }
}

/**
 * 주행 화면 뒤 번짐: 왼쪽 위 코랄, 왼쪽 아래 핑크(설레요 메시의 두 색), 오른쪽 아래 라벤더(야간은 페리윙클),
 * 주간은 오른쪽 위 하늘색까지. cx·cy = 코랄 빛의 중심(화면 비율). 세로 화면은 (0.25, 0.25), 가로 화면은 (0.28, 0.42).
 */
fun Modifier.softGlow(glass: GlassColors, cx: Float = 0.25f, cy: Float = 0.25f): Modifier = drawBehind {
    fun spot(color: Color, x: Float, y: Float, r: Float) = drawRect(
        Brush.radialGradient(listOf(color, Color.Transparent), center = Offset(size.width * x, size.height * y), radius = size.width * r)
    )
    spot(glass.glow, cx, cy, 0.75f)
    if (glass.night) {
        spot(Color(0xFF7C8AFF).copy(alpha = 0.22f), 0.92f, 0.72f, 0.5f)
    } else {
        spot(Color(0xFFD9CCFF).copy(alpha = 0.65f), 0.92f, 0.72f, 0.5f)
        spot(Color(0xFFCDE6FF).copy(alpha = 0.70f), 1.0f, 0.0f, 0.45f)
    }
    spot(MoodColors.upbeatMesh.mixB.copy(alpha = if (glass.night) 0.16f else 0.22f), 0.04f, 0.96f, 0.5f)
}

/** 글래스 카드: 반투명 면 + 아주 옅은 테두리. */
fun Modifier.glassCard(glass: GlassColors, shape: Shape): Modifier =
    clip(shape).background(glass.glass).border(1.5.dp, glass.glassLine, shape)

/**
 * 감정 원 하나를 파스텔 메시로 그린다: 기준색 원(가운데 진하고 바깥으로 투명) 위에
 * 이웃색 두 개를 왼쪽 위·오른쪽 아래로 옮긴 작은 원을 겹친다. r = 번진 원 전체 반지름.
 * 작은 원은 번진 원 안에서 투명해지도록 크기·위치를 맞춰서 원 가장자리에 딱딱한 경계가 생기지 않는다.
 * 야간은 가운데 단색 부분을 넓게 둔다(글자가 어두운 바탕 쪽으로 걸치면 대비가 떨어지므로).
 * alpha: 메인 "재생 중" 상태에서 고르지 않은 원을 흐리게(0.32) 그릴 때 쓴다.
 */
fun DrawScope.drawMoodMesh(m: MoodMesh, center: Offset, r: Float, night: Boolean, alpha: Float = 1f) {
    val c = m.base
    fun a(x: Float) = c.copy(alpha = x * alpha)
    val stops = if (night) {
        arrayOf(0f to a(1f), 0.42f to a(1f), 0.60f to a(0.6f), 0.80f to a(0.22f), 1f to a(0f))
    } else {
        arrayOf(0f to a(1f), 0.34f to a(0.95f), 0.52f to a(0.7f), 0.74f to a(0.3f), 1f to a(0f))
    }
    drawCircle(Brush.radialGradient(*stops, center = center, radius = r), radius = r, center = center)   // *: 배열을 가변 인자 자리에 펼친다
    meshSpot(m.mixA, center + Offset(-0.28f * r, -0.32f * r), 0.52f * r, alpha)
    meshSpot(m.mixB, center + Offset(0.28f * r, 0.36f * r), 0.52f * r, alpha)
}

private fun DrawScope.meshSpot(color: Color, at: Offset, r: Float, alpha: Float) {
    drawCircle(
        Brush.radialGradient(
            0f to color.copy(alpha = 0.95f * alpha), 0.5f to color.copy(alpha = 0.55f * alpha), 1f to color.copy(alpha = 0f),
            center = at,
            radius = r,
        ),
        radius = r,
        center = at,
    )
}

/**
 * 곡 카드 그림(시안 lightTheme_01 · nightTheme_02).
 * 주간: 흰 바탕에 감정의 파스텔이 오른쪽 아래·왼쪽 위·오른쪽 위에서 번진다 → 잉크색 글자.
 * 야간: 플럼·인디고 바탕에 감정색이 오른쪽 아래에서 빛난다 → 흰 글자.
 * mood == null 이면 "자동"(아직 정해지지 않은 곡)이라 라일락 회색으로 그린다.
 */
fun Modifier.moodArt(mood: Mood?, night: Boolean): Modifier {
    val m = MoodColors.mesh(mood)
    val deep = when (mood) {   // 야간 카드의 짙은 바탕 (왼쪽 위 → 오른쪽 아래)
        Mood.CHILL -> Color(0xFF2B2250) to Color(0xFF4A3A7E)
        Mood.GROOVE -> Color(0xFF1E2A54) to Color(0xFF2E4C84)
        Mood.UPBEAT -> Color(0xFF3A1F40) to Color(0xFF6E3050)
        Mood.HYPE -> Color(0xFF3A2E1C) to Color(0xFF64502A)
        Mood.ARRIVAL -> Color(0xFF163838) to Color(0xFF235C5C)
        null -> Color(0xFF2E2C40) to Color(0xFF45405E)
    }
    return drawBehind {
        val w = size.width
        val h = size.height
        if (night) {
            drawRect(Brush.linearGradient(listOf(deep.first, deep.second), start = Offset.Zero, end = Offset(w, h)))
            drawRect(Brush.radialGradient(listOf(m.base.copy(alpha = 0.85f), m.base.copy(alpha = 0.38f), Color.Transparent), center = Offset(w * 0.9f, h), radius = w * 0.6f))
            drawRect(Brush.radialGradient(listOf(m.mixB.copy(alpha = 0.36f), Color.Transparent), center = Offset(w * 0.06f, 0f), radius = w * 0.45f))
        } else {
            drawRect(Brush.linearGradient(listOf(Color.White, Color(0xFFF6F4FA)), start = Offset.Zero, end = Offset(w, h)))
            drawRect(Brush.radialGradient(listOf(m.base, m.base.copy(alpha = 0.6f), Color.Transparent), center = Offset(w * 0.92f, h), radius = w * 0.6f))
            drawRect(Brush.radialGradient(listOf(m.mixA.copy(alpha = 0.9f), Color.Transparent), center = Offset(w * 0.04f, 0f), radius = w * 0.45f))
            drawRect(Brush.radialGradient(listOf(m.mixB.copy(alpha = 0.7f), Color.Transparent), center = Offset(w * 0.6f, 0f), radius = w * 0.4f))
        }
    }
}

/** 번진 메시 원 하나 + 가운데 글자 (경로 대기 화면의 "준비된 첫 곡"). */
@Composable
fun SoftBlob(mesh: MoodMesh, size: Dp, title: String, caption: String, modifier: Modifier = Modifier) {
    val night = LocalGlassColors.current.night
    Column(
        modifier
            .size(size)
            .drawBehind { drawMoodMesh(mesh, center, this.size.minDimension / 2, night) },
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(6.dp, Alignment.CenterVertically),
    ) {
        Text(title, style = PleosType.TitleL, color = MoodColors.blobInk, maxLines = 1)
        Text(caption, style = PleosType.LabelS, color = MoodColors.blobInk, maxLines = 1)
    }
}

/** 사용자가 고른 곡 성격 → 실제 곡. 자동(AUTO)이면 null: 그 지점의 도로·시간을 보고 Policy가 정한다. */
internal fun fixedMood(mode: MoodMode): Mood? = when (mode) {
    MoodMode.AUTO -> null
    MoodMode.CHILL -> Mood.CHILL
    MoodMode.GROOVE -> Mood.GROOVE
    MoodMode.UPBEAT -> Mood.UPBEAT
    MoodMode.HYPE -> Mood.HYPE
}

/**
 * 곡 성격 색 점: 왼쪽 위가 밝은 작은 구슬(이웃색 → 점 색). mood == null(자동)이면 감정 4색을 네 조각으로 나눠 그린다.
 */
@Composable
fun MoodDot(mood: Mood?, size: Dp, modifier: Modifier = Modifier) {
    val night = LocalGlassColors.current.night
    androidx.compose.foundation.Canvas(modifier.size(size)) {
        if (mood != null) {
            val m = MoodColors.mesh(mood)
            val dot = MoodColors.dot(mood, night)
            drawCircle(
                Brush.radialGradient(
                    0f to m.mixA, 0.58f to dot, 1f to m.mixB,   // 시안: 이웃색 → 점 색 → 다른 이웃색
                    center = Offset(this.size.width * 0.3f, this.size.height * 0.28f),
                    radius = this.size.minDimension,
                )
            )
        } else {
            val colors = listOf(Mood.CHILL, Mood.GROOVE, Mood.UPBEAT, Mood.HYPE).map { MoodColors.of(it) }
            colors.forEachIndexed { i, c -> drawArc(c, startAngle = -90f + i * 90f, sweepAngle = 90f, useCenter = true) }
        }
    }
}

/**
 * 둥근 알약 버튼(높이 76 dp, 선 아이콘 + 글자 라벨). 메인의 "곡 목록"·"경로 · 설정", 다른 화면의 "메인"(뒤로).
 * 시안: 반투명 흰 면 + 아주 옅은 잉크색 테두리, 글자는 보통 굵기.
 */
@Composable
internal fun PillButton(label: String, onClick: () -> Unit, icon: ImageVector? = null, modifier: Modifier = Modifier) {
    val glass = LocalGlassColors.current
    Surface(
        onClick = onClick,
        modifier = modifier,
        shape = RoundedCornerShape(percent = 50),
        color = glass.pill,
        contentColor = MaterialTheme.colorScheme.onSurface,
        border = BorderStroke(1.5.dp, glass.line),
    ) {
        Row(
            Modifier.height(76.dp).padding(start = if (icon != null) 24.dp else 30.dp, end = 30.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            if (icon != null) Icon(icon, contentDescription = null, modifier = Modifier.size(24.dp))
            Text(label, style = PleosType.LabelM, maxLines = 1)
        }
    }
}

/** 점선 테두리 원 + 선 아이콘. 메인 하단 막대의 "들어가기" 표시. 장식이라 읽지 않는다. */
@Composable
internal fun DottedIconCircle(icon: ImageVector, size: Dp, iconSize: Dp) {
    val ring = MaterialTheme.colorScheme.onSurfaceVariant
    Box(
        Modifier
            .size(size)
            .drawBehind {
                val w = 2.dp.toPx()
                drawCircle(
                    ring,
                    radius = this.size.minDimension / 2 - w / 2,
                    style = Stroke(w, pathEffect = PathEffect.dashPathEffect(floatArrayOf(2.dp.toPx(), 3.dp.toPx()))),
                )
            }
            .clearAndSetSemantics { },
        contentAlignment = Alignment.Center,
    ) {
        Icon(icon, contentDescription = null, modifier = Modifier.size(iconSize), tint = MaterialTheme.colorScheme.onSurface)
    }
}

/** 잉크색으로 채운 알약 버튼(끄기 등 주 동작). 높이는 부르는 쪽이 정한다. */
@Composable
internal fun InkButton(label: String, icon: ImageVector?, onClick: () -> Unit, modifier: Modifier = Modifier, ) {
    val cs = MaterialTheme.colorScheme
    val view = LocalView.current
    Surface(
        onClick = {
            view.performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP)
            onClick()
        },
        modifier = modifier,
        shape = RoundedCornerShape(percent = 50),
        color = cs.primary,
        contentColor = cs.onPrimary,
    ) {
        Row(
            Modifier.padding(horizontal = 28.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp, Alignment.CenterHorizontally),
        ) {
            if (icon != null) Icon(icon, contentDescription = null, modifier = Modifier.size(18.dp))
            Text(label, style = PleosType.BodyS, fontWeight = PleosType.Strong, maxLines = 1)
        }
    }
}

/** 작은 상태 칩(점 + 글자): "주행 중"·"정차 중" 등. 누를 수 없다. */
@Composable
internal fun StateChip(label: String, dot: Color, height: Dp = 48.dp, modifier: Modifier = Modifier) {
    val glass = LocalGlassColors.current
    Row(
        modifier.height(height).clip(RoundedCornerShape(percent = 50)).background(glass.chip).padding(horizontal = 20.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Box(Modifier.size(12.dp).clip(CircleShape).background(dot))
        Text(label, style = PleosType.LabelS, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1)
    }
}
