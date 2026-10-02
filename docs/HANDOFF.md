# 앱 데이터 계층 작업 정리 (PR #1~#5, 2026-10-02)

화면과 서버를 뺀 **앱 쪽 로직 전부**를 만들어 `main`에 합쳤습니다. 화면 담당은 아래 ViewModel을 가져다 붙이면 되고, 서버 담당은 [서버 담당이 알아야 할 것](#서버-담당이-알아야-할-것)만 보면 됩니다.

| PR | 내용 |
|:---|:---|
| [#1](https://github.com/CodingSilcoon/jeongjungang/pull/1) | 추천 데이터 계층, 공유 문구, 지도 길찾기 링크 |
| [#2](https://github.com/CodingSilcoon/jeongjungang/pull/2) | 주소 검색, 지도 핀 주소 조회 |
| [#3](https://github.com/CodingSilcoon/jeongjungang/pull/3) | 책임 알람 기기 쪽 (예약, 울림, 끄기, 재부팅 재예약) |
| [#4](https://github.com/CodingSilcoon/jeongjungang/pull/4) | 2단계 약속·초대 앱 쪽, 방장 나갈 때 규칙 |
| [#5](https://github.com/CodingSilcoon/jeongjungang/pull/5) | 결정 3건, 3단계 책임 알람 앱 쪽 |

각 PR 본문에 **화면 담당용 코드 예시**가 있습니다. 이 문서는 전체 요약이고, 자세한 사용법은 PR을 보세요.

- 단위 테스트 106개 → **200개**, 전부 통과
- 화면이 없어 에뮬레이터에서 임시 테스트로 확인한 것: 추천 계산, 알람 울림·끄기·재부팅, "아직 안 일어남" 알람, 토큰 암호화, 서버 알람 동기화(가짜 서버)

---

## 1. 처음과 바뀐 것 (팀 확인 필요)

| 항목 | 처음 | 바뀐 뒤 | 반영 위치 |
|:---|:---|:---|:---|
| **후보 순위** | 분 단위로 같을 때만 환승 적은 쪽이 앞 | **2분 이내 차이는 같은 묶음**, 그 안은 환승 적은 순 | `Recommender.TIE_BAND_MINUTES`, CLAUDE.md |
| └ 예: 홍대·노원·천호 | 답십리 → 신설동(환승 3) → 보문 | 답십리 → **마장(환승 1)** → 보문 | |
| **지원 지역** | 기준 없음 (경기도도 계산되지만 결과가 틀림) | 가장 가까운 1~8호선 역이 **3km 넘으면 막음** ("지금은 서울 지하철 1~8호선 근처만 지원해요") | `ServiceArea`, CLAUDE.md |
| **알람 여유시간** | 0분 | **5분** (약속마다 변경 가능) | `AlarmTimeCalculator`, API.md `marginMinutes` |
| **방장이 나갈 때** | 미정 | **가장 먼저 들어온 사람에게 방장 넘김, 혼자면 약속 취소** | API.md 6절 |
| **참가자 목록 순서** | 정해지지 않음 | 서버가 **들어온 순서**로 준다 (위 규칙의 기준) | API.md 6절 |
| **토큰 저장** | EncryptedSharedPreferences | **Android Keystore 직접 암호화** (위 라이브러리가 2025년 지원 중단) | API.md 2절 |
| **HTTP** | HttpURLConnection | **OkHttp 5.5.0** (기존 방식은 명세의 PATCH를 못 보냄) | `build.gradle` |

지원 지역 3km 근거: 서울 안에서 1~8호선 역이 가장 먼 곳도 2.6km(우이동→쌍문)이고, 경기도는 판교(4.6km)부터 멀어집니다.

이번에 CLAUDE.md·API.md "미정"에서 지운 항목: 서비스 지역 밖 처리, 알람 여유시간, 방장이 나갈 때.

---

## 2. 추가한 기능

### 1단계: 추천을 화면에 연결
- **추천 실행** `RecommendViewModel`: 출발지 2~10곳 + 기준 → 후보 3곳. 백그라운드 계산, 마지막 요청 결과만 반영
- **공유 문구** `ShareText`: 후보 3곳 또는 한 곳, "근사값" 안내 자동 포함
- **지도 길찾기** `ExternalApps`: 사람별 출발지 → 후보역, 카카오맵(앱 없으면 웹)·네이버지도
- **주소 검색** `AddressSearchViewModel`: 타이핑 자동 검색(0.3초) + 버튼 검색 둘 다, 지도 핀 주소 조회
  - 서버 없으면 번들된 역 이름으로 검색("홍대" → 홍대입구역)
- **지원 지역 표시**: 검색 결과·핀·추천 결과에서 지역 밖 출발지 표시

### 2단계: 약속과 초대 (서버 필요)
- **약속** `MeetingViewModel`: 만들기, 초대 미리보기, 참가, 4초마다 새로고침(화면 보일 때만), 수정, 장소 확정, 나가기, 내보내기, 취소
- 초대는 링크를 통째로 붙여 넣어도, 코드만 넣어도 됨 (`7k3q-h9mx`처럼 소문자·하이픈도 인식)
- **주변 장소** `PlacesViewModel`: 약속 목적별(식사→식당, 카페→카페, 술자리→술집), 페이지 넘기기
- 약속 참가자 출발지를 바로 추천에 넘김: `snapshot.recommendParticipants()`

### 3단계: 책임 알람
- **기기 알람** `AlarmScheduler`: 정확한 시각 예약, 잠금 화면 위 알람 화면, 소리·진동, 10분 뒤 자동 중지, 재부팅 후 다시 예약
- **서버 연동** `AccountabilityViewModel`: 약속 화면에서 `onSnapshot()`만 불러 주면
  - 확정 장소까지 내 이동시간 자동 보고
  - 서버 알람을 기기에 예약 (서버 시각 기준으로 폰 시계 차이 보정)
  - 준비시간·동의 저장, 방장 켜기/끄기, "이미 일어났어요" 버튼
- **끈 기록 재전송**: 네트워크가 없어도 쌓아 두고, 돌아오면 자동 전송 (앱 종료·재부팅 뒤에도)
- **"○○님이 아직 안 일어났어요" 알람**: 친구 폰에서 2분 울림, 2분 넘게 늦게 도착한 알림은 무시
- 약속에서 나가거나 약속이 취소되면 그 약속 알람도 자동 삭제

---

## 화면(디자인) 담당이 알아야 할 것

**만들어야 할 화면과 쓸 ViewModel**

| 화면 | ViewModel | 사용법 |
|:---|:---|:---|
| 출발지 입력 (검색·지도 핀) | `AddressSearchViewModel` | [#2](https://github.com/CodingSilcoon/jeongjungang/pull/2) |
| 후보 3곳, 상세 비교, 공유, 길찾기 | `RecommendViewModel`, `ShareText`, `ExternalApps` | [#1](https://github.com/CodingSilcoon/jeongjungang/pull/1) |
| 약속 만들기, 초대, 약속 화면 | `MeetingViewModel` | [#4](https://github.com/CodingSilcoon/jeongjungang/pull/4) |
| 주변 식당·카페·술집 | `PlacesViewModel` | [#4](https://github.com/CodingSilcoon/jeongjungang/pull/4) |
| 책임 알람 설정·권한 안내 | `AccountabilityViewModel`, `AlarmPermissions` | [#3](https://github.com/CodingSilcoon/jeongjungang/pull/3), [#5](https://github.com/CodingSilcoon/jeongjungang/pull/5) |
| 알람 화면 | 이미 있음 (`activity_alarm.xml`) | 디자인 바꿔도 됨, **id 4개 유지** |

**꼭 지켜야 할 것**
- **"소요시간은 근사값"** 문구를 화면에 표시 (CLAUDE.md 규칙)
- **지원 지역 밖**: 검색 결과는 `searchState.isOutOfArea(place)`, 핀은 `pinState.outOfArea`, 추천은 `state.outOfArea`. 흐리게 표시하고 `ServiceArea.MESSAGE` 안내
- 서버 연결 전에는 `isAvailable()`이 `false` → 약속·책임 알람 버튼을 숨기거나 "서버 연결 후 사용 가능" 안내
- **알람 권한 안내 화면이 필요합니다.** "정확한 알람" 권한은 Android 14부터 기본으로 꺼져 있고, 안 켜면 알람이 아예 예약되지 않습니다. `AlarmPermissions`에 확인 함수와 설정 화면 이동이 다 있습니다(#3 표 참고)
- 오류·안내 문장(`errorMessage`, `message`)은 사용자용이라 그대로 보여 주면 됩니다

**알람 화면 id**: `alarmTitle`, `alarmSubtitle`, `alarmMessage`, `dismissButton` (친구 알람 화면도 같은 레이아웃 사용)

---

## 서버 담당이 알아야 할 것

명세는 [docs/API.md](API.md)가 기준이고, 앱은 그대로 구현했습니다. `server/setup` 브랜치와 충돌 없는 것 확인했습니다.

**서버가 구현해야 하는 결정 사항**
1. `DELETE /participants/{방장 id}` (방장 본인): 남은 사람 중 **가장 먼저 들어온 사람을 방장으로**, 혼자면 **약속 취소**. 응답 둘 다 `204`
2. `GET /meetings/{id}`의 `participants`는 **들어온 순서**
3. `marginMinutes` 기본값 **5**
4. 초대 링크로 앱이 열리려면 `https://{도메인}/.well-known/assetlinks.json` 필요 (예시와 지문 넣는 법은 API.md 6절)

**앱이 기대하는 동작 (명세대로지만 다르면 앱이 깨지는 부분)**
- 모든 응답은 `{success, data, error}` 봉투. `error.message`는 앱이 그대로 사용자에게 보여 줌
- `GET /meetings/{id}`: `ETag` 헤더 + `If-None-Match`면 `304`
- `429`면 `Retry-After`를 **초 단위 숫자**로 (앱이 그만큼 쉼)
- `PUT /participants/{id}/travel`의 `placeName`은 확정 장소 이름 그대로 비교 (앱은 "답십리역"처럼 "역"을 붙여 보냄)
- `POST /alarms/{id}/dismiss`는 항상 `200` + `{status}`. 앱은 네트워크·5xx·429만 재시도하고 4xx는 버림
- 응답에 `serverTime`을 넣어 주면 앱이 폰 시계 차이를 보정함 (없으면 보정 안 함)

**서버가 생기면 앱 연결 방법**: `android/local.properties`에 한 줄 (git에 안 올라감)
```properties
jeongjungang.apiBaseUrl=https://{도메인}/api/v1
```

---

## 추천 로직(Recommender) 담당이 알아야 할 것

`Recommender.java`에 두 가지를 더했습니다.
- `rank()`: 2분 이내 동률 묶음 (기존 `comparatorFor`는 묶기 전 기본 정렬로 그대로 사용)
- `tripTo(참가자, 역)`: 한 사람이 특정 역까지 가는 시간·환승. 책임 알람 이동시간 보고에 사용

테스트 `RecommenderTest`, `SeoulRecommendationTest`의 순서 관련 기대값을 새 규칙에 맞게 바꿨습니다.

---

## 기술 변경 요약

| 항목 | 내용 |
|:---|:---|
| 라이브러리 추가 | OkHttp 5.5.0, WorkManager 2.12.0, (테스트 전용) org.json |
| 권한 추가 | 인터넷, 정확한 알람, 전체 화면 알림, 알림, 부팅 완료, 진동, 포그라운드 서비스 |
| 백업 제외 | 참가자 토큰, 알람 예약·끈 기록·연결 정보 (기기 전용 데이터) |
| 서버 주소 | `BuildConfig.API_BASE_URL` ← `local.properties`의 `jeongjungang.apiBaseUrl` |
| 새 패키지 | `data/remote`, `data/remote/meeting`, `data/repository`, `alarm`, `ui/recommend`, `ui/search`, `ui/meeting`, `ui/alarm`, `util`, `domain/alarm` |

---

## 아직 남은 것

| 항목 | 누가 / 무엇이 필요 |
|:---|:---|
| 화면 전부 | 화면 담당 |
| 서버 (위 결정 사항 포함) | 서버 담당 |
| 카카오 개발자 앱·키 발급 | 지도 SDK와 서버 `/geocode`가 필요. 담당 미정 |
| 푸시(FCM) 수신 | Firebase 프로젝트와 `google-services.json` 필요. 생기면 `syncAlarm()`, `AlarmRingService.startEscalation()` 호출만 붙이면 됨 |
| 초대 링크로 앱 바로 열기 | 도메인 확정 후 앱 설정 추가 |
| 실기기 테스트 | 특히 삼성 (알람이 배터리 최적화에 밀리는지) |
| 남은 미정 | 서버 호스트, RDB, 유예시간 기본값, 약속 변경 시 재동기화, 전화 걸기(전화번호 수집 여부) |

## 개발할 때 참고

- 에뮬레이터를 오래 켜 두면 앱 화면이 시작 애니메이션에서 멈추고 검은 화면/빈 화면이 될 때가 있습니다(다른 앱도 동일). **Cold Boot**하면 정상입니다. 앱 문제가 아닙니다
- 테스트: `cd android && gradlew.bat test` (JAVA_HOME을 Android Studio 내장 JDK로)
