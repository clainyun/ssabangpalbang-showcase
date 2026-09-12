# AI 리포트 입력 정규화 계약 (AI-004)

## 1. 목적

이 문서는 AI-004 `리포트 입력 정규화·출처 메타데이터 구성`의 내부 계약을 정의한다.

AI-004는 종료된 임장 세션의 체크리스트, 텍스트 기록, STT 결과, 사진 메타데이터를 읽기 전용 스냅샷으로 전달받아 후속 리포트 분석 단계가 공통으로 사용할 수 있는 `schemaVersion: 1` 입력으로 정규화한다.

핵심 목표는 다음과 같다.

- `TEXT`, `STT`, `PHOTO` 기록을 하나의 versioned contract로 변환한다.
- 모든 포함 source에 `sourceId`, 작성 참여자, 체크리스트 항목, 기록 시각을 보존한다.
- 사용자 식별자는 AI 경계에서 `participantRef`로 치환한다.
- 삭제, 중복, 빈 값, 미완료 STT, 사용할 수 없는 사진을 정상 입력과 구분한다.
- 같은 스냅샷은 입력 배열 순서와 무관하게 같은 정렬 결과를 만든다.
- 데이터가 불완전하더라도 추측하지 않고 제외 사유 또는 품질 이슈를 남긴다.

## 2. 범위

### 포함

- 종료된 `field_session` 및 참여자 종료 상태 검증
- 참여자 익명 참조값 생성
- 참여자별 체크리스트와 완료 상태 정규화
- `field_record`의 `TEXT`, `STT`, `PHOTO` 정규화
- source 제외 사유와 입력 품질 이슈 기록
- 입력 건수, 포함·제외 건수 등 정규화 요약 생성
- 결정적 정렬과 참조 무결성 검증
- Pydantic v2 JSON Schema 제공

### 제외

다음 작업은 AI-004 범위가 아니다.

- AI-005~AI-007의 요약, 특징 추출, 문장 생성 및 최종 리포트 조립
- LLM 또는 외부 모델 호출
- 리포트 제목, 요약, 긍정·주의 특징, 카테고리별 분석 생성
- `report.result_json` 생성·저장
- 어떤 source가 어떤 주장에 사용됐는지 결정하는 evidence 선택
- `report_evidence`의 `claim_key`, `display_order` 결정 및 DB 저장
- 리포트 상태 전이, 재시도, Kafka 이벤트, API 응답 조립
- 사진 원본 다운로드, OCR, EXIF 분석, 이미지 분류 또는 vision 분석
- STT 오디오 원본 전달

AI-004의 `sourceId`는 후속 단계가 출처를 추적할 수 있도록 보존하는 키일 뿐이다. 실제 `report_evidence` 저장은 후속 Backend/리포트 오케스트레이션 책임이다.

## 3. 버전 정책

Source 계약과 normalized 계약은 모두 정수형 `schemaVersion: 1`을 사용한다.

- version 1 필드는 임의로 삭제하거나 의미를 변경하지 않는다.
- 필드 의미나 source 해석 규칙이 깨지는 변경은 새 schema version으로 만든다.
- 정의되지 않은 필드는 Pydantic `extra="forbid"` 정책으로 거부한다.
- 모든 날짜·시각 필드는 timezone offset을 포함해야 한다.
- 내부 계약의 JSON 필드명은 Spring 연동 계약과 동일한 camelCase를 사용한다.

## 4. Source 계약 version 1

정규화 함수는 `ReportNormalizationSource`를 입력으로 받는다. 이 객체는 DB Entity가 아니라, authoritative database rows를 읽기 전용으로 복사한 스냅샷 DTO다.

### 4.1 최상위 필드

