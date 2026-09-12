import { useEffect, useState } from 'react';

import { getFieldVisitStatus } from '@/features/checklist/api/fieldVisit';

/**
 * 임장 지도에서 "참여 중 N명 / 종료 M명"을 준실시간으로 보여 주려고 임장 상태
 * (GET /field-visit)를 5초마다 조회해 상태별 참여자 수를 돌려줍니다.
 *
 * 임장 참여는 동시에 몰리지 않으니(누군가 참여/종료할 때만 값이 바뀜) 5초 폴링으로
 * 충분합니다. 완전한 실시간(WebSocket)은 임장 세션에 채널이 없어 도입하지 않았고,
 * 채팅과 달리 이 화면은 몇 초 지연을 허용합니다.
 *
 * 조회 실패 시 화면을 막지 않고 직전 값을 유지합니다(카운트는 부가 정보). 요청이
 * 겹치지 않도록 응답이 온 뒤에 다음 폴링을 예약합니다(setTimeout 재귀).
 */
const POLL_INTERVAL_MS = 5000;

export interface FieldVisitParticipantCounts {
  active: number;
  ended: number;
}

export function useFieldVisitParticipantCounts(
  studyId: number,
): FieldVisitParticipantCounts | null {
  const [counts, setCounts] = useState<FieldVisitParticipantCounts | null>(null);

  useEffect(() => {
    if (!Number.isFinite(studyId)) return;

    let cancelled = false;
    let timer: ReturnType<typeof setTimeout> | null = null;

    const poll = async () => {
      try {
        const result = await getFieldVisitStatus(studyId);
        if (cancelled) return;
        const session = result.session;
        setCounts(
          session === null
            ? { active: 0, ended: 0 }
            : {
                active: session.activeParticipantCount ?? 0,
                ended: session.endedParticipantCount ?? 0,
              },
        );
      } catch {
        // 조회 실패 시 직전 값을 유지합니다.
      } finally {
        if (!cancelled) timer = setTimeout(() => void poll(), POLL_INTERVAL_MS);
      }
    };

    void poll();

    return () => {
      cancelled = true;
      if (timer) clearTimeout(timer);
    };
  }, [studyId]);

  return counts;
}
