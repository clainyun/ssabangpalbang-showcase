import * as Notifications from 'expo-notifications';
import { useEffect } from 'react';

import {
  queueFcmTokenSync,
  resumeFcmTokenRegistration,
} from '@/features/notification/fcmToken';
import { useAuthStore } from '@/store/authStore';

export function useFcmTokenRegistration(
  isAuthenticated: boolean,
  sessionVersion: number,
) {
  useEffect(() => {
    if (!isAuthenticated) {
      return;
    }

    let disposed = false;
    if (!resumeFcmTokenRegistration(sessionVersion)) {
      return;
    }

    const register = (token?: string) => {
      const authState = useAuthStore.getState();
      if (
        !authState.accessToken ||
        authState.sessionVersion !== sessionVersion
      ) {
        return;
      }

      void queueFcmTokenSync(
        authState.accessToken,
        token,
        () => !disposed,
        sessionVersion,
      )
        .catch((error) => {
          if (!disposed) {
            console.warn('[Notifications] Failed to register FCM token.', {
              errorType: error instanceof Error ? error.name : 'unknown',
            });
          }
        });
    };

    register();

    const subscription = Notifications.addPushTokenListener((devicePushToken) => {
      if (devicePushToken.type === 'android' && typeof devicePushToken.data === 'string') {
        console.info('[Notifications] Android FCM token refresh event received.');
        register(devicePushToken.data);
      }
    });

    return () => {
      disposed = true;
      subscription.remove();
    };
  }, [isAuthenticated, sessionVersion]);
}
