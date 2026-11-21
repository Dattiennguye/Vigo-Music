package vn.vigo.music.ui

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Bedtime
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.GraphicEq
import androidx.compose.material.icons.rounded.SwapHoriz
import androidx.compose.material3.Icon
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.delay
import vn.vigo.music.player.AudioFx
import vn.vigo.music.player.PlayerHub
import vn.vigo.music.player.SleepTimer

/** Thời gian còn lại của hẹn giờ, cập nhật mỗi giây. */
@Composable
fun rememberSleepRemaining(sl: SleepTimer): Long {
    val rem by produceState(0L, sl) {
        while (true) {
            value = if (sl.endAt > 0) (sl.endAt - System.currentTimeMillis()).coerceAtLeast(0) else 0L
            if (sl.endAt <= 0) break
            delay(1000)
        }
    }
    return rem
}

@Composable
fun SoundPanel(visible: Boolean, onClose: () -> Unit) {
    Box(Modifier.fillMaxSize()) {
        AnimatedVisibility(visible, enter = fadeIn(tween(200)), exit = fadeOut(tween(200))) {
            Box(
                Modifier.fillMaxSize().background(Color.Black.copy(alpha = 0.28f))
                    .blockTouchesBelow()
                    .bounceClick(scaleDown = 1f, haptic = false) { onClose() }
            )
        }
        AnimatedVisibility(
            visible,
            modifier = Modifier.align(Alignment.BottomCenter),
            enter = slideInVertically(spring(dampingRatio = 0.85f, stiffness = 320f)) { it },
            exit = slideOutVertically(tween(240)) { it },
        ) {
            Column(
                Modifier.fillMaxWidth().heightIn(max = 640.dp)
                    .blockTouchesBelow()
                    .clip(RoundedCornerShape(topStart = 30.dp, topEnd = 30.dp))
                    .background(VG.Cream)
                    .navigationBarsPadding()
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = 20.dp, vertical = 14.dp),
            ) {
                Box(Modifier.align(Alignment.CenterHorizontally).width(40.dp).height(4.dp).clip(CircleShape).background(VG.Line))
                Row(Modifier.fillMaxWidth().padding(top = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                    Text("Âm thanh", style = VG.H2, modifier = Modifier.weight(1f))
                    BounceIconButton(onClick = onClose) { Icon(Icons.Rounded.Close, "Đóng", tint = VG.Gray) }
                }
                SleepSection()
                Divider()
                CrossfadeSection()
                Divider()
                EqSection()
                Spacer(Modifier.height(8.dp))
            }
        }
    }
}

@Composable
private fun Divider() = Box(Modifier.padding(vertical = 14.dp).fillMaxWidth().height(1.dp).background(VG.Line))

@Composable
private fun SectionTitle(icon: androidx.compose.ui.graphics.vector.ImageVector, title: String, trailing: @Composable () -> Unit = {}) {
    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth().padding(bottom = 10.dp)) {
        Box(Modifier.size(34.dp).clip(CircleShape).background(VG.PeachPale), contentAlignment = Alignment.Center) {
            Icon(icon, null, tint = VG.Peach, modifier = Modifier.size(20.dp))
        }
        Spacer(Modifier.width(10.dp))
        Text(title, style = VG.Title.copy(fontWeight = FontWeight.Bold), modifier = Modifier.weight(1f))
        trailing()
    }
}

@Composable
private fun Pill(text: String, selected: Boolean, onClick: () -> Unit) {
    val bg by animateColorAsState(if (selected) VG.Ink else VG.Card, label = "pbg")
    val fg by animateColorAsState(if (selected) Color.White else VG.Ink, label = "pfg")
    Text(
        text, color = fg, fontSize = 14.sp, fontWeight = FontWeight.Medium,
        modifier = Modifier.bounceClick(scaleDown = 0.92f) { onClick() }.clip(CircleShape).background(bg)
            .padding(horizontal = 14.dp, vertical = 9.dp),
    )
}

@Composable
private fun SleepSection() {
    val sl by PlayerHub.sleep.collectAsState()
    val rem = rememberSleepRemaining(sl)
    SectionTitle(Icons.Rounded.Bedtime, "Hẹn giờ tắt nhạc") {
        if (sl.active) Text(
            if (sl.endOfTrack) "Hết bài này" else "Còn ${formatTime(rem)}",
            color = VG.Peach, fontWeight = FontWeight.SemiBold, fontSize = 14.sp,
        )
    }
    val opts = listOf(0 to "Tắt", 15 to "15 phút", 30 to "30 phút", 45 to "45 phút", 60 to "1 giờ", 90 to "1,5 giờ")
    LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        items(opts) { (m, label) ->
            val sel = if (m == 0) !sl.active else (!sl.endOfTrack && sl.totalMs == m * 60_000L && sl.endAt > 0)
            Pill(label, sel) { if (m == 0) PlayerHub.cancelSleep() else PlayerHub.setSleepMinutes(m) }
        }
        item { Pill("Hết bài này", sl.endOfTrack) { PlayerHub.setSleepEndOfTrack() } }
    }
    Spacer(Modifier.height(6.dp))
    Text("Nhạc nhỏ dần trong 30 giây cuối rồi tự dừng.", style = VG.Small)
}

