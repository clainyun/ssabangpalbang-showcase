import Ionicons from '@expo/vector-icons/Ionicons';
import { Image } from 'expo-image';
import { LinearGradient } from 'expo-linear-gradient';
import { useEffect, useState, type ReactNode } from 'react';
import {
  ActivityIndicator,
  Platform,
  Pressable,
  StyleSheet,
  Text,
  useWindowDimensions,
  View,
} from 'react-native';
import Animated, {
  Easing,
  useAnimatedStyle,
  useSharedValue,
  withRepeat,
  withTiming,
} from 'react-native-reanimated';
import { useSafeAreaInsets } from 'react-native-safe-area-context';
import Svg, {
  Defs,
  LinearGradient as SvgLinearGradient,
  Mask,
  RadialGradient,
  Rect,
  Stop,
} from 'react-native-svg';

import {
  BUTTON_BACKGROUND_COLOR,
  DARK_GREEN_COLOR,
  LIKE_ACCENT_COLOR,
  SOFT_GREEN_COLOR,
} from '@/constants/colors';
import type {
  ApartmentDetail,
  ApartmentTransaction,
} from '@/features/apartment/api/apartmentDetail';
import { GlassIconButton } from '@/components/GlassIconButton';
import { GlassSurface } from '@/components/GlassSurface';
import { ApartmentFactBubbles } from '@/features/apartment/components/detail/ApartmentFactBubbles';
import { formatCompletionYearMonth } from '@/features/apartment/format';
import { apartmentImageSource } from '@/lib/apartmentImage';
import { ChatbotFab } from '@/features/chatbot/ChatbotFab';

const KNOWN_RAEMIAN_LAYOUT = require('../../../../../assets/images/apartments/raemian-oksu-riverzen-layout.png');

// 배경 이미지를 아주 천천히 위아래로 패닝한다(아파트마다 이미지 크기·초점 차이를 부드럽게 눅임).
// layoutStage가 overflow:hidden이라, 살짝 확대(SCALE)로 여백을 만든 뒤 그 안에서만 움직여
// 가장자리에 빈 공간이 드러나지 않게 한다.
const BG_PAN_UP = 32; // 위로 움직이는 최대 px (화면상으론 단지 하단이 드러나 '아래로 내려가는' 연출)
const BG_PAN_DOWN = 4; // 아래로 움직이는 최대 px
const BG_PAN_DURATION_MS = 4000; // 한 방향에 걸리는 시간(작을수록 빠름)
const BG_PAN_SCALE = 1.26; // 패닝 여백 확보용 확대(위로 크게 움직이므로 더 키움)

const SCENE_COLORS = {
  background: '#E7EBE6',
  title: '#15231B',
  report: '#804FD0',
  reportText: '#43266D',
  studyEnd: BUTTON_BACKGROUND_COLOR,
  studyStart: SOFT_GREEN_COLOR,
} as const;

type ApartmentDetailSceneProps = {
  detail: ApartmentDetail;
  favoritePending: boolean;
  onBack: () => void;
  onOpenChatbot: () => void;
  onOpenReports: () => void;
  onOpenStudies: () => void;
  onShare: () => void;
  onToggleFavorite: () => void;
  transactions: ApartmentTransaction[];
};

function normalizeApartmentName(name: string): string {
  return name.replaceAll(' ', '').toLocaleLowerCase('ko-KR');
}

