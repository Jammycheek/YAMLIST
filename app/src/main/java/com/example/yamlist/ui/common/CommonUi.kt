package com.example.yamlist.ui.common

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.example.yamlist.R
import com.example.yamlist.domain.model.MarkType
import com.example.yamlist.domain.model.TaskColor
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

/**
 * A task's in-level number carrying its color mark. With no color it is plain
 * default-colored text (black on the light theme); otherwise the number sits on
 * a filled chip of that color. Long-press opens the color list.
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun NumberColorBadge(
    number: String,
    colorKey: String?,
    onClick: () -> Unit,
    onSelectColor: (String?) -> Unit,
    modifier: Modifier = Modifier,
) {
    var picking by remember { mutableStateOf(false) }
    val color = TaskColors.forKey(colorKey)
    val colored = color != TaskColors.none
    Box(modifier) {
        Text(
            text = number,
            style = MaterialTheme.typography.labelLarge,
            fontWeight = FontWeight.Bold,
            color = when {
                !colored -> MaterialTheme.colorScheme.onSurface
                color.luminance() > 0.5f -> Color.Black
                else -> Color.White
            },
            maxLines = 1,
            modifier = Modifier
                .clip(RoundedCornerShape(4.dp))
                .background(if (colored) color else Color.Transparent)
                .combinedClickable(onClick = onClick, onLongClick = { picking = true })
                .padding(horizontal = 6.dp, vertical = 2.dp),
        )
        DropdownMenu(expanded = picking, onDismissRequest = { picking = false }) {
            val selected = TaskColor.fromKeyOrNull(colorKey) ?: TaskColor.NONE
            TaskColor.entries.forEach { c ->
                DropdownMenuItem(
                    leadingIcon = { ColorSwatch(c, selected = c == selected) },
                    text = { Text(taskColorLabel(c)) },
                    onClick = {
                        picking = false
                        onSelectColor(if (c == TaskColor.NONE) null else c.key)
                    },
                )
            }
        }
    }
}

/** Swatch for a task color; "none" is drawn in the default text color (black on the light theme). */
@Composable
fun ColorSwatch(color: TaskColor, selected: Boolean, size: Dp = 20.dp) {
    Box(
        Modifier
            .size(size)
            .clip(CircleShape)
            .background(
                if (color == TaskColor.NONE) MaterialTheme.colorScheme.onSurface
                else TaskColors.forKey(color.key)
            )
            .border(
                width = if (selected) 3.dp else 1.dp,
                color = if (selected) MaterialTheme.colorScheme.primary else Color.Gray,
                shape = CircleShape,
            ),
    )
}

@Composable
fun taskColorLabel(color: TaskColor): String = stringResource(
    when (color) {
        TaskColor.NONE -> R.string.color_default
        TaskColor.RED -> R.string.color_red
        TaskColor.ORANGE -> R.string.color_orange
        TaskColor.YELLOW -> R.string.color_yellow
        TaskColor.GREEN -> R.string.color_green
        TaskColor.BLUE -> R.string.color_blue
        TaskColor.PURPLE -> R.string.color_purple
        TaskColor.GRAY -> R.string.color_gray
    }
)

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
