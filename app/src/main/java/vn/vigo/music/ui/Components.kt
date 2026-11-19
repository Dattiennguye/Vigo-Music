package vn.vigo.music.ui

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.FavoriteBorder
import androidx.compose.material.icons.rounded.MusicNote
import androidx.compose.material.icons.rounded.Pause
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.foundation.shape.GenericShape
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.clipPath
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.isActive
import vn.vigo.music.data.Library
import vn.vigo.music.data.Track
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.roundToInt
import kotlin.math.sin
import kotlin.random.Random

// ---------- Ảnh bìa ----------

private val palettes = listOf(
    listOf(Color(0xFFF4A47C), Color(0xFF9A6A7E), Color(0xFF3E3442)), // hoàng hôn
    listOf(Color(0xFFC9B99A), Color(0xFF7D7357), Color(0xFF3D3A2E)), // đồng cỏ
    listOf(Color(0xFFB5A3D9), Color(0xFF8C7DB5), Color(0xFF3F3A5C)), // trăng tím
    listOf(Color(0xFFF7C9B0), Color(0xFFE8834F), Color(0xFF7A3E2B)), // đào
    listOf(Color(0xFFA9C6C2), Color(0xFF5E8A86), Color(0xFF2E4644)), // biển
    listOf(Color(0xFFE9B7B7), Color(0xFFB3747E), Color(0xFF4F3540)), // hồng đất
)

fun paletteFor(key: String): List<Color> = palettes[abs(key.hashCode()) % palettes.size]

@Composable
fun rememberArt(track: Track?, maxPx: Int = 512): ImageBitmap? {
    val art by produceState<ImageBitmap?>(null, track?.id, track?.hasArt, maxPx) {
        value = track?.let { Library.artwork(it, maxPx)?.asImageBitmap() }
    }
    return art
}

/** Nền "phong cảnh" kiểu ảnh mẫu: dải màu + núi, dùng khi bài không có ảnh bìa. */
@Composable
fun SceneryBackground(key: String, modifier: Modifier = Modifier) {
    val p = paletteFor(key)
    Canvas(modifier) {
        drawRect(Brush.verticalGradient(listOf(p[0].copy(alpha = 0.55f), p[0], p[1])))
        val w = size.width
        val h = size.height
        val far = Path().apply {
            moveTo(0f, h * 0.72f)
            lineTo(w * 0.18f, h * 0.60f); lineTo(w * 0.30f, h * 0.66f)
            lineTo(w * 0.48f, h * 0.50f); lineTo(w * 0.62f, h * 0.62f)
            lineTo(w * 0.78f, h * 0.54f); lineTo(w, h * 0.66f)
            lineTo(w, h); lineTo(0f, h); close()
        }
        drawPath(far, p[1].copy(alpha = 0.75f))
        val near = Path().apply {
            moveTo(0f, h * 0.86f)
            lineTo(w * 0.22f, h * 0.74f); lineTo(w * 0.40f, h * 0.84f)
            lineTo(w * 0.62f, h * 0.70f); lineTo(w * 0.85f, h * 0.82f)
            lineTo(w, h * 0.78f); lineTo(w, h); lineTo(0f, h); close()
        }
        drawPath(near, p[2].copy(alpha = 0.85f))
    }
}

@Composable
fun Artwork(track: Track?, modifier: Modifier = Modifier, corner: Dp = 14.dp, maxPx: Int = 256, seed: String? = null) {
    val art = rememberArt(track, maxPx)
    Box(modifier.clip(RoundedCornerShape(corner)), contentAlignment = Alignment.Center) {
        if (art != null) {
            Image(art, null, Modifier.fillMaxSize(), contentScale = ContentScale.Crop)
        } else {
            SceneryBackground(track?.id ?: seed ?: "x", Modifier.fillMaxSize())
            Icon(Icons.Rounded.MusicNote, null, tint = Color.White.copy(alpha = 0.85f), modifier = Modifier.size(22.dp))
        }
    }
}

// ---------- Đĩa than ----------

