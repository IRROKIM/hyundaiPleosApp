package com.routesoundtrack.ui.components

import android.view.HapticFeedbackConstants
import androidx.annotation.StringRes
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.routesoundtrack.R
import com.routesoundtrack.domain.model.Density
import com.routesoundtrack.domain.model.MoodMode
import com.routesoundtrack.domain.model.Preset
import com.routesoundtrack.session.UiState
import com.routesoundtrack.ui.densityLabel
import com.routesoundtrack.ui.feelLabel
import com.routesoundtrack.ui.narrow.MoodDot
import com.routesoundtrack.ui.narrow.fixedMood
import com.routesoundtrack.ui.presetLabel
import com.routesoundtrack.ui.theme.PleosType
import com.routesoundtrack.ui.theme.LocalGlassColors
import com.routesoundtrack.ui.theme.LocalStatusColors
import com.routesoundtrack.ui.theme.SoftIcons

/**
 * 설정 카드 (시안 "경로 · 설정"의 오른쪽 카드): 주행 목적 · 음악 밀도 · 곡 성격 · 안내 시작 시 자동 시작 + 사운드트랙 켜기/끄기.
 * 바꾸면 재생 지점이 바로 다시 그려진다. 사운드트랙이 켜져 있거나 주행 규제 중이면 잠근다(잠금 띠 + 고르지 않은 칩 45%).
 * fill = true: 주어진 높이를 채우고 켜기 버튼을 맨 아래에 붙인다. 내용이 높이보다 길면 카드 안에서 스크롤한다(가로 화면).
 */
@Composable
fun SettingsCard(
    ui: UiState,
    onPreset: (Preset) -> Unit,
    onDensity: (Density) -> Unit,
    onMoodMode: (MoodMode) -> Unit,
    onAutoStart: (Boolean) -> Unit,
    onToggle: () -> Unit,
    modifier: Modifier = Modifier,
    fill: Boolean = false,
) {
    val glass = LocalGlassColors.current
    val shape = RoundedCornerShape(48.dp)
    val enabled = !ui.running && !ui.locked
    val body: @Composable ColumnScope.() -> Unit = {
        Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Text(stringResource(R.string.settings_title), style = PleosType.TitleS)
            Text(stringResource(R.string.settings_sub), style = PleosType.LabelS, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        if (!enabled) LockBanner(if (ui.locked) R.string.lock_driving else R.string.lock_running)
        ChipGroup(R.string.label_preset, Preset.entries, ui.settings.preset, enabled, onPreset, label = { stringResource(presetLabel(it)) })
        ChipGroup(R.string.label_density, Density.entries, ui.settings.density, enabled, onDensity, label = { stringResource(densityLabel(it)) })
        ChipGroup(
            R.string.label_mood, MoodMode.entries, ui.settings.moodMode, enabled, onMoodMode,
            label = { m -> fixedMood(m)?.let { stringResource(feelLabel(it)) } ?: stringResource(R.string.mood_mode_auto) },
            dot = { m -> MoodDot(fixedMood(m), 18.dp) },
        )
        AutoStartRow(ui.settings.autoStart, enabled, onAutoStart)
    }
    if (fill) {
        BoxWithConstraints(modifier.clip(shape).background(glass.card).border(1.5.dp, glass.line, shape)) {
            // heightIn(min = 카드 높이) + weight 빈칸: 내용이 짧으면 버튼이 맨 아래로 가고, 길면 그대로 스크롤된다
            Column(
                Modifier.fillMaxWidth().verticalScroll(rememberScrollState()).heightIn(min = maxHeight).padding(40.dp),
                verticalArrangement = Arrangement.spacedBy(24.dp),
            ) {
                body()
                Spacer(Modifier.weight(1f))
                SessionToggle(ui, onToggle)
            }
        }
    } else {
        Column(
            modifier.clip(shape).background(glass.card).border(1.5.dp, glass.line, shape).padding(36.dp),
            verticalArrangement = Arrangement.spacedBy(24.dp),
        ) {
            body()
            SessionToggle(ui, onToggle)
        }
    }
}

/** 칩 묶음: 이름 + 3칸 격자. 고른 칩은 잉크색으로 채운다. 모든 칩은 글자 라벨 + 터치 진동, 높이 72 dp. */
@Composable
private fun <T> ChipGroup(
    @StringRes title: Int,
    options: List<T>,
    selected: T,
    enabled: Boolean,
    onSelect: (T) -> Unit,
    label: @Composable (T) -> String,
    dot: (@Composable (T) -> Unit)? = null,
) {
    val cs = MaterialTheme.colorScheme
    val glass = LocalGlassColors.current
    val view = LocalView.current
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Text(stringResource(title), style = PleosType.LabelM, color = cs.onSurfaceVariant)
        for (row in options.chunked(3)) {   // chunked(3): 3개씩 끊어 줄을 만든다
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                for (opt in row) {
                    val on = opt == selected
                    Surface(
                        onClick = {
                            view.performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP)
                            onSelect(opt)
                        },
                        enabled = enabled,
                        modifier = Modifier.weight(1f).alpha(if (!enabled && !on) 0.45f else 1f),
                        shape = RoundedCornerShape(percent = 50),
                        color = if (on) cs.primary else glass.chip,
                        contentColor = if (on) cs.onPrimary else cs.onSurface,
                        border = BorderStroke(1.5.dp, if (on) cs.primary else glass.line),
                    ) {
                        Row(
                            Modifier.height(72.dp).padding(horizontal = 8.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(10.dp, Alignment.CenterHorizontally),
                        ) {
                            dot?.invoke(opt)
                            Text(label(opt), style = PleosType.BodyS, fontWeight = PleosType.Strong, maxLines = 1)
                        }
                    }
                }
                repeat(3 - row.size) { Spacer(Modifier.weight(1f)) }   // 마지막 줄이 덜 차도 칸 폭을 맞춘다
            }
        }
    }
}

