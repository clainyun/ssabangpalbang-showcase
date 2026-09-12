# INF-007 Report Backend E2E (isolated)

Opt-in harness only. Never points at ops PostgreSQL, Kafka, EC2, or
`postgres-prod-data`.

## Prerequisites

1. Docker daemon available
2. Ability to run Gradle from `backend/`
   (harness always runs `bootJar -x test --no-daemon --rerun-tasks` and
   never short-circuits on an existing jar)
3. `RUN_REPORT_BACKEND_E2E=1`

## Isolation

Compose project / DB / volume / ports / topics / consumer group are generated
with a random suffix by `ai/tests/support/report_backend_e2e_harness.py`.

## Schema and fixtures

1. Isolated `backend-e2e` starts with Spring profile `local` and applies the
   repository Flyway chain **V1 through the latest migration** on the
   isolated PostgreSQL database only.
2. After Backend is healthy, the harness inserts **minimum fixtures** via
   `psql` (`seed_three_studies` / `seed_sparse_study` / `_seed_one`).
   There is **no** separate `seed_fixture.sql` file.
3. Ops DB, shared DB names, and existing volumes (including
   `postgres-prod-data` / `ssabangpalbang-*`) are never used.

## Generated env

Harness writes `infra/e2e/report-backend/.env.generated.<suffix>` with
**test-only** credentials/token for that run and deletes it on stack
teardown. Do not commit these files.
