package vn.vigo.music.data

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.io.RandomAccessFile
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder
import java.nio.charset.Charset

data class LyricLine(val timeMs: Long, val text: String)

data class Lyrics(val lines: List<LyricLine>, val synced: Boolean, val source: String) {
    /** Chỉ số dòng đang hát tại vị trí [posMs] (-1 nếu chưa tới dòng đầu). */
    fun indexAt(posMs: Long): Int {
        if (!synced || lines.isEmpty()) return -1
        var lo = 0
        var hi = lines.lastIndex
        var ans = -1
        while (lo <= hi) {
            val mid = (lo + hi) / 2
            if (lines[mid].timeMs <= posMs) { ans = mid; lo = mid + 1 } else hi = mid - 1
        }
        return ans
    }
}

/**
 * Lời bài hát: lấy theo thứ tự
 *  1. bản đã lưu trong app (files/lyrics/<id>.lrc)
 *  2. lời nhúng sẵn trong file mp3 (thẻ ID3 USLT)
 *  3. tìm trên LRCLIB (kho lời miễn phí, có lời chạy theo thời gian) khi người dùng bấm tìm
 *  4. người dùng dán lời hoặc nhập file .lrc
 */
object LyricsRepo {
    private fun dir() = File(Library.musicDir.parentFile, "lyrics").apply { mkdirs() }
    private fun cacheFile(id: String) = File(dir(), "$id.lrc")
    private fun missFile(id: String) = File(dir(), "$id.none")

    suspend fun load(track: Track): Lyrics? = withContext(Dispatchers.IO) {
        val f = cacheFile(track.id)
        if (f.exists()) return@withContext parse(f.readText(), sourceOf(f))
        val embedded = try { readId3Lyrics(Library.fileOf(track)) } catch (_: Exception) { null }
        if (!embedded.isNullOrBlank()) {
            save(track.id, embedded, "Lời trong file")
            return@withContext parse(embedded, "Lời trong file")
        }
        null
    }

    /** Lần trước đã tìm trên mạng mà không có. */
    fun knownMissing(id: String) = missFile(id).exists()

    fun save(id: String, text: String, source: String) {
        cacheFile(id).writeText("[source:$source]\n" + text.trim())
        missFile(id).delete()
    }

    fun delete(id: String) {
        cacheFile(id).delete(); missFile(id).delete()
    }

    private fun sourceOf(f: File): String =
        f.useLines { seq -> seq.firstOrNull()?.let { Regex("^\\[source:(.*)]$").find(it.trim())?.groupValues?.get(1) } } ?: "Đã lưu"

    // ---------- Phân tích LRC ----------

    private val timeTag = Regex("\\[(\\d{1,3}):(\\d{1,2})(?:[.:](\\d{1,3}))?]")
    private val metaTag = Regex("^\\[(ar|ti|al|by|length|re|ve|source|au|#):.*]$", RegexOption.IGNORE_CASE)
    private val offsetTag = Regex("^\\[offset:\\s*([+-]?\\d+)\\s*]$", RegexOption.IGNORE_CASE)

    fun parse(text: String, source: String): Lyrics? {
        val raw = text.replace("\r\n", "\n").replace('\r', '\n').lines()
        var offset = 0L
        val timed = ArrayList<LyricLine>()
        val plain = ArrayList<String>()
        for (line0 in raw) {
            val line = line0.trim()
            val off = offsetTag.find(line)
            if (off != null) { offset = off.groupValues[1].toLongOrNull() ?: 0L; continue }
            if (metaTag.matches(line)) continue
            val tags = timeTag.findAll(line).toList()
            if (tags.isNotEmpty() && line.startsWith("[")) {
                val lyric = line.substring(tags.last().range.last + 1)
                    .replace(Regex("<\\d{1,2}:\\d{1,2}(?:[.:]\\d{1,3})?>"), "") // bỏ thẻ karaoke từng chữ
                    .trim()
                for (t in tags) {
                    val m = t.groupValues[1].toLong()
                    val s = t.groupValues[2].toLong()
                    val fr = t.groupValues[3]
                    val ms = when (fr.length) { 0 -> 0L; 1 -> fr.toLong() * 100; 2 -> fr.toLong() * 10; else -> fr.take(3).toLong() }
                    timed.add(LyricLine((m * 60_000 + s * 1000 + ms - offset).coerceAtLeast(0), lyric))
                }
            } else {
                plain.add(line)
            }
        }
        if (timed.size >= 3) {
            val sorted = timed.sortedBy { it.timeMs }
            // gộp các dòng trống liên tiếp, giữ 1 dòng trống làm khoảng nghỉ
            val out = ArrayList<LyricLine>()
            for (l in sorted) if (!(l.text.isEmpty() && out.lastOrNull()?.text?.isEmpty() == true)) out.add(l)
            return Lyrics(out, true, source)
        }
        val lines = plain.dropWhile { it.isEmpty() }.dropLastWhile { it.isEmpty() }
        if (lines.isEmpty()) return null
        return Lyrics(lines.map { LyricLine(0, it) }, false, source)
    }

    // ---------- LRCLIB ----------

