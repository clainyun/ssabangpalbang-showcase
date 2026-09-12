/**
 * 좌표 계산 유틸. 임장 반경(수백 m ~ 수 km) 수준에서는 구면 근사로 충분해서
 * 측지선(Vincenty) 대신 하버사인·구면 방위각을 씁니다.
 */
export type Coordinate = [number, number];

const EARTH_RADIUS_METERS = 6_371_000;

const toRadians = (degrees: number) => (degrees * Math.PI) / 180;
const toDegrees = (radians: number) => (radians * 180) / Math.PI;

/** 0~360 으로 정규화. 음수 나머지를 한 번 더 더해서 접습니다. */
export function normalizeDegrees(degrees: number): number {
  return ((degrees % 360) + 360) % 360;
}

/** 하버사인 거리(m) */
export function distanceInMeters(from: Coordinate, to: Coordinate): number {
  const latitudeDelta = toRadians(to[1] - from[1]);
  const longitudeDelta = toRadians(to[0] - from[0]);
  const fromLatitude = toRadians(from[1]);
  const toLatitude = toRadians(to[1]);
  const haversine =
    Math.sin(latitudeDelta / 2) ** 2 +
    Math.cos(fromLatitude) * Math.cos(toLatitude) * Math.sin(longitudeDelta / 2) ** 2;

  return 2 * EARTH_RADIUS_METERS * Math.asin(Math.sqrt(haversine));
}

/**
 * 짧은 거리의 북/동 이동량(m)을 WGS84 좌표에 더합니다.
 * 임장 시연처럼 수 m~수백 m 범위에서 쓰며 경도는 현재 위도의 cos 값으로 보정합니다.
 */
export function offsetCoordinateMeters(
  coordinate: Coordinate,
  northMeters: number,
  eastMeters: number,
): Coordinate {
  const latitudeRadians = toRadians(coordinate[1]);
  const latitudeDelta = toDegrees(northMeters / EARTH_RADIUS_METERS);
  const longitudeScale = Math.cos(latitudeRadians);
  const longitudeDelta =
    Math.abs(longitudeScale) < Number.EPSILON
      ? 0
      : toDegrees(eastMeters / (EARTH_RADIUS_METERS * longitudeScale));

  return [coordinate[0] + longitudeDelta, coordinate[1] + latitudeDelta];
}

/**
 * 화면 기준 이동량을 지도 방위만큼 돌려 실제 북/동 이동량으로 바꿉니다.
 *
 * 임장 지도는 진행 방향을 화면 위로 두고 회전하므로(`getNavigationCameraTarget` 의
 * heading) **화면 위가 정북이 아닙니다.** 조이스틱 입력을 그대로 북/동으로 쓰면
 * 지도가 돌아간 만큼 캐릭터가 엉뚱한 쪽으로 갑니다.
 *
 * `toScreenDirection`(월드 방위 → 화면 방향)의 역변환입니다. 지도가 정북이면
 * heading 이 0 이라 항등이 되므로 2D·정북 고정 상태를 따로 다룰 필요가 없습니다.
 */
export function rotateScreenOffsetToWorld(
  screenUpMeters: number,
  screenRightMeters: number,
  mapHeadingDegrees: number,
): { eastMeters: number; northMeters: number } {
  const headingRadians = toRadians(normalizeDegrees(mapHeadingDegrees));
  const cos = Math.cos(headingRadians);
  const sin = Math.sin(headingRadians);

  return {
    eastMeters: screenUpMeters * sin + screenRightMeters * cos,
    northMeters: screenUpMeters * cos - screenRightMeters * sin,
  };
}

/** 짧은 경로에서 좌표와 가장 가까운 선분 위 좌표를 찾습니다. */
export function nearestCoordinateOnLine(
  coordinate: Coordinate,
  line: Coordinate[],
): { coordinate: Coordinate; distanceMeters: number } | null {
  if (line.length < 2) return null;

  const latitudeRadians = toRadians(coordinate[1]);
  const longitudeScale = Math.cos(latitudeRadians);
  const pointX = coordinate[0] * longitudeScale;
  const pointY = coordinate[1];
  let nearestCoordinate = line[0]!;
  let nearestDistance = Number.POSITIVE_INFINITY;

  for (let index = 0; index < line.length - 1; index += 1) {
    const start = line[index]!;
    const end = line[index + 1]!;
    const startX = start[0] * longitudeScale;
    const startY = start[1];
    const endX = end[0] * longitudeScale;
    const endY = end[1];
    const segmentX = endX - startX;
    const segmentY = endY - startY;
    const segmentLengthSquared = segmentX ** 2 + segmentY ** 2;
    const progress =
      segmentLengthSquared === 0
        ? 0
        : Math.min(
            1,
            Math.max(
              0,
              ((pointX - startX) * segmentX + (pointY - startY) * segmentY) / segmentLengthSquared,
            ),
          );
    const candidate: Coordinate = [
      start[0] + (end[0] - start[0]) * progress,
      start[1] + (end[1] - start[1]) * progress,
    ];
    const candidateDistance = distanceInMeters(coordinate, candidate);

    if (candidateDistance < nearestDistance) {
      nearestCoordinate = candidate;
      nearestDistance = candidateDistance;
    }
  }

  return { coordinate: nearestCoordinate, distanceMeters: nearestDistance };
}

/**
 * 두 좌표를 잇는 방위각. 정북 0°, 시계방향 0~360.
 *
 * 캐릭터가 어느 방향으로 걷는지 판정할 때 씁니다. 위도가 올라갈수록 경도 1° 의
 * 실제 거리가 짧아지므로 경도차를 그냥 쓰면 고위도에서 방향이 틀어집니다.
 * 그래서 cos(latitude) 보정이 들어간 구면 공식을 씁니다.
 */
export function bearingDegrees(from: Coordinate, to: Coordinate): number {
  const fromLatitude = toRadians(from[1]);
  const toLatitude = toRadians(to[1]);
  const longitudeDelta = toRadians(to[0] - from[0]);

  const y = Math.sin(longitudeDelta) * Math.cos(toLatitude);
  const x =
    Math.cos(fromLatitude) * Math.sin(toLatitude) -
    Math.sin(fromLatitude) * Math.cos(toLatitude) * Math.cos(longitudeDelta);

  return normalizeDegrees(toDegrees(Math.atan2(y, x)));
}
