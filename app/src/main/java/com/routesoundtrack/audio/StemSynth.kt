package com.routesoundtrack.audio

import com.routesoundtrack.audio.synth.Drums
import com.routesoundtrack.audio.synth.lowPassLoop
import com.routesoundtrack.audio.synth.midiToHz
import com.routesoundtrack.audio.synth.normalize
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.exp
import kotlin.math.min
import kotlin.math.roundToInt
import kotlin.math.sin
import kotlin.math.tanh
import kotlin.random.Random

/**
 * 로우파이 곡(조용한·중간 로우파이, 도착 곡) 렌더러. 음원 파일 없이 8마디 루프를 수학적으로 만든다.
 * 결과는 Layer 번호별 모노 PCM(FloatArray, -1..1)이다. 순수 Kotlin이라 같은 seed는 늘 같은 루프가 된다.
 */
class StemSynth(val sampleRate: Int = SAMPLE_RATE) {

    /** 곡 스타일. 음 높이는 MIDI 번호(60 = 가운데 도). */
    class Style(
        val bpm: Int,
        val chords: List<IntArray>,          // 2마디마다 바뀌는 코드 4개
        val bassRoots: IntArray,             // 코드별 베이스 근음
        val melodyScale: IntArray,           // 멜로디에 쓸 음계
        val swing: Double,                   // 8분음 뒷박을 늦추는 정도(박 단위)
        val kickBeats: DoubleArray,          // 마디 안 킥 위치(박)
        val bassPattern: DoubleArray,        // 마디 안 베이스 위치(박)
        val bassLength: Double,              // 베이스 음 길이(박)
        val melodyDensity: Double,           // 8분음 자리마다 음이 나올 확률
        val chordStrikes: List<DoubleArray>, // 코드를 치는 위치 변형(2마디 안, 박)
    )

    private val drumKit = Drums(sampleRate)

    fun render(style: Style, seed: Long): LoopStems {
        val rnd = Random(seed)
        val spb = 60.0 / style.bpm
        val frames = (BEATS_PER_LOOP * spb * sampleRate).roundToInt()
        val drums = FloatArray(frames)
        val bass = FloatArray(frames)
        val chords = FloatArray(frames)
        val melody = FloatArray(frames)
        val hats = FloatArray(frames)

        // 코드: 4개 코드 × 2마디. 음마다 살짝 늦게 쳐서(스트럼) 부드럽게 만든다.
        val strikes = style.chordStrikes[rnd.nextInt(style.chordStrikes.size)]
        for (c in 0 until 4) {
            for (b in strikes) {
                val start = (c * 8 + b) * spb
                val len = min(8 - b, 4.0) * spb
                style.chords[c].forEachIndexed { n, midi ->
                    addKeys(chords, start + n * 0.012, len, midiToHz(midi), 0.11f)
                }
            }
        }
        lowPassLoop(chords, 2200.0, sampleRate)

        // 베이스
        for (bar in 0 until 8) {
            val root = style.bassRoots[bar / 2]
            for (off in style.bassPattern) {
                addBass(bass, (bar * 4 + off) * spb, style.bassLength * spb, midiToHz(root), 0.5f)
            }
        }

        // 드럼: 부드러운 킥, 스네어(2·4박) / 하이햇: 8분음(뒷박 스윙)
        for (bar in 0 until 8) {
            val base = bar * 4.0
            for (k in style.kickBeats) drumKit.kick(drums, (base + k) * spb, amp = 0.9, fromHz = 120.0, toHz = 46.0, decaySec = 0.16)
            drumKit.snare(drums, (base + 1) * spb, rnd)
            drumKit.snare(drums, (base + 3) * spb, rnd)
            for (e in 0 until 8) {
                val swing = if (e % 2 == 1) style.swing else 0.0
                drumKit.hat(hats, (base + e * 0.5 + swing) * spb, rnd, if (e % 2 == 0) 0.22 else 0.14)
            }
        }
        lowPassLoop(drums, 6000.0, sampleRate)
        lowPassLoop(hats, 8000.0, sampleRate)

        // 멜로디: 음계 위를 한두 칸씩 걷는 랜덤 워크 (seed마다 다른 멜로디)
        var idx = style.melodyScale.size / 2
        for (bar in 0 until 8) {
            var e = 0
            while (e < 8) {
                if (rnd.nextDouble() < style.melodyDensity) {
                    idx = (idx + rnd.nextInt(-2, 3)).coerceIn(0, style.melodyScale.size - 1)
                    val lenEighths = 1 + rnd.nextInt(3)
                    addLead(melody, (bar * 4 + e * 0.5) * spb, lenEighths * 0.5 * spb, midiToHz(style.melodyScale[idx]), 0.16f)
                    e += lenEighths
                } else {
                    e++
                }
            }
        }
        lowPassLoop(melody, 3200.0, sampleRate)

        normalize(drums, 0.32f)
        normalize(bass, 0.32f)
        normalize(chords, 0.32f)
        normalize(melody, 0.32f)
        normalize(hats, 0.14f)

        val layers = Array(Layer.COUNT) { FloatArray(0) }
        layers[Layer.DRUMS] = drums
        layers[Layer.BASS] = bass
        layers[Layer.CHORDS] = chords
        layers[Layer.MELODY] = melody
        layers[Layer.HATS] = hats
        return LoopStems(layers, frames, style.bpm)
    }

