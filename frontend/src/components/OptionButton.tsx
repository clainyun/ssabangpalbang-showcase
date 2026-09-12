import { Pressable, Text, StyleSheet, type StyleProp, type ViewStyle } from 'react-native';

import { BUTTON_BACKGROUND_COLOR, PRIMARY_COLOR, TEXT_COLOR } from '@/constants/colors';

interface OptionButtonProps {
  label: string;
  selected: boolean;
  onPress: () => void;
  style?: StyleProp<ViewStyle>;
}

/** 단일/다중 선택 화면에서 쓰는 범용 선택 버튼 (연령대, 혼인 상태 등 온보딩류 화면 전반에서 재사용). */
export function OptionButton({ label, selected, onPress, style }: OptionButtonProps) {
  return (
    <Pressable
      onPress={onPress}
      style={[styles.button, selected && styles.buttonSelected, style]}
    >
      <Text style={[styles.label, selected && styles.labelSelected]}>{label}</Text>
    </Pressable>
  );
}

const styles = StyleSheet.create({
  button: {
    height: 52,
    borderRadius: 14,
    borderWidth: 1.5,
    borderColor: 'transparent',
    backgroundColor: '#FFFFFF',
    alignItems: 'center',
    justifyContent: 'center',
    paddingHorizontal: 12,
  },
  buttonSelected: {
    backgroundColor: BUTTON_BACKGROUND_COLOR,
  },
  label: {
    fontSize: 15,
    fontWeight: '600',
    color: TEXT_COLOR,
  },
  labelSelected: {
    color: PRIMARY_COLOR,
    fontWeight: '700',
  },
});
