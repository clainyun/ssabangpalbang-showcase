import { authenticatedFetch } from '@/lib/authenticatedFetch';

import type {
  NotificationApiEnvelope,
  NotificationList,
  NotificationPageParam,
} from './types';
import { NotificationApiError } from './types';

export async function getNotifications(
  accessToken: string,
  pageParam: NotificationPageParam,
  signal?: AbortSignal,
  expectedSessionVersion?: number,
): Promise<NotificationList> {
  const measureStartedAt = Date.now();
  let measurementOk = false;

  // 콜드 스타트 계측 로그. 개발 빌드에서만 남깁니다(운영 핫패스에서는 실행 자체를 안 함).
  if (__DEV__) {
    console.info('[ColdStartData] notifications:start', {
      epochMs: measureStartedAt,
    });
  }

  try {
    const params = new URLSearchParams({
      unreadOnly: 'false',
      size: '10',
    });
    if (pageParam.cursor !== null) {
      params.set('cursor', pageParam.cursor);
    }
    const response = await authenticatedFetch(
      `/api/v1/notifications?${params.toString()}`,
      { signal },
      {
        expectedSessionVersion,
        fallbackAccessToken: accessToken,
      },
    );
    const payload = (await response
      .json()
      .catch(() => null)) as NotificationApiEnvelope<NotificationList> | null;

    if (
      !response.ok ||
      payload === null ||
      !payload.success ||
      payload.data === null ||
      payload.data === undefined
    ) {
      throw new NotificationApiError(
        payload?.code ?? 'COMMON_NETWORK_ERROR',
        payload?.message || '알림 목록을 불러오지 못했습니다.',
      );
    }

    measurementOk = true;
    return payload.data;
  } finally {
    if (__DEV__) {
      const measureCompletedAt = Date.now();
      console.info('[ColdStartData] notifications:complete', {
        epochMs: measureCompletedAt,
        durationMs: measureCompletedAt - measureStartedAt,
        aborted: signal?.aborted === true,
        ok: measurementOk,
      });
    }
  }
}
