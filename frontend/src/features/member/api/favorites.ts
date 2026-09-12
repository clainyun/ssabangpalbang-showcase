import { authenticatedFetch } from '@/lib/authenticatedFetch';

interface ApiEnvelope<T> {
  success: boolean;
  code: string;
  message: string;
  data: T;
  timestamp: string;
}

export interface FavoritePage<T> {
  content: T[];
  totalElements: number;
  page: number;
  size: number;
  totalPages: number;
}

export interface FavoriteApartmentLatestTransaction {
  price: number;
  priceUnit: 'TEN_THOUSAND_KRW';
  exclusiveArea: number;
  dealDate: string;
}

export interface FavoriteApartment {
  apartmentId: number;
  name: string;
  address: string;
  districtName: string;
  dongName: string;
  householdCount: number | null;
  latestTransaction: FavoriteApartmentLatestTransaction | null;
  recruitingStudyCount: number;
  completedReportCount: number;
  favoritedAt: string;
  /** 단지 대표 이미지 공개 URL. 매칭 이미지가 없거나 미설정이면 null. */
  imageUrl: string | null;
}

export interface FavoriteReport {
  reportId: number;
  title: string;
  summary: string;
  analysisTags: string[];
  apartment: {
    apartmentId: number;
    name: string;
    address: string;
    /** 법정동 이름(예: 역삼동). 공공데이터 미매칭이면 null. */
    dongName: string | null;
    /** 단지 대표 이미지 공개 URL. 매칭 이미지가 없거나 미설정이면 null. */
    imageUrl: string | null;
  };
  favoritedByMe: boolean;
  completedAt: string | null;
  favoritedAt: string;
}

export interface ApartmentFavoriteActionResponse {
  apartmentId: number;
  favoritedByMe: boolean;
  favoriteCount: number;
}

export interface ReportFavoriteActionResponse {
  reportId: number;
  favoritedByMe: boolean;
  favoriteCount: number;
  favoritedAt?: string;
  unfavoritedAt?: string;
}

export class FavoritesApiError extends Error {
  code: string;

  constructor(code: string, message: string) {
    super(message);
    this.name = 'FavoritesApiError';
    this.code = code;
  }
}

async function get<T>(path: string): Promise<T> {
  const response = await authenticatedFetch(path, {
    method: 'GET',
    headers: { Accept: 'application/json' },
  });
  const body = (await response.json().catch(() => null)) as ApiEnvelope<T> | null;

  if (!response.ok || !body?.success || body.data === null) {
    throw new FavoritesApiError(
      body?.code ?? 'UNKNOWN',
      body?.message ?? '찜 목록을 불러오지 못했습니다.',
    );
  }

  return body.data;
}

async function mutate<T>(path: string, method: 'PUT' | 'DELETE'): Promise<T> {
  const response = await authenticatedFetch(path, {
    method,
    headers: { Accept: 'application/json' },
  });
  const body = (await response.json().catch(() => null)) as ApiEnvelope<T> | null;

  if (!response.ok || !body?.success || body.data === null) {
    throw new FavoritesApiError(
      body?.code ?? 'UNKNOWN',
      body?.message ?? '찜 상태를 변경하지 못했습니다.',
    );
  }

  return body.data;
}

export function getFavoriteApartments(
  page = 0,
  size = 20,
): Promise<FavoritePage<FavoriteApartment>> {
  const query = new URLSearchParams({
    page: page.toString(),
    size: size.toString(),
  });

  return get<FavoritePage<FavoriteApartment>>(
    `/api/v1/members/me/favorite-apartments?${query.toString()}`,
  );
}

export function getFavoriteReports(page = 0, size = 20): Promise<FavoritePage<FavoriteReport>> {
  const query = new URLSearchParams({
    page: page.toString(),
    size: size.toString(),
  });

  return get<FavoritePage<FavoriteReport>>(
    `/api/v1/members/me/favorite-reports?${query.toString()}`,
  );
}

export function favoriteApartment(apartmentId: number): Promise<ApartmentFavoriteActionResponse> {
  return mutate<ApartmentFavoriteActionResponse>(
    `/api/v1/apartments/${apartmentId}/favorite`,
    'PUT',
  );
}

export function unfavoriteApartment(apartmentId: number): Promise<ApartmentFavoriteActionResponse> {
  return mutate<ApartmentFavoriteActionResponse>(
    `/api/v1/apartments/${apartmentId}/favorite`,
    'DELETE',
  );
}

export function favoriteReport(reportId: number): Promise<ReportFavoriteActionResponse> {
  return mutate<ReportFavoriteActionResponse>(`/api/v1/reports/${reportId}/favorite`, 'PUT');
}

export function unfavoriteReport(reportId: number): Promise<ReportFavoriteActionResponse> {
  return mutate<ReportFavoriteActionResponse>(`/api/v1/reports/${reportId}/favorite`, 'DELETE');
}
