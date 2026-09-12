import type { ImageSourcePropType } from 'react-native';

/**
 * 아파트 대표 이미지. 서버가 단지별 사진을 주지 않으므로 아파트 ID 로 준비된 아이콘
 * 중 하나를 고정 배정합니다(같은 단지는 항상 같은 그림).
 */
const APARTMENT_IMAGES = [
  require('../../../assets/apt-icon/apartment.png'),
  require('../../../assets/apt-icon/apt-icon-1.png'),
  require('../../../assets/apt-icon/apt-icon-2.png'),
  require('../../../assets/apt-icon/apt-icon-3.png'),
  require('../../../assets/apt-icon/apt-icon-4.png'),
  require('../../../assets/apt-icon/apt-icon-5.png'),
] as const;

export function apartmentImageSource(apartmentId: number): ImageSourcePropType {
  return APARTMENT_IMAGES[Math.abs(apartmentId) % APARTMENT_IMAGES.length];
}
