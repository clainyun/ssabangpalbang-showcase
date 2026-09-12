import { type InfiniteData, useInfiniteQuery } from '@tanstack/react-query';

import { useAuthStore } from '@/store/authStore';

import { getNotifications } from './getNotifications';
import type { NotificationList, NotificationPageParam } from './types';

export const notificationsQueryKey = (sessionVersion: number) =>
  ['notifications', sessionVersion] as const;

export function useNotifications() {
  const accessToken = useAuthStore((state) => state.accessToken);
  const sessionVersion = useAuthStore((state) => state.sessionVersion);

  return useInfiniteQuery<
    NotificationList,
    Error,
    InfiniteData<NotificationList, NotificationPageParam>,
    ReturnType<typeof notificationsQueryKey>,
    NotificationPageParam
  >({
    queryKey: notificationsQueryKey(sessionVersion),
    enabled: accessToken !== null,
    refetchOnMount: false,
    initialPageParam: { cursor: null } satisfies NotificationPageParam,
    queryFn: ({ pageParam, signal }) => {
      if (accessToken === null) {
        throw new Error('로그인이 필요합니다.');
      }

      return getNotifications(accessToken, pageParam, signal, sessionVersion);
    },
    getNextPageParam: (lastPage) =>
      lastPage.hasNext && lastPage.nextCursor !== null
        ? { cursor: lastPage.nextCursor }
        : undefined,
  });
}