    // ---- 악기 ----------------------------------------------------------------
    // 사인파는 회전 공식(s,c를 매 샘플 회전)으로 만들어 sin() 호출 없이 빠르게 계산한다.

    private fun addKeys(buf: FloatArray, startSec: Double, durSec: Double, hz: Double, amp: Float) {
        val start = (startSec * sampleRate).toInt()
        val n = ((durSec + 0.4) * sampleRate).toInt()
        val w = 2 * PI * hz / sampleRate
        val sw = sin(w); val cw = cos(w)
        var s = 0.0; var c = 1.0
        var decay = 1.0; val dDecay = exp(-1.0 / (1.4 * sampleRate))
        var bright = 1.0; val dBright = exp(-1.0 / (0.35 * sampleRate))
        var rel = 1.0; val dRel = exp(-1.0 / (0.12 * sampleRate))
        val relStart = (durSec * sampleRate).toInt()
        val attackN = 0.008 * sampleRate
        for (i in 0 until n) {
            val s2 = 2 * s * c
            val s3 = s * (3 - 4 * s * s)
            val v = s + 0.28 * s2 * bright + 0.06 * s3
            val att = if (i < attackN) i / attackN else 1.0
            if (i >= relStart) rel *= dRel
            buf[(start + i) % buf.size] += (amp * v * att * decay * rel).toFloat()
            val ns = s * cw + c * sw
            c = c * cw - s * sw
            s = ns
            decay *= dDecay
            bright *= dBright
        }
    }

    private fun addBass(buf: FloatArray, startSec: Double, durSec: Double, hz: Double, amp: Float) {
        val start = (startSec * sampleRate).toInt()
        val n = ((durSec + 0.08) * sampleRate).toInt()
        val w = 2 * PI * hz / sampleRate
        val sw = sin(w); val cw = cos(w)
        var s = 0.0; var c = 1.0
        var body = 1.0; val dBody = exp(-1.0 / (0.9 * sampleRate))
        var rel = 1.0; val dRel = exp(-1.0 / (0.03 * sampleRate))
        val relStart = (durSec * sampleRate).toInt()
        val attackN = 0.005 * sampleRate
        val norm = tanh(1.6)
        for (i in 0 until n) {
            val v = tanh(1.6 * (s + 0.25 * 2 * s * c)) / norm
            val att = if (i < attackN) i / attackN else 1.0
            if (i >= relStart) rel *= dRel
            buf[(start + i) % buf.size] += (amp * v * att * body * rel).toFloat()
            val ns = s * cw + c * sw
            c = c * cw - s * sw
            s = ns
            body *= dBody
        }
    }

