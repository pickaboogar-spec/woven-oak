/*
 * Woven Oak — Copyright (C) 2026 Paul Blanchard (Damonini) and Pepper —
 * Damonini and Pepper Software Development.
 * Free software under the GNU General Public License v3; see LICENSE.
 */
package com.damonini.wovenoak

import android.media.AudioAttributes
import android.media.AudioFormat
import android.media.AudioTrack

/**
 * One-shot playback for short rendered tones — the calibration
 * walk's guide notes. A single shared track: a new tone replaces
 * whatever was playing, and stop() silences it. Playback only;
 * nothing is recorded here and nothing leaves the device.
 */
object TonePlayer {

    private var track: AudioTrack? = null

    fun play(pcm: ShortArray) {
        if (pcm.isEmpty()) return
        stop()
        val t = AudioTrack.Builder()
            .setAudioAttributes(
                AudioAttributes.Builder()
                    .setUsage(AudioAttributes.USAGE_MEDIA)
                    .setContentType(AudioAttributes.CONTENT_TYPE_MUSIC)
                    .build()
            )
            .setAudioFormat(
                AudioFormat.Builder()
                    .setSampleRate(LoopPad.SAMPLE_RATE)
                    .setChannelMask(AudioFormat.CHANNEL_OUT_MONO)
                    .setEncoding(AudioFormat.ENCODING_PCM_16BIT)
                    .build()
            )
            .setBufferSizeInBytes(pcm.size * 2)
            .setTransferMode(AudioTrack.MODE_STATIC)
            .build()
        t.write(pcm, 0, pcm.size)
        t.play()
        track = t
    }

    fun stop() {
        track?.let {
            try { it.pause() } catch (e: IllegalStateException) { /* ignore */ }
            try { it.stop() } catch (e: IllegalStateException) { /* ignore */ }
            it.release()
        }
        track = null
    }
}
