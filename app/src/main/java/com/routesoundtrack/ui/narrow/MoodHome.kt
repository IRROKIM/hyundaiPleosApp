package com.routesoundtrack.ui.narrow

import android.view.HapticFeedbackConstants
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInRoot
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.em
import androidx.compose.ui.unit.sp
import androidx.compose.ui.zIndex
import com.routesoundtrack.R
import com.routesoundtrack.domain.model.Mood
import com.routesoundtrack.domain.model.MoodMode
import com.routesoundtrack.session.UiState
import com.routesoundtrack.ui.etaText
import com.routesoundtrack.ui.components.LocalMoodRipple
import com.routesoundtrack.ui.feelLabel
import com.routesoundtrack.ui.formatDuration
import com.routesoundtrack.ui.formatKm
import com.routesoundtrack.ui.theme.PleosType
import com.routesoundtrack.ui.theme.LocalGlassColors
import com.routesoundtrack.ui.theme.MoodColors
import com.routesoundtrack.ui.theme.SoftIcons

/**
 * 메인 화면 "지금 당신의 감정은 어디로 향하고 있나요?" (세로 1단, 시안 MainPortrait).
 * 감정 원 4개 중 하나를 누르면 그 곡 성격으로 사운드트랙을 켜고 첫 곡을 바로 튼다(onPick).
 * 켜진 뒤 정차 중이면 이 화면에 남아 "재생 중" 상태를 보여 준다(시안 "메인 재생 중"):
 * 고른 원에 테두리와 "재생 중" 칩, 나머지 원은 흐리게, 아래 막대는 지금 곡 + 주행 화면 + 끄기.
 * 이때 다른 원을 누르면 곡 성격이 바뀌고 그 색의 곡이 바로 나온다.
 */
