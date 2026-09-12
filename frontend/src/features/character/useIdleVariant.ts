import { useEffect, useRef, useState } from 'react';

import type { IdleVariant, MotionState } from './spriteConfig';

const IDLE_VARIANT_COUNT = 3;

function randomIdleVariant(): IdleVariant {
  return (Math.floor(Math.random() * IDLE_VARIANT_COUNT) + 1) as IdleVariant;
}

/**
 * 대기 모션 3종(idle1~3) 중 하나를 무작위로 고릅니다.
 *
 * motion 이 걷기→대기로 바뀌는 시점에만 다시 뽑습니다. 대기 도중 계속 다시
 * 뽑으면 프레임이 재생되는 중간에 다른 동작으로 튀어 보이므로, 한 번 고르면
 * 그 대기 구간 동안은 고정합니다.
 */
export function useIdleVariant(motion: MotionState): IdleVariant {
  const [variant, setVariant] = useState<IdleVariant>(randomIdleVariant);
  const previousMotionRef = useRef<MotionState>(motion);

  useEffect(() => {
    if (motion === 'idle' && previousMotionRef.current !== 'idle') {
      setVariant(randomIdleVariant());
    }
    previousMotionRef.current = motion;
  }, [motion]);

  return variant;
}
