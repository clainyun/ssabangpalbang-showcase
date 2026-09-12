import { useEffect, useRef, useState } from 'react';

import { getStudyDetail } from '@/features/study/api/getStudyDetail';

export interface WaitingReportHint {
  reportId: number;
  status: string;
}

/**
 * 대기 폴링 주기. 리포트 생성 자체는 report-generating 화면이 1.5초 간격으로 훨씬
 * 촘촘하게 조회하므로, 여기서는 "세션이 끝났는지"만 느슨하게 확인하면 됩니다.
 */
const POLL_INTERVAL_MS = 4000;

/**
 * 내 임장은 끝났지만(readOnly) 세션 전체는 아직 안 끝난 동안, 다른 참여자가 마지막으로
 * 끝내거나 과반 강제종료로 세션이 끝나는 순간을 감지합니다.
 *
 * field-visit 상태 조회(GET /field-visit)에는 reportId가 없어서, 세션 종료 여부와
 * reportId를 한 번에 주는 스터디 상세(GET /studies/{studyId})를 폴링합니다. 조회
 * 실패는 다음 폴링에서 재시도하고 화면을 막지 않습니다.
 */
export function useFieldVisitWaitingReport(
  studyId: number,
  shouldPoll: boolean,
): WaitingReportHint | null {
  const [report, setReport] = useState<WaitingReportHint | null>(null);
  const foundRef = useRef<WaitingReportHint | null>(null);

  useEffect(() => {
    // readOnly(shouldPoll)는 이 세션 안에서 true → false로 되돌아가지 않으므로
    // 리셋할 일이 없습니다. 폴링을 안 하면 그만입니다.
    if (!shouldPoll) return;

    let cancelled = false;

    const poll = async () => {
      if (foundRef.current !== null) return;
      try {
        const detail = await getStudyDetail(studyId);
        if (cancelled || detail.fieldVisitStatus !== 'ENDED' || detail.report === null) return;
        const found: WaitingReportHint = {
          reportId: detail.report.reportId,
          status: detail.report.status,
        };
        foundRef.current = found;
        setReport(found);
      } catch {
        // 다음 폴링에서 재시도합니다.
      }
    };

    void poll();
    const timer = setInterval(poll, POLL_INTERVAL_MS);

    return () => {
      cancelled = true;
      clearInterval(timer);
    };
  }, [shouldPoll, studyId]);

  return report;
}
