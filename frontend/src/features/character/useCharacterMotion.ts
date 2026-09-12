import { useCallback, useEffect, useRef } from 'react';

import { bearingDegrees, distanceInMeters, type Coordinate } from '@/lib/geo';
import { useCharacterStore } from '@/store/characterStore';

import { toScreenDirection } from './direction';

/**
 * 보행 속도는 약 1.4m/s. 정지 중에도 GPS 는 수 m 씩 흔들리므로(지터) 이 하한으로
 * 걸러야 가만히 서 있는데 걷기 애니메이션이 도는 걸 막을 수 있습니다.
 */
const MOVING_SPEED_MPS = 0.5;

/** 이보다 짧은 변위의 방위각은 GPS 노이즈라서 방향 판정에 쓰지 않습니다. */
const DIRECTION_MIN_DISTANCE_METERS = 3;

/**
 * 걷다가 멈추면 위치 업데이트 자체가 끊길 수 있습니다. 그러면 마지막 "이동 중"
 * 상태에 갇혀서 계속 걷는 것처럼 보이므로, 이 시간이 지나면 스스로 대기로 내려옵니다.
 */
const STOP_AFTER_MS = 2_500;

export interface LocationSample {
  coordinate: Coordinate;
  /** m/s. 기기가 주지 않으면 null */
  speed: number | null;
  /** GPS 가 보고한 진행 방향(정북 0°). 없으면 null */
  course: number | null;
  /** 노이즈 없는 시뮬레이션 좌표처럼 course를 즉시 신뢰해도 되는 샘플 */
  courseIsTrusted?: boolean;
  /** epoch ms */
  timestamp: number;
}

/**
 * 위치 업데이트를 캐릭터의 저빈도 상태(이동 여부·방향)로 변환합니다.
 *
 * 프레임 인덱스는 여기서 다루지 않습니다 — 그건 useSpriteFrame 이 UI 스레드에서
 * 처리합니다. 여기서 zustand 에 쓰는 값은 초당 몇 번 수준이라 안전합니다
 * (characterStore.ts 상단 경고 참고).
 */
export function useCharacterMotion() {
  const setDirection = useCharacterStore((state) => state.setDirection);
  const setMoving = useCharacterStore((state) => state.setMoving);

  const previousSampleRef = useRef<LocationSample | null>(null);
  /** 방향을 마지막으로 갱신한 지점. 여기서 3m 이상 벗어나야 방향을 다시 봅니다. */
  const directionAnchorRef = useRef<Coordinate | null>(null);
  /** 마지막으로 판정한 절대 방위각. 지도가 회전하면 이 값으로 방향을 재계산합니다. */
  const lastBearingRef = useRef<number | null>(null);
  const mapHeadingRef = useRef(0);
  const stopTimerRef = useRef<ReturnType<typeof setTimeout> | null>(null);

  useEffect(
    () => () => {
      if (stopTimerRef.current !== null) {
        clearTimeout(stopTimerRef.current);
      }
    },
    [],
  );

  const reportLocation = useCallback(
    (sample: LocationSample) => {
      const previous = previousSampleRef.current;
      previousSampleRef.current = sample;

      // 기기가 속도를 주면 그걸 씁니다. 변위/시간으로 역산하는 쪽이 GPS 지터에
      // 훨씬 민감해서 오탐이 많습니다.
      let isMoving: boolean;
      if (sample.speed !== null && sample.speed >= 0) {
        isMoving = sample.speed >= MOVING_SPEED_MPS;
      } else if (previous !== null) {
        const elapsedSeconds = (sample.timestamp - previous.timestamp) / 1000;
        const moved = distanceInMeters(previous.coordinate, sample.coordinate);
        isMoving = elapsedSeconds > 0 && moved / elapsedSeconds >= MOVING_SPEED_MPS;
      } else {
        isMoving = false;
      }

      const anchor = directionAnchorRef.current;
      if (sample.courseIsTrusted && isMoving && sample.course !== null) {
        lastBearingRef.current = sample.course;
        directionAnchorRef.current = sample.coordinate;
        setDirection(toScreenDirection(sample.course, mapHeadingRef.current));
      } else if (anchor === null) {
        directionAnchorRef.current = sample.coordinate;
      } else if (distanceInMeters(anchor, sample.coordinate) >= DIRECTION_MIN_DISTANCE_METERS) {
        // 변위로 구한 방위각이 GPS course 보다 안정적입니다. course 는 저속에서
        // 심하게 튀지만 반응은 즉각적이라, 변위가 아직 안 쌓였을 때만 보조로 씁니다.
        const bearing = bearingDegrees(anchor, sample.coordinate);
        lastBearingRef.current = bearing;
        directionAnchorRef.current = sample.coordinate;
        setDirection(toScreenDirection(bearing, mapHeadingRef.current));
      } else if (lastBearingRef.current === null && isMoving && sample.course !== null) {
        lastBearingRef.current = sample.course;
        setDirection(toScreenDirection(sample.course, mapHeadingRef.current));
      }

      setMoving(isMoving);

      if (stopTimerRef.current !== null) {
        clearTimeout(stopTimerRef.current);
        stopTimerRef.current = null;
      }
      if (isMoving) {
        stopTimerRef.current = setTimeout(() => setMoving(false), STOP_AFTER_MS);
      }
    },
    [setDirection, setMoving],
  );

  /**
   * 지도가 회전했을 때 호출합니다. 캐릭터가 멈춰 있어도 지도가 돌면 화면 기준
   * 방향은 바뀌므로 다시 계산해야 합니다.
   */
  const reportMapHeading = useCallback(
    (heading: number) => {
      mapHeadingRef.current = heading;
      const bearing = lastBearingRef.current;
      if (bearing !== null) {
        setDirection(toScreenDirection(bearing, heading));
      }
    },
    [setDirection],
  );

  return { reportLocation, reportMapHeading };
}
