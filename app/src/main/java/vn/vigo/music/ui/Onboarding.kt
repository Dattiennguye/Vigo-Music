package vn.vigo.music.ui

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateDpAsState
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
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.ArrowForward
import androidx.compose.material.icons.rounded.ChevronRight
import androidx.compose.material.icons.rounded.Download
import androidx.compose.material.icons.rounded.Pause
import androidx.compose.material.icons.rounded.QueueMusic
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.launch

private data class OnbPage(val title: String, val body: String)

private val pages = listOf(
    OnbPage("Nhạc của bạn.\nLuôn ở bên.", "Nhập file nhạc từ máy, Zalo hay thẻ nhớ — lưu ngay trong app, nghe cả khi không có mạng."),
    OnbPage("Tìm trên web.\nTải về một chạm.", "Tìm bài hát bằng Google ngay trong app, bấm vào link file nhạc là tự lưu vào thư viện."),
    OnbPage("Nhạc của bạn.\nThế giới nhỏ của bạn.", "Lưu bài yêu thích, tạo playlist và giữ mọi âm thanh thật gần."),
)

@Composable
fun OnboardingScreen(onDone: () -> Unit) {
    val pager = rememberPagerState { pages.size }
    val scope = rememberCoroutineScope()
    Column(
        Modifier.fillMaxSize().background(VG.Cream).statusBarsPadding().navigationBarsPadding(),
    ) {
        Box(Modifier.fillMaxWidth().height(48.dp)) {
            Text(
                "Bỏ qua",
                style = VG.Title.copy(fontWeight = FontWeight.Normal, fontSize = 17.sp),
                modifier = Modifier.align(Alignment.CenterEnd).padding(end = 24.dp)
                    .bounceClick { onDone() }.padding(8.dp),
            )
        }
        HorizontalPager(pager, Modifier.weight(1f)) { page ->
            Column(
                Modifier.fillMaxSize().padding(horizontal = 28.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                // hiệu ứng thị sai: hình minh họa trượt chậm hơn chữ khi vuốt trang
                val off = (pager.currentPage - page) + pager.currentPageOffsetFraction
                Box(
                    Modifier.weight(1f).fillMaxWidth().graphicsLayer {
                        translationX = off * size.width * 0.45f
                        val k = 1f - kotlin.math.abs(off).coerceIn(0f, 1f) * 0.15f
                        scaleX = k; scaleY = k
                        alpha = 1f - kotlin.math.abs(off).coerceIn(0f, 1f) * 0.6f
                    },
                    contentAlignment = Alignment.Center,
                ) {
                    when (page) {
                        0 -> SleeveHero("onb-albums", "Bộ sưu tập", "Album", spinning = true, onPlay = {}, modifier = Modifier.padding(horizontal = 12.dp))
                        1 -> WebIllustration()
                        else -> CollectionIllustration()
                    }
                }
                Text(pages[page].title, style = VG.H1, textAlign = TextAlign.Center)
                Spacer(Modifier.height(12.dp))
                Text(pages[page].body, style = VG.Body.copy(fontSize = 16.sp), textAlign = TextAlign.Center)
                Spacer(Modifier.height(8.dp))
            }
        }
        Row(Modifier.fillMaxWidth().padding(vertical = 18.dp), horizontalArrangement = Arrangement.Center) {
            repeat(pages.size) { i ->
                val sel = pager.currentPage == i
                val c by animateColorAsState(if (sel) VG.Peach else VG.Line, label = "dot")
                val w by animateDpAsState(if (sel) 22.dp else 10.dp, label = "dotw")
                Box(Modifier.padding(horizontal = 4.dp).height(10.dp).width(w).clip(CircleShape).background(c))
            }
        }
        val last = pager.currentPage == pages.lastIndex
        Row(
            Modifier.padding(horizontal = 24.dp).padding(bottom = 16.dp).fillMaxWidth().height(60.dp)
                .bounceClick(scaleDown = 0.95f) {
                    if (last) onDone() else scope.launch { pager.animateScrollToPage(pager.currentPage + 1) }
                }
                .clip(RoundedCornerShape(30.dp)).background(VG.Ink),
            horizontalArrangement = Arrangement.Center,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            androidx.compose.animation.AnimatedContent(last, label = "cta") { l ->
                Text(if (l) "Bắt đầu nghe" else "Tiếp tục", color = Color.White, fontSize = 18.sp, fontWeight = FontWeight.Medium)
            }
            Spacer(Modifier.width(12.dp))
            Icon(Icons.Rounded.ArrowForward, null, tint = Color.White)
        }
    }
}

@Composable
private fun CardBox(modifier: Modifier = Modifier, content: @Composable () -> Unit) {
    Box(
        modifier.fillMaxWidth()
            .shadow(14.dp, RoundedCornerShape(26.dp), ambientColor = VG.Peach.copy(alpha = 0.4f), spotColor = VG.Peach.copy(alpha = 0.3f))
            .background(VG.Card, RoundedCornerShape(26.dp))
            .padding(16.dp)
    ) { content() }
}

@Composable
private fun CollectionIllustration() {
    Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
        CardBox {
            Column {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Rounded.QueueMusic, null, tint = VG.Ink)
                    Spacer(Modifier.width(10.dp))
                    Text("Playlist của tôi", style = VG.Title, modifier = Modifier.weight(1f))
                    Icon(Icons.Rounded.ChevronRight, null, tint = VG.Ink)
                }
                Spacer(Modifier.height(14.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    listOf("a1", "b2", "c3").forEach {
                        Artwork(null, Modifier.weight(1f).height(72.dp), corner = 12.dp, seed = it)
                    }
                    Box(
                        Modifier.weight(1f).height(72.dp).clip(RoundedCornerShape(12.dp)).background(VG.Cream2),
                        contentAlignment = Alignment.Center,
                    ) { Icon(Icons.Rounded.Add, null, tint = VG.Ink, modifier = Modifier.size(28.dp)) }
                }
            }
        }
        CardBox {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(Modifier.size(44.dp).clip(CircleShape).background(VG.Ink), contentAlignment = Alignment.Center) {
                    Icon(Icons.Rounded.Pause, null, tint = Color.White)
                }
                Spacer(Modifier.width(14.dp))
                WaveformSeek("onboarding", 0.45f, Modifier.weight(1f).height(36.dp))
                Spacer(Modifier.width(12.dp))
                Text("2:38", style = VG.Small.copy(color = VG.Ink.copy(alpha = 0.7f)))
            }
        }
    }
}

