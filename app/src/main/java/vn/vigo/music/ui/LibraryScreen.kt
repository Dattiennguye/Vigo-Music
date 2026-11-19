package vn.vigo.music.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.Language
import androidx.compose.material.icons.rounded.LibraryMusic
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material.icons.rounded.Shuffle
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import vn.vigo.music.data.Library
import vn.vigo.music.player.PlayerHub
import java.text.Normalizer

private enum class Filter(val label: String) { All("Tất cả"), Fav("Yêu thích"), Web("Tải từ web"), Recent("Mới thêm") }

/** Bỏ dấu tiếng Việt để tìm "lac troi" vẫn ra "Lạc Trôi". */
fun foldVi(s: String): String =
    Normalizer.normalize(s.lowercase(), Normalizer.Form.NFD)
        .replace(Regex("\\p{Mn}+"), "")
        .replace('đ', 'd')

@Composable
fun LibraryScreen(
    bottomPadding: androidx.compose.ui.unit.Dp,
    onImport: () -> Unit,
    onOpenWeb: () -> Unit,
    onOpenPlayer: () -> Unit,
    toast: (String) -> Unit,
) {
    val tracks by Library.tracks.collectAsState()
    val favs by Library.favorites.collectAsState()
    val ps by PlayerHub.state.collectAsState()
    val frame = rememberAudioFrame(ps.isPlaying)
    var filter by rememberSaveable { mutableStateOf(Filter.All) }
    var query by rememberSaveable { mutableStateOf("") }

    val shown = remember(tracks, favs, filter, query) {
        val base = when (filter) {
            Filter.All -> tracks.sortedBy { foldVi(it.title) }
            Filter.Fav -> tracks.filter { it.id in favs }.sortedBy { foldVi(it.title) }
            Filter.Web -> tracks.filter { it.source == "web" }.sortedByDescending { it.addedAt }
            Filter.Recent -> tracks.sortedByDescending { it.addedAt }.take(50)
        }
        val q = foldVi(query.trim())
        if (q.isEmpty()) base else base.filter { foldVi(it.title).contains(q) || foldVi(it.artist).contains(q) || foldVi(it.album).contains(q) }
    }
    val current = ps.currentId?.let { id -> tracks.firstOrNull { it.id == id } }
    val heroTrack = current ?: tracks.firstOrNull()

    LazyColumn(
        Modifier.fillMaxSize(),
        contentPadding = PaddingValues(start = 18.dp, end = 18.dp, top = 8.dp, bottom = bottomPadding + 16.dp),
    ) {
        item {
            Row(Modifier.fillMaxWidth().padding(start = 6.dp, bottom = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text("Thư viện", style = VG.H2.copy(fontSize = 30.sp))
                    Text("${tracks.size} bài hát", style = VG.Small)
                }
                Box(
                    Modifier.size(46.dp).bounceClick { onImport() }.clip(CircleShape).background(VG.Ink),
                    contentAlignment = Alignment.Center,
                ) { Icon(Icons.Rounded.Add, "Nhập nhạc", tint = Color.White) }
            }
        }

        if (tracks.isEmpty()) {
            item { EmptyLibrary(onImport, onOpenWeb) }
            return@LazyColumn
        }

        item {
            val art = rememberArt(heroTrack, 720)
            SleeveHero(
                keySeed = heroTrack?.id ?: "all",
                label = if (current != null) "Đang phát" else "Bộ sưu tập",
                title = current?.title ?: "Tất cả bài hát",
                spinning = ps.isPlaying,
                art = art,
                labelArt = art,
                frame = frame,
                liftOnPlay = true,
                playing = ps.isPlaying && current != null,
                onPlay = {
                    if (current != null) PlayerHub.togglePlay()
                    else PlayerHub.playIds(shown.map { it.id })
                },
                modifier = Modifier.padding(horizontal = 14.dp, vertical = 6.dp).clickable(enabled = current != null) { onOpenPlayer() },
            )
        }

        item {
            Spacer(Modifier.height(14.dp))
            SearchField(query, { query = it })
            Spacer(Modifier.height(12.dp))
            LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                items(Filter.entries.toList()) { f ->
                    val sel = f == filter
                    val bg by androidx.compose.animation.animateColorAsState(if (sel) VG.Ink else VG.Card, label = "chipbg")
                    val fg by androidx.compose.animation.animateColorAsState(if (sel) Color.White else VG.Ink, label = "chipfg")
                    Text(
                        f.label,
                        color = fg,
                        fontSize = 14.sp, fontWeight = FontWeight.Medium,
                        modifier = Modifier.bounceClick(scaleDown = 0.92f) { filter = f }
                            .clip(CircleShape)
                            .background(bg)
                            .padding(horizontal = 16.dp, vertical = 9.dp),
                    )
                }
            }
            Spacer(Modifier.height(10.dp))
            Row(Modifier.fillMaxWidth().padding(horizontal = 6.dp), verticalAlignment = Alignment.CenterVertically) {
                Text("${shown.size} bài", style = VG.Small, modifier = Modifier.weight(1f))
                if (shown.isNotEmpty()) {
                    Row(
                        Modifier.bounceClick { PlayerHub.playIds(shown.map { it.id }.shuffled(), 0) }.clip(CircleShape)
                            .padding(horizontal = 10.dp, vertical = 6.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Icon(Icons.Rounded.Shuffle, null, tint = VG.Peach, modifier = Modifier.size(18.dp))
                        Spacer(Modifier.width(6.dp))
                        Text("Phát ngẫu nhiên", color = VG.Peach, fontSize = 14.sp, fontWeight = FontWeight.SemiBold)
                    }
                }
            }
        }

        if (shown.isEmpty()) {
            item {
                Text(
                    if (filter == Filter.Fav) "Chưa có bài yêu thích. Bấm ♡ ở bài hát để lưu." else "Không tìm thấy bài phù hợp.",
                    style = VG.Body, textAlign = TextAlign.Center,
                    modifier = Modifier.fillMaxWidth().padding(32.dp),
                )
            }
        }

        items(shown, key = { it.id }) { t ->
            Box(Modifier.animateItem()) {
            TrackItem(
                track = t,
                isCurrent = t.id == ps.currentId,
                isFav = t.id in favs,
                onClick = {
                    if (t.id == ps.currentId) onOpenPlayer()
                    else PlayerHub.playIds(shown.map { it.id }, shown.indexOf(t))
                },
                toast = toast,
                frame = frame,
            )
            }
        }
    }
}

