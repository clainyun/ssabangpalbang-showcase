# INF-006 Nginx HTTPS 배포·복구 Runbook

이 문서는 `legacy.example.com`의 EC2 호스트 Nginx가 Spring Boot 운영
컨테이너를 HTTPS로 공개하는 절차를 설명한다. 모든 명령은 별도 표시가
없으면 저장소 루트에서 실행한다.

## 1. 운영 구조와 공개 범위

외부에는 EC2의 TCP 80·443만 공개한다.

| 경로 또는 서비스 | 접근 범위 | 전달 대상 |
|---|---|---|
| HTTP 전체 | 외부 | ACME를 제외하고 HTTPS 308 전환 |
| HTTPS `/api/*` | 외부 | `127.0.0.1:8080` Spring Boot |
| HTTPS `/actuator/health` | 외부 | `127.0.0.1:8080` Spring Boot |
| WSS `/ws` | 외부 | `127.0.0.1:8080` Spring Boot STOMP |
| FastAPI `ai:8000` | Docker 내부 | Spring Boot·내부 Worker만 접근 |
| PostgreSQL·Redis·Kafka | Docker 내부 | 운영 컨테이너만 접근 |

FastAPI 외부 경로는 운영 구성이 별도로 확정될 때 추가한다.

## 2. AWS 관리자 확인사항

AWS 콘솔 권한이 없는 운영자는 관리자에게 다음을 요청한다.

1. `legacy.example.com` A 레코드와 지급 EC2 Public IPv4 또는 Elastic IP 일치
2. Security Group 인바운드 TCP 80·443 허용
3. Public IP가 재시작 후에도 유지되는지 확인
4. SSH 22는 팀의 승인된 IP 범위로 제한

SSH 접근이 가능하면 EC2의 Public IPv4를 IMDSv2로 확인할 수 있다.

```bash
IMDS_TOKEN="$(curl -sS -X PUT \
  -H 'X-aws-ec2-metadata-token-ttl-seconds: 60' \
  http://169.254.169.254/latest/api/token)"

curl -sS \
  -H "X-aws-ec2-metadata-token: $IMDS_TOKEN" \
  http://169.254.169.254/latest/meta-data/public-ipv4

unset IMDS_TOKEN
```

## 3. Nginx와 최초 인증서 준비

Ubuntu EC2에서 Nginx를 설치한다. 기존 Certbot이 있으면 그 설치 방식을
그대로 사용하고, Certbot이 전혀 없을 때만 Snap으로 설치한다.

```bash
(
set -Eeuo pipefail

sudo apt-get update
sudo apt-get install -y nginx snapd util-linux

if command -v certbot >/dev/null 2>&1; then
  echo "Using existing Certbot: $(command -v certbot)"
else
  if [ -L /usr/local/bin/certbot ] && [ ! -e /usr/local/bin/certbot ]; then
    echo 'Broken /usr/local/bin/certbot symlink found; inspect it before continuing.' >&2
    exit 1
  fi

  if ! sudo snap list certbot >/dev/null 2>&1; then
    sudo snap install --classic certbot
  fi

  sudo test -x /snap/bin/certbot

  if ! command -v certbot >/dev/null 2>&1; then
    if sudo test -e /usr/local/bin/certbot || \
       sudo test -L /usr/local/bin/certbot; then
      echo '/usr/local/bin/certbot already exists; inspect it before continuing.' >&2
      exit 1
    fi

    sudo ln -s /snap/bin/certbot /usr/local/bin/certbot
  fi
fi

certbot --version
)
```

ACME Webroot와 최초 HTTP 설정을 설치한다.

```bash
if sudo test -e /etc/nginx/conf.d/ssabangpalbang.conf; then
  echo 'Existing ssabangpalbang Nginx config found; inspect it before bootstrap.' >&2
  exit 1
fi

sudo install -d -o root -g root -m 0755 /var/www/certbot
sudo install -o root -g root -m 0644 \
  infra/nginx/ssabangpalbang-bootstrap.conf \
  /etc/nginx/conf.d/ssabangpalbang.conf

sudo nginx -t
sudo systemctl enable --now nginx
sudo systemctl reload nginx
```

기존 Nginx 파일이 같은 `server_name` 또는 80·443 포트를 소유하면 임의로
삭제하지 않는다. `sudo nginx -T` 결과에서 충돌 파일을 확인하고 운영
관리자와 정리한 뒤 계속한다. 명령 출력은 인증서 개인키를 포함하지 않지만
운영 설정 전체가 노출되므로 Jenkins Console에는 남기지 않는다.

인증서 관리 이메일을 터미널에서 입력하고 인증서를 발급한다.

```bash
read -r -p 'Certbot notification email: ' CERTBOT_EMAIL

sudo certbot certonly \
  --webroot \
  --webroot-path /var/www/certbot \
  --domain legacy.example.com \
  --email "$CERTBOT_EMAIL" \
  --agree-tos \
  --no-eff-email

unset CERTBOT_EMAIL
sudo certbot certificates
```

인증서 발급 후 운영 설정과 갱신 hook을 설치한다. 활성 설정은 백업한 뒤
검증하고, 검증 실패 시 bootstrap 설정을 복원한다. 인증서와 개인키는
`/etc/letsencrypt`에서만 관리하고 저장소나 Jenkins 작업공간으로 복사하지
않는다.

운영 설정을 처음 활성화하기 전에 Android App Link 정적 자산도 설치한다.
공유 URL `https://legacy.example.com/report/<ID>`는 설치 여부와 관계없이 항상
웹 랜딩을 먼저 표시한다. 웹 랜딩의 앱 열기 버튼은
`https://legacy.example.com/open/report/<ID>`를 사용하며, 새 APK의 Android
intent filter도 이 경로만 처리한다. 앱이 링크를 가로채지 못하면 Nginx가
`/report/<ID>?install=1`로 302 응답한다. `/report/`를 intent filter에 다시
추가하지 않는다.

