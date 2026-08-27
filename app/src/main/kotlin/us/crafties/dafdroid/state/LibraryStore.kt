package us.crafties.dafdroid.state

import android.content.Context
import android.util.Base64
import us.crafties.dafdroid.codec.DAFAudio
import us.crafties.dafdroid.codec.DAFCodec
import us.crafties.dafdroid.codec.SourceKind
import java.io.File
import java.util.UUID


object LibraryStore {

    private const val MANIFEST_NAME = "manifest.tsv"
    private const val MANIFEST_VERSION = "1"

    private fun libraryDir(context: Context): File =
        File(context.filesDir, "library").apply { mkdirs() }

    private fun manifestFile(context: Context): File = File(libraryDir(context), MANIFEST_NAME)

    private fun audioFile(context: Context, id: String): File = File(libraryDir(context), "$id.daf")

    
    fun loadManifest(context: Context): List<Track> {
        val file = manifestFile(context)
        if (!file.exists()) return emptyList()
        val lines = file.readLines()
        if (lines.isEmpty() || lines.first() != MANIFEST_VERSION) return emptyList()
        return lines.drop(1).mapNotNull(::parseLine)
    }

    private fun parseLine(line: String): Track? {
        if (line.isBlank()) return null
        val parts = line.split('\t')
        if (parts.size != 6) return null
        return try {
            Track(
                id = parts[0],
                displayName = String(Base64.decode(parts[1], Base64.NO_WRAP), Charsets.UTF_8),
                sourceKind = SourceKind.valueOf(parts[2]),
                sampleRate = parts[3].toInt(),
                numChannels = parts[4].toInt(),
                numFrames = parts[5].toInt(),
            )
        } catch (e: Exception) {
            null 
        }
    }

    private fun toLine(track: Track): String {
        val nameB64 = Base64.encodeToString(track.displayName.toByteArray(Charsets.UTF_8), Base64.NO_WRAP)
        return listOf(
            track.id, nameB64, track.sourceKind.name,
            track.sampleRate, track.numChannels, track.numFrames,
        ).joinToString("\t")
    }

    private fun saveManifest(context: Context, tracks: List<Track>) {
        val text = buildString {
            append(MANIFEST_VERSION).append('\n')
            for (t in tracks) append(toLine(t)).append('\n')
        }
        val tmp = File(libraryDir(context), "$MANIFEST_NAME.tmp")
        tmp.writeText(text)
        tmp.renameTo(manifestFile(context))
    }

    
    fun addTrack(
        context: Context,
        dafBytes: ByteArray,
        displayName: String,
        sourceKind: SourceKind,
        sampleRate: Int,
        numChannels: Int,
        numFrames: Int,
    ): Track {
        val id = UUID.randomUUID().toString()
        audioFile(context, id).writeBytes(dafBytes)
        val track = Track(id, displayName, sourceKind, sampleRate, numChannels, numFrames)
        saveManifest(context, loadManifest(context) + track)
        return track
    }

    
    fun removeTrack(context: Context, id: String) {
        audioFile(context, id).delete()
        saveManifest(context, loadManifest(context).filterNot { it.id == id })
    }

    
    fun audioFileSize(context: Context, id: String): Long? {
        val file = audioFile(context, id)
        return if (file.exists()) file.length() else null
    }

    
    fun loadAudio(context: Context, id: String): DAFAudio {
        val bytes = audioFile(context, id).readBytes()
        return DAFCodec.decode(bytes)
    }
}
