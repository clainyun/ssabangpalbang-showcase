import { authenticatedFetch } from '@/lib/authenticatedFetch';

import { parseCommunityResponse } from './parseCommunityResponse';
import type { CommunityComment } from './types';

export async function updateCommunityComment(
  accessToken: string,
  commentId: number,
  content: string,
  expectedSessionVersion?: number,
): Promise<CommunityComment> {
  const response = await authenticatedFetch(
    `/api/v1/comments/${commentId}`,
    {
      method: 'PATCH',
      headers: {
        'Content-Type': 'application/json',
      },
      body: JSON.stringify({ content }),
    },
    {
      expectedSessionVersion,
      fallbackAccessToken: accessToken,
    },
  );

  return parseCommunityResponse<CommunityComment>(response, '댓글을 수정하지 못했습니다.');
}