@Composable
fun Vinyl(spinning: Boolean, modifier: Modifier = Modifier, art: ImageBitmap? = null) {
    val rotation = remember { Animatable(0f) }
    LaunchedEffect(spinning) {
        if (spinning) {
            while (isActive) {
                rotation.animateTo(rotation.value + 360f, tween(9000, easing = LinearEasing))
                rotation.snapTo(rotation.value % 360f)
            }
        }
    }
    Box(modifier) {
        Canvas(Modifier.fillMaxSize().graphicsLayer { rotationZ = rotation.value }) {
            val r = size.minDimension / 2f
            val c = center
            drawCircle(VG.Vinyl, r, c)
            var gr = r * 0.97f
            var i = 0
            while (gr > r * 0.42f) {
                drawCircle(Color.White.copy(alpha = if (i % 3 == 0) 0.07f else 0.035f), gr, c, style = Stroke(0.8.dp.toPx()))
                gr -= r * 0.028f; i++
            }
            val lr = r * 0.38f
            if (art != null) {
                val clip = Path().apply { addOval(Rect(c, lr)) }
                clipPath(clip) {
                    drawImage(
                        art,
                        srcOffset = IntOffset.Zero,
                        srcSize = IntSize(art.width, art.height),
                        dstOffset = IntOffset((c.x - lr).roundToInt(), (c.y - lr).roundToInt()),
                        dstSize = IntSize((lr * 2).roundToInt(), (lr * 2).roundToInt()),
                    )
                }
            } else {
                drawCircle(VG.Peach, lr, c)
                // biểu tượng sóng âm nhỏ trên nhãn đĩa
                val hs = listOf(0.25f, 0.5f, 0.8f, 1f, 0.7f, 0.45f, 0.25f)
                val step = lr * 0.12f
                val sx = c.x - step * (hs.size - 1) / 2f
                val cy = c.y - lr * 0.38f
                hs.forEachIndexed { k, hh ->
                    val half = lr * 0.14f * hh
                    drawLine(Color.White.copy(alpha = 0.9f), Offset(sx + k * step, cy - half), Offset(sx + k * step, cy + half), 2.dp.toPx(), StrokeCap.Round)
                }
            }
            drawCircle(Color(0xFFE5DDDA), r * 0.04f, c)
            drawCircle(Color(0xFF9E9592), r * 0.018f, c)
        }
        // ánh sáng phản chiếu đứng yên (không xoay theo đĩa)
        Canvas(Modifier.fillMaxSize()) {
            val r = size.minDimension / 2f
            drawCircle(
                Brush.sweepGradient(
                    listOf(
                        Color.Transparent, Color.White.copy(alpha = 0.10f), Color.Transparent,
                        Color.Transparent, Color.White.copy(alpha = 0.07f), Color.Transparent,
                    ),
                    center,
                ),
                r, center,
            )
        }
    }
}

// ---------- Bìa đĩa (hero giống ảnh mẫu) ----------

private val SleeveShape = GenericShape { size, _ ->
    val w = size.width
    val h = size.height
    val cr = w * 0.07f
    val br = w * 0.10f
    val d = h * 0.26f
    moveTo(0f, cr)
    quadraticBezierTo(0f, 0f, cr, 0f)
    lineTo(w * 0.20f, 0f)
    cubicTo(w * 0.30f, 0f, w * 0.32f, d, w * 0.5f, d)
    cubicTo(w * 0.68f, d, w * 0.70f, 0f, w * 0.80f, 0f)
    lineTo(w - cr, 0f)
    quadraticBezierTo(w, 0f, w, cr)
    lineTo(w, h - br)
    quadraticBezierTo(w, h, w - br, h)
    lineTo(br, h)
    quadraticBezierTo(0f, h, 0f, h - br)
    close()
}

@Composable
fun SleeveHero(
    keySeed: String,
    label: String,
    title: String,
    spinning: Boolean,
    modifier: Modifier = Modifier,
    art: ImageBitmap? = null,
    labelArt: ImageBitmap? = null,
    showHeart: Boolean = true,
    onPlay: (() -> Unit)? = null,
    playing: Boolean = false,
) {
    BoxWithConstraints(modifier.fillMaxWidth()) {
        val w = maxWidth
        val vinylSize = w * 0.80f
        val sleeveH = w * 0.56f
        Box(Modifier.fillMaxWidth().height(vinylSize * 0.52f + sleeveH)) {
            Vinyl(
                spinning,
                Modifier.size(vinylSize).align(Alignment.TopCenter),
                art = labelArt,
            )
            if (showHeart) {
                Box(
                    Modifier
                        .align(Alignment.TopCenter)
                        .offset(x = vinylSize * 0.30f, y = vinylSize * 0.04f)
                        .rotate(8f)
                        .shadow(6.dp, RoundedCornerShape(10.dp))
                        .background(VG.PeachPale, RoundedCornerShape(10.dp))
                        .size(width = 46.dp, height = 56.dp),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(Icons.Rounded.FavoriteBorder, null, tint = VG.Ink, modifier = Modifier.size(24.dp))
                }
            }
            Box(
                Modifier
                    .align(Alignment.BottomCenter)
                    .fillMaxWidth()
                    .height(sleeveH)
                    .shadow(18.dp, SleeveShape, ambientColor = VG.Peach, spotColor = VG.Peach.copy(alpha = 0.5f))
                    .clip(SleeveShape)
            ) {
                if (art != null) {
                    Image(art, null, Modifier.fillMaxSize(), contentScale = ContentScale.Crop)
                    Box(Modifier.fillMaxSize().background(Brush.verticalGradient(listOf(Color.Black.copy(alpha = 0.05f), Color.Black.copy(alpha = 0.45f)))))
                } else {
                    SceneryBackground(keySeed, Modifier.fillMaxSize())
                }
                // lớp kính mờ phía trên
                Box(Modifier.fillMaxSize().background(Brush.verticalGradient(listOf(Color.White.copy(alpha = 0.35f), Color.Transparent))))
                Row(
                    Modifier.align(Alignment.CenterStart).fillMaxWidth().padding(start = 24.dp, end = 20.dp, top = sleeveH * 0.18f),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Column(Modifier.weight(1f)) {
                        Text(label, color = Color.White.copy(alpha = 0.85f), fontSize = 13.sp, maxLines = 1)
                        Text(
                            title, color = Color.White, fontSize = 28.sp, fontWeight = FontWeight.Bold,
                            letterSpacing = (-0.5).sp, maxLines = 1, overflow = TextOverflow.Ellipsis,
                        )
                    }
                    if (onPlay != null) {
                        Box(
                            Modifier.size(48.dp).shadow(8.dp, CircleShape).background(Color.White, CircleShape)
                                .clip(CircleShape).clickable { onPlay() },
                            contentAlignment = Alignment.Center,
                        ) {
                            Icon(if (playing) Icons.Rounded.Pause else Icons.Rounded.PlayArrow, "Phát", tint = VG.Ink)
                        }
                    }
                }
            }
        }
    }
}

