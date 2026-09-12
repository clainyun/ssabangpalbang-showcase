import type Ionicons from '@expo/vector-icons/Ionicons';
import type Mapbox from '@rnmapbox/maps';
import {
  type ComponentProps,
  type RefObject,
  useCallback,
  useEffect,
  useMemo,
  useRef,
  useState,
} from 'react';

/**
 * 지도에 표시할 시설 필터. 임장 지도와 일반 지도가 같은 규칙을 쓰도록 한곳에 둡니다.
 *
 * Mapbox 스타일이 이미 가진 `poi-label`·`transit-label` 레이어에 filter 만 덮어씌우는
 * 방식이라(`<Mapbox.SymbolLayer existing />`), 별도 소스나 마커를 만들지 않습니다.
 */
export type FacilityCategory = 'transport' | 'education' | 'medical' | 'life' | 'environment';

export type MapboxFilter = NonNullable<ComponentProps<typeof Mapbox.SymbolLayer>['filter']>;

type SymbolLayerStyle = NonNullable<ComponentProps<typeof Mapbox.SymbolLayer>['style']>;

export const FACILITY_CATEGORIES: readonly {
  id: FacilityCategory;
  label: string;
  icon: keyof typeof Ionicons.glyphMap;
}[] = [
  { id: 'transport', label: '교통', icon: 'subway-outline' },
  { id: 'education', label: '학군', icon: 'school-outline' },
  { id: 'medical', label: '의료', icon: 'medkit-outline' },
  { id: 'life', label: '생활', icon: 'cart-outline' },
  { id: 'environment', label: '환경', icon: 'leaf-outline' },
];

/**
 * 카테고리별 시설 분류. 기본(임장 핵심)과 토글이 서로 다른 기준을 쓰면(예: 기본은
 * 초등학교만, 토글은 학교 전체) 토글 전후로 다른 게 나오는 것처럼 보입니다. 그래서
 * "학교면 다 학교, 병원이면 다 병원"으로 기준을 하나로 통일하고, 임장 핵심/토글
 * 모두 이 목록만 참조합니다.
 */
const CATEGORY_FACILITY_MAKI: Record<Exclude<FacilityCategory, 'transport'>, readonly string[]> = {
  education: ['school', 'college', 'kindergarten'],
  medical: ['hospital', 'doctor', 'emergency-room', 'pharmacy'],
  // 은행·우체국처럼 생활 반경을 확인할 때 같이 보면 좋은 것들을 추가했습니다.
  life: ['grocery', 'marketplace', 'shopping-mall', 'library', 'town-hall', 'bank', 'post'],
  // 놀이터는 아이 키우는 세대의 임장에서 중요하게 보는 시설이라 추가했습니다.
  environment: ['park', 'garden', 'nature-reserve', 'pitch', 'swimming', 'playground'],
};

/**
 * 우리가 카테고리로 추적하는 모든 maki 타입. `poi-label`의 기본 레이아웃을 덮어써서
 * 아이콘이 항상 보이게 하는 데 씁니다(아래 POI_LABEL_LAYOUT_OVERRIDE 참고).
 */
const TRACKED_POI_MAKI = [...new Set(Object.values(CATEGORY_FACILITY_MAKI).flat())];

const IS_TRACKED_FACILITY: MapboxFilter = [
  'match',
  ['coalesce', ['get', 'maki_beta'], ['get', 'maki']],
  ['literal', TRACKED_POI_MAKI],
  true,
  false,
];

/**
 * Mapbox Streets 기본 poi-label 레이어는 POI의 `sizerank`(실세계 인지도 점수)가
 * 낮을수록(=랜드마크급일수록) 글자를 크게 키우면서 text-anchor를 "center"·
 * text-offset을 [0,0]으로 바꿔 텍스트를 아이콘과 같은 자리에 겹쳐 그립니다. 그
 * 결과 같은 카테고리(예: 학교)인데도 어떤 곳은 아이콘 없이 큰 글자만 보이고, 어떤
 * 곳은 아이콘+작은 글자로 보여 "왜 얘만 다르게 나오냐"는 혼란을 만듭니다.
 *
 * 우리가 추적하는 카테고리(TRACKED_POI_MAKI)에 대해서만 text-anchor·text-offset을
 * 고정해, 글자 크기는 원본 그대로(랜드마크는 계속 크게) 두면서 아이콘은 항상 텍스트
 * 아래로 떨어져 가려지지 않게 합니다. 나머지(전체 시설 보기에서만 보이는 음식점 등)는
 * Mapbox 기본 동작을 그대로 둡니다.
 *
 * ⚠️ `["zoom"]`은 Mapbox GL 스펙상 최상위 step/interpolate의 입력으로만 쓸 수 있고
 * case·match 안에 중첩하면 네이티브에서 "may only be used as input to a top-level
 * step..." 예외를 던지며 그 레이어 속성 적용 자체가 실패합니다. 그래서 원본처럼
 * zoom-step을 가장 바깥에 두고, tracked 여부 분기(case)는 그 step의 각 결과값
 * 안쪽에 넣습니다(zoom을 안 쓰니 중첩 가능).
 */
