import assert from 'node:assert/strict';
import { test } from 'node:test';

import {
  resolveNotificationNavigation,
  resolveNotificationResponseNavigation,
} from './notificationNavigation.ts';

test('현재 FCM 목적지를 허용된 앱 화면으로 변환한다', () => {
  const scenarios = [
    {
      data: { targetScreen: 'MEMBER_PROFILE', targetId: '11' },
      href: {
        pathname: '/(app)/member/[memberId]',
        params: { memberId: '11' },
      },
    },
    {
      data: { targetScreen: 'STUDY_DETAIL', targetId: '12' },
      href: { pathname: '/(app)/study/[id]', params: { id: '12' } },
    },
    {
      data: { targetScreen: 'STUDY_MANAGE', targetId: '13' },
      href: { pathname: '/(app)/study/[id]/manage', params: { id: '13' } },
    },
    {
      data: { targetScreen: 'STUDY_SCHEDULE', targetId: '14' },
      href: { pathname: '/(app)/study/[id]', params: { id: '14' } },
    },
    {
      data: { targetScreen: 'REPORT_DETAIL', targetId: '15' },
      href: {
        pathname: '/(app)/report/[reportId]',
        params: { reportId: '15' },
      },
    },
    {
      data: { targetScreen: 'FIELD_VISIT', targetId: '16', targetSubId: '17' },
      href: {
        pathname: '/(app)/field/[sessionId]',
        params: { sessionId: '17', studyId: '16' },
      },
    },
    {
      data: { targetScreen: 'STUDY_CHAT', studyId: '18', messageId: '19' },
      href: { pathname: '/(app)/chat/[roomId]', params: { roomId: '18' } },
    },
  ];

  for (const { data, href } of scenarios) {
    assert.deepEqual(resolveNotificationResponseNavigation(data), {
      status: 'ready',
      href,
    });
  }
});

test('잘못되거나 임의의 FCM payload는 이동 대상으로 사용하지 않는다', () => {
  const invalidPayloads = [
    undefined,
    {},
    { targetScreen: 'UNKNOWN', targetId: '1' },
    { targetScreen: 'REPORT_DETAIL', targetId: 1 },
    { targetScreen: 'REPORT_DETAIL', targetId: '0' },
    { targetScreen: 'REPORT_DETAIL', targetId: '-1' },
    { targetScreen: 'REPORT_DETAIL', targetId: ' 1' },
    { targetScreen: 'REPORT_DETAIL', targetId: '9007199254740992' },
    { targetScreen: 'FIELD_VISIT', targetId: '1', targetSubId: 'invalid' },
    { targetScreen: 'STUDY_CHAT', studyId: 'invalid' },
    { targetScreen: 'https://evil.example', targetId: '1', url: 'https://evil.example' },
  ];

  for (const payload of invalidPayloads) {
    assert.deepEqual(resolveNotificationResponseNavigation(payload), {
      status: 'unsupported',
    });
  }
});

test('앱 내부 알림의 접근 불가 처리와 일정 목적지를 유지한다', () => {
  assert.deepEqual(
    resolveNotificationNavigation({
      targetAvailable: false,
      targetScreen: 'REPORT_DETAIL',
      targetId: 7,
      targetSubId: null,
    }),
    { status: 'target-unavailable' },
  );
  assert.deepEqual(
    resolveNotificationNavigation({
      targetAvailable: true,
      targetScreen: 'STUDY_SCHEDULE',
      targetId: 8,
      targetSubId: null,
    }),
    {
      status: 'ready',
      href: { pathname: '/(app)/study/[id]', params: { id: '8' } },
    },
  );
});
