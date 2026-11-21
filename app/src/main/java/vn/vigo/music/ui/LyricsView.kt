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
import androidx.compose.animation.core.animateFloat
import androidx.compose.foundation.verticalScroll
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
import vn.vigo.music.ai.AiLang
import vn.vigo.music.ai.AiLyrics
import vn.vigo.music.ai.AiModel
import vn.vigo.music.data.Lyrics
import androidx.compose.material.icons.rounded.AutoAwesome
import androidx.compose.material.icons.rounded.Edit
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material3.LinearProgressIndicator
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
    var pasteInitial by remember { mutableStateOf("") }
    var confirmDelete by remember { mutableStateOf(false) }
    var aiSetup by remember { mutableStateOf(false) }
    val aiJob by AiLyrics.state.collectAsState()
    val aiDone by AiLyrics.done.collectAsState()
    val myJob = aiJob?.takeIf { it.trackId == track.id }
    LaunchedEffect(aiDone) {
        val d = aiDone
        if (d != null && d.first == track.id) { st.lyrics = d.second; toast("AI đã viết xong lời 🎵") }
    }

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
            myJob != null && myJob.running -> AiProgress(myJob)
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
                if (myJob?.error != null) {
                    Spacer(Modifier.height(10.dp))
                    Text("AI: ${myJob.error}", style = VG.Small.copy(color = Color(0xFFC0392B)), textAlign = TextAlign.Center)
                }
                Spacer(Modifier.height(18.dp))
                AiButton { AiLyrics.clearError(); aiSetup = true }
                Spacer(Modifier.height(10.dp))
                BigButton(if (st.searched) "Tìm lại trên mạng" else "Tìm lời trên mạng", Icons.Rounded.Search, dark = true, onClick = { searchOnline() })
                Spacer(Modifier.height(10.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    BigButton("Dán lời", Icons.Rounded.ContentPaste, dark = false, onClick = { pasteInitial = ""; pasting = true }, modifier = Modifier.weight(1f))
                    BigButton("File .lrc", Icons.Rounded.FileOpen, dark = false, onClick = { lrcPicker.launch(arrayOf("*/*")) }, modifier = Modifier.weight(1f))
                }
            }
            l.synced -> SyncedLyrics(l, positionMs, isPlaying)
            else -> PlainLyrics(l)
        }
        if (l != null && !st.loading && myJob?.running != true) {
            Row(
                Modifier.align(Alignment.TopEnd).padding(top = 2.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(l.source + if (l.synced) " · chạy theo nhạc" else "", style = VG.Small.copy(fontSize = 11.sp))
                BounceIconButton(onClick = { AiLyrics.clearError(); aiSetup = true }, modifier = Modifier.size(36.dp)) {
                    Icon(Icons.Rounded.AutoAwesome, "Tạo lại bằng AI", tint = VG.Peach, modifier = Modifier.size(18.dp))
                }
                BounceIconButton(onClick = {
                    pasteInitial = LyricsRepo.rawText(track.id).orEmpty(); pasting = true
                }, modifier = Modifier.size(36.dp)) {
                    Icon(Icons.Rounded.Edit, "Sửa lời", tint = VG.Gray, modifier = Modifier.size(18.dp))
                }
                BounceIconButton(onClick = { confirmDelete = true }, modifier = Modifier.size(36.dp)) {
                    Icon(Icons.Rounded.Delete, "Xóa lời", tint = VG.Gray, modifier = Modifier.size(18.dp))
                }
            }
        }
    }

    if (aiSetup) {
        AiSetupDialog(onDismiss = { aiSetup = false }) { model, lang ->
            aiSetup = false
            AiLyrics.start(context, track, model, lang)
        }
    }

    if (pasting) {
        var text by remember { mutableStateOf(pasteInitial) }
        AlertDialog(
            onDismissRequest = { pasting = false },
            containerColor = VG.Card,
            title = { Text(if (pasteInitial.isBlank()) "Dán lời bài hát" else "Sửa lời bài hát") },
            text = {
                Column {
                    Text("Dán lời thường, hoặc lời dạng LRC có mốc thời gian [01:23.45] để chạy theo nhạc.", style = VG.Small)
                    Spacer(Modifier.height(8.dp))
                    OutlinedTextField(text, { text = it }, modifier = Modifier.fillMaxWidth().heightIn(min = 160.dp, max = 300.dp))
                }
            },
            confirmButton = {
                TextButton(enabled = text.isNotBlank(), onClick = {
                    val src = if (pasteInitial.isBlank()) "Tự thêm" else (st.lyrics?.source ?: "Tự thêm").let { if (it.endsWith("(đã sửa)")) it else "$it (đã sửa)" }
                    val parsed = LyricsRepo.parse(text, src)
                    if (parsed != null) {
                        LyricsRepo.save(track.id, text, src)
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

// ---------- Lời AI ----------

@Composable
private fun AiButton(onClick: () -> Unit) {
    val inf = androidx.compose.animation.core.rememberInfiniteTransition(label = "aib")
    val shift by inf.animateFloat(
        0f, 1f, androidx.compose.animation.core.infiniteRepeatable(tween(2600), androidx.compose.animation.core.RepeatMode.Reverse), label = "s",
    )
    Row(
        Modifier.fillMaxWidth().height(56.dp).bounceClick(scaleDown = 0.95f) { onClick() }
            .clip(RoundedCornerShape(28.dp))
            .background(
                Brush.linearGradient(
                    listOf(VG.Peach, Color(0xFFF0A27A), Color(0xFFD9694A)),
                    start = androidx.compose.ui.geometry.Offset(600f * shift, 0f),
                    end = androidx.compose.ui.geometry.Offset(600f * shift + 700f, 200f),
                )
            ),
        horizontalArrangement = Arrangement.Center, verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(Icons.Rounded.AutoAwesome, null, tint = Color.White)
        Spacer(Modifier.width(10.dp))
        Text("Tạo lời bằng AI (offline)", color = Color.White, fontSize = 16.sp, fontWeight = FontWeight.SemiBold)
    }
}

@Composable
private fun AiProgress(job: vn.vigo.music.ai.AiJob) {
    val inf = androidx.compose.animation.core.rememberInfiniteTransition(label = "aip")
    val t by inf.animateFloat(
        0f, 1f, androidx.compose.animation.core.infiniteRepeatable(tween(1400, easing = androidx.compose.animation.core.LinearEasing)), label = "t",
    )
    Column(
        Modifier.fillMaxSize().padding(horizontal = 18.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        // sóng âm "đang nghe" chạy lượn
        androidx.compose.foundation.Canvas(Modifier.fillMaxWidth().height(70.dp)) {
            val bars = 32
            val gap = size.width / bars
            for (i in 0 until bars) {
                val ph = (i.toFloat() / bars + t) * 2f * Math.PI.toFloat()
                val h = size.height * (0.2f + 0.8f * ((kotlin.math.sin(ph) + 1f) / 2f) * (0.5f + 0.5f * kotlin.math.sin(ph * 2.3f + 1f).let { (it + 1f) / 2f }))
                drawLine(
                    if (i % 3 == 0) VG.Peach else VG.PeachSoft,
                    androidx.compose.ui.geometry.Offset(gap * (i + 0.5f), (size.height - h) / 2),
                    androidx.compose.ui.geometry.Offset(gap * (i + 0.5f), (size.height + h) / 2),
                    gap * 0.45f, androidx.compose.ui.graphics.StrokeCap.Round,
                )
            }
        }
        Spacer(Modifier.height(18.dp))
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(Icons.Rounded.AutoAwesome, null, tint = VG.Peach, modifier = Modifier.size(20.dp))
            Spacer(Modifier.width(8.dp))
            Text(job.stage, style = VG.Title, textAlign = TextAlign.Center)
        }
        Spacer(Modifier.height(12.dp))
        if (job.percent >= 0) {
            val p by animateFloatAsState(job.percent / 100f, tween(300), label = "aipp")
            LinearProgressIndicator(
                progress = { p },
                modifier = Modifier.fillMaxWidth().height(8.dp).clip(CircleShape),
                color = VG.Peach, trackColor = VG.PeachPale,
            )
            Spacer(Modifier.height(6.dp))
            Text("${job.percent}%", style = VG.Small)
        } else {
            LinearProgressIndicator(Modifier.fillMaxWidth().height(8.dp).clip(CircleShape), color = VG.Peach, trackColor = VG.PeachPale)
        }
        Spacer(Modifier.height(14.dp))
        Text("Chạy hoàn toàn trên máy. Bạn vẫn nghe nhạc, chuyển tab bình thường trong lúc chờ.", style = VG.Small, textAlign = TextAlign.Center)
        Spacer(Modifier.height(16.dp))
        TextButton(onClick = { AiLyrics.cancel() }) { Text("Hủy", color = VG.Gray) }
    }
}

@Composable
private fun AiSetupDialog(onDismiss: () -> Unit, onStart: (AiModel, AiLang) -> Unit) {
    val context = LocalContext.current
    val prefs = remember { context.getSharedPreferences("vigo", android.content.Context.MODE_PRIVATE) }
    var model by remember { mutableStateOf(AiModel.entries.firstOrNull { it.name == prefs.getString("ai_model", null) } ?: AiModel.Balanced) }
    var lang by remember { mutableStateOf(AiLang.entries.firstOrNull { it.name == prefs.getString("ai_lang", null) } ?: AiLang.Vi) }
    var tick by remember { mutableStateOf(0) }
    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = VG.Card,
        icon = { Icon(Icons.Rounded.AutoAwesome, null, tint = VG.Peach) },
        title = { Text("Tạo lời bằng AI") },
        text = {
            Column(Modifier.verticalScrollCompat()) {
                Text("AI nghe giọng hát trong bài và tự viết lời chạy theo nhạc. Chạy trên máy, không gửi nhạc đi đâu.", style = VG.Small)
                Spacer(Modifier.height(12.dp))
                Text("Mô hình", style = VG.Title.copy(fontSize = 14.sp))
                Spacer(Modifier.height(6.dp))
                AiModel.entries.forEach { m ->
                    val sel = m == model
                    val have = remember(tick) { AiLyrics.isDownloaded(context, m) }
                    Row(
                        Modifier.fillMaxWidth().padding(vertical = 3.dp).clip(RoundedCornerShape(14.dp))
                            .background(if (sel) VG.PeachPale else VG.Cream)
                            .clickable { model = m }
                            .padding(12.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Column(Modifier.weight(1f)) {
                            Text(m.label + (if (m == AiModel.Balanced) " · khuyên dùng" else ""), style = VG.Title.copy(fontSize = 15.sp))
                            Text(m.note, style = VG.Small.copy(fontSize = 12.sp))
                            Text(
                                if (have) "Đã tải · ${m.sizeMb} MB" else "Cần tải ${m.sizeMb} MB (một lần)",
                                style = VG.Small.copy(fontSize = 12.sp, color = if (have) VG.Peach else VG.Gray, fontWeight = FontWeight.Medium),
                            )
                        }
                        if (sel) Icon(Icons.Rounded.CheckCircle, null, tint = VG.Peach)
                        if (have && !sel) {
                            BounceIconButton(onClick = { AiLyrics.deleteModel(context, m); tick++ }, modifier = Modifier.size(36.dp)) {
                                Icon(Icons.Rounded.Delete, "Xóa mô hình", tint = VG.Gray, modifier = Modifier.size(18.dp))
                            }
                        }
                    }
                }
                Spacer(Modifier.height(10.dp))
                Text("Ngôn ngữ bài hát", style = VG.Title.copy(fontSize = 14.sp))
                Spacer(Modifier.height(6.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    AiLang.entries.forEach { l ->
                        val sel = l == lang
                        Text(
                            l.label, fontSize = 13.sp, fontWeight = FontWeight.Medium,
                            color = if (sel) Color.White else VG.Ink,
                            modifier = Modifier.bounceClick(scaleDown = 0.92f) { lang = l }.clip(CircleShape)
                                .background(if (sel) VG.Ink else VG.Cream).padding(horizontal = 12.dp, vertical = 8.dp),
                        )
                    }
                }
                Spacer(Modifier.height(10.dp))
                Text(
                    "Lời do AI nghe nên có thể sai, nhất là khi nhạc nền to. Bạn sửa lại được bằng nút ✎. " +
                        "Lần đầu cần mạng để tải mô hình (nên dùng Wi-Fi), sau đó chạy offline.",
                    style = VG.Small.copy(fontSize = 12.sp),
                )
            }
        },
        confirmButton = {
            TextButton(onClick = {
                prefs.edit().putString("ai_model", model.name).putString("ai_lang", lang.name).apply()
                onStart(model, lang)
            }) { Text(if (AiLyrics.isDownloaded(context, model)) "Bắt đầu" else "Tải & bắt đầu", color = VG.Peach, fontWeight = FontWeight.Bold) }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Hủy") } },
    )
}

@Composable
private fun Modifier.verticalScrollCompat(): Modifier =
    this.then(Modifier.verticalScroll(androidx.compose.foundation.rememberScrollState()))
