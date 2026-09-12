import Ionicons from '@expo/vector-icons/Ionicons';
import { useRef } from 'react';
import { ActivityIndicator, FlatList, Pressable, StyleSheet, Text, View } from 'react-native';

import {
  BORDER_COLOR,
  DARK_GREEN_COLOR,
  ERROR_COLOR,
  LABEL_COLOR,
  MUTED_TEXT_COLOR,
  PRIMARY_COLOR,
  SOFT_GREEN_COLOR,
  SURFACE_COLOR,
  TEXT_COLOR,
} from '@/constants/colors';

import type { NotificationCategory, NotificationItem } from './api/types';
import { formatNotificationBody, formatNotificationTime } from './notificationTime';

interface NotificationDropdownProps {
  notifications: NotificationItem[];
  isLoading: boolean;
  isFetching: boolean;
  isFetchingNextPage: boolean;
  hasNext: boolean;
  errorMessage?: string;
  appendErrorMessage?: string;
  refreshErrorMessage?: string;
  width: number;
  maxHeight: number;
  processingNotificationId: number | null;
  onNotificationPress: (notification: NotificationItem) => void;
  onLoadMore: () => Promise<void>;
  onRetry: () => void;
  onRetryLoadMore: () => void;
}

const CATEGORY_ICONS: Record<NotificationCategory, keyof typeof Ionicons.glyphMap> = {
  STUDY: 'people-outline',
  FIELD: 'location-outline',
  REPORT: 'document-text-outline',
  COMMUNITY: 'chatbubbles-outline',
  MESSAGE: 'mail-outline',
  SYSTEM: 'information-circle-outline',
};

function NotificationRow({
  item,
  isDisabled,
  isProcessing,
  onPress,
}: {
  item: NotificationItem;
  isDisabled: boolean;
  isProcessing: boolean;
  onPress: (notification: NotificationItem) => void;
}) {
  const displayedBody = formatNotificationBody(item.body, item.type);

  return (
    <Pressable
      accessibilityLabel={`${item.isRead ? '읽은 알림' : '읽지 않은 알림'}, ${item.title}, ${displayedBody}`}
      accessibilityRole="button"
      accessibilityState={{ busy: isProcessing, disabled: isDisabled }}
      disabled={isDisabled}
      onPress={() => onPress(item)}
      style={({ pressed }) => [
        styles.row,
        !item.isRead && styles.unreadRow,
        pressed && styles.rowPressed,
        isDisabled && !isProcessing && styles.rowDisabled,
      ]}
    >
      <View style={styles.categoryIcon}>
        <Ionicons color={DARK_GREEN_COLOR} name={CATEGORY_ICONS[item.category]} size={19} />
      </View>

      <View style={styles.rowContent}>
        <View style={styles.rowTitleLine}>
          <Text numberOfLines={1} style={styles.rowTitle}>
            {item.title}
          </Text>
          {isProcessing ? (
            <ActivityIndicator color={PRIMARY_COLOR} size="small" />
          ) : !item.isRead ? (
            <View accessibilityLabel="읽지 않음" style={styles.unreadDot} />
          ) : null}
        </View>
        <Text numberOfLines={2} style={styles.rowBody}>
          {displayedBody}
        </Text>
        <Text style={styles.rowTime}>{formatNotificationTime(item.sentAt)}</Text>
      </View>
    </Pressable>
  );
}

function EmptyState() {
  return (
    <View style={styles.stateContainer}>
      <Ionicons color={MUTED_TEXT_COLOR} name="notifications-off-outline" size={30} />
      <Text style={styles.stateTitle}>새로운 알림이 없습니다.</Text>
    </View>
  );
}

