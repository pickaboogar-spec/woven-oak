/*
 * Woven Oak — Copyright (C) 2026 Paul Blanchard (Damonini) and Pepper —
 * Damonini and Pepper Software Development.
 * Free software under the GNU General Public License v3; see LICENSE.
 */
package com.damonini.wovenoak

import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.pow
import kotlin.math.roundToInt
import kotlin.math.sin

/**
 * Retune — stage 1 of the voice plan (VOICE-PLAN.md), the piece
 * Auto-Tune is famous for, done our way and offline: analyse a
 * recorded take frame by frame with the app's own pitch tracker,
 * pull each sung note toward the nearest note of a chosen scale
 * by a strength the singer controls, and resynthesize with a
 * granular time-domain pitch shifter. Corrections are usually
 * under a semitone, where this shifter stays honest to the
 * voice. The original take is never touched — retune renders a
 * new one.
 */
object Retune {

    val SCALES: List<Pair<String, IntArray>> = listOf(
        "Major" to intArrayOf(0, 2, 4, 5, 7, 9, 11),
        "Minor" to intArrayOf(0, 2, 3, 5, 7, 8, 10),
        "Chromatic (nearest note)" to IntArray(12) { it }
    )

    private const val FRAME = 2048
    private const val HOP = 512
    private const val GRAIN = 1024

    /**
     * @param rootPc   key root as a pitch class, 0 = C … 11 = B
     * @param scale    pitch-class offsets of the scale
     * @param strength 0f = natural take, 1f = fully snapped
     */
    fun retune(
        pcm: ShortArray,
        rootPc: Int,
        scale: IntArray,
        strength: Float
    ): ShortArray {
        if (pcm.size < FRAME || strength <= 0f) return pcm
        val sr = LoopPad.SAMPLE_RATE

        // 1. The correction curve: semitones to shift, per hop.
        val hops = (pcm.size - FRAME) / HOP + 1
        val raw = FloatArray(hops)
        val frame = ShortArray(FRAME)
        var last = 0f
        for (h in 0 until hops) {
            System.arraycopy(pcm, h * HOP, frame, 0, FRAME)
            val r = PitchTracker.analyse(frame, sr)
            if (r.voiced) {
                val target = nearestScaleNote(r.midiFloat, rootPc, scale)
                last = (target - r.midiFloat) * strength
            }
            raw[h] = last // unvoiced frames hold the last correction
        }
        // 2. Glide, don't jump: smooth the curve (~5 hops ≈ 58 ms).
        val smooth = FloatArray(hops)
        val win = 5
        for (h in 0 until hops) {
            var sum = 0f; var n = 0
            for (k in -win / 2..win / 2) {
                val j = h + k
                if (j in 0 until hops) { sum += raw[j]; n++ }
            }
            smooth[h] = sum / n
        }
        fun correctionAt(samplePos: Int): Float {
            val h = (samplePos / HOP).coerceIn(0, hops - 1)
            return smooth[h]
        }

        // 3. Granular shift: grains resampled by the local ratio,
        //    overlap-added at the original hop under a Hann window,
        //    so the take keeps its length and gains the new pitch.
        val out = FloatArray(pcm.size + GRAIN * 2)
        val norm = FloatArray(out.size)
        val window = FloatArray(GRAIN) { i ->
            (0.5 - 0.5 * kotlin.math.cos(2.0 * PI * i / (GRAIN - 1))).toFloat()
        }
        var pos = 0
        while (pos < pcm.size) {
            val ratio = 2.0.pow(correctionAt(pos) / 12.0)
            val gl = (GRAIN * ratio).toInt().coerceAtLeast(64)
            for (j in 0 until gl) {
                val srcPos = pos + j / ratio
                val i0 = srcPos.toInt()
                if (i0 >= pcm.size) break
                val i1 = minOf(i0 + 1, pcm.size - 1)
                val frac = (srcPos - i0).toFloat()
                val s = (pcm[i0] * (1f - frac) + pcm[i1] * frac) / 32768f
                val w = window[(j * GRAIN / gl).coerceIn(0, GRAIN - 1)]
                out[pos + j] += s * w
                norm[pos + j] += w
            }
            pos += GRAIN / 2
        }
        val result = ShortArray(pcm.size)
        for (i in 0 until pcm.size) {
            val v = if (norm[i] > 0.05f) out[i] / norm[i] else pcm[i] / 32768f
            result[i] = (v.coerceIn(-1f, 1f) * 32767f).toInt().toShort()
        }
        return result
    }

    /** The scale note nearest to a continuous pitch. */
    private fun nearestScaleNote(midiFloat: Float, rootPc: Int, scale: IntArray): Float {
        var best = Float.MAX_VALUE
        var bestNote = midiFloat.roundToInt()
        val around = midiFloat.roundToInt()
        for (oct in -1..1) {
            for (pc in scale) {
                // Candidate note with this pitch class nearest the pitch.
                val base = ((around / 12) + oct) * 12 + ((pc + rootPc) % 12)
                for (cand in intArrayOf(base - 12, base, base + 12)) {
                    val d = abs(cand - midiFloat)
                    if (d < best) { best = d; bestNote = cand }
                }
            }
        }
        return bestNote.toFloat()
    }
}