| 필드 | 타입 | 규칙 |
| --- | --- | --- |
| `schemaVersion` | Integer | 항상 `1` |
| `reportId` | Integer | 1 이상 |
| `studyId` | Integer | 1 이상 |
| `apartmentId` | Integer | 1 이상 |
| `fieldSessionId` | Integer | 1 이상 |
| `sessionStatus` | String | `IN_PROGRESS`, `ENDED` 중 하나 |
| `sessionStartedAt` | DateTime | timezone 필수 |
| `sessionEndedAt` | DateTime 또는 null | timezone 필수. 정규화 시 반드시 값이 있어야 함 |
| `snapshotAt` | DateTime | 스냅샷 기준 시각, timezone 필수 |
| `participants` | Array | 참여자 source 목록 |
| `checklistItems` | Array | 참여자별 체크리스트 항목 source 목록 |
| `authoritativeSourceIds` | Array<Integer> | 같은 read-only snapshot에서 조회한 고유 `field_record.id` 목록 |
| `fieldRecords` | Array | `TEXT`, `STT`, `PHOTO` 기록 목록 |
| `incompleteSttJobs` | Array | field record로 완성되지 않은 STT 작업 목록 |

### 4.2 종료된 read-only snapshot

정규화는 다음 조건이 모두 만족될 때만 시작한다.

- `sessionStatus == "ENDED"`
- `sessionEndedAt != null`
- 모든 participant의 `status == "ENDED"`
- 모든 participant의 `endedAt != null`
- participant의 `startedAt`, `endedAt`이 종료된 field session 범위 안에 있음
- field record의 `recordedAt <= updatedAt <= snapshotAt`이며 `recordedAt >= sessionStartedAt`
- 미완료 STT의 `requestedAt`이 field session 시작 이후, snapshot 이내임

조건을 만족하지 않으면 추측하여 진행하지 않고 다음 오류 중 하나를 발생시킨다.

- `SESSION_NOT_ENDED`
- `PARTICIPANT_NOT_ENDED`
- `PARTICIPANT_TIMESTAMP_INVALID`
- `SOURCE_TIMESTAMP_INVALID`
- `CHECKLIST_TIMESTAMP_INVALID`
- `STT_TIMESTAMP_INVALID`

스냅샷 생산자는 하나의 일관된 read-only DB snapshot에서 참여자, 체크리스트, 기록, 미완료 STT 작업을 읽어야 한다. 정규화기는 DB를 다시 조회하거나 source row를 수정하지 않는다. 정규화 과정에도 DB 쓰기, 상태 변경, 외부 모델 호출이 없다.

운영 Source producer는 Backend의
`GET /internal/v1/reports/{reportId}/input`이다. Backend는
`REPEATABLE READ`, read-only transaction에서 `report.field_session_id` 기반
스냅샷을 생성한다. AI-007은 `REPORT_INTERNAL_TOKEN`으로 인증한 HTTP 응답만
소비하며 운영 DB 자격 증명을 받지 않는다. AI의 `ReportInputRepository`는
AI-004 계약 검증용 참고 구현이며 운영 연동의 권위가 아니다.

Report가 없거나 권위 관계가 유효하지 않은 경우 Backend는
`404 REPORT_NOT_FOUND`로 수렴하고 AI Adapter는 이를 재시도 불가
`SOURCE_LOAD_FAILED`로 변환한다. 권위 관계가 유효한 스냅샷 내부의 삭제 기록,
빈 TEXT, 미완료 STT, 사용할 수 없는 PHOTO는 `200` 원본으로 전달하며
정규화기가 제외 사유와 품질 이슈를 판정한다.

### 4.3 참여자 source

`ParticipantSource`는 다음 필드를 가진다.

- `fieldParticipantId`
- `memberId`
- `status`
- `startedAt`
- `endedAt`

한 스냅샷 안에서 동일한 `memberId`가 두 개 이상의 participant row에 나타나면 `DUPLICATE_PARTICIPANT`로 실패한다.

### 4.4 체크리스트 source

`ChecklistItemSource`는 다음 필드를 가진다.

- `checklistId`, `checklistItemId`, `memberId`
- `fallback`
- `category`, `title`, `subtitle`, `displayOrder`
- `completed`, `completedAt`

동일한 `checklistItemId`가 중복되면 `DUPLICATE_CHECKLIST_ITEM`으로 실패한다.

### 4.5 authoritative source manifest

`authoritativeSourceIds`는 source repository가 동일한 `REPEATABLE READ`, read-only transaction에서 읽은 모든 `field_record.id`의 고유 목록이다.

