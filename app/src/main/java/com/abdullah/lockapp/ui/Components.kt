package com.abdullah.lockapp.ui

import android.Manifest
import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.core.net.toUri
import androidx.lifecycle.compose.LifecycleResumeEffect
import com.abdullah.lockapp.data.ChangeResult
import com.abdullah.lockapp.data.db.PendingChangeEntity
import com.abdullah.lockapp.service.ProtectionState
import com.abdullah.lockapp.service.ProtectionStatus
import com.abdullah.lockapp.ui.theme.Feedback
import kotlinx.coroutines.delay

@Composable
fun SectionCard(
    modifier: Modifier = Modifier,
    container: Color = MaterialTheme.colorScheme.surfaceContainerLow,
    content: @Composable ColumnScope.() -> Unit,
) {
    Card(modifier.fillMaxWidth(), colors = CardDefaults.cardColors(containerColor = container)) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp), content = content)
    }
}

@Composable
fun ScreenTitle(text: String) {
    Text(text, style = MaterialTheme.typography.headlineMedium, modifier = Modifier.padding(top = 8.dp, bottom = 4.dp))
}

/** Single-select chips in a horizontally scrolling row. */
@Composable
fun <T> ChoiceChips(options: List<T>, selected: T?, label: (T) -> String, enabled: Boolean = true, onSelect: (T) -> Unit) {
    Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        options.forEach { option ->
            FilterChip(
                selected = option == selected,
                onClick = { onSelect(option) },
                enabled = enabled,
                label = { Text(label(option)) },
            )
        }
    }
}

fun changeMessage(result: ChangeResult): String? = when (result) {
    ChangeResult.Applied -> null
    is ChangeResult.Queued -> "That loosens a rule, so it takes effect at midnight. You can cancel it on Home."
    is ChangeResult.Refused -> "Rules can't be loosened during a focus session (until ${formatClock(result.focusEndsAt)})."
}

@Composable
fun PendingChangesCard(pending: List<PendingChangeEntity>, onCancel: (Long) -> Unit) {
    if (pending.isEmpty()) return
    SectionCard {
        Text("Waiting until midnight", style = MaterialTheme.typography.titleMedium)
        pending.forEach { change ->
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text(change.description, style = MaterialTheme.typography.bodyMedium)
                    Text(
                        "From ${formatClock(change.effectiveAt)}",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                TextButton(onClick = { onCancel(change.id) }) { Text("Cancel") }
            }
        }
    }
}

/** Re-reads permission/service state on resume and every couple of seconds. */
@Composable
fun rememberProtectionState(): ProtectionState {
    val context = LocalContext.current
    var state by remember { mutableStateOf(ProtectionStatus.read(context)) }
    LifecycleResumeEffect(Unit) {
        state = ProtectionStatus.read(context)
        onPauseOrDispose { }
    }
    LaunchedEffect(Unit) {
        while (true) {
            delay(2_000)
            state = ProtectionStatus.read(context)
        }
    }
    return state
}

fun Context.startSafely(intent: Intent, fallback: Intent? = null) {
    try {
        startActivity(intent)
    } catch (_: ActivityNotFoundException) {
        fallback?.let { runCatching { startActivity(it) } }
    }
}

fun openAccessibilitySettings(context: Context) =
    context.startSafely(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS))

fun openUsageAccessSettings(context: Context) = context.startSafely(
    Intent(Settings.ACTION_USAGE_ACCESS_SETTINGS, "package:${context.packageName}".toUri()),
    fallback = Intent(Settings.ACTION_USAGE_ACCESS_SETTINGS),
)

fun openAppInfo(context: Context, pkg: String) = context.startSafely(
    Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, "package:$pkg".toUri()),
)

@Composable
fun StatusRow(ok: Boolean, title: String, detail: String, okDetail: String, action: String?, onAction: () -> Unit) {
    Row(verticalAlignment = Alignment.Top) {
        Icon(
            if (ok) AppIcons.Check else AppIcons.Warning,
            contentDescription = if (ok) "Done" else "Needs attention",
            tint = if (ok) Feedback.correct else MaterialTheme.colorScheme.error,
            modifier = Modifier.padding(top = 2.dp).size(20.dp),
        )
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(title, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
            Text(if (ok) okDetail else detail, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            if (!ok && action != null) {
                FilledTonalButton(onClick = onAction) { Text(action) }
            }
        }
    }
}

/** The permission checklist shared by onboarding and Settings. */
@Composable
fun ProtectionChecklist(state: ProtectionState) {
    val context = LocalContext.current
    val notificationLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        if (!granted) {
            context.startSafely(
                Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS)
                    .putExtra(Settings.EXTRA_APP_PACKAGE, context.packageName),
            )
        }
    }
    Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
        if (state.advancedProtection) {
            SectionCard(container = MaterialTheme.colorScheme.errorContainer) {
                Text(
                    "Advanced Protection is on. Android 17 blocks accessibility access for apps like LockApp while it's on, " +
                        "so blocking can't work. Turn it off in Settings → Security & privacy → Advanced Protection.",
                    color = MaterialTheme.colorScheme.onErrorContainer,
                    style = MaterialTheme.typography.bodyMedium,
                )
            }
        }
        StatusRow(
            ok = state.serviceEnabled && state.serviceRunning,
            title = "Blocker service",
            detail = if (state.serviceEnabled && !state.serviceRunning) {
                "Switched on, but Android isn't running it yet. If this doesn't clear up in a few seconds, turn it off and on again."
            } else {
                "Settings → Accessibility → LockApp blocker → Use LockApp blocker. If the switch is greyed out, tap it once, " +
                    "then open App info → ⋮ → Allow restricted settings and try again (not possible during a call)."
            },
            okDetail = "On and running.",
            action = "Open Accessibility settings",
        ) { openAccessibilitySettings(context) }
        StatusRow(
            ok = state.usageAccess,
            title = "Usage access",
            detail = "Needed for daily limits and the minutes in Stats.",
            okDetail = "Allowed. Daily limits and Stats minutes work.",
            action = "Allow usage access",
        ) { openUsageAccessSettings(context) }
        StatusRow(
            ok = state.notifications,
            title = "Notifications",
            detail = "For the focus countdown and a warning if protection gets switched off.",
            okDetail = "Allowed.",
            action = "Allow notifications",
        ) { notificationLauncher.launch(Manifest.permission.POST_NOTIFICATIONS) }
    }
}
