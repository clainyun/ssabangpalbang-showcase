# 싸방팔방 포팅 매뉴얼

> 작성 기준: 2026-08-10 · 원격 Git `origin/develop` · commit `d9c6de874fc5ed9310c9618b2550b62702d678af`  
> 프로젝트명: 싸방팔방(아파트 임장 스터디·현장 기록·AI 리포트 Android 앱)

이 문서는 GitLab에서 프로젝트를 clone한 뒤 로컬에서 빌드·실행하거나 Ubuntu EC2에 운영 배포하기 위한 절차를 정리한다. 실제 비밀번호, 토큰, 인증서 개인키, Firebase 서비스 계정 JSON은 이 폴더와 Git에 포함하지 않는다.

## 0. 제출 파일 구성

```text
exec/
├─ README.md                 # 포팅 매뉴얼 본문
├─ DEMO_SCENARIO.md          # 최종 발표 시연 순서
└─ database/
   ├─ README.md              # 최신 DB 구성·덤프·복원 절차
   ├─ ssabangpalbang_schema_v34.sql
   └─ ssabangpalbang_schema_v34.sql.sha256
```

설계 산출물은 저장소 루트에 함께 둔다.

```text
Architecture.png             # 시스템 아키텍처 다이어그램
ERD/
├─ drawsql-1-member-community.jpg
├─ drawsql-2-apartment-chatbot.jpg
├─ drawsql-3-study-chat.jpg
├─ drawsql-4-field-visit-checklist.jpg
└─ drawsql-5-file-report-stt.jpg
```

ERD 이미지는 도메인별로 5장으로 나뉘어 있으며, 테이블·컬럼 정의의 정본은 `docs/ERD.md`와
`backend/src/main/resources/db/migration/`이다.

소스코드는 저장소의 `frontend/`, `backend/`, `ai/`, `infra/`를 그대로 사용한다.

## 1. 시스템 구성

```text
Android 앱
  ├─ HTTPS /api/* ───────────────┐
  └─ WSS /ws ────────────────────┤
                                  ▼
Internet → Nginx(80/443, TLS) → Spring Boot(8080)
                                      ├─ PostgreSQL 17
                                      ├─ Redis 7.4.9
                                      ├─ Kafka 4.3.1
                                      ├─ FastAPI AI(8000, Docker 내부)
                                      └─ 외부 인증·지도·미디어·AI 서비스
```

운영 환경에서 외부에 공개하는 포트는 80과 443뿐이다. Spring Boot는 호스트 `127.0.0.1:8080`에만 바인딩되고 FastAPI, PostgreSQL, Redis, Kafka는 Docker 내부 네트워크에서만 접근한다.

### 1.1 기술 스택과 버전

| 영역 | 제품·버전 | 설정 근거 |
|---|---|---|
| Android | Android SDK Platform 36, Build-Tools 36.0.0 | `frontend/README.md` |
| Frontend | Node.js 22.13.0 이상, Expo SDK 57.0.11, React Native 0.86.2, React 19.2.3, TypeScript 6.0.3 | `frontend/package.json` |
| Backend JVM | Java 17 | `backend/build.gradle`, `backend/Dockerfile` |
| Backend | Spring Boot 3.5.16, Gradle Wrapper 8.14.3 | `backend/build.gradle`, Wrapper |
| WAS | Spring Boot 내장 Servlet Container, 포트 8080 | 별도 외장 WAS 없음 |
| AI | Python 3.12, FastAPI 0.115 이상, Uvicorn 0.32 이상 | `ai/Dockerfile`, `ai/requirements.txt` |
| DB | PostgreSQL 17 + pgvector 0.8.5 + PostGIS 3 | `infra/postgres/Dockerfile` |
| Cache | Redis 7.4.9 Alpine | Compose |
| Message broker | Apache Kafka 4.3.1, KRaft 단일 노드 | Compose |
| Reverse proxy | Nginx, Let's Encrypt Certbot | EC2 호스트 패키지 |
| Container | Docker Engine, Docker Compose v2 | `docker compose` 명령 사용 |
| CI/CD | GitLab Webhook + Jenkins Pipeline | `Jenkinsfile`, `Jenkinsfile.verify` |
| 공통 기준 | UTF-8, LF, `Asia/Seoul` | 애플리케이션·Compose 설정 |

