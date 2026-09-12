import { View, StyleSheet } from 'react-native';

import { PRIMARY_COLOR } from '@/constants/colors';

const TRACK_COLOR = '#E4E7E9';

interface ProgressBarProps {
  /** 1부터 시작하는 현재 단계 */
  step: number;
  totalSteps: number;
}

/** 상단 다단계 진행 표시줄. 온보딩처럼 순서가 있는 여러 화면 어디서든 재사용 가능합니다. */
export function ProgressBar({ step, totalSteps }: ProgressBarProps) {
  return (
    <View style={styles.container}>
      {Array.from({ length: totalSteps }, (_, index) => (
        <View
          key={index}
          style={[styles.segment, index < step && styles.segmentFilled]}
        />
      ))}
    </View>
  );
}

const styles = StyleSheet.create({
  container: {
    flexDirection: 'row',
    gap: 6,
  },
  segment: {
    flex: 1,
    height: 4,
    borderRadius: 2,
    backgroundColor: TRACK_COLOR,
  },
  segmentFilled: {
    backgroundColor: PRIMARY_COLOR,
  },
});
