import { env } from '@/lib/env';
import { fetchWithTimeout } from '@/lib/fetchWithTimeout';

interface ApiEnvelope<T> {
  success: boolean;
  code: string;
  message: string;
  data: T;
  timestamp: string;
}

interface AvailabilityData {
  /** true = 사용 가능(미중복), false = 이미 사용 중(중복) */
  available: boolean;
}

/** 중복확인 조회 실패(형식 오류/네트워크 등). 화면에서는 'error' 상태로만 취급합니다. */
export class AvailabilityCheckError extends Error {
  code: string;

  constructor(code: string, message: string) {
    super(message);
    this.code = code;
  }
}

// abort(디바운스 취소)로 인한 실패는 상위 훅이 무시하도록 원본 그대로 전파합니다.
function isAbortError(err: unknown): boolean {
  return (err as { name?: string })?.name === 'AbortError';
}

async function requestAvailability(path: string, signal?: AbortSignal): Promise<boolean> {
  let response: Response;
  try {
    response = await fetchWithTimeout(`${env.apiBaseUrl}${path}`, { method: 'GET', signal });
  } catch (err) {
    if (isAbortError(err)) throw err;
    throw new AvailabilityCheckError('NETWORK_ERROR', '네트워크 연결을 확인해주세요.');
  }

  const body = (await response.json().catch(() => null)) as ApiEnvelope<AvailabilityData | null> | null;

  if (!response.ok || !body?.success || typeof body.data?.available !== 'boolean') {
    const code = body?.code ?? 'UNKNOWN';
    throw new AvailabilityCheckError(code, body?.message || '중복확인에 실패했습니다.');
  }

  return body.data.available;
}

/**
 * GET /api/v1/auth/check-email?email=<이메일>
 * @returns 사용 가능(미중복)이면 true
 */
export function checkEmailAvailability(email: string, signal?: AbortSignal): Promise<boolean> {
  return requestAvailability(`/api/v1/auth/check-email?email=${encodeURIComponent(email)}`, signal);
}

/**
 * GET /api/v1/auth/check-nickname?nickname=<닉네임>
 * @returns 사용 가능(미중복)이면 true
 */
export function checkNicknameAvailability(nickname: string, signal?: AbortSignal): Promise<boolean> {
  return requestAvailability(
    `/api/v1/auth/check-nickname?nickname=${encodeURIComponent(nickname)}`,
    signal,
  );
}
