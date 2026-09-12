import { create } from 'zustand';

import type { SocialProvider } from '@/features/auth/oauth';

/** social-login의 signupRequired:true 응답을 social-signup 화면으로 넘기는 화면 간 임시
 * 상태입니다. 10분짜리 1회성 토큰이라 authStore와 달리 영속시키지 않고, 화면을 벗어나면
 * (완료·뒤로가기 어느 쪽이든) clear()로 폐기합니다. */
interface SocialSignupState {
  socialSignupToken: string | null;
  provider: SocialProvider | null;
  email: string | null;
  emailRequired: boolean;
  set: (data: {
    socialSignupToken: string;
    provider: SocialProvider;
    email: string | null;
    emailRequired: boolean;
  }) => void;
  clear: () => void;
}

export const useSocialSignupStore = create<SocialSignupState>((set) => ({
  socialSignupToken: null,
  provider: null,
  email: null,
  emailRequired: false,

  set: ({ socialSignupToken, provider, email, emailRequired }) =>
    set({ socialSignupToken, provider, email, emailRequired }),

  clear: () =>
    set({ socialSignupToken: null, provider: null, email: null, emailRequired: false }),
}));
