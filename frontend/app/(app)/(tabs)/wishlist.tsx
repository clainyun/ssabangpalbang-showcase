import { Feather } from '@expo/vector-icons';
import Ionicons from '@expo/vector-icons/Ionicons';
import { useInfiniteQuery, useMutation, useQueryClient } from '@tanstack/react-query';
import { useRouter } from 'expo-router';
import { Image } from 'expo-image';
import { StatusBar } from 'expo-status-bar';
import { useState } from 'react';
import {
  ActivityIndicator,
  FlatList,
  Pressable,
  StyleSheet,
  Text,
  TextInput,
  useWindowDimensions,
  View,
} from 'react-native';
import { Gesture, GestureDetector } from 'react-native-gesture-handler';
import Animated, {
  Extrapolation,
  FadeIn,
  interpolate,
  runOnJS,
  type SharedValue,
  useAnimatedStyle,
  useSharedValue,
  withSpring,
  withTiming,
} from 'react-native-reanimated';
import { useSafeAreaInsets } from 'react-native-safe-area-context';
import Svg, { Defs, RadialGradient, Rect, Stop } from 'react-native-svg';

import { appAlert } from '@/components/AppDialog';
import {
  DARK_GREEN_COLOR,
  DIVIDER_COLOR,
  LIKE_ACCENT_COLOR,
  MUTED_TEXT_COLOR,
  PRIMARY_COLOR,
  SOFT_BACKGROUND_COLOR,
  SOFT_GREEN_COLOR,
  SURFACE_COLOR,
  TEXT_COLOR,
} from '@/constants/colors';
import {
  type FavoriteApartment,
  FavoritesApiError,
  type FavoriteReport,
  getFavoriteApartments,
  getFavoriteReports,
  unfavoriteApartment,
  unfavoriteReport,
} from '@/features/member/api/favorites';
import { apartmentImageSource } from '@/lib/apartmentImage';
import { useAuthStore } from '@/store/authStore';

// 배경 상단 우측 민트 블러 원(radial 글로우) — 커뮤니티 화면과 동일한 방식.
const BG_GLOW_COLOR = '#A6F0C4';

type WishTab = 'PLACES' | 'REPORTS';
// 목록 보기 전용 상태.
type WishViewMode = 'STACK' | 'LIST';
type WishSort = 'RECENT' | 'OLDEST' | 'NAME';
const SORT_OPTIONS: { value: WishSort; label: string }[] = [
  { value: 'RECENT', label: '최근순' },
  { value: 'OLDEST', label: '처음순' },
  { value: 'NAME', label: '이름순' },
];
const SORT_LABELS: Record<WishSort, string> = {
  RECENT: '최근순',
  OLDEST: '처음순',
  NAME: '이름순',
};
const PAGE_SIZE = 20;
// 하단 플로팅 탭바(+안전영역) 높이 — 스택 카드가 탭바에 안 가리도록 이만큼 여유를 둠.
const TAB_BAR_CLEARANCE = 100;
const MAX_STACK_CARD_HEIGHT = 500;
const MIN_STACK_CARD_HEIGHT = 300;

// 카드 대표 이미지 — 실 API에 이미지 URL이 없어 임시로 picsum 원격 이미지 사용(imageIndex를 seed로 고정).
// TODO(실 데이터): 게시글/아파트 대표 이미지 URL이 생기면 교체.
function cardImageUri(imageIndex: number): string {
  return `https://picsum.photos/seed/ssabang-wish-${imageIndex}/900/640`;
}

// 카드 하단 2단 정보 한 블록.
interface CardStat {
  label: string;
  value: string;
  /** 값 아래 보조 설명. 없으면 해당 줄을 렌더하지 않음. */
  sub?: string;
}

// 장소/리포트 카드를 하나의 뷰모델로 통일 — 실 API·더미 모두 이 형태로 변환해 같은 카드 컴포넌트로 렌더.
interface CardVM {
  key: string;
  imageIndex: number;
  /** 서버 대표 이미지 URL(있으면 우선). 없으면 로컬/원격 폴백. */
  imageUrl?: string | null;
  /** 서버 이미지가 없을 때 로컬 일러스트를 고를 단지 id. 아파트/리포트 카드에만 있음. */
  imageFallbackId?: number;
  caption: string;
  region: string;
  title: string;
  /** 제목 아래 회색 보조 텍스트. 없으면 렌더하지 않음(리포트 카드는 미사용). */
  subtitle?: string;
  left: CardStat;
  right: CardStat;
  /** 찜한 시각(ISO). 목록 보기의 "저장" 표시·정렬(최근/처음)에 사용. 스택 카드는 미사용. */
  savedAt: string;
  // 실 API 찜만 해제 대상(장소/리포트 + id). 더미 샘플은 undefined(로컬에서만 숨김).
  favorite?: { kind: 'apartment' | 'report'; id: number };
}

// "43500"(만원) → "4억 3,500" (억/만 단위, 접미사 없음 — 레퍼런스와 동일).
function formatCompactPrice(price?: number | null): string {
  if (price === undefined || price === null) {
    return '정보 없음';
  }
  const normalized = Math.max(Math.trunc(price), 0);
  const eok = Math.floor(normalized / 10_000);
  const man = normalized % 10_000;
  if (eok > 0 && man > 0) {
    return `${eok}억 ${man.toLocaleString('ko-KR')}`;
  }
  if (eok > 0) {
    return `${eok}억`;
  }
  return normalized.toLocaleString('ko-KR');
}

function formatYearMonth(value?: string | null): string {
  if (!value) {
    return '';
  }
  const iso = value.slice(0, 7);
  return /^\d{4}-\d{2}$/.test(iso) ? iso.replace('-', '.') : '';
}

function formatDate(value?: string | null): string {
  if (!value) {
    return '진행 중';
  }
  const iso = value.slice(0, 10);
  return /^\d{4}-\d{2}-\d{2}$/.test(iso) ? iso.replace(/-/g, '.') : '진행 중';
}

function regionFromAddress(address?: string | null): string {
  const match = address?.match(/([가-힣]+동)/);
  return match?.[1] ?? '';
}

// 실 API 장소 찜 → 카드 뷰모델.
function apartmentToCard(item: FavoriteApartment, imageIndex: number): CardVM {
  const transaction = item.latestTransaction;
  const areaAndDate = transaction
    ? [
        transaction.exclusiveArea ? `${Math.round(transaction.exclusiveArea)}㎡` : null,
        formatYearMonth(transaction.dealDate),
      ]
        .filter(Boolean)
        .join(' · ')
    : '최근 거래 없음';

  return {
    key: `apt-${item.apartmentId}`,
    imageIndex,
    imageUrl: item.imageUrl,
    imageFallbackId: item.apartmentId,
    caption: '단지 배치 이미지',
    region: item.dongName || item.districtName || '관심 단지',
    title: item.name,
    subtitle: item.address,
    left: {
      label: '최근 실거래',
      value: transaction ? formatCompactPrice(transaction.price) : '정보 없음',
      sub: areaAndDate,
    },
    right: {
      label: '연결된 탐색',
      value: `모집 스터디 ${item.recruitingStudyCount}`,
      sub: `완료 리포트 ${item.completedReportCount}`,
    },
    savedAt: item.favoritedAt,
    favorite: { kind: 'apartment', id: item.apartmentId },
  };
}

