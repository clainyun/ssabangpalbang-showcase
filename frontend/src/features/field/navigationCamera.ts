export type Coordinate = [number, number];

const EARTH_RADIUS_METERS = 6_371_000;
const toRadians = (degrees: number) => (degrees * Math.PI) / 180;

function distanceInMeters(from: Coordinate, to: Coordinate): number {
  const latitudeDelta = toRadians(to[1] - from[1]);
  const longitudeDelta = toRadians(to[0] - from[0]);
  const fromLatitude = toRadians(from[1]);
  const toLatitude = toRadians(to[1]);
  const haversine =
    Math.sin(latitudeDelta / 2) ** 2 +
    Math.cos(fromLatitude) * Math.cos(toLatitude) * Math.sin(longitudeDelta / 2) ** 2;
  return 2 * EARTH_RADIUS_METERS * Math.asin(Math.sqrt(haversine));
}

function bearingDegrees(from: Coordinate, to: Coordinate): number {
  const fromLatitude = toRadians(from[1]);
  const toLatitude = toRadians(to[1]);
  const longitudeDelta = toRadians(to[0] - from[0]);
  const y = Math.sin(longitudeDelta) * Math.cos(toLatitude);
  const x =
    Math.cos(fromLatitude) * Math.sin(toLatitude) -
    Math.sin(fromLatitude) * Math.cos(toLatitude) * Math.cos(longitudeDelta);
  return ((((Math.atan2(y, x) * 180) / Math.PI) % 360) + 360) % 360;
}

export const NAVIGATION_ROUTE_SNAP_MAX_DISTANCE_METERS = 35;
const NAVIGATION_LOOK_AHEAD_METERS = 50;

export type NavigationCameraTarget = {
  anchorCoordinate: Coordinate;
  heading: number;
};

function destinationFallback(
  coordinate: Coordinate,
  fallbackCoordinate: Coordinate,
): NavigationCameraTarget | null {
  if (distanceInMeters(coordinate, fallbackCoordinate) < 1) return null;
  return {
    anchorCoordinate: coordinate,
    heading: bearingDegrees(coordinate, fallbackCoordinate),
  };
}

/**
 * 현재 위치가 경로에서 35m 안이면 경로를 따라 앞을 보고, 그보다 멀면 stale 경로에
 * 카메라를 붙이지 않고 실제 위치에서 목적지를 바라봅니다.
 */
export function getNavigationCameraTarget(
  coordinate: Coordinate,
  routeCoordinates: Coordinate[],
  fallbackCoordinate: Coordinate,
): NavigationCameraTarget | null {
  if (routeCoordinates.length < 2) {
    return destinationFallback(coordinate, fallbackCoordinate);
  }

  const longitudeScale = Math.cos((coordinate[1] * Math.PI) / 180);
  const pointX = coordinate[0] * longitudeScale;
  const pointY = coordinate[1];
  let nearestSegmentIndex = 0;
  let nearestSegmentProgress = 0;
  let nearestDistance = Number.POSITIVE_INFINITY;

  for (let index = 0; index < routeCoordinates.length - 1; index += 1) {
    const start = routeCoordinates[index]!;
    const end = routeCoordinates[index + 1]!;
    const startX = start[0] * longitudeScale;
    const startY = start[1];
    const segmentX = end[0] * longitudeScale - startX;
    const segmentY = end[1] - startY;
    const segmentLengthSquared = segmentX ** 2 + segmentY ** 2;
    const progress =
      segmentLengthSquared === 0
        ? 0
        : Math.min(
            1,
            Math.max(
              0,
              ((pointX - startX) * segmentX + (pointY - startY) * segmentY) /
                segmentLengthSquared,
            ),
          );
    const candidate: Coordinate = [
      start[0] + (end[0] - start[0]) * progress,
      start[1] + (end[1] - start[1]) * progress,
    ];
    const distance = distanceInMeters(coordinate, candidate);

    if (distance < nearestDistance) {
      nearestDistance = distance;
      nearestSegmentIndex = index;
      nearestSegmentProgress = progress;
    }
  }

  if (nearestDistance > NAVIGATION_ROUTE_SNAP_MAX_DISTANCE_METERS) {
    return destinationFallback(coordinate, fallbackCoordinate);
  }

  const segmentStart = routeCoordinates[nearestSegmentIndex]!;
  const segmentEnd = routeCoordinates[nearestSegmentIndex + 1]!;
  const anchorCoordinate: Coordinate = [
    segmentStart[0] + (segmentEnd[0] - segmentStart[0]) * nearestSegmentProgress,
    segmentStart[1] + (segmentEnd[1] - segmentStart[1]) * nearestSegmentProgress,
  ];
  let remainingMeters = NAVIGATION_LOOK_AHEAD_METERS;
  let cursor = anchorCoordinate;

  for (let index = nearestSegmentIndex + 1; index < routeCoordinates.length; index += 1) {
    const next = routeCoordinates[index]!;
    const segmentMeters = distanceInMeters(cursor, next);

    if (segmentMeters >= remainingMeters && segmentMeters > 0) {
      const ratio = remainingMeters / segmentMeters;
      const lookAheadCoordinate: Coordinate = [
        cursor[0] + (next[0] - cursor[0]) * ratio,
        cursor[1] + (next[1] - cursor[1]) * ratio,
      ];
      return {
        anchorCoordinate,
        heading: bearingDegrees(anchorCoordinate, lookAheadCoordinate),
      };
    }

    remainingMeters -= segmentMeters;
    cursor = next;
  }

  const routeEnd = routeCoordinates.at(-1) ?? fallbackCoordinate;
  if (distanceInMeters(anchorCoordinate, routeEnd) < 1) {
    const lastDistinctRouteCoordinate = routeCoordinates.findLast(
      (routeCoordinate) => distanceInMeters(routeCoordinate, routeEnd) >= 1,
    );
    if (lastDistinctRouteCoordinate === undefined) {
      return destinationFallback(coordinate, fallbackCoordinate);
    }
    return {
      anchorCoordinate,
      heading: bearingDegrees(lastDistinctRouteCoordinate, routeEnd),
    };
  }

  return {
    anchorCoordinate,
    heading: bearingDegrees(anchorCoordinate, routeEnd),
  };
}
