/**
 * 홈 화면 날짜/시간 표시 포맷터. `today`는 이미 Asia/Seoul 기준 날짜 문자열("YYYY-MM-DD")이고,
 * `startAt`은 Asia/Seoul 오프셋이 포함된 ISO datetime이라 파싱 후에도 명시적으로
 * timeZone: 'Asia/Seoul'로 포맷해서 기기 타임존 설정과 무관하게 항상 같은 값이 나오게 합니다.
 */

const EN_WEEKDAY_TO_KO: Record<string, string> = {
  Sun: '일',
  Mon: '월',
  Tue: '화',
  Wed: '수',
  Thu: '목',
  Fri: '금',
  Sat: '토',
};

const SEOUL_PARTS_FORMATTER = new Intl.DateTimeFormat('en-US', {
  timeZone: 'Asia/Seoul',
  weekday: 'short',
  hour: 'numeric',
  minute: '2-digit',
  hour12: false,
});

function getSeoulDateParts(isoString: string) {
  const parts = SEOUL_PARTS_FORMATTER.formatToParts(new Date(isoString));
  const get = (type: string) => parts.find((part) => part.type === type)?.value ?? '';

  return {
    weekdayKo: EN_WEEKDAY_TO_KO[get('weekday')] ?? '',
    hour24: Number(get('hour')),
    minute: get('minute'),
  };
}

function toAmPmHour(hour24: number): { meridiem: '오전' | '오후'; hour12: number } {
  const meridiem = hour24 < 12 ? '오전' : '오후';
  const remainder = hour24 % 12;
  return { meridiem, hour12: remainder === 0 ? 12 : remainder };
}

/** "2026-07-24" → "7월 24일" */
export function formatTodayLabel(todayDate: string): string {
  const [, month, day] = todayDate.split('-');
  return `${Number(month)}월 ${Number(day)}일`;
}

/** "2026-07-27T15:00:00+09:00" → "토 오후3시" */
export function formatVisitBadge(startAt: string): string {
  const { weekdayKo, hour24 } = getSeoulDateParts(startAt);
  const { meridiem, hour12 } = toAmPmHour(hour24);
  return `${weekdayKo} ${meridiem}${hour12}시`;
}

const SEOUL_DATE_FORMATTER = new Intl.DateTimeFormat('en-US', {
  timeZone: 'Asia/Seoul',
  month: 'numeric',
  day: 'numeric',
  weekday: 'short',
});

/** "2026-08-07T14:00:00+09:00" → "8월 7일 금요일" */
export function formatVisitDateBadge(startAt: string): string {
  const parts = SEOUL_DATE_FORMATTER.formatToParts(new Date(startAt));
  const get = (type: string) => parts.find((part) => part.type === type)?.value ?? '';
  const month = Number(get('month'));
  const day = Number(get('day'));
  const weekdayKo = EN_WEEKDAY_TO_KO[get('weekday')] ?? '';
  return `${month}월 ${day}일 ${weekdayKo}요일`;
}

/** "2026-07-27T15:00:00+09:00" → "오후 3:00" */
export function formatVisitTime(startAt: string): string {
  const { hour24, minute } = getSeoulDateParts(startAt);
  const { meridiem, hour12 } = toAmPmHour(hour24);
  return `${meridiem} ${hour12}:${minute}`;
}
