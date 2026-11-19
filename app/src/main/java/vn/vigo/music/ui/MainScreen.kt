package vn.vigo.music.ui

import android.Manifest
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.Crossfade
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Language
import androidx.compose.material.icons.rounded.LibraryMusic
import androidx.compose.material.icons.rounded.QueueMusic
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.Snackbar
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.launch
import vn.vigo.music.data.Importer
import vn.vigo.music.data.Library
import vn.vigo.music.data.NotAudioException
import vn.vigo.music.player.PlayerHub

@Composable
fun MainScreen(incoming: MutableStateFlow<List<Uri>>) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val density = LocalDensity.current
    val snack = remember { SnackbarHostState() }
    val toast: (String) -> Unit = { msg -> scope.launch { snack.currentSnackbarData?.dismiss(); snack.showSnackbar(msg) } }

    var tab by rememberSaveable { mutableIntStateOf(0) }
    var playerOpen by rememberSaveable { mutableStateOf(false) }
    var overlayPx by remember { mutableIntStateOf(0) }
    var importing by remember { mutableStateOf<Pair<Int, Int>?>(null) }
    val web = remember { WebHolder(context) }

    val ps by PlayerHub.state.collectAsState()
    val tracks by Library.tracks.collectAsState()
    val current = ps.currentId?.let { id -> tracks.firstOrNull { it.id == id } }

    // Xin quyền thông báo (Android 13+) để hiện điều khiển phát nhạc
    val notifLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { }
    LaunchedEffect(Unit) {
        if (Build.VERSION.SDK_INT >= 33 &&
            ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
        ) notifLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
    }

    LaunchedEffect(ps.error) {
        ps.error?.let { toast(it); PlayerHub.clearError() }
    }

    suspend fun importAll(uris: List<Uri>, playAfter: Boolean) {
        if (uris.isEmpty()) return
        var ok = 0
        var fail = 0
        val ids = ArrayList<String>()
        uris.forEachIndexed { i, u ->
            importing = (i + 1) to uris.size
            try {
                ids.add(Importer.importUri(context, u).id); ok++
            } catch (e: NotAudioException) { fail++ } catch (e: Exception) { fail++ }
        }
        importing = null
        toast(
            when {
                fail == 0 -> "Đã thêm $ok bài vào thư viện"
                ok == 0 -> "Không nhập được file nào (không phải file nhạc?)"
                else -> "Đã thêm $ok bài, $fail file lỗi"
            }
        )
        if (playAfter && ids.isNotEmpty()) PlayerHub.playIds(ids)
    }

    val picker = rememberLauncherForActivityResult(ActivityResultContracts.OpenMultipleDocuments()) { uris ->
        scope.launch { importAll(uris, playAfter = false) }
    }
    val onImport = { picker.launch(arrayOf("audio/*", "application/ogg")) }

    val pendingIncoming by incoming.collectAsState()
    LaunchedEffect(pendingIncoming) {
        if (pendingIncoming.isNotEmpty()) {
            val list = pendingIncoming
            incoming.value = emptyList()
            tab = 0
            importAll(list, playAfter = true)
        }
    }

    val bottomPad = with(density) { overlayPx.toDp() }

    Box(Modifier.fillMaxSize().background(VG.Cream)) {
        Box(Modifier.fillMaxSize().statusBarsPadding()) {
            Crossfade(tab, animationSpec = tween(220), label = "tab") { t ->
                when (t) {
                    0 -> LibraryScreen(bottomPad, onImport = onImport, onOpenWeb = { tab = 1 }, onOpenPlayer = { playerOpen = true }, toast = toast)
                    1 -> BrowserScreen(web, bottomPad, toast)
                    else -> PlaylistsScreen(bottomPad, onOpenPlayer = { playerOpen = true }, toast = toast)
                }
            }
        }

        // Mini player + thanh điều hướng
        Column(
            Modifier.align(Alignment.BottomCenter).fillMaxWidth()
                .background(androidx.compose.ui.graphics.Brush.verticalGradient(listOf(Color.Transparent, VG.Cream.copy(alpha = 0.92f), VG.Cream)))
                .navigationBarsPadding()
                .padding(horizontal = 14.dp)
                .padding(top = 14.dp, bottom = 8.dp)
                .onSizeChanged { overlayPx = it.height },
        ) {
            importing?.let { (i, n) ->
                Row(
                    Modifier.fillMaxWidth().padding(bottom = 8.dp).clip(RoundedCornerShape(16.dp)).background(VG.Ink).padding(12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    CircularProgressIndicator(Modifier.size(18.dp), color = VG.Peach, strokeWidth = 2.dp)
                    Spacer(Modifier.width(10.dp))
                    Text("Đang nhập $i/$n…", color = Color.White, fontSize = 14.sp)
                }
            }
            AnimatedVisibility(current != null, enter = fadeIn() + slideInVertically { it / 2 }, exit = fadeOut()) {
                current?.let { t ->
                    MiniPlayer(
                        t, ps.isPlaying, ps.positionMs, ps.durationMs,
                        onToggle = { PlayerHub.togglePlay() },
                        onOpen = { playerOpen = true },
                        onSeek = { f -> if (ps.durationMs > 0) PlayerHub.seekTo((f * ps.durationMs).toLong()) },
                        modifier = Modifier.padding(bottom = 10.dp),
                    )
                }
            }
            NavBar(tab) { tab = it }
        }

        SnackbarHost(
            snack,
            Modifier.align(Alignment.BottomCenter).padding(bottom = bottomPad + 4.dp).padding(horizontal = 16.dp),
        ) { data ->
            Snackbar(data, containerColor = VG.Ink, contentColor = Color.White, shape = RoundedCornerShape(16.dp))
        }

        AnimatedVisibility(
            playerOpen && current != null,
            enter = slideInVertically(tween(320)) { it },
            exit = slideOutVertically(tween(260)) { it },
        ) {
            PlayerScreen(onClose = { playerOpen = false })
        }
    }
}

@Composable
private fun NavBar(selected: Int, onSelect: (Int) -> Unit) {
    val items = listOf<Pair<String, ImageVector>>(
        "Thư viện" to Icons.Rounded.LibraryMusic,
        "Tìm & tải" to Icons.Rounded.Language,
        "Playlist" to Icons.Rounded.QueueMusic,
    )
    Row(
        Modifier.fillMaxWidth().height(62.dp)
            .shadow(14.dp, RoundedCornerShape(31.dp), ambientColor = VG.Peach.copy(alpha = 0.3f), spotColor = VG.Peach.copy(alpha = 0.25f))
            .background(VG.Card, RoundedCornerShape(31.dp))
            .padding(6.dp),
        horizontalArrangement = Arrangement.spacedBy(6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        items.forEachIndexed { i, (label, icon) ->
            val sel = i == selected
            Row(
                Modifier.weight(1f).height(50.dp).clip(RoundedCornerShape(25.dp))
                    .background(if (sel) VG.Ink else Color.Transparent)
                    .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null) { onSelect(i) },
                horizontalArrangement = Arrangement.Center,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Icon(icon, label, tint = if (sel) Color.White else VG.Gray, modifier = Modifier.size(22.dp))
                if (sel) {
                    Spacer(Modifier.width(6.dp))
                    Text(label, color = Color.White, fontSize = 13.sp, fontWeight = FontWeight.SemiBold, maxLines = 1)
                }
            }
        }
    }
}