// 실 API 리포트 찜 → 카드 뷰모델.
function reportToCard(item: FavoriteReport, imageIndex: number): CardVM {
  const tags = item.analysisTags.slice(0, 3);
  return {
    key: `rpt-${item.reportId}`,
    imageIndex,
    imageUrl: item.apartment.imageUrl,
    imageFallbackId: item.apartment.apartmentId,
    caption: '리포트 대표 이미지',
    region:
      item.apartment.dongName ||
      regionFromAddress(item.apartment.address) ||
      item.apartment.name,
    title: item.title,
    left: {
      label: '임장 포인트',
      value: tags.length > 0 ? tags.join(' · ') : '—',
    },
    right: {
      label: '생성 완료일',
      value: formatDate(item.completedAt),
    },
    savedAt: item.favoritedAt,
    favorite: { kind: 'report', id: item.reportId },
  };
}

// 목록 보기 표시용 "저장" 날짜 라벨. favoritedAt(ISO) → YYYY.MM.DD.
function formatSavedDate(value?: string | null): string {
  const iso = value?.slice(0, 10);
  return iso && /^\d{4}-\d{2}-\d{2}$/.test(iso) ? iso.replace(/-/g, '.') : '';
}

// 목록 보기: 현재까지 로드된 카드에 대해 클라이언트 검색·정렬.
// 주의(페이지네이션 제약): 무한스크롤로 로드된 페이지만 대상이라 아직 불러오지 않은
// 찜은 검색·정렬에 포함되지 않는다. 스크롤로 다음 페이지를 로드하면 대상이 늘어난다.
function filterAndSortCards(cards: CardVM[], query: string, sort: WishSort): CardVM[] {
  const normalized = query.trim().toLocaleLowerCase('ko-KR');
  const filtered = normalized
    ? cards.filter((card) =>
        `${card.title} ${card.region} ${card.subtitle ?? ''}`
          .toLocaleLowerCase('ko-KR')
          .includes(normalized),
      )
    : cards;
  const sorted = [...filtered];
  sorted.sort((a, b) => {
    if (sort === 'NAME') {
      return a.title.localeCompare(b.title, 'ko-KR');
    }
    // ISO 문자열 사전순 = 시간순. RECENT는 최신 우선(내림차순).
    const cmp = a.savedAt.localeCompare(b.savedAt);
    return sort === 'RECENT' ? -cmp : cmp;
  });
  return sorted;
}

function pageIndicator(index: number, total: number): string {
  return `${String(index + 1).padStart(2, '0')} / ${String(total).padStart(2, '0')}`;
}

// "저장됨" 배지 — 소프트 그린 + 체크.
function SavedBadge() {
  return (
    <View style={styles.savedBadge}>
      <Feather color={PRIMARY_COLOR} name="check" size={13} />
      <Text style={styles.savedBadgeText}>저장됨</Text>
    </View>
  );
}

// 카드 본체 — fill=true면 높이 100%(스택), false면 자연 높이(리스트).
function WishCard({
  card,
  indicatorText,
  fill,
  onPress,
  onUnfavorite,
  unfavoritePending,
}: {
  card: CardVM;
  indicatorText: string;
  fill: boolean;
  onPress: () => void;
  onUnfavorite: () => void;
  unfavoritePending: boolean;
}) {
  const [serverImageFailed, setServerImageFailed] = useState(false);
  // 서버 대표 이미지 우선. 없거나(null) 로드 실패 시 폴백: 단지 id가 있으면(아파트·리포트 찜)
  // 로컬 일러스트, 그 외(더미 샘플)는 기존 원격 임시 이미지.
  const fallbackImageSource =
    card.imageFallbackId != null
      ? apartmentImageSource(card.imageFallbackId)
      : { uri: cardImageUri(card.imageIndex) };
  // 서버 대표 이미지는 요청마다 서명·만료 쿼리가 바뀌는 Presigned GET URL이라, 전체 URL을
  // 캐시 키로 쓰면 refetch마다 캐시 미스 → 재로드 → 깜빡인다. 쿼리를 뗀 S3 객체 경로를
  // cacheKey로 고정해 서명이 바뀌어도 캐시 적중되게 한다(커뮤니티 목록과 동일 패턴).
  const imageSource =
    card.imageUrl && !serverImageFailed
      ? { uri: card.imageUrl, cacheKey: card.imageUrl.split('?')[0] }
      : fallbackImageSource;

  return (
    <Pressable
      accessibilityRole="button"
      accessibilityLabel={`${card.title} 상세 보기`}
      onPress={onPress}
      style={({ pressed }) => [styles.card, fill && styles.cardFill, pressed && styles.cardPressed]}
    >
      <View style={[styles.cardImageBox, fill ? styles.cardImageFill : styles.cardImageFixed]}>
        <Image
          source={imageSource}
          contentFit="cover"
          // 캐시된 이미지는 재렌더/스와이프 때 그대로 유지 → 깜빡임 방지. recyclingKey는
          // 서명이 섞인 URL 대신 데이터 id 기반 안정 키(card.key)로 두고, transition을
          // 없애 만약 재로드되더라도 페이드가 재생되지 않게 한다.
          recyclingKey={card.key}
          style={styles.cardImage}
          onError={() => {
            if (card.imageUrl) setServerImageFailed(true);
          }}
        />
        <SavedBadge />
        {/* 우측 상단 찜 해제 버튼. */}
        <Pressable
          accessibilityRole="button"
          accessibilityLabel={`${card.title} 찜 해제`}
          disabled={unfavoritePending}
          hitSlop={8}
          onPress={onUnfavorite}
          style={({ pressed }) => [styles.unfavoriteButton, pressed && styles.pressed]}
        >
          {unfavoritePending ? (
            <ActivityIndicator color={TEXT_COLOR} size="small" />
          ) : (
            <Text style={styles.unfavoriteButtonText}>찜 해제</Text>
          )}
        </Pressable>
        <View style={styles.captionPill}>
          <Text style={styles.captionText}>{card.caption}</Text>
        </View>
      </View>

      <View style={styles.cardBody}>
        <View style={styles.regionRow}>
          <Text style={styles.regionText}>{card.region}</Text>
          <Text style={styles.indicatorText}>{indicatorText}</Text>
        </View>
        <Text numberOfLines={1} style={styles.cardTitle}>
          {card.title}
        </Text>
        {card.subtitle ? (
          <Text numberOfLines={1} style={styles.cardSubtitle}>
            {card.subtitle}
          </Text>
        ) : null}

        <View style={styles.cardDivider} />

        <View style={styles.statRow}>
          <CardStatBlock stat={card.left} />
          <CardStatBlock stat={card.right} />
        </View>
      </View>
    </Pressable>
  );
}