Android application ID는 `com.ssafy.ssabangpalbang`, 딥링크 scheme은 `ssabangpalbang`, Expo EAS project ID는 `e93e1e1c-53c8-4005-a7f3-a881fd1efad5`다.

Nginx, Docker Engine, Git, IntelliJ IDEA는 특정 패치 버전에 고정되어 있지 않다. Ubuntu 배포 시 배포 시점의 지원 안정 버전을 사용한다.

## 2. 저장소 clone 후 로컬 실행

### 2.1 사전 준비

- Git
- JDK 17
- Node.js 22.13.0 이상
- Docker Engine 및 Docker Compose v2
- Android SDK Platform 36, Build-Tools 36.0.0, Platform-Tools, Emulator
- Android 에뮬레이터 또는 USB 디버깅을 허용한 실제 Android 기기

확인:

```bash
git --version
java -version
node -v
npm -v
docker version
docker compose version
adb devices
```

### 2.2 clone

```bash
git clone https://lab.ssafy.com/s15-webmobile4-sub1/S15P11A701.git
cd S15P11A701
git fetch origin develop
git switch develop
git pull --ff-only origin develop
git rev-parse HEAD
```

이 매뉴얼과 동봉 DB는 `origin/develop`의 `d9c6de874fc5ed9310c9618b2550b62702d678af`를 기준으로 검증했다. 동일 결과를 재현하려면 마지막 출력이 이 SHA인지 확인한다. 이후 develop이 갱신됐다면 팀에서 지정한 제출·시연 commit을 우선한다.

### 2.3 로컬 환경변수

Linux/macOS:

```bash
cp infra/.env.example infra/.env.local
```

Windows PowerShell:

```powershell
Copy-Item infra\.env.example infra\.env.local
```

다음 값은 최소 기동에 필요하다.

| 변수 | 설명 | 로컬 예시·주의 |
|---|---|---|
| `POSTGRES_HOST` | PostgreSQL 호스트 | Backend 직접 실행 시 `localhost` |
| `POSTGRES_DB` | DB 이름 | `ssabangpalbang` |
| `POSTGRES_USER` | DB 계정 | 로컬 전용 계정 |
| `POSTGRES_PASSWORD` | DB 비밀번호 | 예시값 변경 권장 |
| `POSTGRES_PORT` | 호스트 공개 포트 | 기본 `5432` |
| `REDIS_HOST`, `REDIS_PORT` | Redis 접속 | 기본 `localhost:6379` |
| `REDIS_PASSWORD` | Redis 비밀번호 | Compose와 Backend에서 같은 값 |
| `KAFKA_BOOTSTRAP_SERVERS` | Backend Kafka 주소 | `localhost:9092` |
| `KAFKA_PORT` | Kafka 호스트 포트 | 기본 `9092` |
| `KAFKA_CLUSTER_ID` | KRaft cluster ID | 예시 파일 값 사용 가능 |
| `JWT_SECRET` | JWT 서명키 | 32바이트 이상 Base64, 반드시 직접 생성 |

JWT 키 생성:

```bash
openssl rand -base64 32
```

Windows PowerShell:

```powershell
$bytes = New-Object byte[] 32
$rng = [System.Security.Cryptography.RandomNumberGenerator]::Create()
$rng.GetBytes($bytes)
$rng.Dispose()
[Convert]::ToBase64String($bytes)
```

출력값을 `infra/.env.local`의 `JWT_SECRET`에 넣는다. 출력값을 Git이나 채팅에 남기지 않는다.

### 2.4 PostgreSQL·Redis·Kafka·AI 실행

저장소 루트에서 실행한다.

```bash
docker compose \
  --env-file infra/.env.local \
  -f infra/docker-compose.local.yml \
  up -d --build
```

상태 확인:

```bash
docker compose \
  --env-file infra/.env.local \
  -f infra/docker-compose.local.yml \
  ps
```

`postgres`, `redis`, `kafka`, `ai`가 `healthy`여야 한다. AI는 첫 모델 준비 시 시간이 더 걸릴 수 있다. `kafka-report-topic-init`은 리포트용 Kafka Topic을 만든 뒤 종료되는 one-shot 서비스이므로 `Exited (0)`이면 정상이다.

