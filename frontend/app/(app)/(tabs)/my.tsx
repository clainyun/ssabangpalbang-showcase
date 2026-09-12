import Ionicons from '@expo/vector-icons/Ionicons';
import { useInfiniteQuery, useMutation, useQuery, useQueryClient } from '@tanstack/react-query';
import { Image, type ImageProps } from 'expo-image';
import { StatusBar } from 'expo-status-bar';
import { type Href, useFocusEffect, useRouter } from 'expo-router';
import { useCallback, useEffect, useRef, useState } from 'react';
import {
  ActivityIndicator,
  AppState,
  type ImageSourcePropType,
  KeyboardAvoidingView,
  type LayoutChangeEvent,
  Modal,
  type NativeScrollEvent,
  type NativeSyntheticEvent,
  Platform,
  Pressable,
  ScrollView,
  StyleSheet,
  Text,
  TextInput,
  useWindowDimensions,
  View,
} from 'react-native';
import { useSafeAreaInsets } from 'react-native-safe-area-context';

import { appAlert } from '@/components/AppDialog';
import {
  GlassIconButton,
  GLASS_ICON_BUTTON_ICON_SIZE,
} from '@/components/GlassIconButton';
import {
  BORDER_COLOR,
  BUTTON_BACKGROUND_COLOR,
  CALENDAR_SATURDAY_COLOR,
  DARK_GREEN_COLOR,
  ERROR_BACKGROUND_COLOR,
  ERROR_COLOR,
  LABEL_COLOR,
  LIKE_ACCENT_COLOR,
  MODAL_SCRIM_COLOR,
  MUTED_TEXT_COLOR,
  PLACEHOLDER_COLOR,
  PRIMARY_COLOR,
  SCREEN_BACKGROUND_COLOR,
  SOFT_BACKGROUND_COLOR,
  SOFT_GREEN_COLOR,
  SURFACE_COLOR,
  TEXT_COLOR,
} from '@/constants/colors';
import {
  getMemberReviews,
  getMyFollowings,
  getMyProfile,
  getMyReports,
  getMyStudies,
  getMyVisitCalendar,
  unfollowMember,
  type CharacterId,
  type MyFollowing,
  MyPageApiError,
  type MyReport,
  type MyStudy,
  type MyVisitCalendar,
  type MyVisitCalendarVisit,
  type ReviewTagSummary,
} from '@/features/member/api/myPage';
import { myPageQueryKeys, refetchOnFocusIfStale } from '@/features/member/api/myPageQueryKeys';
import {
  getCenteredCalendarRailOffset,
  type CalendarRailItemLayout,
} from '@/features/member/calendarRail';
import {
  DEVELOPMENT_MY_PAGE_COUNTS,
  getDevelopmentMyFollowings,
  getDevelopmentMyReports,
  getDevelopmentMyStudies,
} from '@/features/member/developmentMyPageData';
import {
  REVIEW_TAG_CATEGORY_LABELS,
  REVIEW_TAG_CATEGORY_ORDER,
  getReviewTagsByCategory,
} from '@/features/review/reviewTags';
import { createMemberReview } from '@/features/study/api/createMemberReview';
import { getMembers } from '@/features/study/api/getMembers';
import {
  StudyApiError,
  type StudyMember,
  type StudyMemberListResult,
} from '@/features/study/api/types';
import { useAuthStore } from '@/store/authStore';
import { useMemberStore } from '@/store/memberStore';

type MyPageSectionKey = 'studies' | 'reports' | 'following';

type MyPageSection = {
  key: MyPageSectionKey;
  count: number;
  label: string;
};

type ReviewSheetStudy = Pick<MyStudy, 'studyId' | 'title' | 'apartment'>;

type CalendarDate = {
  year: number;
  month: number;
  day: number;
};

type CalendarMonth = Pick<CalendarDate, 'year' | 'month'>;

const PROFILE_IMAGE = require('../../../assets/images/characters/palbang.png');
const CHARACTER_IMAGES: Record<CharacterId, ImageSourcePropType> = {
  PALBANG: require('../../../assets/images/characters/palbang.png'),
  PALBANG_DOG: require('../../../assets/images/characters/palbang_dog.png'),
  PALBANG_RABBIT: require('../../../assets/images/characters/palbang_rabbit.png'),
};
const SCREEN_HORIZONTAL_PADDING = 20;
const WEEKDAYS = ['일', '월', '화', '수', '목', '금', '토'];
const MIN_CALENDAR_YEAR = 2000;
const MAX_CALENDAR_YEAR = 2100;
const REVIEW_MAX_LENGTH = 500;
const SCHEDULE_RAIL_DAY_SIZE = 42;
const SCHEDULE_RAIL_MIN_PADDING = 5;

const DEVELOPMENT_REVIEW_MEMBERS: StudyMember[] = [
  {
    memberId: 101,
    nickname: '집보는다람쥐',
    profileImageUrl: null,
    selectedCharacterId: 'PALBANG_DOG',
    role: 'LEADER',
    canKick: false,
    reviewedByMe: false,
    joinedAt: '2026-07-01T10:00:00+09:00',
  },
  {
    memberId: 102,
    nickname: '옥수탐방러',
    profileImageUrl: null,
    selectedCharacterId: 'PALBANG_RABBIT',
    role: 'MEMBER',
    canKick: false,
    reviewedByMe: true,
    joinedAt: '2026-07-02T10:00:00+09:00',
  },
  {
    memberId: 103,
    nickname: '성동구새싹',
    profileImageUrl: null,
    selectedCharacterId: 'PALBANG',
    role: 'MEMBER',
    canKick: false,
    reviewedByMe: false,
    joinedAt: '2026-07-03T10:00:00+09:00',
  },
];

const MY_PAGE_SECTION_LABELS: Pick<MyPageSection, 'key' | 'label'>[] = [
  { key: 'studies', label: '스터디' },
  { key: 'reports', label: '리포트' },
  { key: 'following', label: '팔로잉' },
];

const AGE_GROUP_LABELS: Record<string, string> = {
  TEENS: '10대',
  TWENTIES: '20대',
  THIRTIES: '30대',
  FORTIES: '40대',
  FIFTIES: '50대',
  SIXTIES_PLUS: '60대 이상',
};

function profileMeta(ageGroup: string | null, fieldVisitCompletedCount: number): string {
  const ageLabel = ageGroup ? AGE_GROUP_LABELS[ageGroup] : undefined;
  const visitCount = Math.max(fieldVisitCompletedCount, 0);
  return [ageLabel, `임장 ${visitCount}회 완료`].filter(Boolean).join(' · ');
}

function reportDate(completedAt: string | null): string {
  if (!completedAt) return '완료일 정보 없음';

  const date = new Date(completedAt);
  if (Number.isNaN(date.getTime())) return '완료일 정보 없음';

  return `${date.getFullYear()}.${String(date.getMonth() + 1).padStart(2, '0')}.${String(
    date.getDate(),
  ).padStart(2, '0')} 완료`;
}

const STUDY_STATUS_LABELS: Record<MyStudy['status'], string> = {
  RECRUITING: '모집 중',
  CLOSED: '모집 마감',
  IN_PROGRESS: '진행 중',
  COMPLETED: '완료',
};

type StudyFilter = 'ALL' | 'ACTIVE' | 'COMPLETED';

const STUDY_FILTERS: { key: StudyFilter; label: string }[] = [
  { key: 'ALL', label: '전체' },
  { key: 'ACTIVE', label: '진행중' },
  { key: 'COMPLETED', label: '완료' },
];

function studyScheduleDate(startAt: string | undefined): string {
  if (!startAt) return '일정 미정';

  const date = new Date(startAt);
  if (Number.isNaN(date.getTime())) return '일정 미정';

  return `${date.getMonth() + 1}월 ${date.getDate()}일`;
}

function followingMeta(following: MyFollowing): string {
  const ageLabel = following.ageGroup ? AGE_GROUP_LABELS[following.ageGroup] : null;
  return [ageLabel, `스터디 ${following.participatingStudyCount}개`]
    .filter(Boolean)
    .join(' · ');
}

// 원격 프로필 이미지는 요청마다 서명·만료 쿼리가 바뀌는 Presigned GET URL이라, 쿼리를 뗀
// S3 객체 경로를 cacheKey로 고정해 서명이 바뀌어도 expo-image 캐시가 적중되게 한다
// (커뮤니티 목록과 동일 패턴). 로컬 캐릭터 이미지는 기존 require 소스를 그대로 쓴다.
function followingImageSource(following: MyFollowing): ImageProps['source'] {
  return following.profileImageUrl
    ? { uri: following.profileImageUrl, cacheKey: following.profileImageUrl.split('?')[0] }
    : CHARACTER_IMAGES[following.selectedCharacterId];
}

function studyMemberImageSource(member: StudyMember): ImageProps['source'] {
  if (member.profileImageUrl) {
    return { uri: member.profileImageUrl, cacheKey: member.profileImageUrl.split('?')[0] };
  }

  return member.selectedCharacterId in CHARACTER_IMAGES
    ? CHARACTER_IMAGES[member.selectedCharacterId as CharacterId]
    : PROFILE_IMAGE;
}

function getSeoulToday(): CalendarDate {
  const parts = new Intl.DateTimeFormat('en-US', {
    timeZone: 'Asia/Seoul',
    year: 'numeric',
    month: 'numeric',
    day: 'numeric',
  }).formatToParts(new Date());
  const values = new Map(parts.map((part) => [part.type, part.value]));

  return {
    year: Number(values.get('year')),
    month: Number(values.get('month')),
    day: Number(values.get('day')),
  };
}

function getCalendarCells({ year, month }: CalendarMonth): (number | null)[] {
  const firstDayOffset = new Date(Date.UTC(year, month - 1, 1)).getUTCDay();
  const dayCount = new Date(Date.UTC(year, month, 0)).getUTCDate();
  const occupiedCellCount = firstDayOffset + dayCount;
  const cellCount = Math.max(35, Math.ceil(occupiedCellCount / 7) * 7);

  return Array.from({ length: cellCount }, (_, index) => {
    const day = index - firstDayOffset + 1;
    return day > 0 && day <= dayCount ? day : null;
  });
}

function shiftCalendarMonth(month: CalendarMonth, offset: number): CalendarMonth {
  const shifted = new Date(Date.UTC(month.year, month.month - 1 + offset, 1));
  return {
    year: shifted.getUTCFullYear(),
    month: shifted.getUTCMonth() + 1,
  };
}

function calendarDateKey(date: CalendarDate): string {
  return `${date.year}-${String(date.month).padStart(2, '0')}-${String(date.day).padStart(2, '0')}`;
}

function parseCalendarDateKey(value: string): CalendarDate | null {
  const match = /^(\d{4})-(\d{2})-(\d{2})$/.exec(value);
  if (!match) return null;

  return {
    year: Number(match[1]),
    month: Number(match[2]),
    day: Number(match[3]),
  };
}

function isSameCalendarDate(left: CalendarDate | null, right: CalendarDate | null): boolean {
  if (!left || !right) return false;
  return left.year === right.year && left.month === right.month && left.day === right.day;
}

function visitTime(startAt: string): string {
  const match = /T(\d{2}):(\d{2})/.exec(startAt);
  return match ? `${match[1]}:${match[2]}` : '시간 미정';
}

function CalendarScheduleContent({
  calendar,
  isDisabled,
  isError,
  isPending,
  onOpenStudy,
  onRetry,
  selectedDate,
}: {
  calendar: MyVisitCalendar | undefined;
  isDisabled: boolean;
  isError: boolean;
  isPending: boolean;
  onOpenStudy: (studyId: number) => void;
  onRetry: () => void;
  selectedDate: CalendarDate | null;
}) {
  const dateLabel = selectedDate
    ? `${selectedDate.month}월 ${selectedDate.day}일`
    : '날짜를 선택해 주세요';
  const selectedVisits = selectedDate
    ? (calendar?.dates.find((item) => item.date === calendarDateKey(selectedDate))?.visits ?? [])
    : [];

  if (isDisabled) {
    return (
      <View style={styles.calendarStateRow}>
        <Ionicons color={MUTED_TEXT_COLOR} name="lock-closed-outline" size={18} />
        <Text style={styles.calendarStateText}>로그인하면 임장 일정을 확인할 수 있어요.</Text>
      </View>
    );
  }

  if (isPending) {
    return (
      <View style={styles.calendarStateRow}>
        <ActivityIndicator color={PRIMARY_COLOR} size="small" />
        <Text style={styles.calendarStateText}>이달의 임장 일정을 불러오는 중이에요.</Text>
      </View>
    );
  }

  if (isError) {
    return (
      <View style={styles.calendarStateRow}>
        <Ionicons color={ERROR_COLOR} name="alert-circle-outline" size={18} />
        <Text style={styles.calendarStateText}>이달의 임장 일정을 불러오지 못했어요.</Text>
        <Pressable
          accessibilityLabel="임장 일정 다시 불러오기"
          accessibilityRole="button"
          onPress={onRetry}
          style={({ pressed }) => [styles.calendarRetryButton, pressed && styles.pressed]}
        >
          <Text style={styles.calendarRetryText}>재시도</Text>
        </Pressable>
      </View>
    );
  }

  if (!calendar || calendar.monthlyVisitCount === 0) {
    return (
      <View style={styles.calendarStateRow}>
        <Ionicons color={MUTED_TEXT_COLOR} name="calendar-clear-outline" size={18} />
        <Text style={styles.calendarStateText}>이달의 임장 일정이 없어요.</Text>
      </View>
    );
  }

  if (!selectedDate) {
    return (
      <View style={styles.calendarStateRow}>
        <Ionicons color={MUTED_TEXT_COLOR} name="calendar-outline" size={18} />
        <Text style={styles.calendarStateText}>일정이 표시된 날짜를 선택해 주세요.</Text>
      </View>
    );
  }

  if (selectedVisits.length === 0) {
    return (
      <View style={styles.calendarStateRow}>
        <Ionicons color={MUTED_TEXT_COLOR} name="calendar-clear-outline" size={18} />
        <Text style={styles.calendarStateText}>{dateLabel}에는 임장 일정이 없어요.</Text>
      </View>
    );
  }

  return (
    <View style={styles.calendarVisitList}>
      {selectedVisits.map((visit) => {
        const isCompleted = visit.scheduleStatus === 'COMPLETED';

        return (
          <Pressable
            accessibilityLabel={`${visit.studyTitle} 스터디 열기`}
            accessibilityRole="button"
            key={visit.scheduleId}
            onPress={() => onOpenStudy(visit.studyId)}
            style={({ pressed }) => [styles.calendarVisitCard, pressed && styles.cardPressed]}
          >
            <View style={styles.calendarVisitHeader}>
              <View
                style={[
                  styles.calendarVisitStatusChip,
                  isCompleted && styles.calendarVisitStatusChipCompleted,
                ]}
              >
                <Text
                  style={[
                    styles.calendarVisitStatusText,
                    isCompleted && styles.calendarVisitStatusTextCompleted,
                  ]}
                >
                  {isCompleted ? '완료' : '예정'}
                </Text>
              </View>
              <Text style={styles.calendarVisitTime}>{visitTime(visit.startAt)}</Text>
            </View>
            <Text numberOfLines={1} style={styles.calendarVisitTitle}>
              {visit.studyTitle}
            </Text>
            <View style={styles.calendarVisitApartmentRow}>
              <Ionicons color={MUTED_TEXT_COLOR} name="location-outline" size={14} />
              <Text numberOfLines={1} style={styles.calendarVisitApartment}>
                {visit.apartmentName}
              </Text>
            </View>
          </Pressable>
        );
      })}
    </View>
  );
}