export const POI_LABEL_STYLE_OVERRIDE: {
  textAnchor: SymbolLayerStyle['textAnchor'];
  textOffset: SymbolLayerStyle['textOffset'];
} = {
  textAnchor: [
    'step',
    ['zoom'],
    ['case', IS_TRACKED_FACILITY, 'top', ['step', ['get', 'sizerank'], 'center', 5, 'top']],
    17,
    ['case', IS_TRACKED_FACILITY, 'top', ['step', ['get', 'sizerank'], 'center', 13, 'top']],
  ],
  textOffset: [
    'step',
    ['zoom'],
    [
      'case',
      IS_TRACKED_FACILITY,
      ['literal', [0, 0.8]],
      ['step', ['get', 'sizerank'], ['literal', [0, 0]], 5, ['literal', [0, 0.8]]],
    ],
    17,
    [
      'case',
      IS_TRACKED_FACILITY,
      ['literal', [0, 0.8]],
      ['step', ['get', 'sizerank'], ['literal', [0, 0]], 13, ['literal', [0, 0.8]]],
    ],
  ],
};

/**
 * 교통(지하철·버스)은 POI 가 아니라 별도 정류장 레이어(mode 속성)를 씁니다.
 *
 * 다른 카테고리와 달리 기본값과 명시적 선택의 기준이 다릅니다 — 버스 정류장까지
 * 기본으로 다 보여주면 화면이 정류장 아이콘으로 뒤덮여서, 아무것도 안 고른
 * 기본값에서는 지하철·전철만 남기고, '교통'을 직접 고를 때만 버스·경전철까지
 * 넓힙니다.
 */
const TRANSPORT_MODES_DEFAULT = ['rail', 'metro_rail'];
const TRANSPORT_MODES_FULL = ['rail', 'metro_rail', 'light_rail', 'bus'];

/**
 * 항상 false 로 평가되는 필터(보여줄 게 하나도 없을 때 씀).
 *
 * `match` 표현식은 후보(label)가 최소 1개는 있어야 합니다 — 없으면 Mapbox 가
 * "Expected at least one branch label" 예외를 던지며 그대로 크래시합니다. 카테고리를
 * 하나만 골랐는데 그 카테고리가 이 레이어에 해당하는 게 하나도 없을 때(예: POI
 * 레이어에서 '교통'만 고른 경우, 또는 정류장 레이어에서 '교통' 없이 다른 것만 고른
 * 경우) `match` 대신 이 값을 씁니다.
 */
const ALWAYS_FALSE_FILTER: MapboxFilter = ['==', ['literal', 0], 1];

/** 교통을 뺀 나머지 4개 카테고리(POI 레이어에 해당하는 것) id 목록. */
const POI_CATEGORIES = FACILITY_CATEGORIES.map((category) => category.id).filter(
  (id): id is Exclude<FacilityCategory, 'transport'> => id !== 'transport',
);

/** 임장 핵심 — 카테고리를 하나도 안 골랐을 때 보여주는 기본 구성(5개 카테고리 전체). */
const CORE_POI_MAKI = [...new Set(POI_CATEGORIES.flatMap((category) => CATEGORY_FACILITY_MAKI[category]))];

export function buildPoiFilter(
  expandedCategories: FacilityCategory[],
  showAllFacilities: boolean,
  showCoreFacilities: boolean,
): MapboxFilter {
  if (showAllFacilities) {
    // 전체 시설도 원본 filterrank를 적용해 한 화면에 모든 상호가 쏟아지지 않게 합니다.
    return ['<=', ['get', 'filterrank'], 5];
  }

  // 카테고리를 하나라도 골랐으면 임장 핵심 토글과 무관하게 고른 것만 보여줍니다
  // (더해지는 게 아니라 교체 — "교통을 누르면 교통만" 요청대로).
  if (expandedCategories.length > 0) {
    const activeMaki = [
      ...new Set(
        expandedCategories.flatMap((category) =>
          category === 'transport' ? [] : CATEGORY_FACILITY_MAKI[category],
        ),
      ),
    ];
    if (activeMaki.length === 0) {
      // 예: '교통'만 골랐을 때 — POI 레이어에 해당하는 카테고리가 하나도 없습니다.
      return ALWAYS_FALSE_FILTER;
    }

    return [
      'all',
      [
        'match',
        ['coalesce', ['get', 'maki_beta'], ['get', 'maki']],
        ['literal', activeMaki],
        true,
        false,
      ],
      ['<=', ['get', 'filterrank'], ['step', ['zoom'], 2, 14, 3, 16, 4, 18, 5]],
    ];
  }

  if (!showCoreFacilities) {
    return ALWAYS_FALSE_FILTER;
  }

  return [
    'all',
    [
      'match',
      ['coalesce', ['get', 'maki_beta'], ['get', 'maki']],
      ['literal', CORE_POI_MAKI],
      true,
      false,
    ],
    ['<=', ['get', 'filterrank'], ['step', ['zoom'], 2, 14, 3, 16, 4, 18, 5]],
  ];
}