@Composable
private fun CrossfadeSection() {
    val sec by PlayerHub.crossfadeSec.collectAsState()
    SectionTitle(Icons.Rounded.SwapHoriz, "Chuyển bài mượt") {
        Text(if (sec == 0) "Tắt" else "$sec giây", color = if (sec == 0) VG.Gray else VG.Peach, fontWeight = FontWeight.SemiBold, fontSize = 14.sp)
    }
    Slider(
        value = sec.toFloat(), onValueChange = { PlayerHub.setCrossfade(it.toInt()) },
        valueRange = 0f..12f, steps = 11,
        colors = SliderDefaults.colors(thumbColor = VG.Ink, activeTrackColor = VG.Peach, inactiveTrackColor = VG.PeachPale),
    )
    Text("Cuối bài nhỏ dần, bài sau lớn dần lên.", style = VG.Small)
}

@Composable
private fun EqSection() {
    val fx by AudioFx.state.collectAsState()
    SectionTitle(Icons.Rounded.GraphicEq, "Bộ chỉnh âm") {
        if (fx.eqSupported || fx.bassSupported) {
            Switch(
                fx.enabled, { AudioFx.setEnabled(it) },
                colors = SwitchDefaults.colors(checkedTrackColor = VG.Peach, checkedThumbColor = Color.White, uncheckedTrackColor = VG.Line),
            )
        }
    }
    if (!fx.eqSupported && !fx.bassSupported) {
        Text("Máy này không cho ứng dụng dùng bộ chỉnh âm. Hãy phát một bài rồi mở lại; nếu vẫn vậy thì máy không hỗ trợ.", style = VG.Body)
        return
    }
    if (fx.eqSupported) {
        LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            items(AudioFx.presets + listOfNotNull(if (fx.preset == "Tùy chỉnh") "Tùy chỉnh" else null)) { p ->
                Pill(p, fx.enabled && fx.preset == p) { if (p != "Tùy chỉnh") AudioFx.applyPreset(p) }
            }
        }
        Spacer(Modifier.height(10.dp))
        fx.bands.forEachIndexed { i, b ->
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    if (b.centerHz >= 1000) "${b.centerHz / 1000}k" else "${b.centerHz}",
                    style = VG.Small.copy(fontWeight = FontWeight.Medium), modifier = Modifier.widthIn(min = 44.dp),
                )
                Slider(
                    value = b.level.toFloat(),
                    onValueChange = { AudioFx.setBand(i, it.toInt()) },
                    valueRange = fx.minLevel.toFloat()..fx.maxLevel.toFloat(),
                    modifier = Modifier.weight(1f),
                    enabled = fx.enabled,
                    colors = SliderDefaults.colors(thumbColor = VG.Ink, activeTrackColor = VG.Peach, inactiveTrackColor = VG.PeachPale),
                )
                Text(
                    "%+d dB".format(b.level / 100),
                    style = VG.Small, modifier = Modifier.widthIn(min = 52.dp).padding(start = 6.dp),
                )
            }
        }
    }
    if (fx.bassSupported) {
        Spacer(Modifier.height(6.dp))
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text("Bass", style = VG.Small.copy(fontWeight = FontWeight.Medium), modifier = Modifier.widthIn(min = 44.dp))
            Slider(
                value = fx.bass.toFloat(), onValueChange = { AudioFx.setBass(it.toInt()) },
                valueRange = 0f..1000f, modifier = Modifier.weight(1f), enabled = fx.enabled,
                colors = SliderDefaults.colors(thumbColor = VG.Ink, activeTrackColor = VG.Peach, inactiveTrackColor = VG.PeachPale),
            )
            Text("${fx.bass / 10}%", style = VG.Small, modifier = Modifier.widthIn(min = 52.dp).padding(start = 6.dp))
        }
    }
    Text("Dùng bộ chỉnh âm thật của Android, áp dụng cho mọi bài trong app.", style = VG.Small, modifier = Modifier.padding(top = 6.dp))
}