function CalendarCard({
  enabled,
  onOpenStudy,
  sessionVersion,
}: {
  enabled: boolean;
  onOpenStudy: (studyId: number) => void;
  sessionVersion: number;
}) {
  const queryClient = useQueryClient();
  const [today] = useState(getSeoulToday);
  const [isExpanded, setIsExpanded] = useState(false);
  const [visibleMonth, setVisibleMonth] = useState<CalendarMonth>(() => ({
    year: today.year,
    month: today.month,
  }));
  const [selectedDate, setSelectedDate] = useState<CalendarDate | null>(null);
  const [scheduleRailViewportWidth, setScheduleRailViewportWidth] = useState(0);
  const scheduleRailRef = useRef<ScrollView>(null);
  const scheduleRailViewportWidthRef = useRef(0);
  const scheduleRailContentWidthRef = useRef(0);
  const scheduleRailItemLayoutsRef = useRef(new Map<string, CalendarRailItemLayout>());
  const calendarQueryKey = myPageQueryKeys.visitCalendar(
    sessionVersion,
    visibleMonth.year,
    visibleMonth.month,
  );
  const calendarQuery = useQuery({
    queryKey: calendarQueryKey,
    queryFn: () => getMyVisitCalendar(visibleMonth.year, visibleMonth.month),
    enabled,
  });

  useFocusEffect(
    useCallback(() => {
      if (!enabled) return;

      refetchOnFocusIfStale(
        queryClient,
        myPageQueryKeys.visitCalendar(sessionVersion, visibleMonth.year, visibleMonth.month),
      );
    }, [enabled, queryClient, sessionVersion, visibleMonth.month, visibleMonth.year]),
  );
  const calendarCells = getCalendarCells(visibleMonth);
  const eventDates = new Map(calendarQuery.data?.dates.map((item) => [item.date, item.visits]));
  const visitDates = (calendarQuery.data?.dates ?? [])
    .filter((item) => item.visits.length > 0)
    .slice()
    .sort((left, right) => left.date.localeCompare(right.date));
  const selectedDateKey = selectedDate ? calendarDateKey(selectedDate) : null;
  const todayKey = calendarDateKey(today);
  // 선택이 없을 때 기본값은 오늘 이후(오늘 포함) 가장 가까운 임장일. 미래 임장이 없으면
  // 가장 최근 임장일로 둔다(visitDates는 날짜 오름차순).
  const defaultVisitDate =
    visitDates.find((item) => item.date >= todayKey) ??
    visitDates[visitDates.length - 1] ??
    null;
  const activeVisitDate =
    visitDates.find((item) => item.date === selectedDateKey) ?? defaultVisitDate;
  const activeVisits = activeVisitDate?.visits ?? [];
  const activeDate = activeVisitDate ? parseCalendarDateKey(activeVisitDate.date) : null;
  const effectiveSelectedDate =
    selectedDate ??
    activeDate ??
    (visibleMonth.year === today.year && visibleMonth.month === today.month ? today : null);
  const activeWeekday = activeDate
    ? WEEKDAYS[
        new Date(Date.UTC(activeDate.year, activeDate.month - 1, activeDate.day)).getUTCDay()
      ]
    : null;
  const canMovePrevious = visibleMonth.year > MIN_CALENDAR_YEAR || visibleMonth.month > 1;
  const canMoveNext = visibleMonth.year < MAX_CALENDAR_YEAR || visibleMonth.month < 12;
  // 이동 화살표 옆에 목적지 달을 함께 표시한다(예: 8월이면 < 7월 … 9월 >).
  const previousMonth = shiftCalendarMonth(visibleMonth, -1);
  const nextMonth = shiftCalendarMonth(visibleMonth, 1);

  const centerScheduleRailDate = useCallback((date: string | null, animated: boolean) => {
    if (!date) return;

    const itemLayout = scheduleRailItemLayoutsRef.current.get(date);
    if (!itemLayout) return;

    scheduleRailRef.current?.scrollTo({
      x: getCenteredCalendarRailOffset(
        itemLayout,
        scheduleRailViewportWidthRef.current,
        scheduleRailContentWidthRef.current,
      ),
      animated,
    });
  }, []);

  useEffect(() => {
    centerScheduleRailDate(activeVisitDate?.date ?? null, true);
  }, [activeVisitDate?.date, centerScheduleRailDate]);

  useEffect(() => {
    scheduleRailItemLayoutsRef.current.clear();
    scheduleRailContentWidthRef.current = 0;
  }, [visibleMonth.month, visibleMonth.year]);

  const moveMonth = (offset: number) => {
    setVisibleMonth((current) => {
      const next = shiftCalendarMonth(current, offset);
      return next.year >= MIN_CALENDAR_YEAR && next.year <= MAX_CALENDAR_YEAR ? next : current;
    });
    setSelectedDate(null);
  };

  const moveToToday = () => {
    setVisibleMonth({ year: today.year, month: today.month });
    setSelectedDate(today);
  };

  return (
    <View style={styles.calendarCard}>
      <View style={styles.scheduleHeader}>
        <View style={styles.scheduleHeaderCopy}>
          <Text numberOfLines={1} style={styles.scheduleTitle}>
            {activeDate
              ? `${activeDate.month}월 ${activeDate.day}일`
              : `${visibleMonth.year}년 ${visibleMonth.month}월`}
          </Text>
        </View>
        <View style={styles.scheduleHeaderActions}>
          {activeDate ? (
            <View style={styles.scheduleDateBadge}>
              <Text style={styles.scheduleDateNumber}>
                {String(activeDate.day).padStart(2, '0')}
              </Text>
              <Text style={styles.scheduleDateWeekday}>{activeWeekday}요일</Text>
            </View>
          ) : null}
          <Pressable
            accessibilityHint={isExpanded ? '전체 달력을 접습니다.' : '전체 달력을 펼칩니다.'}
            accessibilityLabel={`${visibleMonth.year}년 ${visibleMonth.month}월 전체 달력, ${isExpanded ? '펼쳐짐' : '접힘'}`}
            accessibilityRole="button"
            onPress={() => setIsExpanded((current) => !current)}
            style={({ pressed }) => [styles.calendarCollapseButton, pressed && styles.pressed]}
          >
            <Ionicons
              color={MUTED_TEXT_COLOR}
              name={isExpanded ? 'calendar' : 'calendar-outline'}
              size={19}
            />
          </Pressable>
        </View>
      </View>

      {!isExpanded &&
        (calendarQuery.isPending && enabled ? (
        <View style={styles.scheduleVisitState}>
          <ActivityIndicator color={PRIMARY_COLOR} size="small" />
          <Text style={styles.scheduleVisitStateText}>다가오는 임장을 찾고 있어요.</Text>
        </View>
      ) : activeVisits.length > 0 ? (
        <View>
          {activeVisits.map((visit) => (
            <Pressable
              accessibilityLabel={`${visit.studyTitle} 스터디 열기`}
              accessibilityRole="button"
              key={visit.scheduleId}
              onPress={() => onOpenStudy(visit.studyId)}
              style={({ pressed }) => [styles.scheduleVisitFocus, pressed && styles.cardPressed]}
            >
              <View style={styles.scheduleVisitCopy}>
                <Text style={styles.scheduleVisitTime}>{visitTime(visit.startAt)}</Text>
                <Text numberOfLines={1} style={styles.scheduleVisitTitle}>
                  {visit.studyTitle}
                </Text>
                <Text numberOfLines={1} style={styles.scheduleVisitApartment}>
                  {visit.apartmentName}
                </Text>
              </View>
              <View style={styles.scheduleVisitArrow}>
                <Ionicons color={DARK_GREEN_COLOR} name="arrow-forward" size={18} />
              </View>
            </Pressable>
          ))}
        </View>
      ) : (
        <View style={styles.scheduleVisitState}>
          <View style={styles.scheduleVisitCopy}>
            <Text style={styles.scheduleVisitEmptyTitle}>예정된 임장이 없어요.</Text>
          </View>
        </View>
      ))}

      <View style={styles.scheduleDateRail}>
        <Pressable
          accessibilityLabel="이전 달 보기"
          accessibilityRole="button"
          accessibilityState={{ disabled: !canMovePrevious }}
          disabled={!canMovePrevious}
          onPress={() => moveMonth(-1)}
          style={({ pressed }) => [
            styles.calendarArrowButton,
            !canMovePrevious && styles.calendarArrowButtonDisabled,
            pressed && styles.pressed,
          ]}
        >
          <Ionicons color={TEXT_COLOR} name="chevron-back" size={18} />
        </Pressable>
        <Text
          style={[
            styles.scheduleRailMonth,
            !canMovePrevious && styles.scheduleRailMonthDisabled,
          ]}
        >
          {previousMonth.month}월
        </Text>
        <ScrollView
          contentContainerStyle={[
            styles.scheduleRailDays,
            {
              paddingHorizontal: Math.max(
                SCHEDULE_RAIL_MIN_PADDING,
                (scheduleRailViewportWidth - SCHEDULE_RAIL_DAY_SIZE) / 2,
              ),
            },
          ]}
          horizontal
          onContentSizeChange={(width) => {
            scheduleRailContentWidthRef.current = width;
            centerScheduleRailDate(activeVisitDate?.date ?? null, false);
          }}
          onLayout={(event) => {
            const { width } = event.nativeEvent.layout;
            scheduleRailViewportWidthRef.current = width;
            setScheduleRailViewportWidth((current) => (current === width ? current : width));
            centerScheduleRailDate(activeVisitDate?.date ?? null, false);
          }}
          ref={scheduleRailRef}
          showsHorizontalScrollIndicator={false}
          style={styles.scheduleRailScroll}
        >
          {visitDates.length > 0 ? (
            visitDates.map((item) => {
              const parsedDate = parseCalendarDateKey(item.date);
              if (!parsedDate) return null;
              const { year, month, day } = parsedDate;
              const weekday = WEEKDAYS[new Date(Date.UTC(year, month - 1, day)).getUTCDay()];
              const isActive = item.date === activeVisitDate?.date;
              return (
                <Pressable
                  accessibilityLabel={`${month}월 ${day}일 ${weekday}요일 임장 일정`}
                  accessibilityRole="button"
                  accessibilityState={{ selected: isActive }}
                  key={item.date}
                  onLayout={(event) => {
                    const { x, width } = event.nativeEvent.layout;
                    scheduleRailItemLayoutsRef.current.set(item.date, { x, width });
                    if (isActive) centerScheduleRailDate(item.date, false);
                  }}
                  onPress={() => {
                    setSelectedDate({ year, month, day });
                    centerScheduleRailDate(item.date, true);
                  }}
                  style={({ pressed }) => [
                    styles.scheduleRailDay,
                    isActive && styles.scheduleRailDayActive,
                    pressed && styles.pressed,
                  ]}
                >
                  <Text
                    style={[
                      styles.scheduleRailDayNumber,
                      isActive && styles.scheduleRailDayTextActive,
                    ]}
                  >
                    {String(day).padStart(2, '0')}
                  </Text>
                  <Text
                    style={[
                      styles.scheduleRailWeekday,
                      isActive && styles.scheduleRailDayTextActive,
                    ]}
                  >
                    {weekday}
                  </Text>
                </Pressable>
              );
            })
          ) : (
            <Text style={styles.scheduleRailEmpty}>임장일 없음</Text>
          )}
        </ScrollView>
        <Text
          style={[styles.scheduleRailMonth, !canMoveNext && styles.scheduleRailMonthDisabled]}
        >
          {nextMonth.month}월
        </Text>
        <Pressable
          accessibilityLabel="다음 달 보기"
          accessibilityRole="button"
          accessibilityState={{ disabled: !canMoveNext }}
          disabled={!canMoveNext}
          onPress={() => moveMonth(1)}
          style={({ pressed }) => [
            styles.calendarArrowButton,
            !canMoveNext && styles.calendarArrowButtonDisabled,
            pressed && styles.pressed,
          ]}
        >
          <Ionicons color={TEXT_COLOR} name="chevron-forward" size={18} />
        </Pressable>
      </View>

      {isExpanded ? (
        <View style={styles.calendarBody}>
          <View style={styles.calendarExpandedHeader}>
            <Text style={styles.calendarTitle}>
              {visibleMonth.year}년 {visibleMonth.month}월 전체 일정
            </Text>
            <View
              style={[styles.visitCountChip, calendarQuery.isError && styles.visitCountChipError]}
            >
              <Text
                style={[styles.visitCountText, calendarQuery.isError && styles.visitCountTextError]}
              >
                {!enabled
                  ? '로그인 필요'
                  : calendarQuery.isError
                    ? '조회 실패'
                    : `임장 ${calendarQuery.data?.monthlyVisitCount ?? 0}건`}
              </Text>
            </View>
          </View>
          <View style={styles.weekdayRow}>
            {WEEKDAYS.map((weekday, index) => (
              <View key={weekday} style={styles.calendarColumn}>
                <Text
                  style={[
                    styles.weekdayText,
                    index === 0 && styles.sundayText,
                    index === 6 && styles.saturdayText,
                  ]}
                >
                  {weekday}
                </Text>
              </View>
            ))}
          </View>

          <View style={styles.calendarGrid}>
            {calendarCells.map((day, index) => {
              if (day === null) {
                return <View key={`empty-${index}`} style={styles.dayCell} />;
              }

              const date = { ...visibleMonth, day };
              const isSelected = isSameCalendarDate(date, effectiveSelectedDate);
              const isToday = isSameCalendarDate(date, today);
              const visits = eventDates.get(calendarDateKey(date)) ?? [];
              const eventCount = visits.length;
              const hasEvent = eventCount > 0;
              const hasScheduledVisit = visits.some(
                (visit: MyVisitCalendarVisit) => visit.scheduleStatus === 'SCHEDULED',
              );

              return (
                <View key={day} style={styles.dayCell}>
                  <Pressable
                    accessibilityLabel={`${visibleMonth.year}년 ${visibleMonth.month}월 ${day}일${isToday ? ', 오늘' : ''}${isSelected ? ', 선택됨' : ''}${hasEvent ? `, 임장 일정 ${eventCount}건` : ''}`}
                    accessibilityRole="button"
                    accessibilityState={{ selected: isSelected }}
                    onPress={() => setSelectedDate(date)}
                    style={({ pressed }) => [
                      styles.dayButton,
                      isToday && styles.todayDayButton,
                      hasEvent && styles.eventDayButton,
                      hasEvent && !hasScheduledVisit && styles.completedEventDayButton,
                      isSelected && styles.selectedDayButton,
                      pressed && styles.dayButtonPressed,
                    ]}
                  >
                    <Text
                      style={[
                        styles.dayText,
                        isToday && styles.todayDayText,
                        isSelected && styles.selectedDayText,
                      ]}
                    >
                      {day}
                    </Text>
                    {eventCount === 1 && !isSelected ? (
                      <View
                        style={[styles.eventDot, !hasScheduledVisit && styles.completedEventDot]}
                      />
                    ) : null}
                    {eventCount > 1 ? (
                      <View
                        style={[
                          styles.eventCountBadge,
                          isSelected && styles.eventCountBadgeSelected,
                        ]}
                      >
                        <Text
                          style={[
                            styles.eventCountBadgeText,
                            isSelected && styles.eventCountBadgeTextSelected,
                          ]}
                        >
                          {eventCount}
                        </Text>
                      </View>
                    ) : null}
                  </Pressable>
                </View>
              );
            })}
          </View>
          <View style={styles.calendarSelectionHeader}>
            <Text style={styles.calendarSelectionText}>
              {effectiveSelectedDate
                ? `${effectiveSelectedDate.year}년 ${effectiveSelectedDate.month}월 ${effectiveSelectedDate.day}일`
                : '날짜를 선택해 주세요'}
            </Text>
            {isSameCalendarDate(effectiveSelectedDate, today) ? (
              <View accessibilityLabel="선택한 날짜는 오늘입니다" style={styles.todayBadge}>
                <Text style={styles.todayBadgeText}>오늘</Text>
              </View>
            ) : (
              <Pressable
                accessibilityLabel="오늘 날짜로 이동"
                accessibilityRole="button"
                onPress={moveToToday}
                style={({ pressed }) => [styles.todayMoveButton, pressed && styles.pressed]}
              >
                <Text style={styles.todayMoveButtonText}>오늘로 이동</Text>
              </Pressable>
            )}
          </View>
          <CalendarScheduleContent
            calendar={calendarQuery.data}
            isDisabled={!enabled}
            isError={calendarQuery.isError}
            isPending={calendarQuery.isPending}
            onOpenStudy={onOpenStudy}
            onRetry={() => void calendarQuery.refetch()}
            selectedDate={effectiveSelectedDate}
          />
        </View>
      ) : null}
    </View>
  );
}

