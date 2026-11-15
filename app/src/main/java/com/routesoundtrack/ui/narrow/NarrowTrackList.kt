package com.routesoundtrack.ui.narrow

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.routesoundtrack.R
import com.routesoundtrack.domain.model.Cue
import com.routesoundtrack.domain.model.CueKind
import com.routesoundtrack.domain.model.Mood
import com.routesoundtrack.session.UiState
import com.routesoundtrack.ui.feelLabel
import com.routesoundtrack.ui.formatKm
import com.routesoundtrack.ui.formatKmShort
import com.routesoundtrack.ui.theme.PleosType
import com.routesoundtrack.ui.theme.LocalGlassColors
import com.routesoundtrack.ui.theme.MoodColors
import com.routesoundtrack.ui.theme.SoftIcons

/**
 * 세로 1단 "이번 경로의 곡" (시안 musicList_01·02 → 세로 타임라인).
 * 재생 지점마다 곡 카드 하나: 어떤 감정의 곡이 몇 km에서 나오는지. 곡 성격이 자동이면 아직 정해지지 않았으므로
 * "도로에 맞춰" 카드로 보여 준다(실제 곡은 그 지점의 도로·시간을 보고 Policy가 고른다).
 */
@Composable
fun NarrowTrackList(ui: UiState, onBack: () -> Unit, modifier: Modifier = Modifier) {
    val cs = MaterialTheme.colorScheme
    BoxWithConstraints(modifier.fillMaxSize()) {   // 바탕 번짐은 MainScreen의 screenBackdrop이 그린다
        val pad = if (maxWidth >= 700.dp) 48.dp else 32.dp
        val compact = maxHeight < 1000.dp
        val rowGap = if (compact) 12.dp else 18.dp
        val fixed = fixedMood(ui.settings.moodMode)
        Column(
            Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(pad),
            verticalArrangement = Arrangement.spacedBy(24.dp),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(20.dp)) {
                PillButton(stringResource(R.string.back_main), onBack, SoftIcons.Back)
                Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                    Text(stringResource(R.string.tracks_title), style = PleosType.TitleL, maxLines = 1)
                    if (ui.hasRoute) {
                        Text(
                            stringResource(
                                R.string.tracks_sub,
                                ui.destination.ifBlank { stringResource(R.string.destination_unknown) },
                                formatKm(ui.totalKm),
                                ui.plan?.cues?.size ?: 0,
                            ),
                            style = PleosType.LabelS,
                            color = cs.onSurfaceVariant,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                }
            }
            Row(
                Modifier.height(48.dp).clip(RoundedCornerShape(percent = 50)).background(cs.onSurface.copy(alpha = 0.06f)).padding(horizontal = 20.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                MoodDot(fixed, 18.dp)
                Text(
                    if (fixed == null) stringResource(R.string.tracks_auto) else stringResource(R.string.tracks_fixed, stringResource(feelLabel(fixed))),
                    style = PleosType.LabelS,
                    color = cs.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }

            val cues = ui.plan?.cues.orEmpty().sortedBy { if (it.kind == CueKind.ARRIVAL) Double.MAX_VALUE else it.atKm }
            if (cues.isEmpty()) {
                Text(stringResource(R.string.no_route_cues), style = PleosType.TitleS, color = cs.onSurfaceVariant)
            } else {
                Column(verticalArrangement = Arrangement.spacedBy(rowGap)) {
                    cues.forEachIndexed { i, cue ->   // forEachIndexed: Java의 for (int i...) + 요소를 같이 받는다
                        TrackRow(
                            cue = cue,
                            fixed = fixed,
                            first = i == 0,
                            last = i == cues.lastIndex,
                            height = if (compact) 112.dp else 150.dp,
                            gap = rowGap,
                            wordStyle = if (compact) PleosType.TitleM else PleosType.TitleL,
                            ghostSize = if (compact) 96.sp else 120.sp,
                        )
                    }
                }
            }
            Text(
                stringResource(R.string.tracks_footer),
                modifier = Modifier.fillMaxWidth(),
                style = PleosType.LabelS,
                color = cs.onSurfaceVariant,
                textAlign = TextAlign.Center,
            )
        }
    }
}

@Composable
private fun TrackRow(
    cue: Cue,
    fixed: Mood?,
    first: Boolean,
    last: Boolean,
    height: Dp,
    gap: Dp,
    wordStyle: TextStyle,
    ghostSize: TextUnit,
) {
    val cs = MaterialTheme.colorScheme
    val night = LocalGlassColors.current.night
    val mood: Mood? = if (cue.kind == CueKind.ARRIVAL) Mood.ARRIVAL else fixed   // null = 자동(아직 미정)
    val dot = MoodColors.dot(mood, night)
    val rail = cs.onSurface.copy(alpha = if (night) 0.20f else 0.12f)
    // 카드 위 글자: 주간 파스텔 카드 = 잉크색, 야간 짙은 카드 = 흰색
    val fg = if (night) Color.White else MoodColors.blobInk
    val fgSub = if (night) Color.White.copy(alpha = 0.88f) else Color(0xFF3A3B48)
    val ghostColor = if (night) Color.White.copy(alpha = 0.13f) else Color.White.copy(alpha = 0.72f)
    val pillBg = if (night) Color(0xFF141220).copy(alpha = 0.60f) else Color.White.copy(alpha = 0.62f)
    val word = if (mood != null) stringResource(feelLabel(mood)) else stringResource(R.string.tracks_auto_word)
    val sub = when {
        cue.kind == CueKind.ARRIVAL -> stringResource(R.string.arrival_detail)
        mood == null -> stringResource(R.string.tracks_auto_sub)
        cue.kind == CueKind.START -> stringResource(R.string.cue_start)
        else -> stringResource(R.string.cue_mid)
    }
    val where = if (cue.kind == CueKind.ARRIVAL) stringResource(R.string.cue_cap_arrival) else "${formatKmShort(cue.atKm)} km"
    val ghost = when (mood) {
        Mood.GROOVE -> "lo-fi"
        Mood.UPBEAT -> "synth"
        Mood.HYPE -> "edm"
        Mood.CHILL -> "quiet"
        Mood.ARRIVAL -> "fin"
        null -> "auto"
    }
    Row(Modifier.fillMaxWidth().height(height), horizontalArrangement = Arrangement.spacedBy(18.dp)) {
        // 타임라인 레일: 점과 위아래 선. 선은 줄 간격(gap)만큼 넘쳐 그려 다음 줄과 이어진다.
        Canvas(Modifier.width(28.dp).fillMaxHeight().clearAndSetSemantics { }) {
            val cx = size.width / 2
            val cy = size.height / 2
            val half = gap.toPx() / 2
            if (!first) drawLine(rail, Offset(cx, -half), Offset(cx, cy), 4.dp.toPx())
            if (!last) drawLine(rail, Offset(cx, cy), Offset(cx, size.height + half), 4.dp.toPx())
            drawCircle(dot.copy(alpha = 0.2f), 18.dp.toPx(), Offset(cx, cy))
            drawCircle(dot, 12.dp.toPx(), Offset(cx, cy))
        }
        Box(Modifier.weight(1f).fillMaxHeight().clip(RoundedCornerShape(40.dp)).moodArt(mood, night)) {
            Text(
                ghost,
                modifier = Modifier.align(Alignment.BottomEnd).offset(x = (-24).dp, y = 34.dp).clearAndSetSemantics { },
                style = PleosType.display(ghostSize),
                color = ghostColor,
                maxLines = 1,
                softWrap = false,
                overflow = TextOverflow.Visible,
            )
            Row(
                Modifier.fillMaxSize().padding(start = 32.dp, end = 28.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(16.dp),
            ) {
                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text(word, style = wordStyle, color = fg, maxLines = 1)
                    Text(sub, style = PleosType.LabelS, color = fgSub, maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
                Box(
                    Modifier.height(48.dp).clip(RoundedCornerShape(percent = 50)).background(pillBg).padding(horizontal = 20.dp),
                    contentAlignment = Alignment.Center,
                ) {
                    Text(where, style = PleosType.LabelS, color = fg, maxLines = 1)
                }
            }
        }
    }
}
