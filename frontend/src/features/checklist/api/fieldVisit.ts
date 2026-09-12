import { authenticatedFetch, rethrowSessionFailure } from '@/lib/authenticatedFetch';

/**
 * 개인 임장 종료 API (POST /field-visit/finish). 체크리스트 미완료 항목이 있어도
 * 현재 기록으로 종료하는 것을 허용하며(docs/API.md), 같은 clientRequestId로 다시
 * 호출하면 최초 종료 결과를 그대로 돌려줍니다(멱등).
 */

export interface FieldVisitFinishParticipant {
  participantId: number;
  status: string;
  startedAt: string;
  endedAt: string | null;
  endReason: string | null;
  stayDurationSec: number | null;
}

export interface FieldVisitFinishChecklist {
  completedCount: number;
  totalCount: number;
  incompleteCount: number;
}

export interface FieldVisitFinishResult {
  studyId: number;
  sessionId: number;
  participant: FieldVisitFinishParticipant;
  checklist: FieldVisitFinishChecklist;
  sessionEnded: boolean;
  sessionEndReason: string | null;
  reportTriggered: boolean;
  reportId: number | null;
}

interface ApiEnvelope<T> {
  success: boolean;
  code: string;
  message: string;
  data: T | null;
}

export class FieldVisitApiError extends Error {
  code: string;

  constructor(code: string, message: string) {
    super(message);
    this.name = 'FieldVisitApiError';
    this.code = code;
  }
}

/**
 * 과반수 종료 투표 현황. 임장 상태 조회(GET /field-visit)와 투표(POST
 * /close-votes) 응답이 같은 필드를 공유합니다. 세션이 없어도 서버가 null 대신
 * 0/false 로 채운 객체를 주므로 화면에서 null 체크가 필요 없습니다.
 *
 * requiredVoteCount 는 "실제로 임장을 시작한 참여자"(field_participant) 기준
 * 과반입니다. 스터디 전체 멤버가 아니라, 아직 시작하지 않은 사람은 분모에서
 * 빠집니다(BE-018-1).
 */
export interface FieldVisitCloseVoteStatus {
  startedParticipantCount: number;
  voteCount: number;
  requiredVoteCount: number;
  hasVoted: boolean;
  /** 세션이 진행 중이고, 내가 참여자이고, 아직 투표하지 않았을 때만 true. */
  canVote: boolean;
}

/**
 * 투표 응답. 과반에 도달하면 sessionEnded 가 true 로 오고, 그 순간 리포트 생성이
 * 트리거되어 reportId 가 함께 옵니다(finishFieldVisit 과 같은 방식). 아직 과반이
 * 안 됐으면 reportId 는 null 입니다.
 */
export interface FieldVisitCloseVoteResult extends FieldVisitCloseVoteStatus {
  studyId: number;
  sessionId: number;
  sessionEnded: boolean;
  sessionEndReason: string | null;
  reportTriggered: boolean;
  reportId: number | null;
  reportStatus: string | null;
}

/** 상태 조회 응답 중 체크리스트와 전역 임장 복귀 버튼이 함께 쓰는 부분입니다. */
export interface FieldVisitStatusSession {
  sessionId: number;
  startedAt: string;
  endedAt: string | null;
  /** 현재 임장을 진행 중(IN_PROGRESS)인 참여자 수. 배포 전 구버전 응답엔 없을 수 있어 optional. */
  activeParticipantCount?: number;
  /** 임장을 종료(ENDED)한 참여자 수. 배포 전 구버전 응답엔 없을 수 있어 optional. */
  endedParticipantCount?: number;
}

export interface FieldVisitStatusParticipant {
  participantId: number;
  status: string;
  startedAt: string;
  endedAt: string | null;
}

export interface FieldVisitStatusResult {
  studyId: number;
  status: string;
  session: FieldVisitStatusSession | null;
  participant: FieldVisitStatusParticipant | null;
  closeVote: FieldVisitCloseVoteStatus;
}

/** GET /field-visit — 투표 현황을 읽으려고 씁니다. */
export async function getFieldVisitStatus(studyId: number): Promise<FieldVisitStatusResult> {
  let response: Response;
  try {
    response = await authenticatedFetch(`/api/v1/studies/${studyId}/field-visit`, {
      method: 'GET',
      headers: { Accept: 'application/json' },
    });
  } catch (error) {
    rethrowSessionFailure(error);
    throw new FieldVisitApiError('NETWORK_ERROR', '네트워크 연결을 확인해 주세요.');
  }

  const body = (await response
    .json()
    .catch(() => null)) as ApiEnvelope<FieldVisitStatusResult> | null;

  if (!response.ok || !body?.success || body.data === null) {
    throw new FieldVisitApiError(
      body?.code ?? 'UNKNOWN',
      body?.message ?? '임장 상태를 불러오지 못했습니다.',
    );
  }

  return body.data;
}

/**
 * POST /close-votes — 전체 임장 강제 종료에 한 표를 던집니다. 요청 본문은 없습니다.
 *
 * 취소 API가 없어 한 번 던진 표는 되돌릴 수 없고, 같은 사람이 다시 호출해도
 * 표가 늘지 않습니다(멱등). 이미 종료된 세션에 호출하면 에러가 아니라
 * sessionEnded=true 인 정상 응답이 옵니다.
 */
