import * as AuthSession from 'expo-auth-session';
import * as WebBrowser from 'expo-web-browser';

import { env } from '@/lib/env';

export type SocialProvider = 'KAKAO' | 'NAVER';

const AUTHORIZATION_ENDPOINTS: Record<SocialProvider, string> = {
  KAKAO: 'https://kauth.kakao.com/oauth/authorize',
  NAVER: 'https://nid.naver.com/oauth2.0/authorize',
};

const CALLBACK_PATHS: Record<SocialProvider, string> = {
  KAKAO: '/oauth/kakao/callback',
  NAVER: '/oauth/naver/callback',
};

const APP_REDIRECT_URI = AuthSession.makeRedirectUri({
  scheme: 'ssabangpalbang',
  path: 'oauth',
});

/** social-login이 요구하는 조건부 필드: KAKAO는 redirectUri, NAVER는 state. */
export interface SocialAuthorizationResult {
  authorizationCode: string;
  redirectUri: string;
  state: string;
}

export class OAuthCancelledError extends Error {
  constructor() {
    super('로그인이 취소되었습니다.');
    this.name = 'OAuthCancelledError';
  }
}

export class OAuthProviderNotConfiguredError extends Error {
  constructor(provider: SocialProvider) {
    super(`${provider} 간편 로그인이 아직 설정되지 않았습니다.`);
    this.name = 'OAuthProviderNotConfiguredError';
  }
}

function clientIdFor(provider: SocialProvider): string {
  return provider === 'KAKAO' ? env.kakaoRestApiKey : env.naverClientId;
}

/** PKCE는 끕니다 — 백엔드가 code_verifier 없이 순수 인가 코드만으로 토큰을 교환하기 때문입니다. */
export async function authorizeWithProvider(
  provider: SocialProvider,
): Promise<SocialAuthorizationResult> {
  const clientId = clientIdFor(provider);
  if (!clientId) {
    throw new OAuthProviderNotConfiguredError(provider);
  }

  // 카카오와 네이버 REST/web OAuth는 HTTPS callback을 사용해야 하므로,
  // 백엔드 callback이 인증 결과를 앱 scheme으로 되돌려 주는 중계 흐름을 사용합니다.
  const redirectUri = new URL(CALLBACK_PATHS[provider], env.apiBaseUrl).toString();
  const request = new AuthSession.AuthRequest({
    clientId,
    redirectUri,
    responseType: AuthSession.ResponseType.Code,
    usePKCE: false,
  });

  const result = await promptWithBridgeAsync(
    request,
    AUTHORIZATION_ENDPOINTS[provider],
  );

  if (result.type === 'cancel' || result.type === 'dismiss') {
    throw new OAuthCancelledError();
  }
  // state는 요청 시 자동 생성되어 request.state에 저장됩니다. 콜백으로 돌아온 state가
  // 이것과 다르면 다른 요청에 대한 응답이 섞여든 것이므로(CSRF) 신뢰하지 않습니다.
  if (result.type !== 'success' || !result.params.code || result.params.state !== request.state) {
    throw new Error('간편 로그인 인증에 실패했습니다.');
  }

  return { authorizationCode: result.params.code, redirectUri, state: request.state };
}

async function promptWithBridgeAsync(
  request: AuthSession.AuthRequest,
  authorizationEndpoint: string,
): Promise<AuthSession.AuthSessionResult> {
  const authorizationUrl = await request.makeAuthUrlAsync({
    authorizationEndpoint,
  });
  const result = await WebBrowser.openAuthSessionAsync(
    authorizationUrl,
    APP_REDIRECT_URI,
  );

  if (result.type === 'cancel' || result.type === 'dismiss') {
    throw new OAuthCancelledError();
  }
  if (result.type !== 'success') {
    throw new Error('간편 로그인 인증에 실패했습니다.');
  }

  return request.parseReturnUrl(result.url);
}
