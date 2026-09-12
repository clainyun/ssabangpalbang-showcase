import assert from 'node:assert/strict';
import { test } from 'node:test';

import { rotateScreenOffsetToWorld } from './geo.ts';

/** 부동소수 오차를 허용하는 비교. 회전에는 sin·cos 가 들어갑니다. */
const assertClose = (actual, expected, message) => {
  assert.ok(
    Math.abs(actual - expected) < 1e-9,
    `${message ?? ''} (기대 ${expected}, 실제 ${actual})`,
  );
};

test('지도가 정북이면 화면 기준이 곧 방위다', () => {
  const { eastMeters, northMeters } = rotateScreenOffsetToWorld(1, 0, 0);

  assertClose(northMeters, 1, '화면 위는 북쪽');
  assertClose(eastMeters, 0);
});

test('카메라가 동쪽을 보면 화면 위가 동쪽이 된다', () => {
  const { eastMeters, northMeters } = rotateScreenOffsetToWorld(1, 0, 90);

  assertClose(northMeters, 0);
  assertClose(eastMeters, 1, '스틱을 위로 밀면 동쪽으로 간다');
});

test('카메라가 남쪽을 보면 화면 위가 남쪽이 된다', () => {
  const { eastMeters, northMeters } = rotateScreenOffsetToWorld(1, 0, 180);

  assertClose(northMeters, -1);
  assertClose(eastMeters, 0);
});

test('카메라가 동쪽을 보면 화면 오른쪽은 남쪽이 된다', () => {
  const { eastMeters, northMeters } = rotateScreenOffsetToWorld(0, 1, 90);

  assertClose(northMeters, -1, '오른쪽은 진행 방향의 오른쪽 = 남쪽');
  assertClose(eastMeters, 0);
});

test('회전은 이동 거리를 바꾸지 않는다', () => {
  for (const heading of [0, 37, 90, 175, 268, 359]) {
    const { eastMeters, northMeters } = rotateScreenOffsetToWorld(0.6, -0.8, heading);

    assertClose(Math.hypot(northMeters, eastMeters), 1, `heading=${heading}`);
  }
});

test('음수·360 이상 방위도 정규화해 같은 결과를 낸다', () => {
  const base = rotateScreenOffsetToWorld(1, 0, 90);

  for (const heading of [-270, 450, 810]) {
    const rotated = rotateScreenOffsetToWorld(1, 0, heading);

    assertClose(rotated.northMeters, base.northMeters, `heading=${heading}`);
    assertClose(rotated.eastMeters, base.eastMeters, `heading=${heading}`);
  }
});

test('정지 입력은 어떤 방위에서도 움직이지 않는다', () => {
  const { eastMeters, northMeters } = rotateScreenOffsetToWorld(0, 0, 123);

  assertClose(northMeters, 0);
  assertClose(eastMeters, 0);
});
