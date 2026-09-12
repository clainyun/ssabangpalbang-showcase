import { authenticatedFetch } from '@/lib/authenticatedFetch';

import { parseCommunityResponse } from './parseCommunityResponse';
import type {
  CommunityBoardType,
  CommunityPostList,
  CommunityPostSort,
} from './types';

export async function getCommunityPosts(
  accessToken: string,
  // null이면 boardType을 생략해 전체(정보+자유) 목록을 조회한다.
  boardType: CommunityBoardType | null,
  sort: CommunityPostSort,
  signal?: AbortSignal,
  expectedSessionVersion?: number,
  // 서버 발급 불투명 커서. 목록 무한스크롤의 다음 페이지 조회에 사용한다.
  cursor?: string | null,
): Promise<CommunityPostList> {
  const params = new URLSearchParams({
    sort,
    size: sort === 'HOT' ? '10' : '20',
  });
  if (boardType !== null) {
    params.set('boardType', boardType);
  }
  if (cursor) {
    params.set('cursor', cursor);
  }
  const response = await authenticatedFetch(
    `/api/v1/posts?${params.toString()}`,
    {
      signal,
    },
    {
      expectedSessionVersion,
      fallbackAccessToken: accessToken,
    },
  );
  return parseCommunityResponse<CommunityPostList>(
    response,
    '게시글을 불러오지 못했습니다.',
  );
}
