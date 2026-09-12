import Ionicons from '@expo/vector-icons/Ionicons';
import Mapbox, { type MapState, type StandardStyleConfig } from '@rnmapbox/maps';
import { router, useFocusEffect, useLocalSearchParams } from 'expo-router';
import * as Location from 'expo-location';
import fieldStandardSessionMapStyle from '@/assets/mapStyles/fieldStandardSessionMapStyle.json';
import { useCallback, useEffect, useMemo, useRef, useState } from 'react';
import { AccessibilityInfo, StyleSheet, Text, View } from 'react-native';
import { useSafeAreaInsets } from 'react-native-safe-area-context';

import { appAlert } from '@/components/AppDialog';
import { FacilityFilterPanel } from '@/components/FacilityFilterPanel';
import {
  GlassIconButton,
  GLASS_ICON_BUTTON_ICON_SIZE,
  GLASS_ICON_BUTTON_SIZE,
} from '@/components/GlassIconButton';
import { CharacterHeadingCone } from '@/features/character/CharacterHeadingCone';
import { CharacterSprite } from '@/features/character/CharacterSprite';
import { NativeMapCharacter } from '@/features/character/NativeMapCharacter';
import { useCharacterMotion } from '@/features/character/useCharacterMotion';
import { useSelectedCharacterId } from '@/features/character/useSelectedCharacter';
import { ChatbotFab } from '@/features/chatbot/ChatbotFab';
import { ChatbotModal } from '@/features/chatbot/ChatbotModal';
import { ChecklistSheet } from '@/features/checklist/ChecklistSheet';
import { useFieldVisitParticipantCounts } from '@/features/checklist/useFieldVisitParticipantCounts';
import { useFieldVisitRoute } from '@/features/checklist/useFieldVisitRoute';
import { DeveloperLocationControls } from '@/features/field/DeveloperLocationControls';
import { startFieldTrackRunner } from '@/features/field/fieldTrackRunner';
import {
  getNearbyPediatricFacilities,
  VERIFIED_FIELD_TREES,
} from '@/features/field/map/fieldMapData';
import { POI_LABEL_STYLE_OVERRIDE, useFacilityFilters } from '@/features/map/facilityFilters';
import { BORDER_COLOR, MUTED_TEXT_COLOR, PRIMARY_COLOR } from '@/constants/colors';
import {
  bearingDegrees,
  distanceInMeters,
  normalizeDegrees,
  rotateScreenOffsetToWorld,
  type Coordinate,
} from '@/lib/geo';
import { SEOUL_CAMERA_BOUNDS, SEOUL_MIN_ZOOM_LEVEL } from '@/lib/mapBounds';
import { explainBeforeRequest, showPermanentlyDeniedAlert } from '@/lib/permissions';
import { useCharacterStore } from '@/store/characterStore';
import {
  isDeveloperLocationAvailable,
  useDeveloperLocationStore,
} from '@/store/developerLocationStore';

// 시설 필터 규칙(FACILITY_CATEGORIES·핵심 시설·분류별 maki)은 일반 지도와 공유합니다.
// features/map/facilityFilters.ts 참고.

// 개발자 미리보기와 잠실권 수동 조경 데이터를 위한 화면 중심일 뿐, 서비스 목적지 핀으로 사용하지 않습니다.
const FIELD_PREVIEW_CENTER: Coordinate = [127.0848, 37.5132];

/**
 * 체크리스트 API는 studyId 기준입니다(/studies/{studyId}/field-visit/checklist).
 * FE-010(스터디 상세 "임장 시작하기")이 실제 GPS 검증 시작 API를 호출한 뒤 studyId를
 * 라우트 파라미터로 넘겨줍니다. 그 경로 없이 이 화면에 직접 딥링크로 들어오는
 * 경우(개발 중 테스트 등)를 위해 로컬 DB에 실제로 존재하는 스터디(1번)를 기본값으로
 * 남겨둡니다.
 */
const MOCK_STUDY_ID = 1;

/**
 * 임장 중에는 탭바를 띄우지 않습니다(임장에만 집중). 그래서 체크리스트 시트는
 * 화면 맨 아래까지 내려오고, 제스처 바/홈 인디케이터에 닿지 않을 만큼만 띄웁니다.
 */
const SHEET_BOTTOM_GAP = 10;

/** 유리 버튼 안 아이콘 색. 반투명 배경 위에서도 읽히도록 진한 슬레이트. */
const CONTROL_ICON_COLOR = '#26343A';

/** 지도 컨트롤 세로 간격. 시설 필터 패널 자리를 잡을 때도 같은 값을 씁니다. */
const MAP_CONTROL_GAP = 11;

/**
 * 임장 화면은 딥링크로 바로 열릴 수도 있어(뒤로 갈 곳이 없는 경우) 홈으로 보냅니다.
 * 스터디 상세와 같은 방식입니다.
 */
function goBackOrHome() {
  if (router.canGoBack()) router.back();
  else router.replace('/(app)/(tabs)/home');
}

/** 렌더마다 재직렬화되지 않도록 모듈 스코프에서 한 번만 만듭니다 (map.tsx와 같은 이유). */
const FIELD_MAP_STYLE_JSON = JSON.stringify(fieldStandardSessionMapStyle);

// Metro가 GLB를 바이너리 에셋 ID로 등록하므로 React Native 정적 에셋 방식인 require를 사용합니다.
// eslint-disable-next-line @typescript-eslint/no-require-imports
const FIELD_TREE_CONIFER_MODEL = require('../../../assets/models/trees/tree-conifer.glb');
// eslint-disable-next-line @typescript-eslint/no-require-imports
const FIELD_TREE_ROUND_MODEL = require('../../../assets/models/trees/tree-round.glb');
// eslint-disable-next-line @typescript-eslint/no-require-imports
const FIELD_TREE_BROADLEAF_MODEL = require('../../../assets/models/trees/tree-broadleaf.glb');
const PEDIATRIC_CLINIC_IMAGE = require('../../../assets/images/map/pediatric-clinic.png');

/** 목적지 주변에서 검증된 녹지·보행 좌표 일부만 사용해 GLB 크기와 가림 성능을 확인합니다. */
const FIELD_TREE_MODEL_SHAPE: GeoJSON.FeatureCollection<GeoJSON.Point> = {
  type: 'FeatureCollection',
  features: VERIFIED_FIELD_TREES.filter(
    ({ coordinate }) =>
      distanceInMeters(coordinate, [FIELD_PREVIEW_CENTER[0], FIELD_PREVIEW_CENTER[1]]) <= 1_200,
  )
    .slice(0, 12)
    .map(({ id, coordinate }, index) => ({
      type: 'Feature',
      id: `field-tree-model-${id}`,
      properties: {
        modelId: ['field-tree-conifer', 'field-tree-round', 'field-tree-broadleaf'][index % 3],
      },
      geometry: { type: 'Point', coordinates: coordinate },
    })),
};

const PEDIATRIC_FACILITY_SHAPE: GeoJSON.FeatureCollection<GeoJSON.Point> = {
  type: 'FeatureCollection',
  features: getNearbyPediatricFacilities([FIELD_PREVIEW_CENTER[0], FIELD_PREVIEW_CENTER[1]]).map(
    (facility, index) => ({
      type: 'Feature',
      id: facility.id,
      properties: {
        name: facility.name,
        address: facility.address,
        priority: index,
      },
      geometry: { type: 'Point', coordinates: facility.coordinate },
    }),
  ),
};