function CardStatBlock({ stat }: { stat: CardStat }) {
  return (
    <View style={styles.statBlock}>
      <Text style={styles.statLabel}>{stat.label}</Text>
      <Text numberOfLines={1} style={styles.statValue}>
        {stat.value}
      </Text>
      {stat.sub ? (
        <Text numberOfLines={1} style={styles.statSub}>
          {stat.sub}
        </Text>
      ) : null}
    </View>
  );
}

export default function WishlistScreen() {
  const insets = useSafeAreaInsets();
  const { width, height } = useWindowDimensions();
  const router = useRouter();
  const accessToken = useAuthStore((state) => state.accessToken);
  const sessionVersion = useAuthStore((state) => state.sessionVersion);
  const queryClient = useQueryClient();

  const [activeTab, setActiveTab] = useState<WishTab>('PLACES');
  const [tabsWidth, setTabsWidth] = useState(0);
  const [stackHeight, setStackHeight] = useState(0);
  // 보기 전환(겹쳐/목록). 탭(장소/리포트)과 독립적으로 동작.
  const [viewMode, setViewMode] = useState<WishViewMode>('STACK');
  // 목록 보기 검색·정렬(현재 로드된 항목 대상). 장소/리포트 탭 공통으로 유지.
  const [searchQuery, setSearchQuery] = useState('');
  const [sortMode, setSortMode] = useState<WishSort>('RECENT');
  const [sortOpen, setSortOpen] = useState(false);

  const underline = useSharedValue(0);
  const underlineStyle = useAnimatedStyle(() => ({
    transform: [{ translateX: underline.value * (tabsWidth / 2) }],
  }));

  const apartmentsQuery = useInfiniteQuery({
    queryKey: ['member', 'me', 'favorite-apartments', sessionVersion, { size: PAGE_SIZE }],
    queryFn: ({ pageParam }) => getFavoriteApartments(pageParam, PAGE_SIZE),
    enabled: accessToken !== null,
    initialPageParam: 0,
    getNextPageParam: (lastPage) =>
      lastPage.page + 1 < lastPage.totalPages ? lastPage.page + 1 : undefined,
  });
  const reportsQuery = useInfiniteQuery({
    queryKey: ['member', 'me', 'favorite-reports', sessionVersion, { size: PAGE_SIZE }],
    queryFn: ({ pageParam }) => getFavoriteReports(pageParam, PAGE_SIZE),
    enabled: accessToken !== null,
    initialPageParam: 0,
    getNextPageParam: (lastPage) =>
      lastPage.page + 1 < lastPage.totalPages ? lastPage.page + 1 : undefined,
  });

  const unfavoriteApartmentMutation = useMutation({
    mutationFn: unfavoriteApartment,
    onSuccess: () =>
      queryClient.invalidateQueries({ queryKey: ['member', 'me', 'favorite-apartments'] }),
    onError: (error) =>
      appAlert(
        '찜 해제 실패',
        error instanceof FavoritesApiError ? error.message : '잠시 후 다시 시도해 주세요.',
      ),
  });
  const unfavoriteReportMutation = useMutation({
    mutationFn: unfavoriteReport,
    onSuccess: () =>
      queryClient.invalidateQueries({ queryKey: ['member', 'me', 'favorite-reports'] }),
    onError: (error) =>
      appAlert(
        '찜 해제 실패',
        error instanceof FavoritesApiError ? error.message : '잠시 후 다시 시도해 주세요.',
      ),
  });

  const handleUnfavorite = (card: CardVM) => {
    if (!card.favorite) {
      return;
    }
    if (card.favorite.kind === 'apartment') {
      unfavoriteApartmentMutation.mutate(card.favorite.id);
    } else {
      unfavoriteReportMutation.mutate(card.favorite.id);
    }
  };

  const handleCardPress = (card: CardVM) => {
    if (!card.favorite) {
      return;
    }
    if (card.favorite.kind === 'apartment') {
      router.push({
        pathname: '/(app)/apartment/[id]',
        params: { id: String(card.favorite.id) },
      });
    } else {
      router.push({
        pathname: '/(app)/report/[reportId]',
        params: { reportId: String(card.favorite.id) },
      });
    }
  };

  // 해제 진행 중인 카드 key(스피너 표시용).
  const pendingKey = unfavoriteApartmentMutation.isPending
    ? `apt-${unfavoriteApartmentMutation.variables}`
    : unfavoriteReportMutation.isPending
      ? `rpt-${unfavoriteReportMutation.variables}`
      : null;

  const apartments = apartmentsQuery.data?.pages.flatMap((page) => page.content) ?? [];
  const reports = reportsQuery.data?.pages.flatMap((page) => page.content) ?? [];

  const placeCards: CardVM[] = apartments.map((item, index) => apartmentToCard(item, index));
  const reportCards: CardVM[] = reports.map((item, index) => reportToCard(item, index));

  const placeCount = apartmentsQuery.data?.pages[0]?.totalElements ?? apartments.length;
  const reportCount = reportsQuery.data?.pages[0]?.totalElements ?? reports.length;

  const isPlaces = activeTab === 'PLACES';
  const cards = isPlaces ? placeCards : reportCards;
  const activeQuery = isPlaces ? apartmentsQuery : reportsQuery;
  // 전체 화면 스피너·에러 화면은 '그 탭이 아직 보여 줄 게 하나도 없을 때'만 띄웁니다.
  // isLoading/isError 만 보면, 이미 받아 둔 목록이 있는데도 백그라운드 재조회가 시작되거나
  // (찜 해제 후 invalidate) 그 재조회가 실패하는 순간 목록과 겹쳐/목록 토글이 통째로
  // 언마운트됐다가 다시 붙어 점멸합니다.
  const hasActiveData = activeQuery.data !== undefined;
  const showLoadingState = activeQuery.isLoading && !hasActiveData;
  const showErrorState = activeQuery.isError && !hasActiveData;

  const selectTab = (tab: WishTab) => {
    setActiveTab(tab);
    setSortOpen(false);
    underline.value = withTiming(tab === 'PLACES' ? 0 : 1, { duration: 220 });
  };

  const selectView = (mode: WishViewMode) => {
    setViewMode(mode);
    if (mode === 'STACK') {
      setSortOpen(false);
    }
  };

  const resetSearch = () => {
    setSearchQuery('');
    setSortMode('RECENT');
    setSortOpen(false);
  };

  // 목록 보기에 실제 표시할 카드(현재 로드분 대상 검색·정렬).
  const listCards = filterAndSortCards(cards, searchQuery, sortMode);
  // 겹쳐/목록 토글은 실제 카드가 있을 때만 노출(로딩·에러·0개 상태에서는 숨김).
  // 카드가 있다는 것 자체가 로딩·에러 화면이 아니라는 뜻이므로 카드 수만 봅니다.
  const showViewSwitch = cards.length > 0;

  return (
    <View style={[styles.screen, { paddingTop: Math.max(insets.top, 14) }]}>
      <StatusBar style="dark" />

      {/* 배경 상단 우측 민트 블러 원(radial 글로우). */}
      <Svg pointerEvents="none" style={StyleSheet.absoluteFill} width={width} height={height}>
        <Defs>
          <RadialGradient
            id="wishBgGlow"
            cx={width * 0.78}
            cy={height * 0.08}
            r={width * 0.82}
            gradientUnits="userSpaceOnUse"
          >
            <Stop offset="0" stopColor={BG_GLOW_COLOR} stopOpacity={0.72} />
            <Stop offset="0.5" stopColor={BG_GLOW_COLOR} stopOpacity={0.26} />
            <Stop offset="1" stopColor={BG_GLOW_COLOR} stopOpacity={0} />
          </RadialGradient>
        </Defs>
        <Rect width={width} height={height} fill="url(#wishBgGlow)" />
      </Svg>

      {/* 타이틀 영역 + 보기 전환 토글. */}
      <View style={styles.titleArea}>
        <Text style={styles.title}>
          찜한 {isPlaces ? '장소' : '리포트'}
        </Text>
        {showViewSwitch ? (
          <ViewSwitch mode={viewMode} onChange={selectView} />
        ) : null}
      </View>

      {/* 탭 — 장소/리포트 + 애니메이션 밑줄. */}
      <View style={styles.tabsWrap}>
        <View style={styles.tabsRow} onLayout={(event) => setTabsWidth(event.nativeEvent.layout.width)}>
          {(['PLACES', 'REPORTS'] as const).map((tab) => {
            const selected = activeTab === tab;
            return (
              <Pressable
                accessibilityRole="button"
                accessibilityState={{ selected }}
                key={tab}
                onPress={() => selectTab(tab)}
                style={styles.tabButton}
              >
                <Text style={[styles.tabLabel, selected && styles.tabLabelActive]}>
                  {tab === 'PLACES' ? '장소' : '리포트'}{' '}
                  <Text style={styles.tabCount}>{tab === 'PLACES' ? placeCount : reportCount}</Text>
                </Text>
              </Pressable>
            );
          })}
          <Animated.View style={[styles.tabUnderline, { width: tabsWidth / 2 }, underlineStyle]} />
        </View>
      </View>

      {/* 콘텐츠 — 겹쳐(스택) / 목록. */}
      <View style={styles.content} onLayout={(event) => setStackHeight(event.nativeEvent.layout.height)}>
        {showLoadingState ? (
          <View style={styles.centerState}>
            <ActivityIndicator color={PRIMARY_COLOR} />
          </View>
        ) : showErrorState ? (
          <View style={styles.centerState}>
            <Text style={styles.emptyTitle}>찜 목록을 불러오지 못했어요</Text>
            <Pressable
              accessibilityRole="button"
              onPress={() => void activeQuery.refetch()}
              style={({ pressed }) => [styles.ctaButton, pressed && styles.pressed]}
            >
              <Text style={styles.ctaButtonText}>다시 시도</Text>
            </Pressable>
          </View>
        ) : cards.length === 0 ? (
          <EmptyWishlist isPlaces={isPlaces} router={router} />
        ) : viewMode === 'LIST' ? (
          <Animated.View key="list" entering={FadeIn.duration(180)} style={styles.flex}>
            <WishListView
              cards={listCards}
              searchQuery={searchQuery}
              onSearchChange={setSearchQuery}
              sortMode={sortMode}
              onSortChange={(next) => {
                setSortMode(next);
                setSortOpen(false);
              }}
              sortOpen={sortOpen}
              onToggleSort={() => setSortOpen((prev) => !prev)}
              onCloseSort={() => setSortOpen(false)}
              onResetSearch={resetSearch}
              onCardPress={handleCardPress}
              onUnfavorite={handleUnfavorite}
              pendingKey={pendingKey}
              hasNextPage={activeQuery.hasNextPage}
              isFetchingNextPage={activeQuery.isFetchingNextPage}
              onEndReached={() => {
                if (activeQuery.hasNextPage && !activeQuery.isFetchingNextPage) {
                  void activeQuery.fetchNextPage();
                }
              }}
              bottomInset={insets.bottom + TAB_BAR_CLEARANCE}
            />
          </Animated.View>
        ) : (
          <Animated.View key="stack" entering={FadeIn.duration(180)} style={styles.flex}>
            {stackHeight > 0 && (
              <SwipeStack
                key={activeTab}
                cards={cards}
                height={Math.max(
                  MIN_STACK_CARD_HEIGHT,
                  Math.min(
                    MAX_STACK_CARD_HEIGHT,
                    stackHeight - insets.bottom - TAB_BAR_CLEARANCE,
                  ),
                )}
                width={width}
                onCardPress={handleCardPress}
                onUnfavorite={handleUnfavorite}
                pendingKey={pendingKey}
              />
            )}
          </Animated.View>
        )}
      </View>
    </View>
  );
}

