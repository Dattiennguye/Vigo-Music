package vn.vigo.music.ui

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.Animatable
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.graphics.graphicsLayer
import kotlinx.coroutines.launch
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.offset
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
import androidx.compose.material.icons.rounded.KeyboardArrowDown
import androidx.compose.material.icons.rounded.Tune
import androidx.compose.material.icons.rounded.Bedtime
import androidx.compose.material.icons.rounded.Repeat
import androidx.compose.material.icons.rounded.RepeatOne
import androidx.compose.material.icons.rounded.Shuffle
import androidx.compose.material.icons.rounded.SkipNext
import androidx.compose.material.icons.rounded.SkipPrevious
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.State
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.media3.common.Player
import vn.vigo.music.data.Library
import vn.vigo.music.data.Track
import vn.vigo.music.player.PlayerHub

private val tabs = listOf("Bài hát", "Lời", "Hàng chờ")

@Composable
fun PlayerScreen(onClose: () -> Unit, toast: (String) -> Unit = {}) {
    BackHandler { onClose() }
    val ps by PlayerHub.state.collectAsState()
    val tracks by Library.tracks.collectAsState()
    val favs by Library.favorites.collectAsState()
    val track = ps.currentId?.let { id -> tracks.firstOrNull { it.id == id } }
    var tab by rememberSaveable { mutableIntStateOf(0) }
    var soundOpen by rememberSaveable { androidx.compose.runtime.mutableStateOf(false) }
    val sleep by PlayerHub.sleep.collectAsState()

    LaunchedEffect(track == null) { if (track == null) onClose() }
    if (track == null) return

    val frame = rememberAudioFrame(ps.isPlaying)
    val lyrics = rememberLyrics(track)
    val art = rememberArt(track, 900)
    val progress = if (ps.durationMs > 0) ps.positionMs.toFloat() / ps.durationMs else 0f

    Box(Modifier.fillMaxSize().blockTouchesBelow()) {
        PlayerBackdrop(track, frame, ps.isPlaying, Modifier.fillMaxSize())

        Column(Modifier.fillMaxSize().statusBarsPadding().navigationBarsPadding().padding(horizontal = 22.dp)) {
            Row(Modifier.fillMaxWidth().height(56.dp), verticalAlignment = Alignment.CenterVertically) {
                BounceIconButton(onClick = onClose) {
                    Icon(Icons.Rounded.KeyboardArrowDown, "Thu nhỏ", tint = VG.Ink, modifier = Modifier.size(30.dp))
                }
                Box(Modifier.weight(1f), contentAlignment = Alignment.Center) {
                    SegmentedTabs(tab) { tab = it }
                }
                BounceIconButton(onClick = { soundOpen = true }) {
                    Box {
                        Icon(Icons.Rounded.Tune, "Âm thanh", tint = VG.Ink)
                        if (sleep.active) {
                            Box(
                                Modifier.align(Alignment.TopEnd).offset(x = 6.dp, y = (-6).dp).size(14.dp)
                                    .clip(CircleShape).background(VG.Peach),
                                contentAlignment = Alignment.Center,
                            ) { Icon(Icons.Rounded.Bedtime, null, tint = Color.White, modifier = Modifier.size(9.dp)) }
                        }
                    }
                }
            }
            if (sleep.active) {
                val rem = rememberSleepRemaining(sleep)
                Text(
                    if (sleep.endOfTrack) "☾ Dừng sau bài này" else "☾ Tắt nhạc sau ${formatTime(rem)}",
                    style = VG.Small.copy(color = VG.Peach, fontWeight = FontWeight.SemiBold),
                    modifier = Modifier.align(Alignment.CenterHorizontally),
                )
            }

            AnimatedContent(
                tab, Modifier.weight(1f),
                transitionSpec = {
                    val dir = if (targetState > initialState) 1 else -1
                    (slideInHorizontally(spring(dampingRatio = 0.85f, stiffness = 380f)) { it * dir / 3 } + fadeIn(tween(220))) togetherWith
                        (slideOutHorizontally(tween(200)) { -it * dir / 3 } + fadeOut(tween(150)))
                },
                label = "ptab",
            ) { t ->
                when (t) {
                    0 -> Column(Modifier.fillMaxSize(), verticalArrangement = Arrangement.Center) {
                        SleeveHero(
                            keySeed = track.id,
                            label = track.album.ifBlank { when (track.source) { "web" -> "Tải từ web"; "video" -> "Từ video"; else -> "Thư viện của tôi" } },
                            title = track.artist,
                            spinning = ps.isPlaying,
                            art = art, labelArt = art,
                            showHeart = false,
                            frame = frame,
                            liftOnPlay = true,
                            modifier = Modifier.padding(horizontal = 6.dp),
                        )
                        Spacer(Modifier.height(14.dp))
                        LiveSpectrum(frame, Modifier.fillMaxWidth().height(34.dp).padding(horizontal = 18.dp))
                    }
                    1 -> LyricsPanel(track, lyrics, ps.positionMs, ps.isPlaying, toast)
                    else -> QueueList(frame)
                }
            }

            Spacer(Modifier.height(14.dp))
            // Tên bài trượt khi đổi bài
            Row(verticalAlignment = Alignment.CenterVertically) {
                AnimatedContent(
                    track,
                    Modifier.weight(1f),
                    transitionSpec = {
                        (slideInVertically(spring(dampingRatio = 0.8f, stiffness = 300f)) { it / 2 } + fadeIn(tween(250))) togetherWith
                            (slideOutVertically(tween(180)) { -it / 2 } + fadeOut(tween(150)))
                    },
                    contentKey = { it.id },
                    label = "title",
                ) { t ->
                    Column {
                        Text(t.title, style = VG.H2, maxLines = 2, overflow = TextOverflow.Ellipsis)
                        Spacer(Modifier.height(2.dp))
                        Text(t.artist, style = VG.Body, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    }
                }
                HeartButton(track.id in favs, onToggle = { Library.toggleFavorite(track.id) }, size = 28.dp, idleTint = VG.Ink)
            }
            if (tab != 1) {
                LyricTicker(lyrics, ps.positionMs, Modifier.padding(top = 4.dp).height(24.dp))
            }

            Spacer(Modifier.height(12.dp))
            Column(
                Modifier.fillMaxWidth()
                    .shadow(14.dp, RoundedCornerShape(26.dp), ambientColor = VG.Peach.copy(alpha = 0.35f), spotColor = VG.Peach.copy(alpha = 0.3f))
                    .background(VG.Card.copy(alpha = 0.92f), RoundedCornerShape(26.dp))
                    .padding(horizontal = 18.dp, vertical = 14.dp)
            ) {
                WaveformSeek(
                    track.id, progress, Modifier.fillMaxWidth().height(46.dp), bars = 44,
                    onSeek = { f -> if (ps.durationMs > 0) PlayerHub.seekTo((f * ps.durationMs).toLong()) },
                    frame = frame,
                )
                Row {
                    Text(formatTime(ps.positionMs), style = VG.Small)
                    Spacer(Modifier.weight(1f))
                    Text(formatTime(ps.durationMs), style = VG.Small)
                }
            }

            Spacer(Modifier.height(16.dp))
            Controls(ps.isPlaying, ps.shuffle, ps.repeatMode, frame)
        }
        BackHandler(enabled = soundOpen) { soundOpen = false }
        SoundPanel(soundOpen, onClose = { soundOpen = false })
    }
}

@Composable
private fun Controls(isPlaying: Boolean, shuffle: Boolean, repeatMode: Int, frame: State<FloatArray>) {
    Row(
        Modifier.fillMaxWidth().padding(bottom = 18.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        BounceIconButton(onClick = { PlayerHub.toggleShuffle() }) {
            Icon(Icons.Rounded.Shuffle, "Ngẫu nhiên", tint = if (shuffle) VG.Peach else VG.Gray)
        }
        SkipButton(Icons.Rounded.SkipPrevious, "Bài trước", forward = false) { PlayerHub.previous() }
        Box(Modifier.size(84.dp).bounceClick(scaleDown = 0.86f) { PlayerHub.togglePlay() }, contentAlignment = Alignment.Center) {
            // vòng sáng nhịp theo âm lượng thật
            Canvas(Modifier.fillMaxSize()) {
                val l = frame.value.level
                drawCircle(VG.Peach.copy(alpha = 0.28f * l), size.minDimension / 2f * (0.95f + l * 0.25f))
            }
            Box(
                Modifier.size(76.dp).shadow(14.dp, CircleShape).clip(CircleShape).background(VG.Ink),
                contentAlignment = Alignment.Center,
            ) { PlayPauseIcon(isPlaying, Color.White, 38.dp) }
        }
        SkipButton(Icons.Rounded.SkipNext, "Bài tiếp", forward = true) { PlayerHub.next() }
        BounceIconButton(onClick = { PlayerHub.cycleRepeat() }) {
            AnimatedContent(repeatMode, transitionSpec = { scaleIn() + fadeIn() togetherWith scaleOut() + fadeOut() }, label = "rep") { m ->
                Icon(
                    if (m == Player.REPEAT_MODE_ONE) Icons.Rounded.RepeatOne else Icons.Rounded.Repeat,
                    "Lặp lại",
                    tint = if (m == Player.REPEAT_MODE_OFF) VG.Gray else VG.Peach,
                )
            }
        }
    }
}

/** Nút chuyển bài: icon trượt nhẹ theo hướng khi bấm. */
@Composable
private fun SkipButton(icon: androidx.compose.ui.graphics.vector.ImageVector, desc: String, forward: Boolean, onClick: () -> Unit) {
    val nudge = remember { Animatable(0f) }
    val scope = rememberCoroutineScope()
    BounceIconButton(onClick = {
        onClick()
        scope.launch {
            nudge.snapTo(if (forward) 1f else -1f)
            nudge.animateTo(0f, spring(dampingRatio = 0.35f, stiffness = 400f))
        }
    }, modifier = Modifier.size(60.dp)) {
        Icon(
            icon, desc, tint = VG.Ink,
            modifier = Modifier.size(38.dp).graphicsLayer { translationX = nudge.value * 8.dp.toPx() },
        )
    }
}

@Composable
private fun SegmentedTabs(selected: Int, onSelect: (Int) -> Unit) {
    BoxWithConstraints(
        Modifier.width(250.dp).height(38.dp).clip(CircleShape).background(VG.Card.copy(alpha = 0.75f)).padding(3.dp)
    ) {
        val w = maxWidth / tabs.size
        val x by animateDpAsState(w * selected, spring(dampingRatio = 0.7f, stiffness = 420f), label = "seg")
        Box(Modifier.offset(x = x).width(w).fillMaxHeight().clip(CircleShape).background(VG.Ink))
        Row(Modifier.fillMaxSize()) {
            tabs.forEachIndexed { i, label ->
                Box(
                    Modifier.weight(1f).fillMaxHeight().bounceClick(scaleDown = 0.92f) { onSelect(i) },
                    contentAlignment = Alignment.Center,
                ) {
                    Text(
                        label, fontSize = 13.sp, fontWeight = FontWeight.SemiBold,
                        color = if (i == selected) Color.White else VG.Ink,
                    )
                }
            }
        }
    }
}

@Composable
private fun QueueList(frame: State<FloatArray>) {
    val ps by PlayerHub.state.collectAsState()
    val tracks by Library.tracks.collectAsState()
    val byId = remember(tracks) { tracks.associateBy { it.id } }
    val listState = rememberLazyListState(initialFirstVisibleItemIndex = (ps.index - 1).coerceAtLeast(0))
    LazyColumn(Modifier.fillMaxSize(), state = listState, contentPadding = PaddingValues(vertical = 8.dp)) {
        itemsIndexed(ps.queue, key = { i, id -> "$i-$id" }) { i, id ->
            val t: Track = byId[id] ?: return@itemsIndexed
            val cur = i == ps.index
            Row(
                Modifier.animateItem().fillMaxWidth().clip(RoundedCornerShape(16.dp))
                    .background(if (cur) VG.PeachPale.copy(alpha = 0.8f) else Color.Transparent)
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
                if (cur) EqualizerBars(frame, Modifier.size(18.dp), color = VG.Peach)
                else Text("${i + 1}", style = VG.Small.copy(fontWeight = FontWeight.Medium))
            }
        }
    }
}
