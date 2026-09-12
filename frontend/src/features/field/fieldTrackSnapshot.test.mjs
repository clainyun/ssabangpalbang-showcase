import assert from 'node:assert/strict';
import { test } from 'node:test';

import {
  FIELD_TRACK_SNAPSHOT_VERSION,
  parseFieldTrackSnapshot,
  serializeFieldTrackSnapshot,
} from './fieldTrackSnapshot.ts';

const NOW = 1_800_000_000_000;
const SESSION_ID = 'session-42';

const options = {
  sessionId: SESSION_ID,
  maxPoints: 5_000,
  maxAgeMs: 24 * 60 * 60 * 1_000,
  now: NOW,
};

const track = {
  sessionId: SESSION_ID,
  points: [
    { coordinate: [127.0848, 37.5132], timestamp: NOW - 20_000 },
    { coordinate: [127.0852, 37.5136], timestamp: NOW - 10_000 },
  ],
  startedAt: NOW - 30_000,
  endedAt: null,
  totalDistanceMeters: 61.5,
};

test('같은 세션이면 좌표·누적거리·시작시각을 그대로 되살린다', () => {
  assert.deepEqual(parseFieldTrackSnapshot(serializeFieldTrackSnapshot(track), options), track);
});

test('다른 세션의 궤적은 되살리지 않는다', () => {
  assert.equal(
    parseFieldTrackSnapshot(serializeFieldTrackSnapshot(track), {
      ...options,
      sessionId: 'session-43',
    }),
    null,
  );
});

test('하루가 지난 궤적은 되살리지 않는다', () => {
  assert.equal(
    parseFieldTrackSnapshot(serializeFieldTrackSnapshot(track), {
      ...options,
      now: NOW + 24 * 60 * 60 * 1_000 + 10_001,
    }),
    null,
  );
});

test('손상되었거나 형식이 다른 파일은 통째로 버린다', () => {
  const snapshot = JSON.parse(serializeFieldTrackSnapshot(track));

  assert.equal(parseFieldTrackSnapshot('{', options), null, 'JSON 이 깨진 경우');
  assert.equal(parseFieldTrackSnapshot('null', options), null, 'null 인 경우');
  assert.equal(
    parseFieldTrackSnapshot(JSON.stringify({ ...snapshot, version: 0 }), options),
    null,
    '형식 버전이 다른 경우',
  );
  assert.equal(
    parseFieldTrackSnapshot(JSON.stringify({ ...snapshot, points: 'nope' }), options),
    null,
    '좌표 배열이 아닌 경우',
  );
  assert.equal(
    parseFieldTrackSnapshot(JSON.stringify(snapshot), { ...options, maxPoints: 1 }),
    null,
    '점 개수 상한을 넘는 경우',
  );
});

test('값이 이상한 점만 골라 버리고 나머지는 살린다', () => {
  const snapshot = {
    version: FIELD_TRACK_SNAPSHOT_VERSION,
    sessionId: SESSION_ID,
    startedAt: NOW - 30_000,
    totalDistanceMeters: -1,
    points: [
      [127.0848, 37.5132, NOW - 20_000],
      [999, 37.5132, NOW - 15_000],
      [127.0848, null, NOW - 14_000],
      ['127.0848', 37.5132, NOW - 13_000],
      [127.0852, 37.5136, NOW - 10_000],
    ],
  };

  assert.deepEqual(parseFieldTrackSnapshot(JSON.stringify(snapshot), options), {
    sessionId: SESSION_ID,
    points: track.points,
    startedAt: NOW - 30_000,
    endedAt: null,
    // 음수 누적거리는 믿지 않고 0 으로 되돌립니다.
    totalDistanceMeters: 0,
  });
});
