package vn.vigo.music.player

import android.content.ComponentName
import android.content.Context
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import androidx.media3.session.MediaController
import androidx.media3.session.SessionToken
import kotlinx.coroutines.Job
import kotlinx.coroutines.MainScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.guava.await
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import vn.vigo.music.data.Library

data class PlayState(
    val currentId: String? = null,
    val isPlaying: Boolean = false,
    val positionMs: Long = 0,
    val durationMs: Long = 0,
    val shuffle: Boolean = false,
    val repeatMode: Int = Player.REPEAT_MODE_OFF,
    val queue: List<String> = emptyList(),
    val index: Int = -1,
    val error: String? = null,
)

/** Hẹn giờ tắt: [endAt] là thời điểm dừng (ms, 0 = không hẹn), hoặc dừng khi hết bài đang phát. */
data class SleepTimer(val endAt: Long = 0L, val endOfTrack: Boolean = false, val totalMs: Long = 0L) {
    val active get() = endAt > 0L || endOfTrack
}

/** Cầu nối giữa giao diện và PlaybackService (dùng trên luồng chính). */
object PlayerHub {
    private const val SLEEP_FADE_MS = 30_000L
    private var prefs: android.content.SharedPreferences? = null

    private val _sleep = MutableStateFlow(SleepTimer())
    val sleep: StateFlow<SleepTimer> = _sleep.asStateFlow()

    /** Chuyển bài mượt: số giây nhỏ dần cuối bài & lớn dần đầu bài sau (0 = tắt). */
    private val _crossfadeSec = MutableStateFlow(0)
    val crossfadeSec: StateFlow<Int> = _crossfadeSec.asStateFlow()
    private var fadeInStart = 0L

    private val scope = MainScope()
    private var controller: MediaController? = null
    private var connecting: Job? = null
    private var ticker: Job? = null
    private val pending = ArrayList<(MediaController) -> Unit>()

    private val _state = MutableStateFlow(PlayState())
    val state: StateFlow<PlayState> = _state.asStateFlow()

    fun connect(context: Context) {
        if (controller != null || connecting?.isActive == true) return
        val app = context.applicationContext
        if (prefs == null) {
            prefs = app.getSharedPreferences("vigo", Context.MODE_PRIVATE)
            _crossfadeSec.value = prefs?.getInt("crossfade", 0) ?: 0
        }
        connecting = scope.launch {
            try {
                val token = SessionToken(app, ComponentName(app, PlaybackService::class.java))
                val c = MediaController.Builder(app, token).buildAsync().await()
                controller = c
                c.addListener(object : Player.Listener {
                    override fun onEvents(player: Player, events: Player.Events) = refresh()
                    override fun onMediaItemTransition(mediaItem: MediaItem?, reason: Int) = onTransition(reason)
                    override fun onPlayerError(error: androidx.media3.common.PlaybackException) {
                        _state.value = _state.value.copy(error = "Không phát được bài này")
                    }
                })
                refresh()
                pending.toList().forEach { it(c) }
                pending.clear()
            } catch (e: Exception) {
                _state.value = _state.value.copy(error = "Không khởi động được trình phát")
            }
        }
    }

    private fun withController(block: (MediaController) -> Unit) {
        val c = controller
        if (c != null) block(c) else pending.add(block)
    }

    private fun refresh() {
        val c = controller ?: return
        val ids = (0 until c.mediaItemCount).map { c.getMediaItemAt(it).mediaId }
        _state.value = _state.value.copy(
            currentId = c.currentMediaItem?.mediaId,
            isPlaying = c.isPlaying,
            positionMs = c.currentPosition.coerceAtLeast(0),
            durationMs = c.duration.takeIf { it > 0 } ?: 0L,
            shuffle = c.shuffleModeEnabled,
            repeatMode = c.repeatMode,
            queue = ids,
            index = c.currentMediaItemIndex,
        )
        val sl = _sleep.value
        if (sl.endAt > 0 && System.currentTimeMillis() >= sl.endAt) _sleep.value = SleepTimer()
        if (c.isPlaying) startTicker() else {
            ticker?.cancel()
            if (c.volume < 1f) c.volume = 1f
            fadeInStart = 0L
        }
    }

    private fun onTransition(reason: Int) {
        val c = controller ?: return
        if (reason == Player.MEDIA_ITEM_TRANSITION_REASON_AUTO) {
            if (_sleep.value.endOfTrack) {
                // Hẹn giờ "hết bài này": dừng ngay khi sang bài mới
                c.pause(); c.seekTo(0)
                _sleep.value = SleepTimer()
                c.volume = 1f
                return
            }
            if (_crossfadeSec.value > 0) {
                fadeInStart = System.currentTimeMillis()
                c.volume = 0.05f
                return
            }
        }
        fadeInStart = 0L
        c.volume = 1f
    }

