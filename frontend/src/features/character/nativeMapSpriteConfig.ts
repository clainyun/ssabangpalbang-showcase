import type { ImageSourcePropType } from 'react-native';

import type { Direction } from '@/store/characterStore';

import type { CharacterId, IdleVariant, MotionState } from './spriteConfig';

export interface NativeMapSpriteFrame {
  id: string;
  source: ImageSourcePropType;
}

export interface NativeMapSprite {
  frames: readonly NativeMapSpriteFrame[];
  fps: number;
}

const WALK_FPS = 8;
const IDLE_FPS = 2.5;

function createFrames(
  prefix: string,
  sources: readonly ImageSourcePropType[],
): readonly NativeMapSpriteFrame[] {
  return sources.map((source, index) => ({
    id: `native-character-${prefix}-${index}`,
    source,
  }));
}

const PLAIN_WALK_N = createFrames('plain_walk_n', [
  require('../../../assets/images/characters/native-map/plain_walk_n_0.png'),
  require('../../../assets/images/characters/native-map/plain_walk_n_1.png'),
  require('../../../assets/images/characters/native-map/plain_walk_n_2.png'),
  require('../../../assets/images/characters/native-map/plain_walk_n_3.png'),
  require('../../../assets/images/characters/native-map/plain_walk_n_4.png'),
  require('../../../assets/images/characters/native-map/plain_walk_n_5.png'),
]);

const PLAIN_WALK_NE = createFrames('plain_walk_ne', [
  require('../../../assets/images/characters/native-map/plain_walk_ne_0.png'),
  require('../../../assets/images/characters/native-map/plain_walk_ne_1.png'),
  require('../../../assets/images/characters/native-map/plain_walk_ne_2.png'),
  require('../../../assets/images/characters/native-map/plain_walk_ne_3.png'),
  require('../../../assets/images/characters/native-map/plain_walk_ne_4.png'),
  require('../../../assets/images/characters/native-map/plain_walk_ne_5.png'),
]);

const PLAIN_WALK_E = createFrames('plain_walk_e', [
  require('../../../assets/images/characters/native-map/plain_walk_e_0.png'),
  require('../../../assets/images/characters/native-map/plain_walk_e_1.png'),
  require('../../../assets/images/characters/native-map/plain_walk_e_2.png'),
  require('../../../assets/images/characters/native-map/plain_walk_e_3.png'),
  require('../../../assets/images/characters/native-map/plain_walk_e_4.png'),
  require('../../../assets/images/characters/native-map/plain_walk_e_5.png'),
]);

const PLAIN_WALK_SE = createFrames('plain_walk_se', [
  require('../../../assets/images/characters/native-map/plain_walk_se_0.png'),
  require('../../../assets/images/characters/native-map/plain_walk_se_1.png'),
  require('../../../assets/images/characters/native-map/plain_walk_se_2.png'),
  require('../../../assets/images/characters/native-map/plain_walk_se_3.png'),
  require('../../../assets/images/characters/native-map/plain_walk_se_4.png'),
  require('../../../assets/images/characters/native-map/plain_walk_se_5.png'),
]);

const PLAIN_WALK_S = createFrames('plain_walk_s', [
  require('../../../assets/images/characters/native-map/plain_walk_s_0.png'),
  require('../../../assets/images/characters/native-map/plain_walk_s_1.png'),
  require('../../../assets/images/characters/native-map/plain_walk_s_2.png'),
  require('../../../assets/images/characters/native-map/plain_walk_s_3.png'),
  require('../../../assets/images/characters/native-map/plain_walk_s_4.png'),
  require('../../../assets/images/characters/native-map/plain_walk_s_5.png'),
]);

const PLAIN_WALK_SW = createFrames('plain_walk_sw', [
  require('../../../assets/images/characters/native-map/plain_walk_sw_0.png'),
  require('../../../assets/images/characters/native-map/plain_walk_sw_1.png'),
  require('../../../assets/images/characters/native-map/plain_walk_sw_2.png'),
  require('../../../assets/images/characters/native-map/plain_walk_sw_3.png'),
  require('../../../assets/images/characters/native-map/plain_walk_sw_4.png'),
  require('../../../assets/images/characters/native-map/plain_walk_sw_5.png'),
]);

