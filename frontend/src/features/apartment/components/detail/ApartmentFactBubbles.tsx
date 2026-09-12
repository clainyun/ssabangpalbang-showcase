import Ionicons from '@expo/vector-icons/Ionicons';
import { BlurView } from 'expo-blur';
import { LinearGradient } from 'expo-linear-gradient';
import type { ComponentProps, ReactNode } from 'react';
import {
  StyleSheet,
  Text,
  type StyleProp,
  useWindowDimensions,
  View,
  type ViewStyle,
} from 'react-native';
import { useSafeAreaInsets } from 'react-native-safe-area-context';
import Svg, {
  Circle,
  Defs,
  LinearGradient as SvgLinearGradient,
  Path,
  Stop,
  Text as SvgText,
} from 'react-native-svg';

import type {
  ApartmentDetail,
  ApartmentTransaction,
} from '@/features/apartment/api/apartmentDetail';
import {
  formatDealDate,
  formatFloor,
  formatParkingPerHousehold,
  formatPriceDetail,
} from '@/features/apartment/format';

type BubbleTone = 'jade' | 'coral' | 'blue';
type IoniconName = ComponentProps<typeof Ionicons>['name'];

const BUBBLE_TONES = {
  jade: {
    accent: '#1A9B6B',
    label: '#2F7C50',
    depth: 'rgba(15,23,20,0.02)',
    glassGradient: ['rgba(255,255,255,0.18)', 'rgba(255,255,255,0.075)', 'rgba(255,255,255,0.02)'],
    iconGradient: ['rgba(43,190,137,0.98)', 'rgba(16,113,81,0.82)'],
  },
  coral: {
    accent: '#F66828',
    label: '#C94916',
    depth: 'rgba(15,23,20,0.02)',
    glassGradient: ['rgba(255,255,255,0.18)', 'rgba(255,255,255,0.075)', 'rgba(255,255,255,0.02)'],
    iconGradient: ['#FF7E3F', '#D94910'],
  },
  blue: {
    accent: '#2F99E4',
    label: '#176A9F',
    depth: 'rgba(15,23,20,0.02)',
    glassGradient: ['rgba(255,255,255,0.18)', 'rgba(255,255,255,0.075)', 'rgba(255,255,255,0.02)'],
    iconGradient: ['rgba(83,185,246,0.98)', 'rgba(30,119,187,0.82)'],
  },
} as const;

const TEXT_COLORS = {
  primary: '#10271F',
  secondary: '#52635B',
} as const;

const MAX_TREND_POINTS = 6;
const CHART_WIDTH = 184;
const CHART_HEIGHT = 66;
const PLOT_LEFT = 35;
const PLOT_RIGHT = 178;
const PLOT_TOP = 5;
const PLOT_BOTTOM = 44;

type TrendTransaction = ApartmentTransaction & { price: number };

type ApartmentFactBubblesProps = {
  detail: ApartmentDetail;
  transactions: ApartmentTransaction[];
};

function clamp(value: number, minimum: number, maximum: number): number {
  return Math.min(maximum, Math.max(minimum, value));
}

function isTrendTransaction(transaction: ApartmentTransaction): transaction is TrendTransaction {
  return (
    typeof transaction.price === 'number' &&
    Number.isFinite(transaction.price) &&
    !Number.isNaN(Date.parse(`${transaction.dealDate}T00:00:00Z`))
  );
}

function selectTrendTransactions(transactions: ApartmentTransaction[]): TrendTransaction[] {
  const ordered = transactions
    .filter(isTrendTransaction)
    .sort(
      (left, right) =>
        left.dealDate.localeCompare(right.dealDate) || left.transactionId - right.transactionId,
    );

  if (ordered.length < 2) return ordered;

  const latestTransaction = ordered.at(-1);
  if (!latestTransaction) return [];

  const latestDate = new Date(`${latestTransaction.dealDate}T00:00:00Z`);
  const cutoff = new Date(latestDate);
  cutoff.setUTCFullYear(cutoff.getUTCFullYear() - 1);
  const recent = ordered.filter(
    (transaction) => Date.parse(`${transaction.dealDate}T00:00:00Z`) >= cutoff.getTime(),
  );
  const source = recent.length >= 2 ? recent : ordered;

  if (source.length <= MAX_TREND_POINTS) return source;

  const firstTransaction = source[0];
  if (!firstTransaction) return [];

  return Array.from({ length: MAX_TREND_POINTS }, (_, index) => {
    const sourceIndex = Math.round((index * (source.length - 1)) / (MAX_TREND_POINTS - 1));
    return source[sourceIndex] ?? firstTransaction;
  });
}

