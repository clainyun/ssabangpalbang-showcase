import Ionicons from '@expo/vector-icons/Ionicons';
import { useQuery } from '@tanstack/react-query';
import { LinearGradient } from 'expo-linear-gradient';
import { StatusBar } from 'expo-status-bar';
import { Redirect, useLocalSearchParams, useRouter } from 'expo-router';
import { useMemo, useState } from 'react';
import {
  ActivityIndicator,
  Image,
  Platform,
  Pressable,
  ScrollView,
  Share,
  type StyleProp,
  StyleSheet,
  Text,
  type TextStyle,
  useWindowDimensions,
  View,
} from 'react-native';
import { useSafeAreaInsets } from 'react-native-safe-area-context';

import { appAlert } from '@/components/AppDialog';
import { FloatingTabBar } from '@/components/FloatingTabBar';
import {
  getReportDetail,
  ReportDetailApiError,
  reportDetailQueryKey,
  type ReportCategory,
  type ReportFeature,
} from '@/features/report/api/reportDetail';
import { ReportMascotSprite } from '@/features/report/ReportMascotSprite';
import { createReportShareUrl } from '@/features/report/reportShareLink';
import { useToggleReportFavorite } from '@/features/report/api/useReportFavorite';
import { useAuthStore } from '@/store/authStore';

const INK = '#12211C';
const PRIMARY_MINT = '#80FFCC';
const DEEP_MINT = '#00B583';
const MINT_END = '#37C99A';
const MUTED = '#7A8B84';
const SOFT_BACKGROUND = '#EEF2F8';
const DIVIDER = '#EAEEF0';
const CORAL = '#FF8B76';
const CORAL_END = '#F0553B';
const HERO_MASCOT_TOP = 75;
const HERO_ONE_LINE_TITLE_BOTTOM = 167;
const HERO_ART_CLEARANCE = 8;
const SUN_IMAGE = require('../../../assets/images/report/sun-emoji.png');
const CRYING_EMOJI_IMAGE = require('../../../assets/images/report/crying-emoji.png');
const AVATAR_COLORS = [PRIMARY_MINT, '#7C5CFF', '#4C8DFF', '#F0553B', '#37C99A'];

function clampPercent(value: number): number {
  if (!Number.isFinite(value)) return 0;
  return Math.min(100, Math.max(0, value));
}

function formatPercent(value: number): string {
  const safeValue = clampPercent(value);
  return Number.isInteger(safeValue) ? `${safeValue}` : safeValue.toFixed(1);
}

function formatVisitDate(value: string | null, fallback: string): string {
  const parsed = new Date(value ?? fallback);
  if (Number.isNaN(parsed.getTime())) return '임장 완료';

  return new Intl.DateTimeFormat('ko-KR', {
    year: 'numeric',
    month: 'numeric',
    day: 'numeric',
    weekday: 'short',
    timeZone: 'Asia/Seoul',
  }).format(parsed);
}

function categoryEmoji(category: string): string {
  if (/소음/.test(category)) return '🔊';
  if (/주차/.test(category)) return '🅿️';
  if (/교통|접근/.test(category)) return '🚇';
  if (/안전/.test(category)) return '🛡️';
  if (/일조|채광/.test(category)) return '☀️';
  if (/학군|교육/.test(category)) return '🏫';
  if (/상권|편의/.test(category)) return '🛍️';
  return '📍';
}

function participantInitial(label: string): string {
  const tokens = label.trim().split(/\s+/).filter(Boolean);
  const lastToken = tokens[tokens.length - 1] ?? '';
  return lastToken.slice(0, 2) || '·';
}

function displayUnitLength(value: string): number {
  return Array.from(value).reduce((total, character) => {
    const codePoint = character.codePointAt(0) ?? 0;
    return total + (codePoint <= 0x7f ? 1 : 2);
  }, 0);
}

function balanceTextByWords(text: string, requestedLineCount: number): string {
  const words = text.trim().split(/\s+/).filter(Boolean);
  if (words.join(' ') !== text || words.length > 120) return text;

  const lineCount = Math.min(Math.max(1, requestedLineCount), words.length);
  if (lineCount <= 1 || words.length <= 1) return text;

  const prefixUnits = [0];
  words.forEach((word) => {
    prefixUnits.push((prefixUnits[prefixUnits.length - 1] ?? 0) + displayUnitLength(word));
  });

  const lineUnits = (start: number, end: number) =>
    (prefixUnits[end] ?? 0) - (prefixUnits[start] ?? 0) + Math.max(0, end - start - 1);
  const totalUnits = (prefixUnits[words.length] ?? 0) + Math.max(0, words.length - lineCount);
  const targetUnits = totalUnits / lineCount;
  const costs = Array.from({ length: lineCount + 1 }, () =>
    Array<number>(words.length + 1).fill(Number.POSITIVE_INFINITY),
  );
  const previousBreaks = Array.from({ length: lineCount + 1 }, () =>
    Array<number>(words.length + 1).fill(-1),
  );
  costs[0]![0] = 0;

  for (let usedLines = 1; usedLines <= lineCount; usedLines += 1) {
    const remainingLines = lineCount - usedLines;
    for (let end = usedLines; end <= words.length - remainingLines; end += 1) {
      for (let start = usedLines - 1; start < end; start += 1) {
        const previousCost = costs[usedLines - 1]?.[start] ?? Number.POSITIVE_INFINITY;
        if (!Number.isFinite(previousCost)) continue;

        const deviation = lineUnits(start, end) - targetUnits;
        const nextCost = previousCost + deviation * deviation;
        if (nextCost < (costs[usedLines]?.[end] ?? Number.POSITIVE_INFINITY)) {
          costs[usedLines]![end] = nextCost;
          previousBreaks[usedLines]![end] = start;
        }
      }
    }
  }

  const lines = Array<string>(lineCount);
  let end = words.length;
  for (let usedLines = lineCount; usedLines >= 1; usedLines -= 1) {
    const start = previousBreaks[usedLines]?.[end] ?? -1;
    if (start < 0) return text;
    lines[usedLines - 1] = words.slice(start, end).join(' ');
    end = start;
  }
  return lines.join('\n');
}

