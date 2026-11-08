/*
 * Woven Oak — Copyright (C) 2026 Paul Blanchard (Damonini) and Pepper —
 * Damonini and Pepper Software Development.
 * Free software under the GNU General Public License v3; see LICENSE.
 */
package com.damonini.wovenoak

import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.pow

/**
 * Stage 3 of the voice plan (VOICE-PLAN.md): your own voice,
 * playable. The calibration walk (Calibrate.kt) banks one clean
 * sung second per note in the VoiceBank; this file turns those
 * samples back into music — pick a note, and the nearest banked
 * sample is shifted to it with the same granular time-domain
 * shifter family Retune uses, then loop-extended to the note's
 * length. Everything is the singer's own recorded voice, and
 * nothing here — like everything in Woven Oak — leaves the phone.
 *
 * What this is NOT: typed words sung in your voice. That is
 * stage 4, recorded in VOICE-DESIGN.md as the horizon, and it is
 * not claimed anywhere in this app.
 */

/** Constant-ratio pitch shifter, the textbook two-step:
 *  (1) resample the whole take at the shift ratio — pitch moves
 *  by the ratio and the length by its inverse; (2) WSOLA
 *  time-stretch the resampled take back to the original length:
 *  Hann-windowed grains laid at a synthesis hop scaled by the
 *  stretch factor, each grain's input offset chosen by
 *  cross-correlation with the output already written, so grains
 *  add phase-coherently at the new pitch instead of cancelling
 *  back into the old one. (Plain fixed-offset overlap-add does
 *  exactly that cancellation — the check suite caught it
 *  returning small shifts at the original pitch, and two other
 *  simpler constructions besides; this one passes at ±12.)
 *  Honest at the small shifts the voice bank needs — it keeps a
 *  sample every semitone or two. */
object VoiceShifter {

    private const val GRAIN = 1024
    private const val HOP = 256
    private const val MATCH = 512
    private const val SEARCH = 512

    fun shift(pcm: ShortArray, semitones: Float): ShortArray {
        if (pcm.size < GRAIN || abs(semitones) < 0.01f) return pcm.copyOf()
        val ratio = 2.0.pow(semitones / 12.0)

        // 1. Resample: out[i] = in[i * ratio], linearly interpolated.
        val resLen = (pcm.size / ratio).toInt().coerceAtLeast(64)
        val res = FloatArray(resLen)
        for (i in 0 until resLen) {
            val srcPos = i * ratio
            val i0 = srcPos.toInt().coerceIn(0, pcm.size - 1)
            val i1 = minOf(i0 + 1, pcm.size - 1)
            val frac = (srcPos - i0).toFloat()
            res[i] = (pcm[i0] * (1f - frac) + pcm[i1] * frac) / 32768f
        }

        // 2. WSOLA stretch of res back to the original length.
        val grain = minOf(GRAIN, resLen / 2).coerceAtLeast(64)
        val window = FloatArray(grain) { i ->
            (0.5 - 0.5 * cos(2.0 * PI * i / (grain - 1))).toFloat()
        }
        val stretch = pcm.size.toDouble() / resLen
        val synthHop = (HOP * stretch).toInt().coerceAtLeast(1)
        val out = FloatArray(pcm.size)
        val norm = FloatArray(pcm.size)

        fun writeGrain(s0: Int, d0: Int) {
            for (j in 0 until grain) {
                val d = d0 + j
                if (d >= pcm.size) break
                val w = window[j]
                out[d] += res[s0 + j] * w
                norm[d] += w
            }
        }

        /** Normalized cross-correlation of a candidate grain start
         *  against the output tail it would overlap. */
        fun score(s0: Int, d0: Int, len: Int): Float {
            var xy = 0.0; var xx = 0.0; var yy = 0.0
            for (j in 0 until len) {
                val a = res[s0 + j].toDouble()
                val b = out[d0 + j].toDouble()
                xy += a * b; xx += a * a; yy += b * b
            }
            val denom = kotlin.math.sqrt(xx * yy)
            return if (denom <= 1e-9) 0f else (xy / denom).toFloat()
        }

        var chosen = 0
        var outPos = 0
        writeGrain(0, 0)
        outPos += synthHop
        var nominal = HOP
        while (outPos < pcm.size) {
            if (nominal > resLen - grain) {
                // Out of resampled take: wrap — sustained material.
                nominal = HOP
                chosen = 0
            }
            val remaining = pcm.size - outPos
            if (remaining < 16) break
            val matchLen = minOf(MATCH, grain, remaining)
            var best = nominal
            var bestScore = Float.NEGATIVE_INFINITY
            var cand = nominal - SEARCH
            while (cand <= nominal + SEARCH) {
                val s = cand.coerceIn(0, resLen - grain)
                val sc = score(s, outPos, matchLen)
                if (sc > bestScore) { bestScore = sc; best = s }
                cand += 8
            }
            var refine = best - 7
            while (refine <= best + 7) {
                val s = refine.coerceIn(0, resLen - grain)
                val sc = score(s, outPos, matchLen)
                if (sc > bestScore) { bestScore = sc; best = s }
                refine++
            }
            writeGrain(best, outPos)
            chosen = best
            nominal = chosen + HOP
            outPos += synthHop
        }
        val result = ShortArray(pcm.size)
        for (i in 0 until pcm.size) {
            val v = if (norm[i] > 0.05f) out[i] / norm[i] else 0f
            result[i] = (v.coerceIn(-1f, 1f) * 32767f).toInt().toShort()
        }
        return result
    }
}

