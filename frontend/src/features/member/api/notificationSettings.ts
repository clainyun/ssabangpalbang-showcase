import { authenticatedFetch, rethrowSessionFailure } from '@/lib/authenticatedFetch';
import { useAuthStore } from '@/store/authStore';

interface ApiEnvelope<T> {
  success: boolean;
  code: string;
  message: string;
  data: T | null;
}

export interface NotificationSettings {
  serviceNotificationAgreed: boolean;
  adNotificationAgreed: boolean;
}

export type NotificationSettingsUpdateRequest =
  | {
      serviceNotificationAgreed: boolean;
      adNotificationAgreed?: boolean;
    }
  | {
      serviceNotificationAgreed?: boolean;
      adNotificationAgreed: boolean;
    };

export interface NotificationSettingsUpdateResult extends NotificationSettings {
  updatedAt: string;
}

export class NotificationSettingsApiError extends Error {
  code: string;

  constructor(code: string, message: string) {
    super(message);
    this.name = 'NotificationSettingsApiError';
    this.code = code;
  }
}

async function requestNotificationSettings<T>(
  method: 'GET' | 'PATCH',
  expectedSessionVersion: number,
  request?: NotificationSettingsUpdateRequest,
  signal?: AbortSignal,
): Promise<T> {
  const accessToken = useAuthStore.getState().accessToken;
  let response: Response;

  try {
    response = await authenticatedFetch(
      '/api/v1/members/me/notification-settings',
      {
        method,
        headers: {
          Accept: 'application/json',
          ...(request ? { 'Content-Type': 'application/json' } : {}),
        },
        body: request ? JSON.stringify(request) : undefined,
        signal,
      },
      {
        expectedSessionVersion,
        fallbackAccessToken: accessToken ?? undefined,
      },
    );
  } catch (error) {
    rethrowSessionFailure(error);
    if (error instanceof Error && error.name === 'AbortError') {
      throw error;
    }
    throw new NotificationSettingsApiError(
      'NETWORK_ERROR',
      '서버에 연결할 수 없어요. 네트워크 상태를 확인해 주세요.',
    );
  }

  const body = (await response.json().catch(() => null)) as ApiEnvelope<T> | null;

  if (!response.ok || !body?.success || body.data === null) {
    throw new NotificationSettingsApiError(
      body?.code ?? 'UNKNOWN',
      body?.message ??
        (method === 'GET' ? '알림 설정을 불러오지 못했어요.' : '알림 설정을 저장하지 못했어요.'),
    );
  }

  return body.data;
}

export function getNotificationSettings(
  expectedSessionVersion: number,
  signal?: AbortSignal,
): Promise<NotificationSettings> {
  return requestNotificationSettings('GET', expectedSessionVersion, undefined, signal);
}

export function updateNotificationSettings(
  request: NotificationSettingsUpdateRequest,
  expectedSessionVersion: number = useAuthStore.getState().sessionVersion,
): Promise<NotificationSettingsUpdateResult> {
  return requestNotificationSettings('PATCH', expectedSessionVersion, request);
}
