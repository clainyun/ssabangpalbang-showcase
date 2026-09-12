import Ionicons from '@expo/vector-icons/Ionicons';
import { Image as ExpoImage } from 'expo-image';
import { StatusBar } from 'expo-status-bar';
import { useEffect, useMemo, useState } from 'react';
import {
  AccessibilityInfo,
  Animated,
  AppState,
  Image,
  PixelRatio,
  Pressable,
  StyleSheet,
  Text,
  useWindowDimensions,
  View,
} from 'react-native';
import Reanimated, { useAnimatedStyle } from 'react-native-reanimated';
import { useSafeAreaInsets } from 'react-native-safe-area-context';

import { useSpriteFrame } from '@/features/character/useSpriteFrame';
import { ReportMascotSprite } from '@/features/report/ReportMascotSprite';

const REPORT_BG_DAY = require('../../../assets/images/generation/report-bg-day.png');
const REPORT_BG_NIGHT = require('../../../assets/images/generation/report-bg-night.png');
const REPORT_GROUND = require('../../../assets/images/generation/report-ground.png');
const REPORT_SUN = require('../../../assets/images/generation/report-sun.png');
const REPORT_MOON = require('../../../assets/images/generation/report-moon.png');
const CHECKLIST_BG = require('../../../assets/images/generation/checklist-bg.png');
const CHECKLIST_SPRITE = require('../../../assets/images/generation/checklist-sprite.png');

interface GenerationSpinnerScreenProps {
  variant: 'report' | 'checklist';
  progress?: number;
  message?: string | null;
  errorMessage?: string | null;
  actionLabel?: string;
  onAction?: () => void;
  onBack: () => void;
}

