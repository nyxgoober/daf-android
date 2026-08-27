package us.crafties.dafdroid.codec

import android.content.Context
import android.net.Uri
import android.provider.OpenableColumns
import java.io.File

data class LoadedAudio(
    val audio: DAFAudio,
    val sourceKind: SourceKind,
    val displayName: String,
)

enum class SourceKind { DAF, WAV, OTHER }

object AudioFileLoader {

    private val LOSSY_EXTENSIONS = setOf("mp3", "aac", "m4a", "ogg", "oga", "opus", "webm", "wma", "amr", "mp2")

    fun isLossyExtension(name: String): Boolean {
        val ext = extensionOf(name)
        return ext in LOSSY_EXTENSIONS
    }

    fun extensionOf(name: String): String = name.substringAfterLast('.', "").lowercase()

    fun displayNameOf(context: Context, uri: Uri): String {
        context.contentResolver.query(uri, null, null, null, null)?.use { cursor ->
            val idx = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
            if (idx >= 0 && cursor.moveToFirst()) {
                return cursor.getString(idx) ?: uri.lastPathSegment ?: "audio"
            }
        }
        return uri.lastPathSegment ?: "audio"
    }

    
    fun copyToCache(context: Context, uri: Uri, suggestedName: String): File {
        val safeName = suggestedName.ifBlank { "input" }
        val outFile = File(context.cacheDir, "load_${System.currentTimeMillis()}_$safeName")
        context.contentResolver.openInputStream(uri)?.use { input ->
            outFile.outputStream().use { output -> input.copyTo(output) }
        } ?: error("Could not open input stream for $uri")
        return outFile
    }

    fun load(context: Context, uri: Uri, listener: CodecProgressListener? = null): LoadedAudio {
        val name = displayNameOf(context, uri)
        val file = copyToCache(context, uri, name)
        try {
            val headBytes = file.inputStream().use { it.readNBytes(4) }
            val ext = extensionOf(name)

            return when {
                DAFFormat.isDAF(headBytes) || ext == "daf" -> {
                    val bytes = file.readBytes()
                    LoadedAudio(DAFCodec.decode(bytes, listener), SourceKind.DAF, name)
                }
                ext == "wav" -> {
                    val bytes = file.readBytes()
                    LoadedAudio(WavCodec.parse(bytes), SourceKind.WAV, name)
                }
                else -> {
                    LoadedAudio(PlatformAudioDecoder.decode(file, listener), SourceKind.OTHER, name)
                }
            }
        } finally {
            file.delete()
        }
    }
}
