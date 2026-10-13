package com.abdullah.lockapp.ui.home

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.abdullah.lockapp.AppContainer
import com.abdullah.lockapp.core.AppRule
import com.abdullah.lockapp.core.FocusState
import com.abdullah.lockapp.data.StatsSummary
import com.abdullah.lockapp.ui.AppIcon
import com.abdullah.lockapp.ui.AppIcons
import com.abdullah.lockapp.ui.ChoiceChips
import com.abdullah.lockapp.ui.PendingChangesCard
import com.abdullah.lockapp.ui.ScreenTitle
import com.abdullah.lockapp.ui.SectionCard
import com.abdullah.lockapp.ui.formatClock
import com.abdullah.lockapp.ui.formatCountdown
import com.abdullah.lockapp.ui.formatMinutes
import com.abdullah.lockapp.ui.openAccessibilitySettings
import com.abdullah.lockapp.ui.openUsageAccessSettings
import com.abdullah.lockapp.ui.rememberNow
import com.abdullah.lockapp.ui.rememberProtectionState
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

@Composable
fun HomeScreen(
    c: AppContainer,
    snackbar: SnackbarHostState,
    onOpenApp: (String) -> Unit,
    onOpenApps: () -> Unit,
) {
    val scope = rememberCoroutineScope()
    val context = LocalContext.current
    val rules by c.rules.rules.collectAsStateWithLifecycle(emptyList())
    val focus by c.settings.focus.collectAsStateWithLifecycle(null)
    val pending by c.rules.pending.collectAsStateWithLifecycle(emptyList())
    val stats by remember { c.stats.observe(c.rules.rules) }.collectAsStateWithLifecycle(null)
    val protection = rememberProtectionState()
    val now = rememberNow()
    var showFocusSheet by remember { mutableStateOf(false) }

    LaunchedEffect(rules) {
        while (true) {
            c.stats.refreshUsage(rules.map { it.packageName })
            delay(60_000)
        }
    }

    LazyColumn(contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        item { ScreenTitle("LockApp") }

        if (!protection.ok || !protection.usageAccess) {
            item {
                SectionCard(container = MaterialTheme.colorScheme.errorContainer) {
                    val serviceProblem = !protection.ok
                    Text(
                        if (serviceProblem) "Protection is off" else "Usage access is off",
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.onErrorContainer,
                    )
                    Text(
                        if (serviceProblem) {
                            "Nothing is being blocked. Switch LockApp blocker back on in Accessibility settings."
                        } else {
                            "Daily limits can't be measured, so limited apps ask questions instead."
                        },
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onErrorContainer,
                    )
                    Button(onClick = {
                        if (serviceProblem) openAccessibilitySettings(context) else openUsageAccessSettings(context)
                    }) { Text("Fix it") }
                }
            }
        }

        item {
            FocusCard(
                focus = focus,
                now = now,
                appCount = rules.size,
                onStart = { showFocusSheet = true },
                onExtend = { scope.launch { c.focus.extend(15) } },
            )
        }

        item { TodayCard(stats) }

        if (rules.isEmpty()) {
            item {
                SectionCard {
                    Text("No apps are blocked yet.", style = MaterialTheme.typography.bodyLarge)
                    FilledTonalButton(onClick = onOpenApps) { Text("Choose apps") }
                }
            }
        } else {
            item { Text("Blocked apps", style = MaterialTheme.typography.titleMedium, modifier = Modifier.padding(top = 4.dp)) }
            items(rules, key = { it.packageName }) { rule ->
                AppRuleRow(rule, stats, onClick = { onOpenApp(rule.packageName) })
            }
        }

        item { PendingChangesCard(pending) { id -> scope.launch { c.rules.cancelPending(id) } } }
    }

    if (showFocusSheet) {
        FocusSheet(
            appLabels = rules.map { it.label },
            onDismiss = { showFocusSheet = false },
            onStart = { minutes ->
                showFocusSheet = false
                scope.launch {
                    c.focus.start(minutes)
                    snackbar.showSnackbar("Focus session started")
                }
            },
        )
    }
}

