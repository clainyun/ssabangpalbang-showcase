import { useQuery } from '@tanstack/react-query';

import { ApartmentApiError } from './apartmentDetail';
import { searchApartmentsByDong } from './apartmentSearch';
import { searchApartments, type ApartmentSearchParams } from './searchApartments';

export function useApartmentsByDong(dongCode: string | null) {
  return useQuery({
    queryKey: ['apartments', 'search', { dongCode }] as const,
    queryFn: () => {
      if (dongCode === null) {
        throw new ApartmentApiError('COMMON_NETWORK_ERROR', '동을 먼저 선택해 주세요.');
      }

      return searchApartmentsByDong(dongCode);
    },
    enabled: dongCode !== null,
  });
}

type ApartmentRegionSearchParams = Pick<
  ApartmentSearchParams,
  'keyword' | 'districtCode' | 'dongCode'
>;

/**
 * 스터디 생성 화면의 구·동·이름 조건을 한 번에 서버 검색 API로 전달합니다.
 * 조건이 하나도 없을 때는 서버가 400을 반환하므로, 서울 전체 선택 상태에서는 검색어가 생길 때만 실행합니다.
 */
export function useApartmentRegionSearch(params: ApartmentRegionSearchParams) {
  const keyword = params.keyword?.trim() || undefined;
  const enabled = Boolean(keyword || params.districtCode || params.dongCode);

  return useQuery({
    queryKey: [
      'apartments',
      'study-create-search',
      { keyword, districtCode: params.districtCode, dongCode: params.dongCode },
    ] as const,
    queryFn: ({ signal }) =>
      searchApartments({
        keyword,
        districtCode: params.districtCode,
        dongCode: params.dongCode,
        page: 0,
        size: 100,
        signal,
      }),
    enabled,
  });
}
