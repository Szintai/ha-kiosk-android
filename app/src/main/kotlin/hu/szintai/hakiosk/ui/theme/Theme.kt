package hu.szintai.hakiosk.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

/** Same tokens as the Home Assistant dashboards (`--o-*` in otthon-cards.js), dark variant. */
object Palette {
    val Bg = Color(0xFF000000)
    val Card = Color(0xFF1C1C1E)
    val Soft = Color(0xFF2C2C2E)
    val Line = Color(0x1AFFFFFF)
    val Ink = Color(0xFFFFFFFF)
    val Mute = Color(0xFF8E8E93)
    val Orange = Color(0xFFFF9F0A)
    val Green = Color(0xFF30D158)
    val Blue = Color(0xFF0A84FF)
}

private val Scheme = darkColorScheme(
    primary = Palette.Orange,
    onPrimary = Color.Black,
    background = Palette.Bg,
    onBackground = Palette.Ink,
    surface = Palette.Bg,
    onSurface = Palette.Ink,
    surfaceVariant = Palette.Soft,
    onSurfaceVariant = Palette.Mute,
    outline = Palette.Line,
)

@Composable
fun HaKioskTheme(content: @Composable () -> Unit) {
    MaterialTheme(colorScheme = Scheme) {
        Surface(color = MaterialTheme.colorScheme.background, content = content)
    }
}
