import {
  Image,
  StyleSheet,
  View,
  type ImageSourcePropType,
  type StyleProp,
  type ViewStyle,
} from 'react-native';
import Animated, { useAnimatedStyle } from 'react-native-reanimated';

import { useSpriteFrame } from '@/features/character/useSpriteFrame';

interface SpriteSheetViewProps {
  source: ImageSourcePropType;
  /** 가로 칸 수. */
  columns: number;
  /** 세로 칸 수. */
  rows: number;
  /** 실제 프레임 수(기본 columns×rows). 마지막 줄이 덜 찼을 때만 지정. */
  frameCount?: number;
  fps: number;
  /** 프레임 한 칸의 표시 크기(dp, 정사각). */
  size: number;
  /** false면 0번 프레임에 정지. */
  playing?: boolean;
  /** true면 0→끝→0 핑퐁(yoyo) 재생. */
  pingPong?: boolean;
  /**
   * 지정하면 애니메이션 없이 해당 프레임 인덱스에 고정한다. 프레임 정렬(registration)이
   * 안 맞아 순환 시 캐릭터가 흔들리는 스프라이트는 이걸로 정지 표시한다.
   */
  frame?: number;
  /**
   * 셀 경계를 창 밖으로 잘라낼 비율(0~0.2 권장). 스프라이트를 작게 축소해 그릴 때
   * 인접 프레임이 경계에서 새어 보이는 블리딩을 막는다. 각 셀을 (1+2*edgeInset)배로
   * 확대해 중앙만 보여주므로 값이 클수록 프레임이 더 확대·크롭된다. 기본 0(크롭 없음).
   */
  edgeInset?: number;
  style?: StyleProp<ViewStyle>;
}

/**
 * 격자형 스프라이트시트(예: 6×6)를 프레임 순환 애니메이션으로 렌더한다.
 * CharacterSprite(가로 1행 스트립)와 달리 translateX/Y 둘 다로 2D 격자를 넘기고,
 * edgeInset으로 축소 시 경계 블리딩을 막는다. 프레임 인덱스 계산은 공용
 * useSpriteFrame(주사율 독립, UI 스레드)을 재사용한다.
 */
export function SpriteSheetView({
  source,
  columns,
  rows,
  frameCount,
  fps,
  size,
  playing = true,
  pingPong = false,
  frame,
  edgeInset = 0,
  style,
}: SpriteSheetViewProps) {
  const total = frameCount ?? columns * rows;
  const frameIndex = useSpriteFrame({
    frameCount: total,
    fps,
    playing: playing && total > 1 && frame === undefined,
    pingPong,
  });

  // 각 셀을 (1+2*edgeInset)배로 그려, 경계에서 margin 만큼을 창(size) 밖으로 잘라낸다.
  const cellSize = size * (1 + edgeInset * 2);
  const margin = size * edgeInset;
  const sheetWidth = cellSize * columns;
  const sheetHeight = cellSize * rows;

  const frameStyle = useAnimatedStyle(() => {
    const index = frame ?? frameIndex.get();
    const column = index % columns;
    const row = Math.floor(index / columns);
    return {
      transform: [
        { translateX: -(column * cellSize + margin) },
        { translateY: -(row * cellSize + margin) },
      ],
    };
  });

  return (
    <View style={[styles.window, { width: size, height: size }, style]}>
      <Animated.View
        style={[{ width: sheetWidth, height: sheetHeight }, frameStyle]}
      >
        <Image
          source={source}
          style={{ width: sheetWidth, height: sheetHeight }}
          resizeMode="stretch"
        />
      </Animated.View>
    </View>
  );
}

const styles = StyleSheet.create({
  // overflow:hidden 이 프레임 잘라내기의 핵심. (CharacterSprite와 동일 주의사항 —
  // Android에서 overflow:hidden+transform 클리핑 누락 엣지케이스는 실기기 확인 필요)
  window: { overflow: 'hidden' },
});
