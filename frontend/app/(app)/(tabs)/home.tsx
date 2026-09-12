import Ionicons from '@expo/vector-icons/Ionicons';
import { useQueryClient } from '@tanstack/react-query';
import { openMenu } from 'expo-dev-menu';
import { LinearGradient } from 'expo-linear-gradient';
import { type Href, useFocusEffect, useRouter } from 'expo-router';
import { useCallback, useEffect, useRef } from 'react';
import { Image, Pressable, StyleSheet, Text, View } from 'react-native';
import { Gesture, GestureDetector } from 'react-native-gesture-handler';
import Animated, {
  cancelAnimation,
  Easing,
  useAnimatedStyle,
  useSharedValue,
  withRepeat,
  withSequence,
  withTiming,
} from 'react-native-reanimated';
import { useSafeAreaInsets } from 'react-native-safe-area-context';

import { appAlert } from '@/components/AppDialog';
import {
  BORDER_COLOR,
  DARK_GREEN_COLOR,
  LABEL_COLOR,
  MUTED_TEXT_COLOR,
  SURFACE_COLOR,
  TEXT_COLOR,
} from '@/constants/colors';
import {
  GlassIconButton,
  GLASS_ICON_BUTTON_ICON_SIZE,
} from '@/components/GlassIconButton';
import { GlossyFill } from '@/components/GlossyFill';
import { formatTodayLabel, formatVisitDateBadge, formatVisitTime } from '@/features/home/formatHomeDate';
import { HomeBackgroundIcons } from '@/features/home/HomeBackgroundIcons';
import { HomeApiError, type FineDustGrade } from '@/features/home/types';
import { useHomeLocation } from '@/features/home/useHomeLocation';
import { homeSummaryQueryKey, useHomeSummary } from '@/features/home/useHomeSummary';
import { resolveWeatherEmoji } from '@/features/home/weatherIcons';
import { NotificationButton } from '@/features/notification/NotificationButton';
import { refetchOnFocusIfStale } from '@/lib/refetchOnFocusIfStale';
import { useAuthStore } from '@/store/authStore';
import {
  isDeveloperLocationAvailable,
  useDeveloperLocationStore,
} from '@/store/developerLocationStore';

// 배경 그라데이션: 좌하(초록) → 중앙(흰색) → 우상(파랑) 대각선.
// 초록·파랑은 아주 살짝만 더 진하게, 중앙 흰색 구간은 이전(0.5 한 점)과 그 다음
// 버전(0.35~0.65) 중간 정도 폭(0.42~0.58)으로 잡음.
const GRADIENT_COLORS = ['#D2F0DE', '#FFFFFF', '#FFFFFF', '#D2E9FA'] as const;
const GRADIENT_LOCATIONS = [0, 0.42, 0.58, 1] as const;
const GRADIENT_START = { x: 0, y: 1 };
const GRADIENT_END = { x: 1, y: 0 };
const SETTINGS_MULTI_TAP_WINDOW_MS = 700;

const FINE_DUST_LABEL: Record<FineDustGrade, string> = {
  GOOD: '좋음',
  NORMAL: '보통',
  BAD: '나쁨',
  VERY_BAD: '매우나쁨',
};
// 등급별 색(좋음 파랑 · 보통 초록 · 나쁨 주황 · 매우나쁨 빨강).
const FINE_DUST_COLOR: Record<FineDustGrade, string> = {
  GOOD: '#2C7BE5',
  NORMAL: '#2E9E5B',
  BAD: '#E58A2C',
  VERY_BAD: '#D8483A',
};

