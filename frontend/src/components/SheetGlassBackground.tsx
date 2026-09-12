import type { BottomSheetBackgroundProps } from '@gorhom/bottom-sheet';
import { LinearGradient } from 'expo-linear-gradient';
import { StyleSheet } from 'react-native';
import Animated, { Extrapolation, interpolate, useAnimatedStyle } from 'react-native-reanimated';

import { GlassTabBarBackground } from '@/components/GlassTabBarBackground';
import { SURFACE_COLOR } from '@/constants/colors';

/**
 * 바텀시트 배경. 접혀 있을 때는 탭바(FloatingTabBar)와 똑같은 물방울 유리로 보이다가,
 * 끌어올릴수록 옅은 회백색 그라디언트로 바뀝니다 — 접혀 있을 땐 지도 위 UI 하나처럼
 * 탭바와 한 톤으로 보이고, 펼치면 안의 목록·글자를 읽어야 하니 점점 불투명해지는
 * 방식입니다. 지도 탭 목록 시트와 임장 체크리스트 시트가 함께 씁니다.
 *
 * gorhom 의 `backgroundComponent` 는 style·animatedIndex 외에 커스텀 prop을 못
 * 받으므로, 반경과 "다 펼친" 스냅 인덱스별로 컴포넌트를 만들어 내는 팩토리
 * 형태입니다. 화면마다 `const XxxBackground = createSheetGlassBackground(...)`
 * 로 한 번만 만들어 모듈 스코프에 두고 재사용하세요(렌더마다 새로 만들지 않도록).
 */
export function createSheetGlassBackground(radius: number, expandedIndex: number) {
  return function SheetGlassBackground({ style, animatedIndex }: BottomSheetBackgroundProps) {
    const gradientOverlayStyle = useAnimatedStyle(() => ({
      opacity: interpolate(animatedIndex.get(), [0, expandedIndex], [0, 1], Extrapolation.CLAMP),
    }));

    return (
      <Animated.View
        style={[
          style,
          {
            borderRadius: radius,
            overflow: 'hidden',
            borderWidth: 1,
            borderColor: 'rgba(255, 255, 255, 0.55)',
          },
        ]}
      >
        <GlassTabBarBackground radius={radius} />
        <Animated.View style={[StyleSheet.absoluteFill, gradientOverlayStyle]}>
          <LinearGradient
            colors={['#F4F6F5', SURFACE_COLOR, '#EEF1F5']}
            locations={[0, 0.55, 1]}
            start={{ x: 0, y: 0 }}
            end={{ x: 0.4, y: 1 }}
            style={StyleSheet.absoluteFill}
          />
        </Animated.View>
      </Animated.View>
    );
  };
}
