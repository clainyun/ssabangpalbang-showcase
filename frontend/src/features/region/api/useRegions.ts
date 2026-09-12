import { useQuery } from '@tanstack/react-query';

import { getDistricts, getDongs } from './getRegions';
import { RegionApiError } from './types';

const REGION_STALE_TIME = 12 * 60 * 60 * 1000;

export function useDistricts() {
  return useQuery({
    queryKey: ['regions', 'districts'] as const,
    queryFn: getDistricts,
    staleTime: REGION_STALE_TIME,
  });
}

export function useDongs(districtCode: string | null) {
  return useQuery({
    queryKey: ['regions', 'districts', districtCode, 'dongs'] as const,
    queryFn: () => {
      if (districtCode === null) {
        throw new RegionApiError('COMMON_NETWORK_ERROR', '지역을 먼저 선택해 주세요.');
      }

      return getDongs(districtCode);
    },
    enabled: districtCode !== null,
    staleTime: REGION_STALE_TIME,
  });
}
