package com.routesoundtrack.ui.theme

import androidx.compose.material3.Typography
import androidx.compose.ui.text.ExperimentalTextApi
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontVariation
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.sp
import com.routesoundtrack.R

/*
 * 글꼴: Asta Sans (Pleos 가이드 Foundations › Typography가 시스템 UI 글꼴로 지정한 서체, 42dot, SIL Open Font License).
 * 라이선스 원문은 assets/licenses/OFL-AstaSans.txt. 한글·영문을 모두 지원한다.
 * 파일은 굵기 300–800을 담은 가변 글꼴(variable font) 하나다. 굵기마다 wght 축 값을 정해 같은 파일을 여러 번 등록한다.
 * 기기 글꼴 다운로드(Google Fonts provider)는 차량 OS에 없을 수 있어 APK에 넣었다(res/font).
 */
@OptIn(ExperimentalTextApi::class)   // 리소스 글꼴에 가변 축 값을 주는 API가 아직 실험 단계다(Compose 1.7)
private fun asta(weight: FontWeight) =
    Font(R.font.asta_sans, weight, variationSettings = FontVariation.Settings(FontVariation.weight(weight.weight)))

val AstaSans = FontFamily(
    asta(FontWeight.Normal),      // 400
    asta(FontWeight.Medium),      // 500
    asta(FontWeight.SemiBold),    // 600
    asta(FontWeight.Bold),        // 700
    asta(FontWeight.ExtraBold),   // 800
)

/**
 * Pleos 글자 단계 (가이드 Font Hierarchy, 크기/줄 높이 dp). 정보의 위계에 따라 이 단계만 쓴다.
 * - Headline L·M·S 60/80 · 56/72 · 48/64 (Extra Bold, Bold) → 화면 제목, 주행 화면의 큰 값
 * - Title    L·M·S 40/52 · 36/44 · 32/40 (Extra Bold, Bold, Regular) → 카드 제목, 감정 문구
 * - Body     L·M·S 30/38 · 28/36 · 26/34 (Strong, Normal) → 버튼·목록 항목처럼 읽어야 하는 글자
 * - Label      M·S 24/28 · 20/24 (Normal) → 보조 설명, 작은 칩
 * Body의 "Strong"은 SemiBold(600)로 정했다: 가이드가 Bold와 다른 이름을 쓰기 때문이다.
 * Pleos UI 가이드의 글자 높이 규칙(중요 정보 5 mm ≈ 32 dp, 보조 3 mm ≈ 19 dp)도 이 단계 안에서 지켜진다.
 */
object PleosType {
    val HeadlineL = scale(60, 80, FontWeight.ExtraBold)
    val HeadlineM = scale(56, 72, FontWeight.ExtraBold)
    val HeadlineS = scale(48, 64, FontWeight.ExtraBold)
    val TitleL = scale(40, 52, FontWeight.Bold)
    val TitleM = scale(36, 44, FontWeight.Bold)
    val TitleS = scale(32, 40, FontWeight.Bold)
    val BodyL = scale(30, 38, FontWeight.Normal)
    val BodyM = scale(28, 36, FontWeight.Normal)
    val BodyS = scale(26, 34, FontWeight.Normal)
    val LabelM = scale(24, 28, FontWeight.Normal)
    val LabelS = scale(20, 24, FontWeight.Normal)

    /** Body의 강조 굵기 "Strong". Text(style = PleosType.BodyS, fontWeight = PleosType.Strong) 처럼 쓴다. */
    val Strong = FontWeight.SemiBold

    /**
     * 가이드 단계보다 큰 숫자·장식 글자(주행 화면의 "다음 곡까지 28.8", 곡 카드 뒤 큰 장르 글자).
     * 한 줄짜리라 줄 높이 = 글자 크기.
     */
    fun display(size: TextUnit) = TextStyle(fontFamily = AstaSans, fontWeight = FontWeight.ExtraBold, fontSize = size, lineHeight = size)

    private fun scale(size: Int, line: Int, weight: FontWeight) =
        TextStyle(fontFamily = AstaSans, fontWeight = weight, fontSize = size.sp, lineHeight = line.sp)
}

/**
 * Material 3 글자 슬롯도 같은 단계에 맞춘다(이름이 같다: headline · title · body · label).
 * MaterialTheme은 bodyLarge를 기본 글자 스타일로 깔기 때문에, style을 지정하지 않은 Text도 Asta Sans가 된다.
 */
val RsTypography = Typography(
    displayLarge = PleosType.HeadlineL,
    displayMedium = PleosType.HeadlineM,
    displaySmall = PleosType.HeadlineS,
    headlineLarge = PleosType.HeadlineL,
    headlineMedium = PleosType.HeadlineM,
    headlineSmall = PleosType.HeadlineS,
    titleLarge = PleosType.TitleL,
    titleMedium = PleosType.TitleM,
    titleSmall = PleosType.TitleS,
    bodyLarge = PleosType.BodyL,
    bodyMedium = PleosType.BodyM,
    bodySmall = PleosType.BodyS,
    labelLarge = PleosType.LabelM,
    labelMedium = PleosType.LabelM,
    labelSmall = PleosType.LabelS,
)
