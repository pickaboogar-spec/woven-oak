/*
 * Woven Oak — Copyright (C) 2026 Paul Blanchard (Damonini) and Pepper —
 * Damonini and Pepper Software Development.
 * Free software under the GNU General Public License v3; see LICENSE.
 */
package com.damonini.wovenoak

/**
 * Hum-to-instrument: pitch-track a recorded hum, extract its melody,
 * and re-synthesize the melody in a chosen built-in voice. Entirely
 * on-device — the hum never leaves the phone. Drums are excluded by
 * design: percussion has no pitch to map a hum onto (it stays
 * available in the sound library).
 */
object HumConverter {

    /**
     * Convert [pcm] (a hummed/sung take) into [voice]. Returns the new
     * loop material at the take's original duration, or null if no
     * clear melody could be heard in the take.
     */
    fun convert(pcm: ShortArray, sr: Int, voice: VoicePreset): ShortArray? {
        if (voice.percussion) return null
        val events = PitchTracker.extractMelody(pcm, sr)
        if (events.none { it.midi != null }) return null

        val totalSec = pcm.size.toFloat() / sr
        val mix = FloatArray(pcm.size)
        for (ev in events) {
            val midi = ev.midi ?: continue // rest: leave silence
            val note = Synth.renderNote(
                voice, Instruments.midiToFreq(midi),
                ev.durSec.coerceAtMost(4f), sr
            )
            val start = (ev.startSec * sr).toInt()
            val len = minOf(note.size, (ev.durSec * sr).toInt(), mix.size - start)
            if (len <= 0) continue
            for (j in 0 until len) mix[start + j] += note[j] / 32767f
        }
        // Preserve the take's timing/length exactly so the converted
        // loop drops in where the hum was.
        val out = ShortArray(pcm.size)
        var peak = 0f
        for (x in mix) peak = maxOf(peak, kotlin.math.abs(x))
        val g = if (peak > 0.72f) 0.72f / peak else 1f
        for (i in mix.indices) {
            out[i] = (mix[i] * g * 32767f).toInt().coerceIn(-32768, 32767).toShort()
        }
        return out
    }
}
