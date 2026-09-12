import Ionicons from '@expo/vector-icons/Ionicons';
import { useInfiniteQuery } from '@tanstack/react-query';
import { usePathname, useRouter } from 'expo-router';
import { useCallback, useEffect, useMemo, useState } from 'react';
import {
  ActivityIndicator,
  AppState,
  Modal,
  Pressable,
  ScrollView,
  StyleSheet,
  Text,
  View,
} from 'react-native';
import { useSafeAreaInsets } from 'react-native-safe-area-context';

import { appAlert } from '@/components/AppDialog';
import { GlassSurface } from '@/components/GlassSurface';
import {
  BORDER_COLOR,
  DARK_GREEN_COLOR,
  ERROR_COLOR,
  LABEL_COLOR,
  MODAL_SCRIM_COLOR,
  MUTED_TEXT_COLOR,
  PRIMARY_COLOR,
  SOFT_BACKGROUND_COLOR,
  SOFT_GREEN_COLOR,
  SURFACE_COLOR,
  TEXT_COLOR,
} from '@/constants/colors';
import { formatDateTime } from '@/features/apartment/format';
import { getFieldVisitStatus } from '@/features/checklist/api/fieldVisit';
import {
  resolveOngoingFieldVisitSelection,
  selectOngoingFieldVisitStudies,
} from '@/features/field/activeFieldVisitReturn';
import { getMyInProgressStudies, type MyStudy } from '@/features/member/api/myPage';
import { AuthSessionChangedError, AuthSessionExpiredError } from '@/lib/authenticatedFetch';
import { useAuthStore } from '@/store/authStore';

const ONGOING_STUDIES_PAGE_SIZE = 20;

// 이 버튼은 전역 오버레이라 기본적으로 화면 최상단(insets.top + 10)에 뜬다.
const RETURN_BUTTON_BASE_TOP_OFFSET = 10;

/**
 * 상단에 전체 폭 요소(예: 지도 검색바 블록)가 있는 화면에서는 그 요소를 가리지 않도록
 * 버튼을 그 아래로 내린다. 여기에 등록된 경로만 추가 오프셋을 적용하고, 그 외 화면은
 * 기본 위치를 유지한다.
 */
const RETURN_BUTTON_EXTRA_TOP_OFFSET_BY_ROUTE: Record<string, number> = {
  // 지도 상단 검색바(높이 46, 시작 top+12) 바로 아래에 작은 간격만 두고 내린다.
  // (검색바 아래 범위 칩은 좌·우 정렬이라, 짧은 중앙 핀은 그 사이 빈 공간에 들어간다.)
  '/map': 58,
};

function returnButtonExtraTopOffset(pathname: string): number {
  for (const [route, offset] of Object.entries(RETURN_BUTTON_EXTRA_TOP_OFFSET_BY_ROUTE)) {
    if (pathname === route || pathname.endsWith(route)) return offset;
  }
  return 0;
}

const ongoingFieldVisitStudiesQueryKey = (sessionVersion: number) =>
  ['field-visit', 'return-studies', sessionVersion] as const;

type OngoingStudyListItem = MyStudy & { apartmentName: string };

type OngoingStudyCardProps = {
  disabled: boolean;
  isSelecting: boolean;
  onPress: () => void;
  study: OngoingStudyListItem;
};

