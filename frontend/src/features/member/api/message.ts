import { authenticatedFetch, rethrowSessionFailure } from '@/lib/authenticatedFetch';
import { useAuthStore } from '@/store/authStore';

export const MEMBER_MESSAGE_MAX_LENGTH = 500;

export interface MemberMessageSendRequest {
  content: string;
  clientMessageId: string;
}

export interface MemberMessageSendResponse {
  recipientId: number;
  recipientNickname: string;
  notificationId: number;
  clientMessageId: string;
  sentAt: string;
}

export interface MemberMessageSendResult {
  code: string;
  message: string;
  data: MemberMessageSendResponse;
}

interface ApiEnvelope<T> {
  success: boolean;
  code: string;
  message: string;
  data: T | ErrorData | null;
}

interface ErrorData {
  field?: string;
  reason?: string;
  maxLength?: number;
}

export class MemberMessageApiError extends Error {
  code: string;
  field?: string;

  constructor(code: string, message: string, field?: string) {
    super(message);
    this.name = 'MemberMessageApiError';
    this.code = code;
    this.field = field;
  }
}

function isErrorData(data: unknown): data is ErrorData {
  return typeof data === 'object' && data !== null;
}

function errorMessage(code: string, serverMessage: string | undefined, data: unknown): string {
  if (isErrorData(data) && typeof data.reason === 'string' && data.reason.trim()) {
    return data.reason;
  }

  switch (code) {
    case 'MEMBER_MESSAGE_SELF_NOT_ALLOWED':
      return '내게는 쪽지를 보낼 수 없어요.';
    case 'MEMBER_MESSAGE_CONTENT_REQUIRED':
      return '보낼 내용을 입력해 주세요.';
    case 'MEMBER_MESSAGE_CONTENT_TOO_LONG':
      return `쪽지는 ${MEMBER_MESSAGE_MAX_LENGTH}자까지 입력할 수 있어요.`;
    case 'MEMBER_MESSAGE_FOLLOW_REQUIRED':
      return '현재 팔로잉 중인 사용자에게만 쪽지를 보낼 수 있어요.';
    case 'MEMBER_NOT_FOUND':
      return '쪽지를 받을 사용자를 찾을 수 없어요.';
    default:
      return serverMessage?.trim() || '쪽지를 보내지 못했어요. 잠시 후 다시 시도해 주세요.';
  }
}

export async function sendMemberMessage(
  memberId: number,
  request: MemberMessageSendRequest,
): Promise<MemberMessageSendResult> {
  const { accessToken, sessionVersion } = useAuthStore.getState();

  let response: Response;
  try {
    response = await authenticatedFetch(
      `/api/v1/members/${memberId}/messages`,
      {
        method: 'POST',
        headers: {
          Accept: 'application/json',
          'Content-Type': 'application/json',
        },
        body: JSON.stringify(request),
      },
      {
        expectedSessionVersion: sessionVersion,
        fallbackAccessToken: accessToken ?? undefined,
      },
    );
  } catch (error) {
    rethrowSessionFailure(error);
    throw new MemberMessageApiError(
      'NETWORK_ERROR',
      '서버에 연결할 수 없어요. 네트워크 또는 서버 상태를 확인해 주세요.',
    );
  }

  const body = (await response
    .json()
    .catch(() => null)) as ApiEnvelope<MemberMessageSendResponse> | null;

  if (
    !response.ok ||
    !body?.success ||
    !body.data ||
    (isErrorData(body.data) && !('recipientId' in body.data))
  ) {
    const code = body?.code ?? 'UNKNOWN';
    const data = body?.data;
    throw new MemberMessageApiError(
      code,
      errorMessage(code, body?.message, data),
      isErrorData(data) && typeof data.field === 'string' ? data.field : undefined,
    );
  }

  return {
    code: body.code,
    message: body.message,
    data: body.data as MemberMessageSendResponse,
  };
}
