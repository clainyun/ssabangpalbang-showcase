import * as Crypto from 'expo-crypto';
import { AppState } from 'react-native';
import { useCallback, useEffect, useRef, useState } from 'react';

import {
  ChecklistApiError,
  generateChecklist,
  getChecklist,
  getChecklistGenerationStatus,
  saveChecklistAnswers,
  type ChecklistCategory,
} from '@/features/checklist/api/checklist';
import { generateAndRefreshChecklist } from '@/features/checklist/checklistModel';

export type ChecklistLoadState = 'loading' | 'success' | 'error';

interface ChecklistState {
  loadState: ChecklistLoadState;
  /** 기존 목록 조회가 아니라 AI 체크리스트 생성 요청을 기다리는 동안만 true입니다. */
  isGenerating: boolean;
  generationProgress: number;
  generationMessage: string | null;
  errorMessage: string | null;
  categories: ChecklistCategory[];
  completedCount: number;
  totalCount: number;
  /** AI 생성이 실패해 기본 체크리스트로 대체됐는지. */
  isFallback: boolean;
  /** 이미 종료한 참여자는 체크·기록을 수정할 수 없습니다. */
  readOnly: boolean;
  /**
   * 오류 화면에서 '다시 시도'를 보여줄지. 참여자가 아니거나 임장이 이미 종료된 경우처럼
   * 재시도해도 계속 실패하는 상황에서는 false로 두어 무의미한 재시도 버튼을 숨긴다.
   */
  canRetry: boolean;
  /** 서버 저장이 네트워크 문제로 실패해 로컬에서만 대기 중인 항목. */
  pendingItemIds: number[];
}

const GENERATION_PROGRESS_POLL_MS = 750;
const GENERATION_COMPLETE_HOLD_MS = 300;

function categoriesFromCounts(categories: ChecklistCategory[]): {
  completedCount: number;
  totalCount: number;
} {
  return categories.reduce(
    (acc, category) => ({
      completedCount: acc.completedCount + category.completedCount,
      totalCount: acc.totalCount + category.totalCount,
    }),
    { completedCount: 0, totalCount: 0 },
  );
}

/**
 * 체크리스트 조회·생성·완료 상태 저장을 한곳에서 관리합니다.
 *
 * 오프라인 처리: 별도 네트워크 감지 라이브러리(NetInfo 등)를 새로 넣지 않고, 저장
 * 요청이 전송 단계에서 실패한 경우(ChecklistApiError 'NETWORK_ERROR' — 타임아웃·연결
 * 끊김)만 "로컬 대기"로 표시합니다. 서버가 실제로 응답한 4xx 는 별개 취급합니다.
 * 앱이 다시 활성화되면(AppState → 'active') 대기 중인 항목을 한 번 더 시도합니다.
 */
interface UseChecklistOptions {
  /** 조회 전용 화면에서는 누락된 체크리스트를 새로 만들지 않습니다. */
  generateIfMissing?: boolean;
  /** 유효한 조회 키가 준비되기 전에는 API를 호출하지 않습니다. */
  enabled?: boolean;
}

