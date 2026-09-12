import { authenticatedFetch, rethrowSessionFailure } from '@/lib/authenticatedFetch';

import {
  type ApiEnvelope,
  type MemberReviewCreateRequest,
  type MemberReviewCreateResult,
  StudyApiError,
} from './types';

const NETWORK_ERROR_MESSAGE = '네트워크 연결을 확인해 주세요.';
const FALLBACK_ERROR_MESSAGE = '리뷰를 등록하지 못했습니다.';

/** POST /studies/{studyId}/members/{memberId}/reviews — 같은 스터디 멤버 익명 평가 등록. */
export async function createMemberReview(
  studyId: number,
  memberId: number,
  request: MemberReviewCreateRequest,
  accessToken?: string,
  sessionVersion?: number,
): Promise<MemberReviewCreateResult> {
  let response: Response;
  try {
    response = await authenticatedFetch(
      `/api/v1/studies/${studyId}/members/${memberId}/reviews`,
      {
        method: 'POST',
        headers: {
          Accept: 'application/json',
          'Content-Type': 'application/json',
        },
        body: JSON.stringify(request),
      },
      { fallbackAccessToken: accessToken, expectedSessionVersion: sessionVersion },
    );
  } catch (error) {
    rethrowSessionFailure(error);
    throw new StudyApiError('COMMON_NETWORK_ERROR', NETWORK_ERROR_MESSAGE);
  }

  const body = (await response.json().catch(() => null)) as
    | ApiEnvelope<MemberReviewCreateResult>
    | null;

  if (!response.ok || !body?.success || body.data === null || body.data === undefined) {
    throw new StudyApiError(
      body?.code ?? 'UNKNOWN',
      body?.message?.trim() || FALLBACK_ERROR_MESSAGE,
    );
  }

  return body.data;
}
