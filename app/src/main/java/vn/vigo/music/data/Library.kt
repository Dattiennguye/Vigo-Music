package vn.vigo.music.data

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.util.LruCache
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.io.File

data class Track(
    val id: String,
    val title: String,
    val artist: String,
    val album: String,
    val durationMs: Long,
    val fileName: String,
    val addedAt: Long,
    val source: String, // "file" | "web"
    val hasArt: Boolean,
)

data class Playlist(
    val id: String,
    val name: String,
    val trackIds: List<String>,
)

/**
 * Thư viện nhạc lưu ngay trong bộ nhớ riêng của app:
 *  - files/music/<id>.<ext>   : file nhạc
 *  - files/art/<id>.jpg       : ảnh bìa trích từ file
 *  - files/library.json       : danh sách bài, yêu thích, playlist
 */
object Library {
    private lateinit var root: File
    private val lock = Any()

    private val _tracks = MutableStateFlow<List<Track>>(emptyList())
    val tracks: StateFlow<List<Track>> = _tracks.asStateFlow()

    private val _favorites = MutableStateFlow<Set<String>>(emptySet())
    val favorites: StateFlow<Set<String>> = _favorites.asStateFlow()

    private val _playlists = MutableStateFlow<List<Playlist>>(emptyList())
    val playlists: StateFlow<List<Playlist>> = _playlists.asStateFlow()

    private val artCache = object : LruCache<String, Bitmap>(24 * 1024 * 1024) {
        override fun sizeOf(key: String, value: Bitmap) = value.byteCount
    }

    @Volatile private var loaded = false

    fun init(context: Context) {
        if (loaded) return
        synchronized(lock) {
            if (loaded) return
            root = context.applicationContext.filesDir
            musicDir.mkdirs()
            artDir.mkdirs()
            load()
            loaded = true
        }
    }

    val musicDir: File get() = File(root, "music")
    val artDir: File get() = File(root, "art")
    private val dbFile: File get() = File(root, "library.json")

    fun fileOf(track: Track): File = File(musicDir, track.fileName)
    fun artFileOf(id: String): File = File(artDir, "$id.jpg")
    fun byId(id: String): Track? = _tracks.value.firstOrNull { it.id == id }

    suspend fun artwork(track: Track, maxPx: Int = 512): Bitmap? {
        if (!track.hasArt) return null
        val key = "${track.id}@$maxPx"
        artCache.get(key)?.let { return it }
        return withContext(Dispatchers.IO) {
            val f = artFileOf(track.id)
            if (!f.exists()) return@withContext null
            val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
            BitmapFactory.decodeFile(f.path, bounds)
            var sample = 1
            while (bounds.outWidth / (sample * 2) >= maxPx && bounds.outHeight / (sample * 2) >= maxPx) sample *= 2
            val bmp = BitmapFactory.decodeFile(f.path, BitmapFactory.Options().apply { inSampleSize = sample })
            if (bmp != null) artCache.put(key, bmp)
            bmp
        }
    }

    fun add(track: Track) {
        synchronized(lock) {
            _tracks.value = listOf(track) + _tracks.value.filterNot { it.id == track.id }
            save()
        }
    }

    fun rename(id: String, title: String, artist: String) {
        synchronized(lock) {
            _tracks.value = _tracks.value.map {
                if (it.id == id) it.copy(title = title.ifBlank { it.title }, artist = artist) else it
            }
            save()
        }
    }

    fun delete(id: String) {
        synchronized(lock) {
            val t = byId(id) ?: return
            fileOf(t).delete()
            artFileOf(id).delete()
            _tracks.value = _tracks.value.filterNot { it.id == id }
            _favorites.value = _favorites.value - id
            _playlists.value = _playlists.value.map { p -> p.copy(trackIds = p.trackIds - id) }
            save()
        }
    }

    fun toggleFavorite(id: String) {
        synchronized(lock) {
            val f = _favorites.value
            _favorites.value = if (id in f) f - id else f + id
            save()
        }
    }

