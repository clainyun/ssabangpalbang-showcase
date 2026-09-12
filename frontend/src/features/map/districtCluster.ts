import type { FeatureCollection } from 'geojson';

import type { DistrictSummary } from './api/getDistrictSummary';

export interface DistrictCard {
  districtCode: string;
  districtName: string;
  apartmentCount: number;
  latitude: number;
  longitude: number;
}

export const EMPTY_FEATURE_COLLECTION: FeatureCollection = {
  type: 'FeatureCollection',
  features: [],
};

export const EMPTY_DISTRICT_CARDS: DistrictCard[] = [];

export function buildDistrictCards(summary: DistrictSummary): DistrictCard[] {
  return summary.districts.flatMap((district) => {
    if (
      district.apartmentCount === 0 ||
      district.centerLatitude === null ||
      district.centerLongitude === null
    ) {
      return [];
    }

    return [
      {
        districtCode: district.districtCode,
        districtName: district.districtName,
        apartmentCount: district.apartmentCount,
        latitude: district.centerLatitude,
        longitude: district.centerLongitude,
      },
    ];
  });
}