function SectionSelector({
  sections,
  selectedSection,
  onSelect,
}: {
  sections: MyPageSection[];
  selectedSection: MyPageSectionKey;
  onSelect: (section: MyPageSectionKey) => void;
}) {
  return (
    <View style={styles.sectionNavigation}>
      <View accessibilityRole="tablist" style={styles.sectionSelector}>
        {sections.map((section) => {
          const isSelected = section.key === selectedSection;

          return (
            <Pressable
              accessibilityLabel={`${section.label} ${section.count}개`}
              accessibilityRole="tab"
              accessibilityState={{ selected: isSelected }}
              key={section.key}
              onPress={() => onSelect(section.key)}
              style={({ pressed }) => [styles.sectionTab, pressed && styles.pressed]}
            >
              <View style={styles.sectionTabContent}>
                <Text style={[styles.sectionTabLabel, isSelected && styles.sectionTabLabelActive]}>
                  {section.label}
                </Text>
                <View
                  style={[styles.sectionCountBadge, isSelected && styles.sectionCountBadgeActive]}
                >
                  <Text
                    style={[styles.sectionCountText, isSelected && styles.sectionCountTextActive]}
                  >
                    {section.count}
                  </Text>
                </View>
              </View>
              {isSelected ? <View style={styles.sectionIndicator} /> : null}
            </Pressable>
          );
        })}
      </View>
    </View>
  );
}

function StatusChip({ label, tone }: { label: string; tone: 'green' | 'neutral' | 'red' }) {
  return (
    <View
      style={[
        styles.statusChip,
        tone === 'neutral' && styles.statusChipNeutral,
        tone === 'red' && styles.statusChipRed,
      ]}
    >
      <Text
        style={[
          styles.statusChipText,
          tone === 'neutral' && styles.statusChipTextNeutral,
          tone === 'red' && styles.statusChipTextRed,
        ]}
      >
        {label}
      </Text>
    </View>
  );
}

function StudyList({
  isError,
  isFetchingNextPage,
  isPending,
  onOpen,
  onReview,
  onRetry,
  studies,
}: {
  isError: boolean;
  isFetchingNextPage: boolean;
  isPending: boolean;
  onOpen: (studyId: number) => void;
  onReview: (study: MyStudy) => void;
  onRetry: () => void;
  studies: MyStudy[];
}) {
  const [studyFilter, setStudyFilter] = useState<StudyFilter>('ALL');

  if (isPending) {
    return (
      <View style={[styles.listCard, styles.queryStateCard]}>
        <ActivityIndicator color={PRIMARY_COLOR} />
        <Text style={styles.queryStateText}>스터디를 불러오는 중이에요.</Text>
      </View>
    );
  }

  if (isError) {
    return (
      <View style={[styles.listCard, styles.queryStateCard]}>
        <Text style={styles.emptyTitle}>스터디를 불러오지 못했어요</Text>
        <Pressable
          accessibilityRole="button"
          onPress={onRetry}
          style={({ pressed }) => [styles.retryButton, pressed && styles.pressed]}
        >
          <Text style={styles.retryButtonText}>다시 시도</Text>
        </Pressable>
      </View>
    );
  }

  if (studies.length === 0) {
    return (
      <View style={[styles.listCard, styles.emptyCard]}>
        <View style={styles.emptyIcon}>
          <Ionicons color={DARK_GREEN_COLOR} name="people-outline" size={24} />
        </View>
        <Text style={styles.emptyTitle}>아직 참여한 스터디가 없어요</Text>
        <Text style={styles.emptyDescription}>
          관심 있는 임장 스터디에 참여하면 이곳에서 확인할 수 있어요.
        </Text>
      </View>
    );
  }

  const filteredStudies = studies.filter((study) => {
    if (studyFilter === 'ACTIVE') return study.status !== 'COMPLETED';
    if (studyFilter === 'COMPLETED') return study.status === 'COMPLETED';
    return true;
  });

  return (
    <View style={styles.list}>
      <View accessibilityRole="tablist" style={styles.studyFilterRow}>
        {STUDY_FILTERS.map((filter) => {
          const isSelected = filter.key === studyFilter;

          return (
            <Pressable
              accessibilityLabel={`${filter.label} 스터디 보기`}
              accessibilityRole="tab"
              accessibilityState={{ selected: isSelected }}
              key={filter.key}
              onPress={() => setStudyFilter(filter.key)}
              style={({ pressed }) => [
                styles.studyFilterChip,
                isSelected && styles.studyFilterChipActive,
                pressed && styles.pressed,
              ]}
            >
              <Text
                style={[
                  styles.studyFilterChipText,
                  isSelected && styles.studyFilterChipTextActive,
                ]}
              >
                {filter.label}
              </Text>
            </Pressable>
          );
        })}
      </View>
      {filteredStudies.length === 0 ? (
        <View style={[styles.listCard, styles.emptyCard]}>
          <View style={styles.emptyIcon}>
            <Ionicons color={DARK_GREEN_COLOR} name="funnel-outline" size={22} />
          </View>
          <Text style={styles.emptyTitle}>해당 상태의 스터디가 없어요</Text>
          <Text style={styles.emptyDescription}>다른 상태 필터를 눌러 확인해 보세요.</Text>
        </View>
      ) : null}
      {filteredStudies.map((study) => {
        const completed = study.status === 'COMPLETED';
        const reviewsComplete = completed && study.pendingReviewCount === 0;
        const isAwaitingFieldVisit =
          study.status === 'IN_PROGRESS' && study.hasReturnableFieldVisit === false;
        const statusLabel = isAwaitingFieldVisit
          ? '임장 대기'
          : STUDY_STATUS_LABELS[study.status];
        const tone: 'green' | 'neutral' | 'red' =
          completed || isAwaitingFieldVisit
            ? 'neutral'
            : study.status === 'CLOSED'
              ? 'red'
              : 'green';

        return (
          <View key={study.studyId} style={[styles.listCard, styles.studyCard]}>
            <Pressable
              accessibilityLabel={`${study.title}, ${statusLabel}`}
              accessibilityRole="button"
              onPress={() => onOpen(study.studyId)}
              style={({ pressed }) => [styles.studyOpenArea, pressed && styles.cardPressed]}
            >
              <View style={styles.studyDescription}>
                <View style={styles.studyChipRow}>
                  <StatusChip label={statusLabel} tone={tone} />
                  {study.role === 'LEADER' ? (
                    <View style={styles.studyLeaderBadge}>
                      <Ionicons color={DARK_GREEN_COLOR} name="ribbon" size={11} />
                      <Text style={styles.studyLeaderBadgeText}>스터디장</Text>
                    </View>
                  ) : null}
                </View>
                <Text numberOfLines={1} style={styles.studyTitle}>
                  {study.title}
                </Text>
                <Text numberOfLines={1} style={styles.studyApartment}>
                  {study.apartment.name}
                </Text>
              </View>
              <View style={styles.nextVisitArea}>
                <Text style={styles.nextVisitLabel}>{completed ? '임장 완료' : '다음 임장'}</Text>
                <Text style={styles.nextVisitDate}>
                  {studyScheduleDate(study.nextSchedule?.startAt)}
                </Text>
              </View>
            </Pressable>
            {completed ? (
              <View style={styles.studyReviewRow}>
                <View style={styles.studyReviewGuide}>
                  <Ionicons
                    color={PRIMARY_COLOR}
                    name={reviewsComplete ? 'checkmark-circle' : 'pricetags-outline'}
                    size={15}
                  />
                  <Text style={styles.studyReviewGuideText}>
                    {reviewsComplete
                      ? '스터디원 리뷰를 모두 작성했어요'
                      : `평가할 스터디원이 ${study.pendingReviewCount}명 남았어요`}
                  </Text>
                </View>
                {!reviewsComplete ? (
                  <Pressable
                    accessibilityLabel={`${study.title} 스터디원 태그와 좋아요 남기기`}
                    accessibilityRole="button"
                    onPress={() => onReview(study)}
                    style={({ pressed }) => [styles.studyReviewButton, pressed && styles.pressed]}
                  >
                    <Text style={styles.studyReviewButtonText}>리뷰 남기기</Text>
                    <Ionicons color={DARK_GREEN_COLOR} name="chevron-forward" size={14} />
                  </Pressable>
                ) : null}
              </View>
            ) : null}
          </View>
        );
      })}
      {isFetchingNextPage ? (
        <View accessibilityLabel="스터디를 더 불러오는 중" style={styles.infiniteLoader}>
          <ActivityIndicator color={PRIMARY_COLOR} size="small" />
          <Text style={styles.infiniteLoaderText}>스터디를 더 불러오는 중이에요.</Text>
        </View>
      ) : null}
    </View>
  );
}

function ReportList({
  isError,
  isFetchingNextPage,
  isPending,
  onOpenReport,
  onRetry,
  reports,
}: {
  isError: boolean;
  isFetchingNextPage: boolean;
  isPending: boolean;
  onOpenReport: (reportId: number) => void;
  onRetry: () => void;
  reports: MyReport[];
}) {
  if (isPending) {
    return (
      <View style={[styles.listCard, styles.queryStateCard]}>
        <ActivityIndicator color={PRIMARY_COLOR} />
        <Text style={styles.queryStateText}>리포트를 불러오는 중이에요.</Text>
      </View>
    );
  }

  if (isError) {
    return (
      <View style={[styles.listCard, styles.queryStateCard]}>
        <Text style={styles.emptyTitle}>리포트를 불러오지 못했어요</Text>
        <Pressable
          accessibilityRole="button"
          onPress={onRetry}
          style={({ pressed }) => [styles.retryButton, pressed && styles.pressed]}
        >
          <Text style={styles.retryButtonText}>다시 시도</Text>
        </Pressable>
      </View>
    );
  }

  if (reports.length === 0) {
    return (
      <View style={[styles.listCard, styles.emptyCard]}>
        <View style={styles.emptyIcon}>
          <Ionicons color={DARK_GREEN_COLOR} name="document-text-outline" size={24} />
        </View>
        <Text style={styles.emptyTitle}>아직 완료된 리포트가 없어요</Text>
        <Text style={styles.emptyDescription}>
          임장이 완료되면 생성된 리포트를 확인할 수 있어요.
        </Text>
      </View>
    );
  }

  return (
    <View style={styles.list}>
      {reports.map((report) => (
        <Pressable
          accessibilityLabel={`${report.title ?? report.apartment.name}, ${reportDate(
            report.completedAt,
          )}`}
          accessibilityRole="button"
          key={report.reportId}
          onPress={() => onOpenReport(report.reportId)}
          style={({ pressed }) => [
            styles.listCard,
            styles.reportCard,
            pressed && styles.cardPressed,
          ]}
        >
          <View style={styles.reportHeader}>
            <StatusChip label={report.analysisTags[0] ?? '리포트'} tone="green" />
            {report.favoritedByMe ? (
              <Ionicons color={PRIMARY_COLOR} name="bookmark" size={17} />
            ) : null}
          </View>
          <Text numberOfLines={1} style={styles.reportTitle}>
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
          <Text style={styles.reportDate}>{reportDate(report.completedAt)}</Text>
        </Pressable>
      ))}
      {isFetchingNextPage ? (
        <View accessibilityLabel="리포트를 더 불러오는 중" style={styles.infiniteLoader}>
          <ActivityIndicator color={PRIMARY_COLOR} size="small" />
          <Text style={styles.infiniteLoaderText}>리포트를 더 불러오는 중이에요.</Text>
        </View>
      ) : null}
    </View>
  );
}

