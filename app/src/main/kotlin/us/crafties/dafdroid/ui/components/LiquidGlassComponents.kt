package us.crafties.dafdroid.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp


private fun glassBrush(isDark: Boolean, baseAlphaTop: Float = 0.16f, baseAlphaBottom: Float = 0.08f): Brush = Brush.verticalGradient(
    colors = listOf(
        (if (isDark) Color.White else Color.White).copy(alpha = if (isDark) baseAlphaTop else baseAlphaTop * 0.6f),
        (if (isDark) Color.White else Color.White).copy(alpha = if (isDark) baseAlphaBottom else baseAlphaBottom * 0.6f),
    )
)

@Composable
fun LiquidIconButton(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    size: Dp = 52.dp,
    shape: Shape = CircleShape,
    content: @Composable () -> Unit,
) {
    val isDark = isSystemInDarkTheme()
    Box(
        modifier = modifier
            .size(size)
            .clip(shape)
            .background(glassBrush(isDark, 0.20f, 0.10f), shape)
            .border(1.dp, (if (isDark) Color.White else Color.Black).copy(alpha = if (isDark) 0.22f else 0.08f), shape)
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        content()
    }
}

@Composable
fun LiquidGlassBar(
    modifier: Modifier = Modifier,
    shape: Shape = RoundedCornerShape(28.dp),
    contentPadding: PaddingValues = PaddingValues(horizontal = 12.dp, vertical = 8.dp),
    content: @Composable () -> Unit,
) {
    val isDark = isSystemInDarkTheme()
    val baseBg = if (isDark) Color(0xFF16161C).copy(alpha = 0.92f) else Color(0xFFF2F3F8).copy(alpha = 0.88f)
    val borderColor = if (isDark) Color.White.copy(alpha = 0.16f) else Color.Black.copy(alpha = 0.08f)
    Row(
        modifier = modifier
            .clip(shape)
            .background(baseBg, shape)
            .background(glassBrush(isDark), shape)
            .border(1.dp, borderColor, shape)
            .padding(contentPadding),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        content()
    }
}

@Composable
fun LiquidTextButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    shape: Shape = RoundedCornerShape(20.dp),
) {
    Box(
        modifier = modifier
            .clip(shape)
            .background(
                (if (enabled) MaterialTheme.colorScheme.primary else Color.Gray)
                    .copy(alpha = if (enabled) 0.18f else 0.08f),
                shape,
            )
            .border(1.dp, Color.White.copy(alpha = 0.12f), shape)
            .clickable(enabled = enabled, onClick = onClick)
            .padding(horizontal = 20.dp, vertical = 12.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = text,
            style = MaterialTheme.typography.labelLarge,
            color = if (enabled) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outline,
        )
    }
}
