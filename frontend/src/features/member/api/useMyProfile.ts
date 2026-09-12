import { useQuery } from '@tanstack/react-query';

import { useAuthStore } from '@/store/authStore';

import { getMyProfile } from './getMyProfile';

/**
 * 마이페이지에서 캐릭터를 바꾼 뒤 이 키를 무효화하면 현장 지도의 캐릭터가 즉시
 * 바뀝니다 (FE-014 완료 기준 — "마이페이지에서 바꾼 최신 캐릭터를 즉시 반영").
 *
 *   queryClient.invalidateQueries({ queryKey: myProfileQueryKey })
 */
export const myProfileQueryKey = ['member', 'me'] as const;

export function useMyProfile() {
  const accessToken = useAuthStore((state) => state.accessToken);
  const sessionVersion = useAuthStore((state) => state.sessionVersion);

  return useQuery({
    queryKey: myProfileQueryKey,
    enabled: accessToken !== null,
    queryFn: ({ signal }) => {
      if (accessToken === null) {
        throw new Error('로그인이 필요합니다.');
      }
      return getMyProfile(accessToken, sessionVersion, signal);
    },
  });
}
