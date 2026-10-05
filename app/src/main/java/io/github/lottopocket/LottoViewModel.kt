package io.github.lottopocket

import android.app.Application
import androidx.compose.runtime.*
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import androidx.room.Room
import io.github.lottopocket.data.*
import io.github.lottopocket.domain.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.time.*
import java.time.temporal.TemporalAdjusters
import java.time.temporal.ChronoUnit
import java.util.UUID
import org.json.JSONObject
import org.json.JSONArray

class LottoViewModel(application: Application) : AndroidViewModel(application) {
    private val database = Room.databaseBuilder(application, LottoDatabase::class.java, "lotto-pocket.db").build()
    private val dao = database.dao()
    private val repository = LottoRepository(database)
    var draws by mutableStateOf<List<Draw>>(emptyList()); private set
    var records by mutableStateOf<List<GameRecord>>(emptyList()); private set
    var options by mutableStateOf(Options()); private set
    var round by mutableIntStateOf(upcomingRound()); private set
    var games by mutableStateOf<List<List<Int>>>(emptyList()); private set
    var locks by mutableStateOf<List<Set<Int>>>(emptyList()); private set
    var message by mutableStateOf<String?>(null)
    var busy by mutableStateOf(false); private set
    private var draftId = UUID.randomUUID().toString()
    private var draftTime = System.currentTimeMillis()
    private var draftOptions = "{}"
    var saved by mutableStateOf(false); private set
    val purchased: List<List<Int>> get() = records.filter { it.round == round && it.purchased }.map { it.values() }
    val candidates: List<Int> get() = LottoRules.candidates(purchased, draws, round, options)

    init {
        viewModelScope.launch { dao.observeDraws().collect { draws = it.map(DrawRecord::domain) } }
        viewModelScope.launch { dao.observeGames().collect { records = it } }
        work {
            dao.setting("options")?.let { options = DrawCodec.options(it) }
            withContext(Dispatchers.IO) {
                val seed = application.assets.open("draws.json").bufferedReader().use { it.readText() }
                repository.importDraws(DrawCodec.decode(seed))
            }
        }
    }

    private fun work(block: suspend () -> Unit) {
        if(busy){message="진행 중인 작업이 끝난 뒤 다시 시도해 주세요.";return}
        busy=true
        viewModelScope.launch {
            try { block() } catch(e: Exception) { message = e.message ?: "작업을 완료하지 못했어요." }
            finally { busy = false }
        }
    }
    private fun newDraft() { draftId = UUID.randomUUID().toString(); draftTime = System.currentTimeMillis(); saved = false }
    private fun clearDraft() { games = emptyList(); locks = emptyList(); newDraft() }

