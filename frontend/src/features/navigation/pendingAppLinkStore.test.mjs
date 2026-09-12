import assert from 'node:assert/strict';
import { beforeEach, test } from 'node:test';

import {
  parsePendingReportId,
  takePostAuthRoute,
  usePendingAppLinkStore,
} from './pendingAppLinkStore.ts';

beforeEach(() => {
  usePendingAppLinkStore.getState().clear();
});

test('검증된 리포트 경로만 인증 후 목적지로 보존한다', () => {
  assert.equal(parsePendingReportId('/open/report/42'), '42');
  assert.equal(parsePendingReportId('/(app)/open/report/42'), '42');
  assert.equal(parsePendingReportId('/open/report/42/'), null);
  assert.equal(parsePendingReportId('/report/42'), null);
  assert.equal(parsePendingReportId('/open/report/0'), null);
  assert.equal(parsePendingReportId('/open/report/-1'), null);
  assert.equal(parsePendingReportId('/open/report/not-a-number'), null);
  assert.equal(parsePendingReportId('/open/report/42/extra'), null);
  assert.equal(parsePendingReportId('/open/report/9007199254740992'), null);
  assert.equal(parsePendingReportId('https://evil.example/open/report/42'), null);
});

test('보존한 리포트 목적지는 한 번만 소비한다', () => {
  assert.equal(usePendingAppLinkStore.getState().capturePath('/open/report/42'), true);
  assert.deepEqual(takePostAuthRoute(), {
    pathname: '/(app)/report/[reportId]',
    params: { reportId: '42' },
  });
  assert.equal(takePostAuthRoute(), '/(app)/(tabs)/home');
});

test('유효하지 않은 경로는 기존 목적지를 덮어쓰지 않는다', () => {
  usePendingAppLinkStore.getState().capturePath('/open/report/7');
  assert.equal(usePendingAppLinkStore.getState().capturePath('/settings'), false);
  assert.deepEqual(usePendingAppLinkStore.getState().pendingRoute, {
    pathname: '/(app)/report/[reportId]',
    params: { reportId: '7' },
  });
});

test('검증된 알림 목적지는 인증 후 한 번만 소비한다', () => {
  usePendingAppLinkStore.getState().captureRoute({
    pathname: '/(app)/field/[sessionId]',
    params: { sessionId: '17', studyId: '16' },
  });

  assert.deepEqual(takePostAuthRoute(), {
    pathname: '/(app)/field/[sessionId]',
    params: { sessionId: '17', studyId: '16' },
  });
  assert.equal(takePostAuthRoute(), '/(app)/(tabs)/home');
});

test('가장 최근에 선택한 목적지가 이전 대기 목적지를 대체한다', () => {
  usePendingAppLinkStore.getState().capturePath('/open/report/7');
  usePendingAppLinkStore.getState().captureRoute({
    pathname: '/(app)/chat/[roomId]',
    params: { roomId: '18' },
  });

  assert.deepEqual(takePostAuthRoute(), {
    pathname: '/(app)/chat/[roomId]',
    params: { roomId: '18' },
  });
});
