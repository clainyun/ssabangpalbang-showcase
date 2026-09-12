import assert from 'node:assert/strict';
import { test } from 'node:test';

import { formatNotificationBody, formatNotificationTime } from './notificationTime.ts';

test('서로 다른 offset의 같은 시각을 동일한 한국 시각으로 표시한다', () => {
  const fromUtc = formatNotificationTime('2026-08-12T14:16:00Z');
  const fromSeoul = formatNotificationTime('2026-08-12T23:16:00+09:00');

  assert.equal(fromUtc, fromSeoul);
  assert.match(fromUtc, /8월/);
  assert.match(fromUtc, /12일/);
  assert.match(fromUtc, /오후/);
  assert.match(fromUtc, /11:16/);
});

test('해석할 수 없는 시각은 원문을 유지한다', () => {
  assert.equal(formatNotificationTime('시간 정보 없음'), '시간 정보 없음');
});

test('기존 알림 본문의 ISO 시각도 한국어 서울 시각으로 바꾼다', () => {
  assert.equal(
    formatNotificationBody(
      "'옥수 스터디' 임장이 2026-08-12T14:16:00Z에 옥수역에서 시작해요.",
      'STUDY_SCHEDULE_REMINDER',
    ),
    "'옥수 스터디' 임장이 8월 12일 오후 11시 16분에 옥수역에서 시작해요.",
  );
});

test('회원이 보낸 쪽지 내용은 시간처럼 보여도 변경하지 않는다', () => {
  const body = '2026-08-12T14:16:00Z에 만나요.';
  assert.equal(formatNotificationBody(body, 'MESSAGE'), body);
});
