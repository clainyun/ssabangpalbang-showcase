import type { ImageSourcePropType } from 'react-native';

import type { Direction } from '@/store/characterStore';

/**
 * 캐릭터 스프라이트 시트 설정. 스펙 원본은 docs/CHARACTER_SPRITE_SPEC.md 입니다.
 *
 * 이 파일은 "무엇을 그릴지"만 담습니다. "어떻게 그릴지"(클리핑·transform)는
 * CharacterSprite.tsx 한 겹에만 있습니다. §7.1 이 요구한 분리입니다 — 나중에
 * Skia(B안)로 갈아타야 하면 렌더러만 교체하면 됩니다.
 */

/**
 * processed 시트의 프레임 한 칸 크기(px). 전처리(scripts/process_palbang_sprites.py
 * 등)가 모든 시트를 이 크기의 정사각 셀·가로 스트립으로 통일합니다. 실제 렌더링은 표시
 * 크기(dp)와 frameCount 비율로만 계산하므로 이 값이 바뀌어도 렌더러는 그대로입니다.
 */
export const SPRITE_FRAME_SIZE = 256;

/**
 * 전처리가 발밑에 남긴 여백 비율(BOTTOM_PAD 16px ÷ CELL 256px). 캐릭터 화면
 * 앵커를 GPS 좌표에 맞출 때, 표시 박스 하단이 아니라 실제 발끝이 좌표에 닿도록
 * 이 비율만큼 보정해야 합니다([sessionId].tsx 참고).
 */
export const FOOT_BOTTOM_INSET_RATIO = 16 / 256;

/** 백엔드 ALLOWED_CHARACTER_IDS 와 1:1 (MemberService.java:46) */
export type CharacterId = 'PALBANG' | 'PALBANG_RABBIT' | 'PALBANG_DOG';

export type MotionState = 'walk' | 'idle';

/** 대기 모션 3종(idle1~3) 중 어느 것을 재생할지. useIdleVariant 가 무작위로 고릅니다. */
export type IdleVariant = 1 | 2 | 3;

/**
 * 실제로 아트가 존재하는 제작 방향. 3D 렌더가 8방향 전부 실물이라 Direction 과
 * 1:1 입니다. 그래도 타입을 분리해 두는 이유: 나중에 어느 캐릭터가 일부 방향만
 * 먼저 들어오는 상황이 다시 생겨도(과거 dog/rabbit_2 가 그랬듯) findNearestSheet
 * 하나만으로 "가장 가까운 방향으로 대체"가 그대로 동작합니다.
 */
export type ProducedDirection = Direction;

/**
 * 제작 방향의 방위각. 시트가 일부만 있을 때 "가장 가까운 방향"을 고르는 데 씁니다.
 * N(뒤통수) 0° → 시계방향 → S(정면) 180°.
 */
const PRODUCED_ANGLE: Record<ProducedDirection, number> = {
  N: 0,
  NE: 45,
  E: 90,
  SE: 135,
  S: 180,
  SW: 225,
  W: 270,
  NW: 315,
};

const PRODUCED_DIRECTIONS = Object.keys(PRODUCED_ANGLE) as ProducedDirection[];

export const DEFAULT_CHARACTER_ID: CharacterId = 'PALBANG';

const CHARACTER_IDS: readonly CharacterId[] = ['PALBANG', 'PALBANG_RABBIT', 'PALBANG_DOG'];

/** 서버가 모르는 값을 주거나 아직 못 받았으면 기본 캐릭터로 떨어집니다. */
export function toCharacterId(raw: string | null | undefined): CharacterId {
  return CHARACTER_IDS.find((id) => id === raw) ?? DEFAULT_CHARACTER_ID;
}

