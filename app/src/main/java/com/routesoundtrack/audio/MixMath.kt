package com.routesoundtrack.audio

import kotlin.math.max
import kotlin.math.min
import kotlin.math.tanh

/**
 * 믹스 계산 (순수 Kotlin). 앱의 StemMixer와 테스트용 오프라인 렌더링이 같은 계산을 쓴다.
 */
object MixMath {

    /** 층 켜기·끄기 램프: 0.5초에 걸쳐 0↔1. */
    fun layerStep(sampleRate: Int): Float = 1f / (0.5f * sampleRate)

    /**
     * frame 위치의 층들을 섞어 out[0]=왼쪽, out[1]=오른쪽에 쓴다.
     * gains는 층별 현재 게인(램프 상태)이고, 이 함수가 목표값 쪽으로 한 걸음 옮긴다.
     */
    fun mixLayers(t: ComposedTrack, frame: Int, gains: FloatArray, step: Float, out: FloatArray) {
        val loopIdx = frame / t.loopFrames
        val inLoop = frame - loopIdx * t.loopFrames
        val target = t.loopGains[min(loopIdx, t.loopGains.size - 1)]
        var l = 0f
        var r = 0f
        for (k in t.layers.indices) {
            val g0 = gains[k]
            val tg = target[k]
            val g = if (g0 < tg) min(tg, g0 + step) else max(tg, g0 - step)
            gains[k] = g
            val layer = t.layers[k]
            if (g > 0f && layer.isNotEmpty()) {
                val s = layer[inLoop] * g
                l += s * Layer.PAN_L[k]
                r += s * Layer.PAN_R[k]
            }
        }
        out[0] = l
        out[1] = r
    }

    /** 곡 시작 페이드인·곡 끝 페이드아웃 (0..1). */
    fun envelope(t: ComposedTrack, frame: Int): Float {
        var env = 1f
        if (frame < t.fadeInFrames) env = frame.toFloat() / t.fadeInFrames
        val tailStart = t.totalFrames - t.fadeOutFrames
        if (frame > tailStart) env = min(env, (t.totalFrames - frame).toFloat() / t.fadeOutFrames)
        return env.coerceIn(0f, 1f)
    }

    /** 부드러운 클리핑: 큰 소리를 찌그러지지 않게 둥글게 누른다. */
    fun softClip(x: Float): Float = tanh(x.toDouble()).toFloat()

    /** 테스트·미리듣기용: 곡 전체를 스테레오(L,R 교차) float로 만든다. */
    fun renderOffline(t: ComposedTrack, master: Float = 0.8f): FloatArray {
        val out = FloatArray(t.totalFrames * 2)
        val gains = FloatArray(t.layers.size)
        val lr = FloatArray(2)
        val step = layerStep(t.sampleRate)
        for (f in 0 until t.totalFrames) {
            mixLayers(t, f, gains, step, lr)
            val g = envelope(t, f) * master
            out[2 * f] = softClip(lr[0] * g)
            out[2 * f + 1] = softClip(lr[1] * g)
        }
        return out
    }
}
