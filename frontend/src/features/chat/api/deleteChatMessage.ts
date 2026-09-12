import { authenticatedFetch, rethrowSessionFailure } from '@/lib/authenticatedFetch';
import { useAuthStore } from '@/store/authStore';
import { ChatApiError } from '@/features/chat/types';

interface ApiEnvelope<T> {
  success: boolean;
  code: string;
  message: string;
  data: T;
}

interface ChatMessageDeleteResult {
  studyId: number;
  messageId: number;
  deletedAt: string;
}

/**
 * 본인이 보낸 메시지를 소프트 삭제합니다. 서버가 커밋 후 실시간 구독자에게
 * deleted=true 페이로드를 발행하므로, 호출 측은 성공 시 자기 화면의 말풍선만
 * 즉시 톰스톤으로 바꾸면 됩니다(브로드캐스트는 기존 메시지를 이기지 못합니다).
 */
export async function deleteChatMessage(
  studyId: number,
  messageId: number,
): Promise<ChatMessageDeleteResult> {
  const { accessToken, sessionVersion } = useAuthStore.getState();

  let response: Response;
  try {
    response = await authenticatedFetch(
      `/api/v1/studies/${studyId}/chat/messages/${messageId}`,
      { method: 'DELETE' },
      { expectedSessionVersion: sessionVersion, fallbackAccessToken: accessToken ?? undefined },
    );
  } catch (error) {
    rethrowSessionFailure(error);
    throw new ChatApiError('NETWORK_ERROR', '네트워크 연결을 확인해주세요.');
  }

  const body = (await response
    .json()
    .catch(() => null)) as ApiEnvelope<ChatMessageDeleteResult> | null;

  if (!response.ok || !body?.success) {
    throw new ChatApiError(
      body?.code ?? 'UNKNOWN',
      body?.message ?? '메시지를 삭제하지 못했습니다.',
    );
  }

  return body.data;
}
