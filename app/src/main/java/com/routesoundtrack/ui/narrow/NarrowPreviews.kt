package com.routesoundtrack.ui.narrow

import androidx.compose.foundation.layout.Box
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import com.routesoundtrack.audio.NowPlaying
import com.routesoundtrack.domain.model.Cue
import com.routesoundtrack.domain.model.CueKind
import com.routesoundtrack.domain.model.Mood
import com.routesoundtrack.domain.model.MoodMode
import com.routesoundtrack.domain.model.Road
import com.routesoundtrack.domain.model.RoutePlan
import com.routesoundtrack.domain.model.Settings
import com.routesoundtrack.session.Phase
import com.routesoundtrack.session.PlayedWindow
import com.routesoundtrack.session.UiState
import com.routesoundtrack.ui.theme.LocalGlassColors
import com.routesoundtrack.ui.theme.RouteSoundtrackTheme
import com.routesoundtrack.ui.wide.WideDrive
import com.routesoundtrack.ui.wide.WideHome
import com.routesoundtrack.ui.wide.WideTracks

/*
 * Android Studio 미리보기 (Split/Design 탭). 에뮬레이터는 앱 창이 1개라 세로 창을 띄울 수 없어서,
 * Display Layout 문서의 세로 창 크기로 여기서 확인한다: 840×1184(17″) · 628×855(12.9″) · 800×810(Wide).
 * 예시 값은 디자인 시안과 같다: 강릉 경포해변, 120 km, 재생 지점 5·35·65·95 km + 도착 곡.
 */

private val samplePlan = RoutePlan(
    totalKm = 120.0,
    cues = listOf(
        Cue(0, 5.0, CueKind.START),
        Cue(1, 35.0, CueKind.MID),
        Cue(2, 65.0, CueKind.MID),
        Cue(3, 95.0, CueKind.MID),
        Cue(4, 111.0, CueKind.ARRIVAL),
    ),
    seed = 0L,
)

private fun stopped(mode: MoodMode = MoodMode.AUTO) = UiState(
    settings = Settings(moodMode = mode),
    destination = "강릉 경포해변",
    totalKm = 120.0,
    etaMin = 100,
    plan = samplePlan,
    nextCue = samplePlan.cues[0],
    nextCueInKm = 5.0,
    arrivalKm = 111.0,
    phase = Phase.READY,
)

private fun driving(ducked: Boolean = false) = UiState(
    settings = Settings(moodMode = MoodMode.UPBEAT),
    running = true,
    destination = "강릉 경포해변",
    totalKm = 120.0,
    progressKm = 36.2,
    etaMin = 70,
    speedKmh = 62.0,
    plan = samplePlan,
    nextCue = samplePlan.cues[2],
    nextCueInKm = 28.8,
    progressToNext = 0.04f,
    arrivalKm = 111.0,
    consumedCueIds = setOf(0, 1),
    phase = Phase.PLAYING,
    road = Road.CITY,
    nowPlaying = NowPlaying(trackId = 1001, mood = Mood.UPBEAT, title = "synthwave #07", durationSec = 102.0),
    ducked = ducked,
)

/** 원을 눌렀지만 내비에 목적지가 아직 없음. */
private fun waiting() = UiState(settings = Settings(moodMode = MoodMode.UPBEAT), running = true, phase = Phase.WAITING)

/** 고속도로라 65 km 곡을 미뤄 둔 상태. */
private fun deferred() = driving().copy(
    progressKm = 70.4, nextCue = samplePlan.cues[3], nextCueInKm = 24.6, deferredCueId = 2,
    phase = Phase.DEFERRED, road = Road.HIGHWAY, nowPlaying = null, etaMin = 42,
)