function formatAxisPrice(price: number): string {
  const eok = price / 10_000;
  const rounded = eok.toFixed(1).replace(/\.0$/, '');
  return `${rounded}억`;
}

function formatChartMonth(dealDate: string): string {
  const [year, month] = dealDate.split('-');
  return year && month ? `'${year.slice(-2)}.${month}` : dealDate;
}

function formatTrendAmount(delta: number): string {
  const eok = Math.abs(delta) / 10_000;
  return `${eok.toFixed(1).replace(/\.0$/, '')}억`;
}

function TransactionTrendChart({ points }: { points: TrendTransaction[] }) {
  const firstPoint = points[0];
  const lastPoint = points.at(-1);
  if (!firstPoint || !lastPoint) return null;

  const prices = points.map((point) => point.price);
  const minimumPrice = Math.min(...prices);
  const maximumPrice = Math.max(...prices);
  const priceRange = Math.max(maximumPrice - minimumPrice, 1);
  const coordinates = points.map((point, index) => ({
    dealDate: point.dealDate,
    transactionId: point.transactionId,
    x:
      points.length === 1
        ? (PLOT_LEFT + PLOT_RIGHT) / 2
        : PLOT_LEFT + (index * (PLOT_RIGHT - PLOT_LEFT)) / (points.length - 1),
    y: PLOT_TOP + ((maximumPrice - point.price) * (PLOT_BOTTOM - PLOT_TOP)) / priceRange,
  }));
  const linePath = coordinates
    .map((point, index) => `${index === 0 ? 'M' : 'L'} ${point.x} ${point.y}`)
    .join(' ');
  const firstCoordinate = coordinates[0];
  const lastCoordinate = coordinates.at(-1);
  if (!firstCoordinate || !lastCoordinate) return null;

  const areaPath = `${linePath} L ${lastCoordinate.x} ${PLOT_BOTTOM} L ${firstCoordinate.x} ${PLOT_BOTTOM} Z`;
  const labelIndexes = Array.from(
    new Set([0, Math.floor((points.length - 1) / 2), points.length - 1]),
  );
  const delta = lastPoint.price - firstPoint.price;
  const trendColor = delta < 0 ? '#2F78A6' : '#C96B24';
  const trendIcon = delta < 0 ? 'arrow-down' : delta > 0 ? 'arrow-up' : 'remove';
  const trendText =
    delta === 0 ? '변동 없음' : `${delta > 0 ? '+' : '-'} ${formatTrendAmount(delta)}`;

  return (
    <View style={styles.transactionTrend}>
      <Svg height={CHART_HEIGHT} viewBox={`0 0 ${CHART_WIDTH} ${CHART_HEIGHT}`} width="100%">
        <Defs>
          <SvgLinearGradient id="transaction-area" x1="0" x2="0" y1="0" y2="1">
            <Stop offset="0" stopColor="#F28A4B" stopOpacity="0.28" />
            <Stop offset="1" stopColor="#F28A4B" stopOpacity="0" />
          </SvgLinearGradient>
        </Defs>
        <SvgText fill="#31463D" fontSize="8.8" fontWeight="800" x="0" y="11">
          {formatAxisPrice(maximumPrice)}
        </SvgText>
        <SvgText fill="#31463D" fontSize="8.8" fontWeight="800" x="0" y={PLOT_BOTTOM + 2}>
          {formatAxisPrice(minimumPrice)}
        </SvgText>
        <Path d={areaPath} fill="url(#transaction-area)" />
        <Path
          d={linePath}
          fill="none"
          stroke="#F28A4B"
          strokeLinecap="round"
          strokeLinejoin="round"
          strokeWidth="1.8"
        />
        {coordinates.map((point, index) => (
          <Circle
            key={point.transactionId}
            cx={point.x}
            cy={point.y}
            fill="#F28A4B"
            r="3.4"
            stroke="#FFFFFF"
            strokeWidth="1.4"
          />
        ))}
        {labelIndexes.map((index) => {
          const coordinate = coordinates[index];
          if (!coordinate) return null;

          return (
            <SvgText
              key={coordinate.transactionId}
              fill="#31463D"
              fontSize="8.8"
              fontWeight="800"
              textAnchor={index === 0 ? 'start' : index === points.length - 1 ? 'end' : 'middle'}
              x={coordinate.x}
              y="61"
            >
              {formatChartMonth(coordinate.dealDate)}
            </SvgText>
          );
        })}
      </Svg>
      <View style={styles.trendPill}>
        <Ionicons color={trendColor} name={trendIcon} size={12} />
        <Text style={[styles.trendPillText, { color: trendColor }]}>{trendText}</Text>
        <Text style={styles.trendPeriod}>(최근 1년)</Text>
      </View>
    </View>
  );
}

