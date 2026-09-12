import { env } from '@/lib/env';
import {
  DEFAULT_FETCH_TIMEOUT_MS,
  fetchWithTimeout,
} from '@/lib/fetchWithTimeout';
import { tokenStorage } from '@/lib/tokenStorage';
import { useAuthStore } from '@/store/authStore';

interface ApiEnvelope<T> {
  success: boolean;
  code: string;
  message: string;
  data: T | null;
}

interface ReissuedTokens {
  accessToken: string;
  refreshToken: string;
}

type RequestInitFactory = () => RequestInit | Promise<RequestInit>;

export interface AuthenticatedFetchOptions {
  expectedSessionVersion?: number;
  fallbackAccessToken?: string;
  timeoutMs?: number;
}

export class AuthSessionExpiredError extends Error {
  constructor(message = '로그인이 만료되었습니다. 다시 로그인해 주세요.') {
    super(message);
    this.name = 'AuthSessionExpiredError';
  }
}

export class AuthSessionChangedError extends Error {
  constructor() {
    super('인증 상태가 변경되어 요청을 중단했습니다.');
    this.name = 'AuthSessionChangedError';
  }
}

// authenticatedFetch가 이미 로그아웃 처리까지 마치고 던지는 에러이므로, 호출부의
// try/catch에서 감싸지 말고 그대로 다시 던져 화면이 로그인 이동을 처리하게 합니다.
export function rethrowSessionFailure(error: unknown): void {
  if (error instanceof AuthSessionExpiredError || error instanceof AuthSessionChangedError) {
    throw error;
  }
}

const reissuePromises = new Map<string, Promise<ReissuedTokens>>();
let lastReissue: {
  expiredAccessToken: string;
  sessionVersion: number;
  tokens: ReissuedTokens;
} | null = null;

function apiUrl(path: string): string {
  return `${env.apiBaseUrl.replace(/\/$/, '')}${path.startsWith('/') ? path : `/${path}`}`;
}

function assertCurrentSession(expectedSessionVersion: number): void {
  if (useAuthStore.getState().sessionVersion !== expectedSessionVersion) {
    throw new AuthSessionChangedError();
  }
}

async function readEnvelope<T>(response: Response): Promise<ApiEnvelope<T> | null> {
  try {
    return (await response.json()) as ApiEnvelope<T>;
  } catch {
    return null;
  }
}

async function readAuthenticationFailure(
  response: Response,
): Promise<ApiEnvelope<unknown> | null> {
  if (response.status !== 401) {
    return null;
  }
  return readEnvelope<unknown>(response.clone());
}

async function expireSession(
  expectedSessionVersion: number,
  message?: string,
): Promise<never> {
  const expired = await useAuthStore
    .getState()
    .expireSession(expectedSessionVersion);
  if (!expired) {
    throw new AuthSessionChangedError();
  }
  throw new AuthSessionExpiredError(message);
}

async function performTokenReissue(
  expectedSessionVersion: number,
): Promise<ReissuedTokens> {
  assertCurrentSession(expectedSessionVersion);
  const refreshToken = await tokenStorage.getRefreshToken();
  assertCurrentSession(expectedSessionVersion);
  if (!refreshToken) {
    return expireSession(expectedSessionVersion);
  }

  const response = await fetchWithTimeout(
    apiUrl('/api/v1/auth/reissue'),
    {
      method: 'POST',
      headers: { 'Content-Type': 'application/json' },
      body: JSON.stringify({ refreshToken }),
    },
    DEFAULT_FETCH_TIMEOUT_MS,
  );
  assertCurrentSession(expectedSessionVersion);
  const payload = await readEnvelope<ReissuedTokens>(response);
  assertCurrentSession(expectedSessionVersion);

  if (
    response.ok &&
    payload?.success &&
    payload.data?.accessToken &&
    payload.data.refreshToken
  ) {
    const replaced = await useAuthStore
      .getState()
      .replaceTokens(
        payload.data.accessToken,
        payload.data.refreshToken,
        expectedSessionVersion,
      );
    if (!replaced) {
      throw new AuthSessionChangedError();
    }
    return payload.data;
  }

  // 재발급 실패는 사유(400/401/403/404/500)를 가리지 않고 재시도 없이 즉시 세션을 종료합니다.
  return expireSession(expectedSessionVersion, payload?.message);
}

