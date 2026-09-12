# Report Worker Backend Internal API v1

Status: **BE-019 acquire·progress·input·complete·fail IMPLEMENTED (V21 merge 완료) — 실제 Spring Backend 격리 E2E 통과**
Owner (AI consumer): `AI-007`
Owner (Backend implementer): `BE-019` (`POST /acquire`, `PATCH /progress`, `GET /{reportId}/input`, `PUT /{reportId}/complete`, `PUT /{reportId}/fail` 완료 · Flyway `V21__add_report_failure_metadata.sql` merge 완료)
Freeze point: INF-007 Phase 2 최종화 (Contract Stub + 실제 Backend/PostgreSQL/Kafka 격리 E2E 통과; 운영 enable 미수행)

> 이 문서는 Gate 3B-A Contract Stub으로 검증한 뒤 BE-019에서 채택한 내부 계약입니다.
> 실제 Spring Controller / Swagger / `docs/API.md`는 BE-019에서 API별로 반영합니다.
> Contract Stub은 운영 Backend가 아닙니다.

## Base

- Base path: `/internal/v1/reports`
- Request Content-Type: `application/json`
- Auth header: `Authorization: Bearer <REPORT_INTERNAL_TOKEN>`
- Token은 환경변수로만 주입합니다 (Query Parameter 금지, 하드코딩 금지)

### Backend 공통 응답 형식

`200` 조회 응답과 모든 오류 응답은 Backend 공통 평면
`ApiResponse<T>`를 사용한다.

| Field | Type | Notes |
|---|---|---|
| `success` | boolean | 성공 `true`, 오류 `false` |
| `code` | string | Backend `ResponseCode`의 machine code |
| `message` | string | 안전한 응답 메시지 |
| `data` | object\|null | 성공 DTO 또는 오류 부가정보 |
| `timestamp` | string | `Asia/Seoul` offset을 포함한 ISO-8601 |

`acquire`와 `input`의 실제 DTO는 최상위가 아니라 `data` 아래에 있다.
AI Adapter는 공통 envelope 검증 후 `data`를 unwrap한다.

`progress` / `complete` / `fail` 성공은 **정확히 `204 No Content`이며
body가 없다.** 공통 envelope를 붙이지 않는다. 쓰기 API의 `200` 응답 또는
body가 있는 `204` 응답은 AI Adapter가 `BACKEND_CONTRACT_ERROR`로 거부한다.

## Processing Lease (필수)

`ACQUIRED`로 발급된 `processingToken`에는 **Backend 관리 Lease(TTL)** 가 존재한다.

| 조건 | acquire 결과 |
|---|---|
| Lease 유효 + IN_PROGRESS | `ALREADY_PROCESSING` |
| Lease 만료 + IN_PROGRESS | 새 `processingToken`으로 `ACQUIRED`, `processingAttempt` 증가 |
| DONE | `ALREADY_COMPLETED` |
| FAILED | `ALREADY_FAILED` (Kafka 원본 이벤트로 자동 재시작하지 않음) |

규칙:

- Lease TTL은 Worker의 단일 이벤트 최대 처리 예산 이상이어야 한다.
  현재 Worker 경계값은 `REPORT_KAFKA_MAX_POLL_INTERVAL_MS`이며 기본값은
  1,800,000ms(30분)다. Backend와 Worker 배포 설정을 함께 검증한다.
- 유효 Token+Attempt로 `progress` 저장에 성공할 때마다 Backend는 Lease를
  전체 TTL로 갱신한다.
- Lease 만료 후 또는 재선점 후 이전 Token의 `progress` / `complete` / `fail`은
  거부한다 (`409` + `STALE_PROCESSING_TOKEN`).
- 재선점 후 이전 Token은 즉시 stale
- Worker가 `acquire` 직후 죽어도 Lease 만료 후 재선점 가능 → 영구 `ALREADY_PROCESSING` 금지
- Report 영속 상태는 `PENDING` / `IN_PROGRESS` / `DONE` / `FAILED`를 사용한다.
  `PROCESSING`은 Report 영속 상태로 사용하지 않는다.

### Optional response fields

| Field | When | Notes |
|---|---|---|
| `leaseExpiresAt` | `ACQUIRED` | 관측용. 없어도 Backend TTL이 권위 |
| `retryAfterSeconds` | `ALREADY_PROCESSING` | 관측/backoff 힌트. Worker는 고정 backoff도 허용 |

**선택 근거 (Gate 3B-A):**

