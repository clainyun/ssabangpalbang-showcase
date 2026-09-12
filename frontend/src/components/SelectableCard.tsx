import type { ReactNode } from 'react';
import { Pressable, Text, View, StyleSheet } from 'react-native';

import { BUTTON_BACKGROUND_COLOR, PRIMARY_COLOR, PLACEHOLDER_COLOR, TEXT_COLOR } from '@/constants/colors';

interface SelectableCardProps {
  icon: ReactNode;
  title: string;
  description?: string;
  selected: boolean;
  onPress: () => void;
}

/** 아이콘/이미지 + 제목(+설명)이 있는 큰 카드형 단일 선택 UI (임장 목적, 캐릭터 선택 등에서 재사용). */
export function SelectableCard({ icon, title, description, selected, onPress }: SelectableCardProps) {
  return (
    <Pressable
      onPress={onPress}
      style={[styles.card, selected && styles.cardSelected]}
    >
      <View style={styles.iconWrap}>{icon}</View>
      <Text style={[styles.title, selected && styles.titleSelected]}>{title}</Text>
      {!!description && <Text style={styles.description}>{description}</Text>}
    </Pressable>
  );
}

const styles = StyleSheet.create({
  card: {
    borderRadius: 18,
    borderWidth: 1.5,
    borderColor: 'transparent',
    backgroundColor: '#FFFFFF',
    paddingVertical: 20,
    paddingHorizontal: 16,
    alignItems: 'center',
    gap: 8,
  },
  cardSelected: {
    backgroundColor: BUTTON_BACKGROUND_COLOR,
  },
  iconWrap: {
    alignItems: 'center',
    justifyContent: 'center',
  },
  title: {
    fontSize: 16,
    fontWeight: '700',
    color: TEXT_COLOR,
  },
  titleSelected: {
    color: PRIMARY_COLOR,
  },
  description: {
    fontSize: 12,
    color: PLACEHOLDER_COLOR,
    textAlign: 'center',
  },
});
