/*
 * Woven Oak — Copyright (C) 2026 Paul Blanchard (Damonini) and Pepper —
 * Damonini and Pepper Software Development.
 * Free software under the GNU General Public License v3; see LICENSE.
 *
 * A truly electronic acapella instrument: six pads, two taps.
 * Tap a pad to record; tap again and the take loops by itself.
 * Everything happens on this device; recordings never leave it.
 * No accounts, no telemetry, no ads — free forever.
 */
package com.damonini.wovenoak

import android.Manifest
import android.animation.ValueAnimator
import android.app.Activity
import android.app.AlertDialog
import android.content.Intent
import android.content.pm.PackageManager
import android.content.res.ColorStateList
import android.net.Uri
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.WindowManager
import android.view.animation.LinearInterpolator
import android.widget.Button
import android.widget.TextView
import kotlin.concurrent.thread

class MainActivity : Activity() {

    private val padIds = intArrayOf(
        R.id.pad1, R.id.pad2, R.id.pad3, R.id.pad4, R.id.pad5, R.id.pad6
    )
    private val toggleIds = intArrayOf(
        R.id.tgl1, R.id.tgl2, R.id.tgl3, R.id.tgl4, R.id.tgl5, R.id.tgl6
    )

    // Empty (dim) / Playing (bright) / Ready (mid) tints per pad.
    private val dimTints = longArrayOf(
        0xFF3D232B, 0xFF3D3123, 0xFF2E3D23, 0xFF23403D, 0xFF233149, 0xFF31233D
    ).map { it.toInt() }
    private val playTints = longArrayOf(
        0xFFB03A48, 0xFFC77F2E, 0xFF7FB03A, 0xFF2EB0A3, 0xFF3A7BD5, 0xFF8E4EC6
    ).map { it.toInt() }
    private val readyTints = longArrayOf(
        0xFF6E2A33, 0xFF75562A, 0xFF4E6629, 0xFF29665F, 0xFF2A4E75, 0xFF53356E
    ).map { it.toInt() }
    private val recordingTint = 0xFF8E2424.toInt()

    private lateinit var status: TextView
    private lateinit var pitchNote: TextView
    private lateinit var pitchRange: TextView
    private lateinit var centsMeter: CentsMeterView
    private val buttons = arrayOfNulls<Button>(6)
    private val toggles = arrayOfNulls<Button>(6)
    private val animators = arrayOfNulls<ValueAnimator>(6)
    private val pads = ArrayList<LoopPad>()

    private val ui = Handler(Looper.getMainLooper())
    private var pendingPad: Int = -1

    /** The pad the Sounds / Hum buttons act on: the last one tapped. */
    private var selectedPad = 0
    /** Note naming: sharps by default; tap the ♯/♭ button to switch. */
    private var useFlats = false
    /** Session vocal range (MIDI), from the live pitch readout. */
    private var rangeLow: Int = Int.MAX_VALUE
    private var rangeHigh: Int = Int.MIN_VALUE
    private var lastReading: PitchReading? = null
    @Volatile private var pitchBusy = false
    private var tickCount = 0
    private var meterWasActive = false

