import { useEffect, useState } from 'react';

import {
  getFieldVisitParticipants,
  type FieldVisitParticipantsResult,
} from '@/features/checklist/api/fieldVisit';

/** 대기 화면에서 "다른 N명을 기다리고 있어요"를 실시간으로 갱신하는 주기. */
const POLL_INTERVAL_MS = 5000;

/**
 * 참여자별 임장 상태(진행 중·종료·미참여)를 폴링합니다.
 *
 * 이 API(GET /field-visit/participants)는 아직 백엔드에 연동되지 않았습니다
 * (docs/API.md "연동여부: No"). 그래서 지금은 매 폴링이 실패하고 null을 그대로
 * 유지하며, 호출부(FieldVisitWaitingScreen)는 그동안 인원수 없는 안내문을 씁니다.
 * 배포되면 코드 변경 없이 다음 폴링부터 실시간 인원·캐릭터 배치가 채워집니다.
 */
export function useFieldVisitParticipants(
  studyId: number,
  shouldPoll: boolean,
): FieldVisitParticipantsResult | null {
  const [result, setResult] = useState<FieldVisitParticipantsResult | null>(null);

  useEffect(() => {
    if (!shouldPoll || !Number.isFinite(studyId)) return;

    let cancelled = false;

    const poll = async () => {
      try {
        const data = await getFieldVisitParticipants(studyId);
        if (!cancelled) setResult(data);
      } catch {
        // 아직 연동 전이거나 일시 오류. 다음 폴링에서 다시 시도합니다.
      }
    };

    void poll();
    const timer = setInterval(poll, POLL_INTERVAL_MS);

    return () => {
      cancelled = true;
      clearInterval(timer);
    };
  }, [shouldPoll, studyId]);

  return result;
}
