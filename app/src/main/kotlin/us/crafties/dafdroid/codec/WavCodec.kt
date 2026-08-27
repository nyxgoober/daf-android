package us.crafties.dafdroid.codec

import java.io.ByteArrayOutputStream
import java.nio.ByteBuffer
import java.nio.ByteOrder


object WavCodec {

    fun parse(bytes: ByteArray): DAFAudio {
        val bb = ByteBuffer.wrap(bytes).order(ByteOrder.BIG_ENDIAN)
        require(bb.getInt(0) == 0x52494646) { "Not a RIFF/WAV file" }
        require(bb.getInt(8) == 0x57415645) { "Not a WAVE file" }

        val le = ByteBuffer.wrap(bytes).order(ByteOrder.LITTLE_ENDIAN)

        var pos = 12
        var numChannels = 0
        var sampleRate = 0
        var bitsPerSample = 0
        var dataOffset = -1
        var dataLength = 0

        while (pos + 8 <= bytes.size) {
            val chunkId = bb.getInt(pos)
            val chunkSize = le.getInt(pos + 4)
            val chunkStart = pos + 8
            when (chunkId) {
                0x666d7420 -> {
                    numChannels = le.getShort(chunkStart + 2).toInt() and 0xFFFF
                    sampleRate = le.getInt(chunkStart + 4)
                    bitsPerSample = le.getShort(chunkStart + 14).toInt() and 0xFFFF
                }
                0x64617461 -> {
                    dataOffset = chunkStart
                    dataLength = chunkSize
                }
            }
            pos = chunkStart + chunkSize + (chunkSize and 1)
        }

        require(sampleRate != 0) { "No fmt chunk found" }
        require(dataOffset >= 0) { "No data chunk found" }
        require(bitsPerSample == 16) { "Only 16-bit PCM WAV is supported" }

        val totalSamples = dataLength / 2
        val numFrames = totalSamples / numChannels
        val channels = Array(numChannels) { ShortArray(numFrames) }

        // Fast deinterleave: manual little-endian short decode without ByteBuffer per sample
        var p = dataOffset
        if (numChannels == 1) {
            val ch = channels[0]
            for (i in 0 until numFrames) {
                ch[i] = ((bytes[p].toInt() and 0xFF) or ((bytes[p + 1].toInt() shl 8))).toShort()
                p += 2
            }
        } else if (numChannels == 2) {
            val c0 = channels[0]; val c1 = channels[1]
            for (i in 0 until numFrames) {
                c0[i] = ((bytes[p].toInt() and 0xFF) or ((bytes[p + 1].toInt() shl 8))).toShort()
                c1[i] = ((bytes[p + 2].toInt() and 0xFF) or ((bytes[p + 3].toInt() shl 8))).toShort()
                p += 4
            }
        } else {
            for (i in 0 until numFrames) {
                for (c in 0 until numChannels) {
                    channels[c][i] = ((bytes[p].toInt() and 0xFF) or ((bytes[p + 1].toInt() shl 8))).toShort()
                    p += 2
                }
            }
        }

        return DAFAudio(sampleRate, numChannels, 2, numFrames, channels)
    }

    fun build(audio: DAFAudio): ByteArray {
        val numFrames = audio.numFrames
        val numChannels = audio.numChannels
        val blockAlign = numChannels * 2
        val dataSize = numFrames * blockAlign

        val out = ByteArrayOutputStream(44 + dataSize)
        val header = ByteBuffer.allocate(44).order(ByteOrder.LITTLE_ENDIAN)

        // Write header manually to avoid repeated order switches
        header.putInt(0x46464952) // "RIFF" little-endian already? Actually "RIFF" as bytes
        // Fix: write as bytes "RIFF" big-endian string then handle size little-endian
        header.position(0)
        header.put("RIFF".toByteArray(Charsets.US_ASCII))
        header.putInt(36 + dataSize)
        header.put("WAVE".toByteArray(Charsets.US_ASCII))
        header.put("fmt ".toByteArray(Charsets.US_ASCII))
        header.putInt(16)
        header.putShort(1)
        header.putShort(numChannels.toShort())
        header.putInt(audio.sampleRate)
        header.putInt(audio.sampleRate * blockAlign)
        header.putShort(blockAlign.toShort())
        header.putShort(16)
        header.put("data".toByteArray(Charsets.US_ASCII))
        header.putInt(dataSize)

        out.write(header.array())

        // Fast interleave: write directly to byte array without ByteBuffer per sample
        val dataBytes = ByteArray(dataSize)
        var p = 0
        if (numChannels == 1) {
            val c0 = audio.channels[0]
            for (i in 0 until numFrames) {
                val s = c0[i].toInt()
                dataBytes[p++] = (s and 0xFF).toByte()
                dataBytes[p++] = ((s ushr 8) and 0xFF).toByte()
            }
        } else if (numChannels == 2) {
            val c0 = audio.channels[0]; val c1 = audio.channels[1]
            for (i in 0 until numFrames) {
                val s0 = c0[i].toInt(); val s1 = c1[i].toInt()
                dataBytes[p++] = (s0 and 0xFF).toByte()
                dataBytes[p++] = ((s0 ushr 8) and 0xFF).toByte()
                dataBytes[p++] = (s1 and 0xFF).toByte()
                dataBytes[p++] = ((s1 ushr 8) and 0xFF).toByte()
            }
        } else {
            for (i in 0 until numFrames) {
                for (c in 0 until numChannels) {
                    val s = audio.channels[c][i].toInt()
                    dataBytes[p++] = (s and 0xFF).toByte()
                    dataBytes[p++] = ((s ushr 8) and 0xFF).toByte()
                }
            }
        }
        out.write(dataBytes)

        return out.toByteArray()
    }
}
