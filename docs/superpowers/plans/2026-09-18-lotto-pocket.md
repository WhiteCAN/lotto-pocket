# Lotto Pocket Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking. Execute inline; no subagent dispatch is required.

**Goal:** 구매 용지와 로컬 역대 결과를 이용해 한 손으로 추가 조합을 만들고 보관하는 Android 앱을 구현한다.

**Architecture:** Compose 화면과 ViewModel, 순수 Kotlin 추천/판정 함수, Room DB로 구성한다. 카메라 인식 결과는 검수 후에만 DB에 저장한다. 네트워크 없이 기본 기능이 동작한다.

**Tech Stack:** Kotlin, Compose Material 3, Room, CameraX, bundled on-device QR/OCR.

**Spec:** ../specs/2026-09-18-lotto-pocket-design.md

## Global Constraints

- 최소 Android 8(API 26). 버전 고정은 구현 시 공식 호환성 표로 검증한다.
- 주요 버튼은 하단, 터치 영역 48dp 이상, 본문 16sp, 보조 13sp 이상.
- 1~5게임, 기본 5게임, 빈도 복원 기본 OFF.
- 본번호 6개만 빈도 집계, 보너스는 당첨 판정에 사용.
- 앱이 처음 실행될 때 비행기 모드에서도 DB와 인식 모델을 사용할 수 있어야 한다.
- 실제 QR 및 초기 역대 결과 자료 검증 전에 배포 완료를 선언하지 않는다.
- 기존 개인 기록을 유지하는 Room migration, 서명키 저장소 제외.

## 상태

현재 산출물은 UX 시안과 설계 문서다. 아래 항목은 Android 구현 계획이며 완료된 작업이 아니다. QR 실물 포맷, 초기 DB 출처·배포 권한, 온디바이스 모델 용량은 첫 번째 작업에서 확인할 외부 의존성이다. 검증에 실패하면 지원하지 않는 기능을 성공한 것처럼 표시하지 않는다.

## 파일 구조

`app/src/main/java/io/github/lottopocket/` 아래 `MainActivity.kt`, `LottoApp.kt`, `recommend/RecommendationEngine.kt`, `recommend/RecommendScreen.kt`, `recommend/RecommendViewModel.kt`, `ticket/TicketParser.kt`, `ticket/TicketCaptureScreen.kt`, `ticket/TicketReviewScreen.kt`, `history/HistoryScreen.kt`, `draw/Draw.kt`, `draw/DrawJudge.kt`, `draw/StatisticsScreen.kt`, `data/LottoDatabase.kt`, `data/DrawImporter.kt`, `data/RecordBackup.kt`.

각 도메인의 테스트는 `app/src/test/java/io/github/lottopocket/`, DB·인식·Compose 테스트는 `app/src/androidTest/java/io/github/lottopocket/`에 같은 패키지 구조로 둔다. `app/src/main/assets/draws.db`에는 검증된 결과만 넣는다.

## Task 1: 오프라인 데이터와 인식 경로 검증

Files: `docs/data-provenance.md`, `docs/recognition-validation.md`, `app/src/main/assets/draws.db`, `app/src/main/assets/draws-manifest.json`.

- [ ] 공식 회차 결과의 사용 가능한 원천과 재배포 조건을 확인하고 출처·추출일·범위·해시를 기록한다. 과거부터 최신까지 누락 회차를 검사한다.
- [ ] 실제 용지 QR payload를 확보해 호스트 allowlist, 회차, A~E 게임 파싱 규칙을 고정한다. 임의 URL 접속을 파싱 경로에 넣지 않는다.
- [ ] bundled QR/OCR 의존성 버전, 라이선스, 용량을 공식 문서로 확인한다. 모델 다운로드 없는 최초 비행기 모드 인식을 검증한다.
- [ ] 인식 원본을 저장소에 넣기 전 개인 구매 용지가 아닌 공개 또는 테스트용 자료인지 확인한다. 검증 결과를 기록하고 관련 문서만 커밋한다.

검사할 DB 불변식:

```kotlin
require(draws.map { it.round }.distinct().size == draws.size)
draws.forEach { draw ->
    require(draw.numbers.size == 6 && draw.numbers.toSet().size == 6)
    require(draw.numbers.all { it in 1..45 })
    require(draw.bonus in 1..45 && draw.bonus !in draw.numbers)
}
```

## Task 2: 프로젝트와 추천·당첨 판정 도메인

Files: Gradle 설정, wrapper, `RecommendationEngine.kt`, `Draw.kt`, `DrawJudge.kt`, `RecommendationEngineTest.kt`, `DrawJudgeTest.kt`.

Interface: `data class Draw(val round:Int,val numbers:Set<Int>,val bonus:Int)`; `fun judge(game:Set<Int>,draw:Draw):Int?`; `fun generate(candidates:Set<Int>,locked:Set<Int>,forbidden:Set<Set<Int>>,random:Random):Set<Int>?`. 반환 null은 가능한 새 조합 없음. 후보에 없는 잠금은 별도로 검증해 오류로 반환한다.

