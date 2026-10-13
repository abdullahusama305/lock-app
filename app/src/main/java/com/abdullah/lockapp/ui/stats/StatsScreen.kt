package com.abdullah.lockapp.ui.stats

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.abdullah.lockapp.AppContainer
import com.abdullah.lockapp.data.AppStats
import com.abdullah.lockapp.data.DayStats
import com.abdullah.lockapp.ui.AppIcon
import com.abdullah.lockapp.ui.ScreenTitle
import com.abdullah.lockapp.ui.SectionCard
import com.abdullah.lockapp.ui.dayLabel
import com.abdullah.lockapp.ui.formatMinutes

@Composable
fun StatsScreen(c: AppContainer) {
    val rules by c.rules.rules.collectAsStateWithLifecycle(emptyList())
    val stats by remember { c.stats.observe(c.rules.rules) }.collectAsStateWithLifecycle(null)
    LaunchedEffect(rules) { c.stats.refreshUsage(rules.map { it.packageName }) }
    val summary = stats

    LazyColumn(contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        item { ScreenTitle("Stats") }
        if (summary == null) return@LazyColumn

        item {
            SectionCard {
                Text("Today", style = MaterialTheme.typography.titleMedium)
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    Tile("${summary.today.attempts}", "times you\nopened one")
                    Tile(summary.today.turnedBackPercent?.let { "$it%" } ?: "–", "turned\nback")
                    Tile("${summary.today.unlocks}", "unlocks\nearned")
                    Tile(formatMinutes(summary.minutesToday), "spent in\nblocked apps")
                }
            }
        }

        if (summary.perApp.isNotEmpty()) {
            item { Text("By app, today", style = MaterialTheme.typography.titleMedium) }
            items(summary.perApp, key = { it.rule.packageName }) { AppStatsRow(it) }
        }

        item {
            SectionCard {
                Text("Opens, last 7 days", style = MaterialTheme.typography.titleMedium)
                Legend(
                    MaterialTheme.colorScheme.primary to "turned back",
                    MaterialTheme.colorScheme.tertiary to "unlocked",
                    MaterialTheme.colorScheme.outline to "blocked",
                )
                StackedBars(
                    days = summary.week,
                    segments = listOf(
                        MaterialTheme.colorScheme.primary to { d: DayStats -> d.counts.turnedBack },
                        MaterialTheme.colorScheme.tertiary to { d: DayStats -> d.counts.unlocks },
                        MaterialTheme.colorScheme.outline to { d: DayStats -> d.counts.blocked },
                    ),
                    total = { it.counts.turnedBack + it.counts.unlocks + it.counts.blocked },
                )
            }
        }

        item {
            SectionCard {
                Text("Minutes in blocked apps", style = MaterialTheme.typography.titleMedium)
                StackedBars(
                    days = summary.week,
                    segments = listOf(MaterialTheme.colorScheme.secondary to { d: DayStats -> d.minutes }),
                    total = { it.minutes },
                )
            }
        }

        item {
            SectionCard {
                Text("This week", style = MaterialTheme.typography.titleMedium)
                val week = summary.weekCounts
                val accuracy = if (week.answered == 0) "–" else "${week.correct * 100 / week.answered}%"
                Line("Questions answered", "${week.answered} ($accuracy right)")
                Line("Turned back", "${week.turnedBack} of ${week.gateShown} times")
                Line("Focus sessions", "${summary.focusSessions}")
                Line("Protection switched off", "${summary.protectionOffCount} times")
                Line("Phone clock changed", "${summary.clockChanges} times")
            }
        }
    }
}

@Composable
private fun Tile(value: String, label: String) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(value, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.SemiBold)
        Text(
            label,
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
        )
    }
}

@Composable
private fun Line(label: String, value: String) {
    Row(Modifier.fillMaxWidth()) {
        Text(label, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.weight(1f))
        Text(value, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Medium)
    }
}

@Composable
private fun AppStatsRow(stats: AppStats) {
    SectionCard {
        Row(verticalAlignment = Alignment.CenterVertically) {
            AppIcon(stats.rule.packageName, size = 36.dp)
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text(stats.rule.label, style = MaterialTheme.typography.titleSmall)
                val counts = stats.counts
                Text(
                    "Opened ${counts.attempts}× · turned back ${counts.turnedBack}× · unlocked ${counts.unlocks}×",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            val minutes = stats.minutesToday ?: 0
            Text(
                stats.rule.dailyLimitMinutes?.let { "$minutes / ${formatMinutes(it)}" } ?: formatMinutes(minutes),
                style = MaterialTheme.typography.labelLarge,
            )
        }
    }
}

@Composable
private fun Legend(vararg items: Pair<Color, String>) {
    Row(horizontalArrangement = Arrangement.spacedBy(14.dp)) {
        items.forEach { (color, label) ->
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(Modifier.size(10.dp).clip(CircleShape).background(color))
                Spacer(Modifier.width(6.dp))
                Text(label, style = MaterialTheme.typography.labelMedium)
            }
        }
    }
}

/** One column per day; segments stack bottom-up in the given order. */
@Composable
private fun StackedBars(
    days: List<DayStats>,
    segments: List<Pair<Color, (DayStats) -> Int>>,
    total: (DayStats) -> Int,
) {
    val max = days.maxOfOrNull(total)?.takeIf { it > 0 } ?: 1
    val chartHeight = 120.dp
    Row(Modifier.fillMaxWidth().height(chartHeight + 44.dp), verticalAlignment = Alignment.Bottom) {
        days.forEach { day ->
            Column(Modifier.weight(1f), horizontalAlignment = Alignment.CenterHorizontally) {
                Text("${total(day)}", style = MaterialTheme.typography.labelSmall)
                Column(Modifier.width(18.dp).clip(RoundedCornerShape(6.dp))) {
                    segments.reversed().forEach { (color, value) ->
                        val v = value(day)
                        if (v > 0) Box(Modifier.width(18.dp).height(chartHeight * (v.toFloat() / max)).background(color))
                    }
                }
                Text(dayLabel(day.day), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }
}
