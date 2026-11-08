/*
 * Woven Oak — Copyright (C) 2026 Paul Blanchard (Damonini) and Pepper —
 * Damonini and Pepper Software Development.
 * Free software under the GNU General Public License v3; see LICENSE.
 */
package com.damonini.wovenoak

import kotlin.math.PI
import kotlin.math.pow
import kotlin.math.sin
import kotlin.random.Random

/**
 * A synthesized instrument voice — "in the character of" the named
 * instrument. Every voice is a data row: a harmonic recipe (amplitudes
 * of partials 1..n), an ADSR envelope, vibrato, and a breath-noise mix.
 * One renderer plays them all, so the whole bank costs no audio files
 * and carries zero sample-licensing risk: nothing here is a recording.
 */
data class VoicePreset(
    val name: String,
    /** Amplitude of partials 1, 2, 3, ... (index 0 = fundamental). */
    val partials: FloatArray,
    val attackSec: Float,
    val decaySec: Float,
    val sustainLevel: Float,   // 0..1
    val releaseSec: Float,
    /** True for plucked/struck voices: envelope decays to zero. */
    val pluck: Boolean = false,
    val vibratoHz: Float = 0f,
    val vibratoCents: Float = 0f,
    /** 0..1 white-noise breathiness mixed under the tone. */
    val noiseMix: Float = 0f,
    /** Characteristic register for the library phrase (MIDI note). */
    val homeMidi: Int,
    /** Percussion has no pitch: excluded from hum-to-instrument. */
    val percussion: Boolean = false
)

object Instruments {

    private fun v(
        name: String, homeMidi: Int, partials: FloatArray,
        a: Float, d: Float, s: Float, r: Float,
        pluck: Boolean = false, vibHz: Float = 0f, vibCents: Float = 0f,
        noise: Float = 0f, percussion: Boolean = false
    ) = VoicePreset(name, partials, a, d, s, r, pluck, vibHz, vibCents, noise, homeMidi, percussion)

