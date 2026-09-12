import { authenticatedFetch } from '@/lib/authenticatedFetch';

/**
 * GET /api/v1/members/me — MemberProfileResponse 중 프론트에서 쓰는 필드만 옮겼습니다.
 * 서버 원본: backend/.../member/dto/response/MemberProfileResponse.java
 */
export interface MyProfile {
  memberId: number;
  email: string;
  nickname: string;
  profileImageUrl: string | null;
  /** 'PALBANG' | 'PALBANG_RABBIT' | 'PALBANG_DOG' (MemberService.java:46) */
  selectedCharacterId: string;
  onboardingCompleted: boolean;
}

interface ApiEnvelope<T> {
  success: boolean;
  code: string;
  message: string;
  data: T | null;
}

export async function getMyProfile(
  accessToken: string,
  expectedSessionVersion?: number,
  signal?: AbortSignal,
): Promise<MyProfile> {
  const response = await authenticatedFetch(
    '/api/v1/members/me',
    {
      signal,
    },
    {
      expectedSessionVersion,
      fallbackAccessToken: accessToken,
    },
  );

  const body = (await response.json().catch(() => null)) as ApiEnvelope<MyProfile> | null;

  if (!response.ok || body === null || !body.success || body.data === null) {
    throw new Error(body?.message || '내 정보를 불러오지 못했습니다.');
  }

  return body.data;
}
