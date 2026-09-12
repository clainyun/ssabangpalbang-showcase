export type NotificationCategory =
  'STUDY' | 'FIELD' | 'REPORT' | 'COMMUNITY' | 'MESSAGE' | 'SYSTEM';

export type NotificationType = string;

export interface NotificationActor {
  memberId: number;
  nickname: string;
  profileImageUrl: string | null;
  selectedCharacterId: string | null;
}

export interface NotificationItem {
  notificationId: number;
  category: NotificationCategory;
  type: NotificationType;
  title: string;
  body: string;
  actor: NotificationActor | null;
  isRead: boolean;
  readAt: string | null;
  targetScreen: string | null;
  targetId: number | null;
  targetSubId: number | null;
  targetAvailable: boolean;
  sentAt: string;
}

export interface NotificationList {
  content: NotificationItem[];
  unreadCount: number;
  nextCursor: string | null;
  hasNext: boolean;
}

export interface NotificationPageParam {
  cursor: string | null;
}

export interface NotificationReadResult {
  notificationId: number;
  isRead: boolean;
  readAt: string;
  unreadCount: number;
}

export interface NotificationApiEnvelope<T> {
  success: boolean;
  code: string;
  message: string;
  data: T | null;
  timestamp?: string;
}

export class NotificationApiError extends Error {
  constructor(
    public readonly code: string,
    message: string,
  ) {
    super(message);
    this.name = 'NotificationApiError';
  }
}