    private val ticker = object : Runnable {
        override fun run() {
            tickCount++
            var anyRecording = false
            for (i in pads.indices) {
                if (pads[i].state == LoopPad.State.RECORDING) {
                    renderPad(i)
                    anyRecording = true
                    // ~200 ms cadence for the live pitch readout.
                    if (tickCount % 2 == 0) analyseLivePitch(pads[i])
                }
            }
            if (!anyRecording && meterWasActive) {
                meterWasActive = false
                centsMeter.setReading(0, false)
            }
            ui.postDelayed(this, 100)
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        setContentView(R.layout.activity_main)
        status = findViewById(R.id.status)
        pitchNote = findViewById(R.id.pitchNote)
        pitchRange = findViewById(R.id.pitchRange)
        centsMeter = findViewById(R.id.centsMeter)

        for (i in 0 until 6) {
            val idx = i
            pads.add(LoopPad(i) { runOnUiThread { renderPad(idx) } })
            val b = findViewById<Button>(padIds[i])
            buttons[i] = b
            b.setOnClickListener { onPadTap(idx) }
            b.setOnLongClickListener {
                selectedPad = idx
                pads[idx].clear()
                setStatus("")
                true
            }
            val t = findViewById<Button>(toggleIds[i])
            toggles[i] = t
            t.setOnClickListener { onToggleTap(idx) }
            renderPad(i)
        }

        findViewById<Button>(R.id.playAll).setOnClickListener {
            var any = false
            pads.forEach {
                if (it.pcm != null && it.state != LoopPad.State.PLAYING) {
                    it.startPlayback(); any = true
                }
            }
            if (!any) setStatus("Nothing to play yet — record a pad or load a sound first.")
        }
        findViewById<Button>(R.id.stopAll).setOnClickListener {
            pads.forEach { if (it.state == LoopPad.State.PLAYING) it.stopPlayback() }
        }
        findViewById<Button>(R.id.soundsBtn).setOnClickListener { showSoundsDialog() }
        findViewById<Button>(R.id.humBtn).setOnClickListener { showHumDialog() }
        findViewById<Button>(R.id.exportBtn).setOnClickListener { exportLoop() }
        findViewById<Button>(R.id.flatsToggle).setOnClickListener { v ->
            useFlats = !useFlats
            (v as Button).text = if (useFlats) "♭" else "♯"
            renderPitch()
            renderRange()
        }
        ui.post(ticker)
    }

    private fun onPadTap(i: Int) {
        selectedPad = i
        val pad = pads[i]
        when (pad.state) {
            LoopPad.State.EMPTY -> {
                if (!hasMicPermission()) {
                    pendingPad = i
                    requestPermissions(arrayOf(Manifest.permission.RECORD_AUDIO), REQ_MIC)
                    return
                }
                if (!pad.startRecording()) {
                    setStatus("Finish the current take first — tap the red pad to loop it.")
                } else setStatus("")
            }
            LoopPad.State.RECORDING -> pad.stopRecording() // stops, then auto-plays
            LoopPad.State.PLAYING -> pad.stopPlayback()
            LoopPad.State.READY -> pad.startPlayback()
        }
    }

    /** "Play together" number row: tap a number to add that pad to
     *  what's playing, tap it again to drop it back out. */
    private fun onToggleTap(i: Int) {
        selectedPad = i
        val pad = pads[i]
        when (pad.state) {
            LoopPad.State.PLAYING -> pad.stopPlayback()
            LoopPad.State.READY -> pad.startPlayback()
            LoopPad.State.EMPTY ->
                setStatus("Pad ${i + 1} is empty — tap the big pad ${i + 1} to record it first.")
            LoopPad.State.RECORDING ->
                setStatus("Pad ${i + 1} is recording — tap the big pad to loop it first.")
        }
    }

    private fun renderToggle(i: Int) {
        val t = toggles[i] ?: return
        val n = i + 1
        when (pads[i].state) {
            LoopPad.State.PLAYING -> {
                t.backgroundTintList = ColorStateList.valueOf(0xFF274435.toInt())
                t.text = "▶ $n"
            }
            LoopPad.State.READY -> {
                t.backgroundTintList = ColorStateList.valueOf(0xFF4A4A66.toInt())
                t.text = "$n"
            }
            else -> {
                t.backgroundTintList = ColorStateList.valueOf(0xFF2C2C3E.toInt())
                t.text = "$n"
            }
        }
    }

    private fun hasMicPermission(): Boolean =
        checkSelfPermission(Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED

    override fun onRequestPermissionsResult(
        requestCode: Int, permissions: Array<out String>, grantResults: IntArray
    ) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode != REQ_MIC) return
        val granted = grantResults.isNotEmpty() &&
            grantResults[0] == PackageManager.PERMISSION_GRANTED
        if (granted) {
            val i = pendingPad
            pendingPad = -1
            if (i in pads.indices && pads[i].state == LoopPad.State.EMPTY) {
                if (!pads[i].startRecording()) {
                    setStatus("The microphone could not be opened just now — try again.")
                }
            }
        } else {
            pendingPad = -1
            setStatus(
                "Woven Oak needs the microphone to record your loops. " +
                "Nothing is uploaded — recordings never leave this phone. " +
                "Tap a pad to try again, and allow the microphone."
            )
        }
    }

