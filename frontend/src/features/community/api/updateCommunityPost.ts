import { authenticatedFetch } from '@/lib/authenticatedFetch';

import { parseCommunityResponse } from './parseCommunityResponse';
import type {
  CommunityPostWriteResult,
  UpdateCommunityPostInput,
} from './types';

export async function updateCommunityPost(
  accessToken: string,
  postId: number,
  input: UpdateCommunityPostInput,
  expectedSessionVersion?: number,
): Promise<CommunityPostWriteResult> {
  const response = await authenticatedFetch(
    `/api/v1/posts/${postId}`,
    {
      method: 'PATCH',
      headers: { 'Content-Type': 'application/json' },
      body: JSON.stringify(input),
    },
    {
      expectedSessionVersion,
      fallbackAccessToken: accessToken,
    },
  );

  return parseCommunityResponse<CommunityPostWriteResult>(
    response,
    '게시글을 수정하지 못했습니다.',
  );
}
