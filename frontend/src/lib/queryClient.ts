import { QueryClient } from '@tanstack/react-query';

/**
 * §6.2 — 서버 상태는 전부 TanStack Query 가 맡습니다.
 * 401 재발급·로그아웃 처리는 여기 전역 에러 핸들러에 붙입니다 (§6.3).
 */
export const queryClient = new QueryClient({
  defaultOptions: {
    queries: {
      staleTime: 30_000,
      retry: 1,
      // 모바일에서 포커스 재조회는 과합니다.
      refetchOnWindowFocus: false,
    },
  },
});
