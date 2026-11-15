package com.routesoundtrack.domain.model

// 이 패키지(domain)는 android.* 를 import하지 않는다. JVM 단위 테스트로 바로 검증하기 위해서다.

enum class Density { FREQUENT, OCCASIONAL, OFF }   // 음악 밀도: 자주 · 가끔 · 끄기
enum class Preset { LONG_HAUL, TRIP, FAMILY }      // 주행 목적: 장거리 · 여행 · 가족
/** 곡 성격 설정. 메인 화면의 감정 원 4개(보라·파랑·오렌지-레드·옐로)와 1:1로 대응한다. AUTO = 도로 맥락에 맡김. */
enum class MoodMode { AUTO, CHILL, GROOVE, UPBEAT, HYPE }

/**
 * 실제 재생할 곡.
 * CHILL = 조용한 로우파이(보라), GROOVE = 중간 로우파이(파랑, 드럼·베이스가 있는 로우파이),
 * UPBEAT = 각성형 신스웨이브(오렌지-레드), HYPE = 파워 하우스/EDM(옐로), ARRIVAL = 도착 곡.
 */
enum class Mood { CHILL, GROOVE, UPBEAT, HYPE, ARRIVAL }
enum class Road { CITY, JAM, HIGHWAY }             // 도로 맥락: 시내 · 정체 · 고속도로
enum class CueKind { START, MID, ARRIVAL }         // 재생 지점 종류

/** 사용자 설정. data class = equals·hashCode·toString·copy를 자동 생성하는 불변 값 객체. */
data class Settings(
    val preset: Preset = Preset.TRIP,
    val density: Density = Density.FREQUENT,
    val moodMode: MoodMode = MoodMode.AUTO,
    val autoStart: Boolean = false,   // 내비 안내가 시작되면 자동으로 켜기. 사용자가 직접 켜야 한다(자동 재생 금지 규정)
)

/** 1틱(SDK 1초, 디버그 시뮬레이터 0.25초)마다 모은 차량·내비 값. 모르는 값은 null이다. */
data class DriveSnapshot(
    val timeMs: Long,
    val routeActive: Boolean,
    val totalKm: Double?,
    val progressKm: Double?,
    val remainingKm: Double?,
    val etaSec: Int?,
    val speedKmh: Double?,
    val brakePressed: Boolean? = null,
    val night: Boolean? = null,
    val highwayHint: Boolean? = null,   // SDK가 도로 등급을 주면 채운다
    val drivingTimeMin: Int? = null,    // 연속 주행 시간(분). SDK: getTripCurrentDrivingTime
    val destination: String? = null,    // 목적지 이름 (화면 표시용)
)

/** 재생 지점(큐). atKm = 출발 후 진행 거리. */
data class Cue(val id: Int, val atKm: Double, val kind: CueKind)

data class RoutePlan(val totalKm: Double, val cues: List<Cue>, val seed: Long) {
    /** 거리로 재생하는 큐(시작·중간). 도착 곡은 ETA로 따로 판단한다. */
    val playable: List<Cue> = cues.filter { it.kind != CueKind.ARRIVAL }
    val arrivalCue: Cue? = cues.lastOrNull { it.kind == CueKind.ARRIVAL }
}

/** 주행 맥락. road == null 이면 아직 판단할 수 없음(속도 미상). longDrive = 연속 주행 90분 이상. */
data class DriveContext(val road: Road?, val night: Boolean, val longDrive: Boolean = false)

/** 엔진 → 오디오로 보내는 명령. sealed: 하위 타입이 여기서만 정의돼 when에서 빠짐없이 검사된다. */
sealed interface AudioCommand {
    data class Play(val trackId: Int, val cue: Cue, val mood: Mood, val seed: Long) : AudioCommand
    data class FadeOut(val ms: Int) : AudioCommand
    data class Duck(val gain: Float) : AudioCommand
    data object Restore : AudioCommand     // data object: 필드 없는 싱글턴
    data object Pause : AudioCommand       // 포커스를 잠깐 뺏겼을 때: 곡 위치를 기억한 채 멈춘다
    data object Resume : AudioCommand
    data object Stop : AudioCommand
}

/** 오디오 → 엔진으로 돌아오는 이벤트. */
sealed interface EngineEvent {
    data class TrackEnded(val trackId: Int) : EngineEvent
    data class Skipped(val trackId: Int) : EngineEvent
    data object FocusDuck : EngineEvent
    data object FocusPaused : EngineEvent  // 다른 소리가 잠깐 포커스를 가져감(LOSS_TRANSIENT)
    data object FocusRestore : EngineEvent
    data object FocusLost : EngineEvent
}
