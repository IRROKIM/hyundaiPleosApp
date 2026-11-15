package com.routesoundtrack.ui.theme

import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import com.routesoundtrack.domain.model.Mood

/*
 * 색 토큰은 이 파일 한 곳에만 둔다. 값은 디자인 시안 v3(Claude Design 캔버스 "Route Soundtrack 화면 시안")의 토큰 보드와 같다.
 * - 주간: 차가운 오프화이트 #F4F3F7 + 라벤더·하늘·피치 번짐, 잉크색 글자
 * - 야간: 어스름 보라 #45405E → #34344A(위→아래) + 페리윙클·플럼 번짐, 밝은 글자. 기존 #131417보다 밝게 올렸다
 * - 강조색 대신 잉크색 버튼을 쓰고, 색은 곡 성격(감정 메시)과 도로 상태에만 쓴다.
 * Pleos UI 가이드: 텍스트·아이콘 대비 4.5:1 이상 → 각 값 옆에 바탕 대비를 적었다.
 */
object LightTokens {
    val background = Color(0xFFF4F3F7)          // 차가운 오프화이트
    val surface = Color(0xFFFCFBFE)
    val surfaceVariant = Color(0xFFECEAF3)      // 칩·트랙 바탕
    val onSurface = Color(0xFF17181B)           // 바탕 대비 16.1:1
    val onSurfaceVariant = Color(0xFF55566A)    // 바탕 대비 6.5:1
    val primary = Color(0xFF17181B)             // 주 버튼 = 잉크색
    val onPrimary = Color(0xFFF7F5FB)
    val primaryContainer = Color(0xFFFFE6DE)    // 재생 중 카드(옅은 코랄)
    val onPrimaryContainer = Color(0xFF17181B)
    val outline = Color(0xFF8C8A9C)
}

object DarkTokens {
    val background = Color(0xFF3C3A54)          // 어스름 그라디언트(#45405E → #34344A)의 가운데 색. 그라디언트는 screenBackdrop이 그린다
    val surface = Color(0xFF4A475F)
    val surfaceVariant = Color(0xFF55526C)
    val onSurface = Color(0xFFF7F5FB)           // 바탕 대비 9.0:1 이상
    val onSurfaceVariant = Color(0xFFE2DEEE)    // 바탕 대비 7.4:1, 글래스 위 4.7:1
    val primary = Color(0xFFF7F5FB)
    val onPrimary = Color(0xFF34344A)
    val primaryContainer = Color(0xFF5A4458)    // 재생 중 카드(어두운 플럼)
    val onPrimaryContainer = Color(0xFFF7F5FB)
    val outline = Color(0xFFA9A4BC)
}

/**
 * 감정 하나의 파스텔 메시: 기준색(base) 원 위에 이웃색 2개(mixA, mixB)를 옮겨 겹친다.
 * dot = 주간 바탕 위의 작은 점·막대용 한 단계 진한 색, dotNight = 야간 바탕 위 점 색.
 */
data class MoodMesh(val base: Color, val mixA: Color, val mixB: Color, val dot: Color, val dotNight: Color)

/**
 * 곡 성격 = 감정 색. 보라·파랑·오렌지·노랑 구분은 그대로 두고, 원색 단색 대신 여러 색이 섞인 파스텔로 그린다.
 * 원 위 글자(BlobInk)는 파스텔 위라 대비 8.6:1 이상.
 */
object MoodColors {
    val quietMesh = MoodMesh(Color(0xFFBFA8FF), Color(0xFF9FB2FF), Color(0xFFF6B3DF), Color(0xFF8C72F0), Color(0xFFBFA8FF))   // 라벤더 + 페리윙클 · 핑크
    val grooveMesh = MoodMesh(Color(0xFF8EC5FF), Color(0xFF9CE8DA), Color(0xFFB3B6FF), Color(0xFF4A93EC), Color(0xFF8EC5FF))  // 하늘 + 아쿠아민트 · 연보라
    val upbeatMesh = MoodMesh(Color(0xFFFF9A84), Color(0xFFFFC59A), Color(0xFFF59AC8), Color(0xFFEE6A4E), Color(0xFFFF9A84))  // 코랄 + 피치 · 마젠타핑크
    val hypeMesh = MoodMesh(Color(0xFFFFE07A), Color(0xFFFFB988), Color(0xFFC9F09A), Color(0xFFE8B520), Color(0xFFFFE07A))    // 버터 + 살구 · 라임민트
    val arrivalMesh = MoodMesh(Color(0xFF8FE6C6), Color(0xFFA6DCF7), Color(0xFFF1F2A0), Color(0xFF25B386), Color(0xFF8FE6C6)) // 민트 + 하늘 · 레몬

    /** 아직 곡 성격이 정해지지 않은 "자동" 칸: 라일락 회색. */
    val autoMesh = MoodMesh(Color(0xFFC8C6D6), Color(0xFFD9CCFF), Color(0xFFCDE6FF), Color(0xFF8C8A9C), Color(0xFFA9A4BC))

    val blobInk = Color(0xFF17181B)

    fun mesh(mood: Mood?): MoodMesh = when (mood) {
        Mood.CHILL -> quietMesh
        Mood.GROOVE -> grooveMesh
        Mood.UPBEAT -> upbeatMesh
        Mood.HYPE -> hypeMesh
        Mood.ARRIVAL -> arrivalMesh
        null -> autoMesh
    }

    /** 감정의 기준색(파스텔). 넓은 면·번짐에 쓴다. */
    fun of(mood: Mood): Color = mesh(mood).base

