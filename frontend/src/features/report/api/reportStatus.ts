import { authenticatedFetch, rethrowSessionFailure } from '@/lib/authenticatedFetch';

export type ReportGenerationStatus = 'PENDING' | 'IN_PROGRESS' | 'DONE' | 'FAILED';

export interface ReportStatusResult {
  reportId: number;
  status: ReportGenerationStatus;
  progressRate: number;
  progressStage: string;
  progressMessage: string;
  detailAvailable: boolean;
  retryAvailable: boolean;
  failReason: string | null;
  createdAt: string;
  completedAt: string | null;
  updatedAt: string;
}

interface ApiEnvelope<T> {
  success: boolean;
  code: string;
  message: string;
  data: T | null;
}

export class ReportStatusApiError extends Error {
  code: string;

  constructor(code: string, message: string) {
    super(message);
    this.name = 'ReportStatusApiError';
    this.code = code;
  }
}

export const reportStatusQueryKey = (reportId: number) => ['report', reportId, 'status'] as const;

export async function getReportStatus(reportId: number): Promise<ReportStatusResult> {
  if (!Number.isSafeInteger(reportId) || reportId < 1) {
    throw new ReportStatusApiError('COMMON_INVALID_REQUEST', '올바른 리포트 번호가 아니에요.');
  }

  let response: Response;
  try {
    response = await authenticatedFetch(`/api/v1/reports/${reportId}/status`, {
      method: 'GET',
      headers: { Accept: 'application/json' },
    });
  } catch (error) {
    rethrowSessionFailure(error);
    throw new ReportStatusApiError(
      'NETWORK_ERROR',
      '리포트 생성 상태를 확인하지 못했어요. 네트워크 연결을 확인해 주세요.',
    );
  }

  const body = (await response.json().catch(() => null)) as ApiEnvelope<ReportStatusResult> | null;
  if (!response.ok || !body?.success || body.data === null) {
    throw new ReportStatusApiError(
      body?.code ?? 'UNKNOWN',
      body?.message ?? '리포트 생성 상태를 확인하지 못했어요.',
    );
  }

  return body.data;
}
