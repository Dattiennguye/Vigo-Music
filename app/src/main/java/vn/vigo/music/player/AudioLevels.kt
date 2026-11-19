package vn.vigo.music.player

import android.content.Context
import androidx.annotation.OptIn
import androidx.media3.common.C
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.DefaultRenderersFactory
import androidx.media3.exoplayer.audio.AudioSink
import androidx.media3.exoplayer.audio.DefaultAudioSink
import androidx.media3.exoplayer.audio.TeeAudioProcessor
import java.nio.ByteBuffer
import java.nio.ByteOrder
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.log10
import kotlin.math.max
import kotlin.math.pow
import kotlin.math.sin
import kotlin.math.sqrt

/**
 * Phân tích âm thanh THẬT đang phát (lấy mẫu PCM trong ExoPlayer, không dùng micro):
 * phổ tần [BANDS] dải + độ lớn tổng + năng lượng bass. Giao diện đọc qua [latest] mỗi khung hình.
 *
 * Mẫu được xử lý trước khi ra loa một khoảng bằng bộ đệm âm thanh, nên kết quả được
 * giữ lại [DELAY_NS] rồi mới phát cho giao diện để hiệu ứng khớp với tiếng nghe được.
 */
@OptIn(UnstableApi::class)
object AudioLevels : TeeAudioProcessor.AudioBufferSink {
    const val BANDS = 24
    const val LEVEL = BANDS      // chỉ số độ lớn tổng trong mảng kết quả
    const val BASS = BANDS + 1   // chỉ số bass
    const val SIZE = BANDS + 2

    private const val FFT = 1024
    private const val HOP = 512
    private const val DELAY_NS = 220_000_000L
    private const val RING = 128

    @Volatile private var sampleRate = 44100
    @Volatile private var channels = 2
    @Volatile private var encoding = C.ENCODING_PCM_16BIT

    private val window = FloatArray(FFT) { (0.5 - 0.5 * cos(2 * PI * it / (FFT - 1))).toFloat() }
    private val buf = FloatArray(FFT)
    private var fill = 0
    private val re = FloatArray(FFT)
    private val im = FloatArray(FFT)
    private var bandEdges = IntArray(BANDS + 1)

    private val ringTime = LongArray(RING)
    private val ringData = Array(RING) { FloatArray(SIZE) }
    @Volatile private var head = 0      // vị trí ghi tiếp theo
    @Volatile private var count = 0

    init { computeEdges() }

    private fun computeEdges() {
        val lo = 40.0
        val hi = minOf(16000.0, sampleRate / 2.0 - 100)
        val edges = IntArray(BANDS + 1)
        for (i in 0..BANDS) {
            val f = lo * (hi / lo).pow(i.toDouble() / BANDS)
            edges[i] = (f * FFT / sampleRate).toInt().coerceIn(1, FFT / 2 - 1)
        }
        for (i in 1..BANDS) if (edges[i] <= edges[i - 1]) edges[i] = minOf(edges[i - 1] + 1, FFT / 2)
        bandEdges = edges
    }

    override fun flush(sampleRateHz: Int, channelCount: Int, encoding: Int) {
        synchronized(this) {
            sampleRate = sampleRateHz
            channels = max(1, channelCount)
            this.encoding = encoding
            fill = 0
            count = 0
            computeEdges()
        }
    }

    override fun handleBuffer(buffer: ByteBuffer) {
        synchronized(this) {
            val b = buffer.duplicate().order(ByteOrder.LITTLE_ENDIAN)
            val ch = channels
            when (encoding) {
                C.ENCODING_PCM_16BIT -> while (b.remaining() >= 2 * ch) {
                    var s = 0f
                    repeat(ch) { s += b.short / 32768f }
                    push(s / ch)
                }
                C.ENCODING_PCM_FLOAT -> while (b.remaining() >= 4 * ch) {
                    var s = 0f
                    repeat(ch) { s += b.float }
                    push(s / ch)
                }
                else -> return
            }
        }
    }