    /** 작은 점·막대·눈금처럼 바탕 위에서 구분돼야 하는 곳에 쓰는 색. */
    fun dot(mood: Mood?, night: Boolean): Color = mesh(mood).let { if (night) it.dotNight else it.dot }
}

/** 주행 상태 색 (경로 스트립, 맥락 칩, 잠금 배너). 의미가 있는 색이라 v3에서도 그대로 둔다. */
data class StatusColors(
    val city: Color,
    val highway: Color,
    val jam: Color,
    val arrival: Color,
    val warnBg: Color,
    val warnFg: Color,
)

val LightStatus = StatusColors(
    city = Color(0xFF6B7584),
    highway = Color(0xFF1565C0),
    jam = Color(0xFFB45309),
    arrival = Color(0xFF1B7F4B),
    warnBg = Color(0xFFFFF1DC),
    warnFg = Color(0xFF7A4100),
)

val DarkStatus = StatusColors(
    city = Color(0xFF98A2B1),
    highway = Color(0xFF6CB2FF),
    jam = Color(0xFFF2A33A),
    arrival = Color(0xFF5CC98F),
    warnBg = Color(0xFF5A4524),
    warnFg = Color(0xFFFFD9A0),       // 경고 바탕 대비 6.9:1
)

val LocalStatusColors = staticCompositionLocalOf { LightStatus }

/**
 * 화면 바탕·글래스 카드·곡선 그래프 색.
 * 글래스는 backdrop blur 없이 반투명 면 + 1.5 dp 테두리로만 표현한다(minSdk 28에서 blur를 못 쓰기 때문).
 */
data class GlassColors(
    val bgTop: Color,          // 화면 바탕 위쪽 (주간은 위아래 같은 색)
    val bgBottom: Color,       // 화면 바탕 아래쪽
    val washes: List<Wash>,    // 바탕 위 옅은 번짐들
    val glass: Color,          // 카드 면
    val glassLine: Color,      // 카드 테두리
    val glow: Color,           // 주행 화면 뒤 코랄 빛
    val curve: Color,          // 진행 곡선
    val area: Color,           // 곡선 아래 지나온 구간
    val dotRing: Color,        // 현재 위치 점 테두리
    val pill: Color,           // 알약 버튼·하단 막대 면 (시안 surface)
    val line: Color,           // 알약 버튼·설정 카드·칩 테두리, 구분선 (시안 line)
    val card: Color,           // 경로·설정 화면 카드 면 (글래스보다 조금 더 불투명)
    val chip: Color,           // 선택 안 된 칩·작은 상태 칩 바탕
    val tick: Color,           // 메인(가로) 경로 눈금
    val night: Boolean,        // 원(블롭)·곡 카드 그리는 방식을 주간·야간으로 다르게 한다
)

/** 바탕 번짐 하나: 화면 비율 위치(x, y: 0..1), 화면 폭 대비 반지름, 색(알파 포함). */
data class Wash(val x: Float, val y: Float, val radius: Float, val color: Color)

val LightGlass = GlassColors(
    bgTop = LightTokens.background,
    bgBottom = LightTokens.background,
    washes = listOf(
        Wash(0.0f, 0.0f, 0.75f, Color(0xFFD9CCFF).copy(alpha = 0.60f)),   // 라벤더 · 왼쪽 위
        Wash(1.0f, 0.42f, 0.65f, Color(0xFFCDE6FF).copy(alpha = 0.60f)),  // 하늘 · 오른쪽
        Wash(0.42f, 1.04f, 0.75f, Color(0xFFFFDCCB).copy(alpha = 0.60f)), // 피치 · 아래
    ),
    glass = Color.White.copy(alpha = 0.70f),
    glassLine = Color.White.copy(alpha = 0.95f),
    glow = Color(0xFFFF9A84).copy(alpha = 0.32f),
    curve = Color(0xFFE2583C),
    area = Color(0xFFFF9A84).copy(alpha = 0.24f),
    dotRing = Color.White,
    pill = Color.White.copy(alpha = 0.72f),
    line = Color(0xFF17181B).copy(alpha = 0.07f),
    card = Color.White.copy(alpha = 0.78f),
    chip = Color(0xFFECEAF3),
    tick = Color(0xFF17181B).copy(alpha = 0.26f),
    night = false,
)

val DarkGlass = GlassColors(
    bgTop = Color(0xFF45405E),
    bgBottom = Color(0xFF34344A),
    washes = listOf(
        Wash(0.96f, 0.04f, 0.75f, Color(0xFF7C8AFF).copy(alpha = 0.22f)), // 페리윙클 · 오른쪽 위
        Wash(0.0f, 1.0f, 0.75f, Color(0xFFC86EBE).copy(alpha = 0.16f)),   // 플럼 · 왼쪽 아래
    ),
    glass = Color.White.copy(alpha = 0.08f),
    glassLine = Color.White.copy(alpha = 0.16f),
    glow = Color(0xFFFF9A84).copy(alpha = 0.22f),
    curve = Color(0xFFFFA48C),
    area = Color(0xFFFFA48C).copy(alpha = 0.18f),
    dotRing = Color(0xFF3C3A54),
    pill = Color.White.copy(alpha = 0.08f),
    line = Color.White.copy(alpha = 0.16f),
    card = Color.White.copy(alpha = 0.08f),
    chip = Color.White.copy(alpha = 0.10f),
    tick = Color(0xFFF7F5FB).copy(alpha = 0.34f),
    night = true,
)

val LocalGlassColors = staticCompositionLocalOf { LightGlass }
