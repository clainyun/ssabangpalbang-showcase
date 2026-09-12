import Ionicons from '@expo/vector-icons/Ionicons';
import Mapbox from '@rnmapbox/maps';
import { router, useLocalSearchParams } from 'expo-router';
import * as MediaLibrary from 'expo-media-library';
import { useCallback, useMemo, useRef, useState } from 'react';
import { ActivityIndicator, Pressable, StyleSheet, Text, View } from 'react-native';
import { useSafeAreaInsets } from 'react-native-safe-area-context';

import fieldMapStyle from '@/assets/mapStyles/fieldMapStyle.json';
import { appAlert } from '@/components/AppDialog';
import { GlassIconButton } from '@/components/GlassIconButton';
import { TAB_LABEL_FONT_BOLD } from '@/components/TabIcon';
import {
  BORDER_COLOR,
  BUTTON_BACKGROUND_COLOR,
  DARK_GREEN_COLOR,
  MUTED_TEXT_COLOR,
  PRIMARY_COLOR,
  SOFT_GREEN_COLOR,
  SURFACE_COLOR,
} from '@/constants/colors';
import {
  fieldTrackDurationMs,
  useFieldTrack,
  type FieldTrackPoint,
} from '@/features/field/fieldTrackStore';
import type { Coordinate } from '@/lib/geo';
import { explainBeforeRequest, showPermanentlyDeniedAlert } from '@/lib/permissions';

/**
 * FE-018 — 임장을 끝낸 직후 "오늘 이렇게 걸었어요"를 보여 주는 요약 화면입니다.
 *
 * 궤적은 기기 로컬(fieldTrackStore)에만 있습니다. 이 화면은 어떤 API 도 호출하지
 * 않고, 좌표를 서버로 보내지도 로그로 남기지도 않습니다.
 *
 * 기존 종료 흐름 사이에 끼워 넣은 화면이라 여기서 흐름이 끝나지 않습니다.
 * - reportId 가 있으면 → 리포트 생성 화면으로 이어집니다.
 * - 없으면(다른 팀원이 아직 진행 중) → 기존 전용 대기 화면으로 이어집니다.
 */

/**
 * 렌더마다 재직렬화되지 않도록 모듈 스코프에서 한 번만 만듭니다(지도 탭·임장 지도와
 * 같은 이유). 요약은 기울기 없는 평면 지도라 지도 탭과 같은 스타일을 씁니다.
 */
const SUMMARY_MAP_STYLE_JSON = JSON.stringify(fieldMapStyle);

/**
 * 궤적선 색. 임장 지도에 추천 경로선이 함께 그려지던 시절에는 그것과 구분하려고 더 진한
 * 브랜드 그린을 썼지만, 경로선이 빠진 뒤로는 구분할 대상이 없습니다. 임장 지도의
 * 체크포인트 마커와 같은 연한 민트그린으로 맞춰 임장 화면 → 요약 화면의 색을 잇습니다.
 */
const TRACK_LINE_COLOR = '#63E69A';
/** 밝은 지도 배경과 건물 위에서도 선이 끊겨 보이지 않게 흰 테두리를 깔아 줍니다. */
const TRACK_CASING_COLOR = '#FFFFFF';

/** 궤적 전체가 카드·뒤로가기 버튼에 가리지 않게 두는 지도 여백(px). */
const TRACK_CAMERA_PADDING = 64;

/** 점이 사실상 한 자리에 몰려 bounds 가 무의미해질 때 대신 쓰는 줌. */
const SINGLE_POINT_ZOOM_LEVEL = 17;
/** bounds 의 가로/세로 span 이 이보다 작으면 한 점으로 취급합니다(약 11m). */
const MIN_BOUNDS_SPAN_DEGREES = 0.0001;

/** 선을 그리려면 최소 두 점이 필요합니다. */
const MIN_DRAWABLE_POINTS = 2;

type CameraSettings = NonNullable<React.ComponentProps<typeof Mapbox.Camera>['defaultSettings']>;

function formatDistance(meters: number): string {
  if (meters < 1_000) return `${Math.round(meters)}m`;

  return `${(meters / 1_000).toFixed(2)}km`;
}

function formatDuration(durationMs: number): string {
  const totalMinutes = Math.floor(durationMs / 60_000);
  const hours = Math.floor(totalMinutes / 60);
  const minutes = totalMinutes % 60;

  if (hours > 0) return `${hours}시간 ${minutes}분`;
  if (totalMinutes > 0) return `${totalMinutes}분`;

  return `${Math.floor(durationMs / 1_000)}초`;
}

