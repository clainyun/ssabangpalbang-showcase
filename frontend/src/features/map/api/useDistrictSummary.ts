import { useQuery, type UseQueryResult } from '@tanstack/react-query';

import { getDistrictSummary, type DistrictSummary } from './getDistrictSummary';

const DISTRICT_SUMMARY_STALE_TIME = 1000 * 60 * 60;
const DISTRICT_SUMMARY_GC_TIME = 1000 * 60 * 60 * 24;

export function useDistrictSummary(): UseQueryResult<DistrictSummary, Error> {
  return useQuery({
    queryKey: ['apartment-district-summary'] as const,
    queryFn: ({ signal }) => getDistrictSummary(signal),
    staleTime: DISTRICT_SUMMARY_STALE_TIME,
    gcTime: DISTRICT_SUMMARY_GC_TIME,
    retry: 1,
  });
}
