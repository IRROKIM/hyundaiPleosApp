package com.routesoundtrack.ui.wide

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.em
import androidx.compose.ui.unit.sp
import com.routesoundtrack.R
import com.routesoundtrack.domain.model.Cue
import com.routesoundtrack.domain.model.CueKind
import com.routesoundtrack.domain.model.Mood
import com.routesoundtrack.domain.model.Preset
import com.routesoundtrack.session.UiState
import com.routesoundtrack.ui.feelLabel
import com.routesoundtrack.ui.formatKmShort
import com.routesoundtrack.ui.narrow.MoodDot
import com.routesoundtrack.ui.narrow.PillButton
import com.routesoundtrack.ui.narrow.fixedMood
import com.routesoundtrack.ui.narrow.moodArt
import com.routesoundtrack.ui.theme.PleosType
import com.routesoundtrack.ui.theme.LocalGlassColors
import com.routesoundtrack.ui.theme.SoftIcons

/**
 * 가로(앱 창 1개) "이번 경로의 곡" (시안 MusicList · 1682×1210 기준).
 * 재생 지점마다 곡 카드 하나, 3칸 격자(첫 카드는 2칸). 카드 = 감정 메시 타일 + 큰 영문 장르 글자 + 지점 배지 + 감정 문구.
 * 시안의 "곡 성격" 탭·미리듣기·바꾸기 버튼은 재생 엔진에 미리듣기 기능이 아직 없어서 넣지 않았다.
 */