- [ ] JVM 17과 공식 호환되는 AGP/Kotlin/Compose 버전을 고정하고 Gradle wrapper를 만든다. SDK 경로와 개인 서명 파일은 ignore한다.
- [ ] 아래 도메인 테스트를 먼저 작성하고 `./gradlew testDebugUnitTest` 실패를 확인한다.

```kotlin
@Test fun keepsLockedNumbersAndExcludesPurchased() {
    val candidates = (7..45).toSet()
    val locked = setOf(7, 21)
    val result = generate(candidates, locked, emptySet(), Random(42))!!
    assertEquals(6, result.size)
    assertTrue(result.containsAll(locked))
    assertTrue(result.all { it in candidates })
}
@Test fun distinguishesSecondAndThirdPrize() {
    val draw = Draw(1, setOf(1,2,3,4,5,6), 7)
    assertEquals(2, judge(setOf(1,2,3,4,5,7), draw))
    assertEquals(3, judge(setOf(1,2,3,4,5,8), draw))
}
@Test fun exhaustedPoolTerminates() {
    val only = setOf(1,2,3,4,5,6)
    assertNull(generate(only, emptySet(), setOf(only), Random(1)))
}
```

- [ ] 후보 조합 수를 확인하고 균등한 비복원 추출과 횟수가 제한된 재시도를 구현한다. 재시도 상한 이후에는 조합을 열거할 수 있는 작은 후보 집합만 탐색하고, 불가능하면 null을 반환한다.
- [ ] 한국어 오류 모델로 부족 후보/잠금 충돌/조합 소진을 구분한다. 빈도 동률·기간 경계·완전잠금·1~5게임 중복 테스트를 추가한다.
- [ ] `./gradlew testDebugUnitTest lintDebug` 통과 후 이 작업 파일을 명시해 커밋한다.

## Task 3: Room 저장과 데이터 관리

Files: `LottoDatabase.kt`, `DrawImporter.kt`, `RecordBackup.kt`, `LottoDatabaseTest.kt`, `DrawImporterTest.kt`.

Interface: `suspend fun importDraws(json:String):ImportResult`; `ImportResult`는 Added(count), Conflict(rounds), Invalid(reason)의 sealed 타입. `suspend fun saveRecommendation(draftId:String, snapshot:RecommendationSnapshot):String`은 같은 draftId에 같은 저장 ID를 반환한다.

- [ ] 설계의 엔티티와 외래키·회차 unique key를 Room에 정의한다. 추천은 소스 용지와 옵션 스냅샷을 함께 보존한다.
- [ ] 잘못된 파일 하나로 이전 자료 일부가 바뀌지 않는 테스트를 작성한다.

```kotlin
@Test fun rejectsInvalidImportWithoutChangingExistingDraws() = runTest {
    val before = dao.allDraws()
    val invalid = """{"schemaVersion":1,"draws":[{"round":1,"numbers":[1,1,2,3,4,5],"bonus":6}]}"""
    assertTrue(importer.importDraws(invalid) is ImportResult.Invalid)
    assertEquals(before, dao.allDraws())
}
```

- [ ] 위 테스트에서 실제 dao/importer를 in-memory Room fixture로 주입해 먼저 실패를 확인한다. 파일 크기 5MB 초과, 알 수 없는 schemaVersion, 중복·충돌 회차도 테스트한다.
- [ ] 전체 입력 검증 후 트랜잭션 병합, 새 seed 추가, 기록 내보내기/복원과 Room migration을 구현한다.
- [ ] `./gradlew testDebugUnitTest connectedDebugAndroidTest`에서 가져오기 롤백과 업데이트 후 개인 기록 보존을 확인하고 커밋한다.

## Task 4: 하단 중심 추천 화면

Files: `MainActivity.kt`, `LottoApp.kt`, `RecommendScreen.kt`, `RecommendViewModel.kt`, `RecommendScreenTest.kt`.

Interface: `RecommendUiState`는 selectedRound, selectedTicketIds, count, frequencyOptions, candidates, games, locks, message를 포함. ViewModel에서 selectCount, setFrequency, generate, rerollGame, toggleLock, save를 제공하고 화면은 DB를 직접 호출하지 않는다.

- [ ] Compose navigation 3탭과 Scaffold bottomBar/safeDrawing/ime padding을 구성한다.
- [ ] UI 테스트에서 '3게임' 선택 후 '3게임 추천받기', A 게임 번호 잠금 후 재생성을 수행해 게임 수와 잠금 유지에 실패하는 상태를 먼저 확인한다.

