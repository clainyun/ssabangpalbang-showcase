import type { StudyNotice, StudyNoticeListResult } from './api/types';

/** 재시도나 경계 겹침으로 같은 공지가 들어와도 화면에는 한 번만 누적합니다. */
export function flattenUniqueNotices(
  pages: readonly StudyNoticeListResult[],
): StudyNotice[] {
  const seen = new Set<number>();
  const notices: StudyNotice[] = [];

  for (const page of pages) {
    for (const notice of page.content) {
      if (seen.has(notice.noticeId)) continue;
      seen.add(notice.noticeId);
      notices.push(notice);
    }
  }

  return notices;
}

export function formatLoadedNoticeCount(loadedCount: number, hasNextPage: boolean): string {
  if (loadedCount === 0) return '';
  return `${loadedCount}${hasNextPage ? '+' : ''}`;
}

export function getNextNoticeCursor(
  lastPage: StudyNoticeListResult,
  requestedCursors: readonly (number | undefined)[],
): number | undefined {
  if (!lastPage.hasNext || lastPage.nextCursor === null) return undefined;
  return requestedCursors.includes(lastPage.nextCursor) ? undefined : lastPage.nextCursor;
}
