package io.github.lottopocket.data

import androidx.room.*
import io.github.lottopocket.domain.Draw
import kotlinx.coroutines.flow.Flow

@Entity(tableName = "draws")
data class DrawRecord(@PrimaryKey val round: Int, val date: String, val numbers: String, val bonus: Int, val source: String) {
    fun domain() = Draw(round, date, numberList(numbers), bonus, source)
}

@Entity(tableName = "games", indices = [Index("round"), Index("batch")])
data class GameRecord(
    @PrimaryKey val id: String, val batch: String, val round: Int, val numbers: String,
    val source: String, val purchased: Boolean, val fixed: String, val locks: String,
    val options: String, val createdAt: Long,
) { fun values() = numberList(numbers) }

@Entity(tableName = "settings")
data class Setting(@PrimaryKey val key: String, val value: String)

fun numberList(text: String): List<Int> = if (text.isBlank()) emptyList() else text.split(',').map { it.toInt() }

@Dao
interface LottoDao {
    @Query("SELECT * FROM draws ORDER BY round") fun observeDraws(): Flow<List<DrawRecord>>
    @Query("SELECT * FROM games ORDER BY createdAt DESC, id") fun observeGames(): Flow<List<GameRecord>>
    @Query("SELECT * FROM draws ORDER BY round") suspend fun allDraws(): List<DrawRecord>
    @Query("SELECT * FROM games ORDER BY createdAt DESC, id") suspend fun allGames(): List<GameRecord>
    @Insert(onConflict = OnConflictStrategy.ABORT) suspend fun insertDraws(items: List<DrawRecord>)
    @Insert(onConflict = OnConflictStrategy.IGNORE) suspend fun insertGames(items: List<GameRecord>)
    @Query("UPDATE games SET purchased = :purchased WHERE id = :id") suspend fun purchase(id: String, purchased: Boolean)
    @Query("DELETE FROM games WHERE id IN (:ids)") suspend fun deleteGames(ids: List<String>): Int
    @Query("DELETE FROM games") suspend fun deleteAllGames(): Int
    @Query("SELECT value FROM settings WHERE `key` = :key") suspend fun setting(key: String): String?
    @Insert(onConflict = OnConflictStrategy.REPLACE) suspend fun putSetting(setting: Setting)
}

@Database(entities = [DrawRecord::class, GameRecord::class, Setting::class], version = 1, exportSchema = true)
abstract class LottoDatabase : RoomDatabase() { abstract fun dao(): LottoDao }
