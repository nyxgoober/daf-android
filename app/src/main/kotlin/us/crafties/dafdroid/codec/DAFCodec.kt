package us.crafties.dafdroid.codec

import java.io.ByteArrayOutputStream
import java.nio.ByteBuffer
import java.nio.ByteOrder


fun interface CodecProgressListener {
    fun onProgress(fraction: Float, message: String)
}

object DAFCodec {

    // branchless zigzag: (n << 1) ^ (n >> 31)
    private inline fun zigzagEncode(n: Int): Int = (n shl 1) xor (n shr 31)
    private inline fun zigzagDecode(u: Int): Int = (u ushr 1) xor -(u and 1)

    private inline fun predict(samples: ShortArray, i: Int): Int = when {
        i == 0 -> 0
        i == 1 -> samples[i - 1].toInt()
        else -> (samples[i - 1].toInt() shl 1) - samples[i - 2].toInt()
    }

    private fun bestRiceK(residuals: IntArray, size: Int): Int {
        if (size == 0) return 0
        var sumAbs = 0L
        for (i in 0 until size) {
            val v = residuals[i]
            sumAbs += if (v >= 0) v.toLong() else -v.toLong()
        }
        val meanAbs = sumAbs.toDouble() / size
        // Bit-exact with original: smallest k with (1<<k) >= meanAbs+1, capped at 30
        var k = 0
        while ((1 shl k) < meanAbs + 1 && k < 30) k++
        return k
    }

    fun encode(audio: DAFAudio, listener: CodecProgressListener? = null): ByteArray {
        val totalBlocks = audio.channels.sumOf { ch -> (ch.size + DAFFormat.BLOCK_SIZE - 1) / DAFFormat.BLOCK_SIZE }
        // --- Phase 1: compute Rice k per block in parallel (file-identical, just faster) ---
        val blockKs = IntArray(totalBlocks)
        // Build ordered task list: channel-major order matching final file layout
        data class Task(val globalIdx: Int, val channel: Int, val start: Int, val count: Int)
        val tasks = ArrayList<Task>(totalBlocks)
        var g = 0
        for ((c, ch) in audio.channels.withIndex()) {
            var s = 0
            while (s < ch.size) {
                val e = minOf(s + DAFFormat.BLOCK_SIZE, ch.size)
                tasks.add(Task(g++, c, s, e - s))
                s = e
            }
        }
        // Parallel k computation — each task uses thread-local residuals buffer
        java.util.stream.IntStream.range(0, tasks.size).parallel().forEach { ti ->
            val t = tasks[ti]
            val chSamples = audio.channels[t.channel]
            val residuals = IntArray(t.count)
            for (i in 0 until t.count) {
                val idx = t.start + i
                val pred = predict(chSamples, idx)
                residuals[i] = chSamples[idx].toInt() - pred
            }
            blockKs[t.globalIdx] = bestRiceK(residuals, t.count)
        }
        // --- Phase 2: sequential bitstream write in file order (identical file) ---
        val bw = BitWriter()
        val blockMeta = ArrayList<Int>(totalBlocks)
        val residuals = IntArray(DAFFormat.BLOCK_SIZE)
        var blocksDone = 0
        val logInterval = maxOf(1, totalBlocks / 50)
        var taskPos = 0
        for ((c, chSamples) in audio.channels.withIndex()) {
            val n = chSamples.size
            var start = 0
            while (start < n) {
                val end = minOf(start + DAFFormat.BLOCK_SIZE, n)
                val count = end - start
                for (idx in start until end) {
                    val pred = predict(chSamples, idx)
                    residuals[idx - start] = chSamples[idx].toInt() - pred
                }
                val k = blockKs[taskPos++]
                blockMeta.add(k)
                val mask = if (k == 32) -1 else (1 shl k) - 1
                for (i in 0 until count) {
                    val u = zigzagEncode(residuals[i])
                    val q = if (k == 0) u else u ushr k
                    bw.writeUnary(q)
                    if (k > 0) bw.writeBits(u and mask, k)
                }

                blocksDone++
                if (blocksDone % logInterval == 0 || blocksDone == totalBlocks) {
                    listener?.onProgress(
                        blocksDone.toFloat() / totalBlocks,
                        "ch$c block ${(start / DAFFormat.BLOCK_SIZE) + 1}: k=$k"
                    )
                }
                start += DAFFormat.BLOCK_SIZE
            }
        }

        val body = bw.finish()

        val out = ByteArrayOutputStream(DAFFormat.HEADER_FIXED_SIZE + blockMeta.size + body.size)
        val headerBuf = ByteBuffer.allocate(DAFFormat.HEADER_FIXED_SIZE).order(ByteOrder.LITTLE_ENDIAN)
        headerBuf.put(DAFFormat.MAGIC)
        headerBuf.putInt(audio.sampleRate)
        headerBuf.put(audio.numChannels.toByte())
        headerBuf.put(audio.sampleWidth.toByte())
        headerBuf.putInt(audio.numFrames)
        headerBuf.putInt(blockMeta.size)
        out.write(headerBuf.array())
        for (k in blockMeta) out.write(k)
        out.write(body)

        return out.toByteArray()
    }

    fun decode(bytes: ByteArray, listener: CodecProgressListener? = null): DAFAudio {
        require(DAFFormat.isDAF(bytes)) { "Not a DAF file (bad magic bytes)" }

        val header = ByteBuffer.wrap(bytes, 4, DAFFormat.HEADER_FIXED_SIZE - 4).order(ByteOrder.LITTLE_ENDIAN)
        val sampleRate = header.int
        val numChannels = header.get().toInt() and 0xFF
        val sampleWidth = header.get().toInt() and 0xFF
        val numFrames = header.int
        val numBlocks = header.int

        var offset = DAFFormat.HEADER_FIXED_SIZE
        val blockMeta = IntArray(numBlocks) { bytes[offset + it].toInt() and 0xFF }
        offset += numBlocks

        val br = BitReader(bytes, offset)
        val blocksPerChannel = (numFrames + DAFFormat.BLOCK_SIZE - 1) / DAFFormat.BLOCK_SIZE
        val channels = Array(numChannels) { ShortArray(numFrames) }

        var blockIdx = 0
        var blocksDone = 0

        for (c in 0 until numChannels) {
            val chSamples = channels[c]
            var written = 0
            var remaining = numFrames
            for (b in 0 until blocksPerChannel) {
                val count = minOf(DAFFormat.BLOCK_SIZE, remaining)
                val k = blockMeta[blockIdx++]
                val shift = if (k >= 31) 0 else (1 shl k)
                for (i in 0 until count) {
                    val q = br.readUnary()
                    val rem = if (k > 0) br.readBits(k) else 0
                    val u = if (k == 0) q else (q * shift) + rem
                    val residual = zigzagDecode(u)
                    val pred = predict(chSamples, written)
                    chSamples[written] = (pred + residual).toShort()
                    written++
                }
                remaining -= count

                blocksDone++
                if (blocksDone % 32 == 0 || blocksDone == numBlocks) {
                    listener?.onProgress(
                        blocksDone.toFloat() / numBlocks,
                        "ch$c block ${b + 1}/$blocksPerChannel: k=$k"
                    )
                }
            }
        }

        return DAFAudio(sampleRate, numChannels, sampleWidth, numFrames, channels)
    }
}