    fun applyOptions(value: Options) {
        if(value == options) return
        options = value; clearDraft()
        work { dao.putSetting(Setting("options",DrawCodec.options(value))) }
    }
    fun selectRound(value: Int) {
        if(value <= 0 || value > 10000) { message = "회차는 1~10000 사이로 입력해 주세요."; return }
        if(value != round) { round = value; clearDraft() }
    }
    fun generate(index: Int? = null) = work {
        if(options.frequency && draws.none { it.round < round }) {
            message = "이 회차 이전의 통계 데이터가 없어요. 빈도 반영을 꺼 주세요."
            return@work
        }
        val pool = candidates; val fixed = options.fixed; val count = options.count
        val purchase = purchased; val previous = games; val kept = locks
        val snapshot = JSONObject(DrawCodec.options(options))
            .put("statisticsThroughRound",draws.filter { it.round < round }.maxOfOrNull { it.round } ?: 0)
            .put("sourceTicketIds",JSONArray(records.filter { it.round==round && it.purchased }.map { it.id })).toString()
        val next = withContext(Dispatchers.Default) { LottoRules.generate(pool,fixed,count,purchase,previous,kept,index) }
        games = next
        draftOptions=snapshot
        if(locks.size != count) locks = List(count) { emptySet() }
        newDraft()
    }
    fun toggleLock(index: Int, number: Int) {
        if(number in options.fixed || index !in games.indices) return
        val next = locks.toMutableList()
        next[index] = if(number in next[index]) next[index]-number else next[index]+number
        locks = next
        newDraft()
    }
    fun unlock(index: Int? = null) { locks = locks.mapIndexed { i,s -> if(index == null || i==index) emptySet() else s }; newDraft() }
    fun registerTicket(targetRound: Int, text: String, source: String, onDone: () -> Unit) = work {
        require(targetRound in 1..10000) { "올바른 회차를 입력해 주세요." }
        require(source in listOf("QR","PHOTO","MANUAL"))
        val lines = LottoRules.parseManual(text)
        val previous = dao.allGames().filter { it.round==targetRound && it.purchased }.map { it.values().sorted() }.toSet()
        val fresh = lines.distinct().filter { it.sorted() !in previous }
        require(fresh.isNotEmpty()) { "이미 등록한 번호예요." }
        val batch = UUID.randomUUID().toString(); val timestamp = System.currentTimeMillis()
        dao.insertGames(fresh.mapIndexed { i,n -> GameRecord("$batch:$i",batch,targetRound,n.sorted().joinToString(","),source,true,"","","{}",timestamp) })
        records = dao.allGames()
        round = targetRound; clearDraft()
        message = "${fresh.size}게임을 등록했어요."
        onDone()
    }
    fun save() = work {
        require(games.isNotEmpty()) { "먼저 번호를 추천받아 주세요." }
        val snapshot = draftOptions
        val added=repository.saveRecommendations(games.mapIndexed { i,g -> GameRecord("$draftId:$i",draftId,round,g.joinToString(","),"RECOMMEND",false,
            options.fixed.sorted().joinToString(","),locks.getOrElse(i){emptySet()}.sorted().joinToString(","),snapshot,draftTime) })
        saved = true
        message = if(added==0) "이미 저장한 조합이에요." else "새 조합 ${added}개를 내 로또에 저장했어요."
    }
    fun purchase(record: GameRecord, value: Boolean) = work {
        dao.purchase(record.id,value)
        records=dao.allGames()
        if(record.round==round)clearDraft()
    }
    fun deleteRecords(ids: List<String>) = work {
        val removed = repository.deleteRecords(ids)
        records = dao.allGames()
        clearDraft()
        message = "${removed}개 기록을 삭제했어요."
    }
    fun resetOptions() = work {
        val defaults = Options()
        dao.putSetting(Setting("options", DrawCodec.options(defaults)))
        options = defaults
        clearDraft()
        message = "추천 설정을 초기화했어요."
    }
    fun resetPersonalData() = work {
        repository.resetPersonalData()
        records = dao.allGames()
        options = Options()
        clearDraft()
        message = "전체 개인 기록과 추천 설정을 초기화했어요."
    }
    fun addDraw(targetRound: Int, text: String, bonus: Int, onDone: () -> Unit) = work {
        require(targetRound in 1..10000) { "회차를 확인해 주세요." }
        val lines = LottoRules.parseManual(text)
        require(lines.size==1) { "당첨번호는 한 줄만 입력해 주세요." }
        repository.importDraws(listOf(Draw(targetRound,drawDate(targetRound).toString(),lines.single(),bonus,"직접 입력")))
        draws=dao.allDraws().map(DrawRecord::domain)
        message = "${targetRound}회 결과를 저장하고 당첨 여부를 확인했어요."
        onDone()
    }
    fun importText(text: String) = work {
        message = withContext(Dispatchers.IO) { repository.importFile(text) }
        dao.setting("options")?.let { options=DrawCodec.options(it) }
        clearDraft()
    }
    fun export(write: suspend (String) -> Unit) = work { withContext(Dispatchers.IO) { write(repository.exportBackup()) }; message="백업 파일을 저장했어요." }
    override fun onCleared() { super.onCleared(); database.close() }
}

fun drawDate(round: Int): LocalDate = LocalDate.of(2002,12,7).plusWeeks((round-1).toLong())
fun upcomingRound(): Int {
    val now = ZonedDateTime.now(ZoneId.of("Asia/Seoul"))
    var date = now.toLocalDate().with(TemporalAdjusters.nextOrSame(DayOfWeek.SATURDAY))
    if(now.dayOfWeek==DayOfWeek.SATURDAY && now.toLocalTime()>=LocalTime.of(20,0)) date=date.plusWeeks(1)
    return (ChronoUnit.WEEKS.between(LocalDate.of(2002,12,7),date)+1).toInt().coerceAtLeast(1)
}