function reissueTokens(
  expiredAccessToken: string,
  expectedSessionVersion: number,
): Promise<ReissuedTokens> {
  if (
    lastReissue?.expiredAccessToken === expiredAccessToken &&
    lastReissue.sessionVersion === expectedSessionVersion &&
    useAuthStore.getState().accessToken === lastReissue.tokens.accessToken
  ) {
    return Promise.resolve(lastReissue.tokens);
  }

  const reissueKey = expectedSessionVersion.toString();
  const existingReissue = reissuePromises.get(reissueKey);
  if (existingReissue) {
    return existingReissue;
  }

  let trackedReissue: Promise<ReissuedTokens>;
  trackedReissue = performTokenReissue(expectedSessionVersion)
    .then((tokens) => {
      lastReissue = {
        expiredAccessToken,
        sessionVersion: expectedSessionVersion,
        tokens,
      };
      return tokens;
    })
    .finally(() => {
      if (reissuePromises.get(reissueKey) === trackedReissue) {
        reissuePromises.delete(reissueKey);
      }
    });
  reissuePromises.set(reissueKey, trackedReissue);
  return trackedReissue;
}

async function resolveRequestInit(
  init: RequestInit | RequestInitFactory,
): Promise<RequestInit> {
  return typeof init === 'function' ? init() : init;
}

export async function authenticatedFetch(
  path: string,
  init: RequestInit | RequestInitFactory = {},
  options: AuthenticatedFetchOptions = {},
): Promise<Response> {
  const initialAuthState = useAuthStore.getState();
  const expectedSessionVersion =
    options.expectedSessionVersion ?? initialAuthState.sessionVersion;
  assertCurrentSession(expectedSessionVersion);

  const timeoutMs = options.timeoutMs ?? DEFAULT_FETCH_TIMEOUT_MS;
  const storedAccessToken =
    initialAuthState.accessToken ?? (await tokenStorage.getAccessToken());
  assertCurrentSession(expectedSessionVersion);
  const accessToken = storedAccessToken ?? options.fallbackAccessToken;
  if (!accessToken) {
    return expireSession(expectedSessionVersion);
  }

  const send = async (token: string): Promise<Response> => {
    assertCurrentSession(expectedSessionVersion);
    const requestInit = await resolveRequestInit(init);
    assertCurrentSession(expectedSessionVersion);
    const headers = new Headers(requestInit.headers);
    headers.set('Authorization', `Bearer ${token}`);

    const response = await fetchWithTimeout(
      apiUrl(path),
      {
        ...requestInit,
        headers,
      },
      timeoutMs,
    );
    assertCurrentSession(expectedSessionVersion);
    return response;
  };

  const firstResponse = await send(accessToken);
  const authenticationFailure = await readAuthenticationFailure(firstResponse);
  assertCurrentSession(expectedSessionVersion);
  if (authenticationFailure?.code === 'AUTH_ACCESS_TOKEN_INVALID') {
    return expireSession(expectedSessionVersion, authenticationFailure.message);
  }
  if (authenticationFailure?.code !== 'AUTH_ACCESS_TOKEN_EXPIRED') {
    return firstResponse;
  }

  const latestAccessToken = useAuthStore.getState().accessToken;
  const retryAccessToken =
    latestAccessToken && latestAccessToken !== accessToken
      ? latestAccessToken
      : (
          await reissueTokens(
            accessToken,
            expectedSessionVersion,
          )
        ).accessToken;
  const retryResponse = await send(retryAccessToken);
  const retryAuthenticationFailure =
    await readAuthenticationFailure(retryResponse);
  assertCurrentSession(expectedSessionVersion);
  if (
    retryAuthenticationFailure?.code === 'AUTH_ACCESS_TOKEN_INVALID' ||
    retryAuthenticationFailure?.code === 'AUTH_ACCESS_TOKEN_EXPIRED'
  ) {
    return expireSession(
      expectedSessionVersion,
      retryAuthenticationFailure.message,
    );
  }
  return retryResponse;
}
