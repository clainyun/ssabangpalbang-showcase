import { Accelerometer } from 'expo-sensors';
import { useEffect, useRef } from 'react';

const UPDATE_INTERVAL_MS = 100;
const SHAKE_THRESHOLD = 1.8;
const SHAKE_COOLDOWN_MS = 1000;

/**
 * 가속도계로 흔들기 제스처를 감지합니다. 100ms 간격 샘플 사이의 가속도 변화량이
 * 임계값(SHAKE_THRESHOLD)을 넘으면 onShake를 호출합니다. 흔드는 동안 여러 번
 * 연속으로 트리거되지 않도록 쿨다운(SHAKE_COOLDOWN_MS)을 둡니다.
 * onShake는 ref로 들고 있어서 매 렌더마다 구독을 새로 걸지 않습니다.
 */
export function useShakeGesture(onShake: () => void) {
  const onShakeRef = useRef(onShake);

  useEffect(() => {
    onShakeRef.current = onShake;
  }, [onShake]);

  useEffect(() => {
    Accelerometer.setUpdateInterval(UPDATE_INTERVAL_MS);

    const lastReading = { x: 0, y: 0, z: 0 };
    let lastShakeAt = 0;

    const subscription = Accelerometer.addListener(({ x, y, z }) => {
      const now = Date.now();
      const delta = Math.abs(x + y + z - lastReading.x - lastReading.y - lastReading.z);
      lastReading.x = x;
      lastReading.y = y;
      lastReading.z = z;

      if (delta > SHAKE_THRESHOLD && now - lastShakeAt > SHAKE_COOLDOWN_MS) {
        lastShakeAt = now;
        onShakeRef.current();
      }
    });

    return () => subscription.remove();
  }, []);
}
