import { authenticatedFetch } from '@/lib/authenticatedFetch';

import { parseCommunityResponse } from './parseCommunityResponse';
import type { CommunityCommentList } from './types';

export async function getCommunityComments(
  accessToken: string,
  postId: number,
  cursor: number | undefined,
  size: number,
  signal?: AbortSignal,
  expectedSessionVersion?: number,
): Promise<CommunityCommentList> {
  const params = new URLSearchParams({
    size: String(size),
  });
  if (cursor !== undefined) {
    params.set('cursor', String(cursor));
  }

  const response = await authenticatedFetch(
    `/api/v1/posts/${postId}/comments?${params.toString()}`,
    {
      signal,
    },
    {
      expectedSessionVersion,
      fallbackAccessToken: accessToken,
    },
  );
  return parseCommunityResponse<CommunityCommentList>(
    response,
    '댓글을 불러오지 못했습니다.',
  );
}
