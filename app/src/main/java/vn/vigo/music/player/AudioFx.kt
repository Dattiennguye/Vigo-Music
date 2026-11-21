package vn.vigo.music.player

import android.content.Context
import android.content.SharedPreferences
import android.media.audiofx.BassBoost
import android.media.audiofx.Equalizer
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Bộ chỉnh âm thật của Android (android.media.audiofx) gắn vào đúng phiên âm thanh của trình phát:
 * Equalizer theo dải tần của máy + Tăng bass. Cài đặt được lưu lại.
 * Máy nào không hỗ trợ thì báo rõ, không giả lập.
 */
object AudioFx {
    data class Band(val centerHz: Int, val level: Int)

    data class FxState(
        val eqSupported: Boolean = false,
        val enabled: Boolean = false,
        val bands: List<Band> = emptyList(),
        val minLevel: Int = -1500,
        val maxLevel: Int = 1500,
        val bassSupported: Boolean = false,
        val bass: Int = 0,           // 0..1000
        val preset: String = "Mặc định",
    )

    val presets = listOf("Mặc định", "Bass mạnh", "Giọng hát", "Pop", "Rock", "Nhẹ nhàng", "Điện tử")

    private var eq: Equalizer? = null
    private var bb: BassBoost? = null
    private var prefs: SharedPreferences? = null

    private val _state = MutableStateFlow(FxState())
    val state: StateFlow<FxState> = _state.asStateFlow()

    fun init(context: Context) {
        if (prefs == null) prefs = context.applicationContext.getSharedPreferences("vigo_fx", Context.MODE_PRIVATE)
    }

    fun attach(sessionId: Int) {
        release()
        val p = prefs
        eq = try { Equalizer(0, sessionId) } catch (_: Throwable) { null }
        bb = try { BassBoost(0, sessionId).takeIf { it.strengthSupported } } catch (_: Throwable) { null }
        val e = eq
        var bands = emptyList<Band>()
        var min = -1500
        var max = 1500
        if (e != null) {
            try {
                val range = e.bandLevelRange
                min = range[0].toInt(); max = range[1].toInt()
                val saved = p?.getString("bands", null)?.split(',')?.mapNotNull { it.toIntOrNull() }
                bands = (0 until e.numberOfBands.toInt()).map { i ->
                    val lvl = saved?.getOrNull(i)?.coerceIn(min, max) ?: 0
                    Band(e.getCenterFreq(i.toShort()) / 1000, lvl)
                }
            } catch (_: Throwable) {
                try { e.release() } catch (_: Throwable) {}
                eq = null
            }
        }
        val enabled = p?.getBoolean("enabled", false) ?: false
        val bass = (p?.getInt("bass", 0) ?: 0).coerceIn(0, 1000)
        _state.value = FxState(
            eqSupported = eq != null,
            enabled = enabled,
            bands = bands,
            minLevel = min, maxLevel = max,
            bassSupported = bb != null,
            bass = bass,
            preset = p?.getString("preset", "Mặc định") ?: "Mặc định",
        )
        apply()
    }

    private fun apply() {
        val s = _state.value
        eq?.let { e ->
            try {
                s.bands.forEachIndexed { i, b -> e.setBandLevel(i.toShort(), b.level.toShort()) }
                e.enabled = s.enabled
            } catch (_: Throwable) {}
        }
        bb?.let { b ->
            try {
                b.setStrength(s.bass.toShort())
                b.enabled = s.enabled && s.bass > 0
            } catch (_: Throwable) {}
        }
    }

    private fun save() {
        val s = _state.value
        prefs?.edit()
            ?.putBoolean("enabled", s.enabled)
            ?.putString("bands", s.bands.joinToString(",") { it.level.toString() })
            ?.putInt("bass", s.bass)
            ?.putString("preset", s.preset)
            ?.apply()
    }

    fun setEnabled(on: Boolean) {
        _state.value = _state.value.copy(enabled = on); apply(); save()
    }

    fun setBand(index: Int, level: Int) {
        val s = _state.value
        _state.value = s.copy(
            enabled = true,
            preset = "Tùy chỉnh",
            bands = s.bands.mapIndexed { i, b -> if (i == index) b.copy(level = level.coerceIn(s.minLevel, s.maxLevel)) else b },
        )
        apply(); save()
    }

    fun setBass(strength: Int) {
        _state.value = _state.value.copy(enabled = true, bass = strength.coerceIn(0, 1000)); apply(); save()
    }

    fun applyPreset(name: String) {
        val s = _state.value
        _state.value = s.copy(
            enabled = name != "Mặc định" || s.bass > 0,
            preset = name,
            bands = s.bands.map { b -> b.copy(level = (gainDb(name, b.centerHz) * 100).toInt().coerceIn(s.minLevel, s.maxLevel)) },
            bass = when (name) { "Bass mạnh" -> 600; "Điện tử" -> 400; "Mặc định" -> 0; else -> s.bass },
        )
        apply(); save()
    }

    /** Mức tăng/giảm (dB) của từng preset theo tần số trung tâm của dải. */
    private fun gainDb(preset: String, hz: Int): Float = when (preset) {
        "Bass mạnh" -> when { hz <= 100 -> 7f; hz <= 300 -> 4f; hz <= 1000 -> 0f; else -> -1f }
        "Giọng hát" -> when { hz <= 150 -> -3f; hz <= 500 -> 1f; hz <= 4000 -> 4f; else -> 2f }
        "Pop" -> when { hz <= 100 -> -1f; hz <= 400 -> 2f; hz <= 1500 -> 4f; hz <= 5000 -> 2f; else -> -1f }
        "Rock" -> when { hz <= 150 -> 5f; hz <= 500 -> 2f; hz <= 2000 -> -2f; hz <= 6000 -> 3f; else -> 5f }
        "Nhẹ nhàng" -> when { hz <= 150 -> 2f; hz <= 1000 -> 1f; hz <= 4000 -> 0f; else -> 2f }
        "Điện tử" -> when { hz <= 100 -> 6f; hz <= 400 -> 3f; hz <= 2000 -> -1f; hz <= 6000 -> 2f; else -> 5f }
        else -> 0f
    }

    fun release() {
        try { eq?.release() } catch (_: Throwable) {}
        try { bb?.release() } catch (_: Throwable) {}
        eq = null; bb = null
    }
}
