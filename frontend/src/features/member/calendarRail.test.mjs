import assert from 'node:assert/strict';
import { test } from 'node:test';

import { getCenteredCalendarRailOffset } from './calendarRail.ts';

test('선택한 날짜가 가운데에 오도록 스크롤 위치를 계산한다', () => {
  assert.equal(
    getCenteredCalendarRailOffset({ x: 205, width: 42 }, 192, 310),
    118,
  );
});

test('첫 날짜와 마지막 날짜는 스크롤 가능 범위를 벗어나지 않는다', () => {
  assert.equal(getCenteredCalendarRailOffset({ x: 5, width: 42 }, 192, 310), 0);
  assert.equal(getCenteredCalendarRailOffset({ x: 265, width: 42 }, 192, 310), 118);
});

test('날짜가 화면 너비보다 적으면 스크롤하지 않는다', () => {
  assert.equal(getCenteredCalendarRailOffset({ x: 75, width: 42 }, 240, 240), 0);
});

test('양끝 여백이 있으면 마지막 날짜도 정확히 가운데까지 이동한다', () => {
  assert.equal(getCenteredCalendarRailOffset({ x: 325, width: 42 }, 192, 442), 250);
});
