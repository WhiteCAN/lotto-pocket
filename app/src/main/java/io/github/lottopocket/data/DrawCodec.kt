package io.github.lottopocket.data

import io.github.lottopocket.domain.Draw
import io.github.lottopocket.domain.LottoRules
import io.github.lottopocket.domain.Options
import org.json.JSONArray
import org.json.JSONObject
import java.time.LocalDate

object DrawCodec {
    const val MAX_BYTES = 5 * 1024 * 1024
    fun decode(text: String): List<Draw> = try {
        require(text.toByteArray(Charsets.UTF_8).size <= MAX_BYTES) { "파일은 5MB 이하만 가져올 수 있어요." }
        val root = JSONObject(text)
        require(integer(root,"schemaVersion") == 1) { "지원하지 않는 데이터 버전이에요." }
        val rows = root.getJSONArray("draws")
        val draws = (0 until rows.length()).map { index ->
            val row = rows.getJSONObject(index)
            val nums = row.getJSONArray("numbers").ints()
            val round = integer(row,"round")
            val date = row.getString("date")
            val bonus = integer(row,"bonus")
            require(round in 1..10000 && LottoRules.validateNumbers(nums) && bonus in 1..45 && bonus !in nums) { "${round}회 번호 형식이 잘못됐어요." }
            LocalDate.parse(date)
            Draw(round, date, nums.sorted(), bonus, row.optString("source", "파일 가져오기"))
        }
        require(draws.map { it.round }.distinct().size == draws.size) { "같은 회차가 여러 번 들어 있어요." }
        draws
    } catch (e: Exception) { throw IllegalArgumentException(e.message ?: "데이터 파일을 읽을 수 없어요.", e) }

    fun encodeDraw(draw: Draw) = JSONObject().put("round", draw.round).put("date", draw.date)
        .put("numbers", JSONArray(draw.numbers)).put("bonus", draw.bonus).put("source", draw.source)

    fun options(value: Options): String = JSONObject().put("count", value.count).put("frequency", value.frequency)
        .put("topCount", value.topCount).put("registeredMode", value.registeredMode).put("registeredCount", value.registeredCount)
        .put("last", value.last).put("fixed", JSONArray(value.fixed.sorted())).toString()

    fun options(text: String): Options {
        val row = JSONObject(text)
        val result = Options(row.optInt("count",5),row.optBoolean("frequency",false),row.optInt("topCount",10),
            row.optBoolean("registeredMode",false),row.optInt("registeredCount",3),row.optInt("last",0),
            row.optJSONArray("fixed")?.ints()?.toSet() ?: emptySet())
        require(result.count in 1..5 && result.topCount in 1..45 && result.registeredCount in 1..5 && result.last in listOf(0,50,100))
        require(result.fixed.size <= 5 && result.fixed.all { it in 1..45 })
        return result
    }

    private fun JSONArray.ints() = (0 until length()).map { i ->
        val n = get(i)
        require(n is Number && n.toDouble() == n.toInt().toDouble()) { "번호는 정수여야 해요." }
        n.toInt()
    }
    private fun integer(row:JSONObject,key:String):Int {
        val value=row.get(key)
        require(value is Number && value.toDouble()==value.toInt().toDouble()) { "$key 값은 정수여야 해요." }
        return value.toInt()
    }
}
