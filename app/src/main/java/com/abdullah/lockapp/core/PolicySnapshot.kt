package com.abdullah.lockapp.core

data class AppRule(
    val packageName: String,
    val label: String,
    /** Ask questions before the app can be opened. */
    val gateEnabled: Boolean,
    /** Minutes per day, or null for no limit. */
    val dailyLimitMinutes: Int?,
)

data class FocusState(val startedAt: Long, val endsAt: Long) {
    fun isActive(now: Long) = now < endsAt
}

enum class QuestionCategory(val label: String) {
    GK("General knowledge"),
    SAT_MATH("SAT Math"),
    SAT_WORDS("SAT Vocabulary"),
    ARITHMETIC("Quick maths"),
    TEST("Test"),
}

enum class Difficulty(val label: String, val levels: List<Int>) {
    EASY("Easy", listOf(0, 1)),
    MIXED("Mixed", listOf(0, 1, 2, 3)),
    HARD("Hard", listOf(2, 3)),
}

/** Relative weights for picking a question category. */
data class QuestionMix(val gk: Int, val satMath: Int, val satWords: Int) {
    fun weight(category: QuestionCategory): Int = when (category) {
        QuestionCategory.GK -> gk
        QuestionCategory.SAT_MATH -> satMath
        QuestionCategory.SAT_WORDS -> satWords
        else -> 0
    }

    companion object {
        val DEFAULT = QuestionMix(gk = 40, satMath = 30, satWords = 30)
        val CATEGORIES = listOf(QuestionCategory.GK, QuestionCategory.SAT_MATH, QuestionCategory.SAT_WORDS)
    }
}

data class GateSettings(
    val questionsRequired: Int = 3,
    val unlockMinutes: Int = 10,
    val wrongCooldownSeconds: Int = 10,
    val mix: QuestionMix = QuestionMix.DEFAULT,
    val difficulty: Difficulty = Difficulty.MIXED,
)

/** Everything [LockPolicy] needs, held in memory so decisions never touch disk. */
data class PolicySnapshot(
    val rules: Map<String, AppRule> = emptyMap(),
    val focus: FocusState? = null,
    /** packageName -> time the current unlock expires. */
    val unlocks: Map<String, Long> = emptyMap(),
    val settings: GateSettings = GateSettings(),
    val loaded: Boolean = false,
)