// 배지를 구슬처럼 반질반질·뽀용하게: 표면 그라데이션 + 상단 광택 하이라이트 + 부드러운 그림자.
function GlossyBadge({
  label,
  base,
  light,
  textColor,
}: {
  label: string;
  base: string;
  light: string;
  textColor: string;
}) {
  return (
    <View style={[styles.beadBadge, { backgroundColor: base }]}>
      {/* 구슬 표면 음영 — 위 밝게 → 아래 base로. */}
      <LinearGradient
        colors={[light, base]}
        locations={[0, 0.9]}
        start={{ x: 0, y: 0 }}
        end={{ x: 0, y: 1 }}
        style={StyleSheet.absoluteFill}
      />
      {/* 상단 광택 하이라이트(구슬 반사). */}
      <LinearGradient
        colors={['rgba(255,255,255,0.8)', 'rgba(255,255,255,0)']}
        start={{ x: 0, y: 0 }}
        end={{ x: 0, y: 1 }}
        style={styles.beadGloss}
      />
      <Text style={[styles.badgeText, { color: textColor }]}>{label}</Text>
    </View>
  );
}

function HomeDdaySkeleton() {
  const opacity = useSharedValue(0.42);

  useEffect(() => {
    opacity.value = withRepeat(
      withSequence(
        withTiming(0.72, { duration: 800, easing: Easing.inOut(Easing.quad) }),
        withTiming(0.42, { duration: 800, easing: Easing.inOut(Easing.quad) }),
      ),
      -1,
    );

    return () => cancelAnimation(opacity);
  }, [opacity]);

  const animatedStyle = useAnimatedStyle(() => ({ opacity: opacity.value }));

  return (
    <View
      accessible
      accessibilityLabel="홈 일정 정보를 불러오는 중입니다."
      accessibilityLiveRegion="polite"
      accessibilityState={{ busy: true }}
      style={styles.ddaySkeletonContainer}
    >
      <Animated.View
        accessibilityElementsHidden
        importantForAccessibility="no-hide-descendants"
        style={animatedStyle}
      >
        <View style={[styles.ddaySkeletonBlock, styles.ddaySkeletonLabel]} />
        <View style={[styles.ddaySkeletonBlock, styles.ddaySkeletonValue]} />
        <View style={[styles.ddaySkeletonBlock, styles.ddaySkeletonBadge]} />
        <View style={styles.ddaySkeletonCard}>
          <View style={styles.ddaySkeletonCardText}>
            <View style={[styles.ddaySkeletonBlock, styles.ddaySkeletonApartment]} />
            <View style={[styles.ddaySkeletonBlock, styles.ddaySkeletonSubtitle]} />
          </View>
          <View style={[styles.ddaySkeletonBlock, styles.ddaySkeletonArrow]} />
        </View>
      </Animated.View>
    </View>
  );
}

