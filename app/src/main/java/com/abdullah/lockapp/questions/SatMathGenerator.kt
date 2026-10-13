package com.abdullah.lockapp.questions

import com.abdullah.lockapp.core.QuestionCategory
import kotlin.math.abs
import kotlin.random.Random

/**
 * SAT-style maths questions built from templates, so the answer key is correct by construction.
 * Mixed with the OpenSAT bank, whose machine-written keys are sometimes wrong.
 */
object SatMathGenerator {
    private val templates: List<(Random) -> GateQuestion> = listOf(
        ::linearEquation,
        ::functionValue,
        ::system,
        ::percentChange,
        ::circle,
        ::pythagoras,
        ::mean,
        ::slope,
        ::quadraticRoots,
        ::unitRate,
        ::exponents,
        ::inequality,
    )

    fun generate(random: Random): GateQuestion = templates.random(random)(random)

    private fun q(prompt: String, correct: Any, wrong: List<Any>, explanation: String, random: Random) =
        generatedQuestion(
            QuestionCategory.SAT_MATH,
            prompt,
            correct.toString(),
            wrong.map { it.toString() },
            explanation,
            random,
        )

    /** Three distinct integer distractors near [answer], built from typical slips. */
    private fun near(answer: Int, random: Random, vararg slips: Int): List<Int> {
        val out = LinkedHashSet<Int>()
        slips.filter { it != answer }.forEach { out += it }
        var step = 1
        while (out.size < 3) {
            val candidate = answer + if (random.nextBoolean()) step else -step
            if (candidate != answer) out += candidate
            step++
        }
        return out.take(3)
    }

    private fun signed(n: Int) = if (n < 0) "− ${abs(n)}" else "+ $n"

    private fun linearEquation(r: Random): GateQuestion {
        val a = r.nextInt(2, 10)
        val x = r.nextInt(-9, 13)
        val b = r.nextInt(-20, 21).let { if (it == 0) 7 else it }
        val c = a * x + b
        return q(
            "If ${a}x ${signed(b)} = $c, what is the value of x?",
            x,
            near(x, r, (c + b) / a, -x, c - b),
            (if (b > 0) "Subtract $b from both sides" else "Add ${abs(b)} to both sides") +
                ": ${a}x = ${c - b}. Divide by $a: x = $x.",
            r,
        )
    }

    private fun functionValue(r: Random): GateQuestion {
        val a = r.nextInt(1, 5)
        val b = r.nextInt(-6, 7)
        val c = r.nextInt(-9, 10)
        val k = r.nextInt(-4, 5).let { if (it == 0) 3 else it }
        val value = a * k * k + b * k + c
        val bTerm = when {
            b == 0 -> ""
            b < 0 -> " − ${abs(b)}x"
            else -> " + ${b}x"
        }
        val cTerm = when {
            c == 0 -> ""
            c < 0 -> " − ${abs(c)}"
            else -> " + $c"
        }
        val lead = if (a == 1) "x²" else "${a}x²"
        return q(
            "The function f is defined by f(x) = $lead$bTerm$cTerm. What is the value of f($k)?",
            value,
            near(value, r, -a * k * k + b * k + c, a * k * k - b * k + c, a * 2 * k + b * k + c),
            "f($k) = $a($k)² ${signed(b)}($k) ${signed(c)} = ${a * k * k} ${signed(b * k)} ${signed(c)} = $value.",
            r,
        )
    }

    private fun system(r: Random): GateQuestion {
        val x = r.nextInt(-5, 11)
        val y = r.nextInt(-5, 11)
        val s = x + y
        val d = x - y
        return q(
            "If x + y = $s and x − y = $d, what is the value of x?",
            x,
            near(x, r, y, s, d),
            "Add the equations: 2x = ${s + d}, so x = $x (and y = $y).",
            r,
        )
    }

    private fun percentChange(r: Random): GateQuestion {
        val price = r.nextInt(2, 21) * 20 // keeps every percentage below whole
        val pct = listOf(10, 15, 20, 25, 30, 40, 50).random(r)
        val down = r.nextBoolean()
        val answer = if (down) price * (100 - pct) / 100 else price * (100 + pct) / 100
        val change = price * pct / 100
        val verb = if (down) "decreased" else "increased"
        return q(
            "A price of \$$price is $verb by $pct%. What is the new price, in dollars?",
            answer,
            near(answer, r, if (down) price + change else price - change, change, price - pct),
            "$pct% of $price is $change, so the new price is $price ${if (down) "−" else "+"} $change = $answer.",
            r,
        )
    }

    private fun circle(r: Random): GateQuestion {
        val radius = r.nextInt(3, 13) // r = 2 would make πr² equal 2πr
        return if (r.nextBoolean()) {
            q(
                "A circle has a radius of $radius. What is its area?",
                "${radius * radius}π",
                listOf("${2 * radius}π", "${radius}π", "${4 * radius * radius}π"),
                "Area = πr² = π($radius)² = ${radius * radius}π.",
                r,
            )
        } else {
            q(
                "A circle has a diameter of ${2 * radius}. What is its circumference?",
                "${2 * radius}π",
                listOf("${radius}π", "${radius * radius}π", "${2 * radius * radius}π"),
                "Circumference = πd = ${2 * radius}π.",
                r,
            )
        }
    }

