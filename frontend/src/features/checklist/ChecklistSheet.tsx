import BottomSheet, { BottomSheetScrollView, useBottomSheetInternal } from '@gorhom/bottom-sheet';
import Ionicons from '@expo/vector-icons/Ionicons';
import * as Crypto from 'expo-crypto';
import { useFocusEffect, useRouter } from 'expo-router';
import { useCallback, useEffect, useMemo, useRef, useState } from 'react';
import { ActivityIndicator, Keyboard, Modal, Pressable, StyleSheet, Text, View } from 'react-native';
import Animated, {
  runOnJS,
  useAnimatedKeyboard,
  useAnimatedReaction,
  useAnimatedStyle,
} from 'react-native-reanimated';

import { appAlert } from '@/components/AppDialog';
import { createSheetGlassBackground } from '@/components/SheetGlassBackground';
import { SheetHandleIconBadge } from '@/components/SheetHandleIconBadge';
import { INACTIVE_COLOR as TAB_LABEL_COLOR, TAB_LABEL_FONT_BOLD } from '@/components/TabIcon';
import {
  BORDER_COLOR,
  MUTED_TEXT_COLOR,
  PRIMARY_COLOR,
  SOFT_GREEN_COLOR,
} from '@/constants/colors';
import { ChecklistListView } from '@/features/checklist/ChecklistListView';
import { finishFieldVisit, FieldVisitApiError } from '@/features/checklist/api/fieldVisit';
import { RecordComposerView } from '@/features/checklist/RecordComposerView';
import { createTextRecord, FieldRecordApiError } from '@/features/checklist/api/fieldRecords';
import { savePendingTextDrafts } from '@/features/checklist/savePendingTextDrafts';
import { useChecklist } from '@/features/checklist/useChecklist';
import { useCloseVote } from '@/features/checklist/useCloseVote';
import { useFieldVisitWaitingReport } from '@/features/checklist/useFieldVisitWaitingReport';
import type { FieldVisitRoute } from '@/features/checklist/api/fieldVisitRoute';
import { stopAndFinishFieldTrack } from '@/features/field/fieldTrackRunner';
import { GenerationSpinnerScreen } from '@/features/generation/GenerationSpinnerScreen';

/**
 * 시트가 멈추는 지점(창 높이 기준). 지도 탭(map.tsx)과 같은 3단계 구성입니다.
 * 접힘(핸들만) → 55% → 창 전체.
 */
const SHEET_SNAP_POINTS: (string | number)[] = [92, '55%', '100%'];
const SHEET_EXPANDED_INDEX = SHEET_SNAP_POINTS.length - 1;
/** 시트 본문 아래 기본 여백. 잘려 나간 만큼을 여기에 더해 스크롤 여지를 만듭니다. */
const SCROLL_CONTENT_BOTTOM_PADDING = 24;

/** 키보드와 시트 바닥이 붙어 보이지 않도록 두는 간격. */
const SHEET_KEYBOARD_GAP = 10;

/** 창(sheetWindow)·카드(sheetBackground)·유리 배경이 다 같은 모서리를 쓰도록 한곳에. */
const SHEET_RADIUS = 28;
/** 지도 컨트롤(25)과 챗봇 FAB(40)보다 위에서 시트가 겹친 영역의 터치를 받습니다. */
const SHEET_LAYER_Z_INDEX = 50;

/**
 * 시트 배경(유리 → 그라디언트 전환)은 지도 탭과 함께 쓰는 공용 컴포넌트입니다
 * (SheetGlassBackground.tsx). 렌더마다 새로 만들지 않도록 모듈 스코프에서 한 번만
 * 생성합니다.
 */
const ChecklistSheetBackground = createSheetGlassBackground(SHEET_RADIUS, SHEET_EXPANDED_INDEX);

