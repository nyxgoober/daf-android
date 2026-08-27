package us.crafties.dafdroid.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.FileDownload
import androidx.compose.material.icons.filled.MusicNote
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import us.crafties.dafdroid.audio.PlayerStatus
import us.crafties.dafdroid.codec.SourceKind
import us.crafties.dafdroid.state.Track


enum class ExportFormat { DAF, WAV }


@Composable
fun PlayerScreen(
    tracks: List<Track>,
    activeTrackId: String?,
    playerStatus: PlayerStatus,
    onTrackClick: (Track) -> Unit,
    onRemoveTrack: (Track) -> Unit,
    modifier: Modifier = Modifier,
    loadingTrackId: String? = null,
    bottomContentPadding: Dp = 16.dp,
    onExportTrack: (Track, ExportFormat) -> Unit = { _, _ -> },
) {
    Box(modifier = modifier.fillMaxSize()) {
        if (tracks.isEmpty()) {
            EmptyPlayerState(modifier = Modifier.align(Alignment.Center))
        } else {
            LazyColumn(
                contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 16.dp, bottom = bottomContentPadding),
                verticalArrangement = Arrangement.spacedBy(10.dp),
                modifier = Modifier.fillMaxSize(),
            ) {
                items(tracks, key = { it.id }) { track ->
                    TrackRow(
                        track = track,
                        isActive = track.id == activeTrackId,
                        isLoading = track.id == loadingTrackId,
                        playerStatus = if (track.id == activeTrackId) playerStatus else null,
                        onClick = { onTrackClick(track) },
                        onRemove = { onRemoveTrack(track) },
                        onExport = { format -> onExportTrack(track, format) },
                        // Animates insertion/removal/reordering (e.g. a track added
                        // via "Add to Player", or one removed) instead of rows
                        // snapping straight to their new position.
                        modifier = Modifier.animateItem(),
                    )
                }
            }
        }
    }
}

@Composable
private fun EmptyPlayerState(modifier: Modifier = Modifier) {
    Column(
        modifier = modifier.padding(32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Icon(
            imageVector = Icons.Filled.MusicNote,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.outline,
            modifier = Modifier.size(48.dp),
        )
        Text(
            text = "Nothing loaded yet",
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.onBackground,
            modifier = Modifier.padding(top = 12.dp),
        )
        Text(
            text = "Add a .daf, .wav, or other audio file to get started.",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.outline,
            modifier = Modifier.padding(top = 4.dp),
        )
    }
}

@Composable
private fun TrackRow(
    track: Track,
    isActive: Boolean,
    isLoading: Boolean,
    playerStatus: PlayerStatus?,
    onClick: () -> Unit,
    onRemove: () -> Unit,
    onExport: (ExportFormat) -> Unit,
    modifier: Modifier = Modifier,
) {
    var exportMenuOpen by remember { mutableStateOf(false) }

    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .background(
                if (isActive) MaterialTheme.colorScheme.primary.copy(alpha = 0.10f)
                else MaterialTheme.colorScheme.surface
            )
            .clickable(onClick = onClick)
            .padding(start = 14.dp, top = 14.dp, bottom = 14.dp, end = 4.dp),
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = track.displayName,
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurface,
                maxLines = 1,
            )
            Text(
                text = if (isLoading) "Loading…" else trackSubtitle(track),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.outline,
                modifier = Modifier.padding(top = 2.dp),
            )
            if (isActive && playerStatus != null && playerStatus.totalFrames > 0) {
                LinearProgressIndicator(
                    progress = { (playerStatus.positionFrames.toFloat() / playerStatus.totalFrames).coerceIn(0f, 1f) },
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 8.dp)
                        .height(3.dp),
                )
            }
        }
        Box {
            IconButton(onClick = { exportMenuOpen = true }) {
                Icon(
                    imageVector = Icons.Filled.FileDownload,
                    contentDescription = "Export ${track.displayName}",
                    tint = MaterialTheme.colorScheme.outline,
                )
            }
            DropdownMenu(expanded = exportMenuOpen, onDismissRequest = { exportMenuOpen = false }) {
                DropdownMenuItem(
                    text = { Text("Export as .daf") },
                    onClick = {
                        exportMenuOpen = false
                        onExport(ExportFormat.DAF)
                    },
                )
                DropdownMenuItem(
                    text = { Text("Export as .wav") },
                    onClick = {
                        exportMenuOpen = false
                        onExport(ExportFormat.WAV)
                    },
                )
            }
        }
        IconButton(onClick = onRemove) {
            Icon(
                imageVector = Icons.Filled.Close,
                contentDescription = "Remove ${track.displayName}",
                tint = MaterialTheme.colorScheme.outline,
            )
        }
    }
}

private fun trackSubtitle(track: Track): String {
    val kindLabel = when (track.sourceKind) {
        SourceKind.DAF -> "DAF"
        SourceKind.WAV -> "WAV"
        SourceKind.OTHER -> "Audio"
    }
    val mins = (track.durationSeconds / 60).toInt()
    val secs = (track.durationSeconds % 60).toInt()
    return "$kindLabel · ${track.sampleRate}Hz · ${track.numChannels}ch · $mins:${secs.toString().padStart(2, '0')}"
}