@Composable
fun WideTracks(ui: UiState, onBack: () -> Unit, modifier: Modifier = Modifier) {
    val cs = MaterialTheme.colorScheme
    val glass = LocalGlassColors.current
    val fixed = fixedMood(ui.settings.moodMode)
    Column(modifier.fillMaxSize().padding(56.dp), verticalArrangement = Arrangement.spacedBy(32.dp)) {
        Row(Modifier.fillMaxWidth().height(76.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(28.dp)) {
            PillButton(stringResource(R.string.back_main), onBack, SoftIcons.Back)
            Text(stringResource(R.string.tracks_title), style = PleosType.TitleL, maxLines = 1)
            Spacer(Modifier.weight(1f))
            Row(
                Modifier.height(56.dp).clip(RoundedCornerShape(percent = 50)).background(glass.chip).padding(horizontal = 24.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                MoodDot(fixed, 18.dp)
                Text(
                    if (fixed == null) stringResource(R.string.tracks_auto) else stringResource(R.string.tracks_fixed, stringResource(feelLabel(fixed))),
                    style = PleosType.LabelS, color = cs.onSurfaceVariant, maxLines = 1,
                )
            }
        }

        val cues = ui.plan?.cues.orEmpty().sortedBy { if (it.kind == CueKind.ARRIVAL) Double.MAX_VALUE else it.atKm }
        if (cues.isEmpty()) {
            Text(stringResource(R.string.no_route_cues), style = PleosType.TitleM, color = cs.onSurfaceVariant)
            return@Column
        }
        // 줄 나누기: 첫 줄 = [첫 카드(2칸), 둘째 카드], 그다음은 3장씩
        val rows = listOf(cues.take(2)) + cues.drop(2).chunked(3)
        BoxWithConstraints(Modifier.weight(1f).fillMaxWidth()) {
            val rowGap = 36.dp
            val fit = (maxHeight - rowGap * (rows.size - 1)) / rows.size
            val rowH = maxOf(fit, 240.dp)   // 지점이 많아 너무 낮아지면 스크롤한다
            Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(rowGap)) {
                rows.forEachIndexed { r, row ->
                    Row(Modifier.fillMaxWidth().height(rowH), horizontalArrangement = Arrangement.spacedBy(32.dp)) {
                        row.forEachIndexed { i, cue ->
                            val span = if (r == 0 && i == 0 && row.size == 2) 2f else 1f
                            TrackCard(ui, cue, fixed, Modifier.weight(span).fillMaxHeight())
                        }
                        val used = if (r == 0 && row.size == 2) 3 else row.size
                        repeat(3 - used) { Spacer(Modifier.weight(1f)) }
                    }
                }
            }
        }
    }
}

/** 곡 카드 하나. 곡 성격이 자동이면 아직 정해지지 않았으므로 "도로에 맞춰"(라일락 회색)로 보여 준다. */
@Composable
private fun TrackCard(ui: UiState, cue: Cue, fixed: Mood?, modifier: Modifier) {
    val night = LocalGlassColors.current.night
    val mood: Mood? = if (cue.kind == CueKind.ARRIVAL) Mood.ARRIVAL else fixed
    val fg = if (night) Color.White else Color(0xFF17181B)
    val sub = if (night) Color.White.copy(alpha = 0.88f) else Color(0xFF3A3B48)
    val badge = when (cue.kind) {
        CueKind.START -> stringResource(R.string.cue_cap_start, formatKmShort(cue.atKm))
        CueKind.MID -> stringResource(R.string.cue_cap_mid, formatKmShort(cue.atKm))
        CueKind.ARRIVAL -> stringResource(R.string.cue_cap_arrival)
    }
    val note = when {
        cue.kind == CueKind.ARRIVAL -> stringResource(R.string.arrival_detail)
        mood == null -> stringResource(R.string.tracks_auto_sub)
        cue.kind == CueKind.MID && ui.settings.preset != Preset.LONG_HAUL -> stringResource(R.string.note_mid_hold)
        else -> null
    }
    val shape = RoundedCornerShape(48.dp)
    BoxWithConstraints(modifier.clip(shape).moodArt(mood, night)) {
        val roomy = maxHeight >= 360.dp
        Text(
            ghostWord(mood),
            modifier = Modifier.align(Alignment.BottomStart).offset(x = 30.dp, y = 50.dp).clearAndSetSemantics { },
            style = PleosType.display(176.sp), letterSpacing = (-0.04).em,
            color = if (night) Color.White.copy(alpha = 0.13f) else Color.White.copy(alpha = 0.72f),
            maxLines = 1, softWrap = false, overflow = TextOverflow.Visible,
        )
        Column(
            Modifier.fillMaxSize().padding(start = 32.dp, top = 30.dp, end = 30.dp, bottom = 32.dp),
            verticalArrangement = Arrangement.SpaceBetween,
        ) {
            Box(
                Modifier
                    .height(48.dp)
                    .clip(RoundedCornerShape(percent = 50))
                    .background(if (night) Color(0xFF141220).copy(alpha = 0.36f) else Color.White.copy(alpha = 0.55f))
                    .border(1.5.dp, if (night) Color.White.copy(alpha = 0.30f) else Color(0xFF17181B).copy(alpha = 0.10f), RoundedCornerShape(percent = 50))
                    .padding(horizontal = 20.dp),
                contentAlignment = Alignment.Center,
            ) {
                Text(badge, style = PleosType.LabelS, color = fg, maxLines = 1)
            }
            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Text(
                    if (mood != null) stringResource(feelLabel(mood)) else stringResource(R.string.tracks_auto_word),
                    style = PleosType.TitleL, color = fg, maxLines = 1,
                )
                Text(genreLine(mood), style = PleosType.LabelS, color = sub, maxLines = 1)
                if (note != null) Text(note, style = PleosType.LabelS, color = sub, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
            // 시안의 미리듣기 버튼(높이 76) 자리. 비워 두어 글자가 뒤의 큰 장르 글자와 겹치지 않게 한다.
            if (roomy) Spacer(Modifier.height(76.dp))
        }
    }
}

/** 카드 뒤 큰 글자(장식). */
private fun ghostWord(mood: Mood?): String = when (mood) {
    Mood.CHILL -> "Quiet"
    Mood.GROOVE -> "Lo-fi"
    Mood.UPBEAT -> "Synthwave"
    Mood.HYPE -> "EDM"
    Mood.ARRIVAL -> "Arrival"
    null -> "Auto"
}

/** 장르 줄: 곡 이름은 재생할 때 정해지므로 장르와 빠르기만 적는다. */
private fun genreLine(mood: Mood?): String = when (mood) {
    Mood.CHILL -> "quiet lo-fi"
    Mood.GROOVE -> "lo-fi · 86 bpm"
    Mood.UPBEAT -> "synthwave · 112 bpm"
    Mood.HYPE -> "house · 124 bpm"
    Mood.ARRIVAL -> "arrival"
    null -> "lo-fi · synthwave · EDM"
}
