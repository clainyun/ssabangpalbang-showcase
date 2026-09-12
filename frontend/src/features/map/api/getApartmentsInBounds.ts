import { authenticatedFetch } from '@/lib/authenticatedFetch';

/**
 * GET /api/v1/apartments/bounds — 지도 가시 영역 아파트 조회 (FE-006 / BE-004).
 * 서버 원본: docs/API.md "지도 가시 영역 아파트 조회"
 */
export interface ApartmentBoundsTransaction {
  /** 만 원 단위 */
  price: number;
  priceUnit: 'TEN_THOUSAND_KRW';
  /** ㎡ */
  exclusiveArea: number;
  dealDate: string;
}

export interface ApartmentBoundsItem {
  apartmentId: number;
  name: string;
  address: string;
  latitude: number;
  longitude: number;
  latestTransaction: ApartmentBoundsTransaction | null;
  latestTransactionAvailable: boolean;
  recruitingStudyCount: number;
  completedReportCount: number;
  favoritedByMe: boolean;
}

export interface MapBounds {
  southWestLat: number;
  southWestLng: number;
  northEastLat: number;
  northEastLng: number;
}

interface ApiEnvelope<T> {
  success: boolean;
  code: string;
  message: string;
  data: T | null;
}

interface ApartmentBoundsResponse {
  apartments: ApartmentBoundsItem[];
  count: number;
}

export async function getApartmentsInBounds(
  bounds: MapBounds,
  signal?: AbortSignal,
): Promise<ApartmentBoundsItem[]> {
  const query = new URLSearchParams({
    southWestLat: String(bounds.southWestLat),
    southWestLng: String(bounds.southWestLng),
    northEastLat: String(bounds.northEastLat),
    northEastLng: String(bounds.northEastLng),
  });

  // authenticatedFetch 를 써야 액세스 토큰이 만료됐을 때 자동으로 재발급하고 재시도합니다.
  // 생 fetch + 저장된 토큰으로 부르면 앱을 오래 켜 둔 뒤 401 로 조용히 실패합니다.
  // (fetchWithTimeout 도 내부에서 함께 적용되어 무응답 시 10초 뒤 끊깁니다.)
  const response = await authenticatedFetch(
    `/api/v1/apartments/bounds?${query.toString()}`,
    { method: 'GET', headers: { Accept: 'application/json' }, signal },
  );

  const body = (await response.json().catch(() => null)) as ApiEnvelope<ApartmentBoundsResponse> | null;

  if (!response.ok || body === null || !body.success || body.data === null) {
    throw new Error(body?.message || '지도 영역의 아파트를 불러오지 못했습니다.');
  }

  return body.data.apartments;
}
