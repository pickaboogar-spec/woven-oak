/*
 * Woven Oak — Copyright (C) 2026 Paul Blanchard (Damonini) and Pepper —
 * Damonini and Pepper Software Development.
 * Free software under the GNU General Public License v3; see LICENSE.
 *
 * The check suite for the voice work (Calibrate / VoiceBank /
 * VoicePlay): pure-Kotlin logic exercised on the JVM with
 * synthesized sung tones, so the rules can be verified without
 * a phone. Run it with tests/run-checks.sh. VoiceBank touches
 * android.content.Context only for its files directory, so the
 * script compiles a two-method stub for the JVM run; everything
 * else under test is the exact code that ships in the app.
 */
import com.damonini.wovenoak.*
import java.io.File
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.sin
import kotlin.math.sqrt

val SR = 44100
var passed = 0
var failed = 0
val failures = mutableListOf<String>()

fun check(name: String, cond: Boolean, detail: String = "") {
    if (cond) { passed++; println("PASS  $name") }
    else { failed++; failures.add("$name $detail"); println("FAIL  $name  $detail") }
}

/** A sung-like tone: fundamental + two harmonics, gentle envelope. */
fun tone(midi: Float, sec: Float, wobbleSemis: Float = 0f, wobbleHz: Float = 0f): ShortArray {
    val n = (sec * SR).toInt()
    val out = ShortArray(n)
    var phase = 0.0
    for (i in 0 until n) {
        val t = i.toDouble() / SR
        val bend = if (wobbleHz > 0f) wobbleSemis * sin(2 * PI * wobbleHz * t) else 0.0
        val f = 440.0 * Math.pow(2.0, (midi - 69.0 + bend) / 12.0)
        phase += 2 * PI * f / SR
        val env = minOf(1.0, minOf(i, n - i).toDouble() / (0.03 * SR))
        val s = (sin(phase) + 0.5 * sin(2 * phase) + 0.25 * sin(3 * phase)) / 1.75
        out[i] = (s * env * 0.7 * 32767).toInt().toShort()
    }
    return out
}

fun analyseMidi(pcm: ShortArray, atSec: Float): Float {
    val start = (atSec * SR).toInt()
    val frame = pcm.copyOfRange(start, minOf(start + 4096, pcm.size))
    return PitchTracker.analyse(frame, SR).midiFloat
}

fun rms(pcm: ShortArray, fromSec: Float, toSec: Float): Double {
    var acc = 0.0; var n = 0
    for (i in (fromSec * SR).toInt() until minOf((toSec * SR).toInt(), pcm.size)) {
        val x = pcm[i] / 32768.0; acc += x * x; n++
    }
    return if (n == 0) 0.0 else sqrt(acc / n)
}

