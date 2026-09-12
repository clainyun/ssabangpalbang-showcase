import { View, Text, StyleSheet } from 'react-native';

import { SelectableCard } from '@/components/SelectableCard';
import { TEXT_COLOR, PLACEHOLDER_COLOR } from '@/constants/colors';
import type { Purpose } from '@/features/onboarding/api/saveOnboarding';

const PURPOSES: { value: Purpose; emoji: string; title: string }[] = [
  { value: 'RESIDENCE', emoji: '🏠', title: '실거주' },
  { value: 'INVESTMENT', emoji: '📈', title: '투자' },
  { value: 'STUDY', emoji: '🔍', title: '임장 학습·조사' },
];

interface PurposeStepProps {
  purpose: Purpose | null;
  onPurposeChange: (value: Purpose) => void;
}

export function PurposeStep({ purpose, onPurposeChange }: PurposeStepProps) {
  return (
    <View style={styles.container}>
      <Text style={styles.title}>임장하는 목적이 무엇인가요?</Text>
      <Text style={styles.subtitle}>목적에 맞는 체크리스트와 리포트를 준비해요.</Text>

      <View style={styles.cards}>
        {PURPOSES.map((item) => (
          <SelectableCard
            key={item.value}
            icon={<Text style={styles.emoji}>{item.emoji}</Text>}
            title={item.title}
            selected={purpose === item.value}
            onPress={() => onPurposeChange(item.value)}
          />
        ))}
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
  cards: {
    gap: 14,
  },
  emoji: {
    fontSize: 32,
  },
});