    private fun pythagoras(r: Random): GateQuestion {
        val (a, b, c) = listOf(Triple(3, 4, 5), Triple(5, 12, 13), Triple(8, 15, 17), Triple(7, 24, 25)).random(r)
        val k = r.nextInt(1, 4)
        val (la, lb, lc) = Triple(a * k, b * k, c * k)
        return if (r.nextBoolean()) {
            q(
                "A right triangle has legs of length $la and $lb. What is the length of the hypotenuse?",
                lc,
                near(lc, r, la + lb, lb + 1, lc + k),
                "c² = $la² + $lb² = ${la * la + lb * lb}, so c = $lc.",
                r,
            )
        } else {
            q(
                "A right triangle has a hypotenuse of length $lc and one leg of length $la. What is its area?",
                la * lb / 2,
                near(la * lb / 2, r, la * lb, la * lc / 2, lb * lc / 2),
                "The other leg is √($lc² − $la²) = $lb, so the area is ½ · $la · $lb = ${la * lb / 2}.",
                r,
            )
        }
    }

    private fun mean(r: Random): GateQuestion {
        val n = r.nextInt(4, 6)
        val target = r.nextInt(15, 41)
        val values = MutableList(n - 1) { target + r.nextInt(-6, 7) }
        values += target * n - values.sum()
        values.shuffle(r)
        val sum = values.sum()
        return q(
            "What is the mean of ${values.joinToString(", ")}?",
            target,
            near(target, r, sum / (n - 1), values.sorted()[n / 2], target + 2),
            "The values add up to $sum, and $sum ÷ $n = $target.",
            r,
        )
    }

    private fun slope(r: Random): GateQuestion {
        val m = r.nextInt(-4, 6).let { if (it == 0) 2 else it }
        val x1 = r.nextInt(-5, 5)
        val y1 = r.nextInt(-5, 8)
        val dx = r.nextInt(1, 5)
        val x2 = x1 + dx
        val y2 = y1 + m * dx
        return q(
            "A line passes through the points ($x1, $y1) and ($x2, $y2). What is the slope of the line?",
            m,
            near(m, r, -m, dx, y2 - y1),
            "Slope = ($y2 − ${paren(y1)}) / ($x2 − ${paren(x1)}) = ${y2 - y1} / $dx = $m.",
            r,
        )
    }

    private fun paren(n: Int) = if (n < 0) "($n)" else "$n"

    private fun quadraticRoots(r: Random): GateQuestion {
        val r1 = r.nextInt(1, 10)
        val r2 = r.nextInt(-9, 0)
        val b = -(r1 + r2)
        val c = r1 * r2
        val bTerm = when {
            b == 0 -> ""
            b < 0 -> " − ${abs(b)}x"
            else -> " + ${b}x"
        }
        return q(
            "What is the positive solution of x²$bTerm − ${abs(c)} = 0?",
            r1,
            near(r1, r, -r2, abs(c), abs(b)),
            "x²$bTerm − ${abs(c)} = (x − $r1)(x + ${-r2}), so x = $r1 or x = $r2.",
            r,
        )
    }

    private fun unitRate(r: Random): GateQuestion {
        val per = r.nextInt(2, 9)
        val n1 = r.nextInt(2, 6)
        val n2 = r.nextInt(6, 15)
        val total1 = per * n1
        val answer = per * n2
        return q(
            "If $n1 notebooks cost \$$total1, how much do $n2 notebooks cost, in dollars, at the same price each?",
            answer,
            near(answer, r, total1 * n2, total1 + n2, answer + per),
            "Each notebook costs $total1 ÷ $n1 = \$$per, so $n2 cost $n2 × $per = \$$answer.",
            r,
        )
    }

    private fun exponents(r: Random): GateQuestion {
        val base = listOf(2, 3, 5, 10).random(r)
        val a = r.nextInt(2, 8)
        val b = r.nextInt(2, 8)
        return if (r.nextBoolean()) {
            q(
                "If $base${sup(a)} × $base${sup(b)} = ${base}ⁿ, what is the value of n?",
                a + b,
                near(a + b, r, a * b, abs(a - b), a + b + 1),
                "Multiplying powers of the same base adds the exponents: $a + $b = ${a + b}.",
                r,
            )
        } else {
            q(
                "If ($base${sup(a)})${sup(b)} = ${base}ⁿ, what is the value of n?",
                a * b,
                near(a * b, r, a + b, a * b + a, a * b - b),
                "A power of a power multiplies the exponents: $a × $b = ${a * b}.",
                r,
            )
        }
    }

    private fun sup(n: Int) = n.toString().map { "⁰¹²³⁴⁵⁶⁷⁸⁹"[it - '0'] }.joinToString("")

    private fun inequality(r: Random): GateQuestion {
        val a = r.nextInt(2, 8)
        val b = r.nextInt(1, 15)
        val c = r.nextInt(b + 1, b + 40)
        // smallest integer x with a·x − b > c
        val bound = (c + b).toDouble() / a
        val answer = kotlin.math.floor(bound).toInt() + 1
        return q(
            "What is the smallest integer x that satisfies ${a}x − $b > $c?",
            answer,
            near(answer, r, answer - 1, (c - b) / a + 1, answer + 1),
            "${a}x > ${c + b}, so x > ${"%.2f".format(bound)}. The smallest integer is $answer.",
            r,
        )
    }
}