/** 도착 8분 전부터 도착 곡 재생 중. */
private fun arrival() = driving().copy(
    progressKm = 113.0, nextCue = samplePlan.cues[4], nextCueInKm = 0.0, consumedCueIds = setOf(0, 1, 2, 3),
    phase = Phase.ARRIVAL, road = Road.JAM, etaMin = 8,
    nowPlaying = NowPlaying(trackId = 1005, mood = Mood.ARRIVAL, title = "arrival #04", durationSec = 110.0),
)

/** 끝까지 달려 도착: 들은 곡 5개. */
private fun finished() = stopped().copy(
    finished = true, phase = Phase.ENDED, progressKm = 120.0,
    played = listOf(
        PlayedWindow(5.0, 7.4, Mood.GROOVE),
        PlayedWindow(35.0, 37.8, Mood.UPBEAT),
        PlayedWindow(72.0, 74.6, Mood.HYPE),
        PlayedWindow(95.0, 96.4, Mood.CHILL),
        PlayedWindow(111.0, 120.0, Mood.ARRIVAL),
    ),
)

@Composable
private fun Frame(dark: Boolean, content: @Composable () -> Unit) {
    RouteSoundtrackTheme(dark = dark) {
        Surface(color = MaterialTheme.colorScheme.background) {
            Box(Modifier.screenBackdrop(LocalGlassColors.current)) { content() }   // 앱과 같은 바탕(MainScreen)
        }
    }
}

@Preview(name = "메인 · 840×1184 · 주간", widthDp = 840, heightDp = 1184)
@Composable
private fun MoodHome840() = Frame(dark = false) { MoodHome(stopped(), onPick = {}, onStop = {}, onOpenDrive = {}, onOpenSettings = {}, onOpenTracks = {}) }

@Preview(name = "메인 · 628×855 · 주간", widthDp = 628, heightDp = 855)
@Composable
private fun MoodHome628() = Frame(dark = false) { MoodHome(stopped(), onPick = {}, onStop = {}, onOpenDrive = {}, onOpenSettings = {}, onOpenTracks = {}) }

@Preview(name = "메인 · 800×810 · 야간", widthDp = 800, heightDp = 810)
@Composable
private fun MoodHome800Night() = Frame(dark = true) { MoodHome(stopped(), onPick = {}, onStop = {}, onOpenDrive = {}, onOpenSettings = {}, onOpenTracks = {}) }

@Preview(name = "주행 · 840×1184 · 주간", widthDp = 840, heightDp = 1184)
@Composable
private fun Drive840() = Frame(dark = false) { DrivePortrait(driving(), onStop = {}) }

@Preview(name = "주행 · 840×1184 · 야간 · 안내 음성", widthDp = 840, heightDp = 1184)
@Composable
private fun Drive840NightDucked() = Frame(dark = true) { DrivePortrait(driving(ducked = true), onStop = {}) }

@Preview(name = "주행 · 628×855 · 주간", widthDp = 628, heightDp = 855)
@Composable
private fun Drive628() = Frame(dark = false) { DrivePortrait(driving(), onStop = {}) }

@Preview(name = "주행 · 800×810 · 야간", widthDp = 800, heightDp = 810)
@Composable
private fun Drive800Night() = Frame(dark = true) { DrivePortrait(driving(), onStop = {}) }

@Preview(name = "주행 상태 · 경로 대기", widthDp = 840, heightDp = 1184)
@Composable
private fun DriveWaiting() = Frame(dark = false) { DrivePortrait(waiting(), onStop = {}) }

@Preview(name = "주행 상태 · 고속 구간 대기 · 야간", widthDp = 840, heightDp = 1184)
@Composable
private fun DriveDeferred() = Frame(dark = true) { DrivePortrait(deferred(), onStop = {}) }

@Preview(name = "주행 상태 · 도착 곡", widthDp = 840, heightDp = 1184)
@Composable
private fun DriveArrival() = Frame(dark = false) { DrivePortrait(arrival(), onStop = {}) }

@Preview(name = "곡 목록 · 자동 · 주간", widthDp = 840, heightDp = 1184)
@Composable
private fun Tracks840() = Frame(dark = false) { NarrowTrackList(stopped(), onBack = {}) }

