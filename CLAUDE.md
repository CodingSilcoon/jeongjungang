# 정중앙

"우리 모두 만나기 좋은 곳"을 찾아주는 안드로이드 앱. 목표는 지리적 정중앙이 아니라 모두가 납득할 만한 만남 장소.
흐름: 출발지 입력 → 후보 3곳 → 각자 걸리는 시간·환승 비교 → 친구에게 공유.

## 스택
- Android: **Java only (Kotlin 금지, 수업 요구)**, XML + ViewBinding, MVVM + ViewModel + LiveData, ExecutorService/Handler
- 지도/주소: 카카오맵 SDK, 카카오 로컬(주소→좌표). MVP부터 사용
- 백엔드: 1단계 없음. 2단계(초대 링크)부터 Spring Boot + Redis, Docker. 3단계(책임 알람)부터 RDB(MySQL 또는 Postgres, 미정) + FCM 추가
- 검증(선택): ODsay. Basic 무료 **30회/일**만 확인된 수치. 개발·테스트 중엔 목 데이터 사용
- 쿼터·요금 수치는 확정되기 전까지 가정하지 말 것

## 데이터 (공공누리1, 서울 열린데이터광장, 앱 assets에 번들)
- 역간 소요시간 CSV: 호선, 역명, 소요시간(mm:ss), 역간거리(km), 호선별누계(km) (행 순서 = 노선 진행 순서)
- 역사 좌표 CSV: 호선, 역명, 위도, 경도 (1~8호선 276개 역)
- 환승역 소요시간 CSV: 환승역 이름, 노선, 환승거리(m), 환승소요시간(mm:ss)
- 위 3개는 `android/app/src/main/assets/data/`에 UTF-8로 받아 둠. 파일 간 역명 불일치·지선 처리 등 파싱 주의점은 `docs/DATA.md` 참고
- 범위는 서울교통공사 1~8호선만. 9호선·신분당·분당·경의중앙·공항철도는 미지원 → MVP는 "서울 중심"

## 추천 로직
1. 입력: 참가자 출발지 n개 (주소 검색 또는 지도 핀 → 좌표)
2. Weiszfeld 기하 중앙값. n=1은 그 점, **n=2는 중점으로 분기**(유일해 없음). 좌표 중복 시 NaN 방지. 중앙값 주변 역을 후보 풀로 사용
3. 출발지 → 가까운 역 K개 접근시간 근사 후, 자체 그래프에서 다중 출발 Dijkstra
   - 노드 = (호선, 역), 간선 = 역간 소요시간, 같은 역명의 다른 호선 노드는 환승 간선
   - 접근시간 가정값(튜닝 대상): 직선→실거리 보정 1.3, 실거리 1.2km 이하는 도보 4.5km/h, **넘으면 전 구간 버스**(대기 5분 + 15km/h). 경계를 넘는 순간 버스가 더 빨라 시간이 줄어드는 구간이 있다. 거슬리면 "도보/버스 중 빠른 쪽"으로 바꾼다 (`AccessTimeEstimator`)
   - 역 정차시간: CSV 소요시간은 정차시간 제외라 **구간마다 0.5분을 더한다** (도착역 정차 포함, 환승 간선 제외). `TransitGraph.DEFAULT_DWELL_MINUTES_PER_STOP`
   - 환승 시간은 환승 CSV 값, 없으면 4분
   - 최소시간 경로의 환승 횟수도 함께 추적
   - 후보 풀은 중앙값 주변 역 15개(`Recommender.DEFAULT_POOL_SIZE`), 사람마다 출발점으로 쓰는 가까운 역은 3개. 시연하며 조정한다
   - 전원이 갈 수 있는 후보만 남긴다. 그런 후보가 없으면 빈 목록을 돌려주고 UI가 fallback을 보여 준다
4. 추천 기준을 사용자가 선택: "전체 이동시간 최소" / "최대 이동시간 최소(한 명만 너무 멀지 않게)"
   - 정렬 순서: 기준 지표(화면에 보이는 분 단위) → 환승이 적은 쪽 → 보조 지표 → 역 이름. 분 단위로 같은 후보끼리는 환승이 적은 쪽이 앞선다 (0.1분 차이로 환승 3회짜리가 1위가 되는 것을 막기 위함)
5. 상위 3곳 + 선정 이유 문구 (예: "전원 40분 이내, 환승 최대 1회")
   - 한 사람이 두 번째로 오래 걸리는 사람보다 10분 이상 더 걸리면(3명 이상일 때) "○○님만 42분으로 길어요"
   - 아니면 "전원 N분 이내"(N은 10분 단위 올림, 90분 초과면 가장 오래 걸리는 사람 표시) + "환승 없음" 또는 "환승 최대 N회"
