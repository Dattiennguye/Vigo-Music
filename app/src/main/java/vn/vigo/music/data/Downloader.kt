package vn.vigo.music.data

import android.webkit.CookieManager
import android.webkit.URLUtil
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.io.File
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLDecoder

enum class DlState { Running, Done, Failed }

data class DownloadTask(
    val id: String,
    val name: String,
    val url: String,
    val received: Long = 0,
    val total: Long = -1,
    val state: DlState = DlState.Running,
    val message: String = "",
    val trackId: String? = null,
) {
    val progress: Float get() = if (total > 0) (received.toFloat() / total).coerceIn(0f, 1f) else -1f
}

/** Tải file nhạc từ link trực tiếp (bấm từ trình duyệt trong app) và lưu vào thư viện. */
object Downloader {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val jobs = java.util.concurrent.ConcurrentHashMap<String, Job>()
    private val cancelled = java.util.concurrent.ConcurrentHashMap.newKeySet<String>()

    private val _tasks = MutableStateFlow<List<DownloadTask>>(emptyList())
    val tasks: StateFlow<List<DownloadTask>> = _tasks.asStateFlow()

    fun isLikelyAudioUrl(url: String?): Boolean {
        if (url.isNullOrBlank()) return false
        val path = url.substringBefore('?').substringBefore('#')
        val ext = path.substringAfterLast('.', "")
        return ext.length in 2..4 && Importer.isAudioExt(ext)
    }

    fun start(url: String, userAgent: String?, contentDisposition: String?, mime: String?): String {
        val guessed = URLUtil.guessFileName(url, contentDisposition, mime)
        val name = try { URLDecoder.decode(guessed, "UTF-8") } catch (_: Exception) { guessed }
        val id = Library.newId()
        _tasks.update { listOf(DownloadTask(id, name, url)) + it }
        val job = scope.launch(start = kotlinx.coroutines.CoroutineStart.LAZY) { run(id, url, userAgent, name, mime) }
        jobs[id] = job
        job.start()
        return id
    }

    fun cancel(id: String) {
        cancelled.add(id)
        jobs.remove(id)?.cancel()
        update(id) { it.copy(state = DlState.Failed, message = "Đã hủy") }
    }

    fun dismiss(id: String) {
        _tasks.update { list -> list.filterNot { it.id == id } }
    }

    fun clearFinished() {
        _tasks.update { list -> list.filter { it.state == DlState.Running } }
    }

    private fun update(id: String, f: (DownloadTask) -> DownloadTask) {
        _tasks.update { list -> list.map { if (it.id == id) f(it) else it } }
    }

    private fun run(id: String, startUrl: String, ua: String?, name: String, hintMime: String?) {
        val tmp = File(Library.musicDir, "$id.part")
        try {
            var url = URL(startUrl)
            var conn: HttpURLConnection
            var hops = 0
            while (true) {
                conn = (url.openConnection() as HttpURLConnection).apply {
                    instanceFollowRedirects = false
                    connectTimeout = 20_000
                    readTimeout = 30_000
                    setRequestProperty("User-Agent", ua ?: "Mozilla/5.0 (Linux; Android) VGMusic")
                    CookieManager.getInstance().getCookie(url.toString())?.let { setRequestProperty("Cookie", it) }
                    setRequestProperty("Accept", "audio/*,*/*;q=0.8")
                }
                val code = conn.responseCode
                if (code in 300..399) {
                    val loc = conn.getHeaderField("Location") ?: break
                    conn.disconnect()
                    url = URL(url, loc)
                    if (++hops > 8) throw Exception("Chuyển hướng quá nhiều lần")
                    continue
                }
                if (code !in 200..299) throw Exception("Máy chủ trả lỗi $code")
                break
            }
            val ctype = conn.contentType?.lowercase().orEmpty()
            if (ctype.startsWith("text/html") || ctype.startsWith("application/json")) {
                conn.disconnect()
                throw NotAudioException("Đây là trang web, không phải file nhạc. Hãy bấm vào nút/link tải file .mp3 trực tiếp.")
            }
            val total = conn.contentLengthLong
            update(id) { it.copy(total = total) }
            var received = 0L
            var lastEmit = 0L
            conn.inputStream.use { input ->
                tmp.outputStream().use { out ->
                    val buf = ByteArray(64 * 1024)
                    while (true) {
                        if (id in cancelled) throw InterruptedException()
                        val n = input.read(buf)
                        if (n < 0) break
                        out.write(buf, 0, n)
                        received += n
                        val now = System.currentTimeMillis()
                        if (now - lastEmit > 150) {
                            lastEmit = now
                            val r = received
                            update(id) { it.copy(received = r) }
                        }
                    }
                }
            }
            conn.disconnect()
            if (id in cancelled) { tmp.delete(); return } // đã hủy

            val nameExt = name.substringAfterLast('.', "")
            val ext = when {
                Importer.isAudioExt(nameExt) -> nameExt.lowercase()
                else -> Importer.extFromMime(ctype) ?: Importer.extFromMime(hintMime) ?: "mp3"
            }
            val finalFile = File(Library.musicDir, "$id.$ext")
            if (!tmp.renameTo(finalFile)) { tmp.copyTo(finalFile, true); tmp.delete() }
            val title = if (Importer.isAudioExt(nameExt)) name.substringBeforeLast('.') else name
            val track = Importer.finalize(id, finalFile, title, "web")
            update(id) { it.copy(received = received, total = received, state = DlState.Done, message = "Đã lưu vào thư viện", trackId = track.id) }
        } catch (e: InterruptedException) {
            tmp.delete()
        } catch (e: kotlinx.coroutines.CancellationException) {
            tmp.delete()
        } catch (e: Exception) {
            tmp.delete()
            val msg = when (e) {
                is NotAudioException -> e.message.orEmpty()
                is java.net.UnknownHostException -> "Không có mạng hoặc sai địa chỉ"
                is java.net.SocketTimeoutException -> "Hết thời gian chờ, thử lại sau"
                else -> e.message ?: "Tải thất bại"
            }
            update(id) { it.copy(state = DlState.Failed, message = msg) }
        } finally {
            jobs.remove(id)
            cancelled.remove(id)
        }
    }
}
