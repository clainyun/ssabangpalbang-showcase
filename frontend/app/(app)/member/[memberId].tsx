import Ionicons from '@expo/vector-icons/Ionicons';
import {
  keepPreviousData,
  useInfiniteQuery,
  useMutation,
  useQuery,
  useQueryClient,
} from '@tanstack/react-query';
import { Image, type ImageProps } from 'expo-image';
import { StatusBar } from 'expo-status-bar';
import { useLocalSearchParams, useRouter } from 'expo-router';
import { useState } from 'react';
import {
  ActivityIndicator,
  type ImageSourcePropType,
  type NativeScrollEvent,
  type NativeSyntheticEvent,
  Pressable,
  ScrollView,
  StyleSheet,
  Text,
  View,
} from 'react-native';
import { useSafeAreaInsets } from 'react-native-safe-area-context';

import { appAlert } from '@/components/AppDialog';
import {
  BORDER_COLOR,
  BUTTON_BACKGROUND_COLOR,
  DARK_GREEN_COLOR,
  LABEL_COLOR,
  LIKE_ACCENT_COLOR,
  MUTED_TEXT_COLOR,
  PRIMARY_COLOR,
  SCREEN_BACKGROUND_COLOR,
  SOFT_BACKGROUND_COLOR,
  SOFT_GREEN_COLOR,
  SURFACE_COLOR,
  TEXT_COLOR,
} from '@/constants/colors';
import {
  followMember,
  getMemberReviews,
  getPublicProfile,
  MyPageApiError,
  unfollowMember,
  type CharacterId,
  type PublicProfileFollowing,
  type PublicProfileReport,
  type PublicProfileSection,
  type PublicProfileStudy,
  type ReviewTagSummary,
} from '@/features/member/api/myPage';
import { getDevelopmentPublicProfile } from '@/features/member/developmentMyPageData';
import { useAuthStore } from '@/store/authStore';

type ProfileTabKey = 'studies' | 'reports' | 'following';

type RelationshipTarget = {
  memberId: number;
  nickname: string;
  isFollowing: boolean;
};

const CHARACTER_IMAGES: Record<CharacterId, ImageSourcePropType> = {
  PALBANG: require('../../../assets/images/characters/palbang.png'),
  PALBANG_DOG: require('../../../assets/images/characters/palbang_dog.png'),
  PALBANG_RABBIT: require('../../../assets/images/characters/palbang_rabbit.png'),
};
const PROFILE_TABS: { key: ProfileTabKey; label: string; section: PublicProfileSection }[] = [
  { key: 'studies', label: '스터디', section: 'STUDIES' },
  { key: 'reports', label: '리포트', section: 'REPORTS' },
  { key: 'following', label: '팔로잉', section: 'FOLLOWINGS' },
];
const STUDY_STATUS_LABELS: Record<PublicProfileStudy['status'], string> = {
  RECRUITING: '모집 중',
  CLOSED: '진행 예정',
  IN_PROGRESS: '진행 중',
  COMPLETED: '완료',
};
const AGE_GROUP_LABELS: Record<string, string> = {
  TEENS: '10대',
  TWENTIES: '20대',
  THIRTIES: '30대',
  FORTIES: '40대',
  FIFTIES: '50대',
  SIXTIES_PLUS: '60대 이상',
};

// 원격 프로필 이미지는 요청마다 서명·만료 쿼리가 바뀌는 Presigned GET URL이라, 쿼리를 뗀
// S3 객체 경로를 cacheKey로 고정해 서명이 바뀌어도 expo-image 캐시가 적중되게 한다
// (커뮤니티 목록과 동일 패턴). 로컬 캐릭터 이미지는 기존 require 소스를 그대로 쓴다.
function imageSource(
  profileImageUrl: string | null,
  selectedCharacterId: CharacterId,
): ImageProps['source'] {
  return profileImageUrl
    ? { uri: profileImageUrl, cacheKey: profileImageUrl.split('?')[0] }
    : CHARACTER_IMAGES[selectedCharacterId];
}

function completedDate(completedAt: string | null): string {
  if (!completedAt) return '완료일 정보 없음';

  const date = new Date(completedAt);
  if (Number.isNaN(date.getTime())) return '완료일 정보 없음';

  return `${date.getFullYear()}.${String(date.getMonth() + 1).padStart(2, '0')}.${String(
    date.getDate(),
  ).padStart(2, '0')} 완료`;
}

function ProfileRating({ topTags }: { topTags: ReviewTagSummary[] }) {
  // 상위 1~2개 태그만 배지로 노출한다(마이페이지 ProfileReputation과 동일 톤).
  // 좋아요 수는 프로필 메타(임장 횟수) 옆으로 옮겼고, 리뷰 개수 표시는 제거했다.
  const badges = topTags.slice(0, 2);

  if (badges.length === 0) {
    return (
      <View accessibilityLabel="아직 받은 리뷰가 없어요" style={styles.profileRating}>
        <Text style={styles.profileRatingCount}>아직 받은 리뷰가 없어요</Text>
      </View>
    );
  }

  return (
    <View
      accessibilityLabel={`대표 태그 ${badges.map((tag) => tag.label).join(', ')}`}
      style={styles.profileRating}
    >
      {badges.map((tag) => (
        <View key={tag.code} style={styles.profileTag}>
          <Text style={styles.profileTagEmoji}>{tag.emoji}</Text>
          <Text numberOfLines={1} style={styles.profileTagLabel}>
            {tag.label}
          </Text>
        </View>
      ))}
    </View>
  );
}

