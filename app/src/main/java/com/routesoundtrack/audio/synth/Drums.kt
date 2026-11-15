package com.routesoundtrack.audio.synth

import kotlin.math.PI
import kotlin.math.exp
import kotlin.math.sin
import kotlin.random.Random

/** 드럼 소리 합성. 모두 버퍼에 더하고, 끝을 넘으면 앞으로 감는다. */
class Drums(private val sampleRate: Int) {

    /** 킥: 높은 음에서 낮은 음으로 빠르게 떨어지는 사인파. */
    fun kick(buf: FloatArray, startSec: Double, amp: Double = 0.9, fromHz: Double = 150.0, toHz: Double = 50.0, decaySec: Double = 0.28) {
        val start = (startSec * sampleRate).toInt()
        val n = (0.45 * sampleRate).toInt()
        var phase = 0.0
        for (i in 0 until n) {
            val t = i.toDouble() / sampleRate
            val hz = toHz + (fromHz - toHz) * exp(-t / 0.03)
            phase += 2 * PI * hz / sampleRate
            val attack = if (t < 0.003) t / 0.003 else 1.0
            val env = attack * exp(-t / decaySec)
            buf[(start + i) % buf.size] += (amp * sin(phase) * env).toFloat()
        }
    }

    /** 스네어. gated=true면 80년대식 "게이트 리버브": 꼬리가 길게 울리다 뚝 끊긴다. */
    fun snare(buf: FloatArray, startSec: Double, rnd: Random, amp: Double = 0.6, gated: Boolean = false) {
        val start = (startSec * sampleRate).toInt()
        val len = if (gated) 0.26 else 0.18
        val n = (len * sampleRate).toInt()
        var prev = 0.0
        for (i in 0 until n) {
            val t = i.toDouble() / sampleRate
            val white = rnd.nextDouble() * 2 - 1
            val noise = white - 0.5 * prev         // 저음을 약간 깎은 잡음
            prev = white
            val noiseEnv = if (gated) {
                val gate = if (t > len - 0.012) (len - t) / 0.012 else 1.0
                exp(-t / 0.22) * gate
            } else {
                exp(-t / 0.09)
            }
            val tone = sin(2 * PI * 190 * t) * exp(-t / 0.06)
            buf[(start + i) % buf.size] += (amp * (0.55 * noise * noiseEnv + 0.45 * tone)).toFloat()
        }
    }

    /** 클랩: 짧은 잡음 세 번(사람 여러 명이 치는 느낌) + 짧은 꼬리. */
    fun clap(buf: FloatArray, startSec: Double, rnd: Random, amp: Double = 0.6) {
        val start = (startSec * sampleRate).toInt()
        val n = (0.2 * sampleRate).toInt()
        var prev = 0.0
        for (i in 0 until n) {
            val t = i.toDouble() / sampleRate
            val white = rnd.nextDouble() * 2 - 1
            val hp = white - prev
            prev = white
            val burst = when {
                t < 0.010 -> exp(-t / 0.003)
                t < 0.020 -> exp(-(t - 0.010) / 0.003)
                t < 0.030 -> exp(-(t - 0.020) / 0.003)
                else -> 0.0
            }
            val tail = if (t >= 0.020) exp(-(t - 0.020) / 0.07) else 0.0
            buf[(start + i) % buf.size] += (amp * hp * 0.5 * (burst + tail)).toFloat()
        }
    }

    /** 하이햇: 고역만 남긴 잡음. open=true면 길게 울린다. */
    fun hat(buf: FloatArray, startSec: Double, rnd: Random, amp: Double, open: Boolean = false) {
        val start = (startSec * sampleRate).toInt()
        val decay = if (open) 0.16 else 0.03
        val n = ((decay * 5) * sampleRate).toInt()
        var prev = 0.0
        for (i in 0 until n) {
            val t = i.toDouble() / sampleRate
            val white = rnd.nextDouble() * 2 - 1
            val hp = white - prev
            prev = white
            buf[(start + i) % buf.size] += (amp * hp * exp(-t / decay)).toFloat()
        }
    }
}