const PLAIN_WALK_W = createFrames('plain_walk_w', [
  require('../../../assets/images/characters/native-map/plain_walk_w_0.png'),
  require('../../../assets/images/characters/native-map/plain_walk_w_1.png'),
  require('../../../assets/images/characters/native-map/plain_walk_w_2.png'),
  require('../../../assets/images/characters/native-map/plain_walk_w_3.png'),
  require('../../../assets/images/characters/native-map/plain_walk_w_4.png'),
  require('../../../assets/images/characters/native-map/plain_walk_w_5.png'),
]);

const PLAIN_WALK_NW = createFrames('plain_walk_nw', [
  require('../../../assets/images/characters/native-map/plain_walk_nw_0.png'),
  require('../../../assets/images/characters/native-map/plain_walk_nw_1.png'),
  require('../../../assets/images/characters/native-map/plain_walk_nw_2.png'),
  require('../../../assets/images/characters/native-map/plain_walk_nw_3.png'),
  require('../../../assets/images/characters/native-map/plain_walk_nw_4.png'),
  require('../../../assets/images/characters/native-map/plain_walk_nw_5.png'),
]);

const DOG_WALK_N = createFrames('dog_walk_n', [
  require('../../../assets/images/characters/native-map/dog_walk_n_0.png'),
  require('../../../assets/images/characters/native-map/dog_walk_n_1.png'),
  require('../../../assets/images/characters/native-map/dog_walk_n_2.png'),
  require('../../../assets/images/characters/native-map/dog_walk_n_3.png'),
  require('../../../assets/images/characters/native-map/dog_walk_n_4.png'),
  require('../../../assets/images/characters/native-map/dog_walk_n_5.png'),
]);

const DOG_WALK_NE = createFrames('dog_walk_ne', [
  require('../../../assets/images/characters/native-map/dog_walk_ne_0.png'),
  require('../../../assets/images/characters/native-map/dog_walk_ne_1.png'),
  require('../../../assets/images/characters/native-map/dog_walk_ne_2.png'),
  require('../../../assets/images/characters/native-map/dog_walk_ne_3.png'),
  require('../../../assets/images/characters/native-map/dog_walk_ne_4.png'),
  require('../../../assets/images/characters/native-map/dog_walk_ne_5.png'),
]);

const DOG_WALK_E = createFrames('dog_walk_e', [
  require('../../../assets/images/characters/native-map/dog_walk_e_0.png'),
  require('../../../assets/images/characters/native-map/dog_walk_e_1.png'),
  require('../../../assets/images/characters/native-map/dog_walk_e_2.png'),
  require('../../../assets/images/characters/native-map/dog_walk_e_3.png'),
  require('../../../assets/images/characters/native-map/dog_walk_e_4.png'),
  require('../../../assets/images/characters/native-map/dog_walk_e_5.png'),
]);

const DOG_WALK_SE = createFrames('dog_walk_se', [
  require('../../../assets/images/characters/native-map/dog_walk_se_0.png'),
  require('../../../assets/images/characters/native-map/dog_walk_se_1.png'),
  require('../../../assets/images/characters/native-map/dog_walk_se_2.png'),
  require('../../../assets/images/characters/native-map/dog_walk_se_3.png'),
  require('../../../assets/images/characters/native-map/dog_walk_se_4.png'),
  require('../../../assets/images/characters/native-map/dog_walk_se_5.png'),
]);

const DOG_WALK_S = createFrames('dog_walk_s', [
  require('../../../assets/images/characters/native-map/dog_walk_s_0.png'),
  require('../../../assets/images/characters/native-map/dog_walk_s_1.png'),
  require('../../../assets/images/characters/native-map/dog_walk_s_2.png'),
  require('../../../assets/images/characters/native-map/dog_walk_s_3.png'),
  require('../../../assets/images/characters/native-map/dog_walk_s_4.png'),
  require('../../../assets/images/characters/native-map/dog_walk_s_5.png'),
]);

