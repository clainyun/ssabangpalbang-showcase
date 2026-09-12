import { LiquidGlassView } from '@uginy/react-native-liquid-glass';
import { StyleSheet, Text, View } from 'react-native';

import type { HomeWeather } from '@/features/home/types';
import { FINE_DUST_EMOJI, RAIN_PROBABILITY_EMOJI, resolveWeatherEmoji } from '@/features/home/weatherIcons';

// 유리.png 톤: 강한 굴절(CRYSTAL)이 아니라 뿌연 우윳빛 서리유리 + 상단 대각선 글레어.
const GLASS_CONFIG = {
  blurRadius: 34, // 뒤 배경 아이콘을 강하게 뭉갬 = 서리 느낌
  glassOpacity: 0.14, // 우윳빛 정도 (↑ 더 뿌옇게) — 취향껏 0.10~0.20
  tintColor: '#ffffff',
  refractionStrength: 0.02, // CRYSTAL보다 훨씬 낮게(굴절 약하게)
  chromaticAberration: 0.02,
  edgeGlowIntensity: 0.22, // 모서리 밝은 림
  glareIntensity: 0.28, // 상단 대각선 빛줄기 세기
  iridescence: 0,
  noiseIntensity: 0,
  cornerRadius: 28,
} as const;

const WEEKDAYS = ['SUN', 'MON', 'TUE', 'WED', 'THU', 'FRI', 'SAT'] as const;

// "Sunday 12, Apr 2026" 포맷. RN 앱 런타임에선 new Date() 정상 동작.
const formatToday = () =>
  new Date().toLocaleDateString('en-US', { weekday: 'long', day: 'numeric', month: 'short', year: 'numeric' });

interface WeatherBoxProps {
  weather: HomeWeather | undefined;
  isLoading: boolean;
}

/** 홈 화면 날씨 카드. `@uginy/react-native-liquid-glass`로 애플 날씨 위젯 스타일의
 * 서리유리 표면을 그리고, 그 위에 날짜·온도·강수/미세먼지·요일 스트립을 얹습니다.
 * 배경 아이콘이 계속 움직이므로 텍스트는 전부 흰색 + 옅은 그림자로 가독성 확보. */
export function WeatherBox({ weather, isLoading }: WeatherBoxProps) {
  if (isLoading) {
    return null;
  }
  const ok = !!weather?.available;
  const todayIdx = new Date().getDay();

  return (
    <View style={styles.shadowWrapper}>
      <LiquidGlassView {...GLASS_CONFIG} style={styles.glass}>
        <View style={styles.pad}>
          {/* Row1: 날짜 + 날씨 아이콘 */}
          <View style={styles.rowTop}>
            <Text style={styles.date}>{formatToday()}</Text>
            <Text style={styles.icon}>
              {ok ? resolveWeatherEmoji(weather!.iconKey, weather!.conditionCode) : '—'}
            </Text>
          </View>

          {/* Row2: 큰 온도 + (상태/강수/미세먼지) */}
          <View style={styles.rowMain}>
            <Text style={styles.bigTemp}>
              {ok && weather!.temperatureCelsius !== null ? `${Math.round(weather!.temperatureCelsius)}°C` : '--°'}
            </Text>
            <View style={styles.side}>
              <Text style={styles.condition}>{ok ? (weather!.conditionText ?? '-') : '날씨 정보 없음'}</Text>
              <View style={styles.pillRow}>
                <View style={styles.pill}>
                  <Text style={styles.pillText}>
                    {RAIN_PROBABILITY_EMOJI} {ok && weather!.rainProbability !== null ? `${weather!.rainProbability}%` : '-'}
                  </Text>
                </View>
                <View style={styles.pill}>
                  <Text style={styles.pillText}>
                    {FINE_DUST_EMOJI} {ok && weather!.fineDustValue !== null ? Math.round(weather!.fineDustValue) : '-'}
                  </Text>
                </View>
              </View>
            </View>
          </View>

          {/* Row3: 요일 스트립 — ⚠️ 실제 예보 데이터 없음(오늘만 하이라이트하는 장식).
              주간 예보 API가 생기기 전까지는 장식용. 필요 없으면 이 블록만 지우면 됨. */}
          <View style={styles.weekRow}>
            {WEEKDAYS.map((d, i) => (
              <View key={d} style={[styles.dayCell, i === todayIdx && styles.dayActive]}>
                <Text style={[styles.dayText, i === todayIdx && styles.dayTextActive]}>{d}</Text>
              </View>
            ))}
          </View>
        </View>
      </LiquidGlassView>
    </View>
  );
}

const TEXT_SHADOW = {
  textShadowColor: 'rgba(0, 0, 0, 0.35)',
  textShadowOffset: { width: 0, height: 1 },
  textShadowRadius: 4,
} as const;

const styles = StyleSheet.create({
  // LiquidGlassView 자체가 셰이더로 모서리·테두리를 그리므로, 바깥은 그림자·간격만 담당.
  shadowWrapper: {
    marginBottom: 14,
    borderRadius: 28,
    elevation: 8,
    shadowColor: '#0B1A14',
    shadowOffset: { width: 0, height: 6 },
    shadowOpacity: 0.2,
    shadowRadius: 14,
  },
  glass: {
    borderRadius: 28,
  },
  pad: {
    paddingHorizontal: 22,
    paddingTop: 18,
    paddingBottom: 14,
    gap: 14,
  },

  // Row1
  rowTop: {
    flexDirection: 'row',
    alignItems: 'center',
    justifyContent: 'space-between',
  },
  date: {
    fontSize: 16,
    fontWeight: '600',
    color: '#FFFFFF',
    ...TEXT_SHADOW,
  },
  icon: {
    fontSize: 26,
    lineHeight: 30,
  },

  // Row2
  rowMain: {
    flexDirection: 'row',
    alignItems: 'flex-end',
    justifyContent: 'space-between',
  },
  bigTemp: {
    fontSize: 60,
    fontWeight: '800',
    color: '#FFFFFF',
    letterSpacing: -1,
    ...TEXT_SHADOW,
  },
  side: {
    alignItems: 'flex-end',
    gap: 8,
    paddingBottom: 6,
  },
  condition: {
    fontSize: 15,
    fontWeight: '600',
    color: '#FFFFFF',
    ...TEXT_SHADOW,
  },
  pillRow: {
    flexDirection: 'row',
    gap: 8,
  },
  pill: {
    backgroundColor: 'rgba(0, 0, 0, 0.22)',
    borderRadius: 14,
    paddingHorizontal: 12,
    paddingVertical: 6,
  },
  pillText: {
    fontSize: 13,
    fontWeight: '600',
    color: '#FFFFFF',
  },

  // Row3
  weekRow: {
    flexDirection: 'row',
    alignItems: 'center',
    justifyContent: 'space-between',
    marginTop: 2,
  },
  dayCell: {
    flex: 1,
    alignItems: 'center',
    paddingVertical: 8,
    borderRadius: 16,
  },
  dayActive: {
    backgroundColor: '#FFFFFF',
  },
  dayText: {
    fontSize: 13,
    fontWeight: '700',
    color: '#FFFFFF',
    ...TEXT_SHADOW,
  },
  dayTextActive: {
    color: '#1B1B1B',
    textShadowColor: 'transparent',
  },
});
