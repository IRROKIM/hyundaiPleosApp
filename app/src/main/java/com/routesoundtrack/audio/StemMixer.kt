package com.routesoundtrack.audio

import android.media.AudioAttributes
import android.media.AudioFormat
import android.media.AudioTrack
import android.os.Process
import java.util.concurrent.TimeUnit
import java.util.concurrent.locks.ReentrantLock
import kotlin.concurrent.thread
import kotlin.concurrent.withLock
import kotlin.math.max
import kotlin.math.min

/**
 * 곡의 층들을 하나로 섞어 AudioTrack(Android 기본 API)으로 내보낸다.
 * 전용 오디오 스레드가 20 ms씩 렌더링한다. 층 섞기 계산은 MixMath를 쓰고,
 * 여기서는 명령 페이드아웃·덕킹·마스터 볼륨을 더한다.
 */
class StemMixer(
    private val sampleRate: Int = StemSynth.SAMPLE_RATE,
    private val onFinished: (trackId: Int) -> Unit,
) {
    private val track: AudioTrack = AudioTrack.Builder()
        .setAudioAttributes(
            AudioAttributes.Builder()
                .setUsage(AudioAttributes.USAGE_MEDIA)
                .setContentType(AudioAttributes.CONTENT_TYPE_MUSIC)
                .build()
        )
        .setAudioFormat(
            AudioFormat.Builder()
                .setSampleRate(sampleRate)
                .setEncoding(AudioFormat.ENCODING_PCM_16BIT)
                .setChannelMask(AudioFormat.CHANNEL_OUT_STEREO)
                .build()
        )
        .setBufferSizeInBytes(
            max(
                AudioTrack.getMinBufferSize(sampleRate, AudioFormat.CHANNEL_OUT_STEREO, AudioFormat.ENCODING_PCM_16BIT),
                CHUNK * 2 * 2 * 4,
            )
        )
        .setTransferMode(AudioTrack.MODE_STREAM)
        .build()

    private class Voice(val id: Int, val t: ComposedTrack, val master: Float) {
        var pos = 0
        var fadeStart = -1
        var fadeLen = 1
        var done = false
        val gains = FloatArray(t.layers.size)
    }

    private val lock = ReentrantLock()
    private val wake = lock.newCondition()
    private var current: Voice? = null
    @Volatile private var duckTarget = 1f
    @Volatile private var paused = false
    @Volatile private var running = true
    private val worker = thread(name = "StemMixer") { loop() }

    fun play(t: ComposedTrack, trackId: Int, master: Float) {
        var replaced: Int? = null
        lock.withLock {
            replaced = current?.id
            current = Voice(trackId, t, master)
            wake.signal()
        }
        replaced?.let(onFinished)
    }

    fun fadeOut(ms: Int) {
        lock.withLock {
            current?.let {
                if (it.fadeStart < 0) {
                    it.fadeStart = it.pos
                    it.fadeLen = max(1, ms * sampleRate / 1000)
                }
            }
        }
    }

    fun setDuck(gain: Float) {
        duckTarget = gain
    }

    /** 곡 위치를 그대로 둔 채 소리만 멈춘다(오디오 포커스를 잠깐 뺏겼을 때). */
    fun setPaused(p: Boolean) {
        paused = p
        lock.withLock { wake.signal() }
    }

    fun stop() {
        var old: Int? = null
        lock.withLock {
            old = current?.id
            current = null
        }
        old?.let(onFinished)
    }

    fun release() {
        running = false
        lock.withLock { wake.signal() }
        worker.join(500)
        track.release()
    }

    private fun loop() {
        Process.setThreadPriority(Process.THREAD_PRIORITY_AUDIO)
        val buf = ShortArray(CHUNK * 2)
        var started = false
        var duck = 1f
        while (running) {
            var finishedId = -1
            var have = false
            lock.withLock {
                val v = if (paused) null else current
                if (v != null) {
                    have = true
                    duck = render(v, buf, duck)
                    if (v.done) {
                        finishedId = v.id
                        current = null
                    }
                }
            }
            if (!have) {
                if (started) {
                    track.pause()
                    track.flush()
                    started = false
                }
                lock.withLock { if ((current == null || paused) && running) wake.await(100, TimeUnit.MILLISECONDS) }
                continue
            }
            if (!started) {
                track.play()
                started = true
            }
            track.write(buf, 0, buf.size)
            if (finishedId >= 0) onFinished(finishedId)
        }
        if (started) track.stop()
    }

    /** 20 ms(882프레임)를 렌더링한다. 바뀐 덕킹 게인을 돌려준다. */
    private fun render(v: Voice, out: ShortArray, duckIn: Float): Float {
        val t = v.t
        var duck = duckIn
        val target = duckTarget
        val layerStep = MixMath.layerStep(sampleRate)
        val duckDown = 0.7f / (0.3f * sampleRate)       // 덕킹 0.3초
        val duckUp = 0.7f / (0.8f * sampleRate)         // 복귀 0.8초
        val lr = FloatArray(2)
        for (i in 0 until CHUNK) {
            val f = v.pos
            if (v.done || f >= t.totalFrames) {
                v.done = true
                out[2 * i] = 0
                out[2 * i + 1] = 0
                continue
            }
            MixMath.mixLayers(t, f, v.gains, layerStep, lr)
            duck = if (duck > target) max(target, duck - duckDown) else min(target, duck + duckUp)

            var env = MixMath.envelope(t, f)
            if (v.fadeStart >= 0) {
                val k = 1f - (f - v.fadeStart).toFloat() / v.fadeLen
                if (k <= 0f) {
                    v.done = true
                    out[2 * i] = 0
                    out[2 * i + 1] = 0
                    continue
                }
                env = min(env, k)
            }
            val gain = env * v.master * duck
            out[2 * i] = toPcm(MixMath.softClip(lr[0] * gain))
            out[2 * i + 1] = toPcm(MixMath.softClip(lr[1] * gain))
            v.pos++
        }
        return duck
    }

    private fun toPcm(x: Float): Short = (x * 32767).toInt().toShort()

    companion object {
        const val CHUNK = 882   // 20 ms @ 44.1 kHz
    }
}
