package com.routesoundtrack.session

import android.content.Context
import android.util.Log
import com.routesoundtrack.audio.AudioFocusController.Companion.TAG
import com.routesoundtrack.audio.NowPlaying
import com.routesoundtrack.audio.PlaybackController
import com.routesoundtrack.data.DriveInput
import com.routesoundtrack.data.SettingsStore
import com.routesoundtrack.data.sdk.RouteInfo
import com.routesoundtrack.domain.ContextClassifier
import com.routesoundtrack.domain.EngineState
import com.routesoundtrack.domain.Policy
import com.routesoundtrack.domain.SegmentPlanner
import com.routesoundtrack.domain.TriggerEngine
import com.routesoundtrack.domain.model.AudioCommand
import com.routesoundtrack.domain.model.Cue
import com.routesoundtrack.domain.model.CueKind
import com.routesoundtrack.domain.model.Density
import com.routesoundtrack.domain.model.DriveContext
import com.routesoundtrack.domain.model.DriveSnapshot
import com.routesoundtrack.domain.model.EngineEvent
import com.routesoundtrack.domain.model.Mood
import com.routesoundtrack.domain.model.MoodMode
import com.routesoundtrack.domain.model.Road
import com.routesoundtrack.domain.model.RoutePlan
import com.routesoundtrack.domain.model.Settings
import com.routesoundtrack.domain.planOrNull
import java.util.concurrent.Executors
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.asCoroutineDispatcher
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

enum class Phase { READY, WAITING, DEFERRED, PLAYING, COOLDOWN, ARRIVAL, ARRIVAL_WAIT, ENDED, OFF }

data class PlayedWindow(val startKm: Double, val endKm: Double, val mood: Mood)
data class RoadSpan(val fromKm: Double, val toKm: Double, val road: Road)

/** 화면이 그리는 모든 값. */
data class UiState(
    val settings: Settings,
    val running: Boolean = false,
    val finished: Boolean = false,
    val destination: String = "",       // 빈 문자열 = 목적지 없음
    val totalKm: Double = 0.0,          // 0 = 경로 없음
    val progressKm: Double = 0.0,
    val etaMin: Int? = null,
    val speedKmh: Double? = null,
    val plan: RoutePlan? = null,
    val nextCue: Cue? = null,
    val nextCueInKm: Double? = null,
    val progressToNext: Float = 0f,
    val arrivalKm: Double? = null,
    val consumedCueIds: Set<Int> = emptySet(),
    val deferredCueId: Int? = null,
    val phase: Phase = Phase.READY,
    val road: Road? = null,
    val night: Boolean = false,
    val nowPlaying: NowPlaying? = null,
    val ducked: Boolean = false,
    val played: List<PlayedWindow> = emptyList(),
    val roadTrail: List<RoadSpan> = emptyList(),
    val uxRestricted: Boolean? = null,   // 차량의 주행 규제 상태. null = 알 수 없음
    val replanned: Boolean = false,      // 이번에 켤 때 지난번과 다른 설정으로 재생 지점을 다시 정했다
) {
    /**
     * 주행 중 조작 잠금. 차량이 주행 규제 상태(uxRestricted)를 알려 주면 그 값을 따르고,
     * 모르면(null) 추정 속도 5 km/h 초과로 판단한다.
     */
    val locked: Boolean get() = uxRestricted ?: (running && (speedKmh ?: 0.0) > 5.0)
    val remainingKm: Double get() = (totalKm - progressKm).coerceAtLeast(0.0)
    val hasRoute: Boolean get() = totalKm > 0.0
    /** "안내 시작 시 자동 시작"이 켜져 있고, 내비 경로 안내를 기다리는 중. */
    val armed: Boolean get() = settings.autoStart && !running
}

/**
 * 주행 세션: 입력(SDK) → 스냅샷 → 맥락 분류 → 트리거 엔진 → 오디오, 그리고 화면 상태.
 * 엔진과 세션 상태는 단일 스레드(dispatcher)에서만 바꾼다. 그래서 락이 필요 없다.
 */
