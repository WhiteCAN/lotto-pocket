package io.github.lottopocket.data

import androidx.room.withTransaction
import io.github.lottopocket.domain.Draw
import io.github.lottopocket.domain.LottoRules
import org.json.JSONArray
import org.json.JSONObject

class LottoRepository(private val db: LottoDatabase) {
    private val dao = db.dao()
    suspend fun deleteRecords(ids: List<String>): Int = db.withTransaction {
        ids.distinct().chunked(500).sumOf { dao.deleteGames(it) }
    }
    suspend fun resetPersonalData() = db.withTransaction {
        dao.deleteAllGames()
        dao.putSetting(Setting("options", DrawCodec.options(io.github.lottopocket.domain.Options())))
    }
    suspend fun saveRecommendations(games:List<GameRecord>):Int = db.withTransaction {
        val existing=dao.allGames().filter { it.source=="RECOMMEND" }.map { it.round to it.values().sorted() }.toMutableSet()
        val fresh=games.filter { existing.add(it.round to it.values().sorted()) }
        dao.insertGames(fresh)
        fresh.size
    }
    suspend fun importDraws(draws: List<Draw>): Int = db.withTransaction {
        val verified = DrawCodec.decode(JSONObject().put("schemaVersion",1).put("draws",JSONArray(draws.map(DrawCodec::encodeDraw))).toString())
        val existing = dao.allDraws().associateBy { it.round }
        verified.forEach { draw ->
            val old = existing[draw.round]
            require(old == null || (old.domain().numbers == draw.numbers && old.bonus == draw.bonus && old.date == draw.date)) {
                "${draw.round}회 결과가 기존 데이터와 달라요. 가져오기를 취소했어요."
            }
        }
        val new = verified.filter { it.round !in existing }.map { DrawRecord(it.round,it.date,it.numbers.joinToString(","),it.bonus,it.source) }
        dao.insertDraws(new)
        new.size
    }

    suspend fun exportBackup(): String = db.withTransaction {
        JSONObject().put("schemaVersion",1).put("kind","lotto-pocket-backup")
            .put("draws",JSONArray(dao.allDraws().map { DrawCodec.encodeDraw(it.domain()) }))
            .put("games",JSONArray(dao.allGames().map { g -> JSONObject().put("id",g.id).put("batch",g.batch).put("round",g.round)
                .put("numbers",g.numbers).put("source",g.source).put("purchased",g.purchased).put("fixed",g.fixed)
                .put("locks",g.locks).put("options",g.options).put("createdAt",g.createdAt) }))
            .put("options", dao.setting("options") ?: "{}").toString().also {
                require(it.toByteArray(Charsets.UTF_8).size<=DrawCodec.MAX_BYTES) { "백업이 5MB를 넘어 저장하지 않았어요. 더 큰 백업을 지원하는 버전이 필요합니다." }
            }
    }

    suspend fun importFile(text: String): String {
        val draws = DrawCodec.decode(text)
        val root = JSONObject(text)
        if (!root.has("games")) return "${importDraws(draws)}개 회차를 추가했어요."
        require(root.optString("kind") == "lotto-pocket-backup") { "지원하지 않는 백업 파일이에요." }
        val rows = root.getJSONArray("games")
        val games = (0 until rows.length()).map { i ->
            val row = rows.getJSONObject(i)
            val g = GameRecord(row.getString("id"),row.getString("batch"),row.getInt("round"),row.getString("numbers"),
                row.getString("source"),row.getBoolean("purchased"),row.getString("fixed"),row.getString("locks"),row.getString("options"),row.getLong("createdAt"))
            require(g.id.length in 1..128 && g.batch.length in 1..128 && g.round in 1..10000 && g.createdAt >= 0)
            require(g.source in listOf("MANUAL","QR","PHOTO","RECOMMEND"))
            require(LottoRules.validateNumbers(g.values()))
            val fixed = numberList(g.fixed); val locks = numberList(g.locks)
            require(fixed.size <= 5 && fixed.distinct().size == fixed.size && locks.distinct().size == locks.size)
            require((fixed+locks).all { it in g.values() })
            if(g.source=="RECOMMEND") DrawCodec.options(g.options)
            g
        }
        require(games.map { it.id }.distinct().size == games.size) { "중복된 기록 ID가 있어요." }
        val options = if(root.has("options")) root.getString("options") else null
        options?.let(DrawCodec::options)
        return db.withTransaction {
            val existing = dao.allGames().associateBy { it.id }
            games.forEach { require(existing[it.id] == null || existing[it.id] == it) { "같은 기록의 내용이 달라 복원을 취소했어요." } }
            val added = importDraws(draws)
            dao.insertGames(games)
            if(options!=null)dao.putSetting(Setting("options",options))
            "${added}개 회차와 ${games.count { it.id !in existing }}개 게임을 복원했어요."
        }
    }
}
