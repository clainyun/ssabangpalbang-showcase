import { create } from 'zustand';

import { distanceInMeters, type Coordinate } from '@/lib/geo';

/**
 * 임장 이동 궤적(러닝 앱의 "오늘 뛴 경로"에 해당). **기기 로컬 전용**입니다.
 *
 * - 좌표 배열은 서버로 보내지 않습니다. 어떤 API도 이 값을 읽거나 쓰지 않습니다.
 * - 좌표는 민감정보라 로그로도 남기지 않습니다.
 * - 화면 수명과 분리된 모듈 스코프 스토어라 임장 화면 ↔ 다른 화면을 오가도 기록이
 *   이어집니다(수집기는 fieldTrackRunner.ts).
 * - 앱을 완전히 종료해도 기록이 사라지지 않도록 기기 로컬 파일에 따라 씁니다
 *   (fieldTrackStorage.ts). 이 스토어 자체는 저장을 모르고, 러너가 같은 세션으로
 *   돌아왔을 때만 restoreTrack 으로 되돌려 놓습니다.
 * - 동시에 진행할 수 있는 임장은 하나뿐이라 트랙도 한 개만 들고 있습니다.
 *
 * ⚠️ src/store/characterStore.ts 의 경고와 같은 이유로, 여기 들어오는 값은
 * 아래 임계값으로 걸러 낸 저빈도 샘플(수 초에 하나)이어야 합니다.
 */

/**
 * 직전 점과 이만큼도 안 떨어진 샘플은 버립니다.
 *
 * 서 있는 동안에도 GPS 는 수 m 씩 튀어서, 거르지 않으면 제자리에서 실뭉치 같은
 * 궤적이 그려집니다. 보행 속도(약 1.4 m/s)에서는 5 m 가 3~4초에 한 점이라
 * 경로 모양을 잃지 않으면서 정지 중 잡음만 걸러 냅니다.
 */
export const MIN_POINT_DISTANCE_METERS = 5;

/**
 * 샘플 간 최소 시간 간격. 위치 콜백은 초당 여러 번 올 수 있어서(개발자 조이스틱은
 * 더 잦습니다) 점 개수를 시간으로도 한 번 더 눌러 둡니다.
 */
export const MIN_POINT_INTERVAL_MS = 2_000;

/**
 * 이보다 부정확한 샘플은 버립니다. 실내·지하로 들어가 셀타워/와이파이 추정으로
 * 떨어지면 정확도가 수십~수백 m 로 뛰는데, 그 값을 그대로 이으면 궤적이 몇 블록씩
 * 순간이동합니다. 50 m 는 도심 보행에서 "쓸 만한 GPS"의 통상 상한입니다.
 */
export const MAX_ACCURACY_METERS = 50;

/**
 * 메모리 보호용 점 개수 상한. 5 m 간격이면 약 25 km 분량이라 하루 임장으로는
 * 닿기 어려운 값입니다. 넘으면 오래된 쪽부터 버리지 않고 전체를 절반으로
 * 솎아 내서(decimatePoints) 경로 모양과 시작점을 유지합니다.
 */
export const MAX_TRACK_POINTS = 5_000;

export interface FieldTrackPoint {
  coordinate: Coordinate;
  timestamp: number;
}

export interface FieldTrack {
  sessionId: string;
  points: FieldTrackPoint[];
  startedAt: number | null;
  endedAt: number | null;
  /**
   * 누적 이동거리(m). 점 배열에서 매번 다시 더하지 않고 받아들인 샘플마다 누적합니다.
   * 상한을 넘겨 점을 솎아 내도 실제로 걸은 거리가 줄지 않게 하려는 목적도 있습니다.
   */
  totalDistanceMeters: number;
}

export interface FieldTrackSample {
  coordinate: Coordinate;
  timestamp: number;
  /** expo-location 의 coords.accuracy(m). 모르면 null/undefined 로 두고 통과시킵니다. */
  accuracyMeters?: number | null;
}

interface FieldTrackState {
  track: FieldTrack | null;
  restoreTrack: (track: FieldTrack) => void;
  startTrack: (sessionId: string) => void;
  appendPoint: (
    sessionId: string,
    sample: FieldTrackSample,
    options?: { isFinal?: boolean },
  ) => void;
  finishTrack: (sessionId: string) => void;
  clearTrack: (sessionId: string) => void;
}

function isUsableCoordinate(coordinate: Coordinate): boolean {
  const [longitude, latitude] = coordinate;

  return (
    Number.isFinite(longitude) &&
    Number.isFinite(latitude) &&
    Math.abs(longitude) <= 180 &&
    Math.abs(latitude) <= 90
  );
}

/**
 * 점을 하나 걸러 하나씩 남겨 절반으로 줄입니다. 첫 점(출발지)과 마지막 점(현재
 * 위치)은 요약 화면의 시작·종료 마커가 되므로 반드시 남깁니다.
 */
