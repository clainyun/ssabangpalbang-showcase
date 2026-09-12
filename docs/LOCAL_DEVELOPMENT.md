# 싸방팔방 로컬 개발 환경

팀원이 저장소를 받은 뒤 백엔드와 로컬 인프라를 실행하는 방법을 정리한 문서입니다.

## 개발 환경

| 구분 | 버전 및 기준 |
|---|---|
| Java | 17 |
| Spring Boot | 3.5.16 |
| Gradle Wrapper | 8.14.3 |
| PostgreSQL | 17.10 |
| Redis | 7.4.9 |
| Kafka | 4.3.1 |
| 인코딩 | UTF-8 |
| 줄바꿈 | LF |
| 시간대 | Asia/Seoul |

Gradle은 프로젝트에 포함된 Wrapper를 사용합니다.

- Mac/Linux: `./gradlew`
- Windows: `gradlew.bat`

## 프로젝트 구조

- `frontend`: Android 애플리케이션 (React Native + Expo SDK 57)
- `backend`: Spring Boot 백엔드
- `ai`: AI 관련 코드
- `infra`: Docker 및 인프라 설정
- `docs`: 프로젝트 문서

백엔드는 하나의 Spring Boot 애플리케이션으로 구성했습니다.

## 환경변수 파일

개인별 환경변수는 `infra/.env.local`에서 관리합니다.

처음 저장소를 받은 뒤 예시 파일을 복사합니다.

Mac/Linux:

    cp infra/.env.example infra/.env.local

Windows PowerShell:

    Copy-Item infra\.env.example infra\.env.local

`infra/.env.local`은 Git에 올라가지 않습니다.

## FCM 테스트 알림 설정

FCM 테스트 발송 API는 기본적으로 비활성화되어 있어, Firebase 서비스 계정 파일이 없어도
백엔드는 정상 기동합니다. 실제 FCM 발송을 확인할 개발 환경에서만 Firebase 콘솔에서 발급한
서비스 계정 JSON을 저장소 밖의 안전한 경로에 보관한 뒤 `infra/.env.local`에 아래 두 값을
설정합니다. JSON 내용이나 실제 경로를 저장소, 채팅, 로그에 넣지 않습니다.

    FCM_ENABLED=true
    FCM_SERVICE_ACCOUNT_PATH=/absolute/path/outside-the-repository/firebase-service-account.json

Windows에서는 두 번째 값에 서비스 계정 JSON의 절대 경로를 입력합니다. 파일은 `.gitignore`로
보호되지만, 저장소 내부에 저장하지 않는 것을 원칙으로 합니다.

Jenkins 운영 배포에서 FCM을 활성화하려면 서비스 계정 JSON 전체를 Secret Text credential
`ssabangpalbang-firebase-service-account-json`에 등록하고 운영 환경 파일에는
`FCM_ENABLED=true`만 설정합니다. Compose는 Jenkins가 바인딩한 값을 컨테이너의
`/run/secrets/firebase-service-account.json`에 읽기 전용 secret으로 연결합니다.
운영 호스트 파일 경로나 JSON 원문은 환경 파일, 저장소, 채팅 또는 로그에 넣지 않습니다.

마이페이지의 알림 버튼은 Android 알림 권한을 확인하고 현재 FCM 토큰의 서버 등록이 완료된
뒤 테스트 발송 API를 호출합니다. 서버는 Firebase dry-run 검증 후 요청을 `202 Accepted`로
수락하고 `scheduledAt`을 반환하며, 약 5초 뒤 해당 회원·기기의 최신 토큰을 다시 조회해
발송합니다. 앱이
foreground일 때도 `ssabangpalbang-alerts` HIGH 채널로 배너와 알림 목록에 표시합니다.

로컬 확인 순서는 다음과 같습니다.

1. Android 개발 빌드와 서비스 계정의 Firebase `project_id`가 같은지 값 노출 없이 확인합니다.
2. 백엔드 환경에 `FCM_ENABLED=true`와 저장소 밖 서비스 계정 절대 경로를 설정합니다.
3. 로그인한 Android 개발 빌드에서 알림 버튼을 누릅니다.
4. 예약 성공 안내 뒤 약 5초 후 foreground, background, 종료 상태에서 알림을 확인합니다.

서비스 계정 파일 생성·다운로드, 운영 호스트 배치와 실제 운영 배포는 배포 담당자의 별도 승인
후 수행합니다.

백엔드 인증 기능을 실행하려면 `JWT_SECRET`을 32바이트 이상의 Base64 키로 교체해야 합니다. 예시 파일의 placeholder를 그대로 사용하면 애플리케이션이 시작되지 않습니다.

Mac/Linux:

    openssl rand -base64 32

