package io.github.lottopocket

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import io.github.lottopocket.data.DrawCodec
import io.github.lottopocket.domain.LottoRules
import io.github.lottopocket.ticket.CaptureInput
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.ByteArrayOutputStream
import java.io.InputStream

@Composable
fun LottoTheme(content:@Composable ()->Unit) {
    val colors=if(isSystemInDarkTheme()) darkColorScheme(primary=Color(0xFF9FD4B3),background=Color(0xFF151C18),surface=Color(0xFF1E2821))
        else lightColorScheme(primary=Color(0xFF246447),background=Color(0xFFF5F7F3),surface=Color.White,secondaryContainer=Color(0xFFE4EFE5))
    MaterialTheme(colorScheme=colors,content=content)
}

@Composable
fun LottoApp(vm:LottoViewModel) {
    var tab by rememberSaveable { mutableIntStateOf(0) }
    var sheet by rememberSaveable { mutableStateOf("") }
    var sheetNotice by remember { mutableStateOf<String?>(null) }
    var ticketText by rememberSaveable { mutableStateOf("") }
    var ticketRound by rememberSaveable { mutableStateOf("") }
    var ticketSource by rememberSaveable { mutableStateOf("MANUAL") }
    var editIndex by rememberSaveable { mutableIntStateOf(0) }
    var statsPeriod by rememberSaveable { mutableIntStateOf(0) }
    val snack=remember { SnackbarHostState() }
    val context=LocalContext.current
    val scope=rememberCoroutineScope()
    val export=rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/json")) { uri ->
        if(uri!=null)vm.export { text -> context.contentResolver.openOutputStream(uri,"wt")?.bufferedWriter()?.use { it.write(text) } ?: error("파일을 열 수 없어요.") }
    }
    val import=rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if(uri!=null)scope.launch {
            runCatching { withContext(Dispatchers.IO) { context.contentResolver.openInputStream(uri)?.use(::readLimited) ?: error("파일을 열 수 없어요.") } }
                .onSuccess(vm::importText).onFailure { vm.message=it.message }
        }
    }
    LaunchedEffect(sheet) { sheetNotice=null }
    LaunchedEffect(vm.message) { vm.message?.let { message -> vm.message=null;if(sheet.isNotEmpty())sheetNotice=message else snack.showSnackbar(message) } }
    fun openRegister() { ticketRound=vm.round.toString();ticketText="";ticketSource="MANUAL";sheet="register" }

    Scaffold(
        snackbarHost={SnackbarHost(snack)},
        bottomBar={
            Surface(shadowElevation=6.dp) {
                Column(Modifier.navigationBarsPadding().imePadding()) {
                    Column(Modifier.padding(horizontal=16.dp,vertical=8.dp),verticalArrangement=Arrangement.spacedBy(8.dp)) {
                        if(tab==0) {
                            Row(horizontalArrangement=Arrangement.spacedBy(8.dp)) {
                                OutlinedButton(onClick=::openRegister,enabled=!vm.busy,modifier=Modifier.weight(1f)) { Text("용지 등록") }
                                OutlinedButton(onClick={sheet="options"},enabled=!vm.busy,modifier=Modifier.weight(1f)) { Text("추천 설정") }
                            }
                            if(vm.games.isEmpty()) Button(onClick={vm.generate()},enabled=!vm.busy,modifier=Modifier.fillMaxWidth().heightIn(min=52.dp)) { Text("${vm.options.count}게임 추천받기") }
                            else {
                                Row(horizontalArrangement=Arrangement.spacedBy(8.dp)) {
                                    OutlinedButton(onClick={vm.generate()},enabled=!vm.busy,modifier=Modifier.weight(1f)) { Text("전체 다시 뽑기") }
                                    Button(onClick={vm.save()},enabled=!vm.busy&&!vm.saved,modifier=Modifier.weight(1f)) { Text(if(vm.saved) "저장됨" else "추천 저장") }
                                }
                                TextButton(onClick={editIndex=0;sheet="edit"},enabled=!vm.busy,modifier=Modifier.fillMaxWidth()) { Text("번호 편집 · 잠금") }
                            }
                        } else if(tab==1) {
                            Row(horizontalArrangement=Arrangement.spacedBy(8.dp)) {
                                OutlinedButton(onClick=::openRegister,enabled=!vm.busy,modifier=Modifier.weight(1f)) { Text("용지 등록") }
                                Button(onClick={ticketRound=vm.round.toString();sheet="draw"},enabled=!vm.busy,modifier=Modifier.weight(1f)) { Text("당첨 결과 입력") }
                            }
                        } else Button(onClick={sheet="data"},enabled=!vm.busy,modifier=Modifier.fillMaxWidth()) { Text("내부 데이터 관리") }
                    }
                    NavigationBar(windowInsets=WindowInsets(0,0,0,0)) {
                        val icons=listOf(Icons.Outlined.AutoAwesome,Icons.Outlined.ConfirmationNumber,Icons.Outlined.BarChart)
                        listOf("번호 추천","내 로또","번호 통계").forEachIndexed { i,label -> NavigationBarItem(selected=tab==i,onClick={tab=i},icon={Icon(icons[i],null)},label={Text(label)}) }
                    }
                }
            }
        },
    ) { padding ->
        LazyColumn(Modifier.fillMaxSize().padding(padding).testTag("main-content"),contentPadding=PaddingValues(20.dp),verticalArrangement=Arrangement.spacedBy(14.dp)) {
            item {
                Row(verticalAlignment=Alignment.CenterVertically,horizontalArrangement=Arrangement.spacedBy(8.dp)) {
                    Surface(shape=RoundedCornerShape(10.dp),color=MaterialTheme.colorScheme.primary) { Text("6",Modifier.padding(horizontal=10.dp,vertical=4.dp),fontWeight=FontWeight.Bold,color=MaterialTheme.colorScheme.onPrimary) }
                    Text("로또 포켓",style=MaterialTheme.typography.titleMedium)
                }
                Spacer(Modifier.height(20.dp))
                Text(listOf("내 번호 다음의 조합","내 번호를 모아두는 곳","숫자의 지난 기록")[tab],style=MaterialTheme.typography.headlineSmall,fontWeight=FontWeight.Bold)
                Text(if(tab==0) "고정하고, 제외하고, 나만의 조합을 만들어요." else if(tab==1) "실제 구매와 추천 기록을 따로 확인해요." else "인터넷 없이 휴대폰 안의 기록으로 계산해요.",style=MaterialTheme.typography.bodyMedium)
            }
            if(vm.busy)item { LinearProgressIndicator(Modifier.fillMaxWidth()) }
            if(tab!=2)item {
                Section {
                    Text("제 ${vm.round}회 · ${drawDate(vm.round)}",style=MaterialTheme.typography.titleMedium)
                    TextButton(onClick={ticketRound=vm.round.toString();sheet="round"},enabled=!vm.busy) { Text("회차 변경") }
                }
            }
            when(tab) {
                0 -> {
                    item { Section {
                        Text("고정 ${vm.options.fixed.size}개 + 추천 ${6-vm.options.fixed.size}개",style=MaterialTheme.typography.titleMedium)
                        if(vm.options.fixed.isNotEmpty()) NumberBalls(vm.options.fixed.sorted())
                        Text("구매 ${vm.purchased.size}게임 · 최종 후보 ${vm.candidates.size}개",style=MaterialTheme.typography.bodyMedium)
                        Text(if(vm.options.frequency) "빈도 반영 ON · ${if(vm.options.registeredMode) "구매번호 상위 ${vm.options.registeredCount}개" else "전체 상위 ${vm.options.topCount}개"}" else "빈도 반영 OFF",style=MaterialTheme.typography.bodySmall)
                    } }
                    if(vm.games.isEmpty()) item { Section {
                        Text("가지고 있는 용지를 등록하거나\n고정번호부터 선택해 보세요.",style=MaterialTheme.typography.titleMedium)
                        Text("QR·사진·수동 입력을 지원해요. 용지 없이도 추천받을 수 있어요.")
                    } }
                    items(vm.games.size) { index ->
                        Section(Modifier.testTag("recommendation-game")) {
                            Text("${'A'+index} 게임",style=MaterialTheme.typography.titleMedium)
                            NumberBalls(vm.games[index],vm.options.fixed+vm.locks.getOrElse(index){emptySet()})
                            if(vm.options.fixed.isNotEmpty())Text("공통 고정: ${vm.options.fixed.sorted().joinToString(", ")}",style=MaterialTheme.typography.bodySmall)
                            Row(horizontalArrangement=Arrangement.spacedBy(8.dp)) {
                                TextButton(onClick={editIndex=index;sheet="edit"},enabled=!vm.busy,modifier=Modifier.testTag("edit-game-${'A'+index}")) { Text("번호 편집") }
                                TextButton(onClick={vm.generate(index)},enabled=!vm.busy&&(vm.options.fixed+vm.locks.getOrElse(index){emptySet()}).size<6) { Text("다시 뽑기") }
                            }
                        }
                    }
                }
                1 -> {
                    val selected=vm.records.filter { it.round==vm.round }
                    val draw=vm.draws.find { it.round==vm.round }
                    if(selected.isEmpty())item { Section { Text("이 회차에 저장한 번호가 없어요.") } }
                    items(selected,key={it.id}) { record ->
                        Section {
                            Text(if(record.source=="RECOMMEND") "추천 기록" else "구매 용지 · ${record.source}",style=MaterialTheme.typography.titleMedium)
                            NumberBalls(record.values(),draw?.numbers?.toSet()?:emptySet())
                            val prize=draw?.let { LottoRules.judge(record.values(),it) }
                            Text(if(draw==null) "결과 미등록" else "${if(record.purchased) "구매 결과" else "미구매 추천 성적"}: ${prize?.let { "${it}등" }?:"미당첨"}",color=MaterialTheme.colorScheme.primary)
                            if(record.fixed.isNotBlank()) Text("고정번호 ${record.fixed}",style=MaterialTheme.typography.bodySmall)
                            if(record.source=="RECOMMEND") Row(verticalAlignment=Alignment.CenterVertically) {
                                Checkbox(checked=record.purchased,onCheckedChange={vm.purchase(record,it)},enabled=!vm.busy)
                                Text("실제로 구매한 게임")
                            }
                        }
                    }
                }
                2 -> {
                    item { Section {
                        Text("내부 DB ${vm.draws.size}개 회차",style=MaterialTheme.typography.titleMedium)
                        Text(vm.draws.lastOrNull()?.let { "${vm.draws.first().round}~${it.round}회 · 최근 ${it.date}" }?:"저장된 추첨 결과가 없어요.")
                        Text("출처: 동행복권 공식 결과 / 직접 입력·가져오기",style=MaterialTheme.typography.bodySmall)
                        PeriodChoice(statsPeriod){statsPeriod=it}
                    } }
                    if(vm.draws.isNotEmpty()) items(LottoRules.frequencies(vm.draws, (vm.draws.maxOfOrNull{it.round}?:0)+1,statsPeriod),key={it.number}) { stat ->
                        Row(Modifier.fillMaxWidth(),horizontalArrangement=Arrangement.spacedBy(16.dp),verticalAlignment=Alignment.CenterVertically) {
                            Text(stat.number.toString(),Modifier.width(36.dp),style=MaterialTheme.typography.titleMedium)
                            LinearProgressIndicator(progress={ stat.count.toFloat() / (if(statsPeriod==0) vm.draws.size else minOf(statsPeriod,vm.draws.size)).coerceAtLeast(1) },modifier=Modifier.weight(1f))
                            Text("${stat.count}회",Modifier.widthIn(min=48.dp))
                        }
                    }
                }
            }
        }
    }
    when(sheet) {
        "options" -> BottomSheet("추천 설정",{if(!vm.busy)sheet=""},message=sheetNotice) { OptionsForm(vm.options,vm.games.isNotEmpty()){vm.applyOptions(it);sheet=""} }
        "register" -> BottomSheet("용지 등록",{if(!vm.busy)sheet=""},message=sheetNotice) {
            Text("촬영한 번호는 확인 후에만 저장됩니다.")
            CaptureInput(onDraft={round,text,source -> ticketRound=(round?:vm.round).toString();ticketText=text;ticketSource=source;sheet="review"},onError={vm.message=it})
            OutlinedButton(onClick={sheet="review"},modifier=Modifier.fillMaxWidth()) { Text("직접 입력") }
        }
        "review" -> BottomSheet("번호 확인·수정",{if(!vm.busy)sheet=""},message=sheetNotice) {
            NumberField(ticketRound,{ticketRound=it},"회차",Modifier.fillMaxWidth())
            Text("원본 용지와 비교해 회차와 번호를 확인해 주세요. 한 줄에 6개, 최대 5게임입니다.")
            OutlinedTextField(value=ticketText,onValueChange={ticketText=it},label={Text("A~E 게임 번호")},placeholder={Text("3 11 18 24 32 41")},minLines=5,modifier=Modifier.fillMaxWidth())
            Button(onClick={vm.registerTicket(ticketRound.toIntOrNull()?:0,ticketText,ticketSource){sheet="";tab=0}},enabled=!vm.busy,modifier=Modifier.fillMaxWidth()) { Text("이 번호로 등록") }
        }
        "round" -> BottomSheet("회차 선택",{if(!vm.busy)sheet=""},message=sheetNotice) {
            NumberField(ticketRound,{ticketRound=it},"회차",Modifier.fillMaxWidth())
            Text("회차를 바꾸면 저장 전 추천과 잠금이 초기화돼요.")
            Button(onClick={val n=ticketRound.toIntOrNull();if(n!=null&&n in 1..10000){vm.selectRound(n);sheet=""}else vm.message="올바른 회차를 입력해 주세요."},modifier=Modifier.fillMaxWidth()) { Text("이 회차 보기") }
        }
        "draw" -> BottomSheet("당첨 결과 입력",{if(!vm.busy)sheet=""},message=sheetNotice) {
            var numbers by rememberSaveable { mutableStateOf("") };var bonus by rememberSaveable { mutableStateOf("") }
            NumberField(ticketRound,{ticketRound=it},"회차",Modifier.fillMaxWidth())
            OutlinedTextField(value=numbers,onValueChange={numbers=it},label={Text("당첨번호 6개")},modifier=Modifier.fillMaxWidth())
            NumberField(bonus,{bonus=it},"보너스번호",Modifier.fillMaxWidth())
            Text("기존 회차와 다른 결과는 덮어쓰지 않습니다.",style=MaterialTheme.typography.bodySmall)
            Button(onClick={vm.addDraw(ticketRound.toIntOrNull()?:0,numbers,bonus.toIntOrNull()?:0){sheet=""}},enabled=!vm.busy,modifier=Modifier.fillMaxWidth()) { Text("저장하고 당첨 확인") }
        }
        "edit" -> if(vm.games.isNotEmpty()) BottomSheet("번호 잠금",{if(!vm.busy)sheet=""},message=sheetNotice) {
            val index=editIndex.coerceIn(vm.games.indices)
            Row(horizontalArrangement=Arrangement.spacedBy(4.dp)) {
                vm.games.indices.forEach { i -> FilterChip(selected=index==i,onClick={editIndex=i},label={Text("${'A'+i}")}) }
            }
            Text("공통 고정번호는 추천 설정에서 바꿀 수 있어요.")
            vm.games[index].chunked(3).forEach { row -> Row(horizontalArrangement=Arrangement.spacedBy(10.dp)) {
                row.forEach { n ->
                    val common=n in vm.options.fixed;val locked=n in vm.locks.getOrElse(index){emptySet()}
                    OutlinedButton(onClick={vm.toggleLock(index,n)},enabled=!vm.busy&&!common,modifier=Modifier.weight(1f).heightIn(min=72.dp)) {
                        Column(horizontalAlignment=Alignment.CenterHorizontally) { Text(n.toString(),style=MaterialTheme.typography.titleLarge);Text(if(common) "공통 고정" else if(locked) "잠금 중" else "잠그기",style=MaterialTheme.typography.labelSmall) }
                    }
                }
            } }
            OutlinedButton(onClick={vm.unlock(index)},enabled=!vm.busy,modifier=Modifier.fillMaxWidth()) { Text("이 게임 잠금 해제") }
            Button(onClick={vm.generate(index)},enabled=!vm.busy&&(vm.options.fixed+vm.locks.getOrElse(index){emptySet()}).size<6,modifier=Modifier.fillMaxWidth()) { Text("나머지 다시 뽑기") }
            TextButton(onClick={vm.unlock()},enabled=!vm.busy,modifier=Modifier.fillMaxWidth()) { Text("모든 게임 잠금 해제") }
            TextButton(onClick={sheet=""},modifier=Modifier.fillMaxWidth()) { Text("편집 완료") }
        }
        "data" -> BottomSheet("내부 데이터 관리",{if(!vm.busy)sheet=""},message=sheetNotice) {
            Text("당첨번호와 개인 기록은 휴대폰 내부 DB에 저장돼요. 앱을 삭제하기 전에 백업해 주세요.")
            Button(onClick={sheet="";import.launch(arrayOf("application/json","text/plain"))},modifier=Modifier.fillMaxWidth()) { Text("회차 데이터·백업 가져오기") }
            OutlinedButton(onClick={sheet="";export.launch("lotto-pocket-backup.json")},modifier=Modifier.fillMaxWidth()) { Text("개인 기록과 DB 백업") }
            OutlinedButton(onClick={ticketRound=vm.round.toString();sheet="draw"},modifier=Modifier.fillMaxWidth()) { Text("새 당첨 결과 직접 입력") }
            Text("v0.1.0 · 앱 내 번호 조합과 통계는 당첨을 예측하지 않습니다. 복권 구매 기능은 제공하지 않습니다.",style=MaterialTheme.typography.bodySmall)
        }
    }
}

