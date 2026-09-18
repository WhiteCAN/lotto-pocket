# 용지 인식 검증 기록

검증일: 2026-09-18

## 오프라인 인식 라이브러리

- QR: `com.google.mlkit:barcode-scanning:17.3.0`
- 문자: `com.google.mlkit:text-recognition:16.0.1`

두 좌표는 Google의 bundled 모델이다. 모델이 APK에 정적으로 포함되어 첫 실행 전 별도 다운로드가 필요하지 않는다. 공식 문서 기준 최소 API는 23이며, 이 앱의 최소 API 26에서 사용할 수 있다. 안내된 앱 크기 증가는 QR 약 2.4MB, Latin 문자 모델은 아키텍처별 약 4MB다.

근거:

- <https://developers.google.com/ml-kit/vision/barcode-scanning/android>
- <https://developers.google.com/ml-kit/vision/text-recognition/v2/android>
- <https://developers.google.com/ml-kit/release-notes>

## QR 허용 형식

공개된 동행복권 QR 표본을 바탕으로 다음 조건을 모두 만족할 때만 파싱한다.

- `https` 스킴
- 호스트 `m.dhlottery.co.kr` 완전 일치
- 경로 `/qr.do` 완전 일치
- 쿼리 `method=winQr&v=...`만 존재
- `v`는 4자리 양수 회차 뒤에 `m` 또는 `q`와 2자리 번호 6개가 1~5회 반복
- 각 게임은 1~45의 서로 다른 번호 6개
- 뒤쪽 값은 표본에서 확인한 없음, 숫자 10자리, 숫자 10자리와 `.net`만 허용

구현 fixture:

```text
https://m.dhlottery.co.kr/qr.do?method=winQr&v=1179m052527293436m192427303134m021012152244m041623253540m1618212440440000000645.net
```

다른 호스트, 하위 호스트처럼 위장한 문자열, 다른 경로, 추가 쿼리, 알 수 없는 꼬리값은 거부한다. QR 문자열은 구조화된 회차와 번호로만 변환하며 URL을 열거나 네트워크로 요청하지 않는다.

## 사진 OCR 정책

사진 OCR 결과에서는 한 줄에 1~45의 서로 다른 숫자가 정확히 6개 있는 행만 후보로 사용하며 최대 5행까지만 전달한다. 회차는 OCR로 확정하지 않고 `null`로 두며, 모든 결과는 상위 검수 화면에서 사용자가 확인한 뒤에만 저장해야 한다. 완전한 행이 없으면 등록 초안을 만들지 않는다.

촬영은 앱의 카메라 권한 없이 시스템 카메라의 전체 해상도 `TakePicture` 인텐트를 사용한다. 임시 파일은 FileProvider가 허용한 `cacheDir/capture`에 만들고, 화면 회전 중에는 경로를 저장해 결과를 이어받는다. 인식 완료·촬영 취소·카메라 실행 실패 시 앱이 만든 파일을 삭제한다. 구성 변경 시 외부 카메라가 아직 파일을 쓰고 있을 수 있으므로 화면 폐기만으로 pending 파일을 삭제하지 않는다. 사진 선택은 AndroidX `PickVisualMedia`를 사용하며, 지원하지 않는 기기에서는 contract가 시스템 문서 선택기로 대체한다.

## 남은 실기기 검증

코드와 JVM 파서 fixture 검증만으로 실제 인식 정확도를 보장할 수 없다. 배포 전 공개 테스트 용지를 사용해 새 설치 후 비행기 모드 최초 QR/OCR, 회전 사진, 흐림과 일부 잘림, 1~5게임, 카메라 취소, 시스템 사진 선택기 대체 동작을 실기기에서 확인하고 결과를 이 문서에 추가해야 한다.