function FollowingList({
  followings,
  isError,
  isFetchingNextPage,
  isPending,
  onMessage,
  onOpen,
  onRetry,
  onUnfollow,
  updatingMemberId,
}: {
  followings: MyFollowing[];
  isError: boolean;
  isFetchingNextPage: boolean;
  isPending: boolean;
  onMessage: (following: MyFollowing) => void;
  onOpen: (memberId: number) => void;
  onRetry: () => void;
  onUnfollow: (following: MyFollowing) => void;
  updatingMemberId: number | null;
}) {
  if (isPending) {
    return (
      <View style={[styles.listCard, styles.queryStateCard]}>
        <ActivityIndicator color={PRIMARY_COLOR} />
        <Text style={styles.queryStateText}>팔로잉 목록을 불러오는 중이에요.</Text>
      </View>
    );
  }

  if (isError) {
    return (
      <View style={[styles.listCard, styles.queryStateCard]}>
        <Text style={styles.emptyTitle}>팔로잉 목록을 불러오지 못했어요</Text>
        <Pressable
          accessibilityRole="button"
          onPress={onRetry}
          style={({ pressed }) => [styles.retryButton, pressed && styles.pressed]}
        >
          <Text style={styles.retryButtonText}>다시 시도</Text>
        </Pressable>
      </View>
    );
  }

  if (followings.length === 0) {
    return (
      <View style={[styles.listCard, styles.emptyCard]}>
        <View style={styles.emptyIcon}>
          <Ionicons color={DARK_GREEN_COLOR} name="person-add-outline" size={24} />
        </View>
        <Text style={styles.emptyTitle}>아직 팔로잉한 사용자가 없어요</Text>
        <Text style={styles.emptyDescription}>
          다른 사용자를 팔로우하면 이곳에서 바로 확인할 수 있어요.
        </Text>
      </View>
    );
  }

  return (
    <View style={styles.list}>
      {followings.map((following) => {
        const isUpdating = updatingMemberId === following.memberId;

        return (
          <View key={following.memberId} style={[styles.listCard, styles.followingCard]}>
            <Pressable
              accessibilityLabel={`${following.nickname} 공개 프로필 열기`}
              accessibilityRole="button"
              onPress={() => onOpen(following.memberId)}
              style={({ pressed }) => [styles.followingMain, pressed && styles.pressed]}
            >
              <View style={styles.followingAvatar}>
                <Image
                  contentFit="contain"
                  source={followingImageSource(following)}
                  style={styles.followingAvatarImage}
                />
              </View>
              <View style={styles.followingDescription}>
                <Text numberOfLines={1} style={styles.followingName}>
                  {following.nickname}
                </Text>
                <Text numberOfLines={1} style={styles.followingMeta}>
                  {followingMeta(following)}
                </Text>
              </View>
            </Pressable>
            <View style={styles.followingActions}>
              <Pressable
                accessibilityLabel={`${following.nickname}님 팔로우 해제`}
                accessibilityRole="button"
                disabled={isUpdating}
                onPress={() => onUnfollow(following)}
                style={({ pressed }) => [styles.followingStatusButton, pressed && styles.pressed]}
              >
                {isUpdating ? (
                  <ActivityIndicator color={DARK_GREEN_COLOR} size="small" />
                ) : (
                  <>
                    <Ionicons color={DARK_GREEN_COLOR} name="checkmark" size={15} />
                    <Text style={styles.followingStatusText}>팔로잉</Text>
                  </>
                )}
              </Pressable>
              {following.canSendMessage ? (
                <Pressable
                  accessibilityLabel={`${following.nickname}님에게 쪽지 보내기`}
                  accessibilityRole="button"
                  onPress={() => onMessage(following)}
                  style={({ pressed }) => [styles.messageButton, pressed && styles.pressed]}
                >
                  <Ionicons color={DARK_GREEN_COLOR} name="mail-outline" size={19} />
                </Pressable>
              ) : null}
            </View>
          </View>
        );
      })}
      {isFetchingNextPage ? (
        <View accessibilityLabel="팔로잉을 더 불러오는 중" style={styles.infiniteLoader}>
          <ActivityIndicator color={PRIMARY_COLOR} size="small" />
          <Text style={styles.infiniteLoaderText}>팔로잉을 더 불러오는 중이에요.</Text>
        </View>
      ) : null}
    </View>
  );
}

function ProfileReputation({ topTags }: { topTags: ReviewTagSummary[] }) {
  // 상위 1~2개만 배지로 노출해 프로필 요약이 길어지지 않게 한다.
  const badges = topTags.slice(0, 2);

  if (badges.length === 0) {
    return (
      <View accessibilityLabel="아직 받은 리뷰가 없어요" style={styles.reputationLine}>
        <Text style={styles.reputationHint}>아직 받은 리뷰가 없어요</Text>
      </View>
    );
  }

  const accessibilityLabel = `대표 태그 ${badges.map((tag) => tag.label).join(', ')}`;

  return (
    <View accessibilityLabel={accessibilityLabel} style={styles.reputationLine}>
      {badges.map((tag) => (
        <View key={tag.code} style={styles.reputationTag}>
          <Text style={styles.reputationTagEmoji}>{tag.emoji}</Text>
          <Text numberOfLines={1} style={styles.reputationTagLabel}>
            {tag.label}
          </Text>
        </View>
      ))}
    </View>
  );
}

function ReviewSheet({
  isPreview,
  onClose,
  study,
  viewerMemberId,
}: {
  isPreview: boolean;
  onClose: () => void;
  study: ReviewSheetStudy | null;
  viewerMemberId?: number;
}) {
  const queryClient = useQueryClient();
  const [step, setStep] = useState<'target' | 'review'>('target');
  const [selectedMemberId, setSelectedMemberId] = useState<number | null>(null);
  const [selectedTags, setSelectedTags] = useState<string[]>([]);
  const [liked, setLiked] = useState(false);
  const [reviewText, setReviewText] = useState('');
  const memberQueryKey = ['study', 'members', 'review', study?.studyId, viewerMemberId] as const;
  const membersQuery = useQuery({
    queryKey: memberQueryKey,
    queryFn: () => {
      if (!study) throw new Error('리뷰할 스터디를 찾을 수 없습니다.');
      return getMembers(study.studyId);
    },
    enabled: Boolean(study) && !isPreview && typeof viewerMemberId === 'number',
  });
  const members = (
    isPreview ? DEVELOPMENT_REVIEW_MEMBERS : (membersQuery.data?.members ?? [])
  ).filter((member) => member.memberId !== viewerMemberId);
  const selectedMember = members.find((member) => member.memberId === selectedMemberId);
  const markMemberReviewed = (memberId: number) => {
    queryClient.setQueryData<StudyMemberListResult>(memberQueryKey, (current) =>
      current
        ? {
            ...current,
            members: current.members.map((member) =>
              member.memberId === memberId ? { ...member, reviewedByMe: true } : member,
            ),
          }
        : current,
    );
  };
  const reviewMutation = useMutation({
    mutationFn: ({
      memberId,
      tags,
      liked: likedValue,
      content,
    }: {
      memberId: number;
      tags: string[];
      liked: boolean;
      content: string | null;
    }) => {
      if (!study) throw new Error('리뷰할 스터디를 찾을 수 없습니다.');
      return createMemberReview(study.studyId, memberId, {
        tags,
        liked: likedValue,
        content,
      });
    },
    onSuccess: async (_, variables) => {
      markMemberReviewed(variables.memberId);
      await Promise.all([
        queryClient.invalidateQueries({ queryKey: ['member', 'reviews', variables.memberId] }),
        queryClient.invalidateQueries({ queryKey: ['member', 'me', 'studies'] }),
      ]);
      appAlert('리뷰 등록 완료', '익명 리뷰가 등록되었습니다.', [
        { text: '확인', onPress: onClose },
      ]);
    },
    onError: async (error, variables) => {
      if (error instanceof StudyApiError && error.code === 'MEMBER_REVIEW_ALREADY_EXISTS') {
        markMemberReviewed(variables.memberId);
        setSelectedMemberId(null);
        setSelectedTags([]);
        setLiked(false);
        setReviewText('');
        setStep('target');
        await queryClient.invalidateQueries({ queryKey: ['member', 'me', 'studies'] });
      }
      appAlert(
        '리뷰 등록 실패',
        error instanceof StudyApiError
          ? error.message
          : '리뷰를 등록하지 못했습니다. 잠시 후 다시 시도해 주세요.',
      );
    },
  });
  // 대상을 골라야 다음(리뷰) 단계로 넘어갈 수 있다.
  const canProceedToReview = Boolean(selectedMember) && !selectedMember?.reviewedByMe;
  // 백엔드 신호 규칙과 동일: 태그 1개 이상 또는 좋아요가 있어야 등록 가능.
  const hasSignal = selectedTags.length > 0 || liked;
  const isDraftComplete = canProceedToReview && hasSignal && !reviewMutation.isPending;

  const toggleTag = (code: string) => {
    setSelectedTags((current) =>
      current.includes(code) ? current.filter((tag) => tag !== code) : [...current, code],
    );
  };

  const goToReviewStep = () => {
    if (!canProceedToReview) return;
    setStep('review');
  };

  const submitReview = () => {
    if (!isDraftComplete) return;
    if (isPreview) {
      appAlert('미리보기', '실제 로그인 후 완료된 스터디에서 리뷰를 등록할 수 있습니다.');
      return;
    }
    if (!selectedMember) return;
    reviewMutation.mutate({
      memberId: selectedMember.memberId,
      tags: selectedTags,
      liked,
      content: reviewText.trim() || null,
    });
  };

  return (
    <Modal
      animationType="slide"
      onRequestClose={() => {
        if (!reviewMutation.isPending) onClose();
      }}
      statusBarTranslucent
      transparent
      visible={Boolean(study)}
    >
      <KeyboardAvoidingView
        behavior={Platform.OS === 'ios' ? 'padding' : 'height'}
        style={styles.reviewModalRoot}
      >
        <Pressable
          accessibilityLabel="리뷰 작성 닫기"
          disabled={reviewMutation.isPending}
          onPress={onClose}
          style={styles.reviewScrim}
        />
        <View style={styles.reviewSheet}>
          <View style={styles.reviewHandle} />
          <View style={styles.reviewHeader}>
            {step === 'review' ? (
              <Pressable
                accessibilityLabel="대상 선택으로 돌아가기"
                accessibilityRole="button"
                disabled={reviewMutation.isPending}
                onPress={() => setStep('target')}
                style={({ pressed }) => [styles.reviewBackButton, pressed && styles.pressed]}
              >
                <Ionicons color={TEXT_COLOR} name="chevron-back" size={22} />
              </Pressable>
            ) : null}
            <View style={styles.reviewHeaderCopy}>
              <Text style={styles.reviewEyebrow}>
                {step === 'target' ? '함께한 스터디 리뷰' : '리뷰 남기기'}
              </Text>
              <Text numberOfLines={1} style={styles.reviewTitle}>
                {study?.title ?? ''}
              </Text>
              <Text numberOfLines={1} style={styles.reviewApartment}>
                {study?.apartment.name ?? ''}
              </Text>
            </View>
            <Pressable
              accessibilityLabel="리뷰 작성 닫기"
              accessibilityRole="button"
              disabled={reviewMutation.isPending}
              onPress={onClose}
              style={({ pressed }) => [styles.reviewCloseButton, pressed && styles.pressed]}
            >
              <Ionicons color={TEXT_COLOR} name="close" size={22} />
            </Pressable>
          </View>

          <ScrollView
            contentContainerStyle={styles.reviewSheetContent}
            keyboardShouldPersistTaps="handled"
            showsVerticalScrollIndicator={false}
          >
            {step === 'target' ? (
              <>
                <View style={styles.reviewInfoCard}>
                  <Ionicons color={DARK_GREEN_COLOR} name="shield-checkmark-outline" size={18} />
                  <Text style={styles.reviewInfoText}>
                    함께 완료한 스터디원만 평가할 수 있어요. 먼저 리뷰를 남길 대상을 골라 주세요.
                  </Text>
                </View>

                <Text style={styles.reviewFieldLabel}>누구에게 남길까요?</Text>
                {membersQuery.isPending && !isPreview ? (
                  <View style={styles.reviewMemberState}>
                    <ActivityIndicator color={PRIMARY_COLOR} size="small" />
                    <Text style={styles.reviewMemberStateText}>
                      함께한 스터디원을 불러오는 중이에요.
                    </Text>
                  </View>
                ) : membersQuery.isError && !isPreview ? (
                  <View style={styles.reviewMemberState}>
                    <Text style={styles.reviewMemberStateText}>
                      스터디원 목록을 불러오지 못했어요.
                    </Text>
                    <Pressable
                      accessibilityRole="button"
                      onPress={() => void membersQuery.refetch()}
                      style={({ pressed }) => [styles.reviewRetryButton, pressed && styles.pressed]}
                    >
                      <Text style={styles.reviewRetryButtonText}>다시 시도</Text>
                    </Pressable>
                  </View>
                ) : members.length === 0 ? (
                  <View style={styles.reviewMemberState}>
                    <Text style={styles.reviewMemberStateText}>
                      평가할 수 있는 다른 스터디원이 없어요.
                    </Text>
                  </View>
                ) : (
                  <View style={styles.reviewMemberList}>
                    {members.map((member) => {
                      const reviewed = member.reviewedByMe;
                      const selected = !reviewed && member.memberId === selectedMemberId;
                      return (
                        <Pressable
                          accessibilityLabel={
                            reviewed
                              ? `${member.nickname}, 리뷰 작성 완료`
                              : `${member.nickname} 선택`
                          }
                          accessibilityRole="checkbox"
                          accessibilityState={{ checked: selected, disabled: reviewed }}
                          disabled={reviewed}
                          key={member.memberId}
                          onPress={() => setSelectedMemberId(member.memberId)}
                          style={({ pressed }) => [
                            styles.reviewMemberRow,
                            selected && styles.reviewMemberRowSelected,
                            reviewed && styles.reviewMemberRowCompleted,
                            pressed && !reviewed && styles.pressed,
                          ]}
                        >
                          {/* 체크리스트(ChecklistListView) 체크박스 이식: 미선택=빈 원, 선택=민트 채움+체크 */}
                          <View
                            style={[
                              styles.reviewCheckbox,
                              selected && styles.reviewCheckboxChecked,
                            ]}
                          >
                            {selected ? (
                              <Ionicons color={PRIMARY_COLOR} name="checkmark" size={17} />
                            ) : null}
                          </View>
                          <View style={styles.reviewMemberRowAvatar}>
                            <Image
                              contentFit="contain"
                              source={studyMemberImageSource(member)}
                              style={styles.reviewMemberRowAvatarImage}
                            />
                          </View>
                          <View style={styles.reviewMemberRowText}>
                            <Text numberOfLines={1} style={styles.reviewMemberRowName}>
                              {member.nickname}
                            </Text>
                            <Text
                              style={[
                                styles.reviewMemberRowRole,
                                reviewed && styles.reviewMemberCompletedText,
                              ]}
                            >
                              {reviewed
                                ? '작성 완료'
                                : member.role === 'LEADER'
                                  ? '스터디장'
                                  : '스터디원'}
                            </Text>
                          </View>
                          {reviewed ? (
                            <Ionicons
                              color={PRIMARY_COLOR}
                              name="checkmark-circle"
                              size={20}
                            />
                          ) : null}
                        </Pressable>
                      );
                    })}
                  </View>
                )}
              </>
            ) : (
              <>
                {selectedMember ? (
                  <View style={styles.reviewTargetCard}>
                    <View style={styles.reviewTargetAvatar}>
                      <Image
                        contentFit="contain"
                        source={studyMemberImageSource(selectedMember)}
                        style={styles.reviewMemberRowAvatarImage}
                      />
                    </View>
                    <View style={styles.reviewMemberRowText}>
                      <Text numberOfLines={1} style={styles.reviewTargetName}>
                        {selectedMember.nickname}
                      </Text>
                      <Text style={styles.reviewTargetHint}>
                        어울리는 태그와 좋아요로 마음을 전해 주세요.
                      </Text>
                    </View>
                  </View>
                ) : null}

                {REVIEW_TAG_CATEGORY_ORDER.map((category) => (
                  <View key={category} style={styles.reviewTagSection}>
                    <Text style={styles.reviewFieldLabel}>
                      {REVIEW_TAG_CATEGORY_LABELS[category]}
                    </Text>
                    <View style={styles.reviewTagGrid}>
                      {getReviewTagsByCategory(category).map((tag) => {
                        const active = selectedTags.includes(tag.code);
                        return (
                          <Pressable
                            accessibilityLabel={tag.label}
                            accessibilityRole="button"
                            accessibilityState={{ selected: active }}
                            key={tag.code}
                            onPress={() => toggleTag(tag.code)}
                            style={({ pressed }) => [
                              styles.reviewTagChip,
                              active && styles.reviewTagChipActive,
                              pressed && styles.pressed,
                            ]}
                          >
                            <Text style={styles.reviewTagChipEmoji}>{tag.emoji}</Text>
                            <Text
                              style={[
                                styles.reviewTagChipText,
                                active && styles.reviewTagChipTextActive,
                              ]}
                            >
                              {tag.label}
                            </Text>
                          </Pressable>
                        );
                      })}
                    </View>
                  </View>
                ))}

                <Text style={styles.reviewFieldLabel}>이 스터디원, 어땠나요?</Text>
                <Pressable
                  accessibilityLabel="이 스터디원과 또 함께하고 싶어요"
                  accessibilityRole="switch"
                  accessibilityState={{ checked: liked }}
                  onPress={() => setLiked((current) => !current)}
                  style={({ pressed }) => [
                    styles.reviewLikeToggle,
                    liked && styles.reviewLikeToggleActive,
                    pressed && styles.pressed,
                  ]}
                >
                  <Ionicons
                    color={liked ? SURFACE_COLOR : LIKE_ACCENT_COLOR}
                    name={liked ? 'heart' : 'heart-outline'}
                    size={20}
                  />
                  <Text
                    style={[
                      styles.reviewLikeToggleText,
                      liked && styles.reviewLikeToggleTextActive,
                    ]}
                  >
                    이 스터디원과 또 함께하고 싶어요
                  </Text>
                </Pressable>

                <View style={styles.reviewTextHeader}>
                  <Text style={styles.reviewFieldLabel}>한 줄 후기를 들려주세요 (선택)</Text>
                  <Text style={styles.reviewTextCount}>
                    {reviewText.length}/{REVIEW_MAX_LENGTH}
                  </Text>
                </View>
                <TextInput
                  accessibilityLabel="스터디원 후기"
                  maxLength={REVIEW_MAX_LENGTH}
                  multiline
                  onChangeText={setReviewText}
                  placeholder="시간 약속, 소통, 현장 참여 등 함께한 경험을 적어 주세요."
                  placeholderTextColor={PLACEHOLDER_COLOR}
                  style={styles.reviewTextInput}
                  textAlignVertical="top"
                  value={reviewText}
                />

                <View style={styles.reviewApiNotice}>
                  <Ionicons color={MUTED_TEXT_COLOR} name="information-circle-outline" size={16} />
                  <Text style={styles.reviewApiNoticeText}>
                    태그 또는 좋아요를 하나 이상 남겨 주세요. 리뷰는 익명으로 한 번만 등록되며, 등록
                    후 수정하거나 다시 작성할 수 없어요.
                  </Text>
                </View>
              </>
            )}
          </ScrollView>

          {step === 'target' ? (
            <Pressable
              accessibilityLabel="선택한 스터디원에게 리뷰 남기기"
              accessibilityRole="button"
              accessibilityState={{ disabled: !canProceedToReview }}
              disabled={!canProceedToReview}
              onPress={goToReviewStep}
              style={({ pressed }) => [
                styles.reviewSubmitButton,
                !canProceedToReview && styles.reviewSubmitButtonDisabled,
                pressed && styles.pressed,
              ]}
            >
              <Text style={styles.reviewSubmitButtonText}>다음</Text>
              <Ionicons color={SURFACE_COLOR} name="chevron-forward" size={18} />
            </Pressable>
          ) : (
            <Pressable
              accessibilityLabel="스터디원 리뷰 제출"
              accessibilityRole="button"
              accessibilityState={{ disabled: !isDraftComplete }}
              disabled={!isDraftComplete}
              onPress={submitReview}
              style={({ pressed }) => [
                styles.reviewSubmitButton,
                !isDraftComplete && styles.reviewSubmitButtonDisabled,
                pressed && styles.pressed,
              ]}
            >
              {reviewMutation.isPending ? (
                <ActivityIndicator color={SURFACE_COLOR} size="small" />
              ) : (
                <Ionicons color={SURFACE_COLOR} name="checkmark" size={18} />
              )}
              <Text style={styles.reviewSubmitButtonText}>
                {reviewMutation.isPending ? '등록 중...' : '리뷰 남기기'}
              </Text>
            </Pressable>
          )}
        </View>
      </KeyboardAvoidingView>
    </Modal>
  );
}