function OngoingStudyCard({ disabled, isSelecting, onPress, study }: OngoingStudyCardProps) {
  return (
    <Pressable
      accessibilityHint="선택한 스터디의 최신 임장 상태를 확인한 뒤 이동합니다."
      accessibilityLabel={`${study.title}, ${study.apartment.name} 임장으로 돌아가기`}
      accessibilityRole="button"
      disabled={disabled}
      onPress={onPress}
      style={({ pressed }) => [
        styles.studyCard,
        pressed && styles.pressed,
        disabled && !isSelecting && styles.disabled,
      ]}
    >
      <View style={styles.studyCardHeader}>
        <View style={styles.activeBadge}>
          <View style={styles.activeBadgeDot} />
          <Text style={styles.activeBadgeText}>임장 진행중</Text>
        </View>
        {study.role === 'LEADER' ? <Text style={styles.roleText}>스터디장</Text> : null}
      </View>

      <Text numberOfLines={1} style={styles.studyTitle}>
        {study.title}
      </Text>
      <View style={styles.studyMetaRow}>
        <Ionicons color={MUTED_TEXT_COLOR} name="business-outline" size={15} />
        <Text numberOfLines={1} style={styles.studyMetaText}>
          {study.apartment.name}
        </Text>
      </View>
      <View style={styles.studyMetaRow}>
        <Ionicons color={MUTED_TEXT_COLOR} name="time-outline" size={15} />
        <Text numberOfLines={1} style={styles.studyMetaText}>
          {study.nextSchedule === null
            ? '현재 임장이 진행 중이에요.'
            : formatDateTime(study.nextSchedule.startAt)}
        </Text>
        {isSelecting ? (
          <ActivityIndicator color={PRIMARY_COLOR} size="small" />
        ) : (
          <Ionicons color={DARK_GREEN_COLOR} name="chevron-forward" size={18} />
        )}
      </View>
    </Pressable>
  );
}

