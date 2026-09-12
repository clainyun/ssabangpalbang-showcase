import { authenticatedFetch } from '@/lib/authenticatedFetch';

import type { CommunityCommentDeleteResult } from './commentDeleteTypes';
import { parseCommunityResponse } from './parseCommunityResponse';

export async function deleteCommunityComment(
  accessToken: string,
  commentId: number,
  expectedSessionVersion?: number,
): Promise<CommunityCommentDeleteResult> {
  const response = await authenticatedFetch(
    `/api/v1/comments/${commentId}`,
    {
      method: 'DELETE',
    },
    {
      expectedSessionVersion,
      fallbackAccessToken: accessToken,
    },
  );

  return parseCommunityResponse<CommunityCommentDeleteResult>(
    response,
    '댓글을 삭제하지 못했습니다.',
  );
}
