package com.abdullah.lockapp.ui.settings

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Slider
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.abdullah.lockapp.AppContainer
import com.abdullah.lockapp.BuildConfig
import com.abdullah.lockapp.core.Change
import com.abdullah.lockapp.core.Difficulty
import com.abdullah.lockapp.core.GateSettings
import com.abdullah.lockapp.core.QuestionMix
import com.abdullah.lockapp.data.AppsRepository
import com.abdullah.lockapp.questions.QuestionRefillWorker
import com.abdullah.lockapp.ui.AppIcons
import com.abdullah.lockapp.ui.ChoiceChips
import com.abdullah.lockapp.ui.ProtectionChecklist
import com.abdullah.lockapp.ui.ScreenTitle
import com.abdullah.lockapp.ui.SectionCard
import com.abdullah.lockapp.ui.changeMessage
import com.abdullah.lockapp.ui.formatClock
import com.abdullah.lockapp.ui.formatMinutes
import com.abdullah.lockapp.ui.openAppInfo
import com.abdullah.lockapp.ui.rememberProtectionState
import kotlinx.coroutines.launch

@Composable
fun SettingsScreen(c: AppContainer, snackbar: SnackbarHostState, onOpenAbout: () -> Unit) {
    val scope = rememberCoroutineScope()
    val context = LocalContext.current
    val settings by c.settings.gateSettings.collectAsStateWithLifecycle(GateSettings())
    val total by c.questions.totalCount.collectAsStateWithLifecycle(0)
    val unseen by c.questions.unseenCount.collectAsStateWithLifecycle(0)
    val lastRefill by c.settings.lastRefill.collectAsStateWithLifecycle(null)
    val protection = rememberProtectionState()

    fun submit(change: Change) {
        scope.launch { changeMessage(c.rules.submit(change))?.let { snackbar.showSnackbar(it) } }
    }

    Column(
        Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        ScreenTitle("Settings")

        SectionCard {
            Text("Question gate", style = MaterialTheme.typography.titleMedium)
            Text("Correct answers needed", style = MaterialTheme.typography.labelLarge)
            ChoiceChips((1..6).toList(), settings.questionsRequired, label = { "$it" }) {
                submit(Change.SetQuestionsRequired(it))
            }
            Text("Unlock length", style = MaterialTheme.typography.labelLarge)
            ChoiceChips(listOf(5, 10, 15, 20, 30), settings.unlockMinutes, label = ::formatMinutes) {
                submit(Change.SetUnlockMinutes(it))
            }
            Text("Wait after a wrong answer", style = MaterialTheme.typography.labelLarge)
            ChoiceChips(listOf(5, 10, 20, 30, 60), settings.wrongCooldownSeconds, label = { "$it s" }) {
                submit(Change.SetWrongCooldown(it))
            }
            Text(
                "Stricter settings apply right away. Looser ones wait until midnight, and can't be set during a focus session.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }

        SectionCard {
            Text("Questions", style = MaterialTheme.typography.titleMedium)
            Text("Difficulty", style = MaterialTheme.typography.labelLarge)
            SingleChoiceSegmentedButtonRow {
                Difficulty.entries.forEachIndexed { index, difficulty ->
                    SegmentedButton(
                        selected = settings.difficulty == difficulty,
                        onClick = { scope.launch { c.settings.setDifficulty(difficulty) } },
                        shape = SegmentedButtonDefaults.itemShape(index, Difficulty.entries.size),
                    ) { Text(difficulty.label) }
                }
            }
            Text("Mix", style = MaterialTheme.typography.labelLarge)
            MixSliders(settings.mix) { mix -> scope.launch { c.settings.setMix(mix) } }
        }

        SectionCard {
            Text("Question bank", style = MaterialTheme.typography.titleMedium)
            Text("$total questions stored, $unseen you haven't seen yet. SAT maths questions are also generated on the fly.")
            lastRefill?.let { (at, added) ->
                Text(
                    "Last download: ${formatClock(at)}, $added new",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            FilledTonalButton(onClick = {
                QuestionRefillWorker.refillNow(context, force = true)
                scope.launch { snackbar.showSnackbar("Downloading new questions in the background") }
            }) {
                Icon(AppIcons.Refresh, contentDescription = null)
                Text("  Download more trivia")
            }
        }

        SectionCard {
            Text("Protection", style = MaterialTheme.typography.titleMedium)
            ProtectionChecklist(protection)
            if (c.apps.isInstalled(AppsRepository.YOUTUBE)) {
                Text(
                    "Keep YouTube's picture-in-picture off, or a video can keep playing in a floating window after your time is up.",
                    style = MaterialTheme.typography.bodySmall,
                )
                OutlinedButton(onClick = { openAppInfo(context, AppsRepository.YOUTUBE) }) { Text("Open YouTube's app info") }
            }
        }

        SectionCard(modifier = Modifier.clickable(onClick = onOpenAbout)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("About and credits", style = MaterialTheme.typography.titleMedium, modifier = Modifier.weight(1f))
                Icon(AppIcons.ChevronRight, contentDescription = null)
            }
        }
    }
}

@Composable
private fun MixSliders(mix: QuestionMix, onChange: (QuestionMix) -> Unit) {
    var gk by remember { mutableFloatStateOf(mix.gk.toFloat()) }
    var math by remember { mutableFloatStateOf(mix.satMath.toFloat()) }
    var words by remember { mutableFloatStateOf(mix.satWords.toFloat()) }
    LaunchedEffect(mix) {
        gk = mix.gk.toFloat()
        math = mix.satMath.toFloat()
        words = mix.satWords.toFloat()
    }
    val total = (gk + math + words).coerceAtLeast(1f)
    fun commit() = onChange(QuestionMix(gk.toInt(), math.toInt(), words.toInt()))

    listOf(
        Triple("General knowledge", gk) { v: Float -> gk = v },
        Triple("SAT Math", math) { v: Float -> math = v },
        Triple("SAT Vocabulary", words) { v: Float -> words = v },
    ).forEach { (label, value, set) ->
        Column {
            Row {
                Text(label, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.weight(1f))
                Text("${(value / total * 100).toInt()}%", style = MaterialTheme.typography.bodyMedium)
            }
            Slider(value = value, onValueChange = set, valueRange = 0f..100f, steps = 9, onValueChangeFinished = ::commit)
        }
    }
}

@Composable
fun AboutScreen(onBack: () -> Unit) {
    Column(
        Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = onBack) { Icon(AppIcons.Back, contentDescription = "Back") }
            Text("About", style = MaterialTheme.typography.headlineSmall)
        }
        SectionCard {
            Text("LockApp ${BuildConfig.VERSION_NAME}", style = MaterialTheme.typography.titleMedium)
            Text("A personal app blocker: focus sessions, daily limits, and questions before the apps that eat your time.")
        }
        SectionCard {
            Text("Question sources", style = MaterialTheme.typography.titleMedium)
            Text("Open Trivia DB (opentdb.com), licensed CC BY-SA 4.0.")
            Text("The Trivia API (the-trivia-api.com), licensed CC BY-NC 4.0.")
            Text("OpenSAT question database (github.com/Anas099X/OpenSAT). Answer keys were filtered automatically; use “Report” on any that look wrong.")
            Text("core-word-list by siddharthagit (MIT License), for vocabulary.")
        }
    }
}
