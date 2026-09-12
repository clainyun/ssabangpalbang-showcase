import { authenticatedFetch, rethrowSessionFailure } from '@/lib/authenticatedFetch';
import { useAuthStore } from '@/store/authStore';

import { parseChatbotResponse } from './parseChatbotResponse';
import { ChatbotApiError, type ChatbotMessageList } from './types';

/**
 * 대화 이력을 조회한다(폴링 대상). cursor를 생략하면 처음부터, 전달하면 messageId > cursor.
 * content는 오름차순(과거 → 최신). hasResponseInProgress가 false면 폴링을 멈춘다.
 */
export async function getChatbotMessages(
  apartmentId: number,
  conversationId: number,
  options: { cursor?: number; size?: number } = {},
): Promise<ChatbotMessageList> {
  const { accessToken, sessionVersion } = useAuthStore.getState();

  const query = new URLSearchParams({ size: String(options.size ?? 20) });
  if (options.cursor !== undefined) {
    query.set('cursor', String(options.cursor));
  }

  let response: Response;
  try {
    response = await authenticatedFetch(
      `/api/v1/apartments/${apartmentId}/chatbot/conversations/${conversationId}/messages?${query}`,
      { method: 'GET' },
      {
        expectedSessionVersion: sessionVersion,
        fallbackAccessToken: accessToken ?? undefined,
      },
    );
  } catch (error) {
    rethrowSessionFailure(error);
    throw new ChatbotApiError('NETWORK_ERROR', '네트워크 연결을 확인해주세요.');
  }

  return parseChatbotResponse<ChatbotMessageList>(
    response,
    '대화 이력을 불러오지 못했습니다.',
  );
}