function TitleMetaVeil() {
  return (
    <View pointerEvents="none" style={styles.titleMetaVeil}>
      <Svg
        height="100%"
        pointerEvents="none"
        preserveAspectRatio="none"
        style={StyleSheet.absoluteFill}
        viewBox="0 0 100 100"
        width="100%"
      >
        <Defs>
          <RadialGradient cx="19%" cy="48%" fx="19%" fy="48%" id="apartmentTitleMetaVeil" r="100%">
            <Stop offset="0%" stopColor="#FFFFFF" stopOpacity={0.58} />
            <Stop offset="42%" stopColor="#FFFFFF" stopOpacity={0.32} />
            <Stop offset="72%" stopColor="#FFFFFF" stopOpacity={0.17} />
            <Stop offset="100%" stopColor="#FFFFFF" stopOpacity={0.08} />
          </RadialGradient>
          <SvgLinearGradient id="apartmentTitleMetaMaskGradient" x1="0" x2="0" y1="0" y2="1">
            <Stop offset="0%" stopColor="#FFFFFF" stopOpacity={0} />
            <Stop offset="14%" stopColor="#FFFFFF" stopOpacity={1} />
            <Stop offset="90%" stopColor="#FFFFFF" stopOpacity={1} />
            <Stop offset="100%" stopColor="#FFFFFF" stopOpacity={0} />
          </SvgLinearGradient>
          <Mask
            height={100}
            id="apartmentTitleMetaMask"
            maskContentUnits="userSpaceOnUse"
            maskType="alpha"
            maskUnits="userSpaceOnUse"
            width={100}
            x={0}
            y={0}
          >
            <Rect
              fill="url(#apartmentTitleMetaMaskGradient)"
              height={100}
              width={100}
              x={0}
              y={0}
            />
          </Mask>
        </Defs>

        <Rect
          fill="url(#apartmentTitleMetaVeil)"
          height={100}
          mask="url(#apartmentTitleMetaMask)"
          width={100}
          x={0}
          y={0}
        />
      </Svg>
    </View>
  );
}

/**
 * 상단 뒤로 가기·공유·찜 버튼. 모양·크기·재질은 앱 공통 GlassIconButton 을 씁니다.
 *
 * 찜 여부는 하트 색으로 이미 드러나므로 유리에 민트 틴트까지 입히지는 않습니다.
 * 스크린리더에는 accessibilityLabel('찜하기'/'찜 해제')이 상태를 전합니다.
 */
function TopControl({
  accessibilityLabel,
  children,
  disabled = false,
  onPress,
}: {
  accessibilityLabel: string;
  children: ReactNode;
  disabled?: boolean;
  onPress: () => void;
}) {
  return (
    <GlassIconButton accessibilityLabel={accessibilityLabel} disabled={disabled} onPress={onPress}>
      {children}
    </GlassIconButton>
  );
}

