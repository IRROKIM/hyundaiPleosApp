package com.routesoundtrack.audio.synth

import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.exp
import kotlin.math.min
import kotlin.math.pow

/*
 * 합성에 쓰는 공용 부품 (순수 Kotlin). 모든 곡이 같은 부품을 쓴다.
 * 배경 잡음(레코드판 히스·크랙클)은 차량 스피커에서 고장 소리처럼 들려 넣지 않는다.
 * 버퍼에 소리를 더할 때 끝을 넘으면 앞으로 감아(wrap) 써서 루프 이음매가 자연스럽게 이어진다.
 */

fun midiToHz(midi: Int): Double = 440.0 * 2.0.pow((midi - 69) / 12.0)

/**
 * 버퍼 전체에 1극 저역 통과 필터를 건다(poles번 반복하면 더 가파르다).
 * 루프로 반복되는 소리라서 버퍼 끝부분으로 필터를 한 번 예열한다. 0부터 시작할 때 생기는 이음매 클릭을 막기 위해서다.
 */
fun lowPassLoop(buf: FloatArray, cutoffHz: Double, sampleRate: Int, poles: Int = 1) {
    if (buf.isEmpty()) return
    val a = exp(-2 * PI * cutoffHz / sampleRate)
    repeat(poles) {
        var y = 0.0
        val warm = min(buf.size, sampleRate)
        for (i in buf.size - warm until buf.size) y = (1 - a) * buf[i] + a * y
        for (i in buf.indices) {
            y = (1 - a) * buf[i] + a * y
            buf[i] = y.toFloat()
        }
    }
}

fun normalize(buf: FloatArray, peak: Float) {
    var max = 0f
    for (v in buf) if (abs(v) > max) max = abs(v)
    if (max <= 0f) return
    val k = peak / max
    for (i in buf.indices) buf[i] *= k
}

/** 사이드체인 펌핑: 박마다(킥 순간) 음량을 depth만큼 줄였다가 빠르게 회복해 "출렁이는" 그루브를 만든다. */
fun applyPump(buf: FloatArray, sampleRate: Int, secPerBeat: Double, depth: Double, recoverSec: Double = 0.16) {
    if (buf.isEmpty() || depth <= 0.0) return
    val beatFrames = secPerBeat * sampleRate
    val tau = recoverSec / 3
    for (i in buf.indices) {
        val t = (i % beatFrames) / sampleRate
        buf[i] *= (1.0 - depth * exp(-t / tau)).toFloat()
    }
}

/** 피드백 딜레이(메아리). 루프 끝을 넘는 메아리는 앞으로 감긴다. */
fun addDelay(buf: FloatArray, delayFrames: Int, feedback: Double, mix: Double, repeats: Int = 3) {
    if (buf.isEmpty()) return
    val src = buf.copyOf()
    var g = mix
    for (r in 1..repeats) {
        val off = delayFrames * r
        for (i in src.indices) buf[(i + off) % buf.size] += (src[i] * g).toFloat()
        g *= feedback
    }
}

/** PolyBLEP: 톱니·사각파의 날카로운 모서리를 다듬어 "지직"거리는 앨리어싱 잡음을 줄인다. */
private fun polyBlep(t: Double, dt: Double): Double = when {
    t < dt -> { val x = t / dt; x + x - x * x - 1.0 }
    t > 1.0 - dt -> { val x = (t - 1.0) / dt; x * x + x + x + 1.0 }
    else -> 0.0
}

/** 위상 누적 오실레이터. 매 샘플 주파수를 바꿀 수 있다(비브라토·피치 엔벨로프). */
class Osc(private val sampleRate: Int, startPhase: Double = 0.0) {
    private var phase = startPhase

    fun saw(hz: Double): Double {
        val dt = hz / sampleRate
        val v = 2 * phase - 1 - polyBlep(phase, dt)
        advance(dt)
        return v
    }

    fun square(hz: Double): Double {
        val dt = hz / sampleRate
        var v = if (phase < 0.5) 1.0 else -1.0
        v += polyBlep(phase, dt)
        v -= polyBlep((phase + 0.5) % 1.0, dt)
        advance(dt)
        return v
    }

    private fun advance(dt: Double) {
        phase += dt
        if (phase >= 1.0) phase -= 1.0
    }
}

/** 2극(12 dB/oct) 저역 통과. 컷오프를 샘플마다 바꿀 수 있어 필터 엔벨로프에 쓴다. */
class LowPass2(private val sampleRate: Int) {
    private var y1 = 0.0
    private var y2 = 0.0

    fun process(x: Double, cutoffHz: Double): Double {
        val fc = cutoffHz.coerceIn(20.0, sampleRate * 0.45)
        val a = exp(-2 * PI * fc / sampleRate)
        y1 = (1 - a) * x + a * y1
        y2 = (1 - a) * y1 + a * y2
        return y2
    }
}

/** 소리 크기 엔벨로프: 어택(선형) → 디케이 → 서스테인, 음이 끝나면 릴리스. t·dur는 초. */
fun adsr(t: Double, dur: Double, attack: Double, decay: Double, sustain: Double, release: Double): Double {
    fun held(x: Double): Double = when {
        x < attack -> x / attack
        x < attack + decay -> 1 - (1 - sustain) * (x - attack) / decay
        else -> sustain
    }
    if (t < 0) return 0.0
    if (t <= dur) return held(t)
    val r = 1 - (t - dur) / release
    return if (r <= 0) 0.0 else held(dur) * r
}
