import { Pressable, Text, View, StyleSheet } from 'react-native';

import { BUTTON_BACKGROUND_COLOR, PRIMARY_COLOR, TEXT_COLOR } from '@/constants/colors';

const DISABLED_TEXT_COLOR = '#AEBAB2';

interface SelectableChipProps {
  label: string;
  selected: boolean;
  /** 선택된 순서 (1부터). 선택 안 됐으면 표시 안 함 */
  order?: number;
  /** 이미 최대 개수를 선택해서 이 칩은 더 못 고르는 상태 (선택 해제는 계속 가능) */
  disabled?: boolean;
  onPress: () => void;
}

/** 순서가 매겨지는 다중 선택 칩 (관심 우선순위처럼 "몇 번째로 골랐는지"가 중요한 화면용). */
export function SelectableChip({ label, selected, order, disabled, onPress }: SelectableChipProps) {
  return (
    <Pressable
      onPress={onPress}
      disabled={disabled}
      style={[
        styles.chip,
        selected && styles.chipSelected,
        disabled && styles.chipDisabled,
      ]}
    >
      {selected && !!order && (
        <View style={styles.badge}>
          <Text style={styles.badgeText}>{order}</Text>
        </View>
      )}
      <Text
        style={[
          styles.label,
          selected && styles.labelSelected,
          disabled && styles.labelDisabled,
        ]}
      >
        {label}
      </Text>
    </Pressable>
  );
}

const styles = StyleSheet.create({
  chip: {
    flexDirection: 'row',
    alignItems: 'center',
    gap: 6,
    height: 44,
    borderRadius: 22,
    borderWidth: 1.5,
    borderColor: 'transparent',
    backgroundColor: '#FFFFFF',
    paddingHorizontal: 16,
  },
  chipSelected: {
    backgroundColor: BUTTON_BACKGROUND_COLOR,
  },
  chipDisabled: {
    opacity: 0.45,
  },
  badge: {
    width: 18,
    height: 18,
    borderRadius: 9,
    backgroundColor: PRIMARY_COLOR,
    alignItems: 'center',
    justifyContent: 'center',
  },
  badgeText: {
    fontSize: 11,
    fontWeight: '700',
    color: '#FFFFFF',
  },
  label: {
    fontSize: 14,
    fontWeight: '600',
    color: TEXT_COLOR,
  },
  labelSelected: {
    color: PRIMARY_COLOR,
    fontWeight: '700',
  },
  labelDisabled: {
    color: DISABLED_TEXT_COLOR,
  },
});