/**
 * 모션별 재생 속도(fps). frameCount 는 시트마다 다르므로 여기 두지 않고 시트 엔트리가
 * 직접 들고 있습니다(아래 SheetEntry).
 *
 * walk 8fps = 125ms/프레임 (스펙 §2 권장 100~150ms 범위).
 * idle 2.5fps = 400ms/프레임 (스펙 §2 권장 350~700ms 범위). 눈 깜빡임이 너무 빠르게
 * 반복되지 않게 느리게 둡니다.
 */
const MOTION_FPS: Record<MotionState, number> = {
  walk: 8,
  idle: 2.5,
};

/** 8방향 전부 실제 시트가 있으므로 좌우 반전이 필요 없습니다(항상 false). */
export const DIRECTION_VARIANT: Record<
  Direction,
  { produced: ProducedDirection; mirrored: boolean }
> = {
  N: { produced: 'N', mirrored: false },
  NE: { produced: 'NE', mirrored: false },
  E: { produced: 'E', mirrored: false },
  SE: { produced: 'SE', mirrored: false },
  S: { produced: 'S', mirrored: false },
  SW: { produced: 'SW', mirrored: false },
  W: { produced: 'W', mirrored: false },
  NW: { produced: 'NW', mirrored: false },
};

/** 한 방향 시트: 이미지 + 그 시트의 실제 온전한 프레임 수. */
interface SheetEntry {
  source: ImageSourcePropType;
  frameCount: number;
}

type SheetKey = `${CharacterId}:${ProducedDirection}`;

/**
 * ⬇️ 걷기 시트. scripts/process_palbang_sprites.py 가 palbang_plain(PALBANG),
 * palbang_dog(PALBANG_DOG), palbang_rabbit(PALBANG_RABBIT) 원본에서 8방향 전부
 * 뽑아낸 정규화 스트립입니다.
 *
 * Metro 는 require 인자를 정적 문자열 리터럴로만 해석합니다. 경로를 변수·템플릿
 * 으로 합치면 빌드 타임에 에셋을 못 찾으니 전체 경로를 그대로 적습니다.
 */
