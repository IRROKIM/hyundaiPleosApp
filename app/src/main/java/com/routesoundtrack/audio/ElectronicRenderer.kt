package com.routesoundtrack.audio

import com.routesoundtrack.audio.synth.Drums
import com.routesoundtrack.audio.synth.LowPass2
import com.routesoundtrack.audio.synth.Osc
import com.routesoundtrack.audio.synth.addDelay
import com.routesoundtrack.audio.synth.adsr
import com.routesoundtrack.audio.synth.applyPump
import com.routesoundtrack.audio.synth.lowPassLoop
import com.routesoundtrack.audio.synth.midiToHz
import com.routesoundtrack.audio.synth.normalize
import kotlin.math.PI
import kotlin.math.exp
import kotlin.math.roundToInt
import kotlin.math.sin
import kotlin.random.Random

/**
 * 각성형(드라이브 신스웨이브)·파워(하우스·EDM) 곡 렌더러. 순수 Kotlin.
 * 공통 뼈대: 매 박 킥 + 사이드체인 펌핑(출렁임) + 16분 하이햇 + 마지막 마디 필인 + 동기(motif) 기반 리드.
 */
class ElectronicRenderer(val sampleRate: Int = StemSynth.SAMPLE_RATE) {

    enum class ChordMode { PAD, STAB }
    enum class BassMode { OCTAVE_EIGHTHS, OFFBEAT }

    /** 코드 진행 4개(2마디씩) + 베이스 근음 + 리드 음계. 음 높이는 MIDI 번호. */
    class Progression(val chords: List<IntArray>, val bassRoots: IntArray, val scale: IntArray)

    class Style(
        val bpm: Int,
        val progressions: List<Progression>,
        val chordMode: ChordMode,
        val stabBeats: DoubleArray,     // STAB일 때 마디 안 위치(박)
        val bassMode: BassMode,
        val bassCutoffHz: Double,
        val pumpBass: Double,
        val pumpChords: Double,
        val gatedSnare: Boolean,        // true: 80년대 게이트 스네어, false: 클랩
        val openHats: Boolean,
        val arp: Boolean,
        val riffLead: Boolean,          // true: 짧은 리프 반복(EDM), false: 노래하는 리드(신스웨이브)
    )

    private class Note(val beat: Double, val len: Double, val midi: Int)

    private val drumKit = Drums(sampleRate)

