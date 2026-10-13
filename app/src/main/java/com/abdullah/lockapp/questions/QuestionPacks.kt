package com.abdullah.lockapp.questions

import android.content.Context
import com.abdullah.lockapp.data.SettingsRepository
import com.abdullah.lockapp.data.db.QuestionDao
import com.abdullah.lockapp.data.db.QuestionEntity
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

/** One entry of the bundled packs written by tools/build_question_packs.py. */
@Serializable
data class PackQuestion(
    val id: String,
    val source: String,
    val category: String,
    val difficulty: Int = 0,
    val passage: String? = null,
    val prompt: String,
    val correct: String,
    val wrong: List<String>,
    val explanation: String? = null,
) {
    fun toEntity() = QuestionEntity(
        id = id,
        source = source,
        category = category,
        difficulty = difficulty,
        passage = passage,
        prompt = prompt,
        correct = correct,
        wrong = wrong,
        explanation = explanation,
    )
}

/** Seeds the bundled packs into the database once, so the gate works offline from day one. */
class QuestionPacks(
    private val context: Context,
    private val dao: QuestionDao,
    private val settings: SettingsRepository,
) {
    private val json = Json { ignoreUnknownKeys = true }

    suspend fun seedIfNeeded() = withContext(Dispatchers.IO) {
        if (settings.packsVersion() >= VERSION) return@withContext
        for (file in FILES) {
            val text = context.assets.open(file).bufferedReader().use { it.readText() }
            json.decodeFromString<List<PackQuestion>>(text)
                .map { it.toEntity() }
                .chunked(500)
                .forEach { dao.insertAll(it) }
        }
        settings.setPacksVersion(VERSION)
    }

    private companion object {
        /** Bump when the packs change; existing rows (and their history) are kept. */
        const val VERSION = 1
        val FILES = listOf("questions/gk.json", "questions/sat.json")
    }
}