interface ChecklistSheetProps {
  studyId: number;
  /** 시트가 놓일 창의 위쪽 경계(px). */
  windowTop: number;
  windowBottom: number;
  /**
   * 팔방이 추천 경로. 지도(경유지 점 마커)와 이 시트(항목 묶음)가 같은 값을 봐야
   * 순서가 어긋나지 않으므로, 조회는 화면에서 한 번만 하고 여기로 내려받습니다.
   */
  route: FieldVisitRoute | null;
  isGeneratingRoute: boolean;
  onGenerateRoute: () => void;
  /**
   * 지도에서 탭한 경유지. 값이 있으면 시트를 펼쳐 그 경유지의 항목 목록만 보여 줍니다.
   * 뒤로가기·시트 접힘 때 null 로 되돌리도록 onFocusedWaypointChange 를 부릅니다.
   */
  focusedWaypointId: number | null;
  onFocusedWaypointChange: (waypointId: number | null) => void;
  /** 항목 완료가 바뀌면 화면이 refreshRoute 로 점 마커 진행도를 다시 불러오게 알립니다. */
  onItemCompletionChange: () => void;
}

export function ChecklistSheet({
  studyId,
  windowTop,
  windowBottom,
  route,
  isGeneratingRoute,
  onGenerateRoute,
  focusedWaypointId,
  onFocusedWaypointChange,
  onItemCompletionChange,
}: ChecklistSheetProps) {
  const sheetRef = useRef<BottomSheet>(null);
  const router = useRouter();
  const [isExpanded, setIsExpanded] = useState(false);
  const [openItemId, setOpenItemId] = useState<number | null>(null);
  const [recordDrafts, setRecordDrafts] = useState<Record<number, string>>({});
  const isFinishingRef = useRef(false);

  // edge-to-edge Android에서는 adjustResize만으로 절대 배치된 시트 창이 줄어들지
  // 않습니다. 지도 시트(map.tsx)와 같이 UI 스레드에서 키보드 높이를 따라 bottom만
  // 옮겨, 화면 위쪽 경계는 그대로 두고 시트 바닥을 키보드 위에 붙입니다.
  const keyboard = useAnimatedKeyboard();
  // 키보드 높이는 '기록 입력창이 실제로 포커스일 때'만 반영합니다. RecordComposerView 가
  // 카메라·사진 선택(별도 네이티브 액티비티)으로 전환되면 안드로이드에서 close inset
  // 애니메이션 콜백이 유실돼 keyboard.state/height 가 OPEN·CLOSING에 stuck될 수 있는데
  // (글 작성 create.tsx와 동일 원인), 포커스는 JS에서 직접 제어하므로 게이트가 내려가
  // 있으면 stuck된 값이 시트를 밀어 올리지 못합니다. 게이트는 RecordComposerView 의
  // onInputFocusChange(onFocus/onBlur·피커 진입)로 갱신합니다.
  const [isInputFocused, setIsInputFocused] = useState(false);
  const keyboardAwareSheetStyle = useAnimatedStyle(() => ({
    bottom:
      isInputFocused && keyboard.height.value > 0
        ? keyboard.height.value + SHEET_KEYBOARD_GAP
        : windowBottom,
  }));

  // 키보드가 열린 채 이 화면을 벗어나면(뒤로가기 등) useAnimatedKeyboard 의 height 가
  // 0으로 리셋되지 않아, 복귀 시 시트가 키보드 높이만큼 올라간 채 남는 문제가 있었다.
  // 화면을 벗어날 때 키보드를 명시적으로 닫아 height 를 0으로 되돌린다.
  useFocusEffect(
    useCallback(() => {
      return () => {
        Keyboard.dismiss();
        // 화면을 벗어나면 포커스 게이트도 함께 내려, 복귀 시 stuck된 키보드 값이
        // 시트를 밀어 올린 채 남지 않게 합니다.
        setIsInputFocused(false);
      };
    }, []),
  );

  const {
    loadState,
    isGenerating,
    generationProgress,
    generationMessage,
    errorMessage,
    canRetry,
    categories,
    completedCount,
    totalCount,
    readOnly,
    pendingItemIds,
    reload,
    refreshItemRecordCount,
    toggleItem,
  } = useChecklist(studyId);

  const { closeVote } = useCloseVote(studyId);

  // 내 종료로 세션까지 끝난 경우(혼자 참여했거나 내가 마지막). 기다릴 팀원이 없으니
  // 대기 화면으로 보내면 안 되고, 리포트 생성으로 바로 이어져야 합니다. 다만 리포트는
  // 이벤트로 비동기 생성돼 종료 응답의 reportId 가 아직 null 일 수 있어, 그때는
  // reportId 가 잡힐 때까지 이 화면에서 기다립니다.
  const [isEndedByMe, setIsEndedByMe] = useState(false);
  const endedReport = useFieldVisitWaitingReport(studyId, isEndedByMe);

  /**
   * 종료 시점에 확정한 이동 궤적(FE-018)의 세션 식별자. 리포트로 넘어가기 전에
   * 경로 요약 화면을 한 번 거치게 하려고 들고 있습니다.
   */
  const [finishedTrackSessionId, setFinishedTrackSessionId] = useState<string | null>(null);
  const apartmentName = route?.origin?.name;

  // 종료 뒤 리포트로 바로 보내지 않고, 오늘 걸은 경로를 먼저 보여 줍니다. 리포트
  // 생성 화면으로 넘어가는 것은 요약 화면의 주 CTA 가 이어서 합니다(폴링 자체는
  // 그대로 이 화면에 두고, 목적지만 요약 화면으로 바꿉니다).
  useEffect(() => {
    if (endedReport === null) return;
    router.replace({
      pathname: '/(app)/field-summary/[sessionId]',
      params: {
        // 종료 직전에 확정한 트랙 식별자. 못 잡았으면 궤적 없는 요약(빈 상태)으로
        // 열리고, 리포트로 가는 길은 그대로 남습니다.
        sessionId: finishedTrackSessionId ?? String(studyId),
        studyId: String(studyId),
        reportId: String(endedReport.reportId),
        ...(apartmentName ? { apartmentName } : {}),
      },
    } as never);
  }, [apartmentName, endedReport, finishedTrackSessionId, router, studyId]);

  // 내 임장은 끝났는데(readOnly) 세션은 아직 안 끝났으면, 지도 위에 머물지 않고
  // 다른 참여자를 기다리는 전용 화면(강제 종료 버튼 포함)으로 바로 넘어갑니다.
  useEffect(() => {
    if (!readOnly || isEndedByMe) return;
    router.replace({
      pathname: '/(app)/field-visit-waiting/[studyId]',
      params: { studyId: String(studyId) },
    } as never);
  }, [isEndedByMe, readOnly, router, studyId]);

  // 지도에서 경유지 점을 탭하면 시트를 최상단까지 펼칩니다(외부 BottomSheet 제어).
  useEffect(() => {
    if (focusedWaypointId === null) return;
    sheetRef.current?.snapToIndex(SHEET_EXPANDED_INDEX);
  }, [focusedWaypointId]);

  // 새 경유지를 열면 열려 있던 기록 상세를 닫아 그 지점의 항목 목록부터 보이게 합니다.
  // prop 변화에 맞춰 state 를 조정하는 표준 패턴(렌더 중 조건부 setState)이라, effect
  // 안에서 setState 를 호출하지 않습니다(react.dev "adjusting state when a prop changes").
  const [previousFocusedWaypointId, setPreviousFocusedWaypointId] = useState(focusedWaypointId);
  if (focusedWaypointId !== previousFocusedWaypointId) {
    setPreviousFocusedWaypointId(focusedWaypointId);
    if (focusedWaypointId !== null && openItemId !== null) setOpenItemId(null);
  }

  const openItem = useMemo(
    () =>
      categories
        .flatMap((category) => category.items)
        .find((item) => item.checklistItemId === openItemId) ?? null,
    [categories, openItemId],
  );

  // 완료 토글은 기존 useChecklist 흐름을 그대로 쓰되, 지도 점 마커의 진행도를 새로
  // 불러오도록 화면에 알립니다.
  const handleToggleItem = useCallback(
    (checklistItemId: number, isCompleted: boolean) => {
      toggleItem(checklistItemId, isCompleted);
      onItemCompletionChange();
    },
    [onItemCompletionChange, toggleItem],
  );

  const handleOpenRecord = (checklistItemId: number) => {
    setOpenItemId(checklistItemId);
    sheetRef.current?.snapToIndex(SHEET_EXPANDED_INDEX);
  };

  const handleRecordDraftChange = useCallback((checklistItemId: number, draft: string) => {
    setRecordDrafts((current) => ({ ...current, [checklistItemId]: draft }));
  }, []);

  const handleRecordDraftSaved = useCallback((checklistItemId: number, savedDraft: string) => {
    setRecordDrafts((current) => {
      // 저장 요청 뒤에 사용자가 새로 입력한 내용까지 이전 요청의 성공 콜백이 지우지
      // 않도록, 실제로 전송한 초안과 현재 초안이 같을 때만 해당 항목을 비웁니다.
      if ((current[checklistItemId] ?? '') !== savedDraft) return current;

      const next = { ...current };
      delete next[checklistItemId];
      return next;
    });
  }, []);

  // 기록을 추가·삭제하면 recordCount 배지만 그 자리에서 갱신합니다. 목록 전체를
  // 다시 불러오면 방금 넣은 사진 업로드까지 다시 조회하게 되어 낭비입니다.
  const [recordCountOverrides, setRecordCountOverrides] = useState<Record<number, number>>({});
  const handleRecordCountChange = (checklistItemId: number, recordCount: number) => {
    setRecordCountOverrides((current) => ({ ...current, [checklistItemId]: recordCount }));
  };
  const handleSttRecordCountRefresh = useCallback(
    async (checklistItemId: number) => {
      const recordCount = await refreshItemRecordCount(checklistItemId);
      setRecordCountOverrides((current) => ({ ...current, [checklistItemId]: recordCount }));
    },
    [refreshItemRecordCount],
  );

  const saveDraftsBeforeFinish = useCallback(async () => {
    await savePendingTextDrafts({
      recordDrafts,
      createClientRequestId: Crypto.randomUUID,
      createTextRecord: (checklistItemId, text, clientRequestId) =>
        createTextRecord(studyId, checklistItemId, text, clientRequestId),
      onDraftSaved: handleRecordDraftSaved,
      refreshRecordCount: handleSttRecordCountRefresh,
    });
  }, [handleRecordDraftSaved, handleSttRecordCountRefresh, recordDrafts, studyId]);

  const categoriesWithOverrides = useMemo(
    () =>
      categories.map((category) => ({
        ...category,
        items: category.items.map((item) => ({
          ...item,
          recordCount: recordCountOverrides[item.checklistItemId] ?? item.recordCount,
        })),
      })),
    [categories, recordCountOverrides],
  );

  // 종료는 되돌리기 어려운 동작이라(개인 종료 후 쓰기 차단) 먼저 확인을 받습니다.
  // 성공하면 오늘 걸은 경로 요약(FE-018)을 거쳐 리포트 또는 대기 화면으로 이어집니다.
  const handleFinishVisit = useCallback(() => {
    appAlert(
      '임장을 종료할까요?',
      totalCount > 0
        ? `체크리스트 ${completedCount}/${totalCount}개를 완료했어요. 지금 기록으로 종료하면 되돌릴 수 없어요.`
        : '종료하면 되돌릴 수 없어요.',
      [
        { text: '취소', style: 'cancel' },
        {
          text: '종료하기',
          style: 'destructive',
          onPress: () => {
            void (async () => {
              if (isFinishingRef.current) return;
              isFinishingRef.current = true;
              let isSavingDrafts = true;
              try {
                Keyboard.dismiss();
                await saveDraftsBeforeFinish();
                isSavingDrafts = false;
                const result = await finishFieldVisit(studyId, Crypto.randomUUID());

                // 내 임장이 실제로 끝난 시점에만 궤적 수집을 멈추고 트랙을 확정합니다.
                // (화면 이동으로는 끊기지 않게 러너를 모듈에 둔 이유이기도 합니다.)
                const trackSessionId =
                  (await stopAndFinishFieldTrack()) ?? String(result.sessionId);
                setFinishedTrackSessionId(trackSessionId);

                const summaryParams = {
                  sessionId: trackSessionId,
                  studyId: String(studyId),
                  ...(apartmentName ? { apartmentName } : {}),
                };

                if (result.reportId) {
                  router.replace({
                    pathname: '/(app)/field-summary/[sessionId]',
                    params: { ...summaryParams, reportId: String(result.reportId) },
                  } as never);
                  return;
                }
                if (result.sessionEnded) {
                  // 세션은 끝났는데 reportId 가 아직 없는 상태(비동기 생성). 대기 화면이
                  // 아니라 위 effect 의 폴링으로 리포트를 기다립니다.
                  setIsEndedByMe(true);
                  return;
                }
                // 다른 참여자가 아직 안 끝나 세션은 계속됩니다. 경로 요약을 먼저 보여
                // 주고, 요약 화면의 CTA 가 기존 전용 대기 화면으로 이어 줍니다.
                // (여기서 reload() 로 readOnly 를 만들어 위 effect 에 넘기던 경로를
                // 대신하는 것이라, 도착지는 그대로입니다.)
                router.replace({
                  pathname: '/(app)/field-summary/[sessionId]',
                  params: { ...summaryParams, waiting: '1' },
                } as never);
              } catch (error) {
                appAlert(
                  '오류',
                  isSavingDrafts
                    ? `작성 중인 메모를 저장하지 못해 임장을 종료하지 않았어요.${
                        error instanceof FieldRecordApiError ? ` ${error.message}` : ''
                      }`
                    : error instanceof FieldVisitApiError
                    ? error.message
                    : '임장을 종료하지 못했습니다.',
                );
              } finally {
                isFinishingRef.current = false;
              }
            })();
          },
        },
      ],
    );
  }, [apartmentName, completedCount, router, saveDraftsBeforeFinish, studyId, totalCount]);

  // 시트 손잡이(드래그 핸들). 그립뿐 아니라 '팔방이가 추천하는 체크리스트' 헤더까지 이 안에
  // 넣어, 헤더 아무 곳이나 잡고 시트를 올리고 내릴 수 있게 한다(handleComponent는
  // enableContentPanningGesture 와 무관하게 항상 드래그된다). 탭하면 스냅을 토글한다.
  const renderSheetHandle = useCallback(
    () => (
      <Pressable
        accessibilityRole="button"
        accessibilityState={{ expanded: isExpanded }}
        onPress={() => sheetRef.current?.snapToIndex(isExpanded ? 0 : SHEET_EXPANDED_INDEX)}
        style={[styles.sheetHandle, isExpanded && styles.handleExpanded]}
      >
        <View style={styles.sheetGrip} />
        <View style={styles.handleRow}>
          <SheetHandleIconBadge iconName="checkbox-outline" />
          {/* 문구가 좁은 화면에서 두 줄로 접히면 핸들 높이가 흔들리므로 한 줄로 고정합니다. */}
          <Text numberOfLines={1} style={styles.handleTitle}>
            팔방이가 추천하는 체크리스트
          </Text>
          <Text style={styles.handleCount}>
            {loadState === 'success' ? `${completedCount}/${totalCount}` : ''}
          </Text>
          <Ionicons
            color={MUTED_TEXT_COLOR}
            name={isExpanded ? 'chevron-down' : 'chevron-up'}
            size={20}
          />
        </View>
      </Pressable>
    ),
    [isExpanded, loadState, completedCount, totalCount],
  );

  return (
    <Animated.View
      pointerEvents="box-none"
      style={[styles.sheetWindow, { top: windowTop }, keyboardAwareSheetStyle]}
    >
      <View pointerEvents="box-none" style={styles.sheetWindowClip}>
        <Modal
          animationType="fade"
          onRequestClose={() => router.back()}
          presentationStyle="fullScreen"
          statusBarTranslucent
          visible={isGenerating}
        >
          <GenerationSpinnerScreen
            message={generationMessage}
            onBack={() => router.back()}
            progress={generationProgress}
            variant="checklist"
          />
        </Modal>
        {/* 내 종료로 세션이 끝난 직후 reportId 를 기다리는 짧은 구간. 지도를 그대로
            두면 임장이 아직 진행 중인 것처럼 보여서 리포트 생성 화면을 덮어씁니다. */}
        <Modal
          animationType="fade"
          onRequestClose={() => undefined}
          presentationStyle="fullScreen"
          statusBarTranslucent
          visible={isEndedByMe && endedReport === null}
        >
          <GenerationSpinnerScreen
            message="임장 기록을 모아 리포트를 준비하고 있어요."
            onBack={() => undefined}
            variant="report"
          />
        </Modal>
        <BottomSheet
          ref={sheetRef}
          snapPoints={SHEET_SNAP_POINTS}
          index={0}
          enablePanDownToClose={false}
          // 기본(true)이면 시트가 아직 최상단 스냅이 아닐 때 콘텐츠를 위로 스와이프해도
          // 스크롤이 아니라 "시트를 다음 스냅으로 확장"하는 데 제스처가 먼저 쓰입니다.
          // 사진·메모가 늘어나 목록이 길어질 화면이라, 중간 스냅에서도 곧장 스크롤되게
          // 끕니다. 시트 크기 조절은 핸들(잡이) 자체를 드래그해서 합니다.
          enableContentPanningGesture={false}
          // enableContentPanningGesture=false 와 겹치면 BottomSheetScrollView 가
          // 드래그 중엔 안 스크롤되고 손을 뗀 순간에만 관성이 튀는(고무줄) 버그가
          // 있습니다(gorhom/react-native-bottom-sheet #765·#877·#1450·#1806).
          // overDrag 자체를 꺼서 그 잔여 관성 계산이 안 타게 합니다.
          enableOverDrag={false}
          // snapPoints 를 명시했으므로 콘텐츠 높이로 스냅을 추가하는 동적 사이징은
          // 필요 없습니다(v5 기본값 true). 아래 SheetScrollArea 가 스냅 위치에
          // 따라 콘텐츠 아래 여백을 바꾸는데, 켜 두면 그 높이 변화가 다시 스냅을 바꾸는
          // 순환이 생깁니다.
          enableDynamicSizing={false}
          // 메모 입력창에 포커스가 가면(키보드가 뜨면) 시트를 최상단까지 자동으로 펼쳐
          // 저장 버튼이 키보드 위에서 항상 눌리게 합니다. 시트를 중간까지만 올려둔 채
          // 메모를 쓰기 시작해도 저장 버튼이 키보드에 가려 안 보이던 문제를 막습니다.
          // 입력을 마치면(키보드가 내려가면) 원래 스냅 위치로 되돌립니다.
          keyboardBehavior="extend"
          keyboardBlurBehavior="restore"
          android_keyboardInputMode="adjustResize"
          // 배경은 backgroundComponent(그라디언트)가 직접 그리므로, backgroundStyle 은
          // 안 넘깁니다 — 둘 다 넘기면 같은 borderRadius·테두리를 두 번 적용하게 됩니다.
          backgroundComponent={ChecklistSheetBackground}
          // 그립만이 아니라 '팔방이가 추천하는 체크리스트' 헤더 전체를 드래그 핸들로 렌더해,
          // 헤더 아무 곳이나 잡고 시트를 올리고 내릴 수 있게 합니다.
          handleComponent={renderSheetHandle}
          onChange={(index) => {
            setIsExpanded(index > 0);
            // 시트를 접으면 다음에 열었을 때 항상 전체 목록부터 보이게 하고, 지도에서
            // 탭했던 경유지 포커스도 함께 풉니다.
            if (index === 0) {
              setOpenItemId(null);
              onFocusedWaypointChange(null);
            }
          }}
        >
          <SheetScrollArea>
            {loadState === 'loading' ? (
              <ActivityIndicator color={PRIMARY_COLOR} style={styles.loader} />
            ) : loadState === 'error' ? (
              <View style={styles.errorBox}>
                <Text style={styles.errorText}>
                  {errorMessage ?? '체크리스트를 불러오지 못했습니다.'}
                </Text>
                {canRetry ? (
                  <Pressable
                    accessibilityRole="button"
                    onPress={() => void reload()}
                    style={({ pressed }) => [styles.retryButton, pressed && styles.pressed]}
                  >
                    <Text style={styles.retryButtonText}>다시 시도</Text>
                  </Pressable>
                ) : null}
              </View>
            ) : openItem ? (
              <RecordComposerView
                checklistItemId={openItem.checklistItemId}
                draftText={recordDrafts[openItem.checklistItemId] ?? ''}
                onBack={() => setOpenItemId(null)}
                onDraftTextChange={(draft) =>
                  handleRecordDraftChange(openItem.checklistItemId, draft)
                }
                onDraftSaved={(savedDraft) =>
                  handleRecordDraftSaved(openItem.checklistItemId, savedDraft)
                }
                onInputFocusChange={setIsInputFocused}
                onRecordCountChange={handleRecordCountChange}
                onSttRecordCountRefresh={handleSttRecordCountRefresh}
                readOnly={readOnly}
                studyId={studyId}
                subtitle={openItem.subtitle}
                title={openItem.title}
              />
            ) : (
              <ChecklistListView
                categories={categoriesWithOverrides}
                closeVote={closeVote}
                completedCount={completedCount}
                focusedWaypointId={focusedWaypointId}
                isGeneratingRoute={isGeneratingRoute}
                onCloseFocusedWaypoint={() => onFocusedWaypointChange(null)}
                onFinishVisit={handleFinishVisit}
                onGenerateRoute={onGenerateRoute}
                onOpenRecord={handleOpenRecord}
                onToggleItem={handleToggleItem}
                pendingItemIds={pendingItemIds}
                readOnly={readOnly}
                route={route}
                totalCount={totalCount}
              />
            )}
          </SheetScrollArea>
        </BottomSheet>
      </View>
    </Animated.View>
  );
}

