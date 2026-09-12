import { File, Paths } from 'expo-file-system';
import { AppState } from 'react-native';

import {
  parseFieldTrackSnapshot,
  serializeFieldTrackSnapshot,
} from '@/features/field/fieldTrackSnapshot';
import {
  MAX_TRACK_POINTS,
  useFieldTrackStore,
  type FieldTrack,
} from '@/features/field/fieldTrackStore';

/**
 * 진행 중인 임장 궤적을 기기 디스크에 임시 보관합니다.
 *
 * 앱이 완전히 종료(강제 종료·OS 회수)됐다가 같은 임장으로 돌아왔을 때 그때까지 걸은
 * 경로가 통째로 사라지지 않게 하는 것이 유일한 목적입니다.
 *
 * - **서버로 보내지 않습니다.** 이 파일을 읽거나 쓰는 API 는 없습니다.
 * - 좌표는 민감정보라 값을 로그로 남기지 않습니다(실패해도 조용히 넘깁니다).
 * - SecureStore 는 항목당 크기 제한이 있어 수천 점 좌표에 맞지 않아 앱 전용 문서
 *   디렉터리의 파일 하나를 씁니다. 캐시 디렉터리는 임장 도중 OS 가 비울 수 있어
 *   쓰지 않습니다.
 * - 임장 종료 확정(stopAndFinishFieldTrack)·로그아웃(discardFieldTrack) 때 파일을
 *   지웁니다. 다음 사용자에게 이전 사용자의 경로가 남으면 안 됩니다.
 * - 읽기/쓰기 실패는 전부 삼킵니다. 저장이 안 되더라도 메모리 기록과 임장 진행은
 *   그대로 이어져야 합니다.
 */

/** 앱 전용 문서 디렉터리의 파일 이름. 임장은 동시에 하나뿐이라 파일도 하나면 충분합니다. */
const FIELD_TRACK_FILE_NAME = 'field-track.json';

/**
 * 좌표가 바뀔 때마다 디스크에 쓰면 I/O 가 과합니다. 마지막 변경 뒤 이 간격으로 한 번만
 * 씁니다. 그 사이에 앱이 죽으면 최대 이만큼의 최근 구간을 잃지만, 5 m/2 초 임계값 기준
 * 몇 점 수준입니다. 앱이 백그라운드로 내려가는 순간에는 기다리지 않고 곧바로 씁니다.
 */
const PERSIST_INTERVAL_MS = 5_000;

/**
 * 이보다 오래된 트랙은 복원하지 않고 파일째 지웁니다. 임장은 길어야 몇 시간이라 하루가
 * 지난 파일은 정리에 실패해 남은 잔여물로 보고, 좌표를 필요 이상으로 기기에 남기지 않습니다.
 */
const PERSISTED_TRACK_MAX_AGE_MS = 24 * 60 * 60 * 1_000;

let persistedSessionId: string | null = null;
let unsubscribeTrack: (() => void) | null = null;
let appStateSubscription: ReturnType<typeof AppState.addEventListener> | null = null;
let flushTimer: ReturnType<typeof setTimeout> | null = null;
let hasPendingWrite = false;

/**
 * 파일 핸들은 쓸 때마다 만듭니다. 모듈 최상위에서 만들면 앱이 뜨자마자(헤드리스 실행
 * 포함) 네이티브 파일시스템 모듈을 건드리게 되는데, 그렇게까지 일찍 필요하지 않습니다.
 */
function trackFile(): File | null {
  try {
    return new File(Paths.document, FIELD_TRACK_FILE_NAME);
  } catch {
    // 파일시스템을 쓸 수 없는 환경. 영속화만 포기하고 임장은 계속합니다.
    return null;
  }
}

/**
 * 저장된 궤적을 읽습니다. **같은 세션일 때만** 돌려주고, 다른 세션이거나 너무 오래됐거나
 * 읽을 수 없으면 파일을 지우고 null 을 돌려줍니다.
 */
