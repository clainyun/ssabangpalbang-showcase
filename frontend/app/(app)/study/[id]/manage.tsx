import Ionicons from '@expo/vector-icons/Ionicons';
import { useQueryClient } from '@tanstack/react-query';
import { router, useFocusEffect, useLocalSearchParams } from 'expo-router';
import { useCallback, useMemo, useRef, useState } from 'react';
import {
  ActivityIndicator,
  FlatList,
  Image,
  Modal,
  type NativeScrollEvent,
  type NativeSyntheticEvent,
  Pressable,
  ScrollView,
  StyleSheet,
  Text,
  TextInput,
  View,
} from 'react-native';
import Animated, { useAnimatedKeyboard, useAnimatedStyle } from 'react-native-reanimated';
import { useSafeAreaInsets } from 'react-native-safe-area-context';

import { appAlert } from '@/components/AppDialog';
import { ScreenGlowBackground } from '@/components/ScreenGlowBackground';
import {
  BORDER_COLOR,
  BUTTON_BACKGROUND_COLOR,
  CALENDAR_SATURDAY_COLOR,
  DARK_GREEN_COLOR,
  ERROR_COLOR,
  LABEL_COLOR,
  MUTED_TEXT_COLOR,
  PLACEHOLDER_COLOR,
  PRIMARY_COLOR,
  SOFT_GREEN_COLOR,
  SUBTLE_BACKGROUND_COLOR,
  SURFACE_COLOR,
  TEXT_COLOR,
} from '@/constants/colors';
import { characterAvatarSource } from '@/features/character/characterAvatar';
import { formatDateTime, formatPurpose } from '@/features/apartment/format';
import {
  StudyApiError,
  type ApplicationStatus,
  type ScheduleItem,
  type StudyApplication,
} from '@/features/study/api/types';
import {
  formatScheduleTime,
  getScheduleDateParts,
  getSeoulHourMinute,
  withSeoulTime,
} from '@/features/study/formatStudyDate';
import {
  createScheduleTimePickerState,
  type ScheduleTimePickerState,
} from '@/features/study/studySchedulePicker';
import { studyDetailQueryKey, useStudyDetail } from '@/features/study/useStudyDetail';
import {
  applicationsQueryKey,
  useApplications,
  useApproveApplication,
  useCloseRecruitment,
  useCreateSchedule,
  useDeleteSchedule,
  useRejectApplication,
  useStudySchedule,
  useUpdateSchedule,
} from '@/features/study/useStudyManagement';
import { refetchOnFocusIfStale, refetchOnFocusNow } from '@/lib/refetchOnFocusIfStale';

type ManageTab = 'APPLICANTS' | 'SCHEDULE';
type StatusFilter = 'ALL' | ApplicationStatus;

const TIME_WHEEL_ITEM_HEIGHT = 44;
const TIME_WHEEL_VISIBLE_ITEMS = 5;
const TIME_WHEEL_HEIGHT = TIME_WHEEL_ITEM_HEIGHT * TIME_WHEEL_VISIBLE_ITEMS;
const TIME_WHEEL_PADDING = TIME_WHEEL_ITEM_HEIGHT * 2;
const TIME_WHEEL_HOURS = Array.from({ length: 12 }, (_, index) => index + 1);
const TIME_WHEEL_MINUTES = Array.from({ length: 60 }, (_, index) => index);
const TIME_WHEEL_PERIODS = ['오전', '오후'] as const;

interface TimeWheelColumnProps<T extends string | number> {
  accessibilityLabel: string;
  items: readonly T[];
  selectedIndex: number;
  formatItem: (item: T) => string;
  onSelectIndex: (index: number) => void;
}

function TimeWheelColumn<T extends string | number>({
  accessibilityLabel,
  items,
  selectedIndex,
  formatItem,
  onSelectIndex,
}: TimeWheelColumnProps<T>) {
  const commitScrollPosition = (event: NativeSyntheticEvent<NativeScrollEvent>) => {
    const nextIndex = Math.max(
      0,
      Math.min(
        items.length - 1,
        Math.round(event.nativeEvent.contentOffset.y / TIME_WHEEL_ITEM_HEIGHT),
      ),
    );
    onSelectIndex(nextIndex);
  };

  return (
    <FlatList
      accessibilityLabel={accessibilityLabel}
      contentContainerStyle={styles.timeWheelColumnContent}
      data={items}
      decelerationRate="fast"
      getItemLayout={(_, index) => ({
        index,
        length: TIME_WHEEL_ITEM_HEIGHT,
        offset: TIME_WHEEL_ITEM_HEIGHT * index,
      })}
      initialScrollIndex={selectedIndex}
      keyExtractor={(item) => String(item)}
      onMomentumScrollEnd={commitScrollPosition}
      onScrollEndDrag={commitScrollPosition}
      renderItem={({ item, index }) => (
        <Pressable
          accessibilityRole="button"
          onPress={() => onSelectIndex(index)}
          style={styles.timeWheelItem}
        >
          <Text
            style={[
              styles.timeWheelItemText,
              index === selectedIndex && styles.timeWheelItemTextSelected,
            ]}
          >
            {formatItem(item)}
          </Text>
        </Pressable>
      )}
      showsVerticalScrollIndicator={false}
      snapToAlignment="start"
      snapToInterval={TIME_WHEEL_ITEM_HEIGHT}
      style={styles.timeWheelColumn}
    />
  );
}

function TimeWheelModal({
  picker,
  onCancel,
  onConfirm,
}: {
  picker: ScheduleTimePickerState;
  onCancel: () => void;
  onConfirm: (hour: number, minute: number, period: (typeof TIME_WHEEL_PERIODS)[number]) => void;
}) {
  // 화면 표시(formatScheduleTime)가 Asia/Seoul 기준이라 휠도 같은 기준으로 읽습니다.
  // 기기 로컬 시각을 쓰면 타임존이 KST 가 아닐 때 표시와 휠이 어긋납니다.
  const { hour: initialHour, minute: initialMinute } = getSeoulHourMinute(picker.base);
  const [hourIndex, setHourIndex] = useState((initialHour + 11) % 12);
  const [minuteIndex, setMinuteIndex] = useState(initialMinute);
  const [periodIndex, setPeriodIndex] = useState(initialHour >= 12 ? 1 : 0);

  return (
    <Modal animationType="fade" onRequestClose={onCancel} statusBarTranslucent transparent visible>
      <Pressable style={styles.timeModalScrim} onPress={onCancel}>
        <Pressable accessibilityRole="none" onPress={(event) => event.stopPropagation()}>
          <View style={styles.timeModalCard}>
            <View style={styles.timeModalHeader}>
              <View>
                <Text style={styles.timeModalEyebrow}>
                  {picker.field === 'start' ? '시작 시간' : '종료 시간'}
                </Text>
                <Text style={styles.timeModalTitle}>시간을 선택해 주세요</Text>
              </View>
              <View style={styles.timeModalClockIcon}>
                <Ionicons name="time-outline" size={20} color={PRIMARY_COLOR} />
              </View>
            </View>

            <View style={styles.timeWheelPanel}>
              <View pointerEvents="none" style={styles.timeWheelSelection} />
              <View pointerEvents="none" style={styles.timeWheelTopFade} />
              <View pointerEvents="none" style={styles.timeWheelBottomFade} />
              <TimeWheelColumn
                accessibilityLabel="시 선택"
                formatItem={(item) => String(item)}
                items={TIME_WHEEL_HOURS}
                onSelectIndex={setHourIndex}
                selectedIndex={hourIndex}
              />
              <TimeWheelColumn
                accessibilityLabel="분 선택"
                formatItem={(item) => String(item).padStart(2, '0')}
                items={TIME_WHEEL_MINUTES}
                onSelectIndex={setMinuteIndex}
                selectedIndex={minuteIndex}
              />
              <TimeWheelColumn
                accessibilityLabel="오전 오후 선택"
                formatItem={(item) => item}
                items={TIME_WHEEL_PERIODS}
                onSelectIndex={setPeriodIndex}
                selectedIndex={periodIndex}
              />
            </View>

            <View style={styles.timeModalActions}>
              <Pressable
                onPress={onCancel}
                style={({ pressed }) => [styles.timeModalCancelButton, pressed && styles.pressed]}
              >
                <Text style={styles.timeModalCancelText}>취소</Text>
              </Pressable>
              <Pressable
                onPress={() =>
                  onConfirm(
                    TIME_WHEEL_HOURS[hourIndex] ?? 1,
                    TIME_WHEEL_MINUTES[minuteIndex] ?? 0,
                    TIME_WHEEL_PERIODS[periodIndex] ?? '오전',
                  )
                }
                style={({ pressed }) => [styles.timeModalConfirmButton, pressed && styles.pressed]}
              >
                <Text style={styles.timeModalConfirmText}>확인</Text>
              </Pressable>
            </View>
          </View>
        </Pressable>
      </Pressable>
    </Modal>
  );
}

