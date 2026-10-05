package io.github.lottopocket

import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.geometry.Offset
import org.junit.Assert.assertEquals
import android.graphics.Bitmap
import android.graphics.Canvas
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.io.File

@RunWith(RobolectricTestRunner::class)
@Config(sdk=[35],qualifiers="w360dp-h800dp")
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class AppSmokeTest {
    @get:Rule val ui=createAndroidComposeRule<MainActivity>()
    @Test fun optionsScrollKeepsSheetStationaryAtBothEdges() {
        ui.waitUntil(30000) { ui.onAllNodesWithText("추천 설정").fetchSemanticsNodes().any { !it.config.contains(SemanticsProperties.Disabled) } }
        ui.onNodeWithText("추천 설정").performClick()
        val titleTop=ui.onNodeWithTag("options-title").fetchSemanticsNode().boundsInRoot.top
        val form=ui.onNodeWithTag("options-scroll")
        form.performTouchInput { down(center);moveBy(Offset(0f,150f)) }
        ui.waitForIdle()
        assertEquals(titleTop,ui.onNodeWithTag("options-title").fetchSemanticsNode().boundsInRoot.top,1f)
        form.performTouchInput { up() }
        ui.onNode(isToggleable()).performScrollTo().performClick()
        repeat(3) { form.performTouchInput { swipeUp() } }
        ui.onNodeWithText("통계 기간").assertIsDisplayed()
        assertEquals(titleTop,ui.onNodeWithTag("options-title").fetchSemanticsNode().boundsInRoot.top,1f)
        ui.onNodeWithText("설정 적용").assertIsDisplayed()
        repeat(3) { form.performTouchInput { swipeDown() } }
        ui.onNodeWithText("추천 게임 수").assertIsDisplayed()
        ui.onNodeWithText("닫기").performClick()
        ui.onNodeWithText("5게임 추천받기").assertIsDisplayed()
    }
    @Test fun historyDeletionRequiresConfirmationAndCanBeSavedAgain() {
        ui.waitUntil(30000) { ui.onAllNodesWithText("5게임 추천받기").fetchSemanticsNodes().any { !it.config.contains(SemanticsProperties.Disabled) } }
        ui.onNodeWithText("5게임 추천받기").performClick()
        ui.waitUntil(15000) { ui.onAllNodesWithText("추천 저장").fetchSemanticsNodes().isNotEmpty() }
        ui.onNodeWithText("추천 저장").performClick()
        ui.waitUntil(15000) { ui.onAllNodesWithText("저장됨").fetchSemanticsNodes().isNotEmpty() }
        ui.onNodeWithText("내 로또").performClick()
        ui.onNodeWithTag("main-content").performScrollToNode(isToggleable())
        ui.onAllNodes(isToggleable()).onFirst().performClick()
        ui.waitUntil(15000) { ui.onAllNodesWithText("기록 관리").fetchSemanticsNodes().any { !it.config.contains(SemanticsProperties.Disabled) } }
        ui.onNodeWithText("기록 관리").performClick()
        ui.onNodeWithText("이 회차 미구매 추천 삭제").performClick()
        ui.onNodeWithText("취소").performClick()
        ui.onAllNodesWithText("추천 기록").onFirst().assertExists()
        ui.onNodeWithText("기록 관리").performClick()
        ui.onNodeWithText("이 회차 미구매 추천 삭제").performClick()
        ui.onNodeWithText("삭제",substring=false).performClick()
        ui.waitUntil(15000) { ui.onAllNodesWithText("기록 관리").fetchSemanticsNodes().any { !it.config.contains(SemanticsProperties.Disabled) } }
        ui.onAllNodesWithText("추천 기록").onFirst().assertExists()
        ui.onNodeWithTag("main-content").performScrollToNode(hasText("기록 삭제"))
        ui.onNodeWithText("기록 삭제").performClick()
        ui.onNodeWithText("삭제",substring=false).performClick()
        ui.waitUntil(15000) { ui.onAllNodesWithText("이 회차에 저장한 번호가 없어요.").fetchSemanticsNodes().isNotEmpty() }
        ui.onNodeWithText("번호 추천").performClick()
        ui.onNodeWithText("5게임 추천받기").assertIsDisplayed().performClick()
        ui.waitUntil(15000) { ui.onAllNodesWithText("추천 저장").fetchSemanticsNodes().any { !it.config.contains(SemanticsProperties.Disabled) } }
        ui.onNodeWithText("내 로또").performClick()
        ui.onNodeWithText("기록 관리").performClick()
        ui.onNodeWithText("백업·전체 초기화").performClick()
        ui.onNodeWithText("전체 개인 기록 초기화").performClick()
        ui.onNodeWithText("초기화",substring=false).performClick()
        ui.onNodeWithText("번호 통계").performClick()
        ui.onNodeWithText("내부 DB 1241개 회차").assertExists()
    }
    @Test fun generatesSavesAndShowsHistoryWithOfflineSeed() {
        ui.waitUntil(30000) {
            ui.onAllNodesWithText("5게임 추천받기").fetchSemanticsNodes().any { !it.config.contains(SemanticsProperties.Disabled) }
        }
        ui.onNodeWithText("5게임 추천받기").performClick()
        ui.waitUntil(15000) { ui.onAllNodesWithTag("recommendation-game").fetchSemanticsNodes().isNotEmpty() }
        ui.onNodeWithText("추천 저장").performClick()
        ui.waitUntil(15000) { ui.onAllNodesWithText("저장됨").fetchSemanticsNodes().isNotEmpty() }
        ui.onNodeWithText("내 로또").performClick()
        ui.onAllNodesWithText("추천 기록").onFirst().assertIsDisplayed()
        val output=File("build/reports/ui-screenshots/history.png")
        output.parentFile?.mkdirs()
        ui.runOnIdle {
            val view=ui.activity.window.decorView
            val bitmap=Bitmap.createBitmap(view.width,view.height,Bitmap.Config.ARGB_8888)
            view.draw(Canvas(bitmap))
            output.outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG,100,it) }
            bitmap.recycle()
        }
        ui.onNodeWithText("번호 통계").performClick()
        ui.onNodeWithText("내부 DB 1241개 회차").assertIsDisplayed()
    }
    @Test fun fixedSettingsSurviveRotationAndApplyToNewGames() {
        ui.waitUntil(30000) { ui.onAllNodesWithText("추천 설정").fetchSemanticsNodes().any { !it.config.contains(SemanticsProperties.Disabled) } }
        ui.onNodeWithText("추천 설정").performClick()
        ui.onNodeWithText("−").performClick()
        ui.onNodeWithText("고정번호 0~5개").performTextInput("7")
        ui.onNodeWithText("끝에 번호 구분 공백 추가").performScrollTo().performClick()
        ui.onNodeWithText("고정번호 0~5개").performTextInput("21")
        ui.activityRule.scenario.recreate()
        ui.onNodeWithText("4개").assertExists()
        ui.onNodeWithText("설정 적용").assertIsDisplayed().performClick()
        ui.waitUntil(15000) { ui.onAllNodesWithText("4게임 추천받기").fetchSemanticsNodes().any { !it.config.contains(SemanticsProperties.Disabled) } }
        ui.onNodeWithText("4게임 추천받기").performClick()
        ui.waitUntil(15000) { ui.onAllNodesWithText("추천 저장").fetchSemanticsNodes().isNotEmpty() }
        ui.onNodeWithTag("main-content").performScrollToNode(hasText("공통 고정: 7, 21"))
        ui.onAllNodesWithText("공통 고정: 7, 21").onFirst().assertIsDisplayed()
    }
    @Test fun dateValidationIsLocalAndApplyRemainsVisibleWhenFrequencyExpands() {
        ui.waitUntil(30000) { ui.onAllNodesWithText("추천 설정").fetchSemanticsNodes().any { !it.config.contains(SemanticsProperties.Disabled) } }
        ui.onNodeWithText("추천 설정").performClick()
        ui.onNodeWithText("생일·기념일 숫자 추가").performScrollTo().performClick()
        ui.onNodeWithText("월과 일을 모두 입력해 주세요.").performScrollTo().assertIsDisplayed()
        ui.onNode(isToggleable()).performScrollTo().performClick()
        ui.onNodeWithText("설정 적용").assertIsDisplayed()
    }

}
