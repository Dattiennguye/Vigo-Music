package vn.vigo.music.ui

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.animateScrollBy
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.ContentPaste
import androidx.compose.material.icons.rounded.Delete
import androidx.compose.material.icons.rounded.FileOpen
import androidx.compose.material.icons.rounded.Lyrics
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import vn.vigo.music.data.Lyrics
import vn.vigo.music.data.LyricsRepo
import vn.vigo.music.data.Track
import vn.vigo.music.player.PlayerHub
import kotlin.math.abs

/** Trạng thái lời của bài đang phát, dùng chung cho tab Lời và dòng chạy dưới tên bài. */
class LyricsState {
    var lyrics by mutableStateOf<Lyrics?>(null)
    var loading by mutableStateOf(false)
    var searched by mutableStateOf(false)
    var error by mutableStateOf<String?>(null)
}

@Composable
fun rememberLyrics(track: Track): LyricsState {
    val st = remember(track.id) { LyricsState() }
    LaunchedEffect(track.id) {
        st.loading = true
        st.lyrics = LyricsRepo.load(track)
        st.searched = LyricsRepo.knownMissing(track.id)
        st.loading = false
    }
    return st
}

/** Một dòng lời ngắn chạy dưới tên bài ở tab "Bài hát". */
@Composable
fun LyricTicker(st: LyricsState, positionMs: Long, modifier: Modifier = Modifier) {
    val l = st.lyrics ?: return
    if (!l.synced) return
    val idx = l.indexAt(positionMs + 150)
    val line = l.lines.getOrNull(idx)?.text.orEmpty()
    AnimatedContent(
        line,
        transitionSpec = { (slideInVertically { it } + fadeIn(tween(250))) togetherWith (slideOutVertically { -it } + fadeOut(tween(200))) },
        modifier = modifier,
        label = "ticker",
    ) { t ->
        Text(
            if (t.isBlank()) "♪" else t,
            style = VG.Body.copy(color = VG.Peach, fontWeight = FontWeight.Medium),
            maxLines = 1,
        )
    }
}

