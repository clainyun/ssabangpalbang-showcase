import { Feather, Ionicons } from '@expo/vector-icons';
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query';
import { Image } from 'expo-image';
import { LinearGradient } from 'expo-linear-gradient';
import { useFocusEffect, useRouter } from 'expo-router';
import { useCallback, useEffect, useMemo, useRef, useState } from 'react';
import {
  ActivityIndicator,
  type NativeScrollEvent,
  type NativeSyntheticEvent,
  Pressable,
  ScrollView,
  StyleSheet,
  Text,
  TextInput,
  useWindowDimensions,
  View,
} from 'react-native';
import { Gesture, GestureDetector } from 'react-native-gesture-handler';
import Animated, {
  Extrapolation,
  interpolate,
  interpolateColor,
  runOnJS,
  type SharedValue,
  useAnimatedStyle,
  useSharedValue,
  withSequence,
  withSpring,
  withTiming,
} from 'react-native-reanimated';
import { SafeAreaView, useSafeAreaInsets } from 'react-native-safe-area-context';

import { appAlert } from '@/components/AppDialog';
import {
  GlassIconButton,
  GLASS_ICON_BUTTON_ICON_SIZE,
  GLASS_ICON_BUTTON_SIZE,
} from '@/components/GlassIconButton';
import { ScreenGlowBackground } from '@/components/ScreenGlowBackground';
import { MUTED_TEXT_COLOR, PRIMARY_COLOR, TEXT_COLOR } from '@/constants/colors';
import { CommunityApiError } from '@/features/community/api/types';
import type { CommunityPostListItem } from '@/features/community/api/types';
import {
  useLikeCommunityPost,
  useUnlikeCommunityPost,
} from '@/features/community/api/useCommunityPostMutations';
import {
  communityPostsQueryKey,
  useCommunityPostsInfinite,
} from '@/features/community/api/useCommunityPosts';
import { avatarColorFromNickname } from '@/features/community/avatarColor';
import { getFavoriteReports } from '@/features/member/api/favorites';
import {
  favoriteReport as favoriteReportApi,
  unfavoriteReport as unfavoriteReportApi,
} from '@/features/report/api/reportFavorite';
import { refetchOnFocusIfStale } from '@/lib/refetchOnFocusIfStale';
import { useAuthStore } from '@/store/authStore';

const SWIPE_DISTANCE_THRESHOLD = 80;
const SWIPE_VELOCITY_THRESHOLD = 700;
const SWIPE_FAST_DISTANCE_THRESHOLD = 36;
const SWIPE_DIRECTION_RATIO = 1.2;

// 카테고리 필터 pill. 전체는 UI 상 분류, 자유게시판/정보게시판은 게시판(boardType)에 대응.
// 리포트는 별도 boardType이 아니라 INFORMATION 게시판의 자동 생성 글(isAutoReport)이라,
// UI 상으로만 따로 분리한다(정보게시판 탭에서는 제외, 리포트 탭에서만 노출).
const CATEGORIES = [
  { key: 'ALL', label: '전체' },
  { key: 'FREE', label: '자유게시판' },
  { key: 'INFORMATION', label: '정보게시판' },
  { key: 'REPORT', label: '리포트' },
] as const;
type CategoryKey = (typeof CATEGORIES)[number]['key'];

// 리포트 글 판별 — 임장 완료 시 자동 생성되는 INFORMATION 게시글(isAutoReport=true).
function isReportPost(post: CommunityPostListItem): boolean {
  return post.isAutoReport;
}

// 좋아요/하트 강조 라임그린.
const LIME_COLOR = '#8BE04E';
// 컬러형(썸네일 없는) 카드 기본 배경색.
const TRENDING_FALLBACK_COLOR = '#C86F56';

function boardLabelOf(post: CommunityPostListItem): string {
  if (isReportPost(post)) {
    return '리포트';
  }
  return post.boardType === 'FREE' ? '자유게시판' : '정보게시판';
}

function boardBadgeOf(post: CommunityPostListItem): string {
  if (isReportPost(post)) {
    return '리포트';
  }
  return post.boardType === 'FREE' ? '자유' : '정보';
}

// ISO → "방금 전 / N분 전 / N시간 전 / N일 전 / 2026.08.02" 상대시간.
function formatRelativeTime(iso: string): string {
  const then = new Date(iso).getTime();
  if (Number.isNaN(then)) {
    return '';
  }
  const diffMinutes = Math.floor((Date.now() - then) / 60000);
  if (diffMinutes < 1) {
    return '방금 전';
  }
  if (diffMinutes < 60) {
    return `${diffMinutes}분 전`;
  }
  const diffHours = Math.floor(diffMinutes / 60);
  if (diffHours < 24) {
    return `${diffHours}시간 전`;
  }
  const diffDays = Math.floor(diffHours / 24);
  if (diffDays < 7) {
    return `${diffDays}일 전`;
  }
  return iso.slice(0, 10).replace(/-/g, '.');
}

// 메인 피드(지금 1위) 카드에 필요한 최소 필드.
interface FeaturedPost {
  postId: number;
  boardLabel: string;
  authorMemberId: number | null;
  authorNickname: string;
  timeLabel: string;
  title: string;
  imageUrl: string;
  // 썸네일 없을 때 카드 배경색(트렌딩 컬러 카드와 동일 팔레트).
  bgColor?: string;
  viewCount: number;
  commentCount: number;
  likeCount: number;
}

// ④ 지금 뜨는 이야기 카드. 썸네일(imageUrl) 있으면 이미지형, 없으면 컬러형(bgColor).
interface TrendingItem {
  postId: number;
  badgeLabel: string;
  title: string;
  authorMemberId: number | null;
  authorNickname: string;
  commentCount: number;
  likedByMe: boolean;
  // 리포트 자동 글이면 연결된 리포트 ID. 이 카드의 하트는 커뮤니티 좋아요가 아니라
  // "리포트 찜"을 토글한다(리포트 글은 열면 리포트로 들어가므로).
  reportId?: number;
  imageUrl?: string;
  bgColor?: string;
}

// 선택한 카테고리로 게시글 필터.
// - 전체: 모든 글(리포트 포함)
// - 자유게시판: boardType FREE
// - 정보게시판: boardType INFORMATION 중 리포트가 아닌 일반 정보글만
// - 리포트: 자동 생성 리포트 글만
function filterPostsByCategory(
  posts: CommunityPostListItem[],
  category: CategoryKey,
): CommunityPostListItem[] {
  switch (category) {
    case 'ALL':
      return posts;
    case 'FREE':
      return posts.filter((post) => post.boardType === 'FREE');
    case 'INFORMATION':
      return posts.filter((post) => post.boardType === 'INFORMATION' && !isReportPost(post));
    case 'REPORT':
      return posts.filter(isReportPost);
    default:
      return posts;
  }
}