종료:

```bash
docker compose \
  --env-file infra/.env.local \
  -f infra/docker-compose.local.yml \
  down
```

`down -v`는 PostgreSQL·Redis·Kafka·AI 모델 캐시 볼륨까지 지우므로 일반 종료나 시연 직전에는 사용하지 않는다.

### 2.5 Backend 실행

Linux/macOS:

```bash
cd backend
set -a
source ../infra/.env.local
set +a
export SPRING_PROFILES_ACTIVE=local
./gradlew bootRun
```

Windows PowerShell:

```powershell
Set-Location backend
Get-Content ..\infra\.env.local | ForEach-Object {
    if ($_ -match '^\s*([^#][^=]*)=(.*)$') {
        [Environment]::SetEnvironmentVariable(
            $matches[1].Trim(), $matches[2].Trim(), 'Process'
        )
    }
}
$env:SPRING_PROFILES_ACTIVE = 'local'
.\gradlew.bat bootRun
```

Spring Boot가 시작될 때 Flyway가 `V1`부터 최신 `V34`까지 자동 적용한다. Hibernate `ddl-auto`는 `none`이므로 Flyway 실패를 무시하고 실행하지 않는다.

확인:

```text
Health       http://localhost:8080/actuator/health
Swagger UI   http://localhost:8080/swagger-ui/index.html
AI Health    http://localhost:8000/health
```

### 2.6 Frontend 환경변수

```bash
cd frontend
npm ci
```

Linux/macOS:

```bash
cp .env.example .env.local
```

Windows PowerShell:

```powershell
Copy-Item .env.example .env.local
```

Android Emulator에서 Backend로 연결할 때:

```dotenv
EXPO_PUBLIC_API_BASE_URL=http://10.0.2.2:8080
EXPO_PUBLIC_WS_URL=ws://10.0.2.2:8080/ws
EXPO_PUBLIC_MAPBOX_TOKEN=pk.<Mapbox에서 발급한 제한된 Public Token>
EXPO_PUBLIC_KAKAO_APP_KEY=<카카오 Native App Key>
EXPO_PUBLIC_KAKAO_REST_API_KEY=<카카오 REST API Key>
EXPO_PUBLIC_NAVER_CLIENT_ID=<네이버 Client ID>
EXPO_PUBLIC_DEMO_MODE_ENABLED=false
```

실제 기기는 `10.0.2.2` 대신 개발 PC의 동일 LAN IP를 사용한다. `EXPO_PUBLIC_*` 값은 앱 번들에 포함되므로 서버 비밀번호나 Secret을 넣으면 안 된다.

FCM을 사용할 때만 Firebase Android 설정 파일을 안전한 경로에 두고 다음 경로 변수를 설정한다.

```dotenv
GOOGLE_SERVICES_JSON=./app/google-services.json
```

로컬 개발에서는 원본 파일을 `frontend/app/google-services.json`에 두므로 위 경로를 사용한다. `frontend/.env.production.example`의 `./google-services.json`은 release/EAS 빌드 환경에서 빌드 러너가 주입한 파일을 `frontend` 프로젝트 루트 기준으로 참조하는 예시다. EAS Secret File을 사용하면 실제 주입된 파일 경로를 `GOOGLE_SERVICES_JSON`으로 전달하며, 두 환경의 경로를 임의로 같게 만들 필요는 없다.

### 2.7 Android 개발 빌드

이 앱은 Mapbox와 카카오 로그인 등 네이티브 모듈을 사용하므로 Expo Go가 아니라 Development Build가 필요하다.

```bash
cd frontend
npm run prebuild
npx expo run:android
```

Windows에서 에뮬레이터 ABI만 빌드해 시간을 단축하려면:

```powershell
$env:EXPO_DEV_ABI = 'x86_64'
npm run prebuild
npx expo run:android
```

JS/TS만 변경한 이후에는 네이티브 재빌드 대신 다음 명령을 사용한다.

```bash
npm start
```

## 3. 빌드 및 테스트

### 3.1 Backend

```bash
cd backend
./gradlew clean test bootJar
```

Windows:

```powershell
cd backend
.\gradlew.bat clean test bootJar
```

JAR 출력:

