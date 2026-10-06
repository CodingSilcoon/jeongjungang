# 정중앙 서버 API 명세 (v1 초안)

앱과 서버가 이 문서를 기준으로 병렬 작업한다. 바꿀 때는 이 문서를 먼저 고치고 알린다.

- 기준 주소: `https://{도메인}/api/v1` (HTTPS만 허용)
- 형식: JSON(UTF-8). 날짜·시각은 ISO 8601 + 오프셋 (예: `2026-10-10T19:00:00+09:00`). 서버는 UTC로 저장한다
- 상태: **초안**. `[단계 1]`은 서버를 처음 띄울 때, `[단계 2]`는 초대 링크, `[단계 3]`은 책임 알람

## 1. 전체 구조

서버는 **위치와 약속 상태만 저장**한다. 중앙값 계산, 지하철 최단시간, 후보 3곳 선정은 앱이 한다(`Recommender`). 그래서 책임 알람에 필요한 이동시간도 각 참가자의 앱이 자기 값을 계산해서 서버에 보고한다.

| 용어 | 뜻 |
|:---|:---|
| Meeting(약속) | 한 번의 모임. 초대 코드로 사람을 모으고 장소·시간을 확정한다 |
| Participant(참가자) | 약속에 들어온 사람. 방장(`HOST`)과 멤버(`MEMBER`) |
| Alarm(알람) | 참가자별 출발 알람. 책임 알람을 켠 약속에만 생긴다 |
| Device(기기) | 참가자의 FCM 토큰 |

## 2. 인증: 익명 + 참가자 토큰

로그인은 없다. 약속을 만들거나 초대 코드로 들어오면 서버가 **참가자 토큰**을 발급한다.

```
Authorization: Bearer {participantToken}
```

- 토큰은 추측할 수 없는 무작위 문자열(32바이트, URL-safe)이고, 서버에는 **해시만** 저장한다
- 토큰 하나는 "이 약속의 이 참가자"만 가리킨다. 다른 약속의 데이터는 볼 수 없다
- 앱은 토큰을 Android Keystore 키(AES-GCM)로 암호화해 저장하고 백업에서 뺀다(EncryptedSharedPreferences는 2025년에 지원 중단). 앱을 지우면 약속에서 빠진 것으로 본다
- 초대 코드는 8자리(혼동되는 글자 `I L O U 0 1` 제외)이고, 코드 자체가 참여 자격이다. 대신 가입 요청에 IP별 제한을 둔다
- 인증이 필요 없는 엔드포인트는 표에 **공개**로 적는다

## 3. 공통 규칙

### 응답 형식

```json
{ "success": true, "data": { }, "error": null }
```

```json
{ "success": false, "data": null, "error": { "code": "MEETING_NOT_FOUND", "message": "약속을 찾을 수 없습니다." } }
```

- `message`는 사용자에게 보여도 되는 문장이다. 내부 정보(스택, SQL, 외부 API 응답)는 넣지 않는다
- 응답에는 가능하면 `serverTime`을 넣는다. 앱이 기기 시계와의 차이를 보정하는 데 쓴다

### 오류 코드