function BalancedText({
  maxLines,
  style,
  text,
}: {
  maxLines?: number;
  style: StyleProp<TextStyle>;
  text: string;
}) {
  const [measuredLineCount, setMeasuredLineCount] = useState(1);
  const [candidateMeasurement, setCandidateMeasurement] = useState<{
    lineCount: number;
    text: string;
  } | null>(null);
  const lineCount = Math.max(1, Math.min(maxLines ?? measuredLineCount, measuredLineCount));
  const balancedText = useMemo(() => balanceTextByWords(text, lineCount), [lineCount, text]);
  const balancedLineCount =
    candidateMeasurement?.text === balancedText ? candidateMeasurement.lineCount : null;
  const renderedText =
    balancedText === text || (balancedLineCount !== null && balancedLineCount <= lineCount)
      ? balancedText
      : text;

  return (
    <View style={styles.balancedTextContainer}>
      <Text
        accessible={false}
        onTextLayout={({ nativeEvent }) => {
          const nextLineCount = Math.max(1, nativeEvent.lines.length);
          setMeasuredLineCount((current) => (current === nextLineCount ? current : nextLineCount));
        }}
        pointerEvents="none"
        style={[style, styles.balancedTextMeasure]}
      >
        {text}
      </Text>
      <Text
        accessible={false}
        onTextLayout={({ nativeEvent }) => {
          const nextMeasurement = {
            lineCount: Math.max(1, nativeEvent.lines.length),
            text: balancedText,
          };
          setCandidateMeasurement((current) =>
            current?.text === nextMeasurement.text &&
            current.lineCount === nextMeasurement.lineCount
              ? current
              : nextMeasurement,
          );
        }}
        pointerEvents="none"
        style={[style, styles.balancedTextMeasure]}
      >
        {balancedText}
      </Text>
      <Text numberOfLines={maxLines} style={style}>
        {renderedText}
      </Text>
    </View>
  );
}

function FeatureChart({
  caution,
  features,
  fontScale,
}: {
  caution?: boolean;
  features: ReportFeature[];
  fontScale: number;
}) {
  const visibleFeatures = features.slice(0, 3);
  const safeMentionCount = (count: number) => (Number.isFinite(count) ? Math.max(0, count) : 0);
  const maxCount = Math.max(
    ...visibleFeatures.map((feature) => safeMentionCount(feature.mentionCount)),
    1,
  );
  const chartLabelHeight = 68 * Math.max(1, fontScale);

  if (visibleFeatures.length === 0) {
    return null;
  }

  return (
    <View style={styles.chartRow}>
      {visibleFeatures.map((feature, index) => {
        const mentionCount = safeMentionCount(feature.mentionCount);
        const normalizedHeight = 34 + (mentionCount / maxCount) * 78;
        const highlighted = index === 0;

        return (
          <View
            key={`${feature.rank}-${feature.label}`}
            style={[styles.chartColumn, visibleFeatures.length < 3 && styles.chartColumnSparse]}
          >
            <View style={[styles.chartLabelArea, { height: chartLabelHeight }]}>
              <BalancedText maxLines={2} style={styles.chartLabel} text={feature.label} />
              <Text style={styles.chartCount}>{mentionCount}번</Text>
            </View>

            <View style={styles.barTrack}>
              {highlighted ? (
                <LinearGradient
                  colors={caution ? [CORAL, CORAL_END] : [PRIMARY_MINT, MINT_END]}
                  end={{ x: 0, y: 1 }}
                  start={{ x: 0, y: 0 }}
                  style={[styles.bar, { height: normalizedHeight }]}
                />
              ) : (
                <View style={[styles.bar, styles.neutralBar, { height: normalizedHeight }]} />
              )}
            </View>
          </View>
        );
      })}
    </View>
  );
}

function ExpandableSummary({ summary }: { summary: string }) {
  const [expanded, setExpanded] = useState(false);
  const [canExpand, setCanExpand] = useState(false);

  return (
    <>
      <Text
        accessible={false}
        onTextLayout={({ nativeEvent }) => {
          setCanExpand(nativeEvent.lines.length > 4);
        }}
        pointerEvents="none"
        style={[styles.heroSummary, styles.heroSummaryMeasure]}
      >
        {summary}
      </Text>
      <Text numberOfLines={expanded ? undefined : 4} style={styles.heroSummary}>
        {summary}
      </Text>
      {canExpand ? (
        <Pressable
          accessibilityLabel={expanded ? '리포트 상세 설명 접기' : '리포트 상세 설명 더보기'}
          accessibilityRole="button"
          accessibilityState={{ expanded }}
          hitSlop={8}
          onPress={() => setExpanded((current) => !current)}
          style={({ pressed }) => [styles.summaryToggle, pressed && styles.pressed]}
        >
          <Text style={styles.summaryToggleText}>{expanded ? '접기' : '더보기'}</Text>
          <Ionicons color={DEEP_MINT} name={expanded ? 'chevron-up' : 'chevron-down'} size={15} />
        </Pressable>
      ) : null}
    </>
  );
}

