import { authenticatedFetch, rethrowSessionFailure } from '@/lib/authenticatedFetch';

import { type ApiEnvelope, type KickMemberResult, StudyApiError } from './types';

const NETWORK_ERROR_MESSAGE = '네트워크 연결을 확인해 주세요.';
const FALLBACK_ERROR_MESSAGE = '멤버를 강퇴하지 못했습니다.';

/** DELETE /studies/{studyId}/members/{memberId} — 스터디장만 가능(docs/API.md "스터디 멤버 강퇴"). */
export async function kickMember(
  studyId: number,
  memberId: number,
  accessToken?: string,
  sessionVersion?: number,
): Promise<KickMemberResult> {
  let response: Response;
  try {
    response = await authenticatedFetch(
      `/api/v1/studies/${studyId}/members/${memberId}`,
      { method: 'DELETE' },
      { fallbackAccessToken: accessToken, expectedSessionVersion: sessionVersion },
    );
  } catch (error) {
    rethrowSessionFailure(error);
    throw new StudyApiError('COMMON_NETWORK_ERROR', NETWORK_ERROR_MESSAGE);
  }

  const body = (await response.json().catch(() => null)) as ApiEnvelope<KickMemberResult> | null;

  if (!response.ok || !body?.success || body.data === null || body.data === undefined) {
    throw new StudyApiError(body?.code ?? 'UNKNOWN', body?.message?.trim() || FALLBACK_ERROR_MESSAGE);
  }

  return body.data;
}
