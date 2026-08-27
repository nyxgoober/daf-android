package us.crafties.dafdroid.state

import android.net.Uri
import us.crafties.dafdroid.codec.DAFAudio
import us.crafties.dafdroid.codec.SourceKind


data class Track(
    val id: String,
    val displayName: String,
    val sourceKind: SourceKind,
    val sampleRate: Int,
    val numChannels: Int,
    val numFrames: Int,
) {
    val durationSeconds: Double get() = numFrames.toDouble() / sampleRate.toDouble()
}

enum class ConversionStage { IDLE, WARNING_LOSSY, DECODING, ENCODING, DONE, ERROR }

data class ConversionState(
    val stage: ConversionStage = ConversionStage.IDLE,
    val sourceName: String = "",
    val progress: Float = 0f,
    val log: List<String> = emptyList(),
    val rawBytes: Long = 0,
    val dafBytes: Long = 0,
    val outputUri: Uri? = null,
    val outputName: String = "",
    
    val outputBytes: ByteArray? = null,
    
    val outputAudio: DAFAudio? = null,
    val outputSourceKind: SourceKind? = null,
    val errorMessage: String? = null,
    val pendingLossyUri: Uri? = null,
)