    /** Live pitch readout: analyse the recording pad's latest audio
     *  off the UI thread, then show the note, the drift, and the
     *  session range it extends. */
    private fun analyseLivePitch(pad: LoopPad) {
        if (pitchBusy) return
        val frame = pad.recentSamples(4096)
        if (frame.size < 2048) return
        pitchBusy = true
        thread(name = "wovenoak-pitch") {
            val reading = PitchTracker.analyse(frame, LoopPad.SAMPLE_RATE)
            runOnUiThread {
                pitchBusy = false
                if (pad.state != LoopPad.State.RECORDING) return@runOnUiThread
                lastReading = reading
                if (reading.voiced) {
                    val m = reading.nearestMidi
                    if (m < rangeLow) rangeLow = m
                    if (m > rangeHigh) rangeHigh = m
                    meterWasActive = true
                }
                renderPitch()
                renderRange()
            }
        }
    }

    private fun renderPitch() {
        val r = lastReading
        if (r != null && r.voiced) {
            pitchNote.text = NoteNames.name(r.nearestMidi, useFlats)
            centsMeter.setReading(r.cents, true)
        } else {
            pitchNote.text = "—"
            centsMeter.setReading(0, false)
        }
    }

    private fun renderRange() {
        pitchRange.text = if (rangeLow <= rangeHigh) {
            "Your range: ${NoteNames.name(rangeLow, useFlats)} – ${NoteNames.name(rangeHigh, useFlats)}"
        } else {
            "Your range: —"
        }
    }

