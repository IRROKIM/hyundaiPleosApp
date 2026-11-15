package com.routesoundtrack.audio

/** 곡을 이루는 층 번호. 모든 렌더러(로우파이·전자음악)와 믹서가 같은 번호를 쓴다. */
object Layer {
    const val DRUMS = 0    // 킥·스네어·클랩
    const val BASS = 1
    const val CHORDS = 2   // 코드·패드·스탭
    const val MELODY = 3   // 멜로디·리드
    const val HATS = 4     // 하이햇
    const val ARP = 5      // 아르페지오
    const val COUNT = 6

    /** 좌우 배치(스테레오 폭). */
    val PAN_L = floatArrayOf(1f, 1f, 0.95f, 0.8f, 0.7f, 1f)
    val PAN_R = floatArrayOf(1f, 1f, 0.95f, 1f, 1f, 0.7f)
}

/** 렌더러가 만든 루프 하나 분량의 층들. 쓰지 않는 층은 빈 배열(size 0)이다. */
class LoopStems(val layers: Array<FloatArray>, val loopFrames: Int, val bpm: Int)
