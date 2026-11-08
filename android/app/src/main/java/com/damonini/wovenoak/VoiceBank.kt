/*
 * Woven Oak — Copyright (C) 2026 Paul Blanchard (Damonini) and Pepper —
 * Damonini and Pepper Software Development.
 * Free software under the GNU General Public License v3; see LICENSE.
 */
package com.damonini.wovenoak

import android.content.Context
import java.io.File
import java.nio.ByteBuffer
import java.nio.ByteOrder

/**
 * The singer's own voice, kept as a bank of short sung samples —
 * one per MIDI note — captured by the calibration walk.
 *
 * Storage is deliberately plain: raw 16-bit mono PCM at
 * [LoopPad.SAMPLE_RATE], one file per note inside the app's
 * private files directory. Nothing here ever leaves the device;
 * the app's only permission remains the microphone. A sung
 * second is ~88KB, so a full 25-note range costs about 2MB.
 */
class VoiceBank(context: Context) {

    private val dir: File = File(context.filesDir, "voicebank").apply { mkdirs() }

    private fun fileFor(midi: Int) = File(dir, "note_$midi.raw")

    /** Every note the bank currently holds, low to high. */
    fun notes(): List<Int> =
        dir.listFiles()
            ?.mapNotNull { f ->
                f.name.removePrefix("note_").removeSuffix(".raw").toIntOrNull()
            }
            ?.sorted()
            ?: emptyList()

    fun has(midi: Int): Boolean = fileFor(midi).exists()

    /** The banked note closest to [midi] — the sample a playable
     *  note is shifted from. Null when the bank is empty. */
    fun nearest(midi: Int): Int? =
        notes().minByOrNull { kotlin.math.abs(it - midi) }

    fun low(): Int? = notes().firstOrNull()

    fun high(): Int? = notes().lastOrNull()

    fun count(): Int = notes().size

    /** Store one note's sample, replacing any earlier take. */
    fun save(midi: Int, pcm: ShortArray) {
        val bytes = ByteArray(pcm.size * 2)
        ByteBuffer.wrap(bytes).order(ByteOrder.LITTLE_ENDIAN).asShortBuffer().put(pcm)
        fileFor(midi).writeBytes(bytes)
    }

    /** Load one note's sample, or null if that note was never sung. */
    fun load(midi: Int): ShortArray? {
        val f = fileFor(midi)
        if (!f.exists()) return null
        val bytes = f.readBytes()
        val out = ShortArray(bytes.size / 2)
        ByteBuffer.wrap(bytes).order(ByteOrder.LITTLE_ENDIAN).asShortBuffer().get(out)
        return out
    }

    /** Forget the whole voice. The singer can always sing again. */
    fun clear() {
        dir.listFiles()?.forEach { it.delete() }
    }
}