랜딩은 Android Chrome의 `navigator.getInstalledRelatedApps()`를 보조 신호로만
사용한다. 설치 감지 성공 시에도 앱을 자동 실행하지 않고 확인 dialog를 띄우며,
API 미지원·빈 결과·오류일 때는 앱 설치와 앱 열기 CTA를 모두 유지한다. 현재 목업
설치 주소는 `infra/nginx/www/share/report.html`의 `install-app` 링크 한 곳에서
관리하며, 실제 원스토어 상품 URL이 승인된 뒤 이 값을 교체하고 설치기를 다시
실행한다.

운영 Nginx의 정적 공유 라우트가 아직 적용되지 않아 `/report/<ID>` 요청이 Backend로
전달되는 이전 프록시 구성에서는 Backend의 공개 `GET /report/<ID>`가 데이터 없는 최소
랜딩을 반환한다. 최소 랜딩은 명시적인 Android `intent:`로 앱을 열며 리포트 DB를
조회하지 않는다. 현 정적 Nginx 라우트에서 자산이 누락되면 `404`이므로 배포 smoke
test로 자산 설치를 검증해야 하며, 이 fallback은 도메인 검증과 자동 App Link를
제공하는 Nginx·`assetlinks.json` 설치 절차를 대체하지 않는다.

Play Store 배포 여부와 관계없이 실제 단말에 설치되는 최종 APK의 서명 인증서를
사용한다. 원스토어가 업로드 파일을 다시 서명한다면 업로드 키가 아니라 원스토어가
배포한 APK의 인증서가 기준이다. 최종 배포 APK에서 다음과 같이 지문과 인증서
주체를 확인하고, `CN=Android Debug`인 인증서는 사용하지 않는다.

```bash
apksigner verify --print-certs /secure/path/to/final-distributed.apk
```

출력의 `Signer #1 certificate SHA-256 digest`를 32개의 콜론 구분 바이트 형식으로
준비한다. 지문은 Secret은 아니지만 실제 값을 저장소 예시나 Jenkinsfile에
하드코딩하지 않는다. 설치기는 누락·형식 오류·명시적인 debug/placeholder 값과
반복 바이트 placeholder를 거부하고, HTML placeholder·OG 계약·PNG 형식과 크기도
배치 전에 확인한다. 검증 실패나 동시 실행 시 현재 공개 자산을 바꾸지 않는다.
HTML·브랜드 이미지·웹 manifest·설치 감지 JavaScript·`assetlinks.json`을 새 릴리스
디렉터리에 완성한 뒤 `current` 링크를 원자 교체하므로 Nginx에는 부분 파일이
노출되지 않는다. 설치기의 동시 실행 잠금에는 `util-linux`의 `flock`을 사용한다.

```bash
read -r -p 'Final Android release certificate SHA-256: ' \
  ANDROID_RELEASE_CERT_SHA256

sudo sh infra/nginx/install-app-link-assets.sh \
  --release-sha256 "$ANDROID_RELEASE_CERT_SHA256"

unset ANDROID_RELEASE_CERT_SHA256
```

```bash
(
set -Eeuo pipefail

ACTIVE_NGINX_CONFIG=/etc/nginx/conf.d/ssabangpalbang.conf
NGINX_CONFIG_BACKUP="$(
  sudo mktemp /etc/nginx/conf.d/.ssabangpalbang.conf.backup.XXXXXX
)"
NGINX_BACKUP_READY=false
NGINX_ACTIVE_CHANGED=false
NGINX_CANDIDATE_COMMITTED=false

restore_nginx_candidate() {
  RESTORE_STATUS=$?
  trap - EXIT INT TERM

  if [ "$NGINX_BACKUP_READY" = true ] && \
     [ "$NGINX_ACTIVE_CHANGED" = true ] && \
     [ "$NGINX_CANDIDATE_COMMITTED" != true ]; then
    echo 'Nginx candidate failed; restoring the previous configuration.' >&2

    if ! sudo cp --preserve=mode,ownership,timestamps \
        "$NGINX_CONFIG_BACKUP" "$ACTIVE_NGINX_CONFIG"; then
      echo 'Previous Nginx configuration restore failed.' >&2
      RESTORE_STATUS=1
    elif ! sudo nginx -t; then
      echo 'Restored Nginx configuration validation failed.' >&2
      RESTORE_STATUS=1
    elif ! sudo systemctl reload nginx; then
      echo 'Restored Nginx configuration reload failed.' >&2
      RESTORE_STATUS=1
    fi
  fi

  sudo rm -f "$NGINX_CONFIG_BACKUP" || true
  exit "$RESTORE_STATUS"
}

trap restore_nginx_candidate EXIT
trap 'exit 130' INT
trap 'exit 143' TERM

sudo cp --preserve=mode,ownership,timestamps \
  "$ACTIVE_NGINX_CONFIG" "$NGINX_CONFIG_BACKUP"
NGINX_BACKUP_READY=true
NGINX_ACTIVE_CHANGED=true

sudo install -o root -g root -m 0644 \
  infra/nginx/ssabangpalbang.conf \
  "$ACTIVE_NGINX_CONFIG"

sudo nginx -t
sudo systemctl reload nginx
NGINX_CANDIDATE_COMMITTED=true

sudo rm -f "$NGINX_CONFIG_BACKUP"
NGINX_BACKUP_READY=false
trap - EXIT INT TERM

sudo install -o root -g root -m 0755 \
  infra/nginx/reload-nginx.sh \
  /etc/letsencrypt/renewal-hooks/deploy/reload-nginx

sudo install -o root -g root -m 0755 \
  infra/nginx/jenkins-diagnostics.sh \
  /usr/local/sbin/ssabang-nginx-diagnostics

sudo /etc/letsencrypt/renewal-hooks/deploy/reload-nginx
sudo certbot renew --dry-run
systemctl list-timers --all | grep -i certbot
)
```

## 4. Jenkins 최소 권한

현재 Jenkins는 EC2 호스트의 systemd 서비스가 아니라 Docker 컨테이너에서
실행된다. Jenkins 컨테이너에는 배포용 Docker socket, 운영 환경파일과
영속적인 `/var/jenkins_home`만 최소 범위로 mount한다. 컨테이너 이름은 운영
구성에 맞게 지정한다.