export async function voteCloseFieldVisit(studyId: number): Promise<FieldVisitCloseVoteResult> {
  let response: Response;
  try {
    response = await authenticatedFetch(`/api/v1/studies/${studyId}/field-visit/close-votes`, {
      method: 'POST',
      headers: { Accept: 'application/json' },
    });
  } catch (error) {
    rethrowSessionFailure(error);
    throw new FieldVisitApiError('NETWORK_ERROR', '네트워크 연결을 확인해 주세요.');
  }

  const body = (await response
    .json()
    .catch(() => null)) as ApiEnvelope<FieldVisitCloseVoteResult> | null;

  if (!response.ok || !body?.success || body.data === null) {
    throw new FieldVisitApiError(
      body?.code ?? 'UNKNOWN',
      body?.message ?? '종료 요청을 보내지 못했습니다.',
    );
  }

  return body.data;
}

export type FieldVisitParticipantStatus = 'NOT_JOINED' | 'IN_PROGRESS' | 'ENDED';

/** GET /field-visit/participants 응답의 참여자 한 명(docs/API.md "참여자별 임장 상태 조회"). */
export interface FieldVisitParticipant {
  participantId: number | null;
  memberId: number;
  nickname: string;
  profileImageUrl: string | null;
  selectedCharacterId: string;
  role: 'LEADER' | 'MEMBER';
  status: FieldVisitParticipantStatus;
  startedAt: string | null;
  endedAt: string | null;
  endReason: string | null;
  stayDurationSec: number;
  isMe: boolean;
  isLeader: boolean;
  canRequestFinish: boolean;
}

export interface FieldVisitParticipantsResult {
  studyId: number;
  sessionId: number | null;
  status: string;
  participantCount: number;
  inProgressCount: number;
  endedCount: number;
  notJoinedCount: number;
  participants: FieldVisitParticipant[];
}

/**
 * GET /field-visit/participants — 참여자별 임장 상태(진행 중·종료·미참여)를 조회합니다.
 *
 * docs/API.md 기준 아직 백엔드에 연동되지 않은 API입니다("연동여부: No"). 지금 호출하면
 * 404 등으로 실패하는데, 호출부(useFieldVisitParticipants)가 그 실패를 조용히 삼켜
 * 인원수 없는 기존 안내문으로 대체합니다 — 배포되면 다음 폴링부터 자연스럽게 실시간
 * 인원이 채워집니다.
 */
export async function getFieldVisitParticipants(
  studyId: number,
): Promise<FieldVisitParticipantsResult> {
  let response: Response;
  try {
    response = await authenticatedFetch(`/api/v1/studies/${studyId}/field-visit/participants`, {
      method: 'GET',
      headers: { Accept: 'application/json' },
    });
  } catch (error) {
    rethrowSessionFailure(error);
    throw new FieldVisitApiError('NETWORK_ERROR', '네트워크 연결을 확인해 주세요.');
  }

  const body = (await response
    .json()
    .catch(() => null)) as ApiEnvelope<FieldVisitParticipantsResult> | null;

  if (!response.ok || !body?.success || body.data === null) {
    throw new FieldVisitApiError(
      body?.code ?? 'UNKNOWN',
      body?.message ?? '참여자 임장 상태를 불러오지 못했습니다.',
    );
  }

  return body.data;
}

export async function finishFieldVisit(
  studyId: number,
  clientRequestId: string,
): Promise<FieldVisitFinishResult> {
  let response: Response;
  try {
    response = await authenticatedFetch(`/api/v1/studies/${studyId}/field-visit/finish`, {
      method: 'POST',
      headers: { 'Content-Type': 'application/json', Accept: 'application/json' },
      body: JSON.stringify({ finishConfirmed: true, clientRequestId }),
    });
  } catch (error) {
    rethrowSessionFailure(error);
    throw new FieldVisitApiError('NETWORK_ERROR', '네트워크 연결을 확인해 주세요.');
  }

  const body = (await response
    .json()
    .catch(() => null)) as ApiEnvelope<FieldVisitFinishResult> | null;

  if (!response.ok || !body?.success || body.data === null) {
    throw new FieldVisitApiError(
      body?.code ?? 'UNKNOWN',
      body?.message ?? '임장을 종료하지 못했습니다.',
    );
  }

  return body.data;
}

export interface FieldVisitFinishCancelParticipant {
  participantId: number;
  status: 'IN_PROGRESS';
  startedAt: string;
  endedAt: null;
  endReason: null;
  stayDurationSec: null;
}

export interface FieldVisitFinishCancelResult {
  studyId: number;
  sessionId: number;
  participant: FieldVisitFinishCancelParticipant;
}

/**
 * POST /field-visit/finish/cancel — 방금 낸 개인 임장 종료를 취소하고 다시 진행 중으로
 * 되돌립니다(BE-014). 본인이 직접 종료(SELF_ENDED)했고 세션이 아직 진행 중일 때만
 * 되돌릴 수 있고, 이미 진행 중이면 멱등하게 같은 상태를 돌려줍니다.
 */
export async function cancelFieldVisitFinish(
  studyId: number,
  clientRequestId: string,
): Promise<FieldVisitFinishCancelResult> {
  let response: Response;
  try {
    response = await authenticatedFetch(`/api/v1/studies/${studyId}/field-visit/finish/cancel`, {
      method: 'POST',
      headers: { 'Content-Type': 'application/json', Accept: 'application/json' },
      body: JSON.stringify({ clientRequestId }),
    });
  } catch (error) {
    rethrowSessionFailure(error);
    throw new FieldVisitApiError('NETWORK_ERROR', '네트워크 연결을 확인해 주세요.');
  }

  const body = (await response
    .json()
    .catch(() => null)) as ApiEnvelope<FieldVisitFinishCancelResult> | null;

  if (!response.ok || !body?.success || body.data === null) {
    throw new FieldVisitApiError(
      body?.code ?? 'UNKNOWN',
      body?.message ?? '임장 종료 취소를 처리하지 못했습니다.',
    );
  }

  return body.data;
}
