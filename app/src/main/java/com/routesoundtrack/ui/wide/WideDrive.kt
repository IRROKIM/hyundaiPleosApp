package com.routesoundtrack.ui.wide

import android.view.HapticFeedbackConstants
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
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
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.em
import androidx.compose.ui.unit.sp
import com.routesoundtrack.R
import com.routesoundtrack.domain.model.Road
import com.routesoundtrack.session.Phase
import com.routesoundtrack.session.UiState
import com.routesoundtrack.ui.etaText
import com.routesoundtrack.ui.feelLabel
import com.routesoundtrack.ui.formatDuration
import com.routesoundtrack.ui.formatKm
import com.routesoundtrack.ui.narrow.BigValue
import com.routesoundtrack.ui.narrow.CueChips
import com.routesoundtrack.ui.narrow.InfoPill
import com.routesoundtrack.ui.narrow.PillButton
import com.routesoundtrack.ui.narrow.RouteCurve
import com.routesoundtrack.ui.narrow.StopOrb
import com.routesoundtrack.ui.narrow.WaitingBlob
import com.routesoundtrack.ui.narrow.cardHead
import com.routesoundtrack.ui.narrow.cueCaption
import com.routesoundtrack.ui.narrow.glassCard
import com.routesoundtrack.ui.narrow.roadDot
import com.routesoundtrack.ui.narrow.roadTint
import com.routesoundtrack.ui.narrow.softGlow
import com.routesoundtrack.ui.roadLabel
import com.routesoundtrack.ui.theme.PleosType
import com.routesoundtrack.ui.theme.LocalGlassColors
import com.routesoundtrack.ui.theme.MoodColors
import com.routesoundtrack.ui.theme.SoftIcons

/**
 * 가로(앱 창 1개) 주행 화면 (시안 Drive · 1682×1210 기준).
 * 왼쪽 큰 글래스 카드: 다음 곡까지 N km(가장 큰 글자) · 진행 곡선 · 재생 지점 칩 · 현재 위치와 지나온 도로.
 * 오른쪽 4칸: 지금 도로 · 재생 중 · 도착까지 · 사운드트랙 끄기. 경로 대기·고속 구간·도착 곡 상태는 세로 주행 화면과 같은 문장을 쓴다.
 * 계속 움직이는 효과는 없다(Pleos 주행 중 애니메이션 금지). onBack: 정차 중에 직접 열었을 때만 "메인" 버튼을 보여 준다.
 */
@Composable
fun WideDrive(ui: UiState, onStop: () -> Unit, modifier: Modifier = Modifier, onBack: (() -> Unit)? = null) {
    val glass = LocalGlassColors.current
    BoxWithConstraints(modifier.fillMaxSize().softGlow(glass, cx = 0.28f, cy = 0.42f)) {
        val compact = maxHeight < 1000.dp
        val value = if (compact) PleosType.TitleL else PleosType.HeadlineM
        Row(Modifier.fillMaxSize().padding(56.dp), horizontalArrangement = Arrangement.spacedBy(40.dp)) {
            NextCueCard(ui, compact, onBack, Modifier.weight(1000f).fillMaxHeight())
            Column(Modifier.weight(530f).fillMaxHeight(), verticalArrangement = Arrangement.spacedBy(if (compact) 20.dp else 28.dp)) {
                val card = Modifier.weight(1f).fillMaxWidth()
                RoadCard(ui, value, compact, card)
                NowCard(ui, value, compact, card)
                EtaCard(ui, value, compact, card)
                StopCard(onStop, compact, card)
            }
        }
    }
}

