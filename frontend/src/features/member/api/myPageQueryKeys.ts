// stale 게이트 헬퍼는 여러 도메인 화면이 공유하도록 @/lib 로 승격했습니다.
// 기존 import 경로 호환을 위해 여기서 re-export 합니다.
export {
  FOCUS_REFRESH_INTERVAL_MS as MY_PAGE_FOCUS_REFRESH_INTERVAL_MS,
  refetchOnFocusIfStale,
} from '@/lib/refetchOnFocusIfStale';

export const myPageQueryKeys = {
  profile: (sessionVersion: number) => ['member', 'me', sessionVersion] as const,
  reviewSummary: (memberId: number, sessionVersion: number) =>
    ['member', 'reviews', memberId, sessionVersion, { size: 1 }] as const,
  studiesRoot: ['member', 'me', 'studies'] as const,
  studies: (sessionVersion: number, source: 'api' | 'mock') =>
    ['member', 'me', 'studies', sessionVersion, { status: 'ALL', size: 20, source }] as const,
  reports: (sessionVersion: number, source: 'api' | 'mock') =>
    ['member', 'me', 'reports', sessionVersion, { size: 20, source }] as const,
  followings: (sessionVersion: number, source: 'api' | 'mock') =>
    ['member', 'me', 'followings', sessionVersion, { size: 20, source }] as const,
  visitCalendarRoot: ['member', 'me', 'visit-calendar'] as const,
  visitCalendar: (sessionVersion: number, year: number, month: number) =>
    ['member', 'me', 'visit-calendar', sessionVersion, year, month] as const,
};
