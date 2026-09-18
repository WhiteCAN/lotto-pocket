package io.github.lottopocket.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test

class LottoRulesTest {
    @Test
    fun `candidate numbers exclude purchased numbers`() {
        val result = LottoRules.candidates(
            purchased = listOf(listOf(1, 2, 3, 4, 5, 6)),
            draws = emptyList(),
            round = 10,
            options = Options(),
        )

        assertFalse(result.any { it in 1..6 })
        assertEquals(39, result.size)
    }

    @Test
    fun `global frequency restores only purchased numbers in top ranking`() {
        val draws = listOf(
            draw(1, 1, 2, 3, 4, 5, 6),
            draw(2, 1, 2, 7, 8, 9, 10),
        )

        val result = LottoRules.candidates(
            purchased = listOf(listOf(1, 2, 20, 21, 22, 23)),
            draws = draws,
            round = 3,
            options = Options(frequency = true, topCount = 2),
        )

        assertTrue(1 in result)
        assertTrue(2 in result)
        assertFalse(20 in result)
    }

    @Test
    fun `registered mode ranks only purchased numbers`() {
        val draws = listOf(
            draw(1, 1, 2, 3, 4, 5, 6),
            draw(2, 1, 7, 8, 9, 10, 11),
        )

        val result = LottoRules.candidates(
            purchased = listOf(listOf(1, 2, 20, 21, 22, 23)),
            draws = draws,
            round = 3,
            options = Options(frequency = true, registeredMode = true, registeredCount = 1),
        )

        assertTrue(1 in result)
        assertFalse(2 in result)
    }

    @Test
    fun `fixed numbers override purchased exclusion`() {
        val result = LottoRules.candidates(
            purchased = listOf(listOf(1, 2, 3, 4, 5, 6)),
            draws = emptyList(),
            round = 2,
            options = Options(fixed = setOf(1, 2)),
        )

        assertTrue(result.containsAll(listOf(1, 2)))
    }

    @Test
    fun `frequency option restores nothing when there is no eligible history`() {
        val result = LottoRules.candidates(
            purchased = listOf(listOf(1, 2, 3, 4, 5, 6)),
            draws = listOf(draw(10, 1, 2, 3, 4, 5, 6)),
            round = 10,
            options = Options(frequency = true, topCount = 10),
        )

        assertFalse(result.any { it in 1..6 })
    }

    @Test
    fun `frequency ignores current and future rounds and uses stable number tie break`() {
        val result = LottoRules.frequencies(
            draws = listOf(
                draw(8, 2, 3, 4, 5, 6, 7),
                draw(9, 1, 2, 10, 11, 12, 13),
                draw(10, 45, 40, 39, 38, 37, 36),
            ),
            round = 10,
            last = 1,
        )

        assertEquals(NumberFrequency(1, 1), result[0])
        assertEquals(NumberFrequency(2, 1), result[1])
        assertEquals(NumberFrequency(10, 1), result[2])
        assertEquals(NumberFrequency(3, 0), result[6])
    }

    @Test
    fun `frequency counts an identical duplicate round once`() {
        val same = draw(1, 1, 2, 3, 4, 5, 6)

        val result = LottoRules.frequencies(listOf(same, same.copy(source = "import")), round = 2, last = 0)

        assertEquals(NumberFrequency(1, 1), result.first())
    }

    @Test
    fun `frequency rejects conflicting results for one round`() {
        assertThrows(IllegalArgumentException::class.java) {
            LottoRules.frequencies(
                listOf(draw(1, 1, 2, 3, 4, 5, 6), draw(1, 1, 2, 3, 4, 5, 7)),
                round = 2,
                last = 0,
            )
        }
    }

    @Test
    fun `generation includes fixed and per game locks and avoids duplicates`() {
        val result = LottoRules.generate(
            pool = (1..12).toList(),
            fixed = setOf(1),
            count = 3,
            purchased = listOf(listOf(1, 2, 3, 4, 5, 6)),
            locks = listOf(setOf(2), setOf(3), setOf(4)),
        )

        assertEquals(3, result.size)
        assertEquals(3, result.distinct().size)
        assertTrue(result.all { it.size == 6 && it == it.sorted() && 1 in it })
        assertTrue(2 in result[0])
        assertTrue(3 in result[1])
        assertTrue(4 in result[2])
        assertFalse(listOf(1, 2, 3, 4, 5, 6) in result)
    }

    @Test
    fun `single regeneration preserves other games and changes selected game`() {
        val previous = listOf(
            listOf(1, 2, 3, 4, 5, 6),
            listOf(1, 7, 8, 9, 10, 11),
        )

        val result = LottoRules.generate(
            pool = (1..15).toList(),
            fixed = setOf(1),
            count = 2,
            purchased = emptyList(),
            previous = previous,
            locks = listOf(emptySet(), setOf(7, 8, 9, 10)),
            onlyIndex = 1,
        )

        assertEquals(previous[0], result[0])
        assertTrue(result[1].containsAll(listOf(1, 7, 8, 9, 10)))
        assertFalse(previous[1] == result[1])
    }

