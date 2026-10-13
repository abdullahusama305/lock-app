package com.abdullah.lockapp.core

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import java.time.ZoneId

/** A settings edit that has to pass the Medium-strictness guard. */
@Serializable
sealed interface Change {
    /** Pending changes with the same key replace each other; the latest intent wins. */
    val key: String

    @Serializable
    @SerialName("add_app")
    data class AddApp(val packageName: String, val label: String) : Change {
        override val key get() = "app:$packageName"
    }

    @Serializable
    @SerialName("remove_app")
    data class RemoveApp(val packageName: String) : Change {
        override val key get() = "app:$packageName"
    }

    @Serializable
    @SerialName("set_gate")
    data class SetGate(val packageName: String, val enabled: Boolean) : Change {
        override val key get() = "gate:$packageName"
    }

    @Serializable
    @SerialName("set_limit")
    data class SetLimit(val packageName: String, val minutes: Int?) : Change {
        override val key get() = "limit:$packageName"
    }

    @Serializable
    @SerialName("set_questions_required")
    data class SetQuestionsRequired(val count: Int) : Change {
        override val key get() = "questions_required"
    }

    @Serializable
    @SerialName("set_unlock_minutes")
    data class SetUnlockMinutes(val minutes: Int) : Change {
        override val key get() = "unlock_minutes"
    }

    @Serializable
    @SerialName("set_wrong_cooldown")
    data class SetWrongCooldown(val seconds: Int) : Change {
        override val key get() = "wrong_cooldown"
    }
}

/** The applied configuration the guard compares a change against. */
data class GuardContext(
    val rules: Map<String, AppRule>,
    val settings: GateSettings,
    /** False during first-run setup, when every edit applies instantly. */
    val armed: Boolean,
    val focus: FocusState?,
)

sealed interface Verdict {
    data object ApplyNow : Verdict
    data class Queue(val effectiveAt: Long) : Verdict
    data class Refuse(val until: Long) : Verdict
}

/**
 * Medium strictness: tightening applies now, loosening waits until midnight, and nothing can be
 * loosened while a focus session runs.
 */
object ChangeGuard {
    fun isLoosening(change: Change, ctx: GuardContext): Boolean = when (change) {
        is Change.AddApp -> false
        is Change.RemoveApp -> change.packageName in ctx.rules
        is Change.SetGate -> ctx.rules[change.packageName]?.gateEnabled == true && !change.enabled
        is Change.SetLimit -> {
            val current = ctx.rules[change.packageName]?.dailyLimitMinutes
            when {
                current == null -> false
                change.minutes == null -> true
                else -> change.minutes > current
            }
        }
        is Change.SetQuestionsRequired -> change.count < ctx.settings.questionsRequired
        is Change.SetUnlockMinutes -> change.minutes > ctx.settings.unlockMinutes
        is Change.SetWrongCooldown -> change.seconds < ctx.settings.wrongCooldownSeconds
    }

    fun evaluate(change: Change, ctx: GuardContext, now: Long, zone: ZoneId): Verdict {
        if (!ctx.armed || !isLoosening(change, ctx)) return Verdict.ApplyNow
        ctx.focus?.let { if (it.isActive(now)) return Verdict.Refuse(it.endsAt) }
        return Verdict.Queue(DayMath.nextMidnight(now, zone))
    }

    fun describe(change: Change, label: (String) -> String): String = when (change) {
        is Change.AddApp -> "Add ${change.label} to the blocklist"
        is Change.RemoveApp -> "Remove ${label(change.packageName)} from the blocklist"
        is Change.SetGate ->
            if (change.enabled) "Ask questions before ${label(change.packageName)}"
            else "Stop asking questions before ${label(change.packageName)}"
        is Change.SetLimit ->
            if (change.minutes == null) "Remove ${label(change.packageName)}'s daily limit"
            else "${label(change.packageName)} daily limit → ${change.minutes} min"
        is Change.SetQuestionsRequired -> "Correct answers needed → ${change.count}"
        is Change.SetUnlockMinutes -> "Unlock length → ${change.minutes} min"
        is Change.SetWrongCooldown -> "Wrong-answer wait → ${change.seconds} s"
    }
}
