package com.example.yamlist.ui.theme

import androidx.compose.ui.graphics.Color

val md_primary = Color(0xFF3F6C4E)
val md_onPrimary = Color(0xFFFFFFFF)
val md_primaryContainer = Color(0xFFC0F0CB)
val md_secondary = Color(0xFF52634F)
val md_surface = Color(0xFFFBFDF7)
val md_surfaceDark = Color(0xFF191C19)

/** Palette used for task color bars (spec §11.1). */
object TaskColors {
    val none = Color(0x00000000)
    val red = Color(0xFFE53935)
    val orange = Color(0xFFFB8C00)
    val yellow = Color(0xFFFDD835)
    val green = Color(0xFF43A047)
    val blue = Color(0xFF1E88E5)
    val purple = Color(0xFF8E24AA)
    val gray = Color(0xFF9E9E9E)

    fun forKey(key: String?): Color = when (key?.lowercase()) {
        "red" -> red
        "orange" -> orange
        "yellow" -> yellow
        "green" -> green
        "blue" -> blue
        "purple" -> purple
        "gray" -> gray
        else -> none
    }
}
