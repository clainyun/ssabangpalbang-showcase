import assert from 'node:assert/strict';
import { test } from 'node:test';

import {
  flattenUniqueNotices,
  formatLoadedNoticeCount,
  getNextNoticeCursor,
} from './studyNoticePagination.ts';

const notice = (noticeId) => ({ noticeId, content: `notice-${noticeId}` });
const page = (content) => ({ content });

test('공지 페이지를 순서대로 누적하고 겹친 항목은 제거한다', () => {
  assert.deepEqual(
    flattenUniqueNotices([page([notice(3), notice(2)]), page([notice(2), notice(1)])]).map(
      ({ noticeId }) => noticeId,
    ),
    [3, 2, 1],
  );
});

test('다음 페이지가 있으면 로드한 공지 개수에 +를 표시한다', () => {
  assert.equal(formatLoadedNoticeCount(2, true), '2+');
  assert.equal(formatLoadedNoticeCount(2, false), '2');
  assert.equal(formatLoadedNoticeCount(0, true), '');
});

test('서버가 이미 요청한 cursor를 반복하면 페이지네이션을 종료한다', () => {
  const lastPage = { hasNext: true, nextCursor: 20 };

  assert.equal(getNextNoticeCursor(lastPage, [undefined, 20]), undefined);
  assert.equal(getNextNoticeCursor(lastPage, [undefined, 30]), 20);
});
