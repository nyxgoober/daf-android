package us.crafties.dafdroid.audio

import android.media.AudioAttributes
import android.media.AudioFormat
import android.media.AudioTrack
import us.crafties.dafdroid.codec.DAFAudio
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlin.math.max
import kotlin.math.min

enum class PlaybackState { IDLE, PLAYING, PAUSED, STOPPED }

data class PlayerStatus(
    val state: PlaybackState = PlaybackState.IDLE,
    val positionFrames: Int = 0,
    val totalFrames: Int = 0,
    val sampleRate: Int = 44100,
) {
    val positionSeconds: Double get() = positionFrames.toDouble() / sampleRate
    val totalSeconds: Double get() = totalFrames.toDouble() / sampleRate
}


class PcmPlayer(private val scope: CoroutineScope) {

    private val _status = MutableStateFlow(PlayerStatus())
    val status: StateFlow<PlayerStatus> = _status

    private var audioTrack: AudioTrack? = null
    private var interleaved: ShortArray = ShortArray(0)
    private var totalFrames: Int = 0
    private var numChannels: Int = 1
    private var sampleRate: Int = 44100
    private var writeJob: Job? = null
    private var framePosition: Int = 0

    fun load(audio: DAFAudio) {
        stopInternal()

        sampleRate = audio.sampleRate
        numChannels = audio.numChannels
        totalFrames = audio.numFrames
        interleaved = ShortArray(totalFrames * numChannels)

        var idx = 0
        for (i in 0 until totalFrames) {
            for (c in 0 until numChannels) {
                interleaved[idx++] = audio.channels[c][i]
            }
        }

        val channelMask = if (numChannels >= 2) AudioFormat.CHANNEL_OUT_STEREO else AudioFormat.CHANNEL_OUT_MONO
        val minBufBytes = AudioTrack.getMinBufferSize(sampleRate, channelMask, AudioFormat.ENCODING_PCM_16BIT)
        val bufBytes = max(minBufBytes, 8192)

        audioTrack = AudioTrack.Builder()
            .setAudioAttributes(
                AudioAttributes.Builder()
                    .setUsage(AudioAttributes.USAGE_MEDIA)
                    .setContentType(AudioAttributes.CONTENT_TYPE_MUSIC)
                    .build()
            )
            .setAudioFormat(
                AudioFormat.Builder()
                    .setEncoding(AudioFormat.ENCODING_PCM_16BIT)
                    .setSampleRate(sampleRate)
                    .setChannelMask(channelMask)
                    .build()
            )
            .setBufferSizeInBytes(bufBytes)
            .setTransferMode(AudioTrack.MODE_STREAM)
            .build()

        framePosition = 0
        _status.value = PlayerStatus(PlaybackState.STOPPED, 0, totalFrames, sampleRate)
    }

    fun play() {
        val track = audioTrack ?: return
        if (track.playState != AudioTrack.PLAYSTATE_PLAYING) {
            track.play()
        }
        _status.value = _status.value.copy(state = PlaybackState.PLAYING)

        writeJob?.cancel()
        writeJob = scope.launch(Dispatchers.IO) {
            val chunkFrames = 4096
            while (isActive && framePosition < totalFrames) {
                val framesLeft = totalFrames - framePosition
                val framesToWrite = min(chunkFrames, framesLeft)
                val samplesToWrite = framesToWrite * numChannels
                val startIdx = framePosition * numChannels
                track.write(interleaved, startIdx, samplesToWrite)
                framePosition += framesToWrite
                _status.value = _status.value.copy(positionFrames = framePosition)
            }
            if (isActive) {
                _status.value = _status.value.copy(state = PlaybackState.STOPPED, positionFrames = totalFrames)
                framePosition = 0
                track.stop()
                track.flush()
            }
        }
    }

    fun pause() {
        writeJob?.cancel()
        audioTrack?.pause()
        _status.value = _status.value.copy(state = PlaybackState.PAUSED)
    }

    fun seekTo(fractionOfTotal: Float) {
        val wasPlaying = _status.value.state == PlaybackState.PLAYING
        writeJob?.cancel()
        audioTrack?.pause()
        audioTrack?.flush()
        framePosition = (totalFrames * fractionOfTotal.coerceIn(0f, 1f)).toInt()
        _status.value = _status.value.copy(positionFrames = framePosition, state = PlaybackState.PAUSED)
        if (wasPlaying) play()
    }

    private fun stopInternal() {
        writeJob?.cancel()
        audioTrack?.let {
            try { it.stop() } catch (_: Exception) {}
            it.release()
        }
        audioTrack = null
    }

    fun release() {
        stopInternal()
        _status.value = PlayerStatus()
    }
}
