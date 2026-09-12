import { View, Text, StyleSheet } from 'react-native';

import { OptionButton } from '@/components/OptionButton';
import { TEXT_COLOR, PLACEHOLDER_COLOR } from '@/constants/colors';
import type { AgeGroup } from '@/features/onboarding/api/saveOnboarding';

const AGE_GROUPS: { value: AgeGroup; label: string }[] = [
  { value: 'TEENS', label: '10대' },
  { value: 'TWENTIES', label: '20대' },
  { value: 'THIRTIES', label: '30대' },
  { value: 'FORTIES', label: '40대' },
  { value: 'FIFTIES', label: '50대' },
  { value: 'SIXTIES_PLUS', label: '60대 이상' },
];

interface AgeGroupStepProps {
  ageGroup: AgeGroup | null;
  onAgeGroupChange: (value: AgeGroup) => void;
  ageGroupPublicAgreed: boolean | null;
  onAgeGroupPublicAgreedChange: (value: boolean) => void;
}

export function AgeGroupStep({
  ageGroup,
  onAgeGroupChange,
  ageGroupPublicAgreed,
  onAgeGroupPublicAgreedChange,
}: AgeGroupStepProps) {
  return (
    <View style={styles.container}>
      <Text style={styles.title}>연령대를 알려주세요</Text>
      <Text style={styles.subtitle}>주변 임장 스터디원들과 맞춰볼 때 참고해요.</Text>

      <View style={styles.grid}>
        {AGE_GROUPS.map((item) => (
          <OptionButton
            key={item.value}
            label={item.label}
            selected={ageGroup === item.value}
            onPress={() => onAgeGroupChange(item.value)}
            style={styles.gridItem}
          />
        ))}
      </View>

      <View style={styles.section}>
        <Text style={styles.sectionTitle}>다른 사용자에게 연령대를 공개할까요?</Text>
        <Text style={styles.sectionDescription}>커뮤니티 등에서 내 연령대가 보여요.</Text>
        <View style={styles.row}>
          <OptionButton
            label="공개"
            selected={ageGroupPublicAgreed === true}
            onPress={() => onAgeGroupPublicAgreedChange(true)}
            style={styles.rowItem}
          />
          <OptionButton
            label="비공개"
            selected={ageGroupPublicAgreed === false}
            onPress={() => onAgeGroupPublicAgreedChange(false)}
            style={styles.rowItem}
          />
        </View>
      </View>
    </View>
  );
}

const styles = StyleSheet.create({
  container: {
    gap: 8,
  },
  title: {
    fontSize: 22,
    fontWeight: '800',
    color: TEXT_COLOR,
  },
  subtitle: {
    fontSize: 14,
    color: PLACEHOLDER_COLOR,
    marginBottom: 24,
  },
  grid: {
    flexDirection: 'row',
    flexWrap: 'wrap',
    gap: 12,
  },
  gridItem: {
    width: '47%',
  },
  section: {
    marginTop: 36,
    gap: 8,
  },
  sectionTitle: {
    fontSize: 16,
    fontWeight: '700',
    color: TEXT_COLOR,
  },
  sectionDescription: {
    fontSize: 13,
    color: PLACEHOLDER_COLOR,
    marginBottom: 12,
  },
  row: {
    flexDirection: 'row',
    gap: 12,
  },
  rowItem: {
    flex: 1,
  },
});
