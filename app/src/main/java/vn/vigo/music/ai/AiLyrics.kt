package vn.vigo.music.ai

import android.content.Context
import android.media.AudioFormat
import android.media.MediaCodec
import android.media.MediaExtractor
import android.media.MediaFormat
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import vn.vigo.music.data.Library
import vn.vigo.music.data.LyricLine
import vn.vigo.music.data.Lyrics
import vn.vigo.music.data.LyricsRepo
import vn.vigo.music.data.Track
import java.io.File
import java.net.HttpURLConnection
import java.net.URL
import java.nio.ByteOrder

/** Cầu nối tới whisper.cpp (thư viện C biên dịch kèm app). */
object WhisperNative {
    init { System.loadLibrary("vigowhisper") }

    fun interface ProgressListener { fun onProgress(percent: Int) }

    @JvmStatic external fun init(modelPath: String): Long
    @JvmStatic external fun free(ctx: Long)
    @JvmStatic external fun abort()
    @JvmStatic external fun transcribe(
        ctx: Long, audio: FloatArray, threads: Int, language: String?, prompt: String?, maxLen: Int, listener: ProgressListener?,
    ): Int
    @JvmStatic external fun segmentCount(ctx: Long): Int
    @JvmStatic external fun segmentBytes(ctx: Long, i: Int): ByteArray
    @JvmStatic external fun segmentStart(ctx: Long, i: Int): Long
    @JvmStatic external fun segmentEnd(ctx: Long, i: Int): Long
}

/** Các mô hình Whisper (bản nén q5) tải từ kho chính thức của whisper.cpp trên Hugging Face. */
enum class AiModel(val label: String, val file: String, val sizeMb: Int, val note: String) {
    Fast("Nhanh", "ggml-base-q5_1.bin", 60, "Nhẹ, chạy nhanh, lời có thể sai nhiều hơn"),
    Balanced("Cân bằng", "ggml-small-q5_1.bin", 190, "Khuyên dùng cho tiếng Việt"),
    Accurate("Chính xác nhất", "ggml-large-v3-turbo-q5_0.bin", 575, "Chậm hơn nhiều, nóng máy, lời đúng nhất");

    val url get() = "https://huggingface.co/ggerganov/whisper.cpp/resolve/main/$file"
}

enum class AiLang(val label: String, val code: String) { Vi("Tiếng Việt", "vi"), Auto("Tự nhận diện", "auto"), En("English", "en") }

data class AiJob(
    val trackId: String,
    val stage: String,
    val percent: Int = -1,
    val running: Boolean = true,
    val error: String? = null,
)

/**
 * Lời AI chạy hoàn toàn trên máy:
 *  1. tải mô hình (một lần), 2. giải mã bài nhạc về 16 kHz mono,
 *  3. whisper.cpp nhận dạng giọng hát, 4. dọn lời (bỏ lặp, bỏ câu "ảo"), lưu thành LRC có mốc thời gian.
 */
object AiLyrics {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private var job: Job? = null
    @Volatile private var cancelled = false

    private val _job = MutableStateFlow<AiJob?>(null)
    val state: StateFlow<AiJob?> = _job.asStateFlow()

    /** Lời vừa tạo xong (để màn Lời cập nhật ngay). */
    private val _done = MutableStateFlow<Pair<String, Lyrics>?>(null)
    val done: StateFlow<Pair<String, Lyrics>?> = _done.asStateFlow()

    private fun modelDir(ctx: Context) = File(ctx.filesDir, "models").apply { mkdirs() }
    fun modelFile(ctx: Context, m: AiModel) = File(modelDir(ctx), m.file)
    fun isDownloaded(ctx: Context, m: AiModel) = modelFile(ctx, m).let { it.exists() && it.length() > m.sizeMb * 1024L * 1024 / 2 }
    fun deleteModel(ctx: Context, m: AiModel) { modelFile(ctx, m).delete() }

    fun clearError() { if (_job.value?.running == false) _job.value = null }

    fun cancel() {
        cancelled = true
        try { WhisperNative.abort() } catch (_: Throwable) {}
        job?.cancel()
        _job.value = null
    }

