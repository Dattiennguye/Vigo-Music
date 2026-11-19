package vn.vigo.music.ui

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.ArrowBack
import androidx.compose.material.icons.rounded.ChevronRight
import androidx.compose.material.icons.rounded.Delete
import androidx.compose.material.icons.rounded.Edit
import androidx.compose.material.icons.rounded.Favorite
import androidx.compose.material.icons.rounded.MoreVert
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.QueueMusic
import androidx.compose.material.icons.rounded.Shuffle
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.animation.togetherWith
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import vn.vigo.music.data.Library
import vn.vigo.music.data.Track
import vn.vigo.music.player.PlayerHub

private const val FAV_ID = "__favorites__"

@Composable
fun PlaylistsScreen(bottomPadding: Dp, onOpenPlayer: () -> Unit, toast: (String) -> Unit) {
    var openId by rememberSaveable { mutableStateOf<String?>(null) }
    BackHandler(enabled = openId != null) { openId = null }
    androidx.compose.animation.AnimatedContent(
        openId,
        transitionSpec = {
            val forward = targetState != null
            val d = if (forward) 1 else -1
            (androidx.compose.animation.slideInHorizontally(androidx.compose.animation.core.spring(dampingRatio = 0.86f, stiffness = 320f)) { it * d } +
                androidx.compose.animation.fadeIn()) togetherWith
                (androidx.compose.animation.slideOutHorizontally(androidx.compose.animation.core.tween(260)) { -it * d / 3 } +
                    androidx.compose.animation.fadeOut(androidx.compose.animation.core.tween(200)))
        },
        label = "pl",
    ) { id ->
        if (id != null) PlaylistDetail(id, bottomPadding, onBack = { openId = null }, onOpenPlayer = onOpenPlayer, toast = toast)
        else PlaylistList(bottomPadding, onOpen = { openId = it })
    }
}

@Composable
private fun PlaylistList(bottomPadding: Dp, onOpen: (String) -> Unit) {
    val playlists by Library.playlists.collectAsState()
    val tracks by Library.tracks.collectAsState()
    val favs by Library.favorites.collectAsState()
    var creating by remember { mutableStateOf(false) }
    val byId = remember(tracks) { tracks.associateBy { it.id } }

    LazyColumn(
        Modifier.fillMaxSize(),
        contentPadding = PaddingValues(start = 18.dp, end = 18.dp, top = 8.dp, bottom = bottomPadding + 16.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        item {
            Row(Modifier.fillMaxWidth().padding(start = 6.dp), verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text("Playlist", style = VG.H2.copy(fontSize = 30.sp))
                    Text("${playlists.size} danh sách", style = VG.Small)
                }
                Box(
                    Modifier.size(46.dp).bounceClick { creating = true }.clip(CircleShape).background(VG.Ink),
                    contentAlignment = Alignment.Center,
                ) { Icon(Icons.Rounded.Add, "Tạo playlist", tint = Color.White) }
            }
        }
        item {
            PlaylistCard(
                name = "Yêu thích",
                count = favs.size,
                tracks = favs.mapNotNull { byId[it] }.take(3),
                icon = { Icon(Icons.Rounded.Favorite, null, tint = VG.Peach) },
                onClick = { onOpen(FAV_ID) },
                onAdd = null,
            )
        }
        items(playlists, key = { it.id }) { p ->
            Box(Modifier.animateItem()) {
            PlaylistCard(
                name = p.name,
                count = p.trackIds.size,
                tracks = p.trackIds.mapNotNull { byId[it] }.take(3),
                icon = { Icon(Icons.Rounded.QueueMusic, null, tint = VG.Ink) },
                onClick = { onOpen(p.id) },
                onAdd = { onOpen(p.id) },
            )
            }
        }
        if (playlists.isEmpty()) {
            item {
                Text(
                    "Bấm + để tạo playlist, hoặc chọn “Thêm vào playlist” trong menu ⋮ của bài hát.",
                    style = VG.Body, textAlign = TextAlign.Center, modifier = Modifier.fillMaxWidth().padding(24.dp),
                )
            }
        }
    }

    if (creating) {
        NameDialog("Playlist mới", "", onDismiss = { creating = false }) { name ->
            Library.createPlaylist(name); creating = false
        }
    }
}