function GlassBubbleBackdrop({ tone }: { tone: BubbleTone }) {
  const colors = BUBBLE_TONES[tone];

  return (
    <BlurView
      intensity={18}
      pointerEvents="none"
      style={StyleSheet.absoluteFill}
      tint="default"
    >
      <LinearGradient
        colors={colors.glassGradient}
        end={{ x: 0.92, y: 0.9 }}
        start={{ x: 0.06, y: 0.04 }}
        style={StyleSheet.absoluteFill}
      />
      <LinearGradient
        colors={['rgba(255,255,255,0)', colors.depth]}
        end={{ x: 0, y: 1 }}
        start={{ x: 0, y: 0 }}
        style={styles.bottomDepth}
      />
    </BlurView>
  );
}

function FactIcon({
  glyphSize,
  icon,
  size,
  symbol,
  tone,
}: {
  glyphSize?: number;
  icon?: IoniconName;
  size: number;
  symbol?: string;
  tone: BubbleTone;
}) {
  const colors = BUBBLE_TONES[tone];

  return (
    <View
      accessible={false}
      style={[
        styles.factIconShadow,
        {
          width: size,
          height: size,
          borderRadius: size / 2,
          shadowColor: colors.accent,
        },
      ]}
    >
      <View style={[styles.factIcon, { borderRadius: size / 2 }]}>
        <LinearGradient
          colors={colors.iconGradient}
          end={{ x: 0.85, y: 0.92 }}
          start={{ x: 0.12, y: 0.08 }}
          style={[StyleSheet.absoluteFill, { borderRadius: size / 2 }]}
        />
        <View style={styles.iconGlint} />
        {symbol === undefined ? (
          <Ionicons
            color="#FFFFFF"
            name={icon ?? 'ellipse-outline'}
            size={glyphSize ?? size * 0.56}
          />
        ) : (
          <Text style={[styles.iconSymbol, { fontSize: glyphSize ?? size * 0.56 }]}>{symbol}</Text>
        )}
      </View>
    </View>
  );
}

function BubbleFrame({
  accessibilityLabel,
  children,
  innerStyle,
  style,
  tone,
}: {
  accessibilityLabel: string;
  children: ReactNode;
  innerStyle: StyleProp<ViewStyle>;
  style: StyleProp<ViewStyle>;
  tone: BubbleTone;
}) {
  return (
    <View accessible accessibilityLabel={accessibilityLabel} style={[styles.bubbleWrapper, style]}>
      <View pointerEvents="none" style={[styles.edgeGlowFar, innerStyle]} />
      <View pointerEvents="none" style={[styles.edgeGlowNear, innerStyle]} />
      <View style={[styles.bubbleInner, innerStyle]}>
        <GlassBubbleBackdrop tone={tone} />
        {children}
      </View>
    </View>
  );
}

