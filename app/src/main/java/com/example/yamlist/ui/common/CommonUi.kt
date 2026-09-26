package com.example.yamlist.ui.common

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.yamlist.domain.model.MarkType
import com.example.yamlist.ui.theme.TaskColors

/** Thin progress bar (spec §14.1/§14.2 "███████░░░"). */
@Composable
fun ProgressBar(
    fraction: Float,
    modifier: Modifier = Modifier,
    height: androidx.compose.ui.unit.Dp = 8.dp,
    color: Color = MaterialTheme.colorScheme.primary,
) {
    val clamped = fraction.coerceIn(0f, 1f)
    Box(
        modifier
            .fillMaxWidth()
            .height(height)
            .clip(RoundedCornerShape(4.dp))
            .background(MaterialTheme.colorScheme.surfaceVariant),
    ) {
        Box(
            Modifier
                .fillMaxWidth(clamped)
                .height(height)
                .clip(RoundedCornerShape(4.dp))
                .background(color),
        )
    }
}

/** Color bar shown at the left of a task (spec §11.2: never color-only). */
@Composable
fun ColorBar(colorKey: String?, modifier: Modifier = Modifier) {
    val color = TaskColors.forKey(colorKey)
    Box(
        modifier
            .size(width = 4.dp, height = 24.dp)
            .clip(RoundedCornerShape(2.dp))
            .background(if (color == TaskColors.none) Color.Transparent else color),
    )
}

/** Mark glyph chip (spec §10). Combined with text elsewhere so meaning isn't color-only. */
@Composable
fun MarkChip(mark: MarkType, modifier: Modifier = Modifier) {
    if (mark == MarkType.NONE) return
    Text(
        text = mark.glyph,
        style = MaterialTheme.typography.bodyMedium,
        maxLines = 1,
        overflow = TextOverflow.Clip,
        modifier = modifier.padding(horizontal = 2.dp),
    )
}
