import { authenticatedFetch, rethrowSessionFailure } from '@/lib/authenticatedFetch';

import {
  type ApiEnvelope,
  StudyApiError,
  type UpdateStudyDetailsInput,
  type UpdateStudyDetailsResult,
} from './types';

const NETWORK_ERROR_MESSAGE = '네트워크 연결을 확인해 주세요.';
const FALLBACK_ERROR_MESSAGE = '스터디 목표와 소개를 수정하지 못했습니다.';

/** PATCH /studies/{studyId} — 스터디장 전용 목표·소개 수정. */
export async function updateStudyDetails(
  studyId: number,
  input: UpdateStudyDetailsInput,
  accessToken?: string,
  sessionVersion?: number,
): Promise<UpdateStudyDetailsResult> {
  let response: Response;
  try {
    response = await authenticatedFetch(
      `/api/v1/studies/${studyId}`,
      {
        method: 'PATCH',
        headers: { 'Content-Type': 'application/json' },
        body: JSON.stringify(input),
      },
      { fallbackAccessToken: accessToken, expectedSessionVersion: sessionVersion },
    );
  } catch (error) {
    rethrowSessionFailure(error);
    throw new StudyApiError('COMMON_NETWORK_ERROR', NETWORK_ERROR_MESSAGE);
  }

  const body = (await response.json().catch(() => null)) as
    | ApiEnvelope<UpdateStudyDetailsResult>
    | null;

  if (!response.ok || !body?.success || body.data === null || body.data === undefined) {
    throw new StudyApiError(body?.code ?? 'UNKNOWN', body?.message?.trim() || FALLBACK_ERROR_MESSAGE);
  }

  return body.data;
}