Windows PowerShell:

    $bytes = New-Object byte[] 32
    $rng = [System.Security.Cryptography.RandomNumberGenerator]::Create()
    $rng.GetBytes($bytes)
    $rng.Dispose()
    [Convert]::ToBase64String($bytes)

출력된 값을 `infra/.env.local`의 `JWT_SECRET`에 설정합니다. 실제 키는 Git에 커밋하거나 팀 채팅·MR·로그에 노출하지 않습니다.

카카오·네이버 간편 로그인을 직접 확인하려면 각 개발자 콘솔에 등록한 애플리케이션 값도 설정합니다.

    KAKAO_REST_API_KEY=카카오_REST_API_키
    KAKAO_CLIENT_SECRET=카카오_Client_Secret_사용_시에만_설정
    NAVER_CLIENT_ID=네이버_Client_ID
    NAVER_CLIENT_SECRET=네이버_Client_Secret

`KAKAO_CLIENT_SECRET`은 카카오 개발자 콘솔에서 Client Secret 기능을 활성화한 경우에만 필요합니다. 이 값들이 비어 있어도 백엔드는 기동하지만 해당 소셜 로그인 요청은 `503 AUTH_SOCIAL_PROVIDER_UNAVAILABLE`로 응답합니다. 실제 값과 소셜 인가 코드·Access Token은 Git, 팀 채팅, MR, 애플리케이션 로그에 남기지 않습니다.

홈의 기상청 공식 특보를 직접 확인하려면 공공데이터포털에서 `기상청_기상특보 조회서비스` 활용신청 후 발급된 일반 인증키를 설정합니다. 아파트 데이터 적재용 키를 덮어쓰지 않도록 별도 환경변수를 사용합니다.

    KMA_WEATHER_ALERT_SERVICE_KEY=기상청_기상특보_일반_인증키

현재 위치 좌표를 서울 특보구역으로 변환할 때는 위의 `KAKAO_REST_API_KEY`도 사용합니다. 해당 카카오 앱에서 카카오맵 API 사용을 먼저 활성화해야 하며, 비활성 상태에서는 `OPEN_MAP_AND_LOCAL` 권한 오류가 발생합니다. 키가 없거나 외부 API를 사용할 수 없어도 홈 API는 실패하지 않으며 `weatherAlerts.available=false`, `freshness=UNAVAILABLE`을 반환합니다. 실제 키는 Git, 팀 채팅, MR, 애플리케이션 로그에 남기지 않습니다.

PostgreSQL이나 Redis 기본 포트를 이미 사용 중이라면 개인 환경변수 파일에서 포트만 변경합니다.

예시:

    POSTGRES_PORT=5433
    REDIS_PORT=6380

## Docker 인프라 실행

프로젝트 최상위 폴더에서 실행합니다.

실행:

    docker compose --env-file infra/.env.local -f infra/docker-compose.local.yml up -d

상태 확인:

    docker compose --env-file infra/.env.local -f infra/docker-compose.local.yml ps

PostgreSQL, Redis, Kafka가 모두 `healthy`이면 정상입니다.

종료:

    docker compose --env-file infra/.env.local -f infra/docker-compose.local.yml down

`down -v`를 사용하면 PostgreSQL과 Redis의 로컬 데이터까지 삭제되므로 일반적인 종료에서는 사용하지 않습니다.

## 챗봇(RAG) 로컬 설정

Docker Compose로 AI를 실행할 때 사용하는 환경파일은 `infra/.env.local` 하나입니다.
`ai/.env.local`은 Docker 이미지에 포함되지 않으므로 컨테이너에서는 읽히지 않습니다.

챗봇을 사용할 개발자는 개인 `infra/.env.local`에 다음 값을 입력합니다. 실제 API 키는
각자 발급받아 입력하고 Git, MR, 채팅, 로그에 남기지 않습니다.

```dotenv
GMS_KEY=각자_발급받은_실제_키
AI_PROVIDER=gms-gemini
AI_API_BASE_URL=https://ai-gateway.example.com/gmsapi/generativelanguage.googleapis.com
AI_API_KEY=${GMS_KEY}
AI_MODEL=gemini-3.5-flash
RAG_ENABLED=true
```

실제 키 문자열은 `GMS_KEY` 한 곳에만 저장합니다. 기존 운영 경로가 읽는 `AI_API_KEY`는
같은 파일의 `GMS_KEY`를 참조하므로 키를 다시 복사하지 않습니다.

### GMS 3모델 비교 설정

Gemini 3.5 Flash, GPT-5.4 mini, Claude Sonnet 4.6을 비교할 때도 실제 비밀값은
`GMS_KEY` 하나만 사용합니다. 실행 방식에 따라 다음 중 한 파일에만 저장합니다.