    /** The 30 voices — the most widely played instruments in the world,
     *  each approximated by synthesis. Order matches the README list. */
    val VOICES: List<VoicePreset> = listOf(
        v("Piano", 60, floatArrayOf(1f, .55f, .32f, .18f, .10f, .05f), .004f, .35f, .25f, .18f, pluck = true),
        v("Acoustic Guitar", 57, floatArrayOf(1f, .6f, .35f, .2f, .1f), .004f, .3f, .2f, .12f, pluck = true),
        v("Electric Guitar", 52, floatArrayOf(1f, .8f, .6f, .45f, .3f, .18f), .006f, .15f, .55f, .1f),
        v("Bass Guitar", 36, floatArrayOf(1f, .5f, .22f, .08f), .008f, .25f, .35f, .1f, pluck = true),
        v("Violin", 69, floatArrayOf(1f, .72f, .5f, .36f, .24f, .15f, .09f), .09f, .12f, .8f, .12f, vibHz = 5.5f, vibCents = 22f),
        v("Viola", 62, floatArrayOf(1f, .68f, .45f, .3f, .18f, .1f), .1f, .12f, .8f, .12f, vibHz = 5.2f, vibCents = 20f),
        v("Cello", 48, floatArrayOf(1f, .75f, .52f, .34f, .2f, .11f), .11f, .13f, .82f, .14f, vibHz = 5f, vibCents = 20f),
        v("Double Bass", 36, floatArrayOf(1f, .6f, .3f, .12f), .02f, .3f, .4f, .15f, pluck = true),
        v("Flute", 72, floatArrayOf(1f, .28f, .1f, .03f), .06f, .1f, .85f, .1f, vibHz = 5f, vibCents = 12f, noise = .12f),
        v("Clarinet", 62, floatArrayOf(1f, .06f, .42f, .05f, .2f, .03f, .09f), .05f, .1f, .85f, .09f, vibHz = 4.5f, vibCents = 8f),
        v("Oboe", 69, floatArrayOf(.85f, 1f, .62f, .4f, .22f, .1f), .05f, .1f, .85f, .09f, vibHz = 5f, vibCents = 10f, noise = .05f),
        v("Bassoon", 46, floatArrayOf(.9f, 1f, .55f, .3f, .15f), .06f, .12f, .8f, .1f, vibHz = 4.5f, vibCents = 8f),
        v("Saxophone", 58, floatArrayOf(.9f, 1f, .7f, .48f, .3f, .16f), .05f, .12f, .85f, .1f, vibHz = 5.2f, vibCents = 16f, noise = .06f),
        v("Trumpet", 65, floatArrayOf(.8f, 1f, .85f, .6f, .4f, .24f, .12f), .04f, .1f, .85f, .08f, vibHz = 5f, vibCents = 10f),
        v("Trombone", 53, floatArrayOf(.85f, 1f, .7f, .45f, .25f, .12f), .06f, .12f, .85f, .1f, vibHz = 4.8f, vibCents = 9f),
        v("French Horn", 53, floatArrayOf(1f, .55f, .3f, .15f, .06f), .09f, .14f, .8f, .13f, vibHz = 4.5f, vibCents = 8f),
        v("Harp", 65, floatArrayOf(1f, .5f, .25f, .12f, .05f), .003f, .5f, .1f, .3f, pluck = true),
        v("Organ", 60, floatArrayOf(1f, .8f, .6f, .4f, .28f, .18f, .1f), .02f, .05f, .95f, .06f),
        v("Synthesizer", 60, floatArrayOf(1f, .9f, .8f, .65f, .5f, .35f, .22f, .12f), .01f, .1f, .8f, .1f, vibHz = 6f, vibCents = 6f),
        v("Harmonica", 67, floatArrayOf(.9f, 1f, .6f, .35f, .18f), .04f, .1f, .85f, .08f, vibHz = 5.5f, vibCents = 14f, noise = .08f),
        v("Accordion", 65, floatArrayOf(.9f, 1f, .68f, .42f, .22f), .05f, .1f, .88f, .09f, vibHz = 6.5f, vibCents = 18f),
        v("Ukulele", 67, floatArrayOf(1f, .45f, .2f, .08f), .003f, .18f, .15f, .08f, pluck = true),
        v("Banjo", 62, floatArrayOf(1f, .7f, .45f, .28f, .15f), .002f, .12f, .08f, .06f, pluck = true),
        v("Mandolin", 69, floatArrayOf(1f, .6f, .35f, .18f, .08f), .003f, .16f, .12f, .07f, pluck = true),
        v("Xylophone", 76, floatArrayOf(1f, .25f, .08f, .3f), .002f, .09f, .03f, .05f, pluck = true),
        v("Marimba", 60, floatArrayOf(1f, .3f, .06f, .18f), .003f, .28f, .05f, .12f, pluck = true),
        v("Erhu", 69, floatArrayOf(.9f, 1f, .66f, .44f, .26f, .14f), .1f, .12f, .82f, .12f, vibHz = 5.8f, vibCents = 30f, noise = .04f),
        v("Sitar", 62, floatArrayOf(1f, .75f, .55f, .4f, .28f, .18f, .1f), .004f, .45f, .18f, .25f, pluck = true, vibHz = 7f, vibCents = 25f),
        v("Bells", 72, floatArrayOf(1f, .12f, .45f, .06f, .28f, .04f, .15f), .002f, .8f, .02f, .6f, pluck = true),
        v("Drums", 0, floatArrayOf(1f), .002f, .1f, 0f, .05f, pluck = true, percussion = true)
    )

    fun melodicVoices(): List<VoicePreset> = VOICES.filter { !it.percussion }

    fun midiToFreq(midi: Int): Float = 440f * 2f.pow((midi - 69) / 12f)
}

/** The one renderer behind every voice. */
object Synth {
    private const val PEAK = 0.72f

    /** Render a single note of [preset] at [freq] for [durSec]. */
    fun renderNote(preset: VoicePreset, freq: Float, durSec: Float, sr: Int): ShortArray {
        val n = (durSec * sr).toInt().coerceAtLeast(1)
        val out = FloatArray(n)
        val partials = preset.partials
        val norm = partials.sum().coerceAtLeast(1f)
        val rnd = Random(1234)
        var phase = 0.0
        for (i in 0 until n) {
            val t = i.toDouble() / sr
            // Vibrato bends the instantaneous frequency; integrating it
            // into the phase keeps the pitch centred.
            val vib = if (preset.vibratoHz > 0f)
                2.0.pow(preset.vibratoCents / 1200.0 * sin(2 * PI * preset.vibratoHz * t)) else 1.0
            phase += 2 * PI * freq * vib / sr
            var s = 0.0
            for (p in partials.indices) {
                s += partials[p] * sin(phase * (p + 1))
            }
            s /= norm
            if (preset.noiseMix > 0f) {
                s = s * (1 - preset.noiseMix) + (rnd.nextFloat() * 2 - 1) * preset.noiseMix * 0.5
            }
            out[i] = (s * envelope(preset, t.toFloat(), durSec)).toFloat()
        }
        return toShort(out)
    }

