/*
 * Woven Oak — Copyright (C) 2026 Paul Blanchard (Damonini) and Pepper —
 * Damonini and Pepper Software Development.
 * Free software under the GNU General Public License v3; see LICENSE.
 */
package com.damonini.wovenoak

import android.media.AudioFormat
import android.media.AudioRecord
import android.media.MediaRecorder
import kotlin.concurrent.thread

/**
 * A short microphone take for the calibration walk, kept separate
 * from the loop pads: calibration must not disturb the singer's
 * loops, and the pads must not be disturbed by it. Same capture
 * shape as LoopPad — mono PCM at the app's sample rate, on this
 * device only, under the app's one permission: the microphone.
 */
class CalibCapture {

    @Volatile private var capturing = false
    private var recorder: AudioRecord? = null
    private var recordThread: Thread? = null
    private val chunks = mutableListOf<ShortArray>()

    val isCapturing: Boolean get() = capturing

    /** Begin a take. False when the microphone cannot be opened. */
    fun start(): Boolean {
        if (capturing) return true
        val sr = LoopPad.SAMPLE_RATE
        val minBuf = AudioRecord.getMinBufferSize(
            sr, AudioFormat.CHANNEL_IN_MONO, AudioFormat.ENCODING_PCM_16BIT
        )
        val rec = try {
            AudioRecord(
                MediaRecorder.AudioSource.MIC,
                sr,
                AudioFormat.CHANNEL_IN_MONO,
                AudioFormat.ENCODING_PCM_16BIT,
                maxOf(minBuf, sr)
            )
        } catch (e: SecurityException) {
            return false
        }
        if (rec.state != AudioRecord.STATE_INITIALIZED) {
            rec.release()
            return false
        }
        recorder = rec
        synchronized(chunks) { chunks.clear() }
        capturing = true
        rec.startRecording()
        recordThread = thread(name = "wovenoak-calib") {
            val buf = ShortArray(4096)
            var total = 0
            while (capturing) {
                val n = rec.read(buf, 0, buf.size)
                if (n > 0) {
                    synchronized(chunks) { chunks.add(buf.copyOf(n)) }
                    total += n
                    // A calibration take never needs more than this.
                    if (total >= sr * 6) break
                }
            }
        }
        return true
    }

    /** End the take and hand back everything that was sung. */
    fun stop(): ShortArray {
        capturing = false
        try { recorder?.stop() } catch (e: IllegalStateException) { /* already stopped */ }
        recordThread?.join(500)
        recordThread = null
        recorder?.release()
        recorder = null
        synchronized(chunks) {
            val total = chunks.sumOf { it.size }
            val flat = ShortArray(total)
            var pos = 0
            for (c in chunks) { c.copyInto(flat, pos); pos += c.size }
            chunks.clear()
            return flat
        }
    }

    /** End the take and throw it away. */
    fun cancel() {
        if (!capturing && recorder == null) return
        capturing = false
        try { recorder?.stop() } catch (e: IllegalStateException) { /* already stopped */ }
        recordThread?.join(500)
        recordThread = null
        recorder?.release()
        recorder = null
        synchronized(chunks) { chunks.clear() }
    }
}
