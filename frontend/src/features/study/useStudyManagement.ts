import {
  useInfiniteQuery,
  useMutation,
  useQuery,
  useQueryClient,
  type QueryClient,
} from '@tanstack/react-query';

import { useAuthStore } from '@/store/authStore';
import { homeSummaryQueryRoot } from '@/features/home/useHomeSummary';
import { myPageQueryKeys } from '@/features/member/api/myPageQueryKeys';

import { approveApplication, rejectApplication } from './api/decideApplication';
import { closeRecruitment } from './api/closeRecruitment';
import { reopenRecruitment } from './api/reopenRecruitment';
import { getApplications } from './api/getApplications';
import { getMembers } from './api/getMembers';
import { kickMember } from './api/kickMember';
import { leaveStudy } from './api/leaveStudy';
import {
  createStudySchedule,
  deleteStudySchedule,
  getStudySchedule,
  updateStudySchedule,
} from './api/studySchedule';
import { createNotice, deleteNotice, getNotices, updateNotice } from './api/studyNotice';
import type { StudyDetail } from './api/getStudyDetail';
import type {
  ApplicationDecisionResult,
  ApplicationListResult,
  ApplicationStatus,
  ScheduleInput,
  ScheduleUpdateInput,
  StudyMemberListResult,
  UpdateStudyDetailsInput,
} from './api/types';
import { updateStudyDetails } from './api/updateStudyDetails';
import { getNextNoticeCursor } from './studyNoticePagination';
import { studyDetailQueryKey } from './useStudyDetail';

export const applicationsQueryKey = (studyId: number, status?: ApplicationStatus) =>
  ['study', 'applications', studyId, status ?? 'ALL'] as const;

export const membersQueryKey = (studyId: number) => ['study', 'members', studyId] as const;

export const scheduleQueryKey = (studyId: number) => ['study', 'schedule', studyId] as const;

export const noticesQueryKey = (studyId: number) => ['study', 'notices', studyId] as const;

/** 상태 필터별 신청자 캐시(`'ALL'`·`'PENDING'`·…)를 한꺼번에 가리키는 접두사 키입니다. */
const applicationsQueryRoot = (studyId: number) => ['study', 'applications', studyId] as const;

const STUDY_DETAIL_STATUSES = [
  'RECRUITING',
  'CLOSED',
  'IN_PROGRESS',
  'COMPLETED',
  'CANCELED',
] as const satisfies readonly StudyDetail['status'][];

function isStudyDetailStatus(value: string): value is StudyDetail['status'] {
  return (STUDY_DETAIL_STATUSES as readonly string[]).includes(value);
}

/** 서버 `StudyAccessPolicy.canApproveApplication`과 같은 규칙입니다. */
function canApproveApplication(
  status: ApplicationStatus,
  studyStatus: string,
  currentMemberCount: number,
  capacity: number,
) {
  return status === 'PENDING' && studyStatus === 'RECRUITING' && currentMemberCount < capacity;
}

/**
 * 서버 `StudyAccessPolicy.canRejectApplication`과 같은 규칙입니다.
 * 정원이 차서 `CLOSED`가 되어도 남은 대기자는 거절할 수 있으므로,
 * 승인 조건과 같은 식으로 묶으면 안 됩니다.
 */
function canRejectApplication(status: ApplicationStatus, studyStatus: string) {
  return status === 'PENDING' && (studyStatus === 'RECRUITING' || studyStatus === 'CLOSED');
}

/**
 * 승인·거절 응답을 신청자 목록 캐시에 그대로 반영합니다(재조회를 기다리지 않습니다).
 *
 * 결정된 행만 확정 상태로 바꾸고, 승인 응답이 인원수·모집 상태를 함께 준 경우에만
 * 나머지 대기 행의 버튼 노출을 서버와 같은 규칙으로 다시 계산합니다.
 * 행을 지우거나 새로 넣지 않습니다 — 목록의 정리는 백그라운드 재조회에 맡깁니다.
 */
