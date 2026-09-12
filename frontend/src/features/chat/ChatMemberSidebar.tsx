import Ionicons from '@expo/vector-icons/Ionicons';
import { Image } from 'expo-image';
import { useCallback, useEffect, useRef, useState } from 'react';
import {
  ActivityIndicator,
  FlatList,
  Modal,
  Pressable,
  StyleSheet,
  Text,
  useWindowDimensions,
  View,
} from 'react-native';
import Animated, {
  Easing,
  interpolate,
  runOnJS,
  useAnimatedStyle,
  useSharedValue,
  withTiming,
} from 'react-native-reanimated';
import { useSafeAreaInsets } from 'react-native-safe-area-context';

import {
  BORDER_COLOR,
  DARK_GREEN_COLOR,
  ERROR_COLOR,
  MODAL_SCRIM_COLOR,
  MUTED_TEXT_COLOR,
  PRIMARY_COLOR,
  SOFT_BACKGROUND_COLOR,
  SOFT_GREEN_COLOR,
  SURFACE_COLOR,
  TEXT_COLOR,
} from '@/constants/colors';
import { characterAvatarSource } from '@/features/character/characterAvatar';
import type { StudyMember } from '@/features/study/api/types';
import { useMembers } from '@/features/study/useStudyManagement';

interface ChatMemberSidebarProps {
  visible: boolean;
  studyId: number;
  onClose: () => void;
  onSelectMember: (memberId: number) => void;
}

const PANEL_WIDTH_RATIO = 0.82;
const PANEL_MAX_WIDTH = 380;
const OPEN_DURATION_MS = 260;
const CLOSE_DURATION_MS = 190;

interface SidebarContentProps {
  studyId: number;
  onClose: () => void;
  onMemberPress: (memberId: number) => void;
}

function MemberAvatar({ member }: { member: StudyMember }) {
  const [failedProfileImageUrl, setFailedProfileImageUrl] = useState<string | null>(null);
  const useProfileImage =
    Boolean(member.profileImageUrl) && member.profileImageUrl !== failedProfileImageUrl;
  // 원격 프로필 이미지는 Presigned 서명 쿼리가 요청마다 바뀌므로, 쿼리를 뗀 경로를
  // cacheKey로 고정해 서명이 바뀌어도 캐시 적중되게 한다(커뮤니티 목록과 동일 패턴).
  const source = useProfileImage
    ? { uri: member.profileImageUrl!, cacheKey: member.profileImageUrl!.split('?')[0] }
    : characterAvatarSource(member.selectedCharacterId);

  return (
    <View style={styles.avatarWrap}>
      <Image
        onError={
          useProfileImage ? () => setFailedProfileImageUrl(member.profileImageUrl) : undefined
        }
        contentFit={useProfileImage ? 'cover' : 'contain'}
        recyclingKey={String(member.memberId)}
        source={source}
        style={styles.avatar}
      />
    </View>
  );
}