@Composable
fun Section(modifier:Modifier=Modifier,content:@Composable ColumnScope.()->Unit) {
    Card(modifier.fillMaxWidth(),shape=RoundedCornerShape(20.dp),colors=CardDefaults.cardColors(containerColor=MaterialTheme.colorScheme.surface)) {
        Column(Modifier.padding(18.dp),verticalArrangement=Arrangement.spacedBy(10.dp),content=content)
    }
}

@Composable
fun NumberBalls(numbers:List<Int>,marked:Set<Int> = emptySet()) {
    Row(Modifier.fillMaxWidth(),horizontalArrangement=Arrangement.spacedBy(5.dp)) {
        numbers.forEach { n ->
            val color=when(n){in 1..10->Color(0xFFF5E7B8);in 11..20->Color(0xFFD9EAF7);in 21..30->Color(0xFFF5DAD5);in 31..40->Color(0xFFE0E3DD);else->Color(0xFFDDEED5)}
            Column(Modifier.weight(1f),horizontalAlignment=Alignment.CenterHorizontally) {
                Box(Modifier.fillMaxWidth().aspectRatio(1f).background(color,CircleShape),contentAlignment=Alignment.Center) { Text(n.toString(),color=Color(0xFF263A2B),fontWeight=FontWeight.Bold) }
                if(n in marked)Text("●",color=MaterialTheme.colorScheme.primary,style=MaterialTheme.typography.labelSmall)
            }
        }
    }
}

private fun readLimited(input:InputStream):String {
    val output=ByteArrayOutputStream();val buffer=ByteArray(8192)
    while(true){val count=input.read(buffer);if(count<0)break;require(output.size()+count<=DrawCodec.MAX_BYTES){"파일은 5MB 이하만 가져올 수 있어요."};output.write(buffer,0,count)}
    return output.toString("UTF-8")
}
