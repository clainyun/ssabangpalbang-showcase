import BottomSheet, { BottomSheetFlatList } from '@gorhom/bottom-sheet';
import Ionicons from '@expo/vector-icons/Ionicons';
import Mapbox from '@rnmapbox/maps';
import * as Location from 'expo-location';
import { useRouter } from 'expo-router';
import { memo, useCallback, useEffect, useMemo, useRef, useState } from 'react';
import {
  ActivityIndicator,
  AppState,
  Image,
  Keyboard,
  Linking,
  type ListRenderItem,
  Pressable,
  StyleSheet,
  Text,
  TextInput,
  View,
} from 'react-native';
import {
  KeyboardState,
  runOnJS,
  useAnimatedKeyboard,
  useAnimatedReaction,
  useSharedValue,
} from 'react-native-reanimated';
import { useSafeAreaInsets } from 'react-native-safe-area-context';

import fieldMapStyle from '@/assets/mapStyles/fieldMapStyle.json';
import { FacilityFilterPanel } from '@/components/FacilityFilterPanel';
import {
  GlassIconButton,
  GLASS_ICON_BUTTON_ICON_SIZE,
  GLASS_ICON_BUTTON_SIZE,
} from '@/components/GlassIconButton';
import { createSheetGlassBackground } from '@/components/SheetGlassBackground';
import { SheetHandleIconBadge } from '@/components/SheetHandleIconBadge';
import { INACTIVE_COLOR as TAB_LABEL_COLOR, TAB_LABEL_FONT_BOLD } from '@/components/TabIcon';
import {
  BORDER_COLOR,
  DARK_GREEN_COLOR,
  MUTED_TEXT_COLOR,
  PRIMARY_COLOR,
  SOFT_GREEN_COLOR,
  SURFACE_COLOR,
} from '@/constants/colors';
import {
  searchApartments,
  type ApartmentSearchItem,
} from '@/features/apartment/api/searchApartments';
import { apartmentImageSource } from '@/features/apartment/apartmentImage';
import {
  formatAreaLabel,
  formatDistance,
  formatPriceDetail,
  formatPriceLabel,
  formatShortAddress,
} from '@/features/apartment/format';
import {
  getApartmentsInBounds,
  type ApartmentBoundsItem,
} from '@/features/map/api/getApartmentsInBounds';
import { useDistrictSummary } from '@/features/map/api/useDistrictSummary';
import {
  buildDistrictCards,
  type DistrictCard,
  EMPTY_DISTRICT_CARDS,
  EMPTY_FEATURE_COLLECTION,
} from '@/features/map/districtCluster';
import { DistrictCountCard } from '@/features/map/DistrictCountCard';
import { POI_LABEL_STYLE_OVERRIDE, useFacilityFilters } from '@/features/map/facilityFilters';
import { isMapSheetExpanded } from '@/features/map/mapSheetControls';
import {
  DISTRICT_MODE_MAX_ZOOM,
  MAP_TAB_CAMERA_BOUNDS,
  MAP_TAB_MIN_ZOOM_LEVEL,
} from '@/lib/mapBounds';
import { explainBeforeRequest, showPermanentlyDeniedAlert } from '@/lib/permissions';

type Coordinate = [number, number];

interface ApartmentMapItem {
  id: number;
  name: string;
  address: string;
  longitude: number;
  latitude: number;
  areaLabel: string;
  /** 마커용 짧은 표기. 예: 24.8억 */
  recentPriceLabel: string;
  /** 목록 카드용 전체 표기. 예: 15억 2,000만 원 */
  priceDetailLabel: string;
  /** 최근 정상 실거래가 있는지. 마커 라벨을 짧게 줄일지 판단하는 데 씁니다. */
  hasTransaction: boolean;
}

/**
 * 시트 목록 한 줄. 가시 영역 조회(BE-004)와 검색(BE-041)은 응답 형태가 달라서
 * (검색에는 좌표가 없고 거리가 있음) 카드가 쓰는 항목만 뽑아 하나로 맞춥니다.
 */
interface ApartmentListRow {
  id: number;
  name: string;
  address: string;
  priceDetailLabel: string;
  /**
   * 법정동. 검색은 아파트명뿐 아니라 동 이름으로도 걸리므로('역삼' → 역삼동의
   * 롯데캐슬노블) 왜 이 결과가 나왔는지 보이도록 함께 표시합니다.
   * 가시 영역 조회(BE-004) 응답에는 없어서 그때는 null 입니다.
   */
  dongName: string | null;
  /** 검색 시 현재 위치를 함께 보냈을 때만. 예: 320m */
  distanceLabel: string | null;
}

interface ViewportBounds {
  ne: number[];
  sw: number[];
}

interface ApartmentPointFeature {
  type: 'Feature';
  id: number;
  properties: {
    apartmentId: number;
    name: string;
    areaLabel: string;
    priceLabel: string;
  };
  geometry: {
    type: 'Point';
    coordinates: Coordinate;
  };
}

interface ApartmentFeatureCollection {
  type: 'FeatureCollection';
  features: ApartmentPointFeature[];
}

interface PressedMapProperties {
  apartmentId?: number;
  cluster?: boolean;
  cluster_id?: number;
}

type PressedMapFeature = GeoJSON.Feature<GeoJSON.Point, PressedMapProperties>;

type ViewportState = 'loading' | 'success' | 'empty' | 'error';
type SearchState = 'loading' | 'success' | 'empty' | 'error';
type MapMode = 'district' | 'apartment';
/**
 * 목록에 무엇을 담을지. 'viewport' 는 지도에 보이는 영역(BE-004), 'nearby' 는 현재
 * 위치 반경(BE-041 NEARBY 모드)입니다. 검색어는 이 범위 안에서 다시 걸러집니다.
 */
type ListScope = 'viewport' | 'nearby';
type LocationPermissionState = 'checking' | 'granted' | 'denied';

const VIEWPORT_DEBOUNCE_MS = 400;
const INITIAL_VIEWPORT_FOLLOW_FALLBACK_MS = 1_500;
const INITIAL_VIEWPORT_RETRY_MS = 250;
const INITIAL_VIEWPORT_MAX_ATTEMPTS = 8;

/**
 * 검색어 입력 디바운스. 지도 조회(400ms)보다 짧게 잡아 타이핑 반응이 굼떠 보이지
 * 않게 하되, 한 글자마다 요청이 나가지는 않을 정도로 둡니다.
 */
const SEARCH_DEBOUNCE_MS = 350;

/** 서버 검증(ApartmentSearchCondition)과 같은 규칙. 한글 자모만 있는 입력은 400 이 납니다. */
const SEARCHABLE_PATTERN = /[가-힣A-Za-z0-9]/;

/** '내 주변' 반경. 서버 허용 범위는 100~10,000m 이고 기본값은 3,000m 입니다. */
const NEARBY_RADIUS_METERS = 1000;

/** 위치 권한이 없거나 위치 서비스가 꺼져 있을 때 쓰는 기본 카메라(서울 중심). */
const SEOUL_CENTER_COORDINATE: Coordinate = [127.095, 37.511];
const SEOUL_CENTER_ZOOM_LEVEL = 13.5;

/**
 * 내 위치로 카메라가 붙을 때 쓰는 줌. 진입 시 첫 좌표를 잡을 때도 같은 값을 써서,
 * '내 위치로 스냅 → 추적 켜짐' 전환 사이에 줌이 다시 바뀌는 게 안 보이게 합니다.
 */
const FOLLOW_ZOOM_LEVEL = 14;

/**
 * 검색 결과로 카메라를 옮길 때 최소로 보여 줄 가로·세로 범위.
 *
 * 결과에 딱 맞춰 확대하면 한 단지만 나왔을 때 골목까지 파고들고, 그 상태에서
 * '지도 영역'으로 전환하면 그 좁은 범위만 조회됩니다. 검색한 동네가 통째로 보이도록
 * 최소한 이만큼은 펼쳐 둡니다.
 */
const SEARCH_MIN_VISIBLE_SPAN_METERS = 3000;

/** fitBounds 여백 [위, 오른쪽, 아래, 왼쪽]. 아래는 바텀시트가 덮는 만큼 크게 잡습니다. */
const CAMERA_FIT_PADDING: [number, number, number, number] = [80, 40, 320, 40];

/** 위도 1도의 거리(m). 경도는 위도에 따라 좁아지므로 cos 를 곱해 씁니다. */
const METERS_PER_LATITUDE_DEGREE = 111_320;

interface BoundingBox {
  /** [경도, 위도] */
  ne: [number, number];
  sw: [number, number];
}

function metersPerLongitudeDegree(latitude: number): number {
  return METERS_PER_LATITUDE_DEGREE * Math.cos((latitude * Math.PI) / 180);
}

/**
 * 단지들을 모두 담는 사각형. minSpanMeters 보다 좁으면 중심을 유지한 채 넓힙니다.
 * 단지가 없으면 null.
 */
function boundingBoxOf(
  apartments: ApartmentMapItem[],
  minSpanMeters: number,
): BoundingBox | null {
  if (apartments.length === 0) return null;

  const longitudes = apartments.map((apartment) => apartment.longitude);
  const latitudes = apartments.map((apartment) => apartment.latitude);
  const centerLatitude = (Math.min(...latitudes) + Math.max(...latitudes)) / 2;
  const centerLongitude = (Math.min(...longitudes) + Math.max(...longitudes)) / 2;

  const latitudeSpan = Math.max(
    Math.max(...latitudes) - Math.min(...latitudes),
    minSpanMeters / METERS_PER_LATITUDE_DEGREE,
  );
  const longitudeSpan = Math.max(
    Math.max(...longitudes) - Math.min(...longitudes),
    minSpanMeters / metersPerLongitudeDegree(centerLatitude),
  );

  return {
    ne: [centerLongitude + longitudeSpan / 2, centerLatitude + latitudeSpan / 2],
    sw: [centerLongitude - longitudeSpan / 2, centerLatitude - latitudeSpan / 2],
  };
}

/**
 * 서버는 locationName 을 '서울특별시 성동구 옥수동' 처럼 전체로 줍니다. 헤더에는
 * 시·구가 반복돼 정보량이 없으므로 마지막 동 이름만 씁니다.
 */
function shortLocationName(locationName: string | null): string | null {
  if (locationName === null) return null;
  const parts = locationName.trim().split(/\s+/);

  return parts[parts.length - 1] ?? null;
}

/**
 * 시트를 띄워 둘 높이. 탭바가 화면 위에 떠 있는(absolute) 구조라
 * ((tabs)/_layout.tsx: height 84 + marginBottom) 그만큼 위로 올려야 가리지 않습니다.
 */
const FLOATING_TAB_BAR_HEIGHT = 84;

/** 시트와 탭 바가 붙어 보이지 않도록 두는 최소한의 간격. */
const SHEET_BOTTOM_GAP = 10;

/** 상단 검색바 블록의 각 조각. 시트가 올라갈 수 있는 한계를 계산하는 데도 씁니다. */
const TOP_OVERLAY_TOP_GAP = 12;
const SEARCH_BAR_HEIGHT = 46;
const SEARCH_CHIPS_GAP = 8;
const SCOPE_CHIP_HEIGHT = 30;

/** 검색바 + 칩이 차지하는 높이. */
const TOP_OVERLAY_HEIGHT = SEARCH_BAR_HEIGHT + SEARCH_CHIPS_GAP + SCOPE_CHIP_HEIGHT;

/** 검색바 블록과 시트 사이에 남길 지도 여백. */
const SHEET_TOP_GAP = 16;

