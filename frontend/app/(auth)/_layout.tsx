import { Redirect, Stack, useSegments } from 'expo-router';

import { useAuthStore } from '@/store/authStore';

export default function AuthLayout() {
  const status = useAuthStore((s) => s.status);
  const onboardingRequired = useAuthStore((s) => s.onboardingRequired);
  const segments = useSegments();
  // 회원가입 직후 온보딩 미완료 상태(§7.1)는 인증됐어도 이 그룹에 남아있어야 합니다.
  const isOnboarding = segments[segments.length - 1] === 'onboarding';

  if (status === 'authenticated') {
    // 온보딩이 필요한 세션(첫 소셜/일반 로그인)은 온보딩 화면에 붙잡아 둡니다.
    // onboarding 세그먼트가 아니면 온보딩으로 보내, 로그인 직후 홈으로 새는 경로를 막습니다.
    if (onboardingRequired) {
      if (!isOnboarding) {
        return <Redirect href="/(auth)/onboarding" />;
      }
    } else {
      return <Redirect href="/(app)/(tabs)/home" />;
    }
  }

  return <Stack screenOptions={{ headerShown: false }} />;
}