    fun start(context: Context, track: Track, model: AiModel, lang: AiLang) {
        if (_job.value?.running == true) return
        cancelled = false
        val app = context.applicationContext
        _job.value = AiJob(track.id, "Chuẩn bị…")
        job = scope.launch {
            var ctx = 0L
            try {
                // 1. Mô hình
                val mf = modelFile(app, model)
                if (!isDownloaded(app, model)) download(model, mf, track.id)
                check(!cancelled)

                // 2. Giải mã nhạc
                set(track.id, "Đang đọc bài nhạc…", 0)
                val audio = decode16kMono(Library.fileOf(track)) { p -> set(track.id, "Đang đọc bài nhạc…", (p * 100).toInt()) }
                check(!cancelled)
                if (audio.size < 16000) throw IllegalStateException("Bài quá ngắn để nhận dạng")

                // 3. Nhận dạng
                set(track.id, "Đang nạp mô hình AI…", -1)
                ctx = WhisperNative.init(mf.path)
                if (ctx == 0L) { mf.delete(); throw IllegalStateException("Mô hình bị hỏng, hãy tải lại") }
                check(!cancelled)
                set(track.id, "AI đang nghe & viết lời…", 0)
                val threads = Runtime.getRuntime().availableProcessors().let { (it - 4).coerceIn(2, 4).coerceAtLeast(minOf(4, it / 2)) }
                val prompt = when (lang) {
                    AiLang.Vi -> "Lời bài hát tiếng Việt, viết đúng chính tả, có dấu."
                    AiLang.En -> "Song lyrics."
                    AiLang.Auto -> null
                }
                val r = WhisperNative.transcribe(ctx, audio, threads, lang.code, prompt, 42) { p ->
                    set(track.id, "AI đang nghe & viết lời…", p)
                }
                if (r == -1 || cancelled) return@launch
                if (r != 0) throw IllegalStateException("AI nhận dạng lỗi (mã $r)")

                // 4. Dọn lời
                val n = WhisperNative.segmentCount(ctx)
                val raw = (0 until n).map { i ->
                    val text = String(WhisperNative.segmentBytes(ctx, i), Charsets.UTF_8).replace("�", "")
                    Triple(WhisperNative.segmentStart(ctx, i), WhisperNative.segmentEnd(ctx, i), text)
                }
                val lines = clean(raw)
                if (lines.size < 2) throw IllegalStateException("AI không nghe ra lời (có thể bài không lời hoặc tiếng hát bị nhạc lấn át)")
                val lrc = lines.joinToString("\n") { "[${ts(it.timeMs)}]${it.text}" }
                LyricsRepo.save(track.id, lrc, "AI · ${model.label}")
                val parsed = LyricsRepo.parse(lrc, "AI · ${model.label}") ?: throw IllegalStateException("Không tạo được lời")
                _done.value = track.id to parsed
                _job.value = null
            } catch (e: Throwable) {
                if (cancelled || e is kotlinx.coroutines.CancellationException) {
                    _job.value = null
                } else {
                    _job.value = AiJob(track.id, "", running = false, error = friendly(e))
                }
            } finally {
                if (ctx != 0L) WhisperNative.free(ctx)
            }
        }
    }

    private fun set(id: String, stage: String, pct: Int) {
        if (!cancelled) _job.value = AiJob(id, stage, pct)
    }

    private fun friendly(e: Throwable): String = when (e) {
        is java.net.UnknownHostException -> "Không có mạng để tải mô hình AI (chỉ cần mạng lần đầu)"
        is java.net.SocketTimeoutException -> "Mạng chậm, tải mô hình bị gián đoạn. Thử lại nhé"
        is OutOfMemoryError -> "Không đủ bộ nhớ. Hãy dùng mô hình nhẹ hơn"
        is UnsatisfiedLinkError, is ExceptionInInitializerError, is NoClassDefFoundError -> "Máy không chạy được bộ AI (cần chip ARM 64-bit)"
        else -> e.message ?: "Có lỗi khi tạo lời"
    }

    // ---------- Tải mô hình ----------

