/*
 * Woven Oak — Copyright (C) 2026 Paul Blanchard (Damonini) and Pepper —
 * Damonini and Pepper Software Development.
 * Free software under the GNU General Public License v3; see LICENSE.
 */
package com.damonini.wovenoak

import android.media.AudioAttributes
import android.media.AudioFormat
import android.media.AudioRecord
import android.media.AudioTrack
import android.media.MediaRecorder
import kotlin.concurrent.thread

/**
 * One loop pad: a small state machine over raw PCM.
 *
 *   EMPTY --tap--> RECORDING --tap--> PLAYING <--tap--> READY
 *   any state --hold--> EMPTY
 *
 * Capture is [AudioRecord] PCM; playback is an [AudioTrack] in
 * MODE_STATIC whose loop points span the whole take, so the loop
 * restarts inside the audio engine with no gap and no scheduling
 * latency from app code — the native answer to "a phone screen must
 * feel like a hardware board."
 */
class LoopPad(
    val index: Int,
    /** Called (on an arbitrary thread) whenever visible state changes. */
    private val notify: () -> Unit
) {
    enum class State { EMPTY, RECORDING, PLAYING, READY }

    @Volatile var state: State = State.EMPTY
        private set

    /** The pad's current loop material (a mic take, a library sound,
     *  or a hum conversion) — what playback loops. */
    var pcm: ShortArray? = null
        private set

    /** The last raw microphone take, kept so hum-to-instrument can
     *  re-voice it in different instruments on this pad. */
    var rawTake: ShortArray? = null
        private set

    /** True once this pad holds a mic take hum-to-instrument can use. */
    val hasTake: Boolean get() = rawTake != null

    /** Take length in seconds, for display. */
    val durationSec: Float
        get() = (pcm?.size ?: 0).toFloat() / SAMPLE_RATE

    private var recorder: AudioRecord? = null
    @Volatile private var capturing = false
    private var recordThread: Thread? = null
    private var chunks = mutableListOf<ShortArray>()
    @Volatile var recordStartedAtMs: Long = 0L
        private set

    private var track: AudioTrack? = null

    companion object {
        const val SAMPLE_RATE = 44100
        /** Beta safety cap: a take stops itself at two minutes. */
        const val MAX_FRAMES = SAMPLE_RATE * 120
        /** Only one pad may hold the microphone at a time. */
        @Volatile private var recordingPad: LoopPad? = null
    }

    /** Returns false when another pad is already recording. */
    fun startRecording(): Boolean {
        if (state != State.EMPTY) return true
        if (recordingPad != null && recordingPad !== this) return false

        val minBuf = AudioRecord.getMinBufferSize(
            SAMPLE_RATE, AudioFormat.CHANNEL_IN_MONO, AudioFormat.ENCODING_PCM_16BIT
        )
        val rec = try {
            AudioRecord(
                MediaRecorder.AudioSource.MIC,
                SAMPLE_RATE,
                AudioFormat.CHANNEL_IN_MONO,
                AudioFormat.ENCODING_PCM_16BIT,
                maxOf(minBuf, SAMPLE_RATE) // >= 1s of headroom in shorts*1
            )
        } catch (e: SecurityException) {
            return false
        }
        if (rec.state != AudioRecord.STATE_INITIALIZED) {
            rec.release()
            return false
        }
        recorder = rec
        chunks = mutableListOf()
        recordingPad = this
        capturing = true
        recordStartedAtMs = System.currentTimeMillis()
        state = State.RECORDING
        rec.startRecording()
        recordThread = thread(name = "wovenoak-rec-$index") {
            val buf = ShortArray(4096)
            var total = 0
            while (capturing) {
                val n = rec.read(buf, 0, buf.size)
                if (n > 0) {
                    synchronized(chunks) { chunks.add(buf.copyOf(n)) }
                    total += n
                    if (total >= MAX_FRAMES) {
                        // Cap reached: finish the take from this thread.
                        stopRecording()
                        break
                    }
                }
            }
        }
        notify()
        return true
    }

    /** Stop the take, keep it, and — per the design — play it at once. */
    fun stopRecording() {
        if (state != State.RECORDING) return
        capturing = false
        try { recorder?.stop() } catch (e: IllegalStateException) { /* already stopped */ }
        recordThread?.join(500)
        recordThread = null
        recorder?.release()
        recorder = null
        if (recordingPad === this) recordingPad = null

        val flat: ShortArray
        synchronized(chunks) {
            val total = chunks.sumOf { it.size }
            flat = ShortArray(total)
            var pos = 0
            for (c in chunks) { c.copyInto(flat, pos); pos += c.size }
            chunks.clear()
        }
        if (flat.isEmpty()) {
            pcm = null
            state = State.EMPTY
            notify()
            return
        }
        pcm = flat
        rawTake = flat
        startPlayback()
    }

    fun startPlayback() {
        val data = pcm ?: run { state = State.EMPTY; notify(); return }
        releaseTrack()
        val t = AudioTrack.Builder()
            .setAudioAttributes(
                AudioAttributes.Builder()
                    .setUsage(AudioAttributes.USAGE_MEDIA)
                    .setContentType(AudioAttributes.CONTENT_TYPE_MUSIC)
                    .build()
            )
            .setAudioFormat(
                AudioFormat.Builder()
                    .setSampleRate(SAMPLE_RATE)
                    .setChannelMask(AudioFormat.CHANNEL_OUT_MONO)
                    .setEncoding(AudioFormat.ENCODING_PCM_16BIT)
                    .build()
            )
            .setBufferSizeInBytes(data.size * 2)
            .setTransferMode(AudioTrack.MODE_STATIC)
            .build()
        t.write(data, 0, data.size)
        // The whole take is the loop; -1 repeats forever, gaplessly.
        t.setLoopPoints(0, data.size, -1)
        t.play()
        track = t
        state = State.PLAYING
        notify()
    }

    fun stopPlayback() {
        releaseTrack()
        state = if (pcm != null) State.READY else State.EMPTY
        notify()
    }

    fun clear() {
        if (state == State.RECORDING) {
            capturing = false
            try { recorder?.stop() } catch (e: IllegalStateException) { /* ignore */ }
            recordThread?.join(500)
            recordThread = null
            recorder?.release()
            recorder = null
            if (recordingPad === this) recordingPad = null
            synchronized(chunks) { chunks.clear() }
        }
        releaseTrack()
        pcm = null
        rawTake = null
        state = State.EMPTY
        notify()
    }

    /**
     * Load externally generated PCM onto this pad — a built-in library
     * sound or a hum-to-instrument conversion — and play it at once.
     * The pad's raw mic take (if any) is kept for re-voicing.
     */
    fun loadPcm(data: ShortArray) {
        if (state == State.RECORDING || data.isEmpty()) return
        releaseTrack()
        pcm = data
        startPlayback()
    }

    /** The most recent [count] captured samples, for the live pitch
     *  readout while recording. Empty when nothing is being captured. */
    fun recentSamples(count: Int): ShortArray {
        if (state != State.RECORDING) return ShortArray(0)
        synchronized(chunks) {
            var need = count
            val out = ShortArray(count)
            var pos = count
            var ci = chunks.size - 1
            while (need > 0 && ci >= 0) {
                val c = chunks[ci]
                val take = minOf(need, c.size)
                pos -= take
                c.copyInto(out, pos, c.size - take, c.size)
                need -= take
                ci--
            }
            return if (need > 0) out.copyOfRange(need, count) else out
        }
    }

    fun release() = clear()

    private fun releaseTrack() {
        track?.let {
            try { it.pause() } catch (e: IllegalStateException) { /* ignore */ }
            try { it.stop() } catch (e: IllegalStateException) { /* ignore */ }
            it.release()
        }
        track = null
    }
}