const STATUS_FILTERS: { key: StatusFilter; label: string }[] = [
  { key: 'ALL', label: '전체' },
  { key: 'PENDING', label: '대기' },
  { key: 'APPROVED', label: '승인' },
  { key: 'REJECTED', label: '거절' },
];

function goBackOrHome() {
  if (router.canGoBack()) {
    router.back();
  } else {
    router.replace('/(app)/(tabs)/home');
  }
}

function openMemberProfile(memberId: number) {
  router.push({
    pathname: '/(app)/member/[memberId]',
    params: { memberId: String(memberId) },
  });
}

function extractErrorMessage(error: unknown, fallback: string): string {
  return error instanceof StudyApiError ? error.message : fallback;
}

interface ApplicationRowProps {
  application: StudyApplication;
  isProcessing: boolean;
  onApprove: () => void;
  onOpenProfile: () => void;
  onReject: () => void;
}

function ApplicationRow({
  application,
  isProcessing,
  onApprove,
  onOpenProfile,
  onReject,
}: ApplicationRowProps) {
  return (
    <View style={styles.card}>
      <View style={styles.rowHeader}>
        <Pressable
          accessibilityLabel={`${application.applicant.nickname} 프로필 보기`}
          accessibilityRole="button"
          onPress={onOpenProfile}
          style={({ pressed }) => [styles.profileLink, pressed && styles.pressed]}
        >
          <Image
            source={characterAvatarSource(application.applicant.selectedCharacterId)}
            style={styles.avatar}
          />
          <View style={styles.rowHeaderText}>
            <Text style={styles.nickname}>{application.applicant.nickname}</Text>
            <Text style={styles.metaText}>
              {formatPurpose(application.purpose)} · {formatDateTime(application.createdAt)}
            </Text>
          </View>
        </Pressable>
      </View>
      {!!application.intro && <Text style={styles.introText}>{application.intro}</Text>}

      {application.status === 'PENDING' ? (
        <View style={styles.actionRow}>
          <Pressable
            disabled={!application.canReject || isProcessing}
            onPress={onReject}
            style={({ pressed }) => [
              styles.rejectButton,
              (!application.canReject || isProcessing) && styles.buttonDisabled,
              pressed && styles.pressed,
            ]}
          >
            <Text style={styles.rejectButtonText}>거절</Text>
          </Pressable>
          <Pressable
            disabled={!application.canApprove || isProcessing}
            onPress={onApprove}
            style={({ pressed }) => [
              styles.approveButton,
              (!application.canApprove || isProcessing) && styles.buttonDisabled,
              pressed && styles.pressed,
            ]}
          >
            {isProcessing ? (
              <ActivityIndicator color={SOFT_GREEN_COLOR} size="small" />
            ) : (
              <Text style={styles.approveButtonText}>
                {application.canApprove ? '승인' : '정원 마감'}
              </Text>
            )}
          </Pressable>
        </View>
      ) : (
        <Text style={styles.decidedText}>
          {application.status === 'APPROVED' ? '승인됨' : '거절됨'}
          {application.decidedAt ? ` · ${formatDateTime(application.decidedAt)}` : ''}
        </Text>
      )}
    </View>
  );
}

const CALENDAR_WEEKDAYS = ['일', '월', '화', '수', '목', '금', '토'] as const;

type CalendarMonth = { year: number; month: number };

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

function InlineDateCalendar({
  selectedDate,
  onSelectDate,
}: {
  selectedDate: Date | null;
  onSelectDate: (date: Date) => void;
}) {
  const [visibleMonth, setVisibleMonth] = useState<CalendarMonth>(() => {
    const base = selectedDate ?? new Date();
    return { year: base.getFullYear(), month: base.getMonth() + 1 };
  });
  const calendarCells = useMemo(() => getCalendarCells(visibleMonth), [visibleMonth]);
  const today = new Date();

  return (
    <View style={styles.inlineCalendar}>
      <View style={styles.inlineCalendarHeader}>
        <Pressable
          accessibilityLabel="이전 달"
          hitSlop={8}
          onPress={() => setVisibleMonth((current) => shiftCalendarMonth(current, -1))}
          style={({ pressed }) => [styles.calendarMonthButton, pressed && styles.pressed]}
        >
          <Ionicons name="chevron-back" size={18} color={DARK_GREEN_COLOR} />
        </Pressable>
        <Text style={styles.calendarMonthTitle}>
          {visibleMonth.year}년 {visibleMonth.month}월
        </Text>
        <Pressable
          accessibilityLabel="다음 달"
          hitSlop={8}
          onPress={() => setVisibleMonth((current) => shiftCalendarMonth(current, 1))}
          style={({ pressed }) => [styles.calendarMonthButton, pressed && styles.pressed]}
        >
          <Ionicons name="chevron-forward" size={18} color={DARK_GREEN_COLOR} />
        </Pressable>
      </View>

      <View style={styles.calendarWeekRow}>
        {CALENDAR_WEEKDAYS.map((weekday, index) => (
          <View key={weekday} style={styles.calendarColumn}>
            <Text
              style={[
                styles.calendarWeekday,
                index === 0 && styles.calendarSundayText,
                index === 6 && styles.calendarSaturdayText,
              ]}
            >
              {weekday}
            </Text>
          </View>
        ))}
      </View>

      <View style={styles.calendarGrid}>
        {calendarCells.map((day, index) => {
          if (day === null) return <View key={`empty-${index}`} style={styles.calendarDayCell} />;
          const selected =
            selectedDate?.getFullYear() === visibleMonth.year &&
            selectedDate.getMonth() + 1 === visibleMonth.month &&
            selectedDate.getDate() === day;
          const todayDate =
            today.getFullYear() === visibleMonth.year &&
            today.getMonth() + 1 === visibleMonth.month &&
            today.getDate() === day;
          const weekday = new Date(
            Date.UTC(visibleMonth.year, visibleMonth.month - 1, day),
          ).getUTCDay();
          return (
            <View
              key={`${visibleMonth.year}-${visibleMonth.month}-${day}`}
              style={styles.calendarDayCell}
            >
              <Pressable
                accessibilityLabel={`${visibleMonth.year}년 ${visibleMonth.month}월 ${day}일${todayDate ? ', 오늘' : ''}`}
                accessibilityRole="button"
                accessibilityState={{ selected }}
                onPress={() =>
                  onSelectDate(new Date(visibleMonth.year, visibleMonth.month - 1, day))
                }
                style={({ pressed }) => [
                  styles.calendarDayCircle,
                  todayDate && styles.calendarTodayCircle,
                  selected && styles.calendarSelectedCircle,
                  pressed && styles.pressed,
                ]}
              >
                <Text
                  style={[
                    styles.calendarDayText,
                    weekday === 0 && styles.calendarSundayText,
                    weekday === 6 && styles.calendarSaturdayText,
                    todayDate && styles.calendarTodayText,
                    selected && styles.calendarSelectedText,
                  ]}
                >
                  {day}
                </Text>
              </Pressable>
            </View>
          );
        })}
      </View>
    </View>
  );
}

