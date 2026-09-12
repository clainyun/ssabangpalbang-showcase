import { authenticatedFetch, rethrowSessionFailure } from '@/lib/authenticatedFetch';
import { useAuthStore } from '@/store/authStore';

export type CharacterId = 'PALBANG' | 'PALBANG_RABBIT' | 'PALBANG_DOG';
export type AgeGroup = 'TEENS' | 'TWENTIES' | 'THIRTIES' | 'FORTIES' | 'FIFTIES' | 'SIXTIES_PLUS';
export type Purpose = 'RESIDENCE' | 'INVESTMENT' | 'STUDY';
export type Priority =
  | 'TRANSPORT'
  | 'SAFETY'
  | 'EDUCATION'
  | 'COMMERCIAL'
  | 'WALKABILITY'
  | 'GREEN_SPACE'
  | 'PARKING'
  | 'NOISE';
export type MaritalStatus = 'SINGLE' | 'MARRIED';

export interface MyProfilePreference {
  purpose: Purpose | null;
  maritalStatus: MaritalStatus | null;
  hasVehicle: boolean | null;
  hasChildren: boolean | null;
  priorities: Priority[];
}

export interface MyProfileSummary {
  studyCount: number;
  reportCount: number;
  followingCount: number;
}

/** 리뷰 태그 표시용 최소 형태(FE-037). code는 프론트 카탈로그와 동일합니다. */
export interface ReviewTagDto {
  code: string;
  label: string;
  emoji: string;
}

/** 프로필 요약에 노출되는 상위 태그(카테고리·집계 횟수 포함). */
export interface ReviewTagSummary extends ReviewTagDto {
  category: 'PERSON' | 'VISIT';
  count: number;
}

export interface MemberReviewSummary {
  topTags: ReviewTagSummary[];
  likeReceivedCount: number;
  reviewCount: number;
}

export interface MemberReview {
  reviewId: number;
  tags: ReviewTagDto[];
  liked: boolean;
  content: string | null;
  createdAt: string;
}

export interface MemberReviewList {
  summary: MemberReviewSummary;
  content: MemberReview[];
  nextCursor: string | null;
  hasNext: boolean;
}

export interface MyProfile {
  memberId: number;
  email: string;
  nickname: string;
  profileImageUrl: string | null;
  selectedCharacterId: CharacterId;
  ageGroup: AgeGroup | null;
  ageGroupPublicAgreed: boolean;
  serviceNotificationAgreed: boolean;
  adNotificationAgreed: boolean;
  preference: MyProfilePreference | null;
  onboardingCompleted: boolean;
  joinedDays: number;
  /** 완료한 임장(현장 방문) 횟수. 마이페이지에서 가입 일수 대신 노출합니다(FE-037). */
  fieldVisitCompletedCount: number;
  summary: MyProfileSummary;
  reviewSummary: MemberReviewSummary;
  createdAt: string;
  updatedAt: string;
}

export type VisitScheduleStatus = 'SCHEDULED' | 'COMPLETED';

export interface MyVisitCalendarVisit {
  scheduleId: number;
  studyId: number;
  studyTitle: string;
  apartmentName: string;
  startAt: string;
  scheduleStatus: VisitScheduleStatus;
}

export interface MyVisitCalendarDate {
  date: string;
  visits: MyVisitCalendarVisit[];
}

export interface MyVisitCalendar {
  year: number;
  month: number;
  monthlyVisitCount: number;
  dates: MyVisitCalendarDate[];
}

export type MyStudyStatus = 'RECRUITING' | 'CLOSED' | 'IN_PROGRESS' | 'COMPLETED';
export type MyStudyFilterStatus = 'ALL' | 'ACTIVE' | 'IN_PROGRESS' | 'COMPLETED';
export type MyStudyRole = 'LEADER' | 'MEMBER';

export interface MyStudy {
  studyId: number;
  title: string;
  intro: string | null;
  goal: string;
  status: MyStudyStatus;
  role: MyStudyRole;
  apartment: {
    apartmentId: number;
    name: string;
  };
  nextSchedule: {
    scheduleId: number;
    startAt: string;
    meetingPlace: string | null;
  } | null;
  unreadChatCount: number;
  pendingReviewCount: number;
  readOnly: boolean;
  /** 현재 사용자가 실제로 돌아갈 수 있는 진행 중 임장 세션 보유 여부(서버 계산). */
  hasReturnableFieldVisit: boolean;
}

