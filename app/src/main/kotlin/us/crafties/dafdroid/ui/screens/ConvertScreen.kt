package us.crafties.dafdroid.ui.screens

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import us.crafties.dafdroid.state.ConversionStage
import us.crafties.dafdroid.state.ConversionState
import us.crafties.dafdroid.ui.components.LiquidTextButton

@Composable
fun ConvertScreen(
    state: ConversionState,
    isAddingToPlayer: Boolean,
    onPickFile: () -> Unit,
    onSave: () -> Unit,
    onAddToPlayer: () -> Unit,
    onConfirmLossy: () -> Unit,
    onCancelLossy: () -> Unit,
    modifier: Modifier = Modifier,
    bottomContentPadding: Dp = 16.dp,
) {
    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(start = 16.dp, end = 16.dp, top = 16.dp, bottom = bottomContentPadding),
    ) {
        Text(
            text = "Convert to DAF",
            style = MaterialTheme.typography.headlineSmall,
        )
        Text(
            text = "Pick an audio file. WAV or FLAC in, DAF out — lossless, no seek table, no metadata.",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.outline,
            modifier = Modifier.padding(top = 4.dp, bottom = 16.dp),
        )

        LiquidTextButton(
            text = "Choose file",
            onClick = onPickFile,
            enabled = state.stage != ConversionStage.DECODING &&
                state.stage != ConversionStage.ENCODING &&
                !isAddingToPlayer,
        )

        Spacer(Modifier.height(16.dp))

        if (state.stage != ConversionStage.IDLE) {
            Card(
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                modifier = Modifier.fillMaxWidth(),
            ) {
                Column(Modifier.padding(16.dp)) {
                    Text(
                        state.sourceName,
                        style = MaterialTheme.typography.bodyLarge,
                        color = MaterialTheme.colorScheme.onSurface,
                        maxLines = 1,
                    )

                    when (state.stage) {
                        ConversionStage.DECODING -> {
                            Text("Decoding source audio…", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.outline, modifier = Modifier.padding(top = 8.dp))
                            LinearProgressIndicator(
                                progress = { state.progress },
                                modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
                            )
                        }
                        ConversionStage.ENCODING -> {
                            Text("Encoding to DAF…", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.outline, modifier = Modifier.padding(top = 8.dp))
                            LinearProgressIndicator(
                                progress = { state.progress },
                                modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
                            )
                        }
                        ConversionStage.DONE -> {
                            val ratio = if (state.rawBytes > 0) state.dafBytes.toDouble() / state.rawBytes else 0.0
                            Text(
                                text = "raw: ${fmtBytes(state.rawBytes)}   daf: ${fmtBytes(state.dafBytes)}   ratio: ${"%.3f".format(ratio)}",
                                style = MaterialTheme.typography.bodySmall,
                                fontFamily = FontFamily.Monospace,
                                modifier = Modifier.padding(top = 8.dp),
                            )
                            Row(
                                modifier = Modifier.padding(top = 12.dp),
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                LiquidTextButton(
                                    text = "Save as .daf",
                                    onClick = onSave,
                                    enabled = !isAddingToPlayer,
                                )
                                Spacer(Modifier.width(12.dp))
                                if (isAddingToPlayer) {
                                    // Adding to the player re-decodes and re-encodes
                                    // the source file and writes it to storage before
                                    // it shows up — not instant for anything but a
                                    // short clip. Without this, the button (and the
                                    // whole card) used to just disappear the instant
                                    // it was tapped, with no sign the app was still
                                    // working, which read as the tap not landing.
                                    CircularProgressIndicator(
                                        modifier = Modifier.size(20.dp),
                                        strokeWidth = 2.dp,
                                    )
                                    Spacer(Modifier.width(8.dp))
                                    Text(
                                        "Adding…",
                                        style = MaterialTheme.typography.bodyMedium,
                                        color = MaterialTheme.colorScheme.outline,
                                    )
                                } else {
                                    TextButton(onClick = onAddToPlayer) { Text("Add to Player") }
                                }
                            }
                        }
                        ConversionStage.ERROR -> {
                            Text(
                                text = state.errorMessage ?: "Something went wrong.",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.error,
                                modifier = Modifier.padding(top = 8.dp),
                            )
                        }
                        else -> {}
                    }
                }
            }
        }

        if (state.log.isNotEmpty()) {
            Text(
                text = "Activity",
                style = MaterialTheme.typography.labelLarge,
                modifier = Modifier.padding(top = 20.dp, bottom = 6.dp),
            )
            Card(
                shape = RoundedCornerShape(12.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
                modifier = Modifier.fillMaxWidth().height(160.dp),
            ) {
                val logListState = rememberLazyListState()
                // Keep the newest line in view as the log grows, instead of staying
                // fixed at the top — otherwise, on a long conversion, the visible
                // lines never advance and the user is stuck looking at the first few
                // entries while everything current scrolls in unseen below.
                LaunchedEffect(state.log.size) {
                    if (state.log.isNotEmpty()) {
                        logListState.animateScrollToItem(state.log.size - 1)
                    }
                }
                LazyColumn(state = logListState, modifier = Modifier.padding(10.dp)) {
                    items(state.log) { line ->
                        Text(
                            text = line,
                            style = MaterialTheme.typography.bodySmall,
                            fontFamily = FontFamily.Monospace,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            }
        }
    }

    if (state.stage == ConversionStage.WARNING_LOSSY) {
        AlertDialog(
            onDismissRequest = onCancelLossy,
            title = { Text("Lossy source file") },
            text = {
                Text(
                    "DAF is lossless, but it can't restore detail that was already " +
                        "discarded by a lossy encoder. The file you picked is lossy " +
                        "(MP3, AAC, Opus, etc). Consider using a WAV or FLAC source instead.\n\n" +
                        "Continue anyway?"
                )
            },
            confirmButton = { TextButton(onClick = onConfirmLossy) { Text("Continue") } },
            dismissButton = { TextButton(onClick = onCancelLossy) { Text("Cancel") } },
        )
    }
}

private fun fmtBytes(n: Long): String = when {
    n < 1024 -> "$n B"
    n < 1024 * 1024 -> "%.1f KB".format(n / 1024.0)
    else -> "%.2f MB".format(n / (1024.0 * 1024.0))
}
