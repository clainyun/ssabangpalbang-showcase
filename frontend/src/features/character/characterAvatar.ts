import type { ImageSourcePropType } from 'react-native';

/** 캐릭터 종류가 늘어나면 여기에만 추가하면 됩니다. 모르는 값이 오면 기본 캐릭터로 표시합니다. */
const CHARACTER_AVATARS: Record<string, ImageSourcePropType> = {
  PALBANG: require('../../../assets/images/characters/palbang.png'),
  PALBANG_RABBIT: require('../../../assets/images/characters/palbang_rabbit.png'),
  PALBANG_DOG: require('../../../assets/images/characters/palbang_dog.png'),
};

/** 채팅·스터디 상세 등 캐릭터 아바타가 필요한 화면이 공용으로 씁니다. */
export function characterAvatarSource(characterId: string): ImageSourcePropType {
  return CHARACTER_AVATARS[characterId] ?? CHARACTER_AVATARS.PALBANG!;
}