@Composable
private fun FocusCard(focus: FocusState?, now: Long, appCount: Int, onStart: () -> Unit, onExtend: () -> Unit) {
    val active = focus?.isActive(now) == true
    SectionCard(
        container = if (active) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceContainerLow,
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(AppIcons.Lock, contentDescription = null)
            Spacer(Modifier.width(10.dp))
            Text("Focus session", style = MaterialTheme.typography.titleMedium, modifier = Modifier.weight(1f))
        }
        if (active) {
            Text(
                formatCountdown(focus!!.endsAt - now),
                style = MaterialTheme.typography.displaySmall,
                fontWeight = FontWeight.Medium,
            )
            Text("Every blocked app stays locked until ${formatClock(focus.endsAt)}.", style = MaterialTheme.typography.bodyMedium)
            OutlinedButton(onClick = onExtend) { Text("Add 15 minutes") }
        } else {
            Text(
                (if (appCount == 1) "Lock your blocked app" else "Lock all $appCount blocked apps") +
                    " for a while. No questions, no early exit.",
                style = MaterialTheme.typography.bodyMedium,
            )
            Button(onClick = onStart, enabled = appCount > 0) { Text("Start focus") }
        }
    }
}

@Composable
private fun TodayCard(stats: StatsSummary?) {
    val today = stats?.today ?: return
    SectionCard {
        Text("Today", style = MaterialTheme.typography.titleMedium)
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Metric("${today.attempts}", "opens")
            Metric(today.turnedBackPercent?.let { "$it%" } ?: "–", "turned back")
            Metric("${today.unlocks}", "unlocks")
            Metric(formatMinutes(stats.minutesToday), "used")
        }
    }
}

@Composable
private fun Metric(value: String, label: String) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(value, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.SemiBold)
        Text(label, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@Composable
private fun AppRuleRow(rule: AppRule, stats: StatsSummary?, onClick: () -> Unit) {
    val appStats = stats?.perApp?.firstOrNull { it.rule.packageName == rule.packageName }
    SectionCard(modifier = Modifier.clickable(onClick = onClick)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            AppIcon(rule.packageName, size = 40.dp)
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text(rule.label, style = MaterialTheme.typography.titleSmall)
                val parts = buildList {
                    if (rule.gateEnabled) add("Questions")
                    rule.dailyLimitMinutes?.let { limit ->
                        add("${appStats?.minutesToday ?: 0} / ${formatMinutes(limit)} today")
                    } ?: appStats?.minutesToday?.let { add("${formatMinutes(it)} today") }
                    if (!rule.gateEnabled && rule.dailyLimitMinutes == null) add("Focus sessions only")
                }
                Text(
                    parts.joinToString(" · "),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Icon(AppIcons.ChevronRight, contentDescription = null)
        }
    }
}

@Composable
private fun FocusSheet(appLabels: List<String>, onDismiss: () -> Unit, onStart: (Int) -> Unit) {
    val options = listOf(25, 60, 120, 240, 480)
    var minutes by remember { mutableIntStateOf(60) }
    var confirming by remember { mutableStateOf(false) }

    ModalBottomSheet(onDismissRequest = onDismiss) {
        Column(Modifier.padding(horizontal = 20.dp).padding(bottom = 32.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
            Text("Start a focus session", style = MaterialTheme.typography.titleLarge)
            Text(
                "Locks ${appLabels.joinToString(", ")}. You can add time, but you can't end it early.",
                style = MaterialTheme.typography.bodyMedium,
            )
            ChoiceChips(options, minutes, label = ::formatMinutes) { minutes = it }
            Button(onClick = { confirming = true }, modifier = Modifier.fillMaxWidth()) {
                Text("Lock for ${formatMinutes(minutes)}")
            }
        }
    }

    if (confirming) {
        AlertDialog(
            onDismissRequest = { confirming = false },
            title = { Text("Lock for ${formatMinutes(minutes)}?") },
            text = { Text("There's no way to end a focus session early.") },
            confirmButton = { TextButton(onClick = { onStart(minutes) }) { Text("Start") } },
            dismissButton = { TextButton(onClick = { confirming = false }) { Text("Not now") } },
        )
    }
}
