import { authenticatedFetch, rethrowSessionFailure } from '@/lib/authenticatedFetch';

import { type ApiEnvelope, type RecruitmentCloseResult, StudyApiError } from './types';

const NETWORK_ERROR_MESSAGE = '네트워크 연결을 확인해 주세요.';
const FALLBACK_ERROR_MESSAGE = '모집을 마감하지 못했습니다.';

/** PATCH /studies/{studyId}/recruitment/close — 스터디장만 가능(docs/API.md "스터디 모집 조기 마감"). */
export async function closeRecruitment(
  studyId: number,
  accessToken?: string,
  sessionVersion?: number,
): Promise<RecruitmentCloseResult> {
  let response: Response;
  try {
    response = await authenticatedFetch(
      `/api/v1/studies/${studyId}/recruitment/close`,
      { method: 'PATCH' },
      { fallbackAccessToken: accessToken, expectedSessionVersion: sessionVersion },
    );
  } catch (error) {
    rethrowSessionFailure(error);
    throw new StudyApiError('COMMON_NETWORK_ERROR', NETWORK_ERROR_MESSAGE);
  }

  const body = (await response.json().catch(() => null)) as ApiEnvelope<RecruitmentCloseResult> | null;

  if (!response.ok || !body?.success || body.data === null || body.data === undefined) {
    throw new StudyApiError(body?.code ?? 'UNKNOWN', body?.message?.trim() || FALLBACK_ERROR_MESSAGE);
  }

  return body.data;
}
