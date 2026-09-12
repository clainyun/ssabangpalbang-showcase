import { useFocusEffect } from 'expo-router';
import { useCallback, useEffect, useState } from 'react';
import { AccessibilityInfo, AppState, Image, StyleSheet, View } from 'react-native';
import Animated, { useAnimatedStyle } from 'react-native-reanimated';

import { useSpriteFrame } from '@/features/character/useSpriteFrame';

const COLUMN_COUNT = 6;
const ROW_COUNT = 6;
const FRAME_COUNT = COLUMN_COUNT * ROW_COUNT;
const PING_PONG_FRAME_COUNT = FRAME_COUNT * 2 - 2;
const REPORT_SPRITE = require('../../../assets/images/report/report-sprite.png');

interface ReportMascotSpriteProps {
  size?: number;
  reduceMotion?: boolean;
}

export function ReportMascotSprite({
  size = 172,
  reduceMotion = false,
}: ReportMascotSpriteProps) {
  const [isFocused, setIsFocused] = useState(true);
  const [isAppActive, setIsAppActive] = useState(AppState.currentState === 'active');
  const [prefersReducedMotion, setPrefersReducedMotion] = useState(false);

  useFocusEffect(
    useCallback(() => {
      setIsFocused(true);
      return () => setIsFocused(false);
    }, []),
  );

  useEffect(() => {
    let mounted = true;
    void AccessibilityInfo.isReduceMotionEnabled().then((enabled) => {
      if (mounted) setPrefersReducedMotion(enabled);
    });
    const motionSubscription = AccessibilityInfo.addEventListener(
      'reduceMotionChanged',
      setPrefersReducedMotion,
    );
    const appStateSubscription = AppState.addEventListener('change', (state) => {
      setIsAppActive(state === 'active');
    });

    return () => {
      mounted = false;
      motionSubscription.remove();
      appStateSubscription.remove();
    };
  }, []);

  const cycleFrame = useSpriteFrame({
    frameCount: PING_PONG_FRAME_COUNT,
    fps: 8,
    playing: isFocused && isAppActive && !reduceMotion && !prefersReducedMotion,
  });
  const sheetSize = size * COLUMN_COUNT;

  const sheetStyle = useAnimatedStyle(() => {
    const cycleIndex = cycleFrame.get();
    const frameIndex = cycleIndex < FRAME_COUNT ? cycleIndex : PING_PONG_FRAME_COUNT - cycleIndex;
    const column = frameIndex % COLUMN_COUNT;
    const row = Math.floor(frameIndex / COLUMN_COUNT);

    return {
      transform: [{ translateX: -column * size }, { translateY: -row * size }],
    };
  });

  return (
    <View
      accessibilityElementsHidden
      importantForAccessibility="no-hide-descendants"
      pointerEvents="none"
      style={[styles.frame, { width: size, height: size }]}
    >
      <Animated.View style={[{ width: sheetSize, height: sheetSize }, sheetStyle]}>
        <Image
          resizeMode="stretch"
          source={REPORT_SPRITE}
          style={{ width: sheetSize, height: sheetSize }}
        />
      </Animated.View>
    </View>
  );
}

const styles = StyleSheet.create({
  frame: {
    overflow: 'hidden',
  },
});
