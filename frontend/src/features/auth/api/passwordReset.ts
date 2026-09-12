import { env } from '@/lib/env';

export interface PasswordResetRequestBody {
  email: string;
}

export interface PasswordResetConfirmBody {
  email: string;
  code: string;
  newPassword: string;
}

export interface PasswordResetVerifyBody {
  email: string;
  code: string;
}

interface PasswordResetVerifyData {
  /** true = 인증코드 일치, false = 불일치(200 응답) */
  valid: boolean;
}

interface ApiEnvelope<T> {
  success: boolean;
  code: string;
  message: string;
  data: T;
  timestamp: string;
}

interface ValidationErrorData {
  field: string;
  reason: string;
}

export type PasswordResetErrorField = 'email' | 'code' | 'newPassword';

/** field 가 있으면 해당 입력란 아래에, 없으면 폼 하단 공통 에러로 표시합니다. */
export class PasswordResetError extends Error {
  code: string;
  field?: PasswordResetErrorField;

  constructor(code: string, message: string, field?: PasswordResetErrorField) {
    super(message);
    this.code = code;
    this.field = field;
  }
}

function isValidationErrorData(data: unknown): data is ValidationErrorData {
  return (
    typeof data === 'object' &&
    data !== null &&
    'field' in data &&
    'reason' in data &&
    typeof (data as ValidationErrorData).field === 'string'
  );
}

function toPasswordResetErrorField(field: string): PasswordResetErrorField | undefined {
  return field === 'email' || field === 'code' || field === 'newPassword' ? field : undefined;
}

/**
 * POST /api/v1/auth/password-reset/request
 * 재설정 코드 발급을 요청합니다. 계정 존재 여부와 무관하게 항상 200(AUTH_PASSWORD_RESET_REQUESTED)이며,
 * 이메일 형식 오류(400 COMMON_INVALID_REQUEST)만 필드 에러로 전달합니다(계정 열거 방지).
 */
export async function requestPasswordResetCode(email: string): Promise<void> {
  let response: Response;
  try {
    response = await fetch(`${env.apiBaseUrl}/api/v1/auth/password-reset/request`, {
      method: 'POST',
      headers: { 'Content-Type': 'application/json' },
      body: JSON.stringify({ email } satisfies PasswordResetRequestBody),
    });
  } catch {
    // fetch 자체가 실패한 경우(오프라인, DNS 실패 등)만 여기로 옵니다.
    // 서버가 응답을 준 경우(4xx/5xx 포함)는 아래 !response.ok 분기에서 처리합니다.
    throw new PasswordResetError('NETWORK_ERROR', '네트워크 연결을 확인해주세요.');
  }

  const body = (await response.json().catch(() => null)) as ApiEnvelope<
    ValidationErrorData | null
  > | null;

  if (!response.ok || !body?.success) {
    const code = body?.code ?? 'UNKNOWN';

    if (code === 'COMMON_INVALID_REQUEST' && isValidationErrorData(body?.data)) {
      throw new PasswordResetError(code, body.data.reason, toPasswordResetErrorField(body.data.field) ?? 'email');
    }
    throw new PasswordResetError(
      code,
      body?.message || '코드 요청에 실패했습니다. 잠시 후 다시 시도해주세요.',
    );
  }
}

/**
 * POST /api/v1/auth/password-reset/verify
 * 새 비밀번호 단계 진입 전, 이메일로 받은 코드가 맞는지 사전 확인합니다.
 * - 200 { valid: true }  → 코드 일치 (true 반환)
 * - 200 { valid: false } → 코드 불일치 (false 반환)
 * - 400 AUTH_PASSWORD_RESET_CODE_INVALID → 시도 5회 초과 (PasswordResetError 로 전파, field='code')
 */
