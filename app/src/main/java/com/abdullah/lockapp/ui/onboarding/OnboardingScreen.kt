package com.abdullah.lockapp.ui.onboarding

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.Checkbox
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.abdullah.lockapp.AppContainer
import com.abdullah.lockapp.core.Change
import com.abdullah.lockapp.data.AppsRepository
import com.abdullah.lockapp.data.InstalledApp
import com.abdullah.lockapp.ui.AppIcon
import com.abdullah.lockapp.ui.ProtectionChecklist
import com.abdullah.lockapp.ui.SectionCard
import com.abdullah.lockapp.ui.openAppInfo
import com.abdullah.lockapp.ui.rememberProtectionState
import kotlinx.coroutines.launch

/** First-run setup. Every edit here applies instantly; the change guard arms at "Start protecting". */
@Composable
fun OnboardingScreen(c: AppContainer) {
    val scope = rememberCoroutineScope()
    val context = LocalContext.current
    val protection = rememberProtectionState()
    val rules by c.rules.rules.collectAsStateWithLifecycle(emptyList())
    val pipDone by c.settings.pipStepDone.collectAsStateWithLifecycle(false)
    val installed by produceState(emptyList<InstalledApp>()) { value = c.apps.launchableApps() }
    val suggested = AppsRepository.SUGGESTED.mapNotNull { pkg -> installed.firstOrNull { it.packageName == pkg } }
    val blocked = rules.map { it.packageName }.toSet()
    val youtubeBlocked = AppsRepository.YOUTUBE in blocked

    Surface(Modifier.fillMaxSize()) {
        Column(
            Modifier
                .fillMaxSize()
                .safeDrawingPadding()
                .verticalScroll(rememberScrollState())
                .padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            Text("Set up LockApp", style = MaterialTheme.typography.headlineMedium)
            Text(
                "LockApp covers the apps you pick with a short quiz, a daily limit, or a focus lock. " +
                    "It needs a few permissions to see which app is open.",
                style = MaterialTheme.typography.bodyLarge,
            )

            SectionCard {
                Text("1. Permissions", style = MaterialTheme.typography.titleMedium)
                ProtectionChecklist(protection)
            }

            SectionCard {
                Text("2. Apps to block", style = MaterialTheme.typography.titleMedium)
                if (suggested.isEmpty()) {
                    Text("None of the usual suspects are installed. Add apps later from the Apps tab.")
                }
                suggested.forEach { app ->
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Checkbox(
                            checked = app.packageName in blocked,
                            onCheckedChange = { checked ->
                                scope.launch {
                                    c.rules.submit(
                                        if (checked) Change.AddApp(app.packageName, app.label)
                                        else Change.RemoveApp(app.packageName),
                                    )
                                }
                            },
                        )
                        AppIcon(app.packageName, size = 32.dp)
                        Spacer(Modifier.width(10.dp))
                        Text(app.label, style = MaterialTheme.typography.bodyLarge)
                    }
                }
                Text(
                    "Each one asks 3 questions before opening. You can add daily limits and more apps later.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }

            if (youtubeBlocked) {
                SectionCard {
                    Text("3. Turn off picture-in-picture for YouTube", style = MaterialTheme.typography.titleMedium)
                    Text(
                        "Otherwise a video can keep playing in a floating window after your time runs out. " +
                            "In YouTube's App info, open Picture-in-picture and switch it off (or in YouTube: Settings → General).",
                        style = MaterialTheme.typography.bodyMedium,
                    )
                    OutlinedButton(onClick = { openAppInfo(context, AppsRepository.YOUTUBE) }) { Text("Open YouTube's app info") }
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Checkbox(checked = pipDone, onCheckedChange = { scope.launch { c.settings.setPipStepDone(it) } })
                        Text("Done")
                    }
                }
            }

            Spacer(Modifier.height(4.dp))
            val ready = protection.serviceEnabled && blocked.isNotEmpty()
            Button(
                onClick = { scope.launch { c.settings.setArmed(true) } },
                enabled = ready,
                modifier = Modifier.fillMaxWidth().height(52.dp),
            ) { Text("Start protecting") }
            Text(
                if (ready) {
                    "From now on, making rules stricter applies immediately, but loosening them waits until midnight."
                } else {
                    "Turn on the blocker service and pick at least one app first."
                },
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}
