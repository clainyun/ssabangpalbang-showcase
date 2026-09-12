import type { ChecklistBody, ChecklistDetail, ChecklistGenerateResult } from './api/checklist';

export interface ResolvedGeneratedChecklist {
  body: ChecklistBody;
  readOnly: boolean;
}

interface GenerateAndRefreshChecklistOptions {
  generate: () => Promise<ChecklistGenerateResult>;
  refresh: () => Promise<ChecklistDetail>;
  isNetworkError: (error: unknown) => boolean;
  onGenerated: (body: ChecklistBody) => void;
}

export class ChecklistRefreshContextError extends Error {
  constructor() {
    super('체크리스트 생성 중 임장 상태가 변경되었습니다.');
    this.name = 'ChecklistRefreshContextError';
  }
}

/** 생성 API의 카테고리 집계를 조회 API와 같은 화면 모델로 맞춥니다. */
export function checklistBodyFromGeneration(generated: ChecklistGenerateResult): ChecklistBody {
  return {
    checklistId: generated.checklistId,
    isFallback: generated.isFallback,
    generatedAt: generated.generatedAt,
    completedCount: generated.completedCount,
    totalCount: generated.totalCount,
    categories: generated.categories.map((category) => ({
      category: category.category,
      completedCount: category.items.filter((item) => item.isCompleted).length,
      totalCount: category.itemCount,
      items: category.items,
    })),
  };
}

/** 생성 뒤 재조회 결과를 우선하되, 아직 반영되지 않았으면 생성 결과를 유지합니다. */
export function resolveChecklistAfterGeneration(
  generated: ChecklistGenerateResult,
  generatedBody: ChecklistBody,
  refreshedDetail: ChecklistDetail | null,
): ResolvedGeneratedChecklist {
  if (
    refreshedDetail !== null &&
    (refreshedDetail.studyId !== generated.studyId ||
      refreshedDetail.sessionId !== generated.sessionId ||
      (refreshedDetail.checklist !== null &&
        refreshedDetail.checklist.checklistId !== generated.checklistId))
  ) {
    throw new ChecklistRefreshContextError();
  }

  return {
    body: refreshedDetail?.checklist ?? generatedBody,
    // 재조회 네트워크 실패 시 최신 권한을 알 수 없으므로 수정 기능은 잠급니다.
    readOnly: refreshedDetail?.readOnly ?? true,
  };
}

/** 생성 → 완료 알림 → 공개 API 재조회 순서를 한곳에 고정해 회귀 테스트할 수 있게 합니다. */
export async function generateAndRefreshChecklist(
  options: GenerateAndRefreshChecklistOptions,
): Promise<ResolvedGeneratedChecklist> {
  const generated = await options.generate();
  const generatedBody = checklistBodyFromGeneration(generated);
  options.onGenerated(generatedBody);

  let refreshedDetail: ChecklistDetail | null = null;
  try {
    refreshedDetail = await options.refresh();
  } catch (error) {
    if (!options.isNetworkError(error)) throw error;
  }

  return resolveChecklistAfterGeneration(generated, generatedBody, refreshedDetail);
}