/** 평균 속도(km/h). 시간이 0 이면 의미가 없어 null 로 둡니다. */
function averageSpeedKmh(meters: number, durationMs: number): number | null {
  if (durationMs <= 0 || meters <= 0) return null;

  return meters / 1_000 / (durationMs / 3_600_000);
}

/** 모든 점을 감싸는 경계와 중심. 점이 없으면 null. */
function trackBounds(points: FieldTrackPoint[]): {
  sw: Coordinate;
  ne: Coordinate;
  center: Coordinate;
  isSinglePoint: boolean;
} | null {
  const first = points[0];
  if (first === undefined) return null;

  let [minLongitude, minLatitude] = first.coordinate;
  let [maxLongitude, maxLatitude] = first.coordinate;

  for (const { coordinate } of points) {
    minLongitude = Math.min(minLongitude, coordinate[0]);
    maxLongitude = Math.max(maxLongitude, coordinate[0]);
    minLatitude = Math.min(minLatitude, coordinate[1]);
    maxLatitude = Math.max(maxLatitude, coordinate[1]);
  }

  return {
    sw: [minLongitude, minLatitude],
    ne: [maxLongitude, maxLatitude],
    center: [(minLongitude + maxLongitude) / 2, (minLatitude + maxLatitude) / 2],
    isSinglePoint:
      maxLongitude - minLongitude < MIN_BOUNDS_SPAN_DEGREES &&
      maxLatitude - minLatitude < MIN_BOUNDS_SPAN_DEGREES,
  };
}

/** 임장 화면과 같은 방식으로, 뒤로 갈 곳이 없으면 홈으로 보냅니다. */
function goBackOrHome() {
  if (router.canGoBack()) router.back();
  else router.replace('/(app)/(tabs)/home');
}

