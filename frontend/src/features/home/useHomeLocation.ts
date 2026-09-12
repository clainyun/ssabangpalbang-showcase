import * as Location from 'expo-location';
import { useEffect, useState } from 'react';

import type { HomeLocation } from '@/features/home/api/getHomeSummary';

/**
 * 지도 탭(index.tsx)과 같은 권한 확인 패턴(getForegroundPermissionsAsync +
 * hasServicesEnabledAsync)을 재사용합니다. 다만 지도 탭과 달리 위치가 필수가 아니라서
 * (docs/API.md — 없으면 서버가 다음 임장 위치·서울시청 기본 위치로 대체) 거부 시
 * 안내 화면으로 막지 않고 그냥 null을 반환합니다.
 */
export function useHomeLocation(): HomeLocation | null {
  const [location, setLocation] = useState<HomeLocation | null>(null);

  useEffect(() => {
    let cancelled = false;

    async function resolveLocation() {
      const measureStartedAt = Date.now();
      let outcome:
        | 'success'
        | 'permission-denied'
        | 'services-disabled'
        | 'cancelled'
        | 'error' = 'error';

      // 콜드 스타트 계측 로그. 개발 빌드에서만 남깁니다(운영 핫패스에서는 실행 자체를 안 함).
      if (__DEV__) {
        console.info('[ColdStartData] location:start', {
          epochMs: measureStartedAt,
        });
      }

      try {
        const [permission, servicesEnabled] = await Promise.all([
          Location.getForegroundPermissionsAsync(),
          Location.hasServicesEnabledAsync(),
        ]);
        const isGranted = permission.status === Location.PermissionStatus.GRANTED;

        if (cancelled) {
          outcome = 'cancelled';
          return;
        }
        if (!isGranted) {
          outcome = 'permission-denied';
          return;
        }
        if (!servicesEnabled) {
          outcome = 'services-disabled';
          return;
        }

        const position = await Location.getCurrentPositionAsync({
          accuracy: Location.Accuracy.Balanced,
        });
        if (cancelled) {
          outcome = 'cancelled';
          return;
        }

        setLocation({ latitude: position.coords.latitude, longitude: position.coords.longitude });
        outcome = 'success';
      } catch {
        outcome = cancelled ? 'cancelled' : 'error';
        // 위치 조회 실패는 조용히 무시 — 서버가 기본 위치로 대체합니다.
      } finally {
        if (__DEV__) {
          const measureCompletedAt = Date.now();
          console.info('[ColdStartData] location:complete', {
            epochMs: measureCompletedAt,
            durationMs: measureCompletedAt - measureStartedAt,
            ok: outcome === 'success',
            outcome,
          });
        }
      }
    }

    void resolveLocation();
    return () => {
      cancelled = true;
    };
  }, []);

  return location;
}