export function buildTransitFilter(
  expandedCategories: FacilityCategory[],
  showAllFacilities: boolean,
  showCoreFacilities: boolean,
): MapboxFilter {
  if (showAllFacilities) {
    return ['!=', ['get', 'stop_type'], 'entrance'];
  }

  let modes: string[];
  if (expandedCategories.length > 0) {
    // 교통 없이 다른 카테고리만 골랐으면 정류장을 아예 숨깁니다. '교통'을 직접
    // 고르면 버스·경전철까지 넓힙니다.
    if (!expandedCategories.includes('transport')) {
      return ALWAYS_FALSE_FILTER;
    }
    modes = TRANSPORT_MODES_FULL;
  } else {
    // 임장 핵심(아무것도 안 고름) 은 지하철·전철만 — 정류장 아이콘이 너무 많아지는
    // 걸 피하려고 버스는 뺍니다.
    if (!showCoreFacilities) {
      return ALWAYS_FALSE_FILTER;
    }
    modes = TRANSPORT_MODES_DEFAULT;
  }

  return [
    'all',
    ['match', ['get', 'mode'], ['literal', modes], true, false],
    ['!=', ['get', 'stop_type'], 'entrance'],
    ['<=', ['get', 'filterrank'], ['step', ['zoom'], 3, 15, 5, 17, 8]],
  ];
}

interface UseFacilityFiltersOptions {
  /**
   * 필터가 바뀔 때마다 지도를 살짝 흔들어 다시 그리게 하는 데 씁니다(아래
   * nudgeMapToRerender 주석 참고). 두 화면 다 이미 갖고 있는 카메라 ref를 그대로
   * 넘기면 됩니다. 둘 다 넘기지 않으면 흔들기를 건너뜁니다.
   */
  cameraRef?: RefObject<Mapbox.Camera | null>;
  mapViewRef?: RefObject<Mapbox.MapView | null>;
}

/**
 * @rnmapbox/maps(안드로이드)가 SymbolLayer의 filter를 바꿔도 이미 그려진 심볼을
 * 다시 그리지 않는 경우가 있습니다 — 실기기에서 새 filter 값은 정확한데 화면은
 * 이전 상태 그대로 남는 걸 반복 확인했습니다(레이어를 새로 만들어도 `existing`
 * 레이어라 효과 없음). 이건 안드로이드 SDK의 `styleimagemissing` 이벤트 처리
 * 레이스 컨디션으로 알려진 문제입니다(rnmapbox/maps 저장소 discussions #4007).
 * 매번 재현되는 건 아니라 더 헷갈립니다.
 *
 * 카메라를 살짝(0.0001) 흔드는 것만으로는 못 미더워서(실기기에서 마트가 계속 안
 * 뜨는 경우를 재현함), 레이어 자체를 잠깐 껐다 켭니다(visibility: none → visible).
 * 레이어를 완전히 숨기고 다시 보이면 안드로이드가 심볼 배치를 처음부터 다시
 * 계산하므로, 필터만 바꿀 때보다 훨씬 확실하게 새로고침됩니다. 카메라 흔들기는
 * 혹시 몰라 같이 남겨 둡니다(비용이 거의 없고, 다른 경로로도 재계산을 유도합니다).
 */
async function nudgeMapToRerender(
  cameraRef: RefObject<Mapbox.Camera | null> | undefined,
  mapViewRef: RefObject<Mapbox.MapView | null> | undefined,
) {
  if (cameraRef?.current == null || mapViewRef?.current == null) return;
  try {
    const zoom = await mapViewRef.current.getZoom();
    if (!Number.isFinite(zoom)) return;
    cameraRef.current.setCamera({ zoomLevel: zoom + 0.0001, animationDuration: 0 });
  } catch {
    // 지도가 아직 준비되지 않았으면(마운트 직후 등) 조용히 건너뜁니다.
  }
}

/** 레이어를 껐다 켜는 사이 얼마나 숨겨 둘지. 너무 짧으면 안드로이드가 "완전히
 * 없어졌다가 다시 생겼다"고 인식하지 못할 수 있어 넉넉히 잡습니다. */
