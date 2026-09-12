import { env } from '@/lib/env';

export interface SocialSignupRequest {
  socialSignupToken: string;
  nickname: string;
  email?: string; // emailRequired였던 경우만
}

export interface SocialSignupResponseData {
  memberId: number;
  email: string;
  nickname: string;
  provider: string;
  selectedCharacterId: string;
  onboardingCompleted: false;
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

interface FieldErrorData {
  field: string;
  reason: string;
}

function isFieldErrorData(data: unknown): data is FieldErrorData {
  return typeof data === 'object' && data !== null && 'field' in data;
}

export type SocialSignupErrorField = 'email' | 'nickname';

function toSocialSignupErrorField(field: string): SocialSignupErrorField | undefined {
  return field === 'email' || field === 'nickname' ? field : undefined;
}

/** redirectToLogin이 true면 이 에러는 화면 안에서 고칠 수 없는 상태(임시 토큰 무효/만료 등)라
 * 로그인 화면으로 돌려보내야 합니다. false면 field 아래에 인라인 에러로 표시합니다. */
export class SocialSignupError extends Error {
  code: string;
  field?: SocialSignupErrorField;
  redirectToLogin: boolean;

  constructor(
    code: string,
    message: string,
    options?: { field?: SocialSignupErrorField; redirectToLogin?: boolean },
  ) {
    super(message);
    this.code = code;
    this.field = options?.field;
    this.redirectToLogin = options?.redirectToLogin ?? false;
  }
}

export async function socialSignup(
  request: SocialSignupRequest,
): Promise<SocialSignupResponseData> {
  let response: Response;
  try {
    response = await fetch(`${env.apiBaseUrl}/api/v1/auth/social-signup`, {
      method: 'POST',
      headers: { 'Content-Type': 'application/json' },
      body: JSON.stringify(request),
    });
  } catch {
    throw new SocialSignupError('NETWORK_ERROR', '네트워크 연결을 확인해주세요.');
  }

  const body = (await response.json().catch(() => null)) as ApiEnvelope<
    SocialSignupResponseData | FieldErrorData | null
  > | null;

  if (!response.ok || !body?.success) {
    const code = body?.code ?? 'UNKNOWN';

    if (code === 'AUTH_SOCIAL_SIGNUP_TOKEN_INVALID') {
      throw new SocialSignupError(
        code,
        body?.message || '소셜 회원가입 정보가 유효하지 않습니다. 처음부터 다시 시도해주세요.',
        { redirectToLogin: true },
      );
    }
    if (code === 'AUTH_SOCIAL_SIGNUP_TOKEN_EXPIRED') {
      throw new SocialSignupError(
        code,
        body?.message || '소셜 인증 정보가 만료되었습니다. 다시 로그인해 주세요.',
        { redirectToLogin: true },
      );
    }
    if (code === 'AUTH_SOCIAL_ACCOUNT_ALREADY_EXISTS') {
      throw new SocialSignupError(
        code,
        body?.message || '이미 가입된 간편 로그인 계정입니다.',
        { redirectToLogin: true },
      );
    }
    if (code === 'AUTH_SOCIAL_EMAIL_REQUIRED') {
      throw new SocialSignupError(code, body?.message || '회원가입에 사용할 이메일을 입력해 주세요.', {
        field: 'email',
      });
    }
    if (code === 'AUTH_EMAIL_DUPLICATED') {
      throw new SocialSignupError(
        code,
        '이미 가입된 이메일입니다. 기존 로그인 방식을 이용해주세요.',
        { field: 'email' },
      );
    }
    if (code === 'AUTH_NICKNAME_DUPLICATED') {
      throw new SocialSignupError(code, body?.message || '이미 사용 중인 닉네임입니다.', {
        field: 'nickname',
      });
    }
    if (code === 'COMMON_INVALID_REQUEST' && isFieldErrorData(body?.data)) {
      throw new SocialSignupError(code, body.data.reason, {
        field: toSocialSignupErrorField(body.data.field),
      });
    }
    throw new SocialSignupError(
      code,
      body?.message || '회원가입에 실패했습니다. 잠시 후 다시 시도해주세요.',
    );
  }

  return body.data as SocialSignupResponseData;
}
