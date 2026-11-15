package com.routesoundtrack.ui.theme

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Shapes
import androidx.compose.ui.unit.dp

/** 시안 v2: 모서리를 더 둥글게. 칩·버튼(medium)은 알약 모양, 카드(large)는 32 dp. */
val RsShapes = Shapes(
    small = RoundedCornerShape(16.dp),
    medium = RoundedCornerShape(percent = 50),
    large = RoundedCornerShape(32.dp),
)
