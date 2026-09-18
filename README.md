# 로또 포켓 (가칭)

자동 구매한 로또 번호를 바탕으로 제외번호와 과거 출현 빈도 옵션을 적용해 추가 조합을 만드는, 오프라인 중심 Android 앱의 설계 작업입니다.

현재 상태: UX 설계 및 인터랙티브 시안. Android 앱/APK, 실물 QR·OCR 인식, 역대 회차 DB는 아직 구현되지 않았습니다.

- [제품·UX 설계](docs/superpowers/specs/2026-09-18-lotto-pocket-design.md)
- [구현 순서](docs/superpowers/plans/2026-09-18-lotto-pocket.md)
- [인터랙티브 시안 원본](design/lotto-pocket.html): Codex 시각화용 HTML 조각이며 일반 HTML 문서가 아닙니다.

시안 로직 검사: 프로젝트 폴더에서 `node scripts/check-prototype.cjs`.
브라우저에서 예시 용지 등록, 3게임 생성, 번호 1 잠금 후 재생성, 내 로또 저장을 확인했습니다. 320px 폭 화면도 확인했습니다. 실제 Android 고정 하단/키보드/기기 테스트는 구현 이후 수행합니다.

배포 목표: GitHub 소스 공개 및 Releases APK 직접 설치. 가칭과 저장소명은 변경할 수 있습니다.
