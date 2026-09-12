import { env } from '@/lib/env';

export interface SignupRequest {
  email: string;
  password: string;
  nickname: string;
}

export interface SignupResponseData {
  memberId: number;
  email: string;
  nickname: string;
  selectedCharacterId: 'PALBANG';
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

export type SignupErrorField = 'email' | 'password' | 'nickname';

/** field 가 있으면 해당 입력란 아래에, 없으면 폼 하단 공통 에러로 표시합니다. */
export class SignupError extends Error {
  code: string;
  field?: SignupErrorField;

  constructor(code: string, message: string, field?: SignupErrorField) {
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

function toSignupErrorField(field: string): SignupErrorField | undefined {
  return field === 'email' || field === 'password' || field === 'nickname' ? field : undefined;
}

export async function signup(request: SignupRequest): Promise<SignupResponseData> {
  let response: Response;
  try {
    response = await fetch(`${env.apiBaseUrl}/api/v1/auth/signup`, {
      method: 'POST',
      headers: { 'Content-Type': 'application/json' },
      body: JSON.stringify(request),
    });
  } catch {
    // fetch 자체가 실패한 경우(오프라인, DNS 실패 등)만 여기로 옵니다.
    // 서버가 응답을 준 경우(4xx/5xx 포함)는 아래 !response.ok 분기에서 따로 처리합니다.
    throw new SignupError('NETWORK_ERROR', '네트워크 연결을 확인해주세요.');
  }

  const body = (await response.json().catch(() => null)) as ApiEnvelope<
    SignupResponseData | ValidationErrorData | null
  > | null;

  if (!response.ok || !body?.success) {
    const code = body?.code ?? 'UNKNOWN';

    if (code === 'COMMON_INVALID_REQUEST' && isValidationErrorData(body?.data)) {
      throw new SignupError(code, body.data.reason, toSignupErrorField(body.data.field));
    }
    // 이메일/닉네임 중복은 서버 메시지를 우선 쓰고, 응답에 메시지가 없을 때만 폴백 문구를 씁니다.
    if (code === 'AUTH_EMAIL_DUPLICATED') {
      throw new SignupError(code, body?.message || '이미 사용 중인 이메일입니다.', 'email');
    }
    if (code === 'AUTH_NICKNAME_DUPLICATED') {
      throw new SignupError(code, body?.message || '이미 사용 중인 닉네임입니다.', 'nickname');
    }
    throw new SignupError(code, body?.message || '회원가입에 실패했습니다. 잠시 후 다시 시도해주세요.');
  }

  return body.data as SignupResponseData;
}
