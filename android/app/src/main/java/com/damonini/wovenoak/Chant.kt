/*
 * Woven Oak — Copyright (C) 2026 Paul Blanchard (Damonini) and Pepper —
 * Damonini and Pepper Software Development.
 * Free software under the GNU General Public License v3; see LICENSE.
 */
package com.damonini.wovenoak

import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.exp
import kotlin.math.sin
import kotlin.random.Random

/**
 * The Chant renderer — Paul's template, designed 2026-11-07.
 *
 * It imitates *mechanisms*, never songs: a steady electronic
 * pulse, a processed voice chanting short phrases one to a beat,
 * the phrases stacking and cycling until the list itself becomes
 * the piece. The template ships empty — every word rendered here
 * was recorded by the user onto their own pads.
 *
 * Two rules Paul set on the first night:
 *  1. The words must MATCH the music. Every phrase is digitally
 *     stretched or squeezed to exactly one beat before it is
 *     placed — nobody's natural pace survives; the grid wins.
 *  2. Styles are described, never named for artists. Four
 *     instrumental styles ship as pulse-and-voice recipes:
 *     Robot Chant, Acid House, Industrial Drive, Dark Synth.
 *
 * Rendering is offline and sample-accurate: phrases are fitted
 * and mixed onto the beat grid into one PCM buffer, which a pad
 * then loops gaplessly like any other take.
 */
object ChantRenderer {

    enum class Style(val label: String, val defaultBpm: Int) {
        ROBOT("Robot Chant", 120),
        ACID("Acid House", 124),
        INDUSTRIAL("Industrial Drive", 100),
        DARKSYNTH("Dark Synth", 105)
    }

    /**
     * @param phrases the user's phrase takes, in pad order
     * @param anchor  an optional refrain take, layered every 4 beats
     * @param bpm     tempo of the beat grid
     * @param style   which instrumental recipe drives the pulse
     * @param pulse   synthesize the style's electronic pulse
     * @param treated apply the style's voice treatment
     */
    fun render(
        phrases: List<ShortArray>,
        anchor: ShortArray?,
        bpm: Int,
        style: Style,
        pulse: Boolean,
        treated: Boolean
    ): ShortArray {
        val beat = LoopPad.SAMPLE_RATE * 60 / bpm.coerceIn(60, 200)
        // Two 8-beat passes (half the phrases, then all of them).
        // Kept to 16 beats total so the rendered buffer is the
        // size of an ordinary pad take — the first field test
        // (beta.4) suggested a 32-beat buffer may be more than
        // some phones will loop as a static track.
        val beatsPerBar = 8
        val totalBeats = beatsPerBar * 2
        val total = beat * totalBeats
        val mix = FloatArray(total)

        // Bar 1 runs the first half of the list; bar 2 runs it all.
        val half = phrases.subList(0, (phrases.size + 1) / 2)
        for (b in 0 until totalBeats) {
            val list = if (b < beatsPerBar) half else phrases
            placeFitted(mix, list[b % list.size], b * beat, beat, style, treated, 0.85f)
            if (anchor != null && b % 4 == 0) {
                placeFitted(mix, anchor, b * beat, beat, style, treated, 0.8f)
            }
        }

        if (pulse) addPulse(mix, beat, totalBeats, style)

        // Gentle soft-clip so stacked voices stay loud, not harsh.
        val out = ShortArray(total)
        for (i in 0 until total) {
            val x = mix[i]
            val y = x / (1f + abs(x)) * 1.5f
            out[i] = (y.coerceIn(-1f, 1f) * 32767f).toInt().toShort()
        }
        return out
    }

    /** Fit one phrase to exactly [beat] samples — linear-interp
     *  resampling, so a slow phrase speeds up and a long one is
     *  squeezed in; pitch rides along, which suits the treated
     *  voices — then apply the style's voice treatment and mix. */
    private fun placeFitted(
        mix: FloatArray, phrase: ShortArray, start: Int, beat: Int,
        style: Style, treated: Boolean, gain: Float
    ) {
        if (phrase.isEmpty()) return
        val sr = LoopPad.SAMPLE_RATE
        val n = minOf(beat, mix.size - start)
        val step = phrase.size.toDouble() / beat
        for (j in 0 until n) {
            val pos = j * step
            val i0 = pos.toInt().coerceAtMost(phrase.size - 1)
            val i1 = minOf(i0 + 1, phrase.size - 1)
            val frac = (pos - i0).toFloat()
            var s = (phrase[i0] * (1f - frac) + phrase[i1] * frac) / 32768f
            if (treated) {
                s = when (style) {
                    Style.ROBOT, Style.ACID ->
                        s * sin(2.0 * PI * 30.0 * (start + j) / sr).toFloat()
                    Style.INDUSTRIAL -> {
                        val levels = 48f
                        (kotlin.math.round(s * levels) / levels)
                    }
                    Style.DARKSYNTH -> s
                }
            }
            mix[start + j] += s * gain
        }
    }