export interface MyReport {
  reportId: number;
  title: string | null;
  summary: string | null;
  analysisTags: string[];
  apartment: {
    apartmentId: number;
    name: string;
  };
  study: {
    studyId: number;
    title: string;
    visitedAt: string | null;
    participantCount: number;
  };
  status: string;
  favoritedByMe: boolean;
  canViewEvidence: boolean;
  completedAt: string | null;
}

export type PublicProfileSection = 'STUDIES' | 'REPORTS' | 'FOLLOWINGS';
export type PublicProfileStudyStatus = 'ACTIVE' | 'COMPLETED' | 'ALL';

export interface PublicProfileStudy {
  studyId: number;
  title: string;
  status: 'RECRUITING' | 'CLOSED' | 'IN_PROGRESS' | 'COMPLETED';
  role: 'LEADER' | 'MEMBER';
  apartment: {
    apartmentId: number;
    name: string;
  };
}

export interface PublicProfileReport {
  reportId: number;
  title: string | null;
  summary: string | null;
  analysisTags: string[];
  apartment: {
    apartmentId: number;
    name: string;
  };
  study: {
    studyId: number;
    title: string;
    participantCount: number;
  };
  favoritedByMe: boolean;
  completedAt: string | null;
}

export interface PublicProfileFollowing {
  memberId: number;
  nickname: string;
  profileImageUrl: string | null;
  selectedCharacterId: CharacterId;
  ageGroup: AgeGroup | null;
  participatingStudyCount: number;
  isMe: boolean;
  isFollowing: boolean;
  canFollow: boolean;
  canSendMessage: boolean;
  followedAt: string | null;
}

export interface MyFollowing {
  memberId: number;
  nickname: string;
  profileImageUrl: string | null;
  selectedCharacterId: CharacterId;
  ageGroup: AgeGroup | null;
  participatingStudyCount: number;
  isFollowing: boolean;
  canSendMessage: boolean;
  followedAt: string;
}

export interface MyFollowingList {
  content: MyFollowing[];
  totalCount: number;
  nextCursor: number | null;
  hasNext: boolean;
}

export interface FollowActionResponse {
  memberId: number;
  nickname: string;
  selectedCharacterId: CharacterId;
  isFollowing: boolean;
  canSendMessage: boolean;
  followingCount: number;
  followedAt?: string;
  unfollowedAt?: string;
}

export interface PublicProfile {
  memberId: number;
  nickname: string;
  profileImageUrl: string | null;
  selectedCharacterId: CharacterId;
  ageGroup: AgeGroup | null;
  reviewSummary: MemberReviewSummary;
  /** 완료한 임장 횟수(공개 프로필에도 임베드, FE-037). */
  fieldVisitCompletedCount: number;
  participatingStudyCount: number;
  reportCount: number;
  followingCount: number;
  isMe: boolean;
  isFollowing: boolean;
  canFollow: boolean;
  canSendMessage: boolean;
  section: PublicProfileSection;
  studies: PageData<PublicProfileStudy> | null;
  reports: PageData<PublicProfileReport> | null;
  followings: PageData<PublicProfileFollowing> | null;
}

export interface PageData<T> {
  content: T[];
  totalElements: number;
  page: number;
  size: number;
  totalPages: number;
}

export interface MyProfileUpdateRequest {
  nickname?: string;
  ageGroup?: AgeGroup;
  ageGroupPublicAgreed?: boolean;
  purpose?: Purpose;
  priorities?: Priority[];
  selectedCharacterId?: CharacterId;
  maritalStatus?: MaritalStatus;
  hasVehicle?: boolean;
  hasChildren?: boolean;
}

export interface MyProfileUpdateResponse {
  memberId: number;
  nickname: string;
  ageGroup: AgeGroup | null;
  ageGroupPublicAgreed: boolean;
  purpose: Purpose | null;
  priorities: Priority[];
  selectedCharacterId: CharacterId;
  maritalStatus: MaritalStatus | null;
  hasVehicle: boolean | null;
  hasChildren: boolean | null;
  updatedAt: string;
}

export type MyProfileUpdateErrorField =
  | 'nickname'
  | 'ageGroup'
  | 'ageGroupPublicAgreed'
  | 'purpose'
  | 'priorities'
  | 'selectedCharacterId'
  | 'maritalStatus'
  | 'hasVehicle'
  | 'hasChildren';

interface ApiEnvelope<T> {
  success: boolean;
  code: string;
  message: string;
  data: T | null;
}

interface FieldErrorData {
  field: string;
  reason: string;
}

const MY_PROFILE_UPDATE_ERROR_FIELDS: readonly MyProfileUpdateErrorField[] = [
  'nickname',
  'ageGroup',
  'ageGroupPublicAgreed',
  'purpose',
  'priorities',
  'selectedCharacterId',
  'maritalStatus',
  'hasVehicle',
  'hasChildren',
];

