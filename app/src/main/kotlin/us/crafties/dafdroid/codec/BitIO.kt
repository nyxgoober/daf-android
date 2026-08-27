package us.crafties.dafdroid.codec

import java.io.ByteArrayOutputStream

class BitWriter {
    private val buffer = ByteArrayOutputStream(1 shl 16)
    private var bitBuffer = 0L
    private var bitCount = 0

    private inline fun flushWhileFull() {
        while (bitCount >= 8) {
            bitCount -= 8
            val b = ((bitBuffer ushr bitCount) and 0xFFL).toInt()
            buffer.write(b)
        }
        // keep only remaining bits
        if (bitCount > 0) bitBuffer = bitBuffer and ((1L shl bitCount) - 1) else bitBuffer = 0L
    }

    fun writeBit(bit: Int) {
        bitBuffer = (bitBuffer shl 1) or (bit and 1).toLong()
        bitCount++
        if (bitCount >= 8) flushWhileFull()
    }

    fun writeUnary(q: Int) {
        // emit q zeros then a 1 — chunked to avoid shifting >63 bits at once
        var remaining = q
        while (remaining > 0) {
            val chunk = minOf(remaining, 24)
            // shift in zeros
            bitBuffer = bitBuffer shl chunk
            bitCount += chunk
            flushWhileFull()
            remaining -= chunk
        }
        bitBuffer = (bitBuffer shl 1) or 1L
        bitCount++
        if (bitCount >= 8) flushWhileFull()
    }

    fun writeBits(value: Int, bits: Int) {
        if (bits <= 0) return
        // mask value to bits width
        val v = value.toLong() and ((1L shl bits) - 1)
        bitBuffer = (bitBuffer shl bits) or v
        bitCount += bits
        flushWhileFull()
    }

    fun finish(): ByteArray {
        if (bitCount > 0) {
            val b = ((bitBuffer shl (8 - bitCount)) and 0xFFL).toInt()
            buffer.write(b)
            bitBuffer = 0L
            bitCount = 0
        }
        return buffer.toByteArray()
    }
}

class BitReader(private val bytes: ByteArray, private val startOffset: Int = 0) {
    private var byteIdx = startOffset
    private var bitBuffer = 0
    private var bitsInBuffer = 0

    private inline fun ensure(n: Int) {
        while (bitsInBuffer < n && byteIdx < bytes.size) {
            bitBuffer = (bitBuffer shl 8) or (bytes[byteIdx].toInt() and 0xFF)
            bitsInBuffer += 8
            byteIdx++
        }
    }

    fun readBit(): Int {
        ensure(1)
        bitsInBuffer--
        val bit = (bitBuffer ushr bitsInBuffer) and 1
        // mask off consumed bit by keeping only remaining
        if (bitsInBuffer > 0) bitBuffer = bitBuffer and ((1 shl bitsInBuffer) - 1) else bitBuffer = 0
        return bit
    }

    fun readUnary(): Int {
        var q = 0
        while (true) {
            ensure(1)
            // fast path: count zeros in buffered bits without per-bit loop
            if (bitsInBuffer > 0) {
                // peek bitsInBuffer bits: if all zeros, consume them all
                if (bitBuffer == 0) {
                    q += bitsInBuffer
                    bitsInBuffer = 0
                    bitBuffer = 0
                    continue
                }
                // count leading zeros before next 1
                // bitBuffer holds bitsInBuffer bits in low positions, MSB is next bit
                // e.g., bitsInBuffer=5, bitBuffer=0b00101 -> next bits are 0,0,1...
                var zeros = 0
                // scan from MSB
                for (i in bitsInBuffer - 1 downTo 0) {
                    if (((bitBuffer ushr i) and 1) == 0) zeros++ else break
                }
                if (zeros == bitsInBuffer) {
                    // all remaining buffered bits were zero, but bitBuffer !=0 guarantees not, so unreachable
                    q += zeros
                    bitsInBuffer = 0
                    bitBuffer = 0
                } else {
                    // consume zeros+1
                    q += zeros
                    bitsInBuffer -= (zeros + 1)
                    if (bitsInBuffer > 0) bitBuffer = bitBuffer and ((1 shl bitsInBuffer) - 1) else bitBuffer = 0
                    return q
                }
            } else {
                // need more bytes — will loop and ensure
            }
        }
    }

    fun readBits(bits: Int): Int {
        if (bits <= 0) return 0
        ensure(bits)
        bitsInBuffer -= bits
        val v = (bitBuffer ushr bitsInBuffer) and ((1 shl bits) - 1)
        if (bitsInBuffer > 0) bitBuffer = bitBuffer and ((1 shl bitsInBuffer) - 1) else bitBuffer = 0
        return v
    }
}