// 필터된 목록에서 메인 카드로 쓸 상위 글들: hotRank가 있는 글을 순위 오름차순으로 먼저,
// 나머지는 기존(최신) 순서 그대로 채워 최대 count개. 스와이프로 3위까지 넘겨 본다.
function pickFeaturedSources(
  posts: CommunityPostListItem[],
  count: number,
): CommunityPostListItem[] {
  if (posts.length === 0) {
    return [];
  }
  const ranked = posts
    .filter((post) => post.hotRank != null)
    .sort((left, right) => (left.hotRank as number) - (right.hotRank as number));
  const rest = posts.filter((post) => post.hotRank == null);
  return [...ranked, ...rest].slice(0, count);
}

function toFeatured(source: CommunityPostListItem): FeaturedPost {
  return {
    postId: source.postId,
    boardLabel: boardLabelOf(source),
    authorMemberId: source.author.memberId,
    authorNickname: source.author.nickname,
    timeLabel: formatRelativeTime(source.createdAt),
    title: source.title,
    imageUrl: source.thumbnailUrl ?? '',
    bgColor: source.thumbnailUrl ? undefined : avatarColorFromNickname(source.title),
    viewCount: source.viewCount,
    commentCount: source.commentCount,
    likeCount: source.likeCount,
  };
}

function toTrending(source: CommunityPostListItem): TrendingItem {
  return {
    postId: source.postId,
    badgeLabel: boardBadgeOf(source),
    title: source.title,
    authorMemberId: source.author.memberId,
    authorNickname: source.author.nickname,
    commentCount: source.commentCount,
    likedByMe: source.likedByMe,
    reportId: isReportPost(source) && source.report ? source.report.reportId : undefined,
    imageUrl: source.thumbnailUrl ?? undefined,
    // 썸네일 없는 컬러 카드 배경 — 닉네임 해시 팔레트로 다채롭게(아바타와 동일 팔레트).
    bgColor: source.thumbnailUrl ? undefined : avatarColorFromNickname(source.title),
  };
}

// 최신순(createdAt 내림차순) 정렬. 동시각이면 postId 큰 것이 먼저.
function sortByLatest(posts: CommunityPostListItem[]): CommunityPostListItem[] {
  return [...posts].sort((left, right) => {
    const difference = new Date(right.createdAt).getTime() - new Date(left.createdAt).getTime();
    if (Number.isNaN(difference) || difference === 0) {
      return right.postId - left.postId;
    }
    return difference;
  });
}

// 상단 헤더의 유리 버튼 — 모양·크기·재질은 앱 공통 GlassIconButton 을 그대로 씁니다.
function HeaderAction({
  icon,
  label,
  onPress,
}: {
  icon: 'search' | 'edit-3' | 'x';
  label: string;
  onPress: () => void;
}) {
  return (
    <GlassIconButton accessibilityLabel={label} onPress={onPress}>
      <Feather color={TEXT_COLOR} name={icon} size={GLASS_ICON_BUTTON_ICON_SIZE} />
    </GlassIconButton>
  );
}

// 카테고리 필터 pill — 선택 시 다크 배경+흰 글씨로 부드럽게 색 전환(reanimated).
function CategoryPill({
  label,
  selected,
  onPress,
}: {
  label: string;
  selected: boolean;
  onPress: () => void;
}) {
  const progress = useSharedValue(selected ? 1 : 0);

  useEffect(() => {
    progress.value = withTiming(selected ? 1 : 0, { duration: 200 });
  }, [progress, selected]);

  const pillStyle = useAnimatedStyle(() => ({
    backgroundColor: interpolateColor(
      progress.value,
      [0, 1],
      ['rgba(255, 255, 255, 0.78)', '#17211C'],
    ),
  }));
  const textStyle = useAnimatedStyle(() => ({
    color: interpolateColor(progress.value, [0, 1], ['#4A574F', '#FFFFFF']),
  }));

  return (
    <Pressable
      accessibilityRole="button"
      accessibilityLabel={label}
      accessibilityState={{ selected }}
      onPress={onPress}
    >
      <Animated.View style={[styles.pill, pillStyle]}>
        <Animated.Text style={[styles.pillText, textStyle]}>{label}</Animated.Text>
      </Animated.View>
    </Pressable>
  );
}

// ③ 메인 피드 카드 — 풀 이미지 + 하단 그라데이션 오버레이 + 흰 텍스트.
// showRankBadge: 실제 hotRank 1위일 때만 "지금 1위" 뱃지 표시(게시판 필터 시 1위가 아닐 수 있음).
function AuthorLinkText({
  memberId,
  nickname,
  onPress,
}: {
  memberId: number | null;
  nickname: string;
  onPress: (memberId: number) => void;
}) {
  return (
    <Text
      accessibilityLabel={memberId === null ? undefined : `${nickname} 공개 프로필 열기`}
      accessibilityRole={memberId === null ? undefined : 'link'}
      onPress={
        memberId === null
          ? undefined
          : (event) => {
              event.stopPropagation();
              onPress(memberId);
            }
      }
      style={memberId === null ? undefined : styles.authorLink}
    >
      {nickname}
    </Text>
  );
}

function MainFeedCard({
  post,
  showRankBadge,
  onAuthorPress,
  onPress,
}: {
  post: FeaturedPost;
  showRankBadge: boolean;
  onAuthorPress: (memberId: number) => void;
  onPress: () => void;
}) {
  return (
    <Pressable
      accessibilityRole="button"
      accessibilityLabel={`${post.title}, ${post.authorNickname}`}
      onPress={onPress}
      style={({ pressed }) => [styles.mainCard, pressed && styles.mainCardPressed]}
    >
      {post.imageUrl ? (
        <Image
          source={{ uri: post.imageUrl, cacheKey: post.imageUrl.split('?')[0] }}
          style={StyleSheet.absoluteFill}
          contentFit="cover"
          // 썸네일 깜빡임 방지: thumbnailUrl은 요청마다 서명·만료 쿼리가 바뀌는 Presigned GET
          // URL이라, 전체 URL을 캐시 키로 쓰면 포커스 refetch마다 캐시 미스 → 재로드 → 깜빡였다.
          // 쿼리를 뗀 S3 객체 경로를 cacheKey로 고정해 서명이 바뀌어도 캐시 적중되게 하고,
          // transition을 없애 만약 재로드되더라도 페이드가 재생되지 않게 한다.
          recyclingKey={String(post.postId)}
          cachePolicy="memory-disk"
        />
      ) : (
        // 사진 없으면 컬러 카드(트렌딩 컬러 카드와 동일 결).
        <View
          style={[
            StyleSheet.absoluteFill,
            { backgroundColor: post.bgColor ?? TRENDING_FALLBACK_COLOR },
          ]}
        />
      )}
      {/* 하단 어둡게 — 텍스트 가독성용 그라데이션 오버레이. */}
      <LinearGradient
        colors={['transparent', 'rgba(15, 23, 19, 0.12)', 'rgba(15, 23, 19, 0.82)']}
        locations={[0, 0.45, 1]}
        style={StyleSheet.absoluteFill}
      />

      {/* 지금 1위 뱃지 (반투명 다크 + 라임 점) — 실제 1위 글일 때만. */}
      {showRankBadge && (
        <View style={styles.rankBadge}>
          <View style={styles.rankDot} />
          <Text style={styles.rankBadgeText}>지금 1위</Text>
        </View>
      )}

      {/* 하단 텍스트 블록. */}
      <View style={styles.mainCardBottom}>
        <Text style={styles.mainCardMeta}>
          {post.boardLabel} · {post.timeLabel}
        </Text>
        <Text numberOfLines={3} style={styles.mainCardTitle}>
          {post.title}
        </Text>
        <View style={styles.mainCardStats}>
          {/* 작성자·댓글 수를 카드 하단으로 이동. AuthorLinkText는 색이 없어 흰색을
              상속받도록 mainCardStat Text로 감싼다. */}
          <Text style={styles.mainCardStat}>
            <AuthorLinkText
              memberId={post.authorMemberId}
              nickname={post.authorNickname}
              onPress={onAuthorPress}
            />
          </Text>
          <Text style={styles.mainCardStat}>댓글 {post.commentCount}</Text>
          <Text style={styles.mainCardStat}>조회 {post.viewCount}</Text>
          <View style={styles.mainCardLike}>
            <Ionicons color={LIME_COLOR} name="heart" size={15} />
            <Text style={styles.mainCardLikeText}>{post.likeCount}</Text>
          </View>
        </View>
      </View>
    </Pressable>
  );
}