function SidebarContent({ studyId, onClose, onMemberPress }: SidebarContentProps) {
  const membersQuery = useMembers(studyId);
  const members = membersQuery.data?.members ?? [];
  const memberCount = membersQuery.data?.currentMemberCount ?? members.length;

  return (
    <>
      <View style={styles.header}>
        <View style={styles.headerCopy}>
          <Text style={styles.title}>채팅방 구성원</Text>
          <Text style={styles.memberCount}>
            {membersQuery.data ? `${memberCount}명` : '인원 확인 중'}
          </Text>
        </View>
        <Pressable
          accessibilityLabel="채팅방 구성원 목록 닫기"
          accessibilityRole="button"
          hitSlop={10}
          onPress={onClose}
          style={({ pressed }) => [styles.closeButton, pressed && styles.pressed]}
        >
          <Ionicons color={DARK_GREEN_COLOR} name="close" size={24} />
        </Pressable>
      </View>

      <View style={styles.body}>
        {membersQuery.isPending ? (
          <View accessibilityLiveRegion="polite" style={styles.stateContainer}>
            <ActivityIndicator color={PRIMARY_COLOR} />
            <Text style={styles.stateText}>구성원을 불러오는 중입니다.</Text>
          </View>
        ) : membersQuery.isError ? (
          <View accessibilityRole="alert" style={styles.stateContainer}>
            <Ionicons color={ERROR_COLOR} name="alert-circle-outline" size={32} />
            <Text style={styles.errorText}>구성원 목록을 불러오지 못했습니다.</Text>
            <Pressable
              accessibilityRole="button"
              accessibilityState={{
                busy: membersQuery.isFetching,
                disabled: membersQuery.isFetching,
              }}
              disabled={membersQuery.isFetching}
              onPress={() => void membersQuery.refetch()}
              style={({ pressed }) => [
                styles.retryButton,
                pressed && styles.pressed,
                membersQuery.isFetching && styles.retryButtonDisabled,
              ]}
            >
              {membersQuery.isFetching ? (
                <ActivityIndicator color={DARK_GREEN_COLOR} size="small" />
              ) : (
                <Text style={styles.retryText}>다시 시도</Text>
              )}
            </Pressable>
          </View>
        ) : members.length === 0 ? (
          <View style={styles.stateContainer}>
            <Ionicons color={MUTED_TEXT_COLOR} name="people-outline" size={34} />
            <Text style={styles.stateText}>표시할 구성원이 없습니다.</Text>
          </View>
        ) : (
          <FlatList
            contentContainerStyle={styles.memberList}
            data={members}
            ItemSeparatorComponent={() => <View style={styles.separator} />}
            keyExtractor={(member) => member.memberId.toString()}
            renderItem={({ item }) => {
              const isLeader = item.role === 'LEADER';
              return (
                <Pressable
                  accessibilityLabel={`${item.nickname}, ${isLeader ? '스터디장' : '스터디원'}, 공개 프로필 열기`}
                  accessibilityRole="button"
                  onPress={() => onMemberPress(item.memberId)}
                  style={({ pressed }) => [styles.memberRow, pressed && styles.memberRowPressed]}
                >
                  <MemberAvatar member={item} />
                  <View style={styles.memberCopy}>
                    <Text numberOfLines={1} style={styles.nickname}>
                      {item.nickname}
                    </Text>
                    <Text style={styles.memberRole}>{isLeader ? '스터디장' : '스터디원'}</Text>
                  </View>
                  {isLeader ? (
                    <View style={styles.leaderBadge}>
                      <Text style={styles.leaderBadgeText}>스터디장</Text>
                    </View>
                  ) : null}
                  <Ionicons color={MUTED_TEXT_COLOR} name="chevron-forward" size={18} />
                </Pressable>
              );
            }}
            showsVerticalScrollIndicator={false}
          />
        )}
      </View>
    </>
  );
}

export function ChatMemberSidebar({
  visible,
  studyId,
  onClose,
  onSelectMember,
}: ChatMemberSidebarProps) {
  const insets = useSafeAreaInsets();
  const { width: screenWidth } = useWindowDimensions();
  const panelWidth = Math.min(screenWidth * PANEL_WIDTH_RATIO, PANEL_MAX_WIDTH);
  const [rendered, setRendered] = useState(visible);
  const progress = useSharedValue(visible ? 1 : 0);
  const pendingMemberIdRef = useRef<number | null>(null);
  const onSelectMemberRef = useRef(onSelectMember);

  useEffect(() => {
    onSelectMemberRef.current = onSelectMember;
  }, [onSelectMember]);

  const finishClosing = useCallback(() => {
    setRendered(false);
    const memberId = pendingMemberIdRef.current;
    pendingMemberIdRef.current = null;
    if (memberId !== null) {
      onSelectMemberRef.current(memberId);
    }
  }, []);

  const handleMemberPress = useCallback(
    (memberId: number) => {
      if (pendingMemberIdRef.current !== null) return;
      pendingMemberIdRef.current = memberId;
      onClose();
    },
    [onClose],
  );

  // 닫힘 애니메이션이 끝날 때까지 Modal을 유지합니다.
  if (visible && !rendered) {
    setRendered(true);
  }

  useEffect(() => {
    if (visible) {
      pendingMemberIdRef.current = null;
      progress.value = 0;
      progress.value = withTiming(1, {
        duration: OPEN_DURATION_MS,
        easing: Easing.out(Easing.cubic),
      });
      return;
    }

    if (rendered) {
      progress.value = withTiming(
        0,
        { duration: CLOSE_DURATION_MS, easing: Easing.in(Easing.quad) },
        (finished) => {
          if (finished) runOnJS(finishClosing)();
        },
      );
    }
  }, [finishClosing, progress, rendered, visible]);

  const scrimStyle = useAnimatedStyle(() => ({
    opacity: progress.value,
  }));
  const panelStyle = useAnimatedStyle(() => ({
    transform: [{ translateX: interpolate(progress.value, [0, 1], [panelWidth, 0]) }],
  }));

  if (!rendered) return null;

  return (
    <Modal
      animationType="none"
      onRequestClose={onClose}
      statusBarTranslucent
      transparent
      visible={rendered}
    >
      <View
        accessibilityViewIsModal
        onAccessibilityEscape={onClose}
        style={styles.modalRoot}
        testID="chat-member-sidebar"
      >
        <Animated.View style={[styles.scrim, scrimStyle]}>
          <Pressable
            accessible={false}
            importantForAccessibility="no"
            onPress={onClose}
            style={StyleSheet.absoluteFill}
          />
        </Animated.View>

        <Animated.View
          style={[
            styles.panel,
            {
              width: panelWidth,
              paddingTop: Math.max(insets.top, 16),
              paddingBottom: Math.max(insets.bottom, 16),
            },
            panelStyle,
          ]}
        >
          <SidebarContent onClose={onClose} onMemberPress={handleMemberPress} studyId={studyId} />
        </Animated.View>
      </View>
    </Modal>
  );
}

