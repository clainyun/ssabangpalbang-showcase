import { useCallback, useEffect, useMemo, useRef } from 'react';
import { StyleSheet, Text, View, type StyleProp, type ViewStyle } from 'react-native';
import { Gesture, GestureDetector } from 'react-native-gesture-handler';
import Animated, { useAnimatedStyle, useSharedValue, withSpring } from 'react-native-reanimated';

interface DeveloperLocationControlsProps {
  /**
   * **화면 기준** 이동량을 넘깁니다. 위/오른쪽이 양수이며, 방위(북/동)가 아닙니다.
   *
   * 임장 지도는 진행 방향을 화면 위로 두고 회전하므로 화면 위가 정북이 아닙니다.
   * 받는 쪽에서 지도 방위만큼 돌려야 합니다(`rotateScreenOffsetToWorld`).
   */
  onMove: (screenUpMeters: number, screenRightMeters: number) => void;
  onMoveEnd: () => void;
  reduceMotionEnabled?: boolean;
  style?: StyleProp<ViewStyle>;
}

/** 스틱 입력. 단위 벡터도 화면 기준입니다. */
interface JoystickVector {
  intensity: number;
  rightUnit: number;
  upUnit: number;
}

const PAD_SIZE = 120;
const KNOB_SIZE = 48;
const KNOB_TRAVEL_RADIUS = (PAD_SIZE - KNOB_SIZE) / 2;
const DEAD_ZONE_RATIO = 0.18;
const DEAD_ZONE_RADIUS = KNOB_TRAVEL_RADIUS * DEAD_ZONE_RATIO;
const MOVEMENT_INTERVAL_MS = 50;
const MOVEMENT_STEP_METERS = 1;
const ACCESSIBILITY_STEP_METERS = 1;
const IDLE_VECTOR: JoystickVector = { intensity: 0, rightUnit: 0, upUnit: 0 };

// 스틱과 같은 화면 기준입니다. 지도가 회전하므로 "북쪽"이라고 안내하면 실제 이동과
// 어긋납니다.
const ACCESSIBILITY_ACTIONS = [
  { label: '화면 위쪽으로 한 단계 이동', name: 'increment' },
  { label: '화면 아래쪽으로 한 단계 이동', name: 'decrement' },
  { label: '화면 위쪽으로 한 단계 이동', name: 'moveUp' },
  { label: '화면 오른쪽으로 한 단계 이동', name: 'moveRight' },
  { label: '화면 아래쪽으로 한 단계 이동', name: 'moveDown' },
  { label: '화면 왼쪽으로 한 단계 이동', name: 'moveLeft' },
];

