import { authenticatedFetch } from '@/lib/authenticatedFetch';

import { parseCommunityResponse } from './parseCommunityResponse';
import type { CommunityPostUnlikeResult } from './types';

export async function unlikeCommunityPost(
  accessToken: string,
  postId: number,
  expectedSessionVersion?: number,
): Promise<CommunityPostUnlikeResult> {
  const response = await authenticatedFetch(
    `/api/v1/posts/${postId}/like`,
    {
      method: 'DELETE',
    },
    {
      expectedSessionVersion,
      fallbackAccessToken: accessToken,
    },
  );

  return parseCommunityResponse<CommunityPostUnlikeResult>(
    response,
    '게시글 좋아요를 해제하지 못했습니다.',
  );
}