    private fun addLead(buf: FloatArray, startSec: Double, durSec: Double, hz: Double, amp: Float) {
        val start = (startSec * sampleRate).toInt()
        val n = ((durSec + 0.25) * sampleRate).toInt()
        val w = 2 * PI * hz / sampleRate
        val sw = sin(w); val cw = cos(w)
        var s = 0.0; var c = 1.0
        var decay = 1.0; val dDecay = exp(-1.0 / (2.0 * sampleRate))
        var rel = 1.0; val dRel = exp(-1.0 / (0.08 * sampleRate))
        val relStart = (durSec * sampleRate).toInt()
        val attackN = 0.02 * sampleRate
        for (i in 0 until n) {
            val v = s + 0.12 * s * (3 - 4 * s * s)
            val att = if (i < attackN) i / attackN else 1.0
            if (i >= relStart) rel *= dRel
            buf[(start + i) % buf.size] += (amp * v * att * decay * rel).toFloat()
            val ns = s * cw + c * sw
            c = c * cw - s * sw
            s = ns
            decay *= dDecay
        }
    }

    companion object {
        const val SAMPLE_RATE = 44_100
        const val BEATS_PER_LOOP = 32          // 4/4박 8마디

        private val PENTATONIC = intArrayOf(69, 72, 74, 76, 79, 81, 84, 86)   // 다장조 5음계 (A4–D6)

        /** 조용한 로우파이(보라). 시내·정체 자동 선택. 차분한 ii–V–I–vi 진행, 드럼 없이 코드·멜로디만 쓴다(TrackComposer.layersFor). */
        val CHILL = Style(
            bpm = 75,
            chords = listOf(intArrayOf(53, 57, 60, 64), intArrayOf(53, 59, 62, 67), intArrayOf(52, 55, 59, 62), intArrayOf(55, 59, 60, 64)),
            bassRoots = intArrayOf(38, 43, 36, 45),
            melodyScale = PENTATONIC,
            swing = 0.08,
            kickBeats = doubleArrayOf(0.0, 2.5),
            bassPattern = doubleArrayOf(0.0, 2.5),
            bassLength = 1.6,
            melodyDensity = 0.30,
            chordStrikes = listOf(doubleArrayOf(0.0, 4.0), doubleArrayOf(0.0, 3.5, 6.0)),
        )

        /**
         * 중간 로우파이(파랑): CHILL보다 조금 빠르고 드럼·베이스가 들어가는 vi–IV–I–V 진행.
         * 킥은 반 박 당긴 싱코페이션, 베이스는 짧게 끊어 고개가 끄덕여지는 정도로만 활기를 준다.
         */
        val GROOVE = Style(
            bpm = 86,
            chords = listOf(intArrayOf(57, 60, 64, 67), intArrayOf(53, 57, 60, 64), intArrayOf(55, 59, 60, 64), intArrayOf(55, 59, 62, 64)),
            bassRoots = intArrayOf(45, 41, 36, 43),
            melodyScale = PENTATONIC,
            swing = 0.10,
            kickBeats = doubleArrayOf(0.0, 1.75, 2.5),
            bassPattern = doubleArrayOf(0.0, 1.5, 2.5, 3.5),
            bassLength = 0.9,
            melodyDensity = 0.38,
            chordStrikes = listOf(doubleArrayOf(0.0, 2.5, 4.0, 6.5), doubleArrayOf(0.0, 3.5, 6.0)),
        )

        /** 도착 곡: 느리게 도(C)로 해결되는 마무리 진행. */
        val ARRIVAL = Style(
            bpm = 70,
            chords = listOf(intArrayOf(53, 57, 60, 64), intArrayOf(52, 55, 59, 62), intArrayOf(50, 53, 57, 60), intArrayOf(52, 55, 59, 60)),
            bassRoots = intArrayOf(41, 40, 38, 36),
            melodyScale = PENTATONIC,
            swing = 0.06,
            kickBeats = doubleArrayOf(0.0, 2.5),
            bassPattern = doubleArrayOf(0.0),
            bassLength = 3.0,
            melodyDensity = 0.22,
            chordStrikes = listOf(doubleArrayOf(0.0, 4.0)),
        )
    }
}
