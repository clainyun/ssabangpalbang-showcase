import type { ImageSourcePropType } from 'react-native';

const APARTMENT_IMAGES: ImageSourcePropType[] = [
  require('../../assets/images/apartments/apart_1.png'),
  require('../../assets/images/apartments/apart_2.png'),
  require('../../assets/images/apartments/apart_3.png'),
  require('../../assets/images/apartments/apart_4.png'),
  require('../../assets/images/apartments/apart_5.png'),
  require('../../assets/images/apartments/apart_6.png'),
];

/** 실사진이 없어 apartmentId 기준으로 항상 같은 장식용 일러스트를 고릅니다. */
export function apartmentImageSource(apartmentId: number): ImageSourcePropType {
  return APARTMENT_IMAGES[Math.abs(apartmentId) % APARTMENT_IMAGES.length]!;
}
