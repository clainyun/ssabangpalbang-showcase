import Mapbox, { type ImageEntry } from '@rnmapbox/maps';
import { type ComponentProps, useEffect, useMemo, useRef, useState } from 'react';

import { distanceInMeters, type Coordinate } from '@/lib/geo';
import { SEOUL_MIN_ZOOM_LEVEL } from '@/lib/mapBounds';
import type { Direction } from '@/store/characterStore';

import { CHARACTER_DISPLAY_SIZE } from './CharacterSprite';
import { resolveNativeMapSprite } from './nativeMapSpriteConfig';
import { SPRITE_FRAME_SIZE, type CharacterId, type MotionState } from './spriteConfig';
import { useIdleVariant } from './useIdleVariant';

interface NativeMapCharacterProps {
  coordinate: Coordinate;
  characterId: CharacterId;
  motion: MotionState;
  direction: Direction;
  reduceMotion: boolean;
  positionTweenMs: number;
}

const IMAGE_SCALE = SPRITE_FRAME_SIZE / CHARACTER_DISPLAY_SIZE;
const MAX_POSITION_TWEEN_DISTANCE_METERS = 100;

/**
 * 건물에 가려진 부분을 이어 보여 주는 실루엣의 불투명도. 올리면 건물 위에 떠 보이고,
 * 내리면 가려졌을 때 캐릭터를 놓칩니다.
 */
const GHOST_ICON_OPACITY = 0.32;

/** 본체와 실루엣이 정확히 겹쳐야 해서 스타일을 한곳에서 만듭니다. */
type SymbolLayerStyle = ComponentProps<typeof Mapbox.SymbolLayer>['style'];

function characterSymbolStyle(iconImage: string): SymbolLayerStyle {
  return {
    iconImage,
    iconAnchor: 'bottom',
    iconPitchAlignment: 'viewport',
    iconRotationAlignment: 'viewport',
    iconAllowOverlap: true,
    iconIgnorePlacement: true,
    iconSize: ['interpolate', ['linear'], ['zoom'], SEOUL_MIN_ZOOM_LEVEL, 0.65, 18, 1.15],
  };
}

/**
 * 캐릭터를 RN 화면 오버레이가 아니라 Mapbox의 지리 좌표 레이어에 그립니다.
 * 카메라 투영은 네이티브 렌더러가 매 프레임 처리하므로 지도 이동 중에도 발끝이 같은
 * 좌표에 붙어 있고, JS는 GPS 좌표와 현재 스프라이트 프레임만 갱신합니다.
 */
export function NativeMapCharacter({
  coordinate,
  characterId,
  motion,
  direction,
  reduceMotion,
  positionTweenMs,
}: NativeMapCharacterProps) {
  const idleVariant = useIdleVariant(motion);
  const sprite = useMemo(
    () => resolveNativeMapSprite(characterId, motion, direction, idleVariant),
    [characterId, direction, motion, idleVariant],
  );
  const [frameIndex, setFrameIndex] = useState(0);
  const [animatedPoint] = useState(
    () => new Mapbox.AnimatedPoint({ type: 'Point', coordinates: coordinate }),
  );
  const previousCoordinateRef = useRef(coordinate);

  useEffect(() => {
    animatedPoint.stopAnimation(() => undefined);
    const distance = distanceInMeters(previousCoordinateRef.current, coordinate);
    const animationConfig = {
      coordinates: coordinate,
      duration: distance > MAX_POSITION_TWEEN_DISTANCE_METERS ? 0 : positionTweenMs,
    };
    animatedPoint.timing(animationConfig).start();
    previousCoordinateRef.current = coordinate;
  }, [animatedPoint, coordinate, positionTweenMs]);

  useEffect(() => () => animatedPoint.stopAnimation(() => undefined), [animatedPoint]);

  useEffect(() => {
    if (reduceMotion || sprite.frames.length < 2) return;

    const interval = setInterval(() => {
      setFrameIndex((current) => (current + 1) % sprite.frames.length);
    }, 1000 / sprite.fps);

    return () => clearInterval(interval);
  }, [reduceMotion, sprite]);

  const images = useMemo<Record<string, ImageEntry>>(
    () =>
      Object.fromEntries(
        sprite.frames.map(({ id, source }) => [id, { image: source, scale: IMAGE_SCALE }]),
      ),
    [sprite],
  );
  const frame = sprite.frames[reduceMotion ? 0 : frameIndex % sprite.frames.length];
  if (frame === undefined) return null;

  return (
    <>
      <Mapbox.Images images={images} />
      <Mapbox.Animated.ShapeSource id="native-map-character-source" shape={animatedPoint}>
        {/*
          캐릭터를 두 겹으로 그립니다.

          - 'middle': 3D 건물이 정상적으로 가리는 본체. 이것만 쓰면 건물에 반쯤 잘려
            몸통이 뚝 끊겨 보입니다.
          - 'top': 건물 위에 항상 그려지는 옅은 실루엣. 가려진 부분이 이 레이어 덕분에
            흐리게라도 이어져 보여서, 잘리지도 않고 건물 위에 올라선 것처럼 보이지도
            않습니다(내비 앱이 터널·고가 구간에서 쓰는 방식과 같습니다).
        */}
        <Mapbox.SymbolLayer
          id="native-map-character-symbol"
          slot="middle"
          style={characterSymbolStyle(frame.id)}
        />
        <Mapbox.SymbolLayer
          id="native-map-character-symbol-ghost"
          slot="top"
          style={{ ...characterSymbolStyle(frame.id), iconOpacity: GHOST_ICON_OPACITY }}
        />
      </Mapbox.Animated.ShapeSource>
    </>
  );
}
