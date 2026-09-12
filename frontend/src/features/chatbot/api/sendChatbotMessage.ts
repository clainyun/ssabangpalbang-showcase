import { authenticatedFetch, rethrowSessionFailure } from '@/lib/authenticatedFetch';
import { useAuthStore } from '@/store/authStore';

import { parseChatbotResponse } from './parseChatbotResponse';
import { ChatbotApiError, type SendChatbotMessageResult } from './types';

/**
 * 대화에 질문을 전송한다(202 Accepted). content는 서버가 trim 후 1~1000자로 재검증한다.
 * 반환된 assistantMessage.messageId를 로딩 버블로 두고, polling 정보로 이력을 폴링한다.
 * 답변 생성 중 재전송 시 409 CHATBOT_RESPONSE_IN_PROGRESS가 난다.
 */
export async function sendChatbotMessage(
  apartmentId: number,
  conversationId: number,
  content: string,
): Promise<SendChatbotMessageResult> {
  const { accessToken, sessionVersion } = useAuthStore.getState();

  let response: Response;
  try {
    response = await authenticatedFetch(
      `/api/v1/apartments/${apartmentId}/chatbot/conversations/${conversationId}/messages`,
      {
        method: 'POST',
        headers: { 'Content-Type': 'application/json' },
        body: JSON.stringify({ content }),
      },
      {
        expectedSessionVersion: sessionVersion,
        fallbackAccessToken: accessToken ?? undefined,
      },
    );
  } catch (error) {
    rethrowSessionFailure(error);
    throw new ChatbotApiError('NETWORK_ERROR', '네트워크 연결을 확인해주세요.');
  }

  return parseChatbotResponse<SendChatbotMessageResult>(
    response,
    '질문을 전송하지 못했습니다.',
  );
}
