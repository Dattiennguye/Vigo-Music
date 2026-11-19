package vn.vigo.music.ui

import android.annotation.SuppressLint
import android.content.Context
import android.graphics.Bitmap
import android.view.ViewGroup
import android.webkit.CookieManager
import android.webkit.WebChromeClient
import android.webkit.WebResourceRequest
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.ArrowBack
import androidx.compose.material.icons.rounded.ArrowForward
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.ContentPaste
import androidx.compose.material.icons.rounded.Download
import androidx.compose.material.icons.rounded.ErrorOutline
import androidx.compose.material.icons.rounded.Home
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import vn.vigo.music.data.DlState
import vn.vigo.music.data.Downloader
import vn.vigo.music.data.Importer
import vn.vigo.music.player.PlayerHub
import java.net.URLEncoder

/** Giữ một WebView duy nhất để chuyển tab không mất trang đang xem. */
class WebHolder(context: Context) {
    var url by mutableStateOf("")
    var title by mutableStateOf("")
    var progress by mutableStateOf(100)
    var canBack by mutableStateOf(false)
    var canForward by mutableStateOf(false)
    var started by mutableStateOf(false)
    var pendingAudio by mutableStateOf<PendingDl?>(null)
    var notAudio by mutableStateOf(false)

    data class PendingDl(val url: String, val ua: String?, val cd: String?, val mime: String?)

    @SuppressLint("SetJavaScriptEnabled")
    val webView: WebView = WebView(context).apply {
        layoutParams = ViewGroup.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT)
        settings.javaScriptEnabled = true
        settings.domStorageEnabled = true
        settings.loadWithOverviewMode = true
        settings.useWideViewPort = true
        settings.builtInZoomControls = true
        settings.displayZoomControls = false
        settings.mediaPlaybackRequiresUserGesture = true
        settings.setSupportMultipleWindows(false) // link target=_blank mở ngay trong trang này
        CookieManager.getInstance().setAcceptCookie(true)
        CookieManager.getInstance().setAcceptThirdPartyCookies(this, true)

        webViewClient = object : WebViewClient() {
            override fun shouldOverrideUrlLoading(view: WebView, request: WebResourceRequest): Boolean {
                val u = request.url.toString()
                val scheme = request.url.scheme?.lowercase()
                if (scheme != "http" && scheme != "https") return true // chặn intent://, market:// ...
                if (Downloader.isLikelyAudioUrl(u)) {
                    this@WebHolder.pendingAudio = PendingDl(u, settings.userAgentString, null, null)
                    return true
                }
                return false
            }

            override fun onPageStarted(view: WebView, u: String, favicon: Bitmap?) {
                this@WebHolder.url = u; sync(view)
            }

            override fun onPageFinished(view: WebView, u: String) {
                this@WebHolder.url = u; sync(view)
            }

            override fun doUpdateVisitedHistory(view: WebView, u: String, isReload: Boolean) {
                this@WebHolder.url = u; sync(view)
            }
        }
        webChromeClient = object : WebChromeClient() {
            override fun onProgressChanged(view: WebView, newProgress: Int) { this@WebHolder.progress = newProgress }
            override fun onReceivedTitle(view: WebView, t: String?) { this@WebHolder.title = t.orEmpty() }
        }
        setDownloadListener { u, ua, cd, mime, _ ->
            val m = mime?.lowercase().orEmpty()
            val name = android.webkit.URLUtil.guessFileName(u, cd, mime)
            val ok = m.startsWith("audio/") || m.startsWith("video/") || m == "application/octet-stream" ||
                m == "application/ogg" || m.isBlank() || Importer.isAudioExt(name.substringAfterLast('.', ""))
            if (ok) this@WebHolder.pendingAudio = PendingDl(u, ua, cd, mime) else this@WebHolder.notAudio = true
        }
        setOnLongClickListener {
            val r = hitTestResult
            val link = r.extra
            if ((r.type == WebView.HitTestResult.SRC_ANCHOR_TYPE || r.type == WebView.HitTestResult.SRC_IMAGE_ANCHOR_TYPE) &&
                Downloader.isLikelyAudioUrl(link)
            ) {
                this@WebHolder.pendingAudio = PendingDl(link!!, settings.userAgentString, null, null)
                true
            } else false
        }
    }

    private fun sync(v: WebView) {
        canBack = v.canGoBack(); canForward = v.canGoForward()
    }

    fun open(input: String, addMp3: Boolean) {
        val q = input.trim()
        if (q.isEmpty()) return
        val looksUrl = !q.contains(' ') && (q.startsWith("http://") || q.startsWith("https://") || Regex("^[\\w-]+(\\.[\\w-]+)+(/.*)?$").matches(q))
        val target = when {
            looksUrl && q.startsWith("http") -> q
            looksUrl -> "https://$q"
            else -> "https://www.google.com/search?q=" + URLEncoder.encode(if (addMp3 && !q.contains("mp3", true)) "$q mp3" else q, "UTF-8")
        }
        started = true
        webView.loadUrl(target)
    }

    fun home() {
        started = false
        webView.stopLoading()
        webView.loadUrl("about:blank")
        webView.clearHistory()
        url = ""; title = ""
    }
}

