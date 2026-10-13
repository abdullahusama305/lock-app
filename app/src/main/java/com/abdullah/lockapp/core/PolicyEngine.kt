package com.abdullah.lockapp.core

import com.abdullah.lockapp.data.RulesRepository
import com.abdullah.lockapp.data.SettingsRepository
import com.abdullah.lockapp.data.UsageRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.withContext

/** Keeps the in-memory [PolicySnapshot] current and answers "may this app be on screen?". */
class PolicyEngine(
    rules: RulesRepository,
    settings: SettingsRepository,
    unlocks: UnlockSessions,
    private val usage: UsageRepository,
    private val clock: Clock,
    scope: CoroutineScope,
) {
    val snapshot: StateFlow<PolicySnapshot> = combine(
        rules.rules,
        settings.focus,
        settings.gateSettings,
        unlocks.state.filterNotNull(),
    ) { ruleList, focus, gateSettings, unlockMap ->
        PolicySnapshot(
            rules = ruleList.associateBy { it.packageName },
            focus = focus,
            unlocks = unlockMap,
            settings = gateSettings,
            loaded = true,
        )
    }.stateIn(scope, SharingStarted.Eagerly, PolicySnapshot())

    suspend fun decide(pkg: String): Decision {
        val snap = snapshot.first { it.loaded }
        val limited = snap.rules[pkg]?.dailyLimitMinutes != null
        val used = if (limited) withContext(Dispatchers.IO) { usage.usedTodayMs(pkg) } else null
        return LockPolicy.decide(pkg, clock.now(), snap, used, clock.zone())
    }
}
