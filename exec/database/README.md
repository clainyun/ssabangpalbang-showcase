# DB 최신본·덤프·복원

## 1. DB 정본

| 항목 | 값 |
|---|---|
| DBMS | PostgreSQL 17 |
| 이미지 | `pgvector/pgvector:0.8.5-pg17-trixie` |
| 추가 패키지 | PostGIS 3 |
| 기준 브랜치·commit | `origin/develop` · `d9c6de874fc5ed9310c9618b2550b62702d678af` |
| 최신 Flyway | `V34__restyle_apartment_images_v4.sql` |
| Migration 위치 | `backend/src/main/resources/db/migration/` |
| Hibernate DDL | `none` |

이 폴더에는 개인정보와 운영 데이터를 제외한 최신 포터블 SQL dump가 포함되어 있다. 애플리케이션 테이블 데이터는 비어 있고, 복원 후 Flyway가 `V34` 상태를 정확히 인식하도록 `flyway_schema_history`의 34개 이력만 포함한다.

```text
ssabangpalbang_schema_v34.sql
ssabangpalbang_schema_v34.sql.sha256
```

- 생성 DB: PostgreSQL 17.10
- Migration 도구: Flyway OSS 11.7.2
- 생성 도구: `pg_dump` 17.10
- 생성 방식: 빈 격리 DB에 develop의 Flyway `V1`~`V34`를 적용한 뒤 `--no-owner --no-acl`
- 포함 데이터: `flyway_schema_history` 34행과 확장 설치용 시스템 데이터만 포함, 사용자·아파트·스터디·기록 등 애플리케이션 데이터 없음
- SHA-256: `3df2dfa5fa9aff8c166343fe0a49644ce4de4bf8a3875e26aea6589f7a043bb6`
- 검증: 별도 PostgreSQL 17.10 빈 DB에 복원 후 Flyway 11.7.2 `validate` 성공, `member`·`field_record`·`report` 각 0행 확인

확장 모듈:

```sql
vector
postgis
pgcrypto
pg_trgm
```

develop에 포함된 Flyway `V1`~`V34`가 스키마의 정본이다. Backend가 `local` 또는 `prod` profile로 최초 기동되면 빈 DB에 순서대로 적용한다.

## 2. 최신 스키마 확인

```sql
SELECT installed_rank, version, description, success
FROM flyway_schema_history
ORDER BY installed_rank DESC;
```

맨 위 성공 행이 `34 / restyle apartment images v4`인지 확인한다.

테이블과 확장 확인:

```sql
SELECT extname FROM pg_extension ORDER BY extname;

SELECT table_name
FROM information_schema.tables
WHERE table_schema = 'public'
ORDER BY table_name;
```

## 3. 최신 dump 생성

운영 dump에는 이메일, 현장 기록, 채팅 등 개인정보와 사용자 생성 데이터가 포함될 수 있으므로 Git에 커밋하지 않는다. 제출용 dump가 필요하면 승인된 운영자가 안전한 디렉터리에 생성하고 별도 보안 채널로 전달한다.

저장소에는 덮어쓰기 방지, 권한 600, SHA-256 생성이 포함된 스크립트가 있다.

```bash
export BACKUP_DIR=/secure/backup/ssabangpalbang
export POSTGRES_HOST=127.0.0.1
export POSTGRES_PORT=5432
export POSTGRES_DB=ssabangpalbang
export POSTGRES_USER='<backup 전용 계정>'
export POSTGRES_PASSWORD='<Secret 저장소에서 주입>'

bash infra/scripts/backup-postgres.sh
unset POSTGRES_PASSWORD
```

산출물:

```text
ssabangpalbang_ssabangpalbang_YYYYMMDDTHHMMSSZ.dump
ssabangpalbang_ssabangpalbang_YYYYMMDDTHHMMSSZ.dump.sha256
```

custom format 전체 dump이며 owner와 ACL은 제외된다. 제출본에는 운영 계정 비밀번호를 포함하지 않는다.

### 개인정보 없는 제출용 포터블 dump