interface ScheduleViewProps {
  schedule: ScheduleItem;
  canManage: boolean;
  onEdit: () => void;
  onDelete: () => void;
  isDeleting: boolean;
}

function ScheduleView({ schedule, canManage, onEdit, onDelete, isDeleting }: ScheduleViewProps) {
  const { month, day, weekdayKo } = getScheduleDateParts(schedule.startAt);
  return (
    <View>
      <View style={styles.scheduleSectionHeader}>
        <View>
          <Text style={styles.scheduleSectionEyebrow}>임장 일정</Text>
          <Text style={styles.scheduleSectionTitle}>등록된 일정 1개</Text>
        </View>
        <View style={styles.scheduleStatusBadge}>
          <View style={styles.scheduleStatusDot} />
          <Text style={styles.scheduleStatusText}>일정 확정</Text>
        </View>
      </View>

      <View style={styles.scheduleCard}>
        <View style={styles.scheduleRow}>
          <View style={styles.dateBadge}>
            <Text style={styles.dateBadgeMonth}>{month}월</Text>
            <Text style={styles.dateBadgeDay}>{day}</Text>
            <Text style={styles.dateBadgeWeekday}>{weekdayKo}요일</Text>
          </View>
          <View style={styles.scheduleInfo}>
            <Text style={styles.scheduleInfoLabel}>시작 시간</Text>
            <Text style={styles.scheduleTime}>
              {formatScheduleTime(schedule.startAt)}
              {schedule.endAt ? ` ~ ${formatScheduleTime(schedule.endAt)}` : ''}
            </Text>
            <View style={styles.meetingPlaceRow}>
              <Ionicons name="location-outline" size={15} color={PRIMARY_COLOR} />
              <Text style={styles.meetingPlaceText}>{schedule.meetingPlace}</Text>
            </View>
          </View>
        </View>

        {canManage && (
          <View style={styles.scheduleActions}>
            <Pressable
              onPress={onEdit}
              style={({ pressed }) => [styles.scheduleEditButton, pressed && styles.pressed]}
            >
              <Ionicons name="pencil-outline" size={15} color={DARK_GREEN_COLOR} />
              <Text style={styles.scheduleEditButtonText}>일정 수정</Text>
            </Pressable>
            <Pressable
              disabled={isDeleting}
              onPress={onDelete}
              style={({ pressed }) => [
                styles.scheduleDeleteButton,
                isDeleting && styles.buttonDisabled,
                pressed && styles.pressed,
              ]}
            >
              {isDeleting ? (
                <ActivityIndicator color={ERROR_COLOR} size="small" />
              ) : (
                <>
                  <Ionicons name="trash-outline" size={15} color={ERROR_COLOR} />
                  <Text style={styles.scheduleDeleteButtonText}>일정 삭제</Text>
                </>
              )}
            </Pressable>
          </View>
        )}
      </View>

      <View style={styles.scheduleGuide}>
        <Ionicons name="information-circle-outline" size={16} color={PRIMARY_COLOR} />
        <Text style={styles.scheduleGuideText}>
          일정은 하나만 등록할 수 있으며, 수정 내용은 모든 멤버에게 바로 반영돼요.
        </Text>
      </View>
    </View>
  );
}

interface EmptyScheduleViewProps {
  canManage: boolean;
  onCreate: () => void;
}

function EmptyScheduleView({ canManage, onCreate }: EmptyScheduleViewProps) {
  return (
    <View style={styles.emptyScheduleCard}>
      <View style={styles.emptyScheduleIcon}>
        <Ionicons name="calendar-clear-outline" size={28} color={PRIMARY_COLOR} />
      </View>
      <Text style={styles.emptyScheduleTitle}>아직 등록된 일정이 없어요</Text>
      <Text style={styles.emptyScheduleDescription}>
        스터디에서 함께할 임장 날짜와 만날 장소를 한 번만 등록하면 돼요.
      </Text>
      {canManage && (
        <Pressable
          onPress={onCreate}
          style={({ pressed }) => [styles.createScheduleButton, pressed && styles.pressed]}
        >
          <Ionicons name="add" size={18} color={SURFACE_COLOR} />
          <Text style={styles.createScheduleButtonText}>첫 일정 등록하기</Text>
        </Pressable>
      )}
    </View>
  );
}
interface ScheduleFormProps {
  startAt: Date | null;
  endAt: Date | null;
  meetingPlace: string;
  onOpenTimePicker: (field: 'start' | 'end') => void;
  onSelectDate: (date: Date) => void;
  onClearEndAt: () => void;
  onChangeMeetingPlace: (value: string) => void;
  onMeetingPlaceFocus: () => void;
  onMeetingPlaceBlur: () => void;
  onSubmit: () => void;
  onCancel: () => void;
  showCancel: boolean;
  isSubmitting: boolean;
  isEditMode: boolean;
}

