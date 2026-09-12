/**
 * 태그 기반 동료 리뷰 카탈로그(FE-037 / BE-028).
 *
 * code 값은 백엔드 enum 코드와 반드시 1:1로 일치해야 합니다. 리뷰 등록 요청은 이
 * code 문자열 배열을 그대로 전송하고, 조회 응답의 태그도 같은 code로 내려옵니다.
 * 표시명·이모지는 프론트에서만 쓰는 값이라 서버 응답에 함께 오더라도 이 카탈로그를
 * 기준으로 렌더링하면 됩니다.
 */
export type ReviewTagCategory = 'PERSON' | 'VISIT';

export interface ReviewTag {
  code: string;
  emoji: string;
  label: string;
  category: ReviewTagCategory;
}

export const REVIEW_TAG_CATEGORY_LABELS: Record<ReviewTagCategory, string> = {
  PERSON: '사람·협업',
  VISIT: '임장·활동',
};

/** 카테고리 표시 순서(사람·협업 → 임장·활동). */
export const REVIEW_TAG_CATEGORY_ORDER: ReviewTagCategory[] = ['PERSON', 'VISIT'];

export const REVIEW_TAGS: ReviewTag[] = [
  { code: 'PUNCTUAL', emoji: '⏰', label: '시간 약속을 잘 지켜요', category: 'PERSON' },
  { code: 'GOOD_MANNERS', emoji: '🙌', label: '매너가 좋아요', category: 'PERSON' },
  { code: 'EASY_COMMUNICATION', emoji: '💬', label: '소통이 편해요', category: 'PERSON' },
  { code: 'CONSIDERATE', emoji: '💚', label: '배려심이 깊어요', category: 'PERSON' },
  { code: 'WANT_AGAIN', emoji: '🤝', label: '또 함께하고 싶어요', category: 'PERSON' },
  { code: 'LEADS_MOOD', emoji: '😊', label: '분위기를 잘 이끌어요', category: 'PERSON' },
  { code: 'THOROUGH', emoji: '🔍', label: '꼼꼼하게 살펴봐요', category: 'VISIT' },
  { code: 'LEADS_VISIT', emoji: '🧭', label: '임장을 잘 리드해요', category: 'VISIT' },
  { code: 'WELL_PREPARED', emoji: '📝', label: '준비를 잘해와요', category: 'VISIT' },
  { code: 'SHARP_ANALYSIS', emoji: '🧠', label: '분석이 날카로워요', category: 'VISIT' },
  { code: 'GOOD_RECORDS', emoji: '📸', label: '사진·기록을 잘 남겨요', category: 'VISIT' },
  { code: 'SHARES_INFO', emoji: '📣', label: '정보 공유를 잘해요', category: 'VISIT' },
  { code: 'DILIGENT', emoji: '✅', label: '성실하게 참여해요', category: 'VISIT' },
  { code: 'GOOD_QUESTIONS', emoji: '❓', label: '질문이 좋아요', category: 'VISIT' },
  { code: 'ENERGETIC', emoji: '⚡', label: '에너지가 넘쳐요', category: 'VISIT' },
];

const REVIEW_TAG_BY_CODE: Record<string, ReviewTag> = REVIEW_TAGS.reduce<Record<string, ReviewTag>>(
  (accumulator, tag) => {
    accumulator[tag.code] = tag;
    return accumulator;
  },
  {},
);

/** 카탈로그에 없는 code면 undefined. 서버가 새 태그를 추가해도 앱이 죽지 않게 방어적으로 조회합니다. */
export function findReviewTag(code: string): ReviewTag | undefined {
  return REVIEW_TAG_BY_CODE[code];
}

export function getReviewTagsByCategory(category: ReviewTagCategory): ReviewTag[] {
  return REVIEW_TAGS.filter((tag) => tag.category === category);
}
