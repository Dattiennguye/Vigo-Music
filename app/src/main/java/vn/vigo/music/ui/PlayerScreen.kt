package vn.vigo.music.ui

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
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
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Favorite
import androidx.compose.material.icons.rounded.FavoriteBorder
import androidx.compose.material.icons.rounded.GraphicEq
import androidx.compose.material.icons.rounded.KeyboardArrowDown
import androidx.compose.material.icons.rounded.Pause
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.QueueMusic
import androidx.compose.material.icons.rounded.Repeat
import androidx.compose.material.icons.rounded.RepeatOne
import androidx.compose.material.icons.rounded.Shuffle
import androidx.compose.material.icons.rounded.SkipNext
import androidx.compose.material.icons.rounded.SkipPrevious
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.media3.common.Player
import vn.vigo.music.data.Library
import vn.vigo.music.player.PlayerHub

@Composable
fun PlayerScreen(onClose: () -> Unit) {
    BackHandler { onClose() }
    val ps by PlayerHub.state.collectAsState()
    val tracks by Library.tracks.collectAsState()
    val favs by Library.favorites.collectAsState()
    val track = ps.currentId?.let { id -> tracks.firstOrNull { it.id == id } }
    var showQueue by remember { mutableStateOf(false) }

    LaunchedEffect(track == null) { if (track == null) onClose() }
    if (track == null) return

    val art = rememberArt(track, 900)
    val progress = if (ps.durationMs > 0) ps.positionMs.toFloat() / ps.durationMs else 0f

    Column(
        Modifier.fillMaxSize().background(VG.Cream).statusBarsPadding().navigationBarsPadding().padding(horizontal = 22.dp),
    ) {
        Row(Modifier.fillMaxWidth().height(56.dp), verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = onClose) { Icon(Icons.Rounded.KeyboardArrowDown, "Thu nhỏ", tint = VG.Ink, modifier = Modifier.size(30.dp)) }
            Text(
                if (showQueue) "Hàng chờ" else "Đang phát",
                style = VG.Title, textAlign = TextAlign.Center, modifier = Modifier.weight(1f),
            )
            IconButton(onClick = { showQueue = !showQueue }) {
                Icon(Icons.Rounded.QueueMusic, "Hàng chờ", tint = if (showQueue) VG.Peach else VG.Ink)
            }
        }

        AnimatedContent(showQueue, Modifier.weight(1f), transitionSpec = { fadeIn() togetherWith fadeOut() }, label = "pq") { queue ->
            if (queue) {
                QueueList()
            } else {
                Column(Modifier.fillMaxSize(), verticalArrangement = Arrangement.Center) {
                    SleeveHero(
                        keySeed = track.id,
                        label = track.album.ifBlank { if (track.source == "web") "Tải từ web" else "Thư viện của tôi" },
                        title = track.artist,
                        spinning = ps.isPlaying,
                        art = art, labelArt = art,
                        showHeart = false,
                        modifier = Modifier.padding(horizontal = 6.dp),
                    )
                }
            }
        }

        Spacer(Modifier.height(18.dp))
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text(track.title, style = VG.H2, maxLines = 2, overflow = TextOverflow.Ellipsis)
                Spacer(Modifier.height(2.dp))
                Text(track.artist, style = VG.Body, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
            val fav = track.id in favs
            IconButton(onClick = { Library.toggleFavorite(track.id) }) {
                Icon(if (fav) Icons.Rounded.Favorite else Icons.Rounded.FavoriteBorder, "Yêu thích", tint = if (fav) VG.Peach else VG.Ink, modifier = Modifier.size(28.dp))
            }
        }

        Spacer(Modifier.height(16.dp))
        // Thanh tua dạng sóng âm, nằm trong thẻ trắng giống ảnh mẫu
        Column(
            Modifier.fillMaxWidth()
                .shadow(14.dp, RoundedCornerShape(26.dp), ambientColor = VG.Peach.copy(alpha = 0.35f), spotColor = VG.Peach.copy(alpha = 0.3f))
                .background(VG.Card, RoundedCornerShape(26.dp))
                .padding(horizontal = 18.dp, vertical = 14.dp)
        ) {
            WaveformSeek(
                track.id, progress, Modifier.fillMaxWidth().height(46.dp), bars = 44,
                onSeek = { f -> if (ps.durationMs > 0) PlayerHub.seekTo((f * ps.durationMs).toLong()) },
            )
            Row {
                Text(formatTime(ps.positionMs), style = VG.Small)
                Spacer(Modifier.weight(1f))
                Text(formatTime(ps.durationMs), style = VG.Small)
            }
        }

        Spacer(Modifier.height(18.dp))
        Row(
            Modifier.fillMaxWidth().padding(bottom = 18.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            IconButton(onClick = { PlayerHub.toggleShuffle() }) {
                Icon(Icons.Rounded.Shuffle, "Ngẫu nhiên", tint = if (ps.shuffle) VG.Peach else VG.Gray)
            }
            IconButton(onClick = { PlayerHub.previous() }, modifier = Modifier.size(56.dp)) {
                Icon(Icons.Rounded.SkipPrevious, "Bài trước", tint = VG.Ink, modifier = Modifier.size(36.dp))
            }
            Box(
                Modifier.size(78.dp).shadow(14.dp, CircleShape).clip(CircleShape).background(VG.Ink).clickable { PlayerHub.togglePlay() },
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    if (ps.isPlaying) Icons.Rounded.Pause else Icons.Rounded.PlayArrow,
                    if (ps.isPlaying) "Tạm dừng" else "Phát",
                    tint = Color.White, modifier = Modifier.size(38.dp),
                )
            }
            IconButton(onClick = { PlayerHub.next() }, modifier = Modifier.size(56.dp)) {
                Icon(Icons.Rounded.SkipNext, "Bài tiếp", tint = VG.Ink, modifier = Modifier.size(36.dp))
            }
            IconButton(onClick = { PlayerHub.cycleRepeat() }) {
                Icon(
                    if (ps.repeatMode == Player.REPEAT_MODE_ONE) Icons.Rounded.RepeatOne else Icons.Rounded.Repeat,
                    "Lặp lại",
                    tint = if (ps.repeatMode == Player.REPEAT_MODE_OFF) VG.Gray else VG.Peach,
                )
            }
        }
    }
}

