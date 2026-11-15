package com.routesoundtrack.audio

import android.content.Context
import android.util.Log
import com.routesoundtrack.audio.AudioFocusController.Companion.TAG
import com.routesoundtrack.domain.model.AudioCommand
import com.routesoundtrack.domain.model.EngineEvent
import com.routesoundtrack.domain.model.Mood
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

data class NowPlaying(val trackId: Int, val mood: Mood, val title: String, val durationSec: Double)

/**
 * 엔진의 AudioCommand를 실제 소리로 바꾼다.
 * Play·FadeOut·Stop은 순서대로 처리하고, Duck·Restore는 줄을 서지 않고 바로 반영한다.
 */
class PlaybackController(
    context: Context,
    scope: CoroutineScope,
    private val onEvent: (EngineEvent) -> Unit,
    private val onNowPlaying: (NowPlaying) -> Unit,
) {
    private val composer = TrackComposer()
    private val focus = AudioFocusController(context) { onEvent(it) }
    private val mixer = StemMixer { id ->
        focus.abandon(id)
        onEvent(EngineEvent.TrackEnded(id))
    }
    private val queue = Channel<AudioCommand>(Channel.UNLIMITED)

    @Volatile var masterGain: Float = 0.8f
    @Volatile var fadeOutMs: Int = 3_000

    init {
        scope.launch(Dispatchers.Default) {
            for (cmd in queue) handle(cmd)
        }
    }

    fun submit(cmds: List<AudioCommand>) {
        for (cmd in cmds) {
            when (cmd) {
                is AudioCommand.Duck -> mixer.setDuck(cmd.gain)
                AudioCommand.Restore -> mixer.setDuck(1f)
                AudioCommand.Pause -> mixer.setPaused(true)
                AudioCommand.Resume -> mixer.setPaused(false)
                else -> queue.trySend(cmd)
            }
        }
    }

    private suspend fun handle(cmd: AudioCommand) {
        when (cmd) {
            is AudioCommand.Play -> {
                val startedAt = System.currentTimeMillis()
                val track = composer.compose(cmd.mood, cmd.seed, fadeOutMs)
                if (!focus.request(cmd.trackId)) {
                    Log.d(TAG, "skip track ${cmd.trackId}: audio focus denied")
                    onEvent(EngineEvent.Skipped(cmd.trackId))
                    return
                }
                mixer.setDuck(1f)
                mixer.setPaused(false)
                mixer.play(track, cmd.trackId, masterGain)
                Log.d(TAG, "play track ${cmd.trackId} ${track.title} ${cmd.mood} (${track.durationSec.toInt()} s, composed in ${System.currentTimeMillis() - startedAt} ms)")
                onNowPlaying(NowPlaying(cmd.trackId, cmd.mood, track.title, track.durationSec))
            }
            is AudioCommand.FadeOut -> {
                Log.d(TAG, "fade out ${cmd.ms} ms")
                mixer.fadeOut(cmd.ms)
                delay(cmd.ms.toLong() + 50)
            }
            AudioCommand.Stop -> {
                Log.d(TAG, "stop")
                mixer.setPaused(false)
                mixer.stop()
                focus.abandon()
            }
            is AudioCommand.Duck, AudioCommand.Restore, AudioCommand.Pause, AudioCommand.Resume -> Unit   // submit()에서 바로 처리했다
        }
    }

    fun release() {
        queue.close()
        mixer.release()
        focus.abandon()
    }
}