export default function HomeScreen() {
  const router = useRouter();
  const insets = useSafeAreaInsets();
  const location = useHomeLocation();
  const { data, error, isLoading } = useHomeSummary(location);
  const queryClient = useQueryClient();
  const sessionVersion = useAuthStore((state) => state.sessionVersion);
  const isDeveloperLocationEnabled = useDeveloperLocationStore((state) => state.isEnabled);
  const enableDeveloperLocation = useDeveloperLocationStore((state) => state.enable);
  const disableDeveloperLocation = useDeveloperLocationStore((state) => state.disable);
  const settingsTapCountRef = useRef(0);
  const settingsTapTimerRef = useRef<ReturnType<typeof setTimeout> | null>(null);
  const isNavigatingRef = useRef(false);

  // 404 MEMBER_NOT_FOUND: 탈퇴 회원·유효하지 않은 계정 → 로그인 화면으로.
  useEffect(() => {
    if (error instanceof HomeApiError && error.code === 'MEMBER_NOT_FOUND') {
      router.replace('/(auth)/login');
    }
  }, [error, router]);

  useFocusEffect(
    useCallback(() => {
      isNavigatingRef.current = false;

      // 가입 승인·일정 생성은 다른 회원의 기기에서도 발생합니다. 홈으로 돌아올 때
      // 서버 원본을 다시 조회하되, 20초 이내 재방문은 생략해 불필요한 네트워크와
      // presigned URL 재발급으로 인한 이미지 깜빡임을 줄입니다.
      refetchOnFocusIfStale(queryClient, homeSummaryQueryKey(location, sessionVersion));

      return () => {
        settingsTapCountRef.current = 0;
        if (settingsTapTimerRef.current !== null) {
          clearTimeout(settingsTapTimerRef.current);
          settingsTapTimerRef.current = null;
        }
      };
    }, [location, queryClient, sessionVersion]),
  );

  const navigateOnce = useCallback(
    (href: Href) => {
      if (isNavigatingRef.current) return;

      isNavigatingRef.current = true;
      router.push(href);
    },
    [router],
  );

  const handleSettingsPress = useCallback(() => {
    if (!isDeveloperLocationAvailable) {
      navigateOnce('/(app)/settings');
      return;
    }

    settingsTapCountRef.current += 1;
    if (settingsTapTimerRef.current !== null) {
      clearTimeout(settingsTapTimerRef.current);
    }

    if (settingsTapCountRef.current < 3) {
      settingsTapTimerRef.current = setTimeout(() => {
        settingsTapCountRef.current = 0;
        settingsTapTimerRef.current = null;
        navigateOnce('/(app)/settings');
      }, SETTINGS_MULTI_TAP_WINDOW_MS);
      return;
    }

    settingsTapCountRef.current = 0;
    settingsTapTimerRef.current = null;
    appAlert(
      '시연용 개발자 모드',
      isDeveloperLocationEnabled
        ? '가상 위치 모드를 해제할까요?'
        : '실제 GPS 대신 시연용 가상 위치를 사용합니다. 개발·시연 빌드에서만 활성화하세요.',
      [
        { text: '취소', style: 'cancel' },
        {
          text: isDeveloperLocationEnabled ? '해제' : '활성화',
          style: isDeveloperLocationEnabled ? 'destructive' : 'default',
          onPress: isDeveloperLocationEnabled ? disableDeveloperLocation : enableDeveloperLocation,
        },
      ],
    );
  }, [disableDeveloperLocation, enableDeveloperLocation, isDeveloperLocationEnabled, navigateOnce]);

  const nextVisit = data?.nextVisit;
  // 임장일 날짜 배지. (임장 날짜·시간의 예보는 백엔드 예보 필드가 붙으면 여기에 추가 예정 —
  // 현재 API의 weather는 '오늘 현재 날씨'라 미래 임장일에 붙이면 오해 소지가 있어 표시하지 않음.)
  const visitDateText = nextVisit?.startAt ? formatVisitDateBadge(nextVisit.startAt) : null;
  // "오늘" 아래 현재 날씨(기온·미세먼지). 오늘 기준이라 현재 관측 날씨를 그대로 사용. 없으면 미표시.
  const currentWeather = data?.weather?.available ? data.weather : null;
  const hasUnread = (data?.unreadNotificationCount ?? 0) > 0;

  // 흔들기가 개발자 메뉴를 여는 것(dev 빌드 기본 동작, JS로 못 끔)과 겹쳐서, 대신
  // 배경 빈 곳을 0.6초 길게 누르면 개발자 메뉴가 뜨게 합니다. dev 빌드에서만 동작.
  const devMenuGesture = Gesture.LongPress()
    .minDuration(600)
    .onStart(() => {
      if (__DEV__) {
        openMenu();
      }
    });

  return (
    <LinearGradient
      colors={GRADIENT_COLORS}
      locations={GRADIENT_LOCATIONS}
      start={GRADIENT_START}
      end={GRADIENT_END}
      style={styles.screen}
    >
      {/* 배경 빈 곳 롱프레스 → 개발자 메뉴. 가장 뒤(back)에 깔아서, 위의 box-none 레이어들을
          통과한 빈 영역 터치만 잡습니다(아이콘·버튼 위 터치는 각자 처리). */}
      {__DEV__ ? (
        <GestureDetector gesture={devMenuGesture}>
          <View style={StyleSheet.absoluteFill} />
        </GestureDetector>
      ) : null}
      <HomeBackgroundIcons weather={data?.weather} />
      {/* 예전엔 ScrollView였음 — 콘텐츠(헤더 + D-Day 또는 빈 상태 문구)가 스크롤이
          필요할 만큼 길지 않고, 안드로이드에서 ScrollView는 pointerEvents="box-none"을
          줘도 스크롤 제스처 감지 때문에 빈 여백 터치를 가로채 뒤에 깔린 배경 아이콘이
          안 눌리는 문제가 있어서 일반 View로 교체. */}
      <View
        pointerEvents="box-none"
        style={[styles.content, styles.scroll, { paddingTop: insets.top + 18 }]}
      >
        <View style={styles.header}>
          {/* TODO(FE-005): 임장 달력(BE-040) 연동은 범위 밖 */}
          <View style={styles.headerLeft}>
            <View style={styles.dateRow}>
              <Text style={styles.dateText}>{data ? `오늘 ${formatTodayLabel(data.today)}` : '오늘'}</Text>
              {isDeveloperLocationEnabled ? (
                <View style={styles.demoGpsBadge}>
                  <Text style={styles.demoGpsBadgeText}>DEMO GPS</Text>
                </View>
              ) : null}
            </View>
            {/* 오늘(현재) 기온 + 미세먼지. 미래 임장일 예보가 아니라 현재 관측값이라 여기 표시가 적절. */}
            {currentWeather && currentWeather.temperatureCelsius !== null ? (
              <View style={styles.todayWeatherRow}>
                <Text style={styles.todayWeatherText}>
                  {resolveWeatherEmoji(currentWeather.iconKey, currentWeather.conditionCode)}{' '}
                  {Math.round(currentWeather.temperatureCelsius)}°
                </Text>
                {currentWeather.fineDustGrade ? (
                  <Text style={styles.todayWeatherText}>
                    {'  ·  '}미세먼지{' '}
                    <Text
                      style={{
                        color: FINE_DUST_COLOR[currentWeather.fineDustGrade],
                        fontFamily: 'IBMPlexSansKR_700Bold',
                      }}
                    >
                      {FINE_DUST_LABEL[currentWeather.fineDustGrade]}
                    </Text>
                  </Text>
                ) : null}
              </View>
            ) : null}
          </View>

          {/* 알림·설정 버튼의 모양·크기·재질은 GlassIconButton 이 갖습니다(앱 공통). */}
          <View style={styles.headerIcons}>
            <NotificationButton />
            <GlassIconButton accessibilityLabel="설정 열기" onPress={handleSettingsPress}>
              <Ionicons
                color={DARK_GREEN_COLOR}
                name="settings-outline"
                size={GLASS_ICON_BUTTON_ICON_SIZE}
              />
            </GlassIconButton>
          </View>
        </View>

        {/* 보여 줄 데이터가 아직 하나도 없을 때만 스켈레톤. 위치가 늦게 잡혀 queryKey가
            바뀌는 등으로 재조회가 시작돼도, 이미 그린 내용이 스켈레톤으로 되돌아가지
            않게 합니다(useHomeSummary의 placeholderData와 짝). */}
        {isLoading && data === undefined ? (
          <HomeDdaySkeleton />
        ) : nextVisit?.exists ? (
          <>
            <Text style={styles.ddayLabel}>다음 임장까지</Text>
            <View style={styles.ddayRow}>
              <Image
                resizeMode="contain"
                source={require('../../../assets/색연필2.png')}
                style={styles.ddayHighlightBar}
              />
              {/* 당일(dDay 0)은 'D-0' 대신 'D-DAY'로 표기. 글자수가 늘어 폭이 커지므로
                  뒤 형광 바(D-6 폭 기준) 안에 들어오도록 폰트만 줄인다(세로/정렬 유지). */}
              <Text
                style={[
                  styles.ddayValue,
                  nextVisit.dDay === 0 && styles.ddayValueDday,
                  nextVisit.dDay !== null &&
                    nextVisit.dDay >= 10 &&
                    styles.ddayValueTwoDigit,
                ]}
              >
                {nextVisit.dDay === 0 ? 'D-DAY' : `D-${nextVisit.dDay}`}
              </Text>
            </View>

            <View style={styles.badgeRow}>
              {/* 임장일 날짜만 표시(미래 날짜의 날씨 예보는 백엔드 예보 필드 도입 후). */}
              {visitDateText ? (
                <GlossyBadge
                  label={visitDateText}
                  base="#A6F0C4"
                  light="#E4FFF0"
                  textColor="#1B3327"
                />
              ) : null}
            </View>

            <Pressable
              onPress={() =>
                nextVisit.studyId !== null &&
                navigateOnce({ pathname: '/(app)/study/[id]', params: { id: String(nextVisit.studyId) } })
              }
              style={({ pressed }) => [styles.visitCard, pressed && styles.pressed]}
            >
              <View style={styles.visitCardText}>
                <Text numberOfLines={1} style={styles.apartmentName}>
                  {nextVisit.apartmentName}
                </Text>
                <Text numberOfLines={1} style={styles.visitSubtitle}>
                  {[
                    nextVisit.meetingPlace,
                    nextVisit.currentMemberCount !== null && nextVisit.capacity !== null
                      ? `${nextVisit.currentMemberCount}/${nextVisit.capacity}명`
                      : null,
                    nextVisit.startAt ? formatVisitTime(nextVisit.startAt) : null,
                  ]
                    .filter(Boolean)
                    .join('  ')}
                </Text>
              </View>
              <View style={styles.visitCardArrow}>
                <Ionicons color={TEXT_COLOR} name="chevron-forward" size={17} />
              </View>
            </Pressable>
          </>
        ) : (
          <View style={styles.emptyVisit}>
            <Text style={styles.emptyVisitTitle}>예정된 임장이 없어요</Text>
            <Text style={styles.emptyVisitSubtitle}>스터디를 찾아 다음 임장을 계획해보세요.</Text>
          </View>
        )}
      </View>

      {/* 날씨 카드·버튼은 스크롤과 무관하게 항상 하단 탭바 바로 위에 고정. */}
      <View
        pointerEvents="box-none"
        style={[
          styles.bottomSection,
          // 탭바(_layout.tsx: marginBottom max(18, insets.bottom+8) + height 70) 바로 위
          // 18dp 간격에 버튼을 붙임 — 뜬 간격 없이 네비바 위에 딱 얹히도록.
          { paddingBottom: Math.max(18, insets.bottom + 8) + 70 + 18 },
        ]}
      >
        <View style={styles.buttonRow}>
          {/* 스터디 찾기 → 지도 탭. */}
          <Pressable
            onPress={() => navigateOnce('/(app)/(tabs)/map')}
            style={({ pressed }) => [styles.findStudyButton, pressed && styles.pressed]}
          >
            <GlossyFill base="#A6F0C4" light="#E4FFF0" radius={16} glossOpacity={0.55} />
            <Ionicons color={DARK_GREEN_COLOR} name="search" size={16} />
            <Text style={styles.findStudyText}>스터디 찾기</Text>
          </Pressable>
          <Pressable
            onPress={() => navigateOnce('/(app)/study/create')}
            style={({ pressed }) => [styles.createStudyButton, pressed && styles.pressed]}
          >
            <GlossyFill base="#223028" light="#3B4D43" radius={16} glossOpacity={0.24} />
            <Ionicons color="#AEFBCF" name="add" size={18} />
            <Text style={styles.createStudyText}>내 스터디 만들기</Text>
          </Pressable>
        </View>
      </View>
    </LinearGradient>
  );
}