    /** The styles' pulses — all synthesized here, all original:
     *  sine-drop kicks, noise hats and snares, and simple bass
     *  patterns through a one-pole lowpass. */
    private fun addPulse(mix: FloatArray, beat: Int, totalBeats: Int, style: Style) {
        val sr = LoopPad.SAMPLE_RATE
        val rng = Random(42)
        when (style) {
            Style.ROBOT -> {
                for (b in 0 until totalBeats) {
                    kick(mix, b * beat, 0.85f)
                    hat(mix, b * beat + beat / 2, rng, 0.22f)
                }
            }
            Style.ACID -> {
                // Kick on the beat, offbeat hats, and a squelchy
                // 16th-note bass arp (original pattern, A minor).
                val steps = intArrayOf(0, -1, 12, -1, 0, -1, 3, -1, 0, -1, 12, -1, 10, -1, 3, -1)
                var lp = 0f
                for (b in 0 until totalBeats) {
                    kick(mix, b * beat, 0.8f)
                    hat(mix, b * beat + beat / 2, rng, 0.18f)
                    for (sIdx in 0 until 4) {
                        val semi = steps[sIdx]
                        if (semi < 0) continue
                        val start = b * beat + sIdx * beat / 4
                        val len = beat / 4
                        val freq = 55.0 * Math.pow(2.0, semi / 12.0)
                        for (j in 0 until len) {
                            if (start + j >= mix.size) break
                            val ph = (j * freq / sr) % 1.0
                            val saw = (ph * 2.0 - 1.0).toFloat()
                            val cutoff = 0.06f + 0.5f * (1f - j.toFloat() / len)
                            lp += cutoff * (saw - lp)
                            val env = 1f - j.toFloat() / len * 0.6f
                            mix[start + j] += lp * env * 0.5f
                        }
                    }
                }
            }
            Style.INDUSTRIAL -> {
                // Heavy driven kicks on 1 and 3, metallic hits on
                // every beat, a low drone under the bar.
                for (b in 0 until totalBeats) {
                    if (b % 2 == 0) kick(mix, b * beat, 1.0f, drive = true)
                    hat(mix, b * beat, rng, 0.3f, metallic = true)
                    val droneLen = beat
                    for (j in 0 until droneLen) {
                        val t = (b * beat + j).toDouble() / sr
                        mix[b * beat + j] += (sin(2.0 * PI * 36.7 * t) * 0.10).toFloat()
                    }
                }
            }
            Style.DARKSYNTH -> {
                // Kick 1 & 3, snare 2 & 4, pulsing eighth-note bass.
                val bassSteps = intArrayOf(0, 0, 7, 0, 0, 3, 0, 10)
                var lp = 0f
                for (b in 0 until totalBeats) {
                    if (b % 2 == 0) kick(mix, b * beat, 0.85f) else snare(mix, b * beat, rng)
                    for (halfIdx in 0 until 2) {
                        val semi = bassSteps[(b * 2 + halfIdx) % bassSteps.size]
                        val start = b * beat + halfIdx * beat / 2
                        val len = beat / 2
                        val freq = 55.0 * Math.pow(2.0, semi / 12.0)
                        for (j in 0 until len) {
                            if (start + j >= mix.size) break
                            val ph = (j * freq / sr) % 1.0
                            val sq = if (ph < 0.5) 0.6f else -0.6f
                            lp += 0.18f * (sq - lp)
                            mix[start + j] += lp * 0.4f
                        }
                    }
                }
            }
        }
    }

    private fun kick(mix: FloatArray, start: Int, gain: Float, drive: Boolean = false) {
        val sr = LoopPad.SAMPLE_RATE
        val len = (sr * 0.11).toInt()
        for (j in 0 until len) {
            if (start + j >= mix.size) break
            val t = j.toDouble() / sr
            val freq = 42.0 + 68.0 * exp(-t * 30.0)
            var s = (sin(2.0 * PI * freq * t) * exp(-t * 22.0)).toFloat()
            if (drive) s = (s * 2.5f).coerceIn(-1f, 1f)
            mix[start + j] += s * gain
        }
    }

    private fun hat(
        mix: FloatArray, start: Int, rng: Random,
        gain: Float, metallic: Boolean = false
    ) {
        val sr = LoopPad.SAMPLE_RATE
        val len = (sr * if (metallic) 0.05 else 0.03).toInt()
        var prev = 0f
        for (j in 0 until len) {
            if (start + j >= mix.size) break
            val white = rng.nextFloat() * 2f - 1f
            var hp = white - prev
            prev = white
            if (metallic) hp *= (0.6f + 0.4f * sin(2.0 * PI * 6200.0 * j / sr)).toFloat()
            val env = 1f - j.toFloat() / len
            mix[start + j] += hp * env * gain
        }
    }

    private fun snare(mix: FloatArray, start: Int, rng: Random) {
        val sr = LoopPad.SAMPLE_RATE
        val len = (sr * 0.09).toInt()
        var prev = 0f
        for (j in 0 until len) {
            if (start + j >= mix.size) break
            val t = j.toDouble() / sr
            val white = rng.nextFloat() * 2f - 1f
            val hp = white - prev
            prev = white
            val tone = sin(2.0 * PI * 185.0 * t).toFloat() * 0.4f
            val env = exp(-t * 38.0).toFloat()
            mix[start + j] += (hp * 0.5f + tone) * env * 0.6f
        }
    }
}