export function GenerationSpinnerScreen({
  variant,
  progress,
  message,
  errorMessage,
  actionLabel = '다시 시도',
  onAction,
  onBack,
}: GenerationSpinnerScreenProps) {
  const insets = useSafeAreaInsets();
  const { height, width } = useWindowDimensions();
  const prefersReducedMotion = usePrefersReducedMotion();
  const [backgroundPhase] = useState(() => new Animated.Value(0));
  const [progressValue] = useState(() => new Animated.Value(progress ?? 0));

  useEffect(() => {
    backgroundPhase.setValue(0);
    if (variant !== 'report' || prefersReducedMotion) return;

    const loop = Animated.loop(
      Animated.timing(backgroundPhase, {
        toValue: 1,
        duration: 6_000,
        useNativeDriver: true,
      }),
    );
    loop.start();
    return () => loop.stop();
  }, [backgroundPhase, prefersReducedMotion, variant]);

  const visibleProgress = Math.max(0, Math.min(100, progress ?? 0));
  const visibleMessage =
    errorMessage ??
    message ??
    (variant === 'checklist'
      ? '체크리스트 생성을 준비하고 있어요.'
      : '체크리스트 답변을 분석하는 중…');

  useEffect(() => {
    Animated.timing(progressValue, {
      toValue: visibleProgress,
      duration: prefersReducedMotion ? 0 : 450,
      useNativeDriver: false,
    }).start();
  }, [prefersReducedMotion, progressValue, visibleProgress]);

  const nightOpacity = backgroundPhase.interpolate({
    inputRange: [0, 0.45, 0.5, 0.95, 1],
    outputRange: [0, 0, 1, 1, 0],
  });
  const orbitRotation = backgroundPhase.interpolate({
    inputRange: [0, 1],
    outputRange: ['0deg', '360deg'],
  });
  const progressWidth = progressValue.interpolate({
    inputRange: [0, 100],
    outputRange: ['0%', '100%'],
  });
  const title = variant === 'report' ? '리포트를\n만들어 볼게요' : '체크리스트를\n만들어 볼게요';
  const reportSpriteSize = Math.min(220, width * 0.56);
  const checklistSpriteHeight = PixelRatio.roundToNearestPixel(
    Math.min(640, width * 1.62, height * 0.74),
  );

  return (
    <View style={styles.screen}>
      <StatusBar style="dark" />
      {variant === 'report' ? (
        <>
          <Image resizeMode="cover" source={REPORT_BG_DAY} style={styles.backgroundImage} />
          <Animated.Image
            resizeMode="cover"
            source={REPORT_BG_NIGHT}
            style={[styles.backgroundImage, { opacity: nightOpacity }]}
          />
          <Animated.View
            pointerEvents="none"
            style={[
              styles.orbit,
              {
                top: height * 0.57,
                transform: [{ rotate: orbitRotation }],
              },
            ]}
          >
            <Image source={REPORT_SUN} style={styles.orbitSun} />
            <Image source={REPORT_MOON} style={styles.orbitMoon} />
          </Animated.View>
          <Image resizeMode="cover" source={REPORT_GROUND} style={styles.backgroundImage} />
        </>
      ) : (
        <ExpoImage
          allowDownscaling={false}
          contentFit="cover"
          decodeFormat="argb"
          source={CHECKLIST_BG}
          style={styles.backgroundImage}
        />
      )}

      <Pressable
        accessibilityLabel="이전 화면으로 돌아가기"
        accessibilityRole="button"
        hitSlop={12}
        onPress={onBack}
        style={({ pressed }) => [
          styles.backButton,
          { top: insets.top + 12 },
          pressed && styles.pressed,
        ]}
      >
        <Ionicons color="#12211C" name="chevron-back" size={22} />
      </Pressable>

      <View style={[styles.content, { paddingTop: insets.top + 88 }]}>
        <View style={variant === 'checklist' && styles.titleBackdrop}>
          <Text style={styles.title}>{title}</Text>
          <Text
            accessibilityLiveRegion="polite"
            style={[styles.message, errorMessage && styles.errorMessage]}
          >
            {visibleMessage}
          </Text>
        </View>

        <View style={styles.artArea}>
          {variant === 'report' ? (
            <ReportMascotSprite reduceMotion={prefersReducedMotion} size={reportSpriteSize} />
          ) : (
            <ChecklistGenerationSprite
              height={checklistSpriteHeight}
              reduceMotion={prefersReducedMotion}
            />
          )}
        </View>

        <View
          style={[
            styles.progressPanel,
            variant === 'checklist' && styles.checklistProgressPanel,
            { paddingBottom: Math.max(insets.bottom + 28, 42) },
          ]}
        >
          {errorMessage && onAction ? (
            <Pressable
              accessibilityRole="button"
              onPress={onAction}
              style={({ pressed }) => [styles.actionButton, pressed && styles.pressed]}
            >
              <Text style={styles.actionButtonText}>{actionLabel}</Text>
            </Pressable>
          ) : (
            <>
              <View
                accessibilityLabel={`${Math.round(visibleProgress)}% 진행됨`}
                accessibilityRole="progressbar"
                accessibilityValue={{ min: 0, max: 100, now: Math.round(visibleProgress) }}
                style={styles.progressTrack}
              >
                <Animated.View style={[styles.progressFill, { width: progressWidth }]} />
              </View>
              <Text style={styles.progressText}>{Math.round(visibleProgress)}%</Text>
            </>
          )}
        </View>
      </View>
    </View>
  );
}

function ChecklistGenerationSprite({
  height,
  reduceMotion,
}: {
  height: number;
  reduceMotion: boolean;
}) {
  const frame = useSpriteFrame({ frameCount: 37, fps: 12, playing: !reduceMotion });
  const frameHeight = PixelRatio.roundToNearestPixel(height);
  const frameWidth = PixelRatio.roundToNearestPixel(frameHeight * (9 / 16));
  const sheetWidth = frameWidth * 7;
  const sheetHeight = frameHeight * 6;
  const animatedStyle = useAnimatedStyle(() => {
    const index = frame.get();
    const column = index % 7;
    const row = Math.floor(index / 7);
    return {
      transform: [{ translateX: -column * frameWidth }, { translateY: -row * frameHeight }],
    };
  });

  return (
    <View
      accessibilityElementsHidden
      importantForAccessibility="no-hide-descendants"
      pointerEvents="none"
      style={{ width: frameWidth, height: frameHeight, overflow: 'hidden' }}
    >
      <Reanimated.View style={[{ width: sheetWidth, height: sheetHeight }, animatedStyle]}>
        <ExpoImage
          allowDownscaling={false}
          contentFit="fill"
          decodeFormat="argb"
          source={CHECKLIST_SPRITE}
          style={{ width: sheetWidth, height: sheetHeight }}
        />
      </Reanimated.View>
    </View>
  );
}