현재 제출본은 `ssabangpalbang_schema_v34.sql`이다. 이후 Migration이 추가되면 운영 DB를 덤프하지 말고 새 빈 PostgreSQL 17 DB에 Flyway를 적용한 다음 전체 SQL dump를 생성한다. 그래야 Flyway 이력이 보존된다.

```bash
export PGPASSWORD='<DB 비밀번호>'
pg_dump \
  --no-owner \
  --no-acl \
  --host=127.0.0.1 \
  --port=5432 \
  --username='<DB 계정>' \
  --dbname=ssabangpalbang \
  --file=exec/database/ssabangpalbang_schema_v34.sql
unset PGPASSWORD
```

덤프 후 다음을 확인한다.

```bash
test -s exec/database/ssabangpalbang_schema_v34.sql
sha256sum exec/database/ssabangpalbang_schema_v34.sql \
  > exec/database/ssabangpalbang_schema_v34.sql.sha256
```

애플리케이션 데이터가 섞이지 않았는지 확인한다. 결과는 `flyway_schema_history`만 나와야 한다.

```sql
SELECT schemaname, relname, n_live_tup
FROM pg_stat_user_tables
WHERE n_live_tup > 0
ORDER BY schemaname, relname;
```

## 4. 빈 DB 재현

가장 권장하는 방법은 dump 대신 Flyway를 사용하는 것이다.

```bash
docker compose \
  --env-file infra/.env.local \
  -f infra/docker-compose.local.yml \
  up -d postgres

cd backend
SPRING_PROFILES_ACTIVE=local ./gradlew bootRun
```

Flyway가 성공한 뒤 애플리케이션을 종료해도 PostgreSQL volume은 유지된다.

## 5. dump 복원

운영 DB 또는 `postgres-prod-data`에 직접 복원하지 않는다. 새 DB 이름, 다른 포트, 별도 Volume의 격리 PostgreSQL에서 먼저 검증한다.

```bash
export BACKUP_FILE=/secure/backup/ssabangpalbang/<파일명>.dump
export ISOLATED_RESTORE_HOST=127.0.0.1
export ISOLATED_RESTORE_PORT=5433
export ISOLATED_RESTORE_DB=ssabangpalbang_restore_verify
export ISOLATED_RESTORE_USER='<복원 전용 계정>'
export ISOLATED_RESTORE_PASSWORD='<Secret>'
export ISOLATED_RESTORE_VOLUME=postgres-restore-verify-data
export PRODUCTION_POSTGRES_DB=ssabangpalbang
export PRODUCTION_POSTGRES_VOLUME=postgres-prod-data
export CONFIRM_ISOLATED_RESTORE=true
export CONFIRM_APPLICATION_DISCONNECTED=true
export CONFIRM_SEPARATE_VOLUME=true

bash infra/scripts/restore-postgres-isolated.sh
unset ISOLATED_RESTORE_PASSWORD
```

복원 스크립트는 checksum을 먼저 확인하고, 이미 존재하는 DB에는 덮어쓰지 않는다.

포함된 SQL을 빈 DB에 직접 적용할 때는 PostgreSQL 17과 필수 확장을 설치할 수 있는 권한을 준비한 뒤 실행한다.

```bash
sha256sum --check exec/database/ssabangpalbang_schema_v34.sql.sha256
psql \
  --host=127.0.0.1 \
  --port=5433 \
  --username='<복원 전용 계정>' \
  --dbname='<새 빈 DB>' \
  --set=ON_ERROR_STOP=1 \
  --file=exec/database/ssabangpalbang_schema_v34.sql
```

## 6. DB 접속정보 관리

| 환경 | 파일·주입 위치 |
|---|---|
| 로컬 | `infra/.env.local` |
| 운영 | `/run/secrets/ssabangpalbang.env` |
| Jenkins | root-owned 환경파일 read mount 및 Jenkins Credential |
| AI 조회 계정 | `AI_REPORT_DB_USER`, `AI_REPORT_DB_PASSWORD` 전용 계정 |

운영 `POSTGRES_USER`를 AI 조회 계정으로 재사용하지 않는다. 실제 접속정보와 dump는 Git, MR, Jira, 채팅, 발표자료에 넣지 않는다.