type FeaturedCard = { post: FeaturedPost; isTopRanked: boolean };

// 메인 피드 스택 카드 하나 — 찜 스와이프(StackCard)와 동일한 순환 위치(d) 기반 transform.
//  d < 0: 왼쪽으로 밀려나며 tilt(스와이프되는 카드). d ≥ 0: 뒤에서 확대되며 앞으로.
function FeaturedStackCard({
  card,
  index,
  total,
  scroll,
  width,
  isFront,
  onAuthorPress,
  onPress,
}: {
  card: FeaturedCard;
  index: number;
  total: number;
  scroll: SharedValue<number>;
  width: number;
  isFront: boolean;
  onAuthorPress: (memberId: number) => void;
  onPress: () => void;
}) {
  const animatedStyle = useAnimatedStyle(() => {
    let d = (((index - scroll.value) % total) + total) % total;
    if (d > total / 2) {
      d -= total;
    }
    const translateX =
      d < 0 ? interpolate(d, [-1, 0], [-width * 1.15, 0], Extrapolation.CLAMP) : 0;
    const rotate = d < 0 ? interpolate(d, [-1, 0], [-9, 0], Extrapolation.CLAMP) : 0;
    const back = Math.min(Math.max(d, 0), 1); // 뒤 카드 진행도 0..1
    const scale = 1 - 0.05 * back;
    const translateY = 16 * back;
    const opacity =
      d < 0 ? 1 : interpolate(d, [0, 1, 1.6], [1, 1, 0], Extrapolation.CLAMP);
    return {
      opacity,
      zIndex: Math.round(100 - d * 10),
      transform: [{ translateX }, { translateY }, { scale }, { rotate: `${rotate}deg` }],
    };
  });

  return (
    <Animated.View
      pointerEvents={isFront ? 'auto' : 'none'}
      style={[styles.featuredStackCard, animatedStyle]}
    >
      <MainFeedCard
        onAuthorPress={onAuthorPress}
        post={card.post}
        showRankBadge={card.isTopRanked}
        onPress={onPress}
      />
    </Animated.View>
  );
}

// 인디케이터 점 하나 — 스와이프 중 scroll(공유값)에 실시간 반응해 UI 스레드에서 폭·색을
// 보간한다. 이전에는 스프링이 멈춘 뒤(settle) topOffset 상태로만 갱신해, 손을 떼고 애니메이션이
// 끝나야 점이 바뀌어 "느리게 업데이트되는" 느낌이었다. 카드 transform과 동일한 순환 거리(d)로
// 계산해 카드 움직임과 점이 정확히 함께 움직이게 한다.
function FeaturedDot({
  index,
  total,
  scroll,
}: {
  index: number;
  total: number;
  scroll: SharedValue<number>;
}) {
  const dotStyle = useAnimatedStyle(() => {
    let d = (((index - scroll.value) % total) + total) % total;
    if (d > total / 2) {
      d -= total;
    }
    // |d|=0(현재 카드)일 때 1, |d|>=1이면 0. 폭 6→18, 색 회색→PRIMARY 로 보간.
    const proximity = interpolate(Math.abs(d), [0, 1], [1, 0], Extrapolation.CLAMP);
    return {
      width: interpolate(proximity, [0, 1], [6, 18]),
      backgroundColor: interpolateColor(
        proximity,
        [0, 1],
        ['rgba(16, 39, 30, 0.18)', PRIMARY_COLOR],
      ),
    };
  });

  return <Animated.View style={[styles.featuredDot, dotStyle]} />;
}

// 메인 피드 상위 3위까지 카드 스택 스와이프. 찜(SwipeStack)과 동일하게 단일 연속 위치값
// (scroll)으로 모든 카드 transform을 UI 스레드에서 계산해 넘길 때 튐·깜빡임이 없다.
function FeaturedFeedSwipe({
  cards,
  onAuthorPress,
  onOpenPost,
}: {
  cards: FeaturedCard[];
  onAuthorPress: (memberId: number) => void;
  onOpenPost: (postId: number) => void;
}) {
  const { width } = useWindowDimensions();
  const total = cards.length;
  const scroll = useSharedValue(0);
  const startScroll = useSharedValue(0);
  const [topOffset, setTopOffset] = useState(0);

  const settle = (next: number) => setTopOffset(next);

  const pan = Gesture.Pan()
    .enabled(total > 1)
    // 좌우 드래그에만 반응(세로 스크롤 제스처엔 카드가 잡히지 않음).
    .activeOffsetX([-12, 12])
    .onBegin(() => {
      startScroll.value = scroll.value;
    })
    .onUpdate((event) => {
      scroll.value = startScroll.value - event.translationX / width;
    })
    .onEnd((event) => {
      const distanceThreshold = width * 0.26;
      const velocityThreshold = 800;
      const dir =
        event.translationX < -distanceThreshold || event.velocityX < -velocityThreshold
          ? 1
          : event.translationX > distanceThreshold || event.velocityX > velocityThreshold
            ? -1
            : 0;
      const target = Math.round(startScroll.value) + dir;
      scroll.value = withSpring(target, { damping: 25, stiffness: 200 }, (finished) => {
        if (finished) {
          runOnJS(settle)(target);
        }
      });
    });

  if (total === 0) {
    return null;
  }
  const currentIndex = ((topOffset % total) + total) % total;

  return (
    <View style={styles.featuredWrap}>
      <GestureDetector gesture={pan}>
        <View style={styles.featuredStackArea}>
          {cards.map((card, index) => (
            <FeaturedStackCard
              key={card.post.postId}
              card={card}
              index={index}
              total={total}
              scroll={scroll}
              width={width}
              isFront={index === currentIndex}
              onAuthorPress={onAuthorPress}
              onPress={() => onOpenPost(card.post.postId)}
            />
          ))}
        </View>
      </GestureDetector>
      {total > 1 && (
        <View style={styles.featuredDots}>
          {cards.map((card, index) => (
            <FeaturedDot key={card.post.postId} index={index} scroll={scroll} total={total} />
          ))}
        </View>
      )}
    </View>
  );
}