const DOG_WALK_SW = createFrames('dog_walk_sw', [
  require('../../../assets/images/characters/native-map/dog_walk_sw_0.png'),
  require('../../../assets/images/characters/native-map/dog_walk_sw_1.png'),
  require('../../../assets/images/characters/native-map/dog_walk_sw_2.png'),
  require('../../../assets/images/characters/native-map/dog_walk_sw_3.png'),
  require('../../../assets/images/characters/native-map/dog_walk_sw_4.png'),
  require('../../../assets/images/characters/native-map/dog_walk_sw_5.png'),
]);

const DOG_WALK_W = createFrames('dog_walk_w', [
  require('../../../assets/images/characters/native-map/dog_walk_w_0.png'),
  require('../../../assets/images/characters/native-map/dog_walk_w_1.png'),
  require('../../../assets/images/characters/native-map/dog_walk_w_2.png'),
  require('../../../assets/images/characters/native-map/dog_walk_w_3.png'),
  require('../../../assets/images/characters/native-map/dog_walk_w_4.png'),
  require('../../../assets/images/characters/native-map/dog_walk_w_5.png'),
]);

const DOG_WALK_NW = createFrames('dog_walk_nw', [
  require('../../../assets/images/characters/native-map/dog_walk_nw_0.png'),
  require('../../../assets/images/characters/native-map/dog_walk_nw_1.png'),
  require('../../../assets/images/characters/native-map/dog_walk_nw_2.png'),
  require('../../../assets/images/characters/native-map/dog_walk_nw_3.png'),
  require('../../../assets/images/characters/native-map/dog_walk_nw_4.png'),
  require('../../../assets/images/characters/native-map/dog_walk_nw_5.png'),
]);

const RABBIT_WALK_N = createFrames('rabbit_walk_n', [
  require('../../../assets/images/characters/native-map/rabbit_walk_n_0.png'),
  require('../../../assets/images/characters/native-map/rabbit_walk_n_1.png'),
  require('../../../assets/images/characters/native-map/rabbit_walk_n_2.png'),
  require('../../../assets/images/characters/native-map/rabbit_walk_n_3.png'),
  require('../../../assets/images/characters/native-map/rabbit_walk_n_4.png'),
  require('../../../assets/images/characters/native-map/rabbit_walk_n_5.png'),
]);

const RABBIT_WALK_NE = createFrames('rabbit_walk_ne', [
  require('../../../assets/images/characters/native-map/rabbit_walk_ne_0.png'),
  require('../../../assets/images/characters/native-map/rabbit_walk_ne_1.png'),
  require('../../../assets/images/characters/native-map/rabbit_walk_ne_2.png'),
  require('../../../assets/images/characters/native-map/rabbit_walk_ne_3.png'),
  require('../../../assets/images/characters/native-map/rabbit_walk_ne_4.png'),
  require('../../../assets/images/characters/native-map/rabbit_walk_ne_5.png'),
]);

const RABBIT_WALK_E = createFrames('rabbit_walk_e', [
  require('../../../assets/images/characters/native-map/rabbit_walk_e_0.png'),
  require('../../../assets/images/characters/native-map/rabbit_walk_e_1.png'),
  require('../../../assets/images/characters/native-map/rabbit_walk_e_2.png'),
  require('../../../assets/images/characters/native-map/rabbit_walk_e_3.png'),
  require('../../../assets/images/characters/native-map/rabbit_walk_e_4.png'),
  require('../../../assets/images/characters/native-map/rabbit_walk_e_5.png'),
]);

