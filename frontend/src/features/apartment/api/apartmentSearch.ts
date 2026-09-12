import { authenticatedFetch, rethrowSessionFailure } from '@/lib/authenticatedFetch';

import { ApartmentApiError } from './apartmentDetail';

interface ApiEnvelope<T> {
  success: boolean;
  code?: string;
  message?: string;
  data: T | null;
}

interface ApartmentSearchLocation {
  latitude: number;
  longitude: number;
  radiusMeters: number;
  locationName: string;
}

interface ApartmentLatestTransaction {
  price: number;
  priceUnit: string;
  exclusiveArea: number;
  dealDate: string;
}

export interface ApartmentSearchItem {
  apartmentId: number;
  name: string;
  address: string | null;
  districtName: string | null;
  dongName: string | null;
  latestTransaction: ApartmentLatestTransaction | null;
  latestTransactionAvailable: boolean;
  distanceMeters: number | null;
  favoritedByMe: boolean;
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

const NETWORK_ERROR_MESSAGE = '네트워크 연결을 확인해 주세요.';
const SEARCH_ERROR_MESSAGE = '단지 정보를 불러오지 못했어요.';

export async function searchApartmentsByDong(dongCode: string): Promise<ApartmentSearchResult> {
  const query = new URLSearchParams({
    dongCode,
    page: '0',
    size: '100',
  });

  let response: Response;

  try {
    response = await authenticatedFetch(`/api/v1/apartments?${query}`, {
      method: 'GET',
      headers: {
        Accept: 'application/json',
      },
    });
  } catch (error) {
    rethrowSessionFailure(error);
    throw new ApartmentApiError('COMMON_NETWORK_ERROR', NETWORK_ERROR_MESSAGE);
  }

  const body = (await response
    .json()
    .catch(() => null)) as ApiEnvelope<ApartmentSearchResult> | null;
  const responseMessage = body?.message?.trim();

  if (!response.ok || !body?.success || body.data === null || body.data === undefined) {
    throw new ApartmentApiError(
      body?.code ?? 'COMMON_NETWORK_ERROR',
      responseMessage || SEARCH_ERROR_MESSAGE,
    );
  }

  return body.data;
}
