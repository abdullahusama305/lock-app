package com.abdullah.lockapp.questions

import com.abdullah.lockapp.core.QuestionCategory
import com.abdullah.lockapp.data.SettingsRepository
import com.abdullah.lockapp.data.db.QuestionEntity
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import okhttp3.OkHttpClient
import okhttp3.Request
import java.util.Base64

private val lenientJson = Json { ignoreUnknownKeys = true; coerceInputValues = true }

private fun difficultyLevel(name: String) = when (name.lowercase()) {
    "easy" -> 1
    "medium" -> 2
    "hard" -> 3
    else -> 0
}

private fun OkHttpClient.getText(url: String): String {
    val request = Request.Builder().url(url).header("User-Agent", "LockApp/1.0 (personal app)").build()
    newCall(request).execute().use { response -> return response.body.string() }
}

// ---------------------------------------------------------------- Open Trivia DB (CC BY-SA 4.0)

object OpenTdbParser {
    sealed interface Result {
        data class Ok(val questions: List<QuestionEntity>) : Result
        data object NotEnough : Result
        data object TokenNotFound : Result
        data object TokenExhausted : Result
        data object RateLimited : Result
        data class Failed(val code: Int) : Result
    }

    @Serializable
    private data class Response(
        @SerialName("response_code") val responseCode: Int = -1,
        // A 429 body uses "result" instead; ignoreUnknownKeys plus this default cover it.
        val results: List<Item> = emptyList(),
    )

    @Serializable
    private data class Item(
        val type: String,
        val difficulty: String,
        val question: String,
        @SerialName("correct_answer") val correctAnswer: String,
        @SerialName("incorrect_answers") val incorrectAnswers: List<String>,
    )

    /** Parses a response requested with `encode=base64`. */
    fun parse(body: String): Result {
        val response = lenientJson.decodeFromString<Response>(body)
        return when (response.responseCode) {
            0 -> Result.Ok(response.results.mapNotNull { it.toEntity() })
            1 -> Result.NotEnough
            3 -> Result.TokenNotFound
            4 -> Result.TokenExhausted
            5 -> Result.RateLimited
            else -> Result.Failed(response.responseCode)
        }
    }

    fun decode(value: String): String =
        String(Base64.getDecoder().decode(value), Charsets.UTF_8).replace("­", "").trim()

    private fun Item.toEntity(): QuestionEntity? {
        if (decode(type) != "multiple" || incorrectAnswers.size != 3) return null
        val prompt = decode(question)
        val correct = decode(correctAnswer)
        val wrong = incorrectAnswers.map(::decode)
        if (prompt.isEmpty() || correct.isEmpty() || wrong.any { it.isEmpty() }) return null
        return QuestionEntity(
            id = QuestionIds.of(prompt, correct),
            source = "opentdb",
            category = QuestionCategory.GK.name,
            difficulty = difficultyLevel(decode(difficulty)),
            passage = null,
            prompt = prompt,
            correct = correct,
            wrong = wrong,
            explanation = null,
        )
    }
}

class OpenTdbSource(private val http: OkHttpClient, private val settings: SettingsRepository) {
    /** One page of a category. Open Trivia DB allows one request per 5 s per IP. */
    suspend fun fetch(category: Int, amount: Int = 50): List<QuestionEntity> = withContext(Dispatchers.IO) {
        var token = settings.openTdbToken() ?: newToken()
        repeat(2) {
            val body = http.getText(
                "https://opentdb.com/api.php?amount=$amount&category=$category&type=multiple&encode=base64&token=$token",
            )
            when (val result = OpenTdbParser.parse(body)) {
                is OpenTdbParser.Result.Ok -> return@withContext result.questions
                OpenTdbParser.Result.TokenNotFound -> token = newToken() // tokens expire after 6 h idle
                OpenTdbParser.Result.TokenExhausted -> {
                    delay(RATE_LIMIT_MS)
                    http.getText("https://opentdb.com/api_token.php?command=reset&token=$token")
                    return@withContext emptyList()
                }
                else -> return@withContext emptyList()
            }
        }
        emptyList()
    }

    private suspend fun newToken(): String {
        delay(RATE_LIMIT_MS)
        val body = http.getText("https://opentdb.com/api_token.php?command=request")
        val token = lenientJson.decodeFromString<TokenResponse>(body).token
        settings.setOpenTdbToken(token)
        delay(RATE_LIMIT_MS)
        return token
    }

    @Serializable
    private data class TokenResponse(val token: String)

    companion object {
        const val RATE_LIMIT_MS = 5_500L

        /** General knowledge, science, computers, mythology, geography, history, art, animals. */
        val CATEGORIES = listOf(9, 17, 18, 20, 22, 23, 25, 27)
    }
}

// ---------------------------------------------------------------- The Trivia API (CC BY-NC 4.0)

object TriviaApiParser {
    @Serializable
    private data class Item(
        val id: String,
        val correctAnswer: String,
        val incorrectAnswers: List<String>,
        val question: Text,
        val difficulty: String = "",
        val type: String = "text_choice",
    )

    @Serializable
    private data class Text(val text: String)

    fun parse(body: String): List<QuestionEntity> =
        lenientJson.decodeFromString<List<Item>>(body)
            .filter { it.type == "text_choice" && it.incorrectAnswers.size == 3 }
            .map {
                val prompt = it.question.text.trim()
                val correct = it.correctAnswer.trim()
                QuestionEntity(
                    id = QuestionIds.of(prompt, correct),
                    source = "the-trivia-api",
                    category = QuestionCategory.GK.name,
                    difficulty = difficultyLevel(it.difficulty),
                    passage = null,
                    prompt = prompt,
                    correct = correct,
                    wrong = it.incorrectAnswers.map(String::trim),
                    explanation = null,
                )
            }
}

class TriviaApiSource(private val http: OkHttpClient) {
    suspend fun fetch(): List<QuestionEntity> = withContext(Dispatchers.IO) {
        TriviaApiParser.parse(
            http.getText(
                "https://the-trivia-api.com/v2/questions?limit=50" +
                    "&categories=general_knowledge,science,history,geography,society_and_culture" +
                    "&contentFilter=family",
            ),
        )
    }
}
