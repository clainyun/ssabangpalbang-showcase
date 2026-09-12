import { env } from '@/lib/env';
import type { Coordinate } from '@/lib/geo';

export type { Coordinate };

interface DirectionsResponse {
  code: string;
  message?: string;
  routes?: {
    distance: number;
    duration: number;
    geometry: {
      type: 'LineString';
      coordinates: number[][];
    };
  }[];
}

export interface WalkingRoute {
  coordinates: Coordinate[];
  distanceMeters: number;
  durationSeconds: number;
}

export async function getWalkingRoute(
  origin: Coordinate,
  destination: Coordinate,
): Promise<WalkingRoute> {
  const coordinates = `${origin[0]},${origin[1]};${destination[0]},${destination[1]}`;
  const query = new URLSearchParams({
    access_token: env.mapboxToken,
    geometries: 'geojson',
    overview: 'full',
    steps: 'false',
    walkway_bias: '1',
  });
  const url = `https://api.mapbox.com/directions/v5/mapbox/walking/${coordinates}?${query}`;
  const response = await fetch(url);
  const data = (await response.json()) as DirectionsResponse;

  if (!response.ok || data.code !== 'Ok') {
    throw new Error(data.message ?? `보행 경로 요청 실패 (${response.status})`);
  }

  const route = data.routes?.[0];
  if (!route || route.geometry.type !== 'LineString') {
    throw new Error('사용 가능한 보행 경로가 없습니다.');
  }

  return {
    coordinates: route.geometry.coordinates.map(([longitude, latitude]) => {
      if (longitude === undefined || latitude === undefined) {
        throw new Error('보행 경로 좌표 형식이 올바르지 않습니다.');
      }
      return [longitude, latitude];
    }),
    distanceMeters: route.distance,
    durationSeconds: route.duration,
  };
}