@Composable
fun LyricsPanel(track: Track, st: LyricsState, positionMs: Long, isPlaying: Boolean, toast: (String) -> Unit) {
    val scope = rememberCoroutineScope()
    val context = LocalContext.current
    var pasting by remember { mutableStateOf(false) }
    var confirmDelete by remember { mutableStateOf(false) }

    fun searchOnline() {
        scope.launch {
            st.loading = true; st.error = null
            try {
                val l = LyricsRepo.fetchOnline(track)
                st.lyrics = l
                st.searched = true
                if (l == null) toast("Chưa tìm thấy lời cho bài này")
            } catch (e: Exception) {
                st.error = "Không kết nối được máy chủ lời bài hát"
            }
            st.loading = false
        }
    }

    val lrcPicker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri: Uri? ->
        if (uri != null) scope.launch {
            val text = withContext(Dispatchers.IO) {
                try { context.contentResolver.openInputStream(uri)?.bufferedReader()?.readText() } catch (_: Exception) { null }
            }
            val l = text?.let { LyricsRepo.parse(it, "File .lrc") }
            if (l == null) toast("File không có lời hợp lệ")
            else {
                withContext(Dispatchers.IO) { LyricsRepo.save(track.id, text, "File .lrc") }
                st.lyrics = l
                toast(if (l.synced) "Đã thêm lời chạy theo nhạc" else "Đã thêm lời")
            }
        }
    }

    val l = st.lyrics
    Box(Modifier.fillMaxSize()) {
        when {
            st.loading -> Column(Modifier.align(Alignment.Center), horizontalAlignment = Alignment.CenterHorizontally) {
                CircularProgressIndicator(color = VG.Peach, strokeWidth = 3.dp, modifier = Modifier.size(34.dp))
                Spacer(Modifier.height(12.dp))
                Text("Đang tìm lời…", style = VG.Body)
            }
            l == null -> Column(
                Modifier.align(Alignment.Center).fillMaxWidth().padding(horizontal = 12.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Icon(Icons.Rounded.Lyrics, null, tint = VG.Peach, modifier = Modifier.size(44.dp))
                Spacer(Modifier.height(10.dp))
                Text(if (st.searched) "Chưa có lời cho bài này" else "Chưa có lời bài hát", style = VG.H2.copy(fontSize = 20.sp), textAlign = TextAlign.Center)
                Spacer(Modifier.height(6.dp))
                Text(
                    st.error ?: "Tìm lời chạy theo nhạc trên LRCLIB, hoặc tự thêm lời của bạn.",
                    style = VG.Body, textAlign = TextAlign.Center,
                )
                Spacer(Modifier.height(18.dp))
                BigButton(if (st.searched) "Tìm lại trên mạng" else "Tìm lời trên mạng", Icons.Rounded.Search, dark = true, onClick = { searchOnline() })
                Spacer(Modifier.height(10.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    BigButton("Dán lời", Icons.Rounded.ContentPaste, dark = false, onClick = { pasting = true }, modifier = Modifier.weight(1f))
                    BigButton("File .lrc", Icons.Rounded.FileOpen, dark = false, onClick = { lrcPicker.launch(arrayOf("*/*")) }, modifier = Modifier.weight(1f))
                }
            }
            l.synced -> SyncedLyrics(l, positionMs, isPlaying)
            else -> PlainLyrics(l)
        }
        if (l != null && !st.loading) {
            Row(
                Modifier.align(Alignment.TopEnd).padding(top = 2.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(l.source + if (l.synced) " · chạy theo nhạc" else "", style = VG.Small.copy(fontSize = 11.sp))
                BounceIconButton(onClick = { confirmDelete = true }, modifier = Modifier.size(36.dp)) {
                    Icon(Icons.Rounded.Delete, "Xóa lời", tint = VG.Gray, modifier = Modifier.size(18.dp))
                }
            }
        }
    }

    if (pasting) {
        var text by remember { mutableStateOf("") }
        AlertDialog(
            onDismissRequest = { pasting = false },
            containerColor = VG.Card,
            title = { Text("Dán lời bài hát") },
            text = {
                Column {
                    Text("Dán lời thường, hoặc lời dạng LRC có mốc thời gian [01:23.45] để chạy theo nhạc.", style = VG.Small)
                    Spacer(Modifier.height(8.dp))
                    OutlinedTextField(text, { text = it }, modifier = Modifier.fillMaxWidth().heightIn(min = 160.dp, max = 300.dp))
                }
            },
            confirmButton = {
                TextButton(enabled = text.isNotBlank(), onClick = {
                    val parsed = LyricsRepo.parse(text, "Tự thêm")
                    if (parsed != null) {
                        LyricsRepo.save(track.id, text, "Tự thêm")
                        st.lyrics = parsed
                    }
                    pasting = false
                }) { Text("Lưu") }
            },
            dismissButton = { TextButton(onClick = { pasting = false }) { Text("Hủy") } },
        )
    }
    if (confirmDelete) {
        AlertDialog(
            onDismissRequest = { confirmDelete = false },
            containerColor = VG.Card,
            title = { Text("Xóa lời bài hát?") },
            text = { Text("Bạn có thể tìm lại hoặc thêm lời khác sau.") },
            confirmButton = {
                TextButton(onClick = {
                    LyricsRepo.delete(track.id); st.lyrics = null; st.searched = false; confirmDelete = false
                }) { Text("Xóa", color = Color(0xFFC0392B)) }
            },
            dismissButton = { TextButton(onClick = { confirmDelete = false }) { Text("Hủy") } },
        )
    }
}

@Composable
private fun SyncedLyrics(l: Lyrics, positionMs: Long, isPlaying: Boolean) {
    val listState = rememberLazyListState()
    val idx = l.indexAt(positionMs + 150)
    var userScrollAt by remember { mutableLongStateOf(0L) }
    val auto = remember { booleanArrayOf(false) }

    // Người dùng tự cuộn thì tạm dừng tự cuộn 3 giây
    LaunchedEffect(listState) {
        snapshotFlow { listState.isScrollInProgress }.collect { scrolling ->
            if (scrolling && !auto[0]) userScrollAt = System.currentTimeMillis()
        }
    }
    LaunchedEffect(idx) {
        if (idx < 0) return@LaunchedEffect
        if (System.currentTimeMillis() - userScrollAt < 3000) return@LaunchedEffect
        auto[0] = true
        try {
        val info = listState.layoutInfo
        val item = info.visibleItemsInfo.firstOrNull { it.index == idx }
        if (item != null) {
            val viewportCenter = (info.viewportStartOffset + info.viewportEndOffset) / 2
            val target = item.offset + item.size / 2 - viewportCenter + (info.viewportEndOffset - info.viewportStartOffset) / 8
            listState.animateScrollBy(target.toFloat(), spring(dampingRatio = 0.9f, stiffness = 120f))
        } else {
            listState.animateScrollToItem((idx - 2).coerceAtLeast(0))
        }
        } finally { auto[0] = false }
    }

    val fade = Brush.verticalGradient(0f to Color.Transparent, 0.12f to Color.Black, 0.85f to Color.Black, 1f to Color.Transparent)
    LazyColumn(
        state = listState,
        modifier = Modifier.fillMaxSize()
            .graphicsLayer { compositingStrategy = androidx.compose.ui.graphics.CompositingStrategy.Offscreen }
            .drawWithFade(fade),
        contentPadding = PaddingValues(top = 120.dp, bottom = 220.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        itemsIndexed(l.lines) { i, line ->
            val dist = if (idx < 0) 3 else abs(i - idx)
            val active = i == idx
            val scale by animateFloatAsState(if (active) 1f else 0.88f, spring(dampingRatio = 0.7f, stiffness = 300f), label = "ls")
            val alpha by animateFloatAsState(
                when { active -> 1f; dist == 1 -> 0.55f; dist == 2 -> 0.38f; else -> 0.25f },
                tween(350), label = "la",
            )
            val start = line.timeMs
            val end = l.lines.getOrNull(i + 1)?.timeMs ?: (start + 4000)
            val prog = if (active) ((positionMs - start).toFloat() / (end - start).coerceAtLeast(1)).coerceIn(0f, 1f) else if (i < idx) 1f else 0f
            val text = line.text.ifBlank { "♪ ♪ ♪" }
            val style = TextStyle(fontSize = 26.sp, fontWeight = FontWeight.Bold, lineHeight = 32.sp, letterSpacing = (-0.3).sp)
            Box(
                Modifier.fillMaxWidth()
                    .clip(RoundedCornerShape(14.dp))
                    .clickable(remember { MutableInteractionSource() }, null) { PlayerHub.seekTo(start) }
                    .padding(horizontal = 6.dp, vertical = 6.dp)
                    .graphicsLayer {
                        scaleX = scale; scaleY = scale
                        transformOrigin = androidx.compose.ui.graphics.TransformOrigin(0f, 0.5f)
                        this.alpha = alpha
                    }
            ) {
                if (active) {
                    // hiệu ứng karaoke: tô cam dần theo tiến độ của dòng
                    val edge = prog.coerceIn(0.001f, 0.999f)
                    Text(
                        text,
                        style = style.copy(
                            brush = Brush.horizontalGradient(
                                0f to VG.Peach, edge to VG.Peach,
                                (edge + 0.001f).coerceAtMost(1f) to VG.Ink, 1f to VG.Ink,
                            ),
                        ),
                    )
                } else {
                    Text(text, style = style.copy(color = if (i < idx) VG.Peach else VG.Ink))
                }
            }
        }
    }
}

@Composable
private fun PlainLyrics(l: Lyrics) {
    LazyColumn(
        Modifier.fillMaxSize(),
        contentPadding = PaddingValues(top = 36.dp, bottom = 40.dp),
    ) {
        item {
            Text(
                "Lời không có mốc thời gian nên không chạy theo nhạc. Bạn có thể tìm lại trên mạng hoặc dán bản LRC.",
                style = VG.Small, modifier = Modifier.padding(bottom = 14.dp),
            )
        }
        itemsIndexed(l.lines) { _, line ->
            Text(
                line.text,
                style = VG.Title.copy(fontSize = 19.sp, lineHeight = 28.sp, fontWeight = FontWeight.SemiBold),
                modifier = Modifier.padding(vertical = 2.dp),
            )
        }
    }
}

/** Làm mờ dần mép trên/dưới của danh sách lời. */
private fun Modifier.drawWithFade(brush: Brush): Modifier = this.drawWithContent {
    drawContent()
    drawRect(brush, blendMode = androidx.compose.ui.graphics.BlendMode.DstIn)
}
