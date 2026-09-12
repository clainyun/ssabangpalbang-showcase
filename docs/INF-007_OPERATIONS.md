# INF-007 Operations Guide (Phase 1 + Phase 2)

## Purpose

INF-007 builds the shared operations base for REPORT Kafka, logs, and
PostgreSQL backup/restore tooling.

This document covers:

- Phase 1: REPORT Kafka producer wiring, broker health, backup/restore tooling
- Phase 2: AI Report Worker consumer, rejection/DLT policy, offset rules, health

## Current AI / Backend report status

| Jira | Status |
|---|---|
| AI-004 | complete (merged to `develop`) |
| AI-005 | complete (merged to `develop`) |
| AI-006 | complete (merged to `develop`) |
| AI-007 | complete — Worker · HTTP Adapter · Kafka Runtime (merged to `develop`) |
| BE-019 acquire / input / progress / complete | complete (merged to `develop`) |
| BE-019 fail | **merge 완료** (`develop`, Flyway `V21__add_report_failure_metadata.sql`) |
| INF-007 Phase 2 Backend E2E | **격리 환경 검증 완료** (실제 Spring Backend + 격리 PostgreSQL + 격리 Kafka + Fake Generation/Evidence). Worker/Producer 기본 비활성 유지. 운영 enable 미수행. |

## End-to-end report architecture

```text
Backend field-visit close/finish
→ ReportRequestedEvent
→ AFTER_COMMIT listener
→ INF-007 async Kafka Producer (optional, default off)
→ field-visit.report.request.v1
→ AI-007 FastAPI Report Worker Consumer (optional, default off)
→ AI-005 generation + AI-006 evidence linking
→ Backend acquire / input / progress / complete / fail
→ (BE-019 fail·V21 merge 완료; 격리 Backend E2E 검증 완료; 운영 enable은 별도 승인)
```

Contracts:

- [docs/contracts/report-requested-v1.md](contracts/report-requested-v1.md)
- [docs/contracts/report-worker-backend-v1.md](contracts/report-worker-backend-v1.md)

## REPORT Kafka contract summary (Producer)

- Topic: `field-visit.report.request.v1`
- Key: `studyId` string
- Payload fields: `studyId`, `sessionId`, `apartmentId`, `occurredAt`
- Retention: 7 days (`604800000` ms), cleanup policy `delete`
- Producer default: `REPORT_KAFKA_ENABLED=false`
- Kafka delivery may duplicate; AI-007 Consumer must handle duplicates safely
- Do not enable production producer before consumer E2E + separate approval

## INF-007 Phase 2 — Report Worker Consumer

### Defaults (safe)

| Variable | Default | Notes |
|---|---|---|
| `REPORT_WORKER_ENABLED` | `false` | AI consumer off |
| `REPORT_KAFKA_ENABLED` | `false` | Backend producer off |
| `REPORT_KAFKA_REJECTION_POLICY` | `block` | DLT off unless explicitly `dlt` |
| `REPORT_KAFKA_TOPIC` | `field-visit.report.request.v1` | request topic |
| `REPORT_KAFKA_DLT_TOPIC` | `field-visit.report.request.dlq.v1` | DLT topic |
| `REPORT_KAFKA_GROUP_ID` | `ai.report-worker.v1` | ops consumer group |
| `REPORT_KAFKA_DLT_RETENTION_MS` | `604800000` | 7 days |
| `REPORT_KAFKA_MAX_POLL_INTERVAL_MS` | `1800000` | 30 minutes |
| `REPORT_RETRY_BACKOFF_SECONDS` | `2` | retry pause |
| `REPORT_BACKEND_BASE_URL` | prod Compose: `http://app:8080` | Docker-internal Backend |
| `REPORT_INTERNAL_TOKEN` | empty placeholder | same value for `app` and `ai` |

### Manual offset meaning

- Consumer uses `enable_auto_commit=false`
- Commit value is always **current record offset + 1**
- Incomplete in-flight work must **not** commit on graceful shutdown

### Commit mapping

| Outcome | Offset |
|---|---|
| `COMPLETED` | commit `offset+1` |
| `ALREADY_COMPLETED` | commit `offset+1` |
| `TERMINAL_FAILED` (fail 저장 성공 포함) | commit `offset+1` |
| `POLICY_PENDING` + DLT-eligible + DLT ACK (`policy=dlt`) | commit `offset+1` |
| `RETRY_LATER` / `ALREADY_PROCESSING` | no commit; seek current; pause; backoff; resume |
| `POLICY_PENDING` + not DLT-eligible (auth/generic contract/stale/unknown) | no commit; permanent partition block; health degraded |
| `POLICY_PENDING` + `policy=block` | no commit; permanent partition block; health degraded |
| DLT publish failure | no commit; block; `DLT_PUBLISH_FAILED`; health degraded |
| `COMMIT_FAILED` / `RUNTIME_UNEXPECTED` | no commit; block; no DLT; health degraded |

