import { authenticatedFetch, rethrowSessionFailure } from '@/lib/authenticatedFetch';

/**
 * 임장 AI 체크리스트 생성·조회·완료 저장 API (FE-015 / AI-002).
 *
 * 타입은 docs/API.md 예시가 아니라 백엔드 응답 DTO 실물을 기준으로 정의했습니다
 * (backend/.../fieldvisit/dto/response/ChecklistDetailResponse.java 등).
 */

export interface ChecklistItem {
  checklistItemId: number;
  title: string;
  subtitle: string | null;
  displayOrder: number;
  isCompleted: boolean;
  completedAt: string | null;
  recordCount: number;
}

export interface ChecklistCategory {
  category: string;
  completedCount: number;
  totalCount: number;
  items: ChecklistItem[];
}

export interface ChecklistBody {
  checklistId: number;
  /** AI 생성이 실패해 기본 체크리스트로 대체됐는지. */
  isFallback: boolean;
  generatedAt: string;
  completedCount: number;
  totalCount: number;
  categories: ChecklistCategory[];
}

export interface ChecklistDetail {
  studyId: number;
  sessionId: number | null;
  participantStatus: string | null;
  /** 이미 종료한 참여자는 체크·기록을 수정할 수 없습니다(FE-015 예외 처리). */
  readOnly: boolean;
  /** 아직 생성 전이면 null. 이때는 generateChecklist 를 호출합니다. */
  checklist: ChecklistBody | null;
}

interface ApiEnvelope<T> {
  success: boolean;
  code: string;
  message: string;
  data: T | null;
}

export class ChecklistApiError extends Error {
  code: string;

  constructor(code: string, message: string) {
    super(message);
    this.name = 'ChecklistApiError';
    this.code = code;
  }
}

async function request<T>(path: string, init: RequestInit, fallbackMessage: string): Promise<T> {
  let response: Response;
  try {
    response = await authenticatedFetch(path, {
      headers: { Accept: 'application/json', ...(init.headers ?? {}) },
      ...init,
    });
  } catch (error) {
    rethrowSessionFailure(error);
    throw new ChecklistApiError('NETWORK_ERROR', '네트워크 연결을 확인해 주세요.');
  }

  const body = (await response.json().catch(() => null)) as ApiEnvelope<T> | null;

  if (!response.ok || !body?.success || body.data === null) {
    throw new ChecklistApiError(body?.code ?? 'UNKNOWN', body?.message ?? fallbackMessage);
  }

  return body.data;
}

export function getChecklist(studyId: number): Promise<ChecklistDetail> {
  return request<ChecklistDetail>(
    `/api/v1/studies/${studyId}/field-visit/checklist`,
    { method: 'GET' },
    '체크리스트를 불러오지 못했습니다.',
  );
}

/**
 * 생성 응답은 조회 응답과 모양이 다릅니다 — 카테고리에 completedCount/totalCount 가
 * 없고 itemCount 하나뿐입니다(ChecklistGenerateResponse.java). useChecklist 가 항목의
 * isCompleted 를 세어 조회 응답과 같은 모양으로 맞춥니다.
 */
export interface ChecklistGenerateItem {
  checklistItemId: number;
  title: string;
  subtitle: string | null;
  displayOrder: number;
  isCompleted: boolean;
  completedAt: string | null;
  recordCount: number;
}

export interface ChecklistGenerateCategory {
  category: string;
  itemCount: number;
  items: ChecklistGenerateItem[];
}

export interface ChecklistGenerateResult {
  studyId: number;
  sessionId: number;
  checklistId: number;
  isFallback: boolean;
  generatedAt: string;
  completedCount: number;
  totalCount: number;
  categories: ChecklistGenerateCategory[];
}

/**
 * 개인 맞춤 체크리스트를 생성합니다. getChecklist 의 checklist 가 null 일 때만 호출합니다
 * (이미 있으면 서버가 200 + 기존 체크리스트를 그대로 돌려주므로 호출해도 안전합니다).
 */
export function generateChecklist(
  studyId: number,
  attemptId: string,
): Promise<ChecklistGenerateResult> {
  return request<ChecklistGenerateResult>(
    `/api/v1/studies/${studyId}/field-visit/checklist/generate`,
    {
      method: 'POST',
      headers: { 'X-Checklist-Generation-Attempt-Id': attemptId },
    },
    'AI 체크리스트를 생성하지 못했습니다.',
  );
}

export type ChecklistGenerationStatusValue = 'PENDING' | 'IN_PROGRESS' | 'DONE' | 'FAILED';

export interface ChecklistGenerationStatus {
  attemptId: string | null;
  status: ChecklistGenerationStatusValue;
  progressRate: number;
  progressStage: string;
  progressMessage: string;
  updatedAt: string | null;
}

/** 진행 중인 동기식 생성 요청이 백엔드의 어느 처리 단계에 있는지 조회합니다. */
export function getChecklistGenerationStatus(
  studyId: number,
  attemptId: string,
): Promise<ChecklistGenerationStatus> {
  return request<ChecklistGenerationStatus>(
    `/api/v1/studies/${studyId}/field-visit/checklist/generate/status`,
    {
      method: 'GET',
      headers: { 'X-Checklist-Generation-Attempt-Id': attemptId },
    },
    '체크리스트 생성 상태를 불러오지 못했습니다.',
  );
}

export interface ChecklistAnswerSaveResult {
  studyId: number;
  checklistId: number;
  savedCount: number;
  completedCount: number;
  totalCount: number;
  answers: { checklistItemId: number; isCompleted: boolean; completedAt: string | null }[];
  categoryProgress: { category: string; completedCount: number; totalCount: number }[];
}

/**
 * 완료 상태를 서버에 즉시 저장합니다. 한 항목만 바뀌어도 배열로 보냅니다(서버 계약이
 * 일괄 저장 방식입니다).
 */
export function saveChecklistAnswers(
  studyId: number,
  answers: { checklistItemId: number; isCompleted: boolean }[],
): Promise<ChecklistAnswerSaveResult> {
  return request<ChecklistAnswerSaveResult>(
    `/api/v1/studies/${studyId}/field-visit/checklist/answers`,
    {
      method: 'PUT',
      headers: { 'Content-Type': 'application/json' },
      body: JSON.stringify({ answers }),
    },
    '체크 상태를 저장하지 못했습니다.',
  );
}
