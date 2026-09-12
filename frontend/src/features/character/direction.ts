import { normalizeDegrees } from '@/lib/geo';
import type { Direction } from '@/store/characterStore';

/** 인덱스 순서가 시계방향 45° 간격이어야 아래 round 계산이 성립합니다. */
const DIRECTIONS: readonly Direction[] = ['N', 'NE', 'E', 'SE', 'S', 'SW', 'W', 'NW'];

const SECTOR_DEGREES = 360 / DIRECTIONS.length;

/**
 * 캐릭터가 화면에서 향할 방향.
 *
 * ⚠️ 절대 방위각을 그대로 쓰면 안 됩니다. 현장 지도는 followUserMode 가
 * FollowWithHeading 이라 지도 자체가 회전합니다. 지도 heading 을 빼서 "화면 기준"
 * 상대 방향으로 바꿔야 캐릭터가 실제로 걷는 쪽을 봅니다.
 *
 * 참고: heading 추적이 켜져 있는 동안은 진행 방향이 항상 화면 위쪽이라 결과가
 * 대체로 'N'(뒤통수)으로 수렴합니다. 사용자가 지도를 손으로 돌리거나 정북 고정
 * 카메라로 바뀔 때 나머지 방향이 쓰입니다.
 */
export function toScreenDirection(movementBearing: number, mapHeading: number): Direction {
  const relative = normalizeDegrees(movementBearing - mapHeading);
  const index = Math.round(relative / SECTOR_DEGREES) % DIRECTIONS.length;

  return DIRECTIONS[index] ?? 'S';
}
