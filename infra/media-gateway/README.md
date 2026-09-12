# GCP Media Gateway

Spring Boot와 FastAPI가 사용하는 기존 `/media/**` 내부 계약을 유지하면서,
private Google Cloud Storage bucket에 Signed PUT/GET URL을 발급하는 경량 Node.js
서비스다. 외부 계약의 `s3Key`, `uploadKey`, `objectKey` 이름은 호환성을 위해
그대로 유지하며 내부에서는 GCS object name을 뜻한다.

과거 운영은 AWS API Gateway + Lambda + S3 구조였지만 해당 인프라는 현재
사용할 수 없다. 이 컴포넌트의 신규 목표 구조는 GCP Compute Engine의
`media-gateway` 컨테이너와 private GCS bucket이다.

## Runtime

- Node.js 22
- `@google-cloud/storage`
- Compute Engine service account의 Application Default Credentials(ADC)
- 외부 포트를 공개하지 않는 Docker 내부 서비스
- `GET /health`만 인증 없이 상태를 반환하고, 모든 `/media/**` 요청은 정확한
  `Authorization: Bearer <MEDIA_GATEWAY_INTERNAL_TOKEN>`을 요구한다.

`GET /health`는 container와 Node.js process의 liveness만 확인한다. 이 endpoint가
성공해도 ADC 인증, GCS bucket IAM, IAM Credentials `signBlob`, V4 Signed URL
생성 가능 여부는 증명되지 않는다. Production 활성화 전 실제 private GCS
object를 사용해 다음 전체 smoke test를 통과해야 실제 GCS 연동이 완료된 것으로
판단한다.

```text
upload-url
→ Signed PUT
→ upload/verify
→ download-url
→ Signed GET
→ delete
```

## 환경변수

| 이름 | 필수 | 기본값 | 용도 |
| --- | --- | --- | --- |
| `MEDIA_GATEWAY_INTERNAL_TOKEN` | 예 | 없음 | Spring/FastAPI와 공유하는 32자 이상 printable ASCII 내부 token |
| `MEDIA_GCS_BUCKET` | 예 | 없음 | private GCS bucket 이름 |
| `GOOGLE_CLOUD_PROJECT` | 아니요 | ADC project | GCP project ID |
| `PORT` | 아니요 | `8080` | 내부 HTTP port |
| `MEDIA_UPLOAD_URL_TTL_SECONDS` | 아니요 | `900` | Signed PUT URL TTL |
| `MEDIA_DOWNLOAD_URL_TTL_SECONDS` | 아니요 | `300` | Signed GET URL TTL |

실제 token이나 service account JSON은 저장소와 Docker image에 넣지 않는다.

## Object key와 검증

신규 업로드는 다음 pending object name을 사용한다.

```text
pending/<usage-prefix>/YYYY/MM/DD/<uuid>.<extension>
```

검증 성공 시 generation precondition을 사용해 아래 final object로 복사한 뒤,
확인한 pending generation만 삭제한다.

```text
<usage-prefix>/YYYY/MM/DD/<uuid>.<extension>
```

prefix는 `field-photo`, `stt-audio`, `chat-image`, `post-attachment`다.
MIME과 크기 정책은 Spring `MediaFilePolicy`와 동일하다. 검증은 GCS metadata의
실제 Content-Type, size, etag를 사용한다. 이미 승격된 object에 대한 verify
재호출은 같은 final key를 반환하며, delete는 object가 이미 없어도
`deleted=true`를 반환해 STT cleanup 재시도를 멱등하게 만든다.

pending → final 승격은 `MediaGatewayClient`, `MediaMutationService`와 기존
테스트에서 prepare key와 verify 응답 final key를 별도로 다루는 동작에 맞춘
현재 구현 선택이다. 계약상 유일한 구현이라고 단정하지 않으며 최종 검수 대상이다.

