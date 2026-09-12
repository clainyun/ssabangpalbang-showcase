import { useMyProfile } from '@/features/member/api/useMyProfile';

import { toCharacterId, type CharacterId } from './spriteConfig';

/**
 * 온보딩·마이페이지에서 고른 캐릭터. 서버(GET /members/me)가 유일한 출처입니다.
 *
 * 아직 응답을 못 받았거나 실패했으면 기본 캐릭터로 떨어집니다. 지도에 캐릭터가
 * 아예 안 뜨는 것보다 기본 캐릭터라도 뜨는 게 낫습니다.
 */
export function useSelectedCharacterId(): CharacterId {
  const { data } = useMyProfile();

  return toCharacterId(data?.selectedCharacterId);
}
