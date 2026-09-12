import { authenticatedFetch, rethrowSessionFailure } from '@/lib/authenticatedFetch';

import { type DistrictListResult, type DongListResult, RegionApiError } from './types';

interface ApiEnvelope<T> {
  success: boolean;
  code?: string;
  message?: string;
  data: T | null;
}

const NETWORK_ERROR_MESSAGE = '네트워크 연결을 확인해 주세요.';
const REGION_ERROR_MESSAGE = '지역 정보를 불러오지 못했어요.';

async function getRegionResult<T>(path: string): Promise<T> {
  let response: Response;

  try {
    response = await authenticatedFetch(path, {
      method: 'GET',
      headers: {
        Accept: 'application/json',
      },
    });
  } catch (error) {
    rethrowSessionFailure(error);
    throw new RegionApiError('COMMON_NETWORK_ERROR', NETWORK_ERROR_MESSAGE);
  }

  const body = (await response.json().catch(() => null)) as ApiEnvelope<T> | null;
  const responseMessage = body?.message?.trim();

  if (!response.ok || !body?.success || body.data === null || body.data === undefined) {
    throw new RegionApiError(
      body?.code ?? 'COMMON_NETWORK_ERROR',
      responseMessage || REGION_ERROR_MESSAGE,
    );
  }

  return body.data;
}

export function getDistricts(): Promise<DistrictListResult> {
  return getRegionResult('/api/v1/regions/districts');
}

export function getDongs(districtCode: string): Promise<DongListResult> {
  return getRegionResult(`/api/v1/regions/districts/${encodeURIComponent(districtCode)}/dongs`);
}
