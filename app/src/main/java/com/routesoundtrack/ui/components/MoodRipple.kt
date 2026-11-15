package com.routesoundtrack.ui.components

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInRoot
import androidx.compose.ui.unit.dp
import com.routesoundtrack.domain.model.Mood
import com.routesoundtrack.ui.theme.MoodColors
import com.routesoundtrack.ui.theme.MoodMesh
import kotlin.math.hypot
import kotlin.math.pow

/** 파동 한 번: 어떤 곡 색(mood)이 화면 어디(center, 루트 좌표 px)에서 퍼지는가. key는 같은 원을 또 눌러도 다시 퍼지게 하는 번호. */
class MoodWave(val mood: Mood, val center: Offset, val key: Long)

/**
 * 감정 원을 눌렀을 때 그 곡의 색이 파동처럼 번지는 효과의 상태.
 * MainScreen이 하나 만들어 LocalMoodRipple로 내려 주고, 원(BlobField)은 fire()만 부른다.
 */
class MoodRipple {
    var wave by mutableStateOf<MoodWave?>(null)
        private set

    /** 파동이 아직 퍼지는 중인가. 이 동안 MainScreen은 메인 화면을 잠깐 더 보여 주고 나서 주행 화면으로 넘어간다. */
    var spreading by mutableStateOf(false)
        internal set
    private var count = 0L

    fun fire(mood: Mood, center: Offset) {
        wave = MoodWave(mood, center, ++count)
        spreading = true
    }
}

/** MainScreen 밖(미리보기 등)에서는 기본값을 쓴다. fire()를 불러도 그리는 층이 없어 아무것도 보이지 않는다. */
val LocalMoodRipple = staticCompositionLocalOf { MoodRipple() }

private const val WAVE_MS = 1_500
private const val HOLD_AT = 0.6f      // 이만큼 퍼지면 메인 화면을 놓아 준다 (1.5초 × 0.6 = 0.9초)

/**
 * 파동을 그리는 층. MainScreen의 바탕 바로 위, 화면 내용 아래에 둔다.
 * 그래서 글자·버튼을 가리지 않고, 원을 누른 뒤 주행 화면으로 넘어가도 파동이 끝까지 퍼진다.
 * enabled=false(주행 중 잠금)면 그리지 않는다. 주행 중에는 애니메이션을 쓰지 않는다(Pleos UI 가이드).
 */
@Composable
fun MoodRippleLayer(ripple: MoodRipple, night: Boolean, enabled: Boolean, modifier: Modifier = Modifier) {
    val wave = ripple.wave
    val progress = remember { Animatable(1f) }                 // 0 = 막 누름, 1 = 다 퍼져서 안 보임
    LaunchedEffect(wave?.key) {
        if (wave == null) return@LaunchedEffect
        progress.snapTo(0f)
        progress.animateTo(HOLD_AT, tween((WAVE_MS * HOLD_AT).toInt(), easing = LinearEasing))
        ripple.spreading = false                                 // 여기서 주행 화면으로 넘어갈 수 있다. 남은 파동은 그 뒤에서 옅어진다
        progress.animateTo(1f, tween((WAVE_MS * (1 - HOLD_AT)).toInt(), easing = LinearEasing))
    }
    var origin by remember { mutableStateOf(Offset.Zero) }      // 이 층의 왼쪽 위 (루트 좌표)
    Canvas(modifier.onGloballyPositioned { origin = it.positionInRoot() }) {
        val t = progress.value                                   // 그리기 단계에서만 읽어서, 매 프레임 다시 그리기만 한다
        if (wave == null || !enabled || t >= 1f) return@Canvas
        drawMoodWave(MoodColors.mesh(wave.mood), wave.center - origin, t, night)
    }
}

/**
 * 파동 그리기. t = 0..1 진행도.
 * 1) 번짐: 누른 자리에서 기준색 → 이웃색 A → 이웃색 B로 바뀌는 둥근 그라데이션이 화면 끝까지 커지며 옅어진다.
 * 2) 물결: 그 위로 색이 다른 고리 3개가 조금씩 늦게 출발해 퍼진다.
 */
private fun DrawScope.drawMoodWave(m: MoodMesh, center: Offset, t: Float, night: Boolean) {
    // 누른 자리에서 가장 먼 화면 모서리까지의 거리 = 파동이 닿을 끝
    val far = maxOf(
        hypot(center.x, center.y),
        hypot(size.width - center.x, center.y),
        hypot(center.x, size.height - center.y),
        hypot(size.width - center.x, size.height - center.y),
    )
    val peak = if (night) 0.5f else 0.62f                       // 야간은 흰 글자가 읽히게 조금 옅게

    val r = far * easeOut(t)
    val fade = (1f - t).pow(1.4f)                                // 퍼질수록 옅어진다
    if (r > 1f) {
        drawCircle(
            Brush.radialGradient(
                0f to m.base.copy(alpha = peak * fade),
                0.55f to m.mixA.copy(alpha = peak * 0.7f * fade),
                0.85f to m.mixB.copy(alpha = peak * 0.35f * fade),
                1f to m.mixB.copy(alpha = 0f),
                center = center,
                radius = r,
            ),
            radius = r,
            center = center,
        )
    }

    val rings = arrayOf(m.base, m.mixA, m.mixB)
    for (i in rings.indices) {
        val lag = i * 0.12f                                      // 두 번째·세 번째 고리는 조금 늦게 출발
        val ti = ((t - lag) / (1f - lag)).coerceIn(0f, 1f)
        if (ti <= 0f || ti >= 1f) continue
        val rr = far * easeOut(ti)
        val band = 36.dp.toPx() + rr * 0.08f                     // 고리 두께: 멀리 갈수록 넓고 흐려진다
        drawRing(rings[i], center, rr, band, alpha = peak * 1.1f * (1f - ti).pow(1.2f))
    }
}

/** 처음엔 빠르게, 끝에선 천천히 (물결이 퍼지는 속도). */
private fun easeOut(t: Float): Float = 1f - (1f - t).pow(3)

/** 반지름 r에서 가장 진하고 안팎으로 사라지는 부드러운 고리. */
private fun DrawScope.drawRing(color: Color, center: Offset, r: Float, band: Float, alpha: Float) {
    val outer = r + band
    val inner = ((r - band) / outer).coerceAtLeast(0f)
    drawCircle(
        Brush.radialGradient(
            0f to color.copy(alpha = 0f),
            inner to color.copy(alpha = 0f),
            r / outer to color.copy(alpha = alpha),
            1f to color.copy(alpha = 0f),
            center = center,
            radius = outer,
        ),
        radius = outer,
        center = center,
    )
}
