package com.example.nestprogress.ui.common

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.example.nestprogress.domain.model.MarkType
import com.example.nestprogress.domain.model.TaskColor
import com.example.nestprogress.ui.theme.TaskColors

@Composable
fun ColorPickerRow(selectedKey: String?, onSelect: (String?) -> Unit, label: String) {
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Text(label, style = MaterialTheme.typography.labelLarge)
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
            TaskColor.entries.forEach { c ->
                val color = TaskColors.forKey(c.key)
                val selected = (selectedKey ?: "none") == c.key
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    androidx.compose.foundation.layout.Box(
                        Modifier
                            .size(28.dp)
                            .clip(CircleShape)
                            .background(if (c == TaskColor.NONE) MaterialTheme.colorScheme.surfaceVariant else color)
                            .border(
                                width = if (selected) 3.dp else 1.dp,
                                color = if (selected) MaterialTheme.colorScheme.primary else Color.Gray,
                                shape = CircleShape,
                            )
                            .clickable { onSelect(if (c == TaskColor.NONE) null else c.key) },
                    )
                }
            }
        }
    }
}

@Composable
fun MarkPickerRow(selected: MarkType, onSelect: (MarkType) -> Unit, label: String) {
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Text(label, style = MaterialTheme.typography.labelLarge)
        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            MarkType.entries.forEach { m ->
                FilterChip(
                    selected = selected == m,
                    onClick = { onSelect(m) },
                    label = { Text(if (m == MarkType.NONE) "–" else m.glyph) },
                )
            }
        }
    }
}
