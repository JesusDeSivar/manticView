package dev.jesusdesivar.manticwidget.widget

import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.glance.GlanceTheme
import androidx.glance.material3.ColorProviders

private val LightScheme = lightColorScheme()
private val DarkScheme = darkColorScheme()

/**
 * Applies the user's widget theme preference: "light" and "dark" force one
 * scheme for both system modes; anything else follows the system setting.
 */
@Composable
fun ManticGlanceTheme(theme: String, content: @Composable () -> Unit) {
    val colors = when (theme) {
        "light" -> ColorProviders(light = LightScheme, dark = LightScheme)
        "dark" -> ColorProviders(light = DarkScheme, dark = DarkScheme)
        else -> ColorProviders(light = LightScheme, dark = DarkScheme)
    }
    GlanceTheme(colors = colors, content = content)
}
