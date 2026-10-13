package com.abdullah.lockapp.questions

import com.abdullah.lockapp.core.QuestionCategory
import com.abdullah.lockapp.data.db.QuestionEntity
import java.security.MessageDigest
import java.text.Normalizer
import kotlin.random.Random

/** A question ready to show: choices shuffled, with the index of the right one. */
data class GateQuestion(
    val id: String,
    val category: QuestionCategory,
    val passage: String?,
    val prompt: String,
    val choices: List<String>,
    val correctIndex: Int,
    val explanation: String?,
    val source: String,
    /** Generated on the fly (not stored), so it can't be flagged or tracked. */
    val generated: Boolean = false,
)

object QuestionIds {
    /** Must match norm() in tools/build_question_packs.py so bundled and fetched copies dedupe. */
    fun norm(s: String): String =
        Normalizer.normalize(s, Normalizer.Form.NFKD).lowercase()
            .filter { it in 'a'..'z' || it in '0'..'9' }

    fun of(prompt: String, correct: String): String {
        val digest = MessageDigest.getInstance("SHA-1")
            .digest((norm(prompt) + "|" + norm(correct)).toByteArray(Charsets.UTF_8))
        return "q:" + digest.joinToString("") { "%02x".format(it) }.take(16)
    }
}

fun QuestionEntity.toGateQuestion(random: Random): GateQuestion {
    val choices = (wrong + correct).shuffled(random)
    return GateQuestion(
        id = id,
        category = runCatching { QuestionCategory.valueOf(category) }.getOrDefault(QuestionCategory.GK),
        passage = passage,
        prompt = prompt,
        choices = choices,
        correctIndex = choices.indexOf(correct),
        explanation = explanation,
        source = source,
    )
}

internal fun generatedQuestion(
    category: QuestionCategory,
    prompt: String,
    correct: String,
    wrong: List<String>,
    explanation: String?,
    random: Random,
): GateQuestion {
    val choices = (wrong.distinct().filter { it != correct }.take(3) + correct).shuffled(random)
    return GateQuestion(
        id = "gen:" + QuestionIds.of(prompt, correct),
        category = category,
        passage = null,
        prompt = prompt,
        choices = choices,
        correctIndex = choices.indexOf(correct),
        explanation = explanation,
        source = "generated",
        generated = true,
    )
}
