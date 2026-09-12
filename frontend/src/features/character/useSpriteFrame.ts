import { useEffect } from 'react';
import { useFrameCallback, useSharedValue } from 'react-native-reanimated';

interface SpriteFrameOptions {
  frameCount: number;
  fps: number;
  /** false 면 0번 프레임에 정지합니다. 모션 축소·저성능·정적 대체 상태에 씁니다. */
  playing: boolean;
  /**
   * true 면 0→끝→0 으로 되돌아오는 핑퐁(yoyo) 재생을 합니다. 끝에서 처음으로 튀지
   * 않아 idle 마스코트처럼 자연스럽게 왕복합니다. 기본값 false(단방향 루프 — walk 등).
   */
  pingPong?: boolean;
}

/**
 * 스프라이트 프레임 인덱스를 UI 스레드에서 계산합니다.
 *
 * §7.1 — `frameIndex = floor(elapsedMs / (1000 / fps)) % frameCount`
 *
 * 왜 렌더 프레임 카운터를 안 쓰는가: S23 은 120Hz 입니다. 렌더 프레임마다 스프라이트를
 * 한 칸 넘기면 재생 속도가 기기 주사율에 끌려다녀서 60Hz 기기와 120Hz 기기에서
 * 걷기 속도가 2배 차이납니다. 그래서 경과 시간 기준으로 계산합니다.
 *
 * 반환값은 shared value 입니다. JS 상태가 아니므로 프레임이 넘어가도 리렌더가
 * 발생하지 않습니다 (characterStore.ts 상단 경고와 같은 이유).
 */
export function useSpriteFrame({
  frameCount,
  fps,
  playing,
  pingPong = false,
}: SpriteFrameOptions) {
  const frameIndex = useSharedValue(0);
  const elapsedMs = useSharedValue(0);
  // useFrameCallback 의 워클릿은 등록 시점의 값을 캡처합니다. 상태가 바뀔 때
  // 재등록에 의존하지 않도록 설정을 shared value 로 넘깁니다.
  const config = useSharedValue({ frameCount, fps, playing, pingPong });

  useEffect(() => {
    config.set({ frameCount, fps, playing, pingPong });
    if (!playing) {
      // 다시 걷기 시작할 때 사이클 중간에서 튀어나오지 않게 리셋합니다.
      elapsedMs.set(0);
      frameIndex.set(0);
    }
  }, [frameCount, fps, playing, pingPong, config, elapsedMs, frameIndex]);

  useFrameCallback((info) => {
    'worklet';
    const { frameCount: count, fps: rate, playing: active, pingPong: yoyo } =
      config.get();
    if (!active || count <= 1 || rate <= 0) {
      return;
    }

    const frameDurationMs = 1000 / rate;
    // 핑퐁은 0..count-1..1 을 한 주기로 도므로 (2*count-2) 프레임. 단방향은 count.
    const period = yoyo && count > 2 ? 2 * count - 2 : count;
    const cycleMs = frameDurationMs * period;
    // 사이클 길이로 접어서 값이 무한히 커지지 않게 합니다.
    const elapsed = (elapsedMs.get() + (info.timeSincePreviousFrame ?? 0)) % cycleMs;
    elapsedMs.set(elapsed);

    const position = Math.floor(elapsed / frameDurationMs);
    // 핑퐁: 후반부는 반사시켜 끝에서 되돌아옵니다(양 끝 프레임이 반복되지 않음).
    const next =
      yoyo && count > 2 && position >= count ? period - position : position;
    if (next !== frameIndex.get()) {
      frameIndex.set(next);
    }
  });

  return frameIndex;
}
