/*
 * Woven Oak — Copyright (C) 2026 Paul Blanchard (Damonini) and Pepper —
 * Damonini and Pepper Software Development.
 * Free software under the GNU General Public License v3; see LICENSE.
 */
package com.damonini.wovenoak

import kotlin.math.abs
import kotlin.math.sqrt

/**
 * The ears of the calibration walk. Pure analysis over a sung
 * take — no Android, no storage — so the rules can be reasoned
 * about on their own:
 *
 * - A note is ACCEPTED when the singer actually sang (most
 *   frames voiced), landed near the target (within half a
 *   semitone at the median), and held it steady (the pitch
 *   barely wanders). Those three together are what "that note
 *   is in your range" means.
 * - From an accepted take we keep the STEADIEST SECOND — the
 *   one-second window whose pitch wanders least — as the note's
 *   sample for the bank.
 */
object Calibrate {

    private const val FRAME = 2048
    private const val HOP = 512

    data class Verdict(
        val accepted: Boolean,
        /** Median pitch heard, in fractional MIDI; NaN if unvoiced. */
        val heardMidi: Float,
        /** Median distance from the target, in cents. */
        val centsOff: Float,
        /** Pitch wander across the take, in semitones (lower = steadier). */
        val stability: Float,
        /** Share of frames the tracker heard as voiced, 0..1. */
        val voicedRatio: Float
    )

    private data class FramePitch(val midi: Float, val voiced: Boolean)

    private fun frames(take: ShortArray, sr: Int): List<FramePitch> {
        if (take.size < FRAME) return emptyList()
        val out = ArrayList<FramePitch>()
        var start = 0
        val frame = ShortArray(FRAME)
        while (start + FRAME <= take.size) {
            System.arraycopy(take, start, frame, 0, FRAME)
            val r = PitchTracker.analyse(frame, sr)
            out.add(FramePitch(r.midiFloat, r.voiced))
            start += HOP
        }
        return out
    }

    private fun median(xs: List<Float>): Float {
        if (xs.isEmpty()) return Float.NaN
        val s = xs.sorted()
        val m = s.size / 2
        return if (s.size % 2 == 1) s[m] else (s[m - 1] + s[m]) / 2f
    }

    private fun stdev(xs: List<Float>, mean: Float): Float {
        if (xs.size < 2) return 0f
        var acc = 0.0
        for (x in xs) acc += (x - mean) * (x - mean)
        return sqrt(acc / (xs.size - 1)).toFloat()
    }

    /** Judge one sung take against the guide note it aimed at. */
    fun judge(take: ShortArray, sr: Int, targetMidi: Int): Verdict {
        val fs = frames(take, sr)
        if (fs.isEmpty()) return Verdict(false, Float.NaN, Float.NaN, Float.NaN, 0f)
        val voiced = fs.filter { it.voiced }.map { it.midi }
        val ratio = voiced.size.toFloat() / fs.size
        if (voiced.isEmpty()) return Verdict(false, Float.NaN, Float.NaN, Float.NaN, ratio)
        val med = median(voiced)
        val cents = (med - targetMidi) * 100f
        val mean = voiced.average().toFloat()
        val stab = stdev(voiced, mean)
        val accepted = ratio >= 0.5f && abs(med - targetMidi) <= 0.5f && stab <= 0.35f
        return Verdict(accepted, med, cents, stab, ratio)
    }

    /** The note a free, comfortable sung take sits on, if any. */
    fun comfortableNote(take: ShortArray, sr: Int): Int? {
        val voiced = frames(take, sr).filter { it.voiced }.map { it.midi }
        if (voiced.size < 4) return null
        return Math.round(median(voiced))
    }

    /**
     * The steadiest one second of a take, sample-exact. Windows
     * are scored by pitch wander over their voiced frames; a
     * window needs at least half its frames voiced to qualify.
     * If nothing qualifies, the middle second is returned — the
     * singer was there, even if the tracker lost them.
     */
    fun steadiestSecond(take: ShortArray, sr: Int): ShortArray {
        val oneSec = sr
        if (take.size <= oneSec) return take.copyOf()
        val fs = frames(take, sr)
        val framesPerWindow = oneSec / HOP
        var bestStart = -1
        var bestScore = Float.MAX_VALUE
        var i = 0
        while (i + framesPerWindow <= fs.size) {
            val window = fs.subList(i, i + framesPerWindow)
            val voiced = window.filter { it.voiced }.map { it.midi }
            if (voiced.size >= framesPerWindow / 2) {
                val score = stdev(voiced, voiced.average().toFloat())
                if (score < bestScore) {
                    bestScore = score
                    bestStart = i * HOP
                }
            }
            i++
        }
        if (bestStart < 0) bestStart = (take.size - oneSec) / 2
        val end = minOf(bestStart + oneSec, take.size)
        return take.copyOfRange(bestStart, end)
    }
}
