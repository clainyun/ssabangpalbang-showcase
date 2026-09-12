/* eslint-disable react-hooks/immutability -- 물리·전환 워클릿에서 shared value 재할당은 이 파일의 정상 패턴 */
import { useCallback, useEffect, useRef, useState } from 'react';
import { Image, StyleSheet, useWindowDimensions, View } from 'react-native';
import { Gesture, GestureDetector } from 'react-native-gesture-handler';
import Animated, {
  Easing,
  type SharedValue,
  useAnimatedReaction,
  useAnimatedStyle,
  useFrameCallback,
  useSharedValue,
  withDelay,
  withRepeat,
  withSequence,
  withSpring,
  withTiming,
} from 'react-native-reanimated';

import type { HomeWeather } from '@/features/home/types';
import { useShakeGesture } from '@/features/home/useShakeGesture';
import { resolveWeatherIcon } from '@/features/home/weatherIcons';

interface SlotSpec {
  /** 시작 앵커(화면 비율 0~1). 실제 px는 useWindowDimensions로 계산. */
  topRatio: number;
  leftRatio: number;
  size: number;
  /** 회전 wobble 한 바퀴 주기(ms). */
  periodMs: number;
  rotateBase: number;
  rotateRange: number;
}

// Metro require.context로 assets/apart 폴더의 모든 png를 빌드타임에 자동 수집합니다
// (expo-router가 require.context를 활성화해둠). 파일을 추가/삭제하면 코드 수정 없이
// 리로드만으로 반영돼요 — 흔들 때마다 이 풀에서 랜덤 5개를 뽑습니다.
const apartContext = require.context('../../../assets/apart', false, /\.png$/);
/** assets/apart 폴더의 모든 아파트 아이콘 풀(파일 추가 시 자동 확장). */
const POOL: number[] = apartContext.keys().map((key) => apartContext(key) as number);

/** 화면에 동시에 뜨는 5칸의 위치·물리 스펙(이미지는 slot마다 랜덤으로 바뀜). */
const SLOTS: SlotSpec[] = [
  { topRatio: 0.04, leftRatio: 0.68, size: 190, periodMs: 24000, rotateBase: 12, rotateRange: 10 },
  { topRatio: 0.1, leftRatio: 0.06, size: 190, periodMs: 31000, rotateBase: -8, rotateRange: 12 },
  { topRatio: 0.44, leftRatio: 0.12, size: 190, periodMs: 36000, rotateBase: 10, rotateRange: 14 },
  { topRatio: 0.55, leftRatio: 0.72, size: 190, periodMs: 22000, rotateBase: -12, rotateRange: 8 },
  { topRatio: 0.64, leftRatio: 0.32, size: 190, periodMs: 33000, rotateBase: 6, rotateRange: 11 },
];

/** 5개 슬롯 중 하나는 현재 날씨 오브젝트로 대체(나머지 4개는 랜덤 아파트). */
const WEATHER_SLOT_INDEX = 0;

/** 테두리에서 튕길 때 화면 밖으로 살짝 걸치게 두는 여유(px). */
const EDGE_BLEED = 46;
/** 평상시 유지되는 최소 표류 속도(px/s) — 이 밑으로 느려지면 다시 끌어올려 계속 직진. */
const BASE_SPEED = 34;
/** 던졌을 때 낼 수 있는 속도 상한(px/s). */
const MAX_SPEED = 950;
/** 1초당 남는 속도 비율 — 던진 뒤 초과 속도를 서서히 감쇠. */
const FRICTION_PER_SEC = 0.5;
/** 벽에 부딪힐 때 남기는 속도 비율(탄성). */
const WALL_RESTITUTION = 0.9;
/** 드래그를 놓을 때 손가락 속도를 이 비율만큼 반영(관성). */
const DRAG_RELEASE_WEIGHT = 0.7;

