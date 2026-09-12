import { type InfiniteData, useMutation, useQueryClient } from '@tanstack/react-query';

import type { HomeSummary } from '@/features/home/types';
import { useAuthStore } from '@/store/authStore';
import { homeSummaryQueryRoot } from '@/features/home/useHomeSummary';

import { readNotification } from './readNotification';
import type { NotificationList, NotificationPageParam } from './types';
import { notificationsQueryKey } from './useNotifications';

export function useReadNotification() {
  const queryClient = useQueryClient();
  const accessToken = useAuthStore((state) => state.accessToken);
  const sessionVersion = useAuthStore((state) => state.sessionVersion);

  return useMutation({
    mutationFn: (notificationId: number) => {
      if (accessToken === null) {
        throw new Error('로그인이 필요합니다.');
      }

      return readNotification(accessToken, notificationId, sessionVersion);
    },
    onMutate: async (notificationId) => {
      await Promise.all([
        queryClient.cancelQueries({
          queryKey: notificationsQueryKey(sessionVersion),
          exact: true,
        }),
        queryClient.cancelQueries({ queryKey: homeSummaryQueryRoot }),
      ]);

      const notificationQueryKey = notificationsQueryKey(sessionVersion);
      const previousNotifications = queryClient.getQueryData<
        InfiniteData<NotificationList, NotificationPageParam>
      >(notificationQueryKey);
      const previousHomeSummaries = queryClient.getQueriesData<HomeSummary>({
        queryKey: homeSummaryQueryRoot,
      });
      const wasUnread =
        previousNotifications?.pages.some((page) =>
          page.content.some(
            (notification) =>
              notification.notificationId === notificationId && !notification.isRead,
          ),
        ) ?? false;
      const optimisticReadAt = new Date().toISOString();

      queryClient.setQueryData<InfiniteData<NotificationList, NotificationPageParam>>(
        notificationQueryKey,
        (current) =>
          current === undefined
            ? current
            : {
                ...current,
                pages: current.pages.map((page) => ({
                  ...page,
                  content: page.content.map((notification) =>
                    notification.notificationId === notificationId
                      ? {
                          ...notification,
                          isRead: true,
                          readAt: optimisticReadAt,
                        }
                      : notification,
                  ),
                  unreadCount: wasUnread
                    ? Math.max(0, page.unreadCount - 1)
                    : page.unreadCount,
                })),
              },
      );
      if (wasUnread) {
        queryClient.setQueriesData<HomeSummary>(
          { queryKey: homeSummaryQueryRoot },
          (current) =>
            current === undefined
              ? current
              : {
                  ...current,
                  unreadNotificationCount: Math.max(
                    0,
                    current.unreadNotificationCount - 1,
                  ),
                },
        );
      }

      return { previousNotifications, previousHomeSummaries };
    },
    onError: (_error, _notificationId, context) => {
      if (context?.previousNotifications !== undefined) {
        queryClient.setQueryData(
          notificationsQueryKey(sessionVersion),
          context.previousNotifications,
        );
      }
      for (const [queryKey, summary] of context?.previousHomeSummaries ?? []) {
        queryClient.setQueryData(queryKey, summary);
      }
    },
    onSuccess: (result) => {
      queryClient.setQueryData<InfiniteData<NotificationList, NotificationPageParam>>(
        notificationsQueryKey(sessionVersion),
        (current) =>
          current === undefined
            ? current
            : {
                ...current,
                pages: current.pages.map((page) => ({
                  ...page,
                  content: page.content.map((notification) =>
                    notification.notificationId === result.notificationId
                      ? {
                          ...notification,
                          isRead: result.isRead,
                          readAt: result.readAt,
                        }
                      : notification,
                  ),
                  unreadCount: result.unreadCount,
                })),
              },
      );
      queryClient.setQueriesData<HomeSummary>(
        { queryKey: homeSummaryQueryRoot },
        (current) =>
          current === undefined
            ? current
            : {
                ...current,
                unreadNotificationCount: result.unreadCount,
              },
      );
      void queryClient.invalidateQueries({
        queryKey: notificationsQueryKey(sessionVersion),
        exact: true,
      });
    },
  });
}