// 하트 오버레이 — 탭하면 좋아요 등록/취소 토글. 카드 onPress(글 열기)와 겹치지 않게
// stopPropagation으로 이벤트 전파를 막는다.
function HeartButton({
  liked,
  busy,
  onToggle,
}: {
  liked: boolean;
  busy: boolean;
  onToggle: () => void;
}) {
  return (
    <Pressable
      accessibilityRole="button"
      accessibilityLabel={liked ? '좋아요 취소' : '좋아요'}
      accessibilityState={{ selected: liked, busy, disabled: busy }}
      disabled={busy}
      hitSlop={10}
      onPress={(event) => {
        event.stopPropagation();
        onToggle();
      }}
      style={({ pressed }) => [styles.heartOverlay, pressed && styles.pressed]}
    >
      <Ionicons
        color={liked ? '#E0603E' : '#9AA49E'}
        name={liked ? 'heart' : 'heart-outline'}
        size={17}
      />
    </Pressable>
  );
}

// ④ 지금 뜨는 이야기 카드 — 썸네일 있으면 이미지형(흰 카드), 없으면 컬러형(통짜).
function TrendingCard({
  item,
  liked,
  likeBusy,
  onAuthorPress,
  onToggle,
  onPress,
}: {
  item: TrendingItem;
  // 하트 채움 여부. 일반 글은 커뮤니티 좋아요, 리포트 글은 리포트 찜 상태를 부모가 계산해 넘긴다.
  liked: boolean;
  likeBusy: boolean;
  onAuthorPress: (memberId: number) => void;
  onToggle: () => void;
  onPress: () => void;
}) {
  const heart = <HeartButton busy={likeBusy} liked={liked} onToggle={onToggle} />;

  if (!item.imageUrl) {
    return (
      <Pressable
        accessibilityRole="button"
        accessibilityLabel={item.title}
        onPress={onPress}
        style={({ pressed }) => [
          styles.trendCard,
          styles.trendCardColor,
          { backgroundColor: item.bgColor ?? TRENDING_FALLBACK_COLOR },
          pressed && styles.cardPressed,
        ]}
      >
        <View style={[styles.trendBadge, styles.trendBadgeFree]}>
          <Text style={styles.trendBadgeFreeText}>{item.badgeLabel}</Text>
        </View>
        <Text numberOfLines={3} style={styles.trendColorTitle}>
          {item.title}
        </Text>
        <Text style={styles.trendColorMeta}>
          <AuthorLinkText
            memberId={item.authorMemberId}
            nickname={item.authorNickname}
            onPress={onAuthorPress}
          />{' '}
          · 댓글 {item.commentCount}
        </Text>
        {heart}
      </Pressable>
    );
  }

  return (
    <Pressable
      accessibilityRole="button"
      accessibilityLabel={item.title}
      onPress={onPress}
      style={({ pressed }) => [styles.trendCard, styles.trendCardImage, pressed && styles.cardPressed]}
    >
      <View style={styles.trendImageWrap}>
        <Image
          source={{ uri: item.imageUrl, cacheKey: item.imageUrl.split('?')[0] }}
          style={StyleSheet.absoluteFill}
          contentFit="cover"
          // 썸네일 깜빡임 방지: Presigned GET URL의 서명 쿼리가 요청마다 바뀌어도 캐시 적중되도록
          // 쿼리를 뗀 S3 객체 경로를 cacheKey로 고정하고, transition을 없애 페이드 재생을 막는다.
          recyclingKey={String(item.postId)}
          cachePolicy="memory-disk"
        />
        <View style={[styles.trendBadge, styles.trendBadgeInfo]}>
          <Text style={styles.trendBadgeInfoText}>{item.badgeLabel}</Text>
        </View>
        {heart}
      </View>
      <View style={styles.trendImageBody}>
        <Text numberOfLines={3} style={styles.trendTitle}>
          {item.title}
        </Text>
        <Text style={styles.trendMeta}>
          <AuthorLinkText
            memberId={item.authorMemberId}
            nickname={item.authorNickname}
            onPress={onAuthorPress}
          />{' '}
          · 댓글 {item.commentCount}
        </Text>
      </View>
    </Pressable>
  );
}