@Composable
private fun NextCueCard(ui: UiState, compact: Boolean, onBack: (() -> Unit)?, modifier: Modifier) {
    val cs = MaterialTheme.colorScheme
    val glass = LocalGlassColors.current
    Column(
        modifier.glassCard(glass, RoundedCornerShape(48.dp)).padding(start = 48.dp, top = 48.dp, end = 48.dp, bottom = 40.dp),
        verticalArrangement = Arrangement.spacedBy(if (compact) 12.dp else 20.dp),
    ) {
        Row(verticalAlignment = Alignment.Top, horizontalArrangement = Arrangement.spacedBy(16.dp)) {
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Text(stringResource(cardHead(ui)), style = PleosType.BodyM, fontWeight = PleosType.Strong, maxLines = 1)
                Text(cueCaption(ui), style = PleosType.LabelS, color = cs.onSurfaceVariant, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
            if (onBack != null) {
                PillButton(stringResource(R.string.back_main), onBack, SoftIcons.Back)
            } else {
                LineChip(stringResource(R.string.driving_chip), SoftIcons.Lock, height = 52.dp)
            }
        }
        BigValue(
            ui,
            numSize = if (compact) 128.sp else 176.sp,
            unit = if (compact) PleosType.TitleL else PleosType.HeadlineM,
            sentence = if (compact) PleosType.HeadlineS else PleosType.HeadlineL,
        )
        // 남는 높이: 평소에는 진행 곡선, 경로 대기 중에는 "준비된 첫 곡" 원
        BoxWithConstraints(Modifier.weight(1f).fillMaxWidth(), contentAlignment = Alignment.Center) {
            if (!ui.hasRoute) {
                val blob = minOf(maxHeight, 300.dp)
                if (blob >= 160.dp) WaitingBlob(ui, blob)
            } else if (maxHeight >= 100.dp) {
                RouteCurve(ui, Modifier.fillMaxSize())
            }
        }
        if (ui.hasRoute) {
            Box(Modifier.padding(horizontal = 24.dp)) { CueChips(ui, if (compact) 80.dp else 96.dp, labelStyle = PleosType.BodyM) }
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(18.dp, Alignment.CenterHorizontally)) {
                Text(stringResource(R.string.now_km, formatKm(ui.progressKm)), style = PleosType.BodyS, fontWeight = PleosType.Strong, maxLines = 1)
                val trail = roadTrail(ui)
                if (trail.isNotEmpty()) {
                    Text("·", style = PleosType.LabelM, color = cs.onSurfaceVariant, modifier = Modifier.clearAndSetSemantics { })
                    Text(trail, style = PleosType.LabelM, color = cs.onSurfaceVariant, maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
            }
        }
    }
}

/** 지나온 도로: "시내 → 고속도로 → 정체" (같은 도로가 이어지면 하나로). */
@Composable
private fun roadTrail(ui: UiState): String {
    val roads = mutableListOf<Road>()
    for (span in ui.roadTrail) if (roads.lastOrNull() != span.road) roads += span.road
    if (roads.isEmpty()) ui.road?.let { roads += it }
    return roads.map { stringResource(roadLabel(it)) }.joinToString(" → ")
}

// ---- 오른쪽 4칸 -------------------------------------------------------------

private fun cardPadding(compact: Boolean) =
    if (compact) PaddingValues(start = 36.dp, top = 22.dp, end = 26.dp, bottom = 22.dp)
    else PaddingValues(start = 44.dp, top = 34.dp, end = 34.dp, bottom = 34.dp)

@Composable
private fun CardLabel(text: String) {
    Text(text, style = PleosType.LabelM, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1)
}

@Composable
private fun CardSub(text: String) {
    Text(text, style = PleosType.LabelS, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1, overflow = TextOverflow.Ellipsis)
}

@Composable
private fun CardValue(text: String, style: TextStyle, modifier: Modifier = Modifier) {
    Text(text, modifier = modifier, style = style, maxLines = 1)
}

/** 테두리만 있는 작은 칩(아이콘 + 글자). */
@Composable
private fun LineChip(text: String, icon: ImageVector? = null, height: androidx.compose.ui.unit.Dp = 48.dp) {
    val glass = LocalGlassColors.current
    Row(
        Modifier.height(height).border(1.5.dp, glass.line, RoundedCornerShape(percent = 50)).padding(horizontal = 20.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        if (icon != null) Icon(icon, contentDescription = null, modifier = Modifier.size(20.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant)
        Text(text, style = PleosType.LabelS, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1)
    }
}

/** 테두리 원 안의 선 아이콘(장식). */
@Composable
private fun IconRing(icon: ImageVector) {
    Box(
        Modifier.size(64.dp).border(1.5.dp, LocalGlassColors.current.line, CircleShape).clearAndSetSemantics { },
        contentAlignment = Alignment.Center,
    ) {
        Icon(icon, contentDescription = null, modifier = Modifier.size(28.dp), tint = MaterialTheme.colorScheme.onSurface)
    }
}

@Composable
private fun RoadCard(ui: UiState, value: TextStyle, compact: Boolean, modifier: Modifier) {
    val (a, b) = roadTint(ui.road)
    InfoPill(a, b, modifier, radius = 64.dp, padding = cardPadding(compact)) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.Top) {
            Box(Modifier.weight(1f)) { CardLabel(stringResource(R.string.label_road)) }
            if (ui.phase == Phase.DEFERRED) LineChip(stringResource(R.string.silent_hold), SoftIcons.Route)
        }
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(18.dp)) {
            Box(Modifier.size(20.dp).clip(CircleShape).background(roadDot(ui.road)))
            val road = stringResource(roadLabel(ui.road))
            CardValue(if (ui.night) "$road · ${stringResource(R.string.night)}" else road, value)
        }
    }
}

/** 재생 중. 안내 음성이 나오는 동안에는 내용을 40%로 흐리고 "안내 음성 우선 · 음악 30%" 칩을 위에 띄운다. */
@Composable
private fun NowCard(ui: UiState, value: TextStyle, compact: Boolean, modifier: Modifier) {
    val cs = MaterialTheme.colorScheme
    val np = ui.nowPlaying
    val mesh = MoodColors.mesh(np?.mood)
    Box(modifier) {
        InfoPill(mesh.base, mesh.mixB, Modifier.fillMaxSize(), radius = 64.dp, padding = cardPadding(compact)) {
            val dim = Modifier.alpha(if (ui.ducked) 0.4f else 1f)
            Row(dim.fillMaxWidth(), verticalAlignment = Alignment.Top) {
                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    CardLabel(stringResource(if (np != null) R.string.now_playing else R.string.label_now))
                    CardSub(
                        when {
                            np != null -> np.title
                            !ui.hasRoute -> stringResource(R.string.ready_first)
                            ui.phase == Phase.DEFERRED -> stringResource(R.string.silent_hold)
                            else -> stringResource(R.string.silent_next)
                        }
                    )
                }
                if (!compact) IconRing(SoftIcons.Music)
            }
            Row(dim, verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(18.dp)) {
                CardValue(stringResource(if (np != null) feelLabel(np.mood) else R.string.silent), value)
                if (np != null) LineChip(formatDuration(np.durationSec))
            }
        }
        if (ui.ducked) {
            Row(
                Modifier
                    .align(Alignment.CenterStart)
                    .padding(start = 40.dp)
                    .height(64.dp)
                    .clip(RoundedCornerShape(percent = 50))
                    .background(cs.primary)
                    .padding(horizontal = 24.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                Icon(SoftIcons.Speaker, contentDescription = null, modifier = Modifier.size(22.dp), tint = cs.onPrimary)
                Text(stringResource(R.string.ducked), style = PleosType.BodyS, fontWeight = PleosType.Strong, color = cs.onPrimary, maxLines = 1)
            }
        }
    }
}

@Composable
private fun EtaCard(ui: UiState, value: TextStyle, compact: Boolean, modifier: Modifier) {
    InfoPill(MoodColors.arrivalMesh.base, MoodColors.arrivalMesh.mixA, modifier, radius = 64.dp, padding = cardPadding(compact)) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.Top) {
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                CardLabel(stringResource(R.string.label_eta))
                CardSub(if (ui.hasRoute) ui.destination.ifBlank { stringResource(R.string.destination_unknown) } else stringResource(R.string.no_route))
            }
            if (!compact) IconRing(SoftIcons.Flag)
        }
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(18.dp)) {
            CardValue(if (ui.hasRoute) "${formatKm(ui.remainingKm)} ${stringResource(R.string.unit_km)}" else "—", value)
            if (ui.hasRoute) ui.etaMin?.let { LineChip(stringResource(R.string.eta_about, etaText(it))) }
        }
    }
}