// 카드 스택 스와이프 — 단일 연속 위치값(scroll)으로 모든 카드의 transform을 UI 스레드에서
// 계산한다. 인덱스 교체로 카드/문구가 재렌더되던 레이스가 사라져 넘길 때 튐·깜빡임이 없다.
// 각 카드는 card.key로 고정되어 remount 되지 않는다.
function SwipeStack({
  cards,
  height,
  width,
  onCardPress,
  onUnfavorite,
  pendingKey,
}: {
  cards: CardVM[];
  height: number;
  width: number;
  onCardPress: (card: CardVM) => void;
  onUnfavorite: (card: CardVM) => void;
  pendingKey: string | null;
}) {
  const total = cards.length;
  const scroll = useSharedValue(0); // 연속 위치. round(scroll) = 현재 최상단 카드 오프셋.
  const startScroll = useSharedValue(0);
  const [topOffset, setTopOffset] = useState(0);

  const settle = (next: number) => setTopOffset(next);

  const pan = Gesture.Pan()
    .enabled(total > 1)
    // 좌우 드래그에만 반응(세로 제스처엔 카드가 잡히지 않음).
    .activeOffsetX([-12, 12])
    .onBegin(() => {
      startScroll.value = scroll.value;
    })
    .onUpdate((event) => {
      // 왼쪽으로 끌면 다음 카드로(scroll 증가), 오른쪽이면 이전 카드로.
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
      // damping 25(임계값 ≈28)로 오버슈트를 아주 살짝만 남긴다.
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
    <GestureDetector gesture={pan}>
      <View style={[styles.stackArea, { height }]}>
        {cards.map((card, i) => (
          <StackCard
            key={card.key}
            card={card}
            index={i}
            total={total}
            scroll={scroll}
            width={width}
            height={height}
            indicatorText={pageIndicator(i, total)}
            isFront={i === currentIndex}
            onPress={() => onCardPress(card)}
            onUnfavorite={() => onUnfavorite(card)}
            unfavoritePending={pendingKey === card.key}
          />
        ))}
      </View>
    </GestureDetector>
  );
}

// 카드 하나. scroll과의 순환 상대 위치(d)로 transform을 계산한다.
//  d < 0: 왼쪽으로 밀려나며 tilt(스와이프되는 카드). d ≥ 0: 뒤에서 확대되며 앞으로.
function StackCard({
  card,
  index,
  total,
  scroll,
  width,
  height,
  indicatorText,
  isFront,
  onPress,
  onUnfavorite,
  unfavoritePending,
}: {
  card: CardVM;
  index: number;
  total: number;
  scroll: SharedValue<number>;
  width: number;
  height: number;
  indicatorText: string;
  isFront: boolean;
  onPress: () => void;
  onUnfavorite: () => void;
  unfavoritePending: boolean;
}) {
  const animatedStyle = useAnimatedStyle(() => {
    // 순환 거리 d 를 [-total/2, total/2) 로 정규화.
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
      transform: [
        { translateX },
        { translateY },
        { scale },
        { rotate: `${rotate}deg` },
      ],
    };
  });

  return (
    <Animated.View
      pointerEvents={isFront ? 'auto' : 'none'}
      style={[styles.stackCard, { height }, animatedStyle]}
    >
      <WishCard
        card={card}
        indicatorText={indicatorText}
        fill
        onPress={onPress}
        onUnfavorite={onUnfavorite}
        unfavoritePending={unfavoritePending}
      />
    </Animated.View>
  );
}

