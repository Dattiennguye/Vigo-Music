package vn.vigo.music.data

import android.content.Context
import android.graphics.Bitmap
import android.media.MediaMetadataRetriever
import android.net.Uri
import androidx.annotation.OptIn
import androidx.media3.common.MediaItem
import androidx.media3.common.util.UnstableApi
import androidx.media3.transformer.Composition
import androidx.media3.transformer.EditedMediaItem
import androidx.media3.transformer.ExportException
import androidx.media3.transformer.ExportResult
import androidx.media3.transformer.ProgressHolder
import androidx.media3.transformer.Transformer
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import java.io.File
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

/**
 * Lấy tiếng từ video trong máy: bỏ hình, chỉ giữ âm thanh (file .m4a nhẹ hơn rất nhiều),
 * kèm một khung hình làm ảnh bìa. Dùng Media3 Transformer, chạy ngay trên máy, không cần mạng.
 */
@OptIn(UnstableApi::class)
object VideoAudio {

    /** Tách âm thanh ra [out]. [onProgress] nhận 0..100 khi biết tiến độ. */
    suspend fun extract(context: Context, uri: Uri, out: File, onProgress: (Int) -> Unit) = withContext(Dispatchers.Main) {
        coroutineScope {
            var transformer: Transformer? = null
            val poller = launch {
                val holder = ProgressHolder()
                while (isActive) {
                    delay(250)
                    val t = transformer ?: continue
                    if (t.getProgress(holder) == Transformer.PROGRESS_STATE_AVAILABLE) onProgress(holder.progress)
                }
            }
            try {
                suspendCancellableCoroutine<Unit> { cont ->
                    val t = Transformer.Builder(context.applicationContext)
                        .addListener(object : Transformer.Listener {
                            override fun onCompleted(composition: Composition, exportResult: ExportResult) {
                                if (cont.isActive) cont.resume(Unit)
                            }

                            override fun onError(composition: Composition, exportResult: ExportResult, exportException: ExportException) {
                                if (cont.isActive) cont.resumeWithException(exportException)
                            }
                        })
                        .build()
                    transformer = t
                    cont.invokeOnCancellation { try { t.cancel() } catch (_: Exception) {} }
                    val item = EditedMediaItem.Builder(MediaItem.fromUri(uri)).setRemoveVideo(true).build()
                    t.start(item, out.path)
                }
            } finally {
                poller.cancel()
            }
        }
    }

    /** Một khung hình ở khoảng 1/3 video làm ảnh bìa. */
    fun frameOf(context: Context, uri: Uri): Bitmap? {
        val r = MediaMetadataRetriever()
        return try {
            r.setDataSource(context, uri)
            val dur = r.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION)?.toLongOrNull() ?: 0L
            val atUs = if (dur > 0) dur * 1000 / 3 else 1_000_000L
            val f = r.getFrameAtTime(atUs, MediaMetadataRetriever.OPTION_CLOSEST_SYNC) ?: return null
            // thu nhỏ để ảnh bìa không quá nặng
            val maxSide = 1024
            val scale = maxSide.toFloat() / maxOf(f.width, f.height)
            if (scale < 1f) Bitmap.createScaledBitmap(f, (f.width * scale).toInt(), (f.height * scale).toInt(), true) else f
        } catch (_: Exception) {
            null
        } finally {
            try { r.release() } catch (_: Exception) {}
        }
    }
}