function applyDecisionToApplicationList(
  list: ApplicationListResult,
  result: ApplicationDecisionResult,
): ApplicationListResult {
  const decided = list.content.find(
    (application) => application.applicationId === result.applicationId,
  );
  // 이 캐시에 없거나 이미 처리된 행이면 요약 수치가 두 번 깎이지 않도록 그대로 둡니다.
  if (decided === undefined || decided.status !== 'PENDING') {
    return list;
  }

  // 세 값이 모두 있어야(= 승인 응답이어야) 다른 행의 버튼을 다시 계산할 수 있습니다.
  const study =
    result.currentMemberCount !== undefined &&
    result.capacity !== undefined &&
    result.studyStatus !== undefined
      ? {
          currentMemberCount: result.currentMemberCount,
          capacity: result.capacity,
          status: result.studyStatus,
        }
      : null;

  return {
    ...list,
    content: list.content.map((application) => {
      if (application.applicationId === result.applicationId) {
        return {
          ...application,
          status: result.status,
          decidedAt: result.decidedAt,
          canApprove: false,
          canReject: false,
        };
      }
      if (study === null) {
        return application;
      }
      return {
        ...application,
        canApprove: canApproveApplication(
          application.status,
          study.status,
          study.currentMemberCount,
          study.capacity,
        ),
        canReject: canRejectApplication(application.status, study.status),
      };
    }),
    summary: {
      ...list.summary,
      pendingCount: Math.max(0, list.summary.pendingCount - 1),
      approvedCount:
        result.status === 'APPROVED' ? list.summary.approvedCount + 1 : list.summary.approvedCount,
      rejectedCount:
        result.status === 'REJECTED' ? list.summary.rejectedCount + 1 : list.summary.rejectedCount,
      currentMemberCount: study?.currentMemberCount ?? list.summary.currentMemberCount,
      capacity: study?.capacity ?? list.summary.capacity,
    },
  };
}

/**
 * 승인 응답이 함께 주는 인원수·모집 상태만 스터디 상세 캐시에 반영합니다.
 * `memberSummary`·`canApply`처럼 응답에 없는 값은 추측하지 않고 재조회에 맡깁니다.
 */
function applyApprovalToStudyDetail(
  detail: StudyDetail,
  result: ApplicationDecisionResult,
): StudyDetail {
  return {
    ...detail,
    currentMemberCount: result.currentMemberCount ?? detail.currentMemberCount,
    capacity: result.capacity ?? detail.capacity,
    status:
      result.studyStatus !== undefined && isStudyDetailStatus(result.studyStatus)
        ? result.studyStatus
        : detail.status,
  };
}

export function useApplications(studyId: number, status?: ApplicationStatus) {
  const accessToken = useAuthStore((state) => state.accessToken);
  const sessionVersion = useAuthStore((state) => state.sessionVersion);

  return useQuery({
    queryKey: applicationsQueryKey(studyId, status),
    enabled: accessToken !== null && Number.isFinite(studyId),
    queryFn: () => getApplications(studyId, { status }, accessToken ?? undefined, sessionVersion),
  });
}

export function useMembers(studyId: number) {
  const accessToken = useAuthStore((state) => state.accessToken);
  const sessionVersion = useAuthStore((state) => state.sessionVersion);

  return useQuery({
    queryKey: membersQueryKey(studyId),
    enabled: accessToken !== null && Number.isFinite(studyId),
    queryFn: () => getMembers(studyId, accessToken ?? undefined, sessionVersion),
  });
}

/**
 * 승인·거절·강퇴·마감은 전부 같은 화면(신청자 수·멤버 수·모집 상태)에 영향을 주므로,
 * 어떤 액션이 성공하든 신청자·멤버·스터디 상세 쿼리를 한꺼번에 무효화합니다.
 *
 * 멤버십이 바뀌는 액션은 활성·비활성 관련 쿼리를 모두 다시 조회하고 그 시도가 끝날 때까지
 * mutation 성공을 확정하지 않습니다. 재조회 실패는 이미 성공한 서버 mutation을 실패로
 * 뒤집지 않으며, 화면은 마지막 정상 데이터를 유지합니다. 그 외 액션은 기존 범위대로
 * 활성 쿼리만 백그라운드에서 정정합니다.
 */