    private fun download(model: AiModel, dest: File, trackId: String) {
        val part = File(dest.path + ".part")
        var url = URL(model.url)
        var conn: HttpURLConnection
        var hops = 0
        while (true) {
            conn = (url.openConnection() as HttpURLConnection).apply {
                instanceFollowRedirects = false
                connectTimeout = 20_000; readTimeout = 30_000
                setRequestProperty("User-Agent", "VigoMusic/1.0")
                if (part.exists() && part.length() > 0) setRequestProperty("Range", "bytes=${part.length()}-")
            }
            val code = conn.responseCode
            if (code in 300..399) {
                val loc = conn.getHeaderField("Location") ?: break
                conn.disconnect(); url = URL(url, loc)
                if (++hops > 8) throw IllegalStateException("Chuyển hướng quá nhiều lần")
                continue
            }
            if (code !in 200..299) throw IllegalStateException("Không tải được mô hình (lỗi $code)")
            break
        }
        val resume = conn.responseCode == 206
        if (!resume) part.delete()
        val already = if (resume) part.length() else 0L
        val total = conn.contentLengthLong.let { if (it > 0) it + already else model.sizeMb * 1024L * 1024 }
        var got = already
        var last = 0L
        conn.inputStream.use { input ->
            java.io.FileOutputStream(part, resume).use { out ->
                val buf = ByteArray(256 * 1024)
                while (true) {
                    if (cancelled) throw kotlinx.coroutines.CancellationException()
                    val n = input.read(buf)
                    if (n < 0) break
                    out.write(buf, 0, n)
                    got += n
                    val now = System.currentTimeMillis()
                    if (now - last > 200) {
                        last = now
                        set(trackId, "Đang tải mô hình AI ${got / 1048576}/${total / 1048576} MB (chỉ lần đầu)…", (got * 100 / total).toInt())
                    }
                }
            }
        }
        conn.disconnect()
        if (!part.renameTo(dest)) { part.copyTo(dest, true); part.delete() }
    }

    // ---------- Giải mã nhạc → 16 kHz mono float ----------

    private class FloatBuf {
        var a = FloatArray(1 shl 20)
        var n = 0
        fun add(v: Float) {
            if (n == a.size) a = a.copyOf(a.size * 2)
            a[n++] = v
        }
        fun result() = a.copyOf(n)
    }

