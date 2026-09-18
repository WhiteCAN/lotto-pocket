package io.github.lottopocket.domain

import java.time.DateTimeException
import java.time.LocalDate
import kotlin.random.Random

data class Draw(
    val round: Int,
    val date: String,
    val numbers: List<Int>,
    val bonus: Int,
    val source: String = "",
)

data class Options(
    val count: Int = 5,
    val frequency: Boolean = false,
    val topCount: Int = 10,
    val registeredMode: Boolean = false,
    val registeredCount: Int = 3,
    val last: Int = 0,
    val fixed: Set<Int> = emptySet(),
)

data class NumberFrequency(val number: Int, val count: Int)

object LottoRules {
    fun candidates(
        purchased: List<List<Int>>,
        draws: List<Draw>,
        round: Int,
        options: Options,
    ): List<Int> {
        require(round > 0) { "추천 회차는 양수여야 합니다." }
        require(options.count in 1..5) { "게임 수는 1~5여야 합니다." }
        require(options.topCount in 1..45) { "빈도 상위 개수는 1~45여야 합니다." }
        require(options.registeredCount in 1..5) { "복원 개수는 1~5여야 합니다." }
        validateFixed(options.fixed)
        purchased.forEach { require(validateNumbers(it)) { "구매 번호는 1~45의 서로 다른 숫자 6개여야 합니다." } }

        val excluded = purchased.flatten().toSet()
        val restored = when {
            !options.frequency -> emptySet()
            options.registeredMode -> frequencies(draws, round, options.last)
                .filter { it.count > 0 && it.number in excluded }
                .take(options.registeredCount)
                .mapTo(mutableSetOf()) { it.number }
            else -> frequencies(draws, round, options.last)
                .filter { it.count > 0 }
                .take(options.topCount)
                .map { it.number }
                .filterTo(mutableSetOf()) { it in excluded }
        }

        return ((1..45).toSet() - excluded + restored + options.fixed).sorted()
    }

    fun frequencies(draws: List<Draw>, round: Int, last: Int): List<NumberFrequency> {
        require(round > 0) { "추천 회차는 양수여야 합니다." }
        require(last >= 0) { "통계 기간은 0 이상이어야 합니다." }
        val eligibleByRound = draws
            .filter { it.round in 1 until round }
            .onEach { require(validateDraw(it)) { "유효하지 않은 추첨 결과가 있습니다." } }
            .groupBy { it.round }
            .mapValues { (_, sameRound) ->
                val first = sameRound.first()
                require(sameRound.all {
                    it.date == first.date && it.numbers.toSet() == first.numbers.toSet() && it.bonus == first.bonus
                }) { "같은 회차에 서로 다른 추첨 결과가 있습니다." }
                first
            }
        val eligible = eligibleByRound.values
            .sortedByDescending { it.round }
            .let { if (last == 0) it else it.take(last) }
        val counts = IntArray(46)
        eligible.forEach { draw -> draw.numbers.forEach { counts[it]++ } }
        return (1..45)
            .map { NumberFrequency(it, counts[it]) }
            .sortedWith(compareByDescending<NumberFrequency> { it.count }.thenBy { it.number })
    }

    fun generate(
        pool: List<Int>,
        fixed: Set<Int>,
        count: Int,
        purchased: List<List<Int>>,
        previous: List<List<Int>> = emptyList(),
        locks: List<Set<Int>> = emptyList(),
        onlyIndex: Int? = null,
    ): List<List<Int>> {
        require(count in 1..5) { "게임 수는 1~5여야 합니다." }
        validateFixed(fixed)
        require(pool.all { it in 1..45 }) { "후보 번호는 1~45여야 합니다." }
        purchased.forEach { require(validateNumbers(it)) { "구매 번호가 올바르지 않습니다." } }
        previous.forEach { require(validateNumbers(it)) { "기존 추천 번호가 올바르지 않습니다." } }
        locks.forEach { lock ->
            require(lock.size <= 6 && lock.all { it in 1..45 }) { "잠금 번호가 올바르지 않습니다." }
        }
        require(previous.isEmpty() || previous.size == count) { "기존 추천 게임 수가 일치하지 않습니다." }
        require(locks.size <= count) { "잠금 게임 수가 요청 게임 수보다 많습니다." }
        require(onlyIndex == null || onlyIndex in 0 until count) { "다시 뽑을 게임 위치가 올바르지 않습니다." }
        require(onlyIndex == null || previous.size == count) { "한 게임 다시 뽑기에는 기존 추천이 필요합니다." }

        val basePool = pool.toSet()
        val forbidden = (purchased + previous).mapTo(mutableSetOf()) { it.toSet() }
        val result = MutableList<List<Int>?>(count) { null }

        for (index in 0 until count) {
            if (onlyIndex != null && index != onlyIndex) {
                result[index] = previous[index]
                continue
            }

            val required = fixed + locks.getOrElse(index) { emptySet() }
            require(required.size <= 6) { "공통 고정번호와 잠금번호는 합쳐서 6개 이하여야 합니다." }
            if (required.size == 6 && previous.size == count && previous[index].toSet() == required) {
                result[index] = previous[index].sorted()
                continue
            }

            val available = (basePool + required).sorted()
            require(available.size >= 6) { "후보 번호가 6개 미만입니다. 제외 번호를 완화하세요." }
            val blocked = forbidden + result.filterNotNull().map { it.toSet() }
            val game = randomAvailableGame(available, required, blocked)
                ?: throw IllegalArgumentException("잠금과 중복 제외 조건을 만족하는 새 조합이 없습니다. 잠금을 해제하거나 게임 수를 줄이세요.")
            result[index] = game
        }
        return result.map { requireNotNull(it) }
    }