export default function FieldSummaryScreen() {
  const params = useLocalSearchParams<{
    sessionId: string;
    studyId?: string;
    reportId?: string;
    apartmentName?: string;
    /** '1' 이면 다른 참여자를 기다리는 중이라 리포트가 아직 없습니다. */
    waiting?: string;
  }>();

  const insets = useSafeAreaInsets();
  const mapRef = useRef<Mapbox.MapView>(null);
  const cameraRef = useRef<Mapbox.Camera>(null);
  const [isSavingImage, setIsSavingImage] = useState(false);

  const track = useFieldTrack(params.sessionId ?? '');
  const points = useMemo(() => track?.points ?? [], [track]);
  const hasDrawableTrack = points.length >= MIN_DRAWABLE_POINTS;

  const distanceMeters = track?.totalDistanceMeters ?? 0;
  // 종료 시점에 endedAt 이 박히므로 이 화면에서는 값이 더 이상 흐르지 않습니다.
  const durationMs = track === null ? 0 : fieldTrackDurationMs(track);
  const speedKmh = averageSpeedKmh(distanceMeters, durationMs);

  const trackShape = useMemo<GeoJSON.Feature<GeoJSON.LineString> | null>(() => {
    if (!hasDrawableTrack) return null;

    return {
      type: 'Feature',
      properties: {},
      geometry: { type: 'LineString', coordinates: points.map((point) => point.coordinate) },
    };
  }, [hasDrawableTrack, points]);

  const endpointShape = useMemo<GeoJSON.FeatureCollection<GeoJSON.Point> | null>(() => {
    if (!hasDrawableTrack) return null;

    const start = points[0];
    const end = points[points.length - 1];
    if (start === undefined || end === undefined) return null;

    return {
      type: 'FeatureCollection',
      features: [
        {
          type: 'Feature',
          properties: { label: '출발', isEnd: false },
          geometry: { type: 'Point', coordinates: start.coordinate },
        },
        {
          type: 'Feature',
          properties: { label: '도착', isEnd: true },
          geometry: { type: 'Point', coordinates: end.coordinate },
        },
      ],
    };
  }, [hasDrawableTrack, points]);

  // 궤적 전체가 한 화면에 들어오도록 경계에 맞춥니다. 점이 한 자리에 몰려 있으면
  // 경계가 0 에 가까워 줌이 튀므로 중심 + 고정 줌으로 대신합니다.
  const cameraSettings = useMemo<CameraSettings | null>(() => {
    const bounds = trackBounds(points);
    if (bounds === null) return null;

    if (bounds.isSinglePoint) {
      return {
        centerCoordinate: bounds.center,
        zoomLevel: SINGLE_POINT_ZOOM_LEVEL,
        heading: 0,
        pitch: 0,
      };
    }

    return {
      bounds: {
        ne: bounds.ne,
        sw: bounds.sw,
        paddingTop: TRACK_CAMERA_PADDING,
        paddingRight: TRACK_CAMERA_PADDING,
        paddingBottom: TRACK_CAMERA_PADDING,
        paddingLeft: TRACK_CAMERA_PADDING,
      },
      heading: 0,
      pitch: 0,
    };
  }, [points]);

  // defaultSettings 는 마운트 시 한 번만 적용되는데, 스타일이 늦게 뜨면 그 값이
  // 무시되는 경우가 있어 로딩이 끝난 뒤 같은 값을 한 번 더 명령형으로 넣습니다.
  const applyTrackCamera = useCallback(() => {
    if (cameraSettings === null) return;
    cameraRef.current?.setCamera({ ...cameraSettings, animationDuration: 0 });
  }, [cameraSettings]);

  /**
   * 지도만 이미지로 남깁니다. takeSnap 은 네이티브 지도 뷰의 스냅샷이라 그 위에 얹은
   * RN 오버레이(뒤로가기 버튼)나 아래 요약 카드는 찍히지 않습니다. 카드를 지도 위로
   * 겹치지 않고 아래에 따로 둔 것도 "보이는 지도 = 저장되는 이미지"를 맞추기 위함입니다.
   */
  const handleSaveImage = useCallback(() => {
    void (async () => {
      const map = mapRef.current;
      if (map === null || isSavingImage) return;

      setIsSavingImage(true);
      try {
        // 쓰기 전용 권한만 요청합니다 — 갤러리를 읽을 일이 없습니다.
        let permission = await MediaLibrary.getPermissionsAsync(true, ['photo']);

        if (!permission.granted) {
          const shouldRequest = await explainBeforeRequest('photo');
          if (!shouldRequest) return;

          permission = await MediaLibrary.requestPermissionsAsync(true, ['photo']);
          if (!permission.granted) {
            if (!permission.canAskAgain) showPermanentlyDeniedAlert('photo');
            return;
          }
        }

        // writeToDisk=true 여야 file:// 경로가 나옵니다(false 면 base64 문자열).
        const fileUri = await map.takeSnap(true);
        if (!fileUri) throw new Error('map snapshot returned no uri');

        await MediaLibrary.Asset.create(fileUri);
        appAlert('경로 이미지를 저장했어요', '갤러리에서 오늘 걸은 경로를 확인할 수 있어요.');
      } catch {
        appAlert('저장하지 못했어요', '잠시 후 다시 시도해 주세요.');
      } finally {
        setIsSavingImage(false);
      }
    })();
  }, [isSavingImage]);

  const reportId = params.reportId?.trim();
  const studyId = params.studyId?.trim();
  const isWaitingForTeammates = params.waiting === '1' || !reportId;

  const handlePrimaryAction = useCallback(() => {
    if (reportId) {
      router.replace({
        pathname: '/(app)/report-generating/[reportId]',
        params: { reportId },
      } as never);
      return;
    }

    if (studyId) {
      // 기존 전용 대기 화면(강제 종료·종료 취소 포함)으로 이어 줍니다.
      router.replace({
        pathname: '/(app)/field-visit-waiting/[studyId]',
        params: { studyId },
      } as never);
      return;
    }

    goBackOrHome();
  }, [reportId, studyId]);

  return (
    <View style={styles.screen}>
      <View style={styles.mapArea}>
        {hasDrawableTrack && trackShape !== null ? (
          <Mapbox.MapView
            ref={mapRef}
            style={styles.map}
            styleJSON={SUMMARY_MAP_STYLE_JSON}
            scaleBarEnabled={false}
            zoomEnabled
            scrollEnabled
            rotateEnabled={false}
            pitchEnabled={false}
            onDidFinishLoadingMap={applyTrackCamera}
          >
            <Mapbox.Camera
              ref={cameraRef}
              {...(cameraSettings === null ? {} : { defaultSettings: cameraSettings })}
            />

            {/* 흰 테두리(아래) → 브랜드 그린 본선(위) 순서로 겹칩니다. 이 스타일은
                Standard import 가 없는 고전 스타일이라(지도 탭과 동일) slot 을 쓰지
                않고, 선언 순서대로 기존 레이어 위에 얹힙니다. */}
            <Mapbox.ShapeSource id="field-summary-track-source" shape={trackShape}>
              <Mapbox.LineLayer
                id="field-summary-track-casing"
                style={{
                  lineColor: TRACK_CASING_COLOR,
                  lineWidth: 11,
                  lineOpacity: 0.95,
                  lineCap: 'round',
                  lineJoin: 'round',
                }}
              />
              <Mapbox.LineLayer
                id="field-summary-track-line"
                style={{
                  lineColor: TRACK_LINE_COLOR,
                  lineWidth: 6.5,
                  lineOpacity: 0.98,
                  lineCap: 'round',
                  lineJoin: 'round',
                }}
              />
            </Mapbox.ShapeSource>

            {endpointShape !== null && (
              <Mapbox.ShapeSource id="field-summary-endpoint-source" shape={endpointShape}>
                <Mapbox.CircleLayer
                  id="field-summary-endpoint-circle"
                  style={{
                    circleRadius: 9,
                    // 출발은 속이 빈 원, 도착은 꽉 찬 원 — 러닝 앱의 관례를 따릅니다.
                    circleColor: ['case', ['get', 'isEnd'], TRACK_LINE_COLOR, '#FFFFFF'],
                    circleStrokeColor: ['case', ['get', 'isEnd'], '#FFFFFF', TRACK_LINE_COLOR],
                    circleStrokeWidth: 3.5,
                  }}
                />
                <Mapbox.SymbolLayer
                  id="field-summary-endpoint-label"
                  style={{
                    textField: ['get', 'label'],
                    textSize: 12,
                    textColor: DARK_GREEN_COLOR,
                    textHaloColor: '#FFFFFF',
                    textHaloWidth: 2,
                    textOffset: [0, 1.5],
                    textAnchor: 'top',
                    textAllowOverlap: true,
                  }}
                />
              </Mapbox.ShapeSource>
            )}
          </Mapbox.MapView>
        ) : (
          <View style={styles.emptyState}>
            <Ionicons color={MUTED_TEXT_COLOR} name="footsteps-outline" size={40} />
            <Text style={styles.emptyTitle}>기록된 경로가 없어요</Text>
            <Text style={styles.emptyText}>
              {'위치 권한이 꺼져 있었거나 이동 거리가 짧으면\n경로가 남지 않아요.'}
            </Text>
          </View>
        )}

        {/* 지도 위 RN 오버레이라 takeSnap 이미지에는 찍히지 않습니다. */}
        <GlassIconButton
          accessibilityLabel="뒤로 가기"
          onPress={goBackOrHome}
          style={[styles.backButton, { top: insets.top + 12 }]}
        >
          <Ionicons color={DARK_GREEN_COLOR} name="chevron-back" size={25} />
        </GlassIconButton>
      </View>

      <View style={[styles.card, { paddingBottom: insets.bottom + 20 }]}>
        <Text style={styles.eyebrow}>임장을 마쳤어요</Text>
        <Text numberOfLines={1} style={styles.title}>
          {params.apartmentName?.trim() ? `${params.apartmentName} 임장 경로` : '오늘의 임장 경로'}
        </Text>

        <View style={styles.statRow}>
          <SummaryStat label="이동 거리" value={formatDistance(distanceMeters)} />
          <View style={styles.statDivider} />
          <SummaryStat label="소요 시간" value={formatDuration(durationMs)} />
          <View style={styles.statDivider} />
          <SummaryStat
            label="평균 속도"
            value={speedKmh === null ? '-' : `${speedKmh.toFixed(1)}km/h`}
          />
        </View>

        <View style={styles.actionRow}>
          <Pressable
            accessibilityLabel="임장 경로 이미지를 갤러리에 저장"
            accessibilityRole="button"
            accessibilityState={{ disabled: !hasDrawableTrack || isSavingImage }}
            disabled={!hasDrawableTrack || isSavingImage}
            onPress={handleSaveImage}
            style={({ pressed }) => [
              styles.saveButton,
              pressed && styles.pressed,
              (!hasDrawableTrack || isSavingImage) && styles.disabled,
            ]}
          >
            {isSavingImage ? (
              <ActivityIndicator color={PRIMARY_COLOR} size="small" />
            ) : (
              <>
                <Ionicons color={PRIMARY_COLOR} name="download-outline" size={17} />
                <Text style={styles.saveButtonText}>이미지 저장</Text>
              </>
            )}
          </Pressable>

          <Pressable
            accessibilityLabel={isWaitingForTeammates ? '팀원 기다리기' : '리포트 보러가기'}
            accessibilityRole="button"
            onPress={handlePrimaryAction}
            style={({ pressed }) => [styles.primaryButton, pressed && styles.pressed]}
          >
            <Text style={styles.primaryButtonText}>
              {isWaitingForTeammates ? '팀원 기다리기' : '리포트 보러가기'}
            </Text>
            <Ionicons color={DARK_GREEN_COLOR} name="chevron-forward" size={17} />
          </Pressable>
        </View>
      </View>
    </View>
  );
}

