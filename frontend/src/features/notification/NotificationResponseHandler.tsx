import * as Notifications from 'expo-notifications';
import { useRootNavigationState, useRouter, useSegments } from 'expo-router';
import { useEffect, useRef } from 'react';

import { usePendingAppLinkStore } from '@/features/navigation/pendingAppLinkStore';
import { useAuthStore } from '@/store/authStore';

import { resolveNotificationResponseNavigation } from './notificationNavigation';

function clearLastNotificationResponse() {
  try {
    Notifications.clearLastNotificationResponse();
  } catch (error) {
    console.warn('[Notification] Failed to clear the handled response.', {
      errorType: error instanceof Error ? error.name : 'unknown',
    });
  }
}

export function NotificationResponseHandler() {
  const response = Notifications.useLastNotificationResponse();
  const rootNavigationState = useRootNavigationState();
  const router = useRouter();
  const segments = useSegments();
  const status = useAuthStore((state) => state.status);
  const onboardingRequired = useAuthStore((state) => state.onboardingRequired);
  const captureRoute = usePendingAppLinkStore((state) => state.captureRoute);
  const handledResponseKeyRef = useRef<string | null>(null);

  useEffect(() => {
    if (!response || !rootNavigationState?.key) {
      return;
    }

    const responseKey = `${response.notification.request.identifier}:${response.actionIdentifier}`;
    if (handledResponseKeyRef.current === responseKey) {
      return;
    }

    const consumeResponse = (action?: () => void) => {
      handledResponseKeyRef.current = responseKey;
      try {
        action?.();
      } finally {
        clearLastNotificationResponse();
      }
    };

    if (response.actionIdentifier !== Notifications.DEFAULT_ACTION_IDENTIFIER) {
      consumeResponse();
      return;
    }

    const navigation = resolveNotificationResponseNavigation(
      response.notification.request.content.data,
    );
    if (navigation.status !== 'ready') {
      consumeResponse();
      return;
    }

    if (status === 'loading') {
      return;
    }

    if (status === 'unauthenticated' || onboardingRequired) {
      consumeResponse(() => captureRoute(navigation.href));
      return;
    }

    if (segments[0] !== '(app)') {
      return;
    }

    consumeResponse(() => {
      try {
        router.push(navigation.href);
      } catch (error) {
        console.warn('[Notification] Failed to navigate from the handled response.', {
          errorType: error instanceof Error ? error.name : 'unknown',
        });
      }
    });
  }, [
    captureRoute,
    onboardingRequired,
    response,
    rootNavigationState?.key,
    router,
    segments,
    status,
  ]);

  return null;
}
