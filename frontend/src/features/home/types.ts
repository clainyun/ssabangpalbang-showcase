/**
 * GET /api/v1/home 응답. docs/API.md "홈 화면 통합 조회" 참고.
 * activeStudies·reports는 이 API에 포함되지 않음(문서상 의도적 제외) — 마이페이지·전용 API 몫.
 */
export interface HomeSummary {
  today: string;
  unreadNotificationCount: number;
  nextVisit: NextVisit;
  weather: HomeWeather;
  weatherAlerts: HomeWeatherAlerts;
}

export interface NextVisit {
  exists: boolean;
  /** 당일은 0. exists=false면 null. */
  dDay: number | null;
  /** 카드 탭 시 `/(app)/study/${studyId}`로 이동. */
  studyId: number | null;
  apartmentName: string | null;
  meetingPlace: string | null;
  /** Asia/Seoul 오프셋 포함 ISO datetime. */
  startAt: string | null;
  currentMemberCount: number | null;
  capacity: number | null;
}

export type FineDustGrade = 'GOOD' | 'NORMAL' | 'BAD' | 'VERY_BAD';
export type WeatherFreshness = 'FRESH' | 'STALE' | 'UNAVAILABLE';
export type WeatherLocationBasis = 'CURRENT_LOCATION' | 'NEXT_VISIT_APARTMENT' | 'DEFAULT_LOCATION';

export interface HomeWeather {
  available: boolean;
  temperatureCelsius: number | null;
  /** 프론트 아이콘 매핑용 코드(예: CLEAR_SKY). */
  conditionCode: string | null;
  conditionText: string | null;
  iconKey: string | null;
  rainProbability: number | null;
  fineDustValue: number | null;
  fineDustGrade: FineDustGrade | null;
  observedAt: string | null;
  locationName: string;
  locationBasis: WeatherLocationBasis;
  source: string | null;
  freshness: WeatherFreshness;
}

export type WeatherAlertType =
  | 'STRONG_WIND'
  | 'HEAVY_RAIN'
  | 'COLD_WAVE'
  | 'DRY'
  | 'STORM_SURGE'
  | 'HIGH_WAVES'
  | 'TYPHOON'
  | 'HEAVY_SNOW'
  | 'YELLOW_DUST'
  | 'HEAT_WAVE';

export type WeatherAlertLevel = 'ADVISORY' | 'WARNING' | 'SEVERE_WARNING';

export interface WeatherAlertItem {
  type: WeatherAlertType;
  level: WeatherAlertLevel;
  /** 화면 표시용 특보명. 예: "폭염주의보". */
  title: string;
  areaCode: string;
  areaName: string;
  issuedAt: string;
  effectiveAt: string;
}

export interface HomeWeatherAlerts {
  available: boolean;
  /** 발효 중 특보 없으면 빈 배열. SEVERE_WARNING > WARNING > ADVISORY 순 정렬. */
  items: WeatherAlertItem[];
  source: string;
  freshness: WeatherFreshness;
}

export type HomeApiErrorCode =
  | 'HOME_LOCATION_INCOMPLETE'
  | 'HOME_LATITUDE_INVALID'
  | 'HOME_LONGITUDE_INVALID'
  | 'AUTH_ACCESS_TOKEN_INVALID'
  | 'MEMBER_NOT_FOUND'
  | 'COMMON_INTERNAL_SERVER_ERROR'
  | 'NETWORK_ERROR'
  | 'UNKNOWN';

export class HomeApiError extends Error {
  code: HomeApiErrorCode | string;

  constructor(code: HomeApiErrorCode | string, message: string) {
    super(message);
    this.code = code;
    this.name = 'HomeApiError';
  }
}
