import { authenticatedFetch, rethrowSessionFailure } from '@/lib/authenticatedFetch';
import { useAuthStore } from '@/store/authStore';
import { ChatApiError } from '@/features/chat/types';

export interface ChatUnreadCount {
  studyId: number;
  unreadCount: number;
  lastReadAt: string | null;
}

interface ApiEnvelope<T> {
  success: boolean;
  code: string;
  message: string;
  data: T;
}

export async function getChatUnreadCount(studyId: number): Promise<ChatUnreadCount> {
  const { accessToken, sessionVersion } = useAuthStore.getState();

  let response: Response;
  try {
    response = await authenticatedFetch(
      `/api/v1/studies/${studyId}/chat/unread-count`,
      { method: 'GET' },
      { expectedSessionVersion: sessionVersion, fallbackAccessToken: accessToken ?? undefined },
    );
  } catch (error) {
    rethrowSessionFailure(error);
    throw new ChatApiError('NETWORK_ERROR', '네트워크 연결을 확인해주세요.');
  }

  const body = (await response.json().catch(() => null)) as ApiEnvelope<ChatUnreadCount> | null;

  if (!response.ok || !body?.success) {
    throw new ChatApiError(
      body?.code ?? 'UNKNOWN',
      body?.message ?? '안 읽은 채팅 수를 불러오지 못했습니다.',
    );
  }

  return body.data;
}