export default function MyScreen() {
  const router = useRouter();
  const queryClient = useQueryClient();
  const insets = useSafeAreaInsets();
  const { width } = useWindowDimensions();
  const carouselRef = useRef<ScrollView>(null);
  const isNavigatingRef = useRef(false);
  const [selectedSection, setSelectedSection] = useState<MyPageSectionKey>('studies');
  const [reviewStudy, setReviewStudy] = useState<ReviewSheetStudy | null>(null);
  const [carouselPageHeights, setCarouselPageHeights] = useState<
    Partial<Record<MyPageSectionKey, number>>
  >({});
  const sessionVersion = useAuthStore((state) => state.sessionVersion);
  const accessToken = useAuthStore((state) => state.accessToken);
  const setMemberProfile = useMemberStore((state) => state.setProfile);
  // TODO: 로그인 화면의 임시 로그인 제거 시 함께 삭제합니다.
  const isTemporaryDevelopmentSession = __DEV__ && accessToken === 'dummy-access-token';
  const myPageDataSource = isTemporaryDevelopmentSession ? 'mock' : 'api';
  const profileQuery = useQuery({
    queryKey: myPageQueryKeys.profile(sessionVersion),
    queryFn: getMyProfile,
    enabled: !isTemporaryDevelopmentSession,
  });
  const profileMemberId = profileQuery.data?.memberId;
  const memberReviewsQuery = useQuery({
    queryKey: myPageQueryKeys.reviewSummary(profileMemberId ?? -1, sessionVersion),
    queryFn: () => getMemberReviews(profileMemberId as number, undefined, 1),
    enabled:
      !isTemporaryDevelopmentSession && typeof profileMemberId === 'number' && profileMemberId > 0,
  });
  const studiesQuery = useInfiniteQuery({
    queryKey: myPageQueryKeys.studies(sessionVersion, myPageDataSource),
    queryFn: ({ pageParam }) =>
      isTemporaryDevelopmentSession
        ? getDevelopmentMyStudies(pageParam, 20)
        : getMyStudies(pageParam, 20),
    enabled: true,
    initialPageParam: 0,
    getNextPageParam: (lastPage) =>
      lastPage.page + 1 < lastPage.totalPages ? lastPage.page + 1 : undefined,
  });
  const reportsQuery = useInfiniteQuery({
    queryKey: myPageQueryKeys.reports(sessionVersion, myPageDataSource),
    queryFn: ({ pageParam }) =>
      isTemporaryDevelopmentSession
        ? getDevelopmentMyReports(pageParam, 20)
        : getMyReports(pageParam, 20),
    enabled: true,
    initialPageParam: 0,
    getNextPageParam: (lastPage) =>
      lastPage.page + 1 < lastPage.totalPages ? lastPage.page + 1 : undefined,
  });
  const followingsQuery = useInfiniteQuery({
    queryKey: myPageQueryKeys.followings(sessionVersion, myPageDataSource),
    queryFn: ({ pageParam }) =>
      isTemporaryDevelopmentSession
        ? getDevelopmentMyFollowings(pageParam, 20)
        : getMyFollowings(pageParam, 20),
    enabled: true,
    initialPageParam: undefined as number | undefined,
    getNextPageParam: (lastPage) =>
      lastPage.hasNext ? (lastPage.nextCursor ?? undefined) : undefined,
  });
  const unfollowMutation = useMutation({
    mutationFn: (following: MyFollowing) => unfollowMember(following.memberId),
    onSuccess: async () => {
      await Promise.all([
        queryClient.invalidateQueries({ queryKey: ['member', 'me'] }),
        queryClient.invalidateQueries({ queryKey: ['member', 'public-profile'] }),
      ]);
    },
    onError: (error) => {
      appAlert(
        '팔로우 해제 실패',
        error instanceof MyPageApiError
          ? error.message
          : '팔로우 상태를 변경하지 못했습니다. 잠시 후 다시 시도해 주세요.',
      );
    },
  });
  const pageWidth = Math.max(width - SCREEN_HORIZONTAL_PADDING * 2, 0);
  const profile = profileQuery.data;
  const reviewSummary = isTemporaryDevelopmentSession
    ? {
        topTags: [
          { code: 'PUNCTUAL', label: '시간 약속을 잘 지켜요', emoji: '⏰', category: 'PERSON', count: 8 },
          { code: 'THOROUGH', label: '꼼꼼하게 살펴봐요', emoji: '🔍', category: 'VISIT', count: 6 },
        ] as ReviewTagSummary[],
        likeReceivedCount: 11,
        reviewCount: 12,
      }
    : (memberReviewsQuery.data?.summary ??
      profile?.reviewSummary ?? {
        topTags: [] as ReviewTagSummary[],
        likeReceivedCount: 0,
        reviewCount: 0,
      });
  const studies = studiesQuery.data?.pages.flatMap((page) => page.content) ?? [];
  const reports = reportsQuery.data?.pages.flatMap((page) => page.content) ?? [];
  const followings = followingsQuery.data?.pages.flatMap((page) => page.content) ?? [];
  const fetchNextStudiesPage = studiesQuery.fetchNextPage;
  const hasNextStudiesPage = studiesQuery.hasNextPage;
  const isFetchingNextStudiesPage = studiesQuery.isFetchingNextPage;
  const fetchNextReportsPage = reportsQuery.fetchNextPage;
  const hasNextReportsPage = reportsQuery.hasNextPage;
  const isFetchingNextReportsPage = reportsQuery.isFetchingNextPage;
  const fetchNextFollowingsPage = followingsQuery.fetchNextPage;
  const hasNextFollowingsPage = followingsQuery.hasNextPage;
  const isFetchingNextFollowingsPage = followingsQuery.isFetchingNextPage;
  const sections: MyPageSection[] = MY_PAGE_SECTION_LABELS.map((section) => ({
    ...section,
    count: isTemporaryDevelopmentSession
      ? DEVELOPMENT_MY_PAGE_COUNTS[section.key]
      : section.key === 'studies'
        ? (profile?.summary.studyCount ?? 0)
        : section.key === 'reports'
          ? (profile?.summary.reportCount ?? 0)
          : (profile?.summary.followingCount ?? 0),
  }));
  // 원격 프로필 이미지는 Presigned 서명 쿼리가 매번 바뀌므로 쿼리 뗀 경로를 cacheKey로 고정.
  const profileImageSource: ImageProps['source'] = profile?.profileImageUrl
    ? { uri: profile.profileImageUrl, cacheKey: profile.profileImageUrl.split('?')[0] }
    : profile
      ? CHARACTER_IMAGES[profile.selectedCharacterId]
      : PROFILE_IMAGE;

  useEffect(() => {
    if (!profile) return;

    setMemberProfile({
      selectedCharacterId: profile.selectedCharacterId,
      ageGroupPublicAgreed: profile.ageGroupPublicAgreed,
    });
  }, [profile, setMemberProfile]);

  // 프로필 집계 + 현재 섹션 목록을 다시 불러온다. studies 섹션은 스터디 가입 승인이
  // 다른 회원의 기기에서 일어나 이 기기 QueryClient를 무효화할 수 없으므로, 20초
  // freshness 정책을 건너뛰고 즉시 재조회해 생성·승인 결과를 바로 반영한다.
  const refreshSelectedMyPageData = useCallback(() => {
    if (isTemporaryDevelopmentSession) return;

    const now = Date.now();
    const selectedQueryKey =
      selectedSection === 'studies'
        ? myPageQueryKeys.studies(sessionVersion, myPageDataSource)
        : selectedSection === 'reports'
          ? myPageQueryKeys.reports(sessionVersion, myPageDataSource)
          : myPageQueryKeys.followings(sessionVersion, myPageDataSource);

    if (selectedSection === 'studies') {
      void Promise.all([
        queryClient.refetchQueries({
          queryKey: myPageQueryKeys.profile(sessionVersion),
          exact: true,
          type: 'active',
        }),
        queryClient.refetchQueries({
          queryKey: selectedQueryKey,
          exact: true,
          type: 'active',
        }),
      ]);
    } else {
      refetchOnFocusIfStale(queryClient, myPageQueryKeys.profile(sessionVersion), now);
      refetchOnFocusIfStale(queryClient, selectedQueryKey, now);
    }

    if (typeof profileMemberId === 'number') {
      refetchOnFocusIfStale(
        queryClient,
        myPageQueryKeys.reviewSummary(profileMemberId, sessionVersion),
        now,
      );
    }
  }, [
    isTemporaryDevelopmentSession,
    myPageDataSource,
    profileMemberId,
    queryClient,
    selectedSection,
    sessionVersion,
  ]);

  useFocusEffect(
    useCallback(() => {
      isNavigatingRef.current = false;
      refreshSelectedMyPageData();
    }, [refreshSelectedMyPageData]),
  );

  // 앱이 백그라운드 → 포그라운드로 돌아올 때도(예: 승인을 기다리다 앱을 다시 열 때)
  // useFocusEffect가 다시 실행되지 않으므로, 여기서 한 번 더 재조회해 바로 반영한다.
  useEffect(() => {
    const subscription = AppState.addEventListener('change', (nextState) => {
      if (nextState === 'active') refreshSelectedMyPageData();
    });
    return () => subscription.remove();
  }, [refreshSelectedMyPageData]);

  const navigateOnce = useCallback(
    (href: Href) => {
      if (isNavigatingRef.current) return;

      isNavigatingRef.current = true;
      router.push(href);
    },
    [router],
  );

  const selectSection = useCallback(
    (section: MyPageSectionKey) => {
      const pageIndex = MY_PAGE_SECTION_LABELS.findIndex((item) => item.key === section);

      setSelectedSection(section);
      carouselRef.current?.scrollTo({ x: pageIndex * pageWidth, animated: true });
    },
    [pageWidth],
  );

  const handleSwipeEnd = useCallback(
    (event: NativeSyntheticEvent<NativeScrollEvent>) => {
      if (pageWidth === 0) return;

      const pageIndex = Math.round(event.nativeEvent.contentOffset.x / pageWidth);
      const section = MY_PAGE_SECTION_LABELS[pageIndex];

      if (section) setSelectedSection(section.key);
    },
    [pageWidth],
  );

  const handleCarouselLayout = useCallback(() => {
    const pageIndex = MY_PAGE_SECTION_LABELS.findIndex((item) => item.key === selectedSection);
    carouselRef.current?.scrollTo({ x: pageIndex * pageWidth, animated: false });
  }, [pageWidth, selectedSection]);

  const handleCarouselPageLayout = useCallback(
    (section: MyPageSectionKey, event: LayoutChangeEvent) => {
      const nextHeight = Math.ceil(event.nativeEvent.layout.height);

      setCarouselPageHeights((current) =>
        current[section] === nextHeight ? current : { ...current, [section]: nextHeight },
      );
    },
    [],
  );

  const handlePageScroll = useCallback(
    (event: NativeSyntheticEvent<NativeScrollEvent>) => {
      const { contentOffset, contentSize, layoutMeasurement } = event.nativeEvent;
      const remainingDistance = contentSize.height - (contentOffset.y + layoutMeasurement.height);

      if (remainingDistance >= 220) return;

      if (selectedSection === 'studies' && hasNextStudiesPage && !isFetchingNextStudiesPage) {
        void fetchNextStudiesPage();
      }
      if (selectedSection === 'reports' && hasNextReportsPage && !isFetchingNextReportsPage) {
        void fetchNextReportsPage();
      }
      if (
        selectedSection === 'following' &&
        hasNextFollowingsPage &&
        !isFetchingNextFollowingsPage
      ) {
        void fetchNextFollowingsPage();
      }
    },
    [
      fetchNextFollowingsPage,
      fetchNextReportsPage,
      fetchNextStudiesPage,
      hasNextFollowingsPage,
      hasNextReportsPage,
      hasNextStudiesPage,
      isFetchingNextFollowingsPage,
      isFetchingNextReportsPage,
      isFetchingNextStudiesPage,
      selectedSection,
    ],
  );

  const openMember = useCallback(
    (memberId: number) => {
      navigateOnce({
        pathname: '/(app)/member/[memberId]',
        params: { memberId: memberId.toString() },
      });
    },
    [navigateOnce],
  );

  const openMessage = useCallback(
    (following: MyFollowing) => {
      navigateOnce({
        pathname: '/(app)/message/[memberId]',
        params: {
          memberId: following.memberId.toString(),
          nickname: following.nickname,
          profileImageUrl: following.profileImageUrl ?? '',
          selectedCharacterId: following.selectedCharacterId,
        },
      });
    },
    [navigateOnce],
  );

  const confirmUnfollow = useCallback(
    (following: MyFollowing) => {
      appAlert('팔로우 해제', `${following.nickname}님 팔로우를 해제할까요?`, [
        { text: '취소', style: 'cancel' },
        {
          text: '해제',
          style: 'destructive',
          onPress: () => unfollowMutation.mutate(following),
        },
      ]);
    },
    [unfollowMutation],
  );

  return (
    <View style={styles.screen}>
      <StatusBar style="dark" />
      <ScrollView
        contentContainerStyle={[
          styles.content,
          {
            paddingTop: 0,
            paddingBottom: Math.max(insets.bottom, 18) + 118,
          },
        ]}
        onScroll={handlePageScroll}
        scrollEventThrottle={100}
        showsVerticalScrollIndicator={false}
      >
        <View
          style={[
            styles.passportSection,
            {
              minHeight: 190 + insets.top + 10,
              paddingTop: insets.top + 10,
            },
          ]}
        >
          <View style={styles.screenHeader}>
            <GlassIconButton
              accessibilityLabel="설정 열기"
              onPress={() => navigateOnce('/(app)/settings')}
            >
              <Ionicons
                color={TEXT_COLOR}
                name="settings-outline"
                size={GLASS_ICON_BUTTON_ICON_SIZE}
              />
            </GlassIconButton>
          </View>

          <View style={styles.profileCard}>
            <View style={styles.profileMainRow}>
              <View style={styles.profileAvatar}>
                {profileQuery.isPending && !isTemporaryDevelopmentSession ? (
                  <ActivityIndicator color={PRIMARY_COLOR} size="small" />
                ) : (
                  <Image
                    contentFit="contain"
                    source={profileImageSource}
                    style={styles.profileImage}
                  />
                )}
              </View>
              <View style={styles.profileDescription}>
                <Text numberOfLines={1} style={styles.profileName}>
                  {isTemporaryDevelopmentSession
                    ? '현장토끼'
                    : profileQuery.isError
                      ? '프로필을 불러오지 못했어요'
                      : profile
                        ? profile.nickname
                        : '프로필 불러오는 중'}
                </Text>
                <View style={styles.profileMetaRow}>
                  <Text numberOfLines={1} style={styles.profileMeta}>
                    {isTemporaryDevelopmentSession
                      ? '생활 편의와 보행을 살펴봐요'
                      : profile
                        ? profileMeta(profile.ageGroup, profile.fieldVisitCompletedCount)
                        : profileQuery.isError
                          ? '네트워크 연결을 확인해 주세요.'
                          : '잠시만 기다려 주세요.'}
                  </Text>
                  {reviewSummary.likeReceivedCount > 0 ? (
                    <View
                      accessibilityLabel={`좋아요 ${reviewSummary.likeReceivedCount}`}
                      style={styles.profileMetaLike}
                    >
                      <Ionicons color={LIKE_ACCENT_COLOR} name="heart" size={12} />
                      <Text style={styles.profileMetaLikeText}>
                        {reviewSummary.likeReceivedCount}
                      </Text>
                    </View>
                  ) : null}
                </View>
                <ProfileReputation topTags={reviewSummary.topTags} />
              </View>
              {profileQuery.isError && !isTemporaryDevelopmentSession ? (
                <Pressable
                  accessibilityLabel="프로필 다시 불러오기"
                  accessibilityRole="button"
                  onPress={() => void profileQuery.refetch()}
                  style={({ pressed }) => [styles.editButton, pressed && styles.pressed]}
                >
                  <Text style={styles.editButtonText}>재시도</Text>
                </Pressable>
              ) : (
                <Pressable
                  accessibilityLabel="프로필 편집"
                  accessibilityRole="button"
                  disabled={!profile && !isTemporaryDevelopmentSession}
                  onPress={() => navigateOnce('/(app)/profile-edit')}
                  style={({ pressed }) => [styles.editButton, pressed && styles.pressed]}
                >
                  <Text style={styles.editButtonText}>편집</Text>
                </Pressable>
              )}
            </View>
          </View>
        </View>

        <CalendarCard
          enabled={!isTemporaryDevelopmentSession}
          onOpenStudy={(studyId) =>
            navigateOnce({ pathname: '/(app)/study/[id]', params: { id: String(studyId) } })
          }
          sessionVersion={sessionVersion}
        />
        <SectionSelector
          onSelect={selectSection}
          sections={sections}
          selectedSection={selectedSection}
        />

        <ScrollView
          accessibilityLabel="마이페이지 콘텐츠"
          horizontal
          nestedScrollEnabled
          onLayout={handleCarouselLayout}
          onMomentumScrollEnd={handleSwipeEnd}
          pagingEnabled
          ref={carouselRef}
          showsHorizontalScrollIndicator={false}
          style={[
            styles.carousel,
            carouselPageHeights[selectedSection] !== undefined && {
              height: carouselPageHeights[selectedSection],
            },
          ]}
        >
          <View
            onLayout={(event) => handleCarouselPageLayout('studies', event)}
            style={[styles.carouselPage, { width: pageWidth }]}
          >
            <StudyList
              isError={studiesQuery.isError}
              isFetchingNextPage={studiesQuery.isFetchingNextPage}
              isPending={studiesQuery.isPending}
              onOpen={(studyId) =>
                navigateOnce({ pathname: '/(app)/study/[id]', params: { id: String(studyId) } })
              }
              onReview={setReviewStudy}
              onRetry={() => void studiesQuery.refetch()}
              studies={studies}
            />
          </View>
          <View
            onLayout={(event) => handleCarouselPageLayout('reports', event)}
            style={[styles.carouselPage, { width: pageWidth }]}
          >
            <ReportList
              isError={reportsQuery.isError}
              isFetchingNextPage={reportsQuery.isFetchingNextPage}
              isPending={reportsQuery.isPending}
              onOpenReport={(selectedReportId) => {
                if (isTemporaryDevelopmentSession) {
                  appAlert(
                    '샘플 리포트예요',
                    '실제 완료 리포트에서 상세 화면을 확인할 수 있어요.',
                  );
                  return;
                }
                navigateOnce({
                  pathname: '/(app)/report/[reportId]',
                  params: { reportId: String(selectedReportId) },
                });
              }}
              onRetry={() => void reportsQuery.refetch()}
              reports={reports}
            />
          </View>
          <View
            onLayout={(event) => handleCarouselPageLayout('following', event)}
            style={[styles.carouselPage, { width: pageWidth }]}
          >
            <FollowingList
              followings={followings}
              isError={followingsQuery.isError}
              isFetchingNextPage={followingsQuery.isFetchingNextPage}
              isPending={followingsQuery.isPending}
              onMessage={openMessage}
              onOpen={openMember}
              onRetry={() => void followingsQuery.refetch()}
              onUnfollow={confirmUnfollow}
              updatingMemberId={
                unfollowMutation.isPending ? (unfollowMutation.variables?.memberId ?? null) : null
              }
            />
          </View>
        </ScrollView>
      </ScrollView>
      {reviewStudy ? (
        <ReviewSheet
          isPreview={isTemporaryDevelopmentSession}
          key={reviewStudy.studyId}
          onClose={() => setReviewStudy(null)}
          study={reviewStudy}
          viewerMemberId={profile?.memberId}
        />
      ) : null}
    </View>
  );
}