    suspend fun fetchOnline(track: Track): Lyrics? = withContext(Dispatchers.IO) {
        val title = cleanTitle(track.title)
        val artist = track.artist.takeUnless { it == "Không rõ nghệ sĩ" }.orEmpty()
        var best: JSONObject? = null
        if (artist.isNotBlank()) {
            val q = "track_name=${enc(title)}&artist_name=${enc(artist)}" +
                (if (track.durationMs > 0) "&duration=${track.durationMs / 1000}" else "")
            best = getJson("https://lrclib.net/api/get?$q") as? JSONObject
        }
        if (best == null || (best.optString("syncedLyrics").isBlank() && best.optString("plainLyrics").isBlank())) {
            val arr = getJson("https://lrclib.net/api/search?q=${enc("$title $artist".trim())}") as? JSONArray
            if (arr != null && arr.length() > 0) {
                val dur = track.durationMs / 1000.0
                val items = (0 until arr.length()).map { arr.getJSONObject(it) }
                best = items.sortedWith(
                    compareByDescending<JSONObject> { it.optString("syncedLyrics").isNotBlank() }
                        .thenBy { if (dur > 0) kotlin.math.abs(it.optDouble("duration", 0.0) - dur) else 0.0 }
                ).firstOrNull()
            }
        }
        val synced = best?.optString("syncedLyrics").orEmpty().takeUnless { it == "null" }.orEmpty()
        val plain = best?.optString("plainLyrics").orEmpty().takeUnless { it == "null" }.orEmpty()
        val text = synced.ifBlank { plain }
        if (text.isBlank()) {
            missFile(track.id).writeText("")
            return@withContext null
        }
        save(track.id, text, "LRCLIB")
        parse(text, "LRCLIB")
    }

    private fun cleanTitle(t: String) = t
        .replace(Regex("\\s*[(\\[].*?(official|lyric|mv|audio|video|remix|ver|version|live|karaoke).*?[)\\]]", RegexOption.IGNORE_CASE), "")
        .replace(Regex("\\s+"), " ")
        .trim()

    private fun enc(s: String) = URLEncoder.encode(s, "UTF-8")

    private fun getJson(url: String): Any? {
        val c = (URL(url).openConnection() as HttpURLConnection).apply {
            connectTimeout = 12_000; readTimeout = 15_000
            setRequestProperty("User-Agent", "VigoMusic/1.0 (https://github.com/Dattiennguye/Vigo-Music)")
        }
        try {
            if (c.responseCode == 404) return null
            if (c.responseCode !in 200..299) throw Exception("Máy chủ lời bài hát lỗi ${c.responseCode}")
            val body = c.inputStream.bufferedReader().readText().trim()
            return if (body.startsWith("[")) JSONArray(body) else JSONObject(body)
        } finally {
            c.disconnect()
        }
    }

    // ---------- Đọc lời nhúng trong thẻ ID3v2 (mp3) ----------

    fun readId3Lyrics(file: File): String? {
        RandomAccessFile(file, "r").use { raf ->
            val head = ByteArray(10)
            if (raf.read(head) != 10) return null
            if (head[0] != 'I'.code.toByte() || head[1] != 'D'.code.toByte() || head[2] != '3'.code.toByte()) return null
            val ver = head[3].toInt()
            if (ver !in 3..4) return null
            val flags = head[5].toInt()
            val size = syncsafe(head, 6)
            if (size <= 0 || size > 20_000_000) return null
            var tag = ByteArray(size)
            raf.readFully(tag)
            if (flags and 0x80 != 0 && ver == 3) tag = deUnsync(tag)
            var p = 0
            if (flags and 0x40 != 0) { // extended header
                p += if (ver == 4) syncsafe(tag, 0) else (int32(tag, 0) + 4)
            }
            while (p + 10 <= tag.size) {
                val id = String(tag, p, 4, Charsets.ISO_8859_1)
                if (id[0] == '\u0000') break
                val fsize = if (ver == 4) syncsafe(tag, p + 4) else int32(tag, p + 4)
                val start = p + 10
                if (fsize <= 0 || start + fsize > tag.size) break
                if (id == "USLT") {
                    var data = tag.copyOfRange(start, start + fsize)
                    if (ver == 4 && (tag[p + 9].toInt() and 0x02) != 0) data = deUnsync(data)
                    decodeUslt(data)?.let { if (it.isNotBlank()) return it }
                }
                p = start + fsize
            }
        }
        return null
    }

    private fun decodeUslt(d: ByteArray): String? {
        if (d.size < 5) return null
        val enc = d[0].toInt()
        val cs: Charset = when (enc) {
            0 -> Charsets.ISO_8859_1
            1 -> Charsets.UTF_16
            2 -> Charsets.UTF_16BE
            else -> Charsets.UTF_8
        }
        var i = 4 // bỏ mã hóa + 3 byte ngôn ngữ
        // bỏ phần mô tả (kết thúc bằng 0 hoặc 00 00)
        if (enc == 1 || enc == 2) {
            while (i + 1 < d.size && !(d[i].toInt() == 0 && d[i + 1].toInt() == 0)) i += 2
            i += 2
        } else {
            while (i < d.size && d[i].toInt() != 0) i++
            i += 1
        }
        if (i >= d.size) return null
        return String(d, i, d.size - i, cs).trimEnd('\u0000').trim()
    }

    private fun syncsafe(b: ByteArray, o: Int) =
        ((b[o].toInt() and 0x7f) shl 21) or ((b[o + 1].toInt() and 0x7f) shl 14) or
            ((b[o + 2].toInt() and 0x7f) shl 7) or (b[o + 3].toInt() and 0x7f)

    private fun int32(b: ByteArray, o: Int) =
        ((b[o].toInt() and 0xff) shl 24) or ((b[o + 1].toInt() and 0xff) shl 16) or
            ((b[o + 2].toInt() and 0xff) shl 8) or (b[o + 3].toInt() and 0xff)

    private fun deUnsync(b: ByteArray): ByteArray {
        val out = java.io.ByteArrayOutputStream(b.size)
        var i = 0
        while (i < b.size) {
            out.write(b[i].toInt())
            if ((b[i].toInt() and 0xff) == 0xff && i + 1 < b.size && b[i + 1].toInt() == 0) i++
            i++
        }
        return out.toByteArray()
    }
}
