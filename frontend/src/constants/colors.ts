/**
 * 앱 전역에서 재사용하는 색상 상수. 화면별로 하드코딩하지 말고 여기서 가져다 씁니다.
 *
 * PRIMARY_COLOR는 서비스 핵심 색상(연한 그린 계열의 버튼/강조 색)입니다. 디자인 문서에
 * #13B26E와 #12A56B 두 값이 같이 보였는데, #13B26E로 통일하기로 확인했습니다
 * (TabIcon.tsx, onboarding.tsx 등에 남아있던 #12A56B도 전부 이 값으로 교체함).
 */
export const PRIMARY_COLOR = '#13B26E';

// 가입하기/로그인 버튼의 배경색(연한 민트) — PRIMARY_COLOR와는 다른 값입니다.
export const BUTTON_BACKGROUND_COLOR = '#DDF6E9';

export const ERROR_COLOR = '#E1483F';
export const ERROR_BACKGROUND_COLOR = '#FDECEC';

// 좋아요 하트 등에 쓰는 코랄 강조색. 게시글 상세·커뮤니티·찜 카운트 등에서 공통 재사용.
export const LIKE_ACCENT_COLOR = '#E0603E';

export const TEXT_COLOR = '#1F2937';
export const LABEL_COLOR = '#374151';
export const PLACEHOLDER_COLOR = '#AEBAB2';

export const SURFACE_COLOR = '#FFFFFF';
export const SCREEN_BACKGROUND_COLOR = '#F7F8F7';
export const BORDER_COLOR = '#E5E9E6';
export const MUTED_TEXT_COLOR = '#98A39C';
export const SOFT_BACKGROUND_COLOR = '#F3F5F4';
export const DARK_GREEN_COLOR = '#10271E';
export const SOFT_GREEN_COLOR = '#EDF7F0';
export const CALENDAR_SATURDAY_COLOR = '#4B68CE';
export const MODAL_SCRIM_COLOR = 'rgba(16, 39, 30, 0.48)';

export const SUBTLE_BACKGROUND_COLOR = '#F1F2F1';
export const DIVIDER_COLOR = '#ECEEED';
