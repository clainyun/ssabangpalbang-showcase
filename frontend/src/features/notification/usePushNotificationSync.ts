import { useQueryClient } from '@tanstack/react-query';
import * as Notifications from 'expo-notifications';
import { useEffect } from 'react';

import { invalidateStudyCaches } from '@/features/study/useStudyManagement';

import { notificationsQueryKey } from './api/useNotifications';

/**
 * 서버가 `targetId`에 studyId를 실어 보내는 푸시 화면들입니다.
 * (StudyNotificationPortImpl·StudySchedulePushListener·FieldVisitStartedPushListener 기준)
 */
const STUDY_TARGET_SCREENS: ReadonlySet<string> = new Set([
  'STUDY_DETAIL',
  'STUDY_MANAGE',
  'STUDY_SCHEDULE',
  'FIELD_VISIT',
]);

const POSITIVE_INTEGER_PATTERN = /^[1-9]\d*$/;

/** 스터디 관련 푸시면 studyId를, 아니면 `null`을 돌려줍니다(형식이 어긋나면 조용히 무시). */
function parsePushStudyId(data: Record<string, unknown> | undefined): number | null {
  if (!data) {
    return null;
  }

  const targetScreen = typeof data.targetScreen === 'string' ? data.targetScreen : null;
  if (targetScreen === null || !STUDY_TARGET_SCREENS.has(targetScreen)) {
    return null;
  }

  const rawTargetId = data.targetId;
  if (typeof rawTargetId !== 'string' || !POSITIVE_INTEGER_PATTERN.test(rawTargetId)) {
    return null;
  }

  const studyId = Number(rawTargetId);
  return Number.isSafeInteger(studyId) ? studyId : null;
}

/**
 * 푸시가 도착하면(포그라운드 수신·알림 탭) 그 알림이 가리키는 캐시를 무효화합니다.
 *
 * 서버는 신청 승인·거절, 일정 변경, 임장 시작을 FCM으로 알려주지만 프론트에는 수신
 * 리스너가 없어서, 사용자가 화면을 다시 열 때까지 승인 결과가 반영되지 않았습니다.
 * 화면 이동은 `NotificationResponseHandler`가 그대로 맡고, 여기서는 캐시만 손댑니다.
 */
export function usePushNotificationSync(isAuthenticated: boolean, sessionVersion: number) {
  const queryClient = useQueryClient();

  useEffect(() => {
    if (!isAuthenticated) {
      return;
    }

    const syncFromPushData = (data: Record<string, unknown> | undefined) => {
      // 알림 목록·안 읽음 배지는 어떤 푸시든 바뀌므로 항상 무효화합니다.
      void queryClient.invalidateQueries({ queryKey: notificationsQueryKey(sessionVersion) });

      const studyId = parsePushStudyId(data);
      if (studyId === null) {
        return;
      }
      invalidateStudyCaches(queryClient, studyId);
    };

    const receivedSubscription = Notifications.addNotificationReceivedListener((notification) => {
      syncFromPushData(notification.request.content.data);
    });
    const responseSubscription = Notifications.addNotificationResponseReceivedListener(
      (response) => {
        syncFromPushData(response.notification.request.content.data);
      },
    );

    return () => {
      receivedSubscription.remove();
      responseSubscription.remove();
    };
  }, [isAuthenticated, queryClient, sessionVersion]);
}
