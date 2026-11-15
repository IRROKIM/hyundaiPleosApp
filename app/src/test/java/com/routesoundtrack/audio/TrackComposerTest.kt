package com.routesoundtrack.audio

import com.routesoundtrack.domain.model.Mood
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class TrackComposerTest {

    @Test
    fun `TC-05 분위기가 바뀌면 켜지는 레이어 조합이 바뀐다`() {
        assertEquals(setOf(Layer.CHORDS, Layer.MELODY), TrackComposer.layersFor(Mood.CHILL, Section.MAIN))
        assertEquals(
            setOf(Layer.DRUMS, Layer.HATS, Layer.BASS, Layer.CHORDS, Layer.ARP, Layer.MELODY),
            TrackComposer.layersFor(Mood.UPBEAT, Section.MAIN),
        )
        assertEquals(
            setOf(Layer.DRUMS, Layer.HATS, Layer.BASS, Layer.CHORDS, Layer.MELODY),
            TrackComposer.layersFor(Mood.HYPE, Section.MAIN),
        )
    }

    @Test
    fun `중간 로우파이는 조용한 로우파이에 드럼·베이스를 더한다`() {
        val quiet = TrackComposer.layersFor(Mood.CHILL, Section.MAIN)
        val groove = TrackComposer.layersFor(Mood.GROOVE, Section.MAIN)
        assertTrue(groove.containsAll(quiet))
        assertTrue(Layer.DRUMS in groove && Layer.BASS in groove)
        assertTrue(Layer.DRUMS !in quiet)
        assertTrue(TrackComposer.bpmFor(Mood.CHILL) < TrackComposer.bpmFor(Mood.GROOVE))
        assertTrue(TrackComposer.bpmFor(Mood.GROOVE) < TrackComposer.bpmFor(Mood.UPBEAT))
    }

    @Test
    fun `곡 길이는 90초 이상 120초 이하`() {
        for (mood in Mood.entries) {
            val loopSec = StemSynth.BEATS_PER_LOOP * 60.0 / TrackComposer.bpmFor(mood)
            val sec = TrackComposer.arrangementFor(mood, loopSec).size * loopSec
            assertTrue("$mood: $sec s", sec in 90.0..120.0)
        }
    }

    @Test
    fun `각성형과 파워는 빌드업과 브레이크가 있는 편곡이다`() {
        for (mood in listOf(Mood.UPBEAT, Mood.HYPE)) {
            val loopSec = StemSynth.BEATS_PER_LOOP * 60.0 / TrackComposer.bpmFor(mood)
            assertEquals(
                listOf(Section.INTRO, Section.BUILD, Section.MAIN, Section.BREAK, Section.MAIN, Section.OUTRO),
                TrackComposer.arrangementFor(mood, loopSec),
            )
        }
    }

    @Test
    fun `같은 seed는 같은 곡을 만든다`() {
        val lofi = StemSynth(sampleRate = 8_000)   // 테스트를 빠르게 하려고 낮은 샘플레이트
        assertTrue(lofi.render(StemSynth.CHILL, 42).layers[Layer.MELODY].contentEquals(lofi.render(StemSynth.CHILL, 42).layers[Layer.MELODY]))
        val electronic = ElectronicRenderer(sampleRate = 8_000)
        assertTrue(
            electronic.render(ElectronicRenderer.SYNTHWAVE, 7).layers[Layer.MELODY]
                .contentEquals(electronic.render(ElectronicRenderer.SYNTHWAVE, 7).layers[Layer.MELODY])
        )
    }
}