function useInvalidateStudyManagement(studyId: number) {
  const queryClient = useQueryClient();
  const sessionVersion = useAuthStore((state) => state.sessionVersion);

  return (
    options: {
      profile?: boolean;
      studies?: boolean;
      visitCalendar?: boolean;
      refetchAll?: boolean;
    } = {},
  ): Promise<void> => {
    const refetchType = options.refetchAll === true ? 'all' : 'active';
    const invalidations = [
      queryClient.invalidateQueries({
        queryKey: applicationsQueryRoot(studyId),
        refetchType,
      }),
      queryClient.invalidateQueries({ queryKey: membersQueryKey(studyId), refetchType }),
      queryClient.invalidateQueries({
        queryKey: studyDetailQueryKey(studyId),
        exact: true,
        refetchType,
      }),
      queryClient.invalidateQueries({ queryKey: homeSummaryQueryRoot, refetchType }),
    ];

    if (options.profile) {
      invalidations.push(
        queryClient.invalidateQueries({
          queryKey: myPageQueryKeys.profile(sessionVersion),
          exact: true,
          refetchType,
        }),
      );
    }
    if (options.studies) {
      invalidations.push(
        queryClient.invalidateQueries({ queryKey: myPageQueryKeys.studiesRoot, refetchType }),
      );
    }
    if (options.visitCalendar) {
      invalidations.push(
        queryClient.invalidateQueries({ queryKey: myPageQueryKeys.visitCalendarRoot, refetchType }),
      );
    }

    return Promise.all(invalidations).then(() => undefined);
  };
}

/**
 * 화면 밖(푸시 등)에서 스터디 상태가 바뀐 걸 알게 됐을 때, 그 스터디를 보여주는 캐시를
 * 한꺼번에 무효화합니다. 대상은 `useInvalidateStudyManagement`의 기본 조합(신청자·멤버·
 * 상세·홈 요약)에 마이페이지 스터디 목록을 더한 것입니다.
 *
 * 훅이 아니라 함수인 이유는 알림 리스너처럼 렌더 밖에서도 불러야 하기 때문입니다.
 * 화면에 붙어 있는 쿼리만 다시 조회하도록 기본 `refetchType`('active')을 그대로 씁니다.
 */
export function invalidateStudyCaches(queryClient: QueryClient, studyId: number): void {
  void queryClient.invalidateQueries({ queryKey: applicationsQueryRoot(studyId) });
  void queryClient.invalidateQueries({ queryKey: membersQueryKey(studyId) });
  void queryClient.invalidateQueries({ queryKey: studyDetailQueryKey(studyId), exact: true });
  void queryClient.invalidateQueries({ queryKey: homeSummaryQueryRoot });
  void queryClient.invalidateQueries({ queryKey: myPageQueryKeys.studiesRoot });
}

/** 승인·거절 응답을 신청자 목록과(승인이면) 스터디 상세 캐시에 즉시 반영합니다. */
function useApplyApplicationDecision(studyId: number) {
  const queryClient = useQueryClient();

  return (result: ApplicationDecisionResult): void => {
    queryClient.setQueriesData<ApplicationListResult>(
      { queryKey: applicationsQueryRoot(studyId) },
      (list) => (list === undefined ? list : applyDecisionToApplicationList(list, result)),
    );

    if (result.status !== 'APPROVED') {
      return;
    }
    queryClient.setQueryData<StudyDetail>(studyDetailQueryKey(studyId), (detail) =>
      detail === undefined ? detail : applyApprovalToStudyDetail(detail, result),
    );
  };
}

export function useApproveApplication(studyId: number) {
  const accessToken = useAuthStore((state) => state.accessToken);
  const sessionVersion = useAuthStore((state) => state.sessionVersion);
  const applyDecision = useApplyApplicationDecision(studyId);
  const invalidate = useInvalidateStudyManagement(studyId);

  return useMutation({
    mutationFn: (applicationId: number) =>
      approveApplication(studyId, applicationId, accessToken ?? undefined, sessionVersion),
    onSuccess: (result) => {
      applyDecision(result);
      return invalidate({ profile: true, studies: true, refetchAll: true });
    },
  });
}

export function useRejectApplication(studyId: number) {
  const accessToken = useAuthStore((state) => state.accessToken);
  const sessionVersion = useAuthStore((state) => state.sessionVersion);
  const applyDecision = useApplyApplicationDecision(studyId);
  const invalidate = useInvalidateStudyManagement(studyId);

  return useMutation({
    mutationFn: (applicationId: number) =>
      rejectApplication(studyId, applicationId, accessToken ?? undefined, sessionVersion),
    onSuccess: (result) => {
      applyDecision(result);
      void invalidate();
    },
  });
}

