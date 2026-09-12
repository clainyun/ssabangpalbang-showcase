import { authenticatedFetch, rethrowSessionFailure } from '@/lib/authenticatedFetch';

import {
  type ApiEnvelope,
  StudyApiError,
  type StudyNoticeDeleteResult,
  type StudyNoticeListResult,
  type StudyNoticeMutationResult,
} from './types';

const NETWORK_ERROR_MESSAGE = '네트워크 연결을 확인해 주세요.';

async function requestNotice<T>(
  path: string,
  init: RequestInit,
  fallbackMessage: string,
  accessToken?: string,
  sessionVersion?: number,
): Promise<T> {
  let response: Response;
  try {
    response = await authenticatedFetch(path, init, {
      fallbackAccessToken: accessToken,
      expectedSessionVersion: sessionVersion,
    });
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

/** GET /studies/{studyId}/notices — 스터디장·승인 멤버만 조회 가능 (docs/API.md "스터디 공지 목록 조회") */
export function getNotices(
  studyId: number,
  cursor?: number,
  accessToken?: string,
  sessionVersion?: number,
): Promise<StudyNoticeListResult> {
  const query = cursor !== undefined ? `?cursor=${cursor}` : '';
  return requestNotice(
    `/api/v1/studies/${studyId}/notices${query}`,
    { method: 'GET' },
    '공지를 불러오지 못했습니다.',
    accessToken,
    sessionVersion,
  );
}

/** POST /studies/{studyId}/notices — 스터디장만 가능. */
export function createNotice(
  studyId: number,
  content: string,
  accessToken?: string,
  sessionVersion?: number,
): Promise<StudyNoticeMutationResult> {
  return requestNotice(
    `/api/v1/studies/${studyId}/notices`,
    {
      method: 'POST',
      headers: { 'Content-Type': 'application/json' },
      body: JSON.stringify({ content }),
    },
    '공지를 등록하지 못했습니다.',
    accessToken,
    sessionVersion,
  );
}

/** PATCH /studies/{studyId}/notices/{noticeId} — 스터디장만 가능. */
export function updateNotice(
  studyId: number,
  noticeId: number,
  content: string,
  accessToken?: string,
  sessionVersion?: number,
): Promise<StudyNoticeMutationResult> {
  return requestNotice(
    `/api/v1/studies/${studyId}/notices/${noticeId}`,
    {
      method: 'PATCH',
      headers: { 'Content-Type': 'application/json' },
      body: JSON.stringify({ content }),
    },
    '공지를 수정하지 못했습니다.',
    accessToken,
    sessionVersion,
  );
}

/** DELETE /studies/{studyId}/notices/{noticeId} — 스터디장만 가능. */
export function deleteNotice(
  studyId: number,
  noticeId: number,
  accessToken?: string,
  sessionVersion?: number,
): Promise<StudyNoticeDeleteResult> {
  return requestNotice(
    `/api/v1/studies/${studyId}/notices/${noticeId}`,
    { method: 'DELETE' },
    '공지를 삭제하지 못했습니다.',
    accessToken,
    sessionVersion,
  );
}