export function ActiveFieldVisitReturnButton() {
  const router = useRouter();
  const pathname = usePathname();
  const insets = useSafeAreaInsets();
  const authStatus = useAuthStore((state) => state.status);
  const sessionVersion = useAuthStore((state) => state.sessionVersion);
  const [openSessionVersion, setOpenSessionVersion] = useState<number | null>(null);
  const [selectedStudyId, setSelectedStudyId] = useState<number | null>(null);
  const isOpen = authStatus === 'authenticated' && openSessionVersion === sessionVersion;

  const studiesQuery = useInfiniteQuery({
    queryKey: ongoingFieldVisitStudiesQueryKey(sessionVersion),
    queryFn: ({ pageParam }) => getMyInProgressStudies(pageParam, ONGOING_STUDIES_PAGE_SIZE),
    enabled: authStatus === 'authenticated',
    initialPageParam: 0,
    getNextPageParam: (lastPage) =>
      lastPage.page + 1 < lastPage.totalPages ? lastPage.page + 1 : undefined,
  });

  const studies = useMemo(
    () =>
      selectOngoingFieldVisitStudies(
        studiesQuery.data?.pages.flatMap((page) =>
          page.content.map((study) => ({
            ...study,
            apartmentName: study.apartment.name,
          })),
        ) ?? [],
      ),
    [studiesQuery.data],
  );
  // 배지·라벨 숫자는 실제로 돌아갈 수 있는(hasReturnableFieldVisit 필터된) 스터디 수와 맞춘다.
  // 서버 totalElements는 필터 이전 값이라 아래 목록 개수와 어긋난다.
  const returnableCount = studies.length;
  const hasTrustedInitialResult =
    studiesQuery.data !== undefined && !(studiesQuery.isError && studies.length === 0);
  // 대기 화면 자체가 "돌아갈 임장"이라, 그 화면 안에서는 이 알약이 중복 안내라 숨깁니다.
  const isOnWaitingScreen = pathname.includes('field-visit-waiting');
  // 임장 지도(field/[sessionId])는 이미 그 임장을 진행 중인 화면이라 "임장 계속하기"가
  // 중복이므로 숨깁니다. ('/field/'는 field-visit-waiting('/field-visit-waiting/')과 안 겹침)
  const isOnFieldVisitScreen = pathname.includes('/field/');
  const canShowButton =
    authStatus === 'authenticated' &&
    hasTrustedInitialResult &&
    studies.length > 0 &&
    !isOnWaitingScreen &&
    !isOnFieldVisitScreen;
  const refetchStudies = studiesQuery.refetch;

  const refresh = useCallback(() => {
    if (authStatus === 'authenticated') void refetchStudies();
  }, [authStatus, refetchStudies]);

  useEffect(() => {
    refresh();
  }, [pathname, refresh]);

  useEffect(() => {
    const subscription = AppState.addEventListener('change', (state) => {
      if (state === 'active') refresh();
    });

    return () => subscription.remove();
  }, [refresh]);

  const closeSheet = useCallback(() => {
    if (selectedStudyId === null) setOpenSessionVersion(null);
  }, [selectedStudyId]);

  const openStudyDetail = useCallback(
    (studyId: number) => {
      router.push({
        pathname: '/(app)/study/[id]',
        params: { id: String(studyId) },
      });
    },
    [router],
  );

  const handleStudyPress = useCallback(
    async (study: OngoingStudyListItem) => {
      if (selectedStudyId !== null) return;
      setSelectedStudyId(study.studyId);

      try {
        const latestStatus = await getFieldVisitStatus(study.studyId);
        const selection = resolveOngoingFieldVisitSelection(study, latestStatus);

        if (selection.kind === 'OPEN_FIELD_VISIT') {
          setOpenSessionVersion(null);
          router.navigate({
            pathname: '/(app)/field/[sessionId]',
            params: {
              sessionId: String(selection.sessionId),
              studyId: String(selection.studyId),
              apartmentId: String(study.apartment.apartmentId),
              ...(selection.apartmentName ? { apartmentName: selection.apartmentName } : {}),
            },
          });
          return;
        }

        if (selection.kind === 'OPEN_STUDY') {
          setOpenSessionVersion(null);
          appAlert(
            '임장 참여가 필요해요',
            '스터디 화면에서 기존 임장 참여 절차를 진행해 주세요.',
            [
              {
                text: '스터디로 이동',
                onPress: () => openStudyDetail(selection.studyId),
              },
            ],
            { cancelable: false },
          );
          return;
        }

        appAlert('임장 상태가 변경됐어요', '진행중인 스터디 목록을 새로 확인할게요.');
        await refetchStudies();
      } catch (error) {
        if (error instanceof AuthSessionExpiredError || error instanceof AuthSessionChangedError) {
          return;
        }

        appAlert('임장 상태를 확인하지 못했어요', '잠시 후 다시 시도해 주세요.');
      } finally {
        setSelectedStudyId(null);
      }
    },
    [openStudyDetail, refetchStudies, router, selectedStudyId],
  );

  if (!canShowButton && !isOpen) return null;

  const isInitialLoading = studiesQuery.isPending && studiesQuery.data === undefined;
  const isInitialError = studiesQuery.isError && studiesQuery.data === undefined;

  return (
    <>
      {canShowButton ? (
        <View
          pointerEvents="box-none"
          style={[
            styles.overlay,
            { top: insets.top + RETURN_BUTTON_BASE_TOP_OFFSET + returnButtonExtraTopOffset(pathname) },
          ]}
        >
          <Pressable
            accessibilityHint="진행중인 스터디 목록을 엽니다."
            accessibilityLabel={`진행 중인 임장 ${returnableCount}개, 임장 계속하기`}
            accessibilityRole="button"
            hitSlop={8}
            onPress={() => setOpenSessionVersion(sessionVersion)}
            style={({ pressed }) => [styles.button, pressed && styles.pressed]}
          >
            <GlassSurface
              blurTint="light"
              intensity={28}
              radius={22}
              tint="#DDF6E9"
              tintOpacity={0.82}
            />
            <View style={styles.statusDot} />
            <Text style={styles.label}>임장</Text>
            <Text style={styles.action}>계속하기</Text>
            <Ionicons color={DARK_GREEN_COLOR} name="chevron-down" size={16} />
          </Pressable>
        </View>
      ) : null}

      <Modal
        animationType="slide"
        onRequestClose={closeSheet}
        statusBarTranslucent
        transparent
        visible={isOpen}
      >
        <View style={styles.modalRoot}>
          <Pressable
            accessibilityLabel="진행중인 스터디 목록 닫기"
            accessibilityRole="button"
            disabled={selectedStudyId !== null}
            onPress={closeSheet}
            style={styles.scrim}
          />
          <View
            accessibilityViewIsModal
            style={[styles.sheet, { paddingBottom: Math.max(18, insets.bottom + 12) }]}
          >
            <View style={styles.handle} />
            <View style={styles.sheetHeader}>
              <View style={styles.sheetTitleWrap}>
                <Text style={styles.sheetEyebrow}>돌아갈 임장을 선택해 주세요</Text>
                <View style={styles.sheetTitleRow}>
                  <Text style={styles.sheetTitle}>진행중인 스터디</Text>
                  <View style={styles.countBadge}>
                    <Text style={styles.countBadgeText}>{returnableCount}</Text>
                  </View>
                  {studiesQuery.isRefetching && !studiesQuery.isFetchingNextPage ? (
                    <ActivityIndicator color={PRIMARY_COLOR} size="small" />
                  ) : null}
                </View>
              </View>
              <Pressable
                accessibilityLabel="진행중인 스터디 목록 닫기"
                accessibilityRole="button"
                disabled={selectedStudyId !== null}
                hitSlop={8}
                onPress={closeSheet}
                style={({ pressed }) => [
                  styles.closeButton,
                  pressed && styles.pressed,
                  selectedStudyId !== null && styles.disabled,
                ]}
              >
                <Ionicons color={DARK_GREEN_COLOR} name="close" size={23} />
              </Pressable>
            </View>

            {isInitialLoading ? (
              <View style={styles.stateArea}>
                <ActivityIndicator color={PRIMARY_COLOR} />
                <Text style={styles.stateText}>진행중인 스터디를 불러오고 있어요.</Text>
              </View>
            ) : isInitialError ? (
              <View style={styles.stateArea}>
                <Ionicons color={ERROR_COLOR} name="alert-circle-outline" size={28} />
                <Text style={styles.stateText}>스터디 목록을 불러오지 못했습니다.</Text>
                <Pressable
                  accessibilityRole="button"
                  onPress={() => void refetchStudies()}
                  style={({ pressed }) => [styles.retryButton, pressed && styles.pressed]}
                >
                  <Text style={styles.retryText}>다시 시도</Text>
                </Pressable>
              </View>
            ) : studies.length === 0 ? (
              <View style={styles.stateArea}>
                <Ionicons color={MUTED_TEXT_COLOR} name="walk-outline" size={29} />
                <Text style={styles.stateText}>현재 진행중인 스터디가 없습니다.</Text>
              </View>
            ) : (
              <ScrollView
                contentContainerStyle={styles.listContent}
                keyboardShouldPersistTaps="handled"
                showsVerticalScrollIndicator={false}
                style={styles.list}
              >
                {studiesQuery.isRefetchError ? (
                  <View style={styles.inlineError}>
                    <Ionicons color={ERROR_COLOR} name="alert-circle-outline" size={16} />
                    <Text style={styles.inlineErrorText}>
                      최신 목록을 확인하지 못했어요. 기존 목록을 표시합니다.
                    </Text>
                  </View>
                ) : null}

                {studies.map((study) => (
                  <OngoingStudyCard
                    key={study.studyId}
                    disabled={selectedStudyId !== null}
                    isSelecting={selectedStudyId === study.studyId}
                    onPress={() => void handleStudyPress(study)}
                    study={study}
                  />
                ))}

                {studiesQuery.hasNextPage ? (
                  <View style={styles.loadMoreArea}>
                    {studiesQuery.isFetchNextPageError ? (
                      <Text style={styles.loadMoreError}>추가 목록을 불러오지 못했어요.</Text>
                    ) : null}
                    <Pressable
                      accessibilityLabel="진행중인 스터디 더 불러오기"
                      accessibilityRole="button"
                      disabled={studiesQuery.isFetchingNextPage || selectedStudyId !== null}
                      onPress={() => void studiesQuery.fetchNextPage()}
                      style={({ pressed }) => [
                        styles.loadMoreButton,
                        pressed && styles.pressed,
                        (studiesQuery.isFetchingNextPage || selectedStudyId !== null) &&
                          styles.disabled,
                      ]}
                    >
                      {studiesQuery.isFetchingNextPage ? (
                        <ActivityIndicator color={PRIMARY_COLOR} size="small" />
                      ) : (
                        <Text style={styles.loadMoreText}>
                          {studiesQuery.isFetchNextPageError ? '다시 시도' : '더 보기'}
                        </Text>
                      )}
                    </Pressable>
                  </View>
                ) : null}
              </ScrollView>
            )}
          </View>
        </View>
      </Modal>
    </>
  );
}

