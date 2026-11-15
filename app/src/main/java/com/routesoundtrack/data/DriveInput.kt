package com.routesoundtrack.data

import com.routesoundtrack.data.sdk.RouteInfo
import com.routesoundtrack.domain.model.DriveSnapshot

/**
 * 주행 세션이 읽는 입력 하나. DriveSession은 값이 어디서 오는지 모른다.
 * - 실차·에뮬레이터: SdkDriveInput (Pleos SDK)
 * - 디버그 빌드에서만: ScenarioDriveInput (내장 시나리오). DriveInputs.USE_SIMULATOR로 고른다
 */
interface DriveInput {
    /** read() 사이의 간격. */
    val tickMs: Long

    /** 지금 설정된 내비 경로. 목적지가 없으면 null. 미리보기와 "안내 시작 시 자동 시작" 감시에 쓴다. */
    suspend fun readRoute(): RouteInfo?

    /** 새 주행을 시작할 때 한 번 부른다. 진행 거리·시간을 0부터 다시 센다. */
    fun reset()

    /** 한 틱의 차량·내비 값. playing = 지금 곡이 나오는 중(시뮬레이터만 배속 조절에 쓴다). */
    suspend fun read(playing: Boolean): DriveSnapshot
}
