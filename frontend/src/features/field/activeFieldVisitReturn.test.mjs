import assert from 'node:assert/strict';
import { test } from 'node:test';

import {
  resolveOngoingFieldVisitSelection,
  selectOngoingFieldVisitStudies,
} from './activeFieldVisitReturn.ts';

const activeCandidate = {
  studyId: 12,
  status: 'IN_PROGRESS',
  apartmentName: '싸방 아파트',
  hasReturnableFieldVisit: true,
};

const activeStatus = {
  studyId: 12,
  status: 'IN_PROGRESS',
  session: { sessionId: 34 },
  participant: { status: 'IN_PROGRESS' },
};

test('진행 중 상태이며 유효한 스터디만 중복 없이 목록에 남긴다', () => {
  assert.deepEqual(
    selectOngoingFieldVisitStudies([
      activeCandidate,
      activeCandidate,
      { ...activeCandidate, studyId: 0 },
      {
        ...activeCandidate,
        studyId: 13,
        status: 'CLOSED',
        hasReturnableFieldVisit: false,
      },
      { ...activeCandidate, studyId: 15, hasReturnableFieldVisit: false },
      { ...activeCandidate, studyId: 14, apartmentName: '다른 아파트' },
    ]),
    [activeCandidate, { ...activeCandidate, studyId: 14, apartmentName: '다른 아파트' }],
  );
});

test('진행 중인 내 임장은 선택한 세션의 복귀 대상으로 판별한다', () => {
  assert.deepEqual(resolveOngoingFieldVisitSelection(activeCandidate, activeStatus), {
    kind: 'OPEN_FIELD_VISIT',
    studyId: 12,
    sessionId: 34,
    apartmentName: '싸방 아파트',
  });
});

test('개인 임장을 종료했어도 진행 중인 세션 현황으로 돌아간다', () => {
  assert.deepEqual(
    resolveOngoingFieldVisitSelection(activeCandidate, {
      ...activeStatus,
      participant: { status: 'ENDED' },
    }),
    {
      kind: 'OPEN_FIELD_VISIT',
      studyId: 12,
      sessionId: 34,
      apartmentName: '싸방 아파트',
    },
  );
});

test('아직 참여하지 않았다면 기존 시작 흐름이 있는 스터디로 보낸다', () => {
  assert.deepEqual(
    resolveOngoingFieldVisitSelection(activeCandidate, {
      ...activeStatus,
      participant: null,
    }),
    { kind: 'OPEN_STUDY', studyId: 12 },
  );
});

test('선택 사이 세션이 종료되거나 식별자가 달라지면 이동하지 않는다', () => {
  assert.deepEqual(
    resolveOngoingFieldVisitSelection(activeCandidate, {
      ...activeStatus,
      status: 'ENDED',
    }),
    { kind: 'UNAVAILABLE' },
  );
  assert.deepEqual(
    resolveOngoingFieldVisitSelection(activeCandidate, { ...activeStatus, studyId: 99 }),
    { kind: 'UNAVAILABLE' },
  );
  assert.deepEqual(
    resolveOngoingFieldVisitSelection(activeCandidate, {
      ...activeStatus,
      session: { sessionId: 0 },
    }),
    { kind: 'UNAVAILABLE' },
  );
});

test('후보 또는 상태가 없으면 이동하지 않는다', () => {
  assert.deepEqual(resolveOngoingFieldVisitSelection(undefined, activeStatus), {
    kind: 'UNAVAILABLE',
  });
  assert.deepEqual(resolveOngoingFieldVisitSelection(activeCandidate, undefined), {
    kind: 'UNAVAILABLE',
  });
});
