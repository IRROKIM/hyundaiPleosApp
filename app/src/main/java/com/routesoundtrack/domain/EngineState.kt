package com.routesoundtrack.domain

import com.routesoundtrack.domain.model.Cue
import com.routesoundtrack.domain.model.Mood
import com.routesoundtrack.domain.model.RoutePlan

/** 트리거 엔진 상태 (상태 머신). */
sealed interface EngineState {
    /** 목적지 없음, 또는 음악 끄기. */
    data object Idle : EngineState

    /** 다음 큐를 기다리는 무음 상태. deferred = 고속도로라서 미뤄 둔 큐. */
    data class Armed(val plan: RoutePlan, val next: Int, val deferred: Cue? = null) : EngineState

    /** 시작·중간 곡 재생 중. */
    data class Playing(val plan: RoutePlan, val next: Int, val cue: Cue, val mood: Mood, val trackId: Int) : EngineState

    /** 곡이 끝난 뒤 최소 무음 구간. */
    data class Cooldown(val plan: RoutePlan, val next: Int, val sinceKm: Double, val sinceMs: Long) : EngineState

    /** 도착 곡. playing = false 이면 곡은 끝났고 도착을 기다리는 중. */
    data class Arrival(val plan: RoutePlan, val playing: Boolean, val trackId: Int) : EngineState

    /** 이번 주행 종료. */
    data class Ended(val plan: RoutePlan?) : EngineState
}

fun EngineState.planOrNull(): RoutePlan? = when (this) {
    EngineState.Idle -> null
    is EngineState.Armed -> plan
    is EngineState.Playing -> plan
    is EngineState.Cooldown -> plan
    is EngineState.Arrival -> plan
    is EngineState.Ended -> plan
}