```bash
JENKINS_CONTAINER="${JENKINS_CONTAINER:-jenkins}"

docker exec "$JENKINS_CONTAINER" id
docker exec "$JENKINS_CONTAINER" docker version
docker exec "$JENKINS_CONTAINER" docker compose version
docker exec "$JENKINS_CONTAINER" test -r /run/secrets/ssabangpalbang.env
docker inspect \
  --format '{{range .Mounts}}{{println .Destination .Type}}{{end}}' \
  "$JENKINS_CONTAINER"
```

Docker socket 접근은 사실상 host root 수준 권한이다. Jenkins Job과 GitLab
Webhook 접근을 제한하고 임의 Pipeline 실행 권한을 부여하지 않는다.

Jenkins 컨테이너에서 host의 `sudo`, `nginx -t`, systemd 명령을 직접 실행하지
않는다. Pipeline의 host Nginx 진단은 helper가 명시적으로 제공된 경우에만
실행한다. Helper가 없으면 외부 HTTPS Smoke Test를 권위 있는 판정으로 사용하고,
helper가 있는데 실패하면 잘못된 Nginx 구성을 놓치지 않도록 배포를 실패시킨다.
Host Nginx 상세 진단은 승인된 운영자가 EC2에서 직접 실행한다.

```bash
sudo /usr/local/sbin/ssabang-nginx-diagnostics
```

배포 성공 메타데이터는 Jenkins home 아래
`/var/jenkins_home/ssabangpalbang/deployments/last-success.env`에 기록된다.
별도 `/var/lib` mount는 필요하지 않지만 `/var/jenkins_home` 자체가 named
volume 또는 bind mount로 영속화되어야 한다. Jenkins 컨테이너와 해당 volume을
함께 삭제하면 최근 성공 메타데이터도 유실되므로 컨테이너 교체 전에 mount를
확인하고 Jenkins home volume에 `down -v`를 사용하지 않는다.

기존 Pipeline이 사용하던
`/var/lib/ssabangpalbang/deployments/last-success.env`가 남아 있다면 첫 신규
배포 전에 보존한다. 아래 수동 복구 스크립트는 이 legacy 경로를 먼저 읽고,
신규 배포가 성공하면 Jenkins home의 메타데이터가 이후 기준이 된다.

## 5. 운영 환경과 GitLab Webhook

운영 환경파일은 `/run/secrets/ssabangpalbang.env`에서 관리한다. 실제 값은
저장소, Merge Request, Jenkins Console에 붙여 넣지 않는다. Jenkins
Credentials의 Media Gateway 값과 Firebase 서비스 계정 JSON도 로그로 출력하지
않는다.

`/run`은 재부팅 시 초기화되므로 AWS 관리자는 승인된 영구 Secret 저장소에서
환경파일을 복원하는 root-owned systemd unit을 구성해야 한다. Pipeline을
실행하기 전에 다음 조건이 모두 성립해야 한다.

```bash
JENKINS_CONTAINER="${JENKINS_CONTAINER:-jenkins}"

sudo systemctl is-enabled ssabangpalbang-secrets.service
sudo systemctl is-active ssabangpalbang-secrets.service
docker exec "$JENKINS_CONTAINER" test -r /run/secrets/ssabangpalbang.env
```

Secret 저장소의 종류와 IAM 권한은 AWS 관리자가 결정한다. 서비스는 파일을
`root:jenkins`, mode `0640`으로 생성하고 Jenkins보다 먼저 실행해야 하며,
파일 내용이나 복원 명령의 실제 값을 Console에 출력하면 안 된다.

FCM을 활성화할 때는 Firebase 서비스 계정 JSON 전체를 Jenkins의 Secret Text
credential `ssabangpalbang-firebase-service-account-json`에 등록한다. Pipeline은
이를 `FCM_SERVICE_ACCOUNT_JSON` 환경변수로 바인딩하고 Compose의 environment-backed
secret에만 전달한다. EC2 host나 Jenkins workspace에 JSON 파일을 만들지 않으며,
운영 환경파일에는 활성화 플래그만 둔다.

```dotenv
FCM_ENABLED=true
```

배포 로그의 설정 검증 단계에서 다음 비민감 메시지를 확인한다.

```text
FCM service account Jenkins credential is available.
```

Compose는 Credential 값을 앱 컨테이너에만
`/run/secrets/firebase-service-account.json`으로 제공하고
`FCM_SERVICE_ACCOUNT_PATH`도 같은 내부 경로로 고정한다. 배포 후 내용은 출력하지
않고 존재 여부, 읽기 권한과 read-only mount만 확인한다.

```bash
APP_CONTAINER=ssabangpalbang-prod-app-1
FCM_SERVICE_ACCOUNT_PATH=/run/secrets/firebase-service-account.json

docker exec "$APP_CONTAINER" \
  test -s "$FCM_SERVICE_ACCOUNT_PATH"
docker exec "$APP_CONTAINER" \
  test -r "$FCM_SERVICE_ACCOUNT_PATH"
docker inspect \
  --format '{{range .Mounts}}{{if eq .Destination "'"$FCM_SERVICE_ACCOUNT_PATH"'"}}{{println .Destination .RW}}{{end}}{{end}}' \
  "$APP_CONTAINER"
```

마지막 값이 `false`여야 한다. JSON을 `cat`, `docker inspect .Config.Env`,
`printenv` 또는 Pipeline trace로 출력하지 않는다. 운영 환경파일에는
host 파일 source나 앱 내부 secret 경로를 추가하지 않는다.

다음 다섯 값은 관련 기능이 준비되기 전까지 운영 환경파일에서 생략하거나 빈
값으로 둘 수 있다. Pipeline은 누락 자체로 실패하지 않지만 `change_me` 같은
placeholder가 들어오면 실제 인증정보로 오인하지 않도록 실패시킨다.

```dotenv
KAKAO_REST_API_KEY=
NAVER_CLIENT_ID=
NAVER_CLIENT_SECRET=
AI_REPORT_DB_USER=
AI_REPORT_DB_PASSWORD=
```

