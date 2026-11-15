package com.routesoundtrack.ui.theme

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.addPathNodes
import androidx.compose.ui.unit.dp

/*
 * 디자인 시안의 선 아이콘(24×24, 선 두께 2, 둥근 끝)을 그대로 옮긴 것.
 * SVG path 문자열을 addPathNodes로 읽어 ImageVector를 만든다. 원(circle)과 둥근 사각형(rect rx)은 path로 바꿔 적었다.
 * 색은 Icon(tint)이 입히므로 여기서는 검정으로 둔다.
 */
object SoftIcons {
    /** 곡 목록: 줄 3개 + 음표 */
    val List = stroke("M4 6h12 M4 12h12 M4 18h8 M15.5 18a2.5 2.5 0 1 0 5 0a2.5 2.5 0 1 0 -5 0 M20.5 18V9")

    /** 경로: 출발·도착 점을 잇는 S자 길 */
    val Route = stroke("M2.8 18a2.2 2.2 0 1 0 4.4 0a2.2 2.2 0 1 0 -4.4 0 M16.8 6a2.2 2.2 0 1 0 4.4 0a2.2 2.2 0 1 0 -4.4 0 M7.2 18H15a3.5 3.5 0 0 0 0-7H9a3.5 3.5 0 0 1 0-7h7.8")

    val ArrowRight = stroke("M5 12h14 M13 6l6 6-6 6")
    val Back = stroke("M15 5l-7 7 7 7")
    val Speaker = stroke("M11 5L6 9H3v6h3l5 4z M16 9a4 4 0 0 1 0 6")
    val Lock = stroke("M7 11h10a2 2 0 0 1 2 2v6a2 2 0 0 1 -2 2H7a2 2 0 0 1 -2 -2v-6a2 2 0 0 1 2 -2z M8 11V8a4 4 0 0 1 8 0v3")
    val Music = stroke("M9 18V5l11-2v13 M3 18a3 3 0 1 0 6 0a3 3 0 1 0 -6 0 M14 16a3 3 0 1 0 6 0a3 3 0 1 0 -6 0")
    val Flag = stroke("M5 21V4 M5 4h11l-2 4 2 4H5")

    val Play = fill("M7 4.5v15l13-7.5z")
    val Stop = fill("M7 5h10a2 2 0 0 1 2 2v10a2 2 0 0 1 -2 2H7a2 2 0 0 1 -2 -2V7a2 2 0 0 1 2 -2z")

    private fun builder() = ImageVector.Builder(defaultWidth = 24.dp, defaultHeight = 24.dp, viewportWidth = 24f, viewportHeight = 24f)

    private fun stroke(path: String): ImageVector = builder().addPath(
        pathData = addPathNodes(path),
        stroke = SolidColor(Color.Black),
        strokeLineWidth = 2f,
        strokeLineCap = StrokeCap.Round,
        strokeLineJoin = StrokeJoin.Round,
    ).build()

    private fun fill(path: String): ImageVector = builder().addPath(
        pathData = addPathNodes(path),
        fill = SolidColor(Color.Black),
    ).build()
}