export function ApartmentFactBubbles({ detail, transactions }: ApartmentFactBubblesProps) {
  const { height, width } = useWindowDimensions();
  const insets = useSafeAreaInsets();
  const compact = height < 840 || width < 390;
  const factsTop = Math.max(insets.top + (compact ? 128 : 142), height * 0.174);
  const transactionWidth = clamp(width * 0.51, compact ? 184 : 208, compact ? 198 : 220);
  const householdWidth = clamp(width * 0.39, compact ? 150 : 158, compact ? 166 : 176);
  const parkingWidth = clamp(width * 0.43, compact ? 160 : 170, compact ? 176 : 188);
  const transactionHeight = compact ? 180 : 194;
  const householdHeight = compact ? 72 : 78;
  const parkingHeight = compact ? 80 : 86;
  const bubbleGap = compact ? 24 : 28;
  const transactionTop = factsTop;
  const householdTop = transactionTop + transactionHeight + bubbleGap;
  const parkingTop = householdTop + householdHeight + bubbleGap;

  const transaction =
    detail.latestTransactionAvailable && detail.latestTransaction !== null
      ? detail.latestTransaction
      : null;
  const transactionPrice =
    transaction?.price === null || transaction?.price === undefined
      ? null
      : formatPriceDetail(transaction.price);
  const transactionArea =
    transaction?.exclusiveArea === null || transaction?.exclusiveArea === undefined
      ? null
      : `전용 ${transaction.exclusiveArea}㎡`;
  const transactionDate =
    transaction?.dealDate === null || transaction?.dealDate === undefined
      ? null
      : formatDealDate(transaction.dealDate);
  const trendTransactions = selectTrendTransactions(transactions);
  const parkingPerHousehold = formatParkingPerHousehold(detail.parkingSpacesPerHousehold);
  const parkingTotal =
    detail.parkingSpaceCount === null ? null : `총 ${detail.parkingSpaceCount.toLocaleString()}대`;

  return (
    <View pointerEvents="none" style={styles.bubbleLayer}>
      <BubbleFrame
        accessibilityLabel={
          transaction === null
            ? '최근 실거래 정보 없음'
            : `최근 실거래 ${transactionPrice ?? '가격 정보 없음'}, ${transactionArea ?? '면적 정보 없음'}, ${formatFloor(transaction.floor)}, ${transactionDate ?? '거래일 정보 없음'}, 최근 1년 거래 추이 ${trendTransactions.length}건`
        }
        innerStyle={styles.transactionInner}
        style={[
          styles.transactionWrapper,
          {
            top: transactionTop,
            width: transactionWidth,
            height: transactionHeight,
          },
        ]}
        tone="coral"
      >
        <View style={styles.transactionTopRow}>
          <FactIcon glyphSize={23} size={40} symbol="₩" tone="coral" />
          <View style={styles.transactionCopy}>
            <Text style={[styles.eyebrow, { color: BUBBLE_TONES.coral.label }]}>최근 실거래</Text>
            <Text
              adjustsFontSizeToFit
              minimumFontScale={0.7}
              numberOfLines={1}
              style={styles.transactionValue}
            >
              {transaction === null ? '거래 정보 없음' : (transactionPrice ?? '가격 정보 없음')}
            </Text>
            {transaction !== null ? (
              <Text numberOfLines={1} style={styles.transactionMetaPrimary}>
                {[transactionArea ?? '면적 정보 없음', formatFloor(transaction.floor)].join(' · ')}
              </Text>
            ) : null}
          </View>
        </View>
        {transaction === null ? (
          <View style={styles.transactionEmpty}>
            <Text numberOfLines={2} style={styles.transactionCaption}>
              최근 정상 거래가 없어요
            </Text>
          </View>
        ) : trendTransactions.length >= 2 ? (
          <TransactionTrendChart points={trendTransactions} />
        ) : (
          <View style={styles.transactionEmpty}>
            <Text numberOfLines={1} style={styles.transactionCaption}>
              {transactionDate === null ? '거래일 정보 없음' : `${transactionDate} 거래`}
            </Text>
            <Text numberOfLines={1} style={styles.transactionEmptyHint}>
              추이 비교를 위한 거래가 더 필요해요
            </Text>
          </View>
        )}
      </BubbleFrame>

      <BubbleFrame
        accessibilityLabel={
          detail.householdCount === null
            ? '단지 규모, 세대수 정보 없음'
            : `단지 규모, ${detail.householdCount.toLocaleString()}세대`
        }
        innerStyle={styles.householdInner}
        style={[
          styles.householdWrapper,
          {
            top: householdTop,
            width: householdWidth,
            height: householdHeight,
          },
        ]}
        tone="jade"
      >
        <View style={styles.householdContent}>
          <FactIcon glyphSize={21} icon="people-outline" size={38} tone="jade" />
          <View style={styles.householdCopy}>
            <Text style={[styles.eyebrow, { color: BUBBLE_TONES.jade.label }]}>단지 규모</Text>
            <Text
              adjustsFontSizeToFit
              minimumFontScale={0.8}
              numberOfLines={1}
              style={styles.householdValue}
            >
              {detail.householdCount === null
                ? '정보 없음'
                : `${detail.householdCount.toLocaleString()}세대`}
            </Text>
          </View>
        </View>
      </BubbleFrame>

      <BubbleFrame
        accessibilityLabel={`세대당 주차 ${parkingPerHousehold ?? '정보 없음'}, ${parkingTotal ?? '총 주차대수 정보 없음'}`}
        innerStyle={styles.parkingInner}
        style={[
          styles.parkingWrapper,
          {
            top: parkingTop,
            width: parkingWidth,
            height: parkingHeight,
          },
        ]}
        tone="blue"
      >
        <View style={styles.parkingContent}>
          <FactIcon glyphSize={22} icon="car-outline" size={38} tone="blue" />
          <View style={styles.parkingCopy}>
            <Text style={[styles.eyebrow, { color: BUBBLE_TONES.blue.label }]}>세대당 주차</Text>
            <Text
              adjustsFontSizeToFit
              minimumFontScale={0.8}
              numberOfLines={1}
              style={styles.parkingValue}
            >
              {parkingPerHousehold ?? '정보 없음'}
            </Text>
            <Text numberOfLines={1} style={styles.factSubline}>
              {parkingTotal ?? '총 주차대수 정보 없음'}
            </Text>
          </View>
        </View>
      </BubbleFrame>
    </View>
  );
}