export function ApartmentDetailScene({
  detail,
  favoritePending,
  onBack,
  onOpenChatbot,
  onOpenReports,
  onOpenStudies,
  onShare,
  onToggleFavorite,
  transactions,
}: ApartmentDetailSceneProps) {
  const insets = useSafeAreaInsets();
  const { height, width } = useWindowDimensions();
  // 로드에 실패한 서버 대표 이미지의 '쿼리 제거 경로'. Presigned GET URL은 요청마다
  // 서명·만료 쿼리가 바뀌므로, 전체 URL로 실패를 기억하면 서명이 회전한 같은 이미지를
  // 계속 새 이미지로 착각해 깨진 로드를 반복한다. 캐시 키도 같은 경로로 고정해
  // 서명이 바뀌어도 expo-image 캐시가 적중되게 한다(커뮤니티 목록과 동일 패턴).
  const [failedServerImagePath, setFailedServerImagePath] = useState<string | null>(null);
  const serverImageUrl = detail.imageUrl?.trim() || null;
  const serverImagePath = serverImageUrl?.split('?')[0] ?? null;
  const hasServerImage = serverImageUrl !== null && failedServerImagePath !== serverImagePath;
  const hasKnownLayout = normalizeApartmentName(detail.name) === '래미안옥수리버젠';
  const hasApartmentLayout = hasServerImage || hasKnownLayout;
  const backgroundSource = hasServerImage
    ? { uri: serverImageUrl, cacheKey: serverImagePath ?? undefined }
    : hasKnownLayout
      ? KNOWN_RAEMIAN_LAYOUT
      : apartmentImageSource(detail.apartmentId);

  // 아주 느린 상하 패닝(위로 더 많이 치우친 왕복).
  const bgPan = useSharedValue(0);
  useEffect(() => {
    bgPan.value = withRepeat(
      withTiming(1, { duration: BG_PAN_DURATION_MS, easing: Easing.inOut(Easing.sin) }),
      -1,
      true,
    );
  }, [bgPan]);
  // bgPan 0→1: 아래 끝(+DOWN) → 위 끝(-UP).
  const bgPanStyle = useAnimatedStyle(() => ({
    transform: [
      { translateY: BG_PAN_DOWN - bgPan.value * (BG_PAN_UP + BG_PAN_DOWN) },
      { scale: BG_PAN_SCALE },
    ],
  }));
  const completionLabel =
    detail.completionYearMonth === null
      ? '준공 정보 없음'
      : `${formatCompletionYearMonth(detail.completionYearMonth)} 준공`;
  const compactScene = height < 840 || width < 390;
  const layoutStageTop = Math.max(insets.top + (compactScene ? 146 : 160), height * 0.2);
  const layoutStageWidth = width + 18;
  const transactionBubbleWidth = Math.min(
    compactScene ? 198 : 220,
    Math.max(compactScene ? 184 : 208, width * 0.51),
  );
  const rightFadeWidth = transactionBubbleWidth + 6;
  const layoutStageHeight = Math.max(
    compactScene ? 620 : 680,
    height - layoutStageTop - Math.max(insets.bottom + 18, 24),
  );

  return (
    <View style={styles.container}>
      <View
        pointerEvents="none"
        style={[
          styles.layoutStage,
          {
            top: layoutStageTop,
            width: layoutStageWidth,
            height: layoutStageHeight,
          },
        ]}
      >
        <Animated.View pointerEvents="none" style={[StyleSheet.absoluteFill, bgPanStyle]}>
          <Image
            accessible={false}
            accessibilityIgnoresInvertColors
            onError={
              hasServerImage && serverImagePath !== null
                ? () => setFailedServerImagePath(serverImagePath)
                : undefined
            }
            contentFit="contain"
            cachePolicy="memory-disk"
            source={backgroundSource}
            style={[
              styles.layoutImage,
              hasApartmentLayout ? styles.knownLayoutImage : styles.fallbackLayoutImage,
            ]}
          />
        </Animated.View>
        <LinearGradient
          colors={[
            'rgba(231,235,230,1)',
            'rgba(231,235,230,0.91)',
            'rgba(231,235,230,0.62)',
            'rgba(231,235,230,0.28)',
            'rgba(231,235,230,0.08)',
            'rgba(231,235,230,0)',
          ]}
          end={{ x: 0, y: 1 }}
          locations={[0, 0.12, 0.34, 0.58, 0.8, 1]}
          start={{ x: 0, y: 0 }}
          style={styles.layoutTopFade}
        />
        <LinearGradient
          colors={[
            'rgba(231,235,230,0)',
            'rgba(231,235,230,0.50)',
            'rgba(231,235,230,0.92)',
            'rgba(231,235,230,1)',
            'rgba(231,235,230,1)',
          ]}
          end={{ x: 1, y: 0 }}
          locations={[0, 0.18, 0.42, 0.56, 1]}
          start={{ x: 0, y: 0 }}
          style={[styles.layoutRightFade, { width: rightFadeWidth }]}
        />
        <LinearGradient
          colors={['rgba(231,235,230,0)', 'rgba(231,235,230,0.52)', 'rgba(231,235,230,0.88)']}
          end={{ x: 0, y: 1 }}
          locations={[0, 0.58, 1]}
          start={{ x: 0, y: 0 }}
          style={styles.layoutBottomFade}
        />
      </View>

      <LinearGradient
        colors={[
          'rgba(248,250,248,0.94)',
          'rgba(248,250,248,0.66)',
          'rgba(248,250,248,0.12)',
          'rgba(248,250,248,0)',
          'rgba(248,250,248,0)',
          'rgba(245,249,246,0.18)',
          'rgba(245,249,246,0.58)',
        ]}
        locations={[0, 0.12, 0.24, 0.38, 0.76, 0.9, 1]}
        pointerEvents="none"
        style={StyleSheet.absoluteFill}
      />
      <LinearGradient
        colors={[
          'rgba(247,250,248,0)',
          'rgba(247,250,248,0.03)',
          'rgba(247,250,248,0.11)',
          'rgba(231,235,230,0.26)',
        ]}
        locations={[0, 0.5, 0.76, 1]}
        end={{ x: 1, y: 0 }}
        pointerEvents="none"
        start={{ x: 0, y: 0 }}
        style={[styles.factRailHaze, { top: layoutStageTop - 24 }]}
      />

      <View style={[styles.topControls, { top: insets.top + 8 }]}>
        <TopControl accessibilityLabel="뒤로 가기" onPress={onBack}>
          <Ionicons color={DARK_GREEN_COLOR} name="chevron-back" size={25} />
        </TopControl>

        <View style={styles.topControlsRight}>
          <TopControl accessibilityLabel={`${detail.name} 공유하기`} onPress={onShare}>
            <Ionicons color={DARK_GREEN_COLOR} name="share-social-outline" size={22} />
          </TopControl>
          <TopControl
            accessibilityLabel={detail.favoritedByMe ? '아파트 찜 해제' : '아파트 찜하기'}
            disabled={favoritePending}
            onPress={onToggleFavorite}
          >
            {favoritePending ? (
              <ActivityIndicator color={LIKE_ACCENT_COLOR} size="small" />
            ) : (
              <Ionicons
                color={detail.favoritedByMe ? LIKE_ACCENT_COLOR : DARK_GREEN_COLOR}
                name={detail.favoritedByMe ? 'heart' : 'heart-outline'}
                size={23}
              />
            )}
          </TopControl>
        </View>
      </View>

      <View style={[styles.titleArea, { top: insets.top + 60 }]}>
        <TitleMetaVeil />
        <View style={styles.titleVeil}>
          <Text
            adjustsFontSizeToFit
            minimumFontScale={0.76}
            numberOfLines={1}
            style={styles.apartmentName}
          >
            {detail.name}
          </Text>
        </View>

        <View style={styles.metaVeil}>
          <View style={[styles.metaItem, styles.addressMetaItem]}>
            <Ionicons color="rgba(21,35,27,0.68)" name="location-outline" size={12} />
            <Text numberOfLines={1} style={styles.metaText}>
              {detail.address?.trim() || '주소 정보 없음'}
            </Text>
          </View>
          <View style={[styles.metaItem, styles.completionMetaItem]}>
            <Ionicons color="rgba(21,35,27,0.68)" name="calendar-outline" size={12} />
            <Text numberOfLines={1} style={styles.metaText}>
              {completionLabel}
            </Text>
          </View>
        </View>
      </View>

      <ApartmentFactBubbles detail={detail} transactions={transactions} />

      {/* 임장 지도와 동일한 드래그+탭 챗봇 FAB(팔방이 스프라이트). 탭하면 상담이 열리고 드래그로 이동한다. */}
      <ChatbotFab
        bottom={Math.max(178, insets.bottom + 168)}
        onPress={onOpenChatbot}
        right={16}
      />

      <View style={[styles.actionsRow, { bottom: Math.max(98, insets.bottom + 88) }]}>
        <Pressable
          accessibilityLabel={`함께 임장하기, 모집 스터디 ${detail.recruitingStudyCount ?? 0}개`}
          accessibilityRole="button"
          onPress={onOpenStudies}
          style={({ pressed }) => [
            styles.actionButton,
            styles.studyAction,
            pressed && styles.actionPressed,
          ]}
        >
          {/* 색은 초록으로 유지하되 유리(블러+틴트+rim) 재질로. */}
          <GlassSurface blurTint="light" radius={21} showRim tint="#13B26E" tintOpacity={0.22} />
          <View style={[styles.actionIconTile, styles.studyIconTile]}>
            <Ionicons color={DARK_GREEN_COLOR} name="people-outline" size={19} />
          </View>
          <View style={styles.actionCopy}>
            <Text numberOfLines={1} style={styles.studyActionTitle}>
              함께 임장하기
            </Text>
            <Text numberOfLines={1} style={styles.studyActionMeta}>
              {`스터디 ${detail.recruitingStudyCount ?? 0}개`}
            </Text>
          </View>
        </Pressable>

        <Pressable
          accessibilityLabel={`현장 기록 보기, 완료 리포트 ${detail.completedReportCount ?? 0}개`}
          accessibilityRole="button"
          onPress={onOpenReports}
          style={({ pressed }) => [
            styles.actionButton,
            styles.reportAction,
            pressed && styles.actionPressed,
          ]}
        >
          {/* 색은 보라로 유지하되 유리(블러+틴트+rim) 재질로. */}
          <GlassSurface blurTint="light" radius={21} showRim tint="#804FD0" tintOpacity={0.2} />
          <View style={[styles.actionIconTile, styles.reportIconTile]}>
            <Ionicons color={SCENE_COLORS.report} name="document-text-outline" size={19} />
          </View>
          <View style={styles.actionCopy}>
            <Text numberOfLines={1} style={styles.reportActionTitle}>
              현장 기록 보기
            </Text>
            <Text numberOfLines={1} style={styles.reportActionMeta}>
              {`리포트 ${detail.completedReportCount ?? 0}개`}
            </Text>
          </View>
        </Pressable>
      </View>

    </View>
  );
}