function EmptyWishlist({
  isPlaces,
  router,
}: {
  isPlaces: boolean;
  router: ReturnType<typeof useRouter>;
}) {
  return (
    <View style={styles.centerState}>
      <View style={styles.emptyIcon}>
        <Feather color={PRIMARY_COLOR} name="heart" size={26} />
      </View>
      <Text style={styles.emptyTitle}>아직 찜한 {isPlaces ? '장소' : '리포트'}가 없어요</Text>
      <Text style={styles.emptyDescription}>
        마음에 드는 곳을 저장해 한곳에서 이어보세요.
      </Text>
      <View style={styles.emptyActions}>
        <Pressable
          accessibilityRole="button"
          onPress={() => router.push('/(app)/(tabs)/map')}
          style={({ pressed }) => [styles.ctaButton, pressed && styles.pressed]}
        >
          <Text style={styles.ctaButtonText}>지도 둘러보기</Text>
        </Pressable>
        <Pressable
          accessibilityRole="button"
          onPress={() => router.push('/(app)/(tabs)/community')}
          style={({ pressed }) => [styles.ctaButtonGhost, pressed && styles.pressed]}
        >
          <Text style={styles.ctaButtonGhostText}>커뮤니티 가기</Text>
        </Pressable>
      </View>
    </View>
  );
}

// 겹쳐/목록 보기 세그먼트 컨트롤 — 활성 세그먼트는 진한 배경 + 흰 글씨.
function ViewSwitch({
  mode,
  onChange,
}: {
  mode: WishViewMode;
  onChange: (mode: WishViewMode) => void;
}) {
  return (
    <View accessibilityRole="tablist" style={styles.viewSwitch}>
      {(['STACK', 'LIST'] as const).map((value) => {
        const active = mode === value;
        const label = value === 'STACK' ? '겹쳐 보기' : '목록 보기';
        return (
          <Pressable
            accessibilityRole="button"
            accessibilityState={{ selected: active }}
            accessibilityLabel={label}
            key={value}
            onPress={() => onChange(value)}
            style={[styles.viewSwitchButton, active && styles.viewSwitchButtonActive]}
          >
            <Feather
              color={active ? '#FFFFFF' : MUTED_TEXT_COLOR}
              name={value === 'STACK' ? 'layers' : 'list'}
              size={13}
            />
            <Text
              style={[styles.viewSwitchText, active && styles.viewSwitchTextActive]}
            >
              {label}
            </Text>
          </Pressable>
        );
      })}
    </View>
  );
}

// 목록 보기 썸네일 — WishCard와 동일한 이미지 폴백 규칙(서버 URL → 로컬 일러스트/원격 임시).
function WishRowThumbnail({ card }: { card: CardVM }) {
  const [serverImageFailed, setServerImageFailed] = useState(false);
  const fallbackImageSource =
    card.imageFallbackId != null
      ? apartmentImageSource(card.imageFallbackId)
      : { uri: cardImageUri(card.imageIndex) };
  // Presigned URL 서명 쿼리가 바뀌어도 캐시 적중되도록 쿼리를 뗀 경로를 cacheKey로 고정
  // (WishCard와 동일). recyclingKey도 서명 없는 안정 키(card.key)로, transition은 제거.
  const imageSource =
    card.imageUrl && !serverImageFailed
      ? { uri: card.imageUrl, cacheKey: card.imageUrl.split('?')[0] }
      : fallbackImageSource;

  return (
    <Image
      source={imageSource}
      contentFit="cover"
      recyclingKey={card.key}
      style={styles.rowThumb}
      onError={() => {
        if (card.imageUrl) setServerImageFailed(true);
      }}
    />
  );
}

// 목록 보기 한 줄(컴팩트) — 좌 썸네일 / 텍스트열 / 우 하트(찜 해제).
function WishListRow({
  card,
  onPress,
  onUnfavorite,
  unfavoritePending,
}: {
  card: CardVM;
  onPress: () => void;
  onUnfavorite: () => void;
  unfavoritePending: boolean;
}) {
  const savedLabel = formatSavedDate(card.savedAt);
  const caption = savedLabel
    ? `${card.region} · ${savedLabel} 저장`
    : card.region;

  return (
    <View style={styles.row}>
      <Pressable
        accessibilityRole="button"
        accessibilityLabel={`${card.title} 상세 보기`}
        onPress={onPress}
        style={({ pressed }) => [styles.rowOpen, pressed && styles.rowPressed]}
      >
        <View style={styles.rowThumbBox}>
          <WishRowThumbnail card={card} />
        </View>
        <View style={styles.rowCopy}>
          <Text numberOfLines={1} style={styles.rowCaption}>
            {caption}
          </Text>
          <Text numberOfLines={1} style={styles.rowTitle}>
            {card.title}
          </Text>
          {card.subtitle ? (
            <Text numberOfLines={1} style={styles.rowSubtitle}>
              {card.subtitle}
            </Text>
          ) : null}
          <View style={styles.rowBadges}>
            {/* 리포트는 앰버(실거래가) 배지가 의미가 없어 빈 것처럼 보이므로 감춘다. */}
            {card.favorite?.kind !== 'report' ? (
              <View style={[styles.rowBadge, styles.rowBadgeAmber]}>
                <Text numberOfLines={1} style={[styles.rowBadgeText, styles.rowBadgeAmberText]}>
                  {card.left.value}
                </Text>
              </View>
            ) : null}
            <View style={[styles.rowBadge, styles.rowBadgeMuted]}>
              <Text numberOfLines={1} style={[styles.rowBadgeText, styles.rowBadgeMutedText]}>
                {card.right.value}
              </Text>
            </View>
          </View>
        </View>
      </Pressable>
      <Pressable
        accessibilityRole="button"
        accessibilityLabel={`${card.title} 찜 해제`}
        disabled={unfavoritePending}
        hitSlop={8}
        onPress={onUnfavorite}
        style={({ pressed }) => [styles.rowRemove, pressed && styles.pressed]}
      >
        {unfavoritePending ? (
          <ActivityIndicator color={LIKE_ACCENT_COLOR} size="small" />
        ) : (
          <Ionicons color={LIKE_ACCENT_COLOR} name="heart" size={20} />
        )}
      </Pressable>
    </View>
  );
}

