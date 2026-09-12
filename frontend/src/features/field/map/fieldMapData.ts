import { distanceInMeters, type Coordinate } from '@/lib/geo';

export interface FieldTreePoint {
  id: string;
  coordinate: Coordinate;
  placement: 'verifiedPedestrian' | 'verifiedGreenArea';
  source: 'openstreetmap' | 'openstreetmap-polygon';
}

export interface PediatricFacilityPoint {
  id: string;
  name: string;
  coordinate: Coordinate;
  address: string;
}

/** 실제 OSM natural=tree 노드입니다. 도로 중심선에서 생성한 좌표는 포함하지 않습니다. */
const VERIFIED_OSM_TREE_NODES = [
  ['5687393790', 127.063058, 37.5005521], // 대치동
  ['4659458535', 127.1099626, 37.5069043], // 잠실 롯데월드타워 동쪽
  ['4659458536', 127.1099036, 37.5066011],
  ['4659458537', 127.1096902, 37.506414],
  ['4659458538', 127.1103915, 37.5062286],
  ['9798453657', 126.9973344, 37.5112684], // 반포한강공원 보행 녹지
  ['9798453658', 126.9974048, 37.5112237],
  ['9798453659', 126.9974752, 37.511179],
  ['9798453660', 126.9975456, 37.5111343],
  ['9798453661', 126.997616, 37.5110897],
  ['9798453662', 126.9976864, 37.511045],
  ['9798453663', 126.9977568, 37.5110003],
  ['9798453664', 126.9978272, 37.5109556],
  ['9798453665', 126.9978976, 37.5109109],
] as const;

/** OSM natural=tree_row를 구성하는 검증된 가로수·조경 수목 노드입니다. */
const VERIFIED_OSM_TREE_ROW_NODES = [
  ['tree-row-1457483433-0', 127.0727702, 37.492055],
  ['tree-row-1457483433-1', 127.0724246, 37.4926881],
  ['tree-row-503503950-0', 127.1045858, 37.5156622],
  ['tree-row-503503950-1', 127.1051413, 37.5158322],
  ['tree-row-503503950-2', 127.1060938, 37.5161663],
  ['tree-row-1088081563-0', 126.9979072, 37.4950951],
  ['tree-row-1088081563-1', 126.9981354, 37.4960564],
  ['tree-row-1088081564-0', 126.9981606, 37.4963308],
  ['tree-row-1088081564-1', 126.9981462, 37.4961678],
  ['tree-row-1088081565-0', 126.9980395, 37.4951836],
  ['tree-row-1088081565-1', 126.9981393, 37.4956454],
  ['tree-row-1088081566-0', 126.9981597, 37.4957405],
  ['tree-row-1088081566-1', 126.9982263, 37.4960439],
  ['tree-row-1088081567-0', 126.9982354, 37.4961682],
  ['tree-row-1088081567-1', 126.9983204, 37.496914],
  ['tree-row-1088081568-0', 126.9982323, 37.4969148],
  ['tree-row-1088081568-1', 126.9981985, 37.4966424],
  ['tree-row-802097651-0', 127.0835117, 37.5064074],
  ['tree-row-802097651-1', 127.0836901, 37.5064119],
  ['tree-row-802097651-2', 127.0833868, 37.5064971],
  ['tree-row-802097651-3', 127.0833727, 37.5064674],
  ['tree-row-802097651-4', 127.0834446, 37.5064465],
  ['tree-row-802097658-0', 127.0834322, 37.5060349],
  ['tree-row-802097658-1', 127.0836918, 37.505966],
  ['tree-row-802097658-2', 127.0836609, 37.5058886],
  ['tree-row-802097658-3', 127.0835875, 37.5059077],
  ['tree-row-802097658-4', 127.0836017, 37.5059422],
  ['tree-row-802097658-5', 127.0834904, 37.5059726],
  ['tree-row-802097658-6', 127.0834768, 37.5059396],
  ['tree-row-802097658-7', 127.083402, 37.505959],
  ['tree-row-1218472511-0', 127.0739776, 37.5113713],
  ['tree-row-1218472511-1', 127.0740485, 37.5113517],
  ['tree-row-1218472511-2', 127.0741363, 37.5113395],
  ['tree-row-1218472511-3', 127.0742518, 37.511353],
  ['tree-row-1218472511-4', 127.0743704, 37.5113725],
  ['tree-row-1218472511-5', 127.0760018, 37.5116536],
] as const;

/**
 * OSM의 작은 공원·단지 조경 polygon 내부 대표점입니다.
 * 큰 숲을 채우지 않고 생활권 조경만 표현하며, 차도·교차로 좌표는 제외했습니다.
 */
