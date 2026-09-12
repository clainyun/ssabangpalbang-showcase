import { authenticatedFetch } from '@/lib/authenticatedFetch';

interface ApiEnvelope<T> {
  success: boolean;
  code: string;
  message: string;
  data: T | null;
  timestamp: string;
}

export interface ReportFavoriteResult {
  reportId: number;
  favoritedByMe: boolean;
  favoriteCount: number;
}

interface FavoriteResponseData {
  reportId: number;
  favoritedByMe: boolean;
  favoriteCount: number;
  favoritedAt?: string;
}

interface UnfavoriteResponseData {
  reportId: number;
  favoritedByMe: boolean;
  favoriteCount: number;
  unfavoritedAt?: string;
}

export class ReportFavoriteApiError extends Error {
  code: string;

  constructor(code: string, message: string) {
    super(message);
    this.name = 'ReportFavoriteApiError';
    this.code = code;
  }
}

function assertValidReportId(reportId: number): void {
  if (!Number.isSafeInteger(reportId) || reportId < 1) {
    throw new ReportFavoriteApiError(
      'COMMON_INVALID_REQUEST',
      '올바른 리포트 번호가 아니에요.',
    );
  }
}

export async function favoriteReport(reportId: number): Promise<ReportFavoriteResult> {
  assertValidReportId(reportId);

  const response = await authenticatedFetch(`/api/v1/reports/${reportId}/favorite`, {
    method: 'PUT',
    headers: { Accept: 'application/json' },
  });
  const body = (await response.json().catch(() => null)) as ApiEnvelope<FavoriteResponseData> | null;

  if (!response.ok || !body?.success || body.data === null) {
    throw new ReportFavoriteApiError(
      body?.code ?? 'UNKNOWN',
      body?.message?.trim() || '리포트를 찜하지 못했어요.',
    );
  }

  return {
    reportId: body.data.reportId,
    favoritedByMe: body.data.favoritedByMe,
    favoriteCount: body.data.favoriteCount,
  };
}

export async function unfavoriteReport(reportId: number): Promise<ReportFavoriteResult> {
  assertValidReportId(reportId);

  const response = await authenticatedFetch(`/api/v1/reports/${reportId}/favorite`, {
    method: 'DELETE',
    headers: { Accept: 'application/json' },
  });
  const body = (await response
    .json()
    .catch(() => null)) as ApiEnvelope<UnfavoriteResponseData> | null;

  if (!response.ok || !body?.success || body.data === null) {
    throw new ReportFavoriteApiError(
      body?.code ?? 'UNKNOWN',
      body?.message?.trim() || '리포트 찜을 해제하지 못했어요.',
    );
  }

  return {
    reportId: body.data.reportId,
    favoritedByMe: body.data.favoritedByMe,
    favoriteCount: body.data.favoriteCount,
  };
}