| HTTP | code | 언제 |
|:---:|:---|:---|
| 400 | `VALIDATION_FAILED` | 입력 형식이 틀림 (필드별 사유는 `error.fields`) |
| 401 | `UNAUTHORIZED` | 토큰이 없거나 올바르지 않음 |
| 403 | `FORBIDDEN` | 방장만 할 수 있는 일, 남의 자원 접근 |
| 404 | `NOT_FOUND` | 없는 주소로 요청함 |
| 404 | `MEETING_NOT_FOUND` `PARTICIPANT_NOT_FOUND` `ALARM_NOT_FOUND` | 대상이 없음 |
| 405 | `METHOD_NOT_ALLOWED` | 그 주소가 지원하지 않는 요청 방식(예: GET 자리에 POST) |
| 409 | `MEETING_CLOSED` | 이미 확정·취소된 약속에 참가하려 함 |
| 409 | `MEETING_FULL` | 참가자 상한(10명) 초과 |
| 409 | `NOT_ALL_OPTED_IN` | 책임 알람을 켜려는데 전원이 동의하지 않음 |
| 409 | `ALARM_NOT_READY` | 장소·시간·이동시간·준비시간이 아직 다 안 정해짐 |
| 410 | `MEETING_EXPIRED` | 기간이 지나 삭제된 약속 |
| 429 | `RATE_LIMITED` | 요청이 너무 많음. `Retry-After` 헤더(초)를 준다 |
| 502 | `UPSTREAM_ERROR` | 카카오 등 외부 API 실패 |
| 500 | `INTERNAL_ERROR` | 서버 오류 |

### 요청 제한 (IP 또는 토큰 기준, 초기값)

| 대상 | 제한 |
|:---|:---|
| `GET /geocode*`, `GET /places` | IP당 30회/분 |
| `POST /meetings` | IP당 10회/분 |
| `POST /meetings/by-code/{inviteCode}/participants` | IP당 20회/분 |
| 그 외 인증 API | 토큰당 120회/분 |

### 값 제한

| 필드 | 규칙 |
|:---|:---|
| `nickname` | 1~20자, 앞뒤 공백 제거, 제어문자 불가 |
| `title` | 0~40자 |
| `origin.label` | 1~60자 |
| `lat`, `lng` | `-90~90`, `-180~180` |
| `purpose` | `MEAL` `CAFE` `DRINK` `ETC` |
| `prepMinutes` | 0~240 |
| 참가자 수 | 약속당 최대 10명 |

## 4. 데이터 보관