const WALK_SHEETS: Partial<Record<SheetKey, SheetEntry>> = {
  'PALBANG:N': {
    source: require('../../../assets/images/characters/palbang_plain/processed/plain_walk_n_processed.png'),
    frameCount: 6,
  },
  'PALBANG:NE': {
    source: require('../../../assets/images/characters/palbang_plain/processed/plain_walk_ne_processed.png'),
    frameCount: 6,
  },
  'PALBANG:E': {
    source: require('../../../assets/images/characters/palbang_plain/processed/plain_walk_e_processed.png'),
    frameCount: 6,
  },
  'PALBANG:SE': {
    source: require('../../../assets/images/characters/palbang_plain/processed/plain_walk_se_processed.png'),
    frameCount: 6,
  },
  'PALBANG:S': {
    source: require('../../../assets/images/characters/palbang_plain/processed/plain_walk_s_processed.png'),
    frameCount: 6,
  },
  'PALBANG:SW': {
    source: require('../../../assets/images/characters/palbang_plain/processed/plain_walk_sw_processed.png'),
    frameCount: 6,
  },
  'PALBANG:W': {
    source: require('../../../assets/images/characters/palbang_plain/processed/plain_walk_w_processed.png'),
    frameCount: 6,
  },
  'PALBANG:NW': {
    source: require('../../../assets/images/characters/palbang_plain/processed/plain_walk_nw_processed.png'),
    frameCount: 6,
  },

  'PALBANG_DOG:N': {
    source: require('../../../assets/images/characters/palbang_dog/processed/dog_walk_n_processed.png'),
    frameCount: 6,
  },
  'PALBANG_DOG:NE': {
    source: require('../../../assets/images/characters/palbang_dog/processed/dog_walk_ne_processed.png'),
    frameCount: 6,
  },
  'PALBANG_DOG:E': {
    source: require('../../../assets/images/characters/palbang_dog/processed/dog_walk_e_processed.png'),
    frameCount: 6,
  },
  'PALBANG_DOG:SE': {
    source: require('../../../assets/images/characters/palbang_dog/processed/dog_walk_se_processed.png'),
    frameCount: 6,
  },
  'PALBANG_DOG:S': {
    source: require('../../../assets/images/characters/palbang_dog/processed/dog_walk_s_processed.png'),
    frameCount: 6,
  },
  'PALBANG_DOG:SW': {
    source: require('../../../assets/images/characters/palbang_dog/processed/dog_walk_sw_processed.png'),
    frameCount: 6,
  },
  'PALBANG_DOG:W': {
    source: require('../../../assets/images/characters/palbang_dog/processed/dog_walk_w_processed.png'),
    frameCount: 6,
  },
  'PALBANG_DOG:NW': {
    source: require('../../../assets/images/characters/palbang_dog/processed/dog_walk_nw_processed.png'),
    frameCount: 6,
  },

  'PALBANG_RABBIT:N': {
    source: require('../../../assets/images/characters/palbang_rabbit/processed/rabbit_walk_n_processed.png'),
    frameCount: 6,
  },
  'PALBANG_RABBIT:NE': {
    source: require('../../../assets/images/characters/palbang_rabbit/processed/rabbit_walk_ne_processed.png'),
    frameCount: 6,
  },
  'PALBANG_RABBIT:E': {
    source: require('../../../assets/images/characters/palbang_rabbit/processed/rabbit_walk_e_processed.png'),
    frameCount: 6,
  },
  'PALBANG_RABBIT:SE': {
    source: require('../../../assets/images/characters/palbang_rabbit/processed/rabbit_walk_se_processed.png'),
    frameCount: 6,
  },
  'PALBANG_RABBIT:S': {
    source: require('../../../assets/images/characters/palbang_rabbit/processed/rabbit_walk_s_processed.png'),
    frameCount: 6,
  },
  'PALBANG_RABBIT:SW': {
    source: require('../../../assets/images/characters/palbang_rabbit/processed/rabbit_walk_sw_processed.png'),
    frameCount: 6,
  },
  'PALBANG_RABBIT:W': {
    source: require('../../../assets/images/characters/palbang_rabbit/processed/rabbit_walk_w_processed.png'),
    frameCount: 6,
  },
  'PALBANG_RABBIT:NW': {
    source: require('../../../assets/images/characters/palbang_rabbit/processed/rabbit_walk_nw_processed.png'),
    frameCount: 6,
  },
};

/**
 * 대기 모션 3종. 원본이 전부 정면(S)만 있어서 방향과 무관하게 이 중 하나를
 * 무작위로 고릅니다(useIdleVariant). 인덱스 0 = idle1, 1 = idle2, 2 = idle3.
 */
const IDLE_SHEETS: Record<CharacterId, readonly SheetEntry[]> = {
  PALBANG: [
    {
      source: require('../../../assets/images/characters/palbang_plain/processed/plain_idle_s_1_processed.png'),
      frameCount: 6,
    },
    {
      source: require('../../../assets/images/characters/palbang_plain/processed/plain_idle_s_2_processed.png'),
      frameCount: 6,
    },
    {
      source: require('../../../assets/images/characters/palbang_plain/processed/plain_idle_s_3_processed.png'),
      frameCount: 6,
    },
  ],
  PALBANG_DOG: [
    {
      source: require('../../../assets/images/characters/palbang_dog/processed/dog_idle_s_1_processed.png'),
      frameCount: 6,
    },
    {
      source: require('../../../assets/images/characters/palbang_dog/processed/dog_idle_s_2_processed.png'),
      frameCount: 6,
    },
    {
      source: require('../../../assets/images/characters/palbang_dog/processed/dog_idle_s_3_processed.png'),
      frameCount: 6,
    },
  ],
  PALBANG_RABBIT: [
    {
      source: require('../../../assets/images/characters/palbang_rabbit/processed/rabbit_idle_s_1_processed.png'),
      frameCount: 6,
    },
    {
      source: require('../../../assets/images/characters/palbang_rabbit/processed/rabbit_idle_s_2_processed.png'),
      frameCount: 6,
    },
    {
      source: require('../../../assets/images/characters/palbang_rabbit/processed/rabbit_idle_s_3_processed.png'),
      frameCount: 6,
    },
  ],
};

