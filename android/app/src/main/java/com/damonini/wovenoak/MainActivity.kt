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
import android.graphics.Typeface
import android.text.SpannableString
import android.text.Spanned
import android.text.style.RelativeSizeSpan
import android.text.style.StyleSpan
import android.widget.Button
import android.widget.CheckBox
import android.widget.LinearLayout
import android.widget.SeekBar
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
    /** The rendered chant lives on its own invisible pad. */
    private val chantPad = LoopPad(6) { }
    private val animators = arrayOfNulls<ValueAnimator>(6)
    private val pads = ArrayList<LoopPad>()

    private val ui = Handler(Looper.getMainLooper())
    private var pendingPad: Int = -1
    private var pendingCalib = false

    // Calibration (voice plan, stage 2): the singer's own voice
    // bank, the walk's state, and its dialog's moving parts.
    private lateinit var voiceBank: VoiceBank
    private var calibCapture: CalibCapture? = null
    private var calibStage = CALIB_COMFORT
    private var calibStart = -1
    private var calibTarget = -1
    private var calibTries = 0
    private var calibLine = 0
    private var calibAutoStop: Runnable? = null
    private var calibStep: TextView? = null
    private var calibLyric: TextView? = null
    private var calibInfo: TextView? = null
    private var calibHearBtn: Button? = null
    private var calibSingBtn: Button? = null

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
            // Pad labels (Paul, beta.6): a big number, then the
            // three instructions in small type — sized to FIT the
            // pad, after the beta.4 labels clipped and the beta.5
            // numbers-only pads left people guessing.
            b.textSize = 13f
            b.includeFontPadding = false
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
            if (chantPad.state == LoopPad.State.PLAYING) chantPad.stopPlayback()
        }
        findViewById<Button>(R.id.soundsBtn).setOnClickListener { showSoundsDialog() }
        findViewById<Button>(R.id.humBtn).setOnClickListener { showHumDialog() }
        findViewById<Button>(R.id.exportBtn).setOnClickListener { exportLoop() }
        findViewById<Button>(R.id.chantBtn).setOnClickListener { showChantDialog() }
        findViewById<Button>(R.id.retuneBtn).setOnClickListener { showRetuneDialog() }
        voiceBank = VoiceBank(this)
        findViewById<Button>(R.id.calibBtn).setOnClickListener { showCalibrateDialog() }
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
        if (requestCode == REQ_MIC_CALIB) {
            val grantedCalib = grantResults.isNotEmpty() &&
                grantResults[0] == PackageManager.PERMISSION_GRANTED
            if (grantedCalib && pendingCalib) {
                pendingCalib = false
                showCalibrateDialog()
            } else {
                pendingCalib = false
                setStatus(
                    "Calibration needs the microphone too — the same one " +
                    "permission, used only on this phone. Tap Calibrate to try again."
                )
            }
            return
        }
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
     *  selected pad — no recording needed. Your own calibrated
     *  voice stands at the head of the list (voice plan, stage 3). */
    private fun showSoundsDialog() {
        val padIndex = selectedPad
        val names = (listOf("Your voice — a phrase in your own voice") +
            Instruments.VOICES.map { it.name }).toTypedArray()
        AlertDialog.Builder(this)
            .setTitle("Sounds → pad ${padIndex + 1}\n(built-in ones are synthesized, in the character of)")
            .setItems(names) { _, which ->
                if (which == 0) {
                    loadVoicePhrase(padIndex)
                } else {
                    val voice = Instruments.VOICES[which - 1]
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
        val names = (voices.map { it.name } + "Your voice — from your calibration").toTypedArray()
        AlertDialog.Builder(this)
            .setTitle("Play pad ${padIndex + 1}'s hum as…")
            .setItems(names) { _, which ->
                if (which == voices.size) {
                    convertHumToVoice(padIndex, take)
                } else {
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
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    /** "Your voice" from the Sounds list: a short phrase sung by
     *  the singer's own banked voice, loaded on the pad. */
    private fun loadVoicePhrase(padIndex: Int) {
        if (voiceBank.count() == 0) {
            setStatus("No voice banked yet — tap 🎙 Calibrate… and teach the app your voice first.")
            return
        }
        setStatus("Your voice is warming up…")
        thread(name = "wovenoak-voice") {
            val data = VoiceInstrument.renderPhrase(voiceBank, LoopPad.SAMPLE_RATE)
            runOnUiThread {
                val pad = pads[padIndex]
                if (data == null) {
                    setStatus("Your voice bank couldn't sing just now — try Calibrate once more.")
                } else if (pad.state == LoopPad.State.RECORDING) {
                    setStatus("Finish the current take first — tap the red pad to loop it.")
                } else {
                    pad.loadPcm(data)
                    setStatus("That is your own voice, looping on pad ${padIndex + 1}.")
                }
            }
        }
    }

    /** Hum → your own voice: the selected pad's hum, re-sung by
     *  the calibration bank instead of a synthesized instrument. */
    private fun convertHumToVoice(padIndex: Int, take: ShortArray) {
        if (voiceBank.count() == 0) {
            setStatus("No voice banked yet — tap 🎙 Calibrate… and teach the app your voice first.")
            return
        }
        setStatus("Turning your hum into your own voice…")
        thread(name = "wovenoak-humvoice") {
            val converted = VoiceInstrument.convert(take, LoopPad.SAMPLE_RATE, voiceBank)
            runOnUiThread {
                if (converted == null) {
                    setStatus("Your voice couldn't sing that melody — it may wander outside the notes you banked. Hum it slower, or Calibrate a wider range.")
                } else {
                    pads[padIndex].loadPcm(converted)
                    setStatus("Your hum, sung back in your own voice, is looping on pad ${padIndex + 1}.")
                }
            }
        }
    }

    /**
     * Calibration (voice plan, stage 2 — Paul's design): the app
     * sings first. A guide tone sounds; the singer sings the line
     * back on a steady "ahh"; the range judge (Calibrate.kt)
     * accepts the note only if it was really sung, on pitch, and
     * held steady. The walk starts from a comfortable note, steps
     * down to the bottom of the range, then up to the top. Every
     * accepted note banks its steadiest second in the VoiceBank —
     * on this phone only, under the one microphone permission.
     */
    private fun showCalibrateDialog() {
        if (pads.any { it.state == LoopPad.State.RECORDING }) {
            setStatus("Finish the take on the red pad first — then Calibrate can have the microphone.")
            return
        }
        if (!hasMicPermission()) {
            pendingCalib = true
            requestPermissions(arrayOf(Manifest.permission.RECORD_AUDIO), REQ_MIC_CALIB)
            return
        }
        val ctx = this
        calibStage = CALIB_COMFORT
        calibStart = -1
        calibTarget = -1
        calibTries = 0

        val box = LinearLayout(ctx).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(48, 24, 48, 8)
        }
        val step = TextView(ctx).apply { textSize = 18f; setTypeface(typeface, Typeface.BOLD) }
        val lyric = TextView(ctx).apply {
            textSize = 16f
            setPadding(0, 12, 0, 0)
        }
        val info = TextView(ctx).apply {
            textSize = 14f
            setPadding(0, 12, 0, 0)
        }
        val hear = Button(ctx).apply {
            text = "▶  Hear the note"
            setOnClickListener { playGuideTone() }
        }
        val sing = Button(ctx).apply {
            text = "●  Sing it back"
            setOnClickListener { onCalibSingTap() }
        }
        val erase = Button(ctx).apply {
            text = "Forget my voice (erase the bank)"
            setOnClickListener {
                voiceBank.clear()
                calibInfo?.text = "Voice bank erased. This phone has forgotten your voice — sing again any time."
                setStatus("Voice bank erased.")
            }
        }
        box.addView(step)
        box.addView(lyric)
        box.addView(hear)
        box.addView(sing)
        box.addView(info)
        box.addView(erase)
        calibStep = step
        calibLyric = lyric
        calibInfo = info
        calibHearBtn = hear
        calibSingBtn = sing

        val dialog = AlertDialog.Builder(ctx)
            .setTitle("🎙 Teach the app your voice")
            .setView(box)
            .setNegativeButton("Close", null)
            .create()
        dialog.setOnDismissListener {
            calibAutoStop?.let { ui.removeCallbacks(it) }
            calibAutoStop = null
            calibCapture?.cancel()
            calibCapture = null
            TonePlayer.stop()
            calibStep = null; calibLyric = null; calibInfo = null
            calibHearBtn = null; calibSingBtn = null
        }
        dialog.show()
        refreshCalibUi(
            "First, sing any note that feels comfortable — a steady " +
            "\"ahh\" for about two seconds. That is where the walk starts."
        )
    }

    /** Repaint the calibration dialog for the current stage. */
    private fun refreshCalibUi(message: String) {
        val step = calibStep ?: return
        calibLyric?.text = "\"${CALIB_LYRIC[calibLine % CALIB_LYRIC.size]}\""
        when (calibStage) {
            CALIB_COMFORT -> {
                step.text = "Step 1 of 3 — your comfortable note"
                calibHearBtn?.visibility = android.view.View.GONE
            }
            CALIB_DOWN -> {
                step.text = "Step 2 of 3 — walking down · sing ${NoteNames.name(calibTarget, useFlats)}"
                calibHearBtn?.visibility = android.view.View.VISIBLE
            }
            CALIB_UP -> {
                step.text = "Step 3 of 3 — walking up · sing ${NoteNames.name(calibTarget, useFlats)}"
                calibHearBtn?.visibility = android.view.View.VISIBLE
            }
            else -> {
                step.text = "Your voice is in the bank"
                calibHearBtn?.visibility = android.view.View.GONE
            }
        }
        val banked = voiceBank.count()
        val bankLine = if (banked == 0) {
            "Nothing banked yet."
        } else {
            "Banked so far: $banked note${if (banked == 1) "" else "s"}, " +
                "${NoteNames.name(voiceBank.low()!!, useFlats)} – " +
                "${NoteNames.name(voiceBank.high()!!, useFlats)}."
        }
        calibInfo?.text = "$message\n\n$bankLine"
    }

    /** The guide tone: a soft flute-ish synth note at the target. */
    private fun playGuideTone() {
        val target = if (calibStage == CALIB_DOWN || calibStage == CALIB_UP) calibTarget else -1
        if (target < 0) return
        val voice = Instruments.melodicVoices().firstOrNull { it.name == "Flute" }
            ?: Instruments.melodicVoices().first()
        thread(name = "wovenoak-guide") {
            val tone = Synth.renderNote(voice, Instruments.midiToFreq(target), 1.4f, LoopPad.SAMPLE_RATE)
            runOnUiThread { TonePlayer.play(tone) }
        }
    }

    private fun onCalibSingTap() {
        val cap = calibCapture
        if (cap != null && cap.isCapturing) {
            finishCalibTake()
            return
        }
        val fresh = CalibCapture()
        if (!fresh.start()) {
            setStatus("The microphone could not be opened just now — try Calibrate again.")
            return
        }
        calibCapture = fresh
        calibSingBtn?.text = "■  Done"
        calibHearBtn?.isEnabled = false
        calibInfo?.text = "Listening… sing the line on a steady \"ahh\", then tap Done."
        val auto = Runnable { if (calibCapture?.isCapturing == true) finishCalibTake() }
        calibAutoStop = auto
        ui.postDelayed(auto, 4000)
    }

    /** Judge the finished take and advance the walk. */
    private fun finishCalibTake() {
        calibAutoStop?.let { ui.removeCallbacks(it) }
        calibAutoStop = null
        val cap = calibCapture ?: return
        calibCapture = null
        calibSingBtn?.text = "●  Sing it back"
        calibHearBtn?.isEnabled = true
        val take = cap.stop()
        calibInfo?.text = "Listening to what you sang…"
        val stage = calibStage
        val target = calibTarget
        thread(name = "wovenoak-judge") {
            val sr = LoopPad.SAMPLE_RATE
            var message = ""
            var nextStage = stage
            var nextTarget = target
            when (stage) {
                CALIB_COMFORT -> {
                    val note = Calibrate.comfortableNote(take, sr)
                    if (note == null) {
                        message = "Couldn't hear a steady note there — sing one comfortable " +
                            "\"ahh\", a little louder, and hold it."
                    } else {
                        voiceBank.save(note, Calibrate.steadiestSecond(take, sr))
                        calibStart = note
                        nextStage = CALIB_DOWN
                        nextTarget = note - 1
                        calibTries = 0
                        calibLine++
                        message = "Got it — ${NoteNames.name(note, useFlats)} is home base, and " +
                            "it's banked. Now we walk down: hear each note, sing it back."
                    }
                }
                CALIB_DOWN, CALIB_UP -> {
                    val v = Calibrate.judge(take, sr, target)
                    if (v.accepted) {
                        voiceBank.save(target, Calibrate.steadiestSecond(take, sr))
                        calibTries = 0
                        calibLine++
                        if (stage == CALIB_DOWN) {
                            message = "${NoteNames.name(target, useFlats)} banked. One step lower…"
                            nextTarget = target - 1
                            if (calibStart - nextTarget > 24 || nextTarget < 30) {
                                nextStage = CALIB_UP
                                nextTarget = calibStart + 1
                                message = "${NoteNames.name(target, useFlats)} banked — that's the " +
                                    "bottom for today. Now we walk up."
                            }
                        } else {
                            message = "${NoteNames.name(target, useFlats)} banked. One step higher…"
                            nextTarget = target + 1
                            if (nextTarget - calibStart > 24 || nextTarget > 96) {
                                nextStage = CALIB_DONE
                                message = "That's the top. The walk is done."
                            }
                        }
                    } else {
                        calibTries++
                        val why = when {
                            v.voicedRatio < 0.5f -> "I mostly heard quiet — sing out a bit more."
                            kotlin.math.abs(v.centsOff) > 50f ->
                                "You landed ${if (v.centsOff > 0) "above" else "below"} " +
                                    "${NoteNames.name(target, useFlats)} — hear the note again and aim for its middle."
                            else -> "The note wandered — hold it as steady as you can."
                        }
                        if (calibTries >= 2) {
                            calibTries = 0
                            calibLine++
                            if (stage == CALIB_DOWN) {
                                nextStage = CALIB_UP
                                nextTarget = calibStart + 1
                                message = "$why That seems to be the bottom of your range — " +
                                    "nothing wrong with that. Now we walk up."
                            } else {
                                nextStage = CALIB_DONE
                                message = "$why That seems to be the top of your range. The walk is done."
                            }
                        } else {
                            message = "$why One more try at ${NoteNames.name(target, useFlats)}."
                        }
                    }
                }
                else -> message = "The walk is done — close this and try your voice under Sounds… or Hum → instrument…"
            }
            val doneStage = nextStage
            val doneTarget = nextTarget
            runOnUiThread {
                calibStage = doneStage
                calibTarget = doneTarget
                if (doneStage == CALIB_DONE) {
                    val low = voiceBank.low()
                    val high = voiceBank.high()
                    val summary = if (low != null && high != null) {
                        "Your range: ${NoteNames.name(low, useFlats)} – " +
                            "${NoteNames.name(high, useFlats)} · ${voiceBank.count()} notes banked. " +
                            "Find your voice at the top of Sounds…, and under " +
                            "Hum → instrument… — it never leaves this phone."
                    } else {
                        "No notes were banked this time — sing the walk again whenever you're ready."
                    }
                    refreshCalibUi("$message\n$summary")
                    setStatus(summary)
                } else {
                    refreshCalibUi(message)
                }
            }
        }
    }

    /**
     * Gifting: save the selected pad's loop as a WAV file (system
     * save dialog — no permission needed), then offer the share
     * sheet so it can be handed straight to another musician.
     */
    private var pendingWav: ByteArray? = null
    private var pendingWavPad: Int = -1

    /** Retune (voice plan, stage 1): correct the selected pad's
     *  singing to a scale — the Auto-Tune mechanism, offline, with
     *  the singer holding the dial. Always renders from the pad's
     *  original take, so it can be re-done at any strength. */
    private fun showRetuneDialog() {
        val padIndex = selectedPad
        val pad = pads[padIndex]
        val source = pad.rawTake ?: pad.pcm
        if (source == null) {
            setStatus("Sing into pad ${padIndex + 1} first — then Retune can correct it.")
            return
        }
        val keyNames = arrayOf("C", "C#", "D", "D#", "E", "F", "F#", "G", "G#", "A", "A#", "B")
        var rootPc = 0
        var scaleIdx = 0
        var strengthPct = 80
        val ctx = this
        val box = LinearLayout(ctx).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(48, 24, 48, 8)
        }
        val keyBtn = Button(ctx).apply {
            text = "Key: C"
            setOnClickListener {
                rootPc = (rootPc + 1) % 12
                text = "Key: ${keyNames[rootPc]}"
            }
        }
        val scaleBtn = Button(ctx).apply {
            text = "Scale: ${Retune.SCALES[0].first}"
            setOnClickListener {
                scaleIdx = (scaleIdx + 1) % Retune.SCALES.size
                text = "Scale: ${Retune.SCALES[scaleIdx].first}"
            }
        }
        val strengthLabel = TextView(ctx).apply {
            text = "Strength: $strengthPct%"
            textSize = 16f
        }
        val strengthBar = SeekBar(ctx).apply {
            max = 100
            progress = strengthPct
            setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
                override fun onProgressChanged(s: SeekBar?, p: Int, fromUser: Boolean) {
                    strengthPct = p
                    strengthLabel.text = "Strength: $p%"
                }
                override fun onStartTrackingTouch(s: SeekBar?) {}
                override fun onStopTrackingTouch(s: SeekBar?) {}
            })
        }
        box.addView(keyBtn)
        box.addView(scaleBtn)
        box.addView(strengthLabel)
        box.addView(strengthBar)

        AlertDialog.Builder(ctx)
            .setTitle("Retune pad ${padIndex + 1}")
            .setView(box)
            .setPositiveButton("Retune it") { _, _ ->
                setStatus("Retuning pad ${padIndex + 1}…")
                thread(name = "wovenoak-retune") {
                    val out = try {
                        Retune.retune(
                            source, rootPc, Retune.SCALES[scaleIdx].second,
                            strengthPct / 100f
                        )
                    } catch (e: Exception) { null }
                    runOnUiThread {
                        if (out == null) {
                            setStatus("Retune would not run on that take — tell Pepper.")
                        } else {
                            pad.loadPcm(out)
                            setStatus(
                                "Pad ${padIndex + 1} retuned to ${keyNames[rootPc]} " +
                                "${Retune.SCALES[scaleIdx].first} at $strengthPct% — " +
                                "re-record or re-run any time; it always starts from your original take."
                            )
                        }
                    }
                }
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    /** The Chant template (Paul's design, CHANT-TEMPLATE.md): pour
     *  your own words into the machine — pick a tempo, an anchor pad
     *  whose phrase lands every four beats as the refrain, the
     *  electronic pulse and the robot voice — and your pads render
     *  into one chanting loop. The template ships empty; every word
     *  in it is yours. */
    private fun showChantDialog() {
        var bpm = 120
        var anchorIdx = -1
        var style = ChantRenderer.Style.ROBOT
        val ctx = this
        val box = LinearLayout(ctx).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(48, 24, 48, 8)
        }
        val tempoLabel = TextView(ctx).apply {
            text = "Tempo: $bpm BPM"
            textSize = 16f
        }
        val tempo = SeekBar(ctx).apply {
            max = 50 // 90..140 BPM
            progress = bpm - 90
            setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
                override fun onProgressChanged(s: SeekBar?, p: Int, fromUser: Boolean) {
                    bpm = p + 90
                    tempoLabel.text = "Tempo: $bpm BPM"
                }
                override fun onStartTrackingTouch(s: SeekBar?) {}
                override fun onStopTrackingTouch(s: SeekBar?) {}
            })
        }
        val styleBtn = Button(ctx).apply {
            text = "Style: ${style.label}"
            setOnClickListener {
                val all = ChantRenderer.Style.values()
                style = all[(style.ordinal + 1) % all.size]
                text = "Style: ${style.label}"
                bpm = style.defaultBpm
                tempo.progress = (bpm - 90).coerceIn(0, 50)
                tempoLabel.text = "Tempo: $bpm BPM"
            }
        }
        val anchorBtn = Button(ctx).apply {
            text = "Anchor (refrain): none"
            setOnClickListener {
                anchorIdx = if (anchorIdx >= 5) -1 else anchorIdx + 1
                text = if (anchorIdx < 0) "Anchor (refrain): none"
                       else "Anchor (refrain): pad ${anchorIdx + 1}"
            }
        }
        val pulseBox = CheckBox(ctx).apply {
            text = "Electronic pulse"
            isChecked = true
        }
        val robotBox = CheckBox(ctx).apply {
            text = "Voice treatment (the style's own)"
            isChecked = true
        }
        box.addView(styleBtn)
        box.addView(tempoLabel)
        box.addView(tempo)
        box.addView(anchorBtn)
        box.addView(pulseBox)
        box.addView(robotBox)

        AlertDialog.Builder(ctx)
            .setTitle("Build a chant from your pads")
            .setView(box)
            .setPositiveButton("Build & play") { _, _ ->
                val anchorPcm = if (anchorIdx >= 0) pads.getOrNull(anchorIdx)?.pcm else null
                val phrases = pads
                    .filterIndexed { i, p ->
                        p.pcm != null && p.state != LoopPad.State.RECORDING && i != anchorIdx
                    }
                    .map { it.pcm!! }
                if (phrases.size < 2) {
                    setStatus(
                        "The chant is built from your recorded pads — it needs at least " +
                        "two, and right now ${phrases.size} ${if (phrases.size == 1) "pad has" else "pads have"} " +
                        "a phrase on it. Record a couple of pads, then build."
                    )
                    return@setPositiveButton
                }
                setStatus("Building your chant…")
                thread(name = "wovenoak-chant") {
                    try {
                        val rendered = ChantRenderer.render(
                            phrases, anchorPcm, bpm, style,
                            pulseBox.isChecked, robotBox.isChecked
                        )
                        runOnUiThread {
                            // The chant stands alone: quiet the pads
                            // so what you hear is the chant itself.
                            pads.forEach {
                                if (it.state == LoopPad.State.PLAYING) it.stopPlayback()
                            }
                            chantPad.loadPcm(rendered)
                            setStatus(
                                "Your chant is playing: ${style.label}, ${phrases.size} phrases " +
                                "fitted to $bpm BPM" +
                                if (anchorPcm != null) ", pad ${anchorIdx + 1} as the refrain." else "."
                            )
                        }
                    } catch (e: Exception) {
                        runOnUiThread {
                            setStatus("The chant would not build on this phone — tell Pepper what the status line said before.")
                        }
                    }
                }
            }
            .setNeutralButton("Stop chant") { _, _ ->
                if (chantPad.state == LoopPad.State.PLAYING) chantPad.stopPlayback()
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

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

    /** What every pad says (Paul, beta.6): the number, big, then
     *  how to drive it — tap to record, tap to stop, hold to
     *  clear — small enough to fit the pad whole. State still
     *  reads from the colors, the pulse, and the toggle row. */
    private fun padLabel(n: Int): CharSequence {
        val text = "$n\nTap to record · tap to stop\nHold to clear"
        val span = SpannableString(text)
        span.setSpan(RelativeSizeSpan(2.3f), 0, "$n".length, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
        span.setSpan(StyleSpan(Typeface.BOLD), 0, "$n".length, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
        return span
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
                b.text = padLabel(n)
            }
            LoopPad.State.RECORDING -> {
                stopPulse(i)
                b.backgroundTintList = ColorStateList.valueOf(recordingTint)
                val secs = (System.currentTimeMillis() - pad.recordStartedAtMs) / 1000.0
                b.text = padLabel(n)
            }
            LoopPad.State.PLAYING -> {
                b.backgroundTintList = ColorStateList.valueOf(playTints[i])
                b.text = padLabel(n)
                startPulse(i, pad.durationSec)
            }
            LoopPad.State.READY -> {
                stopPulse(i)
                b.backgroundTintList = ColorStateList.valueOf(readyTints[i])
                b.text = padLabel(n)
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
        calibAutoStop?.let { ui.removeCallbacks(it) }
        calibCapture?.cancel()
        TonePlayer.stop()
        pads.forEach { it.release() }
        super.onDestroy()
    }

    companion object {
        private const val REQ_MIC = 1001
        private const val REQ_EXPORT = 1002
        private const val REQ_MIC_CALIB = 1003

        private const val CALIB_COMFORT = 0
        private const val CALIB_DOWN = 1
        private const val CALIB_UP = 2
        private const val CALIB_DONE = 3

        /** The calibration lyric — an original song written for
         *  this walk; one line per note, sung on a steady "ahh"
         *  or with its words, as the singer likes. */
        private val CALIB_LYRIC = arrayOf(
            "Oh, the oak stands over the water",
            "Sing it low, sing it slow",
            "Down the green and shadowed hollow",
            "Where the hidden rivers flow",
            "Up the hill the morning's climbing",
            "Sing it bright, sing it true",
            "Every note my voice can carry",
            "I will sing them home to you"
        )
    }
}
