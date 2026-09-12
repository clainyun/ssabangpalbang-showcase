import { authenticatedFetch, rethrowSessionFailure } from '@/lib/authenticatedFetch';

import { type ApiEnvelope, type StudyMemberListResult, StudyApiError } from './types';

const NETWORK_ERROR_MESSAGE = '네트워크 연결을 확인해 주세요.';
const FALLBACK_ERROR_MESSAGE = '멤버 목록을 불러오지 못했습니다.';

/** GET /studies/{studyId}/members — 스터디장·승인 멤버만 조회 가능(docs/API.md "스터디 멤버 목록 조회"). */
export async function getMembers(
  studyId: number,
  accessToken?: string,
  sessionVersion?: number,
): Promise<StudyMemberListResult> {
  let response: Response;
  try {
    response = await authenticatedFetch(
      `/api/v1/studies/${studyId}/members`,
      { method: 'GET' },
      { fallbackAccessToken: accessToken, expectedSessionVersion: sessionVersion },
    );
  } catch (error) {
    rethrowSessionFailure(error);
    throw new StudyApiError('COMMON_NETWORK_ERROR', NETWORK_ERROR_MESSAGE);
  }

  const body = (await response.json().catch(() => null)) as ApiEnvelope<StudyMemberListResult> | null;

  if (!response.ok || !body?.success || body.data === null || body.data === undefined) {
    throw new StudyApiError(body?.code ?? 'UNKNOWN', body?.message?.trim() || FALLBACK_ERROR_MESSAGE);
  }

  return body.data;
}
