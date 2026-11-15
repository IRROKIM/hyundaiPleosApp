package com.routesoundtrack.domain

import com.routesoundtrack.domain.model.AudioCommand
import com.routesoundtrack.domain.model.Cue
import com.routesoundtrack.domain.model.CueKind
import com.routesoundtrack.domain.model.Density
import com.routesoundtrack.domain.model.DriveContext
import com.routesoundtrack.domain.model.DriveSnapshot
import com.routesoundtrack.domain.model.EngineEvent
import com.routesoundtrack.domain.model.Mood
import com.routesoundtrack.domain.model.MoodMode
import com.routesoundtrack.domain.model.Preset
import com.routesoundtrack.domain.model.Road
import com.routesoundtrack.domain.model.RoutePlan
import com.routesoundtrack.domain.model.Settings
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class TriggerEngineTest {

    /** 큐를 직접 정해 주는 가짜 계획기. fun interface라 람다로 만든다. */
    private fun fixedPlanner(vararg cues: Cue) = Planner { total, _, seed ->
        RoutePlan(total, cues.toList() + Cue(99, total - 10, CueKind.ARRIVAL), seed)
    }

    private fun engine(preset: Preset = Preset.TRIP, planner: Planner) =
        TriggerEngine(Policy(Settings(preset = preset, density = Density.FREQUENT)), planner, seed = 0L)

    private fun snap(km: Double, eta: Int? = null, total: Double = 50.0) = DriveSnapshot(
        timeMs = (km * 60_000).toLong(),            // 1 km/분으로 가정
        routeActive = true,
        totalKm = total,
        progressKm = km,
        remainingKm = total - km,
        etaSec = eta,
        speedKmh = 60.0,
    )

    private fun ctx(road: Road) = DriveContext(road, night = false)

    private fun List<AudioCommand>.plays() = filterIsInstance<AudioCommand.Play>()

    @Test
    fun `TC-01 큐 위치에서 한 번만 재생하고 곡이 끝나면 무음 구간으로 간다`() {
        val e = engine(planner = fixedPlanner(Cue(0, 5.0, CueKind.START)))
        e.onTick(snap(0.0), ctx(Road.CITY))                               // IDLE → ARMED
        assertTrue(e.onTick(snap(4.9), ctx(Road.CITY)).isEmpty())
        val at5 = e.onTick(snap(5.0), ctx(Road.CITY)).plays()
        assertEquals(1, at5.size)
        assertEquals(Mood.CHILL, at5.first().mood)
        assertTrue(e.onTick(snap(5.1), ctx(Road.CITY)).plays().isEmpty())
        e.onEvent(EngineEvent.TrackEnded(at5.first().trackId))
        assertTrue(e.state is EngineState.Cooldown)
    }

    @Test
    fun `TC-02 안내 음성이 나오면 음악을 줄이고 끝나면 복귀한다`() {
        val e = engine(planner = fixedPlanner(Cue(0, 5.0, CueKind.START)))
        e.onTick(snap(0.0), ctx(Road.CITY))
        e.onTick(snap(5.0), ctx(Road.CITY))
        assertEquals(listOf(AudioCommand.Duck(0.3f)), e.onEvent(EngineEvent.FocusDuck))
        assertEquals(listOf(AudioCommand.Restore), e.onEvent(EngineEvent.FocusRestore))
        assertTrue(e.state is EngineState.Playing)
    }

    @Test
    fun `TC-03 여행 프리셋은 고속 구간에서 큐를 미루고 벗어나면 재생한다`() {
        val e = engine(planner = fixedPlanner(Cue(1, 20.0, CueKind.MID)))
        e.onTick(snap(0.0), ctx(Road.CITY))
        assertTrue(e.onTick(snap(20.1), ctx(Road.HIGHWAY)).plays().isEmpty())
        assertTrue(e.onTick(snap(30.0), ctx(Road.HIGHWAY)).plays().isEmpty())
        val afterExit = e.onTick(snap(36.4), ctx(Road.JAM)).plays()
        assertEquals(1, afterExit.size)
        assertEquals(Mood.CHILL, afterExit.first().mood)
    }

    @Test
    fun `장거리 프리셋은 고속 구간에서 upbeat으로 재생한다`() {
        val e = engine(Preset.LONG_HAUL, fixedPlanner(Cue(1, 27.5, CueKind.MID)))
        e.onTick(snap(0.0), ctx(Road.CITY))
        val plays = e.onTick(snap(27.6), ctx(Road.HIGHWAY)).plays()
        assertEquals(1, plays.size)
        assertEquals(Mood.UPBEAT, plays.first().mood)
    }

    @Test
    fun `TC-06 ETA 8분 이하가 되면 도착 곡으로 바꾸고 도착하면 끝낸다`() {
        val e = engine(planner = fixedPlanner(Cue(1, 20.0, CueKind.MID)))
        e.onTick(snap(0.0), ctx(Road.CITY))
        val mid = e.onTick(snap(40.0, eta = 481), ctx(Road.CITY))
        assertTrue(mid.plays().none { it.mood == Mood.ARRIVAL })
        val arrival = e.onTick(snap(41.0, eta = 479), ctx(Road.CITY))
        assertTrue(arrival.first() is AudioCommand.FadeOut)                 // 재생 중이던 곡은 페이드아웃
        assertEquals(Mood.ARRIVAL, arrival.plays().single().mood)
        assertTrue(e.state is EngineState.Arrival)
        val end = e.onTick(snap(50.0, eta = 0), ctx(Road.CITY))
        assertTrue(end.single() is AudioCommand.FadeOut)
        assertTrue(e.state is EngineState.Ended)
    }

    @Test
    fun `포커스를 잠깐 뺏기면 곡을 멈췄다가 돌려받으면 이어서 재생한다`() {
        val e = engine(planner = fixedPlanner(Cue(0, 5.0, CueKind.START)))
        e.onTick(snap(0.0), ctx(Road.CITY))
        e.onTick(snap(5.0), ctx(Road.CITY))
        assertEquals(listOf(AudioCommand.Pause), e.onEvent(EngineEvent.FocusPaused))
        assertTrue(e.onEvent(EngineEvent.FocusPaused).isEmpty())          // 중복 이벤트는 무시
        assertEquals(listOf(AudioCommand.Resume), e.onEvent(EngineEvent.FocusRestore))
        assertTrue(e.state is EngineState.Playing)
    }

    @Test
    fun `곡이 없을 때 포커스를 뺏겨도 아무 명령도 내지 않는다`() {
        val e = engine(planner = fixedPlanner(Cue(0, 5.0, CueKind.START)))
        e.onTick(snap(0.0), ctx(Road.CITY))
        assertTrue(e.onEvent(EngineEvent.FocusPaused).isEmpty())
    }

    // ---- 메인 화면에서 감정 원을 눌러 켠 경우 (requestStartNow) ----

    @Test
    fun `원을 눌러 켜면 시작 곡을 기다리지 않고 바로 틀고 5 km에서 다시 틀지 않는다`() {
        val e = engine(planner = fixedPlanner(Cue(0, 5.0, CueKind.START), Cue(1, 35.0, CueKind.MID)))
        e.requestStartNow()
        val now = e.onTick(snap(0.0), ctx(Road.CITY)).plays()
        assertEquals(0, now.single().cue.id)                                // 시작 곡을 0 km로 당겨 틀었다
        e.onEvent(EngineEvent.TrackEnded(now.single().trackId))
        assertTrue(e.onTick(snap(5.0), ctx(Road.CITY)).plays().isEmpty())   // 무음 구간이 끝나는 틱
        assertTrue(e.onTick(snap(5.1), ctx(Road.CITY)).plays().isEmpty())   // 시작 곡은 이미 썼다
        assertEquals(1, e.onTick(snap(35.0), ctx(Road.CITY)).plays().single().cue.id)
    }

    @Test
    fun `경로가 아직 없으면 경로가 잡히는 첫 틱에 바로 튼다`() {
        val e = engine(planner = fixedPlanner(Cue(0, 5.0, CueKind.START)))
        e.requestStartNow()
        val noRoute = DriveSnapshot(timeMs = 0, routeActive = false, totalKm = null, progressKm = null, remainingKm = null, etaSec = null, speedKmh = null)
        assertTrue(e.onTick(noRoute, ctx(Road.CITY)).isEmpty())
        assertEquals(1, e.onTick(snap(0.0), ctx(Road.CITY)).plays().size)
    }

    @Test
    fun `원을 눌러 켠 곡은 고속도로에서도 미루지 않고 고른 곡 성격으로 튼다`() {
        val policy = Policy(Settings(preset = Preset.TRIP, density = Density.FREQUENT, moodMode = MoodMode.GROOVE))
        val e = TriggerEngine(policy, fixedPlanner(Cue(0, 5.0, CueKind.START)), seed = 0L)
        e.requestStartNow()
        assertEquals(Mood.GROOVE, e.onTick(snap(0.0), ctx(Road.HIGHWAY)).plays().single().mood)
    }

    @Test
    fun `경로 중간에 켜서 시작 곡이 지났으면 지금 위치에서 틀고 다음 큐는 그대로 둔다`() {
        val e = engine(planner = fixedPlanner(Cue(0, 5.0, CueKind.START), Cue(1, 35.0, CueKind.MID)))
        e.requestStartNow()
        val now = e.onTick(snap(20.0), ctx(Road.CITY)).plays().single()
        assertEquals(TriggerEngine.NOW_CUE_ID, now.cue.id)
        assertEquals(20.0, now.cue.atKm, 0.0)
        e.onEvent(EngineEvent.TrackEnded(now.trackId))
        e.onTick(snap(25.0), ctx(Road.CITY))                                // 무음 구간 끝
        assertEquals(1, e.onTick(snap(35.0), ctx(Road.CITY)).plays().single().cue.id)
    }

    // ---- 재생 중에 다른 감정 원을 누른 경우 (switchMood) ----

    private fun moodPolicy(m: MoodMode) = Policy(Settings(preset = Preset.TRIP, density = Density.FREQUENT, moodMode = m))

    @Test
    fun `재생 중에 다른 원을 누르면 지금 곡을 줄이고 다음 틱에 새 곡 성격으로 튼다`() {
        val e = TriggerEngine(moodPolicy(MoodMode.UPBEAT), fixedPlanner(Cue(0, 5.0, CueKind.START), Cue(1, 35.0, CueKind.MID)), seed = 0L)
        e.requestStartNow()
        val first = e.onTick(snap(0.0), ctx(Road.CITY)).plays().single()
        assertEquals(Mood.UPBEAT, first.mood)

        val cmds = e.switchMood(moodPolicy(MoodMode.CHILL))
        assertTrue(cmds.single() is AudioCommand.FadeOut)
        assertEquals(null, e.currentTrackId)                              // 줄이는 곡의 종료 알림은 무시된다
        e.onEvent(EngineEvent.TrackEnded(first.trackId))

        val next = e.onTick(snap(0.5), ctx(Road.CITY)).plays().single()
        assertEquals(Mood.CHILL, next.mood)
        e.onEvent(EngineEvent.TrackEnded(next.trackId))
        e.onTick(snap(4.0), ctx(Road.CITY))                                // 무음 구간 끝
        assertEquals(1, e.onTick(snap(35.0), ctx(Road.CITY)).plays().single().cue.id)   // 시작 곡은 다시 나오지 않는다
    }

    @Test
    fun `곡이 없는 무음 구간에서 원을 누르면 바로 새 곡을 튼다`() {
        val e = TriggerEngine(moodPolicy(MoodMode.UPBEAT), fixedPlanner(Cue(0, 5.0, CueKind.START), Cue(1, 35.0, CueKind.MID)), seed = 0L)
        e.onTick(snap(0.0), ctx(Road.CITY))                                // 경로를 받아 재생 지점을 정한다
        val first = e.onTick(snap(5.0), ctx(Road.CITY)).plays().single()
        e.onEvent(EngineEvent.TrackEnded(first.trackId))                   // 무음 구간(Cooldown)
        assertTrue(e.switchMood(moodPolicy(MoodMode.HYPE)).isEmpty())
        assertEquals(Mood.HYPE, e.onTick(snap(6.0), ctx(Road.CITY)).plays().single().mood)
    }
}