```kotlin
compose.onNodeWithText("3게임").performClick()
compose.onNodeWithText("3게임 추천받기").performClick()
compose.onAllNodesWithTag("recommendation-game").assertCountEquals(3)
compose.onNodeWithTag("edit-game-A").performClick()
compose.onNodeWithTag("lock-number-0").performClick()
compose.onNodeWithText("나머지 다시 뽑기").performClick()
compose.onNodeWithTag("locked-number").assertExists()
```

- [ ] 게임 카드, 3x2 번호 편집 시트, 마지막 선택 기억, 전체/개별 재생성을 도메인과 연결한다. 옵션 변경 시 기존 잠금과 초안을 초기화하는 확인 동작을 구현한다.
- [ ] 같은 draftId의 중복 저장을 막고 320dp·큰 글꼴·키보드·제스처 영역에서 버튼 가림을 확인한다. 테스트 및 lint 후 커밋한다.

## Task 5: QR·사진·수동 등록

Files: `TicketParser.kt`, `TicketCaptureScreen.kt`, `TicketReviewScreen.kt`, `TicketParserTest.kt`, `TicketCaptureTest.kt`.

Interface: `fun parseQr(raw:String):TicketParseResult`; 인식은 `TicketDraft(round:Int?,games:List<List<Int>>,uncertainCells:Set<Pair<Int,Int>>)`를 만들고 검수 화면에서 validated Ticket로 변환한다.

- [ ] Task 1의 실제 형식 fixture로 정상/잘린 payload/임의 URL/잘못된 회차/중복 숫자 테스트를 먼저 만든다. 고정 임의 URL을 실제 형식이라고 가정하지 않는다.
- [ ] CameraX QR/사진과 Photo Picker를 연결한다. 사진 입력 스트림과 이미지는 화면을 떠날 때 해제한다.
- [ ] OCR 신뢰도·행별 개수 검사, 회차 수정, 수동 번호판, duplicate ticket 확인을 구현한다. 결과 검수 전 DB 저장은 금지한다.
- [ ] 첫 실행 비행기 모드, 카메라 권한 거부, 인식 실패, 사진 회전, 5줄 용지, 일부 잘린 용지를 실기기로 확인한다. 테스트·문서 업데이트 후 커밋한다.

## Task 6: 기록·통계·당첨 확인

Files: `HistoryScreen.kt`, `StatisticsScreen.kt`, `HistoryScreenTest.kt`, `StatisticsTest.kt`.

Interface: 회차별 구매/추천 Flow, 내부 Draw Flow를 결합해 판정을 계산한다. `fun frequencies(draws:List<Draw>,beforeRound:Int,last:Int?):Map<Int,Int>`은 본번호만 세고 round < beforeRound 조건을 적용한다.

- [ ] 미래 회차 누수 방지와 데이터 없는 회차 표시 테스트를 작성한다.

```kotlin
@Test fun excludesCurrentAndFutureRounds() {
    val draws = listOf(Draw(1,setOf(1,2,3,4,5,6),7), Draw(2,setOf(8,9,10,11,12,13),14))
    val counts = frequencies(draws, beforeRound=2, last=null)
    assertEquals(1, counts[1])
    assertEquals(0, counts[8] ?: 0)
}
```

- [ ] 내 로또에 구매 표시·추천 기록·결과 미등록 상태·수동 당첨결과 입력을 연결한다. DB 갱신 후 자동 재판정한다.
- [ ] 통계 기간 필터, 45개 빈도, 데이터 범위·출처, JSON 가져오기/백업 진입을 연결한다. 추천 미구매 게임은 실제 당첨 요약에서 제외한다.
- [ ] 테스트 및 TalkBack 확인 후 커밋한다.

## Task 7: APK 검증과 GitHub 공개 준비

Files: `.github/workflows/android.yml`, `.gitignore`, `README.md`, `docs/privacy.md`, release notes.

- [ ] GitHub Actions에 `./gradlew testDebugUnitTest lintDebug assembleDebug`를 구성한다. CI에 서명키 원문이나 개인 구매 자료를 넣지 않는다.
- [ ] `./gradlew testDebugUnitTest lintDebug assembleDebug assembleRelease`와 실기기 계측 테스트를 실행한다.
- [ ] 320/360/412dp, 글꼴 1.0/1.3/2.0, light/dark, 접근성, 오프라인 신규 설치/업데이트 보존 체크리스트를 수행한다.
- [ ] 서명 APK, SHA-256, 권한 안내, 데이터 기준 회차, 설치·업데이트 방법을 작성한다. 현재 작업은 설계이므로 원격 저장소 생성·공개 배포는 실제 APK 검증 후 진행한다.

## 계획 자체 검토

입력·제외·복원·잠금·통계·판정·로컬 저장·업데이트 보존·하단 조작·공개 APK 준비는 각각 위 작업에 대응한다. 첫 작업의 실물 QR와 합법적으로 재배포 가능한 seed 자료 확보는 추측으로 건너뛸 수 없는 검증 항목이다. 라이브러리의 실제 버전은 최신 공식 호환성을 확인한 뒤 구현 커밋에 고정한다.