    fun render(style: Style, seed: Long): LoopStems {
        val rnd = Random(seed)
        val prog = style.progressions[rnd.nextInt(style.progressions.size)]
        val spb = 60.0 / style.bpm
        val frames = (StemSynth.BEATS_PER_LOOP * spb * sampleRate).roundToInt()
        fun sec(beat: Double) = beat * spb

        val drums = FloatArray(frames)
        val hats = FloatArray(frames)
        val bass = FloatArray(frames)
        val chords = FloatArray(frames)
        val melody = FloatArray(frames)
        val arp = if (style.arp) FloatArray(frames) else FloatArray(0)

        // 드럼: 매 박 킥, 2·4박 스네어(또는 클랩), 마지막 마디 필인
        for (bar in 0 until 8) {
            val base = bar * 4.0
            for (beat in 0 until 4) drumKit.kick(drums, sec(base + beat))
            for (b in doubleArrayOf(1.0, 3.0)) {
                if (style.gatedSnare) drumKit.snare(drums, sec(base + b), rnd, amp = 0.65, gated = true)
                else drumKit.clap(drums, sec(base + b), rnd, amp = 0.75)
            }
            if (bar == 7) {
                for (k in 0 until 8) drumKit.snare(drums, sec(base + 2.0 + k * 0.25), rnd, amp = 0.15 + 0.06 * k)
            }
            // 하이햇: 16분, 뒷박(8분) 강세. 하우스는 뒷박에 열린 하이햇.
            for (s in 0 until 16) {
                val t = sec(base + s * 0.25)
                val offbeat = s % 4 == 2
                if (style.openHats && offbeat) {
                    drumKit.hat(hats, t, rnd, 0.22, open = true)
                } else {
                    drumKit.hat(hats, t, rnd, if (offbeat) 0.24 else if (s % 2 == 0) 0.14 else 0.09)
                }
            }
        }
        lowPassLoop(hats, 9000.0, sampleRate)

        // 베이스: 톱니 + 한 옥타브 아래 사각파, 필터 엔벨로프로 "뚱" 하는 어택
        for (bar in 0 until 8) {
            val root = prog.bassRoots[bar / 2]
            when (style.bassMode) {
                BassMode.OCTAVE_EIGHTHS -> for (e in 0 until 8) {
                    addSynthBass(bass, sec(bar * 4 + e * 0.5), sec(0.38), root + if (e % 2 == 1) 12 else 0, style.bassCutoffHz)
                }
                BassMode.OFFBEAT -> for (b in 0 until 4) {
                    addSynthBass(bass, sec(bar * 4 + b + 0.5), sec(0.35), root, style.bassCutoffHz)
                }
            }
        }
        applyPump(bass, sampleRate, spb, style.pumpBass)

        // 코드: 신스웨이브는 두 겹 디튠 톱니 패드, 하우스는 짧은 스탭
        for (c in 0 until 4) {
            val chord = prog.chords[c]
            when (style.chordMode) {
                ChordMode.PAD -> for (midi in chord) addPad(chords, sec(c * 8.0), sec(8.0), midiToHz(midi))
                ChordMode.STAB -> for (bar in 0 until 2) for (b in style.stabBeats) for (midi in chord) {
                    addStab(chords, sec(c * 8.0 + bar * 4 + b), sec(0.22), midiToHz(midi))
                }
            }
        }
        applyPump(chords, sampleRate, spb, style.pumpChords)

        // 아르페지오: 코드 톤을 16분음으로 2옥타브 오르내리고, 3/16박 메아리를 단다
        if (style.arp) {
            val pattern = rnd.nextInt(3)
            for (bar in 0 until 8) {
                val tones = prog.chords[bar / 2].sorted().let { it + it.map { m -> m + 12 } }
                val order = when (pattern) {
                    0 -> tones
                    1 -> tones.reversed()
                    else -> tones + tones.reversed().drop(1).dropLast(1)
                }
                for (s in 0 until 16) {
                    addPluck(arp, sec(bar * 4 + s * 0.25), sec(0.22), midiToHz(order[(bar * 16 + s) % order.size]))
                }
            }
            addDelay(arp, (sec(0.75) * sampleRate).toInt(), feedback = 0.35, mix = 0.3)
            applyPump(arp, sampleRate, spb, 0.25)
        }

        // 리드
        for (n in composeLead(prog, rnd, style.riffLead)) {
            addLead(melody, sec(n.beat), sec(n.len), midiToHz(n.midi))
        }

        normalize(drums, 0.42f)
        normalize(hats, 0.15f)
        normalize(bass, 0.34f)
        normalize(chords, 0.24f)
        normalize(arp, 0.18f)
        normalize(melody, 0.27f)

        val layers = Array(Layer.COUNT) { FloatArray(0) }
        layers[Layer.DRUMS] = drums
        layers[Layer.BASS] = bass
        layers[Layer.CHORDS] = chords
        layers[Layer.MELODY] = melody
        layers[Layer.HATS] = hats
        layers[Layer.ARP] = arp
        return LoopStems(layers, frames, style.bpm)
    }

    /**
     * 리드 멜로디. 랜덤 워크 대신 2마디 동기(motif)를 만들고
     * A(동기) → A′(끝을 살짝 바꾼 변형) → B(응답) → A″(으뜸음으로 해결) 순서로 배치해 귀에 남게 한다.
     * 리프 모드(EDM)는 1마디 리프를 반복하고 마지막 마디만 바꾼다.
     */
    private fun composeLead(prog: Progression, rnd: Random, riff: Boolean): List<Note> {
        val scale = prog.scale
        val top = scale.size - 1
        val notes = mutableListOf<Note>()

        if (riff) {
            val r = RIFFS[rnd.nextInt(RIFFS.size)]
            val degrees = IntArray(r.size) { rnd.nextInt(2, top - 1) }
            for (bar in 0 until 8) {
                val shift = if (bar == 7) 1 else 0
                r.forEachIndexed { i, (b, len) ->
                    notes += Note(bar * 4 + b, len, scale[(degrees[i] + shift).coerceIn(0, top)])
                }
            }
            return notes
        }

        val motifIdx = rnd.nextInt(RHYTHMS.size)
        val motifRhythm = RHYTHMS[motifIdx]
        val answerRhythm = RHYTHMS[(motifIdx + 1) % RHYTHMS.size]
        var d = rnd.nextInt(2, 5)
        val motif = IntArray(motifRhythm.size) { i ->
            if (i > 0) d = (d + rnd.nextInt(-2, 3)).coerceIn(0, top)
            d
        }
        var a = (motif.last() + 2).coerceIn(0, top)
        val answer = IntArray(answerRhythm.size) { i ->
            if (i > 0) a = (a + rnd.nextInt(-2, 3)).coerceIn(0, top)
            a
        }
        val varied = motif.copyOf().also { v ->
            if (v.size >= 2) {
                v[v.size - 1] = (v[v.size - 1] - 1).coerceAtLeast(0)
                v[v.size - 2] = (v[v.size - 2] + 1).coerceAtMost(top)
            }
        }
        val resolved = motif.copyOf().also { v -> v[v.size - 1] = 0 }   // 음계 첫 음 = 으뜸음

        fun place(unit: Int, rhythm: List<Pair<Double, Double>>, degrees: IntArray) {
            rhythm.forEachIndexed { i, (b, len) -> notes += Note(unit * 8 + b, len, scale[degrees[i].coerceIn(0, top)]) }
        }
        place(0, motifRhythm, motif)
        place(1, motifRhythm, varied)
        place(2, answerRhythm, answer)
        place(3, motifRhythm, resolved)
        return notes
    }