class DriveSession(
    context: Context,
    private val scope: CoroutineScope,
    private val settingsStore: SettingsStore,
    private val input: DriveInput,
    private val uxRestricted: StateFlow<Boolean?>,
) {
    private val dispatcher = Executors.newSingleThreadExecutor { r -> Thread(r, "DriveSession") }.asCoroutineDispatcher()
    private val planner = SegmentPlanner()
    private val playback = PlaybackController(context, scope, onEvent = ::post, onNowPlaying = ::onNowPlaying)

    /** 미리보기용: 마지막으로 읽은 내비 경로. 목적지가 없으면 null. */
    private var route: RouteInfo? = null

    private val _ui = MutableStateFlow(previewState(settingsStore.load()))
    val ui: StateFlow<UiState> = _ui.asStateFlow()

    private var job: Job? = null
    private var engine: TriggerEngine? = null
    private var classifier: ContextClassifier? = null
    private var runCount = 0
    private var lastRunSettings: Settings? = null   // 지난번에 켤 때 쓴 설정(재생 지점이 바뀌었는지 알리기 위해)
    private var openTrackId: Int? = null
    private var arrivalStartKm: Double? = null
    private var hadRoute = false          // 이번 주행에서 경로를 본 적이 있는가
    private val played = mutableListOf<PlayedWindow>()
    private val trail = mutableListOf<RoadSpan>()

    // 내비 경로 감시. 자동 시작 스위치가 켜져 있을 때만 돈다.
    private val watcher = RouteWatcher(
        input::readRoute,
        onRouteStarted = { r ->
            showRoute(r)
            if (!_ui.value.running) { autoStarted = true; startRun() }
        },
        onRouteEnded = {
            showRoute(null)
            if (autoStarted && _ui.value.running) stopRun()
        },
    )
    private var watchJob: Job? = null
    private var autoStarted = false       // 이번 주행을 경로 감지로 시작했는가(버튼으로 켠 주행은 건드리지 않는다)

    init {
        scope.launch(dispatcher) { syncWatcher(_ui.value.settings) }
        // 주행 규제 상태가 바뀌면 화면 상태에 반영한다.
        scope.launch(dispatcher) {
            uxRestricted.collect { r -> _ui.update { it.copy(uxRestricted = r) } }
        }
        refreshRoute()
    }

    /** 스위치 상태에 맞춰 경로 감시를 켜거나 끈다. */
    private fun syncWatcher(settings: Settings) {
        if (settings.autoStart && watchJob == null) {
            watchJob = scope.launch(dispatcher) { watcher.watch() }
        } else if (!settings.autoStart) {
            watchJob?.cancel()
            watchJob = null
        }
    }

    fun updateSettings(transform: (Settings) -> Settings) {
        scope.launch(dispatcher) {
            val cur = _ui.value
            if (cur.running || cur.locked) return@launch   // 사운드트랙이 켜져 있거나 주행 규제 중에는 바꾸지 않는다
            val next = transform(cur.settings)
            settingsStore.save(next)
            _ui.value = previewState(next)
            syncWatcher(next)
        }
    }

    /** 꺼져 있을 때 내비 경로를 한 번 읽어 미리보기를 갱신한다. 앱 화면이 보이는 동안 주기적으로 부른다. */
    fun refreshRoute() {
        scope.launch(dispatcher) {
            if (_ui.value.running) return@launch
            showRoute(input.readRoute())
        }
    }

    /** 새로 읽은 경로로 미리보기를 다시 그린다. 주행이 끝난 화면("도착했어요")은 새 목적지가 생길 때까지 둔다. */
    private fun showRoute(r: RouteInfo?) {
        if (r == route) return
        route = r
        val cur = _ui.value
        if (cur.running || (cur.finished && r == null)) return
        _ui.value = previewState(cur.settings)
    }

    /** 시스템(미디어 버튼·미디어 센터)의 재생·정지 요청용. 화면 버튼은 toggle()을 쓴다. */
    fun start() {
        scope.launch(dispatcher) {
            if (!_ui.value.running) {
                autoStarted = false
                startRun()
            }
        }
    }

    fun stop() {
        scope.launch(dispatcher) { if (_ui.value.running) stopRun() }
    }

    fun toggle() {
        scope.launch(dispatcher) {
            if (_ui.value.running) stopRun() else { autoStarted = false; startRun() }
        }
    }

    /**
     * 메인 화면에서 감정 원을 눌렀을 때: 곡 성격을 그 색으로 정하고 사운드트랙을 켜서 첫 곡을 바로 튼다.
     * 누르는 것이 사용자의 명시적 동작이라 자동 재생 금지 규정에 맞는다.
     * 한 번 누르는 시작 동작이므로 주행 중 설정 잠금(updateSettings)과 달리 곡 성격도 함께 바꾼다.
     * 이미 켜져 있으면(메인 "재생 중" 상태에서 다른 원을 누름) 세션은 그대로 두고 곡 성격만 바꿔 새 곡을 바로 튼다.
     */
    fun startWithMood(mode: MoodMode) {
        scope.launch(dispatcher) {
            val cur = _ui.value
            if (cur.running && cur.settings.moodMode == mode) return@launch   // 이미 그 색으로 재생 중
            val next = cur.settings.copy(moodMode = mode)
            settingsStore.save(next)
            _ui.update { it.copy(settings = next) }
            val eng = engine
            if (cur.running && eng != null) {
                val cmds = eng.switchMood(Policy(next))
                Log.d(TAG, "switch mood -> $mode: $cmds")
                playback.submit(cmds)
                if (cmds.isNotEmpty()) _ui.update { it.copy(nowPlaying = null, ducked = eng.ducked) }   // 줄이는 곡은 지금 곡에서 뺀다
                return@launch
            }
            autoStarted = false
            startRun(playNow = true)
        }
    }

    private fun startRun(playNow: Boolean = false) {
        val settings = _ui.value.settings
        val replanned = lastRunSettings?.let { settings.movesCuesFrom(it) } ?: false
        lastRunSettings = settings
        val policy = Policy(settings)
        playback.masterGain = policy.volume
        playback.fadeOutMs = policy.fadeOutMs
        runCount++
        engine = TriggerEngine(policy, planner, seed = System.currentTimeMillis(), trackIdBase = runCount * 1_000).also {
            if (playNow) it.requestStartNow()   // also { }: 만든 객체로 한 가지 일을 더 하고 그 객체를 그대로 돌려준다
        }
        classifier = ContextClassifier()
        input.reset()
        hadRoute = false
        played.clear()
        trail.clear()
        openTrackId = null
        arrivalStartKm = null
        lastStateName = ""
        lastRoad = null
        tickCount = 0
        Log.d(TAG, "session start: $settings playNow=$playNow")
        _ui.value = previewState(settings).copy(running = true, phase = Phase.WAITING, replanned = replanned)
        job = scope.launch(dispatcher) { runLoop() }
    }

    private fun stopRun() {
        autoStarted = false
        job?.cancel()
        job = null
        engine = null
        playback.submit(listOf(AudioCommand.Stop))
        _ui.value = previewState(_ui.value.settings)
    }

    private suspend fun runLoop() {
        val eng = engine ?: return
        val cls = classifier ?: return
        while (currentCoroutineContext().isActive) {
            delay(input.tickMs)
            val snap = input.read(playing = eng.currentTrackId != null)
            if (snap.routeActive) hadRoute = true

            val ctx = cls.update(snap)
            val cmds = eng.onTick(snap, ctx)
            logTick(eng, snap, ctx, cmds)
            playback.submit(cmds)
            publish(eng, snap, ctx)

            // 도착했거나, 경로를 따라가다가 경로 안내가 끝나면(취소 포함) 세션을 마친다.
            val routeGone = hadRoute && !snap.routeActive && eng.state == EngineState.Idle
            if (eng.state is EngineState.Ended || routeGone) {
                _ui.update { it.copy(running = false, finished = true, phase = Phase.ENDED, speedKmh = 0.0) }
                break
            }
        }
    }

    /** 디버그 로그(태그 RouteSoundtrack): 엔진 상태가 바뀔 때, 명령이 나갈 때, 그리고 10틱마다 스냅샷. */
    private var lastStateName = ""
    private var lastRoad: Road? = null
    private var tickCount = 0

    private fun logTick(eng: TriggerEngine, snap: DriveSnapshot, ctx: DriveContext, cmds: List<AudioCommand>) {
        val km = "%.2f km".format(snap.progressKm ?: 0.0)
        val name = eng.state::class.simpleName ?: "?"
        if (name != lastStateName) {
            Log.d(TAG, "state $lastStateName -> $name at $km (remain ${snap.remainingKm} km, eta ${snap.etaSec} s)")
            lastStateName = name
        }
        if (ctx.road != lastRoad) {
            Log.d(TAG, "road ${lastRoad} -> ${ctx.road} at $km")
            lastRoad = ctx.road
        }
        if (cmds.isNotEmpty()) Log.d(TAG, "commands $cmds at $km")
        if (tickCount++ % 10 == 0) Log.d(TAG, "snapshot $snap")
    }

    /** 오디오·포커스 쪽에서 온 이벤트를 엔진 스레드로 넘긴다. */
    private fun post(e: EngineEvent) {
        scope.launch(dispatcher) {
            val eng = engine ?: return@launch
            val cmds = eng.onEvent(e)
            Log.d(TAG, "event $e -> $cmds (state ${eng.state::class.simpleName})")
            playback.submit(cmds)
            val endedId = when (e) {
                is EngineEvent.TrackEnded -> e.trackId
                is EngineEvent.Skipped -> e.trackId
                else -> null
            }
            _ui.update { s ->
                val clear = e is EngineEvent.FocusLost || (endedId != null && s.nowPlaying?.trackId == endedId)
                s.copy(nowPlaying = if (clear) null else s.nowPlaying, ducked = eng.ducked)
            }
        }
    }

    private fun onNowPlaying(np: NowPlaying) {
        scope.launch(dispatcher) {
            if (engine?.currentTrackId == np.trackId) _ui.update { it.copy(nowPlaying = np) }
        }
    }

    private fun publish(eng: TriggerEngine, snap: DriveSnapshot, ctx: DriveContext) {
        val km = snap.progressKm ?: 0.0
        recordTrail(km, ctx.road)
        recordPlayed(eng, km)

        val st = eng.state
        val plan = st.planOrNull()
        var next: Cue? = null
        var deferred: Cue? = null
        val consumedUpTo = when (st) {
            is EngineState.Armed -> { next = st.plan.playable.getOrNull(st.next); deferred = st.deferred; st.next }
            is EngineState.Playing -> { next = st.plan.playable.getOrNull(st.next); st.next }
            is EngineState.Cooldown -> { next = st.plan.playable.getOrNull(st.next); st.next }
            else -> plan?.playable?.size ?: 0
        }
        val beforeArrival = st is EngineState.Armed || st is EngineState.Playing || st is EngineState.Cooldown
        val arrivalKm = arrivalEstimate(st, snap, km)
        val target = next ?: plan?.arrivalCue?.takeIf { beforeArrival }
        val targetKm = when {
            target == null -> null
            target.kind == CueKind.ARRIVAL -> arrivalKm
            else -> target.atKm
        }
        val prevKm = plan?.playable?.take(consumedUpTo)?.lastOrNull()?.atKm ?: 0.0
        val toNext = targetKm?.let { t ->
            if (t <= prevKm) 1f else ((km - prevKm) / (t - prevKm)).coerceIn(0.0, 1.0).toFloat()
        } ?: 1f
        val consumed = plan?.playable?.take(consumedUpTo)?.map { it.id }?.toMutableSet() ?: mutableSetOf()
        if (!beforeArrival) plan?.arrivalCue?.let { consumed += it.id }
        deferred?.let { consumed -= it.id }

        val phase = when (st) {
            EngineState.Idle -> if (_ui.value.settings.density == Density.OFF) Phase.OFF else Phase.WAITING
            is EngineState.Armed -> if (deferred != null) Phase.DEFERRED else Phase.WAITING
            is EngineState.Playing -> Phase.PLAYING
            is EngineState.Cooldown -> Phase.COOLDOWN
            is EngineState.Arrival -> if (st.playing) Phase.ARRIVAL else Phase.ARRIVAL_WAIT
            is EngineState.Ended -> Phase.ENDED
        }

        _ui.update {
            it.copy(
                running = true,
                destination = snap.destination ?: it.destination,
                totalKm = snap.totalKm ?: it.totalKm,
                progressKm = km,
                etaMin = snap.etaSec?.let { s -> (s + 59) / 60 } ?: it.etaMin,
                speedKmh = snap.speedKmh,
                plan = plan ?: it.plan,
                nextCue = target,
                nextCueInKm = targetKm?.let { t -> (t - km).coerceAtLeast(0.0) },
                progressToNext = toNext,
                arrivalKm = arrivalKm,
                consumedCueIds = consumed,
                deferredCueId = deferred?.id,
                phase = phase,
                road = ctx.road,
                night = ctx.night,
                ducked = eng.ducked,
                played = played.toList(),
                roadTrail = trail.toList(),
            )
        }
    }

    /** 도착 곡 위치 추정: 시작했으면 시작 지점, 아니면 지금 ETA로 "ETA 8분" 지점을 계산한다. */
    private fun arrivalEstimate(st: EngineState, snap: DriveSnapshot, km: Double): Double? {
        arrivalStartKm?.let { return it }
        val plan = st.planOrNull() ?: return _ui.value.plan?.arrivalCue?.atKm
        val eta = snap.etaSec
        val remaining = snap.remainingKm
        if (eta != null && remaining != null && eta > ARRIVAL_ETA_SEC) {
            return km + remaining * (eta - ARRIVAL_ETA_SEC) / eta
        }
        return plan.arrivalCue?.atKm
    }

    private fun recordPlayed(eng: TriggerEngine, km: Double) {
        val st = eng.state
        if (st is EngineState.Arrival && arrivalStartKm == null) arrivalStartKm = km
        val id = eng.currentTrackId
        if (id == null) {
            openTrackId = null
            return
        }
        val mood = when (st) {
            is EngineState.Playing -> st.mood
            is EngineState.Arrival -> Mood.ARRIVAL
            else -> Mood.CHILL
        }
        if (openTrackId != id) {
            played += PlayedWindow(km, km, mood)
            openTrackId = id
        } else {
            played[played.lastIndex] = played.last().copy(endKm = km)
        }
    }

    private fun recordTrail(km: Double, road: Road?) {
        val last = trail.lastOrNull()
        val r = road ?: last?.road ?: Road.CITY
        when {
            last == null -> trail += RoadSpan(0.0, km, r)
            last.road == r -> trail[trail.lastIndex] = last.copy(toKm = km)
            else -> trail += RoadSpan(last.toKm, km, r)
        }
    }

    /** 꺼져 있을 때의 화면: 마지막으로 읽은 내비 경로로 재생 지점을 미리 보여 준다. 경로가 없으면 빈 화면. */
    private fun previewState(settings: Settings): UiState {
        val r = route
        val totalKm = r?.remainingKm ?: 0.0
        val plan = if (totalKm > 0.0) planner.plan(totalKm, settings, 0L) else null
        val first = plan?.playable?.firstOrNull() ?: plan?.arrivalCue
        return UiState(
            settings = settings,
            destination = r?.destinationName.orEmpty(),
            totalKm = totalKm,
            etaMin = r?.etaSec?.let { (it + 59) / 60 },
            plan = plan,
            nextCue = first,
            nextCueInKm = first?.atKm,
            arrivalKm = plan?.arrivalCue?.atKm,
            phase = if (settings.density == Density.OFF) Phase.OFF else Phase.READY,
            uxRestricted = uxRestricted.value,
        )
    }

    companion object {
        private const val ARRIVAL_ETA_SEC = 480
    }
}
