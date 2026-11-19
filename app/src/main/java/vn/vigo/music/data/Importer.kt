package vn.vigo.music.data

import android.content.Context
import android.graphics.BitmapFactory
import android.media.MediaMetadataRetriever
import android.net.Uri
import android.provider.OpenableColumns
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream

class NotAudioException(msg: String) : Exception(msg)

/** Nhập file nhạc vào thư viện: chép vào bộ nhớ app rồi đọc thông tin bài hát. */
object Importer {

    private val audioExt = setOf("mp3", "m4a", "aac", "wav", "flac", "ogg", "oga", "opus", "wma", "amr", "3gp", "mp4", "webm", "mka")

    fun isAudioExt(ext: String) = ext.lowercase() in audioExt

    suspend fun importUri(context: Context, uri: Uri): Track = withContext(Dispatchers.IO) {
        val cr = context.contentResolver
        var name = "Bài hát"
        cr.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)?.use { c ->
            if (c.moveToFirst()) c.getString(0)?.let { name = it }
        }
        if (name == "Bài hát") uri.lastPathSegment?.substringAfterLast('/')?.let { if (it.isNotBlank()) name = it }
        val ext = name.substringAfterLast('.', "").lowercase().ifBlank {
            extFromMime(cr.getType(uri)) ?: "mp3"
        }
        val id = Library.newId()
        val out = File(Library.musicDir, "$id.$ext")
        cr.openInputStream(uri).use { input ->
            requireNotNull(input) { "Không mở được file" }
            FileOutputStream(out).use { input.copyTo(it, 64 * 1024) }
        }
        finalize(id, out, name.substringBeforeLast('.'), "file")
    }

    /** Đọc metadata, trích ảnh bìa, thêm vào thư viện. Xóa file nếu không phải nhạc. */
    fun finalize(id: String, file: File, fallbackTitle: String, source: String): Track {
        val r = MediaMetadataRetriever()
        try {
            try {
                r.setDataSource(file.path)
            } catch (e: Exception) {
                file.delete()
                throw NotAudioException("File không phải nhạc hoặc bị hỏng")
            }
            val hasAudio = r.extractMetadata(MediaMetadataRetriever.METADATA_KEY_HAS_AUDIO) == "yes"
            val duration = r.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION)?.toLongOrNull() ?: 0L
            if (!hasAudio && duration <= 0L) {
                file.delete()
                throw NotAudioException("File không chứa âm thanh")
            }
            val title = r.extractMetadata(MediaMetadataRetriever.METADATA_KEY_TITLE)?.trim().orEmpty()
            val artist = (r.extractMetadata(MediaMetadataRetriever.METADATA_KEY_ARTIST)
                ?: r.extractMetadata(MediaMetadataRetriever.METADATA_KEY_ALBUMARTIST))?.trim().orEmpty()
            val album = r.extractMetadata(MediaMetadataRetriever.METADATA_KEY_ALBUM)?.trim().orEmpty()
            var hasArt = false
            r.embeddedPicture?.let { bytes ->
                // Kiểm tra ảnh giải mã được rồi mới lưu
                val opts = BitmapFactory.Options().apply { inJustDecodeBounds = true }
                BitmapFactory.decodeByteArray(bytes, 0, bytes.size, opts)
                if (opts.outWidth > 0 && opts.outHeight > 0) {
                    Library.artFileOf(id).writeBytes(bytes)
                    hasArt = true
                }
            }
            val cleanFallback = fallbackTitle.replace('_', ' ').replace(Regex("\\s+"), " ").trim()
            val track = Track(
                id = id,
                title = title.ifBlank { cleanFallback.ifBlank { "Bài hát" } },
                artist = artist.ifBlank { "Không rõ nghệ sĩ" },
                album = album,
                durationMs = duration,
                fileName = file.name,
                addedAt = System.currentTimeMillis(),
                source = source,
                hasArt = hasArt,
            )
            Library.add(track)
            return track
        } finally {
            try { r.release() } catch (_: Exception) {}
        }
    }

    fun extFromMime(mime: String?): String? = when (mime?.lowercase()?.substringBefore(';')?.trim()) {
        "audio/mpeg", "audio/mp3" -> "mp3"
        "audio/mp4", "audio/x-m4a", "audio/m4a", "audio/aac" -> "m4a"
        "audio/wav", "audio/x-wav", "audio/wave" -> "wav"
        "audio/flac", "audio/x-flac" -> "flac"
        "audio/ogg", "application/ogg" -> "ogg"
        "audio/opus" -> "opus"
        "audio/webm" -> "webm"
        "audio/amr" -> "amr"
        else -> null
    }
}
