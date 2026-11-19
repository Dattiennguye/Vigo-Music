package vn.vigo.music

import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.ui.Modifier
import vn.vigo.music.ui.IntroOverlay
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import kotlinx.coroutines.flow.MutableStateFlow
import vn.vigo.music.data.Library
import vn.vigo.music.player.PlayerHub
import vn.vigo.music.ui.MainScreen
import vn.vigo.music.ui.OnboardingScreen
import vn.vigo.music.ui.VGTheme

class MainActivity : ComponentActivity() {

    companion object {
        /** Hiệu ứng mở app chỉ chạy một lần mỗi lần khởi động. */
        private var introPlayed = false
    }

    /** File nhạc được mở/chia sẻ từ app khác, chờ nhập vào thư viện. */
    val incoming = MutableStateFlow<List<Uri>>(emptyList())

    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge(
            statusBarStyle = SystemBarStyle.light(android.graphics.Color.TRANSPARENT, android.graphics.Color.TRANSPARENT),
            navigationBarStyle = SystemBarStyle.light(android.graphics.Color.TRANSPARENT, android.graphics.Color.TRANSPARENT),
        )
        super.onCreate(savedInstanceState)
        Library.init(this)
        PlayerHub.connect(this)
        if (savedInstanceState == null) handleIntent(intent)

        val prefs = getSharedPreferences("vigo", MODE_PRIVATE)
        setContent {
            VGTheme {
                var onboarded by remember { mutableStateOf(prefs.getBoolean("onboarded", false)) }
                var showIntro by remember { mutableStateOf(!introPlayed) }
                Box(Modifier.fillMaxSize()) {
                    AnimatedContent(
                        onboarded,
                        transitionSpec = {
                            (fadeIn(tween(450)) + scaleIn(tween(450), initialScale = 1.06f)) togetherWith
                                (fadeOut(tween(300)) + scaleOut(tween(300), targetScale = 0.94f))
                        },
                        label = "root",
                    ) { done ->
                        if (!done) {
                            OnboardingScreen(onDone = {
                                prefs.edit().putBoolean("onboarded", true).apply()
                                onboarded = true
                            })
                        } else {
                            MainScreen(incoming)
                        }
                    }
                    if (showIntro) {
                        IntroOverlay(onFinished = {
                            if (showIntro) { showIntro = false; introPlayed = true }
                        })
                    }
                }
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        handleIntent(intent)
    }

    private fun handleIntent(intent: Intent?) {
        intent ?: return
        val uris = ArrayList<Uri>()
        when (intent.action) {
            Intent.ACTION_VIEW -> intent.data?.let { uris.add(it) }
            Intent.ACTION_SEND -> {
                val u: Uri? = if (Build.VERSION.SDK_INT >= 33) intent.getParcelableExtra(Intent.EXTRA_STREAM, Uri::class.java)
                else @Suppress("DEPRECATION") intent.getParcelableExtra<Uri>(Intent.EXTRA_STREAM)
                u?.let { uris.add(it) }
            }
        }
        if (uris.isNotEmpty()) incoming.value = incoming.value + uris
    }
}