export function DeveloperLocationControls({
  onMove,
  onMoveEnd,
  reduceMotionEnabled = false,
  style,
}: DeveloperLocationControlsProps) {
  const knobX = useSharedValue(0);
  const knobY = useSharedValue(0);
  const inputRef = useRef<JoystickVector>(IDLE_VECTOR);
  const movementTimerRef = useRef<ReturnType<typeof setTimeout> | null>(null);
  const movementActiveRef = useRef(false);
  const onMoveRef = useRef(onMove);
  const onMoveEndRef = useRef(onMoveEnd);

  useEffect(() => {
    onMoveRef.current = onMove;
  }, [onMove]);

  useEffect(() => {
    onMoveEndRef.current = onMoveEnd;
  }, [onMoveEnd]);

  const updateInput = useCallback(
    (touchX: number, touchY: number) => {
      const right = touchX - PAD_SIZE / 2;
      const down = touchY - PAD_SIZE / 2;
      const distance = Math.hypot(right, down);
      const clampedScale = distance > KNOB_TRAVEL_RADIUS ? KNOB_TRAVEL_RADIUS / distance : 1;

      knobX.set(right * clampedScale);
      knobY.set(down * clampedScale);

      if (distance <= DEAD_ZONE_RADIUS) {
        const wasMoving = inputRef.current.intensity > 0;
        inputRef.current = IDLE_VECTOR;
        if (wasMoving) {
          onMoveEndRef.current();
        }
        return;
      }

      const clampedDistance = Math.min(distance, KNOB_TRAVEL_RADIUS);
      inputRef.current = {
        intensity:
          (clampedDistance - DEAD_ZONE_RADIUS) / (KNOB_TRAVEL_RADIUS - DEAD_ZONE_RADIUS),
        rightUnit: right / distance,
        upUnit: -down / distance,
      };
    },
    [knobX, knobY],
  );

  const runMovementTick = useCallback(function runMovementTick() {
    movementTimerRef.current = null;
    if (!movementActiveRef.current) return;

    const { intensity, rightUnit, upUnit } = inputRef.current;
    if (intensity > 0) {
      onMoveRef.current(upUnit * MOVEMENT_STEP_METERS, rightUnit * MOVEMENT_STEP_METERS);
    }

    if (!movementActiveRef.current) return;
    movementTimerRef.current = setTimeout(runMovementTick, MOVEMENT_INTERVAL_MS);
  }, []);

  const beginMovement = useCallback(
    (touchX: number, touchY: number) => {
      updateInput(touchX, touchY);
      movementActiveRef.current = true;
      if (movementTimerRef.current !== null) {
        clearTimeout(movementTimerRef.current);
      }
      movementTimerRef.current = setTimeout(runMovementTick, MOVEMENT_INTERVAL_MS);
    },
    [runMovementTick, updateInput],
  );

  const endMovement = useCallback(
    (animateKnob = true) => {
      movementActiveRef.current = false;
      if (movementTimerRef.current !== null) {
        clearTimeout(movementTimerRef.current);
        movementTimerRef.current = null;
      }
      inputRef.current = IDLE_VECTOR;

      if (animateKnob && !reduceMotionEnabled) {
        const springConfig = { damping: 17, stiffness: 220 };
        knobX.set(withSpring(0, springConfig));
        knobY.set(withSpring(0, springConfig));
      } else {
        knobX.set(0);
        knobY.set(0);
      }

      onMoveEndRef.current();
    },
    [knobX, knobY, reduceMotionEnabled],
  );

  useEffect(() => () => endMovement(false), [endMovement]);

  const panGesture = useMemo(
    () =>
      Gesture.Pan()
        .minDistance(0)
        .maxPointers(1)
        .onBegin((event) => beginMovement(event.x, event.y))
        .onUpdate((event) => updateInput(event.x, event.y))
        .onFinalize(() => endMovement())
        .runOnJS(true),
    [beginMovement, endMovement, updateInput],
  );

  const knobStyle = useAnimatedStyle(() => ({
    transform: [{ translateX: knobX.get() }, { translateY: knobY.get() }],
  }));

  const moveAccessibilityStep = useCallback(
    (actionName: string) => {
      switch (actionName) {
        case 'increment':
        case 'moveUp':
          onMove(ACCESSIBILITY_STEP_METERS, 0);
          break;
        case 'moveRight':
          onMove(0, ACCESSIBILITY_STEP_METERS);
          break;
        case 'decrement':
        case 'moveDown':
          onMove(-ACCESSIBILITY_STEP_METERS, 0);
          break;
        case 'moveLeft':
          onMove(0, -ACCESSIBILITY_STEP_METERS);
          break;
        default:
          return;
      }
      onMoveEnd();
    },
    [onMove, onMoveEnd],
  );

  return (
    <View pointerEvents="box-none" style={[styles.container, style]}>
      <View style={styles.badge}>
        <View style={styles.badgeDot} />
        <Text style={styles.badgeText}>DEMO GPS</Text>
      </View>

      <GestureDetector gesture={panGesture}>
        <Animated.View
          accessibilityActions={ACCESSIBILITY_ACTIONS}
          accessibilityHint="패드를 원하는 방향과 세기만큼 끌거나 접근성 동작을 선택하세요"
          accessibilityLabel="가상 GPS 이동 조이스틱"
          accessibilityRole="adjustable"
          accessible
          onAccessibilityAction={(event) =>
            moveAccessibilityStep(event.nativeEvent.actionName)
          }
          style={styles.pad}
        >
          <Animated.View pointerEvents="none" style={[styles.knob, knobStyle]} />
        </Animated.View>
      </GestureDetector>
    </View>
  );
}

const styles = StyleSheet.create({
  container: {
    alignItems: 'center',
    gap: 8,
  },
  badge: {
    alignItems: 'center',
    backgroundColor: 'rgba(24, 55, 46, 0.92)',
    borderRadius: 12,
    flexDirection: 'row',
    gap: 5,
    paddingHorizontal: 9,
    paddingVertical: 5,
  },
  badgeDot: {
    backgroundColor: '#63E69A',
    borderRadius: 4,
    height: 7,
    width: 7,
  },
  badgeText: {
    color: '#FFFFFF',
    fontSize: 10,
    fontWeight: '800',
    letterSpacing: 0.5,
  },
  pad: {
    alignItems: 'center',
    backgroundColor: 'rgba(43, 47, 47, 0.76)',
    borderRadius: PAD_SIZE / 2,
    height: PAD_SIZE,
    justifyContent: 'center',
    width: PAD_SIZE,
  },
  knob: {
    backgroundColor: 'rgba(224, 228, 227, 0.84)',
    borderRadius: KNOB_SIZE / 2,
    height: KNOB_SIZE,
    width: KNOB_SIZE,
  },
});