function SummaryStat({ label, value }: { label: string; value: string }) {
  return (
    <View style={styles.stat}>
      <Text style={styles.statValue}>{value}</Text>
      <Text style={styles.statLabel}>{label}</Text>
    </View>
  );
}

const styles = StyleSheet.create({
  screen: { flex: 1, backgroundColor: SURFACE_COLOR },
  // 지도는 카드에 가리지 않는 자기 영역을 갖습니다 — 화면에 보이는 지도와 저장되는
  // 이미지가 같아야 하기 때문입니다.
  mapArea: { flex: 1, overflow: 'hidden' },
  map: { flex: 1 },
  backButton: { position: 'absolute', left: 16, zIndex: 5 },
  emptyState: {
    flex: 1,
    alignItems: 'center',
    justifyContent: 'center',
    gap: 10,
    paddingHorizontal: 32,
    backgroundColor: SOFT_GREEN_COLOR,
  },
  emptyTitle: {
    fontSize: 17,
    fontFamily: TAB_LABEL_FONT_BOLD,
    color: DARK_GREEN_COLOR,
  },
  emptyText: {
    fontSize: 13,
    lineHeight: 19,
    textAlign: 'center',
    color: MUTED_TEXT_COLOR,
  },
  card: {
    paddingHorizontal: 24,
    paddingTop: 20,
    borderTopWidth: 1,
    borderTopColor: BORDER_COLOR,
    backgroundColor: SURFACE_COLOR,
  },
  eyebrow: { fontSize: 12, fontWeight: '700', color: PRIMARY_COLOR },
  title: {
    marginTop: 4,
    fontSize: 22,
    lineHeight: 30,
    fontFamily: TAB_LABEL_FONT_BOLD,
    color: DARK_GREEN_COLOR,
    letterSpacing: -0.6,
  },
  statRow: {
    marginTop: 18,
    flexDirection: 'row',
    alignItems: 'center',
    paddingVertical: 14,
    borderRadius: 18,
    backgroundColor: SOFT_GREEN_COLOR,
  },
  stat: { flex: 1, alignItems: 'center', gap: 3 },
  statDivider: { width: 1, height: 26, backgroundColor: BORDER_COLOR },
  statValue: {
    fontSize: 18,
    fontFamily: TAB_LABEL_FONT_BOLD,
    color: DARK_GREEN_COLOR,
    letterSpacing: -0.4,
  },
  statLabel: { fontSize: 11, fontWeight: '600', color: MUTED_TEXT_COLOR },
  actionRow: { marginTop: 16, flexDirection: 'row', gap: 10 },
  saveButton: {
    height: 52,
    flexDirection: 'row',
    alignItems: 'center',
    justifyContent: 'center',
    gap: 6,
    paddingHorizontal: 18,
    borderRadius: 26,
    borderWidth: 1,
    borderColor: BORDER_COLOR,
    backgroundColor: SURFACE_COLOR,
  },
  saveButtonText: { fontSize: 14, fontWeight: '800', color: PRIMARY_COLOR },
  primaryButton: {
    flex: 1,
    height: 52,
    flexDirection: 'row',
    alignItems: 'center',
    justifyContent: 'center',
    gap: 4,
    borderRadius: 26,
    backgroundColor: BUTTON_BACKGROUND_COLOR,
  },
  primaryButtonText: { fontSize: 15, fontWeight: '800', color: DARK_GREEN_COLOR },
  disabled: { opacity: 0.45 },
  pressed: { opacity: 0.82 },
});