export function useKickMember(studyId: number) {
  const accessToken = useAuthStore((state) => state.accessToken);
  const sessionVersion = useAuthStore((state) => state.sessionVersion);
  const queryClient = useQueryClient();
  const invalidate = useInvalidateStudyManagement(studyId);

  return useMutation({
    mutationFn: (memberId: number) =>
      kickMember(studyId, memberId, accessToken ?? undefined, sessionVersion),
    onSuccess: (result) => {
      queryClient.setQueryData<StudyDetail>(studyDetailQueryKey(studyId), (detail) =>
        detail === undefined
          ? detail
          : {
              ...detail,
              currentMemberCount: result.currentMemberCount,
              capacity: result.capacity,
              status: isStudyDetailStatus(result.studyStatus) ? result.studyStatus : detail.status,
              memberSummary: detail.memberSummary.filter(
                (member) => member.memberId !== result.memberId,
              ),
            },
      );
      queryClient.setQueryData<StudyMemberListResult>(membersQueryKey(studyId), (members) =>
        members === undefined
          ? members
          : {
              ...members,
              currentMemberCount: result.currentMemberCount,
              capacity: result.capacity,
              members: members.members.filter((member) => member.memberId !== result.memberId),
            },
      );
      return invalidate({ profile: true, studies: true, refetchAll: true });
    },
  });
}

export function useLeaveStudy(studyId: number) {
  const accessToken = useAuthStore((state) => state.accessToken);
  const sessionVersion = useAuthStore((state) => state.sessionVersion);
  const invalidate = useInvalidateStudyManagement(studyId);

  return useMutation({
    mutationFn: () => leaveStudy(studyId, accessToken ?? undefined, sessionVersion),
    onSuccess: () =>
      invalidate({
        profile: true,
        studies: true,
        visitCalendar: true,
        refetchAll: true,
      }),
  });
}

export function useUpdateStudyDetails(studyId: number) {
  const accessToken = useAuthStore((state) => state.accessToken);
  const sessionVersion = useAuthStore((state) => state.sessionVersion);
  const queryClient = useQueryClient();
  const invalidate = useInvalidateStudyManagement(studyId);

  return useMutation({
    mutationFn: (input: UpdateStudyDetailsInput) =>
      updateStudyDetails(studyId, input, accessToken ?? undefined, sessionVersion),
    onSuccess: (result) => {
      queryClient.setQueryData<StudyDetail>(studyDetailQueryKey(studyId), (detail) =>
        detail === undefined
          ? detail
          : { ...detail, title: result.title, goal: result.goal, intro: result.intro ?? '' },
      );
      void invalidate({ studies: true });
    },
  });
}

export function useCloseRecruitment(studyId: number) {
  const accessToken = useAuthStore((state) => state.accessToken);
  const sessionVersion = useAuthStore((state) => state.sessionVersion);
  const invalidate = useInvalidateStudyManagement(studyId);

  return useMutation({
    mutationFn: () => closeRecruitment(studyId, accessToken ?? undefined, sessionVersion),
    onSuccess: () => {
      void invalidate({ studies: true });
    },
  });
}

/** 모집 마감 취소(다시 모집 중). 마감과 같은 화면(신청자·멤버·모집 상태)에 영향을 주므로 동일하게 무효화합니다. */
export function useReopenRecruitment(studyId: number) {
  const accessToken = useAuthStore((state) => state.accessToken);
  const sessionVersion = useAuthStore((state) => state.sessionVersion);
  const invalidate = useInvalidateStudyManagement(studyId);

  return useMutation({
    mutationFn: () => reopenRecruitment(studyId, accessToken ?? undefined, sessionVersion),
    onSuccess: () => {
      void invalidate({ studies: true });
    },
  });
}

export function useStudySchedule(studyId: number) {
  const accessToken = useAuthStore((state) => state.accessToken);
  const sessionVersion = useAuthStore((state) => state.sessionVersion);

  return useQuery({
    queryKey: scheduleQueryKey(studyId),
    enabled: accessToken !== null && Number.isFinite(studyId),
    queryFn: () => getStudySchedule(studyId, accessToken ?? undefined, sessionVersion),
  });
}

/**
 * 일정 등록·수정·삭제는 홈/상세 화면의 nextSchedule 표시에도 영향을 주므로 함께 무효화합니다.
 * 무효화를 기다리지 않는 이유는 `useInvalidateStudyManagement` 주석과 같습니다.
 */
