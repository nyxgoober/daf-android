package us.crafties.dafdroid.ui.components

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import us.crafties.dafdroid.audio.PlaybackState
import us.crafties.dafdroid.audio.PlayerStatus
import us.crafties.dafdroid.state.Track


@Composable
fun NowPlayingBar(
    track: Track,
    status: PlayerStatus,
    onPlayPauseClick: () -> Unit,
    onSeek: (Float) -> Unit,
    onOpenInfo: () -> Unit,
    modifier: Modifier = Modifier,
) {
    LiquidGlassBar(modifier = modifier.fillMaxWidth().clickable(onClick = onOpenInfo)) {
        Column(modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)) {
            Text(
                text = track.displayName,
                style = MaterialTheme.typography.labelLarge,
                color = if (isSystemInDarkTheme()) Color.White else MaterialTheme.colorScheme.onSurface,
                maxLines = 1,
                modifier = Modifier.padding(bottom = 2.dp),
            )
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.padding(top = 10.dp),
            ) {
                LiquidIconButton(onClick = onPlayPauseClick, size = 44.dp) {
                    Icon(
                        imageVector = if (status.state == PlaybackState.PLAYING) Icons.Filled.Pause else Icons.Filled.PlayArrow,
                        contentDescription = if (status.state == PlaybackState.PLAYING) "Pause" else "Play",
                        tint = if (isSystemInDarkTheme()) Color.White else MaterialTheme.colorScheme.onSurface,
                    )
                }
                val frac = if (status.totalFrames > 0) status.positionFrames.toFloat() / status.totalFrames else 0f
                Slider(
                    value = frac,
                    onValueChange = onSeek,
                    modifier = Modifier
                        .weight(1f)
                        .padding(start = 12.dp),
                )
            }
        }
    }
}
