package io.github.lottopocket.ticket

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class TicketParserTest {
    @Test
    fun `parses observed official five game payload without opening it`() {
        val raw = "https://m.dhlottery.co.kr/qr.do?method=winQr&v=" +
            "1179m052527293436m192427303134m021012152244m041623253540" +
            "m1618212440440000000645.net"

        assertEquals(
            TicketParseResult.Success(
                TicketDraft(
                    round = 1179,
                    games = listOf(
                        listOf(5, 25, 27, 29, 34, 36),
                        listOf(19, 24, 27, 30, 31, 34),
                        listOf(2, 10, 12, 15, 22, 44),
                        listOf(4, 16, 23, 25, 35, 40),
                        listOf(16, 18, 21, 24, 40, 44),
                    ),
                ),
            ),
            parseQr(raw),
        )
    }

    @Test
    fun `parses verified q payload with ten digit receipt suffix`() {
        val result = parseQr(
            "https://m.dhlottery.co.kr/qr.do?method=winQr&v=" +
                "0809q021825303444q050812313445q060817202240" +
                "q121623253641q0408092329440000002233",
        )

        assertTrue(result is TicketParseResult.Success)
        result as TicketParseResult.Success
        assertEquals(809, result.draft.round)
        assertEquals(5, result.draft.games.size)
    }

    @Test
    fun `rejects arbitrary host path and extra query`() {
        val payload = "1179m052527293436"

        listOf(
            "https://m.dhlottery.co.kr.evil.test/qr.do?method=winQr&v=$payload",
            "https://m.dhlottery.co.kr/other?method=winQr&v=$payload",
            "https://m.dhlottery.co.kr/qr.do?method=winQr&v=$payload&next=https://evil.test",
        ).forEach { raw ->
            assertTrue(raw, parseQr(raw) is TicketParseResult.Error)
        }
    }

    @Test
    fun `rejects truncated unknown suffix duplicate and out of range games`() {
        listOf(
            "https://m.dhlottery.co.kr/qr.do?method=winQr&v=1179m0525272934",
            "https://m.dhlottery.co.kr/qr.do?method=winQr&v=1179m052527293436BAD",
            "https://m.dhlottery.co.kr/qr.do?method=winQr&v=1179m050527293436",
            "https://m.dhlottery.co.kr/qr.do?method=winQr&v=1179m052527293446",
        ).forEach { raw ->
            assertTrue(raw, parseQr(raw) is TicketParseResult.Error)
        }
    }

    @Test
    fun `rejects zero round and more than five games`() {
        val game = "m010203040506"
        assertTrue(
            parseQr("https://m.dhlottery.co.kr/qr.do?method=winQr&v=0000$game")
                is TicketParseResult.Error,
        )
        assertTrue(
            parseQr("https://m.dhlottery.co.kr/qr.do?method=winQr&v=1179${game.repeat(6)}")
                is TicketParseResult.Error,
        )
    }

    @Test
    fun `extracts only complete valid OCR number rows`() {
        val text = """
            제 1179회
            A. 05 25 27 29 34 36
            B 19 24 27 30 31 34 자동
            C 02 10 12 15 22
            D 04 16 23 25 35 46
            E 16 18 21 24 40 40
        """.trimIndent()

        assertEquals(
            listOf(
                listOf(5, 25, 27, 29, 34, 36),
                listOf(19, 24, 27, 30, 31, 34),
            ),
            extractOcrGames(text),
        )
    }
}