```text
backend/build/libs/ssabangpalbang-0.0.1-SNAPSHOT.jar
```

실제 PostgreSQL Testcontainers 테스트는 Docker가 켜진 환경에서 별도로 실행한다.

```bash
./gradlew postgresTest
```

### 3.2 AI

Python 3.12 환경에서:

```bash
cd ai
python -m venv .venv
source .venv/bin/activate
pip install --extra-index-url https://download.pytorch.org/whl/cpu -r requirements.txt
python -m pytest -q
```

Windows의 활성화 명령은 `.venv\Scripts\Activate.ps1`이다. 운영과 동일한 이미지를 검증하려면 다음을 사용한다.

```bash
docker build -t ssabangpalbang-ai:verify ai
```

### 3.3 Frontend

```bash
cd frontend
npm ci
npm run lint
npm run typecheck
npm test
npx expo export --platform android
```

### 3.4 Android APK/AAB

EAS CLI 인증과 프로젝트 연결을 완료한 뒤 실행한다.

```bash
cd frontend
npx eas-cli build --platform android --profile demo       # 내부 시연 APK
npx eas-cli build --platform android --profile preview    # 내부 배포 APK
npx eas-cli build --platform android --profile production # 스토어 AAB
```

`production` 프로필은 HTTPS/WSS URL, FCM 설정 파일, 공개 클라이언트 키가 없거나 `EXPO_PUBLIC_DEMO_MODE_ENABLED`가 `false`가 아니면 빌드를 중단한다. 운영용 서명키와 `google-services.json`은 EAS Credential/Secret으로 관리한다.

## 4. 운영 배포

### 4.1 서버 요구사항

- Ubuntu EC2
- DNS A 레코드가 EC2 Public IP 또는 Elastic IP를 가리킴
- Security Group 인바운드 80/443 허용
- SSH 22는 승인된 IP 범위로 제한
- Docker Engine, Docker Compose v2, Nginx, Certbot 설치
- 운영 환경파일을 복원하는 승인된 Secret 저장소
- Docker socket을 사용할 수 있는 Jenkins Agent

현재 운영 주소는 `https://legacy.example.com`이며 다른 서버로 포팅할 때 다음 항목을 새 도메인으로 바꾼다.

- DNS와 TLS 인증서
- `infra/nginx/*.conf`의 `server_name`
- Jenkinsfile의 `HTTP_BASE_URL`, `HTTPS_BASE_URL`
- Frontend의 `EXPO_PUBLIC_API_BASE_URL`, `EXPO_PUBLIC_WS_URL`
- 카카오·네이버 Callback URL
- Android App Link `assetlinks.json`과 서명 SHA-256

### 4.2 Nginx와 HTTPS 최초 설정

```bash
sudo apt-get update
sudo apt-get install -y nginx snapd util-linux
sudo snap install --classic certbot
sudo ln -s /snap/bin/certbot /usr/local/bin/certbot

sudo install -d -o root -g root -m 0755 /var/www/certbot
sudo install -o root -g root -m 0644 \
  infra/nginx/ssabangpalbang-bootstrap.conf \
  /etc/nginx/conf.d/ssabangpalbang.conf
sudo nginx -t
sudo systemctl enable --now nginx
sudo systemctl reload nginx
```

이후 인증서를 Webroot 방식으로 발급하고 최종 설정 `infra/nginx/ssabangpalbang.conf`를 설치한다. 정확한 인증서 명령, App Link 자산 설치, 갱신 검증은 `docs/INF-006_DEPLOYMENT_RUNBOOK.md`를 따른다. 기존 동일 `server_name` 설정은 임의 삭제하지 않는다.

### 4.3 운영 환경파일

```bash
sudo install -d -o root -g root -m 0750 /run/secrets
sudo install -o root -g jenkins -m 0640 /secure/source/ssabangpalbang.env \
  /run/secrets/ssabangpalbang.env
```

`/run`은 재부팅 시 초기화되므로 실제 서버에서는 승인된 영구 Secret 저장소에서 위 파일을 복원하는 systemd unit을 사용한다. 운영 환경파일을 저장소나 Jenkins Console에 출력하지 않는다.

운영 필수값:

