import { useCallback, useEffect, useState } from 'react';

import {
  getFieldVisitStatus,
  type FieldVisitCloseVoteStatus,
} from '@/features/checklist/api/fieldVisit';

/**
 * 전체 임장 강제 종료(과반수 투표) 현황을 읽어 옵니다.
 *
 * 체크리스트 본문과 생명주기가 다릅니다 — 체크리스트는 내 진행 상황이고, 이쪽은
 * 다른 참여자가 던진 표까지 포함한 세션 전체 상태라 useChecklist 와 섞지 않고
 * 따로 뒀습니다.
 *
 * 실패해도 화면을 막지 않습니다. 투표 현황은 체크리스트를 쓰는 데 필수가 아니라
 * 부가 정보라서, 조회가 안 되면 강제 종료 줄만 감춥니다(null 반환).
 */
export function useCloseVote(studyId: number) {
  const [status, setStatus] = useState<FieldVisitCloseVoteStatus | null>(null);

  const fetchStatus = useCallback(async (): Promise<FieldVisitCloseVoteStatus | null> => {
    if (!Number.isFinite(studyId)) return null;
    try {
      const result = await getFieldVisitStatus(studyId);
      return result.closeVote;
    } catch {
      return null;
    }
  }, [studyId]);

  // 응답이 돌아오기 전에 화면을 떠나거나 studyId 가 바뀌면 늦게 도착한 값으로
  // 상태를 덮어쓰지 않도록 버립니다.
  useEffect(() => {
    let cancelled = false;

    void (async () => {
      const next = await fetchStatus();
      if (!cancelled) setStatus(next);
    })();

    return () => {
      cancelled = true;
    };
  }, [fetchStatus]);

  const refreshCloseVote = useCallback(async () => {
    setStatus(await fetchStatus());
  }, [fetchStatus]);

  return { closeVote: status, refreshCloseVote };
}
