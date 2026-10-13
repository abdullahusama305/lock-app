package com.abdullah.lockapp.gate

import android.content.Intent
import android.media.AudioAttributes
import android.media.AudioFocusRequest
import android.media.AudioManager
import android.os.Bundle
import android.os.PowerManager
import androidx.activity.ComponentActivity
import androidx.activity.addCallback
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.abdullah.lockapp.appContainer
import com.abdullah.lockapp.ui.theme.LockTheme

/**
 * Full-screen cover over a blocked app. Lives in its own task (see the manifest), so finishing it
 * reveals the app underneath, while "Go home" sends the user to the launcher instead.
 */
class GateActivity : ComponentActivity() {
    private val viewModel: GateViewModel by viewModels {
        viewModelFactory { initializer { GateViewModel(appContainer) } }
    }
    private var audioFocus: AudioFocusRequest? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        onBackPressedDispatcher.addCallback(this) { goHome() }
        if (!handle(intent)) return

        setContent {
            LockTheme {
                val state by viewModel.state.collectAsStateWithLifecycle()
                LaunchedEffect(state) {
                    if (state is GateViewModel.UiState.Passed || state is GateViewModel.UiState.NotNeeded) {
                        visibleFor = null
                        finish()
                    }
                }
                GateScreen(
                    state = state,
                    onAnswer = viewModel::answer,
                    onNext = viewModel::next,
                    onFlag = viewModel::flagQuestion,
                    onGoHome = ::goHome,
                    onCountdownFinished = { viewModel.reevaluate() },
                )
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        handle(intent)
    }

    private fun handle(intent: Intent): Boolean {
        val pkg = intent.getStringExtra(EXTRA_PACKAGE)
        if (pkg == null) {
            finish()
            return false
        }
        viewModel.setTarget(pkg)
        return true
    }

    override fun onResume() {
        super.onResume()
        visibleFor = viewModel.target
        requestAudioFocus()
    }

    override fun onStop() {
        super.onStop()
        if (visibleFor == viewModel.target) visibleFor = null
        abandonAudioFocus()
        // Switched away (Home gesture, Recents, another app) without unlocking: close for good so a
        // stale gate never lingers. Keep it when only the screen turned off.
        val screenOn = getSystemService(PowerManager::class.java).isInteractive
        if (!isChangingConfigurations && !isFinishing && screenOn) {
            viewModel.onLeftWithoutUnlock()
            finish()
        }
    }

    private fun goHome() {
        viewModel.onLeftWithoutUnlock()
        startActivity(
            Intent(Intent.ACTION_MAIN)
                .addCategory(Intent.CATEGORY_HOME)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
        )
        finish()
    }

    /** Pauses whatever is playing (a YouTube video, say) while the gate is up. */
    private fun requestAudioFocus() {
        if (audioFocus != null) return
        val request = AudioFocusRequest.Builder(AudioManager.AUDIOFOCUS_GAIN_TRANSIENT)
            .setAudioAttributes(
                AudioAttributes.Builder()
                    .setUsage(AudioAttributes.USAGE_ASSISTANCE_SONIFICATION)
                    .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                    .build(),
            )
            .setOnAudioFocusChangeListener { }
            .build()
        getSystemService(AudioManager::class.java).requestAudioFocus(request)
        audioFocus = request
    }

    private fun abandonAudioFocus() {
        audioFocus?.let { getSystemService(AudioManager::class.java).abandonAudioFocusRequest(it) }
        audioFocus = null
    }

    companion object {
        const val EXTRA_PACKAGE = "package"

        /** The package whose gate is on screen, so the service doesn't relaunch it. */
        @Volatile
        var visibleFor: String? = null
    }
}