const RABBIT_WALK_SE = createFrames('rabbit_walk_se', [
  require('../../../assets/images/characters/native-map/rabbit_walk_se_0.png'),
  require('../../../assets/images/characters/native-map/rabbit_walk_se_1.png'),
  require('../../../assets/images/characters/native-map/rabbit_walk_se_2.png'),
  require('../../../assets/images/characters/native-map/rabbit_walk_se_3.png'),
  require('../../../assets/images/characters/native-map/rabbit_walk_se_4.png'),
  require('../../../assets/images/characters/native-map/rabbit_walk_se_5.png'),
]);

const RABBIT_WALK_S = createFrames('rabbit_walk_s', [
  require('../../../assets/images/characters/native-map/rabbit_walk_s_0.png'),
  require('../../../assets/images/characters/native-map/rabbit_walk_s_1.png'),
  require('../../../assets/images/characters/native-map/rabbit_walk_s_2.png'),
  require('../../../assets/images/characters/native-map/rabbit_walk_s_3.png'),
  require('../../../assets/images/characters/native-map/rabbit_walk_s_4.png'),
  require('../../../assets/images/characters/native-map/rabbit_walk_s_5.png'),
]);

const RABBIT_WALK_SW = createFrames('rabbit_walk_sw', [
  require('../../../assets/images/characters/native-map/rabbit_walk_sw_0.png'),
  require('../../../assets/images/characters/native-map/rabbit_walk_sw_1.png'),
  require('../../../assets/images/characters/native-map/rabbit_walk_sw_2.png'),
  require('../../../assets/images/characters/native-map/rabbit_walk_sw_3.png'),
  require('../../../assets/images/characters/native-map/rabbit_walk_sw_4.png'),
  require('../../../assets/images/characters/native-map/rabbit_walk_sw_5.png'),
]);

const RABBIT_WALK_W = createFrames('rabbit_walk_w', [
  require('../../../assets/images/characters/native-map/rabbit_walk_w_0.png'),
  require('../../../assets/images/characters/native-map/rabbit_walk_w_1.png'),
  require('../../../assets/images/characters/native-map/rabbit_walk_w_2.png'),
  require('../../../assets/images/characters/native-map/rabbit_walk_w_3.png'),
  require('../../../assets/images/characters/native-map/rabbit_walk_w_4.png'),
  require('../../../assets/images/characters/native-map/rabbit_walk_w_5.png'),
]);

const RABBIT_WALK_NW = createFrames('rabbit_walk_nw', [
  require('../../../assets/images/characters/native-map/rabbit_walk_nw_0.png'),
  require('../../../assets/images/characters/native-map/rabbit_walk_nw_1.png'),
  require('../../../assets/images/characters/native-map/rabbit_walk_nw_2.png'),
  require('../../../assets/images/characters/native-map/rabbit_walk_nw_3.png'),
  require('../../../assets/images/characters/native-map/rabbit_walk_nw_4.png'),
  require('../../../assets/images/characters/native-map/rabbit_walk_nw_5.png'),
]);

const PLAIN_IDLE_S_1 = createFrames('plain_idle_s_1', [
  require('../../../assets/images/characters/native-map/plain_idle_s_1_0.png'),
  require('../../../assets/images/characters/native-map/plain_idle_s_1_1.png'),
  require('../../../assets/images/characters/native-map/plain_idle_s_1_2.png'),
  require('../../../assets/images/characters/native-map/plain_idle_s_1_3.png'),
  require('../../../assets/images/characters/native-map/plain_idle_s_1_4.png'),
  require('../../../assets/images/characters/native-map/plain_idle_s_1_5.png'),
]);

const PLAIN_IDLE_S_2 = createFrames('plain_idle_s_2', [
  require('../../../assets/images/characters/native-map/plain_idle_s_2_0.png'),
  require('../../../assets/images/characters/native-map/plain_idle_s_2_1.png'),
  require('../../../assets/images/characters/native-map/plain_idle_s_2_2.png'),
  require('../../../assets/images/characters/native-map/plain_idle_s_2_3.png'),
  require('../../../assets/images/characters/native-map/plain_idle_s_2_4.png'),
  require('../../../assets/images/characters/native-map/plain_idle_s_2_5.png'),
]);

