import {
  IBMPlexSansKR_400Regular,
  IBMPlexSansKR_600SemiBold,
  IBMPlexSansKR_700Bold,
} from '@expo-google-fonts/ibm-plex-sans-kr';
import { NotoSansKR_900Black, useFonts } from '@expo-google-fonts/noto-sans-kr';
import { QueryClientProvider } from '@tanstack/react-query';
import * as SecureStore from 'expo-secure-store';
import { Stack } from 'expo-router';
import { StatusBar } from 'expo-status-bar';
import { useCallback, useEffect, useState } from 'react';
import { GestureHandlerRootView } from 'react-native-gesture-handler';
import { SafeAreaProvider } from 'react-native-safe-area-context';
import * as SplashScreen from 'expo-splash-screen';

import { AppDialogProvider } from '@/components/AppDialog';
import { PermissionNoticeModal } from '@/components/PermissionNoticeModal';
import { NotificationResponseHandler } from '@/features/notification/NotificationResponseHandler';
import { initMapbox } from '@/lib/mapbox';
import { queryClient } from '@/lib/queryClient';
import { configureNotificationPresentation } from '@/features/notification/notificationPresentation';
import { useAuthStore } from '@/store/authStore';

SplashScreen.preventAutoHideAsync();

const PERMISSION_NOTICE_SEEN_KEY = 'release.permission-notice-seen';

export default function RootLayout() {
  const restore = useAuthStore((s) => s.restore);
  const [isPermissionNoticeChecked, setIsPermissionNoticeChecked] = useState(false);
  const [isPermissionNoticeVisible, setIsPermissionNoticeVisible] = useState(false);
  // D-Day 등에서 쓰는 디스플레이 폰트. 로드 전엔 기본 폰트로 렌더되다가 로드되면 교체됨
  // (스플래시 로직 안 건드리려고 렌더 게이팅은 하지 않음 — 잠깐의 FOUT 허용).
  useFonts({
    NotoSansKR_900Black,
    IBMPlexSansKR_400Regular,
    IBMPlexSansKR_600SemiBold,
    IBMPlexSansKR_700Bold,
  });

  useEffect(() => {
    configureNotificationPresentation();
    initMapbox();
    void restore();
  }, [restore]);

  // 네이티브 스플래시는 JS 가 첫 프레임을 그리는 즉시 걷어냅니다.
  // status(인증 복구) 완료를 기다렸다가 hideAsync() 를 호출하면, New
  // Architecture(Fabric) 에서 android:id/content 뷰의 onPreDraw 가 그 사이에
  // 다시 발생하지 않아 hideAsync() 는 resolve 되는데도 네이티브 스플래시가
  // 화면에 그대로 남아버리는 문제가 있었습니다 (§13 스파이크). 그래서 로딩
  // 화면은 app/index.tsx 의 SplashScreen 컴포넌트가 순수 JS 로 담당하고,
  // 네이티브 스플래시는 마운트 직후 곧바로 걷습니다.
  useEffect(() => {
    void SplashScreen.hideAsync();
  }, []);

  useEffect(() => {
    let active = true;

    SecureStore.getItemAsync(PERMISSION_NOTICE_SEEN_KEY)
      .then((seen) => {
        if (active) setIsPermissionNoticeVisible(seen !== 'true');
      })
      .catch(() => {
        if (active) setIsPermissionNoticeVisible(true);
      })
      .finally(() => {
        if (active) setIsPermissionNoticeChecked(true);
      });

    return () => {
      active = false;
    };
  }, []);

  const handlePermissionNoticeConfirm = useCallback(async () => {
    try {
      await SecureStore.setItemAsync(PERMISSION_NOTICE_SEEN_KEY, 'true');
    } finally {
      setIsPermissionNoticeVisible(false);
    }
  }, []);

  return (
    <GestureHandlerRootView style={{ flex: 1 }}>
      <SafeAreaProvider>
        <QueryClientProvider client={queryClient}>
          <AppDialogProvider>
            {isPermissionNoticeChecked && isPermissionNoticeVisible ? (
              <PermissionNoticeModal onConfirm={handlePermissionNoticeConfirm} visible />
            ) : null}
            {isPermissionNoticeChecked && !isPermissionNoticeVisible ? (
              // 인증 게이팅은 각 그룹의 _layout 에서 Redirect 로 처리합니다 (§6.1).
              <>
                <NotificationResponseHandler />
                <Stack screenOptions={{ headerShown: false }}>
                  <Stack.Screen name="(auth)" />
                  <Stack.Screen name="(app)" />
                </Stack>
              </>
            ) : null}
            <StatusBar style="dark" />
          </AppDialogProvider>
        </QueryClientProvider>
      </SafeAreaProvider>
    </GestureHandlerRootView>
  );
}
