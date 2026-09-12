import { create } from 'zustand';

/**
 * ⚠️ §6.2 — 프레임 인덱스처럼 초당 수 회 이상 갱신되는 값을 여기에 넣지 마십시오.
 * Reanimated shared value 로 UI 스레드에 둡니다. 여기에는 저빈도 상태만 둡니다.
 */
export type Direction = 'N' | 'NE' | 'E' | 'SE' | 'S' | 'SW' | 'W' | 'NW';

interface CharacterState {
  direction: Direction;
  isMoving: boolean;
  setDirection: (direction: Direction) => void;
  setMoving: (isMoving: boolean) => void;
}

export const useCharacterStore = create<CharacterState>((set) => ({
  direction: 'S',
  isMoving: false,
  setDirection: (direction) => set({ direction }),
  setMoving: (isMoving) => set({ isMoving }),
}));