/** Android에서도 스타일 로딩 뒤 Standard의 3D 개별 옵션을 확실히 다시 적용합니다. */
const FIELD_STANDARD_CONFIG = {
  lightPreset: 'day',
  theme: 'default',
  // 임장 경로와 조경이 주인공이 되도록 건물·도로·대지를 밝은 백색 계열로 낮춥니다.
  colorBuildings: '#FFFFFF',
  colorLand: '#F8FAF9',
  colorRoads: '#FFFFFF',
  colorMotorways: '#F4F7F6',
  colorGreenspaces: '#DDF5E5',
  colorWater: '#DFF4F5',
  showPointOfInterestLabels: false,
  showTransitLabels: false,
  showPlaceLabels: false,
  showRoadLabels: false,
  showPedestrianRoads: true,
  show3dObjects: true,
  show3dBuildings: true,
  show3dTrees: true,
  show3dLandmarks: true,
  show3dFacades: true,
} satisfies StandardStyleConfig;

/**
 * rnmapbox#4192 — 제스처 중 onCameraChanged 가 네이티브 스로틀 없이 JS 로 쏟아집니다.
 * §5.2 가 "JS 스레드 병목 1순위 후보"로 지목한 경로라 여기서 직접 시간으로 걸러냅니다.
 */
const CAMERA_HEADING_SYNC_INTERVAL_MS = 100;

/**
 * GPS 좌표가 갱신될 때 네이티브 ShapeAnimator가 이전 위치에서 새 위치로 부드럽게
 * 이어줍니다. 개발자 조이스틱은 짧은 주기로 갱신되므로 카메라와 같은 빠른 보간을 씁니다.
 */
const POSITION_TWEEN_MS = 500;
const DEVELOPER_CAMERA_TWEEN_MS = 50;

/**
 * 3D 카메라 기울기(도). 차량 내비게이션처럼 시선을 앞으로 눕혀서, 지금 걸어갈 방향의
 * 건물과 길이 화면 위쪽에 함께 보이도록 합니다.
 *
 * 35도는 위에서 내려다보는 쪽에 가까워 앞쪽 지형이 거의 안 보였습니다. Mapbox 는 85도
 * 까지 허용하지만 그 근처는 지평선이 화면을 덮고 라벨이 심하게 겹쳐 방향 감각이 오히려
 * 나빠집니다. 60도가 앞을 충분히 보여주면서 건물 가림이 과하지 않은 지점입니다.
 *
 * followPitch 까지 같은 값을 써야 합니다 — followUserLocation 이 켜져 있으면 추적 중
 * 카메라는 pitch 가 아니라 followPitch 를 따르므로, 한쪽만 올리면 되돌아갑니다.
 */
const MAP_PITCH_3D = 60;
const MAP_PITCH_2D = 0;

/** 네비게이션 화면에서 팔방이는 아래쪽에 두고, 이동 방향(진행 방향)을 위쪽에 보여줍니다. */
const NAVIGATION_ZOOM_LEVEL = 17;
const NAVIGATION_CAMERA_MIN_MOVE_METERS = 0.75;
/** 이 속도(m/s) 이상으로 움직일 때만 GPS 진행 방향으로 카메라를 회전시켜 정지 중 흔들림을 막습니다. */
const CAMERA_HEADING_MIN_SPEED_MPS = 0.3;
const NAVIGATION_CAMERA_PADDING = {
  paddingTop: 280,
  paddingRight: 36,
  paddingBottom: 96,
  paddingLeft: 36,
};

