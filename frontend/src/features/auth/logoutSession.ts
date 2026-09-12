import {
  AuthSessionChangedError,
  authenticatedFetch,
} from '@/lib/authenticatedFetch';
import { tokenStorage } from '@/lib/tokenStorage';
import {
  queueFcmTokenUnregister,
  suspendFcmTokenRegistration,
} from '@/features/notification/fcmToken';
import { useAuthStore } from '@/store/authStore';

const LOGOUT_CLEANUP_TIMEOUT_MS = 22_000;
const logoutPromises = new Map<number, Promise<void>>();

function assertLogoutSession(sessionVersion: number): void {
  if (useAuthStore.getState().sessionVersion !== sessionVersion) {
    throw new AuthSessionChangedError();
  }
}

async function withCleanupTimeout(
  operation: Promise<void>,
  timeoutMs: number,
): Promise<void> {
  let timeoutId: ReturnType<typeof setTimeout> | undefined;
  const timeout = new Promise<never>((_, reject) => {
    timeoutId = setTimeout(() => {
      reject(new Error('로그아웃 원격 정리 시간이 초과되었습니다.'));
    }, timeoutMs);
  });

  try {
    await Promise.race([operation, timeout]);
  } finally {
    if (timeoutId !== undefined) {
      clearTimeout(timeoutId);
    }
  }
}

async function cleanupRemoteSession(
  accessToken: string,
  sessionVersion: number,
): Promise<void> {
  try {
    await queueFcmTokenUnregister(accessToken, sessionVersion);
  } catch (error) {
    console.warn('[Auth] Failed to unregister FCM token during logout.', {
      errorType: error instanceof Error ? error.name : 'unknown',
    });
  }

  try {
    assertLogoutSession(sessionVersion);
    const refreshToken = await tokenStorage.getRefreshToken();
    assertLogoutSession(sessionVersion);
    if (!refreshToken) {
      return;
    }

    const response = await authenticatedFetch(
      '/api/v1/auth/logout',
      async () => {
        assertLogoutSession(sessionVersion);
        const latestRefreshToken =
          (await tokenStorage.getRefreshToken()) ?? refreshToken;
        assertLogoutSession(sessionVersion);
        return {
          method: 'POST',
          headers: { 'Content-Type': 'application/json' },
          body: JSON.stringify({ refreshToken: latestRefreshToken }),
        };
      },
      {
        expectedSessionVersion: sessionVersion,
        fallbackAccessToken: accessToken,
      },
    );

    if (!response.ok) {
      console.warn('[Auth] Remote logout returned an unsuccessful response.', {
        status: response.status,
      });
    }
  } catch (error) {
    console.warn('[Auth] Remote logout failed.', {
      errorType: error instanceof Error ? error.name : 'unknown',
    });
  }
}

async function performLogoutSession(sessionVersion: number): Promise<void> {
  const authState = useAuthStore.getState();
  if (authState.sessionVersion !== sessionVersion) {
    return;
  }

  const fallbackAccessToken =
    authState.accessToken ?? (await tokenStorage.getAccessToken());
  assertLogoutSession(sessionVersion);
  suspendFcmTokenRegistration(sessionVersion);

  try {
    if (fallbackAccessToken) {
      await withCleanupTimeout(
        cleanupRemoteSession(fallbackAccessToken, sessionVersion),
        LOGOUT_CLEANUP_TIMEOUT_MS,
      );
    }
  } catch (error) {
    console.warn('[Auth] Logout cleanup did not complete.', {
      errorType: error instanceof Error ? error.name : 'unknown',
    });
  } finally {
    await useAuthStore.getState().signOut(sessionVersion);
  }
}

export function logoutSession(): Promise<void> {
  const sessionVersion = useAuthStore.getState().sessionVersion;
  const existingLogout = logoutPromises.get(sessionVersion);
  if (existingLogout) {
    return existingLogout;
  }

  let trackedLogout: Promise<void>;
  trackedLogout = performLogoutSession(sessionVersion).finally(() => {
    if (logoutPromises.get(sessionVersion) === trackedLogout) {
      logoutPromises.delete(sessionVersion);
    }
  });
  logoutPromises.set(sessionVersion, trackedLogout);
  return trackedLogout;
}
