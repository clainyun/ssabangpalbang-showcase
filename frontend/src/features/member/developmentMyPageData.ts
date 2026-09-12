import { MyPageApiError } from '@/features/member/api/myPage';
import type {
  AgeGroup,
  CharacterId,
  MyFollowing,
  MyFollowingList,
  MyReport,
  MyStudy,
  PageData,
  PublicProfile,
  PublicProfileFollowing,
  PublicProfileReport,
  PublicProfileSection,
  PublicProfileStudy,
} from '@/features/member/api/myPage';

const DEVELOPMENT_RESPONSE_DELAY_MS = 320;

const APARTMENT_NAMES = [
  '옥수 파크힐스',
  '래미안 옥수 리버젠',
  'e편한세상 금호 파크힐스',
  '서울숲 푸르지오',
  '왕십리 센트라스',
  '행당 한진타운',
  '신금호 파크자이',
  '금호 브라운스톤',
  '응봉 대림강변타운',
  '한남 더힐',
] as const;

const STUDY_TOPICS = [
  '출퇴근 동선과 교통',
  '학군과 교육 환경',
  '야간 보행 안전',
  '상권과 생활 편의',
  '주차와 단지 관리',
  '한강 접근성과 산책로',
] as const;

const NICKNAMES = [
  '옥수동탐험가',
  '집보는두리',
  '성동구새싹',
  '한강뷰연구원',
  '임장메이트',
  '주말집사',
  '교통분석가',
  '햇살좋은집',
  '아파트산책러',
  '꼼꼼한토끼',
  '숲세권찾기',
  '신혼집공부중',
] as const;

const ANALYSIS_TAGS = ['시세 분석', '주변 환경', '교통 분석', '학군 분석', '투자 전망'] as const;
const CHARACTER_IDS: CharacterId[] = ['PALBANG', 'PALBANG_RABBIT', 'PALBANG_DOG'];
const AGE_GROUPS: AgeGroup[] = ['TWENTIES', 'THIRTIES', 'FORTIES', 'FIFTIES'];

const DEVELOPMENT_STUDIES: MyStudy[] = Array.from({ length: 46 }, (_, index) => {
  const apartmentName = APARTMENT_NAMES[index % APARTMENT_NAMES.length] ?? '옥수 파크힐스';
  const topic = STUDY_TOPICS[index % STUDY_TOPICS.length] ?? '생활 환경';
  const status: MyStudy['status'] =
    index % 7 === 0
      ? 'COMPLETED'
      : index % 5 === 0
        ? 'CLOSED'
        : index % 3 === 0
          ? 'IN_PROGRESS'
          : 'RECRUITING';
  const day = (index % 26) + 1;

  return {
    studyId: 5_001 + index,
    title: `${apartmentName} ${topic} 스터디`,
    intro: `${apartmentName}을 직접 걸어보며 ${topic}을 함께 확인해요.`,
    goal: `${topic} 체크리스트를 기준으로 현장을 꼼꼼히 비교합니다.`,
    status,
    role: index % 4 === 0 ? 'LEADER' : 'MEMBER',
    apartment: {
      apartmentId: 1_001 + (index % APARTMENT_NAMES.length),
      name: apartmentName,
    },
    nextSchedule:
      status === 'COMPLETED'
        ? null
        : {
            scheduleId: 7_001 + index,
            startAt: `2026-08-${String(day).padStart(2, '0')}T${index % 2 === 0 ? '10:00' : '14:00'}:00+09:00`,
            meetingPlace: `${apartmentName} 정문`,
          },
    unreadChatCount: status === 'COMPLETED' ? 0 : index % 6,
    pendingReviewCount: status === 'COMPLETED' ? index % 3 : 0,
    readOnly: status === 'COMPLETED',
    hasReturnableFieldVisit: status === 'IN_PROGRESS',
  };
});

const DEVELOPMENT_REPORTS: MyReport[] = Array.from({ length: 45 }, (_, index) => {
  const apartmentName = APARTMENT_NAMES[index % APARTMENT_NAMES.length] ?? '옥수 파크힐스';
  const firstTag = ANALYSIS_TAGS[index % ANALYSIS_TAGS.length] ?? '시세 분석';
  const secondTag = ANALYSIS_TAGS[(index + 2) % ANALYSIS_TAGS.length] ?? '주변 환경';
  const month = 7 - (index % 5);
  const day = 26 - (index % 23);

  return {
    reportId: 6_001 + index,
    title: `${apartmentName} ${firstTag} 리포트`,
    summary: `${apartmentName}의 ${firstTag}, ${secondTag} 데이터를 임장 기록과 함께 정리했어요.`,
    analysisTags: [firstTag, secondTag],
    apartment: {
      apartmentId: 1_001 + (index % APARTMENT_NAMES.length),
      name: apartmentName,
    },
    study: {
      studyId: 5_001 + index,
      title: `${apartmentName} 임장 스터디`,
      visitedAt: `2026-${String(month).padStart(2, '0')}-${String(day).padStart(2, '0')}T10:00:00+09:00`,
      participantCount: 3 + (index % 6),
    },
    status: 'COMPLETED',
    favoritedByMe: index % 3 === 0,
    canViewEvidence: true,
    completedAt: `2026-${String(month).padStart(2, '0')}-${String(day).padStart(2, '0')}T18:00:00+09:00`,
  };
});

