package com.abdullah.lockapp.questions

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.Base64
import kotlin.random.Random

class QuestionSourcesTest {
    private fun b64(s: String) = Base64.getEncoder().encodeToString(s.toByteArray(Charsets.UTF_8))

    @Test
    fun `Open Trivia DB base64 page is decoded and soft hyphens are stripped`() {
        val body = """
            {"response_code":0,"results":[{"type":"${b64("multiple")}","difficulty":"${b64("hard")}",
            "category":"${b64("General Knowledge")}","question":"${b64("In flight systems, what does \"TCAS\" stand­for?")}",
            "correct_answer":"${b64("Traffic Collision Avoidance System")}",
            "incorrect_answers":["${b64("A")}","${b64("B")}","${b64("Café")}"]}]}
        """.trimIndent()
        val result = OpenTdbParser.parse(body) as OpenTdbParser.Result.Ok
        val q = result.questions.single()
        assertEquals("In flight systems, what does \"TCAS\" standfor?", q.prompt)
        assertEquals("Traffic Collision Avoidance System", q.correct)
        assertEquals(listOf("A", "B", "Café"), q.wrong)
        assertEquals(3, q.difficulty)
        assertEquals("GK", q.category)
        assertEquals(QuestionIds.of(q.prompt, q.correct), q.id)
    }

    @Test
    fun `Open Trivia DB status codes are mapped`() {
        assertEquals(OpenTdbParser.Result.NotEnough, OpenTdbParser.parse("""{"response_code":1,"results":[]}"""))
        assertEquals(OpenTdbParser.Result.TokenNotFound, OpenTdbParser.parse("""{"response_code":3,"results":[]}"""))
        assertEquals(OpenTdbParser.Result.TokenExhausted, OpenTdbParser.parse("""{"response_code":4,"results":[]}"""))
        // The 429 body says "result", not "results".
        assertEquals(OpenTdbParser.Result.RateLimited, OpenTdbParser.parse("""{"response_code":5,"result":[]}"""))
    }

    @Test
    fun `The Trivia API page is parsed`() {
        val body = """
            [{"category":"general_knowledge","id":"622a1c367cc59eab6f9503cd","correctAnswer":"Carpathia",
              "incorrectAnswers":["Lusitania","Iberia","Olympia"],
              "question":{"text":"What was the first ship to reach the Titanic after it sank?"},
              "tags":["general_knowledge"],"type":"text_choice","difficulty":"hard","regions":[],"isNiche":false}]
        """.trimIndent()
        val q = TriviaApiParser.parse(body).single()
        assertEquals("Carpathia", q.correct)
        assertEquals(listOf("Lusitania", "Iberia", "Olympia"), q.wrong)
        assertEquals("the-trivia-api", q.source)
    }

    @Test
    fun `question ids match the pack builder`() {
        // Values printed by tools/build_question_packs.py qid().
        assertEquals("q:3430be8d25133450", QuestionIds.of("What is the capital of Australia?", "Canberra"))
        assertEquals("q:b90d15fc70119f99", QuestionIds.of("Which word means: “Unfortunate; unlucky”?", "hapless"))
        assertEquals("cafeunicode2", QuestionIds.norm("Café Ünïcode ²"))
    }

    @Test
    fun `generated SAT maths questions are well formed`() {
        repeat(2_000) { seed ->
            val q = SatMathGenerator.generate(Random(seed))
            assertEquals("seed $seed: ${q.prompt} ${q.choices}", 4, q.choices.size)
            assertEquals("seed $seed: ${q.prompt} ${q.choices}", 4, q.choices.toSet().size)
            assertTrue(q.correctIndex in 0..3)
            assertTrue(q.prompt.isNotBlank())
        }
    }

    @Test
    fun `generated linear equations are actually solved by the key`() {
        val pattern = Regex("""If (\d+)x ([+−]) (\d+) = (-?\d+), what is the value of x\?""")
        var checked = 0
        repeat(3_000) { seed ->
            val q = SatMathGenerator.generate(Random(seed))
            val m = pattern.matchEntire(q.prompt) ?: return@repeat
            val (a, sign, b, c) = m.destructured
            val x = q.choices[q.correctIndex].toInt()
            val lhs = a.toInt() * x + (if (sign == "+") b.toInt() else -b.toInt())
            assertEquals(q.prompt, c.toInt(), lhs)
            checked++
        }
        assertTrue(checked > 50)
    }

    @Test
    fun `arithmetic fallback has four distinct choices including the answer`() {
        repeat(500) { seed ->
            val q = ArithmeticFallback.generate(Random(seed))
            assertEquals(4, q.choices.toSet().size)
            assertTrue(q.correctIndex >= 0)
        }
    }
}