| 변수 | 설명 |
|---|---|
| `JWT_SECRET` | 32바이트 이상 JWT 서명키 |
| `POSTGRES_DB`, `POSTGRES_USER`, `POSTGRES_PASSWORD` | 운영 DB |
| `REDIS_PASSWORD` | Redis 인증 |
| `KAFKA_CLUSTER_ID` | Kafka KRaft cluster ID |
| `MEDIA_GATEWAY_BASE_URL`, `MEDIA_GATEWAY_INTERNAL_TOKEN` | 사진·음성 저장 Gateway를 활성화할 때 필수 |
| `REPORT_INTERNAL_TOKEN` | Backend와 AI Worker 간 동일한 내부 토큰 |
| `AI_API_BASE_URL`, `AI_API_KEY`, `AI_MODEL` | AI 리포트·챗봇 활성화 시 필수 |
| `STT_PROVIDER`, `STT_MODEL` | 운영에서는 각각 `openai`, `whisper-1` 고정 |
| `STT_OPENAI_API_KEY` | STT 전용 OpenAI 키. `AI_API_KEY`·`GMS_KEY`와 공유 금지 |
| `STT_OPENAI_BASE_URL` | 공식 OpenAI API `https://api.openai.com/v1` |

운영 Compose에 주입되는 변수명과 기본값은 `infra/.env.prod.example` 및 `infra/docker-compose.prod.yml`이 정본이다. 로컬 전용 변수와 별도 실행 profile용 변수는 `infra/.env.example` 및 `backend/src/main/resources/application*.yaml`을 함께 확인한다.

### 4.4 Jenkins Credential

| Credential ID | 종류 | 용도 |
|---|---|---|
| `ssabangpalbang-media-gateway-base-url` | Secret Text | AWS Media Gateway URL |
| `ssabangpalbang-media-gateway-internal-token` | Secret Text | Backend·AI·Lambda 내부 인증 |
| `ssabangpalbang-firebase-service-account-json` | Secret Text | FCM 서비스 계정 JSON 전체 |
| GitLab SCM credential | Username/Token 또는 SSH | 저장소 clone, 최소 `read_repository` |
| GitLab API credential | API Token | Commit status 갱신, SCM credential과 분리 |
| GitLab Webhook Secret | Secret Text | Webhook 검증 |

### 4.5 수동 Compose 배포

Jenkins를 사용하지 않을 때 승인된 운영자가 저장소 루트에서 실행한다.

```bash
export BACKEND_IMAGE="ssabangpalbang-backend:$(git rev-parse HEAD)-manual"
unset STT_PROVIDER STT_MODEL STT_OPENAI_API_KEY STT_OPENAI_BASE_URL

docker compose \
  --env-file /run/secrets/ssabangpalbang.env \
  -f infra/docker-compose.prod.yml \
  config --quiet

docker compose \
  --env-file /run/secrets/ssabangpalbang.env \
  -f infra/docker-compose.prod.yml \
  build postgres app ai

docker compose \
  --env-file /run/secrets/ssabangpalbang.env \
  -f infra/docker-compose.prod.yml \
  up -d --no-build
```

실제 OpenAI 전사 검증은 유료 외부 API를 호출하므로 자동 배포 게이트에서 실행하지
않는다. 승인된 운영자가 키·쿼터·외부 통신까지 별도로 확인해야 할 때만 다음 명령을
명시적으로 실행한다.

```bash
unset STT_PROVIDER STT_MODEL STT_OPENAI_API_KEY STT_OPENAI_BASE_URL

docker compose \
  --env-file /run/secrets/ssabangpalbang.env \
  -f infra/docker-compose.prod.yml \
  run --rm --no-deps -T --entrypoint python ai \
  -m app.diagnostics.verify_openai_stt
```

이 수동 검증은 API 사용량이 발생하며 일반 배포 성공 조건에는 포함하지 않는다.

상태와 로그:

```bash
docker compose \
  --env-file /run/secrets/ssabangpalbang.env \
  -f infra/docker-compose.prod.yml \
  ps --all

docker compose \
  --env-file /run/secrets/ssabangpalbang.env \
  -f infra/docker-compose.prod.yml \
  logs --tail=200 app ai
```

환경변수 전체나 인증 Header가 로그에 노출되지 않도록 한다.

### 4.6 Jenkins 배포 흐름

`Jenkinsfile`은 다음을 자동 수행한다.