    /** Built-in sound library: synthesized voices, loaded on the
     *  selected pad — no recording needed. */
    private fun showSoundsDialog() {
        val padIndex = selectedPad
        val names = Instruments.VOICES.map { it.name }.toTypedArray()
        AlertDialog.Builder(this)
            .setTitle("Built-in sounds → pad ${padIndex + 1}\n(synthesized, in the character of)")
            .setItems(names) { _, which ->
                val voice = Instruments.VOICES[which]
                setStatus("Making the ${voice.name} sound…")
                thread(name = "wovenoak-synth") {
                    val data = Synth.renderPhrase(voice, LoopPad.SAMPLE_RATE)
                    runOnUiThread {
                        val pad = pads[padIndex]
                        if (pad.state == LoopPad.State.RECORDING) {
                            setStatus("Finish the current take first — tap the red pad to loop it.")
                        } else {
                            pad.loadPcm(data)
                            setStatus("${voice.name} is looping on pad ${padIndex + 1}.")
                        }
                    }
                }
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    /** Hum-to-instrument: re-voice the selected pad's recorded hum in
     *  a chosen built-in instrument. Drums are not offered — percussion
     *  has no pitch for a hum to map onto. */
    private fun showHumDialog() {
        val padIndex = selectedPad
        val pad = pads[padIndex]
        val take = pad.rawTake
        if (take == null) {
            setStatus("Hum or sing into pad ${padIndex + 1} first — then this turns it into an instrument.")
            return
        }
        val voices = Instruments.melodicVoices()
        val names = voices.map { it.name }.toTypedArray()
        AlertDialog.Builder(this)
            .setTitle("Play pad ${padIndex + 1}'s hum as…")
            .setItems(names) { _, which ->
                val voice = voices[which]
                setStatus("Turning your hum into ${voice.name}…")
                thread(name = "wovenoak-hum") {
                    val converted = HumConverter.convert(take, LoopPad.SAMPLE_RATE, voice)
                    runOnUiThread {
                        if (converted == null) {
                            setStatus("Couldn't hear a clear melody in that take — try humming it a little slower and steadier.")
                        } else {
                            pad.loadPcm(converted)
                            setStatus("Your hum, as ${voice.name}, is looping on pad ${padIndex + 1}.")
                        }
                    }
                }
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    /**
     * Gifting: save the selected pad's loop as a WAV file (system
     * save dialog — no permission needed), then offer the share
     * sheet so it can be handed straight to another musician.
     */
    private var pendingWav: ByteArray? = null
    private var pendingWavPad: Int = -1

    private fun exportLoop() {
        val padIndex = selectedPad
        val pad = pads[padIndex]
        val data = pad.pcm
        if (data == null) {
            setStatus("Pad ${padIndex + 1} has no loop yet — record one or load a sound first.")
            return
        }
        if (pad.state == LoopPad.State.RECORDING) {
            setStatus("Finish the current take first — tap the red pad to loop it.")
            return
        }
        pendingWav = Wav.encode(data, LoopPad.SAMPLE_RATE)
        pendingWavPad = padIndex
        val intent = Intent(Intent.ACTION_CREATE_DOCUMENT).apply {
            addCategory(Intent.CATEGORY_OPENABLE)
            type = "audio/*"
            putExtra(Intent.EXTRA_TITLE, "woven-oak-pad${padIndex + 1}-loop.wav")
        }
        @Suppress("DEPRECATION")
        startActivityForResult(intent, REQ_EXPORT)
    }

    @Deprecated("Framework Activity result flow; fine for this app.")
    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (requestCode != REQ_EXPORT) return
        val wav = pendingWav
        val padIndex = pendingWavPad
        pendingWav = null
        pendingWavPad = -1
        if (resultCode != RESULT_OK || wav == null) return
        val uri: Uri = data?.data ?: return
        thread(name = "wovenoak-export") {
            val ok = try {
                contentResolver.openOutputStream(uri)?.use { it.write(wav) }
                true
            } catch (e: Exception) {
                false
            }
            runOnUiThread {
                if (!ok) {
                    setStatus("That save didn't work — try Export again.")
                } else {
                    setStatus("Pad ${padIndex + 1}'s loop is saved as a WAV. Sharing it is the gift — pass it on.")
                    val share = Intent(Intent.ACTION_SEND).apply {
                        type = "audio/wav"
                        putExtra(Intent.EXTRA_STREAM, uri)
                        addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                    }
                    startActivity(Intent.createChooser(share, "Share your loop gift"))
                }
            }
        }
    }

    private fun renderPad(i: Int) {
        renderToggle(i)
        val pad = pads[i]
        val b = buttons[i] ?: return
        val n = i + 1
        when (pad.state) {
            LoopPad.State.EMPTY -> {
                stopPulse(i)
                b.backgroundTintList = ColorStateList.valueOf(dimTints[i])
                b.text = "$n\nTap to record"
            }
            LoopPad.State.RECORDING -> {
                stopPulse(i)
                b.backgroundTintList = ColorStateList.valueOf(recordingTint)
                val secs = (System.currentTimeMillis() - pad.recordStartedAtMs) / 1000.0
                b.text = "$n\n● Recording %.1fs\nTap to loop it".format(secs)
            }
            LoopPad.State.PLAYING -> {
                b.backgroundTintList = ColorStateList.valueOf(playTints[i])
                b.text = "$n\n▶ Looping ${fmt(pad.durationSec)}\nTap to stop · hold to clear"
                startPulse(i, pad.durationSec)
            }
            LoopPad.State.READY -> {
                stopPulse(i)
                b.backgroundTintList = ColorStateList.valueOf(readyTints[i])
                b.text = "$n\n❚❚ Loop ready ${fmt(pad.durationSec)}\nTap to play · hold to clear"
            }
        }
    }

    /** The pad breathes once per loop — your eyes feel the restart. */
    private fun startPulse(i: Int, loopSec: Float) {
        val b = buttons[i] ?: return
        if (animators[i]?.isRunning == true) return
        val ms = (loopSec * 1000).toLong().coerceIn(350L, 6000L)
        val anim = ValueAnimator.ofFloat(0.78f, 1.0f).apply {
            duration = ms
            repeatCount = ValueAnimator.INFINITE
            repeatMode = ValueAnimator.REVERSE
            interpolator = LinearInterpolator()
            addUpdateListener { b.alpha = it.animatedValue as Float }
        }
        animators[i] = anim
        anim.start()
    }

    private fun stopPulse(i: Int) {
        animators[i]?.cancel()
        animators[i] = null
        buttons[i]?.alpha = 1.0f
    }

    private fun fmt(sec: Float): String = "%.1fs".format(sec)

    private fun setStatus(text: String) { status.text = text }

    override fun onDestroy() {
        ui.removeCallbacks(ticker)
        pads.forEach { it.release() }
        super.onDestroy()
    }

    companion object {
        private const val REQ_MIC = 1001
        private const val REQ_EXPORT = 1002
    }
}