@Composable
private fun PlaylistCard(
    name: String,
    count: Int,
    tracks: List<Track>,
    icon: @Composable () -> Unit,
    onClick: () -> Unit,
    onAdd: (() -> Unit)?,
) {
    Column(
        Modifier.fillMaxWidth()
            .bounceClick(scaleDown = 0.97f) { onClick() }
            .shadow(12.dp, RoundedCornerShape(26.dp), ambientColor = VG.Peach.copy(alpha = 0.35f), spotColor = VG.Peach.copy(alpha = 0.25f))
            .background(VG.Card, RoundedCornerShape(26.dp))
            .clip(RoundedCornerShape(26.dp))
            .padding(16.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            icon()
            Spacer(Modifier.width(10.dp))
            Text(name, style = VG.Title.copy(fontWeight = FontWeight.Bold), modifier = Modifier.weight(1f), maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text("$count bài", style = VG.Small)
            Icon(Icons.Rounded.ChevronRight, null, tint = VG.Ink)
        }
        Spacer(Modifier.height(14.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            for (i in 0 until 3) {
                val t = tracks.getOrNull(i)
                if (t != null) {
                    Artwork(t, Modifier.weight(1f).aspectRatio(1.2f), corner = 12.dp, maxPx = 200)
                } else {
                    Box(Modifier.weight(1f).aspectRatio(1.2f).clip(RoundedCornerShape(12.dp)).background(VG.Cream2.copy(alpha = 0.6f)))
                }
            }
            Box(
                Modifier.weight(1f).aspectRatio(1.2f).clip(RoundedCornerShape(12.dp)).background(VG.Cream2)
                    .clickable(enabled = onAdd != null) { onAdd?.invoke() },
                contentAlignment = Alignment.Center,
            ) { Icon(Icons.Rounded.Add, null, tint = if (onAdd != null) VG.Ink else VG.Gray, modifier = Modifier.size(26.dp)) }
        }
    }
}

@Composable
private fun PlaylistDetail(id: String, bottomPadding: Dp, onBack: () -> Unit, onOpenPlayer: () -> Unit, toast: (String) -> Unit) {
    val playlists by Library.playlists.collectAsState()
    val tracks by Library.tracks.collectAsState()
    val favs by Library.favorites.collectAsState()
    val ps by PlayerHub.state.collectAsState()
    val frame = rememberAudioFrame(ps.isPlaying)
    val isFav = id == FAV_ID
    val playlist = playlists.firstOrNull { it.id == id }
    if (!isFav && playlist == null) {
        androidx.compose.runtime.LaunchedEffect(Unit) { onBack() }
        return
    }
    val byId = remember(tracks) { tracks.associateBy { it.id } }
    val list = if (isFav) favs.mapNotNull { byId[it] } else playlist!!.trackIds.mapNotNull { byId[it] }
    val name = if (isFav) "Yêu thích" else playlist!!.name
    var menu by remember { mutableStateOf(false) }
    var renaming by remember { mutableStateOf(false) }
    var deleting by remember { mutableStateOf(false) }
    var adding by remember { mutableStateOf(false) }

    LazyColumn(
        Modifier.fillMaxSize(),
        contentPadding = PaddingValues(start = 18.dp, end = 18.dp, top = 4.dp, bottom = bottomPadding + 16.dp),
    ) {
        item {
            Row(verticalAlignment = Alignment.CenterVertically) {
                BounceIconButton(onClick = onBack) { Icon(Icons.Rounded.ArrowBack, "Quay lại", tint = VG.Ink) }
                Spacer(Modifier.weight(1f))
                if (!isFav) {
                    Box {
                        BounceIconButton(onClick = { menu = true }) { Icon(Icons.Rounded.MoreVert, "Thêm", tint = VG.Ink) }
                        DropdownMenu(menu, { menu = false }, containerColor = VG.Card) {
                            DropdownMenuItem(text = { Text("Thêm bài hát") }, leadingIcon = { Icon(Icons.Rounded.Add, null) }, onClick = { menu = false; adding = true })
                            DropdownMenuItem(text = { Text("Đổi tên") }, leadingIcon = { Icon(Icons.Rounded.Edit, null) }, onClick = { menu = false; renaming = true })
                            DropdownMenuItem(
                                text = { Text("Xóa playlist", color = Color(0xFFC0392B)) },
                                leadingIcon = { Icon(Icons.Rounded.Delete, null, tint = Color(0xFFC0392B)) },
                                onClick = { menu = false; deleting = true },
                            )
                        }
                    }
                }
            }
            val art = rememberArt(list.firstOrNull(), 720)
            SleeveHero(
                keySeed = id, label = "${list.size} bài hát", title = name,
                spinning = ps.isPlaying && ps.currentId in list.map { it.id },
                art = art, labelArt = art, showHeart = isFav,
                modifier = Modifier.padding(horizontal = 14.dp),
            )
            Spacer(Modifier.height(16.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                BigButton("Phát", Icons.Rounded.PlayArrow, dark = true, onClick = {
                    if (list.isNotEmpty()) { PlayerHub.playIds(list.map { it.id }); onOpenPlayer() }
                }, modifier = Modifier.weight(1f))
                BigButton("Ngẫu nhiên", Icons.Rounded.Shuffle, dark = false, onClick = {
                    if (list.isNotEmpty()) { PlayerHub.playIds(list.map { it.id }.shuffled()); onOpenPlayer() }
                }, modifier = Modifier.weight(1f))
            }
            Spacer(Modifier.height(10.dp))
            if (list.isEmpty()) {
                Text(
                    if (isFav) "Bấm ♡ ở bài hát để thêm vào đây." else "Playlist trống. Bấm ⋮ → Thêm bài hát.",
                    style = VG.Body, textAlign = TextAlign.Center, modifier = Modifier.fillMaxWidth().padding(24.dp),
                )
            }
        }
        items(list, key = { it.id }) { t ->
            Box(Modifier.animateItem()) {
            TrackItem(
                track = t,
                isCurrent = t.id == ps.currentId,
                isFav = t.id in favs,
                onClick = { PlayerHub.playIds(list.map { it.id }, list.indexOf(t)) },
                toast = toast,
                removeFromPlaylistId = if (isFav) null else id,
                frame = frame,
            )
            }
        }
    }

    if (renaming) {
        NameDialog("Đổi tên playlist", name, onDismiss = { renaming = false }) { Library.renamePlaylist(id, it); renaming = false }
    }
    if (deleting) {
        AlertDialog(
            onDismissRequest = { deleting = false },
            containerColor = VG.Card,
            title = { Text("Xóa playlist?") },
            text = { Text("Chỉ xóa danh sách “$name”, các bài hát vẫn còn trong thư viện.") },
            confirmButton = { TextButton(onClick = { deleting = false; Library.deletePlaylist(id); onBack() }) { Text("Xóa", color = Color(0xFFC0392B)) } },
            dismissButton = { TextButton(onClick = { deleting = false }) { Text("Hủy") } },
        )
    }
    if (adding) {
        AddTracksDialog(
            existing = list.map { it.id }.toSet(),
            onDismiss = { adding = false },
            onAdd = { ids -> ids.forEach { Library.addToPlaylist(id, it) }; adding = false; toast("Đã thêm ${ids.size} bài") },
        )
    }
}

@Composable
fun NameDialog(title: String, initial: String, onDismiss: () -> Unit, onSave: (String) -> Unit) {
    var name by remember { mutableStateOf(initial) }
    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = VG.Card,
        title = { Text(title) },
        text = { OutlinedTextField(name, { name = it }, label = { Text("Tên") }, singleLine = true) },
        confirmButton = { TextButton(onClick = { onSave(name) }, enabled = name.isNotBlank()) { Text("Lưu") } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Hủy") } },
    )
}

@Composable
private fun AddTracksDialog(existing: Set<String>, onDismiss: () -> Unit, onAdd: (List<String>) -> Unit) {
    val tracks by Library.tracks.collectAsState()
    var selected by remember { mutableStateOf(setOf<String>()) }
    var q by remember { mutableStateOf("") }
    val candidates = tracks.filter { it.id !in existing }
        .filter { q.isBlank() || foldVi(it.title).contains(foldVi(q)) || foldVi(it.artist).contains(foldVi(q)) }
    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = VG.Card,
        title = { Text("Thêm bài hát") },
        text = {
            Column {
                SearchField(q, { q = it })
                Spacer(Modifier.height(8.dp))
                if (candidates.isEmpty()) Text("Không còn bài nào để thêm.", style = VG.Body)
                LazyColumn(Modifier.height(340.dp)) {
                    items(candidates, key = { it.id }) { t ->
                        val sel = t.id in selected
                        Row(
                            Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp))
                                .background(if (sel) VG.PeachPale else Color.Transparent)
                                .clickable { selected = if (sel) selected - t.id else selected + t.id }
                                .padding(8.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Artwork(t, Modifier.size(40.dp), corner = 10.dp, maxPx = 120)
                            Spacer(Modifier.width(10.dp))
                            Column(Modifier.weight(1f)) {
                                Text(t.title, style = VG.Title.copy(fontSize = 14.sp), maxLines = 1, overflow = TextOverflow.Ellipsis)
                                Text(t.artist, style = VG.Small, maxLines = 1)
                            }
                            if (sel) Text("✓", color = VG.Peach, fontWeight = FontWeight.Bold)
                        }
                    }
                }
            }
        },
        confirmButton = { TextButton(onClick = { onAdd(selected.toList()) }, enabled = selected.isNotEmpty()) { Text("Thêm ${selected.size}") } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Hủy") } },
    )
}