- `fieldRecords`에 manifest에 없는 `sourceId`가 있으면 `SOURCE_NOT_IN_MANIFEST`로 실패한다.
- manifest의 ID에 대응하는 `fieldRecords` payload가 없으면 `SOURCE_PAYLOAD_MISSING`으로 실패한다.
- manifest ID가 중복되면 Pydantic source validation에서 실패한다.

DB 존재성은 source repository가 실제 `field_record`를 읽는 과정에서 보장한다. manifest는 그 repository가 만든 동일 snapshot 안에서 payload가 추가되거나 누락됐는지 탐지한다. 외부 메시지·파일·HTTP에서 역직렬화한 source DTO는 Backend/DB 재조회 없이는 authoritative한 입력으로 취급하지 않는다.

### 4.6 필드 기록 source

`FieldRecordSource`는 다음 공통 필드를 가진다.

- `sourceId`: `field_record.id`
- `sessionId`
- `checklistItemId`
- `authorId`
- `sourceType`: `TEXT`, `STT`, `PHOTO`
- `deletedAt`, `recordedAt`, `updatedAt`

source 종류에 따라 `textContent`, `sttStatus`, `photoFile`을 사용한다.

- 직접 입력 `TEXT`의 `textContent`는 Backend 입력 계약과 동일하게 최대 2,000자다.
- STT 결과는 PostgreSQL `TEXT` 원문을 손실 없이 받으며 2,000자로 임의 절단하지 않는다.

### 4.7 사진 source 메타데이터

`PhotoFileSource`는 다음 메타데이터만 받는다.

- `fileId`
- `contentType` (선택)
- `sizeBytes` (선택)
- `uploadStatus`
- `expiresAt`, `deletedAt`

S3 object key, bucket, 접근 URL, presigned URL, EXIF, 이미지 바이트는 계약에 포함하지 않는다.

### 4.8 미완료 STT 작업

`IncompleteSttJobSource`는 아직 정상 `field_record`로 변환되지 않은 STT 작업의 상태를 품질 이슈로 남기기 위한 source다.

- `sttId`
- `memberId`
- `checklistItemId`
- `status`
- `retryable`
- `failCode`
- `requestedAt`

이 목록은 normalized `sources`를 직접 만들지 않는다.

동일한 `sttId`가 중복되면 순서에 따라 하나를 고르지 않고 `DUPLICATE_STT_JOB`으로 실패한다.

각 작업의 `memberId`는 종료 participant여야 하고, `checklistItemId`는 같은 snapshot의 normalized checklist item이어야 하며, 작업 회원이 해당 checklist의 소유자여야 한다. 위반 시 각각 `STT_OWNER_NOT_PARTICIPANT`, `STT_CHECKLIST_ITEM_MISSING`, `STT_CHECKLIST_OWNER_MISMATCH`로 실패한다.

## 5. 개인정보 및 식별자 치환

정규화 결과에는 source의 `memberId`, `authorId`, `fieldParticipantId`를 노출하지 않는다.

참여자는 다음 순서로 정렬한 뒤 `P1`, `P2`, `P3` 형태의 `participantRef`를 받는다.

1. `startedAt` 오름차순
2. 같은 시작 시각이면 `fieldParticipantId` 오름차순

체크리스트와 기록의 소유자·작성자도 모두 같은 `participantRef`를 참조한다.

`participantRef`는 해당 report snapshot 안에서만 의미가 있는 익명 참조값이다. 다른 리포트 사이의 동일 회원 식별값으로 사용하면 안 된다.

Normalized 계약에는 다음 민감 데이터가 포함되지 않는다.

- 회원 ID와 작성자 ID
- 이메일, 닉네임, 프로필 이미지 URL
- STT 오디오 object key 또는 접근 URL
- 사진 storage object key 또는 접근 URL

원본 파일명, S3 object key, bucket, 접근 URL, presigned URL, EXIF, 이미지 바이트는 source 계약과 normalized 계약 모두에 포함하지 않는다.

## 6. Normalized 계약 version 1

정규화 결과는 `NormalizedReportInput`이다.

### 6.1 최상위 구조