const LAYER_HIDE_DURATION_MS = 80;

/** 필터 상태와 그로부터 계산한 Mapbox filter 를 묶어 둔 훅. 두 지도 화면이 공유합니다. */
export function useFacilityFilters({ cameraRef, mapViewRef }: UseFacilityFiltersOptions = {}) {
  const [expandedCategories, setExpandedCategories] = useState<FacilityCategory[]>([]);
  const [showAllFacilities, setShowAllFacilities] = useState(false);
  // 임장 핵심(카테고리를 하나도 안 골랐을 때의 기본 구성) 도 껐다 켤 수 있게 합니다.
  const [showCoreFacilities, setShowCoreFacilities] = useState(true);

  const poiFilter = useMemo(
    () => buildPoiFilter(expandedCategories, showAllFacilities, showCoreFacilities),
    [expandedCategories, showAllFacilities, showCoreFacilities],
  );
  const transitFilter = useMemo(
    () => buildTransitFilter(expandedCategories, showAllFacilities, showCoreFacilities),
    [expandedCategories, showAllFacilities, showCoreFacilities],
  );

  // 필터가 바뀌는 순간 레이어를 잠깐 숨겨 두는 상태. true인 동안은 poi-label·
  // transit-label 모두 visibility: 'none'으로 렌더링됩니다(아래 반환값 참고).
  const [isLayerHidden, setIsLayerHidden] = useState(false);

  /** 필터를 결정하는 입력값을 하나의 문자열로. 아래 재적용 우회 로직을 그 값이
   * 실제로 바뀔 때만 실행하는 데 씁니다. */
  const filterKey = `${expandedCategories.join(',')}|${showAllFacilities}|${showCoreFacilities}`;

  // 위 nudgeMapToRerender·LAYER_HIDE_DURATION_MS 주석 참고 — filterKey가 바뀔
  // 때마다(첫 렌더는 빼고) 레이어를 껐다 켜고, 카메라도 살짝 흔들어 안드로이드의
  // 필터 재적용 누락을 우회합니다.
  const isFirstFilterRenderRef = useRef(true);
  useEffect(() => {
    if (isFirstFilterRenderRef.current) {
      isFirstFilterRenderRef.current = false;
      return;
    }
    setIsLayerHidden(true);
    void nudgeMapToRerender(cameraRef, mapViewRef);
    const showTimer = setTimeout(() => setIsLayerHidden(false), LAYER_HIDE_DURATION_MS);
    // 한 번의 흔들기가 딱 그 타이밍에 로딩 중이던 타일을 놓칠 수 있어, 조금 뒤
    // 한 번 더 흔듭니다(styleimagemissing 레이스 컨디션은 타이밍에 따라 재현
    // 여부가 갈립니다 — 한 번으로는 못 미더워서 이중으로 겁니다).
    const nudgeTimer = setTimeout(() => void nudgeMapToRerender(cameraRef, mapViewRef), 400);
    return () => {
      clearTimeout(showTimer);
      clearTimeout(nudgeTimer);
    };
    // eslint-disable-next-line react-hooks/exhaustive-deps -- ref 들은 안정적이라 매번 새로 실행할 필요가 없습니다.
  }, [filterKey]);

  // 카테고리를 하나라도 고르면 핵심 대신 그것만 보이므로, 핵심 스위치도 꺼진 걸로
  // 맞춥니다(안 그러면 교통을 골라도 스위치는 계속 켜진 채로 보여서 헷갈립니다).
  // 반대로 전부 해제해도 핵심을 자동으로 다시 켜지는 않습니다 — 스위치가 이미 있으니
  // 다시 보고 싶으면 직접 켜면 됩니다.
  const toggleFacilityCategory = useCallback((category: FacilityCategory) => {
    setExpandedCategories((current) => {
      const next = current.includes(category)
        ? current.filter((item) => item !== category)
        : [...current, category];

      if (next.length > 0) {
        setShowCoreFacilities(false);
      }
      return next;
    });
  }, []);

  // 반대 방향: 임장 핵심을 직접 켜면 카테고리 선택은 비워서 핵심 화면으로 돌아갑니다.
  const changeShowCoreFacilities = useCallback((value: boolean) => {
    setShowCoreFacilities(value);
    if (value) {
      setExpandedCategories([]);
    }
  }, []);

  return {
    expandedCategories,
    showAllFacilities,
    setShowAllFacilities,
    showCoreFacilities,
    setShowCoreFacilities: changeShowCoreFacilities,
    toggleFacilityCategory,
    poiFilter,
    transitFilter,
    /** poi-label·transit-label 의 style.visibility 에 그대로 꽂아 씁니다. */
    layerVisibility: (isLayerHidden ? 'none' : 'visible') as SymbolLayerStyle['visibility'],
    filterKey,
  };
}