@Composable
fun BrowserScreen(holder: WebHolder, bottomPadding: Dp, toast: (String) -> Unit) {
    val tasks by Downloader.tasks.collectAsState()
    val focus = LocalFocusManager.current
    var input by remember { mutableStateOf("") }
    var addMp3 by remember { mutableStateOf(true) }
    var showDownloads by remember { mutableStateOf(false) }
    var pasting by remember { mutableStateOf(false) }
    val running = tasks.count { it.state == DlState.Running }

    BackHandler(enabled = holder.started) {
        if (holder.canBack) holder.webView.goBack() else holder.home()
    }

    // Báo khi tải xong / lỗi
    val notified = remember { mutableSetOf<String>() }
    LaunchedEffect(tasks) {
        tasks.forEach { t ->
            if (t.state != DlState.Running && notified.add(t.id)) {
                if (t.state == DlState.Done) toast("Đã lưu “${t.name}” vào thư viện")
                else if (t.message != "Đã hủy") toast("Tải lỗi: ${t.message}")
            }
        }
    }

    Column(Modifier.fillMaxSize()) {
        // Thanh tìm kiếm
        Column(Modifier.padding(horizontal = 16.dp).padding(top = 6.dp)) {
            Row(
                Modifier.fillMaxWidth()
                    .shadow(10.dp, RoundedCornerShape(22.dp), ambientColor = VG.Peach.copy(alpha = 0.3f), spotColor = VG.Peach.copy(alpha = 0.25f))
                    .background(VG.Card, RoundedCornerShape(22.dp))
                    .padding(start = 8.dp, end = 4.dp, top = 4.dp, bottom = 4.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Box(Modifier.size(34.dp).clip(CircleShape).background(VG.PeachPale), contentAlignment = Alignment.Center) {
                    Text("G", color = VG.Peach, fontWeight = FontWeight.Bold, fontSize = 17.sp)
                }
                Spacer(Modifier.width(10.dp))
                Box(Modifier.weight(1f).padding(vertical = 10.dp)) {
                    val display = if (input.isEmpty() && holder.started) holder.title.ifBlank { holder.url } else ""
                    if (input.isEmpty()) Text(display.ifBlank { "Tìm bài hát trên Google…" }, style = VG.Body, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    BasicTextField(
                        input, { input = it }, singleLine = true,
                        textStyle = VG.Title.copy(fontWeight = FontWeight.Normal, fontSize = 15.sp),
                        cursorBrush = SolidColor(VG.Peach),
                        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search, keyboardType = KeyboardType.Uri),
                        keyboardActions = KeyboardActions(onSearch = { holder.open(input, addMp3); input = ""; focus.clearFocus() }),
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
                if (input.isNotEmpty()) {
                    IconButton(onClick = { input = "" }) { Icon(Icons.Rounded.Close, "Xóa", tint = VG.Gray) }
                }
                Box(
                    Modifier.size(40.dp).clip(CircleShape).background(VG.Ink)
                        .clickable { holder.open(input, addMp3); input = ""; focus.clearFocus() },
                    contentAlignment = Alignment.Center,
                ) { Icon(Icons.Rounded.Search, "Tìm", tint = Color.White, modifier = Modifier.size(20.dp)) }
            }
            Spacer(Modifier.height(8.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                Chip("+ mp3", addMp3) { addMp3 = !addMp3 }
                Spacer(Modifier.width(8.dp))
                Chip("Dán link", false, Icons.Rounded.ContentPaste) { pasting = true }
                Spacer(Modifier.weight(1f))
                if (holder.started) {
                    SmallIcon(Icons.Rounded.ArrowBack, "Lùi", holder.canBack) { holder.webView.goBack() }
                    SmallIcon(Icons.Rounded.ArrowForward, "Tới", holder.canForward) { holder.webView.goForward() }
                    SmallIcon(Icons.Rounded.Refresh, "Tải lại", true) { holder.webView.reload() }
                    SmallIcon(Icons.Rounded.Home, "Trang đầu", true) { holder.home() }
                }
                Box {
                    SmallIcon(Icons.Rounded.Download, "Tải xuống", true) { showDownloads = !showDownloads }
                    if (running > 0 || tasks.isNotEmpty()) {
                        Box(
                            Modifier.align(Alignment.TopEnd).offset(x = (-2).dp, y = 2.dp).size(16.dp).clip(CircleShape)
                                .background(if (running > 0) VG.Peach else VG.Ink),
                            contentAlignment = Alignment.Center,
                        ) { Text("${if (running > 0) running else tasks.size}", color = Color.White, fontSize = 10.sp) }
                    }
                }
            }
            if (holder.started && holder.progress < 100) {
                LinearProgressIndicator(
                    progress = { holder.progress / 100f },
                    modifier = Modifier.fillMaxWidth().height(2.dp).padding(top = 0.dp),
                    color = VG.Peach, trackColor = Color.Transparent,
                )
            }
        }

        Box(Modifier.weight(1f).fillMaxWidth().padding(top = 8.dp)) {
            if (holder.started) {
                AndroidView(
                    factory = { holder.webView.also { (it.parent as? ViewGroup)?.removeView(it) } },
                    modifier = Modifier.fillMaxSize().padding(bottom = bottomPadding),
                )
            } else {
                StartPanel(bottomPadding) { q -> holder.open(q, addMp3) }
            }

            AnimatedVisibility(
                showDownloads,
                Modifier.align(Alignment.BottomCenter),
                enter = slideInVertically { it }, exit = slideOutVertically { it },
            ) {
                DownloadsPanel(bottomPadding, onClose = { showDownloads = false })
            }
        }
    }

    holder.pendingAudio?.let { p ->
        val name = remember(p) {
            val n = android.webkit.URLUtil.guessFileName(p.url, p.cd, p.mime)
            try { java.net.URLDecoder.decode(n, "UTF-8") } catch (_: Exception) { n }
        }
        AlertDialog(
            onDismissRequest = { holder.pendingAudio = null },
            containerColor = VG.Card,
            icon = { Icon(Icons.Rounded.Download, null, tint = VG.Peach) },
            title = { Text("Tải về thư viện?") },
            text = { Text(name, style = VG.Title.copy(fontWeight = FontWeight.Normal)) },
            confirmButton = {
                TextButton(onClick = {
                    Downloader.start(p.url, p.ua, p.cd, p.mime)
                    holder.pendingAudio = null
                    showDownloads = true
                }) { Text("Tải về") }
            },
            dismissButton = { TextButton(onClick = { holder.pendingAudio = null }) { Text("Hủy") } },
        )
    }

    if (holder.notAudio) {
        AlertDialog(
            onDismissRequest = { holder.notAudio = false },
            containerColor = VG.Card,
            title = { Text("Không phải file nhạc") },
            text = { Text("Vigo Music chỉ lưu file âm thanh (mp3, m4a, flac, wav, ogg…).") },
            confirmButton = { TextButton(onClick = { holder.notAudio = false }) { Text("OK") } },
        )
    }

    if (pasting) {
        val clip = LocalClipboardManager.current
        var link by remember { mutableStateOf(clip.getText()?.text?.takeIf { it.startsWith("http") }.orEmpty()) }
        AlertDialog(
            onDismissRequest = { pasting = false },
            containerColor = VG.Card,
            title = { Text("Tải từ link") },
            text = {
                Column {
                    Text("Dán link trực tiếp tới file nhạc (thường kết thúc bằng .mp3, .m4a…).", style = VG.Small)
                    Spacer(Modifier.height(10.dp))
                    OutlinedTextField(link, { link = it }, singleLine = true, label = { Text("https://…") })
                }
            },
            confirmButton = {
                TextButton(
                    enabled = link.trim().startsWith("http"),
                    onClick = {
                        Downloader.start(link.trim(), holder.webView.settings.userAgentString, null, null)
                        pasting = false; showDownloads = true
                    },
                ) { Text("Tải về") }
            },
            dismissButton = { TextButton(onClick = { pasting = false }) { Text("Hủy") } },
        )
    }
}

@Composable
private fun Chip(text: String, selected: Boolean, icon: androidx.compose.ui.graphics.vector.ImageVector? = null, onClick: () -> Unit) {
    Row(
        Modifier.clip(CircleShape).background(if (selected) VG.Ink else VG.Card).clickable { onClick() }
            .padding(horizontal = 12.dp, vertical = 7.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (icon != null) {
            Icon(icon, null, tint = if (selected) Color.White else VG.Ink, modifier = Modifier.size(16.dp))
            Spacer(Modifier.width(6.dp))
        }
        Text(text, color = if (selected) Color.White else VG.Ink, fontSize = 13.sp, fontWeight = FontWeight.Medium)
    }
}

@Composable
private fun SmallIcon(icon: androidx.compose.ui.graphics.vector.ImageVector, desc: String, enabled: Boolean, onClick: () -> Unit) {
    IconButton(onClick = onClick, enabled = enabled, modifier = Modifier.size(38.dp)) {
        Icon(icon, desc, tint = if (enabled) VG.Ink else VG.Line, modifier = Modifier.size(22.dp))
    }
}

@Composable
private fun StartPanel(bottomPadding: Dp, onSearch: (String) -> Unit) {
    Column(
        Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = 22.dp).padding(bottom = bottomPadding + 16.dp),
    ) {
        Spacer(Modifier.height(10.dp))
        Text("Tìm & tải nhạc", style = VG.H2.copy(fontSize = 28.sp))
        Spacer(Modifier.height(6.dp))
        Text("Tìm bằng Google ngay trong app. Khi gặp link file nhạc (.mp3, .m4a…), bấm vào hoặc nhấn giữ link để lưu thẳng vào thư viện.", style = VG.Body)
        Spacer(Modifier.height(20.dp))
        val steps = listOf(
            "1" to "Gõ tên bài hát rồi bấm tìm (bật “+ mp3” để ưu tiên file nhạc).",
            "2" to "Mở trang có nút tải, bấm vào link file nhạc.",
            "3" to "Chọn “Tải về” — bài hát xuất hiện ở Thư viện, nghe được cả khi offline.",
        )
        steps.forEach { (n, s) ->
            Row(Modifier.padding(vertical = 7.dp), verticalAlignment = Alignment.Top) {
                Box(Modifier.size(28.dp).clip(CircleShape).background(VG.PeachPale), contentAlignment = Alignment.Center) {
                    Text(n, color = VG.Peach, fontWeight = FontWeight.Bold)
                }
                Spacer(Modifier.width(12.dp))
                Text(s, style = VG.Body.copy(color = VG.Ink.copy(alpha = 0.8f)), modifier = Modifier.padding(top = 3.dp))
            }
        }
        Spacer(Modifier.height(18.dp))
        Text("Gợi ý tìm nhạc miễn phí bản quyền", style = VG.Title)
        Spacer(Modifier.height(10.dp))
        listOf("nhạc không bản quyền mp3", "lofi chill free download mp3", "piano relaxing royalty free mp3", "archive.org").forEach { q ->
            Row(
                Modifier.fillMaxWidth().padding(vertical = 4.dp).clip(RoundedCornerShape(16.dp)).background(VG.Card)
                    .clickable { onSearch(q) }.padding(14.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Icon(Icons.Rounded.Search, null, tint = VG.Gray, modifier = Modifier.size(20.dp))
                Spacer(Modifier.width(10.dp))
                Text(q, style = VG.Title.copy(fontWeight = FontWeight.Normal, fontSize = 15.sp))
            }
        }
        Spacer(Modifier.height(14.dp))
        Text(
            "Lưu ý: app tải được file nhạc trực tiếp. Nhạc từ YouTube, Spotify, Zing MP3… được bảo vệ nên không tải được — hãy dùng nguồn bạn có quyền tải.",
            style = VG.Small,
        )
    }
}

@Composable
private fun DownloadsPanel(bottomPadding: Dp, onClose: () -> Unit) {
    val tasks by Downloader.tasks.collectAsState()
    Column(
        Modifier.fillMaxWidth().padding(horizontal = 12.dp).padding(bottom = bottomPadding + 8.dp)
            .shadow(24.dp, RoundedCornerShape(26.dp))
            .background(VG.Card, RoundedCornerShape(26.dp))
            .padding(16.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text("Tải xuống", style = VG.Title.copy(fontWeight = FontWeight.Bold), modifier = Modifier.weight(1f))
            if (tasks.any { it.state != DlState.Running }) {
                TextButton(onClick = { Downloader.clearFinished() }) { Text("Dọn", color = VG.Peach) }
            }
            IconButton(onClick = onClose, modifier = Modifier.size(36.dp)) { Icon(Icons.Rounded.Close, "Đóng", tint = VG.Gray) }
        }
        if (tasks.isEmpty()) {
            Text("Chưa có lượt tải nào.", style = VG.Body, textAlign = TextAlign.Center, modifier = Modifier.fillMaxWidth().padding(20.dp))
        }
        LazyColumn(Modifier.heightIn(max = 300.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            items(tasks, key = { it.id }) { t ->
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(Modifier.size(40.dp).clip(RoundedCornerShape(12.dp)).background(VG.PeachPale), contentAlignment = Alignment.Center) {
                        when (t.state) {
                            DlState.Running -> Icon(Icons.Rounded.Download, null, tint = VG.Peach)
                            DlState.Done -> Icon(Icons.Rounded.CheckCircle, null, tint = VG.Peach)
                            DlState.Failed -> Icon(Icons.Rounded.ErrorOutline, null, tint = Color(0xFFC0392B))
                        }
                    }
                    Spacer(Modifier.width(12.dp))
                    Column(Modifier.weight(1f)) {
                        Text(t.name, style = VG.Title.copy(fontSize = 14.sp), maxLines = 1, overflow = TextOverflow.Ellipsis)
                        Spacer(Modifier.height(4.dp))
                        when (t.state) {
                            DlState.Running -> {
                                if (t.progress >= 0f) {
                                    LinearProgressIndicator(progress = { t.progress }, Modifier.fillMaxWidth().height(5.dp).clip(CircleShape), color = VG.Peach, trackColor = VG.PeachPale)
                                } else {
                                    LinearProgressIndicator(Modifier.fillMaxWidth().height(5.dp).clip(CircleShape), color = VG.Peach, trackColor = VG.PeachPale)
                                }
                                Text(sizeText(t.received, t.total), style = VG.Small.copy(fontSize = 11.sp))
                            }
                            else -> Text(t.message, style = VG.Small.copy(fontSize = 12.sp), maxLines = 2)
                        }
                    }
                    when (t.state) {
                        DlState.Running -> IconButton(onClick = { Downloader.cancel(t.id) }) { Icon(Icons.Rounded.Close, "Hủy", tint = VG.Gray) }
                        DlState.Done -> IconButton(onClick = { t.trackId?.let { PlayerHub.playIds(listOf(it)) } }) { Icon(Icons.Rounded.PlayArrow, "Phát", tint = VG.Ink) }
                        DlState.Failed -> IconButton(onClick = { Downloader.dismiss(t.id) }) { Icon(Icons.Rounded.Close, "Bỏ", tint = VG.Gray) }
                    }
                }
            }
        }
    }
}

private fun sizeText(received: Long, total: Long): String {
    fun mb(b: Long) = "%.1f MB".format(b / 1048576.0)
    return if (total > 0) "${mb(received)} / ${mb(total)}" else mb(received)
}