export default function CommunityScreen() {
  const router = useRouter();
  const insets = useSafeAreaInsets();
  const queryClient = useQueryClient();
  const sessionVersion = useAuthStore((state) => state.sessionVersion);
  const isNavigatingRef = useRef(false);
  const searchInputRef = useRef<TextInput>(null);

  const [selectedCategory, setSelectedCategory] = useState<CategoryKey>('ALL');
  const [shouldOpenPostComposer, setShouldOpenPostComposer] = useState(false);
  const [searchOpen, setSearchOpen] = useState(false);
  const [searchQuery, setSearchQuery] = useState('');

  // 실 API 목록 — 전체(정보+자유) 최신글을 커서 무한스크롤로 이어 붙인다.
  // 게시판/카테고리 구분은 병합본에서 클라이언트 필터로 파생.
  const postsQuery = useCommunityPostsInfinite('LATEST');

  const allPosts = useMemo(() => {
    const merged = postsQuery.data?.pages.flatMap((page) => page.content) ?? [];
    // 커서 페이지 사이에 새 글이 끼어들면 같은 글이 두 페이지에 걸쳐 올 수 있어
    // postId로 중복을 제거한다(첫 등장 우선).
    const seen = new Set<number>();
    const deduped = merged.filter((post) => {
      if (seen.has(post.postId)) return false;
      seen.add(post.postId);
      return true;
    });
    return sortByLatest(deduped);
  }, [postsQuery.data]);

  const isLoading = postsQuery.isPending;
  const isError = postsQuery.isError && allPosts.length === 0;

  const { fetchNextPage, hasNextPage, isFetchingNextPage } = postsQuery;
  const handleListScroll = useCallback(
    (event: NativeSyntheticEvent<NativeScrollEvent>) => {
      const { contentOffset, contentSize, layoutMeasurement } = event.nativeEvent;
      const remainingDistance = contentSize.height - (contentOffset.y + layoutMeasurement.height);
      // 매서니 카드가 세로로 길어 여유 있게 400px 남았을 때 다음 페이지를 미리 당긴다.
      if (remainingDistance >= 400) return;
      if (hasNextPage && !isFetchingNextPage) {
        void fetchNextPage();
      }
    },
    [fetchNextPage, hasNextPage, isFetchingNextPage],
  );

  const categoryPosts = filterPostsByCategory(allPosts, selectedCategory);
  // 검색어가 있으면 제목·내용으로 필터(대소문자 무시).
  const normalizedQuery = searchQuery.trim().toLowerCase();
  const visiblePosts =
    normalizedQuery.length === 0
      ? categoryPosts
      : categoryPosts.filter(
          (post) =>
            post.title.toLowerCase().includes(normalizedQuery) ||
            post.contentPreview.toLowerCase().includes(normalizedQuery),
        );
  // 검색 중에는 메인 카드 없이 결과 목록만 보여줌.
  const showMainCard = normalizedQuery.length === 0 && selectedCategory === 'ALL';
  // 메인 피드: 상위 3위까지 스와이프. 검색/게시판 필터 중엔 메인 카드 없이 목록만.
  const featuredSources = showMainCard ? pickFeaturedSources(visiblePosts, 3) : [];
  const featuredIds = new Set(featuredSources.map((post) => post.postId));
  const featuredCards: FeaturedCard[] = featuredSources.map((source, index) => ({
    post: toFeatured(source),
    // hotRank는 게시판(FREE/INFORMATION)별로 매겨져, 두 게시판을 병합하면 1위가 둘일 수 있다.
    // pickFeaturedSources가 순위 오름차순으로 정렬해 두므로, 맨 앞(전체 최상위) 한 장만,
    // 그것이 실제 1위일 때만 "지금 1위" 뱃지를 단다.
    isTopRanked: index === 0 && source.hotRank === 1,
  }));
  const trending = visiblePosts
    .filter((post) => !featuredIds.has(post.postId))
    .map(toTrending);

  const refetchAll = useCallback(() => {
    void postsQuery.refetch();
  }, [postsQuery]);

  // 좋아요 토글 — 훅은 화면 최상위에서만 호출하고, 카드에는 콜백만 내려준다.
  const likeMutation = useLikeCommunityPost();
  const unlikeMutation = useUnlikeCommunityPost();
  // 현재 토글 진행 중인 글(중복 탭 방지) — 해당 카드 하트만 비활성.
  const pendingLikePostId = likeMutation.isPending
    ? likeMutation.variables
    : unlikeMutation.isPending
      ? unlikeMutation.variables
      : undefined;

  const handleToggleLike = useCallback(
    (postId: number, liked: boolean) => {
      if (likeMutation.isPending || unlikeMutation.isPending) {
        return;
      }

      const onError = (error: unknown) => {
        if (error instanceof Error && error.message === '로그인이 필요합니다.') {
          appAlert('로그인이 필요해요', '좋아요를 누르려면 먼저 로그인해주세요.');
          return;
        }
        if (error instanceof CommunityApiError && error.code === 'POST_NOT_FOUND') {
          appAlert('게시글을 확인할 수 없어요', '삭제되었거나 확인할 수 없는 게시글입니다.');
          refetchAll();
          return;
        }
        appAlert(
          liked ? '좋아요를 해제하지 못했어요' : '좋아요를 등록하지 못했어요',
          error instanceof Error ? error.message : '잠시 후 다시 시도해주세요.',
        );
      };

      if (liked) {
        unlikeMutation.mutate(postId, { onError });
      } else {
        likeMutation.mutate(postId, { onError });
      }
    },
    [likeMutation, unlikeMutation, refetchAll],
  );

  // ── 리포트 글의 하트 = 리포트 찜 ──────────────────────────────────────────────
  // 리포트 자동 글은 열면 리포트로 들어가므로, 커뮤니티 좋아요 대신 "리포트 찜"을 토글한다.
  // 리스트 응답엔 찜 상태가 없어, 내가 찜한 리포트 목록을 교차 참조해 채움 여부를 판단한다.
  // (커뮤니티는 로그인 필요 탭이라 항상 조회 가능.)
  const favoriteReportsQuery = useQuery({
    queryKey: ['member', 'me', 'favorite-reports', sessionVersion, { size: 100 }],
    queryFn: () => getFavoriteReports(0, 100),
    staleTime: 30_000,
  });
  const favoriteReportIds = useMemo(
    () => new Set((favoriteReportsQuery.data?.content ?? []).map((report) => report.reportId)),
    [favoriteReportsQuery.data],
  );
  // 토글 직후 서버 재조회 전까지 하트를 즉시 반영하기 위한 낙관적 오버라이드(reportId→찜 여부).
  // 재조회가 진실을 반영하면 해당 항목을 지워 쿼리 결과를 단일 소스로 되돌린다.
  const [reportFavoriteOverride, setReportFavoriteOverride] = useState<Record<number, boolean>>({});
  const isReportFavorited = useCallback(
    (reportId: number): boolean => {
      // 오버라이드가 있으면 그 값을, 없으면(undefined) 찜 목록 교차참조 결과를 쓴다.
      const override = reportFavoriteOverride[reportId];
      return override ?? favoriteReportIds.has(reportId);
    },
    [reportFavoriteOverride, favoriteReportIds],
  );

  const favoriteReportMutation = useMutation({
    mutationFn: (reportId: number) => favoriteReportApi(reportId),
  });
  const unfavoriteReportMutation = useMutation({
    mutationFn: (reportId: number) => unfavoriteReportApi(reportId),
  });
  const pendingReportFavoriteId = favoriteReportMutation.isPending
    ? favoriteReportMutation.variables
    : unfavoriteReportMutation.isPending
      ? unfavoriteReportMutation.variables
      : undefined;

  const handleToggleReportFavorite = useCallback(
    (reportId: number, favorited: boolean) => {
      if (favoriteReportMutation.isPending || unfavoriteReportMutation.isPending) {
        return;
      }
      // 낙관적 반영 — 실패하면 되돌린다.
      setReportFavoriteOverride((prev) => ({ ...prev, [reportId]: !favorited }));

      const settle = () => {
        void queryClient
          .invalidateQueries({ queryKey: ['member', 'me', 'favorite-reports'] })
          .then(() =>
            setReportFavoriteOverride((prev) => {
              const next = { ...prev };
              delete next[reportId];
              return next;
            }),
          );
      };
      const onError = (error: unknown) => {
        setReportFavoriteOverride((prev) => ({ ...prev, [reportId]: favorited }));
        if (error instanceof Error && error.message === '로그인이 필요합니다.') {
          appAlert('로그인이 필요해요', '찜하려면 먼저 로그인해주세요.');
          return;
        }
        appAlert(
          favorited ? '찜을 해제하지 못했어요' : '리포트를 찜하지 못했어요',
          error instanceof Error ? error.message : '잠시 후 다시 시도해주세요.',
        );
      };

      if (favorited) {
        unfavoriteReportMutation.mutate(reportId, { onError, onSuccess: settle });
      } else {
        favoriteReportMutation.mutate(reportId, { onError, onSuccess: settle });
      }
    },
    [favoriteReportMutation, unfavoriteReportMutation, queryClient],
  );

  const toggleSearch = useCallback(() => {
    setSearchOpen((prev) => {
      if (prev) {
        setSearchQuery('');
        return false;
      }
      requestAnimationFrame(() => searchInputRef.current?.focus());
      return true;
    });
  }, []);

  const openPostComposer = useCallback(() => {
    if (isNavigatingRef.current) {
      return;
    }

    isNavigatingRef.current = true;
    router.push('/(app)/post/create');
  }, [router]);

  const openPost = useCallback(
    (postId: number) => {
      if (isNavigatingRef.current) {
        return;
      }

      isNavigatingRef.current = true;
      router.push({
        pathname: '/(app)/post/[id]',
        params: { id: String(postId) },
      });
    },
    [router],
  );

  const openMemberProfile = useCallback(
    (memberId: number) => {
      if (isNavigatingRef.current) return;

      isNavigatingRef.current = true;
      router.push({
        pathname: '/(app)/member/[memberId]',
        params: { memberId: String(memberId) },
      });
    },
    [router],
  );

  // 트렌딩 카드 한 장 렌더 — 리포트 글이면 하트를 "리포트 찜"으로, 일반 글이면 커뮤니티 좋아요로
  // 동작시킨다. reportId != null 을 직접 분기해 각 가지에서 number 로 좁혀지게 한다.
  const renderTrendingCard = (item: TrendingItem) => {
    const reportId = item.reportId;
    const liked = reportId != null ? isReportFavorited(reportId) : item.likedByMe;
    const busy =
      reportId != null ? pendingReportFavoriteId === reportId : pendingLikePostId === item.postId;
    return (
      <TrendingCard
        key={item.postId}
        item={item}
        liked={liked}
        likeBusy={busy}
        onAuthorPress={openMemberProfile}
        onToggle={() =>
          reportId != null
            ? handleToggleReportFavorite(reportId, liked)
            : handleToggleLike(item.postId, item.likedByMe)
        }
        onPress={() => openPost(item.postId)}
      />
    );
  };

  useFocusEffect(
    useCallback(() => {
      isNavigatingRef.current = false;
      setShouldOpenPostComposer(false);
      // 탭 복귀 시 최신 목록 재조회(새 글/좋아요·댓글 수 반영).
      // 20초 이내 재방문은 생략해 불필요한 네트워크·이미지 깜빡임을 줄입니다.
      refetchOnFocusIfStale(
        queryClient,
        communityPostsQueryKey(sessionVersion, 'ALL', 'LATEST'),
        Date.now(),
      );
    }, [queryClient, sessionVersion]),
  );

  useEffect(() => {
    if (!shouldOpenPostComposer) {
      return;
    }

    openPostComposer();
  }, [openPostComposer, shouldOpenPostComposer]);

  const composeSwipeGesture = useMemo(
    () =>
      Gesture.Pan()
        .activeOffsetX(-22)
        .failOffsetX(20)
        .failOffsetY([-18, 18])
        .runOnJS(true)
        .onEnd(({ translationX, translationY, velocityX }) => {
          const isClearlyHorizontal =
            Math.abs(translationX) > Math.abs(translationY) * SWIPE_DIRECTION_RATIO;
          const meetsDistance = translationX <= -SWIPE_DISTANCE_THRESHOLD;
          const meetsVelocity =
            translationX <= -SWIPE_FAST_DISTANCE_THRESHOLD &&
            velocityX <= -SWIPE_VELOCITY_THRESHOLD;

          if (isClearlyHorizontal && (meetsDistance || meetsVelocity)) {
            setShouldOpenPostComposer(true);
          }
        }),
    [],
  );

  return (
    <GestureDetector gesture={composeSwipeGesture}>
      <SafeAreaView edges={['top']} style={styles.safeArea}>
        {/* 커뮤니티 배경 글로우는 라임(노란) 톤 유지 — 공용 컴포넌트 기본(민트) 대신 색 지정. */}
        <ScreenGlowBackground color="#EBF593" />
        <ScrollView
          contentContainerStyle={[
            styles.content,
            { paddingBottom: Math.max(18, insets.bottom + 8) + 90 },
          ]}
          onScroll={handleListScroll}
          scrollEventThrottle={100}
          showsVerticalScrollIndicator={false}
        >
          {/* 검색·글쓰기 버튼은 상단에, 타이틀은 그 아래로 내림. 검색 열면 왼쪽에 입력칸 확장. */}
          <View style={styles.headerActions}>
            {searchOpen && (
              <View style={styles.searchField}>
                <Feather color={MUTED_TEXT_COLOR} name="search" size={18} />
                <TextInput
                  accessibilityLabel="커뮤니티 검색 입력"
                  autoCapitalize="none"
                  onChangeText={setSearchQuery}
                  placeholder="제목·내용 검색"
                  placeholderTextColor={MUTED_TEXT_COLOR}
                  ref={searchInputRef}
                  returnKeyType="search"
                  style={styles.searchInput}
                  value={searchQuery}
                />
                {searchQuery.length > 0 && (
                  <Pressable
                    accessibilityLabel="검색어 지우기"
                    accessibilityRole="button"
                    hitSlop={8}
                    onPress={() => setSearchQuery('')}
                  >
                    <Feather color={MUTED_TEXT_COLOR} name="x-circle" size={17} />
                  </Pressable>
                )}
              </View>
            )}
            <HeaderAction
              icon={searchOpen ? 'x' : 'search'}
              label={searchOpen ? '검색 닫기' : '커뮤니티 검색'}
              onPress={toggleSearch}
            />
            {!searchOpen && (
              <HeaderAction icon="edit-3" label="글쓰기" onPress={openPostComposer} />
            )}
          </View>
          <Text style={styles.headerTitle}>오늘 동네 어때요?</Text>

          {/* ② 카테고리 필터 pill — 개수가 늘어(리포트 추가) 한 줄을 넘칠 수 있어 가로 스크롤로 감쌈. */}
          <ScrollView
            horizontal
            showsHorizontalScrollIndicator={false}
            contentContainerStyle={styles.categoryRow}
          >
            {CATEGORIES.map((category) => (
              <CategoryPill
                key={category.key}
                label={category.label}
                selected={selectedCategory === category.key}
                onPress={() => setSelectedCategory(category.key)}
              />
            ))}
          </ScrollView>

          {isLoading ? (
            <View style={styles.loadingState}>
              <ActivityIndicator color={PRIMARY_COLOR} size="small" />
            </View>
          ) : isError ? (
            <View style={styles.emptyState}>
              <Text style={styles.emptyStateText}>목록을 불러오지 못했어요.</Text>
              <Pressable
                accessibilityRole="button"
                onPress={refetchAll}
                style={({ pressed }) => [styles.retryButton, pressed && styles.pressed]}
              >
                <Text style={styles.retryButtonText}>다시 시도</Text>
              </Pressable>
            </View>
          ) : (
            <>
              {/* 결과가 하나도 없을 때 — 검색 중이면 검색 결과 안내. */}
              {visiblePosts.length === 0 && (
                <View style={styles.emptyState}>
                  <Text style={styles.emptyStateText}>
                    {normalizedQuery.length > 0
                      ? '검색 결과가 없어요.'
                      : '아직 이 게시판에 글이 없어요.'}
                  </Text>
                </View>
              )}

              {/* ③ 메인 피드 — 상위 3위까지 카드 스택 스와이프(찜 넘기듯). */}
              {featuredCards.length > 0 && (
                <FeaturedFeedSwipe
                  cards={featuredCards}
                  onAuthorPress={openMemberProfile}
                  onOpenPost={openPost}
                />
              )}

              {/* ④ 지금 뜨는 이야기 — 메인 카드 외 다른 글이 있을 때만. */}
              {trending.length > 0 && (
                <>
                  <View style={styles.trendSectionHeader}>
                    <Text style={styles.trendSectionTitle}>지금 뜨는 이야기</Text>
                  </View>
                  {/* 매서니 2열 — 순서대로 왼(짝수 index)/오(홀수 index)로 나눠 각 열이 독립적으로 쌓임. */}
                  <View style={styles.trendMasonry}>
                    <View style={styles.trendColumn}>
                      {trending.filter((_, index) => index % 2 === 0).map(renderTrendingCard)}
                    </View>
                    <View style={styles.trendColumn}>
                      {trending.filter((_, index) => index % 2 === 1).map(renderTrendingCard)}
                    </View>
                  </View>
                </>
              )}

              {/* 무한스크롤 다음 페이지 로딩 표시. */}
              {isFetchingNextPage && (
                <View style={styles.listFooterLoading}>
                  <ActivityIndicator color={PRIMARY_COLOR} size="small" />
                </View>
              )}
            </>
          )}

          {/* ⑤ "이야기 남기기" 버튼 — 지금 뜨는 이야기 아래(콘텐츠 흐름 안). */}
          <Pressable
            accessibilityRole="button"
            accessibilityLabel="이야기 남기기"
            onPress={openPostComposer}
            style={({ pressed }) => [styles.composeButton, pressed && styles.composeButtonPressed]}
          >
            <Feather color="#FFFFFF" name="plus" size={18} />
            <Text style={styles.composeButtonText}>이야기 남기기</Text>
          </Pressable>
        </ScrollView>
      </SafeAreaView>
    </GestureDetector>
  );
}