/**
 * 지도 컨트롤(2D/3D · 내 위치 · 시설 필터) 세로 묶음이 시작하는 높이.
 *
 * 임장 지도는 같은 버튼 묶음을 화면 맨 위(상태바 바로 아래)에 두는데, 이 화면은
 * 그 자리를 검색바가 차지하고 있어 같은 높이로는 못 올립니다. 대신 시트 기준선
 * (TOP_OVERLAY_HEIGHT — 지금은 안 그리는 범위 칩 높이까지 포함) 이 아니라 검색바
 * 바로 아래에 붙여, 임장 지도와 최대한 가까운 위치에서 시작하게 합니다.
 */
const MAP_CONTROLS_TOP = TOP_OVERLAY_TOP_GAP + SEARCH_BAR_HEIGHT + 12;

/**
 * 시트가 멈추는 지점(창 높이 기준). 손을 떼면 가장 가까운 지점으로 붙으므로,
 * 중간 단계를 두면 원하는 높이에 맞추기 쉬워집니다.
 * 접힘(헤더만) → 지도 위주 → 반반 → 목록 위주 네 단계입니다.
 *
 * 최대치가 100% 여도 검색바를 덮지 않습니다. 시트가 놓이는 창 자체를 검색바 블록
 * 아래에서 시작하도록 잡아서, 창의 100% 가 곧 '칩 바로 아래'가 되기 때문입니다.
 */
const SHEET_SNAP_POINTS: (string | number)[] = [96, '35%', '65%', '100%'];

/** 헤더를 눌렀을 때 펼쳐지는 높이. */
const SHEET_EXPANDED_INDEX = SHEET_SNAP_POINTS.length - 1;

/** 창(sheetWindow)·카드(sheetBackground)·유리 배경이 다 같은 모서리를 쓰도록 한곳에. */
const SHEET_RADIUS = 28;
/** 시트가 덮은 영역에서는 지도 컨트롤(25)과 필터(26)보다 위에 표시합니다. */
const SHEET_LAYER_Z_INDEX = 50;

/**
 * 시트 배경(유리 → 그라디언트 전환)은 체크리스트 시트와 함께 쓰는 공용 컴포넌트입니다
 * (SheetGlassBackground.tsx). 렌더마다 새로 만들지 않도록 모듈 스코프에서 한 번만
 * 생성합니다.
 */
const MapSheetBackground = createSheetGlassBackground(SHEET_RADIUS, SHEET_EXPANDED_INDEX);

/**
 * 검색·내 주변으로 전환됐을 때 자동으로 올라가는 높이.
 *
 * 이때는 카메라도 결과 쪽으로 옮기므로, 시트를 너무 올리면 정작 마커가 시트에 가려
 * 어디로 옮겨졌는지 안 보입니다. 지도를 넉넉히 남기는 35% 로 두고, 목록을 훑고
 * 싶으면 손으로 올리게 합니다.
 */
const SHEET_RESULT_INDEX = 1;
/** 물방울 버튼 안 아이콘 색. 반투명 배경 위에서도 읽히도록 진한 슬레이트. */
const CONTROL_ICON_COLOR = '#26343A';

const APARTMENT_PRICE_MARKER_IMAGE = require('../../../assets/images/map/apartment-price-marker.png');

/**
 * 78KB 스타일 JSON을 렌더마다 새로 stringify 하지 않도록 모듈 스코프에서 한 번만 만듭니다.
 * MapScreen 이 리렌더될 때(검색어 입력 등)마다 반복 직렬화되는 게 지도 탭 체감 지연의
 * 큰 비중이었고, 매번 새 문자열이라 prop 정체성이 바뀌어 rnmapbox 가 스타일을 다시
 * 적용할 위험도 있었습니다.
 */
const MAP_STYLE_JSON = JSON.stringify(fieldMapStyle);

function toApartmentMapItem(item: ApartmentBoundsItem): ApartmentMapItem {
  const transaction = item.latestTransaction;

  return {
    id: item.apartmentId,
    name: item.name,
    address: item.address,
    longitude: item.longitude,
    latitude: item.latitude,
    areaLabel: transaction ? formatAreaLabel(transaction.exclusiveArea) : '',
    recentPriceLabel: transaction ? formatPriceLabel(transaction.price) : '최근 거래 없음',
    priceDetailLabel: transaction ? formatPriceDetail(transaction.price) : '최근 실거래 정보 없음',
    hasTransaction: transaction !== null,
  };
}

function toListRowFromMapItem(apartment: ApartmentMapItem): ApartmentListRow {
  return {
    id: apartment.id,
    name: apartment.name,
    address: apartment.address,
    priceDetailLabel: apartment.priceDetailLabel,
    dongName: null,
    distanceLabel: null,
  };
}

/**
 * 검색 결과를 지도 마커용 항목으로. 좌표가 비어 있는 단지는 찍을 수 없어 제외합니다.
 */
function toMapItemsFromSearchItems(items: ApartmentSearchItem[]): ApartmentMapItem[] {
  return items.flatMap((item) => {
    // === null 로만 보면 필드 자체가 없는 응답(undefined)을 놓쳐 NaN 좌표가 흘러갑니다.
    // 실제로 좌표 추가 전 서버에 붙었을 때 Mapbox 가 'coordinates must contain numbers'
    // 로 터졌던 지점이라, 숫자인지로 판정합니다.
    const { latitude, longitude } = item;
    if (typeof latitude !== 'number' || !Number.isFinite(latitude)) return [];
    if (typeof longitude !== 'number' || !Number.isFinite(longitude)) return [];

    const transaction = item.latestTransaction;

    return [
      {
        id: item.apartmentId,
        name: item.name,
        address: item.address,
        longitude,
        latitude,
        areaLabel: transaction ? formatAreaLabel(transaction.exclusiveArea) : '',
        recentPriceLabel: transaction ? formatPriceLabel(transaction.price) : '최근 거래 없음',
        priceDetailLabel: transaction
          ? formatPriceDetail(transaction.price)
          : '최근 실거래 정보 없음',
        hasTransaction: transaction !== null,
      },
    ];
  });
}

function toListRowFromSearchItem(item: ApartmentSearchItem): ApartmentListRow {
  const transaction = item.latestTransaction;

  return {
    id: item.apartmentId,
    name: item.name,
    address: item.address,
    priceDetailLabel: transaction
      ? formatPriceDetail(transaction.price)
      : '최근 실거래 정보 없음',
    dongName: item.dongName,
    distanceLabel: formatDistance(item.distanceMeters),
  };
}

function deduplicateApartments(apartments: ApartmentMapItem[]): ApartmentMapItem[] {
  return [...new Map(apartments.map((apartment) => [apartment.id, apartment])).values()];
}

function toApartmentFeatureCollection(
  apartments: ApartmentMapItem[],
): ApartmentFeatureCollection {
  return {
    type: 'FeatureCollection',
    features: apartments.map((apartment) => ({
      type: 'Feature',
      id: apartment.id,
      properties: {
        apartmentId: apartment.id,
        name: apartment.name,
        areaLabel: apartment.areaLabel,
        // 마커는 폭이 좁아 긴 문구가 넘칩니다. 거래가 없으면 대시 하나로 줄입니다.
        priceLabel: apartment.hasTransaction ? apartment.recentPriceLabel : '–',
      },
      geometry: {
        type: 'Point',
        coordinates: [apartment.longitude, apartment.latitude],
      },
    })),
  };
}

/** 빈 배열도 매 렌더 같은 참조를 유지해 FlatList 가 불필요하게 다시 그리지 않도록 합니다. */
const EMPTY_LIST_ROWS: ApartmentListRow[] = [];

function apartmentRowKeyExtractor(item: ApartmentListRow): string {
  return String(item.id);
}

interface ApartmentRowItemProps {
  apartment: ApartmentListRow;
  isSelected: boolean;
  onPress: (apartmentId: number) => void;
}

/**
 * 뷰포트 조회는 최대 500건까지 옵니다(실제로 강남 근처는 401건). BottomSheetScrollView
 * 안에서 .map() 으로 한 번에 마운트하면 화면에 6행 정도만 보여도 401개 Pressable/Image가
 * 전부 인스턴스화됐습니다. BottomSheetFlatList(가상화) + memo 로 실제로 보이는 행만
 * 마운트되게 합니다.
 */
const ApartmentRowItem = memo(function ApartmentRowItem({
  apartment,
  isSelected,
  onPress,
}: ApartmentRowItemProps) {
  return (
    <Pressable
      accessibilityLabel={`${apartment.name} 상세 보기`}
      accessibilityRole="button"
      onPress={() => onPress(apartment.id)}
      style={({ pressed }) => [
        styles.apartmentRow,
        isSelected && styles.apartmentRowSelected,
        pressed && styles.pressed,
      ]}
    >
      <Image source={apartmentImageSource(apartment.id)} style={styles.apartmentThumb} />
      <View style={styles.apartmentRowMain}>
        <Text numberOfLines={1} style={styles.apartmentName}>
          {apartment.name}
        </Text>
        <Text numberOfLines={1} style={styles.apartmentDescription}>
          {[apartment.distanceLabel, apartment.dongName, formatShortAddress(apartment.address)]
            .filter(Boolean)
            .join(' · ')}
        </Text>
        <Text style={styles.apartmentPrice}>{apartment.priceDetailLabel}</Text>
      </View>
      <Ionicons color={MUTED_TEXT_COLOR} name="chevron-forward" size={20} />
    </Pressable>
  );
});

