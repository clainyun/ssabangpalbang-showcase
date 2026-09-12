import { authenticatedFetch, rethrowSessionFailure } from '@/lib/authenticatedFetch';

import { type ApiEnvelope, type ApplicationListResult, type ApplicationStatus, StudyApiError } from './types';

const NETWORK_ERROR_MESSAGE = '네트워크 연결을 확인해 주세요.';
const FALLBACK_ERROR_MESSAGE = '신청자 목록을 불러오지 못했습니다.';

/** GET /studies/{studyId}/applications — 스터디장만 조회 가능(docs/API.md "신청자 목록 조회"). */
export async function getApplications(
  studyId: number,
  options: { status?: ApplicationStatus; cursor?: number; size?: number } = {},
  accessToken?: string,
  sessionVersion?: number,
): Promise<ApplicationListResult> {
  const query = new URLSearchParams();
  if (options.status) query.set('status', options.status);
  if (options.cursor !== undefined) query.set('cursor', String(options.cursor));
  query.set('size', String(options.size ?? 20));

  let response: Response;
  try {
    response = await authenticatedFetch(
      `/api/v1/studies/${studyId}/applications?${query.toString()}`,
      { method: 'GET' },
      { fallbackAccessToken: accessToken, expectedSessionVersion: sessionVersion },
    );
  } catch (error) {
    rethrowSessionFailure(error);
    throw new StudyApiError('COMMON_NETWORK_ERROR', NETWORK_ERROR_MESSAGE);
  }

  const body = (await response.json().catch(() => null)) as ApiEnvelope<ApplicationListResult> | null;

  if (!response.ok || !body?.success || body.data === null || body.data === undefined) {
    throw new StudyApiError(body?.code ?? 'UNKNOWN', body?.message?.trim() || FALLBACK_ERROR_MESSAGE);
  }

  return body.data;
}
