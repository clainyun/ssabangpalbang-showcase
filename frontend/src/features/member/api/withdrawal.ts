import {
  AuthSessionChangedError,
  authenticatedFetch,
  rethrowSessionFailure,
} from '@/lib/authenticatedFetch';
import { tokenStorage } from '@/lib/tokenStorage';
import { useAuthStore } from '@/store/authStore';

interface ApiEnvelope<T> {
  success: boolean;
  code: string;
  message: string;
  data: T | null;
}

export interface MemberWithdrawalResult {
  memberId: number;
  withdrawnAt: string;
}

export class MemberWithdrawalError extends Error {
  code: string;

  constructor(code: string, message: string) {
    super(message);
    this.name = 'MemberWithdrawalError';
    this.code = code;
  }
}

function assertCurrentSession(expectedSessionVersion: number): void {
  if (useAuthStore.getState().sessionVersion !== expectedSessionVersion) {
    throw new AuthSessionChangedError();
  }
}

export async function withdrawMember(confirmationText: string): Promise<MemberWithdrawalResult> {
  const { accessToken, sessionVersion } = useAuthStore.getState();

  let response: Response;
  try {
    response = await authenticatedFetch(
      '/api/v1/members/me',
      async () => {
        assertCurrentSession(sessionVersion);
        const refreshToken = await tokenStorage.getRefreshToken();
        assertCurrentSession(sessionVersion);

        if (!refreshToken) {
          throw new MemberWithdrawalError(
            'AUTH_REFRESH_TOKEN_MISSING',
            '로그인 정보를 확인할 수 없어요. 다시 로그인해 주세요.',
          );
        }

        return {
          method: 'DELETE',
          headers: {
            Accept: 'application/json',
            'Content-Type': 'application/json',
          },
          body: JSON.stringify({
            refreshToken,
            confirmationText,
          }),
        };
      },
      {
        expectedSessionVersion: sessionVersion,
        fallbackAccessToken: accessToken ?? undefined,
      },
    );
  } catch (error) {
    rethrowSessionFailure(error);
    if (error instanceof MemberWithdrawalError) {
      throw error;
    }
    throw new MemberWithdrawalError(
      'NETWORK_ERROR',
      '서버에 연결할 수 없어요. 네트워크 상태를 확인해 주세요.',
    );
  }

  const body = (await response
    .json()
    .catch(() => null)) as ApiEnvelope<MemberWithdrawalResult> | null;

  if (!response.ok || !body?.success || body.data === null) {
    throw new MemberWithdrawalError(
      body?.code ?? 'UNKNOWN',
      body?.message ?? '회원 탈퇴를 완료하지 못했어요. 잠시 후 다시 시도해 주세요.',
    );
  }

  return body.data;
}
