package com.androidantigravity.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

private val AntigravityColors = darkColorScheme(
    primary = Color(0xFF9FC1FF),
    secondary = Color(0xFFBBC7DB),
    background = Color(0xFF101217),
    surface = Color(0xFF181B22),
    surfaceVariant = Color(0xFF232832),
)

@Composable
fun AntigravityTheme(content: @Composable () -> Unit) = MaterialTheme(colorScheme = AntigravityColors, content = content)
