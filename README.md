# 로또 포켓

구매한 로또 번호를 제외하고, 고정번호와 과거 출현 빈도를 선택해 조합을 만드는 오프라인 Android 앱입니다. 주요 조작은 하단 버튼과 하단 시트로 제공합니다.

## 구현 기능

- QR 사진 촬영, 용지 사진 OCR, 사진 선택, 수동 입력 및 저장 전 번호 확인
- 구매번호 제외, 전체/구매번호 안에서 상위 개수를 선택하는 빈도 복원 옵션(기본 꺼짐)
- 전체·최근 50·100회 통계, 공통 고정번호 최대 5개, 생일·기념일 월/일 추가
- 1~5게임 생성, 번호 잠금, 게임별·전체 다시 뽑기, 동일 조합 중복 방지
- 구매/추천 기록 구분, 추천 구매 표시, DB에 결과가 있는 회차의 등수 판정
- 1~1241회 실제 당첨 결과 내장, Room 내부 DB, 결과 수동 입력 및 JSON 가져오기
- 개인 기록 JSON 백업·복원, 밝은/어두운 테마

앱 실행이나 인식에 네트워크가 필요하지 않습니다. 모델과 당첨 결과를 APK에 포함하며 INTERNET 권한이 없습니다. 추천 조건은 조합을 선택하는 방법이며 당첨 확률 향상을 의미하지 않습니다.

## 빌드와 설치

Android 8.0(API 26) 이상. 빌드 환경은 JDK 21, Android SDK 36입니다. local.properties에 자신의 SDK 경로를 설정하거나 ANDROID_HOME을 지정하세요.

```powershell
.\gradlew.bat :app:testDebugUnitTest :app:lintDebug :app:assembleDebug :app:assembleRelease
```

직접 설치용 개발 APK: `app/build/outputs/apk/debug/app-debug.apk`.
ADB 연결 시 `adb install -r app/build/outputs/apk/debug/app-debug.apk`로 설치합니다. 파일을 휴대폰으로 옮겨 설치할 때는 해당 파일 앱의 외부 앱 설치 허용이 필요합니다.

`app-release-unsigned.apk`는 서명 전 산출물이므로 배포용 설치 파일이 아닙니다. 공개 릴리스는 실기기 검증과 지속 사용할 릴리스 서명키 설정 후 진행합니다. 서명키와 local.properties는 Git에 포함하지 않습니다. 개발 APK에서 다른 서명으로 변경하면 그대로 업데이트할 수 없으므로 앱의 데이터 관리에서 먼저 백업하세요.

## 검증 범위와 남은 작업

단위/Room/Compose 테스트는 추천 제약, 등수, 파싱, 가져오기 충돌 롤백, 반복 백업 복원, 설정 회전 복원, 추천 저장 및 내장 DB 표시를 검증합니다. Compose 화면은 Robolectric의 360dp 환경에서 확인했습니다.

2026-10-05 Galaxy S25 Ultra(Android 16)에서 설치·추천·고정·잠금·저장·재실행·업데이트 보존과 생성 이미지 OCR을 확인했습니다. [실기기 테스트 기록](docs/device-test-2026-10-05.md)에 근거와 수정 내용을 정리했습니다. 실제 종이 QR·사진 정확도, 비행기 모드 신규 설치, 기기별 큰 글꼴·TalkBack은 미검증입니다. GitHub Actions 원격 실행과 Releases 공개는 아직 하지 않았습니다.

첫 버전은 구매 조합을 회차+번호로 중복 제거합니다. 번호가 같은 용지를 여러 장 산 수량을 관리하는 가계부 기능은 제공하지 않습니다. OCR은 전체 행을 사용자가 검수하며 개별 글자 신뢰도 표시를 제공하지 않습니다. 백업 파일은 복원 가능한 UTF-8 5MB 이하로 제한하고 초과 내보내기는 오류로 안내합니다.

## 데이터와 문서

첫 실행 시 내장 JSON을 검증해 Room에 넣습니다. 이후 기존 개인 기록은 유지하면서 회차를 병합하고 충돌 데이터는 덮어쓰지 않습니다. 향후 회차가 자동으로 인터넷 갱신되지는 않으며 결과 직접 입력, JSON 가져오기 또는 새 데이터가 포함된 앱 업데이트를 사용합니다.

- [데이터 출처·갱신 방법](docs/data-provenance.md)
- [인식 형식과 실기기 검증 항목](docs/recognition-validation.md)
- [개인 정보와 저장 방식](docs/privacy.md)
- [제품·UX 설계](docs/superpowers/specs/2026-09-18-lotto-pocket-design.md)
- [구현 결과와 계획 차이](docs/implementation-progress.md)
- [구현 계획](docs/superpowers/plans/2026-09-18-lotto-pocket.md)

초기 UX 시안은 design/lotto-pocket.html(Codex 시각화용 HTML 조각)에 보존했습니다. 시안 검사: `node scripts/check-prototype.cjs`.
