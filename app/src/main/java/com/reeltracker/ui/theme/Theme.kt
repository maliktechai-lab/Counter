package com.reeltracker.ui.theme

import android.os.Build
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalContext

private val LightColors = lightColorScheme()
private val DarkColors  = darkColorScheme()

/**
 * Material 3 theme for ReelTracker.
 *
 * Uses dynamic colour on Android 12+ (API 31+) and falls back to the
 * default Material 3 baseline scheme on older API levels.
 */
@Composable
fun ReelTrackerTheme(
    darkTheme: Boolean = false,
    content: @Composable () -> Unit,
) {
    val colorScheme = when {
        Build.VERSION.SDK_INT >= Build.VERSION_CODES.S -> {
            val context = LocalContext.current
            if (darkTheme) dynamicDarkColorScheme(context)
            else           dynamicLightColorScheme(context)
        }
        darkTheme -> DarkColors
        else      -> LightColors
    }

    MaterialTheme(
        colorScheme = colorScheme,
        content     = content,
    )
}
