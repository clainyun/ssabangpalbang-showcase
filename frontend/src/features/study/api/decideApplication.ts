import { authenticatedFetch, rethrowSessionFailure } from '@/lib/authenticatedFetch';

import { type ApiEnvelope, type ApplicationDecisionResult, StudyApiError } from './types';

const NETWORK_ERROR_MESSAGE = '네트워크 연결을 확인해 주세요.';

async function decide(
  studyId: number,
  applicationId: number,
  action: 'approve' | 'reject',
  fallbackMessage: string,
  accessToken?: string,
  sessionVersion?: number,
): Promise<ApplicationDecisionResult> {
  let response: Response;
  try {
    response = await authenticatedFetch(
      `/api/v1/studies/${studyId}/applications/${applicationId}/${action}`,
      { method: 'PATCH' },
      { fallbackAccessToken: accessToken, expectedSessionVersion: sessionVersion },
    );
  } catch (error) {
    rethrowSessionFailure(error);
    throw new StudyApiError('COMMON_NETWORK_ERROR', NETWORK_ERROR_MESSAGE);
  }

  const body = (await response.json().catch(() => null)) as ApiEnvelope<ApplicationDecisionResult> | null;

  if (!response.ok || !body?.success || body.data === null || body.data === undefined) {
    throw new StudyApiError(body?.code ?? 'UNKNOWN', body?.message?.trim() || fallbackMessage);
  }

  return body.data;
}

/** PATCH /studies/{studyId}/applications/{applicationId}/approve */
export function approveApplication(
  studyId: number,
  applicationId: number,
  accessToken?: string,
  sessionVersion?: number,
): Promise<ApplicationDecisionResult> {
  return decide(studyId, applicationId, 'approve', '신청을 승인하지 못했습니다.', accessToken, sessionVersion);
}

/** PATCH /studies/{studyId}/applications/{applicationId}/reject */
export function rejectApplication(
  studyId: number,
  applicationId: number,
  accessToken?: string,
  sessionVersion?: number,
): Promise<ApplicationDecisionResult> {
  return decide(studyId, applicationId, 'reject', '신청을 거절하지 못했습니다.', accessToken, sessionVersion);
}
