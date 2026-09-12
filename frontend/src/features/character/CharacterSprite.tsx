import { Image, StyleSheet, View } from 'react-native';
import Animated, { useAnimatedStyle } from 'react-native-reanimated';

import type { Direction } from '@/store/characterStore';

import { resolveSprite, type CharacterId, type MotionState } from './spriteConfig';
import { useIdleVariant } from './useIdleVariant';
import { useSpriteFrame } from './useSpriteFrame';

/**
 * 지도 위 표시 크기(dp). FE-014 스펙 권장 범위(72~88dp)는 지도에서 너무 작아 보여
 * 72dp 의 1.5배로 키웠습니다. 이 값 하나만 바꾸면 앵커 오프셋도 함께 맞습니다.
 */
export const CHARACTER_DISPLAY_SIZE = 115;

interface CharacterSpriteProps {
  characterId: CharacterId;
  motion: MotionState;
  direction: Direction;
  /** 표시 크기(dp). 시트 원본 128px 을 이 크기로 축소해 그립니다. */
  size?: number;
  /** 모션 축소·저성능 환경. 0번 프레임에 정지합니다. */
  reduceMotion?: boolean;
}

/**
 * §7.1 A안 — 클리핑 View(overflow:hidden) 안에 시트를 넣고 translateX 로 프레임을 넘깁니다.
 *
 * 이 파일이 "어떻게 그릴지" 한 겹 전부입니다. B안(Skia)으로 전환할 일이 생기면
 * 여기만 교체하면 되고 프레임 계산·방향 판정·좌표 변환은 그대로 씁니다.
 */
export function CharacterSprite({
  characterId,
  motion,
  direction,
  size = CHARACTER_DISPLAY_SIZE,
  reduceMotion = false,
}: CharacterSpriteProps) {
  const idleVariant = useIdleVariant(motion);
  const sprite = resolveSprite(characterId, motion, direction, idleVariant);
  const frameIndex = useSpriteFrame({
    frameCount: sprite.frameCount,
    fps: sprite.fps,
    playing: !reduceMotion && sprite.frameCount > 1,
  });

  const sheetWidth = size * sprite.frameCount;

  const frameStyle = useAnimatedStyle(() => ({
    transform: [{ translateX: -frameIndex.get() * size }],
  }));

  return (
    // 좌우 반전은 바깥 클리핑 View 에 겁니다. 안쪽 이미지에 걸면 translateX 방향이
    // 같이 뒤집혀서 프레임 순서가 거꾸로 갑니다.
    <View
      style={[styles.window, { width: size, height: size }, sprite.mirrored && styles.mirrored]}
    >
      <Animated.View style={[{ width: sheetWidth, height: size }, frameStyle]}>
        <Image
          source={sprite.source}
          style={{ width: sheetWidth, height: size }}
          // 실제 시트는 (frameCount : 1) 비율이 정확히 맞으므로 stretch 로 왜곡이 없습니다.
          // 플레이스홀더는 정사각형이 아니라서 contain 으로 비율을 지킵니다.
          resizeMode={sprite.isPlaceholder ? 'contain' : 'stretch'}
        />
      </Animated.View>
    </View>
  );
}

const styles = StyleSheet.create({
  // overflow:hidden 이 프레임 잘라내기의 핵심입니다.
  // ⚠️ Android 에서 overflow:hidden 과 transform 이 함께 걸릴 때 클리핑이 누락되는
  // 엣지케이스가 §7.1 에 리스크로 적혀 있습니다. 실기기에서 눈으로 확인해야 합니다.
  window: { overflow: 'hidden' },
  mirrored: { transform: [{ scaleX: -1 }] },
});