function CategoryCard({ category }: { category: ReportCategory }) {
  return (
    <View style={styles.categoryCard}>
      <View style={styles.categoryHeader}>
        <Text style={styles.categoryTitle}>
          {categoryEmoji(category.category)} {category.category}
        </Text>
        <Text
          style={[
            styles.categoryCount,
            category.cautionOpinionCount > category.positiveOpinionCount &&
              styles.categoryCountCaution,
          ]}
        >
          긍정 {category.positiveOpinionCount} · 주의 {category.cautionOpinionCount}
        </Text>
      </View>
      <BalancedText style={styles.categorySummary} text={category.summary} />
      {!category.dataSufficient ? (
        <View style={styles.insufficientBadge}>
          <Text style={styles.insufficientText}>참고할 기록이 조금 더 필요해요</Text>
        </View>
      ) : null}
      {category.participantOpinions.length > 0 ? (
        <View style={styles.opinionList}>
          {category.participantOpinions.map((opinion, index) => (
            <View
              key={`${opinion.participantLabel}-${opinion.opinionType}-${index}`}
              style={styles.opinionRow}
            >
              <View
                style={[
                  styles.opinionAvatar,
                  { backgroundColor: AVATAR_COLORS[index % AVATAR_COLORS.length] },
                ]}
              >
                <Text style={styles.opinionAvatarText}>
                  {participantInitial(opinion.participantLabel)}
                </Text>
              </View>
              <View style={styles.opinionBody}>
                <Text style={styles.opinionLabel}>{opinion.participantLabel}</Text>
                <BalancedText style={styles.opinionText} text={opinion.summary} />
              </View>
              <View
                accessibilityLabel={opinion.opinionType === 'POSITIVE' ? '긍정 의견' : '주의 의견'}
                accessibilityRole="image"
                accessible
                style={[
                  styles.opinionToneDot,
                  opinion.opinionType === 'CAUTION' && styles.opinionToneDotCaution,
                ]}
              />
            </View>
          ))}
        </View>
      ) : (
        <Text style={styles.noOpinionText}>이 카테고리에는 익명 의견 요약이 없어요.</Text>
      )}
    </View>
  );
}

function ScreenState({
  error,
  onBack,
  onRetry,
  retrying = false,
}: {
  error?: string;
  onBack: () => void;
  onRetry?: () => void;
  retrying?: boolean;
}) {
  const hasError = error !== undefined;

  return (
    <View style={styles.stateScreen}>
      <StatusBar style="dark" />
      {hasError ? (
        <View style={styles.stateIcon}>
          <Ionicons color={DEEP_MINT} name="document-text-outline" size={34} />
        </View>
      ) : (
        <ActivityIndicator color={DEEP_MINT} size="large" />
      )}
      <Text style={styles.stateTitle}>{error?.trim() || 'AI 리포트를 정리하고 있어요'}</Text>
      <Text style={styles.stateDescription}>
        {hasError
          ? onRetry
            ? '잠시 후 다시 시도하거나 이전 화면으로 돌아가 주세요.'
            : '이전 화면으로 돌아가 다른 리포트를 확인해 주세요.'
          : '조금만 기다려 주세요.'}
      </Text>
      {hasError ? (
        <View style={styles.stateActions}>
          <Pressable
            accessibilityRole="button"
            onPress={onBack}
            style={styles.stateSecondaryButton}
          >
            <Text style={styles.stateSecondaryText}>뒤로가기</Text>
          </Pressable>
          {onRetry ? (
            <Pressable
              accessibilityRole="button"
              disabled={retrying}
              onPress={onRetry}
              style={styles.statePrimaryButton}
            >
              {retrying ? (
                <ActivityIndicator color="#0A3D2B" size="small" />
              ) : (
                <Text style={styles.statePrimaryText}>다시 시도</Text>
              )}
            </Pressable>
          ) : null}
        </View>
      ) : null}
    </View>
  );
}

