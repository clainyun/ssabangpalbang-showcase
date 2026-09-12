import { useInfiniteQuery } from '@tanstack/react-query';

import { useAuthStore } from '@/store/authStore';

import { getCommunityComments } from './getCommunityComments';

const COMMENT_PAGE_SIZE = 20;

export const communityCommentsQueryRoot = (
  sessionVersion: number,
  postId: number,
) => ['community', 'comments', sessionVersion, postId] as const;

export const communityCommentsQueryKey = (
  sessionVersion: number,
  postId: number,
) =>
  [
    ...communityCommentsQueryRoot(sessionVersion, postId),
    { size: COMMENT_PAGE_SIZE },
  ] as const;

export function useCommunityComments(
  postId: number | undefined,
  enabled: boolean,
) {
  const accessToken = useAuthStore((state) => state.accessToken);
  const sessionVersion = useAuthStore((state) => state.sessionVersion);
  const queryPostId = postId ?? 0;

  return useInfiniteQuery({
    queryKey: communityCommentsQueryKey(sessionVersion, queryPostId),
    enabled:
      enabled &&
      accessToken !== null &&
      postId !== undefined,
    initialPageParam: undefined as number | undefined,
    queryFn: ({ pageParam, signal }) => {
      if (accessToken === null) {
        throw new Error('로그인이 필요합니다.');
      }
      if (postId === undefined) {
        throw new Error('게시글 번호가 올바르지 않습니다.');
      }
      return getCommunityComments(
        accessToken,
        postId,
        pageParam,
        COMMENT_PAGE_SIZE,
        signal,
        sessionVersion,
      );
    },
    getNextPageParam: (lastPage) =>
      lastPage.hasNext
        ? (lastPage.nextCursor ?? undefined)
        : undefined,
  });
}
