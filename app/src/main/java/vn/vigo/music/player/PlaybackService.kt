package vn.vigo.music.player

import android.app.PendingIntent
import android.content.Intent
import android.net.Uri
import androidx.media3.common.AudioAttributes
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.session.MediaSession
import androidx.media3.session.MediaSessionService
import com.google.common.util.concurrent.Futures
import com.google.common.util.concurrent.ListenableFuture
import vn.vigo.music.data.Library
import vn.vigo.music.data.Track

object MediaItems {
    fun build(t: Track): MediaItem = MediaItem.Builder()
        .setMediaId(t.id)
        .setUri(Uri.fromFile(Library.fileOf(t)))
        .setMediaMetadata(
            MediaMetadata.Builder()
                .setTitle(t.title)
                .setArtist(t.artist)
                .setAlbumTitle(t.album.ifBlank { null })
                .setArtworkUri(if (t.hasArt) Uri.fromFile(Library.artFileOf(t.id)).buildUpon().appendQueryParameter("v", t.artRev.toString()).build() else null)
                .setIsPlayable(true)
                .setIsBrowsable(false)
                .build()
        )
        .build()

    /** Dựng lại MediaItem đầy đủ từ id (URI có thể bị bỏ khi gửi qua MediaController). */
    fun resolve(item: MediaItem): MediaItem? =
        Library.byId(item.mediaId)?.let { build(it) } ?: item.takeIf { it.localConfiguration != null }
}

/** Dịch vụ phát nhạc nền: tiếp tục phát khi tắt màn hình, có điều khiển trên thông báo & màn hình khóa. */
class PlaybackService : MediaSessionService() {
    private var session: MediaSession? = null

    override fun onCreate() {
        super.onCreate()
        Library.init(this)
        val player = ExoPlayer.Builder(this, LevelRenderersFactory(this))
            .setAudioAttributes(
                AudioAttributes.Builder()
                    .setUsage(C.USAGE_MEDIA)
                    .setContentType(C.AUDIO_CONTENT_TYPE_MUSIC)
                    .build(),
                /* handleAudioFocus = */ true
            )
            .setHandleAudioBecomingNoisy(true) // rút tai nghe thì tạm dừng
            .setWakeMode(C.WAKE_MODE_LOCAL)
            .build()

        // Phiên âm thanh riêng để gắn bộ chỉnh âm (Equalizer / Bass)
        val am = getSystemService(AUDIO_SERVICE) as android.media.AudioManager
        val sessionId = am.generateAudioSessionId()
        player.audioSessionId = sessionId
        AudioFx.init(this)
        AudioFx.attach(sessionId)

        // Video đã nhập chỉ phát tiếng, bỏ giải mã hình cho nhẹ máy
        player.trackSelectionParameters = player.trackSelectionParameters.buildUpon()
            .setTrackTypeDisabled(C.TRACK_TYPE_VIDEO, true)
            .build()

        val launch = packageManager.getLaunchIntentForPackage(packageName) ?: Intent()
        val pi = PendingIntent.getActivity(
            this, 0, launch,
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )

        session = MediaSession.Builder(this, player)
            .setSessionActivity(pi)
            .setCallback(object : MediaSession.Callback {
                override fun onAddMediaItems(
                    mediaSession: MediaSession,
                    controller: MediaSession.ControllerInfo,
                    mediaItems: MutableList<MediaItem>
                ): ListenableFuture<MutableList<MediaItem>> =
                    Futures.immediateFuture(mediaItems.mapNotNull { MediaItems.resolve(it) }.toMutableList())
            })
            .build()
    }

    override fun onGetSession(controllerInfo: MediaSession.ControllerInfo): MediaSession? = session

    override fun onTaskRemoved(rootIntent: Intent?) {
        val p = session?.player
        if (p == null || !p.playWhenReady || p.mediaItemCount == 0) stopSelf()
    }

    override fun onDestroy() {
        AudioFx.release()
        session?.run {
            player.release()
            release()
        }
        session = null
        super.onDestroy()
    }
}
