package com.routesoundtrack.audio

import android.content.Context
import android.media.AudioAttributes
import android.media.AudioFocusRequest
import android.media.AudioManager
import android.util.Log
import com.routesoundtrack.domain.model.EngineEvent

/**
 * 안내 음성 우선을 위한 오디오 포커스 처리.
 * setWillPauseWhenDucked(true)는 "덕킹 콜백을 앱이 직접 받겠다"는 뜻으로만 쓴다.
 * 콜백을 받으면 곡을 멈추지 않고 볼륨만 30%로 줄인다(안내 음성과 공존).
 */
class AudioFocusController(context: Context, private val onEvent: (EngineEvent) -> Unit) {
    private val am = context.getSystemService(AudioManager::class.java)
    private val attrs = AudioAttributes.Builder()
        .setUsage(AudioAttributes.USAGE_MEDIA)
        .setContentType(AudioAttributes.CONTENT_TYPE_MUSIC)
        .build()
    private val request = AudioFocusRequest.Builder(AudioManager.AUDIOFOCUS_GAIN_TRANSIENT)
        .setAudioAttributes(attrs)
        .setWillPauseWhenDucked(true)
        .setOnAudioFocusChangeListener { change -> handle(change) }
        .build()
    private var ownerTrackId: Int? = null

    @Synchronized
    fun request(trackId: Int): Boolean {
        val ok = am.requestAudioFocus(request) == AudioManager.AUDIOFOCUS_REQUEST_GRANTED
        if (ok) ownerTrackId = trackId
        return ok
    }

    /** trackId를 주면 그 곡이 포커스 주인일 때만 반납한다(이미 다음 곡이 받은 포커스를 지키기 위해). */
    @Synchronized
    fun abandon(trackId: Int? = null) {
        if (trackId != null && trackId != ownerTrackId) return
        if (ownerTrackId == null) return
        am.abandonAudioFocusRequest(request)
        ownerTrackId = null
    }

    /*
     * "다른 앱 음악이 재생 중이면 건너뛰기"는 1차에서 뺐다.
     * - isMusicActive()는 차량 시스템 UI의 영상(MOVIE) 플레이어에도 true가 되어 도착 곡을 잘못 건너뛰었다.
     * - 플레이어의 재생 상태(isActive)는 공개 SDK에 없어 정확히 판단할 수 없다.
     * 대신 GAIN_TRANSIENT 포커스로 요청하므로, 다른 음악 앱은 곡이 나오는 동안만 잠깐 멈췄다가 곡이 끝나면 다시 재생한다.
     * 2차에서 MediaSession 기반 판단을 검토한다.
     */

    private fun handle(change: Int) {
        Log.d(TAG, "focus change $change (owner=$ownerTrackId)")
        when (change) {
            // Pleos 오디오 포커스 가이드: CAN_DUCK은 볼륨만 낮추고, LOSS_TRANSIENT는 일시정지 후 복귀, LOSS는 즉시 정지
            AudioManager.AUDIOFOCUS_LOSS_TRANSIENT_CAN_DUCK -> onEvent(EngineEvent.FocusDuck)
            AudioManager.AUDIOFOCUS_LOSS_TRANSIENT -> onEvent(EngineEvent.FocusPaused)
            AudioManager.AUDIOFOCUS_GAIN -> onEvent(EngineEvent.FocusRestore)
            AudioManager.AUDIOFOCUS_LOSS -> onEvent(EngineEvent.FocusLost)
        }
    }

    companion object {
        const val TAG = "RouteSoundtrack"
    }
}