export function loadPersistedFieldTrack(sessionId: string): FieldTrack | null {
  const file = trackFile();
  if (file === null) return null;

  let raw: string;
  try {
    if (!file.exists) return null;
    raw = file.textSync();
  } catch {
    // 읽기 실패. 남은 파일이 계속 실패를 만들지 않도록 지우고 새로 시작합니다.
    deletePersistedFieldTrack();
    return null;
  }

  const restored = parseFieldTrackSnapshot(raw, {
    sessionId,
    maxPoints: MAX_TRACK_POINTS,
    maxAgeMs: PERSISTED_TRACK_MAX_AGE_MS,
    now: Date.now(),
  });
  if (restored === null) {
    deletePersistedFieldTrack();
    return null;
  }

  return restored;
}

/** 저장 파일을 지웁니다. 없으면 아무 일도 하지 않습니다. */
export function deletePersistedFieldTrack(): void {
  const file = trackFile();
  if (file === null) return;

  try {
    if (file.exists) file.delete();
  } catch {
    // 삭제 실패. 다음 복원 시도에서 세션 불일치·만료로 다시 지워집니다.
  }
}

function writeTrackNow(): void {
  hasPendingWrite = false;

  const sessionId = persistedSessionId;
  if (sessionId === null) return;

  const track = useFieldTrackStore.getState().track;
  // 다른 세션으로 넘어갔거나 이미 끝난 트랙이면 저장할 이유가 없습니다.
  if (track === null || track.sessionId !== sessionId || track.endedAt !== null) return;

  const file = trackFile();
  if (file === null) return;

  try {
    if (!file.exists) file.create({ intermediates: true, overwrite: true });
    file.write(serializeFieldTrackSnapshot(track));
  } catch {
    // 저장 실패는 임장을 막지 않습니다. 다음 변경 때 다시 시도합니다.
  }
}

function schedulePersist(): void {
  hasPendingWrite = true;
  if (flushTimer !== null) return;

  flushTimer = setTimeout(() => {
    flushTimer = null;
    if (hasPendingWrite) writeTrackNow();
  }, PERSIST_INTERVAL_MS);
}

/** 예약된 저장이 남아 있으면 기다리지 않고 지금 씁니다. */
export function flushPersistedFieldTrack(): void {
  if (flushTimer !== null) {
    clearTimeout(flushTimer);
    flushTimer = null;
  }
  if (hasPendingWrite) writeTrackNow();
}

/**
 * 해당 세션의 궤적을 디스크에 따라 쓰기 시작합니다. 같은 세션으로 다시 불러도 구독을
 * 새로 만들지 않습니다(러너와 같은 이유로 여러 번 호출되는 것이 정상입니다).
 */
export function startPersistingFieldTrack(sessionId: string): void {
  if (persistedSessionId === sessionId) return;

  stopPersistingFieldTrack({ discard: false });
  persistedSessionId = sessionId;

  unsubscribeTrack = useFieldTrackStore.subscribe((state, previousState) => {
    if (state.track === previousState.track) return;

    schedulePersist();
  });

  appStateSubscription = AppState.addEventListener('change', (nextState) => {
    // 화면을 끄거나 홈으로 나가는 순간이 프로세스가 회수될 수 있는 시점이라,
    // 예약된 쓰기를 기다리지 않고 곧바로 디스크에 밀어 넣습니다.
    if (nextState === 'background' || nextState === 'inactive') {
      flushPersistedFieldTrack();
    }
  });
}

/**
 * 따라 쓰기를 멈춥니다.
 *
 * - `discard: false` — 남은 변경을 마지막으로 한 번 쓰고 구독만 끊습니다(세션·모드 교체).
 * - `discard: true` — 저장 파일을 지웁니다(임장 종료 확정·로그아웃).
 */
export function stopPersistingFieldTrack({ discard }: { discard: boolean }): void {
  unsubscribeTrack?.();
  unsubscribeTrack = null;
  appStateSubscription?.remove();
  appStateSubscription = null;

  if (flushTimer !== null) {
    clearTimeout(flushTimer);
    flushTimer = null;
  }

  if (discard) {
    hasPendingWrite = false;
    persistedSessionId = null;
    deletePersistedFieldTrack();
    return;
  }

  if (hasPendingWrite) writeTrackNow();
  persistedSessionId = null;
}
