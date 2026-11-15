package com.routesoundtrack.audio

import com.routesoundtrack.domain.model.Mood
import org.junit.Assert.assertTrue
import org.junit.Assume.assumeTrue
import org.junit.Test
import java.io.DataOutputStream
import java.io.File
import java.io.FileOutputStream
import kotlin.math.abs
import kotlin.math.log10
import kotlin.math.max
import kotlin.math.sqrt

/**
 * 개발용 미리듣기: 무드별 곡을 WAV로 저장하고 소리 지표를 출력한다.
 * 환경변수 RS_PREVIEW=1 일 때만 동작한다.
 *   RS_PREVIEW=1 ./gradlew testDebugUnitTest --tests "*TrackPreviewExport*"
 * 결과: app/build/preview/{chill,upbeat,hype,arrival}.wav
 */
class TrackPreviewExport {

    @Test
    fun exportAll() {
        assumeTrue("RS_PREVIEW=1 일 때만 실행", System.getenv("RS_PREVIEW") == "1")
        val dir = File("build/preview").apply { mkdirs() }
        val composer = TrackComposer()
        val seed = System.getenv("RS_SEED")?.toLongOrNull() ?: 42L
        for (mood in Mood.entries) {
            val track = composer.compose(mood, seed)
            val pcm = MixMath.renderOffline(track)
            val file = File(dir, "${mood.name.lowercase()}.wav")
            writeWav(file, pcm, track.sampleRate)
            report(track, pcm, file)
        }
    }

    private fun report(t: ComposedTrack, pcm: FloatArray, file: File) {
        val sections = TrackComposer.arrangementFor(t.mood, t.loopFrames.toDouble() / t.sampleRate)
        val sectionDb = sections.indices.map { i ->
            // 구간 앞뒤 1초(층 전환·페이드)는 빼고 잰다
            rmsDb(pcm, i * t.loopFrames + t.sampleRate, (i + 1) * t.loopFrames - t.sampleRate)
        }
        var peak = 0f
        for (v in pcm) peak = max(peak, abs(v))
        // 루프 이음매 클릭: 각 층의 마지막 샘플과 첫 샘플 차이
        val seam = t.layers.filter { it.isNotEmpty() }.maxOf { abs(it[0] - it[it.size - 1]) }
        println("=== ${file.absolutePath}")
        println("  ${t.title}  ${"%.1f".format(t.durationSec)} s  peak ${"%.3f".format(peak)}  seam jump ${"%.4f".format(seam)}")
        sections.forEachIndexed { i, s -> println("  ${s.name.padEnd(5)} ${"%.1f".format(sectionDb[i])} dBFS") }

        assertTrue("peak < 1.0", peak < 1.0f)
        assertTrue("seam click ${t.mood}: $seam", seam < 0.05f)
        if (t.mood == Mood.UPBEAT || t.mood == Mood.HYPE) {
            val db = sections.zip(sectionDb)
            val intro = db.first { it.first == Section.INTRO }.second
            val build = db.first { it.first == Section.BUILD }.second
            val main = db.filter { it.first == Section.MAIN }.minOf { it.second }
            val brk = db.first { it.first == Section.BREAK }.second
            assertTrue("energy arc ${t.mood}: intro $intro < build $build < main $main > break $brk",
                intro < build && build < main && main > brk)
        }
    }

    private fun rmsDb(pcm: FloatArray, fromFrame: Int, toFrame: Int): Double {
        var sum = 0.0
        var n = 0
        for (f in fromFrame until toFrame) {
            val l = pcm[2 * f]
            sum += l * l
            n++
        }
        return 20 * log10(sqrt(sum / max(1, n)) + 1e-12)
    }

    private fun writeWav(file: File, pcm: FloatArray, sampleRate: Int) {
        val dataBytes = pcm.size * 2
        DataOutputStream(FileOutputStream(file).buffered()).use { out ->
            fun intLE(v: Int) { out.write(v and 0xff); out.write((v shr 8) and 0xff); out.write((v shr 16) and 0xff); out.write((v shr 24) and 0xff) }
            fun shortLE(v: Int) { out.write(v and 0xff); out.write((v shr 8) and 0xff) }
            out.writeBytes("RIFF"); intLE(36 + dataBytes); out.writeBytes("WAVE")
            out.writeBytes("fmt "); intLE(16); shortLE(1); shortLE(2); intLE(sampleRate); intLE(sampleRate * 4); shortLE(4); shortLE(16)
            out.writeBytes("data"); intLE(dataBytes)
            for (v in pcm) shortLE((v.coerceIn(-1f, 1f) * 32767).toInt())
        }
    }
}