function ScheduleForm({
  startAt,
  endAt,
  meetingPlace,
  onOpenTimePicker,
  onSelectDate,
  onClearEndAt,
  onChangeMeetingPlace,
  onMeetingPlaceFocus,
  onMeetingPlaceBlur,
  onSubmit,
  onCancel,
  showCancel,
  isSubmitting,
  isEditMode,
}: ScheduleFormProps) {
  const canSubmit = startAt !== null && meetingPlace.trim().length > 0 && !isSubmitting;

  return (
    <View style={styles.scheduleFormCard}>
      <View style={styles.scheduleFormHeader}>
        <View style={styles.scheduleFormIcon}>
          <Ionicons
            name={isEditMode ? 'pencil-outline' : 'calendar-outline'}
            size={19}
            color={PRIMARY_COLOR}
          />
        </View>
        <View style={styles.scheduleFormHeadingCopy}>
          <Text style={styles.scheduleFormTitle}>{isEditMode ? '일정 수정' : '일정 등록'}</Text>
          <Text style={styles.scheduleFormDescription}>
            {isEditMode
              ? '변경할 날짜와 장소를 확인해 주세요.'
              : '스터디에서 사용할 하나의 임장 일정을 등록해 주세요.'}
          </Text>
        </View>
      </View>

      <Text style={styles.fieldLabel}>임장 날짜</Text>
      <InlineDateCalendar onSelectDate={onSelectDate} selectedDate={startAt} />

      <View style={styles.labelWithAction}>
        <Text style={styles.fieldLabel}>시간</Text>
        {endAt && (
          <Pressable onPress={onClearEndAt} hitSlop={8}>
            <Text style={styles.clearLinkText}>지우기</Text>
          </Pressable>
        )}
      </View>
      <View style={styles.timePickerRow}>
        <Pressable
          disabled={!startAt}
          onPress={() => onOpenTimePicker('start')}
          style={({ pressed }) => [
            styles.timePickerButton,
            !startAt && styles.timePickerButtonDisabled,
            pressed && styles.pressed,
          ]}
        >
          <Text style={styles.timePickerLabel}>시작</Text>
          <View style={styles.timePickerValueRow}>
            <Ionicons name="time-outline" size={16} color={PRIMARY_COLOR} />
            <Text style={startAt ? styles.timePickerValue : styles.timePickerPlaceholder}>
              {startAt ? formatScheduleTime(startAt.toISOString()) : '날짜 선택'}
            </Text>
          </View>
        </Pressable>
        <Ionicons name="arrow-forward" size={16} color={PLACEHOLDER_COLOR} />
        <Pressable
          disabled={!startAt}
          onPress={() => onOpenTimePicker('end')}
          style={({ pressed }) => [
            styles.timePickerButton,
            !startAt && styles.timePickerButtonDisabled,
            pressed && styles.pressed,
          ]}
        >
          <Text style={styles.timePickerLabel}>종료 (선택)</Text>
          <View style={styles.timePickerValueRow}>
            <Ionicons name="time-outline" size={16} color={PRIMARY_COLOR} />
            <Text style={endAt ? styles.timePickerValue : styles.timePickerPlaceholder}>
              {endAt ? formatScheduleTime(endAt.toISOString()) : '시간 추가'}
            </Text>
          </View>
        </Pressable>
      </View>

      <Text style={styles.fieldLabel}>모임 장소</Text>
      <TextInput
        maxLength={200}
        onChangeText={onChangeMeetingPlace}
        onFocus={onMeetingPlaceFocus}
        onBlur={onMeetingPlaceBlur}
        placeholder="예: 역삼역 2번 출구"
        placeholderTextColor={PLACEHOLDER_COLOR}
        style={styles.textInput}
        value={meetingPlace}
      />

      <View style={styles.formActionRow}>
        {showCancel && (
          <Pressable
            disabled={isSubmitting}
            onPress={onCancel}
            style={({ pressed }) => [styles.rejectButton, pressed && styles.pressed]}
          >
            <Text style={styles.rejectButtonText}>취소</Text>
          </Pressable>
        )}
        <Pressable
          disabled={!canSubmit}
          onPress={onSubmit}
          style={({ pressed }) => [
            styles.approveButton,
            !canSubmit && styles.buttonDisabled,
            pressed && styles.pressed,
          ]}
        >
          {isSubmitting ? (
            <ActivityIndicator color={SOFT_GREEN_COLOR} size="small" />
          ) : (
            <Text style={styles.approveButtonText}>{isEditMode ? '수정 완료' : '일정 등록'}</Text>
          )}
        </Pressable>
      </View>
    </View>
  );
}