1. checkout SHA 확정
2. 필수 환경변수와 placeholder 검증
3. Compose 설정 검증
4. PostgreSQL·Backend·AI 이미지 빌드 및 테스트
5. `docker compose up -d --no-build`
6. PostgreSQL·Redis·Kafka·Spring Boot·FastAPI Health 대기
7. 외부 HTTPS/REST/WebSocket Smoke Test
8. 성공한 경우에만 `last-success.env` 갱신
9. 최근 Backend 이미지 5개 보존

AI 이미지 빌드 후에는 네트워크가 차단된 일회성 컨테이너에서 OpenAI STT
설정·Provider·실연동 검증기의 오프라인 단위 테스트를 수행한다. 실제 OpenAI 전사는
외부 장애와 유료 사용량을 배포 가용성에서 분리하기 위해 §4.5의 명시적 수동
검증으로만 수행한다.

`Jenkinsfile.verify`는 `develop` 전용 비배포 검증 Job이다. 운영 배포 Job과 Trigger를 분리한다.

### 4.7 배포 검증

```bash
curl -fsS https://legacy.example.com/actuator/health
bash infra/scripts/prod-smoke-test.sh
```

정상 기준:

- HTTP 요청은 HTTPS로 308 전환
- HTTPS Health가 `UP`
- 무인증 보호 API는 401
- `/ws` WebSocket Handshake는 101
- App Link 공개 자산은 200
- 앱에서 로그인 후 STOMP 연결·구독·메시지 송수신 성공

## 5. 환경변수 상세

### 5.1 기능별 환경변수

| 기능 | 환경변수 | 비고 |
|---|---|---|
| 기본 인증 | `JWT_SECRET` | 필수 |
| 이메일 재설정 | `MAIL_HOST`, `MAIL_PORT`, `MAIL_USERNAME`, `MAIL_PASSWORD` | 미설정 시 메일 기능만 실패 |
| 카카오 로그인·지도 | `KAKAO_REST_API_KEY`, `KAKAO_CLIENT_SECRET` | Client Secret 기능을 켠 경우에만 Secret 필요 |
| 네이버 로그인 | `NAVER_CLIENT_ID`, `NAVER_CLIENT_SECRET` | 둘을 함께 설정 |
| 아파트 공공데이터 | `PUBLIC_DATA_SERVICE_KEY` | 로컬 `dataload`·`dataload-tx` profile 전용. 현재 운영 Compose에는 주입되지 않음 |
| 기상청 특보 | `KMA_WEATHER_ALERT_SERVICE_KEY` | Backend 설정은 지원하지만 현재 운영 Compose에는 주입되지 않아 운영에서는 특보가 unavailable로 처리됨 |
| FCM(로컬 Backend 직접 실행) | `FCM_ENABLED`, `FCM_SERVICE_ACCOUNT_PATH` | 서비스 계정 JSON의 로컬 파일 경로 |
| FCM(운영) | `FCM_ENABLED`, `FCM_SERVICE_ACCOUNT_JSON` | Jenkins Secret Text로 JSON을 주입하며 컨테이너 내부 PATH는 `/run/secrets/firebase-service-account.json`으로 고정 |
| Media | `MEDIA_GATEWAY_ENABLED`, `MEDIA_GATEWAY_BASE_URL`, `MEDIA_GATEWAY_INTERNAL_TOKEN` | S3 직접 키는 앱에 넣지 않음 |
| AI | `AI_PROVIDER`, `AI_API_BASE_URL`, `AI_API_KEY`, `AI_MODEL` | 현재 GMS Gemini 사용 |
| Report Worker | `REPORT_KAFKA_ENABLED`, `REPORT_WORKER_ENABLED`, `REPORT_INTERNAL_TOKEN` | Backend와 AI가 같은 토큰 사용 |
| RAG | `RAG_ENABLED`, `AI_REPORT_DB_USER`, `AI_REPORT_DB_PASSWORD` | 운영 DB 관리자 계정 재사용 금지 |
| STT | `STT_ENABLED`, `STT_PROVIDER`, `STT_MODEL`, `STT_OPENAI_API_KEY`, `STT_OPENAI_BASE_URL`, Kafka·Redis·Media 변수 | 운영은 `openai`/`whisper-1`/공식 OpenAI URL만 허용한다. 자동 배포는 설정·오프라인 테스트를 검증하고 실제 전사는 승인된 수동 점검에서만 수행 |
| Web 검색 | `WEB_SEARCH_PROVIDER`, `WEB_SEARCH_API_KEY`, `WEB_SEARCH_BASE_URL` | 기본 `none` |