export default function MapScreen() {
  const router = useRouter();
  const insets = useSafeAreaInsets();
  const districtSummaryQuery = useDistrictSummary();
  const sheetRef = useRef<BottomSheet>(null);
  const cameraRef = useRef<Mapbox.Camera>(null);
  const mapViewRef = useRef<Mapbox.MapView>(null);
  const apartmentSourceRef = useRef<Mapbox.ShapeSource>(null);
  /**
   * 위치 권한 확인이 끝나기 전 고정 좌표로 뜬 첫 onMapIdle 은 버리고 조회를 미룹니다.
   * 권한이 있으면 뒤이어 내 위치로 카메라가 옮겨가며 idle 이 다시 발생해 자연스럽게
   * 처리되지만, 권한이 없거나 위치 서비스가 꺼져 있으면 카메라가 움직이지 않아 idle 이
   * 다시 오지 않습니다 — 그 경우를 위해 이 값으로 '수동 조회를 이미 걸었는지'를 한 번만
   * 표시해 둡니다.
   */
  const hasResolvedInitialViewportRef = useRef(false);
  /** 검색 포커스 전 시트 높이. 키보드가 닫히면 사용자가 보던 높이로 돌려놓습니다. */
  const currentSheetIndexRef = useRef(0);
  const sheetIndexBeforeSearchFocusRef = useRef(0);
  /** 검색창에 포커스가 있는 동안인지. 자동 스냅이 입력 중 시트를 내리지 못하게 막습니다. */
  const isSearchFocusedRef = useRef(false);
  const viewportDebounceRef = useRef<ReturnType<typeof setTimeout> | null>(null);
  const viewportAbortControllerRef = useRef<AbortController | null>(null);
  const viewportRequestIdRef = useRef(0);
  const latestViewportBoundsRef = useRef<ViewportBounds | null>(null);
  const latestMapZoomRef = useRef(SEOUL_CENTER_ZOOM_LEVEL);
  /** 마지막으로 조회를 건 경계. 같은 경계로 들어온 onMapIdle 반복을 걸러냅니다. */
  const lastBoundsSignatureRef = useRef<string | null>(null);
  const searchDebounceRef = useRef<ReturnType<typeof setTimeout> | null>(null);
  /** followUserLocation 이 꺼지기를 기다리는 카메라 이동. */
  const pendingCameraMoveRef = useRef<(() => void) | null>(null);
  const isFollowingUserRef = useRef(false);
  /** 최신 조회어. 입력 핸들러가 state 를 의존성으로 잡지 않고 비교하기 위한 값입니다. */
  const searchKeywordRef = useRef('');


  const [visibleApartments, setVisibleApartments] = useState<ApartmentMapItem[]>([]);
  // 마커·목록 탭이 모두 상세 화면으로 이동하도록 바뀌면서 선택 상태를 세팅하는 곳이 없어졌습니다.
  // 목록 행의 선택 하이라이트 배선은 남겨 두되(다시 필요해질 수 있음) 값은 항상 null 입니다.
  const [selectedApartment] = useState<ApartmentMapItem | null>(null);
  const [hasLocationPermission, setHasLocationPermission] = useState(false);
  const [locationPermissionState, setLocationPermissionState] =
    useState<LocationPermissionState>('checking');
  const [canAskLocationPermissionAgain, setCanAskLocationPermissionAgain] = useState(true);
  const [isLocationServiceEnabled, setIsLocationServiceEnabled] = useState<boolean | null>(null);
  const [isFollowingUser, setIsFollowingUser] = useState(false);
  /**
   * 카메라 초기값. 권한이 있으면 서울을 거치지 않고 바로 이 값을 내 위치로 바꿔 둔
   * 다음 추적을 켭니다(아래 pendingFollowActivation 참고). 권한이 없거나 못 구하면
   * 서울 기본값 그대로 둡니다.
   */
  const [initialCameraCoordinate, setInitialCameraCoordinate] =
    useState<Coordinate>(SEOUL_CENTER_COORDINATE);
  const [initialCameraZoomLevel, setInitialCameraZoomLevel] = useState(SEOUL_CENTER_ZOOM_LEVEL);
  /**
   * 좌표를 내 위치로 옮긴 바로 다음 렌더에서 추적을 켜야 하는지 표시하는 플래그.
   *
   * 좌표 변경과 추적 켜기를 같은 틱에 같이 하면, rnmapbox 가 그 렌더에서 이미
   * followUserLocation 을 true 로 보고 이 좌표 변경 자체를 무시합니다(카메라가
   * 안 움직임 — Camera.js 의 buildNativeStop 이 추적 중엔 null 을 반환). 그래서
   * 좌표를 먼저 반영한 뒤, 그 다음 렌더에서 추적을 켭니다.
   */
  const [pendingFollowActivation, setPendingFollowActivation] = useState(false);
  const [viewportState, setViewportState] = useState<ViewportState>('loading');
  const [zoomMode, setZoomMode] = useState<MapMode>('apartment');
  // 시트는 index={0}(접힘)으로 마운트되고 animateOnMount={false}라 마운트 시 onChange가
  // 불리지 않는다. 초기값을 true로 두면 접혀 있는데도 꺽쇠가 아래(chevron-down)로 보였다.
  // 접힘 상태에 맞춰 false로 시작해 "끌어 올리세요" 뜻의 chevron-up 이 보이게 한다.
  const [isSheetExpanded, setIsSheetExpanded] = useState(false);
  const sheetAnimatedIndex = useSharedValue(0);
  useAnimatedReaction(
    () => isMapSheetExpanded(sheetAnimatedIndex.value),
    (expanded, previous) => {
      if (expanded === previous) return;
      runOnJS(setIsSheetExpanded)(expanded);
    },
    [sheetAnimatedIndex],
  );
  // 임장 지도와 같은 3버튼 구성: 2D/3D · 내 위치 · 시설 필터.
  const [is3dEnabled, setIs3dEnabled] = useState(false);
  const [isFilterOpen, setIsFilterOpen] = useState(false);
  /**
   * 지도의 현재 방위(도, 0=정북). 회전이 멈춘 onMapIdle 에서만 갱신합니다. 0 이 아니면
   * 나침반 버튼을 띄우고, 누르면 정북으로 되돌립니다.
   */
  const [mapHeading, setMapHeading] = useState(0);
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
  } = useFacilityFilters({ cameraRef, mapViewRef });
  const [viewportErrorMessage, setViewportErrorMessage] = useState<string | null>(null);

  // 검색(FE-040 / BE-041). 입력값과 실제 조회어를 분리해 두어야 디바운스 중에도
  // 입력창이 즉시 반응합니다.
  const [searchInput, setSearchInput] = useState('');
  const [searchKeyword, setSearchKeyword] = useState('');
  const [searchResults, setSearchResults] = useState<ApartmentListRow[]>([]);
  /** 검색 결과 중 좌표가 있는 것들. 지도 마커로 그립니다. */
  const [searchMapItems, setSearchMapItems] = useState<ApartmentMapItem[]>([]);
  const [searchTotalCount, setSearchTotalCount] = useState(0);
  const [searchState, setSearchState] = useState<SearchState>('success');
  const [searchErrorMessage, setSearchErrorMessage] = useState<string | null>(null);
  // 범위 전환 칩('지도 영역' / '내 주변')을 걷어내면서 값을 바꾸는 곳이 없어졌습니다.
  // 검색 흐름이 두 값을 계속 읽으므로 상태 자체는 남겨 둡니다(항상 viewport / null).
  const [listScope] = useState<ListScope>('viewport');
  const [nearbyOrigin] = useState<{
    latitude: number;
    longitude: number;
  } | null>(null);
  /** 서버가 알려준 현재 위치의 동 이름. '옥수동 반경 1km' 헤더에 씁니다. */
  const [nearbyLocationName, setNearbyLocationName] = useState<string | null>(null);

  // edge-to-edge Android에서는 adjustResize만으로 절대 배치된 시트가 키보드 위로
  // 올라오지 않습니다. 키보드 높이만큼 시트 창의 bottom을 직접 끌어올립니다.
  const keyboard = useAnimatedKeyboard();
  const [keyboardHeight, setKeyboardHeight] = useState(0);
  /**
   * 검색창에 포커스가 있는지(상태판 — isSearchFocusedRef 와 별개로 렌더에서 씁니다).
   * 이 화면에서 키보드가 뜨는 경우는 검색 입력뿐이라, 시트 창을 키보드 높이만큼
   * 끌어올리는 건 검색 중일 때만 해야 합니다. 권한 다이얼로그처럼 검색과 무관하게
   * window inset 이 흔들리면 useAnimatedKeyboard 가 헛된 keyboard OPEN 을 낼 수 있는데,
   * 그때 창을 줄이면 시트가 창 밖(overflow:hidden)으로 잘려 통째로 사라집니다
   * (지도 첫 진입 시 권한 프롬프트 직후 시트가 안 보이던 원인). 포커스일 때만 반영해
   * 검색과 무관한 헛 이벤트가 시트를 건드리지 못하게 막습니다.
   */
  const [isSearchFocused, setIsSearchFocused] = useState(false);
  const restingSheetBottom =
    FLOATING_TAB_BAR_HEIGHT + Math.max(18, insets.bottom + 8) + SHEET_BOTTOM_GAP;
  const sheetWindowBottom =
    isSearchFocused && keyboardHeight > 0
      ? keyboardHeight + SHEET_BOTTOM_GAP
      : restingSheetBottom;

  /**
   * 키보드 높이를 '리액트 상태'로 받습니다. useAnimatedStyle 로 bottom 을 UI 스레드에서
   * 직접 옮기면 시트 창은 올라가지만 BottomSheet 가 창 높이 변화를 못 받습니다. 스냅 높이는
   * 창 높이의 비율(35%/65%/100%)이라, 창이 줄어든 걸 모른 채 예전 높이로 계산한 위치에
   * 시트를 두고 창 바깥(overflow: hidden)으로 잘려 나가 시트가 통째로 사라집니다.
   * 상태로 바꾸면 정상적으로 다시 레이아웃돼 onLayout → 스냅 높이 재계산까지 이어집니다.
   *
   * 프레임마다 setState 하지 않도록 열림/닫힘이 확정된 순간에만 반영합니다.
   */
  useAnimatedReaction(
    () => keyboard.state.value,
    (state, previousState) => {
      if (state === previousState) return;
      if (state === KeyboardState.OPEN) {
        runOnJS(setKeyboardHeight)(Math.round(keyboard.height.value));
      } else if (state === KeyboardState.CLOSED) {
        runOnJS(setKeyboardHeight)(0);
      }
    },
  );

  const isSearching = searchKeyword.length > 0;
  const isNearby = listScope === 'nearby';
  /** 목록을 검색 API(BE-041)로 채우는 상태. 아니면 지도 영역 조회(BE-004)를 씁니다. */
  const isRemoteList = isSearching || isNearby;
  const mapMode: MapMode =
    isRemoteList || districtSummaryQuery.isError ? 'apartment' : zoomMode;
  const isDistrictMode = mapMode === 'district';

  // 지도에 그릴 단지. 검색·내 주변일 때는 그 결과를, 아니면 가시 영역 조회 결과를 씁니다.
  const mapItems = isRemoteList ? searchMapItems : visibleApartments;
  const apartmentFeatureCollection = useMemo(
    () => (isDistrictMode ? EMPTY_FEATURE_COLLECTION : toApartmentFeatureCollection(mapItems)),
    [isDistrictMode, mapItems],
  );
  const districtCards = useMemo(
    () =>
      isDistrictMode && districtSummaryQuery.data
        ? buildDistrictCards(districtSummaryQuery.data)
        : EMPTY_DISTRICT_CARDS,
    [districtSummaryQuery.data, isDistrictMode],
  );

  const cancelViewportQuery = useCallback(() => {
    if (viewportDebounceRef.current) {
      clearTimeout(viewportDebounceRef.current);
      viewportDebounceRef.current = null;
    }
    viewportAbortControllerRef.current?.abort();
    viewportAbortControllerRef.current = null;
    viewportRequestIdRef.current += 1;
  }, []);

  const requestLocationPermission = useCallback(async () => {
    const current = await Location.getForegroundPermissionsAsync();
    let result = current;

    if (!current.granted) {
      const shouldRequest = await explainBeforeRequest('location');
      if (shouldRequest) {
        result = await Location.requestForegroundPermissionsAsync();
        if (!result.granted && !result.canAskAgain) {
          showPermanentlyDeniedAlert('location');
        }
      }
    }

    const [permissionResult, servicesEnabled] = await Promise.all([
      Promise.resolve(result),
      Location.hasServicesEnabledAsync(),
    ]);
    const isGranted = permissionResult.status === Location.PermissionStatus.GRANTED;

    setHasLocationPermission(isGranted);
    setIsLocationServiceEnabled(servicesEnabled);
    setIsFollowingUser(isGranted && servicesEnabled);
    setCanAskLocationPermissionAgain(permissionResult.canAskAgain);
    setLocationPermissionState(isGranted ? 'granted' : 'denied');
  }, []);

  const handleLocationPermissionPress = useCallback(() => {
    if (isLocationServiceEnabled === false) {
      void Linking.sendIntent('android.settings.LOCATION_SOURCE_SETTINGS');
      return;
    }

    if (canAskLocationPermissionAgain) {
      void requestLocationPermission();
      return;
    }

    showPermanentlyDeniedAlert('location');
  }, [canAskLocationPermissionAgain, isLocationServiceEnabled, requestLocationPermission]);

  /**
   * 내 위치 버튼. followUserLocation 을 켜면 rnmapbox 가 카메라를 현재 위치에 붙여
   * 둡니다. 다시 누르면 추적을 끄고 지도를 자유롭게 움직일 수 있습니다.
   * 권한이 없으면 이동 대신 권한 안내 흐름을 탑니다.
   */
  const handleLocateMePress = useCallback(() => {
    if (!hasLocationPermission || isLocationServiceEnabled === false) {
      handleLocationPermissionPress();
      return;
    }

    setIsFollowingUser((following) => !following);
  }, [handleLocationPermissionPress, hasLocationPermission, isLocationServiceEnabled]);

  useEffect(() => {
    const requestInitialLocationPermission = async () => {
      const current = await Location.getForegroundPermissionsAsync();
      let result = current;

      if (!current.granted) {
        const shouldRequest = await explainBeforeRequest('location');
        if (shouldRequest) {
          result = await Location.requestForegroundPermissionsAsync();
          if (!result.granted && !result.canAskAgain) {
            showPermanentlyDeniedAlert('location');
          }
        }
      }

      const servicesEnabled = await Location.hasServicesEnabledAsync();
      const isGranted = result.status === Location.PermissionStatus.GRANTED;

      setHasLocationPermission(isGranted);
      setIsLocationServiceEnabled(servicesEnabled);
      setCanAskLocationPermissionAgain(result.canAskAgain);
      setLocationPermissionState(isGranted ? 'granted' : 'denied');

      if (!isGranted || !servicesEnabled) {
        setIsFollowingUser(false);
        return;
      }

      // 권한이 있으면 서울 기본값을 거치지 않고 바로 내 위치로 카메라를 잡습니다.
      // 좌표만 먼저 옮기고(추적은 아직 끔), 다음 렌더에서 pendingFollowActivation
      // effect 가 추적을 켭니다.
      try {
        const lastKnownPosition = await Location.getLastKnownPositionAsync();
        const position =
          lastKnownPosition ??
          (await Location.getCurrentPositionAsync({ accuracy: Location.Accuracy.Balanced }));

        setInitialCameraCoordinate([position.coords.longitude, position.coords.latitude]);
        setInitialCameraZoomLevel(FOLLOW_ZOOM_LEVEL);
        setPendingFollowActivation(true);
      } catch (error) {
        // 좌표를 못 구했으면 서울 기본값에서 추적을 켜서, 기존처럼 followUserLocation 이
        // 알아서 내 위치로 옮겨가도록 둡니다.
        console.warn('초기 위치를 가져오지 못했습니다.', error);
        setIsFollowingUser(true);
      }
    };

    void requestInitialLocationPermission();
  }, []);

  // 좌표가 내 위치로 반영된 바로 다음 렌더에서 추적을 켭니다(위 state 선언부 주석 참고).
  // 일부러 한 렌더 늦게 실행해야 하는 경우라 lint 가 권하는 "effect 없이 계산" 방식으로는
  // 안 되고, 실제로 effect 안에서 setState 를 해야 합니다.
  useEffect(() => {
    if (!pendingFollowActivation) return;

    // eslint-disable-next-line react-hooks/set-state-in-effect
    setPendingFollowActivation(false);
    setIsFollowingUser(true);
  }, [pendingFollowActivation]);

  useEffect(() => {
    const subscription = AppState.addEventListener('change', (nextState) => {
      if (nextState !== 'active') return;

      void Promise.all([
        Location.getForegroundPermissionsAsync(),
        Location.hasServicesEnabledAsync(),
      ]).then(([result, servicesEnabled]) => {
        const isGranted = result.status === Location.PermissionStatus.GRANTED;

        setHasLocationPermission(isGranted);
        setIsLocationServiceEnabled(servicesEnabled);
        setCanAskLocationPermissionAgain(result.canAskAgain);
        setLocationPermissionState(isGranted ? 'granted' : 'denied');
        // 앱에 돌아왔다고 추적을 다시 켜면, 검색해 둔 위치에서 카메라가 내 위치로
        // 튕겨 돌아갑니다. 권한이 사라졌을 때 끄기만 하고 켜지는 않습니다.
        setIsFollowingUser((following) => following && isGranted && servicesEnabled);
      });
    });

    return () => subscription.remove();
  }, []);

  useEffect(
    () => () => {
      cancelViewportQuery();
      if (searchDebounceRef.current) {
        clearTimeout(searchDebounceRef.current);
      }
    },
    [cancelViewportQuery],
  );

  /**
   * 카메라를 움직이는 유일한 통로.
   *
   * followUserLocation 이 켜져 있는 동안 rnmapbox 는 명령형 카메라 이동을 **조용히
   * 버립니다**(Camera.tsx 의 buildNativeStop 이 null 을 반환). 위치 권한을 허용하면
   * 추적이 켜지므로, 그 상태에서 부른 fitBounds·setCamera 는 아무 일도 일어나지
   * 않습니다. setIsFollowingUser(false) 를 먼저 불러도 같은 틱에서는 아직 켜진
   * 상태라 소용이 없어서, 추적이 실제로 꺼진 다음 렌더까지 이동을 미뤄 둡니다.
   *
   * 이동이 실패해도 목록은 이미 받아 온 상태이므로, 예외가 조회 실패로 잡혀
   * '불러오지 못했습니다'가 뜨지 않도록 안에서 삼킵니다.
   */
  const moveCamera = useCallback((move: () => void) => {
    const guarded = () => {
      try {
        move();
      } catch (error) {
        console.warn('카메라를 옮기지 못했습니다.', error);
      }
    };

    if (isFollowingUserRef.current) {
      pendingCameraMoveRef.current = guarded;
      setIsFollowingUser(false);
      return;
    }

    guarded();
  }, []);

  // 추적이 꺼진 뒤 밀어 둔 카메라 이동을 실행합니다.
  useEffect(() => {
    isFollowingUserRef.current = isFollowingUser;
    if (isFollowingUser) return;

    const pendingMove = pendingCameraMoveRef.current;
    if (pendingMove === null) return;

    pendingCameraMoveRef.current = null;
    pendingMove();
  }, [isFollowingUser]);

  /**
   * 2D/3D 전환. 기울기를 주면 fill-extrusion 건물이 입체로 보입니다.
   * 임장 지도와 같은 35도를 씁니다.
   */
  const toggleMapDimension = useCallback(() => {
    const nextIs3dEnabled = !is3dEnabled;
    setIs3dEnabled(nextIs3dEnabled);
    moveCamera(() =>
      cameraRef.current?.setCamera({
        pitch: nextIs3dEnabled ? 35 : 0,
        animationDuration: 450,
        animationMode: 'easeTo',
      }),
    );
  }, [is3dEnabled, moveCamera]);

  /**
   * 나침반. 지도를 돌려 둔 상태에서 누르면 정북(heading=0)으로 되돌립니다. 추적 중이면
   * moveCamera 가 추적을 끄고 이 이동을 미뤄 실행하므로, 추적과 충돌하지 않습니다.
   */
  const resetMapHeading = useCallback(() => {
    moveCamera(() =>
      cameraRef.current?.setCamera({
        heading: 0,
        animationMode: 'flyTo',
        animationDuration: 400,
      }),
    );
  }, [moveCamera]);

  /** 위경도 사각형이 모두 들어오도록 맞춥니다. */
  const fitCameraToBox = useCallback(
    (box: BoundingBox) => {
      moveCamera(() =>
        cameraRef.current?.fitBounds(box.ne, box.sw, CAMERA_FIT_PADDING, 600),
      );
    },
    [moveCamera],
  );

  /**
   * 검색 결과가 모두 들어오도록 카메라를 옮깁니다.
   *
   * 결과에 딱 맞추면 한 블록에 몰려 있을 때(또는 결과가 하나일 때) 과하게 확대돼,
   * 뒤이어 '지도 영역'으로 돌아가도 좁은 범위에 갇힙니다. 그래서 최소 가시 범위를
   * 두어 주변 동네가 함께 보이게 합니다.
   */
  const fitCameraToApartments = useCallback(
    (apartments: ApartmentMapItem[]) => {
      const box = boundingBoxOf(apartments, SEARCH_MIN_VISIBLE_SPAN_METERS);
      if (box === null) return;

      fitCameraToBox(box);
    },
    [fitCameraToBox],
  );

  const applySearchKeyword = useCallback((keyword: string) => {
    searchKeywordRef.current = keyword;
    setSearchKeyword(keyword);
  }, []);

  // 입력 → 조회어 디바운스. 타이머를 effect 가 아니라 입력 핸들러에서 관리해야
  // 렌더 중 setState 연쇄가 생기지 않습니다.
  const handleSearchInputChange = useCallback(
    (text: string) => {
      setSearchInput(text);

      if (searchDebounceRef.current) {
        clearTimeout(searchDebounceRef.current);
      }

      const trimmed = text.trim();
      // 한글 조합 중간 상태('ㄱ', 'ㅅㅏ')는 서버 검증에서 400 이 나므로 보내지 않습니다.
      const nextKeyword = SEARCHABLE_PATTERN.test(trimmed) ? trimmed : '';
      if (nextKeyword === searchKeywordRef.current) return;

      // 지우는 경우는 디바운스 없이 즉시 반영합니다. '내 주변'이 켜져 있으면 목록이
      // 비는 게 아니라 반경 전체 결과로 다시 조회되므로 로딩만 걸어 둡니다.
      if (nextKeyword === '') {
        applySearchKeyword('');
        if (listScope === 'nearby') {
          setSearchState('loading');
        } else {
          setSearchResults([]);
          setSearchMapItems([]);
          setSearchTotalCount(0);
          setSearchErrorMessage(null);
          setSearchState('success');
        }
        return;
      }

      // 요청은 디바운스 뒤에 나가지만, 타이핑 즉시 진행 중임을 보여 줍니다.
      setSearchState('loading');
      searchDebounceRef.current = setTimeout(
        () => applySearchKeyword(nextKeyword),
        SEARCH_DEBOUNCE_MS,
      );
    },
    [applySearchKeyword, listScope],
  );

  const clearSearch = useCallback(() => {
    handleSearchInputChange('');
    Keyboard.dismiss();
  }, [handleSearchInputChange]);

  const handleSearchFocus = useCallback(() => {
    isSearchFocusedRef.current = true;
    setIsSearchFocused(true);
    sheetIndexBeforeSearchFocusRef.current = currentSheetIndexRef.current;
    // 검색 결과가 키보드 뒤에 가려지지 않도록 입력 중에는 사용 가능한 높이를 모두 씁니다.
    sheetRef.current?.snapToIndex(SHEET_EXPANDED_INDEX);
  }, []);

  const handleSearchBlur = useCallback(() => {
    isSearchFocusedRef.current = false;
    setIsSearchFocused(false);
    const previousIndex = sheetIndexBeforeSearchFocusRef.current;
    // 검색 결과 모드라면 기존 정책인 35%보다 더 낮게 접히지 않도록 유지합니다.
    const nextIndex = isRemoteList ? Math.max(previousIndex, SHEET_RESULT_INDEX) : previousIndex;
    sheetRef.current?.snapToIndex(nextIndex);
  }, [isRemoteList]);

  useEffect(() => {
    if (searchKeyword.length === 0 && listScope !== 'nearby') return;
    // '내 주변'을 켠 직후에는 아직 위치를 못 받았을 수 있습니다. 좌표가 들어오면
    // 이 effect 가 다시 돌면서 조회합니다.
    if (listScope === 'nearby' && nearbyOrigin === null) return;

    const controller = new AbortController();

    const runSearch = async () => {
      try {
        // 위경도를 보내면 서버가 반경 안으로 결과를 제한하고 거리순으로 정렬합니다.
        // '지도 영역' 범위에서 검색할 때는 멀리 있는 단지도 나와야 하므로 안 보냅니다.
        const result = await searchApartments({
          keyword: searchKeyword.length > 0 ? searchKeyword : undefined,
          latitude: nearbyOrigin && listScope === 'nearby' ? nearbyOrigin.latitude : undefined,
          longitude: nearbyOrigin && listScope === 'nearby' ? nearbyOrigin.longitude : undefined,
          radiusMeters: listScope === 'nearby' ? NEARBY_RADIUS_METERS : undefined,
          size: 30,
          signal: controller.signal,
        });
        if (controller.signal.aborted) return;

        const rows = result.content.map(toListRowFromSearchItem);
        const markers = toMapItemsFromSearchItems(result.content);
        setSearchResults(rows);
        setSearchMapItems(markers);
        setSearchTotalCount(result.totalElements);
        setNearbyLocationName(shortLocationName(result.currentLocation?.locationName ?? null));
        setSearchState(rows.length > 0 ? 'success' : 'empty');

        // '내 주변'은 칩을 누른 순간 이미 내 위치 반경으로 옮겨 뒀습니다. 여기서 결과에
        // 다시 맞추면 반경 안에 단지가 한쪽으로 쏠렸을 때 내가 화면 밖으로 밀려납니다.
        // 검색어 검색은 결과가 어디 있는지 모르므로(목동에서 '역삼' 검색) 옮겨 줍니다.
        if (listScope !== 'nearby') {
          fitCameraToApartments(markers);
        }
      } catch (error) {
        if (controller.signal.aborted) return;
        console.warn('아파트를 검색하지 못했습니다.', error);
        setSearchErrorMessage(
          error instanceof Error && error.message.length > 0 ? error.message : null,
        );
        setSearchState('error');
      }
    };

    void runSearch();

    return () => controller.abort();
  }, [fitCameraToApartments, listScope, nearbyOrigin, searchKeyword]);

  // 검색·내 주변으로 전환하면 결과가 가려지지 않게 시트를 펼칩니다.
  useEffect(() => {
    if (!isRemoteList) return;
    // 단, 검색어를 입력하는 중이라면 handleSearchFocus 가 이미 최대 높이로 펼쳐 뒀습니다.
    // 여기서 35% 로 되돌리면 키보드 위에 남은 창에서는 헤더만 보여, 정작 타이핑하면서
    // 확인해야 할 검색 결과가 사라집니다.
    if (isSearchFocusedRef.current) return;
    sheetRef.current?.snapToIndex(SHEET_RESULT_INDEX);
  }, [isRemoteList]);

  // 키보드가 올라와 시트 창이 줄어들면 스냅 높이가 다시 계산됩니다. 입력 중에는 그 새 높이
  // 기준으로도 최대까지 펼쳐 두어야 결과가 키보드 위에 그대로 남습니다.
  useEffect(() => {
    if (keyboardHeight === 0) return;
    if (!isSearchFocusedRef.current) return;
    sheetRef.current?.snapToIndex(SHEET_EXPANDED_INDEX);
  }, [keyboardHeight]);

  const scheduleViewportQuery = useCallback(
    (bounds: ViewportBounds) => {
      const { ne, sw } = bounds;
      const [neLongitude, neLatitude] = ne;
      const [swLongitude, swLatitude] = sw;
      if (
        neLongitude === undefined ||
        neLatitude === undefined ||
        swLongitude === undefined ||
        swLatitude === undefined
      ) {
        cancelViewportQuery();
        lastBoundsSignatureRef.current = null;
        return false;
      }

      // onMapIdle 은 카메라가 미세하게 흔들리기만 해도 반복 발생합니다. 그때마다
      // 디바운스 타이머를 다시 걸면 타이머가 영원히 리셋되어 요청이 한 번도 나가지
      // 않습니다(실제로 목록이 '불러오는 중'에서 멈추는 증상). 이미 요청한 경계와
      // 사실상 같은 값이면 무시합니다.
      const signature = [swLongitude, swLatitude, neLongitude, neLatitude]
        .map((value) => value.toFixed(4))
        .join(',');
      if (signature === lastBoundsSignatureRef.current) {
        return true;
      }
      lastBoundsSignatureRef.current = signature;

      cancelViewportQuery();
      const requestId = ++viewportRequestIdRef.current;
      setViewportState('loading');
      setViewportErrorMessage(null);

      viewportDebounceRef.current = setTimeout(() => {
        viewportDebounceRef.current = null;
        const controller = new AbortController();
        viewportAbortControllerRef.current = controller;

        const loadApartments = async () => {
          try {
            const response = await getApartmentsInBounds(
              {
                southWestLat: swLatitude,
                southWestLng: swLongitude,
                northEastLat: neLatitude,
                northEastLng: neLongitude,
              },
              controller.signal,
            );
            if (controller.signal.aborted || requestId !== viewportRequestIdRef.current) return;

            const deduplicated = deduplicateApartments(response.map(toApartmentMapItem));
            setVisibleApartments(deduplicated);
            setViewportState(deduplicated.length > 0 ? 'success' : 'empty');
          } catch (error) {
            if (controller.signal.aborted || requestId !== viewportRequestIdRef.current) return;
            console.warn('지도 영역의 아파트를 불러오지 못했습니다.', error);
            // 서버 메시지를 그대로 보여 줍니다. 조회 영역이 너무 넓을 때처럼
            // (APARTMENT_BOUNDS_TOO_LARGE) 사용자가 할 수 있는 행동이 담겨 있습니다.
            setViewportErrorMessage(
              error instanceof Error && error.message.length > 0 ? error.message : null,
            );
            setViewportState('error');
          } finally {
            if (viewportAbortControllerRef.current === controller) {
              viewportAbortControllerRef.current = null;
            }
          }
        };

        void loadApartments();
      }, VIEWPORT_DEBOUNCE_MS);
      return true;
    },
    // authenticatedFetch 가 스토어에서 토큰을 직접 읽으므로 여기서 토큰에 의존하지 않습니다.
    [cancelViewportQuery],
  );

  useEffect(() => {
    if (isRemoteList) {
      cancelViewportQuery();
      lastBoundsSignatureRef.current = null;
      return;
    }

    if (mapMode === 'district') {
      cancelViewportQuery();
      lastBoundsSignatureRef.current = null;
      return;
    }

    const latestBounds = latestViewportBoundsRef.current;
    if (latestBounds) {
      scheduleViewportQuery(latestBounds);
    }
  }, [
    cancelViewportQuery,
    districtSummaryQuery.isError,
    isRemoteList,
    mapMode,
    scheduleViewportQuery,
  ]);

  useEffect(() => {
    if (!districtSummaryQuery.isError) return;
    console.warn(
      '자치구별 아파트 집계를 불러오지 못했습니다.',
      districtSummaryQuery.error,
    );
  }, [districtSummaryQuery.error, districtSummaryQuery.isError]);

  const handleMapIdle = useCallback(
    (bounds: ViewportBounds, zoom: number) => {
      // 위치 권한 확인 전 고정 좌표로 발생한 첫 idle 은 버립니다. 권한이 있으면
      // 사용자 위치로 이동한 뒤 다시 idle 이 발생하고, 없으면 아래 보정 effect 가
      // 현재 화면의 bounds 를 전달합니다.
      if (locationPermissionState === 'checking') return;

      latestMapZoomRef.current = zoom;

      const latestBounds = {
        ne: [...bounds.ne],
        sw: [...bounds.sw],
      };
      latestViewportBoundsRef.current = latestBounds;

      const nextZoomMode: MapMode =
        zoom < DISTRICT_MODE_MAX_ZOOM ? 'district' : 'apartment';
      setZoomMode(nextZoomMode);

      if (isRemoteList) {
        hasResolvedInitialViewportRef.current = true;
        return;
      }

      if (zoom < DISTRICT_MODE_MAX_ZOOM && !districtSummaryQuery.isError) {
        cancelViewportQuery();
        lastBoundsSignatureRef.current = null;
        hasResolvedInitialViewportRef.current = true;
        return;
      }

      if (scheduleViewportQuery(latestBounds)) {
        hasResolvedInitialViewportRef.current = true;
      }
    },
    [
      cancelViewportQuery,
      districtSummaryQuery.isError,
      isRemoteList,
      locationPermissionState,
      scheduleViewportQuery,
    ],
  );

  /**
   * 위치 권한 다이얼로그가 닫힌 직후 Mapbox가 아직 경계를 반환하지 못하는 경우까지
   * 보정합니다. 사용자 위치 추적 중에는 자연스러운 idle을 먼저 기다리고,
   * 지연되거나 경계가 아직 준비되지 않았으면 재시도합니다.
   */
  useEffect(() => {
    if (locationPermissionState === 'checking') return;
    if (hasResolvedInitialViewportRef.current) return;
    let cancelled = false;
    let attemptCount = 0;
    let retryTimer: ReturnType<typeof setTimeout> | null = null;

    const resolveFixedInitialViewport = async () => {
      if (cancelled || hasResolvedInitialViewportRef.current) return;
      attemptCount += 1;

      try {
        const bounds = await mapViewRef.current?.getVisibleBounds();
        const ne = bounds?.[0];
        const sw = bounds?.[1];

        if (cancelled || hasResolvedInitialViewportRef.current) return;
        if (!ne || !sw) {
          if (attemptCount < INITIAL_VIEWPORT_MAX_ATTEMPTS) {
            retryTimer = setTimeout(
              () => void resolveFixedInitialViewport(),
              INITIAL_VIEWPORT_RETRY_MS,
            );
          }
          return;
        }

        handleMapIdle({ ne, sw }, latestMapZoomRef.current);
      } catch (error) {
        if (cancelled) return;
        if (attemptCount < INITIAL_VIEWPORT_MAX_ATTEMPTS) {
          retryTimer = setTimeout(
            () => void resolveFixedInitialViewport(),
            INITIAL_VIEWPORT_RETRY_MS,
          );
          return;
        }
        console.warn('초기 지도 영역을 가져오지 못했습니다.', error);
      }
    };

    retryTimer = setTimeout(
      () => void resolveFixedInitialViewport(),
      isFollowingUser ? INITIAL_VIEWPORT_FOLLOW_FALLBACK_MS : 0,
    );

    return () => {
      cancelled = true;
      if (retryTimer) clearTimeout(retryTimer);
    };
  }, [handleMapIdle, isFollowingUser, locationPermissionState]);

  const handleApartmentSourcePress = useCallback(
    async (feature: PressedMapFeature | undefined) => {
      const properties = feature?.properties;
      const coordinates = feature?.geometry?.coordinates;

      if (!properties || !coordinates || coordinates.length < 2) return;
      const [longitude, latitude] = coordinates;
      if (longitude === undefined || latitude === undefined) return;

      if (properties.cluster && typeof properties.cluster_id === 'number') {
        try {
          const zoom = await apartmentSourceRef.current?.getClusterExpansionZoom(feature);

          if (typeof zoom === 'number') {
            cameraRef.current?.setCamera({
              centerCoordinate: [longitude, latitude],
              zoomLevel: zoom,
              animationMode: 'flyTo',
              animationDuration: 500,
            });
          }
        } catch (error) {
          console.warn('클러스터를 확대하지 못했습니다.', error);
        }
        return;
      }

      // 마커 탭은 바로 상세로 보냅니다. 예전에는 selectApartment 로 카메라만 옮기고
      // 시트에 선택 상태를 남겼는데, 시트가 접혀 있으면 아무 반응 없는 것처럼 보였습니다.
      // 바텀시트 목록 행(handleApartmentRowPress)과 동작을 맞춥니다.
      const apartmentId = Number(properties.apartmentId);
      if (Number.isFinite(apartmentId)) router.push(`/(app)/apartment/${apartmentId}`);
    },
    [router],
  );

  const handleDistrictCardPress = useCallback(
    (card: DistrictCard) => {
      moveCamera(() =>
        cameraRef.current?.setCamera({
          centerCoordinate: [card.longitude, card.latitude],
          zoomLevel: 12.5,
          animationMode: 'flyTo',
          animationDuration: 700,
        }),
      );
    },
    [moveCamera],
  );

  const viewportRows = useMemo(
    () => visibleApartments.map(toListRowFromMapItem),
    [visibleApartments],
  );

  // 검색어나 '내 주변'이 켜져 있으면 목록이 검색 API 결과를 보여 줍니다. 검색 응답에는
  // 아직 좌표가 없어서 마커는 그대로 두고 목록만 바꿉니다(카드 → 상세 이동은 동일).
  const listRows = isRemoteList ? searchResults : viewportRows;
  const listState: ViewportState | SearchState = isRemoteList ? searchState : viewportState;
  const listErrorMessage = isRemoteList ? searchErrorMessage : viewportErrorMessage;

  // 시트 부제목. 제목 아래에 붙으므로 상태만 짧게 알려 줍니다.
  // 예) '옥수동 반경 1km · 12개 단지', "'래미안' 34개 단지", '현재 영역 12개 단지'
  const nearbyPrefix = `${nearbyLocationName ?? '내 주변'} 반경 ${NEARBY_RADIUS_METERS / 1000}km`;
  const countLabel = isSearching
    ? `'${searchKeyword}' ${searchTotalCount}개 단지`
    : `${searchTotalCount}개 단지`;
  const isDistrictZoom = !isRemoteList && zoomMode === 'district';

  const handleRowPress = useCallback(
    (apartmentId: number) => router.push(`/(app)/apartment/${apartmentId}`),
    [router],
  );

  const selectedApartmentId = selectedApartment?.id;
  const renderApartmentRow = useCallback<ListRenderItem<ApartmentListRow>>(
    ({ item }: { item: ApartmentListRow }) => (
      <ApartmentRowItem
        apartment={item}
        isSelected={selectedApartmentId === item.id}
        onPress={handleRowPress}
      />
    ),
    [handleRowPress, selectedApartmentId],
  );

  // 로딩·에러·빈 상태 문구. 세 상태 중 하나면 목록 대신 이 문구를 보여 줍니다
  // (BottomSheetFlatList 의 ListEmptyComponent).
  const listPlaceholder = useMemo(() => {
    if (isDistrictMode) {
      return (
        <Text style={styles.sheetPlaceholder}>
          지도를 확대하면 단지 목록이 보입니다
        </Text>
      );
    }
    if (listState === 'loading' && listRows.length === 0) {
      return <ActivityIndicator color={PRIMARY_COLOR} style={styles.sheetLoader} />;
    }
    if (listState === 'error') {
      return (
        <Text style={styles.sheetPlaceholder}>
          {listErrorMessage ??
            (isRemoteList
              ? '아파트를 불러오지 못했습니다. 잠시 후 다시 시도해 주세요.'
              : '아파트를 불러오지 못했습니다. 지도를 다시 움직여 주세요.')}
        </Text>
      );
    }
    if (listRows.length === 0) {
      return (
        <Text style={styles.sheetPlaceholder}>
          {isSearching
            ? `'${searchKeyword}'와 일치하는 아파트가 없습니다. 다른 검색어를 입력해 보세요.`
            : isNearby
              ? `${nearbyPrefix} 안에 등록된 아파트가 없습니다. '지도 영역'으로 넓혀 보세요.`
              : '현재 영역에 표시할 아파트가 없습니다. 지도를 옮기거나 확대해 보세요.'}
        </Text>
      );
    }
    return null;
  }, [
    isNearby,
    isDistrictMode,
    isRemoteList,
    isSearching,
    listErrorMessage,
    listRows.length,
    listState,
    nearbyPrefix,
    searchKeyword,
  ]);

  const listData = isDistrictMode || listPlaceholder ? EMPTY_LIST_ROWS : listRows;

  const sheetSubtitle = isDistrictZoom
    ? districtSummaryQuery.isLoading
      ? '불러오는 중...'
      : districtSummaryQuery.isError || !districtSummaryQuery.data
        ? '불러오지 못했습니다'
        : `서울 전체 ${districtSummaryQuery.data.totalApartmentCount.toLocaleString('ko-KR')}개 단지`
    : isRemoteList
      ? searchState === 'loading'
      ? isSearching
        ? '검색 중...'
        : '내 주변을 찾는 중...'
      : searchState === 'error'
        ? '불러오지 못했습니다'
        : searchState === 'empty'
          ? isNearby
            ? `${nearbyPrefix} · 단지가 없습니다`
            : '검색 결과가 없습니다'
          : isNearby
            ? `${nearbyPrefix} · ${countLabel}`
            : countLabel
      : viewportState === 'loading'
        ? '불러오는 중...'
        : viewportState === 'empty'
          ? '현재 영역에 단지가 없습니다'
          : viewportState === 'error'
            ? '불러오지 못했습니다'
            : `현재 영역 ${visibleApartments.length}개 단지`;

  return (
    <View style={styles.container}>
      <Mapbox.MapView
        ref={mapViewRef}
        style={styles.map}
        // 임장 지도(field/[sessionId].tsx)와 같은 커스텀 스타일 재사용 — 한글 라벨
        // 우선순위 + 3D 건물. 이 지도는 pitch 가 항상 0 이라 건물이 압출돼 있어도
        // 위에서 내려다보는 모양은 평면 폴리곤과 시각적으로 동일합니다.
        styleJSON={MAP_STYLE_JSON}
        scaleBarEnabled={false}
        zoomEnabled
        scrollEnabled
        rotateEnabled
        pitchEnabled={false}
        onMapIdle={(state) => {
          handleMapIdle(state.properties.bounds, state.properties.zoom);
          // 회전이 멈춘 시점의 방위를 [0,360) 로 정규화해 나침반 표시/회전에 씁니다.
          setMapHeading(((state.properties.heading % 360) + 360) % 360);
        }}
        // onMapIdle 은 새 아키텍처(Fabric)에서 팬·줌 뒤에 항상 발화하지는 않아, 초기 진입
        // 이후 가시영역 재조회가 걸리지 않던 문제가 있었다(지도를 움직여도 처음 화면의
        // 단지만 남고 갱신 안 됨). 카메라가 실제로 움직일 때 발화하는 onCameraChanged 로도
        // 재조회를 건다. bounds·zoom payload 형태가 onMapIdle 과 같고, scheduleViewportQuery
        // 의 debounce·서명 dedup 이 연속 이벤트를 카메라가 멈춘 뒤 한 번의 요청으로 합친다.
        onCameraChanged={(state) => {
          handleMapIdle(state.properties.bounds, state.properties.zoom);
        }}
      >
        <Mapbox.Camera
          ref={cameraRef}
          centerCoordinate={initialCameraCoordinate}
          zoomLevel={initialCameraZoomLevel}
          minZoomLevel={MAP_TAB_MIN_ZOOM_LEVEL}
          maxBounds={MAP_TAB_CAMERA_BOUNDS}
          pitch={0}
          // heading 은 선언적으로 고정하지 않습니다. 여기에 heading={0} 을 두면 rnmapbox 가
          // 카메라 방위를 계속 0 으로 되돌려, rotateEnabled 로 돌려도 onMapIdle 이 항상
          // heading≈0 을 보고합니다(나침반이 돌지 않던 원인). 정북 복귀는 나침반 버튼의
          // resetMapHeading 이 명령형(setCamera)으로 처리합니다.
          animationMode="none"
          followUserLocation={isFollowingUser}
          followUserMode={Mapbox.UserTrackingMode.Follow}
          followZoomLevel={FOLLOW_ZOOM_LEVEL}
          followPitch={0}
        />

        {hasLocationPermission && isLocationServiceEnabled && (
          // puckBearing="heading": 기기 나침반이 바라보는 방향으로 퍽에 방향 빔이 나옵니다.
          // 실제 방향은 기기 나침반(자기 센서)에 의존해, 에뮬레이터에서는 고정돼 밋밋할 수
          // 있습니다. sonar 형 pulsing 과 약간의 scale 로 현재 위치·방향이 눈에 띄게만 합니다.
          <Mapbox.LocationPuck
            puckBearingEnabled
            puckBearing="heading"
            scale={1.1}
            pulsing={{ isEnabled: true, color: PRIMARY_COLOR, radius: 'accuracy' }}
          />
        )}

        {/*
          스타일이 이미 갖고 있는 시설 라벨에 filter 만 덮어씌웁니다(임장 지도와 동일).
          아무 카테고리도 안 고르면 5개(교통·학군·의료·생활·환경) 전부, 하나라도 고르면
          그 카테고리만 보여 줍니다(더해지지 않고 교체). 음식점·카페는 '전체 시설 보기'를
          켜야 나옵니다.
        */}
        {/* 우리가 추적하는 카테고리(학교·병원 등)는 Mapbox의 sizerank 기반 랜드마크
            처리로 글자가 아이콘 자리에 겹쳐 아이콘이 안 보이던 문제를 고칩니다 —
            텍스트를 항상 아이콘 아래로 내립니다(facilityFilters.ts 참고). */}
        <Mapbox.SymbolLayer
          existing
          filter={poiFilter}
          id="poi-label"
          style={{
            textAnchor: POI_LABEL_STYLE_OVERRIDE.textAnchor,
            textOffset: POI_LABEL_STYLE_OVERRIDE.textOffset,
            visibility: layerVisibility,
          }}
        />
        <Mapbox.SymbolLayer
          existing
          filter={transitFilter}
          id="transit-label"
          style={{ visibility: layerVisibility }}
        />

        <Mapbox.Images
          images={{
            'apartment-price-marker': {
              image: APARTMENT_PRICE_MARKER_IMAGE,
            },
          }}
        />

        {isDistrictMode &&
          districtCards.map((card) => (
            <Mapbox.MarkerView
              key={card.districtCode}
              coordinate={[card.longitude, card.latitude]}
              anchor={{ x: 0.5, y: 0.5 }}
              allowOverlap
            >
              <DistrictCountCard
                districtName={card.districtName}
                apartmentCount={card.apartmentCount}
                onPress={() => handleDistrictCardPress(card)}
              />
            </Mapbox.MarkerView>
          ))}

        <Mapbox.ShapeSource
          ref={apartmentSourceRef}
          id="visible-apartments-source"
          shape={apartmentFeatureCollection}
          cluster
          // 55px·15 는 "화면 가로가 740m 보다 좁아져야 낱개 마커가 나오는" 설정이라
          // 확대해도 클러스터만 보인다는 제보가 있었습니다(진입 줌 13.5 는 화면 가로 2.1km).
          // 13 이면 화면 가로 약 3km 부터 낱개라 앱을 켜자마자 단지가 보입니다.
          // 55px 는 줌 14 에서 208m·줌 13 에서 417m 라 서울 단지 간격(100~300m)을 거의 다 삼킵니다.
          clusterRadius={40}
          clusterMaxZoomLevel={13}
          onPress={(event) => {
            void handleApartmentSourcePress(event.features[0] as PressedMapFeature | undefined);
          }}
        >
          <Mapbox.CircleLayer
            id="apartment-cluster-circle"
            filter={['has', 'point_count']}
            style={{
              circleColor: '#D5F7D8',
              circleOpacity: 0.70,
              circleRadius: [
                'interpolate',
                ['linear'],
                ['zoom'],
                8,
                ['step', ['get', 'point_count'], 30, 25, 38, 100, 48],
                13,
                ['step', ['get', 'point_count'], 26, 25, 34, 100, 42],
                16,
                ['step', ['get', 'point_count'], 22, 25, 29, 100, 36],
              ],
              circleStrokeWidth: 0,
            }}
          />
          <Mapbox.SymbolLayer
            id="apartment-cluster-count"
            filter={['has', 'point_count']}
            style={{
              textField: ['get', 'point_count_abbreviated'],
              textSize: ['interpolate', ['linear'], ['zoom'], 8, 23, 16, 21],
              textColor: '#1F5F55',
              textFont: ['DIN Pro Medium', 'Arial Unicode MS Regular'],
              textAllowOverlap: true,
              textIgnorePlacement: true,
            }}
          />
          <Mapbox.SymbolLayer
            id="apartment-price-label"
            filter={['!', ['has', 'point_count']]}
            style={{
              iconImage: 'apartment-price-marker',
              // 렌더 높이 약 77dp. 글자를 얹을 수 있는 곳은 '몸통'(진한 초록 사각형)인
              // -19 ~ -39dp 구간 20dp 뿐입니다. 지붕은 채워진 삼각형이 아니라 선이라
              // 그 위(-39dp 보다 위)에 글자를 두면 배경이 비쳐 집 밖으로 삐져나와 보입니다.
              // 아래 두 라벨의 textSize·textOffset 은 전부 이 20dp 안에 맞춘 값입니다.
              iconSize: 0.15,
              iconAnchor: 'bottom',
              iconPitchAlignment: 'viewport',
              iconRotationAlignment: 'viewport',
              // 겹치면 마커 자체를 통째로 숨깁니다. allowOverlap 을 끄면 이미 놓인 심볼과
              // 겹치는 아이콘이 배치되지 않고, textOptional 을 주지 않았으므로 글자가
              // 안 들어가는 경우에도 아이콘까지 함께 빠집니다. 앞(화면 아래쪽) 마커가 먼저
              // 배치되므로 살아남는 건 항상 앞엣것입니다.
              iconAllowOverlap: false,
              iconIgnorePlacement: false,
              // 면적과 가격을 '한 심볼'의 두 줄로 넣습니다. 예전에는 면적을 별도 SymbolLayer 로
              // 뒀는데, symbolZOrder 는 '같은 레이어 안'의 심볼끼리만 정렬하므로 뒤쪽 마커의
              // 면적 글자가 앞쪽 마커 아이콘 위로 항상 덮어 그려졌습니다. 한 레이어로 합치면
              // 아이콘·글자가 심볼 단위로 함께 정렬돼 뒤쪽 글자가 앞쪽 집에 가려집니다.
              //
              // 거래가 없는 단지는 areaLabel 이 빈 문자열이라, 그대로 두면 윗줄이 비어
              // 대시(–)만 아래로 처집니다. 그때는 한 줄짜리로 분기합니다.
              textField: [
                'case',
                ['==', ['get', 'areaLabel'], ''],
                ['format', ['get', 'priceLabel'], {}],
                [
                  'format',
                  ['get', 'areaLabel'],
                  { 'font-scale': 0.8333, 'text-color': '#C6F9D2' },
                  '\n',
                  {},
                  ['get', 'priceLabel'],
                  {},
                ],
              ],
              // 12 * 0.8333 = 10dp 가 면적 줄의 실제 크기입니다.
              textSize: 12,
              textColor: '#FFFFFF',
              textFont: ['DIN Pro Medium', 'Arial Unicode MS Regular'],
              textLetterSpacing: 0,
              textHaloWidth: 0,
              // 기본 1.2 면 두 줄이 28.8dp 라 20dp 짜리 몸통을 넘칩니다.
              textLineHeight: 0.9,
              // PNG 의 투명 여백을 잘라내(512x512 → 347x363) 앵커가 캔버스 바닥에서 집 꼬리 끝으로
              // 올라왔습니다. 그만큼(약 8.6dp) 글자도 같이 내려야 집 안에 그대로 남습니다.
              textOffset: [0, -2.07],
              // Mapbox 는 한 레이어 안에서도 '아이콘 전부 → 텍스트 전부' 순서로 그립니다.
              // symbolZOrder 로는 뒤쪽 글자가 앞쪽 집을 덮는 걸 못 막으므로, 겹치는 마커는
              // 글자와 아이콘을 함께 배치에서 뺍니다. textOptional 을 주지 않는 게 핵심입니다 —
              // 주면 글자만 빠지고 '빈 집'이 남습니다.
              textAllowOverlap: false,
              textIgnorePlacement: false,
              // 화면 아래쪽(= 보는 사람에게 가까운) 마커가 먼저 배치돼 살아남습니다.
              symbolZOrder: 'viewport-y',
            }}
          />
        </Mapbox.ShapeSource>
      </Mapbox.MapView>

      {/*
        FE-040 — 검색바 + 범위 칩. 지도 위에 떠 있는 한 덩어리라 위치 안내 카드까지
        같은 컬럼에 넣어 서로 겹치지 않게 합니다. box-none 이라 칩·카드 사이 빈 공간은
        지도로 터치가 통과합니다.
      */}
      <View
        pointerEvents="box-none"
        style={[styles.topOverlay, { top: insets.top + TOP_OVERLAY_TOP_GAP }]}
      >
        <View style={styles.searchBar}>
          <Ionicons color={MUTED_TEXT_COLOR} name="search" size={18} />
          <TextInput
            accessibilityLabel="아파트 검색"
            autoCorrect={false}
            onBlur={handleSearchBlur}
            onChangeText={handleSearchInputChange}
            onFocus={handleSearchFocus}
            placeholder="아파트명, 동 이름으로 검색"
            placeholderTextColor={MUTED_TEXT_COLOR}
            returnKeyType="search"
            style={styles.searchInput}
            value={searchInput}
          />
          {isRemoteList && searchState === 'loading' && (
            <ActivityIndicator color={PRIMARY_COLOR} size="small" />
          )}
          {searchInput.length > 0 && (
            <Pressable
              accessibilityLabel="검색어 지우기"
              accessibilityRole="button"
              hitSlop={8}
              onPress={clearSearch}
              style={({ pressed }) => pressed && styles.pressed}
            >
              <Ionicons color={MUTED_TEXT_COLOR} name="close-circle" size={18} />
            </Pressable>
          )}
        </View>


        {(locationPermissionState === 'denied' || isLocationServiceEnabled === false) && (
          <View style={styles.locationPermissionCard}>
            <View style={styles.locationPermissionCopy}>
              <Text style={styles.locationPermissionTitle}>
                {isLocationServiceEnabled === false
                  ? '휴대폰 위치 기능이 꺼져 있어요'
                  : '현재 위치를 표시할 수 없어요'}
              </Text>
              <Text style={styles.locationPermissionDescription}>
                {isLocationServiceEnabled === false
                  ? '내 위치와 주변 아파트를 보려면 휴대폰 위치 기능을 켜 주세요.'
                  : '내 위치와 주변 아파트를 보려면 위치 권한을 허용해 주세요.'}
              </Text>
            </View>
            <Pressable
              accessibilityRole="button"
              onPress={handleLocationPermissionPress}
              style={({ pressed }) => [
                styles.locationPermissionButton,
                pressed && styles.locationPermissionButtonPressed,
              ]}
            >
              <Text style={styles.locationPermissionButtonText}>
                {isLocationServiceEnabled === false
                  ? '위치 켜기'
                  : canAskLocationPermissionAgain
                    ? '다시 요청'
                    : '설정 열기'}
              </Text>
            </Pressable>
          </View>
        )}
      </View>

      {/*
        FE-007 — 뷰포트 안의 아파트 목록. 지도 마커와 같은 visibleApartments 를 쓰므로
        마커 선택·목록 선택이 같은 항목을 가리킵니다(선택 항목은 강조 표시).

        손으로 끌어 높이를 조절할 수 있게 @gorhom/bottom-sheet 를 씁니다. 시트 드래그와
        내부 목록 스크롤이 서로 간섭하지 않게 하는 처리가 까다로워서 BottomSheetFlatList 를
        사용합니다. 긴 목록은 화면 주변 행만 렌더링합니다.
      */}
      {/*
        지도 컨트롤. 검색바 바로 아래 오른쪽에 세로로 둡니다. 임장 지도와 같은
        구성·순서(2D/3D · 내 위치 · 시설 필터)이고 같은 버튼 컴포넌트를 씁니다.
        임장 지도에만 있는 스터디 채팅은 이 묶음 맨 아래에 붙습니다.
      */}
      <View
        pointerEvents="box-none"
        style={[styles.mapControls, { top: insets.top + MAP_CONTROLS_TOP }]}
      >
        {/* 2D/3D·내 위치는 라벨/접근성 문구로 상태를 알리므로, 눌러서 켜진 상태로 남는
            색 강조는 주지 않습니다(active 미전달). */}
        <GlassIconButton
          accessibilityLabel={is3dEnabled ? '2D 지도로 전환' : '3D 지도로 전환'}
          onPress={toggleMapDimension}
        >
          <Text style={styles.dimensionLabel}>{is3dEnabled ? '3D' : '2D'}</Text>
        </GlassIconButton>

        <GlassIconButton
          accessibilityLabel={isFollowingUser ? '위치 추적 끄기' : '내 위치로 이동'}
          onPress={handleLocateMePress}
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

        {/* 나침반은 항상 띄웁니다. 아이콘을 현재 방위의 반대로 돌려 바늘이 실제 정북을
            가리키게 하고, 누르면 지도를 정북으로 되돌립니다(정북이면 눌러도 제자리). */}
        <GlassIconButton accessibilityLabel="지도 방향 초기화" onPress={resetMapHeading}>
          <Ionicons
            color={CONTROL_ICON_COLOR}
            name="compass"
            size={GLASS_ICON_BUTTON_ICON_SIZE}
            style={{ transform: [{ rotate: `${-mapHeading}deg` }] }}
          />
        </GlassIconButton>
      </View>

      {isFilterOpen && (
        <FacilityFilterPanel
          expandedCategories={expandedCategories}
          onChangeShowAllFacilities={setShowAllFacilities}
          onChangeShowCoreFacilities={setShowCoreFacilities}
          onClose={() => setIsFilterOpen(false)}
          onToggleCategory={toggleFacilityCategory}
          showAllFacilities={showAllFacilities}
          showCoreFacilities={showCoreFacilities}
          style={{ ...styles.filterPanel, top: insets.top + MAP_CONTROLS_TOP }}
        />
      )}

      {/*
        시트를 담는 창(mask). 시트가 놓일 수 있는 영역을 우리가 직접 정하고
        overflow: hidden + borderRadius 로 잘라 냅니다.

        이렇게 하는 이유: @gorhom/bottom-sheet 는 시트 카드의 높이를 '가장 높은
        스냅 높이'로 고정해 두고 통째로 위아래로 밀기만 합니다. 그래서 시트를
        내리면 둥근 바닥이 이 창 아래(탭 바 뒤)로 빠져 각지게 잘려 보였습니다.
        창 쪽에서 잘라 내면 시트를 어느 높이로 내리든 바닥이 항상 이 창의 둥근
        아래 모서리에서 끝나므로, 탭 바 위 간격과 둥근 모양이 그대로 유지됩니다.
      */}
      <View
        pointerEvents="box-none"
        style={[
          styles.sheetWindow,
          {
            // 창 위쪽은 검색바 블록 바로 아래. 시트를 아무리 끌어올려도 이 선을
            // 넘지 못하므로 검색바·칩이 가려질 일이 없습니다.
            top: insets.top + TOP_OVERLAY_TOP_GAP + TOP_OVERLAY_HEIGHT + SHEET_TOP_GAP,
            // 키보드가 올라오면 그 위에서 창이 끝납니다. BottomSheet 가 이 높이 변화를
            // onLayout 으로 받아야 스냅 높이를 다시 계산하므로 애니메이션 스타일이 아니라
            // 일반 레이아웃 값으로 넘깁니다.
            bottom: sheetWindowBottom,
          },
        ]}
      >
        <BottomSheet
          ref={sheetRef}
          animatedIndex={sheetAnimatedIndex}
          // 접힘(헤더만) → 35% → 65% → 창 전체. 끌다 놓으면 가장 가까운 단계로 붙습니다.
          snapPoints={SHEET_SNAP_POINTS}
          index={0}
          // 첫 마운트 때 슬라이드 인 애니메이션이 지도(무거운 네이티브 뷰)·안전영역 인셋
          // 레이아웃 확정과 경합하면 시트가 창 밖으로 어긋난 채 남을 수 있어(첫 진입 시
          // 시트가 안 보이던 증상), 애니메이션 없이 초기 스냅 위치에 바로 배치합니다.
          animateOnMount={false}
          // 지도를 계속 봐야 하므로 시트를 완전히 닫지는 않습니다.
          enablePanDownToClose={false}
          // 배경은 backgroundComponent(유리→그라디언트)가 직접 그리므로, backgroundStyle 은
          // 안 넘깁니다 — 둘 다 넘기면 같은 borderRadius·테두리를 두 번 적용하게 됩니다.
          backgroundComponent={MapSheetBackground}
          handleIndicatorStyle={styles.sheetGrip}
          // 시트를 끌어 올리고 내리는 손잡이 영역을 넉넉히 잡아 드래그로 잡기 쉽게 합니다.
          handleStyle={styles.sheetHandleArea}
          onChange={(index) => {
            currentSheetIndexRef.current = index;
            setIsSheetExpanded(isMapSheetExpanded(index));
          }}
        >
          <Pressable
            accessibilityRole="button"
            accessibilityState={{ expanded: isSheetExpanded }}
            onPress={() =>
              sheetRef.current?.snapToIndex(isSheetExpanded ? 0 : SHEET_EXPANDED_INDEX)
            }
            style={({ pressed }) => [
              styles.sheetHandle,
              // 접혀 있을 때는 핸들이 곧 시트 전체라, 아래 목록과 나눌 것이 없어 밑줄이
              // 뜬금없는 줄로 보입니다. 펼쳤을 때만 헤더/본문 구분선으로 보여 줍니다.
              isSheetExpanded && styles.sheetHandleExpanded,
              pressed && styles.pressed,
            ]}
          >
            <SheetHandleIconBadge iconName="business" />
            <View style={styles.sheetHeading}>
              <Text style={styles.sheetTitle}>{isSearching ? '검색 결과' : '내 주변 아파트'}</Text>
              <Text style={styles.sheetSubtitle}>{sheetSubtitle}</Text>
            </View>
            <Ionicons
              color={MUTED_TEXT_COLOR}
              name={isSheetExpanded ? 'chevron-down' : 'chevron-up'}
              size={18}
              style={styles.sheetChevron}
            />
          </Pressable>

          <BottomSheetFlatList
            contentContainerStyle={styles.sheetListContent}
            data={listData}
            extraData={selectedApartmentId}
            initialNumToRender={12}
            keyExtractor={apartmentRowKeyExtractor}
            keyboardShouldPersistTaps="handled"
            ListEmptyComponent={listPlaceholder}
            maxToRenderPerBatch={12}
            renderItem={renderApartmentRow}
            windowSize={7}
          />
        </BottomSheet>
      </View>
    </View>
  );
}

