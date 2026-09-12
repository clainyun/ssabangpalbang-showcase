import { useCallback, useEffect, useState } from 'react';

import {
  FieldVisitRouteApiError,
  generateFieldVisitRoute,
  getFieldVisitRoute,
  type FieldVisitRoute,
} from '@/features/checklist/api/fieldVisitRoute';

/**
 * 팔방이 추천 경로를 읽고, 없으면 만들 수 있게 합니다.
 *
 * 경로가 없는 것은 오류가 아닙니다(아직 안 짠 상태). 그때 체크리스트는 기존
 * 카테고리 묶음으로 보여 주고, 경로가 생기면 경유지 순서 묶음으로 바뀝니다.
 *
 * 조회 실패도 화면을 막지 않습니다 — 경로는 체크리스트를 쓰는 데 필수가 아니라
 * 보기 방식일 뿐이라서, 실패하면 카테고리 묶음으로 그대로 씁니다.
 */
export function useFieldVisitRoute(studyId: number) {
  const [route, setRoute] = useState<FieldVisitRoute | null>(null);
  const [isGenerating, setIsGenerating] = useState(false);

  /**
   * 경로를 새로 짭니다. 실패 사유는 사용자에게 보여 줄 수 있어야 하므로
   * 여기서 삼키지 않고 문구를 돌려줍니다(성공이면 null).
   */
  const generate = useCallback(async (): Promise<string | null> => {
    setIsGenerating(true);
    try {
      setRoute(await generateFieldVisitRoute(studyId));
      return null;
    } catch (error) {
      return error instanceof FieldVisitRouteApiError
        ? error.message
        : '경로를 만들지 못했습니다.';
    } finally {
      setIsGenerating(false);
    }
  }, [studyId]);

  // 진입 시 경로를 조회하고, 아직 없으면(쓰기 가능한 진행 중 세션) 자동으로 생성합니다.
  // 별도 버튼 없이도 지도의 경유지 마커가 바로 떠 있게 하기 위함입니다.
  //
  // 임장 시작 직후 첫 진입에는 체크리스트가 아직 생성 중이라 경로 생성이
  // ROUTE_CHECKLIST_REQUIRED(409)로 실패할 수 있습니다. 이 경우 체크리스트가 곧 생기므로
  // 짧게 재시도해, 재진입 없이도 마커가 바로 뜨게 합니다. 그 외 실패(경유지 부족·외부
  // 서비스 등)나 조회 실패는 폴백(route=null, 카테고리 묶음)으로 두고 중단합니다.
  useEffect(() => {
    if (!Number.isFinite(studyId)) return;

    let cancelled = false;
    const wait = (ms: number) => new Promise((resolve) => setTimeout(resolve, ms));
    const MAX_ATTEMPTS = 10;
    const RETRY_DELAY_MS = 2000;

    void (async () => {
      for (let attempt = 0; attempt < MAX_ATTEMPTS; attempt += 1) {
        if (cancelled) return;

        let detail;
        try {
          detail = await getFieldVisitRoute(studyId);
        } catch {
          return; // 조회 실패 → 폴백 유지, 중단
        }
        if (cancelled) return;

        if (detail.route) {
          setRoute(detail.route);
          return;
        }
        if (detail.readOnly) {
          setRoute(null);
          return;
        }

        // 경로 없음 + 진행 중 → 생성 시도
        setIsGenerating(true);
        try {
          const generated = await generateFieldVisitRoute(studyId);
          if (!cancelled) setRoute(generated);
          return;
        } catch (error) {
          const checklistNotReady =
            error instanceof FieldVisitRouteApiError &&
            error.code === 'ROUTE_CHECKLIST_REQUIRED';
          if (!checklistNotReady) return; // 재시도해도 소용없는 실패 → 폴백 유지, 중단
          // 체크리스트가 아직 생성 중 → 잠시 후 재시도
        } finally {
          if (!cancelled) setIsGenerating(false);
        }

        await wait(RETRY_DELAY_MS);
      }
    })();

    return () => {
      cancelled = true;
    };
  }, [studyId]);

  const refreshRoute = useCallback(async () => {
    if (!Number.isFinite(studyId)) return;
    try {
      const detail = await getFieldVisitRoute(studyId);
      setRoute(detail.route);
    } catch {
      // 조회 실패 시 기존 경로를 그대로 유지합니다.
    }
  }, [studyId]);

  return { route, isGeneratingRoute: isGenerating, generateRoute: generate, refreshRoute };
}
