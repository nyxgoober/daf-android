package us.crafties.dafdroid.codec

import android.media.MediaCodec
import android.media.MediaExtractor
import android.media.MediaFormat
import java.io.File
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.nio.ShortBuffer


object PlatformAudioDecoder {

    fun decode(file: File, listener: CodecProgressListener? = null): DAFAudio {
        val extractor = MediaExtractor()
        extractor.setDataSource(file.absolutePath)

        var trackIndex = -1
        var format: MediaFormat? = null
        for (i in 0 until extractor.trackCount) {
            val f = extractor.getTrackFormat(i)
            val mime = f.getString(MediaFormat.KEY_MIME) ?: continue
            if (mime.startsWith("audio/")) {
                trackIndex = i
                format = f
                break
            }
        }
        require(trackIndex >= 0 && format != null) { "No audio track found in file" }
        extractor.selectTrack(trackIndex)

        val mime = format.getString(MediaFormat.KEY_MIME)!!
        val sampleRate = format.getInteger(MediaFormat.KEY_SAMPLE_RATE)
        val channelCount = format.getInteger(MediaFormat.KEY_CHANNEL_COUNT)

        val codec = MediaCodec.createDecoderByType(mime)
        codec.configure(format, null, null, 0)
        codec.start()
        var checkedOutputEncoding = false

        val pcmChunks = ArrayList<ShortArray>()
        val bufferInfo = MediaCodec.BufferInfo()
        var sawInputEof = false
        var sawOutputEof = false
        var buffersProcessed = 0
        var lastLoggedPercent = -1
        val durationUs = if (format.containsKey(MediaFormat.KEY_DURATION)) format.getLong(MediaFormat.KEY_DURATION) else -1L

        while (!sawOutputEof) {
            if (!sawInputEof) {
                val inIndex = codec.dequeueInputBuffer(10_000)
                if (inIndex >= 0) {
                    val inBuf = codec.getInputBuffer(inIndex)!!
                    val sampleSize = extractor.readSampleData(inBuf, 0)
                    if (sampleSize < 0) {
                        codec.queueInputBuffer(inIndex, 0, 0, 0, MediaCodec.BUFFER_FLAG_END_OF_STREAM)
                        sawInputEof = true
                    } else {
                        val presentationTimeUs = extractor.sampleTime
                        codec.queueInputBuffer(inIndex, 0, sampleSize, presentationTimeUs, 0)
                        extractor.advance()

                        buffersProcessed++
                        // Update roughly every ~2% of the file rather than a fixed
                        // buffer count, so a short clip and a long album track both
                        // log a similarly small, useful number of lines instead of
                        // the log filling with dozens of identical "decoding source
                        // audio…" entries for a large file. The message now includes
                        // the percentage so each logged line is actually distinct,
                        // instead of repeating the exact same text every time.
                        if (durationUs > 0 && listener != null) {
                            val frac = (presentationTimeUs.toFloat() / durationUs.toFloat()).coerceIn(0f, 1f)
                            val percent = (frac * 100).toInt()
                            if (percent != lastLoggedPercent && percent % 2 == 0) {
                                lastLoggedPercent = percent
                                listener.onProgress(frac * 0.9f, "decoding source audio… $percent%")
                            }
                        }
                    }
                }
            }

            val outIndex = codec.dequeueOutputBuffer(bufferInfo, 10_000)
            if (outIndex == MediaCodec.INFO_OUTPUT_FORMAT_CHANGED) {
                if (!checkedOutputEncoding) {
                    checkedOutputEncoding = true
                    val outputFormat = codec.outputFormat
                    val pcmEncoding = if (outputFormat.containsKey(MediaFormat.KEY_PCM_ENCODING)) {
                        outputFormat.getInteger(MediaFormat.KEY_PCM_ENCODING)
                    } else {
                        android.media.AudioFormat.ENCODING_PCM_16BIT 
                    }
                    require(pcmEncoding == android.media.AudioFormat.ENCODING_PCM_16BIT) {
                        "Decoded PCM isn't 16-bit (encoding=$pcmEncoding) — this source can't be converted yet"
                    }
                }
            } else if (outIndex >= 0) {
                if (bufferInfo.size > 0) {
                    val outBuf = codec.getOutputBuffer(outIndex)!!
                    outBuf.position(bufferInfo.offset)
                    outBuf.limit(bufferInfo.offset + bufferInfo.size)
                    val shortBuf: ShortBuffer = outBuf.order(ByteOrder.LITTLE_ENDIAN).asShortBuffer()
                    val chunk = ShortArray(shortBuf.remaining())
                    shortBuf.get(chunk)
                    pcmChunks.add(chunk)
                }
                codec.releaseOutputBuffer(outIndex, false)
                if (bufferInfo.flags and MediaCodec.BUFFER_FLAG_END_OF_STREAM != 0) {
                    sawOutputEof = true
                }
            }
        }

        codec.stop()
        codec.release()
        extractor.release()

        val totalInterleavedSamples = pcmChunks.sumOf { it.size }
        val numFrames = totalInterleavedSamples / channelCount
        val channels = Array(channelCount) { ShortArray(numFrames) }
        val usableInterleavedSamples = numFrames.toLong() * channelCount
        var interleavedIdx = 0L
        var frameIdx = 0
        var chIdx = 0
        val chunkIterator = pcmChunks.listIterator()
        outer@ while (chunkIterator.hasNext()) {
            val chunk = chunkIterator.next()
            for (s in chunk) {
                if (interleavedIdx >= usableInterleavedSamples) break@outer
                channels[chIdx][frameIdx] = s
                interleavedIdx++
                chIdx++
                if (chIdx == channelCount) {
                    chIdx = 0
                    frameIdx++
                }
            }
            chunkIterator.set(EMPTY_SHORT_ARRAY) 
        }

        listener?.onProgress(0.9f, "decode complete")
        return DAFAudio(sampleRate, channelCount, 2, numFrames, channels)
    }

    private val EMPTY_SHORT_ARRAY = ShortArray(0)
}