    fun decode16kMono(file: File, onProgress: (Float) -> Unit): FloatArray {
        val ex = MediaExtractor()
        ex.setDataSource(file.path)
        val ti = (0 until ex.trackCount).firstOrNull {
            ex.getTrackFormat(it).getString(MediaFormat.KEY_MIME)?.startsWith("audio/") == true
        } ?: throw IllegalStateException("File không có âm thanh")
        ex.selectTrack(ti)
        val fmt = ex.getTrackFormat(ti)
        val mime = fmt.getString(MediaFormat.KEY_MIME)!!
        val durUs = if (fmt.containsKey(MediaFormat.KEY_DURATION)) fmt.getLong(MediaFormat.KEY_DURATION) else 0L
        var sr = fmt.getInteger(MediaFormat.KEY_SAMPLE_RATE)
        var ch = fmt.getInteger(MediaFormat.KEY_CHANNEL_COUNT)
        var enc = AudioFormat.ENCODING_PCM_16BIT

        val codec = MediaCodec.createDecoderByType(mime)
        codec.configure(fmt, null, null, 0)
        codec.start()
        val out = FloatBuf()
        // bộ lọc trung bình + lấy mẫu lại về 16 kHz
        var ratio = sr / 16000.0
        var phase = 0.0
        var acc = 0f
        var cnt = 0
        var lastOut = 0f
        fun pushMono(s: Float) {
            acc += s; cnt++
            phase += 1.0
            var first = true
            while (phase >= ratio) {
                if (first) { lastOut = acc / cnt; acc = 0f; cnt = 0; first = false }
                out.add(lastOut)
                phase -= ratio
            }
        }

        val info = MediaCodec.BufferInfo()
        var inputDone = false
        var lastReport = 0L
        try {
            while (true) {
                if (cancelled) throw kotlinx.coroutines.CancellationException()
                if (!inputDone) {
                    val ii = codec.dequeueInputBuffer(10_000)
                    if (ii >= 0) {
                        val ib = codec.getInputBuffer(ii)!!
                        val n = ex.readSampleData(ib, 0)
                        if (n < 0) {
                            codec.queueInputBuffer(ii, 0, 0, 0, MediaCodec.BUFFER_FLAG_END_OF_STREAM)
                            inputDone = true
                        } else {
                            codec.queueInputBuffer(ii, 0, n, ex.sampleTime, 0)
                            ex.advance()
                        }
                    }
                }
                val oi = codec.dequeueOutputBuffer(info, 10_000)
                when {
                    oi == MediaCodec.INFO_OUTPUT_FORMAT_CHANGED -> {
                        val f = codec.outputFormat
                        sr = f.getInteger(MediaFormat.KEY_SAMPLE_RATE)
                        ch = f.getInteger(MediaFormat.KEY_CHANNEL_COUNT)
                        if (f.containsKey(MediaFormat.KEY_PCM_ENCODING)) enc = f.getInteger(MediaFormat.KEY_PCM_ENCODING)
                        ratio = sr / 16000.0
                    }
                    oi >= 0 -> {
                        val ob = codec.getOutputBuffer(oi)!!
                        ob.position(info.offset); ob.limit(info.offset + info.size)
                        val b = ob.slice().order(ByteOrder.nativeOrder())
                        if (enc == AudioFormat.ENCODING_PCM_FLOAT) {
                            while (b.remaining() >= 4 * ch) {
                                var s = 0f
                                repeat(ch) { s += b.float }
                                pushMono(s / ch)
                            }
                        } else {
                            while (b.remaining() >= 2 * ch) {
                                var s = 0f
                                repeat(ch) { s += b.short / 32768f }
                                pushMono(s / ch)
                            }
                        }
                        codec.releaseOutputBuffer(oi, false)
                        val now = System.currentTimeMillis()
                        if (durUs > 0 && now - lastReport > 200) {
                            lastReport = now
                            onProgress((info.presentationTimeUs.toFloat() / durUs).coerceIn(0f, 1f))
                        }
                        if (info.flags and MediaCodec.BUFFER_FLAG_END_OF_STREAM != 0) break
                    }
                }
            }
        } finally {
            try { codec.stop() } catch (_: Exception) {}
            codec.release()
            ex.release()
        }
        return out.result()
    }

    // ---------- Dọn lời ----------

    private val junk = listOf(
        "subscribe", "đăng ký kênh", "đăng kí kênh", "ghiền mì gõ", "cảm ơn các bạn đã theo dõi",
        "hãy like", "la la school", "thanks for watching", "amara.org", "phụ đề", "vietsub", "lời bài hát tiếng việt",
    )

    private fun clean(raw: List<Triple<Long, Long, String>>): List<LyricLine> {
        val out = ArrayList<LyricLine>()
        var lastText = ""
        var repeat = 0
        var lastEnd = 0L
        for ((t0, t1, t) in raw) {
            var text = t.trim()
                .replace(Regex("\\[[^\\]]*\\]|\\([^)]*\\)|♪+|♫+"), "")
                .replace(Regex("\\s+"), " ")
                .trim(' ', '-', '–', ',', '.')
            if (text.isEmpty()) continue
            if (junk.any { text.lowercase().contains(it) }) continue
            if (text.equals(lastText, ignoreCase = true)) {
                if (++repeat >= 2) continue // whisper hay lặp một câu nhiều lần khi đoạn nhạc không lời
            } else { repeat = 0; lastText = text }
            // khoảng nghỉ dài (dạo nhạc) → chèn dòng trống để lời cũ không "treo" trên màn hình
            if (out.isNotEmpty() && t0 - lastEnd > 6000) out.add(LyricLine(lastEnd + 500, ""))
            text = text.replaceFirstChar { it.uppercase() }
            out.add(LyricLine(t0, text))
            lastEnd = t1
        }
        return out
    }

    private fun ts(ms: Long): String {
        val m = ms / 60000
        val s = (ms % 60000) / 1000
        val cs = (ms % 1000) / 10
        return "%02d:%02d.%02d".format(m, s, cs)
    }
}