6. 결과 화면: 참가자별 시간·환승 비교, 카카오맵/네이버지도 딥링크(버스 포함 실제 경로), 공유(Android 공유 인텐트)
7. (선택) ODsay로 1위 후보 검증(버스 포함). 호출은 n회만
- 역×역 소요시간표는 앱 시작 시 또는 빌드 시점에 미리 계산 가능 (역 수백 개, 수 ms~1초)

## 규칙
- 소요시간은 근사값임을 UI에 명시
- API 키는 클라이언트에 두지 않음 (ODsay 등 유료·키 보호 대상은 백엔드 경유)
- 조회 실패, 가까운 역 없음은 fallback 처리
- 딥링크 스킴 파라미터는 공식 문서 확인 후 구현
- 책임 알람의 서버 시각이 기준이다. 기기 보고가 없다고 알람을 건너뛰지 않고, 에스컬레이션은 알람당 1회만 보낸다
- 책임 알람은 전원 opt-in일 때만 켠다. 출발지·알람 데이터는 개인정보이므로 서버에는 필요한 만큼만 저장하고 약속이 끝나면 TTL로 정리한다
- 알람 이동시간은 근사값(정차시간 제외 등)이라 실제보다 짧을 수 있다. 알람시각을 잡을 때 여유시간을 둘지 `미정` 항목으로 정한 뒤 적용한다
- FCM 서버 자격증명과 외부 API 키는 서버에만 둔다 (클라이언트에는 `google-services.json`의 공개 설정만)

## 범위
- 1단계(MVP): 위치 입력(한 명이 전체 입력) → 후보 3곳 → 시간·환승 비교 → 선정 이유 → 공유. 백엔드 없음
- 2단계: 약속 목적 필터(식사·카페·술자리, 카카오 로컬 카테고리 검색), 초대 링크(Redis 방+TTL, 앱 링크, 폴링)
- 3단계: 책임 알람 (아래 "확장" 섹션). 약속 그룹·참가자별 출발지가 필요해서 2단계 이후에 붙인다. 앱의 로컬 알람 예약·풀스크린 울림처럼 서버 없이 만들 수 있는 부분은 먼저 시작해도 된다
- 후순위: 결제, 장거리, 타 운영기관 노선

## 서버
- 담당: 서버는 한 사람이 맡아 별도 브랜치(`server/…`)에서 작업한다. 서버 작업은 `main`에 바로 올리지 않고 브랜치에서 하고, 합칠 때 PR로 한다
- API 명세는 `docs/API.md`가 기준이다. 서버와 앱은 이 문서를 먼저 고치고 구현한다
- 결정: Java 17 + Spring Boot 3.5, PostgreSQL 16(Flyway), Redis(요청 제한·캐시·에스컬레이션 ZSET), Docker Compose + Caddy(HTTPS), 익명 + 참가자 토큰 인증(로그인 없음)
- 서버는 위치와 약속 상태만 저장한다. 중앙값·최단시간·후보 선정은 앱(`Recommender`)이 하고, 책임 알람의 이동시간도 각 참가자의 앱이 계산해서 보고한다
- 도메인은 DuckDNS 서브도메인을 새로 발급해 쓴다(무료 계정은 5개까지). 서버 호스트(새 서버)는 아직 정하지 않았다
- 카카오 REST 키, FCM 서비스 계정, DB 비밀번호는 서버 환경변수로만 주입한다

## 확장: 책임 알람 (Accountability Alarm)
약속 장소가 정해지면 참가자별 이동시간을 역산해 출발 알람을 자동으로 걸고, 누가 정해진 시간 안에 알람을 안 끄면 같은 약속 멤버 전원 폰이 울린다.
- 플랫폼은 **Android only** (iOS는 원격 강제 알람을 보장할 수 없어 제외). 클라이언트 Java, 서버 Spring Boot + Redis + RDB, 푸시 FCM
- 알람시각 = 약속시간 − 이동시간 − 준비시간(개인 설정). 이동시간은 정중앙 추천에서 이미 계산한 값을 재사용 (자체 그래프 근사값, ODsay는 선택)

핵심 플로우
1. 약속 생성 → 장소·약속 시간 확정 → 서버가 참가자별 알람시각 확정
2. 서버가 FCM으로 각 기기에 동기화 → 기기는 로컬로 예약
3. 알람이 울리면 유예시간(예: 60초) 안에 끄고, 서버에 DISMISSED 보고
4. 서버는 기기 보고와 무관하게 `fireAt + 유예시간`에 직접 확인 (폰이 꺼져 있거나 오프라인이어도 에스컬레이션되어야 하기 때문)
5. DISMISSED가 없으면 나머지 멤버에게 FCM high priority 전송 → 풀스크린 알람("○○ 아직 안 일어남") + 내 알람 끄기 / 전화 걸기 버튼

도메인 모델
- Meeting: id, placeId, meetAt, gracePeriodSec, accountabilityEnabled
- Participant: id, meetingId, userId, origin, travelMinutes, prepMinutes, optedIn
- Alarm: id, participantId, fireAt, status(SCHEDULED / RINGING / DISMISSED / ESCALATED / CANCELLED)
- Device: userId, fcmToken, updatedAt

