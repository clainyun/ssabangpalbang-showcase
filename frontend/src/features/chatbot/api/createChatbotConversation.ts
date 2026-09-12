import { authenticatedFetch, rethrowSessionFailure } from '@/lib/authenticatedFetch';
import { useAuthStore } from '@/store/authStore';

import { parseChatbotResponse } from './parseChatbotResponse';
import { ChatbotApiError, type CreateChatbotConversationResult } from './types';

/**
 * 특정 아파트에 대한 새 챗봇 대화를 시작한다.
 * 호출할 때마다 새 conversationId가 생성된다(멱등 아님).
 */
export async function createChatbotConversation(
  apartmentId: number,
): Promise<CreateChatbotConversationResult> {
  const { accessToken, sessionVersion } = useAuthStore.getState();

  let response: Response;
  try {
    response = await authenticatedFetch(
      `/api/v1/apartments/${apartmentId}/chatbot/conversations`,
      { method: 'POST' },
      {
        expectedSessionVersion: sessionVersion,
        fallbackAccessToken: accessToken ?? undefined,
      },
    );
  } catch (error) {
    rethrowSessionFailure(error);
    throw new ChatbotApiError('NETWORK_ERROR', '네트워크 연결을 확인해주세요.');
  }

  return parseChatbotResponse<CreateChatbotConversationResult>(
    response,
    '챗봇 대화를 시작하지 못했습니다.',
  );
}
