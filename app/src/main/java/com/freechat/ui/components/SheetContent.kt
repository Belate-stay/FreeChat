package com.freechat.ui.components

import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember

/** Closing clears the active target before AnimatedVisibility finishes drawing it. */
@Composable
fun <T : Any> rememberSheetContent(value: T?): T? {
    val retained = remember { mutableStateOf(value) }
    SideEffect { if (value != null) retained.value = value }
    return value ?: retained.value
}