function StudyCard({ study, onOpen }: { study: PublicProfileStudy; onOpen: () => void }) {
  const isCompleted = study.status === 'COMPLETED';

  return (
    <Pressable
      accessibilityLabel={`${study.title}, ${STUDY_STATUS_LABELS[study.status]}, 스터디 상세 보기`}
      accessibilityRole="button"
      onPress={onOpen}
      style={({ pressed }) => [styles.listCard, styles.studyCard, pressed && styles.cardPressed]}
    >
      <View style={styles.studyCardHeader}>
        <View style={[styles.studyStatusChip, isCompleted && styles.studyStatusChipCompleted]}>
          <View style={[styles.studyStatusDot, isCompleted && styles.studyStatusDotCompleted]} />
          <Text style={[styles.studyStatusText, isCompleted && styles.studyStatusTextCompleted]}>
            {STUDY_STATUS_LABELS[study.status]}
          </Text>
        </View>
        <View style={styles.studyRoleChip}>
          <Text style={styles.studyRoleText}>{study.role === 'LEADER' ? '스터디장' : '스터디원'}</Text>
        </View>
      </View>
      <Text numberOfLines={2} style={styles.cardTitle}>
        {study.title}
      </Text>
      <View style={styles.studyApartmentRow}>
        <Ionicons color={MUTED_TEXT_COLOR} name="business-outline" size={15} />
        <Text numberOfLines={1} style={styles.studyApartmentName}>
          {study.apartment.name}
        </Text>
        <Ionicons color={MUTED_TEXT_COLOR} name="chevron-forward" size={16} />
      </View>
    </Pressable>
  );
}

function ReportCard({ onPress, report }: { onPress: () => void; report: PublicProfileReport }) {
  return (
    <Pressable
      accessibilityLabel={`${report.title ?? report.apartment.name}, ${completedDate(
        report.completedAt,
      )}`}
      accessibilityRole="button"
      onPress={onPress}
      style={({ pressed }) => [styles.listCard, pressed && styles.cardPressed]}
    >
      <View style={styles.reportHeader}>
        <View style={styles.tagChip}>
          <Text style={styles.tagText}>{report.analysisTags[0] ?? '리포트'}</Text>
        </View>
        {report.favoritedByMe ? <Ionicons color={PRIMARY_COLOR} name="bookmark" size={17} /> : null}
      </View>
      <Text numberOfLines={1} style={styles.cardTitle}>
        {report.title ?? `${report.apartment.name} 임장 리포트`}
      </Text>
      {report.summary ? (
        <Text numberOfLines={2} style={styles.reportSummary}>
          {report.summary}
        </Text>
      ) : null}
      <Text numberOfLines={1} style={styles.reportMeta}>
        {report.apartment.name} · {report.study.title} · 참여 {report.study.participantCount}명
      </Text>
      <Text style={styles.reportDate}>{completedDate(report.completedAt)}</Text>
    </Pressable>
  );
}

function FollowingCard({
  following,
  isUpdating,
  onMessage,
  onOpen,
  onToggleFollow,
}: {
  following: PublicProfileFollowing;
  isUpdating: boolean;
  onMessage: () => void;
  onOpen: () => void;
  onToggleFollow: () => void;
}) {
  const ageLabel = following.ageGroup ? AGE_GROUP_LABELS[following.ageGroup] : null;
  const meta = [ageLabel, `스터디 ${following.participatingStudyCount}개`]
    .filter(Boolean)
    .join(' · ');

  return (
    <View style={[styles.listCard, styles.followingCard]}>
      <Pressable
        accessibilityLabel={`${following.nickname} 공개 프로필 열기`}
        accessibilityRole="button"
        onPress={onOpen}
        style={({ pressed }) => [styles.followingMain, pressed && styles.pressed]}
      >
        <View style={styles.followingAvatar}>
          <Image
            contentFit="contain"
            source={imageSource(following.profileImageUrl, following.selectedCharacterId)}
            style={styles.followingAvatarImage}
          />
        </View>
        <View style={styles.followingDescription}>
          <Text numberOfLines={1} style={styles.followingName}>
            {following.nickname}
          </Text>
          <Text numberOfLines={1} style={styles.followingMeta}>
            {meta}
          </Text>
        </View>
      </Pressable>
      <View style={styles.followingActions}>
        {!following.isMe && (following.isFollowing || following.canFollow) ? (
          <Pressable
            accessibilityLabel={`${following.nickname}님 ${
              following.isFollowing ? '팔로우 해제' : '팔로우'
            }`}
            accessibilityRole="button"
            disabled={isUpdating || (!following.isFollowing && !following.canFollow)}
            onPress={onToggleFollow}
            style={({ pressed }) => [
              styles.compactFollowButton,
              following.isFollowing && styles.compactFollowingButton,
              pressed && styles.pressed,
            ]}
          >
            {isUpdating ? (
              <ActivityIndicator
                color={following.isFollowing ? DARK_GREEN_COLOR : SURFACE_COLOR}
                size="small"
              />
            ) : (
              <>
                <Ionicons
                  color={following.isFollowing ? DARK_GREEN_COLOR : SURFACE_COLOR}
                  name={following.isFollowing ? 'checkmark' : 'person-add-outline'}
                  size={14}
                />
                <Text
                  style={[
                    styles.compactFollowText,
                    following.isFollowing && styles.compactFollowingText,
                  ]}
                >
                  {following.isFollowing ? '팔로잉' : '팔로우'}
                </Text>
              </>
            )}
          </Pressable>
        ) : null}
        {following.canSendMessage ? (
          <Pressable
            accessibilityLabel={`${following.nickname}님에게 쪽지 보내기`}
            accessibilityRole="button"
            onPress={onMessage}
            style={({ pressed }) => [styles.messageButton, pressed && styles.pressed]}
          >
            <Ionicons color={DARK_GREEN_COLOR} name="mail-outline" size={19} />
          </Pressable>
        ) : null}
      </View>
    </View>
  );
}