/**
 * 시트 본문 스크롤 영역.
 *
 * gorhom 은 본문 컨테이너 높이를 "가장 높은 스냅(100%) 기준 고정값"으로 잡습니다
 * (BottomSheet.tsx 의 `animatedSheetHeight = containerHeight - highestDetentPosition`,
 * BottomSheetContent.tsx 의 animatedContentHeightMax). 낮은 스냅에서는 시트를 아래로
 * 밀어 넘치는 부분을 화면 밖으로 잘라내기만 하므로, 스크롤 뷰포트가 실제로 보이는
 * 높이보다 커집니다. 그래서 55% 스냅에서는 ScrollView 가 "내용이 다 들어간다"고 판단해
 * 스크롤이 생기지 않았습니다. 체크리스트 목록은 100% 높이보다도 길어 우연히 스크롤됐고,
 * 기록 화면(사진·메모)만 아래를 못 보던 이유가 이것입니다.
 *
 * 키보드가 뜨면 같은 화면이 스크롤되는 것도 같은 이유입니다 — keyboardBehavior="extend"
 * 일 때만 gorhom 이 본문 높이를 키보드 높이만큼 줄여 주기 때문입니다.
 *
 * 반대로 ScrollView 에 style={{height}} 로 뷰포트를 줄이는 방법은 통하지 않습니다.
 * 실측해 보면 값을 넘겨도 프레임 높이가 그대로였습니다(계산 382 / 실제 752).
 * 그래서 뷰포트를 줄이는 대신, 화면 밖으로 잘려 나간 만큼을 콘텐츠 아래 여백으로 더해
 * 스크롤 여지를 만듭니다. contentContainerStyle 은 정상적으로 반영됩니다.
 */