const styles = StyleSheet.create({
  bubbleLayer: {
    position: 'absolute',
    top: 0,
    right: 0,
    bottom: 0,
    left: 0,
    zIndex: 6,
  },
  bubbleWrapper: {
    position: 'absolute',
    overflow: 'visible',
    shadowColor: '#16372B',
    shadowOffset: { width: 0, height: 7 },
    shadowOpacity: 0.08,
    shadowRadius: 16,
  },
  bubbleInner: {
    zIndex: 1,
    width: '100%',
    height: '100%',
    overflow: 'hidden',
    borderWidth: 1,
    borderColor: 'rgba(255,255,255,0.82)',
  },
  edgeGlowFar: {
    position: 'absolute',
    top: -5,
    right: -5,
    bottom: -5,
    left: -5,
    borderWidth: 4,
    borderColor: 'rgba(255,255,255,0.14)',
    backgroundColor: 'rgba(255,255,255,0.01)',
    boxShadow: '0 0 18px rgba(255,255,255,0.82)',
    shadowColor: '#FFFFFF',
    shadowOffset: { width: 0, height: 0 },
    shadowOpacity: 0.82,
    shadowRadius: 18,
  },
  edgeGlowNear: {
    position: 'absolute',
    top: -2,
    right: -2,
    bottom: -2,
    left: -2,
    borderWidth: 2,
    borderColor: 'rgba(255,255,255,0.58)',
    backgroundColor: 'rgba(255,255,255,0.01)',
    boxShadow: '0 0 9px rgba(255,255,255,0.94)',
    shadowColor: '#FFFFFF',
    shadowOffset: { width: 0, height: 0 },
    shadowOpacity: 0.94,
    shadowRadius: 9,
  },
  householdWrapper: {
    right: 16,
    borderTopLeftRadius: 30,
    borderTopRightRadius: 26,
    borderBottomRightRadius: 34,
    borderBottomLeftRadius: 27,
  },
  householdInner: {
    flexDirection: 'row',
    alignItems: 'center',
    gap: 9,
    paddingTop: 7,
    paddingRight: 13,
    paddingBottom: 6,
    paddingLeft: 13,
    borderTopLeftRadius: 30,
    borderTopRightRadius: 26,
    borderBottomRightRadius: 34,
    borderBottomLeftRadius: 27,
  },
  transactionWrapper: {
    right: 6,
    borderTopLeftRadius: 42,
    borderTopRightRadius: 36,
    borderBottomRightRadius: 48,
    borderBottomLeftRadius: 39,
  },
  transactionInner: {
    paddingTop: 16,
    paddingRight: 14,
    paddingBottom: 10,
    paddingLeft: 15,
    borderTopLeftRadius: 42,
    borderTopRightRadius: 36,
    borderBottomRightRadius: 48,
    borderBottomLeftRadius: 39,
  },
  parkingWrapper: {
    right: 10,
    borderTopLeftRadius: 31,
    borderTopRightRadius: 35,
    borderBottomRightRadius: 29,
    borderBottomLeftRadius: 38,
  },
  parkingInner: {
    paddingTop: 8,
    paddingRight: 14,
    paddingBottom: 7,
    paddingLeft: 14,
    borderTopLeftRadius: 31,
    borderTopRightRadius: 35,
    borderBottomRightRadius: 29,
    borderBottomLeftRadius: 38,
  },
  bottomDepth: {
    position: 'absolute',
    right: 0,
    bottom: 0,
    left: 0,
    height: '32%',
  },
  factIconShadow: {
    flexShrink: 0,
    alignItems: 'center',
    justifyContent: 'center',
    backgroundColor: 'rgba(255,255,255,0.01)',
    shadowOffset: { width: 0, height: 5 },
    shadowOpacity: 0.28,
    shadowRadius: 8,
    elevation: 5,
  },
  factIcon: {
    width: '100%',
    height: '100%',
    alignItems: 'center',
    justifyContent: 'center',
    overflow: 'hidden',
    borderWidth: 1,
    borderColor: 'rgba(255,255,255,0.68)',
  },
  iconGlint: {
    position: 'absolute',
    top: 5,
    left: 7,
    width: 12,
    height: 6,
    borderRadius: 6,
    backgroundColor: 'rgba(255,255,255,0.28)',
    transform: [{ rotate: '-24deg' }],
  },
  iconSymbol: {
    fontWeight: '900',
    color: '#FFFFFF',
    letterSpacing: -0.8,
  },
  householdContent: {
    minWidth: 0,
    flex: 1,
    flexDirection: 'row',
    alignItems: 'center',
    gap: 9,
  },
  householdCopy: { minWidth: 0, flex: 1 },
  transactionTopRow: {
    minWidth: 0,
    flexDirection: 'row',
    alignItems: 'flex-start',
    gap: 9,
  },
  transactionCopy: { minWidth: 0, flex: 1 },
  transactionTrend: {
    flex: 1,
    alignItems: 'center',
    justifyContent: 'flex-end',
    marginTop: 2,
  },
  trendPill: {
    height: 27,
    flexDirection: 'row',
    alignItems: 'center',
    justifyContent: 'center',
    gap: 3,
    paddingHorizontal: 10,
    borderRadius: 13,
    backgroundColor: 'rgba(255,255,255,0.42)',
    shadowColor: '#5A3928',
    shadowOffset: { width: 0, height: 2 },
    shadowOpacity: 0.08,
    shadowRadius: 5,
  },
  trendPillText: {
    fontSize: 11,
    lineHeight: 14,
    fontWeight: '900',
    letterSpacing: -0.1,
  },
  trendPeriod: {
    fontSize: 10,
    lineHeight: 13,
    fontWeight: '700',
    color: TEXT_COLORS.secondary,
    letterSpacing: -0.1,
  },
  transactionEmpty: {
    flex: 1,
    alignItems: 'center',
    justifyContent: 'center',
    gap: 3,
    paddingHorizontal: 8,
  },
  transactionEmptyHint: {
    fontSize: 8,
    lineHeight: 11,
    fontWeight: '700',
    color: TEXT_COLORS.secondary,
    textAlign: 'center',
  },
  parkingContent: {
    minWidth: 0,
    flex: 1,
    flexDirection: 'row',
    alignItems: 'center',
    gap: 10,
  },
  parkingCopy: { minWidth: 0, flex: 1 },
  eyebrow: {
    fontSize: 12,
    lineHeight: 16,
    fontWeight: '800',
    letterSpacing: -0.1,
    textShadowColor: 'rgba(255,255,255,0.74)',
    textShadowOffset: { width: 0, height: 1 },
    textShadowRadius: 5,
  },
  householdValue: {
    marginTop: 1,
    fontSize: 18,
    lineHeight: 21,
    fontWeight: '900',
    color: TEXT_COLORS.primary,
    letterSpacing: -0.7,
  },
  transactionValue: {
    marginTop: 1,
    fontSize: 18,
    lineHeight: 22,
    fontWeight: '900',
    color: TEXT_COLORS.primary,
    letterSpacing: -0.75,
  },
  parkingValue: {
    marginTop: 1,
    fontSize: 19,
    lineHeight: 22,
    fontWeight: '900',
    color: TEXT_COLORS.primary,
    letterSpacing: -0.7,
  },
  transactionMetaPrimary: {
    marginTop: 2,
    fontSize: 12,
    lineHeight: 16,
    fontWeight: '800',
    color: '#3B2820',
    letterSpacing: -0.15,
  },
  transactionCaption: {
    fontSize: 10,
    lineHeight: 14,
    fontWeight: '700',
    color: '#654B40',
    letterSpacing: -0.15,
  },
  factSubline: {
    marginTop: 1,
    fontSize: 11,
    lineHeight: 15,
    fontWeight: '700',
    color: TEXT_COLORS.secondary,
    letterSpacing: -0.1,
  },
});
