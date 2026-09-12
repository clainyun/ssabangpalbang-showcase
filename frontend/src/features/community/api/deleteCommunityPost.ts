import { authenticatedFetch } from '@/lib/authenticatedFetch';

import { parseCommunityResponse } from './parseCommunityResponse';
import type { CommunityPostDeleteResult } from './types';

export async function deleteCommunityPost(
  accessToken: string,
  postId: number,
  expectedSessionVersion?: number,
): Promise<CommunityPostDeleteResult> {
  const response = await authenticatedFetch(
    `/api/v1/posts/${postId}`,
    {
      method: 'DELETE',
    },
    {
      expectedSessionVersion,
      fallbackAccessToken: accessToken,
    },
  );

  return parseCommunityResponse<CommunityPostDeleteResult>(
    response,
    '게시글을 삭제하지 못했습니다.',
  );
}