export function useChecklist(studyId: number, options: UseChecklistOptions = {}) {
  const generateIfMissing = options.generateIfMissing ?? true;
  const enabled = options.enabled ?? true;
  const [state, setState] = useState<ChecklistState>({
    loadState: 'loading',
    isGenerating: false,
    generationProgress: 0,
    generationMessage: null,
    errorMessage: null,
    categories: [],
    completedCount: 0,
    totalCount: 0,
    isFallback: false,
    readOnly: false,
    canRetry: true,
    pendingItemIds: [],
  });

  // 마지막으로 사용자가 원한 완료 상태. 저장이 실패해 재시도할 때 최신 의도를 씁니다.
  const desiredCompletionRef = useRef(new Map<number, boolean>());
  // AppState 리스너는 한 번만 구독하므로, 재구독 없이 최신 대기 목록을 읽으려면
  // state 클로저 대신 ref를 씁니다(그러지 않으면 구독 시점의 오래된 값에 갇힙니다).
  const pendingItemIdsRef = useRef<number[]>([]);
  // 스터디 전환·재시도 중 먼저 시작한 요청이 늦게 끝나 최신 화면을 덮지 않게 합니다.
  const loadRequestRef = useRef(0);
  const generationPollTimerRef = useRef<ReturnType<typeof setTimeout> | null>(null);
  const generationPollSequenceRef = useRef(0);

  const stopGenerationProgressPolling = useCallback(() => {
    generationPollSequenceRef.current += 1;
    if (generationPollTimerRef.current !== null) {
      clearTimeout(generationPollTimerRef.current);
      generationPollTimerRef.current = null;
    }
  }, []);

  const startGenerationProgressPolling = useCallback(
    (requestId: number, attemptId: string) => {
      stopGenerationProgressPolling();
      const pollSequence = generationPollSequenceRef.current;

      const poll = async () => {
        if (
          loadRequestRef.current !== requestId ||
          generationPollSequenceRef.current !== pollSequence
        ) {
          return;
        }
        const pollStartedAt = Date.now();
        let reachedTerminalStatus = false;
        try {
          const progress = await getChecklistGenerationStatus(studyId, attemptId);
          if (
            loadRequestRef.current !== requestId ||
            generationPollSequenceRef.current !== pollSequence
          ) {
            return;
          }

          if (progress.attemptId === attemptId) {
            setState((current) => {
              if (progress.progressRate < current.generationProgress) return current;
              return {
                ...current,
                generationProgress: progress.progressRate,
                generationMessage: progress.progressMessage || current.generationMessage,
              };
            });
            reachedTerminalStatus = progress.status === 'DONE' || progress.status === 'FAILED';
          }
        } catch {
          // 생성 POST가 성공/실패의 기준이다. 상태 조회의 일시 오류는 다음 폴링에서 복구한다.
        }

        if (reachedTerminalStatus) {
          generationPollTimerRef.current = null;
          return;
        }
        if (
          loadRequestRef.current !== requestId ||
          generationPollSequenceRef.current !== pollSequence
        ) {
          return;
        }
        const nextPollDelay = Math.max(
          0,
          GENERATION_PROGRESS_POLL_MS - (Date.now() - pollStartedAt),
        );
        generationPollTimerRef.current = setTimeout(() => void poll(), nextPollDelay);
      };

      void poll();
    },
    [stopGenerationProgressPolling, studyId],
  );

  const load = useCallback(async () => {
    const requestId = ++loadRequestRef.current;
    stopGenerationProgressPolling();
    if (!enabled) return;
    setState((current) => ({
      ...current,
      loadState: 'loading',
      isGenerating: false,
      generationProgress: 0,
      generationMessage: null,
      errorMessage: null,
    }));
    try {
      const detail = await getChecklist(studyId);
      let body = detail.checklist;
      let readOnly = detail.readOnly;
      if (!body) {
        if (!generateIfMissing) {
          if (loadRequestRef.current !== requestId) return;
          setState({
            loadState: 'success',
            isGenerating: false,
            generationProgress: 0,
            generationMessage: null,
            errorMessage: null,
            categories: [],
            completedCount: 0,
            totalCount: 0,
            isFallback: false,
            readOnly: true,
            canRetry: true,
            pendingItemIds: [],
          });
          return;
        }
        // 참여자가 아니거나(participantStatus null) 이미 종료·읽기 전용이면 백엔드가 생성을
        // 거부한다(재시도해도 계속 실패). 생성 POST를 아예 시도하지 않고 명확히 안내한다.
        if (detail.participantStatus !== 'IN_PROGRESS' || detail.readOnly) {
          if (loadRequestRef.current !== requestId) return;
          setState((current) => ({
            ...current,
            loadState: 'error',
            isGenerating: false,
            generationProgress: 0,
            generationMessage: null,
            canRetry: false,
            errorMessage: detail.readOnly
              ? '이미 종료된 임장이에요. 체크리스트를 새로 만들 수 없어요.'
              : '이 임장에 참여하지 않아 체크리스트를 볼 수 없어요. 대상 아파트 근처에서 임장을 시작하면 만들어져요.',
          }));
          return;
        }
        if (loadRequestRef.current !== requestId) return;
        setState((current) => ({
          ...current,
          isGenerating: true,
          generationProgress: 0,
          generationMessage: '체크리스트 생성 요청을 보내고 있어요.',
        }));
        const attemptId = Crypto.randomUUID();
        startGenerationProgressPolling(requestId, attemptId);
        const resolved = await generateAndRefreshChecklist({
          generate: () => generateChecklist(studyId, attemptId),
          refresh: () => getChecklist(studyId),
          isNetworkError: (error) =>
            error instanceof ChecklistApiError && error.code === 'NETWORK_ERROR',
          onGenerated: () => {
            if (loadRequestRef.current !== requestId) return;
            stopGenerationProgressPolling();
            setState((current) => ({
              ...current,
              generationProgress: 100,
              generationMessage: '체크리스트가 완성됐어요.',
            }));
          },
        });
        if (loadRequestRef.current !== requestId) return;
        ({ body, readOnly } = resolved);
        await delay(GENERATION_COMPLETE_HOLD_MS);
      }

      if (loadRequestRef.current !== requestId) return;
      setState({
        loadState: 'success',
        isGenerating: false,
        generationProgress: 100,
        generationMessage: null,
        errorMessage: null,
        categories: body.categories,
        completedCount: body.completedCount,
        totalCount: body.totalCount,
        isFallback: body.isFallback,
        readOnly,
        canRetry: true,
        pendingItemIds: [],
      });
    } catch (error) {
      if (loadRequestRef.current !== requestId) return;
      stopGenerationProgressPolling();
      // 참여 자격·세션 종료 관련 실패는 재시도해도 계속 실패하므로 '다시 시도'를 숨긴다.
      // (GET 통과 후 POST 사이에 세션이 종료되는 경쟁 상황 등에서 여기로 온다.)
      const code = error instanceof ChecklistApiError ? error.code : null;
      const terminal =
        code === 'CHECKLIST_GENERATE_FORBIDDEN' ||
        code === 'FIELD_VISIT_ALREADY_ENDED' ||
        code === 'FIELD_PARTICIPANT_ALREADY_ENDED' ||
        code === 'CHECKLIST_ACCESS_DENIED';
      setState((current) => ({
        ...current,
        loadState: 'error',
        isGenerating: false,
        generationMessage: null,
        canRetry: !terminal,
        errorMessage:
          error instanceof ChecklistApiError ? error.message : '체크리스트를 불러오지 못했습니다.',
      }));
    }
  }, [
    enabled,
    generateIfMissing,
    startGenerationProgressPolling,
    stopGenerationProgressPolling,
    studyId,
  ]);

  useEffect(() => {
    if (enabled) void load();
    return () => {
      loadRequestRef.current += 1;
      stopGenerationProgressPolling();
    };
  }, [enabled, load, stopGenerationProgressPolling]);

  const applyItemCompletion = useCallback(
    (checklistItemId: number, isCompleted: boolean, completedAt: string | null) => {
      setState((current) => {
        const categories = current.categories.map((category) => {
          const existing = category.items.find((item) => item.checklistItemId === checklistItemId);
          if (!existing) return category;

          const items = category.items.map((item) =>
            item.checklistItemId === checklistItemId ? { ...item, isCompleted, completedAt } : item,
          );
          const delta = isCompleted === existing.isCompleted ? 0 : isCompleted ? 1 : -1;
          return { ...category, items, completedCount: category.completedCount + delta };
        });
        const totals = categoriesFromCounts(categories);

        return { ...current, categories, ...totals };
      });
    },
    [],
  );

  const setPending = useCallback((checklistItemId: number, pending: boolean) => {
    setState((current) => {
      const pendingItemIds = pending
        ? [...new Set([...current.pendingItemIds, checklistItemId])]
        : current.pendingItemIds.filter((id) => id !== checklistItemId);
      pendingItemIdsRef.current = pendingItemIds;

      return { ...current, pendingItemIds };
    });
  }, []);

  const trySave = useCallback(
    async (checklistItemId: number, isCompleted: boolean) => {
      try {
        const result = await saveChecklistAnswers(studyId, [{ checklistItemId, isCompleted }]);
        // 저장 도중 사용자가 다시 바꿨으면(빠르게 두 번 탭) 그 최신 의도가 우선입니다 —
        // 여기서 덮어쓰지 않고 최신 값과 같을 때만 서버 응답(completedAt)을 반영합니다.
        if (desiredCompletionRef.current.get(checklistItemId) === isCompleted) {
          const saved = result.answers.find((a) => a.checklistItemId === checklistItemId);
          applyItemCompletion(checklistItemId, isCompleted, saved?.completedAt ?? null);
          setPending(checklistItemId, false);
        }
      } catch (error) {
        const isOffline = error instanceof ChecklistApiError && error.code === 'NETWORK_ERROR';
        if (isOffline) {
          setPending(checklistItemId, true);
        } else {
          // 서버가 실제로 거부한 경우(권한·종료 등)는 로컬 낙관적 반영을 되돌립니다.
          applyItemCompletion(checklistItemId, !isCompleted, null);
          setPending(checklistItemId, false);
        }
      }
    },
    [applyItemCompletion, setPending, studyId],
  );

  const toggleItem = useCallback(
    (checklistItemId: number, isCompleted: boolean) => {
      if (state.readOnly) return;
      desiredCompletionRef.current.set(checklistItemId, isCompleted);
      applyItemCompletion(checklistItemId, isCompleted, null);
      void trySave(checklistItemId, isCompleted);
    },
    [applyItemCompletion, state.readOnly, trySave],
  );

  const refreshItemRecordCount = useCallback(
    async (checklistItemId: number): Promise<number> => {
      const detail = await getChecklist(studyId);
      const body = detail.checklist;
      const refreshedItem = body?.categories
        .flatMap((category) => category.items)
        .find((item) => item.checklistItemId === checklistItemId);
      if (!refreshedItem) {
        throw new ChecklistApiError(
          'CHECKLIST_ITEM_NOT_FOUND',
          '체크리스트 기록 개수를 새로 불러오지 못했습니다.',
        );
      }

      setState((current) => ({
        ...current,
        readOnly: detail.readOnly,
        categories: current.categories.map((category) => ({
          ...category,
          items: category.items.map((item) =>
            item.checklistItemId === checklistItemId
              ? { ...item, recordCount: refreshedItem.recordCount }
              : item,
          ),
        })),
      }));
      return refreshedItem.recordCount;
    },
    [studyId],
  );

  // 앱이 다시 활성화되면 로컬 대기 중인 항목을 한 번 더 시도합니다.
  useEffect(() => {
    const subscription = AppState.addEventListener('change', (nextState) => {
      if (nextState !== 'active') return;
      for (const itemId of pendingItemIdsRef.current) {
        const desired = desiredCompletionRef.current.get(itemId);
        if (desired !== undefined) void trySave(itemId, desired);
      }
    });

    return () => subscription.remove();
  }, [trySave]);

  return {
    ...state,
    reload: load,
    refreshItemRecordCount,
    toggleItem,
  };
}

function delay(durationMs: number): Promise<void> {
  return new Promise((resolve) => setTimeout(resolve, durationMs));
}