    fun createPlaylist(name: String): Playlist {
        synchronized(lock) {
            val p = Playlist(newId(), name.trim().ifBlank { "Playlist mới" }, emptyList())
            _playlists.value = _playlists.value + p
            save()
            return p
        }
    }

    fun renamePlaylist(id: String, name: String) {
        synchronized(lock) {
            _playlists.value = _playlists.value.map { if (it.id == id) it.copy(name = name.trim().ifBlank { it.name }) else it }
            save()
        }
    }

    fun deletePlaylist(id: String) {
        synchronized(lock) {
            _playlists.value = _playlists.value.filterNot { it.id == id }
            save()
        }
    }

    fun addToPlaylist(playlistId: String, trackId: String) {
        synchronized(lock) {
            _playlists.value = _playlists.value.map {
                if (it.id == playlistId && trackId !in it.trackIds) it.copy(trackIds = it.trackIds + trackId) else it
            }
            save()
        }
    }

    fun removeFromPlaylist(playlistId: String, trackId: String) {
        synchronized(lock) {
            _playlists.value = _playlists.value.map {
                if (it.id == playlistId) it.copy(trackIds = it.trackIds - trackId) else it
            }
            save()
        }
    }

    fun newId(): String = System.currentTimeMillis().toString(36) + (1000..9999).random().toString(36)

    private fun load() {
        if (!dbFile.exists()) return
        try {
            val o = JSONObject(dbFile.readText())
            val arr = o.optJSONArray("tracks") ?: JSONArray()
            val list = ArrayList<Track>()
            for (i in 0 until arr.length()) {
                val t = arr.getJSONObject(i)
                val track = Track(
                    id = t.getString("id"),
                    title = t.optString("title"),
                    artist = t.optString("artist"),
                    album = t.optString("album"),
                    durationMs = t.optLong("durationMs"),
                    fileName = t.getString("fileName"),
                    addedAt = t.optLong("addedAt"),
                    source = t.optString("source", "file"),
                    hasArt = t.optBoolean("hasArt"),
                )
                // Bỏ những bài mà file đã mất
                if (File(musicDir, track.fileName).exists()) list.add(track)
            }
            _tracks.value = list
            val fav = o.optJSONArray("favorites") ?: JSONArray()
            _favorites.value = (0 until fav.length()).map { fav.getString(it) }.toSet()
            val pls = o.optJSONArray("playlists") ?: JSONArray()
            _playlists.value = (0 until pls.length()).map { i ->
                val p = pls.getJSONObject(i)
                val ids = p.optJSONArray("trackIds") ?: JSONArray()
                Playlist(p.getString("id"), p.optString("name"), (0 until ids.length()).map { ids.getString(it) })
            }
        } catch (_: Exception) {
            // File hỏng: giữ lại bản sao để không mất dữ liệu, bắt đầu thư viện trống
            dbFile.copyTo(File(root, "library.broken.json"), overwrite = true)
        }
    }

    private fun save() {
        val o = JSONObject()
        o.put("tracks", JSONArray().apply {
            _tracks.value.forEach { t ->
                put(JSONObject().apply {
                    put("id", t.id); put("title", t.title); put("artist", t.artist)
                    put("album", t.album); put("durationMs", t.durationMs)
                    put("fileName", t.fileName); put("addedAt", t.addedAt)
                    put("source", t.source); put("hasArt", t.hasArt)
                })
            }
        })
        o.put("favorites", JSONArray(_favorites.value.toList()))
        o.put("playlists", JSONArray().apply {
            _playlists.value.forEach { p ->
                put(JSONObject().apply {
                    put("id", p.id); put("name", p.name); put("trackIds", JSONArray(p.trackIds))
                })
            }
        })
        val tmp = File(root, "library.json.tmp")
        tmp.writeText(o.toString())
        if (!tmp.renameTo(dbFile)) {
            dbFile.writeText(o.toString()); tmp.delete()
        }
    }
}
