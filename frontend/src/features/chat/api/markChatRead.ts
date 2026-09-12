import { authenticatedFetch, rethrowSessionFailure } from '@/lib/authenticatedFetch';
import { useAuthStore } from '@/store/authStore';
import { ChatApiError } from '@/features/chat/types';

export interface ChatReadResult {
  studyId: number;
  lastReadAt: string;
  unreadCount: number;
}

interface ApiEnvelope<T> {
  success: boolean;
  code: string;
  message: string;
  data: T;
}

const pendingReadsByStudy = new Map<number, Set<Promise<ChatReadResult>>>();

function trackPendingRead(
  studyId: number,
  request: Promise<ChatReadResult>,
): Promise<ChatReadResult> {
  const pendingReads = pendingReadsByStudy.get(studyId) ?? new Set<Promise<ChatReadResult>>();
  pendingReads.add(request);
  pendingReadsByStudy.set(studyId, pendingReads);
  void request.finally(() => {
    pendingReads.delete(request);
    if (pendingReads.size === 0) pendingReadsByStudy.delete(studyId);
  }).catch(() => {});
  return request;
}

/** 화면 복귀 직후 unread 조회가 아직 진행 중인 읽음 PATCH보다 앞서지 않게 한다. */
export async function waitForPendingChatReads(studyId: number): Promise<void> {
  while (pendingReadsByStudy.get(studyId)?.size) {
    await Promise.allSettled([...pendingReadsByStudy.get(studyId)!]);
  }
}

/** lastReadMessageId를 생략하면 현재 시각 기준으로 전체 읽음 처리됩니다. */
export function markChatRead(
  studyId: number,
  lastReadMessageId?: number,
): Promise<ChatReadResult> {
  return trackPendingRead(studyId, performMarkChatRead(studyId, lastReadMessageId));
}

async function performMarkChatRead(
  studyId: number,
  lastReadMessageId?: number,
): Promise<ChatReadResult> {
  const { accessToken, sessionVersion } = useAuthStore.getState();

  let response: Response;
  try {
    response = await authenticatedFetch(
      `/api/v1/studies/${studyId}/chat/read`,
      {
        method: 'PATCH',
        headers: { 'Content-Type': 'application/json' },
        body: JSON.stringify(
          lastReadMessageId !== undefined ? { lastReadMessageId } : {},
        ),
      },
      { expectedSessionVersion: sessionVersion, fallbackAccessToken: accessToken ?? undefined },
    );
  } catch (error) {
    rethrowSessionFailure(error);
    throw new ChatApiError('NETWORK_ERROR', '네트워크 연결을 확인해주세요.');
  }

  const body = (await response.json().catch(() => null)) as ApiEnvelope<ChatReadResult> | null;

  if (!response.ok || !body?.success) {
    throw new ChatApiError(body?.code ?? 'UNKNOWN', body?.message ?? '읽음 처리에 실패했습니다.');
  }

  return body.data;
}