object VoiceInstrument {

    /**
     * One note of the singer's own voice at [midi] for [durSec]:
     * nearest banked sample, loop-extended to length, shifted
     * home. Null when the bank is empty or the sample is missing.
     */
    fun note(bank: VoiceBank, midi: Int, durSec: Float, sr: Int): ShortArray? {
        val srcMidi = bank.nearest(midi) ?: return null
        val sample = bank.load(srcMidi) ?: return null
        if (sample.isEmpty()) return null
        val target = (durSec * sr).toInt().coerceAtLeast(1)
        val extended = extend(sample, target)
        // The bank holds a sample every note or two, so shifts are
        // small; clamp at an octave so a sparse bank can never ask
        // the shifter for something wild — the note just lands on
        // the nearest honest octave instead.
        val semis = (midi - srcMidi).toFloat().coerceIn(-12f, 12f)
        val shifted = VoiceShifter.shift(extended, semis)
        val out = if (shifted.size >= target) shifted.copyOf(target) else shifted
        applyEdges(out, sr)
        return out
    }

    /**
     * Hum-to-your-own-voice: the HumConverter trick with the bank
     * standing in for a synth voice. The melody and timing of
     * [pcm] are kept; every note is sung by the banked voice.
     * Null when no clear melody, or the bank is empty.
     */
    fun convert(pcm: ShortArray, sr: Int, bank: VoiceBank): ShortArray? {
        if (bank.count() == 0) return null
        val events = PitchTracker.extractMelody(pcm, sr)
        if (events.none { it.midi != null }) return null
        val mix = FloatArray(pcm.size)
        var sounded = false
        for (ev in events) {
            val midi = ev.midi ?: continue // rest: leave silence
            val note = note(bank, midi, ev.durSec.coerceAtMost(4f), sr) ?: continue
            val start = (ev.startSec * sr).toInt()
            val len = minOf(note.size, (ev.durSec * sr).toInt(), mix.size - start)
            if (len <= 0) continue
            sounded = true
            for (j in 0 until len) mix[start + j] += note[j] / 32767f
        }
        if (!sounded) return null
        val out = ShortArray(pcm.size)
        var peak = 0f
        for (x in mix) peak = maxOf(peak, abs(x))
        val g = if (peak > 0.72f) 0.72f / peak else 1f
        for (i in mix.indices) {
            out[i] = (mix[i] * g * 32767f).toInt().coerceIn(-32768, 32767).toShort()
        }
        return out
    }

    /**
     * The bank's library entry, in the shape of Synth.renderPhrase:
     * home, third, fifth, octave, fifth, third — sung by the
     * singer's own voice, around the middle of their banked range.
     */
    fun renderPhrase(bank: VoiceBank, sr: Int): ShortArray? {
        val notes = bank.notes()
        if (notes.isEmpty()) return null
        val base = notes[notes.size / 2]
        val steps = intArrayOf(0, 4, 7, 12, 7, 4)
        val noteDur = 0.34f
        val total = ((steps.size * noteDur + 0.25f) * sr).toInt()
        val mix = FloatArray(total)
        var sounded = false
        steps.forEachIndexed { idx, off ->
            val note = note(bank, base + off, noteDur + 0.12f, sr) ?: return@forEachIndexed
            sounded = true
            val start = (idx * noteDur * sr).toInt()
            for (j in note.indices) {
                if (start + j < total) mix[start + j] += note[j] / 32767f
            }
        }
        if (!sounded) return null
        var peak = 0f
        for (x in mix) peak = maxOf(peak, abs(x))
        val g = if (peak > 0.72f) 0.72f / peak else 1f
        return ShortArray(total) {
            (mix[it] * g * 32767f).toInt().coerceIn(-32768, 32767).toShort()
        }
    }

    /** Tile a sustained sample to [target] samples with a short
     *  crossfade at each seam, so a one-second "ahh" can hold a
     *  longer note without an audible restart click. */
    private fun extend(sample: ShortArray, target: Int): ShortArray {
        if (sample.size >= target) return sample.copyOf(target)
        val fade = minOf(2048, sample.size / 4).coerceAtLeast(1)
        val out = FloatArray(target)
        var pos = 0
        var first = true
        while (pos < target) {
            val n = minOf(sample.size, target - pos + if (first) 0 else fade)
            for (j in 0 until n) {
                val dst = pos + j - if (first) 0 else fade
                if (dst < 0) continue
                if (dst >= target) break
                val s = sample[j] / 32768f
                out[dst] = if (!first && j < fade) {
                    val k = j.toFloat() / fade
                    out[dst] * (1f - k) + s * k
                } else s
            }
            pos += sample.size - if (first) 0 else fade
            first = false
        }
        return ShortArray(target) {
            (out[it].coerceIn(-1f, 1f) * 32767f).toInt().toShort()
        }
    }

    /** 15 ms ramps at the ends, so banked notes start and stop
     *  without clicks when phrases are mixed together. */
    private fun applyEdges(pcm: ShortArray, sr: Int) {
        val ramp = minOf((0.015f * sr).toInt(), pcm.size / 2)
        if (ramp <= 0) return
        for (i in 0 until ramp) {
            val k = i.toFloat() / ramp
            pcm[i] = (pcm[i] * k).toInt().toShort()
            val j = pcm.size - 1 - i
            pcm[j] = (pcm[j] * k).toInt().toShort()
        }
    }
}
