import { type QueryClient, useMutation, useQueryClient } from '@tanstack/react-query';

import { useAuthStore } from '@/store/authStore';

import { type ReportDetail, reportDetailQueryKey } from './reportDetail';
import {
  favoriteReport,
  type ReportFavoriteResult,
  unfavoriteReport,
} from './reportFavorite';

function updateReportDetailCache(
  queryClient: QueryClient,
  sessionVersion: number,
  result: ReportFavoriteResult,
) {
  queryClient.setQueryData<ReportDetail>(
    [...reportDetailQueryKey(result.reportId), sessionVersion],
    (current) =>
      current === undefined
        ? current
        : {
            ...current,
            favoritedByMe: result.favoritedByMe,
            favoriteCount: result.favoriteCount,
          },
  );
}

export function useToggleReportFavorite(reportId: number) {
  const queryClient = useQueryClient();
  const sessionVersion = useAuthStore((state) => state.sessionVersion);

  return useMutation({
    mutationFn: (nextFavorited: boolean) =>
      nextFavorited ? favoriteReport(reportId) : unfavoriteReport(reportId),
    onSuccess: (result) => {
      updateReportDetailCache(queryClient, sessionVersion, result);
      // 찜 탭(리포트 목록)이 즉시 반영되도록 무효화한다. 리포트 상세·커뮤니티 등
      // 어디서 토글해도 다음 진입 시 최신 찜 상태가 보인다.
      void queryClient.invalidateQueries({ queryKey: ['member', 'me', 'favorite-reports'] });
    },
  });
}
