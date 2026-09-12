import { authenticatedFetch, rethrowSessionFailure } from '@/lib/authenticatedFetch';

export type SttJobStatus = 'PENDING' | 'PROCESSING' | 'DONE' | 'FAILED';

interface ApiEnvelope<T> {
  success: boolean;
  code: string;
  message: string;
  data: T | null;
}

export interface SttRequestResult {
  sttId: string;
  studyId: number;
  sessionId: number;
  audioFileId: number;
  checklistItemId: number;
  status: SttJobStatus;
  sourceId: number | null;
  requestedAt: string;
}

export interface SttStatusResult {
  sttId: string;
  studyId: number;
  checklistItemId: number;
  status: SttJobStatus;
  sourceId: number | null;
  textContent: string | null;
  failReason: string | null;
  retryable: boolean;
  requestedAt: string;
  completedAt: string | null;
}

export interface SttRetryResult {
  sttId: string;
  studyId: number;
  checklistItemId: number;
  status: SttJobStatus;
  sourceId: number | null;
  retryRequestedAt: string;
}

export class SttApiError extends Error {
  constructor(
    public readonly code: string,
    message: string,
    public readonly httpStatus?: number,
  ) {
    super(message);
    this.name = 'SttApiError';
  }
}

async function request<T>(
  path: string,
  init: RequestInit,
  fallbackMessage: string,
  isValid: (value: unknown) => value is T,
  expectedSessionVersion?: number,
): Promise<T> {
  let response: Response;
  try {
    response = await authenticatedFetch(
      path,
      {
        ...init,
        headers: { Accept: 'application/json', ...(init.headers ?? {}) },
      },
      { expectedSessionVersion },
    );
  } catch (error) {
    rethrowSessionFailure(error);
    throw new SttApiError('NETWORK_ERROR', '네트워크 연결을 확인해 주세요.');
  }

  const body = (await response.json().catch(() => null)) as ApiEnvelope<unknown> | null;
  if (!response.ok || !body?.success || body.data === null) {
    throw new SttApiError(
      body?.code ?? 'UNKNOWN',
      body?.message ?? fallbackMessage,
      response.status,
    );
  }
  if (!isValid(body.data)) {
    throw new SttApiError('INVALID_RESPONSE', fallbackMessage, response.status);
  }

  return body.data;
}

export function requestStt(
  studyId: number,
  audioFileId: number,
  checklistItemId: number,
  clientRequestId: string,
  expectedSessionVersion?: number,
): Promise<SttRequestResult> {
  return request<SttRequestResult>(
    `/api/v1/studies/${studyId}/field-visit/stt`,
    {
      method: 'POST',
      headers: { 'Content-Type': 'application/json' },
      body: JSON.stringify({ audioFileId, checklistItemId, clientRequestId }),
    },
    '음성 변환을 시작하지 못했습니다.',
    isSttRequestResult,
    expectedSessionVersion,
  );
}

export function getSttStatus(
  studyId: number,
  sttId: string,
  expectedSessionVersion?: number,
): Promise<SttStatusResult> {
  return request<SttStatusResult>(
    `/api/v1/studies/${studyId}/field-visit/stt/${encodeURIComponent(sttId)}`,
    { method: 'GET' },
    '음성 변환 상태를 확인하지 못했습니다.',
    isSttStatusResult,
    expectedSessionVersion,
  );
}

export function retryStt(
  studyId: number,
  sttId: string,
  clientRequestId: string,
  expectedSessionVersion?: number,
): Promise<SttRetryResult> {
  return request<SttRetryResult>(
    `/api/v1/studies/${studyId}/field-visit/stt/${encodeURIComponent(sttId)}/retry`,
    {
      method: 'POST',
      headers: { 'Content-Type': 'application/json' },
      body: JSON.stringify({ clientRequestId }),
    },
    '음성 변환을 다시 시작하지 못했습니다.',
    isSttRetryResult,
    expectedSessionVersion,
  );
}

const STT_STATUSES = new Set<SttJobStatus>(['PENDING', 'PROCESSING', 'DONE', 'FAILED']);

function isObject(value: unknown): value is Record<string, unknown> {
  return typeof value === 'object' && value !== null;
}

function isPositiveInteger(value: unknown): value is number {
  return Number.isInteger(value) && Number(value) > 0;
}

function isNullablePositiveInteger(value: unknown): value is number | null {
  return value === null || isPositiveInteger(value);
}

function isStatus(value: unknown): value is SttJobStatus {
  return typeof value === 'string' && STT_STATUSES.has(value as SttJobStatus);
}

function isNonEmptyString(value: unknown): value is string {
  return typeof value === 'string' && value.length > 0;
}

function isNullableString(value: unknown): value is string | null {
  return value === null || typeof value === 'string';
}

function isSttRequestResult(value: unknown): value is SttRequestResult {
  if (!isObject(value)) return false;
  return (
    isNonEmptyString(value.sttId) &&
    isPositiveInteger(value.studyId) &&
    isPositiveInteger(value.sessionId) &&
    isPositiveInteger(value.audioFileId) &&
    isPositiveInteger(value.checklistItemId) &&
    isStatus(value.status) &&
    isNullablePositiveInteger(value.sourceId) &&
    isNonEmptyString(value.requestedAt)
  );
}

function isSttStatusResult(value: unknown): value is SttStatusResult {
  if (!isObject(value)) return false;
  return (
    isNonEmptyString(value.sttId) &&
    isPositiveInteger(value.studyId) &&
    isPositiveInteger(value.checklistItemId) &&
    isStatus(value.status) &&
    isNullablePositiveInteger(value.sourceId) &&
    isNullableString(value.textContent) &&
    isNullableString(value.failReason) &&
    typeof value.retryable === 'boolean' &&
    isNonEmptyString(value.requestedAt) &&
    isNullableString(value.completedAt)
  );
}

function isSttRetryResult(value: unknown): value is SttRetryResult {
  if (!isObject(value)) return false;
  return (
    isNonEmptyString(value.sttId) &&
    isPositiveInteger(value.studyId) &&
    isPositiveInteger(value.checklistItemId) &&
    isStatus(value.status) &&
    isNullablePositiveInteger(value.sourceId) &&
    isNonEmptyString(value.retryRequestedAt)
  );
}
