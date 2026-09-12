import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query';
import { useEffect, useRef, useState } from 'react';
import { AppState } from 'react-native';

import { useAuthStore } from '@/store/authStore';

import { applyToStudy } from './api/applyToStudy';
import { getStudyDetail } from './api/getStudyDetail';
import type { StudyDetail } from './api/getStudyDetail';
import type { ApplyToStudyInput } from './api/types';

export const studyDetailQueryKey = (studyId: number) => ['study', 'detail', studyId] as const;

const MAX_TIMER_DELAY_MS = 2_147_000_000;
const START_BOUNDARY_BUFFER_MS = 100;
const START_BOUNDARY_RETRY_MS = 5_000;
const PENDING_CHANGE_POLL_MS = 15_000;

/**
 * "서버에서 무언가 바뀌기를 기다리는 중"인 화면만 주기적으로 다시 조회합니다.
 *
 * - 승인 대기 중인 신청자: 스터디장이 승인·거절하면 화면 전체가 바뀝니다.
 * - 임장 시작을 기다리는 멤버: 스터디장이 모집을 마감해도 서버가 알림을 보내지 않아
 *   (백엔드 `StudyRecruitmentService.closeRecruitment`), 폴링이 유일한 자동 반영 경로입니다.
 *
 * 그 밖의 상태(비멤버·거절·완료·취소·이미 시작 가능·임장 종료)에서는 폴링하지 않습니다.
 */
function shouldPollStudyDetail(detail: StudyDetail | undefined) {
  if (detail === undefined) {
    return false;
  }
  if (detail.myParticipationStatus === 'PENDING') {
    return true;
  }
  return (
    detail.isMember &&
    !detail.canStartFieldVisit &&
    (detail.status === 'RECRUITING' || detail.status === 'CLOSED') &&
    detail.fieldVisitStatus !== 'ENDED'
  );
}

function shouldRetryStartBoundaryRefresh(detail: StudyDetail) {
  const scheduleStartAt = detail.nextSchedule?.startAt;
  if (
    detail.canStartFieldVisit ||
    !detail.isMember ||
    (detail.status !== 'CLOSED' && detail.status !== 'IN_PROGRESS') ||
    detail.fieldVisitStatus === 'ENDED' ||
    !scheduleStartAt
  ) {
    return false;
  }

  const startAtMilliseconds = Date.parse(scheduleStartAt);
  return Number.isFinite(startAtMilliseconds) && startAtMilliseconds <= Date.now();
}

export function useStudyDetail(studyId: number) {
  const accessToken = useAuthStore((state) => state.accessToken);
  const isEnabled = accessToken !== null && Number.isFinite(studyId);
  // TanStack Query의 `refetchIntervalInBackground`는 웹 문서 가시성 기준이라 React Native
  // 에서는 백그라운드를 구분하지 못합니다. 그래서 앱 활성 상태를 직접 보고 폴링을 멈춥니다.
  const [isAppActive, setIsAppActive] = useState(() => AppState.currentState === 'active');

  const query = useQuery({
    queryKey: studyDetailQueryKey(studyId),
    enabled: isEnabled,
    queryFn: () => getStudyDetail(studyId),
    refetchInterval: (polledQuery) =>
      isAppActive && shouldPollStudyDetail(polledQuery.state.data)
        ? PENDING_CHANGE_POLL_MS
        : false,
  });

  const { data: detail, refetch } = query;
  const isPollingRef = useRef(false);
  const shouldPoll = isEnabled && shouldPollStudyDetail(detail);

  useEffect(() => {
    isPollingRef.current = shouldPoll;
  }, [shouldPoll]);

  // 백그라운드에 있는 동안은 폴링이 멈추므로, 앱으로 돌아온 즉시 한 번 맞춰 줍니다.
  useEffect(() => {
    const subscription = AppState.addEventListener('change', (nextState) => {
      const isActive = nextState === 'active';
      setIsAppActive(isActive);
      if (isActive && isPollingRef.current) {
        void refetch();
      }
    });

    return () => subscription.remove();
  }, [refetch]);

  const scheduleStartAt = detail?.nextSchedule?.startAt;
  const shouldRefreshAtStart =
    detail !== undefined &&
    !detail.canStartFieldVisit &&
    detail.isMember &&
    (detail.status === 'CLOSED' || detail.status === 'IN_PROGRESS') &&
    detail.fieldVisitStatus !== 'ENDED' &&
    scheduleStartAt !== undefined;

  useEffect(() => {
    if (!shouldRefreshAtStart || !scheduleStartAt) return;

    const startAtMilliseconds = Date.parse(scheduleStartAt);
    if (!Number.isFinite(startAtMilliseconds)) return;

    let cancelled = false;
    let timeout: ReturnType<typeof setTimeout> | undefined;

    const refreshUntilServerReady = async () => {
      const result = await refetch();
      if (cancelled || !result.isSuccess || !result.data) return;

      if (shouldRetryStartBoundaryRefresh(result.data)) {
        timeout = setTimeout(() => void refreshUntilServerReady(), START_BOUNDARY_RETRY_MS);
      }
    };

    const scheduleRefresh = () => {
      const remaining = startAtMilliseconds - Date.now();
      if (remaining <= 0) {
        void refreshUntilServerReady();
        return;
      }

      timeout = setTimeout(
        scheduleRefresh,
        Math.min(remaining + START_BOUNDARY_BUFFER_MS, MAX_TIMER_DELAY_MS),
      );
    };

    scheduleRefresh();

    return () => {
      cancelled = true;
      if (timeout !== undefined) clearTimeout(timeout);
    };
  }, [refetch, scheduleStartAt, shouldRefreshAtStart]);

  return query;
}

/** 신청 성공 시 canApply/myParticipationStatus가 바뀌므로 상세를 즉시 다시 불러옵니다. */
export function useApplyToStudy(studyId: number) {
  const accessToken = useAuthStore((state) => state.accessToken);
  const sessionVersion = useAuthStore((state) => state.sessionVersion);
  const queryClient = useQueryClient();

  return useMutation({
    mutationFn: (input: ApplyToStudyInput) =>
      applyToStudy(studyId, input, accessToken ?? undefined, sessionVersion),
    onSuccess: (result) => {
      queryClient.setQueryData<StudyDetail>(studyDetailQueryKey(studyId), (detail) =>
        detail === undefined
          ? detail
          : { ...detail, myParticipationStatus: result.status, canApply: false },
      );
      return queryClient.invalidateQueries({
        queryKey: studyDetailQueryKey(studyId),
        exact: true,
        refetchType: 'all',
      });
    },
  });
}
