package com.abdullah.lockapp.data

import com.abdullah.lockapp.core.AppRule
import com.abdullah.lockapp.core.Change
import com.abdullah.lockapp.core.ChangeGuard
import com.abdullah.lockapp.core.Clock
import com.abdullah.lockapp.core.GuardContext
import com.abdullah.lockapp.core.Verdict
import com.abdullah.lockapp.data.db.AppDatabase
import com.abdullah.lockapp.data.db.AppRuleEntity
import com.abdullah.lockapp.data.db.PendingChangeEntity
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.serialization.json.Json

sealed interface ChangeResult {
    data object Applied : ChangeResult
    data class Queued(val effectiveAt: Long) : ChangeResult
    data class Refused(val focusEndsAt: Long) : ChangeResult
}

/** The blocklist, per-app rules and gate settings, with every edit routed through [ChangeGuard]. */
class RulesRepository(
    private val db: AppDatabase,
    private val settings: SettingsRepository,
    private val clock: Clock,
) {
    private val mutex = Mutex()
    private val json = Json { ignoreUnknownKeys = true }

    val rules: Flow<List<AppRule>> = db.appRules().observeAll().map { list -> list.map { it.toRule() } }
    val pending: Flow<List<PendingChangeEntity>> = db.pending().observeAll()

    suspend fun submit(change: Change): ChangeResult = mutex.withLock {
        val ctx = GuardContext(
            rules = db.appRules().getAll().associate { it.packageName to it.toRule() },
            settings = settings.gateSettings.first(),
            armed = settings.armed.first(),
            focus = settings.focus.first(),
        )
        val now = clock.now()
        when (val verdict = ChangeGuard.evaluate(change, ctx, now, clock.zone())) {
            Verdict.ApplyNow -> {
                apply(change)
                // A stricter edit supersedes any queued loosening of the same setting.
                db.pending().deleteByKey(change.key)
                ChangeResult.Applied
            }
            is Verdict.Queue -> {
                db.pending().deleteByKey(change.key)
                db.pending().replace(
                    PendingChangeEntity(
                        changeKey = change.key,
                        payload = json.encodeToString(Change.serializer(), change),
                        description = ChangeGuard.describe(change) { pkg -> ctx.rules[pkg]?.label ?: pkg },
                        packageName = change.packageNameOrNull(),
                        createdAt = now,
                        effectiveAt = verdict.effectiveAt,
                    ),
                )
                ChangeResult.Queued(verdict.effectiveAt)
            }
            is Verdict.Refuse -> ChangeResult.Refused(verdict.until)
        }
    }

    /** Cancelling a queued loosening only makes things stricter, so it is always allowed. */
    suspend fun cancelPending(id: Long) = db.pending().delete(id)

    /** Applies queued changes whose time has come (midnight). */
    suspend fun applyDuePending(now: Long = clock.now()) = mutex.withLock {
        for (row in db.pending().due(now)) {
            runCatching { json.decodeFromString(Change.serializer(), row.payload) }.getOrNull()?.let { apply(it) }
            db.pending().delete(row.id)
        }
    }

    /** Applies a change without the guard. Used by the guard itself and by debug tooling. */
    suspend fun applyNow(change: Change) = apply(change)

    private suspend fun apply(change: Change) {
        val dao = db.appRules()
        when (change) {
            is Change.AddApp -> if (dao.get(change.packageName) == null) {
                dao.upsert(
                    AppRuleEntity(
                        packageName = change.packageName,
                        label = change.label,
                        gateEnabled = true,
                        dailyLimitMinutes = null,
                        addedAt = clock.now(),
                    ),
                )
            }
            is Change.RemoveApp -> dao.delete(change.packageName)
            is Change.SetGate -> dao.setGate(change.packageName, change.enabled)
            is Change.SetLimit -> dao.setLimit(change.packageName, change.minutes)
            is Change.SetQuestionsRequired -> settings.setQuestionsRequired(change.count)
            is Change.SetUnlockMinutes -> settings.setUnlockMinutes(change.minutes)
            is Change.SetWrongCooldown -> settings.setWrongCooldown(change.seconds)
        }
    }

    private fun Change.packageNameOrNull(): String? = when (this) {
        is Change.AddApp -> packageName
        is Change.RemoveApp -> packageName
        is Change.SetGate -> packageName
        is Change.SetLimit -> packageName
        else -> null
    }
}

fun AppRuleEntity.toRule() = AppRule(packageName, label, gateEnabled, dailyLimitMinutes)
