import type { Href } from 'expo-router';

import type { NotificationItem } from './api/types';

export type NotificationNavigationResult =
  { status: 'ready'; href: Href } | { status: 'target-unavailable' } | { status: 'unsupported' };

export interface NotificationNavigationTarget {
  targetScreen: string | null;
  targetId: number | null;
  targetSubId: number | null;
}

const POSITIVE_INTEGER_PATTERN = /^[1-9]\d*$/;

type ParsedOptionalId = number | null | undefined;

function isPositiveSafeInteger(value: number | null): value is number {
  return value !== null && Number.isSafeInteger(value) && value > 0;
}

function hasValidOptionalId(value: number | null): boolean {
  return value === null || isPositiveSafeInteger(value);
}

function parseOptionalId(value: unknown): ParsedOptionalId {
  if (value === undefined || value === null) {
    return null;
  }
  if (typeof value !== 'string' || !POSITIVE_INTEGER_PATTERN.test(value)) {
    return undefined;
  }

  const parsed = Number(value);
  return Number.isSafeInteger(parsed) ? parsed : undefined;
}

export function resolveNotificationNavigation(
  notification: NotificationItem,
): NotificationNavigationResult {
  if (!notification.targetAvailable) {
    return { status: 'target-unavailable' };
  }

  return resolveNotificationTargetNavigation(notification);
}

export function resolveNotificationResponseNavigation(
  data: Record<string, unknown> | undefined,
): NotificationNavigationResult {
  if (!data) {
    return { status: 'unsupported' };
  }

  const targetScreen = typeof data.targetScreen === 'string' ? data.targetScreen : null;
  const rawTargetId =
    targetScreen === 'STUDY_CHAT' && data.targetId === undefined ? data.studyId : data.targetId;
  const targetId = parseOptionalId(rawTargetId);
  const targetSubId = parseOptionalId(data.targetSubId);

  if (targetId === undefined || targetSubId === undefined) {
    return { status: 'unsupported' };
  }

  return resolveNotificationTargetNavigation({
    targetScreen,
    targetId,
    targetSubId,
  });
}

export function resolveNotificationTargetNavigation(
  target: NotificationNavigationTarget,
): NotificationNavigationResult {
  const { targetId, targetScreen, targetSubId } = target;

  if (!hasValidOptionalId(targetId) || !hasValidOptionalId(targetSubId)) {
    return { status: 'unsupported' };
  }

  switch (targetScreen) {
    case 'MEMBER_PROFILE':
      return isPositiveSafeInteger(targetId)
        ? {
            status: 'ready',
            href: {
              pathname: '/(app)/member/[memberId]',
              params: { memberId: String(targetId) },
            },
          }
        : { status: 'unsupported' };
    case 'STUDY_DETAIL':
      return isPositiveSafeInteger(targetId)
        ? {
            status: 'ready',
            href: {
              pathname: '/(app)/study/[id]',
              params: { id: String(targetId) },
            },
          }
        : { status: 'unsupported' };
    case 'STUDY_MANAGE':
      return isPositiveSafeInteger(targetId)
        ? {
            status: 'ready',
            href: {
              pathname: '/(app)/study/[id]/manage',
              params: { id: String(targetId) },
            },
          }
        : { status: 'unsupported' };
    case 'STUDY_SCHEDULE':
      return isPositiveSafeInteger(targetId)
        ? {
            status: 'ready',
            href: {
              pathname: '/(app)/study/[id]',
              params: { id: String(targetId) },
            },
          }
        : { status: 'unsupported' };
    case 'REPORT_DETAIL':
      return isPositiveSafeInteger(targetId)
        ? {
            status: 'ready',
            // FE-021이 제공하는 라우트다. 이 브랜치에서는 화면을 복제하지 않는다.
            href: {
              pathname: '/(app)/report/[reportId]',
              params: { reportId: String(targetId) },
            } as Href,
          }
        : { status: 'unsupported' };
    case 'FIELD_VISIT':
      return isPositiveSafeInteger(targetId) && isPositiveSafeInteger(targetSubId)
        ? {
            status: 'ready',
            href: {
              pathname: '/(app)/field/[sessionId]',
              params: {
                sessionId: String(targetSubId),
                studyId: String(targetId),
              },
            },
          }
        : { status: 'unsupported' };
    case 'STUDY_CHAT':
      return isPositiveSafeInteger(targetId)
        ? {
            status: 'ready',
            href: {
              pathname: '/(app)/chat/[roomId]',
              params: { roomId: String(targetId) },
            },
          }
        : { status: 'unsupported' };
    default:
      return { status: 'unsupported' };
  }
}
