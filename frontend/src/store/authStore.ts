import { create } from 'zustand';

import { discardFieldTrack } from '@/features/field/fieldTrackRunner';
import { tokenStorage } from '@/lib/tokenStorage';

/**
 * §6.2 — zustand 는 인증·캐릭터 저빈도 상태·지도 카메라·UI 상태만 맡습니다.
 * 서버에서 온 데이터는 여기에 넣지 마십시오. TanStack Query 담당입니다.
 */
type AuthStatus = 'loading' | 'authenticated' | 'unauthenticated';

interface AuthState {
  status: AuthStatus;
  accessToken: string | null;
  sessionVersion: number;
  // 로그인/회원가입 직후 온보딩(§FE-002)을 아직 마치지 않은 세션인지. true면 index/
  // AuthLayout 이 인증됐어도 홈이 아니라 온보딩으로 보냅니다. 이 플래그가 없으면
  // signIn 으로 status 가 authenticated 로 바뀌는 순간 AuthLayout 이 온보딩 이동보다
  // 먼저 홈으로 Redirect 해버려서 첫 소셜/일반 로그인의 온보딩이 통째로 건너뛰어집니다.
  onboardingRequired: boolean;
  restore: () => Promise<void>;
  signIn: (
    accessToken: string,
    refreshToken: string,
    onboardingCompleted: boolean,
  ) => Promise<void>;
  completeOnboarding: () => void;
  replaceTokens: (
    accessToken: string,
    refreshToken: string,
    expectedSessionVersion: number,
  ) => Promise<boolean>;
  expireSession: (expectedSessionVersion: number) => Promise<boolean>;
  signOut: (expectedSessionVersion?: number) => Promise<boolean>;
}

// SecureStore 조회는 보통 몇 ms 안에 끝나서, 이 시간만 기다리면 스플래시
// 애니메이션(SplashScreen.tsx)이 화면에 페인트되기도 전에 status 가 바뀌어
// 사라져버립니다. 최소 노출 시간을 같이 기다려서 애니메이션이 실제로
// 보이도록 합니다.
const MIN_SPLASH_DURATION_MS = 2000;
const delay = (ms: number) => new Promise<void>((resolve) => setTimeout(resolve, ms));
let tokenMutationQueue: Promise<void> = Promise.resolve();

function queueTokenMutation<T>(operation: () => Promise<T>): Promise<T> {
  const result = tokenMutationQueue.then(operation, operation);
  tokenMutationQueue = result.then(
    () => undefined,
    () => undefined,
  );
  return result;
}

export const useAuthStore = create<AuthState>((set, get) => ({
  status: 'loading',
  accessToken: null,
  sessionVersion: 0,
  onboardingRequired: false,

  restore: () =>
    queueTokenMutation(async () => {
      try {
        const [token] = await Promise.all([
          tokenStorage.getAccessToken(),
          delay(MIN_SPLASH_DURATION_MS),
        ]);
        // 저장된 토큰으로 복원된 세션(재방문 사용자)은 온보딩을 강제하지 않습니다.
        // (첫 로그인만 강제하는 범위 — 온보딩 도중 앱 재시작 복원은 이번 범위 밖)
        set((state) => ({
          accessToken: token,
          status: token ? 'authenticated' : 'unauthenticated',
          onboardingRequired: false,
          sessionVersion: state.sessionVersion + 1,
        }));
      } catch {
        set((state) => ({
          accessToken: null,
          status: 'unauthenticated',
          onboardingRequired: false,
          sessionVersion: state.sessionVersion + 1,
        }));
      }
    }),

  signIn: (accessToken, refreshToken, onboardingCompleted) =>
    queueTokenMutation(async () => {
      await tokenStorage.save(accessToken, refreshToken);
      // status 와 onboardingRequired 를 같은 set() 으로 원자적으로 바꿔, authenticated
      // 로 바뀌는 렌더에서 플래그가 이미 확정돼 있게 합니다 (레이스 방지).
      set((state) => ({
        accessToken,
        status: 'authenticated',
        onboardingRequired: !onboardingCompleted,
        sessionVersion: state.sessionVersion + 1,
      }));
    }),

  completeOnboarding: () => set({ onboardingRequired: false }),

  replaceTokens: (accessToken, refreshToken, expectedSessionVersion) =>
    queueTokenMutation(async () => {
      if (get().sessionVersion !== expectedSessionVersion) {
        return false;
      }

      await tokenStorage.save(accessToken, refreshToken);
      if (get().sessionVersion !== expectedSessionVersion) {
        return false;
      }

      set({ accessToken, status: 'authenticated' });
      return true;
    }),

  expireSession: (expectedSessionVersion) =>
    get().signOut(expectedSessionVersion),

  signOut: (expectedSessionVersion) =>
    queueTokenMutation(async () => {
      if (
        expectedSessionVersion !== undefined &&
        get().sessionVersion !== expectedSessionVersion
      ) {
        return false;
      }

      try {
        await tokenStorage.clear();
      } catch (error) {
        console.warn('[Auth] Failed to clear persisted authentication tokens.', {
          errorType: error instanceof Error ? error.name : 'unknown',
        });
      } finally {
        if (
          expectedSessionVersion === undefined ||
          get().sessionVersion === expectedSessionVersion
        ) {
          // 세션이 끝나면 임장 궤적 수집도 함께 끊고 메모리에 남은 좌표를 버립니다.
          // 로그아웃 뒤에도 위치를 계속 모으거나, 다음 사용자에게 이전 사용자의
          // 경로가 남아 보이면 안 됩니다.
          discardFieldTrack();
          set((state) => ({
            accessToken: null,
            status: 'unauthenticated',
            onboardingRequired: false,
            sessionVersion: state.sessionVersion + 1,
          }));
        }
      }
      return true;
    }),
}));