export async function verifyPasswordResetCode(params: PasswordResetVerifyBody): Promise<boolean> {
  let response: Response;
  try {
    response = await fetch(`${env.apiBaseUrl}/api/v1/auth/password-reset/verify`, {
      method: 'POST',
      headers: { 'Content-Type': 'application/json' },
      body: JSON.stringify(params satisfies PasswordResetVerifyBody),
    });
  } catch {
    throw new PasswordResetError('NETWORK_ERROR', '네트워크 연결을 확인해주세요.');
  }

  const body = (await response.json().catch(() => null)) as ApiEnvelope<
    PasswordResetVerifyData | ValidationErrorData | null
  > | null;

  if (!response.ok || !body?.success) {
    const code = body?.code ?? 'UNKNOWN';

    if (code === 'COMMON_INVALID_REQUEST' && isValidationErrorData(body?.data)) {
      throw new PasswordResetError(
        code,
        body.data.reason,
        toPasswordResetErrorField(body.data.field) ?? 'code',
      );
    }
    // 시도 횟수 초과(5회) — 코드 필드 에러로 표시하고, 코드 재요청을 유도합니다.
    if (code === 'AUTH_PASSWORD_RESET_CODE_INVALID') {
      throw new PasswordResetError(
        code,
        body?.message || '인증 시도 횟수를 초과했어요. 코드를 다시 요청해 주세요.',
        'code',
      );
    }
    throw new PasswordResetError(
      code,
      body?.message || '코드 확인에 실패했습니다. 잠시 후 다시 시도해주세요.',
    );
  }

  const data = body.data as PasswordResetVerifyData;
  return data?.valid === true;
}

/**
 * POST /api/v1/auth/password-reset/confirm
 * 코드 + 새 비밀번호로 비밀번호를 재설정합니다.
 * 200(AUTH_PASSWORD_RESET_SUCCESS) 성공, 그 외 코드는 PasswordResetError 로 매핑합니다.
 */
export async function confirmPasswordReset(params: PasswordResetConfirmBody): Promise<void> {
  let response: Response;
  try {
    response = await fetch(`${env.apiBaseUrl}/api/v1/auth/password-reset/confirm`, {
      method: 'POST',
      headers: { 'Content-Type': 'application/json' },
      body: JSON.stringify(params satisfies PasswordResetConfirmBody),
    });
  } catch {
    throw new PasswordResetError('NETWORK_ERROR', '네트워크 연결을 확인해주세요.');
  }

  const body = (await response.json().catch(() => null)) as ApiEnvelope<
    ValidationErrorData | null
  > | null;

  if (!response.ok || !body?.success) {
    const code = body?.code ?? 'UNKNOWN';

    // 비밀번호 정책 위반 등: data 에 { field, reason } 이 있으면 해당 필드(주로 newPassword) 에러로 표시합니다.
    if (code === 'COMMON_INVALID_REQUEST' && isValidationErrorData(body?.data)) {
      throw new PasswordResetError(
        code,
        body.data.reason,
        toPasswordResetErrorField(body.data.field) ?? 'newPassword',
      );
    }
    // 코드 없음/만료/불일치/시도초과 — 코드 필드 에러로 표시합니다.
    if (code === 'AUTH_PASSWORD_RESET_CODE_INVALID') {
      throw new PasswordResetError(
        code,
        body?.message || '코드가 올바르지 않거나 만료됐어요. 다시 요청해 주세요.',
        'code',
      );
    }
    // 탈퇴 회원 / 소셜 전용 계정 — 특정 필드에 붙일 수 없는 공통 실패로 폼 하단에 표시합니다.
    if (code === 'AUTH_MEMBER_WITHDRAWN') {
      throw new PasswordResetError(code, body?.message || '탈퇴 처리된 회원입니다.');
    }
    if (code === 'AUTH_PASSWORD_LOGIN_NOT_AVAILABLE') {
      throw new PasswordResetError(
        code,
        body?.message || '소셜 로그인 계정은 비밀번호가 없어요. 카카오/네이버로 로그인해 주세요.',
      );
    }
    throw new PasswordResetError(
      code,
      body?.message || '비밀번호 변경에 실패했습니다. 잠시 후 다시 시도해주세요.',
    );
  }
}
