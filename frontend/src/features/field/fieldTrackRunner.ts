import * as Location from 'expo-location';
import * as TaskManager from 'expo-task-manager';

import { PRIMARY_COLOR } from '@/constants/colors';
import {
  loadPersistedFieldTrack,
  startPersistingFieldTrack,
  stopPersistingFieldTrack,
} from '@/features/field/fieldTrackStorage';
import {
  MAX_ACCURACY_METERS,
  MIN_POINT_DISTANCE_METERS,
  MIN_POINT_INTERVAL_MS,
  useFieldTrackStore,
} from '@/features/field/fieldTrackStore';
import type { Coordinate } from '@/lib/geo';
import { useDeveloperLocationStore } from '@/store/developerLocationStore';

/**
 * 임장 궤적 수집기.
 *
 * 화면을 꺼 두거나 다른 앱을 보는 동안에도 그 구간이 비지 않도록,
 * expo-location 의 **포그라운드 서비스**(TaskManager 위치 태스크)로 수집합니다.
 * 임장 중에는 "임장 기록 중" 알림이 상단에 떠 있고, 그 알림이 있는 동안에만 위치가
 * 들어옵니다. 사용자가 지금 위치가 수집되는지 항상 볼 수 있는 방식입니다.
 *
 * ACCESS_BACKGROUND_LOCATION 은 계속 차단합니다(app.config.ts 의 blockedPermissions).
 * foregroundServiceType=location 은 그 권한 없이도 동작하고, expo-location 도 두 경로를
 * 구분합니다 — 포그라운드 서비스를 쓰면 백그라운드 위치 권한을 요구하지 않습니다.
 *
 * 수집 경로는 이 태스크 하나뿐입니다. watchPositionAsync 를 함께 두면 같은 좌표가
 * 두 경로로 들어와 중복 집계되므로 포그라운드 구독은 남기지 않았습니다.
 *
 * "앱 안에서 다른 화면으로 이동"하는 동안에도 계속 기록돼야 해서 수집 상태를 컴포넌트가
 * 아니라 이 모듈이 들고 있습니다. 임장 화면이 언마운트돼도 살아 있고, 임장 종료
 * (stopAndFinishFieldTrack)·로그아웃(discardFieldTrack) 때만 끊깁니다.
 */

type RunnerMode = 'gps' | 'developer';

/**
 * 위치 태스크 이름. 등록 정보는 OS 에 남으므로 값을 바꾸면 예전 이름으로 등록된 태스크가
 * 고아로 남습니다.
 */
const FIELD_TRACK_LOCATION_TASK = 'ssabangpalbang-field-track-location';

let runningSessionId: string | null = null;
let runningMode: RunnerMode | null = null;
let developerUnsubscribe: (() => void) | null = null;

/**
 * 위치 서비스 시작/중지를 한 줄로 세웁니다. 세션·모드가 바뀔 때 중지와 시작이 겹쳐
 * 들어가면 "중지가 시작보다 늦게 끝나 서비스가 죽어 있는" 상태가 될 수 있습니다.
 */
let locationServiceQueue: Promise<void> = Promise.resolve();

function enqueueLocationServiceAction(action: () => Promise<void>): Promise<void> {
  // 큐는 항상 catch 로 끝나 거부되지 않으므로, 앞선 작업이 실패해도 다음 작업은 실행됩니다.
  locationServiceQueue = locationServiceQueue.then(action).catch(() => undefined);

  return locationServiceQueue;
}

function isStillRunning(sessionId: string, mode: RunnerMode): boolean {
  return runningSessionId === sessionId && runningMode === mode;
}

async function hasStartedLocationService(): Promise<boolean> {
  try {
    return await Location.hasStartedLocationUpdatesAsync(FIELD_TRACK_LOCATION_TASK);
  } catch {
    // 태스크 모듈을 쓸 수 없는 환경. 시작된 적이 없는 것으로 봅니다.
    return false;
  }
}