function SheetScrollArea({ children }: { children: React.ReactNode }) {
  const { animatedPosition } = useBottomSheetInternal();
  const [hiddenBottom, setHiddenBottom] = useState(0);

  // 시트는 100% 높이 그대로 있고 position 만큼 아래로 밀려 있으므로, 화면 밖으로
  // 잘려 나간 양이 곧 position 입니다. 그만큼을 콘텐츠 아래 여백으로 더해 주면
  // 스크롤 여지가 생겨, 잘려 있던 마지막 내용까지 끌어올려 볼 수 있습니다.
  useAnimatedReaction(
    // 드래그 중 매 프레임 setState 하지 않도록 1px 단위로 반올림합니다.
    () => Math.max(0, Math.round(animatedPosition.get())),
    (next, previous) => {
      if (next === previous) return;
      runOnJS(setHiddenBottom)(next);
    },
    [animatedPosition],
  );

  const contentContainerStyle = useMemo(
    () => [styles.scrollContent, { paddingBottom: SCROLL_CONTENT_BOTTOM_PADDING + hiddenBottom }],
    [hiddenBottom],
  );

  return (
    <BottomSheetScrollView
      contentContainerStyle={contentContainerStyle}
      keyboardShouldPersistTaps="handled"
    >
      {children}
    </BottomSheetScrollView>
  );
}

