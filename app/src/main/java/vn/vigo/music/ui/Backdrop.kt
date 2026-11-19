package vn.vigo.music.ui

import android.graphics.Bitmap
import android.os.Build
import androidx.compose.animation.Crossfade
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.State
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.blur
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import vn.vigo.music.data.Library
import vn.vigo.music.data.Track
import kotlin.math.cos
import kotlin.math.sin

private data class Mood(val small: ImageBitmap?, val accent: Color, val second: Color)

/** Màu chủ đạo của ảnh bìa (màu bão hòa nhất, đủ sáng) + bản ảnh siêu nhỏ để làm nền mờ. */
private suspend fun moodOf(track: Track): Mood = withContext(Dispatchers.Default) {
    val bmp: Bitmap? = Library.artwork(track, 256)
    if (bmp == null) {
        val p = paletteFor(track.id)
        return@withContext Mood(null, p[1], p[0])
    }
    val small = Bitmap.createScaledBitmap(bmp, 24, 24, true)
    var best = VG.Peach
    var bestScore = -1f
    var avgR = 0f; var avgG = 0f; var avgB = 0f
    val hsv = FloatArray(3)
    for (y in 0 until 24) for (x in 0 until 24) {
        val c = small.getPixel(x, y)
        android.graphics.Color.colorToHSV(c, hsv)
        val score = hsv[1] * (1f - kotlin.math.abs(hsv[2] - 0.7f))
        if (score > bestScore) { bestScore = score; best = Color(c) }
        avgR += android.graphics.Color.red(c); avgG += android.graphics.Color.green(c); avgB += android.graphics.Color.blue(c)
    }
    val n = 24f * 24f
    Mood(small.asImageBitmap(), best, Color(avgR / n / 255f, avgG / n / 255f, avgB / n / 255f))
}

/**
 * Nền màn hình phát nhạc: ảnh bìa phóng to, làm mờ, trôi chậm (hiệu ứng Ken Burns),
 * thêm các vầng màu lấy từ ảnh bìa phập phồng theo bass thật. Đổi bài thì chuyển mờ dần.
 */
@Composable
fun PlayerBackdrop(track: Track, frame: State<FloatArray>, playing: Boolean, modifier: Modifier = Modifier) {
    Crossfade(track.id, modifier, animationSpec = tween(700), label = "backdrop") { id ->
        val t = if (id == track.id) track else Library.byId(id) ?: track
        val mood by produceState<Mood?>(null, t.id, t.hasArt) { value = moodOf(t) }
        val inf = rememberInfiniteTransition(label = "kb")
        val phase by inf.animateFloat(0f, 1f, infiniteRepeatable(tween(24000, easing = LinearEasing)), label = "phase")
        val breathe by inf.animateFloat(1.25f, 1.45f, infiniteRepeatable(tween(9000), RepeatMode.Reverse), label = "zoom")

        Box(Modifier.fillMaxSize().background(VG.Cream)) {
            val m = mood
            if (m?.small != null) {
                Image(
                    m.small, null,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.fillMaxSize()
                        .graphicsLayer {
                            val a = phase * 2f * Math.PI.toFloat()
                            scaleX = breathe; scaleY = breathe
                            translationX = cos(a) * size.width * 0.06f
                            translationY = sin(a) * size.height * 0.04f
                            rotationZ = sin(a) * 4f
                            alpha = 0.55f
                        }
                        .then(if (Build.VERSION.SDK_INT >= 31) Modifier.blur(30.dp) else Modifier),
                )
            }
            val accent = m?.accent ?: VG.Peach
            val second = m?.second ?: VG.PeachSoft
            // vầng màu trôi + phập phồng theo bass
            Canvas(Modifier.fillMaxSize()) {
                val f = frame.value
                val a = phase * 2f * Math.PI.toFloat()
                val w = size.width
                val h = size.height
                val boost = if (playing) f.bass else 0f
                fun blob(cx: Float, cy: Float, r: Float, c: Color, alpha: Float) =
                    drawCircle(Brush.radialGradient(listOf(c.copy(alpha = alpha), Color.Transparent), Offset(cx, cy), r), r, Offset(cx, cy))
                blob(w * (0.25f + 0.12f * cos(a)), h * (0.22f + 0.06f * sin(a * 2)), w * (0.75f + boost * 0.25f), accent, 0.40f + boost * 0.25f)
                blob(w * (0.80f + 0.10f * sin(a)), h * (0.42f + 0.08f * cos(a)), w * (0.65f + boost * 0.2f), second, 0.32f)
                blob(w * (0.45f + 0.15f * sin(a * 1.5f)), h * 0.70f, w * 0.7f, VG.Peach, 0.18f + f.level * 0.15f)
            }
            // phủ kem phía dưới để chữ & nút luôn dễ đọc
            Box(
                Modifier.fillMaxSize().background(
                    Brush.verticalGradient(
                        0f to VG.Cream.copy(alpha = 0.25f),
                        0.45f to VG.Cream.copy(alpha = 0.35f),
                        0.75f to VG.Cream.copy(alpha = 0.85f),
                        1f to VG.Cream,
                    )
                )
            )
        }
    }
}

@Suppress("unused")
private fun ImageBitmap.toAndroid() = asAndroidBitmap()
