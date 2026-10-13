package com.abdullah.lockapp.gate

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.abdullah.lockapp.core.BlockReason
import com.abdullah.lockapp.gate.GateViewModel.AnswerState
import com.abdullah.lockapp.gate.GateViewModel.UiState
import com.abdullah.lockapp.ui.AppIcon
import com.abdullah.lockapp.ui.AppIcons
import com.abdullah.lockapp.ui.formatClock
import com.abdullah.lockapp.ui.formatCountdown
import com.abdullah.lockapp.ui.formatMinutes
import com.abdullah.lockapp.ui.rememberNow
import com.abdullah.lockapp.ui.theme.Feedback

@Composable
fun GateScreen(
    state: UiState,
    onAnswer: (Int) -> Unit,
    onNext: () -> Unit,
    onFlag: () -> Unit,
    onGoHome: () -> Unit,
    onCountdownFinished: () -> Unit,
) {
    Surface(Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.surface) {
        Column(
            Modifier
                .fillMaxSize()
                .safeDrawingPadding()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp, vertical = 16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            when (state) {
                UiState.Loading, UiState.NotNeeded -> Loading()
                is UiState.Passed -> Loading()
                is UiState.Blocked -> Blocked(state, onCountdownFinished)
                is UiState.Quiz -> Quiz(state, onAnswer, onNext, onFlag)
            }
            Spacer(Modifier.weight(1f))
            FilledTonalButton(onClick = onGoHome, modifier = Modifier.fillMaxWidth().height(52.dp)) {
                Text(if (state is UiState.Quiz) "Never mind, take me home" else "Go home")
            }
        }
    }
}

@Composable
private fun Loading() {
    Column(Modifier.fillMaxWidth().padding(top = 96.dp), horizontalAlignment = Alignment.CenterHorizontally) {
        CircularProgressIndicator()
    }
}

@Composable
private fun AppHeader(packageName: String, title: String, subtitle: String) {
    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(top = 8.dp)) {
        AppIcon(packageName, size = 48.dp)
        Spacer(Modifier.width(14.dp))
        Column {
            Text(title, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.SemiBold)
            Text(subtitle, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

@Composable
private fun Blocked(state: UiState.Blocked, onCountdownFinished: () -> Unit) {
    val now = rememberNow()
    val remaining = state.until - now
    LaunchedEffect(remaining <= 0) { if (remaining <= 0) onCountdownFinished() }

    val (title, body) = when (state.reason) {
        BlockReason.FOCUS ->
            "Focus session" to "${state.app.label} is locked until ${formatClock(state.until)}. Focus sessions can't be ended early."
        BlockReason.DAILY_LIMIT ->
            "Daily limit reached" to
                "You've used your ${state.limitMinutes?.let { formatMinutes(it) } ?: "time"} of ${state.app.label} today. " +
                "It opens again at midnight."
    }
    AppHeader(state.app.packageName, state.app.label, title)
    Column(
        Modifier.fillMaxWidth().padding(top = 48.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        Icon(
            if (state.reason == BlockReason.FOCUS) AppIcons.Lock else AppIcons.Timer,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.primary,
            modifier = Modifier.size(56.dp),
        )
        Text(
            formatCountdown(remaining),
            style = MaterialTheme.typography.displayMedium,
            fontWeight = FontWeight.Medium,
        )
        Text(body, style = MaterialTheme.typography.bodyLarge, textAlign = TextAlign.Center)
    }
}

@Composable
private fun Quiz(state: UiState.Quiz, onAnswer: (Int) -> Unit, onNext: () -> Unit, onFlag: () -> Unit) {
    val now = rememberNow(intervalMs = 250)
    val question = state.question
    val subtitle = if (state.timeUp) "Your time is up" else "Answer to open"
    AppHeader(state.app.packageName, state.app.label, subtitle)

    Text(
        "Get ${state.required} right to use ${state.app.label} for ${formatMinutes(state.unlockMinutes)}." +
            (state.minutesLeftToday?.let { " ${formatMinutes(it)} left today." } ?: ""),
        style = MaterialTheme.typography.bodyLarge,
    )
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        LinearProgressIndicator(
            progress = { state.correctSoFar.toFloat() / state.required },
            modifier = Modifier.fillMaxWidth(),
        )
        Text(
            "${state.correctSoFar} of ${state.required} correct",
            style = MaterialTheme.typography.labelLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }

    AssistChip(onClick = {}, label = { Text(question.category.label) })

    question.passage?.let {
        Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerHigh)) {
            Text(it, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.padding(14.dp))
        }
    }
    Text(question.prompt, style = MaterialTheme.typography.titleLarge)

    val reading = now < state.choicesEnabledAt
    question.choices.forEachIndexed { index, choice ->
        val isRight = index == question.correctIndex
        val isPicked = index == state.selected
        val highlight: Color? = when {
            state.answer == AnswerState.WAITING -> null
            isRight -> Feedback.correct
            isPicked -> Feedback.wrong
            else -> null
        }
        OutlinedButton(
            onClick = { onAnswer(index) },
            enabled = state.answer == AnswerState.WAITING && !reading,
            shape = RoundedCornerShape(14.dp),
            border = BorderStroke(if (highlight != null) 2.dp else 1.dp, highlight ?: MaterialTheme.colorScheme.outline),
            modifier = Modifier.fillMaxWidth(),
        ) {
            Row(Modifier.fillMaxWidth().padding(vertical = 6.dp), verticalAlignment = Alignment.CenterVertically) {
                Text(
                    choice,
                    style = MaterialTheme.typography.bodyLarge,
                    color = highlight ?: MaterialTheme.colorScheme.onSurface,
                    modifier = Modifier.weight(1f),
                )
                if (highlight != null) {
                    Icon(if (isRight) AppIcons.Check else AppIcons.Close, contentDescription = null, tint = highlight)
                }
            }
        }
    }
    if (reading) {
        Text("Read the question first…", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }

    if (state.answer == AnswerState.WRONG) {
        Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.errorContainer)) {
            Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Text(
                    "Not quite. Correct answer: ${question.choices[question.correctIndex]}",
                    style = MaterialTheme.typography.titleSmall,
                    color = MaterialTheme.colorScheme.onErrorContainer,
                )
                question.explanation?.let {
                    Text(it, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onErrorContainer)
                }
            }
        }
        val wait = state.nextEnabledAt - now
        Button(onClick = onNext, enabled = wait <= 0, modifier = Modifier.fillMaxWidth()) {
            Text(if (wait > 0) "Next question in ${(wait + 999) / 1000}s" else "Next question")
        }
    }

    if (!question.generated) {
        val canFlag = state.answer == AnswerState.WAITING ||
            (state.answer == AnswerState.WRONG && now >= state.nextEnabledAt)
        TextButton(onClick = onFlag, enabled = canFlag) {
            Icon(AppIcons.Flag, contentDescription = null, modifier = Modifier.size(18.dp))
            Spacer(Modifier.width(6.dp))
            Text("Report a wrong or broken question")
        }
    }
}