```json
{
  "schemaVersion": 1,
  "reportId": 1,
  "studyId": 10,
  "apartmentId": 20,
  "fieldSessionId": 30,
  "sessionEndedAt": "2026-07-31T10:00:00+09:00",
  "snapshotAt": "2026-07-31T10:00:01+09:00",
  "participants": [
    {
      "participantRef": "P1",
      "startedAt": "2026-07-31T09:00:00+09:00",
      "endedAt": "2026-07-31T10:00:00+09:00"
    }
  ],
  "checklistItems": [],
  "sources": [],
  "excludedSources": [],
  "qualityIssues": [
    {
      "code": "MISSING_CHECKLIST",
      "participantRef": "P1",
      "referenceId": null,
      "detail": null
    },
    {
      "code": "NO_USABLE_SOURCES",
      "participantRef": null,
      "referenceId": null,
      "detail": null
    }
  ],
  "normalizationSummary": {
    "participantCount": 1,
    "checklistItemCount": 0,
    "completedChecklistItemCount": 0,
    "inputRecordCount": 0,
    "includedSourceCount": 0,
    "excludedRecordCount": 0,
    "duplicateRecordCount": 0,
    "incompleteSttJobCount": 0
  }
}
```

### 6.2 `participants`

각 `NormalizedParticipant`는 다음 필드를 가진다.

- `participantRef`
- `startedAt`
- `endedAt`

`participantRef`는 결과 안에서 유일해야 한다.

종료된 participant가 최소 한 명 있어야 하며, `startedAt <= endedAt <= sessionEndedAt`이어야 한다.

### 6.3 `checklistItems`

각 `NormalizedChecklistItem`은 다음 필드를 가진다.

- `checklistItemId`
- `participantRef`
- `category`, `title`, `subtitle`, `displayOrder`
- `fallback`
- `completed`, `completedAt`

체크리스트 소유자가 participant에 없으면 해당 항목은 normalized 목록에 포함하지 않고 `CHECKLIST_OWNER_NOT_PARTICIPANT` 품질 이슈를 남긴다.

완료 항목인데 `completedAt`이 없으면 항목 자체는 포함하되 `COMPLETION_TIMESTAMP_MISSING` 품질 이슈를 남긴다.

participant에게 체크리스트 항목이 하나도 없으면 `MISSING_CHECKLIST` 품질 이슈를 남긴다.

### 6.4 공통 source 필드

포함된 모든 normalized source는 다음 값을 가진다.

- `sourceId`
- `sourceType`
- `participantRef`
- `checklistItemId`
- `recordedAt`

`sourceId`는 결과 내에서 유일해야 하며 후속 evidence 추적에 사용한다.

### 6.5 TEXT 정규화

포함 조건은 다음과 같다.

- 삭제되지 않음
- 동일 field session 소속
- 존재하는 normalized checklist item 참조
- 작성자가 participant이며 checklist 소유자와 일치
- `textContent`가 null 또는 blank가 아님

결과는 `NormalizedTextSource.semanticText`에 저장한다.

- 문자열 양끝 공백은 제거한다.
- 내부 공백과 개행은 보존한다.
- `semanticText` 최대 길이는 2,000자다.

빈 문자열은 `EMPTY_TEXT`로 제외한다.

### 6.6 STT 정규화

TEXT와 같은 관계·삭제 검증을 적용하며 다음 조건을 추가한다.

- `sttStatus == "DONE"`
- `textContent`가 null 또는 blank가 아님

정상 STT 결과는 `NormalizedSttSource.semanticText`에 저장한다. 오디오 파일, object key, URL은 전달하지 않는다.

Backend가 저장한 STT 원문은 2,000자를 초과해도 손실 없이 보존한다. 후속 모델 입력의 토큰 예산, chunking 또는 요약 전략은 AI-005 이후 단계의 책임이며 AI-004는 원문을 조용히 자르지 않는다.

- `DONE`이 아니면 `STT_NOT_DONE`으로 제외한다.
- `DONE`이지만 텍스트가 비어 있으면 `EMPTY_TEXT`로 제외한다.
- field record로 완성되지 않은 STT 작업은 `qualityIssues`에 별도로 기록한다.

### 6.7 PHOTO 정규화

