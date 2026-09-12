import type { FieldTrack, FieldTrackPoint } from '@/features/field/fieldTrackStore';

/**
 * 임장 궤적을 디스크에 담는 형식(순수 로직). 파일 입출력은 fieldTrackStorage.ts 가 맡고,
 * 여기서는 "무엇을 쓰고, 무엇을 되살릴지"만 정합니다.
 *
 * 되살리는 쪽을 특히 깐깐하게 봅니다. 손상되거나 남의 세션인 파일을 그대로 믿으면
 * 궤적이 엉뚱한 곳으로 튀거나 다른 사용자의 경로가 화면에 나타날 수 있습니다.
 */

/** 저장 형식 버전. 형식을 바꾸면 올려서 예전 파일을 조용히 버리게 합니다. */
export const FIELD_TRACK_SNAPSHOT_VERSION = 1;

/** 좌표 한 점을 `[경도, 위도, epoch ms]` 로 저장합니다. 객체로 두는 것보다 파일이 3배쯤 작습니다. */
type SnapshotPoint = [number, number, number];

interface FieldTrackSnapshot {
  version: number;
  sessionId: string;
  startedAt: number | null;
  totalDistanceMeters: number;
  points: SnapshotPoint[];
}

export interface ParseFieldTrackSnapshotOptions {
  /** 이 세션의 궤적일 때만 되살립니다. 다른 임장(또는 다른 사용자)의 좌표는 이어 그리지 않습니다. */
  sessionId: string;
  /** 스토어의 점 개수 상한(MAX_TRACK_POINTS). 넘으면 우리가 쓴 파일이 아닙니다. */
  maxPoints: number;
  /** 이보다 오래된 궤적은 되살리지 않습니다. */
  maxAgeMs: number;
  now: number;
}

/** 진행 중인 트랙을 파일에 쓸 문자열로 만듭니다. */
export function serializeFieldTrackSnapshot(track: FieldTrack): string {
  const snapshot: FieldTrackSnapshot = {
    version: FIELD_TRACK_SNAPSHOT_VERSION,
    sessionId: track.sessionId,
    startedAt: track.startedAt,
    totalDistanceMeters: track.totalDistanceMeters,
    points: track.points.map(({ coordinate, timestamp }) => [
      coordinate[0],
      coordinate[1],
      timestamp,
    ]),
  };

  return JSON.stringify(snapshot);
}

function isUsablePoint(entry: unknown): entry is SnapshotPoint {
  if (!Array.isArray(entry) || entry.length !== 3) return false;

  const [longitude, latitude, timestamp] = entry as unknown[];

  return (
    typeof longitude === 'number' &&
    typeof latitude === 'number' &&
    typeof timestamp === 'number' &&
    Number.isFinite(longitude) &&
    Number.isFinite(latitude) &&
    Number.isFinite(timestamp) &&
    Math.abs(longitude) <= 180 &&
    Math.abs(latitude) <= 90
  );
}

/**
 * 파일 내용을 트랙으로 되돌립니다. 조금이라도 이상하면 통째로 버립니다(null) —
 * 부른 쪽은 null 을 "이 파일은 지우고 새로 시작"으로 다룹니다.
 */
export function parseFieldTrackSnapshot(
  raw: string,
  { sessionId, maxPoints, maxAgeMs, now }: ParseFieldTrackSnapshotOptions,
): FieldTrack | null {
  let parsed: unknown;
  try {
    parsed = JSON.parse(raw);
  } catch {
    return null;
  }

  if (typeof parsed !== 'object' || parsed === null) return null;

  const candidate = parsed as Partial<FieldTrackSnapshot>;
  if (candidate.version !== FIELD_TRACK_SNAPSHOT_VERSION) return null;
  if (candidate.sessionId !== sessionId || sessionId.length === 0) return null;
  if (!Array.isArray(candidate.points)) return null;
  // 스토어 상한을 넘는 파일은 우리가 쓴 것이 아닙니다.
  if (candidate.points.length > maxPoints) return null;

  const points: FieldTrackPoint[] = [];
  for (const entry of candidate.points) {
    if (!isUsablePoint(entry)) continue;

    points.push({ coordinate: [entry[0], entry[1]], timestamp: entry[2] });
  }

  const startedAt =
    typeof candidate.startedAt === 'number' && Number.isFinite(candidate.startedAt)
      ? candidate.startedAt
      : null;
  const lastTimestamp = points[points.length - 1]?.timestamp ?? startedAt;
  if (lastTimestamp !== null && now - lastTimestamp > maxAgeMs) return null;

  const totalDistanceMeters =
    typeof candidate.totalDistanceMeters === 'number' &&
    Number.isFinite(candidate.totalDistanceMeters) &&
    candidate.totalDistanceMeters >= 0
      ? candidate.totalDistanceMeters
      : 0;

  return {
    sessionId,
    points,
    startedAt,
    // 저장 대상은 "진행 중인" 트랙뿐이라 끝난 트랙은 애초에 파일에 남지 않습니다.
    endedAt: null,
    totalDistanceMeters,
  };
}
