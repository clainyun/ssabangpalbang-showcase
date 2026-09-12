import { Pressable, View, Text, Image, StyleSheet } from 'react-native';

import { BUTTON_BACKGROUND_COLOR, PRIMARY_COLOR, TEXT_COLOR, PLACEHOLDER_COLOR } from '@/constants/colors';
import type { CharacterId } from '@/features/onboarding/api/saveOnboarding';

const CHARACTERS: { value: CharacterId; title: string; image: number }[] = [
  { value: 'PALBANG', title: '기본 팔방이', image: require('../../../../assets/images/characters/palbang.png') },
  { value: 'PALBANG_RABBIT', title: '토끼 팔방이', image: require('../../../../assets/images/characters/palbang_rabbit.png') },
  { value: 'PALBANG_DOG', title: '강아지 팔방이', image: require('../../../../assets/images/characters/palbang_dog.png') },
];

interface CharacterStepProps {
  selectedCharacterId: CharacterId | null;
  onSelectedCharacterIdChange: (value: CharacterId) => void;
}

export function CharacterStep({ selectedCharacterId, onSelectedCharacterIdChange }: CharacterStepProps) {
  return (
    <View style={styles.container}>
      <Text style={styles.title}>함께할 캐릭터를 골라주세요</Text>
      <Text style={styles.subtitle}>나중에 마이페이지에서 바꿀 수 있어요.</Text>

      <View style={styles.cards}>
        {CHARACTERS.map((item) => {
          const selected = selectedCharacterId === item.value;
          return (
            <Pressable
              key={item.value}
              onPress={() => onSelectedCharacterIdChange(item.value)}
              style={[styles.card, selected && styles.cardSelected]}
            >
              <Image source={item.image} style={styles.characterImage} />
              <Text style={[styles.cardTitle, selected && styles.cardTitleSelected]}>{item.title}</Text>
            </Pressable>
          );
        })}
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
    flexDirection: 'row',
    gap: 10,
  },
  card: {
    flex: 1,
    borderRadius: 18,
    borderWidth: 1.5,
    borderColor: 'transparent',
    backgroundColor: 'transparent',
    paddingVertical: 16,
    paddingHorizontal: 4,
    alignItems: 'center',
    gap: 8,
  },
  cardSelected: {
    backgroundColor: BUTTON_BACKGROUND_COLOR,
  },
  characterImage: {
    width: 92,
    height: 92,
    resizeMode: 'contain',
  },
  cardTitle: {
    fontSize: 13,
    fontWeight: '700',
    color: TEXT_COLOR,
    textAlign: 'center',
  },
  cardTitleSelected: {
    color: PRIMARY_COLOR,
  },
});
