package io.github.lottopocket.data

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import io.github.lottopocket.domain.Draw
import kotlinx.coroutines.test.runTest
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28])
class DatabaseTest {
    @Test fun deletionIsScopedAndResetPreservesDrawsAndUnrelatedSettings() = runTest {
        val db=Room.inMemoryDatabaseBuilder(ApplicationProvider.getApplicationContext<Context>(),LottoDatabase::class.java).build()
        try {
            val dao=db.dao();val repo=LottoRepository(db)
            val recommendation=GameRecord("one","batch",2,"1,2,3,4,5,6","RECOMMEND",false,"","","{}",1)
            val purchased=recommendation.copy(id="two",purchased=true)
            val otherRound=recommendation.copy(id="three",round=3)
            dao.insertGames(listOf(recommendation,purchased,otherRound))
            repo.importDraws(listOf(Draw(1,"2002-12-07",listOf(10,23,29,33,37,40),16)))
            dao.putSetting(Setting("options",DrawCodec.options(io.github.lottopocket.domain.Options(fixed=setOf(7)))))
            dao.putSetting(Setting("unrelated","keep"))
            assertEquals(1,repo.deleteRecords(listOf("one","one","missing")))
            assertEquals(setOf("two","three"),dao.allGames().map { it.id }.toSet())
            assertEquals(1,repo.saveRecommendations(listOf(recommendation.copy(numbers="7,8,9,10,11,12"))))
            assertEquals(1,repo.deleteRecords(listOf("two")))
            assertTrue(dao.allGames().none { it.purchased })
            repo.resetPersonalData()
            assertTrue(dao.allGames().isEmpty())
            assertEquals(io.github.lottopocket.domain.Options(),DrawCodec.options(dao.setting("options")!!))
            assertEquals(1,dao.allDraws().size)
            assertEquals("keep",dao.setting("unrelated"))
        } finally { db.close() }
    }
    @Test fun backupRestoresRecordsAndIsIdempotent() = runTest {
        val context=ApplicationProvider.getApplicationContext<Context>()
        val source=Room.inMemoryDatabaseBuilder(context,LottoDatabase::class.java).build()
        val target=Room.inMemoryDatabaseBuilder(context,LottoDatabase::class.java).build()
        try {
            source.dao().insertGames(listOf(GameRecord("one","batch",2,"1,2,3,4,5,6","MANUAL",true,"","","{}",1)))
            val backup=LottoRepository(source).exportBackup()
            val repo=LottoRepository(target)
            repo.importFile(backup);repo.importFile(backup)
            assertEquals(source.dao().allGames(),target.dao().allGames())
            assertEquals(1,target.dao().allGames().size)
        } finally {source.close();target.close()}
    }
    @Test fun resavingChangedBatchOnlyAddsNewCombinations() = runTest {
        val db=Room.inMemoryDatabaseBuilder(ApplicationProvider.getApplicationContext<Context>(),LottoDatabase::class.java).build()
        try {
            val repo=LottoRepository(db)
            val first=GameRecord("one","first",2,"1,2,3,4,5,6","RECOMMEND",false,"","","{}",1L)
            assertEquals(1,repo.saveRecommendations(listOf(first)))
            assertEquals(0,repo.saveRecommendations(listOf(first.copy(id="second",batch="second",locks="1"))))
            assertEquals(1,repo.saveRecommendations(listOf(first.copy(id="third"),first.copy(id="fourth",numbers="1,2,3,4,5,7"))))
            assertEquals(2,db.dao().allGames().size)
        } finally {db.close()}
    }
    @Test fun conflictingImportRollsBackAndPreservesPersonalRecord() = runTest {
        val db = Room.inMemoryDatabaseBuilder(ApplicationProvider.getApplicationContext<Context>(), LottoDatabase::class.java).build()
        try {
            val repo = LottoRepository(db)
            repo.importDraws(listOf(Draw(1,"2002-12-07",listOf(10,23,29,33,37,40),16)))
            db.dao().insertGames(listOf(GameRecord("one","batch",2,"1,2,3,4,5,6","MANUAL",true,"","","{}",1L)))
            try {
                repo.importDraws(listOf(Draw(2,"2002-12-14",listOf(9,13,21,25,32,42),2),Draw(1,"2002-12-07",listOf(1,2,3,4,5,6),7)))
                fail("conflict must reject entire import")
            } catch (_: IllegalArgumentException) { }
            assertEquals(1,db.dao().allDraws().size)
            assertEquals(1,db.dao().allGames().size)
            assertEquals("1,2,3,4,5,6",db.dao().allGames().single().numbers)
            assertEquals(0,repo.importDraws(listOf(Draw(1,"2002-12-07",listOf(10,23,29,33,37,40),16))))
        } finally { db.close() }
    }
}
