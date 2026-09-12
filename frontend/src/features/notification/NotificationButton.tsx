import Ionicons from '@expo/vector-icons/Ionicons';
import { useQueryClient } from '@tanstack/react-query';
import { useFocusEffect, useRouter } from 'expo-router';
import { useCallback, useEffect, useMemo, useRef, useState } from 'react';
import {
  Modal,
  Pressable,
  StyleSheet,
  Text,
  useWindowDimensions,
  View,
} from 'react-native';
import { useSafeAreaInsets } from 'react-native-safe-area-context';

import { appAlert } from '@/components/AppDialog';
import {
  GlassIconButton,
  GLASS_ICON_BUTTON_ICON_SIZE,
  GLASS_ICON_BUTTON_SIZE,
} from '@/components/GlassIconButton';
import { DARK_GREEN_COLOR, ERROR_COLOR, SURFACE_COLOR } from '@/constants/colors';
import { refetchOnFocusIfStale } from '@/lib/refetchOnFocusIfStale';
import { useAuthStore } from '@/store/authStore';

import { NotificationApiError, type NotificationItem } from './api/types';
import { notificationsQueryKey, useNotifications } from './api/useNotifications';
import { useReadNotification } from './api/useReadNotification';
import { NotificationDropdown } from './NotificationDropdown';
import { resolveNotificationNavigation } from './notificationNavigation';

interface NotificationButtonProps {
  buttonSize?: number;
  iconSize?: number;
}

interface DropdownAnchor {
  left: number;
  top: number;
  width: number;
  maxHeight: number;
}

const DROPDOWN_MAX_WIDTH = 360;
const DROPDOWN_MAX_HEIGHT = 460;
const DROPDOWN_MIN_HEIGHT = 160;
const SCREEN_MARGIN = 16;
const DROPDOWN_GAP = 8;

function waitForNextFrame(): Promise<void> {
  return new Promise((resolve) => requestAnimationFrame(() => resolve()));
}

