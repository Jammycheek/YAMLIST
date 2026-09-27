package com.example.yamlist.ui.common

import androidx.annotation.StringRes
import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource

/**
 * A message a ViewModel wants shown, as a string resource plus its arguments.
 * Resolved in the UI so it follows the app's current language.
 */
data class UiText(@StringRes val id: Int, val args: List<String>) {
    constructor(@StringRes id: Int, vararg args: String) : this(id, args.toList())
}

@Composable
fun UiText.resolve(): String = stringResource(id, *args.toTypedArray())

/** The system file picker handed back a destination that could not be opened for writing. */
class DestinationUnavailable : java.io.IOException()
