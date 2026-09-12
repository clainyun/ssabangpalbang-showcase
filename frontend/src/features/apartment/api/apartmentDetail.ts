import { authenticatedFetch, rethrowSessionFailure } from '@/lib/authenticatedFetch';

/**
 * 아파트 상세·모집 스터디·공개 리포트·찜 API (FE-007 / BE-004).
 *
 * ⚠️ 타입은 docs/API.md 예시가 아니라 **백엔드 응답 DTO 실물**을 기준으로 정의했습니다.
 * 문서 예시와 실제 응답이 다른 부분이 있습니다 — 스터디 일정이 문서에는
 * `nextScheduleAt` 로 적혀 있지만 실제로는 `schedule` 객체입니다
 * (backend/.../apartment/dto/response/ApartmentStudyResponse.java).
 */

export interface ApartmentDetailLatestTransaction {
  transactionId: number;
  /** 만 원 단위 */
  price: number | null;
  priceUnit: 'TEN_THOUSAND_KRW';
  /** ㎡ */
  exclusiveArea: number | null;
  dealDate: string | null;
  floor: number | null;
}

export interface ApartmentDetail {
  apartmentId: number;
  name: string;
  address: string | null;
  districtCode: string | null;
  districtName: string | null;
  dongName: string | null;
  latitude: number | null;
  longitude: number | null;
  householdCount: number | null;
  /** 'YYYY-MM' */
  completionYearMonth: string | null;
  /** 검증된 단지 대표 이미지의 공개 URL. 미매칭이면 null입니다. */
  imageUrl: string | null;
  parkingSpaceCount: number | null;
  /** 세대당 주차대수. 서버가 문자열로 직렬화할 수 있어 number|string 을 모두 받습니다. */
  parkingSpacesPerHousehold: number | string | null;
  latestTransaction: ApartmentDetailLatestTransaction | null;
  latestTransactionAvailable: boolean;
  recruitingStudyCount: number | null;
  completedReportCount: number | null;
  favoritedByMe: boolean;
}

export interface ApartmentTransaction {
  transactionId: number;
  dealDate: string;
  /** 만 원 단위. 원천 데이터가 불완전한 경우 null일 수 있습니다. */
  price: number | null;
  priceUnit: 'TEN_THOUSAND_KRW';
  /** ㎡ */
  exclusiveArea: number | null;
  floor: number | null;
}

export type ApartmentTransactionSort =
  'DEAL_DATE_DESC' | 'DEAL_DATE_ASC' | 'PRICE_DESC' | 'PRICE_ASC' | 'AREA_DESC' | 'AREA_ASC';

export interface ApartmentTransactionQuery {
  exclusiveArea?: number;
  year?: number;
  sort?: ApartmentTransactionSort;
  page?: number;
  size?: number;
}

export type ApartmentStudyPurpose = 'RESIDENCE' | 'INVESTMENT' | 'STUDY';

export interface ApartmentStudySchedule {
  scheduleId: number;
  startAt: string;
  endAt: string | null;
  meetingPlace: string | null;
  /** 일정 시작일까지 남은 일수. 당일이면 0 */
  dDay: number;
}

export interface ApartmentStudy {
  studyId: number;
  status: string;
  title: string;
  intro: string | null;
  goal: string | null;
  purpose: ApartmentStudyPurpose | null;
  currentMemberCount: number;
  capacity: number | null;
  remainingCapacity: number;
  schedule: ApartmentStudySchedule | null;
  leader: {
    memberId: number;
    nickname: string;
    selectedCharacterId: string;
    ageGroup: string | null;
  } | null;
  applicationStatus: string | null;
  canApply: boolean;
  isMember: boolean;
  isLeader: boolean;
}

export interface ApartmentReport {
  reportId: number;
  title: string | null;
  analysisTags: string[];
  summary: string | null;
  completedAt: string | null;
  isAiGenerated: boolean;
  favoritedByMe: boolean;
}

export interface PageData<T> {
  content: T[];
  totalElements: number;
  page: number;
  size: number;
  totalPages: number;
}

interface ApiEnvelope<T> {
  success: boolean;
  code: string;
  message: string;
  data: T | null;
}

export class ApartmentApiError extends Error {
  code: string;

  constructor(code: string, message: string) {
    super(message);
    this.name = 'ApartmentApiError';
    this.code = code;
  }
}

async function get<T>(path: string, fallbackMessage: string): Promise<T> {
  let response: Response;
  try {
    response = await authenticatedFetch(path, {
      method: 'GET',
      headers: { Accept: 'application/json' },
    });
  } catch (error) {
    rethrowSessionFailure(error);
    throw new ApartmentApiError('NETWORK_ERROR', '네트워크 연결을 확인해 주세요.');
  }

  const body = (await response.json().catch(() => null)) as ApiEnvelope<T> | null;

  if (!response.ok || !body?.success || body.data === null) {
    throw new ApartmentApiError(body?.code ?? 'UNKNOWN', body?.message ?? fallbackMessage);
  }

  return body.data;
}

export function getApartmentDetail(apartmentId: number): Promise<ApartmentDetail> {
  return get<ApartmentDetail>(
    `/api/v1/apartments/${apartmentId}`,
    '아파트 정보를 불러오지 못했습니다.',
  );
}

export function getApartmentTransactions(
  apartmentId: number,
  params: ApartmentTransactionQuery = {},
): Promise<PageData<ApartmentTransaction>> {
  const query = new URLSearchParams();

  for (const [key, value] of Object.entries(params)) {
    if (value !== undefined) query.set(key, String(value));
  }

  return get<PageData<ApartmentTransaction>>(
    `/api/v1/apartments/${apartmentId}/transactions?${query.toString()}`,
    '최근 실거래 추이를 불러오지 못했습니다.',
  );
}

export function getApartmentStudies(
  apartmentId: number,
  page = 0,
  size = 20,
): Promise<PageData<ApartmentStudy>> {
  const query = new URLSearchParams({ page: String(page), size: String(size) });

  return get<PageData<ApartmentStudy>>(
    `/api/v1/apartments/${apartmentId}/studies?${query.toString()}`,
    '모집 중인 스터디를 불러오지 못했습니다.',
  );
}

export function getApartmentReports(
  apartmentId: number,
  page = 0,
  size = 20,
): Promise<PageData<ApartmentReport>> {
  const query = new URLSearchParams({ page: String(page), size: String(size) });

  return get<PageData<ApartmentReport>>(
    `/api/v1/apartments/${apartmentId}/reports?${query.toString()}`,
    '공개 리포트를 불러오지 못했습니다.',
  );
}