export function NotificationButton({
  buttonSize = GLASS_ICON_BUTTON_SIZE,
  iconSize = GLASS_ICON_BUTTON_ICON_SIZE,
}: NotificationButtonProps) {
  const buttonRef = useRef<View>(null);
  const isFocusedRef = useRef(false);
  const isSelectionInFlightRef = useRef(false);
  const openRequestIdRef = useRef(0);
  const router = useRouter();
  const insets = useSafeAreaInsets();
  const { width: screenWidth, height: screenHeight } = useWindowDimensions();
  const [isOpen, setIsOpen] = useState(false);
  const [anchor, setAnchor] = useState<DropdownAnchor>(() => ({
    left: SCREEN_MARGIN,
    top: insets.top + buttonSize + DROPDOWN_GAP,
    width: Math.min(screenWidth - SCREEN_MARGIN * 2, DROPDOWN_MAX_WIDTH),
    maxHeight: DROPDOWN_MAX_HEIGHT,
  }));
  const [processingNotificationId, setProcessingNotificationId] = useState<number | null>(null);
  const queryClient = useQueryClient();
  const sessionVersion = useAuthStore((state) => state.sessionVersion);
  const notificationsQuery = useNotifications();
  const { mutateAsync: markNotificationAsRead } = useReadNotification();
  const { refetch: refetchNotifications } = notificationsQuery;
  const notificationPages = notificationsQuery.data?.pages;
  const unreadCount = notificationPages?.[notificationPages.length - 1]?.unreadCount ?? 0;
  const notifications = useMemo(() => {
    const seenNotificationIds = new Set<number>();

    return (notificationPages ?? []).flatMap((page) =>
      page.content.filter((notification) => {
        if (seenNotificationIds.has(notification.notificationId)) {
          return false;
        }
        seenNotificationIds.add(notification.notificationId);
        return true;
      }),
    );
  }, [notificationPages]);

  const close = useCallback(() => {
    setIsOpen(false);
  }, []);

  const requestClose = useCallback(() => {
    if (!isSelectionInFlightRef.current) {
      close();
    }
  }, [close]);

  const handleNotificationPress = useCallback(
    async (notification: NotificationItem) => {
      if (isSelectionInFlightRef.current) {
        return;
      }

      isSelectionInFlightRef.current = true;
      setProcessingNotificationId(notification.notificationId);

      try {
        if (!notification.isRead) {
          await markNotificationAsRead(notification.notificationId);
        }

        if (!isFocusedRef.current) {
          return;
        }

        const navigation = resolveNotificationNavigation(notification);
        if (navigation.status === 'target-unavailable') {
          appAlert('확인할 수 없는 알림', '더 이상 확인할 수 없는 내용입니다.');
          return;
        }
        if (navigation.status === 'unsupported') {
          appAlert('이동할 수 없는 알림', '이 알림의 연결 화면은 지원하지 않습니다.');
          return;
        }

        close();
        await waitForNextFrame();
        if (!isFocusedRef.current) {
          return;
        }
        router.push(navigation.href);
      } catch (error) {
        if (isFocusedRef.current) {
          appAlert(
            '알림 처리 실패',
            error instanceof Error
              ? error.message
              : '알림을 처리하지 못했습니다. 잠시 후 다시 시도해 주세요.',
          );
        }
      } finally {
        isSelectionInFlightRef.current = false;
        setProcessingNotificationId(null);
      }
    },
    [close, markNotificationAsRead, router],
  );

  useFocusEffect(
    useCallback(() => {
      isFocusedRef.current = true;
      // 20초 이내 재방문이면 재조회를 생략합니다. 첫 마운트 조회가 진행 중이면
      // 헬퍼가 fetching 상태를 보고 건너뛰고, 드롭다운을 여는 순간(open)에는
      // 게이트 없이 항상 최신 목록을 다시 불러옵니다.
      refetchOnFocusIfStale(queryClient, notificationsQueryKey(sessionVersion));

      return () => {
        isFocusedRef.current = false;
        openRequestIdRef.current += 1;
        close();
      };
    }, [close, queryClient, sessionVersion]),
  );

  useEffect(() => {
    openRequestIdRef.current += 1;
    const timeoutId = setTimeout(close, 0);
    return () => clearTimeout(timeoutId);
  }, [close, insets.bottom, insets.top, screenHeight, screenWidth]);

  const calculateAnchor = useCallback(
    (x: number, y: number, width: number, height: number): DropdownAnchor => {
      const dropdownWidth = Math.min(screenWidth - SCREEN_MARGIN * 2, DROPDOWN_MAX_WIDTH);
      const left = Math.max(
        SCREEN_MARGIN,
        Math.min(x + width - dropdownWidth, screenWidth - dropdownWidth - SCREEN_MARGIN),
      );
      const belowTop = y + height + DROPDOWN_GAP;
      const availableBelow = screenHeight - insets.bottom - SCREEN_MARGIN - belowTop;
      const availableAbove = y - insets.top - SCREEN_MARGIN - DROPDOWN_GAP;
      const shouldOpenAbove =
        availableBelow < DROPDOWN_MIN_HEIGHT && availableAbove > availableBelow;
      const availableHeight = shouldOpenAbove ? availableAbove : availableBelow;
      const maxHeight = Math.max(
        DROPDOWN_MIN_HEIGHT,
        Math.min(DROPDOWN_MAX_HEIGHT, availableHeight),
      );
      const top = shouldOpenAbove
        ? Math.max(insets.top + SCREEN_MARGIN, y - maxHeight - DROPDOWN_GAP)
        : belowTop;

      return { left, top, width: dropdownWidth, maxHeight };
    },
    [insets.bottom, insets.top, screenHeight, screenWidth],
  );

  const open = () => {
    const requestId = openRequestIdRef.current + 1;
    openRequestIdRef.current = requestId;
    void refetchNotifications();

    if (!buttonRef.current) {
      if (!isFocusedRef.current) {
        return;
      }
      setAnchor(
        calculateAnchor(
          screenWidth - SCREEN_MARGIN - buttonSize,
          insets.top,
          buttonSize,
          buttonSize,
        ),
      );
      setIsOpen(true);
      return;
    }

    buttonRef.current.measureInWindow((x, y, width, height) => {
      if (!isFocusedRef.current || openRequestIdRef.current !== requestId) {
        return;
      }
      setAnchor(calculateAnchor(x, y, width, height));
      setIsOpen(true);
    });
  };

  const toggle = () => {
    if (isOpen) {
      close();
      return;
    }

    open();
  };

  return (
    <View style={styles.anchor}>
      <GlassIconButton
        accessibilityLabel={
          isOpen
            ? '알림 닫기'
            : unreadCount > 0
              ? `알림 열기, 읽지 않은 알림 ${unreadCount}개`
              : '알림 열기'
        }
        active={isOpen}
        expanded={isOpen}
        onPress={toggle}
        ref={buttonRef}
        size={buttonSize}
        testID="notification-button"
      >
        <Ionicons
          color={DARK_GREEN_COLOR}
          name={isOpen ? 'notifications' : 'notifications-outline'}
          size={iconSize}
        />
        {unreadCount > 0 ? (
          <View style={styles.badge} testID="notification-badge">
            <Text style={styles.badgeText}>{unreadCount > 99 ? '99+' : unreadCount}</Text>
          </View>
        ) : null}
      </GlassIconButton>

      <Modal
        animationType="fade"
        onRequestClose={requestClose}
        statusBarTranslucent
        transparent
        visible={isOpen}
      >
        <View accessibilityViewIsModal onAccessibilityEscape={requestClose} style={styles.modal}>
          <Pressable
            accessible={false}
            importantForAccessibility="no"
            onPress={requestClose}
            style={StyleSheet.absoluteFill}
          />
          <View style={[styles.dropdownPosition, { left: anchor.left, top: anchor.top }]}>
            <NotificationDropdown
              errorMessage={
                !notificationsQuery.data && notificationsQuery.error instanceof Error
                  ? notificationsQuery.error.message
                  : undefined
              }
              appendErrorMessage={
                notificationsQuery.data && notificationsQuery.isFetchNextPageError
                  ? notificationsQuery.error instanceof Error
                    ? notificationsQuery.error.message
                    : '알림을 더 불러오지 못했습니다.'
                  : undefined
              }
              refreshErrorMessage={
                notificationsQuery.data &&
                notificationsQuery.isRefetchError &&
                !notificationsQuery.isFetchNextPageError
                  ? notificationsQuery.error instanceof Error
                    ? notificationsQuery.error.message
                    : '알림 목록을 새로고침하지 못했습니다.'
                  : undefined
              }
              hasNext={notificationsQuery.hasNextPage}
              isFetching={
                notificationsQuery.isRefetching && !notificationsQuery.isFetchingNextPage
              }
              isFetchingNextPage={notificationsQuery.isFetchingNextPage}
              isLoading={notificationsQuery.isPending}
              maxHeight={anchor.maxHeight}
              notifications={notifications}
              onLoadMore={async () => {
                if (
                  notificationsQuery.hasNextPage &&
                  !notificationsQuery.isFetchingNextPage &&
                  !notificationsQuery.isFetchNextPageError
                ) {
                  await notificationsQuery.fetchNextPage();
                }
              }}
              onNotificationPress={(notification) => void handleNotificationPress(notification)}
              onRetry={() => void refetchNotifications()}
              onRetryLoadMore={() => {
                if (
                  notificationsQuery.error instanceof NotificationApiError &&
                  notificationsQuery.error.code === 'NOTIFICATION_CURSOR_INVALID'
                ) {
                  void refetchNotifications();
                  return;
                }
                void notificationsQuery.fetchNextPage();
              }}
              processingNotificationId={processingNotificationId}
              width={anchor.width}
            />
          </View>
        </View>
      </Modal>
    </View>
  );
}

const styles = StyleSheet.create({
  anchor: {
    position: 'relative',
  },
  badge: {
    position: 'absolute',
    top: -4,
    right: -5,
    minWidth: 20,
    height: 20,
    paddingHorizontal: 5,
    alignItems: 'center',
    justifyContent: 'center',
    borderWidth: 2,
    borderColor: SURFACE_COLOR,
    borderRadius: 10,
    backgroundColor: ERROR_COLOR,
  },
  badgeText: {
    color: SURFACE_COLOR,
    fontSize: 10,
    fontWeight: '800',
  },
  modal: {
    flex: 1,
  },
  dropdownPosition: {
    position: 'absolute',
  },
});
