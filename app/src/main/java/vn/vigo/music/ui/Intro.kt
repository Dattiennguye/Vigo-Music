package vn.vigo.music.ui

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearOutSlowInEasing
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/**
 * Hiệu ứng mở app (~1,8 giây, chạm để bỏ qua):
 * đĩa than lăn vào và xoay, nhãn cam bật ra, sóng âm lan ra, chữ "Vigo Music" hiện từng chữ,
 * rồi cả màn trượt mờ đi để lộ ứng dụng.
 */
@Composable
fun IntroOverlay(onFinished: () -> Unit) {
    val disc = remember { Animatable(0f) }      // đĩa lăn vào
    val spin = remember { Animatable(-540f) }
    val label = remember { Animatable(0f) }
    val ripple = remember { Animatable(0f) }
    val text = remember { Animatable(0f) }
    val exit = remember { Animatable(0f) }
    val title = "Vigo Music"

    LaunchedEffect(Unit) {
        launch { disc.animateTo(1f, spring(dampingRatio = 0.62f, stiffness = 140f)) }
        launch { spin.animateTo(0f, tween(1100, easing = FastOutSlowInEasing)) }
        launch { delay(380); label.animateTo(1f, spring(dampingRatio = 0.38f, stiffness = 300f)) }
        launch { delay(520); ripple.animateTo(1f, tween(900, easing = LinearOutSlowInEasing)) }
        launch { delay(450); text.animateTo(1f, tween(700, easing = LinearOutSlowInEasing)) }
        delay(1450)
        exit.animateTo(1f, tween(380, easing = FastOutSlowInEasing))
        onFinished()
    }

    Box(
        Modifier.fillMaxSize()
            .graphicsLayer {
                alpha = 1f - exit.value
                val s = 1f + exit.value * 0.12f
                scaleX = s; scaleY = s
            }
            .background(VG.Cream)
            .clickable(remember { MutableInteractionSource() }, null) { onFinished() },
        contentAlignment = Alignment.Center,
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Box(Modifier.size(220.dp), contentAlignment = Alignment.Center) {
                // sóng âm lan ra
                Canvas(Modifier.fillMaxSize()) {
                    val t = ripple.value
                    if (t > 0f) for (k in 0 until 3) {
                        val p = ((t - k * 0.18f) / 0.82f).coerceIn(0f, 1f)
                        if (p > 0f) drawCircle(
                            VG.Peach.copy(alpha = (1f - p) * 0.45f),
                            radius = size.minDimension * (0.32f + 0.30f * p),
                            style = Stroke((3f - 2f * p).dp.toPx()),
                        )
                    }
                }
                Canvas(
                    Modifier.size(150.dp).graphicsLayer {
                        val d = disc.value
                        translationX = (1f - d) * 420f
                        alpha = d.coerceIn(0f, 1f)
                        rotationZ = spin.value
                        scaleX = 0.6f + 0.4f * d; scaleY = 0.6f + 0.4f * d
                    }
                ) {
                    val r = size.minDimension / 2f
                    drawCircle(VG.Vinyl, r)
                    var gr = r * 0.95f
                    var i = 0
                    while (gr > r * 0.42f) {
                        drawCircle(Color.White.copy(alpha = if (i % 3 == 0) 0.08f else 0.04f), gr, style = Stroke(1f))
                        gr -= r * 0.035f; i++
                    }
                    val lr = r * 0.38f * label.value
                    drawCircle(VG.Peach, lr)
                    // vệt sáng để thấy rõ đĩa đang xoay
                    drawLine(
                        Brush.linearGradient(listOf(Color.Transparent, Color.White.copy(alpha = 0.25f), Color.Transparent), Offset(center.x, 0f), Offset(center.x, r * 0.55f)),
                        Offset(center.x, r * 0.08f), Offset(center.x, r * 0.55f), 6f, StrokeCap.Round,
                    )
                    drawCircle(Color(0xFFE5DDDA), r * 0.04f)
                }
            }
            Spacer(Modifier.height(18.dp))
            Row(horizontalArrangement = Arrangement.Center) {
                title.forEachIndexed { i, ch ->
                    val start = i / (title.length + 4f)
                    val p = ((text.value - start) / 0.35f).coerceIn(0f, 1f)
                    Text(
                        ch.toString(),
                        fontSize = 34.sp, fontWeight = FontWeight.Bold, letterSpacing = (-1).sp,
                        color = if (i < 4) VG.Ink else VG.Peach,
                        modifier = Modifier.graphicsLayer { alpha = p; translationY = (1f - p) * 40f },
                    )
                }
            }
            Spacer(Modifier.height(6.dp))
            Text(
                "Thế giới nhỏ của bạn",
                style = VG.Body,
                modifier = Modifier.graphicsLayer { alpha = ((text.value - 0.6f) / 0.4f).coerceIn(0f, 1f) },
            )
        }
    }
}
