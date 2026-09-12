- source_spec: `docs/spec-automatic-token-reissue-protected-api.md`
  summary: 로그인 세션별로 TanStack Query의 회원·커뮤니티 캐시를 격리하거나 로그아웃 시 보호 캐시를 제거한다.
  evidence: 현재 회원 프로필 및 커뮤니티 query key에 sessionVersion 또는 회원 식별자가 없어, 로그아웃 후 다른 계정으로 로그인하면 staleTime 동안 이전 계정의 캐시가 재사용될 수 있다.

- source_spec: `docs/spec-automatic-token-reissue-protected-api.md`
  summary: Access Token과 Refresh Token을 원자적으로 저장하거나 부분 저장 실패를 복구한다.
  evidence: tokenStorage.save가 Access Token 저장 후 Refresh Token을 순차 저장하므로 두 번째 쓰기 실패 시 회전된 토큰 쌍이 불일치할 수 있다.

- source_spec: `docs/spec-post-like.md`
  summary: 게시글 상세 화면에 댓글 작성 API를 연결하고 프론트 입력 길이를 서버의 1000자 계약과 맞춘다.
  evidence: 동시 커밋된 댓글 API와 달리 상세 화면은 준비 중 알림만 표시하며 TextInput은 300자로 제한한다.

- source_spec: `docs/spec-post-like.md`
  summary: 댓글 콘텐츠의 HTML·제어 문자 처리 정책을 API 문서와 구현 사이에서 일치시키고 안전한 렌더링 경계를 검증한다.
  evidence: 현재 서비스는 HTML과 혼합된 FORMAT·CONTROL 문자를 그대로 저장하지만 문서는 HTML 이스케이프를 명시한다.

- source_spec: `docs/spec-post-like.md`
  summary: 댓글 생성 요청의 후행 JSON 토큰과 비활성 회원 오류 계약을 별도 댓글 기능 범위에서 검증한다.
  evidence: 커스텀 deserializer가 첫 객체 종료 시 반환하고 댓글 API 문서에는 서비스가 반환하는 MEMBER_NOT_FOUND 404가 빠져 있다.

- source_spec: `docs/spec-media-lambda-internal-token-auth.md`
  summary: `/media/upload/verify`를 재호출·동시 호출·Presigned PUT 재사용에도 안전한 멱등 승격 작업으로 보완한다.
  evidence: 현재 첫 성공 후 pending 객체를 삭제해 재호출이 404가 되며, 아직 유효한 PUT URL로 pending 객체를 다시 만든 뒤 같은 final key를 덮어쓸 수 있다.

- source_spec: `docs/spec-media-lambda-internal-token-auth.md`
  summary: pending 업로드의 최대 크기와 수명, 삭제 실패 복구를 S3 정책과 정리 작업으로 강제한다.
  evidence: Presigned PUT은 선언한 크기를 서명 조건으로 제한하지 않고, 완료하지 않거나 safeDelete가 실패한 pending 객체를 제거하는 수명 주기 규칙이 저장소에 없다.

- source_spec: `docs/spec-media-lambda-internal-token-auth.md`
  summary: 업로드 파일의 실제 형식을 magic bytes 또는 안전한 디코딩으로 검증한 뒤 최종 객체로 승격한다.
  evidence: 현재 HeadObject의 Content-Type과 크기만 검사하므로 허용 MIME 타입으로 위장한 임의 바이트를 최종 미디어로 저장할 수 있다.

- source_spec: `docs/spec-media-lambda-internal-token-auth.md`
  summary: 다운로드 URL 발급 전에 최종 S3 객체 존재를 확인하고 없는 객체를 Gateway 404로 매핑한다.
  evidence: 현재 관리 경로 형식만 통과하면 존재하지 않는 객체에도 Presigned GET URL을 발급해 실제 다운로드 시점까지 실패가 지연된다.

- source_spec: `docs/spec-media-lambda-internal-token-auth.md`
  summary: Media Lambda의 AWS SDK 의존성을 고정한 배포 번들과 네 경로 계약 테스트를 CI에 연결한다.
  evidence: 현재 소스에는 Lambda 전용 package manifest와 lockfile이 없고 인증 테스트도 Jenkins에서 자동 실행되지 않아 런타임 SDK 변경이나 S3 경로 회귀를 배포 전에 잡지 못한다.

- source_spec: `docs/spec-inf-006-nginx-https.md`
  summary: FastAPI와 커스텀 PostgreSQL 이미지에도 Build 단위 불변 태그와 호환 가능한 다중 서비스 복구 단위를 적용한다.
  evidence: INF-006은 Spring Boot Backend 이미지만 최근 성공 Build로 복구하도록 범위를 고정했지만 기존 Pipeline은 AI와 PostgreSQL 이미지도 함께 빌드하므로, 두 이미지가 변경되는 배포에는 별도의 버전 메타데이터와 호환성 검증이 필요하다.