/** 사운드트랙 끄기: 글자 + 큰 메시 원. 원 크기는 칸 높이에 맞춘다(최대 148). */
@Composable
private fun StopCard(onStop: () -> Unit, compact: Boolean, modifier: Modifier) {
    val cs = MaterialTheme.colorScheme
    val glass = LocalGlassColors.current
    val view = LocalView.current
    Surface(
        onClick = {
            view.performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP)
            onStop()
        },
        modifier = modifier,
        shape = RoundedCornerShape(64.dp),
        color = glass.glass,
        contentColor = cs.onSurface,
        border = BorderStroke(1.5.dp, glass.glassLine),
    ) {
        BoxWithConstraints(Modifier.fillMaxSize()) {
            val orb = minOf(148.dp, maxHeight - 32.dp)
            Row(
                Modifier.fillMaxSize().padding(start = if (compact) 36.dp else 44.dp, end = 16.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(20.dp),
            ) {
                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text(stringResource(R.string.session_stop), style = PleosType.BodyM, fontWeight = PleosType.Strong, maxLines = 1)
                    Text(stringResource(R.string.skip_rest), style = PleosType.LabelS, color = cs.onSurfaceVariant, maxLines = 2)
                }
                StopOrb(orb, orb * 0.27f)
            }
        }
    }
}
