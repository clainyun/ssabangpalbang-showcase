import { authenticatedFetch, rethrowSessionFailure } from '@/lib/authenticatedFetch';

import {
  type ApiEnvelope,
  type ScheduleDeleteResult,
  type ScheduleInput,
  type ScheduleMutationResult,
  type ScheduleUpdateInput,
  StudyApiError,
  type StudyScheduleDetail,
} from './types';

const NETWORK_ERROR_MESSAGE = '네트워크 연결을 확인해 주세요.';

async function requestSchedule<T>(
  studyId: number,
  init: RequestInit,
  fallbackMessage: string,
  accessToken?: string,
  sessionVersion?: number,
): Promise<T> {
  let response: Response;
  try {
    response = await authenticatedFetch(
      `/api/v1/studies/${studyId}/schedule`,
      init,
      { fallbackAccessToken: accessToken, expectedSessionVersion: sessionVersion },
    );
  } catch (error) {
    rethrowSessionFailure(error);
    throw new StudyApiError('COMMON_NETWORK_ERROR', NETWORK_ERROR_MESSAGE);
  }

  const body = (await response.json().catch(() => null)) as ApiEnvelope<T> | null;

  if (!response.ok || !body?.success || body.data === null || body.data === undefined) {
    throw new StudyApiError(body?.code ?? 'UNKNOWN', body?.message?.trim() || fallbackMessage);
  }

  return body.data;
}

/** GET /studies/{studyId}/schedule (docs/API.md "임장 일정 조회") */
export function getStudySchedule(
  studyId: number,
  accessToken?: string,
  sessionVersion?: number,
): Promise<StudyScheduleDetail> {
  return requestSchedule(
    studyId,
    { method: 'GET' },
    '일정을 불러오지 못했습니다.',
    accessToken,
    sessionVersion,
  );
}

/** POST /studies/{studyId}/schedule — 스터디장만 가능. */
export function createStudySchedule(
  studyId: number,
  input: ScheduleInput,
  accessToken?: string,
  sessionVersion?: number,
): Promise<ScheduleMutationResult> {
  return requestSchedule(
    studyId,
    {
      method: 'POST',
      headers: { 'Content-Type': 'application/json' },
      body: JSON.stringify(input),
    },
    '일정을 등록하지 못했습니다.',
    accessToken,
    sessionVersion,
  );
}

/** PATCH /studies/{studyId}/schedule — 스터디장만 가능. */
export function updateStudySchedule(
  studyId: number,
  input: ScheduleUpdateInput,
  accessToken?: string,
  sessionVersion?: number,
): Promise<ScheduleMutationResult> {
  return requestSchedule(
    studyId,
    {
      method: 'PATCH',
      headers: { 'Content-Type': 'application/json' },
      body: JSON.stringify(input),
    },
    '일정을 수정하지 못했습니다.',
    accessToken,
    sessionVersion,
  );
}

/** DELETE /studies/{studyId}/schedule — 스터디장만 가능(취소 상태로 변경). */
export function deleteStudySchedule(
  studyId: number,
  accessToken?: string,
  sessionVersion?: number,
): Promise<ScheduleDeleteResult> {
  return requestSchedule(
    studyId,
    { method: 'DELETE' },
    '일정을 삭제하지 못했습니다.',
    accessToken,
    sessionVersion,
  );
}
