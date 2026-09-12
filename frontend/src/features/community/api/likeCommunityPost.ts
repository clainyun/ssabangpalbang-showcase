import { authenticatedFetch } from '@/lib/authenticatedFetch';

import { parseCommunityResponse } from './parseCommunityResponse';
import type { CommunityPostLikeResult } from './types';

export async function likeCommunityPost(
  accessToken: string,
  postId: number,
  expectedSessionVersion?: number,
): Promise<CommunityPostLikeResult> {
  const response = await authenticatedFetch(
    `/api/v1/posts/${postId}/like`,
    {
      method: 'PUT',
    },
    {
      expectedSessionVersion,
      fallbackAccessToken: accessToken,
    },
  );

  return parseCommunityResponse<CommunityPostLikeResult>(
    response,
    '게시글에 좋아요를 등록하지 못했습니다.',
  );
}
