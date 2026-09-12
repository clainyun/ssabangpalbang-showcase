import { authenticatedFetch } from '@/lib/authenticatedFetch';

import { parseCommunityResponse } from './parseCommunityResponse';
import type { CommunityCommentCreateResult } from './types';

export async function createCommunityComment(
  accessToken: string,
  postId: number,
  content: string,
  expectedSessionVersion?: number,
): Promise<CommunityCommentCreateResult> {
  const response = await authenticatedFetch(
    `/api/v1/posts/${postId}/comments`,
    {
      method: 'POST',
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

  return parseCommunityResponse<CommunityCommentCreateResult>(
    response,
    '댓글을 등록하지 못했습니다.',
  );
}
