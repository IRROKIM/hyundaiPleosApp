package com.routesoundtrack.audio

import com.routesoundtrack.audio.Layer.ARP
import com.routesoundtrack.audio.Layer.BASS
import com.routesoundtrack.audio.Layer.CHORDS
import com.routesoundtrack.audio.Layer.DRUMS
import com.routesoundtrack.audio.Layer.HATS
import com.routesoundtrack.audio.Layer.MELODY
import com.routesoundtrack.domain.model.Mood
import kotlin.math.ceil
import kotlin.math.max

/** 곡의 구간. 한 구간 = 루프 1개(8마디). */
enum class Section { INTRO, BUILD, MAIN, BREAK, OUTRO }

/** 믹서가 재생할 한 곡. 루프 하나 분량의 층 데이터 + 루프마다 켤 층 목록. */
class ComposedTrack(
    val mood: Mood,
    val title: String,
    val layers: Array<FloatArray>,
    val loopFrames: Int,
    val loopGains: Array<FloatArray>,   // [루프 번호][층] = 0f 또는 1f
    val totalFrames: Int,
    val fadeInFrames: Int,
    val fadeOutFrames: Int,
    val sampleRate: Int,
) {
    val durationSec: Double get() = totalFrames.toDouble() / sampleRate
}

/**
 * 무드와 seed로 90–120초짜리 곡을 만든다.
 * - 조용한·중간 로우파이·도착 곡: 로우파이 렌더러(StemSynth), intro → main… → outro
 * - 각성형(신스웨이브)·파워(EDM): 전자음악 렌더러, intro → build → main → break → main → outro (에너지 두 번 고조)
 */
class TrackComposer(sampleRate: Int = StemSynth.SAMPLE_RATE) {
    private val lofi = StemSynth(sampleRate)
    private val electronic = ElectronicRenderer(sampleRate)

    fun render(mood: Mood, seed: Long): LoopStems = when (mood) {
        Mood.CHILL -> lofi.render(StemSynth.CHILL, seed)
        Mood.GROOVE -> lofi.render(StemSynth.GROOVE, seed)
        Mood.ARRIVAL -> lofi.render(StemSynth.ARRIVAL, seed)
        Mood.UPBEAT -> electronic.render(ElectronicRenderer.SYNTHWAVE, seed)
        Mood.HYPE -> electronic.render(ElectronicRenderer.HOUSE, seed)
    }

    fun compose(mood: Mood, seed: Long, fadeOutMs: Int = 3_000): ComposedTrack {
        val stems = render(mood, seed)
        val sr = lofi.sampleRate
        val loopSec = stems.loopFrames.toDouble() / sr
        val sections = arrangementFor(mood, loopSec)
        val gains = Array(sections.size) { i ->
            val on = layersFor(mood, sections[i])
            FloatArray(Layer.COUNT) { layer -> if (layer in on) 1f else 0f }
        }
        return ComposedTrack(
            mood = mood,
            title = titleFor(mood, seed),
            layers = stems.layers,
            loopFrames = stems.loopFrames,
            loopGains = gains,
            totalFrames = stems.loopFrames * sections.size,
            fadeInFrames = 2 * sr,
            fadeOutFrames = fadeOutMs * sr / 1000,
            sampleRate = sr,
        )
    }

    companion object {
        const val MIN_TRACK_SEC = 90.0

        fun bpmFor(mood: Mood): Int = when (mood) {
            Mood.CHILL -> StemSynth.CHILL.bpm
            Mood.GROOVE -> StemSynth.GROOVE.bpm
            Mood.ARRIVAL -> StemSynth.ARRIVAL.bpm
            Mood.UPBEAT -> ElectronicRenderer.SYNTHWAVE.bpm
            Mood.HYPE -> ElectronicRenderer.HOUSE.bpm
        }

        /** 전체 길이가 90초 이상이 되는 최소 루프 수로 구간을 짠다. */
        fun arrangementFor(mood: Mood, loopSec: Double): List<Section> {
            val loops = max(3, ceil(MIN_TRACK_SEC / loopSec).toInt())
            val energetic = mood == Mood.UPBEAT || mood == Mood.HYPE
            if (!energetic || loops < 6) {
                return listOf(Section.INTRO) + List(loops - 2) { Section.MAIN } + Section.OUTRO
            }
            val mains = loops - 4                       // INTRO·BUILD·BREAK·OUTRO를 뺀 나머지
            val before = (mains + 1) / 2
            return listOf(Section.INTRO, Section.BUILD) +
                List(before) { Section.MAIN } + Section.BREAK +
                List(mains - before) { Section.MAIN } + Section.OUTRO
        }

        /** 무드·구간별로 켜는 층 (기획서 "레이어 조합" 표 + v0.2 전자음악 편곡). */
        fun layersFor(mood: Mood, section: Section): Set<Int> = when (mood) {
            Mood.CHILL, Mood.ARRIVAL -> when (section) {
                Section.INTRO, Section.BREAK -> setOf(CHORDS)
                Section.BUILD, Section.MAIN, Section.OUTRO -> setOf(CHORDS, MELODY)
            }
            // 중간 로우파이: 조용한 로우파이에 드럼·하이햇·베이스를 더한다. 처음과 끝은 코드+하이햇으로 가볍게.
            Mood.GROOVE -> when (section) {
                Section.INTRO, Section.OUTRO -> setOf(CHORDS, HATS)
                Section.BUILD, Section.MAIN -> setOf(DRUMS, HATS, BASS, CHORDS, MELODY)
                Section.BREAK -> setOf(CHORDS, MELODY)
            }
            Mood.UPBEAT -> when (section) {
                Section.INTRO -> setOf(CHORDS, ARP)
                Section.BUILD -> setOf(CHORDS, ARP, DRUMS, HATS, BASS)
                Section.MAIN -> setOf(DRUMS, HATS, BASS, CHORDS, ARP, MELODY)
                Section.BREAK -> setOf(CHORDS, MELODY, ARP)
                Section.OUTRO -> setOf(CHORDS, ARP, HATS)
            }
            Mood.HYPE -> when (section) {
                Section.INTRO -> setOf(DRUMS, HATS)
                Section.BUILD -> setOf(DRUMS, HATS, BASS, CHORDS)
                Section.MAIN -> setOf(DRUMS, HATS, BASS, CHORDS, MELODY)
                Section.BREAK -> setOf(CHORDS, MELODY)
                Section.OUTRO -> setOf(DRUMS, HATS)
            }
        }

        fun titleFor(mood: Mood, seed: Long): String {
            val n = ((seed % 100) + 100) % 100
            val style = when (mood) {
                Mood.CHILL -> "quiet lofi"
                Mood.GROOVE -> "lofi"
                Mood.ARRIVAL -> "arrival"
                Mood.UPBEAT -> "synthwave"
                Mood.HYPE -> "house"
            }
            return "%s #%02d".format(style, n)
        }
    }
}