export class MyPageApiError extends Error {
  code: string;

  constructor(code: string, message: string) {
    super(message);
    this.name = 'MyPageApiError';
    this.code = code;
  }
}

export class MyProfileUpdateError extends MyPageApiError {
  field?: MyProfileUpdateErrorField;

  constructor(code: string, message: string, field?: MyProfileUpdateErrorField) {
    super(code, message);
    this.name = 'MyProfileUpdateError';
    this.field = field;
  }
}

function isFieldErrorData(data: unknown): data is FieldErrorData {
  return (
    typeof data === 'object' &&
    data !== null &&
    'field' in data &&
    'reason' in data &&
    typeof (data as FieldErrorData).field === 'string' &&
    typeof (data as FieldErrorData).reason === 'string'
  );
}

function toMyProfileUpdateErrorField(field: string): MyProfileUpdateErrorField | undefined {
  return MY_PROFILE_UPDATE_ERROR_FIELDS.includes(field as MyProfileUpdateErrorField)
    ? (field as MyProfileUpdateErrorField)
    : undefined;
}

async function get<T>(path: string): Promise<T> {
  const response = await authenticatedFetch(path, {
    method: 'GET',
    headers: { Accept: 'application/json' },
  });
  const body = (await response.json().catch(() => null)) as ApiEnvelope<T> | null;

  if (!response.ok || !body?.success || body.data === null) {
    throw new MyPageApiError(
      body?.code ?? 'UNKNOWN',
      body?.message ?? '마이페이지 정보를 불러오지 못했습니다.',
    );
  }

  return body.data;
}

async function mutate<T>(path: string, method: 'PUT' | 'DELETE'): Promise<T> {
  const { accessToken, sessionVersion } = useAuthStore.getState();

  let response: Response;
  try {
    response = await authenticatedFetch(
      path,
      {
        method,
        headers: { Accept: 'application/json' },
      },
      {
        expectedSessionVersion: sessionVersion,
        fallbackAccessToken: accessToken ?? undefined,
      },
    );
  } catch (error) {
    rethrowSessionFailure(error);
    throw new MyPageApiError('NETWORK_ERROR', '네트워크 연결을 확인해 주세요.');
  }

  const body = (await response.json().catch(() => null)) as ApiEnvelope<T> | null;

  if (!response.ok || !body?.success || body.data === null) {
    throw new MyPageApiError(
      body?.code ?? 'UNKNOWN',
      body?.message ?? '팔로우 상태를 변경하지 못했습니다.',
    );
  }

  return body.data;
}

export function getMyProfile(): Promise<MyProfile> {
  return get<MyProfile>('/api/v1/members/me');
}

export function getMemberReviews(
  memberId: number,
  cursor?: string,
  size = 20,
): Promise<MemberReviewList> {
  const query = new URLSearchParams({ size: size.toString() });
  if (cursor) query.set('cursor', cursor);

  return get<MemberReviewList>(`/api/v1/members/${memberId}/reviews?${query.toString()}`);
}

export function getMyVisitCalendar(year: number, month: number): Promise<MyVisitCalendar> {
  const query = new URLSearchParams({
    year: year.toString(),
    month: month.toString(),
  });

  return get<MyVisitCalendar>(`/api/v1/members/me/visit-calendar?${query.toString()}`);
}

export function getMyStudies(
  page = 0,
  size = 20,
  status: MyStudyFilterStatus = 'ALL',
): Promise<PageData<MyStudy>> {
  const query = new URLSearchParams({
    status,
    page: page.toString(),
    size: size.toString(),
  });

  return get<PageData<MyStudy>>(`/api/v1/members/me/studies?${query.toString()}`);
}

/**
 * 프론트와 백엔드를 순차 배포해도 전역 임장 복귀 진입점이 사라지지 않게 합니다.
 * 새 서버는 정확한 IN_PROGRESS 필터를 사용하고, 구버전 서버가 해당 필터를 거절하면
 * 기존 ACTIVE 계약의 모든 페이지를 읽은 뒤 같은 조건으로 클라이언트에서 좁힙니다.
 */