const PLAIN_IDLE_S_3 = createFrames('plain_idle_s_3', [
  require('../../../assets/images/characters/native-map/plain_idle_s_3_0.png'),
  require('../../../assets/images/characters/native-map/plain_idle_s_3_1.png'),
  require('../../../assets/images/characters/native-map/plain_idle_s_3_2.png'),
  require('../../../assets/images/characters/native-map/plain_idle_s_3_3.png'),
  require('../../../assets/images/characters/native-map/plain_idle_s_3_4.png'),
  require('../../../assets/images/characters/native-map/plain_idle_s_3_5.png'),
]);

const DOG_IDLE_S_1 = createFrames('dog_idle_s_1', [
  require('../../../assets/images/characters/native-map/dog_idle_s_1_0.png'),
  require('../../../assets/images/characters/native-map/dog_idle_s_1_1.png'),
  require('../../../assets/images/characters/native-map/dog_idle_s_1_2.png'),
  require('../../../assets/images/characters/native-map/dog_idle_s_1_3.png'),
  require('../../../assets/images/characters/native-map/dog_idle_s_1_4.png'),
  require('../../../assets/images/characters/native-map/dog_idle_s_1_5.png'),
]);

const DOG_IDLE_S_2 = createFrames('dog_idle_s_2', [
  require('../../../assets/images/characters/native-map/dog_idle_s_2_0.png'),
  require('../../../assets/images/characters/native-map/dog_idle_s_2_1.png'),
  require('../../../assets/images/characters/native-map/dog_idle_s_2_2.png'),
  require('../../../assets/images/characters/native-map/dog_idle_s_2_3.png'),
  require('../../../assets/images/characters/native-map/dog_idle_s_2_4.png'),
  require('../../../assets/images/characters/native-map/dog_idle_s_2_5.png'),
]);

const DOG_IDLE_S_3 = createFrames('dog_idle_s_3', [
  require('../../../assets/images/characters/native-map/dog_idle_s_3_0.png'),
  require('../../../assets/images/characters/native-map/dog_idle_s_3_1.png'),
  require('../../../assets/images/characters/native-map/dog_idle_s_3_2.png'),
  require('../../../assets/images/characters/native-map/dog_idle_s_3_3.png'),
  require('../../../assets/images/characters/native-map/dog_idle_s_3_4.png'),
  require('../../../assets/images/characters/native-map/dog_idle_s_3_5.png'),
]);

const RABBIT_IDLE_S_1 = createFrames('rabbit_idle_s_1', [
  require('../../../assets/images/characters/native-map/rabbit_idle_s_1_0.png'),
  require('../../../assets/images/characters/native-map/rabbit_idle_s_1_1.png'),
  require('../../../assets/images/characters/native-map/rabbit_idle_s_1_2.png'),
  require('../../../assets/images/characters/native-map/rabbit_idle_s_1_3.png'),
  require('../../../assets/images/characters/native-map/rabbit_idle_s_1_4.png'),
  require('../../../assets/images/characters/native-map/rabbit_idle_s_1_5.png'),
]);

const RABBIT_IDLE_S_2 = createFrames('rabbit_idle_s_2', [
  require('../../../assets/images/characters/native-map/rabbit_idle_s_2_0.png'),
  require('../../../assets/images/characters/native-map/rabbit_idle_s_2_1.png'),
  require('../../../assets/images/characters/native-map/rabbit_idle_s_2_2.png'),
  require('../../../assets/images/characters/native-map/rabbit_idle_s_2_3.png'),
  require('../../../assets/images/characters/native-map/rabbit_idle_s_2_4.png'),
  require('../../../assets/images/characters/native-map/rabbit_idle_s_2_5.png'),
]);

const RABBIT_IDLE_S_3 = createFrames('rabbit_idle_s_3', [
  require('../../../assets/images/characters/native-map/rabbit_idle_s_3_0.png'),
  require('../../../assets/images/characters/native-map/rabbit_idle_s_3_1.png'),
  require('../../../assets/images/characters/native-map/rabbit_idle_s_3_2.png'),
  require('../../../assets/images/characters/native-map/rabbit_idle_s_3_3.png'),
  require('../../../assets/images/characters/native-map/rabbit_idle_s_3_4.png'),
  require('../../../assets/images/characters/native-map/rabbit_idle_s_3_5.png'),
]);