- AI를 로컬 Python으로 직접 실행하면 `ai/.env.example`을 `ai/.env.local`로 복사하고
  그 파일의 `GMS_KEY`에만 실제 값을 넣습니다.
- Docker Compose로 AI를 실행하면 `infra/.env.example`을 `infra/.env.local`로 복사하고
  그 파일의 `GMS_KEY`에만 실제 값을 넣습니다. `ai/.env.local`은 컨테이너에 전달되지 않습니다.

모델명과 endpoint는 비밀값이 아닙니다. Gemini 전체 endpoint는 예시 파일에 설정되어 있습니다.
GPT와 Claude의 URL은 제공된 curl 예시에서 가려져 있으므로 코드가 추정하지 않습니다.
GMS 문서에 표시된 따옴표 안의 **전체 요청 URL**을 개인 환경파일의 아래 두 항목에 그대로
복사합니다. 안전을 위해 `https://ai-gateway.example.com` 주소만 허용하며, URL 끝에
`/chat/completions`나 `/v1/messages`를 다시 붙이지 않습니다.

```dotenv
GMS_OPENAI_ENDPOINT_URL=GMS_문서의_GPT_전체_URL
GMS_ANTHROPIC_ENDPOINT_URL=GMS_문서의_Claude_전체_URL
```

나머지 비교 설정은 예시 파일의 기본값을 사용합니다.

```dotenv
GMS_GEMINI_MODEL=gemini-3.5-flash
GMS_OPENAI_MODEL=gpt-5.4-mini
GMS_ANTHROPIC_MODEL=claude-sonnet-4-6
GMS_ANTHROPIC_VERSION=2023-06-01
GMS_ANTHROPIC_MAX_TOKENS=4096
```

`create_gms_model_suite()`는 평가 코드가 세 공급자를 명시적으로 만들 때만 사용합니다.
함수를 호출해 registry를 만드는 것만으로 모델 요청이 발생하지 않으며, 평가 코드가 각 entry의
`complete_json()`을 호출해야 실제 요청이 실행됩니다. 기존 챗봇·체크리스트·리포트 트래픽은
계속 `AI_PROVIDER`가 선택한 공급자 하나만 호출합니다. 세 비교 모델이 모든 운영 요청에서
자동으로 동시에 호출되지는 않습니다.

RAG DB 접속값은 기존 `POSTGRES_DB`, `POSTGRES_USER`, `POSTGRES_PASSWORD`에서 자동으로
전달되므로 따로 입력하지 않습니다. `RAG_ENABLED`에 허용되는 값은
`1 / true / yes / on / 0 / false / no / off`뿐입니다.

PostgreSQL을 유지한 채 AI 컨테이너만 재생성합니다.

Mac/Linux:

    docker compose --env-file infra/.env.local \
      -f infra/docker-compose.local.yml \
      up -d --build --force-recreate --no-deps ai

Windows PowerShell:

    docker compose --env-file infra/.env.local `
      -f infra/docker-compose.local.yml `
      up -d --build --force-recreate --no-deps ai

적용 후 다음 명령의 결과가 `true`인지 확인합니다.

    docker compose --env-file infra/.env.local \
      -f infra/docker-compose.local.yml \
      exec ai printenv RAG_ENABLED

이 절차는 AI 컨테이너만 재생성하므로 PostgreSQL 컨테이너와 `postgres-data` 볼륨을
유지합니다. 임베딩 모델도 `ai-model-cache` 볼륨의 `/models`에 남아 다시 받지 않습니다.
`down -v`와 `docker volume rm`은 이 절차에 사용하지 않습니다.

기존 `FAILED` 메시지는 자동 복구되지 않으므로 반드시 새 질문으로 검증합니다. 색인 자료가
없고 웹검색이 꺼져 있으면 오류가 아니라
`"신뢰할 수 있는 자료를 찾지 못해 답변드리기 어렵습니다."`라는 NONE 응답이 정상입니다.

### RAG 색인은 별도 단계다

`RAG_ENABLED=true`는 기능 스위치일 뿐 데이터를 만들지 않습니다. 정보성 답변에는
`apartment_rag_document`에 해당 아파트 문서가 색인돼 있어야 합니다.

색인 여부를 확인합니다.

    docker compose --env-file infra/.env.local \
      -f infra/docker-compose.local.yml \
      exec postgres psql -U "$POSTGRES_USER" -d "$POSTGRES_DB" \
      -c "SELECT source_type, count(*) FROM apartment_rag_document GROUP BY source_type;"