/** 아이콘 간 충돌 반경 = size × 이 비율. 이미지 투명 여백 때문에 시각 크기보다 살짝 작게. */
const COLLISION_RADIUS_RATIO = 0.36;
/** 물리 바디 개수(=슬롯 수). 워클릿에서 쓰려고 숫자 상수로 보관. */
const BODY_COUNT = SLOTS.length;

// 흔들기 전환(슈루룩) 타이밍.
const EXIT_STAGGER_MS = 45;
const EXIT_DURATION_MS = 280;
const ENTER_STAGGER_MS = 45;
const ENTER_DURATION_MS = 340;
/** 흔든 뒤 이미지 교체 + 등장 시작 시점(퇴장 애니메이션이 대체로 끝난 뒤). */
const SWAP_DELAY_MS = 470;

/** 풀에서 랜덤 5개를 뽑음(Fisher-Yates). 흔들기 이벤트 핸들러에서만 호출(렌더 순수성 유지). */
function pickFive(pool: number[]): number[] {
  const copy = [...pool];
  for (let i = copy.length - 1; i > 0; i -= 1) {
    const j = Math.floor(Math.random() * (i + 1));
    const a = copy[i]!;
    copy[i] = copy[j]!;
    copy[j] = a;
  }
  return copy.slice(0, 5);
}

