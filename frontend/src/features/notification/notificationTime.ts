const ISO_OFFSET_DATE_TIME_PATTERN =
  /\d{4}-\d{2}-\d{2}T\d{2}:\d{2}(?::\d{2}(?:\.\d{1,9})?)?(?:Z|[+-]\d{2}:\d{2})/g;

type SeoulDateTimeParts = {
  month: string;
  day: string;
  period: '오전' | '오후';
  hour: string;
  minute: string;
};

function getSeoulDateTimeParts(value: string): SeoulDateTimeParts | null {
  const date = new Date(value);
  if (Number.isNaN(date.getTime())) {
    return null;
  }

  const parts = new Intl.DateTimeFormat('en-US', {
    timeZone: 'Asia/Seoul',
    month: 'numeric',
    day: 'numeric',
    hour: 'numeric',
    minute: '2-digit',
    hour12: true,
  }).formatToParts(date);
  const part = (type: Intl.DateTimeFormatPartTypes): string =>
    parts.find((item) => item.type === type)?.value ?? '';
  return {
    month: part('month'),
    day: part('day'),
    period: part('dayPeriod').toUpperCase() === 'PM' ? '오후' : '오전',
    hour: part('hour'),
    minute: part('minute'),
  };
}

export function formatNotificationTime(sentAt: string): string {
  const parts = getSeoulDateTimeParts(sentAt);
  if (!parts) return sentAt;

  return `${parts.month}월 ${parts.day}일 ${parts.period} ${parts.hour}:${parts.minute}`;
}

export function formatNotificationBody(body: string, notificationType: string): string {
  if (notificationType === 'MESSAGE') return body;

  return body.replace(ISO_OFFSET_DATE_TIME_PATTERN, (value) => {
    const parts = getSeoulDateTimeParts(value);
    return parts
      ? `${parts.month}월 ${parts.day}일 ${parts.period} ${parts.hour}시 ${parts.minute}분`
      : value;
  });
}
