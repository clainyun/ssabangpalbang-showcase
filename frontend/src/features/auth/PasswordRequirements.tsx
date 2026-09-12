import { StyleSheet, Text, View } from 'react-native';
import Svg, { Path } from 'react-native-svg';

import { PLACEHOLDER_COLOR, PRIMARY_COLOR, TEXT_COLOR } from '@/constants/colors';
import { validatePassword } from '@/features/auth/passwordValidation';

interface RequirementRowProps {
  met: boolean;
  label: string;
}

// 충족 시 초록 체크, 미충족 시 회색 체크로만 색을 바꿔 "다음에 충족되면 초록이 된다"는
// 진행 상태를 자연스럽게 보여줍니다.
function CheckIcon({ met }: { met: boolean }) {
  return (
    <Svg width={16} height={16} viewBox="0 0 24 24" fill="none">
      <Path
        d="M20 6L9 17l-5-5"
        stroke={met ? PRIMARY_COLOR : PLACEHOLDER_COLOR}
        strokeWidth={met ? 3 : 2}
        strokeLinecap="round"
        strokeLinejoin="round"
      />
    </Svg>
  );
}

function RequirementRow({ met, label }: RequirementRowProps) {
  return (
    <View
      style={styles.row}
      accessibilityRole="text"
      accessibilityLabel={`${label} ${met ? '충족' : '미충족'}`}
    >
      <CheckIcon met={met} />
      <Text style={[styles.label, met ? styles.labelMet : styles.labelUnmet]}>{label}</Text>
    </View>
  );
}

/**
 * 비밀번호 정책을 개별 조건으로 실시간 표시하는 체크리스트.
 * 회원가입·비밀번호 재설정 양쪽에서 재사용합니다(§공통 비밀번호 검증).
 */
export function PasswordRequirements({ password }: { password: string }) {
  const { hasLetter, hasDigit, hasLength } = validatePassword(password);
  return (
    <View style={styles.container}>
      <RequirementRow met={hasLetter} label="영문 포함" />
      <RequirementRow met={hasDigit} label="숫자 포함" />
      <RequirementRow met={hasLength} label="8~64자" />
    </View>
  );
}

const styles = StyleSheet.create({
  container: {
    marginTop: -2,
    gap: 6,
  },
  row: {
    flexDirection: 'row',
    alignItems: 'center',
    gap: 8,
  },
  label: {
    fontSize: 12,
    lineHeight: 16,
  },
  labelMet: {
    color: TEXT_COLOR,
    fontWeight: '600',
  },
  labelUnmet: {
    color: PLACEHOLDER_COLOR,
  },
});
