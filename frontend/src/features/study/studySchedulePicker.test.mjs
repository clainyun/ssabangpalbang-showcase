import assert from 'node:assert/strict';
import { test } from 'node:test';

import {
  createScheduleTimePickerState,
  resolveScheduleTimePickerSelection,
} from './studySchedulePicker.ts';

test('빈 종료 시각 picker를 열어도 값은 선택 완료 전까지 만들어지지 않는다', () => {
  const startAt = new Date('2026-08-06T10:00:00+09:00');
  const picker = createScheduleTimePickerState('end', startAt, null);

  assert.ok(picker);
  assert.equal(resolveScheduleTimePickerSelection(picker, 'dismissed'), null);
});

test('종료 시각 선택 완료 이벤트에서만 합성 기준 시각을 실제 값으로 확정한다', () => {
  const startAt = new Date('2026-08-06T10:00:00+09:00');
  const picker = createScheduleTimePickerState('end', startAt, null);
  const selected = new Date('2026-08-06T11:30:00+09:00');
  const result = resolveScheduleTimePickerSelection(picker, 'set', selected);

  assert.ok(result);
  assert.equal(result.field, 'end');
  assert.equal(result.value.getHours(), selected.getHours());
  assert.equal(result.value.getMinutes(), 30);
});