- `leaseExpiresAt`/`retryAfterSeconds`를 **선택 필드**로 둔다.
- 장점: 운영 관측·클라이언트 backoff 힌트
- 단점: 시계 왜곡. 권위는 항상 Backend TTL 재선점 결과(`ACQUIRED` vs `ALREADY_PROCESSING`)
- 필드가 없어도 Lease/재선점 의미는 계약 필수이다.

## Endpoints

### POST `/internal/v1/reports/acquire`

원자적 create-or-get + processing claim.

Request:

| Field | Type | Notes |
|---|---|---|
| studyId | number | >= 1 |
| sessionId | number | >= 1 |
| apartmentId | number | >= 1 |
| occurredAt | string | timezone 포함 ISO-8601 |

Response `200 ApiResponse<AcquireResponse>`:

```json
{
  "success": true,
  "code": "REPORT_ACQUIRE_SUCCESS",
  "message": "리포트 처리권을 확인했습니다.",
  "data": {
    "status": "ACQUIRED",
    "reportId": 1,
    "processingToken": "<opaque-token>",
    "processingAttempt": 1,
    "leaseExpiresAt": "2026-08-02T13:00:00+09:00",
    "retryAfterSeconds": null
  },
  "timestamp": "2026-08-02T12:30:00+09:00"
}
```

위 성공 `code`와 `message`는 BE-019의 `ReportResponseCode`로 확정했다.
공통 다섯 필드와 `data` 내부 DTO 구조도 고정 계약이다.

`data` fields:

| Field | Type | Notes |
|---|---|---|
| status | enum | `ACQUIRED` / `ALREADY_COMPLETED` / `ALREADY_PROCESSING` / `ALREADY_FAILED` / `CONTRACT_CONFLICT` |
| reportId | number\|null | `ACQUIRED` 필수 |
| processingToken | string\|null | `ACQUIRED` 필수 |
| processingAttempt | number\|null | `ACQUIRED` 시 >= 1 |
| leaseExpiresAt | string\|null | optional |
| retryAfterSeconds | number\|null | optional |

WorkerOutcome / CommitDecision 매핑:

| acquire status | WorkerOutcome | CommitDecision |
|---|---|---|
| ACQUIRED | (파이프라인 진행) | 결과 따름 |
| ALREADY_COMPLETED | ALREADY_COMPLETED | COMMIT |
| ALREADY_PROCESSING | ALREADY_PROCESSING | DO_NOT_COMMIT |
| ALREADY_FAILED | TERMINAL_FAILED | COMMIT |
| CONTRACT_CONFLICT | CONTRACT_CONFLICT | POLICY_PENDING |

### GET `/internal/v1/reports/{reportId}/input`

AI-004 `ReportNormalizationSource` 전체. TEXT/STT 원문 **로그 금지**.

- 내부 Bearer Token만 사용한다. Request Body, Query Parameter,
  `processingToken`, `processingAttempt`는 받지 않는다.
- Backend는 `report.field_session_id`를 권위 세션으로 사용하고
  `REPEATABLE_READ`, read-only 트랜잭션 하나에서 스냅샷을 구성한다.
- 응답: `200 ApiResponse<ReportNormalizationSource>`
- 성공 code/message: `REPORT_INPUT_SUCCESS` / `리포트 입력을 조회했습니다.`
- AI Adapter는 최상위 공통 envelope 검증 후 `data`만
  `ReportNormalizationSource`로 검증한다.
- Report가 없거나 Report·Study·Apartment·field_session의 권위 관계가
  유효하지 않거나 Study가 삭제·취소된 경우 안전한 단일 경계인
  `404 REPORT_NOT_FOUND`를 반환한다. 다른 세션이나 아파트로 대체 조회하지 않는다.
- 위 `404`는 AI Adapter에서 재시도 불가 `SOURCE_LOAD_FAILED`로 변환되고,
  Worker는 terminal fail 저장 후 Kafka offset을 COMMIT한다.
- 권위 관계가 유효하면 삭제 기록, 빈 TEXT, 미완료 STT, 사용할 수 없는 PHOTO도
  `200` 원본에 포함한다. AI-004가 제외 사유와 품질 이슈를 판정한다.

### PATCH `/internal/v1/reports/{reportId}/progress`

- Request: `processingToken`, `processingAttempt >= 1`, `stage`
- stage: `RECORD_COLLECTION`, `STT_VALIDATION`, `NORMALIZATION`,
  `REPORT_GENERATION`, `EVIDENCE_MAPPING`, `RESULT_SAVING`만 허용 (`COMPLETED` 금지)
