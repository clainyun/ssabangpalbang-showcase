import assert from 'node:assert/strict';
import test from 'node:test';

import { createReportShareUrl } from './reportShareLink.ts';

test('리포트 ID를 운영 HTTPS 앱링크로 변환한다', () => {
  assert.equal(createReportShareUrl(42), 'https://portfolio.example.com/report/42');
});

test('안전한 양의 정수가 아닌 리포트 ID를 거부한다', () => {
  for (const reportId of [0, -1, 1.5, Number.NaN, Number.MAX_SAFE_INTEGER + 1]) {
    assert.throws(() => createReportShareUrl(reportId), RangeError);
  }
});