function decimatePoints(points: FieldTrackPoint[]): FieldTrackPoint[] {
  const kept = points.filter((_, index) => index % 2 === 0);
  const last = points[points.length - 1];

  if (last !== undefined && kept[kept.length - 1] !== last) {
    kept.push(last);
  }

  return kept;
}

export const useFieldTrackStore = create<FieldTrackState>((set) => ({
  track: null,

  /**
   * 앱이 완전히 종료됐다 같은 임장으로 돌아왔을 때, 디스크에 남아 있던 궤적을 되돌려
   * 놓습니다(fieldTrackStorage.ts). 메모리에 이미 트랙이 있으면 그쪽이 최신이므로
   * 건드리지 않고, 이미 끝난 트랙도 되살리지 않습니다.
   */
  restoreTrack: (track) =>
    set((state) => {
      if (state.track !== null) return state;
      if (track.sessionId.length === 0 || track.endedAt !== null) return state;

      return { track };
    }),

  startTrack: (sessionId) =>
    set((state) => {
      // 같은 세션을 다시 시작해도(화면 재진입 등) 지금까지 모은 궤적을 버리지 않습니다.
      if (state.track !== null && state.track.sessionId === sessionId) return state;

      return {
        track: {
          sessionId,
          points: [],
          startedAt: Date.now(),
          endedAt: null,
          totalDistanceMeters: 0,
        },
      };
    }),

  appendPoint: (sessionId, { coordinate, timestamp, accuracyMeters }, options) =>
    set((state) => {
      const track = state.track;
      if (track === null || track.sessionId !== sessionId || track.endedAt !== null) return state;
      if (!isUsableCoordinate(coordinate)) return state;
      if (
        accuracyMeters !== null &&
        accuracyMeters !== undefined &&
        (!Number.isFinite(accuracyMeters) || accuracyMeters > MAX_ACCURACY_METERS)
      ) {
        return state;
      }

      const sampleTimestamp = Number.isFinite(timestamp) ? timestamp : Date.now();
      const previous = track.points[track.points.length - 1];
      let movedMeters = 0;

      if (previous !== undefined) {
        movedMeters = distanceInMeters(previous.coordinate, coordinate);

        // 임장을 종료한 자리는 "직전 점에서 5m 이상, 2초 이상 지났는가"와 무관하게 반드시
        // 남아야 합니다. 이 점이 요약 화면의 도착 마커가 되기 때문입니다. 정지 중 잡음
        // 필터는 기록 중에 들어오는 나머지 샘플에만 겁니다.
        if (options?.isFinal !== true) {
          if (sampleTimestamp - previous.timestamp < MIN_POINT_INTERVAL_MS) return state;
          if (movedMeters < MIN_POINT_DISTANCE_METERS) return state;
        }
      }

      const appended = [...track.points, { coordinate, timestamp: sampleTimestamp }];

      return {
        track: {
          ...track,
          points: appended.length > MAX_TRACK_POINTS ? decimatePoints(appended) : appended,
          totalDistanceMeters: track.totalDistanceMeters + movedMeters,
        },
      };
    }),

  finishTrack: (sessionId) =>
    set((state) => {
      const track = state.track;
      if (track === null || track.sessionId !== sessionId || track.endedAt !== null) return state;

      return { track: { ...track, endedAt: Date.now() } };
    }),

  clearTrack: (sessionId) =>
    set((state) =>
      state.track !== null && state.track.sessionId === sessionId ? { track: null } : state,
    ),
}));

/** 렌더 밖(콜백·모듈 스코프)에서 현재 트랙을 읽습니다. */
export function getFieldTrack(sessionId: string): FieldTrack | null {
  const track = useFieldTrackStore.getState().track;

  return track !== null && track.sessionId === sessionId ? track : null;
}

/** 지금 기록 중이거나 방금 끝난 트랙의 세션 식별자. 없으면 null. */
export function getActiveFieldTrackSessionId(): string | null {
  return useFieldTrackStore.getState().track?.sessionId ?? null;
}

/**
 * 해당 세션의 트랙을 구독합니다. 다른 세션이면 null 을 돌려주되, 같은 세션인
 * 동안에는 스토어가 들고 있는 객체를 그대로 반환해 참조가 흔들리지 않게 합니다.
 */
export function useFieldTrack(sessionId: string): FieldTrack | null {
  return useFieldTrackStore((state) =>
    state.track !== null && state.track.sessionId === sessionId ? state.track : null,
  );
}

/** 임장에 걸린 시간(ms). 아직 안 끝났으면 지금까지 걸린 시간을 씁니다. */
export function fieldTrackDurationMs(track: FieldTrack, now = Date.now()): number {
  if (track.startedAt === null) return 0;

  return Math.max(0, (track.endedAt ?? now) - track.startedAt);
}
