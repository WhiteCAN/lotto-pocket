package io.github.lottopocket.ticket

import java.net.URI

data class TicketDraft(
    val round: Int?,
    val games: List<List<Int>>,
    val uncertainCells: Set<Pair<Int, Int>> = emptySet(),
)

sealed interface TicketParseResult {
    data class Success(val draft: TicketDraft) : TicketParseResult
    data class Error(val message: String) : TicketParseResult
}

private val payloadPattern = Regex(
    "^(\\d{4})((?:[mq]\\d{12}){1,5})(?:(\\d{10})(?:\\.net)?)?$",
)
private val gamePattern = Regex("[mq](\\d{12})")
private val ocrNumberPattern = Regex("(?<!\\d)\\d{1,2}(?!\\d)")

fun parseQr(raw: String): TicketParseResult {
    val uri = runCatching { URI(raw.trim()) }.getOrNull()
        ?: return TicketParseResult.Error("QR 주소 형식을 읽을 수 없습니다.")
    if (
        uri.scheme != "https" ||
        uri.host != "m.dhlottery.co.kr" ||
        uri.path != "/qr.do" ||
        uri.port != -1 ||
        uri.userInfo != null ||
        uri.fragment != null
    ) {
        return TicketParseResult.Error("지원하는 동행복권 QR이 아닙니다.")
    }

    val query = uri.rawQuery ?: return TicketParseResult.Error("QR 정보가 비어 있습니다.")
    val value = Regex("^method=winQr&v=([^&]+)$").matchEntire(query)?.groupValues?.get(1)
        ?: return TicketParseResult.Error("지원하는 동행복권 QR 형식이 아닙니다.")
    val payload = payloadPattern.matchEntire(value)
        ?: return TicketParseResult.Error("QR 번호 정보가 잘렸거나 손상되었습니다.")
    val round = payload.groupValues[1].toInt()
    if (round <= 0) return TicketParseResult.Error("회차가 올바르지 않습니다.")

    val games = gamePattern.findAll(payload.groupValues[2]).map { match ->
        match.groupValues[1].chunked(2).map(String::toInt)
    }.toList()
    if (games.any { !isValidGame(it) }) {
        return TicketParseResult.Error("게임 번호는 1~45의 서로 다른 숫자 6개여야 합니다.")
    }
    return TicketParseResult.Success(TicketDraft(round, games))
}

fun extractOcrGames(text: String): List<List<Int>> = text.lineSequence()
    .map { line -> ocrNumberPattern.findAll(line).map { it.value.toInt() }.toList() }
    .filter(::isValidGame)
    .take(5)
    .toList()

private fun isValidGame(numbers: List<Int>): Boolean =
    numbers.size == 6 && numbers.distinct().size == 6 && numbers.all { it in 1..45 }