### Rejection policy / DLT allowlist

- `block` (default): permanent reject → partition block, no DLT, no original commit
- `dlt`: publish only for **allowlisted** permanent cases; ACK then original commit
- DLT allowlist:
  - `INVALID_EVENT`
  - acquire `CONTRACT_CONFLICT`
  - `COMPLETE_PAYLOAD_CONFLICT`
  - `FAIL_PAYLOAD_CONFLICT`
- DLT forbidden (fail-closed block, no original commit):
  - `BACKEND_AUTH_ERROR` (wrong token must not drain the topic)
  - generic `BACKEND_CONTRACT_ERROR`
  - `STALE_PROCESSING_TOKEN`
  - schema/envelope mismatch / unknown codes
  - `COMMIT_FAILED` / `RUNTIME_UNEXPECTED`
- `BACKEND_REJECTED` alone never enables DLT; outcome + safe conflict code required
- DLT payload contains only safe metadata + `payloadHash` (SHA-256)
- Temporary outcomes (`RETRY_LATER`, `ALREADY_PROCESSING`) never publish DLT

### Topic provisioning

Compose one-shot service `kafka-report-topic-init`:

- runs after Kafka health
- create-if-not-exists for request topic and DLT topic
- partition count follows `REPORT_KAFKA_PARTITIONS`
- DLT retention follows `REPORT_KAFKA_DLT_RETENTION_MS`
- cleanup policy `delete`
- does not modify STT topics
- does not modify Backend `NewTopic` beans
- does not touch PostgreSQL

### Health

`/health` exposes report worker fields including:

- `reportWorkerEnabled`, `reportWorkerHealthy`, `reportWorkerRunning`
- `reportConsumerStarted`, `reportAssignedPartitions`, `reportBlockedPartitions`
- `reportLastPollAt`, `reportLastSuccessAt`, `reportLastErrorCode`
- `reportAttemptedStage`, `reportLastCompletedStage`
- `reportLastDltAt`, `reportDltPublishCount`, `reportDltFailureCount`
- `reportRejectionPolicy`, `reportDltTopic`

Unhealthy (`503`) when:

- consumer task stopped / required component not started
- blocked partition exists
- `COMMIT_FAILED` / `RUNTIME_UNEXPECTED` / `DLT_PUBLISH_FAILED`
- `policy=dlt` but DLT producer not started

Successful DLT publish alone is **not** unhealthy.

Lag snapshot is **not** implemented in `/health` (avoid blocking broker calls on the health path). Operators can inspect consumer group lag with Kafka CLI against the internal bootstrap when needed.

### Graceful shutdown

- AI Compose `stop_grace_period: 40s` (>= worker grace default 30s)
- stop order: stop accepting new work → wait in-flight grace → no commit of incomplete offsets → DLT producer flush/stop → consumer stop → HTTP client close
- duplicate start/stop must be safe

### Producer / Consumer enable order

1. Confirm BE-019 fail·V21이 develop에 병합됨 (완료). 운영 enable 전 실제 Backend E2E 통과 필요
2. Confirm topics exist (`kafka-report-topic-init` or verified manually)
3. Deploy AI with `REPORT_WORKER_ENABLED=false`, verify `/health`
4. Enable AI worker in a non-prod / approved window with matching token/base URL
5. Only then consider `REPORT_KAFKA_ENABLED=true` for Backend producer
6. Production enable requires **separate explicit approval**

### Incident check order (REPORT consumer)

1. `/health` report fields (`reportWorkerHealthy`, blocked partitions, last error)
2. AI logs for outcome / commitDecision / errorCode (no secrets/payloads)
3. Kafka topic existence and consumer group status (internal bootstrap only)
4. DLT topic messages (safe fields only) when `policy=dlt`
5. Backend internal API reachability from AI network
6. Decide retry vs block vs operator intervention

Do **not** auto-restart FAILED reports from the original Kafka event.
FAILED retry/admin API is out of INF-007 / AI-007 scope.

## Kafka KRaft structure

Existing Compose Kafka service:

- Image: `apache/kafka:4.3.1`
- Roles: `broker,controller`
- Internal listener: `kafka:19092`
- Production Compose exposes no external Kafka port
- Local Compose binds Kafka to `127.0.0.1` only

