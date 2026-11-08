/*
 * Woven Oak — Copyright (C) 2026 Paul Blanchard (Damonini) and Pepper —
 * Damonini and Pepper Software Development.
 * Free software under the GNU General Public License v3; see LICENSE.
 */
package com.damonini.wovenoak

import kotlin.math.PI
import kotlin.math.exp
import kotlin.math.sin
import kotlin.random.Random

/**
 * The Chant renderer — Paul's template, designed 2026-11-07.
 *
 * It imitates a *mechanism*, never a song: a steady electronic
 * pulse, a processed voice chanting short phrases one to a beat,
 * the phrases stacking and cycling until the list itself becomes
 * the piece. The template ships empty — every word rendered here
 * was recorded by the user onto their own pads.
 *
 * Rendering is offline and sample-accurate: phrases are mixed
 * onto a beat grid into one PCM buffer, which a pad then loops
 * gaplessly like any other take.
 */
object ChantRenderer {

    /**
     * @param phrases the user's phrase takes, in pad order
     * @param anchor  an optional refrain take, layered every 4 beats
     * @param bpm     tempo of the beat grid
     * @param pulse   synthesize the electronic pulse (kick + hat)
     * @param robot   ring-modulate the voices (the robot treatment)
     */
    fun render(
        phrases: List<ShortArray>,
        anchor: ShortArray?,
        bpm: Int,
        pulse: Boolean,
        robot: Boolean
    ): ShortArray {
        val sr = LoopPad.SAMPLE_RATE
        val beat = sr * 60 / bpm.coerceIn(60, 200)
        val beatsPerBar = 16
        val totalBeats = beatsPerBar * 2
        val total = beat * totalBeats
        val mix = FloatArray(total)

        // Bar 1 runs the first half of the list; bar 2 runs it all.
        val half = phrases.subList(0, (phrases.size + 1) / 2)
        for (b in 0 until totalBeats) {
            val list = if (b < beatsPerBar) half else phrases
            val phrase = list[b % list.size]
            place(mix, phrase, b * beat, robot, 0.85f)
            if (anchor != null && b % 4 == 0) {
                place(mix, anchor, b * beat, robot, 0.8f)
            }
        }

        if (pulse) addPulse(mix, beat, totalBeats)

        // Gentle soft-clip so stacked voices stay loud, not harsh.
        val out = ShortArray(total)
        for (i in 0 until total) {
            val x = mix[i]
            val y = x / (1f + kotlin.math.abs(x)) * 1.5f
            out[i] = (y.coerceIn(-1f, 1f) * 32767f).toInt().toShort()
        }
        return out
    }

    /** Mix one phrase into the buffer at [start], optionally
     *  ring-modulated at 30 Hz — the classic robot-voice treatment,
     *  phase-continuous across the whole render. */
    private fun place(
        mix: FloatArray, phrase: ShortArray, start: Int,
        robot: Boolean, gain: Float
    ) {
        val sr = LoopPad.SAMPLE_RATE
        val n = minOf(phrase.size, mix.size - start)
        for (j in 0 until n) {
            var s = phrase[j] / 32768f
            if (robot) {
                val phase = 2.0 * PI * 30.0 * (start + j) / sr
                s *= sin(phase).toFloat()
            }
            mix[start + j] += s * gain
        }
    }

    /** An original synthesized pulse: a kick on every beat (sine
     *  sweep 110→42 Hz) and a noise hat on the offbeats. */
    private fun addPulse(mix: FloatArray, beat: Int, totalBeats: Int) {
        val sr = LoopPad.SAMPLE_RATE
        val rng = Random(42)
        val kickLen = (sr * 0.11).toInt()
        for (b in 0 until totalBeats) {
            val start = b * beat
            for (j in 0 until kickLen) {
                val t = j.toDouble() / sr
                val freq = 42.0 + 68.0 * exp(-t * 30.0)
                val env = exp(-t * 22.0)
                mix[start + j] += (sin(2.0 * PI * freq * t) * env * 0.85).toFloat()
            }
            val hatStart = start + beat / 2
            val hatLen = (sr * 0.03).toInt()
            var prev = 0f
            for (j in 0 until hatLen) {
                if (hatStart + j >= mix.size) break
                val white = rng.nextFloat() * 2f - 1f
                val hp = white - prev
                prev = white
                val env = 1f - j.toFloat() / hatLen
                mix[hatStart + j] += hp * env * 0.22f
            }
        }
    }
}
