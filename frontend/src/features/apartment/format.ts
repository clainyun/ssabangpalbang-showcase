/**
 * 아파트 정보 표시 포맷터. 지도 마커와 아파트 상세가 같은 규칙으로 보여주기 위해
 * 한곳에 모아둡니다.
 *
 * 서버는 가격을 만 원 단위 정수, 전용면적을 ㎡ 로 줍니다(docs/API.md).
 * 억 단위 축약은 프론트 책임입니다.
 */

/**
 * 전용면적 표기. 서버 단위(㎡)를 그대로 씁니다. 예: 84㎡
 *
 * 찜 목록(`wishlist.tsx`)과 아파트 상세(`ApartmentFactBubbles.tsx`)가 이미 ㎡ 로 보여주고
 * 있어서, 지도 마커만 평으로 나가던 것을 여기에 맞췄습니다. 소수점은 버립니다 —
 * 마커 라벨 자리가 좁고 찜 목록도 Math.round 를 씁니다.
 */
export function formatAreaLabel(exclusiveAreaSquareMeters: number): string {
  return `${Math.round(exclusiveAreaSquareMeters)}㎡`;
}

/** 지도 마커처럼 좁은 자리에 쓰는 짧은 표기. 예: 24.8억 / 8,500만 */
export function formatPriceLabel(priceInTenThousandWon: number): string {
  const eok = priceInTenThousandWon / 10000;

  return eok >= 1 ? `${eok.toFixed(1)}억` : `${priceInTenThousandWon.toLocaleString()}만`;
}

/** 상세 화면처럼 정확히 읽어야 하는 자리에 쓰는 표기. 예: 24억 8,000만 원 */
export function formatPriceDetail(priceInTenThousandWon: number): string {
  const eok = Math.floor(priceInTenThousandWon / 10000);
  const man = priceInTenThousandWon % 10000;

  if (eok === 0) return `${man.toLocaleString()}만 원`;
  if (man === 0) return `${eok}억 원`;

  return `${eok}억 ${man.toLocaleString()}만 원`;
}

/**
 * 목록 카드용 짧은 주소. 서버는 '서울특별시 강남구 테헤란로48길 10' 처럼 전체 주소를
 * 주는데, 목록에서는 시·구가 반복돼 정보량이 없으므로 떼고 도로명만 남깁니다.
 */
export function formatShortAddress(address: string | null): string {
  if (address === null) return '';

  return address.replace(/^서울특별시\s+\S+구\s+/, '').trim();
}

/**
 * 검색 결과의 현재 위치 기준 거리. 1km 미만은 미터로, 그 이상은 소수점 한 자리
 * km 로 보여 줍니다. 위치를 함께 보내지 않으면 서버가 null 을 주므로 그대로 null.
 */
export function formatDistance(distanceMeters: number | null): string | null {
  if (distanceMeters === null) return null;
  if (distanceMeters < 1000) return `${Math.round(distanceMeters)}m`;

  return `${(distanceMeters / 1000).toFixed(1)}km`;
}

/** '2026-06-15' → '2026.06.15' */
export function formatDealDate(dealDate: string): string {
  return dealDate.replaceAll('-', '.');
}

/** '2012-12' → '2012년 12월' */
export function formatCompletionYearMonth(completionYearMonth: string): string {
  const [year, month] = completionYearMonth.split('-');
  if (!year || !month) return completionYearMonth;

  return `${year}년 ${Number(month)}월`;
}

/** 층. 서버가 null 을 줄 수 있어(정보 미제공) 호출부에서 분기하지 않게 여기서 처리합니다. */
export function formatFloor(floor: number | null): string {
  return floor === null ? '정보 없음' : `${floor}층`;
}

/**
 * 세대당 주차대수. 서버가 BigDecimal 을 문자열로 직렬화할 수 있어 둘 다 받습니다.
 * 값이 없으면 null 을 돌려주고, 표시 문구는 호출부가 정합니다.
 */
export function formatParkingPerHousehold(value: number | string | null): string | null {
  if (value === null) return null;

  const parsed = typeof value === 'number' ? value : Number(value);
  if (Number.isNaN(parsed)) return null;

  return `${parsed.toFixed(2)}대`;
}

/** ISO 8601 → '2026.07.27 15:00' (Asia/Seoul 오프셋이 담긴 문자열을 그대로 파싱) */
export function formatDateTime(isoString: string): string {
  const date = new Date(isoString);
  if (Number.isNaN(date.getTime())) return isoString;

  const pad = (value: number) => String(value).padStart(2, '0');

  return `${date.getFullYear()}.${pad(date.getMonth() + 1)}.${pad(date.getDate())} ${pad(date.getHours())}:${pad(date.getMinutes())}`;
}

/** ISO 8601 → '2026.07.22' */
export function formatDate(isoString: string): string {
  const date = new Date(isoString);
  if (Number.isNaN(date.getTime())) return isoString;

  const pad = (value: number) => String(value).padStart(2, '0');

  return `${date.getFullYear()}.${pad(date.getMonth() + 1)}.${pad(date.getDate())}`;
}

/** D-day 배지 문구. 당일이면 'D-DAY' */
export function formatDDay(dDay: number): string {
  if (dDay === 0) return 'D-DAY';

  return dDay > 0 ? `D-${dDay}` : `D+${Math.abs(dDay)}`;
}

const PURPOSE_LABELS: Record<string, string> = {
  RESIDENCE: '실거주',
  INVESTMENT: '투자',
  STUDY: '공부',
};

export function formatPurpose(purpose: string | null): string | null {
  return purpose === null ? null : (PURPOSE_LABELS[purpose] ?? purpose);
}
