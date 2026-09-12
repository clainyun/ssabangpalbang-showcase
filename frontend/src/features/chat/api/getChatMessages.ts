import { authenticatedFetch, rethrowSessionFailure } from '@/lib/authenticatedFetch';
import { useAuthStore } from '@/store/authStore';
import { ChatApiError, type ChatMessage } from '@/features/chat/types';

export interface ChatMessagePage {
  studyId: number;
  content: ChatMessage[];
  /** 다음(과거) 목록 조회용 커서. 더 없으면 null. */
  nextCursor: number | null;
  hasNext: boolean;
}

interface ApiEnvelope<T> {
  success: boolean;
  code: string;
  message: string;
  data: T;
}

/** cursor를 생략하면 최신 메시지부터, 전달하면 그보다 과거 메시지를 조회합니다. */
export async function getChatMessages(
  studyId: number,
  options: { cursor?: number; size?: number } = {},
): Promise<ChatMessagePage> {
  const { accessToken, sessionVersion } = useAuthStore.getState();

  const query = new URLSearchParams({ size: String(options.size ?? 30) });
  if (options.cursor !== undefined) {
    query.set('cursor', String(options.cursor));
  }

  let response: Response;
  try {
    response = await authenticatedFetch(
      `/api/v1/studies/${studyId}/chat/messages?${query}`,
      { method: 'GET' },
      { expectedSessionVersion: sessionVersion, fallbackAccessToken: accessToken ?? undefined },
    );
  } catch (error) {
    rethrowSessionFailure(error);
    throw new ChatApiError('NETWORK_ERROR', '네트워크 연결을 확인해주세요.');
  }

  const body = (await response.json().catch(() => null)) as ApiEnvelope<ChatMessagePage> | null;

  if (!response.ok || !body?.success) {
    throw new ChatApiError(
      body?.code ?? 'UNKNOWN',
      body?.message ?? '채팅 이력을 불러오지 못했습니다.',
    );
  }

  return body.data;
}