### 5.2 설정 파일 목록

| 파일 | 역할 | Git 포함 |
|---|---|---|
| `infra/.env.example` | 로컬 환경변수 템플릿 | Yes |
| `infra/.env.local` | 개발자의 실제 로컬 값 | No |
| `infra/.env.prod.example` | 운영 변수 템플릿 | Yes |
| `/run/secrets/ssabangpalbang.env` | 운영 실제 값 | No |
| `frontend/.env.example` | Android 개발 공개 설정 | Yes |
| `frontend/.env.local` | 개발 앱 설정 | No |
| `frontend/.env.production.example` | EAS 운영 설정 템플릿 | Yes |
| `frontend/app.config.ts` | Android package·권한·딥링크·빌드 검증 | Yes |
| `frontend/eas.json` | APK/AAB 빌드 프로필 | Yes |
| `backend/src/main/resources/application*.yaml` | Spring profile 설정 | Yes |
| `ai/.env.example` | FastAPI 단독 실행 템플릿 | Yes |
| `infra/docker-compose.local.yml` | 로컬 인프라·AI | Yes |
| `infra/docker-compose.prod.yml` | 운영 전체 스택 | Yes |

## 6. 외부 서비스

실제 키·비밀번호는 담당자가 각 콘솔에서 발급하고 Secret 저장소에 주입한다.

| 서비스 | 사용 기능 | 준비 사항 |
|---|---|---|
| SSAFY GMS Gemini | 체크리스트, AI 리포트, 챗봇 | GMS API Key 발급, `AI_API_BASE_URL`, `AI_MODEL` 설정 |
| Kakao Developers | 카카오 로그인, 주소·좌표, POI·도보 경로, 선택적 웹검색 | 앱 생성, Native/REST Key 등록, Android package·키 해시 및 OAuth Redirect URI 등록 |
| Naver Developers | 네이버 로그인 | 애플리케이션 생성, Android package와 Callback URL 등록 |
| Mapbox | Android 지도 | Public Token(`pk.`) 발급 후 앱 제한 설정 |
| Firebase Cloud Messaging | Android Push | Firebase Android 앱 등록, `google-services.json`, Admin SDK 서비스 계정 생성 |
| 공공데이터포털 | 공동주택·실거래 데이터, 기상청 특보 | 해당 API 활용신청 후 일반 인증키 발급 |
| Open-Meteo | 날씨·대기질 | 별도 키 없이 사용, 외부 통신 필요 |
| AWS S3/API Gateway/Lambda | 이미지·STT 음성 업로드·검증·다운로드 | Media Gateway 배포 후 Invoke URL과 32바이트 이상 내부 토큰 설정 |
| SMTP Provider | 비밀번호 재설정 메일 | SMTP 계정, TLS 587 설정 |
| Expo EAS | APK/AAB와 Android 서명 | Expo 계정·프로젝트 연결, EAS 환경변수·Credential 등록 |
| GitLab | 소스·Webhook | 프로젝트 접근권한, Protected branch, Webhook Secret |
| Jenkins | CI/CD | Docker socket, 운영 Secret read 권한, GitLab Plugin/credential |
| Let's Encrypt | HTTPS | DNS 연결 후 Certbot으로 인증서 발급·갱신 |

### 6.1 OAuth Redirect URI

현재 운영 기준:

```text
Kakao  https://legacy.example.com/oauth/kakao/callback
Naver  https://legacy.example.com/oauth/naver/callback
```

도메인을 바꾸면 Backend·Frontend 환경변수와 각 개발자 콘솔의 URI를 함께 변경한다.

카카오·네이버·Firebase·Mapbox 콘솔의 Android application ID에는 `com.ssafy.ssabangpalbang`을 사용한다. 카카오 로그인은 배포 서명 인증서의 key hash도 함께 등록한다.

### 6.2 Firebase