const styles = StyleSheet.create({
  safeArea: {
    flex: 1,
    backgroundColor: '#FFFFFF',
  },
  content: {
    paddingHorizontal: 22,
    paddingTop: 8,
  },
  headerTitle: {
    // 버튼(상단)보다 아래로 내려 여백 확보 — 인기 게시글 쯤 높이까지.
    // (버튼을 8 내린 만큼 제목 marginTop을 줄여 제목 위치는 유지.)
    marginTop: 14,
    paddingBottom: 18,
    color: '#17211C',
    fontSize: 27,
    lineHeight: 34,
    // IBM Plex는 700(Bold)이 최대 → 더 굵은 디스플레이용으로 Noto Sans KR Black(900) 사용.
    fontFamily: 'NotoSansKR_900Black',
    letterSpacing: -0.6,
  },
  headerActions: {
    flexDirection: 'row',
    alignItems: 'center',
    justifyContent: 'flex-end',
    gap: 10,
    // 검색·연필 버튼을 아주 살짝 아래로.
    marginTop: 8,
  },
  // 검색 열었을 때 왼쪽으로 확장되는 입력 필드. 옆 버튼과 높이·곡률을 맞춥니다.
  searchField: {
    flex: 1,
    height: GLASS_ICON_BUTTON_SIZE,
    flexDirection: 'row',
    alignItems: 'center',
    gap: 8,
    paddingHorizontal: 16,
    borderRadius: GLASS_ICON_BUTTON_SIZE / 2,
    backgroundColor: '#FFFFFF',
    boxShadow: '0px 4px 12px rgba(16, 39, 30, 0.12)',
  },
  searchInput: {
    flex: 1,
    paddingVertical: 0,
    color: '#17211C',
    fontSize: 14,
    fontFamily: 'IBMPlexSansKR_600SemiBold',
    letterSpacing: -0.3,
  },
  // pill 행 — 4개가 메인 카드 너비에 맞춰 양옆 균형(space-between)으로 배치.
  categoryRow: {
    flexDirection: 'row',
    justifyContent: 'flex-start',
    alignItems: 'center',
    gap: 8,
    paddingTop: 4,
    paddingBottom: 20,
  },
  pill: {
    height: 44,
    paddingHorizontal: 14,
    borderRadius: 22,
    alignItems: 'center',
    justifyContent: 'center',
    boxShadow: '0px 4px 10px rgba(16, 39, 30, 0.16)',
  },
  pillText: {
    fontSize: 14,
    fontFamily: 'IBMPlexSansKR_600SemiBold',
    letterSpacing: -0.3,
  },
  loadingState: {
    paddingVertical: 72,
    alignItems: 'center',
    justifyContent: 'center',
  },
  listFooterLoading: {
    paddingVertical: 18,
    alignItems: 'center',
    justifyContent: 'center',
  },
  emptyState: {
    paddingVertical: 56,
    alignItems: 'center',
    justifyContent: 'center',
    gap: 14,
  },
  emptyStateText: {
    color: MUTED_TEXT_COLOR,
    fontSize: 14,
    fontFamily: 'IBMPlexSansKR_600SemiBold',
    letterSpacing: -0.3,
  },
  retryButton: {
    minHeight: 42,
    paddingHorizontal: 20,
    borderRadius: 21,
    alignItems: 'center',
    justifyContent: 'center',
    backgroundColor: '#EDF7F0',
  },
  retryButtonText: {
    color: PRIMARY_COLOR,
    fontSize: 13,
    fontFamily: 'IBMPlexSansKR_700Bold',
  },
  // 메인 피드 스택 래퍼 — 카드 높이(300) + 하단 여백 + 인디케이터 공간.
  featuredWrap: {
    marginBottom: 28,
  },
  featuredStackArea: {
    height: 300,
  },
  featuredStackCard: {
    position: 'absolute',
    left: 0,
    right: 0,
    top: 0,
  },
  featuredDots: {
    flexDirection: 'row',
    justifyContent: 'center',
    alignItems: 'center',
    gap: 6,
    marginTop: 14,
  },
  featuredDot: {
    // 폭·색은 FeaturedDot 의 애니메이션 스타일이 실시간으로 덮어쓴다(활성 6→18, 회색→PRIMARY).
    width: 6,
    height: 6,
    borderRadius: 3,
    backgroundColor: 'rgba(16, 39, 30, 0.18)',
  },
  mainCard: {
    height: 300,
    borderRadius: 24,
    overflow: 'hidden',
    // 이미지 로드 전/실패 시 폴백 배경.
    backgroundColor: '#DCE3DD',
    boxShadow: '0px 10px 24px rgba(16, 39, 30, 0.18)',
  },
  mainCardPressed: {
    opacity: 0.92,
  },
  rankBadge: {
    position: 'absolute',
    top: 18,
    left: 18,
    flexDirection: 'row',
    alignItems: 'center',
    gap: 7,
    paddingHorizontal: 14,
    paddingVertical: 9,
    borderRadius: 20,
    backgroundColor: 'rgba(16, 39, 30, 0.5)',
  },
  rankDot: {
    width: 8,
    height: 8,
    borderRadius: 4,
    backgroundColor: LIME_COLOR,
  },
  rankBadgeText: {
    color: '#FFFFFF',
    fontSize: 13,
    fontFamily: 'IBMPlexSansKR_700Bold',
    letterSpacing: -0.2,
  },
  mainCardBottom: {
    position: 'absolute',
    left: 20,
    right: 20,
    bottom: 20,
  },
  mainCardMeta: {
    marginBottom: 8,
    color: 'rgba(255, 255, 255, 0.9)',
    fontSize: 12.5,
    fontFamily: 'IBMPlexSansKR_600SemiBold',
    letterSpacing: -0.2,
  },
  mainCardTitle: {
    color: '#FFFFFF',
    fontSize: 23,
    lineHeight: 30,
    fontFamily: 'IBMPlexSansKR_700Bold',
    letterSpacing: -0.5,
  },
  mainCardStats: {
    marginTop: 12,
    flexDirection: 'row',
    alignItems: 'center',
    gap: 14,
  },
  mainCardStat: {
    color: 'rgba(255, 255, 255, 0.92)',
    fontSize: 13,
    fontFamily: 'IBMPlexSansKR_600SemiBold',
  },
  mainCardLike: {
    flexDirection: 'row',
    alignItems: 'center',
    gap: 4,
  },
  mainCardLikeText: {
    color: LIME_COLOR,
    fontSize: 13,
    fontFamily: 'IBMPlexSansKR_700Bold',
  },
  trendSectionHeader: {
    flexDirection: 'row',
    alignItems: 'baseline',
    justifyContent: 'space-between',
    paddingHorizontal: 2,
    marginBottom: 14,
  },
  trendSectionTitle: {
    color: '#17211C',
    fontSize: 18,
    fontFamily: 'IBMPlexSansKR_700Bold',
    letterSpacing: -0.4,
  },
  // 매서니: 두 열을 space-between으로 절반씩, 각 열은 세로로 카드 쌓기(gap).
  trendMasonry: {
    flexDirection: 'row',
    alignItems: 'flex-start',
    justifyContent: 'space-between',
    marginBottom: 28,
  },
  trendColumn: {
    width: '48%',
    gap: 12,
  },
  trendCard: {
    width: '100%',
    borderRadius: 20,
    overflow: 'hidden',
    boxShadow: '0px 6px 16px rgba(16, 39, 30, 0.12)',
  },
  cardPressed: {
    opacity: 0.9,
  },
  trendCardImage: {
    backgroundColor: '#FFFFFF',
  },
  trendImageWrap: {
    height: 150,
    backgroundColor: '#DCE3DD',
  },
  trendImageBody: {
    paddingHorizontal: 14,
    paddingTop: 12,
    paddingBottom: 14,
  },
  trendTitle: {
    color: '#17211C',
    fontSize: 15,
    fontFamily: 'IBMPlexSansKR_700Bold',
    letterSpacing: -0.3,
  },
  trendMeta: {
    marginTop: 12,
    color: MUTED_TEXT_COLOR,
    fontSize: 11.5,
    fontFamily: 'IBMPlexSansKR_600SemiBold',
  },
  trendCardColor: {
    minHeight: 222,
    padding: 16,
  },
  trendColorTitle: {
    marginTop: 26,
    color: '#FFFFFF',
    fontSize: 20,
    lineHeight: 27,
    fontFamily: 'IBMPlexSansKR_700Bold',
    letterSpacing: -0.4,
  },
  trendColorMeta: {
    marginTop: 16,
    color: 'rgba(255, 255, 255, 0.92)',
    fontSize: 11.5,
    fontFamily: 'IBMPlexSansKR_600SemiBold',
  },
  authorLink: {
    fontFamily: 'IBMPlexSansKR_700Bold',
    textDecorationLine: 'underline',
  },
  trendBadge: {
    alignSelf: 'flex-start',
    paddingHorizontal: 11,
    paddingVertical: 6,
    borderRadius: 13,
  },
  trendBadgeInfo: {
    position: 'absolute',
    top: 10,
    left: 10,
    backgroundColor: 'rgba(255, 255, 255, 0.92)',
  },
  trendBadgeInfoText: {
    color: '#3A443E',
    fontSize: 11,
    fontFamily: 'IBMPlexSansKR_700Bold',
  },
  trendBadgeFree: {
    backgroundColor: 'rgba(255, 255, 255, 0.22)',
  },
  trendBadgeFreeText: {
    color: '#FFFFFF',
    fontSize: 11,
    fontFamily: 'IBMPlexSansKR_700Bold',
  },
  heartOverlay: {
    position: 'absolute',
    right: 10,
    top: 10,
    width: 32,
    height: 32,
    borderRadius: 16,
    alignItems: 'center',
    justifyContent: 'center',
    backgroundColor: 'rgba(255, 255, 255, 0.92)',
  },
  pressed: {
    opacity: 0.68,
  },
  composeButton: {
    alignSelf: 'center',
    flexDirection: 'row',
    alignItems: 'center',
    gap: 8,
    height: 52,
    paddingHorizontal: 22,
    borderRadius: 26,
    backgroundColor: '#17211C',
    boxShadow: '0px 8px 20px rgba(16, 39, 30, 0.28)',
  },
  composeButtonText: {
    color: '#FFFFFF',
    fontSize: 15,
    fontFamily: 'IBMPlexSansKR_700Bold',
    letterSpacing: -0.3,
  },
  composeButtonPressed: {
    opacity: 0.85,
    transform: [{ scale: 0.98 }],
  },
});