export default function StudyManageScreen() {
  const params = useLocalSearchParams<{ id: string; tab?: ManageTab }>();
  const studyId = Number(params.id);
  const insets = useSafeAreaInsets();

  // edge-to-edge + New Arch에선 adjustResize가 창을 안 줄여 키보드가 모임 장소 입력을 덮음.
  // reanimated useAnimatedKeyboard로 스크롤 끝에 키보드 높이만큼 스페이서를 둬 입력이 키보드 위로 스크롤되게.
  const keyboard = useAnimatedKeyboard();
  // 키보드 높이는 '모임 장소 입력이 실제로 포커스일 때'만 반영한다. 안드로이드에서
  // 액티비티·모달 전환 중 close inset 애니메이션 콜백이 유실되면 keyboard.height가
  // 이전 값에 stuck될 수 있는데, 포커스는 JS에서 직접 제어하므로 게이트가 내려가
  // 있으면 stuck된 값이 스페이서를 벌리지 못한다(글 작성 create.tsx와 동일 패턴).
  const [isInputFocused, setIsInputFocused] = useState(false);
  const keyboardSpacerStyle = useAnimatedStyle(() => ({
    height: isInputFocused ? keyboard.height.value : 0,
  }));
  const scrollRef = useRef<ScrollView>(null);
  const handleMeetingPlaceFocus = useCallback(() => {
    setIsInputFocused(true);
    requestAnimationFrame(() => scrollRef.current?.scrollToEnd({ animated: true }));
  }, []);
  const handleMeetingPlaceBlur = useCallback(() => {
    setIsInputFocused(false);
  }, []);

  const [tab, setTab] = useState<ManageTab>(params.tab === 'SCHEDULE' ? 'SCHEDULE' : 'APPLICANTS');
  const [statusFilter, setStatusFilter] = useState<StatusFilter>('PENDING');
  const [processingId, setProcessingId] = useState<number | null>(null);

  const queryClient = useQueryClient();
  const { data: study } = useStudyDetail(studyId);
  const applicationsQuery = useApplications(
    studyId,
    statusFilter === 'ALL' ? undefined : statusFilter,
  );
  const approveMutation = useApproveApplication(studyId);
  const rejectMutation = useRejectApplication(studyId);
  const closeMutation = useCloseRecruitment(studyId);

  const scheduleQuery = useStudySchedule(studyId);
  const createScheduleMutation = useCreateSchedule(studyId);
  const updateScheduleMutation = useUpdateSchedule(studyId);
  const deleteScheduleMutation = useDeleteSchedule(studyId);

  const [isEditingSchedule, setIsEditingSchedule] = useState(false);
  const [scheduleStartAt, setScheduleStartAt] = useState<Date | null>(null);
  const [scheduleEndAt, setScheduleEndAt] = useState<Date | null>(null);
  const [meetingPlace, setMeetingPlace] = useState('');
  const [picker, setPicker] = useState<ScheduleTimePickerState | null>(null);

  useFocusEffect(
    useCallback(() => {
      if (!Number.isFinite(studyId)) return;
      // 모집 상태·인원수는 운영 판단의 기준이라 게이트 없이 항상 다시 조회합니다.
      refetchOnFocusNow(queryClient, studyDetailQueryKey(studyId));
      // 신청자 목록은 승인·거절 응답이 캐시에 즉시 반영되고 새 신청은 푸시로 무효화되므로,
      // 프로필 이미지 깜빡임을 줄이는 20초 게이트를 그대로 둡니다.
      refetchOnFocusIfStale(
        queryClient,
        applicationsQueryKey(studyId, statusFilter === 'ALL' ? undefined : statusFilter),
      );
    }, [queryClient, statusFilter, studyId]),
  );

  const handleApprove = useCallback(
    (application: StudyApplication) => {
      setProcessingId(application.applicationId);
      approveMutation.mutate(application.applicationId, {
        onError: (error) =>
          appAlert('승인 실패', extractErrorMessage(error, '신청을 승인하지 못했습니다.')),
        onSettled: () => setProcessingId(null),
      });
    },
    [approveMutation],
  );

  const handleReject = useCallback(
    (application: StudyApplication) => {
      appAlert('신청을 거절할까요?', `${application.applicant.nickname}님의 신청을 거절합니다.`, [
        { text: '취소', style: 'cancel' },
        {
          text: '거절하기',
          style: 'destructive',
          onPress: () => {
            setProcessingId(application.applicationId);
            rejectMutation.mutate(application.applicationId, {
              onError: (error) =>
                appAlert('거절 실패', extractErrorMessage(error, '신청을 거절하지 못했습니다.')),
              onSettled: () => setProcessingId(null),
            });
          },
        },
      ]);
    },
    [rejectMutation],
  );

  const handleCloseRecruitment = useCallback(() => {
    appAlert('모집을 마감할까요?', '마감하면 새로운 신청을 받을 수 없어요.', [
      { text: '취소', style: 'cancel' },
      {
        text: '마감하기',
        onPress: () => {
          closeMutation.mutate(undefined, {
            onError: (error) =>
              appAlert('마감 실패', extractErrorMessage(error, '모집을 마감하지 못했습니다.')),
          });
        },
      },
    ]);
  }, [closeMutation]);

  const schedule = scheduleQuery.data?.schedule ?? null;
  const canManageSchedule = scheduleQuery.data?.canManageSchedule ?? false;

  const startEditingSchedule = useCallback(() => {
    if (schedule) {
      setScheduleStartAt(new Date(schedule.startAt));
      setScheduleEndAt(schedule.endAt ? new Date(schedule.endAt) : null);
      setMeetingPlace(schedule.meetingPlace);
    } else {
      setScheduleStartAt(null);
      setScheduleEndAt(null);
      setMeetingPlace('');
    }
    setIsEditingSchedule(true);
  }, [schedule]);

  const handleOpenPicker = useCallback(
    (field: 'start' | 'end') => {
      const nextPicker = createScheduleTimePickerState(field, scheduleStartAt, scheduleEndAt);
      if (nextPicker !== null) setPicker(nextPicker);
    },
    [scheduleStartAt, scheduleEndAt],
  );

  const handleSelectScheduleDate = useCallback(
    (selectedDate: Date) => {
      const nextStart = scheduleStartAt ? new Date(scheduleStartAt) : new Date(selectedDate);
      nextStart.setFullYear(
        selectedDate.getFullYear(),
        selectedDate.getMonth(),
        selectedDate.getDate(),
      );
      // 아직 시각을 고른 적이 없으면 지금 시각으로 채웁니다. 고정값(예전 10:00)은
      // 어디서 온 숫자인지 알 수 없어 매번 바꿔야 했습니다.
      if (!scheduleStartAt) {
        const now = getSeoulHourMinute(new Date());
        setScheduleStartAt(withSeoulTime(nextStart, now.hour, now.minute));
        return;
      }
      setScheduleStartAt(nextStart);

      if (scheduleEndAt) {
        const nextEnd = new Date(scheduleEndAt);
        nextEnd.setFullYear(
          selectedDate.getFullYear(),
          selectedDate.getMonth(),
          selectedDate.getDate(),
        );
        setScheduleEndAt(nextEnd);
      }
    },
    [scheduleEndAt, scheduleStartAt],
  );

  const handleConfirmPicker = useCallback(
    (hour: number, minute: number, period: (typeof TIME_WHEEL_PERIODS)[number]) => {
      const current = picker;
      setPicker(null);
      if (!current) return;

      const hour24 = (hour % 12) + (period === '오후' ? 12 : 0);
      const merged = withSeoulTime(current.base, hour24, minute);
      if (current.field === 'start') setScheduleStartAt(merged);
      else setScheduleEndAt(merged);
    },
    [picker],
  );

  const handleSubmitSchedule = useCallback(() => {
    if (!scheduleStartAt || meetingPlace.trim().length === 0) return;
    const input = {
      startAt: scheduleStartAt.toISOString(),
      endAt: scheduleEndAt ? scheduleEndAt.toISOString() : undefined,
      meetingPlace: meetingPlace.trim(),
    };
    const mutation = schedule ? updateScheduleMutation : createScheduleMutation;
    mutation.mutate(input, {
      onSuccess: () => setIsEditingSchedule(false),
      onError: (error) =>
        appAlert(
          schedule ? '수정 실패' : '등록 실패',
          extractErrorMessage(
            error,
            schedule ? '일정을 수정하지 못했습니다.' : '일정을 등록하지 못했습니다.',
          ),
        ),
    });
  }, [
    schedule,
    scheduleStartAt,
    scheduleEndAt,
    meetingPlace,
    createScheduleMutation,
    updateScheduleMutation,
  ]);

  const handleDeleteSchedule = useCallback(() => {
    appAlert(
      '일정을 삭제할까요?',
      '현재 임장 일정이 사라져요. 삭제 후에는 새 일정을 다시 등록할 수 있습니다.',
      [
        { text: '취소', style: 'cancel' },
        {
          text: '삭제하기',
          style: 'destructive',
          onPress: () => {
            deleteScheduleMutation.mutate(undefined, {
              onSuccess: () => {
                setScheduleStartAt(null);
                setScheduleEndAt(null);
                setMeetingPlace('');
              },
              onError: (error) =>
                appAlert('삭제 실패', extractErrorMessage(error, '일정을 삭제하지 못했습니다.')),
            });
          },
        },
      ],
    );
  }, [deleteScheduleMutation]);

  const summary = applicationsQuery.data?.summary;
  const applications = useMemo(
    () => applicationsQuery.data?.content ?? [],
    [applicationsQuery.data],
  );
  return (
    <View style={styles.screen}>
      <ScreenGlowBackground />

      <View style={[styles.headerRow, { paddingTop: insets.top + 8 }]}>
        <Pressable onPress={goBackOrHome} hitSlop={12} style={styles.headerIconButton}>
          <Ionicons color={DARK_GREEN_COLOR} name="chevron-back" size={20} />
        </Pressable>
        <Text style={styles.headerTitle}>스터디 관리</Text>
        {/* 제목을 가운데로 맞추기 위한 빈 자리 — 뒤로가기 버튼과 같은 폭이되, 배경 원은 없앤다. */}
        <View style={styles.headerIconSpacer} />
      </View>

      {study && study.status === 'RECRUITING' && (
        <View style={styles.closeRow}>
          <Text style={styles.closeRowText}>
            현재 {study.currentMemberCount}/{study.capacity}명 모집 중
          </Text>
          <Pressable
            disabled={closeMutation.isPending}
            onPress={handleCloseRecruitment}
            style={({ pressed }) => [styles.closeButton, pressed && styles.pressed]}
          >
            {closeMutation.isPending ? (
              <ActivityIndicator color={MUTED_TEXT_COLOR} size="small" />
            ) : (
              <Text style={styles.closeButtonText}>모집 마감</Text>
            )}
          </Pressable>
        </View>
      )}

      <View style={styles.tabRow}>
        <Pressable
          onPress={() => setTab('APPLICANTS')}
          style={[styles.tabButton, tab === 'APPLICANTS' && styles.tabButtonActive]}
        >
          <Text style={[styles.tabButtonText, tab === 'APPLICANTS' && styles.tabButtonTextActive]}>
            신청자{summary ? ` ${summary.pendingCount}` : ''}
          </Text>
        </Pressable>
        <Pressable
          onPress={() => setTab('SCHEDULE')}
          style={[styles.tabButton, tab === 'SCHEDULE' && styles.tabButtonActive]}
        >
          <Text style={[styles.tabButtonText, tab === 'SCHEDULE' && styles.tabButtonTextActive]}>
            일정
          </Text>
        </Pressable>
      </View>

      <ScrollView
        alwaysBounceVertical={false}
        bounces={false}
        contentContainerStyle={[styles.scrollContent, { paddingBottom: insets.bottom + 32 }]}
        keyboardShouldPersistTaps="handled"
        overScrollMode="never"
        ref={scrollRef}
        showsVerticalScrollIndicator={false}
      >
        {tab === 'APPLICANTS' ? (
          <>
            <View style={styles.filterRow}>
              {STATUS_FILTERS.map((filter) => (
                <Pressable
                  key={filter.key}
                  onPress={() => setStatusFilter(filter.key)}
                  style={[
                    styles.filterChip,
                    statusFilter === filter.key && styles.filterChipActive,
                  ]}
                >
                  <Text
                    style={[
                      styles.filterChipText,
                      statusFilter === filter.key && styles.filterChipTextActive,
                    ]}
                  >
                    {filter.label}
                  </Text>
                </Pressable>
              ))}
            </View>

            {applicationsQuery.isLoading ? (
              <ActivityIndicator color={PRIMARY_COLOR} style={styles.loader} />
            ) : applicationsQuery.isError && applicationsQuery.data === undefined ? (
              <Text style={styles.errorText}>
                {extractErrorMessage(applicationsQuery.error, '신청자 목록을 불러오지 못했어요.')}
              </Text>
            ) : applications.length === 0 ? (
              <Text style={styles.emptyText}>해당하는 신청이 없어요.</Text>
            ) : (
              applications.map((application) => (
                <ApplicationRow
                  application={application}
                  isProcessing={processingId === application.applicationId}
                  key={application.applicationId}
                  onApprove={() => handleApprove(application)}
                  onOpenProfile={() => openMemberProfile(application.applicant.memberId)}
                  onReject={() => handleReject(application)}
                />
              ))
            )}
          </>
        ) : scheduleQuery.isLoading ? (
          <ActivityIndicator color={PRIMARY_COLOR} style={styles.loader} />
        ) : scheduleQuery.isError ? (
          <Text style={styles.errorText}>
            {extractErrorMessage(scheduleQuery.error, '일정을 불러오지 못했어요.')}
          </Text>
        ) : isEditingSchedule && canManageSchedule ? (
          <ScheduleForm
            endAt={scheduleEndAt}
            isEditMode={schedule !== null}
            isSubmitting={createScheduleMutation.isPending || updateScheduleMutation.isPending}
            meetingPlace={meetingPlace}
            onCancel={() => setIsEditingSchedule(false)}
            onChangeMeetingPlace={setMeetingPlace}
            onClearEndAt={() => setScheduleEndAt(null)}
            onMeetingPlaceBlur={handleMeetingPlaceBlur}
            onMeetingPlaceFocus={handleMeetingPlaceFocus}
            onOpenTimePicker={handleOpenPicker}
            onSelectDate={handleSelectScheduleDate}
            onSubmit={handleSubmitSchedule}
            showCancel
            startAt={scheduleStartAt}
          />
        ) : schedule ? (
          <ScheduleView
            canManage={canManageSchedule}
            isDeleting={deleteScheduleMutation.isPending}
            onDelete={handleDeleteSchedule}
            onEdit={startEditingSchedule}
            schedule={schedule}
          />
        ) : (
          <EmptyScheduleView canManage={canManageSchedule} onCreate={startEditingSchedule} />
        )}
        {/* 키보드 높이만큼 스크롤 여백을 확보해 모임 장소 입력이 키보드 위로 스크롤되게. */}
        <Animated.View style={keyboardSpacerStyle} />
      </ScrollView>

      {picker && (
        <TimeWheelModal
          onCancel={() => setPicker(null)}
          onConfirm={handleConfirmPicker}
          picker={picker}
        />
      )}
    </View>
  );
}