/**
 * 시트가 아직 없을 때 쓰는 1프레임 대체 이미지. 캐릭터별로 따로 둡니다.
 *
 * 지금은 8방향+대기 3종이 전부 채워져 있어 정상 경로에서는 쓰이지 않고, 에셋
 * 로딩 실패 등 진짜 예외 상황의 안전망으로만 남습니다.
 */
const PLACEHOLDER_SOURCES: Record<CharacterId, ImageSourcePropType> = {
  PALBANG: require('../../../assets/images/characters/palbang.png'),
  PALBANG_RABBIT: require('../../../assets/images/characters/palbang_rabbit.png'),
  PALBANG_DOG: require('../../../assets/images/characters/palbang_dog.png'),
};

export interface ResolvedSprite {
  source: ImageSourcePropType;
  frameCount: number;
  fps: number;
  mirrored: boolean;
  /** true 면 실제 시트가 아니라 1프레임 대체 이미지입니다. */
  isPlaceholder: boolean;
}

/**
 * 요청한 방향의 시트가 없으면 각도상 가장 가까운 등록된 방향을 찾습니다.
 *
 * 왜 정면(S) 고정 대체가 아닌가: 아파트 단지는 철근 구조물 때문에 지자기 간섭이
 * 커서 기기 나침반이 ±15~30° 씩 흔들립니다. FollowWithHeading 은 그 값으로 지도를
 * 회전시키므로 상대 방향이 N 에 고정되지 않고 NW/N/NE 사이를 오갑니다. 이때 정면으로
 * 떨어뜨리면 걷는 중에 캐릭터가 갑자기 카메라를 쳐다봅니다. 가장 가까운 방향으로
 * 붙이면 N 만 있어도 NW/NE 요청이 N 으로 흡수됩니다.
 */
function findNearestWalkSheet(
  characterId: CharacterId,
  produced: ProducedDirection,
): SheetEntry | null {
  const targetAngle = PRODUCED_ANGLE[produced];
  let bestEntry: SheetEntry | null = null;
  let bestDelta = Number.POSITIVE_INFINITY;

  for (const candidate of PRODUCED_DIRECTIONS) {
    const entry = WALK_SHEETS[`${characterId}:${candidate}`];
    if (entry === undefined) continue;

    const delta = Math.abs(PRODUCED_ANGLE[candidate] - targetAngle);
    if (delta < bestDelta) {
      bestDelta = delta;
      bestEntry = entry;
    }
  }

  return bestEntry;
}

export function resolveSprite(
  characterId: CharacterId,
  motion: MotionState,
  direction: Direction,
  idleVariant: IdleVariant = 1,
): ResolvedSprite {
  if (motion === 'idle') {
    const variants = IDLE_SHEETS[characterId];
    const entry = variants[idleVariant - 1] ?? variants[0]!;
    return {
      source: entry.source,
      frameCount: entry.frameCount,
      fps: MOTION_FPS.idle,
      mirrored: false,
      isPlaceholder: false,
    };
  }

  const variant = DIRECTION_VARIANT[direction];
  const entry = findNearestWalkSheet(characterId, variant.produced);

  if (entry === null) {
    return {
      source: PLACEHOLDER_SOURCES[characterId],
      frameCount: 1,
      fps: 1,
      mirrored: false,
      isPlaceholder: true,
    };
  }

  return {
    source: entry.source,
    frameCount: entry.frameCount,
    fps: MOTION_FPS.walk,
    mirrored: variant.mirrored,
    isPlaceholder: false,
  };
}
