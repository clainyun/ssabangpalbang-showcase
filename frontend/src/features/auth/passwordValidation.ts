/**
 * 회원가입 / 비밀번호 재설정 공통 비밀번호 정책.
 * 백엔드 SignupRequest·비밀번호 재설정의 @Pattern 과 동일한 규칙입니다.
 * (영문 1자 이상 + 숫자 1자 이상 + 총 8~64자)
 */
export const PASSWORD_REGEX = /^(?=.*[A-Za-z])(?=.*\d).{8,64}$/;

export interface PasswordChecks {
  /** 영문(A-Z, a-z) 1자 이상 포함 */
  hasLetter: boolean;
  /** 숫자 1자 이상 포함 */
  hasDigit: boolean;
  /** 8~64자 */
  hasLength: boolean;
  /** 세 조건을 모두 충족(= PASSWORD_REGEX 통과) */
  isValid: boolean;
}

/** 비밀번호를 개별 조건별로 분해해 실시간 체크리스트 표시에 사용합니다. */
export function validatePassword(password: string): PasswordChecks {
  const hasLetter = /[A-Za-z]/.test(password);
  const hasDigit = /\d/.test(password);
  const hasLength = password.length >= 8 && password.length <= 64;
  return { hasLetter, hasDigit, hasLength, isValid: hasLetter && hasDigit && hasLength };
}

/** 비밀번호 확인 필드가 원본과 일치하는지(둘 다 비어있지 않을 때만). */
export function passwordsMatch(password: string, confirm: string): boolean {
  return password.length > 0 && password === confirm;
}