const styles = StyleSheet.create({
  overlay: {
    position: 'absolute',
    left: 0,
    right: 0,
    zIndex: 1000,
    elevation: 1000,
    alignItems: 'center',
  },
  button: {
    minHeight: 44,
    borderRadius: 22,
    paddingHorizontal: 16,
    flexDirection: 'row',
    alignItems: 'center',
    gap: 7,
    boxShadow: '0px 4px 14px rgba(16, 39, 30, 0.18)',
  },
  pressed: {
    opacity: 0.72,
    transform: [{ scale: 0.98 }],
  },
  disabled: { opacity: 0.48 },
  statusDot: {
    width: 8,
    height: 8,
    borderRadius: 4,
    backgroundColor: PRIMARY_COLOR,
  },
  label: {
    color: DARK_GREEN_COLOR,
    fontFamily: 'IBMPlexSansKR_700Bold',
    fontSize: 13,
  },
  action: {
    color: PRIMARY_COLOR,
    fontFamily: 'IBMPlexSansKR_600SemiBold',
    fontSize: 12,
  },
  modalRoot: { flex: 1, justifyContent: 'flex-end' },
  scrim: {
    position: 'absolute',
    top: 0,
    right: 0,
    bottom: 0,
    left: 0,
    backgroundColor: MODAL_SCRIM_COLOR,
  },
  sheet: {
    maxHeight: '78%',
    minHeight: 330,
    overflow: 'hidden',
    borderTopLeftRadius: 28,
    borderTopRightRadius: 28,
    backgroundColor: SURFACE_COLOR,
    shadowColor: DARK_GREEN_COLOR,
    shadowOffset: { width: 0, height: -8 },
    shadowOpacity: 0.18,
    shadowRadius: 22,
    elevation: 16,
  },
  handle: {
    alignSelf: 'center',
    width: 42,
    height: 4,
    marginTop: 10,
    borderRadius: 2,
    backgroundColor: BORDER_COLOR,
  },
  sheetHeader: {
    flexDirection: 'row',
    alignItems: 'center',
    gap: 12,
    paddingTop: 13,
    paddingRight: 16,
    paddingBottom: 15,
    paddingLeft: 20,
    borderBottomWidth: 1,
    borderBottomColor: BORDER_COLOR,
  },
  sheetTitleWrap: { minWidth: 0, flex: 1 },
  sheetEyebrow: {
    fontSize: 11,
    lineHeight: 16,
    fontWeight: '600',
    color: MUTED_TEXT_COLOR,
  },
  sheetTitleRow: { flexDirection: 'row', alignItems: 'center', gap: 7 },
  sheetTitle: {
    flexShrink: 1,
    fontSize: 21,
    lineHeight: 28,
    fontWeight: '900',
    color: DARK_GREEN_COLOR,
    letterSpacing: -0.8,
  },
  countBadge: {
    minWidth: 25,
    height: 25,
    alignItems: 'center',
    justifyContent: 'center',
    paddingHorizontal: 7,
    borderRadius: 13,
    backgroundColor: SOFT_GREEN_COLOR,
  },
  countBadgeText: { fontSize: 12, fontWeight: '800', color: PRIMARY_COLOR },
  closeButton: {
    width: 44,
    height: 44,
    alignItems: 'center',
    justifyContent: 'center',
    borderRadius: 22,
    backgroundColor: SOFT_BACKGROUND_COLOR,
  },
  stateArea: {
    minHeight: 230,
    alignItems: 'center',
    justifyContent: 'center',
    gap: 12,
    paddingHorizontal: 28,
  },
  stateText: {
    fontSize: 14,
    lineHeight: 20,
    color: MUTED_TEXT_COLOR,
    textAlign: 'center',
  },
  retryButton: {
    minHeight: 44,
    justifyContent: 'center',
    paddingHorizontal: 20,
    borderRadius: 22,
    backgroundColor: SOFT_GREEN_COLOR,
  },
  retryText: { fontSize: 14, fontWeight: '800', color: PRIMARY_COLOR },
  list: { flexShrink: 1 },
  listContent: { gap: 10, padding: 16, paddingBottom: 8 },
  inlineError: {
    minHeight: 44,
    flexDirection: 'row',
    alignItems: 'center',
    gap: 7,
    paddingHorizontal: 12,
    borderRadius: 14,
    backgroundColor: '#FFF6F5',
  },
  inlineErrorText: { flex: 1, fontSize: 11, lineHeight: 16, color: ERROR_COLOR },
  studyCard: {
    gap: 9,
    padding: 16,
    borderRadius: 18,
    borderWidth: 1,
    borderColor: BORDER_COLOR,
    backgroundColor: SURFACE_COLOR,
    shadowColor: DARK_GREEN_COLOR,
    shadowOffset: { width: 0, height: 4 },
    shadowOpacity: 0.05,
    shadowRadius: 10,
    elevation: 1,
  },
  studyCardHeader: {
    flexDirection: 'row',
    alignItems: 'center',
    justifyContent: 'space-between',
    gap: 8,
  },
  activeBadge: {
    flexDirection: 'row',
    alignItems: 'center',
    gap: 5,
    paddingHorizontal: 8,
    paddingVertical: 4,
    borderRadius: 10,
    backgroundColor: SOFT_GREEN_COLOR,
  },
  activeBadgeDot: {
    width: 6,
    height: 6,
    borderRadius: 3,
    backgroundColor: PRIMARY_COLOR,
  },
  activeBadgeText: { fontSize: 10, lineHeight: 14, fontWeight: '800', color: PRIMARY_COLOR },
  roleText: { fontSize: 11, fontWeight: '700', color: MUTED_TEXT_COLOR },
  studyTitle: {
    fontSize: 16,
    lineHeight: 22,
    fontWeight: '800',
    color: TEXT_COLOR,
    letterSpacing: -0.5,
  },
  studyMetaRow: {
    minWidth: 0,
    flexDirection: 'row',
    alignItems: 'center',
    gap: 6,
  },
  studyMetaText: { flex: 1, fontSize: 12, lineHeight: 17, color: LABEL_COLOR },
  loadMoreArea: { alignItems: 'center', gap: 6, paddingTop: 2 },
  loadMoreError: { fontSize: 11, lineHeight: 16, color: ERROR_COLOR },
  loadMoreButton: {
    minWidth: 112,
    minHeight: 44,
    alignItems: 'center',
    justifyContent: 'center',
    paddingHorizontal: 18,
    borderRadius: 22,
    backgroundColor: SOFT_GREEN_COLOR,
  },
  loadMoreText: { fontSize: 13, fontWeight: '800', color: PRIMARY_COLOR },
});