PHOTO는 이미지 내용을 분석하지 않고 다음 metadata만 normalized 결과에 포함한다.

- `fileId`
- `contentType`
- `sizeBytes`

다음 조건을 모두 만족해야 한다.

- `photoFile`이 존재함
- `uploadStatus == "COMPLETED"`
- 파일 `deletedAt == null`
- `contentType`이 `image/`로 시작함

metadata가 없으면 `PHOTO_METADATA_MISSING`, 사용할 수 없는 상태면 `PHOTO_UNAVAILABLE`로 제외한다.

`expiresAt`은 임시 접근 URL 또는 보관 정책에 쓰일 수 있는 source metadata이며, 현재 Backend의 `FIELD_PHOTO` 유효성 계약에는 포함되지 않는다. 따라서 완료·미삭제 이미지 파일 자체를 정규화에서 제외하는 기준으로 사용하지 않는다.

## 7. 제외 사유와 품질 이슈

`excludedSources`는 실제 `fieldRecords` 가운데 normalized `sources`에 포함되지 않은 건을 나타낸다. 가능한 경우에도 `sourceId`, `sourceType`, 익명화된 `participantRef`, `checklistItemId`, `recordedAt`을 보존하여 제외 사유까지 원천 기록과 추적할 수 있게 한다. 작성자가 대상 participant가 아닌 비정상 기록은 개인정보 경계 때문에 실제 회원 ID 대신 `participantRef: null`을 사용한다.

### 7.1 `ExcludedSourceReason`

| 코드 | 의미 |
| --- | --- |
| `DELETED` | soft-delete된 기록 |
| `DUPLICATED` | 같은 `sourceId`가 앞에서 이미 처리됨 |
| `SESSION_MISMATCH` | 대상 field session과 다른 기록 |
| `CHECKLIST_ITEM_MISSING` | normalized checklist item이 없음 |
| `AUTHOR_NOT_PARTICIPANT` | 작성자가 임장 participant가 아님 |
| `AUTHOR_CHECKLIST_MISMATCH` | 기록 작성자와 checklist 소유자가 다름 |
| `EMPTY_TEXT` | TEXT 또는 완료 STT의 텍스트가 비어 있음 |
| `STT_NOT_DONE` | STT 상태가 `DONE`이 아님 |
| `PHOTO_METADATA_MISSING` | 사진 메타데이터가 없음 |
| `PHOTO_UNAVAILABLE` | 완료·미삭제·이미지 타입 조건을 만족하지 않음 |

제외 항목에는 가능한 경우 `sourceId`, `sourceType`, `participantRef`, `checklistItemId`, `recordedAt`, `reason`, 제한된 `detail`을 남긴다. 원문, 파일 경로, object key 같은 민감 데이터는 `detail`에 기록하지 않는다.

### 7.2 `QualityIssueCode`

| 코드 | 의미 |
| --- | --- |
| `MISSING_CHECKLIST` | 종료 participant의 checklist 항목이 없음 |
| `CHECKLIST_OWNER_NOT_PARTICIPANT` | checklist 소유자가 participant가 아님 |
| `COMPLETION_TIMESTAMP_MISSING` | 완료 항목의 `completedAt`이 없음 |
| `STT_NOT_DONE` | 미완료 STT 작업이 남아 있음 |
| `STT_RESULT_MISSING` | STT 작업은 `DONE`이지만 연결된 정상 결과가 없음 |
| `NO_USABLE_SOURCES` | 정규화 후 사용할 수 있는 source가 한 건도 없음 |

품질 이슈는 정규화 전체를 실패시키지 않는다. 후속 AI 단계가 결과 생성 가능 여부, 보완 필요 여부, 실패 사유를 결정할 때 참고하는 진단 정보다. 미완료 STT 이슈에는 검증된 `participantRef`, `sttId` 기반 `referenceId`, `requestedAt` 기반 `observedAt`을 보존한다.

## 8. 중복 처리

`fieldRecords`는 `sourceId`로 그룹화한다.

