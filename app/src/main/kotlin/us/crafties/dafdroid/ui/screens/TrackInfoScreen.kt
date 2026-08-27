package us.crafties.dafdroid.ui.screens

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.MutableTransitionState
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.wrapContentHeight
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.MusicNote
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import kotlinx.coroutines.launch
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import us.crafties.dafdroid.audio.PlaybackState
import us.crafties.dafdroid.audio.PlayerStatus
import us.crafties.dafdroid.codec.SourceKind
import us.crafties.dafdroid.state.Track
import us.crafties.dafdroid.ui.components.LiquidIconButton
import kotlin.math.cos
import kotlin.math.roundToInt
import kotlin.math.sin

@Composable
fun TrackInfoScreen(
    track: Track,
    status: PlayerStatus,
    fileSizeBytes: Long?,
    visible: Boolean,
    onClose: () -> Unit,
    onFullyClosed: () -> Unit,
    onPlayPauseClick: () -> Unit,
    onSeek: (Float) -> Unit,
    modifier: Modifier = Modifier,
    animatedBackground: Boolean = true,
) {
    val rotation: Float
    if (animatedBackground) {
        val infiniteTransition = rememberInfiniteTransition(label = "player-bg")
        rotation = infiniteTransition.animateFloat(
            initialValue = 0f,
            targetValue = 360f,
            animationSpec = infiniteRepeatable(
                animation = tween(durationMillis = 24000, easing = LinearEasing),
            ),
            label = "gradient-rotation",
        ).value
    } else {
        rotation = 0f
    }

    val dragOffsetY = remember { Animatable(0f) }
    val dismissThresholdPx = with(LocalDensity.current) { 120.dp.toPx() }
    var liveDragOffset by remember { mutableFloatStateOf(0f) }
    val dragScope = rememberCoroutineScope()
    LaunchedEffect(Unit) {
        snapshotFlow { liveDragOffset }.collect { dragOffsetY.snapTo(it) }
    }
    // Only reset drag when opening, not on close — otherwise a swipe that
    // exceeds the threshold would snap back to 0 before the exit animation
    // starts, reading as "jumps to top then slides out".
    LaunchedEffect(visible) {
        if (visible) {
            dragOffsetY.snapTo(0f)
            liveDragOffset = 0f
        }
    }
    val progressFraction = (dragOffsetY.value / dismissThresholdPx).coerceIn(0f, 1f)

    val contentReveal = remember { Animatable(0f) }
    LaunchedEffect(visible) {
        if (visible) {
            contentReveal.snapTo(0f)
            contentReveal.animateTo(1f, tween(680, delayMillis = 120, easing = FastOutSlowInEasing))
        } else {
            contentReveal.snapTo(0f)
        }
    }

    // Forces enter transition even when parent mounts us already visible.
    val paneVisibilityState = remember { MutableTransitionState(false) }
    paneVisibilityState.targetState = visible

    // Scrim crossfades with sheet — durations matched to slide (420 in / 320 out)
    // so the black dim doesn't pop in/out separately from blur/desaturation.
    val scrimAlpha by animateFloatAsState(
        targetValue = if (visible) 1f else 0f,
        animationSpec = tween(if (visible) 420 else 320, easing = FastOutSlowInEasing),
        label = "scrim-alpha",
    )

    Box(
        modifier = modifier.fillMaxSize(),
        contentAlignment = Alignment.BottomCenter,
    ) {
        // Scrim — full-screen dim, crossfaded seamlessly. Always composed so the
        // fade out isn't cut by conditional removal; visibility is driven purely
        // by animated alpha (matched to sheet duration).
        Box(
            modifier = Modifier
                .fillMaxSize()
                .graphicsLayer { alpha = scrimAlpha }
                .background(Color.Black.copy(alpha = 0.45f))
                .clickable(enabled = scrimAlpha > 0.02f, onClick = onClose),
        )

        AnimatedVisibility(
            visibleState = paneVisibilityState,
            enter = slideInVertically(
                animationSpec = tween(420, easing = FastOutSlowInEasing),
                initialOffsetY = { fullHeight -> fullHeight },
            ) + fadeIn(tween(280, easing = FastOutSlowInEasing)),
            exit = slideOutVertically(
                animationSpec = tween(320, easing = FastOutSlowInEasing),
                targetOffsetY = { fullHeight -> fullHeight },
            ) + fadeOut(tween(240, easing = FastOutSlowInEasing)),
            modifier = Modifier.fillMaxSize(),
        ) {
            DisposableEffect(Unit) {
                onDispose { onFullyClosed() }
            }
            // Sheet container — bottom-aligned, adaptive height, rounded top corners like iOS.
            // Tapping empty space above/around the sheet (not just the scrim) dismisses.
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .clickable(
                        interactionSource = remember { MutableInteractionSource() },
                        indication = null,
                        onClick = onClose,
                    ),
                contentAlignment = Alignment.BottomCenter,
            ) {
                Surface(
                    shape = RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp),
                    color = MaterialTheme.colorScheme.background,
                    modifier = Modifier
                        .fillMaxWidth()
                        .wrapContentHeight()
                        .heightIn(max = 680.dp)
                        .clickable(
                            interactionSource = remember { MutableInteractionSource() },
                            indication = null,
                            onClick = {},
                        )
                        .graphicsLayer {
                            translationY = dragOffsetY.value
                            val scale = 1f - (progressFraction * 0.03f)
                            scaleX = scale
                            scaleY = scale
                            alpha = 1f - (progressFraction * 0.2f)
                        }
                        .pointerInput(Unit) {
                            detectVerticalDragGestures(
                                onVerticalDrag = { change, dragAmount ->
                                    change.consume()
                                    liveDragOffset = (liveDragOffset + dragAmount).coerceAtLeast(0f)
                                },
                                onDragEnd = {
                                    val distance = liveDragOffset
                                    dragScope.launch {
                                        if (distance > dismissThresholdPx) {
                                            // Don't snap back — let exit slide continue from
                                            // the released offset. AnimatedVisibility's
                                            // slideOut will start from layout position 0
                                            // plus current translationY, so it continues
                                            // downward without a jump.
                                            onClose()
                                        } else {
                                            dragOffsetY.animateTo(0f, tween(220, easing = FastOutSlowInEasing))
                                            liveDragOffset = 0f
                                        }
                                    }
                                },
                                onDragCancel = {
                                    dragScope.launch {
                                        dragOffsetY.animateTo(0f, tween(220, easing = FastOutSlowInEasing))
                                        liveDragOffset = 0f
                                    }
                                },
                            )
                        },
                ) {
                    val gradientPrimary = MaterialTheme.colorScheme.primary
                    val gradientSecondary = MaterialTheme.colorScheme.secondary
                    val gradientTertiary = MaterialTheme.colorScheme.tertiary
                    val gradientBackground = MaterialTheme.colorScheme.background
                    val counterRotation = 360f - rotation
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .wrapContentHeight()
                            .clip(RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp))
                            .drawBehind {
                                val orbit = 0.35f
                                val radians1 = Math.toRadians(rotation.toDouble())
                                val cx1 = size.width * (0.5f + orbit * cos(radians1).toFloat())
                                val cy1 = size.height * (0.4f + orbit * sin(radians1).toFloat())
                                val radians2 = Math.toRadians((counterRotation + 140f).toDouble())
                                val cx2 = size.width * (0.5f + orbit * cos(radians2).toFloat())
                                val cy2 = size.height * (0.6f + orbit * sin(radians2).toFloat())
                                val radians3 = Math.toRadians((rotation + 240f).toDouble())
                                val cx3 = size.width * (0.5f + (orbit * 0.7f) * cos(radians3).toFloat())
                                val cy3 = size.height * (0.5f + (orbit * 0.7f) * sin(radians3).toFloat())
                                drawRect(color = gradientBackground)
                                drawRect(
                                    brush = Brush.radialGradient(
                                        colors = listOf(
                                            gradientPrimary.copy(alpha = 0.22f),
                                            gradientPrimary.copy(alpha = 0.08f),
                                            Color.Transparent,
                                        ),
                                        center = Offset(cx1, cy1),
                                        radius = size.maxDimension * 0.75f,
                                    ),
                                )
                                drawRect(
                                    brush = Brush.radialGradient(
                                        colors = listOf(
                                            gradientTertiary.copy(alpha = 0.18f),
                                            gradientTertiary.copy(alpha = 0.06f),
                                            Color.Transparent,
                                        ),
                                        center = Offset(cx2, cy2),
                                        radius = size.maxDimension * 0.65f,
                                    ),
                                )
                                drawRect(
                                    brush = Brush.radialGradient(
                                        colors = listOf(
                                            gradientSecondary.copy(alpha = 0.16f),
                                            gradientSecondary.copy(alpha = 0.05f),
                                            Color.Transparent,
                                        ),
                                        center = Offset(cx3, cy3),
                                        radius = size.maxDimension * 0.55f,
                                    ),
                                )
                            },
                    ) {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .navigationBarsPadding()
                                .padding(horizontal = 24.dp)
                                .padding(bottom = 16.dp),
                        ) {
                            Box(
                                modifier = Modifier
                                    .align(Alignment.CenterHorizontally)
                                    .padding(top = 10.dp, bottom = 12.dp)
                                    .size(width = 36.dp, height = 5.dp)
                                    .clip(RoundedCornerShape(50))
                                    .graphicsLayer { alpha = contentReveal.value }
                                    .background(MaterialTheme.colorScheme.outline.copy(alpha = 0.28f)),
                            )

                            Box(
                                modifier = Modifier
                                    .align(Alignment.CenterHorizontally)
                                    .size(168.dp)
                                    .graphicsLayer {
                                        alpha = contentReveal.value
                                        val slide = (1f - contentReveal.value) * 26f
                                        translationY = slide
                                        val scale = 0.92f + contentReveal.value * 0.08f
                                        scaleX = scale
                                        scaleY = scale
                                    }
                                    .clip(RoundedCornerShape(22.dp))
                                    .background(MaterialTheme.colorScheme.surfaceVariant),
                                contentAlignment = Alignment.Center,
                            ) {
                                Icon(
                                    imageVector = Icons.Filled.MusicNote,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.outline,
                                    modifier = Modifier.size(60.dp),
                                )
                            }

                            Spacer(Modifier.height(18.dp))

                            Column(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .graphicsLayer {
                                        alpha = contentReveal.value
                                        translationY = (1f - contentReveal.value) * 14f
                                    },
                            ) {
                                Text(
                                    text = track.displayName,
                                    style = MaterialTheme.typography.titleLarge,
                                    color = MaterialTheme.colorScheme.onBackground,
                                    textAlign = TextAlign.Center,
                                    maxLines = 3,
                                    modifier = Modifier.fillMaxWidth(),
                                )
                                Row(
                                    modifier = Modifier.fillMaxWidth().padding(top = 4.dp),
                                    horizontalArrangement = Arrangement.Center,
                                ) {
                                    Text(
                                        text = kindLabel(track.sourceKind),
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.outline,
                                    )
                                    if (fileSizeBytes != null) {
                                        Text(
                                            text = " · ${fmtBytes(fileSizeBytes)}",
                                            style = MaterialTheme.typography.bodySmall,
                                            color = MaterialTheme.colorScheme.outline,
                                        )
                                    }
                                }
                            }

                            Spacer(Modifier.height(20.dp))

                            Column(
                                modifier = Modifier
                                    .align(Alignment.CenterHorizontally)
                                    .fillMaxWidth(0.86f)
                                    .graphicsLayer {
                                        alpha = contentReveal.value
                                        translationY = (1f - contentReveal.value) * 10f
                                    },
                                horizontalAlignment = Alignment.CenterHorizontally,
                            ) {
                                val frac = if (status.totalFrames > 0) status.positionFrames.toFloat() / status.totalFrames else 0f
                                Slider(value = frac, onValueChange = onSeek, modifier = Modifier.fillMaxWidth())
                                Row(
                                    modifier = Modifier.fillMaxWidth().padding(top = 0.dp),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                ) {
                                    Text(fmtTime(status.positionSeconds), style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.outline)
                                    Text(fmtTime(status.totalSeconds), style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.outline)
                                }

                                Spacer(Modifier.height(10.dp))

                                AnimatedContent(
                                    targetState = status.state == PlaybackState.PLAYING,
                                    transitionSpec = {
                                        (fadeIn(tween(220, easing = FastOutSlowInEasing)) + scaleIn(tween(220, easing = FastOutSlowInEasing), initialScale = 0.7f)) togetherWith
                                            (fadeOut(tween(160)) + scaleOut(tween(160), targetScale = 0.7f))
                                    },
                                    label = "play-pause-icon",
                                ) { isPlaying ->
                                    LiquidIconButton(onClick = onPlayPauseClick, size = 56.dp) {
                                        Icon(
                                            imageVector = if (isPlaying) Icons.Filled.Pause else Icons.Filled.PlayArrow,
                                            contentDescription = if (isPlaying) "Pause" else "Play",
                                            tint = MaterialTheme.colorScheme.onBackground,
                                            modifier = Modifier.size(26.dp),
                                        )
                                    }
                                }
                            }

                            Spacer(Modifier.height(20.dp))
                            HorizontalDivider(
                                color = MaterialTheme.colorScheme.outline.copy(alpha = 0.3f),
                                modifier = Modifier.graphicsLayer { alpha = contentReveal.value },
                            )
                            Spacer(Modifier.height(4.dp))

                            Column(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .graphicsLayer {
                                        alpha = contentReveal.value
                                        translationY = (1f - contentReveal.value) * 10f
                                    },
                            ) {
                                val bitDepth = 16
                                val rawBitrateKbps = (track.sampleRate.toLong() * track.numChannels * bitDepth) / 1000.0

                                InfoRow("Sample rate", "${track.sampleRate} Hz")
                                InfoRow("Channels", channelsLabel(track.numChannels))
                                InfoRow("Bit depth", "$bitDepth-bit")
                                InfoRow("Raw PCM bitrate", "${rawBitrateKbps.roundToInt()} kbps")
                                if (fileSizeBytes != null && track.durationSeconds > 0) {
                                    val effectiveKbps = (fileSizeBytes * 8.0 / 1000.0) / track.durationSeconds
                                    InfoRow("Effective bitrate", "${effectiveKbps.roundToInt()} kbps")
                                }
                            }

                            Spacer(Modifier.height(8.dp))
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun InfoRow(label: String, value: String) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        Text(label, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.outline)
        Text(
            value,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onBackground,
            fontFamily = FontFamily.Monospace,
        )
    }
}

private fun kindLabel(kind: SourceKind): String = when (kind) {
    SourceKind.DAF -> "DAF · lossless"
    SourceKind.WAV -> "WAV · converted to DAF"
    SourceKind.OTHER -> "Audio · converted to DAF"
}

private fun channelsLabel(n: Int): String = when (n) {
    1 -> "Mono"
    2 -> "Stereo"
    else -> "$n ch"
}

private fun fmtTime(seconds: Double): String {
    val total = seconds.roundToInt().coerceAtLeast(0)
    val m = total / 60
    val s = total % 60
    return "$m:${s.toString().padStart(2, '0')}"
}

private fun fmtBytes(n: Long): String = when {
    n < 1024 -> "$n B"
    n < 1024 * 1024 -> "%.1f KB".format(n / 1024.0)
    else -> "%.2f MB".format(n / (1024.0 * 1024.0))
}