Do not add external listeners/ports for Sprint 2 operations.

## Broker healthcheck

Compose healthcheck uses internal bootstrap:

- command checks topic list through `kafka:19092`
- interval / timeout / retries / start_period are defined in Compose
- on failure, inspect Kafka container logs with Compose log commands

## REPORT Topic check procedure

1. Confirm `REPORT_KAFKA_ENABLED` / `REPORT_WORKER_ENABLED` values
2. Confirm topic envs: `REPORT_KAFKA_TOPIC`, `REPORT_KAFKA_DLT_TOPIC`
3. Confirm retention envs
4. Before enabling Report Worker/Producer, run/confirm `kafka-report-topic-init`
   separately (AI container does not require init success at startup)
5. Keep STT topics unchanged

## BE-019 병합 후 실제 E2E 절차 (검증 완료)

BE-019 fail API와 `V21__add_report_failure_metadata.sql`은 `develop`에 merge 완료다.
INF-007 Phase 2 최종화에서 아래를 **격리 환경에서 실제 실행·통과**했다.

Opt-in:

```bash
RUN_REPORT_BACKEND_E2E=1
```

Harness:

- `ai/tests/test_report_backend_e2e.py`
- `ai/tests/support/report_backend_e2e_harness.py`
- `infra/docker-compose.report-backend-e2e.yml`

검증한 시나리오:

1. 정상 완료 → DONE / evidence 저장 / lease 종료 / offset commit
2. 데이터 부족 → DONE + `claims=[]` / offset commit
3. 영구 실패 → FAILED + fail metadata(V21) / offset commit
4. FAILED 중복 이벤트 → ALREADY_FAILED / generation·fail 재호출 없음
5. 잘못된 Internal Token → BACKEND_AUTH_ERROR / DLT 없음 / offset 미commit / block
6. Worker 재시작 후 동일 group 재처리 → DONE / offset commit
7. malformed → safe DLT / raw·token 미포함 / offset commit
8. DLT ACK 실패 → DLT_PUBLISH_FAILED / offset 미commit / block

테스트 결과 요약 (최종화 실행):

- Backend `postgresTest` (Fail/Complete/AcquireConcurrency): 8 passed
- AI 경량 pytest: 504 passed / 6 skipped
- Kafka-only E2E: 3 passed
- Backend+PG+Kafka E2E: 7 passed (E2E-5/6 통합 1건 포함)
- Compose config: local / prod / report-kafka-e2e / report-backend-e2e → exit 0

운영 범위 (미수행):

- `REPORT_WORKER_ENABLED` / `REPORT_KAFKA_ENABLED` 운영 enable 없음
- EC2 / 운영 PostgreSQL / 운영 Kafka / `postgres-prod-data` 미접근
- 운영 활성화에는 별도 승인 필요

참고: Backend 단위 기본 `test` task는 `@Tag("postgres")`를 제외한다.
PostgreSQL 격리 테스트는 별도 Gradle task `postgresTest`로 실행한다.

Contract Stub Kafka-only E2E도 계속 사용 가능하다.

### Flyway / DB schema ownership (INF-007 must not touch)

- `origin/develop` has **V20** (`V20__enforce_auto_report_post_uniqueness.sql`, BE-023)
  and **V21** (`V21__add_report_failure_metadata.sql`, BE-019 fail metadata).
- INF-007 branch must **not**:
  - add / copy / modify any Flyway migration
  - change Backend Entity or DB schema
- If INF-007 implementation appears to need a DB schema change, **stop and report**
- Isolated Backend E2E only **applies** the merged full Flyway chain to a new
  PostgreSQL instance for verification

### Topic init vs AI startup

- Compose keeps one-shot `kafka-report-topic-init` (create-if-not-exists)
- `ai` does **not** hard-depend on topic-init success
- Reason: `REPORT_WORKER_ENABLED=false` by default; Report topic init failure must
  not block STT/other AI startup
- Before enabling Report Worker/Producer, operators must run/confirm topic-init
  (or equivalent topic existence) separately

## Log locations

### Spring Boot (`app`)

- stdout/stderr via Docker json-file logging
- levels from `application*.yaml`
- useful signals: Kafka publish failure/timeout, AI timeout, HTTP 5xx
- Compose log rotation in prod: `max-size=10m`, `max-file=5`

### FastAPI (`ai`)

- stdout/stderr via Docker json-file logging
- `LOG_LEVEL` env
- useful signals: provider timeout, schema validation failure, STT/report worker errors
- Compose log rotation in prod: `max-size=10m`, `max-file=5`

### Kafka / Redis

- container logs via Docker logging driver
- prod Compose rotation: `max-size=10m`, `max-file=5`