/**
 * "안내 시작 시 자동 시작" 스위치(시안의 104×60 둥근 스위치). 기본은 꺼짐이다.
 * Pleos 규정상 음악은 사용자의 명시적 동작으로만 시작해야 하므로, 이 스위치를 켜는 것이 그 동작이다.
 */
@Composable
private fun AutoStartRow(checked: Boolean, enabled: Boolean, onChange: (Boolean) -> Unit) {
    val cs = MaterialTheme.colorScheme
    val night = LocalGlassColors.current.night
    val view = LocalView.current
    val label = stringResource(R.string.label_auto_start)
    val track = when {
        checked && night -> Color(0xFF8C72F0)
        checked -> cs.primary
        night -> Color.White.copy(alpha = 0.24f)
        else -> cs.onSurface.copy(alpha = 0.25f)
    }
    Row(
        Modifier.fillMaxWidth().heightIn(min = 72.dp).alpha(if (enabled) 1f else 0.45f),
        horizontalArrangement = Arrangement.spacedBy(20.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(label, style = PleosType.BodyS, fontWeight = PleosType.Strong)
            Text(stringResource(R.string.auto_start_detail), style = PleosType.LabelS, color = cs.onSurfaceVariant)
        }
        Box(
            Modifier
                .size(width = 104.dp, height = 60.dp)
                .clip(RoundedCornerShape(percent = 50))
                .background(track)
                .toggleable(value = checked, enabled = enabled, role = Role.Switch) {
                    view.performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP)
                    onChange(it)
                }
                .semantics { contentDescription = label },
        ) {
            Box(Modifier.offset(x = if (checked) 50.dp else 6.dp, y = 6.dp).size(48.dp).clip(CircleShape).background(Color.White))
        }
    }
}

@Composable
private fun LockBanner(@StringRes text: Int) {
    val status = LocalStatusColors.current
    Row(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(percent = 50)).background(status.warnBg).padding(horizontal = 22.dp, vertical = 16.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        Icon(SoftIcons.Lock, contentDescription = null, modifier = Modifier.size(24.dp), tint = status.warnFg)
        Text(stringResource(text), style = PleosType.BodyS, fontWeight = PleosType.Strong, color = status.warnFg)
    }
}

/** 사운드트랙 켜기·끄기(높이 88, 모서리 28). 자동 재생 금지 규정 때문에 사용자가 직접 켜는 기본 조작이다. */
@Composable
fun SessionToggle(ui: UiState, onToggle: () -> Unit, modifier: Modifier = Modifier) {
    val cs = MaterialTheme.colorScheme
    val view = LocalView.current
    val label = when {
        ui.running -> R.string.session_stop
        ui.finished -> R.string.session_restart
        else -> R.string.session_start
    }
    Surface(
        onClick = {
            view.performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP)
            onToggle()
        },
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(28.dp),
        color = cs.primary,
        contentColor = cs.onPrimary,
    ) {
        Row(
            Modifier.height(88.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp, Alignment.CenterHorizontally),
        ) {
            Icon(if (ui.running) SoftIcons.Stop else SoftIcons.Play, contentDescription = null, modifier = Modifier.size(24.dp))
            Text(stringResource(label), style = PleosType.BodyS, fontWeight = PleosType.Strong)
        }
    }
}
