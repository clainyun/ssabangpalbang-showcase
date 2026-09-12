import type { Position } from 'geojson';

/**
 * 서울특별시 행정구역 전체를 감싸는 카메라 이동 제한 범위입니다.
 * Mapbox 좌표는 항상 [경도, 위도] 순서입니다.
 */
export const SEOUL_CAMERA_BOUNDS: { sw: Position; ne: Position } = {
  sw: [126.7644, 37.4133],
  ne: [127.1843, 37.7151],
};

/**
 * 일반 지도 탭에서 사용하는 이동 제한 범위입니다.
 * 서울 전역과 인접 지역을 함께 볼 수 있도록 서울 경계 바깥에 완충 범위를 둡니다.
 */
export const MAP_TAB_CAMERA_BOUNDS: { sw: Position; ne: Position } = {
  sw: [126.6, 37.25],
  ne: [127.38, 37.88],
};

/** 해외·전국 단위까지 축소되지 않으면서 서울 안을 탐색할 수 있는 최소 줌입니다. */
export const SEOUL_MIN_ZOOM_LEVEL = 11.5;

/**
 * 이 줌 미만이면 탭 지도가 자치구 카드 모드로 전환됩니다.
 *
 * 10.8 은 화면 가로 약 15km 로, 서울 절반 이상이 들어오는 '도시 개관' 구간입니다.
 * Mapbox 는 512px 타일이라 m/px = 40075016.686 * cos(위도) / (512 * 2^zoom) 입니다
 * (위도 37.55° 기준 62,065 / 2^zoom). 256px 타일 공식으로 잡으면 거리가 2배로 나와
 * 줌이 한 단계씩 어긋납니다 — 실제로 11.5(8.4km)로 뒀다가 "가까운데도 구로 보인다"가 났습니다.
 */
export const DISTRICT_MODE_MAX_ZOOM = 12.2;

/**
 * 탭 지도에서만 쓰는 최소 줌. 세로형 휴대폰에서 바텀시트가 펼쳐져 있어도
 * 서울 전역과 인접 지역을 함께 볼 수 있도록 충분한 축소 범위를 허용합니다.
 * 위 SEOUL_MIN_ZOOM_LEVEL(11.5) 은 임장 지도와 캐릭터 레이어가 공유하므로 건드리지 않습니다.
 * maxBounds(MAP_TAB_CAMERA_BOUNDS) 로 전국·해외까지 이동하는 것은 제한합니다.
 */
export const MAP_TAB_MIN_ZOOM_LEVEL = 8.0;
