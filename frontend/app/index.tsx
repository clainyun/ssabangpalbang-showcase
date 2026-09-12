import { Redirect } from 'expo-router';

import { useAuthStore } from '@/store/authStore';
import { SplashScreen } from '@/components/SplashScreen';

/** 진입점 — SecureStore 복구 결과에 따라 분기합니다 (§6.1, §6.3). */
export default function Index() {
  const status = useAuthStore((s) => s.status);
  const onboardingRequired = useAuthStore((s) => s.onboardingRequired);

  if (status === 'loading') {
    return <SplashScreen />;
  }

  if (status === 'authenticated') {
    return (
      <Redirect href={onboardingRequired ? '/(auth)/onboarding' : '/(app)/(tabs)/home'} />
    );
  }

  return <Redirect href="/(auth)/login" />;
}