fun main() {
    val a4 = tone(69f, 2f)

    // --- Calibrate.judge ---
    val v1 = Calibrate.judge(a4, SR, 69)
    check("judge accepts steady on-target A4", v1.accepted, "verdict=$v1")
    check("judge heard A4 within a quarter semitone", abs(v1.heardMidi - 69f) < 0.25f, "heard=${v1.heardMidi}")
    check("judge voiced ratio > 0.9 on a sung tone", v1.voicedRatio > 0.9f, "ratio=${v1.voicedRatio}")
    val v2 = Calibrate.judge(a4, SR, 72)
    check("judge rejects A4 aimed at C5", !v2.accepted, "verdict=$v2")
    check("judge reports ~-300 cents for A4 vs C5", abs(v2.centsOff + 300f) < 40f, "cents=${v2.centsOff}")
    val v3 = Calibrate.judge(ShortArray(2 * SR), SR, 69)
    check("judge rejects silence", !v3.accepted && v3.voicedRatio == 0f, "verdict=$v3")
    val wob = tone(69f, 2f, wobbleSemis = 1f, wobbleHz = 5f)
    val v4 = Calibrate.judge(wob, SR, 69)
    check("judge rejects a wobbling tone as unsteady", !v4.accepted && v4.stability > 0.35f, "stab=${v4.stability}")

    // --- Calibrate.comfortableNote ---
    check("comfortableNote finds C3", Calibrate.comfortableNote(tone(48f, 2f), SR) == 48)
    check("comfortableNote returns null on silence", Calibrate.comfortableNote(ShortArray(2 * SR), SR) == null)

    // --- Calibrate.steadiestSecond ---
    check("steadiestSecond of 3s is exactly 1s", Calibrate.steadiestSecond(tone(60f, 3f), SR).size == SR)
    check("steadiestSecond of 0.5s keeps 0.5s", Calibrate.steadiestSecond(tone(60f, 0.5f), SR).size == SR / 2)

    // --- VoiceBank ---
    val dir = File("${System.getProperty("java.io.tmpdir")}/wo-bankfiles").apply { deleteRecursively(); mkdirs() }
    val bank = VoiceBank(android.content.Context(dir))
    check("empty bank: count 0, nearest null", bank.count() == 0 && bank.nearest(60) == null)
    val s60 = tone(60f, 1f); val s64 = tone(64f, 1f)
    bank.save(60, s60); bank.save(64, s64)
    check("bank notes sorted [60, 64]", bank.notes() == listOf(60, 64), "notes=${bank.notes()}")
    check("bank low/high", bank.low() == 60 && bank.high() == 64)
    check("bank nearest(63) is 64, nearest(61) is 60", bank.nearest(63) == 64 && bank.nearest(61) == 60)
    check("bank load round-trips sample content", bank.load(60)!!.contentEquals(s60))
    val s60b = tone(60f, 0.5f)
    bank.save(60, s60b)
    check("bank save replaces earlier take", bank.load(60)!!.size == SR / 2)
    bank.save(60, s60)
    bank.clear()
    check("bank clear empties it", bank.count() == 0 && bank.notes().isEmpty())

    // --- VoiceShifter ---
    val up = VoiceShifter.shift(a4, 12f)
    check("shift +12 lands on A5", abs(analyseMidi(up, 0.8f) - 81f) < 0.35f, "heard=${analyseMidi(up, 0.8f)}")
    val down = VoiceShifter.shift(a4, -12f)
    check("shift -12 lands on A3", abs(analyseMidi(down, 0.8f) - 57f) < 0.35f, "heard=${analyseMidi(down, 0.8f)}")
    check("shift by 0 is an identical copy", VoiceShifter.shift(a4, 0f).contentEquals(a4))
    check("shift preserves length", up.size == a4.size)

    // --- VoiceInstrument ---
    for (m in listOf(57, 60, 64, 67, 69, 72)) bank.save(m, tone(m.toFloat(), 1f))
    val n65 = VoiceInstrument.note(bank, 65, 1.0f, SR)
    check("voice note renders at requested length", n65 != null && n65.size == SR, "size=${n65?.size}")
    check("voice note 65 (shifted +1 from 64) reads as F4", n65 != null && abs(analyseMidi(n65, 0.4f) - 65f) < 0.4f, "heard=${n65?.let { analyseMidi(it, 0.4f) }}")
    val nLong = VoiceInstrument.note(bank, 60, 2.0f, SR)
    check("voice note loop-extends to 2s", nLong != null && nLong.size == 2 * SR, "size=${nLong?.size}")
    check("loop-extended note still sounds in its second half", nLong != null && rms(nLong, 1.4f, 1.9f) > 0.05, "rms=${nLong?.let { rms(it, 1.4f, 1.9f) }}")
    val emptyBank = VoiceBank(android.content.Context(File("${System.getProperty("java.io.tmpdir")}/wo-emptybank").apply { deleteRecursively(); mkdirs() }))
    check("voice note with empty bank is null", VoiceInstrument.note(emptyBank, 60, 1f, SR) == null)

    // --- VoiceInstrument.convert ---
    val hum = ShortArray((1.4f * SR).toInt())
    tone(69f, 0.7f).copyInto(hum, 0)
    tone(72f, 0.7f).copyInto(hum, (0.7f * SR).toInt())
    val conv = VoiceInstrument.convert(hum, SR, bank)
    check("hum converts in your own voice (non-null, same length)", conv != null && conv.size == hum.size)
    check("converted first note reads A4", conv != null && abs(analyseMidi(conv, 0.3f) - 69f) < 0.5f, "heard=${conv?.let { analyseMidi(it, 0.3f) }}")
    check("converted second note reads C5", conv != null && abs(analyseMidi(conv, 1.0f) - 72f) < 0.5f, "heard=${conv?.let { analyseMidi(it, 1.0f) }}")
    check("convert with empty bank is null", VoiceInstrument.convert(hum, SR, emptyBank) == null)

    // --- VoiceInstrument.renderPhrase ---
    val phrase = VoiceInstrument.renderPhrase(bank, SR)
    val expectLen = ((6 * 0.34f + 0.25f) * SR).toInt()
    check("voice phrase renders at phrase length", phrase != null && abs(phrase.size - expectLen) < 200, "size=${phrase?.size} expect=$expectLen")
    check("voice phrase is audible", phrase != null && rms(phrase, 0.1f, 0.4f) > 0.05)

    println()
    println("RESULT: $passed passed, $failed failed of ${passed + failed}")
    if (failures.isNotEmpty()) { println("FAILURES:"); failures.forEach { println(" - $it") } }
    kotlin.system.exitProcess(if (failed == 0) 0 else 1)
}
