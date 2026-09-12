import { useQuery } from '@tanstack/react-query';

import { useAuthStore } from '@/store/authStore';

import { getCommunityPost } from './getCommunityPost';

export const communityPostQueryKey = (
  sessionVersion: number,
  postId: number | undefined,
) => ['community', 'post', sessionVersion, postId ?? null] as const;

export function useCommunityPost(postId: number | undefined) {
  const accessToken = useAuthStore((state) => state.accessToken);
  const sessionVersion = useAuthStore((state) => state.sessionVersion);

  return useQuery({
    queryKey: communityPostQueryKey(sessionVersion, postId),
    enabled: accessToken !== null && postId !== undefined,
    queryFn: ({ signal }) => {
      if (accessToken === null) {
        throw new Error('로그인이 필요합니다.');
      }
      if (postId === undefined) {
        throw new Error('게시글 번호가 올바르지 않습니다.');
      }
      return getCommunityPost(
        accessToken,
        postId,
        signal,
        sessionVersion,
      );
    },
  });
}
