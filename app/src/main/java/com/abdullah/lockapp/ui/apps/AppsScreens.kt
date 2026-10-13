package com.abdullah.lockapp.ui.apps

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.abdullah.lockapp.AppContainer
import com.abdullah.lockapp.core.Change
import com.abdullah.lockapp.data.AppsRepository
import com.abdullah.lockapp.data.InstalledApp
import com.abdullah.lockapp.ui.AppIcon
import com.abdullah.lockapp.ui.AppIcons
import com.abdullah.lockapp.ui.ChoiceChips
import com.abdullah.lockapp.ui.PendingChangesCard
import com.abdullah.lockapp.ui.ScreenTitle
import com.abdullah.lockapp.ui.SectionCard
import com.abdullah.lockapp.ui.changeMessage
import com.abdullah.lockapp.ui.formatMinutes
import kotlinx.coroutines.launch

@Composable
fun AppsScreen(c: AppContainer, onOpenApp: (String) -> Unit) {
    val rules by c.rules.rules.collectAsStateWithLifecycle(emptyList())
    val installed by produceState(emptyList<InstalledApp>()) { value = c.apps.launchableApps() }
    var query by rememberSaveable { mutableStateOf("") }
    val blocked = rules.map { it.packageName }.toSet()
    val suggested = installed.filter { it.packageName in AppsRepository.SUGGESTED && it.packageName !in blocked }
    val others = installed.filter {
        it.packageName !in blocked && (query.isBlank() || it.label.contains(query.trim(), ignoreCase = true))
    }

    LazyColumn(contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
        item { ScreenTitle("Apps") }
        item { SectionTitle("Blocked") }
        if (rules.isEmpty()) {
            item { Text("Nothing yet. Pick an app below.", style = MaterialTheme.typography.bodyMedium) }
        }
        items(rules, key = { "rule-" + it.packageName }) { rule ->
            val detail = buildList {
                if (rule.gateEnabled) add("Questions")
                rule.dailyLimitMinutes?.let { add("${formatMinutes(it)} a day") }
                if (isEmpty()) add("Focus sessions only")
            }.joinToString(" · ")
            AppListRow(rule.packageName, rule.label, detail) { onOpenApp(rule.packageName) }
        }
        if (query.isBlank() && suggested.isNotEmpty()) {
            item { SectionTitle("Suggested") }
            items(suggested, key = { "suggested-" + it.packageName }) { app ->
                AppListRow(app.packageName, app.label, "Not blocked") { onOpenApp(app.packageName) }
            }
        }
        item {
            SectionTitle("All apps")
            OutlinedTextField(
                value = query,
                onValueChange = { query = it },
                label = { Text("Search") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth().padding(bottom = 8.dp),
            )
        }
        items(others, key = { "app-" + it.packageName }) { app ->
            AppListRow(app.packageName, app.label, null) { onOpenApp(app.packageName) }
        }
    }
}

@Composable
private fun SectionTitle(text: String) {
    Text(text, style = MaterialTheme.typography.titleMedium, modifier = Modifier.padding(top = 16.dp, bottom = 4.dp))
}

@Composable
private fun AppListRow(packageName: String, label: String, detail: String?, onClick: () -> Unit) {
    ListItem(
        headlineContent = { Text(label) },
        supportingContent = detail?.let { { Text(it) } },
        leadingContent = { AppIcon(packageName, size = 36.dp) },
        trailingContent = { Icon(AppIcons.ChevronRight, contentDescription = null) },
        modifier = Modifier.clickable(onClick = onClick),
    )
}

@Composable
fun AppDetailScreen(c: AppContainer, packageName: String, snackbar: SnackbarHostState, onBack: () -> Unit) {
    val scope = rememberCoroutineScope()
    val rules by c.rules.rules.collectAsStateWithLifecycle(emptyList())
    val pending by c.rules.pending.collectAsStateWithLifecycle(emptyList())
    val rule = rules.firstOrNull { it.packageName == packageName }
    val label = remember(packageName) { c.apps.label(packageName) }

    fun submit(change: Change) {
        scope.launch {
            val result = c.rules.submit(change)
            changeMessage(result)?.let { snackbar.showSnackbar(it) }
        }
    }

    Column(
        Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = onBack) { Icon(AppIcons.Back, contentDescription = "Back") }
            AppIcon(packageName, size = 40.dp)
            Spacer(Modifier.width(12.dp))
            Text(label, style = MaterialTheme.typography.headlineSmall)
        }

        if (rule == null) {
            SectionCard {
                Text("$label isn't blocked.", style = MaterialTheme.typography.bodyLarge)
                Text(
                    "Blocking it asks questions before it opens and includes it in focus sessions. You can add a daily limit next.",
                    style = MaterialTheme.typography.bodyMedium,
                )
                Button(onClick = { submit(Change.AddApp(packageName, label)) }) { Text("Block $label") }
            }
            return@Column
        }

        SectionCard {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text("Ask questions first", style = MaterialTheme.typography.titleMedium)
                    Text(
                        "Answer SAT or general-knowledge questions to open it for a few minutes.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                Switch(checked = rule.gateEnabled, onCheckedChange = { submit(Change.SetGate(packageName, it)) })
            }
        }

        SectionCard {
            Text("Daily limit", style = MaterialTheme.typography.titleMedium)
            Text(
                "Once it's used up, $label stays locked until midnight. No questions can unlock it.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            val options: List<Int?> = listOf(null, 15, 30, 45, 60, 90, 120)
            ChoiceChips(options, rule.dailyLimitMinutes, label = { it?.let(::formatMinutes) ?: "No limit" }) {
                submit(Change.SetLimit(packageName, it))
            }
        }

        PendingChangesCard(pending.filter { it.packageName == packageName }) { id ->
            scope.launch { c.rules.cancelPending(id) }
        }

        HorizontalDivider()
        Text(
            "Making rules stricter applies right away. Loosening them waits until midnight, and isn't possible during a focus session.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        OutlinedButton(
            onClick = { submit(Change.RemoveApp(packageName)) },
            colors = ButtonDefaults.outlinedButtonColors(contentColor = MaterialTheme.colorScheme.error),
        ) { Text("Stop blocking $label") }
    }
}
