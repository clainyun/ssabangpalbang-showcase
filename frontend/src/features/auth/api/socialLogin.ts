import { env } from '@/lib/env';
import type { SocialProvider } from '@/features/auth/oauth';

export interface SocialLoginRequest {
  provider: SocialProvider;
  authorizationCode: string;
  redirectUri?: string; // KAKAO일 때 필수
  state?: string; // NAVER일 때 필수
}

export interface SocialLoginExistingMemberData {
  signupRequired: false;
  memberId: number;
  email: string;
  nickname: string;
  profileImageUrl: string | null;
  selectedCharacterId: string;
  onboardingCompleted: boolean;
  accessToken: string;
  refreshToken: string;
}

export interface SocialSignupRequiredData {
  signupRequired: true;
  provider: SocialProvider;
  email: string | null;
  emailRequired: boolean;
  socialSignupToken: string;
  expiresIn: number;
}

export type SocialLoginResponseData = SocialLoginExistingMemberData | SocialSignupRequiredData;

interface ApiEnvelope<T> {
  success: boolean;
  code: string;
  message: string;
  data: T;
  timestamp: string;
}

/** 필드 구분 없이 폼 하단 공통 에러로만 표시합니다 (일반 로그인의 AUTH_LOGIN_FAILED와 동일한 방식). */
export class SocialLoginError extends Error {
  code: string;

  constructor(code: string, message: string) {
    super(message);
    this.code = code;
  }
}

export async function socialLogin(
  request: SocialLoginRequest,
): Promise<SocialLoginResponseData> {
  let response: Response;
  try {
    response = await fetch(`${env.apiBaseUrl}/api/v1/auth/social-login`, {
      method: 'POST',
      headers: { 'Content-Type': 'application/json' },
      body: JSON.stringify(request),
    });
  } catch {
    throw new SocialLoginError('NETWORK_ERROR', '네트워크 연결을 확인해주세요.');
  }

  const body = (await response.json().catch(() => null)) as ApiEnvelope<
    SocialLoginResponseData | null
  > | null;

  if (!response.ok || !body?.success) {
    const code = body?.code ?? 'UNKNOWN';

    if (code === 'AUTH_SOCIAL_AUTHENTICATION_FAILED') {
      throw new SocialLoginError(code, body?.message || '간편 로그인 인증에 실패했습니다.');
    }
    if (code === 'AUTH_MEMBER_WITHDRAWN') {
      throw new SocialLoginError(code, body?.message || '탈퇴 처리된 회원입니다.');
    }
    if (code === 'AUTH_SOCIAL_PROVIDER_UNAVAILABLE') {
      throw new SocialLoginError(
        code,
        body?.message || '일시적인 오류입니다. 잠시 후 다시 시도해주세요.',
      );
    }
    // AUTH_SOCIAL_PROVIDER_INVALID(지원 안 하는 제공자)는 프론트가 KAKAO/NAVER만 보내므로
    // 정상 플로우에서는 발생하지 않는 방어 코드 — 별도 문구 없이 공통 메시지로 처리합니다.
    throw new SocialLoginError(
      code,
      body?.message || '간편 로그인에 실패했습니다. 잠시 후 다시 시도해주세요.',
    );
  }

  return body.data as SocialLoginResponseData;
}
