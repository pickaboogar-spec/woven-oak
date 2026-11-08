/*
 * Woven Oak — Copyright (C) 2026 Paul Blanchard (Damonini) and Pepper —
 * Damonini and Pepper Software Development.
 * Free software under the GNU General Public License v3; see LICENSE.
 */
package com.damonini.wovenoak

import kotlin.math.abs
import kotlin.math.ln
import kotlin.math.roundToInt
import kotlin.math.sqrt

/** One analysed frame: the pitch found in a short slice of audio. */
data class PitchReading(
    /** Continuous pitch as a MIDI note number (69 = A4); NaN if unvoiced. */
    val midiFloat: Float,
    /** -50..+50 cents from the nearest note; 0 if unvoiced. */
    val cents: Int,
    val voiced: Boolean
) {
    val nearestMidi: Int get() = midiFloat.roundToInt()
}

/** A note (or rest) in an extracted melody; midi == null means a rest. */
data class NoteEvent(val midi: Int?, val startSec: Float, val durSec: Float)

/**
 * Autocorrelation pitch tracking, all on-device. Used three ways:
 * the live note readout while recording, the session vocal range,
 * and hum-to-instrument melody extraction.
 */
object PitchTracker {
    private const val MIN_HZ = 70f     // below most sung fundamentals
    private const val MAX_HZ = 1000f   // above most hummed melodies
    private const val VOICED_RMS = 0.015f
    private const val MIN_CORR = 0.55f

    /** Analyse one frame of PCM. [frame] should be ~2048–4096 samples. */
    fun analyse(frame: ShortArray, sr: Int): PitchReading {
        val n = frame.size
        if (n < 512) return PitchReading(Float.NaN, 0, false)

        var sumSq = 0.0
        for (s in frame) { val x = s / 32768.0; sumSq += x * x }
        val rms = sqrt(sumSq / n)
        if (rms < VOICED_RMS) return PitchReading(Float.NaN, 0, false)

        val minLag = (sr / MAX_HZ).toInt().coerceAtLeast(2)
        val maxLag = (sr / MIN_HZ).toInt().coerceAtMost(n / 2)
        // Normalised autocorrelation over the lag range.
        val corrs = DoubleArray(maxLag + 2)
        var bestCorr = 0.0
        for (lag in minLag..maxLag) {
            var num = 0.0; var d1 = 0.0; var d2 = 0.0
            val m = n - lag
            for (i in 0 until m) {
                val a = frame[i].toDouble(); val b = frame[i + lag].toDouble()
                num += a * b; d1 += a * a; d2 += b * b
            }
            val denom = sqrt(d1 * d2)
            if (denom <= 0.0) continue
            val corr = num / denom
            corrs[lag] = corr
            if (corr > bestCorr) bestCorr = corr
        }
        if (bestCorr < MIN_CORR) return PitchReading(Float.NaN, 0, false)
        // A periodic signal's correlation has a local peak at the true
        // period and at every multiple of it. Take the FIRST strong
        // local peak (later multiples are the octave-down trap), then
        // refine it with parabolic interpolation — picking the peak,
        // not a point on its rising slope, keeps the reading centred.
        var bestLag = -1
        for (lag in (minLag + 1) until maxLag) {
            if (corrs[lag] >= corrs[lag - 1] && corrs[lag] >= corrs[lag + 1] &&
                corrs[lag] >= bestCorr * 0.9
            ) { bestLag = lag; break }
        }
        if (bestLag <= 0) {
            var bi = minLag
            for (lag in minLag..maxLag) if (corrs[lag] > corrs[bi]) bi = lag
            bestLag = bi
        }
        val y1 = corrs[bestLag - 1]; val y2 = corrs[bestLag]; val y3 = corrs[bestLag + 1]
        val denomP = y1 - 2 * y2 + y3
        val shift = if (denomP != 0.0) (0.5 * (y1 - y3) / denomP).coerceIn(-1.0, 1.0) else 0.0
        val freq = sr.toDouble() / (bestLag + shift)
        val midi = 69.0 + 12.0 * (ln(freq / 440.0) / ln(2.0))
        val nearest = midi.roundToInt()
        val cents = ((midi - nearest) * 100).roundToInt()
        return PitchReading(midi.toFloat(), cents, true)
    }

    /**
     * Extract a melody (notes + rests) from a recorded take, for
     * hum-to-instrument. Frames of 2048 samples, half overlapped;
     * median-smoothed, then merged into events.
     */
    fun extractMelody(pcm: ShortArray, sr: Int): List<NoteEvent> {
        val frameSize = 2048
        val hop = 1024
        if (pcm.size < frameSize) return emptyList()
        val frames = (pcm.size - frameSize) / hop + 1
        val midis = IntArray(frames) { -1 }
        val frame = ShortArray(frameSize)
        for (f in 0 until frames) {
            System.arraycopy(pcm, f * hop, frame, 0, frameSize)
            val r = analyse(frame, sr)
            if (r.voiced) midis[f] = r.nearestMidi
        }
        // Median-of-3 smoothing kills single-frame octave/quantise blips.
        val smooth = midis.copyOf()
        for (i in 1 until frames - 1) {
            val trio = intArrayOf(midis[i - 1], midis[i], midis[i + 1]).sorted()
            smooth[i] = trio[1]
        }
        val hopSec = hop.toFloat() / sr
        val events = mutableListOf<NoteEvent>()
        var cur = smooth[0]; var start = 0
        for (i in 1..frames) {
            val v = if (i < frames) smooth[i] else Int.MIN_VALUE
            if (v != cur) {
                events.add(NoteEvent(if (cur >= 0) cur else null, start * hopSec, (i - start) * hopSec))
                cur = v; start = i
            }
        }
        // Drop events shorter than ~70 ms into their neighbours' time
        // by simply removing them (their span becomes part of playback
        // timing drift of at most one short event — fine for a beta).
        val kept = events.filter { it.durSec >= 0.07f }
        return kept
    }
}

/** Note naming — sharps by default, flats as enharmonic equivalents. */
object NoteNames {
    private val SHARPS = arrayOf("C", "C#", "D", "D#", "E", "F", "F#", "G", "G#", "A", "A#", "B")
    private val FLATS = arrayOf("C", "Db", "D", "Eb", "E", "F", "Gb", "G", "Ab", "A", "Bb", "B")

    /** "A#3", "C4" — MIDI 69 = A4. */
    fun name(midi: Int, useFlats: Boolean): String {
        val names = if (useFlats) FLATS else SHARPS
        val pc = ((midi % 12) + 12) % 12
        val octave = midi / 12 - 1
        return "${names[pc]}$octave"
    }
}
