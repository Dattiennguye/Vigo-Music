package vn.vigo.music.ui

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Favorite
import androidx.compose.material.icons.rounded.FavoriteBorder
import androidx.compose.material.icons.rounded.Pause
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.State
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import vn.vigo.music.player.AudioLevels
import kotlin.math.cos
import kotlin.math.sin

// ---------- Nhấn nút: thu nhỏ rồi nảy lại + rung nhẹ ----------

fun Modifier.bounceClick(
    enabled: Boolean = true,
    scaleDown: Float = 0.88f,
    haptic: Boolean = true,
    onClick: () -> Unit,
): Modifier = composed {
    val interaction = remember { MutableInteractionSource() }
    val pressed by interaction.collectIsPressedAsState()
    val scale by animateFloatAsState(
        if (pressed && enabled) scaleDown else 1f,
        spring(dampingRatio = 0.42f, stiffness = 700f),
        label = "bounce",
    )
    val hf = LocalHapticFeedback.current
    this
        .graphicsLayer { scaleX = scale; scaleY = scale }
        .clickable(interactionSource = interaction, indication = null, enabled = enabled) {
            if (haptic) hf.performHapticFeedback(HapticFeedbackType.TextHandleMove)
            onClick()
        }
}

/** Thay IconButton: vùng chạm 48dp, nảy khi bấm. */
@Composable
fun BounceIconButton(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    content: @Composable () -> Unit,
) {
    Box(
        modifier.size(48.dp).bounceClick(enabled = enabled, scaleDown = 0.8f, onClick = onClick),
        contentAlignment = Alignment.Center,
    ) { content() }
}

// ---------- Play / Pause đổi hình mượt ----------

@Composable
fun PlayPauseIcon(isPlaying: Boolean, tint: Color, size: Dp) {
    AnimatedContent(
        isPlaying,
        transitionSpec = {
            (scaleIn(tween(220), initialScale = 0.4f) + fadeIn(tween(160))) togetherWith
                (scaleOut(tween(160), targetScale = 0.4f) + fadeOut(tween(120)))
        },
        label = "pp",
    ) { playing ->
        Icon(
            if (playing) Icons.Rounded.Pause else Icons.Rounded.PlayArrow,
            if (playing) "Tạm dừng" else "Phát",
            tint = tint, modifier = Modifier.size(size),
        )
    }
}

// ---------- Tim: bật to + bắn hạt khi thả tim ----------

@Composable
fun HeartButton(isFav: Boolean, onToggle: () -> Unit, size: Dp = 24.dp, idleTint: Color = VG.Gray) {
    val pop = remember { Animatable(1f) }
    val burst = remember { Animatable(1f) }
    var first by remember { mutableStateOf(true) }
    LaunchedEffect(isFav) {
        if (first) { first = false; return@LaunchedEffect }
        if (isFav) {
            launch { burst.snapTo(0f); burst.animateTo(1f, tween(520, easing = FastOutSlowInEasing)) }
            pop.snapTo(0.6f)
            pop.animateTo(1f, spring(dampingRatio = 0.32f, stiffness = Spring.StiffnessMediumLow))
        } else {
            pop.snapTo(0.8f); pop.animateTo(1f, spring(dampingRatio = 0.5f))
        }
    }
    BounceIconButton(onClick = onToggle) {
        Box(contentAlignment = Alignment.Center) {
            Canvas(Modifier.size(size * 2.2f)) {
                val t = burst.value
                if (t < 1f) {
                    val r0 = this.size.minDimension * 0.18f
                    val r = r0 + this.size.minDimension * 0.32f * t
                    val a = (1f - t)
                    for (i in 0 until 8) {
                        val ang = (i * 45f + 22.5f) * (Math.PI / 180).toFloat()
                        val c = if (i % 2 == 0) VG.Peach else VG.PeachSoft
                        drawCircle(c.copy(alpha = a), radius = 3.dp.toPx() * (1f - t * 0.6f), center = center + Offset(cos(ang) * r, sin(ang) * r))
                    }
                    drawCircle(VG.Peach.copy(alpha = 0.25f * a), radius = r0 + this.size.minDimension * 0.25f * t, style = androidx.compose.ui.graphics.drawscope.Stroke(2.dp.toPx() * a))
                }
            }
            Icon(
                if (isFav) Icons.Rounded.Favorite else Icons.Rounded.FavoriteBorder,
                if (isFav) "Bỏ yêu thích" else "Yêu thích",
                tint = if (isFav) VG.Peach else idleTint,
                modifier = Modifier.size(size).graphicsLayer { scaleX = pop.value; scaleY = pop.value },
            )
        }
    }
}

