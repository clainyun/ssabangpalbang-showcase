import { Redirect, Stack, usePathname } from 'expo-router';
import { StyleSheet, View } from 'react-native';

import { SplashScreen } from '@/components/SplashScreen';
import { ActiveFieldVisitReturnButton } from '@/features/field/ActiveFieldVisitReturnButton';
import { parsePendingReportId } from '@/features/navigation/pendingAppLinkStore';
import { useFcmTokenRegistration } from '@/features/notification/useFcmTokenRegistration';
import { usePushNotificationSync } from '@/features/notification/usePushNotificationSync';
import { useAuthStore } from '@/store/authStore';

export default function AppLayout() {
  const status = useAuthStore((s) => s.status);
  const sessionVersion = useAuthStore((s) => s.sessionVersion);
  const pathname = usePathname();

  useFcmTokenRegistration(status === 'authenticated', sessionVersion);
  usePushNotificationSync(status === 'authenticated', sessionVersion);

  if (status === 'loading') {
    return <SplashScreen />;
  }

  if (status === 'unauthenticated') {
    const returnToReportId = parsePendingReportId(pathname);
    const isInvalidReportAppLink =
      /^(?:\/\(app\))?\/open\/report(?:\/|$)/.test(pathname) && !returnToReportId;

    // 잘못된 App Link는 로그인으로 보내 정상 진입처럼 보이게 하지 않고,
    // 브리지 화면이 오류 상태를 직접 설명하도록 둡니다.
    if (isInvalidReportAppLink) {
      return renderAppStack();
    }

    return (
      <Redirect
        href={
          returnToReportId
            ? {
                pathname: '/(auth)/login',
                params: { returnToReportId },
              }
            : '/(auth)/login'
        }
      />
    );
  }

  return renderAppStack();
}

function renderAppStack() {
  return (
    <View style={styles.container}>
      <Stack>
        <Stack.Screen name="(tabs)" options={{ headerShown: false }} />
        <Stack.Screen name="profile-edit" options={{ headerShown: false }} />
        <Stack.Screen name="settings" options={{ headerShown: false }} />
        <Stack.Screen name="member/[memberId]" options={{ headerShown: false }} />
        <Stack.Screen name="message/[memberId]" options={{ headerShown: false }} />
        <Stack.Screen name="apartment/[id]" options={{ title: '단지 상세' }} />
        <Stack.Screen name="field/[sessionId]" options={{ headerShown: false }} />
        <Stack.Screen name="field-summary/[sessionId]" options={{ headerShown: false }} />
        <Stack.Screen name="field-visit-waiting/[studyId]" options={{ headerShown: false }} />
        <Stack.Screen name="report-generating/[reportId]" options={{ headerShown: false }} />
        <Stack.Screen name="report/[reportId]" options={{ headerShown: false }} />
        <Stack.Screen name="open/report/[reportId]" options={{ headerShown: false }} />
        <Stack.Screen name="post/create" options={{ headerShown: false }} />
        <Stack.Screen name="post/[id]" options={{ headerShown: false }} />
        <Stack.Screen name="study/create" options={{ headerShown: false }} />
        <Stack.Screen name="study/[id]" options={{ headerShown: false }} />
        <Stack.Screen name="study/[id]/overview" options={{ headerShown: false }} />
        <Stack.Screen name="study/[id]/records" options={{ headerShown: false }} />
        <Stack.Screen name="study/[id]/manage" options={{ headerShown: false }} />
        <Stack.Screen name="study/[id]/settings" options={{ headerShown: false }} />
        <Stack.Screen name="chat/[roomId]" options={{ headerShown: false }} />
        <Stack.Screen name="chatbot" options={{ title: 'AI 상담' }} />
      </Stack>
      <ActiveFieldVisitReturnButton />
    </View>
  );
}

const styles = StyleSheet.create({
  container: { flex: 1 },
});
