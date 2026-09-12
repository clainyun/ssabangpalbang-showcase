import { authenticatedFetch, rethrowSessionFailure } from '@/lib/authenticatedFetch';
import { useAuthStore } from '@/store/authStore';
import { ChatApiError } from '@/features/chat/types';

interface ApiEnvelope<T> {
  success: boolean;
  code: string;
  message: string;
  data: T;
}

interface ChatMessageEditResult {
  studyId: number;
  messageId: number;
  content: string;
  editedAt: string;
}

/**
 * 본인이 보낸 TEXT 메시지의 본문을 수정합니다. 성공하면 서버가 실시간 구독자에게
 * 바뀐 본문과 editedAt을 발행합니다. IMAGE·SYSTEM·삭제된 메시지는 수정할 수 없습니다.
 */
export async function editChatMessage(
  studyId: number,
  messageId: number,
  content: string,
): Promise<ChatMessageEditResult> {
  const { accessToken, sessionVersion } = useAuthStore.getState();

  let response: Response;
  try {
    response = await authenticatedFetch(
      `/api/v1/studies/${studyId}/chat/messages/${messageId}`,
      {
        method: 'PATCH',
        headers: { 'Content-Type': 'application/json' },
        body: JSON.stringify({ content }),
      },
      { expectedSessionVersion: sessionVersion, fallbackAccessToken: accessToken ?? undefined },
    );
  } catch (error) {
    rethrowSessionFailure(error);
    throw new ChatApiError('NETWORK_ERROR', '네트워크 연결을 확인해주세요.');
  }

  const body = (await response
    .json()
    .catch(() => null)) as ApiEnvelope<ChatMessageEditResult> | null;

  if (!response.ok || !body?.success) {
    throw new ChatApiError(
      body?.code ?? 'UNKNOWN',
      body?.message ?? '메시지를 수정하지 못했습니다.',
    );
  }

  return body.data;
}
