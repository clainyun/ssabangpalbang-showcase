import { useQuery } from '@tanstack/react-query';

import { useAuthStore } from '@/store/authStore';
import { getHomeSummary, type HomeLocation } from '@/features/home/api/getHomeSummary';

export const homeSummaryQueryRoot = ['home', 'summary'] as const;

export const homeSummaryQueryKey = (location: HomeLocation | null, sessionVersion: number) =>
  [...homeSummaryQueryRoot, sessionVersion, location ?? 'no-location'] as const;

/**
 * location=null이면 위치 권한이 없는 상태로 간주해 위경도 없이 조회합니다
 * (docs/API.md — 위치 권한이 없으면 latitude/longitude 둘 다 생략).
 */
export function useHomeSummary(location: HomeLocation | null) {
  const accessToken = useAuthStore((state) => state.accessToken);
  const sessionVersion = useAuthStore((state) => state.sessionVersion);

  return useQuery({
    queryKey: homeSummaryQueryKey(location, sessionVersion),
    enabled: accessToken !== null,
    queryFn: () => getHomeSummary(location),
    // GPS는 화면이 뜬 뒤에 늦게 잡힙니다. 그러면 queryKey가 'no-location' → 좌표로 바뀌며
    // 새 캐시 항목이 되어 isPending이 다시 true가 되고, 이미 그려 둔 홈 D-day 영역이
    // 스켈레톤으로 되돌아갑니다. 이전 키의 데이터를 그대로 들고 있어 화면이 뒤로 감기지
    // 않게 합니다(공개 프로필 목록 member/[memberId].tsx의 keepPreviousData와 같은 의도).
    // 다만 세션이 바뀌면(로그아웃 후 다른 계정 로그인) 이전 계정의 홈 정보가 잠깐이라도
    // 비쳐서는 안 되므로, 같은 sessionVersion의 이전 데이터만 유지합니다.
    placeholderData: (previousData, previousQuery) =>
      previousQuery?.queryKey[2] === sessionVersion ? previousData : undefined,
  });
}
