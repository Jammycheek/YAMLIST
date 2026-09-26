package com.example.yamlist.ui.common

import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.example.yamlist.R
import com.example.yamlist.domain.model.MarkType
import com.example.yamlist.domain.model.TaskColor

/**
 * Color choice row. [noneAsTextColor] draws "no color" in the default text color
 * used by task numbers; projects keep the neutral empty swatch.
 */
@Composable
fun ColorPickerRow(
    selectedKey: String?,
    onSelect: (String?) -> Unit,
    label: String,
    noneAsTextColor: Boolean = false,
) {
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Text(label, style = MaterialTheme.typography.labelLarge)
        Row(
            Modifier.horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            val noneColor =
                if (noneAsTextColor) MaterialTheme.colorScheme.onSurface
                else MaterialTheme.colorScheme.surfaceVariant
            TaskColor.entries.forEach { c ->
                ColorSwatch(
                    color = c,
                    selected = (selectedKey ?: "none") == c.key,
                    size = 28.dp,
                    noneColor = noneColor,
                    modifier = Modifier
                        .clip(CircleShape)
                        .clickable { onSelect(if (c == TaskColor.NONE) null else c.key) },
                )
            }
        }
    }
}

/**
 * Mark choice row. "None" comes first so clearing a mark is always on screen,
 * and tapping the selected mark again also clears it.
 */
@Composable
fun MarkPickerRow(selected: MarkType, onSelect: (MarkType) -> Unit, label: String) {
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Text(label, style = MaterialTheme.typography.labelLarge)
        Row(
            Modifier.horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            val ordered = listOf(MarkType.NONE) + MarkType.entries.filter { it != MarkType.NONE }
            ordered.forEach { m ->
                FilterChip(
                    selected = selected == m,
                    onClick = { onSelect(if (selected == m) MarkType.NONE else m) },
                    label = { Text(if (m == MarkType.NONE) stringResource(R.string.mark_none) else m.glyph) },
                )
            }
        }
    }
}
