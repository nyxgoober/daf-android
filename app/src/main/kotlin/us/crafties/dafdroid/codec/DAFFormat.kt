package us.crafties.dafdroid.codec


object DAFFormat {
    val MAGIC = byteArrayOf('D'.code.toByte(), 'A'.code.toByte(), 'F'.code.toByte(), '0'.code.toByte())
    const val BLOCK_SIZE = 4096
    const val HEADER_FIXED_SIZE = 18 

    fun isDAF(bytes: ByteArray): Boolean {
        if (bytes.size < 4) return false
        return bytes[0] == MAGIC[0] && bytes[1] == MAGIC[1] && bytes[2] == MAGIC[2] && bytes[3] == MAGIC[3]
    }
}

data class DAFAudio(
    val sampleRate: Int,
    val numChannels: Int,
    val sampleWidth: Int, 
    val numFrames: Int,
    
    val channels: Array<ShortArray>,
) {
    val durationSeconds: Double get() = numFrames.toDouble() / sampleRate.toDouble()
}
