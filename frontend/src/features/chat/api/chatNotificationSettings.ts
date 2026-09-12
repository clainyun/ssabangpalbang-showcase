import { authenticatedFetch, rethrowSessionFailure } from '@/lib/authenticatedFetch';
import { useAuthStore } from '@/store/authStore';
import { ChatApiError } from '@/features/chat/types';

export interface ChatNotificationSetting {
  studyId: number;
  pushEnabled: boolean;
}

interface ApiEnvelope<T> {
  success: boolean;
  code: string;
  message: string;
  data: T | null;
}

async function requestChatNotificationSetting(
  studyId: number,
  method: 'GET' | 'PATCH',
  pushEnabled?: boolean,
): Promise<ChatNotificationSetting> {
  const { accessToken, sessionVersion } = useAuthStore.getState();

  let response: Response;
  try {
    response = await authenticatedFetch(
      `/api/v1/studies/${studyId}/chat/notification-settings`,
      {
        method,
        headers: {
          Accept: 'application/json',
          ...(method === 'PATCH' ? { 'Content-Type': 'application/json' } : {}),
        },
        body: method === 'PATCH' ? JSON.stringify({ pushEnabled }) : undefined,
      },
      { expectedSessionVersion: sessionVersion, fallbackAccessToken: accessToken ?? undefined },
    );
  } catch (error) {
    rethrowSessionFailure(error);
    throw new ChatApiError('NETWORK_ERROR', '네트워크 연결을 확인해 주세요.');
  }

  const body = (await response.json().catch(() => null)) as
    | ApiEnvelope<ChatNotificationSetting>
    | null;
  if (!response.ok || !body?.success || body.data === null) {
    throw new ChatApiError(
      body?.code ?? 'UNKNOWN',
      body?.message ?? '채팅 푸시 알림 설정을 불러오지 못했습니다.',
    );
  }
  return body.data;
}

export function getChatNotificationSetting(studyId: number) {
  return requestChatNotificationSetting(studyId, 'GET');
}

export function updateChatNotificationSetting(studyId: number, pushEnabled: boolean) {
  return requestChatNotificationSetting(studyId, 'PATCH', pushEnabled);
}
