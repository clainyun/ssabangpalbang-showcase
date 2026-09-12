import { authenticatedFetch, rethrowSessionFailure } from '@/lib/authenticatedFetch';

import { type ApiEnvelope, type LeaveStudyResult, StudyApiError } from './types';

const NETWORK_ERROR_MESSAGE = '네트워크 연결을 확인해 주세요.';
const FALLBACK_ERROR_MESSAGE = '스터디에서 나가지 못했습니다.';

/** DELETE /studies/{studyId}/members/me — 임장 시작 전 일반 멤버만 가능. */
export async function leaveStudy(
  studyId: number,
  accessToken?: string,
  sessionVersion?: number,
): Promise<LeaveStudyResult> {
  let response: Response;
  try {
    response = await authenticatedFetch(
      `/api/v1/studies/${studyId}/members/me`,
      { method: 'DELETE' },
      { fallbackAccessToken: accessToken, expectedSessionVersion: sessionVersion },
    );
  } catch (error) {
    rethrowSessionFailure(error);
    throw new StudyApiError('COMMON_NETWORK_ERROR', NETWORK_ERROR_MESSAGE);
  }

  const body = (await response.json().catch(() => null)) as ApiEnvelope<LeaveStudyResult> | null;

  if (!response.ok || !body?.success || body.data === null || body.data === undefined) {
    throw new StudyApiError(body?.code ?? 'UNKNOWN', body?.message?.trim() || FALLBACK_ERROR_MESSAGE);
  }

  return body.data;
}
