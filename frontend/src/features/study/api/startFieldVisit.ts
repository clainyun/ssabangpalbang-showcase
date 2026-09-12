import * as Crypto from 'expo-crypto';

import { authenticatedFetch, rethrowSessionFailure } from '@/lib/authenticatedFetch';
import { useAuthStore } from '@/store/authStore';

import type { ApiEnvelope } from './types';

export interface FieldVisitStartSession {
  sessionId: number;
  status: string;
  startedAt: string;
}

export interface FieldVisitStartParticipant {
  participantId: number;
  status: string;
  startedAt: string;
}

/** POST /api/v1/studies/{studyId}/field-visit/start 응답 (docs/API.md "GPS 검증 후 임장 시작"). */
export interface FieldVisitStartResult {
  studyId: number;
  apartmentId: number;
  distanceMeters: number;
  allowedRadiusMeters: number;
  session: FieldVisitStartSession;
  participant: FieldVisitStartParticipant;
  checklistGenerated: boolean;
}

/** 422 FIELD_VISIT_OUT_OF_RANGE 등 data에 부가 정보가 실려 오는 에러가 있어 함께 보존합니다. */
export class FieldVisitStartApiError extends Error {
  code: string;
  data: unknown;

  constructor(code: string, message: string, data?: unknown) {
    super(message);
    this.name = 'FieldVisitStartApiError';
    this.code = code;
    this.data = data;
  }
}

export async function startFieldVisit(
  studyId: number,
  latitude: number,
  longitude: number,
  scheduleOverrideConfirmed = false,
): Promise<FieldVisitStartResult> {
  const { accessToken, sessionVersion } = useAuthStore.getState();

  let response: Response;
  try {
    response = await authenticatedFetch(
      `/api/v1/studies/${studyId}/field-visit/start`,
      {
        method: 'POST',
        headers: { 'Content-Type': 'application/json' },
        body: JSON.stringify({
          latitude,
          longitude,
          clientRequestId: Crypto.randomUUID(),
          scheduleOverrideConfirmed,
        }),
      },
      { expectedSessionVersion: sessionVersion, fallbackAccessToken: accessToken ?? undefined },
    );
  } catch (error) {
    rethrowSessionFailure(error);
    throw new FieldVisitStartApiError('NETWORK_ERROR', '네트워크 연결을 확인해 주세요.');
  }

  const body = (await response
    .json()
    .catch(() => null)) as ApiEnvelope<FieldVisitStartResult> | null;

  if (!response.ok || !body?.success || body.data === null) {
    throw new FieldVisitStartApiError(
      body?.code ?? 'UNKNOWN',
      body?.message ?? '임장을 시작하지 못했습니다.',
      body?.data,
    );
  }

  return body.data;
}