export function NotificationDropdown({
  notifications,
  isLoading,
  isFetching,
  isFetchingNextPage,
  hasNext,
  errorMessage,
  appendErrorMessage,
  refreshErrorMessage,
  width,
  maxHeight,
  processingNotificationId,
  onNotificationPress,
  onLoadMore,
  onRetry,
  onRetryLoadMore,
}: NotificationDropdownProps) {
  const endReachedLockRef = useRef(false);

  const renderFooter = () => {
    if (isFetchingNextPage) {
      return (
        <View style={styles.footerState}>
          <ActivityIndicator color={PRIMARY_COLOR} size="small" />
          <Text style={styles.stateText}>알림을 더 불러오는 중입니다.</Text>
        </View>
      );
    }
    if (appendErrorMessage) {
      return (
        <View style={styles.footerState}>
          <Text style={styles.errorText}>{appendErrorMessage}</Text>
          <Pressable
            accessibilityRole="button"
            onPress={onRetryLoadMore}
            style={styles.retryButton}
          >
            <Text style={styles.retryText}>다시 시도</Text>
          </Pressable>
        </View>
      );
    }
    if (refreshErrorMessage) {
      return (
        <View style={styles.footerState}>
          <Text style={styles.errorText}>{refreshErrorMessage}</Text>
          <Pressable accessibilityRole="button" onPress={onRetry} style={styles.retryButton}>
            <Text style={styles.retryText}>다시 시도</Text>
          </Pressable>
        </View>
      );
    }
    return null;
  };

  return (
    <View
      accessibilityLabel="알림 목록"
      accessibilityViewIsModal
      style={[styles.container, { width, maxHeight }]}
      testID="notification-dropdown"
    >
      <View style={styles.header}>
        <View>
          <Text style={styles.headerTitle}>알림</Text>
          <Text style={styles.headerCaption}>읽지 않은 알림부터 최신순</Text>
        </View>
        {isFetching && !isLoading ? <ActivityIndicator color={PRIMARY_COLOR} size="small" /> : null}
      </View>

      {isLoading ? (
        <View style={styles.stateContainer}>
          <ActivityIndicator color={PRIMARY_COLOR} />
          <Text style={styles.stateText}>알림을 불러오는 중입니다.</Text>
        </View>
      ) : errorMessage ? (
        <View style={styles.stateContainer}>
          <Ionicons color={ERROR_COLOR} name="alert-circle-outline" size={30} />
          <Text style={styles.errorText}>{errorMessage}</Text>
          <Pressable accessibilityRole="button" onPress={onRetry} style={styles.retryButton}>
            <Text style={styles.retryText}>다시 시도</Text>
          </Pressable>
        </View>
      ) : (
        <FlatList
          data={notifications}
          keyExtractor={(item) => item.notificationId.toString()}
          ListEmptyComponent={EmptyState}
          ListFooterComponent={renderFooter}
          ItemSeparatorComponent={() => <View style={styles.separator} />}
          onEndReached={() => {
            if (
              !endReachedLockRef.current &&
              hasNext &&
              !isFetchingNextPage &&
              !appendErrorMessage
            ) {
              endReachedLockRef.current = true;
              void onLoadMore().finally(() => {
                endReachedLockRef.current = false;
              });
            }
          }}
          onEndReachedThreshold={0.35}
          renderItem={({ item }) => (
            <NotificationRow
              isDisabled={processingNotificationId !== null}
              isProcessing={processingNotificationId === item.notificationId}
              item={item}
              onPress={onNotificationPress}
            />
          )}
          showsVerticalScrollIndicator={false}
          style={[styles.list, { maxHeight: Math.max(maxHeight - 68, 92) }]}
        />
      )}
    </View>
  );
}

const styles = StyleSheet.create({
  container: {
    overflow: 'hidden',
    maxHeight: 460,
    borderWidth: 1,
    borderColor: BORDER_COLOR,
    borderRadius: 18,
    backgroundColor: SURFACE_COLOR,
    shadowColor: DARK_GREEN_COLOR,
    shadowOffset: { width: 0, height: 10 },
    shadowOpacity: 0.16,
    shadowRadius: 24,
    elevation: 12,
  },
  header: {
    minHeight: 68,
    paddingHorizontal: 18,
    paddingVertical: 14,
    flexDirection: 'row',
    alignItems: 'center',
    justifyContent: 'space-between',
    borderBottomWidth: 1,
    borderBottomColor: BORDER_COLOR,
  },
  headerTitle: {
    color: TEXT_COLOR,
    fontSize: 18,
    fontWeight: '700',
  },
  headerCaption: {
    marginTop: 2,
    color: LABEL_COLOR,
    fontSize: 12,
  },
  list: {
    maxHeight: 390,
  },
  row: {
    paddingHorizontal: 16,
    paddingVertical: 14,
    flexDirection: 'row',
    gap: 12,
    backgroundColor: SURFACE_COLOR,
  },
  unreadRow: {
    backgroundColor: SOFT_GREEN_COLOR,
  },
  rowPressed: {
    opacity: 0.68,
  },
  rowDisabled: {
    opacity: 0.48,
  },
  categoryIcon: {
    width: 36,
    height: 36,
    alignItems: 'center',
    justifyContent: 'center',
    borderRadius: 18,
    backgroundColor: SURFACE_COLOR,
  },
  rowContent: {
    flex: 1,
    minWidth: 0,
  },
  rowTitleLine: {
    flexDirection: 'row',
    alignItems: 'center',
    gap: 7,
  },
  rowTitle: {
    flex: 1,
    color: TEXT_COLOR,
    fontSize: 14,
    fontWeight: '700',
  },
  unreadDot: {
    width: 7,
    height: 7,
    borderRadius: 4,
    backgroundColor: ERROR_COLOR,
  },
  rowBody: {
    marginTop: 4,
    color: DARK_GREEN_COLOR,
    fontSize: 13,
    lineHeight: 18,
  },
  rowTime: {
    marginTop: 6,
    color: LABEL_COLOR,
    fontSize: 11,
  },
  separator: {
    height: 1,
    backgroundColor: BORDER_COLOR,
  },
  stateContainer: {
    minHeight: 160,
    paddingHorizontal: 24,
    alignItems: 'center',
    justifyContent: 'center',
    gap: 10,
  },
  footerState: {
    minHeight: 72,
    paddingHorizontal: 20,
    paddingVertical: 12,
    alignItems: 'center',
    justifyContent: 'center',
    gap: 8,
  },
  stateTitle: {
    color: TEXT_COLOR,
    fontSize: 14,
    fontWeight: '600',
  },
  stateText: {
    color: LABEL_COLOR,
    fontSize: 13,
  },
  errorText: {
    color: ERROR_COLOR,
    fontSize: 13,
    textAlign: 'center',
  },
  retryButton: {
    paddingHorizontal: 14,
    paddingVertical: 8,
    borderRadius: 8,
    backgroundColor: SOFT_GREEN_COLOR,
  },
  retryText: {
    color: DARK_GREEN_COLOR,
    fontSize: 13,
    fontWeight: '700',
  },
});
