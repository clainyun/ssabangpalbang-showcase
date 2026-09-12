import { env } from '@/lib/env';

export interface LoginRequest {
  email: string;
  password: string;
}

export interface LoginResponseData {
  memberId: number;
  email: string;
  nickname: string;
  profileImageUrl: string | null;
  selectedCharacterId: string;
  onboardingCompleted: boolean;
  accessToken: string;
  refreshToken: string;
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

export type LoginErrorField = 'email' | 'password';

/** field 가 있으면 해당 입력란 아래에, 없으면 폼 하단 공통 에러로 표시 */
export class LoginError extends Error {
  code: string;
  field?: LoginErrorField;

  constructor(code: string, message: string, field?: LoginErrorField) {
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

function toLoginErrorField(field: string): LoginErrorField | undefined {
  return field === 'email' || field === 'password' ? field : undefined;
}

export async function login(request: LoginRequest): Promise<LoginResponseData> {
  let response: Response;
  try {
    response = await fetch(`${env.apiBaseUrl}/api/v1/auth/login`, {
      method: 'POST',
      headers: { 'Content-Type': 'application/json' },
      body: JSON.stringify(request),
    });
  } catch {
    // fetch 자체가 실패한 경우(오프라인, DNS 실패 등)만 여기로 옴
    // 서버가 응답을 준 경우(4xx/5xx 포함)는 아래 !response.ok 분기에서 따로 처리
    throw new LoginError('NETWORK_ERROR', '네트워크 연결을 확인해주세요.');
  }

  const body = (await response.json().catch(() => null)) as ApiEnvelope<
    LoginResponseData | ValidationErrorData | null
  > | null;

  if (!response.ok || !body?.success) {
    const code = body?.code ?? 'UNKNOWN';

    if (code === 'COMMON_INVALID_REQUEST' && isValidationErrorData(body?.data)) {
      throw new LoginError(code, body.data.reason, toLoginErrorField(body.data.field));
    }
    // 보안상 이메일 존재 여부를 노출하지 않도록 필드 구분 없이 폼 하단 공통 에러로만 표시합니다.
    if (code === 'AUTH_LOGIN_FAILED') {
      throw new LoginError(code, body?.message || '이메일 또는 비밀번호를 확인해 주세요.');
    }
    if (code === 'AUTH_MEMBER_WITHDRAWN') {
      throw new LoginError(code, body?.message || '탈퇴 처리된 회원입니다.');
    }
    if (code === 'AUTH_PASSWORD_LOGIN_NOT_AVAILABLE') {
      throw new LoginError(code, body?.message || '간편 로그인을 이용해 주세요.');
    }
    throw new LoginError(code, body?.message || '로그인에 실패했습니다. 잠시 후 다시 시도해주세요.');
  }

  return body.data as LoginResponseData;
}
