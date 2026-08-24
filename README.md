# 이곳 교구 (Korea Diocese Locator) v0.4

대한민국에서 현재 위치 또는 사전 검색으로 관할 천주교 교구와 교구장을 확인하는 Android 앱입니다.

## v0.4 핵심 변경

- 한국천주교주교회의 **온라인 주소록**을 매일 자동 확인하는 Python 수집기 추가
- GitHub Actions가 매일 12:17 KST에 수집기를 실행하도록 구성
- 15개 지역교구(군종교구 제외)가 **모두 정상 파싱된 경우에만** 원격 JSON 갱신
- 교구장/직무대행 변경은 `data/remote_data.json`에 자동 반영
- `교구장 직무대행`은 앱에서 `(교구장을 기억하지 않음)`으로 유지
- 주교회의의 `관할지역(한글)` 문구가 실질적으로 변경되면 `boundaryVersion` 자동 +1
- 기존 앱이 더 낮은 `boundaryVersion`을 받으면 **앱 업데이트 필요 팝업** 표시
- 네트워크/HTML 구조 변경/파싱 오류 시 기존 정상 데이터 보존
- 별도 서버 하드웨어 없음: GitHub 저장소와 GitHub Actions만 사용

## 전체 구조

1. 앱에는 교구 판정 규칙과 기본 교구장 데이터가 내장됩니다.
2. GitHub Actions가 하루 1회 `automation/update_cbck.py`를 실행합니다.
3. 스크립트가 주교회의 온라인 주소록의 교구 목록을 직접 발견하고 15개 지역교구 페이지를 읽습니다.
4. 검증 성공 시에만 `data/remote_data.json`을 갱신하고 자동 커밋합니다.
5. 앱은 공개된 `data/remote_data.json`을 받아 최신 교구장 정보를 적용합니다.
6. 인터넷이 없으면 마지막 정상 수신 데이터를 계속 사용합니다.

## 안전장치

주교회의의 내용을 신뢰하되 **수집 프로그램의 오류는 신뢰하지 않는** 방식입니다.

- 정확히 15개 지역교구가 발견되어야 함
- 모든 교구에서 현재 교구장 또는 교구장 직무대행을 읽어야 함
- 모든 교구에서 `관할지역(한글)`을 읽어야 함
- 성직자 직함(주교/대주교/추기경/신부 등)을 정상 판별해야 함
- 위 조건 중 하나라도 실패하면 프로세스가 오류 종료되므로 GitHub Actions는 커밋하지 않음
- 따라서 빈 이름이나 잘못 파싱된 결과가 기존 정상 JSON을 덮어쓰지 않음

## 교구 관할 변경 감지

`data/boundaries_snapshot.json`에 주교회의의 관할지역 설명을 저장합니다.

다음 실행에서 설명이 바뀌면:

1. 변경 교구를 기록
2. `boundaryVersion`을 1 증가
3. 앱은 원격 버전이 내장 버전보다 높음을 확인
4. `교구 관할 정보 업데이트 필요` 팝업 표시

단, `(5,772㎢)` 같은 **면적 수치만 변경된 경우에는 경계 변경으로 보지 않습니다.**

교구장 변경과 달리 실제 GPS polygon 경계를 자동 수정하지 않는 이유는, 자연어 관할 설명 변경을 GIS 경계로 자동 변환하면 오판 가능성이 있기 때문입니다.

## GitHub 연결 — 한 번만 하면 됨

공개 GitHub 저장소를 만든 뒤 이 프로젝트를 올립니다. 앱이 읽을 주소는 다음 형태입니다.

`https://raw.githubusercontent.com/OWNER/REPO/main/data/remote_data.json`

`gradle.properties.example`의 예시를 참고해 빌드 시 `KDL_REMOTE_DATA_URL` 값을 설정합니다.

```properties
KDL_REMOTE_DATA_URL=https://raw.githubusercontent.com/OWNER/REPO/main/data/remote_data.json
```

이 값은 APK 빌드 시 `BuildConfig.REMOTE_DATA_URL`로 들어갑니다.

## GitHub Actions

워크플로 파일:

`.github/workflows/update-cbck.yml`

자동 실행 외에도 GitHub의 **Actions → Update CBCK diocese data → Run workflow**에서 수동으로 즉시 실행할 수 있습니다.

워크플로에는 `contents: write` 권한이 있어 검증된 변경만 자동 커밋합니다. 저장소 설정에서 Actions의 쓰기 권한이 제한되어 있다면 **Settings → Actions → General → Workflow permissions → Read and write permissions**를 허용해야 합니다.

## 주요 파일

- `automation/update_cbck.py` — 주교회의 자동 확인 및 검증
- `automation/test_parser.py` — 정규 교구장/직무대행 파싱 테스트
- `.github/workflows/update-cbck.yml` — 매일 자동 실행
- `data/remote_data.json` — 앱이 읽는 공개 데이터
- `data/boundaries_snapshot.json` — 관할 설명 비교 기준
- `app/.../RemoteDataManager.kt` — 원격 데이터 적용/캐시/경계버전 확인
- `app/.../DioceseRepository.kt` — 앱 내장 교구 판정 규칙

## 테스트

```bash
python automation/test_parser.py
python -m py_compile automation/update_cbck.py
```

실제 주교회의 사이트 확인은 인터넷이 가능한 환경에서:

```bash
python automation/update_cbck.py
```

## 현재 앱 기능

- Android 위치 권한 요청
- 현재 GPS 위치 취득
- Android Geocoder 주소 변환
- 교구 관할 로컬 판정
- 교구장 자동 원격 갱신 및 오프라인 캐시
- `현재 위치` / `미리 검색` 탭
- 직무대행 시 `(교구장을 기억하지 않음)`
- 교구 경계 버전 상승 시 앱 업데이트 안내

## 다음 개발 단계

현재 위치 판정은 행정구역 규칙 기반입니다. 다음 버전에서는 공식 행정구역 경계 GeoJSON을 내장하여 시흥·안산, 동해·평창, 김해·밀양 같은 분할 관할 지역도 GPS 좌표로 직접 판정하도록 개선할 예정입니다.


## v0.5 추가 사항
- GPS 역지오코딩 결과의 ISO 국가 코드(`countryCode`)가 `KR`이 아니면 교구 판정을 수행하지 않습니다.
- 대한민국 밖에서는 `서비스 지역이 아닙니다. 현재 이 앱은 대한민국 내 천주교 교구만 지원합니다.` 안내를 표시합니다.
- 국가 코드 확인이 끝난 뒤에만 국내 교구 판정 규칙을 실행합니다.