const styles = StyleSheet.create({
  container: {
    flex: 1,
    overflow: 'hidden',
    backgroundColor: SCENE_COLORS.background,
  },
  layoutStage: {
    position: 'absolute',
    left: -18,
    zIndex: 0,
    overflow: 'hidden',
  },
  layoutImage: {
    position: 'absolute',
    top: 0,
    bottom: 0,
    left: 0,
    width: '100%',
    height: '100%',
  },
  knownLayoutImage: {
    left: -20,
    opacity: 1,
    width: '90%',
  },
  fallbackLayoutImage: {
    opacity: 0.48,
    transform: [{ scale: 1.08 }],
  },
  layoutTopFade: {
    position: 'absolute',
    top: 0,
    right: 0,
    left: 0,
    height: 150,
  },
  layoutRightFade: {
    position: 'absolute',
    top: 0,
    right: 0,
    bottom: 0,
  },
  layoutBottomFade: {
    position: 'absolute',
    right: 0,
    bottom: 0,
    left: 0,
    height: 72,
  },
  factRailHaze: {
    position: 'absolute',
    right: 0,
    bottom: 130,
    left: '38%',
    zIndex: 1,
  },
  topControls: {
    position: 'absolute',
    left: 16,
    right: 16,
    zIndex: 20,
    flexDirection: 'row',
    alignItems: 'center',
    justifyContent: 'space-between',
  },
  topControlsRight: {
    flexDirection: 'row',
    gap: 8,
  },
  titleArea: {
    position: 'absolute',
    left: 0,
    right: 0,
    zIndex: 10,
  },
  titleMetaVeil: {
    position: 'absolute',
    top: -14,
    right: 0,
    bottom: -7,
    left: 0,
  },
  titleVeil: {
    position: 'relative',
    zIndex: 1,
    width: '100%',
    paddingTop: 8,
    paddingRight: 54,
    paddingBottom: 12,
    paddingLeft: 16,
  },
  apartmentName: {
    fontSize: 32,
    lineHeight: 34,
    fontWeight: '900',
    color: SCENE_COLORS.title,
    letterSpacing: -2,
    textShadowColor: 'rgba(255,255,255,0.78)',
    textShadowOffset: { width: 0, height: 3 },
    textShadowRadius: 12,
  },
  metaVeil: {
    position: 'relative',
    zIndex: 1,
    width: '100%',
    flexDirection: 'column',
    alignItems: 'flex-start',
    justifyContent: 'flex-start',
    gap: 2,
    marginTop: 0,
    paddingTop: 4,
    paddingRight: 16,
    paddingBottom: 6,
    paddingLeft: 16,
  },
  metaItem: {
    minWidth: 0,
    flexDirection: 'row',
    alignItems: 'center',
    gap: 7,
  },
  addressMetaItem: {
    maxWidth: '92%',
    flexShrink: 1,
  },
  completionMetaItem: { flexShrink: 0 },
  metaText: {
    flexShrink: 1,
    fontSize: 12,
    lineHeight: 16,
    fontWeight: '800',
    color: '#26372D',
    letterSpacing: -0.15,
    textShadowColor: 'rgba(255,255,255,0.86)',
    textShadowOffset: { width: 0, height: 1 },
    textShadowRadius: 7,
  },
  actionsRow: {
    position: 'absolute',
    left: 14,
    right: 14,
    zIndex: 21,
    flexDirection: 'row',
    gap: 12,
  },
  actionButton: {
    minWidth: 0,
    minHeight: 64,
    flex: 1,
    flexDirection: 'row',
    alignItems: 'center',
    gap: 8,
    overflow: 'hidden',
    paddingHorizontal: 13,
    borderRadius: 21,
    borderWidth: 1,
    // iOS는 기존 그림자 유지. 안드로이드는 elevation이 transparent+borderRadius 뷰에서
    // 사각 바운딩 박스 그림자(흰 네모)를 그리는 버그가 있어, borderRadius를 존중하는
    // boxShadow로 대체한다(New Arch/RN 0.76+, 탭바·커뮤니티와 동일한 해결 방식).
    ...Platform.select({
      ios: {
        shadowColor: DARK_GREEN_COLOR,
        shadowOffset: { width: 0, height: 7 },
        shadowOpacity: 0.13,
        shadowRadius: 13,
      },
      android: {
        boxShadow: '0px 7px 13px rgba(16,39,30,0.18)',
      },
    }),
  },
  studyAction: {
    borderColor: 'rgba(19,178,110,0.28)',
    backgroundColor: 'transparent',
  },
  reportAction: {
    borderColor: 'rgba(128,79,208,0.28)',
    backgroundColor: 'transparent',
  },
  actionPressed: { opacity: 0.8, transform: [{ scale: 0.98 }] },
  actionCopy: { minWidth: 0, flex: 1 },
  actionIconTile: {
    width: 34,
    height: 34,
    alignItems: 'center',
    justifyContent: 'center',
    borderRadius: 12,
  },
  studyIconTile: { backgroundColor: 'rgba(19,178,110,0.11)' },
  reportIconTile: { backgroundColor: 'rgba(128,79,208,0.16)' },
  studyActionTitle: {
    fontSize: 11,
    lineHeight: 15,
    fontWeight: '700',
    color: DARK_GREEN_COLOR,
    letterSpacing: -0.45,
  },
  studyActionMeta: {
    fontSize: 12,
    lineHeight: 17,
    fontWeight: '900',
    color: 'rgba(16,39,30,0.74)',
  },
  reportActionTitle: {
    fontSize: 11,
    lineHeight: 15,
    fontWeight: '700',
    color: SCENE_COLORS.reportText,
    letterSpacing: -0.45,
  },
  reportActionMeta: {
    fontSize: 12,
    lineHeight: 17,
    fontWeight: '900',
    color: 'rgba(67,38,109,0.84)',
  },
});
