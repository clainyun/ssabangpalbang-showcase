import { authenticatedFetch } from '@/lib/authenticatedFetch';

interface ApiEnvelope<T> {
  success: boolean;
  code: string;
  message: string;
  data: T | null;
  timestamp: string;
}

export type ReportOpinionType = 'POSITIVE' | 'CAUTION';

export interface ReportFeature {
  rank: number;
  label: string;
  summary: string;
  mentionCount: number;
  mentionRate: number;
  sourceIds: number[];
}

export interface ReportParticipantOpinion {
  participantLabel: string;
  opinionType: ReportOpinionType;
  summary: string;
  sourceIds: number[];
}

export interface ReportCategory {
  category: string;
  summary: string;
  checklistItemCount: number;
  positiveOpinionCount: number;
  positiveOpinionRate: number;
  cautionOpinionCount: number;
  cautionOpinionRate: number;
  unrecordedOpinionCount: number;
  unrecordedOpinionRate: number;
  dataSufficient: boolean;
  participantOpinions: ReportParticipantOpinion[];
}

export interface ReportCommonOpinion {
  category: string;
  label: string;
  opinionType: ReportOpinionType;
  summary: string;
  participantCount: number;
  participantRate: number;
  sourceIds: number[];
}

export interface ReportConflictingOpinion {
  category: string;
  label: string;
  summary: string;
  positiveParticipantCount: number;
  positiveParticipantRate: number;
  cautionParticipantCount: number;
  cautionParticipantRate: number;
  sourceIds: number[];
}

export interface ReportDetail {
  reportId: number;
  status: 'DONE';
  progressStage: 'COMPLETED';
  title: string;
  summary: string;
  apartment: {
    apartmentId: number;
    name: string;
    address: string;
    householdCount: number | null;
    completionYearMonth: string | null;
    parkingSpaceCount: number | null;
  };
  study: {
    studyId: number;
    title: string;
    goal: string | null;
    visitedAt: string | null;
    participantCount: number;
  };
  metrics: {
    totalChecklistItemCount: number;
    completedChecklistItemCount: number;
    averageCompletionRate: number;
    fieldRecordCount: number;
    evidenceCount: number;
    hasAiEvidence: boolean;
  };
  topPositiveFeatures: ReportFeature[];
  topCautionFeatures: ReportFeature[];
  commonOpinions: ReportCommonOpinion[];
  conflictingOpinions: ReportConflictingOpinion[];
  categories: ReportCategory[];
  viewer: {
    isParticipant: boolean;
    canViewEvidenceList: boolean;
    canViewEvidenceOriginal: boolean;
    canFavorite: boolean;
  };
  favoritedByMe: boolean;
  favoriteCount: number;
  postId: number | null;
  completedAt: string;
  updatedAt: string;
}

export class ReportDetailApiError extends Error {
  code: string;

  constructor(code: string, message: string) {
    super(message);
    this.name = 'ReportDetailApiError';
    this.code = code;
  }
}

export const reportDetailQueryKey = (reportId: number) => ['report', reportId, 'detail'] as const;

export async function getReportDetail(reportId: number): Promise<ReportDetail> {
  if (!Number.isSafeInteger(reportId) || reportId < 1) {
    throw new ReportDetailApiError(
      'COMMON_INVALID_REQUEST',
      '올바른 리포트 번호가 아니에요.',
    );
  }

  const response = await authenticatedFetch(`/api/v1/reports/${reportId}`, {
    method: 'GET',
    headers: { Accept: 'application/json' },
  });
  const body = (await response.json().catch(() => null)) as ApiEnvelope<ReportDetail> | null;

  if (!response.ok || !body?.success || body.data === null) {
    throw new ReportDetailApiError(
      body?.code ?? 'UNKNOWN',
      body?.message?.trim() || '리포트를 불러오지 못했어요.',
    );
  }

  return body.data;
}