// 정렬 옵션 오버레이 — 목록 위에 겹쳐 열려 콘텐츠 시작 위치를 밀지 않는다.
function SortPopover({
  sortMode,
  onSelect,
}: {
  sortMode: WishSort;
  onSelect: (sort: WishSort) => void;
}) {
  return (
    <View accessibilityRole="menu" style={styles.sortPopover}>
      {SORT_OPTIONS.map((option) => {
        const active = sortMode === option.value;
        return (
          <Pressable
            accessibilityRole="menuitem"
            accessibilityState={{ selected: active }}
            key={option.value}
            onPress={() => onSelect(option.value)}
            style={({ pressed }) => [
              styles.sortOption,
              active && styles.sortOptionActive,
              pressed && styles.pressed,
            ]}
          >
            <Text style={[styles.sortOptionText, active && styles.sortOptionTextActive]}>
              {option.label}
            </Text>
            {active ? <Feather color={PRIMARY_COLOR} name="check" size={15} /> : null}
          </Pressable>
        );
      })}
    </View>
  );
}

// 검색 결과 없음(찜 0개인 EmptyWishlist와 구분) — 검색어·정렬 리셋 액션 제공.
function FilterEmptyWishlist({ onReset }: { onReset: () => void }) {
  return (
    <View style={styles.filterEmpty}>
      <View style={styles.emptyIcon}>
        <Feather color={PRIMARY_COLOR} name="search" size={24} />
      </View>
      <Text style={styles.emptyTitle}>찾는 찜이 없어요</Text>
      <Text style={styles.emptyDescription}>이름이나 동네를 바꿔 다시 찾아보세요.</Text>
      <Pressable
        accessibilityRole="button"
        onPress={onReset}
        style={({ pressed }) => [styles.ctaButton, styles.filterEmptyButton, pressed && styles.pressed]}
      >
        <Text style={styles.ctaButtonText}>검색 초기화</Text>
      </Pressable>
    </View>
  );
}

// 목록 보기 본체 — 상단 도구(검색 + 정렬) 한 줄 + 무한스크롤 리스트.
function WishListView({
  cards,
  searchQuery,
  onSearchChange,
  sortMode,
  onSortChange,
  sortOpen,
  onToggleSort,
  onCloseSort,
  onResetSearch,
  onCardPress,
  onUnfavorite,
  pendingKey,
  hasNextPage,
  isFetchingNextPage,
  onEndReached,
  bottomInset,
}: {
  cards: CardVM[];
  searchQuery: string;
  onSearchChange: (value: string) => void;
  sortMode: WishSort;
  onSortChange: (sort: WishSort) => void;
  sortOpen: boolean;
  onToggleSort: () => void;
  onCloseSort: () => void;
  onResetSearch: () => void;
  onCardPress: (card: CardVM) => void;
  onUnfavorite: (card: CardVM) => void;
  pendingKey: string | null;
  hasNextPage: boolean;
  isFetchingNextPage: boolean;
  onEndReached: () => void;
  bottomInset: number;
}) {
  return (
    <View style={styles.flex}>
      {/* 도구 한 줄 — 검색 입력 + 정렬 알약. */}
      <View style={styles.listTools}>
        <View style={styles.listSearch}>
          <Feather color={MUTED_TEXT_COLOR} name="search" size={18} />
          <TextInput
            accessibilityLabel="찜 검색"
            placeholder="이름이나 동네로 찾기"
            placeholderTextColor={MUTED_TEXT_COLOR}
            value={searchQuery}
            onChangeText={onSearchChange}
            returnKeyType="search"
            style={styles.listSearchInput}
          />
        </View>
        <Pressable
          accessibilityRole="button"
          accessibilityLabel={`정렬: ${SORT_LABELS[sortMode]}`}
          accessibilityState={{ expanded: sortOpen }}
          onPress={onToggleSort}
          style={({ pressed }) => [styles.sortTrigger, pressed && styles.pressed]}
        >
          <Text style={styles.sortTriggerText}>{SORT_LABELS[sortMode]}</Text>
          <Feather color="#FFFFFF" name={sortOpen ? 'chevron-up' : 'chevron-down'} size={15} />
        </Pressable>
      </View>

      {cards.length === 0 ? (
        <FilterEmptyWishlist onReset={onResetSearch} />
      ) : (
        <FlatList
          contentContainerStyle={{ paddingBottom: bottomInset }}
          data={cards}
          keyExtractor={(card) => card.key}
          keyboardShouldPersistTaps="handled"
          onEndReached={onEndReached}
          onEndReachedThreshold={0.4}
          renderItem={({ item }) => (
            <WishListRow
              card={item}
              onPress={() => onCardPress(item)}
              onUnfavorite={() => onUnfavorite(item)}
              unfavoritePending={pendingKey === item.key}
            />
          )}
          ItemSeparatorComponent={() => <View style={styles.rowDivider} />}
          ListFooterComponent={
            isFetchingNextPage ? (
              <View style={styles.listFooter}>
                <ActivityIndicator color={PRIMARY_COLOR} />
              </View>
            ) : null
          }
          showsVerticalScrollIndicator={false}
          style={styles.listFeed}
        />
      )}

      {/* 정렬 오버레이 — 리스트 위에 겹쳐 표시. 바깥 탭으로 닫힘. */}
      {sortOpen ? (
        <>
          <Pressable
            accessibilityLabel="정렬 닫기"
            accessibilityRole="button"
            onPress={onCloseSort}
            style={StyleSheet.absoluteFill}
          />
          <SortPopover sortMode={sortMode} onSelect={onSortChange} />
        </>
      ) : null}
    </View>
  );
}