AI Report DB와 RAG는 명시적으로 활성화하기 전까지 비활성 상태로 기동한다.
두 플래그를 `true`로 바꾸기 전에 전용 read-only DB 계정과 비밀번호를 Secret
저장소에 등록한다. 비활성 상태에서는 FastAPI Health와 STT는 정상 동작하지만
DB 기반 Report/RAG 요청은 사용할 수 없다.

```dotenv
AI_REPORT_DB_ENABLED=false
RAG_ENABLED=false
```

운영 챗봇을 활성화할 때는 `/run/secrets/ssabangpalbang.env`에 다음 값을 등록한다.
실제 값은 승인된 Secret 저장소에서만 주입하고 저장소, MR, Jenkins Console에 남기지
않는다.

```dotenv
RAG_ENABLED=true
AI_PROVIDER=gms-gemini
AI_API_BASE_URL=https://ai-gateway.example.com/gmsapi/generativelanguage.googleapis.com
AI_API_KEY=
AI_MODEL=gemini-3.5-flash
AI_REPORT_DB_USER=
AI_REPORT_DB_PASSWORD=
```

`AI_REPORT_DB_USER`와 `AI_REPORT_DB_PASSWORD`는 운영 Compose에서 각각 `RAG_DB_USER`와
`RAG_DB_PASSWORD`로도 사용된다. 운영 DB 관리자 계정인 `POSTGRES_USER`를 재사용하지
않는다. 값이 비어 있으면 AI 컨테이너가 `missing required RAG settings`로 기동에
실패한다. `RAG_ENABLED`에 허용되는 값은
`1 / true / yes / on / 0 / false / no / off`뿐이다.

설정을 적용할 때는 PostgreSQL 컨테이너와 볼륨을 건드리지 않고 AI 서비스만 재생성한다.

```bash
docker compose \
  --env-file /run/secrets/ssabangpalbang.env \
  -f infra/docker-compose.prod.yml \
  up -d --no-build --no-deps --force-recreate ai
```

이 절차에서는 `docker compose down`, `docker compose down -v`, `docker volume rm`을
사용하지 않는다. 배포 전후로 §9의 Volume 유지 증빙 절차를 실행해 네 개 볼륨이
동일한지 기록한다. 기존 목록의 `ssabangpalbang-prod_ai-model-cache`는 임베딩 모델을
보존한다.

배포 후 다음을 확인한다.

1. `docker compose ... exec ai printenv RAG_ENABLED` 결과가 `true`다.
2. AI 컨테이너 Health가 `healthy`다. RAG DB 접속 실패는 답변 요청 시
   502 `RAG_DB_FAILED`로 드러난다.
3. 운영 PostgreSQL에서 `apartment_rag_document`의 `source_type`별 색인 건수를 확인한다.
4. 기존 `FAILED` 메시지가 아닌 새 질문으로 챗봇 답변이 `COMPLETED`가 되는지 확인한다.
5. 색인이 0건이고 `WEB_SEARCH_PROVIDER=none`이면
   `"신뢰할 수 있는 자료를 찾지 못해 답변드리기 어렵습니다."`라는 NONE 응답이 정상이다.

운영 배포 Job은 별도로 다음을 확인한다. 아래 develop 검증 Job의 Push-only 설정과
혼합하지 않는다.

1. Jenkins Job SCM 브랜치가 `develop`으로 제한되어 있다.
2. GitLab Webhook은 Push event와 Merge Request event 중 팀 정책에 맞는
   이벤트만 활성화한다.
3. Webhook Secret Token 검증을 활성화한다.
4. 수동 EC2 `git pull`이나 Pipeline을 우회한 재빌드는 운영 배포로 사용하지
   않는다.

### develop 브랜치 검증 전용 Jenkins Job

`ssabangpalbang-develop-verify`는 운영 배포 Job과 분리한 비배포 Pipeline Job이다.
`develop` Push에서 `Jenkinsfile.verify`만 읽고 Frontend·Backend·AI·PostgreSQL·
Infra·Compose 계약을 검증한다. 기존 운영 EC2의 Docker daemon을 함께 사용하므로
이름과 label에 의한 격리는 보안 경계가 아니다. Docker socket은 사실상 host root
권한이며, `develop`에 Push할 수 있는 사용자는 검증 Pipeline을 통해 Docker host에서
명령을 실행할 수 있다. GitLab Protected branch의 Push·Merge 권한은 신뢰된 팀원으로
제한한다.

`Jenkinsfile.verify`는 별도 node label을 요구하지 않고 현재 사용 가능한 Jenkins
Agent에서 실행한다. Pipeline 첫 단계는 Linux shell 도구, Git, Docker daemon과
Docker Compose v2 접근을 명시적으로 검사하고 하나라도 없으면 이미지 빌드 전에
실패한다. 현재는 Docker socket을 가진 단일 Jenkins node만 사용한다. 향후 Agent를
추가할 때는 `agent any`가 Docker 접근 권한이 없는 node를 선택할 수 있으므로 전용
Docker Agent 또는 node label 도입을 다시 검토한다.

Jenkins에 GitLab Plugin과 Git Plugin을 설치한다. `Manage Jenkins > System > GitLab`
에서 전용 Connection(예: `ssabangpalbang-gitlab`)을 만들고 GitLab URL과 API Token
credential을 선택한 뒤 `Test Connection`을 통과시킨다. Commit status 작성에는
GitLab API 권한이 필요하므로 Project Access Token을 우선 사용하고, 플러그인이
요구하는 최소 `api` scope와 해당 프로젝트 권한만 부여한다. 이 credential은 SCM
checkout용 `read_repository` credential, Webhook Secret Token, 운영 애플리케이션
Secret과 각각 분리한다. Token 값은 저장소나 Console에 기록하지 않는다.

GitLab에서 `develop`을 Protected branch로 등록하고 `Allowed to merge`와
`Allowed to push`를 팀 정책의 승인된 역할로 제한한다. 검증 Job에는 운영 환경파일,
Firebase·Media Gateway credential 또는 운영 배포 parameter를 연결하지 않는다.
전용 Jenkins Agent/label 도입은 현재 범위가 아니며, Agent를 늘릴 때 Docker socket
접근 가능한 전용 label로 옮길지는 운영자 승인을 받아 결정한다.