- 약속 데이터는 **약속 시각 + 24시간**, 약속 시각이 없으면 **생성 후 7일**에 삭제한다
- 출발지(좌표)와 별명은 같은 약속 참가자에게만 보인다. 그 외에는 어디에도 노출하지 않는다
- 전화번호는 받지 않는다. 에스컬레이션의 "전화 걸기"는 [미정 항목](#9-아직-정해지지-않은-것) 참고

---

## 5. 기본

### `GET /health` — 공개 [단계 1]

서버가 살아 있는지 확인한다. 운영 점검용 `/actuator/health`와 별개로 앱이 쓴다.

```json
{ "success": true, "data": { "status": "UP", "version": "0.1.0", "serverTime": "2026-10-10T10:00:00Z" }, "error": null }
```

### `GET /geocode?query={검색어}` — 공개 [단계 1]

주소나 장소 이름을 좌표로 바꾼다. 카카오 로컬 API를 서버가 대신 호출한다(키는 서버만 보관). 같은 검색어는 24시간 캐시한다.

| 파라미터 | 규칙 |
|:---|:---|
| `query` | 필수, 2~100자 |
| `size` | 선택, 1~10 (기본 10) |

```json
{
  "success": true,
  "data": {
    "items": [
      { "type": "PLACE", "name": "홍대입구역 2호선", "address": "서울 마포구 양화로 160", "lat": 37.5572, "lng": 126.9245 },
      { "type": "ADDRESS", "name": "서울 마포구 와우산로 94", "address": "서울 마포구 상수동 331-5", "lat": 37.5489, "lng": 126.9227 }
    ]
  },
  "error": null
}
```

결과가 없으면 `items`는 빈 배열이다(404가 아님).

### `GET /geocode/reverse?lat=&lng=` — 공개 [단계 1]

지도에서 찍은 핀 좌표를 주소 문구로 바꾼다.

```json
{ "success": true, "data": { "address": "서울 마포구 상수동 331-5", "roadAddress": "서울 마포구 와우산로 94" }, "error": null }
```

주소를 못 찾으면 `address`와 `roadAddress`는 `null`이다.

---

## 6. 약속과 초대 [단계 2]

### `POST /meetings` — 공개

약속을 만들고 방장 토큰을 받는다.

```json
{
  "title": "금요일 저녁",
  "hostNickname": "민수",
  "purpose": "MEAL",
  "meetAt": "2026-10-10T19:00:00+09:00",
  "origin": { "label": "홍대입구역", "lat": 37.5572, "lng": 126.9245 }
}
```

`title`, `purpose`, `meetAt`, `origin`은 선택이다. `purpose` 기본값은 `ETC`.

응답 `201`:

```json
{
  "success": true,
  "data": {
    "meeting": {
      "id": "6f1c6d0e-...",
      "inviteCode": "7K3QH9MX",
      "inviteUrl": "https://{도메인}/m/7K3QH9MX",
      "title": "금요일 저녁",
      "purpose": "MEAL",
      "meetAt": "2026-10-10T19:00:00+09:00",
      "status": "OPEN",
      "place": null,
      "expiresAt": "2026-10-11T19:00:00+09:00"
    },
    "participant": { "id": "a2b9...", "nickname": "민수", "role": "HOST" },
    "participantToken": "…"
  },
  "error": null
}
```

초대 링크 `https://{도메인}/m/{inviteCode}`는 앱이 설치돼 있으면 앱으로 열리고(Android App Links), 없으면 안내 페이지로 간다.

- App Links가 동작하려면 서버가 `https://{도메인}/.well-known/assetlinks.json`을 `Content-Type: application/json`으로, 리다이렉트 없이 준다
- 내용은 패키지 `com.jeongjungang`과 앱 서명 인증서의 SHA-256 지문이다. 디버그 키는 사람마다 달라서 시연할 기기에 설치하는 빌드의 지문을 모두 넣는다(`cd android && gradlew.bat signingReport`로 확인)

```json
[{
  "relation": ["delegate_permission/common.handle_all_urls"],
  "target": { "namespace": "android_app", "package_name": "com.jeongjungang",
              "sha256_cert_fingerprints": ["AA:BB:…"] }
}]
```

- 앱은 링크가 안 열리는 경우를 대비해 **링크나 코드를 붙여 넣어 참가**하는 입력도 지원한다(소문자, 공백, 하이픈 허용)

### `GET /meetings/by-code/{inviteCode}` — 공개

초대 화면에서 "누구의 어떤 약속인지" 미리 보여 준다. 위치 같은 개인 정보는 주지 않는다.

```json
{
  "success": true,
  "data": { "title": "금요일 저녁", "hostNickname": "민수", "participantCount": 2, "status": "OPEN", "meetAt": "2026-10-10T19:00:00+09:00" },
  "error": null
}
```

### `POST /meetings/by-code/{inviteCode}/participants` — 공개

초대 코드로 참가하고 토큰을 받는다.

```json
{ "nickname": "지현", "origin": { "label": "노원역", "lat": 37.6552, "lng": 127.0614 } }
```

응답 `201`: `{ "participant": {...}, "participantToken": "…", "meetingId": "…" }`
오류: `MEETING_NOT_FOUND`, `MEETING_CLOSED`(확정·취소됨), `MEETING_FULL`, `MEETING_EXPIRED`

### `GET /meetings/{meetingId}` — 참가자 [단계 2]

약속의 현재 상태 전체. 앱이 **3~5초 간격으로 폴링**한다.

```json
{
  "success": true,
  "data": {
    "version": 12,
    "meeting": { "id": "…", "title": "금요일 저녁", "purpose": "MEAL", "meetAt": "…", "status": "OPEN",
                 "place": null, "accountability": { "enabled": false, "gracePeriodSec": 60, "marginMinutes": 5 } },
    "participants": [
      { "id": "a2b9…", "nickname": "민수", "role": "HOST", "origin": { "label": "홍대입구역", "lat": 37.5572, "lng": 126.9245 },
        "prepMinutes": null, "optedIn": false },
      { "id": "c4d1…", "nickname": "지현", "role": "MEMBER", "origin": null, "prepMinutes": null, "optedIn": false }
    ],
    "me": { "participantId": "c4d1…", "role": "MEMBER" },
    "serverTime": "2026-10-10T10:00:00Z"
  },
  "error": null
}
```

- `version`은 약속·참가자 정보가 바뀔 때마다 올라간다. 응답에 `ETag: "v12"`를 주고, 앱이 `If-None-Match: "v12"`로 요청하면 바뀐 게 없을 때 **`304`** 로 응답한다
- `place`가 확정되면 `{ "name": "답십리역", "lat": 37.5669, "lng": 127.0527, "lines": [5] }` 형태가 된다
- `participants`는 **들어온 순서**(먼저 들어온 사람이 앞)로 준다. 방장이 나갈 때 이 순서로 다음 방장을 정한다

### `PATCH /meetings/{meetingId}` — 방장

방장만 약속 정보를 바꾼다. 보낸 필드만 바뀐다.

```json
{
  "title": "금요일 저녁",
  "meetAt": "2026-10-10T19:00:00+09:00",
  "purpose": "MEAL",
  "place": { "name": "답십리역", "lat": 37.5669, "lng": 127.0527, "lines": [5] },
  "status": "CONFIRMED"
}
```

- `status`는 `OPEN` → `CONFIRMED`(장소·시간 확정)만 허용한다. 되돌리려면 `OPEN`을 보낸다
- 장소나 시간이 바뀌면 책임 알람이 켜진 약속은 알람시각을 다시 계산하고 기기에 재동기화 푸시를 보낸다 [단계 3]
- `status: "CONFIRMED"`에는 `place`와 `meetAt`이 있어야 한다. 없으면 `VALIDATION_FAILED`

### `DELETE /meetings/{meetingId}` — 방장

약속을 취소하고 데이터를 지운다. 책임 알람이 켜져 있으면 모든 기기에 취소 푸시를 보낸다 [단계 3]. 응답 `204`.

### `PATCH /participants/{participantId}` — 본인

내 정보를 바꾼다. 보낸 필드만 바뀐다. 다른 사람 것은 `FORBIDDEN`.

```json
{ "nickname": "지현", "origin": { "label": "노원역", "lat": 37.6552, "lng": 127.0614 }, "prepMinutes": 40, "optedIn": true }
```

`prepMinutes`와 `optedIn`은 책임 알람용이다 [단계 3].

### `DELETE /participants/{participantId}` — 본인 또는 방장

약속에서 나가거나(본인) 내보낸다(방장). 응답 `204`.

**방장이 나갈 때** (2026-10-02 결정)
- 방장이 자기 자신을 지우면, 남은 참가자 중 **가장 먼저 들어온 사람**이 방장(`HOST`)이 된다. 응답은 `204`
- 방장 혼자 남은 약속이면 **약속을 취소**한다(`DELETE /meetings/{id}`와 같게 데이터 삭제, 책임 알람이 켜져 있으면 취소 푸시). 응답은 `204`
- 다른 참가자는 폴링에서 `role`이 바뀐 것으로 알게 된다. 새 방장의 토큰은 그대로 쓰고, 서버는 그 참가자의 권한만 바꾼다
- 방장이 다른 참가자를 내보낼 때는 방장이 바뀌지 않는다

### `GET /places` — 공개 [단계 2]

확정한 역 주변에서 약속 목적에 맞는 장소를 찾는다(카카오 로컬 카테고리 검색을 서버가 대신 호출).

| 파라미터 | 규칙 |
|:---|:---|
| `lat`, `lng` | 필수. 보통 확정한 역 좌표 |
| `category` | 필수. `FOOD` `CAFE` `BAR` |
| `radius` | 선택, 100~1000(m), 기본 500 |
| `page` | 선택, 기본 1 |

```json
{
  "success": true,
  "data": {
    "items": [
      { "name": "○○식당", "category": "한식", "address": "서울 동대문구 …", "lat": 37.567, "lng": 127.053,
        "distanceMeters": 180, "placeUrl": "https://place.map.kakao.com/…" }
    ],
    "page": 1, "hasNext": true
  },
  "error": null
}
```

---

## 7. 책임 알람 [단계 3]

알람시각은 서버가 정한다.

```
fireAt = meetAt − travelMinutes − prepMinutes − marginMinutes
```

`travelMinutes`는 앱이 계산해서 보고하고, `prepMinutes`는 본인이 설정한다. `marginMinutes`(여유시간)의 기본값은 **5분**이다(2026-10-02 결정). 이동시간이 근사값(역까지 접근은 가정값, 배차 대기 미포함)이라 실제보다 짧게 나올 수 있어서 둔다. 약속마다 바꿀 수 있다.

### `PUT /participants/{participantId}/travel` — 본인

확정된 장소까지 내가 걸리는 시간. 앱이 `Recommender`/`RouteFinder`로 계산한 값이다. 장소가 바뀌면 다시 보낸다.

```json
{ "placeName": "답십리역", "minutes": 27.0, "transfers": 1 }
```

응답 `204`. `placeName`이 서버의 확정 장소와 다르면 `409 ALARM_NOT_READY`.

### `POST /meetings/{meetingId}/accountability` — 방장

책임 알람을 켠다. **전원이 `optedIn: true`일 때만** 켜진다.

```json
{ "gracePeriodSec": 60, "marginMinutes": 5 }
```

- `gracePeriodSec`: 30~300, 기본 60
- 전원 동의가 아니면 `409 NOT_ALL_OPTED_IN`, 장소·시간이 확정되지 않았으면 `409 ALARM_NOT_READY`
- 켜진 뒤 `travelMinutes`·`prepMinutes`가 모두 들어온 참가자부터 알람이 만들어지고(`SCHEDULED`), 각 기기에 `ALARM_SYNC` 푸시가 간다
- 응답 `200`: `{ "enabled": true, "gracePeriodSec": 60, "marginMinutes": 5 }`

### `DELETE /meetings/{meetingId}/accountability` — 방장

책임 알람을 끈다. 모든 알람이 `CANCELLED`가 되고 `ALARM_CANCELLED` 푸시가 간다. 응답 `204`.

### `POST /devices` — 본인

푸시를 받을 기기를 등록한다(없으면 만들고 있으면 갱신).

```json
{ "fcmToken": "…", "platform": "ANDROID", "appVersion": "0.1.0" }
```

응답 `204`. FCM 토큰이 바뀌면 다시 호출한다.

### `GET /meetings/{meetingId}/alarms` — 본인

내 알람 정보. 푸시를 놓쳤을 때 동기화하는 용도다. **내 알람만** 돌려준다.

```json
{
  "success": true,
  "data": {
    "accountability": { "enabled": true, "gracePeriodSec": 60 },
    "alarm": { "id": "9b7e…", "fireAt": "2026-10-10T17:53:00+09:00", "graceUntil": "2026-10-10T17:54:00+09:00", "status": "SCHEDULED" },
    "serverTime": "2026-10-10T10:00:00Z"
  },
  "error": null
}
```

알람이 아직 없으면 `alarm`은 `null`이다.

### `POST /alarms/{alarmId}/ringing` — 본인 (선택)

기기에서 알람이 울리기 시작했음을 알린다. 상태 표시용이고, 보내지 않아도 에스컬레이션은 동작한다. 응답 `204`.

### `POST /alarms/{alarmId}/dismiss` — 본인 (멱등)

알람을 껐다고 보고한다. 같은 요청을 몇 번 보내도 결과가 같다. 네트워크가 끊겼을 때는 앱이 큐에 쌓았다가 복구되면 재전송한다.

```json
{ "dismissedAt": "2026-10-10T17:53:20+09:00", "source": "DEVICE" }
```

- `source`: `DEVICE`(알람 화면에서 끔) 또는 `MANUAL_AWAKE`(앱의 "이미 일어남" 버튼)
- 응답은 항상 `200`이고 처리 후 상태를 준다: `{ "status": "DISMISSED" }`
- 이미 `ESCALATED`면 되돌리지 않고 `{ "status": "ESCALATED" }`를 준다
- 상태 전이: `SCHEDULED`/`RINGING` → `DISMISSED`. `CANCELLED`는 그대로

### 알람 상태

```
SCHEDULED ──(기기 울림 보고)──> RINGING ──(끔 보고)──> DISMISSED
    │                              │
    └────(fireAt + 유예시간까지 DISMISSED 없음)────> ESCALATED
    └────(약속 취소·알람 끔·참가자 탈퇴)────> CANCELLED
```

서버는 기기 보고와 관계없이 `fireAt + gracePeriodSec`에 상태를 확인하고, `DISMISSED`가 아니면 `ESCALATED`로 바꾼 뒤 **알람당 1회만** 나머지 멤버에게 푸시를 보낸다. 보내기 직전에 DB 상태를 다시 확인해서 dismiss와의 경쟁을 막는다.

---

## 8. 푸시 메시지 (FCM)

모두 **data 메시지**, `priority: high`이고 내용은 최소로 담는다. 앱은 푸시를 받으면 필요할 때 `GET`으로 자세한 내용을 가져온다. 수신 즉시 화면을 띄우지 않으면 FCM 우선순위가 낮아질 수 있어서, `ESCALATION`은 받자마자 풀스크린 알람을 띄운다.

| `type` | 보내는 때 | 받는 사람 | 필드 |
|:---|:---|:---|:---|
| `ALARM_SYNC` | 알람이 만들어지거나 시각이 바뀜 | 해당 참가자 | `meetingId` |
| `ALARM_CANCELLED` | 약속 취소, 알람 끔, 참가자 탈퇴 | 해당 참가자 | `meetingId` |
| `ESCALATION` | 유예시간 안에 못 끔 | 같은 약속의 **나머지 멤버**(동의한 사람, 기기 등록된 사람) | `meetingId`, `alarmId`, `sleeperNickname`, `fireAt`, `escalatedAt` |

`ESCALATION` 메시지는 TTL을 120초로 둔다. 오래 지난 뒤 도착하면 무의미하기 때문이다.

---

## 9. 아직 정해지지 않은 것

| 항목 | 현재 가정 | 정할 시점 |
|:---|:---|:---|
| 약속 저장소 | PostgreSQL에 저장하고 만료 삭제 작업을 돌린다. Redis는 요청 제한, 캐시, 에스컬레이션 ZSET에만 쓴다 | 서버 구현 시작 전 |
| 전화 걸기 | 전화번호를 받지 않는다. 받는다면 참가자가 자발적으로 입력한 `phone`만 에스컬레이션 푸시에 실어 보낸다 | 3단계 시작 전 |
| 약속 시각이 새벽인 경우 | 서버는 `meetAt`을 막지 않고, 앱이 경고한다 | 3단계 |
| 카카오 로컬 API 쿼터 | 콘솔에서 확인 필요 | 카카오 앱 생성 후 |

## 10. 서버 구현 메모

- 스택: Java 17, Spring Boot 3.5, PostgreSQL 16(Flyway), Redis, Docker Compose, Caddy(HTTPS)
- 단계별 패키지(제안): `common`(응답·예외), `geocode`(카카오 프록시), `meeting`, `participant`, `alarm`, `push`(FCM), `ratelimit`
- 비밀 값(카카오 REST 키, FCM 서비스 계정, DB 비밀번호)은 환경변수로만 주입하고 저장소에 두지 않는다
