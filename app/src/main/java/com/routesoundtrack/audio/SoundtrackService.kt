package com.routesoundtrack.audio

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.Bundle
import android.support.v4.media.MediaBrowserCompat
import android.support.v4.media.MediaMetadataCompat
import android.support.v4.media.session.MediaSessionCompat
import android.support.v4.media.session.PlaybackStateCompat
import android.util.Log
import androidx.core.app.NotificationCompat
import androidx.core.app.ServiceCompat
import androidx.core.content.ContextCompat
import androidx.media.MediaBrowserServiceCompat
import com.routesoundtrack.App
import com.routesoundtrack.MainActivity
import com.routesoundtrack.R
import com.routesoundtrack.audio.AudioFocusController.Companion.TAG
import com.routesoundtrack.session.DriveSession
import com.routesoundtrack.session.UiState
import com.routesoundtrack.ui.moodLabel
import kotlin.math.ceil
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch

/**
 * Pleos Music/Audio 앱 필수 요건: MediaBrowserService + MediaSession.
 * - 시스템(미디어 센터·핸들 버튼)에 재생 상태와 곡 정보를 알리고, 재생·정지 요청을 받는다.
 * - 주행 세션이 도는 동안에만 포그라운드 서비스가 된다. 그래야 앱 화면을 내려도 음악이 이어진다.
 * - 세션이 끝나면 알림·포그라운드 상태·MediaSession 활성화를 모두 반납한다(쓸 일이 없는 FGS 유지 금지).
 * 소리는 DriveSession → PlaybackController가 낸다. 이 서비스는 상태를 지켜보고 전달만 한다.
 */
class SoundtrackService : MediaBrowserServiceCompat() {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)
    private lateinit var drive: DriveSession
    private lateinit var media: MediaSessionCompat
    private var foreground = false

    /** 화면 상태(UiState)에서 시스템에 알릴 것만 뽑은 값. 이 값이 바뀔 때만 갱신한다(0.25초마다 갱신하지 않도록). */
    private data class Shown(val running: Boolean, val title: String?, val moodRes: Int?, val nextKm: String?)

    override fun onCreate() {
        super.onCreate()
        drive = (application as App).container.session
        media = MediaSessionCompat(this, "RouteSoundtrack").apply {
            setCallback(object : MediaSessionCompat.Callback() {
                override fun onPlay() = drive.start()      // 사용자의 명시적 동작 또는 시스템 재개 요청
                override fun onPause() = drive.stop()
                override fun onStop() = drive.stop()
            })
        }
        sessionToken = media.sessionToken
        createChannel()
        scope.launch {
            drive.ui.map { it.toShown() }.distinctUntilChanged().collect { show(it) }
        }
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        // startForegroundService()로 시작됐으면 5초 안에 startForeground()를 불러야 한다.
        goForeground(drive.ui.value.toShown())
        if (!drive.ui.value.running) leaveForeground()
        return START_NOT_STICKY
    }

    override fun onDestroy() {
        scope.cancel()
        media.isActive = false
        media.release()
        super.onDestroy()
    }

    // 탐색할 음원 목록이 없으므로 빈 목록만 돌려준다.
    override fun onGetRoot(clientPackageName: String, clientUid: Int, rootHints: Bundle?): BrowserRoot =
        BrowserRoot("root", null)

    override fun onLoadChildren(parentId: String, result: Result<MutableList<MediaBrowserCompat.MediaItem>>) {
        result.sendResult(mutableListOf())
    }

    private fun UiState.toShown() = Shown(
        running = running,
        title = nowPlaying?.title,
        moodRes = nowPlaying?.let { moodLabel(it.mood) },
        // 1 km 단위로만 보여 준다. 0.1 km마다 바뀌면 시스템 미디어 정보를 너무 자주 갱신하게 된다.
        nextKm = if (nowPlaying == null) nextCueInKm?.let { "%.0f".format(ceil(it)) } else null,
    )

    private fun show(s: Shown) {
        val state = when {
            !s.running -> PlaybackStateCompat.STATE_STOPPED
            s.title != null -> PlaybackStateCompat.STATE_PLAYING
            else -> PlaybackStateCompat.STATE_PAUSED          // 곡 사이 무음 구간
        }
        media.setPlaybackState(
            PlaybackStateCompat.Builder()
                .setActions(PlaybackStateCompat.ACTION_PLAY or PlaybackStateCompat.ACTION_PAUSE or PlaybackStateCompat.ACTION_STOP)
                .setState(state, PlaybackStateCompat.PLAYBACK_POSITION_UNKNOWN, if (s.title != null) 1f else 0f)
                .build()
        )
        media.setMetadata(
            MediaMetadataCompat.Builder()
                .putString(MediaMetadataCompat.METADATA_KEY_TITLE, titleOf(s))
                .putString(MediaMetadataCompat.METADATA_KEY_ARTIST, getString(R.string.app_name))
                .build()
        )
        media.isActive = s.running
        if (s.running) goForeground(s) else leaveForeground()
    }

    private fun titleOf(s: Shown): String = when {
        s.title != null && s.moodRes != null -> "${getString(s.moodRes)} · ${s.title}"
        s.nextKm != null -> getString(R.string.notif_next, s.nextKm)
        else -> getString(R.string.app_name)
    }

    private fun goForeground(s: Shown) {
        val type = if (Build.VERSION.SDK_INT >= 29) ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PLAYBACK else 0
        // 백그라운드에서는 시스템이 포그라운드 전환을 막을 수 있다. 그래도 재생은 계속하도록 예외를 삼킨다.
        runCatching { ServiceCompat.startForeground(this, NOTIF_ID, notification(s), type) }
            .onSuccess { foreground = true }
            .onFailure { Log.w(TAG, "startForeground failed: $it") }
    }

    private fun leaveForeground() {
        if (foreground) ServiceCompat.stopForeground(this, ServiceCompat.STOP_FOREGROUND_REMOVE)
        foreground = false
        stopSelf()
    }

    private fun notification(s: Shown): Notification {
        val open = PendingIntent.getActivity(
            this, 0, Intent(this, MainActivity::class.java), PendingIntent.FLAG_IMMUTABLE,
        )
        return NotificationCompat.Builder(this, CHANNEL)
            .setSmallIcon(R.drawable.ic_stat_soundtrack)
            .setContentTitle(titleOf(s))
            .setContentText(getString(R.string.app_name))
            .setContentIntent(open)
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
            .setStyle(androidx.media.app.NotificationCompat.MediaStyle().setMediaSession(media.sessionToken))
            .build()
    }

    private fun createChannel() {
        val nm = getSystemService(NotificationManager::class.java)
        nm.createNotificationChannel(
            NotificationChannel(CHANNEL, getString(R.string.notif_channel), NotificationManager.IMPORTANCE_LOW)
        )
    }

    companion object {
        private const val CHANNEL = "drive_session"
        private const val NOTIF_ID = 1

        /** 주행 세션이 시작될 때 부른다. 백그라운드라서 시작이 막히면 조용히 넘어간다. */
        fun start(context: Context) {
            runCatching { ContextCompat.startForegroundService(context, Intent(context, SoundtrackService::class.java)) }
                .onFailure { Log.w(TAG, "service start blocked: $it") }
        }
    }
}