- Report는 `IN_PROGRESS`여야 하며 유효 Token+Attempt+Lease가 필요
- 동일 단계 재전송 허용, 성공할 때마다 Lease를 전체 TTL로 갱신
- 정상 다음 단계만 허용하며 역행 또는 임의 건너뛰기는 거부
- `EVIDENCE_MAPPING`은 조건부이므로 `REPORT_GENERATION -> RESULT_SAVING` 허용
- Token/Attempt 불일치, Lease 만료, 재선점, 비 `IN_PROGRESS` 상태,
  허용되지 않은 단계 전이는
  `409 STALE_PROCESSING_TOKEN`
- Report 없음은 `404 REPORT_NOT_FOUND`
- 응답: body 없는 정확한 `204`

### PUT `/internal/v1/reports/{reportId}/complete`

- `generationResult` / `evidenceResult` 전체 전달 (축약 금지)
- 결과와 근거 저장, `status=DONE`, `progressStage=COMPLETED`, 완료 시각을
  한 트랜잭션에서 원자적으로 반영한다.
- 응답: body 없는 정확한 `204`
- 동일 payload 재호출: 멱등 성공
- 이미 DONE + 상이 payload: `409` + `COMPLETE_PAYLOAD_CONFLICT`
- stale token: `409` + `STALE_PROCESSING_TOKEN`

Worker 처리:

| complete 결과 | WorkerOutcome | CommitDecision |
|---|---|---|
| 204 | COMPLETED | COMMIT |
| retryable error (timeout/5xx) | RETRY_LATER | DO_NOT_COMMIT |
| nonretryable 409 conflict | BACKEND_REJECTED | POLICY_PENDING (무한 RETRY_LATER 금지, fail 덮어쓰기 금지) |

### PUT `/internal/v1/reports/{reportId}/fail`

- 고정 안전 `message`만
- 동일 fail 명령 재호출: 멱등 성공 (body 없는 정확한 `204`)
- 다른 Token/Attempt 또는 상이 fail payload: `409` + `STALE_PROCESSING_TOKEN` / `FAIL_PAYLOAD_CONFLICT`
- fail 응답 유실 후 재전달: 동일 명령이면 멱등 `204` → Worker `TERMINAL_FAILED` → COMMIT
- FAILED 상태에서 동일 Kafka acquire: `ALREADY_FAILED` → COMMIT (자동 재처리 아님)
- 현재 Kafka 원본 이벤트 경로에서는 FAILED를 자동 재시작하지 않는다.

### FAILED 명시적 retry 경계

- 재시작 권한과 `retryable` 검증은 Backend의 명시적 retry API가 소유한다.
- retry가 허용되면 Backend는 기존 `reportId`를 유지한 채 먼저
  `FAILED -> PENDING`으로 전이하고, 트랜잭션 커밋 후 report 요청 이벤트를 발행한다.
- 이후 이벤트의 `acquire`만 새 `processingToken`을 발급하고
  `processingAttempt`를 증가시켜 처리한다.
- 명시적 retry 전의 일반 중복/재전달 이벤트는 계속 `ALREADY_FAILED`이며 COMMIT한다.
- AI-007은 retry API를 호출하거나 FAILED 상태를 직접 변경하지 않는다.

## 409 conflict codes

오류도 Backend 공통 평면 `ApiResponse`를 사용한다. 응답 body는 로그 금지이며
Adapter는 최상위 `code`만 allowlist로 안전 파싱한다.

```json
{
  "success": false,
  "code": "STALE_PROCESSING_TOKEN",
  "message": "리포트 처리 상태가 충돌합니다.",
  "data": null,
  "timestamp": "2026-08-02T12:30:00+09:00"
}
```

허용 conflict `code`:

- `STALE_PROCESSING_TOKEN`
- `COMPLETE_PAYLOAD_CONFLICT`
- `FAIL_PAYLOAD_CONFLICT`

Adapter는 HTTP 409를 `BACKEND_CONTRACT_ERROR` + `retryable=false`로 매핑한다.
내부 `ReportBackendError.message`에는 allowlist로 검증한 conflict code만 전달할 수
있으며, 로그에는 HTTP status/path/`conflictCode`만 남긴다.

## Error semantics (AI Adapter mapping)

Backend Port (acquire/progress/complete/fail):

