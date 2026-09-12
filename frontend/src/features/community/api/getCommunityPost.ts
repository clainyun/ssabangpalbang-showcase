import { authenticatedFetch } from '@/lib/authenticatedFetch';

import { parseCommunityResponse } from './parseCommunityResponse';
import type { CommunityPostDetail } from './types';

export async function getCommunityPost(
  accessToken: string,
  postId: number,
  signal?: AbortSignal,
  expectedSessionVersion?: number,
): Promise<CommunityPostDetail> {
  const response = await authenticatedFetch(
    `/api/v1/posts/${postId}`,
    {
      signal,
    },
    {
      expectedSessionVersion,
      fallbackAccessToken: accessToken,
    },
  );
  return parseCommunityResponse<CommunityPostDetail>(
    response,
    '게시글을 불러오지 못했습니다.',
  );
}