const styles = StyleSheet.create({
  container: { flex: 1 },
  map: { flex: 1 },
  // 검색바 + 범위 칩 + 위치 안내(FE-040). 노치를 피하도록 top 은 인라인으로 줍니다.
  topOverlay: {
    position: 'absolute',
    left: 16,
    right: 16,
    zIndex: 30,
    gap: SEARCH_CHIPS_GAP,
  },
  searchBar: {
    flexDirection: 'row',
    alignItems: 'center',
    gap: 8,
    height: SEARCH_BAR_HEIGHT,
    paddingHorizontal: 14,
    borderRadius: SEARCH_BAR_HEIGHT / 2,
    backgroundColor: '#FFFFFF',
    elevation: 6,
    shadowColor: '#111111',
    shadowOffset: { width: 0, height: 2 },
    shadowOpacity: 0.12,
    shadowRadius: 6,
  },
  searchInput: {
    flex: 1,
    padding: 0,
    fontSize: 14,
    color: DARK_GREEN_COLOR,
  },
  // 왼쪽 범위 칩 / 오른쪽 임장 진입 버튼을 한 줄에 둡니다.
  scopeRow: {
    flexDirection: 'row',
    alignItems: 'center',
    justifyContent: 'space-between',
    gap: 6,
  },
  scopeChips: {
    flexDirection: 'row',
    gap: 6,
  },
  // TODO(임시): 임장 지도 진입 버튼. 실제 진입 흐름이 붙으면 이 스타일도 지웁니다.
  fieldPreviewButton: {
    flexDirection: 'row',
    alignItems: 'center',
    gap: 4,
    height: SCOPE_CHIP_HEIGHT,
    paddingHorizontal: 12,
    borderRadius: SCOPE_CHIP_HEIGHT / 2,
    backgroundColor: '#019CA1',
    elevation: 3,
    shadowColor: '#111111',
    shadowOffset: { width: 0, height: 1 },
    shadowOpacity: 0.1,
    shadowRadius: 3,
  },
  fieldPreviewButtonPressed: {
    opacity: 0.82,
  },
  fieldPreviewButtonText: {
    color: '#FFFFFF',
    fontSize: 12,
    fontWeight: '700',
  },
  scopeChip: {
    flexDirection: 'row',
    alignItems: 'center',
    gap: 4,
    height: SCOPE_CHIP_HEIGHT,
    paddingHorizontal: 12,
    borderRadius: SCOPE_CHIP_HEIGHT / 2,
    backgroundColor: '#FFFFFF',
    elevation: 3,
    shadowColor: '#111111',
    shadowOffset: { width: 0, height: 1 },
    shadowOpacity: 0.1,
    shadowRadius: 3,
  },
  scopeChipActive: {
    backgroundColor: DARK_GREEN_COLOR,
  },
  scopeChipText: {
    fontSize: 12,
    fontWeight: '600',
    color: MUTED_TEXT_COLOR,
  },
  scopeChipTextActive: {
    color: '#FFFFFF',
  },
  locationPermissionCard: {
    flexDirection: 'row',
    alignItems: 'center',
    gap: 12,
    padding: 14,
    borderRadius: 16,
    backgroundColor: '#FFFFFF',
    elevation: 6,
    shadowColor: '#111111',
    shadowOffset: { width: 0, height: 2 },
    shadowOpacity: 0.12,
    shadowRadius: 6,
  },
  locationPermissionCopy: {
    flex: 1,
  },
  locationPermissionTitle: {
    color: '#0F5C4E',
    fontSize: 14,
    fontWeight: '700',
  },
  locationPermissionDescription: {
    marginTop: 3,
    color: '#5E6A72',
    fontSize: 11,
    lineHeight: 16,
  },
  locationPermissionButton: {
    minHeight: 34,
    justifyContent: 'center',
    paddingHorizontal: 12,
    borderRadius: 17,
    backgroundColor: '#0F5C4E',
  },
  locationPermissionButtonPressed: {
    opacity: 0.82,
  },
  locationPermissionButtonText: {
    color: '#FFFFFF',
    fontSize: 11,
    fontWeight: '700',
  },
  apartmentName: {
    color: '#011D4E',
    fontSize: 16,
    fontWeight: '700',
  },
  apartmentDescription: {
    marginTop: 4,
    color: '#5E6A72',
    fontSize: 12,
  },
  // 뷰포트 아파트 목록 시트(FE-007). @gorhom/bottom-sheet 의존성을 새로 넣지 않고
  // 하단 고정 패널 + 접기/펼치기로 처리합니다.
  // 지도 컨트롤 세로 묶음. top 은 검색바 높이에 맞춰 인라인으로 줍니다.
  // 간격은 임장 지도(MAP_CONTROL_GAP)와 같은 값을 씁니다.
  mapControls: {
    position: 'absolute',
    right: 16,
    zIndex: 25,
    gap: 11,
  },
  dimensionLabel: {
    color: CONTROL_ICON_COLOR,
    fontSize: 14,
    fontWeight: '800',
    letterSpacing: 0.2,
  },
  // 컨트롤 버튼 왼쪽에 붙여 띄웁니다(버튼 지름 + 간격 10).
  filterPanel: {
    position: 'absolute',
    right: 16 + GLASS_ICON_BUTTON_SIZE + 10,
    zIndex: 26,
  },
  // 시트를 잘라 내는 창. top/bottom 은 노치·탭 바에 맞춰 인라인으로 줍니다.
  sheetWindow: {
    position: 'absolute',
    left: 12,
    right: 12,
    borderRadius: SHEET_RADIUS,
    overflow: 'hidden',
    // 접힌 시트 밖은 box-none으로 지도 조작을 유지하고, 펼친 시트가 덮은
    // 영역에서는 오른쪽 플로팅 컨트롤을 가리고 시트가 터치를 받습니다.
    zIndex: SHEET_LAYER_Z_INDEX,
  },
  sheetHandle: {
    flexDirection: 'row',
    alignItems: 'center',
    gap: 10,
    paddingBottom: 12,
    paddingHorizontal: 20,
  },
  // 접혀 있을 때는 핸들이 곧 시트 전체라, 아래 목록과 나눌 것이 없어 밑줄이 뜬금없는
  // 줄로 보입니다(ChecklistSheet 와 같은 이유). 펼쳤을 때만 구분선으로 보여 줍니다.
  sheetHandleExpanded: {
    borderBottomWidth: 1,
    borderBottomColor: BORDER_COLOR,
  },
  sheetGrip: {
    width: 56,
    height: 6,
    borderRadius: 3,
    backgroundColor: BORDER_COLOR,
  },
  // 그립(잡이)을 감싸는 기본 핸들 영역. 위아래 여백을 넉넉히 줘 드래그로 잡기 쉽게.
  sheetHandleArea: {
    paddingTop: 16,
    paddingBottom: 8,
  },
  sheetHeading: { flex: 1, gap: 2 },
  // 나브바 탭 라벨과 색은 그대로 맞추고, 굵기만 한 단계 올립니다(체크리스트 시트와 동일).
  sheetTitle: {
    fontSize: 17,
    fontFamily: TAB_LABEL_FONT_BOLD,
    color: TAB_LABEL_COLOR,
  },
  sheetSubtitle: {
    fontSize: 12,
    color: MUTED_TEXT_COLOR,
  },
  sheetChevron: { position: 'absolute', right: 20, bottom: 16 },
  sheetListContent: {
    paddingHorizontal: 16,
    paddingTop: 12,
    paddingBottom: 16,
    gap: 8,
  },
  sheetLoader: { paddingVertical: 24 },
  sheetPlaceholder: {
    paddingVertical: 24,
    textAlign: 'center',
    fontSize: 13,
    color: MUTED_TEXT_COLOR,
  },
  apartmentRow: {
    flexDirection: 'row',
    alignItems: 'center',
    gap: 12,
    padding: 12,
    borderRadius: 12,
    borderWidth: 1,
    borderColor: BORDER_COLOR,
    backgroundColor: SURFACE_COLOR,
  },
  apartmentRowSelected: {
    borderColor: PRIMARY_COLOR,
    backgroundColor: SOFT_GREEN_COLOR,
  },
  apartmentRowMain: { flex: 1, gap: 2 },
  apartmentThumb: {
    width: 56,
    height: 56,
    borderRadius: 12,
    resizeMode: 'cover',
  },
  apartmentPrice: {
    marginTop: 2,
    fontSize: 15,
    fontWeight: '700',
    color: DARK_GREEN_COLOR,
  },
  pressed: { opacity: 0.6 },
});
