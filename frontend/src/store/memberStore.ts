import { create } from 'zustand';

/**
 * §6.2 — 온보딩 완료 응답에서 받은 값 중, 다른 화면(홈의 캐릭터 표시 등)에서
 * 동기적으로 필요할 수 있는 저빈도 필드만 둡니다. 나머지 프로필 정보는
 * 필요해지면 TanStack Query로 GET /api/v1/members/me를 통해 가져옵니다.
 */
export type CharacterId = 'PALBANG' | 'PALBANG_RABBIT' | 'PALBANG_DOG';

interface MemberProfile {
  selectedCharacterId: CharacterId;
  ageGroupPublicAgreed: boolean;
}

interface MemberState extends Partial<MemberProfile> {
  setProfile: (profile: MemberProfile) => void;
}

export const useMemberStore = create<MemberState>((set) => ({
  selectedCharacterId: undefined,
  ageGroupPublicAgreed: undefined,
  setProfile: (profile) => set(profile),
}));
