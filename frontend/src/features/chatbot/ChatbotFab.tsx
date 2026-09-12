import {
  StyleSheet,
  useWindowDimensions,
  View,
  type StyleProp,
  type ViewStyle,
} from 'react-native';
import { Gesture, GestureDetector } from 'react-native-gesture-handler';
import { useSafeAreaInsets } from 'react-native-safe-area-context';
import Animated, {
  runOnJS,
  useAnimatedStyle,
  useSharedValue,
  withTiming,
} from 'react-native-reanimated';

import { getFloatingTabBarOverlayBottom } from '@/components/floatingTabBarMetrics';

import { SpriteSheetView } from './SpriteSheetView';

// 챗봇 마스코트(초록 지붕 집 캐릭터) 6×6 스프라이트시트(정렬됨).
const CHATBOT_SPRITE = require('../../../assets/chatbot-sprite.png');

// 이 거리(dp) 미만으로 움직이면 드래그가 아니라 탭(=열기)으로 본다.
const TAP_SLOP = 8;
// 화면 가장자리에 남길 최소 여백(dp).
const EDGE_MARGIN = 8;

interface ChatbotFabProps {
  onPress: () => void;
  /** 하단 오프셋(dp). 화면마다 하단 UI 높이가 달라 조정한다. */
  bottom?: number;
  /** 우측 오프셋(dp). */
  right?: number;
  /** 마스코트 표시 크기(dp). */
  size?: number;
  /** 화면별 레이어 순서를 조정할 때 사용합니다. */
  style?: StyleProp<ViewStyle>;
}

/**
 * 우측 하단 플로팅 챗봇 버튼. 탭하면 열리고, 드래그하면 화면 안에서 이동한다.
 * (화면 밖으로 나가지 않도록 이동 범위를 clamp한다.)
 */
export function ChatbotFab({
  onPress,
  bottom = 24,
  right = 20,
  size = 80,
  style,
}: ChatbotFabProps) {
  const { width: winWidth, height: winHeight } = useWindowDimensions();
  const insets = useSafeAreaInsets();

  const translateX = useSharedValue(0);
  const translateY = useSharedValue(0);
  const startX = useSharedValue(0);
  const startY = useSharedValue(0);
  const scale = useSharedValue(1);

  // 호출 화면은 모두 하단 플로팅 탭 바를 사용한다. 기본 위치와 드래그 최하단을
  // 탭 바 상단보다 높게 유지해 탭 바가 FAB의 터치 영역을 가로채지 않게 한다.
  const minimumBottom = getFloatingTabBarOverlayBottom(insets.bottom);
  const anchorBottom = Math.max(bottom, minimumBottom);

  // 기본 앵커(우측 하단) 기준 이동 가능 범위.
  const minX = -(winWidth - size - right - EDGE_MARGIN);
  const maxX = right - EDGE_MARGIN;
  const minY = -(winHeight - size - anchorBottom - EDGE_MARGIN);
  const maxY = anchorBottom - minimumBottom;

  const panGesture = Gesture.Pan()
    .onBegin(() => {
      scale.value = withTiming(0.92, { duration: 90 });
    })
    .onStart(() => {
      startX.value = translateX.value;
      startY.value = translateY.value;
    })
    .onUpdate((event) => {
      const nextX = startX.value + event.translationX;
      const nextY = startY.value + event.translationY;
      translateX.value = Math.min(Math.max(nextX, minX), maxX);
      translateY.value = Math.min(Math.max(nextY, minY), maxY);
    })
    .onFinalize(() => {
      scale.value = withTiming(1, { duration: 120 });
    });

  // Pan 단독으로는 '움직임 없는 순수 탭'이 활성화되지 않아 onPress가 호출되지 않는다.
  // Tap 제스처를 Race로 합쳐 탭은 열기로, 드래그는 이동으로 처리한다.
  const tapGesture = Gesture.Tap()
    .maxDistance(TAP_SLOP)
    .onEnd((_event, success) => {
      if (success) {
        runOnJS(onPress)();
      }
    });

  const gesture = Gesture.Race(panGesture, tapGesture);

  const animatedStyle = useAnimatedStyle(() => ({
    transform: [
      { translateX: translateX.value },
      { translateY: translateY.value },
      { scale: scale.value },
    ],
  }));

  return (
    <GestureDetector gesture={gesture}>
      <Animated.View
        accessibilityLabel="임장 도우미 챗봇 열기"
        accessibilityRole="button"
        style={[
          styles.fab,
          { bottom: anchorBottom, right, width: size, height: size },
          style,
          animatedStyle,
        ]}
      >
        {/* 마스코트 뒤 소프트 블러 그림자 — 하드한 가장자리를 눌러 자연스럽게 앉아 보이게. */}
        <View
          style={[
            styles.shadow,
            { width: size * 0.6, height: size * 0.5, borderRadius: size, top: size * 0.2 },
          ]}
        />
        <SpriteSheetView
          columns={6}
          edgeInset={0.07}
          fps={10}
          pingPong
          rows={6}
          size={size}
          source={CHATBOT_SPRITE}
        />
      </Animated.View>
    </GestureDetector>
  );
}

const styles = StyleSheet.create({
  fab: {
    position: 'absolute',
    alignItems: 'center',
    justifyContent: 'center',
    // 임장 화면의 체크리스트 바텀시트(gorhom, elevation 사용) 위로 올려 가려지지 않게.
    // 배경이 투명이라 Android elevation 그림자는 생기지 않고 z-order만 올라간다.
    zIndex: 40,
    elevation: 40,
  },
  shadow: {
    position: 'absolute',
    backgroundColor: 'rgba(50, 70, 60, 0.10)',
    boxShadow: '0px 6px 14px rgba(28, 46, 38, 0.34)',
  },
});
