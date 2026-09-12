import { authenticatedFetch } from '@/lib/authenticatedFetch';

export interface DistrictSummaryItem {
  districtCode: string;
  districtName: string;
  apartmentCount: number;
  centerLatitude: number | null;
  centerLongitude: number | null;
}

export interface DistrictSummary {
  districts: DistrictSummaryItem[];
  totalCount: number;
  totalApartmentCount: number;
}

interface ApiEnvelope<T> {
  success: boolean;
  code: string;
  message: string;
  data: T | null;
}

export async function getDistrictSummary(signal?: AbortSignal): Promise<DistrictSummary> {
  const response = await authenticatedFetch('/api/v1/apartments/districts/summary', {
    method: 'GET',
    headers: { Accept: 'application/json' },
    signal,
  });

  const body = (await response.json().catch(() => null)) as ApiEnvelope<DistrictSummary> | null;

  if (!response.ok || body === null || !body.success || body.data === null) {
    throw new Error(body?.message || '자치구별 집계를 불러오지 못했습니다.');
  }

  return body.data;
}
