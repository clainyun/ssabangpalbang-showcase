export interface District {
  districtCode: string;
  districtName: string;
}

export interface DistrictListResult {
  cityCode: string;
  cityName: string;
  districts: District[];
  totalCount: number;
}

export interface Dong {
  dongCode: string;
  dongName: string;
  hasApartment: boolean;
  apartmentCount: number;
}

export interface DongListResult {
  districtCode: string;
  districtName: string;
  dongs: Dong[];
  totalCount: number;
}

export class RegionApiError extends Error {
  constructor(
    public readonly code: string,
    message: string,
  ) {
    super(message);
    this.name = 'RegionApiError';
  }
}
