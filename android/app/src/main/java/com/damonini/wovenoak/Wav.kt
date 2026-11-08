/*
 * Woven Oak — Copyright (C) 2026 Paul Blanchard (Damonini) and Pepper —
 * Damonini and Pepper Software Development.
 * Free software under the GNU General Public License v3; see LICENSE.
 */
package com.damonini.wovenoak

import java.io.ByteArrayOutputStream
import java.nio.ByteBuffer
import java.nio.ByteOrder

/**
 * The gift format: a pad's raw PCM wrapped as a standard WAV file
 * (RIFF, 16-bit mono) that any player, editor or friend can open.
 * Creation is the best gift — a loop you save can become the seed
 * of somebody else's piece.
 */
object Wav {

    fun encode(pcm: ShortArray, sampleRate: Int): ByteArray {
        val dataSize = pcm.size * 2
        val out = ByteArrayOutputStream(44 + dataSize)
        val header = ByteBuffer.allocate(44).order(ByteOrder.LITTLE_ENDIAN)
        header.put("RIFF".toByteArray(Charsets.US_ASCII))
        header.putInt(36 + dataSize)
        header.put("WAVE".toByteArray(Charsets.US_ASCII))
        header.put("fmt ".toByteArray(Charsets.US_ASCII))
        header.putInt(16)                 // fmt chunk size
        header.putShort(1)                // PCM
        header.putShort(1)                // mono
        header.putInt(sampleRate)
        header.putInt(sampleRate * 2)     // byte rate
        header.putShort(2)                // block align
        header.putShort(16)               // bits per sample
        header.put("data".toByteArray(Charsets.US_ASCII))
        header.putInt(dataSize)
        out.write(header.array())
        val data = ByteBuffer.allocate(dataSize).order(ByteOrder.LITTLE_ENDIAN)
        data.asShortBuffer().put(pcm)
        out.write(data.array())
        return out.toByteArray()
    }
}
