import { useQuery, useQueryClient } from '@tanstack/react-query';
import { useLocalSearchParams, useRouter } from 'expo-router';
import { useEffect } from 'react';

import { GenerationSpinnerScreen } from '@/features/generation/GenerationSpinnerScreen';
import { homeSummaryQueryRoot } from '@/features/home/useHomeSummary';
import {
  getReportStatus,
  reportStatusQueryKey,
  ReportStatusApiError,
} from '@/features/report/api/reportStatus';

const FALLBACK_STAGE_MESSAGES: Record<string, string> = {
  RECORD_COLLECTION: '임장 기록을 수집하는 중…',
  STT_VALIDATION: '음성 기록을 확인하는 중…',
  NORMALIZATION: '참여자별 기록을 정리하는 중…',
  REPORT_GENERATION: '임장 의견을 분석하는 중…',
  EVIDENCE_MAPPING: '분석 결과와 근거를 연결하는 중…',
  RESULT_SAVING: '완성된 리포트를 저장하는 중…',
  COMPLETED: '리포트가 완성됐어요!',
};

export default function ReportGeneratingScreen() {
  const { reportId: rawReportId } = useLocalSearchParams<{ reportId: string }>();
  const reportId = Number(rawReportId);
  const isValidReportId = Number.isSafeInteger(reportId) && reportId >= 1;
  const router = useRouter();
  const queryClient = useQueryClient();

  const statusQuery = useQuery({
    queryKey: reportStatusQueryKey(reportId),
    enabled: isValidReportId,
    queryFn: () => getReportStatus(reportId),
    refetchInterval: (query) => {
      const status = query.state.data?.status;
      return status === 'DONE' || status === 'FAILED' ? false : 1_500;
    },
    retry: 2,
  });

  useEffect(() => {
    const status = statusQuery.data;
    if (status?.status === 'DONE' && status.detailAvailable) {
      void queryClient.invalidateQueries({ queryKey: homeSummaryQueryRoot });
      router.replace(`/report/${reportId}`);
    }
  }, [queryClient, reportId, router, statusQuery.data]);

  const status = statusQuery.data;
  const failedMessage =
    status?.status === 'FAILED'
      ? (status.failReason ?? '리포트를 생성하지 못했어요.')
      : !isValidReportId
        ? '올바른 리포트 번호가 아니에요.'
        : statusQuery.isError
          ? statusQuery.error instanceof ReportStatusApiError
            ? statusQuery.error.message
            : '리포트 생성 상태를 확인하지 못했어요.'
          : null;
  const isGenerationFailure = status?.status === 'FAILED' || !isValidReportId;
  const stageMessage = status
    ? status.progressMessage || FALLBACK_STAGE_MESSAGES[status.progressStage]
    : null;

  return (
    <GenerationSpinnerScreen
      actionLabel={isGenerationFailure ? '돌아가기' : '다시 확인'}
      errorMessage={failedMessage}
      message={stageMessage}
      onAction={
        failedMessage
          ? isGenerationFailure
            ? () => router.back()
            : () => void statusQuery.refetch()
          : undefined
      }
      onBack={() => router.back()}
      progress={status?.progressRate ?? 0}
      variant="report"
    />
  );
}