const styles = StyleSheet.create({
  screen: {
    flex: 1,
    backgroundColor: SURFACE_COLOR,
  },
  flex: {
    flex: 1,
  },
  titleArea: {
    paddingHorizontal: 22,
    paddingTop: 8,
    flexDirection: 'row',
    alignItems: 'center',
    justifyContent: 'space-between',
    gap: 12,
  },
  title: {
    flexShrink: 1,
    color: TEXT_COLOR,
    fontSize: 28,
    lineHeight: 36,
    fontFamily: 'NotoSansKR_900Black',
    letterSpacing: -0.8,
  },
  // 겹쳐/목록 보기 세그먼트 컨트롤.
  viewSwitch: {
    flexDirection: 'row',
    padding: 3,
    gap: 3,
    borderRadius: 15,
    borderWidth: StyleSheet.hairlineWidth,
    borderColor: DIVIDER_COLOR,
    backgroundColor: 'rgba(255, 255, 255, 0.7)',
    boxShadow: '0px 7px 18px rgba(16, 39, 30, 0.05)',
  },
  viewSwitchButton: {
    flexDirection: 'row',
    alignItems: 'center',
    gap: 4,
    paddingHorizontal: 10,
    paddingVertical: 8,
    borderRadius: 12,
  },
  viewSwitchButtonActive: {
    backgroundColor: DARK_GREEN_COLOR,
  },
  viewSwitchText: {
    color: MUTED_TEXT_COLOR,
    fontSize: 11,
    fontFamily: 'IBMPlexSansKR_700Bold',
    letterSpacing: -0.2,
  },
  viewSwitchTextActive: {
    color: '#FFFFFF',
  },
  tabsWrap: {
    paddingHorizontal: 22,
    marginTop: 16,
  },
  tabsRow: {
    flexDirection: 'row',
    borderBottomWidth: StyleSheet.hairlineWidth,
    borderBottomColor: DIVIDER_COLOR,
  },
  tabButton: {
    flex: 1,
    alignItems: 'center',
    paddingBottom: 14,
  },
  tabLabel: {
    color: MUTED_TEXT_COLOR,
    fontSize: 15,
    fontFamily: 'IBMPlexSansKR_700Bold',
    letterSpacing: -0.3,
  },
  tabLabelActive: {
    color: TEXT_COLOR,
  },
  tabCount: {
    color: LIKE_ACCENT_COLOR,
    fontFamily: 'IBMPlexSansKR_700Bold',
  },
  tabUnderline: {
    position: 'absolute',
    left: 0,
    bottom: -1,
    height: 3,
    borderRadius: 3,
    backgroundColor: DARK_GREEN_COLOR,
  },
  content: {
    flex: 1,
    paddingHorizontal: 22,
    paddingTop: 20,
  },
  // 스택 영역.
  stackArea: {
    position: 'relative',
  },
  stackCard: {
    position: 'absolute',
    left: 0,
    right: 0,
    top: 0,
  },
  // 목록 보기 도구 한 줄(검색 + 정렬).
  listTools: {
    flexDirection: 'row',
    alignItems: 'center',
    gap: 8,
    height: 54,
    zIndex: 2,
  },
  listSearch: {
    flex: 1,
    flexDirection: 'row',
    alignItems: 'center',
    gap: 8,
    height: 46,
    paddingHorizontal: 14,
    borderRadius: 15,
    backgroundColor: SOFT_BACKGROUND_COLOR,
  },
  listSearchInput: {
    flex: 1,
    minWidth: 0,
    color: TEXT_COLOR,
    fontSize: 14,
    fontFamily: 'IBMPlexSansKR_400Regular',
    letterSpacing: -0.2,
    padding: 0,
  },
  sortTrigger: {
    flexDirection: 'row',
    alignItems: 'center',
    gap: 5,
    height: 46,
    paddingHorizontal: 16,
    borderRadius: 15,
    backgroundColor: DARK_GREEN_COLOR,
    boxShadow: '0px 6px 14px rgba(16, 39, 30, 0.16)',
  },
  sortTriggerText: {
    color: '#FFFFFF',
    fontSize: 13,
    fontFamily: 'IBMPlexSansKR_700Bold',
    letterSpacing: -0.2,
  },
  // 정렬 오버레이(리스트 위 겹침).
  sortPopover: {
    position: 'absolute',
    top: 58,
    right: 0,
    width: 168,
    padding: 5,
    borderRadius: 16,
    borderWidth: StyleSheet.hairlineWidth,
    borderColor: DIVIDER_COLOR,
    backgroundColor: SURFACE_COLOR,
    boxShadow: '0px 17px 38px rgba(16, 39, 30, 0.17)',
    zIndex: 5,
    elevation: 8,
  },
  sortOption: {
    flexDirection: 'row',
    alignItems: 'center',
    justifyContent: 'space-between',
    height: 44,
    paddingHorizontal: 12,
    borderRadius: 12,
  },
  sortOptionActive: {
    backgroundColor: SOFT_GREEN_COLOR,
  },
  sortOptionText: {
    color: MUTED_TEXT_COLOR,
    fontSize: 14,
    fontFamily: 'IBMPlexSansKR_600SemiBold',
    letterSpacing: -0.2,
  },
  sortOptionTextActive: {
    color: PRIMARY_COLOR,
    fontFamily: 'IBMPlexSansKR_700Bold',
  },
  // 목록 피드(둥근 카드 안에 행 + 구분선).
  listFeed: {
    flex: 1,
    marginTop: 10,
    borderRadius: 24,
    borderWidth: StyleSheet.hairlineWidth,
    borderColor: DIVIDER_COLOR,
    backgroundColor: SURFACE_COLOR,
  },
  listFooter: {
    paddingVertical: 18,
    alignItems: 'center',
  },
  rowDivider: {
    height: StyleSheet.hairlineWidth,
    marginHorizontal: 14,
    backgroundColor: DIVIDER_COLOR,
  },
  // 목록 한 줄.
  row: {
    position: 'relative',
  },
  rowOpen: {
    flexDirection: 'row',
    alignItems: 'center',
    gap: 13,
    paddingVertical: 12,
    paddingLeft: 12,
    paddingRight: 56,
  },
  rowPressed: {
    opacity: 0.7,
  },
  rowThumbBox: {
    width: 76,
    height: 84,
    borderRadius: 18,
    overflow: 'hidden',
    backgroundColor: SOFT_GREEN_COLOR,
  },
  rowThumb: {
    width: '100%',
    height: '100%',
  },
  rowCopy: {
    flex: 1,
    minWidth: 0,
  },
  rowCaption: {
    color: PRIMARY_COLOR,
    fontSize: 11.5,
    fontFamily: 'IBMPlexSansKR_700Bold',
    letterSpacing: -0.2,
  },
  rowTitle: {
    marginTop: 5,
    color: TEXT_COLOR,
    fontSize: 16,
    fontFamily: 'IBMPlexSansKR_700Bold',
    letterSpacing: -0.4,
  },
  rowSubtitle: {
    marginTop: 4,
    color: MUTED_TEXT_COLOR,
    fontSize: 12,
    fontFamily: 'IBMPlexSansKR_400Regular',
    letterSpacing: -0.2,
  },
  rowBadges: {
    flexDirection: 'row',
    alignItems: 'center',
    gap: 6,
    marginTop: 10,
  },
  rowBadge: {
    flexShrink: 1,
    paddingHorizontal: 9,
    paddingVertical: 5,
    borderRadius: 999,
  },
  rowBadgeAmber: {
    backgroundColor: '#FFF3DB',
  },
  rowBadgeMuted: {
    backgroundColor: '#EEF3EF',
  },
  rowBadgeText: {
    fontSize: 11.5,
    fontFamily: 'IBMPlexSansKR_700Bold',
    letterSpacing: -0.2,
  },
  rowBadgeAmberText: {
    color: '#8D5608',
  },
  rowBadgeMutedText: {
    color: '#516158',
  },
  rowRemove: {
    position: 'absolute',
    top: 12,
    right: 8,
    width: 44,
    height: 44,
    alignItems: 'center',
    justifyContent: 'center',
    borderRadius: 15,
  },
  // 검색 결과 없음(찜 0개 상태와 구분).
  filterEmpty: {
    flex: 1,
    alignItems: 'center',
    justifyContent: 'center',
    paddingHorizontal: 28,
    paddingBottom: 80,
  },
  filterEmptyButton: {
    marginTop: 20,
  },
  // 카드.
  card: {
    borderRadius: 26,
    backgroundColor: SURFACE_COLOR,
    borderWidth: StyleSheet.hairlineWidth,
    borderColor: DIVIDER_COLOR,
    overflow: 'hidden',
    boxShadow: '0px 12px 28px rgba(16, 39, 30, 0.10)',
  },
  cardFill: {
    height: '100%',
  },
  cardPressed: {
    opacity: 0.94,
  },
  cardImageBox: {
    backgroundColor: SOFT_GREEN_COLOR,
    alignItems: 'center',
    justifyContent: 'center',
    position: 'relative',
  },
  cardImageFill: {
    flex: 1,
  },
  cardImageFixed: {
    height: 188,
  },
  cardImage: {
    width: '100%',
    height: '100%',
  },
  savedBadge: {
    position: 'absolute',
    top: 14,
    left: 14,
    flexDirection: 'row',
    alignItems: 'center',
    gap: 5,
    paddingHorizontal: 11,
    paddingVertical: 6,
    borderRadius: 15,
    backgroundColor: SOFT_GREEN_COLOR,
  },
  savedBadgeText: {
    color: PRIMARY_COLOR,
    fontSize: 12,
    fontFamily: 'IBMPlexSansKR_700Bold',
  },
  unfavoriteButton: {
    position: 'absolute',
    top: 12,
    right: 12,
    minWidth: 40,
    height: 34,
    paddingHorizontal: 14,
    borderRadius: 17,
    alignItems: 'center',
    justifyContent: 'center',
    backgroundColor: 'rgba(255, 255, 255, 0.92)',
    boxShadow: '0px 2px 8px rgba(16, 39, 30, 0.14)',
  },
  unfavoriteButtonText: {
    color: TEXT_COLOR,
    fontSize: 12.5,
    fontFamily: 'IBMPlexSansKR_700Bold',
    letterSpacing: -0.2,
  },
  captionPill: {
    position: 'absolute',
    right: 14,
    bottom: 12,
    paddingHorizontal: 10,
    paddingVertical: 5,
    borderRadius: 12,
    backgroundColor: 'rgba(255, 255, 255, 0.82)',
  },
  captionText: {
    color: MUTED_TEXT_COLOR,
    fontSize: 11.5,
    fontFamily: 'IBMPlexSansKR_600SemiBold',
    letterSpacing: -0.2,
  },
  cardBody: {
    paddingHorizontal: 20,
    paddingTop: 16,
    paddingBottom: 20,
  },
  regionRow: {
    flexDirection: 'row',
    alignItems: 'center',
    justifyContent: 'space-between',
  },
  regionText: {
    color: PRIMARY_COLOR,
    fontSize: 13,
    fontFamily: 'IBMPlexSansKR_700Bold',
    letterSpacing: -0.2,
  },
  indicatorText: {
    color: TEXT_COLOR,
    fontSize: 13,
    fontFamily: 'IBMPlexSansKR_700Bold',
    letterSpacing: 0.5,
  },
  cardTitle: {
    marginTop: 12,
    color: TEXT_COLOR,
    fontSize: 22,
    fontFamily: 'IBMPlexSansKR_700Bold',
    letterSpacing: -0.5,
  },
  cardSubtitle: {
    marginTop: 6,
    color: MUTED_TEXT_COLOR,
    fontSize: 13,
    fontFamily: 'IBMPlexSansKR_400Regular',
    letterSpacing: -0.2,
  },
  cardDivider: {
    marginTop: 16,
    marginBottom: 16,
    height: StyleSheet.hairlineWidth,
    backgroundColor: DIVIDER_COLOR,
  },
  statRow: {
    flexDirection: 'row',
  },
  statBlock: {
    flex: 1,
    minWidth: 0,
  },
  statLabel: {
    color: MUTED_TEXT_COLOR,
    fontSize: 12,
    fontFamily: 'IBMPlexSansKR_600SemiBold',
    letterSpacing: -0.2,
  },
  statValue: {
    marginTop: 7,
    color: TEXT_COLOR,
    fontSize: 18,
    fontFamily: 'IBMPlexSansKR_700Bold',
    letterSpacing: -0.4,
  },
  statSub: {
    marginTop: 5,
    color: MUTED_TEXT_COLOR,
    fontSize: 12,
    fontFamily: 'IBMPlexSansKR_400Regular',
    letterSpacing: -0.2,
  },
  // 상태.
  centerState: {
    flex: 1,
    alignItems: 'center',
    justifyContent: 'center',
    paddingHorizontal: 28,
    paddingBottom: 80,
  },
  emptyIcon: {
    width: 64,
    height: 64,
    marginBottom: 16,
    borderRadius: 32,
    backgroundColor: SOFT_GREEN_COLOR,
    alignItems: 'center',
    justifyContent: 'center',
  },
  emptyTitle: {
    color: TEXT_COLOR,
    fontSize: 17,
    fontFamily: 'IBMPlexSansKR_700Bold',
    letterSpacing: -0.3,
  },
  emptyDescription: {
    marginTop: 8,
    textAlign: 'center',
    color: MUTED_TEXT_COLOR,
    fontSize: 13.5,
    fontFamily: 'IBMPlexSansKR_400Regular',
    letterSpacing: -0.2,
  },
  emptyActions: {
    marginTop: 22,
    flexDirection: 'row',
    gap: 10,
  },
  ctaButton: {
    height: 48,
    paddingHorizontal: 20,
    borderRadius: 24,
    alignItems: 'center',
    justifyContent: 'center',
    backgroundColor: DARK_GREEN_COLOR,
  },
  ctaButtonText: {
    color: '#FFFFFF',
    fontSize: 14,
    fontFamily: 'IBMPlexSansKR_700Bold',
    letterSpacing: -0.3,
  },
  ctaButtonGhost: {
    height: 48,
    paddingHorizontal: 20,
    borderRadius: 24,
    alignItems: 'center',
    justifyContent: 'center',
    backgroundColor: SOFT_GREEN_COLOR,
  },
  ctaButtonGhostText: {
    color: PRIMARY_COLOR,
    fontSize: 14,
    fontFamily: 'IBMPlexSansKR_700Bold',
    letterSpacing: -0.3,
  },
  pressed: {
    opacity: 0.7,
  },
});