@Composable
private fun WebIllustration() {
    Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
        CardBox {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(Modifier.size(36.dp).clip(CircleShape).background(VG.PeachPale), contentAlignment = Alignment.Center) {
                    Text("G", color = VG.Peach, fontWeight = FontWeight.Bold, fontSize = 18.sp)
                }
                Spacer(Modifier.width(12.dp))
                Text("lạc trôi mp3", style = VG.Title.copy(fontWeight = FontWeight.Normal), modifier = Modifier.weight(1f))
                Icon(Icons.Rounded.Search, null, tint = VG.Gray)
            }
        }
        CardBox {
            Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
                listOf("bai-hat-cua-ban.mp3" to 1f, "ban-thu-am.m4a" to 0.62f).forEach { (name, p) ->
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Box(Modifier.size(40.dp).clip(RoundedCornerShape(12.dp)).background(VG.PeachPale), contentAlignment = Alignment.Center) {
                            Icon(Icons.Rounded.Download, null, tint = VG.Peach)
                        }
                        Spacer(Modifier.width(12.dp))
                        Column(Modifier.weight(1f)) {
                            Text(name, style = VG.Title.copy(fontSize = 14.sp))
                            Spacer(Modifier.height(6.dp))
                            Box(Modifier.fillMaxWidth().height(5.dp).clip(CircleShape).background(VG.PeachPale)) {
                                Box(Modifier.fillMaxWidth(p).height(5.dp).clip(CircleShape).background(VG.Peach))
                            }
                        }
                    }
                }
            }
        }
    }
}