@Preview(name = "곡 목록 · 설렘 고정 · 야간 · 628×855", widthDp = 628, heightDp = 855)
@Composable
private fun Tracks628Night() = Frame(dark = true) { NarrowTrackList(stopped(MoodMode.UPBEAT), onBack = {}) }

@Preview(name = "도착 요약 · 주간", widthDp = 840, heightDp = 1184)
@Composable
private fun Summary840() = Frame(dark = false) { ArrivalSummary(finished(), onDone = {}) }

@Preview(name = "도착 요약 · 야간 · 628×855", widthDp = 628, heightDp = 855)
@Composable
private fun Summary628Night() = Frame(dark = true) { ArrivalSummary(finished(), onDone = {}) }

// ---- 메인 "재생 중" (정차 중에 원을 누른 뒤) ----

/** 정차 중에 설레요 원을 눌러 첫 곡이 나오는 중. */
private fun parkedPlaying() = driving().copy(speedKmh = 0.0, progressKm = 0.4, road = null, consumedCueIds = setOf(0), nextCue = samplePlan.cues[1], nextCueInKm = 34.6)

@Preview(name = "메인 재생 중 · 840×1184 · 주간", widthDp = 840, heightDp = 1184)
@Composable
private fun MoodHomePlaying840() = Frame(dark = false) { MoodHome(parkedPlaying(), onPick = {}, onStop = {}, onOpenDrive = {}, onOpenSettings = {}, onOpenTracks = {}) }

@Preview(name = "메인 재생 중 · 840×1184 · 야간", widthDp = 840, heightDp = 1184)
@Composable
private fun MoodHomePlaying840Night() = Frame(dark = true) { MoodHome(parkedPlaying(), onPick = {}, onStop = {}, onOpenDrive = {}, onOpenSettings = {}, onOpenTracks = {}) }

@Preview(name = "주행 · 정차 중 직접 열기(메인 버튼)", widthDp = 840, heightDp = 1184)
@Composable
private fun DriveOpened840() = Frame(dark = false) { DrivePortrait(parkedPlaying(), onStop = {}, onBack = {}) }

// ---- 가로(앱 창 1개) 1682×1210 ----

@Preview(name = "가로 메인 · 주간", widthDp = 1682, heightDp = 1210)
@Composable
private fun WideHomeDay() = Frame(dark = false) { WideHome(stopped(), onPick = {}, onStop = {}, onOpenDrive = {}, onOpenSettings = {}, onOpenTracks = {}) }

@Preview(name = "가로 메인 재생 중 · 야간", widthDp = 1682, heightDp = 1210)
@Composable
private fun WideHomePlayingNight() = Frame(dark = true) { WideHome(parkedPlaying(), onPick = {}, onStop = {}, onOpenDrive = {}, onOpenSettings = {}, onOpenTracks = {}) }

@Preview(name = "가로 주행 · 주간", widthDp = 1682, heightDp = 1210)
@Composable
private fun WideDriveDay() = Frame(dark = false) { WideDrive(driving(), onStop = {}) }

@Preview(name = "가로 주행 · 야간 · 안내 음성", widthDp = 1682, heightDp = 1210)
@Composable
private fun WideDriveNightDucked() = Frame(dark = true) { WideDrive(driving(ducked = true), onStop = {}) }

@Preview(name = "가로 곡 목록 · 설렘 고정 · 주간", widthDp = 1682, heightDp = 1210)
@Composable
private fun WideTracksDay() = Frame(dark = false) { WideTracks(stopped(MoodMode.UPBEAT), onBack = {}) }

@Preview(name = "가로 곡 목록 · 자동 · 야간", widthDp = 1682, heightDp = 1210)
@Composable
private fun WideTracksNight() = Frame(dark = true) { WideTracks(stopped(), onBack = {}) }