function QueryState({
  description,
  onRetry,
  title,
}: {
  description?: string;
  onRetry?: () => void;
  title: string;
}) {
  return (
    <View style={styles.stateCard}>
      <View style={styles.stateIcon}>
        <Ionicons color={DARK_GREEN_COLOR} name="person-outline" size={25} />
      </View>
      <Text style={styles.stateTitle}>{title}</Text>
      {description ? <Text style={styles.stateDescription}>{description}</Text> : null}
      {onRetry ? (
        <Pressable
          accessibilityRole="button"
          onPress={onRetry}
          style={({ pressed }) => [styles.retryButton, pressed && styles.pressed]}
        >
          <Text style={styles.retryText}>다시 시도</Text>
        </Pressable>
      ) : null}
    </View>
  );
}

export default function PublicProfileScreen() {
  const router = useRouter();
  const queryClient = useQueryClient();
  const insets = useSafeAreaInsets();
  const accessToken = useAuthStore((state) => state.accessToken);
  const isTemporaryDevelopmentSession = __DEV__ && accessToken === 'dummy-access-token';
  const params = useLocalSearchParams<{ memberId?: string | string[] }>();
  const memberIdParam = Array.isArray(params.memberId) ? params.memberId[0] : params.memberId;
  const memberId = Number(memberIdParam);
  const isValidMemberId = Number.isSafeInteger(memberId) && memberId > 0;
  const [selectedTab, setSelectedTab] = useState<ProfileTabKey>('studies');
  const selectedSection = PROFILE_TABS.find((tab) => tab.key === selectedTab)?.section ?? 'STUDIES';
  const profileQuery = useInfiniteQuery({
    queryKey: [
      'member',
      'public-profile',
      memberId,
      selectedSection,
      { size: 20, source: isTemporaryDevelopmentSession ? 'mock' : 'api' },
    ],
    queryFn: ({ pageParam }) =>
      isTemporaryDevelopmentSession
        ? getDevelopmentPublicProfile(memberId, selectedSection, pageParam, 20)
        : getPublicProfile(memberId, selectedSection, pageParam, 20),
    enabled: isValidMemberId,
    initialPageParam: 0,
    getNextPageParam: (lastPage) => {
      const page =
        selectedSection === 'STUDIES'
          ? lastPage.studies
          : selectedSection === 'REPORTS'
            ? lastPage.reports
            : lastPage.followings;

      return page && page.page + 1 < page.totalPages ? page.page + 1 : undefined;
    },
    placeholderData: keepPreviousData,
  });
  const memberReviewsQuery = useQuery({
    queryKey: ['member', 'reviews', memberId, { size: 1 }],
    queryFn: () => getMemberReviews(memberId, undefined, 1),
    enabled: isValidMemberId && !isTemporaryDevelopmentSession,
  });
  const relationshipMutation = useMutation({
    mutationFn: (target: RelationshipTarget) =>
      target.isFollowing ? unfollowMember(target.memberId) : followMember(target.memberId),
    onSuccess: async () => {
      await Promise.all([
        queryClient.invalidateQueries({ queryKey: ['member', 'me'] }),
        queryClient.invalidateQueries({ queryKey: ['member', 'public-profile'] }),
      ]);
    },
    onError: (error) => {
      appAlert(
        '팔로우 변경 실패',
        error instanceof MyPageApiError
          ? error.message
          : '팔로우 상태를 변경하지 못했습니다. 잠시 후 다시 시도해 주세요.',
      );
    },
  });
  const firstPage = profileQuery.data?.pages[0];
  const profile = firstPage?.memberId === memberId ? firstPage : null;
  const reviewSummary = memberReviewsQuery.data?.summary ??
    profile?.reviewSummary ?? {
      topTags: [] as ReviewTagSummary[],
      likeReceivedCount: 0,
      reviewCount: 0,
    };
  const studies = profileQuery.data?.pages.flatMap((page) => page.studies?.content ?? []) ?? [];
  const reports = profileQuery.data?.pages.flatMap((page) => page.reports?.content ?? []) ?? [];
  const followings =
    profileQuery.data?.pages.flatMap((page) => page.followings?.content ?? []) ?? [];

  const goBack = () => {
    if (router.canGoBack()) {
      router.back();
      return;
    }
    router.replace('/(app)/(tabs)/my');
  };

  const openMember = (targetMemberId: number) => {
    router.push({
      pathname: '/(app)/member/[memberId]',
      params: { memberId: targetMemberId.toString() },
    });
  };

  const openMessage = (target: {
    memberId: number;
    nickname: string;
    profileImageUrl: string | null;
    selectedCharacterId: CharacterId;
  }) => {
    router.push({
      pathname: '/(app)/message/[memberId]',
      params: {
        memberId: target.memberId.toString(),
        nickname: target.nickname,
        profileImageUrl: target.profileImageUrl ?? '',
        selectedCharacterId: target.selectedCharacterId,
      },
    });
  };

  const toggleRelationship = (target: RelationshipTarget) => {
    if (!target.isFollowing) {
      relationshipMutation.mutate(target);
      return;
    }

    appAlert('팔로우 해제', `${target.nickname}님 팔로우를 해제할까요?`, [
      { text: '취소', style: 'cancel' },
      {
        text: '해제',
        style: 'destructive',
        onPress: () => relationshipMutation.mutate(target),
      },
    ]);
  };

  const initialErrorMessage =
    profileQuery.error instanceof MyPageApiError && profileQuery.error.code === 'MEMBER_NOT_FOUND'
      ? '탈퇴했거나 존재하지 않는 회원입니다.'
      : '네트워크 연결을 확인한 뒤 다시 시도해 주세요.';

  const handleScroll = (event: NativeSyntheticEvent<NativeScrollEvent>) => {
    if (!profileQuery.hasNextPage || profileQuery.isFetchingNextPage) return;

    const { contentOffset, contentSize, layoutMeasurement } = event.nativeEvent;
    const remainingDistance = contentSize.height - (contentOffset.y + layoutMeasurement.height);

    if (remainingDistance < 220) void profileQuery.fetchNextPage();
  };

  return (
    <View style={styles.screen}>
      <StatusBar style="dark" />
      <View style={[styles.header, { paddingTop: insets.top + 10 }]}>
        <Pressable
          accessibilityLabel="이전 화면으로 돌아가기"
          accessibilityRole="button"
          onPress={goBack}
          style={({ pressed }) => [styles.headerButton, pressed && styles.pressed]}
        >
          <Ionicons color={TEXT_COLOR} name="chevron-back" size={25} />
        </Pressable>
        <Text style={styles.headerTitle}>프로필</Text>
        <View style={styles.headerButton} />
      </View>

      {!isValidMemberId ? (
        <View style={styles.fullState}>
          <QueryState
            description="프로필 주소를 다시 확인해 주세요."
            title="잘못된 프로필 주소예요"
          />
        </View>
      ) : (profileQuery.isPending || profileQuery.isPlaceholderData) && !profile ? (
        <View style={styles.loadingState}>
          <ActivityIndicator color={PRIMARY_COLOR} size="large" />
          <Text style={styles.loadingText}>공개 프로필을 불러오는 중이에요.</Text>
        </View>
      ) : profileQuery.isError && !profile ? (
        <View style={styles.fullState}>
          <QueryState
            description={initialErrorMessage}
            onRetry={() => void profileQuery.refetch()}
            title="프로필을 불러오지 못했어요"
          />
        </View>
      ) : profile ? (
        <ScrollView
          contentContainerStyle={[
            styles.content,
            { paddingBottom: Math.max(insets.bottom, 20) + 24 },
          ]}
          onScroll={handleScroll}
          scrollEventThrottle={100}
          showsVerticalScrollIndicator={false}
        >
          <View style={styles.profileCard}>
            <View style={styles.profileAvatar}>
              <Image
                contentFit="contain"
                source={imageSource(profile.profileImageUrl, profile.selectedCharacterId)}
                style={styles.profileImage}
              />
            </View>
            <Text style={styles.profileName}>{profile.nickname}</Text>
            <View style={styles.profileMetaRow}>
              <Text style={styles.profileMeta}>
                {[
                  profile.ageGroup ? AGE_GROUP_LABELS[profile.ageGroup] : '연령대 비공개',
                  `임장 ${Math.max(profile.fieldVisitCompletedCount, 0)}회 완료`,
                ].join(' · ')}
              </Text>
              {Math.max(reviewSummary.likeReceivedCount, 0) > 0 ? (
                <View style={styles.profileLike}>
                  <Ionicons color={LIKE_ACCENT_COLOR} name="heart" size={13} />
                  <Text style={styles.profileLikeText}>
                    {Math.max(reviewSummary.likeReceivedCount, 0)}
                  </Text>
                </View>
              ) : null}
            </View>
            <ProfileRating topTags={reviewSummary.topTags} />

            {profile.isMe ? (
              <Pressable
                accessibilityRole="button"
                onPress={() => router.push('/(app)/profile-edit')}
                style={({ pressed }) => [styles.wideActionButton, pressed && styles.pressed]}
              >
                <Text style={styles.wideActionText}>내 프로필 편집</Text>
              </Pressable>
            ) : (
              <View style={styles.actionRow}>
                <Pressable
                  accessibilityLabel={`${profile.nickname}님 ${
                    profile.isFollowing ? '팔로우 해제' : '팔로우'
                  }`}
                  accessibilityRole="button"
                  disabled={relationshipMutation.isPending}
                  onPress={() =>
                    toggleRelationship({
                      memberId: profile.memberId,
                      nickname: profile.nickname,
                      isFollowing: profile.isFollowing,
                    })
                  }
                  style={({ pressed }) => [
                    styles.followButton,
                    profile.isFollowing && styles.followingButton,
                    pressed && styles.pressed,
                  ]}
                >
                  {relationshipMutation.isPending &&
                  relationshipMutation.variables?.memberId === profile.memberId ? (
                    <ActivityIndicator
                      color={profile.isFollowing ? DARK_GREEN_COLOR : SURFACE_COLOR}
                      size="small"
                    />
                  ) : (
                    <>
                      <Ionicons
                        color={profile.isFollowing ? DARK_GREEN_COLOR : SURFACE_COLOR}
                        name={profile.isFollowing ? 'checkmark' : 'person-add-outline'}
                        size={17}
                      />
                      <Text
                        style={[
                          styles.followButtonText,
                          profile.isFollowing && styles.followingButtonText,
                        ]}
                      >
                        {profile.isFollowing ? '팔로잉' : '팔로우'}
                      </Text>
                    </>
                  )}
                </Pressable>
                {profile.canSendMessage ? (
                  <Pressable
                    accessibilityLabel={`${profile.nickname}님에게 쪽지 보내기`}
                    accessibilityRole="button"
                    onPress={() => openMessage(profile)}
                    style={({ pressed }) => [
                      styles.profileMessageButton,
                      pressed && styles.pressed,
                    ]}
                  >
                    <Ionicons color={DARK_GREEN_COLOR} name="mail-outline" size={20} />
                  </Pressable>
                ) : null}
              </View>
            )}
          </View>

          <View accessibilityRole="tablist" style={styles.tabList}>
            {PROFILE_TABS.map((tab) => {
              const isSelected = selectedTab === tab.key;
              const count =
                tab.key === 'studies'
                  ? profile.participatingStudyCount
                  : tab.key === 'reports'
                    ? profile.reportCount
                    : profile.followingCount;

              return (
                <Pressable
                  accessibilityLabel={`${tab.label} ${count}개`}
                  accessibilityRole="tab"
                  accessibilityState={{ selected: isSelected }}
                  key={tab.key}
                  onPress={() => setSelectedTab(tab.key)}
                  style={({ pressed }) => [styles.tab, pressed && styles.pressed]}
                >
                  <Text style={[styles.tabText, isSelected && styles.tabTextActive]}>
                    {tab.label}
                  </Text>
                  <View style={[styles.countBadge, isSelected && styles.countBadgeActive]}>
                    <Text style={[styles.countText, isSelected && styles.countTextActive]}>
                      {count}
                    </Text>
                  </View>
                  {isSelected ? <View style={styles.tabIndicator} /> : null}
                </Pressable>
              );
            })}
          </View>

          <View style={styles.listArea}>
            {profileQuery.isPending || profileQuery.isPlaceholderData ? (
              <View style={styles.listLoading}>
                <ActivityIndicator color={PRIMARY_COLOR} />
                <Text style={styles.loadingText}>목록을 불러오는 중이에요.</Text>
              </View>
            ) : profileQuery.isError ? (
              <QueryState
                description="목록을 다시 불러와 주세요."
                onRetry={() => void profileQuery.refetch()}
                title="목록을 불러오지 못했어요"
              />
            ) : selectedTab === 'studies' ? (
              studies.length > 0 ? (
                <View style={styles.list}>
                  {studies.map((study) => (
                    <StudyCard
                      key={study.studyId}
                      onOpen={() =>
                        router.push({
                          pathname: '/(app)/study/[id]',
                          params: { id: String(study.studyId) },
                        })
                      }
                      study={study}
                    />
                  ))}
                </View>
              ) : (
                <QueryState
                  description="참여 중이거나 완료한 스터디가 생기면 이곳에 표시됩니다."
                  title="공개할 스터디가 없어요"
                />
              )
            ) : selectedTab === 'reports' ? (
              reports.length > 0 ? (
                <View style={styles.list}>
                  {reports.map((report) => (
                    <ReportCard
                      key={report.reportId}
                      onPress={() => {
                        if (isTemporaryDevelopmentSession) {
                          appAlert(
                            '샘플 리포트예요',
                            '실제 공개 리포트에서 상세 화면을 확인할 수 있어요.',
                          );
                          return;
                        }
                        router.push({
                          pathname: '/(app)/report/[reportId]',
                          params: { reportId: String(report.reportId) },
                        });
                      }}
                      report={report}
                    />
                  ))}
                </View>
              ) : (
                <QueryState
                  description="완료된 리포트가 생기면 이곳에 표시됩니다."
                  title="공개된 리포트가 없어요"
                />
              )
            ) : followings.length > 0 ? (
              <View style={styles.list}>
                {followings.map((following) => (
                  <FollowingCard
                    following={following}
                    isUpdating={
                      relationshipMutation.isPending &&
                      relationshipMutation.variables?.memberId === following.memberId
                    }
                    key={following.memberId}
                    onMessage={() => openMessage(following)}
                    onOpen={() => openMember(following.memberId)}
                    onToggleFollow={() =>
                      toggleRelationship({
                        memberId: following.memberId,
                        nickname: following.nickname,
                        isFollowing: following.isFollowing,
                      })
                    }
                  />
                ))}
              </View>
            ) : (
              <QueryState
                description="팔로잉한 사용자가 생기면 이곳에 표시됩니다."
                title="팔로잉 목록이 비어 있어요"
              />
            )}

            {profileQuery.isFetchingNextPage ? (
              <View accessibilityLabel="목록을 더 불러오는 중" style={styles.infiniteLoader}>
                <ActivityIndicator color={PRIMARY_COLOR} size="small" />
                <Text style={styles.infiniteLoaderText}>목록을 더 불러오는 중이에요.</Text>
              </View>
            ) : null}
          </View>
        </ScrollView>
      ) : null}
    </View>
  );
}

