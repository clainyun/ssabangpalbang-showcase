import { create } from 'zustand';

import { env } from '@/lib/env';
import { offsetCoordinateMeters, type Coordinate } from '@/lib/geo';

export const isDeveloperLocationAvailable = __DEV__ || env.demoModeEnabled;

const INITIAL_DISTANCE_SOUTH_METERS = 100;
const MAX_MOVE_DELTA_METERS = 5;

interface DeveloperLocationState {
  isEnabled: boolean;
  currentCoordinate: Coordinate | null;
  destinationCoordinate: Coordinate | null;
  enable: () => void;
  disable: () => void;
  initializeSession: (destination: Coordinate) => Coordinate;
  moveBy: (northMeters: number, eastMeters: number) => void;
}

export const useDeveloperLocationStore = create<DeveloperLocationState>((set) => ({
  isEnabled: false,
  currentCoordinate: null,
  destinationCoordinate: null,
  enable: () => {
    if (isDeveloperLocationAvailable) {
      set({ isEnabled: true });
    }
  },
  disable: () =>
    set({
      isEnabled: false,
      currentCoordinate: null,
      destinationCoordinate: null,
    }),
  initializeSession: (destination) => {
    const start = offsetCoordinateMeters(destination, -INITIAL_DISTANCE_SOUTH_METERS, 0);
    set({ currentCoordinate: start, destinationCoordinate: destination });
    return start;
  },
  moveBy: (northMeters, eastMeters) =>
    set((state) => {
      if (!state.isEnabled || state.currentCoordinate === null) return state;
      if (!Number.isFinite(northMeters) || !Number.isFinite(eastMeters)) return state;
      if (northMeters === 0 && eastMeters === 0) return state;
      if (
        Math.abs(northMeters) > MAX_MOVE_DELTA_METERS ||
        Math.abs(eastMeters) > MAX_MOVE_DELTA_METERS
      ) {
        return state;
      }

      return {
        currentCoordinate: offsetCoordinateMeters(state.currentCoordinate, northMeters, eastMeters),
      };
    }),
}));