    private fun push(s: Float) {
        buf[fill++] = s
        if (fill == FFT) {
            analyze()
            System.arraycopy(buf, HOP, buf, 0, FFT - HOP)
            fill = FFT - HOP
        }
    }

    private fun analyze() {
        var sum = 0f
        for (i in 0 until FFT) {
            val v = buf[i]
            sum += v * v
            re[i] = v * window[i]
            im[i] = 0f
        }
        fft(re, im)
        val out = ringData[head]
        val edges = bandEdges
        for (b in 0 until BANDS) {
            var m = 0f
            for (k in edges[b] until max(edges[b] + 1, edges[b + 1])) {
                val mag = sqrt(re[k] * re[k] + im[k] * im[k])
                if (mag > m) m = mag
            }
            // biên độ → dB → 0..1, bù nhẹ cho dải cao vốn yếu hơn
            val db = 20f * log10(m / (FFT / 4f) + 1e-6f) + b * 0.45f
            out[b] = ((db + 52f) / 46f).coerceIn(0f, 1f)
        }
        val rms = sqrt(sum / FFT)
        out[LEVEL] = ((20f * log10(rms + 1e-6f) + 42f) / 38f).coerceIn(0f, 1f)
        out[BASS] = ((out[0] + out[1] + out[2] + out[3] + out[4]) / 5f).coerceIn(0f, 1f)
        ringTime[head] = System.nanoTime() + DELAY_NS
        head = (head + 1) % RING
        if (count < RING) count++
    }

    /** Khung phân tích mới nhất đã "đến giờ" phát, hoặc null nếu đang im lặng/dừng. */
    fun latest(now: Long = System.nanoTime()): FloatArray? {
        val h = head
        val c = count
        for (i in 1..c) {
            val idx = (h - i + RING) % RING
            val t = ringTime[idx]
            if (t <= now) return if (now - t < 160_000_000L) ringData[idx] else null
        }
        return null
    }

    private fun fft(x: FloatArray, y: FloatArray) {
        val n = x.size
        var j = 0
        for (i in 1 until n) {
            var bit = n shr 1
            while (j and bit != 0) { j = j xor bit; bit = bit shr 1 }
            j = j xor bit
            if (i < j) {
                val tr = x[i]; x[i] = x[j]; x[j] = tr
                val ti = y[i]; y[i] = y[j]; y[j] = ti
            }
        }
        var len = 2
        while (len <= n) {
            val ang = -2 * PI / len
            val wr = cos(ang).toFloat()
            val wi = sin(ang).toFloat()
            var i = 0
            while (i < n) {
                var cr = 1f
                var ci = 0f
                for (k in 0 until len / 2) {
                    val a = i + k
                    val b = a + len / 2
                    val vr = x[b] * cr - y[b] * ci
                    val vi = x[b] * ci + y[b] * cr
                    x[b] = x[a] - vr; y[b] = y[a] - vi
                    x[a] += vr; y[a] += vi
                    val ncr = cr * wr - ci * wi
                    ci = cr * wi + ci * wr
                    cr = ncr
                }
                i += len
            }
            len = len shl 1
        }
    }
}

/** Gắn bộ phân tích vào đường âm thanh của ExoPlayer. */
@OptIn(UnstableApi::class)
class LevelRenderersFactory(context: Context) : DefaultRenderersFactory(context) {
    override fun buildAudioSink(
        context: Context,
        enableFloatOutput: Boolean,
        enableAudioTrackPlaybackParams: Boolean,
    ): AudioSink = DefaultAudioSink.Builder(context)
        .setEnableFloatOutput(enableFloatOutput)
        .setEnableAudioTrackPlaybackParams(enableAudioTrackPlaybackParams)
        .setAudioProcessors(arrayOf(TeeAudioProcessor(AudioLevels)))
        .build()
}
