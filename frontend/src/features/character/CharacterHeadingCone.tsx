import Mapbox from '@rnmapbox/maps';
import { useMemo } from 'react';

import { PRIMARY_COLOR } from '@/constants/colors';
import { offsetCoordinateMeters, type Coordinate } from '@/lib/geo';

interface CharacterHeadingConeProps {
  coordinate: Coordinate;
  /** 캐릭터가 바라보는 실제 방위(도, 정북 기준 시계방향). null 이면 그리지 않습니다. */
  bearing: number | null;
}

/** 부채꼴이 퍼지는 각도(도). 너무 넓으면 방향이 아니라 원처럼 보입니다. */
const CONE_SPREAD_DEGREES = 62;

/**
 * 부채꼴 반지름(m)과 불투명도를 겹쳐 그라데이션처럼 보이게 합니다. 가까울수록 진하고
 * 멀수록 옅어지도록 큰 것부터 그립니다(뒤에 그린 것이 위로 올라옵니다).
 */
const CONE_BANDS: { radiusMeters: number; opacity: number }[] = [
  { radiusMeters: 26, opacity: 0.1 },
  { radiusMeters: 17, opacity: 0.14 },
  { radiusMeters: 9, opacity: 0.18 },
];

/** 부채꼴 호를 몇 조각으로 나눠 그릴지. 낮으면 각져 보입니다. */
const ARC_SEGMENTS = 12;

function toRadians(degrees: number): number {
  return (degrees * Math.PI) / 180;
}

/** 중심에서 bearing 방향으로 spread 만큼 퍼지는 부채꼴 폴리곤을 만듭니다. */
function buildSectorPolygon(
  center: Coordinate,
  bearing: number,
  radiusMeters: number,
): GeoJSON.Feature<GeoJSON.Polygon> {
  const start = bearing - CONE_SPREAD_DEGREES / 2;
  const ring: Coordinate[] = [center];

  for (let index = 0; index <= ARC_SEGMENTS; index += 1) {
    const angle = toRadians(start + (CONE_SPREAD_DEGREES * index) / ARC_SEGMENTS);
    // 방위는 정북 기준 시계방향이라 north = cos, east = sin 입니다.
    ring.push(
      offsetCoordinateMeters(
        center,
        Math.cos(angle) * radiusMeters,
        Math.sin(angle) * radiusMeters,
      ),
    );
  }
  ring.push(center);

  return { type: 'Feature', properties: {}, geometry: { type: 'Polygon', coordinates: [ring] } };
}

/**
 * 캐릭터가 보고 있는 방향을 바닥에 부채꼴로 깔아 줍니다(지도 앱의 방향 표시와 같은 역할).
 *
 * 캐릭터 스프라이트를 가리지 않도록 캐릭터보다 아래 slot 에 옅게 깔고, 지도에 붙는
 * fill 이라 지도를 기울이거나 돌려도 바닥에 그대로 누워 있습니다. 그래서 방향 표시
 * 동시에 "캐릭터가 이 바닥 지점에 서 있다"는 접지 단서도 됩니다.
 */
export function CharacterHeadingCone({ coordinate, bearing }: CharacterHeadingConeProps) {
  const bands = useMemo(() => {
    if (bearing === null || !Number.isFinite(bearing)) return null;
    return CONE_BANDS.map((band) => ({
      ...band,
      shape: buildSectorPolygon(coordinate, bearing, band.radiusMeters),
    }));
  }, [bearing, coordinate]);

  const point = useMemo<GeoJSON.Feature<GeoJSON.Point>>(
    () => ({
      type: 'Feature',
      properties: {},
      geometry: { type: 'Point', coordinates: coordinate },
    }),
    [coordinate],
  );

  return (
    <>
      {/* 발밑 접지 그림자. 부채꼴과 달리 방향을 몰라도 항상 깔려서, 캐릭터가 바닥
          어디에 서 있는지 알려 줍니다(떠 보이는 인상을 줄이는 실제 단서). */}
      <Mapbox.ShapeSource id="character-ground-shadow" shape={point}>
        <Mapbox.CircleLayer
          id="character-ground-shadow-circle"
          slot="middle"
          style={{
            circleColor: '#10271E',
            circleOpacity: 0.16,
            circleBlur: 0.7,
            // 지도에 눕혀 그려야 기울였을 때 타원으로 보여 바닥에 붙습니다.
            circlePitchAlignment: 'map',
            circleRadius: ['interpolate', ['linear'], ['zoom'], 15, 5, 18, 13],
          }}
        />
      </Mapbox.ShapeSource>

      {bands?.map((band) => (
        <Mapbox.ShapeSource
          id={`character-heading-cone-${band.radiusMeters}`}
          key={String(band.radiusMeters)}
          shape={band.shape}
        >
          <Mapbox.FillLayer
            id={`character-heading-cone-fill-${band.radiusMeters}`}
            slot="middle"
            style={{ fillColor: PRIMARY_COLOR, fillOpacity: band.opacity }}
          />
        </Mapbox.ShapeSource>
      ))}
    </>
  );
}