/** 포그라운드 서비스를 내립니다. 알림이 남지 않게 하는 유일한 경로입니다. */
function stopLocationService(): Promise<void> {
  return enqueueLocationServiceAction(async () => {
    // 줄을 서서 기다리는 사이에 새 임장이 시작됐으면 그 수집을 끊지 않습니다.
    // 정상적인 중지 경로는 모두 runningSessionId 를 먼저 비우고 부릅니다.
    if (runningSessionId !== null && runningMode === 'gps') return;
    if (!(await hasStartedLocationService())) return;

    try {
      await Location.stopLocationUpdatesAsync(FIELD_TRACK_LOCATION_TASK);
    } catch {
      // 이미 내려갔거나 태스크가 없는 경우. 더 할 일이 없습니다.
    }
  });
}

/** 시작에 성공했으면 true. 실패해도 임장 자체는 계속 진행되고 다음 호출에서 다시 시도합니다. */
function startLocationService(sessionId: string): Promise<boolean> {
  let started = false;

  return enqueueLocationServiceAction(async () => {
    // 줄을 서서 기다리는 사이에 종료·세션 교체가 일어났으면 시작하지 않습니다.
    if (!isStillRunning(sessionId, 'gps')) return;

    // 태스크 등록 정보는 OS(SharedPreferences)에 남고, 앱이 강제 종료된 뒤 프로세스가 다시
    // 뜨면 TaskManager 가 그 등록을 복원합니다. 그래서 "등록돼 있다"는 "지금 수집 중이다"를
    // 뜻하지 않습니다 — killServiceOnDestroy 로 서비스만 죽어 있거나, 백그라운드에서
    // 포그라운드 서비스를 띄우지 못해 알림 없이 등록만 남아 있을 수 있습니다. 그 상태를
    // "이미 시작됨"으로 믿으면 알림도 좌표도 영영 오지 않으므로, 남은 등록은 걷어내고
    // 항상 새로 시작합니다.
    if (await hasStartedLocationService()) {
      try {
        await Location.stopLocationUpdatesAsync(FIELD_TRACK_LOCATION_TASK);
      } catch {
        // 이미 내려가 있으면 그대로 새로 시작하면 됩니다.
      }
      if (!isStillRunning(sessionId, 'gps')) return;
    }

    try {
      await Location.startLocationUpdatesAsync(FIELD_TRACK_LOCATION_TASK, {
        // 보행 궤적이라 정확도가 곧 품질입니다. Balanced 는 수십 m 오차가 흔해
        // 골목 단위 경로가 뭉개집니다.
        accuracy: Location.Accuracy.High,
        // 스토어의 채택 임계값과 같은 값을 네이티브에도 알려 줘, 어차피 버릴
        // 샘플로 JS 브리지를 깨우지 않게 합니다.
        timeInterval: MIN_POINT_INTERVAL_MS,
        distanceInterval: MIN_POINT_DISTANCE_METERS,
        foregroundService: {
          notificationTitle: '임장 기록 중',
          notificationBody: '이동 경로를 기록하고 있어요',
          notificationColor: PRIMARY_COLOR,
          // 앱을 완전히 종료하면 서비스도 함께 내려갑니다. 사용자가 앱을 치웠는데
          // 알림만 남아 위치가 계속 수집되는 상태를 만들지 않습니다.
          killServiceOnDestroy: true,
        },
      });
      started = true;
    } catch {
      // 위치 서비스가 꺼져 있거나, 앱이 막 백그라운드로 내려가 포그라운드 서비스를
      // 시작할 수 없는 경우. 궤적만 비고 임장은 그대로 진행됩니다.
    }
  }).then(() => started);
}

/**
 * 포그라운드 서비스가 보내는 위치 배치를 받습니다. 태스크 정의는 모듈 최상위에서 딱
 * 한 번만 실행돼야 합니다(앱이 헤드리스로 살아나도 이 파일이 평가되면서 등록됩니다).
 */
TaskManager.defineTask<{ locations?: Location.LocationObject[] }>(
  FIELD_TRACK_LOCATION_TASK,
  async ({ data, error }) => {
    if (error !== null) return;

    const sessionId = runningSessionId;
    if (sessionId === null || runningMode !== 'gps') {
      // 러너는 멈췄는데 위치가 들어온다 = 앱이 내려간 뒤 서비스만 남은 상태입니다.
      // 알림과 배터리 소모를 남기지 않도록 여기서 정리합니다.
      await stopLocationService();
      return;
    }

    const appendPoint = useFieldTrackStore.getState().appendPoint;
    for (const location of data?.locations ?? []) {
      appendPoint(sessionId, {
        coordinate: [location.coords.longitude, location.coords.latitude],
        timestamp: location.timestamp ?? Date.now(),
        accuracyMeters: location.coords.accuracy,
      });
    }
  },
);

