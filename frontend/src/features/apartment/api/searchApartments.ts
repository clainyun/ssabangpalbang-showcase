import { authenticatedFetch } from '@/lib/authenticatedFetch';

/**
 * GET /api/v1/apartments — 아파트 검색 (FE-040 / BE-041).
 *
 * 지도 탭의 기본 목록은 가시 영역 조회(`/apartments/bounds`, BE-004)를 쓰고,
 * 검색어를 입력했을 때만 이 API 로 전환합니다. 두 API 는 응답 형태가 달라서
 * (여기는 페이지네이션 + 거리 정보) 별도 모듈로 둡니다.
 */
export interface ApartmentSearchTransaction {
  /** 만 원 단위 */
  price: number;
  priceUnit: 'TEN_THOUSAND_KRW';
  /** ㎡ */
  exclusiveArea: number;
  dealDate: string;
}

export interface ApartmentSearchItem {
  apartmentId: number;
  name: string;
  address: string;
  districtName: string;
  dongName: string;
  /** 좌표가 없는 단지가 있을 수 있어 nullable 입니다. 마커는 값이 있는 것만 찍습니다. */
  latitude: number | null;
  longitude: number | null;
  latestTransaction: ApartmentSearchTransaction | null;
  latestTransactionAvailable: boolean;
  /** 위치를 함께 보냈을 때만 채워집니다. */
  distanceMeters: number | null;
  favoritedByMe: boolean;
}

export interface ApartmentSearchLocation {
  latitude: number | null;
  longitude: number | null;
  radiusMeters: number | null;
  /** 예: "옥수동" */
  locationName: string | null;
}

export interface ApartmentSearchResult {
  searchMode: 'KEYWORD' | 'REGION' | 'NEARBY';
  currentLocation: ApartmentSearchLocation | null;
  content: ApartmentSearchItem[];
  totalElements: number;
  page: number;
  size: number;
  totalPages: number;
}

export interface ApartmentSearchParams {
  keyword?: string;
  districtCode?: string;
  dongCode?: string;
  latitude?: number;
  longitude?: number;
  radiusMeters?: number;
  page?: number;
  size?: number;
  signal?: AbortSignal;
}

interface ApiEnvelope<T> {
  success: boolean;
  code: string;
  message: string;
  data: T | null;
}

export async function searchApartments(
  params: ApartmentSearchParams,
): Promise<ApartmentSearchResult> {
  const { signal, ...queryParams } = params;
  const query = new URLSearchParams();

  // 서버는 keyword / 지역코드 / 위경도 중 최소 하나를 요구합니다(없으면 400).
  // undefined 인 값은 아예 빼야 "" 로 전송돼 조건으로 잡히는 일이 없습니다.
  for (const [key, value] of Object.entries(queryParams)) {
    if (value === undefined || value === null || value === '') continue;
    query.set(key, String(value));
  }

  const response = await authenticatedFetch(`/api/v1/apartments?${query.toString()}`, {
    method: 'GET',
    headers: { Accept: 'application/json' },
    signal,
  });

  const body = (await response.json().catch(() => null)) as
    | ApiEnvelope<ApartmentSearchResult>
    | null;

  if (!response.ok || body === null || !body.success || body.data === null) {
    throw new Error(body?.message || '아파트를 검색하지 못했습니다.');
  }

  return body.data;
}
