export interface OngoingFieldVisitStudy {
  studyId: number;
  status: string;
  apartmentName: string | null;
  /** 현재 사용자가 실제로 돌아갈 수 있는 진행 중 임장 세션 보유 여부(서버 계산). */
  hasReturnableFieldVisit: boolean;
}

export interface ActiveFieldVisitStatusSnapshot {
  studyId: number;
  status: string;
  session: { sessionId: number } | null;
  participant: { status: string } | null;
}

export type OngoingFieldVisitSelection =
  | {
      kind: 'OPEN_FIELD_VISIT';
      studyId: number;
      sessionId: number;
      apartmentName: string | null;
    }
  | { kind: 'OPEN_STUDY'; studyId: number }
  | { kind: 'UNAVAILABLE' };

function isPositiveSafeInteger(value: number | null | undefined): value is number {
  return Number.isSafeInteger(value) && (value ?? 0) > 0;
}

export function selectOngoingFieldVisitStudies<T extends OngoingFieldVisitStudy>(
  studies: readonly T[],
): T[] {
  const seenStudyIds = new Set<number>();

  return studies.filter((study) => {
    if (
      !study.hasReturnableFieldVisit ||
      !isPositiveSafeInteger(study.studyId) ||
      seenStudyIds.has(study.studyId)
    ) {
      return false;
    }

    seenStudyIds.add(study.studyId);
    return true;
  });
}

export function resolveOngoingFieldVisitSelection(
  candidate: OngoingFieldVisitStudy | null | undefined,
  status: ActiveFieldVisitStatusSnapshot | null | undefined,
): OngoingFieldVisitSelection {
  if (
    candidate?.status !== 'IN_PROGRESS' ||
    !isPositiveSafeInteger(candidate.studyId) ||
    !status ||
    status.studyId !== candidate.studyId ||
    status.status !== 'IN_PROGRESS' ||
    !isPositiveSafeInteger(status.session?.sessionId)
  ) {
    return { kind: 'UNAVAILABLE' };
  }

  if (status.participant === null) {
    return { kind: 'OPEN_STUDY', studyId: candidate.studyId };
  }

  if (status.participant.status !== 'IN_PROGRESS' && status.participant.status !== 'ENDED') {
    return { kind: 'UNAVAILABLE' };
  }

  return {
    kind: 'OPEN_FIELD_VISIT',
    studyId: candidate.studyId,
    sessionId: status.session.sessionId,
    apartmentName: candidate.apartmentName,
  };
}