    @Test
    fun `fully locked game is preserved`() {
        val previous = listOf(listOf(1, 2, 3, 4, 5, 6))

        val result = LottoRules.generate(
            pool = (1..10).toList(),
            fixed = emptySet(),
            count = 1,
            purchased = emptyList(),
            previous = previous,
            locks = listOf(previous.single().toSet()),
        )

        assertEquals(previous, result)
    }

    @Test
    fun `generation reports impossible unique combination without retrying forever`() {
        val error = assertThrows(IllegalArgumentException::class.java) {
            LottoRules.generate(
                pool = (1..6).toList(),
                fixed = emptySet(),
                count = 1,
                purchased = listOf(listOf(1, 2, 3, 4, 5, 6)),
            )
        }

        assertTrue(error.message!!.contains("조합"))
    }

    @Test
    fun `five locks find final number even when most combinations are forbidden`() {
        val forbidden = (6..45).map { listOf(1, 2, 3, 4, 5, it) }.dropLast(1)

        val result = LottoRules.generate(
            pool = (1..45).toList(),
            fixed = emptySet(),
            count = 1,
            purchased = forbidden,
            locks = listOf(setOf(1, 2, 3, 4, 5)),
        )

        assertEquals(listOf(1, 2, 3, 4, 5, 45), result.single())
    }

    @Test
    fun `generation selects the only unblocked combination by rank`() {
        val blocked = listOf(
            listOf(1, 2, 3, 4, 5, 6),
            listOf(1, 2, 3, 4, 5, 7),
            listOf(1, 2, 3, 4, 6, 7),
            listOf(1, 2, 3, 5, 6, 7),
            listOf(1, 2, 4, 5, 6, 7),
            listOf(1, 3, 4, 5, 6, 7),
        )

        val result = LottoRules.generate(
            pool = (1..7).toList(),
            fixed = emptySet(),
            count = 1,
            purchased = blocked,
        )

        assertEquals(listOf(2, 3, 4, 5, 6, 7), result.single())
    }

    @Test
    fun `judge follows official rank rules`() {
        val draw = draw(1, 1, 2, 3, 4, 5, 6, bonus = 7)

        assertEquals(1, LottoRules.judge(listOf(1, 2, 3, 4, 5, 6), draw))
        assertEquals(2, LottoRules.judge(listOf(1, 2, 3, 4, 5, 7), draw))
        assertEquals(3, LottoRules.judge(listOf(1, 2, 3, 4, 5, 8), draw))
        assertEquals(4, LottoRules.judge(listOf(1, 2, 3, 4, 8, 9), draw))
        assertEquals(5, LottoRules.judge(listOf(1, 2, 3, 8, 9, 10), draw))
        assertNull(LottoRules.judge(listOf(1, 2, 8, 9, 10, 11), draw))
    }

    @Test
    fun `manual input accepts common separators and rejects malformed games`() {
        assertEquals(
            listOf(listOf(1, 2, 3, 4, 5, 6), listOf(7, 8, 9, 10, 11, 12)),
            LottoRules.parseManual("1, 2, 3, 4, 5, 6\n7 8 9 10 11 12"),
        )
        assertThrows(IllegalArgumentException::class.java) {
            LottoRules.parseManual("1 2 3")
        }
        assertThrows(IllegalArgumentException::class.java) {
            LottoRules.parseManual((1..6).joinToString("\n") { "1 2 3 4 5 6" })
        }
    }

    @Test
    fun `date helper validates calendar date and merges duplicate month day`() {
        assertEquals(setOf(2, 29), LottoRules.fixedFromDate(2, 29))
        assertEquals(setOf(7), LottoRules.fixedFromDate(7, 7))
        assertThrows(IllegalArgumentException::class.java) {
            LottoRules.fixedFromDate(2, 30)
        }
    }

    @Test
    fun `number validation requires six unique values from one to forty five`() {
        assertTrue(LottoRules.validateNumbers(listOf(1, 2, 3, 4, 5, 45)))
        assertFalse(LottoRules.validateNumbers(listOf(1, 2, 3, 4, 5, 46)))
        assertFalse(LottoRules.validateNumbers(listOf(1, 1, 2, 3, 4, 5)))
    }

    private fun draw(round: Int, vararg numbers: Int, bonus: Int = 45) = Draw(
        round = round,
        date = "2026-01-01",
        numbers = numbers.toList(),
        bonus = bonus,
    )
}