const DEVELOPMENT_FOLLOWINGS: MyFollowing[] = Array.from({ length: 52 }, (_, index) => ({
  memberId: 8_001 + index,
  nickname: `${NICKNAMES[index % NICKNAMES.length] ?? '임장메이트'}${Math.floor(index / NICKNAMES.length) + 1}`,
  profileImageUrl: null,
  selectedCharacterId: CHARACTER_IDS[index % CHARACTER_IDS.length] ?? 'PALBANG',
  ageGroup: AGE_GROUPS[index % AGE_GROUPS.length] ?? 'THIRTIES',
  participatingStudyCount: 1 + (index % 9),
  isFollowing: true,
  canSendMessage: true,
  followedAt: `2026-07-${String((index % 27) + 1).padStart(2, '0')}T09:00:00+09:00`,
}));

const DEVELOPMENT_PUBLIC_REPORTS: PublicProfileReport[] = DEVELOPMENT_REPORTS.slice(0, 33).map(
  (report) => ({
    reportId: report.reportId,
    title: report.title,
    summary: report.summary,
    analysisTags: report.analysisTags,
    apartment: report.apartment,
    study: {
      studyId: report.study.studyId,
      title: report.study.title,
      participantCount: report.study.participantCount,
    },
    favoritedByMe: report.favoritedByMe,
    completedAt: report.completedAt,
  }),
);

const DEVELOPMENT_PUBLIC_STUDIES: PublicProfileStudy[] = DEVELOPMENT_STUDIES.slice(0, 34).map(
  (study) => ({
    studyId: study.studyId,
    title: study.title,
    status: study.status,
    role: study.role,
    apartment: study.apartment,
  }),
);

export const DEVELOPMENT_MY_PAGE_COUNTS = {
  studies: DEVELOPMENT_STUDIES.length,
  reports: DEVELOPMENT_REPORTS.length,
  following: DEVELOPMENT_FOLLOWINGS.length,
} as const;

function waitForDevelopmentResponse(): Promise<void> {
  return new Promise((resolve) => setTimeout(resolve, DEVELOPMENT_RESPONSE_DELAY_MS));
}

function normalizeSize(size: number): number {
  return Number.isSafeInteger(size) && size > 0 ? size : 20;
}

async function getDevelopmentPage<T>(
  content: T[],
  page: number,
  size: number,
): Promise<PageData<T>> {
  await waitForDevelopmentResponse();
  const safePage = Number.isSafeInteger(page) && page >= 0 ? page : 0;
  const safeSize = normalizeSize(size);
  const start = safePage * safeSize;

  return {
    content: content.slice(start, start + safeSize),
    totalElements: content.length,
    page: safePage,
    size: safeSize,
    totalPages: Math.ceil(content.length / safeSize),
  };
}

export function getDevelopmentMyStudies(page = 0, size = 20): Promise<PageData<MyStudy>> {
  return getDevelopmentPage(DEVELOPMENT_STUDIES, page, size);
}

export function getDevelopmentMyReports(page = 0, size = 20): Promise<PageData<MyReport>> {
  return getDevelopmentPage(DEVELOPMENT_REPORTS, page, size);
}

export async function getDevelopmentMyFollowings(
  cursor?: number,
  size = 20,
): Promise<MyFollowingList> {
  await waitForDevelopmentResponse();
  const safeSize = normalizeSize(size);
  const start = cursor !== undefined && Number.isSafeInteger(cursor) && cursor >= 0 ? cursor : 0;
  const end = Math.min(start + safeSize, DEVELOPMENT_FOLLOWINGS.length);
  const hasNext = end < DEVELOPMENT_FOLLOWINGS.length;

  return {
    content: DEVELOPMENT_FOLLOWINGS.slice(start, end),
    totalCount: DEVELOPMENT_FOLLOWINGS.length,
    nextCursor: hasNext ? end : null,
    hasNext,
  };
}

function toPublicFollowing(following: MyFollowing): PublicProfileFollowing {
  return {
    ...following,
    isMe: false,
    canFollow: true,
  };
}

export async function getDevelopmentPublicProfile(
  memberId: number,
  section: PublicProfileSection,
  page = 0,
  size = 20,
): Promise<PublicProfile> {
  const target = DEVELOPMENT_FOLLOWINGS.find((following) => following.memberId === memberId);

  if (!target) {
    await waitForDevelopmentResponse();
    throw new MyPageApiError('MEMBER_NOT_FOUND', '더미 사용자 정보를 찾을 수 없어요.');
  }

  const publicFollowings = DEVELOPMENT_FOLLOWINGS.filter(
    (following) => following.memberId !== memberId,
  ).map(toPublicFollowing);
  const studies =
    section === 'STUDIES' ? await getDevelopmentPage(DEVELOPMENT_PUBLIC_STUDIES, page, size) : null;
  const reports =
    section === 'REPORTS' ? await getDevelopmentPage(DEVELOPMENT_PUBLIC_REPORTS, page, size) : null;
  const followings =
    section === 'FOLLOWINGS' ? await getDevelopmentPage(publicFollowings, page, size) : null;

  return {
    memberId: target.memberId,
    nickname: target.nickname,
    profileImageUrl: target.profileImageUrl,
    selectedCharacterId: target.selectedCharacterId,
    ageGroup: target.ageGroup,
    reviewSummary: {
      topTags: [
        { code: 'PUNCTUAL', label: '시간 약속을 잘 지켜요', emoji: '⏰', category: 'PERSON', count: 7 },
        { code: 'THOROUGH', label: '꼼꼼하게 살펴봐요', emoji: '🔍', category: 'VISIT', count: 5 },
      ],
      likeReceivedCount: 9,
      reviewCount: 12,
    },
    fieldVisitCompletedCount: 6,
    participatingStudyCount: target.participatingStudyCount,
    reportCount: DEVELOPMENT_PUBLIC_REPORTS.length,
    followingCount: publicFollowings.length,
    isMe: false,
    isFollowing: true,
    canFollow: true,
    canSendMessage: true,
    section,
    studies,
    reports,
    followings,
  };
}