const styles = StyleSheet.create({
  screen: { flex: 1, backgroundColor: SURFACE_COLOR },

  headerRow: {
    flexDirection: 'row',
    alignItems: 'center',
    paddingHorizontal: 20,
    paddingBottom: 12,
  },
  headerIconButton: {
    width: 36,
    height: 36,
    borderRadius: 18,
    alignItems: 'center',
    justifyContent: 'center',
    backgroundColor: SOFT_GREEN_COLOR,
  },
  headerIconSpacer: {
    width: 36,
    height: 36,
  },
  headerTitle: {
    flex: 1,
    textAlign: 'center',
    fontSize: 15,
    fontWeight: '800',
    color: DARK_GREEN_COLOR,
  },

  closeRow: {
    flexDirection: 'row',
    alignItems: 'center',
    justifyContent: 'space-between',
    marginHorizontal: 20,
    marginBottom: 12,
    padding: 14,
    borderRadius: 16,
    backgroundColor: SOFT_GREEN_COLOR,
  },
  closeRowText: { fontSize: 13, fontWeight: '700', color: DARK_GREEN_COLOR },
  closeButton: {
    paddingHorizontal: 14,
    height: 34,
    borderRadius: 17,
    alignItems: 'center',
    justifyContent: 'center',
    backgroundColor: SURFACE_COLOR,
  },
  closeButtonText: { fontSize: 12.5, fontWeight: '700', color: DARK_GREEN_COLOR },

  tabRow: {
    flexDirection: 'row',
    marginHorizontal: 20,
    marginBottom: 8,
    borderRadius: 14,
    backgroundColor: SOFT_GREEN_COLOR,
    padding: 4,
  },
  tabButton: {
    flex: 1,
    height: 38,
    borderRadius: 10,
    alignItems: 'center',
    justifyContent: 'center',
  },
  tabButtonActive: { backgroundColor: SURFACE_COLOR },
  tabButtonText: { fontSize: 13, fontWeight: '700', color: MUTED_TEXT_COLOR },
  tabButtonTextActive: { color: DARK_GREEN_COLOR },

  scrollContent: { paddingHorizontal: 20, paddingTop: 8 },

  filterRow: { flexDirection: 'row', gap: 8, marginBottom: 12 },
  filterChip: {
    paddingHorizontal: 14,
    height: 32,
    borderRadius: 16,
    alignItems: 'center',
    justifyContent: 'center',
    borderWidth: 1,
    borderColor: BORDER_COLOR,
  },
  filterChipActive: { backgroundColor: DARK_GREEN_COLOR, borderColor: DARK_GREEN_COLOR },
  filterChipText: { fontSize: 12.5, fontWeight: '700', color: MUTED_TEXT_COLOR },
  filterChipTextActive: { color: SOFT_GREEN_COLOR },

  loader: { paddingVertical: 32 },
  errorText: { textAlign: 'center', paddingVertical: 24, fontSize: 13, color: MUTED_TEXT_COLOR },
  emptyText: { textAlign: 'center', paddingVertical: 24, fontSize: 13, color: MUTED_TEXT_COLOR },

  card: {
    marginBottom: 10,
    padding: 14,
    borderRadius: 16,
    borderWidth: 1,
    borderColor: BORDER_COLOR,
    backgroundColor: SURFACE_COLOR,
  },
  rowHeader: { flexDirection: 'row', alignItems: 'center', gap: 10 },
  profileLink: { flex: 1, minWidth: 0, flexDirection: 'row', alignItems: 'center', gap: 10 },
  avatar: { width: 36, height: 36, borderRadius: 18, backgroundColor: SOFT_GREEN_COLOR },
  rowHeaderText: { flex: 1 },
  nickname: { fontSize: 14, fontWeight: '800', color: TEXT_COLOR },
  metaText: { marginTop: 2, fontSize: 11.5, color: MUTED_TEXT_COLOR },
  introText: { marginTop: 10, fontSize: 13, lineHeight: 19, color: TEXT_COLOR },

  actionRow: { flexDirection: 'row', gap: 8, marginTop: 12 },
  rejectButton: {
    flex: 1,
    height: 38,
    borderRadius: 12,
    alignItems: 'center',
    justifyContent: 'center',
    borderWidth: 1,
    borderColor: BORDER_COLOR,
  },
  rejectButtonText: { fontSize: 13, fontWeight: '700', color: MUTED_TEXT_COLOR },
  approveButton: {
    flex: 1,
    height: 38,
    borderRadius: 12,
    alignItems: 'center',
    justifyContent: 'center',
    backgroundColor: DARK_GREEN_COLOR,
  },
  approveButtonText: { fontSize: 13, fontWeight: '700', color: SOFT_GREEN_COLOR },
  buttonDisabled: { opacity: 0.4 },
  decidedText: { marginTop: 10, fontSize: 12, fontWeight: '700', color: MUTED_TEXT_COLOR },

  scheduleSectionHeader: {
    marginBottom: 14,
    flexDirection: 'row',
    alignItems: 'flex-end',
    justifyContent: 'space-between',
  },
  scheduleSectionEyebrow: { fontSize: 11, fontWeight: '800', color: PRIMARY_COLOR },
  scheduleSectionTitle: {
    marginTop: 3,
    fontSize: 20,
    fontWeight: '900',
    color: DARK_GREEN_COLOR,
  },
  scheduleStatusBadge: {
    height: 30,
    paddingHorizontal: 11,
    flexDirection: 'row',
    alignItems: 'center',
    gap: 6,
    borderRadius: 15,
    backgroundColor: SOFT_GREEN_COLOR,
  },
  scheduleStatusDot: { width: 6, height: 6, borderRadius: 3, backgroundColor: PRIMARY_COLOR },
  scheduleStatusText: { fontSize: 11, fontWeight: '800', color: PRIMARY_COLOR },
  scheduleCard: {
    padding: 18,
    borderRadius: 24,
    borderWidth: 1,
    borderColor: 'rgba(46, 167, 107, 0.16)',
    backgroundColor: 'rgba(255, 255, 255, 0.94)',
    boxShadow: '0px 10px 24px rgba(32, 87, 58, 0.08)',
  },
  scheduleRow: { flexDirection: 'row', alignItems: 'center', gap: 16 },
  dateBadge: {
    width: 72,
    height: 82,
    borderRadius: 23,
    backgroundColor: SOFT_GREEN_COLOR,
    alignItems: 'center',
    justifyContent: 'center',
  },
  dateBadgeMonth: { fontSize: 10.5, fontWeight: '800', color: PRIMARY_COLOR },
  dateBadgeDay: { fontSize: 28, fontWeight: '900', color: DARK_GREEN_COLOR, lineHeight: 32 },
  dateBadgeWeekday: { fontSize: 10, fontWeight: '700', color: MUTED_TEXT_COLOR },
  scheduleInfo: { flex: 1 },
  scheduleInfoLabel: { fontSize: 10.5, fontWeight: '800', color: MUTED_TEXT_COLOR },
  scheduleTime: { marginTop: 4, fontSize: 18, fontWeight: '900', color: DARK_GREEN_COLOR },
  meetingPlaceRow: { flexDirection: 'row', alignItems: 'center', gap: 5, marginTop: 10 },
  meetingPlaceText: { flex: 1, fontSize: 12.5, fontWeight: '700', color: LABEL_COLOR },
  scheduleActions: { marginTop: 18, gap: 10 },
  scheduleEditButton: {
    height: 40,
    flexDirection: 'row',
    alignItems: 'center',
    justifyContent: 'center',
    gap: 6,
    borderRadius: 14,
    backgroundColor: SOFT_GREEN_COLOR,
  },
  scheduleEditButtonText: {
    fontSize: 14,
    fontWeight: '900',
    color: DARK_GREEN_COLOR,
  },
  scheduleDeleteButton: {
    height: 40,
    flexDirection: 'row',
    alignItems: 'center',
    justifyContent: 'center',
    gap: 6,
    borderRadius: 14,
    backgroundColor: '#FFF4F2',
  },
  scheduleDeleteButtonText: { fontSize: 14, fontWeight: '900', color: ERROR_COLOR },
  scheduleGuide: {
    marginTop: 12,
    paddingHorizontal: 12,
    paddingVertical: 10,
    flexDirection: 'row',
    alignItems: 'flex-start',
    gap: 7,
  },
  scheduleGuideText: { flex: 1, fontSize: 11, lineHeight: 17, color: MUTED_TEXT_COLOR },
  emptyScheduleCard: {
    paddingHorizontal: 24,
    paddingVertical: 30,
    alignItems: 'center',
    borderRadius: 24,
    borderWidth: 1,
    borderColor: 'rgba(46, 167, 107, 0.16)',
    backgroundColor: 'rgba(255, 255, 255, 0.94)',
    boxShadow: '0px 10px 24px rgba(32, 87, 58, 0.08)',
  },
  emptyScheduleIcon: {
    width: 58,
    height: 58,
    alignItems: 'center',
    justifyContent: 'center',
    borderRadius: 20,
    backgroundColor: SOFT_GREEN_COLOR,
  },
  emptyScheduleTitle: {
    marginTop: 16,
    fontSize: 17,
    fontWeight: '900',
    color: DARK_GREEN_COLOR,
  },
  emptyScheduleDescription: {
    maxWidth: 260,
    marginTop: 7,
    textAlign: 'center',
    fontSize: 12,
    lineHeight: 19,
    color: MUTED_TEXT_COLOR,
  },
  createScheduleButton: {
    width: '100%',
    height: 48,
    marginTop: 20,
    flexDirection: 'row',
    alignItems: 'center',
    justifyContent: 'center',
    gap: 7,
    borderRadius: 16,
    backgroundColor: PRIMARY_COLOR,
  },
  createScheduleButtonText: { fontSize: 14, fontWeight: '900', color: SURFACE_COLOR },

  scheduleFormCard: {
    padding: 18,
    borderRadius: 24,
    borderWidth: 1,
    borderColor: 'rgba(46, 167, 107, 0.16)',
    backgroundColor: 'rgba(255, 255, 255, 0.95)',
    boxShadow: '0px 10px 24px rgba(32, 87, 58, 0.08)',
  },
  scheduleFormHeader: { flexDirection: 'row', alignItems: 'center', gap: 12, marginBottom: 4 },
  scheduleFormIcon: {
    width: 44,
    height: 44,
    alignItems: 'center',
    justifyContent: 'center',
    borderRadius: 15,
    backgroundColor: SOFT_GREEN_COLOR,
  },
  scheduleFormHeadingCopy: { flex: 1 },
  scheduleFormTitle: { fontSize: 17, fontWeight: '900', color: DARK_GREEN_COLOR },
  scheduleFormDescription: {
    marginTop: 3,
    fontSize: 10.5,
    lineHeight: 16,
    color: MUTED_TEXT_COLOR,
  },
  formActionRow: { flexDirection: 'row', gap: 8, marginTop: 18 },

  inlineCalendar: {
    paddingHorizontal: 12,
    paddingTop: 12,
    paddingBottom: 10,
    borderRadius: 22,
    borderWidth: 1,
    borderColor: BORDER_COLOR,
    backgroundColor: 'rgba(250,252,250,0.94)',
  },
  inlineCalendarHeader: {
    height: 42,
    flexDirection: 'row',
    alignItems: 'center',
    justifyContent: 'space-between',
  },
  calendarMonthButton: {
    width: 34,
    height: 34,
    alignItems: 'center',
    justifyContent: 'center',
    borderRadius: 12,
    backgroundColor: SOFT_GREEN_COLOR,
  },
  calendarMonthTitle: { fontSize: 15, fontWeight: '900', color: DARK_GREEN_COLOR },
  calendarWeekRow: { marginTop: 4, marginBottom: 3, flexDirection: 'row' },
  calendarColumn: { width: '14.2857%', alignItems: 'center' },
  calendarWeekday: {
    paddingVertical: 7,
    fontSize: 12,
    fontWeight: '800',
    color: MUTED_TEXT_COLOR,
  },
  calendarGrid: { flexDirection: 'row', flexWrap: 'wrap' },
  calendarDayCell: {
    width: '14.2857%',
    height: 46,
    alignItems: 'center',
    justifyContent: 'center',
  },
  calendarDayCircle: {
    width: 38,
    height: 38,
    alignItems: 'center',
    justifyContent: 'center',
    borderRadius: 13,
  },
  calendarTodayCircle: { borderWidth: 1.5, borderColor: PRIMARY_COLOR },
  calendarSelectedCircle: { borderColor: DARK_GREEN_COLOR, backgroundColor: DARK_GREEN_COLOR },
  calendarDayText: { fontSize: 14, fontWeight: '800', color: TEXT_COLOR },
  calendarSundayText: { color: ERROR_COLOR },
  calendarSaturdayText: { color: CALENDAR_SATURDAY_COLOR },
  calendarTodayText: { color: PRIMARY_COLOR, fontWeight: '900' },
  calendarSelectedText: { color: BUTTON_BACKGROUND_COLOR },
  timePickerRow: { flexDirection: 'row', alignItems: 'center', gap: 8 },
  timePickerButton: {
    flex: 1,
    minHeight: 68,
    paddingHorizontal: 12,
    paddingVertical: 10,
    justifyContent: 'center',
    borderRadius: 16,
    backgroundColor: SUBTLE_BACKGROUND_COLOR,
  },
  timePickerButtonDisabled: { opacity: 0.55 },
  timePickerLabel: { fontSize: 10.5, fontWeight: '800', color: MUTED_TEXT_COLOR },
  timePickerValueRow: { marginTop: 7, flexDirection: 'row', alignItems: 'center', gap: 5 },
  timePickerValue: { fontSize: 14, fontWeight: '900', color: DARK_GREEN_COLOR },
  timePickerPlaceholder: { fontSize: 12, fontWeight: '700', color: PLACEHOLDER_COLOR },
  timeModalScrim: {
    flex: 1,
    paddingHorizontal: 22,
    alignItems: 'center',
    justifyContent: 'center',
    backgroundColor: 'rgba(12, 28, 22, 0.42)',
  },
  timeModalCard: {
    width: '100%',
    maxWidth: 380,
    padding: 18,
    borderRadius: 30,
    borderWidth: 1,
    borderColor: 'rgba(255,255,255,0.84)',
    backgroundColor: 'rgba(249,252,250,0.98)',
    boxShadow: '0px 20px 48px rgba(13, 49, 34, 0.22)',
  },
  timeModalHeader: {
    marginBottom: 14,
    flexDirection: 'row',
    alignItems: 'center',
    justifyContent: 'space-between',
  },
  timeModalEyebrow: { fontSize: 11, fontWeight: '800', color: PRIMARY_COLOR },
  timeModalTitle: { marginTop: 3, fontSize: 18, fontWeight: '900', color: DARK_GREEN_COLOR },
  timeModalClockIcon: {
    width: 42,
    height: 42,
    alignItems: 'center',
    justifyContent: 'center',
    borderRadius: 15,
    backgroundColor: SOFT_GREEN_COLOR,
  },
  timeWheelPanel: {
    height: TIME_WHEEL_HEIGHT,
    flexDirection: 'row',
    overflow: 'hidden',
    borderRadius: 22,
    borderWidth: 1,
    borderColor: BORDER_COLOR,
    backgroundColor: 'rgba(244,248,245,0.96)',
    // 세 칸을 똑같이 나누면 글자 폭 차이 때문에 좌우 여백이 어긋나 보입니다.
    // '오전/오후'(한글 두 자)가 '10'·'00'보다 넓어서 오른쪽 끝에 붙는데, 그 차이의
    // 절반만큼 오른쪽을 밀어 주면 맨 왼쪽 숫자의 여백과 맞아떨어집니다.
    paddingRight: 14,
  },
  timeWheelColumn: { zIndex: 2, flex: 1 },
  timeWheelColumnContent: { paddingVertical: TIME_WHEEL_PADDING },
  timeWheelItem: {
    height: TIME_WHEEL_ITEM_HEIGHT,
    alignItems: 'center',
    justifyContent: 'center',
  },
  timeWheelItemText: { fontSize: 20, fontWeight: '600', color: 'rgba(77, 94, 86, 0.38)' },
  timeWheelItemTextSelected: { fontSize: 24, fontWeight: '900', color: DARK_GREEN_COLOR },
  timeWheelSelection: {
    position: 'absolute',
    zIndex: 1,
    top: TIME_WHEEL_PADDING,
    right: 10,
    left: 10,
    height: TIME_WHEEL_ITEM_HEIGHT,
    borderRadius: 14,
    backgroundColor: 'rgba(198, 249, 210, 0.52)',
  },
  timeWheelTopFade: {
    position: 'absolute',
    zIndex: 3,
    top: 0,
    right: 0,
    left: 0,
    height: 32,
    backgroundColor: 'rgba(244,248,245,0.72)',
  },
  timeWheelBottomFade: {
    position: 'absolute',
    zIndex: 3,
    right: 0,
    bottom: 0,
    left: 0,
    height: 32,
    backgroundColor: 'rgba(244,248,245,0.72)',
  },
  timeModalActions: { marginTop: 12, flexDirection: 'row', gap: 9 },
  timeModalCancelButton: {
    flex: 1,
    height: 50,
    alignItems: 'center',
    justifyContent: 'center',
    borderRadius: 17,
    borderWidth: 1,
    borderColor: BORDER_COLOR,
    backgroundColor: SURFACE_COLOR,
  },
  timeModalCancelText: { fontSize: 14, fontWeight: '800', color: MUTED_TEXT_COLOR },
  timeModalConfirmButton: {
    flex: 1.45,
    height: 50,
    alignItems: 'center',
    justifyContent: 'center',
    borderRadius: 17,
    backgroundColor: PRIMARY_COLOR,
  },
  timeModalConfirmText: { fontSize: 14, fontWeight: '900', color: SURFACE_COLOR },

  fieldLabel: {
    marginTop: 14,
    marginBottom: 8,
    fontSize: 13,
    fontWeight: '700',
    color: LABEL_COLOR,
  },
  labelWithAction: {
    flexDirection: 'row',
    justifyContent: 'space-between',
    alignItems: 'center',
    marginTop: 14,
    marginBottom: 8,
  },
  clearLinkText: { fontSize: 12, fontWeight: '700', color: PRIMARY_COLOR },
  textInput: {
    minHeight: 48,
    paddingHorizontal: 14,
    paddingVertical: 12,
    borderRadius: 12,
    backgroundColor: SUBTLE_BACKGROUND_COLOR,
    color: TEXT_COLOR,
    fontSize: 14,
  },

  pressed: { opacity: 0.82 },
});
