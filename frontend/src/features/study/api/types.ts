export type StudyPurpose = 'RESIDENCE' | 'INVESTMENT' | 'STUDY';

export interface CreateStudyInput {
  apartmentId: number;
  title: string;
  intro?: string;
  goal: string;
  capacity: number;
  purpose: StudyPurpose;
}

export interface StudyCreateResult {
  studyId: number;
  title: string;
  intro: string | null;
  goal: string;
  purpose: StudyPurpose;
  status: string;
  capacity: number;
  currentMemberCount: number;
  apartment: {
    apartmentId: number;
    name: string;
    address: string | null;
  };
  leader: {
    memberId: number;
    nickname: string;
    selectedCharacterId: string;
  };
  createdAt: string;
}

export class StudyApiError extends Error {
  constructor(
    public readonly code: string,
    message: string,
  ) {
    super(message);
    this.name = 'StudyApiError';
  }
}

export interface ApiEnvelope<T> {
  success: boolean;
  code: string;
  message: string;
  data: T | null;
}

// ── FE-011: 신청자·멤버 관리·마감·강퇴 ──────────────────────────────────

export type ApplicationStatus = 'PENDING' | 'APPROVED' | 'REJECTED';

export interface ApplicantSummary {
  memberId: number;
  nickname: string;
  selectedCharacterId: string;
}

export interface StudyApplication {
  applicationId: number;
  applicant: ApplicantSummary;
  intro: string;
  purpose: StudyPurpose;
  status: ApplicationStatus;
  canApprove: boolean;
  canReject: boolean;
  createdAt: string;
  decidedAt: string | null;
}

export interface ApplicationListSummary {
  pendingCount: number;
  approvedCount: number;
  rejectedCount: number;
  currentMemberCount: number;
  capacity: number;
}

export interface ApplicationListResult {
  content: StudyApplication[];
  summary: ApplicationListSummary;
  nextCursor: number | null;
  hasNext: boolean;
}

/** 승인/거절 응답 — 승인만 currentMemberCount·capacity·studyStatus를 함께 반환합니다. */
export interface ApplicationDecisionResult {
  applicationId: number;
  studyId: number;
  applicant: ApplicantSummary;
  status: ApplicationStatus;
  decidedAt: string;
  currentMemberCount?: number;
  capacity?: number;
  studyStatus?: string;
}

export type StudyMemberRole = 'LEADER' | 'MEMBER';

export interface StudyMember {
  memberId: number;
  nickname: string;
  profileImageUrl: string | null;
  selectedCharacterId: string;
  role: StudyMemberRole;
  canKick: boolean;
  reviewedByMe: boolean;
  joinedAt: string;
}

export interface StudyMemberListResult {
  studyId: number;
  isLeader: boolean;
  currentMemberCount: number;
  capacity: number;
  members: StudyMember[];
}

/** 리뷰 응답에 실려 오는 태그(FE-037). code는 프론트 카탈로그와 동일합니다. */
export interface ReviewTag {
  code: string;
  label: string;
  emoji: string;
}

export interface MemberReviewCreateRequest {
  tags: string[];
  liked: boolean;
  content?: string | null;
}

export interface MemberReviewCreateResult {
  reviewId: number;
  studyId: number;
  reviewedMemberId: number;
  tags: ReviewTag[];
  liked: boolean;
  content: string | null;
  createdAt: string;
}

export interface KickMemberResult {
  studyId: number;
  memberId: number;
  currentMemberCount: number;
  capacity: number;
  studyStatus: string;
  kickedAt: string;
}

export interface LeaveStudyResult {
  studyId: number;
  memberId: number;
  currentMemberCount: number;
  capacity: number;
  studyStatus: string;
  leftAt: string;
}

export interface UpdateStudyDetailsInput {
  title?: string;
  goal?: string;
  intro?: string;
}

export interface UpdateStudyDetailsResult {
  studyId: number;
  title: string;
  goal: string;
  intro: string | null;
}

export interface RecruitmentCloseResult {
  studyId: number;
  status: string;
  currentMemberCount: number;
  capacity: number;
  pendingApplicationCount: number;
  recruitmentClosedAt: string;
}

// ── FE-012: 임장 일정 등록·수정·삭제 (스터디당 일정 1개) ──────────────────

export type ScheduleStatus = 'SCHEDULED' | 'COMPLETED' | 'CANCELED';

export interface ScheduleCalendarEvent {
  title: string;
  startAt: string;
  endAt: string | null;
  location: string;
}

export interface ScheduleItem {
  scheduleId: number;
  status: ScheduleStatus;
  startAt: string;
  endAt: string | null;
  meetingPlace: string;
  calendarEvent: ScheduleCalendarEvent;
  createdAt: string;
  updatedAt: string;
}

export interface StudyScheduleDetail {
  studyId: number;
  studyTitle: string;
  studyStatus: string;
  isLeader: boolean;
  canManageSchedule: boolean;
  schedule: ScheduleItem | null;
}

export interface ScheduleInput {
  startAt: string;
  endAt?: string;
  meetingPlace: string;
}

export interface ScheduleUpdateInput {
  startAt?: string;
  endAt?: string;
  meetingPlace?: string;
}

export interface ScheduleMutationResult {
  scheduleId: number;
  studyId: number;
  status: ScheduleStatus;
  startAt: string;
  endAt: string | null;
  meetingPlace: string;
  createdAt?: string;
  updatedAt: string;
}

export interface ScheduleDeleteResult {
  studyId: number;
  scheduleId: number;
  status: ScheduleStatus;
  updatedAt: string;
}

// ── 스터디 신청 (비멤버 → PENDING) ─────────────────────────────────────

export interface ApplyToStudyInput {
  intro: string;
  purpose: StudyPurpose;
}

export interface ApplyToStudyResult {
  applicationId: number;
  studyId: number;
  applicant: ApplicantSummary;
  intro: string;
  purpose: StudyPurpose;
  status: 'PENDING';
  createdAt: string;
}

// ── 스터디 공지 등록·조회·수정·삭제 (스터디장만 작성 가능) ────────────────

export interface StudyNotice {
  noticeId: number;
  content: string;
  canEdit: boolean;
  canDelete: boolean;
  createdAt: string;
  updatedAt: string;
}

export interface StudyNoticeListResult {
  studyId: number;
  isLeader: boolean;
  readOnly: boolean;
  content: StudyNotice[];
  nextCursor: number | null;
  hasNext: boolean;
}

export interface StudyNoticeMutationResult {
  noticeId: number;
  studyId: number;
  content: string;
  createdAt: string;
  updatedAt: string;
}

export interface StudyNoticeDeleteResult {
  studyId: number;
  noticeId: number;
  deletedAt: string;
}
