# 싸방팔방 API 명세서

> 최종 갱신: 2026-07-31
> 관리 방식: GitLab Merge Request
> 원본: Notion Markdown export `9d041b88-a44c-4a30-842a-90b092f3e30b_ExportBlock-007cbcb7-3b0c-4f9a-a970-9998e335980c.zip`
> 원본 SHA-256: `92D51D4339A9BDD6FDE49339E120CA8B0208B7D6D7AC13A792F13DE5CF85F935`

이 문서는 싸방팔방 API의 Git 정본이다. API 계약을 변경할 때는 구현 코드, 테스트, Swagger/OpenAPI와 이 문서를 같은 MR에서 함께 수정한다.

## 공통 계약

### Base URL

```text
/api/v1
```

### 공통 응답 구조

모든 REST API는 다음의 평면 응답 구조를 사용한다.

```json
{
  "success": true,
  "code": "DOMAIN_SUCCESS_CODE",
  "message": "처리 결과 메시지",
  "data": {},
  "timestamp": "2026-07-28T15:30:00+09:00"
}
```

- 필드 순서는 `success`, `code`, `message`, `data`, `timestamp`다.
- 반환할 데이터가 없어도 `data: null`을 포함한다.
- `timestamp`는 `Asia/Seoul` 기준 ISO-8601 오프셋 형식이다.
- 오류 응답도 같은 구조를 사용하며 별도의 중첩 `error` 객체를 만들지 않는다.

### JWT 인증 정책

인증은 기본 차단 방식이다. 아래 공개 허용 목록을 제외한 모든 API는 유효한 Access Token이 필요하다.

| Method | URI | 설명 |
| --- | --- | --- |
| `POST` | `/api/v1/auth/signup` | 회원가입 |
| `POST` | `/api/v1/auth/login` | 일반 로그인 |
| `POST` | `/api/v1/auth/social-login` | 소셜 로그인 |
| `POST` | `/api/v1/auth/social-signup` | 소셜 회원가입 완료 |
| `POST` | `/api/v1/auth/reissue` | Refresh Token을 이용한 토큰 재발급 |
| `POST` | `/api/v1/auth/password-reset/request` | 비밀번호 재설정 코드 발송 요청 |
| `POST` | `/api/v1/auth/password-reset/confirm` | 비밀번호 재설정 코드 검증 및 새 비밀번호 설정 |
| `POST` | `/api/v1/auth/password-reset/verify` | 비밀번호 재설정 코드 검증(사전 확인) |
| `GET` | `/api/v1/auth/check-email` | 이메일 중복 확인 |
| `GET` | `/api/v1/auth/check-nickname` | 닉네임 중복 확인 |
| `GET` | `/report/{reportId}` | 카카오톡 등 외부 공유 링크의 앱 연결 페이지. 리포트 데이터는 반환하지 않음 |

운영·개발 지원 경로인 `GET /actuator/health`, `/swagger-ui.html`, `/swagger-ui/**`, `/v3/api-docs/**`도 인증 없이 접근할 수 있다.

보호된 API는 다음 헤더를 사용한다.

```http
Authorization: Bearer {accessToken}
```

- Access Token이 없거나 올바르지 않으면 `401 AUTH_ACCESS_TOKEN_INVALID`을 반환한다.
- 만료된 Access Token은 `401 AUTH_ACCESS_TOKEN_EXPIRED`를 반환한다.
- Refresh Token은 일반 보호 API의 인증 수단으로 사용할 수 없다.
- 소유권, 스터디장 여부, 현재 참여 상태, 원본 근거 접근 권한 등 세부 권한은 각 API의 서비스 정책으로 추가 검증한다.

### 회원 공개 정보 정책

- `ageGroup`은 회원이 `ageGroupPublicAgreed=true`로 설정한 경우에만 공개하고, 그렇지 않으면 `null`을 반환한다.
- `interestRegion`은 공개 프로필과 팔로잉 목록 응답에 포함하지 않는다.
- 이메일, 임장 목적, 혼인 여부, 차량·자녀 정보, 우선순위 등 개인화 정보는 다른 회원에게 공개하지 않는다.

### 리포트 정책

- 모든 리포트 API는 인증이 필요하다.
- `GET /report/{reportId}`는 리포트 API가 아니라 앱 연결용 공개 HTML 페이지다. 리포트 존재 여부나 내용을 조회·노출하지 않는다.
- 완료된 리포트 요약은 로그인 회원이 조회할 수 있다.
- `report.public_id`와 `report.visibility`는 Flyway `V3`에서 제거되었다.
- API 응답에서 `publicId`, `visibility`, 익명 공유 URL을 사용하지 않는다.
- 리포트 식별에는 내부 숫자형 `reportId`를 사용한다.
- `field_participant`는 임장 참여와 원본 근거 접근 권한을 판정하기 위해 유지한다.
- 리포트 요약 조회 권한이 있어도 현장 기록, 사진, 개인 메모, STT 원문 등 근거 자료를 볼 수 있는 것은 아니다.

### 페이지네이션

- 페이지 방식 API는 보통 `page=0`, `size=20`을 기본값으로 사용한다.
- 페이지 번호는 0 이상, 페이지 크기는 일반적으로 1~100 범위다.
- 페이지 응답은 `content`, `totalElements`, `page`, `size`, `totalPages`를 사용한다.
- 커서 방식 API는 각 상세 명세의 `cursor`, `nextCursor`, `hasNext` 계약을 따른다.

### 변경 관리

1. API 계약 변경 전 정확한 Method와 URI를 확인한다.
2. `docs/API.md`, 구현 코드, 테스트, Swagger/OpenAPI를 함께 변경한다.
3. 변경 브랜치에서 테스트 후 `develop` 대상 MR을 생성한다.
4. 리뷰어는 `최태선`으로 지정한다.
5. DB 변경은 기존 Flyway 파일을 수정하지 않고 다음 버전 마이그레이션을 추가한다.

## 활성 API 목록

| Method | URI | API | 상태 | 담당자 |
| --- | --- | --- | --- | --- |
| `POST` | `/api/v1/auth/signup` | 회원가입 | 완료 | 박재명 |
| `POST` | `/api/v1/auth/login` | 일반 로그인 | 완료 | 박재명 |
| `POST` | `/api/v1/auth/social-login` | 네이버/카카오 간편 로그인 | 완료 | 박재명 |
| `POST` | `/api/v1/auth/social-signup` | 소셜 회원가입 완료 | 완료 | 박재명 |
| `POST` | `/api/v1/auth/reissue` | 토큰 재발급 | 완료 | 박재명 |
| `POST` | `/api/v1/auth/logout` | 로그아웃 | 완료 | 박재명 |
| `POST` | `/api/v1/auth/password-reset/request` | 비밀번호 재설정 코드 발송 요청 | 진행 중 | 박재명 |
| `POST` | `/api/v1/auth/password-reset/confirm` | 비밀번호 재설정 코드 검증 및 새 비밀번호 설정 | 진행 중 | 박재명 |
| `POST` | `/api/v1/auth/password-reset/verify` | 비밀번호 재설정 코드 검증(사전 확인) | 완료 | 박재명 |
| `GET` | `/api/v1/auth/check-email` | 이메일 중복 확인 | 완료 | 박재명 |
| `GET` | `/api/v1/auth/check-nickname` | 닉네임 중복 확인 | 완료 | 박재명 |
| `PUT` | `/api/v1/members/me/onboarding` | 온보딩 정보 저장 | 완료 | 박재명 |
| `GET` | `/api/v1/members/me` | 내 프로필 조회 | 완료 | 박재명 |
| `PATCH` | `/api/v1/members/me` | 내 프로필 수정 | 완료 | 박재명 |
| `DELETE` | `/api/v1/members/me` | 회원 탈퇴 | 완료 | 박재명 |
| `GET` | `/api/v1/members/{memberId}` | 다른 사용자 공개 프로필 조회 | 완료 | 박재명 |
| `PUT` | `/api/v1/members/{memberId}/follow` | 사용자 팔로우 | 완료 | 박재명 |
| `DELETE` | `/api/v1/members/{memberId}/follow` | 사용자 팔로우 해제 | 완료 | 박재명 |
| `GET` | `/api/v1/members/me/followings` | 내 팔로잉 목록 조회 | 완료 | 박재명 |
| `GET` | `/api/v1/members/me/notification-settings` | 알림 수신 설정 조회 | 완료 | 김윤석 |
| `PATCH` | `/api/v1/members/me/notification-settings` | 알림 수신 설정 수정 | 완료 | 김윤석 |
| `PUT` | `/api/v1/members/me/devices/{deviceId}/fcm-token` | 기기별 FCM 토큰 등록·갱신 | 완료 | 김윤석 |
| `DELETE` | `/api/v1/members/me/devices/{deviceId}/fcm-token` | 기기별 FCM 토큰 삭제 | 완료 | 김윤석 |
| `POST` | `/api/v1/members/me/devices/{deviceId}/test-fcm-push` | 현재 기기 FCM 테스트 알림 5초 지연 예약 | 완료 | 김윤석 |
| `GET` | `/api/v1/members/me/studies` | 내 스터디 목록 조회 | 진행 중 | 박재명 |
| `GET` | `/api/v1/members/me/reports` | 내 리포트 목록 조회 | 완료 | 박재명 |
| `GET` | `/api/v1/members/me/posts` | 내가 작성한 게시글 목록 조회 | 시작 전 | 김윤석 |
| `GET` | `/api/v1/members/me/favorite-apartments` | 내가 찜한 아파트 목록 조회 | 완료 | 박재명 |
| `GET` | `/api/v1/home` | 홈 화면 통합 조회 | 완료 | 박재명 |
| `PATCH` | `/api/v1/notifications/{notificationId}/read` | 알림 한 건 읽음 처리 | 진행 중 | 김윤석 |
| `GET` | `/api/v1/regions/districts` | 서울 구 목록 조회 | 완료 | 박재명 |
| `GET` | `/api/v1/members/me/comments` | 내가 작성한 댓글 목록 조회 | 시작 전 | 김윤석 |
| `GET` | `/api/v1/studies/{studyId}/field-visit` | 임장 세션 상태 조회 | 완료 | 윤다인 |
| `POST` | `/api/v1/studies/{studyId}/field-visit/start` | GPS 검증 후 임장 시작 | 완료 | 최태선 |
| `POST` | `/api/v1/studies/{studyId}/field-visit/route` | 체크리스트 기반 추천 경로 생성 | 완료 | 윤다인 |
| `GET` | `/api/v1/studies/{studyId}/field-visit/route` | 추천 경로 조회 | 완료 | 윤다인 |
| `GET` | `/api/v1/members/me/visit-calendar` | 월별 임장 달력 조회 | 완료 | 박재명 |
| `POST` | `/api/v1/posts` | 커뮤니티 게시글 작성 | 완료 | 김윤석 |
| `GET` | `/api/v1/posts/{postId}` | 커뮤니티 게시글 상세 조회 | 완료 | 김윤석 |
| `PATCH` | `/api/v1/posts/{postId}` | 커뮤니티 게시글 수정 | 완료 | 김윤석 |
| `DELETE` | `/api/v1/posts/{postId}` | 커뮤니티 게시글 삭제 | 완료 | 김윤석 |
| `POST` | `/api/v1/posts/{postId}/comments` | 댓글 작성 | 완료 | 김윤석 |
| `PATCH` | `/api/v1/comments/{commentId}` | 댓글 수정 | 완료 | 김윤석 |
| `DELETE` | `/api/v1/comments/{commentId}` | 댓글 삭제 | 완료 | 김윤석 |
| `PUT` | `/api/v1/posts/{postId}/like` | 게시글 좋아요 | 완료 | 김윤석 |
| `DELETE` | `/api/v1/posts/{postId}/like` | 게시글 좋아요 해제 | 완료 | 김윤석 |
| `PUT` | `/api/v1/studies/{studyId}/field-visit/checklist/answers` | 체크리스트 답변 일괄 저장 | 완료 | 윤다인 |
| `POST` | `/api/v1/studies/{studyId}/field-visit/checklist/generate` | 개인 맞춤 체크리스트 생성 | 완료 | 윤다인 |
| `GET` | `/api/v1/studies/{studyId}/field-visit/checklist` | 내 체크리스트 조회 | 완료 | 윤다인 |
| `GET` | `/api/v1/studies/{studyId}/field-visit/checklist/generate/status` | 개인 맞춤 체크리스트 생성 상태 조회 | 완료 | 김윤석 |
| `GET` | `/api/v1/studies/{studyId}/field-visit/participants` | 참여자별 임장 상태 조회 | 완료 | 윤다인 |
| `GET` | `/api/v1/notifications` | 알림 목록 조회 | 진행 중 | 김윤석 |
| `PATCH` | `/api/v1/notifications/read-all` | 알림 전체 읽음 처리 | 진행 중 | 김윤석 |
| `GET` | `/api/v1/apartments/{apartmentId}/chatbot/conversations/{conversationId}/messages` | 아파트 챗봇 대화 이력 조회 | 완료 | 윤다인 |
| `POST` | `/api/v1/studies/{studyId}/field-visit/finish-requests` | 미종료 참여자 종료 요청 | 시작 전 | 윤다인 |
| `POST` | `/api/v1/studies/{studyId}/field-visit/finish` | 개인 임장 종료 | 완료 | 윤다인 |
| `POST` | `/api/v1/studies/{studyId}/field-visit/finish/cancel` | 개인 임장 종료 취소 | 완료 | 윤다인 |
| `POST` | `/api/v1/studies/{studyId}/field-visit/stt/{sttId}/retry` | 실패한 STT 재처리 요청 | 완료 | 김윤석 |
| `GET` | `/api/v1/studies/{studyId}/field-visit/stt/{sttId}` | STT 진행 상태/결과 조회 | 완료 | 김윤석 |
| `POST` | `/api/v1/studies/{studyId}/field-visit/stt` | STT 변환 요청 | 완료 | 김윤석 |
| `DELETE` | `/api/v1/studies/{studyId}/field-visit/records/{recordId}` | 현장 기록 삭제 | 완료 | 윤다인 |
| `PATCH` | `/api/v1/studies/{studyId}/field-visit/records/{recordId}` | 현장 기록 수정 | 완료 | 윤다인 |
| `POST` | `/api/v1/studies/{studyId}/field-visit/records` | 현장 기록 저장 | 완료 | 윤다인 |
| `GET` | `/api/v1/studies/{studyId}/field-visit/records` | 현장 기록 목록 조회 | 완료 | 윤다인 |
| `GET` | `/api/v1/apartments/{apartmentId}/reports` | 아파트별 완료 리포트 목록 조회 | 완료 | 최태선 |
| `GET` | `/api/v1/apartments` | 아파트 키워드·현재 위치 주변 검색 | 완료 | 최태선 |
| `GET` | `/api/v1/regions/districts/{districtCode}/dongs` | 선택한 구의 동 목록 조회 | 완료 | 박재명 |
| `GET` | `/api/v1/apartments/{apartmentId}/studies` | 아파트별 모집 스터디 목록 조회 | 완료 | 최태선 |
| `POST` | `/api/v1/apartments/{apartmentId}/chatbot/conversations` | 아파트 챗봇 대화 시작 | 완료 | 윤다인 |
| `POST` | `/api/v1/studies/{studyId}/field-visit/close` | 스터디장 전체 임장 마감 | 완료 | 윤다인 |
| `POST` | `/api/v1/studies/{studyId}/field-visit/close-votes` | 과반수 동의 기반 전체 임장 종료 요청 | 완료 | 윤다인 |
| `GET` | `/api/v1/posts` | 커뮤니티 게시글 목록/검색 | 완료 | 김윤석 |
| `GET` | `/api/v1/apartments/bounds` | 지도 가시 영역 아파트 조회 | 완료 | 최태선 |
| `GET` | `/api/v1/apartments/districts/summary` | 자치구별 아파트 집계 조회 | 완료 | 최태선 |
| `GET` | `/api/v1/apartments/{apartmentId}/transactions` | 아파트 최근 실거래 목록 조회 | 완료 | 최태선 |
| `GET` | `/api/v1/apartments/{apartmentId}` | 아파트 상세 조회 | 완료 | 최태선 |
| `GET` | `/api/v1/posts/{postId}/comments` | 댓글 목록 조회 | 완료 | 김윤석 |
| `DELETE` | `/api/v1/apartments/{apartmentId}/favorite` | 아파트 찜 해제 | 완료 | 최태선 |
| `PUT` | `/api/v1/apartments/{apartmentId}/favorite` | 아파트 찜 | 완료 | 최태선 |
| `POST` | `/api/v1/apartments/{apartmentId}/chatbot/conversations/{conversationId}/messages` | 아파트 챗봇 질문 전송 | 완료 | 윤다인 |
| `GET` | `/api/v1/reports/{reportId}/status` | 리포트 생성 상태 조회 | 완료 | 박재명 |
| `POST` | `/internal/v1/reports/acquire` | AI 리포트 create-or-get·처리권 획득 | 완료 | 박재명 |
| `PATCH` | `/internal/v1/reports/{reportId}/progress` | AI 리포트 진행 단계 저장·Lease 갱신 | 완료 | 박재명 |
| `GET` | `/internal/v1/reports/{reportId}/input` | AI-004 정규화 입력 원본 조회 | 완료 | 박재명 |
| `PUT` | `/internal/v1/reports/{reportId}/complete` | AI 리포트 결과·근거 완료 저장 | 완료 | 박재명 |
| `PUT` | `/internal/v1/reports/{reportId}/fail` | AI 리포트 실패 정보 저장 | 완료 | 박재명 |
| `POST` | `/api/v1/reports/{reportId}/retry` | 실패한 리포트 재생성 요청 | 완료 | 박재명 |
| `GET` | `/api/v1/reports/{reportId}/evidences/{sourceId}` | 리포트 근거 원문 조회 | 완료 | 박재명 |
| `GET` | `/api/v1/reports/{reportId}/evidences` | 리포트 근거 목록 조회 | 완료 | 박재명 |
| `GET` | `/api/v1/reports/{reportId}` | 리포트 상세 조회 | 완료 | 박재명 |
| `GET` | `/report/{reportId}` | 리포트 공유 앱 연결 페이지 | 완료 | 박재명 |
| `GET` | `/api/v1/studies/{studyId}/chat/messages` | 스터디 채팅 이력 조회 | 완료 | 윤다인 |
| `PATCH` | `/api/v1/studies/{studyId}/chat/read` | 스터디 채팅 읽음 처리 | 완료 | 윤다인 |
| `GET` | `/api/v1/studies/{studyId}/chat/unread-count` | 읽지 않은 스터디 채팅 수 조회 | 완료 | 윤다인 |
| `DELETE` | `/api/v1/studies/{studyId}/chat/messages/{messageId}` | 스터디 채팅 메시지 삭제 | 완료 | 윤다인 |
| `PATCH` | `/api/v1/studies/{studyId}/chat/messages/{messageId}` | 스터디 채팅 메시지 수정 | 완료 | 윤다인 |
| `GET` | `/api/v1/studies/{studyId}/chat/notification-settings` | 스터디 채팅 푸시 알림 설정 조회 | 완료 | 김윤석 |
| `PATCH` | `/api/v1/studies/{studyId}/chat/notification-settings` | 스터디 채팅 푸시 알림 설정 변경 | 완료 | 김윤석 |
| `POST` | `/api/v1/media/presigned-urls` | S3 업로드 URL 발급 | 완료 | 김윤석 |
| `POST` | `/api/v1/members/{memberId}/messages` | 팔로잉 사용자에게 MVP 쪽지 전송 | 완료 | 박재명 |
| `PATCH` | `/api/v1/studies/{studyId}/applications/{applicationId}/reject` | 스터디 신청 거절 | 완료 | 최태선 |
| `POST` | `/api/v1/studies` | 스터디 생성 | 완료 | 최태선 |
| `PATCH` | `/api/v1/studies/{studyId}/applications/{applicationId}/approve` | 스터디 신청 승인 | 완료 | 최태선 |
| `GET` | `/api/v1/studies/{studyId}/applications` | 신청자 목록 조회 | 완료 | 최태선 |
| `POST` | `/api/v1/studies/{studyId}/applications` | 스터디 신청 | 완료 | 최태선 |
| `DELETE` | `/api/v1/studies/{studyId}` | 스터디 취소 | 완료 | 최태선 |
| `GET` | `/api/v1/studies/{studyId}` | 스터디 모집/홈 상세 조회 | 완료 | 최태선 |
| `PATCH` | `/api/v1/studies/{studyId}` | 스터디 목표·소개 수정 | 완료 | 최태선 |
| `POST` | `/api/v1/studies/{studyId}/notices` | 스터디 공지 등록 | 완료 | 최태선 |
| `DELETE` | `/api/v1/studies/{studyId}/members/{memberId}` | 스터디 멤버 강퇴 | 완료 | 최태선 |
| `GET` | `/api/v1/studies/{studyId}/members` | 스터디 멤버 목록 조회 | 완료 | 최태선 |
| `DELETE` | `/api/v1/studies/{studyId}/members/me` | 스터디 나가기 | 완료 | 최태선 |
| `POST` | `/api/v1/studies/{studyId}/members/{memberId}/reviews` | 같은 스터디 멤버 익명 태그·좋아요 평가 등록 | 진행 중 | 박재명 |
| `GET` | `/api/v1/members/{memberId}/reviews` | 사용자 익명 태그·좋아요 평가 목록 조회 | 진행 중 | 박재명 |
| `GET` | `/api/v1/studies/{studyId}/notices` | 스터디 공지 목록 조회 | 완료 | 최태선 |
| `PATCH` | `/api/v1/studies/{studyId}/recruitment/close` | 스터디 모집 조기 마감 | 완료 | 최태선 |
| `PATCH` | `/api/v1/studies/{studyId}/recruitment/open` | 스터디 모집 재개 | 완료 | 최태선 |
| `PATCH` | `/api/v1/studies/{studyId}/notices/{noticeId}` | 스터디 공지 수정 | 완료 | 최태선 |
| `GET` | `/api/v1/media/{fileId}` | 파일 메타데이터/접근 URL 조회 | 완료 | 김윤석 |
| `DELETE` | `/api/v1/media/{fileId}` | 파일 삭제 | 완료 | 김윤석 |
| `PATCH` | `/api/v1/studies/{studyId}/schedule` | 임장 일정 수정 | 완료 | 최태선 |
| `POST` | `/api/v1/studies/{studyId}/schedule` | 임장 일정 등록 | 완료 | 최태선 |
| `GET` | `/api/v1/studies/{studyId}/schedule` | 임장 일정 조회 | 완료 | 최태선 |
| `DELETE` | `/api/v1/studies/{studyId}/notices/{noticeId}` | 스터디 공지 삭제 | 완료 | 최태선 |
| `POST` | `/api/v1/media/{fileId}/complete` | S3 업로드 완료 처리 | 완료 | 김윤석 |
| `DELETE` | `/api/v1/studies/{studyId}/schedule` | 임장 일정 삭제 | 완료 | 최태선 |
| `GET` | `/api/v1/members/me/favorite-reports` | 내가 찜한 리포트 목록 조회 | 완료 | 박재명 |
| `DELETE` | `/api/v1/reports/{reportId}/favorite` | 리포트 찜 해제 | 완료 | 박재명 |
| `PUT` | `/api/v1/reports/{reportId}/favorite` | 리포트 찜 | 완료 | 박재명 |

## 상세 API 명세

---

## 회원가입

Domain: Auth
Method: POST
Progress: 완료
URI: /api/v1/auth/signup
담당자: 박재명
연동여부: Yes
프론트 담당자: 장선형

로그인 이메일과 비밀번호를 사용하는 일반 회원을 생성한다.

회원가입 직후 기본 캐릭터는 `PALBANG`이며, 온보딩 정보가 없으므로 `onboardingCompleted`는 `false`로 반환한다.

### Request

- Request HTTP Method: `POST`
- URI: `/api/v1/auth/signup`
- 인증 필요: 없음
- Content-Type: `application/json`

#### Request Body

```
{
  "email":"dain@example.com",
  "password":"Ssafy1234",
  "nickname":"루돌푸"
}
```

| 필드 | 타입 | 필수 | 설명 |
| --- | --- | --- | --- |
| `email` | String | Y | 로그인 이메일, 최대 255자 |
| `password` | String | Y | 비밀번호, 8~64자, 영문과 숫자 포함 |
| `nickname` | String | Y | 서비스 닉네임, 최대 50자 |

### 처리 기준

- 이메일의 앞뒤 공백을 제거하고 소문자로 변환한다.
- 이메일과 닉네임은 중복될 수 없다.
- 비밀번호는 단방향 암호화하여 `password_hash`에 저장한다.
- 기본 캐릭터는 `PALBANG`으로 저장한다.
- 회원 상태는 `ACTIVE`로 저장한다.
- `deletedAt`은 `null`로 저장한다.
- 서비스 알림 동의 기본값은 `true`다.
- 광고성 알림 동의 기본값은 `false`다.
- 회원가입 성공 시 Access Token과 Refresh Token을 발급한다.
- `member_preference`가 아직 없으므로 `onboardingCompleted`는 `false`다.
- 탈퇴 회원이 사용하던 이메일·닉네임의 재사용 여부는 회원 탈퇴 정책을 따른다.

### Response

#### 201 Created

```
{
  "success":true,
  "code":"AUTH_SIGNUP_SUCCESS",
  "message":"회원가입이 완료되었습니다.",
  "data": {
    "memberId":1,
    "email":"dain@example.com",
    "nickname":"루돌푸",
    "selectedCharacterId":"PALBANG",
    "onboardingCompleted":false,
    "accessToken":"access-token",
    "refreshToken":"refresh-token"
  },
  "timestamp":"2026-07-24T14:40:00+09:00"
}
```

| 필드 | 타입 | 설명 |
| --- | --- | --- |
| `memberId` | Long | 생성된 회원 ID |
| `email` | String | 회원 이메일 |
| `nickname` | String | 회원 닉네임 |
| `selectedCharacterId` | String | 현재 선택 캐릭터 |
| `onboardingCompleted` | Boolean | 온보딩 완료 여부 |
| `accessToken` | String | API 인증용 Access Token |
| `refreshToken` | String | 토큰 재발급용 Refresh Token |

### Exception

#### 400 Bad Request

```
{
  "success":false,
  "code":"COMMON_INVALID_REQUEST",
  "message":"입력값을 확인해 주세요.",
  "data": {
    "field":"password",
    "reason":"비밀번호는 8자 이상이며 영문과 숫자를 포함해야 합니다."
  },
  "timestamp":"2026-07-24T14:40:00+09:00"
}
```

#### 409 Conflict — 이메일 중복

```
{
  "success":false,
  "code":"AUTH_EMAIL_DUPLICATED",
  "message":"이미 사용 중인 이메일입니다.",
  "data":null,
  "timestamp":"2026-07-24T14:40:00+09:00"
}
```

#### 409 Conflict — 닉네임 중복

```
{
  "success":false,
  "code":"AUTH_NICKNAME_DUPLICATED",
  "message":"이미 사용 중인 닉네임입니다.",
  "data":null,
  "timestamp":"2026-07-24T14:40:00+09:00"
}
```

#### 500 Internal Server Error

```
{
  "success":false,
  "code":"COMMON_INTERNAL_SERVER_ERROR",
  "message":"일시적인 오류가 발생했습니다.",
  "data":null,
  "timestamp":"2026-07-24T14:40:00+09:00"
}
```

### 프론트 처리

```
회원가입 성공
→ Access Token과 Refresh Token 저장
→ 로그인 상태 적용
→ onboardingCompleted = false 확인
→ 온보딩 화면으로 이동
```

---

## 비밀번호 재설정 코드 발송 요청

Domain: Auth
Method: POST
Progress: 진행 중
URI: /api/v1/auth/password-reset/request
담당자: 박재명
연동여부: No

이메일 기반 비밀번호 재설정을 위한 6자리 인증 코드를 발송한다.

계정 존재 여부를 노출하지 않기 위해, 미가입·소셜 전용·탈퇴 계정이더라도 항상 `200`과 동일한 메시지를 반환한다.

### Request

- Request HTTP Method: `POST`
- URI: `/api/v1/auth/password-reset/request`
- 인증 필요: 없음
- Content-Type: `application/json`

#### Request Body

```
{
  "email":"dain@example.com"
}
```

| 필드 | 타입 | 필수 | 설명 |
| --- | --- | --- | --- |
| `email` | String | Y | 재설정 대상 이메일, 최대 255자 |

### 처리 기준

- 이메일의 앞뒤 공백을 제거하고 소문자로 변환해 대상 회원을 식별한다.
- 이메일과 비밀번호로 로그인 가능한 `ACTIVE` 회원인 경우에만 6자리 숫자 코드를 생성한다.
- 코드는 Redis에 이메일 기준 TTL 10분으로 저장하며, 재요청 시 최신 코드로 덮어쓴다.
- 코드 원문은 응답 본문에 절대 포함하지 않는다.
- 같은 이메일로 최근 발송 후 60초 동안은 재발송을 억제하되, 응답은 동일하게 `200`을 유지한다.
- 미가입·소셜 전용(비밀번호 없음)·탈퇴 회원이면 아무 동작 없이 조용히 종료한다.
- 발송 채널은 이메일이며, 로컬/테스트 환경에서는 코드가 서버 로그로 출력된다(운영 로그에는 출력하지 않는다).

### Response

#### 200 OK

```
{
  "success":true,
  "code":"AUTH_PASSWORD_RESET_REQUESTED",
  "message":"입력하신 이메일로 재설정 코드를 보냈어요.",
  "data":null,
  "timestamp":"2026-08-07T14:40:00+09:00"
}
```

### Exception

#### 400 Bad Request

```
{
  "success":false,
  "code":"COMMON_INVALID_REQUEST",
  "message":"입력값을 확인해 주세요.",
  "data": {
    "field":"email",
    "reason":"올바른 이메일 형식이 아닙니다."
  },
  "timestamp":"2026-08-07T14:40:00+09:00"
}
```

---

## 비밀번호 재설정 코드 검증 및 새 비밀번호 설정

Domain: Auth
Method: POST
Progress: 진행 중
URI: /api/v1/auth/password-reset/confirm
담당자: 박재명
연동여부: No

이메일로 받은 재설정 코드를 검증하고 새 비밀번호로 갱신한다.

성공 시 해당 회원의 기존 Refresh Token을 모두 무효화해 기존 로그인 세션을 종료한다.

### Request

- Request HTTP Method: `POST`
- URI: `/api/v1/auth/password-reset/confirm`
- 인증 필요: 없음(코드 소유 증명이 인증을 대체)
- Content-Type: `application/json`

#### Request Body

```
{
  "email":"dain@example.com",
  "code":"123456",
  "newPassword":"Ssafy5678"
}
```

| 필드 | 타입 | 필수 | 설명 |
| --- | --- | --- | --- |
| `email` | String | Y | 재설정 대상 이메일, 최대 255자 |
| `code` | String | Y | 발송받은 6자리 재설정 코드 |
| `newPassword` | String | Y | 새 비밀번호, 8~64자, 영문과 숫자 포함 |

### 처리 기준

- Redis에 저장된 이메일 기준 코드와 일치하는지 검증한다. 없거나 만료·불일치면 실패한다.
- 이메일과 비밀번호로 로그인 가능한 `ACTIVE` 회원을 재확인한다.
- 새 비밀번호는 회원가입과 동일한 정책(8~64자, 영문·숫자 포함)으로 검증한다.
- 검증을 통과하면 새 비밀번호를 단방향 암호화해 `password_hash`에 저장한다.
- 사용한 재설정 코드를 삭제하고, 해당 회원의 기존 Refresh Token을 모두 무효화한다.

### Response

#### 200 OK

```
{
  "success":true,
  "code":"AUTH_PASSWORD_RESET_SUCCESS",
  "message":"비밀번호가 변경되었습니다. 새 비밀번호로 로그인해 주세요.",
  "data":null,
  "timestamp":"2026-08-07T14:40:00+09:00"
}
```

### Exception

#### 400 Bad Request — 코드 불일치·만료

```
{
  "success":false,
  "code":"AUTH_PASSWORD_RESET_CODE_INVALID",
  "message":"인증 코드가 올바르지 않거나 만료되었습니다.",
  "data":null,
  "timestamp":"2026-08-07T14:40:00+09:00"
}
```

#### 400 Bad Request — 비밀번호 정책 위반

```
{
  "success":false,
  "code":"COMMON_INVALID_REQUEST",
  "message":"입력값을 확인해 주세요.",
  "data": {
    "field":"newPassword",
    "reason":"비밀번호는 8자 이상이며 영문과 숫자를 포함해야 합니다."
  },
  "timestamp":"2026-08-07T14:40:00+09:00"
}
```

#### 403 Forbidden — 탈퇴 회원

```
{
  "success":false,
  "code":"AUTH_MEMBER_WITHDRAWN",
  "message":"탈퇴 처리된 회원입니다.",
  "data":null,
  "timestamp":"2026-08-07T14:40:00+09:00"
}
```

#### 409 Conflict — 소셜 전용 계정

```
{
  "success":false,
  "code":"AUTH_PASSWORD_LOGIN_NOT_AVAILABLE",
  "message":"간편 로그인을 이용해 주세요.",
  "data":null,
  "timestamp":"2026-08-07T14:40:00+09:00"
}
```

---

## 비밀번호 재설정 코드 검증(사전 확인)

Domain: Auth
Method: POST
Progress: 완료
URI: /api/v1/auth/password-reset/verify
담당자: 박재명
연동여부: No

이메일로 받은 재설정 코드가 유효한지만 확인하는 사전 검증 단계다. 비밀번호는 변경하지 않고 코드도 소비(삭제)하지 않으므로, 이후 `POST /api/v1/auth/password-reset/confirm` 호출을 방해하지 않는다.

### Request

- Request HTTP Method: `POST`
- URI: `/api/v1/auth/password-reset/verify`
- 인증 필요: 없음
- Content-Type: `application/json`

#### Request Body

```
{
  "email":"dain@example.com",
  "code":"123456"
}
```

| 필드 | 타입 | 필수 | 설명 |
| --- | --- | --- | --- |
| `email` | String | Y | 재설정 대상 이메일, 최대 255자 |
| `code` | String | Y | 발송받은 6자리 재설정 코드 |

### 처리 기준

- 이메일의 앞뒤 공백을 제거하고 소문자로 변환해 코드 저장소(해시)를 조회한다.
- 저장된 코드와 일치하면 `valid=true`를 반환하며, 코드와 시도 카운터를 건드리지 않는다.
- 불일치면 `confirm`과 동일한 시도 카운터를 누적하고 `valid=false`를 반환한다(정상 응답 `200`).
- 무차별 대입 방어를 위해 시도 횟수가 임계치(5회)를 초과하면 코드를 폐기하고 `400`으로 거절한다. 이 카운터는 `confirm`과 공유되며 한도도 동일하다.
- 계정 존재 여부에 따라 동작이 갈리지 않도록 회원 존재 여부는 조회하지 않는다.

### Response

#### 200 OK

```
{
  "success":true,
  "code":"AUTH_PASSWORD_RESET_CODE_VERIFY_SUCCESS",
  "message":"인증 코드 확인이 완료되었습니다.",
  "data": {
    "valid":true
  },
  "timestamp":"2026-08-07T14:40:00+09:00"
}
```

### Exception

#### 400 Bad Request — 입력값 오류

```
{
  "success":false,
  "code":"COMMON_INVALID_REQUEST",
  "message":"입력값을 확인해 주세요.",
  "data": {
    "field":"code",
    "reason":"인증 코드는 필수입니다."
  },
  "timestamp":"2026-08-07T14:40:00+09:00"
}
```

#### 400 Bad Request — 시도 횟수 초과로 코드 폐기

```
{
  "success":false,
  "code":"AUTH_PASSWORD_RESET_CODE_INVALID",
  "message":"인증 코드가 올바르지 않거나 만료되었습니다.",
  "data":null,
  "timestamp":"2026-08-07T14:40:00+09:00"
}
```

---

## 이메일 중복 확인

Domain: Auth
Method: GET
Progress: 완료
URI: /api/v1/auth/check-email
담당자: 박재명
연동여부: No

회원가입 전 이메일 사용 가능 여부를 확인한다. `available=true`이면 아직 사용되지 않아 가입에 사용할 수 있다.

### Request

- Request HTTP Method: `GET`
- URI: `/api/v1/auth/check-email`
- 인증 필요: 없음

#### Query Parameter

| 파라미터 | 타입 | 필수 | 설명 |
| --- | --- | --- | --- |
| `email` | String | Y | 확인할 이메일, 최대 255자 |

### 처리 기준

- 이메일의 앞뒤 공백을 제거하고 소문자로 변환해 회원 존재 여부를 조회한다(회원가입과 동일한 정규화).
- 동일한 이메일을 사용하는 회원이 없으면 `available=true`, 있으면 `available=false`를 반환한다.

### Response

#### 200 OK

```
{
  "success":true,
  "code":"AUTH_EMAIL_AVAILABILITY_SUCCESS",
  "message":"이메일 사용 가능 여부를 확인했습니다.",
  "data": {
    "available":true
  },
  "timestamp":"2026-08-07T14:40:00+09:00"
}
```

### Exception

#### 400 Bad Request — 입력값 오류

```
{
  "success":false,
  "code":"COMMON_INVALID_REQUEST",
  "message":"입력값을 확인해 주세요.",
  "data": {
    "field":"email",
    "reason":"올바른 이메일 형식이 아닙니다."
  },
  "timestamp":"2026-08-07T14:40:00+09:00"
}
```

---

## 닉네임 중복 확인

Domain: Auth
Method: GET
Progress: 완료
URI: /api/v1/auth/check-nickname
담당자: 박재명
연동여부: No

회원가입 전 닉네임 사용 가능 여부를 확인한다. `available=true`이면 아직 사용되지 않아 가입에 사용할 수 있다.

### Request

- Request HTTP Method: `GET`
- URI: `/api/v1/auth/check-nickname`
- 인증 필요: 없음

#### Query Parameter

| 파라미터 | 타입 | 필수 | 설명 |
| --- | --- | --- | --- |
| `nickname` | String | Y | 확인할 닉네임, 최대 50자 |

### 처리 기준

- 동일한 닉네임을 사용하는 회원이 없으면 `available=true`, 있으면 `available=false`를 반환한다.

### Response

#### 200 OK

```
{
  "success":true,
  "code":"AUTH_NICKNAME_AVAILABILITY_SUCCESS",
  "message":"닉네임 사용 가능 여부를 확인했습니다.",
  "data": {
    "available":true
  },
  "timestamp":"2026-08-07T14:40:00+09:00"
}
```

### Exception

#### 400 Bad Request — 입력값 오류

```
{
  "success":false,
  "code":"COMMON_INVALID_REQUEST",
  "message":"입력값을 확인해 주세요.",
  "data": {
    "field":"nickname",
    "reason":"닉네임은 필수입니다."
  },
  "timestamp":"2026-08-07T14:40:00+09:00"
}
```

---

## 일반 로그인

Method: POST
Progress: 완료
URI: /api/v1/auth/login
담당자: 박재명
연동여부: Yes
프론트 담당자: 장선형

이메일과 비밀번호를 검증하고 인증 토큰을 발급한다.

### Request

- Request HTTP Method: `POST`
- URI: `/api/v1/auth/login`
- 인증 필요: 없음
- Content-Type: `application/json`

#### Request Body

```
{
  "email":"dain@example.com",
  "password":"Ssafy1234"
}
```

| 필드 | 타입 | 필수 | 설명 |
| --- | --- | --- | --- |
| `email` | String | Y | 가입한 이메일 |
| `password` | String | Y | 계정 비밀번호 |

### 처리 기준

- 이메일의 앞뒤 공백을 제거하고 소문자로 변환한다.
- 이메일에 해당하는 회원이 존재하는지 확인한다.
- 저장된 비밀번호 해시와 입력한 비밀번호를 비교한다.
- `status = ACTIVE`인 회원만 로그인할 수 있다.
- 소셜 계정만 존재하고 비밀번호가 없는 회원은 일반 로그인을 사용할 수 없다.
- 로그인 성공 시 Access Token과 Refresh Token을 새로 발급한다.
- `member_preference` 존재 여부로 온보딩 완료 여부를 판단한다.
- 로그인 실패 응답에서 이메일 존재 여부와 비밀번호 오류 여부를 구분해 노출하지 않는다.

### Response

#### 200 OK

```
{
  "success":true,
  "code":"AUTH_LOGIN_SUCCESS",
  "message":"로그인에 성공했습니다.",
  "data": {
    "memberId":1,
    "email":"dain@example.com",
    "nickname":"루돌푸",
    "profileImageUrl":null,
    "selectedCharacterId":"JIPKONG",
    "onboardingCompleted":true,
    "accessToken":"access-token",
    "refreshToken":"refresh-token"
  },
  "timestamp":"2026-07-24T14:45:00+09:00"
}
```

### Exception

#### 400 Bad Request

```
{
  "success":false,
  "code":"COMMON_INVALID_REQUEST",
  "message":"입력값을 확인해 주세요.",
  "data": {
    "field":"email",
    "reason":"올바른 이메일 형식이 아닙니다."
  },
  "timestamp":"2026-07-24T14:45:00+09:00"
}
```

#### 401 Unauthorized — 로그인 실패

```
{
  "success":false,
  "code":"AUTH_LOGIN_FAILED",
  "message":"이메일 또는 비밀번호를 확인해 주세요.",
  "data":null,
  "timestamp":"2026-07-24T14:45:00+09:00"
}
```

#### 403 Forbidden — 탈퇴 회원

```
{
  "success":false,
  "code":"AUTH_MEMBER_WITHDRAWN",
  "message":"탈퇴 처리된 회원입니다.",
  "data":null,
  "timestamp":"2026-07-24T14:45:00+09:00"
}
```

#### 409 Conflict — 일반 로그인 불가 계정

```
{
  "success":false,
  "code":"AUTH_PASSWORD_LOGIN_NOT_AVAILABLE",
  "message":"간편 로그인을 이용해 주세요.",
  "data":null,
  "timestamp":"2026-07-24T14:45:00+09:00"
}
```

#### 500 Internal Server Error

```
{
  "success":false,
  "code":"COMMON_INTERNAL_SERVER_ERROR",
  "message":"일시적인 오류가 발생했습니다.",
  "data":null,
  "timestamp":"2026-07-24T14:45:00+09:00"
}
```

### 프론트 처리

```
로그인 성공
→ 토큰 저장
→ onboardingCompleted = true
→ 홈 화면 이동

로그인 성공
→ onboardingCompleted = false
→ 온보딩 화면 이동
```

---

## 네이버/카카오 간편 로그인

Method: POST
Progress: 완료
URI: /api/v1/auth/social-login
담당자: 박재명
연동여부: No

네이버 또는 카카오의 OAuth 인증 결과를 검증한다.

기존 소셜 계정과 연결된 회원이면 서비스 로그인 토큰을 발급하고, 신규 사용자이면 소셜 회원가입 완료 API에서 사용할 임시 토큰을 반환한다.

### Request

- Request HTTP Method: `POST`
- URI: `/api/v1/auth/social-login`
- 인증 필요: 없음
- Content-Type: `application/json`

#### Request Body

```
{
  "provider":"KAKAO",
  "authorizationCode":"oauth-authorization-code",
  "redirectUri":"com.ssafy.palbang://oauth"
}
```

네이버는 인가 요청에서 사용한 `state`를 함께 전달한다.

```
{
  "provider":"NAVER",
  "authorizationCode":"oauth-authorization-code",
  "state":"oauth-request-state"
}
```

| 필드 | 타입 | 필수 | 설명 |
| --- | --- | --- | --- |
| `provider` | String | Y | 소셜 로그인 제공자 |
| `authorizationCode` | String | Y | 소셜 제공자가 발급한 인가 코드 |
| `redirectUri` | String | 조건부 | `KAKAO`일 때 필수. OAuth 인증 요청에서 사용한 Redirect URI |
| `state` | String | 조건부 | `NAVER`일 때 필수. OAuth 인증 요청에서 생성하고 콜백에서 확인한 원본 상태값 |

### 소셜 제공자

| 값 | 설명 |
| --- | --- |
| `NAVER` | 네이버 간편 로그인 |
| `KAKAO` | 카카오 간편 로그인 |

### 처리 기준

- `provider`가 `NAVER` 또는 `KAKAO`인지 검증한다.
- `KAKAO` 요청은 OAuth 인증 요청에서 사용한 `redirectUri`를 필수로 전달한다.
- `NAVER` 요청은 OAuth 인증 요청과 콜백에서 검증한 동일한 `state`를 필수로 전달한다.
- 소셜 제공자의 OAuth API에 인가 코드를 전달하여 인증 결과를 검증한다.
- 소셜 제공자로부터 고유 사용자 식별자 `socialUserId`와 이메일을 조회한다.
- 이메일은 소셜 제공자의 동의 범위에 따라 제공되지 않을 수 있다.
- `provider + socialUserId` 조합으로 기존 `social_account`를 조회한다.
- 기존 소셜 계정이 존재하면 연결된 회원으로 로그인한다.
- 연결된 회원의 `status`가 `ACTIVE`인 경우에만 로그인할 수 있다.
- 연결된 회원의 상태가 `WITHDRAWN`이면 로그인을 차단한다.
- 기존 회원 로그인에 성공하면 Access Token과 Refresh Token을 발급한다.
- `member_preference` 존재 여부로 온보딩 완료 여부를 판단한다.
- 기존 소셜 계정이 없으면 회원을 즉시 생성하지 않는다.
- 신규 사용자에게는 소셜 회원가입 완료 API에서 사용할 `socialSignupToken`을 발급한다.
- `socialSignupToken`에는 최소한 다음 정보를 포함한다.
    - `provider`
    - `socialUserId`
    - 소셜 제공자 이메일
    - 발급 시각
    - 만료 시각
- `socialSignupToken`의 유효시간은 10분으로 한다.
- 소셜 제공자 이메일이 없으면 `emailRequired = true`로 반환한다.
- 소셜 제공자 이메일이 있으면 `emailRequired = false`로 반환한다.
- 소셜 제공자로부터 받은 OAuth Access Token과 Refresh Token은 DB에 저장하지 않는다.
- 소셜 제공자 이메일이 기존 일반 회원 이메일과 같더라도 자동으로 계정을 연결하지 않는다.
- 소셜 로그인과 일반 회원 계정 연결 기능은 MVP 범위에 포함하지 않는다.

### Response

#### 200 OK — 기존 회원 로그인

```
{
  "success":true,
  "code":"AUTH_SOCIAL_LOGIN_SUCCESS",
  "message":"간편 로그인에 성공했습니다.",
  "data": {
    "signupRequired":false,
    "memberId":1,
    "email":"dain@example.com",
    "nickname":"루돌푸",
    "profileImageUrl":null,
    "selectedCharacterId":"JIPKONG",
    "onboardingCompleted":true,
    "accessToken":"access-token",
    "refreshToken":"refresh-token"
  },
  "timestamp":"2026-07-24T15:50:00+09:00"
}
```

#### 200 OK — 신규 사용자, 제공자 이메일 존재

```
{
  "success":true,
  "code":"AUTH_SOCIAL_SIGNUP_REQUIRED",
  "message":"소셜 회원가입이 필요합니다.",
  "data": {
    "signupRequired":true,
    "provider":"KAKAO",
    "email":"dain@example.com",
    "emailRequired":false,
    "socialSignupToken":"temporary-social-signup-token",
    "expiresIn":600
  },
  "timestamp":"2026-07-24T15:50:00+09:00"
}
```

#### 200 OK — 신규 사용자, 제공자 이메일 없음

```
{
  "success":true,
  "code":"AUTH_SOCIAL_SIGNUP_REQUIRED",
  "message":"소셜 회원가입이 필요합니다.",
  "data": {
    "signupRequired":true,
    "provider":"NAVER",
    "email":null,
    "emailRequired":true,
    "socialSignupToken":"temporary-social-signup-token",
    "expiresIn":600
  },
  "timestamp":"2026-07-24T15:50:00+09:00"
}
```

### Response Field

#### 기존 회원 응답

| 필드 | 타입 | 설명 |
| --- | --- | --- |
| `signupRequired` | Boolean | 추가 회원가입 필요 여부. 기존 회원은 `false` |
| `memberId` | Long | 회원 ID |
| `email` | String | 회원 이메일 |
| `nickname` | String | 회원 닉네임 |
| `profileImageUrl` | String | 프로필 이미지 URL |
| `selectedCharacterId` | String | 선택 캐릭터 ID |
| `onboardingCompleted` | Boolean | 온보딩 완료 여부 |
| `accessToken` | String | API 인증용 Access Token |
| `refreshToken` | String | 토큰 재발급용 Refresh Token |

#### 신규 사용자 응답

| 필드 | 타입 | 설명 |
| --- | --- | --- |
| `signupRequired` | Boolean | 추가 회원가입 필요 여부. 신규 사용자는 `true` |
| `provider` | String | 소셜 로그인 제공자 |
| `email` | String | 소셜 제공자가 전달한 이메일, 없으면 `null` |
| `emailRequired` | Boolean | 소셜 회원가입 시 이메일 입력 필요 여부 |
| `socialSignupToken` | String | 소셜 회원가입 완료용 임시 토큰 |
| `expiresIn` | Integer | 임시 토큰 유효시간, 초 단위 |

### Exception

#### 400 Bad Request — 지원하지 않는 제공자

```
{
  "success":false,
  "code":"AUTH_SOCIAL_PROVIDER_INVALID",
  "message":"지원하지 않는 간편 로그인 제공자입니다.",
  "data": {
    "allowedValues": ["NAVER","KAKAO"
    ]
  },
  "timestamp":"2026-07-24T15:50:00+09:00"
}
```

#### 400 Bad Request — 인가 코드 누락

```
{
  "success":false,
  "code":"COMMON_INVALID_REQUEST",
  "message":"입력값을 확인해 주세요.",
  "data": {
    "field":"authorizationCode",
    "reason":"인가 코드는 필수입니다."
  },
  "timestamp":"2026-07-24T15:50:00+09:00"
}
```

#### 401 Unauthorized — 소셜 인증 실패

```
{
  "success":false,
  "code":"AUTH_SOCIAL_AUTHENTICATION_FAILED",
  "message":"간편 로그인 인증에 실패했습니다.",
  "data":null,
  "timestamp":"2026-07-24T15:50:00+09:00"
}
```

#### 403 Forbidden — 탈퇴 회원

```
{
  "success":false,
  "code":"AUTH_MEMBER_WITHDRAWN",
  "message":"탈퇴 처리된 회원입니다.",
  "data":null,
  "timestamp":"2026-07-24T15:50:00+09:00"
}
```

#### 503 Service Unavailable — 소셜 제공자 장애

```
{
  "success":false,
  "code":"AUTH_SOCIAL_PROVIDER_UNAVAILABLE",
  "message":"간편 로그인 서비스에 일시적인 오류가 발생했습니다.",
  "data":null,
  "timestamp":"2026-07-24T15:50:00+09:00"
}
```

#### 500 Internal Server Error

```
{
  "success":false,
  "code":"COMMON_INTERNAL_SERVER_ERROR",
  "message":"일시적인 오류가 발생했습니다.",
  "data":null,
  "timestamp":"2026-07-24T15:50:00+09:00"
}
```

### 프론트 처리

```
네이버 또는 카카오 OAuth 인증 완료
→ provider, authorizationCode를 서버에 전달
→ KAKAO는 인증 요청에 사용한 redirectUri를 함께 전달
→ NAVER는 인증 요청과 콜백에서 확인한 state를 함께 전달

signupRequired = false
→ Access Token과 Refresh Token 저장
→ onboardingCompleted = true이면 홈 화면 이동
→ onboardingCompleted = false이면 온보딩 화면 이동

signupRequired = true
→ socialSignupToken 임시 저장
→ emailRequired = false이면 닉네임 입력 화면 이동
→ emailRequired = true이면 이메일·닉네임 입력 화면 이동

소셜 회원가입을 완료하지 않고 화면 이탈
→ socialSignupToken 삭제
```

### 백엔드 구현 메모

- 카카오·네이버별 OAuth 클라이언트를 분리한다.
- 외부 OAuth 응답을 공통 DTO로 변환한다.
- `socialSignupToken`은 서버 서명 토큰 또는 일회성 인증 저장소를 이용한다.
- 임시 토큰 원문에 소셜 OAuth Access Token을 포함하지 않는다.
- 소셜 제공자 API 타임아웃과 장애를 내부 서버 오류와 구분한다.
- 로그인 성공 시 발급한 Refresh Token은 서버 인증 저장소에 저장한다.

---

## 소셜 회원가입 완료

Method: POST
Progress: 완료
URI: /api/v1/auth/social-signup
담당자: 박재명
연동여부: No

신규 소셜 사용자의 회원 정보를 생성하고 소셜 계정을 연결한다.

소셜 제공자가 이메일을 전달하지 않은 경우 서비스에서 사용할 이메일을 추가로 입력받는다.

### Request

- Request HTTP Method: `POST`
- URI: `/api/v1/auth/social-signup`
- 인증 필요: 없음
- Content-Type: `application/json`

#### Request Body — 소셜 제공자 이메일이 있는 경우

```
{
  "socialSignupToken":"temporary-social-signup-token",
  "nickname":"루돌푸"
}
```

#### Request Body — 소셜 제공자 이메일이 없는 경우

```
{
  "socialSignupToken":"temporary-social-signup-token",
  "email":"dain@example.com",
  "nickname":"루돌푸"
}
```

| 필드 | 타입 | 필수 | 설명 |
| --- | --- | --- | --- |
| `socialSignupToken` | String | Y | 소셜 로그인 API에서 발급한 임시 토큰 |
| `email` | String | 조건부 | 소셜 제공자가 이메일을 전달하지 않은 경우 필수 |
| `nickname` | String | Y | 서비스 닉네임, 최대 50자 |

### 처리 기준

- `socialSignupToken`의 서명과 만료 여부를 검증한다.
- 임시 토큰에서 다음 정보를 확인한다.
    - `provider`
    - `socialUserId`
    - 소셜 제공자 이메일
    - 만료 시각
- 같은 `provider + socialUserId` 조합의 소셜 계정이 이미 존재하는지 다시 확인한다.
- 이미 가입된 소셜 계정이면 신규 회원을 생성하지 않는다.
- 소셜 제공자 이메일이 존재하면 해당 이메일을 회원 이메일로 사용한다.
- 소셜 제공자 이메일이 없으면 Request Body의 `email`을 사용한다.
- 소셜 제공자 이메일이 없는데 Request Body의 `email`도 없으면 오류를 반환한다.
- 이메일의 앞뒤 공백을 제거하고 소문자로 변환한다.
- 이메일 형식을 검증한다.
- 회원 이메일은 중복될 수 없다.
- 닉네임의 앞뒤 공백을 제거한다.
- 닉네임은 공백만으로 구성할 수 없다.
- 닉네임은 중복될 수 없다.
- 소셜 회원은 `password_hash = null`로 저장한다.
- 기본 캐릭터는 `PALBANG`으로 저장한다.
- 서비스 알림 동의 기본값은 `true`다.
- 광고성 알림 동의 기본값은 `false`다.
- 회원 상태는 `ACTIVE`로 저장한다.
- `social_account`에는 다음 정보를 저장한다.
    - 생성한 회원 ID
    - 소셜 제공자
    - 소셜 사용자 고유 ID
    - 소셜 제공자가 전달한 이메일
- 사용자가 직접 입력한 이메일과 소셜 제공자 이메일은 구분한다.
- 회원 생성과 `social_account` 생성은 하나의 트랜잭션으로 처리한다.
- 회원가입 성공 시 Access Token과 Refresh Token을 발급한다.
- `member_preference`가 없으므로 `onboardingCompleted = false`로 반환한다.
- 회원가입 성공 후 사용한 `socialSignupToken`은 재사용할 수 없다.
- 회원가입 처리 중 오류가 발생하면 회원과 소셜 계정 생성을 모두 롤백한다.

### Response

#### 201 Created

```
{
  "success":true,
  "code":"AUTH_SOCIAL_SIGNUP_SUCCESS",
  "message":"소셜 회원가입이 완료되었습니다.",
  "data": {
    "memberId":2,
    "email":"dain@example.com",
    "nickname":"루돌푸",
    "provider":"KAKAO",
    "selectedCharacterId":"PALBANG",
    "onboardingCompleted":false,
    "accessToken":"access-token",
    "refreshToken":"refresh-token"
  },
  "timestamp":"2026-07-24T15:55:00+09:00"
}
```

### Response Field

| 필드 | 타입 | 설명 |
| --- | --- | --- |
| `memberId` | Long | 생성된 회원 ID |
| `email` | String | 서비스 회원 이메일 |
| `nickname` | String | 회원 닉네임 |
| `provider` | String | 연결한 소셜 로그인 제공자 |
| `selectedCharacterId` | String | 기본 선택 캐릭터 |
| `onboardingCompleted` | Boolean | 온보딩 완료 여부 |
| `accessToken` | String | API 인증용 Access Token |
| `refreshToken` | String | 토큰 재발급용 Refresh Token |

### Exception

#### 400 Bad Request — 임시 토큰 오류

```
{
  "success":false,
  "code":"AUTH_SOCIAL_SIGNUP_TOKEN_INVALID",
  "message":"소셜 회원가입 정보가 유효하지 않습니다.",
  "data":null,
  "timestamp":"2026-07-24T15:55:00+09:00"
}
```

#### 400 Bad Request — 이메일 입력 필요

```
{
  "success":false,
  "code":"AUTH_SOCIAL_EMAIL_REQUIRED",
  "message":"회원가입에 사용할 이메일을 입력해 주세요.",
  "data": {
    "field":"email"
  },
  "timestamp":"2026-07-24T15:55:00+09:00"
}
```

#### 400 Bad Request — 이메일 형식 오류

```
{
  "success":false,
  "code":"COMMON_INVALID_REQUEST",
  "message":"입력값을 확인해 주세요.",
  "data": {
    "field":"email",
    "reason":"올바른 이메일 형식이 아닙니다."
  },
  "timestamp":"2026-07-24T15:55:00+09:00"
}
```

#### 401 Unauthorized — 임시 토큰 만료

```
{
  "success":false,
  "code":"AUTH_SOCIAL_SIGNUP_TOKEN_EXPIRED",
  "message":"소셜 인증 정보가 만료되었습니다. 다시 로그인해 주세요.",
  "data":null,
  "timestamp":"2026-07-24T15:55:00+09:00"
}
```

#### 409 Conflict — 이메일 중복

```
{
  "success":false,
  "code":"AUTH_EMAIL_DUPLICATED",
  "message":"이미 사용 중인 이메일입니다.",
  "data":null,
  "timestamp":"2026-07-24T15:55:00+09:00"
}
```

#### 409 Conflict — 닉네임 중복

```
{
  "success":false,
  "code":"AUTH_NICKNAME_DUPLICATED",
  "message":"이미 사용 중인 닉네임입니다.",
  "data":null,
  "timestamp":"2026-07-24T15:55:00+09:00"
}
```

#### 409 Conflict — 이미 가입된 소셜 계정

```
{
  "success":false,
  "code":"AUTH_SOCIAL_ACCOUNT_ALREADY_EXISTS",
  "message":"이미 가입된 간편 로그인 계정입니다.",
  "data":null,
  "timestamp":"2026-07-24T15:55:00+09:00"
}
```

#### 500 Internal Server Error

```
{
  "success":false,
  "code":"COMMON_INTERNAL_SERVER_ERROR",
  "message":"일시적인 오류가 발생했습니다.",
  "data":null,
  "timestamp":"2026-07-24T15:55:00+09:00"
}
```

### 프론트 처리

```
소셜 로그인 응답에서 signupRequired = true 확인
→ socialSignupToken 임시 저장

emailRequired = false
→ 닉네임 입력
→ socialSignupToken과 nickname 전송

emailRequired = true
→ 이메일과 닉네임 입력
→ socialSignupToken, email, nickname 전송

소셜 회원가입 성공
→ Access Token과 Refresh Token 저장
→ socialSignupToken 삭제
→ 온보딩 화면으로 이동

AUTH_EMAIL_DUPLICATED
→ 이미 가입된 이메일 안내
→ 기존 로그인 방식 이용 안내
```

### 백엔드 구현 메모

- 회원 이메일은 `member.email`에 저장하며 `UNIQUE`, `NOT NULL` 제약을 적용한다.
- 소셜 제공자 원본 이메일은 `social_account.email`에 저장한다.
- 사용자가 직접 입력한 이메일은 `member.email`에만 저장하고, 소셜 제공자가 전달하지 않았다면 `social_account.email`은 `null`로 저장한다.
- `provider + socialUserId` 중복 검증과 이메일·닉네임 중복 검증은 회원 생성 직전에 다시 수행한다.
- 회원 생성과 소셜 계정 생성은 반드시 하나의 트랜잭션으로 처리한다.
- 성공한 `socialSignupToken`은 Redis에서 삭제하거나 사용 완료 상태로 변경한다.
- 성공 시 발급한 Refresh Token은 서버 인증 저장소에 저장한다.

---

## 토큰 재발급

Method: POST
Progress: 완료
URI: /api/v1/auth/reissue
담당자: 박재명
연동여부: Yes
프론트 담당자: 장선형

유효한 Refresh Token을 검증하고 새로운 Access Token과 Refresh Token을 발급한다.

Refresh Token은 서버 인증 저장소인 Redis에서 관리하며, 재발급할 때 기존 토큰을 폐기하는 회전 방식을 사용한다.

### Request

- Request HTTP Method: `POST`
- URI: `/api/v1/auth/reissue`
- 인증 필요: Access Token 불필요
- Content-Type: `application/json`

#### Request Body

```
{
  "refreshToken":"refresh-token"
}
```

| 필드 | 타입 | 필수 | 설명 |
| --- | --- | --- | --- |
| `refreshToken` | String | Y | 로그인 또는 이전 재발급 시 발급된 Refresh Token |

### 처리 기준

- Refresh Token의 서명을 검증한다.
- Refresh Token의 만료 여부를 검증한다.
- 토큰에서 회원 ID를 확인한다.
- Redis에 저장된 해당 회원의 활성 Refresh Token을 조회한다.
- 요청한 Refresh Token과 Redis에 저장된 Refresh Token이 일치하는지 확인한다.
- 토큰의 회원이 존재하는지 확인한다.
- 회원의 `status`가 `ACTIVE`인지 확인한다.
- 정상 토큰이면 새로운 Access Token과 Refresh Token을 모두 발급한다.
- 새 Refresh Token을 Redis에 저장한다.
- 기존 Refresh Token은 즉시 폐기한다.
- 기존 Refresh Token의 남은 만료시간과 관계없이 재사용할 수 없다.
- 위조·만료·폐기된 Refresh Token은 재발급할 수 없다.
- 로그아웃된 Refresh Token은 재발급할 수 없다.
- 탈퇴 회원은 토큰을 재발급할 수 없다.
- 탈퇴 처리 시 해당 회원의 모든 Refresh Token을 Redis에서 삭제한다.
- 토큰 재발급 과정은 동시 요청으로 같은 Refresh Token이 여러 번 사용되지 않도록 원자적으로 처리한다.

### Response

#### 200 OK

```
{
  "success":true,
  "code":"AUTH_TOKEN_REISSUE_SUCCESS",
  "message":"토큰이 재발급되었습니다.",
  "data": {
    "accessToken":"new-access-token",
    "refreshToken":"new-refresh-token"
  },
  "timestamp":"2026-07-24T16:00:00+09:00"
}
```

### Response Field

| 필드 | 타입 | 설명 |
| --- | --- | --- |
| `accessToken` | String | 새로 발급한 Access Token |
| `refreshToken` | String | 새로 발급한 Refresh Token |

### Exception

#### 400 Bad Request — 토큰 누락

```
{
  "success":false,
  "code":"COMMON_INVALID_REQUEST",
  "message":"입력값을 확인해 주세요.",
  "data": {
    "field":"refreshToken",
    "reason":"Refresh Token은 필수입니다."
  },
  "timestamp":"2026-07-24T16:00:00+09:00"
}
```

#### 401 Unauthorized — 유효하지 않은 Refresh Token

```
{
  "success":false,
  "code":"AUTH_REFRESH_TOKEN_INVALID",
  "message":"유효하지 않은 Refresh Token입니다.",
  "data":null,
  "timestamp":"2026-07-24T16:00:00+09:00"
}
```

#### 401 Unauthorized — 만료된 Refresh Token

```
{
  "success":false,
  "code":"AUTH_REFRESH_TOKEN_EXPIRED",
  "message":"로그인이 만료되었습니다. 다시 로그인해 주세요.",
  "data":null,
  "timestamp":"2026-07-24T16:00:00+09:00"
}
```

#### 401 Unauthorized — 폐기되거나 이미 사용된 토큰

```
{
  "success":false,
  "code":"AUTH_REFRESH_TOKEN_REVOKED",
  "message":"사용할 수 없는 Refresh Token입니다.",
  "data":null,
  "timestamp":"2026-07-24T16:00:00+09:00"
}
```

#### 403 Forbidden — 탈퇴 회원

```
{
  "success":false,
  "code":"AUTH_MEMBER_WITHDRAWN",
  "message":"탈퇴 처리된 회원입니다.",
  "data":null,
  "timestamp":"2026-07-24T16:00:00+09:00"
}
```

#### 404 Not Found — 회원 없음

```
{
  "success":false,
  "code":"MEMBER_NOT_FOUND",
  "message":"회원 정보를 찾을 수 없습니다.",
  "data":null,
  "timestamp":"2026-07-24T16:00:00+09:00"
}
```

#### 500 Internal Server Error

```
{
  "success":false,
  "code":"COMMON_INTERNAL_SERVER_ERROR",
  "message":"일시적인 오류가 발생했습니다.",
  "data":null,
  "timestamp":"2026-07-24T16:00:00+09:00"
}
```

### 프론트 처리

```
API 요청에서 Access Token 만료 응답 수신
→ 저장된 Refresh Token으로 토큰 재발급 요청

토큰 재발급 성공
→ 기존 Access Token과 Refresh Token 교체
→ 실패했던 기존 API 요청을 한 번만 재시도

여러 API에서 동시에 Access Token 만료 발생
→ 토큰 재발급 요청은 한 번만 수행
→ 나머지 요청은 재발급 완료까지 대기

토큰 재발급 실패
→ 로컬 Access Token과ㅌㅌ Refresh Token 삭제
→ 로그인 상태 초기화
→ 로그인 화면으로 이동
```

### 백엔드 구현 메모

Redis 저장 예시:

```
Key: auth:refresh:{memberId}
Value: Refresh Token 또는 Refresh Token 해시
TTL: Refresh Token 만료시간
```

- 보안을 위해 Refresh Token 원문 대신 해시값 저장을 권장한다.
- 재발급 시 Redis의 기존 토큰 검증과 새 토큰 교체를 원자적으로 처리한다.
- 동일 Refresh Token에 대한 동시 재발급 요청 중 하나만 성공하도록 한다.
- Refresh Token 재사용이 감지되면 해당 회원의 저장된 Refresh Token을 폐기하는 정책을 적용할 수 있다.
- Access Token은 서버에 저장하지 않는다.

---

## 로그아웃

Method: POST
Progress: 완료
URI: /api/v1/auth/logout
담당자: 박재명
연동여부: Yes

현재 기기의 Refresh Token을 폐기하고 서버 측 로그인 상태를 종료한다.

로그아웃은 회원 계정이나 FCM 토큰을 삭제하지 않으며, FCM 토큰 삭제가 필요한 경우 별도 API를 호출한다.

### Request

- Request HTTP Method: `POST`
- URI: `/api/v1/auth/logout`
- 인증 필요: 필요
- Content-Type: `application/json`

#### Request Header

```
Authorization: Bearer {accessToken}
```

#### Request Body

```
{
  "refreshToken":"refresh-token"
}
```

| 필드 | 타입 | 필수 | 설명 |
| --- | --- | --- | --- |
| `refreshToken` | String | Y | 현재 기기에 저장된 Refresh Token |

### 처리 기준

- Authorization Header의 Access Token을 검증한다.
- Access Token에서 로그인 회원 ID를 확인한다.
- 요청한 Refresh Token의 서명과 회원 ID를 확인한다.
- 요청한 Refresh Token이 로그인 회원에게 발급된 토큰인지 확인한다.
- Redis에 저장된 활성 Refresh Token과 요청 토큰이 일치하는지 확인한다.
- 일치하는 Refresh Token을 Redis에서 삭제한다.
- 삭제된 Refresh Token으로 토큰을 재발급할 수 없다.
- 이미 폐기되었거나 Redis에 존재하지 않는 Refresh Token으로 요청해도 성공으로 처리한다.
- 다른 회원에게 발급된 Refresh Token이면 오류를 반환한다.
- 로그아웃은 회원 정보를 변경하지 않는다.
- 로그아웃은 회원의 알림 수신 동의를 변경하지 않는다.
- 로그아웃은 FCM 토큰을 자동으로 삭제하지 않는다.
- 현재 기기의 Push 수신도 중지해야 한다면 로그아웃 전후에 다음 API를 별도로 호출한다.

```
DELETE /api/v1/members/me/devices/{deviceId}/fcm-token
```

- 클라이언트는 로그아웃 API 성공 여부와 관계없이 로컬 Access Token과 Refresh Token을 삭제한다.

### Response

#### 200 OK

```
{
  "success":true,
  "code":"AUTH_LOGOUT_SUCCESS",
  "message":"로그아웃되었습니다.",
  "data":null,
  "timestamp":"2026-07-24T16:05:00+09:00"
}
```

### Exception

#### 400 Bad Request — Refresh Token 누락

```
{
  "success":false,
  "code":"COMMON_INVALID_REQUEST",
  "message":"입력값을 확인해 주세요.",
  "data": {
    "field":"refreshToken",
    "reason":"Refresh Token은 필수입니다."
  },
  "timestamp":"2026-07-24T16:05:00+09:00"
}
```

#### 400 Bad Request — 다른 회원의 Refresh Token

```
{
  "success":false,
  "code":"AUTH_REFRESH_TOKEN_MEMBER_MISMATCH",
  "message":"로그아웃 요청 정보가 올바르지 않습니다.",
  "data":null,
  "timestamp":"2026-07-24T16:05:00+09:00"
}
```

#### 401 Unauthorized — Access Token 오류

```
{
  "success":false,
  "code":"AUTH_ACCESS_TOKEN_INVALID",
  "message":"로그인이 필요합니다.",
  "data":null,
  "timestamp":"2026-07-24T16:05:00+09:00"
}
```

#### 401 Unauthorized — Access Token 만료

```
{
  "success":false,
  "code":"AUTH_ACCESS_TOKEN_EXPIRED",
  "message":"Access Token이 만료되었습니다.",
  "data":null,
  "timestamp":"2026-07-24T16:05:00+09:00"
}
```

#### 500 Internal Server Error

```
{
  "success":false,
  "code":"COMMON_INTERNAL_SERVER_ERROR",
  "message":"일시적인 오류가 발생했습니다.",
  "data":null,
  "timestamp":"2026-07-24T16:05:00+09:00"
}
```

### 프론트 처리

```
사용자가 로그아웃 선택
→ 필요하면 현재 기기의 FCM 토큰 삭제 API 호출
→ POST /api/v1/auth/logout 호출

로그아웃 성공
→ 로컬 Access Token과 Refresh Token 삭제
→ 로그인 사용자 정보 초기화
→ 로그인 화면으로 이동

로그아웃 API 실패
→ 로컬 Access Token과 Refresh Token은 삭제
→ 로그인 사용자 정보 초기화
→ 로그인 화면으로 이동
```

### 백엔드 구현 메모

- Redis Key는 토큰 재발급 API와 동일한 규칙을 사용한다.

```
auth:refresh:{memberId}
```

- 이미 Redis에서 삭제된 토큰에 대한 요청은 멱등 성공으로 처리한다.
- 다른 회원의 토큰을 이용한 로그아웃 요청은 실패 처리한다.
- Access Token을 별도의 블랙리스트에 저장하지 않는 경우, 이미 발급된 Access Token은 남은 만료시간 동안 형식상 유효할 수 있다.
- 따라서 Access Token 만료시간은 짧게 설정한다.
- 즉시 Access Token 무효화가 필요하면 Redis 블랙리스트를 별도로 운영해야 한다.

---

## 온보딩 정보 저장

Domain: Members
Method: PUT
Progress: 완료
URI: /api/v1/members/me/onboarding
담당자: 박재명
연동여부: Yes
프론트 담당자: 장선형

회원가입 또는 최초 소셜 로그인 후 임장 목적, 생활 정보, 관심 우선순위, 연령대 공개 여부와 선택 캐릭터를 저장한다.

온보딩의 모든 항목은 필수이며, 온보딩 건너뛰기는 지원하지 않는다.

### Request

- HTTP Method: `PUT`
- URI: `/api/v1/members/me/onboarding`
- 인증 필요: 필요
- Content-Type: `application/json`

#### Request Header

```
Authorization: Bearer {accessToken}
```

#### Request Body

```json
{
  "purpose": "RESIDENCE",
  "maritalStatus": "MARRIED",
  "hasVehicle": true,
  "hasChildren": true,
  "priorities": [
    "TRANSPORT",
    "WALKABILITY",
    "PARKING",
    "GREEN_SPACE"
  ],
  "ageGroup": "FIFTIES",
  "ageGroupPublicAgreed": true,
  "selectedCharacterId": "PALBANG_RABBIT"
}
```

| 필드 | 타입 | 필수 | 설명 |
| --- | --- | --- | --- |
| `purpose` | String | Y | 임장 목적 |
| `maritalStatus` | String | Y | 혼인 여부 |
| `hasVehicle` | Boolean | Y | 차량 보유 여부 |
| `hasChildren` | Boolean | Y | 자녀 유무 |
| `priorities` | Array<String> | Y | 중요도 순서의 우선 항목. 1~4개 |
| `ageGroup` | String | Y | 연령대 |
| `ageGroupPublicAgreed` | Boolean | Y | 다른 사용자에게 연령대를 공개할지 여부 |
| `selectedCharacterId` | String | Y | 선택 캐릭터 |

다음 필드는 더 이상 요청하지 않는다.

```
skipped
householdType
budget
interestRegion
interestRegionPublicAgreed
```

### 주요 enum

#### purpose

```
RESIDENCE
INVESTMENT
STUDY
```

#### maritalStatus

```
SINGLE
MARRIED
```

#### ageGroup

```
TEENS
TWENTIES
THIRTIES
FORTIES
FIFTIES
SIXTIES_PLUS
```

| 값 | 설명 |
| --- | --- |
| `TEENS` | 10대 |
| `TWENTIES` | 20대 |
| `THIRTIES` | 30대 |
| `FORTIES` | 40대 |
| `FIFTIES` | 50대 |
| `SIXTIES_PLUS` | 60대 이상 |

#### selectedCharacterId

```
PALBANG
PALBANG_RABBIT
PALBANG_DOG
```

| 값 | 설명 |
| --- | --- |
| `PALBANG` | 기본 팔방이 |
| `PALBANG_RABBIT` | 토끼 팔방이 |
| `PALBANG_DOG` | 강아지 팔방이 |

#### priorities

```
TRANSPORT
SAFETY
EDUCATION
COMMERCIAL
WALKABILITY
GREEN_SPACE
PARKING
NOISE
```

| 값 | 설명 |
| --- | --- |
| `TRANSPORT` | 교통 |
| `SAFETY` | 안전·치안 |
| `EDUCATION` | 교육 환경 |
| `COMMERCIAL` | 상권·생활 편의시설 |
| `WALKABILITY` | 보행 환경 |
| `GREEN_SPACE` | 공원·녹지 |
| `PARKING` | 주차 환경 |
| `NOISE` | 소음 환경 |

### 처리 기준

- 로그인 회원 ID는 Access Token에서 확인한다.
- 회원 상태가 `ACTIVE`인 경우에만 저장할 수 있다.
- 온보딩 건너뛰기는 지원하지 않는다.
- 모든 요청 필드는 필수다.
- 문자열 앞뒤 공백은 제거한 후 검증·저장한다.
- `priorities`는 1개 이상 4개 이하로 선택한다.
- `priorities` 배열 순서를 사용자가 선택한 중요도 순서로 저장한다.
- 같은 우선순위를 중복해서 선택할 수 없다.
- `ageGroupPublicAgreed=false`이면 공개 프로필에서 연령대가 `null`로 반환된다.
- `selectedCharacterId`는 반드시 요청해야 하며 기본 캐릭터 자동 적용은 하지 않는다.
- `member_preference`가 없으면 생성하고, 있으면 기존 정보를 갱신한다.
- 저장 성공 후 `onboardingCompleted=true`가 된다.
- `householdType`, `budget`, `interestRegion`은 현재 온보딩에서 수집하지 않는다.

### Response

#### 200 OK

```json
{
  "success": true,
  "code": "MEMBER_ONBOARDING_SAVED",
  "message": "온보딩 정보가 저장되었습니다.",
  "data": {
    "memberId": 1,
    "purpose": "RESIDENCE",
    "maritalStatus": "MARRIED",
    "hasVehicle": true,
    "hasChildren": true,
    "priorities": [
      "TRANSPORT",
      "WALKABILITY",
      "PARKING",
      "GREEN_SPACE"
    ],
    "ageGroup": "FIFTIES",
    "ageGroupPublicAgreed": true,
    "selectedCharacterId": "PALBANG_RABBIT",
    "onboardingCompleted": true
  },
  "timestamp": "2026-07-28T15:30:00+09:00"
}
```

### Exception

- `400 MEMBER_ONBOARDING_REQUIRED_FIELD_MISSING`
- `400 MEMBER_CHARACTER_INVALID`
- `400 MEMBER_AGE_GROUP_INVALID`
- `400 MEMBER_PRIORITY_DUPLICATED`
- `400 COMMON_INVALID_REQUEST`
- `401 AUTH_ACCESS_TOKEN_INVALID`
- `403 AUTH_MEMBER_WITHDRAWN`
- `404 MEMBER_NOT_FOUND`
- `500 COMMON_INTERNAL_SERVER_ERROR`

#### 400 Bad Request — 필수 항목 누락

```json
{
  "success": false,
  "code": "MEMBER_ONBOARDING_REQUIRED_FIELD_MISSING",
  "message": "온보딩 필수 항목을 모두 선택해 주세요.",
  "data": {
    "field": "purpose",
    "reason": "필수 값입니다."
  },
  "timestamp": "2026-07-28T15:30:00+09:00"
}
```

`field`에는 다음 값 중 누락된 첫 필드가 반환될 수 있다.

```
purpose
maritalStatus
hasVehicle
hasChildren
priorities
ageGroup
ageGroupPublicAgreed
selectedCharacterId
```

#### 400 Bad Request — 잘못된 캐릭터

```json
{
  "success": false,
  "code": "MEMBER_CHARACTER_INVALID",
  "message": "선택할 수 없는 캐릭터입니다.",
  "data": {
    "allowedValues": [
      "PALBANG",
      "PALBANG_RABBIT",
      "PALBANG_DOG"
    ]
  },
  "timestamp": "2026-07-28T15:30:00+09:00"
}
```

#### 400 Bad Request — 잘못된 연령대

```json
{
  "success": false,
  "code": "MEMBER_AGE_GROUP_INVALID",
  "message": "선택할 수 없는 연령대입니다.",
  "data": {
    "allowedValues": [
      "TEENS",
      "TWENTIES",
      "THIRTIES",
      "FORTIES",
      "FIFTIES",
      "SIXTIES_PLUS"
    ]
  },
  "timestamp": "2026-07-28T15:30:00+09:00"
}
```

#### 400 Bad Request — 우선순위 중복

```json
{
  "success": false,
  "code": "MEMBER_PRIORITY_DUPLICATED",
  "message": "같은 우선순위를 중복해서 선택할 수 없습니다.",
  "data": null,
  "timestamp": "2026-07-28T15:30:00+09:00"
}
```

#### 400 Bad Request — 허용되지 않은 선택값

```json
{
  "success": false,
  "code": "COMMON_INVALID_REQUEST",
  "message": "입력값을 확인해 주세요.",
  "data": {
    "field": "maritalStatus",
    "reason": "허용되지 않은 값입니다."
  },
  "timestamp": "2026-07-28T15:30:00+09:00"
}
```

#### 401 Unauthorized

```json
{
  "success": false,
  "code": "AUTH_ACCESS_TOKEN_INVALID",
  "message": "로그인이 필요합니다.",
  "data": null,
  "timestamp": "2026-07-28T15:30:00+09:00"
}
```

#### 403 Forbidden — 탈퇴 회원

```json
{
  "success": false,
  "code": "AUTH_MEMBER_WITHDRAWN",
  "message": "탈퇴 처리된 회원입니다.",
  "data": null,
  "timestamp": "2026-07-28T15:30:00+09:00"
}
```

### 프론트 처리

```
회원가입 또는 최초 로그인
→ onboardingCompleted=false 확인
→ 필수 온보딩 화면 표시
→ 모든 항목 선택
→ PUT /api/v1/members/me/onboarding 호출
→ 성공 응답의 선택 캐릭터와 연령대 공개 상태를 사용자 상태에 반영
→ onboardingCompleted=true로 갱신
→ 홈 화면 이동
```

온보딩 건너뛰기 버튼과 `skipped=true` 요청은 사용하지 않는다.

---

## 내 프로필 조회

Method: GET
Progress: 완료
URI: /api/v1/members/me
담당자: 박재명
연동여부: Yes

### Request

- HTTP Method: `GET`
- URI: `/api/v1/members/me`
- 인증 필요: 필요

#### Request Header

```
Authorization: Bearer {accessToken}
```

### 처리 기준

- 로그인 회원 ID는 Access Token에서 확인한다.
- 회원 상태가 `ACTIVE`인 경우에만 조회할 수 있다.
- `member_preference`가 존재하면 `onboardingCompleted=true`다.
- `member_preference`가 없으면 `preference=null`, `onboardingCompleted=false`다.
- `joinedDays`는 `Asia/Seoul` 날짜 기준으로 가입일부터 현재일까지의 일수다.
- `fieldVisitCompletedCount`는 로그인 회원의 `field_participant` 중 `status='ENDED'`인 참여 수다.
- `ageGroupPublicAgreed`는 공개 프로필에서 연령대를 공개할지 나타낸다.
- `studyCount`는 로그인 회원이 스터디장이거나 현재 `ACTIVE` 멤버인 삭제되지 않은 스터디 수다.
- `reportCount`는 위 조회 범위에 포함되는 스터디의 `DONE` 리포트 수다.
- `followingCount`는 로그인 회원이 팔로우한 회원 수다.
- `reviewSummary`는 로그인 회원이 받은 전체 익명 평가의 태그 집계(`topTags`), 받은 좋아요 수(`likeReceivedCount`), 평가 수(`reviewCount`)다.
- `topTags`는 `count` 내림차순, 동점이면 `code` 오름차순으로 정렬하며 각 항목은 `code`/`label`/`emoji`/`category`/`count`를 가진다.
- 받은 평가가 없으면 `topTags=[]`, `likeReceivedCount=0`, `reviewCount=0`을 반환한다.

선호 정보에는 다음 항목만 반환한다.

```
purpose
maritalStatus
hasVehicle
hasChildren
priorities
```

다음 레거시 항목은 더 이상 응답하지 않는다.

```
householdType
budget
interestRegion
interestRegionPublicAgreed
```

### Response

#### 200 OK — 온보딩 완료 회원

```json
{
  "success": true,
  "code": "MEMBER_PROFILE_SUCCESS",
  "message": "내 프로필 조회에 성공했습니다.",
  "data": {
    "memberId": 1,
    "email": "dain@example.com",
    "nickname": "성동구탐방러",
    "profileImageUrl": null,
    "selectedCharacterId": "PALBANG_RABBIT",
    "ageGroup": "FIFTIES",
    "ageGroupPublicAgreed": true,
    "serviceNotificationAgreed": true,
    "adNotificationAgreed": false,
    "preference": {
      "purpose": "RESIDENCE",
      "maritalStatus": "MARRIED",
      "hasVehicle": true,
      "hasChildren": false,
      "priorities": [
        "TRANSPORT",
        "WALKABILITY",
        "PARKING"
      ]
    },
    "onboardingCompleted": true,
    "joinedDays": 32,
    "fieldVisitCompletedCount": 7,
    "summary": {
      "studyCount": 3,
      "reportCount": 2,
      "followingCount": 5
    },
    "reviewSummary": {
      "topTags": [
        { "code": "PUNCTUAL", "label": "시간 약속을 잘 지켜요", "emoji": "⏰", "category": "PERSON", "count": 8 },
        { "code": "SHARES_INFO", "label": "정보 공유를 잘해요", "emoji": "📣", "category": "VISIT", "count": 5 }
      ],
      "likeReceivedCount": 9,
      "reviewCount": 12
    },
    "createdAt": "2026-06-26T00:00:00+09:00",
    "updatedAt": "2026-07-28T15:00:00+09:00"
  },
  "timestamp": "2026-07-28T15:30:00+09:00"
}
```

#### 200 OK — 온보딩 미완료 회원

```json
{
  "success": true,
  "code": "MEMBER_PROFILE_SUCCESS",
  "message": "내 프로필 조회에 성공했습니다.",
  "data": {
    "memberId": 1,
    "email": "dain@example.com",
    "nickname": "성동구탐방러",
    "profileImageUrl": null,
    "selectedCharacterId": "PALBANG",
    "ageGroup": null,
    "ageGroupPublicAgreed": false,
    "serviceNotificationAgreed": true,
    "adNotificationAgreed": false,
    "preference": null,
    "onboardingCompleted": false,
    "joinedDays": 0,
    "fieldVisitCompletedCount": 0,
    "summary": {
      "studyCount": 0,
      "reportCount": 0,
      "followingCount": 0
    },
    "reviewSummary": {
      "topTags": [],
      "likeReceivedCount": 0,
      "reviewCount": 0
    },
    "createdAt": "2026-07-28T15:00:00+09:00",
    "updatedAt": "2026-07-28T15:00:00+09:00"
  },
  "timestamp": "2026-07-28T15:30:00+09:00"
}
```

### Exception

- `401 AUTH_ACCESS_TOKEN_INVALID`
- `403 AUTH_MEMBER_WITHDRAWN`
- `404 MEMBER_NOT_FOUND`
- `500 COMMON_INTERNAL_SERVER_ERROR`

#### 401 Unauthorized

```json
{
  "success": false,
  "code": "AUTH_ACCESS_TOKEN_INVALID",
  "message": "로그인이 필요합니다.",
  "data": null,
  "timestamp": "2026-07-28T15:30:00+09:00"
}
```

#### 403 Forbidden — 탈퇴 회원

```json
{
  "success": false,
  "code": "AUTH_MEMBER_WITHDRAWN",
  "message": "탈퇴 처리된 회원입니다.",
  "data": null,
  "timestamp": "2026-07-28T15:30:00+09:00"
}
```

### 프론트 처리

```
마이페이지 진입
→ GET /api/v1/members/me 호출
→ 기본 프로필, summary와 reviewSummary 표시
→ preference가 있으면 온보딩 선택 정보 표시
→ onboardingCompleted=false이면 필수 온보딩 화면으로 이동
```

---

## 내 프로필 수정

Method: PATCH
Progress: 완료
URI: /api/v1/members/me
담당자: 박재명
연동여부: Yes

로그인한 회원의 닉네임, 연령대, 연령대 공개 동의, 임장 목적, 우선순위, 선택 캐릭터와 생활 조건을 수정한다.

요청에 포함되지 않은 항목은 기존 값을 유지하며, 수정 완료 후 최신 정보를 반환한다.

### Request

- HTTP Method: `PATCH`
- URI: `/api/v1/members/me`
- 인증 필요: 필요
- Content-Type: `application/json`

#### Request Header

```
Authorization: Bearer {accessToken}
```

#### Request Body

```json
{
  "nickname": "성동구탐방러",
  "ageGroup": "FIFTIES",
  "ageGroupPublicAgreed": true,
  "purpose": "INVESTMENT",
  "priorities": [
    "PARKING",
    "TRANSPORT",
    "SAFETY"
  ],
  "selectedCharacterId": "PALBANG_DOG",
  "maritalStatus": "MARRIED",
  "hasVehicle": true,
  "hasChildren": false
}
```

| 필드 | 타입 | 필수 | 설명 |
| --- | --- | --- | --- |
| `nickname` | String | N | 닉네임, 앞뒤 공백 제거 후 1~50자 |
| `ageGroup` | String | N | 연령대 |
| `ageGroupPublicAgreed` | Boolean | N | 공개 프로필 연령대 노출 동의 |
| `purpose` | String | N | 임장 목적 |
| `priorities` | Array<String> | N | 중요도 순서의 우선 항목, 1~4개 |
| `selectedCharacterId` | String | N | 선택 캐릭터 |
| `maritalStatus` | String | N | 혼인 상태 |
| `hasVehicle` | Boolean | N | 차량 소유 여부 |
| `hasChildren` | Boolean | N | 자녀 유무 |

#### 주요 enum

```
ageGroup:
TEENS / TWENTIES / THIRTIES / FORTIES / FIFTIES / SIXTIES_PLUS

purpose:
RESIDENCE / INVESTMENT / STUDY

priorities:
TRANSPORT / SAFETY / EDUCATION / COMMERCIAL /
WALKABILITY / GREEN_SPACE / PARKING / NOISE

selectedCharacterId:
PALBANG / PALBANG_RABBIT / PALBANG_DOG

maritalStatus:
SINGLE / MARRIED
```

### 처리 기준

- 로그인 회원 ID는 Access Token에서 확인한다.
- 요청에 포함된 필드만 수정하고 전달되지 않은 필드는 기존 값을 유지한다.
- 수정 가능한 필드가 하나도 없으면 오류를 반환한다.
- 필드를 명시적으로 `null`로 전달하면 오류를 반환한다.
- Boolean의 `false`는 정상적인 수정값으로 처리한다.
- 닉네임은 앞뒤 공백을 제거한 후 저장한다.
- 닉네임을 변경하는 경우 기존 회원과의 중복을 검사한다.
- 자신의 현재 닉네임을 다시 전달한 경우 중복으로 처리하지 않는다.
- 공개 동의를 끄더라도 저장된 연령대는 삭제하지 않는다.
- `priorities`를 전달하면 배열 전체를 전달된 순서로 교체한다.
- `priorities`는 1개 이상 4개 이하이며 중복값을 허용하지 않는다.
- `member_preference`가 없고 온보딩 선호 또는 생활 조건을 수정하면 새로 생성한다.
- 선택 캐릭터 변경 성공 후 앱 전역 사용자 상태에도 최신 캐릭터를 반영한다.
- 성공 시 수정된 최신 프로필 값을 반환한다.

### Response

#### 200 OK

```json
{
  "success": true,
  "code": "MEMBER_PROFILE_UPDATE_SUCCESS",
  "message": "프로필이 수정되었습니다.",
  "data": {
    "memberId": 1,
    "nickname": "성동구탐방러",
    "ageGroup": "FIFTIES",
    "ageGroupPublicAgreed": true,
    "purpose": "INVESTMENT",
    "priorities": [
      "PARKING",
      "TRANSPORT",
      "SAFETY"
    ],
    "selectedCharacterId": "PALBANG_DOG",
    "maritalStatus": "MARRIED",
    "hasVehicle": true,
    "hasChildren": false,
    "updatedAt": "2026-07-28T15:10:00+09:00"
  },
  "timestamp": "2026-07-28T15:10:00+09:00"
}
```

### Exception

- `400 MEMBER_PROFILE_UPDATE_EMPTY`
- `400 MEMBER_CHARACTER_INVALID`
- `400 MEMBER_AGE_GROUP_INVALID`
- `400 MEMBER_PRIORITY_DUPLICATED`
- `400 COMMON_INVALID_REQUEST`
- `401 AUTH_ACCESS_TOKEN_INVALID`
- `403 AUTH_MEMBER_WITHDRAWN`
- `404 MEMBER_NOT_FOUND`
- `409 AUTH_NICKNAME_DUPLICATED`
- `500 COMMON_INTERNAL_SERVER_ERROR`

#### 400 Bad Request — 수정 항목 없음

```json
{
  "success": false,
  "code": "MEMBER_PROFILE_UPDATE_EMPTY",
  "message": "수정할 내 정보를 입력해 주세요.",
  "data": null,
  "timestamp": "2026-07-28T15:10:00+09:00"
}
```

#### 400 Bad Request — 잘못된 닉네임

```json
{
  "success": false,
  "code": "COMMON_INVALID_REQUEST",
  "message": "입력값을 확인해 주세요.",
  "data": {
    "field": "nickname",
    "reason": "닉네임은 1자 이상 50자 이하로 입력해 주세요."
  },
  "timestamp": "2026-07-28T15:10:00+09:00"
}
```

#### 400 Bad Request — 잘못된 연령대

```json
{
  "success": false,
  "code": "MEMBER_AGE_GROUP_INVALID",
  "message": "선택할 수 없는 연령대입니다.",
  "data": {
    "field": "ageGroup",
    "allowedValues": [
      "TEENS",
      "TWENTIES",
      "THIRTIES",
      "FORTIES",
      "FIFTIES",
      "SIXTIES_PLUS"
    ]
  },
  "timestamp": "2026-07-28T15:10:00+09:00"
}
```

#### 400 Bad Request — 잘못된 캐릭터

```json
{
  "success": false,
  "code": "MEMBER_CHARACTER_INVALID",
  "message": "선택할 수 없는 캐릭터입니다.",
  "data": {
    "field": "selectedCharacterId",
    "allowedValues": [
      "PALBANG",
      "PALBANG_RABBIT",
      "PALBANG_DOG"
    ]
  },
  "timestamp": "2026-07-28T15:10:00+09:00"
}
```

#### 400 Bad Request — 잘못된 혼인 상태

```json
{
  "success": false,
  "code": "COMMON_INVALID_REQUEST",
  "message": "입력값을 확인해 주세요.",
  "data": {
    "field": "maritalStatus",
    "reason": "허용되지 않은 값입니다.",
    "allowedValues": [
      "SINGLE",
      "MARRIED"
    ]
  },
  "timestamp": "2026-07-28T15:10:00+09:00"
}
```

#### 401 Unauthorized

```json
{
  "success": false,
  "code": "AUTH_ACCESS_TOKEN_INVALID",
  "message": "로그인이 필요합니다.",
  "data": null,
  "timestamp": "2026-07-28T15:10:00+09:00"
}
```

#### 403 Forbidden

```json
{
  "success": false,
  "code": "AUTH_MEMBER_WITHDRAWN",
  "message": "탈퇴 처리된 회원입니다.",
  "data": null,
  "timestamp": "2026-07-28T15:10:00+09:00"
}
```

#### 404 Not Found

```json
{
  "success": false,
  "code": "MEMBER_NOT_FOUND",
  "message": "회원 정보를 찾을 수 없습니다.",
  "data": null,
  "timestamp": "2026-07-28T15:10:00+09:00"
}
```

#### 409 Conflict — 닉네임 중복

```json
{
  "success": false,
  "code": "AUTH_NICKNAME_DUPLICATED",
  "message": "이미 사용 중인 닉네임입니다.",
  "data": null,
  "timestamp": "2026-07-28T15:10:00+09:00"
}
```

#### 500 Internal Server Error

```json
{
  "success": false,
  "code": "COMMON_INTERNAL_SERVER_ERROR",
  "message": "일시적인 오류가 발생했습니다.",
  "data": null,
  "timestamp": "2026-07-28T15:10:00+09:00"
}
```

### 프론트 처리

```
사용자가 변경한 필드만 Request Body에 포함
→ 프로필 수정 API 호출
→ 성공 응답의 최신 값으로 화면과 전역 사용자 상태 갱신

닉네임 중복
→ AUTH_NICKNAME_DUPLICATED 메시지를 닉네임 입력란 아래에 표시

캐릭터 변경
→ 성공 응답의 selectedCharacterId를 전역 상태에 반영
→ 마이페이지·홈·임장 지도·챗봇 캐릭터를 즉시 변경

API 요청 실패
→ 변경 전 화면 상태로 복원
→ 저장 실패 안내 표시
```

---

## 회원 탈퇴

Method: DELETE
Progress: 완료
URI: /api/v1/members/me
담당자: 박재명
연동여부: Yes

회원 계정을 물리 삭제하지 않고 탈퇴 상태로 변경하고 인증·알림 수신 수단을 폐기한다.

로그인한 회원을 물리적으로 삭제하지 않고 탈퇴 상태로 변경한다.

회원이 작성한 게시글, 댓글, 임장 기록과 리포트 근거는 서비스 데이터의 관계를 유지하기 위해 보존하며, 화면에는 작성자를 `탈퇴한 사용자`로 표시한다.

### Request

- HTTP Method: `DELETE`
- URI: `/api/v1/members/me`
- 인증 필요: 필요
- Content-Type: `application/json`

#### Request Body

```
{
  "refreshToken": "refresh-token",
  "confirmationText": "회원탈퇴"
}
```

| 필드 | 타입 | 필수 | 설명 |
| --- | --- | --- | --- |
| `refreshToken` | String | Y | 폐기할 현재 Refresh Token |
| `confirmationText` | String | Y | 탈퇴 확인 문구 `회원탈퇴` |

#### Request Header

```
Authorization: Bearer {accessToken}
```

### 처리 기준

- 회원을 `status=WITHDRAWN`, `deletedAt=현재 시각`으로 변경한다.
- 제출한 Refresh Token의 서명·만료 여부와 Access Token 회원과의 일치 여부를 검증한 뒤, 해당 회원의 Redis Refresh Token 키 전체와 모든 FCM 토큰을 폐기한다.
- `field_participant.status=IN_PROGRESS`이면서 연결된 `field_session.status=IN_PROGRESS`인 임장 참여가 있으면 탈퇴를 제한한다.
- 해당 회원이 스터디장인 `RECRUITING`, `CLOSED`, `IN_PROGRESS` 스터디가 있으면 탈퇴를 제한한다.
- `PENDING` 신청은 `REJECTED`로 변경하고 `decidedAt`을 현재 시각으로 저장한다. 이는 회원 탈퇴 후 처리되지 않은 신청이 남지 않도록 하는 최종 정책이다.
- 일반 멤버 관계는 `study_member.status=REMOVED`, `leftAt=현재 시각`으로 변경한다.
- 작성 게시글·댓글·현장 기록은 보존하되 공개 화면에서 회원 정보를 비식별화한다.
- 최종 ERD에 없는 탈퇴 사유 `reason`, 신청 상태 `CANCELED`, 멤버 상태 `LEFT`는 사용하지 않는다.

### Response

#### 200 OK

```
{
  "success": true,
  "code": "MEMBER_WITHDRAW_SUCCESS",
  "message": "회원 탈퇴가 완료되었습니다.",
  "data": {
    "memberId": 1,
    "withdrawnAt": "2026-07-24T15:45:00+09:00"
  },
  "timestamp": "2026-07-24T15:45:00+09:00"
}
```

### Response Field

| 필드 | 타입 | 설명 |
| --- | --- | --- |
| `memberId` | Long | 탈퇴 처리된 회원 ID |
| `withdrawnAt` | String | 회원 탈퇴 처리 시각 |

---

### Exception

- `400 COMMON_INVALID_REQUEST`: 필수 요청값 누락
- `400 MEMBER_WITHDRAW_CONFIRMATION_INVALID`
- `401 AUTH_ACCESS_TOKEN_INVALID`
- `401 AUTH_REFRESH_TOKEN_INVALID`
- `401 AUTH_REFRESH_TOKEN_EXPIRED`
- `404 MEMBER_NOT_FOUND`
- `409 MEMBER_WITHDRAWAL_FIELD_SESSION_IN_PROGRESS`: 진행 중인 임장 참여
- `409 MEMBER_WITHDRAWAL_ACTIVE_STUDY_LEADER`: 운영 중인 스터디의 스터디장
- `409 MEMBER_ALREADY_WITHDRAWN`: 이미 탈퇴한 회원
- `500 COMMON_INTERNAL_SERVER_ERROR`

---

### 추가 Exception 예시

#### 400 Bad Request — 탈퇴 확인 문구 불일치

```
{
  "success":false,
  "code":"MEMBER_WITHDRAW_CONFIRMATION_INVALID",
  "message":"회원 탈퇴 확인 문구가 일치하지 않습니다.",
  "data": {
    "field":"confirmationText",
    "expectedValue":"회원탈퇴"
  },
  "timestamp":"2026-07-22T10:30:00+09:00"
}
```

---

#### 401 Unauthorized

Access Token이 없거나 유효하지 않은 경우

```
{
  "success":false,
  "code":"AUTH_ACCESS_TOKEN_INVALID",
  "message":"로그인이 필요합니다.",
  "data":null,
  "timestamp":"2026-07-22T10:30:00+09:00"
}
```

---

#### 409 Conflict — 진행 중인 임장 참여

```
{
  "success":false,
  "code":"MEMBER_WITHDRAWAL_FIELD_SESSION_IN_PROGRESS",
  "message":"진행 중인 임장을 종료한 후 회원 탈퇴를 진행해 주세요.",
  "data": {
    "studyId":10,
    "sessionId":5
  },
  "timestamp":"2026-07-22T10:30:00+09:00"
}
```

---

#### 409 Conflict — 진행 중인 스터디의 스터디장

```
{
  "success":false,
  "code":"MEMBER_WITHDRAWAL_ACTIVE_STUDY_LEADER",
  "message":"운영 중인 스터디를 정리한 후 회원 탈퇴를 진행해 주세요.",
  "data": {
    "studyIds": [10,14
    ]
  },
  "timestamp":"2026-07-22T10:30:00+09:00"
}
```

---

#### 409 Conflict — 이미 탈퇴한 회원

```
{
  "success":false,
  "code":"MEMBER_ALREADY_WITHDRAWN",
  "message":"이미 탈퇴 처리된 회원입니다.",
  "data":null,
  "timestamp":"2026-07-22T10:30:00+09:00"
}
```

---

#### 500 Internal Server Error

```
{
  "success":false,
  "code":"COMMON_INTERNAL_SERVER_ERROR",
  "message":"일시적인 오류가 발생했습니다.",
  "data":null,
  "timestamp":"2026-07-22T10:30:00+09:00"
}
```

---

### 추가 프론트 처리 기준

```
회원 탈퇴 버튼 선택
→ 탈퇴 주의사항 표시
→ 사용자가 "회원탈퇴" 문구 입력
→ 회원 탈퇴 API 호출

탈퇴 성공
→ Access Token·Refresh Token 삭제
→ 사용자 정보와 로컬 캐시 삭제
→ 로그인 상태 해제
→ 시작 화면 이동

진행 중인 임장 또는 운영 중인 스터디가 있는 경우
→ 탈퇴를 중단
→ 응답에 포함된 스터디 또는 임장 화면으로 이동할 수 있도록 안내
```

---

## 다른 사용자 공개 프로필 조회

Method: GET
Progress: 완료
URI: /api/v1/members/{memberId}
담당자: 박재명
연동여부: Yes

로그인한 회원이 다른 회원의 공개 프로필과 참여 스터디, 해당 스터디에서 생성된 완료 리포트, 팔로잉 목록을 탭별로 조회한다.

연령대는 대상 회원이 공개에 동의한 경우에만 반환한다. 관심 지역과 개인화 선호 정보는 공개하지 않는다.

### Request

- HTTP Method: `GET`
- URI: `/api/v1/members/{memberId}`
- 인증 필요: 필요
- Request Body: 없음

#### Request Header

```http
Authorization: Bearer {accessToken}
```

#### Path Variable

| 필드 | 타입 | 필수 | 설명 |
| --- | --- | --- | --- |
| `memberId` | Long | Y | 조회 대상 회원 ID. 1 이상의 숫자 |

#### Query Parameter

```http
GET /api/v1/members/12?section=STUDIES&studyStatus=ACTIVE&page=0&size=20
```

| 필드 | 타입 | 필수 | 기본값 | 설명 |
| --- | --- | --- | --- | --- |
| `section` | String | N | `STUDIES` | `STUDIES`, `REPORTS`, `FOLLOWINGS` |
| `studyStatus` | String | N | `ACTIVE` | 스터디 탭 필터. `ACTIVE`, `COMPLETED`, `ALL` |
| `page` | Integer | N | `0` | 페이지 번호. 0 이상 |
| `size` | Integer | N | `20` | 페이지 크기. 1~100 |

`studyStatus`는 `section=STUDIES`일 때 목록 필터로 사용한다.

### 처리 기준

#### 회원과 공개 정보

- 로그인 회원과 조회 대상 회원은 모두 `ACTIVE` 상태여야 한다.
- 대상 회원이 없거나 탈퇴 또는 Soft Delete된 경우 `MEMBER_NOT_FOUND`를 반환한다.
- `nickname`, `profileImageUrl`, `selectedCharacterId`는 공개한다.
- `ageGroup`은 `ageGroupPublicAgreed=true`일 때만 반환하고, 그렇지 않으면 `null`을 반환한다.
- `interestRegion`은 공개 동의 여부와 관계없이 응답에 포함하지 않는다.
- 이메일, 임장 목적, 혼인 여부, 차량·자녀 정보, 우선순위는 반환하지 않는다.

#### 집계

- `participatingStudyCount`는 대상 회원이 스터디장이거나 참여한 취소되지 않은 스터디 수다.
- 진행 중인 스터디는 현재 `ACTIVE` 스터디원인 경우에 포함한다.
- 완료된 스터디는 해당 스터디에 참여했던 이력이 있으면 포함한다.
- `reportCount`는 대상 회원이 스터디장이거나 참여했던 스터디에서 생성된 `DONE` 리포트 수다.
- 실제 임장 세션 참여 여부와 관계없이 스터디 참여 이력이 있으면 완료 리포트 목록에 포함한다.
- `followingCount`는 대상 회원이 팔로우 중인 `ACTIVE` 회원 수다.
- `fieldVisitCompletedCount`는 대상 회원의 `field_participant` 중 `status='ENDED'`인 참여 수다.
- `reviewSummary`는 대상 회원이 받은 전체 익명 평가의 태그 집계(`topTags`), 받은 좋아요 수(`likeReceivedCount`), 평가 수(`reviewCount`)다.
- `topTags`는 `count` 내림차순, 동점이면 `code` 오름차순으로 정렬하며 각 항목은 `code`/`label`/`emoji`/`category`/`count`를 가진다.
- 받은 평가가 없으면 `topTags=[]`, `likeReceivedCount=0`, `reviewCount=0`을 반환한다.

#### 관계 상태

- `isMe`는 로그인 회원과 조회 대상 회원이 같은지 나타낸다.
- `isFollowing`은 로그인 회원이 대상 회원을 팔로우하고 있는지 나타낸다.
- `canFollow`은 본인이 아니고 현재 팔로우하지 않은 경우에만 `true`다.
- `canSendMessage`는 본인이 아니고 현재 팔로우 중인 경우에만 `true`다.
- 본인이면 `isFollowing=false`, `canFollow=false`, `canSendMessage=false`다.

#### 탭별 목록

- `section`으로 선택한 목록만 페이지 객체로 반환한다.
- 선택하지 않은 `studies`, `reports`, `followings`는 `null`이다.
- 목록은 `content`, `totalElements`, `page`, `size`, `totalPages` 구조를 사용한다.
- 목록이 없으면 `content=[]`을 반환한다.

#### 스터디 탭

- 삭제되거나 `CANCELED`인 스터디는 제외한다.
- `ACTIVE`는 `RECRUITING`, `CLOSED`, `IN_PROGRESS` 상태를 포함한다.
- `COMPLETED`는 `COMPLETED` 상태만 포함한다.
- `ALL`은 취소되지 않은 진행 중·완료 스터디를 모두 포함한다.
- `role`은 `LEADER` 또는 `MEMBER`다.

#### 리포트 탭

- `DONE` 상태 리포트만 반환한다.
- 삭제되거나 취소된 스터디의 리포트는 제외한다.
- 모든 리포트 조회에는 인증이 필요하다.
- 리포트 식별에는 숫자형 `reportId`를 사용한다.
- 임장 원본 기록, 사진, 개인 메모와 STT 원문은 포함하지 않는다.

#### 팔로잉 탭

- 대상 회원이 팔로우 중인 `ACTIVE` 회원만 반환한다.
- 최근 팔로우한 순서로 반환한다.
- 팔로잉 회원의 `ageGroup`도 해당 회원이 공개에 동의한 경우에만 반환한다.
- 팔로잉 카드에도 `interestRegion`을 포함하지 않는다.
- 카드의 관계 상태는 로그인 회원을 기준으로 계산한다.

### Response

#### 200 OK — 스터디 탭

```json
{
  "success": true,
  "code": "MEMBER_PUBLIC_PROFILE_SUCCESS",
  "message": "사용자 프로필 조회에 성공했습니다.",
  "data": {
    "memberId": 12,
    "nickname": "옥수탐방러",
    "profileImageUrl": null,
    "selectedCharacterId": "PALBANG",
    "ageGroup": "TWENTIES",
    "participatingStudyCount": 4,
    "reportCount": 3,
    "followingCount": 5,
    "fieldVisitCompletedCount": 6,
    "reviewSummary": {
      "topTags": [
        { "code": "PUNCTUAL", "label": "시간 약속을 잘 지켜요", "emoji": "⏰", "category": "PERSON", "count": 8 },
        { "code": "SHARES_INFO", "label": "정보 공유를 잘해요", "emoji": "📣", "category": "VISIT", "count": 5 }
      ],
      "likeReceivedCount": 9,
      "reviewCount": 12
    },
    "isMe": false,
    "isFollowing": true,
    "canFollow": false,
    "canSendMessage": true,
    "section": "STUDIES",
    "studies": {
      "content": [
        {
          "studyId": 31,
          "title": "옥수동 실거주 임장 스터디",
          "status": "IN_PROGRESS",
          "role": "MEMBER",
          "apartment": {
            "apartmentId": 101,
            "name": "옥수파크힐스"
          }
        }
      ],
      "totalElements": 2,
      "page": 0,
      "size": 20,
      "totalPages": 1
    },
    "reports": null,
    "followings": null
  },
  "timestamp": "2026-07-28T15:50:00+09:00"
}
```

#### 200 OK — 리포트 탭의 content 항목

```json
{
  "reportId": 51,
  "title": "7월 옥수동 임장 리포트",
  "summary": "교통과 생활 편의시설 접근성이 우수한 단지입니다.",
  "analysisTags": ["교통", "생활편의", "학군"],
  "apartment": {
    "apartmentId": 101,
    "name": "옥수파크힐스"
  },
  "study": {
    "studyId": 31,
    "title": "옥수동 실거주 임장 스터디",
    "participantCount": 4
  },
  "favoritedByMe": false,
  "completedAt": "2026-07-20T18:00:00+09:00"
}
```

#### 200 OK — 팔로잉 탭의 content 항목

```json
{
  "memberId": 21,
  "nickname": "성수탐방러",
  "profileImageUrl": null,
  "selectedCharacterId": "PALBANG_RABBIT",
  "ageGroup": "THIRTIES",
  "participatingStudyCount": 2,
  "isMe": false,
  "isFollowing": false,
  "canFollow": true,
  "canSendMessage": false,
  "followedAt": "2026-07-20T14:00:00+09:00"
}
```

연령대 공개에 동의하지 않은 경우 `ageGroup=null`을 반환한다. `interestRegion`은 키 자체를 반환하지 않는다.

### Exception

- `400 COMMON_INVALID_REQUEST`: 회원 ID, 탭, 상태 또는 페이지 값 오류
- `401 AUTH_ACCESS_TOKEN_INVALID`
- `401 AUTH_ACCESS_TOKEN_EXPIRED`
- `403 AUTH_MEMBER_WITHDRAWN`
- `404 MEMBER_NOT_FOUND`
- `500 COMMON_INTERNAL_SERVER_ERROR`

#### 400 Bad Request — 잘못된 탭

```json
{
  "success": false,
  "code": "COMMON_INVALID_REQUEST",
  "message": "입력값을 확인해 주세요.",
  "data": {
    "field": "section",
    "allowedValues": ["STUDIES", "REPORTS", "FOLLOWINGS"]
  },
  "timestamp": "2026-07-28T15:50:00+09:00"
}
```

#### 404 Not Found

```json
{
  "success": false,
  "code": "MEMBER_NOT_FOUND",
  "message": "회원 정보를 찾을 수 없습니다.",
  "data": null,
  "timestamp": "2026-07-28T15:50:00+09:00"
}
```

### 프론트 처리

```text
공개 프로필 진입
→ section=STUDIES, studyStatus=ACTIVE로 조회
→ 상단 공개 프로필과 참여 스터디 목록 표시
```

```text
스터디·AI 리포트·팔로잉 탭 변경
→ 선택한 section으로 다시 조회
→ 선택한 목록만 화면에 표시
```

```text
isMe=true
→ 팔로우·쪽지 버튼 숨김
→ 내 정보 수정 버튼 표시
```

```text
isMe=false, isFollowing=false
→ 팔로우 버튼 표시
→ 쪽지 버튼 숨김 또는 비활성화
```

```text
isMe=false, isFollowing=true
→ 팔로우 해제 버튼 표시
→ canSendMessage=true이면 쪽지 버튼 활성화
```

---

## 사용자 팔로우

Method: PUT
Progress: 완료
URI: /api/v1/members/{memberId}/follow
담당자: 박재명
연동여부: Yes

로그인한 회원이 다른 사용자를 팔로우한다.

동일한 팔로우 요청이 반복되더라도 중복 관계를 생성하지 않는다.

---

### Request

- Request HTTP Method: `PUT`
- 인증 필요: 필요
- Request Body: 없음

#### Request Header

```
Authorization: Bearer {accessToken}
```

#### Path Variable

```
PUT /api/v1/members/15/follow
```

| 필드 | 타입 | 필수 | 설명 |
| --- | --- | --- | --- |
| `memberId` | Long | Y | 팔로우할 대상 회원 ID |

#### 처리 기준

- 로그인한 회원 ID는 Access Token에서 확인한다.
- Path Variable의 `memberId`에 해당하는 회원을 팔로우 대상으로 조회한다.
- 로그인한 회원과 팔로우 대상 회원의 상태가 모두 `ACTIVE`인 경우에만 처리한다.
- 본인은 팔로우할 수 없다.
- 탈퇴했거나 Soft Delete된 회원은 팔로우할 수 없다.
- 팔로우 관계가 존재하지 않으면 `follow`에 새로운 관계를 생성한다.
- 이미 팔로우 중인 회원에게 동일한 요청을 보내면 새로운 행을 생성하지 않고 기존 팔로우 정보를 반환한다.
- 팔로우 관계는 이력 보존이 필요하지 않은 관계 데이터이므로 팔로우 해제 시 물리적으로 삭제한다.
- 팔로우 성공만으로 상대방이 로그인한 회원을 자동으로 팔로우하지 않는다.
- 팔로우 생성 시각은 `followedAt`으로 반환한다.
- 로그인한 회원의 현재 팔로잉 수를 `followingCount`로 반환한다.

#### 데이터 제약조건

```
UNIQUE(follower_id, following_id)

follower_id != following_id
```

#### 팔로우 관계 예시

```
로그인 회원 ID: 1
팔로우 대상 회원 ID: 15

follower_id = 1
following_id = 15
```

팔로우 방향은 단방향이다.

```
회원 1이 회원 15를 팔로우
≠
회원 15가 회원 1을 팔로우
```

---

#### MVP 메시지 전송 가능 여부

- 팔로우 등록 후 대상 회원에게 MVP 일회성 메시지를 전송할 수 있으므로 `canSendMessage = true`로 반환한다.
- 메시지 전송은 `POST /api/v1/members/{memberId}/messages`를 사용하며 대화방을 생성하지 않는다.
- 신규 팔로우 관계가 생성되면 대상 회원의 알림함에 `MEMBER_FOLLOWED` 알림을 저장한다.
- 이미 팔로우 중인 관계의 멱등 응답과 팔로우 해제에는 알림을 추가하지 않는다.
- 대상 회원이 서비스 알림에 동의하고 등록된 FCM 토큰이 있으면 모든 기기로 Push를 전송한다.

### Response

#### 200 OK — 신규 팔로우

```
{
  "success": true,
  "code": "MEMBER_FOLLOWED",
  "message": "사용자를 팔로우했습니다.",
  "data": {
    "memberId": 15,
    "nickname": "임장초보",
    "selectedCharacterId": "PALBANG_DOG",
    "isFollowing": true,
    "canSendMessage": true,
    "followingCount": 6,
    "followedAt": "2026-07-25T10:30:00+09:00"
  },
  "timestamp": "2026-07-25T10:30:00+09:00"
}
```

| 필드 | 타입 | 설명 |
| --- | --- | --- |
| `memberId` | Long | 팔로우한 대상 회원 ID |
| `nickname` | String | 대상 회원의 닉네임 |
| `selectedCharacterId` | String | 대상 회원이 선택한 캐릭터 ID |
| `isFollowing` | Boolean | 현재 팔로우 여부, 항상 `true` |
| `canSendMessage` | Boolean | 대상 회원에게 쪽지를 보낼 수 있는지 여부 |
| `followingCount` | Long | 로그인한 회원이 현재 팔로우 중인 사용자 수 |
| `followedAt` | String | 팔로우 관계가 생성된 시각 |

#### Character Enum

| 값 | 설명 |
| --- | --- |
| `PALBANG` | 기본 팔방이 |
| `PALBANG_RABBIT` | 토끼 팔방이 |
| `PALBANG_DOG` | 강아지 팔방이 |

---

#### 200 OK — 이미 팔로우 중인 경우

```
{
  "success": true,
  "code": "MEMBER_ALREADY_FOLLOWING",
  "message": "이미 팔로우 중인 사용자입니다.",
  "data": {
    "memberId": 15,
    "nickname": "임장초보",
    "selectedCharacterId": "PALBANG_DOG",
    "isFollowing": true,
    "canSendMessage": true,
    "followingCount": 6,
    "followedAt": "2026-07-20T18:20:00+09:00"
  },
  "timestamp": "2026-07-22T10:30:00+09:00"
}
```

이미 팔로우 중인 경우 새로운 팔로우 관계를 만들지 않으며, 기존 `followedAt`을 반환한다.

---

### Exception

#### 400 Bad Request — 잘못된 회원 ID

`memberId`가 숫자가 아니거나 1보다 작은 값인 경우

```
{
  "success": false,
  "code": "COMMON_INVALID_REQUEST",
  "message": "입력값을 확인해 주세요.",
  "data": {
    "field": "memberId",
    "reason": "회원 ID는 1 이상의 숫자여야 합니다."
  },
  "timestamp": "2026-07-22T10:30:00+09:00"
}
```

---

#### 400 Bad Request — 본인 팔로우

```
{
  "success": false,
  "code": "MEMBER_SELF_FOLLOW_NOT_ALLOWED",
  "message": "본인은 팔로우할 수 없습니다.",
  "data": null,
  "timestamp": "2026-07-22T10:30:00+09:00"
}
```

---

#### 401 Unauthorized

Access Token이 없거나 유효하지 않은 경우

```
{
  "success": false,
  "code": "AUTH_ACCESS_TOKEN_INVALID",
  "message": "로그인이 필요합니다.",
  "data": null,
  "timestamp": "2026-07-22T10:30:00+09:00"
}
```

---

#### 404 Not Found — 회원 없음

팔로우 대상 회원이 존재하지 않거나 탈퇴한 경우

```
{
  "success": false,
  "code": "MEMBER_NOT_FOUND",
  "message": "회원 정보를 찾을 수 없습니다.",
  "data": null,
  "timestamp": "2026-07-22T10:30:00+09:00"
}
```

탈퇴한 회원인지 원래 존재하지 않는 회원인지는 구분하여 노출하지 않는다.

---

#### 500 Internal Server Error

```
{
  "success": false,
  "code": "COMMON_INTERNAL_SERVER_ERROR",
  "message": "일시적인 오류가 발생했습니다.",
  "data": null,
  "timestamp": "2026-07-22T10:30:00+09:00"
}
```

---

### 프론트 처리

```
다른 사용자 공개 프로필 진입
→ isFollowing = false이면 "팔로우" 버튼 표시
→ 사용자가 팔로우 버튼 선택
→ 사용자 팔로우 API 호출

팔로우 성공
→ isFollowing을 true로 변경
→ 버튼 문구를 "팔로우 해제"로 변경
→ followingCount 갱신
→ canSendMessage = true로 변경
→ MVP 쪽지 버튼 활성화

MEMBER_ALREADY_FOLLOWING 반환
→ 오류 안내를 표시하지 않음
→ 응답값으로 화면 상태를 팔로우 중으로 맞춤

MEMBER_SELF_FOLLOW_NOT_ALLOWED 반환
→ 팔로우 버튼을 숨기고 내 정보 수정 버튼 표시

MEMBER_NOT_FOUND 반환
→ "탈퇴했거나 존재하지 않는 사용자입니다." 안내
→ 이전 화면으로 이동
```

---

## 사용자 팔로우 해제

Method: DELETE
Progress: 완료
URI: /api/v1/members/{memberId}/follow
담당자: 박재명
연동여부: Yes

로그인한 회원이 팔로우 중인 사용자의 팔로우를 해제한다.

팔로우 관계는 즉시 삭제되며, 팔로우 해제 후에는 해당 사용자가 내 팔로잉 목록에서 제외된다.

동일한 팔로우 해제 요청이 반복되더라도 오류 없이 동일한 결과를 반환한다.

---

### Request

- Request HTTP Method: `DELETE`
- 인증 필요: 필요
- Request Body: 없음

#### Request Header

```
Authorization: Bearer {accessToken}
```

#### Path Variable

```
DELETE /api/v1/members/15/follow
```

| 필드 | 타입 | 필수 | 설명 |
| --- | --- | --- | --- |
| `memberId` | Long | Y | 팔로우를 해제할 대상 회원 ID |

#### 처리 기준

- 로그인한 회원 ID는 Access Token에서 확인한다.
- Path Variable의 `memberId`에 해당하는 회원을 팔로우 해제 대상으로 조회한다.
- 본인을 대상으로 팔로우 해제할 수 없다.
- 로그인한 회원의 `follow.follower_id`와 대상 회원의 `follow.following_id`가 일치하는 관계를 조회한다.
- 팔로우 관계가 존재하면 해당 관계를 물리적으로 삭제한다.
- 팔로우 관계는 별도의 이력 보존이 필요하지 않은 관계 데이터이므로 Soft Delete하지 않는다.
- 이미 팔로우가 해제된 사용자에게 동일한 요청을 보내도 오류를 반환하지 않는다.
- 동일한 요청이 여러 번 전달되더라도 결과는 항상 `isFollowing = false`가 되어야 한다.
- 팔로우 해제는 상대방이 로그인한 회원을 팔로우하는 관계에는 영향을 주지 않는다.
- 팔로우 해제 후 로그인한 회원의 현재 팔로잉 수를 다시 계산해 `followingCount`로 반환한다.
- 팔로우 해제 후 해당 사용자에게 새 쪽지를 보낼 수 없으므로 `canSendMessage`를 `false`로 반환한다.
- 팔로우 해제만으로 상대방에게 별도의 FCM 알림을 전송하지 않는다.

#### 삭제 대상 관계 예시

```
로그인 회원 ID: 1
팔로우 해제 대상 회원 ID: 15

DELETE follow
WHERE follower_id = 1
  AND following_id = 15
```

다음 반대 방향의 팔로우 관계는 삭제하지 않는다.

```
follower_id = 15
following_id = 1
```

---

#### MVP 메시지 전송 가능 여부

- 팔로우 해제 후에는 대상 회원에게 새 MVP 메시지를 전송할 수 없으므로 `canSendMessage = false`로 반환한다.
- 팔로우 해제 전에 수신자 알림함에 생성된 `MESSAGE` 알림은 삭제하지 않는다.
- 현재 범위에는 대화방·메시지 이력·읽음 상태가 없으므로 별도의 메시지방 보존·비활성화 처리를 수행하지 않는다.
- 팔로우를 다시 등록하면 대상 회원이 `ACTIVE`인 경우 새 MVP 메시지를 전송할 수 있다.

#### 200 OK — 팔로우 해제 완료

```json
{
  "success": true,
  "code": "MEMBER_UNFOLLOWED",
  "message": "사용자 팔로우를 해제했습니다.",
  "data": {
    "memberId": 15,
    "nickname": "임장초보",
    "selectedCharacterId": "PALBANG_DOG",
    "isFollowing": false,
    "canSendMessage": false,
    "followingCount": 5,
    "unfollowedAt": "2026-07-22T10:30:00+09:00"
  },
  "timestamp": "2026-07-22T10:30:00+09:00"
}
```

| 필드 | 타입 | 설명 |
| --- | --- | --- |
| `memberId` | Long | 팔로우를 해제한 대상 회원 ID |
| `nickname` | String | 대상 회원의 닉네임 |
| `selectedCharacterId` | String | 대상 회원이 선택한 캐릭터 ID |
| `isFollowing` | Boolean | 현재 팔로우 여부, 항상 `false` |
| `canSendMessage` | Boolean | 대상 회원에게 새 쪽지를 보낼 수 있는지 여부, 항상 `false` |
| `followingCount` | Long | 팔로우 해제 후 로그인한 회원의 팔로잉 수 |
| `unfollowedAt` | String | 팔로우 해제 요청이 처리된 시각 |

#### Character Enum

| 값 | 설명 |
| --- | --- |
| `PALBANG` | 기본 팔방이 |
| `PALBANG_RABBIT` | 토끼 팔방이 |
| `PALBANG_DOG` | 강아지 팔방이 |

---

#### 200 OK — 이미 팔로우하지 않은 경우

```json
{
  "success": true,
  "code": "MEMBER_ALREADY_UNFOLLOWED",
  "message": "이미 팔로우하지 않은 사용자입니다.",
  "data": {
    "memberId": 15,
    "nickname": "임장초보",
    "selectedCharacterId": "PALBANG_DOG",
    "isFollowing": false,
    "canSendMessage": false,
    "followingCount": 5,
    "unfollowedAt": "2026-07-22T10:30:00+09:00"
  },
  "timestamp": "2026-07-22T10:30:00+09:00"
}
```

팔로우 관계가 존재하지 않아도 멱등성을 보장하기 위해 정상 응답을 반환한다.

---

#### 400 Bad Request — 잘못된 회원 ID

`memberId`가 숫자가 아니거나 1보다 작은 값인 경우

```json
{
  "success": false,
  "code": "COMMON_INVALID_REQUEST",
  "message": "입력값을 확인해 주세요.",
  "data": {
    "field": "memberId",
    "reason": "회원 ID는 1 이상의 숫자여야 합니다."
  },
  "timestamp": "2026-07-22T10:30:00+09:00"
}
```

---

#### 400 Bad Request — 본인 팔로우 해제

```json
{
  "success": false,
  "code": "MEMBER_SELF_UNFOLLOW_NOT_ALLOWED",
  "message": "본인을 팔로우 해제 대상으로 지정할 수 없습니다.",
  "data": null,
  "timestamp": "2026-07-22T10:30:00+09:00"
}
```

---

#### 401 Unauthorized

Access Token이 없거나 유효하지 않은 경우

```json
{
  "success": false,
  "code": "AUTH_ACCESS_TOKEN_INVALID",
  "message": "로그인이 필요합니다.",
  "data": null,
  "timestamp": "2026-07-22T10:30:00+09:00"
}
```

---

#### 404 Not Found — 회원 없음

대상 회원이 존재하지 않거나 탈퇴한 경우

```json
{
  "success": false,
  "code": "MEMBER_NOT_FOUND",
  "message": "회원 정보를 찾을 수 없습니다.",
  "data": null,
  "timestamp": "2026-07-22T10:30:00+09:00"
}
```

탈퇴한 회원인지 원래 존재하지 않는 회원인지는 구분하여 노출하지 않는다.

---

#### 500 Internal Server Error

```json
{
  "success": false,
  "code": "COMMON_INTERNAL_SERVER_ERROR",
  "message": "일시적인 오류가 발생했습니다.",
  "data": null,
  "timestamp": "2026-07-22T10:30:00+09:00"
}
```

---

### 프론트 처리

```
다른 사용자 공개 프로필 진입
→ isFollowing = true이면 "팔로우 해제" 버튼 표시
→ 사용자가 팔로우 해제 버튼 선택
→ 팔로우 해제 확인 모달 표시
→ 확인 시 사용자 팔로우 해제 API 호출

팔로우 해제 성공
→ isFollowing을 false로 변경
→ 버튼 문구를 "팔로우"로 변경
→ followingCount 갱신
→ 쪽지 버튼 비활성화 또는 숨김

내 팔로잉 목록에서 팔로우 해제
→ API 성공 시 해당 사용자를 현재 목록에서 제거
→ 목록 상단의 팔로잉 수 갱신

MEMBER_ALREADY_UNFOLLOWED 반환
→ 오류 메시지를 표시하지 않음
→ 화면 상태를 isFollowing = false로 맞춤
→ 팔로잉 목록에 표시되어 있다면 목록에서 제거

MEMBER_SELF_UNFOLLOW_NOT_ALLOWED 반환
→ 팔로우 관련 버튼을 숨김
→ 내 정보 수정 버튼 표시

MEMBER_NOT_FOUND 반환
→ "탈퇴했거나 존재하지 않는 사용자입니다." 안내
→ 현재 목록에서 해당 사용자 제거
```

---

## 내 팔로잉 목록 조회

Method: GET
Progress: 완료
URI: /api/v1/members/me/followings
담당자: 박재명
연동여부: Yes

로그인한 회원이 현재 팔로우하고 있는 사용자 목록을 조회한다.

최근에 팔로우한 사용자부터 반환하며, 데이터가 많아져도 안정적으로 이어서 조회할 수 있도록 커서 페이지네이션을 사용한다.

각 사용자의 닉네임, 프로필 이미지, 선택 캐릭터, 공개에 동의한 연령대, 참여 스터디 수와 쪽지 전송 가능 여부를 반환한다. 관심 지역은 반환하지 않는다.

### Request

- HTTP Method: `GET`
- URI: `/api/v1/members/me/followings`
- 인증 필요: 필요

#### Query Parameter

```
GET /api/v1/members/me/followings?cursor=30&size=20
```

| 필드 | 타입 | 필수 | 기본값 | 설명 |
| --- | --- | --- | --- | --- |
| `cursor` | Long | N | 없음 | 다음 조회에 사용할 팔로우 ID |
| `size` | Integer | N | `20` | 조회 개수, 1~100 |

#### Request Header

```
Authorization: Bearer {accessToken}
```

### 처리 기준

- 로그인 회원이 팔로우한 `ACTIVE` 회원만 반환한다.
- 최근 팔로우한 순으로 반환한다.
- 연령대는 `ageGroupPublicAgreed=true`인 경우에만 반환하며, 공개에 동의하지 않은 경우 `null`을 반환한다.
- 관심 지역은 공개 동의 여부와 관계없이 응답 필드에 포함하지 않는다.
- 목록의 사용자는 모두 `isFollowing=true`, `canSendMessage=true`다.
- 정식 쪽지방 관련 ID는 반환하지 않는다.

### Response

#### 200 OK

```json
{
  "success": true,
  "code": "MEMBER_FOLLOWING_LIST_SUCCESS",
  "message": "팔로잉 목록 조회에 성공했습니다.",
  "data": {
    "content": [
      {
        "memberId": 12,
        "nickname": "옥수탐방러",
        "profileImageUrl": null,
        "selectedCharacterId": "PALBANG",
        "ageGroup": "TWENTIES",
        "participatingStudyCount": 4,
        "isFollowing": true,
        "canSendMessage": true,
        "followedAt": "2026-07-20T14:00:00+09:00"
      }
    ],
    "totalCount": 5,
    "nextCursor": null,
    "hasNext": false
  },
  "timestamp": "2026-07-24T16:05:00+09:00"
}
```

---

### 추가 Exception 예시

#### 400 Bad Request — 잘못된 커서

`cursor`가 숫자가 아니거나 1보다 작은 경우

```json
{
  "success": false,
  "code": "MEMBER_FOLLOWING_CURSOR_INVALID",
  "message": "팔로잉 목록 커서가 올바르지 않습니다.",
  "data": {
    "field": "cursor",
    "reason": "커서는 1 이상의 숫자여야 합니다."
  },
  "timestamp": "2026-07-22T10:30:00+09:00"
}
```

---

#### 400 Bad Request — 잘못된 조회 개수

`size`가 1보다 작거나 100을 초과한 경우

```json
{
  "success": false,
  "code": "COMMON_INVALID_REQUEST",
  "message": "입력값을 확인해 주세요.",
  "data": {
    "field": "size",
    "reason": "조회 개수는 1 이상 100 이하이어야 합니다."
  },
  "timestamp": "2026-07-22T10:30:00+09:00"
}
```

---

#### 401 Unauthorized

Access Token이 없거나 유효하지 않은 경우

```json
{
  "success": false,
  "code": "AUTH_ACCESS_TOKEN_INVALID",
  "message": "로그인이 필요합니다.",
  "data": null,
  "timestamp": "2026-07-22T10:30:00+09:00"
}
```

---

#### 404 Not Found

Access Token에 해당하는 회원이 존재하지 않거나 탈퇴한 경우

```json
{
  "success": false,
  "code": "MEMBER_NOT_FOUND",
  "message": "회원 정보를 찾을 수 없습니다.",
  "data": null,
  "timestamp": "2026-07-22T10:30:00+09:00"
}
```

---

#### 500 Internal Server Error

```json
{
  "success": false,
  "code": "COMMON_INTERNAL_SERVER_ERROR",
  "message": "일시적인 오류가 발생했습니다.",
  "data": null,
  "timestamp": "2026-07-22T10:30:00+09:00"
}
```

---

### 추가 프론트 처리 기준

```
마이페이지에서 팔로잉 목록 선택
→ cursor 없이 첫 번째 목록 조회
→ 최근 팔로우한 사용자부터 표시

목록 하단 도달
→ hasNext 확인
→ hasNext = true이면 nextCursor를 cursor로 전달
→ 다음 팔로잉 목록 조회
→ 기존 목록 뒤에 새로운 content 추가

사용자 카드 선택
→ GET /api/v1/members/{memberId} 호출
→ 해당 사용자의 공개 프로필 화면으로 이동

isFollowing = true
→ "팔로우 해제" 버튼 표시

canSendMessage = false
→ 쪽지 버튼 비활성화 또는 숨김

팔로우 해제 성공
→ 현재 목록에서 해당 사용자 제거
→ followingCount를 1 감소
→ 목록이 비어 있고 hasNext = true이면 다음 목록 추가 조회

content가 빈 배열
→ "아직 팔로우한 사용자가 없습니다." 표시

nextCursor = null이고 hasNext = false
→ 추가 조회 중지
→ 목록 하단 로딩 UI 제거
```

---

## 알림 수신 설정 조회

Method: GET
Progress: 완료
URI: /api/v1/members/me/notification-settings
담당자: 김윤석
연동여부: Yes

로그인한 회원의 서비스 알림과 광고성 알림 수신 동의 상태를 조회한다.

서비스 알림에는 스터디 활동, 팔로우, 임장 일정·시작, AI 리포트와 1:1 쪽지에 관한 알림이 포함된다.

이 API는 서버에 저장된 FCM Push 수신 동의 상태를 조회하며, Android 운영체제의 알림 권한 상태는 조회하지 않는다.

---

### Request

- Request HTTP Method: `GET`
- 인증 필요: 필요
- Request Body: 없음

#### Request Header

```
Authorization: Bearer {accessToken}
```

#### Query Parameter

없음

#### 처리 기준

- 로그인한 회원 ID는 Access Token에서 확인한다.
- 회원 상태가 `ACTIVE`인 경우에만 조회할 수 있다.
- `member.service_notification_agreed` 값을 서비스 알림 수신 동의 상태로 반환한다.
- `member.ad_notification_agreed` 값을 광고성 알림 수신 동의 상태로 반환한다.
- 서비스 알림과 광고성 알림은 서로 독립적으로 관리한다.
- 서비스 알림 수신 동의 여부가 광고성 알림 수신 동의 여부에 영향을 주지 않는다.
- 광고성 알림 수신 동의 여부가 서비스 알림 수신 동의 여부에 영향을 주지 않는다.
- FCM 토큰이 등록되지 않은 회원도 설정 상태는 조회할 수 있다.
- Android 운영체제에서 알림 권한이 거부된 상태여도 서버에 저장된 설정값을 그대로 반환한다.
- 이 API를 호출해도 알림 수신 설정은 변경되지 않는다.
- 개별 알림 유형별로 설정하지 않고, 서비스 알림 전체와 광고성 알림 전체를 각각 하나의 설정으로 관리한다.

#### 서비스 알림에 포함되는 항목

| 알림 유형 | 설명 |
| --- | --- |
| 스터디 가입 신청 | 스터디장에게 전달되는 새로운 가입 신청 알림 |
| 스터디 신청 결과 | 스터디 신청 승인 또는 거절 알림 |
| 신규 팔로우 | 다른 회원이 나를 새로 팔로우한 알림 |
| 스터디 일정 변경 | 임장 일시나 집결 장소 변경 알림 |
| 임장 사전 알림 | 임장 일정 D-1 등 사전 안내 |
| 임장 실제 시작 | 최초 임장 세션이 시작된 고정 후보 대상 알림 |
| 임장 종료 요청 | 종료하지 않은 참여자에게 보내는 종료 요청 |
| AI 리포트 완료 | AI 리포트 생성 완료 알림 |
| 1:1 새 쪽지 | 팔로잉 사용자가 보낸 새로운 쪽지 알림 |

#### 광고성 알림에 포함되는 항목

| 알림 유형 | 설명 |
| --- | --- |
| 이벤트 | 서비스 이벤트 안내 |
| 프로모션 | 포인트, 리포트 등 프로모션 안내 |
| 마케팅 정보 | 광고성 정보와 혜택 안내 |

---

### Response

#### 200 OK

```json
{
  "success": true,
  "code": "MEMBER_NOTIFICATION_SETTINGS_SUCCESS",
  "message": "알림 수신 설정 조회에 성공했습니다.",
  "data": {
    "serviceNotificationAgreed": true,
    "adNotificationAgreed": false
  },
  "timestamp": "2026-07-22T10:30:00+09:00"
}
```

| 필드 | 타입 | 설명 |
| --- | --- | --- |
| `serviceNotificationAgreed` | Boolean | 서비스 알림 수신 동의 여부 |
| `adNotificationAgreed` | Boolean | 광고성 알림 수신 동의 여부 |

#### 서비스 알림과 광고성 알림을 모두 허용한 경우

```json
{
  "success": true,
  "code": "MEMBER_NOTIFICATION_SETTINGS_SUCCESS",
  "message": "알림 수신 설정 조회에 성공했습니다.",
  "data": {
    "serviceNotificationAgreed": true,
    "adNotificationAgreed": true
  },
  "timestamp": "2026-07-22T10:30:00+09:00"
}
```

#### 모든 알림을 거부한 경우

```json
{
  "success": true,
  "code": "MEMBER_NOTIFICATION_SETTINGS_SUCCESS",
  "message": "알림 수신 설정 조회에 성공했습니다.",
  "data": {
    "serviceNotificationAgreed": false,
    "adNotificationAgreed": false
  },
  "timestamp": "2026-07-22T10:30:00+09:00"
}
```

서버의 서비스 알림 설정이 `false`이면 다음 알림에 대한 FCM Push를 발송하지 않는다.

```
스터디 가입 신청·승인·거절
신규 팔로우
스터디 일정 변경
임장 사전 알림
임장 실제 시작
임장 종료 요청
AI 리포트 생성 완료
1:1 새 쪽지
```

---

### Exception

#### 401 Unauthorized

Access Token이 없거나 유효하지 않은 경우

```json
{
  "success": false,
  "code": "AUTH_ACCESS_TOKEN_INVALID",
  "message": "로그인이 필요합니다.",
  "data": null,
  "timestamp": "2026-07-22T10:30:00+09:00"
}
```

---

#### 404 Not Found

Access Token에 해당하는 회원이 존재하지 않거나 탈퇴한 경우

```json
{
  "success": false,
  "code": "MEMBER_NOT_FOUND",
  "message": "회원 정보를 찾을 수 없습니다.",
  "data": null,
  "timestamp": "2026-07-22T10:30:00+09:00"
}
```

탈퇴한 회원인지 원래 존재하지 않는 회원인지는 구분하여 노출하지 않는다.

---

#### 500 Internal Server Error

```json
{
  "success": false,
  "code": "COMMON_INTERNAL_SERVER_ERROR",
  "message": "일시적인 오류가 발생했습니다.",
  "data": null,
  "timestamp": "2026-07-22T10:30:00+09:00"
}
```

---

### 프론트 처리

```
마이페이지의 알림 설정 화면 진입
→ 알림 수신 설정 조회 API 호출
→ serviceNotificationAgreed 값으로 서비스 알림 스위치 설정
→ adNotificationAgreed 값으로 광고성 알림 스위치 설정

serviceNotificationAgreed = true
→ 서비스 알림 스위치 활성화
→ 스터디·임장·리포트·1:1 쪽지 알림 수신 가능 상태로 표시

serviceNotificationAgreed = false
→ 서비스 알림 스위치 비활성화
→ "스터디 일정과 새 쪽지 알림을 받을 수 없습니다." 안내 가능

adNotificationAgreed = true
→ 광고성 알림 스위치 활성화

adNotificationAgreed = false
→ 광고성 알림 스위치 비활성화

서버의 서비스 알림 설정은 true이지만 Android 알림 권한이 거부된 경우
→ 서버 설정 스위치는 true로 표시
→ "기기 알림 권한이 꺼져 있습니다." 안내
→ Android 알림 설정 화면 이동 버튼 제공

FCM 토큰이 등록되지 않은 경우
→ 서버 설정값은 정상 표시
→ 앱 시작 또는 로그인 시 FCM 토큰 등록을 다시 시도

알림 스위치 변경
→ PATCH /api/v1/members/me/notification-settings 호출
→ 성공 응답의 설정값으로 화면 상태 갱신
```

---

## 알림 수신 설정 수정

Method: PATCH
Progress: 완료
URI: /api/v1/members/me/notification-settings
담당자: 김윤석
연동여부: Yes

로그인한 회원의 서비스 알림과 광고성 알림 수신 동의 상태를 수정한다.

요청에 포함된 설정만 변경하고, 포함되지 않은 설정은 기존 값을 유지한다.

서비스 알림에는 스터디 활동, 팔로우, 임장 일정·시작, AI 리포트와 1:1 새 쪽지 알림이 포함된다.

---

### Request

- Request HTTP Method: `PATCH`
- 인증 필요: 필요
- Content-Type: `application/json`

#### Request Header

```
Authorization: Bearer {accessToken}
```

#### Request Body

```json
{
  "serviceNotificationAgreed": true,
  "adNotificationAgreed": false
}
```

| 필드 | 타입 | 필수 | 설명 |
| --- | --- | --- | --- |
| `serviceNotificationAgreed` | Boolean | N | 서비스 알림 수신 동의 여부 |
| `adNotificationAgreed` | Boolean | N | 광고성 알림 수신 동의 여부 |

모든 필드는 선택 사항이지만, 최소 한 개 이상의 변경 항목을 전달해야 한다.

요청에 포함하지 않은 설정은 기존 값을 그대로 유지한다.

#### 서비스 알림만 변경

```json
{
  "serviceNotificationAgreed": false
}
```

#### 광고성 알림만 변경

```json
{
  "adNotificationAgreed": true
}
```

#### 모든 알림 활성화

```json
{
  "serviceNotificationAgreed": true,
  "adNotificationAgreed": true
}
```

#### 모든 알림 비활성화

```json
{
  "serviceNotificationAgreed": false,
  "adNotificationAgreed": false
}
```

#### 서비스 알림에 포함되는 항목

| 알림 유형 | 설명 |
| --- | --- |
| 스터디 가입 신청 | 스터디장에게 전달되는 새로운 가입 신청 알림 |
| 스터디 신청 결과 | 스터디 신청 승인 또는 거절 알림 |
| 신규 팔로우 | 다른 회원이 나를 새로 팔로우한 알림 |
| 스터디 일정 변경 | 임장 일시나 집결 장소 변경 알림 |
| 임장 사전 알림 | 임장 일정 D-1 등 사전 안내 |
| 임장 실제 시작 | 최초 임장 세션이 시작된 고정 후보 대상 알림 |
| 임장 종료 요청 | 임장을 종료하지 않은 참여자에게 보내는 알림 |
| AI 리포트 완료 | AI 리포트 생성 완료 알림 |
| 1:1 새 쪽지 | 팔로잉 사용자가 보낸 새로운 쪽지 알림 |

#### 광고성 알림에 포함되는 항목

| 알림 유형 | 설명 |
| --- | --- |
| 이벤트 | 서비스 이벤트 안내 |
| 프로모션 | 포인트, 리포트 등 프로모션 안내 |
| 마케팅 정보 | 광고성 정보와 혜택 안내 |

#### 처리 기준

- 로그인한 회원 ID는 Access Token에서 확인한다.
- 회원 상태가 `ACTIVE`인 경우에만 설정을 수정할 수 있다.
- 요청에 포함된 필드만 변경한다.
- 요청에 포함되지 않은 필드는 기존 값을 유지한다.
- `serviceNotificationAgreed`와 `adNotificationAgreed`는 서로 독립적으로 관리한다.
- 서비스 알림 설정을 변경해도 광고성 알림 설정은 자동으로 변경하지 않는다.
- 광고성 알림 설정을 변경해도 서비스 알림 설정은 자동으로 변경하지 않는다.
- `serviceNotificationAgreed`는 `member.service_notification_agreed`에 저장한다.
- `adNotificationAgreed`는 `member.ad_notification_agreed`에 저장한다.
- 두 필드 모두 Boolean 값만 허용한다.
- Request Body가 비어 있거나 변경 가능한 필드가 하나도 없으면 요청을 거절한다.
- 기존 설정과 같은 값을 요청해도 오류 없이 정상 처리한다.
- 서비스 알림을 비활성화하면 변경 시점 이후의 서비스성 FCM Push 발송 대상에서 제외한다.
- 광고성 알림을 비활성화하면 변경 시점 이후의 광고성 FCM Push 발송 대상에서 제외한다.
- 설정을 비활성화하더라도 등록된 FCM 토큰은 삭제하지 않는다.
- 설정을 다시 활성화하면 기존에 등록된 FCM 토큰을 이용하여 알림을 발송할 수 있다.
- FCM 토큰이 등록되지 않은 상태에서도 설정값은 정상적으로 저장한다.
- Android 운영체제의 알림 권한이 거부된 상태에서도 서버 설정값은 정상적으로 저장한다.
- 이 API는 Android 운영체제의 알림 권한을 변경하지 않는다.
- 이미 발생했거나 발송된 알림은 설정 변경으로 삭제하거나 회수하지 않는다.
- 설정 변경 후 최신 서비스 알림 및 광고성 알림 상태를 모두 반환한다.
- 수정 시 `member.updated_at`을 현재 시각으로 갱신한다.

#### 서비스 알림 발송 판단

```
서비스 알림 발생
→ serviceNotificationAgreed 확인
→ false이면 FCM Push 발송 제외
→ true이면 유효한 FCM 토큰 확인
→ 기기별 FCM Push 발송
```

#### 광고성 알림 발송 판단

```
광고성 알림 발생
→ adNotificationAgreed 확인
→ false이면 FCM Push 발송 제외
→ true이면 유효한 FCM 토큰 확인
→ 기기별 FCM Push 발송
```

---

### Response

#### 200 OK

```json
{
  "success": true,
  "code": "MEMBER_NOTIFICATION_SETTINGS_UPDATED",
  "message": "알림 수신 설정이 수정되었습니다.",
  "data": {
    "serviceNotificationAgreed": true,
    "adNotificationAgreed": false,
    "updatedAt": "2026-07-22T10:30:00+09:00"
  },
  "timestamp": "2026-07-22T10:30:00+09:00"
}
```

| 필드 | 타입 | 설명 |
| --- | --- | --- |
| `serviceNotificationAgreed` | Boolean | 수정 후 서비스 알림 수신 동의 여부 |
| `adNotificationAgreed` | Boolean | 수정 후 광고성 알림 수신 동의 여부 |
| `updatedAt` | String | 알림 수신 설정이 반영된 시각 |

요청에 한 개의 필드만 포함했더라도 응답에는 현재 서비스 알림과 광고성 알림 설정을 모두 반환한다.

---

#### 200 OK — 기존과 같은 값으로 요청한 경우

```json
{
  "success": true,
  "code": "MEMBER_NOTIFICATION_SETTINGS_UPDATED",
  "message": "알림 수신 설정이 수정되었습니다.",
  "data": {
    "serviceNotificationAgreed": true,
    "adNotificationAgreed": false,
    "updatedAt": "2026-07-22T10:30:00+09:00"
  },
  "timestamp": "2026-07-22T10:30:00+09:00"
}
```

기존 값과 같은 설정을 전달해도 중복 요청 오류를 반환하지 않는다.

---

### Exception

#### 400 Bad Request — 변경 항목 없음

Request Body가 비어 있거나 수정 가능한 설정이 전달되지 않은 경우

```json
{
  "success": false,
  "code": "MEMBER_NOTIFICATION_SETTINGS_UPDATE_EMPTY",
  "message": "변경할 알림 설정을 입력해 주세요.",
  "data": null,
  "timestamp": "2026-07-22T10:30:00+09:00"
}
```

---

#### 400 Bad Request — 잘못된 서비스 알림 값

Boolean이 아닌 값이 전달된 경우

```json
{
  "success": false,
  "code": "COMMON_INVALID_REQUEST",
  "message": "입력값을 확인해 주세요.",
  "data": {
    "field": "serviceNotificationAgreed",
    "reason": "true 또는 false 값을 입력해 주세요."
  },
  "timestamp": "2026-07-22T10:30:00+09:00"
}
```

---

#### 400 Bad Request — 잘못된 광고성 알림 값

```json
{
  "success": false,
  "code": "COMMON_INVALID_REQUEST",
  "message": "입력값을 확인해 주세요.",
  "data": {
    "field": "adNotificationAgreed",
    "reason": "true 또는 false 값을 입력해 주세요."
  },
  "timestamp": "2026-07-22T10:30:00+09:00"
}
```

---

#### 400 Bad Request — null 값 전달

```json
{
  "success": false,
  "code": "COMMON_INVALID_REQUEST",
  "message": "입력값을 확인해 주세요.",
  "data": {
    "field": "serviceNotificationAgreed",
    "reason": "값을 비워 둘 수 없습니다."
  },
  "timestamp": "2026-07-22T10:30:00+09:00"
}
```

필드를 변경하지 않으려면 `null`로 전달하지 않고 Request Body에서 해당 필드를 제외한다.

---

#### 401 Unauthorized

Access Token이 없거나 유효하지 않은 경우

```json
{
  "success": false,
  "code": "AUTH_ACCESS_TOKEN_INVALID",
  "message": "로그인이 필요합니다.",
  "data": null,
  "timestamp": "2026-07-22T10:30:00+09:00"
}
```

---

#### 404 Not Found

Access Token에 해당하는 회원이 존재하지 않거나 탈퇴한 경우

```json
{
  "success": false,
  "code": "MEMBER_NOT_FOUND",
  "message": "회원 정보를 찾을 수 없습니다.",
  "data": null,
  "timestamp": "2026-07-22T10:30:00+09:00"
}
```

탈퇴한 회원인지 원래 존재하지 않는 회원인지는 구분하여 노출하지 않는다.

---

#### 500 Internal Server Error

```json
{
  "success": false,
  "code": "COMMON_INTERNAL_SERVER_ERROR",
  "message": "일시적인 오류가 발생했습니다.",
  "data": null,
  "timestamp": "2026-07-22T10:30:00+09:00"
}
```

---

### 프론트 처리

```
알림 설정 화면 진입
→ GET /api/v1/members/me/notification-settings 호출
→ 현재 설정값으로 각각의 스위치 상태 구성

서비스 알림 스위치 변경
→ 변경된 serviceNotificationAgreed만 Request Body에 포함
→ 알림 수신 설정 수정 API 호출
→ 성공 응답값으로 스위치 상태 확정

광고성 알림 스위치 변경
→ 변경된 adNotificationAgreed만 Request Body에 포함
→ 알림 수신 설정 수정 API 호출
→ 성공 응답값으로 스위치 상태 확정

serviceNotificationAgreed = false
→ 스터디 신청 결과·일정 변경·임장 사전 알림·임장 종료 요청
→ AI 리포트 완료·1:1 새 쪽지 FCM Push 수신 중단

serviceNotificationAgreed = true
→ Android 알림 권한 확인
→ 권한이 없으면 기기 설정에서 권한 허용 안내

adNotificationAgreed를 false에서 true로 변경
→ 광고성 정보 수신 동의 안내 표시
→ 사용자 확인 후 수정 API 호출

API 요청 성공
→ 응답의 serviceNotificationAgreed와 adNotificationAgreed를 화면 상태에 반영
→ 저장 완료 메시지 표시

API 요청 실패
→ 스위치를 변경 전 상태로 복원
→ "알림 설정을 저장하지 못했습니다." 안내
→ 재시도 버튼 제공

서버 설정은 true이지만 Android 알림 권한이 거부된 경우
→ 서버 설정 스위치는 true로 유지
→ "기기 알림 권한이 꺼져 있습니다." 안내
→ Android 앱 알림 설정 화면 이동 버튼 제공
```

---

## 기기별 FCM 토큰 등록·갱신

Method: PUT
Progress: 완료
URI: /api/v1/members/me/devices/{deviceId}/fcm-token
담당자: 김윤석
연동여부: Yes

현재 기기에서 Firebase Cloud Messaging으로 발급받은 FCM 토큰을 로그인한 회원과 연결한다.

같은 기기에서 FCM 토큰이 새로 발급되거나 갱신된 경우 기존 토큰을 새로운 값으로 변경한다.

동일한 요청이 반복되어도 중복 데이터를 생성하지 않으며, 스터디·임장·리포트·1:1 새 쪽지 알림 발송에 사용한다.

---

### Request

- Request HTTP Method: `PUT`
- 인증 필요: 필요
- Content-Type: `application/json`

#### Request Header

```
Authorization: Bearer {accessToken}
```

#### Path Variable

```
PUT /api/v1/members/me/devices/550e8400-e29b-41d4-a716-446655440000/fcm-token
```

| 필드 | 타입 | 필수 | 설명 |
| --- | --- | --- | --- |
| `deviceId` | String | Y | 앱 설치 단위를 식별하는 기기 ID, 1자 이상 255자 이하 |

`deviceId`는 IMEI, 전화번호와 같은 하드웨어·개인 식별값을 사용하지 않는다.

Android 앱을 처음 실행할 때 UUID를 생성하여 로컬 저장소에 보관하고, 이후 동일한 값을 계속 사용한다.

#### Request Body

```json
{
  "fcmToken": "firebase-cloud-messaging-token"
}
```

| 필드 | 타입 | 필수 | 설명 |
| --- | --- | --- | --- |
| `fcmToken` | String | Y | Firebase에서 현재 앱 설치 환경에 발급한 FCM 토큰 |

#### 처리 기준

- 로그인한 회원 ID는 Access Token에서 확인한다.
- 회원 상태가 `ACTIVE`인 경우에만 FCM 토큰을 등록할 수 있다.
- `deviceId`와 `fcmToken`의 앞뒤 공백을 제거한 후 유효성을 검사한다.
- 공백을 제거한 값이 비어 있으면 요청을 거절한다.
- `deviceId`는 앱 설치 시 생성한 UUID 사용을 권장한다.
- IMEI, Android ID와 같은 하드웨어 식별자를 서버에 전달하거나 저장하지 않는다.
- 회원과 기기의 FCM 토큰 정보가 존재하지 않으면 새로운 데이터를 생성한다.
- 동일한 `member_id`와 `device_id`의 정보가 이미 존재하면 기존 `token`을 새로운 FCM 토큰으로 갱신한다.
- 기존 FCM 토큰과 요청한 토큰이 같아도 오류 없이 정상 처리한다.
- 동일한 요청이 여러 번 전달되어도 FCM 토큰 데이터가 중복 생성되지 않아야 한다.
- 하나의 회원은 여러 기기의 FCM 토큰을 등록할 수 있다.
- 하나의 `deviceId`는 현재 로그인한 한 명의 회원에게만 연결되어야 한다.
- 같은 기기에서 다른 회원으로 로그인한 경우 이전 회원과 해당 기기의 연결을 해제하고 현재 로그인한 회원에게 연결한다.
- 같은 FCM 토큰이 다른 회원이나 다른 기기에 연결되어 있으면 이전 연결을 제거한 후 현재 요청 정보로 갱신한다.
- 회원·기기·토큰 관계 변경은 하나의 트랜잭션에서 처리한다.
- FCM 토큰 등록은 서비스 알림 또는 광고성 알림 수신 동의를 의미하지 않는다.
- 실제 Push 발송 시 알림 종류에 따라 다음 회원 설정을 확인한다.

```
서비스성 알림
→ serviceNotificationAgreed 확인

광고성 알림
→ adNotificationAgreed 확인
```

- 서비스 알림에는 다음 항목이 포함된다.

```
스터디 가입 신청·승인·거절
신규 팔로우
스터디 일정 변경
임장 사전 알림
임장 실제 시작
임장 종료 요청
AI 리포트 생성 완료
1:1 새 쪽지
```

- 회원의 알림 수신 설정이 `false`여도 FCM 토큰 자체는 등록할 수 있다.
- 이후 알림 설정을 다시 활성화하면 기존에 등록한 FCM 토큰을 사용할 수 있다.
- Android 운영체제의 알림 권한이 거부된 상태여도 토큰 등록 요청은 정상 처리한다.
- Android 운영체제의 알림 권한 상태는 이 API에서 저장하거나 변경하지 않는다.
- FCM 토큰은 로그에 원문으로 남기지 않는다.
- 오류 로그가 필요한 경우 토큰 전체가 아닌 일부 마스킹 값만 기록한다.
- 등록 또는 갱신 시 `fcm_token.updated_at`을 현재 시각으로 변경한다.

#### 데이터 제약조건

```
UNIQUE(member_id, device_id)
UNIQUE(token)
```

#### 신규 등록 예시

```
회원 ID: 1
기기 ID: device-A
기존 데이터: 없음

→ 새로운 FCM 토큰 데이터 생성
```

#### 같은 기기의 토큰 갱신 예시

```
회원 ID: 1
기기 ID: device-A
기존 토큰: old-token
새 토큰: new-token

→ 기존 행의 token을 new-token으로 변경
```

#### 같은 기기에서 다른 회원으로 로그인한 경우

```
기기 ID: device-A
기존 연결 회원: 1
현재 로그인 회원: 15

→ 회원 1과 device-A의 연결 제거
→ 회원 15와 device-A의 FCM 토큰 연결
```

#### 알림 발송 대상 확인

```
알림 이벤트 발생
→ 알림 종류 확인
→ 회원 알림 수신 설정 확인
→ 회원에게 등록된 유효한 FCM 토큰 조회
→ 기기별 Push 발송
```

---

### Response

#### 200 OK — 신규 등록

```json
{
  "success": true,
  "code": "MEMBER_FCM_TOKEN_SAVED",
  "message": "기기 알림 정보가 등록되었습니다.",
  "data": {
    "deviceId": "550e8400-e29b-41d4-a716-446655440000",
    "registered": true,
    "updatedAt": "2026-07-22T10:30:00+09:00"
  },
  "timestamp": "2026-07-22T10:30:00+09:00"
}
```

| 필드 | 타입 | 설명 |
| --- | --- | --- |
| `deviceId` | String | FCM 토큰이 연결된 앱 설치 기기 ID |
| `registered` | Boolean | 현재 회원과 기기의 FCM 토큰 등록 여부, 항상 `true` |
| `updatedAt` | String | FCM 토큰을 등록하거나 마지막으로 갱신한 시각 |

---

#### 200 OK — 기존 토큰 갱신

```json
{
  "success": true,
  "code": "MEMBER_FCM_TOKEN_SAVED",
  "message": "기기 알림 정보가 갱신되었습니다.",
  "data": {
    "deviceId": "550e8400-e29b-41d4-a716-446655440000",
    "registered": true,
    "updatedAt": "2026-07-22T10:30:00+09:00"
  },
  "timestamp": "2026-07-22T10:30:00+09:00"
}
```

신규 등록과 기존 토큰 갱신 모두 `200 OK`를 반환한다.

프론트에서는 응답 메시지의 차이를 기준으로 별도 화면 처리를 하지 않고, `registered = true` 여부만 확인한다.

---

#### 200 OK — 같은 토큰을 다시 등록한 경우

```json
{
  "success": true,
  "code": "MEMBER_FCM_TOKEN_SAVED",
  "message": "기기 알림 정보가 등록되었습니다.",
  "data": {
    "deviceId": "550e8400-e29b-41d4-a716-446655440000",
    "registered": true,
    "updatedAt": "2026-07-22T10:30:00+09:00"
  },
  "timestamp": "2026-07-22T10:30:00+09:00"
}
```

같은 요청이 반복되어도 중복 오류를 반환하지 않는다.

---

### Exception

#### 400 Bad Request — FCM 토큰 누락

Request Body에 `fcmToken`이 없거나 빈 문자열인 경우

```json
{
  "success": false,
  "code": "COMMON_INVALID_REQUEST",
  "message": "입력값을 확인해 주세요.",
  "data": {
    "field": "fcmToken",
    "reason": "FCM 토큰은 필수 값입니다."
  },
  "timestamp": "2026-07-22T10:30:00+09:00"
}
```

---

#### 400 Bad Request — 잘못된 기기 ID

`deviceId`가 비어 있거나 허용된 길이를 벗어난 경우

```json
{
  "success": false,
  "code": "MEMBER_DEVICE_ID_INVALID",
  "message": "기기 식별 정보가 올바르지 않습니다.",
  "data": {
    "field": "deviceId",
    "reason": "기기 ID는 1자 이상 255자 이하이어야 합니다."
  },
  "timestamp": "2026-07-22T10:30:00+09:00"
}
```

---

#### 400 Bad Request — FCM 토큰 형식 오류

공백만 존재하거나 서버에서 허용하는 최대 길이를 초과한 경우

```json
{
  "success": false,
  "code": "MEMBER_FCM_TOKEN_INVALID",
  "message": "FCM 토큰 정보가 올바르지 않습니다.",
  "data": {
    "field": "fcmToken",
    "reason": "유효한 FCM 토큰을 입력해 주세요."
  },
  "timestamp": "2026-07-22T10:30:00+09:00"
}
```

---

#### 401 Unauthorized

Access Token이 없거나 유효하지 않은 경우

```json
{
  "success": false,
  "code": "AUTH_ACCESS_TOKEN_INVALID",
  "message": "로그인이 필요합니다.",
  "data": null,
  "timestamp": "2026-07-22T10:30:00+09:00"
}
```

---

#### 404 Not Found

Access Token에 해당하는 회원이 존재하지 않거나 탈퇴한 경우

```json
{
  "success": false,
  "code": "MEMBER_NOT_FOUND",
  "message": "회원 정보를 찾을 수 없습니다.",
  "data": null,
  "timestamp": "2026-07-22T10:30:00+09:00"
}
```

탈퇴한 회원인지 원래 존재하지 않는 회원인지는 구분하여 노출하지 않는다.

---

#### 500 Internal Server Error

```json
{
  "success": false,
  "code": "COMMON_INTERNAL_SERVER_ERROR",
  "message": "일시적인 오류가 발생했습니다.",
  "data": null,
  "timestamp": "2026-07-22T10:30:00+09:00"
}
```

---

### 프론트 처리

```
앱 최초 실행
→ 로컬에 저장된 deviceId 확인
→ deviceId가 없으면 UUID 생성
→ 생성한 deviceId를 로컬 저장소에 보관

일반 로그인 또는 소셜 로그인 성공
→ Firebase에서 현재 FCM 토큰 조회
→ 로컬 deviceId 확인
→ 기기별 FCM 토큰 등록·갱신 API 호출

Firebase의 onNewToken 이벤트 발생
→ 새로 발급된 fcmToken 확인
→ 현재 로그인 상태인지 확인
→ 로그인 상태이면 동일한 deviceId로 등록·갱신 API 호출

FCM 토큰 등록 성공
→ registered = true 확인
→ 별도의 사용자 안내 없이 앱 이용 계속

FCM 토큰 등록 실패
→ 로그인 자체는 유지
→ 앱 이용을 차단하지 않음
→ 네트워크 연결 후 백그라운드에서 재시도

같은 기기에서 다른 회원으로 로그인
→ 새 회원의 Access Token으로 FCM 토큰 등록·갱신 API 호출
→ 서버에서 이전 회원과 기기의 연결을 정리

serviceNotificationAgreed = false
→ FCM 토큰은 등록 상태로 유지
→ 서비스성 Push 발송 대상에서만 제외

adNotificationAgreed = false
→ FCM 토큰은 등록 상태로 유지
→ 광고성 Push 발송 대상에서만 제외

Android 알림 권한이 거부된 경우
→ FCM 토큰 등록은 수행
→ 알림이 필요한 화면에서 기기 권한 허용 안내 표시

로그아웃
→ 현재 deviceId를 로그아웃 API에 전달하거나
→ 기기별 FCM 토큰 삭제 API를 호출하여 현재 회원과 기기의 연결 해제
```

---

## 기기별 FCM 토큰 삭제

Method: DELETE
Progress: 완료
URI: /api/v1/members/me/devices/{deviceId}/fcm-token
담당자: 김윤석
연동여부: Yes

현재 기기와 로그인한 회원 사이의 FCM 토큰 연결을 삭제한다.

로그아웃하거나 현재 기기에서 Push 알림 수신 연결을 해제할 때 사용한다.

FCM 토큰은 보존해야 하는 회원 활동 이력이 아니므로 Soft Delete하지 않고 물리적으로 삭제한다. 동일한 삭제 요청이 반복되더라도 오류 없이 같은 결과를 반환한다.

---

### Request

- Request HTTP Method: `DELETE`
- 인증 필요: 필요
- Request Body: 없음

#### Request Header

```
Authorization: Bearer {accessToken}
```

#### Path Variable

```
DELETE /api/v1/members/me/devices/550e8400-e29b-41d4-a716-446655440000/fcm-token
```

| 필드 | 타입 | 필수 | 설명 |
| --- | --- | --- | --- |
| `deviceId` | String | Y | FCM 토큰 연결을 해제할 앱 설치 단위의 기기 ID, 1자 이상 255자 이하 |

`deviceId`는 앱 최초 실행 시 생성하고 로컬에 저장한 UUID를 사용한다.

IMEI, 전화번호, Android ID와 같은 하드웨어·개인 식별값은 사용하지 않는다.

#### 처리 기준

- 로그인한 회원 ID는 Access Token에서 확인한다.
- 회원 상태가 `ACTIVE`인 경우에만 처리할 수 있다.
- `deviceId`의 앞뒤 공백을 제거한 후 유효성을 검사한다.
- 공백 제거 후 값이 비어 있으면 요청을 거절한다.
- `member_id`와 `device_id`가 모두 일치하는 FCM 토큰 정보를 조회한다.
- 일치하는 정보가 존재하면 `fcm_token` 테이블에서 물리적으로 삭제한다.
- 동일한 기기에 여러 토큰 행이 존재하지 않도록 데이터 제약조건을 적용한다.
- 해당 회원과 기기의 연결만 삭제한다.
- 다른 회원에게 연결된 같은 `deviceId`의 정보는 삭제하지 않는다.
- 같은 회원의 다른 기기에 등록된 FCM 토큰은 유지한다.
- FCM 토큰 정보가 이미 삭제되었거나 존재하지 않아도 오류를 반환하지 않는다.
- 동일한 삭제 요청이 여러 번 전달되더라도 결과는 항상 `registered = false`여야 한다.
- FCM 토큰 삭제는 회원의 알림 수신 동의 값을 변경하지 않는다.
- 다음 회원 설정은 그대로 유지한다.

```
serviceNotificationAgreed
adNotificationAgreed
```

- 토큰을 삭제한 이후 같은 기기에서 다시 로그인하면 FCM 토큰 등록·갱신 API를 다시 호출할 수 있다.
- FCM 토큰이 삭제되면 해당 회원에게 현재 기기를 대상으로 하는 서비스 알림과 광고성 알림을 발송하지 않는다.
- 동일 회원의 다른 기기 토큰이 남아 있다면 다른 기기에는 Push 알림을 발송할 수 있다.
- 이미 FCM 발송 요청이 진행 중인 알림은 토큰 삭제와 동시에 회수할 수 없다.
- 삭제 작업과 로그아웃 토큰 무효화 작업을 함께 처리하는 경우에는 하나의 서비스 흐름에서 순서를 관리한다.
- FCM 토큰 원문은 응답과 로그에 포함하지 않는다.

#### 삭제 대상 예시

```
로그인 회원 ID: 1
기기 ID: device-A

DELETE FROM fcm_token
WHERE member_id = 1
  AND device_id = 'device-A'
```

#### 다른 기기 정보 유지 예시

```
회원 1
- device-A
- device-B

device-A 삭제 요청
→ device-A 토큰만 삭제
→ device-B 토큰은 유지
```

#### 데이터 제약조건

```
UNIQUE(member_id, device_id)
UNIQUE(token)
```

---

### Response

#### 200 OK — FCM 토큰 삭제 완료

```json
{
  "success": true,
  "code": "MEMBER_FCM_TOKEN_DELETED",
  "message": "기기 알림 연결이 해제되었습니다.",
  "data": {
    "deviceId": "550e8400-e29b-41d4-a716-446655440000",
    "registered": false,
    "deletedAt": "2026-07-22T10:30:00+09:00"
  },
  "timestamp": "2026-07-22T10:30:00+09:00"
}
```

| 필드 | 타입 | 설명 |
| --- | --- | --- |
| `deviceId` | String | FCM 토큰 연결을 해제한 기기 ID |
| `registered` | Boolean | 현재 회원과 기기의 FCM 토큰 등록 여부, 항상 `false` |
| `deletedAt` | String | 삭제 요청이 정상적으로 처리된 시각 |

---

#### 200 OK — 이미 삭제된 경우

```json
{
  "success": true,
  "code": "MEMBER_FCM_TOKEN_ALREADY_DELETED",
  "message": "이미 기기 알림 연결이 해제되어 있습니다.",
  "data": {
    "deviceId": "550e8400-e29b-41d4-a716-446655440000",
    "registered": false,
    "deletedAt": "2026-07-22T10:30:00+09:00"
  },
  "timestamp": "2026-07-22T10:30:00+09:00"
}
```

FCM 토큰 정보가 존재하지 않더라도 멱등성을 보장하기 위해 정상 응답을 반환한다.

프론트에서는 두 응답을 구분할 필요 없이 `registered = false`만 확인한다.

---

### Exception

#### 400 Bad Request — 잘못된 기기 ID

`deviceId`가 비어 있거나 허용된 길이를 벗어난 경우

```json
{
  "success": false,
  "code": "MEMBER_DEVICE_ID_INVALID",
  "message": "기기 식별 정보가 올바르지 않습니다.",
  "data": {
    "field": "deviceId",
    "reason": "기기 ID는 1자 이상 255자 이하이어야 합니다."
  },
  "timestamp": "2026-07-22T10:30:00+09:00"
}
```

---

#### 401 Unauthorized

Access Token이 없거나 유효하지 않은 경우

```json
{
  "success": false,
  "code": "AUTH_ACCESS_TOKEN_INVALID",
  "message": "로그인이 필요합니다.",
  "data": null,
  "timestamp": "2026-07-22T10:30:00+09:00"
}
```

---

#### 404 Not Found

Access Token에 해당하는 회원이 존재하지 않거나 탈퇴한 경우

```json
{
  "success": false,
  "code": "MEMBER_NOT_FOUND",
  "message": "회원 정보를 찾을 수 없습니다.",
  "data": null,
  "timestamp": "2026-07-22T10:30:00+09:00"
}
```

탈퇴한 회원인지 원래 존재하지 않는 회원인지는 구분하여 노출하지 않는다.

---

#### 500 Internal Server Error

```json
{
  "success": false,
  "code": "COMMON_INTERNAL_SERVER_ERROR",
  "message": "일시적인 오류가 발생했습니다.",
  "data": null,
  "timestamp": "2026-07-22T10:30:00+09:00"
}
```

---

### 프론트 처리

```
로그아웃
→ 로컬에 저장된 deviceId 확인
→ 기기별 FCM 토큰 삭제 API 호출
→ 성공 여부와 관계없이 로그아웃 절차 계속 진행

FCM 토큰 삭제 성공
→ registered = false 확인
→ 현재 회원과 기기의 Push 연결 해제 완료
→ Access Token과 Refresh Token 삭제
→ 로그인 화면으로 이동

MEMBER_FCM_TOKEN_ALREADY_DELETED 반환
→ 오류 메시지를 표시하지 않음
→ 이미 연결 해제된 상태로 간주
→ 로그아웃 절차 계속 진행

FCM 토큰 삭제 API 실패
→ 사용자 로그아웃을 막지 않음
→ 삭제 요청 정보를 로컬 재시도 큐에 저장
→ 네트워크 연결 후 가능한 경우 재시도

같은 회원이 여러 기기를 사용하는 경우
→ 현재 deviceId의 토큰만 삭제
→ 다른 기기의 로그인과 Push 알림은 유지

서비스 알림 스위치만 비활성화
→ FCM 토큰 삭제 API 호출하지 않음
→ PATCH /api/v1/members/me/notification-settings만 호출

다른 회원으로 다시 로그인
→ 현재 로그인한 회원의 Access Token으로
→ PUT /api/v1/members/me/devices/{deviceId}/fcm-token 호출
→ 새로운 회원과 현재 기기를 다시 연결
```

---

## 내 스터디 목록 조회

Method: GET
Progress: 진행 중
URI: /api/v1/members/me/studies
담당자: 박재명
연동여부: Yes

마이페이지의 참여 스터디 요약과 진행 중·완료 스터디 목록에 사용한다.

로그인한 회원이 스터디장 또는 스터디원으로 참여하고 있는 스터디 목록을 조회한다.

진행 중인 스터디와 완료된 스터디를 구분하여 조회할 수 있으며, 완료된 스터디는 과거 임장 기록과 AI 리포트를 확인하는 읽기 전용 화면으로 진입한다.

### Request

- HTTP Method: `GET`
- URI: `/api/v1/members/me/studies`
- 인증 필요: 필요

#### Query Parameter

```
GET /api/v1/members/me/studies?status=ACTIVE&page=0&size=20
```

| 필드 | 타입 | 필수 | 기본값 | 설명 |
| --- | --- | --- | --- | --- |
| `status` | String | N | `ALL` | `ALL`, `ACTIVE`, `IN_PROGRESS`, `COMPLETED` |
| `page` | Integer | N | `0` | 페이지 번호 |
| `size` | Integer | N | `20` | 페이지 크기 |

`ACTIVE` 필터는 `RECRUITING`, `CLOSED`, `IN_PROGRESS` 상태를 포함한다.

#### Request Header

```
Authorization: Bearer {accessToken}
```

### 처리 기준

- 스터디장 또는 `study_member.status=ACTIVE`인 스터디만 반환한다.
- `IN_PROGRESS` 필터는 스터디 상태가 정확히 `IN_PROGRESS`인 항목만 반환한다.
- 대기·거절 신청과 `REMOVED` 멤버 관계는 포함하지 않는다.
- 완료 스터디는 `readOnly=true`로 반환한다.
- 진행 스터디(`RECRUITING`·`CLOSED`·`IN_PROGRESS`)를 완료 스터디보다 먼저 노출하고, 각 그룹 내에서는 가입(참여) 시각 내림차순으로 정렬하며(리더처럼 참여 이력이 없으면 스터디 생성 시각을 사용), 동일 시각은 `studyId` 내림차순으로 정렬한다.
- 다음 일정, 집결 장소와 읽지 않은 채팅 수를 함께 반환한다.
- `pendingReviewCount`는 본인을 제외한 현재 활성 스터디원 중 로그인 회원이 아직 평가하지 않은 인원 수다.
- 완료 스터디의 `pendingReviewCount=0`이면 프론트는 리뷰 작성 버튼 대신 전체 작성 완료 상태를 표시한다.
- `READY`, `REPORTING` 상태는 사용하지 않는다.

### Response

#### 200 OK

```json
{
  "success": true,
  "code": "MEMBER_STUDY_LIST_SUCCESS",
  "message": "내 스터디 목록 조회에 성공했습니다.",
  "data": {
    "content": [
      {
        "studyId": 10,
        "title": "옥수동 주말 임장",
        "intro": "교통과 단지 환경을 확인합니다.",
        "goal": "역 접근성·경사·주변 소음을 함께 확인합니다.",
        "status": "CLOSED",
        "role": "MEMBER",
        "apartment": {
          "apartmentId": 15,
          "name": "래미안 옥수 리버젠"
        },
        "nextSchedule": {
          "scheduleId": 7,
          "startAt": "2026-07-27T15:00:00+09:00",
          "meetingPlace": "옥수역 3번 출구"
        },
        "unreadChatCount": 3,
        "pendingReviewCount": 2,
        "readOnly": false
      }
    ],
    "totalElements": 3,
    "page": 0,
    "size": 20,
    "totalPages": 1
  },
  "timestamp": "2026-07-24T16:30:00+09:00"
}
```

---

### 추가 Exception 예시

#### 400 Bad Request — 잘못된 조회 상태

```json
{
  "success": false,
  "code": "MEMBER_STUDY_STATUS_INVALID",
  "message": "조회할 수 없는 내 스터디 상태입니다.",
  "data": {
    "field": "status",
    "allowedValues": [
      "ACTIVE",
      "COMPLETED",
      "ALL"
    ]
  },
  "timestamp": "2026-07-22T10:30:00+09:00"
}
```

---

#### 400 Bad Request — 잘못된 페이지 번호

```json
{
  "success": false,
  "code": "COMMON_INVALID_REQUEST",
  "message": "입력값을 확인해 주세요.",
  "data": {
    "field": "page",
    "reason": "페이지 번호는 0 이상이어야 합니다."
  },
  "timestamp": "2026-07-22T10:30:00+09:00"
}
```

---

#### 400 Bad Request — 잘못된 페이지 크기

```json
{
  "success": false,
  "code": "COMMON_INVALID_REQUEST",
  "message": "입력값을 확인해 주세요.",
  "data": {
    "field": "size",
    "reason": "페이지 크기는 1 이상 100 이하이어야 합니다."
  },
  "timestamp": "2026-07-22T10:30:00+09:00"
}
```

---

#### 401 Unauthorized

Access Token이 없거나 유효하지 않은 경우

```json
{
  "success": false,
  "code": "AUTH_ACCESS_TOKEN_INVALID",
  "message": "로그인이 필요합니다.",
  "data": null,
  "timestamp": "2026-07-22T10:30:00+09:00"
}
```

---

#### 404 Not Found

Access Token에 해당하는 회원이 존재하지 않거나 탈퇴한 경우

```json
{
  "success": false,
  "code": "MEMBER_NOT_FOUND",
  "message": "회원 정보를 찾을 수 없습니다.",
  "data": null,
  "timestamp": "2026-07-22T10:30:00+09:00"
}
```

탈퇴한 회원인지 원래 존재하지 않는 회원인지는 구분하여 노출하지 않는다.

---

#### 500 Internal Server Error

```json
{
  "success": false,
  "code": "COMMON_INTERNAL_SERVER_ERROR",
  "message": "일시적인 오류가 발생했습니다.",
  "data": null,
  "timestamp": "2026-07-22T10:30:00+09:00"
}
```

---

### 추가 프론트 처리 기준

```
마이페이지에서 내 스터디 선택
→ status = ACTIVE로 첫 목록 조회
→ 진행 중인 스터디 탭에 표시

완료된 스터디 탭 선택
→ status = COMPLETED로 목록 재조회
→ 완료된 스터디를 최근 완료순으로 표시

진행 중인 스터디 카드 선택
→ GET /api/v1/studies/{studyId} 호출
→ 스터디 홈으로 이동

완료된 스터디 카드 선택
→ readOnly = true 확인
→ 읽기 전용 스터디 홈으로 이동
→ 공지·채팅 이력·임장 기록·AI 리포트 조회 기능만 제공

studyStatus = RECRUITING
→ "모집 중" 상태 배지 표시

studyStatus = IN_PROGRESS
→ "임장 진행 중" 상태 표시
→ 임장 모드 진입 버튼 제공

studyStatus = COMPLETED
→ "임장 완료" 상태 표시
→ AI 리포트 보기 버튼 제공

unreadChatCount > 0
→ 채팅 탭 또는 스터디 카드에 읽지 않은 메시지 배지 표시

schedule = null
→ 임장 일정 영역에 "등록된 일정이 없습니다." 표시

page + 1 < totalPages
→ 목록 하단 도달 시 page 값을 1 증가시켜 다음 목록 조회
→ 기존 목록 뒤에 content 추가

content가 빈 배열
→ ACTIVE: "현재 참여 중인 스터디가 없습니다."
→ COMPLETED: "완료된 스터디가 없습니다."
```

---

## 내 리포트 목록 조회

Domain: Members
Method: GET
Progress: 완료
URI: /api/v1/members/me/reports
담당자: 박재명
연동여부: Yes

로그인 회원이 스터디장이거나 현재 `ACTIVE` 스터디원인 스터디에서 생성된 완료 리포트 목록을 조회한다.

직접 임장에 참여하지 않았더라도 리포트 요약은 조회할 수 있다. 원본 근거 접근 가능 여부는 별도의 `field_participant` 기록으로 판단한다.

### Request

- HTTP Method: `GET`
- URI: `/api/v1/members/me/reports`
- 인증 필요: 필요
- Request Body: 없음

#### Request Header

```http
Authorization: Bearer {accessToken}
```

#### Query Parameter

```http
GET /api/v1/members/me/reports?page=0&size=20
```

| 필드 | 타입 | 필수 | 기본값 | 설명 |
| --- | --- | --- | --- | --- |
| `page` | Integer | N | `0` | 페이지 번호. 0 이상 |
| `size` | Integer | N | `20` | 페이지 크기. 1~100 |

### 처리 기준

- 로그인 회원 ID는 Access Token에서 확인한다.
- 로그인 회원이 스터디장이거나 현재 `ACTIVE` 스터디원인 삭제되지 않은 스터디를 대상으로 한다.
- `DONE` 상태 리포트만 최근 완료 순으로 반환한다.
- `PROCESSING`, `FAILED` 등 완료되지 않은 리포트는 목록에 포함하지 않는다.
- 실제 임장 참여 여부는 리포트 요약 목록 접근을 제한하지 않는다.
- `canViewEvidence`는 해당 스터디의 임장 세션에 로그인 회원의 `field_participant` 행이 있을 때만 `true`다.
- `canViewEvidence=false`인 회원은 리포트 요약은 볼 수 있지만 원본 근거 API는 호출할 수 없다.
- `study.participantCount`는 실제 임장 세션의 참여자 수다.
- 모든 리포트 API에는 JWT 인증이 필요하다.
- 리포트 식별에는 숫자형 `reportId`를 사용한다.

### Response

#### 200 OK

```json
{
  "success": true,
  "code": "MEMBER_REPORT_LIST_SUCCESS",
  "message": "내 리포트 목록 조회에 성공했습니다.",
  "data": {
    "content": [
      {
        "reportId": 48,
        "title": "래미안 옥수 리버젠 임장 리포트",
        "summary": "교통 접근성이 좋고 단지 경사는 주의가 필요합니다.",
        "analysisTags": [
          "교통 우수",
          "단지 경사"
        ],
        "apartment": {
          "apartmentId": 15,
          "name": "래미안 옥수 리버젠"
        },
        "study": {
          "studyId": 31,
          "title": "옥수동 주말 생활환경 임장",
          "visitedAt": "2026-07-20T14:00:00+09:00",
          "participantCount": 5
        },
        "status": "DONE",
        "favoritedByMe": true,
        "canViewEvidence": false,
        "completedAt": "2026-07-22T18:07:00+09:00"
      }
    ],
    "totalElements": 2,
    "page": 0,
    "size": 20,
    "totalPages": 1
  },
  "timestamp": "2026-07-28T16:35:00+09:00"
}
```

### Response Field

| 필드 | 타입 | 설명 |
| --- | --- | --- |
| `reportId` | Long | 리포트 ID |
| `title` | String | 리포트 제목 |
| `summary` | String | 리포트 요약 |
| `analysisTags` | Array<String> | 분석 태그 |
| `apartment` | Object | 아파트 ID와 이름 |
| `study` | Object | 스터디 ID, 제목, 임장 시각과 실제 임장 참여자 수 |
| `status` | String | 리포트 상태. 이 목록에서는 `DONE` |
| `favoritedByMe` | Boolean | 로그인 회원의 찜 여부 |
| `canViewEvidence` | Boolean | 원본 근거 조회 가능 여부 |
| `completedAt` | String/null | 리포트 완료 시각 |

### Exception

- `400 COMMON_INVALID_REQUEST`: 페이지 값 오류
- `401 AUTH_ACCESS_TOKEN_INVALID`
- `401 AUTH_ACCESS_TOKEN_EXPIRED`
- `403 AUTH_MEMBER_WITHDRAWN`
- `404 MEMBER_NOT_FOUND`
- `500 COMMON_INTERNAL_SERVER_ERROR`

#### 400 Bad Request — 잘못된 페이지 크기

```json
{
  "success": false,
  "code": "COMMON_INVALID_REQUEST",
  "message": "입력값을 확인해 주세요.",
  "data": {
    "field": "size",
    "reason": "페이지 크기는 1 이상 100 이하이어야 합니다."
  },
  "timestamp": "2026-07-28T16:35:00+09:00"
}
```

#### 401 Unauthorized

```json
{
  "success": false,
  "code": "AUTH_ACCESS_TOKEN_INVALID",
  "message": "로그인이 필요합니다.",
  "data": null,
  "timestamp": "2026-07-28T16:35:00+09:00"
}
```

### 프론트 처리

```text
마이페이지에서 AI 리포트 탭 선택
→ GET /api/v1/members/me/reports?page=0&size=20 호출
→ 최근 완료된 리포트부터 카드로 표시
```

```text
리포트 카드
→ 아파트명, 스터디명, 요약, 참여자 수, 완료 날짜 표시
```

```text
리포트 카드 선택
→ GET /api/v1/reports/{reportId} 호출
→ 리포트 상세 화면 이동
```

```text
canViewEvidence=true
→ 근거 보기 버튼 표시
```

```text
canViewEvidence=false
→ 리포트 요약은 표시
→ 근거 보기 버튼은 숨김 또는 비활성화
```

```text
content=[]
→ "아직 완료된 임장 리포트가 없습니다." 표시
```

---

## 내가 작성한 게시글 목록 조회

Method: GET
Progress: 시작 전
URI: /api/v1/members/me/posts
담당자: 김윤석
연동여부: No

커뮤니티의 `내 게시물 → 내가 쓴 글`에서 사용한다.

로그인한 회원이 커뮤니티에 직접 작성한 게시글 목록을 조회한다.

개정본_v3에 따라 마이페이지가 아니라 **커뮤니티 탭의 `내 활동 > 작성 글` 화면**에서 사용한다.

최근 작성한 게시글부터 반환하며, 목록이 추가되는 중에도 중복이나 누락 없이 조회할 수 있도록 커서 페이지네이션을 사용한다.

### Request

- HTTP Method: `GET`
- URI: `/api/v1/members/me/posts`
- 인증 필요: 필요

#### Query Parameter

```
GET /api/v1/members/me/posts?boardType=ALL&cursor=124&size=20

boardType: ALL / INFORMATION / FREE
```

#### Request Header

```
Authorization: Bearer {accessToken}
```

### 처리 기준

- 로그인 회원이 직접 작성한 게시글만 반환한다.
- 자동 리포트 게시글은 포함하지 않는다.
- 정상 글에는 반응·댓글·조회 수와 HOT 여부를 반환한다.
- `status=HIDDEN` 또는 `deletedAt!=null`이면 제목·본문 미리보기를 노출하지 않고 `originalAvailable=false`로 반환한다.

### Response

#### 200 OK

```json
{
  "success": true,
  "code": "MEMBER_POST_LIST_SUCCESS",
  "message": "내가 작성한 게시글 목록 조회에 성공했습니다.",
  "data": {
    "content": [
      {
        "postId": 124,
        "boardType": "FREE",
        "title": "성동구 임장 후기입니다.",
        "status": "ACTIVE",
        "originalAvailable": true,
        "commentCount": 3,
        "likeCount": 5,
        "viewCount": 82,
        "isHot": true,
        "createdAt": "2026-07-22T16:30:00+09:00"
      },
      {
        "postId": 110,
        "boardType": "INFORMATION",
        "title": null,
        "status": "HIDDEN",
        "originalAvailable": false,
        "commentCount": 0,
        "likeCount": 0,
        "viewCount": 0,
        "isHot": false,
        "createdAt": "2026-07-15T10:00:00+09:00"
      }
    ],
    "totalCount": 2,
    "nextCursor": null,
    "hasNext": false
  },
  "timestamp": "2026-07-24T16:40:00+09:00"
}
```

---

### 추가 Exception 예시

#### 400 Bad Request — 잘못된 커서

`cursor`가 숫자가 아니거나 1보다 작은 경우

```json
{
  "success": false,
  "code": "MEMBER_POST_CURSOR_INVALID",
  "message": "게시글 목록 커서가 올바르지 않습니다.",
  "data": {
    "field": "cursor",
    "reason": "커서는 1 이상의 숫자여야 합니다."
  },
  "timestamp": "2026-07-22T10:30:00+09:00"
}
```

---

#### 400 Bad Request — 잘못된 조회 개수

`size`가 1보다 작거나 100을 초과한 경우

```json
{
  "success": false,
  "code": "COMMON_INVALID_REQUEST",
  "message": "입력값을 확인해 주세요.",
  "data": {
    "field": "size",
    "reason": "조회 개수는 1 이상 100 이하이어야 합니다."
  },
  "timestamp": "2026-07-22T10:30:00+09:00"
}
```

---

#### 401 Unauthorized

Access Token이 없거나 유효하지 않은 경우

```json
{
  "success": false,
  "code": "AUTH_ACCESS_TOKEN_INVALID",
  "message": "로그인이 필요합니다.",
  "data": null,
  "timestamp": "2026-07-22T10:30:00+09:00"
}
```

---

#### 404 Not Found

Access Token에 해당하는 회원이 존재하지 않거나 탈퇴한 경우

```json
{
  "success": false,
  "code": "MEMBER_NOT_FOUND",
  "message": "회원 정보를 찾을 수 없습니다.",
  "data": null,
  "timestamp": "2026-07-22T10:30:00+09:00"
}
```

---

#### 500 Internal Server Error

```json
{
  "success": false,
  "code": "COMMON_INTERNAL_SERVER_ERROR",
  "message": "일시적인 오류가 발생했습니다.",
  "data": null,
  "timestamp": "2026-07-22T10:30:00+09:00"
}
```

---

### 추가 프론트 처리 기준

```
커뮤니티 탭 진입
→ 내 활동 선택
→ 작성 글 탭 선택
→ cursor 없이 첫 목록 조회

자유게시판 필터 선택
→ boardType = FREE로 목록 재조회

목록 하단 도달
→ hasNext 확인
→ hasNext = true이면 nextCursor를 cursor로 전달
→ 다음 게시글 목록 조회
→ 기존 content 뒤에 추가

postStatus = ACTIVE
→ 제목·내용 미리보기·댓글 수·좋아요 수 표시
→ 카드 선택 시 GET /api/v1/posts/{postId} 호출

postStatus = HIDDEN
→ "운영 정책에 따라 숨김 처리된 게시글입니다." 표시
→ 내용 미리보기와 상세 이동 비활성화

게시글 삭제 성공
→ 현재 목록에서 해당 게시글 제거

게시글 수정 성공
→ 해당 목록 항목의 제목·미리보기·updatedAt 갱신

content가 빈 배열
→ "작성한 게시글이 없습니다." 표시

nextCursor = null이고 hasNext = false
→ 추가 조회 중지
→ 목록 하단 로딩 UI 제거
```

---

## 내가 찜한 아파트 목록 조회

Method: GET
Progress: 완료
URI: /api/v1/members/me/favorite-apartments
담당자: 박재명
연동여부: Yes

로그인한 회원이 찜한 아파트 목록을 조회한다.

하단 내비게이션의 `찜` 화면에서 사용하며, 최근에 찜한 아파트부터 반환한다.

각 아파트의 기본 정보, 최근 실거래 정보, 현재 모집 중인 스터디 수와 완료된 리포트 수를 함께 반환한다.

### Request

- HTTP Method: `GET`
- URI: `/api/v1/members/me/favorite-apartments`
- 인증 필요: 필요

#### Query Parameter

```
GET /api/v1/members/me/favorite-apartments?page=0&size=20
```

#### Request Header

```
Authorization: Bearer {accessToken}
```

### 처리 기준

- 최근 찜한 순으로 반환한다.
- 최근 실거래는 `isCanceled=false`인 가장 최신 거래를 사용한다.
- 최근 거래가 없으면 `latestTransaction=null`로 반환한다.

### Response

#### 200 OK

```json
{
  "success": true,
  "code": "MEMBER_FAVORITE_APARTMENT_LIST_SUCCESS",
  "message": "찜한 아파트 목록 조회에 성공했습니다.",
  "data": {
    "content": [
      {
        "apartmentId": 15,
        "name": "래미안 옥수 리버젠",
        "address": "서울특별시 성동구 매봉길 15",
        "districtName": "성동구",
        "dongName": "옥수동",
        "householdCount": 1821,
        "latestTransaction": {
          "price": 183000,
          "priceUnit": "TEN_THOUSAND_KRW",
          "exclusiveArea": 84.95,
          "dealDate": "2026-06-15"
        },
        "recruitingStudyCount": 2,
        "completedReportCount": 5,
        "favoritedAt": "2026-07-22T18:30:00+09:00"
      }
    ],
    "totalElements": 4,
    "page": 0,
    "size": 20,
    "totalPages": 1
  },
  "timestamp": "2026-07-24T16:50:00+09:00"
}
```

---

### 추가 Exception 예시

#### 400 Bad Request — 잘못된 페이지 번호

`page`가 0보다 작은 경우

```json
{
  "success": false,
  "code": "COMMON_INVALID_REQUEST",
  "message": "입력값을 확인해 주세요.",
  "data": {
    "field": "page",
    "reason": "페이지 번호는 0 이상이어야 합니다."
  },
  "timestamp": "2026-07-22T10:30:00+09:00"
}
```

---

#### 400 Bad Request — 잘못된 페이지 크기

`size`가 1보다 작거나 100을 초과한 경우

```json
{
  "success": false,
  "code": "COMMON_INVALID_REQUEST",
  "message": "입력값을 확인해 주세요.",
  "data": {
    "field": "size",
    "reason": "페이지 크기는 1 이상 100 이하이어야 합니다."
  },
  "timestamp": "2026-07-22T10:30:00+09:00"
}
```

---

#### 401 Unauthorized

Access Token이 없거나 유효하지 않은 경우

```json
{
  "success": false,
  "code": "AUTH_ACCESS_TOKEN_INVALID",
  "message": "로그인이 필요합니다.",
  "data": null,
  "timestamp": "2026-07-22T10:30:00+09:00"
}
```

---

#### 404 Not Found

Access Token에 해당하는 회원이 존재하지 않거나 탈퇴한 경우

```json
{
  "success": false,
  "code": "MEMBER_NOT_FOUND",
  "message": "회원 정보를 찾을 수 없습니다.",
  "data": null,
  "timestamp": "2026-07-22T10:30:00+09:00"
}
```

탈퇴한 회원인지 처음부터 존재하지 않는 회원인지는 구분하여 노출하지 않는다.

---

#### 500 Internal Server Error

```json
{
  "success": false,
  "code": "COMMON_INTERNAL_SERVER_ERROR",
  "message": "일시적인 오류가 발생했습니다.",
  "data": null,
  "timestamp": "2026-07-22T10:30:00+09:00"
}
```

---

### 추가 프론트 처리 기준

```
하단 내비게이션에서 찜 선택
→ GET /api/v1/members/me/favorite-apartments?page=0&size=20 호출
→ 최근에 찜한 아파트부터 카드 형태로 표시

아파트 카드 표시
→ 단지명
→ 자치구·동
→ 세대수
→ 최근 실거래 가격
→ 모집 중인 스터디 수
→ 완료 리포트 수 표시

아파트 카드 선택
→ GET /api/v1/apartments/{apartmentId} 호출
→ 아파트 상세 화면으로 이동

recruitingStudyCount > 0
→ "모집 중인 스터디 N개" 배지 표시
→ 선택 시 GET /api/v1/apartments/{apartmentId}/studies 호출

completedReportCount > 0
→ "리포트 N개" 정보 표시
→ 선택 시 GET /api/v1/apartments/{apartmentId}/reports 호출

찜 해제 버튼 선택
→ DELETE /api/v1/apartments/{apartmentId}/favorite 호출
→ 성공 시 현재 목록에서 해당 아파트 제거

찜 해제 실패
→ 목록에서 제거하지 않음
→ 찜 상태를 기존 값으로 복원
→ "찜 해제에 실패했습니다." 안내

latestTransaction = null
→ 최근 거래 가격 대신 "최근 실거래 정보 없음" 표시

hasNext = true
→ 목록 하단 도달 시 page 값을 1 증가
→ 다음 찜 아파트 목록 조회
→ 기존 content 뒤에 추가

content가 빈 배열
→ "아직 찜한 아파트가 없습니다." 표시
→ 아파트 찾기 버튼 제공

아파트 찾기 버튼 선택
→ 스터디 찾기 지도 또는 통합 검색 화면으로 이동
```

---

## 홈 화면 통합 조회

Domain: Home
Method: GET
Progress: 완료
URI: /api/v1/home
담당자: 박재명
프론트 담당자: 장선형 (`FE-005`)
연동여부: Yes

로그인 회원의 다음 임장 카드, 읽지 않은 알림 수와 현재 위치 기준 날씨·미세먼지·기상청 공식 특보를 한 번에 조회한다.

로그인한 회원의 홈 화면에 필요한 정보를 한 번에 조회한다.

홈 화면 진입 시 이 API 하나로 오늘 날짜, 다음 임장 D-Day, 일정 요약, 알림 배지와 날씨 카드를 구성한다.

### Request

- HTTP Method: `GET`
- URI: `/api/v1/home`
- 인증 필요: 필요
- Request Body: 없음

#### Query Parameter

```
GET /api/v1/home?latitude=37.5412&longitude=127.0178
```

| 필드 | 타입 | 필수 | 설명 |
| --- | --- | --- | --- |
| `latitude` | Double | N | 현재 위치 위도 |
| `longitude` | Double | N | 현재 위치 경도 |

위도와 경도는 함께 전달한다. 위치 권한이 없으면 둘 다 생략한다.

#### Request Header

```
Authorization: Bearer {accessToken}
```

### 처리 기준

#### 다음 임장

- 스터디장 또는 `study_member.status=ACTIVE`인 회원의 진행 중 임장과 미래 일정을 대상으로 한다.
- 승인 대기·거절 신청, `REMOVED` 멤버 관계, 취소·완료 스터디는 제외한다.
- `IN_PROGRESS` 임장을 우선 반환하고, 없으면 현재 시각 이후 일정 중 가장 가까운 한 건을 반환한다.
- D-Day는 Asia/Seoul 날짜 기준으로 계산하며 당일 또는 이미 시작한 진행 중 임장은 `0`이다.
- 다음 일정이 없으면 오류가 아니라 `exists=false`를 반환한다.
- 다음 임장 카드 선택 시 스터디 상세로 이동할 수 있도록 `studyId`를 반환한다.

#### 알림 배지

- `unreadNotificationCount`는 로그인 회원의 `notification.is_read=false` 건수다.
- 값이 `1` 이상이면 프론트는 상단 알림 아이콘에 읽지 않음 표시를 노출한다.
- 알림 목록 자체는 `GET /api/v1/notifications`에서 조회한다.

#### 날씨 위치 우선순위

```
1. 요청으로 받은 현재 위치
2. 다음 임장 아파트 위치
3. 합의된 서비스 기본 위치
```

기본 위치는 서울시청 좌표(`37.5665`, `126.9780`)와 `서울특별시 중구` 표기를 사용한다.

- 날씨·대기질·공식 특보 외부 조회 실패가 다음 임장과 알림 수 조회를 실패시키면 안 된다.
- 최신 조회가 실패했으나 유효한 캐시가 있으면 `freshness=STALE`로 반환한다.
- 사용 가능한 데이터가 없으면 `available=false`, `freshness=UNAVAILABLE`로 반환한다.
- 날씨와 대기질은 Open-Meteo의 Forecast API 및 Air Quality API를 사용한다.
- 같은 위치의 정상 응답은 15분 동안 `FRESH`로 재사용하고, 최신 조회 실패 시 최대 1시간 캐시를 `STALE`로 반환한다.
- `fineDustValue`는 화면의 미세먼지 카드에 표시하는 PM10 농도(μg/m³)다.
- PM10 등급은 `GOOD(0~30)`, `NORMAL(31~80)`, `BAD(81~150)`, `VERY_BAD(151 이상)`으로 계산한다.
- `weatherAlerts`는 기상청 기상특보 조회서비스가 실제 발표한 발효 중 특보만 반환한다.
- 현재 위치 좌표는 카카오 좌표→행정구역 변환 API로 서울 자치구를 확인한 뒤 기상청의 서울 4개 특보구역(`서울동남권 / 서울동북권 / 서울서남권 / 서울서북권`) 중 하나로 매핑한다.
- 다음 임장 또는 기본 위치의 표시명에 서울 자치구가 있으면 외부 좌표 변환 없이 특보구역을 결정한다.
- 같은 위치 기준의 정상 응답은 5분 동안 `FRESH`로 재사용하고, 최신 조회 실패 시 최대 30분 캐시를 `STALE`로 반환한다.
- 동시에 여러 특보가 발효 중이면 모두 반환하며 `SEVERE_WARNING`, `WARNING`, `ADVISORY` 순으로 정렬한다.
- 공식 특보 조회 성공 후 발효 중 특보가 없으면 `available=true`, `items=[]`로 반환한다.
- 좌표가 서울 밖이거나 카카오·기상청 설정 또는 응답을 사용할 수 없고 캐시도 없으면 `available=false`, `items=[]`, `freshness=UNAVAILABLE`로 반환한다.
- 검색·설정·스터디 찾기·스터디 생성 버튼은 정적 이동 요소이므로 별도 응답 필드를 반환하지 않는다.

#### 날씨 신선도

```
FRESH / STALE / UNAVAILABLE
```

#### 날씨 위치 기준

```
CURRENT_LOCATION / NEXT_VISIT_APARTMENT / DEFAULT_LOCATION
```

### 추가 상세 처리 기준

- 로그인한 회원 ID는 Access Token에서 확인한다.
- 회원 상태가 `ACTIVE`인 경우에만 홈 화면을 조회할 수 있다.
- 탈퇴한 회원이나 유효하지 않은 Access Token은 조회할 수 없다.
- 스터디 신청 대기·거절 상태는 제외한다.
- 임장 시작 전에 탈퇴하거나 강퇴된 회원은 제외한다.
- 조회 대상 일정이 여러 개면 현재 시각 이후 가장 가까운 임장을 반환한다.
- 임장 시작 시각이 가장 가까운 일정을 한 건 반환한다.
- 시작 시각이 같으면 `scheduleId`가 작은 일정을 우선한다.
- 신청 대기 중인 스터디 일정은 반환하지 않는다.
- 신청이 거절된 스터디 일정은 반환하지 않는다.
- 임장 시작 전에 탈퇴하거나 강퇴된 스터디 일정은 반환하지 않는다.
- 다음 일정이 없으면 `nextVisit` 객체는 유지하고 `exists=false`, 나머지 필드는 `null`로 반환한다.
- `dDay`는 `Asia/Seoul` 날짜를 기준으로 계산한다.
- 일정이 오늘이면 `dDay = 0`을 반환한다.
- 위치 정보가 전달된 경우 해당 좌표를 기준으로 날씨와 대기질 정보를 조회한다.
- 같은 위치 기준으로 기상청 공식 특보를 조회하며, 현재 위치의 서울 자치구 확인에는 카카오 좌표→행정구역 변환 API를 사용한다.
- 외부 날씨·대기질 API 호출 결과는 일정 시간 캐시하여 불필요한 반복 호출을 줄인다.
- 권장 캐시 시간은 10분에서 30분 사이다.
- 날씨 데이터 조회 실패가 홈 화면 전체 실패로 이어지지 않도록 한다.
- 위치 정보가 전달되지 않은 경우에도 홈의 나머지 데이터는 정상적으로 반환한다.
- 위치가 없고 다음 임장도 없으면 합의된 서울시청 기본 위치를 사용하고 `locationBasis=DEFAULT_LOCATION`으로 명시한다.
- 날씨와 미세먼지 정보는 참고용임을 전제로 한다.
- 홈 응답에는 참여 스터디 목록과 리포트 목록을 포함하지 않는다. 해당 데이터는 마이페이지와 각 전용 API에서 조회한다.

---

### Response

| 필드 | 타입 | 설명 |
| --- | --- | --- |
| `today` | String(date) | `Asia/Seoul` 기준 오늘 날짜 |
| `unreadNotificationCount` | Long | 읽지 않은 알림 수. 없으면 `0` |
| `nextVisit.exists` | Boolean | 다음 임장 존재 여부 |
| `nextVisit.dDay` | Long? | 다음 임장까지 남은 날짜. 당일은 `0` |
| `nextVisit.studyId` | Long? | 카드 선택 시 이동할 스터디 ID |
| `nextVisit.apartmentName` | String? | 화면에 표시할 아파트명 |
| `nextVisit.meetingPlace` | String? | 모임 장소 |
| `nextVisit.startAt` | String(date-time)? | 임장 시작 시각 (`Asia/Seoul` 오프셋 포함) |
| `nextVisit.currentMemberCount` | Long? | 현재 승인 인원 |
| `nextVisit.capacity` | Integer? | 최대 모집 인원 |
| `weather.available` | Boolean | 날씨 카드 사용 가능 여부 |
| `weather.temperatureCelsius` | Double? | 현재 기온(℃) |
| `weather.conditionCode` | String? | 프론트 아이콘 매핑용 날씨 코드 |
| `weather.conditionText` | String? | 날씨 설명 |
| `weather.iconKey` | String? | 기본 날씨 아이콘 키 |
| `weather.rainProbability` | Integer? | 현재 강수확률(%) |
| `weather.fineDustValue` | Double? | PM10 농도(μg/m³) |
| `weather.fineDustGrade` | String? | `GOOD / NORMAL / BAD / VERY_BAD` |
| `weather.observedAt` | String(date-time)? | 관측 기준 시각 |
| `weather.locationName` | String | 조회 기준 위치 표시명 |
| `weather.locationBasis` | String | `CURRENT_LOCATION / NEXT_VISIT_APARTMENT / DEFAULT_LOCATION` |
| `weather.source` | String? | 정상 응답이면 `OPEN_METEO` |
| `weather.freshness` | String | `FRESH / STALE / UNAVAILABLE` |
| `weatherAlerts.available` | Boolean | 공식 특보 조회 결과 사용 가능 여부 |
| `weatherAlerts.items` | Array | 현재 발효 중인 기상청 공식 특보 목록. 없으면 빈 배열 |
| `weatherAlerts.items[].type` | String | `STRONG_WIND / HEAVY_RAIN / COLD_WAVE / DRY / STORM_SURGE / HIGH_WAVES / TYPHOON / HEAVY_SNOW / YELLOW_DUST / HEAT_WAVE` |
| `weatherAlerts.items[].level` | String | `ADVISORY / WARNING / SEVERE_WARNING` |
| `weatherAlerts.items[].title` | String | 화면 표시용 공식 특보명. 예: `폭염주의보`, `호우경보` |
| `weatherAlerts.items[].areaCode` | String | 기상청 특보구역 코드 |
| `weatherAlerts.items[].areaName` | String | 기상청 특보구역명 |
| `weatherAlerts.items[].issuedAt` | String(date-time) | 기상청 발표 시각 (`Asia/Seoul` 오프셋 포함) |
| `weatherAlerts.items[].effectiveAt` | String(date-time) | 특보 발효 시각 (`Asia/Seoul` 오프셋 포함) |
| `weatherAlerts.source` | String | `KMA` |
| `weatherAlerts.freshness` | String | `FRESH / STALE / UNAVAILABLE` |

#### 200 OK

```json
{
  "success": true,
  "code": "HOME_SUCCESS",
  "message": "홈 화면 정보 조회에 성공했습니다.",
  "data": {
    "today": "2026-07-24",
    "unreadNotificationCount": 2,
    "nextVisit": {
      "exists": true,
      "dDay": 3,
      "studyId": 10,
      "apartmentName": "래미안 옥수 리버젠",
      "meetingPlace": "옥수역 3번 출구",
      "startAt": "2026-07-27T15:00:00+09:00",
      "currentMemberCount": 5,
      "capacity": 6
    },
    "weather": {
      "available": true,
      "temperatureCelsius": 33.4,
      "conditionCode": "CLEAR_SKY",
      "conditionText": "맑음",
      "iconKey": "clear_day",
      "rainProbability": 10,
      "fineDustValue": 32,
      "fineDustGrade": "NORMAL",
      "observedAt": "2026-07-24T14:00:00+09:00",
      "locationName": "서울특별시 성동구",
      "locationBasis": "CURRENT_LOCATION",
      "source": "OPEN_METEO",
      "freshness": "FRESH"
    },
    "weatherAlerts": {
      "available": true,
      "items": [
        {
          "type": "HEAT_WAVE",
          "level": "ADVISORY",
          "title": "폭염주의보",
          "areaCode": "L1100200",
          "areaName": "서울동북권",
          "issuedAt": "2026-07-24T10:00:00+09:00",
          "effectiveAt": "2026-07-24T10:00:00+09:00"
        }
      ],
      "source": "KMA",
      "freshness": "FRESH"
    }
  },
  "timestamp": "2026-07-24T14:05:00+09:00"
}
```

#### 다음 임장 없음

```json
{
  "nextVisit": {
    "exists": false,
    "dDay": null,
    "studyId": null,
    "apartmentName": null,
    "meetingPlace": null,
    "startAt": null,
    "currentMemberCount": null,
    "capacity": null
  }
}
```

#### 날씨 이용 불가

```json
{
  "weather": {
    "available": false,
    "temperatureCelsius": null,
    "conditionCode": null,
    "conditionText": null,
    "iconKey": null,
    "rainProbability": null,
    "fineDustValue": null,
    "fineDustGrade": null,
    "observedAt": null,
    "locationName": "서울특별시 성동구",
    "locationBasis": "NEXT_VISIT_APARTMENT",
    "source": null,
    "freshness": "UNAVAILABLE"
  }
}
```

#### 공식 특보 이용 불가

```json
{
  "weatherAlerts": {
    "available": false,
    "items": [],
    "source": "KMA",
    "freshness": "UNAVAILABLE"
  }
}
```

### Exception

- `400 HOME_LOCATION_INCOMPLETE`: 위도·경도 중 한쪽만 전달
- `400 HOME_LATITUDE_INVALID`: 위도 형식 또는 범위 오류
- `400 HOME_LONGITUDE_INVALID`: 경도 형식 또는 범위 오류
- `401 AUTH_ACCESS_TOKEN_INVALID`
- `404 MEMBER_NOT_FOUND`
- `500 COMMON_INTERNAL_SERVER_ERROR`

### 추가 Exception 예시

#### 400 Bad Request — 위도만 전달한 경우

```json
{
  "success": false,
  "code": "HOME_LOCATION_INCOMPLETE",
  "message": "위치 정보를 확인해 주세요.",
  "data": {
    "reason": "위도와 경도는 함께 전달해야 합니다."
  },
  "timestamp": "2026-07-22T10:30:00+09:00"
}
```

---

#### 400 Bad Request — 잘못된 위도

```json
{
  "success": false,
  "code": "HOME_LATITUDE_INVALID",
  "message": "위도 값이 올바르지 않습니다.",
  "data": {
    "field": "latitude",
    "reason": "위도는 -90 이상 90 이하이어야 합니다."
  },
  "timestamp": "2026-07-22T10:30:00+09:00"
}
```

---

#### 400 Bad Request — 잘못된 경도

```json
{
  "success": false,
  "code": "HOME_LONGITUDE_INVALID",
  "message": "경도 값이 올바르지 않습니다.",
  "data": {
    "field": "longitude",
    "reason": "경도는 -180 이상 180 이하이어야 합니다."
  },
  "timestamp": "2026-07-22T10:30:00+09:00"
}
```

---

#### 401 Unauthorized

```json
{
  "success": false,
  "code": "AUTH_ACCESS_TOKEN_INVALID",
  "message": "로그인이 필요합니다.",
  "data": null,
  "timestamp": "2026-07-22T10:30:00+09:00"
}
```

---

#### 404 Not Found

Access Token에 해당하는 회원이 존재하지 않거나 탈퇴한 경우

```json
{
  "success": false,
  "code": "MEMBER_NOT_FOUND",
  "message": "회원 정보를 찾을 수 없습니다.",
  "data": null,
  "timestamp": "2026-07-22T10:30:00+09:00"
}
```

---

#### 500 Internal Server Error

```json
{
  "success": false,
  "code": "COMMON_INTERNAL_SERVER_ERROR",
  "message": "일시적인 오류가 발생했습니다.",
  "data": null,
  "timestamp": "2026-07-22T10:30:00+09:00"
}
```

---

### 프론트 처리

```
홈 진입
→ 홈 통합 조회
→ 오늘 날짜·다음 임장 D-Day·알림 배지·날씨 카드·공식 특보 배지 표시

nextVisit.exists=false
→ 다음 임장 빈 상태 표시
→ “스터디 찾기” 버튼 제공

weather.available=false
→ 날씨 이용 불가 문구만 표시
→ 다른 홈 카드는 그대로 유지

다음 임장 카드 선택
→ nextVisit.studyId로 내 스터디 상세 이동
```

### 추가 프론트 처리 기준

```
앱 홈 화면 진입
→ 현재 위치 권한 확인
→ 위치 사용 가능 시 latitude와 longitude 전달
→ 위치를 사용할 수 없으면 Query Parameter 없이 홈 API 호출

weather.available = true
→ 기온·날씨·강수확률·미세먼지 정보 표시

weatherAlerts.available = true && weatherAlerts.items가 비어 있지 않음
→ 첫 항목을 다음 임장 D-Day 옆 공식 특보 배지로 표시
→ 전체 항목이 필요하면 items 순서대로 표시

weatherAlerts.available = true && weatherAlerts.items가 비어 있음
→ 현재 발효 중인 공식 특보가 없으므로 특보 배지를 표시하지 않음

weatherAlerts.available = false
→ 특보 배지만 숨김
→ 날씨·다음 임장·알림 배지는 그대로 유지

weather.available = false
→ 날씨 영역을 숨기거나
→ "날씨 정보를 불러올 수 없습니다." 표시
→ 다음 임장과 알림 배지는 그대로 유지

unreadNotificationCount > 0
→ 상단 알림 아이콘에 읽지 않음 표시

알림 아이콘 선택
→ 알림 화면으로 이동
→ GET /api/v1/notifications 호출

스터디 찾기 선택
→ 지도 화면으로 이동
→ GET /api/v1/apartments/bounds 호출

검색 버튼 선택
→ 통합 검색 화면으로 이동
→ GET /api/v1/search 호출

내 스터디 만들기 선택
→ 스터디 생성 화면으로 이동
```

---

## 알림 한 건 읽음 처리

Method: PATCH
Progress: 진행 중
URI: /api/v1/notifications/{notificationId}/read
담당자: 김윤석
연동여부: No

로그인한 회원의 특정 알림을 읽음 상태로 변경한다.

알림 목록에서 사용자가 알림 하나를 선택했을 때 호출하며, 이미 읽은 알림에 같은 요청을 다시 보내도 오류 없이 동일한 결과를 반환한다.

---

### Request

- Request HTTP Method: `PATCH`
- 인증 필요: 필요
- Request Body: 없음

#### Request Header

```
Authorization: Bearer {accessToken}
```

#### Path Variable

```
PATCH /api/v1/notifications/81/read
```

| 필드 | 타입 | 필수 | 설명 |
| --- | --- | --- | --- |
| `notificationId` | Long | Y | 읽음 처리할 알림 ID, 1 이상의 값 |

---

### 처리 기준

- 로그인한 회원 ID는 Access Token에서 확인한다.
- 회원 상태가 `ACTIVE`인 경우에만 처리할 수 있다.
- `notificationId`에 해당하는 알림이 존재하는지 확인한다.
- 해당 알림의 `member_id`가 로그인한 회원 ID와 일치해야 한다.
- 다른 회원에게 발송된 알림은 읽음 처리할 수 없다.
- 삭제되거나 만료 처리된 알림은 읽음 처리할 수 없다.
- 읽지 않은 알림이면 다음과 같이 변경한다.

```
is_read = true
read_at = 현재 시각
```

- 이미 읽은 알림이면 기존 `readAt`을 변경하지 않는다.
- 동일한 요청이 반복되어도 새로운 데이터가 생성되지 않는다.
- 이미 읽은 알림에 다시 요청하더라도 정상 응답을 반환한다.
- 읽음 처리만 수행하며 알림 데이터를 삭제하지 않는다.
- 읽음 처리는 FCM Push 수신 여부나 회원의 알림 수신 설정을 변경하지 않는다.
- 알림의 이동 대상이 삭제됐거나 접근 불가능해도 알림 자체는 읽음 처리할 수 있다.
- 읽음 처리 후 로그인한 회원의 현재 읽지 않은 전체 알림 수를 다시 계산하여 반환한다.
- 처리 과정은 하나의 트랜잭션에서 수행한다.

#### 알림 소유권 확인 예시

```
로그인 회원 ID: 1
알림 ID: 81
알림 수신 회원 ID: 1

→ 읽음 처리 가능

로그인 회원 ID: 1
알림 ID: 90
알림 수신 회원 ID: 15

→ 읽음 처리 불가
→ 알림 존재 여부를 노출하지 않도록 404 반환
```

#### 멱등 처리 예시

```
첫 번째 요청
is_read = false
read_at = null

→ is_read = true
→ read_at = 2026-07-22T10:30:00+09:00

두 번째 요청
is_read = true
read_at = 2026-07-22T10:30:00+09:00

→ 기존 read_at 유지
→ 정상 응답 반환
```

---

### Response

#### 200 OK — 읽음 처리 완료

```json
{
  "success": true,
  "code": "NOTIFICATION_READ_SUCCESS",
  "message": "알림을 읽음 처리했습니다.",
  "data": {
    "notificationId": 81,
    "isRead": true,
    "readAt": "2026-07-22T10:30:00+09:00",
    "unreadCount": 2
  },
  "timestamp": "2026-07-22T10:30:00+09:00"
}
```

| 필드 | 타입 | 설명 |
| --- | --- | --- |
| `notificationId` | Long | 읽음 처리한 알림 ID |
| `isRead` | Boolean | 현재 읽음 여부, 항상 `true` |
| `readAt` | String | 최초 읽음 처리 시각 |
| `unreadCount` | Integer | 읽음 처리 후 남아 있는 전체 읽지 않은 알림 수 |

---

#### 200 OK — 이미 읽은 알림

```json
{
  "success": true,
  "code": "NOTIFICATION_ALREADY_READ",
  "message": "이미 읽은 알림입니다.",
  "data": {
    "notificationId": 81,
    "isRead": true,
    "readAt": "2026-07-21T19:10:00+09:00",
    "unreadCount": 2
  },
  "timestamp": "2026-07-22T10:30:00+09:00"
}
```

이미 읽은 알림은 기존 `readAt`을 유지한다.

프론트에서는 두 성공 응답을 구분할 필요 없이 `isRead = true`와 `unreadCount`를 반영한다.

---

### Exception

#### 400 Bad Request — 잘못된 알림 ID

`notificationId`가 숫자가 아니거나 1보다 작은 경우

```json
{
  "success": false,
  "code": "NOTIFICATION_ID_INVALID",
  "message": "알림 ID가 올바르지 않습니다.",
  "data": {
    "field": "notificationId",
    "reason": "알림 ID는 1 이상의 숫자여야 합니다."
  },
  "timestamp": "2026-07-22T10:30:00+09:00"
}
```

---

#### 401 Unauthorized

Access Token이 없거나 유효하지 않은 경우

```json
{
  "success": false,
  "code": "AUTH_ACCESS_TOKEN_INVALID",
  "message": "로그인이 필요합니다.",
  "data": null,
  "timestamp": "2026-07-22T10:30:00+09:00"
}
```

---

#### 404 Not Found — 회원 없음

Access Token에 해당하는 회원이 존재하지 않거나 탈퇴한 경우

```json
{
  "success": false,
  "code": "MEMBER_NOT_FOUND",
  "message": "회원 정보를 찾을 수 없습니다.",
  "data": null,
  "timestamp": "2026-07-22T10:30:00+09:00"
}
```

---

#### 404 Not Found — 알림 없음 또는 접근 불가

알림이 존재하지 않거나 다른 회원의 알림인 경우

```json
{
  "success": false,
  "code": "NOTIFICATION_NOT_FOUND",
  "message": "알림을 찾을 수 없습니다.",
  "data": null,
  "timestamp": "2026-07-22T10:30:00+09:00"
}
```

다른 회원의 알림인지 실제로 존재하지 않는 알림인지는 구분하여 노출하지 않는다.

---

#### 410 Gone — 만료된 알림

알림 데이터가 만료 상태로 남아 있지만 더 이상 사용할 수 없는 경우

```json
{
  "success": false,
  "code": "NOTIFICATION_EXPIRED",
  "message": "만료된 알림입니다.",
  "data": {
    "notificationId": 81
  },
  "timestamp": "2026-07-22T10:30:00+09:00"
}
```

만료 알림을 즉시 물리 삭제하는 정책이면 이 응답 대신 `NOTIFICATION_NOT_FOUND`를 사용한다.

---

#### 500 Internal Server Error

```json
{
  "success": false,
  "code": "COMMON_INTERNAL_SERVER_ERROR",
  "message": "일시적인 오류가 발생했습니다.",
  "data": null,
  "timestamp": "2026-07-22T10:30:00+09:00"
}
```

---

### 프론트 처리

```
알림 목록에서 알림 선택
→ 해당 알림의 isRead 확인

isRead = false
→ PATCH /api/v1/notifications/{notificationId}/read 호출

isRead = true
→ 읽음 처리 API를 생략하고 바로 이동할 수 있음
→ 중복 호출해도 서버에서는 정상 처리

읽음 처리 성공
→ 해당 알림의 isRead를 true로 변경
→ readAt을 응답값으로 갱신
→ 홈과 알림 화면의 읽지 않은 알림 배지를 unreadCount로 갱신

targetAvailable = true
→ 읽음 처리 후 targetScreen과 targetId를 이용해 대상 화면으로 이동

targetAvailable = false
→ 읽음 처리만 수행
→ "더 이상 확인할 수 없는 내용입니다." 안내
→ 상세 화면으로 이동하지 않음

NOTIFICATION_ALREADY_READ 반환
→ 오류 메시지를 표시하지 않음
→ 읽은 알림 상태로 화면 갱신

NOTIFICATION_NOT_FOUND 반환
→ 현재 목록에서 해당 알림 제거
→ "알림을 확인할 수 없습니다." 안내

읽음 처리 API 실패
→ 대상 화면 이동이 반드시 필요한 경우 이동은 허용할 수 있음
→ 알림 읽음 상태는 기존 값 유지
→ 네트워크 복구 후 재시도
```

---

## 서울 구 목록 조회

Domain: Region
Method: GET
Progress: 완료
URI: /api/v1/regions/districts
담당자: 박재명
연동여부: Yes

서비스에서 지원하는 서울특별시 자치구 목록을 조회한다.

스터디·아파트 검색의 지역 필터와 회원 임장 선호 지역 선택 화면에서 사용한다.

서울특별시 25개 자치구를 자치구 코드와 이름 기준으로 반환한다.

---

### Request

- Request HTTP Method: `GET`
- 인증 필요: 필요
- Request Body: 없음
- Query Parameter: 없음

#### Request Header

```
Authorization: Bearer {accessToken}
```

#### 요청 예시

```
GET /api/v1/regions/districts
Authorization: Bearer {accessToken}
```

---

### 처리 기준

#### 1. 회원 확인

- 로그인한 회원 ID는 Access Token에서 확인한다.
- 회원 상태가 `ACTIVE`인 경우에만 조회할 수 있다.
- 탈퇴했거나 존재하지 않는 회원은 조회할 수 없다.

---

#### 2. 조회 대상

- 서비스 대상 지역인 서울특별시 자치구만 반환한다.
- 서울 외 지역은 반환하지 않는다.
- 행정구역 데이터 중 현재 사용 중인 자치구만 반환한다.
- 폐지되었거나 비활성화된 행정구역은 제외한다.
- 서울특별시는 현재 25개 자치구를 기준으로 한다.
- 반환하는 `districtCode`는 아파트·실거래·법정동 데이터 조회 시 사용하는 공공데이터 기준 자치구 코드와 일치시킨다.
- 자치구 코드는 문자열로 반환한다.
- 코드의 앞자리 `0`이 포함될 가능성을 고려해 숫자형으로 변환하지 않는다.

---

#### 3. 정렬 기준

자치구 목록은 이름의 가나다순으로 반환한다.

```
강남구
강동구
강북구
강서구
관악구
...
```

화면에서 별도의 재정렬 없이 그대로 표시할 수 있도록 서버에서 정렬한다.

---

#### 4. 하위 동 조회

사용자가 자치구를 선택하면 다음 API로 해당 자치구의 동 목록을 조회한다.

```
GET /api/v1/regions/districts/{districtCode}/dongs
```

- 이 API는 구 목록만 반환한다.
- 각 구의 동 목록을 함께 포함하지 않는다.
- 초기 응답 크기를 줄이고 선택된 자치구에 대해서만 동 목록을 조회한다.

---

#### 5. 캐시 기준

- 행정구역 데이터는 자주 변경되지 않는 기준정보다.
- 서버에서 장시간 캐시할 수 있다.
- 권장 캐시 시간은 12시간 이상이다.
- 프론트에서도 로컬 캐시를 사용할 수 있다.
- 캐시된 데이터가 있더라도 앱 재설치 또는 캐시 만료 후 서버에서 다시 조회한다.
- 행정구역 데이터 조회 실패가 발생한 경우 임의로 지역 코드를 생성하지 않는다.

---

### Response

#### 200 OK

```json
{
  "success": true,
  "code": "REGION_DISTRICT_LIST_SUCCESS",
  "message": "서울 자치구 목록 조회에 성공했습니다.",
  "data": {
    "cityCode": "11",
    "cityName": "서울특별시",
    "districts": [
      {
        "districtCode": "11680",
        "districtName": "강남구"
      },
      {
        "districtCode": "11740",
        "districtName": "강동구"
      },
      {
        "districtCode": "11305",
        "districtName": "강북구"
      },
      {
        "districtCode": "11500",
        "districtName": "강서구"
      },
      {
        "districtCode": "11620",
        "districtName": "관악구"
      },
      {
        "districtCode": "11215",
        "districtName": "광진구"
      },
      {
        "districtCode": "11530",
        "districtName": "구로구"
      },
      {
        "districtCode": "11545",
        "districtName": "금천구"
      },
      {
        "districtCode": "11350",
        "districtName": "노원구"
      },
      {
        "districtCode": "11320",
        "districtName": "도봉구"
      },
      {
        "districtCode": "11230",
        "districtName": "동대문구"
      },
      {
        "districtCode": "11590",
        "districtName": "동작구"
      },
      {
        "districtCode": "11440",
        "districtName": "마포구"
      },
      {
        "districtCode": "11410",
        "districtName": "서대문구"
      },
      {
        "districtCode": "11650",
        "districtName": "서초구"
      },
      {
        "districtCode": "11200",
        "districtName": "성동구"
      },
      {
        "districtCode": "11290",
        "districtName": "성북구"
      },
      {
        "districtCode": "11710",
        "districtName": "송파구"
      },
      {
        "districtCode": "11470",
        "districtName": "양천구"
      },
      {
        "districtCode": "11560",
        "districtName": "영등포구"
      },
      {
        "districtCode": "11170",
        "districtName": "용산구"
      },
      {
        "districtCode": "11380",
        "districtName": "은평구"
      },
      {
        "districtCode": "11110",
        "districtName": "종로구"
      },
      {
        "districtCode": "11140",
        "districtName": "중구"
      },
      {
        "districtCode": "11260",
        "districtName": "중랑구"
      }
    ],
    "totalCount": 25
  },
  "timestamp": "2026-07-22T10:30:00+09:00"
}
```

---

### Response Field

#### 서울특별시 정보

| 필드 | 타입 | 설명 |
| --- | --- | --- |
| `cityCode` | String | 서울특별시 행정구역 코드 |
| `cityName` | String | 시·도 이름, 항상 `서울특별시` |
| `districts` | Array | 서비스 대상 서울 자치구 목록 |
| `totalCount` | Integer | 반환된 자치구 수 |

#### 자치구 정보

| 필드 | 타입 | 설명 |
| --- | --- | --- |
| `districts[].districtCode` | String | 자치구 코드 |
| `districts[].districtName` | String | 자치구 이름 |

---

### 조회 결과가 없는 경우

정상적인 운영 상태에서는 서울 자치구 목록이 비어 있으면 안 된다.

다만 기준정보가 준비되지 않은 개발 환경에서는 빈 배열을 반환할 수 있다.

```json
{
  "success": true,
  "code": "REGION_DISTRICT_LIST_SUCCESS",
  "message": "서울 자치구 목록 조회에 성공했습니다.",
  "data": {
    "cityCode": "11",
    "cityName": "서울특별시",
    "districts": [],
    "totalCount": 0
  },
  "timestamp": "2026-07-22T10:30:00+09:00"
}
```

운영 환경에서 `totalCount = 0`이면 기준정보 적재 상태를 확인해야 한다.

---

### Exception

#### 401 Unauthorized

Access Token이 없거나 유효하지 않은 경우

```json
{
  "success": false,
  "code": "AUTH_ACCESS_TOKEN_INVALID",
  "message": "로그인이 필요합니다.",
  "data": null,
  "timestamp": "2026-07-22T10:30:00+09:00"
}
```

---

#### 404 Not Found

Access Token에 해당하는 회원이 존재하지 않거나 탈퇴한 경우

```json
{
  "success": false,
  "code": "MEMBER_NOT_FOUND",
  "message": "회원 정보를 찾을 수 없습니다.",
  "data": null,
  "timestamp": "2026-07-22T10:30:00+09:00"
}
```

---

#### 503 Service Unavailable — 지역 기준정보 조회 불가

행정구역 기준정보가 적재되지 않았거나 일시적으로 조회할 수 없는 경우

```json
{
  "success": false,
  "code": "REGION_DATA_UNAVAILABLE",
  "message": "지역 정보를 불러올 수 없습니다.",
  "data": null,
  "timestamp": "2026-07-22T10:30:00+09:00"
}
```

---

#### 500 Internal Server Error

```json
{
  "success": false,
  "code": "COMMON_INTERNAL_SERVER_ERROR",
  "message": "일시적인 오류가 발생했습니다.",
  "data": null,
  "timestamp": "2026-07-22T10:30:00+09:00"
}
```

---

### 프론트 처리

```
지역 선택 화면 진입
→ GET /api/v1/regions/districts 호출
→ districts를 가나다순 목록으로 표시

사용자가 자치구 선택
→ 선택한 districtCode 저장
→ GET /api/v1/regions/districts/{districtCode}/dongs 호출
→ 해당 자치구의 동 목록 표시

온보딩 관심 지역 선택
→ 자치구 목록 표시
→ 선택한 지역을 memberPreference에 저장

아파트·스터디 검색 필터
→ 자치구 선택값을 검색 조건으로 사용
→ 화면에는 districtName 표시
→ API 요청에는 districtCode 전달

목록 조회 성공
→ 로컬에 자치구 목록 캐시
→ 동일 세션에서 불필요한 재호출 방지

totalCount = 0
→ "지역 정보를 불러올 수 없습니다." 표시
→ 재시도 버튼 제공

REGION_DATA_UNAVAILABLE
→ 기존 캐시가 있으면 캐시 데이터 사용
→ 캐시도 없으면 지역 선택 기능 비활성화
→ 사용자에게 재시도 안내
```

---

## 내가 작성한 댓글 목록 조회

Method: GET
Progress: 시작 전
URI: /api/v1/members/me/comments
담당자: 김윤석
연동여부: No

커뮤니티의 `내 게시물 → 내가 쓴 댓글`에서 사용한다.

로그인한 회원이 커뮤니티 게시글에 작성한 댓글 목록을 조회한다.

개정본_v3에 따라 마이페이지가 아니라 **커뮤니티 탭의 `내 활동 > 작성 댓글` 화면**에서 사용한다.

최근에 작성한 댓글부터 반환하며, 원본 게시글이 삭제되거나 숨김 처리된 경우에는 현재 접근 상태를 함께 반환한다.

### Request

- HTTP Method: `GET`
- URI: `/api/v1/members/me/comments`
- 인증 필요: 필요

#### Query Parameter

```
GET /api/v1/members/me/comments?boardType=ALL&cursor=36&size=20
```

#### Request Header

```
Authorization: Bearer {accessToken}
```

### 처리 기준

- 로그인 회원이 작성한 삭제되지 않은 댓글을 반환한다.
- 댓글은 평면 구조이며 부모 댓글·대댓글 필드를 반환하지 않는다.
- 원문 이동에 필요한 `postId`를 반드시 반환한다.
- 게시글이 숨김·삭제 상태이면 댓글 내용과 게시글 제목을 제한하고 `originalAvailable=false`로 반환한다.

### Response

#### 200 OK

```json
{
  "success": true,
  "code": "MEMBER_COMMENT_LIST_SUCCESS",
  "message": "내가 작성한 댓글 목록 조회에 성공했습니다.",
  "data": {
    "content": [
      {
        "commentId": 36,
        "content": "주말 소음도 함께 확인해 보세요.",
        "postId": 124,
        "postTitle": "성동구 임장 후기입니다.",
        "postStatus": "ACTIVE",
        "originalAvailable": true,
        "createdAt": "2026-07-22T17:10:00+09:00"
      },
      {
        "commentId": 22,
        "content": null,
        "postId": 110,
        "postTitle": null,
        "postStatus": "HIDDEN",
        "originalAvailable": false,
        "createdAt": "2026-07-15T11:00:00+09:00"
      }
    ],
    "totalCount": 2,
    "nextCursor": null,
    "hasNext": false
  },
  "timestamp": "2026-07-24T16:45:00+09:00"
}
```

---

### 추가 Exception 예시

#### 400 Bad Request — 잘못된 게시판 종류

```json
{
  "success": false,
  "code": "MEMBER_COMMENT_BOARD_TYPE_INVALID",
  "message": "조회할 수 없는 게시판 종류입니다.",
  "data": {
    "field": "boardType",
    "allowedValues": [
      "INFORMATION",
      "FREE",
      "ALL"
    ]
  },
  "timestamp": "2026-07-22T10:30:00+09:00"
}
```

---

#### 400 Bad Request — 잘못된 커서

`cursor`가 숫자가 아니거나 1보다 작은 경우

```json
{
  "success": false,
  "code": "MEMBER_COMMENT_CURSOR_INVALID",
  "message": "댓글 목록 커서가 올바르지 않습니다.",
  "data": {
    "field": "cursor",
    "reason": "커서는 1 이상의 숫자여야 합니다."
  },
  "timestamp": "2026-07-22T10:30:00+09:00"
}
```

---

#### 400 Bad Request — 잘못된 조회 개수

`size`가 1보다 작거나 100을 초과한 경우

```json
{
  "success": false,
  "code": "COMMON_INVALID_REQUEST",
  "message": "입력값을 확인해 주세요.",
  "data": {
    "field": "size",
    "reason": "조회 개수는 1 이상 100 이하이어야 합니다."
  },
  "timestamp": "2026-07-22T10:30:00+09:00"
}
```

---

#### 401 Unauthorized

```json
{
  "success": false,
  "code": "AUTH_ACCESS_TOKEN_INVALID",
  "message": "로그인이 필요합니다.",
  "data": null,
  "timestamp": "2026-07-22T10:30:00+09:00"
}
```

---

#### 404 Not Found

Access Token에 해당하는 회원이 존재하지 않거나 탈퇴한 경우

```json
{
  "success": false,
  "code": "MEMBER_NOT_FOUND",
  "message": "회원 정보를 찾을 수 없습니다.",
  "data": null,
  "timestamp": "2026-07-22T10:30:00+09:00"
}
```

---

#### 500 Internal Server Error

```json
{
  "success": false,
  "code": "COMMON_INTERNAL_SERVER_ERROR",
  "message": "일시적인 오류가 발생했습니다.",
  "data": null,
  "timestamp": "2026-07-22T10:30:00+09:00"
}
```

---

### 추가 프론트 처리 기준

```
커뮤니티 탭 진입
→ 내 활동 선택
→ 작성 댓글 탭 선택
→ cursor 없이 첫 목록 조회

정보게시판 필터 선택
→ boardType = INFORMATION으로 목록 재조회

자유게시판 필터 선택
→ boardType = FREE로 목록 재조회

목록 하단 도달
→ hasNext 확인
→ hasNext = true이면 nextCursor를 cursor로 전달
→ 다음 댓글 목록 조회
→ 기존 content 뒤에 추가

commentStatus = ACTIVE
그리고 postAccessStatus = ACCESSIBLE
→ 댓글 내용과 원본 게시글 제목 표시
→ 항목 선택 시 GET /api/v1/posts/{postId} 호출
→ 해당 게시글의 댓글 위치로 이동

postAccessStatus = DELETED
→ "삭제된 게시글입니다." 표시
→ 게시글 상세 이동 비활성화

postAccessStatus = HIDDEN
→ "숨김 처리된 게시글입니다." 표시
→ 게시글 상세 이동 비활성화

postAccessStatus = ACCESS_DENIED
→ "접근할 수 없는 게시글입니다." 표시
→ 게시글 상세 이동 비활성화

commentStatus = HIDDEN
→ 댓글 내용 대신 "운영 정책에 따라 숨김 처리된 댓글입니다." 표시

댓글 삭제 성공
→ 현재 목록에서 해당 댓글 제거

댓글 수정 성공
→ 해당 목록 항목의 commentPreview와 updatedAt 갱신

content가 빈 배열
→ "작성한 댓글이 없습니다." 표시

nextCursor = null이고 hasNext = false
→ 추가 조회 중지
→ 목록 하단 로딩 UI 제거
```

---

## 임장 세션 상태 조회

Domain: Field Visit
Method: GET
Progress: 완료
URI: /api/v1/studies/{studyId}/field-visit
담당자: 윤다인
연동여부: No

승인된 스터디 멤버가 임장 세션의 현재 상태, 본인의 참여 상태, 체크리스트 진행 현황과 현재 사용할 수 있는 기능을 조회한다.

스터디 홈에서 임장 진행 화면으로 진입할 때 사용하며, 세션이 아직 시작되지 않았는지, 임장이 진행 중인지, 이미 종료되었는지에 따라 화면과 버튼 상태를 결정한다.

---

### Request

- Request HTTP Method: `GET`
- 인증 필요: 필요
- Request Body: 없음

#### Request Header

```
Authorization: Bearer {accessToken}
```

#### Path Variable

```
GET /api/v1/studies/7/field-visit
```

| 필드 | 타입 | 필수 | 설명 |
| --- | --- | --- | --- |
| `studyId` | Long | Y | 임장 세션 상태를 조회할 스터디 ID |

---

### 처리 기준

#### 1. 회원·스터디 권한 확인

- 로그인한 회원 ID는 Access Token에서 확인한다.
- 로그인 회원의 `member.status = ACTIVE`, `member.deleted_at = null`이어야 한다.
- `studyId`에 해당하는 스터디가 존재하고 `deletedAt = null`이어야 한다.
- 요청자는 해당 스터디의 스터디장이거나 `study_member.status = ACTIVE`인 승인 멤버여야 한다.
- 신청 대기자, 신청 거절자, 강퇴·탈퇴 처리된 멤버는 임장 세션 상태를 조회할 수 없다.
- 스터디가 `CANCELED` 상태이면 임장 진행 기능은 사용할 수 없으며, 세션이 이미 존재하는 경우에도 읽기 전용 상태로 반환한다.

#### 2. 세션 존재 여부

- `field_session`은 스터디당 최대 한 건만 존재한다.
- 스터디에 연결된 `field_session`이 없으면 API 파생 상태인 `NOT_STARTED`를 반환한다.
- `NOT_STARTED`는 DB에 저장하는 `field_session.status` 값이 아니라 세션 행이 없음을 표현하기 위한 응답 상태다.
- 세션이 존재하면 DB의 상태에 따라 `IN_PROGRESS` 또는 `ENDED`를 반환한다.

#### 3. 본인 참여 상태

- 세션이 존재하면 `field_participant.session_id`와 로그인 회원 ID로 본인의 참여 정보를 조회한다.
- 본인이 아직 GPS 시작을 완료하지 않아 참여 행이 없는 경우 `participant = null`로 반환한다.
- 본인 참여 상태는 `IN_PROGRESS`, `ENDED` 중 하나다.
- 진행 중인 참여자의 `stayDurationSec`는 서버 조회 시각과 `startedAt` 차이로 계산한다.
- 종료된 참여자는 `field_participant.stay_duration_sec`에 확정 저장된 값을 반환한다.
- 본인 종료 사유는 `SELF_ENDED`, `LEADER_FORCED`, `SESSION_ENDED`, `MAJORITY_FORCED` 중 하나이며 진행 중이면 `null`이다.

#### 4. 체크리스트 진행 현황

- 본인의 체크리스트가 존재하면 전체 항목 수와 완료 항목 수를 계산한다.
- 완료 항목은 `checklist_answer.is_completed = true`인 항목만 집계한다.
- 체크리스트가 아직 생성되지 않았으면 `checklistProgress.generated = false`, 완료·전체 개수는 0으로 반환한다.
- 현장 기록 개수는 삭제되지 않은 `field_record`만 집계한다.

#### 5. 경과 시간

- 세션이 진행 중이면 `elapsedSeconds`는 서버 조회 시각과 `field_session.started_at` 차이로 계산한다.
- 세션이 종료되었으면 `startedAt`과 `endedAt` 차이를 계산하여 확정값으로 반환한다.
- 계산 결과는 0 미만이 되지 않도록 한다.

#### 6. 기능 권한

- `canStart`는 다음을 모두 만족할 때 `true`다.
  1. 로그인 회원이 승인된 ACTIVE 멤버이고 본인 `participant`가 아직 없다
  2. `SCHEDULED` 일정이 있고 현재 시각이 `schedule.startAt`과 같거나 이후다
  3. 세션이 없으면 `study.status = CLOSED`이다
  4. 세션이 있으면 세션이 `IN_PROGRESS`이고 로그인 회원이 세션 시작 시 고정된 참여 후보이다
- `study.status`가 `CANCELED` 또는 `COMPLETED`이거나 본인 참여가 이미 있으면 `canStart = false`다.
- `canStart`는 GPS를 포함하지 않는다. GPS는 시작 API에서 최종 검증한다.
- `canEditChecklist`와 `canCreateRecord`는 스터디·세션·본인 참여 상태가 모두 `IN_PROGRESS`이고 리포트가 없을 때만 `true`다.
- 본인이 종료한 뒤에는 세션 전체가 진행 중이어도 체크리스트·메모를 수정할 수 없다.
- `canFinish`는 스터디·세션·본인 참여 상태가 모두 `IN_PROGRESS`일 때 `true`다.
- `canCloseSession`은 로그인 회원이 스터디장이고 스터디·세션 상태가 모두 `IN_PROGRESS`일 때만 `true`다.
- 세션이 종료되면 모든 쓰기 권한은 `false`이고 조회만 가능하다.

#### 7. 과반수 종료 투표 현황 (`closeVote`)

- 응답의 `closeVote`는 항상 객체를 반환하며 `null`이 아니다.
- 세션이 없으면 `startedParticipantCount=0`, `voteCount=0`, `requiredVoteCount=0`, `hasVoted=false`, `canVote=false`다.
- `startedParticipantCount`는 현재 session의 `field_participant` 전체 수다. `IN_PROGRESS`와 `ENDED`를 포함하고, candidate만 있고 아직 시작하지 않은 사용자는 제외한다.
- `requiredVoteCount = floor(startedParticipantCount / 2) + 1`이며, 시작 참여자가 0명이면 0이다.
- `hasVoted`는 조회 사용자의 `field_participant`가 있고 해당 participant의 종료 동의 투표 행이 있으면 `true`다.
- `canVote`는 session이 `IN_PROGRESS`이고 본인 participant가 있으며 아직 투표하지 않았을 때만 `true`다.
- participant가 `ENDED`여도 session이 `IN_PROGRESS`이고 미투표면 `canVote=true`일 수 있다.
- session이 `ENDED`이면 `canVote=false`다. 세션 종료 여부는 상위 `status == "ENDED"`로 판단하고, 종료 사유는 `session.endReason`으로 확인한다.
- `closeVote` 객체 자체에는 `sessionEnded` 필드가 없다.

---

### Response

#### 200 OK — 임장 진행 중

```json
{
  "success": true,
  "code": "FIELD_VISIT_STATUS_SUCCESS",
  "message": "임장 세션 상태 조회에 성공했습니다.",
  "data": {
    "studyId": 7,
    "status": "IN_PROGRESS",
    "session": {
      "sessionId": 100,
      "startedAt": "2026-07-25T14:00:00+09:00",
      "endedAt": null,
      "endedByMemberId": null,
      "endReason": null,
      "elapsedSeconds": 1830,
      "activeParticipantCount": 3,
      "endedParticipantCount": 1
    },
    "participant": {
      "participantId": 301,
      "status": "IN_PROGRESS",
      "startedAt": "2026-07-25T14:02:00+09:00",
      "endedAt": null,
      "endReason": null,
      "stayDurationSec": 1710
    },
    "checklistProgress": {
      "generated": true,
      "checklistId": 55,
      "completedCount": 6,
      "totalCount": 12,
      "recordCount": 8
    },
    "permissions": {
      "canStart": false,
      "canEditChecklist": true,
      "canCreateRecord": true,
      "canFinish": true,
      "canCloseSession": false
    },
    "closeVote": {
      "startedParticipantCount": 3,
      "voteCount": 1,
      "requiredVoteCount": 2,
      "hasVoted": true,
      "canVote": false
    }
  },
  "timestamp": "2026-07-25T14:30:30+09:00"
}
```

#### 200 OK — 세션 미시작

```json
{
  "success": true,
  "code": "FIELD_VISIT_STATUS_SUCCESS",
  "message": "임장 세션 상태 조회에 성공했습니다.",
  "data": {
    "studyId": 7,
    "status": "NOT_STARTED",
    "session": null,
    "participant": null,
    "checklistProgress": {
      "generated": false,
      "checklistId": null,
      "completedCount": 0,
      "totalCount": 0,
      "recordCount": 0
    },
    "permissions": {
      "canStart": true,
      "canEditChecklist": false,
      "canCreateRecord": false,
      "canFinish": false,
      "canCloseSession": false
    },
    "closeVote": {
      "startedParticipantCount": 0,
      "voteCount": 0,
      "requiredVoteCount": 0,
      "hasVoted": false,
      "canVote": false
    }
  },
  "timestamp": "2026-07-25T13:00:00+09:00"
}
```

#### 200 OK — 개인 종료 후 세션 진행 중

```json
{
  "success": true,
  "code": "FIELD_VISIT_STATUS_SUCCESS",
  "message": "임장 세션 상태 조회에 성공했습니다.",
  "data": {
    "studyId": 7,
    "status": "IN_PROGRESS",
    "session": {
      "sessionId": 100,
      "startedAt": "2026-07-25T14:00:00+09:00",
      "endedAt": null,
      "endedByMemberId": null,
      "endReason": null,
      "elapsedSeconds": 3600,
      "activeParticipantCount": 2,
      "endedParticipantCount": 1
    },
    "participant": {
      "participantId": 301,
      "status": "ENDED",
      "startedAt": "2026-07-25T14:02:00+09:00",
      "endedAt": "2026-07-25T14:48:00+09:00",
      "endReason": "SELF_ENDED",
      "stayDurationSec": 2760
    },
    "checklistProgress": {
      "generated": true,
      "checklistId": 55,
      "completedCount": 10,
      "totalCount": 12,
      "recordCount": 11
    },
    "permissions": {
      "canStart": false,
      "canEditChecklist": false,
      "canCreateRecord": false,
      "canFinish": false,
      "canCloseSession": false
    },
    "closeVote": {
      "startedParticipantCount": 3,
      "voteCount": 1,
      "requiredVoteCount": 2,
      "hasVoted": false,
      "canVote": true
    }
  },
  "timestamp": "2026-07-25T15:00:00+09:00"
}
```

---

### Response Field

#### 기본 정보

| 필드 | 타입 | 설명 |
| --- | --- | --- |
| `studyId` | Long | 스터디 ID |
| `status` | String | 임장 세션 상태. `NOT_STARTED`, `IN_PROGRESS`, `ENDED` |
| `session` | Object | null | 임장 세션 정보. 미시작이면 `null` |
| `participant` | Object | null | 로그인 회원의 참여 정보. 세션 미참여이면 `null` |
| `checklistProgress` | Object | 본인 체크리스트 진행 현황 |
| `permissions` | Object | 현재 사용자에게 허용된 임장 기능 |
| `closeVote` | Object | 과반수 종료 투표 현황. 세션이 없어도 기본값 객체를 반환 |

#### `session`

| 필드 | 타입 | 설명 |
| --- | --- | --- |
| `sessionId` | Long | 임장 세션 ID |
| `startedAt` | String | 세션 시작 시각 |
| `endedAt` | String | null | 세션 종료 시각 |
| `endedByMemberId` | Long | null | 전체 세션을 종료한 회원 ID |
| `endReason` | String | null | 세션 종료 사유. `ALL_ENDED`, `LEADER_FORCED`, `MAJORITY_FORCED` |
| `elapsedSeconds` | Integer | 세션 경과 시간, 초 단위 |
| `activeParticipantCount` | Integer | 현재 임장을 진행 중(`IN_PROGRESS`)인 참여자 수 |
| `endedParticipantCount` | Integer | 임장을 종료(`ENDED`)한 참여자 수 |

#### `participant`

| 필드 | 타입 | 설명 |
| --- | --- | --- |
| `participantId` | Long | 본인 참여 레코드 ID |
| `status` | String | 참여 상태. `IN_PROGRESS`, `ENDED` |
| `startedAt` | String | 본인 임장 시작 시각 |
| `endedAt` | String | null | 본인 임장 종료 시각 |
| `endReason` | String | null | `SELF_ENDED`, `LEADER_FORCED`, `SESSION_ENDED`, `MAJORITY_FORCED` |
| `stayDurationSec` | Integer | 본인 체류 시간, 초 단위 |

#### `checklistProgress`

| 필드 | 타입 | 설명 |
| --- | --- | --- |
| `generated` | Boolean | 본인 체크리스트 생성 여부 |
| `checklistId` | Long | null | 체크리스트 ID |
| `completedCount` | Integer | 완료 처리된 항목 수 |
| `totalCount` | Integer | 전체 항목 수 |
| `recordCount` | Integer | 삭제되지 않은 본인 현장 기록 수 |

#### `permissions`

| 필드 | 타입 | 설명 |
| --- | --- | --- |
| `canStart` | Boolean | GPS 검증 후 임장 시작 가능 여부 |
| `canEditChecklist` | Boolean | 체크리스트 완료 상태 변경 가능 여부 |
| `canCreateRecord` | Boolean | 현장 기록 생성 가능 여부 |
| `canFinish` | Boolean | 개인 임장 종료 가능 여부 |
| `canCloseSession` | Boolean | 스터디장 전체 마감 가능 여부 |

#### `closeVote`

| 필드 | 타입 | 설명 |
| --- | --- | --- |
| `startedParticipantCount` | Integer | 현재 session의 실제 시작 참여자 수 |
| `voteCount` | Integer | 종료 동의 수 |
| `requiredVoteCount` | Integer | 과반수 종료에 필요한 동의 수 |
| `hasVoted` | Boolean | 조회 사용자의 투표 여부 |
| `canVote` | Boolean | 조회 사용자의 투표 가능 여부 |

`closeVote`에는 `sessionEnded` 필드가 없다. 세션 종료 여부는 상위 `status`, 종료 사유는 `session.endReason`으로 확인한다.

---

### Exception

#### 400 Bad Request — 잘못된 스터디 ID

```json
{
  "success": false,
  "code": "COMMON_INVALID_REQUEST",
  "message": "입력값을 확인해 주세요.",
  "data": {
    "field": "studyId",
    "reason": "스터디 ID는 1 이상의 숫자여야 합니다."
  },
  "timestamp": "2026-07-25T13:00:00+09:00"
}
```

---

#### 401 Unauthorized

```json
{
  "success": false,
  "code": "AUTH_ACCESS_TOKEN_INVALID",
  "message": "로그인이 필요합니다.",
  "data": null,
  "timestamp": "2026-07-25T13:00:00+09:00"
}
```

---

#### 403 Forbidden — 승인된 스터디 멤버 아님

```json
{
  "success": false,
  "code": "FIELD_VISIT_ACCESS_DENIED",
  "message": "승인된 스터디 멤버만 임장 정보를 확인할 수 있습니다.",
  "data": null,
  "timestamp": "2026-07-25T13:00:00+09:00"
}
```

---

#### 404 Not Found — 스터디 없음

```json
{
  "success": false,
  "code": "STUDY_NOT_FOUND",
  "message": "스터디를 찾을 수 없습니다.",
  "data": null,
  "timestamp": "2026-07-25T13:00:00+09:00"
}
```

---

#### 500 Internal Server Error

```json
{
  "success": false,
  "code": "COMMON_INTERNAL_SERVER_ERROR",
  "message": "일시적인 오류가 발생했습니다.",
  "data": null,
  "timestamp": "2026-07-25T13:00:00+09:00"
}
```

---

### 프론트 처리

```
스터디 홈에서 임장 영역 진입
→ GET /api/v1/studies/{studyId}/field-visit 호출
→ status와 permissions에 따라 화면 구성

status = NOT_STARTED && canStart = true
→ "임장 시작" 버튼 표시
→ 선택 시 GPS 검증 후 임장 시작 API 호출

status = NOT_STARTED 또는 IN_PROGRESS && 일정 없음 또는 시작 시각 전
→ 슬라이드 완료 시 즉시 진행 확인 팝업 표시
→ 사용자가 진행을 선택한 경우에만 scheduleOverrideConfirmed=true로 시작 API 호출

status = IN_PROGRESS && participant.status = IN_PROGRESS
→ 임장 지도·경과 시간·체크리스트 완료 수 표시
→ 체크리스트와 현장 기록 입력 활성화

participant.status = ENDED
→ 체크리스트와 현장 기록을 읽기 전용으로 표시
→ 추가 작성·수정·삭제 버튼 숨김

status = ENDED
→ 임장 종료 화면 또는 리포트 생성 상태 화면으로 이동
→ 모든 임장 입력 기능 비활성화

participant = null && status = IN_PROGRESS
→ 본인이 아직 임장을 시작하지 않은 상태 안내
→ 시작 가능 조건을 다시 확인해 시작 버튼 표시
```

---

## GPS 검증 후 임장 시작

Method: POST
Progress: 완료
URI: /api/v1/studies/{studyId}/field-visit/start
담당자: 최태선
연동여부: No

승인된 스터디 멤버가 대상 아파트의 허용 반경 안에서 GPS 검증을 통과한 뒤 임장을 시작한다.

스터디에 임장 세션이 없으면 최초 요청에서 세션을 생성하고, 그 시점의 승인 멤버 명단을 `field_visit_candidate`에 고정한다. 고정 후보 중 현재도 ACTIVE이고 GPS 검증을 통과한 회원만 `field_participant`로 생성한다. 동일 회원의 중복 시작 요청은 새 참여 기록을 생성하지 않고 기존 결과를 반환한다.

---

### Request

- Request HTTP Method: `POST`
- 인증 필요: 필요
- Content-Type: `application/json`

#### Request Header

```
Authorization: Bearer {accessToken}
```

#### Path Variable

```
POST /api/v1/studies/7/field-visit/start
```

| 필드 | 타입 | 필수 | 설명 |
| --- | --- | --- | --- |
| `studyId` | Long | Y | 임장을 시작할 스터디 ID |

#### Request Body

```json
{
  "latitude": 37.5133,
  "longitude": 127.0842,
  "clientRequestId": "a1b2c3d4-1234-5678-90ab-cdef12345678",
  "scheduleOverrideConfirmed": false
}
```

| 필드 | 타입 | 필수 | 설명 |
| --- | --- | --- | --- |
| `latitude` | Double | Y | 사용자의 현재 위도, `-90~90` |
| `longitude` | Double | Y | 사용자의 현재 경도, `-180~180` |
| `clientRequestId` | String | Y | 중복 시작 요청 방지용 UUID |
| `scheduleOverrideConfirmed` | Boolean | N | 일정 없음/시작 시각 전 즉시 진행 확인 여부, 기본값 `false` |

---

### 처리 기준

#### 1. 회원·스터디·멤버 검증

- 로그인한 회원 ID는 Access Token에서 확인한다.
- 로그인 회원의 `member.status = ACTIVE`, `member.deleted_at = null`이어야 한다.
- `studyId`에 해당하는 스터디가 존재하고 취소·삭제 상태가 아니어야 한다.
- 로그인 회원은 스터디장이거나 `study_member.status = ACTIVE`인 승인 멤버여야 한다.
- 신청 대기·거절 회원, 강퇴·탈퇴 회원은 임장을 시작할 수 없다.
- 스터디 상태는 임장 시작이 가능한 상태여야 한다. 최초 세션 생성은 `study.status = CLOSED`일 때만 가능하다.
- 확인 플래그가 없거나 `false`인 일반 요청은 `SCHEDULED` 일정이 있고, 현재 시각이 `schedule.startAt`과 같거나 이후여야 세션·참여 기록을 생성한다.
- `scheduleOverrideConfirmed = true`이면 시작 트랜잭션 안에서 일정이 없을 때 현재 시각의 `SCHEDULED` 일정을 생성하고, 미래 일정이면 `startAt`을 서버 현재 시각으로 조정한 뒤 위의 동일한 일정 정책을 검증한다.
- 확인 플래그가 없거나 `false`인 기존 요청은 일정이 없거나 시작 시각 전일 때 일정을 변경하지 않고 기존과 동일하게 거부한다.
- `RECRUITING`, `COMPLETED`, `CANCELED` 상태에서는 세션을 생성하거나 진행 중 세션에 새로 참여할 수 없다.
- 이미 세션이 `IN_PROGRESS`이면 고정 참여 후보이면서 현재 ACTIVE인 멤버만 GPS 검증 후 개인 참여를 시작할 수 있다.
- 최초 세션 생성 트랜잭션에서 `study.status`를 `CLOSED → IN_PROGRESS`로 전환한다. 일반 시작 요청은 `schedule.status`를 변경하지 않으며, 즉시 진행 확인 요청만 누락·취소 일정을 `SCHEDULED`로 활성화할 수 있다.

#### 2. 위치값 검증

- 위도와 경도는 모두 필수이며 숫자 범위를 검증한다.
- 클라이언트가 위치 권한을 허용하지 않았거나 위치 수신에 실패한 경우 API를 호출하지 않는 것을 권장한다.
- 서버는 요청 좌표와 `apartment.latitude`, `apartment.longitude` 사이의 직선거리를 미터 단위로 계산한다.
- 허용 반경은 서버 설정값(`FIELD_VISIT_START_ALLOWED_RADIUS_METERS`, 기본 1000m)으로 관리하며 코드에 하드코딩하지 않는다.
- 계산된 거리가 허용 반경 이내(`distanceMeters <= allowedRadiusMeters`)이면 통과하고, 초과하면 세션·참여 기록을 생성하지 않는다.

#### 3. 세션 생성과 멤버 명단 고정

- 스터디의 `field_session`이 없으면 최초 시작 요청이 세션을 생성한다.
- 세션은 `status = IN_PROGRESS`, `startedAt = 서버 현재 시각`으로 저장한다.
- 세션 생성 시점에 `study_member.status = ACTIVE`인 승인 멤버 명단을 `field_visit_candidate`에 한 번 저장한다.
- 승인 멤버 전원을 `field_participant`로 미리 생성하지 않는다. 고정 후보가 실제 GPS 검증을 통과한 시점에만 participant를 생성한다.
- 세션 시작 후 새로 승인된 회원은 고정 후보가 아니므로 현재 임장 세션에 참여할 수 없다.
- 고정 명단은 이후 승인·강퇴·탈퇴로 변경하지 않는다. 단, 요청 시점에도 현재 ACTIVE 멤버여야 개인 참여를 시작할 수 있다.
- 세션 시작 후 신청 승인·멤버 강퇴 등 멤버 구성을 바꾸는 기능은 기존 정책대로 차단한다.
- 최초 세션이 생성되면 시작자를 제외한 고정 참여 후보의 알림함에 `FIELD_VISIT_STARTED` 알림을 한 건씩 저장한다.
- 후발 개인 참여, 기존 참여자의 재요청과 같은 `clientRequestId` 재시도에는 시작 알림을 추가하지 않는다.
- 수신자가 서비스 알림에 동의하고 등록된 FCM 토큰이 있으면 `FIELD_VISIT(studyId, sessionId)` 대상으로 Push를 전송한다.

#### 4. 개인 참여 시작

- 요청자의 `field_participant`가 없으면 `IN_PROGRESS` 상태로 생성한다.
- `startedAt`은 실제로 GPS 검증을 통과한 개인 시작 시각을 저장한다.
- 세션은 이미 진행 중이고 다른 멤버가 나중에 시작하는 경우, 세션 `startedAt`은 변경하지 않는다.
- 이미 본인이 `ENDED` 상태인 경우 다시 시작할 수 없다.

#### 5. 중복 요청과 멱등성

- 같은 `clientRequestId`가 재전송되면 기존 처리 결과를 반환한다.
- 멱등 재전송도 요청 시점의 ACTIVE 회원·스터디 멤버 권한을 다시 검증한다. 세션 또는 개인 참여가 이미 종료되었으면 기존 성공 응답 대신 각각 `FIELD_VISIT_ALREADY_ENDED`, `FIELD_PARTICIPANT_ALREADY_ENDED`를 반환하고, 취소·완료된 스터디에서는 시작을 허용하지 않는다.
- 네트워크 재시도로 서로 다른 `clientRequestId`가 전송되더라도 `session_id + member_id` 유일성으로 참여 기록을 중복 생성하지 않는다.
- 이미 진행 중인 요청자가 다시 시작하면 `200 OK`와 현재 세션·참여 정보를 반환한다.
- 동시에 최초 시작 요청이 여러 건 도착해도 스터디당 `field_session`은 한 건만 생성한다.
- `clientRequestId` 멱등성은 PostgreSQL 요청 이력 테이블(`field_visit_start_request`)로 처리하며, 최종 결과는 항상 하나의 세션·하나의 개인 참여 행이어야 한다.

#### 6. 체크리스트

- 임장 시작만으로 체크리스트가 자동 생성되는지 여부는 클라이언트 흐름에 따라 결정할 수 있다.
- 본 API는 세션과 개인 참여 시작까지 처리하며, 체크리스트가 없으면 이후 생성 API를 호출한다.

---

### Response

#### 201 Created — 최초 임장 시작

```json
{
  "success": true,
  "code": "FIELD_VISIT_START_SUCCESS",
  "message": "임장을 시작했습니다.",
  "data": {
    "studyId": 7,
    "apartmentId": 25,
    "distanceMeters": 82,
    "allowedRadiusMeters": 1000,
    "session": {
      "sessionId": 100,
      "status": "IN_PROGRESS",
      "startedAt": "2026-07-25T14:00:00+09:00"
    },
    "participant": {
      "participantId": 301,
      "status": "IN_PROGRESS",
      "startedAt": "2026-07-25T14:00:00+09:00"
    },
    "checklistGenerated": false
  },
  "timestamp": "2026-07-25T14:00:00+09:00"
}
```

#### 200 OK — 이미 시작한 상태

```json
{
  "success": true,
  "code": "FIELD_VISIT_ALREADY_STARTED",
  "message": "이미 임장을 시작한 상태입니다.",
  "data": {
    "studyId": 7,
    "apartmentId": 25,
    "distanceMeters": 80,
    "allowedRadiusMeters": 1000,
    "session": {
      "sessionId": 100,
      "status": "IN_PROGRESS",
      "startedAt": "2026-07-25T14:00:00+09:00"
    },
    "participant": {
      "participantId": 301,
      "status": "IN_PROGRESS",
      "startedAt": "2026-07-25T14:00:00+09:00"
    },
    "checklistGenerated": true
  },
  "timestamp": "2026-07-25T14:05:00+09:00"
}
```

---

### Response Field

| 필드 | 타입 | 설명 |
| --- | --- | --- |
| `studyId` | Long | 스터디 ID |
| `apartmentId` | Long | 임장 대상 아파트 ID |
| `distanceMeters` | Integer | 요청 위치와 대상 아파트 사이 거리 |
| `allowedRadiusMeters` | Integer | 서버에 설정된 시작 허용 반경 |
| `session` | Object | 임장 세션 정보 |
| `participant` | Object | 로그인 회원의 개인 참여 정보 |
| `checklistGenerated` | Boolean | 본인 체크리스트가 이미 생성되어 있는지 여부 |

#### `session`

| 필드 | 타입 | 설명 |
| --- | --- | --- |
| `sessionId` | Long | 임장 세션 ID |
| `status` | String | 세션 상태. 시작 성공 시 `IN_PROGRESS` |
| `startedAt` | String | 세션 최초 시작 시각 |

#### `participant`

| 필드 | 타입 | 설명 |
| --- | --- | --- |
| `participantId` | Long | 개인 참여 레코드 ID |
| `status` | String | 개인 참여 상태. 시작 성공 시 `IN_PROGRESS` |
| `startedAt` | String | 개인 임장 시작 시각 |

---

### Exception

#### 400 Bad Request — 위치 정보 오류

```json
{
  "success": false,
  "code": "FIELD_VISIT_LOCATION_INVALID",
  "message": "현재 위치 정보를 확인해 주세요.",
  "data": {
    "field": "latitude",
    "reason": "위도는 -90 이상 90 이하이어야 합니다."
  },
  "timestamp": "2026-07-25T14:00:00+09:00"
}
```

---

#### 400 Bad Request — clientRequestId 멱등 충돌

```json
{
  "success": false,
  "code": "FIELD_VISIT_START_IDEMPOTENCY_MISMATCH",
  "message": "동일한 요청 ID를 다른 임장 시작 요청에 사용할 수 없습니다.",
  "data": null,
  "timestamp": "2026-07-25T14:00:00+09:00"
}
```

---

#### 400 Bad Request — clientRequestId 형식 오류

```json
{
  "success": false,
  "code": "COMMON_INVALID_REQUEST",
  "message": "입력값을 확인해 주세요.",
  "data": {
    "field": "clientRequestId",
    "reason": "clientRequestId는 UUID 형식이어야 합니다."
  },
  "timestamp": "2026-07-25T14:00:00+09:00"
}
```

---

#### 401 Unauthorized

```json
{
  "success": false,
  "code": "AUTH_ACCESS_TOKEN_INVALID",
  "message": "로그인이 필요합니다.",
  "data": null,
  "timestamp": "2026-07-25T14:00:00+09:00"
}
```

---

#### 403 Forbidden — 승인 멤버 아님

```json
{
  "success": false,
  "code": "FIELD_VISIT_START_FORBIDDEN",
  "message": "승인된 스터디 멤버만 임장을 시작할 수 있습니다.",
  "data": null,
  "timestamp": "2026-07-25T14:00:00+09:00"
}
```

---

#### 404 Not Found — 스터디 없음

```json
{
  "success": false,
  "code": "STUDY_NOT_FOUND",
  "message": "스터디를 찾을 수 없습니다.",
  "data": null,
  "timestamp": "2026-07-25T14:00:00+09:00"
}
```

---

#### 422 Unprocessable Entity — 허용 반경 밖

```json
{
  "success": false,
  "code": "FIELD_VISIT_OUT_OF_RANGE",
  "message": "임장 지역이 아닙니다.",
  "data": {
    "distanceMeters": 1200,
    "allowedRadiusMeters": 1000
  },
  "timestamp": "2026-07-25T14:00:00+09:00"
}
```

---

#### 409 Conflict — 임장 시작 시간이 아님

```json
{
  "success": false,
  "code": "FIELD_VISIT_START_NOT_AVAILABLE",
  "message": "임장 시작 시간이 아닙니다.",
  "data": null,
  "timestamp": "2026-07-25T14:00:00+09:00"
}
```

---

#### 409 Conflict — 이미 개인 임장 종료

```json
{
  "success": false,
  "code": "FIELD_PARTICIPANT_ALREADY_ENDED",
  "message": "이미 종료한 임장은 다시 시작할 수 없습니다.",
  "data": null,
  "timestamp": "2026-07-25T14:00:00+09:00"
}
```

---

#### 409 Conflict — 세션 종료

```json
{
  "success": false,
  "code": "FIELD_VISIT_ALREADY_ENDED",
  "message": "이미 종료된 임장 세션입니다.",
  "data": null,
  "timestamp": "2026-07-25T14:00:00+09:00"
}
```

---

#### 500 Internal Server Error

```json
{
  "success": false,
  "code": "COMMON_INTERNAL_SERVER_ERROR",
  "message": "일시적인 오류가 발생했습니다.",
  "data": null,
  "timestamp": "2026-07-25T14:00:00+09:00"
}
```

---

### 프론트 처리

```
사용자가 임장 시작 선택
→ 위치 권한 확인
→ 현재 GPS 위치 수신
→ clientRequestId 생성
→ POST /api/v1/studies/{studyId}/field-visit/start 호출

201 Created 또는 FIELD_VISIT_ALREADY_STARTED
→ sessionId와 participantId 저장
→ 임장 지도 화면 진입
→ checklistGenerated = false이면 체크리스트 생성 API 호출

FIELD_VISIT_OUT_OF_RANGE
→ 현재 거리와 허용 반경 표시
→ "임장 지역이 아닙니다." 안내
→ 자동으로 반복 요청하지 않음

FIELD_VISIT_START_NOT_AVAILABLE
→ 임장 약속 시각 이후 다시 시도하도록 안내
→ 세션·참여 상태가 바뀌지 않음을 전제한다

위치 권한 거부 또는 위치 수신 실패
→ API 호출하지 않음
→ 위치 권한 설정 또는 재시도 안내

FIELD_PARTICIPANT_ALREADY_ENDED 또는 FIELD_VISIT_ALREADY_ENDED
→ 쓰기 화면으로 진입하지 않음
→ 읽기 전용 종료 화면 또는 리포트 상태 화면으로 이동
```

---

## 월별 임장 달력 조회

Method: GET
Progress: 완료
URI: /api/v1/members/me/visit-calendar
담당자: 박재명
연동여부: Yes

로그인한 회원의 특정 연도·월 임장 일정을 달력 형식으로 조회한다.

마이페이지의 `임장 달력` 화면에서 사용하며, 로그인한 회원이 승인된 스터디원 또는 스터디장으로 참여한 임장 일정만 반환한다.

예정된 임장과 완료된 임장을 구분하고, 같은 날짜에 여러 임장이 존재하면 해당 날짜의 일정 목록을 모두 반환한다.

### Request

- HTTP Method: `GET`
- URI: `/api/v1/members/me/visit-calendar`
- 인증 필요: 필요

#### Query Parameter

```
GET /api/v1/members/me/visit-calendar?year=2026&month=7
```

| 필드 | 타입 | 필수 | 설명 |
| --- | --- | --- | --- |
| `year` | Integer | Y | 조회 연도, 2000~2100 |
| `month` | Integer | Y | 조회 월, 1~12 |

#### Request Header

```
Authorization: Bearer {accessToken}
```

### 처리 기준

- Asia/Seoul 기준 월 시작·종료 범위를 적용한다.
- 스터디장 또는 `ACTIVE` 멤버로 참여 확정된 일정만 반환한다.
- 승인 전·거절 신청, `CANCELED` 일정, `REMOVED` 멤버 관계는 제외한다.
- `SCHEDULED`, `COMPLETED` 일정을 구분해 반환한다.
- 동일 날짜의 복수 일정도 모두 반환한다.
- `READY`, `REPORTING` 상태는 사용하지 않는다.

### Response

#### 200 OK

```json
{
  "success": true,
  "code": "MEMBER_VISIT_CALENDAR_SUCCESS",
  "message": "월별 임장 달력 조회에 성공했습니다.",
  "data": {
    "year": 2026,
    "month": 7,
    "monthlyVisitCount": 2,
    "dates": [
      {
        "date": "2026-07-27",
        "visits": [
          {
            "scheduleId": 7,
            "studyId": 10,
            "studyTitle": "옥수동 주말 임장",
            "apartmentName": "래미안 옥수 리버젠",
            "startAt": "2026-07-27T15:00:00+09:00",
            "scheduleStatus": "SCHEDULED"
          }
        ]
      }
    ]
  },
  "timestamp": "2026-07-24T17:00:00+09:00"
}
```

### Exception

- `400 MEMBER_VISIT_CALENDAR_YEAR_INVALID`
- `400 MEMBER_VISIT_CALENDAR_MONTH_INVALID`
- `401 AUTH_ACCESS_TOKEN_INVALID`
- `404 MEMBER_NOT_FOUND`
- `500 COMMON_INTERNAL_SERVER_ERROR`

---

### 추가 Exception 예시

#### 400 Bad Request — 연도 누락

```json
{
  "success": false,
  "code": "COMMON_INVALID_REQUEST",
  "message": "입력값을 확인해 주세요.",
  "data": {
    "field": "year",
    "reason": "조회할 연도는 필수 값입니다."
  },
  "timestamp": "2026-07-22T10:30:00+09:00"
}
```

---

#### 400 Bad Request — 잘못된 연도

```json
{
  "success": false,
  "code": "MEMBER_VISIT_CALENDAR_YEAR_INVALID",
  "message": "조회할 수 없는 연도입니다.",
  "data": {
    "field": "year",
    "reason": "연도는 2000 이상 2100 이하이어야 합니다."
  },
  "timestamp": "2026-07-22T10:30:00+09:00"
}
```

---

#### 400 Bad Request — 월 누락

```json
{
  "success": false,
  "code": "COMMON_INVALID_REQUEST",
  "message": "입력값을 확인해 주세요.",
  "data": {
    "field": "month",
    "reason": "조회할 월은 필수 값입니다."
  },
  "timestamp": "2026-07-22T10:30:00+09:00"
}
```

---

#### 400 Bad Request — 잘못된 월

```json
{
  "success": false,
  "code": "MEMBER_VISIT_CALENDAR_MONTH_INVALID",
  "message": "조회할 수 없는 월입니다.",
  "data": {
    "field": "month",
    "reason": "월은 1 이상 12 이하이어야 합니다."
  },
  "timestamp": "2026-07-22T10:30:00+09:00"
}
```

---

#### 401 Unauthorized

```json
{
  "success": false,
  "code": "AUTH_ACCESS_TOKEN_INVALID",
  "message": "로그인이 필요합니다.",
  "data": null,
  "timestamp": "2026-07-22T10:30:00+09:00"
}
```

---

#### 404 Not Found

Access Token에 해당하는 회원이 존재하지 않거나 탈퇴한 경우

```json
{
  "success": false,
  "code": "MEMBER_NOT_FOUND",
  "message": "회원 정보를 찾을 수 없습니다.",
  "data": null,
  "timestamp": "2026-07-22T10:30:00+09:00"
}
```

---

#### 500 Internal Server Error

```json
{
  "success": false,
  "code": "COMMON_INTERNAL_SERVER_ERROR",
  "message": "일시적인 오류가 발생했습니다.",
  "data": null,
  "timestamp": "2026-07-22T10:30:00+09:00"
}
```

---

### 추가 프론트 처리 기준

```
마이페이지에서 임장 달력 선택
→ 현재 연도와 월을 Query Parameter로 전달
→ 월별 임장 달력 조회 API 호출
→ 응답의 dates를 달력 날짜에 표시

달력 날짜 표시
→ SCHEDULED 일정이 있으면 예정 일정 표시
→ COMPLETED 일정이 있으면 완료 일정 표시
→ 같은 날짜에 여러 일정이 있으면 개수 배지 표시

사용자가 날짜 선택
→ 해당 dates[].visits 목록을 시간순으로 하단에 표시

예정된 임장 선택
→ scheduleStatus = SCHEDULED 확인
→ studyId를 이용해 스터디 홈으로 이동

완료된 임장 선택
→ scheduleStatus = COMPLETED 확인
→ 읽기 전용 스터디 홈으로 이동
→ 과거 임장 기록과 AI 리포트 조회 기능 제공

이전 달 또는 다음 달 이동
→ 이동한 year와 month로 API 재호출
→ 기존 달력 데이터 교체

dates가 빈 배열
→ 달력 날짜에는 일정 표시 없음
→ 하단에 "이달의 임장 일정이 없습니다." 안내

같은 날짜에 일정이 여러 개 존재
→ 날짜에 전체 일정 수 표시
→ 선택 시 모든 일정을 startAt 순으로 표시
```

---

## 커뮤니티 게시글 작성

Method: POST
Progress: 완료
URI: /api/v1/posts
담당자: 김윤석
연동여부: No

로그인한 회원이 정보게시판 또는 자유게시판에 일반 커뮤니티 게시글을 작성한다.

게시판 선택, 제목·본문 입력, 업로드 완료 파일 첨부의 순서로 처리하며, 회원은 자동 리포트 게시글 여부와 연결 리포트를 직접 지정할 수 없다.

---

### Request

- Request HTTP Method: `POST`
- 인증 필요: 필요
- Content-Type: `application/json`

#### Request Header

```
Authorization: Bearer {accessToken}
```

#### Request Body

```
{
  "boardType": "INFORMATION",
  "title": "성동구 임장 시 확인하면 좋은 항목을 공유합니다.",
  "content": "교통과 상권뿐 아니라 경사, 보행 환경, 주말 소음도 함께 확인해 보세요.",
  "apartmentId": 15,
  "fileIds": [401, 402]
}
```

| 필드 | 타입 | 필수 | 설명 |
| --- | --- | --- | --- |
| `boardType` | String | Y | 게시판 유형. `INFORMATION`, `FREE` |
| `title` | String | Y | 게시글 제목, 앞뒤 공백 제거 후 1~200자 |
| `content` | String | Y | 게시글 본문, 앞뒤 공백 제거 후 1~5000자 |
| `apartmentId` | Long | N | 게시글과 연결할 아파트 ID |
| `fileIds` | Array<Long> | N | 업로드 완료 첨부 파일 ID 목록, 최대 10개 |

---

### 게시판 유형

| 값 | 설명 |
| --- | --- |
| `INFORMATION` | 아파트·지역·임장 정보 공유 |
| `FREE` | 자유로운 주제의 커뮤니티 소통 |

---

### 파일 업로드 선행 흐름

```
POST /api/v1/media/presigned-urls
→ fileUsage = POST_ATTACHMENT로 업로드 URL 발급
→ 클라이언트가 S3에 파일 업로드
→ POST /api/v1/media/{fileId}/complete
→ 완료된 fileId를 게시글 작성 요청에 포함
```

---

### 처리 기준

#### 1. 회원 확인

- 로그인한 회원 ID는 Access Token에서 확인한다.
- 회원 상태가 `ACTIVE`인 경우에만 게시글을 작성할 수 있다.
- 작성자는 Request Body로 전달받지 않고 로그인 회원으로 설정한다.

#### 2. 게시판·제목·본문 검증

- `boardType`은 `INFORMATION`, `FREE` 중 하나여야 한다.
- 기존 `INFO` 값은 허용하지 않는다.
- 제목과 본문의 앞뒤 공백을 제거한다.
- 공백만 있는 제목이나 본문은 저장할 수 없다.
- 제목은 최종 ERD의 `VARCHAR(200)`에 맞춰 최대 200자다.
- 본문 최대 길이 5000자는 기존 API 입력 정책을 유지한다.
- HTML·스크립트가 전달되면 저장·출력 정책에 따라 이스케이프하고 실행되지 않도록 한다.

#### 3. 일반 게시글과 자동 리포트 게시글 구분

회원이 작성하는 게시글은 항상 다음 값으로 저장한다.

```
isAutoReport = false
reportId = null
```

- 요청 Body에 `isAutoReport` 또는 `reportId`를 포함해도 바인딩하지 않거나 오류 처리한다.
- 자동 리포트 게시글은 `PUT /internal/v1/reports/{reportId}/complete`의 **최초 성공**을 처리하는 서버 로직에서만 생성한다. `POST /api/v1/posts`를 내부적으로 호출하지 않는다.
- 완료 리포트·근거·자동 정보게시글은 같은 DB 트랜잭션에서 저장한다. 자동 글의 생성·구성에 실패하면 리포트는 `DONE`으로 남지 않는다.
- 자동 글은 `INFORMATION`, `isAutoReport = true`, `authorId = null`, 해당 `reportId`와 `apartmentId`로 저장한다. 제목은 완료 결과의 `title`이고 본문에는 아파트명·임장 시작일·요약 일부·`/api/v1/reports/{reportId}` 경로를 저장한다.
- `post.report_id`가 자동 글의 멱등 키다. 같은 완료 요청 또는 중복 완료 이벤트는 기존 글을 변경하지 않으며, soft delete 여부와 무관하게 리포트당 자동 글은 한 건만 존재한다.

#### 4. 아파트 연결

- `apartmentId`는 선택값이다.
- 전달된 경우 실제 존재하고 서비스 조회 가능한 아파트인지 확인한다.
- 정보게시판과 자유게시판 모두 필요하면 아파트를 연결할 수 있다.
- 연결 아파트가 없으면 `apartment_id = null`로 저장한다.
- 게시글 작성이 아파트 찜·스터디·리포트 상태를 변경하지 않는다.

#### 5. 첨부 파일 검증

- `fileIds`가 없거나 빈 배열이면 첨부 없이 작성한다.
- 최대 10개까지 허용한다.
- 중복 fileId를 허용하지 않는다.
- 모든 파일은 다음 조건을 만족해야 한다.

```
업로드 회원 = 로그인 회원
fileUsage = POST_ATTACHMENT
uploadStatus = COMPLETED
deletedAt IS NULL
다른 게시글에 이미 연결되지 않음
```

- 배열 순서대로 `post_attachment.display_order`를 0 또는 1부터 일관되게 저장한다.
- 본 명세의 응답은 1부터 표시하지만 DB는 0부터 저장해도 된다.
- 하나라도 사용할 수 없는 파일이 있으면 게시글과 첨부 연결 전체를 롤백한다.

#### 6. 게시글 저장

초기값은 다음과 같다.

```
status = ACTIVE
isAutoReport = false
viewCount = 0
deletedAt = null
```

- 좋아요와 댓글은 별도 관계 테이블로 관리하며 최초 개수는 0이다.
- `createdAt`, `updatedAt`은 서버 시각을 사용한다.
- 게시글과 `post_attachment` 연결을 하나의 트랜잭션으로 처리한다.
- 작성 성공만으로 작성자가 자신의 글에 좋아요를 누른 상태가 되지 않는다.

#### 7. 미사용 업로드 파일

- 게시글 작성 실패 시 이미 S3 업로드를 완료한 파일이 즉시 물리 삭제되는지는 미사용 파일 정리 정책에 따른다.
- 프론트가 작성 화면을 이탈한 경우 연결되지 않은 파일을 만료 배치로 정리할 수 있다.
- 내부 S3 Key는 게시글 응답에 노출하지 않는다.

---

### Response

#### 201 Created

```
{
  "success": true,
  "code": "POST_CREATE_SUCCESS",
  "message": "게시글이 작성되었습니다.",
  "data": {
    "postId": 154,
    "boardType": "INFORMATION",
    "title": "성동구 임장 시 확인하면 좋은 항목을 공유합니다.",
    "content": "교통과 상권뿐 아니라 경사, 보행 환경, 주말 소음도 함께 확인해 보세요.",
    "status": "ACTIVE",
    "author": {
      "memberId": 7,
      "nickname": "집보는다람쥐",
      "profileImageUrl": null,
      "selectedCharacterId": "JIPKONG",
      "authorType": "MEMBER"
    },
    "isAutoReport": false,
    "apartment": {
      "apartmentId": 15,
      "name": "래미안 옥수 리버젠"
    },
    "report": null,
    "attachments": [
      {
        "fileId": 401,
        "originalName": "station-route.jpg",
        "contentType": "image/jpeg",
        "fileUrl": "https://s3.example.com/presigned/post-401",
        "displayOrder": 1,
        "expiresAt": "2026-07-25T16:40:00+09:00"
      },
      {
        "fileId": 402,
        "originalName": "complex-slope.jpg",
        "contentType": "image/jpeg",
        "fileUrl": "https://s3.example.com/presigned/post-402",
        "displayOrder": 2,
        "expiresAt": "2026-07-25T16:40:00+09:00"
      }
    ],
    "viewCount": 0,
    "likeCount": 0,
    "commentCount": 0,
    "likedByMe": false,
    "isMine": true,
    "isHot": false,
    "createdAt": "2026-07-25T16:30:00+09:00",
    "updatedAt": "2026-07-25T16:30:00+09:00"
  },
  "timestamp": "2026-07-25T16:30:00+09:00"
}
```

---

### Response Field

| 필드 | 타입 | 설명 |
| --- | --- | --- |
| `postId` | Long | 생성된 게시글 ID |
| `boardType` | String | 게시판 유형 |
| `title` | String | 게시글 제목 |
| `content` | String | 게시글 본문 |
| `status` | String | 최초 생성 시 `ACTIVE` |
| `author` | Object | 작성자 정보 |
| `isAutoReport` | Boolean | 일반 회원 글은 `false` |
| `apartment` | Object | null | 연결 아파트 |
| `report` | Object | null | 일반 회원 글은 `null` |
| `attachments` | Array | 첨부 파일 목록 |
| `viewCount` | Long | 최초 생성 시 0 |
| `likeCount` | Long | 최초 생성 시 0 |
| `commentCount` | Long | 최초 생성 시 0 |
| `likedByMe` | Boolean | 최초 생성 시 `false` |
| `isMine` | Boolean | 작성자이므로 `true` |
| `isHot` | Boolean | 최초 작성 직후 HOT 기준 충족 여부 |
| `createdAt` | String | 생성 시각 |
| `updatedAt` | String | 최종 수정 시각 |

---

### Exception

#### 400 Bad Request — 입력값 오류

```
{
  "success": false,
  "code": "COMMON_INVALID_REQUEST",
  "message": "입력값을 확인해 주세요.",
  "data": {
    "field": "title",
    "reason": "제목은 1자 이상 200자 이하이어야 합니다."
  },
  "timestamp": "2026-07-25T16:30:00+09:00"
}
```

---

#### 400 Bad Request — 게시판 유형 오류

```
{
  "success": false,
  "code": "POST_BOARD_TYPE_INVALID",
  "message": "유효하지 않은 게시판 유형입니다.",
  "data": {
    "allowedValues": ["INFORMATION", "FREE"]
  },
  "timestamp": "2026-07-25T16:30:00+09:00"
}
```

---

#### 400 Bad Request — 첨부 파일 개수 초과

```
{
  "success": false,
  "code": "POST_ATTACHMENT_LIMIT_EXCEEDED",
  "message": "첨부 파일은 최대 10개까지 등록할 수 있습니다.",
  "data": null,
  "timestamp": "2026-07-25T16:30:00+09:00"
}
```

---

#### 401 Unauthorized

```
{
  "success": false,
  "code": "AUTH_ACCESS_TOKEN_INVALID",
  "message": "로그인이 필요합니다.",
  "data": null,
  "timestamp": "2026-07-25T16:30:00+09:00"
}
```

---

#### 403 Forbidden — 사용할 수 없는 파일

```
{
  "success": false,
  "code": "MEDIA_ACCESS_DENIED",
  "message": "사용할 수 없는 첨부 파일입니다.",
  "data": {
    "fileId": 401
  },
  "timestamp": "2026-07-25T16:30:00+09:00"
}
```

---

#### 404 Not Found — 아파트 없음

```
{
  "success": false,
  "code": "APARTMENT_NOT_FOUND",
  "message": "아파트 정보를 찾을 수 없습니다.",
  "data": null,
  "timestamp": "2026-07-25T16:30:00+09:00"
}
```

---

#### 404 Not Found — 첨부 파일 없음

```
{
  "success": false,
  "code": "MEDIA_FILE_NOT_FOUND",
  "message": "첨부 파일을 찾을 수 없습니다.",
  "data": {
    "fileId": 401
  },
  "timestamp": "2026-07-25T16:30:00+09:00"
}
```

---

#### 409 Conflict — 첨부 파일 중복 연결

```
{
  "success": false,
  "code": "POST_ATTACHMENT_ALREADY_USED",
  "message": "이미 다른 게시글에 사용된 첨부 파일입니다.",
  "data": {
    "fileId": 401
  },
  "timestamp": "2026-07-25T16:30:00+09:00"
}
```

---

#### 500 Internal Server Error

```
{
  "success": false,
  "code": "COMMON_INTERNAL_SERVER_ERROR",
  "message": "일시적인 오류가 발생했습니다.",
  "data": null,
  "timestamp": "2026-07-25T16:30:00+09:00"
}
```

---

### 프론트 처리

```
게시글 작성 선택
→ 게시판 INFORMATION/FREE 선택
→ 제목과 본문 입력
→ 필요하면 아파트 연결
→ 필요하면 파일 업로드
→ POST /api/v1/posts 호출

파일 첨부
→ POST_ATTACHMENT 용도로 Presigned URL 발급
→ S3 업로드
→ 업로드 완료 처리
→ 반환된 fileId를 배열 순서대로 전달

작성 성공
→ 생성된 postId로 상세 화면 이동
→ 현재 게시판 최신 목록 첫 페이지 갱신

입력값 오류
→ data.field에 해당하는 입력 영역에 오류 표시
→ 작성 중인 제목·본문·첨부 상태 유지

작성 실패
→ 연결되지 않은 업로드 파일은 미사용 파일 정리 정책에 따름
→ 사용자가 명시적으로 취소하기 전 작성 내용 유지
```

---

## 커뮤니티 게시글 상세 조회

Method: GET
Progress: 완료
URI: /api/v1/posts/{postId}
담당자: 김윤석
연동여부: No

게시글 ID를 기준으로 커뮤니티 게시글의 전체 본문, 작성자, 연결 아파트·리포트, 첨부 파일, 조회 수, 좋아요·댓글 수와 현재 사용자의 권한을 조회한다.

정상 상세 조회가 완료되면 조회 수를 증가시킨다.

---

### Request

- Request HTTP Method: `GET`
- 인증 필요: 필요
- Request Body: 없음

#### Request Header

```
Authorization: Bearer {accessToken}
```

#### Path Variable

```
GET /api/v1/posts/154
```

| 필드 | 타입 | 필수 | 설명 |
| --- | --- | --- | --- |
| `postId` | Long | Y | 조회할 게시글 ID |

---

### 처리 기준

#### 1. 회원·게시글 확인

- 로그인한 회원 ID는 Access Token에서 확인한다.
- 회원 상태가 `ACTIVE`인지 확인한다.
- `postId`에 해당하는 게시글이 존재하는지 확인한다.
- `post.deletedAt IS NULL`, `status = ACTIVE`인 게시글만 일반 상세 조회가 가능하다.
- 작성자가 삭제한 글과 운영상 `HIDDEN` 처리된 글은 일반 상세 API에서 원문을 반환하지 않는다.
- 삭제·숨김 여부를 과도하게 구분해 노출하지 않고 `POST_NOT_FOUND` 또는 접근 불가 오류로 처리할 수 있다.

#### 2. 조회 수 증가

- 최종 요구사항에는 게시글 조회 수 표시가 포함되므로 정상 상세 조회 시 `view_count`를 증가시킨다.
- 목록 조회와 댓글 목록 조회만으로는 증가시키지 않는다.
- 최종 요구사항·ERD에는 동일 사용자의 중복 조회 방지 시간이 확정되어 있지 않다.
- **본 MVP 명세에서는 정상 상세 조회 요청마다 1 증가하는 단순 정책을 적용한다.** 회원별·세션별·시간별 중복 제거가 필요하면 Redis 기반 고도화 정책을 별도로 확정한다.
- 조회 수 증가는 원자적 UPDATE로 처리하고 0 미만이 되지 않도록 한다.
- 조회 수 증가에 실패하면 상세 조회 전체를 실패시킬지 별도 비동기 집계로 분리할지는 구현 정책에 따르며, 응답에는 최종 조회된 `viewCount`를 반환한다.

```
UPDATE post
SET view_count = view_count + 1
WHERE id = :postId
  AND status = 'ACTIVE'
  AND deleted_at IS NULL;
```

#### 3. 작성자 정보

- 일반 게시글은 회원 ID, 닉네임, 프로필 이미지, 선택 캐릭터를 반환한다.
- 자동 리포트 게시글은 시스템 작성자로 표시하고 `author.memberId = null`일 수 있다.
- 탈퇴 회원의 글을 유지할 경우 작성자 정보를 비식별화한다.
- 현재 로그인 회원이 작성자인지 `isMine`으로 반환한다.

#### 4. 자동 리포트 게시글

자동 리포트 게시글의 조건은 다음과 같다.

```
isAutoReport = true
boardType = INFORMATION
reportId != null
```

- 사용자가 직접 수정·삭제할 수 없다.
- 연결된 리포트가 `DONE`이고 현재 조회 가능한지 확인한다.
- 완료 리포트면 `reportAvailable = true`와 리포트 정보를 반환한다.
- 연결 리포트가 삭제되었거나 `DONE` 상태가 아니면 `reportAvailable = false`로 반환한다.
- 커뮤니티 자동 글의 리포트 상세 이동과 아파트 상세 리포트 카드의 이동은 모두 `reportId`를 권위 값으로 사용하며, 동일한 `GET /api/v1/reports/{reportId}`를 호출한다.

#### 5. 첨부 파일

- `post_attachment.display_order` 순서로 반환한다.
- 파일이 없으면 빈 배열을 반환한다.
- 파일 접근 URL은 현재 요청 시점에 발급한 Presigned URL 또는 CDN URL이다.
- 파일이 삭제·만료됐으면 해당 항목을 제외하거나 `available = false`로 반환한다.
- 내부 S3 Key는 노출하지 않는다.

#### 6. 좋아요·댓글 정보

- `likeCount`는 현재 게시글의 좋아요 관계 수다.
- `commentCount`는 삭제되지 않은 댓글 수다.
- 현재 로그인 회원의 좋아요 여부를 `likedByMe`로 반환한다.
- 댓글 본문은 별도 댓글 목록 API에서 조회한다.

#### 7. HOT 정보

- 조회 수 증가 후 확정 HOT 점수를 다시 계산할 수 있다.
- 게시글이 작성 후 7일 이내이고 점수 20 이상이면 `isHot = true`다.
- `hotScore`, `hotRank`는 현재 조회 시점의 계산값이며 시간이 지나면 달라질 수 있다.
- 7일이 지난 글은 `isHot = false`, `hotScore`와 `hotRank`를 `null`로 반환할 수 있다.

#### 8. 권한 정보

- 일반 회원 글이며 `isMine = true`, 게시글이 `ACTIVE`인 경우에만 `canEdit`, `canDelete`를 `true`로 반환한다.
- 자동 리포트 게시글은 일반 회원이 수정·삭제할 수 없다.
- 운영 숨김 글은 작성자라도 일반 API로 수정·삭제할 수 없도록 할 수 있다.

---

### Response

#### 200 OK — 일반 게시글

```
{
  "success": true,
  "code": "POST_DETAIL_SUCCESS",
  "message": "게시글 상세 조회에 성공했습니다.",
  "data": {
    "postId": 154,
    "boardType": "INFORMATION",
    "title": "성동구 임장 시 확인하면 좋은 항목을 공유합니다.",
    "content": "교통과 상권뿐 아니라 경사, 보행 환경, 주말 소음도 함께 확인해 보세요.",
    "status": "ACTIVE",
    "originalAvailable": true,
    "author": {
      "memberId": 7,
      "nickname": "집보는다람쥐",
      "profileImageUrl": null,
      "selectedCharacterId": "JIPKONG",
      "authorType": "MEMBER"
    },
    "isAutoReport": false,
    "apartment": {
      "apartmentId": 15,
      "name": "래미안 옥수 리버젠",
      "address": "서울특별시 성동구 매봉길 15"
    },
    "report": null,
    "attachments": [
      {
        "fileId": 401,
        "originalName": "station-route.jpg",
        "contentType": "image/jpeg",
        "fileUrl": "https://s3.example.com/presigned/post-401",
        "displayOrder": 1,
        "available": true,
        "expiresAt": "2026-07-25T16:50:00+09:00"
      }
    ],
    "viewCount": 1,
    "likeCount": 0,
    "commentCount": 0,
    "likedByMe": false,
    "isMine": true,
    "isHot": false,
    "hotScore": 17.7,
    "hotRank": null,
    "permissions": {
      "canEdit": true,
      "canDelete": true,
      "canLike": true,
      "canComment": true
    },
    "createdAt": "2026-07-25T16:30:00+09:00",
    "updatedAt": "2026-07-25T16:30:00+09:00"
  },
  "timestamp": "2026-07-25T16:40:00+09:00"
}
```

#### 200 OK — 자동 리포트 게시글

```
{
  "success": true,
  "code": "POST_DETAIL_SUCCESS",
  "message": "게시글 상세 조회에 성공했습니다.",
  "data": {
    "postId": 153,
    "boardType": "INFORMATION",
    "title": "래미안 옥수 리버젠 임장 리포트가 공개됐어요",
    "content": "옥수역 접근성과 생활 편의시설은 긍정적이며 단지 진입 경사는 추가 확인이 필요합니다.",
    "status": "ACTIVE",
    "originalAvailable": true,
    "author": {
      "memberId": null,
      "nickname": "싸방팔방 리포트",
      "profileImageUrl": null,
      "selectedCharacterId": "PALBANG",
      "authorType": "SYSTEM"
    },
    "isAutoReport": true,
    "apartment": {
      "apartmentId": 15,
      "name": "래미안 옥수 리버젠",
      "address": "서울특별시 성동구 매봉길 15"
    },
    "report": {
      "reportId": 48,
      "status": "DONE",
      "reportAvailable": true
    },
    "attachments": [],
    "viewCount": 83,
    "likeCount": 5,
    "commentCount": 3,
    "likedByMe": true,
    "isMine": false,
    "isHot": true,
    "hotScore": 125.6,
    "hotRank": 2,
    "permissions": {
      "canEdit": false,
      "canDelete": false,
      "canLike": true,
      "canComment": true
    },
    "createdAt": "2026-07-25T12:00:00+09:00",
    "updatedAt": "2026-07-25T12:00:00+09:00"
  },
  "timestamp": "2026-07-25T16:40:00+09:00"
}
```

---

### Response Field

| 필드 | 타입 | 설명 |
| --- | --- | --- |
| `postId` | Long | 게시글 ID |
| `boardType` | String | `INFORMATION`, `FREE` |
| `title` | String | 게시글 제목 |
| `content` | String | 전체 본문 |
| `status` | String | 일반 조회 성공 시 `ACTIVE` |
| `originalAvailable` | Boolean | 원문 표시 가능 여부 |
| `author` | Object | 작성자 정보 |
| `isAutoReport` | Boolean | 자동 리포트 게시글 여부 |
| `apartment` | Object | null | 연결 아파트 |
| `report` | Object | null | 연결 리포트 |
| `attachments` | Array | 첨부 파일 목록 |
| `viewCount` | Long | 이번 조회 반영 후 누적 조회 수 |
| `likeCount` | Long | 좋아요 수 |
| `commentCount` | Long | 삭제되지 않은 댓글 수 |
| `likedByMe` | Boolean | 현재 회원의 좋아요 여부 |
| `isMine` | Boolean | 현재 회원이 작성자인지 여부 |
| `isHot` | Boolean | HOT 여부 |
| `hotScore` | Decimal | null | 현재 HOT 점수 |
| `hotRank` | Long | null | 게시판 내 HOT 순위 |
| `permissions` | Object | 수정·삭제·좋아요·댓글 가능 여부 |
| `createdAt` | String | 작성 시각 |
| `updatedAt` | String | 최종 수정 시각 |

---

### Exception

#### 400 Bad Request — 잘못된 게시글 ID

```
{
  "success": false,
  "code": "COMMON_INVALID_REQUEST",
  "message": "입력값을 확인해 주세요.",
  "data": {
    "field": "postId",
    "reason": "게시글 ID는 1 이상의 숫자여야 합니다."
  },
  "timestamp": "2026-07-25T16:40:00+09:00"
}
```

---

#### 401 Unauthorized

```
{
  "success": false,
  "code": "AUTH_ACCESS_TOKEN_INVALID",
  "message": "로그인이 필요합니다.",
  "data": null,
  "timestamp": "2026-07-25T16:40:00+09:00"
}
```

---

#### 404 Not Found — 게시글 없음·삭제·숨김

```
{
  "success": false,
  "code": "POST_NOT_FOUND",
  "message": "게시글을 찾을 수 없습니다.",
  "data": null,
  "timestamp": "2026-07-25T16:40:00+09:00"
}
```

---

#### 500 Internal Server Error

```
{
  "success": false,
  "code": "COMMON_INTERNAL_SERVER_ERROR",
  "message": "일시적인 오류가 발생했습니다.",
  "data": null,
  "timestamp": "2026-07-25T16:40:00+09:00"
}
```

---

### 프론트 처리

```
게시글 목록에서 항목 선택
→ GET /api/v1/posts/{postId} 호출
→ 본문·작성자·첨부·반응 수 표시

permissions.canEdit = true
→ 수정 메뉴 표시
permissions.canDelete = true
→ 삭제 메뉴 표시

isAutoReport = true && report.reportAvailable = true
→ AI 리포트 보기 버튼 표시
→ reportId로 리포트 상세 이동

likedByMe = true
→ 좋아요 활성 상태 표시

댓글 영역 진입
→ GET /api/v1/posts/{postId}/comments 호출

POST_NOT_FOUND
→ "삭제되었거나 숨김 처리된 게시글입니다." 안내
→ 커뮤니티 목록 또는 내 게시물 화면으로 이동
```

---

## 커뮤니티 게시글 수정

Method: PATCH
Progress: 완료
URI: /api/v1/posts/{postId}
담당자: 김윤석
연동여부: No

로그인한 회원이 자신이 작성한 일반 커뮤니티 게시글의 게시판 유형, 제목, 본문, 연결 아파트와 첨부 파일 구성을 수정한다.

자동 생성된 AI 리포트 게시글과 다른 회원의 게시글은 수정할 수 없다.

---

### Request

- Request HTTP Method: `PATCH`
- 인증 필요: 필요
- Content-Type: `application/json`

#### Request Header

```
Authorization: Bearer {accessToken}
```

#### Path Variable

```
PATCH /api/v1/posts/154
```

| 필드 | 타입 | 필수 | 설명 |
| --- | --- | --- | --- |
| `postId` | Long | Y | 수정할 게시글 ID |

#### Request Body

```
{
  "boardType": "FREE",
  "title": "성동구 임장 후 확인한 항목을 공유합니다.",
  "content": "실제 걸어보니 역 접근성뿐 아니라 경사와 주말 소음도 중요했습니다.",
  "apartmentId": null,
  "fileIds": [401, 405]
}
```

| 필드 | 타입 | 필수 | 설명 |
| --- | --- | --- | --- |
| `boardType` | String | N | 변경할 게시판 유형. `INFORMATION`, `FREE` |
| `title` | String | N | 변경할 제목, 1~200자 |
| `content` | String | N | 변경할 본문, 1~5000자 |
| `apartmentId` | Long | null | N | 연결할 아파트 ID. 명시적 `null`은 연결 해제 |
| `fileIds` | Array<Long> | N | 수정 후 유지할 전체 첨부 파일 ID 목록, 최대 10개 |

---

### 처리 기준

#### 1. 게시글·회원·작성자 확인

- 로그인한 회원 ID는 Access Token에서 확인한다.
- `postId`에 해당하는 게시글이 존재하는지 확인한다.
- 게시글의 `author_id`가 로그인 회원 ID와 일치해야 한다.
- 다른 회원의 게시글은 수정할 수 없다.
- `deletedAt IS NOT NULL`인 글은 수정할 수 없다.
- `status = HIDDEN`인 게시글은 운영 정책상 일반 회원 수정이 제한될 수 있다.

#### 2. 자동 리포트 게시글 수정 제한

- `isAutoReport = true`인 게시글은 일반 회원이 수정할 수 없다.
- 자동 게시글의 내용은 완료 리포트 생성·갱신 정책에서 서버가 관리한다.
- 작성자 필드가 `null`인 시스템 글에 대해 일반 수정 권한을 부여하지 않는다.

#### 3. 부분 수정 규칙

- 요청 Body에 포함된 필드만 수정한다.
- 전달되지 않은 필드는 기존 값을 유지한다.
- `title`, `content`는 전달된 경우 앞뒤 공백을 제거한다.
- 빈 문자열 또는 공백만 있는 제목·본문으로 수정할 수 없다.
- 수정할 필드가 하나도 없으면 `POST_UPDATE_EMPTY` 오류를 반환한다.
- `createdAt`은 유지하고 `updatedAt`만 현재 서버 시각으로 변경한다.
- 수정으로 게시글이 최신 목록 상단에 다시 정렬되지 않는다.

#### 4. null과 미전달 구분

- `apartmentId` 미전달: 기존 아파트 연결 유지
- `apartmentId = null`: 아파트 연결 제거
- `fileIds` 미전달: 기존 첨부 구성 유지
- `fileIds = []`: 모든 첨부 연결 제거

서버 DTO는 미전달과 명시적 null을 구분할 수 있어야 한다.

#### 5. 게시판 유형 변경

- `INFORMATION`, `FREE` 사이 변경을 허용한다.
- 변경 후에도 게시글 ID, 작성자, 댓글, 좋아요, 조회 수는 유지한다.
- 사용자 일반 글의 `isAutoReport = false`, `reportId = null`은 변경하지 않는다.
- 지원하지 않는 `INFO` 등 구 enum은 허용하지 않는다.

#### 6. 아파트 연결 수정

- 새 `apartmentId`가 전달되면 실제 존재하는지 확인한다.
- 명시적 `null`이면 기존 `apartment_id`를 제거한다.
- 연결 변경은 아파트 데이터 자체에 영향을 주지 않는다.

#### 7. 첨부 파일 전체 교체

- `fileIds`가 전달되면 수정 후 최종 첨부 목록으로 해석한다.
- 유지할 기존 파일도 배열에 다시 포함해야 한다.
- 배열에서 제외된 기존 파일은 `post_attachment` 연결을 제거한다.
- 새 파일은 본인 소유, `POST_ATTACHMENT`, 업로드 완료, 미삭제 상태여야 한다.
- 다른 게시글에 연결된 파일은 사용할 수 없다.
- 배열 순서대로 `display_order`를 다시 부여한다.
- 첨부 파일 변경 전체를 게시글 수정과 하나의 트랜잭션으로 처리한다.

예시:

```
기존: [401, 402]
요청: [401, 405]
결과: 401 유지, 402 연결 해제, 405 신규 연결
```

#### 8. 반응·HOT 정보

- 수정으로 좋아요 수, 댓글 수, 조회 수는 변경하지 않는다.
- HOT 점수는 조회·좋아요·댓글·작성 경과시간으로 계산하므로 본문 수정 자체로 직접 변경되지 않는다.
- 게시판 유형을 변경하면 HOT 순위의 게시판 파티션이 달라질 수 있다.

---

### Response

#### 200 OK

```
{
  "success": true,
  "code": "POST_UPDATE_SUCCESS",
  "message": "게시글이 수정되었습니다.",
  "data": {
    "postId": 154,
    "boardType": "FREE",
    "title": "성동구 임장 후 확인한 항목을 공유합니다.",
    "content": "실제 걸어보니 역 접근성뿐 아니라 경사와 주말 소음도 중요했습니다.",
    "status": "ACTIVE",
    "author": {
      "memberId": 7,
      "nickname": "집보는다람쥐",
      "profileImageUrl": null,
      "selectedCharacterId": "JIPKONG",
      "authorType": "MEMBER"
    },
    "isAutoReport": false,
    "apartment": null,
    "report": null,
    "attachments": [
      {
        "fileId": 401,
        "originalName": "station-route.jpg",
        "contentType": "image/jpeg",
        "fileUrl": "https://s3.example.com/presigned/post-401",
        "displayOrder": 1
      },
      {
        "fileId": 405,
        "originalName": "weekend-noise.jpg",
        "contentType": "image/jpeg",
        "fileUrl": "https://s3.example.com/presigned/post-405",
        "displayOrder": 2
      }
    ],
    "viewCount": 12,
    "likeCount": 2,
    "commentCount": 1,
    "likedByMe": false,
    "isMine": true,
    "isHot": true,
    "createdAt": "2026-07-25T16:30:00+09:00",
    "updatedAt": "2026-07-25T16:50:00+09:00"
  },
  "timestamp": "2026-07-25T16:50:00+09:00"
}
```

---

### Response Field

정상 응답은 수정 완료된 게시글의 상세 요약을 반환하며 게시글 상세 조회 API의 핵심 필드와 동일하게 구성한다.

| 필드 | 타입 | 설명 |
| --- | --- | --- |
| `postId` | Long | 수정된 게시글 ID |
| `boardType` | String | 수정 후 게시판 유형 |
| `title` | String | 수정 후 제목 |
| `content` | String | 수정 후 본문 |
| `status` | String | 게시글 상태 |
| `author` | Object | 작성자 정보 |
| `isAutoReport` | Boolean | 일반 회원 글은 `false` |
| `apartment` | Object | null | 수정 후 아파트 연결 |
| `attachments` | Array | 수정 후 전체 첨부 목록 |
| `viewCount` | Long | 기존 조회 수 유지 |
| `likeCount` | Long | 기존 좋아요 수 유지 |
| `commentCount` | Long | 기존 댓글 수 유지 |
| `updatedAt` | String | 수정 처리 시각 |

---

### Exception

#### 400 Bad Request — 입력값 오류

```
{
  "success": false,
  "code": "COMMON_INVALID_REQUEST",
  "message": "입력값을 확인해 주세요.",
  "data": {
    "field": "title",
    "reason": "제목은 1자 이상 200자 이하이어야 합니다."
  },
  "timestamp": "2026-07-25T16:50:00+09:00"
}
```

---

#### 400 Bad Request — 수정할 필드 없음

```
{
  "success": false,
  "code": "POST_UPDATE_EMPTY",
  "message": "수정할 내용을 입력해 주세요.",
  "data": null,
  "timestamp": "2026-07-25T16:50:00+09:00"
}
```

---

#### 400 Bad Request — 첨부 개수 초과

```
{
  "success": false,
  "code": "POST_ATTACHMENT_LIMIT_EXCEEDED",
  "message": "첨부 파일은 최대 10개까지 등록할 수 있습니다.",
  "data": null,
  "timestamp": "2026-07-25T16:50:00+09:00"
}
```

---

#### 401 Unauthorized

```
{
  "success": false,
  "code": "AUTH_ACCESS_TOKEN_INVALID",
  "message": "로그인이 필요합니다.",
  "data": null,
  "timestamp": "2026-07-25T16:50:00+09:00"
}
```

---

#### 403 Forbidden — 작성자 아님

```
{
  "success": false,
  "code": "POST_UPDATE_FORBIDDEN",
  "message": "게시글을 수정할 권한이 없습니다.",
  "data": null,
  "timestamp": "2026-07-25T16:50:00+09:00"
}
```

---

#### 403 Forbidden — 자동 리포트 게시글

```
{
  "success": false,
  "code": "POST_AUTO_REPORT_UPDATE_FORBIDDEN",
  "message": "자동 생성된 리포트 게시글은 수정할 수 없습니다.",
  "data": null,
  "timestamp": "2026-07-25T16:50:00+09:00"
}
```

---

#### 404 Not Found — 게시글 없음

```
{
  "success": false,
  "code": "POST_NOT_FOUND",
  "message": "게시글을 찾을 수 없습니다.",
  "data": null,
  "timestamp": "2026-07-25T16:50:00+09:00"
}
```

---

#### 404 Not Found — 아파트·파일 없음

```
{
  "success": false,
  "code": "MEDIA_FILE_NOT_FOUND",
  "message": "첨부 파일을 찾을 수 없습니다.",
  "data": {
    "fileId": 405
  },
  "timestamp": "2026-07-25T16:50:00+09:00"
}
```

---

#### 409 Conflict — 숨김 상태

```
{
  "success": false,
  "code": "POST_STATUS_CONFLICT",
  "message": "현재 상태에서는 게시글을 수정할 수 없습니다.",
  "data": {
    "status": "HIDDEN"
  },
  "timestamp": "2026-07-25T16:50:00+09:00"
}
```

---

#### 500 Internal Server Error

```
{
  "success": false,
  "code": "COMMON_INTERNAL_SERVER_ERROR",
  "message": "일시적인 오류가 발생했습니다.",
  "data": null,
  "timestamp": "2026-07-25T16:50:00+09:00"
}
```

---

### 프론트 처리

```
게시글 상세에서 수정 선택
→ 기존 boardType, 제목, 본문, 아파트, 첨부 목록을 수정 화면에 표시
→ 변경 후 PATCH /api/v1/posts/{postId} 호출

첨부 수정
→ 유지할 기존 fileId와 새로 업로드한 fileId를 모두 전달
→ 제거할 파일은 fileIds에서 제외

아파트 연결 제거
→ apartmentId를 명시적으로 null로 전달

수정 성공
→ 응답 데이터로 상세 화면 갱신
→ 게시판이 변경됐으면 기존 목록에서 제거 후 새 탭 갱신

POST_UPDATE_FORBIDDEN 또는 자동 리포트 수정 제한
→ 수정 화면 종료
→ 현재 게시글 다시 조회
```

---

## 커뮤니티 게시글 삭제

Method: DELETE
Progress: 완료
URI: /api/v1/posts/{postId}
담당자: 김윤석
연동여부: No

로그인한 회원이 자신이 작성한 일반 커뮤니티 게시글을 삭제한다.

게시글은 `deletedAt`을 기록하는 Soft Delete 방식으로 처리하며, 댓글·좋아요·조회 통계는 운영과 데이터 정합성을 위해 유지한다. 자동 생성된 리포트 게시글은 일반 회원이 직접 삭제할 수 없다.

---

### Request

- Request HTTP Method: `DELETE`
- 인증 필요: 필요
- Request Body: 없음

#### Request Header

```
Authorization: Bearer {accessToken}
```

#### Path Variable

```
DELETE /api/v1/posts/154
```

| 필드 | 타입 | 필수 | 설명 |
| --- | --- | --- | --- |
| `postId` | Long | Y | 삭제할 게시글 ID |

---

### 처리 기준

#### 1. 회원·게시글 확인

- 로그인한 회원 ID는 Access Token에서 확인한다.
- `postId`로 게시글을 비관적 쓰기 잠금 조회한다.
- 존재하지 않는 게시글은 `POST_NOT_FOUND`로 처리한다.
- `deletedAt IS NOT NULL`인 게시글은 이미 삭제된 글로 처리한다.
- 탈퇴 회원이 아닌 `ACTIVE` 회원만 삭제 요청을 수행할 수 있다.

#### 2. 작성자 권한

- 일반 회원은 자신의 게시글만 삭제할 수 있다.
- 서버는 Access Token의 회원 ID와 `post.author_id`를 비교한다.
- 클라이언트가 작성자 ID를 요청에 전달하지 않는다.
- 작성자가 아닌 회원은 `POST_DELETE_FORBIDDEN` 오류를 반환한다.

#### 3. 자동 리포트 게시글 삭제 제한

자동 리포트 게시글 조건:

```
isAutoReport = true
reportId != null
boardType = INFORMATION
```

- 자동 리포트 게시글은 일반 회원이 삭제할 수 없다.
- 완료 리포트와 커뮤니티 게시글의 상태 정합성을 위해 리포트 공개 정책 또는 운영자 처리로만 숨김·삭제한다.
- 시스템 작성자 글에 일반 회원 삭제 권한을 부여하지 않는다.

#### 4. Soft Delete 방식

최종 ERD v7의 게시글 상태는 다음과 같이 사용한다.

```
status = ACTIVE | HIDDEN
deletedAt = 작성자 삭제 여부
```

작성자 삭제 시:

```
deletedAt = 현재 서버 시각
updatedAt = 현재 서버 시각
status는 ACTIVE를 유지할 수 있음
```

- `DELETED`를 `status` enum 값으로 저장하지 않는다.
- 운영자가 숨김 처리하는 경우 `status = HIDDEN`을 사용한다.
- 작성자 삭제와 운영 숨김을 서로 다른 컬럼으로 구분한다.

#### 5. 댓글·좋아요·첨부 처리

Soft Delete 시 다음 데이터를 즉시 물리 삭제하지 않는다.

- `post_comment`
- `post_like`
- `post_attachment`
- 첨부 `file_meta`

이유:

- 운영·신고 대응과 통계 정합성
- 삭제 전 활동 이력 보존
- 리포트 자동 게시글과의 연결 검증

단, 삭제된 게시글의 일반 목록·상세·댓글·좋아요 API 접근은 제한한다.

첨부 파일 접근 URL도 일반 사용자에게 더 이상 발급하지 않는다.

#### 6. 아파트·리포트 연결

- 일반 게시글 삭제는 연결된 아파트 데이터에 영향을 주지 않는다.
- 일반 회원 글은 `reportId = null`이므로 리포트 상태를 변경하지 않는다.
- 자동 리포트 글은 이 API로 삭제할 수 없으므로 리포트 연결 정합성이 유지된다.

#### 7. HOT·검색·내 게시물 반영

- `deletedAt IS NOT NULL`인 게시글은 HOT VIEW 대상과 최신·검색 목록에서 제외한다.
- 내 게시물 화면에서는 요구사항에 따라 삭제된 항목의 상태만 표시할 수 있다.
- 내 게시물에서 원문 이동을 시도하면 `originalAvailable = false`로 처리한다.
- 삭제 게시글의 기존 댓글에서 원문으로 이동하는 경우 "삭제되었거나 확인할 수 없는 게시글" 안내를 표시한다.

#### 8. 중복 요청

- 현재 명세에서는 첫 삭제 성공 후 동일 게시글 재삭제 요청을 `404 Not Found`로 처리한다.
- 잠금 조회 뒤 삭제 상태를 다시 검증해 동시 요청을 안전하게 처리한다.
- 엄격한 동일 성공 응답 멱등성이 필요하면 이미 삭제된 글에도 200을 반환할 수 있으나, 현재 원문 접근 상태를 명확히 구분하기 위해 `POST_NOT_FOUND` 정책을 유지한다.

#### 9. 트랜잭션

다음 작업을 하나의 트랜잭션으로 처리한다.

```
게시글 조회
→ 삭제 여부 확인
→ 자동 리포트 여부 확인
→ 작성자 확인
→ 운영 숨김 여부 확인
→ deletedAt, updatedAt 갱신
→ 커밋
```

권한 또는 상태 검증에 실패하면 게시글을 변경하지 않는다.

---

### Response

#### 200 OK

```
{
  "success": true,
  "code": "POST_DELETE_SUCCESS",
  "message": "게시글이 삭제되었습니다.",
  "data": {
    "postId": 154,
    "deletedAt": "2026-07-25T17:00:00+09:00"
  },
  "timestamp": "2026-07-25T17:00:00+09:00"
}
```

---

### Response Field

| 필드 | 타입 | 설명 |
| --- | --- | --- |
| `postId` | Long | 삭제된 게시글 ID |
| `deletedAt` | String | Soft Delete 처리 시각 |

---

### Exception

#### 400 Bad Request — 게시글 ID 오류

```
{
  "success": false,
  "code": "COMMON_INVALID_REQUEST",
  "message": "입력값을 확인해 주세요.",
  "data": {
    "field": "postId",
    "reason": "게시글 ID는 1 이상의 숫자여야 합니다."
  },
  "timestamp": "2026-07-25T17:00:00+09:00"
}
```

---

#### 401 Unauthorized

```
{
  "success": false,
  "code": "AUTH_ACCESS_TOKEN_INVALID",
  "message": "로그인이 필요합니다.",
  "data": null,
  "timestamp": "2026-07-25T17:00:00+09:00"
}
```

---

#### 403 Forbidden — 작성자 아님

```
{
  "success": false,
  "code": "POST_DELETE_FORBIDDEN",
  "message": "게시글을 삭제할 권한이 없습니다.",
  "data": null,
  "timestamp": "2026-07-25T17:00:00+09:00"
}
```

---

#### 403 Forbidden — 자동 리포트 게시글

```
{
  "success": false,
  "code": "POST_AUTO_REPORT_DELETE_FORBIDDEN",
  "message": "자동 생성된 리포트 게시글은 직접 삭제할 수 없습니다.",
  "data": null,
  "timestamp": "2026-07-25T17:00:00+09:00"
}
```

---

#### 404 Not Found — 게시글 없음 또는 이미 삭제

```
{
  "success": false,
  "code": "POST_NOT_FOUND",
  "message": "게시글을 찾을 수 없습니다.",
  "data": null,
  "timestamp": "2026-07-25T17:00:00+09:00"
}
```

---

#### 409 Conflict — 운영 숨김 상태

```
{
  "success": false,
  "code": "POST_STATUS_CONFLICT",
  "message": "현재 상태에서는 게시글을 삭제할 수 없습니다.",
  "data": {
    "status": "HIDDEN"
  },
  "timestamp": "2026-07-25T17:00:00+09:00"
}
```

---

#### 500 Internal Server Error

```
{
  "success": false,
  "code": "COMMON_INTERNAL_SERVER_ERROR",
  "message": "일시적인 오류가 발생했습니다.",
  "data": null,
  "timestamp": "2026-07-25T17:00:00+09:00"
}
```

---

### 프론트 처리

```
게시글 상세에서 permissions.canDelete = true
→ 삭제 메뉴 표시
→ 선택 시 확인 모달 표시
→ DELETE /api/v1/posts/{postId} 호출

삭제 성공
→ 상세 화면 종료
→ 현재 커뮤니티 목록에서 해당 글 제거
→ 내 게시물 목록을 다시 조회하거나 삭제 상태로 갱신

POST_AUTO_REPORT_DELETE_FORBIDDEN
→ 삭제 메뉴 숨김
→ 리포트 공개 정책으로 관리되는 글임을 안내

POST_NOT_FOUND
→ "이미 삭제되었거나 존재하지 않는 게시글입니다." 안내
→ 목록으로 이동

게시글 삭제 후 기존 댓글 알림·내 댓글에서 원문 이동
→ 원문 접근 불가 안내
→ 본문 표시 금지
```

---

## 댓글 작성

Method: POST
Progress: 완료
URI: /api/v1/posts/{postId}/comments
담당자: 김윤석
연동여부: No

로그인한 회원이 활성 상태의 커뮤니티 게시글에 평면 댓글을 작성한다.

최종 ERD v7에서는 댓글 계층을 제공하지 않으므로 요청에는 댓글 본문만 전달한다.

---

### Request

- Request HTTP Method: `POST`
- 인증 필요: 필요
- Content-Type: `application/json`

#### Request Header

```
Authorization: Bearer {accessToken}
```

#### Path Variable

```
POST /api/v1/posts/154/comments
```

| 필드 | 타입 | 필수 | 설명 |
| --- | --- | --- | --- |
| `postId` | Long | Y | 댓글을 작성할 게시글 ID |

#### Request Body

```
{
  "content": "역 입구뿐 아니라 개찰구까지 걸리는 시간도 확인하면 좋습니다."
}
```

| 필드 | 타입 | 필수 | 설명 |
| --- | --- | --- | --- |
| `content` | String | Y | 댓글 내용, 앞뒤 공백 제거 후 1~1000자 |

---

### 처리 기준

#### 1. 회원·게시글 확인

- 로그인한 회원 ID는 Access Token에서 확인한다.
- 회원 상태가 `ACTIVE`인 경우에만 댓글을 작성할 수 있다.
- `postId`에 해당하는 게시글이 존재하는지 확인한다.
- 게시글이 `status = ACTIVE`, `deletedAt IS NULL`인 경우에만 댓글을 작성할 수 있다.
- 운영 숨김 또는 작성자 삭제 게시글에는 댓글을 추가할 수 없다.
- 자동 리포트 게시글에도 활성 상태이고 접근 가능하면 댓글을 작성할 수 있다.

#### 2. 댓글 본문 검증

- 본문의 앞뒤 공백을 제거한다.
- 공백만 있는 댓글은 저장할 수 없다.
- 최대 1000자를 허용한다.
- 스크립트·HTML은 일반 텍스트로 이스케이프해 실행되지 않도록 한다.
- 작성자 ID는 Request Body로 받지 않고 Access Token에서 결정한다.

#### 3. 평면 구조

- 댓글은 `post_comment`에 게시글 ID, 작성자 ID, 본문을 저장한다.
- 댓글 간 상하 관계를 저장하지 않는다.
- 특정 사용자에게 답변하는 UI를 제공하더라도 일반 댓글 본문으로 저장한다.
- 별도 답글 깊이 검증이나 부모 댓글 검증을 수행하지 않는다.

#### 4. 저장 값

```
postId = Path Variable
authorId = 로그인 회원 ID
content = 정제된 본문
deletedAt = null
createdAt = 서버 시각
updatedAt = 서버 시각
```

- 댓글 작성과 게시글 조회를 하나의 트랜잭션에서 검증한다.
- 별도 댓글 수 카운터 컬럼이 없으므로 응답 시 현재 삭제되지 않은 댓글 수를 집계한다.

#### 5. 댓글 수와 HOT 점수

- 댓글 작성 성공 후 `commentCount`가 1 증가한다.
- HOT 점수는 댓글 수 × 3을 포함하므로 작성 직후 현재 `isHot`, `hotScore`가 달라질 수 있다.
- 삭제 댓글은 HOT 점수 댓글 수에서 제외한다.
- 댓글 작성만으로 게시글 조회 수 또는 좋아요 수는 변경되지 않는다.

#### 6. 작성자 표시

- 로그인 회원이 게시글 작성자이면 `isPostAuthor = true`로 반환한다.
- 방금 작성한 댓글은 `isMine = true`, `canEdit = true`, `canDelete = true`다.

#### 7. 알림

- 최종 확정 알림 유형에는 댓글 알림이 별도로 정의되어 있지 않으므로 댓글 작성만으로 필수 알림·FCM을 생성하지 않는다.
- 추후 커뮤니티 알림을 추가하려면 `notification.category = COMMUNITY`와 별도 type을 요구사항·ERD enum 정책에 먼저 추가한다.

---

### Response

#### 201 Created

```
{
  "success": true,
  "code": "COMMENT_CREATE_SUCCESS",
  "message": "댓글이 작성되었습니다.",
  "data": {
    "comment": {
      "commentId": 36,
      "postId": 154,
      "content": "역 입구뿐 아니라 개찰구까지 걸리는 시간도 확인하면 좋습니다.",
      "author": {
        "memberId": 12,
        "nickname": "옥수탐방러",
        "profileImageUrl": null,
        "selectedCharacterId": "DURI"
      },
      "isMine": true,
      "isPostAuthor": false,
      "canEdit": true,
      "canDelete": true,
      "createdAt": "2026-07-25T17:15:00+09:00",
      "updatedAt": "2026-07-25T17:15:00+09:00"
    },
    "postMetrics": {
      "commentCount": 1,
      "likeCount": 0,
      "viewCount": 1,
      "isHot": true,
      "hotScore": 20.7
    }
  },
  "timestamp": "2026-07-25T17:15:00+09:00"
}
```

---

### Response Field

#### `comment`

| 필드 | 타입 | 설명 |
| --- | --- | --- |
| `commentId` | Long | 생성된 댓글 ID |
| `postId` | Long | 댓글이 작성된 게시글 ID |
| `content` | String | 정제 후 저장된 댓글 본문 |
| `author` | Object | 작성자 정보 |
| `isMine` | Boolean | 항상 `true` |
| `isPostAuthor` | Boolean | 게시글 작성자 여부 |
| `canEdit` | Boolean | 작성 직후 `true` |
| `canDelete` | Boolean | 작성 직후 `true` |
| `createdAt` | String | 생성 시각 |
| `updatedAt` | String | 최종 수정 시각 |

#### `postMetrics`

| 필드 | 타입 | 설명 |
| --- | --- | --- |
| `commentCount` | Long | 작성 후 삭제되지 않은 댓글 수 |
| `likeCount` | Long | 현재 좋아요 수 |
| `viewCount` | Long | 현재 조회 수 |
| `isHot` | Boolean | 작성 후 HOT 여부 |
| `hotScore` | Decimal | null | 현재 HOT 점수 |

---

### Exception

#### 400 Bad Request — 댓글 내용 오류

```
{
  "success": false,
  "code": "COMMON_INVALID_REQUEST",
  "message": "입력값을 확인해 주세요.",
  "data": {
    "field": "content",
    "reason": "댓글은 1자 이상 1000자 이하이어야 합니다."
  },
  "timestamp": "2026-07-25T17:15:00+09:00"
}
```

---

#### 401 Unauthorized

```
{
  "success": false,
  "code": "AUTH_ACCESS_TOKEN_INVALID",
  "message": "로그인이 필요합니다.",
  "data": null,
  "timestamp": "2026-07-25T17:15:00+09:00"
}
```

---

#### 404 Not Found — 게시글 없음·삭제·숨김

```
{
  "success": false,
  "code": "POST_NOT_FOUND",
  "message": "게시글을 찾을 수 없습니다.",
  "data": null,
  "timestamp": "2026-07-25T17:15:00+09:00"
}
```

---

#### 500 Internal Server Error

```
{
  "success": false,
  "code": "COMMON_INTERNAL_SERVER_ERROR",
  "message": "일시적인 오류가 발생했습니다.",
  "data": null,
  "timestamp": "2026-07-25T17:15:00+09:00"
}
```

---

### 프론트 처리

```
댓글 입력 후 등록
→ POST /api/v1/posts/{postId}/comments 호출
→ 성공한 comment를 현재 목록 마지막에 추가
→ 입력창 초기화

postMetrics.commentCount로 게시글 댓글 수 갱신
→ isHot과 hotScore가 바뀌면 HOT 배지 상태 동기화

isPostAuthor = true
→ 작성자 배지 표시

POST_NOT_FOUND
→ 작성 중 내용 유지하지 않고 원문 접근 불가 안내
→ 게시글 상세 종료
```

---

## 댓글 수정

Method: PATCH
Progress: 완료
URI: /api/v1/comments/{commentId}
담당자: 김윤석
연동여부: No

로그인한 회원이 자신이 작성한 삭제되지 않은 댓글의 본문을 수정한다.

댓글이 속한 게시글, 작성자와 댓글 구조는 변경하지 않으며, 다른 회원의 댓글과 삭제된 댓글은 수정할 수 없다.

---

### Request

- Request HTTP Method: `PATCH`
- 인증 필요: 필요
- Content-Type: `application/json`

#### Request Header

```
Authorization: Bearer {accessToken}
```

#### Path Variable

```
PATCH /api/v1/comments/36
```

| 필드 | 타입 | 필수 | 설명 |
| --- | --- | --- | --- |
| `commentId` | Long | Y | 수정할 댓글 ID |

#### Request Body

```
{
  "content": "역 입구뿐 아니라 개찰구까지 걸리는 실제 시간도 확인하면 좋습니다."
}
```

| 필드 | 타입 | 필수 | 설명 |
| --- | --- | --- | --- |
| `content` | String | Y | 수정할 댓글 내용, 앞뒤 공백 제거 후 1~1000자 |

---

### 처리 기준

#### 1. 회원·댓글·게시글 확인

- 로그인한 회원 ID는 Access Token에서 확인한다.
- `commentId`에 해당하는 댓글이 존재하는지 확인한다.
- 댓글의 `deletedAt IS NULL`인지 확인한다.
- 댓글이 속한 게시글이 `ACTIVE`이고 삭제되지 않았는지 확인한다.
- 원문 게시글이 삭제 또는 운영 숨김 상태면 댓글도 수정할 수 없다.

#### 2. 작성자 권한

- 댓글의 `author_id`가 로그인 회원 ID와 일치해야 한다.
- 게시글 작성자라도 다른 회원 댓글은 수정할 수 없다.
- 관리자 수정 기능은 현재 MVP 범위에 포함하지 않는다.
- 다른 회원이 요청하면 `COMMENT_UPDATE_FORBIDDEN`을 반환한다.

#### 3. 본문 검증

- 본문의 앞뒤 공백을 제거한다.
- 공백만 있는 댓글로 수정할 수 없다.
- 최대 1000자를 허용한다.
- 수정 시 스크립트가 실행되지 않도록 출력 이스케이프 정책을 유지한다.

#### 4. 수정 범위

수정 가능한 값:

```
content
updatedAt
```

변경하지 않는 값:

```
commentId
postId
authorId
createdAt
```

- 댓글은 평면 구조이므로 계층·부모 관계를 변경하는 기능이 없다.
- 댓글 수정으로 게시글 `commentCount`는 변경되지 않는다.
- 댓글 수가 같으므로 HOT 점수도 댓글 수정만으로 변경되지 않는다.

#### 5. 동시 수정

- 단순 MVP에서는 마지막 정상 요청이 저장되는 Last Write Wins 정책을 사용할 수 있다.
- 엄격한 충돌 방지가 필요하면 `updatedAt` 또는 버전 필드를 조건으로 추가해야 하나, 최종 ERD에는 버전 컬럼이 없다.
- 삭제와 수정이 동시에 발생하면 `deletedAt IS NULL` 조건부 UPDATE를 이용해 삭제된 댓글이 다시 살아나지 않도록 한다.

---

### Response

#### 200 OK

```
{
  "success": true,
  "code": "COMMENT_UPDATE_SUCCESS",
  "message": "댓글이 수정되었습니다.",
  "data": {
    "commentId": 36,
    "postId": 154,
    "content": "역 입구뿐 아니라 개찰구까지 걸리는 실제 시간도 확인하면 좋습니다.",
    "author": {
      "memberId": 12,
      "nickname": "옥수탐방러",
      "profileImageUrl": null,
      "selectedCharacterId": "DURI"
    },
    "isMine": true,
    "isPostAuthor": false,
    "canEdit": true,
    "canDelete": true,
    "createdAt": "2026-07-25T17:15:00+09:00",
    "updatedAt": "2026-07-25T17:20:00+09:00"
  },
  "timestamp": "2026-07-25T17:20:00+09:00"
}
```

---

### Response Field

| 필드 | 타입 | 설명 |
| --- | --- | --- |
| `commentId` | Long | 수정된 댓글 ID |
| `postId` | Long | 댓글이 속한 게시글 ID |
| `content` | String | 수정 후 본문 |
| `author` | Object | 작성자 정보 |
| `isMine` | Boolean | 현재 요청자 작성 여부, 성공 응답은 `true` |
| `isPostAuthor` | Boolean | 게시글 작성자 여부 |
| `canEdit` | Boolean | 현재 수정 가능 여부 |
| `canDelete` | Boolean | 현재 삭제 가능 여부 |
| `createdAt` | String | 최초 작성 시각 |
| `updatedAt` | String | 수정 처리 시각 |

---

### Exception

#### 400 Bad Request — 댓글 내용 오류

```
{
  "success": false,
  "code": "COMMON_INVALID_REQUEST",
  "message": "입력값을 확인해 주세요.",
  "data": {
    "field": "content",
    "reason": "댓글은 1자 이상 1000자 이하이어야 합니다."
  },
  "timestamp": "2026-07-25T17:20:00+09:00"
}
```

---

#### 401 Unauthorized

```
{
  "success": false,
  "code": "AUTH_ACCESS_TOKEN_INVALID",
  "message": "로그인이 필요합니다.",
  "data": null,
  "timestamp": "2026-07-25T17:20:00+09:00"
}
```

---

#### 404 Not Found — 회원 없음·비활성·탈퇴

```
{
  "success": false,
  "code": "MEMBER_NOT_FOUND",
  "message": "회원을 찾을 수 없습니다.",
  "data": null,
  "timestamp": "2026-07-25T17:20:00+09:00"
}
```

---

#### 403 Forbidden — 작성자 아님

```
{
  "success": false,
  "code": "COMMENT_UPDATE_FORBIDDEN",
  "message": "댓글을 수정할 권한이 없습니다.",
  "data": null,
  "timestamp": "2026-07-25T17:20:00+09:00"
}
```

---

#### 404 Not Found — 댓글 없음

```
{
  "success": false,
  "code": "COMMENT_NOT_FOUND",
  "message": "댓글을 찾을 수 없습니다.",
  "data": null,
  "timestamp": "2026-07-25T17:20:00+09:00"
}
```

---

#### 404 Not Found — 게시글 없음·삭제·숨김

```
{
  "success": false,
  "code": "POST_NOT_FOUND",
  "message": "게시글을 찾을 수 없습니다.",
  "data": null,
  "timestamp": "2026-07-25T17:20:00+09:00"
}
```

---

#### 409 Conflict — 이미 삭제된 댓글

```
{
  "success": false,
  "code": "COMMENT_ALREADY_DELETED",
  "message": "삭제된 댓글은 수정할 수 없습니다.",
  "data": null,
  "timestamp": "2026-07-25T17:20:00+09:00"
}
```

---

#### 500 Internal Server Error

```
{
  "success": false,
  "code": "COMMON_INTERNAL_SERVER_ERROR",
  "message": "일시적인 오류가 발생했습니다.",
  "data": null,
  "timestamp": "2026-07-25T17:20:00+09:00"
}
```

---

### 프론트 처리

```
댓글 수정 선택
→ 기존 본문을 입력창에 표시
→ 완료 시 PATCH /api/v1/comments/{commentId} 호출

수정 성공
→ 동일 commentId 항목의 content와 updatedAt 갱신
→ 입력 상태 종료

COMMENT_ALREADY_DELETED
→ 해당 댓글을 현재 목록에서 제거
→ commentCount 재조회 또는 1 감소 처리

POST_NOT_FOUND
→ 원문 게시글 접근 불가 안내
→ 상세 화면 종료
```

---

## 댓글 삭제

Method: DELETE
Progress: 완료
URI: /api/v1/comments/{commentId}
담당자: 김윤석
연동여부: No

로그인한 회원이 자신이 작성한 댓글을 Soft Delete한다.

최종 댓글은 평면 구조이므로 삭제된 댓글을 목록에 placeholder로 남기지 않고 일반 댓글 목록과 댓글 수에서 제외한다.

---

### Request

- Request HTTP Method: `DELETE`
- 인증 필요: 필요
- Request Body: 없음

#### Request Header

```
Authorization: Bearer {accessToken}
```

#### Path Variable

```
DELETE /api/v1/comments/36
```

| 필드 | 타입 | 필수 | 설명 |
| --- | --- | --- | --- |
| `commentId` | Long | Y | 삭제할 댓글 ID |

---

### 처리 기준

#### 1. 회원·댓글 확인

- 로그인한 회원 ID는 Access Token에서 확인한다.
- `commentId`에 해당하는 댓글이 존재하는지 확인한다.
- 댓글의 `deletedAt` 상태를 확인한다.
- 존재하지 않는 댓글은 `COMMENT_NOT_FOUND`로 처리한다.
- 이미 삭제된 댓글은 `COMMENT_ALREADY_DELETED`로 처리한다.

#### 2. 작성자 권한

- 댓글의 `author_id`가 로그인 회원 ID와 일치해야 한다.
- 게시글 작성자는 다른 회원의 댓글을 일반 API로 삭제할 수 없다.
- 다른 회원이 요청하면 `COMMENT_DELETE_FORBIDDEN`을 반환한다.

#### 3. 원문 게시글 확인

- 댓글이 속한 게시글을 조회한다.
- 게시글이 삭제 또는 운영 숨김 상태여도 본인이 남긴 댓글 정리를 허용할지 정책이 필요하다.
- 본 명세에서는 데이터 자기 통제를 위해 댓글 자체가 조회 가능하면 본인 삭제를 허용하고, 응답의 `postAvailable`로 원문 접근 가능 여부를 반환한다.
- 일반 댓글 화면에서는 활성 게시글에서만 삭제 버튼을 노출한다.

#### 4. Soft Delete

- 댓글 행을 물리 삭제하지 않고 `deletedAt`과 `updatedAt`을 현재 서버 시각으로 변경한다.
- 댓글 본문과 작성자 관계는 운영·통계 대응을 위해 DB에 유지한다.
- 일반 댓글 목록에서는 `deletedAt IS NOT NULL`인 댓글을 제외한다.
- 댓글 계층이 없으므로 삭제된 위치를 placeholder로 유지하지 않는다.

```
UPDATE post_comment
SET deleted_at = NOW(),
    updated_at = NOW()
WHERE id = :commentId
  AND author_id = :memberId
  AND deleted_at IS NULL;
```

#### 5. 댓글 수와 HOT 점수

- 삭제 후 게시글의 삭제되지 않은 댓글 수를 다시 집계한다.
- `commentCount`가 1 감소한다.
- HOT 점수에는 댓글 수 × 3이 반영되므로 `isHot`, `hotScore`, `hotRank`가 변경될 수 있다.
- 댓글 수는 0 미만이 될 수 없다.

#### 6. 중복 요청

- 첫 삭제 요청은 성공한다.
- 이미 삭제된 댓글에 대한 재요청은 `409 COMMENT_ALREADY_DELETED`를 반환한다.
- DB UPDATE는 `deletedAt IS NULL` 조건을 사용해 동시 요청에서도 한 번만 상태가 변경되도록 한다.
- 프론트는 이미 삭제 응답을 받아도 화면을 삭제 상태로 동기화한다.

---

### Response

#### 200 OK

```
{
  "success": true,
  "code": "COMMENT_DELETE_SUCCESS",
  "message": "댓글이 삭제되었습니다.",
  "data": {
    "commentId": 36,
    "postId": 154,
    "deletedAt": "2026-07-25T17:25:00+09:00",
    "postAvailable": true,
    "postMetrics": {
      "commentCount": 0,
      "likeCount": 0,
      "viewCount": 1,
      "isHot": false,
      "hotScore": 17.7
    }
  },
  "timestamp": "2026-07-25T17:25:00+09:00"
}
```

---

### Response Field

| 필드 | 타입 | 설명 |
| --- | --- | --- |
| `commentId` | Long | 삭제된 댓글 ID |
| `postId` | Long | 댓글이 속한 게시글 ID |
| `deletedAt` | String | Soft Delete 시각 |
| `postAvailable` | Boolean | 원문 게시글 접근 가능 여부 |
| `postMetrics` | Object | 삭제 후 댓글 수와 HOT 상태 |

---

### Exception

#### 400 Bad Request — 댓글 ID 오류

```
{
  "success": false,
  "code": "COMMON_INVALID_REQUEST",
  "message": "입력값을 확인해 주세요.",
  "data": {
    "field": "commentId",
    "reason": "댓글 ID는 1 이상의 숫자여야 합니다."
  },
  "timestamp": "2026-07-25T17:25:00+09:00"
}
```

---

#### 401 Unauthorized

```
{
  "success": false,
  "code": "AUTH_ACCESS_TOKEN_INVALID",
  "message": "로그인이 필요합니다.",
  "data": null,
  "timestamp": "2026-07-25T17:25:00+09:00"
}
```

---

#### 403 Forbidden — 작성자 아님

```
{
  "success": false,
  "code": "COMMENT_DELETE_FORBIDDEN",
  "message": "댓글을 삭제할 권한이 없습니다.",
  "data": null,
  "timestamp": "2026-07-25T17:25:00+09:00"
}
```

---

#### 404 Not Found — 댓글 없음

```
{
  "success": false,
  "code": "COMMENT_NOT_FOUND",
  "message": "댓글을 찾을 수 없습니다.",
  "data": null,
  "timestamp": "2026-07-25T17:25:00+09:00"
}
```

---

#### 409 Conflict — 이미 삭제된 댓글

```
{
  "success": false,
  "code": "COMMENT_ALREADY_DELETED",
  "message": "이미 삭제된 댓글입니다.",
  "data": {
    "commentId": 36
  },
  "timestamp": "2026-07-25T17:25:00+09:00"
}
```

---

#### 500 Internal Server Error

```
{
  "success": false,
  "code": "COMMON_INTERNAL_SERVER_ERROR",
  "message": "일시적인 오류가 발생했습니다.",
  "data": null,
  "timestamp": "2026-07-25T17:25:00+09:00"
}
```

---

### 프론트 처리

```
댓글 삭제 선택
→ 삭제 확인 모달 표시
→ DELETE /api/v1/comments/{commentId} 호출

삭제 성공
→ 해당 댓글을 목록에서 제거
→ postMetrics.commentCount로 댓글 수 갱신
→ HOT 배지 상태 갱신

COMMENT_ALREADY_DELETED
→ 오류 토스트를 생략하거나 짧게 안내
→ 해당 댓글을 목록에서 제거
→ 댓글 목록 재조회 가능

postAvailable = false
→ 댓글 삭제 완료 후 원문 화면 이동 없이 내 댓글 목록으로 복귀
```

---

## 게시글 좋아요

Method: PUT
Progress: 완료
URI: /api/v1/posts/{postId}/like
담당자: 김윤석
연동여부: Yes

로그인한 회원이 활성 상태의 커뮤니티 게시글에 좋아요를 등록한다.

동일 회원은 같은 게시글에 한 번만 좋아요를 등록할 수 있으며, 동일 요청이 반복되어도 좋아요 수가 중복 증가하지 않는다.

---

### Request

- Request HTTP Method: `PUT`
- 인증 필요: 필요
- Request Body: 없음

#### Request Header

```
Authorization: Bearer {accessToken}
```

#### Path Variable

```
PUT /api/v1/posts/154/like
```

| 필드 | 타입 | 필수 | 설명 |
| --- | --- | --- | --- |
| `postId` | Long | Y | 좋아요를 등록할 게시글 ID |

---

### 처리 기준

#### 1. 회원·게시글 확인

- 로그인한 회원 ID는 Access Token에서 확인한다.
- 회원 상태가 `ACTIVE`인지 확인한다.
- `postId`에 해당하는 게시글이 존재하는지 확인한다.
- 게시글이 `status = ACTIVE`, `deletedAt IS NULL`인 경우에만 좋아요를 등록할 수 있다.
- 삭제 또는 운영 숨김 게시글에는 좋아요를 등록할 수 없다.

#### 2. 좋아요 등록

- `post_like`에 게시글 ID와 회원 ID를 저장한다.
- `(post_id, member_id)` Unique 제약으로 중복 관계를 방지한다.
- 일반 회원 글과 자동 리포트 게시글 모두 좋아요할 수 있다.
- 자신의 게시글에도 좋아요를 등록할 수 있다.
- 좋아요 등록은 게시글 본문·조회 수·댓글 수를 변경하지 않는다.

```
INSERT INTO post_like (post_id, member_id, created_at)
VALUES (:postId, :memberId, NOW());
```

#### 3. 멱등성

- 이미 좋아요한 게시글에 다시 요청해도 오류로 처리하지 않는다.
- 기존 관계를 유지하고 새 행을 생성하지 않는다.
- 동시 요청으로 Unique 충돌이 발생하면 현재 좋아요 관계를 조회해 정상 응답한다.
- 최종 응답은 항상 `likedByMe = true`다.

#### 4. 좋아요 수와 HOT 점수

- 등록 후 현재 `likeCount`를 집계한다.
- 좋아요 한 건은 HOT 점수에 5점으로 반영된다.
- 등록 전 HOT 기준 미달 글이 등록 후 `isHot = true`가 될 수 있다.
- `hotScore`, `hotRank`는 현재 시점의 계산값을 반환한다.
- 7일이 지난 글은 좋아요를 누를 수 있더라도 HOT 대상에서는 제외된다.

#### 5. 알림

- 최종 확정 알림 유형에는 게시글 좋아요 알림이 별도로 정의되어 있지 않으므로 좋아요 등록만으로 필수 알림·FCM을 생성하지 않는다.
- 향후 알림을 추가할 경우 COMMUNITY category와 별도 type을 먼저 정의한다.

---

### Response

#### 200 OK — 좋아요 등록 완료

```
{
  "success": true,
  "code": "POST_LIKE_SUCCESS",
  "message": "게시글에 좋아요를 등록했습니다.",
  "data": {
    "postId": 154,
    "likedByMe": true,
    "likeCount": 1,
    "commentCount": 0,
    "viewCount": 1,
    "isHot": true,
    "hotScore": 22.7,
    "hotRank": 8,
    "likedAt": "2026-07-25T17:30:00+09:00"
  },
  "timestamp": "2026-07-25T17:30:00+09:00"
}
```

#### 200 OK — 이미 좋아요한 경우

```
{
  "success": true,
  "code": "POST_LIKE_ALREADY_EXISTS",
  "message": "이미 좋아요한 게시글입니다.",
  "data": {
    "postId": 154,
    "likedByMe": true,
    "likeCount": 1,
    "commentCount": 0,
    "viewCount": 1,
    "isHot": true,
    "hotScore": 22.7,
    "hotRank": 8,
    "likedAt": "2026-07-25T17:30:00+09:00"
  },
  "timestamp": "2026-07-25T17:30:03+09:00"
}
```

---

### Response Field

| 필드 | 타입 | 설명 |
| --- | --- | --- |
| `postId` | Long | 좋아요 대상 게시글 ID |
| `likedByMe` | Boolean | 현재 회원의 좋아요 여부, 항상 `true` |
| `likeCount` | Long | 등록 후 전체 좋아요 수 |
| `commentCount` | Long | 현재 댓글 수 |
| `viewCount` | Long | 현재 조회 수 |
| `isHot` | Boolean | 등록 후 HOT 여부 |
| `hotScore` | Decimal | null | 등록 후 HOT 점수 |
| `hotRank` | Long | null | 게시판 내 HOT 순위 |
| `likedAt` | String | 최초 좋아요 등록 시각 |

---

### Exception

#### 404 Not Found — 회원 없음·비활성·탈퇴

```
{
  "success": false,
  "code": "MEMBER_NOT_FOUND",
  "message": "회원 정보를 찾을 수 없습니다.",
  "data": null,
  "timestamp": "2026-07-25T17:30:00+09:00"
}
```

---

#### 400 Bad Request — 게시글 ID 오류

```
{
  "success": false,
  "code": "COMMON_INVALID_REQUEST",
  "message": "입력값을 확인해 주세요.",
  "data": {
    "field": "postId",
    "reason": "게시글 ID는 1 이상의 숫자여야 합니다."
  },
  "timestamp": "2026-07-25T17:30:00+09:00"
}
```

---

#### 401 Unauthorized

```
{
  "success": false,
  "code": "AUTH_ACCESS_TOKEN_INVALID",
  "message": "로그인이 필요합니다.",
  "data": null,
  "timestamp": "2026-07-25T17:30:00+09:00"
}
```

---

#### 404 Not Found — 게시글 없음·삭제·숨김

```
{
  "success": false,
  "code": "POST_NOT_FOUND",
  "message": "게시글을 찾을 수 없습니다.",
  "data": null,
  "timestamp": "2026-07-25T17:30:00+09:00"
}
```

---

#### 500 Internal Server Error

```
{
  "success": false,
  "code": "COMMON_INTERNAL_SERVER_ERROR",
  "message": "일시적인 오류가 발생했습니다.",
  "data": null,
  "timestamp": "2026-07-25T17:30:00+09:00"
}
```

---

### 프론트 처리

```
비활성 좋아요 버튼 선택
→ PUT /api/v1/posts/{postId}/like 호출
→ likedByMe = true 적용
→ likeCount와 HOT 상태 갱신

POST_LIKE_ALREADY_EXISTS
→ 오류 메시지 표시하지 않음
→ 좋아요 활성 상태 유지
→ 서버 카운트로 동기화

POST_NOT_FOUND
→ "삭제되었거나 확인할 수 없는 게시글입니다." 안내
→ 커뮤니티 목록으로 이동
```

---

## 게시글 좋아요 해제

Method: DELETE
Progress: 완료
URI: /api/v1/posts/{postId}/like
담당자: 김윤석
연동여부: No

로그인한 회원이 커뮤니티 게시글에 등록한 자신의 좋아요를 해제한다.

좋아요 관계가 이미 없어도 오류로 처리하지 않으며, 반복 요청의 최종 결과는 항상 `likedByMe = false`다.

---

### Request

- Request HTTP Method: `DELETE`
- 인증 필요: 필요
- Request Body: 없음

#### Request Header

```
Authorization: Bearer {accessToken}
```

#### Path Variable

```
DELETE /api/v1/posts/154/like
```

| 필드 | 타입 | 필수 | 설명 |
| --- | --- | --- | --- |
| `postId` | Long | Y | 좋아요를 해제할 게시글 ID |

---

### 처리 기준

#### 1. 회원·게시글 확인

- 로그인한 회원 ID는 Access Token에서 확인한다.
- 회원 상태가 `ACTIVE`인지 확인한다.
- `postId`에 해당하는 게시글이 존재하는지 확인한다.
- 일반 UI에서는 활성 게시글에서만 해제 요청을 보낸다.
- 게시글이 삭제 또는 숨김 처리된 뒤에도 기존 관계 정리를 허용할지 정책이 필요하나, 현재 일반 API는 접근 불가 게시글을 `POST_NOT_FOUND`로 처리한다.

#### 2. 좋아요 관계 삭제

- `post_id + member_id`가 일치하는 `post_like` 관계를 조회한다.
- 관계가 존재하면 물리적으로 삭제한다.
- 좋아요 관계는 별도 Soft Delete 이력을 보존하지 않는다.

```
DELETE FROM post_like
WHERE post_id = :postId
  AND member_id = :memberId;
```

#### 3. 멱등성

- 이미 좋아요하지 않은 게시글에 해제 요청을 보내도 정상 응답한다.
- 관계가 없으면 추가 삭제를 수행하지 않는다.
- 동시 요청 중 하나가 먼저 관계를 삭제해도 나머지 요청은 이미 해제된 상태로 처리한다.
- 최종 응답은 항상 `likedByMe = false`다.
- 중복 요청으로 좋아요 수가 여러 번 감소해서는 안 된다.

#### 4. 좋아요 수와 HOT 점수

- 해제 후 현재 `likeCount`를 다시 집계한다.
- 좋아요 한 건이 제거되면 HOT 점수에서 5점이 감소한다.
- 해제 후 점수가 20 미만이면 `isHot = false`가 된다.
- `hotRank`도 다시 계산될 수 있다.
- 좋아요 수는 0 미만이 될 수 없다.

#### 5. 다른 데이터 영향

- 좋아요 해제로 게시글 조회 수, 댓글 수, 본문, 작성자 정보는 변경되지 않는다.
- 자동 리포트 게시글의 리포트 찜 상태와 게시글 좋아요는 별개의 기능이다.
- 게시글 좋아요 해제가 리포트 찜을 해제하지 않는다.

---

### Response

#### 200 OK — 좋아요 해제 완료

```
{
  "success": true,
  "code": "POST_UNLIKE_SUCCESS",
  "message": "게시글 좋아요를 해제했습니다.",
  "data": {
    "postId": 154,
    "likedByMe": false,
    "likeCount": 0,
    "commentCount": 0,
    "viewCount": 1,
    "isHot": false,
    "hotScore": 17.7,
    "hotRank": null,
    "unlikedAt": "2026-07-25T17:35:00+09:00"
  },
  "timestamp": "2026-07-25T17:35:00+09:00"
}
```

#### 200 OK — 이미 좋아요가 없는 경우

```
{
  "success": true,
  "code": "POST_ALREADY_UNLIKED",
  "message": "이미 좋아요가 해제된 게시글입니다.",
  "data": {
    "postId": 154,
    "likedByMe": false,
    "likeCount": 0,
    "commentCount": 0,
    "viewCount": 1,
    "isHot": false,
    "hotScore": 17.7,
    "hotRank": null,
    "unlikedAt": "2026-07-25T17:35:00+09:00"
  },
  "timestamp": "2026-07-25T17:35:00+09:00"
}
```

`unlikedAt`은 별도 해제 이력 컬럼이 아니라 현재 요청 처리 시각이다.

---

### Response Field

| 필드 | 타입 | 설명 |
| --- | --- | --- |
| `postId` | Long | 좋아요를 해제한 게시글 ID |
| `likedByMe` | Boolean | 현재 회원의 좋아요 여부, 항상 `false` |
| `likeCount` | Long | 해제 후 전체 좋아요 수 |
| `commentCount` | Long | 현재 댓글 수 |
| `viewCount` | Long | 현재 조회 수 |
| `isHot` | Boolean | 해제 후 HOT 여부 |
| `hotScore` | Decimal | null | 해제 후 HOT 점수 |
| `hotRank` | Long | null | 게시판 내 HOT 순위 |
| `unlikedAt` | String | 요청 처리 시각 |

---

### Exception

#### 400 Bad Request — 게시글 ID 오류

```
{
  "success": false,
  "code": "COMMON_INVALID_REQUEST",
  "message": "입력값을 확인해 주세요.",
  "data": {
    "field": "postId",
    "reason": "게시글 ID는 1 이상의 숫자여야 합니다."
  },
  "timestamp": "2026-07-25T17:35:00+09:00"
}
```

---

#### 401 Unauthorized

```
{
  "success": false,
  "code": "AUTH_ACCESS_TOKEN_INVALID",
  "message": "로그인이 필요합니다.",
  "data": null,
  "timestamp": "2026-07-25T17:35:00+09:00"
}
```

---

#### 404 Not Found — 게시글 없음·삭제·숨김

```
{
  "success": false,
  "code": "POST_NOT_FOUND",
  "message": "게시글을 찾을 수 없습니다.",
  "data": null,
  "timestamp": "2026-07-25T17:35:00+09:00"
}
```

---

#### 500 Internal Server Error

```
{
  "success": false,
  "code": "COMMON_INTERNAL_SERVER_ERROR",
  "message": "일시적인 오류가 발생했습니다.",
  "data": null,
  "timestamp": "2026-07-25T17:35:00+09:00"
}
```

---

### 프론트 처리

```
활성 좋아요 버튼 선택
→ DELETE /api/v1/posts/{postId}/like 호출
→ likedByMe = false 적용
→ likeCount와 HOT 상태 갱신

POST_ALREADY_UNLIKED
→ 오류 메시지 표시하지 않음
→ 좋아요 비활성 상태로 동기화

자동 리포트 게시글
→ 게시글 좋아요와 리포트 찜 버튼을 별개로 관리
→ 게시글 좋아요 해제로 리포트 찜 UI를 변경하지 않음
```

---

## 체크리스트 답변 일괄 저장

Method: PUT
Progress: 완료
URI: /api/v1/studies/{studyId}/field-visit/checklist/answers
담당자: 윤다인
연동여부: Yes

로그인한 임장 참여자가 본인 체크리스트의 여러 항목 완료 여부를 한 번에 저장한다.

이 API는 체크리스트 문항의 점수·주관식 답변을 저장하지 않으며, 각 항목을 현장에서 확인했는지에 대한 `isCompleted` 상태만 관리한다. 텍스트·사진·STT 메모는 현장 기록 API로 별도 저장한다.

범위: BE-015. AI-002 완료 범위(생성·조회·FastAPI 내부 generate)에 포함하지 않는다.

쓰기 조건: ACTIVE 멤버, session/participant `IN_PROGRESS`, 해당 study의 `report` 행이 아직 없어야 한다.
`report`가 존재하면(PENDING/IN_PROGRESS/DONE/FAILED) `FIELD_VISIT_REPORT_LOCKED`로 차단한다.

---

### Request

- Request HTTP Method: `PUT`
- 인증 필요: 필요
- Content-Type: `application/json`

#### Request Header

```
Authorization: Bearer {accessToken}
```

#### Path Variable

```
PUT /api/v1/studies/7/field-visit/checklist/answers
```

| 필드 | 타입 | 필수 | 설명 |
| --- | --- | --- | --- |
| `studyId` | Long | Y | 체크리스트 완료 상태를 저장할 스터디 ID |

#### Request Body

```json
{
  "answers": [
    {
      "checklistItemId": 501,
      "isCompleted": true
    },
    {
      "checklistItemId": 502,
      "isCompleted": false
    }
  ]
}
```

| 필드 | 타입 | 필수 | 설명 |
| --- | --- | --- | --- |
| `answers` | Array | Y | 저장할 항목 상태 목록, 1개 이상 |
| `answers[].checklistItemId` | Long | Y | 본인 체크리스트 항목 ID |
| `answers[].isCompleted` | Boolean | Y | 현장 확인 완료 여부 |

---

### 처리 기준

#### 1. 회원·참여 상태 확인

- 로그인한 회원 ID는 Access Token에서 확인한다.
- 요청자는 해당 스터디의 승인 멤버이며 현재 임장 세션 참여자여야 한다.
- `field_session.status = IN_PROGRESS`이고 본인 `field_participant.status = IN_PROGRESS`일 때만 저장할 수 있다.
- 개인 임장을 종료했거나 전체 세션이 종료된 경우 완료 상태를 변경할 수 없다.
- 완료된 스터디의 체크리스트는 읽기 전용이다.

#### 2. 항목 소유권 검증

- 모든 `checklistItemId`는 로그인 회원의 체크리스트에 속해야 한다.
- 다른 참여자의 체크리스트 항목은 변경할 수 없다.
- 같은 요청 배열에 동일한 `checklistItemId`가 중복되면 잘못된 요청으로 처리한다.
- 하나라도 존재하지 않거나 소유권이 다른 항목이 있으면 전체 요청을 저장하지 않는다.

#### 3. 완료 상태 저장

- 항목별 `checklist_answer`가 없으면 생성하고, 있으면 기존 행을 갱신한다.
- `isCompleted = true`로 변경하면 `completedAt`을 서버 현재 시각으로 저장한다.
- 이미 완료된 항목을 다시 `true`로 저장하면 기존 완료 시각을 유지하는 것을 권장한다.
- `isCompleted = false`로 변경하면 `completedAt = null`로 초기화한다.
- 완료 상태를 해제하더라도 해당 항목에 연결된 현장 기록은 삭제하지 않는다.
- 완료 체크와 현장 기록 존재 여부는 서로 독립적으로 관리한다.

#### 4. 일괄 처리와 원자성

- 요청 배열의 모든 항목은 하나의 트랜잭션에서 처리한다.
- 일부 항목만 성공하고 일부가 실패하는 부분 저장은 허용하지 않는다.
- 동일한 상태를 반복 저장해도 결과가 중복 생성되지 않는다.
- 응답에는 요청 처리 후 전체 완료 수와 카테고리별 완료 수를 반환한다.

#### 5. 오프라인 재전송

- 최종 요구사항은 체크 상태의 오프라인 저장·재전송을 포함한다.
- 이 API 자체에는 `clientRequestId`가 없으므로 동일한 최종 상태를 반복 `PUT`해도 동일 결과가 되는 상태 기반 멱등성을 사용한다.
- 클라이언트는 로컬 대기 상태를 서버 응답과 동기화한 뒤 제거한다.

---

### Response

#### 200 OK

```json
{
  "success": true,
  "code": "CHECKLIST_COMPLETION_SAVE_SUCCESS",
  "message": "체크리스트 완료 상태를 저장했습니다.",
  "data": {
    "studyId": 7,
    "checklistId": 55,
    "savedCount": 2,
    "completedCount": 6,
    "totalCount": 12,
    "answers": [
      {
        "checklistItemId": 501,
        "isCompleted": true,
        "completedAt": "2026-07-25T14:15:00+09:00"
      },
      {
        "checklistItemId": 502,
        "isCompleted": false,
        "completedAt": null
      }
    ],
    "categoryProgress": [
      {
        "category": "교통",
        "completedCount": 2,
        "totalCount": 3
      },
      {
        "category": "단지환경",
        "completedCount": 1,
        "totalCount": 3
      }
    ]
  },
  "timestamp": "2026-07-25T14:15:00+09:00"
}
```

---

### Response Field

| 필드 | 타입 | 설명 |
| --- | --- | --- |
| `studyId` | Long | 스터디 ID |
| `checklistId` | Long | 본인 체크리스트 ID |
| `savedCount` | Integer | 이번 요청에서 처리한 항목 수 |
| `completedCount` | Integer | 저장 후 완료된 전체 항목 수 |
| `totalCount` | Integer | 전체 항목 수 |
| `answers` | Array | 저장 결과 |
| `categoryProgress` | Array | 카테고리별 완료 현황 |

#### `answers[]`

| 필드 | 타입 | 설명 |
| --- | --- | --- |
| `checklistItemId` | Long | 체크리스트 항목 ID |
| `isCompleted` | Boolean | 저장된 완료 여부 |
| `completedAt` | String | null | 완료 시각. 미완료이면 `null` |

#### `categoryProgress[]`

| 필드 | 타입 | 설명 |
| --- | --- | --- |
| `category` | String | 동적 카테고리명 |
| `completedCount` | Integer | 해당 카테고리 완료 수 |
| `totalCount` | Integer | 해당 카테고리 전체 수 |

---

### Exception

#### 400 Bad Request — 빈 목록

```json
{
  "success": false,
  "code": "COMMON_INVALID_REQUEST",
  "message": "입력값을 확인해 주세요.",
  "data": {
    "field": "answers",
    "reason": "저장할 체크리스트 항목을 1개 이상 전달해 주세요."
  },
  "timestamp": "2026-07-25T14:15:00+09:00"
}
```

---

#### 400 Bad Request — 항목 중복

```json
{
  "success": false,
  "code": "CHECKLIST_ITEM_DUPLICATED",
  "message": "동일한 체크리스트 항목이 중복되었습니다.",
  "data": {
    "checklistItemId": 501
  },
  "timestamp": "2026-07-25T14:15:00+09:00"
}
```

---

#### 401 Unauthorized

```json
{
  "success": false,
  "code": "AUTH_ACCESS_TOKEN_INVALID",
  "message": "로그인이 필요합니다.",
  "data": null,
  "timestamp": "2026-07-25T14:15:00+09:00"
}
```

---

#### 403 Forbidden — 본인 체크리스트 항목 아님

```json
{
  "success": false,
  "code": "CHECKLIST_ITEM_ACCESS_DENIED",
  "message": "본인의 체크리스트 항목만 수정할 수 있습니다.",
  "data": {
    "checklistItemId": 999
  },
  "timestamp": "2026-07-25T14:15:00+09:00"
}
```

---

#### 404 Not Found — 체크리스트 없음

```json
{
  "success": false,
  "code": "CHECKLIST_NOT_FOUND",
  "message": "생성된 체크리스트를 찾을 수 없습니다.",
  "data": null,
  "timestamp": "2026-07-25T14:15:00+09:00"
}
```

---

#### 404 Not Found — 항목 없음

```json
{
  "success": false,
  "code": "CHECKLIST_ITEM_NOT_FOUND",
  "message": "체크리스트 항목을 찾을 수 없습니다.",
  "data": {
    "checklistItemId": 999
  },
  "timestamp": "2026-07-25T14:15:00+09:00"
}
```

---

#### 409 Conflict — 개인 임장 종료

```json
{
  "success": false,
  "code": "FIELD_PARTICIPANT_ALREADY_ENDED",
  "message": "임장을 종료한 뒤에는 체크리스트를 수정할 수 없습니다.",
  "data": null,
  "timestamp": "2026-07-25T14:15:00+09:00"
}
```

---

#### 409 Conflict — 세션 종료

```json
{
  "success": false,
  "code": "FIELD_VISIT_ALREADY_ENDED",
  "message": "종료된 임장 세션의 체크리스트는 수정할 수 없습니다.",
  "data": null,
  "timestamp": "2026-07-25T14:15:00+09:00"
}
```

---

#### 500 Internal Server Error

```json
{
  "success": false,
  "code": "COMMON_INTERNAL_SERVER_ERROR",
  "message": "일시적인 오류가 발생했습니다.",
  "data": null,
  "timestamp": "2026-07-25T14:15:00+09:00"
}
```

---

### 프론트 처리

```
사용자가 체크박스 선택 또는 해제
→ 로컬 화면 상태 즉시 반영
→ 변경된 항목을 answers에 담아 PUT 요청

저장 성공
→ 서버가 반환한 isCompleted·completedAt으로 상태 동기화
→ completedCount / totalCount 갱신

오프라인 상태
→ Room에 최종 isCompleted 상태 저장
→ 재전송 대기 표시
→ 네트워크 복구 후 동일 PUT 요청

FIELD_PARTICIPANT_ALREADY_ENDED 또는 FIELD_VISIT_ALREADY_ENDED
→ 로컬 변경 롤백
→ 체크리스트를 읽기 전용으로 전환

CHECKLIST_ITEM_ACCESS_DENIED 또는 CHECKLIST_ITEM_NOT_FOUND
→ 체크리스트 전체 재조회
→ 서버 목록과 로컬 목록 동기화
```

---

## 개인 맞춤 체크리스트 생성

Method: POST
Progress: 완료
URI: /api/v1/studies/{studyId}/field-visit/checklist/generate
담당자: 윤다인
연동여부: Yes

임장 세션에 참여 중인 회원의 온보딩 정보·스터디 목적·대상 아파트 정보를 바탕으로
개인 맞춤 체크리스트를 1회 생성한다.

공개 API Method·Path·Request·Response는 변경하지 않는다. Android/Frontend는 이 공개 API만
호출하며, 내부 `/internal/v1/checklists/select`를 직접 호출하지 않는다.
외부 응답 DTO에도 `itemCode`·`answerType`은 추가되지 않는다.

생성 방식은 feature flag `CHECKLIST_CATALOG_SELECTION_ENABLED`(기본 false)로 병행한다.

- flag false: 기존 AI-002 경로. Spring → FastAPI `POST /internal/v1/checklists/generate`
  → GMS Gemini가 category/title/subtitle를 생성한다.
- flag true(AI-002-1): Spring이 v3 카탈로그를 필터·점수·shortlist한 뒤 FastAPI
  `POST /internal/v1/checklists/select`로 itemCode만 선택한다. Spring이 카탈로그
  문구를 기존 `checklist_item` 스냅샷으로 저장한다. select 실패 시 catalog fallback,
  catalog fallback 실패 시 기존 hardcoded fallback을 사용한다.

체크리스트 카테고리는 고정 enum이 아니라 생성·선택 결과의 동적 문자열이며,
각 항목은 `category`·`title`·`subtitle`·`displayOrder`를 가진다.

범위: AI-002 / AI-002-1. 답변 일괄 저장(`PUT .../checklist/answers`)과 현장 기록 CRUD는 BE-015다.

---

### Request

- Request HTTP Method: `POST`
- 인증 필요: 필요
- Request Body: 없음

#### Request Header

```
Authorization: Bearer {accessToken}
```

#### Path Variable

```
POST /api/v1/studies/7/field-visit/checklist/generate
```

| 필드 | 타입 | 필수 | 설명 |
| --- | --- | --- | --- |
| `studyId` | Long | Y | 체크리스트를 생성할 스터디 ID (1 이상) |

---

### 처리 기준

#### 1. 생성 권한·상태

- 로그인 회원 ID는 Access Token에서 확인한다.
- 스터디가 존재하고(`deleted_at IS NULL`) ACTIVE `study_member`여야 한다.
- 해당 스터디의 `field_session`이 존재해야 한다. 없으면 `FIELD_VISIT_NOT_STARTED`.
- `field_session.status = IN_PROGRESS`여야 한다. 아니면 `FIELD_VISIT_ALREADY_ENDED`.
- 본인 `field_participant`가 존재해야 한다. 없으면 `CHECKLIST_GENERATE_FORBIDDEN`.
- `field_participant.status = IN_PROGRESS`여야 한다. 아니면
  `FIELD_PARTICIPANT_ALREADY_ENDED`와 체크리스트 전용 message를 반환한다.

#### 2. 체크리스트 유일성·멱등

- 체크리스트는 `(session_id, member_id)` UNIQUE로 참여자·세션당 1건이다.
- 이미 있으면 AI를 호출하지 않고 기존 결과를 `200 OK` / `CHECKLIST_ALREADY_EXISTS`로 반환한다.
- 동시 생성으로 UNIQUE 충돌이 나면 실패한 저장을 버리고 기존 행을 재조회해 동일하게 반환한다.
- 재생성·항목 추가·삭제는 제공하지 않는다.

#### 3. 맞춤 생성 입력 (AI-002 확정 범위)

Spring이 FastAPI에 전달하는 개인화 입력은 다음만 포함한다.

- 회원: `memberPurpose`, `maritalStatus`, `hasVehicle`, `hasChildren`, `priorities`(순서 보존), `ageGroup`
- 아파트: `apartmentId`, `name`, `address`, `districtName`, `dongName`, `householdCount`, `completionYearMonth`, `parkingSpaceCount`
- 스터디: `studyId`, `studyPurpose`, `goal`

AI 입력에서 제외: `householdType`, `budget`, `interestRegion`, 공개 동의 플래그, `selectedCharacterId`.
다른 참여자의 선호·현장 기록은 포함하지 않는다.

#### 4. AI 호출과 fallback

- Spring connect timeout 3초, read timeout 20초, 자동 retry 0회(`ssabangpalbang.fieldvisit.ai`).
- `CHECKLIST_CATALOG_SELECTION_ENABLED=false`(기본): Spring → FastAPI
  (`FIELD_VISIT_AI_BASE_URL` + `FIELD_VISIT_AI_GENERATE_PATH`) → GMS Gemini 질문 생성.
- `CHECKLIST_CATALOG_SELECTION_ENABLED=true`: Spring 카탈로그 필터·점수·shortlist →
  FastAPI (`FIELD_VISIT_AI_SELECT_PATH`) itemCode 선택 → Spring 검증·문구 스냅샷 저장.
  실패 시 catalog fallback → hardcoded fallback 순으로 전환한다.
- FastAPI·Provider 연결 실패, timeout, 5xx, 빈/잘못된 응답, 스키마 검증 실패,
  온보딩 허용값 부족 시 Spring production fallback을 사용한다.
- fallback 사용 시 `isFallback = true`, HTTP 201, `CHECKLIST_FALLBACK_GENERATE_SUCCESS`.
- AI/카탈로그 실패가 공개 API 500으로 이어지지 않도록 fallback을 우선한다.
- 내부 예외 상세·Secret은 사용자 응답에 노출하지 않는다.

#### 5. 동적 카테고리·항목

- `category`는 최대 30자의 동적 문자열이다. 클라이언트는 고정 enum을 가정하지 않는다.
- `title`은 비어 있을 수 없고, `subtitle`은 `null`일 수 있다.
- `displayOrder`는 1 이상이며 같은 체크리스트 안에서 중복되지 않는다. 연속 번호는 강제하지 않는다.
- 응답 `categories[]`는 항목의 `displayOrder` 오름차순을 기준으로 카테고리를 묶는다.

#### 6. 초기 완료·기록 상태

- AI-002 생성 경로에서는 `checklist_answer` 행을 만들지 않는다.
- 응답에서 답변 행이 없으면 `isCompleted = false`, `completedAt = null`로 처리한다.
- 생성 직후 `completedCount = 0`, 항목별 `recordCount`는 기존 본인 기록 집계값이다.

---

### Response

#### 201 Created — AI 맞춤 체크리스트 생성

```json
{
  "success": true,
  "code": "CHECKLIST_GENERATE_SUCCESS",
  "message": "개인 맞춤 체크리스트를 생성했습니다.",
  "data": {
    "studyId": 7,
    "sessionId": 100,
    "checklistId": 55,
    "isFallback": false,
    "generatedAt": "2026-07-25T14:03:00+09:00",
    "completedCount": 0,
    "totalCount": 4,
    "categories": [
      {
        "category": "교통",
        "itemCount": 2,
        "items": [
          {
            "checklistItemId": 501,
            "title": "지하철역 접근성",
            "subtitle": "단지 출입구에서 승강장까지 실제 소요 시간을 확인해 주세요.",
            "displayOrder": 1,
            "isCompleted": false,
            "completedAt": null,
            "recordCount": 0
          },
          {
            "checklistItemId": 502,
            "title": "출퇴근 시간대 혼잡",
            "subtitle": "버스·지하철 대기 인원과 도로 정체를 확인해 주세요.",
            "displayOrder": 2,
            "isCompleted": false,
            "completedAt": null,
            "recordCount": 0
          }
        ]
      },
      {
        "category": "단지환경",
        "itemCount": 2,
        "items": [
          {
            "checklistItemId": 503,
            "title": "단지 내부 경사",
            "subtitle": "유모차·휠체어 이동이 어려운 구간이 있는지 확인해 주세요.",
            "displayOrder": 3,
            "isCompleted": false,
            "completedAt": null,
            "recordCount": 0
          },
          {
            "checklistItemId": 504,
            "title": "지하주차장 상태",
            "subtitle": null,
            "displayOrder": 4,
            "isCompleted": false,
            "completedAt": null,
            "recordCount": 0
          }
        ]
      }
    ]
  },
  "timestamp": "2026-07-25T14:03:00+09:00"
}
```

#### 200 OK — 이미 생성된 체크리스트

```json
{
  "success": true,
  "code": "CHECKLIST_ALREADY_EXISTS",
  "message": "이미 생성된 체크리스트입니다.",
  "data": {
    "studyId": 7,
    "sessionId": 100,
    "checklistId": 55,
    "isFallback": false,
    "generatedAt": "2026-07-25T14:03:00+09:00",
    "completedCount": 1,
    "totalCount": 4,
    "categories": [
      {
        "category": "교통",
        "itemCount": 1,
        "items": [
          {
            "checklistItemId": 501,
            "title": "지하철역 접근성",
            "subtitle": "단지 출입구에서 승강장까지 실제 소요 시간을 확인해 주세요.",
            "displayOrder": 1,
            "isCompleted": true,
            "completedAt": "2026-07-25T14:15:00+09:00",
            "recordCount": 2
          }
        ]
      }
    ]
  },
  "timestamp": "2026-07-25T14:20:00+09:00"
}
```

#### 201 Created — fallback 체크리스트 생성

```json
{
  "success": true,
  "code": "CHECKLIST_FALLBACK_GENERATE_SUCCESS",
  "message": "기본 체크리스트를 생성했습니다.",
  "data": {
    "studyId": 7,
    "sessionId": 100,
    "checklistId": 56,
    "isFallback": true,
    "generatedAt": "2026-07-25T14:03:00+09:00",
    "completedCount": 0,
    "totalCount": 10,
    "categories": [
      {
        "category": "교통",
        "itemCount": 1,
        "items": [
          {
            "checklistItemId": 601,
            "title": "대중교통·도로 접근성",
            "subtitle": "가장 가까운 역·버스 정류장까지 실제 도보 시간을 확인하세요.",
            "displayOrder": 1,
            "isCompleted": false,
            "completedAt": null,
            "recordCount": 0
          }
        ]
      }
    ]
  },
  "timestamp": "2026-07-25T14:03:00+09:00"
}
```

---

### Response Field

| 필드 | 타입 | 설명 |
| --- | --- | --- |
| `studyId` | Long | 스터디 ID |
| `sessionId` | Long | 임장 세션 ID |
| `checklistId` | Long | 개인 체크리스트 ID |
| `isFallback` | Boolean | fallback(기본) 체크리스트 여부 |
| `generatedAt` | String | 생성 시각(Asia/Seoul OffsetDateTime) |
| `completedCount` | Integer | 완료된 항목 수 |
| `totalCount` | Integer | 전체 항목 수 |
| `categories` | Array | 동적 카테고리별 항목 목록 |

#### `categories[]`

| 필드 | 타입 | 설명 |
| --- | --- | --- |
| `category` | String | 서버가 생성한 카테고리 이름 |
| `itemCount` | Integer | 카테고리 항목 수 |
| `items` | Array | 항목 목록 |

#### `items[]`

| 필드 | 타입 | 설명 |
| --- | --- | --- |
| `checklistItemId` | Long | 항목 ID |
| `title` | String | 주 제목 |
| `subtitle` | String \| null | 보조 설명 |
| `displayOrder` | Integer | 전체 표시 순서 |
| `isCompleted` | Boolean | 완료 여부 |
| `completedAt` | String \| null | 완료 시각 |
| `recordCount` | Integer | 로그인 회원이 해당 항목에 남긴 삭제되지 않은 `field_record` 수 |

---

### Exception

#### 401 Unauthorized

```json
{
  "success": false,
  "code": "AUTH_ACCESS_TOKEN_INVALID",
  "message": "로그인이 필요합니다.",
  "data": null,
  "timestamp": "2026-07-25T14:03:00+09:00"
}
```

---

#### 403 Forbidden — 생성 권한 없음

ACTIVE 멤버가 아니거나 본인 `field_participant`가 없을 때.

```json
{
  "success": false,
  "code": "CHECKLIST_GENERATE_FORBIDDEN",
  "message": "진행 중인 임장 참여자만 체크리스트를 생성할 수 있습니다.",
  "data": null,
  "timestamp": "2026-07-25T14:03:00+09:00"
}
```

---

#### 404 Not Found — 스터디 없음

```json
{
  "success": false,
  "code": "STUDY_NOT_FOUND",
  "message": "스터디를 찾을 수 없습니다.",
  "data": null,
  "timestamp": "2026-07-25T14:03:00+09:00"
}
```

---

#### 409 Conflict — 임장 미시작

```json
{
  "success": false,
  "code": "FIELD_VISIT_NOT_STARTED",
  "message": "임장을 시작한 후 체크리스트를 생성할 수 있습니다.",
  "data": null,
  "timestamp": "2026-07-25T14:03:00+09:00"
}
```

---

#### 409 Conflict — 세션 종료

```json
{
  "success": false,
  "code": "FIELD_VISIT_ALREADY_ENDED",
  "message": "이미 종료된 임장 세션입니다.",
  "data": null,
  "timestamp": "2026-07-25T14:03:00+09:00"
}
```

---

#### 409 Conflict — 개인 임장 종료

`FIELD_PARTICIPANT_ALREADY_ENDED` code는 BE-016(STT)과 공유한다.
체크리스트 생성 API에서는 아래 전용 message를 사용한다.
(STT 기본 message: "임장을 종료한 뒤에는 음성 기록을 추가할 수 없습니다."와 혼동하지 않는다.)

```json
{
  "success": false,
  "code": "FIELD_PARTICIPANT_ALREADY_ENDED",
  "message": "임장을 종료한 뒤에는 체크리스트를 생성할 수 없습니다.",
  "data": null,
  "timestamp": "2026-07-25T14:03:00+09:00"
}
```

---

#### 500 Internal Server Error

예상하지 못한 서버 오류. AI 실패 자체는 fallback으로 처리되므로 이 응답의 일반 원인이 아니다.

```json
{
  "success": false,
  "code": "COMMON_INTERNAL_SERVER_ERROR",
  "message": "일시적인 오류가 발생했습니다.",
  "data": null,
  "timestamp": "2026-07-25T14:03:00+09:00"
}
```

---

### 프론트 처리

```
임장 시작 후 체크리스트 보기 선택
→ 내 체크리스트 조회 API 호출
→ checklist = null이면 생성 API 호출

체크리스트 생성 중
→ 로딩 상태 표시
→ 중복 생성 버튼 비활성화

CHECKLIST_GENERATE_SUCCESS / CHECKLIST_FALLBACK_GENERATE_SUCCESS
→ categories를 서버 반환 순서대로 표시

isFallback = true
→ 기본 체크리스트 안내 후 일반과 동일하게 사용

CHECKLIST_ALREADY_EXISTS
→ 오류 없이 기존 체크리스트 화면 표시

FIELD_PARTICIPANT_ALREADY_ENDED / FIELD_VISIT_ALREADY_ENDED
→ 읽기 전용 전환
```

---

## 내 체크리스트 조회

Method: GET
Progress: 완료
URI: /api/v1/studies/{studyId}/field-visit/checklist
담당자: 윤다인
연동여부: Yes

현재 스터디의 임장 세션에서 로그인 회원 본인에게 생성된 개인 체크리스트를 조회한다.

완료 수·전체 수, 동적 카테고리, 항목 제목·보조 설명, 완료 여부,
본인 현장 기록 수(`recordCount`)와 유형별 요약(`recordSummary`)을 반환한다.

범위: AI-002 읽기 전용 조회. 답변 저장·현장 기록 CRUD는 BE-015다.

---

### Request

- Request HTTP Method: `GET`
- 인증 필요: 필요
- Request Body: 없음

#### Request Header

```
Authorization: Bearer {accessToken}
```

#### Path Variable

```
GET /api/v1/studies/7/field-visit/checklist
```

| 필드 | 타입 | 필수 | 설명 |
| --- | --- | --- | --- |
| `studyId` | Long | Y | 내 체크리스트를 조회할 스터디 ID (1 이상) |

---

### 처리 기준

#### 1. 조회 권한

- 로그인 회원 ID는 Access Token에서 확인한다.
- 스터디가 존재하고 ACTIVE `study_member`여야 한다. 아니면 `CHECKLIST_ACCESS_DENIED`.
- 본인 `(session_id, member_id)` 체크리스트만 조회한다. 다른 참여자 체크리스트는 반환하지 않는다.

#### 2. 체크리스트 미생성·세션 미시작

- 세션이 아직 없으면 오류 대신 `sessionId = null`, `participantStatus = null`,
  `readOnly = false`, `checklist = null`을 반환한다.
- 세션은 있으나 본인 체크리스트가 없으면 `checklist = null`을 반환한다.
- 프론트는 `checklist = null`일 때 생성 API를 호출한다.
- GET은 `FIELD_VISIT_NOT_STARTED`를 던지지 않는다.

#### 3. 카테고리와 정렬

- `category`는 `checklist_item.category` 문자열 그대로다.
- 항목은 `displayOrder` 오름차순으로 정렬한 뒤 동일 카테고리로 묶는다.
- 카테고리 배열 순서는 각 카테고리 첫 항목의 `displayOrder` 순서를 따른다.

#### 4. 완료 상태

- `checklist_answer.is_completed`가 있으면 그대로 반영한다.
- 답변 행이 없으면 `isCompleted = false`, `completedAt = null`.
- `completedCount` / `totalCount`는 전체 및 카테고리 단위로 집계한다.

#### 5. recordCount·recordSummary

하드코딩 0이 아니다. `FieldRecordCountRepository`가 `field_record`를 집계한다.

- 작성자: 로그인 회원(`author_id = memberId`)
- 항목: 해당 `checklist_item_id`
- 삭제되지 않음: `deleted_at IS NULL`
- `recordCount`: 위 조건의 COUNT
- `recordSummary`: 동일 조건에서 `source_type`별 COUNT
  - `TEXT` → `textCount`
  - `PHOTO` → `photoCount`
  - `STT` → `sttCount`

다른 참여자 기록은 포함하지 않는다. 항목별 기록 목록 전체는 BE-015 현장 기록 API 범위다.

#### 6. readOnly

- `field_session.status = ENDED`이면 `readOnly = true`
- 본인 `field_participant.status = ENDED`이면 `readOnly = true`
- 그 외(세션·참여 진행 중)는 `readOnly = false`
- 세션이 아예 없으면 `readOnly = false`와 `checklist = null`

---

### Response

#### 200 OK — 체크리스트 존재

```json
{
  "success": true,
  "code": "CHECKLIST_DETAIL_SUCCESS",
  "message": "체크리스트 조회에 성공했습니다.",
  "data": {
    "studyId": 7,
    "sessionId": 100,
    "participantStatus": "IN_PROGRESS",
    "readOnly": false,
    "checklist": {
      "checklistId": 55,
      "isFallback": false,
      "generatedAt": "2026-07-25T14:03:00+09:00",
      "completedCount": 1,
      "totalCount": 3,
      "categories": [
        {
          "category": "교통",
          "completedCount": 1,
          "totalCount": 2,
          "items": [
            {
              "checklistItemId": 501,
              "title": "지하철역 접근성",
              "subtitle": "단지 출입구에서 승강장까지 실제 소요 시간을 확인해 주세요.",
              "displayOrder": 1,
              "isCompleted": true,
              "completedAt": "2026-07-25T14:15:00+09:00",
              "recordCount": 2,
              "recordSummary": {
                "textCount": 1,
                "photoCount": 1,
                "sttCount": 0
              }
            },
            {
              "checklistItemId": 502,
              "title": "출퇴근 혼잡",
              "subtitle": null,
              "displayOrder": 2,
              "isCompleted": false,
              "completedAt": null,
              "recordCount": 0,
              "recordSummary": {
                "textCount": 0,
                "photoCount": 0,
                "sttCount": 0
              }
            }
          ]
        },
        {
          "category": "단지환경",
          "completedCount": 0,
          "totalCount": 1,
          "items": [
            {
              "checklistItemId": 503,
              "title": "단지 내부 경사",
              "subtitle": "유모차와 휠체어 이동이 어려운 구간을 확인해 주세요.",
              "displayOrder": 3,
              "isCompleted": false,
              "completedAt": null,
              "recordCount": 1,
              "recordSummary": {
                "textCount": 0,
                "photoCount": 0,
                "sttCount": 1
              }
            }
          ]
        }
      ]
    }
  },
  "timestamp": "2026-07-25T14:20:00+09:00"
}
```

#### 200 OK — 체크리스트 미생성

```json
{
  "success": true,
  "code": "CHECKLIST_DETAIL_SUCCESS",
  "message": "체크리스트 조회에 성공했습니다.",
  "data": {
    "studyId": 7,
    "sessionId": 100,
    "participantStatus": "IN_PROGRESS",
    "readOnly": false,
    "checklist": null
  },
  "timestamp": "2026-07-25T14:05:00+09:00"
}
```

#### 200 OK — 세션 미시작

```json
{
  "success": true,
  "code": "CHECKLIST_DETAIL_SUCCESS",
  "message": "체크리스트 조회에 성공했습니다.",
  "data": {
    "studyId": 7,
    "sessionId": null,
    "participantStatus": null,
    "readOnly": false,
    "checklist": null
  },
  "timestamp": "2026-07-25T14:05:00+09:00"
}
```

---

### Response Field

#### 기본 정보

| 필드 | 타입 | 설명 |
| --- | --- | --- |
| `studyId` | Long | 스터디 ID |
| `sessionId` | Long \| null | 임장 세션 ID |
| `participantStatus` | String \| null | 본인 참여 상태(`IN_PROGRESS` / `ENDED` 등) |
| `readOnly` | Boolean | 읽기 전용 여부 |
| `checklist` | Object \| null | 체크리스트. 미생성이면 `null` |

#### `checklist`

| 필드 | 타입 | 설명 |
| --- | --- | --- |
| `checklistId` | Long | 체크리스트 ID |
| `isFallback` | Boolean | fallback 여부 |
| `generatedAt` | String | 생성 시각 |
| `completedCount` | Integer | 완료 항목 수 |
| `totalCount` | Integer | 전체 항목 수 |
| `categories` | Array | 동적 카테고리 목록 |

#### `categories[]`

| 필드 | 타입 | 설명 |
| --- | --- | --- |
| `category` | String | 카테고리 이름 |
| `completedCount` | Integer | 카테고리 내 완료 수 |
| `totalCount` | Integer | 카테고리 내 전체 수 |
| `items` | Array | 항목 목록 |

#### `items[]`

| 필드 | 타입 | 설명 |
| --- | --- | --- |
| `checklistItemId` | Long | 항목 ID |
| `title` | String | 제목 |
| `subtitle` | String \| null | 보조 설명 |
| `displayOrder` | Integer | 전체 표시 순서 |
| `isCompleted` | Boolean | 완료 여부 |
| `completedAt` | String \| null | 완료 시각 |
| `recordCount` | Integer | 본인·해당 항목·미삭제 `field_record` COUNT |
| `recordSummary` | Object | `textCount` / `photoCount` / `sttCount` |

---

### Exception

#### 401 Unauthorized

```json
{
  "success": false,
  "code": "AUTH_ACCESS_TOKEN_INVALID",
  "message": "로그인이 필요합니다.",
  "data": null,
  "timestamp": "2026-07-25T14:20:00+09:00"
}
```

---

#### 403 Forbidden — 조회 권한 없음

```json
{
  "success": false,
  "code": "CHECKLIST_ACCESS_DENIED",
  "message": "승인된 스터디 멤버만 체크리스트를 확인할 수 있습니다.",
  "data": null,
  "timestamp": "2026-07-25T14:20:00+09:00"
}
```

---

#### 404 Not Found — 스터디 없음

```json
{
  "success": false,
  "code": "STUDY_NOT_FOUND",
  "message": "스터디를 찾을 수 없습니다.",
  "data": null,
  "timestamp": "2026-07-25T14:20:00+09:00"
}
```

---

#### 500 Internal Server Error

```json
{
  "success": false,
  "code": "COMMON_INTERNAL_SERVER_ERROR",
  "message": "일시적인 오류가 발생했습니다.",
  "data": null,
  "timestamp": "2026-07-25T14:20:00+09:00"
}
```

---

### 프론트 처리

```
임장 지도에서 "체크리스트 보기" 선택
→ 내 체크리스트 조회 API 호출
→ 바텀시트에 완료 수/전체 수와 카테고리별 항목 표시

checklist = null
→ 개인 맞춤 체크리스트 생성 API 호출
→ 생성 응답 또는 재조회로 화면 구성

항목 표시
→ title 주 문구, subtitle 보조 설명
→ recordCount / recordSummary로 본인 기록 요약 표시

readOnly = false
→ 완료 체크·기록 버튼은 BE-015 API와 연동

readOnly = true
→ 조회만 허용, 완료 체크·기록 변경 비활성화
```

---

## 개인 맞춤 체크리스트 생성 상태 조회

Domain: Field Visit
Method: GET
Progress: 완료
URI: /api/v1/studies/{studyId}/field-visit/checklist/generate/status
담당자: 김윤석
연동여부: Yes

체크리스트 생성 요청의 진행 상태를 조회한다. 프론트는 생성 요청 후 이 API를 폴링해 진행률 UI를 표시한다.

### Request

- HTTP Method: `GET`
- URI: `/api/v1/studies/{studyId}/field-visit/checklist/generate/status`
- 인증 필요: 필요
- Request Body: 없음

#### Request Header

```
Authorization: Bearer {accessToken}
X-Checklist-Generation-Attempt-Id: {attemptId}   // 선택, 64자 이하
```

#### Path Variable

| 필드 | 타입 | 필수 | 설명 |
| --- | --- | --- | --- |
| `studyId` | Long | Y | 스터디 ID, 1 이상 |

### 처리 기준

- 요청자는 해당 스터디 임장에 접근 가능한 회원이어야 한다. 권한이 없으면 `403 CHECKLIST_ACCESS_DENIED`다.
- 임장 세션이 아직 시작되지 않았으면 오류가 아니라 `status=PENDING`, `progressRate=0`을 반환한다.
- `X-Checklist-Generation-Attempt-Id` 헤더를 보내면 해당 생성 시도의 진행 상태를 조회하고, 생략하면 본인 세션의 가장 최근 진행 상태를 반환한다.
- 진행 기록이 없으면 `status=PENDING`을 반환한다.
- `status`는 `PENDING`·`IN_PROGRESS`·`DONE`·`FAILED` 중 하나다.
- `progressStage`는 `PENDING`·`PREPARING`(10%)·`PERSONALIZATION`(30%)·`AI_GENERATION`(55%)·`CONTENT_READY`(80%)·`RESULT_SAVING`(90%)·`COMPLETED`(100%) 중 하나다.
- 실패한 시도는 `progressMessage`에 실패 안내 문구를 담는다.

### Response

#### 200 OK — 진행 중

```json
{
  "success": true,
  "code": "CHECKLIST_GENERATION_STATUS_SUCCESS",
  "message": "체크리스트 생성 상태 조회에 성공했습니다.",
  "data": {
    "attemptId": "550e8400-e29b-41d4-a716-446655440000",
    "status": "IN_PROGRESS",
    "progressRate": 55,
    "progressStage": "AI_GENERATION",
    "progressMessage": "맞춤 체크 항목을 만들고 있어요.",
    "updatedAt": "2026-08-06T15:00:03+09:00"
  },
  "timestamp": "2026-08-06T15:00:05+09:00"
}
```

#### 200 OK — 진행 기록 없음

```json
{
  "success": true,
  "code": "CHECKLIST_GENERATION_STATUS_SUCCESS",
  "message": "체크리스트 생성 상태 조회에 성공했습니다.",
  "data": {
    "attemptId": null,
    "status": "PENDING",
    "progressRate": 0,
    "progressStage": "PENDING",
    "progressMessage": "체크리스트 생성 요청을 기다리고 있어요.",
    "updatedAt": null
  },
  "timestamp": "2026-08-06T15:00:05+09:00"
}
```

#### Response Field

| 필드 | 타입 | 설명 |
| --- | --- | --- |
| `attemptId` | String | 생성 시도 ID, 진행 기록이 없고 헤더도 없으면 `null` |
| `status` | String | `PENDING` / `IN_PROGRESS` / `DONE` / `FAILED` |
| `progressRate` | Integer | 진행률(0~100) |
| `progressStage` | String | 진행 단계 |
| `progressMessage` | String | 사용자 안내 문구, 실패 시 실패 문구 |
| `updatedAt` | String | 마지막 갱신 시각, 진행 기록이 없으면 `null` |

### Exception

- `400 COMMON_INVALID_REQUEST` — `studyId`가 1 미만이거나 attempt 헤더가 64자 초과
- `401 AUTH_ACCESS_TOKEN_INVALID`
- `403 CHECKLIST_ACCESS_DENIED` — 임장 접근 권한 없음
- `404 STUDY_NOT_FOUND` — 스터디 없음

---

## [내부] 체크리스트 AI 생성 (Spring → FastAPI)

Method: POST
Progress: 구현 완료 (AI-002)
URI: /internal/v1/checklists/generate
담당자: 윤다인
연동여부: Yes (Spring Boot 전용)

Android·Frontend가 직접 호출하는 공개 API가 아니다.
Spring Boot만 FastAPI에 호출하며, FastAPI는 DB·JWT·스터디 권한을 처리하지 않는다.

FastAPI는 Pydantic으로 요청·응답을 검증하고, LLM 출력은 JSON Schema/Pydantic으로 재검증한다.
Provider는 SSAFY GMS Gemini(`AI_PROVIDER=gms-gemini`, 기본 모델 `gemini-3.5-flash`)다.
기술적 실패 시 FastAPI가 5xx 등으로 실패하면 Spring이 fallback을 적용한다.

---

### Request

- Request HTTP Method: `POST`
- 인증: 공개 JWT 없음 (내부 네트워크 호출)
- Content-Type: `application/json`

#### Request Body 예시

```json
{
  "personalization": {
    "member": {
      "memberPurpose": "RESIDENCE",
      "maritalStatus": "SINGLE",
      "hasVehicle": true,
      "hasChildren": false,
      "priorities": ["TRANSPORT", "SAFETY", "NOISE"],
      "ageGroup": "THIRTIES"
    },
    "apartment": {
      "apartmentId": 15,
      "name": "래미안 테스트",
      "address": "서울특별시 강남구 테헤란로 1",
      "districtName": "강남구",
      "dongName": "역삼동",
      "householdCount": 1200,
      "completionYearMonth": "201503",
      "parkingSpaceCount": 1500
    },
    "study": {
      "studyId": 7,
      "studyPurpose": "RESIDENCE",
      "goal": "실거주 적합성을 함께 확인"
    }
  }
}
```

추가 필드는 Pydantic `extra=forbid`로 거부한다.

---

### Response

#### 200 OK

```json
{
  "items": [
    {
      "category": "교통",
      "title": "지하철역 접근성",
      "subtitle": "단지 출입구에서 승강장까지 실제 소요 시간을 확인해 주세요.",
      "displayOrder": 1
    },
    {
      "category": "소음",
      "title": "도로 소음",
      "subtitle": null,
      "displayOrder": 2
    }
  ]
}
```

| 필드 | 타입 | 설명 |
| --- | --- | --- |
| `items` | Array | 1개 이상 |
| `items[].category` | String | 1~30자, blank 금지 |
| `items[].title` | String | blank 금지 |
| `items[].subtitle` | String \| null | 보조 설명 |
| `items[].displayOrder` | Integer | 1 이상, items 내 중복 금지 |

---

### 운영 설정 (확인된 값)

| 항목 | 값 |
| --- | --- |
| Spring base URL | `FIELD_VISIT_AI_BASE_URL` (로컬 기본 `http://localhost:8000`) |
| Spring generate path | `FIELD_VISIT_AI_GENERATE_PATH` (기본 `/internal/v1/checklists/generate`) |
| Spring connect timeout | 3s |
| Spring read timeout | 20s |
| Spring retry | 0 |
| FastAPI health | `GET /health` |
| GMS model env | `AI_MODEL` (예: `gemini-3.5-flash`) |

API Key·JWT·DB 비밀번호 등 Secret은 이 문서에 기록하지 않는다.

---


## [내부] 체크리스트 itemCode 선택 (Spring → FastAPI)

Method: POST
Progress: 구현 중 (AI-002-1)
URI: /internal/v1/checklists/select
담당자: 윤다인
연동여부: Yes (Spring Boot 전용)

Android/Frontend가 직접 호출하는 공개 API가 아니다.
호출 주체는 Spring Boot, 제공 주체는 FastAPI다.
인증·네트워크 범위는 기존 내부 API 정책(`POST /internal/v1/checklists/generate`)을 따른다.
FastAPI는 DB·JWT를 다루지 않는다.

`CHECKLIST_CATALOG_SELECTION_ENABLED=true`일 때만 Spring이 이 경로를 호출한다.
false이면 기존 generate 경로만 사용한다.

---

### Request

- Request HTTP Method: `POST`
- 인증: 회원 JWT 없음 (내부 네트워크 호출)
- Content-Type: `application/json`

#### Request Body 예시

```json
{
  "selectionVersion": "v3-select-1",
  "targetItemCount": 10,
  "mappedPurpose": "LIVE",
  "selectedPriorities": [
    "TRANSPORTATION",
    "SAFETY"
  ],
  "shortlist": [
    {
      "itemCode": "PED_018",
      "categoryCode": "PED",
      "title": "단지 안 보행 연속성",
      "priorityTags": [
        "WALKABILITY",
        "SAFETY",
        "CONVENIENCE"
      ],
      "conditionTags": [
        "WALK",
        "CHILD",
        "ELDERLY",
        "STROLLER",
        "SAFETY"
      ],
      "serverScore": 200,
      "isCommonCore": true
    },
    {
      "itemCode": "ENV_001",
      "categoryCode": "ENV",
      "title": "외부와 단지 안 소음 차이",
      "priorityTags": [
        "NOISE"
      ],
      "conditionTags": [
        "QUIET",
        "LIVE",
        "INVEST"
      ],
      "serverScore": 199,
      "isCommonCore": true
    },
    {
      "itemCode": "SUM_001",
      "categoryCode": "SUM",
      "title": "가장 큰 장점",
      "priorityTags": [
        "TRANSPORTATION",
        "SAFETY",
        "EDUCATION",
        "CONVENIENCE",
        "WALKABILITY",
        "GREEN_SPACE",
        "PARKING",
        "NOISE"
      ],
      "conditionTags": [
        "ALL"
      ],
      "serverScore": 198,
      "isCommonCore": true
    },
    {
      "itemCode": "SUM_002",
      "categoryCode": "SUM",
      "title": "가장 큰 우려",
      "priorityTags": [
        "TRANSPORTATION",
        "SAFETY",
        "EDUCATION",
        "CONVENIENCE",
        "WALKABILITY",
        "GREEN_SPACE",
        "PARKING",
        "NOISE"
      ],
      "conditionTags": [
        "ALL"
      ],
      "serverScore": 197,
      "isCommonCore": true
    },
    {
      "itemCode": "TRN_001",
      "categoryCode": "TRN",
      "title": "역까지 실제 도보 시간",
      "priorityTags": [
        "TRANSPORTATION"
      ],
      "conditionTags": [
        "TRANSIT",
        "COMMUTE"
      ],
      "serverScore": 196,
      "isCommonCore": false
    },
    {
      "itemCode": "TRN_002",
      "categoryCode": "TRN",
      "title": "승강장까지 추가 이동 시간",
      "priorityTags": [
        "TRANSPORTATION"
      ],
      "conditionTags": [
        "TRANSIT",
        "COMMUTE"
      ],
      "serverScore": 195,
      "isCommonCore": false
    },
    {
      "itemCode": "TRN_003",
      "categoryCode": "TRN",
      "title": "역 접근 편의",
      "priorityTags": [
        "TRANSPORTATION",
        "WALKABILITY"
      ],
      "conditionTags": [
        "TRANSIT",
        "STROLLER",
        "ELDERLY",
        "WHEELCHAIR",
        "ACCESSIBILITY"
      ],
      "serverScore": 194,
      "isCommonCore": false
    },
    {
      "itemCode": "SAF_001",
      "categoryCode": "SAF",
      "title": "CCTV와 가로등 배치",
      "priorityTags": [
        "SAFETY",
        "WALKABILITY"
      ],
      "conditionTags": [
        "SAFETY",
        "SINGLE",
        "CHILD",
        "ELDERLY"
      ],
      "serverScore": 193,
      "isCommonCore": false
    },
    {
      "itemCode": "EXT_001",
      "categoryCode": "EXT",
      "title": "출입구 이용 편의",
      "priorityTags": [
        "WALKABILITY",
        "TRANSPORTATION",
        "CONVENIENCE"
      ],
      "conditionTags": [
        "LIVE",
        "INVEST",
        "LEARN"
      ],
      "serverScore": 192,
      "isCommonCore": true
    },
    {
      "itemCode": "EXT_003",
      "categoryCode": "EXT",
      "title": "차량·보행 출입 분리",
      "priorityTags": [
        "SAFETY",
        "WALKABILITY",
        "PARKING"
      ],
      "conditionTags": [
        "SAFETY",
        "CHILD",
        "ELDERLY",
        "CAR"
      ],
      "serverScore": 191,
      "isCommonCore": true
    },
    {
      "itemCode": "GRN_001",
      "categoryCode": "GRN",
      "title": "공원까지 실제 도보 시간",
      "priorityTags": [
        "GREEN_SPACE",
        "WALKABILITY"
      ],
      "conditionTags": [
        "PARK",
        "CHILD",
        "ELDERLY",
        "PET"
      ],
      "serverScore": 190,
      "isCommonCore": false
    },
    {
      "itemCode": "CON_001",
      "categoryCode": "CON",
      "title": "편의점 실제 거리",
      "priorityTags": [
        "CONVENIENCE"
      ],
      "conditionTags": [
        "CONVENIENCE",
        "SINGLE",
        "ELDERLY"
      ],
      "serverScore": 189,
      "isCommonCore": false
    }
  ]
}
```

| 필드 | 타입 | 필수 | 설명 |
| --- | --- | --- | --- |
| `selectionVersion` | String | Y | 선택 계약 버전 (예: `v3-select-1`) |
| `targetItemCount` | Integer | Y | 최종 문항 수. 8~18 |
| `mappedPurpose` | String \| null | N | 카탈로그 목적 코드 (`LIVE`/`INVEST`/`LEARN`) |
| `selectedPriorities` | String[] | Y | 카탈로그 우선순위 코드 목록 |
| `shortlist` | Array | Y | Spring이 점수·필터한 후보 |
| `shortlist[].itemCode` | String | Y | 후보 코드 |
| `shortlist[].categoryCode` | String | Y | 카테고리 코드 |
| `shortlist[].title` | String | Y | 후보 제목 |
| `shortlist[].priorityTags` | String[] | Y | 우선순위 태그 |
| `shortlist[].conditionTags` | String[] | Y | 조건 태그(자유 태그) |
| `shortlist[].serverScore` | Integer | Y | Spring 서버 점수 |
| `shortlist[].isCommonCore` | Boolean | Y | 공통 핵심 여부 |

추가 필드는 Pydantic `extra=forbid`로 거부한다.

---

### Response

#### 200 OK

```json
{
  "itemCodes": [
    "PED_018",
    "ENV_001",
    "SUM_001",
    "TRN_001",
    "TRN_003",
    "SAF_001",
    "EXT_001",
    "EXT_003",
    "GRN_001",
    "CON_001"
  ]
}
```

| 필드 | 타입 | 설명 |
| --- | --- | --- |
| `itemCodes` | String[] | 선택된 코드. 배열 순서가 최종 `displayOrder` |
| `itemCodes` 개수 | Integer | `targetItemCount`와 일치, 8~18 |
| 중복 | - | 금지 |
| shortlist 밖 코드 | - | 금지 |

---

### 검증·오류

- FastAPI·Spring 모두 `targetItemCount` 8~18, 중복 금지, shortlist 부분집합을 검증한다.
- 잘못된 모델 결과는 Spring이 저장하지 않는다.
- FastAPI 기술 실패(연결·timeout·5xx·빈/잘못된 JSON) 시 Spring catalog fallback.
- catalog fallback도 target 개수를 만들지 못하면 기존 hardcoded fallback.
- shortlist 크기가 `targetItemCount`보다 작으면 FastAPI를 호출하지 않고 catalog fallback한다.

### 운영 설정 (확인된 값)

| 항목 | 값 |
| --- | --- |
| Spring base URL | `FIELD_VISIT_AI_BASE_URL` (로컬 기본 `http://localhost:8000`) |
| Spring select path | `FIELD_VISIT_AI_SELECT_PATH` (기본 `/internal/v1/checklists/select`) |
| feature flag | `CHECKLIST_CATALOG_SELECTION_ENABLED` (기본 `false`) |
| Spring connect timeout | 3s |
| Spring read timeout | 20s |
| Spring retry | 0 |

API Key·JWT·DB 비밀번호 등 Secret은 이 문서에 기록하지 않는다.

---

## 참여자별 임장 상태 조회

Method: GET
Progress: 완료
URI: /api/v1/studies/{studyId}/field-visit/participants
담당자: 윤다인
연동여부: No

임장 세션에 고정된 참여 후보 전원의 임장 진행 상태·시작·종료 시각·체류시간·종료 사유를 조회한다.

임장 진행 화면에서 함께 참여한 멤버의 현재 상태를 표시하고, 스터디장이 전체 마감 전 미종료 참여자를 확인하는 데 사용한다.

---

### Request

- Request HTTP Method: `GET`
- 인증 필요: 필요
- Request Body: 없음

#### Request Header

```
Authorization: Bearer {accessToken}
```

#### Path Variable

```
GET /api/v1/studies/7/field-visit/participants
```

| 필드 | 타입 | 필수 | 설명 |
| --- | --- | --- | --- |
| `studyId` | Long | Y | 참여자 임장 상태를 조회할 스터디 ID |

---

### 처리 기준

#### 1. 조회 권한

- 로그인한 회원 ID는 Access Token에서 확인한다.
- 요청자는 스터디장이거나 `study_member.status = ACTIVE`인 승인 멤버여야 한다.
- 승인되지 않은 회원에게는 참여자 닉네임, 캐릭터, 임장 상태를 노출하지 않는다.
- 완료된 스터디의 기존 승인 멤버는 읽기 전용으로 조회할 수 있다.

#### 2. 세션 미시작 처리

- `field_session`이 없으면 오류가 아닌 `status = NOT_STARTED`와 빈 `participants`를 반환한다.
- 세션이 없으므로 `sessionId = null`, 참여자 수와 진행·종료 수는 0이다.

#### 3. 참여자 명단

- 참여자 목록은 `field_visit_candidate`에 저장된 세션 최초 시작 시점의 고정 명단을 기준으로 한다.
- 세션 시작 이후 승인된 회원은 현재 임장 참여자 목록에 포함하지 않는다.
- 스터디 멤버에서 이후 제거되었더라도 세션의 고정 후보와 이미 생성된 참여 기록은 임장·리포트 무결성을 위해 유지한다.
- 목록에는 `field_participant`가 생성된 참여자와, 아직 GPS 시작을 하지 않은 고정 후보를 파생 상태 `NOT_JOINED`로 함께 반환한다.

#### 4. 상태·체류시간

- 개인 상태는 `NOT_JOINED`, `IN_PROGRESS`, `ENDED`로 반환한다.
- `NOT_JOINED`는 DB 저장값이 아니라 아직 `field_participant` 행이 없는 고정 후보를 위한 응답 상태다.
- 진행 중 참여자의 `stayDurationSec`는 조회 시각과 `startedAt` 차이로 계산한다.
- 종료된 참여자는 저장된 `stayDurationSec`를 반환한다.
- 종료 사유는 `SELF_ENDED`, `LEADER_FORCED`, `SESSION_ENDED` 중 하나다.
- 아직 종료되지 않았으면 `endedAt`, `endReason`은 `null`이다.

#### 5. 정렬과 요약

- 스터디장을 목록 첫 번째에 표시하고, 나머지는 개인 임장 시작 시각 오름차순으로 정렬한다.
- 아직 시작하지 않은 참여자는 시작한 참여자 뒤에 표시한다.
- `inProgressCount`, `endedCount`, `notJoinedCount`를 함께 반환한다.
- `isMe`, `isLeader`를 반환해 화면 배지와 관리 버튼에 사용한다.

#### 6. 종료 요청 가능 여부

- `canRequestFinish`는 로그인 회원이 스터디장이고 세션이 진행 중이며 해당 참여자가 아직 종료되지 않았을 때 `true`다.
- 본인 항목에는 스터디장이라도 종료 요청을 보내지 않고 개인 종료 API를 사용한다.
- 이미 종료된 참여자나 아직 시작하지 않은 참여자에게는 종료 요청을 보낼 수 없다.

---

### Response

#### 200 OK — 임장 진행 중

```json
{
  "success": true,
  "code": "FIELD_VISIT_PARTICIPANTS_SUCCESS",
  "message": "참여자 임장 상태 조회에 성공했습니다.",
  "data": {
    "studyId": 7,
    "sessionId": 100,
    "status": "IN_PROGRESS",
    "participantCount": 4,
    "inProgressCount": 2,
    "endedCount": 1,
    "notJoinedCount": 1,
    "participants": [
      {
        "participantId": 301,
        "memberId": 42,
        "nickname": "루돌푸",
        "profileImageUrl": null,
        "selectedCharacterId": "PALBANG",
        "role": "LEADER",
        "status": "IN_PROGRESS",
        "startedAt": "2026-07-25T14:00:00+09:00",
        "endedAt": null,
        "endReason": null,
        "stayDurationSec": 1830,
        "isMe": false,
        "isLeader": true,
        "canRequestFinish": true
      },
      {
        "participantId": 302,
        "memberId": 51,
        "nickname": "집콩이",
        "profileImageUrl": "<https://cdn.example.com/profiles/51.jpg>",
        "selectedCharacterId": "JIPKONG",
        "role": "MEMBER",
        "status": "ENDED",
        "startedAt": "2026-07-25T14:01:00+09:00",
        "endedAt": "2026-07-25T14:40:00+09:00",
        "endReason": "SELF_ENDED",
        "stayDurationSec": 2340,
        "isMe": false,
        "isLeader": false,
        "canRequestFinish": false
      },
      {
        "participantId": 303,
        "memberId": 63,
        "nickname": "성동구탐방러",
        "profileImageUrl": null,
        "selectedCharacterId": "DURI",
        "role": "MEMBER",
        "status": "IN_PROGRESS",
        "startedAt": "2026-07-25T14:05:00+09:00",
        "endedAt": null,
        "endReason": null,
        "stayDurationSec": 1530,
        "isMe": true,
        "isLeader": false,
        "canRequestFinish": false
      },
      {
        "participantId": null,
        "memberId": 72,
        "nickname": "옥수초보",
        "profileImageUrl": null,
        "selectedCharacterId": "PALBANG",
        "role": "MEMBER",
        "status": "NOT_JOINED",
        "startedAt": null,
        "endedAt": null,
        "endReason": null,
        "stayDurationSec": 0,
        "isMe": false,
        "isLeader": false,
        "canRequestFinish": false
      }
    ]
  },
  "timestamp": "2026-07-25T14:30:30+09:00"
}
```

#### 200 OK — 세션 미시작

```json
{
  "success": true,
  "code": "FIELD_VISIT_PARTICIPANTS_SUCCESS",
  "message": "참여자 임장 상태 조회에 성공했습니다.",
  "data": {
    "studyId": 7,
    "sessionId": null,
    "status": "NOT_STARTED",
    "participantCount": 0,
    "inProgressCount": 0,
    "endedCount": 0,
    "notJoinedCount": 0,
    "participants": []
  },
  "timestamp": "2026-07-25T13:00:00+09:00"
}
```

---

### Response Field

| 필드 | 타입 | 설명 |
| --- | --- | --- |
| `studyId` | Long | 스터디 ID |
| `sessionId` | Long | null | 임장 세션 ID |
| `status` | String | 세션 상태. `NOT_STARTED`, `IN_PROGRESS`, `ENDED` |
| `participantCount` | Integer | 세션 고정 참여 후보 인원 수 |
| `inProgressCount` | Integer | 현재 진행 중인 인원 수 |
| `endedCount` | Integer | 종료한 인원 수 |
| `notJoinedCount` | Integer | 아직 GPS 시작하지 않은 인원 수 |
| `participants` | Array | 참여자 상태 목록 |

#### `participants[]`

| 필드 | 타입 | 설명 |
| --- | --- | --- |
| `participantId` | Long | null | 개인 임장 참여 ID. 미시작이면 `null` |
| `memberId` | Long | 회원 ID |
| `nickname` | String | 닉네임 |
| `profileImageUrl` | String | null | 프로필 이미지 URL |
| `selectedCharacterId` | String | 선택 캐릭터 ID |
| `role` | String | 스터디 역할. `LEADER`, `MEMBER` |
| `status` | String | `NOT_JOINED`, `IN_PROGRESS`, `ENDED` |
| `startedAt` | String | null | 개인 임장 시작 시각 |
| `endedAt` | String | null | 개인 임장 종료 시각 |
| `endReason` | String | null | 종료 사유 |
| `stayDurationSec` | Integer | 체류시간, 초 단위 |
| `isMe` | Boolean | 로그인 회원인지 여부 |
| `isLeader` | Boolean | 스터디장인지 여부 |
| `canRequestFinish` | Boolean | 스터디장이 종료 요청을 보낼 수 있는지 여부 |

---

### Exception

#### 401 Unauthorized

```json
{
  "success": false,
  "code": "AUTH_ACCESS_TOKEN_INVALID",
  "message": "로그인이 필요합니다.",
  "data": null,
  "timestamp": "2026-07-25T14:30:30+09:00"
}
```

---

#### 403 Forbidden — 조회 권한 없음

```json
{
  "success": false,
  "code": "FIELD_VISIT_PARTICIPANTS_ACCESS_DENIED",
  "message": "승인된 스터디 멤버만 참여자 상태를 확인할 수 있습니다.",
  "data": null,
  "timestamp": "2026-07-25T14:30:30+09:00"
}
```

---

#### 404 Not Found — 스터디 없음

```json
{
  "success": false,
  "code": "STUDY_NOT_FOUND",
  "message": "스터디를 찾을 수 없습니다.",
  "data": null,
  "timestamp": "2026-07-25T14:30:30+09:00"
}
```

---

#### 500 Internal Server Error

```json
{
  "success": false,
  "code": "COMMON_INTERNAL_SERVER_ERROR",
  "message": "일시적인 오류가 발생했습니다.",
  "data": null,
  "timestamp": "2026-07-25T14:30:30+09:00"
}
```

---

### 프론트 처리

```
임장 진행 화면 진입 또는 참여자 영역 새로고침
→ 참여자별 임장 상태 조회 API 호출
→ 참여자를 역할·상태별로 표시

status = IN_PROGRESS
→ 진행 중 배지와 실시간 체류시간 표시

status = ENDED
→ 종료 배지와 종료 사유·확정 체류시간 표시

status = NOT_JOINED
→ "아직 임장을 시작하지 않았어요" 표시

canRequestFinish = true
→ 스터디장에게 해당 참여자의 종료 요청 버튼 표시

세션 전체 상태 = ENDED
→ 참여자 목록은 읽기 전용으로 유지
→ 리포트 생성 상태 화면으로 이동할 수 있는 버튼 표시
```

---

## 알림 목록 조회

Domain: Notification
Method: GET
Progress: 진행 중
URI: /api/v1/notifications
담당자: 김윤석
연동여부: No

로그인 회원에게 생성된 스터디·임장·리포트·커뮤니티·MVP 메시지·시스템 알림을 미읽음 최신순, 읽음 최신순으로 조회한다.

로그인한 회원에게 발송된 서비스 알림 목록을 조회한다.

### Request

- HTTP Method: `GET`
- URI: `/api/v1/notifications`
- 인증 필요: 필요
- Request Body: 없음

#### Query Parameter

```
GET /api/v1/notifications?unreadOnly=false&cursor={nextCursor}&size=10
```

| 필드 | 타입 | 필수 | 기본값 | 설명 |
| --- | --- | --- | --- | --- |
| `unreadOnly` | Boolean | N | `false` | 읽지 않은 알림만 조회 |
| `cursor` | String | N | 없음 | 서버가 반환한 다음 조회용 URL-safe 불투명 커서 |
| `size` | Integer | N | `10` | 조회 개수, 1~100 |

### 알림 category

```
STUDY / FIELD / REPORT / COMMUNITY / MESSAGE / SYSTEM
```

기존 `SERVICE`, `ADVERTISEMENT` 값은 사용하지 않는다. 광고 수신 동의와 알림 화면 category는 별개다.

### 알림 type

```
STUDY_APPLICATION_SUBMITTED
STUDY_APPLICATION_APPROVED
STUDY_APPLICATION_REJECTED
MEMBER_FOLLOWED
SCHEDULE_CHANGED
D1
REPORT_COMPLETED
FIELD_VISIT_STARTED
FIELD_END_REQUEST
MESSAGE
```

#### Request Header

```
Authorization: Bearer {accessToken}
```

### 처리 기준

- `notification.recipient_id`가 로그인 회원 ID인 알림만 반환한다.
- 최초 조회 시점의 읽음 상태를 기준으로 미읽음 그룹을 먼저 반환하고, 이후 읽음 그룹을 반환한다.
- 각 그룹은 `sent_at DESC, id DESC`로 정렬한다.
- `cursor`에는 스냅샷 최대 ID·시각·필터·현재 그룹·마지막 정렬 키가 서명되어 있으며 서버만 해석한다.
- `unreadOnly=true`이면 `is_read=false`인 알림만 반환한다.
- 전체 미읽음 알림 수를 `unreadCount`로 반환한다.
- `actor_id`가 있으면 발신자 공개 프로필 요약을 반환한다.
- MVP 메시지는 `category=MESSAGE`, `type=MESSAGE`이며 정식 쪽지방으로 이동하지 않는다.
- `targetId`는 주 대상 ID, `targetSubId`는 일정·댓글 등 보조 대상 ID다.
- 대상이 삭제·숨김·접근 불가이면 `targetAvailable=false`로 계산해 반환한다.
- 동일 이벤트는 `idempotencyKey`로 중복 생성하지 않는다.
- Push 실패 여부와 관계없이 서버에 저장된 알림은 조회할 수 있다.
- 기존 `DIRECT_MESSAGE_RECEIVED`, `DIRECT_MESSAGE_ROOM`, 쪽지방 이동 정책은 삭제한다.

#### 이 문서에서 추가한 서비스 알림 계약

| type | category | 수신자 | target |
| --- | --- | --- | --- |
| `STUDY_APPLICATION_SUBMITTED` | `STUDY` | 스터디장 | `STUDY_MANAGE(studyId)` |
| `STUDY_APPLICATION_APPROVED` | `STUDY` | 신청자 | `STUDY_DETAIL(studyId)` |
| `STUDY_APPLICATION_REJECTED` | `STUDY` | 신청자 | `STUDY_DETAIL(studyId)` |
| `MEMBER_FOLLOWED` | `COMMUNITY` | 팔로우 대상 회원 | `MEMBER_PROFILE(followerId)` |
| `FIELD_VISIT_STARTED` | `FIELD` | 시작자 외 고정 참여 후보 | `FIELD_VISIT(studyId, sessionId)` |

FCM data에는 공통으로 `notificationType`, `targetScreen`, `targetId`, `notificationId`를 문자열로 담는다. `FIELD_VISIT_STARTED`에는 `targetSubId=sessionId`도 담는다. 앱 내 알림은 서비스 알림 동의 여부와 무관하게 저장하며, FCM은 원 업무 커밋 후 동의한 수신자의 등록 기기에만 전송한다.

### 추가 상세 처리 기준

- 로그인한 회원 ID는 Access Token에서 확인한다.
- 회원 상태가 `ACTIVE`인 경우에만 알림 목록을 조회할 수 있다.
- 다른 회원의 알림은 조회할 수 없다.

---

- `unreadOnly = true`이면 스냅샷 시점에 읽지 않았던 알림만 반환한다.
- `unreadOnly = false`이면 읽음 여부와 관계없이 반환한다.
- 현재 읽지 않은 전체 알림 수를 `unreadCount`로 함께 반환한다.
- 읽지 않은 알림이 없으면 `unreadCount = 0`으로 반환한다.

---

- 첫 요청에서 `cursor`가 없으면 조회 시점의 최대 알림 ID와 시각으로 스냅샷을 생성한다.
- 스냅샷 당시 미읽음이었던 알림을 `sent_at DESC, id DESC`로 모두 반환한 뒤, 읽음 알림을 같은 기준으로 반환한다.
- 조회 도중 읽음 처리된 알림은 `read_at > snapshotAt`이면 기존 스냅샷의 미읽음 그룹에 유지한다.
- 조회 도중 생성된 알림은 `id > snapshotMaxId`이므로 기존 스냅샷에서 제외하고 새 조회에 반영한다.
- `cursor`가 전달되면 커서에 보존된 그룹과 마지막 `sentAt/id` 다음부터 조회한다.
- 요청한 `size`보다 한 건 더 조회하여 다음 데이터 존재 여부를 확인한다.
- 실제 응답에는 최대 `size`개의 알림만 반환한다.
- 다음 데이터가 존재하면 `hasNext = true`로 반환한다.
- `hasNext = true`이면 현재 스냅샷의 다음 위치를 나타내는 문자열을 `nextCursor`로 반환한다.
- 다음 데이터가 없으면 `hasNext = false`, `nextCursor = null`로 반환한다.

---

- 알림 생성 시 기본값은 `isRead = false`다.
- 사용자가 알림을 선택하면 알림 한 건 읽음 처리 API를 호출한다.

```
PATCH /api/v1/notifications/{notificationId}/read
```

- 사용자가 전체 읽음을 선택하면 알림 전체 읽음 처리 API를 호출한다.

```
PATCH /api/v1/notifications/read-all
```

- 읽지 않은 알림은 `readAt = null`로 반환한다.
- 읽음 처리된 알림은 실제 읽음 처리 시각을 `readAt`으로 반환한다.

---

- FCM 토큰 등록 여부와 알림 수신 동의는 별도로 관리한다.
- 수신 동의가 없는 알림은 FCM Push 발송 대상에서 제외한다.
- 서비스 내 알림함에 저장할지 여부는 알림 정책에 따라 구분한다.
- 스터디 가입 신청·결정, 신규 팔로우, 임장 실제 시작, 일정 변경, 종료 요청, 리포트 완료와 같은 핵심 서비스 알림은 사용자 확인을 위해 알림함에 저장한다.
- Android 알림 권한이 거부되어 Push가 표시되지 않더라도 서버 알림함 데이터는 조회할 수 있다.

---

- 연결 대상이 삭제되거나 접근할 수 없는 경우 `targetAvailable = false`로 반환한다.
- 접근 가능한 경우 `targetAvailable = true`로 반환한다.
- 접근할 수 없는 대상의 ID를 이용해 프론트가 상세 API를 반복 호출하지 않도록 한다.

---

- 서버는 알림 유형에 따라 사용자에게 표시할 제목과 내용을 생성한다.
- 닉네임, 스터디명, 아파트명 등 필요한 정보를 알림 생성 시점에 문자열로 저장할 수 있다.
- 연결된 스터디나 회원 정보가 이후 변경돼도 과거 알림 문구는 생성 당시 내용을 유지할 수 있다.
- 알림 목록에는 FCM 토큰과 내부 민감 정보가 포함되지 않는다.

---

- 알림은 회원 활동과 서비스 상태 확인을 위한 데이터이므로 일정 기간 보관할 수 있다.
- 읽음 처리만으로 알림 데이터를 삭제하지 않는다.
- 알림 목록이 없으면 오류가 아닌 빈 배열을 반환한다.

---

### Response

#### 200 OK

```json
{
  "success": true,
  "code": "NOTIFICATION_LIST_SUCCESS",
  "message": "알림 목록 조회에 성공했습니다.",
  "data": {
    "content": [
      {
        "notificationId": 81,
        "category": "REPORT",
        "type": "REPORT_COMPLETED",
        "title": "AI 임장 리포트가 완성됐어요",
        "body": "래미안 옥수 리버젠 임장 리포트가 생성되었습니다.",
        "actor": null,
        "isRead": false,
        "readAt": null,
        "targetScreen": "REPORT_DETAIL",
        "targetId": 48,
        "targetSubId": null,
        "targetAvailable": true,
        "sentAt": "2026-07-24T13:20:00+09:00"
      },
      {
        "notificationId": 80,
        "category": "MESSAGE",
        "type": "MESSAGE",
        "title": "새로운 메시지가 도착했어요",
        "body": "다음 임장도 같이 참여해요!",
        "actor": {
          "memberId": 12,
          "nickname": "옥수탐방러",
          "profileImageUrl": null,
          "selectedCharacterId": "PALBANG"
        },
        "isRead": false,
        "readAt": null,
        "targetScreen": "MEMBER_PROFILE",
        "targetId": 12,
        "targetSubId": null,
        "targetAvailable": true,
        "sentAt": "2026-07-24T12:40:00+09:00"
      },
      {
        "notificationId": 76,
        "category": "STUDY",
        "type": "SCHEDULE_CHANGED",
        "title": "임장 일정이 변경됐어요",
        "body": "옥수동 주말 임장의 일정이 변경되었습니다.",
        "actor": {
          "memberId": 7,
          "nickname": "집보는다람쥐",
          "profileImageUrl": null,
          "selectedCharacterId": "JIPKONG"
        },
        "isRead": true,
        "readAt": "2026-07-24T10:00:00+09:00",
        "targetScreen": "STUDY_DETAIL",
        "targetId": 10,
        "targetSubId": 7,
        "targetAvailable": true,
        "sentAt": "2026-07-24T09:30:00+09:00"
      }
    ],
    "unreadCount": 2,
    "nextCursor": "eyJ2IjoxLCJzbmFwc2hvdE1heElkIjo4MX0.example-signature",
    "hasNext": true
  },
  "timestamp": "2026-07-24T14:10:00+09:00"
}
```

### Response Field 변경

```
content → body
createdAt → sentAt
notification.member_id 설명 → notification.recipient_id
```

MVP 메시지 알림에서는 `actor`를 반드시 반환한다.

### Exception

- `400 NOTIFICATION_CURSOR_INVALID`
- `400 COMMON_INVALID_REQUEST`: size 범위 오류
- `401 AUTH_ACCESS_TOKEN_INVALID`
- `500 COMMON_INTERNAL_SERVER_ERROR`

### 추가 Exception 예시

#### 400 Bad Request — 잘못된 커서

```json
{
  "success": false,
  "code": "NOTIFICATION_CURSOR_INVALID",
  "message": "알림 목록 커서가 올바르지 않습니다.",
  "data": {
    "field": "cursor",
    "reason": "커서가 유효하지 않거나 요청 조건과 일치하지 않습니다."
  },
  "timestamp": "2026-07-22T10:30:00+09:00"
}
```

---

#### 400 Bad Request — 잘못된 조회 개수

```json
{
  "success": false,
  "code": "COMMON_INVALID_REQUEST",
  "message": "입력값을 확인해 주세요.",
  "data": {
    "field": "size",
    "reason": "조회 개수는 1 이상 100 이하이어야 합니다."
  },
  "timestamp": "2026-07-22T10:30:00+09:00"
}
```

---

#### 401 Unauthorized

```json
{
  "success": false,
  "code": "AUTH_ACCESS_TOKEN_INVALID",
  "message": "로그인이 필요합니다.",
  "data": null,
  "timestamp": "2026-07-22T10:30:00+09:00"
}
```

---

#### 404 Not Found

```json
{
  "success": false,
  "code": "MEMBER_NOT_FOUND",
  "message": "회원 정보를 찾을 수 없습니다.",
  "data": null,
  "timestamp": "2026-07-22T10:30:00+09:00"
}
```

---

#### 500 Internal Server Error

```json
{
  "success": false,
  "code": "COMMON_INTERNAL_SERVER_ERROR",
  "message": "일시적인 오류가 발생했습니다.",
  "data": null,
  "timestamp": "2026-07-22T10:30:00+09:00"
}
```

---

### 프론트 처리

```
알림 선택
→ 한 건 읽음 처리
→ targetAvailable 확인
→ 접근 가능할 때만 targetScreen·targetId·targetSubId로 이동

type=MESSAGE
→ 알림 본문을 표시
→ actor 프로필로 이동 가능
→ 정식 쪽지방으로 이동하지 않음
```

### 추가 프론트 처리 기준

```
홈 화면 알림 버튼 선택
→ GET /api/v1/notifications?size=10 호출
→ 읽지 않은 알림 최신순 다음 읽은 알림 최신순으로 목록 표시

isRead = false
→ 읽지 않은 알림 스타일 적용
→ 강조 배경 또는 읽지 않음 표시 제공

알림 항목 선택
→ PATCH /api/v1/notifications/{notificationId}/read 호출
→ 해당 알림을 읽음 상태로 변경

targetAvailable = true
→ targetScreen과 targetId를 이용해 대상 화면으로 이동

targetAvailable = false
→ 상세 화면으로 이동하지 않음
→ "더 이상 확인할 수 없는 내용입니다." 안내

전체 읽음 선택
→ PATCH /api/v1/notifications/read-all 호출
→ 화면의 모든 알림을 읽음 상태로 변경
→ unreadCount를 0으로 갱신

목록 하단 도달
→ hasNext 확인
→ hasNext = true이면 nextCursor를 cursor로 전달
→ 다음 알림을 최대 10개 조회
→ 기존 content 뒤에 추가
→ notificationId가 이미 표시된 항목은 중복 추가하지 않음

추가 조회 실패
→ 기존 목록 유지
→ 목록 하단의 다시 시도로 같은 nextCursor 재요청

content가 빈 배열
→ "새로운 알림이 없습니다." 표시

REPORT_COMPLETED 알림 선택
→ targetScreen = REPORT_DETAIL 확인
→ GET /api/v1/reports/{reportId} 호출
```

---

## 알림 전체 읽음 처리

Method: PATCH
Progress: 진행 중
URI: /api/v1/notifications/read-all
담당자: 김윤석
연동여부: No

로그인한 회원의 읽지 않은 알림을 모두 읽음 상태로 변경한다.

알림 목록 화면에서 사용자가 `전체 읽음`을 선택했을 때 호출한다.

이미 모든 알림이 읽음 상태인 경우에도 오류 없이 정상 응답을 반환한다.

---

### Request

- Request HTTP Method: `PATCH`
- 인증 필요: 필요
- Request Body: 없음

#### Request Header

```
Authorization: Bearer {accessToken}
```

#### 요청 예시

```
PATCH /api/v1/notifications/read-all
Authorization: Bearer {accessToken}
```

---

### 처리 기준

- 로그인한 회원 ID는 Access Token에서 확인한다.
- 회원 상태가 `ACTIVE`인 경우에만 처리할 수 있다.
- 로그인한 회원에게 속한 알림만 읽음 처리한다.
- 다른 회원의 알림에는 영향을 주지 않는다.
- 다음 조건을 만족하는 알림을 일괄 수정한다.

```
notification.recipient_id = 로그인 회원 ID
notification.is_read = false
삭제되지 않은 알림
만료되지 않은 알림
```

- 읽지 않은 알림은 다음과 같이 변경한다.

```
is_read = true
read_at = 현재 시각
```

- 이미 읽은 알림의 `readAt`은 변경하지 않는다.
- 읽지 않은 알림이 한 건도 없어도 오류를 반환하지 않는다.
- 동일한 요청이 반복되어도 결과는 항상 모든 알림이 읽음 상태여야 한다.
- 전체 읽음 처리는 알림 데이터를 삭제하지 않는다.
- 전체 읽음 처리는 FCM 토큰이나 알림 수신 동의 설정을 변경하지 않는다.
- 만료된 알림이 목록에서 제외되는 정책이라면 만료 알림은 수정 대상에 포함하지 않는다.
- 삭제된 알림은 수정 대상에 포함하지 않는다.
- 알림 이동 대상의 현재 접근 가능 여부와 관계없이 알림 자체는 읽음 처리할 수 있다.
- 처리된 알림 수를 `updatedCount`로 반환한다.
- 처리 후 로그인한 회원의 읽지 않은 알림 수는 항상 `0`이어야 한다.
- 여러 알림에 대한 수정은 하나의 트랜잭션에서 처리한다.
- 일괄 수정 도중 오류가 발생하면 전체 작업을 롤백한다.

#### 일괄 처리 예시

```
읽지 않은 알림 5건
이미 읽은 알림 7건

전체 읽음 처리
→ 읽지 않은 5건만 is_read = true로 변경
→ 이미 읽은 7건의 read_at 유지
→ updatedCount = 5
→ unreadCount = 0
```

#### 이미 모두 읽은 경우

```
읽지 않은 알림 0건
이미 읽은 알림 12건

전체 읽음 처리
→ 변경되는 데이터 없음
→ updatedCount = 0
→ unreadCount = 0
→ 정상 응답
```

---

### Response

#### 200 OK — 전체 읽음 처리 완료

```json
{
  "success": true,
  "code": "NOTIFICATION_READ_ALL_SUCCESS",
  "message": "모든 알림을 읽음 처리했습니다.",
  "data": {
    "updatedCount": 5,
    "unreadCount": 0,
    "readAt": "2026-07-22T10:30:00+09:00"
  },
  "timestamp": "2026-07-22T10:30:00+09:00"
}
```

| 필드 | 타입 | 설명 |
| --- | --- | --- |
| `updatedCount` | Integer | 이번 요청으로 새롭게 읽음 처리된 알림 수 |
| `unreadCount` | Integer | 처리 후 남아 있는 읽지 않은 알림 수, 항상 `0` |
| `readAt` | String | 이번 일괄 읽음 처리 시각 |

---

#### 200 OK — 이미 모든 알림을 읽은 경우

```json
{
  "success": true,
  "code": "NOTIFICATION_ALREADY_ALL_READ",
  "message": "이미 모든 알림을 확인했습니다.",
  "data": {
    "updatedCount": 0,
    "unreadCount": 0,
    "readAt": "2026-07-22T10:30:00+09:00"
  },
  "timestamp": "2026-07-22T10:30:00+09:00"
}
```

프론트에서는 두 성공 응답을 구분하지 않고 `unreadCount = 0`을 반영한다.

---

### Exception

#### 401 Unauthorized

Access Token이 없거나 유효하지 않은 경우

```json
{
  "success": false,
  "code": "AUTH_ACCESS_TOKEN_INVALID",
  "message": "로그인이 필요합니다.",
  "data": null,
  "timestamp": "2026-07-22T10:30:00+09:00"
}
```

---

#### 404 Not Found

Access Token에 해당하는 회원이 존재하지 않거나 탈퇴한 경우

```json
{
  "success": false,
  "code": "MEMBER_NOT_FOUND",
  "message": "회원 정보를 찾을 수 없습니다.",
  "data": null,
  "timestamp": "2026-07-22T10:30:00+09:00"
}
```

탈퇴한 회원인지 처음부터 존재하지 않는 회원인지는 구분하여 노출하지 않는다.

---

#### 500 Internal Server Error

```json
{
  "success": false,
  "code": "COMMON_INTERNAL_SERVER_ERROR",
  "message": "일시적인 오류가 발생했습니다.",
  "data": null,
  "timestamp": "2026-07-22T10:30:00+09:00"
}
```

---

### 프론트 처리

```
알림 목록 화면 진입
→ unreadCount 확인
→ unreadCount가 1 이상이면 전체 읽음 버튼 활성화

사용자가 전체 읽음 선택
→ PATCH /api/v1/notifications/read-all 호출

전체 읽음 처리 성공
→ 현재 화면에 표시된 모든 알림의 isRead를 true로 변경
→ 읽지 않은 알림 강조 스타일 제거
→ 홈과 알림 화면의 알림 배지를 0으로 변경

updatedCount = 0
→ 오류 메시지를 표시하지 않음
→ 이미 모든 알림을 읽은 상태로 처리

NOTIFICATION_ALREADY_ALL_READ 반환
→ 전체 읽음 버튼 비활성화
→ unreadCount를 0으로 유지

전체 읽음 처리 실패
→ 기존 알림 읽음 상태 유지
→ "알림 읽음 처리에 실패했습니다." 안내
→ 네트워크 복구 후 재시도 가능

전체 읽음 처리 후 새 알림 수신
→ 새 알림만 isRead = false로 목록 상단에 추가
→ unreadCount를 1 증가
```

---

## 아파트 챗봇 대화 이력 조회

Method: GET
Progress: 완료
URI: /api/v1/apartments/{apartmentId}/chatbot/conversations/{conversationId}/messages
담당자: 윤다인
연동여부: No

특정 아파트의 챗봇 대화에 저장된 사용자 질문과 AI 답변 이력을 조회한다.

대화를 생성한 회원만 조회할 수 있으며, 답변 처리 상태·근거 유형·출처 목록을 함께 반환한다.

---

### Request

- Request HTTP Method: `GET`
- 인증 필요: 필요
- Request Body: 없음

#### Request Header

```
Authorization: Bearer {accessToken}
```

#### Path Variable

```
GET /api/v1/apartments/15/chatbot/conversations/41/messages
```

| 필드 | 타입 | 필수 | 설명 |
| --- | --- | --- | --- |
| `apartmentId` | Long | Y | 대화 대상 아파트 ID |
| `conversationId` | Long | Y | 조회할 챗봇 대화 ID |

#### Query Parameter

첫 번째 조회:

```
GET /api/v1/apartments/15/chatbot/conversations/41/messages?size=20
```

다음 조회:

```
GET /api/v1/apartments/15/chatbot/conversations/41/messages?cursor=52&size=20
```

| 필드 | 타입 | 필수 | 기본값 | 설명 |
| --- | --- | --- | --- | --- |
| `cursor` | Long | N | 없음 | 다음 목록 조회에 사용할 마지막 메시지 ID |
| `size` | Integer | N | `20` | 한 번에 조회할 메시지 수, 1~100 |

---

### 메시지 역할

| 값 | 설명 |
| --- | --- |
| `USER` | 사용자가 전송한 질문 |
| `ASSISTANT` | AI 챗봇 답변 |

최종 ERD v7의 챗봇 메시지는 `USER`, `ASSISTANT`만 지원하며 `SYSTEM` 역할을 사용하지 않는다.

### 메시지 상태

| 값 | 설명 |
| --- | --- |
| `PENDING` | 비동기 답변 작업이 접수된 초기 상태 |
| `PROCESSING` | AI가 답변과 출처를 생성 중인 상태 |
| `COMPLETED` | 답변 생성과 출처 검증이 완료된 상태 |
| `FAILED` | 답변 생성에 실패한 상태 |
- 사용자 질문은 저장 즉시 `COMPLETED`다.
- AI 답변의 `content`는 `PENDING` 또는 `PROCESSING`일 때만 `null`일 수 있다.
- `COMPLETED`인 AI 답변은 `content`가 반드시 존재해야 한다.

### 답변 근거 유형

| 값 | 화면 라벨 | 설명 |
| --- | --- | --- |
| `REPORT` | 리포트 기반 | 조회 가능한 완료 리포트만 사용한 답변 |
| `WEB` | 웹 기반 | 질문에 활용할 수 있는 리포트 근거가 부족해 웹 검색 결과로 생성한 답변 |
| `NONE` | 근거 부족 | 신뢰할 수 있는 답변 근거를 확보하지 못한 경우 |

---

### 처리 기준

#### 1. 회원·아파트·대화 확인

- 로그인한 회원 ID는 Access Token에서 확인한다.
- `conversationId`에 해당하는 대화가 존재하는지 확인한다.
- 대화의 `member_id`가 로그인 회원 ID와 일치해야 한다.
- 대화의 `apartment_id`가 Path Variable의 `apartmentId`와 일치해야 한다.
- 다른 회원의 대화는 조회할 수 없다.
- 존재하지 않는 아파트 또는 대화는 각각 명확한 오류로 처리한다.

#### 2. 메시지 조회

- 사용자 질문과 AI 답변 placeholder·완료 결과를 모두 반환한다.
- 메시지는 `createdAt ASC, messageId ASC`의 오래된 순으로 반환한다.
- 같은 질문에 대한 사용자 메시지와 AI 답변은 별도 행으로 저장하고 `replyToMessageId`로 연결할 수 있다.
- `replyToMessageId`는 최종 ERD의 필수 컬럼은 아니므로, 저장 구조가 없다면 응답에서 생략하고 생성 순서로 묶을 수 있다.
- 대화 이력이 없으면 오류가 아닌 빈 배열을 반환한다.

#### 3. 진행 중 답변

- `PENDING` 또는 `PROCESSING`인 AI 메시지는 동일한 `messageId`로 반환한다.
- 프론트는 새로운 버블을 추가하지 않고 기존 AI 답변 버블의 상태를 갱신한다.
- 답변이 완료되면 같은 `messageId`의 `content`, `basisType`, `basisLabel`, `sources`, `completedAt`이 채워진다.
- 실패하면 `status = FAILED`, 정제된 `failReason`과 `retryable`을 반환한다.

#### 4. 리포트 기반 답변 원칙

- 질문 처리 시점에 조회 가능한 `DONE` 리포트 문서를 먼저 검색한다.
- 현재 질문과 관련된 리포트 근거의 유사도가 설정 임계값 이상이면 `basisType = REPORT`를 사용한다.
- 사용한 리포트 ID·제목·관련 섹션을 `sources`에 포함한다.
- 리포트가 존재하더라도 현재 질문에 활용할 수 있는 근거가 없거나 유사도가 임계값 미만이면 웹 검색 단계로 전환한다.

#### 5. 웹 기반 답변 원칙

- 조회 가능한 완료 리포트가 없거나 현재 질문에 활용할 리포트 근거가 부족하면 웹 검색을 시도한다.
- 신뢰할 수 있는 웹 검색 결과로 답변을 생성한 경우 `basisType = WEB`을 사용한다.
- 웹·공공데이터·아파트 기본 정보·실거래 데이터의 출처를 응답에 포함한다.
- 확인 시점과 출처를 명시하고, 변동 가능한 시세·교통 정보는 단정하지 않는다.
- 검색 결과의 제목과 URL이 실제 참조한 문서와 일치해야 한다.
- 리포트와 웹 모두에서 신뢰할 수 있는 근거를 확보하지 못하면 `basisType = NONE`을 사용한다.
- 하나의 답변에서 `REPORT`와 `WEB` 출처를 혼합하지 않는다.

#### 6. 스터디 문맥

- "스터디 추천"과 같은 질문에는 해당 아파트의 모집 중 스터디를 조회해 답변할 수 있다.
- 공개 모집 정보만 사용하며 비공개 공지·채팅·신청자 정보는 사용하지 않는다.
- 로그인 회원이 이미 참여 또는 신청한 스터디라면 공개 가능한 범위에서 상태를 반영할 수 있다.

#### 7. 출처 정보

- `sources`는 AI가 실제 사용한 출처만 반환한다.
- 출처가 없는 경우 빈 배열을 반환하고 `basisType = NONE`을 사용할 수 있다.
- 리포트 원문 근거 `sourceId`를 챗봇 사용자에게 그대로 공개할지는 리포트 원문 권한 정책을 따른다.
- 비참여자가 리포트 기반 답변을 받더라도 참여자 원문 근거는 노출하지 않는다.

#### 8. 커서 페이지네이션

- 첫 요청에는 `cursor`를 전달하지 않는다.
- `cursor`가 전달되면 `messageId > cursor`인 메시지를 조회한다.
- 요청한 `size`보다 한 건 더 조회해 다음 데이터 존재 여부를 판단한다.
- 응답은 최대 `size`개이며 다음 데이터가 있으면 마지막 메시지 ID를 `nextCursor`로 반환한다.
- 진행 중 답변 상태를 확인하기 위해 마지막 메시지 이후만 요청하는 방식 외에, 특정 메시지 갱신이 필요하면 첫 페이지 또는 최신 메시지를 재조회할 수 있다.

---

### Response

#### 200 OK

```
{
  "success": true,
  "code": "CHATBOT_MESSAGE_LIST_SUCCESS",
  "message": "챗봇 대화 이력 조회에 성공했습니다.",
  "data": {
    "conversationId": 41,
    "apartment": {
      "apartmentId": 15,
      "name": "래미안 옥수 리버젠"
    },
    "content": [
      {
        "messageId": 51,
        "role": "USER",
        "content": "교통 어때요?",
        "status": "COMPLETED",
        "basisType": "NONE",
        "basisLabel": null,
        "sources": [],
        "failReason": null,
        "retryable": false,
        "createdAt": "2026-07-25T16:05:00+09:00",
        "completedAt": "2026-07-25T16:05:00+09:00"
      },
      {
        "messageId": 52,
        "role": "ASSISTANT",
        "content": "임장 리포트에서는 옥수역 개찰구까지 실제 도보 약 8분이 걸렸다는 기록이 반복적으로 확인됐습니다. 대중교통 접근성은 긍정적이지만 출퇴근 시간대 단지 앞 차량 흐름은 추가 확인이 필요합니다.",
        "status": "COMPLETED",
        "basisType": "REPORT",
        "basisLabel": "리포트 기반",
        "sources": [
          {
            "sourceType": "REPORT",
            "sourceId": 48,
            "reportId": 48,
            "title": "래미안 옥수 리버젠 임장 리포트",
            "sectionLabel": "교통",
            "url": null
          }
        ],
        "failReason": null,
        "retryable": false,
        "createdAt": "2026-07-25T16:05:00+09:00",
        "completedAt": "2026-07-25T16:05:04+09:00"
      },
      {
        "messageId": 53,
        "role": "USER",
        "content": "시세 알려줘",
        "status": "COMPLETED",
        "basisType": "NONE",
        "basisLabel": null,
        "sources": [],
        "failReason": null,
        "retryable": false,
        "createdAt": "2026-07-25T16:06:00+09:00",
        "completedAt": "2026-07-25T16:06:00+09:00"
      },
      {
        "messageId": 54,
        "role": "ASSISTANT",
        "content": null,
        "status": "PROCESSING",
        "basisType": "REPORT",
        "basisLabel": "리포트 기반",
        "sources": [],
        "failReason": null,
        "retryable": false,
        "createdAt": "2026-07-25T16:06:00+09:00",
        "completedAt": null
      }
    ],
    "nextCursor": 54,
    "hasNext": false,
    "lastMessageAt": "2026-07-25T16:06:00+09:00",
    "hasResponseInProgress": true
  },
  "timestamp": "2026-07-25T16:06:02+09:00"
}
```

#### 답변 생성 실패 메시지 예시

```
{
  "messageId": 56,
  "role": "ASSISTANT",
  "content": null,
  "status": "FAILED",
  "basisType": "WEB",
  "basisLabel": "웹 기반",
  "sources": [],
  "failReason": "관련 정보를 조회하는 중 일시적인 오류가 발생했습니다.",
  "retryable": true,
  "createdAt": "2026-07-25T16:08:00+09:00",
  "completedAt": "2026-07-25T16:08:12+09:00"
}
```

#### 대화 이력이 없는 경우

```
{
  "success": true,
  "code": "CHATBOT_MESSAGE_LIST_SUCCESS",
  "message": "챗봇 대화 이력 조회에 성공했습니다.",
  "data": {
    "conversationId": 41,
    "apartment": {
      "apartmentId": 15,
      "name": "래미안 옥수 리버젠"
    },
    "content": [],
    "nextCursor": null,
    "hasNext": false,
    "lastMessageAt": null,
    "hasResponseInProgress": false
  },
  "timestamp": "2026-07-25T16:06:02+09:00"
}
```

---

### Response Field

#### 대화 정보

| 필드 | 타입 | 설명 |
| --- | --- | --- |
| `conversationId` | Long | 챗봇 대화 ID |
| `apartment` | Object | 대화 대상 아파트 |
| `content` | Array | 메시지 목록 |
| `nextCursor` | Long | null | 다음 조회에 사용할 메시지 ID |
| `hasNext` | Boolean | 다음 메시지 존재 여부 |
| `lastMessageAt` | String | null | 대화의 마지막 메시지 시각 |
| `hasResponseInProgress` | Boolean | 답변 생성 중 메시지 존재 여부 |

#### 메시지 정보

| 필드 | 타입 | 설명 |
| --- | --- | --- |
| `messageId` | Long | 메시지 ID |
| `role` | String | `USER`, `ASSISTANT` |
| `content` | String | null | 질문 또는 답변 내용 |
| `status` | String | 메시지 처리 상태 |
| `basisType` | String | `REPORT`, `WEB`, `NONE` |
| `basisLabel` | String | null | `리포트 기반`, `웹 기반` 등 표시 문구 |
| `sources` | Array | 실제 답변 출처 |
| `failReason` | String | null | 사용자 표시용 실패 사유 |
| `retryable` | Boolean | 동일 질문 재시도 가능 여부 |
| `createdAt` | String | 메시지 생성 시각 |
| `completedAt` | String | null | 처리 완료 또는 실패 시각 |

---

### Exception

#### 400 Bad Request — 잘못된 조회 개수

```
{
  "success": false,
  "code": "COMMON_INVALID_REQUEST",
  "message": "입력값을 확인해 주세요.",
  "data": {
    "field": "size",
    "reason": "조회 개수는 1 이상 100 이하이어야 합니다."
  },
  "timestamp": "2026-07-25T16:06:00+09:00"
}
```

---

#### 400 Bad Request — 아파트 불일치

```
{
  "success": false,
  "code": "CHATBOT_APARTMENT_MISMATCH",
  "message": "해당 아파트의 챗봇 대화가 아닙니다.",
  "data": null,
  "timestamp": "2026-07-25T16:06:00+09:00"
}
```

---

#### 401 Unauthorized

```
{
  "success": false,
  "code": "AUTH_ACCESS_TOKEN_INVALID",
  "message": "로그인이 필요합니다.",
  "data": null,
  "timestamp": "2026-07-25T16:06:00+09:00"
}
```

---

#### 403 Forbidden — 대화 접근 권한 없음

```
{
  "success": false,
  "code": "CHATBOT_CONVERSATION_ACCESS_DENIED",
  "message": "해당 챗봇 대화에 접근할 권한이 없습니다.",
  "data": null,
  "timestamp": "2026-07-25T16:06:00+09:00"
}
```

---

#### 404 Not Found — 아파트 없음

```
{
  "success": false,
  "code": "APARTMENT_NOT_FOUND",
  "message": "아파트 정보를 찾을 수 없습니다.",
  "data": null,
  "timestamp": "2026-07-25T16:06:00+09:00"
}
```

---

#### 404 Not Found — 대화 없음

```
{
  "success": false,
  "code": "CHATBOT_CONVERSATION_NOT_FOUND",
  "message": "챗봇 대화를 찾을 수 없습니다.",
  "data": null,
  "timestamp": "2026-07-25T16:06:00+09:00"
}
```

---

#### 500 Internal Server Error

```
{
  "success": false,
  "code": "COMMON_INTERNAL_SERVER_ERROR",
  "message": "일시적인 오류가 발생했습니다.",
  "data": null,
  "timestamp": "2026-07-25T16:06:00+09:00"
}
```

---

### 프론트 처리

```
챗봇 대화 화면 진입
→ 대화 이력 조회
→ content를 createdAt·messageId 순으로 표시

ASSISTANT status = PENDING 또는 PROCESSING
→ 동일 messageId의 로딩 버블 표시
→ 일정 간격으로 대화 이력 재조회
→ 새 버블을 중복 추가하지 않음

status = COMPLETED
→ content 표시
→ basisLabel 배지 표시
→ sources가 있으면 출처 보기 영역 표시

status = FAILED
→ failReason 표시
→ retryable = true이면 "다시 질문" 버튼 표시

basisType = REPORT
→ "리포트 기반" 배지 표시
→ 웹 기반으로 오인하지 않도록 출처 라벨 고정

CHATBOT_CONVERSATION_ACCESS_DENIED
→ "접근할 수 없는 대화입니다." 안내
→ 아파트 상세 화면으로 이동
```

---

## 미종료 참여자 종료 요청

Method: POST
Progress: 시작 전
URI: /api/v1/studies/{studyId}/field-visit/finish-requests
담당자: 윤다인
연동여부: No

스터디장이 아직 임장을 종료하지 않은 참여자에게 종료 요청 알림을 전송한다.

이 API는 참여자를 강제로 종료하지 않는다. 대상 참여자는 알림을 선택해 임장 진행 화면으로 이동한 뒤 본인의 종료 버튼을 통해 직접 종료할 수 있다.

---

### Request

- Request HTTP Method: `POST`
- 인증 필요: 필요
- Content-Type: `application/json`

#### Request Header

```
Authorization: Bearer {accessToken}
```

#### Path Variable

```
POST /api/v1/studies/7/field-visit/finish-requests
```

| 필드 | 타입 | 필수 | 설명 |
| --- | --- | --- | --- |
| `studyId` | Long | Y | 종료 요청을 보낼 스터디 ID |

#### Request Body

특정 참여자에게 요청:

```json
{
  "targetMemberIds": [51, 63],
  "clientRequestId": "8fe64d66-b92b-47b6-b45e-10bcda0d7a72"
}
```

미종료 참여자 전원에게 요청:

```json
{
  "targetMemberIds": null,
  "clientRequestId": "8fe64d66-b92b-47b6-b45e-10bcda0d7a72"
}
```

| 필드 | 타입 | 필수 | 설명 |
| --- | --- | --- | --- |
| `targetMemberIds` | Array\<Long> | N | 종료 요청 대상. 생략 또는 `null`이면 미종료 참여자 전원 |
| `clientRequestId` | String | Y | 중복 알림 생성 방지용 UUID |

---

### 처리 기준

#### 1. 스터디장 권한

- 로그인한 회원 ID는 Access Token에서 확인한다.
- 로그인 회원은 해당 스터디의 스터디장이어야 한다.
- 일반 멤버는 다른 참여자에게 종료 요청을 보낼 수 없다.
- 스터디장 본인의 개인 종료는 개인 임장 종료 API를 사용한다.

#### 2. 세션 상태

- `field_session.status = IN_PROGRESS`인 경우에만 요청할 수 있다.
- 세션이 없거나 이미 종료되었으면 요청을 처리하지 않는다.
- 아직 임장을 시작하지 않은 고정 후보(`field_participant` 없음)는 종료 요청 대상에 포함하지 않는다.

#### 3. 대상 참여자 검증

- 대상은 현재 세션의 `field_participant.status = IN_PROGRESS`인 회원이어야 한다.
- 이미 종료된 참여자는 자동 제외한다.
- 존재하지 않는 회원, 다른 세션 참여자, 아직 시작하지 않은 회원 ID가 명시적으로 포함되면 유효한 대상만 처리할지 전체 거절할지 정책이 필요하다. 본 명세에서는 잘못된 대상이 하나라도 있으면 전체 요청을 거절하여 오발송을 방지한다.
- `targetMemberIds`가 비어 있거나 `null`이면 현재 미종료 참여자 전원을 조회한다.
- 요청자 본인 ID는 대상에서 제외한다.

#### 4. 알림 생성

- 대상별로 `category = FIELD`, `type = FIELD_END_REQUEST` 알림을 생성한다.
- `targetScreen = FIELD_VISIT`, `targetId = studyId`, `targetSubId = sessionId`를 저장한다.
- `actorId`에는 스터디장 ID를 저장한다.
- 대상 회원의 서비스 알림 동의와 FCM 토큰 여부에 따라 Push를 발송한다.
- Push 발송에 실패해도 서버 알림 생성이 성공했다면 요청 자체를 롤백하지 않는다.

#### 5. 멱등성

- `clientRequestId`와 대상 회원 ID를 조합해 알림 `idempotencyKey`를 생성한다.
- 동일 요청이 반복되어도 대상별 알림은 한 건만 생성한다.
- 이미 요청을 받은 대상에게 동일 `clientRequestId`로 재호출하면 기존 알림 ID를 반환한다.
- 새로운 요청 ID로 반복 요청하는 것은 허용할 수 있으나 과도한 알림을 방지하기 위한 쿨다운 정책을 적용할 수 있다.

#### 6. 강제 종료와의 차이

- 이 API는 `field_participant.status`, `endedAt`, `stayDurationSec`를 변경하지 않는다.
- 스터디장이 즉시 전체 마감을 원하면 전체 임장 마감 API를 사용한다.

---

### Response

#### 200 OK

```json
{
  "success": true,
  "code": "FIELD_VISIT_FINISH_REQUEST_SUCCESS",
  "message": "미종료 참여자에게 종료를 요청했습니다.",
  "data": {
    "studyId": 7,
    "sessionId": 100,
    "requestedCount": 2,
    "requests": [
      {
        "memberId": 51,
        "notificationId": 801,
        "sendStatus": "SENT"
      },
      {
        "memberId": 63,
        "notificationId": 802,
        "sendStatus": "SKIPPED"
      }
    ],
    "requestedAt": "2026-07-25T14:50:00+09:00"
  },
  "timestamp": "2026-07-25T14:50:00+09:00"
}
```

#### 200 OK — 요청할 참여자 없음

```json
{
  "success": true,
  "code": "FIELD_VISIT_NO_UNFINISHED_PARTICIPANT",
  "message": "종료를 요청할 참여자가 없습니다.",
  "data": {
    "studyId": 7,
    "sessionId": 100,
    "requestedCount": 0,
    "requests": [],
    "requestedAt": "2026-07-25T14:50:00+09:00"
  },
  "timestamp": "2026-07-25T14:50:00+09:00"
}
```

---

### Response Field

| 필드 | 타입 | 설명 |
| --- | --- | --- |
| `studyId` | Long | 스터디 ID |
| `sessionId` | Long | 임장 세션 ID |
| `requestedCount` | Integer | 종료 요청 알림 대상 수 |
| `requests` | Array | 대상별 알림 생성 결과 |
| `requestedAt` | String | 요청 처리 시각 |

#### `requests[]`

| 필드 | 타입 | 설명 |
| --- | --- | --- |
| `memberId` | Long | 대상 회원 ID |
| `notificationId` | Long | 생성된 알림 ID |
| `sendStatus` | String | Push 발송 상태. `PENDING`, `SENT`, `FAILED`, `SKIPPED` |

---

### Exception

#### 400 Bad Request — 대상 회원 오류

```json
{
  "success": false,
  "code": "FIELD_VISIT_FINISH_REQUEST_TARGET_INVALID",
  "message": "종료 요청 대상을 확인해 주세요.",
  "data": {
    "memberId": 999,
    "reason": "현재 임장 중인 참여자가 아닙니다."
  },
  "timestamp": "2026-07-25T14:50:00+09:00"
}
```

---

#### 401 Unauthorized

```json
{
  "success": false,
  "code": "AUTH_ACCESS_TOKEN_INVALID",
  "message": "로그인이 필요합니다.",
  "data": null,
  "timestamp": "2026-07-25T14:50:00+09:00"
}
```

---

#### 403 Forbidden — 스터디장 아님

```json
{
  "success": false,
  "code": "FIELD_VISIT_FINISH_REQUEST_FORBIDDEN",
  "message": "스터디장만 종료를 요청할 수 있습니다.",
  "data": null,
  "timestamp": "2026-07-25T14:50:00+09:00"
}
```

---

#### 404 Not Found — 임장 세션 없음

```json
{
  "success": false,
  "code": "FIELD_VISIT_NOT_FOUND",
  "message": "임장 세션을 찾을 수 없습니다.",
  "data": null,
  "timestamp": "2026-07-25T14:50:00+09:00"
}
```

---

#### 409 Conflict — 세션 종료

```json
{
  "success": false,
  "code": "FIELD_VISIT_ALREADY_ENDED",
  "message": "이미 종료된 임장 세션입니다.",
  "data": null,
  "timestamp": "2026-07-25T14:50:00+09:00"
}
```

---

#### 500 Internal Server Error

```json
{
  "success": false,
  "code": "COMMON_INTERNAL_SERVER_ERROR",
  "message": "일시적인 오류가 발생했습니다.",
  "data": null,
  "timestamp": "2026-07-25T14:50:00+09:00"
}
```

---

### 프론트 처리

```
스터디장이 참여자 상태 목록 확인
→ 미종료 참여자 존재 시 "종료 요청" 버튼 표시

개별 또는 전체 대상 선택
→ 종료 요청 API 호출
→ 요청 중 버튼 중복 탭 방지

요청 성공
→ 대상 항목에 "종료 요청 보냄" 상태 표시
→ 참여자 상태 자체는 IN_PROGRESS로 유지

FIELD_VISIT_NO_UNFINISHED_PARTICIPANT
→ 전체 마감 필요 여부 확인
→ 참여자 목록 새로고침
```

---

## 개인 임장 종료

Method: POST
Progress: 완료
URI: /api/v1/studies/{studyId}/field-visit/finish
담당자: 윤다인
연동여부: No

로그인한 참여자가 본인의 임장을 종료한다.

종료 전 미완료 체크리스트 수를 확인할 수 있으며, 종료 후에는 본인의 체크리스트 완료 상태와 현장 기록을 추가·변경·삭제할 수 없다. 마지막 진행 참여자가 종료하면 세션을 자동 종료하고 리포트 생성 이벤트를 한 번만 발행한다.

---

### Request

- Request HTTP Method: `POST`
- 인증 필요: 필요
- Content-Type: `application/json`

#### Request Header

```
Authorization: Bearer {accessToken}
```

#### Path Variable

```
POST /api/v1/studies/7/field-visit/finish
```

| 필드 | 타입 | 필수 | 설명 |
| --- | --- | --- | --- |
| `studyId` | Long | Y | 개인 임장을 종료할 스터디 ID |

#### Request Body

```json
{
  "finishConfirmed": true,
  "clientRequestId": "305eebd1-3ef8-4a2f-a5cf-56ff7c8ab4c1"
}
```

| 필드 | 타입 | 필수 | 설명 |
| --- | --- | --- | --- |
| `finishConfirmed` | Boolean | Y | 미완료 항목 안내를 확인하고 종료하는지 여부 |
| `clientRequestId` | String | Y | 중복 종료 요청 방지용 UUID |

---

### 처리 기준

#### 1. 참여자 확인

- 로그인한 회원 ID는 Access Token에서 확인한다.
- 진행 중인 `field_session`과 본인의 `field_participant`가 존재해야 한다.
- 해당 세션의 참여자가 아니면 종료할 수 없다.
- 아직 GPS 임장을 시작하지 않은 고정 후보는 개인 종료 API 대상이 아니다.

#### 2. 미완료 체크리스트 안내

- 본인 체크리스트의 `totalCount`, `completedCount`, `incompleteCount`를 계산한다.
- 미완료 항목이 있어도 사용자가 현재 기록으로 종료하는 것은 허용한다.
- `finishConfirmed = false`인 요청은 종료하지 않고 미완료 현황을 반환하는 별도 미리보기 API가 아니므로 잘못된 요청으로 처리한다.
- 프론트는 종료 API 호출 전에 미완료 개수를 화면에 표시한다.

#### 3. 개인 종료 처리

- 본인 `field_participant.status`를 `ENDED`로 변경한다.
- `endedAt`에 서버 현재 시각을 저장한다.
- `endReason = SELF_ENDED`로 저장한다.
- `stayDurationSec`는 `endedAt - startedAt`을 초 단위로 계산해 저장한다.
- 음수 또는 비정상적으로 큰 값은 서버 데이터 검증 대상으로 기록한다.

#### 4. 종료 후 쓰기 차단

- 개인 종료 후 체크리스트 완료 상태 저장을 차단한다.
- 텍스트·사진·신규 STT 기록 생성과 현장 기록 삭제를 차단한다.
- 종료 전에 요청된 STT 작업의 처리·재처리는 보존 기간 정책에 따라 계속될 수 있다.
- 종료한 참여자는 기존 체크리스트와 기록을 읽기 전용으로 조회할 수 있다.

#### 5. 전원 종료 판정

- 개인 종료 처리 후 현재 세션 참여자의 상태를 다시 확인한다.
- 모든 시작 참여자가 `ENDED`이고 아직 시작하지 않은 고정 후보를 세션 종료에 포함하지 않는 정책을 적용한다.
- 전원이 종료되면 `field_session.status = ENDED`, `endedAt = 현재 시각`, `endReason = ALL_ENDED`로 저장한다.
- 세션 종료 시 `study.status`를 `IN_PROGRESS` 상태로 유지한 채 리포트 생성이 끝나면 `COMPLETED`로 전환하는 흐름을 사용한다.

#### 6. 리포트 생성 이벤트

- 세션이 이번 요청으로 종료되면 같은 종료 트랜잭션에서 `PENDING` 리포트를 먼저 생성하고 ID를 확정한 뒤 `REPORT_REQUESTED` 이벤트를 발행한다.
- 이벤트의 실제 Kafka 발행은 커밋 후 처리하며, 종료 응답의 `reportId`는 워커가 acquire할 리포트와 동일하다.
- 동일 종료 요청이나 Kafka 재시도로 리포트 생성 이벤트가 중복 발행되지 않도록 idempotency key를 사용한다.
- 리포트는 스터디당 한 건만 생성되며, 이미 리포트가 있으면 중복 생성하지 않는다.

#### 7. 멱등성

- 같은 `clientRequestId` 요청은 기존 종료 결과를 반환한다.
- 이미 본인이 종료한 상태에서 다시 호출해도 최초 `endedAt`, `stayDurationSec`, `endReason`을 유지하고 정상 응답한다.
- 세션까지 이미 종료된 경우에도 현재 확정 상태를 반환한다.

---

### Response

#### 200 OK — 개인 종료, 세션은 계속 진행

```json
{
  "success": true,
  "code": "FIELD_VISIT_FINISH_SUCCESS",
  "message": "임장을 종료했습니다.",
  "data": {
    "studyId": 7,
    "sessionId": 100,
    "participant": {
      "participantId": 301,
      "status": "ENDED",
      "startedAt": "2026-07-25T14:00:00+09:00",
      "endedAt": "2026-07-25T14:45:00+09:00",
      "endReason": "SELF_ENDED",
      "stayDurationSec": 2700
    },
    "checklist": {
      "completedCount": 10,
      "totalCount": 12,
      "incompleteCount": 2
    },
    "sessionEnded": false,
    "sessionEndReason": null,
    "reportTriggered": false,
    "reportId": null
  },
  "timestamp": "2026-07-25T14:45:00+09:00"
}
```

#### 200 OK — 마지막 참여자 종료로 전체 세션 종료

```json
{
  "success": true,
  "code": "FIELD_VISIT_FINISH_AND_SESSION_END_SUCCESS",
  "message": "임장을 종료하고 리포트 생성을 시작했습니다.",
  "data": {
    "studyId": 7,
    "sessionId": 100,
    "participant": {
      "participantId": 301,
      "status": "ENDED",
      "startedAt": "2026-07-25T14:00:00+09:00",
      "endedAt": "2026-07-25T15:00:00+09:00",
      "endReason": "SELF_ENDED",
      "stayDurationSec": 3600
    },
    "checklist": {
      "completedCount": 12,
      "totalCount": 12,
      "incompleteCount": 0
    },
    "sessionEnded": true,
    "sessionEndReason": "ALL_ENDED",
    "reportTriggered": true,
    "reportId": 48
  },
  "timestamp": "2026-07-25T15:00:00+09:00"
}
```

#### 200 OK — 이미 종료된 참여자

```json
{
  "success": true,
  "code": "FIELD_PARTICIPANT_ALREADY_ENDED",
  "message": "이미 임장을 종료했습니다.",
  "data": {
    "studyId": 7,
    "sessionId": 100,
    "participant": {
      "participantId": 301,
      "status": "ENDED",
      "startedAt": "2026-07-25T14:00:00+09:00",
      "endedAt": "2026-07-25T14:45:00+09:00",
      "endReason": "SELF_ENDED",
      "stayDurationSec": 2700
    },
    "checklist": {
      "completedCount": 10,
      "totalCount": 12,
      "incompleteCount": 2
    },
    "sessionEnded": false,
    "sessionEndReason": null,
    "reportTriggered": false,
    "reportId": null
  },
  "timestamp": "2026-07-25T14:46:00+09:00"
}
```

---

### Response Field

| 필드 | 타입 | 설명 |
| --- | --- | --- |
| `participant` | Object | 본인 종료 정보 |
| `checklist` | Object | 종료 시점 체크리스트 현황 |
| `sessionEnded` | Boolean | 이번 요청으로 세션 전체가 종료되었는지 여부 |
| `sessionEndReason` | String | null | `ALL_ENDED` 또는 `null` |
| `reportTriggered` | Boolean | 리포트 생성 이벤트 발행 여부 |
| `reportId` | Long \| null | 전체 세션 종료 시 즉시 생성된 양의 리포트 ID. 세션이 계속 진행 중이면 `null` |

---

### Exception

#### 400 Bad Request — 종료 확인 누락

```json
{
  "success": false,
  "code": "FIELD_VISIT_FINISH_CONFIRMATION_REQUIRED",
  "message": "임장 종료 여부를 확인해 주세요.",
  "data": {
    "field": "finishConfirmed"
  },
  "timestamp": "2026-07-25T14:45:00+09:00"
}
```

---

#### 401 Unauthorized

```json
{
  "success": false,
  "code": "AUTH_ACCESS_TOKEN_INVALID",
  "message": "로그인이 필요합니다.",
  "data": null,
  "timestamp": "2026-07-25T14:45:00+09:00"
}
```

---

#### 403 Forbidden — 세션 참여자 아님

```json
{
  "success": false,
  "code": "FIELD_VISIT_FINISH_FORBIDDEN",
  "message": "임장 참여자만 개인 임장을 종료할 수 있습니다.",
  "data": null,
  "timestamp": "2026-07-25T14:45:00+09:00"
}
```

---

#### 404 Not Found — 임장 세션 없음

```json
{
  "success": false,
  "code": "FIELD_VISIT_NOT_FOUND",
  "message": "임장 세션을 찾을 수 없습니다.",
  "data": null,
  "timestamp": "2026-07-25T14:45:00+09:00"
}
```

---

#### 500 Internal Server Error

```json
{
  "success": false,
  "code": "COMMON_INTERNAL_SERVER_ERROR",
  "message": "일시적인 오류가 발생했습니다.",
  "data": null,
  "timestamp": "2026-07-25T14:45:00+09:00"
}
```

---

### 프론트 처리

```
상단 종료 버튼 선택
→ 미완료 체크리스트 개수 확인
→ "현재 기록으로 종료할까요?" 확인 모달 표시

사용자가 계속 작성 선택
→ API 호출하지 않음
→ 임장 진행 화면 유지

종료 확인
→ finishConfirmed = true와 clientRequestId로 API 호출

종료 성공
→ 체크리스트·메모 입력을 읽기 전용으로 변경
→ 개인 종료 상태 표시

sessionEnded = true
→ 리포트 생성 상태 화면으로 이동
→ reportId로 상태 조회 시작

FIELD_PARTICIPANT_ALREADY_ENDED
→ 오류 표시 없이 읽기 전용 종료 화면으로 동기화
```

---

## 개인 임장 종료 취소

Method: POST
Progress: 완료
URI: /api/v1/studies/{studyId}/field-visit/finish/cancel
담당자: 윤다인
연동여부: No

로그인한 참여자가 방금 종료한 본인의 임장을 다시 진행 중으로 되돌린다.

본인이 직접 종료(`SELF_ENDED`)했고 임장 세션이 아직 진행 중일 때에만 취소할 수 있다. 세션이 이미 종료되면 리포트 생성이 시작되므로 되돌릴 수 없다.

---

### Request

- Request HTTP Method: `POST`
- 인증 필요: 필요
- Content-Type: `application/json`

#### Request Header

```
Authorization: Bearer {accessToken}
```

#### Path Variable

```
POST /api/v1/studies/7/field-visit/finish/cancel
```

| 필드 | 타입 | 필수 | 설명 |
| --- | --- | --- | --- |
| `studyId` | Long | Y | 개인 임장 종료를 취소할 스터디 ID |

#### Request Body

```json
{
  "clientRequestId": "305eebd1-3ef8-4a2f-a5cf-56ff7c8ab4c1"
}
```

| 필드 | 타입 | 필수 | 설명 |
| --- | --- | --- | --- |
| `clientRequestId` | String | Y | 중복 취소 요청 방지용 UUID |

---

### 처리 기준

#### 1. 참여자 확인

- 로그인한 회원 ID는 Access Token에서 확인한다.
- 해당 스터디의 `ACTIVE` 멤버이자 그 세션의 본인 참여자여야 한다.
- 세션 참여자가 아니면 취소할 수 없다.

#### 2. 취소 가능 상태 확인

- 임장 세션이 이미 `ENDED`이면 리포트 생성이 시작되었으므로 취소할 수 없다(409).
- 본인 참여가 이미 `IN_PROGRESS`이면 아무것도 바꾸지 않고 멱등 성공으로 현재 상태를 반환한다(200).
- 본인 참여가 `ENDED`이지만 `endReason`이 `SELF_ENDED`가 아니면(스터디장 강제·과반수 종료) 취소할 수 없다(409).

#### 3. 종료 취소 처리

- 본인 `field_participant.status`를 다시 `IN_PROGRESS`로 되돌린다.
- `endedAt`, `endReason`, `stayDurationSec`를 `null`로 초기화한다.

#### 4. 멱등성

- 같은 `clientRequestId`를 재전송하거나 이미 진행 중인 상태에서 다시 호출해도 현재 진행 중 상태를 그대로 반환한다.

---

### Response

#### 200 OK — 개인 임장 종료 취소 성공

```json
{
  "success": true,
  "code": "FIELD_VISIT_FINISH_CANCEL_SUCCESS",
  "message": "개인 임장 종료를 취소하고 다시 진행 중으로 되돌렸습니다.",
  "data": {
    "studyId": 7,
    "sessionId": 100,
    "participant": {
      "participantId": 301,
      "status": "IN_PROGRESS",
      "startedAt": "2026-07-25T14:00:00+09:00",
      "endedAt": null,
      "endReason": null,
      "stayDurationSec": null
    }
  },
  "timestamp": "2026-07-25T14:46:00+09:00"
}
```

#### 200 OK — 이미 진행 중인 참여자

```json
{
  "success": true,
  "code": "FIELD_PARTICIPANT_ALREADY_IN_PROGRESS",
  "message": "이미 임장을 진행 중입니다.",
  "data": {
    "studyId": 7,
    "sessionId": 100,
    "participant": {
      "participantId": 301,
      "status": "IN_PROGRESS",
      "startedAt": "2026-07-25T14:00:00+09:00",
      "endedAt": null,
      "endReason": null,
      "stayDurationSec": null
    }
  },
  "timestamp": "2026-07-25T14:46:00+09:00"
}
```

---

### Response Field

| 필드 | 타입 | 설명 |
| --- | --- | --- |
| `studyId` | Long | 스터디 ID |
| `sessionId` | Long | 임장 세션 ID |
| `participant` | Object | 다시 진행 중이 된 본인 참여 정보 |
| `participant.participantId` | Long | 참여자 ID |
| `participant.status` | String | 항상 `IN_PROGRESS` |
| `participant.startedAt` | String | 임장 시작 시각(ISO-8601) |
| `participant.endedAt` | null | 종료 취소로 항상 `null` |
| `participant.endReason` | null | 종료 취소로 항상 `null` |
| `participant.stayDurationSec` | null | 종료 취소로 항상 `null` |

---

### Exception

#### 401 Unauthorized

```json
{
  "success": false,
  "code": "AUTH_ACCESS_TOKEN_INVALID",
  "message": "로그인이 필요합니다.",
  "data": null,
  "timestamp": "2026-07-25T14:45:00+09:00"
}
```

---

#### 403 Forbidden — 세션 참여자 아님

```json
{
  "success": false,
  "code": "FIELD_VISIT_FINISH_CANCEL_FORBIDDEN",
  "message": "임장 세션 참여자만 개인 임장 종료를 취소할 수 있습니다.",
  "data": null,
  "timestamp": "2026-07-25T14:45:00+09:00"
}
```

---

#### 404 Not Found — 임장 세션 없음

```json
{
  "success": false,
  "code": "FIELD_VISIT_NOT_FOUND",
  "message": "임장 세션을 찾을 수 없습니다.",
  "data": null,
  "timestamp": "2026-07-25T14:45:00+09:00"
}
```

---

#### 409 Conflict — 세션이 이미 종료됨

```json
{
  "success": false,
  "code": "FIELD_VISIT_ALREADY_ENDED",
  "message": "이미 종료된 임장 세션입니다.",
  "data": null,
  "timestamp": "2026-07-25T14:45:00+09:00"
}
```

---

#### 409 Conflict — 본인이 직접 종료한 임장이 아님

```json
{
  "success": false,
  "code": "FIELD_VISIT_FINISH_CANCEL_NOT_ALLOWED",
  "message": "직접 종료한 임장만 다시 진행 중으로 되돌릴 수 있습니다.",
  "data": null,
  "timestamp": "2026-07-25T14:45:00+09:00"
}
```

---

#### 500 Internal Server Error

```json
{
  "success": false,
  "code": "COMMON_INTERNAL_SERVER_ERROR",
  "message": "일시적인 오류가 발생했습니다.",
  "data": null,
  "timestamp": "2026-07-25T14:45:00+09:00"
}
```

---

## 실패한 STT 재처리 요청

Method: POST
Progress: 완료
URI: /api/v1/studies/{studyId}/field-visit/stt/{sttId}/retry
담당자: 김윤석
연동여부: No

실패한 STT 작업의 임시 음성 원본이 재처리 가능 기간 안에 남아 있는 경우 변환을 다시 요청한다.

새 작업을 중복 생성하지 않고 기존 `sttId`의 상태를 `PENDING`으로 되돌려 재처리한다. 보존 기간이 만료되었거나 음성 원본이 삭제된 경우에는 재처리할 수 없다.

---

### Request

- Request HTTP Method: `POST`
- 인증 필요: 필요
- Content-Type: `application/json`

#### Request Header

```
Authorization: Bearer {accessToken}
```

#### Path Variable

```
POST /api/v1/studies/7/field-visit/stt/stt-9f2021ab/retry
```

| 필드 | 타입 | 필수 | 설명 |
| --- | --- | --- | --- |
| `studyId` | Long | Y | STT 작업이 속한 스터디 ID |
| `sttId` | String | Y | 재처리할 STT 작업 ID |

#### Request Body

```json
{
  "clientRequestId": "55591972-492e-4c29-81bd-eb203f37be49"
}
```

| 필드 | 타입 | 필수 | 설명 |
| --- | --- | --- | --- |
| `clientRequestId` | String | Y | 중복 재처리 요청 방지용 UUID |

---

### 처리 기준

#### 1. 요청자·작업 확인

- 로그인 회원은 STT 작업을 처음 요청한 작성자여야 한다.
- 작업의 스터디 ID가 Path Variable과 일치해야 한다.
- 다른 회원의 실패 작업을 재처리할 수 없다.
- 작업이 존재하지 않으면 `FIELD_STT_NOT_FOUND`를 반환한다.

#### 2. 재처리 가능 상태

- 현재 상태가 `FAILED`인 작업만 재처리할 수 있다.
- `PENDING`, `PROCESSING`이면 기존 작업이 진행 중이므로 새 재처리를 시작하지 않는다.
- `DONE` 상태는 이미 현장 기록이 생성되었으므로 재처리할 수 없다.
- 실패 원인이 영구 오류로 분류되어 `retryable = false`이면 재처리하지 않는다.

#### 3. 음성 원본 보존 기간

- 연결된 `file_meta`가 `STT_AUDIO`이며 아직 삭제되지 않았어야 한다.
- `expiresAt`이 현재 시각 이후여야 한다.
- 음성 원본이 삭제되었거나 만료되었으면 `410 Gone`을 반환한다.
- 재처리 요청만으로 보존 기간을 무기한 연장하지 않는다.

#### 4. 임장 종료 이후 재처리

- 최종 요구사항은 실패 원본을 보존 기간 내 재처리할 수 있도록 요구한다.
- 따라서 최초 요청이 임장 진행 중 정상 접수된 작업이라면 개인·세션 종료 후에도 보존 기간 내 재처리를 허용할 수 있다.
- 단, 재처리 완료 후 생성되는 현장 기록은 최초 요청 당시의 `sessionId`, `checklistItemId`, 작성자에 연결한다.
- 세션 종료 후 새 음성 업로드나 새 STT 요청은 허용하지 않는다.

#### 5. 상태 변경과 실패 정보

- 재처리 시작 시 기존 작업의 상태를 `PENDING`으로 변경한다.
- 이전 `failReason`은 작업 이력에 남길 수 있으나 현재 응답에서는 초기화한다.
- 실제 처리 시작 후 `PROCESSING`, 성공 시 `DONE`, 재실패 시 `FAILED`로 변경한다.
- 성공하면 기존 작업에 `sourceId`를 연결하고 음성 원본을 삭제한다.

#### 6. 멱등성

- 동일한 재처리 `clientRequestId`가 반복되면 기존 재처리 결과를 반환한다.
- 동시에 여러 재처리 요청이 들어와도 하나의 작업만 실행한다.
- 이미 재처리가 진행 중이면 `200 OK`로 현재 상태를 반환하거나 `409`로 처리할 수 있으나, 본 명세에서는 중복 탭을 사용자 오류로 보지 않고 기존 상태를 정상 반환한다.

---

### Response

#### 202 Accepted — 재처리 시작

```json
{
  "success": true,
  "code": "FIELD_STT_RETRY_ACCEPTED",
  "message": "음성 변환을 다시 시작했습니다.",
  "data": {
    "sttId": "stt-9f2021ab",
    "studyId": 7,
    "checklistItemId": 503,
    "status": "PENDING",
    "sourceId": null,
    "retryRequestedAt": "2026-07-25T14:30:00+09:00"
  },
  "timestamp": "2026-07-25T14:30:00+09:00"
}
```

#### 200 OK — 이미 재처리 진행 중

```json
{
  "success": true,
  "code": "FIELD_STT_RETRY_ALREADY_IN_PROGRESS",
  "message": "음성 변환 재처리가 이미 진행 중입니다.",
  "data": {
    "sttId": "stt-9f2021ab",
    "studyId": 7,
    "checklistItemId": 503,
    "status": "PROCESSING",
    "sourceId": null,
    "retryRequestedAt": "2026-07-25T14:30:00+09:00"
  },
  "timestamp": "2026-07-25T14:30:02+09:00"
}
```

---

### Response Field

| 필드 | 타입 | 설명 |
| --- | --- | --- |
| `sttId` | String | 기존 STT 작업 ID |
| `studyId` | Long | 스터디 ID |
| `checklistItemId` | Long | 결과 연결 항목 ID |
| `status` | String | 재처리 후 상태 |
| `sourceId` | Long | null | 완료된 현장 기록 ID |
| `retryRequestedAt` | String | 재처리 요청 시각 |

---

### Exception

#### 400 Bad Request — clientRequestId 오류

```json
{
  "success": false,
  "code": "COMMON_INVALID_REQUEST",
  "message": "입력값을 확인해 주세요.",
  "data": {
    "field": "clientRequestId",
    "reason": "clientRequestId는 UUID 형식이어야 합니다."
  },
  "timestamp": "2026-07-25T14:30:00+09:00"
}
```

---

#### 401 Unauthorized

```json
{
  "success": false,
  "code": "AUTH_ACCESS_TOKEN_INVALID",
  "message": "로그인이 필요합니다.",
  "data": null,
  "timestamp": "2026-07-25T14:30:00+09:00"
}
```

---

#### 403 Forbidden — 작성자 아님

```json
{
  "success": false,
  "code": "FIELD_STT_RETRY_FORBIDDEN",
  "message": "본인이 요청한 음성 변환만 재처리할 수 있습니다.",
  "data": null,
  "timestamp": "2026-07-25T14:30:00+09:00"
}
```

---

#### 404 Not Found — STT 작업 없음

```json
{
  "success": false,
  "code": "FIELD_STT_NOT_FOUND",
  "message": "음성 변환 작업을 찾을 수 없습니다.",
  "data": null,
  "timestamp": "2026-07-25T14:30:00+09:00"
}
```

---

#### 409 Conflict — 재처리 불가 상태

```json
{
  "success": false,
  "code": "FIELD_STT_RETRY_NOT_ALLOWED",
  "message": "현재 상태에서는 음성 변환을 재처리할 수 없습니다.",
  "data": {
    "status": "DONE",
    "retryable": false
  },
  "timestamp": "2026-07-25T14:30:00+09:00"
}
```

---

#### 410 Gone — 음성 원본 만료

```json
{
  "success": false,
  "code": "FIELD_STT_AUDIO_EXPIRED",
  "message": "음성 원본의 재처리 가능 기간이 만료되었습니다. 다시 녹음해 주세요.",
  "data": null,
  "timestamp": "2026-07-25T14:30:00+09:00"
}
```

---

#### 500 Internal Server Error

```json
{
  "success": false,
  "code": "COMMON_INTERNAL_SERVER_ERROR",
  "message": "일시적인 오류가 발생했습니다.",
  "data": null,
  "timestamp": "2026-07-25T14:30:00+09:00"
}
```

---

### 프론트 처리

```
STT 실패 상태에서 retryable = true
→ "다시 시도" 버튼 표시

재시도 선택
→ clientRequestId 새로 생성
→ retry API 호출
→ 버튼 중복 탭 방지

202 Accepted 또는 FIELD_STT_RETRY_ALREADY_IN_PROGRESS
→ 상태를 PENDING/PROCESSING으로 갱신
→ 상태 조회 재개

FIELD_STT_AUDIO_EXPIRED
→ 재시도 버튼 숨김
→ 새 음성 녹음 안내
```

---

## STT 진행 상태/결과 조회

Method: GET
Progress: 완료
URI: /api/v1/studies/{studyId}/field-visit/stt/{sttId}
담당자: 김윤석
연동여부: No

STT 비동기 작업의 현재 처리 상태와 완료된 변환 텍스트, 생성된 현장 기록 `sourceId`를 조회한다.

클라이언트는 STT 요청 후 이 API를 이용해 변환 중·완료·실패 상태를 구분하고 체크리스트 항목의 메모 목록을 갱신한다.

---

### Request

- Request HTTP Method: `GET`
- 인증 필요: 필요
- Request Body: 없음

#### Request Header

```
Authorization: Bearer {accessToken}
```

#### Path Variable

```
GET /api/v1/studies/7/field-visit/stt/stt-4c7186fb
```

| 필드 | 타입 | 필수 | 설명 |
| --- | --- | --- | --- |
| `studyId` | Long | Y | STT 작업이 속한 스터디 ID |
| `sttId` | String | Y | STT 변환 요청에서 받은 작업 ID |

---

### STT 상태

| 값 | 설명 |
| --- | --- |
| `PENDING` | 작업 대기 |
| `PROCESSING` | 음성 분석·텍스트 변환 중 |
| `DONE` | 변환 완료 및 현장 기록 생성 완료 |
| `FAILED` | 변환 실패 |

---

### 처리 기준

#### 1. 작업 접근 권한

- 로그인 회원은 해당 STT 작업을 요청한 작성자여야 한다.
- 작업의 `studyId`, `memberId`, `checklistItemId`가 요청 정보와 일치해야 한다.
- 다른 회원의 음성 처리 상태와 변환 텍스트는 조회할 수 없다.
- 완료된 스터디에서도 작성자는 본인의 완료·실패 결과를 읽기 전용으로 조회할 수 있다.

#### 2. 상태별 응답

- `PENDING`, `PROCESSING` 상태에서는 `sourceId`, `textContent`, `completedAt`을 `null`로 반환한다.
- `DONE` 상태에서는 생성된 `field_record.id`를 `sourceId`로 반환한다.
- 완료된 현장 기록의 `sourceType`은 `STT`, `sttStatus`는 `DONE`이다.
- `FAILED` 상태에서는 사용자에게 표시 가능한 실패 사유와 `retryable`을 반환한다.
- 내부 스택 트레이스, 모델 응답 원문, S3 Key는 반환하지 않는다.

#### 3. 원본 음성 접근

- 모든 상태에서 일반 응답에 음성 원본 URL을 포함하지 않는다.
- `DONE`이면 원본 음성은 삭제되어야 한다.
- `FAILED`이고 재처리 기간이 남아 있어도 재처리는 retry API를 통해서만 수행한다.

#### 4. 재처리 가능 여부

- 실패 상태이고 음성 임시 파일이 존재하며 `expiresAt` 이전이면 `retryable = true`다.
- 음성 파일이 삭제·만료되었거나 오류가 재처리 불가능 유형이면 `retryable = false`다.
- `PENDING`, `PROCESSING`, `DONE`에서는 `retryable = false`다.

#### 5. 조회 빈도

- 프론트는 과도한 폴링을 피하고 일정 간격으로 조회한다.
- 앱이 백그라운드로 전환되면 폴링을 중단하고 복귀 시 재개한다.
- 서버는 동일 상태 조회를 멱등하게 처리한다.

---

### Response

#### 200 OK — 처리 중

```json
{
  "success": true,
  "code": "FIELD_STT_STATUS_SUCCESS",
  "message": "음성 변환 상태 조회에 성공했습니다.",
  "data": {
    "sttId": "stt-4c7186fb",
    "studyId": 7,
    "checklistItemId": 501,
    "status": "PROCESSING",
    "sourceId": null,
    "textContent": null,
    "failReason": null,
    "retryable": false,
    "requestedAt": "2026-07-25T14:25:00+09:00",
    "completedAt": null
  },
  "timestamp": "2026-07-25T14:25:04+09:00"
}
```

#### 200 OK — 변환 완료

```json
{
  "success": true,
  "code": "FIELD_STT_STATUS_SUCCESS",
  "message": "음성 변환 상태 조회에 성공했습니다.",
  "data": {
    "sttId": "stt-4c7186fb",
    "studyId": 7,
    "checklistItemId": 501,
    "status": "DONE",
    "sourceId": 830,
    "textContent": "역에서 단지 입구까지 경사가 있고 보도 폭은 충분합니다.",
    "failReason": null,
    "retryable": false,
    "requestedAt": "2026-07-25T14:25:00+09:00",
    "completedAt": "2026-07-25T14:25:08+09:00"
  },
  "timestamp": "2026-07-25T14:25:10+09:00"
}
```

#### 200 OK — 변환 실패

```json
{
  "success": true,
  "code": "FIELD_STT_STATUS_SUCCESS",
  "message": "음성 변환 상태 조회에 성공했습니다.",
  "data": {
    "sttId": "stt-9f2021ab",
    "studyId": 7,
    "checklistItemId": 503,
    "status": "FAILED",
    "sourceId": null,
    "textContent": null,
    "failReason": "음성에서 인식 가능한 발화를 찾지 못했습니다.",
    "retryable": true,
    "requestedAt": "2026-07-25T14:27:00+09:00",
    "completedAt": null
  },
  "timestamp": "2026-07-25T14:27:10+09:00"
}
```

---

### Response Field

| 필드 | 타입 | 설명 |
| --- | --- | --- |
| `sttId` | String | STT 작업 ID |
| `studyId` | Long | 스터디 ID |
| `checklistItemId` | Long | 결과가 연결될 항목 ID |
| `status` | String | STT 처리 상태 |
| `sourceId` | Long | null | 완료된 현장 기록 ID |
| `textContent` | String | null | 변환 텍스트 |
| `failReason` | String | null | 사용자 표시용 실패 사유 |
| `retryable` | Boolean | 현재 재처리 가능 여부 |
| `requestedAt` | String | 최초 요청 시각 |
| `completedAt` | String | null | 변환 완료 시각 |

---

### Exception

#### 401 Unauthorized

```json
{
  "success": false,
  "code": "AUTH_ACCESS_TOKEN_INVALID",
  "message": "로그인이 필요합니다.",
  "data": null,
  "timestamp": "2026-07-25T14:25:10+09:00"
}
```

---

#### 403 Forbidden — 다른 회원의 STT 작업

```json
{
  "success": false,
  "code": "FIELD_STT_STATUS_FORBIDDEN",
  "message": "본인이 요청한 음성 변환만 확인할 수 있습니다.",
  "data": null,
  "timestamp": "2026-07-25T14:25:10+09:00"
}
```

---

#### 404 Not Found — STT 작업 없음

```json
{
  "success": false,
  "code": "FIELD_STT_NOT_FOUND",
  "message": "음성 변환 작업을 찾을 수 없습니다.",
  "data": null,
  "timestamp": "2026-07-25T14:25:10+09:00"
}
```

---

#### 400 Bad Request — 스터디 불일치

```json
{
  "success": false,
  "code": "FIELD_STT_STUDY_MISMATCH",
  "message": "해당 스터디의 음성 변환 작업이 아닙니다.",
  "data": null,
  "timestamp": "2026-07-25T14:25:10+09:00"
}
```

---

#### 500 Internal Server Error

```json
{
  "success": false,
  "code": "COMMON_INTERNAL_SERVER_ERROR",
  "message": "일시적인 오류가 발생했습니다.",
  "data": null,
  "timestamp": "2026-07-25T14:25:10+09:00"
}
```

---

### 프론트 처리

```
STT 요청 성공
→ sttId 저장
→ 상태 조회를 일정 간격으로 호출

status = PENDING 또는 PROCESSING
→ 녹음 기록 영역에 "변환 중" 표시
→ 재처리·삭제 버튼 비활성화

status = DONE
→ 폴링 중단
→ sourceId와 textContent로 현장 기록 목록 갱신
→ 음성 원본 재생 버튼은 제공하지 않음

status = FAILED && retryable = true
→ 실패 사유 표시
→ "다시 시도" 버튼 노출

status = FAILED && retryable = false
→ 새로 녹음하도록 안내
```

---

## STT 변환 요청

Method: POST
Progress: 완료
URI: /api/v1/studies/{studyId}/field-visit/stt
담당자: 김윤석
연동여부: No

업로드 완료된 임시 음성 파일을 STT 작업에 제출하고 변환 결과를 특정 체크리스트 항목의 현장 기록으로 연결한다.

음성 원본은 STT 처리용 임시 객체로만 보관한다. 변환 성공 후 원본을 삭제하고 최종 영속 데이터는 변환 텍스트, 처리 상태, `sourceId`, `checklistItemId`로 제한한다.

---

### Request

- Request HTTP Method: `POST`
- 인증 필요: 필요
- Content-Type: `application/json`

#### Request Header

```
Authorization: Bearer {accessToken}
```

#### Path Variable

```
POST /api/v1/studies/7/field-visit/stt
```

| 필드 | 타입 | 필수 | 설명 |
| --- | --- | --- | --- |
| `studyId` | Long | Y | 음성 기록을 저장할 스터디 ID |

#### Request Body

```json
{
  "audioFileId": 90,
  "checklistItemId": 501,
  "clientRequestId": "81197c8f-780b-40c2-abf6-b83473de9c82"
}
```

| 필드 | 타입 | 필수 | 설명 |
| --- | --- | --- | --- |
| `audioFileId` | Long | Y | 업로드 완료된 임시 음성 파일 메타 ID |
| `checklistItemId` | Long | Y | 변환 결과를 연결할 본인 체크리스트 항목 ID |
| `clientRequestId` | String | Y | 중복 STT 요청 방지용 UUID |

---

### 처리 기준

#### 1. 회원·세션 상태 검증

- 로그인 회원은 해당 스터디의 승인 멤버이자 진행 중인 임장 참여자여야 한다.
- `field_session.status = IN_PROGRESS`, 본인 `field_participant.status = IN_PROGRESS`일 때만 요청할 수 있다.
- 개인 종료 또는 전체 마감 후에는 새로운 STT 요청을 생성할 수 없다.

#### 2. 체크리스트 항목 검증

- `checklistItemId`는 로그인 회원 본인의 체크리스트 항목이어야 한다.
- 다른 회원, 다른 스터디, 다른 세션의 항목에는 STT 결과를 연결할 수 없다.
- 체크리스트가 아직 생성되지 않았으면 STT 요청을 받지 않는다.

#### 3. 음성 파일 검증

- `audioFileId`에 해당하는 `file_meta`가 존재해야 한다.
- 파일 소유자는 로그인 회원이어야 한다.
- `study_id`가 요청 스터디와 일치해야 한다.
- `fileUsage = STT_AUDIO`, `uploadStatus = COMPLETED`, `deletedAt = null`이어야 한다.
- 파일의 `expiresAt`이 현재 시각 이전이면 요청할 수 없다.
- 지원하는 오디오 MIME 타입·용량·재생시간은 미디어 업로드 정책에서 검증한다.

#### 4. STT 작업 ID

- 최종 ERD에는 별도 `stt_job` 테이블이 정의되어 있지 않다.
- 본 API의 `sttId`는 AI 작업 시스템 또는 Redis에 저장되는 외부 작업 식별자로 사용한다.
- 작업 저장소는 최소 `sttId`, `memberId`, `studyId`, `sessionId`, `audioFileId`, `checklistItemId`, `clientRequestId`, 상태, 실패 사유, 생성·완료 시각을 보관해야 한다.
- 별도 DB 영속 작업 이력이 필요하면 `stt_job` 테이블을 추가해야 한다.

#### 5. 비동기 처리

- 요청을 검증한 뒤 STT 작업을 비동기로 발행한다.
- 최초 상태는 `PENDING`, 실제 처리 시작 후 `PROCESSING`이다.
- 완료되면 `sourceType = STT`인 `field_record`를 생성한다.
- 완료된 `field_record.id`를 `sourceId`로 반환한다.
- `text_content`에는 변환된 한국어 텍스트를 저장하고 `stt_status = DONE`으로 저장한다.
- 실패하면 작업 상태를 `FAILED`로 변경하며 재처리 가능 여부와 실패 사유를 관리한다.

#### 6. 음성 원본 삭제

- STT 성공 후 S3 음성 원본을 즉시 삭제한다.
- `file_meta.upload_status = DELETED`, `deletedAt = 삭제 시각`으로 전환하거나 동등한 접근 불가 상태를 보장한다.
- 성공 후 파일 메타·접근 URL 조회 API에서 음성 URL을 반환하지 않는다.
- 실패 원본은 `expiresAt`까지 재처리를 위해 임시 보관하고, 보존 기간이 지나면 삭제한다.

#### 7. 중복 요청

- 같은 `clientRequestId`는 작업을 한 번만 생성한다.
- 같은 `audioFileId`가 이미 STT 작업에 사용된 경우 새 작업을 중복 생성하지 않는다.
- 동일 요청 재전송 시 기존 `sttId`와 현재 상태를 반환한다.
- 기존 작업과 회원·항목이 다른데 같은 `clientRequestId`를 사용하면 충돌 오류를 반환한다.

---

### Response

#### 202 Accepted — 변환 요청 접수

```json
{
  "success": true,
  "code": "FIELD_STT_ACCEPTED",
  "message": "음성 변환을 시작했습니다.",
  "data": {
    "sttId": "stt-4c7186fb",
    "studyId": 7,
    "sessionId": 100,
    "audioFileId": 90,
    "checklistItemId": 501,
    "status": "PENDING",
    "sourceId": null,
    "requestedAt": "2026-07-25T14:25:00+09:00"
  },
  "timestamp": "2026-07-25T14:25:00+09:00"
}
```

#### 200 OK — 동일 요청이 이미 존재

```json
{
  "success": true,
  "code": "FIELD_STT_ALREADY_REQUESTED",
  "message": "이미 접수된 음성 변환 요청입니다.",
  "data": {
    "sttId": "stt-4c7186fb",
    "studyId": 7,
    "sessionId": 100,
    "audioFileId": 90,
    "checklistItemId": 501,
    "status": "PROCESSING",
    "sourceId": null,
    "requestedAt": "2026-07-25T14:25:00+09:00"
  },
  "timestamp": "2026-07-25T14:25:02+09:00"
}
```

---

### Response Field

| 필드 | 타입 | 설명 |
| --- | --- | --- |
| `sttId` | String | STT 비동기 작업 ID |
| `studyId` | Long | 스터디 ID |
| `sessionId` | Long | 임장 세션 ID |
| `audioFileId` | Long | 임시 음성 파일 ID |
| `checklistItemId` | Long | 결과 연결 대상 항목 ID |
| `status` | String | `PENDING`, `PROCESSING`, `DONE`, `FAILED` |
| `sourceId` | Long | null | 완료된 현장 기록 ID. 완료 전에는 `null` |
| `requestedAt` | String | STT 요청 접수 시각 |

---

### Exception

#### 400 Bad Request — 음성 파일 유형 오류

```json
{
  "success": false,
  "code": "FIELD_STT_AUDIO_INVALID",
  "message": "STT 처리에 사용할 수 없는 음성 파일입니다.",
  "data": {
    "audioFileId": 90,
    "requiredFileUsage": "STT_AUDIO"
  },
  "timestamp": "2026-07-25T14:25:00+09:00"
}
```

---

#### 400 Bad Request — clientRequestId 충돌

```json
{
  "success": false,
  "code": "FIELD_STT_IDEMPOTENCY_KEY_REUSED",
  "message": "동일한 요청 ID를 다른 음성 변환에 사용할 수 없습니다.",
  "data": null,
  "timestamp": "2026-07-25T14:25:00+09:00"
}
```

---

#### 401 Unauthorized

```json
{
  "success": false,
  "code": "AUTH_ACCESS_TOKEN_INVALID",
  "message": "로그인이 필요합니다.",
  "data": null,
  "timestamp": "2026-07-25T14:25:00+09:00"
}
```

---

#### 403 Forbidden — 파일 또는 항목 접근 불가

```json
{
  "success": false,
  "code": "FIELD_STT_REQUEST_FORBIDDEN",
  "message": "해당 음성 또는 체크리스트 항목에 접근할 수 없습니다.",
  "data": null,
  "timestamp": "2026-07-25T14:25:00+09:00"
}
```

---

#### 404 Not Found — 음성 파일 없음

```json
{
  "success": false,
  "code": "MEDIA_FILE_NOT_FOUND",
  "message": "음성 파일을 찾을 수 없습니다.",
  "data": null,
  "timestamp": "2026-07-25T14:25:00+09:00"
}
```

---

#### 404 Not Found — 체크리스트 항목 없음

```json
{
  "success": false,
  "code": "CHECKLIST_ITEM_NOT_FOUND",
  "message": "체크리스트 항목을 찾을 수 없습니다.",
  "data": null,
  "timestamp": "2026-07-25T14:25:00+09:00"
}
```

---

#### 409 Conflict — 개인 임장 종료

```json
{
  "success": false,
  "code": "FIELD_PARTICIPANT_ALREADY_ENDED",
  "message": "임장을 종료한 뒤에는 음성 기록을 추가할 수 없습니다.",
  "data": null,
  "timestamp": "2026-07-25T14:25:00+09:00"
}
```

---

#### 410 Gone — 음성 파일 만료

```json
{
  "success": false,
  "code": "FIELD_STT_AUDIO_EXPIRED",
  "message": "음성 파일의 재처리 가능 기간이 만료되었습니다.",
  "data": {
    "audioFileId": 90
  },
  "timestamp": "2026-07-25T14:25:00+09:00"
}
```

---

#### 500 Internal Server Error

```json
{
  "success": false,
  "code": "COMMON_INTERNAL_SERVER_ERROR",
  "message": "일시적인 오류가 발생했습니다.",
  "data": null,
  "timestamp": "2026-07-25T14:25:00+09:00"
}
```

---

### 프론트 처리

```
음성 기록 선택
→ 녹음 시작·종료
→ STT_AUDIO 용도로 Presigned URL 발급
→ S3 업로드 및 완료 처리
→ audioFileId, checklistItemId, clientRequestId로 STT 요청

202 Accepted 또는 FIELD_STT_ALREADY_REQUESTED
→ sttId를 로컬에 저장
→ 항목 기록 목록에 변환 중 상태 표시
→ 상태 조회 API 주기적 호출

앱 백그라운드 전환
→ sttId와 checklistItemId를 로컬에 보존
→ 복귀 후 상태 조회 재개

FIELD_STT_AUDIO_EXPIRED
→ 기존 음성으로 재시도하지 않음
→ 다시 녹음하도록 안내
```

---

## 현장 기록 삭제

Method: DELETE
Progress: 완료
URI: /api/v1/studies/{studyId}/field-visit/records/{recordId}
담당자: 윤다인
연동여부: Yes

로그인한 회원이 임장 진행 중 본인이 작성한 텍스트·사진·STT 현장 기록을 삭제한다.

삭제는 Soft Delete로 처리하며 기록은 일반 목록과 리포트 신규 생성 입력에서 제외된다. 다른 참여자의 기록은 삭제할 수 없고 개인 임장 종료 후에는 본인 기록도 삭제할 수 없다.

쓰기 조건: ACTIVE 멤버, session/participant `IN_PROGRESS`, study의 `report` 미존재. `report` 존재 시 `FIELD_VISIT_REPORT_LOCKED`.

---

### Request

- Request HTTP Method: `DELETE`
- 인증 필요: 필요
- Request Body: 없음

#### Request Header

```
Authorization: Bearer {accessToken}
```

#### Path Variable

```
DELETE /api/v1/studies/7/field-visit/records/812
```

| 필드 | 타입 | 필수 | 설명 |
| --- | --- | --- | --- |
| `studyId` | Long | Y | 현장 기록이 속한 스터디 ID |
| `recordId` | Long | Y | 삭제할 현장 기록 ID. API에서는 `sourceId`와 동일한 값 |

---

### 처리 기준

#### 1. 기록·스터디 일치 확인

- `recordId`에 해당하는 `field_record`를 조회한다.
- 기록의 세션이 Path Variable의 `studyId`에 속하는지 확인한다.
- 다른 스터디의 기록 ID를 조합한 요청은 처리하지 않는다.
- 존재하지 않는 기록과 이미 물리적으로 제거된 기록은 `FIELD_RECORD_NOT_FOUND`로 처리한다.

#### 2. 작성자 권한

- 로그인한 회원 ID는 Access Token에서 확인한다.
- `field_record.author_id`가 로그인 회원 ID와 일치해야 한다.
- 스터디장도 다른 참여자의 원문 기록을 대신 삭제할 수 없다.
- 리포트 근거에서 제외해야 할 부적절한 기록의 운영 숨김 기능은 별도 관리자 기능이며 본 API 범위에 포함하지 않는다.

#### 3. 삭제 가능 시점

- `field_session.status = IN_PROGRESS`여야 한다.
- 본인 `field_participant.status = IN_PROGRESS`여야 한다.
- 개인 종료 후에는 세션 전체가 진행 중이어도 삭제할 수 없다.
- 세션 종료 또는 리포트 생성 단계 진입 후에는 원본 무결성을 위해 삭제할 수 없다.

#### 4. Soft Delete

- `field_record.deleted_at`에 서버 현재 시각을 저장한다.
- 기록 행을 물리 삭제하지 않는다.
- 삭제된 기록은 현장 기록 목록, 체크리스트 `recordCount`, 리포트 신규 생성 입력에서 제외한다.
- 이미 생성 완료된 리포트가 해당 기록을 참조하고 있는 상황은 정상 흐름상 발생하지 않도록 임장 종료 후 삭제를 차단한다.

#### 5. 사진 파일 처리

- 사진 기록을 삭제해도 `file_meta`를 즉시 물리 삭제할지는 파일 보존 정책에 따른다.
- 기록과 연결된 사진이 다른 곳에서 사용되지 않는 경우 삭제 상태로 전환하거나 정리 배치 대상으로 표시할 수 있다.
- 기록 삭제 직후에는 사진 접근 URL을 새로 발급하지 않는다.

#### 6. STT 기록 처리

- STT 완료 기록을 삭제하면 변환 텍스트가 목록과 리포트 입력에서 제외된다.
- 성공 후 이미 삭제된 음성 원본은 복구하지 않는다.
- 진행 중 STT 기록 삭제를 허용할 경우 AI 작업 취소가 필요하므로, 본 명세에서는 `PENDING` 또는 `PROCESSING` STT 기록 삭제를 제한한다.

#### 7. 멱등성

- 이미 Soft Delete된 기록에 동일 요청이 반복되면 오류 대신 현재 삭제 상태를 반환한다.
- 동일 요청이 반복되어도 `deletedAt`은 최초 삭제 시각을 유지한다.
- 삭제 후 항목별 기록 수를 다시 계산해 반환한다.

---

### Response

#### 200 OK — 삭제 완료

```json
{
  "success": true,
  "code": "FIELD_RECORD_DELETE_SUCCESS",
  "message": "현장 기록을 삭제했습니다.",
  "data": {
    "studyId": 7,
    "sessionId": 100,
    "sourceId": 812,
    "checklistItemId": 501,
    "author": {
      "memberId": 42,
      "nickname": "루돌푸",
      "selectedCharacterId": "PALBANG"
    },
    "sourceType": "TEXT",
    "textContent": "단지 출입구에서 역 개찰구까지 약 8분이 걸렸습니다.",
    "photo": null,
    "sttStatus": null,
    "clientRequestId": "47baaf21-f10f-468e-a279-4dc969c829dd",
    "deleted": true,
    "deletedAt": "2026-07-25T14:40:00+09:00",
    "itemRecordCount": 2,
    "createdAt": "2026-07-25T14:15:00+09:00",
    "updatedAt": "2026-07-25T14:40:00+09:00"
  },
  "timestamp": "2026-07-25T14:40:00+09:00"
}
```

#### 200 OK — 이미 삭제된 기록

```json
{
  "success": true,
  "code": "FIELD_RECORD_ALREADY_DELETED",
  "message": "이미 삭제된 현장 기록입니다.",
  "data": {
    "studyId": 7,
    "sessionId": 100,
    "sourceId": 812,
    "checklistItemId": 501,
    "author": {
      "memberId": 42,
      "nickname": "루돌푸",
      "selectedCharacterId": "PALBANG"
    },
    "sourceType": "TEXT",
    "textContent": "단지 출입구에서 역 개찰구까지 약 8분이 걸렸습니다.",
    "photo": null,
    "sttStatus": null,
    "clientRequestId": "47baaf21-f10f-468e-a279-4dc969c829dd",
    "deleted": true,
    "deletedAt": "2026-07-25T14:40:00+09:00",
    "itemRecordCount": 2,
    "createdAt": "2026-07-25T14:15:00+09:00",
    "updatedAt": "2026-07-25T14:40:00+09:00"
  },
  "timestamp": "2026-07-25T14:41:00+09:00"
}
```

---

### Response Field

| 필드 | 타입 | 설명 |
| --- | --- | --- |
| `studyId` | Long | 스터디 ID |
| `sessionId` | Long | 임장 세션 ID |
| `sourceId` | Long | 삭제된 현장 기록 ID |
| `checklistItemId` | Long | 기록이 연결되었던 체크리스트 항목 ID |
| `author` | Object | 작성자 공개 정보 |
| `sourceType` | String | 기록 유형 |
| `textContent` | String | null | 삭제 시점의 텍스트 |
| `photo` | Object | null | 삭제 후 사진 URL은 재발급하지 않음 |
| `sttStatus` | String | null | STT 상태 |
| `clientRequestId` | String | 최초 생성 요청 식별자 |
| `deleted` | Boolean | 삭제 여부, 항상 `true` |
| `deletedAt` | String | 최초 삭제 시각 |
| `itemRecordCount` | Integer | 삭제 후 해당 항목의 본인 기록 수 |
| `createdAt` | String | 최초 생성 시각 |
| `updatedAt` | String | 마지막 갱신 시각 |

---

### Exception

#### 401 Unauthorized

```json
{
  "success": false,
  "code": "AUTH_ACCESS_TOKEN_INVALID",
  "message": "로그인이 필요합니다.",
  "data": null,
  "timestamp": "2026-07-25T14:40:00+09:00"
}
```

---

#### 403 Forbidden — 작성자 아님

```json
{
  "success": false,
  "code": "FIELD_RECORD_DELETE_FORBIDDEN",
  "message": "본인이 작성한 현장 기록만 삭제할 수 있습니다.",
  "data": null,
  "timestamp": "2026-07-25T14:40:00+09:00"
}
```

---

#### 404 Not Found — 현장 기록 없음

```json
{
  "success": false,
  "code": "FIELD_RECORD_NOT_FOUND",
  "message": "현장 기록을 찾을 수 없습니다.",
  "data": null,
  "timestamp": "2026-07-25T14:40:00+09:00"
}
```

---

#### 409 Conflict — 개인 임장 종료

```json
{
  "success": false,
  "code": "FIELD_PARTICIPANT_ALREADY_ENDED",
  "message": "임장을 종료한 뒤에는 현장 기록을 삭제할 수 없습니다.",
  "data": null,
  "timestamp": "2026-07-25T14:40:00+09:00"
}
```

---

#### 409 Conflict — 세션 종료

```json
{
  "success": false,
  "code": "FIELD_VISIT_ALREADY_ENDED",
  "message": "종료된 임장 세션의 기록은 삭제할 수 없습니다.",
  "data": null,
  "timestamp": "2026-07-25T14:40:00+09:00"
}
```

---

#### 409 Conflict — STT 처리 중

```json
{
  "success": false,
  "code": "FIELD_RECORD_STT_PROCESSING",
  "message": "음성 변환이 끝난 뒤 기록을 삭제해 주세요.",
  "data": {
    "sttStatus": "PROCESSING"
  },
  "timestamp": "2026-07-25T14:40:00+09:00"
}
```

---

#### 500 Internal Server Error

```json
{
  "success": false,
  "code": "COMMON_INTERNAL_SERVER_ERROR",
  "message": "일시적인 오류가 발생했습니다.",
  "data": null,
  "timestamp": "2026-07-25T14:40:00+09:00"
}
```

---

### 프론트 처리

```
본인 기록의 삭제 버튼 선택
→ 삭제 확인 모달 표시
→ 확인 시 DELETE API 호출

삭제 성공 또는 FIELD_RECORD_ALREADY_DELETED
→ 현재 목록에서 해당 기록 제거
→ 항목 recordCount를 응답 값으로 갱신

FIELD_PARTICIPANT_ALREADY_ENDED 또는 FIELD_VISIT_ALREADY_ENDED
→ 삭제 버튼 숨김
→ 체크리스트와 메모 목록을 읽기 전용으로 전환

FIELD_RECORD_STT_PROCESSING
→ 삭제하지 않음
→ 변환 상태 조회를 계속하고 완료 후 삭제 가능 상태 갱신
```

---

## 현장 기록 수정

Method: PATCH
Progress: 완료
URI: /api/v1/studies/{studyId}/field-visit/records/{recordId}
담당자: 윤다인
연동여부: Yes

로그인한 회원이 임장 진행 중 본인이 작성한 현장 기록을 수정한다.

텍스트 기록의 내용 교정, STT 변환 결과 교정, 사진 교체와 체크리스트 항목 변경을 지원한다.

임장이 종료되거나 리포트 생성이 시작된 이후(`report` 행 존재)에는 근거 데이터의 일관성을 위해 수정할 수 없다.
세션 종료 코드는 `FIELD_VISIT_ALREADY_ENDED`, 항목 없음은 `CHECKLIST_ITEM_NOT_FOUND`를 사용한다.

---

### Request

- Request HTTP Method: `PATCH`
- 인증 필요: 필요
- Content-Type: `application/json`

#### Request Header

```
Authorization: Bearer {accessToken}
```

#### Path Variable

```
PATCH /api/v1/studies/10/field-visit/records/501
```

| 필드 | 타입 | 필수 | 설명 |
| --- | --- | --- | --- |
| `studyId` | Long | Y | 현장 기록이 속한 스터디 ID |
| `recordId` | Long | Y | 수정할 현장 기록 ID |

#### Request Body — 텍스트 기록 수정

```
{
  "checklistItemId":101,
  "textContent":"옥수역 3번 출구에서 단지 정문까지 도보 약 8분이 걸렸습니다."
}
```

#### Request Body — STT 변환 결과 교정

```
{
  "textContent":"단지 내부 경사가 다소 높고 유모차 이동 시 주의가 필요합니다."
}
```

#### Request Body — 사진 교체

```
{
  "checklistItemId":105,
  "photoFileId":302
}
```

| 필드 | 타입 | 필수 | 설명 |
| --- | --- | --- | --- |
| `checklistItemId` | Long | N | 기록을 연결할 체크리스트 항목 ID |
| `textContent` | String | N | 수정할 텍스트 또는 STT 교정 내용 |
| `photoFileId` | Long | N | 교체할 업로드 완료 사진 파일 ID |
- 수정할 필드를 최소 한 개 이상 전달해야 한다.
- `sourceType`은 Request Body로 받지 않는다.
- `clientRequestId`는 기존 기록의 값을 유지하며 수정하지 않는다.

---

### 처리 기준

#### 1. 회원 및 스터디 확인

- 로그인 회원 ID는 Access Token에서 확인한다.
- Path Variable의 `studyId`에 해당하는 스터디를 조회한다.
- 존재하지 않거나 삭제된 스터디는 조회할 수 없다.
- 로그인 회원은 해당 스터디의 승인된 멤버여야 한다.
- 신청 대기·거절 상태의 회원은 현장 기록을 수정할 수 없다.
- 강퇴되거나 탈퇴한 회원은 기록을 수정할 수 없다.

---

#### 2. 임장 진행 상태 확인

- 해당 스터디의 `field_session`을 조회한다.
- 임장 세션의 상태가 `IN_PROGRESS`인 경우에만 수정할 수 있다.
- 임장 세션이 `ENDED`이면 모든 현장 기록은 읽기 전용이다.
- 로그인 회원의 `field_participant.status`도 `IN_PROGRESS`여야 한다.
- 본인이 개인 임장을 종료해 `field_participant.status = ENDED`가 된 후에는 전체 세션이 진행 중이더라도 수정할 수 없다.
- 리포트 생성이 이미 시작된 경우 근거 데이터의 일관성을 위해 수정할 수 없다.

---

#### 3. 현장 기록 확인

- `recordId`에 해당하는 `field_record`를 조회한다.
- 현장 기록의 `session_id`가 현재 스터디의 임장 세션과 일치해야 한다.
- `deleted_at IS NULL`인 기록만 수정할 수 있다.
- 삭제된 기록은 수정할 수 없다.
- 현장 기록의 `author_id`가 로그인 회원 ID와 일치해야 한다.
- 스터디장이라도 다른 참여자가 작성한 기록을 수정할 수 없다.

조회 예시:

```
SELECT*FROM field_recordWHERE id= :recordIdAND session_id= :sessionIdAND deleted_atISNULL;
```

---

#### 4. 공통 수정 규칙

- Request Body에 포함된 필드만 수정한다.
- 수정할 필드가 하나도 없으면 오류를 반환한다.
- 기존 기록의 `source_type`은 변경할 수 없다.
- 기존 기록의 `author_id`, `session_id`, `client_request_id`, `created_at`은 변경하지 않는다.
- 실제 변경이 발생하면 `updated_at`을 현재 시각으로 갱신한다.
- 기존 값과 동일한 내용으로 다시 요청해도 오류 없이 현재 결과를 반환한다.
- 동시에 수정 요청이 발생할 가능성이 있다면 낙관적 잠금 또는 최종 수정 시각 비교를 적용할 수 있다.

---

#### 5. 체크리스트 항목 변경

- `checklistItemId`가 전달되면 해당 체크리스트 항목이 현재 임장 세션의 체크리스트에 속하는지 확인한다.
- 다른 스터디 또는 다른 임장 세션의 체크리스트 항목으로 변경할 수 없다.
- 존재하지 않거나 삭제된 체크리스트 항목으로 변경할 수 없다.
- 기록을 다른 체크리스트 항목으로 이동해도 작성자와 기록 유형은 유지한다.
- 체크리스트 항목 변경 후 기존 항목과 새 항목의 `recordCount`가 목록 조회 시 올바르게 다시 계산되어야 한다.

---

#### 6. TEXT 기록 수정

`sourceType = TEXT`인 경우:

- `textContent`와 `checklistItemId`를 수정할 수 있다.
- `textContent`는 공백만으로 구성할 수 없다.
- 앞뒤 공백을 제거한 후 저장한다.
- `photoFileId`는 전달할 수 없다.
- 텍스트를 `PHOTO` 또는 `STT` 기록으로 변경할 수 없다.

수정 예시:

```
UPDATE field_recordSET text_content= :textContent,
    checklist_item_id= COALESCE(:checklistItemId, checklist_item_id),
    updated_at= now()WHERE id= :recordId;
```

---

#### 7. PHOTO 기록 수정

`sourceType = PHOTO`인 경우:

- `photoFileId`와 `checklistItemId`를 수정할 수 있다.
- `photoFileId`가 전달되면 해당 파일이 로그인 회원이 업로드한 파일인지 확인한다.
- 파일의 `fileUsage`는 `FIELD_PHOTO`여야 한다.
- 파일 업로드 상태는 완료 상태여야 한다.
- 삭제·만료·업로드 실패 상태의 파일로 교체할 수 없다.
- 다른 현장 기록에 이미 연결된 파일의 재사용 허용 여부는 파일 정책에 따른다.
- 사진을 교체한 뒤 기존 파일이 어느 데이터에서도 사용되지 않으면 비동기 정리 대상으로 처리할 수 있다.
- `textContent`를 사진 설명으로 사용하지 않는 현재 구조라면 PHOTO 기록에서는 `textContent` 수정을 허용하지 않는다.
- 사진 기록을 텍스트 기록으로 변경할 수 없다.

---

#### 8. STT 기록 수정

`sourceType = STT`인 경우:

- STT 처리 상태가 `DONE`인 경우에만 변환된 `textContent`를 교정할 수 있다.
- `checklistItemId`도 변경할 수 있다.
- STT가 `PENDING` 또는 `PROCESSING` 상태이면 수정할 수 없다. 오류 코드는 `FIELD_RECORD_STT_PROCESSING`이다.
- STT가 `FAILED`이면 수정할 수 없다. 오류 코드는 `FIELD_RECORD_STT_UPDATE_NOT_ALLOWED`이다. 재처리는 BE-016 retry API 책임이다.
- STT `FAILED` 기록은 삭제만 가능하다(`canEdit=false`, `canDelete=true`).
- 현장 기록 수정 API로 음성 파일을 교체하거나 STT 작업을 다시 실행하지 않는다.
- 사용자가 변환 결과를 수정해도 `sourceType = STT`는 유지한다.
- 사용자가 수정한 문장은 이후 AI 리포트 근거로 사용한다.
- STT 원본 음성이 이미 삭제되었더라도 변환 결과 텍스트는 수정할 수 있다.

---

#### 9. 리포트 근거 일관성

- 임장 종료 전 수정된 최신 기록이 최종 AI 리포트 생성에 사용된다.
- 전체 임장이 종료되어 리포트 생성이 시작된 이후에는 기록을 수정할 수 없다.
- 이미 생성된 `report_evidence`가 참조하는 기록을 수정하지 않는다.
- 리포트 생성 실패 후 재시도하는 경우에도 임장이 종료된 상태이므로 현장 기록은 수정할 수 없다.
- 리포트 재생성 API는 동일한 확정 근거를 사용한다.

---

#### 10. 알림 처리

- 현장 기록 수정만으로 다른 스터디원에게 서비스 알림이나 FCM Push를 발송하지 않는다.
- 채팅에 시스템 메시지를 자동으로 생성하지 않는다.

---

### Response

#### 200 OK — 텍스트 기록 수정 완료

```json
{
  "success": true,
  "code": "FIELD_RECORD_UPDATE_SUCCESS",
  "message": "현장 기록이 수정되었습니다.",
  "data": {
    "studyId": 10,
    "sessionId": 20,
    "sourceId": 501,
    "checklistItemId": 101,
    "author": {
      "memberId": 1,
      "nickname": "옥수탐방러",
      "selectedCharacterId": "JIPKONG"
    },
    "sourceType": "TEXT",
    "textContent": "옥수역 3번 출구에서 단지 정문까지 도보 약 8분이 걸렸습니다.",
    "photo": null,
    "sttStatus": null,
    "clientRequestId": "47baaf21-f10f-468e-a279-4dc969c829dd",
    "deleted": false,
    "deletedAt": null,
    "itemRecordCount": 2,
    "createdAt": "2026-07-25T10:10:00+09:00",
    "updatedAt": "2026-07-25T10:25:00+09:00"
  },
  "timestamp": "2026-07-25T10:25:00+09:00"
}
```

#### 200 OK — STT 결과 교정 완료

```json
{
  "success": true,
  "code": "FIELD_RECORD_UPDATE_SUCCESS",
  "message": "현장 기록이 수정되었습니다.",
  "data": {
    "studyId": 10,
    "sessionId": 20,
    "sourceId": 502,
    "checklistItemId": 103,
    "author": {
      "memberId": 1,
      "nickname": "옥수탐방러",
      "selectedCharacterId": "JIPKONG"
    },
    "sourceType": "STT",
    "textContent": "단지 내부 경사가 다소 높고 유모차 이동 시 주의가 필요합니다.",
    "photo": null,
    "sttStatus": "DONE",
    "clientRequestId": "STT:stt-abc",
    "deleted": false,
    "deletedAt": null,
    "itemRecordCount": 1,
    "createdAt": "2026-07-25T10:12:00+09:00",
    "updatedAt": "2026-07-25T10:27:00+09:00"
  },
  "timestamp": "2026-07-25T10:27:00+09:00"
}
```

#### 200 OK — 사진 기록 수정 완료

```json
{
  "success": true,
  "code": "FIELD_RECORD_UPDATE_SUCCESS",
  "message": "현장 기록이 수정되었습니다.",
  "data": {
    "studyId": 10,
    "sessionId": 20,
    "sourceId": 503,
    "checklistItemId": 105,
    "author": {
      "memberId": 1,
      "nickname": "옥수탐방러",
      "selectedCharacterId": "JIPKONG"
    },
    "sourceType": "PHOTO",
    "textContent": null,
    "photo": {
      "fileId": 302,
      "originalName": "parking-entrance.jpg",
      "contentType": "image/jpeg",
      "fileUrl": "https://cdn.example.com/field/302.jpg",
      "available": true,
      "expiresAt": "2026-07-25T10:40:00+09:00"
    },
    "sttStatus": null,
    "clientRequestId": "92d93d31-1d7f-4e97-81a1-e6b61788422f",
    "deleted": false,
    "deletedAt": null,
    "itemRecordCount": 1,
    "createdAt": "2026-07-25T10:15:00+09:00",
    "updatedAt": "2026-07-25T10:30:00+09:00"
  },
  "timestamp": "2026-07-25T10:30:00+09:00"
}
```

### Response Field

| 필드 | 타입 | 설명 |
| --- | --- | --- |
| `studyId` | Long | 스터디 ID |
| `sessionId` | Long | 현장 임장 세션 ID |
| `sourceId` | Long | 수정한 현장 기록 ID (`field_record.id`) |
| `checklistItemId` | Long | 연결된 체크리스트 항목 ID |
| `author` | Object | 기록 작성자 공개 정보 |
| `sourceType` | String | 기록 출처 유형 |
| `textContent` | String | 텍스트 또는 STT 변환·교정 내용 |
| `photo` | Object | 사진 파일 정보, 사진이 아니면 `null` |
| `sttStatus` | String | STT 처리 상태, STT가 아니면 `null` |
| `clientRequestId` | String | 최초 생성 요청 식별자(유지) |
| `deleted` | Boolean | 삭제 여부 |
| `deletedAt` | String | 삭제 시각, 미삭제면 `null` |
| `itemRecordCount` | Integer | 수정 후 해당 항목의 본인 기록 수 |
| `createdAt` | String | 최초 기록 생성 시각(유지) |
| `updatedAt` | String | 마지막 수정 시각 |

#### Source Type Enum

| 값 | 설명 |
| --- | --- |
| `TEXT` | 사용자가 직접 입력한 텍스트 기록 |
| `PHOTO` | 사용자가 촬영하거나 업로드한 사진 기록 |
| `STT` | 음성을 STT로 변환한 텍스트 기록 |

---

### Exception

#### 400 Bad Request — 수정 필드 없음

```
{
  "success":false,
  "code":"FIELD_RECORD_UPDATE_EMPTY",
  "message":"수정할 현장 기록 내용을 입력해 주세요.",
  "data":null,
  "timestamp":"2026-07-25T10:30:00+09:00"
}
```

#### 400 Bad Request — 기록 유형에 맞지 않는 필드

```
{
  "success":false,
  "code":"FIELD_RECORD_UPDATE_FIELD_INVALID",
  "message":"현장 기록 유형에 맞지 않는 수정 항목입니다.",
  "data": {
    "sourceType":"TEXT",
    "invalidField":"photoFileId"
  },
  "timestamp":"2026-07-25T10:30:00+09:00"
}
```

#### 400 Bad Request — 텍스트 내용 오류

```
{
  "success":false,
  "code":"COMMON_INVALID_REQUEST",
  "message":"입력값을 확인해 주세요.",
  "data": {
    "field":"textContent",
    "reason":"텍스트 기록은 공백만으로 구성할 수 없습니다."
  },
  "timestamp":"2026-07-25T10:30:00+09:00"
}
```

#### 401 Unauthorized

```
{
  "success":false,
  "code":"AUTH_ACCESS_TOKEN_INVALID",
  "message":"로그인이 필요합니다.",
  "data":null,
  "timestamp":"2026-07-25T10:30:00+09:00"
}
```

#### 403 Forbidden — 스터디 멤버 아님

```
{
  "success":false,
  "code":"STUDY_MEMBER_ACCESS_DENIED",
  "message":"스터디 멤버만 현장 기록을 수정할 수 있습니다.",
  "data":null,
  "timestamp":"2026-07-25T10:30:00+09:00"
}
```

#### 403 Forbidden — 다른 사용자의 기록

```
{
  "success":false,
  "code":"FIELD_RECORD_UPDATE_FORBIDDEN",
  "message":"본인이 작성한 현장 기록만 수정할 수 있습니다.",
  "data":null,
  "timestamp":"2026-07-25T10:30:00+09:00"
}
```

#### 409 Conflict — 개인 임장 종료

```
{
  "success":false,
  "code":"FIELD_PARTICIPANT_ALREADY_ENDED",
  "message":"개인 임장을 종료한 후에는 현장 기록을 수정할 수 없습니다.",
  "data":null,
  "timestamp":"2026-07-25T10:30:00+09:00"
}
```

#### 409 Conflict — 전체 임장 종료

```
{
  "success":false,
  "code":"FIELD_VISIT_ALREADY_ENDED",
  "message":"종료된 임장의 현장 기록은 수정할 수 없습니다.",
  "data":null,
  "timestamp":"2026-07-25T10:30:00+09:00"
}
```

#### 409 Conflict — STT 처리 중

```
{
  "success":false,
  "code":"FIELD_RECORD_STT_PROCESSING",
  "message":"음성 변환이 완료된 후 기록을 수정해 주세요.",
  "data": {
    "sttStatus":"PROCESSING"
  },
  "timestamp":"2026-07-25T10:30:00+09:00"
}
```

#### 409 Conflict — 삭제된 기록

```
{
  "success":false,
  "code":"FIELD_RECORD_ALREADY_DELETED",
  "message":"삭제된 현장 기록은 수정할 수 없습니다.",
  "data":null,
  "timestamp":"2026-07-25T10:30:00+09:00"
}
```

#### 404 Not Found — 스터디 없음

```
{
  "success":false,
  "code":"STUDY_NOT_FOUND",
  "message":"스터디를 찾을 수 없습니다.",
  "data":null,
  "timestamp":"2026-07-25T10:30:00+09:00"
}
```

#### 404 Not Found — 현장 기록 없음

```
{
  "success":false,
  "code":"FIELD_RECORD_NOT_FOUND",
  "message":"현장 기록을 찾을 수 없습니다.",
  "data":null,
  "timestamp":"2026-07-25T10:30:00+09:00"
}
```

#### 404 Not Found — 체크리스트 항목 없음

```
{
  "success":false,
  "code":"FIELD_CHECKLIST_ITEM_NOT_FOUND",
  "message":"체크리스트 항목을 찾을 수 없습니다.",
  "data":null,
  "timestamp":"2026-07-25T10:30:00+09:00"
}
```

#### 404 Not Found — 사진 파일 없음

```
{
  "success":false,
  "code":"MEDIA_FILE_NOT_FOUND",
  "message":"사진 파일을 찾을 수 없습니다.",
  "data":null,
  "timestamp":"2026-07-25T10:30:00+09:00"
}
```

#### 500 Internal Server Error

```
{
  "success":false,
  "code":"COMMON_INTERNAL_SERVER_ERROR",
  "message":"일시적인 오류가 발생했습니다.",
  "data":null,
  "timestamp":"2026-07-25T10:30:00+09:00"
}
```

---

### 프론트 처리

```
임장 진행 화면에서 본인이 작성한 현장 기록 선택
→ 수정 또는 삭제 메뉴 표시
→ 수정 선택
→ 기록 유형에 맞는 수정 화면 표시

sourceType = TEXT
→ 기존 textContent를 입력창에 표시
→ 텍스트와 연결된 체크리스트 항목 수정 가능

sourceType = PHOTO
→ 기존 사진 미리보기 표시
→ 새 사진 업로드 완료 후 photoFileId를 수정 API에 전달
→ 사진 또는 체크리스트 항목 수정 가능

sourceType = STT, sttStatus = DONE
→ 변환된 textContent를 입력창에 표시
→ 사용자가 잘못 인식된 문장 교정
→ 수정된 텍스트를 PATCH 요청

sourceType = STT, sttStatus = PENDING 또는 PROCESSING
→ 수정 버튼 비활성화
→ "음성 변환이 완료된 후 수정할 수 있습니다." 표시

수정 성공
→ 현재 기록 카드 내용을 응답 데이터로 교체
→ updatedAt 갱신
→ 체크리스트 항목을 변경했다면 기존 항목과 새 항목의 기록 개수 갱신

FIELD_PARTICIPANT_ALREADY_ENDED 또는 FIELD_VISIT_ALREADY_ENDED
→ 수정 화면 종료
→ 기록을 읽기 전용으로 전환

FIELD_RECORD_UPDATE_FORBIDDEN
→ 수정 메뉴 숨김
→ 다른 참여자의 기록은 조회만 가능
```

---

## 현장 기록 저장

Method: POST
Progress: 완료
URI: /api/v1/studies/{studyId}/field-visit/records
담당자: 윤다인
연동여부: Yes

임장 참여자가 특정 체크리스트 항목에 텍스트 메모 또는 사진 기록을 저장한다.

모든 현장 기록은 작성자, 작성 시각, `checklistItemId`, `sourceType`, `sourceId`(= `field_record.id`)를 유지하며 이후 AI 리포트 근거로 사용될 수 있다. 음성 기록은 이 API가 아니라 STT 변환 요청 API를 통해 저장한다.

멱등성: `client_request_id` UNIQUE + `request_fingerprint`(최초 POST payload SHA-256). 동일 fingerprint 재요청은 200 `FIELD_RECORD_ALREADY_CREATED`, 다른 payload는 `FIELD_RECORD_IDEMPOTENCY_KEY_REUSED`(HTTP 400).
쓰기 시 `report` 존재하면 `FIELD_VISIT_REPORT_LOCKED`.

BE-017은 BE-015 공개 API 계약을 유지한 채, 오프라인 재전송·멱등 재시도의 서버 책임을 명확히 하고 불일치 재사용에 대한 안전 로그·동시성 보장을 보강한다.

---

### Request

- Request HTTP Method: `POST`
- 인증 필요: 필요
- Content-Type: `application/json`

#### Request Header

```
Authorization: Bearer {accessToken}
```

#### Path Variable

```
POST /api/v1/studies/7/field-visit/records
```

| 필드 | 타입 | 필수 | 설명 |
| --- | --- | --- | --- |
| `studyId` | Long | Y | 현장 기록을 저장할 스터디 ID |

#### Request Body — 텍스트 기록

```json
{
  "checklistItemId": 501,
  "sourceType": "TEXT",
  "textContent": "단지 출입구에서 역 개찰구까지 약 8분이 걸렸습니다.",
  "photoFileId": null,
  "clientRequestId": "47baaf21-f10f-468e-a279-4dc969c829dd"
}
```

#### Request Body — 사진 기록

```json
{
  "checklistItemId": 503,
  "sourceType": "PHOTO",
  "textContent": null,
  "photoFileId": 89,
  "clientRequestId": "92d93d31-1d7f-4e97-81a1-e6b61788422f"
}
```

| 필드 | 타입 | 필수 | 설명 |
| --- | --- | --- | --- |
| `checklistItemId` | Long | Y | 기록을 연결할 본인 체크리스트 항목 ID |
| `sourceType` | String | Y | 클라이언트 직접 저장은 `TEXT`, `PHOTO`만 허용 |
| `textContent` | String | 조건부 | `TEXT`일 때 필수, 앞뒤 공백 제거 후 1자 이상 |
| `photoFileId` | Long | 조건부 | `PHOTO`일 때 필수인 업로드 완료 파일 ID |
| `clientRequestId` | String | Y | 오프라인 재전송·중복 저장 방지용 UUID |

---

### 처리 기준

#### 1. 회원·임장 상태 검증

- 로그인한 회원 ID는 Access Token에서 확인한다.
- 로그인 회원은 해당 스터디의 승인 멤버이자 현재 세션 참여자여야 한다.
- `field_session.status = IN_PROGRESS`, 본인 `field_participant.status = IN_PROGRESS`인 경우에만 저장할 수 있다.
- 개인 종료 또는 전체 세션 종료 후에는 새 기록을 저장할 수 없다.

#### 2. 체크리스트 항목 검증

- `checklistItemId`는 로그인 회원 본인의 체크리스트 항목이어야 한다.
- 다른 참여자의 항목이나 다른 스터디·세션 항목에는 기록을 저장할 수 없다.
- 체크리스트 항목이 삭제되거나 존재하지 않으면 요청을 거절한다.
- 하나의 항목에 여러 개의 텍스트·사진·STT 기록을 저장할 수 있다.

#### 3. sourceType별 필수값

##### `TEXT`

- `textContent`가 필수다.
- 앞뒤 공백을 제거한 뒤 빈 문자열이면 저장할 수 없다.
- 최대 길이는 서비스 입력 UI와 서버 Bean Validation 기준으로 통일한다. 본 명세에서는 최대 2,000자를 권장한다.
- `photoFileId`는 `null`이어야 한다.

##### `PHOTO`

- `photoFileId`가 필수다.
- `textContent`는 사용하지 않으며 `null`로 전달한다.
- 사진 파일은 본인 소유, 동일 스터디 연결, `fileUsage = FIELD_PHOTO`, `uploadStatus = COMPLETED`, `deletedAt = null`이어야 한다.
- 지원하지 않는 MIME 타입 또는 허용 크기 초과 파일은 미디어 업로드 단계에서 차단한다.

##### `STT`

- `STT` 기록은 STT 완료 처리 과정에서 서버가 생성한다.
- 일반 클라이언트가 이 API에 `sourceType = STT`를 전달하면 `FIELD_RECORD_SOURCE_TYPE_NOT_ALLOWED` 오류를 반환한다.

#### 4. 멱등성·오프라인 재전송 (BE-015 + BE-017)

- Android는 현장 기록 요청 생성 시 `clientRequestId`를 UUID로 발급한다.
- 네트워크 오류 시 같은 `clientRequestId`와 같은 payload로 재전송한다.
- `field_record.client_request_id`는 전체 기록에서 유일하다(PostgreSQL UNIQUE).
- 같은 `clientRequestId`가 동일 요청으로 재전송되면 서버는 새 행을 만들지 않고 기존 결과를 200 `FIELD_RECORD_ALREADY_CREATED`로 반환한다.
- 동일 `clientRequestId`를 다른 요청에 재사용하면 HTTP 400 `FIELD_RECORD_IDEMPOTENCY_KEY_REUSED`로 거부한다.
- 재사용 비교 대상:
  - `sessionId`
  - `authorId`(요청 회원)
  - `checklistItemId`
  - `sourceType`
  - `request_fingerprint`(TEXT/PHOTO payload SHA-256)
- 스터디 일치는 path `studyId`로 쓰기 가능 세션을 로드한 뒤 `sessionId` 일치로 함께 검증한다.
- 동시에 같은 요청이 들어와도 PostgreSQL UNIQUE와 트랜잭션 처리로 기록은 한 건만 저장된다.
- 불일치 재사용은 `event=FIELD_RECORD_IDEMPOTENCY_MISMATCH` WARN 로그로 확인한다. 로그에는 식별자·불일치 항목만 남기고 `textContent` 원문·사진 URL·JWT·fingerprint 원문 등은 남기지 않는다.
- Android는 서버 성공 응답(201 또는 200)을 받은 요청을 오프라인 큐에서 제거한다. 저장 결과의 `sourceId`를 로컬 대기 기록과 연결할 수 있다.
- 서로 다른 `clientRequestId` 요청의 전송 순서는 Android 오프라인 큐 책임이다.
- 서버는 서로 다른 요청 간 도착 순서를 강제로 보장하거나 거부하지 않는다.
- `sequence` 필드와 Redis 큐는 사용하지 않는다.

#### 5. sourceId와 리포트 근거

- 생성된 `field_record.id`를 API의 `sourceId`로 사용한다.
- `sourceId`는 리포트 근거 연결 시 그대로 사용한다.
- 기록 저장 시점에는 어떤 리포트 문장에 사용될지 확정하지 않는다.
- 사진은 현재 요구사항에서 비전 분석 대상이 아니라 출처 메타데이터로만 사용할 수 있다.

#### 6. 파일 연결과 삭제

- 사진 파일 메타는 현장 기록과 연결한다.
- 기록 저장 실패 시 이미 업로드된 미사용 파일은 즉시 삭제하지 않고 미사용 파일 정리 정책에 따라 만료 처리할 수 있다.

---

### Response

#### 201 Created — 텍스트 기록 저장

```json
{
  "success": true,
  "code": "FIELD_RECORD_CREATE_SUCCESS",
  "message": "현장 기록을 저장했습니다.",
  "data": {
    "studyId": 7,
    "sessionId": 100,
    "record": {
      "sourceId": 814,
      "checklistItemId": 501,
      "sourceType": "TEXT",
      "textContent": "단지 출입구에서 역 개찰구까지 약 8분이 걸렸습니다.",
      "photo": null,
      "sttStatus": null,
      "author": {
        "memberId": 42,
        "nickname": "루돌푸",
        "selectedCharacterId": "PALBANG"
      },
      "isMine": true,
      "canEdit": true,
      "canDelete": true,
      "clientRequestId": "47baaf21-f10f-468e-a279-4dc969c829dd",
      "createdAt": "2026-07-25T14:22:00+09:00",
      "updatedAt": "2026-07-25T14:22:00+09:00"
    },
    "itemRecordCount": 3
  },
  "timestamp": "2026-07-25T14:22:00+09:00"
}
```

#### 201 Created — 사진 기록 저장

```json
{
  "success": true,
  "code": "FIELD_RECORD_CREATE_SUCCESS",
  "message": "현장 기록을 저장했습니다.",
  "data": {
    "studyId": 7,
    "sessionId": 100,
    "record": {
      "sourceId": 815,
      "checklistItemId": 503,
      "sourceType": "PHOTO",
      "textContent": null,
      "photo": {
        "fileId": 89,
        "originalName": "slope.jpg",
        "contentType": "image/jpeg",
        "fileUrl": "<https://s3.example.com/presigned/field-photo-89>",
        "available": true,
        "expiresAt": "2026-07-25T14:32:00+09:00"
      },
      "sttStatus": null,
      "author": {
        "memberId": 42,
        "nickname": "루돌푸",
        "selectedCharacterId": "PALBANG"
      },
      "isMine": true,
      "canEdit": true,
      "canDelete": true,
      "clientRequestId": "92d93d31-1d7f-4e97-81a1-e6b61788422f",
      "createdAt": "2026-07-25T14:23:00+09:00",
      "updatedAt": "2026-07-25T14:23:00+09:00"
    },
    "itemRecordCount": 1
  },
  "timestamp": "2026-07-25T14:23:00+09:00"
}
```

#### 200 OK — 동일 요청 재전송

```json
{
  "success": true,
  "code": "FIELD_RECORD_ALREADY_CREATED",
  "message": "이미 저장된 현장 기록입니다.",
  "data": {
    "studyId": 7,
    "sessionId": 100,
    "record": {
      "sourceId": 814,
      "checklistItemId": 501,
      "sourceType": "TEXT",
      "textContent": "단지 출입구에서 역 개찰구까지 약 8분이 걸렸습니다.",
      "photo": null,
      "sttStatus": null,
      "author": {
        "memberId": 42,
        "nickname": "루돌푸",
        "selectedCharacterId": "PALBANG"
      },
      "isMine": true,
      "canEdit": true,
      "canDelete": true,
      "clientRequestId": "47baaf21-f10f-468e-a279-4dc969c829dd",
      "createdAt": "2026-07-25T14:22:00+09:00",
      "updatedAt": "2026-07-25T14:22:00+09:00"
    },
    "itemRecordCount": 3
  },
  "timestamp": "2026-07-25T14:22:05+09:00"
}
```

---

### Response Field

| 필드 | 타입 | 설명 |
| --- | --- | --- |
| `studyId` | Long | 스터디 ID |
| `sessionId` | Long | 임장 세션 ID |
| `record` | Object | 저장된 현장 기록 |
| `itemRecordCount` | Integer | 해당 항목에 저장된 본인 기록 수 |

#### `record`

| 필드 | 타입 | 설명 |
| --- | --- | --- |
| `sourceId` | Long | 현장 기록 ID 및 리포트 근거 sourceId |
| `checklistItemId` | Long | 연결된 체크리스트 항목 ID |
| `sourceType` | String | `TEXT`, `PHOTO` |
| `textContent` | String | null | 텍스트 메모 |
| `photo` | Object | null | 사진 파일 정보 |
| `sttStatus` | String | null | 직접 저장 기록에서는 `null` |
| `author` | Object | 작성자 요약 |
| `isMine` | Boolean | 항상 `true` |
| `canEdit` | Boolean | 현재 수정 가능 여부 |
| `canDelete` | Boolean | 현재 삭제 가능 여부 |
| `clientRequestId` | String | 중복 방지 요청 ID |
| `createdAt` | String | 기록 생성 시각 |
| `updatedAt` | String | 기록 수정 시각 |

---

### Exception

#### 400 Bad Request — 유형별 필수값 누락

```json
{
  "success": false,
  "code": "FIELD_RECORD_PAYLOAD_INVALID",
  "message": "현장 기록 입력값을 확인해 주세요.",
  "data": {
    "field": "photoFileId",
    "reason": "PHOTO 기록에는 photoFileId가 필요합니다."
  },
  "timestamp": "2026-07-25T14:22:00+09:00"
}
```

---

#### 400 Bad Request — STT 직접 저장 시도

```json
{
  "success": false,
  "code": "FIELD_RECORD_SOURCE_TYPE_NOT_ALLOWED",
  "message": "음성 기록은 STT 변환 API를 이용해 주세요.",
  "data": {
    "sourceType": "STT"
  },
  "timestamp": "2026-07-25T14:22:00+09:00"
}
```

---

#### 400 Bad Request — clientRequestId 재사용 충돌

```json
{
  "success": false,
  "code": "FIELD_RECORD_IDEMPOTENCY_KEY_REUSED",
  "message": "동일한 요청 ID를 다른 기록에 사용할 수 없습니다.",
  "data": {
    "field": "clientRequestId"
  },
  "timestamp": "2026-07-25T14:22:00+09:00"
}
```

---

#### 401 Unauthorized

```json
{
  "success": false,
  "code": "AUTH_ACCESS_TOKEN_INVALID",
  "message": "로그인이 필요합니다.",
  "data": null,
  "timestamp": "2026-07-25T14:22:00+09:00"
}
```

---

#### 403 Forbidden — 체크리스트 항목 접근 불가

```json
{
  "success": false,
  "code": "CHECKLIST_ITEM_ACCESS_DENIED",
  "message": "본인의 체크리스트 항목에만 기록을 저장할 수 있습니다.",
  "data": null,
  "timestamp": "2026-07-25T14:22:00+09:00"
}
```

---

#### 403 Forbidden — 파일 접근 불가

```json
{
  "success": false,
  "code": "MEDIA_FILE_ACCESS_DENIED",
  "message": "사용할 수 없는 사진 파일입니다.",
  "data": {
    "fileId": 89
  },
  "timestamp": "2026-07-25T14:22:00+09:00"
}
```

---

#### 404 Not Found — 항목 없음

```json
{
  "success": false,
  "code": "CHECKLIST_ITEM_NOT_FOUND",
  "message": "체크리스트 항목을 찾을 수 없습니다.",
  "data": null,
  "timestamp": "2026-07-25T14:22:00+09:00"
}
```

---

#### 404 Not Found — 사진 파일 없음

```json
{
  "success": false,
  "code": "MEDIA_FILE_NOT_FOUND",
  "message": "사진 파일을 찾을 수 없습니다.",
  "data": {
    "fileId": 89
  },
  "timestamp": "2026-07-25T14:22:00+09:00"
}
```

---

#### 409 Conflict — 개인 임장 종료

```json
{
  "success": false,
  "code": "FIELD_PARTICIPANT_ALREADY_ENDED",
  "message": "임장을 종료한 뒤에는 현장 기록을 저장할 수 없습니다.",
  "data": null,
  "timestamp": "2026-07-25T14:22:00+09:00"
}
```

---

#### 409 Conflict — 세션 종료

```json
{
  "success": false,
  "code": "FIELD_VISIT_ALREADY_ENDED",
  "message": "종료된 임장 세션에는 기록을 저장할 수 없습니다.",
  "data": null,
  "timestamp": "2026-07-25T14:22:00+09:00"
}
```

---

#### 500 Internal Server Error

```json
{
  "success": false,
  "code": "COMMON_INTERNAL_SERVER_ERROR",
  "message": "일시적인 오류가 발생했습니다.",
  "data": null,
  "timestamp": "2026-07-25T14:22:00+09:00"
}
```

---

### 프론트 처리

```
체크리스트 항목에서 기록 버튼 선택
→ 사진·음성·텍스트 방식 선택 팝업 표시

TEXT 선택
→ 빈 문자열·길이 검증
→ clientRequestId 생성
→ 현장 기록 저장 API 호출

PHOTO 선택
→ 카메라 촬영
→ Presigned URL 발급 및 S3 업로드
→ 업로드 완료 처리
→ 받은 photoFileId로 현장 기록 저장 API 호출

음성 선택
→ 이 API를 호출하지 않음
→ STT_AUDIO 업로드 후 STT 변환 요청 API 호출

FIELD_RECORD_ALREADY_CREATED
→ 중복 오류를 표시하지 않음
→ 반환된 기존 sourceId로 로컬 대기 기록 동기화

저장 실패
→ 입력 텍스트 또는 업로드 완료 파일 ID를 로컬에 유지
→ 재시도 시 동일 clientRequestId 사용
```

---

## 현장 기록 목록 조회

Method: GET
Progress: 완료
URI: /api/v1/studies/{studyId}/field-visit/records
담당자: 윤다인
연동여부: Yes

임장 세션의 텍스트·사진·STT 현장 기록을 조회한다.

`mineOnly=true`(기본)는 본인 기록만, `mineOnly=false`는 같은 세션의 삭제되지 않은 전체 참여자 기록을 반환한다.
각 기록에 `isMine`, `canEdit`, `canDelete`를 포함한다. 종료·리포트 잠금 상태에서는 조회는 가능하고 `canEdit`/`canDelete`는 false다.

---

### Request

- Request HTTP Method: `GET`
- 인증 필요: 필요
- Request Body: 없음

#### Request Header

```
Authorization: Bearer {accessToken}
```

#### Path Variable · Query Parameter

첫 요청:

```
GET /api/v1/studies/7/field-visit/records?checklistItemId=501&sourceType=PHOTO&size=20
```

다음 페이지:

```
GET /api/v1/studies/7/field-visit/records?checklistItemId=501&sourceType=PHOTO&cursor=820&size=20
```

| 필드 | 타입 | 필수 | 기본값 | 설명 |
| --- | --- | --- | --- | --- |
| `studyId` | Long | Y | - | 스터디 ID, Path Variable |
| `checklistItemId` | Long | N | 전체 항목 | 특정 체크리스트 항목의 기록만 조회 |
| `sourceType` | String | N | 전체 | `TEXT`, `PHOTO`, `STT` |
| `mineOnly` | Boolean | N | `true` | 본인 기록만 조회할지 여부 |
| `cursor` | Long | N | 없음 | 다음 페이지 조회 기준이 되는 마지막 `sourceId` |
| `size` | Integer | N | `20` | 조회 개수, 1~50 |

#### Source Type

| 값 | 설명 |
| --- | --- |
| `TEXT` | 사용자가 직접 입력한 텍스트 메모 |
| `PHOTO` | 현장 사진 기록 |
| `STT` | 음성에서 변환된 텍스트 기록 |

---

### 처리 기준

#### 1. 회원·스터디 접근 권한

- 로그인한 회원 ID는 Access Token에서 확인한다.
- 요청자는 해당 스터디의 승인 멤버여야 한다.
- 임장 세션에 참여하지 않은 승인 멤버라도 완료된 스터디의 기록을 볼 수 있는지는 서비스 정책에 따라 달라질 수 있으나, 최종 요구사항의 참여자 전용 원문 원칙에 따라 현재 세션 참여자 또는 스터디장만 조회할 수 있도록 제한한다.
- 신청 대기자, 신청 거절자, 비멤버는 현장 기록을 조회할 수 없다.

#### 2. 조회 범위

- 기본값 `mineOnly = true`에서는 `field_record.author_id = 로그인 회원 ID`인 기록만 반환한다.
- `mineOnly = false`는 스터디원별 기록을 함께 보여주는 화면이 실제로 필요한 경우에만 허용한다.
- 일반 참여자가 `mineOnly = false`로 조회할 수 있는 범위는 같은 임장 세션의 승인 참여자 기록으로 제한한다.
- 완료 리포트 화면에서는 이 API를 호출하지 않는다. 완료 리포트는 안전하게 요약된 집계만 제공한다.
- 삭제된 기록(`deletedAt != null`)은 목록에 포함하지 않는다.

#### 3. 체크리스트 항목 필터

- `checklistItemId`가 전달되면 해당 항목에 연결된 기록만 반환한다.
- 전달된 항목은 현재 스터디의 임장 세션에 생성된 체크리스트 항목이어야 한다.
- 본인 체크리스트 항목이 아닌 항목을 `mineOnly = true`로 요청하면 접근을 거절한다.
- 다른 세션 또는 다른 스터디의 항목 ID는 조회할 수 없다.

#### 4. 기록 유형 필터

- `sourceType`이 없으면 `TEXT`, `PHOTO`, `STT` 전체를 반환한다.
- 유효하지 않은 유형은 `400 Bad Request`로 처리한다.
- `STT` 기록은 `sttStatus`와 변환 텍스트를 함께 반환한다.
- `PENDING`, `PROCESSING`, `FAILED` STT 기록도 본인이 진행 상태를 확인할 수 있도록 반환한다.

#### 5. 사진 접근 URL

- `PHOTO` 기록의 파일은 `file_meta.file_usage = FIELD_PHOTO`, `upload_status = COMPLETED`, `deletedAt = null`이어야 한다.
- 파일이 삭제되었거나 접근할 수 없으면 기록 자체는 유지하되 `photo.available = false`, `fileUrl = null`로 반환할 수 있다.
- 얼굴·차량번호·세대 내부 등 민감 정보 촬영에 대한 주의 문구는 프론트 입력 화면에서 제공한다.

#### 6. STT 음성 원본

- STT 기록에는 변환 텍스트만 반환한다.
- 성공 처리 후 삭제된 음성 원본 URL이나 S3 Key는 반환하지 않는다.
- 실패 후 재처리 가능 기간 중에도 일반 현장 기록 목록에서 음성 원본 접근 URL을 제공하지 않는다.

#### 7. 정렬·커서 페이지네이션

- 기록은 `sourceId DESC` 기준 최근 작성순으로 반환한다.
- `cursor`가 있으면 `sourceId < cursor`인 기록을 조회한다.
- `size + 1`건을 조회해 다음 페이지 존재 여부를 판단하고 실제 응답은 최대 `size`건만 반환한다.
- 다음 기록이 있으면 현재 응답의 마지막 `sourceId`를 `nextCursor`로 반환한다.
- 기록이 없으면 오류가 아닌 빈 배열을 반환한다.

#### 8. 권한 표시

- 각 기록에 `isMine`, `canEdit`, `canDelete`를 반환한다.
- `canEdit`과 `canDelete`는 서로 다른 값일 수 있다. 하나의 `canMutate`로 합치지 않는다.
- 타인 기록은 `isMine=false`, `canEdit=false`, `canDelete=false`다.

| 조건 | canEdit | canDelete |
| --- | --- | --- |
| TEXT/PHOTO, 본인, 쓰기 가능 | true | true |
| STT DONE, 본인, 쓰기 가능 | true | true |
| STT PENDING | false | false |
| STT PROCESSING | false | false |
| STT FAILED | false | true |
| 타인 기록 | false | false |
| 세션 종료 | false | false |
| 참여자 종료 | false | false |
| report lock | false | false |
| 삭제 기록 | false | false |

쓰기 가능 = `field_session.status=IN_PROGRESS` AND 본인 `field_participant.status=IN_PROGRESS` AND study의 `report` 행 없음 AND 기록 `deleted_at IS NULL`.

---

### Response

#### 200 OK

```json
{
  "success": true,
  "code": "FIELD_RECORD_LIST_SUCCESS",
  "message": "현장 기록 목록 조회에 성공했습니다.",
  "data": {
    "studyId": 7,
    "sessionId": 100,
    "readOnly": false,
    "checklistItem": {
      "checklistItemId": 501,
      "category": "교통",
      "title": "지하철역 접근성",
      "subtitle": "단지 출입구에서 승강장까지 실제 소요 시간을 확인해 주세요."
    },
    "content": [
      {
        "sourceId": 820,
        "checklistItemId": 501,
        "sourceType": "PHOTO",
        "textContent": null,
        "photo": {
          "fileId": 89,
          "originalName": "station-road.jpg",
          "contentType": "image/jpeg",
          "fileUrl": "<https://s3.example.com/presigned/field-photo-89>",
          "available": true,
          "expiresAt": "2026-07-25T14:40:00+09:00"
        },
        "sttStatus": null,
        "author": {
          "memberId": 42,
          "nickname": "루돌푸",
          "selectedCharacterId": "PALBANG"
        },
        "isMine": true,
        "canEdit": true,
        "canDelete": true,
        "createdAt": "2026-07-25T14:20:00+09:00",
        "updatedAt": "2026-07-25T14:20:00+09:00"
      },
      {
        "sourceId": 812,
        "checklistItemId": 501,
        "sourceType": "TEXT",
        "textContent": "단지 출입구에서 역 개찰구까지 약 8분이 걸렸습니다.",
        "photo": null,
        "sttStatus": null,
        "author": {
          "memberId": 42,
          "nickname": "루돌푸",
          "selectedCharacterId": "PALBANG"
        },
        "isMine": true,
        "canEdit": true,
        "canDelete": true,
        "createdAt": "2026-07-25T14:15:00+09:00",
        "updatedAt": "2026-07-25T14:15:00+09:00"
      },
      {
        "sourceId": 807,
        "checklistItemId": 501,
        "sourceType": "STT",
        "textContent": "역에서 단지까지 오는 길에 짧은 오르막이 있습니다.",
        "photo": null,
        "sttStatus": "DONE",
        "author": {
          "memberId": 42,
          "nickname": "루돌푸",
          "selectedCharacterId": "PALBANG"
        },
        "isMine": true,
        "canEdit": true,
        "canDelete": true,
        "createdAt": "2026-07-25T14:10:00+09:00",
        "updatedAt": "2026-07-25T14:10:00+09:00"
      }
    ],
    "nextCursor": 807,
    "hasNext": true
  },
  "timestamp": "2026-07-25T14:30:00+09:00"
}
```

#### 200 OK — 기록 없음

```json
{
  "success": true,
  "code": "FIELD_RECORD_LIST_SUCCESS",
  "message": "현장 기록 목록 조회에 성공했습니다.",
  "data": {
    "studyId": 7,
    "sessionId": 100,
    "readOnly": false,
    "checklistItem": null,
    "content": [],
    "nextCursor": null,
    "hasNext": false
  },
  "timestamp": "2026-07-25T14:30:00+09:00"
}
```

---

### Response Field

| 필드 | 타입 | 설명 |
| --- | --- | --- |
| `studyId` | Long | 스터디 ID |
| `sessionId` | Long | 임장 세션 ID |
| `readOnly` | Boolean | 종료·리포트 잠금 등으로 쓰기 불가인지 여부 |
| `checklistItem` | Object | null | 특정 항목 필터를 사용한 경우 항목 요약 |
| `content` | Array | 현장 기록 목록 |
| `nextCursor` | Long | null | 다음 페이지 조회 커서 |
| `hasNext` | Boolean | 다음 페이지 존재 여부 |

#### `content[]`

| 필드 | 타입 | 설명 |
| --- | --- | --- |
| `sourceId` | Long | 현장 기록 ID. 리포트 근거에서 사용하는 sourceId |
| `checklistItemId` | Long | 연결된 체크리스트 항목 ID |
| `sourceType` | String | `TEXT`, `PHOTO`, `STT` |
| `textContent` | String | null | 텍스트 메모 또는 STT 변환문 |
| `photo` | Object | null | 사진 기록 정보 |
| `sttStatus` | String | null | STT 상태. `PENDING`, `PROCESSING`, `DONE`, `FAILED` |
| `author` | Object | 작성자 공개 요약 |
| `isMine` | Boolean | 로그인 회원이 작성자인지 여부 |
| `canEdit` | Boolean | 현재 수정 가능 여부 |
| `canDelete` | Boolean | 현재 삭제 가능 여부 |
| `createdAt` | String | 기록 생성 시각 |
| `updatedAt` | String | 기록 수정 시각 |

#### `photo`

| 필드 | 타입 | 설명 |
| --- | --- | --- |
| `fileId` | Long | 파일 메타데이터 ID |
| `originalName` | String | null | 원본 파일명 |
| `contentType` | String | null | MIME 타입 |
| `fileUrl` | String | null | 조회용 임시 접근 URL |
| `available` | Boolean | 현재 파일 접근 가능 여부 |
| `expiresAt` | String | null | 접근 URL 만료 시각 |

---

### Exception

#### 400 Bad Request — 잘못된 sourceType

```json
{
  "success": false,
  "code": "FIELD_RECORD_SOURCE_TYPE_INVALID",
  "message": "유효하지 않은 현장 기록 유형입니다.",
  "data": {
    "field": "sourceType",
    "allowedValues": ["TEXT", "PHOTO", "STT"]
  },
  "timestamp": "2026-07-25T14:30:00+09:00"
}
```

---

#### 400 Bad Request — 잘못된 조회 개수

```json
{
  "success": false,
  "code": "COMMON_INVALID_REQUEST",
  "message": "입력값을 확인해 주세요.",
  "data": {
    "field": "size",
    "reason": "조회 개수는 1 이상 50 이하이어야 합니다."
  },
  "timestamp": "2026-07-25T14:30:00+09:00"
}
```

---

#### 401 Unauthorized

```json
{
  "success": false,
  "code": "AUTH_ACCESS_TOKEN_INVALID",
  "message": "로그인이 필요합니다.",
  "data": null,
  "timestamp": "2026-07-25T14:30:00+09:00"
}
```

---

#### 403 Forbidden — 조회 권한 없음

```json
{
  "success": false,
  "code": "FIELD_RECORD_ACCESS_DENIED",
  "message": "해당 임장 기록을 조회할 권한이 없습니다.",
  "data": null,
  "timestamp": "2026-07-25T14:30:00+09:00"
}
```

---

#### 404 Not Found — 체크리스트 항목 없음

```json
{
  "success": false,
  "code": "CHECKLIST_ITEM_NOT_FOUND",
  "message": "체크리스트 항목을 찾을 수 없습니다.",
  "data": null,
  "timestamp": "2026-07-25T14:30:00+09:00"
}
```

---

#### 404 Not Found — 스터디 없음

```json
{
  "success": false,
  "code": "STUDY_NOT_FOUND",
  "message": "스터디를 찾을 수 없습니다.",
  "data": null,
  "timestamp": "2026-07-25T14:30:00+09:00"
}
```

---

#### 500 Internal Server Error

```json
{
  "success": false,
  "code": "COMMON_INTERNAL_SERVER_ERROR",
  "message": "일시적인 오류가 발생했습니다.",
  "data": null,
  "timestamp": "2026-07-25T14:30:00+09:00"
}
```

---

### 프론트 처리

```
체크리스트 항목의 기록 버튼 선택
→ checklistItemId를 포함해 목록 API 호출
→ 텍스트·사진·STT 기록을 작성 시각과 함께 표시

사진 기록 선택
→ photo.available = true이면 fileUrl로 미리보기
→ URL 만료 시 목록 또는 파일 메타 API 재호출

sttStatus = PENDING 또는 PROCESSING
→ 변환 중 상태 표시
→ 해당 sttId 상태 조회 흐름 유지

sttStatus = FAILED
→ 실패 상태와 재시도 버튼 표시

canDelete = true
→ 본인 기록에 삭제 메뉴 표시

목록 하단 도달
→ hasNext = true이면 nextCursor로 다음 기록 조회
→ 기존 목록 뒤에 추가
```

---

## 아파트별 완료 리포트 목록 조회

Method: GET
Progress: 완료
URI: /api/v1/apartments/{apartmentId}/reports
담당자: 최태선
연동여부: Yes

선택한 아파트를 대상으로 생성 완료된 AI 임장 리포트 목록을 조회한다.

완료된 리포트의 요약 내용은 로그인한 회원에게 공개하며, 사진·메모·체크리스트·음성 변환문 등 리포트 생성에 사용된 원본 임장 기록은 해당 임장 참여자만 조회할 수 있다.

### Request

- HTTP Method: `GET`
- URI: `/api/v1/apartments/{apartmentId}/reports`
- 인증 필요: 필요

#### Query Parameter

```
GET /api/v1/apartments/15/reports?page=0&size=20
```

#### Request Header

```
Authorization: Bearer {accessToken}
```

#### Path Variable

```
GET /api/v1/apartments/25/reports
```

| 필드 | 타입 | 필수 | 설명 |
| --- | --- | --- | --- |
| `apartmentId` | Long | Y | 완료 리포트를 조회할 아파트 ID, 1 이상의 값 |

### 처리 기준

- 리포트 카드에는 분석 태그·제목·작성일·AI 생성 여부·요약과 찜 여부를 제공한다.
- 기존 근거 유형별 개수 중심 카드 구조는 사용하지 않는다.
- 동일 `reportId`는 한 번만 반환한다.

### 추가 상세 처리 기준

- 로그인한 회원 ID는 Access Token에서 확인한다.
- 회원 상태가 `ACTIVE`인 경우에만 조회할 수 있다.
- 리포트 목록은 로그인한 회원 모두 조회할 수 있다.
- 로그인한 회원이 해당 임장에 참여하지 않았더라도 완료된 리포트 요약과 본문은 조회할 수 있다.

---

- 존재하지 않는 아파트는 `404 Not Found`를 반환한다.
- 서비스 대상에서 제외되거나 삭제된 아파트도 동일한 응답을 반환한다.

---

- 생성 중인 리포트는 공개 목록에 노출하지 않는다.
- 생성 실패한 리포트는 공개 목록에 노출하지 않는다.
- 하나의 스터디에는 하나의 최종 AI 리포트만 존재하는 것을 원칙으로 한다.
- 재생성된 리포트가 존재하는 경우 현재 유효한 최종 리포트만 반환한다.
- 리포트가 하나도 없으면 오류가 아닌 빈 배열을 반환한다.

---

- 리포트 목록의 `isParticipant`로 로그인 회원의 참여 여부를 반환한다.
- 원본 근거 접근 가능 여부를 `canViewEvidence`로 반환한다.
- 참여자라면 `canViewEvidence = true`다.
- 비참여자라면 `canViewEvidence = false`다.
- 리포트의 작성권·구매권·포인트 결제 기능은 현재 범위에 포함하지 않는다.

---

- 스터디 신청만 했거나 승인만 받고 실제 임장에 참여하지 않은 회원은 참여자로 판단하지 않는다.
- 임장 시작 전에 탈퇴하거나 강퇴된 회원은 참여자로 판단하지 않는다.
- 리포트 생성 당시 참여자로 확정된 회원은 스터디 완료 후에도 근거 원문을 조회할 수 있다.
- 과거 참여 이력은 현재 스터디 상태와 분리하여 보존한다.

---

- 리포트 전체 본문은 목록에 포함하지 않는다.
- 리포트 상세 내용은 다음 API로 조회한다.

```
GET /api/v1/reports/{reportId}
```

- 근거 목록은 다음 API로 조회한다.

```
GET /api/v1/reports/{reportId}/evidences
```

- 근거 원문은 참여자 권한을 확인한 후 다음 API로 조회한다.

```
GET /api/v1/reports/{reportId}/evidences/{sourceId}
```

---

- 목록에 표시할 리포트 대표 요약을 `summary`로 반환한다.
- 대표 요약은 리포트 전체 내용을 짧게 정리한 문장이다.
- 지나치게 긴 리포트 본문을 그대로 반환하지 않는다.
- 대표 요약이 없는 비정상 완료 리포트는 데이터 상태를 점검해야 한다.
- 프론트는 화면 너비에 따라 말줄임 처리할 수 있다.

---

- `evidenceCount`는 리포트에 연결된 전체 근거 수다.
- 삭제되거나 리포트에서 제외된 근거는 개수에 포함하지 않는다.
- 비참여자에게도 근거 개수는 공개할 수 있다.
- 비참여자에게는 근거 원문 내용과 작성자별 기록을 반환하지 않는다.
- 근거 유형별 개수를 목록 응답에 포함할 수 있다.
- `participantCount`는 리포트 생성 대상에 포함된 실제 임장 참여자 수다.
- 단순히 스터디에 승인된 전체 인원 수가 아니다.
- 임장 시작 전에 탈퇴하거나 강퇴된 회원은 제외한다.
- 임장 종료 시 스터디장이 제외한 미종료 회원은 리포트 생성 대상 정책에 따라 제외될 수 있다.
- 참여자 닉네임 목록은 완료 리포트 목록에서 반환하지 않는다.

---

- 임장 날짜가 아니라 리포트 생성 완료 시각을 기본 정렬 기준으로 사용한다.
- 같은 완료 시각이면 리포트 ID가 큰 리포트를 먼저 반환한다.

---

- `page`는 0부터 시작한다.
- `size`는 1 이상 100 이하로 제한한다.
- 다음 페이지 조회 시 같은 `size`를 유지한다.
- 결과가 없으면 빈 배열과 페이지 정보를 반환한다.

---

- 완료 리포트 존재 여부는 AI 챗봇의 우선 검색 경로를 안내하는 데 사용한다.
- 완료 리포트가 있어도 현재 질문과 관련된 근거가 부족하면 챗봇은 웹 검색으로 전환할 수 있다.
- 최종 근거 유형은 질문 처리 시 사용한 실제 출처에 따라 `REPORT`, `WEB`, `NONE` 중 하나로 결정한다.
- `evidenceCount`는 실제 유효한 리포트 근거 수와 일치해야 한다.
- `isParticipant`와 `canViewEvidence`는 같은 참여자 판정 기준을 사용한다.
- 리포트별 참여 여부와 근거 개수를 반복 조회하지 않도록 배치 또는 집계 쿼리를 사용한다.

---

### Response

#### 200 OK

```json
{
  "success": true,
  "code": "APARTMENT_REPORT_LIST_SUCCESS",
  "message": "아파트 완료 리포트 목록 조회에 성공했습니다.",
  "data": {
    "content": [
      {
        "reportId": 48,
        "title": "래미안 옥수 리버젠 임장 리포트",
        "analysisTags": [
          "교통 우수",
          "단지 경사"
        ],
        "summary": "역 접근성이 좋고 일부 진입로 경사를 확인했습니다.",
        "completedAt": "2026-07-22T18:07:00+09:00",
        "isAiGenerated": true,
        "favoritedByMe": false
      }
    ],
    "totalElements": 5,
    "page": 0,
    "size": 20,
    "totalPages": 1
  },
  "timestamp": "2026-07-24T17:35:00+09:00"
}
```

### Exception

- `401 AUTH_ACCESS_TOKEN_INVALID`
- `404 APARTMENT_NOT_FOUND`
- `500 COMMON_INTERNAL_SERVER_ERROR`

### 추가 Exception 예시

#### 400 Bad Request — 잘못된 아파트 ID

```json
{
  "success": false,
  "code": "APARTMENT_ID_INVALID",
  "message": "아파트 ID가 올바르지 않습니다.",
  "data": {
    "field": "apartmentId",
    "reason": "아파트 ID는 1 이상의 숫자여야 합니다."
  },
  "timestamp": "2026-07-22T10:30:00+09:00"
}
```

---

#### 400 Bad Request — 잘못된 페이지 번호

```json
{
  "success": false,
  "code": "COMMON_INVALID_REQUEST",
  "message": "입력값을 확인해 주세요.",
  "data": {
    "field": "page",
    "reason": "페이지 번호는 0 이상이어야 합니다."
  },
  "timestamp": "2026-07-22T10:30:00+09:00"
}
```

---

#### 400 Bad Request — 잘못된 페이지 크기

```json
{
  "success": false,
  "code": "COMMON_INVALID_REQUEST",
  "message": "입력값을 확인해 주세요.",
  "data": {
    "field": "size",
    "reason": "페이지 크기는 1 이상 100 이하이어야 합니다."
  },
  "timestamp": "2026-07-22T10:30:00+09:00"
}
```

---

#### 401 Unauthorized

```json
{
  "success": false,
  "code": "AUTH_ACCESS_TOKEN_INVALID",
  "message": "로그인이 필요합니다.",
  "data": null,
  "timestamp": "2026-07-22T10:30:00+09:00"
}
```

---

#### 404 Not Found — 회원 없음

```json
{
  "success": false,
  "code": "MEMBER_NOT_FOUND",
  "message": "회원 정보를 찾을 수 없습니다.",
  "data": null,
  "timestamp": "2026-07-22T10:30:00+09:00"
}
```

---

#### 404 Not Found — 아파트 없음

```json
{
  "success": false,
  "code": "APARTMENT_NOT_FOUND",
  "message": "아파트 정보를 찾을 수 없습니다.",
  "data": {
    "apartmentId": 25
  },
  "timestamp": "2026-07-22T10:30:00+09:00"
}
```

---

#### 500 Internal Server Error

```json
{
  "success": false,
  "code": "COMMON_INTERNAL_SERVER_ERROR",
  "message": "일시적인 오류가 발생했습니다.",
  "data": null,
  "timestamp": "2026-07-22T10:30:00+09:00"
}
```

---

### 추가 프론트 처리 기준

```
아파트 상세에서 완료 리포트 영역 선택
→ GET /api/v1/apartments/{apartmentId}/reports?page=0&size=20 호출
→ 최근 완료된 리포트부터 표시

리포트 카드 표시
→ 스터디명
→ 임장 날짜
→ 대표 요약
→ 참여자 수
→ 근거 수
→ 완료 날짜 표시

리포트 카드 선택
→ GET /api/v1/reports/{reportId} 호출
→ 완료 리포트 상세 화면으로 이동

canViewEvidence = true
→ 상세 화면에서 근거 보기 버튼 활성화

canViewEvidence = false
→ 상세 화면에서 근거 원문 보기 버튼 비활성화
→ "원본 임장 기록은 해당 임장 참여자만 확인할 수 있습니다." 안내

hasNext = true
→ 목록 하단 도달 시 page 값을 1 증가
→ 다음 리포트 목록 조회
→ 기존 content 뒤에 추가

content가 빈 배열
→ "아직 등록된 임장 리포트가 없습니다." 표시
→ 모집 중인 스터디 보기 또는 스터디 생성 버튼 제공
```

---

## 아파트 키워드·현재 위치 주변 검색

Domain: Apartment
Method: GET
Progress: 완료
URI: /api/v1/apartments
담당자: 최태선
연동여부: Yes

아파트명·동명·도로명 주소 키워드로 검색하거나 현재 위치 반경 내 아파트를 거리순으로 조회한다.

선택한 서울 자치구·법정동 또는 아파트 검색어를 기준으로 아파트 목록을 조회한다.

지역 선택 후 아파트 목록을 표시하거나, 특정 단지명을 검색하는 화면에서 사용한다.

### Request

- HTTP Method: `GET`
- URI: `/api/v1/apartments`
- 인증 필요: 필요
- Request Body: 없음

#### 키워드 검색

```
GET /api/v1/apartments?keyword=옥수&page=0&size=20
```

#### 현재 위치 주변 검색

```
GET /api/v1/apartments?latitude=37.5412&longitude=127.0178&radiusMeters=3000&page=0&size=20
```

| 필드 | 타입 | 필수 | 기본값 | 설명 |
| --- | --- | --- | --- | --- |
| `keyword` | String | N | 없음 | 아파트명·동명·주소 검색어, 최대 100자 |
| `districtCode` | String | N | 없음 | 구 필터 |
| `dongCode` | String | N | 없음 | 동 필터 |
| `latitude` | Double | 조건부 | 없음 | 현재 위치 위도 |
| `longitude` | Double | 조건부 | 없음 | 현재 위치 경도 |
| `radiusMeters` | Integer | N | `3000` | 주변 검색 반경, 100~10000m |
| `page` | Integer | N | `0` | 페이지 번호 |
| `size` | Integer | N | `20` | 페이지 크기, 1~100 |

#### Request Header

```
Authorization: Bearer {accessToken}
```

### 처리 기준

- 다음 중 하나 이상의 조회 조건이 있어야 한다: `keyword`, `districtCode` 또는 `dongCode`, `latitude + longitude`.
- `keyword`는 앞뒤 공백을 제거하고 `apartment.name`, `dong_name`, `address`를 대상으로 검색한다.
- 위도와 경도는 함께 전달해야 한다.
- 위치가 전달되면 반경 안의 아파트만 조회하고 거리 오름차순으로 정렬한다.
- 위치 권한이 없으면 키워드·지역 필터 검색만 사용한다.
- 동일 아파트는 `apartmentId` 기준으로 한 번만 반환한다.
- 최근 실거래는 `isCanceled=false`인 가장 최신 거래를 사용한다.
- 최근 거래가 없으면 `latestTransaction=null`, `latestTransactionAvailable=false`로 반환한다.
- 거래 가격 `price`는 기존 API와 동일하게 만 원 단위로 반환한다.

### 추가 상세 처리 기준

- 로그인한 회원 ID는 Access Token에서 확인한다.
- 회원 상태가 `ACTIVE`인 경우에만 조회할 수 있다.
- 로그인한 회원을 기준으로 각 아파트의 찜 여부를 계산한다.

---

- `districtCode`는 공백 제거 후 검증한다.
- 5자리 숫자 문자열이어야 한다.
- 서울특별시에 속한 현재 사용 중인 자치구 코드여야 한다.
- 서울 외 지역 코드는 허용하지 않는다.
- `dongCode`는 공백 제거 후 검증한다.
- 10자리 숫자 문자열이어야 한다.
- 서울특별시에 속한 현재 사용 중인 법정동 코드여야 한다.
- 폐지되거나 비활성화된 법정동은 사용할 수 없다.
- `dongCode`가 실제로 `districtCode`에 속하는지 확인한다.
- 서로 다른 지역 조합이면 요청을 거절한다.

```
districtCode = 11710
dongCode = 1171010100
→ 송파구 잠실동
→ 정상 요청

districtCode = 11440
dongCode = 1171010100
→ 마포구 코드와 송파구 잠실동 코드가 불일치
→ 요청 거절
```

---

- `keyword`의 앞뒤 공백을 제거한다.
- 연속된 공백은 하나로 정규화한다.
- 특수문자만 입력한 경우 유효한 검색어로 처리하지 않는다.
- 영문 검색은 대소문자를 구분하지 않는다.
- 한글 초성 검색은 MVP 필수 범위에 포함하지 않는다.
- 서비스 범위인 서울 소재 아파트만 반환한다.
- 공동주택 유형 중 서비스 대상이 `아파트`인 데이터만 반환한다.
- 원룸·투룸·오피스텔 등 현재 MVP 범위 밖의 주거 유형은 제외한다.
- 비활성화되거나 서비스 대상에서 제외된 아파트는 반환하지 않는다.
- 동일한 공공데이터 단지 코드에 해당하는 아파트가 중복 반환되지 않도록 한다.
- 목록 결과가 없으면 오류가 아닌 빈 배열을 반환한다.

---

- 각 아파트의 가장 최근 정상 실거래 한 건을 `latestTransaction`으로 반환한다.
- 거래 해제 또는 취소 상태인 거래는 제외한다.
- 거래 가격 단위는 만 원으로 통일한다.
- 거래일이 동일한 정상 거래가 여러 개이면 다음 기준으로 한 건을 선택한다.

```
1. dealDate DESC
2. collectedAt DESC
3. id DESC
```

- 정상 실거래가 없으면 `latestTransaction = null`을 반환한다.
- 실거래 정보가 없는 아파트도 아파트 목록에는 포함한다.
- 목록 API에서는 최근 거래 한 건만 반환한다.
- 전체 실거래 목록은 다음 API에서 조회한다.

```
GET /api/v1/apartments/{apartmentId}/transactions
```

---

- 찜 관계가 없으면 `false`를 반환한다.
- 찜 관계는 다음 제약조건을 가진다.

```
UNIQUE(member_id, apartment_id)
```

- 목록 조회 중 찜 관계를 새로 생성하거나 삭제하지 않는다.

---

- `page`는 0부터 시작한다.
- `size`는 1 이상 100 이하로 제한한다.
- 조회 조건이나 정렬 조건이 변경되면 `page = 0`부터 다시 조회한다.
- 아파트 목록이 비어 있으면 빈 배열과 페이지 정보를 반환한다.

---

- `district_code`, `legal_dong_code`, `dong_name`, `name`, `address` 컬럼에 조회용 인덱스를 적용한다.
- 단지명 부분 검색이 느린 경우 PostgreSQL `pg_trgm` 인덱스를 사용할 수 있다.
- 최근 실거래, 스터디 수, 리포트 수, 찜 여부를 아파트마다 개별 조회하지 않는다.
- 집계 쿼리 또는 배치 조회를 사용하여 N+1 쿼리를 방지한다.
- 목록 응답에는 전체 실거래·전체 스터디·전체 리포트 내용을 포함하지 않는다.

---

### Response

#### 200 OK — 주변 검색

```
{
  "success": true,
  "code": "APARTMENT_LIST_SUCCESS",
  "message": "아파트 목록 조회에 성공했습니다.",
  "data": {
    "searchMode": "NEARBY",
    "currentLocation": {
      "latitude": 37.5412,
      "longitude": 127.0178,
      "radiusMeters": 3000,
      "locationName": "서울특별시 성동구 옥수동"
    },
    "content": [
      {
        "apartmentId": 15,
        "name": "래미안 옥수 리버젠",
        "address": "서울특별시 성동구 매봉길 15",
        "districtName": "성동구",
        "dongName": "옥수동",
        "latitude": 37.5405,
        "longitude": 127.0186,
        "latestTransaction": {
          "price": 183000,
          "priceUnit": "TEN_THOUSAND_KRW",
          "exclusiveArea": 84.95,
          "dealDate": "2026-06-15"
        },
        "latestTransactionAvailable": true,
        "distanceMeters": 620,
        "favoritedByMe": false
      }
    ],
    "totalElements": 8,
    "page": 0,
    "size": 20,
    "totalPages": 1
  },
  "timestamp": "2026-07-24T17:10:00+09:00"
}
```

#### 200 OK — 최근 거래 없음

```
{
  "apartmentId": 21,
  "name": "새 아파트",
  "address": "서울특별시 성동구 ...",
  "districtName": "성동구",
  "dongName": "옥수동",
  "latitude": 37.5398,
  "longitude": 127.0201,
  "latestTransaction": null,
  "latestTransactionAvailable": false,
  "distanceMeters": 910,
  "favoritedByMe": false
}
```

### Exception

- `400 APARTMENT_LOCATION_INVALID`: 좌표 누락·범위 오류
- `400 APARTMENT_RADIUS_INVALID`
- `400 COMMON_INVALID_REQUEST`: 검색어·페이지 크기 오류
- `401 AUTH_ACCESS_TOKEN_INVALID`
- `500 COMMON_INTERNAL_SERVER_ERROR`

### 추가 Exception 예시

#### 400 Bad Request — 조회 조건 없음

```
{
  "success": false,
  "code": "APARTMENT_FILTER_REQUIRED",
  "message": "지역 또는 검색어를 입력해 주세요.",
  "data": {
    "reason": "keyword, 지역 코드 또는 현재 위치 중 하나 이상의 조회 조건이 필요합니다."
  },
  "timestamp": "2026-07-22T10:30:00+09:00"
}
```

---

#### 400 Bad Request — 잘못된 자치구 코드

```
{
  "success": false,
  "code": "REGION_DISTRICT_CODE_INVALID",
  "message": "자치구 코드가 올바르지 않습니다.",
  "data": {
    "field": "districtCode",
    "reason": "자치구 코드는 5자리 숫자여야 합니다."
  },
  "timestamp": "2026-07-22T10:30:00+09:00"
}
```

---

#### 400 Bad Request — 잘못된 법정동 코드

```
{
  "success": false,
  "code": "REGION_DONG_CODE_INVALID",
  "message": "법정동 코드가 올바르지 않습니다.",
  "data": {
    "field": "dongCode",
    "reason": "법정동 코드는 10자리 숫자여야 합니다."
  },
  "timestamp": "2026-07-22T10:30:00+09:00"
}
```

---

#### 400 Bad Request — 지역 코드 불일치

```
{
  "success": false,
  "code": "REGION_CODE_MISMATCH",
  "message": "자치구와 법정동 정보가 일치하지 않습니다.",
  "data": {
    "districtCode": "11440",
    "dongCode": "1171010100"
  },
  "timestamp": "2026-07-22T10:30:00+09:00"
}
```

---

#### 400 Bad Request — 잘못된 페이지 번호

```
{
  "success": false,
  "code": "COMMON_INVALID_REQUEST",
  "message": "입력값을 확인해 주세요.",
  "data": {
    "field": "page",
    "reason": "페이지 번호는 0 이상이어야 합니다."
  },
  "timestamp": "2026-07-22T10:30:00+09:00"
}
```

---

#### 400 Bad Request — 잘못된 페이지 크기

```
{
  "success": false,
  "code": "COMMON_INVALID_REQUEST",
  "message": "입력값을 확인해 주세요.",
  "data": {
    "field": "size",
    "reason": "페이지 크기는 1 이상 100 이하이어야 합니다."
  },
  "timestamp": "2026-07-22T10:30:00+09:00"
}
```

---

#### 401 Unauthorized

```
{
  "success": false,
  "code": "AUTH_ACCESS_TOKEN_INVALID",
  "message": "로그인이 필요합니다.",
  "data": null,
  "timestamp": "2026-07-22T10:30:00+09:00"
}
```

---

#### 404 Not Found — 회원 없음

```
{
  "success": false,
  "code": "MEMBER_NOT_FOUND",
  "message": "회원 정보를 찾을 수 없습니다.",
  "data": null,
  "timestamp": "2026-07-22T10:30:00+09:00"
}
```

---

#### 404 Not Found — 지역 정보 없음

```
{
  "success": false,
  "code": "REGION_NOT_FOUND",
  "message": "지역 정보를 찾을 수 없습니다.",
  "data": null,
  "timestamp": "2026-07-22T10:30:00+09:00"
}
```

---

#### 500 Internal Server Error

```
{
  "success": false,
  "code": "COMMON_INTERNAL_SERVER_ERROR",
  "message": "일시적인 오류가 발생했습니다.",
  "data": null,
  "timestamp": "2026-07-22T10:30:00+09:00"
}
```

---

### 프론트 처리

```
검색어 입력
→ 디바운스 후 keyword로 조회
→ 결과 선택 시 apartmentId 상세 이동

위치 권한 허용
→ 현재 좌표·반경으로 주변 목록 조회
→ distanceMeters 오름차순 표시
```

---

### 추가 프론트 처리 기준

```
자치구 선택
→ GET /api/v1/apartments?districtCode={districtCode}&page=0&size=20 호출
→ 해당 자치구 아파트 목록 표시

법정동 선택
→ GET /api/v1/apartments?dongCode={dongCode}&page=0&size=20 호출
→ 해당 동의 아파트 목록 표시

아파트명 검색
→ keyword 앞뒤 공백 제거
→ 300~500ms 디바운스 적용
→ GET /api/v1/apartments?keyword={keyword}&page=0&size=20 호출

아파트 카드 선택
→ GET /api/v1/apartments/{apartmentId} 호출
→ 아파트 상세 화면으로 이동

latestTransaction = null
→ 최근 실거래 영역에 "최근 실거래 정보 없음" 표시

completedReportCount > 0
→ "완료 리포트 N개" 표시
→ 선택 시 아파트별 완료 리포트 목록 화면으로 이동

hasNext = true
→ 목록 하단 도달 시 page 값을 1 증가
→ 동일한 필터와 정렬 조건으로 다음 페이지 호출
→ 기존 content 뒤에 추가

content가 빈 배열
→ "조건에 맞는 아파트가 없습니다." 표시
→ 다른 지역이나 단지명을 선택하도록 안내
```

---

## 선택한 구의 동 목록 조회

Method: GET
Progress: 완료
URI: /api/v1/regions/districts/{districtCode}/dongs
담당자: 박재명
연동여부: Yes

선택한 서울특별시 자치구에 속한 동 목록을 조회한다.

지역 필터, 아파트 검색, 스터디 검색, 온보딩 관심 지역 선택 화면에서 사용한다.

서비스의 아파트·실거래 데이터와 일관되게 연결할 수 있도록 동 이름과 법정동 코드를 반환한다.

---

### Request

- Request HTTP Method: `GET`
- 인증 필요: 필요
- Request Body: 없음

#### Request Header

```
Authorization: Bearer {accessToken}
```

#### Path Variable

```
GET /api/v1/regions/districts/11710/dongs
```

| 필드 | 타입 | 필수 | 설명 |
| --- | --- | --- | --- |
| `districtCode` | String | Y | 조회할 서울 자치구 코드, 5자리 문자열 |

#### 요청 예시

```
GET /api/v1/regions/districts/11710/dongs
Authorization: Bearer {accessToken}
```

`11710`은 송파구 코드다.

---

### 처리 기준

#### 1. 회원 확인

- 로그인한 회원 ID는 Access Token에서 확인한다.
- 회원 상태가 `ACTIVE`인 경우에만 조회할 수 있다.
- 탈퇴했거나 존재하지 않는 회원은 조회할 수 없다.

---

#### 2. 자치구 코드 검증

- `districtCode`는 공백 제거 후 검증한다.
- 자치구 코드는 5자리 숫자로 구성된 문자열이어야 한다.
- 숫자형으로 변환하지 않고 문자열로 처리한다.
- 서울특별시에 속한 현재 사용 중인 자치구 코드인지 확인한다.
- 서울 외 지역 코드이거나 존재하지 않는 코드이면 조회할 수 없다.
- 비활성화된 자치구는 조회 대상에서 제외한다.

#### 유효한 요청 예시

```
11710
→ 송파구
```

#### 유효하지 않은 요청 예시

```
1171
→ 5자리가 아니므로 오류

26110
→ 부산광역시 코드이므로 서비스 범위 밖

99999
→ 존재하지 않는 자치구 코드
```

---

#### 3. 조회 대상

- 선택한 자치구에 속한 법정동 목록을 반환한다.
- 서비스의 아파트와 실거래 데이터를 법정동 코드로 연결하는 경우 법정동 기준을 사용한다.
- 행정동과 법정동을 혼합하여 반환하지 않는다.
- 폐지되었거나 비활성화된 동은 제외한다.
- 서비스에 등록된 아파트가 없는 동도 지역 필터 일관성을 위해 반환할 수 있다.
- 아파트가 존재하는 동만 노출하기로 결정한 경우 `hasApartment = true`인 동만 필터링할 수 있으나, 기본 정책은 전체 법정동 반환이다.
- 하나의 법정동은 하나의 자치구에만 연결된다.
- 동 코드는 공공데이터와 아파트 테이블에서 사용하는 법정동 코드와 동일해야 한다.
- 법정동 코드는 문자열로 반환한다.
- 동 이름은 화면 표시용 한글 명칭을 반환한다.

---

#### 4. 동별 아파트 존재 여부

- 각 동에 서비스 대상 아파트가 존재하는지 `hasApartment`로 반환한다.
- 서비스 대상 아파트가 한 개 이상 존재하면 `true`다.
- 서비스 대상 아파트가 없으면 `false`다.
- 비활성화되거나 삭제된 아파트는 아파트 수 계산에서 제외한다.
- 동별 서비스 대상 아파트 수를 `apartmentCount`로 반환한다.
- `apartmentCount = 0`이면 `hasApartment = false`여야 한다.

#### 계산 예시

```
잠실동
활성 아파트 18개
→ hasApartment = true
→ apartmentCount = 18

장지동
활성 아파트 0개
→ hasApartment = false
→ apartmentCount = 0
```

---

#### 5. 정렬 기준

- 동 목록은 이름의 가나다순으로 반환한다.
- 같은 이름이 존재할 가능성에 대비해 동 코드 오름차순을 보조 정렬 기준으로 사용한다.

```
1. dongName ASC
2. dongCode ASC
```

프론트에서 별도로 재정렬하지 않아도 된다.

---

#### 6. 상위 자치구 정보

응답에는 요청한 자치구의 코드와 이름을 함께 반환한다.

```json
{
  "districtCode": "11710",
  "districtName": "송파구"
}
```

프론트는 이전 화면에서 전달받은 자치구 이름과 서버 응답값이 다를 경우 서버 응답값을 우선 사용한다.

---

#### 7. 캐시 기준

- 법정동 기준정보는 자주 변경되지 않는다.
- 서버에서 자치구 코드별로 캐시할 수 있다.
- 권장 캐시 시간은 12시간 이상이다.
- 동별 아파트 수가 응답에 포함되므로 해당 집계값은 별도의 짧은 캐시를 사용할 수 있다.
- 아파트 데이터가 신규 적재되더라도 동 이름과 코드는 변하지 않으므로 기준정보와 집계 캐시를 분리할 수 있다.
- 프론트도 자치구별 동 목록을 로컬 캐시할 수 있다.

---

### Response

#### 200 OK

```json
{
  "success": true,
  "code": "REGION_DONG_LIST_SUCCESS",
  "message": "동 목록 조회에 성공했습니다.",
  "data": {
    "districtCode": "11710",
    "districtName": "송파구",
    "dongs": [
      {
        "dongCode": "1171011300",
        "dongName": "가락동",
        "hasApartment": true,
        "apartmentCount": 24
      },
      {
        "dongCode": "1171010700",
        "dongName": "거여동",
        "hasApartment": true,
        "apartmentCount": 12
      },
      {
        "dongCode": "1171011400",
        "dongName": "마천동",
        "hasApartment": true,
        "apartmentCount": 8
      },
      {
        "dongCode": "1171010400",
        "dongName": "문정동",
        "hasApartment": true,
        "apartmentCount": 19
      },
      {
        "dongCode": "1171010300",
        "dongName": "방이동",
        "hasApartment": true,
        "apartmentCount": 16
      },
      {
        "dongCode": "1171010100",
        "dongName": "잠실동",
        "hasApartment": true,
        "apartmentCount": 18
      },
      {
        "dongCode": "1171010800",
        "dongName": "장지동",
        "hasApartment": false,
        "apartmentCount": 0
      }
    ],
    "totalCount": 7
  },
  "timestamp": "2026-07-22T10:30:00+09:00"
}
```

---

### Response Field

#### 자치구 정보

| 필드 | 타입 | 설명 |
| --- | --- | --- |
| `districtCode` | String | 요청한 자치구 코드 |
| `districtName` | String | 요청한 자치구 이름 |
| `dongs` | Array | 선택한 자치구의 법정동 목록 |
| `totalCount` | Integer | 반환된 동 개수 |

#### 동 정보

| 필드 | 타입 | 설명 |
| --- | --- | --- |
| `dongs[].dongCode` | String | 법정동 코드 |
| `dongs[].dongName` | String | 법정동 이름 |
| `dongs[].hasApartment` | Boolean | 서비스 대상 아파트 존재 여부 |
| `dongs[].apartmentCount` | Integer | 해당 동의 서비스 대상 아파트 수 |

---

### 아파트가 없는 동이 포함된 경우

```json
{
  "dongCode": "1171010800",
  "dongName": "장지동",
  "hasApartment": false,
  "apartmentCount": 0
}
```

프론트에서는 다음 중 하나로 처리할 수 있다.

```
지역 선택 목록에는 표시
→ 선택은 가능
→ 아파트 검색 결과는 빈 목록으로 표시
```

또는

```
hasApartment = false
→ 비활성화된 스타일로 표시
→ 선택할 수 없도록 처리
```

기본 권장안은 지역 기준정보의 완전성을 위해 목록에는 표시하되, 아파트가 없다는 상태를 안내하는 방식이다.

---

### 동 목록이 없는 경우

정상적인 서울 자치구라면 동 목록이 비어 있으면 안 된다.

개발 환경에서 기준정보가 아직 적재되지 않은 경우 다음처럼 반환할 수 있다.

```json
{
  "success": true,
  "code": "REGION_DONG_LIST_SUCCESS",
  "message": "동 목록 조회에 성공했습니다.",
  "data": {
    "districtCode": "11710",
    "districtName": "송파구",
    "dongs": [],
    "totalCount": 0
  },
  "timestamp": "2026-07-22T10:30:00+09:00"
}
```

운영 환경에서 `totalCount = 0`이면 법정동 기준정보 적재 상태를 점검해야 한다.

---

### Exception

#### 400 Bad Request — 잘못된 자치구 코드 형식

`districtCode`가 5자리 숫자 문자열이 아닌 경우

```json
{
  "success": false,
  "code": "REGION_DISTRICT_CODE_INVALID",
  "message": "자치구 코드가 올바르지 않습니다.",
  "data": {
    "field": "districtCode",
    "reason": "자치구 코드는 5자리 숫자여야 합니다."
  },
  "timestamp": "2026-07-22T10:30:00+09:00"
}
```

---

#### 400 Bad Request — 서울 외 지역 코드

```json
{
  "success": false,
  "code": "REGION_OUT_OF_SERVICE_AREA",
  "message": "현재 서울 지역만 지원합니다.",
  "data": {
    "districtCode": "26110"
  },
  "timestamp": "2026-07-22T10:30:00+09:00"
}
```

---

#### 401 Unauthorized

Access Token이 없거나 유효하지 않은 경우

```json
{
  "success": false,
  "code": "AUTH_ACCESS_TOKEN_INVALID",
  "message": "로그인이 필요합니다.",
  "data": null,
  "timestamp": "2026-07-22T10:30:00+09:00"
}
```

---

#### 404 Not Found — 회원 없음

Access Token에 해당하는 회원이 존재하지 않거나 탈퇴한 경우

```json
{
  "success": false,
  "code": "MEMBER_NOT_FOUND",
  "message": "회원 정보를 찾을 수 없습니다.",
  "data": null,
  "timestamp": "2026-07-22T10:30:00+09:00"
}
```

---

#### 404 Not Found — 자치구 없음

해당 코드의 서울 자치구가 존재하지 않는 경우

```json
{
  "success": false,
  "code": "REGION_DISTRICT_NOT_FOUND",
  "message": "자치구 정보를 찾을 수 없습니다.",
  "data": {
    "districtCode": "99999"
  },
  "timestamp": "2026-07-22T10:30:00+09:00"
}
```

---

#### 503 Service Unavailable — 지역 기준정보 조회 불가

```json
{
  "success": false,
  "code": "REGION_DATA_UNAVAILABLE",
  "message": "지역 정보를 불러올 수 없습니다.",
  "data": null,
  "timestamp": "2026-07-22T10:30:00+09:00"
}
```

---

#### 500 Internal Server Error

```json
{
  "success": false,
  "code": "COMMON_INTERNAL_SERVER_ERROR",
  "message": "일시적인 오류가 발생했습니다.",
  "data": null,
  "timestamp": "2026-07-22T10:30:00+09:00"
}
```

---

### 프론트 처리

```
사용자가 서울 자치구 선택
→ 선택한 districtCode 확인
→ GET /api/v1/regions/districts/{districtCode}/dongs 호출
→ 해당 자치구의 동 목록 표시

동 목록 표시
→ dongName을 화면에 표시
→ 선택값으로 dongCode를 저장

사용자가 동 선택
→ 아파트 목록 조회 시 dongCode 전달
→ GET /api/v1/apartments?dongCode={dongCode} 호출

hasApartment = true
→ 정상 선택 가능
→ 필요하면 "아파트 N개" 표시

hasApartment = false
→ "등록된 아파트 없음" 표시
→ 정책에 따라 선택 비활성화 또는 빈 검색 허용

온보딩 관심 지역 설정
→ 자치구 선택
→ 동 선택
→ 선택한 districtCode·dongCode 또는 표시 이름을 선호 정보에 저장

자치구 변경
→ 기존에 선택한 동 초기화
→ 새로운 districtCode로 동 목록 재조회

지역 목록 조회 성공
→ 자치구 코드별로 동 목록 로컬 캐시
→ 같은 자치구 재선택 시 캐시 우선 표시

REGION_DATA_UNAVAILABLE
→ 해당 자치구의 기존 캐시가 있으면 캐시 사용
→ 캐시가 없으면 재시도 버튼 표시
```

---

## 아파트별 모집 스터디 목록 조회

Method: GET
Progress: 완료
URI: /api/v1/apartments/{apartmentId}/studies
담당자: 최태선
연동여부: Yes

선택한 아파트를 대상으로 현재 스터디원을 모집하고 있는 임장 스터디 목록을 조회한다.

아파트 상세 화면에서 `모집 중인 스터디` 영역을 선택했을 때 사용한다.

스터디 일정, 현재 인원, 스터디장 정보와 로그인한 회원의 신청·참여 상태를 함께 반환한다.

### Request

- HTTP Method: `GET`
- URI: `/api/v1/apartments/{apartmentId}/studies`
- 인증 필요: 필요

#### Query Parameter

```
GET /api/v1/apartments/15/studies?sort=SCHEDULE_ASC&page=0&size=20
```

#### Request Header

```
Authorization: Bearer {accessToken}
```

#### Path Variable

```
GET /api/v1/apartments/25/studies
```

| 필드 | 타입 | 필수 | 설명 |
| --- | --- | --- | --- |
| `apartmentId` | Long | Y | 모집 스터디를 조회할 아파트 ID, 1 이상의 값 |

### 처리 기준

- 기본적으로 `study.status=RECRUITING`인 스터디만 반환한다.
- 삭제된 스터디는 제외한다.
- 현재 활성 멤버 수를 `currentMemberCount`로 계산한다.
- 다음 일정이 없으면 `nextScheduleAt=null`이다.
- `READY`, `REPORTING` 상태는 사용하지 않는다.
- 목적 enum은 스터디 신청·생성과 같은 계약을 사용한다.

```
RESIDENCE / INVESTMENT / STUDY
```

### 추가 상세 처리 기준

- 로그인한 회원 ID는 Access Token에서 확인한다.
- 회원 상태가 `ACTIVE`인 경우에만 조회할 수 있다.
- 로그인한 회원을 기준으로 각 스터디의 신청 상태와 참여 여부를 계산한다.

---

- 존재하지 않는 아파트는 `404 Not Found`를 반환한다.
- 삭제되거나 서비스 대상에서 제외된 아파트도 동일한 응답을 반환한다.

---

- 취소 또는 삭제된 스터디는 제외한다.
- 모집 조기 마감 처리된 스터디는 제외한다.
- 현재 승인 인원이 최대 인원에 도달한 스터디는 제외한다.
- 신청 대기 인원은 현재 참여 인원에 포함하지 않는다.
- 임장 일정이 등록되지 않은 모집 스터디는 목록에 포함할 수 있다.
- 일정이 등록됐지만 시작 시각이 이미 지난 스터디는 모집 목록에서 제외한다.
- 조회 중 정원이 모두 찬 경우 응답 시점의 상태를 기준으로 반환한다.
- 조회 결과가 없으면 오류가 아닌 빈 배열을 반환한다.

---

- 현재 참여 인원은 승인된 스터디원과 스터디장을 기준으로 계산한다.
- 스터디장도 참여 인원에 포함한다.
- 신청 대기·거절 상태는 포함하지 않는다.
- 탈퇴하거나 강퇴된 회원은 포함하지 않는다.
- 현재 참여 인원은 `memberCount`로 반환한다.
- 최대 참여 인원은 `capacity`로 반환한다.
- 남은 모집 인원은 다음과 같이 계산한다.

```
remainingCapacity = capacity - memberCount
```

- 정상적인 모집 스터디는 `remainingCapacity`가 1 이상이어야 한다.

---

- 현재 유효한 신청이 없다면 `applicationStatus = REJECTED`를 반환할 수 있다.
- 재신청이 허용되는 정책이면 `canApply = true`로 반환한다.
- 재신청이 허용되지 않는 정책이면 `canApply = false`로 반환한다.
- 현재 프로젝트에서는 거절 후 재신청 가능 여부를 서버 정책으로 관리한다.
- 목록 조회 후 신청 API 호출 사이에 정원이 찰 수 있다.
- 최종 신청 가능 여부는 신청 API에서 다시 검증한다.

```
POST /api/v1/studies/{studyId}/applications
```

---

- 스터디에 등록된 활성 임장 일정을 반환한다.
- 일정이 없으면 `schedule = null`을 반환한다.
- `schedule.status = CANCELED`인 일정은 반환하지 않는다.
- 임장 시작·종료 시각은 `Asia/Seoul` 기준 ISO 8601 형식으로 반환한다.
- 일정 시작일까지 남은 날짜를 `dDay`로 반환한다.
- 일정 당일이면 `dDay = 0`이다.
- 모집 목록에는 일정이 이미 종료된 스터디를 포함하지 않는다.

---

- 프로필 이미지는 개정본_v3 현재 범위에서 반환하지 않는다.
- `ageGroupPublicAgreed = true`인 경우에만 `ageGroup`을 반환한다.
- 연령대 공개에 동의하지 않은 경우 `ageGroup = null`로 반환한다.
- 이메일 등 개인정보는 반환하지 않는다.

---

- 일정이 없는 스터디는 일정 기준 정렬에서 뒤에 배치한다.

---

- `page`는 0부터 시작한다.
- `size`는 1 이상 100 이하로 제한한다.
- 다음 페이지 조회 시 같은 `sort`와 `size`를 유지한다.
- 정렬 기준이 변경되면 `page = 0`부터 다시 조회한다.
- 목록이 비어 있으면 빈 배열과 페이지 정보를 반환한다.

---

- `memberCount`는 승인된 현재 멤버 목록의 수와 일치해야 한다.
- `remainingCapacity`는 `capacity - memberCount`와 일치해야 한다.
- `canApply`는 현재 신청·참여 상태와 스터디 모집 상태를 기준으로 계산한다.
- `recruitingStudyCount`는 아파트 상세 API에 반환되는 모집 스터디 수와 일치해야 한다.
- 목록 조회 시 멤버 수와 신청 상태를 스터디별로 반복 조회하지 않도록 집계 쿼리 또는 배치 조회를 사용한다.

---

### Response

#### 200 OK

```json
{
  "success": true,
  "code": "APARTMENT_STUDY_LIST_SUCCESS",
  "message": "아파트 모집 스터디 목록 조회에 성공했습니다.",
  "data": {
    "content": [
      {
        "studyId": 10,
        "status": "RECRUITING",
        "title": "옥수동 주말 임장",
        "intro": "교통과 단지 환경을 함께 확인합니다.",
        "goal": "역 접근성·단지 경사·주변 소음 확인",
        "purpose": "RESIDENCE",
        "currentMemberCount": 4,
        "capacity": 6,
        "nextScheduleAt": "2026-07-27T15:00:00+09:00"
      }
    ],
    "totalElements": 3,
    "page": 0,
    "size": 20,
    "totalPages": 1
  },
  "timestamp": "2026-07-24T17:30:00+09:00"
}
```

---

### 추가 Exception 예시

#### 400 Bad Request — 잘못된 아파트 ID

```json
{
  "success": false,
  "code": "APARTMENT_ID_INVALID",
  "message": "아파트 ID가 올바르지 않습니다.",
  "data": {
    "field": "apartmentId",
    "reason": "아파트 ID는 1 이상의 숫자여야 합니다."
  },
  "timestamp": "2026-07-22T10:30:00+09:00"
}
```

---

#### 400 Bad Request — 잘못된 정렬 기준

```json
{
  "success": false,
  "code": "APARTMENT_STUDY_SORT_INVALID",
  "message": "정렬 기준을 확인해 주세요.",
  "data": {
    "field": "sort",
    "allowedValues": [
      "SCHEDULE_ASC",
      "CREATED_DESC",
      "REMAINING_CAPACITY_DESC"
    ]
  },
  "timestamp": "2026-07-22T10:30:00+09:00"
}
```

---

#### 400 Bad Request — 잘못된 페이지 번호

```json
{
  "success": false,
  "code": "COMMON_INVALID_REQUEST",
  "message": "입력값을 확인해 주세요.",
  "data": {
    "field": "page",
    "reason": "페이지 번호는 0 이상이어야 합니다."
  },
  "timestamp": "2026-07-22T10:30:00+09:00"
}
```

---

#### 400 Bad Request — 잘못된 페이지 크기

```json
{
  "success": false,
  "code": "COMMON_INVALID_REQUEST",
  "message": "입력값을 확인해 주세요.",
  "data": {
    "field": "size",
    "reason": "페이지 크기는 1 이상 100 이하이어야 합니다."
  },
  "timestamp": "2026-07-22T10:30:00+09:00"
}
```

---

#### 401 Unauthorized

```json
{
  "success": false,
  "code": "AUTH_ACCESS_TOKEN_INVALID",
  "message": "로그인이 필요합니다.",
  "data": null,
  "timestamp": "2026-07-22T10:30:00+09:00"
}
```

---

#### 404 Not Found — 회원 없음

```json
{
  "success": false,
  "code": "MEMBER_NOT_FOUND",
  "message": "회원 정보를 찾을 수 없습니다.",
  "data": null,
  "timestamp": "2026-07-22T10:30:00+09:00"
}
```

---

#### 404 Not Found — 아파트 없음

```json
{
  "success": false,
  "code": "APARTMENT_NOT_FOUND",
  "message": "아파트 정보를 찾을 수 없습니다.",
  "data": {
    "apartmentId": 25
  },
  "timestamp": "2026-07-22T10:30:00+09:00"
}
```

---

#### 500 Internal Server Error

```json
{
  "success": false,
  "code": "COMMON_INTERNAL_SERVER_ERROR",
  "message": "일시적인 오류가 발생했습니다.",
  "data": null,
  "timestamp": "2026-07-22T10:30:00+09:00"
}
```

---

### 추가 프론트 처리 기준

```
아파트 상세에서 모집 중인 스터디 영역 선택
→ GET /api/v1/apartments/{apartmentId}/studies 호출
→ 임장 일정이 가까운 스터디부터 표시

스터디 카드 표시
→ 스터디 제목
→ 임장 목적
→ 스터디장 닉네임·캐릭터
→ 현재 인원/최대 인원
→ 임장 일정
→ 신청 상태 표시

schedule != null
→ 임장 날짜·시간·집결 장소·D-day 표시

schedule = null
→ "임장 일정 조율 중" 표시

canApply = true
→ 스터디 신청 버튼 활성화

applicationStatus = PENDING
→ "승인 대기 중" 표시
→ 신청 버튼 비활성화

isMember = true
→ "참여 중" 표시
→ 신청 버튼 대신 스터디 홈 버튼 제공

isLeader = true
→ "내가 만든 스터디" 표시
→ 스터디 관리 또는 스터디 홈 버튼 제공

스터디 카드 선택
→ GET /api/v1/studies/{studyId} 호출
→ 스터디 상세 화면으로 이동

신청 버튼 선택
→ POST /api/v1/studies/{studyId}/applications 호출
→ 신청 성공 시 applicationStatus를 PENDING으로 갱신
→ canApply를 false로 변경

정렬 기준 변경
→ page를 0으로 초기화
→ 선택한 sort로 목록 재조회

hasNext = true
→ 목록 하단 도달 시 page 값을 1 증가
→ 동일한 sort와 size로 다음 페이지 호출
→ 기존 content 뒤에 추가

content가 빈 배열
→ "현재 모집 중인 임장 스터디가 없습니다." 표시
→ 스터디 생성 버튼 제공
```

---

## 아파트 챗봇 대화 시작

Domain: Chatbot
Method: POST
Progress: 완료
URI: /api/v1/apartments/{apartmentId}/chatbot/conversations
담당자: 윤다인
연동여부: No

로그인한 회원이 특정 아파트에 대한 새로운 AI 챗봇 대화를 시작한다.

아파트 상세 화면과 GPS 임장 지도에서 동일한 API를 사용하며, 생성된 `conversationId`는 이후 질문 전송과 이전 대화 이력 조회에 사용한다.

---

### Request

- Request HTTP Method: `POST`
- 인증 필요: 필요
- Request Body: 없음

#### Request Header

```
Authorization: Bearer {accessToken}
```

#### Path Variable

```
POST /api/v1/apartments/15/chatbot/conversations
```

| 필드 | 타입 | 필수 | 설명 |
| --- | --- | --- | --- |
| `apartmentId` | Long | Y | 챗봇 대화를 시작할 아파트 ID |

---

### 처리 기준

#### 1. 회원 및 아파트 확인

- 로그인한 회원 ID는 Access Token에서 확인한다.
- 회원 상태가 `ACTIVE`인 경우에만 대화를 생성할 수 있다.
- `apartmentId`에 해당하는 아파트가 존재하는지 확인한다.
- 삭제되거나 서비스 조회 대상에서 제외된 아파트에는 대화를 생성할 수 없다.
- 대화는 로그인 회원과 요청한 아파트에 연결한다.

#### 2. 대화 생성

- `chatbot_conversation.member_id`에는 로그인 회원 ID를 저장한다.
- `chatbot_conversation.apartment_id`에는 요청한 아파트 ID를 저장한다.
- 생성 시 메시지는 저장하지 않으며, 실제 질문은 질문 전송 API를 통해 등록한다.
- 대화 제목과 별도의 대화 상태는 최종 ERD v7에 없으므로 저장하거나 반환하지 않는다.
- 대화 생성 시 `lastMessageAt`은 `null`이다.
- 첫 질문이 저장되면 사용자 메시지 생성 시각으로 `lastMessageAt`을 갱신한다.

#### 3. 대화 소유권

- 생성된 대화는 생성한 회원만 조회하고 질문을 전송할 수 있다.
- 다른 회원의 `conversationId`를 이용한 이력 조회와 질문 전송은 차단한다.
- 대화는 생성 시 지정한 아파트에 고정된다.
- 다른 아파트 URI에서 동일한 `conversationId`를 사용할 수 없다.

#### 4. 아파트 상세·임장 지도 공통 사용

- 아파트 상세에서 진입하든 GPS 임장 지도에서 진입하든 같은 아파트 ID에 대해 동일한 대화 스키마를 사용한다.
- 임장 지도에서 진입했다고 해서 별도 `studyId` 또는 `fieldSessionId`를 대화 테이블에 저장하지 않는다.
- 다만 AI 답변 생성 시 로그인 회원이 현재 참여 중인 해당 아파트 스터디 문맥을 조회해 추천 질문에 활용할 수 있다.
- 접근할 수 없는 스터디의 비공개 정보는 챗봇 문맥에 포함하지 않는다.

#### 5. 답변 근거 모드 사전 안내

대화 시작 시 해당 아파트에 대해 로그인 회원이 조회할 수 있는 `DONE` 리포트가 존재하는지 확인해 예상 근거 모드를 반환할 수 있다.

```
조회 가능한 DONE 리포트 존재
→ preferredBasisType = REPORT
→ 질문 처리 시 리포트 근거를 먼저 검색

조회 가능한 DONE 리포트 없음
→ preferredBasisType = WEB
→ 질문 처리 시 웹 검색을 우선 시도
```

- `preferredBasisType`은 대화 시작 시점의 우선 탐색 경로를 안내하는 값이며 최종 답변의 근거 유형을 보장하지 않는다.
- 실제 질문 처리 시점에 리포트 상태·관련도·접근 가능성이 달라질 수 있으므로 최종 `basisType`은 답변 생성 결과에서 다시 결정한다.
- 완료 리포트가 존재해도 현재 질문과 관련된 근거가 부족하면 웹 검색으로 전환할 수 있다.

#### 6. 추천 질문

최종 화면에서 다음 추천 질문을 기본 제공한다.

```
교통 어때요?
시세 알려줘
스터디 추천
```

- 추천 질문은 서버 응답으로 반환하거나 앱 상수로 관리할 수 있다.
- 서버에서 반환하는 경우 앱 버전과 관계없이 문구를 변경할 수 있다.
- 추천 질문을 선택하면 일반 질문과 동일하게 질문 전송 API를 호출한다.

#### 7. 중복 생성

- 이 API를 호출할 때마다 새로운 `conversationId`를 생성한다.
- 기존 대화를 이어가려면 저장된 `conversationId`로 이력 조회와 질문 전송 API를 호출한다.
- 네트워크 재시도로 대화가 중복 생성되는 것을 반드시 방지해야 한다면 클라이언트 요청 ID와 Redis 멱등 키를 별도로 도입해야 하나, 현재 ERD v7에는 대화 생성 멱등 컬럼이 없다.

---

### Response

#### 201 Created

```
{
  "success": true,
  "code": "CHATBOT_CONVERSATION_CREATE_SUCCESS",
  "message": "챗봇 대화가 시작되었습니다.",
  "data": {
    "conversationId": 41,
    "apartment": {
      "apartmentId": 15,
      "name": "래미안 옥수 리버젠",
      "address": "서울특별시 성동구 매봉길 15"
    },
    "preferredBasisType": "REPORT",
    "preferredBasisLabel": "리포트 기반",
    "availableReport": {
      "reportId": 48,
      "title": "래미안 옥수 리버젠 임장 리포트"
    },
    "recommendedQuestions": [
      "교통 어때요?",
      "시세 알려줘",
      "스터디 추천"
    ],
    "lastMessageAt": null,
    "createdAt": "2026-07-25T16:00:00+09:00"
  },
  "timestamp": "2026-07-25T16:00:00+09:00"
}
```

#### 201 Created — 완료 리포트가 없는 경우

```
{
  "success": true,
  "code": "CHATBOT_CONVERSATION_CREATE_SUCCESS",
  "message": "챗봇 대화가 시작되었습니다.",
  "data": {
    "conversationId": 42,
    "apartment": {
      "apartmentId": 27,
      "name": "서울숲리버뷰자이",
      "address": "서울특별시 성동구 고산자로2길 65"
    },
    "preferredBasisType": "WEB",
    "preferredBasisLabel": "웹 기반",
    "availableReport": null,
    "recommendedQuestions": [
      "교통 어때요?",
      "시세 알려줘",
      "스터디 추천"
    ],
    "lastMessageAt": null,
    "createdAt": "2026-07-25T16:01:00+09:00"
  },
  "timestamp": "2026-07-25T16:01:00+09:00"
}
```

---

### Response Field

| 필드 | 타입 | 설명 |
| --- | --- | --- |
| `conversationId` | Long | 생성된 챗봇 대화 ID |
| `apartment` | Object | 대화 대상 아파트 정보 |
| `preferredBasisType` | String | 질문 전송 전 우선 탐색 경로 안내, `REPORT`, `WEB` |
| `preferredBasisLabel` | String | 우선 탐색 경로의 화면 표시용 라벨 `리포트 기반`, `웹 기반` |
| `availableReport` | Object | null | 우선 검색할 수 있는 완료 리포트 요약. 최종 답변에 사용되지 않을 수 있음 |
| `recommendedQuestions` | Array<String> | 추천 질문 문구 |
| `lastMessageAt` | String | null | 마지막 메시지 생성 시각, 생성 직후에는 `null` |
| `createdAt` | String | 대화 생성 시각 |

`preferredBasisType`은 대화 테이블 저장 컬럼이 아니라 대화 시작 시점의 현재 문맥을 계산한 응답 필드다. 최종 답변의 `basisType`은 질문 관련도와 실제 사용 출처에 따라 달라질 수 있다.

---

### Exception

#### 400 Bad Request — 잘못된 아파트 ID

```
{
  "success": false,
  "code": "COMMON_INVALID_REQUEST",
  "message": "입력값을 확인해 주세요.",
  "data": {
    "field": "apartmentId",
    "reason": "아파트 ID는 1 이상의 숫자여야 합니다."
  },
  "timestamp": "2026-07-25T16:00:00+09:00"
}
```

---

#### 401 Unauthorized

```
{
  "success": false,
  "code": "AUTH_ACCESS_TOKEN_INVALID",
  "message": "로그인이 필요합니다.",
  "data": null,
  "timestamp": "2026-07-25T16:00:00+09:00"
}
```

---

#### 404 Not Found — 아파트 없음

```
{
  "success": false,
  "code": "APARTMENT_NOT_FOUND",
  "message": "아파트 정보를 찾을 수 없습니다.",
  "data": null,
  "timestamp": "2026-07-25T16:00:00+09:00"
}
```

---

#### 500 Internal Server Error

```
{
  "success": false,
  "code": "COMMON_INTERNAL_SERVER_ERROR",
  "message": "일시적인 오류가 발생했습니다.",
  "data": null,
  "timestamp": "2026-07-25T16:00:00+09:00"
}
```

---

### 프론트 처리

```
아파트 상세 또는 GPS 임장 지도에서 AI 챗봇 선택
→ POST /api/v1/apartments/{apartmentId}/chatbot/conversations 호출
→ conversationId 저장
→ 챗봇 대화 화면으로 이동

recommendedQuestions 표시
→ 사용자가 추천 질문 선택
→ 선택한 문구를 질문 전송 API의 content로 전달

preferredBasisType = REPORT
→ 입력창 상단 또는 안내 영역에 "리포트 기반" 표시

preferredBasisType = WEB
→ "웹 기반" 표시
→ 웹 정보는 시점에 따라 달라질 수 있다는 안내 가능

APARTMENT_NOT_FOUND
→ "조회할 수 없는 아파트입니다." 안내
→ 이전 화면으로 이동
```

---

## 스터디장 전체 임장 마감

Method: POST
Progress: 완료
URI: /api/v1/studies/{studyId}/field-visit/close
담당자: 윤다인
연동여부: No

스터디장이 진행 중인 임장 세션을 전체 마감한다.

아직 종료하지 않은 참여자는 현재까지 저장된 체크리스트와 현장 기록을 기준으로 강제 종료하고, 세션을 종료한 뒤 AI 리포트 생성 이벤트를 한 번만 발행한다.

---

### Request

- Request HTTP Method: `POST`
- 인증 필요: 필요
- Content-Type: `application/json`

#### Request Header

```
Authorization: Bearer {accessToken}
```

#### Path Variable

```
POST /api/v1/studies/7/field-visit/close
```

| 필드 | 타입 | 필수 | 설명 |
| --- | --- | --- | --- |
| `studyId` | Long | Y | 전체 임장을 마감할 스터디 ID |

#### Request Body

```json
{
  "closeConfirmed": true,
  "clientRequestId": "5c91e23b-b315-450e-b803-fb45150e947e"
}
```

| 필드 | 타입 | 필수 | 설명 |
| --- | --- | --- | --- |
| `closeConfirmed` | Boolean | Y | 미종료 참여자 강제 종료 안내 확인 여부 |
| `clientRequestId` | String | Y | 중복 전체 마감 요청 방지용 UUID |

---

### 처리 기준

#### 1. 스터디장 권한

- 로그인한 회원 ID는 Access Token에서 확인한다.
- 로그인 회원은 `study.leader_id` 또는 `study_member.role = LEADER`인 스터디장이어야 한다.
- 일반 참여자는 전체 임장을 마감할 수 없다.

#### 2. 세션 상태 확인

- 스터디에 `field_session`이 존재해야 한다.
- `field_session.status = IN_PROGRESS`인 경우에만 최초 마감 처리를 수행한다.
- 이미 `ENDED`인 세션에 다시 요청하면 멱등하게 기존 종료 결과를 반환한다.
- 세션이 아직 시작되지 않았으면 전체 마감 대상이 아니다.

#### 3. 미종료 참여자 확인

- `field_participant.status = IN_PROGRESS`인 참여자를 조회한다.
- 아직 GPS 시작을 하지 않아 `field_participant` 행이 없는 고정 후보는 강제 종료 대상에 포함하지 않는다.
- 미종료 참여자가 있으면 `closeConfirmed = true`일 때만 마감한다.
- 프론트는 마감 전 미종료 인원 수와 미완료 체크리스트가 현재 기록만으로 반영된다는 내용을 안내한다.

#### 4. 참여자 강제 종료

- 미종료 참여자의 상태를 `ENDED`로 변경한다.
- `endedAt`은 전체 마감 시각으로 저장한다.
- `endReason = LEADER_FORCED`로 저장한다.
- `stayDurationSec`를 각 참여자의 `startedAt`부터 마감 시각까지 계산해 확정한다.
- 이미 개인 종료한 참여자의 종료 시각·사유·체류시간은 변경하지 않는다.

#### 5. 세션 종료

- `field_session.status = ENDED`로 변경한다.
- `endedAt`에 서버 현재 시각을 저장한다.
- `endedById`에 스터디장 회원 ID를 저장한다.
- `endReason = LEADER_FORCED`로 저장한다.
- 종료 후 체크리스트, 현장 기록, 신규 STT 요청 등 모든 쓰기 동작을 차단한다.

#### 6. 리포트 생성 이벤트

- 마감 트랜잭션이 성공하면 `REPORT_REQUESTED` 이벤트를 발행한다.
- `report`가 아직 없으면 마감 트랜잭션 안에서 `PENDING` 상태의 리포트 행을 먼저 생성한다.
- 종료 응답의 `reportId`는 커밋 후 이벤트를 처리하는 워커가 acquire하는 권위 리포트 ID와 동일하다.
- 스터디당 리포트는 한 건만 존재한다.
- `clientRequestId`, 세션 ID 또는 report ID 기반 idempotency key로 이벤트 중복 발행을 방지한다.
- 이벤트 발행과 DB 상태 변경 사이의 정합성은 Outbox 또는 트랜잭션 이벤트 정책으로 보장하는 것을 권장한다.

#### 7. 현재 기록 반영

- 강제 종료된 참여자는 마감 시점까지 서버에 저장 완료된 체크리스트·텍스트·사진·STT 결과만 리포트 입력에 포함한다.
- 오프라인 로컬에만 남아 있고 서버에 재전송되지 않은 기록은 리포트에 포함할 수 없다.
- 진행 중 STT 작업은 리포트 워커의 STT 확인 단계에서 완료 여부를 검사한다.

#### 8. 멱등성

- 동일 `clientRequestId`가 반복되면 기존 종료·리포트 정보를 반환한다.
- 다른 요청 ID로 이미 종료된 세션을 다시 마감해도 참여자 종료 정보와 세션 종료 시각을 변경하지 않는다.
- 리포트 생성 이벤트는 재발행하지 않는다.

---

### Response

#### 200 OK — 전체 마감 완료

```json
{
  "success": true,
  "code": "FIELD_VISIT_CLOSE_SUCCESS",
  "message": "전체 임장을 마감하고 리포트 생성을 시작했습니다.",
  "data": {
    "studyId": 7,
    "session": {
      "sessionId": 100,
      "status": "ENDED",
      "startedAt": "2026-07-25T14:00:00+09:00",
      "endedAt": "2026-07-25T15:00:00+09:00",
      "endedByMemberId": 42,
      "endReason": "LEADER_FORCED"
    },
    "forcedEndedParticipants": [
      {
        "participantId": 303,
        "memberId": 63,
        "endReason": "LEADER_FORCED",
        "endedAt": "2026-07-25T15:00:00+09:00",
        "stayDurationSec": 3300
      }
    ],
    "forcedEndedCount": 1,
    "reportTriggered": true,
    "reportId": 48,
    "reportStatus": "PENDING"
  },
  "timestamp": "2026-07-25T15:00:00+09:00"
}
```

#### 200 OK — 이미 마감된 세션

```json
{
  "success": true,
  "code": "FIELD_VISIT_ALREADY_CLOSED",
  "message": "이미 마감된 임장 세션입니다.",
  "data": {
    "studyId": 7,
    "session": {
      "sessionId": 100,
      "status": "ENDED",
      "startedAt": "2026-07-25T14:00:00+09:00",
      "endedAt": "2026-07-25T15:00:00+09:00",
      "endedByMemberId": 42,
      "endReason": "LEADER_FORCED"
    },
    "forcedEndedParticipants": [],
    "forcedEndedCount": 0,
    "reportTriggered": false,
    "reportId": 48,
    "reportStatus": "IN_PROGRESS"
  },
  "timestamp": "2026-07-25T15:02:00+09:00"
}
```

---

### Response Field

| 필드 | 타입 | 설명 |
| --- | --- | --- |
| `session` | Object | 종료된 세션 정보 |
| `forcedEndedParticipants` | Array | 이번 요청으로 강제 종료된 참여자 목록 |
| `forcedEndedCount` | Integer | 강제 종료 인원 수 |
| `reportTriggered` | Boolean | 이번 요청에서 리포트 이벤트를 발행했는지 여부 |
| `reportId` | Long | 생성 또는 기존 리포트 ID |
| `reportStatus` | String | 리포트 생성 상태 |

---

### Exception

#### 400 Bad Request — 마감 확인 누락

```json
{
  "success": false,
  "code": "FIELD_VISIT_CLOSE_CONFIRMATION_REQUIRED",
  "message": "미종료 참여자 강제 종료 여부를 확인해 주세요.",
  "data": {
    "field": "closeConfirmed",
    "unfinishedParticipantCount": 1
  },
  "timestamp": "2026-07-25T15:00:00+09:00"
}
```

---

#### 401 Unauthorized

```json
{
  "success": false,
  "code": "AUTH_ACCESS_TOKEN_INVALID",
  "message": "로그인이 필요합니다.",
  "data": null,
  "timestamp": "2026-07-25T15:00:00+09:00"
}
```

---

#### 403 Forbidden — 스터디장 아님

```json
{
  "success": false,
  "code": "FIELD_VISIT_CLOSE_FORBIDDEN",
  "message": "스터디장만 전체 임장을 마감할 수 있습니다.",
  "data": null,
  "timestamp": "2026-07-25T15:00:00+09:00"
}
```

---

#### 404 Not Found — 임장 세션 없음

```json
{
  "success": false,
  "code": "FIELD_VISIT_NOT_FOUND",
  "message": "임장 세션을 찾을 수 없습니다.",
  "data": null,
  "timestamp": "2026-07-25T15:00:00+09:00"
}
```

---

#### 500 Internal Server Error

```json
{
  "success": false,
  "code": "COMMON_INTERNAL_SERVER_ERROR",
  "message": "일시적인 오류가 발생했습니다.",
  "data": null,
  "timestamp": "2026-07-25T15:00:00+09:00"
}
```

---

### 프론트 처리

```
스터디장이 전체 마감 선택
→ 참여자 상태 조회
→ 미종료 인원과 현재 기록만 반영된다는 경고 모달 표시

마감 확인
→ closeConfirmed = true, clientRequestId로 close API 호출
→ 중복 탭 방지

마감 성공
→ 임장 진행 화면 전체를 읽기 전용으로 전환
→ forcedEndedParticipants 상태 갱신
→ reportId로 리포트 생성 상태 화면 이동

FIELD_VISIT_ALREADY_CLOSED
→ 오류로 표시하지 않음
→ 반환된 reportId·reportStatus 기준으로 리포트 화면 이동

마감 요청 실패
→ 화면을 진행 중 상태로 유지
→ 참여자 상태 재조회 후 재시도 안내
```

---

## 과반수 동의 기반 전체 임장 종료 요청

Domain: Field Visit
Method: POST
Progress: 완료
URI: /api/v1/studies/{studyId}/field-visit/close-votes
담당자: 윤다인
연동여부: No

실제 임장을 시작해 `field_participant`가 생성된 ACTIVE 스터디 멤버가 공용 임장 세션의 전체 종료에 동의한다.

과반수에 도달하면 남아 있는 진행 중 참여자를 모두 종료하고 세션을 `MAJORITY_FORCED`로 마감한 뒤 리포트 생성 이벤트를 발행한다. 이 API는 스터디장 즉시 전체 마감(`POST .../close`)을 대체하지 않으며, 개인 종료(`POST .../finish`)와도 병행된다.

| 종료 방식 | API | 실행 주체 | 세션 종료 사유 |
| --- | --- | --- | --- |
| 개인 종료 | `POST /finish` | 각 participant | `SELF_ENDED`, 마지막이면 세션 `ALL_ENDED` |
| 스터디장 즉시 마감 | `POST /close` | 스터디장 | `LEADER_FORCED` |
| 과반수 종료 요청 | `POST /close-votes` | 실제 시작 participant | 과반수 도달 시 `MAJORITY_FORCED` |

---

### Request

- Request HTTP Method: `POST`
- 인증 필요: 필요
- Request Body: 없음

#### Request Header

```
Authorization: Bearer {accessToken}
```

#### Path Variable

```
POST /api/v1/studies/7/field-visit/close-votes
```

| 필드 | 타입 | 필수 | 설명 |
| --- | --- | --- | --- |
| `studyId` | Long | Y | 과반수 종료 투표를 요청할 스터디 ID |

---

### 처리 기준

#### 1. 회원·스터디 권한 확인

- 로그인한 회원 ID는 Access Token에서 확인한다.
- `studyId`에 해당하는 스터디가 존재해야 한다.
- 요청자는 해당 스터디의 ACTIVE 승인 멤버여야 한다.
- ACTIVE가 아닌 멤버는 `FIELD_VISIT_CLOSE_VOTE_FORBIDDEN`으로 거부한다.

#### 2. 투표 자격

- 해당 스터디의 `field_session`이 존재해야 한다. 없으면 `FIELD_VISIT_NOT_FOUND`다.
- 실제 `field_participant`가 있는 사용자만 투표할 수 있다.
- candidate만 있고 임장을 시작하지 않은 사용자는 투표할 수 없다.
- candidate만 있는 사용자는 과반수 분모에도 포함하지 않는다.
- session이 `IN_PROGRESS`일 때만 신규 투표를 추가한다.
- participant가 `IN_PROGRESS`인 경우 투표 가능하다.
- 개인 임장을 먼저 종료한 `ENDED` participant도 공용 session이 `IN_PROGRESS`라면 투표 가능하다.
- 스터디장도 실제 participant라면 투표 가능하다.
- 투표 취소 API는 없다.
- 기존 스터디장 즉시 전체 종료 API(`POST .../close`)는 별도로 유지한다.

#### 3. 한 표·멱등 처리

- 한 participant당 한 표만 저장한다. DB UNIQUE(`session`, `participant`)로 방어한다.
- 동일 사용자의 중복 호출은 투표 수를 증가시키지 않는 멱등 처리다.
- 중복 호출 시 HTTP 200, `hasVoted=true`, `canVote=false`이며 리포트 이벤트를 새로 발행하지 않는다.
- 세션이 아직 진행 중이면 응답 코드는 `FIELD_VISIT_CLOSE_VOTE_SUCCESS`다.

#### 4. 과반수 계산

```text
requiredVoteCount =
floor(startedParticipantCount / 2) + 1
```

분모(`startedParticipantCount`)는 현재 session에 생성된 `field_participant` 전체다.

- `IN_PROGRESS` 포함
- `ENDED` 포함
- candidate-only 미시작자 제외

| 실제 시작 참여자 수 | 필요 동의 수 |
| ---: | ---: |
| 1 | 1 |
| 2 | 2 |
| 3 | 2 |
| 4 | 3 |
| 5 | 3 |

#### 5. 과반수 도달 결과

- 남아 있는 `IN_PROGRESS` participant 전체를 `ENDED`로 변경한다.
- participant `endReason = MAJORITY_FORCED`
- `field_session.status = ENDED`
- `field_session.endReason = MAJORITY_FORCED`
- 이미 `ENDED`인 participant는 다시 변경하지 않는다.
- 리포트 생성 요청 이벤트(`ReportRequestedEvent`)를 발행한다.
- 실제 Kafka 발행은 트랜잭션 커밋 후(`AFTER_COMMIT`) 처리한다.
- Kafka 발행 전에 같은 트랜잭션에서 스터디당 유일한 `PENDING` 리포트를 생성해 응답 ID를 확보한다.

#### 6. 이미 종료된 세션

- session이 이미 `ENDED`이면 신규 투표를 insert하지 않는다.
- 신규 리포트 이벤트를 발행하지 않는다.
- HTTP 200, 응답 코드 `FIELD_VISIT_ALREADY_CLOSED`
- `sessionEnded=true`, `canVote=false`, `reportTriggered=false`

---

### Response

#### 200 OK — 과반수 미달 동의 성공

```json
{
  "success": true,
  "code": "FIELD_VISIT_CLOSE_VOTE_SUCCESS",
  "message": "전체 임장 종료 요청에 동의했습니다.",
  "data": {
    "studyId": 7,
    "sessionId": 100,
    "startedParticipantCount": 3,
    "voteCount": 1,
    "requiredVoteCount": 2,
    "hasVoted": true,
    "canVote": false,
    "sessionEnded": false,
    "sessionEndReason": null,
    "reportTriggered": false,
    "reportId": null,
    "reportStatus": null
  },
  "timestamp": "2026-07-25T14:40:00+09:00"
}
```

#### 200 OK — 과반수 도달로 세션 종료

```json
{
  "success": true,
  "code": "FIELD_VISIT_CLOSE_VOTE_AND_SESSION_END_SUCCESS",
  "message": "과반수 동의로 전체 임장을 종료하고 리포트 생성을 시작했습니다.",
  "data": {
    "studyId": 7,
    "sessionId": 100,
    "startedParticipantCount": 3,
    "voteCount": 2,
    "requiredVoteCount": 2,
    "hasVoted": true,
    "canVote": false,
    "sessionEnded": true,
    "sessionEndReason": "MAJORITY_FORCED",
    "reportTriggered": true,
    "reportId": 48,
    "reportStatus": "PENDING"
  },
  "timestamp": "2026-07-25T14:45:00+09:00"
}
```

#### 200 OK — 중복 투표(멱등)

```json
{
  "success": true,
  "code": "FIELD_VISIT_CLOSE_VOTE_SUCCESS",
  "message": "전체 임장 종료 요청에 동의했습니다.",
  "data": {
    "studyId": 7,
    "sessionId": 100,
    "startedParticipantCount": 3,
    "voteCount": 1,
    "requiredVoteCount": 2,
    "hasVoted": true,
    "canVote": false,
    "sessionEnded": false,
    "sessionEndReason": null,
    "reportTriggered": false,
    "reportId": null,
    "reportStatus": null
  },
  "timestamp": "2026-07-25T14:41:00+09:00"
}
```

#### 200 OK — 이미 마감된 세션

```json
{
  "success": true,
  "code": "FIELD_VISIT_ALREADY_CLOSED",
  "message": "이미 마감된 임장 세션입니다.",
  "data": {
    "studyId": 7,
    "sessionId": 100,
    "startedParticipantCount": 3,
    "voteCount": 2,
    "requiredVoteCount": 2,
    "hasVoted": true,
    "canVote": false,
    "sessionEnded": true,
    "sessionEndReason": "MAJORITY_FORCED",
    "reportTriggered": false,
    "reportId": 48,
    "reportStatus": "IN_PROGRESS"
  },
  "timestamp": "2026-07-25T15:02:00+09:00"
}
```

---

### Response Field

| 필드 | 타입 | 설명 |
| --- | --- | --- |
| `studyId` | Long | 스터디 ID |
| `sessionId` | Long | 임장 세션 ID |
| `startedParticipantCount` | Integer | 실제 임장을 시작한 참여자 수 |
| `voteCount` | Integer | 현재 종료 동의 수 |
| `requiredVoteCount` | Integer | 과반수 종료에 필요한 동의 수 |
| `hasVoted` | Boolean | 현재 사용자의 투표 여부 |
| `canVote` | Boolean | 현재 사용자의 추가 투표 가능 여부 |
| `sessionEnded` | Boolean | 이번 요청 결과 세션 종료 여부 |
| `sessionEndReason` | String \| null | 세션 종료 사유 |
| `reportTriggered` | Boolean | 이번 요청으로 리포트 생성 이벤트가 발생했는지 여부 |
| `reportId` | Long \| null | 세션 종료 시 생성되었거나 기존에 존재하는 리포트 ID |
| `reportStatus` | String \| null | `PENDING`, `IN_PROGRESS`, `DONE`, `FAILED`; 리포트가 없으면 `null` |

---

### Exception

#### 401 Unauthorized

```json
{
  "success": false,
  "code": "AUTH_ACCESS_TOKEN_INVALID",
  "message": "로그인이 필요합니다.",
  "data": null,
  "timestamp": "2026-07-25T14:40:00+09:00"
}
```

---

#### 403 Forbidden — 투표 자격 없음

```json
{
  "success": false,
  "code": "FIELD_VISIT_CLOSE_VOTE_FORBIDDEN",
  "message": "실제 임장을 시작한 참여자만 전체 임장 종료를 요청할 수 있습니다.",
  "data": null,
  "timestamp": "2026-07-25T14:40:00+09:00"
}
```

ACTIVE 스터디 멤버가 아니거나 실제 `field_participant`가 없을 때 반환한다.

---

#### 404 Not Found — 스터디 없음

```json
{
  "success": false,
  "code": "STUDY_NOT_FOUND",
  "message": "스터디를 찾을 수 없습니다.",
  "data": null,
  "timestamp": "2026-07-25T14:40:00+09:00"
}
```

---

#### 404 Not Found — 임장 세션 없음

```json
{
  "success": false,
  "code": "FIELD_VISIT_NOT_FOUND",
  "message": "임장 세션을 찾을 수 없습니다.",
  "data": null,
  "timestamp": "2026-07-25T14:40:00+09:00"
}
```

---

#### 500 Internal Server Error

```json
{
  "success": false,
  "code": "COMMON_INTERNAL_SERVER_ERROR",
  "message": "일시적인 오류가 발생했습니다.",
  "data": null,
  "timestamp": "2026-07-25T14:40:00+09:00"
}
```

---

### 프론트 처리

```
임장 진행 중 상태 조회
→ closeVote.canVote가 true이면 전체 종료 요청 버튼 노출
→ closeVote.voteCount / requiredVoteCount로 동의 현황 표시

전체 종료 요청
→ POST /close-votes 호출 (Body 없음)
→ FIELD_VISIT_CLOSE_VOTE_SUCCESS
  → 투표 현황만 갱신, 세션 유지
→ FIELD_VISIT_CLOSE_VOTE_AND_SESSION_END_SUCCESS
  → 임장 화면을 읽기 전용으로 전환
  → 리포트 생성 상태 화면 이동
→ FIELD_VISIT_ALREADY_CLOSED
  → 오류로 표시하지 않음
  → 상위 status·session.endReason 기준으로 종료 화면 처리

FIELD_VISIT_CLOSE_VOTE_FORBIDDEN
→ 투표 불가 안내
→ 상태 재조회
```

---

## 커뮤니티 게시글 목록/검색

Domain: Community
Method: GET
Progress: 완료
URI: /api/v1/posts
담당자: 김윤석
연동여부: No

정보게시판과 자유게시판의 게시글을 조회하고 제목·본문 키워드로 검색한다.

각 게시판에서 `HOT` 인기 글과 `LATEST` 최신 글을 구분해 조회하며, 목록 카드에 조회 수·좋아요 수·댓글 수·HOT 여부와 자동 리포트 게시글 정보를 반환한다.

---

### Request

- Request HTTP Method: `GET`
- 인증 필요: 필요
- Request Body: 없음

#### Request Header

```
Authorization: Bearer {accessToken}
```

#### Query Parameter

최신 글 첫 조회:

```
GET /api/v1/posts?boardType=INFORMATION&sort=LATEST&size=20
```

HOT 글 조회:

```
GET /api/v1/posts?boardType=INFORMATION&sort=HOT&size=10
```

검색 다음 페이지 조회:

```
GET /api/v1/posts?boardType=FREE&sort=LATEST&keyword=옥수동&cursor=eyJzb3J0IjoiTEFURVNUIiwiaWQiOjEyNH0&size=20
```

| 필드 | 타입 | 필수 | 기본값 | 설명 |
| --- | --- | --- | --- | --- |
| `boardType` | String | N | 전체 | 게시판 구분. `INFORMATION`, `FREE` |
| `sort` | String | N | `LATEST` | 정렬·목록 유형. `HOT`, `LATEST` |
| `keyword` | String | N | 없음 | 제목과 본문 검색어, 최대 100자 |
| `cursor` | String | N | 없음 | 다음 페이지 조회용 서버 발급 커서 |
| `size` | Integer | N | `20` | 한 번에 조회할 게시글 수, 1~50 |

#### 게시판 유형

| 값 | 설명 |
| --- | --- |
| `INFORMATION` | 아파트·지역·임장 정보와 완료 AI 리포트를 제공하는 정보게시판 |
| `FREE` | 임장·주거 생활 등 자유로운 주제로 소통하는 자유게시판 |

기존 `INFO` 값은 사용하지 않고 최종 ERD v7의 `INFORMATION`으로 통일한다.

#### 정렬 유형

| 값 | 설명 |
| --- | --- |
| `HOT` | 확정 HOT 점수 기준을 충족한 최근 인기 글 |
| `LATEST` | 생성 시각과 게시글 ID 기준 최신 글 |

---

### HOT 확정 규칙

최종 ERD v7의 `post_hot_metric` VIEW를 기준으로 계산한다.

#### 대상 게시글

```
status = ACTIVE
AND deletedAt IS NULL
AND 작성 후 7일 이내
```

#### 점수식

```
HOT 점수
= 조회 수 × 1
+ 좋아요 수 × 5
+ 댓글 수 × 3
+ max(0, 168 - 작성 후 경과 시간) × 0.1
```

#### HOT 기준 및 정렬

```
hotScore >= 20

hotScore DESC
→ createdAt DESC
→ postId DESC
```

- 삭제된 댓글은 댓글 수에 포함하지 않는다.
- 7일이 지난 게시글은 점수가 높더라도 HOT 목록에서 제외한다.
- `HOT` 조회 시 `isHot = true`인 게시글만 반환한다.
- `LATEST` 조회에서도 각 게시글의 현재 `isHot`, `hotScore`, `hotRank`를 표시용으로 반환할 수 있다.

---

### 처리 기준

#### 1. 회원 확인

- 로그인한 회원 ID는 Access Token에서 확인한다.
- 회원 상태가 `ACTIVE`인 경우에만 커뮤니티 목록을 조회할 수 있다.
- `likedByMe`, `isMine`을 계산하기 위해 로그인 회원 ID를 사용한다.

#### 2. 조회 대상

다음 조건을 만족하는 게시글만 일반 목록에 노출한다.

```
post.status = ACTIVE
post.deletedAt IS NULL
```

- 운영 정책으로 `HIDDEN` 처리된 글은 제외한다.
- 작성자가 삭제한 글은 제외한다.
- 자동 리포트 게시글과 일반 회원 게시글을 모두 반환한다.
- 자동 리포트 게시글은 `boardType = INFORMATION`, `isAutoReport = true`, `reportId != null`이어야 한다.
- 리포트별 활성 자동 정보게시글은 한 건만 존재해야 한다.

#### 3. 게시판 필터

- `boardType`을 전달하면 해당 게시판만 조회한다.
- 전달하지 않으면 `INFORMATION`, `FREE`를 함께 조회할 수 있으나 실제 탭 UI에서는 게시판 값을 전달하는 것을 권장한다.
- 지원하지 않는 게시판 값은 오류로 처리한다.

#### 4. 키워드 검색

- `keyword`의 앞뒤 공백을 제거한다.
- 공백만 입력하면 검색 조건이 없는 것으로 처리한다.
- 제목과 본문을 대상으로 검색한다.
- PostgreSQL trigram 검색 인덱스를 이용해 부분 일치 검색을 지원한다.
- 검색어는 최대 100자다.
- 숨김·삭제 게시글은 검색 결과에도 포함하지 않는다.
- 검색 결과가 없으면 오류가 아니라 빈 배열을 반환한다.

#### 5. 최신 글 정렬

`sort = LATEST`일 때 다음 순서로 정렬한다.

```
createdAt DESC
→ postId DESC
```

- 게시글 수정 시 `updatedAt`이 변경되더라도 최신 글 상단으로 다시 올라가지 않는다.
- 최신 정렬은 최초 작성 시각을 기준으로 한다.

#### 6. 목록 표시용 본문과 첨부 파일

- 본문 전체가 아니라 목록용 `contentPreview`를 반환한다.
- 미리보기는 줄바꿈·HTML 제어문자를 정리한 일반 텍스트 최대 150자로 반환한다.
- 첨부 파일이 있으면 첫 번째 이미지 파일을 `thumbnailUrl`로 반환할 수 있다.
- 첨부 파일 전체는 게시글 상세 조회에서 반환한다.
- 접근 URL은 짧은 유효기간의 URL일 수 있다.

#### 7. 작성자 정보

- 일반 게시글은 작성자의 회원 ID, 닉네임, 프로필 이미지, 선택 캐릭터를 반환한다.
- 자동 리포트 게시글은 시스템 작성자로 표시한다.
- 자동 리포트 게시글의 `author.memberId`는 `null`일 수 있다.
- 탈퇴 회원의 게시글을 보존하는 경우 작성자 정보를 비식별화한다.

#### 8. 반응·조회 정보

- `likeCount`는 현재 `post_like` 수를 반환한다.
- `commentCount`는 `deletedAt IS NULL`인 댓글 수를 반환한다.
- `viewCount`는 게시글 상세 조회 과정에서 증가한 누적 조회 수다.
- 로그인 회원의 좋아요 여부를 `likedByMe`로 반환한다.
- 로그인 회원이 작성자인지 `isMine`으로 반환한다.
- 목록 조회만으로 조회 수를 증가시키지 않는다.

#### 9. 자동 리포트 게시글

- 자동 리포트 글에는 연결된 아파트와 리포트 요약을 반환한다.
- 연결 리포트는 `DONE` 상태이며 현재 조회 가능한 완료 리포트여야 한다.
- 리포트가 더 이상 공개되지 않거나 접근할 수 없으면 자동 게시글 노출 정책에 따라 게시글을 숨김 처리하거나 `reportAvailable = false`로 반환한다.
- 목록에서 리포트 원문 근거를 반환하지 않는다.

#### 10. 커서 페이지네이션

- 커서는 정렬 유형에 따라 필요한 정렬 키를 서버가 인코딩한 문자열이다.
- 프론트는 커서를 직접 생성·수정하지 않고 응답의 `nextCursor`를 그대로 전달한다.
- `LATEST` 커서에는 마지막 게시글의 `createdAt`, `postId`가 포함될 수 있다.
- `HOT` 커서에는 `hotScore`, `createdAt`, `postId`가 포함될 수 있다.
- `size + 1`건을 조회해 다음 데이터 존재 여부를 판단한다.
- 검색어·게시판·정렬 조건이 바뀌면 기존 커서를 폐기하고 첫 페이지부터 다시 조회한다.

---

### Response

#### 200 OK — 정보게시판 최신 글

```
{
  "success": true,
  "code": "POST_LIST_SUCCESS",
  "message": "게시글 목록 조회에 성공했습니다.",
  "data": {
    "boardType": "INFORMATION",
    "sort": "LATEST",
    "keyword": null,
    "content": [
      {
        "postId": 153,
        "boardType": "INFORMATION",
        "title": "래미안 옥수 리버젠 임장 리포트가 공개됐어요",
        "contentPreview": "옥수역 접근성과 생활 편의시설은 긍정적이며 단지 진입 경사는 추가 확인이 필요합니다.",
        "author": {
          "memberId": null,
          "nickname": "싸방팔방 리포트",
          "profileImageUrl": null,
          "selectedCharacterId": "PALBANG",
          "authorType": "SYSTEM"
        },
        "thumbnailUrl": null,
        "isAutoReport": true,
        "apartment": {
          "apartmentId": 15,
          "name": "래미안 옥수 리버젠"
        },
        "report": {
          "reportId": 48,
          "status": "DONE",
          "reportAvailable": true
        },
        "viewCount": 82,
        "likeCount": 5,
        "commentCount": 3,
        "likedByMe": true,
        "isMine": false,
        "isHot": true,
        "hotScore": 124.6,
        "hotRank": 2,
        "createdAt": "2026-07-25T12:00:00+09:00",
        "updatedAt": "2026-07-25T12:00:00+09:00"
      },
      {
        "postId": 152,
        "boardType": "INFORMATION",
        "title": "성동구 임장 시 확인하면 좋은 교통 포인트",
        "contentPreview": "역 입구까지 거리뿐 아니라 개찰구까지 걸리는 시간과 출퇴근 혼잡도를 함께 확인해 보세요.",
        "author": {
          "memberId": 12,
          "nickname": "옥수탐방러",
          "profileImageUrl": null,
          "selectedCharacterId": "DURI",
          "authorType": "MEMBER"
        },
        "thumbnailUrl": "https://s3.example.com/presigned/post-thumb-401",
        "isAutoReport": false,
        "apartment": {
          "apartmentId": 15,
          "name": "래미안 옥수 리버젠"
        },
        "report": null,
        "viewCount": 24,
        "likeCount": 2,
        "commentCount": 1,
        "likedByMe": false,
        "isMine": false,
        "isHot": true,
        "hotScore": 38.2,
        "hotRank": 6,
        "createdAt": "2026-07-25T11:30:00+09:00",
        "updatedAt": "2026-07-25T11:30:00+09:00"
      }
    ],
    "pageInfo": {
      "size": 20,
      "nextCursor": "eyJzb3J0IjoiTEFURVNUIiwiaWQiOjE1Mn0",
      "hasNext": true
    }
  },
  "timestamp": "2026-07-25T16:20:00+09:00"
}
```

#### 200 OK — HOT 글

```
{
  "success": true,
  "code": "POST_LIST_SUCCESS",
  "message": "게시글 목록 조회에 성공했습니다.",
  "data": {
    "boardType": "FREE",
    "sort": "HOT",
    "keyword": null,
    "content": [
      {
        "postId": 140,
        "boardType": "FREE",
        "title": "주말 임장 다녀온 후기입니다",
        "contentPreview": "교통과 상권은 좋았지만 단지 내 경사가 생각보다 있었습니다.",
        "author": {
          "memberId": 8,
          "nickname": "주말탐방러",
          "profileImageUrl": null,
          "selectedCharacterId": "JIPKONG",
          "authorType": "MEMBER"
        },
        "thumbnailUrl": null,
        "isAutoReport": false,
        "apartment": null,
        "report": null,
        "viewCount": 110,
        "likeCount": 11,
        "commentCount": 8,
        "likedByMe": false,
        "isMine": false,
        "isHot": true,
        "hotScore": 204.4,
        "hotRank": 1,
        "createdAt": "2026-07-24T19:30:00+09:00",
        "updatedAt": "2026-07-24T19:30:00+09:00"
      }
    ],
    "pageInfo": {
      "size": 10,
      "nextCursor": null,
      "hasNext": false
    }
  },
  "timestamp": "2026-07-25T16:20:00+09:00"
}
```

#### 검색 결과가 없는 경우

```
{
  "success": true,
  "code": "POST_LIST_SUCCESS",
  "message": "게시글 목록 조회에 성공했습니다.",
  "data": {
    "boardType": "FREE",
    "sort": "LATEST",
    "keyword": "존재하지않는검색어",
    "content": [],
    "pageInfo": {
      "size": 20,
      "nextCursor": null,
      "hasNext": false
    }
  },
  "timestamp": "2026-07-25T16:20:00+09:00"
}
```

---

### Response Field

#### 목록 정보

| 필드 | 타입 | 설명 |
| --- | --- | --- |
| `boardType` | String | null | 적용된 게시판 필터 |
| `sort` | String | 정렬 유형 |
| `keyword` | String | null | 정제된 검색어 |
| `content` | Array | 게시글 카드 목록 |
| `pageInfo` | Object | 커서 페이지네이션 정보 |

#### `content[]`

| 필드 | 타입 | 설명 |
| --- | --- | --- |
| `postId` | Long | 게시글 ID |
| `boardType` | String | `INFORMATION`, `FREE` |
| `title` | String | 게시글 제목 |
| `contentPreview` | String | 목록용 본문 미리보기 |
| `author` | Object | 작성자 또는 시스템 작성자 정보 |
| `thumbnailUrl` | String | null | 첫 이미지 썸네일 URL |
| `isAutoReport` | Boolean | 자동 리포트 게시글 여부 |
| `apartment` | Object | null | 연결 아파트 요약 |
| `report` | Object | null | 연결 리포트 요약 |
| `viewCount` | Long | 누적 조회 수 |
| `likeCount` | Long | 좋아요 수 |
| `commentCount` | Long | 삭제되지 않은 댓글 수 |
| `likedByMe` | Boolean | 현재 회원의 좋아요 여부 |
| `isMine` | Boolean | 현재 회원이 작성자인지 여부 |
| `isHot` | Boolean | 확정 HOT 기준 충족 여부 |
| `hotScore` | Decimal | null | HOT 계산 점수 |
| `hotRank` | Long | null | 게시판 내 HOT 순위 |
| `createdAt` | String | 작성 시각 |
| `updatedAt` | String | 최종 수정 시각 |

---

### Exception

#### 400 Bad Request — 게시판 유형 오류

```
{
  "success": false,
  "code": "POST_BOARD_TYPE_INVALID",
  "message": "유효하지 않은 게시판 유형입니다.",
  "data": {
    "field": "boardType",
    "allowedValues": ["INFORMATION", "FREE"]
  },
  "timestamp": "2026-07-25T16:20:00+09:00"
}
```

---

#### 400 Bad Request — 정렬 유형 오류

```
{
  "success": false,
  "code": "POST_SORT_INVALID",
  "message": "유효하지 않은 게시글 정렬 기준입니다.",
  "data": {
    "field": "sort",
    "allowedValues": ["HOT", "LATEST"]
  },
  "timestamp": "2026-07-25T16:20:00+09:00"
}
```

---

#### 400 Bad Request — 검색어 길이 초과

```
{
  "success": false,
  "code": "COMMON_INVALID_REQUEST",
  "message": "입력값을 확인해 주세요.",
  "data": {
    "field": "keyword",
    "reason": "검색어는 100자 이하여야 합니다."
  },
  "timestamp": "2026-07-25T16:20:00+09:00"
}
```

---

#### 400 Bad Request — 유효하지 않은 커서

```
{
  "success": false,
  "code": "COMMON_INVALID_CURSOR",
  "message": "유효하지 않은 페이지 커서입니다.",
  "data": null,
  "timestamp": "2026-07-25T16:20:00+09:00"
}
```

---

#### 401 Unauthorized

```
{
  "success": false,
  "code": "AUTH_ACCESS_TOKEN_INVALID",
  "message": "로그인이 필요합니다.",
  "data": null,
  "timestamp": "2026-07-25T16:20:00+09:00"
}
```

---

#### 500 Internal Server Error

```
{
  "success": false,
  "code": "COMMON_INTERNAL_SERVER_ERROR",
  "message": "일시적인 오류가 발생했습니다.",
  "data": null,
  "timestamp": "2026-07-25T16:20:00+09:00"
}
```

---

### 프론트 처리

```
커뮤니티 진입
→ 정보 또는 자유 탭 선택
→ HOT와 LATEST API를 각각 호출하거나 섹션 진입 시 호출
→ HOT 카드에 HOT 배지 표시

검색 실행
→ 현재 boardType과 keyword, sort를 전달
→ 기존 목록과 cursor 초기화
→ 검색 결과 표시

게시판 또는 정렬 변경
→ 기존 nextCursor 폐기
→ 첫 페이지 재조회

목록 하단 도달
→ hasNext = true이면 nextCursor를 그대로 전달
→ 기존 목록 뒤에 추가

isAutoReport = true
→ AI 리포트 배지 표시
→ reportAvailable = true이면 리포트 상세 이동 제공

content가 빈 배열
→ 오류 화면이 아닌 "게시글이 없습니다." 빈 상태 표시
```

---

## 지도 가시 영역 아파트 조회

Method: GET
Progress: 완료
URI: /api/v1/apartments/bounds
담당자: 최태선
연동여부: Yes

Mapbox 지도에서 현재 화면에 보이는 경계 안의 서울 아파트를 조회한다.

서버는 아파트별 위치, 최근 실거래 요약, 모집 중인 스터디 수, 완료된 완료 리포트 수와 로그인 회원의 찜 여부를 평면 목록으로 반환한다. 지도 축척에 따른 클러스터 생성·해제와 클러스터 선택 시 확대 동작은 Mapbox SDK에서 처리한다.

---

### Request

- Request HTTP Method: `GET`
- 인증 필요: 필요
- Request Body: 없음

#### Request Header

```
Authorization: Bearer {accessToken}
```

#### Query Parameter

```
GET /api/v1/apartments/bounds
    ?southWestLat=37.5000
    &southWestLng=126.9000
    &northEastLat=37.6000
    &northEastLng=127.1000
```

| 필드 | 타입 | 필수 | 설명 |
| --- | --- | --- | --- |
| `southWestLat` | Double | Y | 현재 지도 화면 남서쪽 위도 |
| `southWestLng` | Double | Y | 현재 지도 화면 남서쪽 경도 |
| `northEastLat` | Double | Y | 현재 지도 화면 북동쪽 위도 |
| `northEastLng` | Double | Y | 현재 지도 화면 북동쪽 경도 |

---

### 처리 기준

#### 1. 회원 확인

- 로그인한 회원 ID는 Access Token에서 확인한다.
- 회원 상태가 `ACTIVE`인 경우에만 조회할 수 있다.
- 로그인 회원을 기준으로 각 아파트의 찜 여부를 `favoritedByMe`로 계산한다.
- 지도 조회 자체로 찜 관계를 생성하거나 삭제하지 않는다.

#### 2. 좌표 형식 검증

- 네 개의 경계 좌표는 모두 필수다.
- 위도는 `-90` 이상 `90` 이하만 허용한다.
- 경도는 `-180` 이상 `180` 이하만 허용한다.
- 남서쪽 위도는 북동쪽 위도보다 작아야 한다.
- 남서쪽 경도는 북동쪽 경도보다 작아야 한다.

```
southWestLat < northEastLat
southWestLng < northEastLng
```

- `NaN`, 무한대, 숫자로 변환할 수 없는 값은 허용하지 않는다.
- 경계가 서울 영역과 겹치지 않으면 오류가 아니라 빈 목록을 반환할 수 있다.

#### 3. 최대 조회 범위

- 한 번에 지나치게 넓은 영역을 요청하지 못하도록 최대 위도·경도 차이 또는 최대 면적을 제한한다.
- 허용 범위를 초과하면 `APARTMENT_BOUNDS_TOO_LARGE` 오류를 반환한다.
- 서버는 넓은 범위를 클러스터 응답으로 강제 변환하지 않는다.
- 프론트는 오류를 받으면 지도를 확대하도록 안내한다.

#### 4. 아파트 조회 범위

- 현재 지도 경계 안에 좌표가 존재하는 아파트만 반환한다.
- 현재 서비스 범위인 서울특별시 소재 아파트만 반환한다.
- 오피스텔, 원룸, 투룸 등 MVP 범위 밖의 주거 유형은 제외한다.
- 위치 정보가 없는 아파트는 지도에 표시할 수 없으므로 결과에서 제외한다.
- 동일한 `complexCode` 또는 `apartmentId`에 해당하는 아파트가 중복 반환되지 않도록 한다.
- 결과가 없으면 빈 배열과 `count = 0`을 반환한다.

PostGIS를 사용할 경우 다음과 같은 공간 조건으로 조회할 수 있다.

```
ST_Within(
  ST_SetSRID(ST_MakePoint(apartment.longitude, apartment.latitude), 4326),
  ST_MakeEnvelope(
    :southWestLng,
    :southWestLat,
    :northEastLng,
    :northEastLat,
    4326
  )
)
```

단순 범위 조건을 사용하는 경우 다음과 같이 조회한다.

```
apartment.latitude BETWEEN :southWestLat AND :northEastLat
AND apartment.longitude BETWEEN :southWestLng AND :northEastLng
```

#### 5. 최근 실거래 요약

- 각 아파트의 취소되지 않은 가장 최근 실거래 한 건을 `latestTransaction`으로 반환한다.
- `apartment_transaction.is_canceled = false`인 거래만 사용한다.
- 최신 거래 선택 순서는 다음과 같다.

```
1. dealDate DESC
2. collectedAt DESC
3. id DESC
```

- 가격은 아파트 실거래 API와 동일하게 만 원 단위로 반환한다.
- 최근 정상 거래가 없으면 `latestTransaction = null`, `latestTransactionAvailable = false`로 반환한다.
- 최근 거래가 없는 아파트도 지도 결과에는 포함한다.

#### 6. 모집 중인 스터디 수

- 해당 아파트에 연결된 `study.status = RECRUITING` 스터디 수를 `recruitingStudyCount`로 반환한다.
- 신청 대기 인원은 스터디 수 계산에 영향을 주지 않는다.
- `CLOSED`, `IN_PROGRESS`, `COMPLETED`, `CANCELED` 상태의 스터디는 모집 중인 스터디 수에 포함하지 않는다.
- 모집 중인 스터디가 없으면 `0`을 반환한다.

#### 7. 완료된 완료 리포트 수

- `PENDING`, `IN_PROGRESS`, `FAILED` 상태 리포트는 포함하지 않는다.
- 완료된 완료 리포트가 없으면 `0`을 반환한다.
- 완료 리포트 수와 리포트 근거 원문 접근 권한은 별개다. 완료 리포트의 근거 원문은 해당 스터디 참여자만 조회할 수 있다.

#### 8. 응답 크기와 성능

- 이 API는 지도 마커와 하단 요약 카드에 필요한 최소 정보만 반환한다.
- 아파트 전체 상세 정보와 전체 실거래 내역은 포함하지 않는다.
- 조회 결과가 지나치게 많아지는 것을 방지하기 위해 최대 반환 개수를 제한할 수 있다.
- 최대 개수를 초과할 가능성이 있으면 조회 범위를 확대하지 말고 프론트에 지도 확대를 요청하는 정책을 사용한다.
- 위도·경도 또는 PostGIS 공간 인덱스를 사용하여 전체 아파트를 순차 검색하지 않는다.
- 최근 거래, 스터디 수, 리포트 수와 찜 여부를 아파트별 개별 쿼리로 조회하지 않고 집계 쿼리 또는 배치 조회를 사용한다.
- 동일하거나 유사한 지도 경계 요청은 짧은 시간 동안 캐시할 수 있다. 회원별 찜 여부는 공용 캐시와 분리하거나 응답 조합 단계에서 추가한다.

#### 9. Mapbox 클러스터 처리

- 서버 응답에는 `mode`, `clusters`, `clusterId`, `forcedClustering`과 같은 서버 클러스터 필드를 포함하지 않는다.
- 프론트는 `apartments` 배열을 Mapbox GeoJSON Source에 전달한다.
- 클러스터 반경, 클러스터 최소 포인트 수와 확대 수준별 표현은 Mapbox 스타일 설정에서 관리한다.
- 클러스터를 선택하면 Mapbox SDK로 확대하고, 카메라 이동이 끝난 뒤 변경된 경계로 이 API를 다시 호출한다.

---

### Response

#### 200 OK

```
{
  "success": true,
  "code": "APARTMENT_BOUNDS_SUCCESS",
  "message": "지도 영역의 아파트 조회에 성공했습니다.",
  "data": {
    "apartments": [
      {
        "apartmentId": 15,
        "name": "래미안 옥수 리버젠",
        "address": "서울특별시 성동구 매봉길 15",
        "latitude": 37.5412,
        "longitude": 127.0178,
        "latestTransaction": {
          "price": 183000,
          "priceUnit": "TEN_THOUSAND_KRW",
          "exclusiveArea": 84.95,
          "dealDate": "2026-06-15"
        },
        "latestTransactionAvailable": true,
        "recruitingStudyCount": 2,
        "completedReportCount": 1,
        "favoritedByMe": true
      },
      {
        "apartmentId": 18,
        "name": "옥수파크힐스",
        "address": "서울특별시 성동구 매봉길 50",
        "latitude": 37.5461,
        "longitude": 127.0134,
        "latestTransaction": null,
        "latestTransactionAvailable": false,
        "recruitingStudyCount": 0,
        "completedReportCount": 0,
        "favoritedByMe": false
      }
    ],
    "count": 2
  },
  "timestamp": "2026-07-25T11:20:00+09:00"
}
```

#### Response Field

| 필드 | 타입 | 설명 |
| --- | --- | --- |
| `apartments` | Array | 현재 지도 경계 안의 아파트 목록 |
| `apartments[].apartmentId` | Long | 아파트 ID |
| `apartments[].name` | String | 아파트명 |
| `apartments[].address` | String | 아파트 주소 |
| `apartments[].latitude` | Double | 아파트 위도 |
| `apartments[].longitude` | Double | 아파트 경도 |
| `apartments[].latestTransaction` | Object/null | 가장 최근 정상 실거래 요약 |
| `apartments[].latestTransaction.price` | Long | 거래 가격, 만 원 단위 |
| `apartments[].latestTransaction.priceUnit` | String | 가격 단위, `TEN_THOUSAND_KRW` |
| `apartments[].latestTransaction.exclusiveArea` | Double | 전용면적, ㎡ |
| `apartments[].latestTransaction.dealDate` | String | 거래일 |
| `apartments[].latestTransactionAvailable` | Boolean | 최근 정상 실거래 존재 여부 |
| `apartments[].recruitingStudyCount` | Integer | 모집 중인 스터디 수 |
| `apartments[].completedReportCount` | Integer | 완료된 완료 리포트 수 |
| `apartments[].favoritedByMe` | Boolean | 로그인 회원의 아파트 찜 여부 |
| `count` | Integer | 반환된 아파트 수 |

#### 200 OK — 조회 결과 없음

```
{
  "success": true,
  "code": "APARTMENT_BOUNDS_SUCCESS",
  "message": "지도 영역의 아파트 조회에 성공했습니다.",
  "data": {
    "apartments": [],
    "count": 0
  },
  "timestamp": "2026-07-25T11:20:00+09:00"
}
```

---

### Exception

#### 400 Bad Request — 지도 경계 누락

```
{
  "success": false,
  "code": "APARTMENT_BOUNDS_REQUIRED",
  "message": "지도 영역 정보를 입력해 주세요.",
  "data": {
    "reason": "남서쪽과 북동쪽 좌표가 모두 필요합니다."
  },
  "timestamp": "2026-07-25T11:20:00+09:00"
}
```

#### 400 Bad Request — 잘못된 좌표

```
{
  "success": false,
  "code": "APARTMENT_BOUNDS_COORDINATE_INVALID",
  "message": "지도 좌표가 올바르지 않습니다.",
  "data": {
    "reason": "위도는 -90 이상 90 이하, 경도는 -180 이상 180 이하이어야 합니다."
  },
  "timestamp": "2026-07-25T11:20:00+09:00"
}
```

#### 400 Bad Request — 경계 순서 오류

```
{
  "success": false,
  "code": "APARTMENT_BOUNDS_ORDER_INVALID",
  "message": "지도 영역의 남서쪽과 북동쪽 좌표를 확인해 주세요.",
  "data": {
    "reason": "남서쪽 좌표는 북동쪽 좌표보다 작아야 합니다."
  },
  "timestamp": "2026-07-25T11:20:00+09:00"
}
```

#### 400 Bad Request — 조회 영역이 너무 넓은 경우

```
{
  "success": false,
  "code": "APARTMENT_BOUNDS_TOO_LARGE",
  "message": "지도 영역을 조금 더 확대해 주세요.",
  "data": null,
  "timestamp": "2026-07-25T11:20:00+09:00"
}
```

#### 401 Unauthorized

```
{
  "success": false,
  "code": "AUTH_ACCESS_TOKEN_INVALID",
  "message": "로그인이 필요합니다.",
  "data": null,
  "timestamp": "2026-07-25T11:20:00+09:00"
}
```

#### 404 Not Found — 회원 없음

```
{
  "success": false,
  "code": "MEMBER_NOT_FOUND",
  "message": "회원 정보를 찾을 수 없습니다.",
  "data": null,
  "timestamp": "2026-07-25T11:20:00+09:00"
}
```

#### 500 Internal Server Error

```
{
  "success": false,
  "code": "COMMON_INTERNAL_SERVER_ERROR",
  "message": "일시적인 오류가 발생했습니다.",
  "data": null,
  "timestamp": "2026-07-25T11:20:00+09:00"
}
```

---

### 프론트 처리

```
스터디 찾기 지도 진입
→ 서울 기본 위치로 Mapbox 지도 표시
→ 카메라 이동 완료 시 현재 남서·북동 경계 좌표 확인
→ GET /api/v1/apartments/bounds 호출

사용자가 지도 이동 또는 확대·축소
→ 카메라 이동 완료 이벤트 확인
→ 약 300~500ms 디바운스 적용
→ 변경된 경계 좌표로 API 재호출

응답 수신
→ apartments 배열을 GeoJSON Feature로 변환
→ Mapbox Source에 전달
→ Mapbox SDK에서 클러스터와 개별 마커 표시

클러스터 선택
→ Mapbox SDK로 클러스터 중심 확대
→ 카메라 이동 완료 후 변경된 경계로 API 재호출

개별 아파트 마커 선택
→ 단지명·최근 실거래·모집 스터디 수·완료 리포트 수·찜 여부를 하단 카드에 표시

아파트 카드 선택
→ GET /api/v1/apartments/{apartmentId} 호출
→ 아파트 상세 화면으로 이동

APARTMENT_BOUNDS_TOO_LARGE
→ "지도를 조금 더 확대해 주세요." 안내
→ 기존 지도 데이터는 유지하거나 초기화 정책에 따라 처리
```

---

## 자치구별 아파트 집계 조회

Domain: Apartment
Method: GET
Progress: 완료
URI: /api/v1/apartments/districts/summary
담당자: 최태선
연동여부: Yes

서울 25개 자치구별 아파트 단지 수와 중심 좌표를 조회한다. 지도 축소 상태에서 구 단위 클러스터를 표시할 때 사용한다.

### Request

- HTTP Method: `GET`
- URI: `/api/v1/apartments/districts/summary`
- 인증 필요: 필요
- Request Body: 없음

#### Request Header

```
Authorization: Bearer {accessToken}
```

쿼리 파라미터는 없다.

### 처리 기준

- 로그인한 회원 ID는 Access Token에서 확인한다.
- 서울 25개 자치구 전체를 항상 반환한다. 집계 결과가 없는 자치구는 `apartmentCount=0`, `centerLatitude=null`, `centerLongitude=null`로 반환한다.
- `centerLatitude`·`centerLongitude`는 해당 자치구 아파트 좌표의 중심값이다.
- `totalCount`는 반환한 자치구 수(25), `totalApartmentCount`는 자치구별 아파트 수의 합계다.

### Response

#### 200 OK

```json
{
  "success": true,
  "code": "APARTMENT_DISTRICT_SUMMARY_SUCCESS",
  "message": "자치구별 아파트 집계 조회에 성공했습니다.",
  "data": {
    "districts": [
      {
        "districtCode": "11680",
        "districtName": "강남구",
        "apartmentCount": 312,
        "centerLatitude": 37.4979,
        "centerLongitude": 127.0276
      },
      {
        "districtCode": "11740",
        "districtName": "강동구",
        "apartmentCount": 0,
        "centerLatitude": null,
        "centerLongitude": null
      }
    ],
    "totalCount": 25,
    "totalApartmentCount": 5124
  },
  "timestamp": "2026-08-06T15:00:00+09:00"
}
```

#### Response Field

| 필드 | 타입 | 설명 |
| --- | --- | --- |
| `districts[].districtCode` | String | 서울 자치구 코드 |
| `districts[].districtName` | String | 자치구 이름 |
| `districts[].apartmentCount` | Long | 자치구 내 아파트 단지 수 |
| `districts[].centerLatitude` | Double | 자치구 아파트 중심 위도, 집계 없으면 `null` |
| `districts[].centerLongitude` | Double | 자치구 아파트 중심 경도, 집계 없으면 `null` |
| `totalCount` | Integer | 반환한 자치구 수 |
| `totalApartmentCount` | Long | 전체 아파트 단지 수 합계 |

### Exception

- `401 AUTH_ACCESS_TOKEN_INVALID`
- `404 MEMBER_NOT_FOUND`
- `500 COMMON_INTERNAL_SERVER_ERROR`

---

## 아파트 최근 실거래 목록 조회

Method: GET
Progress: 완료
URI: /api/v1/apartments/{apartmentId}/transactions
담당자: 최태선
연동여부: Yes

선택한 아파트 단지의 최근 매매 실거래 목록을 조회한다.

아파트 상세 화면의 `실거래 전체 보기`에서 사용하며, 거래 해제·취소되지 않은 정상 거래를 최근 거래일 순으로 반환한다.

거래 가격은 만 원, 전용면적은 ㎡ 단위로 통일한다.

### Request

- HTTP Method: `GET`
- URI: `/api/v1/apartments/{apartmentId}/transactions`
- 인증 필요: 필요

#### Query Parameter

```
GET /api/v1/apartments/15/transactions?exclusiveArea=84.95&year=2026&sort=DEAL_DATE_DESC&page=0&size=20
```

기존 정렬·면적·연도 필터를 유지한다.

#### Request Header

```
Authorization: Bearer {accessToken}
```

#### Path Variable

```
GET /api/v1/apartments/25/transactions
```

| 필드 | 타입 | 필수 | 설명 |
| --- | --- | --- | --- |
| `apartmentId` | Long | Y | 실거래를 조회할 아파트 ID, 1 이상의 값 |

### 처리 기준

- `apartment_transaction.is_canceled=false`인 거래만 반환한다.
- `price`는 만 원 단위다.
- 최종 ERD에 없는 `buildYear`, `legalDongName`, `lotNumber`는 응답에서 제거한다.
- 거래가 없으면 빈 목록을 반환한다.

### 추가 상세 처리 기준

- 로그인한 회원 ID는 Access Token에서 확인한다.
- 회원 상태가 `ACTIVE`인 경우에만 조회할 수 있다.
- 실거래 데이터는 모든 로그인 회원에게 동일하게 제공한다.
- 회원별 찜 여부나 스터디 참여 여부는 실거래 목록에 영향을 주지 않는다.

---

- 존재하지 않는 아파트는 `404 Not Found`를 반환한다.
- 서비스 대상에서 제외되거나 삭제된 아파트도 동일한 응답을 반환한다.
- 아파트가 존재하지만 실거래가 없으면 빈 배열을 반환한다.

---

- 전세·월세 거래가 별도 테이블에 존재하더라도 현재 API에서는 반환하지 않는다.
- 거래 해제·취소된 건은 목록에서 제외한다.
- 동일한 거래가 공공데이터 중복 적재로 여러 번 저장되지 않도록 고유 식별값 또는 복합 유니크 조건을 적용한다.
- 공공데이터 정정으로 거래 내용이 변경된 경우 최신 정정 데이터를 기준으로 반환한다.

---

- 계약 연·월·일 정보를 조합하여 `dealDate`로 반환한다.
- 계약일이 일 단위까지 제공되지 않는 데이터라면 실제 원천 데이터 범위에 맞게 처리한다.
- 날짜 형식은 `YYYY-MM-DD`로 통일한다.

```json
{
  "dealDate": "2026-07-10"
}
```

---

- 실거래 가격은 만 원 단위의 정수로 반환한다.
- 원천 데이터에 쉼표가 포함된 경우 제거한 뒤 숫자로 변환한다.

```
원천 데이터: "245,000"
응답 데이터: 245000
단위: 만 원
```

프론트 표시 예시:

```
245000만 원
→ 24억 5,000만 원
```

- 거래 가격이 없거나 유효하지 않은 데이터는 목록에서 제외하거나 `null` 처리한다.
- 정상 거래 데이터에서 가격이 누락되는 경우 데이터 적재 상태를 점검한다.

---

- 전용면적은 ㎡ 단위로 반환한다.
- 소수점 데이터가 존재할 수 있으므로 `Double` 타입으로 반환한다.
- 화면에서 평형을 표시할 경우 프론트에서 별도로 계산할 수 있다.

```
84.8㎡
→ 약 25.7평
```

- API 응답에는 평 단위 값을 기본으로 포함하지 않는다.
- 정확한 거래 비교 기준은 전용면적 ㎡를 사용한다.

---

- 거래된 세대의 층수를 반환한다.
- 지하층인 경우 원천 데이터 구조에 따라 음수 또는 별도 문자열로 관리할 수 있다.
- 현재 응답은 정수형 `floor`를 기본으로 한다.
- 층 정보가 제공되지 않으면 `null`을 반환한다.

---

- 아파트 상세의 사용승인일과 실거래 원천 데이터의 건축 연도가 다를 수 있다.
- 실거래 응답에서는 해당 거래 원천 데이터의 건축 연도를 사용한다.
- 정보가 없으면 `null`을 반환한다.

---

- 거래 원천 데이터에 법정동과 지번이 포함되어 있으면 응답에 포함할 수 있다.
- 해당 정보는 아파트 식별과 데이터 검증 목적으로 사용한다.
- 목록 카드에서 반드시 표시할 필요는 없다.
- 개인정보에 해당하는 동·호수 정보는 저장하거나 반환하지 않는다.

```
허용
→ 잠실동
→ 지번 19

반환 금지
→ 101동 1203호
```

---

- 요청값은 0보다 커야 한다.
- 실수 오차를 고려하여 정확히 같은 값만 비교하지 않을 수 있다.
- 권장 비교 허용 범위는 `±0.05㎡`다.

```
요청 전용면적: 84.8㎡

조회 범위 예시:
84.75㎡ 이상
84.85㎡ 이하
```

- 전용면적 필터가 없으면 모든 면적의 거래를 반환한다.

---

- `year`가 전달되면 해당 연도에 계약된 거래만 반환한다.
- 허용 범위는 공공데이터가 제공되는 최초 연도부터 현재 연도까지다.
- 미래 연도는 허용하지 않는다.
- 현재 MVP에서는 연도 단위 필터만 제공한다.
- 월 단위 필터가 필요하면 이후 `month` Query Parameter를 추가할 수 있다.

---

- 가격 통계는 현재 적용된 `exclusiveArea`, `year` 조건을 기준으로 계산한다.
- 페이지에 포함된 거래만이 아니라 전체 조회 조건의 거래를 기준으로 계산한다.
- 실거래가 없으면 가격 요약값은 `null`로 반환한다.

---

- `page`는 0부터 시작한다.
- `size`는 1 이상 100 이하로 제한한다.
- 다음 페이지 조회 시 같은 필터와 정렬 기준을 유지한다.

```
exclusiveArea
year
sort
size
```

- 필터나 정렬값이 변경되면 `page = 0`부터 다시 조회한다.
- 조회 결과가 없으면 오류가 아닌 빈 배열을 반환한다.

---

- 실거래 데이터는 외부 공공데이터를 정기적으로 수집하여 저장한다.
- 동일한 거래가 중복 저장되지 않도록 원천 거래 식별값을 관리한다.
- 거래 해제 데이터가 추가로 수집되면 해당 거래를 정상 목록에서 제외한다.
- 데이터 적재 시각은 사용자에게 표시할 거래일과 구분한다.
- 필요한 경우 응답에 `dataUpdatedAt`을 포함하여 데이터 기준 시각을 안내한다.

---

### Response

#### 200 OK

```json
{
  "success": true,
  "code": "APARTMENT_TRANSACTION_LIST_SUCCESS",
  "message": "아파트 실거래 목록 조회에 성공했습니다.",
  "data": {
    "content": [
      {
        "transactionId": 381,
        "dealDate": "2026-07-10",
        "price": 245000,
        "priceUnit": "TEN_THOUSAND_KRW",
        "exclusiveArea": 84.80,
        "floor": 15
      }
    ],
    "totalElements": 12,
    "page": 0,
    "size": 20,
    "totalPages": 1
  },
  "timestamp": "2026-07-24T17:25:00+09:00"
}
```

### Exception

- `400 APARTMENT_TRANSACTION_FILTER_INVALID`
- `401 AUTH_ACCESS_TOKEN_INVALID`
- `404 APARTMENT_NOT_FOUND`
- `500 COMMON_INTERNAL_SERVER_ERROR`

---

### 추가 Exception 예시

#### 400 Bad Request — 잘못된 아파트 ID

```json
{
  "success": false,
  "code": "APARTMENT_ID_INVALID",
  "message": "아파트 ID가 올바르지 않습니다.",
  "data": {
    "field": "apartmentId",
    "reason": "아파트 ID는 1 이상의 숫자여야 합니다."
  },
  "timestamp": "2026-07-22T10:30:00+09:00"
}
```

---

#### 400 Bad Request — 잘못된 전용면적

```json
{
  "success": false,
  "code": "APARTMENT_TRANSACTION_AREA_INVALID",
  "message": "전용면적을 확인해 주세요.",
  "data": {
    "field": "exclusiveArea",
    "reason": "전용면적은 0보다 커야 합니다."
  },
  "timestamp": "2026-07-22T10:30:00+09:00"
}
```

---

#### 400 Bad Request — 잘못된 계약 연도

```json
{
  "success": false,
  "code": "APARTMENT_TRANSACTION_YEAR_INVALID",
  "message": "조회할 연도를 확인해 주세요.",
  "data": {
    "field": "year",
    "reason": "현재 연도보다 이후의 거래는 조회할 수 없습니다."
  },
  "timestamp": "2026-07-22T10:30:00+09:00"
}
```

---

#### 400 Bad Request — 잘못된 정렬 기준

```json
{
  "success": false,
  "code": "APARTMENT_TRANSACTION_SORT_INVALID",
  "message": "정렬 기준을 확인해 주세요.",
  "data": {
    "field": "sort",
    "allowedValues": [
      "DEAL_DATE_DESC",
      "DEAL_DATE_ASC",
      "PRICE_DESC",
      "PRICE_ASC",
      "AREA_DESC",
      "AREA_ASC"
    ]
  },
  "timestamp": "2026-07-22T10:30:00+09:00"
}
```

---

#### 400 Bad Request — 잘못된 페이지 번호

```json
{
  "success": false,
  "code": "COMMON_INVALID_REQUEST",
  "message": "입력값을 확인해 주세요.",
  "data": {
    "field": "page",
    "reason": "페이지 번호는 0 이상이어야 합니다."
  },
  "timestamp": "2026-07-22T10:30:00+09:00"
}
```

---

#### 400 Bad Request — 잘못된 페이지 크기

```json
{
  "success": false,
  "code": "COMMON_INVALID_REQUEST",
  "message": "입력값을 확인해 주세요.",
  "data": {
    "field": "size",
    "reason": "페이지 크기는 1 이상 100 이하이어야 합니다."
  },
  "timestamp": "2026-07-22T10:30:00+09:00"
}
```

---

#### 401 Unauthorized

```json
{
  "success": false,
  "code": "AUTH_ACCESS_TOKEN_INVALID",
  "message": "로그인이 필요합니다.",
  "data": null,
  "timestamp": "2026-07-22T10:30:00+09:00"
}
```

---

#### 404 Not Found — 회원 없음

```json
{
  "success": false,
  "code": "MEMBER_NOT_FOUND",
  "message": "회원 정보를 찾을 수 없습니다.",
  "data": null,
  "timestamp": "2026-07-22T10:30:00+09:00"
}
```

---

#### 404 Not Found — 아파트 없음

```json
{
  "success": false,
  "code": "APARTMENT_NOT_FOUND",
  "message": "아파트 정보를 찾을 수 없습니다.",
  "data": {
    "apartmentId": 25
  },
  "timestamp": "2026-07-22T10:30:00+09:00"
}
```

---

#### 500 Internal Server Error

```json
{
  "success": false,
  "code": "COMMON_INTERNAL_SERVER_ERROR",
  "message": "일시적인 오류가 발생했습니다.",
  "data": null,
  "timestamp": "2026-07-22T10:30:00+09:00"
}
```

---

### 추가 프론트 처리 기준

```
아파트 상세에서 실거래 전체 보기 선택
→ GET /api/v1/apartments/{apartmentId}/transactions?page=0&size=20 호출
→ 최근 거래일부터 목록 표시

거래 카드 표시
→ 거래 가격
→ 계약일
→ 전용면적
→ 층수 표시

가격 표시
→ API의 price는 만 원 단위
→ 억·만 원 단위로 변환하여 표시

전용면적 필터 선택
→ summary.exclusiveAreas 목록을 필터 선택지로 사용
→ 선택한 exclusiveArea로 page = 0부터 재조회

계약 연도 필터 선택
→ 선택한 year로 page = 0부터 재조회

정렬 조건 변경
→ sort 값 변경
→ page = 0으로 초기화
→ 실거래 목록 재조회

hasNext = true
→ 목록 하단 도달 시 page 값을 1 증가
→ 동일한 필터·정렬 조건으로 다음 페이지 호출
→ 기존 content 뒤에 추가

content가 빈 배열
→ "최근 실거래 정보가 없습니다." 표시

dataUpdatedAt 표시가 필요한 경우
→ "실거래 정보 기준: 2026.07.22" 형태로 안내

거래 해제·취소 데이터
→ API 응답에 포함되지 않음
→ 프론트에서 별도 필터링하지 않음
```

---

## 아파트 상세 조회

Method: GET
Progress: 완료
URI: /api/v1/apartments/{apartmentId}
담당자: 최태선
연동여부: Yes

아파트 기본 정보와 대표 이미지 URL, 최근 실거래 요약, 모집 스터디 수, 완료 리포트 수와 내 찜 여부를 조회한다.

선택한 아파트 단지의 상세 정보를 조회한다.

아파트 기본 정보, 최근 실거래 요약, 모집 중인 임장 스터디 수, 완료된 공개 AI 리포트 수, 로그인한 회원의 찜 여부를 반환한다.

아파트 상세 화면에서 모집 중인 스터디, 완료 리포트, AI 챗봇으로 이동하기 위한 기준 정보로 사용한다.

### Request

- HTTP Method: `GET`
- URI: `/api/v1/apartments/{apartmentId}`
- 인증 필요: 필요

#### Request Header

```
Authorization: Bearer {accessToken}
```

#### Path Variable

```
GET /api/v1/apartments/25
```

| 필드 | 타입 | 필수 | 설명 |
| --- | --- | --- | --- |
| `apartmentId` | Long | Y | 조회할 아파트 ID, 1 이상의 값 |

---

### 처리 기준

- 존재하는 아파트를 조회한다.
- 최근 거래는 취소되지 않은 최신 거래를 사용한다.
- 세대당 주차대수는 `parkingSpaceCount / householdCount`로 계산한다.
- 최근 거래·준공년월·주차 정보가 없으면 필드별로 `null`을 반환한다.
- `recruitingStudyCount`는 실제 모집 스터디 목록의 건수와 일치해야 한다.
- `completedReportCount`는 접근 가능한 `DONE` 리포트 목록 건수와 일치해야 한다.
- 기존 `buildingCount`, `approvalDate` 필드는 사용하지 않는다.

### 추가 상세 처리 기준

- 로그인한 회원 ID는 Access Token에서 확인한다.
- 회원 상태가 `ACTIVE`인 경우에만 조회할 수 있다.
- 로그인한 회원을 기준으로 아파트 찜 여부를 계산한다.

---

- 오피스텔, 원룸, 투룸 등 현재 MVP 범위에 포함되지 않은 주거 유형은 조회하지 않는다.
- 삭제되거나 서비스 대상에서 제외된 아파트는 `404 Not Found`를 반환한다.
- 아파트의 위치 정보가 없더라도 상세 정보 자체는 조회할 수 있다.
- 위치 정보가 없는 경우 `latitude`, `longitude`를 `null`로 반환한다.

---

- 공공데이터에서 제공되지 않는 값은 `null`로 반환한다.
- 검증된 대표 이미지가 있고 공개 이미지 base URL이 설정된 경우에만 `imageUrl`을 반환한다.
- 이미지가 없거나 base URL이 설정되지 않으면 `imageUrl = null`을 반환하며 프론트 fallback을 사용한다.
- 숫자형 정보가 없다고 임의로 `0`을 반환하지 않는다.
- 주소는 서비스에서 기준으로 정한 도로명 주소를 우선 사용한다.
- 도로명 주소가 없으면 지번 주소를 사용할 수 있다.
- 아파트 이름과 주소는 공공데이터 원문 기준을 유지한다.

---

- 해당 아파트의 가장 최근 정상 실거래 한 건을 `latestTransaction`으로 반환한다.
- 거래 해제 또는 취소된 실거래는 제외한다.
- 거래 가격은 만 원 단위로 반환한다.
- 실거래일이 같은 거래가 여러 건이면 다음 순서로 하나를 선택한다.

```
1. dealDate DESC
2. collectedAt DESC
3. id DESC
```

- 정상 실거래가 하나도 없으면 `latestTransaction = null`을 반환한다.
- 전체 실거래 목록은 별도 API로 조회한다.

```
GET /api/v1/apartments/{apartmentId}/transactions
```

---

- MVP에서는 복잡한 시세 예측이나 가격 상승률을 계산하지 않는다.
- 최근 거래 데이터가 없는 경우 통계값은 `null` 또는 `0`으로 반환한다.
- 취소·해제 거래는 집계에서 제외한다.

---

- 모집 중인 스터디가 없으면 `0`을 반환한다.
- 신청 대기 인원은 승인 인원에 포함하지 않는다.
- 임장 진행 중·리포트 생성 중·완료 스터디는 모집 중인 수에 포함하지 않는다.
- 전체 모집 스터디 목록은 다음 API로 조회한다.

```
GET /api/v1/apartments/{apartmentId}/studies
```

---

- 해당 아파트를 대상으로 생성된 완료 리포트 수를 `completedReportCount`로 반환한다.
- `report.status = DONE`인 리포트만 포함한다.
- 생성 대기·생성 중·생성 실패 상태는 제외한다.
- 삭제되거나 무효 처리된 리포트는 제외한다.
- 완료된 리포트는 로그인 회원의 임장 참여 여부와 관계없이 공개 목록에서 조회할 수 있다.
- 단, 리포트 근거가 된 사진·메모·음성 변환문 등 원본 기록은 해당 임장 참여자만 조회할 수 있다.
- 완료 리포트 목록은 다음 API로 조회한다.

```
GET /api/v1/apartments/{apartmentId}/reports
```

---

- 로그인 회원이 조회할 수 있는 `DONE` 리포트 문서를 질문별로 먼저 검색한다.
- 현재 질문과 관련된 리포트 근거가 설정 임계값 이상이면 `REPORT` 답변을 생성한다.
- 완료 리포트가 존재하더라도 질문에 필요한 근거가 부족하면 공개 웹 검색으로 전환할 수 있다.
- 웹 검색에서 신뢰할 수 있는 근거를 확보하면 `WEB`, 리포트와 웹 모두 근거가 부족하면 `NONE` 답변을 생성한다.
- 하나의 답변에서 `REPORT` 근거와 `WEB` 근거를 혼합하지 않는다.
- 신뢰할 수 있는 근거가 없으면 정보가 부족하다고 안내한다.
- 서버 설정이나 AI 서비스 장애 등으로 챗봇을 사용할 수 없는 경우다.
- 아파트 상세 화면 자체는 정상적으로 반환한다.

---

- 로그인한 회원과 해당 아파트 사이에 찜 관계가 존재하면 `favoritedByMe = true`를 반환한다.
- 찜 관계가 없으면 `false`를 반환한다.
- 찜 조회 과정에서 새로운 찜 관계를 생성하거나 삭제하지 않는다.
- 찜 등록과 해제는 각각 다음 API를 사용한다.

```
PUT /api/v1/apartments/{apartmentId}/favorite
DELETE /api/v1/apartments/{apartmentId}/favorite
```

---

- 가장 가까운 모집 중인 스터디 일정
- 가장 최근 완료된 완료 리포트
- 모집 중인 스터디가 없으면 `nearestRecruitingStudy = null`을 반환한다.
- 완료 리포트가 없으면 `latestCompletedReport = null`을 반환한다.
- 상세 본문 전체는 포함하지 않고 화면 카드에 필요한 요약만 반환한다.

---

- `recruitingStudyCount`는 실제 아파트별 모집 스터디 목록의 개수와 일치해야 한다.
- `completedReportCount`는 실제 공개 완료 리포트 목록의 개수와 일치해야 한다.
- `favoritedByMe`는 현재 로그인한 회원의 찜 관계와 일치해야 한다.
- 리포트 개수와 챗봇의 `basisType`은 같은 트랜잭션 시점 또는 일관된 조회 시점을 기준으로 계산한다.
- 각 집계값을 별도 반복 조회하지 않고 집계 쿼리 또는 배치 조회를 사용한다.

---

### Response

#### 200 OK

```
{
  "success": true,
  "code": "APARTMENT_DETAIL_SUCCESS",
  "message": "아파트 상세 조회에 성공했습니다.",
  "data": {
    "apartmentId": 15,
    "name": "래미안 옥수 리버젠",
    "address": "서울특별시 성동구 매봉길 15",
    "districtCode": "11200",
    "districtName": "성동구",
    "dongName": "옥수동",
    "latitude": 37.5412,
    "longitude": 127.0178,
    "householdCount": 1511,
    "completionYearMonth": "2012-12",
    "imageUrl": "https://legacy.example.com/apartment-images/v1/A12345678.webp",
    "parkingSpaceCount": 1830,
    "parkingSpacesPerHousehold": 1.21,
    "latestTransaction": {
      "transactionId": 381,
      "price": 183000,
      "priceUnit": "TEN_THOUSAND_KRW",
      "exclusiveArea": 84.95,
      "dealDate": "2026-06-15",
      "floor": 15
    },
    "latestTransactionAvailable": true,
    "recruitingStudyCount": 3,
    "completedReportCount": 5,
    "favoritedByMe": true
  },
  "timestamp": "2026-07-24T17:20:00+09:00"
}
```

### Exception

- `401 AUTH_ACCESS_TOKEN_INVALID`
- `404 APARTMENT_NOT_FOUND`
- `500 COMMON_INTERNAL_SERVER_ERROR`

---

### 추가 Exception 예시

#### 400 Bad Request — 잘못된 아파트 ID

```
{
  "success": false,
  "code": "APARTMENT_ID_INVALID",
  "message": "아파트 ID가 올바르지 않습니다.",
  "data": {
    "field": "apartmentId",
    "reason": "아파트 ID는 1 이상의 숫자여야 합니다."
  },
  "timestamp": "2026-07-22T10:30:00+09:00"
}
```

---

#### 401 Unauthorized

```
{
  "success": false,
  "code": "AUTH_ACCESS_TOKEN_INVALID",
  "message": "로그인이 필요합니다.",
  "data": null,
  "timestamp": "2026-07-22T10:30:00+09:00"
}
```

---

#### 404 Not Found — 회원 없음

```
{
  "success": false,
  "code": "MEMBER_NOT_FOUND",
  "message": "회원 정보를 찾을 수 없습니다.",
  "data": null,
  "timestamp": "2026-07-22T10:30:00+09:00"
}
```

---

#### 404 Not Found — 아파트 없음

```
{
  "success": false,
  "code": "APARTMENT_NOT_FOUND",
  "message": "아파트 정보를 찾을 수 없습니다.",
  "data": {
    "apartmentId": 25
  },
  "timestamp": "2026-07-22T10:30:00+09:00"
}
```

삭제되거나 서비스 대상에서 제외된 아파트도 동일한 응답을 반환한다.

---

#### 500 Internal Server Error

```
{
  "success": false,
  "code": "COMMON_INTERNAL_SERVER_ERROR",
  "message": "일시적인 오류가 발생했습니다.",
  "data": null,
  "timestamp": "2026-07-22T10:30:00+09:00"
}
```

---

### 추가 프론트 처리 기준

```
아파트 목록 또는 지도 마커 선택
→ GET /api/v1/apartments/{apartmentId} 호출
→ 아파트 상세 화면 표시

아파트 상세 상단
→ 단지명
→ 자치구·동
→ 주소
→ 세대수
→ 사용승인일 표시

latestTransaction != null
→ 최근 실거래 가격
→ 전용면적
→ 층수
→ 거래일 표시

latestTransaction = null
→ "최근 실거래 정보가 없습니다." 표시

실거래 전체 보기 선택
→ GET /api/v1/apartments/{apartmentId}/transactions 호출

favoritedByMe = false
→ 빈 찜 아이콘 표시
→ 선택 시 PUT /api/v1/apartments/{apartmentId}/favorite 호출

favoritedByMe = true
→ 채워진 찜 아이콘 표시
→ 선택 시 DELETE /api/v1/apartments/{apartmentId}/favorite 호출

recruitingStudyCount > 0
→ "모집 중인 스터디 N개" 표시
→ 선택 시 GET /api/v1/apartments/{apartmentId}/studies 호출

recruitingStudyCount = 0
→ "현재 모집 중인 스터디가 없습니다." 표시
→ 스터디 생성 버튼 제공 가능

completedReportCount > 0
→ 공개 AI 리포트 개수 표시
→ 선택 시 GET /api/v1/apartments/{apartmentId}/reports 호출

latestCompletedReport != null
→ 최근 리포트 요약 카드 표시
→ 카드 선택 시 GET /api/v1/reports/{reportId} 호출

chatbot.available = true
→ AI 챗봇 버튼 활성화

chatbot.basisType = REPORT
→ "완료된 임장 리포트 기반" 안내 표시

chatbot.basisType = WEB
→ "공개 웹 정보 기반" 안내 표시

chatbot.available = false
→ AI 챗봇 버튼 비활성화
→ "현재 챗봇을 사용할 수 없습니다." 표시
```

---

## 댓글 목록 조회

Method: GET
Progress: 완료
URI: /api/v1/posts/{postId}/comments
담당자: 김윤석
연동여부: Yes

특정 커뮤니티 게시글에 작성된 삭제되지 않은 댓글 목록을 조회한다.

최종 ERD v7의 댓글은 한 단계의 평면 구조이며, 댓글에 대한 별도의 답글 계층은 제공하지 않는다.

---

### Request

- Request HTTP Method: `GET`
- 인증 필요: 필요
- Request Body: 없음

#### Request Header

```
Authorization: Bearer {accessToken}
```

#### Path Variable

```
GET /api/v1/posts/154/comments
```

| 필드 | 타입 | 필수 | 설명 |
| --- | --- | --- | --- |
| `postId` | Long | Y | 댓글 목록을 조회할 게시글 ID |

#### Query Parameter

첫 번째 조회:

```
GET /api/v1/posts/154/comments?size=20
```

다음 조회:

```
GET /api/v1/posts/154/comments?cursor=36&size=20
```

| 필드 | 타입 | 필수 | 기본값 | 설명 |
| --- | --- | --- | --- | --- |
| `cursor` | Long | N | 없음 | 다음 목록 조회에 사용할 마지막 댓글 ID |
| `size` | Integer | N | `20` | 한 번에 조회할 댓글 수, 1~100 |

---

### 처리 기준

#### 1. 회원·게시글 확인

- 로그인한 회원 ID는 Access Token에서 확인한다.
- 회원 상태가 `ACTIVE`인지 확인한다.
- `postId`에 해당하는 게시글이 존재하는지 확인한다.
- 게시글이 `status = ACTIVE`, `deletedAt IS NULL`인 경우에만 댓글을 조회할 수 있다.
- 삭제 또는 운영 숨김 처리된 게시글의 댓글 목록은 반환하지 않는다.
- 댓글 목록 조회만으로 게시글 조회 수를 증가시키지 않는다.

#### 2. 평면 댓글 구조

- 댓글은 `post_comment` 한 단계 구조로 반환한다.
- 별도의 부모 댓글 ID, 중첩 replies 배열, 답글 깊이를 사용하지 않는다.
- UI에서 특정 사용자를 언급하려면 본문에 닉네임을 표시하는 방식은 가능하지만, DB 계층 관계로 저장하지 않는다.
- 기존 대댓글 관련 예외 코드와 계층 페이지네이션은 사용하지 않는다.

#### 3. 조회 대상

다음 조건을 만족하는 댓글만 반환한다.

```
post_id = 요청 postId
deleted_at IS NULL
```

- 삭제된 댓글은 일반 댓글 목록에서 제외한다.
- 계층 구조가 없으므로 삭제 위치를 유지하기 위한 "삭제된 댓글입니다" placeholder가 필요하지 않다.
- 탈퇴 회원이 작성한 댓글을 데이터 정책상 유지하는 경우 작성자 정보를 비식별화한다.
- 댓글이 없으면 오류가 아니라 빈 배열을 반환한다.

#### 4. 정렬

- 댓글은 작성 시각이 오래된 순으로 반환한다.
- 동일 시각이면 댓글 ID가 작은 순으로 정렬한다.

```
createdAt ASC
→ commentId ASC
```

- 커뮤니티 화면에서 최신 댓글부터 표시하기로 변경하려면 API 정렬 정책과 커서를 함께 변경해야 하며, 현재 명세는 기존 흐름을 유지해 오래된 순을 사용한다.

#### 5. 작성자·권한 정보

- 작성자의 회원 ID, 닉네임, 프로필 이미지, 선택 캐릭터를 반환한다.
- 로그인 회원이 댓글 작성자이면 `isMine = true`다.
- 게시글 작성자가 작성한 댓글이면 `isPostAuthor = true`다.
- `isMine = true`이고 게시글·댓글이 활성 상태이면 `canEdit`, `canDelete`를 `true`로 반환한다.
- 게시글 작성자는 다른 회원의 댓글을 일반 API로 수정·삭제할 수 없다.

#### 6. 커서 페이지네이션

- 첫 요청에는 `cursor`를 전달하지 않는다.
- `cursor`가 전달되면 같은 게시글에서 해당 댓글의 작성 시각과 ID를 조회한다.
- 다음 댓글은 `(createdAt, commentId)`가 커서 댓글보다 큰 복합 경계로 조회한다.
- `size + 1`건을 조회해 다음 댓글 존재 여부를 판단한다.
- 응답에는 최대 `size`개를 반환한다.
- 다음 데이터가 있으면 현재 응답의 마지막 댓글 ID를 `nextCursor`로 반환한다.
- 페이지 조회 중 커서 댓글이 삭제되어도 해당 댓글의 작성 시각과 ID를 경계로 계속 조회한다.
- 삭제된 댓글은 목록과 `totalCount`에서는 제외한다.

#### 7. 댓글 수

- `totalCount`는 현재 삭제되지 않은 댓글 전체 수다.
- 게시글 목록·상세의 `commentCount`와 같은 기준을 사용한다.
- 댓글 목록 페이지 크기와 관계없이 전체 개수를 반환한다.

---

### Response

#### 200 OK

```
{
  "success": true,
  "code": "COMMENT_LIST_SUCCESS",
  "message": "댓글 목록 조회에 성공했습니다.",
  "data": {
    "postId": 154,
    "content": [
      {
        "commentId": 36,
        "content": "역 입구뿐 아니라 개찰구까지 걸리는 시간도 확인하면 좋습니다.",
        "author": {
          "memberId": 12,
          "nickname": "옥수탐방러",
          "profileImageUrl": null,
          "selectedCharacterId": "DURI"
        },
        "isMine": false,
        "isPostAuthor": false,
        "canEdit": false,
        "canDelete": false,
        "createdAt": "2026-07-25T16:42:00+09:00",
        "updatedAt": "2026-07-25T16:42:00+09:00"
      },
      {
        "commentId": 37,
        "content": "맞아요. 다음에는 출퇴근 시간대에도 확인해 볼게요.",
        "author": {
          "memberId": 7,
          "nickname": "집보는다람쥐",
          "profileImageUrl": null,
          "selectedCharacterId": "JIPKONG"
        },
        "isMine": true,
        "isPostAuthor": true,
        "canEdit": true,
        "canDelete": true,
        "createdAt": "2026-07-25T16:44:00+09:00",
        "updatedAt": "2026-07-25T16:44:00+09:00"
      }
    ],
    "totalCount": 2,
    "nextCursor": null,
    "hasNext": false
  },
  "timestamp": "2026-07-25T17:10:00+09:00"
}
```

#### 댓글이 없는 경우

```
{
  "success": true,
  "code": "COMMENT_LIST_SUCCESS",
  "message": "댓글 목록 조회에 성공했습니다.",
  "data": {
    "postId": 154,
    "content": [],
    "totalCount": 0,
    "nextCursor": null,
    "hasNext": false
  },
  "timestamp": "2026-07-25T17:10:00+09:00"
}
```

---

### Response Field

| 필드 | 타입 | 설명 |
| --- | --- | --- |
| `postId` | Long | 댓글이 속한 게시글 ID |
| `content` | Array | 평면 댓글 목록 |
| `totalCount` | Long | 삭제되지 않은 전체 댓글 수 |
| `nextCursor` | Long | null | 다음 조회에 사용할 댓글 ID |
| `hasNext` | Boolean | 다음 댓글 존재 여부 |

#### `content[]`

| 필드 | 타입 | 설명 |
| --- | --- | --- |
| `commentId` | Long | 댓글 ID |
| `content` | String | 댓글 본문 |
| `author` | Object | 작성자 공개 정보 |
| `isMine` | Boolean | 현재 로그인 회원이 작성자인지 여부 |
| `isPostAuthor` | Boolean | 게시글 작성자가 작성한 댓글인지 여부 |
| `canEdit` | Boolean | 현재 수정 가능 여부 |
| `canDelete` | Boolean | 현재 삭제 가능 여부 |
| `createdAt` | String | 댓글 작성 시각 |
| `updatedAt` | String | 댓글 최종 수정 시각 |

---

### Exception

#### 400 Bad Request — 게시글 ID 또는 커서 오류

```
{
  "success": false,
  "code": "COMMON_INVALID_REQUEST",
  "message": "입력값을 확인해 주세요.",
  "data": {
    "field": "cursor",
    "reason": "커서는 1 이상의 숫자여야 합니다."
  },
  "timestamp": "2026-07-25T17:10:00+09:00"
}
```

---

#### 400 Bad Request — 조회 개수 오류

```
{
  "success": false,
  "code": "COMMON_INVALID_REQUEST",
  "message": "입력값을 확인해 주세요.",
  "data": {
    "field": "size",
    "reason": "조회 개수는 1 이상 100 이하이어야 합니다."
  },
  "timestamp": "2026-07-25T17:10:00+09:00"
}
```

---

#### 401 Unauthorized

```
{
  "success": false,
  "code": "AUTH_ACCESS_TOKEN_INVALID",
  "message": "로그인이 필요합니다.",
  "data": null,
  "timestamp": "2026-07-25T17:10:00+09:00"
}
```

---

#### 404 Not Found — 게시글 없음·삭제·숨김

```
{
  "success": false,
  "code": "POST_NOT_FOUND",
  "message": "게시글을 찾을 수 없습니다.",
  "data": null,
  "timestamp": "2026-07-25T17:10:00+09:00"
}
```

---

#### 500 Internal Server Error

```
{
  "success": false,
  "code": "COMMON_INTERNAL_SERVER_ERROR",
  "message": "일시적인 오류가 발생했습니다.",
  "data": null,
  "timestamp": "2026-07-25T17:10:00+09:00"
}
```

---

### 프론트 처리

```
게시글 상세 댓글 영역 진입
→ GET /api/v1/posts/{postId}/comments 호출
→ 오래된 댓글부터 표시

isPostAuthor = true
→ 작성자 배지 표시

canEdit = true
→ 수정 메뉴 표시
canDelete = true
→ 삭제 메뉴 표시

목록 하단 도달
→ hasNext = true이면 nextCursor로 다음 목록 조회
→ 기존 댓글 뒤에 추가

content가 빈 배열
→ "아직 작성된 댓글이 없습니다." 표시

POST_NOT_FOUND
→ 원문 게시글 접근 불가 안내
→ 커뮤니티 목록 또는 내 게시물로 이동
```

---

## 아파트 찜 해제

Method: DELETE
Progress: 완료
URI: /api/v1/apartments/{apartmentId}/favorite
담당자: 최태선
연동여부: Yes

로그인한 회원이 찜 목록에 등록한 아파트를 해제한다.

이미 찜이 해제된 아파트에 다시 요청해도 오류로 처리하지 않고 현재 상태를 반환한다.

---

### Request

- Request HTTP Method: `DELETE`
- 인증 필요: 필요
- Request Body: 없음

#### Request Header

```
Authorization: Bearer {accessToken}
```

#### Path Variable

```
DELETE /api/v1/apartments/15/favorite
```

| 필드 | 타입 | 필수 | 설명 |
| --- | --- | --- | --- |
| `apartmentId` | Long | Y | 찜을 해제할 아파트 ID |

---

### 처리 기준

#### 1. 회원 및 아파트 확인

- 로그인한 회원 ID는 Access Token에서 확인한다.
- 회원 상태가 `ACTIVE`인 경우에만 찜을 해제할 수 있다.
- `apartmentId`에 해당하는 아파트가 존재하는지 확인한다.

---

#### 2. 찜 해제

- 회원 ID와 아파트 ID에 해당하는 찜 데이터를 삭제한다.
- 찜 해제 성공 시 `favoritedByMe = false`로 반환한다.
- 해당 아파트는 내 찜한 아파트 목록에서 제외된다.

```
GET /api/v1/members/me/favorite-apartments
```

---

#### 3. 중복 요청

- 이미 찜하지 않은 아파트에 다시 해제 요청해도 오류로 처리하지 않는다.
- 찜 데이터가 없는 경우 추가 삭제를 수행하지 않는다.
- 네트워크 재시도로 동일한 요청이 반복돼도 찜 수는 중복 감소하지 않는다.

---

### Response

#### 200 OK

```json
{
  "success": true,
  "code": "APARTMENT_UNFAVORITE_SUCCESS",
  "message": "아파트 찜을 해제했습니다.",
  "data": {
    "apartmentId": 15,
    "favoritedByMe": false,
    "favoriteCount": 127
  },
  "timestamp": "2026-07-22T18:35:00+09:00"
}
```

---

### Response Field

| 필드 | 타입 | 설명 |
| --- | --- | --- |
| `apartmentId` | Long | 찜을 해제한 아파트 ID |
| `favoritedByMe` | Boolean | 현재 사용자의 찜 여부 |
| `favoriteCount` | Integer | 해당 아파트를 찜한 전체 회원 수 |

---

### 이미 찜이 해제된 경우

```json
{
  "success": true,
  "code": "APARTMENT_FAVORITE_NOT_FOUND",
  "message": "이미 찜이 해제된 아파트입니다.",
  "data": {
    "apartmentId": 15,
    "favoritedByMe": false,
    "favoriteCount": 127
  },
  "timestamp": "2026-07-22T18:35:00+09:00"
}
```

이미 찜하지 않은 경우에도 HTTP 상태 코드는 `200 OK`로 반환한다.

---

### Exception

#### 400 Bad Request — 잘못된 아파트 ID

```json
{
  "success": false,
  "code": "COMMON_INVALID_REQUEST",
  "message": "입력값을 확인해 주세요.",
  "data": {
    "field": "apartmentId",
    "reason": "아파트 ID는 1 이상의 숫자여야 합니다."
  },
  "timestamp": "2026-07-22T18:35:00+09:00"
}
```

---

#### 401 Unauthorized

```json
{
  "success": false,
  "code": "AUTH_ACCESS_TOKEN_INVALID",
  "message": "로그인이 필요합니다.",
  "data": null,
  "timestamp": "2026-07-22T18:35:00+09:00"
}
```

---

#### 404 Not Found

```json
{
  "success": false,
  "code": "APARTMENT_NOT_FOUND",
  "message": "아파트 정보를 찾을 수 없습니다.",
  "data": null,
  "timestamp": "2026-07-22T18:35:00+09:00"
}
```

---

#### 500 Internal Server Error

```json
{
  "success": false,
  "code": "COMMON_INTERNAL_SERVER_ERROR",
  "message": "일시적인 오류가 발생했습니다.",
  "data": null,
  "timestamp": "2026-07-22T18:35:00+09:00"
}
```

---

### 프론트 처리

```
활성화된 찜 버튼 선택
→ DELETE /api/v1/apartments/{apartmentId}/favorite 호출
→ favoritedByMe를 false로 변경
→ 응답의 favoriteCount로 찜 수 갱신

APARTMENT_FAVORITE_NOT_FOUND 응답
→ 찜 비활성 상태 유지
→ 응답의 favoriteCount로 현재 값 동기화

내 찜 목록에서 해제한 경우
→ 해당 아파트를 목록에서 제거

APARTMENT_NOT_FOUND 발생
→ "조회할 수 없는 아파트입니다." 안내
→ 이전 화면으로 이동

찜 해제 요청 실패
→ 기존 찜 상태 유지
→ 잠시 후 다시 시도하도록 안내
```

---

## 아파트 찜

Method: PUT
Progress: 완료
URI: /api/v1/apartments/{apartmentId}/favorite
담당자: 최태선
연동여부: No

로그인한 회원이 관심 있는 아파트를 찜 목록에 추가한다.

이미 찜한 아파트에 다시 요청해도 중복 데이터는 생성하지 않는다.

---

### Request

- Request HTTP Method: `PUT`
- 인증 필요: 필요
- Request Body: 없음

#### Request Header

```
Authorization: Bearer {accessToken}
```

#### Path Variable

```
PUT /api/v1/apartments/15/favorite
```

| 필드 | 타입 | 필수 | 설명 |
| --- | --- | --- | --- |
| `apartmentId` | Long | Y | 찜할 아파트 ID |

---

### 처리 기준

#### 1. 회원 및 아파트 확인

- 로그인한 회원 ID는 Access Token에서 확인한다.
- 회원 상태가 `ACTIVE`인 경우에만 아파트를 찜할 수 있다.
- `apartmentId`에 해당하는 아파트가 존재하는지 확인한다.
- 서비스에서 조회 가능한 아파트만 찜할 수 있다.

---

#### 2. 찜 등록

- 회원 ID와 아파트 ID를 기준으로 찜 정보를 저장한다.
- 한 회원은 동일한 아파트를 한 번만 찜할 수 있다.
- 찜 등록 성공 시 `favoritedByMe = true`로 반환한다.
- 등록된 아파트는 내 찜한 아파트 목록에서 조회할 수 있다.

```
GET /api/v1/members/me/favorite-apartments
```

---

#### 3. 중복 요청

- 이미 찜한 아파트에 다시 요청해도 새로운 찜 데이터는 생성하지 않는다.
- 중복 요청은 오류로 처리하지 않고 현재 찜 상태를 반환한다.
- 네트워크 재시도로 요청이 반복되어도 중복 저장하지 않는다.

---

### Response

#### 200 OK

```json
{
  "success": true,
  "code": "APARTMENT_FAVORITE_SUCCESS",
  "message": "아파트를 찜했습니다.",
  "data": {
    "apartmentId": 15,
    "favoritedByMe": true,
    "favoriteCount": 128
  },
  "timestamp": "2026-07-22T18:30:00+09:00"
}
```

---

### Response Field

| 필드 | 타입 | 설명 |
| --- | --- | --- |
| `apartmentId` | Long | 찜한 아파트 ID |
| `favoritedByMe` | Boolean | 현재 사용자의 찜 여부 |
| `favoriteCount` | Integer | 해당 아파트를 찜한 전체 회원 수 |

---

### 이미 찜한 경우

```json
{
  "success": true,
  "code": "APARTMENT_FAVORITE_ALREADY_EXISTS",
  "message": "이미 찜한 아파트입니다.",
  "data": {
    "apartmentId": 15,
    "favoritedByMe": true,
    "favoriteCount": 128
  },
  "timestamp": "2026-07-22T18:30:00+09:00"
}
```

이미 찜한 경우에도 HTTP 상태 코드는 `200 OK`로 반환한다.

---

### Exception

#### 400 Bad Request — 잘못된 아파트 ID

```json
{
  "success": false,
  "code": "COMMON_INVALID_REQUEST",
  "message": "입력값을 확인해 주세요.",
  "data": {
    "field": "apartmentId",
    "reason": "아파트 ID는 1 이상의 숫자여야 합니다."
  },
  "timestamp": "2026-07-22T18:30:00+09:00"
}
```

---

#### 401 Unauthorized

```json
{
  "success": false,
  "code": "AUTH_ACCESS_TOKEN_INVALID",
  "message": "로그인이 필요합니다.",
  "data": null,
  "timestamp": "2026-07-22T18:30:00+09:00"
}
```

---

#### 404 Not Found

```json
{
  "success": false,
  "code": "APARTMENT_NOT_FOUND",
  "message": "아파트 정보를 찾을 수 없습니다.",
  "data": null,
  "timestamp": "2026-07-22T18:30:00+09:00"
}
```

---

#### 500 Internal Server Error

```json
{
  "success": false,
  "code": "COMMON_INTERNAL_SERVER_ERROR",
  "message": "일시적인 오류가 발생했습니다.",
  "data": null,
  "timestamp": "2026-07-22T18:30:00+09:00"
}
```

---

### 프론트 처리

```
아파트 상세 또는 목록에서 찜 버튼 선택
→ PUT /api/v1/apartments/{apartmentId}/favorite 호출
→ favoritedByMe를 true로 변경
→ 응답의 favoriteCount로 찜 수 갱신

APARTMENT_FAVORITE_ALREADY_EXISTS 응답
→ 찜 활성 상태 유지
→ 응답의 favoriteCount로 현재 값 동기화

APARTMENT_NOT_FOUND 발생
→ "조회할 수 없는 아파트입니다." 안내
→ 이전 화면으로 이동

찜 요청 실패
→ 기존 찜 상태 유지
→ 잠시 후 다시 시도하도록 안내
```

---

## 아파트 챗봇 질문 전송

Method: POST
Progress: 완료
URI: /api/v1/apartments/{apartmentId}/chatbot/conversations/{conversationId}/messages
담당자: 윤다인
연동여부: No

로그인한 회원이 특정 아파트의 챗봇 대화에 질문을 전송한다.

사용자 질문과 AI 답변 placeholder를 저장한 뒤 비동기 답변 생성을 요청한다. 실제 답변은 질문과 관련된 리포트 근거를 먼저 검색하고, 근거가 부족하면 웹 검색으로 전환해 `REPORT`, `WEB`, `NONE` 중 하나로 결정한다.

---

### Request

- Request HTTP Method: `POST`
- 인증 필요: 필요
- Content-Type: `application/json`

#### Request Header

```
Authorization: Bearer {accessToken}
```

#### Path Variable

```
POST /api/v1/apartments/15/chatbot/conversations/41/messages
```

| 필드 | 타입 | 필수 | 설명 |
| --- | --- | --- | --- |
| `apartmentId` | Long | Y | 질문 대상 아파트 ID |
| `conversationId` | Long | Y | 질문을 전송할 챗봇 대화 ID |

#### Request Body

```
{
  "content": "교통 어때요?"
}
```

| 필드 | 타입 | 필수 | 설명 |
| --- | --- | --- | --- |
| `content` | String | Y | 사용자 질문, 앞뒤 공백 제거 후 1~1000자 |

---

### 처리 기준

#### 1. 회원·아파트·대화 검증

- 로그인한 회원 ID는 Access Token에서 확인한다.
- 회원 상태가 `ACTIVE`인지 확인한다.
- `apartmentId`에 해당하는 아파트가 존재하는지 확인한다.
- `conversationId`에 해당하는 대화가 존재하는지 확인한다.
- 대화의 회원 ID가 로그인 회원과 일치해야 한다.
- 대화의 아파트 ID가 요청 URI의 `apartmentId`와 일치해야 한다.
- 다른 회원의 대화 또는 다른 아파트 대화에는 질문을 전송할 수 없다.
- 최종 ERD에는 대화 종료 상태가 없으므로 `CHATBOT_CONVERSATION_CLOSED` 정책을 사용하지 않는다.

#### 2. 질문 입력값 검증

- 질문 앞뒤 공백을 제거한다.
- 공백만 입력한 질문은 전송할 수 없다.
- 최대 1000자를 초과하면 오류를 반환한다.
- HTML·스크립트 문자열은 일반 텍스트로 처리하고 출력 시 이스케이프한다.
- 프롬프트 인젝션으로 시스템 지침이나 다른 회원 데이터에 접근하려는 요청은 안전 정책에 따라 거절할 수 있다.

#### 3. 동시 답변 제한

- 동일 대화에 `PENDING` 또는 `PROCESSING`인 AI 답변이 있으면 새 질문을 받지 않는다.
- 한 대화에서 동시에 여러 답변이 생성되어 메시지 순서가 뒤섞이는 것을 방지한다.
- 기존 답변이 `COMPLETED` 또는 `FAILED`가 된 뒤 다음 질문을 전송할 수 있다.
- 답변 생성 중 중복 전송된 질문은 새 사용자 메시지로 저장하지 않는다.

#### 4. 사용자 질문 저장

- 사용자 메시지를 다음 값으로 저장한다.

```
role = USER
status = COMPLETED
content = 정제된 질문
basisType = NONE
completedAt = createdAt
```

- 사용자 질문 저장 후 `chatbot_conversation.last_message_at`을 갱신한다.
- 사용자 메시지와 AI placeholder 생성은 하나의 트랜잭션으로 처리한다.

#### 5. AI 답변 placeholder 생성

- 사용자 질문과 같은 트랜잭션에서 AI 답변 행을 생성한다.

```
role = ASSISTANT
status = PENDING 또는 PROCESSING
content = null
failReason = null
completedAt = null
```

- `content`는 AI 답변이 `PENDING` 또는 `PROCESSING`인 동안만 `null`을 허용한다.
- 생성된 AI 메시지 ID를 응답해 프론트가 동일 버블을 갱신할 수 있게 한다.

#### 6. 근거 모드 결정

질문 처리 시점에 해당 아파트의 조회 가능한 완료 리포트 문서를 먼저 검색하고, 현재 질문과의 관련도를 평가한다.

##### 질문에 활용할 수 있는 리포트 근거가 있는 경우

```
basisType = REPORT
basisLabel = 리포트 기반
```

- 해당 리포트의 `resultJson`과 접근 가능한 요약 문맥을 사용한다.
- 가장 관련도 높은 리포트 근거가 설정 임계값 이상일 때만 리포트 기반 답변을 생성한다.
- 실제 사용한 리포트를 `sources`에 기록한다.

##### 질문에 활용할 수 있는 리포트 근거가 부족한 경우

```
basisType = WEB
basisLabel = 웹 기반
```

- 완료 리포트가 없거나, 리포트가 있어도 질문 관련 근거가 없거나 유사도가 임계값 미만이면 웹 검색으로 전환한다.
- 예를 들어 리포트에 병원 거리 정보가 없으면 해당 아파트와 질문을 조합해 공개 웹 정보를 검색할 수 있다.
- 아파트 기본 정보, 최근 실거래, 공개 모집 스터디, 웹·공공데이터를 사용할 수 있다.
- 실제 조회한 출처만 `sources`에 기록한다.
- 시세·교통·개발 정보는 기준 시점과 불확실성을 함께 설명한다.
- 하나의 답변에 리포트와 웹 출처를 함께 사용하지 않는다.

##### 신뢰할 근거가 없는 경우

```
basisType = NONE
```

- 확인 가능한 정보가 부족하다는 답변을 생성한다.
- 추정 내용을 사실처럼 단정하지 않는다.

#### 7. 스터디 추천 질문

- "스터디 추천" 질문에는 해당 아파트의 `RECRUITING` 스터디를 조회한다.
- 제목, 목표, 현재 인원, 정원, 다음 일정을 공개 가능한 범위에서 사용할 수 있다.
- 신청자 목록, 비공개 공지, 채팅 내용은 답변 근거로 사용하지 않는다.
- 모집 중 스터디가 없으면 없다고 명확히 안내한다.

#### 8. 비동기 작업 처리

- 사용자 질문과 AI placeholder 저장 후 Kafka 또는 AI 서비스 호출을 비동기로 수행한다.
- 작업 시작 시 AI 메시지를 `PROCESSING`으로 변경한다.
- 성공 시 `content`, `basisType`, `basisLabel`, `sourcesJson`, `completedAt`을 저장하고 `status = COMPLETED`로 변경한다.
- 실패 시 `status = FAILED`, 정제된 `failReason`, `completedAt`을 저장한다.
- 성공·실패 갱신 후 대화의 `lastMessageAt`을 AI 메시지 완료 시각으로 갱신할 수 있다.

#### 8-1. AI 서비스 구성 (2026-08-01 변경)

- 백엔드는 `POST /internal/v1/chatbot/answers`(FastAPI)를 HTTP로 호출한다.
- 근거 검색용 임베딩 모델을 **SSAFY GPU에서 EC2 CPU로 옮겼다.**

```
변경 전: Qwen/Qwen3-Embedding-4B  (2560차원, cuda:0, SSAFY GPU 서버)
변경 후: intfloat/multilingual-e5-base (768차원, cpu, EC2 ai 컨테이너)
```

- `apartment_rag_document.embedding`이 `vector(2560)` → `vector(768)`로 바뀐다(Flyway `V15`).
  기존 색인은 전량 삭제 후 재색인한다.
- e5 계열은 접두어가 필수다 — 문서는 `passage: `, 질문은 `query: `.
- GPU inbound 차단이 사라져 **Kafka 구독 방식은 채택하지 않는다.** HTTP가 최종 구조다.

#### 9. 출처 정확성

- `sources`의 제목·ID·URL은 실제 답변 생성에 사용한 자료와 일치해야 한다.
- 리포트 기반 답변에 웹 출처를 함께 넣어 근거 모드를 혼합하지 않는다.
- 웹 기반 답변에 존재하지 않는 URL이나 임의 제목을 생성하지 않는다.
- 참여자 원문 근거는 리포트 원문 접근 권한을 우회해 노출하지 않는다.

#### 10. 실패와 재질문

- 별도의 챗봇 답변 재시도 API는 현재 목록에 없다.
- `FAILED` 답변은 이력에 유지한다.
- 사용자가 "다시 질문"을 선택하면 동일한 질문 내용을 새 POST 요청으로 전송한다.
- 이전 실패 메시지를 수정하거나 덮어쓰지 않는다.

---

### Response

#### 202 Accepted — 질문 접수

```
{
  "success": true,
  "code": "CHATBOT_MESSAGE_ACCEPTED",
  "message": "질문이 전송되었습니다.",
  "data": {
    "conversationId": 41,
    "basisPolicy": {
      "basisType": "REPORT",
      "basisLabel": "리포트 기반",
      "reportId": 48
    },
    "userMessage": {
      "messageId": 53,
      "role": "USER",
      "content": "교통 어때요?",
      "status": "COMPLETED",
      "basisType": "NONE",
      "basisLabel": null,
      "sources": [],
      "createdAt": "2026-07-25T16:10:00+09:00",
      "completedAt": "2026-07-25T16:10:00+09:00"
    },
    "assistantMessage": {
      "messageId": 54,
      "role": "ASSISTANT",
      "content": null,
      "status": "PENDING",
      "basisType": "REPORT",
      "basisLabel": "리포트 기반",
      "sources": [],
      "failReason": null,
      "createdAt": "2026-07-25T16:10:00+09:00",
      "completedAt": null
    },
    "polling": {
      "messageHistoryApi": "/api/v1/apartments/15/chatbot/conversations/41/messages",
      "recommendedIntervalMs": 2500
    }
  },
  "timestamp": "2026-07-25T16:10:00+09:00"
}
```

#### 완료 후 이력 조회 예시 — 리포트 기반

```
{
  "messageId": 54,
  "role": "ASSISTANT",
  "content": "임장 리포트에서는 옥수역 개찰구까지 실제 도보 약 8분이 걸렸다는 기록이 반복적으로 확인됐습니다. 대중교통 접근성은 긍정적이지만 출퇴근 시간대 차량 흐름은 추가 확인이 필요합니다.",
  "status": "COMPLETED",
  "basisType": "REPORT",
  "basisLabel": "리포트 기반",
  "sources": [
    {
      "sourceType": "REPORT",
      "sourceId": 48,
      "title": "래미안 옥수 리버젠 임장 리포트",
      "sectionLabel": "교통",
      "url": null
    }
  ],
  "failReason": null,
  "createdAt": "2026-07-25T16:10:00+09:00",
  "completedAt": "2026-07-25T16:10:04+09:00"
}
```

#### 완료 후 이력 조회 예시 — 웹 기반

```
{
  "messageId": 60,
  "role": "ASSISTANT",
  "content": "최근 공개된 실거래 자료에서는 전용 84㎡ 거래가 확인됩니다. 거래 시점과 층에 따라 가격 차이가 있으므로 상세 거래 목록을 함께 확인해 주세요.",
  "status": "COMPLETED",
  "basisType": "WEB",
  "basisLabel": "웹 기반",
  "sources": [
    {
      "sourceType": "APARTMENT_TRANSACTION",
      "sourceId": 325,
      "title": "최근 아파트 실거래 내역",
      "observedAt": "2026-07-24T03:00:00+09:00",
      "url": null
    },
    {
      "sourceType": "WEB_PAGE",
      "sourceId": null,
      "title": "국토교통부 실거래가 공개 자료",
      "observedAt": "2026-07-25T16:12:00+09:00",
      "url": "https://example.com/source/transaction"
    }
  ],
  "failReason": null,
  "createdAt": "2026-07-25T16:12:00+09:00",
  "completedAt": "2026-07-25T16:12:06+09:00"
}
```

---

### Response Field

| 필드 | 타입 | 설명 |
| --- | --- | --- |
| `conversationId` | Long | 챗봇 대화 ID |
| `basisPolicy` | Object | 답변 생성 전 우선 탐색할 근거 모드. 최종 AI 답변의 `basisType`은 질문 관련도와 실제 사용 출처에 따라 달라질 수 있음 |
| `userMessage` | Object | 저장된 사용자 질문 |
| `assistantMessage` | Object | 생성 대기 중인 AI 답변 |
| `polling` | Object | 결과 확인 API와 권장 조회 간격 |

#### 메시지 공통 필드

| 필드 | 타입 | 설명 |
| --- | --- | --- |
| `messageId` | Long | 메시지 ID |
| `role` | String | `USER`, `ASSISTANT` |
| `content` | String | null | 질문 또는 답변 내용 |
| `status` | String | 처리 상태 |
| `basisType` | String | `REPORT`, `WEB`, `NONE` |
| `basisLabel` | String | null | 화면 표시용 근거 라벨 |
| `sources` | Array | 실제 사용 출처 |
| `failReason` | String | null | 실패 사유 |
| `createdAt` | String | 메시지 생성 시각 |
| `completedAt` | String | null | 처리 완료 시각 |

---

### Exception

#### 400 Bad Request — 질문 내용 오류

```
{
  "success": false,
  "code": "COMMON_INVALID_REQUEST",
  "message": "입력값을 확인해 주세요.",
  "data": {
    "field": "content",
    "reason": "질문은 1자 이상 1000자 이하이어야 합니다."
  },
  "timestamp": "2026-07-25T16:10:00+09:00"
}
```

---

#### 400 Bad Request — 아파트 불일치

```
{
  "success": false,
  "code": "CHATBOT_APARTMENT_MISMATCH",
  "message": "해당 아파트의 챗봇 대화가 아닙니다.",
  "data": null,
  "timestamp": "2026-07-25T16:10:00+09:00"
}
```

---

#### 401 Unauthorized

```
{
  "success": false,
  "code": "AUTH_ACCESS_TOKEN_INVALID",
  "message": "로그인이 필요합니다.",
  "data": null,
  "timestamp": "2026-07-25T16:10:00+09:00"
}
```

---

#### 403 Forbidden — 대화 접근 권한 없음

```
{
  "success": false,
  "code": "CHATBOT_CONVERSATION_ACCESS_DENIED",
  "message": "해당 챗봇 대화에 접근할 권한이 없습니다.",
  "data": null,
  "timestamp": "2026-07-25T16:10:00+09:00"
}
```

---

#### 404 Not Found — 아파트 없음

```
{
  "success": false,
  "code": "APARTMENT_NOT_FOUND",
  "message": "아파트 정보를 찾을 수 없습니다.",
  "data": null,
  "timestamp": "2026-07-25T16:10:00+09:00"
}
```

---

#### 404 Not Found — 대화 없음

```
{
  "success": false,
  "code": "CHATBOT_CONVERSATION_NOT_FOUND",
  "message": "챗봇 대화를 찾을 수 없습니다.",
  "data": null,
  "timestamp": "2026-07-25T16:10:00+09:00"
}
```

---

#### 409 Conflict — 이전 답변 생성 중

```
{
  "success": false,
  "code": "CHATBOT_RESPONSE_IN_PROGRESS",
  "message": "이전 질문에 대한 답변을 생성하고 있습니다.",
  "data": {
    "assistantMessageId": 54,
    "status": "PROCESSING"
  },
  "timestamp": "2026-07-25T16:10:00+09:00"
}
```

---

#### 429 Too Many Requests — 사용량 제한

```
{
  "success": false,
  "code": "CHATBOT_RATE_LIMIT_EXCEEDED",
  "message": "질문 요청이 많습니다. 잠시 후 다시 시도해 주세요.",
  "data": {
    "retryAfterSeconds": 30
  },
  "timestamp": "2026-07-25T16:10:00+09:00"
}
```

---

#### 503 Service Unavailable — AI 서비스 장애

질문과 placeholder를 저장하기 전 AI 요청 접수 자체가 불가능한 경우

```
{
  "success": false,
  "code": "CHATBOT_SERVICE_UNAVAILABLE",
  "message": "AI 챗봇을 일시적으로 사용할 수 없습니다.",
  "data": null,
  "timestamp": "2026-07-25T16:10:00+09:00"
}
```

---

#### 500 Internal Server Error

```
{
  "success": false,
  "code": "COMMON_INTERNAL_SERVER_ERROR",
  "message": "일시적인 오류가 발생했습니다.",
  "data": null,
  "timestamp": "2026-07-25T16:10:00+09:00"
}
```

---

### 프론트 처리

```
질문 입력 또는 추천 질문 선택
→ POST /api/v1/apartments/{apartmentId}/chatbot/conversations/{conversationId}/messages 호출
→ userMessage 즉시 표시
→ assistantMessage.messageId로 로딩 버블 생성

202 Accepted
→ 입력창 비활성화
→ polling.messageHistoryApi를 권장 간격으로 조회

동일 assistantMessage.messageId status = COMPLETED
→ 로딩 버블을 실제 답변으로 교체
→ basisLabel과 sources 표시
→ 입력창 다시 활성화

status = FAILED
→ failReason 표시
→ 입력창 활성화
→ 사용자가 동일 질문을 새로 전송할 수 있게 함

CHATBOT_RESPONSE_IN_PROGRESS
→ 새로운 사용자 질문 버블을 추가하지 않음
→ 기존 처리 중 답변으로 스크롤

basisType = REPORT
→ "리포트 기반" 배지 표시

basisType = WEB
→ "웹 기반" 배지와 실제 웹 출처 표시

basisType = NONE
→ 신뢰할 수 있는 근거를 찾지 못했다는 안내 표시
```

---

## 내부 Report Worker API 공통 정책

AI-007 Report Worker와 Backend 사이의 API는 프론트엔드용 `/api/v1/**`와
분리된 `/internal/v1/reports/**` 경로를 사용한다.

- 인증은 `Authorization: Bearer {REPORT_INTERNAL_TOKEN}`만 허용한다.
- 사용자 Access Token은 내부 API 인증으로 사용할 수 없다.
- Spring Security는 `/internal/v1/reports/**` 전용 체인을 사용자 JWT 체인보다 먼저 적용한다.
- 내부 Token은 환경변수로만 주입하며 Query Parameter, 코드, 로그에 남기지 않는다.
- `processingToken`, Authorization Header, 요청·응답 전문, TEXT·STT 원문을 로그에 남기지 않는다.
- 조회형 성공 응답은 공통 5필드 `ApiResponse<T>`를 사용한다.
- 후속 `progress`, `complete`, `fail` 성공 응답은 body 없는 정확한 `204 No Content`를 사용한다.
- 하나의 `studyId`에는 하나의 Report만 존재한다.
- 처리권은 Backend가 관리하는 Lease이며 기본 TTL은 30분이다.
- Worker의 단일 처리 최대 예산이 30분보다 커지면 Backend Lease TTL도 함께 늘려야 한다.
- 원문 `processingToken`은 `ACQUIRED` 응답에서 한 번만 전달하고 DB에는 SHA-256 해시만 저장한다.
- `FAILED` Report는 Kafka 중복 이벤트만으로 자동 재시작하지 않는다. 공개 retry API가 먼저
  `FAILED -> PENDING`으로 전이한 뒤 발행한 새 이벤트만 다시 처리권을 획득한다.
- `publicId`, `visibility`는 재도입하지 않으며 모든 사용자 Report API는 기존 인증 정책을 유지한다.

후속 내부 API는 아래 순서로 별도 구현한다.

| Method | URI | 목적 | 상태 |
| --- | --- | --- | --- |
| `PATCH` | `/internal/v1/reports/{reportId}/progress` | 진행 단계 저장·Lease 갱신 | 완료 |
| `GET` | `/internal/v1/reports/{reportId}/input` | AI-004 정규화 입력 원본 조회 | 완료 |
| `PUT` | `/internal/v1/reports/{reportId}/complete` | AI 결과·근거 원자적 완료 저장 | 완료 |
| `PUT` | `/internal/v1/reports/{reportId}/fail` | 안전한 실패 정보 멱등 저장 | 완료 |

---

## AI 리포트 처리권 획득

Domain: Internal Report
Method: POST
Progress: 완료
URI: /internal/v1/reports/acquire
담당자: 박재명
연동여부: AI-007 HTTP Adapter 계약 연동 (실제 Worker E2E 미검증)

AI Worker가 스터디당 하나의 Report를 원자적으로 생성하거나 기존 Report를 조회하고,
Backend가 관리하는 Lease 기반 처리권을 획득한다. 프론트엔드가 호출하는 공개 API가 아니다.

---

### Request

- Request HTTP Method: `POST`
- 인증 필요: 내부 서비스 인증 필요
- Content-Type: `application/json`

#### Request Header

```http
Authorization: Bearer {REPORT_INTERNAL_TOKEN}
```

#### Request Body

```json
{
  "studyId": 7,
  "sessionId": 3,
  "apartmentId": 100,
  "occurredAt": "2026-08-02T12:30:00+09:00"
}
```

| 필드 | 타입 | 필수 | 설명 |
| --- | --- | --- | --- |
| `studyId` | Long | Y | Report 멱등 키가 되는 스터디 ID, 1 이상 |
| `sessionId` | Long | Y | 종료된 임장 세션 ID, 1 이상 |
| `apartmentId` | Long | Y | 해당 스터디의 아파트 ID, 1 이상 |
| `occurredAt` | String | Y | Offset이 포함된 ISO-8601 이벤트 발생 시각 |

`occurredAt`은 이벤트 메타데이터 형식을 검증하되 멱등 키나 계약 충돌 판정에는 사용하지 않는다.
임장 종료 시각의 권위 값은 DB의 `field_session.ended_at`이다.

---

### 처리 기준

#### 1. 원본 관계 검증과 동시성

- `studyId`에 속한 `field_session` 행을 비관적 쓰기 잠금으로 조회한다.
- `sessionId`가 해당 스터디의 실제 세션인지, 세션 상태가 `ENDED`인지 검증한다.
- 삭제·취소되지 않은 스터디인지, `apartmentId`가 스터디의 실제 아파트인지 검증한다.
- `report.study_id UNIQUE`, `field_session` 행 잠금, 기존 `report` 행 잠금을 함께 사용해
  최초 생성과 Lease 재선점 경쟁을 직렬화한다.
- `report.field_session_id`는 `NOT NULL + UNIQUE + FK`로 고정한다. 연결할 권위 세션이 없는
  기존 Report는 임의 세션으로 대체하지 않고 마이그레이션을 중단해 데이터 정리를 요구한다.
- 동일 `studyId` 이벤트의 `sessionId` 또는 `apartmentId`가 기존 Report와 다르면
  HTTP 오류 대신 `200 + CONTRACT_CONFLICT`를 반환한다.

#### 2. 상태별 처리권 판정

| Report 상태·Lease | 결과 | DB 변경 |
| --- | --- | --- |
| Report 없음 | `ACQUIRED` | Report 생성, `IN_PROGRESS`, attempt 1, 새 Lease |
| `PENDING` | `ACQUIRED` | `IN_PROGRESS`, attempt 증가, 새 Lease |
| `IN_PROGRESS` + Lease 유효 | `ALREADY_PROCESSING` | 없음 |
| `IN_PROGRESS` + Lease 만료 | `ACQUIRED` | attempt 증가, 새 Token·Lease |
| `DONE` | `ALREADY_COMPLETED` | 없음 |
| `FAILED` | `ALREADY_FAILED` | 없음, 자동 재시작 금지 |

`ACQUIRED`가 되면 `progressStage=RECORD_COLLECTION`을 저장한다.
Lease가 만료되어 재선점되면 이전 Token은 즉시 무효가 되며 후속 API에서 `409`로 거부한다.

#### 3. 진행 단계

Backend와 AI Worker는 아래 고정 단계를 사용한다.

| 단계 | 설명 | 상태 조회 권장 진행률 |
| --- | --- | ---: |
| `RECORD_COLLECTION` | 임장 원본 수집 | 20 |
| `STT_VALIDATION` | STT 완료·유효성 확인 | 40 |
| `NORMALIZATION` | 참여자별 입력 정규화 | 55 |
| `REPORT_GENERATION` | 스토리형 리포트 생성 | 70 |
| `EVIDENCE_MAPPING` | AI 문장과 TEXT·STT 근거 연결 | 90 |
| `RESULT_SAVING` | 결과·근거 원자적 저장 | 95 |
| `COMPLETED` | 저장 완료 | 100 |

---

### Response

#### 200 OK — 처리권 획득

```json
{
  "success": true,
  "code": "REPORT_ACQUIRE_SUCCESS",
  "message": "리포트 처리권을 확인했습니다.",
  "data": {
    "status": "ACQUIRED",
    "reportId": 48,
    "processingToken": "opaque-one-time-token",
    "processingAttempt": 1,
    "leaseExpiresAt": "2026-08-02T13:00:00+09:00",
    "retryAfterSeconds": null
  },
  "timestamp": "2026-08-02T12:30:00+09:00"
}
```

#### 200 OK — 다른 Worker가 처리 중

```json
{
  "success": true,
  "code": "REPORT_ACQUIRE_SUCCESS",
  "message": "리포트 처리권을 확인했습니다.",
  "data": {
    "status": "ALREADY_PROCESSING",
    "reportId": 48,
    "processingToken": null,
    "processingAttempt": null,
    "leaseExpiresAt": null,
    "retryAfterSeconds": 120
  },
  "timestamp": "2026-08-02T12:58:00+09:00"
}
```

#### 200 OK — 계약 충돌

```json
{
  "success": true,
  "code": "REPORT_ACQUIRE_SUCCESS",
  "message": "리포트 처리권을 확인했습니다.",
  "data": {
    "status": "CONTRACT_CONFLICT",
    "reportId": 48,
    "processingToken": null,
    "processingAttempt": null,
    "leaseExpiresAt": null,
    "retryAfterSeconds": null
  },
  "timestamp": "2026-08-02T12:30:00+09:00"
}
```

`ALREADY_COMPLETED`와 `ALREADY_FAILED`도 `reportId`만 반환하고 처리권 관련 필드는 null이다.

#### Response Field

| 필드 | 타입 | 설명 |
| --- | --- | --- |
| `status` | String | `ACQUIRED`, `ALREADY_COMPLETED`, `ALREADY_PROCESSING`, `ALREADY_FAILED`, `CONTRACT_CONFLICT` |
| `reportId` | Long/null | 기존 또는 생성된 Report ID. 관계 확인 전 충돌이면 null 가능 |
| `processingToken` | String/null | `ACQUIRED`일 때만 반환하는 일회성 불투명 Token |
| `processingAttempt` | Integer/null | `ACQUIRED`일 때 1 이상인 처리 시도 번호 |
| `leaseExpiresAt` | String/null | `ACQUIRED` 처리권의 관측용 만료 시각 |
| `retryAfterSeconds` | Long/null | `ALREADY_PROCESSING`일 때 재시도 대기 힌트 |

---

### Exception

#### 400 Bad Request — 요청값 검증 실패

```json
{
  "success": false,
  "code": "COMMON_INVALID_REQUEST",
  "message": "입력값을 확인해 주세요.",
  "data": {
    "field": "studyId",
    "reason": "1 이상의 값이어야 합니다."
  },
  "timestamp": "2026-08-02T12:30:00+09:00"
}
```

#### 401 Unauthorized — 내부 Token 누락·불일치

```json
{
  "success": false,
  "code": "REPORT_INTERNAL_AUTHENTICATION_FAILED",
  "message": "내부 API 인증이 필요합니다.",
  "data": null,
  "timestamp": "2026-08-02T12:30:00+09:00"
}
```

#### 403 Forbidden — 내부 API 접근 거부

```json
{
  "success": false,
  "code": "REPORT_INTERNAL_ACCESS_DENIED",
  "message": "내부 API에 접근할 권한이 없습니다.",
  "data": null,
  "timestamp": "2026-08-02T12:30:00+09:00"
}
```

#### 500 Internal Server Error

```json
{
  "success": false,
  "code": "COMMON_INTERNAL_SERVER_ERROR",
  "message": "일시적인 오류가 발생했습니다.",
  "data": null,
  "timestamp": "2026-08-02T12:30:00+09:00"
}
```

---

### AI Worker 처리

```text
ACQUIRED
→ processingToken과 processingAttempt를 메모리에만 유지
→ PATCH /internal/v1/reports/{reportId}/progress (RECORD_COLLECTION)
→ GET /internal/v1/reports/{reportId}/input 진행

ALREADY_COMPLETED 또는 ALREADY_FAILED
→ 현재 Kafka offset COMMIT

ALREADY_PROCESSING
→ 현재 Kafka offset을 COMMIT하지 않고 backoff

CONTRACT_CONFLICT
→ 자동 재시도하지 않고 운영 정책 확인 대상으로 분류
```

---

## AI 리포트 진행 단계 저장

Domain: Internal Report
Method: PATCH
Progress: 완료
URI: /internal/v1/reports/{reportId}/progress
담당자: 박재명
연동여부: AI-007 HTTP Adapter 계약 연동 (실제 Worker E2E 미검증)

처리권을 가진 AI Worker가 현재 진행 단계를 저장하고 Backend 관리 Lease를 전체 TTL로 갱신한다.
프론트엔드가 호출하는 공개 API가 아니다.

---

### Request

- Request HTTP Method: `PATCH`
- 인증 필요: 내부 서비스 인증 필요
- Content-Type: `application/json`

#### Path Parameter

| 필드 | 타입 | 필수 | 설명 |
| --- | --- | --- | --- |
| `reportId` | Long | Y | 진행 단계를 저장할 Report ID, 1 이상 |

#### Request Header

```http
Authorization: Bearer {REPORT_INTERNAL_TOKEN}
```

#### Request Body

```json
{
  "processingToken": "opaque-one-time-token",
  "processingAttempt": 1,
  "stage": "NORMALIZATION"
}
```

| 필드 | 타입 | 필수 | 설명 |
| --- | --- | --- | --- |
| `processingToken` | String | Y | `ACQUIRED` 응답에서 한 번만 받은 불투명 처리 Token, 공백 불가 |
| `processingAttempt` | Integer | Y | `ACQUIRED` 응답의 처리 시도 번호, 1 이상 |
| `stage` | String | Y | 현재 Worker 진행 단계 |

허용 `stage`는 아래 6개다.

```text
RECORD_COLLECTION
STT_VALIDATION
NORMALIZATION
REPORT_GENERATION
EVIDENCE_MAPPING
RESULT_SAVING
```

`COMPLETED`는 이 API에서 허용하지 않는다. 결과와 근거를 원자적으로 저장하는
`PUT /internal/v1/reports/{reportId}/complete`만 `COMPLETED`로 전이할 수 있다.

---

### 처리 기준

- Report 행을 비관적 쓰기 잠금으로 조회해 같은 Report의 재선점·완료·실패 요청과 직렬화한다.
- Report가 `IN_PROGRESS`이고 Lease가 현재 시각보다 뒤에 있어야 한다.
- `processingAttempt`는 현재 Report의 attempt와 정확히 일치해야 한다.
- 요청 `processingToken`을 SHA-256 해시한 값과 DB의 해시를 상수시간 비교한다.
- Token 원문은 저장하거나 로그에 남기지 않는다.
- 유효한 요청이면 `progress_stage`를 저장하고 `processing_lease_expires_at`을
  현재 시각부터 전체 TTL(기본 30분)로 갱신한다.
- 동일 단계 재전송도 허용하며 Lease를 다시 전체 TTL로 갱신한다.
- 정상 다음 단계 전이만 허용하며 역행하거나 여러 단계를 임의로 건너뛰는 요청은 거부한다.
- Worker 계약상 `EVIDENCE_MAPPING`은 조건부 단계이므로 해당 단계를 건너뛰고
  `REPORT_GENERATION` 다음에 `RESULT_SAVING`을 저장할 수 있다.
- 상태, Token 해시, attempt는 변경하지 않는다.
- Lease 만료 또는 재선점 후 이전 Token·attempt 요청은 `409 STALE_PROCESSING_TOKEN`으로 거부한다.

---

### Response

#### 204 No Content

성공 응답은 공통 envelope를 사용하지 않으며 body가 없는 정확한 `204 No Content`다.

---

### Exception

#### 400 Bad Request — 요청값 검증 실패

- `reportId`가 1 미만
- `processingToken`이 누락되거나 공백
- `processingAttempt`가 1 미만
- `stage`가 누락되거나 허용값이 아님
- `stage=COMPLETED`

```json
{
  "success": false,
  "code": "COMMON_INVALID_REQUEST",
  "message": "입력값을 확인해 주세요.",
  "data": {
    "field": "stage",
    "reason": "허용되지 않은 진행 단계입니다."
  },
  "timestamp": "2026-08-02T12:30:00+09:00"
}
```

#### 401 Unauthorized — 내부 Token 누락·불일치

```json
{
  "success": false,
  "code": "REPORT_INTERNAL_AUTHENTICATION_FAILED",
  "message": "내부 API 인증이 필요합니다.",
  "data": null,
  "timestamp": "2026-08-02T12:30:00+09:00"
}
```

#### 403 Forbidden — 내부 API 접근 거부

```json
{
  "success": false,
  "code": "REPORT_INTERNAL_ACCESS_DENIED",
  "message": "내부 API에 접근할 권한이 없습니다.",
  "data": null,
  "timestamp": "2026-08-02T12:30:00+09:00"
}
```

#### 404 Not Found — Report 없음

```json
{
  "success": false,
  "code": "REPORT_NOT_FOUND",
  "message": "리포트를 찾을 수 없습니다.",
  "data": null,
  "timestamp": "2026-08-02T12:30:00+09:00"
}
```

#### 409 Conflict — 처리권 만료·불일치

Report가 `IN_PROGRESS`가 아니거나 Token·attempt가 현재 처리권과 다르거나 Lease가 만료된 경우다.
현재 단계와 같지 않고 정상 다음 단계 또는 허용된 `EVIDENCE_MAPPING` 생략 전이도 아닌 경우도 포함한다.

```json
{
  "success": false,
  "code": "STALE_PROCESSING_TOKEN",
  "message": "리포트 처리 상태가 충돌합니다.",
  "data": null,
  "timestamp": "2026-08-02T12:30:00+09:00"
}
```

#### 500 Internal Server Error

```json
{
  "success": false,
  "code": "COMMON_INTERNAL_SERVER_ERROR",
  "message": "일시적인 오류가 발생했습니다.",
  "data": null,
  "timestamp": "2026-08-02T12:30:00+09:00"
}
```

---

## AI 리포트 정규화 입력 원본 조회

Domain: Internal Report
Method: GET
Progress: 완료
URI: /internal/v1/reports/{reportId}/input
담당자: 박재명
연동여부: AI-007 `ReportInputHttpAdapter` 계약 연동 (실제 Worker E2E 미검증)

AI Report Worker가 AI-004 입력 정규화에 사용할 임장 원본 스냅샷을 조회한다.

Backend가 권위 있는 DB 스냅샷을 생성하고, AI Worker는 공통 envelope의 `data`만
`ReportNormalizationSource` version 1로 검증한다.

---

### Request

- Request HTTP Method: `GET`
- 인증 필요: 내부 서비스 인증 필요
- Request Body: 없음
- Query Parameter: 없음

#### Request Header

```text
Authorization: Bearer {REPORT_INTERNAL_TOKEN}
```

사용자 Access Token, `processingToken`, `processingAttempt`는 이 조회 API의 요청 계약이 아니다.

#### Path Parameter

| 필드 | 타입 | 필수 | 설명 |
| --- | --- | --- | --- |
| `reportId` | Long | Y | 조회할 Report ID. 1 이상 |

---

### 처리 기준

- `report.field_session_id`를 권위 있는 임장 세션으로 사용한다. `study_id`만으로 다른 세션을 재탐색하지 않는다.
- `REPEATABLE_READ`, read-only 트랜잭션 하나에서 context, 참여자, 체크리스트, 현장 기록, 미완료 STT 작업을 조회한다.
- Report, Study, `field_session`의 관계가 일치하고 Study가 삭제·취소되지 않은 경우에만 원본을 반환한다.
- 세션·참여자 종료 상태나 timestamp 이상은 Backend가 임의 보정하지 않는다. 원본 상태를 AI-004에 전달해 정규화기가 판정한다.
- 체크리스트나 기록이 없으면 `null`이 아닌 빈 배열을 반환한다.
- `authoritativeSourceIds`는 동일 스냅샷의 모든 `field_record.id`를 중복 없이 오름차순으로 반환하며 `fieldRecords.sourceId` 집합과 일치해야 한다.
- soft delete된 기록, 빈 TEXT, 미완료 STT, 사용할 수 없는 PHOTO도 미리 제외하지 않고 원본 상태로 반환한다.
- `incompleteSttJobs`에는 `stt_job.field_record_id IS NULL`인 작업을 상태와 관계없이 포함한다.
- PHOTO 메타데이터는 파일 소유자와 기록 작성자가 같고, Study가 일치하며, `file_usage=FIELD_PHOTO`인 경우에만 `photoFile`에 넣는다.
- S3 key, 원본 파일명, URL, 오디오 파일 ID, STT `fail_reason`, 파일 바이트는 반환하지 않는다.
- TEXT·STT 원문, 전체 응답 body, Authorization Header, 내부 Token은 로그에 남기지 않는다.
- 조회 성공 응답에는 `Cache-Control: no-store`를 적용한다.

#### 결정적 정렬

```text
participants       : startedAt ASC, fieldParticipantId ASC
checklistItems     : participant 순서, displayOrder ASC, checklistItemId ASC
authoritativeSourceIds / fieldRecords : sourceId ASC
incompleteSttJobs  : sttId ASC
```

---

### Response

#### 200 OK

```json
{
  "success": true,
  "code": "REPORT_INPUT_SUCCESS",
  "message": "리포트 입력을 조회했습니다.",
  "data": {
    "schemaVersion": 1,
    "reportId": 48,
    "studyId": 7,
    "apartmentId": 15,
    "fieldSessionId": 900,
    "sessionStatus": "ENDED",
    "sessionStartedAt": "2026-07-20T14:00:00+09:00",
    "sessionEndedAt": "2026-07-20T16:00:00+09:00",
    "snapshotAt": "2026-07-20T16:05:00+09:00",
    "participants": [
      {
        "fieldParticipantId": 71,
        "memberId": 7,
        "status": "ENDED",
        "startedAt": "2026-07-20T14:00:00+09:00",
        "endedAt": "2026-07-20T16:00:00+09:00"
      }
    ],
    "checklistItems": [
      {
        "checklistId": 55,
        "checklistItemId": 501,
        "memberId": 7,
        "fallback": false,
        "category": "교통",
        "title": "지하철역 접근성",
        "subtitle": null,
        "displayOrder": 1,
        "completed": true,
        "completedAt": "2026-07-20T14:35:00+09:00"
      }
    ],
    "authoritativeSourceIds": [201],
    "fieldRecords": [
      {
        "sourceId": 201,
        "sessionId": 900,
        "checklistItemId": 501,
        "authorId": 7,
        "sourceType": "TEXT",
        "textContent": "현관에서 지하철역 입구까지 걸어서 약 8분이 걸립니다.",
        "sttStatus": null,
        "photoFile": null,
        "deletedAt": null,
        "recordedAt": "2026-07-20T14:30:00+09:00",
        "updatedAt": "2026-07-20T14:31:00+09:00"
      }
    ],
    "incompleteSttJobs": []
  },
  "timestamp": "2026-08-02T12:30:00+09:00"
}
```

#### Response Field

| 필드 | 타입 | 설명 |
| --- | --- | --- |
| `schemaVersion` | Integer | 항상 `1` |
| `reportId` | Long | Report ID |
| `studyId` | Long | Study ID |
| `apartmentId` | Long | Apartment ID |
| `fieldSessionId` | Long | V16의 권위 있는 임장 세션 ID |
| `sessionStatus` | String | `IN_PROGRESS`, `ENDED` |
| `sessionStartedAt` | DateTime | timezone offset 포함 |
| `sessionEndedAt` | DateTime/null | timezone offset 포함 |
| `snapshotAt` | DateTime | 동일 DB 스냅샷 기준 시각 |
| `participants` | Array | 권위 `field_session`에 속한 전체 `field_participant` 원본 목록. 종료 여부는 AI-004가 검증 |
| `checklistItems` | Array | 참여자별 체크리스트와 완료 상태 |
| `authoritativeSourceIds` | Array<Long> | 모든 권위 `field_record.id` 목록 |
| `fieldRecords` | Array | TEXT·STT·PHOTO 현장 기록 원본 |
| `incompleteSttJobs` | Array | 아직 `field_record`로 완성되지 않은 STT 작업 |

#### participants

| 필드 | 타입 | 설명 |
| --- | --- | --- |
| `fieldParticipantId` | Long | 내부 정규화용 참여 행 ID |
| `memberId` | Long | AI-004에서 `participantRef`로 치환할 회원 ID |
| `status` | String | `IN_PROGRESS`, `ENDED` |
| `startedAt` | DateTime | 참여 시작 시각 |
| `endedAt` | DateTime/null | 참여 종료 시각 |

#### checklistItems

| 필드 | 타입 | 설명 |
| --- | --- | --- |
| `checklistId` | Long | Checklist ID |
| `checklistItemId` | Long | Checklist Item ID |
| `memberId` | Long | Checklist 소유 회원 ID |
| `fallback` | Boolean | 기본 체크리스트 대체 여부 |
| `category` | String | 카테고리, 최대 30자 |
| `title` | String | 항목 제목 |
| `subtitle` | String/null | 항목 부제 |
| `displayOrder` | Integer | 1 이상의 표시 순서 |
| `completed` | Boolean | 완료 여부 |
| `completedAt` | DateTime/null | 완료 시각 |

#### fieldRecords

| 필드 | 타입 | 설명 |
| --- | --- | --- |
| `sourceId` | Long | `field_record.id` |
| `sessionId` | Long | 임장 세션 ID |
| `checklistItemId` | Long | 연결된 Checklist Item ID |
| `authorId` | Long | AI-004에서 `participantRef`로 치환할 작성자 ID |
| `sourceType` | String | `TEXT`, `STT`, `PHOTO` |
| `textContent` | String/null | TEXT 원문 또는 STT transcript. STT는 임의 절단 금지 |
| `sttStatus` | String/null | `PENDING`, `PROCESSING`, `DONE`, `FAILED` |
| `photoFile` | Object/null | 검증된 PHOTO 파일 메타데이터 |
| `deletedAt` | DateTime/null | 기록 soft delete 시각 |
| `recordedAt` | DateTime | 기록 생성 시각 |
| `updatedAt` | DateTime | 기록 갱신 시각 |

`sourceType`별 nullable 규칙은 다음과 같다.

- `TEXT`: `sttStatus`, `photoFile`은 `null`이다. `textContent`가 `null` 또는 blank여도 원본 행은 반환한다.
- `STT`: `photoFile`은 `null`이다. `sttStatus`와 transcript 원본을 그대로 반환하며 미완료 상태도 허용한다.
- `PHOTO`: `textContent`, `sttStatus`는 `null`이다. 파일 소유권·Study·용도가 맞지 않으면 행은 유지하되 `photoFile=null`로 반환한다.
- 위 불완전 원본은 404 대상이 아니며 AI-004가 제외 사유와 품질 이슈를 판정한다.

#### photoFile

| 필드 | 타입 | 설명 |
| --- | --- | --- |
| `fileId` | Long | File Meta ID |
| `contentType` | String/null | 최대 100자 |
| `sizeBytes` | Long/null | 0 이상의 파일 크기 |
| `uploadStatus` | String | `PENDING`, `COMPLETED`, `FAILED`, `DELETED` |
| `expiresAt` | DateTime/null | 임시 보관 만료 시각 |
| `deletedAt` | DateTime/null | 파일 삭제 시각 |

#### incompleteSttJobs

| 필드 | 타입 | 설명 |
| --- | --- | --- |
| `sttId` | String | 최대 50자 |
| `memberId` | Long | 요청 회원 ID |
| `checklistItemId` | Long | 연결된 Checklist Item ID |
| `status` | String | `PENDING`, `PROCESSING`, `DONE`, `FAILED` |
| `retryable` | Boolean | 재시도 가능 여부 |
| `failCode` | String/null | 안전한 실패 코드, 최대 100자 |
| `requestedAt` | DateTime | STT 요청 시각 |

---

### Exception

- `400 COMMON_INVALID_REQUEST`: `reportId`가 1 미만
- `401 REPORT_INTERNAL_AUTHENTICATION_FAILED`: 내부 Token 누락 또는 불일치
- `403 REPORT_INTERNAL_ACCESS_DENIED`: 내부 API 접근 권한 없음
- `404 REPORT_NOT_FOUND`: Report가 존재하지 않거나, `report.study_id`·`report.apartment_id`·`report.field_session_id`와 Study·`field_session`의 권위 관계가 일치하지 않거나, Study가 삭제·취소되어 안전한 원본 스냅샷을 구성할 수 없음. 세부 관계 오류를 별도 코드로 노출하지 않으며 `studyId`로 다른 세션이나 아파트를 대체 조회하지 않음
- `500 COMMON_INTERNAL_SERVER_ERROR`

AI Adapter는 `404`를 재시도 불가 `SOURCE_LOAD_FAILED`로 처리한다. Worker는 terminal fail을
저장하고 Kafka offset을 COMMIT한다. 반면 권위 관계가 유효한 스냅샷 내부의 삭제 기록,
빈 TEXT, 미완료 STT, 사용할 수 없는 PHOTO는 `404`가 아니라 `200` 원본에 포함한다.
`5xx`·timeout·network 오류는 재시도 가능한 `BACKEND_TRANSIENT`로 처리한다.

---

## AI 리포트 결과·근거 완료 저장

Domain: Internal Report
Method: PUT
Progress: 완료
URI: /internal/v1/reports/{reportId}/complete
담당자: 박재명
연동여부: AI-007 `ReportBackendHttpAdapter` 계약 연동 (실제 Worker E2E 미검증)

처리권을 가진 AI Report Worker가 AI-005의 최종 리포트 결과와 AI-006의
TEXT·STT 직접 근거를 한 트랜잭션으로 저장하고 Report와 연결된 Study를 완료
상태로 전이한다.
프론트엔드가 호출하는 공개 API가 아니다.

---

### Request

- Request HTTP Method: `PUT`
- 인증 필요: 내부 서비스 인증 필요
- Content-Type: `application/json`

#### Path Parameter

| 필드 | 타입 | 필수 | 설명 |
| --- | --- | --- | --- |
| `reportId` | Long | Y | 완료할 Report ID, 1 이상 |

#### Request Header

```http
Authorization: Bearer {REPORT_INTERNAL_TOKEN}
```

사용자 Access Token은 사용할 수 없다. Header의 내부 서비스 Token과 Body의 일회성
`processingToken`은 서로 다른 값이며 둘 다 로그에 남기지 않는다.

#### Request Body

아래는 원본 스냅샷에 체크리스트와 유효 기록이 없는 데이터 부족 Report의 최소 예시다.
실제 요청은 AI-005·AI-006 결과 전체를 축약 없이 전달해야 한다.
계약에 정의되지 않은 최상위·중첩 필드는 허용하지 않는다.

```json
{
  "processingToken": "opaque-one-time-token",
  "processingAttempt": 1,
  "generationResult": {
    "title": "현장 기록이 부족한 임장 리포트",
    "summary": "분석할 수 있는 현장 기록이 부족합니다.",
    "metrics": {
      "totalChecklistItemCount": 0,
      "completedChecklistItemCount": 0,
      "averageCompletionRate": 0.0,
      "fieldRecordCount": 0
    },
    "topPositiveFeatures": [],
    "topCautionFeatures": [],
    "commonOpinions": [],
    "conflictingOpinions": [],
    "categories": []
  },
  "evidenceResult": {
    "claims": []
  }
}
```

#### 최상위 필드

| 필드 | 타입 | 필수 | 설명 |
| --- | --- | --- | --- |
| `processingToken` | String | Y | `ACQUIRED` 응답에서 한 번만 받은 불투명 처리 Token, 공백 불가 |
| `processingAttempt` | Integer | Y | `ACQUIRED` 응답의 처리 시도 번호, 1 이상 |
| `generationResult` | Object | Y | AI-005 `ReportGenerationResult` 전체 |
| `evidenceResult` | Object | Y | AI-006 `EvidenceLinkResult` 전체 |

#### generationResult

| 필드 | 타입 | 필수 | 설명 |
| --- | --- | --- | --- |
| `title` | String | Y | 리포트 제목, 1~120자 |
| `summary` | String | Y | 전체 요약, 1~2,000자 |
| `metrics` | Object | Y | 권위 DB 원본과 다시 대조할 집계 |
| `topPositiveFeatures` | Array | Y | 순위가 1부터 연속인 긍정 특징, 최대 3개 |
| `topCautionFeatures` | Array | Y | 순위가 1부터 연속인 주의 특징, 최대 3개 |
| `commonOpinions` | Array | Y | 둘 이상 참여자가 공통으로 언급한 의견 |
| `conflictingOpinions` | Array | Y | 긍정·주의가 함께 존재하는 의견 |
| `categories` | Array | Y | 체크리스트 카테고리 순서와 일치하는 참여자별 의견 집계 |

`metrics`는 `totalChecklistItemCount`, `completedChecklistItemCount`,
`averageCompletionRate`, `fieldRecordCount`를 가진다. 완료율은 소수 첫째 자리까지이며
Backend가 실제 체크리스트와 정규화에 포함되는 고유 원본 수를 다시 계산한다.

특징은 `rank`, `label`, `summary`, `mentionCount`, `participantRefs`를 가진다.
공통 의견은 `category`, `label`, `opinionType`, `summary`, `participantCount`,
`participantRefs`를 가진다. 상반 의견은 `category`, `label`, `summary`,
`positiveParticipantCount`, `cautionParticipantCount`, `positiveParticipantRefs`,
`cautionParticipantRefs`를 가진다.

카테고리는 `category`, `summary`, `positiveOpinionCount`, `cautionOpinionCount`,
`dataSufficient`, `participantOpinions`를 가진다. 참여자 의견은 `participantRef`,
`participantLabel`, `opinionType`, `summary`를 가진다. `opinionType`은
`POSITIVE`, `CAUTION`만 허용한다.

#### evidenceResult

`claims` 각 항목은 아래 필드를 가진다.

| 필드 | 타입 | 필수 | 설명 |
| --- | --- | --- | --- |
| `claimKey` | String | Y | AI 문장 식별 키, 1~100자, 요청 내 고유 |
| `claimType` | String | Y | `FEATURE_POSITIVE`, `FEATURE_CAUTION`, `COMMON`, `CONFLICT`, `PARTICIPANT_OPINION` |
| `category` | String/null | 조건부 | 카테고리 기반 claim의 카테고리, 최대 30자 |
| `label` | String/null | 조건부 | 특징·공통·상반 의견의 label, 최대 80자 |
| `opinionType` | String/null | 조건부 | `POSITIVE`, `CAUTION` |
| `participantRefs` | Array<String> | Y | claim에 포함된 고유 익명 참여자 참조, `P1` 형식 |
| `evidences` | Array<Object> | Y | 한 개 이상의 TEXT·STT 근거 |
| `displayOrder` | Integer | Y | 1부터 연속인 표시 순서 |

각 `evidences` 항목은 `sourceType`, `sourceId`, `participantRef`,
`checklistItemId`, `category`, `recordedAt`, `evidenceRole`을 가진다.
`sourceType`은 `TEXT`, `STT`만 허용한다. 일반 claim의 `evidenceRole`은
`SUPPORT`이고, `CONFLICT`는 `SUPPORT_POSITIVE`, `SUPPORT_CAUTION`을 모두 가져야 한다.

---

### 처리 기준

#### 1. 처리권과 동시성

- Report 행을 비관적 쓰기 잠금으로 조회해 같은 Report의 완료·재선점·실패 요청을 직렬화한다.
- 최초 완료는 Report가 `IN_PROGRESS`이고 Lease가 현재 시각보다 뒤에 있어야 한다.
- `processingAttempt`는 현재 attempt와 정확히 일치해야 한다.
- 요청 `processingToken`의 SHA-256 해시를 DB 해시와 상수시간 비교한다.
- Report가 없으면 `404 REPORT_NOT_FOUND`, 만료·재선점·잘못된 처리권이면
  `409 STALE_PROCESSING_TOKEN`을 반환한다.
- 완료를 위해 `RESULT_SAVING` 단계를 선행 저장하는 것은 Worker의 정상 흐름이지만,
  완료 API 자체는 특정 이전 진행 단계를 별도로 강제하지 않는다.

#### 2. 권위 집계 재검증

- `report.field_session_id` 기준의 동일 DB 스냅샷을 다시 조회한다.
- 참여자는 최종 `field_participant` 정렬 순서대로 `P1`, `P2`, ...에 대응한다.
- 체크리스트 총수·완료수·완료율과 정규화 대상 고유 원본 수를 다시 계산한다.
- 특징·공통·상반·카테고리 인원 수는 중복 없는 유효 `participantRef`와 일치해야 한다.
- 카테고리 목록과 순서는 권위 체크리스트 카테고리와 일치해야 한다.
- 집계·참여자·claim 구조가 권위 원본과 다르면 `400 COMMON_INVALID_REQUEST`로 거부한다.

#### 3. AI 직접 근거 저장

- `generationResult`의 의미 claim과 `evidenceResult.claims`를 정확히 대응시킨다.
- 현재 `report_evidence`에는 존재하고 삭제되지 않은 TEXT와 변환 완료된 STT만 저장한다.
- PHOTO, 존재하지 않는 `sourceId`, 삭제된 기록, 미완료 STT, source type이 맞지 않는
  기록은 `report_evidence`에 저장하지 않는다.
- 저장 대상 근거의 참여자, 체크리스트, 카테고리, 기록 시각과 상반 의견의 긍정·주의
  역할은 DB 원본과 일치해야 한다.
- `(reportId, fieldRecordId, claimKey)` 중복은 한 건으로 제거하며 `displayOrder`를 함께 저장한다.
- PHOTO와 체크리스트 완료 상태는 이후 사용자 raw 데이터 조회에서 DB 원본으로 제공하며
  AI 직접 근거 테이블에는 저장하지 않는다.

#### 4. 원자 저장과 완료 상태

- 유효한 `generationResult` Object를 wrapper 없이 `report.result_json` 루트에 저장한다.
- 검증된 근거를 `report_evidence`에 저장한다.
- 같은 트랜잭션에서 `status=DONE`, `progressStage=COMPLETED`, `completedAt=현재 시각`,
  `isRetryable=false`를 반영하고 Lease를 해제한다.
- 완료 이벤트에 `studyId`를 함께 전달하고 Study 행을 비관적 쓰기 잠금으로 조회해
  같은 트랜잭션에서 `IN_PROGRESS → COMPLETED`로 전이한다.
- Study가 이미 `COMPLETED`이면 중복 완료 이벤트로 보고 멱등하게 유지한다.
  `RECRUITING`, `CLOSED`, `CANCELED`이거나 Study가 삭제·미존재하면 상태를 덮어쓰지
  않고 리포트 결과·근거·자동 게시글·Study 전이 전체를 롤백한다.
- Study 완료 후 공지·일정·멤버 관리, 사용자 채팅 전송, 체크리스트 답변과 현장 기록
  생성·수정·삭제는 기존 상태 정책으로 차단한다. 기존 승인 참여자의 상세·공지·일정·
  채팅 이력·임장 기록 조회는 읽기 전용으로 유지한다.
- 임장 진행 중 정상 접수된 실패 STT 작업의 재처리는 해당 API 계약에 따라 세션 종료
  이후에도 음성 원본 보존 기간 내 허용한다. 이는 완료 후 새 음성 업로드·새 STT 요청을
  허용하는 것이 아니다.
- 최초 완료에서는 커밋 전 내부 이벤트로 자동 정보게시글도 생성한다. 이 작업은 `report_id` 기반 DB 멱등 삽입이며, 실패하면 결과·근거·`DONE` 전이 전체를 롤백한다.
- 최초 완료 전 이미 최종 결과·완료 payload 해시·근거가 일부 존재하면 기존 데이터를
  덮어쓰지 않고 `409 COMPLETE_PAYLOAD_CONFLICT`로 거부한다.
- 원문 완료 요청과 `processingToken`은 저장하지 않는다. `generationResult`와
  `evidenceResult`의 canonical SHA-256 해시만 `complete_payload_hash`에 저장한다.

#### 5. 멱등성

- `DONE` Report에 같은 Token·attempt와 같은 결과·근거를 재전송하면 Lease 상태와 무관하게
  아무것도 변경하지 않고 다시 `204`를 반환한다.
- Token, attempt, `generationResult`, `evidenceResult` 중 하나라도 다르면
  `409 COMPLETE_PAYLOAD_CONFLICT`를 반환한다.
- 완료 후에도 멱등 판정을 위해 Token 해시와 attempt는 유지하고 Lease만 null로 해제한다.

---

### Response

#### 204 No Content

성공 응답은 공통 envelope를 사용하지 않으며 body가 없는 정확한 `204 No Content`다.
응답에는 `Cache-Control: no-store`를 적용한다.

---

### Exception

- `400 COMMON_INVALID_REQUEST`: `reportId`, Token, attempt, 중첩 결과 형식 또는 권위 집계 검증 실패
- `401 REPORT_INTERNAL_AUTHENTICATION_FAILED`: 내부 Token 누락 또는 불일치
- `403 REPORT_INTERNAL_ACCESS_DENIED`: 내부 API 접근 권한 없음
- `404 REPORT_NOT_FOUND`: Report 없음 또는 권위 Report·Study·Session 관계 불일치
- `409 STALE_PROCESSING_TOKEN`: 최초 완료 처리권·attempt·Lease 불일치
- `409 COMPLETE_PAYLOAD_CONFLICT`: 완료된 요청과 다른 payload 또는 예기치 않은 선행 저장 데이터 존재
- `500 COMMON_INTERNAL_SERVER_ERROR`

`400`, `401`, `403`, `404`, `409`, `500` 오류는 공통 5필드 `ApiResponse` envelope를 사용한다.
안전한 code·message 이외에 Token, 원문 결과, TEXT·STT 또는 내부 예외를 노출하지 않는다.

### AI Worker 처리

```text
204
→ COMPLETED
→ Kafka offset COMMIT

timeout / network / 5xx
→ RETRY_LATER
→ Kafka offset DO_NOT_COMMIT

400 / 401 / 403 / 409
→ BACKEND_REJECTED
→ POLICY_PENDING
→ fail API로 덮어쓰거나 무한 재시도하지 않음
```

---

## AI 리포트 실패 정보 저장

Domain: Internal Report
Method: PUT
Progress: 완료
URI: /internal/v1/reports/{reportId}/fail
담당자: 박재명
연동여부: AI-007 `ReportBackendHttpAdapter` 계약 대상 · 실제 Backend 연동 검증 대기

처리권을 가진 AI Report Worker가 생성 실패 단계와 안전한 실패 정보를 원자적으로
저장하고 Report를 `FAILED`로 전환한다. 프론트엔드가 호출하는 공개 API가 아니다.

---

### Request

- Request HTTP Method: `PUT`
- 인증 필요: 내부 서비스 인증 필요
- Content-Type: `application/json`

#### Path Parameter

| 필드 | 타입 | 필수 | 설명 |
| --- | --- | --- | --- |
| `reportId` | Long | Y | 실패 처리할 Report ID, 1 이상 |

#### Request Header

```http
Authorization: Bearer {REPORT_INTERNAL_TOKEN}
```

사용자 Access Token은 사용할 수 없다. Header의 내부 서비스 Token과 Body의 일회성
`processingToken`은 서로 다른 값이며 둘 다 저장하거나 로그에 남기지 않는다.

#### Request Body

```json
{
  "processingToken": "opaque-one-time-token",
  "processingAttempt": 1,
  "failedStage": "NORMALIZATION",
  "errorCode": "NORMALIZATION_FAILED",
  "message": "리포트 입력 정규화에 실패했습니다.",
  "retryable": false
}
```

| 필드 | 타입 | 필수 | 설명 |
| --- | --- | --- | --- |
| `processingToken` | String | Y | `ACQUIRED` 응답에서 한 번만 받은 불투명 처리 Token, 공백 불가 |
| `processingAttempt` | Integer | Y | `ACQUIRED` 응답의 처리 시도 번호, 1 이상 |
| `failedStage` | String | Y | 실패가 확정된 Worker 진행 단계 |
| `errorCode` | String | Y | AI-007 allowlist 실패 코드, 최대 100자 |
| `message` | String | Y | `errorCode`에 정확히 대응하는 고정 안전 문구, 최대 500자 |
| `retryable` | Boolean | Y | 명시적 재생성 요청이 가능한 실패인지 여부 |

계약에 정의되지 않은 필드는 허용하지 않는다.

#### failedStage 허용값

```text
RECORD_COLLECTION
STT_VALIDATION
NORMALIZATION
REPORT_GENERATION
EVIDENCE_MAPPING
RESULT_SAVING
```

`COMPLETED`는 실패 단계가 아니므로 허용하지 않는다.

#### errorCode와 고정 안전 문구

| errorCode | message |
| --- | --- |
| `INVALID_EVENT` | `리포트 요청 이벤트가 올바르지 않습니다.` |
| `CONTRACT_CONFLICT` | `리포트 요청 계약이 충돌합니다.` |
| `GENERATION_RESULT_INVALID` | `리포트 생성 결과가 유효하지 않습니다.` |
| `NORMALIZATION_FAILED` | `리포트 입력 정규화에 실패했습니다.` |
| `SOURCE_LOAD_FAILED` | `리포트 원본 데이터 조회에 실패했습니다.` |
| `PROVIDER_FAILED` | `AI 제공자 호출에 실패했습니다.` |
| `MISSING_EVIDENCE` | `리포트 근거 연결에 실패했습니다.` |
| `INPUT_TOO_LARGE` | `리포트 입력 크기가 제한을 초과했습니다.` |
| `INVALID_REFERENCE` | `리포트 참조가 올바르지 않습니다.` |
| `SCHEMA_VALIDATION_FAILED` | `AI 응답 스키마 검증에 실패했습니다.` |
| `PYDANTIC_VALIDATION_FAILED` | `AI 응답 데이터 검증에 실패했습니다.` |
| `BACKEND_TRANSIENT` | `Backend 일시 오류가 발생했습니다.` |
| `BACKEND_CONTRACT_ERROR` | `Backend 계약 오류가 발생했습니다.` |
| `BACKEND_AUTH_ERROR` | `Backend 인증에 실패했습니다.` |
| `UNKNOWN_FAILURE` | `리포트 생성 중 오류가 발생했습니다.` |

Worker나 외부 제공자가 만든 원문 오류 메시지, 예외 메시지, traceback은 요청하거나
저장하지 않는다. `errorCode`와 `message`가 위 조합과 정확히 일치하지 않으면
`400 COMMON_INVALID_REQUEST`로 거부한다.

---

### 처리 기준

#### 1. 처리권과 동시성

- Report 행을 비관적 쓰기 잠금으로 조회해 같은 Report의 진행·완료·재선점 요청과 직렬화한다.
- 최초 실패는 Report가 `IN_PROGRESS`이고 Lease가 현재 시각보다 뒤에 있어야 한다.
- `processingAttempt`는 현재 attempt와 정확히 일치해야 한다.
- 요청 `processingToken`의 SHA-256 해시를 DB 해시와 상수시간 비교한다.
- Report가 없으면 `404 REPORT_NOT_FOUND`를 반환한다.
- 최초 요청에서 상태, Lease, Token 또는 attempt가 일치하지 않으면
  `409 STALE_PROCESSING_TOKEN`을 반환한다.

#### 2. 안전한 실패 정보 저장

- 요청 `failedStage`를 `report.progress_stage`에 저장한다.
- allowlist `errorCode`를 `report.fail_code`에 저장한다.
- 요청 원문이 아니라 Backend allowlist의 정본 문구를 `report.fail_reason`에 저장한다.
- `retryable`을 `report.is_retryable`에 저장한다.
- Backend의 단일 현재 시각을 `report.failed_at`에 저장한다.
- 같은 트랜잭션에서 `status=FAILED`로 전환하고 Lease를 null로 해제한다.
- 멱등 판정에 필요한 processing Token 해시와 attempt는 유지한다.
- `result_json`, 완료 payload 해시 또는 Report 근거가 이미 존재하면 실패 정보로
  덮어쓰지 않고 `409 FAIL_PAYLOAD_CONFLICT`로 거부한다.
- `completed_at`은 실패 시각으로 재사용하지 않고 null로 유지한다.

#### 3. 멱등성

- 실패 단계·코드·정본 문구·retryable의 canonical SHA-256만
  `report.fail_payload_hash`에 저장한다.
- `FAILED` Report에 같은 Token·attempt와 같은 실패 payload를 재전송하면 Lease와
  무관하게 아무것도 변경하지 않고 다시 `204`를 반환한다.
- 이미 `FAILED`인 Report에 Token, attempt, 단계, 코드, 문구 또는 retryable 중
  하나라도 다른 명령을 보내면 `409 FAIL_PAYLOAD_CONFLICT`를 반환한다.
- 원문 `processingToken`과 전체 실패 요청 body는 저장하지 않는다.

#### 4. 재시도 경계

- `retryable=true`는 재시도 가능성을 기록할 뿐 이 API가 즉시 재시작한다는 뜻이 아니다.
- 기존 Kafka 이벤트를 다시 수신해도 `FAILED` Report는 `ALREADY_FAILED`로 판정한다.
- 재시작 권한은 별도 사용자 retry API가 소유한다. retry API가 `FAILED -> PENDING`으로
  전이하고 새 이벤트를 발행한 뒤에만 새 Token과 증가한 attempt를 획득할 수 있다.
- retry API 구현과 이벤트 발행은 이 API의 범위에 포함하지 않는다.

---

### Response

#### 204 No Content

성공 응답은 공통 envelope를 사용하지 않으며 body가 없는 정확한 `204 No Content`다.
응답에는 `Cache-Control: no-store`를 적용한다.

---

### Exception

- `400 COMMON_INVALID_REQUEST`: ID·Token·attempt·단계·코드·문구·retryable 형식 또는 고정 문구 불일치
- `401 REPORT_INTERNAL_AUTHENTICATION_FAILED`: 내부 Token 누락 또는 불일치
- `403 REPORT_INTERNAL_ACCESS_DENIED`: 내부 API 접근 권한 없음
- `404 REPORT_NOT_FOUND`: Report 없음
- `409 STALE_PROCESSING_TOKEN`: 최초 실패 처리권·attempt·Lease 불일치
- `409 FAIL_PAYLOAD_CONFLICT`: 이미 확정된 실패 명령과 다른 요청 또는 선행 완료 데이터 존재
- `500 COMMON_INTERNAL_SERVER_ERROR`

오류는 공통 5필드 `ApiResponse` envelope를 사용한다. 안전한 code·message 이외에
Authorization, processing Token, 전체 요청, 원문 오류, TEXT·STT 또는 내부 예외를
노출하지 않는다.

### AI Worker 처리

```text
204
→ TERMINAL_FAILED
→ Kafka offset COMMIT

동일 명령 재전달 204
→ TERMINAL_FAILED 유지
→ Kafka offset 재 COMMIT 가능

timeout / network / 5xx
→ RETRY_LATER
→ Kafka offset DO_NOT_COMMIT

400 / 401 / 403 / 404 / 409
→ BACKEND_REJECTED
→ POLICY_PENDING
→ 자동 FAILED 덮어쓰기 또는 무한 재시도 금지
```

---

## 리포트 생성 상태 조회

Domain: Report
Method: GET
Progress: 완료
URI: /api/v1/reports/{reportId}/status
담당자: 박재명
연동여부: No

AI 임장 리포트의 현재 생성 상태, 진행 단계, 진행률, 실패 사유와 재시도 가능 여부를 조회한다.

임장 세션이 종료된 뒤 리포트 생성 화면에서 진행 상황을 표시하고, 생성 완료 시 상세 화면으로 이동하거나 생성 실패 시 재생성 가능 여부를 판단할 때 사용한다.

---

### Request

- Request HTTP Method: `GET`
- 인증 필요: 필요
- Request Body: 없음

#### Request Header

```
Authorization: Bearer {accessToken}
```

#### Path Variable

```
GET /api/v1/reports/48/status
```

| 필드 | 타입 | 필수 | 설명 |
| --- | --- | --- | --- |
| `reportId` | Long | Y | 생성 상태를 조회할 리포트 ID |

---

### 리포트 상태

| 값 | 설명 |
| --- | --- |
| `PENDING` | 리포트 생성 이벤트가 접수되어 작업 시작을 기다리는 상태 |
| `IN_PROGRESS` | AI 파이프라인이 자료 수집·분석·근거 연결을 수행 중인 상태 |
| `DONE` | 리포트 생성과 저장이 모두 완료된 상태 |
| `FAILED` | 생성 과정에서 오류가 발생해 작업이 종료된 상태 |

### 진행 단계

| 값 | 설명 |
| --- | --- |
| `RECORD_COLLECTION` | 체크리스트 완료 상태, 텍스트·사진·STT 기록과 아파트 정보를 수집하는 단계 |
| `STT_VALIDATION` | 음성 기록의 STT 완료 상태와 유효성을 확인하는 단계 |
| `NORMALIZATION` | 참여자별 입력을 AI-004 정규화 계약으로 변환하는 단계 |
| `REPORT_GENERATION` | 참여자 의견을 긍정 요소·주의 요소와 카테고리별 의견으로 생성하는 단계 |
| `EVIDENCE_MAPPING` | 생성 문장과 실제 TEXT·STT `sourceId`를 연결하는 단계 |
| `RESULT_SAVING` | 최종 결과와 고유 근거를 원자적으로 저장하는 단계 |
| `COMPLETED` | 최종 `resultJson`과 근거 저장이 완료된 단계 |

`FAILED`는 리포트 상태이며 진행 단계 enum에는 포함하지 않는다. 실패한 경우 `progressStage`에는 실패 직전까지 수행하던 단계를 유지한다.

---

### 처리 기준

#### 1. 회원 및 리포트 확인

- 로그인한 회원 ID는 Access Token에서 확인하고, 탈퇴·삭제되지 않은 `ACTIVE` 회원인지 검증한다.
- `reportId`에 해당하는 리포트가 존재하는지 확인한다.
- 리포트 생성이 진행 중이거나 실패한 상태에서는 해당 임장 스터디의 스터디장 또는 현재 `ACTIVE` 참여자만 상태를 조회할 수 있다.
- `DONE` 상태 리포트 요약은 로그인한 회원이 조회할 수 있다.
- 존재하지 않는 리포트와 접근할 수 없는 비완료 리포트를 응답에서 과도하게 구분해 노출하지 않는다.

#### 2. 상태 정보 조회

- ERD의 `report.status`, `progress_stage`, `fail_reason`, `is_retryable`, `created_at`, `updated_at`, `completed_at`을 조회한다.
- `progressRate`는 별도 DB 컬럼이 아니라 `status`와 `progressStage`를 기준으로 서버가 계산한다.
- 단계별 권장 진행률은 `PENDING=0`, `RECORD_COLLECTION=20`, `STT_VALIDATION=40`, `NORMALIZATION=55`, `REPORT_GENERATION=70`, `EVIDENCE_MAPPING=90`, `RESULT_SAVING=95`, `COMPLETED=100`이다.
- `FAILED` 상태에서는 실패 직전 `progressStage`에 해당하는 계산 진행률을 반환한다.
- `DONE` 상태이면 `progressRate = 100`, `progressStage = COMPLETED`, `detailAvailable = true`로 반환한다.
- `FAILED` 상태이면 `detailAvailable = false`로 반환하고, 서버가 재시도 가능하다고 판단한 경우에만 `retryAvailable = true`로 반환한다.
- `is_retryable = false`인 실패는 원본 데이터 부족, 권한 문제, 삭제된 필수 데이터 등 단순 재시도로 해결할 수 없는 경우다.
- 프론트가 `/reports/{reportId}` 상세에 직접 진입해 `REPORT_NOT_DONE`을 받으면 일반 오류 대신 `/report-generating/{reportId}`로 전환해 같은 ID의 상태를 폴링한다.

#### 3. 사용자 표시 문구

- `progressMessage`는 DB에 저장된 필수 컬럼이 아니라 현재 `status`와 `progressStage`를 기반으로 서버가 계산해 반환하는 표시용 필드다.
- 프론트가 단계 enum을 자체 문구로 변환할 수도 있으나, 앱 버전별 문구 불일치를 줄이기 위해 서버 문구를 우선 사용할 수 있다.
- `failReason`은 서버가 승인한 사용자 표시용 문구만 반환한다. 내부 예외 스택, 네트워크·DB 정보 또는 외부 AI 응답 원문 등 승인되지 않은 값은 일반 실패 문구로 대체한다.

#### 4. 조회 주기

- `PENDING` 또는 `IN_PROGRESS` 상태에서 프론트는 일정 간격으로 이 API를 재호출할 수 있다.
- 권장 폴링 간격은 2~5초이며, 같은 화면에서 과도하게 짧은 간격으로 호출하지 않는다.
- `DONE` 또는 `FAILED`가 반환되면 상태 폴링을 중단한다.
- 서버는 상태 조회만으로 AI 작업을 새로 실행하거나 재시도 횟수를 증가시키지 않는다.

#### 5. 리포트 완료 후 후속 처리

- 리포트가 `DONE`으로 전환되면 동일 트랜잭션 또는 신뢰할 수 있는 후속 이벤트로 완료 시각을 저장한다.
- 완료 리포트인 경우 커뮤니티 자동 게시글이 생성될 수 있다.
- 완료 이벤트는 동일 리포트에 대해 한 번만 처리되어야 한다.
- 완료 알림과 FCM Push는 별도 알림 처리 과정에서 멱등하게 생성한다.

---

### Response

#### 200 OK — 생성 대기

```
{
  "success": true,
  "code": "REPORT_STATUS_SUCCESS",
  "message": "리포트 생성 상태 조회에 성공했습니다.",
  "data": {
    "reportId": 48,
    "status": "PENDING",
    "progressRate": 0,
    "progressStage": "RECORD_COLLECTION",
    "progressMessage": "임장 기록을 수집할 준비를 하고 있습니다.",
    "detailAvailable": false,
    "retryAvailable": false,
    "failReason": null,
    "createdAt": "2026-07-25T14:58:00+09:00",
    "completedAt": null,
    "updatedAt": "2026-07-25T15:00:00+09:00"
  },
  "timestamp": "2026-07-25T15:00:00+09:00"
}
```

#### 200 OK — 생성 중

```
{
  "success": true,
  "code": "REPORT_STATUS_SUCCESS",
  "message": "리포트 생성 상태 조회에 성공했습니다.",
  "data": {
    "reportId": 48,
    "status": "IN_PROGRESS",
    "progressRate": 70,
    "progressStage": "REPORT_GENERATION",
    "progressMessage": "참여자들의 현장 의견을 분석하고 있습니다.",
    "detailAvailable": false,
    "retryAvailable": false,
    "failReason": null,
    "createdAt": "2026-07-25T14:58:00+09:00",
    "completedAt": null,
    "updatedAt": "2026-07-25T15:00:02+09:00"
  },
  "timestamp": "2026-07-25T15:00:02+09:00"
}
```

#### 200 OK — 생성 완료

```
{
  "success": true,
  "code": "REPORT_STATUS_SUCCESS",
  "message": "리포트 생성 상태 조회에 성공했습니다.",
  "data": {
    "reportId": 48,
    "status": "DONE",
    "progressStage": "COMPLETED",
    "progressRate": 100,
    "progressStage": "COMPLETED",
    "progressMessage": "AI 임장 리포트가 완성되었습니다.",
    "detailAvailable": true,
    "retryAvailable": false,
    "failReason": null,
    "createdAt": "2026-07-25T14:58:00+09:00",
    "completedAt": "2026-07-25T15:01:30+09:00",
    "updatedAt": "2026-07-25T15:01:30+09:00"
  },
  "timestamp": "2026-07-25T15:01:31+09:00"
}
```

#### 200 OK — 생성 실패

```
{
  "success": true,
  "code": "REPORT_STATUS_SUCCESS",
  "message": "리포트 생성 상태 조회에 성공했습니다.",
  "data": {
    "reportId": 48,
    "status": "FAILED",
    "progressRate": 70,
    "progressStage": "REPORT_GENERATION",
    "progressMessage": "리포트 생성에 실패했습니다.",
    "detailAvailable": false,
    "retryAvailable": true,
    "failReason": "AI 분석 결과를 처리하는 중 일시적인 오류가 발생했습니다.",
    "createdAt": "2026-07-25T14:58:00+09:00",
    "completedAt": null,
    "updatedAt": "2026-07-25T15:00:45+09:00"
  },
  "timestamp": "2026-07-25T15:00:46+09:00"
}
```

---

### Response Field

| 필드 | 타입 | 설명 |
| --- | --- | --- |
| `reportId` | Long | 리포트 ID |
| `status` | String | 리포트 생성 상태 |
| `progressRate` | Integer | 생성 진행률, 0~100 |
| `progressStage` | String | 현재 또는 마지막 진행 단계 |
| `progressMessage` | String | 사용자에게 표시할 진행 안내 문구 |
| `detailAvailable` | Boolean | 리포트 상세 조회 가능 여부 |
| `retryAvailable` | Boolean | 실패한 리포트 재생성 가능 여부 |
| `failReason` | String/null | 사용자 표시용 실패 사유 |
| `createdAt` | String | 리포트 행 생성 시각 |
| `completedAt` | String/null | 생성 완료 시각 |
| `updatedAt` | String | 상태 최종 갱신 시각 |

---

### Exception

#### 400 Bad Request — 잘못된 리포트 ID

```
{
  "success": false,
  "code": "COMMON_INVALID_REQUEST",
  "message": "입력값을 확인해 주세요.",
  "data": {
    "field": "reportId",
    "reason": "리포트 ID는 1 이상의 숫자여야 합니다."
  },
  "timestamp": "2026-07-25T15:00:00+09:00"
}
```

---

#### 401 Unauthorized

```
{
  "success": false,
  "code": "AUTH_ACCESS_TOKEN_INVALID",
  "message": "로그인이 필요합니다.",
  "data": null,
  "timestamp": "2026-07-25T15:00:00+09:00"
}
```

---

#### 403 Forbidden — 리포트 접근 권한 없음

```
{
  "success": false,
  "code": "REPORT_ACCESS_DENIED",
  "message": "해당 리포트에 접근할 권한이 없습니다.",
  "data": null,
  "timestamp": "2026-07-25T15:00:00+09:00"
}
```

---

#### 404 Not Found — 리포트 없음

```
{
  "success": false,
  "code": "REPORT_NOT_FOUND",
  "message": "리포트를 찾을 수 없습니다.",
  "data": null,
  "timestamp": "2026-07-25T15:00:00+09:00"
}
```

---

#### 404 Not Found — 활성 회원 정보 없음

탈퇴했거나 삭제된 회원의 기존 Access Token으로 조회한 경우

```json
{
  "success": false,
  "code": "MEMBER_NOT_FOUND",
  "message": "회원 정보를 찾을 수 없습니다.",
  "data": null,
  "timestamp": "2026-07-25T15:00:00+09:00"
}
```

---

#### 500 Internal Server Error

```
{
  "success": false,
  "code": "COMMON_INTERNAL_SERVER_ERROR",
  "message": "일시적인 오류가 발생했습니다.",
  "data": null,
  "timestamp": "2026-07-25T15:00:00+09:00"
}
```

---

### 프론트 처리

```
임장 전체 마감 또는 리포트 생성 알림 수신
→ GET /api/v1/reports/{reportId}/status 호출
→ status, progressStage와 계산된 progressRate를 생성 화면에 표시

status = PENDING 또는 IN_PROGRESS
→ progressStage와 progressMessage 표시
→ 2~5초 간격으로 상태 재조회
→ 화면 이탈 시 불필요한 폴링 중단

status = DONE
→ 상태 조회 중단
→ 완료 애니메이션 또는 안내 표시
→ GET /api/v1/reports/{reportId} 호출
→ 리포트 상세 화면으로 이동

status = FAILED
→ 상태 조회 중단
→ failReason 표시
→ retryAvailable = true이면 재생성 버튼 노출
→ false이면 문의 또는 데이터 확인 안내 표시

REPORT_ACCESS_DENIED
→ "접근할 수 없는 리포트입니다." 안내
→ 내 리포트 목록 또는 이전 화면으로 이동
```

---

## 실패한 리포트 재생성 요청

Method: POST
Progress: 완료
URI: /api/v1/reports/{reportId}/retry
담당자: 박재명
연동여부: No

생성에 실패한 AI 임장 리포트의 재생성을 요청한다.


---

### Request

- Request HTTP Method: `POST`
- 인증 필요: 필요
- Request Body: 없음

#### Request Header

```
Authorization: Bearer {accessToken}
```

#### Path Variable

```
POST /api/v1/reports/48/retry
```

| 필드 | 타입 | 필수 | 설명 |
| --- | --- | --- | --- |
| `reportId` | Long | Y | 재생성을 요청할 리포트 ID |

---

### 처리 기준

#### 1. 회원·리포트·권한 확인

- 로그인한 회원 ID는 Access Token에서 확인한다.
- `reportId`에 해당하는 리포트가 존재하는지 확인한다.
- 재생성 요청 권한은 해당 리포트의 임장 스터디장에게 부여한다.
- 일반 참여자는 실패 상태와 재시도 가능 여부를 확인할 수 있지만 재생성 요청은 할 수 없다.
- 스터디가 취소됐거나 리포트 생성의 필수 원본을 더 이상 사용할 수 없는 경우 재시도할 수 없다.

#### 2. 재생성 가능 상태

- `report.status = FAILED`인 경우에만 재생성을 요청할 수 있다.
- `report.is_retryable = true`여야 한다.
- `retryRequestedAt`이 있는 `PENDING` 또는 `IN_PROGRESS` 상태는 이미 재생성이 진행 중인 요청으로 판단하고 `200 REPORT_RETRY_ALREADY_IN_PROGRESS`를 반환한다.
- `retryRequestedAt`이 없는 `PENDING` 또는 `IN_PROGRESS` 상태는 최초 생성 작업이므로 `409 REPORT_RETRY_NOT_ALLOWED`를 반환한다.
- `DONE` 상태의 완성된 리포트는 이 API로 다시 생성하지 않는다.
- 완성 리포트의 새 버전 생성 기능은 별도 요구사항이 없으므로 MVP 범위에 포함하지 않는다.

#### 3. 원본 데이터 재검증

재생성 전에 다음을 다시 확인한다.

- 리포트 대상 `field_session`이 종료 상태인지
- 체크리스트와 체크리스트 항목이 존재하는지
- 완료 상태 데이터가 조회 가능한지
- 리포트 근거 후보인 `field_record`가 존재하는지
- STT 근거가 필요한 경우 변환 완료 텍스트가 저장되어 있는지
- 대상 아파트와 스터디 정보가 존재하는지

단순 재시도로 해결할 수 없는 원본 데이터 부족이면 `REPORT_SOURCE_DATA_INSUFFICIENT`를 반환한다.

`data.missing`에는 실제 부족한 항목만 다음 고정 값으로 반환한다.

```text
STUDY
APARTMENT
FIELD_SESSION
FIELD_PARTICIPANT
CHECKLIST
CHECKLIST_ITEM
FIELD_RECORD
STT_TEXT
```

미완료 STT가 있어도 사용 가능한 TEXT·DONE STT·PHOTO 원본이 하나 이상이면 미완료 STT는 AI 입력의 품질 정보로 전달하고 재생성을 차단하지 않는다. 사용 가능한 원본이 없고 미완료 STT만 남은 경우에만 `FIELD_RECORD`, `STT_TEXT`를 함께 반환한다.

#### 4. 상태 초기화

재생성 요청을 수락하면 다음처럼 초기화한다.

```
status = PENDING
progressStage = RECORD_COLLECTION
progressRate = 0
failReason = null
completedAt = null
```

- 이전 실패 상태의 불완전한 `resultJson`은 사용자에게 노출하지 않는다.
- 이전 결과가 일부 저장되어 있으면 재생성 성공 전까지 교체 또는 임시 영역에 보관한다.
- 재생성 성공 시 최종 `resultJson`과 `report_evidence`를 원자적으로 교체한다.

#### 5. 중복 요청과 동시성

- 동일 리포트에 생성 작업이 하나만 존재하도록 DB 락, 상태 조건부 갱신 또는 분산 락을 적용한다.
- 첫 요청이 상태를 `PENDING`으로 변경한 뒤 동일 요청이 다시 들어오면 새 작업을 발행하지 않는다.
- 중복 요청에는 현재 생성 상태를 정상 응답으로 반환할 수 있다.
- 스터디당 Report 하나 제약에 맞춰 Kafka 메시지 Key는 `studyId`를 사용한다.
- `REPORT_REQUESTED` payload는 기존 계약대로 `studyId`, `sessionId`, `apartmentId`, `occurredAt`만 포함하며 `reportId`를 추가하지 않는다.

#### 6. 완료·알림·자동 게시글

- 재생성 성공 후 완료 알림은 한 번만 생성한다.
- 기존 완료 리포트에 연결된 자동 게시글이 있는 경우 새 게시글을 중복 생성하지 않고 기존 연결을 유지하거나 내용을 갱신하는 정책을 사용한다.
- 기존 리포트 찜 관계는 유지한다.

---

### Response

#### 202 Accepted — 재생성 요청 수락

`202`는 재생성 요청의 DB 상태 전이가 수락되었음을 뜻한다. 비동기 Kafka 전달 완료나 AI 처리 완료를 보장하지 않으며, 클라이언트는 `statusApi`로 후속 상태를 조회한다.

```
{
  "success": true,
  "code": "REPORT_RETRY_ACCEPTED",
  "message": "리포트 재생성을 시작했습니다.",
  "data": {
    "reportId": 48,
    "status": "PENDING",
    "progressRate": 0,
    "progressStage": "RECORD_COLLECTION",
    "retryRequestedAt": "2026-07-25T15:30:00+09:00",
    "statusApi": "/api/v1/reports/48/status"
  },
  "timestamp": "2026-07-25T15:30:00+09:00"
}
```

#### 200 OK — 이미 재생성 진행 중

```
{
  "success": true,
  "code": "REPORT_RETRY_ALREADY_IN_PROGRESS",
  "message": "리포트 재생성이 이미 진행 중입니다.",
  "data": {
    "reportId": 48,
    "status": "IN_PROGRESS",
    "progressRate": 40,
    "progressStage": "STT_VALIDATION",
    "retryRequestedAt": "2026-07-25T15:30:00+09:00",
    "statusApi": "/api/v1/reports/48/status"
  },
  "timestamp": "2026-07-25T15:30:03+09:00"
}
```

---

### Response Field

| 필드 | 타입 | 설명 |
| --- | --- | --- |
| `reportId` | Long | 기존 리포트 ID |
| `status` | String | 재생성 요청 후 상태 |
| `progressRate` | Integer | 현재 진행률 |
| `progressStage` | String | 현재 진행 단계 |
| `retryRequestedAt` | String | 재생성 요청이 최초 수락된 시각 |
| `statusApi` | String | 상태 조회 API 경로 |

`retryCount`는 최종 ERD v7에 저장 컬럼이 없으므로 응답의 필수 필드로 사용하지 않는다. 재시도 이력이 필요하면 별도 작업 이력 테이블 또는 로그·모니터링 저장소를 사용한다.

---

### Exception

#### 401 Unauthorized

```
{
  "success": false,
  "code": "AUTH_ACCESS_TOKEN_INVALID",
  "message": "로그인이 필요합니다.",
  "data": null,
  "timestamp": "2026-07-25T15:30:00+09:00"
}
```

---

#### 403 Forbidden — 재생성 권한 없음

```
{
  "success": false,
  "code": "REPORT_RETRY_ACCESS_DENIED",
  "message": "리포트를 재생성할 권한이 없습니다.",
  "data": null,
  "timestamp": "2026-07-25T15:30:00+09:00"
}
```

---

#### 404 Not Found — 리포트 없음

```
{
  "success": false,
  "code": "REPORT_NOT_FOUND",
  "message": "리포트를 찾을 수 없습니다.",
  "data": null,
  "timestamp": "2026-07-25T15:30:00+09:00"
}
```

---

#### 409 Conflict — 실패 상태가 아님

```
{
  "success": false,
  "code": "REPORT_RETRY_NOT_ALLOWED",
  "message": "재생성할 수 없는 리포트 상태입니다.",
  "data": {
    "reportId": 48,
    "status": "DONE"
  },
  "timestamp": "2026-07-25T15:30:00+09:00"
}
```

---

#### 409 Conflict — 재시도 불가능한 실패

```
{
  "success": false,
  "code": "REPORT_RETRY_NOT_RETRYABLE",
  "message": "현재 리포트는 다시 생성할 수 없습니다.",
  "data": {
    "reportId": 48,
    "isRetryable": false
  },
  "timestamp": "2026-07-25T15:30:00+09:00"
}
```

---

#### 409 Conflict — 원본 데이터 부족

```
{
  "success": false,
  "code": "REPORT_SOURCE_DATA_INSUFFICIENT",
  "message": "리포트를 생성하기 위한 임장 기록이 부족합니다.",
  "data": {
    "missing": ["FIELD_RECORD"]
  },
  "timestamp": "2026-07-25T15:30:00+09:00"
}
```

---

#### 500 Internal Server Error

```
{
  "success": false,
  "code": "COMMON_INTERNAL_SERVER_ERROR",
  "message": "일시적인 오류가 발생했습니다.",
  "data": null,
  "timestamp": "2026-07-25T15:30:00+09:00"
}
```

---

### 프론트 처리

```
리포트 상태 조회 결과
→ status = FAILED && retryAvailable = true
→ 스터디장에게 재생성 버튼 표시

재생성 버튼 선택
→ 확인 모달 표시
→ POST /api/v1/reports/{reportId}/retry 호출

202 Accepted
→ 재생성 버튼 비활성화
→ 상태 화면으로 전환
→ statusApi 주기적 조회

REPORT_RETRY_ALREADY_IN_PROGRESS
→ 오류 토스트를 표시하지 않음
→ 기존 생성 진행 화면으로 동기화

REPORT_SOURCE_DATA_INSUFFICIENT
→ 부족한 임장 데이터 안내
→ 자동 반복 재시도 금지
```

---

## 리포트 근거 원문 조회

Method: GET
Progress: 완료
URI: /api/v1/reports/{reportId}/evidences/{sourceId}
담당자: 박재명
연동여부: No

리포트 대상 임장 세션에 속한 특정 현장 기록의 원문을 조회한다.

`TEXT`, 변환 완료된 `STT`, `PHOTO` 원문을 동일한 상세 API로 제공한다.
AI 결과에서 직접 인용되지 않은 원문도 조회할 수 있으며, AI 인용 여부와 원문 조회 가능 여부는 분리한다.

---

### Request

- Request HTTP Method: `GET`
- 인증 필요: 필요
- Request Body: 없음

#### Request Header

```
Authorization: Bearer {accessToken}
```

#### Path Variable

```
GET /api/v1/reports/48/evidences/214
```

| 필드 | 타입 | 필수 | 설명 |
| --- | --- | --- | --- |
| `reportId` | Long | Y | 원문 범위와 접근 권한을 결정할 리포트 ID |
| `sourceId` | Long | Y | 조회할 현장 기록 ID, `field_record.id`와 동일 |

---

### 처리 기준

#### 1. 리포트 접근 권한

- 로그인한 회원 ID는 Access Token에서 확인한다.
- 리포트는 `status = DONE`, `progressStage = COMPLETED`이고 `completedAt`이 존재해야 한다.
- 로그인 회원이 `report.field_session_id`와 정확히 같은 세션의 `field_participant`에 존재해야 한다.
- 현재 `study_member`의 `ACTIVE` 여부만으로 원문 접근 권한을 부여하거나 거부하지 않는다.
- 원문 존재 여부가 노출되지 않도록 참여자 권한을 `sourceId` 조회보다 먼저 확인한다.
- 비참여자는 `403 REPORT_EVIDENCE_ACCESS_DENIED`로 처리한다.

#### 2. 리포트와 sourceId 범위 검증

- `sourceId`에 해당하는 삭제되지 않은 `field_record`가 존재해야 한다.
- `field_record.session_id`는 `report.field_session_id`와 정확히 일치해야 한다.
- 기록 작성자는 같은 세션의 `field_participant`여야 하며 체크리스트·파일 연결도 해당 세션과 일치해야 한다.
- `report_evidence` 연결은 원문 조회의 필수조건이 아니다.
- `report_evidence`는 AI가 해당 원문을 직접 인용한 경우 `usedIn`을 구성하는 용도로만 사용한다.
- AI가 인용하지 않은 `TEXT`, `STT`, `PHOTO`도 조회할 수 있으며 `usedIn`은 빈 배열이다.
- 같은 스터디라도 다른 임장 세션에 속한 `sourceId`는 `REPORT_EVIDENCE_REPORT_MISMATCH`로 처리한다.
- 존재하지 않거나 삭제된 기록은 `REPORT_EVIDENCE_NOT_FOUND`로 처리한다.

#### 3. sourceType별 원문

##### `TEXT`

- 삭제되지 않고 비어 있지 않은 `field_record.text_content` 전체를 절단 없이 반환한다.
- `media`, `sttStatus`는 `null`이다.
- AI 미인용 기록이면 `usedIn = []`이다.

##### `PHOTO`

- `photo_file_id`로 연결된 사진 메타데이터와 짧은 유효기간의 접근 URL을 반환한다.
- 파일의 소유자, Study, 용도가 현장 기록과 일치해야 한다.
- 내부 S3 Key와 EXIF 위치정보는 반환하지 않는다.
- 현재 MVP에서 PHOTO는 AI 직접 근거로 저장하지 않으므로 `usedIn`은 항상 빈 배열이다.

##### `STT`

- `sttStatus = DONE`이고 변환 텍스트가 존재하는 기록만 반환한다.
- `field_record.text_content` 전체를 절단 없이 반환한다.
- 처리용 음성 원본 URL은 반환하지 않는다.
- AI 미인용 기록이면 `usedIn = []`이다.

#### 4. 참여자 익명화

- 작성자의 실제 회원 ID와 닉네임 대신 리포트 내부 익명 라벨을 반환한다.
- 같은 리포트의 다른 화면과 동일한 참여자에게 동일한 라벨을 사용한다.
- 운영·디버깅용 내부 회원 식별자를 응답에 포함하지 않는다.

#### 5. 삭제·접근 불가 파일

- 원문 기록이 Soft Delete 상태이면 일반 사용자에게 원문을 반환하지 않는다.
- 정상 PHOTO 응답의 `media.available`은 `true`다.
- 사진 파일이 없거나 삭제·만료되었거나 파일 자체에 접근할 수 없으면 `410 REPORT_EVIDENCE_MEDIA_UNAVAILABLE`을 반환한다.
- 파일 저장소 비활성화·네트워크 장애·잘못된 URL 발급 응답처럼 저장소 자체가 일시적으로 정상 동작하지 않으면 `503 MEDIA_GATEWAY_UNAVAILABLE`을 반환한다.
- 파일 접근 URL은 짧은 유효기간의 Presigned URL로 반환하며 만료 후 이 API를 다시 호출한다.

#### 6. AI 인용 위치

- `usedIn`은 원문 조회 권한이 아니라 AI 직접 인용 여부를 표현한다.
- `report_evidence`로 연결된 `TEXT` 또는 `STT`만 인용 위치를 반환한다.
- 연결되지 않은 `TEXT`·`STT`와 모든 `PHOTO`는 `usedIn = []`이다.
- 각 항목에는 `claimKey`, `resultSection`, `resultKey`를 반환한다.
- 하나의 원문이 여러 AI 문장에 연결되면 `usedIn`에 여러 항목을 반환한다.

민감한 원문 응답에는 `Cache-Control: no-store`를 적용한다.

---

### Response

#### 200 OK — 텍스트 근거

```
{
  "success": true,
  "code": "REPORT_EVIDENCE_DETAIL_SUCCESS",
  "message": "리포트 근거 원문 조회에 성공했습니다.",
  "data": {
    "reportId": 48,
    "sourceId": 201,
    "sourceType": "TEXT",
    "category": "교통",
    "checklistItem": {
      "checklistItemId": 501,
      "title": "지하철역 접근성",
      "subtitle": "단지 출입구에서 역 개찰구까지 실제 이동 시간을 확인해 주세요."
    },
    "participantLabel": "참여자 1",
    "textContent": "단지 출입구에서 옥수역 개찰구까지 보통 걸음으로 약 8분이 걸렸습니다. 큰 횡단보도는 한 번 건넜습니다.",
    "media": null,
    "sttStatus": null,
    "usedIn": [
      {
        "claimKey": "feature.positive.transport",
        "resultSection": "TOP_POSITIVE_FEATURE",
        "resultKey": "지하철 접근성"
      }
    ],
    "recordedAt": "2026-07-20T14:30:00+09:00"
  },
  "timestamp": "2026-07-25T15:25:00+09:00"
}
```

#### 200 OK — 사진 근거

```
{
  "success": true,
  "code": "REPORT_EVIDENCE_DETAIL_SUCCESS",
  "message": "리포트 근거 원문 조회에 성공했습니다.",
  "data": {
    "reportId": 48,
    "sourceId": 214,
    "sourceType": "PHOTO",
    "category": "단지환경",
    "checklistItem": {
      "checklistItemId": 503,
      "title": "단지 진입 경사",
      "subtitle": "유모차·휠체어·고령자 보행 관점에서 경사를 확인해 주세요."
    },
    "participantLabel": "참여자 3",
    "textContent": null,
    "media": {
      "available": true,
      "fileId": 301,
      "originalName": "complex-slope.jpg",
      "contentType": "image/jpeg",
      "sizeBytes": 1852034,
      "accessUrl": "https://s3.example.com/presigned/evidence-301",
      "expiresAt": "2026-07-25T15:35:00+09:00"
    },
    "sttStatus": null,
    "usedIn": [],
    "recordedAt": "2026-07-20T14:45:00+09:00"
  },
  "timestamp": "2026-07-25T15:25:00+09:00"
}
```

#### 200 OK — STT 근거

```
{
  "success": true,
  "code": "REPORT_EVIDENCE_DETAIL_SUCCESS",
  "message": "리포트 근거 원문 조회에 성공했습니다.",
  "data": {
    "reportId": 48,
    "sourceId": 228,
    "sourceType": "STT",
    "category": "소음",
    "checklistItem": {
      "checklistItemId": 509,
      "title": "시간대별 소음",
      "subtitle": "차량·학교·상가 소음과 재확인이 필요한 시간대를 기록해 주세요."
    },
    "participantLabel": "참여자 2",
    "textContent": "현재 시간은 오후 세 시이고 차량 소음은 크지 않습니다. 다만 출퇴근 시간에는 다시 확인할 필요가 있어 보입니다.",
    "media": null,
    "sttStatus": "DONE",
    "usedIn": [
      {
        "claimKey": "category.noise",
        "resultSection": "CATEGORY_OPINION",
        "resultKey": "소음"
      }
    ],
    "recordedAt": "2026-07-20T15:05:00+09:00"
  },
  "timestamp": "2026-07-25T15:25:00+09:00"
}
```

---

### Response Field

| 필드 | 타입 | 설명 |
| --- | --- | --- |
| `reportId` | Long | 리포트 ID |
| `sourceId` | Long | 현장 기록 ID |
| `sourceType` | String | `TEXT`, `PHOTO`, `STT` |
| `category` | String | 동적 체크리스트 카테고리 |
| `checklistItem` | Object | 연결된 체크리스트 항목 |
| `participantLabel` | String | 익명 참여자 라벨 |
| `textContent` | String | null | TEXT 또는 DONE STT 원문 전체. 임의 절단하지 않음 |
| `media` | Object | null | PHOTO의 검증된 파일 메타데이터와 접근 정보 |
| `sttStatus` | String | null | STT이면 `DONE`, 그 외에는 `null` |
| `usedIn` | Array | AI 직접 인용 위치. 미인용 원문과 PHOTO는 빈 배열 |
| `recordedAt` | String | 현장 기록 시각 |

#### `usedIn[]`

| 필드 | 타입 | 설명 |
| --- | --- | --- |
| `claimKey` | String | AI 문장 식별 키 |
| `resultSection` | String | 결과 내 사용 영역 |
| `resultKey` | String | 특징 label 또는 동적 category |

#### `media`

| 필드 | 타입 | 설명 |
| --- | --- | --- |
| `available` | Boolean | 정상 200 응답에서는 `true` |
| `fileId` | Long | 사진 파일 메타 ID |
| `originalName` | String | 원본 파일명 |
| `contentType` | String | 이미지 MIME Type |
| `sizeBytes` | Long | 파일 크기 |
| `accessUrl` | String | 짧은 유효기간의 접근 URL |
| `expiresAt` | String | 접근 URL 만료 시각 |

---

### Exception

#### 400 Bad Request — 잘못된 ID

```
{
  "success": false,
  "code": "COMMON_INVALID_REQUEST",
  "message": "입력값을 확인해 주세요.",
  "data": {
    "field": "sourceId",
    "reason": "sourceId는 1 이상의 숫자여야 합니다."
  },
  "timestamp": "2026-07-25T15:25:00+09:00"
}
```

---

#### 400 Bad Request — 다른 리포트의 근거

```
{
  "success": false,
  "code": "REPORT_EVIDENCE_REPORT_MISMATCH",
  "message": "요청한 근거가 해당 리포트와 일치하지 않습니다.",
  "data": null,
  "timestamp": "2026-07-25T15:25:00+09:00"
}
```

---

#### 401 Unauthorized

```
{
  "success": false,
  "code": "AUTH_ACCESS_TOKEN_INVALID",
  "message": "로그인이 필요합니다.",
  "data": null,
  "timestamp": "2026-07-25T15:25:00+09:00"
}
```

---

#### 403 Forbidden — 원문 접근 권한 없음

```
{
  "success": false,
  "code": "REPORT_EVIDENCE_ACCESS_DENIED",
  "message": "리포트 원문 근거를 조회할 권한이 없습니다.",
  "data": null,
  "timestamp": "2026-07-25T15:25:00+09:00"
}
```

---

#### 404 Not Found — 리포트 없음

```
{
  "success": false,
  "code": "REPORT_NOT_FOUND",
  "message": "리포트를 찾을 수 없습니다.",
  "data": null,
  "timestamp": "2026-07-25T15:25:00+09:00"
}
```

---

#### 404 Not Found — 근거 없음

```
{
  "success": false,
  "code": "REPORT_EVIDENCE_NOT_FOUND",
  "message": "리포트 근거를 찾을 수 없습니다.",
  "data": null,
  "timestamp": "2026-07-25T15:25:00+09:00"
}
```

---

#### 410 Gone — 사진 파일 접근 불가

사진 원문 파일이 보존 정책에 따라 삭제된 경우

```
{
  "success": false,
  "code": "REPORT_EVIDENCE_MEDIA_UNAVAILABLE",
  "message": "근거 사진 파일을 더 이상 확인할 수 없습니다.",
  "data": {
    "sourceId": 214,
    "sourceType": "PHOTO"
  },
  "timestamp": "2026-07-25T15:25:00+09:00"
}
```

---

#### 503 Service Unavailable — 파일 저장소 일시 장애

```json
{
  "success": false,
  "code": "MEDIA_GATEWAY_UNAVAILABLE",
  "message": "파일 저장소에 일시적으로 연결할 수 없습니다.",
  "data": null,
  "timestamp": "2026-07-25T15:25:00+09:00"
}
```

---

#### 500 Internal Server Error

```
{
  "success": false,
  "code": "COMMON_INTERNAL_SERVER_ERROR",
  "message": "일시적인 오류가 발생했습니다.",
  "data": null,
  "timestamp": "2026-07-25T15:25:00+09:00"
}
```

---

### 프론트 처리

```
근거 목록에서 항목 선택
→ GET /api/v1/reports/{reportId}/evidences/{sourceId} 호출
→ sourceType에 맞는 원문 UI 표시

sourceType = TEXT 또는 STT
→ textContent 전체 표시
→ STT는 음성 재생 버튼을 표시하지 않음

sourceType = PHOTO && media.available = true
→ accessUrl로 사진 확대 보기
→ URL 만료 시 원문 API 재호출

REPORT_EVIDENCE_ACCESS_DENIED
→ "원문 근거는 임장 참여자만 확인할 수 있습니다." 안내
→ 완료 리포트 상세로 복귀

REPORT_EVIDENCE_MEDIA_UNAVAILABLE
→ 텍스트 메타정보는 유지
→ 사진 영역에 "보관 기간이 지나 확인할 수 없습니다." 표시
```

---

## 리포트 근거 목록 조회

Method: GET
Progress: 완료
URI: /api/v1/reports/{reportId}/evidences
담당자: 박재명
연동여부: No

AI 임장 리포트의 문장과 주요 요소에 실제로 연결된 현장 기록 근거 목록을 조회한다.

근거는 텍스트 메모, 사진 기록, STT 변환 텍스트로 구성되며, 각 `sourceId`는 최종 ERD의 `field_record.id`와 동일하다.

> 현재 완료 저장 계약에서 AI 직접 근거로 `report_evidence`에 저장되는 유형은
> `TEXT`, `STT`뿐이다. 기존 프론트 계약 호환을 위해 `PHOTO` 필터와 media 응답 필드는
> 유지하지만, 현재 정상 완료 리포트의 `PHOTO` 필터 결과는 빈 목록이고 `photoCount=0`이다.
> 세션의 raw PHOTO를 AI 직접 근거 목록에 임의로 섞지 않는다.

---

### Request

- Request HTTP Method: `GET`
- 인증 필요: 필요
- Request Body: 없음

#### Request Header

```
Authorization: Bearer {accessToken}
```

#### Path Variable

```
GET /api/v1/reports/48/evidences
```

| 필드 | 타입 | 필수 | 설명 |
| --- | --- | --- | --- |
| `reportId` | Long | Y | 근거 목록을 조회할 리포트 ID |

#### Query Parameter

첫 조회:

```
GET /api/v1/reports/48/evidences?sourceType=TEXT&category=교통&size=20
```

다음 조회:

```
GET /api/v1/reports/48/evidences?sourceType=TEXT&category=교통&cursor=214&size=20
```

| 필드 | 타입 | 필수 | 기본값 | 설명 |
| --- | --- | --- | --- | --- |
| `sourceType` | String | N | 전체 | `TEXT`, `PHOTO`, `STT` 근거 유형 필터 |
| `category` | String | N | 전체 | 체크리스트 동적 카테고리명 필터 |
| `sourceIds` | String | N | 없음 | 특정 sourceId 목록 조회. 쉼표로 구분하며 중복 제거 후 최대 100개, 예: `201,205,214` |
| `cursor` | Long | N | 없음 | 다음 조회에 사용할 마지막 sourceId |
| `size` | Integer | N | `20` | 한 번에 조회할 근거 수, 1~100 |

`sourceIds`는 리포트 상세의 특정 긍정·주의 요소 또는 의견에 연결된 근거만 표시할 때 사용한다.

---

### 근거 유형

| 값 | 설명 |
| --- | --- |
| `TEXT` | 임장 중 직접 작성한 텍스트 메모 |
| `PHOTO` | 체크리스트 항목에서 촬영·업로드한 사진 기록 |
| `STT` | 음성 원본을 변환해 저장한 텍스트 기록 |

최종 리포트 근거 API에서는 `CHECKLIST_ANSWER`, `APARTMENT_INFO`, `APARTMENT_TRANSACTION`, `PUBLIC_DATA`를 근거 유형으로 사용하지 않는다. 리포트의 참여자 근거는 `report_evidence → field_record`로 직접 연결한다.

`PHOTO`는 향후 직접 근거 저장 계약 확장을 위한 호환 값이다. 현재의 raw PHOTO와
체크리스트 완료 상태는 별도 사용자 원본 조회 계약에서 다루며 이 목록에 포함하지 않는다.

---

### 처리 기준

#### 1. 회원·리포트·참여 권한 확인

- 로그인한 회원 ID는 Access Token에서 확인한다.
- `reportId`에 해당하는 리포트가 존재하는지 확인한다.
- 리포트 상태가 `DONE`인지 확인한다.
- 원문 근거 목록은 해당 임장 스터디의 승인 참여자만 조회할 수 있다.
- 강퇴·탈퇴된 멤버는 원문 근거에 접근할 수 없다.

#### 2. 실제 사용 근거만 반환

- `report_evidence.report_id = reportId`로 연결된 source만 반환한다.
- 리포트 생성 대상이었더라도 최종 결과에서 사용되지 않은 현장 기록은 반환하지 않는다.
- `sourceId`는 `field_record.id`와 동일하다.
- 다른 리포트의 근거 또는 같은 스터디의 사용되지 않은 기록을 섞어 반환하지 않는다.

#### 3. 원문 미리보기와 개인정보 보호

- 목록에는 전체 원문 대신 안전하게 잘라낸 `preview`를 반환한다.
- `preview`는 공백을 정규화하고 Unicode code point 기준 최대 200자로 자른다.
- 작성자의 실제 회원 ID와 닉네임 대신 리포트 내부 익명 라벨을 반환한다.
- 사진 위치정보(EXIF), 내부 S3 Key, 음성 원본 경로는 반환하지 않는다.
- STT 근거에는 변환 텍스트만 반환하며 음성 원본 URL은 제공하지 않는다.
- 민감정보가 포함된 기록은 마스킹 또는 비공개 처리할 수 있다.

#### 4. 사진 접근 URL

- `PHOTO` 근거의 썸네일 또는 미리보기 URL은 짧은 유효기간의 접근 URL로 반환한다.
- 파일이 삭제됐거나 접근 불가능하면 `mediaAvailable = false`, URL은 `null`로 반환한다.
- 사진 접근 실패가 전체 근거 목록 조회 실패로 이어지지 않도록 한다.

#### 5. 정렬·페이지네이션

- 기본 정렬은 리포트 결과에서 사용된 순서, 카테고리·항목 표시 순서, 기록 생성 순서를 고려한 고정 순서를 사용한다.
- 커서 구현을 단순화하는 경우 `sourceId ASC` 또는 `sourceId DESC` 중 한 방식으로 문서와 쿼리를 통일한다.
- 본 명세에서는 `sourceId ASC`로 반환한다.
- `cursor`가 있으면 `sourceId > cursor`인 다음 근거를 조회한다.
- `size + 1`건을 조회해 `hasNext`를 판단한다.

#### 6. 필터 검증

- `sourceType`은 `TEXT`, `PHOTO`, `STT` 중 하나여야 한다.
- `sourceIds`를 전달하면 모든 ID가 1 이상의 Long인지 검증하고 중복을 제거하며, 한 번에 최대 100개까지 허용한다.
- `sourceIds`에 해당 리포트의 유효한 직접 근거가 아닌 ID가 하나라도 포함되면 `REPORT_EVIDENCE_REPORT_MISMATCH`로 처리한다.
- `category`는 고정 enum으로 검증하지 않고 동적 카테고리의 정확한 문자열 필터로 사용한다. 일치하는 직접 근거가 없으면 빈 목록을 반환한다.

---

### Response

#### 200 OK

```
{
  "success": true,
  "code": "REPORT_EVIDENCE_LIST_SUCCESS",
  "message": "리포트 근거 목록 조회에 성공했습니다.",
  "data": {
    "reportId": 48,
    "filters": {
      "sourceType": null,
      "category": null,
      "sourceIds": []
    },
    "content": [
      {
        "sourceId": 201,
        "sourceType": "TEXT",
        "category": "교통",
        "checklistItem": {
          "checklistItemId": 501,
          "title": "지하철역 접근성",
          "subtitle": "단지 출입구에서 역 개찰구까지 실제 이동 시간을 확인해 주세요."
        },
        "participantLabel": "참여자 1",
        "preview": "단지 출입구에서 역 개찰구까지 약 8분이 걸렸습니다.",
        "media": null,
        "mediaAvailable": false,
        "originalAvailable": true,
        "usedIn": [
          {
            "claimKey": "feature.positive.transport",
            "resultSection": "TOP_POSITIVE_FEATURE",
            "resultKey": "지하철 접근성"
          },
          {
            "claimKey": "category.transport.p1",
            "resultSection": "CATEGORY_OPINION",
            "resultKey": "교통"
          }
        ],
        "recordedAt": "2026-07-20T14:30:00+09:00"
      },
      {
        "sourceId": 228,
        "sourceType": "STT",
        "category": "소음",
        "checklistItem": {
          "checklistItemId": 509,
          "title": "시간대별 소음",
          "subtitle": "차량·학교·상가 소음과 재확인이 필요한 시간대를 기록해 주세요."
        },
        "participantLabel": "참여자 2",
        "preview": "현재 시간은 오후 세 시이고 차량 소음은 크지 않습니다. 다만 출퇴근 시간에는 다시 확인할 필요가 있습니다.",
        "media": null,
        "mediaAvailable": false,
        "originalAvailable": true,
        "usedIn": [
          {
            "claimKey": "category.noise.p2",
            "resultSection": "CATEGORY_OPINION",
            "resultKey": "소음"
          }
        ],
        "recordedAt": "2026-07-20T15:05:00+09:00"
      }
    ],
    "summary": {
      "totalEvidenceCount": 16,
      "textCount": 12,
      "photoCount": 0,
      "sttCount": 4
    },
    "nextCursor": 228,
    "hasNext": true
  },
  "timestamp": "2026-07-25T15:20:00+09:00"
}
```

#### 근거가 없는 경우

```
{
  "success": true,
  "code": "REPORT_EVIDENCE_LIST_SUCCESS",
  "message": "리포트 근거 목록 조회에 성공했습니다.",
  "data": {
    "reportId": 48,
    "filters": {
      "sourceType": "PHOTO",
      "category": "소음",
      "sourceIds": []
    },
    "content": [],
    "summary": {
      "totalEvidenceCount": 16,
      "textCount": 12,
      "photoCount": 0,
      "sttCount": 4
    },
    "nextCursor": null,
    "hasNext": false
  },
  "timestamp": "2026-07-25T15:20:00+09:00"
}
```

---

### Response Field

| 필드 | 타입 | 설명 |
| --- | --- | --- |
| `reportId` | Long | 리포트 ID |
| `filters` | Object | 적용된 필터 |
| `content` | Array | 실제 사용 근거 목록 |
| `summary` | Object | 리포트 전체 근거 유형별 개수 |
| `nextCursor` | Long | null | 다음 조회에 사용할 sourceId |
| `hasNext` | Boolean | 다음 목록 존재 여부 |

#### `content[]`

| 필드 | 타입 | 설명 |
| --- | --- | --- |
| `sourceId` | Long | `field_record.id`와 동일한 근거 ID |
| `sourceType` | String | `TEXT`, `PHOTO`, `STT` |
| `category` | String | 동적 체크리스트 카테고리 |
| `checklistItem` | Object | 연결된 체크리스트 항목 요약 |
| `participantLabel` | String | 익명 참여자 라벨 |
| `preview` | String | 목록 표시용 근거 미리보기 |
| `media` | Object | null | 사진 썸네일 접근 정보 |
| `mediaAvailable` | Boolean | 사진 접근 가능 여부 |
| `originalAvailable` | Boolean | 원문 상세 조회 가능 여부 |
| `usedIn` | Array | 리포트 결과에서 사용된 위치 |
| `recordedAt` | String | 현장 기록 작성 시각 |

#### `usedIn[]`

| 필드 | 타입 | 설명 |
| --- | --- | --- |
| `claimKey` | String | 동일 source가 사용된 AI 문장을 구분하는 키 |
| `resultSection` | String | `TOP_POSITIVE_FEATURE`, `TOP_CAUTION_FEATURE`, `COMMON_OPINION`, `CONFLICTING_OPINION`, `CATEGORY_OPINION` |
| `resultKey` | String | 특징·의견 label 또는 동적 category |

`summary`는 필터·커서와 무관한 리포트 전체의 유효한 고유 직접 근거 수를 반환한다.
현재 정상 완료 계약에서는 `photoCount`가 항상 `0`이다.

---

### Exception

#### 400 Bad Request — 잘못된 근거 유형

```
{
  "success": false,
  "code": "REPORT_EVIDENCE_SOURCE_TYPE_INVALID",
  "message": "유효하지 않은 근거 유형입니다.",
  "data": {
    "field": "sourceType",
    "allowedValues": ["TEXT", "PHOTO", "STT"]
  },
  "timestamp": "2026-07-25T15:20:00+09:00"
}
```

---

#### 400 Bad Request — 잘못된 sourceIds

```
{
  "success": false,
  "code": "COMMON_INVALID_REQUEST",
  "message": "입력값을 확인해 주세요.",
  "data": {
    "field": "sourceIds",
    "reason": "sourceIds는 1 이상의 숫자를 쉼표로 구분해 전달해야 합니다."
  },
  "timestamp": "2026-07-25T15:20:00+09:00"
}
```

---

#### 401 Unauthorized

```
{
  "success": false,
  "code": "AUTH_ACCESS_TOKEN_INVALID",
  "message": "로그인이 필요합니다.",
  "data": null,
  "timestamp": "2026-07-25T15:20:00+09:00"
}
```

---

#### 403 Forbidden — 원문 근거 접근 권한 없음

```
{
  "success": false,
  "code": "REPORT_EVIDENCE_ACCESS_DENIED",
  "message": "리포트 원문 근거를 조회할 권한이 없습니다.",
  "data": null,
  "timestamp": "2026-07-25T15:20:00+09:00"
}
```

---

#### 404 Not Found — 리포트 없음

```
{
  "success": false,
  "code": "REPORT_NOT_FOUND",
  "message": "리포트를 찾을 수 없습니다.",
  "data": null,
  "timestamp": "2026-07-25T15:20:00+09:00"
}
```

---

#### 409 Conflict — 생성 미완료

```
{
  "success": false,
  "code": "REPORT_NOT_DONE",
  "message": "생성이 완료된 리포트의 근거만 조회할 수 있습니다.",
  "data": {
    "status": "IN_PROGRESS"
  },
  "timestamp": "2026-07-25T15:20:00+09:00"
}
```

---

#### 500 Internal Server Error

```
{
  "success": false,
  "code": "COMMON_INTERNAL_SERVER_ERROR",
  "message": "일시적인 오류가 발생했습니다.",
  "data": null,
  "timestamp": "2026-07-25T15:20:00+09:00"
}
```

---

### 프론트 처리

```
리포트 상세에서 주요 요소 또는 카테고리의 근거 보기 선택
→ 해당 sourceIds를 Query Parameter로 전달
→ 연결된 근거만 목록 표시

전체 근거 보기 선택
→ sourceIds 없이 목록 조회
→ 카테고리·TEXT/PHOTO/STT 필터 제공

originalAvailable = true
→ 근거 항목 선택 가능
→ GET /api/v1/reports/{reportId}/evidences/{sourceId} 호출

mediaAvailable = true
→ 사진 썸네일 표시
→ 만료된 URL 오류가 발생하면 목록 또는 원문 API 재조회

REPORT_EVIDENCE_ACCESS_DENIED
→ 완료 리포트 요약 화면은 유지
→ "원문 근거는 임장 참여자만 확인할 수 있습니다." 안내
```

---

## 리포트 상세 조회

Method: GET
Progress: 완료
URI: /api/v1/reports/{reportId}
담당자: 박재명
연동여부: No

생성이 완료된 스토리형 AI 임장 리포트의 상세 내용을 조회한다.

전체 체크 개수와 평균 완료율, 주요 긍정·주의 요소, 공통·상반 의견, 카테고리별 참여자 인원·비율, 스터디원별 익명 의견 요약, 연결된 근거 ID, 대상 아파트와 스터디 정보를 반환한다.

---

### Request

- Request HTTP Method: `GET`
- 인증 필요: 필요
- Request Body: 없음

#### Request Header

```
Authorization: Bearer {accessToken}
```

#### Path Variable

```
GET /api/v1/reports/48
```

| 필드 | 타입 | 필수 | 설명 |
| --- | --- | --- | --- |
| `reportId` | Long | Y | 조회할 리포트 ID |

---

### 처리 기준

#### 1. 회원 및 리포트 확인

- 로그인한 회원 ID는 Access Token에서 확인한다.
- `reportId`에 해당하는 리포트가 존재하는지 확인한다.
- 삭제되거나 유효하지 않은 리포트는 조회할 수 없다.
- 리포트가 `DONE` 상태인지 확인한다.
- `PENDING` 또는 `IN_PROGRESS`이면 상세 결과를 반환하지 않고 생성 상태 조회 API를 사용하도록 안내한다.
- `FAILED`이면 실패 상태와 재시도 가능 여부를 반환하는 오류로 처리한다.

#### 2. 접근 권한과 개인정보 보호

- `DONE` 상태 리포트 요약은 로그인 회원이 조회할 수 있다.
- 원문 근거 조회 권한은 별도 근거 API에서 다시 검증한다.

#### 3. 스토리형 리포트 구성

리포트는 단순 점수형 평가가 아니라 다음 순서의 스토리 구조로 반환한다.

```
대상 아파트·임장 개요
→ 임장 한눈에 보기
→ 가장 많이 언급된 긍정 요소
→ 가장 많이 언급된 주의 요소
→ 여러 참여자의 공통 의견·긍정과 주의가 공존하는 상반 의견
→ 카테고리별 의견
→ 스터디원별 익명 의견 펼치기
→ 근거 보기·찜
```

- `overallScore`, 카테고리별 5점 점수와 같은 단순 점수 중심 구조는 사용하지 않는다.
- 긍정 요소와 주의 요소는 실제 참여자 기록에서 언급한 고유 참여자 수를 `mentionCount`로 반환한다.
- `mentionRate`를 포함한 모든 참여자 비율의 분모는 리포트가 참조하는 최종 `field_session`의 전체 `field_participant` 수다.
- 동일 참여자의 사실상 동일한 표현을 중복 언급으로 계산할지는 AI 분석·집계 규칙에서 통일한다.
- 근거가 부족한 내용을 임의로 단정하지 않는다.

#### 4. 체크리스트 통계

- `totalChecklistItemCount`는 리포트 생성 대상 참여자들의 전체 체크리스트 항목 수다.
- `completedChecklistItemCount`는 완료 처리된 항목의 전체 수다.
- `averageCompletionRate`는 참여자별 완료율의 평균 또는 전체 완료 수/전체 항목 수 중 팀에서 확정한 계산식을 일관되게 사용한다.
- 본 명세에서는 다음 계산을 사용한다.

```
averageCompletionRate
= completedChecklistItemCount / totalChecklistItemCount × 100
```

- 항목이 0개인 경우 0으로 나누지 않고 `averageCompletionRate = 0.0`으로 반환한다.

#### 5. 주요 긍정·주의 요소

- `topPositiveFeatures`에는 반복적으로 긍정 평가된 핵심 요소를 반환한다.
- `topCautionFeatures`에는 반복적으로 주의가 필요하다고 언급된 핵심 요소를 반환한다.
- 각 요소는 표시 문구, 한 줄 설명, 언급 횟수와 근거 `sourceIds`를 포함한다.
- 각 요소의 `mentionRate`는 `mentionCount / 전체 field_participant 수 × 100`이며 소수 첫째 자리로 반올림한다.
- `sourceIds`는 실제 `field_record.id`이며 리포트 참여자가 근거 목록·원문 API에서 확인할 수 있다.
- 완료 리포트를 조회하는 비참여자에게도 요약과 언급 횟수는 보이지만, 원문 근거 열람 권한은 부여하지 않는다.

#### 6. 공통·상반 의견

- `commonOpinions`는 둘 이상의 참여자가 같은 방향으로 언급한 공통 의견이다.
- `participantCount`는 해당 의견을 언급한 고유 참여자 수이고 `participantRate`는 전체 `field_participant` 수를 분모로 계산한다.
- `conflictingOpinions`는 같은 특징에 긍정·주의 의견이 함께 존재하는 경우다.
- 긍정·주의 인원과 비율은 각각 독립적으로 계산하므로 두 비율의 합이 100%일 필요는 없다.
- 상반 의견의 `sourceIds`는 현재 저장 구조상 긍정·주의 근거를 합친 목록으로 반환한다.

#### 7. 카테고리별 의견

- 체크리스트 카테고리는 고정 enum이 아니라 생성 시점에 저장된 동적 문자열이다.
- 리포트는 실제 체크리스트에 존재한 카테고리 순서를 유지한다.
- 각 카테고리에 긍정·주의 의견의 고유 참여자 수와 독립 비율, 의견 미기록 인원·비율, 카테고리 요약과 참여자별 익명 의견을 반환한다.
- `unrecordedOpinionCount`는 전체 참여자 수에서 해당 카테고리에 유효한 긍정 또는 주의 의견을 남긴 참여자 합집합 수를 뺀 값이다.
- 특정 카테고리의 근거가 부족하면 `dataSufficient = false`로 표시하고 추정 내용을 생성하지 않는다.
- `dataSufficient = false`여도 실제 존재하는 참여자 의견과 집계는 유지한다. 단, AI가 직접 연결한 근거 claim은 생성하지 않으므로 해당 의견의 `sourceIds`는 빈 배열이다.
- 명확한 긍정·주의 의견으로 분류되지 않는 사실형·중립형 TEXT·STT도 누락하지 않고,
  실제 입력을 근거로 해당 카테고리 요약에 반영한다. `dataSufficient = false`는 기록
  자체를 숨기는 값이 아니라 단정적 분석을 제한하는 값이다.

#### 8. 참여자 의견 익명화

- 참여자 의견에는 실제 `memberId`, 닉네임, 이메일, 프로필 이미지를 포함하지 않는다.
- 동일 리포트 안에서만 일관된 `participantLabel`을 사용한다.

```
참여자 1
참여자 2
참여자 3
```

- 참여자 원문은 이 API가 아니라 근거 원문 API에서 접근 권한을 검증한 뒤 조회한다.

#### 9. 근거 연결

- 리포트 결과의 `sourceIds`는 `report_evidence`를 통해 실제 `field_record`와 연결되어야 한다.
- 존재하지 않는 sourceId를 생성하거나 다른 리포트의 기록을 연결하면 안 된다.
- 사진은 이미지 자체를 AI 비전 분석한 것으로 단정하지 않고, 사진 기록이 존재했다는 출처 메타데이터와 참여자 텍스트 문맥을 구분한다.
- STT 근거는 음성 원본이 아니라 변환 완료된 텍스트를 사용한다.
- `evidenceCount`는 여러 claim에 중복 연결된 같은 원본을 한 번만 세는 고유 `field_record.id` 수다.
- `hasAiEvidence`는 고유 직접 근거가 하나 이상 존재하는지 나타낸다.

#### 10. 찜·커뮤니티 연결

- 현재 로그인 회원의 리포트 찜 여부를 `favoritedByMe`로 반환한다.
- 현재 회원이 이 리포트를 찜할 수 있는지 `canFavorite`으로 반환한다.
- 완료 리포트에 자동 생성된 정보 게시글이 있으면 `postId`를 반환한다.

---

### Response

#### 200 OK

```
{
  "success": true,
  "code": "REPORT_DETAIL_SUCCESS",
  "message": "리포트 상세 조회에 성공했습니다.",
  "data": {
     "reportId": 48,
     "status": "DONE",
     "progressStage": "COMPLETED",
     "title": "래미안 옥수 리버젠 임장 리포트",
    "summary": "옥수역 접근성과 생활 편의시설은 여러 참여자가 공통적으로 긍정 평가했습니다. 다만 단지 진입 경사와 출퇴근 시간대 차량 혼잡은 실제 생활 전 추가 확인이 필요합니다.",
    "apartment": {
      "apartmentId": 15,
      "name": "래미안 옥수 리버젠",
      "address": "서울특별시 성동구 매봉길 15",
      "householdCount": 1511,
      "completionYearMonth": "2012-12",
      "parkingSpaceCount": 1964
    },
    "study": {
      "studyId": 10,
      "title": "옥수동 주말 생활환경 임장",
      "goal": "교통·생활환경·소음 확인",
      "visitedAt": "2026-07-20T14:00:00+09:00",
      "participantCount": 5
    },
    "metrics": {
      "totalChecklistItemCount": 60,
      "completedChecklistItemCount": 52,
      "averageCompletionRate": 86.7,
      "fieldRecordCount": 34,
      "evidenceCount": 22,
      "hasAiEvidence": true
    },
    "topPositiveFeatures": [
      {
        "rank": 1,
        "label": "지하철 접근성",
        "summary": "옥수역까지의 실제 도보 동선이 짧고 주요 업무지구 이동이 편리하다는 의견이 많았습니다.",
        "mentionCount": 4,
        "mentionRate": 80.0,
        "sourceIds": [201, 205, 219, 224]
      },
      {
        "rank": 2,
        "label": "생활 편의시설",
        "summary": "편의점·병원·상권을 도보로 이용하기 편리하다는 의견이 반복적으로 확인됐습니다.",
        "mentionCount": 3,
        "mentionRate": 60.0,
        "sourceIds": [208, 216, 231]
      }
    ],
    "topCautionFeatures": [
      {
        "rank": 1,
        "label": "단지 진입 경사",
        "summary": "역에서 단지로 진입하는 일부 구간과 단지 내부 동선의 경사가 부담될 수 있습니다.",
        "mentionCount": 3,
        "mentionRate": 60.0,
        "sourceIds": [214, 220, 229]
      },
      {
        "rank": 2,
        "label": "출퇴근 차량 혼잡",
        "summary": "평일 출퇴근 시간대 주변 도로와 단지 출입구의 혼잡을 추가로 확인할 필요가 있습니다.",
        "mentionCount": 2,
        "mentionRate": 40.0,
        "sourceIds": [217, 233]
      }
    ],
    "commonOpinions": [
      {
        "category": "교통",
        "label": "지하철 접근성",
        "opinionType": "POSITIVE",
        "summary": "옥수역까지의 보행 동선이 편리하다는 의견이 공통적으로 확인됐습니다.",
        "participantCount": 4,
        "participantRate": 80.0,
        "sourceIds": [201, 205, 219]
      }
    ],
    "conflictingOpinions": [
      {
        "category": "주차",
        "label": "주차 공간",
        "summary": "주차 공간의 충분성에 대해 긍정과 주의 의견이 함께 확인됐습니다.",
        "positiveParticipantCount": 2,
        "positiveParticipantRate": 40.0,
        "cautionParticipantCount": 3,
        "cautionParticipantRate": 60.0,
        "sourceIds": [210, 215, 232]
      }
    ],
    "categories": [
      {
        "category": "교통",
        "summary": "대중교통 접근성은 긍정적이지만 출퇴근 차량 흐름은 시간대별 재확인이 필요합니다.",
        "checklistItemCount": 12,
        "positiveOpinionCount": 5,
        "positiveOpinionRate": 100.0,
        "cautionOpinionCount": 2,
        "cautionOpinionRate": 40.0,
        "unrecordedOpinionCount": 0,
        "unrecordedOpinionRate": 0.0,
        "dataSufficient": true,
        "participantOpinions": [
          {
            "participantLabel": "참여자 1",
            "opinionType": "POSITIVE",
            "summary": "역 개찰구까지 실제로 약 8분이 걸렸고 보행 동선이 단순했습니다.",
            "sourceIds": [201]
          },
          {
            "participantLabel": "참여자 3",
            "opinionType": "CAUTION",
            "summary": "단지 입구 앞 도로는 퇴근 시간대 혼잡 여부를 다시 볼 필요가 있습니다.",
            "sourceIds": [217]
          }
        ]
      },
      {
        "category": "소음",
        "summary": "주간 기록은 양호했지만 야간·평일 출퇴근 시간대 데이터가 충분하지 않습니다.",
        "checklistItemCount": 8,
        "positiveOpinionCount": 1,
        "positiveOpinionRate": 20.0,
        "cautionOpinionCount": 0,
        "cautionOpinionRate": 0.0,
        "unrecordedOpinionCount": 4,
        "unrecordedOpinionRate": 80.0,
        "dataSufficient": false,
        "participantOpinions": [
          {
            "participantLabel": "참여자 2",
            "opinionType": "POSITIVE",
            "summary": "주말 오후 현장에서는 소음이 크지 않았습니다.",
            "sourceIds": []
          }
        ]
      }
    ],
    "viewer": {
      "isParticipant": true,
      "canViewEvidenceList": true,
      "canViewEvidenceOriginal": true,
      "canFavorite": true
    },
    "favoritedByMe": true,
    "favoriteCount": 18,
    "postId": 123,
    "completedAt": "2026-07-22T18:07:00+09:00",
    "updatedAt": "2026-07-22T18:07:00+09:00"
  },
  "timestamp": "2026-07-25T15:10:00+09:00"
}
```

---

### Response Field

#### 리포트 기본 정보

| 필드 | 타입 | 설명 |
| --- | --- | --- |
| `reportId` | Long | 리포트 ID |
| `status` | String | 리포트 상태, 상세 조회 성공 시 `DONE` |
| `progressStage` | String | 상세 조회 성공 시 최종 진행 단계 `COMPLETED` |
| `title` | String | 리포트 제목 |
| `summary` | String | 스토리형 종합 요약 |
| `apartment` | Object | 대상 아파트 요약 |
| `study` | Object | 임장 스터디 요약 |
| `metrics` | Object | 체크리스트·현장 기록 통계 |
| `topPositiveFeatures` | Array | 주요 긍정 요소 |
| `topCautionFeatures` | Array | 주요 주의 요소 |
| `commonOpinions` | Array | 여러 참여자의 공통 의견 |
| `conflictingOpinions` | Array | 긍정·주의가 함께 확인된 상반 의견 |
| `categories` | Array | 동적 카테고리별 분석 |
| `viewer` | Object | 현재 조회자의 권한 정보 |
| `favoritedByMe` | Boolean | 현재 회원의 찜 여부 |
| `favoriteCount` | Long | 리포트 전체 찜 수 |
| `postId` | Long | null | 연결된 정보 게시글 ID |
| `completedAt` | String | 생성 완료 시각 |
| `updatedAt` | String | 최종 갱신 시각 |

#### `metrics`

| 필드 | 타입 | 설명 |
| --- | --- | --- |
| `totalChecklistItemCount` | Integer | 참여자 전체 체크리스트 항목 수 |
| `completedChecklistItemCount` | Integer | 완료 처리된 항목 수 |
| `averageCompletionRate` | Decimal | 전체 평균 완료율, 0~100 |
| `fieldRecordCount` | Integer | 리포트 생성 대상 현장 기록 수 |
| `evidenceCount` | Integer | 실제 결과에 연결된 고유 현장 기록 수 |
| `hasAiEvidence` | Boolean | AI 직접 근거가 하나 이상 존재하는지 여부 |

#### 주요 요소

| 필드 | 타입 | 설명 |
| --- | --- | --- |
| `rank` | Integer | 표시 순위 |
| `label` | String | 요소명 |
| `summary` | String | 요소 설명 |
| `mentionCount` | Integer | 분석 결과에서 집계된 언급 횟수 |
| `mentionRate` | Decimal | 전체 임장 참여자 중 해당 요소를 언급한 비율 |
| `sourceIds` | Array<Long> | 연결된 현장 기록 ID 목록 |

#### `commonOpinions[]`

| 필드 | 타입 | 설명 |
| --- | --- | --- |
| `category` | String | 동적 체크리스트 카테고리명 |
| `label` | String | 공통 의견 요소명 |
| `opinionType` | String | `POSITIVE`, `CAUTION` |
| `summary` | String | 공통 의견 요약 |
| `participantCount` | Integer | 의견을 남긴 고유 참여자 수 |
| `participantRate` | Decimal | 전체 임장 참여자 중 의견을 남긴 비율 |
| `sourceIds` | Array<Long> | 연결된 현장 기록 ID 목록 |

#### `conflictingOpinions[]`

| 필드 | 타입 | 설명 |
| --- | --- | --- |
| `category` | String | 동적 체크리스트 카테고리명 |
| `label` | String | 상반 의견 요소명 |
| `summary` | String | 상반 의견 요약 |
| `positiveParticipantCount` | Integer | 긍정 의견 고유 참여자 수 |
| `positiveParticipantRate` | Decimal | 전체 임장 참여자 중 긍정 의견 비율 |
| `cautionParticipantCount` | Integer | 주의 의견 고유 참여자 수 |
| `cautionParticipantRate` | Decimal | 전체 임장 참여자 중 주의 의견 비율 |
| `sourceIds` | Array<Long> | 긍정·주의 직접 근거를 합친 현장 기록 ID 목록 |

#### `categories[]`

| 필드 | 타입 | 설명 |
| --- | --- | --- |
| `category` | String | 체크리스트에서 생성된 동적 카테고리명 |
| `summary` | String | 카테고리별 종합 요약 |
| `checklistItemCount` | Integer | 해당 카테고리에 속한 체크리스트 항목 수. 조회 시점에 임장 세션의 참여자 체크리스트 항목을 `category`별로 재집계한 값이며, 전체 합은 `metrics.totalChecklistItemCount`와 일치한다. 매칭되는 항목이 없으면 0 |
| `positiveOpinionCount` | Integer | 긍정 의견 수 |
| `positiveOpinionRate` | Decimal | 전체 임장 참여자 중 긍정 의견 비율 |
| `cautionOpinionCount` | Integer | 주의 의견 수 |
| `cautionOpinionRate` | Decimal | 전체 임장 참여자 중 주의 의견 비율 |
| `unrecordedOpinionCount` | Integer | 해당 카테고리에 유효 의견을 남기지 않은 참여자 수 |
| `unrecordedOpinionRate` | Decimal | 해당 카테고리에 유효 의견을 남기지 않은 참여자 비율 |
| `dataSufficient` | Boolean | 분석에 필요한 근거가 충분한지 여부 |
| `participantOpinions` | Array | 익명화된 참여자별 의견 |

#### `participantOpinions[]`

| 필드 | 타입 | 설명 |
| --- | --- | --- |
| `participantLabel` | String | 리포트 내부에서만 사용하는 익명 라벨 |
| `opinionType` | String | `POSITIVE`, `CAUTION` |
| `summary` | String | 참여자 의견 요약 |
| `sourceIds` | Array<Long> | 의견에 연결된 현장 기록 ID |

---

### Exception

#### 400 Bad Request — 잘못된 리포트 ID

```
{
  "success": false,
  "code": "COMMON_INVALID_REQUEST",
  "message": "입력값을 확인해 주세요.",
  "data": {
    "field": "reportId",
    "reason": "리포트 ID는 1 이상의 숫자여야 합니다."
  },
  "timestamp": "2026-07-25T15:10:00+09:00"
}
```

---

#### 401 Unauthorized

```
{
  "success": false,
  "code": "AUTH_ACCESS_TOKEN_INVALID",
  "message": "로그인이 필요합니다.",
  "data": null,
  "timestamp": "2026-07-25T15:10:00+09:00"
}
```

---

#### 403 Forbidden — 접근 권한 없음

```
{
  "success": false,
  "code": "REPORT_ACCESS_DENIED",
  "message": "해당 리포트에 접근할 권한이 없습니다.",
  "data": null,
  "timestamp": "2026-07-25T15:10:00+09:00"
}
```

---

#### 404 Not Found — 리포트 없음

```
{
  "success": false,
  "code": "REPORT_NOT_FOUND",
  "message": "리포트를 찾을 수 없습니다.",
  "data": null,
  "timestamp": "2026-07-25T15:10:00+09:00"
}
```

---

#### 409 Conflict — 생성 미완료

```
{
  "success": false,
  "code": "REPORT_NOT_DONE",
  "message": "아직 생성이 완료되지 않은 리포트입니다.",
  "data": {
    "reportId": 48,
    "status": "IN_PROGRESS",
    "statusApi": "/api/v1/reports/48/status"
  },
  "timestamp": "2026-07-25T15:10:00+09:00"
}
```

---

#### 409 Conflict — 생성 실패

```
{
  "success": false,
  "code": "REPORT_GENERATION_FAILED",
  "message": "생성에 실패한 리포트입니다.",
  "data": {
    "reportId": 48,
    "retryAvailable": true
  },
  "timestamp": "2026-07-25T15:10:00+09:00"
}
```

---

#### 500 Internal Server Error

```
{
  "success": false,
  "code": "COMMON_INTERNAL_SERVER_ERROR",
  "message": "일시적인 오류가 발생했습니다.",
  "data": null,
  "timestamp": "2026-07-25T15:10:00+09:00"
}
```

---

### 프론트 처리

```
내 리포트·아파트 상세·커뮤니티 자동 게시글에서 리포트 선택
→ GET /api/v1/reports/{reportId} 호출
→ 리포트 개요와 주요 긍정·주의 요소 표시

긍정 요소·주의 요소 카드
→ mentionCount와 mentionRate 표시
→ 참여자인 경우 근거 보기 버튼 활성화
→ sourceIds와 근거 목록 API 연결

공통·상반 의견 영역
→ commonOpinions와 conflictingOpinions 표시
→ 긍정·주의 인원과 비율은 독립 지표로 표시

카테고리 영역 선택
→ 카테고리 요약 펼치기
→ 긍정·주의·미기록 인원과 비율 표시
→ 스터디원별 익명 의견 펼치기

dataSufficient = false
→ "확인된 현장 기록이 충분하지 않습니다." 표시
→ 점수나 단정적인 결론을 임의로 표시하지 않음

favoritedByMe = false && canFavorite = true
→ 리포트 찜 버튼 활성화


REPORT_NOT_DONE
→ 리포트 생성 상태 화면으로 이동
→ statusApi 재조회
```

---

## 스터디 채팅 구조

담당자: 윤다인

스터디 채팅은 별도의 채팅방 생성 API나 `chat_room` 같은 별도 엔티티를 사용하지 않는다. `Study`의 `studyId` 자체가 채팅방 식별자 역할을 하며, REST 이력 조회·읽음 처리·안 읽은 수 조회와 STOMP 구독·발행이 모두 동일한 `studyId`를 사용한다.

- `chat_message`는 메시지마다 `studyId`를 함께 저장한다.
- `chat_read_status`는 회원별로 스터디별 마지막 읽은 위치(`last_read_at`)를 저장한다.
- 스터디가 생성되는 시점에 별도의 채팅방 생성 절차 없이 곧바로 채팅을 사용할 수 있다.

### 사용자 화면 흐름

```
스터디 생성
→ 스터디 홈 생성
→ 채팅 탭 진입
→ 채팅 이력 조회 (GET /api/v1/studies/{studyId}/chat/messages)
→ STOMP 연결 및 구독 (/ws, /sub/studies/{studyId}/chat)
→ 실시간 메시지 송수신
```

위 흐름은 백엔드 계약 기준이며, 프론트엔드 연동이 완료되었다는 의미는 아니다.

---

## 스터디 채팅 이력 조회

Domain: Chat
Method: GET
Progress: 완료
URI: /api/v1/studies/{studyId}/chat/messages
담당자: 윤다인
연동여부: Yes
프론트 담당자: 장선형

스터디 멤버가 스터디 채팅방의 이전 메시지 이력을 조회한다.

스터디 홈의 채팅 탭에서 사용하며, 재연결·스크롤 시 이전 메시지를 커서 기반으로 불러온다. 실시간 신규 메시지는 WebSocket(STOMP)으로 수신하고, 본 API는 과거 이력 로딩에 사용한다.

---

#### Request

- Request HTTP Method: `GET`
- 인증 필요: 필요
- Content-Type: `application/json`
- Request Body: 없음

##### Request Header

```
Authorization: Bearer {accessToken}
```

##### Path Variable · Query Parameter

첫 요청은 `cursor` 없이 최신 메시지부터 조회한다.

```
GET /api/v1/studies/{studyId}/chat/messages?size=30
```

다음(더 과거) 목록은 이전 응답의 `nextCursor`를 전달한다.

```
GET /api/v1/studies/{studyId}/chat/messages?cursor=1050&size=30
```

| 필드 | 타입 | 필수 | 기본값 | 설명 |
| --- | --- | --- | --- | --- |
| `studyId` | Long | Y | - | 스터디 ID (Path) |
| `cursor` | Long | N | 없음 | 다음 목록 조회에 사용할 메시지 ID 커서 |
| `size` | Integer | N | `30` | 한 번에 조회할 메시지 수, 1 이상 50 이하 |

##### Message Type Enum

| 값 | 설명 |
| --- | --- |
| `TEXT` | 텍스트 메시지 |
| `IMAGE` | 사진 메시지 |
| `SYSTEM` | 시스템 메시지(일정 변경·멤버 변경 등) |

---

#### 처리 기준

##### 1. 회원 확인

- 로그인한 회원 ID는 Access Token에서 확인한다.
- 회원 상태가 `ACTIVE`인 경우에만 조회할 수 있다.

##### 2. 권한 확인

- 요청자는 해당 스터디의 승인된 멤버(`study_member.status = ACTIVE`)여야 한다.
- 승인 멤버가 아니면 조회를 거절한다.
- 존재하지 않는 스터디는 거절한다.

##### 3. 조회·정렬

- 해당 스터디의 채팅 메시지를 `chat_message.id DESC`(최신순)로 조회한다.
- 첫 요청에서 `cursor`가 없으면 가장 최근 메시지부터 조회한다.
- `cursor`가 전달되면 `chat_message.id < cursor`인 메시지를 이어서 조회한다.
- 요청한 `size`보다 한 건 더 조회하여 다음 데이터 존재 여부를 판단한다.
- 실제 응답에는 최대 `size`개만 반환한다.
- 다음 데이터가 있으면 `hasNext = true`, 마지막 메시지 ID를 `nextCursor`로 반환한다. 없으면 `hasNext = false`, `nextCursor = null`로 반환한다.

##### 4. 메시지 구성

- `SYSTEM` 메시지는 `sender`가 `null`이다.
- `IMAGE` 메시지는 조회용 임시 URL(presigned)을 포함한 `image` 객체를 반환한다.
- `TEXT` 메시지는 `content`에 본문을, `image`는 `null`로 반환한다.

##### 5. 보안

- 해당 스터디 멤버 외에는 메시지 본문·이미지 URL에 접근할 수 없다.

---

#### Response

##### 200 OK

json

```json
{
  "success": true,
  "code": "CHAT_MESSAGE_LIST_SUCCESS",
  "message": "채팅 이력 조회에 성공했습니다.",
  "data": {
    "studyId": 7,
    "content": [
      {
        "messageId": 1080,
        "messageType": "TEXT",
        "content": "다들 몇 시에 모일까요?",
        "image": null,
        "sender": {
          "memberId": 42,
          "nickname": "루돌푸",
          "selectedCharacterId": "PALBANG"
        },
        "createdAt": "2026-07-22T13:40:00+09:00"
      },
      {
        "messageId": 1075,
        "messageType": "IMAGE",
        "content": null,
        "image": {
          "fileId": 91,
          "imageUrl": "<https://s3>.../chat/xxx.jpg?expires=...",
          "uploadStatus": "COMPLETED"
        },
        "sender": {
          "memberId": 51,
          "nickname": "집콩이",
          "selectedCharacterId": "JIPKONG"
        },
        "createdAt": "2026-07-22T13:35:00+09:00"
      },
      {
        "messageId": 1074,
        "messageType": "SYSTEM",
        "content": "임장 일정이 7월 26일 14:00으로 변경되었습니다.",
        "image": null,
        "sender": null,
        "createdAt": "2026-07-22T13:30:00+09:00"
      }
    ],
    "nextCursor": 1074,
    "hasNext": true
  },
  "timestamp": "2026-07-22T14:00:00+09:00"
}
```

##### 200 OK — 마지막(가장 오래된) 목록

json

```json
{
  "success": true,
  "code": "CHAT_MESSAGE_LIST_SUCCESS",
  "message": "채팅 이력 조회에 성공했습니다.",
  "data": {
    "studyId": 7,
    "content": [
      {
        "messageId": 1,
        "messageType": "SYSTEM",
        "content": "스터디 채팅방이 생성되었습니다.",
        "image": null,
        "sender": null,
        "createdAt": "2026-07-15T09:30:00+09:00"
      }
    ],
    "nextCursor": null,
    "hasNext": false
  },
  "timestamp": "2026-07-22T14:00:00+09:00"
}
```

##### 200 OK — 메시지 없음

json

```json
{
  "success": true,
  "code": "CHAT_MESSAGE_LIST_SUCCESS",
  "message": "채팅 이력 조회에 성공했습니다.",
  "data": {
    "studyId": 7,
    "content": [],
    "nextCursor": null,
    "hasNext": false
  },
  "timestamp": "2026-07-22T14:00:00+09:00"
}
```

---

#### Response Field

##### 채팅 이력

| 필드 | 타입 | 설명 |
| --- | --- | --- |
| `studyId` | Long | 스터디 ID |
| `content` | Array | 채팅 메시지 목록(최신순) |
| `content[].messageId` | Long | 메시지 ID |
| `content[].messageType` | String | 메시지 종류 (Message Type Enum) |
| `content[].content` | String | 텍스트·시스템 메시지 본문, 이미지 메시지면 `null` |
| `content[].image` | Object | 이미지 정보, 이미지 메시지가 아니면 `null` |
| `content[].sender` | Object | 보낸 회원 정보, 시스템 메시지면 `null` |
| `content[].createdAt` | String | 메시지 전송 시각 |

##### 이미지 정보

| 필드 | 타입 | 설명 |
| --- | --- | --- |
| `image.fileId` | Long | 파일 메타 ID |
| `image.imageUrl` | String | 사진 조회용 임시 URL |
| `image.uploadStatus` | String | 업로드 상태 |

##### 발신자 정보

| 필드 | 타입 | 설명 |
| --- | --- | --- |
| `sender.memberId` | Long | 회원 ID |
| `sender.nickname` | String | 회원 닉네임 |
| `sender.selectedCharacterId` | String | 선택 캐릭터 |

##### 커서 정보

| 필드 | 타입 | 설명 |
| --- | --- | --- |
| `nextCursor` | Long | 다음(과거) 목록 커서, 없으면 `null` |
| `hasNext` | Boolean | 다음 목록 존재 여부 |

---

#### Exception

##### 403 Forbidden — 스터디 멤버 아님

json

```json
{ "success": false, "code": "CHAT_FORBIDDEN", "message": "해당 스터디의 멤버만 조회할 수 있습니다.", "data": null, "timestamp": "2026-07-22T14:00:00+09:00" }
```

##### 400 Bad Request — 잘못된 조회 개수

json

```json
{ "success": false, "code": "COMMON_INVALID_REQUEST", "message": "입력값을 확인해 주세요.", "data": { "field": "size", "reason": "조회 개수는 1 이상 50 이하이어야 합니다." }, "timestamp": "2026-07-22T14:00:00+09:00" }
```

##### 404 Not Found — 스터디 없음

json

```json
{ "success": false, "code": "STUDY_NOT_FOUND", "message": "존재하지 않는 스터디입니다.", "data": null, "timestamp": "2026-07-22T14:00:00+09:00" }
```

##### 401 Unauthorized

json

```json
{ "success": false, "code": "AUTH_ACCESS_TOKEN_INVALID", "message": "로그인이 필요합니다.", "data": null, "timestamp": "2026-07-22T14:00:00+09:00" }
```

##### 404 Not Found — 회원 없음

json

```json
{ "success": false, "code": "MEMBER_NOT_FOUND", "message": "회원 정보를 찾을 수 없습니다.", "data": null, "timestamp": "2026-07-22T14:00:00+09:00" }
```

##### 500 Internal Server Error

json

```json
{ "success": false, "code": "COMMON_INTERNAL_SERVER_ERROR", "message": "일시적인 오류가 발생했습니다.", "data": null, "timestamp": "2026-07-22T14:00:00+09:00" }
```

---

#### 프론트 처리

```
스터디 채팅 탭 진입
→ cursor 없이 최신 메시지 조회
→ WebSocket(STOMP) 연결로 신규 메시지 실시간 수신

채팅 목록 상단으로 스크롤(과거 로딩)
→ hasNext 확인
→ hasNext = true이면 nextCursor를 cursor로 전달해 이전 메시지 조회
→ 기존 목록 위에 추가

messageType = SYSTEM
→ 가운데 정렬 시스템 안내 스타일로 표시

messageType = IMAGE
→ imageUrl로 썸네일 렌더링(Glide)

재연결 성공
→ 마지막 수신 메시지 이후를 동기화하여 대화 복원
```

---

## 스터디 채팅 읽음 처리

Method: PATCH
Progress: 완료
URI: /api/v1/studies/{studyId}/chat/read
담당자: 윤다인
연동여부: Yes
프론트 담당자: 장선형

로그인한 회원이 스터디 채팅방의 메시지를 읽음 처리한다.

채팅 탭에 진입하거나 새 메시지를 확인했을 때 호출하여, 회원의 마지막 읽은 시각을 갱신하고 안 읽은 수를 0으로 만든다.

---

#### Request

- Request HTTP Method: `PATCH`
- 인증 필요: 필요
- Content-Type: `application/json`

##### Request Header

```
Authorization: Bearer {accessToken}
```

##### Path Variable

```
PATCH /api/v1/studies/{studyId}/chat/read
```

| 필드 | 타입 | 필수 | 설명 |
| --- | --- | --- | --- |
| `studyId` | Long | Y | 스터디 ID |

##### Request Body

json

```json
{
  "lastReadMessageId": 1080
}
```

| 필드 | 타입 | 필수 | 설명 |
| --- | --- | --- | --- |
| `lastReadMessageId` | Long | N | 읽음 처리 기준 메시지 ID. 생략 시 현재 시각 기준으로 전체 읽음 처리 |

---

#### 처리 기준

##### 1. 회원 확인

- 로그인한 회원 ID는 Access Token에서 확인한다.
- 회원 상태가 `ACTIVE`인 경우에만 처리할 수 있다.

##### 2. 권한 확인

- 요청자는 해당 스터디의 승인된 멤버(`study_member.status = ACTIVE`)여야 한다.

##### 3. 읽음 처리

- 회원·스터디의 읽음 상태(`chat_read_status`)가 없으면 새로 생성하고, 있으면 갱신한다. (upsert)
- `lastReadMessageId`가 주어지면 해당 메시지의 전송 시각을 `last_read_at`으로 설정한다.
- `lastReadMessageId`가 없으면 현재 시각을 `last_read_at`으로 설정한다.
- `lastReadMessageId`는 해당 스터디의 메시지여야 한다.
- 이미 더 최신 시각을 읽은 상태이면 `last_read_at`을 과거로 되돌리지 않는다.
- 회원·스터디 조합은 하나의 읽음 상태만 유지한다. (`UNIQUE(study_id, member_id)`)
- 동일 요청이 반복되어도 오류 없이 멱등하게 처리한다.

---

#### Response

##### 200 OK

json

```json
{
  "success": true,
  "code": "CHAT_READ_SUCCESS",
  "message": "채팅을 읽음 처리했습니다.",
  "data": {
    "studyId": 7,
    "lastReadAt": "2026-07-22T13:40:00+09:00",
    "unreadCount": 0
  },
  "timestamp": "2026-07-22T14:00:00+09:00"
}
```

---

#### Response Field

| 필드 | 타입 | 설명 |
| --- | --- | --- |
| `studyId` | Long | 스터디 ID |
| `lastReadAt` | String | 갱신된 마지막 읽은 시각 |
| `unreadCount` | Integer | 처리 후 안 읽은 메시지 수(항상 0) |

---

#### Exception

##### 403 Forbidden — 스터디 멤버 아님

json

```json
{ "success": false, "code": "CHAT_FORBIDDEN", "message": "해당 스터디의 멤버만 읽음 처리할 수 있습니다.", "data": null, "timestamp": "2026-07-22T14:00:00+09:00" }
```

##### 400 Bad Request — 잘못된 메시지 ID

json

```json
{ "success": false, "code": "CHAT_MESSAGE_INVALID", "message": "읽음 기준 메시지를 확인해 주세요.", "data": { "field": "lastReadMessageId", "reason": "해당 스터디의 메시지가 아닙니다." }, "timestamp": "2026-07-22T14:00:00+09:00" }
```

##### 404 Not Found — 스터디 없음

json

```json
{ "success": false, "code": "STUDY_NOT_FOUND", "message": "존재하지 않는 스터디입니다.", "data": null, "timestamp": "2026-07-22T14:00:00+09:00" }
```

##### 401 Unauthorized

json

```json
{ "success": false, "code": "AUTH_ACCESS_TOKEN_INVALID", "message": "로그인이 필요합니다.", "data": null, "timestamp": "2026-07-22T14:00:00+09:00" }
```

##### 404 Not Found — 회원 없음

json

```json
{ "success": false, "code": "MEMBER_NOT_FOUND", "message": "회원 정보를 찾을 수 없습니다.", "data": null, "timestamp": "2026-07-22T14:00:00+09:00" }
```

##### 500 Internal Server Error

json

```json
{ "success": false, "code": "COMMON_INTERNAL_SERVER_ERROR", "message": "일시적인 오류가 발생했습니다.", "data": null, "timestamp": "2026-07-22T14:00:00+09:00" }
```

---

#### 프론트 처리

```
채팅 탭 진입
→ 최신 메시지 조회 후 읽음 처리 API 호출(lastReadMessageId = 최신 메시지 ID)
→ 채팅방 목록의 안 읽은 뱃지 제거

채팅방에서 새 메시지 수신(포그라운드)
→ 화면에 표시됨과 동시에 읽음 처리 API 호출

채팅방 이탈 후 재진입
→ 읽음 처리 재호출로 unreadCount 동기화
```

---

## 읽지 않은 스터디 채팅 수 조회

Method: GET
Progress: 완료
URI: /api/v1/studies/{studyId}/chat/unread-count
담당자: 윤다인
연동여부: Yes
프론트 담당자: 장선형

로그인한 회원의 스터디 채팅방 안 읽은 메시지 수를 조회한다.

스터디 목록·채팅 탭의 안 읽음 뱃지 표시에 사용한다. 특정 스터디 하나 또는 참여 중인 전체 스터디의 안 읽은 수를 조회한다.

---

#### Request

- Request HTTP Method: `GET`
- 인증 필요: 필요
- Content-Type: `application/json`
- Request Body: 없음

##### Request Header

```
Authorization: Bearer {accessToken}
```

##### Path Variable

```
GET /api/v1/studies/{studyId}/chat/unread-count
```

| 필드 | 타입 | 필수 | 설명 |
| --- | --- | --- | --- |
| `studyId` | Long | Y | 스터디 ID |

---

#### 처리 기준

##### 1. 회원 확인

- 로그인한 회원 ID는 Access Token에서 확인한다.
- 회원 상태가 `ACTIVE`인 경우에만 조회할 수 있다.

##### 2. 권한 확인

- 요청자는 해당 스터디의 승인된 멤버(`study_member.status = ACTIVE`)여야 한다.

##### 3. 안 읽은 수 계산

- 회원의 읽음 상태(`chat_read_status.last_read_at`)를 기준으로, `chat_message.created_at > last_read_at`인 메시지 수를 계산한다.
- 읽음 상태가 없으면(한 번도 안 읽음) 스터디의 전체 메시지 수를 안 읽은 수로 반환한다.
- 본인이 보낸 메시지는 안 읽은 수에서 제외한다.
- 시스템 메시지 포함 여부는 서비스 정책을 따른다(기본 포함).

---

#### Response

##### 200 OK

json

```json
{
  "success": true,
  "code": "CHAT_UNREAD_COUNT_SUCCESS",
  "message": "안 읽은 채팅 수 조회에 성공했습니다.",
  "data": {
    "studyId": 7,
    "unreadCount": 5,
    "lastReadAt": "2026-07-22T13:40:00+09:00"
  },
  "timestamp": "2026-07-22T14:00:00+09:00"
}
```

##### 200 OK — 한 번도 읽지 않음

json

```json
{
  "success": true,
  "code": "CHAT_UNREAD_COUNT_SUCCESS",
  "message": "안 읽은 채팅 수 조회에 성공했습니다.",
  "data": {
    "studyId": 7,
    "unreadCount": 23,
    "lastReadAt": null
  },
  "timestamp": "2026-07-22T14:00:00+09:00"
}
```

---

#### Response Field

| 필드 | 타입 | 설명 |
| --- | --- | --- |
| `studyId` | Long | 스터디 ID |
| `unreadCount` | Integer | 안 읽은 메시지 수 |
| `lastReadAt` | String | 마지막 읽은 시각, 한 번도 안 읽었으면 `null` |

---

#### Exception

##### 403 Forbidden — 스터디 멤버 아님

json

```json
{ "success": false, "code": "CHAT_FORBIDDEN", "message": "해당 스터디의 멤버만 조회할 수 있습니다.", "data": null, "timestamp": "2026-07-22T14:00:00+09:00" }
```

##### 404 Not Found — 스터디 없음

json

```json
{ "success": false, "code": "STUDY_NOT_FOUND", "message": "존재하지 않는 스터디입니다.", "data": null, "timestamp": "2026-07-22T14:00:00+09:00" }
```

##### 401 Unauthorized

json

```json
{ "success": false, "code": "AUTH_ACCESS_TOKEN_INVALID", "message": "로그인이 필요합니다.", "data": null, "timestamp": "2026-07-22T14:00:00+09:00" }
```

##### 404 Not Found — 회원 없음

json

```json
{ "success": false, "code": "MEMBER_NOT_FOUND", "message": "회원 정보를 찾을 수 없습니다.", "data": null, "timestamp": "2026-07-22T14:00:00+09:00" }
```

##### 500 Internal Server Error

json

```json
{ "success": false, "code": "COMMON_INTERNAL_SERVER_ERROR", "message": "일시적인 오류가 발생했습니다.", "data": null, "timestamp": "2026-07-22T14:00:00+09:00" }
```

---

#### 프론트 처리

```
스터디 목록·채팅 탭 진입
→ 안 읽은 채팅 수 조회
→ unreadCount > 0이면 뱃지 표시

채팅 읽음 처리 후
→ 안 읽은 수 재조회 또는 로컬에서 0으로 갱신

새 메시지 FCM 수신(백그라운드)
→ 해당 스터디 안 읽은 수 재조회
```

---

## 스터디 채팅 메시지 삭제

Method: DELETE
Progress: 완료
URI: /api/v1/studies/{studyId}/chat/messages/{messageId}
담당자: 윤다인
연동여부: No
프론트 담당자: 박재명

> 사용자 요청(버그 리포트 "채팅방 사진 다운로드·삭제·수정 불가")으로 추가된 계약이다.
> 원본 Notion 명세에는 없던 엔드포인트이며, CONFLICT_LOG C-016에 근거를 기록했다.

#### 개요

본인이 보낸 TEXT·IMAGE 메시지를 소프트 삭제한다. 행은 지우지 않고 `deleted_at`만
기록한다 — 커서 페이지네이션과 안 읽은 수 계산이 흔들리지 않는다. 삭제된 메시지는
이력 조회에서 `deleted: true`로 내려가고 content·image는 노출하지 않는다(톰스톤).
커밋 후 실시간 구독자(`/sub/studies/{studyId}/chat`)에게 `deleted: true` 페이로드가
발행되어 열려 있는 채팅방에서도 즉시 지워진다.

#### 인증/권한

- Bearer Access Token 필수
- 활성(ACTIVE) 회원 + 해당 스터디의 ACTIVE 멤버
- 본인이 보낸 메시지만 삭제 가능(SYSTEM 메시지는 발신자가 없어 삭제 불가)
- COMPLETED·CANCELED 스터디는 읽기 전용 보관이므로 삭제 차단(SEND 차단과 같은 기준)

#### 요청

```
DELETE /api/v1/studies/{studyId}/chat/messages/{messageId}
Authorization: Bearer {accessToken}
```

| 경로 변수 | 타입 | 설명 |
| --- | --- | --- |
| `studyId` | Long | 스터디 ID |
| `messageId` | Long | 삭제할 메시지 ID |

요청 본문 없음.

#### 응답 (200)

```json
{
  "success": true,
  "code": "CHAT_MESSAGE_DELETE_SUCCESS",
  "message": "채팅 메시지를 삭제했습니다.",
  "data": {
    "studyId": 7,
    "messageId": 1042,
    "deletedAt": "2026-08-12T14:00:00+09:00"
  },
  "timestamp": "2026-08-12T14:00:00.123456+09:00"
}
```

이미 삭제된 메시지를 다시 삭제해도 200과 최초 `deletedAt`을 돌려준다(재시도 멱등,
재브로드캐스트 없음).

#### 오류

| 상태 | 코드 | 조건 |
| --- | --- | --- |
| 401 | `UNAUTHORIZED` | Access Token 없음/무효 |
| 403 | `CHAT_FORBIDDEN` | 해당 스터디의 ACTIVE 멤버가 아님 |
| 403 | `CHAT_MESSAGE_DELETE_FORBIDDEN` | 본인이 보낸 메시지가 아님(SYSTEM 포함) |
| 403 | `CHAT_STUDY_COMPLETED` | 완료·취소된 스터디 |
| 404 | `MEMBER_NOT_FOUND` | 활성 회원이 아님 |
| 404 | `STUDY_NOT_FOUND` | 스터디가 없거나 삭제됨 |
| 404 | `CHAT_MESSAGE_NOT_FOUND` | 해당 스터디에 그 메시지가 없음 |

#### 연관 계약 변경(하위 호환 추가)

- 이력 조회(`GET .../chat/messages`) 항목과 실시간 브로드캐스트 페이로드에
  `deleted`(boolean) 필드가 추가된다. 삭제된 메시지는 `content: null`,
  `image: null`이며 `sender`는 정렬 판단을 위해 유지된다.
- 삭제된 IMAGE 메시지의 presigned URL은 발급하지 않는다.

#### 프론트 처리

```
내 말풍선 길게 누르기
→ 삭제 확인 다이얼로그
→ DELETE 호출 성공 시 해당 말풍선을 "삭제된 메시지입니다" 톰스톤으로 교체

실시간 deleted=true 수신
→ 같은 messageId의 말풍선을 톰스톤으로 교체
```

---

## 스터디 채팅 메시지 수정

Method: PATCH
Progress: 완료
URI: /api/v1/studies/{studyId}/chat/messages/{messageId}
담당자: 윤다인
연동여부: No
프론트 담당자: 박재명

> 사용자 요청(버그 리포트 "채팅방 사진 다운로드·삭제·수정 불가")으로 추가된 계약이다.
> 원본 Notion 명세에는 없던 엔드포인트이며, CONFLICT_LOG C-016에 근거를 기록했다.

#### 개요

본인이 보낸 TEXT 메시지의 본문을 제자리에서 수정한다. `edited_at`이 기록되고
이력·실시간 페이로드의 `editedAt` 필드로 "수정됨" 표시와 클라이언트 병합
우선순위(더 늦게 수정된 버전이 이긴다)를 판단한다. 커밋 후 실시간 구독자에게
바뀐 본문과 editedAt이 발행된다.

#### 인증/권한

- Bearer Access Token 필수
- 활성(ACTIVE) 회원 + 해당 스터디의 ACTIVE 멤버
- 본인이 보낸 TEXT 메시지만 수정 가능 (IMAGE·SYSTEM 불가 — 사진 교체는 삭제 후 재전송)
- 삭제된 메시지는 404로 감춘다
- COMPLETED·CANCELED 스터디는 읽기 전용 보관이므로 수정 차단

#### 요청

```
PATCH /api/v1/studies/{studyId}/chat/messages/{messageId}
Authorization: Bearer {accessToken}
Content-Type: application/json

{ "content": "고친 내용" }
```

| 필드 | 타입 | 필수 | 설명 |
| --- | --- | --- | --- |
| `content` | String | 필수 | 새 본문. 공백만 있으면 400 (길이 상한은 전송과 동일하게 없음) |

#### 응답 (200)

```json
{
  "success": true,
  "code": "CHAT_MESSAGE_EDIT_SUCCESS",
  "message": "채팅 메시지를 수정했습니다.",
  "data": {
    "studyId": 7,
    "messageId": 1042,
    "content": "고친 내용",
    "editedAt": "2026-08-12T14:00:00+09:00"
  },
  "timestamp": "2026-08-12T14:00:00.123456+09:00"
}
```

#### 오류

| 상태 | 코드 | 조건 |
| --- | --- | --- |
| 400 | `COMMON_INVALID_REQUEST` | content가 비어 있음 |
| 400 | `CHAT_MESSAGE_TYPE_INVALID` | TEXT 메시지가 아님(IMAGE·SYSTEM) |
| 401 | `UNAUTHORIZED` | Access Token 없음/무효 |
| 403 | `CHAT_FORBIDDEN` | 해당 스터디의 ACTIVE 멤버가 아님 |
| 403 | `CHAT_MESSAGE_EDIT_FORBIDDEN` | 본인이 보낸 메시지가 아님 |
| 403 | `CHAT_STUDY_COMPLETED` | 완료·취소된 스터디 |
| 404 | `MEMBER_NOT_FOUND` / `STUDY_NOT_FOUND` | 활성 회원/스터디가 아님 |
| 404 | `CHAT_MESSAGE_NOT_FOUND` | 메시지가 없거나 이미 삭제됨 |

#### 연관 계약 변경(하위 호환 추가)

- 이력 조회 항목과 실시간 브로드캐스트 페이로드에 `editedAt`(nullable ISO) 필드가 추가된다.

#### 프론트 처리

```
내 TEXT 말풍선 길게 누르기 → "수정하기"
→ 입력바가 수정 모드로 전환(원문 프리필 + "메시지 수정 중" 배너)
→ 전송 버튼 = 수정 확정(PATCH), 원문과 같으면 조용히 취소
→ 성공 시 말풍선 본문 교체 + 시간 옆 "수정됨" 표시

실시간 editedAt 수신
→ 같은 messageId 말풍선의 본문 교체(더 늦은 editedAt이 이김)
```

---

## 스터디 실시간 채팅 WebSocket·STOMP 계약

담당자: 윤다인

Swagger UI(`/swagger-ui/index.html`)는 REST API 명세만 표시하며, 아래 WebSocket·STOMP 계약은 Swagger에 나타나지 않는 별도 실시간 통신 규격이므로 REST API와 구분해 이 섹션에 별도로 정리한다.

### 연결

```
Handshake URI: /ws
```

클라이언트는 WebSocket 연결 시 유효한 Access Token을 전달한다.

```
Authorization: Bearer {accessToken}
```

### 구독

```
/sub/studies/{studyId}/chat
```

### 발행

```
/pub/studies/{studyId}/chat/messages
```

### 발행 Payload — 텍스트

```json
{
  "messageType": "TEXT",
  "content": "다들 몇 시에 모일까요?",
  "imageFileId": null,
  "clientMessageId": "08a63b81-5a97-4dbc-93a1-d984f83c2e12"
}
```

### 발행 Payload — 사진

```json
{
  "messageType": "IMAGE",
  "content": null,
  "imageFileId": 91,
  "clientMessageId": "39e38e7f-24d8-4ef1-aa43-d5cf48abcfde"
}
```

### 메시지 유형

```
TEXT / IMAGE / SYSTEM
```

`SYSTEM` 메시지는 클라이언트가 직접 발행하지 않고 일정 변경·멤버 변경 등 서버 이벤트로 생성한다.

### 수신 Payload

```json
{
  "messageId": 1080,
  "studyId": 7,
  "messageType": "TEXT",
  "content": "다들 몇 시에 모일까요?",
  "image": null,
  "sender": {
    "memberId": 42,
    "nickname": "루돌푸",
    "selectedCharacterId": "PALBANG"
  },
  "createdAt": "2026-07-24T19:20:00+09:00"
}
```

### 처리 기준

- `study_member.status=ACTIVE`인 승인 멤버만 연결·구독·발행할 수 있다.
- 비멤버는 채널 정보를 조회하거나 구독할 수 없다.
- `IMAGE` 메시지의 파일은 `fileUsage=CHAT_IMAGE`, `uploadStatus=COMPLETED`여야 한다.
- 일정 변경과 멤버 변경은 `SYSTEM` 메시지로 저장하고 연결된 멤버에게 전송한다.
- 리포트 생성 완료 후 스터디가 `COMPLETED`가 되면 기존 이력 조회는 허용하지만 신규 메시지 발행은 차단한다.
- 재연결 후 누락된 메시지는 REST 이력 조회 API로 복원한다.
- 메시지는 데이터베이스 저장 트랜잭션이 커밋된 이후에만(AFTER_COMMIT) 실시간으로 발행한다. 저장이 실패하거나 트랜잭션이 롤백되면 발행하지 않는다.
- 동일 `clientMessageId`의 중복 발행은 한 번만 저장한다. `chat_message`에 `client_message_id` 컬럼을 두고 `(sender_id, client_message_id)` 조합(둘 다 NULL이 아닐 때만)에 partial unique index를 적용해 DB 레벨에서 영구적으로 보장한다. `SYSTEM` 메시지처럼 `sender_id`가 없는 저장은 이 제약의 대상이 아니다.

### 오류 전달 경로

채팅 오류는 발생 위치에 따라 서로 다른 경로로 전달되며, 프론트는 두 경로를 모두 구독해야 한다.

1. **STOMP ERROR 프레임** — CONNECT 인증, SUBSCRIBE·SEND 권한(스터디 멤버 여부, 완료 스터디 여부) 검사 실패 시 발생한다. STOMP 규약상 ERROR 프레임 전송 후 연결이 종료되므로, 클라이언트는 재연결(재로그인 또는 토큰 재발급 후 재연결)로 대응해야 한다.
2. **`/user/queue/errors`** — `messageType`·`content`·`imageFileId` 등 발행 payload 자체의 검증 실패 시 발생한다. 연결을 끊지 않고 요청을 보낸 사용자에게만 전달되므로, 클라이언트는 CONNECT 성공 직후 `/user/queue/errors`를 구독해 두어야 한다.

두 경로 모두 다음과 같은 동일한 형태의 JSON 본문을 사용한다.

```json
{
  "code": "CHAT_FORBIDDEN",
  "message": "해당 스터디의 멤버만 채팅을 이용할 수 있습니다."
}
```

| 오류 코드 | 전달 경로 | 발생 시점 |
| --- | --- | --- |
| `CHAT_CONNECTION_UNAUTHORIZED` | STOMP ERROR 프레임 | CONNECT 시 Access Token이 없거나 유효하지 않음, 회원이 `ACTIVE`가 아님 |
| `CHAT_FORBIDDEN` | STOMP ERROR 프레임 | SUBSCRIBE·SEND 시 해당 스터디의 `ACTIVE` 멤버가 아님 |
| `CHAT_STUDY_COMPLETED` | STOMP ERROR 프레임 | SEND 시 스터디가 `COMPLETED` 상태임(SUBSCRIBE는 허용) |
| `CHAT_MESSAGE_TYPE_INVALID` | `/user/queue/errors` | `messageType`이 `TEXT`·`IMAGE`가 아님(`SYSTEM` 직접 발행 포함) |
| `CHAT_CONTENT_REQUIRED` | `/user/queue/errors` | `TEXT` 메시지의 `content`가 비어 있음 |
| `CHAT_IMAGE_INVALID` | `/user/queue/errors` | `IMAGE` 메시지의 `imageFileId`가 없거나, 해당 파일이 `CHAT_IMAGE`·`COMPLETED`·발신자 소유·같은 스터디가 아니거나, S3 기능이 비활성화 상태임 |

### 상세 연결·권한 기준

- WebSocket 연결 시 Access Token을 STOMP CONNECT Header에 전달한다.
- 서버는 토큰 회원이 `ACTIVE`이며 해당 스터디의 `study_member.status=ACTIVE`인지 확인한다.
- 비승인 회원의 구독과 발행을 모두 거절한다.
- `TEXT`, `IMAGE`, `SYSTEM` 외 메시지 유형은 허용하지 않는다.
- 클라이언트는 `SYSTEM` 메시지를 직접 발행할 수 없다.
- 일정 변경·멤버 변경 시스템 메시지는 서버 내부 이벤트로 생성한다. 이 기능은 현재 서버 내부 저장·발행 진입점까지만 구현되어 있으며, 일정 변경·멤버 변경(BE-008) 서비스가 구현되면 해당 진입점을 호출하도록 연결해야 한다.
- 완료된 스터디는 기존 채팅 구독·이력 조회는 허용하되 신규 TEXT·IMAGE 발행은 차단한다.
- 연결 해제 후 재접속하면 REST 채팅 이력 API로 누락 메시지를 복원한다.
- 사진 메시지는 먼저 `CHAT_IMAGE` 용도의 파일 업로드를 완료한 뒤 `imageFileId`를 발행한다.

### BE-013 채팅 IMAGE 메시지 처리 범위 완료

BE-013 채팅 IMAGE 연동 범위는 다음을 포함하여 완료되었다.

- IMAGE 메시지의 `imageFileId` 수신
- 파일 메타데이터(`file_meta`) 조회
- `fileUsage = CHAT_IMAGE` 용도 검증
- `uploadStatus = COMPLETED` 업로드 완료 상태 검증
- 삭제 여부(`deletedAt`) 검증
- 파일 소유자(`ownerId`)와 메시지 발신자 일치 검증
- 파일의 `studyId`와 채팅 `studyId` 일치 검증
- S3 Presigned GET URL 생성
- 실시간 STOMP 수신 Payload에 이미지 접근 URL 포함
- 채팅 이력 조회 응답에 이미지 접근 URL 포함
- 정상·예외 케이스 자동 테스트
- `AWS_S3_ENABLED=false` 환경에서도 TEXT 메시지는 정상 동작

다음은 이번 하위 태스크(BE-013)의 제외 범위이며, 별도 Media 업로드 기능으로 후속 진행한다.

- `POST /api/v1/media/presigned-urls`
- `POST /api/v1/media/{fileId}/complete`
- `GET /api/v1/media/{fileId}`
- 현장 사진 업로드, 음성 업로드, STT 연동
- 업로드 만료·실패·재시도 처리

---

## 스터디 채팅 푸시 알림 설정 조회

Domain: Chat
Method: GET
Progress: 완료
URI: /api/v1/studies/{studyId}/chat/notification-settings
담당자: 김윤석
연동여부: Yes

로그인한 회원의 해당 스터디 채팅 푸시 알림 수신 설정을 조회한다.
설정 값은 스터디 멤버별로 저장되며(V30 `study_member.chat_push_enabled`), 기본값은 `true`다.

### Request

- HTTP Method: `GET`
- URI: `/api/v1/studies/{studyId}/chat/notification-settings`
- 인증 필요: 필요
- Request Body: 없음

#### Request Header

```
Authorization: Bearer {accessToken}
```

#### Path Variable

| 필드 | 타입 | 필수 | 설명 |
| --- | --- | --- | --- |
| `studyId` | Long | Y | 스터디 ID |

### 처리 기준

- 회원 상태가 `ACTIVE`이고 탈퇴하지 않은 회원만 조회한다.
- 요청자는 삭제되지 않은 스터디의 활성 멤버(`study_member.status = ACTIVE`)여야 한다.

### Response

#### 200 OK

```json
{
  "success": true,
  "code": "CHAT_NOTIFICATION_SETTING_SUCCESS",
  "message": "채팅 푸시 알림 설정을 조회했습니다.",
  "data": {
    "studyId": 7,
    "pushEnabled": true
  },
  "timestamp": "2026-08-06T15:00:00+09:00"
}
```

#### Response Field

| 필드 | 타입 | 설명 |
| --- | --- | --- |
| `studyId` | Long | 스터디 ID |
| `pushEnabled` | Boolean | 채팅 푸시 알림 수신 여부 |

### Exception

- `401 AUTH_ACCESS_TOKEN_INVALID`
- `403 CHAT_FORBIDDEN` — 해당 스터디의 활성 멤버가 아님
- `404 MEMBER_NOT_FOUND` — 회원이 없거나 활성 상태가 아님
- `404 STUDY_NOT_FOUND` — 스터디가 없거나 삭제됨

---

## 스터디 채팅 푸시 알림 설정 변경

Domain: Chat
Method: PATCH
Progress: 완료
URI: /api/v1/studies/{studyId}/chat/notification-settings
담당자: 김윤석
연동여부: Yes

로그인한 회원의 해당 스터디 채팅 푸시 알림 수신 여부를 변경한다.

### Request

- HTTP Method: `PATCH`
- URI: `/api/v1/studies/{studyId}/chat/notification-settings`
- 인증 필요: 필요
- Content-Type: `application/json`

#### Request Header

```
Authorization: Bearer {accessToken}
```

#### Path Variable

| 필드 | 타입 | 필수 | 설명 |
| --- | --- | --- | --- |
| `studyId` | Long | Y | 스터디 ID |

#### Request Body

```json
{
  "pushEnabled": false
}
```

| 필드 | 타입 | 필수 | 설명 |
| --- | --- | --- | --- |
| `pushEnabled` | Boolean | Y | 채팅 푸시 알림 수신 여부. `true`/`false` 불리언만 허용 |

### 처리 기준

- 회원 상태가 `ACTIVE`이고 탈퇴하지 않은 회원만 변경한다.
- 요청자는 삭제되지 않은 스터디의 활성 멤버(`study_member.status = ACTIVE`)여야 한다.
- `pushEnabled`는 엄격한 불리언으로 역직렬화한다. 문자열·숫자 등 불리언이 아닌 값은 `400`이다.
- 설정은 스터디 멤버 행(`study_member.chat_push_enabled`)에 저장한다.

### Response

#### 200 OK

```json
{
  "success": true,
  "code": "CHAT_NOTIFICATION_SETTING_UPDATED",
  "message": "채팅 푸시 알림 설정을 변경했습니다.",
  "data": {
    "studyId": 7,
    "pushEnabled": false
  },
  "timestamp": "2026-08-06T15:00:00+09:00"
}
```

### Exception

- `400 COMMON_INVALID_REQUEST` — `pushEnabled` 누락 또는 불리언이 아닌 값
- `401 AUTH_ACCESS_TOKEN_INVALID`
- `403 CHAT_FORBIDDEN` — 해당 스터디의 활성 멤버가 아님
- `404 MEMBER_NOT_FOUND` — 회원이 없거나 활성 상태가 아님
- `404 STUDY_NOT_FOUND` — 스터디가 없거나 삭제됨

---

## S3 업로드 URL 발급

Domain: Media
Method: POST
Progress: 완료
URI: /api/v1/media/presigned-urls
담당자: 김윤석
연동여부: No

현장 사진, STT 임시 음성, 채팅 사진, 게시글 첨부 파일을 S3에 직접 업로드하기 위한 Presigned URL과 파일 메타 ID를 발급한다.

파일을 S3에 직접 업로드하기 위한 Presigned URL을 발급한다.

현장 사진·채팅 사진 등을 올리기 전에 호출하며, 발급받은 URL로 앱이 S3에 직접 업로드한 뒤 완료 처리 API를 호출한다. 서버는 업로드 자리(파일 메타)를 미리 생성해 둔다.

### Request

- HTTP Method: `POST`
- URI: `/api/v1/media/presigned-urls`
- 인증 필요: 필요
- Content-Type: `application/json`

#### Request Body

```json
{
  "fileUsage": "FIELD_PHOTO",
  "originalName": "entrance.jpg",
  "contentType": "image/jpeg",
  "sizeBytes": 1048576,
  "studyId": 7
}
```

| 필드 | 타입 | 필수 | 설명 |
| --- | --- | --- | --- |
| `fileUsage` | String | Y | 파일 사용 목적 |
| `originalName` | String | N | 원본 파일명 |
| `contentType` | String | Y | MIME 타입 |
| `sizeBytes` | Long | Y | 파일 크기, 바이트 |
| `studyId` | Long | 조건부 | 스터디 관련 파일이면 필수 |

### File Usage

```
FIELD_PHOTO
STT_AUDIO
CHAT_IMAGE
POST_ATTACHMENT
```

기존 `fileType`, `purpose`, `FIELD_RECORD`, `CHAT`, `PROFILE` 값은 사용하지 않는다.

#### Request Header

```
Authorization: Bearer {accessToken}
```

### 처리 기준

- `FIELD_PHOTO`, `STT_AUDIO`, `CHAT_IMAGE`는 요청자가 해당 스터디의 `ACTIVE` 멤버여야 한다.
- `POST_ATTACHMENT`는 `studyId` 없이 사용할 수 있다.
- `contentType`과 `sizeBytes`는 `fileUsage`별 허용 정책을 검증한다.
- 파일 메타를 `uploadStatus=PENDING`으로 생성한다.
- `STT_AUDIO`는 STT 재처리 보존 기간을 기준으로 `expiresAt`을 저장한다.
- 사진·게시글 첨부는 일반적으로 `expiresAt=null`이다.
- 버킷 퍼블릭 접근을 차단하고 발급된 URL로만 업로드한다.

### Response

#### 201 Created

```json
{
  "success": true,
  "code": "MEDIA_PRESIGNED_URL_ISSUED",
  "message": "업로드 URL을 발급했습니다.",
  "data": {
    "fileId": 89,
    "fileUsage": "FIELD_PHOTO",
    "uploadUrl": "<https://s3>.../field-photo/7/42/uuid.jpg?...",
    "uploadStatus": "PENDING",
    "uploadUrlExpiresAt": "2026-07-24T19:40:00+09:00",
    "fileExpiresAt": null
  },
  "timestamp": "2026-07-24T19:30:00+09:00"
}
```

`STT_AUDIO` 예시:

```json
{
  "fileId": 90,
  "fileUsage": "STT_AUDIO",
  "uploadStatus": "PENDING",
  "uploadUrlExpiresAt": "2026-07-24T19:40:00+09:00",
  "fileExpiresAt": "2026-07-25T19:30:00+09:00"
}
```

### Exception

- `400 MEDIA_FILE_USAGE_INVALID`
- `400 MEDIA_CONTENT_TYPE_INVALID`
- `400 MEDIA_FILE_SIZE_EXCEEDED`
- `401 AUTH_ACCESS_TOKEN_INVALID`
- `403 MEDIA_STUDY_FORBIDDEN`
- `404 STUDY_NOT_FOUND`
- `500 COMMON_INTERNAL_SERVER_ERROR`

### 프론트 처리

```
파일 선택·촬영·녹음 완료
→ fileUsage별 Presigned URL 요청
→ uploadUrl로 S3 PUT
→ 성공 후 파일 업로드 완료 처리 호출
```

---

## 팔로잉 사용자에게 MVP 쪽지 전송

Method: POST
Progress: 완료
URI: /api/v1/members/{memberId}/messages
담당자: 박재명
연동여부: Yes

로그인한 회원이 현재 팔로잉 중인 사용자에게 최대 500자의 텍스트 메시지를 일회성으로 전송한다.

메시지는 정식 대화방에 저장하지 않고 수신자의 알림함에 `MESSAGE` 알림 한 건으로 생성한다. 현재 MVP에서는 대화방, 메시지 이력, 별도 읽음 상태, 차단과 신고 기능을 제공하지 않는다.

---

### Request

- Request HTTP Method: `POST`
- 인증 필요: 필요
- Content-Type: `application/json`

#### Request Header

```
Authorization: Bearer {accessToken}
```

#### Path Variable

```
POST /api/v1/members/12/messages
```

| 필드 | 타입 | 필수 | 설명 |
| --- | --- | --- | --- |
| `memberId` | Long | Y | 메시지를 받을 대상 회원 ID |

#### Request Body

```
{
  "content": "다음 임장도 같이 참여해요!",
  "clientMessageId": "8e70e108-7c81-477a-bb55-a9f334fb5e67"
}
```

| 필드 | 타입 | 필수 | 설명 |
| --- | --- | --- | --- |
| `content` | String | Y | 앞뒤 공백 제거 후 1자 이상 500자 이하의 메시지 |
| `clientMessageId` | String | Y | 중복 전송 방지용 UUID |

---

### 처리 기준

#### 1. 회원 확인

- 로그인 회원 ID는 Access Token에서 확인한다.
- Path Variable의 `memberId`에 해당하는 대상 회원을 조회한다.
- 발신자와 수신자 모두 `ACTIVE` 상태여야 한다.
- 존재하지 않거나 탈퇴한 대상 회원은 동일하게 회원을 찾을 수 없는 것으로 처리한다.
- 본인에게 메시지를 전송할 수 없다.

#### 2. 팔로잉 관계 확인

- 로그인 회원이 대상 회원을 현재 팔로잉하고 있어야 한다.
- 다음 단방향 관계가 존재하는지 확인한다.

```
SELECT id
FROM follow
WHERE follower_id = :currentMemberId
  AND following_id = :targetMemberId;
```

- 상대방이 로그인 회원을 팔로우하는지는 전송 조건에 영향을 주지 않는다.
- 팔로우 해제 후에는 새 메시지를 전송할 수 없다.

#### 3. 메시지 검증

- `content`의 앞뒤 공백을 제거한 뒤 저장한다.
- 공백을 제거한 결과가 비어 있으면 요청을 거절한다.
- 메시지는 1자 이상 500자 이하로 제한한다.
- HTML 태그와 실행 가능한 스크립트는 일반 텍스트로 처리하거나 서버 정책에 따라 제거한다.
- 현재 MVP는 텍스트만 지원하며 이미지, 파일, URL 미리보기와 답장 기능을 제공하지 않는다.

#### 4. 멱등성

- `clientMessageId`는 UUID 형식이어야 한다.
- 서버의 `notification.idempotency_key`는 발신자와 클라이언트 메시지 ID를 조합해 생성한다.

```
member-message:{senderId}:{clientMessageId}
```

- 동일한 발신자가 같은 `clientMessageId`로 재요청하면 새로운 알림을 만들지 않고 기존 전송 결과를 반환한다.
- 같은 `clientMessageId`라도 발신자가 다르면 별도의 요청으로 처리할 수 있다.
- 네트워크 재시도로 중복 FCM Push가 발송되지 않도록 알림 생성과 발송 이벤트를 멱등하게 처리한다.

#### 5. 알림 생성

수신자에게 다음 값을 가진 `notification` 한 건을 생성한다.

```
recipient_id = 대상 회원 ID
actor_id = 로그인 회원 ID
category = MESSAGE
type = MESSAGE
target_screen = MEMBER_PROFILE
target_id = 발신 회원 ID
target_sub_id = null
body = 정제된 메시지 내용
idempotency_key = member-message:{senderId}:{clientMessageId}
```

- `title`은 `새로운 메시지가 도착했어요`와 같이 서버에서 생성한다.
- 정식 메시지 테이블이나 대화방은 생성하지 않는다.
- 메시지는 알림 본문으로 보관되며 Notification API를 통해 조회한다.

#### 6. FCM Push

- 수신자가 서비스 알림 수신에 동의하고 유효한 FCM 토큰이 있으면 Push를 발송한다.
- Push 발송 실패가 `notification` 저장 자체를 롤백하지 않는다.
- 발송 결과는 `send_status`와 `fail_reason`에 기록할 수 있다.
- Push가 실패해도 수신자는 앱의 알림 목록에서 메시지를 확인할 수 있다.

#### 7. 현재 MVP 제외 범위

- 대화방 생성 및 조회
- 메시지 대화 이력
- 메시지별 별도 읽음 상태
- 답장 스레드
- 사용자 차단과 신고
- 이미지·파일 메시지

---

### Response

#### 201 Created — 메시지 전송 완료

```
{
  "success": true,
  "code": "MEMBER_MESSAGE_SEND_SUCCESS",
  "message": "쪽지를 보냈습니다.",
  "data": {
    "recipientId": 12,
    "recipientNickname": "옥수탐방러",
    "notificationId": 81,
    "clientMessageId": "8e70e108-7c81-477a-bb55-a9f334fb5e67",
    "sentAt": "2026-07-25T11:00:00+09:00"
  },
  "timestamp": "2026-07-25T11:00:00+09:00"
}
```

#### 200 OK — 이미 전송된 요청

```
{
  "success": true,
  "code": "MEMBER_MESSAGE_ALREADY_SENT",
  "message": "이미 전송된 쪽지입니다.",
  "data": {
    "recipientId": 12,
    "recipientNickname": "옥수탐방러",
    "notificationId": 81,
    "clientMessageId": "8e70e108-7c81-477a-bb55-a9f334fb5e67",
    "sentAt": "2026-07-25T11:00:00+09:00"
  },
  "timestamp": "2026-07-25T11:00:02+09:00"
}
```

동일한 `clientMessageId`로 재요청한 경우 기존 결과를 반환하며 새 알림과 새 Push를 생성하지 않는다.

#### Response Field

| 필드 | 타입 | 설명 |
| --- | --- | --- |
| `recipientId` | Long | 메시지 수신 회원 ID |
| `recipientNickname` | String | 수신 회원 닉네임 |
| `notificationId` | Long | 생성되었거나 기존에 존재하는 메시지 알림 ID |
| `clientMessageId` | String | 클라이언트 중복 방지 UUID |
| `sentAt` | String | 최초 메시지 알림 생성 시각 |

---

### Exception

#### 400 Bad Request — 잘못된 회원 ID

```
{
  "success": false,
  "code": "COMMON_INVALID_REQUEST",
  "message": "입력값을 확인해 주세요.",
  "data": {
    "field": "memberId",
    "reason": "회원 ID는 1 이상의 숫자여야 합니다."
  },
  "timestamp": "2026-07-25T11:00:00+09:00"
}
```

#### 400 Bad Request — 본인에게 전송

```
{
  "success": false,
  "code": "MEMBER_MESSAGE_SELF_NOT_ALLOWED",
  "message": "본인에게 쪽지를 보낼 수 없습니다.",
  "data": null,
  "timestamp": "2026-07-25T11:00:00+09:00"
}
```

#### 400 Bad Request — 빈 메시지

```
{
  "success": false,
  "code": "MEMBER_MESSAGE_CONTENT_REQUIRED",
  "message": "쪽지 내용을 입력해 주세요.",
  "data": {
    "field": "content"
  },
  "timestamp": "2026-07-25T11:00:00+09:00"
}
```

#### 400 Bad Request — 메시지 길이 초과

```
{
  "success": false,
  "code": "MEMBER_MESSAGE_CONTENT_TOO_LONG",
  "message": "쪽지는 500자 이하로 입력해 주세요.",
  "data": {
    "field": "content",
    "maxLength": 500
  },
  "timestamp": "2026-07-25T11:00:00+09:00"
}
```

#### 400 Bad Request — UUID 형식 오류

```
{
  "success": false,
  "code": "COMMON_INVALID_REQUEST",
  "message": "입력값을 확인해 주세요.",
  "data": {
    "field": "clientMessageId",
    "reason": "올바른 UUID 형식이 아닙니다."
  },
  "timestamp": "2026-07-25T11:00:00+09:00"
}
```

#### 401 Unauthorized

```
{
  "success": false,
  "code": "AUTH_ACCESS_TOKEN_INVALID",
  "message": "로그인이 필요합니다.",
  "data": null,
  "timestamp": "2026-07-25T11:00:00+09:00"
}
```

#### 403 Forbidden — 팔로잉 관계 없음

```
{
  "success": false,
  "code": "MEMBER_MESSAGE_FOLLOW_REQUIRED",
  "message": "팔로잉 중인 사용자에게만 쪽지를 보낼 수 있습니다.",
  "data": null,
  "timestamp": "2026-07-25T11:00:00+09:00"
}
```

#### 404 Not Found — 회원 없음

```
{
  "success": false,
  "code": "MEMBER_NOT_FOUND",
  "message": "회원 정보를 찾을 수 없습니다.",
  "data": null,
  "timestamp": "2026-07-25T11:00:00+09:00"
}
```

#### 500 Internal Server Error

```
{
  "success": false,
  "code": "COMMON_INTERNAL_SERVER_ERROR",
  "message": "일시적인 오류가 발생했습니다.",
  "data": null,
  "timestamp": "2026-07-25T11:00:00+09:00"
}
```

---

### 프론트 처리

```
다른 사용자 공개 프로필 또는 팔로잉 목록 진입
→ isFollowing = true, canSendMessage = true인 경우 쪽지 버튼 표시
→ 버튼 선택 시 대상 닉네임과 메시지 입력 모달 표시

메시지 입력
→ 앞뒤 공백 제거 후 1~500자 검증
→ 최초 전송 시 UUID clientMessageId 생성
→ 전송 중 버튼 비활성화
→ 같은 전송을 재시도할 때는 동일한 clientMessageId 사용

전송 성공 또는 MEMBER_MESSAGE_ALREADY_SENT
→ "쪽지를 보냈어요" 안내
→ 입력 모달 닫기
→ 대화방으로 이동하지 않음

MEMBER_MESSAGE_FOLLOW_REQUIRED
→ canSendMessage = false로 변경
→ 쪽지 버튼 숨김 또는 비활성화
→ "팔로잉 중인 사용자에게만 보낼 수 있습니다." 안내

전송 실패
→ 입력 내용 유지
→ 재시도 버튼 표시
→ 재시도 시 기존 clientMessageId 유지
```

---

## 스터디 신청 거절

Method: PATCH
Progress: 완료
URI: /api/v1/studies/{studyId}/applications/{applicationId}/reject
담당자: 최태선
연동여부: Yes

스터디장이 `PENDING` 신청을 거절한다. 최종 ERD에 거절 사유 컬럼이 없으므로 Request Body를 받지 않는다.

스터디장이 참여 신청을 거절한다.

거절된 신청자는 스터디 멤버로 등록되지 않으며, 승인된 참여 인원에도 포함되지 않는다.

### Request

- HTTP Method: `PATCH`
- URI: `/api/v1/studies/{studyId}/applications/{applicationId}/reject`
- 인증 필요: 필요
- Request Body: 없음

#### Request Header

```
Authorization: Bearer {accessToken}
```

#### Path Variable

```
PATCH /api/v1/studies/10/applications/25/reject
```

| 필드 | 타입 | 필수 | 설명 |
| --- | --- | --- | --- |
| `studyId` | Long | Y | 신청이 접수된 스터디 ID |
| `applicationId` | Long | Y | 거절할 신청 ID |

### 처리 기준

- 스터디장만 거절할 수 있다.
- 신청이 요청한 스터디에 속해야 한다.
- `PENDING` 신청만 `REJECTED`로 변경한다.
- `decidedAt`에 현재 시각을 저장한다.
- 기존 `reason` 요청·응답·검증·알림 문구는 삭제한다.

### 추가 상세 처리 기준

- 로그인한 회원 ID는 Access Token에서 확인한다.
- `studyId`에 해당하는 스터디가 존재하는지 확인한다.
- 해당 스터디의 스터디장만 신청을 거절할 수 있다.
- 일반 참여자는 신청을 거절할 수 없다.

---

- `applicationId`에 해당하는 신청이 존재하는지 확인한다.
- 신청이 요청한 `studyId`에 속하는지 확인한다.
- `PENDING` 상태인 신청만 거절할 수 있다.
- 이미 승인되거나 거절된 신청은 다시 처리할 수 없다.

---

- 신청 상태를 `REJECTED`로 변경한다.
- 거절 처리 시각을 서버 시간으로 저장한다.
- 거절 사유가 전달되면 앞뒤 공백을 제거한 후 저장한다.
- 거절된 신청자는 스터디 멤버로 등록하지 않는다.
- 현재 참여 인원과 스터디 모집 상태는 변경하지 않는다.

---

- 신청자에게 스터디 신청 거절 알림을 생성한다.
- 거절 사유가 있으면 알림 또는 신청 결과 화면에서 확인할 수 있다.
- 푸시 알림 발송 여부는 신청자의 서비스 알림 수신 설정을 따른다.

---

### Response

#### 200 OK

```json
{
  "success": true,
  "code": "STUDY_APPLICATION_REJECT_SUCCESS",
  "message": "스터디 신청을 거절했습니다.",
  "data": {
    "applicationId": 25,
    "studyId": 10,
    "applicant": {
      "memberId": 12,
      "nickname": "옥수탐방러",
      "selectedCharacterId": "PALBANG"
    },
    "status": "REJECTED",
    "decidedAt": "2026-07-24T18:20:00+09:00"
  },
  "timestamp": "2026-07-24T18:20:00+09:00"
}
```

### Exception

- `400 STUDY_APPLICATION_STUDY_MISMATCH`
- `401 AUTH_ACCESS_TOKEN_INVALID`
- `403 STUDY_APPLICATION_REJECT_FORBIDDEN`
- `404 STUDY_NOT_FOUND`
- `404 STUDY_APPLICATION_NOT_FOUND`
- `409 STUDY_APPLICATION_ALREADY_PROCESSED`
- `409 STUDY_APPLICATION_REJECT_NOT_ALLOWED`

---

### 추가 Exception 예시

#### 400 Bad Request — 다른 스터디의 신청

```json
{
  "success": false,
  "code": "STUDY_APPLICATION_STUDY_MISMATCH",
  "message": "해당 스터디의 신청 정보가 아닙니다.",
  "data": null,
  "timestamp": "2026-07-22T19:35:00+09:00"
}
```

---

#### 401 Unauthorized

```json
{
  "success": false,
  "code": "AUTH_ACCESS_TOKEN_INVALID",
  "message": "로그인이 필요합니다.",
  "data": null,
  "timestamp": "2026-07-22T19:35:00+09:00"
}
```

---

#### 403 Forbidden — 스터디장 아님

```json
{
  "success": false,
  "code": "STUDY_APPLICATION_REJECT_FORBIDDEN",
  "message": "스터디 신청을 거절할 권한이 없습니다.",
  "data": null,
  "timestamp": "2026-07-22T19:35:00+09:00"
}
```

---

#### 404 Not Found — 스터디 없음

```json
{
  "success": false,
  "code": "STUDY_NOT_FOUND",
  "message": "스터디를 찾을 수 없습니다.",
  "data": null,
  "timestamp": "2026-07-22T19:35:00+09:00"
}
```

---

#### 404 Not Found — 신청 없음

```json
{
  "success": false,
  "code": "STUDY_APPLICATION_NOT_FOUND",
  "message": "스터디 신청 정보를 찾을 수 없습니다.",
  "data": null,
  "timestamp": "2026-07-22T19:35:00+09:00"
}
```

---

#### 409 Conflict — 이미 처리된 신청

```json
{
  "success": false,
  "code": "STUDY_APPLICATION_ALREADY_PROCESSED",
  "message": "이미 처리된 스터디 신청입니다.",
  "data": {
    "status": "APPROVED"
  },
  "timestamp": "2026-07-22T19:35:00+09:00"
}
```

---

#### 409 Conflict — 처리 불가 상태

```json
{
  "success": false,
  "code": "STUDY_APPLICATION_REJECT_NOT_ALLOWED",
  "message": "현재 상태에서는 신청을 거절할 수 없습니다.",
  "data": {
    "studyStatus": "CANCELED"
  },
  "timestamp": "2026-07-22T19:35:00+09:00"
}
```

---

#### 500 Internal Server Error

```json
{
  "success": false,
  "code": "COMMON_INTERNAL_SERVER_ERROR",
  "message": "일시적인 오류가 발생했습니다.",
  "data": null,
  "timestamp": "2026-07-22T19:35:00+09:00"
}
```

---

### 추가 프론트 처리 기준

```
신청자 목록에서 거절 선택
→ 필요하면 거절 사유 입력
→ PATCH /api/v1/studies/{studyId}/applications/{applicationId}/reject 호출

거절 성공
→ 신청 상태를 REJECTED로 변경
→ 승인·거절 버튼 숨김
→ 대기 신청 수 갱신

STUDY_APPLICATION_ALREADY_PROCESSED 발생
→ 응답의 현재 신청 상태로 목록 갱신

STUDY_APPLICATION_REJECT_FORBIDDEN 발생
→ "스터디장만 신청을 처리할 수 있습니다." 안내
```

---

## 스터디 생성

Domain: Study
Method: POST
Progress: 완료
URI: /api/v1/studies
담당자: 최태선
연동여부: Yes

특정 아파트를 대상으로 임장 스터디를 생성한다. 스터디 생성과 임장 일정 등록은 분리한다.

로그인한 회원이 특정 아파트를 대상으로 임장 스터디를 생성한다.

### Request

- HTTP Method: `POST`
- URI: `/api/v1/studies`
- 인증 필요: 필요
- Content-Type: `application/json`

#### Request Body

```json
{
  "apartmentId": 15,
  "title": "옥수동 주말 임장",
  "intro": "교통과 단지 환경을 함께 확인합니다.",
  "goal": "역 접근성·단지 경사·주변 소음을 확인합니다.",
  "capacity": 6,
  "purpose": "RESIDENCE"
}
```

| 필드 | 타입 | 필수 | 설명 |
| --- | --- | --- | --- |
| `apartmentId` | Long | Y | 대상 아파트 ID |
| `title` | String | Y | 스터디 제목, 1~200자 |
| `intro` | String | N | 스터디 소개 |
| `goal` | String | Y | 함께 확인할 스터디 목표 |
| `capacity` | Integer | Y | 스터디장 포함 최대 인원, 1명 이상 20명 이하 |
| `purpose` | String | Y | `RESIDENCE`, `INVESTMENT`, `STUDY` |

#### Request Header

```
Authorization: Bearer {accessToken}
```

### 처리 기준

- 로그인 회원을 스터디장으로 저장한다.
- 스터디장은 생성 즉시 `study_member.role=LEADER`, `status=ACTIVE`로 등록한다.
- 최초 스터디 상태는 `RECRUITING`이다.
- 정원이 1명이면 생성 즉시 모집을 마감한다(`status=CLOSED`). 1인 임장을 허용하되 신청을 받지 않는다.
- `goal`은 공백일 수 없다.
- 생성 Request에서 일정·집결 장소·좌표·`applicationMessageRequired`를 받지 않는다.
- 일정은 생성 후 `POST /api/v1/studies/{studyId}/schedule`로 등록한다.

### 추가 상세 처리 기준

- 로그인한 회원 ID는 Access Token에서 확인한다.
- 회원 상태가 `ACTIVE`인 경우에만 스터디를 생성할 수 있다.
- `apartmentId`에 해당하는 아파트가 존재하는지 확인한다.
- 서비스에서 조회 가능한 아파트만 임장 대상으로 선택할 수 있다.

---

- 스터디를 생성한 회원을 스터디장으로 설정한다.
- 스터디장은 생성과 동시에 참여 멤버로 등록한다.
- 스터디장의 참여 상태는 `APPROVED`로 저장한다.
- 스터디장은 최대 참여 인원에 포함한다.

---

- 공백만 입력된 값은 허용하지 않는다.
- 임장 일시는 현재 시각 이후여야 한다.
- 최대 참여 인원은 1명 이상 20명 이하로 설정한다.

---

- 생성된 스터디의 모집 상태는 `RECRUITING`으로 설정한다.
- 생성 직후 다른 회원이 스터디에 신청할 수 있다.
- 현재 참여 인원은 스터디장을 포함하여 1명으로 시작한다.
- 모집 인원이 모두 차면 모집 상태를 `CLOSED`로 변경할 수 있다.
- 생성 후 스터디 일정 수정 API를 통해 변경할 수 있다.
- 일정이 변경되면 승인된 스터디 참여자에게 알림을 생성할 수 있다.

---

### Response

#### 201 Created

```json
{
  "success": true,
  "code": "STUDY_CREATE_SUCCESS",
  "message": "스터디가 생성되었습니다.",
  "data": {
    "studyId": 10,
    "title": "옥수동 주말 임장",
    "intro": "교통과 단지 환경을 함께 확인합니다.",
    "goal": "역 접근성·단지 경사·주변 소음을 확인합니다.",
    "purpose": "RESIDENCE",
    "status": "RECRUITING",
    "capacity": 6,
    "currentMemberCount": 1,
    "apartment": {
      "apartmentId": 15,
      "name": "래미안 옥수 리버젠",
      "address": "서울특별시 성동구 매봉길 15"
    },
    "leader": {
      "memberId": 7,
      "nickname": "집보는다람쥐",
      "selectedCharacterId": "JIPKONG"
    },
    "createdAt": "2026-07-24T18:00:00+09:00"
  },
  "timestamp": "2026-07-24T18:00:00+09:00"
}
```

### Exception

- `400 COMMON_INVALID_REQUEST`
- `400 STUDY_CAPACITY_INVALID`
- `400 STUDY_PURPOSE_INVALID`
- `401 AUTH_ACCESS_TOKEN_INVALID`
- `404 APARTMENT_NOT_FOUND`
- `500 COMMON_INTERNAL_SERVER_ERROR`

### 추가 Exception 예시

#### 401 Unauthorized

```json
{
  "success": false,
  "code": "AUTH_ACCESS_TOKEN_INVALID",
  "message": "로그인이 필요합니다.",
  "data": null,
  "timestamp": "2026-07-22T17:00:00+09:00"
}
```

---

#### 404 Not Found — 아파트 없음

```json
{
  "success": false,
  "code": "APARTMENT_NOT_FOUND",
  "message": "아파트 정보를 찾을 수 없습니다.",
  "data": null,
  "timestamp": "2026-07-22T17:00:00+09:00"
}
```

---

#### 500 Internal Server Error

```json
{
  "success": false,
  "code": "COMMON_INTERNAL_SERVER_ERROR",
  "message": "일시적인 오류가 발생했습니다.",
  "data": null,
  "timestamp": "2026-07-22T17:00:00+09:00"
}
```

---

### 프론트 처리

```
스터디 생성 성공
→ 생성된 studyId의 내 스터디 상세로 이동
→ 필요하면 일정 등록 화면으로 이동
```

---

### 추가 프론트 처리 기준

```
스터디 만들기 선택
→ 임장 대상 아파트 선택
→ 스터디 정보와 일정 입력
→ POST /api/v1/studies 호출

스터디 생성 성공
→ 생성된 studyId 확인
→ GET /api/v1/studies/{studyId} 호출
→ 스터디 홈 화면으로 이동

STUDY_SCHEDULE_INVALID 발생
→ 일정 입력 영역에 오류 표시
→ 미래 일시로 다시 선택하도록 안내

APARTMENT_NOT_FOUND 발생
→ 아파트 선택 화면으로 이동
→ 조회 가능한 아파트를 다시 선택
```

---

## 스터디 신청 승인

Method: PATCH
Progress: 완료
URI: /api/v1/studies/{studyId}/applications/{applicationId}/approve
담당자: 최태선
연동여부: Yes

스터디장이 참여 신청을 승인하여 신청자를 스터디 멤버로 등록한다.

승인된 신청자는 스터디 공지, 일정, 채팅과 임장 기능을 이용할 수 있다.

---

### Request

- Request HTTP Method: `PATCH`
- 인증 필요: 필요
- Request Body: 없음

#### Request Header

```
Authorization: Bearer {accessToken}
```

#### Path Variable

```
PATCH /api/v1/studies/10/applications/25/approve
```

| 필드 | 타입 | 필수 | 설명 |
| --- | --- | --- | --- |
| `studyId` | Long | Y | 신청이 접수된 스터디 ID |
| `applicationId` | Long | Y | 승인할 신청 ID |

---

### 처리 기준

#### 1. 스터디 및 권한 확인

- 로그인한 회원 ID는 Access Token에서 확인한다.
- `studyId`에 해당하는 스터디가 존재하는지 확인한다.
- 해당 스터디의 스터디장만 신청을 승인할 수 있다.
- 일반 참여자는 신청을 승인할 수 없다.

---

#### 2. 신청 확인

- `applicationId`에 해당하는 신청이 존재하는지 확인한다.
- 신청이 요청한 `studyId`에 속하는지 확인한다.
- `PENDING` 상태인 신청만 승인할 수 있다.
- 이미 승인되거나 거절된 신청은 다시 승인할 수 없다.

---

#### 3. 모집 상태 및 정원 확인

- 스터디가 `RECRUITING` 상태인 경우에만 승인할 수 있다.
- 현재 승인 멤버 수이 최대 정원보다 적어야 한다.
- 승인 처리 시점에 정원이 모두 찬 경우 승인할 수 없다.
- 승인 후 참여 인원이 최대 인원에 도달하면 모집 상태를 `CLOSED`로 변경할 수 있다.

---

#### 4. 승인 처리

- 신청 상태를 `APPROVED`로 변경한다.
- 신청자를 스터디 멤버로 등록한다.
- 스터디의 현재 승인 멤버 수을 1 증가시킨다.
- 승인 처리 시각을 서버 시간으로 저장한다.
- 동일 회원이 중복으로 멤버 등록되지 않도록 처리한다.

---

#### 5. 알림 처리

- 승인된 신청자에게 스터디 신청 승인 알림을 생성한다.
- 알림 선택 시 해당 스터디 홈으로 이동한다.
- 푸시 알림 발송 여부는 회원의 서비스 알림 수신 설정을 따른다.

---

### Response

#### 200 OK

```
{
  "success": true,
  "code": "STUDY_APPLICATION_APPROVE_SUCCESS",
  "message": "스터디 신청을 승인했습니다.",
  "data": {
    "applicationId": 25,
    "studyId": 10,
    "applicant": {
      "memberId": 12,
      "nickname": "옥수탐방러",
      "profileImageUrl": "<https://cdn.example.com/profiles/12.jpg>",
      "selectedCharacterId": "PALBANG"
    },
    "status": "APPROVED",
    "currentMemberCount": 5,
    "capacity": 6,
    "studyStatus": "RECRUITING",
    "decidedAt": "2026-07-22T19:30:00+09:00"
  },
  "timestamp": "2026-07-22T19:30:00+09:00"
}
```

---

### Response Field

| 필드 | 타입 | 설명 |
| --- | --- | --- |
| `applicationId` | Long | 승인된 신청 ID |
| `studyId` | Long | 스터디 ID |
| `applicant` | Object | 승인된 신청자 정보 |
| `status` | String | 변경된 신청 상태. `APPROVED` |
| `currentMemberCount` | Integer | 승인 후 현재 승인 멤버 수 |
| `capacity` | Integer | 최대 정원 |
| `studyStatus` | String | 승인 후 스터디 상태 |
| `decidedAt` | String | 승인 처리 시각 |

---

### 정원이 모두 찬 경우

승인 후 참여 인원이 최대 인원에 도달하면 다음과 같이 반환할 수 있다.

```
{
  "success": true,
  "code": "STUDY_APPLICATION_APPROVE_SUCCESS",
  "message": "스터디 신청을 승인했습니다.",
  "data": {
    "applicationId": 25,
    "studyId": 10,
    "applicant": {
      "memberId": 12,
      "nickname": "옥수탐방러",
      "profileImageUrl": "<https://cdn.example.com/profiles/12.jpg>",
      "selectedCharacterId": "PALBANG"
    },
    "status": "APPROVED",
    "currentMemberCount": 6,
    "capacity": 6,
    "studyStatus": "CLOSED",
    "decidedAt": "2026-07-22T19:30:00+09:00"
  },
  "timestamp": "2026-07-22T19:30:00+09:00"
}
```

---

### Exception

#### 401 Unauthorized

```
{
  "success": false,
  "code": "AUTH_ACCESS_TOKEN_INVALID",
  "message": "로그인이 필요합니다.",
  "data": null,
  "timestamp": "2026-07-22T19:30:00+09:00"
}
```

---

#### 403 Forbidden — 스터디장 아님

```
{
  "success": false,
  "code": "STUDY_APPLICATION_APPROVE_FORBIDDEN",
  "message": "스터디 신청을 승인할 권한이 없습니다.",
  "data": null,
  "timestamp": "2026-07-22T19:30:00+09:00"
}
```

---

#### 404 Not Found — 스터디 없음

```
{
  "success": false,
  "code": "STUDY_NOT_FOUND",
  "message": "스터디를 찾을 수 없습니다.",
  "data": null,
  "timestamp": "2026-07-22T19:30:00+09:00"
}
```

---

#### 404 Not Found — 신청 없음

```
{
  "success": false,
  "code": "STUDY_APPLICATION_NOT_FOUND",
  "message": "스터디 신청 정보를 찾을 수 없습니다.",
  "data": null,
  "timestamp": "2026-07-22T19:30:00+09:00"
}
```

---

#### 400 Bad Request — 다른 스터디의 신청

```
{
  "success": false,
  "code": "STUDY_APPLICATION_STUDY_MISMATCH",
  "message": "해당 스터디의 신청 정보가 아닙니다.",
  "data": null,
  "timestamp": "2026-07-22T19:30:00+09:00"
}
```

---

#### 409 Conflict — 이미 처리된 신청

```
{
  "success": false,
  "code": "STUDY_APPLICATION_ALREADY_PROCESSED",
  "message": "이미 처리된 스터디 신청입니다.",
  "data": {
    "status": "REJECTED"
  },
  "timestamp": "2026-07-22T19:30:00+09:00"
}
```

---

#### 409 Conflict — 모집 정원 초과

```
{
  "success": false,
  "code": "STUDY_CAPACITY_FULL",
  "message": "스터디 모집 인원이 모두 찼습니다.",
  "data": {
    "currentMemberCount": 6,
    "capacity": 6
  },
  "timestamp": "2026-07-22T19:30:00+09:00"
}
```

---

#### 409 Conflict — 승인 불가 상태

```
{
  "success": false,
  "code": "STUDY_APPLICATION_APPROVE_NOT_ALLOWED",
  "message": "현재 상태에서는 신청을 승인할 수 없습니다.",
  "data": {
    "studyStatus": "CANCELED"
  },
  "timestamp": "2026-07-22T19:30:00+09:00"
}
```

---

#### 500 Internal Server Error

```
{
  "success": false,
  "code": "COMMON_INTERNAL_SERVER_ERROR",
  "message": "일시적인 오류가 발생했습니다.",
  "data": null,
  "timestamp": "2026-07-22T19:30:00+09:00"
}
```

---

### 프론트 처리

```
신청자 목록에서 승인 선택
→ 승인 확인 팝업 표시
→ PATCH /api/v1/studies/{studyId}/applications/{applicationId}/approve 호출

승인 성공
→ 신청 상태를 APPROVED로 변경
→ 승인·거절 버튼 숨김
→ 현재 승인 멤버 수 갱신

studyStatus = CLOSED
→ 모집 마감 상태로 변경
→ 남은 신청자의 승인 버튼 비활성화

STUDY_CAPACITY_FULL 발생
→ 승인 버튼 비활성화
→ "모집 인원이 모두 찼습니다." 안내

STUDY_APPLICATION_ALREADY_PROCESSED 발생
→ 응답의 신청 상태로 목록 갱신
```

---

## 신청자 목록 조회

Method: GET
Progress: 완료
URI: /api/v1/studies/{studyId}/applications
담당자: 최태선
연동여부: Yes

스터디장이 신청 상태별 신청자 목록을 조회한다.

스터디장이 해당 스터디에 참여를 신청한 회원 목록을 조회한다.

신청 상태별로 필터링할 수 있으며, 최근 신청 순으로 반환한다.

### Request

- HTTP Method: `GET`
- URI: `/api/v1/studies/{studyId}/applications`
- 인증 필요: 필요

#### Query Parameter

```
GET /api/v1/studies/10/applications?status=PENDING&cursor=25&size=20
```

#### Request Header

```
Authorization: Bearer {accessToken}
```

#### Path Variable

```
GET /api/v1/studies/10/applications
```

| 필드 | 타입 | 필수 | 설명 |
| --- | --- | --- | --- |
| `studyId` | Long | Y | 신청자 목록을 조회할 스터디 ID |

### 처리 기준

- 스터디장만 조회할 수 있다.
- `status`는 `PENDING`, `APPROVED`, `REJECTED` 중 하나다.
- 신청자의 `intro`, `purpose`, `createdAt`, `decidedAt`을 반환한다.
- `PENDING` 신청만 승인·거절할 수 있다.
- 기존 `message`, `processedAt` 필드는 `intro`, `decidedAt`으로 교체한다.

### 추가 상세 처리 기준

- 로그인한 회원 ID는 Access Token에서 확인한다.
- `studyId`에 해당하는 스터디가 존재하는지 확인한다.
- 해당 스터디의 스터디장만 신청자 목록을 조회할 수 있다.
- 일반 참여자와 신청자는 다른 신청자의 정보를 조회할 수 없다.

---

- 해당 스터디에 제출된 신청만 반환한다.
- `status`가 전달되면 해당 상태의 신청만 조회한다.
- 신청은 최근 신청 순으로 반환한다.
- 정렬 기준은 `applicationId DESC`로 한다.
- 신청자가 작성한 신청 메시지와 공개 프로필 정보를 함께 반환한다.
- 신청 목록이 없으면 오류가 아닌 빈 배열을 반환한다.

---

- `PENDING` 상태인 신청만 승인하거나 거절할 수 있다.
- 이미 승인 또는 거절된 신청은 `canProcess = false`로 반환한다.
- 모집이 마감되었거나 정원이 가득 찬 경우 승인 가능 여부를 `canApprove`로 반환한다.
- 스터디가 취소되거나 임장이 시작된 경우 신청을 처리할 수 없다.

---

- 첫 요청에는 `cursor`를 전달하지 않는다.
- `cursor`가 전달되면 `applicationId < cursor`인 신청을 조회한다.
- 요청한 `size`보다 한 건 더 조회해 다음 데이터 존재 여부를 판단한다.
- 다음 데이터가 있으면 마지막 신청 ID를 `nextCursor`로 반환한다.
- 다음 데이터가 없으면 `nextCursor = null`, `hasNext = false`로 반환한다.

---

### Response

#### 200 OK

```json
{
  "success": true,
  "code": "STUDY_APPLICATION_LIST_SUCCESS",
  "message": "신청자 목록 조회에 성공했습니다.",
  "data": {
    "content": [
      {
        "applicationId": 25,
        "applicant": {
          "memberId": 12,
          "nickname": "옥수탐방러",
          "selectedCharacterId": "PALBANG"
        },
        "intro": "옥수동에 실거주를 고려하고 있습니다.",
        "purpose": "RESIDENCE",
        "status": "PENDING",
        "canApprove": true,
        "canReject": true,
        "createdAt": "2026-07-24T18:10:00+09:00",
        "decidedAt": null
      }
    ],
    "summary": {
      "pendingCount": 1,
      "approvedCount": 3,
      "rejectedCount": 1,
      "currentMemberCount": 4,
      "capacity": 6
    },
    "nextCursor": null,
    "hasNext": false
  },
  "timestamp": "2026-07-24T18:15:00+09:00"
}
```

### Exception

- `400 STUDY_APPLICATION_STATUS_INVALID`
- `401 AUTH_ACCESS_TOKEN_INVALID`
- `403 STUDY_APPLICATION_LIST_FORBIDDEN`
- `404 STUDY_NOT_FOUND`
- `500 COMMON_INTERNAL_SERVER_ERROR`

---

### 추가 프론트 처리 기준

```
스터디 관리 화면에서 신청자 관리 선택
→ GET /api/v1/studies/{studyId}/applications 호출
→ 신청자 목록과 신청 현황 표시

status = PENDING
→ 승인 및 거절 버튼 표시

canApprove = false
→ 승인 버튼 비활성화
→ 모집 정원 또는 스터디 상태 확인

신청 상태 필터 선택
→ status를 Query Parameter로 전달
→ 선택한 상태의 신청만 표시

신청 승인 또는 거절 완료
→ 해당 신청 상태 갱신
→ summary와 참여 인원 다시 조회

STUDY_APPLICATION_LIST_FORBIDDEN 발생
→ "스터디장만 신청자를 관리할 수 있습니다." 안내
→ 스터디 홈으로 이동
```

---

## 스터디 신청

Method: POST
Progress: 완료
URI: /api/v1/studies/{studyId}/applications
담당자: 최태선
연동여부: No

한 줄 자기소개와 참여 목적을 제출해 모집 중인 스터디에 신청한다.

로그인한 회원이 모집 중인 임장 스터디에 참여를 신청한다.

회원은 한 줄 자기소개와 참여 목적을 제출하며, 신청 후 스터디장의 승인 또는 거절을 기다린다.

### Request

- HTTP Method: `POST`
- URI: `/api/v1/studies/{studyId}/applications`
- 인증 필요: 필요
- Content-Type: `application/json`

#### Request Body

```
{
  "intro": "옥수동에 실거주를 고려하고 있습니다.",
  "purpose": "RESIDENCE"
}
```

| 필드 | 타입 | 필수 | 설명 |
| --- | --- | --- | --- |
| `intro` | String | Y | 한 줄 자기소개, 최대 200자 |
| `purpose` | String | Y | `RESIDENCE`, `INVESTMENT`, `STUDY` |

#### Request Header

```
Authorization: Bearer {accessToken}
```

#### Path Variable

```
POST /api/v1/studies/10/applications
```

| 필드 | 타입 | 필수 | 설명 |
| --- | --- | --- | --- |
| `studyId` | Long | Y | 참여를 신청할 스터디 ID |

### 처리 기준

- `RECRUITING` 상태의 스터디에만 신청할 수 있다.
- 스터디장·이미 참여 중인 회원·기존 신청자는 다시 신청할 수 없다.
- 현재 활성 멤버 수가 정원에 도달하면 신청할 수 없다.
- 최초 상태는 `PENDING`이다.
- 기존 `message`, `applicationMessageRequired` 필드는 사용하지 않는다.

### 추가 상세 처리 기준

- 로그인한 회원 ID는 Access Token에서 확인한다.
- 회원 상태가 `ACTIVE`인 경우에만 신청할 수 있다.
- `studyId`에 해당하는 스터디가 존재하는지 확인한다.
- `RECRUITING` 상태의 스터디에만 신청할 수 있다.
- 취소되거나 모집이 마감된 스터디에는 신청할 수 없다.

---

- 스터디장은 자신의 스터디에 신청할 수 없다.
- 이미 승인된 참여자는 다시 신청할 수 없다.
- 승인 대기 중인 신청이 있으면 중복 신청할 수 없다.
- 이전 신청이 거절된 회원의 재신청 허용 여부는 운영 정책을 따른다.
- 현재 승인된 참여 인원이 최대 인원에 도달한 경우 신청할 수 없다.

---

- `intro`의 앞뒤 공백을 제거한 후 저장한다.
- `intro`는 한 줄 자기소개이며 최대 200자다.
- `intro`가 없거나 공백만 입력된 경우 신청할 수 없다.
- `purpose`는 `RESIDENCE`, `INVESTMENT`, `STUDY` 중 하나여야 한다.
- 기존 `message`, `applicationMessageRequired` 필드는 사용하지 않는다.

---

- 신청 생성 시 상태는 `PENDING`으로 설정한다.
- 스터디장이 승인하면 `APPROVED`로 변경한다.
- 스터디장이 거절하면 `REJECTED`로 변경한다.
- 신청만으로 현재 참여 인원은 증가하지 않는다.
- 승인된 경우에만 스터디 멤버로 등록하고 참여 인원을 증가시킨다.
- 신청이 생성되면 스터디장에게 새로운 신청 알림을 생성할 수 있다.
- 알림 선택 시 스터디 신청자 관리 화면으로 이동한다.
- 푸시 알림 발송 여부는 스터디장의 알림 수신 설정을 따른다.

---

### Response

#### 201 Created

```
{
  "success": true,
  "code": "STUDY_APPLICATION_CREATE_SUCCESS",
  "message": "스터디 참여를 신청했습니다.",
  "data": {
    "applicationId": 25,
    "studyId": 10,
    "applicant": {
      "memberId": 12,
      "nickname": "옥수탐방러",
      "selectedCharacterId": "PALBANG"
    },
    "intro": "옥수동에 실거주를 고려하고 있습니다.",
    "purpose": "RESIDENCE",
    "status": "PENDING",
    "createdAt": "2026-07-24T18:10:00+09:00"
  },
  "timestamp": "2026-07-24T18:10:00+09:00"
}
```

### Exception

- `400 COMMON_INVALID_REQUEST`
- `400 STUDY_APPLICATION_PURPOSE_INVALID`
- `401 AUTH_ACCESS_TOKEN_INVALID`
- `404 STUDY_NOT_FOUND`
- `409 STUDY_APPLICATION_ALREADY_EXISTS`
- `409 STUDY_ALREADY_MEMBER`
- `409 STUDY_APPLICATION_NOT_ALLOWED`
- `409 STUDY_CAPACITY_FULL`

---

### 추가 프론트 처리 기준

```
스터디 상세에서 신청 버튼 선택
→ 한 줄 자기소개와 참여 목적 입력
→ POST /api/v1/studies/{studyId}/applications 호출

신청 성공
→ myParticipationStatus를 PENDING으로 변경
→ 신청 버튼 비활성화
→ "스터디장의 승인을 기다리고 있습니다." 표시

STUDY_APPLICATION_ALREADY_EXISTS 발생
→ 응답 상태에 맞춰 승인 대기 또는 참여 중 상태로 동기화

STUDY_CAPACITY_FULL 또는 STUDY_APPLICATION_NOT_ALLOWED 발생
→ 신청 버튼 숨김 또는 비활성화
→ 모집 마감 안내 표시
```

---

## 스터디 취소

Method: DELETE
Progress: 완료
URI: /api/v1/studies/{studyId}
담당자: 최태선
연동여부: No

스터디장이 생성한 스터디를 취소한다.

스터디가 취소되면 모집, 신청, 채팅, 공지, 일정 및 임장 기능을 더 이상 사용할 수 없다.

---

### Request

- Request HTTP Method: `DELETE`
- 인증 필요: 필요
- Request Body: 없음

#### Request Header

```
Authorization: Bearer {accessToken}
```

#### Path Variable

```
DELETE /api/v1/studies/10
```

| 필드 | 타입 | 필수 | 설명 |
| --- | --- | --- | --- |
| `studyId` | Long | Y | 취소할 스터디 ID |

---

### 처리 기준

#### 1. 스터디 및 권한 확인

- 로그인한 회원 ID는 Access Token에서 확인한다.
- `studyId`에 해당하는 스터디가 존재하는지 확인한다.
- 스터디장만 스터디를 취소할 수 있다.
- 일반 참여자와 신청자는 스터디를 취소할 수 없다.
- 이미 취소된 스터디는 다시 취소할 수 없다.

---

#### 2. 취소 가능 상태

- `RECRUITING`, `CLOSED` 상태의 스터디만 취소할 수 있다.
- 임장이 시작된 `IN_PROGRESS` 상태에서는 취소할 수 없다.
- 임장이 완료된 `COMPLETED` 상태에서는 취소할 수 없다.

---

#### 3. 스터디 취소 처리

- 스터디 상태를 `CANCELED`로 변경한다.
- 실제 데이터를 즉시 삭제하지 않고 취소 상태로 보관한다.
- 모집과 새로운 참여 신청을 중단한다.
- 대기 중인 신청은 더 이상 승인하거나 거절할 수 없다.
- 승인된 멤버는 취소된 스터디 정보를 조회할 수 있다.
- 공지, 일정, 채팅과 임장 기능은 읽기 전용 또는 사용 불가 상태로 전환한다.
- 취소 시각을 서버 시간으로 저장한다.

---

#### 4. 알림 처리

- 스터디가 취소되면 승인된 참여자와 신청 대기자에게 취소 알림을 생성한다.
- 알림에는 스터디명과 취소 사실을 포함한다.
- 푸시 알림 전송 여부는 회원의 서비스 알림 수신 설정을 따른다.

---

### Response

#### 200 OK

```json
{
  "success": true,
  "code": "STUDY_CANCEL_SUCCESS",
  "message": "스터디가 취소되었습니다.",
  "data": {
    "studyId": 10,
    "status": "CANCELED",
    "canceledAt": "2026-07-22T19:00:00+09:00"
  },
  "timestamp": "2026-07-22T19:00:00+09:00"
}
```

---

### Response Field

| 필드 | 타입 | 설명 |
| --- | --- | --- |
| `studyId` | Long | 취소된 스터디 ID |
| `status` | String | 변경된 스터디 상태. `CANCELED` |
| `canceledAt` | String | 스터디 취소 시각 |

---

### Exception

#### 401 Unauthorized

```json
{
  "success": false,
  "code": "AUTH_ACCESS_TOKEN_INVALID",
  "message": "로그인이 필요합니다.",
  "data": null,
  "timestamp": "2026-07-22T19:00:00+09:00"
}
```

---

#### 403 Forbidden — 스터디장 아님

```json
{
  "success": false,
  "code": "STUDY_CANCEL_FORBIDDEN",
  "message": "스터디를 취소할 권한이 없습니다.",
  "data": null,
  "timestamp": "2026-07-22T19:00:00+09:00"
}
```

---

#### 404 Not Found

```json
{
  "success": false,
  "code": "STUDY_NOT_FOUND",
  "message": "스터디를 찾을 수 없습니다.",
  "data": null,
  "timestamp": "2026-07-22T19:00:00+09:00"
}
```

---

#### 409 Conflict — 이미 취소된 스터디

```json
{
  "success": false,
  "code": "STUDY_ALREADY_CANCELED",
  "message": "이미 취소된 스터디입니다.",
  "data": null,
  "timestamp": "2026-07-22T19:00:00+09:00"
}
```

---

#### 409 Conflict — 취소할 수 없는 상태

```json
{
  "success": false,
  "code": "STUDY_CANCEL_NOT_ALLOWED",
  "message": "현재 상태에서는 스터디를 취소할 수 없습니다.",
  "data": {
    "studyId": 10,
    "status": "IN_PROGRESS"
  },
  "timestamp": "2026-07-22T19:00:00+09:00"
}
```

---

#### 500 Internal Server Error

```json
{
  "success": false,
  "code": "COMMON_INTERNAL_SERVER_ERROR",
  "message": "일시적인 오류가 발생했습니다.",
  "data": null,
  "timestamp": "2026-07-22T19:00:00+09:00"
}
```

---

### 프론트 처리

```
스터디 관리 화면에서 취소 선택
→ 취소 확인 팝업 표시
→ DELETE /api/v1/studies/{studyId} 호출

스터디 취소 성공
→ status를 CANCELED로 변경
→ 모집, 신청, 공지 작성, 일정 수정, 채팅 및 임장 버튼 비활성화
→ "스터디가 취소되었습니다." 안내

STUDY_CANCEL_FORBIDDEN 발생
→ "스터디장만 취소할 수 있습니다." 안내

STUDY_CANCEL_NOT_ALLOWED 발생
→ 현재 상태에 맞는 안내 표시
→ 임장 진행 중이거나 완료된 스터디의 취소 버튼 숨김

STUDY_ALREADY_CANCELED 발생
→ 화면 상태를 CANCELED로 동기화
```

---

## 스터디 모집/홈 상세 조회

Method: GET
Progress: 완료
URI: /api/v1/studies/{studyId}
담당자: 최태선
연동여부: No

회원의 참여 상태에 따라 모집 상세 또는 내 스터디 홈 정보를 반환한다.

스터디 ID를 기준으로 스터디의 모집 정보와 스터디 홈 정보를 조회한다.

로그인한 회원의 참여 상태와 권한에 따라 신청 버튼, 멤버 관리, 공지, 일정 등 사용할 수 있는 기능을 함께 반환한다.

### Request

- HTTP Method: `GET`
- URI: `/api/v1/studies/{studyId}`
- 인증 필요: 필요

#### Request Header

```
Authorization: Bearer {accessToken}
```

#### Path Variable

```
GET /api/v1/studies/10
```

| 필드 | 타입 | 필수 | 설명 |
| --- | --- | --- | --- |
| `studyId` | Long | Y | 조회할 스터디 ID |

---

### 처리 기준

- 비멤버는 모집 정보만 조회한다.
- `PENDING` 신청자는 모집 정보와 신청 상태를 조회한다.
- 승인 멤버와 스터디장은 일정·멤버·목표·채팅 배지·임장 시작 정보를 조회한다.
- 승인 멤버에게는 임장 세션이 존재할 때 `fieldSessionId`를, 리포트가 존재할 때 `{reportId,status}`를 함께 반환한다. 비멤버에게는 두 참조를 노출하지 않는다.
- 완료 스터디는 기존 데이터를 조회할 수 있으나 `readOnly=true`로 반환한다.
- `applicationMessageRequired`는 반환하지 않는다.
- 다음 일정은 `status=SCHEDULED`인 일정만 반환한다.
- `canStartFieldVisit`는 승인 멤버이고 일정·스터디·세션 상태가 시작 가능하며, 현재 시각이 `schedule.startAt`과 같거나 이후일 때만 `true`다.

### 추가 상세 처리 기준

- 로그인한 회원 ID는 Access Token에서 확인한다.
- `studyId`에 해당하는 스터디가 존재하는지 확인한다.
- 취소된 스터디도 기존 참여자는 상세 내용을 조회할 수 있다.
- 참여하지 않은 회원에게는 모집에 필요한 공개 정보만 반환한다.

---

- 스터디 제목, 소개, 대상 아파트, 임장 일정과 모집 인원을 반환한다.
- 현재 승인된 참여 인원에는 스터디장을 포함한다.
- 모집 중이고 정원이 남은 경우 `canApply = true`로 반환한다.
- 모집이 마감됐거나 이미 신청한 경우 `canApply = false`로 반환한다.
- 본인이 스터디장인 경우 참여 신청할 수 없다.

---

- 로그인한 회원의 신청 및 참여 상태를 `myParticipationStatus`로 반환한다.
- 스터디장이면 `isLeader = true`로 반환한다.
- 승인된 참여자이면 `isMember = true`로 반환한다.
- 스터디장은 신청자 관리, 멤버 강퇴, 공지 작성과 모집 마감 권한을 가진다.
- 승인된 참여자는 스터디 공지, 일정, 채팅과 임장 기능을 이용할 수 있다.

---

- 승인된 참여자에게 최근 공지와 임장 일정 요약을 반환한다.
- 참여하지 않은 회원에게는 비공개 공지와 채팅 정보를 반환하지 않는다.
- 읽지 않은 채팅 수와 새로운 공지 여부를 함께 반환할 수 있다.
- 등록된 공지나 일정이 없으면 해당 값은 `null`로 반환한다.

---

### Response

#### 200 OK — 승인 멤버

```json
{
  "success": true,
  "code": "STUDY_DETAIL_SUCCESS",
  "message": "스터디 상세 조회에 성공했습니다.",
  "data": {
    "studyId": 10,
    "title": "옥수동 주말 임장",
    "intro": "교통과 단지 환경을 함께 확인합니다.",
    "goal": "역 접근성·단지 경사·주변 소음을 확인합니다.",
    "purpose": "RESIDENCE",
    "status": "CLOSED",
    "apartment": {
      "apartmentId": 15,
      "name": "래미안 옥수 리버젠",
      "address": "서울특별시 성동구 매봉길 15"
    },
    "leader": {
      "memberId": 7,
      "nickname": "집보는다람쥐",
      "selectedCharacterId": "JIPKONG"
    },
    "currentMemberCount": 5,
    "capacity": 6,
    "nextSchedule": {
      "scheduleId": 7,
      "startAt": "2026-07-27T15:00:00+09:00",
      "endAt": "2026-07-27T18:00:00+09:00",
      "meetingPlace": "옥수역 3번 출구"
    },
    "memberSummary": [
      {
        "memberId": 7,
        "nickname": "집보는다람쥐",
        "selectedCharacterId": "JIPKONG",
        "role": "LEADER"
      },
      {
        "memberId": 12,
        "nickname": "옥수탐방러",
        "selectedCharacterId": "PALBANG",
        "role": "MEMBER"
      }
    ],
    "myParticipationStatus": "APPROVED",
    "isLeader": false,
    "isMember": true,
    "canApply": false,
    "unreadChatCount": 3,
    "fieldVisitStatus": "NOT_STARTED",
    "canStartFieldVisit": true,
    "readOnly": false,
    "permissions": {
      "canManageApplications": false,
      "canManageMembers": false,
      "canManageNotices": false,
      "canManageSchedule": false,
      "canUseChat": true,
      "canUseFieldVisit": true
    }
  },
  "timestamp": "2026-07-24T18:05:00+09:00"
}
```

#### 승인 멤버 — 종료 임장 참조

```json
{
  "fieldVisitStatus": "ENDED",
  "fieldSessionId": 100,
  "report": {
    "reportId": 48,
    "status": "PENDING"
  }
}
```

`report`는 생성 상태에 따라 `PENDING`, `IN_PROGRESS`, `DONE`, `FAILED`를 반환한다. 종료 직후에도 `PENDING` 리포트가 먼저 생성되므로 정상 종료 경로에서는 양의 `reportId`를 즉시 사용할 수 있다.

#### 비멤버

```json
{
  "myParticipationStatus": "NONE",
  "isLeader": false,
  "isMember": false,
  "canApply": true,
  "unreadChatCount": null,
  "fieldVisitStatus": null,
  "canStartFieldVisit": false,
  "readOnly": false,
  "permissions": {
    "canManageApplications": false,
    "canManageMembers": false,
    "canManageNotices": false,
    "canManageSchedule": false,
    "canUseChat": false,
    "canUseFieldVisit": false
  }
}
```

### Exception

- `401 AUTH_ACCESS_TOKEN_INVALID`
- `404 STUDY_NOT_FOUND`
- `500 COMMON_INTERNAL_SERVER_ERROR`

---

### 추가 Exception 예시

#### 401 Unauthorized

```json
{
  "success": false,
  "code": "AUTH_ACCESS_TOKEN_INVALID",
  "message": "로그인이 필요합니다.",
  "data": null,
  "timestamp": "2026-07-22T18:40:00+09:00"
}
```

---

#### 404 Not Found

```json
{
  "success": false,
  "code": "STUDY_NOT_FOUND",
  "message": "스터디를 찾을 수 없습니다.",
  "data": null,
  "timestamp": "2026-07-22T18:40:00+09:00"
}
```

---

#### 500 Internal Server Error

```json
{
  "success": false,
  "code": "COMMON_INTERNAL_SERVER_ERROR",
  "message": "일시적인 오류가 발생했습니다.",
  "data": null,
  "timestamp": "2026-07-22T18:40:00+09:00"
}
```

---

### 추가 프론트 처리 기준

```
스터디 목록 또는 아파트 상세에서 스터디 선택
→ GET /api/v1/studies/{studyId} 호출
→ 스터디 모집 또는 홈 화면 표시

canApply = true
→ 참여 신청 버튼 표시

myParticipationStatus = PENDING
→ "승인 대기 중" 표시
→ 신청 버튼 비활성화

isMember = true
→ 공지, 일정, 채팅과 임장 메뉴 표시

isMember = true AND fieldVisitStatus = ENDED
→ "내 임장 기록"과 "리포트 보기" CTA 표시
→ 원문 화면에서는 본인 체크리스트와 `mineOnly=true` 기록의 모든 cursor 페이지를 읽기 전용으로 표시
→ report.status = PENDING 또는 IN_PROGRESS이면 리포트 생성 화면에서 reportId 상태 폴링
→ report.status = DONE이면 리포트 상세, FAILED이면 기존 실패 안내 표시
→ report = null이면 준비 안내와 상세 재조회 동작 제공

isLeader = true
→ 신청자 관리, 멤버 관리, 모집 마감과 공지 관리 메뉴 표시

status = CANCELED
→ 스터디 취소 안내 표시
→ 신청 및 스터디 활동 기능 비활성화
```

---

## 스터디 목표·소개 수정

Method: PATCH
Progress: 완료
URI: /api/v1/studies/{studyId}
연동여부: Yes

활성 상태의 스터디장만 제목·목표·소개를 수정한다. 전달하지 않은 필드는 기존 값을 유지한다.
완료되거나 취소된 스터디는 읽기 전용이므로 수정할 수 없다.

### Request

```json
{
  "title": "옥수동 실거주 임장 스터디",
  "goal": "역 접근성과 야간 소음을 함께 확인합니다.",
  "intro": "옥수동 실거주 관점으로 함께 살펴보는 스터디입니다."
}
```

| 필드 | 타입 | 필수 | 설명 |
| --- | --- | --- | --- |
| `title` | String | N | 앞뒤 공백 제거 후 1~200자. 공백만 전달할 수 없음 |
| `goal` | String | N | 앞뒤 공백 제거 후 1~300자. 공백만 전달할 수 없음 |
| `intro` | String | N | 앞뒤 공백 제거 후 0~1000자. 빈 문자열은 소개 삭제 |

- 세 필드 중 하나 이상을 전달해야 한다.
- 스터디장이 아닌 회원의 요청은 `403 STUDY_UPDATE_FORBIDDEN`이다.
- 완료·취소 상태는 `409 STUDY_UPDATE_NOT_ALLOWED`이다.
- 빈 요청은 `400 STUDY_UPDATE_EMPTY`, 유효하지 않은 길이·공백 제목·목표는 `400 COMMON_INVALID_REQUEST`다.

### Response

```json
{
  "success": true,
  "code": "STUDY_UPDATE_SUCCESS",
  "message": "스터디 목표와 소개를 수정했습니다.",
  "data": {
    "studyId": 10,
    "title": "옥수동 실거주 임장 스터디",
    "intro": "옥수동 실거주 관점으로 함께 살펴보는 스터디입니다.",
    "goal": "역 접근성과 야간 소음을 함께 확인합니다."
  },
  "timestamp": "2026-08-06T15:00:00+09:00"
}
```

프론트는 `isLeader=true`이고 `readOnly=false`일 때만 목표 탭에 수정 동작을 표시한다.
성공 후 스터디 상세와 내 스터디 목록을 다시 조회한다.

---

## 스터디 공지 등록

Method: POST
Progress: 완료
URI: /api/v1/studies/{studyId}/notices
담당자: 최태선
연동여부: Yes

스터디장이 스터디 참여자에게 전달할 공지를 등록한다.

공지 등록 후 승인된 스터디 멤버는 공지 목록에서 해당 내용을 확인할 수 있다.

### Request

- HTTP Method: `POST`
- URI: `/api/v1/studies/{studyId}/notices`
- 인증 필요: 필요
- Content-Type: `application/json`

#### Request Body

```json
{
  "content": "편한 운동화와 필기 도구를 준비해 주세요. 오후 1시 50분까지 모여 주세요."
}
```

| 필드 | 타입 | 필수 | 설명 |
| --- | --- | --- | --- |
| `content` | String | Y | 공지 내용 |

#### Request Header

```
Authorization: Bearer {accessToken}
```

#### Path Variable

```
POST /api/v1/studies/10/notices
```

| 필드 | 타입 | 필수 | 설명 |
| --- | --- | --- | --- |
| `studyId` | Long | Y | 공지를 등록할 스터디 ID |

### 처리 기준

- 스터디장만 등록할 수 있다.
- 공백만 있는 내용을 등록할 수 없다.
- `COMPLETED`, `CANCELED` 스터디에는 새 공지를 등록할 수 없다.
- 기존 `title`, `isPinned` 입력과 고정 공지 정책은 삭제한다.

### 추가 상세 처리 기준

- 로그인한 회원 ID는 Access Token에서 확인한다.
- `studyId`에 해당하는 스터디가 존재하는지 확인한다.
- 해당 스터디의 스터디장만 공지를 등록할 수 있다.
- 일반 참여자는 공지를 등록할 수 없다.

---

- 제목과 내용의 앞뒤 공백을 제거한 후 저장한다.
- 제목이나 내용이 공백으로만 구성된 경우 등록할 수 없다.
- 제목은 최대 100자, 내용은 최대 2000자까지 작성할 수 있다.
- 공지 작성자는 로그인한 스터디장으로 설정한다.
- 생성 시각과 수정 시각은 서버 시간을 기준으로 저장한다.
- 취소된 스터디에는 새로운 공지를 등록할 수 없다.
- 완료된 스터디의 공지 등록 허용 여부는 운영 정책에 따라 제한할 수 있다.

---

- 공지 등록 시 승인된 스터디 멤버에게 새 공지 알림을 생성할 수 있다.
- 알림에는 스터디명과 공지 제목을 포함한다.
- 푸시 알림 발송 여부는 회원의 서비스 알림 수신 설정을 따른다.

---

### Response

#### 201 Created

```json
{
  "success": true,
  "code": "STUDY_NOTICE_CREATE_SUCCESS",
  "message": "스터디 공지가 등록되었습니다.",
  "data": {
    "noticeId": 18,
    "studyId": 10,
    "content": "편한 운동화와 필기 도구를 준비해 주세요. 오후 1시 50분까지 모여 주세요.",
    "createdAt": "2026-07-24T18:30:00+09:00",
    "updatedAt": "2026-07-24T18:30:00+09:00"
  },
  "timestamp": "2026-07-24T18:30:00+09:00"
}
```

### Exception

- `400 COMMON_INVALID_REQUEST`
- `401 AUTH_ACCESS_TOKEN_INVALID`
- `403 STUDY_NOTICE_CREATE_FORBIDDEN`
- `404 STUDY_NOT_FOUND`
- `409 STUDY_NOTICE_CREATE_NOT_ALLOWED`

---

### 추가 Exception 예시

#### 401 Unauthorized

```json
{
  "success": false,
  "code": "AUTH_ACCESS_TOKEN_INVALID",
  "message": "로그인이 필요합니다.",
  "data": null,
  "timestamp": "2026-07-22T20:20:00+09:00"
}
```

---

#### 403 Forbidden — 스터디장 아님

```json
{
  "success": false,
  "code": "STUDY_NOTICE_CREATE_FORBIDDEN",
  "message": "스터디 공지를 등록할 권한이 없습니다.",
  "data": null,
  "timestamp": "2026-07-22T20:20:00+09:00"
}
```

---

#### 404 Not Found

```json
{
  "success": false,
  "code": "STUDY_NOT_FOUND",
  "message": "스터디를 찾을 수 없습니다.",
  "data": null,
  "timestamp": "2026-07-22T20:20:00+09:00"
}
```

---

#### 409 Conflict — 공지 등록 불가 상태

```json
{
  "success": false,
  "code": "STUDY_NOTICE_CREATE_NOT_ALLOWED",
  "message": "현재 상태에서는 공지를 등록할 수 없습니다.",
  "data": {
    "studyStatus": "CANCELED"
  },
  "timestamp": "2026-07-22T20:20:00+09:00"
}
```

---

#### 500 Internal Server Error

```json
{
  "success": false,
  "code": "COMMON_INTERNAL_SERVER_ERROR",
  "message": "일시적인 오류가 발생했습니다.",
  "data": null,
  "timestamp": "2026-07-22T20:20:00+09:00"
}
```

---

### 추가 프론트 처리 기준

```
스터디 공지 화면에서 공지 등록 선택
→ 제목, 내용, 상단 고정 여부 입력
→ POST /api/v1/studies/{studyId}/notices 호출

입력값 오류
→ data.field에 해당하는 입력 영역에 오류 메시지 표시
→ 사용자가 입력한 내용 유지

STUDY_NOTICE_CREATE_FORBIDDEN 발생
→ "스터디장만 공지를 등록할 수 있습니다." 안내
→ 공지 작성 화면 종료

STUDY_NOTICE_CREATE_NOT_ALLOWED 발생
→ 공지 등록 버튼 비활성화
→ 현재 스터디 상태에 맞는 안내 표시
```

---

## 스터디 멤버 강퇴

Method: DELETE
Progress: 완료
URI: /api/v1/studies/{studyId}/members/{memberId}
담당자: 최태선
연동여부: Yes

스터디장이 승인된 일반 참여자를 스터디에서 강퇴한다.

강퇴된 회원은 스터디 공지, 일정, 채팅과 임장 기능에 더 이상 접근할 수 없다.

---

### Request

- Request HTTP Method: `DELETE`
- 인증 필요: 필요
- Request Body: 없음

#### Request Header

```
Authorization: Bearer {accessToken}
```

#### Path Variable

```
DELETE /api/v1/studies/10/members/12
```

| 필드 | 타입 | 필수 | 설명 |
| --- | --- | --- | --- |
| `studyId` | Long | Y | 대상 스터디 ID |
| `memberId` | Long | Y | 강퇴할 회원 ID |

---

### 처리 기준

#### 1. 스터디 및 권한 확인

- 로그인한 회원 ID는 Access Token에서 확인한다.
- `studyId`에 해당하는 스터디가 존재하는지 확인한다.
- 해당 스터디의 스터디장만 멤버를 강퇴할 수 있다.
- 일반 참여자는 다른 멤버를 강퇴할 수 없다.

---

#### 2. 강퇴 대상 확인

- `memberId`에 해당하는 회원이 현재 승인된 스터디 멤버인지 확인한다.
- 신청 대기자나 거절된 신청자는 강퇴 대상이 아니다.
- 스터디장은 자신을 강퇴할 수 없다.
- 이미 탈퇴하거나 강퇴된 회원은 다시 강퇴할 수 없다.

---

#### 3. 강퇴 가능 상태

- `RECRUITING`, `CLOSED` 상태에서만 강퇴할 수 있다.
- 임장이 시작된 `IN_PROGRESS` 상태에서는 강퇴할 수 없다.
- 완료되거나 취소된 스터디에서는 강퇴할 수 없다.

---

#### 4. 강퇴 처리

- 해당 회원을 스터디 멤버에서 제외한다.
- 현재 승인 멤버 수을 1 감소시킨다.
- 강퇴된 회원의 기존 채팅, 기록과 활동 내역은 삭제하지 않는다.
- 강퇴된 회원은 이후 스터디 홈, 공지, 채팅과 임장 기능에 접근할 수 없다.
- 정원 초과로 `CLOSED` 상태였던 스터디는 인원이 줄어들면 다시 `RECRUITING`으로 변경할 수 있다.

---

#### 5. 알림 처리

- 강퇴된 회원에게 스터디에서 제외되었다는 알림을 생성할 수 있다.
- 푸시 알림 발송 여부는 회원의 서비스 알림 수신 설정을 따른다.

---

### Response

#### 200 OK

```
{
  "success": true,
  "code": "STUDY_MEMBER_KICK_SUCCESS",
  "message": "스터디 멤버를 강퇴했습니다.",
  "data": {
    "studyId": 10,
    "memberId": 12,
    "currentMemberCount": 4,
    "capacity": 6,
    "studyStatus": "RECRUITING",
    "kickedAt": "2026-07-22T19:50:00+09:00"
  },
  "timestamp": "2026-07-22T19:50:00+09:00"
}
```

---

### Response Field

| 필드 | 타입 | 설명 |
| --- | --- | --- |
| `studyId` | Long | 스터디 ID |
| `memberId` | Long | 강퇴된 회원 ID |
| `currentMemberCount` | Integer | 강퇴 후 현재 승인 멤버 수 |
| `capacity` | Integer | 최대 정원 |
| `studyStatus` | String | 강퇴 후 스터디 상태 |
| `kickedAt` | String | 강퇴 처리 시각 |

---

### Exception

#### 401 Unauthorized

```
{
  "success": false,
  "code": "AUTH_ACCESS_TOKEN_INVALID",
  "message": "로그인이 필요합니다.",
  "data": null,
  "timestamp": "2026-07-22T19:50:00+09:00"
}
```

---

#### 403 Forbidden — 스터디장 아님

```
{
  "success": false,
  "code": "STUDY_MEMBER_KICK_FORBIDDEN",
  "message": "스터디 멤버를 강퇴할 권한이 없습니다.",
  "data": null,
  "timestamp": "2026-07-22T19:50:00+09:00"
}
```

---

#### 400 Bad Request — 스터디장 강퇴 시도

```
{
  "success": false,
  "code": "STUDY_LEADER_CANNOT_BE_KICKED",
  "message": "스터디장은 강퇴할 수 없습니다.",
  "data": null,
  "timestamp": "2026-07-22T19:50:00+09:00"
}
```

---

#### 404 Not Found — 스터디 없음

```
{
  "success": false,
  "code": "STUDY_NOT_FOUND",
  "message": "스터디를 찾을 수 없습니다.",
  "data": null,
  "timestamp": "2026-07-22T19:50:00+09:00"
}
```

---

#### 404 Not Found — 멤버 없음

```
{
  "success": false,
  "code": "STUDY_MEMBER_NOT_FOUND",
  "message": "스터디 멤버를 찾을 수 없습니다.",
  "data": null,
  "timestamp": "2026-07-22T19:50:00+09:00"
}
```

---

#### 409 Conflict — 강퇴 불가 상태

```
{
  "success": false,
  "code": "STUDY_MEMBER_KICK_NOT_ALLOWED",
  "message": "현재 상태에서는 멤버를 강퇴할 수 없습니다.",
  "data": {
    "studyStatus": "IN_PROGRESS"
  },
  "timestamp": "2026-07-22T19:50:00+09:00"
}
```

---

#### 500 Internal Server Error

```
{
  "success": false,
  "code": "COMMON_INTERNAL_SERVER_ERROR",
  "message": "일시적인 오류가 발생했습니다.",
  "data": null,
  "timestamp": "2026-07-22T19:50:00+09:00"
}
```

---

### 프론트 처리

```
멤버 목록에서 강퇴 선택
→ 강퇴 확인 팝업 표시
→ DELETE /api/v1/studies/{studyId}/members/{memberId} 호출

강퇴 성공
→ 해당 멤버를 목록에서 제거
→ 현재 승인 멤버 수 갱신
→ 응답의 studyStatus로 모집 상태 갱신

STUDY_LEADER_CANNOT_BE_KICKED 발생
→ 스터디장 관리 메뉴 숨김

STUDY_MEMBER_KICK_NOT_ALLOWED 발생
→ 강퇴 버튼 비활성화
→ "임장 진행 중에는 멤버를 강퇴할 수 없습니다." 안내

STUDY_MEMBER_NOT_FOUND 발생
→ 멤버 목록 다시 조회
```

---

## 스터디 나가기

Method: DELETE
Progress: 완료
URI: /api/v1/studies/{studyId}/members/me
연동여부: Yes

모집 중이거나 모집 마감된 스터디의 활성 일반 멤버가 스터디에서 나간다.
가입 행은 삭제하지 않고 `status=REMOVED`, `leftAt=현재 시각`으로 변경해 과거 기록을 보존한다.

### Request

요청 본문 없음.

### Response

```json
{
  "success": true,
  "code": "STUDY_MEMBER_LEAVE_SUCCESS",
  "message": "스터디에서 나갔습니다.",
  "data": {
    "studyId": 10,
    "memberId": 8,
    "currentMemberCount": 3,
    "capacity": 6,
    "studyStatus": "RECRUITING",
    "leftAt": "2026-08-06T15:05:00+09:00"
  },
  "timestamp": "2026-08-06T15:05:00+09:00"
}
```

- 스터디장은 `409 STUDY_LEADER_CANNOT_LEAVE`로 거부한다.
- 비멤버 또는 이미 제거된 멤버는 `404 STUDY_MEMBER_NOT_FOUND`다.
- 임장이 시작됐거나 스터디가 진행·완료·취소 상태이면 `409 STUDY_MEMBER_LEAVE_NOT_ALLOWED`다.
- 성공 후 채팅·임장 등 멤버 전용 권한이 즉시 해제된다.
- 프론트는 홈으로 이동하고 상세·홈·마이페이지 캐시를 갱신한다.

---

## 스터디 멤버 목록 조회

Method: GET
Progress: 완료
URI: /api/v1/studies/{studyId}/members
담당자: 최태선
연동여부: Yes

스터디에 승인된 참여자와 스터디장 정보를 조회한다.

스터디 참여자는 멤버 목록을 확인할 수 있으며, 스터디장은 멤버 관리 권한 정보를 함께 확인할 수 있다.

---

### Request

- Request HTTP Method: `GET`
- 인증 필요: 필요
- Request Body: 없음

#### Request Header

```
Authorization: Bearer {accessToken}
```

#### Path Variable

```
GET /api/v1/studies/10/members
```

| 필드 | 타입 | 필수 | 설명 |
| --- | --- | --- | --- |
| `studyId` | Long | Y | 멤버 목록을 조회할 스터디 ID |

---

### 멤버 역할

| 값 | 설명 |
| --- | --- |
| `LEADER` | 스터디장 |
| `MEMBER` | 승인된 일반 참여자 |

---

### 처리 기준

#### 1. 스터디 및 권한 확인

- 로그인한 회원 ID는 Access Token에서 확인한다.
- `studyId`에 해당하는 스터디가 존재하는지 확인한다.
- 스터디장과 승인된 참여자만 멤버 목록을 조회할 수 있다.
- 신청 대기자, 거절된 신청자와 비참여 회원은 조회할 수 없다.
- 취소된 스터디도 기존 승인 멤버는 목록을 조회할 수 있다.

---

#### 2. 멤버 조회

- 스터디장과 승인된 참여자만 반환한다.
- 승인 대기 또는 거절 상태의 신청자는 포함하지 않는다.
- 스터디장을 목록의 첫 번째로 반환한다.
- 일반 멤버는 참여 승인 시각이 빠른 순으로 반환한다.
- 탈퇴하거나 강퇴된 멤버는 현재 멤버 목록에서 제외한다.
- 멤버가 스터디장 한 명뿐이어도 오류가 아닌 정상 목록을 반환한다.

---

#### 3. 권한 정보

- 현재 로그인한 회원이 스터디장이면 `isLeader = true`로 반환한다.
- 각 멤버에 대해 현재 사용자가 강퇴할 수 있는지 `canKick`으로 반환한다.
- 각 멤버에 대해 로그인 사용자가 이 스터디에서 이미 평가했는지 `reviewedByMe`로 반환한다.
- 스터디장은 자신을 강퇴할 수 없다.
- 일반 참여자는 다른 멤버를 강퇴할 수 없다.
- 스터디 상태가 `IN_PROGRESS`, `COMPLETED`, `CANCELED`이면 멤버 강퇴를 제한할 수 있다.

---

### Response

#### 200 OK

```
{
  "success": true,
  "code": "STUDY_MEMBER_LIST_SUCCESS",
  "message": "스터디 멤버 목록 조회에 성공했습니다.",
  "data": {
    "studyId": 10,
    "isLeader": true,
    "currentMemberCount": 4,
    "capacity": 6,
    "members": [
      {
        "memberId": 7,
        "nickname": "집보는다람쥐",
        "profileImageUrl": "<https://cdn.example.com/profiles/7.jpg>",
        "selectedCharacterId": "JIPKONG",
        "role": "LEADER",
        "canKick": false,
        "reviewedByMe": false,
        "joinedAt": "2026-07-22T15:00:00+09:00"
      },
      {
        "memberId": 12,
        "nickname": "옥수탐방러",
        "profileImageUrl": "<https://cdn.example.com/profiles/12.jpg>",
        "selectedCharacterId": "PALBANG",
        "role": "MEMBER",
        "canKick": true,
        "reviewedByMe": true,
        "joinedAt": "2026-07-22T19:30:00+09:00"
      },
      {
        "memberId": 9,
        "nickname": "성동구새싹",
        "profileImageUrl": null,
        "selectedCharacterId": "JIPKONG",
        "role": "MEMBER",
        "canKick": true,
        "reviewedByMe": false,
        "joinedAt": "2026-07-21T16:00:00+09:00"
      }
    ]
  },
  "timestamp": "2026-07-22T19:40:00+09:00"
}
```

---

### Response Field

| 필드 | 타입 | 설명 |
| --- | --- | --- |
| `studyId` | Long | 스터디 ID |
| `isLeader` | Boolean | 현재 사용자가 스터디장인지 여부 |
| `currentMemberCount` | Integer | 현재 승인 멤버 수 |
| `capacity` | Integer | 최대 정원 |
| `members` | Array | 스터디 멤버 목록 |

#### `members`

| 필드 | 타입 | 설명 |
| --- | --- | --- |
| `memberId` | Long | 회원 ID |
| `nickname` | String | 회원 닉네임 |
| `profileImageUrl` | String | 프로필 이미지 URL, 없으면 `null` |
| `selectedCharacterId` | String | 회원이 선택한 캐릭터 ID |
| `role` | String | 스터디 내 역할. `LEADER`, `MEMBER` |
| `canKick` | Boolean | 현재 사용자가 해당 멤버를 강퇴할 수 있는지 여부 |
| `reviewedByMe` | Boolean | 로그인 사용자가 이 스터디에서 해당 멤버에게 이미 리뷰를 등록했는지 여부 |
| `joinedAt` | String | 스터디 참여 승인 시각 |

---

### Exception

#### 401 Unauthorized

```
{
  "success": false,
  "code": "AUTH_ACCESS_TOKEN_INVALID",
  "message": "로그인이 필요합니다.",
  "data": null,
  "timestamp": "2026-07-22T19:40:00+09:00"
}
```

---

#### 403 Forbidden

스터디 멤버가 아닌 회원이 조회한 경우

```
{
  "success": false,
  "code": "STUDY_MEMBER_LIST_FORBIDDEN",
  "message": "스터디 멤버 목록을 조회할 권한이 없습니다.",
  "data": null,
  "timestamp": "2026-07-22T19:40:00+09:00"
}
```

---

#### 404 Not Found

```
{
  "success": false,
  "code": "STUDY_NOT_FOUND",
  "message": "스터디를 찾을 수 없습니다.",
  "data": null,
  "timestamp": "2026-07-22T19:40:00+09:00"
}
```

---

#### 500 Internal Server Error

```
{
  "success": false,
  "code": "COMMON_INTERNAL_SERVER_ERROR",
  "message": "일시적인 오류가 발생했습니다.",
  "data": null,
  "timestamp": "2026-07-22T19:40:00+09:00"
}
```

---

### 프론트 처리

```
스터디 홈에서 멤버 목록 선택
→ GET /api/v1/studies/{studyId}/members 호출
→ 스터디장과 참여자 목록 표시

role = LEADER
→ 스터디장 배지 표시
→ 목록 상단에 고정

canKick = true
→ 해당 멤버의 관리 메뉴에 강퇴 버튼 표시

isLeader = false
→ 모든 멤버의 관리 메뉴 숨김

STUDY_MEMBER_LIST_FORBIDDEN 발생
→ "승인된 스터디 멤버만 확인할 수 있습니다." 안내
→ 스터디 모집 상세 화면으로 이동
```

---

## 스터디 공지 목록 조회

Method: GET
Progress: 완료
URI: /api/v1/studies/{studyId}/notices
담당자: 최태선
연동여부: Yes

스터디 멤버가 해당 스터디의 공지 내용을 최신순으로 조회한다.

스터디에 등록된 공지사항 목록을 조회한다.

승인된 스터디 멤버만 조회할 수 있으며, 공지는 최신 등록 순으로 반환한다.

### Request

- HTTP Method: `GET`
- URI: `/api/v1/studies/{studyId}/notices`
- 인증 필요: 필요

#### Query Parameter

```
GET /api/v1/studies/10/notices?cursor=18&size=20
```

#### Path Variable

```
GET /api/v1/studies/10/notices
```

| 필드 | 타입 | 필수 | 설명 |
| --- | --- | --- | --- |
| `studyId` | Long | Y | 공지 목록을 조회할 스터디 ID |

### 처리 기준

- 스터디장과 `ACTIVE` 멤버만 조회할 수 있다.
- `noticeId DESC`로 정렬한다.
- 최종 `study_notice`에는 `content`만 저장하므로 제목·고정 여부·작성자 필드를 사용하지 않는다.
- 완료 스터디도 기존 멤버는 읽기 전용으로 조회할 수 있다.

### 추가 상세 처리 기준

- 로그인한 회원 ID는 Access Token에서 확인한다.
- `studyId`에 해당하는 스터디가 존재하는지 확인한다.
- 스터디장과 승인된 참여자만 공지 목록을 조회할 수 있다.
- 신청 대기자, 거절된 신청자와 비참여 회원은 조회할 수 없다.
- 취소되거나 완료된 스터디도 기존 승인 멤버는 공지를 조회할 수 있다.

---

- 해당 스터디에 등록된 공지만 반환한다.
- 공지는 최근 등록 순으로 반환한다.
- 정렬 기준은 `noticeId DESC`로 한다.
- 공지 제목, 내용 미리보기, 작성자와 작성 시각을 반환한다.
- 공지 내용 미리보기는 최대 150자까지 반환한다.
- 공지가 없으면 오류가 아닌 빈 배열을 반환한다.

---

- 현재 로그인한 회원이 스터디장이면 `isLeader = true`로 반환한다.
- 스터디장만 공지를 등록, 수정, 삭제할 수 있다.
- 각 공지에 대해 수정·삭제 가능 여부를 `canEdit`, `canDelete`로 반환한다.

---

- 첫 요청에는 `cursor`를 전달하지 않는다.
- `cursor`가 전달되면 해당 공지 ID보다 작은 공지를 조회한다.
- 요청한 `size`보다 한 건 더 조회해 다음 데이터 존재 여부를 판단한다.
- 다음 데이터가 있으면 마지막 공지 ID를 `nextCursor`로 반환한다.
- 다음 데이터가 없으면 `nextCursor = null`, `hasNext = false`로 반환한다.

---

### Response

#### 200 OK

```json
{
  "success": true,
  "code": "STUDY_NOTICE_LIST_SUCCESS",
  "message": "스터디 공지 목록 조회에 성공했습니다.",
  "data": {
    "studyId": 10,
    "isLeader": false,
    "readOnly": false,
    "content": [
      {
        "noticeId": 18,
        "content": "편한 운동화와 필기 도구를 준비해 주세요. 오후 1시 50분까지 모여 주세요.",
        "canEdit": false,
        "canDelete": false,
        "createdAt": "2026-07-24T16:00:00+09:00",
        "updatedAt": "2026-07-24T16:00:00+09:00"
      }
    ],
    "nextCursor": null,
    "hasNext": false
  },
  "timestamp": "2026-07-24T18:25:00+09:00"
}
```

---

### 추가 Exception 예시

#### 400 Bad Request — 잘못된 조회 개수

```json
{
  "success": false,
  "code": "COMMON_INVALID_REQUEST",
  "message": "입력값을 확인해 주세요.",
  "data": {
    "field": "size",
    "reason": "조회 개수는 1 이상 100 이하이어야 합니다."
  },
  "timestamp": "2026-07-22T20:10:00+09:00"
}
```

---

#### 403 Forbidden

```json
{
  "success": false,
  "code": "STUDY_NOTICE_LIST_FORBIDDEN",
  "message": "스터디 공지를 조회할 권한이 없습니다.",
  "data": null,
  "timestamp": "2026-07-22T20:10:00+09:00"
}
```

---

#### 404 Not Found

```json
{
  "success": false,
  "code": "STUDY_NOT_FOUND",
  "message": "스터디를 찾을 수 없습니다.",
  "data": null,
  "timestamp": "2026-07-22T20:10:00+09:00"
}
```

---

#### 500 Internal Server Error

```json
{
  "success": false,
  "code": "COMMON_INTERNAL_SERVER_ERROR",
  "message": "일시적인 오류가 발생했습니다.",
  "data": null,
  "timestamp": "2026-07-22T20:10:00+09:00"
}
```

---

### 추가 프론트 처리 기준

```
isLeader = true
→ 공지 등록 버튼 표시

canEdit = true
→ 해당 공지의 수정 메뉴 표시

canDelete = true
→ 해당 공지의 삭제 메뉴 표시

목록 하단 도달
→ hasNext 확인
→ nextCursor를 cursor로 전달
→ 다음 공지 목록을 기존 목록 뒤에 추가

content가 빈 배열
→ "등록된 공지가 없습니다." 표시

STUDY_NOTICE_LIST_FORBIDDEN 발생
→ "승인된 스터디 멤버만 공지를 확인할 수 있습니다." 안내
→ 스터디 모집 상세 화면으로 이동
```

---

## 스터디 모집 조기 마감

Method: PATCH
Progress: 완료
URI: /api/v1/studies/{studyId}/recruitment/close
담당자: 최태선
연동여부: Yes

스터디장이 모집 중인 스터디의 참여자 모집을 조기에 마감한다.

모집이 마감되면 새로운 참여 신청과 기존 대기 신청의 승인을 제한한다.

---

### Request

- Request HTTP Method: `PATCH`
- 인증 필요: 필요
- Request Body: 없음

#### Request Header

```
Authorization: Bearer {accessToken}
```

#### Path Variable

```
PATCH /api/v1/studies/10/recruitment/close
```

| 필드 | 타입 | 필수 | 설명 |
| --- | --- | --- | --- |
| `studyId` | Long | Y | 모집을 마감할 스터디 ID |

---

### 처리 기준

#### 1. 스터디 및 권한 확인

- 로그인한 회원 ID는 Access Token에서 확인한다.
- `studyId`에 해당하는 스터디가 존재하는지 확인한다.
- 해당 스터디의 스터디장만 모집을 조기 마감할 수 있다.
- 일반 참여자는 모집 상태를 변경할 수 없다.

---

#### 2. 마감 가능 상태

- `RECRUITING` 상태의 스터디만 모집을 마감할 수 있다.
- 이미 `CLOSED` 상태인 스터디는 다시 마감할 수 없다.
- `IN_PROGRESS`, `COMPLETED`, `CANCELED` 상태에서는 모집 마감 요청을 처리할 수 없다.
- 최대 정원에 도달하지 않았더라도 스터디장이 직접 모집을 마감할 수 있다.

---

#### 3. 모집 마감 처리

- 스터디 상태를 `CLOSED`로 변경한다.
- 모집 마감 시각을 서버 시간으로 저장한다.
- 새로운 스터디 참여 신청을 받을 수 없다.
- 기존 승인된 참여자는 그대로 유지한다.
- 현재 승인 멤버 수과 최대 정원은 변경하지 않는다.

---

#### 4. 대기 신청 처리

- 모집 마감 시 `PENDING` 상태의 신청을 자동 거절하지 않고 유지할 수 있다.
- 다만 모집 마감 이후에는 대기 신청을 승인할 수 없다.
- 스터디장은 남아 있는 대기 신청을 거절 처리할 수 있다.
- 대기 신청 자동 거절 여부는 운영 정책에 따라 별도로 적용할 수 있다.

---

### Response

#### 200 OK

```
{
  "success": true,
  "code": "STUDY_RECRUITMENT_CLOSE_SUCCESS",
  "message": "스터디 모집을 마감했습니다.",
  "data": {
    "studyId": 10,
    "status": "CLOSED",
    "currentMemberCount": 4,
    "capacity": 6,
    "pendingApplicationCount": 2,
    "recruitmentClosedAt": "2026-07-22T20:00:00+09:00"
  },
  "timestamp": "2026-07-22T20:00:00+09:00"
}
```

---

### Response Field

| 필드 | 타입 | 설명 |
| --- | --- | --- |
| `studyId` | Long | 모집을 마감한 스터디 ID |
| `status` | String | 변경된 스터디 상태. `CLOSED` |
| `currentMemberCount` | Integer | 현재 승인된 참여 인원 |
| `capacity` | Integer | 최대 정원 |
| `pendingApplicationCount` | Integer | 처리되지 않은 승인 대기 신청 수 |
| `recruitmentClosedAt` | String | 모집 마감 시각 |

---

### Exception

#### 401 Unauthorized

```
{
  "success": false,
  "code": "AUTH_ACCESS_TOKEN_INVALID",
  "message": "로그인이 필요합니다.",
  "data": null,
  "timestamp": "2026-07-22T20:00:00+09:00"
}
```

---

#### 403 Forbidden — 스터디장 아님

```
{
  "success": false,
  "code": "STUDY_RECRUITMENT_CLOSE_FORBIDDEN",
  "message": "스터디 모집을 마감할 권한이 없습니다.",
  "data": null,
  "timestamp": "2026-07-22T20:00:00+09:00"
}
```

---

#### 404 Not Found

```
{
  "success": false,
  "code": "STUDY_NOT_FOUND",
  "message": "스터디를 찾을 수 없습니다.",
  "data": null,
  "timestamp": "2026-07-22T20:00:00+09:00"
}
```

---

#### 409 Conflict — 이미 모집 마감

```
{
  "success": false,
  "code": "STUDY_RECRUITMENT_ALREADY_CLOSED",
  "message": "이미 모집이 마감된 스터디입니다.",
  "data": null,
  "timestamp": "2026-07-22T20:00:00+09:00"
}
```

---

#### 409 Conflict — 마감 불가 상태

```
{
  "success": false,
  "code": "STUDY_RECRUITMENT_CLOSE_NOT_ALLOWED",
  "message": "현재 상태에서는 모집을 마감할 수 없습니다.",
  "data": {
    "studyStatus": "IN_PROGRESS"
  },
  "timestamp": "2026-07-22T20:00:00+09:00"
}
```

---

#### 500 Internal Server Error

```
{
  "success": false,
  "code": "COMMON_INTERNAL_SERVER_ERROR",
  "message": "일시적인 오류가 발생했습니다.",
  "data": null,
  "timestamp": "2026-07-22T20:00:00+09:00"
}
```

---

### 프론트 처리

```
스터디 관리 화면에서 모집 마감 선택
→ 모집 마감 확인 팝업 표시
→ PATCH /api/v1/studies/{studyId}/recruitment/close 호출

모집 마감 성공
→ 스터디 상태를 CLOSED로 변경
→ 참여 신청 버튼 숨김 또는 비활성화
→ 모집 마감 버튼 숨김

pendingApplicationCount > 0
→ "승인 대기 신청이 남아 있습니다." 안내
→ 신청자 관리 화면에서 거절 처리 가능

STUDY_RECRUITMENT_ALREADY_CLOSED 발생
→ 화면 상태를 CLOSED로 동기화

STUDY_RECRUITMENT_CLOSE_FORBIDDEN 발생
→ "스터디장만 모집을 마감할 수 있습니다." 안내
```

---

## 스터디 모집 재개

Method: PATCH
Progress: 완료
URI: /api/v1/studies/{studyId}/recruitment/open
담당자: 최태선
연동여부: No

스터디장이 조기 마감했던 스터디의 참여자 모집을 다시 재개한다.

모집이 재개되면 다시 새로운 참여 신청과 대기 신청 승인을 받을 수 있다.

---

### Request

- Request HTTP Method: `PATCH`
- 인증 필요: 필요
- Request Body: 없음

#### Request Header

```
Authorization: Bearer {accessToken}
```

#### Path Variable

```
PATCH /api/v1/studies/10/recruitment/open
```

| 필드 | 타입 | 필수 | 설명 |
| --- | --- | --- | --- |
| `studyId` | Long | Y | 모집을 재개할 스터디 ID |

---

### 처리 기준

#### 1. 스터디 및 권한 확인

- 로그인한 회원 ID는 Access Token에서 확인한다.
- `studyId`에 해당하는 스터디가 존재하는지 확인한다.
- 해당 스터디의 스터디장만 모집을 재개할 수 있다.
- 일반 참여자는 모집 상태를 변경할 수 없다.

---

#### 2. 재개 가능 상태

- `CLOSED` 상태의 스터디만 모집을 재개할 수 있다.
- 이미 `RECRUITING` 상태인 스터디는 다시 재개할 수 없다.
- `IN_PROGRESS`, `COMPLETED`, `CANCELED` 상태에서는 모집 재개 요청을 처리할 수 없다.
- 현재 승인된 참여 인원이 최대 정원에 도달한 경우 모집을 재개할 수 없다.

---

#### 3. 모집 재개 처리

- 스터디 상태를 `RECRUITING`으로 변경한다.
- 모집 마감 시각(`recruitmentClosedAt`)을 `null`로 초기화한다.
- 다시 새로운 스터디 참여 신청을 받을 수 있다.
- 기존 승인된 참여자는 그대로 유지한다.
- 현재 승인 멤버 수와 최대 정원은 변경하지 않는다.

---

### Response

#### 200 OK

```
{
  "success": true,
  "code": "STUDY_RECRUITMENT_OPEN_SUCCESS",
  "message": "스터디 모집을 재개했습니다.",
  "data": {
    "studyId": 10,
    "status": "RECRUITING",
    "currentMemberCount": 4,
    "capacity": 6,
    "pendingApplicationCount": 2,
    "recruitmentClosedAt": null
  },
  "timestamp": "2026-07-22T20:00:00+09:00"
}
```

---

### Response Field

| 필드 | 타입 | 설명 |
| --- | --- | --- |
| `studyId` | Long | 모집을 재개한 스터디 ID |
| `status` | String | 변경된 스터디 상태. `RECRUITING` |
| `currentMemberCount` | Integer | 현재 승인된 참여 인원 |
| `capacity` | Integer | 최대 정원 |
| `pendingApplicationCount` | Integer | 처리되지 않은 승인 대기 신청 수 |
| `recruitmentClosedAt` | String | 모집 마감 시각. 재개 후에는 `null` |

---

### Exception

#### 401 Unauthorized

```
{
  "success": false,
  "code": "AUTH_ACCESS_TOKEN_INVALID",
  "message": "로그인이 필요합니다.",
  "data": null,
  "timestamp": "2026-07-22T20:00:00+09:00"
}
```

---

#### 403 Forbidden — 스터디장 아님

```
{
  "success": false,
  "code": "STUDY_RECRUITMENT_OPEN_FORBIDDEN",
  "message": "스터디 모집을 재개할 권한이 없습니다.",
  "data": null,
  "timestamp": "2026-07-22T20:00:00+09:00"
}
```

---

#### 404 Not Found

```
{
  "success": false,
  "code": "STUDY_NOT_FOUND",
  "message": "스터디를 찾을 수 없습니다.",
  "data": null,
  "timestamp": "2026-07-22T20:00:00+09:00"
}
```

---

#### 409 Conflict — 이미 모집 중

```
{
  "success": false,
  "code": "STUDY_RECRUITMENT_ALREADY_OPEN",
  "message": "이미 모집 중인 스터디입니다.",
  "data": null,
  "timestamp": "2026-07-22T20:00:00+09:00"
}
```

---

#### 409 Conflict — 재개 불가 상태

```
{
  "success": false,
  "code": "STUDY_RECRUITMENT_OPEN_NOT_ALLOWED",
  "message": "현재 상태에서는 모집을 재개할 수 없습니다.",
  "data": {
    "studyStatus": "IN_PROGRESS"
  },
  "timestamp": "2026-07-22T20:00:00+09:00"
}
```

---

#### 409 Conflict — 정원 마감

```
{
  "success": false,
  "code": "STUDY_CAPACITY_FULL",
  "message": "스터디 정원이 가득 찼습니다.",
  "data": null,
  "timestamp": "2026-07-22T20:00:00+09:00"
}
```

---

#### 500 Internal Server Error

```
{
  "success": false,
  "code": "COMMON_INTERNAL_SERVER_ERROR",
  "message": "일시적인 오류가 발생했습니다.",
  "data": null,
  "timestamp": "2026-07-22T20:00:00+09:00"
}
```

---

### 프론트 처리

```
스터디 관리 화면에서 모집 재개 선택
→ 모집 재개 확인 팝업 표시
→ PATCH /api/v1/studies/{studyId}/recruitment/open 호출

모집 재개 성공
→ 스터디 상태를 RECRUITING으로 변경
→ 참여 신청 버튼 다시 노출
→ 모집 재개 버튼 숨김, 모집 마감 버튼 노출

STUDY_RECRUITMENT_ALREADY_OPEN 발생
→ 화면 상태를 RECRUITING으로 동기화

STUDY_RECRUITMENT_OPEN_FORBIDDEN 발생
→ "스터디장만 모집을 재개할 수 있습니다." 안내

STUDY_CAPACITY_FULL 발생
→ "정원이 가득 차 모집을 재개할 수 없습니다." 안내
```

---

## 스터디 공지 수정

Method: PATCH
Progress: 완료
URI: /api/v1/studies/{studyId}/notices/{noticeId}
담당자: 최태선
연동여부: Yes

스터디장이 자신이 등록한 스터디 공지의 제목, 내용, 상단 고정 여부를 수정한다.

일반 참여자는 공지를 수정할 수 없다.

### Request

- HTTP Method: `PATCH`
- URI: `/api/v1/studies/{studyId}/notices/{noticeId}`
- 인증 필요: 필요
- Content-Type: `application/json`

#### Request Body

```json
{
  "content": "오후 1시 40분까지 옥수역 3번 출구로 모여 주세요."
}
```

#### Request Header

```
Authorization: Bearer {accessToken}
```

#### Path Variable

```
PATCH /api/v1/studies/10/notices/18
```

| 필드 | 타입 | 필수 | 설명 |
| --- | --- | --- | --- |
| `studyId` | Long | Y | 공지가 등록된 스터디 ID |
| `noticeId` | Long | Y | 수정할 공지 ID |

### 처리 기준

- 스터디장만 수정할 수 있다.
- 공지가 요청한 스터디에 속해야 한다.
- `content`만 수정한다.
- `COMPLETED`, `CANCELED` 스터디에서는 수정할 수 없다.
- 기존 `title`, `isPinned` 필드는 삭제한다.

### 추가 상세 처리 기준

- 로그인한 회원 ID는 Access Token에서 확인한다.
- `studyId`에 해당하는 스터디가 존재하는지 확인한다.
- 해당 스터디의 스터디장만 공지를 수정할 수 있다.
- 일반 참여자는 공지를 수정할 수 없다.

---

- `noticeId`에 해당하는 공지가 존재하는지 확인한다.
- 해당 공지가 요청한 `studyId`에 속하는지 확인한다.
- 삭제된 공지는 수정할 수 없다.

---

- 요청에 포함된 필드만 수정한다.
- 전달되지 않은 필드는 기존 값을 유지한다.
- 제목과 내용의 앞뒤 공백을 제거한 후 저장한다.
- 제목이나 내용을 빈 문자열 또는 공백만 있는 값으로 수정할 수 없다.
- 수정할 필드가 하나도 없으면 잘못된 요청으로 처리한다.
- 수정 시 `updatedAt`을 현재 서버 시각으로 갱신한다.
- `createdAt`은 변경하지 않는다.

---

- 취소된 스터디의 공지는 수정할 수 없다.
- 완료된 스터디의 공지 수정 허용 여부는 운영 정책에 따라 제한할 수 있다.
- 공지 수정으로 별도의 알림을 발송할지 여부는 운영 정책에 따른다.

---

### Response

#### 200 OK

```json
{
  "success": true,
  "code": "STUDY_NOTICE_UPDATE_SUCCESS",
  "message": "스터디 공지가 수정되었습니다.",
  "data": {
    "noticeId": 18,
    "studyId": 10,
    "content": "오후 1시 40분까지 옥수역 3번 출구로 모여 주세요.",
    "createdAt": "2026-07-24T18:30:00+09:00",
    "updatedAt": "2026-07-24T18:35:00+09:00"
  },
  "timestamp": "2026-07-24T18:35:00+09:00"
}
```

### Exception

- `400 STUDY_NOTICE_STUDY_MISMATCH`
- `400 COMMON_INVALID_REQUEST`
- `401 AUTH_ACCESS_TOKEN_INVALID`
- `403 STUDY_NOTICE_UPDATE_FORBIDDEN`
- `404 STUDY_NOT_FOUND`
- `404 STUDY_NOTICE_NOT_FOUND`
- `409 STUDY_NOTICE_UPDATE_NOT_ALLOWED`

---

### 추가 Exception 예시

#### 400 Bad Request — 수정할 필드 없음

```json
{
  "success": false,
  "code": "STUDY_NOTICE_UPDATE_EMPTY",
  "message": "수정할 내용을 입력해 주세요.",
  "data": null,
  "timestamp": "2026-07-22T20:30:00+09:00"
}
```

---

#### 400 Bad Request — 다른 스터디의 공지

```json
{
  "success": false,
  "code": "STUDY_NOTICE_STUDY_MISMATCH",
  "message": "해당 스터디의 공지가 아닙니다.",
  "data": null,
  "timestamp": "2026-07-22T20:30:00+09:00"
}
```

---

#### 401 Unauthorized

```json
{
  "success": false,
  "code": "AUTH_ACCESS_TOKEN_INVALID",
  "message": "로그인이 필요합니다.",
  "data": null,
  "timestamp": "2026-07-22T20:30:00+09:00"
}
```

---

#### 403 Forbidden — 스터디장 아님

```json
{
  "success": false,
  "code": "STUDY_NOTICE_UPDATE_FORBIDDEN",
  "message": "스터디 공지를 수정할 권한이 없습니다.",
  "data": null,
  "timestamp": "2026-07-22T20:30:00+09:00"
}
```

---

#### 404 Not Found — 스터디 없음

```json
{
  "success": false,
  "code": "STUDY_NOT_FOUND",
  "message": "스터디를 찾을 수 없습니다.",
  "data": null,
  "timestamp": "2026-07-22T20:30:00+09:00"
}
```

---

#### 404 Not Found — 공지 없음

```json
{
  "success": false,
  "code": "STUDY_NOTICE_NOT_FOUND",
  "message": "스터디 공지를 찾을 수 없습니다.",
  "data": null,
  "timestamp": "2026-07-22T20:30:00+09:00"
}
```

---

#### 409 Conflict — 수정 불가 상태

```json
{
  "success": false,
  "code": "STUDY_NOTICE_UPDATE_NOT_ALLOWED",
  "message": "현재 상태에서는 공지를 수정할 수 없습니다.",
  "data": {
    "studyStatus": "CANCELED"
  },
  "timestamp": "2026-07-22T20:30:00+09:00"
}
```

---

#### 500 Internal Server Error

```json
{
  "success": false,
  "code": "COMMON_INTERNAL_SERVER_ERROR",
  "message": "일시적인 오류가 발생했습니다.",
  "data": null,
  "timestamp": "2026-07-22T20:30:00+09:00"
}
```

---

### 추가 프론트 처리 기준

```
공지 목록 또는 상세 화면에서 수정 선택
→ 기존 제목, 내용, 고정 여부를 수정 화면에 표시
→ PATCH /api/v1/studies/{studyId}/notices/{noticeId} 호출

STUDY_NOTICE_UPDATE_FORBIDDEN 발생
→ 수정 화면 종료
→ "스터디장만 공지를 수정할 수 있습니다." 안내

STUDY_NOTICE_NOT_FOUND 발생
→ "삭제되었거나 존재하지 않는 공지입니다." 안내
→ 공지 목록 다시 조회

입력값 오류
→ data.field에 해당하는 입력 영역에 오류 표시
→ 사용자가 입력한 내용 유지
```

---

## 파일 메타데이터/접근 URL 조회

Method: GET
Progress: 완료
URI: /api/v1/media/{fileId}
담당자: 김윤석
연동여부: No

권한이 있는 파일의 메타데이터와 임시 접근 URL을 조회한다.

파일 메타데이터와 조회용 임시 접근 URL을 조회한다.

이미 업로드된 사진을 화면에 표시할 때 사용하며, 만료된 조회 URL을 새로 발급받는 용도로도 사용한다.

### Request

- HTTP Method: `GET`
- URI: `/api/v1/media/{fileId}`
- 인증 필요: 필요

#### Request Header

```
Authorization: Bearer {accessToken}
```

#### Path Variable

```
GET /api/v1/media/{fileId}
```

| 필드 | 타입 | 필수 | 설명 |
| --- | --- | --- | --- |
| `fileId` | Long | Y | 조회할 파일 메타 ID |

---

### 처리 기준

- 파일 소유자 또는 파일이 연결된 스터디의 `ACTIVE` 멤버만 조회한다.
- 게시글 첨부는 해당 게시글을 조회할 수 있는 사용자에게 접근을 허용한다.
- `uploadStatus=COMPLETED`이고 `deletedAt=null`인 파일만 접근 URL을 반환한다.
- `STT_AUDIO`가 STT 성공 후 삭제됐거나 만료 배치로 삭제된 경우 `uploadStatus=DELETED`, `accessUrl=null`로 반환한다.
- STT 성공 후 음성 원본을 다시 열 수 있는 API를 제공하지 않는다.

### Response

#### 200 OK — 접근 가능

```json
{
  "success": true,
  "code": "MEDIA_FILE_GET_SUCCESS",
  "message": "파일 정보 조회에 성공했습니다.",
  "data": {
    "fileId": 89,
    "fileUsage": "FIELD_PHOTO",
    "originalName": "entrance.jpg",
    "contentType": "image/jpeg",
    "sizeBytes": 1048576,
    "uploadStatus": "COMPLETED",
    "accessUrl": "<https://s3>.../field-photo/7/42/uuid.jpg?...",
    "accessUrlExpiresAt": "2026-07-24T20:30:00+09:00",
    "fileExpiresAt": null,
    "createdAt": "2026-07-24T19:30:00+09:00"
  },
  "timestamp": "2026-07-24T19:40:00+09:00"
}
```

#### 200 OK — 삭제된 STT 음성

```json
{
  "success": true,
  "code": "MEDIA_FILE_GET_SUCCESS",
  "message": "파일 정보 조회에 성공했습니다.",
  "data": {
    "fileId": 90,
    "fileUsage": "STT_AUDIO",
    "originalName": "recording.m4a",
    "contentType": "audio/mp4",
    "sizeBytes": 524288,
    "uploadStatus": "DELETED",
    "accessUrl": null,
    "accessUrlExpiresAt": null,
    "fileExpiresAt": "2026-07-25T19:30:00+09:00",
    "createdAt": "2026-07-24T19:30:00+09:00"
  },
  "timestamp": "2026-07-24T19:40:00+09:00"
}
```

### Exception

- `401 AUTH_ACCESS_TOKEN_INVALID`
- `403 MEDIA_FORBIDDEN`
- `404 MEDIA_FILE_NOT_FOUND`
- `500 COMMON_INTERNAL_SERVER_ERROR`

---

## 파일 삭제

Domain: Media
Method: DELETE
Progress: 완료
URI: /api/v1/media/{fileId}
담당자: 김윤석
연동여부: No

파일 소유자가 업로드한 파일을 삭제한다. S3 객체를 삭제한 뒤 파일 메타를 소프트 삭제(`uploadStatus=DELETED`, `deletedAt` 기록)한다.

### Request

- HTTP Method: `DELETE`
- URI: `/api/v1/media/{fileId}`
- 인증 필요: 필요
- Request Body: 없음

#### Request Header

```
Authorization: Bearer {accessToken}
```

#### Path Variable

| 필드 | 타입 | 필수 | 설명 |
| --- | --- | --- | --- |
| `fileId` | Long | Y | 삭제할 파일 메타 ID, 1 이상 |

### 처리 기준

- 파일 소유자만 삭제할 수 있다. 소유자가 아니면 `403 MEDIA_NOT_OWNER`다.
- 이미 삭제된 파일은 오류가 아니라 기존 `deletedAt`을 그대로 담아 `200`으로 멱등 응답한다.
- `uploadStatus=COMPLETED`가 아닌 파일(`PENDING`·`FAILED`)은 `403 MEDIA_ACCESS_DENIED`다.
- 미디어 게이트웨이로 S3 객체를 삭제한다. 객체가 이미 없으면(NOT_FOUND) 계속 진행하고, 그 외 게이트웨이 실패는 `503 MEDIA_GATEWAY_UNAVAILABLE`다.
- 삭제 성공 시 파일 메타를 `uploadStatus=DELETED`, `deletedAt=현재 시각`으로 변경한다. 물리 행은 삭제하지 않는다.

### Response

#### 200 OK

```json
{
  "success": true,
  "code": "MEDIA_FILE_DELETE_SUCCESS",
  "message": "파일을 삭제했습니다.",
  "data": {
    "fileId": 89,
    "uploadStatus": "DELETED",
    "deletedAt": "2026-08-06T15:00:00+09:00"
  },
  "timestamp": "2026-08-06T15:00:00+09:00"
}
```

#### Response Field

| 필드 | 타입 | 설명 |
| --- | --- | --- |
| `fileId` | Long | 파일 메타 ID |
| `uploadStatus` | String | 항상 `DELETED` |
| `deletedAt` | String | 삭제 처리 시각, 이미 삭제된 파일이면 기존 삭제 시각 |

### Exception

- `400 COMMON_INVALID_REQUEST` — `fileId`가 1 미만
- `401 AUTH_ACCESS_TOKEN_INVALID`
- `403 MEDIA_NOT_OWNER` — 파일 소유자가 아님
- `403 MEDIA_ACCESS_DENIED` — 완료 상태가 아닌 파일
- `404 MEDIA_FILE_NOT_FOUND`
- `503 MEDIA_GATEWAY_UNAVAILABLE` — 스토리지 게이트웨이 삭제 실패

---

## 임장 일정 수정

Method: PATCH
Progress: 완료
URI: /api/v1/studies/{studyId}/schedule
담당자: 최태선
연동여부: Yes

스터디장이 등록된 임장 일정의 날짜, 시간, 집결 장소를 수정한다.

변경된 일정은 승인된 스터디 참여자에게 공유되며, 일정 변경 알림을 발송할 수 있다.

### Request

- HTTP Method: `PATCH`
- URI: `/api/v1/studies/{studyId}/schedule`
- 인증 필요: 필요
- Content-Type: `application/json`

#### Request Body

```json
{
  "startAt": "2026-07-27T16:00:00+09:00",
  "endAt": "2026-07-27T19:00:00+09:00",
  "meetingPlace": "옥수역 4번 출구"
}
```

#### Request Header

```
Authorization: Bearer {accessToken}
```

#### Path Variable

```
PATCH /api/v1/studies/10/schedule
```

| 필드 | 타입 | 필수 | 설명 |
| --- | --- | --- | --- |
| `studyId` | Long | Y | 임장 일정을 수정할 스터디 ID |

### 처리 기준

- 요청에 포함된 필드만 수정한다.
- 스터디장만 수정할 수 있다.
- `SCHEDULED` 일정만 수정할 수 있다.
- 일정 변경 시 기존 D-1 예약을 취소하고 다시 예약한다.
- 승인 멤버에게 `SCHEDULE_CHANGED` 알림과 SYSTEM 채팅 메시지를 한 번 생성한다.
- 기존 날짜·시간 분리 필드와 좌표 필드는 사용하지 않는다.

### 추가 상세 처리 기준

- 로그인한 회원 ID는 Access Token에서 확인한다.
- `studyId`에 해당하는 스터디가 존재하는지 확인한다.
- 해당 스터디의 스터디장만 임장 일정을 수정할 수 있다.
- 일반 참여자는 일정을 수정할 수 없다.

---

- 해당 스터디에 등록된 임장 일정이 존재하는지 확인한다.
- 등록된 일정이 없는 경우 일정 등록 API를 사용해야 한다.

```
POST /api/v1/studies/{studyId}/schedule
```

---

- 전달되지 않은 필드는 기존 값을 유지한다.
- 집결 장소와 상세 설명은 앞뒤 공백을 제거한 후 저장한다.
- 집결 장소를 빈 문자열이나 공백만 있는 값으로 수정할 수 없다.
- 수정할 필드가 하나도 없으면 잘못된 요청으로 처리한다.
- 수정 시 `updatedAt`을 현재 서버 시각으로 갱신한다.
- 기존 `createdAt`은 변경하지 않는다.

---

- 수정된 임장 시작 일시는 현재 시각 이후여야 한다.
- 종료 시각이 있는 경우 시작 시각보다 늦어야 한다.
- 위도는 `90~90`, 경도는 `180~180` 범위여야 한다.
- `RECRUITING`, `CLOSED` 상태의 스터디만 일정을 수정할 수 있다.
- 임장이 시작된 `IN_PROGRESS` 상태에서는 수정할 수 없다.
- `COMPLETED`, `CANCELED` 상태에서는 수정할 수 없다.

---

- 일정이 변경되면 승인된 스터디 참여자에게 일정 변경 알림을 생성한다.
- 날짜, 시작 시각 또는 집결 장소가 변경된 경우 알림을 발송한다.
- 알림에는 변경된 임장 일시와 집결 장소를 포함한다.
- 푸시 알림 발송 여부는 회원의 서비스 알림 수신 설정을 따른다.

---

### Response

#### 200 OK

```json
{
  "success": true,
  "code": "STUDY_SCHEDULE_UPDATE_SUCCESS",
  "message": "임장 일정이 수정되었습니다.",
  "data": {
    "scheduleId": 7,
    "studyId": 10,
    "status": "SCHEDULED",
    "startAt": "2026-07-27T16:00:00+09:00",
    "endAt": "2026-07-27T19:00:00+09:00",
    "meetingPlace": "옥수역 4번 출구",
    "updatedAt": "2026-07-24T19:00:00+09:00"
  },
  "timestamp": "2026-07-24T19:00:00+09:00"
}
```

### Exception

- `400 STUDY_SCHEDULE_UPDATE_EMPTY`
- `400 STUDY_SCHEDULE_TIME_INVALID`
- `401 AUTH_ACCESS_TOKEN_INVALID`
- `403 STUDY_SCHEDULE_UPDATE_FORBIDDEN`
- `404 STUDY_NOT_FOUND`
- `404 STUDY_SCHEDULE_NOT_FOUND`
- `409 STUDY_SCHEDULE_UPDATE_NOT_ALLOWED`

---

### 추가 Exception 예시

#### 400 Bad Request — 수정할 필드 없음

```json
{
  "success": false,
  "code": "STUDY_SCHEDULE_UPDATE_EMPTY",
  "message": "수정할 내용을 입력해 주세요.",
  "data": null,
  "timestamp": "2026-07-22T21:10:00+09:00"
}
```

---

#### 400 Bad Request — 종료 시각 오류

```json
{
  "success": false,
  "code": "STUDY_SCHEDULE_TIME_INVALID",
  "message": "종료 시각은 시작 시각보다 늦어야 합니다.",
  "data": {
    "field": "endTime"
  },
  "timestamp": "2026-07-22T21:10:00+09:00"
}
```

---

#### 401 Unauthorized

```json
{
  "success": false,
  "code": "AUTH_ACCESS_TOKEN_INVALID",
  "message": "로그인이 필요합니다.",
  "data": null,
  "timestamp": "2026-07-22T21:10:00+09:00"
}
```

---

#### 403 Forbidden — 스터디장 아님

```json
{
  "success": false,
  "code": "STUDY_SCHEDULE_UPDATE_FORBIDDEN",
  "message": "임장 일정을 수정할 권한이 없습니다.",
  "data": null,
  "timestamp": "2026-07-22T21:10:00+09:00"
}
```

---

#### 404 Not Found — 스터디 없음

```json
{
  "success": false,
  "code": "STUDY_NOT_FOUND",
  "message": "스터디를 찾을 수 없습니다.",
  "data": null,
  "timestamp": "2026-07-22T21:10:00+09:00"
}
```

---

#### 404 Not Found — 일정 없음

```json
{
  "success": false,
  "code": "STUDY_SCHEDULE_NOT_FOUND",
  "message": "등록된 임장 일정을 찾을 수 없습니다.",
  "data": null,
  "timestamp": "2026-07-22T21:10:00+09:00"
}
```

---

#### 409 Conflict — 수정 불가 상태

```json
{
  "success": false,
  "code": "STUDY_SCHEDULE_UPDATE_NOT_ALLOWED",
  "message": "현재 상태에서는 임장 일정을 수정할 수 없습니다.",
  "data": {
    "studyStatus": "IN_PROGRESS"
  },
  "timestamp": "2026-07-22T21:10:00+09:00"
}
```

---

#### 500 Internal Server Error

```json
{
  "success": false,
  "code": "COMMON_INTERNAL_SERVER_ERROR",
  "message": "일시적인 오류가 발생했습니다.",
  "data": null,
  "timestamp": "2026-07-22T21:10:00+09:00"
}
```

---

### 추가 프론트 처리 기준

```
임장 일정 화면에서 수정 선택
→ 기존 일정 정보를 수정 화면에 표시
→ PATCH /api/v1/studies/{studyId}/schedule 호출

일정 수정 성공
→ 응답 데이터로 일정 화면 갱신
→ "임장 일정이 수정되었습니다." 안내

STUDY_SCHEDULE_NOT_FOUND 발생
→ 일정 등록 화면으로 이동

STUDY_SCHEDULE_UPDATE_FORBIDDEN 발생
→ "스터디장만 일정을 수정할 수 있습니다." 안내
→ 수정 화면 종료

STUDY_SCHEDULE_UPDATE_NOT_ALLOWED 발생
→ 수정 버튼 비활성화
→ 현재 스터디 상태에 맞는 안내 표시
```

---

## 임장 일정 등록

Method: POST
Progress: 완료
URI: /api/v1/studies/{studyId}/schedule
담당자: 최태선
연동여부: Yes

스터디장이 해당 스터디의 임장 일시와 집결 장소를 등록한다.

등록된 일정은 승인된 스터디 참여자에게 공유되며, 스터디 홈과 임장 일정 화면에서 확인할 수 있다.

### Request

- HTTP Method: `POST`
- URI: `/api/v1/studies/{studyId}/schedule`
- 인증 필요: 필요
- Content-Type: `application/json`

#### Request Body

```json
{
  "startAt": "2026-07-27T15:00:00+09:00",
  "endAt": "2026-07-27T18:00:00+09:00",
  "meetingPlace": "옥수역 3번 출구"
}
```

| 필드 | 타입 | 필수 | 설명 |
| --- | --- | --- | --- |
| `startAt` | String | Y | 임장 시작 시각 |
| `endAt` | String | N | 임장 종료 예정 시각 |
| `meetingPlace` | String | Y | 집결 장소, 최대 200자 |

#### Request Header

```
Authorization: Bearer {accessToken}
```

#### Path Variable

```
POST /api/v1/studies/10/schedule
```

| 필드 | 타입 | 필수 | 설명 |
| --- | --- | --- | --- |
| `studyId` | Long | Y | 임장 일정을 등록할 스터디 ID |

### 처리 기준

- 스터디장만 등록할 수 있다.
- 시작 시각은 현재 이후여야 하며 종료 시각은 시작 시각 이상이어야 한다.
- `RECRUITING`, `CLOSED` 스터디에서만 등록할 수 있다.
- 일정 행이 없으면 생성한다.
- 기존 일정 행이 `CANCELED`이면 같은 행을 새 값으로 갱신하고 `SCHEDULED`로 재활성화한다.
- D-1 알림을 예약한다.
- 승인 멤버에게 일정 등록 알림과 SYSTEM 채팅 메시지를 생성한다.

### 추가 상세 처리 기준

- 로그인한 회원 ID는 Access Token에서 확인한다.
- `studyId`에 해당하는 스터디가 존재하는지 확인한다.
- 해당 스터디의 스터디장만 임장 일정을 등록할 수 있다.
- 일반 참여자는 일정을 등록할 수 없다.

---

- 임장 시작 일시는 현재 시각 이후여야 한다.
- 종료 시각이 있는 경우 시작 시각보다 늦어야 한다.
- 집결 장소와 상세 설명의 앞뒤 공백을 제거한 후 저장한다.
- 집결 장소는 공백만 입력할 수 없다.
- 위도와 경도는 함께 전달하거나 모두 전달하지 않아야 한다.
- 위도는 `90~90`, 경도는 `180~180` 범위여야 한다.

---

- 해당 스터디에 등록된 일정이 없는 경우에만 생성할 수 있다.
- 일정 상태는 `SCHEDULED`로 저장한다.
- 생성 시각과 수정 시각은 서버 시간을 기준으로 저장한다.
- 스터디 생성 시 이미 일정이 저장된 구조라면 중복 등록할 수 없으며 일정 수정 API를 사용한다.

```
PATCH /api/v1/studies/{studyId}/schedule
```

---

- `RECRUITING`, `CLOSED` 상태의 스터디에 일정을 등록할 수 있다.
- 임장이 시작된 `IN_PROGRESS` 상태에서는 새 일정을 등록할 수 없다.
- `COMPLETED`, `CANCELED` 상태에서는 일정을 등록할 수 없다.

---

- 일정 등록 시 승인된 스터디 참여자에게 임장 일정 알림을 생성할 수 있다.
- 알림에는 임장 일시와 집결 장소를 포함한다.
- 푸시 알림 발송 여부는 회원의 서비스 알림 수신 설정을 따른다.

---

### Response

#### 201 Created

```json
{
  "success": true,
  "code": "STUDY_SCHEDULE_CREATE_SUCCESS",
  "message": "임장 일정이 등록되었습니다.",
  "data": {
    "scheduleId": 7,
    "studyId": 10,
    "status": "SCHEDULED",
    "startAt": "2026-07-27T15:00:00+09:00",
    "endAt": "2026-07-27T18:00:00+09:00",
    "meetingPlace": "옥수역 3번 출구",
    "createdAt": "2026-07-24T18:45:00+09:00",
    "updatedAt": "2026-07-24T18:45:00+09:00"
  },
  "timestamp": "2026-07-24T18:45:00+09:00"
}
```

### Exception

- `400 STUDY_SCHEDULE_TIME_INVALID`
- `401 AUTH_ACCESS_TOKEN_INVALID`
- `403 STUDY_SCHEDULE_CREATE_FORBIDDEN`
- `404 STUDY_NOT_FOUND`
- `409 STUDY_SCHEDULE_ALREADY_EXISTS`
- `409 STUDY_SCHEDULE_CREATE_NOT_ALLOWED`

---

### 추가 Exception 예시

#### 400 Bad Request — 입력값 오류

```json
{
  "success": false,
  "code": "COMMON_INVALID_REQUEST",
  "message": "입력값을 확인해 주세요.",
  "data": {
    "field": "meetingPlace",
    "reason": "집결 장소를 입력해 주세요."
  },
  "timestamp": "2026-07-22T20:55:00+09:00"
}
```

---

#### 400 Bad Request — 종료 시각 오류

```json
{
  "success": false,
  "code": "STUDY_SCHEDULE_TIME_INVALID",
  "message": "종료 시각은 시작 시각보다 늦어야 합니다.",
  "data": {
    "field": "endTime"
  },
  "timestamp": "2026-07-22T20:55:00+09:00"
}
```

---

#### 401 Unauthorized

```json
{
  "success": false,
  "code": "AUTH_ACCESS_TOKEN_INVALID",
  "message": "로그인이 필요합니다.",
  "data": null,
  "timestamp": "2026-07-22T20:55:00+09:00"
}
```

---

#### 403 Forbidden — 스터디장 아님

```json
{
  "success": false,
  "code": "STUDY_SCHEDULE_CREATE_FORBIDDEN",
  "message": "임장 일정을 등록할 권한이 없습니다.",
  "data": null,
  "timestamp": "2026-07-22T20:55:00+09:00"
}
```

---

#### 404 Not Found

```json
{
  "success": false,
  "code": "STUDY_NOT_FOUND",
  "message": "스터디를 찾을 수 없습니다.",
  "data": null,
  "timestamp": "2026-07-22T20:55:00+09:00"
}
```

---

#### 409 Conflict — 일정이 이미 존재함

```json
{
  "success": false,
  "code": "STUDY_SCHEDULE_ALREADY_EXISTS",
  "message": "이미 등록된 임장 일정이 있습니다.",
  "data": null,
  "timestamp": "2026-07-22T20:55:00+09:00"
}
```

---

#### 409 Conflict — 등록 불가 상태

```json
{
  "success": false,
  "code": "STUDY_SCHEDULE_CREATE_NOT_ALLOWED",
  "message": "현재 상태에서는 임장 일정을 등록할 수 없습니다.",
  "data": {
    "studyStatus": "IN_PROGRESS"
  },
  "timestamp": "2026-07-22T20:55:00+09:00"
}
```

---

#### 500 Internal Server Error

```json
{
  "success": false,
  "code": "COMMON_INTERNAL_SERVER_ERROR",
  "message": "일시적인 오류가 발생했습니다.",
  "data": null,
  "timestamp": "2026-07-22T20:55:00+09:00"
}
```

---

### 추가 프론트 처리 기준

```
임장 일정 화면에서 일정 등록 선택
→ 날짜, 시작·종료 시각과 집결 장소 입력
→ POST /api/v1/studies/{studyId}/schedule 호출

지도에서 집결 장소 선택
→ meetingPlace와 위도·경도를 함께 요청에 포함

일정 등록 성공
→ 응답 데이터로 일정 화면 갱신
→ 승인된 참여자에게 일정이 공유됨
→ "임장 일정이 등록되었습니다." 안내

STUDY_SCHEDULE_ALREADY_EXISTS 발생
→ 일정 등록 화면 종료
→ 기존 일정 조회 또는 수정 화면으로 이동

STUDY_SCHEDULE_CREATE_FORBIDDEN 발생
→ "스터디장만 일정을 등록할 수 있습니다." 안내
```

---

## 임장 일정 조회

Method: GET
Progress: 완료
URI: /api/v1/studies/{studyId}/schedule
담당자: 최태선
연동여부: Yes

스터디의 활성 임장 일정과 캘린더 연동용 데이터를 조회한다.

승인된 스터디 참여자가 해당 스터디의 임장 일정과 집결 장소를 조회한다.

스터디장 여부와 일정 관리 권한도 함께 반환한다.

### Request

- HTTP Method: `GET`
- URI: `/api/v1/studies/{studyId}/schedule`
- 인증 필요: 필요

#### Request Header

```
Authorization: Bearer {accessToken}
```

#### Path Variable

```
GET /api/v1/studies/10/schedule
```

| 필드 | 타입 | 필수 | 설명 |
| --- | --- | --- | --- |
| `studyId` | Long | Y | 임장 일정을 조회할 스터디 ID |

---

### 처리 기준

- 스터디장과 `ACTIVE` 멤버만 조회할 수 있다.
- 활성 일정은 `status=SCHEDULED` 또는 `COMPLETED`인 일정이다.
- 내부 일정 행이 `CANCELED`이면 화면 응답은 `schedule=null`로 반환한다.
- 기존 `visitDate`, `startTime`, `meetingPlaceDetail`, 위도·경도 필드는 사용하지 않는다.
- 완료 스터디는 조회 가능하지만 `canManageSchedule=false`다.

### 추가 상세 처리 기준

- 로그인한 회원 ID는 Access Token에서 확인한다.
- `studyId`에 해당하는 스터디가 존재하는지 확인한다.
- 스터디장과 승인된 참여자만 일정을 조회할 수 있다.
- 신청 대기자, 거절된 신청자와 비참여 회원은 조회할 수 없다.
- 취소되거나 완료된 스터디도 기존 참여자는 일정을 조회할 수 있다.

---

- 등록된 임장 일시와 집결 장소를 반환한다.
- 집결 장소 좌표가 등록된 경우 위도와 경도를 함께 반환한다.
- 일정이 등록되지 않은 경우 오류가 아닌 `schedule = null`로 반환한다.
- 일정 변경 시각을 `updatedAt`으로 반환한다.

---

- 현재 시각과 스터디 상태를 기준으로 일정 상태를 반환한다.
- 스터디가 취소된 경우 일정 상태도 `CANCELED`로 반환한다.

---

- 현재 사용자가 스터디장이면 `isLeader = true`로 반환한다.
- 스터디장만 일정을 등록, 수정, 삭제할 수 있다.
- 일정 관리 가능 여부를 `canManageSchedule`로 반환한다.
- 임장이 시작되거나 완료된 경우 일정 수정과 삭제를 제한한다.

---

### Response

#### 200 OK

```json
{
  "success": true,
  "code": "STUDY_SCHEDULE_DETAIL_SUCCESS",
  "message": "임장 일정 조회에 성공했습니다.",
  "data": {
    "studyId": 10,
    "studyTitle": "옥수동 주말 임장",
    "studyStatus": "CLOSED",
    "isLeader": false,
    "canManageSchedule": false,
    "schedule": {
      "scheduleId": 7,
      "status": "SCHEDULED",
      "startAt": "2026-07-27T15:00:00+09:00",
      "endAt": "2026-07-27T18:00:00+09:00",
      "meetingPlace": "옥수역 3번 출구",
      "calendarEvent": {
        "title": "옥수동 주말 임장",
        "startAt": "2026-07-27T15:00:00+09:00",
        "endAt": "2026-07-27T18:00:00+09:00",
        "location": "옥수역 3번 출구"
      },
      "createdAt": "2026-07-24T18:45:00+09:00",
      "updatedAt": "2026-07-24T18:45:00+09:00"
    }
  },
  "timestamp": "2026-07-24T18:50:00+09:00"
}
```

#### 일정 없음

```json
{
  "studyId": 10,
  "isLeader": true,
  "canManageSchedule": true,
  "schedule": null
}
```

---

### 추가 Exception 예시

#### 401 Unauthorized

```json
{
  "success": false,
  "code": "AUTH_ACCESS_TOKEN_INVALID",
  "message": "로그인이 필요합니다.",
  "data": null,
  "timestamp": "2026-07-22T20:50:00+09:00"
}
```

---

#### 403 Forbidden

```json
{
  "success": false,
  "code": "STUDY_SCHEDULE_ACCESS_DENIED",
  "message": "임장 일정을 조회할 권한이 없습니다.",
  "data": null,
  "timestamp": "2026-07-22T20:50:00+09:00"
}
```

---

#### 404 Not Found

```json
{
  "success": false,
  "code": "STUDY_NOT_FOUND",
  "message": "스터디를 찾을 수 없습니다.",
  "data": null,
  "timestamp": "2026-07-22T20:50:00+09:00"
}
```

---

#### 500 Internal Server Error

```json
{
  "success": false,
  "code": "COMMON_INTERNAL_SERVER_ERROR",
  "message": "일시적인 오류가 발생했습니다.",
  "data": null,
  "timestamp": "2026-07-22T20:50:00+09:00"
}
```

---

### 추가 프론트 처리 기준

```
스터디 홈에서 임장 일정 선택
→ GET /api/v1/studies/{studyId}/schedule 호출
→ 임장 일시와 집결 장소 표시

schedule = null
→ "등록된 임장 일정이 없습니다." 표시
→ canManageSchedule = true이면 일정 등록 버튼 표시

canManageSchedule = true
→ 일정 수정 및 삭제 메뉴 표시

STUDY_SCHEDULE_ACCESS_DENIED 발생
→ "승인된 스터디 멤버만 일정을 확인할 수 있습니다." 안내
→ 스터디 모집 상세 화면으로 이동
```

---

## 스터디 공지 삭제

Method: DELETE
Progress: 완료
URI: /api/v1/studies/{studyId}/notices/{noticeId}
담당자: 최태선
연동여부: Yes

URI와 권한 구조는 유지하되 제목·고정 공지 관련 설명을 제거한다.

스터디장이 등록된 스터디 공지를 삭제한다.

삭제된 공지는 공지 목록과 스터디 홈의 최근 공지에서 더 이상 조회되지 않는다.

### Request

- HTTP Method: `DELETE`
- URI: `/api/v1/studies/{studyId}/notices/{noticeId}`
- 인증 필요: 필요
- Request Body: 없음

#### Request Header

```
Authorization: Bearer {accessToken}
```

#### Path Variable

```
DELETE /api/v1/studies/10/notices/18
```

| 필드 | 타입 | 필수 | 설명 |
| --- | --- | --- | --- |
| `studyId` | Long | Y | 공지가 등록된 스터디 ID |
| `noticeId` | Long | Y | 삭제할 공지 ID |

---

### 처리 기준

- 스터디장만 삭제할 수 있다.
- 공지가 요청한 스터디에 속해야 한다.
- `COMPLETED`, `CANCELED` 스터디에서는 삭제할 수 없다.
- 삭제 후 목록에 노출하지 않는다.
- 고정 공지 재정렬·최근 공지 제목 갱신 같은 기존 정책은 사용하지 않는다.

### 추가 상세 처리 기준

- 로그인한 회원 ID는 Access Token에서 확인한다.
- `studyId`에 해당하는 스터디가 존재하는지 확인한다.
- 해당 스터디의 스터디장만 공지를 삭제할 수 있다.
- 일반 참여자는 공지를 삭제할 수 없다.

---

- `noticeId`에 해당하는 공지가 존재하는지 확인한다.
- 해당 공지가 요청한 `studyId`에 속하는지 확인한다.
- 이미 삭제된 공지는 다시 삭제할 수 없다.

---

- 공지는 즉시 물리 삭제하거나 삭제 상태로 변경할 수 있다.
- 삭제된 공지는 공지 목록에서 제외한다.
- 삭제된 공지가 스터디 홈의 최근 공지였다면 다음 최신 공지를 표시한다.
- 공지 삭제 시각은 서버 시간을 기준으로 저장한다.

---

- 취소된 스터디의 공지는 삭제할 수 없다.
- 완료된 스터디의 공지 삭제 허용 여부는 운영 정책에 따라 제한할 수 있다.
- 공지 삭제로 승인된 멤버나 스터디 상태는 변경되지 않는다.

---

### Response

#### 200 OK

```json
{
  "success": true,
  "code": "STUDY_NOTICE_DELETE_SUCCESS",
  "message": "스터디 공지가 삭제되었습니다.",
  "data": {
    "studyId": 10,
    "noticeId": 18,
    "deletedAt": "2026-07-24T18:40:00+09:00"
  },
  "timestamp": "2026-07-24T18:40:00+09:00"
}
```

---

### 추가 Exception 예시

#### 400 Bad Request — 다른 스터디의 공지

```json
{
  "success": false,
  "code": "STUDY_NOTICE_STUDY_MISMATCH",
  "message": "해당 스터디의 공지가 아닙니다.",
  "data": null,
  "timestamp": "2026-07-22T20:40:00+09:00"
}
```

---

#### 401 Unauthorized

```json
{
  "success": false,
  "code": "AUTH_ACCESS_TOKEN_INVALID",
  "message": "로그인이 필요합니다.",
  "data": null,
  "timestamp": "2026-07-22T20:40:00+09:00"
}
```

---

#### 403 Forbidden — 스터디장 아님

```json
{
  "success": false,
  "code": "STUDY_NOTICE_DELETE_FORBIDDEN",
  "message": "스터디 공지를 삭제할 권한이 없습니다.",
  "data": null,
  "timestamp": "2026-07-22T20:40:00+09:00"
}
```

---

#### 404 Not Found — 스터디 없음

```json
{
  "success": false,
  "code": "STUDY_NOT_FOUND",
  "message": "스터디를 찾을 수 없습니다.",
  "data": null,
  "timestamp": "2026-07-22T20:40:00+09:00"
}
```

---

#### 404 Not Found — 공지 없음

```json
{
  "success": false,
  "code": "STUDY_NOTICE_NOT_FOUND",
  "message": "스터디 공지를 찾을 수 없습니다.",
  "data": null,
  "timestamp": "2026-07-22T20:40:00+09:00"
}
```

---

#### 409 Conflict — 이미 삭제된 공지

```json
{
  "success": false,
  "code": "STUDY_NOTICE_ALREADY_DELETED",
  "message": "이미 삭제된 공지입니다.",
  "data": null,
  "timestamp": "2026-07-22T20:40:00+09:00"
}
```

---

#### 409 Conflict — 삭제 불가 상태

```json
{
  "success": false,
  "code": "STUDY_NOTICE_DELETE_NOT_ALLOWED",
  "message": "현재 상태에서는 공지를 삭제할 수 없습니다.",
  "data": {
    "studyStatus": "CANCELED"
  },
  "timestamp": "2026-07-22T20:40:00+09:00"
}
```

---

#### 500 Internal Server Error

```json
{
  "success": false,
  "code": "COMMON_INTERNAL_SERVER_ERROR",
  "message": "일시적인 오류가 발생했습니다.",
  "data": null,
  "timestamp": "2026-07-22T20:40:00+09:00"
}
```

---

### 추가 프론트 처리 기준

```
공지 목록 또는 상세 화면에서 삭제 선택
→ 삭제 확인 팝업 표시
→ DELETE /api/v1/studies/{studyId}/notices/{noticeId} 호출

공지 삭제 성공
→ 해당 공지를 공지 목록에서 제거
→ 최근 공지였다면 스터디 홈 정보 다시 조회
→ "공지가 삭제되었습니다." 안내

STUDY_NOTICE_DELETE_FORBIDDEN 발생
→ "스터디장만 공지를 삭제할 수 있습니다." 안내

STUDY_NOTICE_ALREADY_DELETED 발생
→ 해당 공지를 목록에서 제거
→ 공지 목록 다시 조회

STUDY_NOTICE_NOT_FOUND 발생
→ "삭제되었거나 존재하지 않는 공지입니다." 안내
→ 공지 목록으로 이동
```

---

## S3 업로드 완료 처리

Method: POST
Progress: 완료
URI: /api/v1/media/{fileId}/complete
담당자: 김윤석
연동여부: No

클라이언트가 Presigned URL을 사용해 S3에 직접 업로드한 파일을 서버에서 검증하고 파일 메타데이터의 업로드 상태를 `COMPLETED`로 변경한다.

이 API가 성공한 파일만 현장 사진, STT 음성, 채팅 이미지 또는 게시글 첨부 파일로 연결할 수 있다.

---

### Request

- Request HTTP Method: `POST`
- 인증 필요: 필요
- Request Body: 없음

#### Request Header

```
Authorization: Bearer {accessToken}
```

#### Path Variable

```
POST /api/v1/media/89/complete
```

| 필드 | 타입 | 필수 | 설명 |
| --- | --- | --- | --- |
| `fileId` | Long | Y | 업로드 완료 처리할 `file_meta.id` |

---

### 처리 기준

#### 1. 회원 및 파일 확인

- 로그인한 회원 ID는 Access Token에서 확인한다.
- `fileId`에 해당하는 `file_meta`가 존재하는지 확인한다.
- 파일의 `owner_id`가 로그인 회원 ID와 일치해야 한다.
- 다른 회원이 발급받은 업로드 URL의 파일을 완료 처리할 수 없다.
- `deleted_at IS NULL`인 파일만 완료 처리할 수 있다.

#### 2. 업로드 상태 확인

파일 업로드 상태는 다음 값을 사용한다.

| 값 | 설명 |
| --- | --- |
| `PENDING` | Presigned URL이 발급됐지만 업로드 완료가 확정되지 않은 상태 |
| `COMPLETED` | S3 오브젝트 검증과 파일 메타 갱신이 완료된 상태 |
| `FAILED` | 업로드 오브젝트 검증에 실패한 상태 |
- `PENDING` 상태의 파일만 최초 완료 처리한다.
- 이미 `COMPLETED`인 파일에 같은 요청을 보내면 새 작업을 수행하지 않고 현재 파일 정보를 반환한다.
- 이미 `FAILED`인 파일은 자동으로 완료 상태로 되돌리지 않는다. 새 업로드 URL을 발급받아 다시 업로드하도록 처리한다.

#### 3. 임시 업로드 만료 확인

- `expires_at`이 존재하고 현재 시각보다 이전이면 완료 처리할 수 없다.
- 만료된 `PENDING` 파일은 `MEDIA_UPLOAD_EXPIRED`로 처리한다.
- `STT_AUDIO`처럼 임시 보관하는 파일은 완료 처리 후에도 `expires_at`을 유지한다.
- 영구 보관 파일은 `expires_at = null`일 수 있다.

#### 4. S3 오브젝트 존재 확인

- `file_meta.s3_key`에 해당하는 S3 오브젝트가 실제로 존재하는지 `HEAD Object` 등으로 확인한다.
- 오브젝트가 존재하지 않으면 `MEDIA_OBJECT_NOT_FOUND`를 반환한다.
- S3 접근 실패가 일시적 오류인지 실제 미존재인지 구분한다.
- 내부 S3 Bucket 이름과 Key는 일반 응답에 노출하지 않는다.

#### 5. 파일 크기 검증

- 실제 S3 오브젝트 크기와 발급 시 등록한 `size_bytes`를 비교한다.
- 크기가 일치하지 않거나 파일 사용 목적별 최대 크기를 초과하면 완료 처리하지 않는다.
- 검증에 성공하면 실제 오브젝트 크기를 `file_meta.size_bytes`에 저장하거나 기존 값과 일치하는지 확인한다.
- 음수 크기와 0바이트 파일은 허용하지 않는다.

#### 6. MIME 타입 검증

- 실제 오브젝트의 Content-Type과 `file_meta.content_type`을 확인한다.
- 확장자만으로 파일 유형을 신뢰하지 않는다.
- 허용되는 MIME 타입은 `fileUsage`별 정책을 따른다.

| `fileUsage` | 허용 예시 |
| --- | --- |
| `FIELD_PHOTO` | `image/jpeg`, `image/png`, `image/webp` |
| `STT_AUDIO` | `audio/m4a`, `audio/mp4`, `audio/mpeg`, `audio/wav`, `audio/webm` |
| `CHAT_IMAGE` | `image/jpeg`, `image/png`, `image/webp` |
| `POST_ATTACHMENT` | 프로젝트에서 허용한 이미지 형식 |
- 허용되지 않는 MIME 타입이면 `MEDIA_CONTENT_TYPE_INVALID` 오류를 반환한다.

#### 7. 파일 사용 목적과 스터디 연결 확인

- `FIELD_PHOTO`, `STT_AUDIO`, `CHAT_IMAGE`는 `study_id`가 존재해야 한다.
- 파일의 `study_id`가 업로드 URL 발급 당시의 대상 스터디와 일치해야 한다.
- 로그인 회원이 해당 스터디의 승인 멤버가 아닌 경우 완료 처리할 수 없다.
- `POST_ATTACHMENT`는 게시글 작성 전 임시 파일일 수 있으므로 `study_id = null`을 허용할 수 있다.

#### 8. 완료 처리

- 모든 검증에 성공하면 `file_meta.upload_status = COMPLETED`로 변경한다.
- `file_meta`에는 `updated_at` 컬럼이 없으므로 별도의 수정 시각을 저장하지 않는다.
- 완료 처리 시 `owner_id`, `study_id`, `file_usage`, `s3_key`, `created_at`은 변경하지 않는다.
- 응답의 `createdAt`은 파일 메타 최초 생성 시각이다.
- 파일 상태 변경 시각이 반드시 필요하다면 ERD에 `updated_at` 또는 `completed_at` 컬럼을 추가해야 하며, 현재 API 명세에서는 반환하지 않는다.

#### 9. 접근 URL

- 완료된 파일에 대해 필요하면 짧은 유효기간의 Presigned GET URL을 `accessUrl`로 반환한다.
- `accessUrl`은 영구 URL이 아니며 `accessUrlExpiresAt` 이후 사용할 수 없다.
- 모든 파일을 `imageUrl`로 표현하지 않는다. 사진과 음성을 공통으로 처리할 수 있도록 `accessUrl`을 사용한다.
- `STT_AUDIO`의 접근 URL은 STT 처리에 필요한 내부 흐름에서만 사용하고 일반 화면에 노출하지 않는 것을 권장한다.

#### 10. STT 음성 후속 처리

- `fileUsage = STT_AUDIO`인 파일은 완료 처리 후 STT 변환 요청에 사용할 수 있다.
- STT 성공 후 음성 원본은 삭제하고 `file_meta.deleted_at`을 기록한다.
- STT 실패 시 재처리 가능 기간 동안만 임시 보관한다.
- 원본이 삭제된 뒤에는 파일 접근 API에서 접근 URL을 반환하지 않는다.

#### 11. 멱등성 및 트랜잭션

- 이미 `COMPLETED`인 파일의 반복 요청은 `200 OK`로 처리한다.
- 반복 요청으로 파일 메타 행을 새로 생성하지 않는다.
- S3 오브젝트 검증과 DB 상태 변경 사이의 실패를 고려해 재시도 가능한 구조로 구현한다.
- 검증 실패 시 무조건 `FAILED`로 바꿀지, 일시적 S3 장애에서는 `PENDING`을 유지할지는 내부 오류 유형에 따라 구분한다.

---

### Response

#### 200 OK — 업로드 완료 처리

```
{
  "success": true,
  "code": "MEDIA_UPLOAD_COMPLETED",
  "message": "업로드가 완료 처리되었습니다.",
  "data": {
    "fileId": 89,
    "fileUsage": "FIELD_PHOTO",
    "originalName": "entrance.jpg",
    "contentType": "image/jpeg",
    "sizeBytes": 1048576,
    "uploadStatus": "COMPLETED",
    "accessUrl": "https://s3.example.com/presigned/field-photo-89",
    "accessUrlExpiresAt": "2026-07-25T11:45:00+09:00",
    "fileExpiresAt": null,
    "createdAt": "2026-07-25T11:30:00+09:00"
  },
  "timestamp": "2026-07-25T11:35:00+09:00"
}
```

#### 200 OK — 이미 완료된 파일

```
{
  "success": true,
  "code": "MEDIA_UPLOAD_ALREADY_COMPLETED",
  "message": "이미 업로드 완료 처리된 파일입니다.",
  "data": {
    "fileId": 89,
    "fileUsage": "FIELD_PHOTO",
    "originalName": "entrance.jpg",
    "contentType": "image/jpeg",
    "sizeBytes": 1048576,
    "uploadStatus": "COMPLETED",
    "accessUrl": "https://s3.example.com/presigned/field-photo-89-renewed",
    "accessUrlExpiresAt": "2026-07-25T11:46:00+09:00",
    "fileExpiresAt": null,
    "createdAt": "2026-07-25T11:30:00+09:00"
  },
  "timestamp": "2026-07-25T11:36:00+09:00"
}
```

이미 완료된 파일에서도 `accessUrl`은 새로 발급된 URL일 수 있다. 최초 완료 시각을 의미하는 필드는 현재 ERD에 없으므로 반환하지 않는다.

#### Response Field

| 필드 | 타입 | 설명 |
| --- | --- | --- |
| `fileId` | Long | 파일 메타 ID |
| `fileUsage` | String | 파일 사용 목적 |
| `originalName` | String | 원본 파일명 |
| `contentType` | String | 검증된 MIME 타입 |
| `sizeBytes` | Long | 검증된 파일 크기, Byte |
| `uploadStatus` | String | 업로드 상태, 완료 후 `COMPLETED` |
| `accessUrl` | String | 짧은 유효기간의 파일 접근 URL |
| `accessUrlExpiresAt` | String | 접근 URL 만료 시각 |
| `fileExpiresAt` | String/null | 임시 파일 자체의 만료 시각 |
| `createdAt` | String | 파일 메타 생성 시각 |

---

### Exception

#### 400 Bad Request — 잘못된 파일 ID

```
{
  "success": false,
  "code": "COMMON_INVALID_REQUEST",
  "message": "입력값을 확인해 주세요.",
  "data": {
    "field": "fileId",
    "reason": "파일 ID는 1 이상의 숫자여야 합니다."
  },
  "timestamp": "2026-07-25T11:35:00+09:00"
}
```

#### 400 Bad Request — 허용되지 않는 MIME 타입

```
{
  "success": false,
  "code": "MEDIA_CONTENT_TYPE_INVALID",
  "message": "지원하지 않는 파일 형식입니다.",
  "data": {
    "contentType": "application/x-msdownload",
    "fileUsage": "FIELD_PHOTO"
  },
  "timestamp": "2026-07-25T11:35:00+09:00"
}
```

#### 400 Bad Request — 파일 크기 불일치

```
{
  "success": false,
  "code": "MEDIA_SIZE_MISMATCH",
  "message": "업로드한 파일 크기가 요청 정보와 일치하지 않습니다.",
  "data": null,
  "timestamp": "2026-07-25T11:35:00+09:00"
}
```

#### 401 Unauthorized

```
{
  "success": false,
  "code": "AUTH_ACCESS_TOKEN_INVALID",
  "message": "로그인이 필요합니다.",
  "data": null,
  "timestamp": "2026-07-25T11:35:00+09:00"
}
```

#### 403 Forbidden — 파일 소유자가 아님

```
{
  "success": false,
  "code": "MEDIA_NOT_OWNER",
  "message": "해당 파일을 완료 처리할 권한이 없습니다.",
  "data": null,
  "timestamp": "2026-07-25T11:35:00+09:00"
}
```

#### 404 Not Found — 파일 메타 없음

```
{
  "success": false,
  "code": "MEDIA_FILE_NOT_FOUND",
  "message": "파일 정보를 찾을 수 없습니다.",
  "data": null,
  "timestamp": "2026-07-25T11:35:00+09:00"
}
```

#### 409 Conflict — S3 오브젝트 없음

```
{
  "success": false,
  "code": "MEDIA_OBJECT_NOT_FOUND",
  "message": "업로드된 파일을 확인할 수 없습니다.",
  "data": null,
  "timestamp": "2026-07-25T11:35:00+09:00"
}
```

#### 409 Conflict — 업로드 URL 만료

```
{
  "success": false,
  "code": "MEDIA_UPLOAD_EXPIRED",
  "message": "업로드 가능 시간이 만료되었습니다. 다시 업로드해 주세요.",
  "data": null,
  "timestamp": "2026-07-25T11:35:00+09:00"
}
```

#### 409 Conflict — 실패 상태 파일

```
{
  "success": false,
  "code": "MEDIA_UPLOAD_ALREADY_FAILED",
  "message": "실패 처리된 파일입니다. 새로운 업로드 URL을 발급받아 주세요.",
  "data": null,
  "timestamp": "2026-07-25T11:35:00+09:00"
}
```

#### 500 Internal Server Error

```
{
  "success": false,
  "code": "COMMON_INTERNAL_SERVER_ERROR",
  "message": "일시적인 오류가 발생했습니다.",
  "data": null,
  "timestamp": "2026-07-25T11:35:00+09:00"
}
```

---

### 프론트 처리

```
Presigned URL 발급 성공
→ 발급받은 uploadUrl로 S3 PUT 업로드
→ S3 업로드 성공 확인
→ POST /api/v1/media/{fileId}/complete 호출

완료 처리 성공
→ 응답의 fileId와 uploadStatus = COMPLETED 저장
→ 이후 현장 기록·STT·채팅·게시글 작성 API에 fileId 전달

MEDIA_UPLOAD_ALREADY_COMPLETED
→ 오류로 표시하지 않음
→ 응답 파일 정보를 정상 완료 상태로 반영

MEDIA_OBJECT_NOT_FOUND
→ S3 업로드 성공 여부 확인
→ 필요하면 Presigned URL을 다시 발급받아 재업로드

MEDIA_UPLOAD_EXPIRED 또는 MEDIA_UPLOAD_ALREADY_FAILED
→ 기존 fileId 재사용 중단
→ 새 Presigned URL 발급 API 호출

accessUrlExpiresAt 경과
→ 기존 URL을 영구 저장하거나 재사용하지 않음
→ 파일 표시가 필요하면 파일 접근 URL 조회 API 호출
```

---

## 임장 일정 삭제

Method: DELETE
Progress: 완료
URI: /api/v1/studies/{studyId}/schedule
담당자: 최태선
연동여부: Yes

일정을 물리 삭제하지 않고 `CANCELED` 상태로 변경한다.

스터디장이 등록된 임장 일정을 삭제한다.

일정이 삭제되면 승인된 참여자는 더 이상 기존 임장 일정을 확인할 수 없다.

### Request

- HTTP Method: `DELETE`
- URI: `/api/v1/studies/{studyId}/schedule`
- 인증 필요: 필요
- Request Body: 없음

#### Request Header

```
Authorization: Bearer {accessToken}
```

#### Path Variable

```
DELETE /api/v1/studies/10/schedule
```

| 필드 | 타입 | 필수 | 설명 |
| --- | --- | --- | --- |
| `studyId` | Long | Y | 임장 일정을 삭제할 스터디 ID |

---

### 처리 기준

- 스터디장만 삭제할 수 있다.
- `SCHEDULED` 일정만 취소할 수 있다.
- 일정 상태를 `CANCELED`로 변경한다.
- 기존 D-1 예약을 취소한다.
- 승인 멤버에게 일정 취소 알림과 SYSTEM 채팅 메시지를 생성한다.
- 조회 API는 취소된 일정 행을 `schedule=null`로 반환한다.
- 이후 일정 등록 API가 취소된 행을 새 일정으로 재활성화할 수 있다.
- 최종 ERD에 없는 `deletedAt`은 응답하지 않는다.

### 추가 상세 처리 기준

- 로그인한 회원 ID는 Access Token에서 확인한다.
- `studyId`에 해당하는 스터디와 등록된 임장 일정이 존재해야 한다.
- 해당 스터디의 스터디장만 일정을 삭제할 수 있다.
- 일반 참여자는 일정을 삭제할 수 없다.
- `RECRUITING`, `CLOSED` 상태에서만 일정을 삭제할 수 있다.
- `IN_PROGRESS`, `COMPLETED`, `CANCELED` 상태에서는 삭제할 수 없다.
- 일정 삭제 후 일정 조회 API는 `schedule = null`을 반환한다.
- 일정이 삭제돼도 스터디와 승인된 멤버 정보는 유지한다.
- 승인된 참여자에게 일정 취소 또는 삭제 알림을 생성할 수 있다.

---

### Response

#### 200 OK

```json
{
  "success": true,
  "code": "STUDY_SCHEDULE_DELETE_SUCCESS",
  "message": "임장 일정이 삭제되었습니다.",
  "data": {
    "studyId": 10,
    "scheduleId": 7,
    "status": "CANCELED",
    "updatedAt": "2026-07-24T19:10:00+09:00"
  },
  "timestamp": "2026-07-24T19:10:00+09:00"
}
```

### Exception

- `401 AUTH_ACCESS_TOKEN_INVALID`
- `403 STUDY_SCHEDULE_DELETE_FORBIDDEN`
- `404 STUDY_NOT_FOUND`
- `404 STUDY_SCHEDULE_NOT_FOUND`
- `409 STUDY_SCHEDULE_DELETE_NOT_ALLOWED`

### 추가 Exception 예시

#### 401 Unauthorized

```json
{
  "success": false,
  "code": "AUTH_ACCESS_TOKEN_INVALID",
  "message": "로그인이 필요합니다.",
  "data": null,
  "timestamp": "2026-07-22T21:20:00+09:00"
}
```

---

#### 403 Forbidden — 스터디장 아님

```json
{
  "success": false,
  "code": "STUDY_SCHEDULE_DELETE_FORBIDDEN",
  "message": "임장 일정을 삭제할 권한이 없습니다.",
  "data": null,
  "timestamp": "2026-07-22T21:20:00+09:00"
}
```

---

#### 404 Not Found — 스터디 없음

```json
{
  "success": false,
  "code": "STUDY_NOT_FOUND",
  "message": "스터디를 찾을 수 없습니다.",
  "data": null,
  "timestamp": "2026-07-22T21:20:00+09:00"
}
```

---

#### 404 Not Found — 일정 없음

```json
{
  "success": false,
  "code": "STUDY_SCHEDULE_NOT_FOUND",
  "message": "등록된 임장 일정을 찾을 수 없습니다.",
  "data": null,
  "timestamp": "2026-07-22T21:20:00+09:00"
}
```

---

#### 409 Conflict — 삭제 불가 상태

```json
{
  "success": false,
  "code": "STUDY_SCHEDULE_DELETE_NOT_ALLOWED",
  "message": "현재 상태에서는 임장 일정을 삭제할 수 없습니다.",
  "data": {
    "studyStatus": "IN_PROGRESS"
  },
  "timestamp": "2026-07-22T21:20:00+09:00"
}
```

---

#### 500 Internal Server Error

```json
{
  "success": false,
  "code": "COMMON_INTERNAL_SERVER_ERROR",
  "message": "일시적인 오류가 발생했습니다.",
  "data": null,
  "timestamp": "2026-07-22T21:20:00+09:00"
}
```

---

### 추가 프론트 처리 기준

```
임장 일정 화면에서 삭제 선택
→ 삭제 확인 팝업 표시
→ DELETE /api/v1/studies/{studyId}/schedule 호출

일정 삭제 성공
→ 일정 정보를 화면에서 제거
→ "등록된 임장 일정이 없습니다." 표시
→ 스터디장이면 일정 등록 버튼 표시

STUDY_SCHEDULE_DELETE_FORBIDDEN 발생
→ "스터디장만 일정을 삭제할 수 있습니다." 안내

STUDY_SCHEDULE_NOT_FOUND 발생
→ 일정이 없는 상태로 화면 갱신
```

---

## 내가 찜한 리포트 목록 조회

Method: GET
Progress: 완료
URI: /api/v1/members/me/favorite-reports
담당자: 박재명
연동여부: Yes

로그인한 회원이 찜한 AI 임장 리포트를 최근 찜한 순서로 조회한다.

리포트 찜 탭에서 카드 목록과 전체 개수를 표시하며, 현재 회원이 더 이상 접근할 수 없는 리포트는 결과에서 제외한다.

---

### Request

- Request HTTP Method: `GET`
- 인증 필요: 필요
- Request Body: 없음

#### Request Header

```
Authorization: Bearer {accessToken}
```

#### Query Parameter

```
GET /api/v1/members/me/favorite-reports?page=0&size=20
```

| 필드 | 타입 | 필수 | 기본값 | 설명 |
| --- | --- | --- | --- | --- |
| `page` | Integer | N | `0` | 페이지 번호, 0부터 시작 |
| `size` | Integer | N | `20` | 페이지당 조회 개수, 1~100 |

---

### 처리 기준

#### 1. 회원 확인

- 로그인한 회원 ID는 Access Token에서 확인한다.
- 회원 상태가 `ACTIVE`인 경우에만 조회할 수 있다.
- `report_favorite.member_id`가 로그인 회원 ID와 일치하는 관계만 조회한다.

#### 2. 조회 대상

- `report.status = DONE`인 리포트만 반환한다.
- `DONE` 상태 리포트는 로그인 회원이 조회할 수 있다.
- 삭제되었거나 `DONE` 상태가 아닌 리포트는 목록에서 제외한다.
- 존재하지 않거나 삭제된 리포트는 반환하지 않는다.

#### 3. 중복 및 정렬

- `report_favorite(member_id, report_id)` 유일 제약으로 같은 리포트의 중복 찜을 방지한다.
- 최근 찜한 순서인 `report_favorite.created_at DESC`, `report_favorite.id DESC`로 정렬한다.
- 각 카드의 `favoritedByMe`는 찜 목록이므로 항상 `true`다.
- 찜한 시각은 `favoritedAt`으로 반환한다.

#### 4. 페이지네이션

- `page`는 0부터 시작한다.
- `size`는 1 이상 100 이하로 제한한다.
- 결과가 없으면 오류가 아닌 빈 배열과 `totalElements = 0`을 반환한다.
- 페이지 정보는 `totalElements`, `page`, `size`, `totalPages`로 통일한다.

#### 5. 카드 정보

- 제목, 요약, 분석 태그와 아파트 정보를 반환한다.
- `analysisTags`와 `summary`는 `report.result_json`에서 목록 카드용으로 추출한다.
- 리포트 결과 일부가 없으면 해당 필드는 `null` 또는 빈 배열로 반환하되 목록 전체를 실패시키지 않는다.

---

### Response

#### 200 OK

```
{
  "success": true,
  "code": "MEMBER_FAVORITE_REPORT_LIST_SUCCESS",
  "message": "찜한 리포트 목록 조회에 성공했습니다.",
  "data": {
    "content": [
      {
        "reportId": 48,
        "title": "래미안 옥수 리버젠 임장 리포트",
        "summary": "교통 접근성은 좋고 단지 진입 경사는 주의가 필요합니다.",
        "analysisTags": [
          "교통 우수",
          "단지 경사"
        ],
        "apartment": {
          "apartmentId": 15,
          "name": "래미안 옥수 리버젠",
          "address": "서울특별시 성동구 매봉길 15"
        },
        "favoritedByMe": true,
        "completedAt": "2026-07-22T18:07:00+09:00",
        "favoritedAt": "2026-07-23T09:00:00+09:00"
      }
    ],
    "totalElements": 2,
    "page": 0,
    "size": 20,
    "totalPages": 1
  },
  "timestamp": "2026-07-25T10:40:00+09:00"
}
```

#### Response Field

| 필드 | 타입 | 설명 |
| --- | --- | --- |
| `content` | Array | 찜한 리포트 목록 |
| `content[].reportId` | Long | 리포트 ID |
| `content[].title` | String | 리포트 제목 |
| `content[].summary` | String | 목록 카드용 요약 |
| `content[].analysisTags` | Array | 리포트 핵심 분석 태그 |
| `content[].apartment` | Object | 대상 아파트 요약 정보 |
| `content[].favoritedByMe` | Boolean | 현재 회원의 찜 여부, 항상 `true` |
| `content[].completedAt` | String | 리포트 생성 완료 시각 |
| `content[].favoritedAt` | String | 현재 회원이 찜한 시각 |
| `totalElements` | Long | 접근 가능한 찜 리포트 전체 개수 |
| `page` | Integer | 현재 페이지 번호 |
| `size` | Integer | 페이지 크기 |
| `totalPages` | Integer | 전체 페이지 수 |

#### 200 OK — 찜한 리포트가 없는 경우

```
{
  "success": true,
  "code": "MEMBER_FAVORITE_REPORT_LIST_SUCCESS",
  "message": "찜한 리포트 목록 조회에 성공했습니다.",
  "data": {
    "content": [],
    "totalElements": 0,
    "page": 0,
    "size": 20,
    "totalPages": 0
  },
  "timestamp": "2026-07-25T10:40:00+09:00"
}
```

---

### Exception

#### 400 Bad Request — 페이지 번호 오류

```
{
  "success": false,
  "code": "COMMON_INVALID_REQUEST",
  "message": "입력값을 확인해 주세요.",
  "data": {
    "field": "page",
    "reason": "페이지 번호는 0 이상이어야 합니다."
  },
  "timestamp": "2026-07-25T10:40:00+09:00"
}
```

#### 400 Bad Request — 페이지 크기 오류

```
{
  "success": false,
  "code": "COMMON_INVALID_REQUEST",
  "message": "입력값을 확인해 주세요.",
  "data": {
    "field": "size",
    "reason": "페이지 크기는 1 이상 100 이하이어야 합니다."
  },
  "timestamp": "2026-07-25T10:40:00+09:00"
}
```

#### 401 Unauthorized

```
{
  "success": false,
  "code": "AUTH_ACCESS_TOKEN_INVALID",
  "message": "로그인이 필요합니다.",
  "data": null,
  "timestamp": "2026-07-25T10:40:00+09:00"
}
```

#### 500 Internal Server Error

```
{
  "success": false,
  "code": "COMMON_INTERNAL_SERVER_ERROR",
  "message": "일시적인 오류가 발생했습니다.",
  "data": null,
  "timestamp": "2026-07-25T10:40:00+09:00"
}
```

---

### 프론트 처리

```
마이페이지의 찜 탭 진입
→ "찜한 리포트" 탭 선택
→ GET /api/v1/members/me/favorite-reports?page=0&size=20 호출
→ content와 totalElements 표시

리포트 카드 선택
→ reportId를 이용해 리포트 상세 화면으로 이동

리포트 찜 해제 성공
→ 현재 목록에서 해당 카드 제거
→ totalElements를 1 감소
→ 현재 페이지가 비면 이전 페이지 또는 첫 페이지 재조회

content가 빈 배열
→ "찜한 리포트가 없습니다." 빈 상태 표시
```

---

## 리포트 찜 해제

Method: DELETE
Progress: 완료
URI: /api/v1/reports/{reportId}/favorite
담당자: 박재명
연동여부: Yes

로그인한 회원이 찜한 리포트를 찜 목록에서 해제한다.

이미 찜이 해제된 리포트에 동일한 요청을 반복해도 오류 없이 `favoritedByMe = false`인 현재 상태를 반환한다.

---

### Request

- Request HTTP Method: `DELETE`
- 인증 필요: 필요
- Request Body: 없음

#### Request Header

```
Authorization: Bearer {accessToken}
```

#### Path Variable

```
DELETE /api/v1/reports/48/favorite
```

| 필드 | 타입 | 필수 | 설명 |
| --- | --- | --- | --- |
| `reportId` | Long | Y | 찜을 해제할 리포트 ID |

---

### 처리 기준

#### 1. 회원·리포트 확인

- 로그인한 회원 ID는 Access Token에서 확인한다.
- 회원 상태가 `ACTIVE`인지 확인한다.
- `reportId`에 해당하는 리포트가 존재하는지 확인한다.
- 로그인 회원에게 기존 찜 관계가 있으면 해제 요청을 허용한다.
- 존재하지 않는 리포트와 삭제된 리포트는 `REPORT_NOT_FOUND`로 처리한다.

#### 2. 찜 관계 삭제

- `member_id + report_id`가 일치하는 `report_favorite` 행을 조회한다.
- 관계가 존재하면 물리적으로 삭제한다.
- 찜 관계는 별도 Soft Delete 이력이 필요하지 않은 관계 데이터로 처리한다.

```
DELETE FROM report_favorite
WHERE member_id = :memberId
  AND report_id = :reportId;
```

#### 3. 멱등성

- 이미 찜하지 않은 리포트에 해제 요청을 보내도 정상 응답한다.
- 동일 요청이 반복되어도 추가 삭제를 수행하지 않는다.
- 최종 응답은 항상 `favoritedByMe = false`다.
- 동시 요청으로 한 요청이 먼저 관계를 삭제한 경우 나머지 요청은 이미 해제된 상태로 처리한다.

#### 4. 찜 수와 목록 반영

- 관계 삭제 후 현재 전체 찜 수를 다시 계산해 `favoriteCount`로 반환한다.
- 찜 수는 0 미만이 될 수 없다.
- 내 찜한 리포트 목록에서 호출한 경우 성공 후 해당 항목을 목록에서 제거한다.
- 찜 해제는 자동 게시글과 리포트 내용에 영향을 주지 않는다.

---

### Response

#### 200 OK — 찜 해제 완료

```
{
  "success": true,
  "code": "REPORT_UNFAVORITE_SUCCESS",
  "message": "리포트 찜을 해제했습니다.",
  "data": {
    "reportId": 48,
    "favoritedByMe": false,
    "favoriteCount": 17,
    "unfavoritedAt": "2026-07-25T15:45:00+09:00"
  },
  "timestamp": "2026-07-25T15:45:00+09:00"
}
```

#### 200 OK — 이미 찜하지 않은 경우

```
{
  "success": true,
  "code": "REPORT_ALREADY_UNFAVORITED",
  "message": "이미 찜하지 않은 리포트입니다.",
  "data": {
    "reportId": 48,
    "favoritedByMe": false,
    "favoriteCount": 17,
    "unfavoritedAt": "2026-07-25T15:45:00+09:00"
  },
  "timestamp": "2026-07-25T15:45:00+09:00"
}
```

`unfavoritedAt`은 별도 이력 컬럼이 아니라 현재 해제 요청 처리 시각이다.

---

### Response Field

| 필드 | 타입 | 설명 |
| --- | --- | --- |
| `reportId` | Long | 찜을 해제한 리포트 ID |
| `favoritedByMe` | Boolean | 현재 회원의 찜 여부, 항상 `false` |
| `favoriteCount` | Long | 해제 처리 후 전체 찜 수 |
| `unfavoritedAt` | String | 요청 처리 시각 |

---

### Exception

#### 400 Bad Request — 잘못된 리포트 ID

```
{
  "success": false,
  "code": "COMMON_INVALID_REQUEST",
  "message": "입력값을 확인해 주세요.",
  "data": {
    "field": "reportId",
    "reason": "리포트 ID는 1 이상의 숫자여야 합니다."
  },
  "timestamp": "2026-07-25T15:45:00+09:00"
}
```

---

#### 401 Unauthorized

```
{
  "success": false,
  "code": "AUTH_ACCESS_TOKEN_INVALID",
  "message": "로그인이 필요합니다.",
  "data": null,
  "timestamp": "2026-07-25T15:45:00+09:00"
}
```

---

#### 404 Not Found — 리포트 없음

```
{
  "success": false,
  "code": "REPORT_NOT_FOUND",
  "message": "리포트를 찾을 수 없습니다.",
  "data": null,
  "timestamp": "2026-07-25T15:45:00+09:00"
}
```

---

#### 500 Internal Server Error

```
{
  "success": false,
  "code": "COMMON_INTERNAL_SERVER_ERROR",
  "message": "일시적인 오류가 발생했습니다.",
  "data": null,
  "timestamp": "2026-07-25T15:45:00+09:00"
}
```

---

### 프론트 처리

```
활성화된 리포트 찜 버튼 선택
→ DELETE /api/v1/reports/{reportId}/favorite 호출
→ favoritedByMe = false로 변경
→ favoriteCount 갱신

내 찜한 리포트 목록에서 해제
→ 성공 시 해당 리포트를 현재 목록에서 제거
→ 탭의 전체 찜 개수 감소

REPORT_ALREADY_UNFAVORITED
→ 오류 메시지를 표시하지 않음
→ 화면 찜 상태를 비활성으로 동기화
```

---

## 리포트 찜

Method: PUT
Progress: 완료
URI: /api/v1/reports/{reportId}/favorite
담당자: 박재명
연동여부: No

로그인한 회원이 조회 가능한 AI 임장 리포트를 찜 목록에 추가한다.

이미 찜한 리포트에 동일한 요청을 반복해도 중복 데이터는 생성하지 않고 현재 찜 상태를 반환한다.

---

### Request

- Request HTTP Method: `PUT`
- 인증 필요: 필요
- Request Body: 없음

#### Request Header

```
Authorization: Bearer {accessToken}
```

#### Path Variable

```
PUT /api/v1/reports/48/favorite
```

| 필드 | 타입 | 필수 | 설명 |
| --- | --- | --- | --- |
| `reportId` | Long | Y | 찜할 리포트 ID |

---

### 처리 기준

#### 1. 회원·리포트 확인

- 로그인한 회원 ID는 Access Token에서 확인한다.
- 회원 상태가 `ACTIVE`인지 확인한다.
- `reportId`에 해당하는 리포트가 존재하는지 확인한다.
- `status = DONE`인 리포트만 찜할 수 있다.
- `DONE` 상태 리포트는 모든 로그인 회원이 찜할 수 있다.
- 접근 권한을 잃은 리포트는 새로 찜할 수 없다.

#### 2. 찜 등록

- `report_favorite`에 회원 ID와 리포트 ID를 저장한다.
- 한 회원은 동일한 리포트를 한 번만 찜할 수 있다.
- DB의 회원·리포트 복합 Unique 제약으로 중복 생성을 방지한다.
- 생성 시각을 `favoritedAt`으로 반환한다.

```
INSERT INTO report_favorite (member_id, report_id, created_at)
VALUES (:memberId, :reportId, NOW());
```

#### 3. 멱등성

- 이미 찜한 리포트에 다시 요청해도 오류로 처리하지 않는다.
- 기존 `report_favorite` 행을 유지하고 새 행을 생성하지 않는다.
- 동시 요청이 발생해 Unique 제약 충돌이 발생한 경우 기존 찜 정보를 조회해 정상 상태로 반환한다.
- 최종 응답은 항상 `favoritedByMe = true`다.

#### 4. 찜 수

- 응답의 `favoriteCount`는 현재 유효한 리포트 찜 관계 수를 집계한 값이다.
- 리포트 자체에 카운터 컬럼이 없으므로 집계 쿼리 또는 캐시를 사용할 수 있다.
- 중복 요청으로 찜 수가 증가해서는 안 된다.

#### 5. 목록 반영

- 찜한 리포트는 다음 API에서 조회한다.

```
GET /api/v1/members/me/favorite-reports
```

- 리포트가 삭제되거나 `DONE` 상태가 아니면 찜 관계가 남아 있어도 목록에서 제외한다.
- 찜 등록만으로 리포트 내용이나 커뮤니티 게시글 상태는 변경하지 않는다.

---

### Response

#### 200 OK — 찜 등록 완료

```
{
  "success": true,
  "code": "REPORT_FAVORITE_SUCCESS",
  "message": "리포트를 찜했습니다.",
  "data": {
    "reportId": 48,
    "favoritedByMe": true,
    "favoriteCount": 18,
    "favoritedAt": "2026-07-25T15:40:00+09:00"
  },
  "timestamp": "2026-07-25T15:40:00+09:00"
}
```

#### 200 OK — 이미 찜한 경우

```
{
  "success": true,
  "code": "REPORT_FAVORITE_ALREADY_EXISTS",
  "message": "이미 찜한 리포트입니다.",
  "data": {
    "reportId": 48,
    "favoritedByMe": true,
    "favoriteCount": 18,
    "favoritedAt": "2026-07-24T09:00:00+09:00"
  },
  "timestamp": "2026-07-25T15:40:00+09:00"
}
```

---

### Response Field

| 필드 | 타입 | 설명 |
| --- | --- | --- |
| `reportId` | Long | 찜한 리포트 ID |
| `favoritedByMe` | Boolean | 현재 회원의 찜 여부, 항상 `true` |
| `favoriteCount` | Long | 현재 전체 찜 수 |
| `favoritedAt` | String | 최초 찜 등록 시각 |

---

### Exception

#### 400 Bad Request — 잘못된 리포트 ID

```
{
  "success": false,
  "code": "COMMON_INVALID_REQUEST",
  "message": "입력값을 확인해 주세요.",
  "data": {
    "field": "reportId",
    "reason": "리포트 ID는 1 이상의 숫자여야 합니다."
  },
  "timestamp": "2026-07-25T15:40:00+09:00"
}
```

---

#### 401 Unauthorized

```
{
  "success": false,
  "code": "AUTH_ACCESS_TOKEN_INVALID",
  "message": "로그인이 필요합니다.",
  "data": null,
  "timestamp": "2026-07-25T15:40:00+09:00"
}
```

---

#### 403 Forbidden — 리포트 접근 권한 없음

```
{
  "success": false,
  "code": "REPORT_FAVORITE_ACCESS_DENIED",
  "message": "해당 리포트를 찜할 권한이 없습니다.",
  "data": null,
  "timestamp": "2026-07-25T15:40:00+09:00"
}
```

---

#### 404 Not Found — 리포트 없음

```
{
  "success": false,
  "code": "REPORT_NOT_FOUND",
  "message": "리포트를 찾을 수 없습니다.",
  "data": null,
  "timestamp": "2026-07-25T15:40:00+09:00"
}
```

---

#### 409 Conflict — 생성 미완료

```
{
  "success": false,
  "code": "REPORT_FAVORITE_NOT_ALLOWED",
  "message": "생성이 완료된 리포트만 찜할 수 있습니다.",
  "data": {
    "status": "IN_PROGRESS"
  },
  "timestamp": "2026-07-25T15:40:00+09:00"
}
```

---

#### 500 Internal Server Error

```
{
  "success": false,
  "code": "COMMON_INTERNAL_SERVER_ERROR",
  "message": "일시적인 오류가 발생했습니다.",
  "data": null,
  "timestamp": "2026-07-25T15:40:00+09:00"
}
```

---

### 프론트 처리

```
리포트 상세 또는 리포트 카드에서 빈 찜 버튼 선택
→ PUT /api/v1/reports/{reportId}/favorite 호출
→ 성공 응답의 favoritedByMe와 favoriteCount로 UI 동기화

REPORT_FAVORITE_ALREADY_EXISTS
→ 오류 메시지를 표시하지 않음
→ 찜 활성 상태 유지

찜 등록 성공
→ 마이페이지 찜한 리포트 탭 개수 갱신
→ 필요하면 현재 목록에 리포트 추가
```

---

## 현재 기기 FCM 테스트 알림 예약

Method: POST
Progress: 완료
URI: `/api/v1/members/me/devices/{deviceId}/test-fcm-push`

Bearer Access Token으로 인증한 활성 회원이 본인 현재 기기의 테스트 알림을 예약한다. 요청
본문은 없으며, 클라이언트는 호출 전에 Android 알림 권한을 확인하고 현재 FCM 토큰 등록 PUT을
완료해야 한다.

서버는 FCM 설정과 현재 기기 토큰을 Firebase dry-run으로 검증한 뒤 HTTP 스레드를 대기시키지
않고 약 5초 뒤 실행할 작업을 등록한다. 실행 시점에 같은 회원·기기의 최신 토큰을 다시
조회하므로 그 사이 토큰이
갱신되면 새 토큰으로 전송하고, 삭제되면 발송을 생략한다. 응답과 로그에는 FCM 토큰, 서비스
계정, provider 원문 오류를 포함하지 않는다.

#### 202 Accepted

```json
{
  "success": true,
  "code": "MEMBER_FCM_TEST_PUSH_SCHEDULED",
  "message": "현재 기기의 FCM 테스트 알림을 예약했습니다.",
  "data": {
    "deviceId": "550e8400-e29b-41d4-a716-446655440000",
    "scheduledAt": "2026-07-29T16:30:05+09:00"
  },
  "timestamp": "2026-07-29T16:30:00+09:00"
}
```

예외는 `400 MEMBER_DEVICE_ID_INVALID`, `401 AUTH_ACCESS_TOKEN_INVALID`,
`404 MEMBER_NOT_FOUND`, `404 MEMBER_FCM_TOKEN_NOT_FOUND`,
`502 FCM_PUSH_DELIVERY_FAILED`, `503 FCM_PUSH_NOT_CONFIGURED`,
`503 FCM_PUSH_SCHEDULING_FAILED`를 사용한다. 예약 이후의 비동기 FCM 제공자 실패는 이 API
응답을 바꾸지 않고 민감정보가 없는 구조화 로그로 관측한다.

## 임장 추천 경로 생성

Method: `POST`<br>
Progress: 완료
URI: `/api/v1/studies/{studyId}/field-visit/route`

Bearer Access Token으로 인증한 진행 중인 임장 참여자가 세션 전체 체크리스트를 기준으로
공유 추천 경로를 생성한다. 요청 본문은 없다. 세션당 하나만 생성되며, 이미 생성됐거나 동시
요청으로 먼저 생성된 경우에는 기존 경로를 반환한다.

- 체크리스트가 하나도 없으면 생성하지 않고 `409 ROUTE_CHECKLIST_REQUIRED`를 반환한다.
- 카카오 주변 시설 조회 결과로 확정된 경유지가 2개 미만이면 생성하지 않고
  `409 ROUTE_NOT_APPLICABLE`를 반환한다.
- 아파트에서 마지막 경유지까지 복귀 구간이 없는 편도 순서를 정한 뒤, 카카오 보행 API를
  `route_mode=SHORTEST`로 정확히 한 번 호출해 실제 leg 거리·시간과 선형 경로를 확정한다.
- 응답의 `myItems`, `myProgress`는 로그인 회원 기준이고, 다른 참여자의 항목은
  `sharedItemCount`에만 반영된다.
- 경유지와 연결되지 않은 항목 및 제외 사유는 응답에 포함하지 않는다.

#### 201 Created — 신규 생성

```json
{
  "success": true,
  "code": "ROUTE_GENERATE_SUCCESS",
  "message": "추천 경로를 생성했습니다.",
  "data": {
    "studyId": 7,
    "sessionId": 100,
    "routeId": 21,
    "generatedAt": "2026-08-02T10:12:00+09:00",
    "totalDistanceMeters": 2180,
    "estimatedDurationMinutes": 54,
    "origin": {
      "apartmentId": 3012,
      "name": "래미안 원베일리",
      "latitude": 37.5013,
      "longitude": 127.0122
    },
    "geometry": {
      "type": "LineString",
      "coordinates": [
        [127.0122, 37.5013],
        [127.0141, 37.5031],
        [127.0169, 37.5047]
      ]
    },
    "myProgress": {
      "completedWaypointCount": 0,
      "totalWaypointCount": 3,
      "completedItemCount": 0,
      "totalItemCount": 2
    },
    "waypoints": [
      {
        "waypointId": 301,
        "sequence": 1,
        "facilityType": "SUBWAY_STATION",
        "name": "신반포역",
        "address": "서울 서초구 신반포로 지하 200",
        "latitude": 37.5047,
        "longitude": 127.0169,
        "kakaoPlaceId": "21160810",
        "distanceFromOriginM": 620,
        "distanceFromPrevM": 620,
        "walkMinutesFromPrev": 10,
        "stayMinutes": 8,
        "guide": "단지에서 승강장까지 실제로 걸어 시간을 재 보세요.",
        "myItems": [
          {
            "checklistItemId": 501,
            "category": "교통",
            "title": "대중교통 접근성",
            "subtitle": "실제 도보 시간을 확인하세요.",
            "isCompleted": false,
            "recordCount": 0
          }
        ],
        "myItemCount": 1,
        "sharedItemCount": 3
      }
    ]
  },
  "timestamp": "2026-08-02T10:12:00+09:00"
}
```

#### 200 OK — 기존 경로

동일한 `data` 형식으로 `ROUTE_ALREADY_EXISTS`를 반환한다. 클라이언트는 이를 오류로
처리하지 않고 받은 경로를 표시한다.

| 필드 | 설명 |
| --- | --- |
| `origin` | 아파트 출발 지점이다. 마지막 경유지에서 아파트로 복귀하는 구간은 없다. `waypoints`에는 포함되지 않는다. |
| `geometry` | 카카오 보행 경로의 GeoJSON `LineString`이다. 좌표 순서는 `[longitude, latitude]`다. |
| `totalDistanceMeters` | 카카오 보행 API가 반환한 아파트→마지막 경유지 편도 총거리다. |
| `estimatedDurationMinutes` | 카카오 편도 총 보행 시간(초를 분으로 올림)과 모든 경유지 체류 시간의 합이다. |
| `myProgress.completedWaypointCount` | 내 연결 항목이 모두 완료된 경유지 수다. 내 항목이 없는 공유 경유지는 확인할 항목이 없으므로 완료로 센다. |
| `myProgress.totalWaypointCount` | 공유 경로의 전체 경유지 수다. |
| `myProgress.totalItemCount` | 경로에 포함된 로그인 회원의 항목 수다. 내 체크리스트 전체 수가 아니다. |
| `waypoints[].facilityType` | 12종 고정 시설 enum이다. |
| `waypoints[].myItems` | 로그인 회원의 연결된 체크리스트 항목만 포함하며 비어 있을 수 있다. |
| `waypoints[].sharedItemCount` | 다른 참여자를 포함한 해당 경유지의 전체 연결 항목 수다. |
| `waypoints[].distanceFromOriginM` | 후보 선정용 아파트 직선거리 메타데이터다. |
| `waypoints[].distanceFromPrevM` | 카카오 보행 API가 반환한 해당 leg의 실제 거리다. |
| `waypoints[].walkMinutesFromPrev` | 카카오 보행 API의 해당 leg 시간을 분으로 올림한 값이다. |

#### 예외

| HTTP | code | 상황 |
| --- | --- | --- |
| 401 | `AUTH_ACCESS_TOKEN_INVALID` / `AUTH_ACCESS_TOKEN_EXPIRED` | 인증 실패 |
| 403 | `ROUTE_GENERATE_FORBIDDEN` | 활성 스터디 멤버 또는 진행 중 참여자가 아님 |
| 404 | `STUDY_NOT_FOUND` | 스터디 없음 |
| 404 | `FIELD_VISIT_NOT_STARTED` | 임장 세션 없음 |
| 409 | `FIELD_VISIT_ALREADY_ENDED` / `FIELD_PARTICIPANT_ALREADY_ENDED` | 세션 또는 참여 종료 |
| 409 | `ROUTE_CHECKLIST_REQUIRED` | 세션 체크리스트 없음 |
| 409 | `ROUTE_NOT_APPLICABLE` | 확정 경유지 2개 미만 |
| 503 | `ROUTE_POI_UNAVAILABLE` | 카카오 주변 시설 조회 실패·타임아웃·유효하지 않은 응답 |
| 503 | `ROUTE_WALKING_UNAVAILABLE` | 카카오 보행 경로 조회 실패·타임아웃·유효하지 않은 응답. 기존 데이터나 직선거리로 대체하지 않음 |

## 임장 추천 경로 조회

Method: `GET`<br>
Progress: 완료
URI: `/api/v1/studies/{studyId}/field-visit/route`

Bearer Access Token으로 인증한 활성 스터디 멤버가 공유 경로를 조회한다. 생성되지 않았거나
임장 세션이 아직 시작되지 않은 경우에도 성공 응답으로 `route: null`을 반환한다. 종료된
세션은 `readOnly: true`로 조회만 가능하다.

#### 200 OK — 미생성 경로

```json
{
  "success": true,
  "code": "ROUTE_DETAIL_SUCCESS",
  "message": "추천 경로 조회에 성공했습니다.",
  "data": {
    "studyId": 7,
    "readOnly": false,
    "route": null
  },
  "timestamp": "2026-08-02T10:12:00+09:00"
}
```

생성된 경우 `route`에는 생성 API의 `sessionId`부터 `waypoints`까지 동일한 경로 필드가
포함된다. `myItems`와 `myProgress`는 요청 시점의 완료 상태·기록 수로 다시 계산된다.
V16에서 생성되어 `geometry`가 없는 기존 경로는 최초 생성·조회 응답 전에 현재 체크리스트와
POI 필터부터 다시 적용한다. 동물병원을 제외하고 80m 이내 시설을 포함한 waypoint·연결·보행
경로를 한 트랜잭션으로 교체하며, 외부 API 또는 저장 실패 시 기존 값을 보존한다.

조회 예외는 인증 실패 `401`, 활성 스터디 멤버가 아닌 경우
`403 FIELD_VISIT_ACCESS_DENIED`, 스터디가 없는 경우 `404 STUDY_NOT_FOUND`, 재선정한 경유지가
2개 미만인 경우 `409 ROUTE_NOT_APPLICABLE`, 기존 경로 재계산 중 카카오 POI/보행 API를 사용할
수 없는 경우 각각 `503 ROUTE_POI_UNAVAILABLE`, `503 ROUTE_WALKING_UNAVAILABLE`이다.

## 같은 스터디 멤버 익명 태그·좋아요 평가 등록

Method: POST
Progress: 진행 중
URI: /api/v1/studies/{studyId}/members/{memberId}/reviews
담당자: 박재명
연동여부: Yes

같은 스터디의 현재 활성 멤버가 다른 활성 멤버에게 태그와 좋아요, 선택 리뷰를 익명으로 한 번 등록한다. 태그를 하나 이상 선택하거나 좋아요를 남겨야 한다.

### Request

- 인증 필요: 필요
- Content-Type: `application/json`

```text
POST /api/v1/studies/31/members/12/reviews
Authorization: Bearer {accessToken}
```

```json
{
  "tags": ["PUNCTUAL", "GOOD_RECORDS", "SHARES_INFO"],
  "liked": true,
  "content": "약속 시간을 잘 지키고 임장 내용을 꼼꼼히 공유해 주셨어요."
}
```

| 필드 | 타입 | 필수 | 제한 | 설명 |
| --- | --- | --- | --- | --- |
| `tags` | String[] | N | 최대 15개, 중복 불가 | 평가 태그 코드 목록. 각 원소는 유효한 `ReviewTag` 코드여야 한다 |
| `liked` | Boolean | N | 기본 `false` | 좋아요(하트) 신호 |
| `content` | String | N | 최대 500자 | 선택 리뷰. 공백 문자열은 저장하지 않고 `null`로 처리 |

- `tags`가 비어 있지 않거나 `liked=true` 중 최소 하나의 신호가 필요하다. 둘 다 없으면 `400`이다.
- 태그 코드는 애플리케이션 `ReviewTag` enum으로 검증하며 카탈로그 조회 API는 없다. 프론트는 동일한 코드 집합을 미러링한다.

#### ReviewTag 코드

| code | emoji | label | category |
| --- | --- | --- | --- |
| `PUNCTUAL` | ⏰ | 시간 약속을 잘 지켜요 | `PERSON` |
| `GOOD_MANNERS` | 🙌 | 매너가 좋아요 | `PERSON` |
| `EASY_COMMUNICATION` | 💬 | 소통이 편해요 | `PERSON` |
| `CONSIDERATE` | 💚 | 배려심이 깊어요 | `PERSON` |
| `WANT_AGAIN` | 🤝 | 또 함께하고 싶어요 | `PERSON` |
| `LEADS_MOOD` | 😊 | 분위기를 잘 이끌어요 | `PERSON` |
| `THOROUGH` | 🔍 | 꼼꼼하게 살펴봐요 | `VISIT` |
| `LEADS_VISIT` | 🧭 | 임장을 잘 리드해요 | `VISIT` |
| `WELL_PREPARED` | 📝 | 준비를 잘해와요 | `VISIT` |
| `SHARP_ANALYSIS` | 🧠 | 분석이 날카로워요 | `VISIT` |
| `GOOD_RECORDS` | 📸 | 사진·기록을 잘 남겨요 | `VISIT` |
| `SHARES_INFO` | 📣 | 정보 공유를 잘해요 | `VISIT` |
| `DILIGENT` | ✅ | 성실하게 참여해요 | `VISIT` |
| `GOOD_QUESTIONS` | ❓ | 질문이 좋아요 | `VISIT` |
| `ENERGETIC` | ⚡ | 에너지가 넘쳐요 | `VISIT` |

category 코드: `PERSON`(사람·협업), `VISIT`(임장·활동).

### 처리 기준

- 로그인 회원과 평가 대상 모두 해당 `studyId`의 `ACTIVE` 스터디 멤버여야 한다.
- 본인은 평가할 수 없다.
- 취소되거나 삭제된 스터디에서는 평가할 수 없다.
- 태그·좋아요 신호가 하나도 없으면 등록하지 않는다.
- `tags`에 유효하지 않은 코드나 중복 코드가 있으면 `400`이다.
- 동일한 `studyId + reviewerId + revieweeId` 조합은 한 번만 저장한다.
- 중복 여부는 서비스 검증과 DB unique constraint를 함께 사용하여 동시 요청에도 보장한다.
- 태그는 리뷰 저장과 같은 트랜잭션에서 `member_review_tag`에 저장한다.
- 프론트는 스터디 멤버 목록의 `reviewedByMe=true` 대상을 선택할 수 없게 표시한다.
- 등록 성공 후 멤버 목록과 내 스터디 목록의 남은 평가 인원을 다시 조회한다.
- 작성자 ID는 관리자 감사와 중복 검증을 위해 DB에 보존하지만 일반 API 응답에는 노출하지 않는다.
- 이 API는 등록만 담당한다. 조회·수정·삭제·관리자 감사 API는 별도 범위다.

### Response

#### 201 Created

```json
{
  "success": true,
  "code": "MEMBER_REVIEW_CREATE_SUCCESS",
  "message": "스터디 멤버 평가가 등록되었습니다.",
  "data": {
    "reviewId": 120,
    "studyId": 31,
    "reviewedMemberId": 12,
    "tags": [
      { "code": "PUNCTUAL", "label": "시간 약속을 잘 지켜요", "emoji": "⏰" },
      { "code": "GOOD_RECORDS", "label": "사진·기록을 잘 남겨요", "emoji": "📸" },
      { "code": "SHARES_INFO", "label": "정보 공유를 잘해요", "emoji": "📣" }
    ],
    "liked": true,
    "content": "약속 시간을 잘 지키고 임장 내용을 꼼꼼히 공유해 주셨어요.",
    "createdAt": "2026-08-03T22:10:00+09:00"
  },
  "timestamp": "2026-08-03T22:10:00+09:00"
}
```

응답에는 `reviewerId`, 작성자 닉네임 등 작성자를 식별할 수 있는 필드를 포함하지 않는다.

### Exception

- `400 COMMON_INVALID_REQUEST`: 태그 15개 초과, 리뷰 500자 초과 또는 잘못된 경로 ID
- `400 MEMBER_REVIEW_SIGNAL_REQUIRED`: 태그와 좋아요가 모두 없는 경우
- `400 MEMBER_REVIEW_TAG_INVALID`: 유효하지 않거나 중복된 태그 코드
- `400 MEMBER_REVIEW_SELF_NOT_ALLOWED`: 본인을 평가한 경우
- `401 AUTH_ACCESS_TOKEN_INVALID`: Access Token이 없거나 유효하지 않은 경우
- `403 MEMBER_REVIEW_CREATE_FORBIDDEN`: 로그인 회원이 해당 스터디의 활성 멤버가 아닌 경우
- `404 MEMBER_NOT_FOUND`: 로그인 회원을 찾을 수 없거나 탈퇴한 경우
- `404 STUDY_NOT_FOUND`: 삭제되었거나 존재하지 않는 스터디
- `404 MEMBER_REVIEW_TARGET_NOT_FOUND`: 평가 대상이 해당 스터디의 활성 멤버가 아니거나 탈퇴한 경우
- `409 MEMBER_REVIEW_STUDY_CANCELED`: 취소된 스터디
- `409 MEMBER_REVIEW_ALREADY_EXISTS`: 같은 스터디에서 동일 회원을 이미 평가한 경우
- `500 COMMON_INTERNAL_SERVER_ERROR`: 서버 내부 오류

---

## 사용자 익명 태그·좋아요 평가 목록 조회

Method: GET
Progress: 진행 중
URI: /api/v1/members/{memberId}/reviews
담당자: 박재명
연동여부: No

로그인 회원이 활성 사용자가 받은 태그 집계(topTags), 받은 좋아요 수, 평가 수와 익명 리뷰 목록을 최근 작성 순서로 조회한다. 마이페이지는 내 `memberId`, 타인 프로필은 조회 대상 `memberId`로 같은 API를 사용한다.

### Request

- 인증 필요: 필요
- Request Body: 없음

```text
GET /api/v1/members/12/reviews?cursor={opaqueCursor}&size=20
Authorization: Bearer {accessToken}
```

| 필드 | 위치 | 타입 | 필수 | 기본값 | 제한 | 설명 |
| --- | --- | --- | --- | --- | --- | --- |
| `memberId` | Path | Long | Y | - | 1 이상 | 평가를 받은 회원 ID |
| `cursor` | Query | String | N | - | 서버 발급값 | 이전 응답의 불투명 커서 |
| `size` | Query | Integer | N | `20` | 1~100 | 조회 개수 |

### 처리 기준

- 로그인 회원과 조회 대상 회원은 모두 `ACTIVE` 상태여야 한다.
- 대상 회원이 없거나 탈퇴 또는 Soft Delete된 경우 `MEMBER_NOT_FOUND`를 반환한다.
- `reviewCount`, `likeReceivedCount`, `topTags`는 대상 회원이 받은 전체 `member_review`를 DB에서 집계한다.
- `likeReceivedCount`는 `liked=true`인 평가 수다.
- `topTags`는 받은 전체 태그를 `tag_code`별로 집계해 `count` 내림차순, 동점이면 `code` 오름차순으로 정렬한다. `label`/`emoji`/`category`는 서버가 `ReviewTag`로 보강한다.
- 받은 평가가 없으면 `topTags=[]`, `likeReceivedCount=0`, `reviewCount=0`을 반환한다.
- 목록은 `createdAt DESC, reviewId DESC`로 정렬한다.
- 각 항목의 `tags`는 현재 페이지 리뷰들의 태그를 일괄 조회해 매핑한다(N+1 회피).
- 커서는 조회 대상 회원, 마지막 `createdAt`, 마지막 `reviewId`를 포함한 서명된 불투명 문자열이다.
- 목록과 응답에는 `reviewerId`, 작성자 닉네임·프로필 이미지, `studyId`를 포함하지 않는다.
- 평가 작성자가 이후 탈퇴하더라도 익명 평가 기록은 유지한다.
- 리뷰 작성 당시 유효했던 스터디가 이후 완료·취소되더라도 이미 생성된 평가를 소급 삭제하지 않는다.

### Response

#### 200 OK

```json
{
  "success": true,
  "code": "MEMBER_REVIEW_LIST_SUCCESS",
  "message": "사용자 평가 목록 조회에 성공했습니다.",
  "data": {
    "summary": {
      "topTags": [
        { "code": "PUNCTUAL", "label": "시간 약속을 잘 지켜요", "emoji": "⏰", "category": "PERSON", "count": 8 },
        { "code": "SHARES_INFO", "label": "정보 공유를 잘해요", "emoji": "📣", "category": "VISIT", "count": 5 }
      ],
      "likeReceivedCount": 9,
      "reviewCount": 12
    },
    "content": [
      {
        "reviewId": 120,
        "tags": [
          { "code": "PUNCTUAL", "label": "시간 약속을 잘 지켜요", "emoji": "⏰" },
          { "code": "GOOD_RECORDS", "label": "사진·기록을 잘 남겨요", "emoji": "📸" }
        ],
        "liked": true,
        "content": "약속 시간을 잘 지키고 임장 내용을 꼼꼼히 공유해 주셨어요.",
        "createdAt": "2026-08-03T22:10:00+09:00"
      }
    ],
    "nextCursor": "eyJ2IjoxLCJtZW1iZXJJZCI6MTJ9.signature",
    "hasNext": true
  },
  "timestamp": "2026-08-03T22:15:00+09:00"
}
```

#### 200 OK — 평가 없음

```json
{
  "success": true,
  "code": "MEMBER_REVIEW_LIST_SUCCESS",
  "message": "사용자 평가 목록 조회에 성공했습니다.",
  "data": {
    "summary": {
      "topTags": [],
      "likeReceivedCount": 0,
      "reviewCount": 0
    },
    "content": [],
    "nextCursor": null,
    "hasNext": false
  },
  "timestamp": "2026-08-03T22:15:00+09:00"
}
```

### Exception

- `400 COMMON_INVALID_REQUEST`: 잘못된 회원 ID 또는 `size`
- `400 COMMON_INVALID_CURSOR`: 유효하지 않거나 다른 회원의 커서
- `401 AUTH_ACCESS_TOKEN_INVALID`: Access Token이 없거나 유효하지 않은 경우
- `401 AUTH_ACCESS_TOKEN_EXPIRED`: Access Token이 만료된 경우
- `403 AUTH_MEMBER_WITHDRAWN`: 탈퇴한 로그인 회원
- `404 MEMBER_NOT_FOUND`: 조회 대상 회원이 없거나 탈퇴한 경우
- `500 COMMON_INTERNAL_SERVER_ERROR`: 서버 내부 오류

---

## 리포트 공유 앱 연결 페이지

Domain: Report
Method: GET
Progress: 완료
URI: /report/{reportId}
담당자: 박재명
연동여부: Yes

카카오톡 등 외부 메신저에 공유된 HTTPS 링크를 Android 싸방팔방 앱의 인증된 리포트
화면으로 연결한다. 이 경로는 API Base URL(`/api/v1`) 밖의 브라우저용 HTML
진입점이며, 공통 `ApiResponse` JSON 래퍼를 사용하지 않는다.

### Request

- 인증 필요: 불필요
- Request Body: 없음
- Path Variable: `reportId`는 `1` 이상 `Number.MAX_SAFE_INTEGER` 이하의 정수

### 처리 및 보안 기준

- 유효한 ID 형식이면 리포트 존재 여부를 DB에서 확인하지 않고 데이터 없는 HTML만 반환한다.
- HTML에는 리포트 제목·요약·아파트·스터디·회원 정보와 실제 리포트 API 응답을 포함하지 않는다.
- Android 앱 열기 버튼은 패키지 `com.ssafy.ssabangpalbang`과
  `https://legacy.example.com/open/report/{reportId}`를 명시한 `intent:` 링크를 사용한다.
- 앱에서는 로그인 상태를 확인하고, 비로그인 상태이면 로그인 완료 후 원래 숫자형
  `reportId`의 상세 화면으로 복귀한다.
- 실제 데이터는 계속 JWT가 필요한 `GET /api/v1/reports/{reportId}`로만 조회한다.
- 유효하지 않은 Bearer Token이 공개 페이지 요청에 포함된 경우에도 기존 JWT 정책대로
  `401 AUTH_ACCESS_TOKEN_INVALID`을 반환한다.
- 검색엔진 색인을 막고(`noindex, nofollow, noarchive`), 캐시 금지와 CSP·frame 차단
  헤더를 적용한다.

### Response

#### 200 OK

- Content-Type: `text/html`
- Cache-Control: `no-store`
- 데이터가 없는 싸방팔방 앱 연결 페이지

#### 404 Not Found

- `reportId`가 0, 음수, 숫자가 아니거나 JavaScript 안전 정수 범위를 벗어난 경우
- 개인정보나 리포트 존재 여부를 포함하지 않는 빈 응답

### 기존 공유 정책과의 관계

- 익명 리포트 조회 API를 재도입하지 않는다.
- `report.public_id`, `report.visibility`를 재도입하지 않는다.
- 폐기된 `GET /api/v1/reports/public/{publicId}`를 호출하지 않는다.

---

## 폐기된 API

### `GET /api/v1/reports/public/{publicId}`

- 상태: 정책 변경으로 폐기 — 구현·연동 대상 아님
- 사유: 익명 외부 공유를 사용하지 않고 모든 리포트 API에 JWT 인증을 적용한다.
- 관련 스키마: `report.public_id`, `report.visibility`는 Flyway `V3`에서 제거되었다.
- 대체 방식: 로그인 후 숫자형 `reportId`를 사용하는 리포트 목록·상세 API를 호출한다.
- 클라이언트 처리: 외부 공유 URL을 생성하거나 이 URI를 호출하지 않는다.