@Composable
private fun QueueList() {
    val ps by PlayerHub.state.collectAsState()
    val tracks by Library.tracks.collectAsState()
    val byId = remember(tracks) { tracks.associateBy { it.id } }
    val listState = rememberLazyListState(initialFirstVisibleItemIndex = (ps.index - 1).coerceAtLeast(0))
    LazyColumn(Modifier.fillMaxSize(), state = listState, contentPadding = PaddingValues(vertical = 8.dp)) {
        itemsIndexed(ps.queue) { i, id ->
            val t = byId[id] ?: return@itemsIndexed
            val cur = i == ps.index
            Row(
                Modifier.fillMaxWidth().clip(RoundedCornerShape(16.dp))
                    .background(if (cur) VG.PeachPale.copy(alpha = 0.7f) else Color.Transparent)
                    .clickable { PlayerHub.jumpTo(i) }
                    .padding(8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Artwork(t, Modifier.size(46.dp), corner = 12.dp, maxPx = 140)
                Spacer(Modifier.width(12.dp))
                Column(Modifier.weight(1f)) {
                    Text(t.title, style = VG.Title.copy(fontSize = 15.sp, color = if (cur) VG.Peach else VG.Ink), maxLines = 1, overflow = TextOverflow.Ellipsis)
                    Text(t.artist, style = VG.Small, maxLines = 1)
                }
                if (cur) Icon(Icons.Rounded.GraphicEq, null, tint = VG.Peach)
                else Text("${i + 1}", style = VG.Small.copy(fontWeight = FontWeight.Medium))
            }
        }
    }
}