// ---------- Sóng âm / thanh tua ----------

fun waveHeights(seed: String, count: Int): List<Float> {
    val rnd = Random(seed.hashCode())
    return List(count) { i ->
        val env = 0.45f + 0.55f * sin(PI.toFloat() * (i + 0.5f) / count)
        (0.25f + rnd.nextFloat() * 0.75f) * env
    }
}

/**
 * Thanh sóng âm có thể chạm/kéo để tua. [onSeek] nhận tỉ lệ 0..1.
 */
@Composable
fun WaveformSeek(
    seed: String,
    progress: Float,
    modifier: Modifier = Modifier,
    bars: Int = 36,
    active: Color = VG.Peach,
    inactive: Color = VG.PeachSoft,
    onSeek: ((Float) -> Unit)? = null,
) {
    val heights = remember(seed, bars) { waveHeights(seed, bars) }
    var dragging by remember { mutableStateOf<Float?>(null) }
    val shown = (dragging ?: progress).coerceIn(0f, 1f)
    val input = if (onSeek == null) Modifier else Modifier
        .pointerInput(onSeek) {
            detectTapGestures { o -> onSeek((o.x / size.width).coerceIn(0f, 1f)) }
        }
        .pointerInput(onSeek) {
            detectHorizontalDragGestures(
                onDragStart = { o -> dragging = (o.x / size.width).coerceIn(0f, 1f) },
                onHorizontalDrag = { change, _ -> dragging = (change.position.x / size.width).coerceIn(0f, 1f) },
                onDragEnd = { dragging?.let(onSeek); dragging = null },
                onDragCancel = { dragging = null },
            )
        }
    Canvas(modifier.then(input)) {
        val gap = size.width / bars
        val stroke = (gap * 0.45f).coerceAtMost(4.dp.toPx())
        heights.forEachIndexed { i, hh ->
            val x = gap * (i + 0.5f)
            val half = size.height / 2f * hh
            val played = (i + 0.5f) / bars <= shown
            drawLine(
                if (played) active else inactive,
                Offset(x, size.height / 2f - half), Offset(x, size.height / 2f + half),
                stroke, StrokeCap.Round,
            )
        }
    }
}

// ---------- Mini player ----------

@Composable
fun MiniPlayer(
    track: Track,
    isPlaying: Boolean,
    positionMs: Long,
    durationMs: Long,
    onToggle: () -> Unit,
    onOpen: () -> Unit,
    onSeek: (Float) -> Unit,
    modifier: Modifier = Modifier,
) {
    val progress = if (durationMs > 0) positionMs.toFloat() / durationMs else 0f
    Row(
        modifier
            .fillMaxWidth()
            .shadow(16.dp, RoundedCornerShape(30.dp), ambientColor = VG.Peach.copy(alpha = 0.4f), spotColor = VG.Peach.copy(alpha = 0.35f))
            .background(VG.Card, RoundedCornerShape(30.dp))
            .clip(RoundedCornerShape(30.dp))
            .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null) { onOpen() }
            .padding(horizontal = 12.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Box(
            Modifier.size(44.dp).clip(CircleShape).background(VG.Ink).clickable { onToggle() },
            contentAlignment = Alignment.Center,
        ) {
            Icon(if (isPlaying) Icons.Rounded.Pause else Icons.Rounded.PlayArrow, if (isPlaying) "Tạm dừng" else "Phát", tint = Color.White)
        }
        Column(Modifier.weight(1f)) {
            Text(track.title, style = VG.Title.copy(fontSize = 14.sp), maxLines = 1, overflow = TextOverflow.Ellipsis)
            Spacer(Modifier.height(2.dp))
            WaveformSeek(track.id, progress, Modifier.fillMaxWidth().height(22.dp), bars = 32, onSeek = onSeek)
        }
        Text(
            formatTime((durationMs - positionMs).coerceAtLeast(0)),
            style = VG.Small.copy(color = VG.Ink.copy(alpha = 0.7f)),
            modifier = Modifier.width(44.dp),
        )
    }
}
