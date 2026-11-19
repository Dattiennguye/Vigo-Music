package vn.vigo.music.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.Delete
import androidx.compose.material.icons.rounded.Edit
import androidx.compose.material.icons.rounded.Favorite
import androidx.compose.material.icons.rounded.FavoriteBorder
import androidx.compose.material.icons.rounded.GraphicEq
import androidx.compose.material.icons.rounded.MoreVert
import androidx.compose.material.icons.rounded.PlaylistAdd
import androidx.compose.material.icons.rounded.RemoveCircleOutline
import androidx.compose.material.icons.rounded.QueueMusic
import androidx.compose.material.icons.rounded.SkipNext
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import vn.vigo.music.data.Library
import vn.vigo.music.data.Track
import vn.vigo.music.player.PlayerHub

/** Một dòng bài hát kèm menu thao tác (phát tiếp, hàng chờ, playlist, đổi tên, xóa). */
@Composable
fun TrackItem(
    track: Track,
    isCurrent: Boolean,
    isFav: Boolean,
    onClick: () -> Unit,
    toast: (String) -> Unit,
    removeFromPlaylistId: String? = null,
) {
    var menu by remember { mutableStateOf(false) }
    var renaming by remember { mutableStateOf(false) }
    var deleting by remember { mutableStateOf(false) }
    var picking by remember { mutableStateOf(false) }

    Row(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(18.dp))
            .background(if (isCurrent) VG.PeachPale.copy(alpha = 0.6f) else Color.Transparent)
            .clickable { onClick() }
            .padding(horizontal = 10.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(contentAlignment = Alignment.Center) {
            Artwork(track, Modifier.size(54.dp), corner = 14.dp, maxPx = 160)
            if (isCurrent) {
                Box(Modifier.size(54.dp).clip(RoundedCornerShape(14.dp)).background(Color.Black.copy(alpha = 0.35f)), contentAlignment = Alignment.Center) {
                    Icon(Icons.Rounded.GraphicEq, null, tint = Color.White)
                }
            }
        }
        Spacer(Modifier.width(14.dp))
        Column(Modifier.weight(1f)) {
            Text(track.title, style = VG.Title.copy(color = if (isCurrent) VG.Peach else VG.Ink), maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text(
                buildString {
                    append(track.artist)
                    if (track.durationMs > 0) append(" · ").append(formatTime(track.durationMs))
                },
                style = VG.Small, maxLines = 1, overflow = TextOverflow.Ellipsis,
            )
        }
        IconButton(onClick = { Library.toggleFavorite(track.id) }) {
            Icon(
                if (isFav) Icons.Rounded.Favorite else Icons.Rounded.FavoriteBorder,
                if (isFav) "Bỏ yêu thích" else "Yêu thích",
                tint = if (isFav) VG.Peach else VG.Gray,
            )
        }
        Box {
            IconButton(onClick = { menu = true }) { Icon(Icons.Rounded.MoreVert, "Thêm", tint = VG.Gray) }
            DropdownMenu(menu, onDismissRequest = { menu = false }, containerColor = VG.Card) {
                DropdownMenuItem(
                    text = { Text("Phát tiếp theo") }, leadingIcon = { Icon(Icons.Rounded.SkipNext, null) },
                    onClick = { menu = false; PlayerHub.playNext(track.id); toast("Sẽ phát tiếp: ${track.title}") },
                )
                DropdownMenuItem(
                    text = { Text("Thêm vào hàng chờ") }, leadingIcon = { Icon(Icons.Rounded.QueueMusic, null) },
                    onClick = { menu = false; PlayerHub.addToQueue(track.id); toast("Đã thêm vào hàng chờ") },
                )
                DropdownMenuItem(
                    text = { Text("Thêm vào playlist") }, leadingIcon = { Icon(Icons.Rounded.PlaylistAdd, null) },
                    onClick = { menu = false; picking = true },
                )
                if (removeFromPlaylistId != null) {
                    DropdownMenuItem(
                        text = { Text("Bỏ khỏi playlist này") }, leadingIcon = { Icon(Icons.Rounded.RemoveCircleOutline, null) },
                        onClick = { menu = false; Library.removeFromPlaylist(removeFromPlaylistId, track.id) },
                    )
                }
                DropdownMenuItem(
                    text = { Text("Đổi tên") }, leadingIcon = { Icon(Icons.Rounded.Edit, null) },
                    onClick = { menu = false; renaming = true },
                )
                DropdownMenuItem(
                    text = { Text("Xóa khỏi máy", color = Color(0xFFC0392B)) },
                    leadingIcon = { Icon(Icons.Rounded.Delete, null, tint = Color(0xFFC0392B)) },
                    onClick = { menu = false; deleting = true },
                )
            }
        }
    }

    if (renaming) {
        var title by remember { mutableStateOf(track.title) }
        var artist by remember { mutableStateOf(track.artist) }
        AlertDialog(
            onDismissRequest = { renaming = false },
            containerColor = VG.Card,
            title = { Text("Đổi tên bài hát") },
            text = {
                Column {
                    OutlinedTextField(title, { title = it }, label = { Text("Tên bài") }, singleLine = true)
                    Spacer(Modifier.size(8.dp))
                    OutlinedTextField(artist, { artist = it }, label = { Text("Nghệ sĩ") }, singleLine = true)
                }
            },
            confirmButton = {
                TextButton(onClick = {
                    Library.rename(track.id, title.trim(), artist.trim())
                    PlayerHub.refreshMetadata(track.id)
                    renaming = false
                }) { Text("Lưu") }
            },
            dismissButton = { TextButton(onClick = { renaming = false }) { Text("Hủy") } },
        )
    }

    if (deleting) {
        AlertDialog(
            onDismissRequest = { deleting = false },
            containerColor = VG.Card,
            title = { Text("Xóa bài hát?") },
            text = { Text("“${track.title}” sẽ bị xóa khỏi thư viện và bộ nhớ của app.") },
            confirmButton = {
                TextButton(onClick = {
                    PlayerHub.removeEverywhere(track.id)
                    Library.delete(track.id)
                    deleting = false
                    toast("Đã xóa")
                }) { Text("Xóa", color = Color(0xFFC0392B)) }
            },
            dismissButton = { TextButton(onClick = { deleting = false }) { Text("Hủy") } },
        )
    }

    if (picking) {
        PlaylistPicker(
            onDismiss = { picking = false },
            onPick = { pid, name ->
                Library.addToPlaylist(pid, track.id)
                picking = false
                toast("Đã thêm vào “$name”")
            },
        )
    }
}

@Composable
fun PlaylistPicker(onDismiss: () -> Unit, onPick: (id: String, name: String) -> Unit) {
    val playlists by Library.playlists.collectAsState()
    var creating by remember { mutableStateOf(false) }
    var name by remember { mutableStateOf("") }
    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = VG.Card,
        title = { Text(if (creating) "Playlist mới" else "Thêm vào playlist") },
        text = {
            if (creating || playlists.isEmpty()) {
                OutlinedTextField(name, { name = it }, label = { Text("Tên playlist") }, singleLine = true)
            } else {
                LazyColumn(Modifier.heightIn(max = 320.dp)) {
                    items(playlists, key = { it.id }) { p ->
                        Row(
                            Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp)).clickable { onPick(p.id, p.name) }.padding(12.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Icon(Icons.Rounded.QueueMusic, null, tint = VG.Peach)
                            Spacer(Modifier.width(12.dp))
                            Text(p.name, style = VG.Title, modifier = Modifier.weight(1f))
                            Text("${p.trackIds.size}", style = VG.Small)
                        }
                    }
                }
            }
        },
        confirmButton = {
            if (creating || playlists.isEmpty()) {
                TextButton(onClick = {
                    val p = Library.createPlaylist(name)
                    onPick(p.id, p.name)
                }) { Text("Tạo & thêm") }
            } else {
                TextButton(onClick = { creating = true }) {
                    Icon(Icons.Rounded.Add, null); Spacer(Modifier.width(4.dp)); Text("Playlist mới")
                }
            }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Hủy") } },
    )
}