API 초안
- `POST /meetings/{id}/accountability` 책임 알람 켜기 (전원 opt-in 필요)
- `PATCH /participants/{id}/prep` 준비시간 설정
- `GET /meetings/{id}/alarms` 내 알람 조회 (동기화 실패 대비)
- `POST /alarms/{id}/ringing` 울림 시작 보고 (선택, 상태 표시용)
- `POST /alarms/{id}/dismiss` 끔 보고 (멱등)
- `POST /devices` FCM 토큰 등록

서버 에스컬레이션 스케줄러
- Redis ZSET `escalation:due`: score = fireAt + grace(epoch ms), member = alarmId
- 1초 주기 `@Scheduled`로 기한 지난 항목을 Lua 스크립트로 조회+삭제 원자 처리 (인스턴스가 여러 개여도 중복 발송 방지)
- dismiss가 들어오면 ZSET에서 ZREM + DB 상태 갱신. 에스컬레이션 직전에 DB 상태를 다시 확인해서 dismiss 경쟁 조건 방어
- 약속 시간·장소 변경 시 `fireAt` 재계산 → ZSET 갱신 + 기기 재동기화 푸시

Android 클라이언트
- 예약: `AlarmManager.setAlarmClock()` (Doze 무시, 가장 정확)
- 울림: Foreground Service + 풀스크린 인텐트 알림 + 알람 Activity
- 에스컬레이션 수신: FCM data message, priority high → 바로 풀스크린 알람 (안 띄우면 FCM 우선순위가 깎인다)
- 권한: `USE_EXACT_ALARM` 또는 `SCHEDULE_EXACT_ALARM`, `USE_FULL_SCREEN_INTENT`, `POST_NOTIFICATIONS`, `RECEIVE_BOOT_COMPLETED`
- 재부팅 시 BOOT_COMPLETED에서 로컬 알람 재예약. 온보딩에서 배터리 최적화 제외 유도(삼성·샤오미 필수)
- dismiss 보고 실패 시 로컬 큐에 쌓았다가 네트워크가 돌아오면 재전송

엣지 케이스
- dismiss했는데 네트워크가 끊겨 보고 못 함 → 억울한 에스컬레이션. 재전송 + "이미 일어남" 수동 버튼으로 완화
- 에스컬레이션은 알람당 1회만, 다른 멤버가 각자 끄면 끝
- 참가자 탈퇴·약속 취소 → 모든 기기에 CANCELLED 푸시
- 동의 없는 사람 폰이 울리면 안 되므로 **전원 opt-in일 때만 활성화**
- 심야 등 이상한 시간의 약속은 경고

알람 MVP 순서: ① 서버 도메인/API + Redis 에스컬레이션 스케줄러 → ② 앱 로컬 알람 예약 + 풀스크린 울림 + dismiss 보고 → ③ FCM 연동(동기화 + 에스컬레이션 수신) → ④ 정중앙 이동시간 연동으로 알람시각 자동 계산 → ⑤ 친구들 실기기(삼성 포함) 테스트

## 미정
- 수익 모델, 레포 구조, 수업 제약 반영
- 서버 호스트(새 서버), 약속 저장소(Postgres 가정), 책임 알람의 전화 걸기(전화번호 수집 여부), 방장이 나갈 때 처리 (`docs/API.md` 9절)
- 수도권 밖·서비스 지역 밖 출발지 처리(입력 단계 차단 기준)
- 책임 알람: RDB 선택(MySQL / Postgres), 유예시간 기본값(예: 60초), 알람시각 여유시간, 약속 변경 시 재동기화 정책

## 폴더 (임시 구조 — 레포 구조 확정 시 변경)
- `android/app/src/main/java/com/jeongjungang/` — `domain/{model,geo}`(순수 Java 코어), `data/{remote,repository}`, `ui`, `util`
- `android/` — Gradle 프로젝트 루트 (AGP 9.3 / Gradle 9.5, Groovy DSL, Java 11 호환, minSdk 26, ViewBinding 사용). `applicationId`와 패키지는 `com.jeongjungang`. Android Studio에서 이 폴더를 연다. 경로에 한글이 있으면 빌드가 실패한다
- `android/app/src/main/assets/data/` — 위 CSV 데이터 번들 위치
- 테스트: `cd android && gradlew.bat test` (Gradle 데몬이 JDK 25를 요구하므로 `JAVA_HOME`을 Android Studio 내장 JDK로 지정)
- `backend/` — 2·3단계용 Spring Boot 3.5 / Java 17 뼈대(`common` `config` `cache` `proxy`). 1단계에서는 사용하지 않음. 이전 설계(ODsay 프록시 + 역 쌍 Redis 캐시) 기준이라 2단계 착수 시 재검토. 알람 도메인·에스컬레이션 스케줄러(Redis ZSET)는 새 패키지로 추가한다
- `docs/` — 문서