    /** Âm lượng theo hẹn giờ (nhỏ dần 30 giây cuối) và chuyển bài mượt; dừng khi hết giờ. */
    private fun updateVolume(c: MediaController) {
        val now = System.currentTimeMillis()
        var v = 1f
        val sl = _sleep.value
        if (sl.endAt > 0) {
            val rem = sl.endAt - now
            if (rem <= 0) {
                c.pause(); c.volume = 1f
                _sleep.value = SleepTimer()
                return
            }
            v = minOf(v, (rem.toFloat() / SLEEP_FADE_MS).coerceIn(0f, 1f))
        }
        val xf = _crossfadeSec.value * 1000L
        if (xf > 0) {
            val dur = c.duration
            if (dur > 0 && c.hasNextMediaItem() && c.repeatMode != Player.REPEAT_MODE_ONE && !_sleep.value.endOfTrack) {
                val left = dur - c.currentPosition
                if (left < xf) v = minOf(v, (left.toFloat() / xf).coerceIn(0.02f, 1f))
            }
            if (fadeInStart > 0) {
                val p = (now - fadeInStart).toFloat() / xf
                if (p >= 1f) fadeInStart = 0L else v = minOf(v, p.coerceIn(0.05f, 1f))
            }
        }
        if (kotlin.math.abs(c.volume - v) > 0.01f) c.volume = v
    }

    fun setSleepMinutes(min: Int) {
        _sleep.value = if (min <= 0) SleepTimer() else {
            val ms = min * 60_000L
            SleepTimer(endAt = System.currentTimeMillis() + ms, totalMs = ms)
        }
        controller?.let { if (!_sleep.value.active) it.volume = 1f }
    }

    fun setSleepEndOfTrack() { _sleep.value = SleepTimer(endOfTrack = true) }

    fun cancelSleep() {
        _sleep.value = SleepTimer()
        controller?.volume = 1f
    }

    fun setCrossfade(sec: Int) {
        _crossfadeSec.value = sec.coerceIn(0, 12)
        prefs?.edit()?.putInt("crossfade", _crossfadeSec.value)?.apply()
        if (sec == 0) { fadeInStart = 0L; controller?.volume = 1f }
    }

    private fun startTicker() {
        if (ticker?.isActive == true) return
        ticker = scope.launch {
            while (isActive) {
                controller?.let { c ->
                    _state.value = _state.value.copy(
                        positionMs = c.currentPosition.coerceAtLeast(0),
                        durationMs = c.duration.takeIf { it > 0 } ?: _state.value.durationMs,
                    )
                    updateVolume(c)
                }
                delay(100)
            }
        }
    }

    fun clearError() { _state.value = _state.value.copy(error = null) }

    fun playIds(ids: List<String>, startIndex: Int = 0, shuffle: Boolean = false) {
        val items: List<MediaItem> = ids.mapNotNull { Library.byId(it)?.let(MediaItems::build) }
        if (items.isEmpty()) return
        withController { c ->
            c.shuffleModeEnabled = shuffle
            c.setMediaItems(items, startIndex.coerceIn(0, items.lastIndex), 0L)
            c.prepare()
            c.play()
        }
    }

    fun togglePlay() = withController { c ->
        if (c.isPlaying) c.pause() else {
            if (c.playbackState == Player.STATE_IDLE) c.prepare()
            if (c.playbackState == Player.STATE_ENDED) c.seekTo(0)
            c.play()
        }
    }

    fun next() = withController { if (it.hasNextMediaItem()) it.seekToNextMediaItem() }

    fun previous() = withController { c ->
        // Quá 3 giây thì về đầu bài, ngược lại về bài trước
        if (c.currentPosition > 3000 || !c.hasPreviousMediaItem()) c.seekTo(0) else c.seekToPreviousMediaItem()
    }

    fun seekTo(ms: Long) = withController {
        it.seekTo(ms)
        _state.value = _state.value.copy(positionMs = ms)
    }

    fun toggleShuffle() = withController { it.shuffleModeEnabled = !it.shuffleModeEnabled }

    fun cycleRepeat() = withController {
        it.repeatMode = when (it.repeatMode) {
            Player.REPEAT_MODE_OFF -> Player.REPEAT_MODE_ALL
            Player.REPEAT_MODE_ALL -> Player.REPEAT_MODE_ONE
            else -> Player.REPEAT_MODE_OFF
        }
    }

    fun jumpTo(index: Int) = withController {
        if (index in 0 until it.mediaItemCount) { it.seekTo(index, 0); it.play() }
    }

    fun playNext(id: String) {
        val t = Library.byId(id) ?: return
        withController { c ->
            if (c.mediaItemCount == 0) { playIds(listOf(id)); return@withController }
            c.addMediaItem((c.currentMediaItemIndex + 1).coerceAtMost(c.mediaItemCount), MediaItems.build(t))
        }
    }

    fun addToQueue(id: String) {
        val t = Library.byId(id) ?: return
        withController { c ->
            if (c.mediaItemCount == 0) { playIds(listOf(id)); return@withController }
            c.addMediaItem(MediaItems.build(t))
        }
    }

    /** Gọi trước khi xóa bài khỏi thư viện để không phát file đã mất. */
    fun removeEverywhere(id: String) = withController { c ->
        for (i in c.mediaItemCount - 1 downTo 0) {
            if (c.getMediaItemAt(i).mediaId == id) c.removeMediaItem(i)
        }
    }

    /** Cập nhật tên hiển thị trên thông báo khi người dùng đổi tên bài. */
    fun refreshMetadata(id: String) {
        val t = Library.byId(id) ?: return
        withController { c ->
            for (i in 0 until c.mediaItemCount) {
                if (c.getMediaItemAt(i).mediaId == id) c.replaceMediaItem(i, MediaItems.build(t))
            }
        }
    }
}
