import { ChatbotApiError, type ApiEnvelope } from './types';

/** 공통 응답 봉투(ApiEnvelope)를 언랩한다. 실패면 code·message·부가 data로 에러를 던진다. */
export async function parseChatbotResponse<T>(
  response: Response,
  fallbackMessage: string,
): Promise<T> {
  const body = (await response.json().catch(() => null)) as
    | ApiEnvelope<T>
    | null;

  if (
    !response.ok ||
    body === null ||
    !body.success ||
    body.data === null ||
    body.data === undefined
  ) {
    throw new ChatbotApiError(
      body?.code ?? 'COMMON_NETWORK_ERROR',
      body?.message || fallbackMessage,
      // 409 CHATBOT_RESPONSE_IN_PROGRESS, 429 rate limit 등은 data에 부가 정보가 담긴다.
      body?.data ?? undefined,
    );
  }

  return body.data;
}