export default function ReportDetailScreen() {
  const { reportId: rawReportId } = useLocalSearchParams<{ reportId: string }>();
  const reportId = Number(rawReportId);
  const isValidReportId = Number.isSafeInteger(reportId) && reportId >= 1;
  const router = useRouter();
  const insets = useSafeAreaInsets();
  const { fontScale, width } = useWindowDimensions();
  const [checklistOpen, setChecklistOpen] = useState(false);
  const [opinionsOpen, setOpinionsOpen] = useState(false);
  const [heroTitleLayout, setHeroTitleLayout] = useState<{
    reportId: number;
    bottom: number;
  } | null>(null);
  const [sharing, setSharing] = useState(false);
  const sessionVersion = useAuthStore((state) => state.sessionVersion);
  const favoriteMutation = useToggleReportFavorite(reportId);

  const reportQuery = useQuery({
    queryKey: [...reportDetailQueryKey(reportId), sessionVersion],
    enabled: isValidReportId,
    queryFn: () => getReportDetail(reportId),
    retry: (failureCount, error) =>
      error instanceof ReportDetailApiError && error.code === 'REPORT_NOT_DONE'
        ? false
        : failureCount < 3,
  });
  const report = reportQuery.data;
  const participantLabels = useMemo(() => {
    const labels = new Set<string>();
    report?.categories.forEach((category) =>
      category.participantOpinions.forEach((opinion) => labels.add(opinion.participantLabel)),
    );
    return [...labels];
  }, [report]);

  const handleBack = () => {
    if (router.canGoBack()) {
      router.back();
      return;
    }
    router.replace('/(app)/(tabs)/my');
  };

  if (!isValidReportId) {
    return <ScreenState error="올바른 리포트 번호가 아니에요" onBack={handleBack} />;
  }

  if (reportQuery.isPending || report === undefined) {
    if (reportQuery.isError) {
      if (
        reportQuery.error instanceof ReportDetailApiError &&
        reportQuery.error.code === 'REPORT_NOT_DONE'
      ) {
        return <Redirect href={`/(app)/report-generating/${reportId}`} />;
      }
      if (reportQuery.isFetching) {
        return <ScreenState onBack={handleBack} />;
      }
      const message =
        reportQuery.error instanceof ReportDetailApiError
          ? reportQuery.error.message
          : '리포트를 불러오지 못했어요.';
      const retryable =
        !(reportQuery.error instanceof ReportDetailApiError) ||
        !['REPORT_NOT_FOUND', 'REPORT_ACCESS_DENIED', 'COMMON_INVALID_REQUEST'].includes(
          reportQuery.error.code,
        );
      return (
        <ScreenState
          error={message}
          onBack={handleBack}
          onRetry={retryable ? () => void reportQuery.refetch() : undefined}
          retrying={reportQuery.isFetching}
        />
      );
    }
    return <ScreenState onBack={handleBack} />;
  }

  const hasPositiveOpinions = report.topPositiveFeatures.length > 0;
  const hasCautionOpinions = report.topCautionFeatures.length > 0;
  const hasNoOpinions = !hasPositiveOpinions && !hasCautionOpinions;
  const positiveLead = report.topPositiveFeatures[0]?.label ?? '';
  const cautionLead = report.topCautionFeatures[0]?.label ?? '';
  const completionRate = clampPercent(report.metrics.averageCompletionRate);
  const mascotSize = Math.min(172, Math.max(138, width * 0.44));
  const heroTitleBottom =
    heroTitleLayout?.reportId === report.reportId
      ? heroTitleLayout.bottom
      : insets.top + HERO_ONE_LINE_TITLE_BOTTOM;
  const heroSummaryMarginTop = Math.max(
    26,
    HERO_MASCOT_TOP + mascotSize + HERO_ART_CLEARANCE - heroTitleBottom,
  );
  const shareDockBottom = Math.max(18, insets.bottom + 8) + 70;
  const visibleParticipants = participantLabels.slice(
    0,
    Math.min(3, report.study.participantCount),
  );
  const hiddenParticipantCount = Math.max(
    0,
    report.study.participantCount - visibleParticipants.length,
  );

  const canFavorite = report.viewer.canFavorite;
  const favoritedByMe = report.favoritedByMe;
  const favoritePending = favoriteMutation.isPending;

  const handleToggleFavorite = () => {
    if (favoritePending) return;
    favoriteMutation.mutate(!favoritedByMe, {
      onError: () => {
        appAlert(
          favoritedByMe ? '찜을 해제하지 못했어요' : '찜하지 못했어요',
          '잠시 후 다시 시도해 주세요.',
        );
      },
    });
  };

  const handleShare = async () => {
    if (sharing) return;
    setSharing(true);
    try {
      const shareUrl = createReportShareUrl(report.reportId);
      await Share.share(Platform.OS === 'ios' ? { url: shareUrl } : { message: shareUrl });
    } catch {
      appAlert('공유하지 못했어요', '잠시 후 다시 시도해 주세요.');
    } finally {
      setSharing(false);
    }
  };

  return (
    <View style={styles.screen}>
      <StatusBar style="dark" />
      <ScrollView
        contentContainerStyle={[styles.scrollContent, { paddingBottom: 230 + insets.bottom }]}
        showsVerticalScrollIndicator={false}
      >
        <LinearGradient
          colors={['#EEF2F8', '#FFFFFF']}
          style={[styles.hero, { paddingTop: insets.top + 10 }]}
        >
          <Pressable
            accessibilityLabel="이전 화면으로 돌아가기"
            accessibilityRole="button"
            hitSlop={10}
            onPress={handleBack}
            style={({ pressed }) => [styles.backButton, pressed && styles.pressed]}
          >
            <Ionicons color={INK} name="chevron-back" size={27} />
          </Pressable>

          <LinearGradient
            colors={['#7C5CFF', '#5B3EE0']}
            style={[styles.heroBlock, styles.heroBlockPurple]}
          />
          <LinearGradient
            colors={[PRIMARY_MINT, MINT_END]}
            style={[styles.heroBlock, styles.heroBlockMint]}
          />
          <LinearGradient
            colors={['#4C8DFF', '#2E63E0']}
            style={[styles.heroBlock, styles.heroBlockBlue]}
          />
          <View style={[styles.heroBlock, styles.heroBlockCoral]} />
          <View style={[styles.mascot, { width: mascotSize, height: mascotSize }]}>
            <ReportMascotSprite size={mascotSize} />
          </View>

          <Text style={styles.heroDate}>
            {formatVisitDate(report.study.visitedAt, report.completedAt)}
          </Text>
          <Text
            numberOfLines={2}
            onLayout={({ nativeEvent }) => {
              const nextBottom = nativeEvent.layout.y + nativeEvent.layout.height;
              setHeroTitleLayout((current) =>
                current?.reportId === report.reportId && Math.abs(current.bottom - nextBottom) < 0.5
                  ? current
                  : { reportId: report.reportId, bottom: nextBottom },
              );
            }}
            style={[styles.heroTitle, { maxWidth: Math.max(126, width - mascotSize - 48) }]}
          >
            {report.apartment.name}
          </Text>
          <View style={[styles.heroSummaryContainer, { marginTop: heroSummaryMarginTop }]}>
            <ExpandableSummary key={report.reportId} summary={report.summary} />
          </View>
        </LinearGradient>

        <View style={styles.storySection}>
          {hasPositiveOpinions ? (
            <>
              <View style={styles.storyTitleRow}>
                <Ionicons color={DEEP_MINT} name="happy" size={44} style={styles.storyTitleIcon} />
                <Text style={styles.storyTitle}>
                  <Text style={styles.positiveText}>{positiveLead} </Text>의견이{`\n`}가장 많이
                  나왔어요
                </Text>
              </View>
              <FeatureChart features={report.topPositiveFeatures} fontScale={fontScale} />
            </>
          ) : (
            <>
              <Text style={styles.storyTitle}>
                아직 <Text style={styles.positiveText}>긍정적인 의견</Text>이{`\n`}
                충분히 모이지 않았어요
              </Text>
              <Image
                accessible={false}
                resizeMode="contain"
                source={CRYING_EMOJI_IMAGE}
                style={styles.emptyOpinionImage}
              />
            </>
          )}
        </View>

        {hasNoOpinions ? <View style={styles.divider} /> : null}

        <View style={styles.storySection}>
          {hasCautionOpinions ? (
            <>
              <View style={styles.storyTitleRow}>
                <Ionicons color={CORAL} name="sad" size={44} style={styles.storyTitleIcon} />
                <Text style={styles.storyTitle}>
                  <Text style={styles.cautionText}>{cautionLead} </Text>특징을{`\n`}많이 우려하고
                  있어요
                </Text>
              </View>
              <FeatureChart caution features={report.topCautionFeatures} fontScale={fontScale} />
            </>
          ) : (
            <>
              <Text style={styles.storyTitle}>
                아직 <Text style={styles.cautionText}>주의할 점에 대한 의견</Text>이{`\n`}
                충분히 모이지 않았어요
              </Text>
              <Image
                accessible={false}
                resizeMode="contain"
                source={CRYING_EMOJI_IMAGE}
                style={styles.emptyOpinionImage}
              />
            </>
          )}
        </View>

        <View style={styles.divider} />

        <View style={styles.storySectionCompact}>
          <Pressable
            accessibilityRole="button"
            accessibilityState={{ expanded: checklistOpen }}
            onPress={() => setChecklistOpen((open) => !open)}
            style={({ pressed }) => [styles.accordionHeader, pressed && styles.pressed]}
          >
            <Text style={styles.storyTitle}>
              총{' '}
              <Text style={styles.positiveText}>
                {report.metrics.totalChecklistItemCount}개의 체크 항목
              </Text>
              을{`\n`}
              확인했어요
            </Text>
            <Ionicons
              color={MUTED}
              name={checklistOpen ? 'chevron-up' : 'chevron-down'}
              size={23}
            />
          </Pressable>
          {checklistOpen ? (
            report.categories.length > 0 ? (
              <View style={styles.checkGrid}>
                {report.categories.map((category) => (
                  <View key={category.category} style={styles.checkChip}>
                    <View style={styles.checkCountBadge}>
                      <Text style={styles.checkCountText}>{category.checklistItemCount}</Text>
                    </View>
                    <Text numberOfLines={1} style={styles.checkText}>
                      {category.category}
                    </Text>
                  </View>
                ))}
              </View>
            ) : (
              <Text style={styles.accordionEmpty}>정리된 체크 카테고리가 아직 없어요.</Text>
            )
          ) : null}
        </View>

        <View style={styles.divider} />

        <View style={styles.storySectionCompact}>
          <Pressable
            accessibilityRole="button"
            accessibilityState={{ expanded: opinionsOpen }}
            onPress={() => setOpinionsOpen((open) => !open)}
            style={({ pressed }) => [styles.accordionHeader, pressed && styles.pressed]}
          >
            <Text style={styles.storyTitle}>
              체크리스트를{' '}
              <Text style={styles.positiveText}>평균 {formatPercent(completionRate)}%</Text>
              {`\n`}
              완료했어요
            </Text>
            <Ionicons color={MUTED} name={opinionsOpen ? 'chevron-up' : 'chevron-down'} size={23} />
          </Pressable>
          <View style={styles.progressTrack}>
            <LinearGradient
              colors={[MINT_END, PRIMARY_MINT]}
              end={{ x: 1, y: 0 }}
              start={{ x: 0, y: 0 }}
              style={[styles.progressFill, { width: `${completionRate}%` }]}
            />
          </View>
          <View style={styles.progressMeta}>
            <Text style={styles.progressHint}>탭하여 항목별 의견 보기</Text>
            <Text style={styles.progressBest}>
              {report.metrics.completedChecklistItemCount}개 완료
            </Text>
          </View>
          {opinionsOpen ? (
            report.categories.length > 0 ? (
              <View style={styles.categoryList}>
                {report.categories.map((category) => (
                  <CategoryCard category={category} key={category.category} />
                ))}
              </View>
            ) : (
              <Text style={styles.accordionEmpty}>함께 살펴볼 익명 의견이 아직 없어요.</Text>
            )
          ) : null}
        </View>

        <View style={styles.divider} />

        <View style={styles.storySectionCompact}>
          <Text style={styles.storyTitle}>
            스터디원 <Text style={styles.positiveText}>{report.study.participantCount}명</Text>의
            기록을{`\n`}
            함께 남겼어요
          </Text>
          <View style={styles.participantRow}>
            {visibleParticipants.map((participantLabel, index) => (
              <View
                accessibilityLabel={participantLabel}
                accessible
                key={participantLabel}
                style={[
                  styles.participantAvatar,
                  { backgroundColor: AVATAR_COLORS[index % AVATAR_COLORS.length] },
                  index > 0 && styles.participantAvatarOverlap,
                ]}
              >
                <Text style={styles.participantAvatarText}>
                  {participantInitial(participantLabel)}
                </Text>
              </View>
            ))}
            {hiddenParticipantCount > 0 ? (
              <View
                accessibilityLabel={`외 ${hiddenParticipantCount}명`}
                accessible
                style={[
                  styles.participantAvatar,
                  styles.moreAvatar,
                  styles.participantAvatarOverlap,
                ]}
              >
                <Text style={styles.moreAvatarText}>+{hiddenParticipantCount}</Text>
              </View>
            ) : null}
          </View>
        </View>

        <View style={styles.closing}>
          <Image source={SUN_IMAGE} style={styles.sunImage} />
          <Text style={styles.closingDescription}>임장 여정을 함께해줘서 고마워요</Text>
          <Text style={styles.closingTitle}>다음은 어디로 가볼까요?</Text>
        </View>
      </ScrollView>

      <LinearGradient
        colors={['rgba(255,255,255,0)', '#FFFFFF', '#FFFFFF']}
        locations={[0, 0.38, 1]}
        pointerEvents="box-none"
        style={[styles.shareDock, { bottom: shareDockBottom }]}
      >
        <View style={styles.dockRow}>
          {canFavorite ? (
            <Pressable
              accessibilityLabel={favoritedByMe ? '리포트 찜 해제' : '리포트 찜하기'}
              accessibilityRole="button"
              accessibilityState={{
                selected: favoritedByMe,
                busy: favoritePending,
                disabled: favoritePending,
              }}
              disabled={favoritePending}
              onPress={handleToggleFavorite}
              style={({ pressed }) => [
                styles.favoriteButton,
                pressed && styles.favoriteButtonPressed,
              ]}
            >
              {favoritePending ? (
                <ActivityIndicator color={CORAL_END} size="small" />
              ) : (
                <Ionicons
                  color={favoritedByMe ? CORAL_END : MUTED}
                  name={favoritedByMe ? 'heart' : 'heart-outline'}
                  size={24}
                />
              )}
            </Pressable>
          ) : null}
          <Pressable
            accessibilityLabel="리포트 공유하기"
            accessibilityRole="button"
            accessibilityState={{ busy: sharing, disabled: sharing }}
            disabled={sharing}
            onPress={() => void handleShare()}
            style={({ pressed }) => [
              styles.shareButton,
              styles.shareButtonFlex,
              pressed && styles.shareButtonPressed,
            ]}
          >
            {sharing ? (
              <ActivityIndicator color="#0A3D2B" size="small" />
            ) : (
              <Ionicons color="#0A3D2B" name="share-outline" size={21} />
            )}
            <Text style={styles.shareButtonText}>
              {sharing ? '공유 준비 중' : '리포트 공유하기'}
            </Text>
          </Pressable>
        </View>
      </LinearGradient>
      <FloatingTabBar activeTab="my" />
    </View>
  );
}

