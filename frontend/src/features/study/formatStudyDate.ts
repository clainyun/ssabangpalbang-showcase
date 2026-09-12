/** 스터디 상세 화면 전용 날짜 포맷. startAt은 Asia/Seoul 오프셋 포함 ISO 문자열입니다. */

const EN_WEEKDAY_TO_KO: Record<string, string> = {
  Sun: '일',
  Mon: '월',
  Tue: '화',
  Wed: '수',
  Thu: '목',
  Fri: '금',
  Sat: '토',
};

const DATE_PARTS_FORMATTER = new Intl.DateTimeFormat('en-US', {
  timeZone: 'Asia/Seoul',
  month: 'numeric',
  day: 'numeric',
  weekday: 'short',
});

const TIME_PARTS_FORMATTER = new Intl.DateTimeFormat('en-US', {
  timeZone: 'Asia/Seoul',
  hour: 'numeric',
  minute: '2-digit',
  hour12: false,
});

export interface ScheduleDateParts {
  month: number;
  day: number;
  weekdayKo: string;
}

/** 일정 카드의 날짜 배지(월/일/요일)에 씁니다. */
export function getScheduleDateParts(startAt: string): ScheduleDateParts {
  const parts = DATE_PARTS_FORMATTER.formatToParts(new Date(startAt));
  const get = (type: string) => parts.find((part) => part.type === type)?.value ?? '';
  return {
    month: Number(get('month')),
    day: Number(get('day')),
    weekdayKo: EN_WEEKDAY_TO_KO[get('weekday')] ?? '',
  };
}

/** "2026-07-27T15:00:00+09:00" → "오후 3:00" */
export function formatScheduleTime(startAt: string): string {
  const parts = TIME_PARTS_FORMATTER.formatToParts(new Date(startAt));
  const get = (type: string) => parts.find((part) => part.type === type)?.value ?? '';
  const hour24 = Number(get('hour'));
  const minute = get('minute');
  const meridiem = hour24 < 12 ? '오전' : '오후';
  const remainder = hour24 % 12;
  const hour12 = remainder === 0 ? 12 : remainder;
  return `${meridiem} ${hour12}:${minute}`;
}

/**
 * 한국은 1988년 이후 서머타임이 없어 UTC+9 로 고정입니다. 그래서 오프셋을 상수로 둬도
 * 안전합니다.
 */
const SEOUL_OFFSET_MS = 9 * 60 * 60 * 1000;

/**
 * 서울 기준 시:분을 읽습니다.
 *
 * 화면의 시각 표시는 전부 Asia/Seoul 로 포맷하는데(TIME_PARTS_FORMATTER), 시간 선택
 * 휠이 Date.getHours() 같은 기기 로컬 시각을 쓰면 기기 타임존이 KST 가 아닐 때 둘이
 * 어긋납니다(예: UTC 기기에서 오후 7:00 일정이 휠에는 10시로 열림).
 */
export function getSeoulHourMinute(date: Date): { hour: number; minute: number } {
  const shifted = new Date(date.getTime() + SEOUL_OFFSET_MS);
  return { hour: shifted.getUTCHours(), minute: shifted.getUTCMinutes() };
}

/** base 의 서울 기준 날짜는 그대로 두고, 서울 기준 시:분만 바꾼 Date 를 만듭니다. */
export function withSeoulTime(base: Date, hour24: number, minute: number): Date {
  const shifted = new Date(base.getTime() + SEOUL_OFFSET_MS);
  const seoulMidnightUtc = Date.UTC(
    shifted.getUTCFullYear(),
    shifted.getUTCMonth(),
    shifted.getUTCDate(),
  );
  return new Date(seoulMidnightUtc + (hour24 * 60 + minute) * 60 * 1000 - SEOUL_OFFSET_MS);
}