### PostgreSQL

- **Production Compose (`infra/docker-compose.prod.yml`) does not change the
  postgres service in INF-007**, including logging options
- Reason: any Compose service config change can mark the production PostgreSQL
  container for recreate on later deploy
- Production PostgreSQL log rotation must be reviewed separately with an approach
  that does not recreate the container or touch `postgres-prod-data`
- Local Compose PostgreSQL may use json-file rotation for local development only

### Host Nginx

Repository has no Nginx config file. On the host, verify:

- access log path used by the installed Nginx
- error log path
- HTTP 5xx responses
- upstream failures
- WebSocket upgrade errors
- logrotate configuration

Do not assume a specific host path until verified on the server.

## EC2 resource checks

Document and periodically check:

- CPU usage
- memory usage
- disk usage for Docker and backup directories
- HTTP 5xx rate at Nginx/app
- AI provider failure/timeout counts in app/ai logs

## Secret protection

- Never commit real `.env` values
- Use `infra/.env.example` and `infra/.env.prod.example` placeholders only
- Do not log passwords, JWT secrets, API keys, FCM tokens, full payloads,
  Authorization headers, `REPORT_INTERNAL_TOKEN`, or `processingToken`
- Jenkins credentials remain outside the Git repository

## PostgreSQL backup tooling

Script: `infra/scripts/backup-postgres.sh`

- custom-format dump
- timestamped filename
- refuses overwrite of dump/temp/checksum artifacts
- writes SHA-256 checksum
- succeeds only when dump + checksum pair both exist
- owner-only file permissions (`umask 077` / `chmod 600`)
- validates `POSTGRES_DB` identifier format
- rejects empty/forbidden backup directories and PostgreSQL data paths
- recommended policy: daily, retain 7 days
- scheduler and auto-deletion are out of this phase

**Gate 3 did not execute this script against any real database.**
Any real run requires separate explicit approval.

## Isolated restore tooling

Script: `infra/scripts/restore-postgres-isolated.sh`

Required operator confirmation flags (all must be exactly `true`):

- `CONFIRM_ISOLATED_RESTORE`
- `CONFIRM_APPLICATION_DISCONNECTED`
- `CONFIRM_SEPARATE_VOLUME`

These flags are operator acknowledgements only. They do **not** prove technical
isolation. Operators must separately confirm that the PostgreSQL instance and
volume are isolated from production.

Fail-closed conditions include:

- restore DB name equals production DB name
- restore volume is `postgres-prod-data`, equals production volume, or contains
  that name
- host is not `localhost` / `127.0.0.1`
- port equals production-default `5432`
- missing backup/checksum or checksum mismatch (checked before any DB call)
- target database already exists
- incomplete isolation variables

Restore rules:

- only a newly created empty database is allowed
- existing databases are refused; no object cleanup / overwrite restore
- non-local restore is forbidden
- script never creates/deletes/changes Docker containers or volumes
- `ISOLATED_RESTORE_VOLUME` is an operator-declared name; the script cannot prove
  which volume a live PostgreSQL process mounts

Allowed restore target must use:

- local isolated PostgreSQL instance (`127.0.0.1` / `localhost`)
- non-production port (example: `5433`)
- different DB name
- different declared volume
- no production app connection
- separated env vars

Post-restore checks (isolated only):

- `apartment` count
- `member`, `study`, `field_session`, `field_record`, `report` counts
- `flyway_schema_history` presence

If restore fails after `CREATE DATABASE`, a partial isolated DB may remain.
Automatic deletion is intentionally not performed.

**Gate 3 did not execute this script against any real database.**

## Absolute production PostgreSQL protection

Production volume name: `postgres-prod-data`

Production already holds **10,000+ apartment rows**.

Never:

- connect to production DB from this tooling without separate approval
- restore into production
- recreate/replace/remove `postgres-prod-data`
- change production postgres Compose service in INF-007
- change production DB name/user/password/image/port/network in INF-007
- run Flyway against production as part of INF-007 tooling

## CloudWatch

Not introduced in INF-007 Phase 1.
Optional follow-up after permission/cost review.

## Incident check order

1. Jenkins failed stage / console
2. recent successful build and commit
3. Compose config and env file readability
4. container status / network / volumes
5. Spring and FastAPI logs
6. Kafka topic and consumer status (REPORT consumer Phase 2)
7. PostgreSQL / Redis status
8. Nginx / HTTPS / Actuator
9. latest approved backup availability
10. decide whether manual recovery is required

Dangerous data-deletion commands and full-service outage commands are intentionally
not written in this document.
