import assert from 'node:assert/strict';
import { test } from 'node:test';

import { getNavigationCameraTarget } from './navigationCamera.ts';

const route = [
  [127, 37.5],
  [127.001, 37.5],
];
const destination = route.at(-1);

test('경로 35m 안에서는 카메라 중심을 경로 위에 둔다', () => {
  const result = getNavigationCameraTarget([127.0002, 37.5001], route, destination);

  assert.ok(result);
  assert.ok(Math.abs(result.anchorCoordinate[1] - 37.5) < 0.000001);
});

test('경로에서 35m 넘게 벗어나면 실제 위치에서 목적지를 바라본다', () => {
  const coordinate = [127.0002, 37.501];
  const result = getNavigationCameraTarget(coordinate, route, destination);

  assert.ok(result);
  assert.deepEqual(result.anchorCoordinate, coordinate);
  assert.ok(Number.isFinite(result.heading));
});

test('실제 위치와 목적지가 같으면 불필요한 heading을 만들지 않는다', () => {
  assert.equal(getNavigationCameraTarget(destination, [], destination), null);
});

test('경로 끝 중복 좌표는 마지막 유효 구간의 방향을 유지한다', () => {
  const routeWithDuplicateEnd = [route[0], route[1], route[1]];
  const result = getNavigationCameraTarget(route[1], routeWithDuplicateEnd, route[1]);

  assert.ok(result);
  assert.ok(result.heading > 80 && result.heading < 100);
});