Jenkins에서 다음 값으로 Job을 생성한다.

1. `New Item`에서 `ssabangpalbang-develop-verify` 이름의 `Pipeline`을 만든다.
2. `Definition`은 `Pipeline script from SCM`, `SCM`은 `Git`으로 설정한다.
3. `Repository URL`은
   `https://lab.ssafy.com/s15-webmobile4-sub1/S15P11A701.git`로 설정한다.
4. `Credentials`는 `read_repository` 범위만 가진 SCM 전용 credential을
   사용한다. 운영 배포 credential과 애플리케이션 Secret은 연결하지 않는다.
5. `Branch Specifier`를 정확히 `*/develop`으로 설정한다.
6. `Script Path`를 정확히 `Jenkinsfile.verify`로 설정한다.
7. Job의 GitLab Connection은 위에서 검증한 전용 Connection을 선택한다. Commit
   status context는 Pipeline이 사용하는 `develop-ci`다.
8. Job parameter는 만들지 않는다. 운영 배포 Job의 Branch Specifier와 production
   `Jenkinsfile`은 이 절차에서 변경하지 않는다.

GitLab Push Webhook은 다음과 같이 연결한다.

1. Jenkins Job의 `Build Triggers`에서
   `Build when a change is pushed to GitLab`을 활성화한다.
2. `Push Events`만 활성화하고 Merge Request event는 비활성화한다.
3. `Filter branches by regex`를 선택하고 필터를 정확히 `^develop$`로 설정한다.
4. Jenkins가 표시한 Job 전용 Webhook URL
   `https://<JENKINS_HOST>/project/ssabangpalbang-develop-verify`와 Secret Token을
   GitLab `Settings > Webhooks`에 등록한다. Token 실제 값은 저장소나 Console에
   기록하지 않는다.
5. GitLab Webhook에서는 `Push events`만 켜고 Push branch filter를 wildcard
   `develop` 또는 정규식 `^develop$`로 설정하며 SSL verification을 유지한다.
   GitLab 버전상 Webhook 화면에 브랜치 필터가 없는 경우 Jenkins의 `^develop$`
   필터와 Pipeline 내부 branch/SHA guard가 오설정을 이중으로 차단한다.
6. Webhook의 `Test > Push events`와 실제 `develop` Push로 HTTP 2xx, Jenkins 1회
   실행, GitLab commit status `develop-ci`의 running → success/failed 전환을 확인한다.
   새 Push가 이전 Run을 중단하면 canceled가 terminal status이며, 다른 브랜치 Push와
   Merge Request event는 이 Job을 시작하지 않아야 한다.

운영 배포 Job도 `develop` Push Webhook을 계속 구독하면 하나의 Push로 검증 Job과
배포 Job이 모두 시작되어, 검증 완료 전에 운영 배포가 진행될 수 있다. 현재 절차는
production `Jenkinsfile`이나 운영 Job을 자동 변경하지 않는다. develop Push에서
배포도 계속할지 팀이 결정하기 전에는 운영 Job Webhook endpoint/trigger를 그대로
둔 채 검증 전용이라고 간주하면 안 된다. 검증 선행을 강제하려면 별도 승인 후 운영
Job의 자동 trigger를 비활성화하거나 성공한 `develop-ci` status를 배포 조건으로
연결한다.

검증 Pipeline은 `checkout scm`의 SHA만 사용한다. `GIT_BRANCH`/`BRANCH_NAME`과
Webhook의 `gitlabSourceBranch`/`gitlabBranch`를 정규화해 `develop`인지 확인한다.
Webhook action 값이 있으면 `PUSH`만 허용하고, checkout SHA가
`refs/remotes/origin/develop`과 정확히 일치하지 않으면 이미지 빌드 전에 실패한다.
Docker-safe Job 이름에는 정규화한 이름과 원본 `JOB_NAME`의 짧은 해시를 함께 넣고,
이미지 태그에는 Job 식별자·전체 SHA·Build 번호를 넣는다.

Compose는 Git-tracked local·prod·report Kafka E2E·report Backend E2E 네 정의와
예시 환경파일만 임시 디렉터리에 풀어 각각 `docker compose config --quiet`로
렌더한다. E2E 정의의 필수 값은 render-only 임시 값으로 주입하며 서비스를
기동하지 않는다. 운영 환경파일 `/run/secrets/ssabangpalbang.env`, Jenkins
애플리케이션 credential, 배포 메타데이터는 읽지 않는다.

Frontend는 `node:22-bookworm-slim` 기반의 현재 Run 전용 컨테이너에 checkout SHA의
`frontend` 디렉터리만 복사한다. bind mount 없이 `npm ci`, `npm run lint`,
`npm run typecheck`, `npm test`, `npx expo export --platform android`를 순서대로
실행한다. Expo 번들에는 localhost API/WS 주소와 CI 전용 공개 Mapbox placeholder만
주입하며 실제 운영 키나 Secret을 사용하지 않는다. lockfile을 갱신하거나
비결정적인 최신 `expo-doctor`를 호출하지 않는다. 컨테이너는 non-root 사용자,
capability 제거와 `no-new-privileges`로 실행하고, `npm ci`가 끝나면 bridge network를
분리한 뒤 lockfile에 설치된 Expo CLI로 나머지 gate를 오프라인 실행한다.

Backend와 AI 이미지 Context, `ai/pytest.ini`, AI 테스트와 계약 fixture는 모두
검증 SHA의 `git archive`에서 전달한다. AI 테스트 컨테이너는 network를 `none`으로
고정하고 bind mount와 volume을 사용하지 않으며 CPU 1개, 메모리 2 GiB, PID
256개로 제한한다. 같은 무네트워크 컨테이너에서
`infra/scripts/tests/test_backup_restore_guards.py`도 실행하며 테스트의 fake 명령과
임시 디렉터리만 사용한다. Pipeline은 Compose
`up`·`down`, 운영 Smoke Test, Docker 전역 prune을 실행하지 않는다.