    private fun envelope(p: VoicePreset, t: Float, dur: Float): Float {
        val a = p.attackSec.coerceAtLeast(0.001f)
        if (t < a) return t / a
        if (p.pluck) {
            // Struck/plucked: exponential-ish fall from the attack peak.
            val tau = (p.decaySec + p.releaseSec).coerceAtLeast(0.02f)
            return kotlin.math.exp(-(t - a) / tau * 2.2).toFloat()
        }
        val dEnd = a + p.decaySec
        if (t < dEnd) {
            val k = (t - a) / p.decaySec.coerceAtLeast(0.001f)
            return 1f - (1f - p.sustainLevel) * k
        }
        val relStart = (dur - p.releaseSec).coerceAtLeast(dEnd)
        if (t > relStart) {
            val k = ((dur - t) / p.releaseSec.coerceAtLeast(0.001f)).coerceIn(0f, 1f)
            return p.sustainLevel * k
        }
        return p.sustainLevel
    }

    /**
     * The library entry for a voice: a short characteristic phrase —
     * a rising scale fragment home, third, fifth, octave — rendered in
     * that voice. Drums get a two-bar beat instead.
     */
    fun renderPhrase(preset: VoicePreset, sr: Int): ShortArray {
        if (preset.percussion) return renderDrumLoop(sr)
        val steps = intArrayOf(0, 4, 7, 12, 7, 4)
        val noteDur = 0.34f
        val total = ((steps.size * noteDur + 0.25f) * sr).toInt()
        val mix = FloatArray(total)
        steps.forEachIndexed { idx, off ->
            val note = renderNote(preset, Instruments.midiToFreq(preset.homeMidi + off), noteDur + 0.12f, sr)
            val start = (idx * noteDur * sr).toInt()
            for (j in note.indices) {
                if (start + j < total) mix[start + j] += note[j] / 32767f
            }
        }
        return toShort(mix)
    }

    /** Kick / snare / hats, two bars of 4/4 at 100 BPM. */
    fun renderDrumLoop(sr: Int): ShortArray {
        val beat = 60f / 100f
        val total = (beat * 8 * sr).toInt()
        val mix = FloatArray(total)
        val rnd = Random(99)
        fun add(startSec: Float, lenSec: Float, gen: (Float, Float) -> Float) {
            val s0 = (startSec * sr).toInt()
            val len = (lenSec * sr).toInt()
            for (j in 0 until len) {
                if (s0 + j < total) mix[s0 + j] += gen(j.toFloat() / sr, lenSec)
            }
        }
        for (bar in 0 until 2) {
            val base = bar * 4 * beat
            for (b in 0 until 4) {
                val t0 = base + b * beat
                // Kick: sine pitch drop.
                add(t0, 0.22f) { t, _ ->
                    val f = 120.0 - 80.0 * (t / 0.22f)
                    (sin(2 * PI * f * t) * kotlin.math.exp(-t * 18.0)).toFloat() * 0.95f
                }
                // Hats on the off-eighths.
                add(t0 + beat / 2, 0.05f) { t, _ ->
                    ((rnd.nextFloat() * 2 - 1) * kotlin.math.exp(-t * 90.0)).toFloat() * 0.32f
                }
                if (b == 1 || b == 3) {
                    // Snare: noise burst + body.
                    add(t0, 0.16f) { t, _ ->
                        ((rnd.nextFloat() * 2 - 1) * kotlin.math.exp(-t * 30.0) * 0.6 +
                            sin(2 * PI * 185 * t) * kotlin.math.exp(-t * 38.0) * 0.5).toFloat()
                    }
                }
            }
        }
        return toShort(mix)
    }

    private fun toShort(mix: FloatArray): ShortArray {
        var peak = 0f
        for (x in mix) peak = maxOf(peak, kotlin.math.abs(x))
        val g = if (peak > PEAK) PEAK / peak else 1f
        return ShortArray(mix.size) { (mix[it] * g * 32767f).toInt().coerceIn(-32768, 32767).toShort() }
    }
}
