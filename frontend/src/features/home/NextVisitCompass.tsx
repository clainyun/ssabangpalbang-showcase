import * as Location from 'expo-location';
import { useEffect, useState } from 'react';
import { StyleSheet, Text, View } from 'react-native';
import Animated, { useAnimatedStyle, useSharedValue, withTiming } from 'react-native-reanimated';

import { useHomeLocation } from '@/features/home/useHomeLocation';

interface Coord {
  latitude: number;
  longitude: number;
}

// 🟡 목업: 실제 임장지 좌표가 API 응답(NextVisit)에 아직 없어서 테스트용 고정 좌표(강남역).
// TODO(실데이터): NextVisit에 latitude/longitude가 생기면 target prop으로 교체.
const MOCK_TARGET = { latitude: 37.497942, longitude: 127.02762, label: '강남역(목업)' };
// GPS 거부/미측정 시 방향 계산용 폴백 현재 위치(서울시청).
const FALLBACK_ORIGIN: Coord = { latitude: 37.5663, longitude: 126.9779 };

const toRad = (deg: number) => (deg * Math.PI) / 180;
const toDeg = (rad: number) => (rad * 180) / Math.PI;

/** 두 좌표 사이 초기 방위각(정북 0°, 시계방향 0~360°). */
function bearingDeg(from: Coord, to: Coord): number {
  const phi1 = toRad(from.latitude);
  const phi2 = toRad(to.latitude);
  const dLambda = toRad(to.longitude - from.longitude);
  const y = Math.sin(dLambda) * Math.cos(phi2);
  const x = Math.cos(phi1) * Math.sin(phi2) - Math.sin(phi1) * Math.cos(phi2) * Math.cos(dLambda);
  return (toDeg(Math.atan2(y, x)) + 360) % 360;
}

/** 하버사인 거리(km). */
function distanceKm(from: Coord, to: Coord): number {
  const R = 6371;
  const dPhi = toRad(to.latitude - from.latitude);
  const dLambda = toRad(to.longitude - from.longitude);
  const a =
    Math.sin(dPhi / 2) ** 2 +
    Math.cos(toRad(from.latitude)) * Math.cos(toRad(to.latitude)) * Math.sin(dLambda / 2) ** 2;
  return R * 2 * Math.atan2(Math.sqrt(a), Math.sqrt(1 - a));
}

/**
 * "다음 임장지 방향 나침반" (프로토타입 · 목업 좌표).
 * 기기 나침반(watchHeadingAsync)과 내 위치→임장지 방위각의 차이만큼 화살표를 회전시켜,
 * 폰을 어느 방향으로 돌려도 화살표가 항상 임장지 쪽을 실시간으로 가리킵니다.
 * (화살표 위(↑) = 지금 향한 방향이 곧 임장지 방향 = 목적지 정면)
 */
export function NextVisitCompass({ target = MOCK_TARGET }: { target?: Coord & { label?: string } }) {
  const gps = useHomeLocation();
  const origin = gps ?? FALLBACK_ORIGIN;

  const bearing = bearingDeg(origin, target);
  const dist = distanceKm(origin, target);

  const rotation = useSharedValue(0);
  const [heading, setHeading] = useState<number | null>(null);

  useEffect(() => {
    let sub: Location.LocationSubscription | undefined;
    let cancelled = false;

    async function subscribe() {
      const permission = await Location.getForegroundPermissionsAsync();
      if (permission.status !== Location.PermissionStatus.GRANTED || cancelled) {
        return;
      }
      sub = await Location.watchHeadingAsync((h) => {
        // trueHeading이 -1(미지원/미보정)이면 magHeading으로 폴백.
        setHeading(h.trueHeading >= 0 ? h.trueHeading : h.magHeading);
      });
    }

    void subscribe();
    return () => {
      cancelled = true;
      sub?.remove();
    };
  }, []);

  useEffect(() => {
    if (heading == null) {
      return;
    }
    const desired = (bearing - heading + 360) % 360;
    // 0/360 경계에서 먼 쪽으로 도는 것 방지 — 최단 회전(±180°)으로 언랩.
    const delta = ((desired - (rotation.value % 360) + 540) % 360) - 180;
    rotation.value = withTiming(rotation.value + delta, { duration: 120 });
  }, [heading, bearing, rotation]);

  const arrowStyle = useAnimatedStyle(() => ({ transform: [{ rotate: `${rotation.value}deg` }] }));

  const ready = heading != null;
  const distText = dist < 1 ? `${Math.round(dist * 1000)}m` : `${dist.toFixed(1)}km`;

  return (
    <View style={styles.card}>
      <View style={styles.dial}>
        <Animated.View style={arrowStyle}>
          <Text style={styles.arrow}>↑</Text>
        </Animated.View>
      </View>
      <View style={styles.info}>
        <Text style={styles.title}>다음 임장지 방향</Text>
        <Text style={styles.sub}>
          {target.label ?? '목적지'} · {distText}
        </Text>
        {!ready && <Text style={styles.hint}>나침반 준비중… (∞자로 흔들어 보정)</Text>}
      </View>
    </View>
  );
}

const styles = StyleSheet.create({
  card: {
    flexDirection: 'row',
    alignItems: 'center',
    gap: 14,
    paddingVertical: 12,
    paddingHorizontal: 16,
    borderRadius: 20,
    backgroundColor: 'rgba(255, 255, 255, 0.82)',
    shadowColor: '#0B1A14',
    shadowOffset: { width: 0, height: 4 },
    shadowOpacity: 0.12,
    shadowRadius: 10,
    elevation: 4,
  },
  dial: {
    width: 54,
    height: 54,
    borderRadius: 27,
    alignItems: 'center',
    justifyContent: 'center',
    backgroundColor: '#13B26E',
  },
  arrow: {
    fontSize: 30,
    lineHeight: 34,
    fontWeight: '900',
    color: '#FFFFFF',
  },
  info: {
    flex: 1,
    gap: 2,
  },
  title: {
    fontSize: 15,
    fontWeight: '800',
    color: '#17211C',
  },
  sub: {
    fontSize: 13,
    color: '#4A5852',
  },
  hint: {
    fontSize: 11,
    color: '#B08900',
  },
});