function usePrefersReducedMotion() {
  const [prefersReducedMotion, setPrefersReducedMotion] = useState(false);
  const [isAppActive, setIsAppActive] = useState(AppState.currentState === 'active');

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

  return useMemo(() => prefersReducedMotion || !isAppActive, [isAppActive, prefersReducedMotion]);
}

const styles = StyleSheet.create({
  screen: {
    flex: 1,
    overflow: 'hidden',
    backgroundColor: '#F8FFFC',
  },
  backgroundImage: {
    position: 'absolute',
    top: 0,
    right: 0,
    bottom: 0,
    left: 0,
    width: '100%',
    height: '100%',
  },
  orbit: {
    position: 'absolute',
    left: '50%',
    width: 1,
    height: 1,
  },
  orbitSun: {
    position: 'absolute',
    top: -190,
    left: -55,
    width: 110,
    height: 110,
  },
  orbitMoon: {
    position: 'absolute',
    top: 80,
    left: -55,
    width: 110,
    height: 110,
    transform: [{ scaleY: -1 }],
  },
  backButton: {
    position: 'absolute',
    left: 15,
    zIndex: 10,
    width: 44,
    height: 44,
    borderRadius: 22,
    alignItems: 'center',
    justifyContent: 'center',
    backgroundColor: '#FFFFFF',
    shadowColor: '#143C2D',
    shadowOffset: { width: 0, height: 8 },
    shadowOpacity: 0.14,
    shadowRadius: 9,
    elevation: 5,
  },
  content: {
    flex: 1,
    zIndex: 2,
  },
  titleBackdrop: {
    alignSelf: 'center',
    minWidth: '82%',
    paddingHorizontal: 22,
    paddingVertical: 14,
    borderRadius: 20,
  },
  title: {
    fontSize: 35,
    lineHeight: 43,
    fontWeight: '900',
    letterSpacing: -1.2,
    textAlign: 'center',
    color: '#12211C',
  },
  message: {
    minHeight: 20,
    marginTop: 8,
    paddingHorizontal: 20,
    fontSize: 14,
    lineHeight: 20,
    fontWeight: '600',
    textAlign: 'center',
    color: '#7A8B84',
  },
  errorMessage: {
    color: '#C14432',
  },
  artArea: {
    flex: 1,
    width: '100%',
    minHeight: 250,
    marginTop: -16,
    alignItems: 'center',
    justifyContent: 'center',
    overflow: 'hidden',
  },
  progressPanel: {
    zIndex: 4,
    paddingTop: 14,
    paddingHorizontal: 32,
  },
  checklistProgressPanel: {
    marginHorizontal: 24,
    paddingTop: 20,
    paddingHorizontal: 16,
    borderRadius: 20,
  },
  progressTrack: {
    width: '100%',
    height: 10,
    overflow: 'hidden',
    borderRadius: 999,
    backgroundColor: '#EEF2F0',
  },
  progressFill: {
    height: '100%',
    borderRadius: 999,
    backgroundColor: '#37C99A',
  },
  progressText: {
    marginTop: 10,
    fontSize: 13,
    lineHeight: 18,
    fontWeight: '800',
    textAlign: 'center',
    color: '#00B583',
  },
  actionButton: {
    alignSelf: 'center',
    minWidth: 132,
    height: 46,
    paddingHorizontal: 22,
    borderRadius: 23,
    alignItems: 'center',
    justifyContent: 'center',
    backgroundColor: '#12211C',
  },
  actionButtonText: {
    fontSize: 14,
    fontWeight: '800',
    color: '#80FFCC',
  },
  pressed: {
    opacity: 0.78,
  },
});