V4 Signed PUT은 기존 Frontend의 `PUT` 계약을 유지해야 하므로 업로드 byte 수를
서명 조건으로 제한하지 못한다. verify에서 MIME/크기가 다른 pending generation을
즉시 삭제하고, 업로드 후 complete를 호출하지 않은 객체까지 회수하려면 bucket에
다음 lifecycle rule을 필수로 적용한다.

```json
{
  "rule": [
    {
      "action": { "type": "Delete" },
      "condition": { "age": 1, "matchesPrefix": ["pending/"] }
    }
  ]
}
```

위 정책은 pending 객체만 1일 후 제거하며 final media retention에는 영향을 주지
않는다. 운영 bucket의 기존 lifecycle rule이 있다면 덮어쓰지 말고 병합한다.

## 로컬 검증

```bash
npm ci
npm test
docker build -t ssabangpalbang-media-gateway:local .
```

ADC와 테스트용 private bucket이 준비된 환경에서만 실제 Signed URL 통합 검증을
수행한다. 이 저장소 작업은 bucket 생성이나 운영 배포를 실행하지 않는다.

## GCP IAM

권장 방식은 JSON key 없이 Media Gateway 전용 service account를 Compute Engine
VM에 연결하고, container가 ADC로 이 attached service account를 사용하는 것이다.
JSON service account key를 생성하거나 repository 또는 container에 mount하지
않는다.

1. bucket 범위에서 Gateway service account에 `roles/storage.objectUser`를
   부여한다. Gateway의 metadata 조회, create/copy, read, delete와 Signed
   PUT/GET에 인코딩되는 권한이 필요하다. 조직에서 custom role을 사용한다면
   최소 `storage.objects.get`, `storage.objects.create`,
   `storage.objects.delete`를 포함하고 rewrite/copy 동작을 실제로 검증한다.
2. attached service account가 Signed URL 생성에 사용되는 principal이다. 이
   principal은 자기 자신을 impersonate해 `signBlob`을 호출할 수 있어야 하며,
   핵심 permission은 `iam.serviceAccounts.signBlob`이다. Google Cloud 공식
   가이드에 따라 `roles/iam.serviceAccountTokenCreator`를 해당 service
   account 자신에게 principal로 부여하되, 리소스 범위는 그 service account가
   속한 project로 설정한다.
3. IAM Service Account Credentials API를 활성화한다.
4. Signed PUT/GET이 수행할 GCS object 권한과 `signBlob` 권한은 서로 별도로
   필요하다.
5. VM access scope는 `cloud-platform`을 사용하고 실제 접근은 위 IAM role로
   제한한다.

`Storage Object` 권한만으로 ADC 기반 V4 Signed URL 서명이 보장되지는 않는다.
런타임은 private key 대신 IAM Credentials `signBlob`을 호출하므로 별도의
서명 권한이 반드시 필요하다.

## 네트워크와 CORS

Gateway 내부 API는 Nginx에 reverse proxy하지 않고 Spring/FastAPI가
`http://media-gateway:8080`으로 호출한다. Android 단말은 Gateway가 반환한
`https://storage.googleapis.com/...` Signed URL에 직접 접근한다.

React Native native networking에는 bucket CORS가 필요하지 않으므로 기본 운영
구성에는 CORS 정책을 추가하지 않는다. 향후 브라우저 클라이언트를 지원할 때만
실제 HTTPS web origin, `PUT`/`GET`, `Content-Type`으로 한정한 bucket CORS를
별도 승인 후 적용한다. `origin: ["*"]`는 사용하지 않는다.

## 운영 연결 주의

실제 GCP 운영 Compose는 repository 밖 GCP 서버에 별도로 관리된다. 이 작업에서는
repository의 과거 `infra/docker-compose.prod.yml`을 수정하거나 실행하지 않고,
GCP Compose 파일을 새로 만들지도 않는다. 구체적인 운영 반영 항목은 repository
밖 검수 파일의 production 배포 절차 초안에서만 다룬다.