Backend Docker build는 같은 daemon의 CPU·메모리와 BuildKit cache를 공유한다.
이는 동일 EC2 방식을 선택한 결과이므로 검증은 트래픽이 낮은 시간에 수행하고,
운영자는 실행 전후 `docker system df`와 EC2 CPU·메모리·디스크 사용량을 확인한다.
검증 Job에서 전역 `docker builder prune`을 실행하지 않는다. cache 증가가 운영
한계에 접근하면 운영자 점검 시간에 보존 대상 이미지를 확인한 뒤 host 유지보수
절차로 처리하거나 검증 전용 Agent 이전을 승인받는다.

이미지 태그나 AI/Frontend 테스트 컨테이너 이름이 이미 있으면 Pipeline은 덮어쓰거나
삭제하지 않고 `Preflight Isolation` 단계에서 실패한다. 정상·실패·중단 후 정리는
현재 Run의 정확한 이름과 `com.ssabangpalbang.verify-run` label이 모두 일치하는
리소스만 제거한다. 충돌 또는 label 불일치가 발생하면 임의로 `docker rm`이나
`docker image rm`을 실행하지 말고 Console의 이름과 label을 운영자와 확인한다.

첫 실행 전에 운영 리소스 식별자를 기록하고 `Build Now` 및 develop Push 실행
후 같은 명령을 다시 실행해 결과가 동일한지 비교한다.

```bash
docker ps --all \
  --filter label=com.docker.compose.project=ssabangpalbang-prod \
  --format 'container={{.ID}} name={{.Names}} image={{.Image}}' \
  | sort

docker volume ls \
  --filter label=com.docker.compose.project=ssabangpalbang-prod \
  --format 'volume={{.Name}}' \
  | sort

docker network ls \
  --filter label=com.docker.compose.project=ssabangpalbang-prod \
  --format 'network={{.ID}} name={{.Name}}' \
  | sort
```

Console Output에서 develop branch/SHA guard, Compose 4종 `config --quiet`, Frontend
5개 gate, Backend Gradle 테스트 포함 이미지 빌드, `--network none` AI pytest와
Infra unittest, PostgreSQL Testcontainers, 현재 Run 전용 정리를 확인한다. 성공 후
현재 Run의 이미지와 컨테이너가 남지 않았고 위 운영 container·volume·network
식별자가 그대로인지 완료 증빙으로 보관한다.

Job 간 격리는 유지보수 시간에 Webhook을 연결하지 않은 동일 구성의 임시 검증
Job을 하나 만들고 두 Job에서 같은 SHA를 검증해 확인한다. Console의 Job hash와
이미지·컨테이너 이름이 서로 다르고, 한 Job의 정리가 다른 Job의 label을 가진
리소스를 제거하지 않는지 확인한다. 동시 빌드가 운영 부하 한계를 넘을 가능성이
있으면 동시에 실행하지 않고 생성된 Job key의 차이와 정리 label 검사를 증빙한다.

충돌 실패 경로는 다음 조건을 모두 통제할 수 있는 유지보수 시간에만 검증한다.

1. Jenkins에서 대상 SHA와 다음 Build 번호를 확인하고, Console에 표시되는 규칙과
   동일한 AI 또는 Frontend 테스트 컨테이너 이름 하나를 계산한다.
2. 해당 이름에 `com.ssabangpalbang.verify-run=foreign-probe` label을 가진 정지
   컨테이너를 `--network none`과 mount 없이 생성한다.
3. `Build Now`가 `Preflight Isolation`에서 실패하고 probe 컨테이너를 삭제하지
   않았는지 ID와 label로 확인한다.
4. 확인한 probe 컨테이너 ID만 `docker container rm --volumes <PROBE_ID>`로
   제거한다. 이름·Build 번호가 불명확하면 이 검증을 실행하지 않는다.

모바일 운영 빌드에는 다음 공개 주소를 주입한다.

```dotenv
EXPO_PUBLIC_API_BASE_URL=https://legacy.example.com
EXPO_PUBLIC_WS_URL=wss://legacy.example.com/ws
```

## 6. 배포·검증

Jenkins Pipeline은 다음 순서로 동작한다.

1. 운영 환경·Compose 검증과 선택적 host Nginx helper 확인
2. `ssabangpalbang-backend:<Git SHA>-<Build number>` 이미지 빌드
3. `docker compose up -d --no-build` 재배포
4. Spring Boot와 FastAPI 컨테이너 Health 대기
5. 외부 HTTP 전환·Actuator·REST·WebSocket Handshake 검증
6. 모든 검증 성공 후에만 원자적으로 `last-success.env` 갱신
7. 최근 Backend 이미지 5개를 보존하고 7일보다 오래된 Build Cache만 정리

Host Nginx helper가 제공되지 않은 환경에서는 컨테이너 Jenkins가 host Nginx를
직접 검증할 수 없으므로 5번 외부 HTTPS Smoke Test가 공개 경로의 권위 있는
배포 판정이다. Helper가 제공되면 해당 검증도 반드시 통과해야 한다.

EC2에서 동일한 외부 검증을 실행할 수 있다.

```bash
read -r -p 'Expected Android release certificate SHA-256: ' \
  EXPECTED_APP_LINK_SHA256

EXPECTED_REPORT_STATUS=200 \
  EXPECTED_APP_LINK_SHA256="$EXPECTED_APP_LINK_SHA256" \
  bash infra/scripts/prod-smoke-test.sh

unset EXPECTED_APP_LINK_SHA256
```

Jenkins의 기본 smoke test는 Nginx App Link 구성이 준비될 때까지 `/report/1`의
`401`만 확인하고 App Link 상세 검증을 건너뛴다. 위 운영자 명령은
`EXPECTED_REPORT_STATUS=200`을 명시하므로 기존 App Link 전체 계약을 엄격하게
검증한다.

예상 결과:

