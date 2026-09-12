import { useInfiniteQuery } from '@tanstack/react-query';

import { useAuthStore } from '@/store/authStore';

import { getCommunityPosts } from './getCommunityPosts';
import type { CommunityBoardType, CommunityPostSort } from './types';

export const communityPostsQueryRoot = (sessionVersion: number) =>
  ['community', 'posts', sessionVersion] as const;

export const communityPostsQueryKey = (
  sessionVersion: number,
  boardType: CommunityBoardType | 'ALL',
  sort: CommunityPostSort,
) => [...communityPostsQueryRoot(sessionVersion), boardType, sort] as const;

/**
 * 커뮤니티 목록 무한스크롤 조회. boardType을 생략(전체)하고 서버 발급 커서로
 * 다음 페이지를 이어 붙인다. 쿼리 키는 기존 root를 유지해 게시글/댓글 mutation의
 * root 기반 invalidation이 그대로 동작한다.
 */
export function useCommunityPostsInfinite(sort: CommunityPostSort) {
  const accessToken = useAuthStore((state) => state.accessToken);
  const sessionVersion = useAuthStore((state) => state.sessionVersion);

  return useInfiniteQuery({
    queryKey: communityPostsQueryKey(sessionVersion, 'ALL', sort),
    enabled: accessToken !== null,
    initialPageParam: null as string | null,
    queryFn: ({ signal, pageParam }) => {
      if (accessToken === null) {
        throw new Error('로그인이 필요합니다.');
      }
      return getCommunityPosts(
        accessToken,
        null,
        sort,
        signal,
        sessionVersion,
        pageParam,
      );
    },
    getNextPageParam: (lastPage) =>
      lastPage.pageInfo.hasNext ? lastPage.pageInfo.nextCursor : undefined,
  });
}