// ---------- Âm thanh thật → trạng thái mượt cho giao diện ----------

/**
 * Đọc phổ âm thanh đang phát mỗi khung hình, làm mượt (lên nhanh, xuống chậm).
 * Chỉ đọc giá trị trong lambda vẽ/graphicsLayer để không gây vẽ lại cả màn hình.
 */
@Composable
fun rememberAudioFrame(active: Boolean): State<FloatArray> {
    val state = remember { mutableStateOf(FloatArray(AudioLevels.SIZE)) }
    LaunchedEffect(active) {
        val smooth = state.value.copyOf()
        while (isActive) {
            withFrameNanos { }
            val f = if (active) AudioLevels.latest() else null
            var moving = false
            for (i in smooth.indices) {
                val target = f?.get(i) ?: 0f
                val k = if (target > smooth[i]) 0.55f else 0.14f
                val nv = smooth[i] + (target - smooth[i]) * k
                if (kotlin.math.abs(nv - smooth[i]) > 0.002f) moving = true
                smooth[i] = nv
            }
            if (moving || f != null) state.value = smooth.copyOf()
            else if (!active && smooth.all { it < 0.003f }) break
        }
    }
    return state
}

fun FloatArray.band(i: Int): Float = if (i in 0 until AudioLevels.BANDS) this[i] else 0f
val FloatArray.level: Float get() = this[AudioLevels.LEVEL]
val FloatArray.bass: Float get() = this[AudioLevels.BASS]

/** Cột equalizer nhỏ (dùng cho bài đang phát trong danh sách, mini player). */
@Composable
fun EqualizerBars(frame: State<FloatArray>, modifier: Modifier = Modifier, color: Color = Color.White, bars: Int = 4) {
    Canvas(modifier) {
        val f = frame.value
        val w = size.width / (bars * 2 - 1)
        for (i in 0 until bars) {
            val v = f.band(2 + i * 5).coerceAtLeast(0.12f)
            val h = size.height * v
            drawRoundRect(
                color, Offset(i * 2 * w, size.height - h), Size(w, h), CornerRadius(w / 2, w / 2),
            )
        }
    }
}

/** Phổ âm thanh đối xứng, cột bo tròn màu cam đào. */
@Composable
fun LiveSpectrum(frame: State<FloatArray>, modifier: Modifier = Modifier, color: Color = VG.Peach) {
    Canvas(modifier) {
        val f = frame.value
        val n = AudioLevels.BANDS
        val total = n * 2
        val gap = size.width / total
        val bw = gap * 0.55f
        val brush = Brush.verticalGradient(listOf(color, color.copy(alpha = 0.35f)))
        for (j in 0 until total) {
            // giữa là bass, hai bên là tần số cao — đối xứng
            val bandIdx = if (j < n) n - 1 - j else j - n
            val v = (0.06f + f.band(bandIdx) * 0.94f)
            val h = size.height * v
            val x = gap * (j + 0.5f) - bw / 2
            drawRoundRect(brush, Offset(x, (size.height - h) / 2), Size(bw, h), CornerRadius(bw / 2, bw / 2))
        }
    }
}