- HTTP `/actuator/health`: 308 및 동일 HTTPS Location
- HTTPS `/actuator/health`: `{"status":"UP"}`
- 무인증 `/api/v1/members/me`: Spring Security의 401
- HTTPS `/report/1`: 개인정보 없는 공통 OG HTML, 설치 CTA와 앱 열기 버튼
- HTTPS `/open/report/1`: 앱이 가로채지 못하면 `/report/1?install=1`로 302
- HTTPS `/share/report.webmanifest`: 운영 Android 패키지 관계
- HTTPS `/share/report-open.js`: 점진적 설치 감지와 확인 dialog 제어
- HTTPS `/.well-known/assetlinks.json`: 운영 패키지와 최종 서명 SHA-256
- HTTPS `/share/report-card.png`: 공통 브랜드 PNG
- `/report/0`, `/open/report/0`, 음수·문자·추가 경로: 404
- `/ws`: 101 Switching Protocols

앱 링크 자산이나 지문을 갱신할 때도 먼저 설치기를 실행한 뒤 `nginx -t`, reload,
외부 Smoke Test 순서로 확인한다. 설치기는 이전 릴리스 디렉터리를 삭제하지 않으므로
장애 시 `/var/www/ssabangpalbang/app-link-assets/current`를 확인하고 승인된 운영자가
직전 릴리스로 링크를 되돌릴 수 있다. 임의의 지문으로 복구하거나 부분 JSON을 직접
편집하지 않는다.

`/report/`에서 `/open/report/`로 바뀐 intent filter와 설치 감지용
`asset_statements`는 Android manifest 자산이므로 EAS Update만으로 반영되지 않는다.
반드시 새 release APK를 빌드해 최종 배포 서명과 manifest를 확인한다. 실제 단말의
Android Chrome에서 설치됨·미설치·API 미지원 또는 인앱 브라우저 세 경우를 확인하고,
감지 성공 시 확인 전 자동 이동이 없는지와 취소 뒤 두 CTA가 남는지를 기록한다.

101은 HTTP Handshake만 검증한다. STOMP 완료 증빙은 유효한 테스트 계정으로
운영 모바일 앱에 로그인하여 채팅방을 연 뒤 다음을 함께 확인한다.

1. `wss://legacy.example.com/ws` 연결
2. STOMP `CONNECTED` 수신
3. `/sub/studies/{studyId}/chat` 구독
4. 메시지 송수신 및 1분 이상 연결 유지
5. JWT나 STOMP CONNECT Header가 앱·Nginx·Jenkins 로그에 출력되지 않음

## 7. 실패 진단

Pipeline 실패 시 자동 롤백하지 않는다. 먼저 다음을 확인한다.

1. 실패한 Jenkins Stage와 Console Output
2. `docker compose ps` 및 서비스 Health
3. Spring Boot·FastAPI 최근 로그
4. Nginx 설정·서비스·안전한 접근 로그·오류 로그
5. 공개 인증서의 발급자와 만료일
6. 누락된 환경변수 이름
7. 최근 성공 Build·Commit·Backend 이미지

수동 점검 명령:

```bash
docker compose \
  --env-file /run/secrets/ssabangpalbang.env \
  -f infra/docker-compose.prod.yml ps

sudo /usr/local/sbin/ssabang-nginx-diagnostics

JENKINS_CONTAINER="${JENKINS_CONTAINER:-jenkins}"
docker exec "$JENKINS_CONTAINER" sed -n \
  -e '/^BUILD_NUMBER=/p' \
  -e '/^GIT_COMMIT=/p' \
  -e '/^BACKEND_IMAGE=/p' \
  -e '/^DEPLOYED_AT=/p' \
  /var/jenkins_home/ssabangpalbang/deployments/last-success.env
```

환경파일 전체, `docker inspect`의 전체 Config, Authorization Header와
인증서 개인키는 진단 결과로 출력하지 않는다.

## 8. 최근 정상 이미지로 수동 복구

먼저 최근 성공 이미지와 Flyway Migration 호환성을 확인한다. 실패한 새
애플리케이션이 이미 전진 Migration을 적용했다면 이전 이미지가 현재
Schema와 호환되는지 확인하기 전에는 복구하지 않는다. DB Migration과
Volume은 자동으로 되돌리지 않는다.

Jenkins에서 주입하던 Media Gateway 값은 승인된 Secret 저장소에서 현재
Shell에 안전하게 주입해야 한다. 값은 명령 인자나 Shell trace로 출력하지
않는다. 명령 도중 실패해도 환경변수가 남지 않도록 cleanup trap을 먼저
등록한다. 아래 블록 전체를 한 번에 실행한다. 복구 명령은 격리된 Subshell에서
동작하므로 완료 또는 실패 후 현재 Shell의 옵션과 환경변수는 바뀌지 않는다.
최근 정상 Backend 이미지만 사용해 App 서비스를 재생성한다.

운영 환경의 `FCM_ENABLED=true`를 유지한 채 host에서 App을 수동 재생성하려면
`FCM_SERVICE_ACCOUNT_JSON`도 승인된 Secret 공급 경로에서 현재 Shell에만
주입되어 있어야 한다. Jenkins Credential의 값을 host로 추출하거나 Console에
출력하지 않는다. 승인된 공급 경로가 없다면 이 수동 복구를 실행하지 않고
Credential이 바인딩되는 Jenkins 복구 절차를 사용한다.

