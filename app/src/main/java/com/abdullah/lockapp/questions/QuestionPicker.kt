package com.abdullah.lockapp.questions

import com.abdullah.lockapp.core.Difficulty
import com.abdullah.lockapp.core.QuestionCategory
import com.abdullah.lockapp.core.QuestionMix
import com.abdullah.lockapp.data.db.QuestionEntity
import kotlin.random.Random

/** Where the picker draws stored questions from (the database, or a fake in tests). */
interface QuestionSource {
    suspend fun availableCategories(): Set<QuestionCategory>
    suspend fun missed(category: QuestionCategory, exclude: Set<String>): QuestionEntity?
    suspend fun unseen(category: QuestionCategory, levels: List<Int>, exclude: Set<String>): QuestionEntity?
    suspend fun leastRecent(category: QuestionCategory, levels: List<Int>, exclude: Set<String>): QuestionEntity?
}

class QuestionPicker(private val source: QuestionSource, private val random: Random) {
    /**
     * Picks the next question: a category by the configured mix, then about one time in three a
     * question you got wrong earlier, otherwise one you've never seen, otherwise the least recent.
     * SAT maths comes from the generator half the time. Falls back to arithmetic so the gate never
     * runs dry.
     */
    suspend fun next(mix: QuestionMix, difficulty: Difficulty, exclude: Set<String>, testOnly: Boolean): GateQuestion {
        if (testOnly) {
            fromStore(QuestionCategory.TEST, Difficulty.MIXED, exclude)?.let { return it }
        }
        val stored = source.availableCategories()
        val available = stored + QuestionCategory.SAT_MATH // the generator never runs out
        val category = chooseCategory(mix, available, random)
            ?: return ArithmeticFallback.generate(random)
        if (category == QuestionCategory.SAT_MATH && (QuestionCategory.SAT_MATH !in stored || random.nextBoolean())) {
            return SatMathGenerator.generate(random)
        }
        return fromStore(category, difficulty, exclude) ?: ArithmeticFallback.generate(random)
    }

    private suspend fun fromStore(category: QuestionCategory, difficulty: Difficulty, exclude: Set<String>): GateQuestion? {
        if (random.nextInt(3) == 0) source.missed(category, exclude)?.let { return it.toGateQuestion(random) }
        val all = Difficulty.MIXED.levels
        val entity = source.unseen(category, difficulty.levels, exclude)
            ?: source.unseen(category, all, exclude)
            ?: source.leastRecent(category, difficulty.levels, exclude)
            ?: source.leastRecent(category, all, exclude)
        return entity?.toGateQuestion(random)
    }

    companion object {
        /** Weighted choice among [available] categories; ignores weights if they're all zero. */
        fun chooseCategory(mix: QuestionMix, available: Set<QuestionCategory>, random: Random): QuestionCategory? {
            val candidates = QuestionMix.CATEGORIES.filter { it in available }
            if (candidates.isEmpty()) return null
            val weights = candidates.map { mix.weight(it).coerceAtLeast(0) }
            val total = weights.sum()
            if (total == 0) return candidates.random(random)
            var roll = random.nextInt(total)
            candidates.forEachIndexed { i, category ->
                roll -= weights[i]
                if (roll < 0) return category
            }
            return candidates.last()
        }
    }
}
