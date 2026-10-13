package com.abdullah.lockapp.questions

import com.abdullah.lockapp.core.Clock
import com.abdullah.lockapp.core.QuestionCategory
import com.abdullah.lockapp.data.SettingsRepository
import com.abdullah.lockapp.data.db.QuestionDao
import com.abdullah.lockapp.data.db.QuestionEntity
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext
import kotlin.random.Random

class QuestionRepository(
    private val dao: QuestionDao,
    private val settings: SettingsRepository,
    private val clock: Clock,
    private val requestRefill: () -> Unit,
) : QuestionSource {
    private val random = Random.Default
    private val picker = QuestionPicker(this, random)

    val totalCount: Flow<Int> = dao.observeCount()
    val unseenCount: Flow<Int> = dao.observeUnseenCount()

    suspend fun next(exclude: Set<String>): GateQuestion = withContext(Dispatchers.IO) {
        val gateSettings = settings.gateSettings.first()
        picker.next(gateSettings.mix, gateSettings.difficulty, exclude, settings.testQuestionsOnly.first())
    }

    suspend fun recordAnswer(question: GateQuestion, correct: Boolean) = withContext(Dispatchers.IO) {
        if (question.generated) return@withContext
        dao.recordAnswer(question.id, correct, if (correct) 1 else 0, clock.now())
        if (dao.unseenCount(QuestionCategory.GK.name) < LOW_WATER_MARK) requestRefill()
    }

    suspend fun flag(id: String) = withContext(Dispatchers.IO) { dao.flag(id) }

    /** Inserts fetched questions, skipping ones already stored. Returns how many were new. */
    suspend fun insert(items: List<QuestionEntity>): Int = withContext(Dispatchers.IO) {
        dao.insertAll(items).count { it != -1L }
    }

    suspend fun unseenGeneralKnowledge(): Int = withContext(Dispatchers.IO) { dao.unseenCount(QuestionCategory.GK.name) }

    // QuestionSource

    override suspend fun availableCategories(): Set<QuestionCategory> =
        dao.countsByCategory().filter { it.n > 0 }
            .mapNotNull { runCatching { QuestionCategory.valueOf(it.category) }.getOrNull() }
            .toSet()

    override suspend fun missed(category: QuestionCategory, exclude: Set<String>) =
        dao.oldestMissed(category.name, clock.now() - RETRY_AFTER_MS, exclude.toList())

    override suspend fun unseen(category: QuestionCategory, levels: List<Int>, exclude: Set<String>) =
        dao.randomUnseen(category.name, levels, exclude.toList())

    override suspend fun leastRecent(category: QuestionCategory, levels: List<Int>, exclude: Set<String>) =
        dao.leastRecent(category.name, levels, exclude.toList())

    private companion object {
        const val LOW_WATER_MARK = 50
        /** Don't re-ask a missed question within the same few minutes. */
        const val RETRY_AFTER_MS = 30 * 60 * 1000L
    }
}
