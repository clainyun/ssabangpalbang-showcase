/**
 * 닉네임 기반 아바타 색/이니셜. 닉네임 해시로 색을 배정해 같은 유저는 항상 같은 색이 됩니다.
 * 더미가 아니라 실제 작성자에도 쓰이므로 더미 데이터와 분리(삭제 대상 아님).
 */

// 테라코타·초록·남색·오커 등 서로 구분되는 차분한 팔레트.
const AVATAR_COLORS = [
  '#C86F56',
  '#5B8C7B',
  '#4F6D8C',
  '#B5763E',
  '#7C6A9C',
  '#3E8E7E',
  '#C08552',
  '#6E8B5B',
] as const;

export function avatarColorFromNickname(nickname: string): string {
  let hash = 0;
  for (let i = 0; i < nickname.length; i += 1) {
    hash = (hash * 31 + nickname.charCodeAt(i)) >>> 0;
  }
  return AVATAR_COLORS[hash % AVATAR_COLORS.length]!;
}

/** 아바타에 표시할 이니셜(닉네임 첫 글자). */
export function avatarInitial(nickname: string): string {
  return nickname.trim().charAt(0) || '?';
}
