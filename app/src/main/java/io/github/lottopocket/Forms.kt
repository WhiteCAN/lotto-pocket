package io.github.lottopocket

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.saveable.Saver
import androidx.compose.ui.Modifier
import androidx.compose.ui.Alignment
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.unit.dp
import io.github.lottopocket.domain.LottoRules
import io.github.lottopocket.domain.Options
import io.github.lottopocket.data.DrawCodec

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun BottomSheet(title: String, close: () -> Unit, message:String? = null, content: @Composable ColumnScope.() -> Unit) {
    ModalBottomSheet(onDismissRequest=close, sheetState=rememberModalBottomSheetState(skipPartiallyExpanded=true)) {
        Column(Modifier.fillMaxWidth().imePadding().verticalScroll(rememberScrollState()).padding(horizontal=20.dp).padding(bottom=24.dp), verticalArrangement=Arrangement.spacedBy(14.dp)) {
            Text(title,style=MaterialTheme.typography.headlineSmall)
            message?.let { Text(it,color=MaterialTheme.colorScheme.error) }
            content()
        }
    }
}

@Composable
fun StepChoice(label:String,value:Int,range:IntRange,onChange:(Int)->Unit) {
    Row(Modifier.fillMaxWidth(),horizontalArrangement=Arrangement.spacedBy(12.dp),verticalAlignment=androidx.compose.ui.Alignment.CenterVertically) {
        Column(Modifier.weight(1f)) { Text(label); Text("${value}개",style=MaterialTheme.typography.titleLarge) }
        OutlinedButton(onClick={onChange(value-1)},enabled=value>range.first,modifier=Modifier.sizeIn(minWidth=48.dp,minHeight=48.dp)) { Text("−") }
        OutlinedButton(onClick={onChange(value+1)},enabled=value<range.last,modifier=Modifier.sizeIn(minWidth=48.dp,minHeight=48.dp)) { Text("+") }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun OptionsSheet(original:Options,hasDraft:Boolean,close:()->Unit,apply:(Options)->Unit) {
    ModalBottomSheet(
        onDismissRequest=close,
        sheetState=rememberModalBottomSheetState(skipPartiallyExpanded=true),
        // Only the form scrolls; dragging the sheet competes with its nested scroll and IME layout.
        sheetGesturesEnabled=false,
        dragHandle=null,
    ) {
        Column(Modifier.fillMaxHeight(0.92f).imePadding().padding(horizontal=20.dp)) {
            Row(Modifier.fillMaxWidth(),verticalAlignment=Alignment.CenterVertically) {
                Text("추천 설정",modifier=Modifier.weight(1f).testTag("options-title"),style=MaterialTheme.typography.headlineSmall)
                TextButton(onClick=close) { Text("닫기") }
            }
            OptionsForm(original,hasDraft,Modifier.weight(1f),apply)
        }
    }
}

@Composable
fun OptionsForm(original:Options,hasDraft:Boolean,modifier:Modifier=Modifier,apply:(Options)->Unit) {
    var value by rememberSaveable(stateSaver=Saver(save={DrawCodec.options(it)},restore={DrawCodec.options(it)})) { mutableStateOf(original) }
    var fixed by rememberSaveable(stateSaver=TextFieldValue.Saver) { mutableStateOf(TextFieldValue(original.fixed.sorted().joinToString(" "))) }
    var month by rememberSaveable { mutableStateOf("") }
    var day by rememberSaveable { mutableStateOf("") }
    var error by remember { mutableStateOf<String?>(null) }
    var dateError by remember { mutableStateOf<String?>(null) }
    fun parsed():Set<Int> {
        val nums=if(fixed.text.isBlank()) emptySet() else fixed.text.trim().split(Regex("[\\s,]+" )).map { it.toIntOrNull() ?: error("숫자만 입력해 주세요.") }.toSet()
        require(nums.size<=5 && nums.all { it in 1..45 }) { "고정번호는 1~45에서 최대 5개예요." }
        return nums
    }
    Column(modifier.fillMaxWidth()) {
    Column(Modifier.weight(1f).testTag("options-scroll").verticalScroll(rememberScrollState()).padding(vertical=14.dp),verticalArrangement=Arrangement.spacedBy(14.dp)) {
    StepChoice("추천 게임 수",value.count,1..5) { value=value.copy(count=it) }
    HorizontalDivider()
    Text("나만의 고정번호",style=MaterialTheme.typography.titleMedium)
    Text("모든 게임에 넣고 나머지만 추천해요. 구매번호 제외보다 우선합니다.",style=MaterialTheme.typography.bodyMedium)
    OutlinedTextField(value=fixed,onValueChange={fixed=it},label={Text("고정번호 0~5개")},placeholder={Text("예: 7 21 32")},keyboardOptions=KeyboardOptions(keyboardType=KeyboardType.Number),modifier=Modifier.fillMaxWidth())
    TextButton(onClick={if(fixed.text.isNotBlank()) { val text=fixed.text.trimEnd()+" ";fixed=TextFieldValue(text,TextRange(text.length)) }},modifier=Modifier.fillMaxWidth()) { Text("끝에 번호 구분 공백 추가") }
    Row(horizontalArrangement=Arrangement.spacedBy(10.dp)) {
        NumberField(month,{month=it;dateError=null},"월",Modifier.weight(1f))
        NumberField(day,{day=it;dateError=null},"일",Modifier.weight(1f))
    }
    dateError?.let { Text(it,color=MaterialTheme.colorScheme.error) }
    OutlinedButton(onClick={runCatching {
        val m=month.toIntOrNull();val d=day.toIntOrNull()
        require(m!=null && d!=null) { "월과 일을 모두 입력해 주세요." }
        val next=parsed()+LottoRules.fixedFromDate(m,d)
        require(next.size<=5) { "최대 5개까지 고정할 수 있어요." }
        val text=next.sorted().joinToString(" ");fixed=TextFieldValue(text,TextRange(text.length));dateError=null
    }.onFailure { dateError=it.message ?: "월과 일을 확인해 주세요." }},modifier=Modifier.fillMaxWidth()) { Text("생일·기념일 숫자 추가") }
    Text("7월 21일 → 7, 21 · 같은 숫자는 한 번만 · 연도 제외",style=MaterialTheme.typography.bodySmall)
    HorizontalDivider()
    Row(Modifier.fillMaxWidth(),horizontalArrangement=Arrangement.SpaceBetween,verticalAlignment=androidx.compose.ui.Alignment.CenterVertically) {
        Text("자주 나온 번호 반영",Modifier.weight(1f));Switch(checked=value.frequency,onCheckedChange={value=value.copy(frequency=it)})
    }
    if(value.frequency) {
        FlowRow(horizontalArrangement=Arrangement.spacedBy(8.dp)) {
            FilterChip(selected=!value.registeredMode,onClick={value=value.copy(registeredMode=false)},label={Text("전체 순위")})
            FilterChip(selected=value.registeredMode,onClick={value=value.copy(registeredMode=true)},label={Text("구매번호 내 순위")})
        }
        if(value.registeredMode) StepChoice("복원할 번호",value.registeredCount,1..5){value=value.copy(registeredCount=it)}
        else {
            StepChoice("전체 상위 몇 개까지?",value.topCount,1..45){value=value.copy(topCount=it)}
            Slider(value=value.topCount.toFloat(),onValueChange={value=value.copy(topCount=it.toInt())},valueRange=1f..45f,steps=43)
            Text(if(value.topCount==45) "전체 45개가 다시 후보가 됩니다." else "상위 ${value.topCount}개 중 구매번호에 있는 숫자만 복원해요.",style=MaterialTheme.typography.bodySmall)
        }
        PeriodChoice(value.last){value=value.copy(last=it)}
    }
    if(hasDraft) Text("설정을 변경하면 저장 전 추천과 게임별 잠금이 초기화돼요.",color=MaterialTheme.colorScheme.primary)
    }
    error?.let { Text(it,color=MaterialTheme.colorScheme.error) }
    Button(onClick={runCatching { apply(value.copy(fixed=parsed())) }.onFailure { error=it.message }},modifier=Modifier.fillMaxWidth().padding(vertical=12.dp).heightIn(min=52.dp)) { Text("설정 적용") }
    }
}

@Composable
fun NumberField(value:String,change:(String)->Unit,label:String,modifier:Modifier=Modifier) {
    OutlinedTextField(value=value,onValueChange={if(it.length<=6 && it.all(Char::isDigit))change(it)},label={Text(label)},singleLine=true,
        keyboardOptions=KeyboardOptions(keyboardType=KeyboardType.Number),modifier=modifier)
}

@Composable
fun PeriodChoice(selected:Int,change:(Int)->Unit) {
    Text("통계 기간",style=MaterialTheme.typography.titleSmall)
    FlowRow(horizontalArrangement=Arrangement.spacedBy(6.dp)) {
        listOf(0,100,50).forEach { period -> FilterChip(selected=period==selected,onClick={change(period)},label={Text(if(period==0) "전체" else "최근 ${period}회")}) }
    }
}