export default function FieldSessionScreen() {
  const params = useLocalSearchParams<{
    sessionId: string;
    studyId?: string;
    apartmentId?: string;
    apartmentName?: string;
  }>();
  const parsedStudyId = Number(params.studyId);
  const studyId =
    Number.isFinite(parsedStudyId) && parsedStudyId > 0 ? parsedStudyId : MOCK_STUDY_ID;

  // 임장 지도에서 "참여 중 N명 / 종료 M명"을 5초마다 갱신해 준실시간으로 보여 줍니다.
  const participantCounts = useFieldVisitParticipantCounts(studyId);

  // 스터디 상세의 '임장 시작하기'가 서버에서 확정한 아파트를 넘겨줍니다. 경로 조회
  // (fieldRoute.origin)보다 먼저 도착하고 화면 수명 동안 고정이라, 챗봇 대화 상대가
  // 도중에 바뀌지 않습니다. 파라미터 없이 들어오는 진입에서는 경로 쪽으로 폴백합니다.
  const parsedApartmentId = Number(params.apartmentId);
  const paramApartmentId =
    Number.isInteger(parsedApartmentId) && parsedApartmentId > 0 ? parsedApartmentId : null;
  const paramApartmentName = params.apartmentName?.trim() ? params.apartmentName : undefined;

  const insets = useSafeAreaInsets();
  /** 체크리스트 시트·챗봇 버튼이 공통으로 쓰는 화면 아래쪽 여백. */
  const sheetBottomInset = Math.max(SHEET_BOTTOM_GAP, insets.bottom);
  const developerLocationEnabled = useDeveloperLocationStore((state) => state.isEnabled);
  const developerCoordinate = useDeveloperLocationStore((state) => state.currentCoordinate);
  const developerDestination = useDeveloperLocationStore((state) => state.destinationCoordinate);
  const initializeDeveloperSession = useDeveloperLocationStore((state) => state.initializeSession);
  const moveDeveloperLocationBy = useDeveloperLocationStore((state) => state.moveBy);
  const isDeveloperMode = isDeveloperLocationAvailable && developerLocationEnabled;
  const [hasLocationPermission, setHasLocationPermission] = useState(false);
  const [currentCoordinate, setCurrentCoordinate] = useState<Coordinate | null>(null);
  const [isReduceMotionEnabled, setIsReduceMotionEnabled] = useState(false);
  const [is3dEnabled, setIs3dEnabled] = useState(true);
  const [isChatOpen, setIsChatOpen] = useState(false);
  const [isFilterOpen, setIsFilterOpen] = useState(false);
  // 지도에서 웨이포인트 점을 탭하면 그 경유지의 항목 목록을 체크리스트 시트에서 엽니다.
  const [focusedWaypointId, setFocusedWaypointId] = useState<number | null>(null);

  // 지도(경유지 점 마커)와 체크리스트 시트(항목 묶음)가 같은 경유지 데이터를 봐야
  // 하므로 조회는 이 화면에서 한 번만 하고 시트로 내려보냅니다. 항목 완료가 바뀌면
  // refreshRoute 로 다시 불러와 점 안의 진행도(체크마크/개수)를 갱신합니다.
  const {
    route: fieldRoute,
    isGeneratingRoute,
    generateRoute,
    refreshRoute,
  } = useFieldVisitRoute(studyId);

  // 생성은 카카오 POI·보행 경로 같은 외부 서비스를 타서 실패할 수 있습니다. 실패
  // 사유(체크리스트 없음·경유지 부족·외부 서비스 장애)를 서버 문구 그대로 보여 줍니다.
  const handleGenerateRoute = useCallback(() => {
    void (async () => {
      const failure = await generateRoute();
      if (failure !== null) appAlert('경로를 만들지 못했어요', failure);
    })();
  }, [generateRoute]);

  const cameraRef = useRef<Mapbox.Camera>(null);
  const mapRef = useRef<Mapbox.MapView>(null);
  const currentCoordinateRef = useRef<Coordinate | null>(null);
  const previousDeveloperCoordinateRef = useRef<Coordinate | null>(null);
  const isDeveloperJoystickActiveRef = useRef(false);
  const lastCameraSyncRef = useRef(0);
  const hasCenteredNavigationCameraRef = useRef(false);
  const lastNavigationCameraCoordinateRef = useRef<Coordinate | null>(null);
  /**
   * 캐릭터가 바라보는 실제 방위(도). 바닥 방향 부채꼴과 "현재 위치로" 버튼이 함께 씁니다.
   * 콜백 안에서 최신값을 읽어야 해서 ref 와 state 를 같이 둡니다.
   */
  const facingBearingRef = useRef<number | null>(null);
  const [facingBearing, setFacingBearing] = useState<number | null>(null);
  /** 지도 방위(도). 아직 걸은 적이 없어 진행 방향을 모를 때 부채꼴 방향을 정하는 데 씁니다. */
  const [mapHeading, setMapHeading] = useState(0);
  /**
   * 같은 지도 방위를 콜백에서 최신값으로 읽기 위한 ref. 가상 GPS 조이스틱이 화면 기준
   * 입력을 실제 방위로 돌릴 때 씁니다. state 를 쓰면 콜백이 계속 새로 만들어집니다.
   */
  const mapHeadingRef = useRef(0);
  const refreshRouteTimerRef = useRef<ReturnType<typeof setTimeout> | null>(null);

  const {
    expandedCategories,
    showAllFacilities,
    setShowAllFacilities,
    showCoreFacilities,
    setShowCoreFacilities,
    toggleFacilityCategory,
    poiFilter,
    transitFilter,
    layerVisibility,
  } = useFacilityFilters({ cameraRef, mapViewRef: mapRef });

  const characterId = useSelectedCharacterId();
  const isMoving = useCharacterStore((state) => state.isMoving);
  const direction = useCharacterStore((state) => state.direction);
  const { reportLocation, reportMapHeading } = useCharacterMotion();
  const fieldApartment = fieldRoute?.origin ?? null;
  // 지도 목적지 핀은 좌표가 필요해 경로(fieldApartment)에서만 나옵니다. 챗봇은 좌표가
  // 필요 없으므로 경로를 아직 안 짠 임장에서도 열리도록 라우트 파라미터를 우선합니다.
  const chatbotApartmentId = paramApartmentId ?? fieldApartment?.apartmentId ?? null;
  const chatbotApartmentName = paramApartmentName ?? fieldApartment?.name;
  const destinationCoordinate = useMemo<Coordinate | null>(
    () =>
      isDeveloperMode && developerDestination !== null
        ? developerDestination
        : fieldApartment === null
          ? null
          : [fieldApartment.longitude, fieldApartment.latitude],
    [developerDestination, fieldApartment, isDeveloperMode],
  );
  const destinationShape = useMemo<GeoJSON.Feature<GeoJSON.Point> | null>(
    () =>
      destinationCoordinate === null
        ? null
        : {
            type: 'Feature',
            properties: { name: isDeveloperMode ? '대상 아파트' : fieldApartment?.name },
            geometry: { type: 'Point', coordinates: destinationCoordinate },
          },
    [destinationCoordinate, fieldApartment?.name, isDeveloperMode],
  );
  // 팔방이는 경로에 스냅하지 않고 항상 현재 GPS 좌표에 그대로 섭니다.
  const characterCoordinate = currentCoordinate;

  const handleDeveloperMove = useCallback(
    (screenUpMeters: number, screenRightMeters: number) => {
      isDeveloperJoystickActiveRef.current = true;
      // 조이스틱은 화면 기준으로 입력을 줍니다. 이 지도는 진행 방향을 화면 위로 두고
      // 회전하므로, 지도 방위만큼 돌려야 "위로 밀면 앞으로" 가 성립합니다.
      const { eastMeters, northMeters } = rotateScreenOffsetToWorld(
        screenUpMeters,
        screenRightMeters,
        mapHeadingRef.current,
      );
      moveDeveloperLocationBy(northMeters, eastMeters);
    },
    [moveDeveloperLocationBy],
  );

  const handleDeveloperMoveEnd = useCallback(() => {
    isDeveloperJoystickActiveRef.current = false;
    useCharacterStore.getState().setMoving(false);
  }, []);

  useEffect(() => {
    if (
      !isDeveloperMode ||
      params.sessionId !== 'preview-session' ||
      developerCoordinate !== null
    ) {
      return;
    }

    initializeDeveloperSession(FIELD_PREVIEW_CENTER);
  }, [developerCoordinate, initializeDeveloperSession, isDeveloperMode, params.sessionId]);

  useEffect(() => {
    if (isDeveloperMode) {
      return;
    }

    let cancelled = false;
    const requestLocationPermission = async () => {
      const current = await Location.getForegroundPermissionsAsync();
      let result = current;

      if (!current.granted) {
        const shouldRequest = await explainBeforeRequest('location');
        if (!shouldRequest) return;
        result = await Location.requestForegroundPermissionsAsync();
        if (!result.granted && !result.canAskAgain) {
          showPermanentlyDeniedAlert('location');
        }
      }

      const isGranted = result.status === Location.PermissionStatus.GRANTED;

      if (!cancelled) {
        setHasLocationPermission(isGranted);
      }
    };

    void requestLocationPermission();
    return () => {
      cancelled = true;
    };
  }, [isDeveloperMode]);

  /**
   * 임장 이동 궤적 기록 시작(FE-018). 수집기는 이 화면이 아니라 모듈이 들고 있어서,
   * 채팅·스터디 상세 등으로 잠깐 빠져나가도 기록이 끊기지 않습니다. 그래서 여기서
   * 정리(stop)하지 않고, 임장을 실제로 끝낼 때(ChecklistSheet) 한 번만 멈춥니다.
   * 여러 번 호출돼도 러너가 같은 세션·같은 모드면 구독을 새로 만들지 않습니다.
   */
  useEffect(() => {
    const sessionId = params.sessionId;
    if (!sessionId) return;
    if (!isDeveloperMode && !hasLocationPermission) return;

    void startFieldTrackRunner(sessionId, { useDeveloperLocation: isDeveloperMode });
  }, [hasLocationPermission, isDeveloperMode, params.sessionId]);

  // 모션 축소 설정이 켜져 있으면 걷기 애니메이션을 정지 프레임으로 대체합니다 (FE-014 예외 처리).
  useEffect(() => {
    void AccessibilityInfo.isReduceMotionEnabled().then(setIsReduceMotionEnabled);
    const subscription = AccessibilityInfo.addEventListener(
      'reduceMotionChanged',
      setIsReduceMotionEnabled,
    );

    return () => subscription.remove();
  }, []);

  /**
   * 걸어가는 동안 캐릭터를 화면 안에 두기 위한 추적. 중심만 옮기고 줌·기울기·방위는
   * 건드리지 않습니다 — 예전에는 여기서 매 위치 샘플마다 zoomLevel·pitch 를 다시
   * 넣어서, 사용자가 지도를 돌리거나 줌아웃해 둔 것이 곧바로 되감겨(갑자기 줌인되는
   * 것처럼) 보였습니다. 네비게이션처럼 맞추는 일은 3D 버튼과 현재 위치 버튼이 합니다.
   */
  const followCharacterCamera = useCallback(
    (coordinate: Coordinate) => {
      const previousCoordinate = lastNavigationCameraCoordinateRef.current;
      if (
        hasCenteredNavigationCameraRef.current &&
        previousCoordinate !== null &&
        distanceInMeters(previousCoordinate, coordinate) < NAVIGATION_CAMERA_MIN_MOVE_METERS
      ) {
        return;
      }

      const camera = cameraRef.current;
      if (camera === null) return;

      camera.setCamera({
        // 경로에 스냅하지 않고 현재 GPS 좌표를 그대로 중심에 둡니다.
        centerCoordinate: coordinate,
        animationDuration: isDeveloperMode ? DEVELOPER_CAMERA_TWEEN_MS : POSITION_TWEEN_MS,
        animationMode: 'easeTo',
      });
      lastNavigationCameraCoordinateRef.current = coordinate;
      hasCenteredNavigationCameraRef.current = true;
    },
    [isDeveloperMode],
  );

  /**
   * 네비게이션 시점으로 맞춥니다(중심·줌·기울기·여백, 그리고 방위가 있으면 진행 방향을
   * 위로). 버튼을 눌렀을 때와 화면에 처음 들어왔을 때만 씁니다.
   */
  const recenterNavigationCamera = useCallback(
    (coordinate: Coordinate, heading: number | undefined, animated = true) => {
      const camera = cameraRef.current;
      if (camera === null) return;

      camera.setCamera({
        centerCoordinate: coordinate,
        zoomLevel: NAVIGATION_ZOOM_LEVEL,
        ...(heading === undefined ? {} : { heading }),
        pitch: is3dEnabled ? MAP_PITCH_3D : MAP_PITCH_2D,
        padding: NAVIGATION_CAMERA_PADDING,
        animationDuration: animated ? 450 : 0,
        animationMode: 'easeTo',
      });
      lastNavigationCameraCoordinateRef.current = coordinate;
      hasCenteredNavigationCameraRef.current = true;
    },
    [is3dEnabled],
  );

  const handleLocationSample = useCallback(
    ({
      coordinate,
      speed,
      course,
      timestamp,
    }: {
      coordinate: Coordinate;
      speed: number | null;
      course: number | null;
      timestamp: number;
    }) => {
      currentCoordinateRef.current = coordinate;
      setCurrentCoordinate(coordinate);

      reportLocation({ coordinate, speed, course, timestamp, courseIsTrusted: isDeveloperMode });
      // 충분히 움직일 때만 바라보는 방위를 갱신합니다. 정지 중 GPS course 잡음으로
      // 방향 표시가 흔들리는 것을 막습니다. 카메라를 돌리는 데는 쓰지 않고(사용자가
      // 돌려 둔 방향을 유지), 방향 부채꼴과 현재 위치 버튼이 이 값을 씁니다.
      if (
        course !== null &&
        Number.isFinite(course) &&
        course >= 0 &&
        (speed ?? 0) > CAMERA_HEADING_MIN_SPEED_MPS
      ) {
        facingBearingRef.current = course;
        setFacingBearing(course);
      }

      // 첫 위치를 받은 순간에는 네비게이션 시점으로 한 번 맞춰 주고, 그다음부터는
      // 중심만 따라갑니다.
      if (hasCenteredNavigationCameraRef.current) {
        followCharacterCamera(coordinate);
      } else {
        recenterNavigationCamera(coordinate, undefined, false);
      }
    },
    [followCharacterCamera, isDeveloperMode, recenterNavigationCamera, reportLocation],
  );

  useEffect(() => {
    if (!isDeveloperMode || developerCoordinate === null) return;

    const previousCoordinate = previousDeveloperCoordinateRef.current;
    const hasMoved =
      previousCoordinate !== null &&
      distanceInMeters(previousCoordinate, developerCoordinate) > Number.EPSILON;

    handleLocationSample({
      coordinate: developerCoordinate,
      // 스틱 세기는 실제 좌표 이동량에 이미 반영됩니다. 캐릭터 모션에는 움직임 여부만
      // 전달해 약한 입력에서도 캐릭터가 미끄러지지 않고 걷도록 합니다.
      speed: hasMoved && isDeveloperJoystickActiveRef.current ? 1.4 : 0,
      course: hasMoved ? bearingDegrees(previousCoordinate, developerCoordinate) : null,
      timestamp: Date.now(),
    });
    previousDeveloperCoordinateRef.current = developerCoordinate;
  }, [developerCoordinate, handleLocationSample, isDeveloperMode]);

  const handleCameraChanged = useCallback(
    (state: MapState) => {
      // ref 갱신은 렌더를 유발하지 않으므로 스로틀 앞에 둡니다. 조이스틱이 매 50ms 마다
      // 이 값을 읽어 화면 기준 입력을 방위로 돌리기 때문에, 여기서 100ms 를 기다리면
      // 지도를 빠르게 돌리는 동안 이동 방향이 뒤늦게 따라옵니다.
      mapHeadingRef.current = state.properties.heading;

      const now = Date.now();
      if (now - lastCameraSyncRef.current < CAMERA_HEADING_SYNC_INTERVAL_MS) return;
      lastCameraSyncRef.current = now;

      reportMapHeading(state.properties.heading);
      setMapHeading(state.properties.heading);
    },
    [reportMapHeading],
  );

  // 항목 완료가 바뀌면 경로/진행도를 다시 불러와 점 마커의 체크마크·개수를 갱신합니다.
  // 빠르게 여러 항목을 체크할 때 매번 요청하지 않도록 잠깐 모았다가 한 번만 조회합니다.
  const handleItemCompletionChange = useCallback(() => {
    if (refreshRouteTimerRef.current !== null) clearTimeout(refreshRouteTimerRef.current);
    refreshRouteTimerRef.current = setTimeout(() => {
      refreshRouteTimerRef.current = null;
      void refreshRoute();
    }, 600);
  }, [refreshRoute]);

  useEffect(
    () => () => {
      if (refreshRouteTimerRef.current !== null) clearTimeout(refreshRouteTimerRef.current);
    },
    [],
  );

  const isCharacterLocated =
    (isDeveloperMode || hasLocationPermission) && currentCoordinate !== null;
  // buildPoiFilter 가 의료를 보여주는 조건과 맞춥니다: 전체 보기, 명시적으로 '의료'를
  // 골랐을 때, 또는 아무것도 안 골라서 임장 핵심(켜져 있으면) 이 적용될 때.
  const isMedicalFilterEnabled =
    showAllFacilities ||
    expandedCategories.includes('medical') ||
    (expandedCategories.length === 0 && showCoreFacilities);

  // 카메라 ref로 지도 화면을 다시 만들지 않고 2D/3D와 현재 위치 이동을 제어합니다.
  // 3D 로 바꾸는 순간이 "네비게이션 시점으로 맞춘다"는 뜻이라, 기울기만 세우지 않고
  // 진행 방향을 위로 두고 현재 위치·줌까지 함께 맞춥니다.
  const toggleMapDimension = useCallback(() => {
    const nextIs3dEnabled = !is3dEnabled;
    setIs3dEnabled(nextIs3dEnabled);

    // 기울기만 바꿉니다. 보고 있던 위치·줌·방위는 그대로 둬야 "지금 보던 곳을 각도만
    // 바꿔 본다"가 됩니다. 위치로 돌아가는 건 현재 위치 버튼의 몫입니다.
    cameraRef.current?.setCamera({
      pitch: nextIs3dEnabled ? MAP_PITCH_3D : MAP_PITCH_2D,
      animationDuration: 450,
      animationMode: 'easeTo',
    });
  }, [is3dEnabled]);

  /**
   * 채팅·체크리스트 기록 등 다른 화면에 갔다가 임장 지도로 돌아오면 네비게이션 시점으로
   * 다시 맞춰 줍니다(화면에 처음 들어올 때는 첫 위치를 받는 시점에 맞춥니다).
   * 화면 안에서 지도를 돌려 보는 동안에는 개입하지 않습니다.
   */
  useFocusEffect(
    useCallback(() => {
      if (!hasCenteredNavigationCameraRef.current) return;

      const coordinate = currentCoordinateRef.current;
      if (coordinate === null) return;

      recenterNavigationCamera(
        coordinate,
        is3dEnabled && facingBearingRef.current !== null ? facingBearingRef.current : undefined,
      );
    }, [is3dEnabled, recenterNavigationCamera]),
  );

  /**
   * 3D 상태에서 누르면 진행 방향까지 위로 맞춰 네비게이션 시점으로 복귀하고,
   * 2D 에서는 위치·줌만 맞춰 지도를 돌리지 않습니다.
   *
   * 아직 한 번도 걷지 않았으면 진행 방향(facingBearing)이 없습니다 — 나침반을 쓰지
   * 않고 GPS 이동 궤적으로만 방위를 구하기 때문입니다. 이때는 일부러 지도를 돌리지
   * 않고 보고 있던 각도를 그대로 둡니다. 기준도 없는데 정북으로 휙 돌아가면 오히려
   * 방향 감각이 끊기고, 걷기 시작하면 자연스럽게 진행 방향으로 맞춰집니다.
   */
  const moveToCurrentLocation = useCallback(() => {
    const coordinate = characterCoordinate ?? currentCoordinateRef.current;
    if (coordinate === null) return;

    recenterNavigationCamera(
      coordinate,
      is3dEnabled && facingBearingRef.current !== null ? facingBearingRef.current : undefined,
    );
  }, [characterCoordinate, is3dEnabled, recenterNavigationCamera]);

  // 스터디 상세(study/[id].tsx)의 채팅 버튼과 같은 화면을 씁니다 — roomId를 studyId로
  // 쓰는 것까지 동일해서(chat/[roomId].tsx 주석 참고), 별도 채팅방을 새로 만들지 않습니다.
  const handleOpenChat = useCallback(() => {
    router.push({
      pathname: '/(app)/chat/[roomId]',
      params: { roomId: String(studyId) },
    });
  }, [studyId]);

  /**
   * 체크리스트 경유지 점 마커. 경로선 대신 각 장소를 크게 강조한 점으로 찍고, 점 안에는
   * 내 항목의 집계 진행도를 보여 줍니다 — 내 항목이 모두 완료면 체크마크(✓), 아니면
   * 완료/전체 개수(예: 2/3). 내 항목이 없는 경유지(다른 참여자 몫만)는 흐린 점으로만
   * 표시하고 탭해도 열지 않습니다. isCompleted 는 refreshRoute 로 다시 불러온 값입니다.
   */
  const questWaypointShape = useMemo<GeoJSON.FeatureCollection<GeoJSON.Point> | null>(() => {
    const waypoints = fieldRoute?.waypoints;
    if (waypoints === undefined || waypoints.length === 0) return null;

    return {
      type: 'FeatureCollection',
      features: [...waypoints]
        .sort((left, right) => left.sequence - right.sequence)
        .map((waypoint) => {
          const myTotal = waypoint.myItems.length;
          const myDone = waypoint.myItems.filter((item) => item.isCompleted).length;
          const hasMine = myTotal > 0;
          const allDone = hasMine && myDone === myTotal;

          return {
            type: 'Feature' as const,
            properties: {
              waypointId: waypoint.waypointId,
              name: waypoint.name,
              hasMine,
              allDone,
              // 완료면 체크마크, 아니면 완료/전체 개수. 내 몫이 없는 경유지는 점만 찍습니다
              // (순서 번호는 쓰지 않습니다 — 경유지는 이름표로 구분합니다).
              label: hasMine ? (allDone ? '✓' : `${myDone}/${myTotal}`) : '',
            },
            geometry: {
              type: 'Point' as const,
              coordinates: [waypoint.longitude, waypoint.latitude],
            },
          };
        }),
    };
  }, [fieldRoute]);

  return (
    <View style={styles.container}>
      <Mapbox.MapView
        ref={mapRef}
        style={styles.map}
        // Standard가 3D 건물·나무·조명을 그리고, 별도 Streets 레이어가 한글 라벨만
        // top 슬롯에 표시합니다. 생성 규칙은 scripts/build_field_map_style.py에 있습니다.
        styleJSON={FIELD_MAP_STYLE_JSON}
        scaleBarEnabled={false}
        zoomEnabled
        scrollEnabled
        rotateEnabled
        pitchEnabled
        onCameraChanged={handleCameraChanged}
        onPress={() => setIsFilterOpen(false)}
      >
        <Mapbox.StyleImport id="basemap" existing config={FIELD_STANDARD_CONFIG} />

        {/*
          Streets 벡터 소스의 기존 한글 라벨 레이어에 동적 filter만 적용합니다.
          별도 POI API 없이도 아무 카테고리도 안 고르면 5개 전부, 하나라도 고르면
          그 카테고리만 남도록 같은 원본 레이어의 filter 를 바꿔 씁니다.
        */}
        {/*
          아이콘·글자를 원본 스타일보다 1.3배 키웁니다. iconSize 는 원본에 없어서(기본값 1)
          바로 1.3으로 두면 되지만, textSize 는 zoom·sizerank 에 따라 달라지는 원본 식을
          갖고 있어서 각 숫자만 1.3배 해서 유지했습니다(zoom 을 쓰는 식은 최상위에만 올
          수 있어서, 식 전체를 ['*', ...] 로 한 번 더 감싸면 Mapbox 가 파싱에 실패합니다 —
          실제로 RNMBXStyleFactory 예외로 확인).
        */}
        <Mapbox.SymbolLayer
          id="poi-label"
          existing
          filter={poiFilter}
          style={{
            iconSize: 1.3,
            textSize: [
              'step',
              ['zoom'],
              ['step', ['get', 'sizerank'], 18 * 1.3, 5, 12 * 1.3],
              17,
              ['step', ['get', 'sizerank'], 18 * 1.3, 13, 12 * 1.3],
            ],
            // 우리가 추적하는 카테고리(학교·병원 등)는 sizerank 가 낮은(랜드마크급)
            // 곳일수록 글자가 아이콘 자리에 그대로 겹쳐져 아이콘이 안 보이던 문제를
            // 고칩니다 — 텍스트는 항상 아이콘 아래로 내려서 아이콘이 가려지지
            // 않게 합니다(facilityFilters.ts 의 POI_LABEL_STYLE_OVERRIDE 참고).
            textAnchor: POI_LABEL_STYLE_OVERRIDE.textAnchor,
            textOffset: POI_LABEL_STYLE_OVERRIDE.textOffset,
            visibility: layerVisibility,
          }}
        />
        <Mapbox.SymbolLayer
          id="transit-label"
          existing
          filter={transitFilter}
          style={{ iconSize: 1.3, textSize: 12 * 1.3, visibility: layerVisibility }}
        />

        <Mapbox.Camera
          ref={cameraRef}
          // centerCoordinate/pitch 등을 여기 최상위 prop 으로 매번 넘기면 rnmapbox 가
          // pitch 가 바뀔 때마다(예: 2D/3D 토글) 이 값들 전체를 다시 "이동 명령"으로
          // 보냅니다 — followUserLocation 없는 이 화면에서는 그 이동 명령이 그대로
          // 실행돼, 3D/2D 를 누를 때마다 카메라가 목적지 단지(destinationCoordinate)로
          // 도로 날아가 버렸습니다(카메라는 moveNavigationCamera 로 계속 명령형으로
          // 옮기고 있는데, 여기 선언적 prop 이 그 위치를 덮어씀). defaultSettings 는
          // 마운트 시 최초 1회만 적용되고 이후 값이 바뀌어도 다시 튀지 않습니다.
          defaultSettings={{
            centerCoordinate: destinationCoordinate ?? FIELD_PREVIEW_CENTER,
            zoomLevel: NAVIGATION_ZOOM_LEVEL,
            pitch: is3dEnabled ? MAP_PITCH_3D : MAP_PITCH_2D,
            heading: 0,
            padding: NAVIGATION_CAMERA_PADDING,
          }}
          minZoomLevel={SEOUL_MIN_ZOOM_LEVEL}
          maxBounds={SEOUL_CAMERA_BOUNDS}
        />

        {!isDeveloperMode && hasLocationPermission && (
          // visible=false: 임장 지도에서는 기본 위치 핀(LocationPuck)을 숨기고 팔방이가
          // 현재 위치를 대신 표시합니다(스펙 "현재 위치 핀 교체"). onUpdate 는 visible 과
          // 무관하게 네이티브 위치 매니저에서 계속 발생하므로 위치 추적은 유지됩니다.
          // 일반 지도 화면은 이 화면과 별개라 영향받지 않습니다.
          <Mapbox.UserLocation
            visible={false}
            minDisplacement={0}
            onUpdate={({ coords, timestamp }) => {
              handleLocationSample({
                coordinate: [coords.longitude, coords.latitude],
                speed: coords.speed ?? null,
                course: coords.course ?? null,
                timestamp: timestamp ?? Date.now(),
              });
            }}
          />
        )}

        {/* 캐릭터보다 먼저(아래에) 깔아 스프라이트를 가리지 않게 합니다. 아직 한 번도
            걷지 않아 진행 방향을 모르면 캐릭터가 카메라를 보고 서 있으므로(기본 자세),
            화면 아래쪽 = 지도 방위 + 180도를 바라보는 것으로 둡니다. */}
        {isCharacterLocated && characterCoordinate !== null && (
          <CharacterHeadingCone
            bearing={facingBearing ?? normalizeDegrees(mapHeading + 180)}
            coordinate={characterCoordinate}
          />
        )}

        {isCharacterLocated && characterCoordinate !== null && (
          <NativeMapCharacter
            coordinate={characterCoordinate}
            characterId={characterId}
            motion={isMoving ? 'walk' : 'idle'}
            direction={direction}
            reduceMotion={isReduceMotionEnabled}
            positionTweenMs={isDeveloperMode ? DEVELOPER_CAMERA_TWEEN_MS : POSITION_TWEEN_MS}
          />
        )}

        <Mapbox.Images images={{ 'pediatric-clinic': { image: PEDIATRIC_CLINIC_IMAGE } }} />

        {/* GLB는 실제 미터 크기의 지도 고정 3D 모델이라 줌·피치에 맞춰 원근과 건물 가림이 적용됩니다. */}
        <Mapbox.Models
          models={{
            'field-tree-conifer': FIELD_TREE_CONIFER_MODEL,
            'field-tree-round': FIELD_TREE_ROUND_MODEL,
            'field-tree-broadleaf': FIELD_TREE_BROADLEAF_MODEL,
          }}
        />
        <Mapbox.ShapeSource id="field-tree-model-source" shape={FIELD_TREE_MODEL_SHAPE}>
          <Mapbox.ModelLayer
            id="field-tree-models"
            slot="middle"
            minZoomLevel={15.2}
            style={{
              modelId: ['get', 'modelId'],
              modelScale: [0.82, 0.82, 0.82],
              modelRotation: [0, 0, 0],
              modelTranslation: [0, 0, 0],
              modelType: 'common-3d',
              modelOpacity: ['interpolate', ['linear'], ['zoom'], 15.2, 0, 15.6, 1],
              modelCastShadows: true,
              modelReceiveShadows: true,
              modelAmbientOcclusionIntensity: 0.7,
              modelAllowDensityReduction: true,
            }}
          />
        </Mapbox.ShapeSource>

        {/* 체크리스트 경유지 점 마커. 경로선 없이 각 장소를 크게 강조한 점으로만 찍고,
            점 안에는 내 항목의 집계 진행도(완료면 ✓, 아니면 완료/전체 개수)를 보여 줍니다.
            top 슬롯이라 3D 건물에 가리지 않습니다. onPress 로 점을 탭하면 그 경유지의 항목
            목록을 체크리스트 시트에서 엽니다. GlossyFill(RN 그라데이션 View)은 지도 레이어
            위에 못 얹으므로 원형 레이어 조합으로 그립니다. */}
        {questWaypointShape !== null && (
          <Mapbox.ShapeSource
            id="field-quest-waypoint-source"
            shape={questWaypointShape}
            onPress={(event) => {
              const feature = event.features[0];
              const waypointId = feature?.properties?.waypointId;
              const hasMine = feature?.properties?.hasMine;
              // 내 항목이 없는 경유지(다른 참여자 몫만)는 열 목록이 없어 무시합니다.
              if (typeof waypointId === 'number' && hasMine === true) {
                setFocusedWaypointId(waypointId);
              }
            }}
          >
            <Mapbox.CircleLayer
              id="field-quest-waypoint-halo"
              slot="top"
              style={{
                circleRadius: ['case', ['get', 'hasMine'], 24, 15],
                circleColor: 'rgba(255, 255, 255, 0.5)',
                circleBlur: 0.9,
              }}
            />
            <Mapbox.CircleLayer
              id="field-quest-waypoint-circle"
              slot="top"
              style={{
                // 내 몫이 있는 점은 크게 강조하고, 완료면 브랜드 그린으로 채웁니다.
                circleRadius: ['case', ['get', 'hasMine'], 19, 11],
                circleColor: [
                  'case',
                  ['get', 'allDone'],
                  '#13B26E',
                  ['get', 'hasMine'],
                  '#FFFFFF',
                  '#D9DEDC',
                ],
                circleOpacity: 0.98,
                // 미완료 점은 초록 테두리로 "여기 할 일이 남았다"를 알립니다.
                circleStrokeColor: '#13B26E',
                circleStrokeWidth: ['case', ['get', 'allDone'], 0, ['get', 'hasMine'], 2.5, 0],
              }}
            />
            {/* 위쪽에 작고 또렷한 점광 하나만 — 유리알에 빛이 맺힌 자리. */}
            <Mapbox.CircleLayer
              id="field-quest-waypoint-gloss"
              slot="top"
              filter={['get', 'hasMine']}
              style={{
                circleRadius: 3.5,
                circleColor: 'rgba(255, 255, 255, 0.9)',
                circleTranslate: [-4.5, -6],
                circleBlur: 0.3,
              }}
            />
            <Mapbox.SymbolLayer
              id="field-quest-waypoint-label"
              slot="top"
              style={{
                textField: ['get', 'label'],
                textSize: ['case', ['get', 'allDone'], 17, 14],
                textFont: ['DIN Pro Bold', 'Arial Unicode MS Bold'],
                // 완료(초록 채움)는 흰 글자, 미완료(흰 채움)는 초록 글자, 남의 몫은 회색.
                textColor: [
                  'case',
                  ['get', 'allDone'],
                  '#FFFFFF',
                  ['get', 'hasMine'],
                  '#13B26E',
                  '#6B7570',
                ],
                textHaloColor: '#FFFFFF',
                textHaloWidth: ['case', ['get', 'allDone'], 0, 0.6],
                // 개수를 점 안에 그대로 두려면 지도 회전·기울기와 무관하게 고정해야 합니다.
                textAllowOverlap: true,
                textIgnorePlacement: true,
              }}
            />
            {/* 점 아래에 경유지 이름표. 번호 대신 장소명으로 어디인지 바로 알 수 있게 합니다.
                자리가 없으면(textOptional) 이름은 생략해 점이 가려지지 않게 합니다. */}
            <Mapbox.SymbolLayer
              id="field-quest-waypoint-name"
              slot="top"
              style={{
                textField: ['get', 'name'],
                textSize: 12,
                textFont: ['DIN Pro Bold', 'Arial Unicode MS Bold'],
                textColor: '#17332A',
                textHaloColor: '#FFFFFF',
                textHaloWidth: 1.8,
                // 점(최대 반경 24px) 아래로 이름을 내립니다.
                textOffset: [0, 1.9],
                textAnchor: 'top',
                textOptional: true,
              }}
            />
          </Mapbox.ShapeSource>
        )}

        {/* 네이티브 View 마커 대신 지도 레이어를 사용해 Android에서도 안정적으로 표시합니다. */}
        {isMedicalFilterEnabled && (
          <Mapbox.ShapeSource id="field-pediatric-source" shape={PEDIATRIC_FACILITY_SHAPE}>
            <Mapbox.SymbolLayer
              id="field-pediatric-marker"
              slot="top"
              minZoomLevel={13.4}
              style={{
                iconImage: 'pediatric-clinic',
                iconSize: [
                  'interpolate',
                  ['linear'],
                  ['zoom'],
                  13.4,
                  0.026,
                  15.5,
                  0.036,
                  18,
                  0.046,
                ],
                iconAnchor: 'bottom',
                iconAllowOverlap: true,
                iconIgnorePlacement: true,
                symbolSortKey: ['get', 'priority'],
              }}
            />
            <Mapbox.SymbolLayer
              id="field-pediatric-label"
              slot="top"
              minZoomLevel={14.5}
              style={{
                textField: ['get', 'name'],
                textSize: 12,
                textColor: '#235A78',
                textHaloColor: '#FFFFFF',
                textHaloWidth: 2,
                // 아이콘의 투명 여백까지 고려해 이름을 아이콘 아래로 충분히 내립니다.
                textOffset: [0, 2.15],
                textAnchor: 'top',
                textOptional: true,
                symbolSortKey: ['get', 'priority'],
              }}
            />
          </Mapbox.ShapeSource>
        )}

        {destinationShape !== null && (
          <Mapbox.ShapeSource id="field-destination-source" shape={destinationShape}>
            <Mapbox.CircleLayer
              id="field-destination-halo"
              slot="top"
              style={{
                circleRadius: 18,
                circleColor: 'rgba(255, 255, 255, 0.96)',
                circleStrokeColor: 'rgba(31, 95, 85, 0.18)',
                circleStrokeWidth: 2,
              }}
            />
            <Mapbox.CircleLayer
              id="field-destination-marker"
              slot="top"
              style={{
                circleRadius: 10,
                circleColor: '#63E69A',
                circleStrokeColor: '#1F5F55',
                circleStrokeWidth: 3,
              }}
            />
            <Mapbox.SymbolLayer
              id="field-destination-label"
              slot="top"
              style={{
                textField: ['get', 'name'],
                textSize: 13,
                textColor: '#1F5F55',
                textHaloColor: '#FFFFFF',
                textHaloWidth: 2,
                textOffset: [0, 2.2],
                textAnchor: 'top',
                textAllowOverlap: true,
              }}
            />
          </Mapbox.ShapeSource>
        )}
      </Mapbox.MapView>

      {!isCharacterLocated && (
        <View style={styles.characterOverlay} pointerEvents="none">
          <View style={styles.staticCharacter}>
            <CharacterSprite characterId={characterId} motion="idle" direction="S" reduceMotion />
          </View>
        </View>
      )}

      {/*
        임장 화면에는 탭바가 없으므로, 다른 화면(아파트·스터디 상세)과 같은 자리인
        왼쪽 위에 뒤로 가기 버튼을 둡니다. 임장을 끝내는 게 아니라 화면만 빠져나가는
        것이라, 세션은 그대로 두고 이동만 합니다(진행 중 임장은 홈의 복귀 버튼으로
        다시 들어올 수 있습니다 — ActiveFieldVisitReturnButton 참고).
      */}
      <GlassIconButton
        accessibilityLabel="뒤로 가기"
        onPress={goBackOrHome}
        style={[styles.backButton, { top: insets.top + 8 }]}
      >
        <Ionicons color={CONTROL_ICON_COLOR} name="chevron-back" size={25} />
      </GlassIconButton>

      {/*
        지도를 다루는 버튼 세 개(2D/3D · 내 위치 · 시설 필터)를 위로 모으고, 성격이
        다른 스터디 채팅은 맨 아래로 내립니다. 지도 탭도 같은 세 개를 같은 순서로
        씁니다(map.tsx).
      */}
      <View pointerEvents="box-none" style={[styles.mapControls, { top: insets.top + 8 }]}>
        {/* 2D/3D 는 라벨 텍스트로 상태를 알리므로, 눌러서 켜진 상태로 남는 색 강조는 주지
            않습니다(active 미전달). */}
        <GlassIconButton
          accessibilityLabel={is3dEnabled ? '2D 지도로 전환' : '3D 지도로 전환'}
          onPress={toggleMapDimension}
        >
          <Text style={styles.dimensionLabel}>{is3dEnabled ? '3D' : '2D'}</Text>
        </GlassIconButton>

        <GlassIconButton
          accessibilityLabel="현재 위치로 이동"
          disabled={!isCharacterLocated}
          onPress={moveToCurrentLocation}
        >
          <Ionicons
            color={CONTROL_ICON_COLOR}
            name="locate-outline"
            size={GLASS_ICON_BUTTON_ICON_SIZE}
          />
        </GlassIconButton>

        <GlassIconButton
          accessibilityLabel="지도 시설 필터 열기"
          active={isFilterOpen}
          expanded={isFilterOpen}
          onPress={() => setIsFilterOpen((open) => !open)}
        >
          <Ionicons
            color={CONTROL_ICON_COLOR}
            name="options-outline"
            size={GLASS_ICON_BUTTON_ICON_SIZE}
          />
        </GlassIconButton>

        <GlassIconButton accessibilityLabel="스터디 채팅 열기" onPress={handleOpenChat}>
          <Ionicons
            color={CONTROL_ICON_COLOR}
            name="chatbubble-ellipses-outline"
            size={GLASS_ICON_BUTTON_ICON_SIZE}
          />
        </GlassIconButton>
      </View>

      {/*
        임장 참여/종료 인원 요약. 상단 가운데(뒤로 가기·컨트롤 사이)에 띄우고,
        useFieldVisitParticipantCounts 가 5초마다 갱신합니다. 세션에 참여자가 한 명도
        없으면(아직 아무도 시작 안 함) 굳이 0/0 을 보여 주지 않도록 감춥니다.
      */}
      {participantCounts !== null &&
      participantCounts.active + participantCounts.ended > 0 ? (
        <View
          pointerEvents="none"
          style={[styles.participantPill, { top: insets.top + 8 }]}
        >
          <View style={styles.participantActiveDot} />
          <Text style={styles.participantText}>참여 {participantCounts.active}</Text>
          <Text style={styles.participantDivider}>·</Text>
          <Ionicons color={MUTED_TEXT_COLOR} name="flag" size={13} />
          <Text style={styles.participantText}>종료 {participantCounts.ended}</Text>
        </View>
      ) : null}

      {isDeveloperMode && developerCoordinate !== null ? (
        <DeveloperLocationControls
          onMove={handleDeveloperMove}
          onMoveEnd={handleDeveloperMoveEnd}
          reduceMotionEnabled={isReduceMotionEnabled}
          style={[styles.developerLocationControls, { top: insets.top + 76 }]}
        />
      ) : null}

      {isFilterOpen && (
        <FacilityFilterPanel
          expandedCategories={expandedCategories}
          onChangeShowAllFacilities={setShowAllFacilities}
          onChangeShowCoreFacilities={setShowCoreFacilities}
          onClose={() => setIsFilterOpen(false)}
          onToggleCategory={toggleFacilityCategory}
          showAllFacilities={showAllFacilities}
          showCoreFacilities={showCoreFacilities}
          style={{ ...styles.filterPanel, top: insets.top + 8 }}
        />
      )}

      {/*
        FE-015 — AI 체크리스트 바텀시트. map.tsx 의 sheetWindow 와 같은 방식으로
        자유롭게 끌어올릴 수 있게 합니다.

        창 위쪽 경계는 상태바 바로 아래까지만 남깁니다. 지도 컨트롤 버튼 3개(2D/3D·
        위치·필터)를 항상 가리지 않게 하려면 top 을 274px 이상 내려야 하는데, 그러면
        시트가 화면 절반 정도까지밖에 못 올라갑니다. 지도 탭의 검색바(항상 눌러야
        하는 필수 UI)와 달리 이 버튼들은 체크리스트를 보는 동안은 안 보여도 되는
        보조 기능이라 판단해, 시트를 최대로 펼치면 그 위로 덮이는 쪽을 택했습니다.
        아래쪽 경계는 지도 탭과 똑같이 떠 있는 탭바 위에서 끝납니다.
      */}
      <ChecklistSheet
        focusedWaypointId={focusedWaypointId}
        isGeneratingRoute={isGeneratingRoute}
        onFocusedWaypointChange={setFocusedWaypointId}
        onGenerateRoute={handleGenerateRoute}
        onItemCompletionChange={handleItemCompletionChange}
        route={fieldRoute}
        studyId={studyId}
        windowBottom={sheetBottomInset}
        windowTop={insets.top + 16}
      />

      {/*
        임장 도우미 챗봇. 대상 아파트를 확인한 뒤에만 그 아파트 컨텍스트로 엽니다.
        체크리스트를 펼치면 시트 레이어가 챗봇보다 위에서 겹친 영역을 가립니다.
      */}
      {chatbotApartmentId !== null && (
        <>
          <ChatbotFab
            bottom={sheetBottomInset + 96}
            onPress={() => setIsChatOpen(true)}
            right={16}
            style={styles.chatbotFabBelowSheet}
          />
          <ChatbotModal
            apartmentId={chatbotApartmentId}
            apartmentName={chatbotApartmentName}
            onClose={() => setIsChatOpen(false)}
            open={isChatOpen}
          />
        </>
      )}
    </View>
  );
}

