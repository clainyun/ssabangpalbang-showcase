import { authenticatedFetch, rethrowSessionFailure } from '@/lib/authenticatedFetch';
import { useAuthStore } from '@/store/authStore';

import { StudyApiError, type ApiEnvelope, type StudyPurpose } from './types';

export interface StudyApartmentSummary {
  apartmentId: number;
  name: string;
  address: string;
}

export interface StudyLeaderSummary {
  memberId: number;
  nickname: string;
  selectedCharacterId: string;
}

export interface StudyMemberSummary {
  memberId: number;
  nickname: string;
  selectedCharacterId: string;
  role: 'LEADER' | 'MEMBER';
}

export interface StudyNextSchedule {
  scheduleId: number;
  startAt: string;
  endAt: string | null;
  meetingPlace: string;
}

export interface StudyPermissions {
  canManageApplications: boolean;
  canManageMembers: boolean;
  canManageNotices: boolean;
  canManageSchedule: boolean;
  canUseChat: boolean;
  canUseFieldVisit: boolean;
}

export interface StudyReportSummary {
  reportId: number;
  status: 'PENDING' | 'IN_PROGRESS' | 'DONE' | 'FAILED';
}

/**
 * GET /api/v1/studies/{studyId} 응답 (docs/API.md "스터디 모집/홈 상세 조회").
 * 비멤버·신청 대기자는 모집 정보만, 승인 멤버·스터디장은 일정·멤버·채팅 배지까지 받습니다.
 * 참여 상태에 따라 일부 필드가 비어 있을 수 있어(null·빈 배열) 화면에서는 존재 여부를
 * 먼저 확인하고 씁니다.
 */
export interface StudyDetail {
  studyId: number;
  title: string;
  intro: string;
  goal: string;
  purpose: StudyPurpose;
  status: 'RECRUITING' | 'CLOSED' | 'IN_PROGRESS' | 'COMPLETED' | 'CANCELED';
  apartment: StudyApartmentSummary;
  leader: StudyLeaderSummary;
  currentMemberCount: number;
  capacity: number;
  nextSchedule: StudyNextSchedule | null;
  memberSummary: StudyMemberSummary[];
  myParticipationStatus: 'NONE' | 'PENDING' | 'APPROVED' | 'REJECTED';
  isLeader: boolean;
  isMember: boolean;
  canApply: boolean;
  unreadChatCount: number | null;
  fieldVisitStatus: 'NOT_STARTED' | 'IN_PROGRESS' | 'ENDED' | null;
  fieldSessionId: number | null;
  report: StudyReportSummary | null;
  canStartFieldVisit: boolean;
  readOnly: boolean;
  permissions: StudyPermissions;
}

export async function getStudyDetail(studyId: number): Promise<StudyDetail> {
  const { accessToken, sessionVersion } = useAuthStore.getState();

  let response: Response;
  try {
    response = await authenticatedFetch(
      `/api/v1/studies/${studyId}`,
      { method: 'GET' },
      { expectedSessionVersion: sessionVersion, fallbackAccessToken: accessToken ?? undefined },
    );
  } catch (error) {
    rethrowSessionFailure(error);
    throw new StudyApiError('NETWORK_ERROR', '네트워크 연결을 확인해 주세요.');
  }

  const body = (await response.json().catch(() => null)) as ApiEnvelope<StudyDetail> | null;

  if (!response.ok || !body?.success || body.data === null) {
    throw new StudyApiError(body?.code ?? 'UNKNOWN', body?.message ?? '스터디 정보를 불러오지 못했습니다.');
  }

  return body.data;
}