@Composable
fun MoodHome(
    ui: UiState,
    onPick: (MoodMode) -> Unit,
    onStop: () -> Unit,
    onOpenDrive: () -> Unit,
    onOpenSettings: () -> Unit,
    onOpenTracks: () -> Unit,
    modifier: Modifier = Modifier,
) {
    BoxWithConstraints(modifier.fillMaxSize()) {
        val pad = if (maxWidth >= 700.dp) 48.dp else 32.dp
        val compact = maxHeight < 1000.dp
        val showMark = maxWidth >= 760.dp   // 좁은 창(628 dp 등)에서는 워드마크를 빼서 버튼 자리를 확보한다
        val title = if (compact) PleosType.HeadlineS else PleosType.HeadlineM
        Column(Modifier.fillMaxSize().padding(pad), verticalArrangement = Arrangement.spacedBy(20.dp)) {
            Column {
                TopBar(showMark, onOpenSettings, onOpenTracks)
                Spacer(Modifier.height(20.dp))
                Text(
                    stringResource(R.string.feel_title),
                    style = title,
                    letterSpacing = (-0.02).em,
                    maxLines = 2,
                )
                Spacer(Modifier.height(6.dp))
                Text(
                    stringResource(if (ui.running) R.string.feel_hint_playing else R.string.feel_hint),
                    style = PleosType.LabelM,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            // zIndex(-1f): 번진 원이 위·아래 글자보다 먼저(뒤에) 그려지게 한다
            BoxWithConstraints(Modifier.weight(1f).fillMaxWidth().zIndex(-1f)) {
                val fw = maxWidth
                val fh = maxHeight
                val wide = fh / fw < 0.75f                                     // Dp / Dp = Float(비율)
                // 누르는 원(글자가 올라가는 단색 부분)의 지름. 원끼리 겹치지 않을 만큼, 150–250 dp.
                val core = (if (wide) minOf(fw * 0.24f, fh * 0.46f) else minOf(fw * 0.40f, fh * 0.38f)).coerceIn(150.dp, 250.dp)
                BlobField(
                    ui = ui,
                    core = core,
                    wordStyle = if (core >= 210.dp) PleosType.TitleL else PleosType.TitleM,
                    place = { if (wide) it.wide else it.tall },
                    onPick = onPick,
                    modifier = Modifier.fillMaxSize(),
                )
            }
            if (ui.running) PlayingBar(ui, onStop, onOpenDrive) else RouteBar(ui, onOpenSettings)
        }
    }
}

/**
 * 감정 원 하나. tall·wide = 세로 1단 원 영역 안의 중심 위치(0..1), screen = 가로(창 1개) 화면 전체에서의 중심 위치.
 * 세로로 긴 창은 지그재그 2열, 낮고 넓은 창은 가로 지그재그, 가로 화면은 제목 오른쪽에 흩어 놓는다(시안 Main).
 */
internal class FeelBlob(val mode: MoodMode, val mood: Mood, val genre: String, val tall: Offset, val wide: Offset, val screen: Offset)

internal val FeelBlobs = listOf(
    FeelBlob(MoodMode.UPBEAT, Mood.UPBEAT, "synthwave", Offset(0.28f, 0.24f), Offset(0.14f, 0.36f), Offset(0.535f, 0.273f)),
    FeelBlob(MoodMode.GROOVE, Mood.GROOVE, "lo-fi", Offset(0.72f, 0.40f), Offset(0.38f, 0.66f), Offset(0.820f, 0.463f)),
    FeelBlob(MoodMode.HYPE, Mood.HYPE, "EDM", Offset(0.28f, 0.64f), Offset(0.62f, 0.36f), Offset(0.333f, 0.570f)),
    FeelBlob(MoodMode.CHILL, Mood.CHILL, "quiet lo-fi", Offset(0.72f, 0.80f), Offset(0.86f, 0.66f), Offset(0.606f, 0.686f)),
)

/**
 * 감정 원들(번진 메시 + 누르는 가운데 원). place = 원 중심의 영역 비율 위치, core = 누르는 원 지름.
 * 사운드트랙이 켜져 있고 곡 성격을 원으로 골랐으면 그 원에 테두리를 두르고 나머지 번짐을 32%로 흐린다.
 */
@Composable
internal fun BlobField(
    ui: UiState,
    core: Dp,
    wordStyle: TextStyle,
    place: (FeelBlob) -> Offset,
    onPick: (MoodMode) -> Unit,
    modifier: Modifier,
    genreSpacing: TextUnit = 0.sp,
) {
    val night = LocalGlassColors.current.night
    val view = LocalView.current
    val ripple = LocalMoodRipple.current
    var fieldOrigin by remember { mutableStateOf(Offset.Zero) }   // 이 영역의 왼쪽 위 (루트 좌표). 파동 중심을 구할 때 쓴다
    val selected = if (ui.running) fixedMood(ui.settings.moodMode) else null
    BoxWithConstraints(modifier.onGloballyPositioned { fieldOrigin = it.positionInRoot() }) {
        val fw = maxWidth
        val fh = maxHeight
        // 1) 번진 원: 파스텔 메시(기준색 + 이웃색 2개). Modifier.blur 없이 그라디언트만 겹친다(minSdk 28 대응)
        Canvas(Modifier.matchParentSize()) {
            val r = core.toPx() * 1.04f                                // 번진 원 반지름 (누르는 원 지름의 1.04배)
            for (b in FeelBlobs) {
                val p = place(b)
                val dim = selected != null && b.mood != selected
                drawMoodMesh(MoodColors.mesh(b.mood), Offset(p.x * size.width, p.y * size.height), r, night, alpha = if (dim) 0.32f else 1f)
            }
        }
        // 2) 누를 수 있는 가운데 원 + 감정 문구
        for (b in FeelBlobs) {
            val p = place(b)
            BlobButton(
                blob = b,
                core = core,
                on = b.mood == selected,
                dim = selected != null && b.mood != selected,
                sounding = b.mood == selected && ui.nowPlaying?.mood == b.mood,
                wordStyle = wordStyle,
                genreSpacing = genreSpacing,
                modifier = Modifier.offset(x = fw * p.x - core / 2, y = fh * p.y - core / 2),
                onClick = {
                    view.performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP)
                    // 누른 원의 중심에서 그 곡 색의 파동을 퍼뜨린다 (constraints = 이 영역의 px 크기)
                    ripple.fire(b.mood, fieldOrigin + Offset(constraints.maxWidth * p.x, constraints.maxHeight * p.y))
                    onPick(b.mode)
                },
            )
        }
    }
}

@Composable
private fun BlobButton(
    blob: FeelBlob,
    core: Dp,
    on: Boolean,
    dim: Boolean,
    sounding: Boolean,
    wordStyle: TextStyle,
    genreSpacing: TextUnit,
    modifier: Modifier,
    onClick: () -> Unit,
) {
    val feel = stringResource(feelLabel(blob.mood))
    val desc = if (on) stringResource(R.string.now_playing_feel, feel) else stringResource(R.string.feel_tap, feel)
    // 흐려진 원 위 글자: 야간은 어두운 바탕이 비쳐 잉크색이 안 읽히므로 밝은 글자로 바꾼다
    val fg = if (dim && LocalGlassColors.current.night) MaterialTheme.colorScheme.onSurface else MoodColors.blobInk
    Box(
        modifier
            .size(core)
            .clip(CircleShape)
            .clickable(role = Role.Button, onClick = onClick)
            .semantics { contentDescription = desc },
        contentAlignment = Alignment.Center,
    ) {
        // 고른 원: 선 대신 가장자리에 우윳빛 파스텔 번짐 고리 (안쪽·바깥쪽 모두 투명하게 풀려서 몽글하게)
        if (on) Canvas(Modifier.matchParentSize()) {
            drawCircle(
                Brush.radialGradient(
                    0.62f to Color(0x00FFF4FB),
                    0.80f to Color(0x8CFFF4FB),
                    0.90f to Color(0xB3FFFFFF),
                    1.00f to Color(0x00FFFFFF),
                    center = center,
                    radius = size.minDimension / 2f,
                ),
            )
        }
        Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Text(feel, style = wordStyle, color = fg, maxLines = 1)
            Text(blob.genre, style = PleosType.LabelS, letterSpacing = genreSpacing, color = fg, maxLines = 1)
            if (sounding) {
                Row(
                    Modifier.padding(top = 4.dp).clip(RoundedCornerShape(percent = 50)).background(MoodColors.blobInk).padding(horizontal = 14.dp, vertical = 4.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    Icon(SoftIcons.Play, contentDescription = null, modifier = Modifier.size(14.dp), tint = Color.White)
                    Text(stringResource(R.string.now_playing), style = PleosType.LabelS, color = Color.White, maxLines = 1)
                }
            }
        }
    }
}

@Composable
private fun TopBar(showMark: Boolean, onOpenSettings: () -> Unit, onOpenTracks: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().height(76.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        if (showMark) {
            Text("Route Soundtrack", style = PleosType.BodyM, fontWeight = PleosType.Strong, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1)
        }
        Spacer(Modifier.weight(1f))
        PillButton(stringResource(R.string.open_tracks), onOpenTracks, SoftIcons.List)
        PillButton(stringResource(R.string.open_settings), onOpenSettings, SoftIcons.Route)
    }
}

/** 하단 "오늘 경로" 막대(꺼져 있을 때). 누르면 경로·설정 화면으로 간다. */
@Composable
private fun RouteBar(ui: UiState, onOpenSettings: () -> Unit) {
    val cs = MaterialTheme.colorScheme
    val glass = LocalGlassColors.current
    val title = when {
        ui.finished -> stringResource(R.string.phase_ended)
        ui.hasRoute -> stringResource(R.string.today_route, ui.destination.ifBlank { stringResource(R.string.destination_unknown) })
        else -> stringResource(R.string.no_route)
    }
    Surface(
        onClick = onOpenSettings,
        shape = RoundedCornerShape(percent = 50),
        color = glass.pill,
        contentColor = cs.onSurface,
        border = BorderStroke(1.5.dp, glass.line),
    ) {
        Row(
            Modifier.fillMaxWidth().height(104.dp).padding(start = 30.dp, end = 16.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text(title, style = PleosType.BodyS, fontWeight = PleosType.Strong, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Text(routeLine(ui), style = PleosType.LabelS, color = cs.onSurfaceVariant, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
            DottedIconCircle(SoftIcons.ArrowRight, 72.dp, 26.dp)
        }
    }
}

/** "120 km · 약 1시간 40분 · 재생 지점 5곳". 경로가 없으면 안내 문구. */
@Composable
internal fun routeLine(ui: UiState): String = when {
    ui.hasRoute -> stringResource(
        R.string.route_line,
        formatKm(ui.remainingKm),
        ui.etaMin?.let { etaText(it) } ?: "—",
        ui.plan?.cues?.size ?: 0,
    )
    ui.armed -> stringResource(R.string.armed_waiting)
    else -> stringResource(R.string.no_route_line)
}

/**
 * 하단 "재생 중" 막대(켜져 있을 때, 시안 MainPlaying): 곡 성격 구슬 · 지금 곡 · 주행 화면 · 끄기.
 * "주행 화면"은 시안에 없는 버튼이다. 정차 중에도 주행 화면을 열 수 있게 둔다(에뮬레이터는 주행 판정이 거의 켜지지 않는다).
 */
@Composable
internal fun PlayingBar(
    ui: UiState,
    onStop: () -> Unit,
    onOpenDrive: () -> Unit,
    modifier: Modifier = Modifier,
    dotSize: Dp = 24.dp,
    titleStyle: TextStyle = PleosType.BodyS,
) {
    val cs = MaterialTheme.colorScheme
    val glass = LocalGlassColors.current
    val np = ui.nowPlaying
    val chosen = fixedMood(ui.settings.moodMode)
    val chosenWord = stringResource(if (chosen != null) feelLabel(chosen) else R.string.mood_mode_auto)
    val title = if (np != null) stringResource(R.string.now_playing_feel, stringResource(feelLabel(np.mood))) else stringResource(R.string.session_on_feel, chosenWord)
    val sub = when {
        np != null -> "${np.title} · ${formatDuration(np.durationSec)}"
        ui.nextCueInKm != null -> stringResource(R.string.notif_next, formatKm(ui.nextCueInKm))
        else -> stringResource(R.string.silent_next)
    }
    Surface(
        modifier = modifier,
        shape = RoundedCornerShape(percent = 50),
        color = glass.pill,
        contentColor = cs.onSurface,
        border = BorderStroke(1.5.dp, glass.line),
    ) {
        Row(
            Modifier.height(104.dp).padding(start = 28.dp, end = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(18.dp),
        ) {
            MoodDot(np?.mood ?: chosen, dotSize)
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text(title, style = titleStyle, fontWeight = PleosType.Strong, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Text(sub, style = PleosType.LabelS, color = cs.onSurfaceVariant, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
            Surface(
                onClick = onOpenDrive,
                shape = RoundedCornerShape(percent = 50),
                color = glass.chip,
                contentColor = cs.onSurface,
            ) {
                Box(Modifier.height(80.dp).padding(horizontal = 26.dp), contentAlignment = Alignment.Center) {
                    Text(stringResource(R.string.open_drive), style = PleosType.BodyS, fontWeight = PleosType.Strong, maxLines = 1)
                }
            }
            InkButton(stringResource(R.string.stop_short), SoftIcons.Stop, onStop, Modifier.height(80.dp))
        }
    }
}