    // ---- 악기 ----------------------------------------------------------------

    private fun addSynthBass(buf: FloatArray, startSec: Double, durSec: Double, midi: Int, cutoffHz: Double) {
        val start = (startSec * sampleRate).toInt()
        val n = ((durSec + 0.05) * sampleRate).toInt()
        val hz = midiToHz(midi)
        val saw = Osc(sampleRate)
        val sub = Osc(sampleRate)
        val lp = LowPass2(sampleRate)
        for (i in 0 until n) {
            val t = i.toDouble() / sampleRate
            val env = adsr(t, durSec, 0.003, 0.12, 0.7, 0.04)
            val raw = 0.7 * saw.saw(hz) + 0.3 * sub.square(hz / 2)
            val y = lp.process(raw, cutoffHz * (1 + 2.0 * exp(-t / 0.06)))
            buf[(start + i) % buf.size] += (0.6 * y * env).toFloat()
        }
    }

    private fun addPad(buf: FloatArray, startSec: Double, durSec: Double, hz: Double) {
        val start = (startSec * sampleRate).toInt()
        val n = ((durSec + 0.6) * sampleRate).toInt()
        val a = Osc(sampleRate, 0.0)
        val b = Osc(sampleRate, 0.37)
        val lp = LowPass2(sampleRate)
        val detune = 1.0058   // ±0.1반음: 두 소리가 살짝 어긋나며 넓게 퍼진다
        for (i in 0 until n) {
            val t = i.toDouble() / sampleRate
            val env = adsr(t, durSec, 0.3, 0.6, 0.8, 0.5)
            val raw = 0.5 * (a.saw(hz * detune) + b.saw(hz / detune))
            buf[(start + i) % buf.size] += (0.25 * lp.process(raw, 2600.0) * env).toFloat()
        }
    }

    private fun addStab(buf: FloatArray, startSec: Double, durSec: Double, hz: Double) {
        val start = (startSec * sampleRate).toInt()
        val n = ((durSec + 0.08) * sampleRate).toInt()
        val a = Osc(sampleRate, 0.0)
        val b = Osc(sampleRate, 0.5)
        val lp = LowPass2(sampleRate)
        for (i in 0 until n) {
            val t = i.toDouble() / sampleRate
            val env = adsr(t, durSec, 0.002, 0.1, 0.5, 0.06)
            val raw = 0.5 * (a.saw(hz * 1.004) + b.saw(hz / 1.004))
            buf[(start + i) % buf.size] += (0.3 * lp.process(raw, 1200.0 + 3800.0 * exp(-t / 0.08)) * env).toFloat()
        }
    }

    private fun addPluck(buf: FloatArray, startSec: Double, durSec: Double, hz: Double) {
        val start = (startSec * sampleRate).toInt()
        val n = ((durSec + 0.05) * sampleRate).toInt()
        val osc = Osc(sampleRate)
        val lp = LowPass2(sampleRate)
        for (i in 0 until n) {
            val t = i.toDouble() / sampleRate
            val env = adsr(t, durSec, 0.002, 0.12, 0.25, 0.05)
            buf[(start + i) % buf.size] += (0.3 * lp.process(osc.square(hz), 900.0 + 4000.0 * exp(-t / 0.05)) * env).toFloat()
        }
    }

    private fun addLead(buf: FloatArray, startSec: Double, durSec: Double, hz: Double) {
        val start = (startSec * sampleRate).toInt()
        val n = ((durSec + 0.1) * sampleRate).toInt()
        val saw = Osc(sampleRate)
        val sq = Osc(sampleRate, 0.25)
        val lp = LowPass2(sampleRate)
        for (i in 0 until n) {
            val t = i.toDouble() / sampleRate
            val vib = if (t > 0.15) 1 + 0.004 * sin(2 * PI * 5.5 * t) else 1.0   // 길게 끄는 음에만 약한 비브라토
            val f = hz * vib
            val raw = 0.6 * saw.saw(f) + 0.4 * sq.square(f)
            val env = adsr(t, durSec, 0.008, 0.2, 0.75, 0.08)
            buf[(start + i) % buf.size] += (0.3 * lp.process(raw, 2400.0 + 2000.0 * exp(-t / 0.12)) * env).toFloat()
        }
    }