/**
 * 시작에 실패했을 때 다음 호출에서 다시 시도할 수 있도록 상태를 되돌립니다. 그 사이에
 * 다른 세션이 시작됐다면 건드리지 않습니다.
 */
function abortRun(sessionId: string, mode: RunnerMode): void {
  if (!isStillRunning(sessionId, mode)) return;

  runningSessionId = null;
  runningMode = null;
  // 모아 둔 좌표는 남깁니다(파일도 그대로). 재시도하면 이어서 기록합니다.
  stopPersistingFieldTrack({ discard: false });
}

/**
 * 궤적 수집을 시작합니다. 같은 세션·같은 모드로 다시 불러도 수집을 새로 만들지
 * 않습니다(임장 화면 재진입·리렌더로 여러 번 호출되는 것이 정상입니다).
 *
 * 앱이 완전히 종료됐다 같은 임장으로 돌아온 경우, 디스크에 남아 있던 궤적을 먼저
 * 되돌려 놓고 그 뒤에 이어서 기록합니다. 저장된 세션이 다르면 그 파일은 버립니다.
 *
 * 개발자 가짜 GPS 모드에서는 실제 위치 수집을 아예 시작하지 않습니다. 임장 화면이
 * isDeveloperMode 로 Mapbox.UserLocation 렌더를 배타 처리하는 것과 같은 이유로,
 * 두 좌표원이 같은 트랙에 섞여 들어가지 않게 하려는 것입니다.
 */
export async function startFieldTrackRunner(
  sessionId: string,
  { useDeveloperLocation }: { useDeveloperLocation: boolean },
): Promise<void> {
  if (sessionId.length === 0) return;

  const mode: RunnerMode = useDeveloperLocation ? 'developer' : 'gps';
  if (isStillRunning(sessionId, mode)) return;

  stopFieldTrackRunner();
  runningSessionId = sessionId;
  runningMode = mode;

  const restored = loadPersistedFieldTrack(sessionId);
  if (restored !== null) {
    useFieldTrackStore.getState().restoreTrack(restored);
  }
  useFieldTrackStore.getState().startTrack(sessionId);
  startPersistingFieldTrack(sessionId);

  const appendPoint = useFieldTrackStore.getState().appendPoint;

  if (mode === 'developer') {
    // 조이스틱이 아직 움직이기 전이라도 현재 서 있는 자리를 출발점으로 남깁니다.
    const { currentCoordinate } = useDeveloperLocationStore.getState();
    if (currentCoordinate !== null) {
      appendPoint(sessionId, { coordinate: currentCoordinate, timestamp: Date.now() });
    }

    developerUnsubscribe = useDeveloperLocationStore.subscribe((state, previousState) => {
      const coordinate = state.currentCoordinate;
      if (coordinate === null || coordinate === previousState.currentCoordinate) return;

      appendPoint(sessionId, { coordinate, timestamp: Date.now() });
    });
    return;
  }

  // 권한 요청은 임장 화면이 이미 안내 문구와 함께 처리합니다. 여기서는 허용 상태만
  // 확인하고, 아직이면 조용히 물러나 다음 호출에서 다시 시도되게 둡니다.
  const permission = await Location.getForegroundPermissionsAsync().catch(() => null);
  if (permission === null || !permission.granted) {
    abortRun(sessionId, mode);
    return;
  }
  if (!isStillRunning(sessionId, mode)) return;

  const started = await startLocationService(sessionId);
  if (!started) {
    abortRun(sessionId, mode);
  }
}

/**
 * 수집을 모두 멈춥니다(포그라운드 서비스 알림도 내려갑니다). 트랙 자체는 메모리와
 * 디스크에 남겨, 요약 화면이 그대로 읽고 다시 들어와도 이어 쓸 수 있게 둡니다.
 */