    fun judge(game: List<Int>, draw: Draw): Int? {
        require(validateNumbers(game)) { "게임 번호가 올바르지 않습니다." }
        require(validateDraw(draw)) { "추첨 결과가 올바르지 않습니다." }
        val matches = game.count { it in draw.numbers }
        return when {
            matches == 6 -> 1
            matches == 5 && draw.bonus in game -> 2
            matches == 5 -> 3
            matches == 4 -> 4
            matches == 3 -> 5
            else -> null
        }
    }

    fun validateNumbers(numbers: List<Int>): Boolean =
        numbers.size == 6 && numbers.distinct().size == 6 && numbers.all { it in 1..45 }

    fun fixedFromDate(month: Int, day: Int): Set<Int> {
        try {
            LocalDate.of(2024, month, day)
        } catch (_: DateTimeException) {
            throw IllegalArgumentException("유효한 월·일을 입력하세요.")
        }
        require(month in 1..45 && day in 1..45) { "월·일은 로또 번호 범위여야 합니다." }
        return setOf(month, day)
    }

    fun parseManual(multiline: String): List<List<Int>> {
        val games = multiline.lineSequence()
            .map(String::trim)
            .filter(String::isNotEmpty)
            .map { line ->
                val numbers = line.split(Regex("[,\\s]+"))
                    .map { it.toIntOrNull() ?: throw IllegalArgumentException("번호는 숫자로 입력하세요.") }
                require(validateNumbers(numbers)) { "각 게임은 1~45의 서로 다른 숫자 6개여야 합니다." }
                numbers.sorted()
            }
            .toList()
        require(games.isNotEmpty()) { "번호를 한 게임 이상 입력하세요." }
        require(games.size <= 5) { "한 번에 최대 5게임까지 입력할 수 있습니다." }
        return games
    }

    private fun validateDraw(draw: Draw): Boolean =
        draw.round > 0 && runCatching { LocalDate.parse(draw.date) }.isSuccess &&
            validateNumbers(draw.numbers) && draw.bonus in 1..45 && draw.bonus !in draw.numbers

    private fun validateFixed(fixed: Set<Int>) {
        require(fixed.size <= 5 && fixed.all { it in 1..45 }) { "공통 고정번호는 1~45에서 최대 5개까지 선택할 수 있습니다." }
    }

    private fun randomAvailableGame(
        available: List<Int>,
        required: Set<Int>,
        blocked: Set<Set<Int>>,
    ): List<Int>? {
        val choices = available.filterNot { it in required }
        val needed = 6 - required.size
        if (needed == 0) return required.sorted().takeIf { it.toSet() !in blocked }
        if (choices.size < needed) return null

        val availableSet = available.toSet()
        val choiceIndexes = choices.withIndex().associate { (index, number) -> number to index }
        val blockedRanks = blocked.asSequence()
            .filter { game -> game.size == 6 && game.containsAll(required) && game.all { it in availableSet } }
            .map { game -> game.filterNot { it in required }.map { choiceIndexes.getValue(it) }.sorted() }
            .map { combinationRank(choices.size, it) }
            .distinct()
            .sorted()
            .toList()
        val validCount = combinations(choices.size, needed) - blockedRanks.size
        if (validCount <= 0) return null
        var fullRank = Random.Default.nextLong(validCount)
        for (blockedRank in blockedRanks) {
            if (blockedRank > fullRank) break
            fullRank++
        }
        val selected = combinationAtRank(choices.size, needed, fullRank).map { choices[it] }
        return (required + selected).sorted()
    }

    private fun combinationRank(n: Int, indexes: List<Int>): Long {
        var rank = 0L
        var start = 0
        indexes.forEachIndexed { position, index ->
            for (candidate in start until index) {
                rank += combinations(n - candidate - 1, indexes.size - position - 1)
            }
            start = index + 1
        }
        return rank
    }

    private fun combinationAtRank(n: Int, k: Int, requestedRank: Long): List<Int> {
        var rank = requestedRank
        var start = 0
        return buildList(k) {
            repeat(k) { position ->
                val remaining = k - position - 1
                for (candidate in start..(n - remaining - 1)) {
                    val blockSize = combinations(n - candidate - 1, remaining)
                    if (rank < blockSize) {
                        add(candidate)
                        start = candidate + 1
                        return@repeat
                    }
                    rank -= blockSize
                }
            }
        }
    }

    private fun combinations(n: Int, k: Int): Long {
        val size = minOf(k, n - k)
        var result = 1L
        for (step in 1..size) result = result * (n - size + step) / step
        return result
    }
}