const styles = StyleSheet.create({
  screen: {
    flex: 1,
    backgroundColor: SCREEN_BACKGROUND_COLOR,
  },
  content: {
    paddingHorizontal: SCREEN_HORIZONTAL_PADDING,
  },
  passportSection: {
    position: 'relative',
    minHeight: 190,
    marginHorizontal: -SCREEN_HORIZONTAL_PADDING,
    paddingHorizontal: SCREEN_HORIZONTAL_PADDING,
    // 달력 카드가 marginTop -18로 겹쳐 올라오므로, 태그 2개일 때도 겹치지 않도록
    // 하단 여백을 18보다 크게 둬 실제 간격을 확보한다.
    paddingBottom: 28,
    backgroundColor: '#F2F4F3',
    overflow: 'hidden',
  },
  screenHeader: {
    minHeight: 52,
    marginBottom: 3,
    flexDirection: 'row',
    alignItems: 'center',
    justifyContent: 'flex-end',
  },
  profileCard: {
    position: 'relative',
    minHeight: 105,
    paddingHorizontal: 17,
    paddingTop: 7,
  },
  profileMainRow: {
    minHeight: 82,
    flexDirection: 'row',
    alignItems: 'center',
  },
  profileAvatar: {
    width: 72,
    height: 72,
    marginRight: 13,
    borderWidth: 1,
    borderColor: 'rgba(19, 178, 110, 0.16)',
    borderRadius: 36,
    alignItems: 'center',
    justifyContent: 'center',
    backgroundColor: 'rgba(255, 255, 255, 0.84)',
    overflow: 'hidden',
  },
  profileImage: {
    width: 64,
    height: 64,
  },
  profileDescription: {
    flex: 1,
    minWidth: 0,
  },
  profileName: {
    marginTop: 3,
    color: PRIMARY_COLOR,
    fontSize: 24,
    fontWeight: '900',
    letterSpacing: -0.8,
  },
  profileJourney: {
    color: MUTED_TEXT_COLOR,
    fontSize: 10,
    fontWeight: '800',
  },
  profileMetaRow: {
    marginTop: 3,
    flexDirection: 'row',
    alignItems: 'center',
    gap: 6,
  },
  profileMeta: {
    flexShrink: 1,
    color: LABEL_COLOR,
    fontSize: 11,
    fontWeight: '700',
    letterSpacing: -0.2,
  },
  profileMetaLike: {
    flexDirection: 'row',
    alignItems: 'center',
    gap: 2,
  },
  profileMetaLikeText: {
    color: LIKE_ACCENT_COLOR,
    fontSize: 11,
    fontWeight: '900',
  },
  editButton: {
    minWidth: 48,
    minHeight: 40,
    marginLeft: 8,
    paddingHorizontal: 12,
    borderWidth: 1,
    borderColor: 'rgba(16, 39, 30, 0.08)',
    borderRadius: 20,
    alignItems: 'center',
    justifyContent: 'center',
    backgroundColor: 'rgba(255, 255, 255, 0.86)',
  },
  editButtonText: {
    color: TEXT_COLOR,
    fontSize: 13,
    fontWeight: '800',
  },
  reputationLine: {
    minHeight: 18,
    marginTop: 6,
    flexDirection: 'row',
    flexWrap: 'wrap',
    alignItems: 'center',
    gap: 5,
  },
  reputationTag: {
    maxWidth: '100%',
    paddingHorizontal: 9,
    paddingVertical: 3.5,
    borderRadius: 999,
    backgroundColor: BUTTON_BACKGROUND_COLOR,
    borderWidth: 1,
    borderColor: 'rgba(19, 178, 110, 0.28)',
    flexDirection: 'row',
    alignItems: 'center',
    gap: 3,
  },
  reputationTagEmoji: {
    fontSize: 11,
  },
  reputationTagLabel: {
    minWidth: 0,
    flexShrink: 1,
    color: DARK_GREEN_COLOR,
    fontSize: 11,
    fontWeight: '900',
  },
  reputationHint: {
    marginLeft: 1,
    color: MUTED_TEXT_COLOR,
    fontSize: 9,
    fontWeight: '700',
  },
  calendarCard: {
    zIndex: 2,
    marginTop: -18,
    marginHorizontal: -3,
    paddingHorizontal: 18,
    paddingTop: 14,
    paddingBottom: 12,
    borderWidth: 1,
    borderColor: 'rgba(255, 255, 255, 0.96)',
    borderRadius: 28,
    backgroundColor: SURFACE_COLOR,
    shadowColor: DARK_GREEN_COLOR,
    shadowOffset: { width: 0, height: 14 },
    shadowOpacity: 0.09,
    shadowRadius: 24,
    elevation: 4,
  },
  scheduleHeader: {
    minHeight: 42,
    flexDirection: 'row',
    alignItems: 'center',
    justifyContent: 'space-between',
  },
  scheduleHeaderCopy: {
    minWidth: 0,
    flex: 1,
  },
  scheduleTitle: {
    color: TEXT_COLOR,
    fontSize: 19,
    fontWeight: '900',
    letterSpacing: -0.6,
    textAlign: 'center',
  },
  scheduleHeaderActions: {
    position: 'absolute',
    right: 0,
    top: 0,
    bottom: 0,
    flexDirection: 'row',
    alignItems: 'center',
    gap: 2,
  },
  scheduleDateBadge: {
    width: 46,
    height: 42,
    alignItems: 'center',
    justifyContent: 'center',
  },
  scheduleDateNumber: {
    color: TEXT_COLOR,
    fontSize: 22,
    fontWeight: '900',
    lineHeight: 24,
  },
  scheduleDateWeekday: {
    marginTop: 2,
    color: MUTED_TEXT_COLOR,
    fontSize: 9,
    fontWeight: '800',
  },
  scheduleVisitFocus: {
    minHeight: 67,
    marginTop: 7,
    paddingVertical: 9,
    borderTopWidth: 1,
    borderTopColor: BORDER_COLOR,
    borderBottomWidth: 1,
    borderBottomColor: BORDER_COLOR,
    flexDirection: 'row',
    alignItems: 'center',
  },
  scheduleVisitCopy: {
    minWidth: 0,
    flex: 1,
  },
  scheduleVisitTime: {
    color: DARK_GREEN_COLOR,
    fontSize: 10,
    fontWeight: '900',
  },
  scheduleVisitTitle: {
    marginTop: 3,
    color: TEXT_COLOR,
    fontSize: 14,
    fontWeight: '900',
  },
  scheduleVisitApartment: {
    marginTop: 3,
    color: MUTED_TEXT_COLOR,
    fontSize: 10,
    fontWeight: '700',
  },
  scheduleVisitArrow: {
    width: 40,
    height: 40,
    marginLeft: 10,
    borderWidth: 1,
    borderColor: '#BFE8D1',
    borderRadius: 14,
    alignItems: 'center',
    justifyContent: 'center',
    backgroundColor: BUTTON_BACKGROUND_COLOR,
  },
  scheduleVisitState: {
    minHeight: 64,
    marginTop: 7,
    paddingHorizontal: 4,
    borderTopWidth: 1,
    borderTopColor: BORDER_COLOR,
    borderBottomWidth: 1,
    borderBottomColor: BORDER_COLOR,
    flexDirection: 'row',
    alignItems: 'center',
    gap: 9,
  },
  scheduleVisitEmptyTitle: {
    color: TEXT_COLOR,
    fontSize: 12,
    fontWeight: '900',
  },
  scheduleVisitStateText: {
    marginTop: 3,
    color: MUTED_TEXT_COLOR,
    fontSize: 10,
    fontWeight: '700',
  },
  scheduleDateRail: {
    minHeight: 51,
    flexDirection: 'row',
    alignItems: 'center',
  },
  scheduleRailMonth: {
    width: 35,
    color: MUTED_TEXT_COLOR,
    fontSize: 10,
    fontWeight: '800',
    textAlign: 'center',
  },
  scheduleRailMonthDisabled: {
    opacity: 0.28,
  },
  scheduleRailScroll: {
    minWidth: 0,
    flex: 1,
  },
  scheduleRailDays: {
    flexGrow: 1,
    paddingHorizontal: 5,
    alignItems: 'center',
    justifyContent: 'center',
    gap: 8,
  },
  scheduleRailDay: {
    width: SCHEDULE_RAIL_DAY_SIZE,
    height: SCHEDULE_RAIL_DAY_SIZE,
    borderWidth: 1,
    borderColor: 'transparent',
    borderRadius: 14,
    alignItems: 'center',
    justifyContent: 'center',
  },
  scheduleRailDayActive: {
    borderColor: '#BFE8D1',
    backgroundColor: BUTTON_BACKGROUND_COLOR,
  },
  scheduleRailDayNumber: {
    color: LABEL_COLOR,
    fontSize: 12,
    fontWeight: '900',
  },
  scheduleRailWeekday: {
    marginTop: 2,
    color: MUTED_TEXT_COLOR,
    fontSize: 8,
    fontWeight: '700',
  },
  scheduleRailDayTextActive: {
    color: DARK_GREEN_COLOR,
  },
  scheduleRailEmpty: {
    color: MUTED_TEXT_COLOR,
    fontSize: 10,
    fontWeight: '700',
  },
  calendarHeader: {
    minHeight: 34,
    flexDirection: 'row',
    alignItems: 'center',
    justifyContent: 'space-between',
    gap: 8,
  },
  calendarMonthNavigation: {
    minWidth: 0,
    flexShrink: 1,
    flexDirection: 'row',
    alignItems: 'center',
  },
  calendarArrowButton: {
    width: 28,
    height: 34,
    borderRadius: 12,
    alignItems: 'center',
    justifyContent: 'center',
  },
  calendarArrowButtonDisabled: {
    opacity: 0.28,
  },
  calendarTitleArea: {
    minWidth: 102,
    minHeight: 34,
    paddingHorizontal: 4,
    alignItems: 'center',
    justifyContent: 'center',
  },
  calendarTitle: {
    color: TEXT_COLOR,
    fontSize: 16,
    fontWeight: '900',
    letterSpacing: -0.5,
  },
  calendarHeaderActions: {
    flexShrink: 0,
    flexDirection: 'row',
    alignItems: 'center',
    gap: 2,
  },
  visitCountChip: {
    flexShrink: 0,
    paddingHorizontal: 10,
    paddingVertical: 6,
    borderRadius: 16,
    backgroundColor: SOFT_GREEN_COLOR,
  },
  visitCountChipError: {
    backgroundColor: ERROR_BACKGROUND_COLOR,
  },
  visitCountText: {
    color: DARK_GREEN_COLOR,
    fontSize: 11,
    fontWeight: '800',
  },
  visitCountTextError: {
    color: ERROR_COLOR,
  },
  calendarCollapseButton: {
    width: 28,
    height: 34,
    borderRadius: 12,
    alignItems: 'center',
    justifyContent: 'center',
  },
  calendarBody: {
    marginTop: 10,
    paddingTop: 14,
    borderTopWidth: 1,
    borderTopColor: BORDER_COLOR,
  },
  calendarExpandedHeader: {
    minHeight: 36,
    marginBottom: 8,
    flexDirection: 'row',
    alignItems: 'center',
    justifyContent: 'space-between',
  },
  weekdayRow: {
    flexDirection: 'row',
    marginBottom: 3,
  },
  calendarColumn: {
    width: '14.2857%',
    alignItems: 'center',
  },
  weekdayText: {
    color: MUTED_TEXT_COLOR,
    fontSize: 12,
    fontWeight: '800',
  },
  sundayText: {
    color: ERROR_COLOR,
  },
  saturdayText: {
    color: CALENDAR_SATURDAY_COLOR,
  },
  calendarGrid: {
    flexDirection: 'row',
    flexWrap: 'wrap',
  },
  dayCell: {
    width: '14.2857%',
    height: 46,
    alignItems: 'center',
    justifyContent: 'center',
  },
  dayButton: {
    width: 38,
    height: 38,
    borderRadius: 13,
    alignItems: 'center',
    justifyContent: 'center',
  },
  eventDayButton: {
    backgroundColor: SOFT_GREEN_COLOR,
  },
  completedEventDayButton: {
    backgroundColor: SOFT_BACKGROUND_COLOR,
  },
  todayDayButton: {
    borderWidth: 1.5,
    borderColor: PRIMARY_COLOR,
  },
  selectedDayButton: {
    borderColor: DARK_GREEN_COLOR,
    backgroundColor: DARK_GREEN_COLOR,
  },
  dayButtonPressed: {
    opacity: 0.72,
  },
  dayText: {
    color: TEXT_COLOR,
    fontSize: 14,
    fontWeight: '800',
  },
  todayDayText: {
    color: PRIMARY_COLOR,
    fontWeight: '900',
  },
  selectedDayText: {
    color: BUTTON_BACKGROUND_COLOR,
  },
  eventDot: {
    position: 'absolute',
    bottom: 5,
    width: 3.5,
    height: 3.5,
    borderRadius: 2,
    backgroundColor: PRIMARY_COLOR,
  },
  completedEventDot: {
    backgroundColor: MUTED_TEXT_COLOR,
  },
  eventCountBadge: {
    position: 'absolute',
    top: -2,
    right: -2,
    minWidth: 16,
    height: 16,
    paddingHorizontal: 4,
    borderRadius: 8,
    alignItems: 'center',
    justifyContent: 'center',
    backgroundColor: PRIMARY_COLOR,
  },
  eventCountBadgeSelected: {
    backgroundColor: BUTTON_BACKGROUND_COLOR,
  },
  eventCountBadgeText: {
    color: SURFACE_COLOR,
    fontSize: 9,
    fontWeight: '900',
  },
  eventCountBadgeTextSelected: {
    color: DARK_GREEN_COLOR,
  },
  calendarSelectionHeader: {
    minHeight: 38,
    marginTop: 8,
    flexDirection: 'row',
    alignItems: 'center',
    justifyContent: 'space-between',
  },
  calendarSelectionText: {
    flex: 1,
    minWidth: 0,
    marginRight: 8,
    color: LABEL_COLOR,
    fontSize: 12,
    fontWeight: '800',
  },
  todayBadge: {
    paddingHorizontal: 11,
    paddingVertical: 7,
    borderRadius: 14,
    backgroundColor: SOFT_GREEN_COLOR,
  },
  todayBadgeText: {
    color: DARK_GREEN_COLOR,
    fontSize: 11,
    fontWeight: '900',
  },
  todayMoveButton: {
    paddingHorizontal: 12,
    paddingVertical: 7,
    borderRadius: 14,
    backgroundColor: BUTTON_BACKGROUND_COLOR,
  },
  todayMoveButtonText: {
    color: DARK_GREEN_COLOR,
    fontSize: 11,
    fontWeight: '900',
  },
  calendarStateRow: {
    minHeight: 44,
    paddingHorizontal: 12,
    paddingVertical: 9,
    borderRadius: 14,
    backgroundColor: SOFT_BACKGROUND_COLOR,
    flexDirection: 'row',
    alignItems: 'center',
    gap: 8,
  },
  calendarStateText: {
    minWidth: 0,
    flex: 1,
    color: MUTED_TEXT_COLOR,
    fontSize: 11,
    fontWeight: '700',
    lineHeight: 16,
  },
  calendarRetryButton: {
    flexShrink: 0,
    paddingHorizontal: 11,
    paddingVertical: 7,
    borderRadius: 13,
    backgroundColor: ERROR_BACKGROUND_COLOR,
  },
  calendarRetryText: {
    color: ERROR_COLOR,
    fontSize: 11,
    fontWeight: '900',
  },
  calendarVisitList: {
    gap: 8,
  },
  calendarVisitCard: {
    paddingHorizontal: 13,
    paddingVertical: 12,
    borderWidth: 1,
    borderColor: BORDER_COLOR,
    borderRadius: 15,
    backgroundColor: SOFT_BACKGROUND_COLOR,
  },
  calendarVisitHeader: {
    flexDirection: 'row',
    alignItems: 'center',
    justifyContent: 'space-between',
  },
  calendarVisitStatusChip: {
    paddingHorizontal: 9,
    paddingVertical: 4,
    borderRadius: 10,
    backgroundColor: SOFT_GREEN_COLOR,
  },
  calendarVisitStatusChipCompleted: {
    backgroundColor: SURFACE_COLOR,
  },
  calendarVisitStatusText: {
    color: DARK_GREEN_COLOR,
    fontSize: 10,
    fontWeight: '900',
  },
  calendarVisitStatusTextCompleted: {
    color: MUTED_TEXT_COLOR,
  },
  calendarVisitTime: {
    color: LABEL_COLOR,
    fontSize: 12,
    fontWeight: '900',
  },
  calendarVisitTitle: {
    marginTop: 8,
    color: TEXT_COLOR,
    fontSize: 14,
    fontWeight: '900',
    letterSpacing: -0.3,
  },
  calendarVisitApartmentRow: {
    marginTop: 5,
    minWidth: 0,
    flexDirection: 'row',
    alignItems: 'center',
    gap: 4,
  },
  calendarVisitApartment: {
    minWidth: 0,
    flex: 1,
    color: MUTED_TEXT_COLOR,
    fontSize: 11,
    fontWeight: '700',
  },
  sectionNavigation: {
    marginTop: 24,
  },
  sectionSelector: {
    borderBottomWidth: 1,
    borderBottomColor: BORDER_COLOR,
    flexDirection: 'row',
  },
  sectionTab: {
    position: 'relative',
    flex: 1,
    minHeight: 48,
    alignItems: 'center',
    justifyContent: 'center',
  },
  sectionTabContent: {
    flexDirection: 'row',
    alignItems: 'center',
    gap: 5,
    paddingBottom: 9,
  },
  sectionTabLabel: {
    color: MUTED_TEXT_COLOR,
    fontSize: 13,
    fontWeight: '800',
    letterSpacing: -0.3,
  },
  sectionTabLabelActive: {
    color: DARK_GREEN_COLOR,
    fontWeight: '900',
  },
  sectionCountBadge: {
    minWidth: 21,
    height: 21,
    paddingHorizontal: 6,
    borderRadius: 11,
    backgroundColor: SOFT_BACKGROUND_COLOR,
    alignItems: 'center',
    justifyContent: 'center',
  },
  sectionCountBadgeActive: {
    backgroundColor: BUTTON_BACKGROUND_COLOR,
  },
  sectionCountText: {
    color: MUTED_TEXT_COLOR,
    fontSize: 11,
    fontWeight: '900',
  },
  sectionCountTextActive: {
    color: PRIMARY_COLOR,
  },
  sectionIndicator: {
    position: 'absolute',
    right: 12,
    bottom: -1,
    left: 12,
    height: 3,
    borderRadius: 2,
    backgroundColor: PRIMARY_COLOR,
  },
  carousel: {
    width: '100%',
    marginTop: 14,
  },
  carouselPage: {
    alignSelf: 'flex-start',
  },
  list: {
    gap: 12,
  },
  studyFilterRow: {
    flexDirection: 'row',
    gap: 8,
    marginBottom: 2,
  },
  studyFilterChip: {
    paddingHorizontal: 14,
    paddingVertical: 8,
    borderWidth: 1,
    borderColor: BORDER_COLOR,
    borderRadius: 16,
    backgroundColor: SURFACE_COLOR,
  },
  studyFilterChipActive: {
    borderColor: PRIMARY_COLOR,
    backgroundColor: BUTTON_BACKGROUND_COLOR,
  },
  studyFilterChipText: {
    color: MUTED_TEXT_COLOR,
    fontSize: 12,
    fontWeight: '800',
    letterSpacing: -0.2,
  },
  studyFilterChipTextActive: {
    color: DARK_GREEN_COLOR,
    fontWeight: '900',
  },
  listCard: {
    borderWidth: 1,
    borderColor: BORDER_COLOR,
    borderRadius: 20,
    backgroundColor: SURFACE_COLOR,
  },
  emptyCard: {
    minHeight: 168,
    paddingHorizontal: 28,
    alignItems: 'center',
    justifyContent: 'center',
  },
  emptyIcon: {
    width: 46,
    height: 46,
    marginBottom: 13,
    borderRadius: 23,
    alignItems: 'center',
    justifyContent: 'center',
    backgroundColor: SOFT_GREEN_COLOR,
  },
  emptyTitle: {
    color: TEXT_COLOR,
    fontSize: 15,
    fontWeight: '900',
    textAlign: 'center',
  },
  emptyDescription: {
    marginTop: 7,
    color: MUTED_TEXT_COLOR,
    fontSize: 12,
    fontWeight: '600',
    lineHeight: 18,
    textAlign: 'center',
  },
  queryStateCard: {
    minHeight: 150,
    gap: 12,
    alignItems: 'center',
    justifyContent: 'center',
  },
  queryStateText: {
    color: MUTED_TEXT_COLOR,
    fontSize: 13,
    fontWeight: '700',
  },
  retryButton: {
    marginTop: 12,
    paddingHorizontal: 18,
    paddingVertical: 10,
    borderRadius: 18,
    backgroundColor: BUTTON_BACKGROUND_COLOR,
  },
  retryButtonText: {
    color: DARK_GREEN_COLOR,
    fontSize: 13,
    fontWeight: '900',
  },
  cardPressed: {
    transform: [{ scale: 0.99 }],
    opacity: 0.86,
  },
  studyCard: {
    minHeight: 98,
    overflow: 'hidden',
  },
  studyChipRow: {
    flexDirection: 'row',
    alignItems: 'center',
    gap: 6,
  },
  studyLeaderBadge: {
    flexDirection: 'row',
    alignItems: 'center',
    gap: 3,
    paddingHorizontal: 7,
    paddingVertical: 3,
    borderRadius: 999,
    backgroundColor: SOFT_GREEN_COLOR,
  },
  studyLeaderBadgeText: {
    fontSize: 10,
    fontWeight: '800',
    color: DARK_GREEN_COLOR,
  },
  studyOpenArea: {
    minHeight: 98,
    paddingLeft: 17,
    flexDirection: 'row',
    alignItems: 'center',
  },
  studyDescription: {
    flex: 1,
    minWidth: 0,
    paddingVertical: 15,
    paddingRight: 12,
    alignItems: 'flex-start',
  },
  statusChip: {
    alignSelf: 'flex-start',
    paddingHorizontal: 10,
    paddingVertical: 4,
    borderRadius: 12,
    backgroundColor: SOFT_GREEN_COLOR,
  },
  statusChipRed: {
    backgroundColor: ERROR_BACKGROUND_COLOR,
  },
  statusChipNeutral: {
    backgroundColor: SOFT_BACKGROUND_COLOR,
  },
  statusChipText: {
    color: DARK_GREEN_COLOR,
    fontSize: 11,
    fontWeight: '900',
  },
  statusChipTextRed: {
    color: ERROR_COLOR,
  },
  statusChipTextNeutral: {
    color: MUTED_TEXT_COLOR,
  },
  studyTitle: {
    marginTop: 7,
    color: TEXT_COLOR,
    fontSize: 16,
    fontWeight: '900',
    letterSpacing: -0.45,
  },
  studyApartment: {
    marginTop: 5,
    color: MUTED_TEXT_COLOR,
    fontSize: 12,
    fontWeight: '700',
  },
  nextVisitArea: {
    width: 102,
    minHeight: 64,
    borderLeftWidth: 1,
    borderLeftColor: BORDER_COLOR,
    alignItems: 'center',
    justifyContent: 'center',
  },
  nextVisitLabel: {
    color: PLACEHOLDER_COLOR,
    fontSize: 11,
    fontWeight: '800',
  },
  nextVisitDate: {
    marginTop: 5,
    color: TEXT_COLOR,
    fontSize: 16,
    fontWeight: '900',
    letterSpacing: -0.3,
  },
  studyReviewRow: {
    minHeight: 51,
    paddingHorizontal: 13,
    borderTopWidth: 1,
    borderTopColor: BORDER_COLOR,
    backgroundColor: '#FAFCFA',
    flexDirection: 'row',
    alignItems: 'center',
    justifyContent: 'space-between',
    gap: 8,
  },
  studyReviewGuide: {
    minWidth: 0,
    flex: 1,
    flexDirection: 'row',
    alignItems: 'center',
    gap: 5,
  },
  studyReviewGuideText: {
    minWidth: 0,
    flex: 1,
    color: LABEL_COLOR,
    fontSize: 10,
    fontWeight: '700',
  },
  studyReviewButton: {
    minHeight: 34,
    paddingHorizontal: 11,
    borderRadius: 17,
    backgroundColor: BUTTON_BACKGROUND_COLOR,
    flexDirection: 'row',
    alignItems: 'center',
    gap: 1,
  },
  studyReviewButtonText: {
    color: DARK_GREEN_COLOR,
    fontSize: 10,
    fontWeight: '900',
  },
  reportCard: {
    minHeight: 132,
    paddingHorizontal: 17,
    paddingVertical: 15,
    alignItems: 'flex-start',
  },
  reportHeader: {
    width: '100%',
    flexDirection: 'row',
    alignItems: 'center',
    justifyContent: 'space-between',
  },
  reportTitle: {
    marginTop: 7,
    color: TEXT_COLOR,
    fontSize: 16,
    fontWeight: '900',
    letterSpacing: -0.45,
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
  followingCard: {
    minHeight: 88,
    paddingLeft: 16,
    paddingRight: 12,
    flexDirection: 'row',
    alignItems: 'center',
  },
  followingMain: {
    minWidth: 0,
    flex: 1,
    paddingVertical: 14,
    flexDirection: 'row',
    alignItems: 'center',
  },
  followingAvatar: {
    width: 50,
    height: 50,
    marginRight: 13,
    borderRadius: 25,
    alignItems: 'center',
    justifyContent: 'center',
    overflow: 'hidden',
  },
  followingAvatarImage: {
    width: 43,
    height: 43,
  },
  followingDescription: {
    flex: 1,
    minWidth: 0,
  },
  followingName: {
    color: TEXT_COLOR,
    fontSize: 16,
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
  followingStatusButton: {
    minWidth: 72,
    height: 38,
    paddingHorizontal: 10,
    borderRadius: 19,
    backgroundColor: SOFT_GREEN_COLOR,
    flexDirection: 'row',
    alignItems: 'center',
    justifyContent: 'center',
    gap: 4,
  },
  followingStatusText: {
    color: DARK_GREEN_COLOR,
    fontSize: 12,
    fontWeight: '900',
  },
  messageButton: {
    width: 38,
    height: 38,
    borderRadius: 19,
    alignItems: 'center',
    justifyContent: 'center',
    backgroundColor: BUTTON_BACKGROUND_COLOR,
  },
  messageButtonPressed: {
    backgroundColor: BUTTON_BACKGROUND_COLOR,
  },
  reviewModalRoot: {
    flex: 1,
    justifyContent: 'flex-end',
  },
  reviewScrim: {
    position: 'absolute',
    top: 0,
    right: 0,
    bottom: 0,
    left: 0,
    backgroundColor: MODAL_SCRIM_COLOR,
  },
  reviewSheet: {
    maxHeight: '91%',
    paddingTop: 9,
    paddingBottom: 24,
    borderTopLeftRadius: 30,
    borderTopRightRadius: 30,
    backgroundColor: SURFACE_COLOR,
    overflow: 'hidden',
  },
  reviewHandle: {
    width: 42,
    height: 4,
    alignSelf: 'center',
    borderRadius: 2,
    backgroundColor: BORDER_COLOR,
  },
  reviewHeader: {
    minHeight: 91,
    paddingHorizontal: 20,
    paddingTop: 17,
    paddingBottom: 13,
    borderBottomWidth: 1,
    borderBottomColor: BORDER_COLOR,
    flexDirection: 'row',
    alignItems: 'center',
  },
  reviewBackButton: {
    width: 40,
    height: 40,
    marginRight: 8,
    borderRadius: 20,
    alignItems: 'center',
    justifyContent: 'center',
    backgroundColor: SOFT_BACKGROUND_COLOR,
  },
  reviewHeaderCopy: {
    minWidth: 0,
    flex: 1,
  },
  reviewEyebrow: {
    color: PRIMARY_COLOR,
    fontSize: 11,
    fontWeight: '900',
  },
  reviewTitle: {
    marginTop: 4,
    color: TEXT_COLOR,
    fontSize: 19,
    fontWeight: '900',
    letterSpacing: -0.5,
  },
  reviewApartment: {
    marginTop: 3,
    color: MUTED_TEXT_COLOR,
    fontSize: 11,
    fontWeight: '700',
  },
  reviewCloseButton: {
    width: 40,
    height: 40,
    marginLeft: 12,
    borderRadius: 20,
    alignItems: 'center',
    justifyContent: 'center',
    backgroundColor: SOFT_BACKGROUND_COLOR,
  },
  reviewSheetContent: {
    paddingHorizontal: 20,
    paddingTop: 17,
    paddingBottom: 16,
  },
  reviewInfoCard: {
    minHeight: 58,
    paddingHorizontal: 13,
    paddingVertical: 11,
    borderRadius: 17,
    backgroundColor: SOFT_GREEN_COLOR,
    flexDirection: 'row',
    alignItems: 'center',
    gap: 9,
  },
  reviewInfoText: {
    minWidth: 0,
    flex: 1,
    color: DARK_GREEN_COLOR,
    fontSize: 11,
    fontWeight: '700',
    lineHeight: 17,
  },
  reviewFieldLabel: {
    marginTop: 20,
    color: TEXT_COLOR,
    fontSize: 14,
    fontWeight: '900',
  },
  reviewMemberList: {
    marginTop: 11,
    gap: 9,
  },
  reviewMemberRow: {
    minHeight: 64,
    paddingHorizontal: 12,
    paddingVertical: 10,
    borderWidth: 1,
    borderColor: BORDER_COLOR,
    borderRadius: 18,
    flexDirection: 'row',
    alignItems: 'center',
    gap: 12,
    backgroundColor: SURFACE_COLOR,
  },
  reviewMemberRowSelected: {
    borderColor: PRIMARY_COLOR,
    backgroundColor: SOFT_GREEN_COLOR,
  },
  reviewMemberRowCompleted: {
    opacity: 0.55,
    backgroundColor: SOFT_BACKGROUND_COLOR,
  },
  // 체크리스트(ChecklistListView) 체크박스와 동일 규격: 테두리만 있는 빈 원 → 민트 채움+체크.
  reviewCheckbox: {
    width: 26,
    height: 26,
    borderRadius: 13,
    borderWidth: 2,
    borderColor: BORDER_COLOR,
    alignItems: 'center',
    justifyContent: 'center',
  },
  reviewCheckboxChecked: {
    borderColor: BUTTON_BACKGROUND_COLOR,
    backgroundColor: BUTTON_BACKGROUND_COLOR,
  },
  reviewMemberRowAvatar: {
    width: 40,
    height: 40,
    borderRadius: 20,
    alignItems: 'center',
    justifyContent: 'center',
    backgroundColor: SOFT_BACKGROUND_COLOR,
    overflow: 'hidden',
  },
  reviewMemberRowAvatarImage: {
    width: 36,
    height: 36,
  },
  reviewMemberRowText: {
    minWidth: 0,
    flex: 1,
  },
  reviewMemberRowName: {
    color: TEXT_COLOR,
    fontSize: 13,
    fontWeight: '900',
  },
  reviewMemberRowRole: {
    marginTop: 2,
    color: MUTED_TEXT_COLOR,
    fontSize: 10,
    fontWeight: '700',
  },
  reviewMemberCompletedText: {
    color: PRIMARY_COLOR,
    fontWeight: '900',
  },
  reviewTargetCard: {
    minHeight: 62,
    paddingHorizontal: 13,
    paddingVertical: 11,
    borderRadius: 18,
    backgroundColor: SOFT_GREEN_COLOR,
    flexDirection: 'row',
    alignItems: 'center',
    gap: 12,
  },
  reviewTargetAvatar: {
    width: 44,
    height: 44,
    borderRadius: 22,
    alignItems: 'center',
    justifyContent: 'center',
    backgroundColor: SURFACE_COLOR,
    overflow: 'hidden',
  },
  reviewTargetName: {
    color: DARK_GREEN_COLOR,
    fontSize: 15,
    fontWeight: '900',
  },
  reviewTargetHint: {
    marginTop: 2,
    color: MUTED_TEXT_COLOR,
    fontSize: 11,
    fontWeight: '700',
  },
  reviewTagSection: {
    marginTop: 4,
  },
  reviewTagGrid: {
    marginTop: 10,
    flexDirection: 'row',
    flexWrap: 'wrap',
    gap: 8,
  },
  reviewTagChip: {
    paddingHorizontal: 12,
    paddingVertical: 8,
    borderWidth: 1,
    borderColor: BORDER_COLOR,
    borderRadius: 999,
    flexDirection: 'row',
    alignItems: 'center',
    gap: 5,
    backgroundColor: SURFACE_COLOR,
  },
  reviewTagChipActive: {
    borderColor: PRIMARY_COLOR,
    backgroundColor: SOFT_GREEN_COLOR,
  },
  reviewTagChipEmoji: {
    fontSize: 13,
  },
  reviewTagChipText: {
    color: LABEL_COLOR,
    fontSize: 12,
    fontWeight: '700',
  },
  reviewTagChipTextActive: {
    color: DARK_GREEN_COLOR,
    fontWeight: '900',
  },
  reviewLikeToggle: {
    minHeight: 54,
    marginTop: 10,
    paddingHorizontal: 15,
    borderWidth: 1,
    borderColor: BORDER_COLOR,
    borderRadius: 18,
    flexDirection: 'row',
    alignItems: 'center',
    gap: 10,
    backgroundColor: SURFACE_COLOR,
  },
  reviewLikeToggleActive: {
    borderColor: LIKE_ACCENT_COLOR,
    backgroundColor: LIKE_ACCENT_COLOR,
  },
  reviewLikeToggleText: {
    minWidth: 0,
    flex: 1,
    color: LABEL_COLOR,
    fontSize: 13,
    fontWeight: '800',
  },
  reviewLikeToggleTextActive: {
    color: SURFACE_COLOR,
    fontWeight: '900',
  },
  reviewMemberState: {
    minHeight: 82,
    marginTop: 11,
    paddingHorizontal: 14,
    borderRadius: 18,
    backgroundColor: SOFT_BACKGROUND_COLOR,
    flexDirection: 'row',
    alignItems: 'center',
    justifyContent: 'center',
    gap: 9,
  },
  reviewMemberStateText: {
    color: MUTED_TEXT_COLOR,
    fontSize: 11,
    fontWeight: '700',
  },
  reviewRetryButton: {
    paddingHorizontal: 11,
    paddingVertical: 7,
    borderRadius: 14,
    backgroundColor: BUTTON_BACKGROUND_COLOR,
  },
  reviewRetryButtonText: {
    color: DARK_GREEN_COLOR,
    fontSize: 10,
    fontWeight: '900',
  },
  reviewTextHeader: {
    flexDirection: 'row',
    alignItems: 'flex-end',
    justifyContent: 'space-between',
  },
  reviewTextCount: {
    color: MUTED_TEXT_COLOR,
    fontSize: 10,
    fontWeight: '700',
  },
  reviewTextInput: {
    minHeight: 105,
    marginTop: 10,
    paddingHorizontal: 14,
    paddingVertical: 12,
    borderWidth: 1,
    borderColor: BORDER_COLOR,
    borderRadius: 18,
    backgroundColor: SURFACE_COLOR,
    color: TEXT_COLOR,
    fontSize: 12,
    fontWeight: '600',
    lineHeight: 19,
  },
  reviewApiNotice: {
    marginTop: 11,
    flexDirection: 'row',
    alignItems: 'center',
    gap: 6,
  },
  reviewApiNoticeText: {
    minWidth: 0,
    flex: 1,
    color: MUTED_TEXT_COLOR,
    fontSize: 9,
    fontWeight: '700',
    lineHeight: 14,
  },
  reviewSubmitButton: {
    minHeight: 54,
    marginHorizontal: 20,
    borderRadius: 18,
    backgroundColor: PRIMARY_COLOR,
    flexDirection: 'row',
    alignItems: 'center',
    justifyContent: 'center',
    gap: 7,
  },
  reviewSubmitButtonDisabled: {
    backgroundColor: PLACEHOLDER_COLOR,
  },
  reviewSubmitButtonText: {
    color: SURFACE_COLOR,
    fontSize: 14,
    fontWeight: '900',
  },
  pressed: {
    opacity: 0.66,
  },
});