const PLAIN_WALK_BY_DIRECTION: Record<Direction, readonly NativeMapSpriteFrame[]> = {
  N: PLAIN_WALK_N,
  NE: PLAIN_WALK_NE,
  E: PLAIN_WALK_E,
  SE: PLAIN_WALK_SE,
  S: PLAIN_WALK_S,
  SW: PLAIN_WALK_SW,
  W: PLAIN_WALK_W,
  NW: PLAIN_WALK_NW,
};

const DOG_WALK_BY_DIRECTION: Record<Direction, readonly NativeMapSpriteFrame[]> = {
  N: DOG_WALK_N,
  NE: DOG_WALK_NE,
  E: DOG_WALK_E,
  SE: DOG_WALK_SE,
  S: DOG_WALK_S,
  SW: DOG_WALK_SW,
  W: DOG_WALK_W,
  NW: DOG_WALK_NW,
};

const RABBIT_WALK_BY_DIRECTION: Record<Direction, readonly NativeMapSpriteFrame[]> = {
  N: RABBIT_WALK_N,
  NE: RABBIT_WALK_NE,
  E: RABBIT_WALK_E,
  SE: RABBIT_WALK_SE,
  S: RABBIT_WALK_S,
  SW: RABBIT_WALK_SW,
  W: RABBIT_WALK_W,
  NW: RABBIT_WALK_NW,
};

const PLAIN_IDLE_VARIANTS: readonly (readonly NativeMapSpriteFrame[])[] = [
  PLAIN_IDLE_S_1,
  PLAIN_IDLE_S_2,
  PLAIN_IDLE_S_3,
];
const DOG_IDLE_VARIANTS: readonly (readonly NativeMapSpriteFrame[])[] = [
  DOG_IDLE_S_1,
  DOG_IDLE_S_2,
  DOG_IDLE_S_3,
];
const RABBIT_IDLE_VARIANTS: readonly (readonly NativeMapSpriteFrame[])[] = [
  RABBIT_IDLE_S_1,
  RABBIT_IDLE_S_2,
  RABBIT_IDLE_S_3,
];

const WALK_BY_CHARACTER: Record<CharacterId, Record<Direction, readonly NativeMapSpriteFrame[]>> = {
  PALBANG: PLAIN_WALK_BY_DIRECTION,
  PALBANG_DOG: DOG_WALK_BY_DIRECTION,
  PALBANG_RABBIT: RABBIT_WALK_BY_DIRECTION,
};

const IDLE_BY_CHARACTER: Record<CharacterId, readonly (readonly NativeMapSpriteFrame[])[]> = {
  PALBANG: PLAIN_IDLE_VARIANTS,
  PALBANG_DOG: DOG_IDLE_VARIANTS,
  PALBANG_RABBIT: RABBIT_IDLE_VARIANTS,
};

/**
 * 지도 위 SymbolLayer 용 스프라이트 프레임을 고릅니다.
 *
 * 대기(idle)는 3가지 변형 중 useIdleVariant 가 고른 idleVariant(1~3)를 그대로
 * 인덱스로 씁니다. 원본이 전부 정면(S)만 있어 방향은 무시합니다(걷기와 달리
 * 기존부터 그래왔던 동작).
 */
export function resolveNativeMapSprite(
  characterId: CharacterId,
  motion: MotionState,
  direction: Direction,
  idleVariant: IdleVariant = 1,
): NativeMapSprite {
  if (motion === 'idle') {
    const variants = IDLE_BY_CHARACTER[characterId];
    const frames = variants[idleVariant - 1] ?? variants[0]!;
    return { frames, fps: IDLE_FPS };
  }

  return { frames: WALK_BY_CHARACTER[characterId][direction], fps: WALK_FPS };
}
