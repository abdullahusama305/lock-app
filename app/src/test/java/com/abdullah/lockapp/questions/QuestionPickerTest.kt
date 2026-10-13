package com.abdullah.lockapp.questions

import com.abdullah.lockapp.core.Difficulty
import com.abdullah.lockapp.core.QuestionCategory
import com.abdullah.lockapp.core.QuestionMix
import com.abdullah.lockapp.data.db.QuestionEntity
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.random.Random

class QuestionPickerTest {
    private fun q(id: String, category: QuestionCategory, shown: Int = 0, lastCorrect: Boolean? = null, lastShownAt: Long? = null) =
        QuestionEntity(
            id = id,
            source = "test",
            category = category.name,
            difficulty = 1,
            passage = null,
            prompt = "Prompt $id",
            correct = "right",
            wrong = listOf("a", "b", "c"),
            explanation = null,
            timesShown = shown,
            lastCorrect = lastCorrect,
            lastShownAt = lastShownAt,
        )

    /** Mimics the DAO queries over an in-memory list. */
    private class FakeSource(val items: List<QuestionEntity>) : QuestionSource {
        override suspend fun availableCategories() =
            items.mapNotNull { runCatching { QuestionCategory.valueOf(it.category) }.getOrNull() }.toSet()

        override suspend fun missed(category: QuestionCategory, exclude: Set<String>) =
            items.filter { it.category == category.name && it.lastCorrect == false && it.id !in exclude }
                .minByOrNull { it.lastShownAt ?: 0 }

        override suspend fun unseen(category: QuestionCategory, levels: List<Int>, exclude: Set<String>) =
            items.firstOrNull { it.category == category.name && it.timesShown == 0 && it.difficulty in levels && it.id !in exclude }

        override suspend fun leastRecent(category: QuestionCategory, levels: List<Int>, exclude: Set<String>) =
            items.filter { it.category == category.name && it.difficulty in levels && it.id !in exclude }
                .minByOrNull { it.lastShownAt ?: 0 }
    }

    private val gkOnly = QuestionMix(gk = 100, satMath = 0, satWords = 0)

    @Test
    fun `prefers unseen questions`() = runTest {
        val source = FakeSource(listOf(q("seen", QuestionCategory.GK, shown = 3, lastCorrect = true), q("new", QuestionCategory.GK)))
        repeat(20) { seed ->
            val picked = QuestionPicker(source, Random(seed)).next(gkOnly, Difficulty.MIXED, emptySet(), testOnly = false)
            assertEquals("new", picked.id)
        }
    }

    @Test
    fun `missed questions come back sometimes`() = runTest {
        val source = FakeSource(listOf(q("missed", QuestionCategory.GK, shown = 1, lastCorrect = false, lastShownAt = 5), q("new", QuestionCategory.GK)))
        val picks = (0 until 300).map {
            QuestionPicker(source, Random(it)).next(gkOnly, Difficulty.MIXED, emptySet(), testOnly = false).id
        }
        val missedShare = picks.count { it == "missed" } / 300.0
        assertTrue("missed share was $missedShare", missedShare in 0.2..0.47)
    }

    @Test
    fun `falls back to arithmetic when every stored question is used up`() = runTest {
        val source = FakeSource(listOf(q("only", QuestionCategory.GK)))
        val picked = QuestionPicker(source, Random(1)).next(gkOnly, Difficulty.MIXED, setOf("only"), testOnly = false)
        assertEquals(QuestionCategory.ARITHMETIC, picked.category)
        assertEquals(4, picked.choices.size)
    }

    @Test
    fun `an empty store still produces a question`() = runTest {
        val picked = QuestionPicker(FakeSource(emptyList()), Random(1)).next(gkOnly, Difficulty.MIXED, emptySet(), testOnly = false)
        assertTrue(picked.generated)
        assertEquals(4, picked.choices.size)
    }

    @Test
    fun `SAT maths can always be generated`() = runTest {
        val mathOnly = QuestionMix(gk = 0, satMath = 100, satWords = 0)
        val picked = QuestionPicker(FakeSource(emptyList()), Random(2)).next(mathOnly, Difficulty.MIXED, emptySet(), testOnly = false)
        assertEquals(QuestionCategory.SAT_MATH, picked.category)
        assertTrue(picked.generated)
    }

    @Test
    fun `category choice follows the mix`() {
        val all = setOf(QuestionCategory.GK, QuestionCategory.SAT_MATH, QuestionCategory.SAT_WORDS)
        val random = Random(42)
        val counts = (0 until 10_000).groupingBy {
            QuestionPicker.chooseCategory(QuestionMix(gk = 50, satMath = 30, satWords = 20), all, random)
        }.eachCount()
        assertEquals(0.5, counts.getValue(QuestionCategory.GK) / 10_000.0, 0.03)
        assertEquals(0.3, counts.getValue(QuestionCategory.SAT_MATH) / 10_000.0, 0.03)
        assertEquals(0.2, counts.getValue(QuestionCategory.SAT_WORDS) / 10_000.0, 0.03)
    }

    @Test
    fun `no available category means no choice`() {
        assertNull(QuestionPicker.chooseCategory(QuestionMix.DEFAULT, emptySet(), Random(1)))
    }
}