const styles = StyleSheet.create({
  // 모서리 반경(SHEET_RADIUS)은 떠 있는 탭바(FloatingTabBar, borderRadius 30)와
  // 같은 둥근 정도로 맞췄습니다. 시트는 폭이 훨씬 넓어 30을 그대로 쓰면 아래쪽
  // 모서리가 過하게 파여 보여서 살짝만 낮췄습니다.
  sheetWindow: {
    position: 'absolute',
    left: 12,
    right: 12,
    // 접힌 시트 밖은 box-none이라 지도 조작을 유지하고, 시트가 덮은 영역만 지도 위
    // 플로팅 버튼보다 먼저 그려 터치가 체크리스트 항목에 도달하게 합니다.
    zIndex: SHEET_LAYER_Z_INDEX,
  },
  // 카드 모서리를 둥글게 잘라내는 건 시트(+생성 모달)만 해당합니다. 대기 버블은
  // 이 클리핑 밖에 형제로 둬서 화면 오른쪽 위로 자연스럽게 삐져나오게 합니다.
  sheetWindowClip: {
    flex: 1,
    borderRadius: SHEET_RADIUS,
    overflow: 'hidden',
  },
  sheetGrip: {
    width: 56,
    height: 6,
    borderRadius: 3,
    backgroundColor: BORDER_COLOR,
    alignSelf: 'center',
  },
  // 드래그 핸들 전체(그립 + 헤더 행). 위 그립부터 헤더 제목 줄까지 잡고 끌 수 있다.
  sheetHandle: {
    paddingTop: 12,
  },
  // 헤더 행(아이콘 배지 · 제목 · 개수 · 펼침 화살표). 핸들 안에 들어가 드래그 타깃이 된다.
  handleRow: {
    flexDirection: 'row',
    alignItems: 'center',
    gap: 10,
    marginTop: 8,
    paddingBottom: 14,
    paddingHorizontal: 20,
  },
  handleExpanded: {
    borderBottomWidth: 1,
    borderBottomColor: BORDER_COLOR,
  },
  // 나브바 탭 라벨과 색은 그대로 맞추고, 굵기만 한 단계 올립니다(SemiBold → Bold).
  // 이 자리는 시트의 제목 역할이라 탭 라벨보다 살짝 더 강조돼야 합니다.
  handleTitle: {
    flex: 1,
    fontSize: 15,
    fontFamily: TAB_LABEL_FONT_BOLD,
    color: TAB_LABEL_COLOR,
  },
  handleCount: {
    fontSize: 13,
    fontWeight: '600',
    color: MUTED_TEXT_COLOR,
  },
  scrollContent: {
    paddingHorizontal: 20,
    paddingTop: 16,
    paddingBottom: SCROLL_CONTENT_BOTTOM_PADDING,
  },
  loader: { paddingVertical: 32 },
  errorBox: { alignItems: 'center', gap: 12, paddingVertical: 24 },
  errorText: {
    fontSize: 13,
    color: MUTED_TEXT_COLOR,
    textAlign: 'center',
  },
  retryButton: {
    paddingHorizontal: 16,
    height: 36,
    borderRadius: 18,
    alignItems: 'center',
    justifyContent: 'center',
    backgroundColor: SOFT_GREEN_COLOR,
  },
  retryButtonText: {
    fontSize: 13,
    fontWeight: '700',
    color: PRIMARY_COLOR,
  },
  pressed: { opacity: 0.82 },
});