    companion object {
        /** 2마디(8박) 리듬 템플릿: (시작 박, 길이 박) */
        private val RHYTHMS: List<List<Pair<Double, Double>>> = listOf(
            listOf(0.0 to 0.5, 0.75 to 0.5, 1.5 to 1.0, 3.0 to 0.5, 3.5 to 0.5, 4.0 to 1.5, 6.0 to 1.5),
            listOf(0.0 to 1.0, 1.0 to 0.5, 1.5 to 0.5, 2.0 to 1.5, 4.0 to 0.5, 4.5 to 0.5, 5.0 to 0.5, 5.5 to 2.0),
            listOf(0.5 to 0.5, 1.0 to 0.5, 1.5 to 1.0, 2.5 to 0.5, 3.0 to 1.0, 4.5 to 0.5, 5.0 to 1.0, 6.0 to 1.5),
        )

        /** 1마디(4박) EDM 리프 템플릿 */
        private val RIFFS: List<List<Pair<Double, Double>>> = listOf(
            listOf(0.0 to 0.25, 0.5 to 0.25, 0.75 to 0.25, 1.5 to 0.25, 2.0 to 0.5, 3.0 to 0.25, 3.5 to 0.25),
            listOf(0.0 to 0.5, 0.75 to 0.25, 1.5 to 0.25, 2.0 to 0.25, 2.5 to 0.5, 3.5 to 0.25),
        )

        private val A_MINOR_PENTA = intArrayOf(69, 72, 74, 76, 79, 81, 84, 86)
        private val E_MINOR_PENTA = intArrayOf(64, 67, 69, 71, 74, 76, 79, 81)
        private val F_MINOR_PENTA = intArrayOf(65, 68, 70, 72, 75, 77, 80, 82)

        /** 각성형: 80년대 밤 드라이브 느낌의 신스웨이브. */
        val SYNTHWAVE = Style(
            bpm = 112,
            progressions = listOf(
                // Am7 – Fmaj7 – C – G (i–VI–III–VII)
                Progression(
                    chords = listOf(intArrayOf(57, 60, 64, 67), intArrayOf(53, 57, 60, 64), intArrayOf(55, 60, 64, 67), intArrayOf(55, 59, 62, 67)),
                    bassRoots = intArrayOf(45, 41, 48, 43),
                    scale = A_MINOR_PENTA,
                ),
                // Em7 – Cmaj7 – G – D
                Progression(
                    chords = listOf(intArrayOf(52, 55, 59, 62), intArrayOf(55, 59, 60, 64), intArrayOf(55, 59, 62, 67), intArrayOf(54, 57, 62, 66)),
                    bassRoots = intArrayOf(40, 36, 43, 38),
                    scale = E_MINOR_PENTA,
                ),
            ),
            chordMode = ChordMode.PAD,
            stabBeats = doubleArrayOf(),
            bassMode = BassMode.OCTAVE_EIGHTHS,
            bassCutoffHz = 800.0,
            pumpBass = 0.5,
            pumpChords = 0.35,
            gatedSnare = true,
            openHats = false,
            arp = true,
            riffLead = false,
        )

        /** 파워: 가장 신나는 단계, 하우스·EDM. */
        val HOUSE = Style(
            bpm = 124,
            progressions = listOf(
                // Am – F – C – G
                Progression(
                    chords = listOf(intArrayOf(60, 64, 69), intArrayOf(60, 65, 69), intArrayOf(60, 64, 67), intArrayOf(59, 62, 67)),
                    bassRoots = intArrayOf(45, 41, 48, 43),
                    scale = A_MINOR_PENTA,
                ),
                // Fm – D♭ – A♭ – E♭
                Progression(
                    chords = listOf(intArrayOf(60, 65, 68), intArrayOf(61, 65, 68), intArrayOf(60, 63, 68), intArrayOf(58, 63, 67)),
                    bassRoots = intArrayOf(41, 37, 44, 39),
                    scale = F_MINOR_PENTA,
                ),
            ),
            chordMode = ChordMode.STAB,
            stabBeats = doubleArrayOf(0.75, 1.5, 2.75, 3.5),
            bassMode = BassMode.OFFBEAT,
            bassCutoffHz = 600.0,
            pumpBass = 0.6,
            pumpChords = 0.4,
            gatedSnare = false,
            openHats = true,
            arp = false,
            riffLead = true,
        )
    }
}