const VERIFIED_URBAN_GREEN_POINTS = [
  ['jamsil-ppongnamu-park', 127.0821986, 37.5141516], // 뽕나무근린공원, way 394541403
  ['jamsil-buri-park', 127.0872755, 37.5139874], // 부리근린공원, way 472433029
  ['jamsil-complex-green', 127.0867487, 37.5133042], // 단지 조경, way 472445705
  ['jamsil-neighborhood-green', 127.0841824, 37.5104941], // 생활권 공원, way 1282769569
  ['daechi-ginkgo-park', 127.0515706, 37.5018497], // 대치은행나무공원, way 383846975
  ['daechi-kkachi-park', 127.0592699, 37.4970041], // 까치공원, way 443025248
  ['daechi-hanti-park', 127.0619093, 37.4954988], // 한티근린공원, way 474636219
  ['daechi-magnolia-park', 127.05838, 37.4998022], // 대치목련공원, way 900203752
  // 송파나루공원 polygon 대표점은 석촌호수 수면과 겹치므로 사용하지 않습니다.
  ['jamsil-donghosu-park', 127.1069802, 37.509681], // 동호수어린이공원, way 169794551
  ['jamsil-6dong-park', 127.1014174, 37.5183697], // 잠실6동공원, way 468564151
  ['banpo-hangang-park', 126.9901073, 37.5087083], // 반포한강공원, way 418249072
  ['banpo-banwon-park', 127.0036701, 37.5092424], // 반원어린이공원, way 413360475
  ['banpo-jamwon-park', 127.0100874, 37.5126687], // 잠원근린공원, way 418110862
  ['daechi-neighborhood-park', 127.0696255, 37.4956837], // 대치근린공원, way 468924490
  ['daechi-drainage-park', 127.0681042, 37.5043037], // 대치유수지체육공원, way 637366668
  ['daechi-sunshine-park', 127.0524714, 37.4957892], // 도곡햇살공원, way 636207734
  ['daechi-grass-01', 127.0525262, 37.5082292], // 생활권 녹지, way 382000602
  ['daechi-grass-02', 127.0621646, 37.5097032], // 생활권 녹지, way 838309244
  ['jamsil-imagination-park', 127.0997831, 37.5059344], // 석촌상상 어린이공원, way 169789722
  ['jamsil-obongsan-park', 127.1024919, 37.5061937], // 오봉산 어린이공원, way 169789735
  ['jamsil-bangigol-park', 127.1103612, 37.5134625], // 방잇골 어린이공원, way 169795564
  ['jamsil-peace-park', 127.1120503, 37.5162846], // 평화소공원, way 169795490
  ['jamsil-haneulgaram-park', 127.1066506, 37.5195089], // 하늘가람근린공원, way 470153246
  ['banpo-parangsae-park', 126.9952528, 37.5050751], // 파랑새어린이공원, way 413280018
  ['banpo-neighborhood-park', 126.9912668, 37.4986168], // 반포공원, way 469016343
  ['banpo-shindong-park', 127.0139442, 37.514149], // 신동근린공원, way 469022911
  ['banpo-myeongju-park', 127.015116, 37.5172499], // 명주근린공원, way 469639931
  ['banpo-riverside-island', 126.9895458, 37.5078097], // 서래섬 공원, way 25979109
] as const;

export const VERIFIED_FIELD_TREES: readonly FieldTreePoint[] = [
  ...VERIFIED_OSM_TREE_NODES.map(([id, longitude, latitude]) => ({
    id,
    coordinate: [longitude, latitude] as Coordinate,
    placement: 'verifiedPedestrian' as const,
    source: 'openstreetmap' as const,
  })),
  ...VERIFIED_OSM_TREE_ROW_NODES.map(([id, longitude, latitude]) => ({
    id,
    coordinate: [longitude, latitude] as Coordinate,
    placement: 'verifiedPedestrian' as const,
    source: 'openstreetmap' as const,
  })),
  ...VERIFIED_URBAN_GREEN_POINTS.map(([id, longitude, latitude]) => ({
    id,
    coordinate: [longitude, latitude] as Coordinate,
    placement: 'verifiedGreenArea' as const,
    source: 'openstreetmap-polygon' as const,
  })),
];

/** 송파구 공식 보육 안내 자료의 주소를 좌표화한 잠실권 소아청소년과입니다. */
const PEDIATRIC_FACILITIES: readonly PediatricFacilityPoint[] = [
  {
    id: 'gangnam-junior-pediatrics',
    name: '강남주니어소아청소년과',
    coordinate: [127.086206, 37.510776],
    address: '서울 송파구 석촌호수로 61',
  },
  {
    id: 'mom-and-mom-pediatrics',
    name: '맘앤맘소아청소년과',
    coordinate: [127.084132, 37.5121548],
    address: '서울 송파구 올림픽로 119',
  },
];

/** 대상 아파트에서 가까운 순으로 1.5km 이내 최대 5개만 반환합니다. */
export function getNearbyPediatricFacilities(destination: Coordinate): PediatricFacilityPoint[] {
  return PEDIATRIC_FACILITIES.filter(
    ({ coordinate }) => distanceInMeters(destination, coordinate) <= 1_500,
  )
    .sort(
      (left, right) =>
        distanceInMeters(destination, left.coordinate) -
        distanceInMeters(destination, right.coordinate),
    )
    .slice(0, 5);
}