동일한 `sourceId`와 완전히 동일한 payload가 여러 번 나타나면 한 건만 검증·정규화 대상으로 사용하고 나머지는 `DUPLICATED`로 제외한다. 동일한 `sourceId`의 payload가 서로 다르면 어떤 값도 임의 선택하지 않고 `CONFLICTING_SOURCE_ID` 무결성 오류로 정규화를 중단한다.

따라서 스냅샷 생산자는 가능한 한 DB에서 `field_record.id`별 한 행만 생성해야 한다. 정규화기의 중복 처리는 join 중복 등 비정상 입력이 결과 source를 중복시키지 않게 하는 방어선이다.

## 9. 결정적 정렬

동일한 source snapshot은 입력 배열의 기존 순서와 무관하게 다음 순서로 정렬된다.

### 참여자

```text
(startedAt, fieldParticipantId)
```

### 체크리스트 항목

```text
(participantRef 번호, displayOrder, checklistItemId)
```

### 정규화 source

```text
(
  checklist item의 participantRef 번호,
  checklist item의 displayOrder,
  checklistItemId,
  recordedAt,
  sourceId
)
```

### 제외 source

원본 field record의 canonical 처리 순서인 다음 정렬을 따른다.

```text
(sourceId, recordedAt, updatedAt)
```

### 미완료 STT 품질 이슈

```text
sttId 오름차순
```

정규화 함수에는 현재 시각 조회, 난수, DB 호출, 외부 모델 호출이 없으므로 같은 snapshot은 재시도해도 같은 구조와 순서를 만든다.

## 10. 정규화 요약과 무결성

`normalizationSummary`는 다음 값을 제공한다.

- `participantCount`
- `checklistItemCount`
- `completedChecklistItemCount`
- `inputRecordCount`
- `includedSourceCount`
- `excludedRecordCount`
- `duplicateRecordCount`
- `incompleteSttJobCount`

Normalized 모델은 다음 불변식을 다시 검증한다.

- `participantRef`는 유일함
- `checklistItemId`는 유일함
- 포함된 `sourceId`는 유일함
- checklist와 source가 존재하는 `participantRef`만 참조함
- source가 존재하는 normalized checklist item만 참조함
- source의 `participantRef`가 참조 checklist의 `participantRef`와 일치함
- quality issue가 존재하는 `participantRef`만 참조함
- summary의 participant, checklist, 완료, 포함, 제외 count가 실제 배열 길이와 일치함
- `duplicateRecordCount`가 실제 `DUPLICATED` 제외 건수와 일치함
- `incompleteSttJobCount`가 실제 STT 품질 이슈 수와 일치함
- `NO_USABLE_SOURCES`가 `sources`가 비어 있을 때만 정확히 하나의 상태 의미로 존재함
- `inputRecordCount == includedSourceCount + excludedRecordCount`

이 불변식이 깨지면 후속 단계가 임의로 복구하지 않고 Pydantic validation error로 실패한다.

## 11. 후속 단계와의 경계

AI-005~AI-007은 `NormalizedReportInput`을 입력으로 사용하며 다음 원칙을 따라야 한다.

- `sources`에 존재하지 않는 `sourceId`를 생성하거나 인용하지 않는다.
- `excludedSources`를 정상 evidence처럼 사용하지 않는다.
- `qualityIssues`를 사실 데이터로 해석하지 않는다.
- participant를 식별할 때 `participantRef`만 사용한다.
- PHOTO에 대해 이미지 내용을 보았다고 주장하지 않는다.
- STT 오디오 원문이나 저장소 위치를 요구하지 않는다.
- AI-005의 내부 `categorySummaries[].sourceIndexes`는 필터링된 모든 `usableSources`의
  0-based index를 정확히 한 번씩 포함해야 한다. 이를 통해 명확한 긍정·주의로
  분류되지 않는 사실형·중립형 세부 기록도 해당 카테고리 요약의 근거로 반영한다.
- `sourceIndexes`는 내부 생성 결과 검증용이며 공개 리포트 API 응답에는 노출하지 않는다.

후속 Backend 단계는 AI 결과가 참조한 `sourceId`를 authoritative `field_record`와 다시 대조한 뒤에만 `report_evidence`를 저장해야 한다. AI-004는 evidence 사용 여부, `claim_key`, 표시 순서, 저장 성공을 보장하지 않는다.
