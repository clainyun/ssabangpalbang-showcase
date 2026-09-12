import { authenticatedFetch } from '@/lib/authenticatedFetch';

import { parseCommunityResponse } from './parseCommunityResponse';
import type {
  CommunityPostWriteResult,
  CreateCommunityPostInput,
} from './types';

export async function createCommunityPost(
  accessToken: string,
  input: CreateCommunityPostInput,
  expectedSessionVersion?: number,
): Promise<CommunityPostWriteResult> {
  const response = await authenticatedFetch(
    '/api/v1/posts',
    {
      method: 'POST',
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
    '게시글을 등록하지 못했습니다.',
  );
}