```bash
(
rollback_backend() {
  set -Eeuo pipefail
  set +x

  cleanup_rollback_secrets() {
    unset MEDIA_GATEWAY_BASE_URL MEDIA_GATEWAY_INTERNAL_TOKEN
    unset FCM_SERVICE_ACCOUNT_JSON
    if [ -n "${ROLLBACK_METADATA_TEMP:-}" ]; then
      rm -f -- "$ROLLBACK_METADATA_TEMP"
    fi
  }
  trap cleanup_rollback_secrets EXIT
  trap 'exit 130' INT
  trap 'exit 143' TERM

  read -r -p 'Media Gateway base URL: ' MEDIA_GATEWAY_BASE_URL
  read -r -s -p 'Media Gateway internal token: ' MEDIA_GATEWAY_INTERNAL_TOKEN
  echo

  if [ -z "$MEDIA_GATEWAY_BASE_URL" ] || \
     [ -z "$MEDIA_GATEWAY_INTERNAL_TOKEN" ]; then
    echo 'Media Gateway rollback credentials must not be empty.' >&2
    exit 1
  fi

  FCM_ENABLED_VALUE="$(
    sed -n 's/^[[:space:]]*FCM_ENABLED[[:space:]]*=[[:space:]]*//p' \
      /run/secrets/ssabangpalbang.env \
      | tail -n 1 \
      | tr -d '\r[:space:]' \
      | tr '[:upper:]' '[:lower:]'
  )"

  case "${FCM_ENABLED_VALUE:-false}" in
    true)
      if [ -z "${FCM_SERVICE_ACCOUNT_JSON:-}" ]; then
        echo 'FCM rollback credential must be injected before recovery.' >&2
        exit 1
      fi
      export FCM_SERVICE_ACCOUNT_JSON
      ;;
    false) ;;
    *)
      echo 'FCM_ENABLED must be true or false.' >&2
      exit 1
      ;;
  esac

  export MEDIA_GATEWAY_BASE_URL MEDIA_GATEWAY_INTERNAL_TOKEN

  JENKINS_CONTAINER="${JENKINS_CONTAINER:-jenkins}"
  LEGACY_METADATA=/var/lib/ssabangpalbang/deployments/last-success.env
  JENKINS_METADATA=/var/jenkins_home/ssabangpalbang/deployments/last-success.env
  ROLLBACK_METADATA_TEMP=""

  if [ -r "$LEGACY_METADATA" ]; then
    LAST_BACKEND_IMAGE="$(
      sed -n 's/^BACKEND_IMAGE=//p' "$LEGACY_METADATA"
    )"
  elif docker inspect "$JENKINS_CONTAINER" >/dev/null 2>&1; then
    if [ "$(docker inspect --format '{{.State.Running}}' "$JENKINS_CONTAINER")" = true ]; then
      LAST_BACKEND_IMAGE="$(
        docker exec "$JENKINS_CONTAINER" \
          sed -n 's/^BACKEND_IMAGE=//p' "$JENKINS_METADATA"
      )"
    else
      ROLLBACK_METADATA_TEMP="$(mktemp)"
      docker cp \
        "$JENKINS_CONTAINER:$JENKINS_METADATA" \
        "$ROLLBACK_METADATA_TEMP"
      LAST_BACKEND_IMAGE="$(
        sed -n 's/^BACKEND_IMAGE=//p' "$ROLLBACK_METADATA_TEMP"
      )"
    fi
  else
    echo 'Jenkins container and legacy deployment metadata are unavailable.' >&2
    echo 'Mount the persistent Jenkins home volume in a temporary container and recover last-success.env first.' >&2
    exit 1
  fi

  if [[ ! "$LAST_BACKEND_IMAGE" =~ ^ssabangpalbang-backend:[0-9a-f]{40}-[0-9]+$ ]]; then
    echo 'Invalid BACKEND_IMAGE in last-success.env.' >&2
    exit 1
  fi

  docker image inspect "$LAST_BACKEND_IMAGE" >/dev/null

  BACKEND_IMAGE="$LAST_BACKEND_IMAGE" \
  docker compose \
    --env-file /run/secrets/ssabangpalbang.env \
    -f infra/docker-compose.prod.yml \
    up -d --no-deps --no-build --force-recreate app

  APP_CONTAINER="$(
    BACKEND_IMAGE="$LAST_BACKEND_IMAGE" \
      docker compose \
        --env-file /run/secrets/ssabangpalbang.env \
        -f infra/docker-compose.prod.yml \
        ps --all -q app \
      | while IFS= read -r CANDIDATE_CONTAINER
        do
          [ -n "$CANDIDATE_CONTAINER" ] || continue
          docker inspect \
            --format '{{.Created}} {{.Id}}' \
            "$CANDIDATE_CONTAINER" 2>/dev/null || true
        done \
      | sort -r \
      | awk 'NR == 1 { print $2 }'
  )"

  test -n "$APP_CONTAINER"

  DEPLOYED_BACKEND_IMAGE="$(
    docker inspect --format '{{.Config.Image}}' "$APP_CONTAINER"
  )"

  if [ "$DEPLOYED_BACKEND_IMAGE" != "$LAST_BACKEND_IMAGE" ]; then
    echo 'Recovered container does not use the recorded backend image.' >&2
    exit 1
  fi

  APP_HEALTH=""
  for ATTEMPT in $(seq 1 30); do
    APP_HEALTH="$(
      docker inspect \
        --format '{{if .State.Health}}{{.State.Health.Status}}{{else}}{{.State.Status}}{{end}}' \
        "$APP_CONTAINER"
    )"

    [ "$APP_HEALTH" = healthy ] && break
    case "$APP_HEALTH" in
      unhealthy|exited|dead) exit 1 ;;
    esac

    [ "$ATTEMPT" -lt 30 ] && sleep 10
  done

  [ "$APP_HEALTH" = healthy ]
  bash infra/scripts/prod-smoke-test.sh
}

rollback_backend
)
```

`docker compose down`과 `docker compose down -v`는 사용하지 않는다.

## 9. Volume 유지 증빙

배포 전후 다음 값이 동일한지 기록한다.

```bash
docker volume inspect \
  ssabangpalbang-prod_postgres-prod-data \
  --format 'name={{.Name}} mountpoint={{.Mountpoint}}'

docker volume inspect \
  ssabangpalbang-prod_redis-prod-data \
  --format 'name={{.Name}} mountpoint={{.Mountpoint}}'

docker volume inspect \
  ssabangpalbang-prod_kafka-prod-data \
  --format 'name={{.Name}} mountpoint={{.Mountpoint}}'

docker volume inspect \
  ssabangpalbang-prod_ai-model-cache \
  --format 'name={{.Name}} mountpoint={{.Mountpoint}}'
```

성공 Build 번호, Git SHA, 이미지명, 외부 Smoke Test, Nginx 인증서 만료일,
WebSocket STOMP 결과와 함께 Jira 완료 증빙으로 보관한다.