@Composable
fun SearchField(value: String, onChange: (String) -> Unit, placeholder: String = "Tìm bài hát, nghệ sĩ…") {
    Row(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(18.dp)).background(VG.Card).padding(horizontal = 14.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(Icons.Rounded.Search, null, tint = VG.Gray)
        Spacer(Modifier.width(10.dp))
        Box(Modifier.weight(1f)) {
            if (value.isEmpty()) Text(placeholder, style = VG.Body)
            BasicTextField(
                value, onChange, singleLine = true,
                textStyle = VG.Title.copy(fontWeight = FontWeight.Normal, fontSize = 15.sp),
                cursorBrush = SolidColor(VG.Peach),
                modifier = Modifier.fillMaxWidth(),
            )
        }
        if (value.isNotEmpty()) {
            Icon(Icons.Rounded.Close, "Xóa", tint = VG.Gray, modifier = Modifier.size(20.dp).clickable { onChange("") })
        }
    }
}

@Composable
private fun EmptyLibrary(onImport: () -> Unit, onOpenWeb: () -> Unit) {
    Column(Modifier.fillMaxWidth().padding(top = 8.dp), horizontalAlignment = Alignment.CenterHorizontally) {
        SleeveHero("empty", "Bắt đầu nào", "Thư viện trống", spinning = false, modifier = Modifier.padding(horizontal = 14.dp))
        Spacer(Modifier.height(20.dp))
        Text("Thêm bài hát đầu tiên", style = VG.H2, textAlign = TextAlign.Center)
        Spacer(Modifier.height(8.dp))
        Text("Chọn file nhạc trong máy, hoặc tìm trên web rồi bấm vào link .mp3 để tải về.", style = VG.Body, textAlign = TextAlign.Center)
        Spacer(Modifier.height(22.dp))
        BigButton("Chọn file nhạc", Icons.Rounded.LibraryMusic, dark = true, onClick = onImport)
        Spacer(Modifier.height(10.dp))
        BigButton("Tìm & tải trên web", Icons.Rounded.Language, dark = false, onClick = onOpenWeb)
    }
}

@Composable
fun BigButton(text: String, icon: androidx.compose.ui.graphics.vector.ImageVector, dark: Boolean, onClick: () -> Unit, modifier: Modifier = Modifier) {
    Row(
        modifier.fillMaxWidth().height(56.dp).bounceClick(scaleDown = 0.95f) { onClick() }
            .clip(RoundedCornerShape(28.dp)).background(if (dark) VG.Ink else VG.Card),
        horizontalArrangement = Arrangement.Center, verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(icon, null, tint = if (dark) Color.White else VG.Ink)
        Spacer(Modifier.width(10.dp))
        Text(text, color = if (dark) Color.White else VG.Ink, fontSize = 16.sp, fontWeight = FontWeight.Medium)
    }
}