const styles = StyleSheet.create({
  screen: {
    flex: 1,
    backgroundColor: SCREEN_BACKGROUND_COLOR,
  },
  header: {
    minHeight: 66,
    paddingHorizontal: 12,
    backgroundColor: SCREEN_BACKGROUND_COLOR,
    flexDirection: 'row',
    alignItems: 'center',
    justifyContent: 'space-between',
  },
  headerButton: {
    width: 44,
    height: 44,
    borderRadius: 22,
    alignItems: 'center',
    justifyContent: 'center',
  },
  headerTitle: {
    color: TEXT_COLOR,
    fontSize: 18,
    fontWeight: '900',
    letterSpacing: -0.4,
  },
  content: {
    paddingHorizontal: 20,
  },
  profileCard: {
    paddingHorizontal: 18,
    paddingTop: 26,
    paddingBottom: 20,
    borderWidth: 1,
    borderColor: BORDER_COLOR,
    borderRadius: 24,
    backgroundColor: SURFACE_COLOR,
    alignItems: 'center',
  },
  profileAvatar: {
    width: 82,
    height: 82,
    borderRadius: 41,
    backgroundColor: SOFT_BACKGROUND_COLOR,
    alignItems: 'center',
    justifyContent: 'center',
    overflow: 'hidden',
  },
  profileImage: {
    width: 70,
    height: 70,
  },
  profileName: {
    marginTop: 13,
    color: TEXT_COLOR,
    fontSize: 21,
    fontWeight: '900',
    letterSpacing: -0.6,
  },
  profileMetaRow: {
    marginTop: 5,
    flexDirection: 'row',
    alignItems: 'center',
    justifyContent: 'center',
    gap: 8,
  },
  profileMeta: {
    color: MUTED_TEXT_COLOR,
    fontSize: 13,
    fontWeight: '700',
  },
  profileRating: {
    minHeight: 24,
    marginTop: 8,
    flexDirection: 'row',
    flexWrap: 'wrap',
    justifyContent: 'center',
    alignItems: 'center',
    gap: 6,
  },
  profileTag: {
    maxWidth: '100%',
    paddingHorizontal: 10,
    paddingVertical: 4,
    borderRadius: 999,
    backgroundColor: SOFT_GREEN_COLOR,
    flexDirection: 'row',
    alignItems: 'center',
    gap: 4,
  },
  profileTagEmoji: {
    fontSize: 12,
  },
  profileTagLabel: {
    minWidth: 0,
    flexShrink: 1,
    color: DARK_GREEN_COLOR,
    fontSize: 12,
    fontWeight: '900',
  },
  profileLike: {
    flexDirection: 'row',
    alignItems: 'center',
    gap: 3,
  },
  profileLikeText: {
    color: LIKE_ACCENT_COLOR,
    fontSize: 12,
    fontWeight: '900',
  },
  profileRatingCount: {
    color: MUTED_TEXT_COLOR,
    fontSize: 11,
    fontWeight: '700',
  },
  wideActionButton: {
    width: '100%',
    minHeight: 46,
    marginTop: 18,
    borderRadius: 16,
    backgroundColor: BUTTON_BACKGROUND_COLOR,
    alignItems: 'center',
    justifyContent: 'center',
  },
  wideActionText: {
    color: DARK_GREEN_COLOR,
    fontSize: 14,
    fontWeight: '900',
  },
  actionRow: {
    width: '100%',
    marginTop: 18,
    flexDirection: 'row',
    gap: 8,
  },
  followButton: {
    minHeight: 46,
    flex: 1,
    borderRadius: 16,
    backgroundColor: PRIMARY_COLOR,
    flexDirection: 'row',
    alignItems: 'center',
    justifyContent: 'center',
    gap: 6,
  },
  followingButton: {
    borderWidth: 1,
    borderColor: BORDER_COLOR,
    backgroundColor: SURFACE_COLOR,
  },
  followButtonText: {
    color: SURFACE_COLOR,
    fontSize: 14,
    fontWeight: '900',
  },
  followingButtonText: {
    color: DARK_GREEN_COLOR,
  },
  profileMessageButton: {
    width: 50,
    minHeight: 46,
    borderRadius: 16,
    backgroundColor: BUTTON_BACKGROUND_COLOR,
    alignItems: 'center',
    justifyContent: 'center',
  },
  tabList: {
    marginTop: 20,
    borderBottomWidth: 1,
    borderBottomColor: BORDER_COLOR,
    flexDirection: 'row',
  },
  tab: {
    position: 'relative',
    minHeight: 50,
    flex: 1,
    flexDirection: 'row',
    alignItems: 'center',
    justifyContent: 'center',
    gap: 6,
  },
  tabText: {
    color: MUTED_TEXT_COLOR,
    fontSize: 14,
    fontWeight: '800',
  },
  tabTextActive: {
    color: DARK_GREEN_COLOR,
    fontWeight: '900',
  },
  countBadge: {
    minWidth: 21,
    height: 21,
    paddingHorizontal: 6,
    borderRadius: 11,
    backgroundColor: SOFT_BACKGROUND_COLOR,
    alignItems: 'center',
    justifyContent: 'center',
  },
  countBadgeActive: {
    backgroundColor: BUTTON_BACKGROUND_COLOR,
  },
  countText: {
    color: MUTED_TEXT_COLOR,
    fontSize: 11,
    fontWeight: '900',
  },
  countTextActive: {
    color: PRIMARY_COLOR,
  },
  tabIndicator: {
    position: 'absolute',
    right: 18,
    bottom: -1,
    left: 18,
    height: 3,
    borderRadius: 2,
    backgroundColor: PRIMARY_COLOR,
  },
  listArea: {
    marginTop: 14,
    gap: 12,
  },
  list: {
    gap: 12,
  },
  listCard: {
    paddingHorizontal: 17,
    paddingVertical: 15,
    borderWidth: 1,
    borderColor: BORDER_COLOR,
    borderRadius: 20,
    backgroundColor: SURFACE_COLOR,
  },
  studyCard: {
    paddingVertical: 16,
  },
  studyCardHeader: {
    flexDirection: 'row',
    alignItems: 'center',
    justifyContent: 'space-between',
  },
  studyStatusChip: {
    minHeight: 25,
    paddingHorizontal: 10,
    borderRadius: 13,
    backgroundColor: SOFT_GREEN_COLOR,
    flexDirection: 'row',
    alignItems: 'center',
    gap: 5,
  },
  studyStatusChipCompleted: {
    backgroundColor: SOFT_BACKGROUND_COLOR,
  },
  studyStatusDot: {
    width: 6,
    height: 6,
    borderRadius: 3,
    backgroundColor: PRIMARY_COLOR,
  },
  studyStatusDotCompleted: {
    backgroundColor: MUTED_TEXT_COLOR,
  },
  studyStatusText: {
    color: DARK_GREEN_COLOR,
    fontSize: 11,
    fontWeight: '900',
  },
  studyStatusTextCompleted: {
    color: MUTED_TEXT_COLOR,
  },
  studyRoleChip: {
    paddingHorizontal: 9,
    paddingVertical: 4,
    borderRadius: 12,
    borderWidth: 1,
    borderColor: BORDER_COLOR,
  },
  studyRoleText: {
    color: LABEL_COLOR,
    fontSize: 10.5,
    fontWeight: '800',
  },
  studyApartmentRow: {
    marginTop: 10,
    flexDirection: 'row',
    alignItems: 'center',
    gap: 6,
  },
  studyApartmentName: {
    minWidth: 0,
    flex: 1,
    color: MUTED_TEXT_COLOR,
    fontSize: 12.5,
    fontWeight: '700',
  },
  reportHeader: {
    flexDirection: 'row',
    alignItems: 'center',
    justifyContent: 'space-between',
  },
  tagChip: {
    paddingHorizontal: 10,
    paddingVertical: 4,
    borderRadius: 12,
    backgroundColor: SOFT_GREEN_COLOR,
  },
  tagText: {
    color: DARK_GREEN_COLOR,
    fontSize: 11,
    fontWeight: '900',
  },
  cardTitle: {
    marginTop: 8,
    color: TEXT_COLOR,
    fontSize: 16,
    fontWeight: '900',
    letterSpacing: -0.4,
  },
  reportSummary: {
    marginTop: 7,
    color: LABEL_COLOR,
    fontSize: 13,
    fontWeight: '600',
    lineHeight: 19,
  },
  reportMeta: {
    marginTop: 9,
    color: MUTED_TEXT_COLOR,
    fontSize: 12,
    fontWeight: '700',
  },
  reportDate: {
    marginTop: 5,
    color: MUTED_TEXT_COLOR,
    fontSize: 12,
    fontWeight: '700',
  },
  followingCard: {
    paddingVertical: 10,
    paddingRight: 12,
    flexDirection: 'row',
    alignItems: 'center',
  },
  followingMain: {
    minWidth: 0,
    flex: 1,
    flexDirection: 'row',
    alignItems: 'center',
  },
  followingAvatar: {
    width: 50,
    height: 50,
    marginRight: 12,
    borderRadius: 25,
    backgroundColor: SOFT_BACKGROUND_COLOR,
    alignItems: 'center',
    justifyContent: 'center',
    overflow: 'hidden',
  },
  followingAvatarImage: {
    width: 43,
    height: 43,
  },
  followingDescription: {
    minWidth: 0,
    flex: 1,
  },
  followingName: {
    color: TEXT_COLOR,
    fontSize: 15,
    fontWeight: '900',
  },
  followingMeta: {
    marginTop: 4,
    color: MUTED_TEXT_COLOR,
    fontSize: 12,
    fontWeight: '700',
  },
  followingActions: {
    marginLeft: 8,
    flexDirection: 'row',
    alignItems: 'center',
    gap: 6,
  },
  compactFollowButton: {
    minWidth: 72,
    height: 38,
    paddingHorizontal: 10,
    borderRadius: 19,
    backgroundColor: PRIMARY_COLOR,
    flexDirection: 'row',
    alignItems: 'center',
    justifyContent: 'center',
    gap: 4,
  },
  compactFollowingButton: {
    backgroundColor: SOFT_GREEN_COLOR,
  },
  compactFollowText: {
    color: SURFACE_COLOR,
    fontSize: 12,
    fontWeight: '900',
  },
  compactFollowingText: {
    color: DARK_GREEN_COLOR,
  },
  messageButton: {
    width: 38,
    height: 38,
    borderRadius: 19,
    backgroundColor: BUTTON_BACKGROUND_COLOR,
    alignItems: 'center',
    justifyContent: 'center',
  },
  stateCard: {
    minHeight: 170,
    paddingHorizontal: 26,
    borderWidth: 1,
    borderColor: BORDER_COLOR,
    borderRadius: 20,
    backgroundColor: SURFACE_COLOR,
    alignItems: 'center',
    justifyContent: 'center',
  },
  stateIcon: {
    width: 48,
    height: 48,
    marginBottom: 12,
    borderRadius: 24,
    backgroundColor: SOFT_GREEN_COLOR,
    alignItems: 'center',
    justifyContent: 'center',
  },
  stateTitle: {
    color: TEXT_COLOR,
    fontSize: 15,
    fontWeight: '900',
    textAlign: 'center',
  },
  stateDescription: {
    marginTop: 7,
    color: MUTED_TEXT_COLOR,
    fontSize: 12,
    fontWeight: '600',
    lineHeight: 18,
    textAlign: 'center',
  },
  retryButton: {
    marginTop: 14,
    paddingHorizontal: 18,
    paddingVertical: 10,
    borderRadius: 18,
    backgroundColor: BUTTON_BACKGROUND_COLOR,
  },
  retryText: {
    color: DARK_GREEN_COLOR,
    fontSize: 13,
    fontWeight: '900',
  },
  infiniteLoader: {
    minHeight: 48,
    flexDirection: 'row',
    alignItems: 'center',
    justifyContent: 'center',
    gap: 8,
  },
  infiniteLoaderText: {
    color: MUTED_TEXT_COLOR,
    fontSize: 13,
    fontWeight: '700',
  },
  fullState: {
    flex: 1,
    paddingHorizontal: 20,
    justifyContent: 'center',
  },
  loadingState: {
    flex: 1,
    gap: 12,
    alignItems: 'center',
    justifyContent: 'center',
  },
  listLoading: {
    minHeight: 150,
    gap: 12,
    alignItems: 'center',
    justifyContent: 'center',
  },
  loadingText: {
    color: MUTED_TEXT_COLOR,
    fontSize: 13,
    fontWeight: '700',
  },
  cardPressed: {
    transform: [{ scale: 0.99 }],
    opacity: 0.86,
  },
  pressed: {
    opacity: 0.66,
  },
});
