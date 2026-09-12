import * as Notifications from 'expo-notifications';

let isConfigured = false;

/** 앱이 포그라운드일 때도 FCM 알림을 배너와 알림 목록에 표시합니다. */
export function configureNotificationPresentation() {
  if (isConfigured) {
    return;
  }

  Notifications.setNotificationHandler({
    handleNotification: async (notification) => {
      const isTestPush =
        notification.request.content.data?.notificationType === 'FCM_TEST_PUSH';

      return {
        shouldShowBanner: true,
        shouldShowList: true,
        shouldPlaySound: isTestPush,
        shouldSetBadge: false,
        priority: Notifications.AndroidNotificationPriority.HIGH,
      };
    },
  });
  isConfigured = true;
}
