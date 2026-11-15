package com.routesoundtrack.domain

import com.routesoundtrack.domain.model.AudioCommand
import com.routesoundtrack.domain.model.Cue
import com.routesoundtrack.domain.model.CueKind
import com.routesoundtrack.domain.model.Density
import com.routesoundtrack.domain.model.DriveContext
import com.routesoundtrack.domain.model.DriveSnapshot
import com.routesoundtrack.domain.model.EngineEvent
import com.routesoundtrack.domain.model.Mood
import com.routesoundtrack.domain.model.RoutePlan

/**
 * 재생 여부를 정하는 유일한 곳.
 * 입력(스냅샷·맥락·이벤트)을 받아 상태를 바꾸고 AudioCommand 목록을 돌려준다.
 * 시간·SDK·오디오를 직접 건드리지 않으므로 같은 입력에는 항상 같은 출력이 나온다.
 */
class TriggerEngine(
    private var policy: Policy,           // var: 재생 중에 메인에서 다른 감정 원을 누르면 바꾼다(switchMood)
    private val planner: Planner,
    private val seed: Long,
    trackIdBase: Int = 0,                 // 사운드트랙을 다시 켜도 곡 id가 겹치지 않게 하는 시작값
) {
    var state: EngineState = EngineState.Idle
        private set                       // 밖에서는 읽기만 가능
    var ducked: Boolean = false
        private set
    var paused: Boolean = false           // 포커스를 잠깐 뺏겨 곡을 멈춰 둔 상태
        private set

    private var trackSeq = trackIdBase
    private var last: DriveSnapshot? = null
    private var arrivalStarted = false
    private var startNow = false          // 메인에서 감정 원을 눌러 켰다: 첫 곡을 기다리지 않고 바로 튼다

    /**
     * 사용자가 메인 화면에서 감정 원을 눌러 사운드트랙을 켰을 때 부른다.
     * 다음 틱에 첫 곡을 재생 지점까지 기다리지 않고 바로 튼다(고속도로 연기 규칙도 적용하지 않는다: 사용자가 직접 요청했으므로).
     * 경로가 아직 없으면 경로가 잡히는 첫 틱에 튼다.
     */
    fun requestStartNow() {
        startNow = true
    }

    /**
     * 사운드트랙이 켜진 채로 메인에서 다른 감정 원을 눌렀을 때: 곡 성격 규칙을 바꾸고 그 색의 곡을 바로 튼다.
     * 지금 곡이 나오고 있으면 짧게 줄여 끝내고, 다음 틱에 requestStartNow()와 같은 방식으로 새 곡을 튼다.
     * 이미 지난 재생 지점은 그대로 지난 것으로 둔다. 도착 곡이 나오는 중이거나 주행이 끝났으면 규칙만 바꾼다.
     */
    fun switchMood(next: Policy): List<AudioCommand> {
        policy = next
        return when (val st = state) {
            is EngineState.Playing -> {
                state = EngineState.Armed(st.plan, st.next)
                startNow = true
                ducked = false
                paused = false
                listOf(AudioCommand.FadeOut(SWITCH_FADE_MS))
            }
            is EngineState.Cooldown -> {
                state = EngineState.Armed(st.plan, st.next)
                startNow = true
                emptyList()
            }
            EngineState.Idle, is EngineState.Armed -> {
                startNow = true
                emptyList()
            }
            is EngineState.Arrival, is EngineState.Ended -> emptyList()
        }
    }

    /** 지금 소리를 내고 있는 곡의 id. 없으면 null. */
    val currentTrackId: Int?
        get() = when (val st = state) {
            is EngineState.Playing -> st.trackId
            is EngineState.Arrival -> if (st.playing) st.trackId else null
            else -> null
        }

    fun onTick(s: DriveSnapshot, ctx: DriveContext): List<AudioCommand> {
        last = s
        val st = state
        if (!s.routeActive) return leaveRoute(st)
        if (st is EngineState.Ended) return emptyList()
        if (st is EngineState.Arrival) {
            val rem = s.remainingKm
            return if (rem != null && rem < 0.1) end(st) else emptyList()
        }
        if (!arrivalStarted && st.canEnterArrival() && policy.isArrivalDue(s)) return enterArrival(st)

        val km = s.progressKm ?: return emptyList()   // ?: 왼쪽이 null이면 오른쪽 실행
        return when (st) {                             // when: 값을 돌려주는 switch
            EngineState.Idle -> {
                arm(s, km)
                val armed = state
                if (startNow && armed is EngineState.Armed) playNow(armed, km, ctx) else emptyList()
            }
            is EngineState.Armed -> if (startNow) playNow(st, km, ctx) else armedTick(st, km, ctx)
            is EngineState.Cooldown -> {
                if (policy.silenceDone(st.sinceKm, st.sinceMs, km, s.timeMs)) {
                    state = EngineState.Armed(st.plan, st.next)
                }
                emptyList()
            }
            else -> emptyList()                        // Playing은 이벤트(곡 종료)로만 바뀐다
        }
    }

    fun onEvent(e: EngineEvent): List<AudioCommand> = when (e) {
        is EngineEvent.TrackEnded -> trackFinished(e.trackId)
        is EngineEvent.Skipped -> trackFinished(e.trackId)
        EngineEvent.FocusDuck ->
            if (currentTrackId != null && !ducked) {
                ducked = true
                listOf(AudioCommand.Duck(policy.duckGain))
            } else emptyList()
        EngineEvent.FocusPaused ->
            if (currentTrackId != null && !paused) {
                paused = true
                listOf(AudioCommand.Pause)
            } else emptyList()
        EngineEvent.FocusRestore -> {
            val cmds = mutableListOf<AudioCommand>()
            if (paused) {
                paused = false
                cmds += AudioCommand.Resume
            }
            if (ducked) {
                ducked = false
                cmds += AudioCommand.Restore
            }
            cmds
        }
        EngineEvent.FocusLost -> {
            ducked = false
            paused = false
            state = EngineState.Ended(state.planOrNull())
            listOf(AudioCommand.Stop)
        }
    }

    private fun arm(s: DriveSnapshot, km: Double): List<AudioCommand> {
        val total = s.totalKm ?: return emptyList()
        if (policy.settings.density == Density.OFF) return emptyList()
        val plan = planner.plan(total, policy.settings, seed)
        // 이미 지난 큐는 건너뛴다 (경로 중간에 앱을 켠 경우).
        val next = plan.playable.indexOfFirst { it.atKm > km }.let { if (it < 0) plan.playable.size else it }
        state = EngineState.Armed(plan, next)
        return emptyList()
    }

    private fun armedTick(st: EngineState.Armed, km: Double, ctx: DriveContext): List<AudioCommand> {
        val playable = st.plan.playable
        var idx = st.next
        var due: Cue? = null
        while (idx < playable.size && km >= playable[idx].atKm) {   // 지나친 큐는 마지막 하나만 남긴다
            due = playable[idx]
            idx++
        }
        val cue = due ?: st.deferred ?: return emptyList()           // 새 큐가 연기 큐보다 우선
        if (!policy.allowsNow(ctx)) {
            state = st.copy(next = idx, deferred = cue)               // copy: 일부 필드만 바꾼 새 객체
            return emptyList()
        }
        return startTrack(st.plan, idx, cue, policy.moodFor(cue, ctx))
    }

    /**
     * requestStartNow()의 처리. 다음 큐가 시작 곡이면 그 곡을 지금으로 당겨 튼다(그래서 5 km에서 다시 나오지 않는다).
     * 경로 중간에 켜서 시작 곡이 이미 지났으면 지금 위치에 임시 큐를 만들어 튼다. 이후 곡은 평소처럼 재생 지점에서 나온다.
     */
    private fun playNow(st: EngineState.Armed, km: Double, ctx: DriveContext): List<AudioCommand> {
        startNow = false
        val first = st.plan.playable.getOrNull(st.next)
        return if (first != null && first.kind == CueKind.START) {
            startTrack(st.plan, st.next + 1, first, policy.moodFor(first, ctx))
        } else {
            val cue = Cue(NOW_CUE_ID, km, CueKind.START)
            startTrack(st.plan, st.next, cue, policy.moodFor(cue, ctx))
        }
    }

    private fun startTrack(plan: RoutePlan, next: Int, cue: Cue, mood: Mood): List<AudioCommand> {
        val id = ++trackSeq
        ducked = false
        paused = false
        state = EngineState.Playing(plan, next, cue, mood, id)
        return listOf(AudioCommand.Play(id, cue, mood, plan.seed + cue.id))
    }

    private fun trackFinished(id: Int): List<AudioCommand> {
        if (id != currentTrackId) return emptyList()   // 이미 교체된 곡의 종료 알림은 무시
        ducked = false
        paused = false
        when (val st = state) {
            is EngineState.Playing -> {
                val s = last
                state = EngineState.Cooldown(st.plan, st.next, s?.progressKm ?: st.cue.atKm, s?.timeMs ?: 0L)
            }
            is EngineState.Arrival -> state = st.copy(playing = false)
            else -> Unit
        }
        return emptyList()
    }

    private fun enterArrival(st: EngineState): List<AudioCommand> {
        val plan = st.planOrNull() ?: return emptyList()
        arrivalStarted = true
        val cue = plan.arrivalCue ?: Cue(-1, plan.totalKm, CueKind.ARRIVAL)
        val cmds = mutableListOf<AudioCommand>()
        if (st is EngineState.Playing) cmds += AudioCommand.FadeOut(3_000)
        val id = ++trackSeq
        ducked = false
        paused = false
        state = EngineState.Arrival(plan, playing = true, trackId = id)
        cmds += AudioCommand.Play(id, cue, Mood.ARRIVAL, plan.seed + cue.id)
        return cmds
    }

    private fun end(st: EngineState.Arrival): List<AudioCommand> {
        state = EngineState.Ended(st.plan)
        ducked = false
        paused = false
        return if (st.playing) listOf(AudioCommand.FadeOut(policy.fadeOutMs)) else emptyList()
    }

    private fun leaveRoute(st: EngineState): List<AudioCommand> = when (st) {
        EngineState.Idle, is EngineState.Ended -> emptyList()
        is EngineState.Arrival -> end(st)
        else -> {
            val wasPlaying = st is EngineState.Playing
            state = EngineState.Idle
            ducked = false
            paused = false
            if (wasPlaying) listOf(AudioCommand.FadeOut(1_000)) else emptyList()
        }
    }

    private fun EngineState.canEnterArrival(): Boolean =
        this is EngineState.Armed || this is EngineState.Playing || this is EngineState.Cooldown

    companion object {
        /** "바로 재생"용 임시 큐 id. 계획기의 큐 id(0부터)와 겹치지 않는다. */
        const val NOW_CUE_ID = -2

        /** 곡 성격을 바꿀 때 지금 곡을 줄이는 시간(ms). */
        const val SWITCH_FADE_MS = 800
    }
}
