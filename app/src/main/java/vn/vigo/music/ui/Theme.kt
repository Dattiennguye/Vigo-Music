package vn.vigo.music.ui

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.em
import androidx.compose.ui.unit.sp

object VG {
    val Cream = Color(0xFFFAF3F0)
    val Cream2 = Color(0xFFF3E7E2)
    val Card = Color(0xFFFFFCFB)
    val Peach = Color(0xFFE8834F)
    val PeachSoft = Color(0xFFF7C9B0)
    val PeachPale = Color(0xFFFBE3D6)
    val Ink = Color(0xFF1E1A18)
    val Gray = Color(0xFF8A817D)
    val Line = Color(0xFFEDE2DD)
    val Vinyl = Color(0xFF1B1919)

    val H1 = TextStyle(fontSize = 34.sp, fontWeight = FontWeight.Bold, letterSpacing = (-0.04).em, lineHeight = 38.sp, color = Ink)
    val H2 = TextStyle(fontSize = 24.sp, fontWeight = FontWeight.Bold, letterSpacing = (-0.03).em, color = Ink)
    val Title = TextStyle(fontSize = 16.sp, fontWeight = FontWeight.SemiBold, letterSpacing = (-0.01).em, color = Ink)
    val Body = TextStyle(fontSize = 15.sp, color = Gray, lineHeight = 21.sp)
    val Small = TextStyle(fontSize = 13.sp, color = Gray)
}

@Composable
fun VGTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = lightColorScheme(
            primary = VG.Peach,
            onPrimary = Color.White,
            secondary = VG.Ink,
            background = VG.Cream,
            surface = VG.Card,
            onSurface = VG.Ink,
            onBackground = VG.Ink,
            surfaceVariant = VG.Cream2,
            outline = VG.Line,
        ),
        content = content,
    )
}

fun formatTime(ms: Long): String {
    val s = (ms / 1000).coerceAtLeast(0)
    val h = s / 3600
    val m = (s % 3600) / 60
    val sec = s % 60
    return if (h > 0) "%d:%02d:%02d".format(h, m, sec) else "%d:%02d".format(m, sec)
}