| HTTP | Worker error.code | retryable |
|---|---|---|
| timeout / network | `BACKEND_TRANSIENT` | true |
| 5xx | `BACKEND_TRANSIENT` | true |
| 401 / 403 | `BACKEND_AUTH_ERROR` | false |
| 400 / 404 / 422 / 409 | `BACKEND_CONTRACT_ERROR` | false |

Input Port (`GET .../input`) HTTP → Adapter code → Worker:

| HTTP / 조건 | Adapter code | WorkerOutcome | fail 저장 | CommitDecision |
|---|---|---|---|---|
| 404 | `SOURCE_LOAD_FAILED` | TERMINAL_FAILED | 예 | COMMIT |
| 400 / 409 / 422 | `BACKEND_CONTRACT_ERROR` | BACKEND_REJECTED | 아니오 | POLICY_PENDING |
| 401 / 403 | `BACKEND_AUTH_ERROR` | BACKEND_REJECTED | 아니오 | POLICY_PENDING |
| 5xx / timeout / network | `BACKEND_TRANSIENT` | RETRY_LATER | 아니오 | DO_NOT_COMMIT |
| invalid JSON / Schema mismatch | `BACKEND_CONTRACT_ERROR` | BACKEND_REJECTED | 아니오 | POLICY_PENDING |
| SOURCE_LOAD_FAILED + retryable | `SOURCE_LOAD_FAILED` | RETRY_LATER | 아니오 | DO_NOT_COMMIT |
| 알 수 없는 Exception | (전파) | RETRY_LATER | 아니오 | DO_NOT_COMMIT |

## Security

- Spring Security는 `/internal/v1/reports/**` 전용 `SecurityFilterChain`을 사용자 API보다
  높은 우선순위로 둔다.
- 전용 chain은 `Authorization: Bearer <REPORT_INTERNAL_TOKEN>`을 검증하며,
  사용자 JWT 필터는 `/internal/v1/reports/**`에 적용하지 않는다.
- `/internal/v1/reports/**`를 `permitAll`로 열거나 사용자 Access Token 인증과 혼용하지 않는다.
- Authorization Header / Token / processingToken 로그 금지
- HTTP response body 로그 금지
- TEXT/STT/semanticText/claimText 로그 금지
- traceback 로그 금지

## Idempotency

- acquire: studyId 기준 create-or-get + lease claim
- complete: 동일 결과 재호출 성공, 상이 결과 `COMPLETE_PAYLOAD_CONFLICT`
- fail: 동일 명령 재호출 성공, 상이 명령 `FAIL_PAYLOAD_CONFLICT`
- progress: 유효 Token+Attempt+Lease만 허용

## BE-019 상태

- `POST /internal/v1/reports/acquire`: Backend 구현 완료
- `PATCH /internal/v1/reports/{reportId}/progress`: Backend 구현 완료
- `GET /internal/v1/reports/{reportId}/input`: Backend 구현 완료
- `PUT /internal/v1/reports/{reportId}/complete`: Backend 구현 완료
- `PUT /internal/v1/reports/{reportId}/fail`: Backend 구현·V21 merge 완료 · 실제 Worker/Backend 격리 E2E 통과
- Gate 3B-A Contract Stub + HTTP Adapter 검증 결과를 Backend 계약으로 채택
- BE-019가 Controller/DTO/Swagger/`docs/API.md`를 API별로 구현
- Flyway: `V21__add_report_failure_metadata.sql`이 develop에 merge됨.
  INF-007은 migration 추가·복제·수정·Entity/스키마 변경을 하지 않음.
- INF-007 Phase 2는 Contract Stub만이 아니라 실제 Spring Backend + 격리 PostgreSQL +
  격리 Kafka E2E(`RUN_REPORT_BACKEND_E2E=1`)도 통과했다.
  검증 범위는 격리 환경이며 운영 enable·운영 DB/Kafka 접근은 포함하지 않는다.
- AI Adapter는 409 allowlisted conflict code를 `ReportBackendError.conflict_code`로 전달한다
  (`STALE_PROCESSING_TOKEN` / `COMPLETE_PAYLOAD_CONFLICT` / `FAIL_PAYLOAD_CONFLICT`).
  DLT는 COMPLETE/FAIL conflict와 INVALID_EVENT/CONTRACT_CONFLICT만 허용한다.
- FAILED 재시작은 Backend 명시적 retry API가 `FAILED -> PENDING` 전이와
  이벤트 발행을 담당하며 AI-007 범위에는 포함하지 않음