const styles = StyleSheet.create({
  screen: {
    flex: 1,
  },
  scroll: {
    flex: 1,
    pointerEvents: 'box-none',
  },
  content: {
    paddingHorizontal: 20,
  },
  pressed: {
    opacity: 0.66,
  },

  header: {
    flexDirection: 'row',
    alignItems: 'flex-start',
    justifyContent: 'space-between',
    marginBottom: 22,
  },
  headerLeft: {
    gap: 0,
  },
  dateRow: {
    flexDirection: 'row',
    alignItems: 'center',
    gap: 4,
  },
  dateText: {
    fontSize: 19,
    fontFamily: 'IBMPlexSansKR_700Bold',
    color: TEXT_COLOR,
  },
  demoGpsBadge: {
    backgroundColor: DARK_GREEN_COLOR,
    borderRadius: 9,
    paddingHorizontal: 7,
    paddingVertical: 3,
  },
  demoGpsBadgeText: {
    color: '#FFFFFF',
    fontSize: 9,
    fontWeight: '800',
    letterSpacing: 0.4,
  },
  todayWeatherRow: {
    flexDirection: 'row',
    alignItems: 'center',
    marginTop: -3,
  },
  todayWeatherText: {
    fontSize: 14,
    fontFamily: 'IBMPlexSansKR_600SemiBold',
    // 상단 날씨 줄만 조금 더 진한 회색(하단 weatherLabel은 기존 톤 유지).
    color: '#6E7A73',
  },
  headerIcons: {
    flexDirection: 'row',
    alignItems: 'center',
    gap: 8,
  },
  ddayLabel: {
    fontSize: 19,
    fontFamily: 'IBMPlexSansKR_700Bold',
    color: '#17211C',
    marginBottom: 0,
  },
  ddayRow: {
    alignSelf: 'flex-start',
    position: 'relative',
    // 큰 디스플레이 폰트의 상단 내부 여백 때문에 라벨과 떠 보여서 위로 당겨 붙임.
    marginTop: -16,
    marginBottom: 6,
  },
  ddayValue: {
    fontSize: 124,
    lineHeight: 126,
    // Noto Sans KR Black(무게 900) — 깔끔한 정통 고딕에 초굵기.
    // (홈 나머지 텍스트는 IBM Plex Sans KR이지만, D-Day 대형 숫자만 초굵기 디스플레이용으로 Noto 유지.)
    fontFamily: 'NotoSansKR_900Black',
    // 참고.png 글씨가 가로로 넓적한 인상이라 자간을 살짝 벌림.
    letterSpacing: 1,
    // 참고.png 실측 색상.
    color: '#17211C',
  },
  // 'D-DAY'(당일)는 글자수가 많아 그대로 두면 형광 바를 넘치므로 폰트만 축소.
  // lineHeight/letterSpacing은 base를 물려받아 세로 위치·바 정렬은 그대로 유지.
  ddayValueDday: {
    fontSize: 74,
    letterSpacing: 0,
  },
  // 두 자리 D-day(D-10~D-99)는 'D-6'보다 폭이 넓어 형광 바(D-6 폭 기준)를
  // 넘치므로 폰트만 축소해 바 안에 들어오게 한다(세로 위치·바 정렬은 유지).
  ddayValueTwoDigit: {
    fontSize: 92,
    letterSpacing: 0,
  },
  // "D-6" 뒤의 색연필 스와이프(색연필2.png, 실제 비율 1799:874).
  // height를 D-6 lineHeight(138)에 맞추고 그 비율 그대로 width(284)를 계산해서
  // resizeMode="contain"이 정확히 꽉 채우게 함 — letterbox 여백 없이 딱 맞음.
  ddayHighlightBar: {
    position: 'absolute',
    // "D"/"6" 양쪽으로 고르게 살짝 삐져나오도록 텍스트(약 220) 대비 중앙 정렬.
    left: -32,
    top: 0,
    width: 284,
    height: 138,
    // 왼쪽으로 아주아주아주 살짝만 기울임(음수 = 반시계방향).
    transform: [{ rotate: '-1deg' }],
  },

  ddaySkeletonContainer: {
    width: '100%',
    maxWidth: 286,
  },
  ddaySkeletonBlock: {
    backgroundColor: 'rgba(23, 33, 28, 0.18)',
  },
  ddaySkeletonLabel: {
    width: 116,
    height: 20,
    borderRadius: 8,
    marginBottom: 8,
  },
  ddaySkeletonValue: {
    width: 220,
    height: 96,
    borderRadius: 22,
    marginBottom: 12,
  },
  ddaySkeletonBadge: {
    width: 86,
    height: 32,
    borderRadius: 16,
    marginBottom: 14,
  },
  ddaySkeletonCard: {
    width: '100%',
    height: 68,
    flexDirection: 'row',
    alignItems: 'center',
    gap: 12,
    borderRadius: 18,
    paddingHorizontal: 16,
    paddingVertical: 12,
    backgroundColor: 'rgba(255, 255, 255, 0.65)',
  },
  ddaySkeletonCardText: {
    flex: 1,
    gap: 8,
  },
  ddaySkeletonApartment: {
    width: 142,
    height: 18,
    borderRadius: 7,
  },
  ddaySkeletonSubtitle: {
    width: 184,
    height: 12,
    borderRadius: 6,
  },
  ddaySkeletonArrow: {
    width: 36,
    height: 36,
    borderRadius: 18,
  },

  badgeRow: {
    flexDirection: 'row',
    gap: 8,
    marginBottom: 14,
  },
  beadBadge: {
    paddingHorizontal: 16,
    paddingVertical: 8,
    borderRadius: 999,
    overflow: 'hidden',
    alignItems: 'center',
    justifyContent: 'center',
    // 뽀용한 구슬 느낌 — 부드럽고 넓게 퍼지는 그림자.
    boxShadow: '0px 4px 14px rgba(16, 39, 30, 0.25)',
  },
  // 상단 광택(구슬 반사) — 위 절반에 흰 하이라이트가 부드럽게 사라짐.
  beadGloss: {
    position: 'absolute',
    top: 0,
    left: 5,
    right: 5,
    height: '55%',
    borderRadius: 999,
  },
  badgeText: {
    fontSize: 12,
    fontFamily: 'IBMPlexSansKR_700Bold',
    color: '#20302C',
  },

  emptyVisit: {
    marginBottom: 16,
    alignSelf: 'flex-start',
    backgroundColor: 'rgba(255, 255, 255, 0.6)',
    borderRadius: 16,
    paddingHorizontal: 16,
    paddingVertical: 14,
  },
  emptyVisitTitle: {
    fontSize: 21,
    fontFamily: 'IBMPlexSansKR_700Bold',
    color: TEXT_COLOR,
    marginBottom: 2,
  },
  emptyVisitSubtitle: {
    fontSize: 15,
    fontFamily: 'IBMPlexSansKR_400Regular',
    color: '#17211C',
  },

  visitCard: {
    flexDirection: 'row',
    // 화살표가 카드 오른쪽 끝이 아니라 텍스트 바로 옆에 붙게(flex:1 대신 flexShrink),
    // 세로로는 아파트명+서브텍스트 두 줄 전체 가운데.
    alignItems: 'center',
    gap: 12,
    // 아파트명 + 서브텍스트 + 화살표 버튼을 하나의 반투명 흰색 박스로 감싼다
    // (떠다니는 배경 위에서도 잘 읽히게). 내용 폭만큼만 감싸도록 flex-start.
    alignSelf: 'flex-start',
    backgroundColor: 'rgba(255, 255, 255, 0.65)',
    borderRadius: 18,
    paddingVertical: 12,
    paddingHorizontal: 16,
  },
  visitCardText: {
    flexShrink: 1,
    gap: 0,
  },
  apartmentName: {
    fontSize: 19,
    lineHeight: 22,
    fontFamily: 'IBMPlexSansKR_700Bold',
    // 참고.png 실측 색상.
    color: '#17211C',
  },
  visitSubtitle: {
    fontSize: 13,
    fontFamily: 'IBMPlexSansKR_400Regular',
    color: '#7F8A84',
    // gap:0 위에서 글꼴 자체 여백까지 걷어내 두 줄을 더 붙임.
    marginTop: -4,
  },
  compassWrap: {
    marginTop: 16,
  },
  visitCardArrow: {
    width: 36,
    height: 36,
    borderRadius: 18,
    alignItems: 'center',
    justifyContent: 'center',
    backgroundColor: SURFACE_COLOR,
    elevation: 3,
    shadowColor: '#111111',
    shadowOffset: { width: 0, height: 2 },
    shadowOpacity: 0.15,
    shadowRadius: 4,
  },

  bottomSection: {
    paddingHorizontal: 20,
  },

  weatherCard: {
    flexDirection: 'row',
    alignItems: 'center',
    borderRadius: 18,
    backgroundColor: '#FFFFFF',
    paddingVertical: 14,
    marginBottom: 14,
    elevation: 6,
    shadowColor: '#111111',
    shadowOffset: { width: 0, height: 2 },
    shadowOpacity: 0.12,
    shadowRadius: 6,
  },
  weatherColumn: {
    flex: 1,
    alignItems: 'center',
    gap: 6,
  },
  weatherDivider: {
    width: StyleSheet.hairlineWidth,
    alignSelf: 'stretch',
    backgroundColor: BORDER_COLOR,
  },
  weatherEmoji: {
    fontSize: 24,
    lineHeight: 28,
  },
  weatherValue: {
    fontSize: 17,
    fontFamily: 'IBMPlexSansKR_700Bold',
    color: TEXT_COLOR,
  },
  weatherLabel: {
    fontSize: 12,
    fontFamily: 'IBMPlexSansKR_400Regular',
    color: LABEL_COLOR,
  },
  weatherUnavailableCard: {
    justifyContent: 'center',
    paddingVertical: 22,
  },
  weatherUnavailableText: {
    fontSize: 13,
    fontFamily: 'IBMPlexSansKR_400Regular',
    color: MUTED_TEXT_COLOR,
  },

  buttonRow: {
    flexDirection: 'row',
    gap: 10,
  },
  findStudyButton: {
    flex: 1,
    flexDirection: 'row',
    alignItems: 'center',
    justifyContent: 'center',
    gap: 6,
    height: 52,
    borderRadius: 16,
    // 유리(블러) 재질은 GlassSurface가 그림 — 배경은 투명. ⚠️ elevation은 투명배경+
    // borderRadius에서 사각 흰박스 아티팩트를 유발하므로 금지 → boxShadow로 부드러운 그림자.
    boxShadow: '0px 6px 16px rgba(16, 39, 30, 0.24)',
  },
  findStudyText: {
    fontSize: 15,
    fontFamily: 'IBMPlexSansKR_700Bold',
    color: DARK_GREEN_COLOR,
  },
  createStudyButton: {
    flex: 1,
    flexDirection: 'row',
    alignItems: 'center',
    justifyContent: 'center',
    gap: 6,
    height: 52,
    borderRadius: 16,
    boxShadow: '0px 6px 16px rgba(0, 0, 0, 0.3)',
  },
  createStudyText: {
    fontSize: 15,
    fontFamily: 'IBMPlexSansKR_700Bold',
    color: '#AEFBCF',
  },
});
