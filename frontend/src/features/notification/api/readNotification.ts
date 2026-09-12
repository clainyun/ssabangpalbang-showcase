import {
  authenticatedFetch,
  rethrowSessionFailure,
} from '@/lib/authenticatedFetch';

import type { NotificationApiEnvelope, NotificationReadResult } from './types';
import { NotificationApiError } from './types';

const NETWORK_ERROR_MESSAGE = '네트워크 연결을 확인해 주세요.';
const FALLBACK_ERROR_MESSAGE = '알림을 읽음 처리하지 못했습니다.';

export async function readNotification(
  accessToken: string,
  notificationId: number,
  expectedSessionVersion?: number,
): Promise<NotificationReadResult> {
  if (!Number.isSafeInteger(notificationId) || notificationId <= 0) {
    throw new NotificationApiError(
      'NOTIFICATION_ID_INVALID',
      '알림 번호가 올바르지 않습니다.',
    );
  }

  let response: Response;
  try {
    response = await authenticatedFetch(
      `/api/v1/notifications/${notificationId}/read`,
      { method: 'PATCH' },
      {
        expectedSessionVersion,
        fallbackAccessToken: accessToken,
      },
    );
  } catch (error) {
    rethrowSessionFailure(error);
    throw new NotificationApiError('COMMON_NETWORK_ERROR', NETWORK_ERROR_MESSAGE);
  }

  const payload = (await response.json().catch(() => null)) as
    | NotificationApiEnvelope<NotificationReadResult>
    | null;

  if (!response.ok || payload === null || !payload.success || payload.data == null) {
    throw new NotificationApiError(
      payload?.code ?? 'COMMON_NETWORK_ERROR',
      payload?.message?.trim() || FALLBACK_ERROR_MESSAGE,
    );
  }

  return payload.data;
}