function useInvalidateSchedule(studyId: number) {
  const queryClient = useQueryClient();

  return (): void => {
    void queryClient.invalidateQueries({ queryKey: scheduleQueryKey(studyId) });
    void queryClient.invalidateQueries({ queryKey: studyDetailQueryKey(studyId) });
    void queryClient.invalidateQueries({ queryKey: homeSummaryQueryRoot });
    void queryClient.invalidateQueries({ queryKey: myPageQueryKeys.studiesRoot });
    void queryClient.invalidateQueries({ queryKey: myPageQueryKeys.visitCalendarRoot });
  };
}

export function useCreateSchedule(studyId: number) {
  const accessToken = useAuthStore((state) => state.accessToken);
  const sessionVersion = useAuthStore((state) => state.sessionVersion);
  const invalidate = useInvalidateSchedule(studyId);

  return useMutation({
    mutationFn: (input: ScheduleInput) =>
      createStudySchedule(studyId, input, accessToken ?? undefined, sessionVersion),
    onSuccess: () => {
      invalidate();
    },
  });
}

export function useUpdateSchedule(studyId: number) {
  const accessToken = useAuthStore((state) => state.accessToken);
  const sessionVersion = useAuthStore((state) => state.sessionVersion);
  const invalidate = useInvalidateSchedule(studyId);

  return useMutation({
    mutationFn: (input: ScheduleUpdateInput) =>
      updateStudySchedule(studyId, input, accessToken ?? undefined, sessionVersion),
    onSuccess: () => {
      invalidate();
    },
  });
}

export function useDeleteSchedule(studyId: number) {
  const accessToken = useAuthStore((state) => state.accessToken);
  const sessionVersion = useAuthStore((state) => state.sessionVersion);
  const invalidate = useInvalidateSchedule(studyId);

  return useMutation({
    mutationFn: () => deleteStudySchedule(studyId, accessToken ?? undefined, sessionVersion),
    onSuccess: () => {
      invalidate();
    },
  });
}

export function useNotices(studyId: number, enabled = true) {
  const accessToken = useAuthStore((state) => state.accessToken);
  const sessionVersion = useAuthStore((state) => state.sessionVersion);

  return useInfiniteQuery({
    queryKey: noticesQueryKey(studyId),
    enabled: enabled && accessToken !== null && Number.isFinite(studyId),
    initialPageParam: undefined as number | undefined,
    queryFn: ({ pageParam }) =>
      getNotices(studyId, pageParam, accessToken ?? undefined, sessionVersion),
    getNextPageParam: (lastPage, _allPages, _lastPageParam, allPageParams) =>
      getNextNoticeCursor(lastPage, allPageParams),
  });
}

function useInvalidateNotices(studyId: number) {
  const queryClient = useQueryClient();
  return (): void => {
    void queryClient.invalidateQueries({ queryKey: noticesQueryKey(studyId) });
  };
}

export function useCreateNotice(studyId: number) {
  const accessToken = useAuthStore((state) => state.accessToken);
  const sessionVersion = useAuthStore((state) => state.sessionVersion);
  const invalidate = useInvalidateNotices(studyId);

  return useMutation({
    mutationFn: (content: string) =>
      createNotice(studyId, content, accessToken ?? undefined, sessionVersion),
    onSuccess: () => {
      invalidate();
    },
  });
}

export function useUpdateNotice(studyId: number) {
  const accessToken = useAuthStore((state) => state.accessToken);
  const sessionVersion = useAuthStore((state) => state.sessionVersion);
  const invalidate = useInvalidateNotices(studyId);

  return useMutation({
    mutationFn: ({ noticeId, content }: { noticeId: number; content: string }) =>
      updateNotice(studyId, noticeId, content, accessToken ?? undefined, sessionVersion),
    onSuccess: () => {
      invalidate();
    },
  });
}

export function useDeleteNotice(studyId: number) {
  const accessToken = useAuthStore((state) => state.accessToken);
  const sessionVersion = useAuthStore((state) => state.sessionVersion);
  const invalidate = useInvalidateNotices(studyId);

  return useMutation({
    mutationFn: (noticeId: number) =>
      deleteNotice(studyId, noticeId, accessToken ?? undefined, sessionVersion),
    onSuccess: () => {
      invalidate();
    },
  });
}