export async function getMyInProgressStudies(page = 0, size = 20): Promise<PageData<MyStudy>> {
  try {
    return await getMyStudies(page, size, 'IN_PROGRESS');
  } catch (error) {
    if (!(error instanceof MyPageApiError) || error.code !== 'MEMBER_STUDY_STATUS_INVALID') {
      throw error;
    }
  }

  const fallbackPageSize = 100;
  const firstPage = await getMyStudies(0, fallbackPageSize, 'ACTIVE');
  const activeStudies = [...firstPage.content];

  for (let nextPage = 1; nextPage < firstPage.totalPages; nextPage += 1) {
    const next = await getMyStudies(nextPage, fallbackPageSize, 'ACTIVE');
    activeStudies.push(...next.content);
  }

  const inProgressStudies = activeStudies.filter((study) => study.status === 'IN_PROGRESS');

  return {
    content: inProgressStudies,
    totalElements: inProgressStudies.length,
    page: 0,
    size: Math.max(inProgressStudies.length, 1),
    totalPages: inProgressStudies.length === 0 ? 0 : 1,
  };
}

export function getMyReports(page = 0, size = 20): Promise<PageData<MyReport>> {
  const query = new URLSearchParams({
    page: page.toString(),
    size: size.toString(),
  });

  return get<PageData<MyReport>>(`/api/v1/members/me/reports?${query.toString()}`);
}

export function getMyFollowings(cursor?: number, size = 20): Promise<MyFollowingList> {
  const query = new URLSearchParams({ size: size.toString() });
  if (cursor !== undefined) query.set('cursor', cursor.toString());

  return get<MyFollowingList>(`/api/v1/members/me/followings?${query.toString()}`);
}

export function followMember(memberId: number): Promise<FollowActionResponse> {
  return mutate<FollowActionResponse>(`/api/v1/members/${memberId}/follow`, 'PUT');
}

export function unfollowMember(memberId: number): Promise<FollowActionResponse> {
  return mutate<FollowActionResponse>(`/api/v1/members/${memberId}/follow`, 'DELETE');
}

export function getPublicProfile(
  memberId: number,
  section: PublicProfileSection,
  page = 0,
  size = 20,
  studyStatus: PublicProfileStudyStatus = 'ALL',
): Promise<PublicProfile> {
  const query = new URLSearchParams({
    section,
    page: page.toString(),
    size: size.toString(),
  });
  if (section === 'STUDIES') query.set('studyStatus', studyStatus);

  return get<PublicProfile>(`/api/v1/members/${memberId}?${query.toString()}`);
}

export async function updateMyProfile(
  request: MyProfileUpdateRequest,
): Promise<MyProfileUpdateResponse> {
  const { accessToken, sessionVersion } = useAuthStore.getState();

  let response: Response;
  try {
    response = await authenticatedFetch(
      '/api/v1/members/me',
      {
        method: 'PATCH',
        headers: {
          Accept: 'application/json',
          'Content-Type': 'application/json',
        },
        body: JSON.stringify(request),
      },
      {
        expectedSessionVersion: sessionVersion,
        fallbackAccessToken: accessToken ?? undefined,
      },
    );
  } catch (error) {
    rethrowSessionFailure(error);
    throw new MyProfileUpdateError('NETWORK_ERROR', '네트워크 연결을 확인해 주세요.');
  }

  const body = (await response.json().catch(() => null)) as ApiEnvelope<
    MyProfileUpdateResponse | FieldErrorData
  > | null;

  if (!response.ok || !body?.success || body.data === null) {
    const code = body?.code ?? 'UNKNOWN';

    if (isFieldErrorData(body?.data)) {
      throw new MyProfileUpdateError(
        code,
        body.data.reason,
        toMyProfileUpdateErrorField(body.data.field),
      );
    }
    if (code === 'AUTH_NICKNAME_DUPLICATED') {
      throw new MyProfileUpdateError(
        code,
        body?.message || '이미 사용 중인 닉네임입니다.',
        'nickname',
      );
    }
    if (code === 'MEMBER_CHARACTER_INVALID') {
      throw new MyProfileUpdateError(
        code,
        body?.message || '선택할 수 없는 캐릭터입니다.',
        'selectedCharacterId',
      );
    }
    if (code === 'MEMBER_AGE_GROUP_INVALID') {
      throw new MyProfileUpdateError(
        code,
        body?.message || '선택할 수 없는 연령대입니다.',
        'ageGroup',
      );
    }
    if (code === 'MEMBER_PRIORITY_DUPLICATED') {
      throw new MyProfileUpdateError(
        code,
        body?.message || '같은 우선순위를 중복해서 선택할 수 없습니다.',
        'priorities',
      );
    }
    throw new MyProfileUpdateError(
      code,
      body?.message || '프로필을 저장하지 못했습니다. 잠시 후 다시 시도해 주세요.',
    );
  }

  return body.data as MyProfileUpdateResponse;
}