const styles = StyleSheet.create({
  container: { flex: 1 },
  map: { flex: 1 },
  characterOverlay: { position: 'absolute', top: 0, left: 0, right: 0, bottom: 0 },
  staticCharacter: { flex: 1, alignItems: 'center', justifyContent: 'center' },
  // top 은 노치 높이에 맞춰 인라인으로 줍니다(뒤로 가기 버튼과 같은 줄에서 시작).
  backButton: { position: 'absolute', left: 16, zIndex: 25 },
  mapControls: { position: 'absolute', right: 16, gap: MAP_CONTROL_GAP, zIndex: 25 },
  // 공통 ChatbotFab는 다른 화면의 시트 위에 뜰 수 있도록 elevation 40을 사용하지만,
  // 임장 체크리스트에서는 시트가 덮은 의견 버튼의 터치를 가로채면 안 됩니다.
  chatbotFabBelowSheet: { zIndex: 24, elevation: 0 },
  // 참여/종료 인원 알약. 상단 가운데(뒤로 가기 버튼과 컨트롤 열 사이)에 띄웁니다.
  participantPill: {
    position: 'absolute',
    alignSelf: 'center',
    flexDirection: 'row',
    alignItems: 'center',
    gap: 6,
    height: 40,
    paddingHorizontal: 14,
    borderRadius: 20,
    backgroundColor: 'rgba(255, 255, 255, 0.92)',
    borderWidth: 1,
    borderColor: BORDER_COLOR,
    zIndex: 25,
    shadowColor: '#000',
    shadowOpacity: 0.12,
    shadowRadius: 8,
    shadowOffset: { width: 0, height: 2 },
    elevation: 4,
  },
  participantActiveDot: {
    width: 8,
    height: 8,
    borderRadius: 4,
    backgroundColor: PRIMARY_COLOR,
  },
  participantText: { fontSize: 13, fontWeight: '700', color: '#24302E' },
  participantDivider: { fontSize: 13, color: MUTED_TEXT_COLOR },
  developerLocationControls: { left: 16, position: 'absolute' },
  dimensionLabel: {
    color: '#24302E',
    fontSize: 13,
    fontWeight: '900',
    textShadowColor: 'rgba(255, 255, 255, 0.8)',
    textShadowOffset: { width: 0, height: 1 },
    textShadowRadius: 2,
  },
  // 생김새는 FacilityFilterPanel 이 갖고 있고, 여기서는 놓을 자리만 정합니다.
  // 컨트롤 버튼 왼쪽에 붙여 띄웁니다(버튼 지름 + 간격).
  filterPanel: {
    position: 'absolute',
    right: 16 + GLASS_ICON_BUTTON_SIZE + 10,
    zIndex: 26,
  },
});