const styles = StyleSheet.create({
  modalRoot: {
    flex: 1,
  },
  scrim: {
    position: 'absolute',
    top: 0,
    right: 0,
    bottom: 0,
    left: 0,
    backgroundColor: MODAL_SCRIM_COLOR,
  },
  panel: {
    position: 'absolute',
    top: 0,
    right: 0,
    bottom: 0,
    backgroundColor: SURFACE_COLOR,
    shadowColor: DARK_GREEN_COLOR,
    shadowOffset: { width: -8, height: 0 },
    shadowOpacity: 0.2,
    shadowRadius: 20,
    elevation: 18,
  },
  header: {
    minHeight: 68,
    paddingHorizontal: 20,
    paddingBottom: 14,
    flexDirection: 'row',
    alignItems: 'center',
    borderBottomWidth: StyleSheet.hairlineWidth,
    borderBottomColor: BORDER_COLOR,
  },
  headerCopy: {
    flex: 1,
  },
  title: {
    color: TEXT_COLOR,
    fontSize: 20,
    fontWeight: '800',
  },
  memberCount: {
    marginTop: 3,
    color: MUTED_TEXT_COLOR,
    fontSize: 13,
  },
  closeButton: {
    width: 40,
    height: 40,
    borderRadius: 20,
    alignItems: 'center',
    justifyContent: 'center',
    backgroundColor: SOFT_BACKGROUND_COLOR,
  },
  pressed: {
    opacity: 0.58,
  },
  body: {
    flex: 1,
  },
  stateContainer: {
    flex: 1,
    paddingHorizontal: 24,
    alignItems: 'center',
    justifyContent: 'center',
    gap: 12,
  },
  stateText: {
    color: MUTED_TEXT_COLOR,
    fontSize: 14,
    textAlign: 'center',
  },
  errorText: {
    color: ERROR_COLOR,
    fontSize: 14,
    lineHeight: 20,
    textAlign: 'center',
  },
  retryButton: {
    minWidth: 92,
    minHeight: 40,
    paddingHorizontal: 16,
    borderRadius: 12,
    alignItems: 'center',
    justifyContent: 'center',
    backgroundColor: SOFT_GREEN_COLOR,
  },
  retryButtonDisabled: {
    opacity: 0.65,
  },
  retryText: {
    color: DARK_GREEN_COLOR,
    fontSize: 14,
    fontWeight: '700',
  },
  memberList: {
    paddingVertical: 8,
  },
  memberRow: {
    minHeight: 72,
    paddingHorizontal: 18,
    paddingVertical: 10,
    flexDirection: 'row',
    alignItems: 'center',
    gap: 12,
  },
  memberRowPressed: {
    backgroundColor: SOFT_GREEN_COLOR,
  },
  separator: {
    height: StyleSheet.hairlineWidth,
    marginLeft: 78,
    backgroundColor: BORDER_COLOR,
  },
  avatarWrap: {
    width: 48,
    height: 48,
    borderRadius: 24,
    alignItems: 'center',
    justifyContent: 'center',
    overflow: 'hidden',
    backgroundColor: SOFT_BACKGROUND_COLOR,
  },
  avatar: {
    width: 46,
    height: 46,
    borderRadius: 23,
  },
  memberCopy: {
    flex: 1,
    minWidth: 0,
  },
  nickname: {
    color: TEXT_COLOR,
    fontSize: 15,
    fontWeight: '700',
  },
  memberRole: {
    marginTop: 3,
    color: MUTED_TEXT_COLOR,
    fontSize: 12,
  },
  leaderBadge: {
    paddingHorizontal: 8,
    paddingVertical: 4,
    borderRadius: 999,
    backgroundColor: SOFT_GREEN_COLOR,
  },
  leaderBadgeText: {
    color: PRIMARY_COLOR,
    fontSize: 11,
    fontWeight: '800',
  },
});