- Client용 `google-services.json`: EAS Secret/Credential에서 Android 빌드에 제공한다.
- Server용 Admin SDK JSON: Jenkins Secret Text `ssabangpalbang-firebase-service-account-json`으로 제공한다.
- 두 파일의 Firebase `project_id`가 같아야 한다.
- JSON 원문은 Git, 환경파일, Jenkins Console에 기록하지 않는다.

## 7. DB 구성과 덤프

DB 상세는 [`database/README.md`](database/README.md)를 따른다.

- 최신 Flyway: `V34__restyle_apartment_images_v4.sql`
- 초기 스키마: `backend/src/main/resources/db/migration/V1__init_schema.sql`
- 확장: `vector`, `postgis`, `pgcrypto`, `pg_trgm`
- 최신 DB는 빈 PostgreSQL 17에서 Backend `prod` 또는 `local` 프로필을 한 번 기동하면 재현된다.
- `exec/database/ssabangpalbang_schema_v34.sql`은 애플리케이션 데이터 없이 스키마와 Flyway 이력만 포함한 제출용 포터블 dump다.
- 운영 데이터 dump는 개인정보를 포함할 수 있으므로 Git에 저장하지 않는다.

## 8. 시연 시나리오

화면별 실행 순서와 발표 동선은 별도 문서인 [`DEMO_SCENARIO.md`](DEMO_SCENARIO.md)를 따른다.

## 9. 장애 진단과 복구

### 9.1 점검 순서

1. 실패한 Jenkins Stage
2. Compose 서비스 Health
3. Spring Boot·FastAPI 최근 로그
4. Nginx 설정과 TLS 인증서 만료일
5. 누락된 환경변수의 이름
6. 최근 성공 commit과 Backend 이미지

```bash
docker compose \
  --env-file /run/secrets/ssabangpalbang.env \
  -f infra/docker-compose.prod.yml \
  ps --all

docker compose \
  --env-file /run/secrets/ssabangpalbang.env \
  -f infra/docker-compose.prod.yml \
  logs --tail=200 app ai
```

### 9.2 복구 주의사항

- Pipeline은 자동 rollback하지 않는다.
- 최근 정상 이미지로 되돌리기 전에 새 Flyway Migration과 구 이미지의 호환성을 확인한다.
- `postgres-prod-data`를 삭제하거나 복원 대상으로 사용하지 않는다.
- `docker compose down -v`, `docker volume rm`, 전역 prune을 운영 복구에 사용하지 않는다.
- DB 복원은 별도 PostgreSQL 인스턴스·별도 DB·별도 Volume에서 검증한 뒤 승인된 절차로 수행한다.

## 10. 최종 체크리스트

- [ ] JDK 17, Node 22.13 이상, Docker Compose v2 확인
- [ ] 실제 Secret이 Git에 포함되지 않음
- [ ] PostgreSQL·Redis·Kafka·AI Health 정상
- [ ] 외부 STT 실연동 확인이 필요한 경우에만 승인 후 OpenAI `whisper-1` 수동 전사 smoke 통과
- [ ] `STT_OPENAI_API_KEY`가 AI/GMS 키와 분리되어 있고 Secret 파일 권한이 `root:jenkins 0640`
- [ ] Backend `/actuator/health` 정상
- [ ] Flyway가 `V34`까지 적용됨
- [ ] Frontend lint·typecheck·test 통과
- [ ] Android APK 설치 및 로그인 성공
- [ ] HTTPS·WSS 운영 주소 적용
- [ ] OAuth Callback URI와 Android package 등록
- [ ] FCM Client/Server 프로젝트 일치
- [ ] `DEMO_SCENARIO.md` 순서대로 실제 기기 리허설 완료
- [ ] 운영 DB·볼륨 백업 및 복원 검증

## 11. 정본 문서

- 로컬 실행: `docs/LOCAL_DEVELOPMENT.md`
- 운영 배포·복구: `docs/INF-006_DEPLOYMENT_RUNBOOK.md`
- Android 개발: `frontend/README.md`
- API 계약: `docs/API.md`
- ERD: `docs/ERD.md`
- ERD 다이어그램(이미지): `ERD/`
- 시스템 아키텍처 다이어그램: `Architecture.png`
- DB Migration: `backend/src/main/resources/db/migration/`