function FloatingApartIcon({
  spec,
  source,
  index,
  exit,
  enter,
  centerXs,
  centerYs,
  velXs,
  velYs,
  actives,
}: {
  spec: SlotSpec;
  source: number;
  index: number;
  /** 부모에서 흔들면 증가 → 퇴장(슈루룩 아웃) 트리거. */
  exit: SharedValue<number>;
  /** 이미지 교체 후 증가 → 등장(슈루룩 인) 트리거. */
  enter: SharedValue<number>;
  /** 모든 바디의 절대 중심 X/Y, 속도 X/Y, 활성여부(1/0). 인덱스로 접근해 충돌 계산. */
  centerXs: SharedValue<number>[];
  centerYs: SharedValue<number>[];
  velXs: SharedValue<number>[];
  velYs: SharedValue<number>[];
  actives: SharedValue<number>[];
}) {
  const { width, height } = useWindowDimensions();

  const anchorLeft = width * spec.leftRatio;
  const anchorTop = height * spec.topRatio;
  const minX = Math.min(-anchorLeft, width - spec.size - anchorLeft) - EDGE_BLEED;
  const maxX = Math.max(-anchorLeft, width - spec.size - anchorLeft) + EDGE_BLEED;
  const minY = Math.min(-anchorTop, height - spec.size - anchorTop) - EDGE_BLEED;
  const maxY = Math.max(-anchorTop, height - spec.size - anchorTop) + EDGE_BLEED;
  const startX = Math.min(Math.max(0, minX), maxX);
  const startY = Math.min(Math.max(0, minY), maxY);

  const x = useSharedValue(startX);
  const y = useSharedValue(startY);
  const vx = useSharedValue(0);
  const vy = useSharedValue(0);
  const rotate = useSharedValue(spec.rotateBase);
  const scale = useSharedValue(1);
  const opacity = useSharedValue(1);
  const isDragging = useSharedValue(false);
  // 흔들기 전환 중에는 물리 루프가 위치를 건드리지 않도록 막는 플래그.
  const transitioning = useSharedValue(false);
  const dragStartX = useSharedValue(0);
  const dragStartY = useSharedValue(0);

  // 매 프레임 물리: 마찰 → 최소속도 보정(한 방향 직진 유지) → 이동 → 벽 반사 →
  // 공유 레지스트리에 자기 상태 게시 → 다른 아이콘과 원-원 충돌(분리 + 탄성 반사).
  useFrameCallback((frameInfo) => {
    const dt = Math.min((frameInfo.timeSincePreviousFrame ?? 16) / 1000, 0.05);
    // 드래그 중인 바디는 손가락이 위치를 정하므로 물리를 멈추되, 다른 바디의 장애물로는 남긴다.
    // 전환(슈루룩) 중인 바디는 화면 밖으로 빠지는 중이라 충돌에서 완전히 제외한다.
    const movable = !isDragging.value && !transitioning.value;

    if (movable) {
      const friction = Math.pow(FRICTION_PER_SEC, dt);
      vx.value *= friction;
      vy.value *= friction;

      let speed = Math.sqrt(vx.value * vx.value + vy.value * vy.value);
      if (speed > MAX_SPEED) {
        const k = MAX_SPEED / speed;
        vx.value *= k;
        vy.value *= k;
        speed = MAX_SPEED;
      }
      if (speed < BASE_SPEED && speed > 0.0001) {
        const k = BASE_SPEED / speed;
        vx.value *= k;
        vy.value *= k;
      }

      let nextX = x.value + vx.value * dt;
      let nextY = y.value + vy.value * dt;

      if (nextX < minX) {
        nextX = minX;
        vx.value = Math.abs(vx.value) * WALL_RESTITUTION;
      } else if (nextX > maxX) {
        nextX = maxX;
        vx.value = -Math.abs(vx.value) * WALL_RESTITUTION;
      }
      if (nextY < minY) {
        nextY = minY;
        vy.value = Math.abs(vy.value) * WALL_RESTITUTION;
      } else if (nextY > maxY) {
        nextY = maxY;
        vy.value = -Math.abs(vy.value) * WALL_RESTITUTION;
      }

      x.value = nextX;
      y.value = nextY;
    }

    // 절대 좌표 중심(각 바디의 앵커는 상수라 x/y 오프셋 = 절대 이동량과 동일).
    const cx = anchorLeft + x.value + spec.size / 2;
    const cy = anchorTop + y.value + spec.size / 2;
    // 자기 상태를 스칼라 shared value에 게시(전환 중이면 active=0으로 충돌 대상에서 뺌).
    centerXs[index]!.value = cx;
    centerYs[index]!.value = cy;
    velXs[index]!.value = vx.value;
    velYs[index]!.value = vy.value;
    actives[index]!.value = transitioning.value ? 0 : 1;

    // 전환 중이거나 드래그 중인 바디 자신은 밀리지 않는다(드래그는 남을 밀기만).
    if (!movable) {
      return;
    }

    const minDist = spec.size * COLLISION_RADIUS_RATIO * 2; // 슬롯 크기가 동일해 반경 2배
    for (let j = 0; j < BODY_COUNT; j += 1) {
      if (j === index || actives[j]!.value === 0) {
        continue; // 자기 자신·비활성(전환 중) 바디는 무시
      }
      const dx = centerXs[j]!.value - cx;
      const dy = centerYs[j]!.value - cy;
      const distSq = dx * dx + dy * dy;
      if (distSq >= minDist * minDist || distSq < 0.0001) {
        continue;
      }
      const dist = Math.sqrt(distSq);
      const nx = dx / dist;
      const ny = dy / dist;
      const overlap = minDist - dist;
      // 겹친 만큼 절반을 상대 반대쪽으로 밀어 분리(상대는 자기 콜백에서 나머지 절반).
      x.value -= nx * overlap * 0.5;
      y.value -= ny * overlap * 0.5;
      // 등질량 탄성: 접근 중일 때만 법선 속도 성분을 상대 것과 교환(반발계수 적용).
      const vin = vx.value * nx + vy.value * ny;
      const vjn = velXs[j]!.value * nx + velYs[j]!.value * ny;
      if (vin - vjn > 0) {
        const delta = (vjn - vin) * WALL_RESTITUTION;
        vx.value += delta * nx;
        vy.value += delta * ny;
      }
    }
  }, true);

  // 흔들면: 슬롯마다 살짝 시차를 두고 오른쪽으로 슈루룩 미끄러지며 사라짐(물리 정지).
  useAnimatedReaction(
    () => exit.value,
    (cur, prev) => {
      if (prev === null || cur === prev) {
        return;
      }
      transitioning.value = true;
      const delay = index * EXIT_STAGGER_MS;
      x.value = withDelay(
        delay,
        withTiming(maxX + spec.size + 140, { duration: EXIT_DURATION_MS, easing: Easing.in(Easing.cubic) }),
      );
      opacity.value = withDelay(delay, withTiming(0, { duration: EXIT_DURATION_MS }));
    },
  );

  // 이미지 교체 후: 왼쪽 밖에서 제자리로 슈루룩 등장 → 끝나면 새 방향 부여하고 물리 재개.
  useAnimatedReaction(
    () => enter.value,
    (cur, prev) => {
      if (prev === null || cur === prev) {
        return;
      }
      const delay = index * ENTER_STAGGER_MS;
      x.value = minX - spec.size - 140;
      y.value = startY;
      opacity.value = 0;
      opacity.value = withDelay(delay, withTiming(1, { duration: ENTER_DURATION_MS }));
      x.value = withDelay(
        delay,
        withTiming(startX, { duration: ENTER_DURATION_MS, easing: Easing.out(Easing.cubic) }, (finished) => {
          'worklet';
          if (finished) {
            const angle = (((index * 73 + 20) % 360) * Math.PI) / 180;
            const sp = BASE_SPEED * 1.3;
            vx.value = Math.cos(angle) * sp;
            vy.value = Math.sin(angle) * sp;
            transitioning.value = false;
          }
        }),
      );
    },
  );

  useEffect(() => {
    // 첫 등장: 슬롯마다 다른 방향으로 BASE_SPEED 살짝 위 속도로 출발.
    const angle = Math.random() * Math.PI * 2;
    const sp = BASE_SPEED * (1.1 + Math.random() * 0.6);
    vx.value = Math.cos(angle) * sp;
    vy.value = Math.sin(angle) * sp;

    const ease = Easing.inOut(Easing.sin);
    const segment = spec.periodMs / 4;
    rotate.value = withRepeat(
      withSequence(
        withTiming(spec.rotateBase + spec.rotateRange, { duration: segment * 1.6, easing: ease }),
        withTiming(spec.rotateBase - spec.rotateRange, { duration: segment * 1.6, easing: ease }),
        withTiming(spec.rotateBase, { duration: segment * 0.8, easing: ease }),
      ),
      -1,
    );
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, []);

  // "띠용띠용": 짧게 커졌다가 withSpring이 튕기며 원래 크기로 정착.
  const tapGesture = Gesture.Tap().onEnd(() => {
    scale.value = withSequence(
      withTiming(1.18, { duration: 90, easing: Easing.out(Easing.quad) }),
      withSpring(1, { damping: 6, stiffness: 170, mass: 0.6 }),
    );
  });

  // 잡아서 끌면 손가락을 따라오고, 놓으면 관성으로 날아가 벽에 튕긴 뒤 표류로 정착.
  const panGesture = Gesture.Pan()
    .onStart(() => {
      isDragging.value = true;
      dragStartX.value = x.value;
      dragStartY.value = y.value;
    })
    .onUpdate((event) => {
      x.value = Math.min(Math.max(dragStartX.value + event.translationX, minX), maxX);
      y.value = Math.min(Math.max(dragStartY.value + event.translationY, minY), maxY);
    })
    .onEnd((event) => {
      vx.value = event.velocityX * DRAG_RELEASE_WEIGHT;
      vy.value = event.velocityY * DRAG_RELEASE_WEIGHT;
      isDragging.value = false;
    });

  const composedGesture = Gesture.Race(panGesture, tapGesture);

  const animatedStyle = useAnimatedStyle(() => ({
    opacity: opacity.value,
    transform: [
      { translateX: x.value },
      { translateY: y.value },
      { rotate: `${rotate.value}deg` },
      { scale: scale.value },
    ],
  }));

  return (
    <Animated.View
      style={[
        styles.icon,
        {
          top: height * spec.topRatio,
          left: width * spec.leftRatio,
          width: spec.size,
          height: spec.size,
        },
        animatedStyle,
      ]}
    >
      <GestureDetector gesture={composedGesture}>
        <View style={styles.iconTouchable}>
          <Image resizeMode="contain" source={source} style={styles.iconImage} />
        </View>
      </GestureDetector>
    </Animated.View>
  );
}

/** 홈 화면 배경에서 한 방향으로 직진하며 테두리 안에서 튕기는 장식 아이콘.
 * 잡아서 끌면 따라오고 놓으면 관성으로 날아가 벽에 튕깁니다. 탭하면 통통.
 * 폰을 흔들면 5개가 슈루룩 사라지고 8개 풀에서 랜덤 5개가 슈루룩 새로 등장합니다. */
export function HomeBackgroundIcons({ weather }: { weather?: HomeWeather }) {
  const { height } = useWindowDimensions();
  const weatherAvailable = !!weather?.available;
  // 렌더 순수성 때문에 초기값은 랜덤 대신 풀 앞 5개(첫 흔들기부터 랜덤).
  const [sources, setSources] = useState<number[]>(() => POOL.slice(0, 5));
  const exit = useSharedValue(0);
  const enter = useSharedValue(0);
  // 아이콘 간 충돌용 공유 상태(스칼라 shared value 배열 — 배열 shared value의 불확실성 회피).
  // 각 바디가 매 프레임 자기 중심좌표/속도/활성여부를 게시하면 다른 바디가 읽어 충돌 처리.
  /* eslint-disable react-hooks/rules-of-hooks -- SLOTS는 상수 길이라 훅 호출 수가 항상 고정됨 */
  const centerXs = SLOTS.map(() => useSharedValue(0));
  const centerYs = SLOTS.map(() => useSharedValue(0));
  const velXs = SLOTS.map(() => useSharedValue(0));
  const velYs = SLOTS.map(() => useSharedValue(0));
  const actives = SLOTS.map(() => useSharedValue(0));
  /* eslint-enable react-hooks/rules-of-hooks */
  const busyRef = useRef(false);

  const handleShake = useCallback(() => {
    if (busyRef.current) {
      return;
    }
    busyRef.current = true;
    exit.value += 1; // 슈루룩 아웃
    setTimeout(() => {
      setSources(pickFive(POOL)); // 이미지 교체
      enter.value += 1; // 슈루룩 인
      setTimeout(() => {
        busyRef.current = false;
      }, ENTER_DURATION_MS + SLOTS.length * ENTER_STAGGER_MS + 80);
    }, SWAP_DELAY_MS);
  }, [exit, enter]);

  useShakeGesture(handleShake);

  return (
    // RN 0.86부터 pointerEvents는 prop이 아니라 style로 줘야 확실히 반영됩니다.
    <View pointerEvents="box-none" style={[styles.container, { height, pointerEvents: 'box-none' }]}>
      {SLOTS.map((spec, index) => {
        // 지정 슬롯은 현재 날씨 오브젝트로, 나머지는 랜덤 아파트로. (날씨 없으면 전부 아파트)
        const isWeatherSlot = index === WEATHER_SLOT_INDEX && weatherAvailable && !!weather;
        const source = isWeatherSlot
          ? resolveWeatherIcon(weather.iconKey, weather.conditionCode)
          : (sources[index] ?? POOL[index] ?? POOL[0]!);
        return (
          <FloatingApartIcon
            key={index}
            spec={spec}
            source={source}
            index={index}
            exit={exit}
            enter={enter}
            centerXs={centerXs}
            centerYs={centerYs}
            velXs={velXs}
            velYs={velYs}
            actives={actives}
          />
        );
      })}
    </View>
  );
}

const styles = StyleSheet.create({
  container: {
    position: 'absolute',
    top: 0,
    left: 0,
    right: 0,
  },
  icon: {
    position: 'absolute',
    shadowColor: '#000000',
    shadowOffset: { width: 0, height: 4 },
    shadowOpacity: 0.15,
    shadowRadius: 6,
    elevation: 4,
  },
  iconTouchable: {
    width: '100%',
    height: '100%',
  },
  iconImage: {
    width: '100%',
    height: '100%',
  },
});
