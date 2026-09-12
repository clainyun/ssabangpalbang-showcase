import type { ImageSourcePropType } from 'react-native';

/**
 * 임장 대기 화면(FieldVisitWaitingScreen)에서만 쓰는 '책상에 앉은' 포즈입니다.
 * 채팅 등에서 쓰는 characterAvatarSource(서 있는 포즈, characterAvatar.ts)와는 다른
 * 이미지셋이라 따로 둡니다 — 캐릭터가 늘어나면 두 파일 모두에 추가해야 합니다.
 */
const CHARACTER_DESK_AVATARS: Record<string, ImageSourcePropType> = {
  PALBANG: require('../../../assets/images/characters/desk/desk_plain.png'),
  PALBANG_RABBIT: require('../../../assets/images/characters/desk/desk_rabbit.png'),
  PALBANG_DOG: require('../../../assets/images/characters/desk/desk_dog.png'),
};

/** 대기 화면의 멤버 카드가 공용으로 씁니다. */
export function characterDeskAvatarSource(characterId: string): ImageSourcePropType {
  return CHARACTER_DESK_AVATARS[characterId] ?? CHARACTER_DESK_AVATARS.PALBANG!;
}
