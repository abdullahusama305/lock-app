package com.abdullah.lockapp.questions

import com.abdullah.lockapp.core.QuestionCategory
import kotlin.random.Random

/** Last resort when no stored question is available. The gate never lets anyone through for free. */
object ArithmeticFallback {
    fun generate(random: Random): GateQuestion {
        val a = random.nextInt(12, 60)
        val b = random.nextInt(3, 13)
        val c = random.nextInt(2, 30)
        val (prompt, answer) = when (random.nextInt(3)) {
            0 -> "What is $a × $b?" to a * b
            1 -> "What is $a × $b + $c?" to a * b + c
            else -> "What is ${a * b} ÷ $b?" to a
        }
        val wrong = LinkedHashSet<Int>()
        listOf(answer + b, answer - b, answer + 10, answer - 10, answer + 1, answer - 1)
            .shuffled(random)
            .filter { it > 0 && it != answer }
            .forEach { if (wrong.size < 3) wrong += it }
        return generatedQuestion(
            QuestionCategory.ARITHMETIC,
            prompt,
            answer.toString(),
            wrong.map { it.toString() },
            explanation = null,
            random = random,
        )
    }
}