색인 명령은 AI 컨테이너 안에서 실행하며 `--all`이나 `--apartment-ids` 중 하나가 필수입니다.
먼저 `--dry-run`으로 대상 건수를 확인합니다. 이 명령은 DB를 변경하지 않습니다.

    docker compose --env-file infra/.env.local \
      -f infra/docker-compose.local.yml \
      exec ai python -m app.rag.index_public --all --dry-run

    docker compose --env-file infra/.env.local \
      -f infra/docker-compose.local.yml \
      exec ai python -m app.rag.index_public --all

    docker compose --env-file infra/.env.local \
      -f infra/docker-compose.local.yml \
      exec ai python -m app.rag.index_report --all

`index_public`을 먼저 실행하면 리포트가 없어도 공공데이터 문서가 생성됩니다.
`index_report`는 `status=DONE`인 리포트만 대상으로 합니다. 색인 CLI는
`RAG_ENABLED`와 무관하게 동작하며, 필요한 `RAG_DB_*`는 Local Compose가 이미 전달합니다.
자동 색인은 이번 작업에서 만들지 않으며 필요하면 별도 과제로 분리합니다.

## Spring Boot 실행

Mac/Linux:

    cd backend
    set -a
    source ../infra/.env.local
    set +a
    export SPRING_PROFILES_ACTIVE=local
    ./gradlew bootRun

Windows PowerShell:

    cd backend
    Get-Content ..\infra\.env.local | ForEach-Object {
        if ($_ -match '^\s*([^#][^=]*)=(.*)$') {
            [Environment]::SetEnvironmentVariable(
                $matches[1].Trim(),
                $matches[2].Trim(),
                'Process'
            )
        }
    }
    $env:SPRING_PROFILES_ACTIVE = 'local'
    .\gradlew.bat bootRun

기본 서버 포트는 8080입니다.

상태 확인 주소:

    http://localhost:8080/actuator/health

Swagger UI:

    http://localhost:8080/swagger-ui/index.html

## 테스트

기본 테스트는 H2와 test Profile을 사용하기 때문에 Docker가 없어도 실행할 수 있습니다.

Mac/Linux:

    cd backend
    ./gradlew clean test

Windows:

    cd backend
    .\gradlew.bat clean test

실제 PostgreSQL, Redis, Kafka 연결 테스트는 Docker 인프라를 실행한 상태에서 진행합니다.

Mac/Linux:

    cd backend
    set -a
    source ../infra/.env.local
    set +a
    RUN_LOCAL_INFRA_TESTS=true ./gradlew test --tests "com.ssafy.ssabangpalbang.integration.LocalInfrastructureConnectionTest"

해당 테스트에서는 PostgreSQL 쿼리 실행, Redis 저장·조회·삭제, Kafka Broker 연결을 확인합니다.

## 실행 파일 빌드

Mac/Linux:

    cd backend
    ./gradlew clean bootJar

Windows:

    cd backend
    .\gradlew.bat clean bootJar

생성 위치:

    backend/build/libs/ssabangpalbang-0.0.1-SNAPSHOT.jar

## Profile 구분

| Profile | 용도 | DB | Flyway |
|---|---|---|---|
| local | 로컬 개발 | Docker PostgreSQL | 사용 |
| test | 자동 테스트 | H2 | 사용하지 않음 |
| prod | 운영 배포 | 운영 환경변수 | 배포 단계에서 구성 |

## 데이터베이스 변경 규칙

초기 ERD는 아래 Flyway 파일로 관리합니다.

    backend/src/main/resources/db/migration/V1__init_schema.sql

V1이 공유된 이후에는 기존 파일을 직접 수정하지 않습니다.

DB 변경이 필요하면 다음 버전의 Migration 파일을 추가합니다.

    V2__add_example_table.sql
    V3__alter_example_column.sql

Hibernate의 자동 스키마 생성은 사용하지 않고 DB 구조는 Flyway를 기준으로 관리합니다.

## 공통 응답과 예외 처리

API 응답과 오류 형식을 맞추기 위해 아래 공통 구조를 사용합니다.

- `global/response/ApiResponse`
- `global/error/ErrorCode`
- `global/error/ErrorResponse`
- `global/error/BusinessException`
- `global/error/GlobalExceptionHandler`

기능별 예외는 `BusinessException`과 `ErrorCode`를 사용합니다.

## 초기 세팅 검증 결과

초기 세팅 과정에서 다음 내용을 확인했습니다.

- PostgreSQL, Redis, Kafka 컨테이너 Health Check
- Spring Boot local Profile 실행
- 실행 JAR 빌드
- PostgreSQL, Redis, Kafka 통합 연결 테스트
- Flyway V1 Migration 적용
- 서비스 테이블 33개 생성
- pgvector, PostGIS, pgcrypto, pg_trgm 확장 적용
- Vector 컬럼 생성
- PostGIS GIST 인덱스 생성
- HOT 게시글 View 생성
