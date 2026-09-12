import { Redirect } from 'expo-router';

import { useAuthStore } from '@/store/authStore';
import { SplashScreen } from '@/components/SplashScreen';

/**
 * 소셜 로그인 리다이렉트 주소(ssabangpalbang://oauth, oauth.ts의 APP_REDIRECT_URI)를
 * 위한 방어 라우트입니다.
 *
 * 정상 성공 플로우에서는 openAuthSessionAsync 가 이 리다이렉트를 가로채 코드로 되돌려
 * 주므로 이 화면은 마운트되지 않습니다. 하지만 인증 창을 뒤로가기로 닫는 도중 등에
 * 이 딥링크가 앱 라우터로 새면, 매칭 라우트가 없어 +not-found("요청한 화면이 없습니다")
 * 로 떨어집니다. 이 라우트가 그 경우를 받아 로그인/홈으로 안전하게 돌려보냅니다.
 */
export default function OAuthRedirectScreen() {
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