export function stopFieldTrackRunner(): void {
  runningSessionId = null;
  runningMode = null;
  developerUnsubscribe?.();
  developerUnsubscribe = null;
  void stopLocationService();
  stopPersistingFieldTrack({ discard: false });
}

/**
 * 로그아웃·세션 만료처럼 "이 사용자의 임장이 더 이상 진행되지 않는" 시점에 부릅니다.
 * 수집을 끊고 메모리와 디스크에 남은 좌표까지 버립니다. 좌표는 민감정보라, 다음 사용자가
 * 같은 기기에서 로그인했을 때 이전 사용자의 경로가 남아 있으면 안 됩니다.
 */
export function discardFieldTrack(): void {
  const sessionId = useFieldTrackStore.getState().track?.sessionId ?? null;

  // 저장 파일을 먼저 지웁니다. 그래야 아래 stop 이 남은 변경을 디스크에 다시 쓰지 않습니다.
  stopPersistingFieldTrack({ discard: true });
  stopFieldTrackRunner();
  if (sessionId !== null) {
    useFieldTrackStore.getState().clearTrack(sessionId);
  }
}

/**
 * 종료 지점으로 쓸 캐시 좌표의 최대 나이. 새 측위(getCurrentPositionAsync)는 실내에서
 * 수 초씩 걸려 종료 → 요약 화면 전환을 붙잡으므로 쓰지 않습니다. 이보다 오래됐거나
 * 부정확하면 아무것도 찍지 않고, 마지막으로 채택된 점이 그대로 도착 지점이 됩니다.
 */
const FINISH_POSITION_MAX_AGE_MS = 30_000;

/** 임장을 종료한 자리. 확실하지 않으면 null 을 돌려줍니다(추측한 좌표를 찍지 않습니다). */
async function readFinishCoordinate(mode: RunnerMode | null): Promise<Coordinate | null> {
  if (mode === 'developer') {
    return useDeveloperLocationStore.getState().currentCoordinate;
  }
  if (mode !== 'gps') return null;

  const location = await Location.getLastKnownPositionAsync({
    maxAge: FINISH_POSITION_MAX_AGE_MS,
    requiredAccuracy: MAX_ACCURACY_METERS,
  }).catch(() => null);

  return location === null ? null : [location.coords.longitude, location.coords.latitude];
}

/**
 * 임장 종료 시점에 한 번 부릅니다. 수집을 멈추고 트랙을 확정한 뒤, 요약 화면으로
 * 넘길 세션 식별자를 돌려줍니다(트랙이 없으면 null).
 *
 * 멈추기 전에 지금 서 있는 자리를 마지막 점으로 한 번 더 찍습니다. 마지막으로 채택된
 * 좌표와 종료 시점 사이에는 공백이 생길 수 있고(앱이 강제 종료됐다 돌아온 구간, 콜드
 * 스타트 직후 정확도가 나쁜 구간, 5m 미만으로만 움직인 구간), 그 공백을 메우지 않으면
 * 요약 화면의 "도착" 마커가 종료한 자리가 아니라 그 공백이 시작된 자리에 찍힙니다.
 *
 * 확정된 트랙은 요약 화면이 메모리에서 읽습니다. 다시 이어 쓸 일이 없으므로 저장
 * 파일은 여기서 지웁니다.
 */
export async function stopAndFinishFieldTrack(): Promise<string | null> {
  const sessionId = useFieldTrackStore.getState().track?.sessionId ?? null;
  // stopFieldTrackRunner 가 모드를 비우므로 먼저 읽어 둡니다.
  const mode = runningMode;

  // 저장 파일을 먼저 지웁니다. 그래야 아래 stop 이 남은 변경을 디스크에 다시 쓰지 않습니다.
  stopPersistingFieldTrack({ discard: true });
  stopFieldTrackRunner();
  if (sessionId === null) return null;

  const finishCoordinate = await readFinishCoordinate(mode);
  if (finishCoordinate !== null) {
    useFieldTrackStore
      .getState()
      .appendPoint(
        sessionId,
        { coordinate: finishCoordinate, timestamp: Date.now() },
        { isFinal: true },
      );
  }

  useFieldTrackStore.getState().finishTrack(sessionId);

  return sessionId;
}