const styles = StyleSheet.create({
  screen: { flex: 1, backgroundColor: '#FFFFFF' },
  scrollContent: { backgroundColor: '#FFFFFF' },
  hero: {
    minHeight: 390,
    paddingHorizontal: 26,
    paddingBottom: 38,
    overflow: 'hidden',
  },
  backButton: {
    width: 38,
    height: 38,
    marginLeft: -7,
    alignItems: 'center',
    justifyContent: 'center',
    zIndex: 10,
  },
  heroBlock: { position: 'absolute' },
  heroBlockPurple: {
    width: 66,
    height: 110,
    top: 80,
    right: 40,
    borderRadius: 14,
    transform: [{ rotate: '-16deg' }],
  },
  heroBlockMint: {
    width: 52,
    height: 82,
    top: 116,
    right: 106,
    borderRadius: 12,
    transform: [{ rotate: '12deg' }],
  },
  heroBlockBlue: {
    width: 44,
    height: 44,
    top: 196,
    right: 62,
    borderRadius: 22,
  },
  heroBlockCoral: {
    width: 26,
    height: 26,
    top: 90,
    right: 160,
    borderRadius: 7,
    backgroundColor: CORAL_END,
    opacity: 0.9,
    transform: [{ rotate: '20deg' }],
  },
  mascot: {
    position: 'absolute',
    top: HERO_MASCOT_TOP,
    right: 14,
    zIndex: 6,
  },
  heroDate: {
    maxWidth: '58%',
    marginTop: 50,
    color: DEEP_MINT,
    fontFamily: 'IBMPlexSansKR_700Bold',
    fontSize: 17,
    lineHeight: 24,
  },
  heroTitle: {
    maxWidth: '65%',
    marginTop: 10,
    color: '#000000',
    fontFamily: 'NotoSansKR_900Black',
    fontSize: 30,
    lineHeight: 35,
    letterSpacing: -0.9,
  },
  heroSummaryContainer: {
    width: '100%',
    position: 'relative',
  },
  heroSummary: {
    color: MUTED,
    fontFamily: 'IBMPlexSansKR_600SemiBold',
    fontSize: 17,
    lineHeight: 26,
  },
  heroSummaryMeasure: {
    position: 'absolute',
    left: 0,
    right: 0,
    opacity: 0,
  },
  summaryToggle: {
    minHeight: 36,
    marginTop: 6,
    alignSelf: 'flex-start',
    flexDirection: 'row',
    alignItems: 'center',
    gap: 2,
  },
  summaryToggleText: {
    color: DEEP_MINT,
    fontFamily: 'IBMPlexSansKR_700Bold',
    fontSize: 14,
  },
  storySection: { paddingHorizontal: 26, paddingTop: 34 },
  storySectionCompact: { paddingHorizontal: 26, paddingTop: 32 },
  storyTitleRow: { flexDirection: 'row', alignItems: 'center', marginLeft: -10 },
  storyTitleIcon: { marginRight: 8 },
  storyTitle: {
    flexShrink: 1,
    color: INK,
    fontFamily: 'NotoSansKR_900Black',
    fontSize: 24,
    lineHeight: 32,
    letterSpacing: -0.55,
  },
  positiveText: { color: DEEP_MINT },
  cautionText: { color: CORAL },
  emptyOpinionImage: { width: 72, height: 72, marginTop: 20, alignSelf: 'center' },
  chartRow: {
    minHeight: 192,
    marginTop: 22,
    flexDirection: 'row',
    alignItems: 'flex-start',
    justifyContent: 'center',
    gap: 16,
  },
  chartColumn: { flex: 1, minWidth: 0, alignItems: 'center' },
  chartColumnSparse: { flex: 0, width: 96 },
  chartLabelArea: { alignItems: 'center', justifyContent: 'center' },
  balancedTextContainer: { width: '100%', position: 'relative' },
  balancedTextMeasure: { position: 'absolute', left: 0, right: 0, opacity: 0 },
  chartLabel: {
    color: MUTED,
    fontFamily: 'IBMPlexSansKR_600SemiBold',
    fontSize: 16,
    lineHeight: 22,
    textAlign: 'center',
  },
  chartCount: {
    marginTop: 2,
    color: INK,
    fontFamily: 'IBMPlexSansKR_700Bold',
    fontSize: 16,
  },
  barTrack: { width: '100%', height: 112, justifyContent: 'flex-end' },
  bar: { width: '100%', borderRadius: 12 },
  neutralBar: { backgroundColor: '#E4E8EE' },
  divider: { height: 1, marginHorizontal: 26, marginTop: 34, backgroundColor: DIVIDER },
  accordionHeader: {
    flexDirection: 'row',
    alignItems: 'flex-start',
    justifyContent: 'space-between',
    gap: 12,
  },
  checkGrid: { marginTop: 18, flexDirection: 'row', flexWrap: 'wrap', gap: 8 },
  checkChip: {
    width: '48.5%',
    minHeight: 42,
    paddingHorizontal: 13,
    borderRadius: 12,
    backgroundColor: SOFT_BACKGROUND,
    flexDirection: 'row',
    alignItems: 'center',
    gap: 8,
  },
  checkCountBadge: {
    minWidth: 22,
    height: 22,
    paddingHorizontal: 6,
    borderRadius: 11,
    backgroundColor: DEEP_MINT,
    alignItems: 'center',
    justifyContent: 'center',
  },
  checkCountText: {
    color: '#FFFFFF',
    fontFamily: 'IBMPlexSansKR_700Bold',
    fontSize: 12,
    lineHeight: 15,
  },
  checkText: {
    flex: 1,
    color: '#3A4A43',
    fontFamily: 'IBMPlexSansKR_600SemiBold',
    fontSize: 15,
    lineHeight: 21,
  },
  accordionEmpty: {
    marginTop: 18,
    padding: 16,
    borderRadius: 14,
    backgroundColor: SOFT_BACKGROUND,
    color: MUTED,
    fontFamily: 'IBMPlexSansKR_600SemiBold',
    fontSize: 16,
    lineHeight: 22,
    textAlign: 'center',
  },
  progressTrack: {
    height: 12,
    marginTop: 20,
    borderRadius: 9,
    backgroundColor: '#EEF2F0',
    overflow: 'hidden',
  },
  progressFill: { height: '100%', borderRadius: 9 },
  progressMeta: {
    marginTop: 10,
    flexDirection: 'row',
    flexWrap: 'wrap',
    alignItems: 'flex-start',
    justifyContent: 'space-between',
    gap: 10,
    rowGap: 4,
  },
  progressHint: {
    flexShrink: 1,
    minWidth: 0,
    color: MUTED,
    fontFamily: 'IBMPlexSansKR_600SemiBold',
    fontSize: 16,
    lineHeight: 22,
  },
  progressBest: {
    flexShrink: 0,
    color: DEEP_MINT,
    fontFamily: 'IBMPlexSansKR_700Bold',
    fontSize: 14,
  },
  categoryList: { marginTop: 18, gap: 10 },
  categoryCard: { padding: 16, borderRadius: 16, backgroundColor: SOFT_BACKGROUND },
  categoryHeader: {
    flexDirection: 'row',
    alignItems: 'center',
    justifyContent: 'space-between',
    gap: 8,
  },
  categoryTitle: {
    flex: 1,
    color: INK,
    fontFamily: 'IBMPlexSansKR_700Bold',
    fontSize: 18,
    lineHeight: 25,
  },
  categoryCount: {
    color: DEEP_MINT,
    fontFamily: 'IBMPlexSansKR_700Bold',
    fontSize: 13,
  },
  categoryCountCaution: { color: CORAL_END },
  categorySummary: {
    marginTop: 10,
    color: '#3A4A43',
    fontFamily: 'IBMPlexSansKR_600SemiBold',
    fontSize: 16,
    lineHeight: 24,
  },
  insufficientBadge: {
    alignSelf: 'flex-start',
    marginTop: 10,
    paddingHorizontal: 9,
    paddingVertical: 5,
    borderRadius: 10,
    backgroundColor: '#FFF0EC',
  },
  insufficientText: {
    color: CORAL_END,
    fontFamily: 'IBMPlexSansKR_600SemiBold',
    fontSize: 12,
  },
  opinionList: { marginTop: 14, gap: 11 },
  opinionRow: { flexDirection: 'row', alignItems: 'flex-start', gap: 9 },
  opinionAvatar: {
    width: 28,
    height: 28,
    borderRadius: 14,
    alignItems: 'center',
    justifyContent: 'center',
  },
  opinionAvatarText: {
    color: '#15392C',
    fontFamily: 'IBMPlexSansKR_700Bold',
    fontSize: 11,
  },
  opinionBody: { flex: 1, minWidth: 0 },
  opinionLabel: {
    color: MUTED,
    fontFamily: 'IBMPlexSansKR_600SemiBold',
    fontSize: 16,
    lineHeight: 23,
  },
  opinionText: {
    marginTop: 2,
    color: '#3A4A43',
    fontFamily: 'IBMPlexSansKR_600SemiBold',
    fontSize: 17,
    lineHeight: 25,
  },
  opinionToneDot: {
    width: 7,
    height: 7,
    marginTop: 6,
    borderRadius: 4,
    backgroundColor: DEEP_MINT,
  },
  opinionToneDotCaution: { backgroundColor: CORAL_END },
  noOpinionText: {
    marginTop: 12,
    color: MUTED,
    fontFamily: 'IBMPlexSansKR_600SemiBold',
    fontSize: 16,
    lineHeight: 22,
  },
  participantRow: { marginTop: 20, flexDirection: 'row' },
  participantAvatar: {
    width: 40,
    height: 40,
    borderWidth: 2,
    borderColor: '#FFFFFF',
    borderRadius: 20,
    alignItems: 'center',
    justifyContent: 'center',
  },
  participantAvatarOverlap: { marginLeft: -10 },
  participantAvatarText: {
    color: '#15392C',
    fontFamily: 'IBMPlexSansKR_700Bold',
    fontSize: 14,
  },
  moreAvatar: { backgroundColor: '#E4E8EE' },
  moreAvatarText: {
    color: MUTED,
    fontFamily: 'IBMPlexSansKR_700Bold',
    fontSize: 15,
  },
  closing: { paddingHorizontal: 26, paddingTop: 46, alignItems: 'center' },
  sunImage: { width: 52, height: 52 },
  closingDescription: {
    marginTop: 14,
    color: MUTED,
    fontFamily: 'IBMPlexSansKR_600SemiBold',
    fontSize: 17,
    lineHeight: 25,
  },
  closingTitle: {
    marginTop: 6,
    color: INK,
    fontFamily: 'NotoSansKR_900Black',
    fontSize: 23,
    lineHeight: 30,
  },
  shareDock: {
    position: 'absolute',
    left: 0,
    right: 0,
    zIndex: 8,
    paddingHorizontal: 24,
    paddingTop: 30,
    paddingBottom: 8,
  },
  dockRow: {
    flexDirection: 'row',
    alignItems: 'center',
    gap: 12,
  },
  favoriteButton: {
    width: 52,
    height: 52,
    borderRadius: 16,
    backgroundColor: '#FFFFFF',
    alignItems: 'center',
    justifyContent: 'center',
    boxShadow: '0px 3px 18px rgba(10,15,40,0.28)',
    elevation: 5,
  },
  favoriteButtonPressed: { transform: [{ scale: 0.96 }], opacity: 0.9 },
  shareButton: {
    height: 52,
    borderRadius: 16,
    backgroundColor: PRIMARY_MINT,
    flexDirection: 'row',
    alignItems: 'center',
    justifyContent: 'center',
    gap: 8,
    boxShadow: '0px 3px 18px rgba(10,15,40,0.28)',
    elevation: 5,
  },
  shareButtonFlex: { flex: 1 },
  shareButtonPressed: { transform: [{ scale: 0.99 }], opacity: 0.9 },
  shareButtonText: {
    color: '#0A3D2B',
    fontFamily: 'IBMPlexSansKR_700Bold',
    fontSize: 16,
  },
  stateScreen: {
    flex: 1,
    paddingHorizontal: 28,
    backgroundColor: '#FFFFFF',
    alignItems: 'center',
    justifyContent: 'center',
  },
  stateIcon: {
    width: 66,
    height: 66,
    borderRadius: 24,
    backgroundColor: '#E6FFF4',
    alignItems: 'center',
    justifyContent: 'center',
  },
  stateTitle: {
    marginTop: 18,
    color: INK,
    fontFamily: 'NotoSansKR_900Black',
    fontSize: 20,
    textAlign: 'center',
  },
  stateDescription: {
    marginTop: 8,
    color: MUTED,
    fontFamily: 'IBMPlexSansKR_600SemiBold',
    fontSize: 15,
    lineHeight: 22,
    textAlign: 'center',
  },
  stateActions: { marginTop: 24, flexDirection: 'row', gap: 10 },
  stateSecondaryButton: {
    height: 46,
    paddingHorizontal: 20,
    borderRadius: 14,
    backgroundColor: SOFT_BACKGROUND,
    alignItems: 'center',
    justifyContent: 'center',
  },
  stateSecondaryText: {
    color: INK,
    fontFamily: 'IBMPlexSansKR_700Bold',
    fontSize: 14,
  },
  statePrimaryButton: {
    height: 46,
    paddingHorizontal: 20,
    borderRadius: 14,
    backgroundColor: PRIMARY_MINT,
    alignItems: 'center',
    justifyContent: 'center',
  },
  statePrimaryText: {
    color: '#0A3D2B',
    fontFamily: 'IBMPlexSansKR_700Bold',
    fontSize: 14,
  },
  pressed: { opacity: 0.65 },
});
