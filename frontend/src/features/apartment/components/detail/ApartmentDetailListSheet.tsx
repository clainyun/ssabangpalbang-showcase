import Ionicons from '@expo/vector-icons/Ionicons';
import {
  ActivityIndicator,
  Modal,
  Pressable,
  ScrollView,
  StyleSheet,
  Text,
  View,
} from 'react-native';
import { useSafeAreaInsets } from 'react-native-safe-area-context';

import {
  BORDER_COLOR,
  DARK_GREEN_COLOR,
  ERROR_BACKGROUND_COLOR,
  ERROR_COLOR,
  LABEL_COLOR,
  MODAL_SCRIM_COLOR,
  MUTED_TEXT_COLOR,
  PRIMARY_COLOR,
  SOFT_BACKGROUND_COLOR,
  SOFT_GREEN_COLOR,
  SURFACE_COLOR,
  TEXT_COLOR,
} from '@/constants/colors';
import type { ApartmentReport, ApartmentStudy } from '@/features/apartment/api/apartmentDetail';
import { formatDate, formatDDay, formatDateTime, formatPurpose } from '@/features/apartment/format';

export type ApartmentDetailPanel = 'studies' | 'reports';

type ApartmentDetailListSheetProps = {
  apartmentName: string;
  isError: boolean;
  isPending: boolean;
  items: ApartmentStudy[] | ApartmentReport[];
  onClose: () => void;
  onReportPress: (reportId: number) => void;
  onRetry: () => void;
  onStudyPress: (studyId: number) => void;
  panel: ApartmentDetailPanel | null;
  totalElements: number;
};

function StudyCard({ onPress, study }: { onPress: () => void; study: ApartmentStudy }) {
  const purpose = formatPurpose(study.purpose);
  const isAlmostFull = study.remainingCapacity > 0 && study.remainingCapacity <= 1;

  return (
    <Pressable
      accessibilityLabel={`${study.title} 스터디 상세 보기`}
      accessibilityRole="button"
      onPress={onPress}
      style={({ pressed }) => [styles.card, pressed && styles.cardPressed]}
    >
      <View style={styles.cardHeader}>
        <View style={styles.badgeRow}>
          <View style={[styles.badge, isAlmostFull ? styles.urgentBadge : styles.greenBadge]}>
            <Text
              style={[
                styles.badgeText,
                isAlmostFull ? styles.urgentBadgeText : styles.greenBadgeText,
              ]}
            >
              {isAlmostFull ? '마감 임박' : '모집 중'}
            </Text>
          </View>
          {purpose !== null ? (
            <View style={[styles.badge, styles.neutralBadge]}>
              <Text style={[styles.badgeText, styles.neutralBadgeText]}>{purpose}</Text>
            </View>
          ) : null}
        </View>
        <Text style={styles.memberCount}>
          {study.currentMemberCount}
          {study.capacity === null ? '' : `/${study.capacity}`}명
        </Text>
      </View>

      <Text numberOfLines={1} style={styles.cardTitle}>
        {study.title}
      </Text>
      {study.intro !== null && study.intro.length > 0 ? (
        <Text numberOfLines={2} style={styles.cardBody}>
          {study.intro}
        </Text>
      ) : null}

      <View style={styles.cardFooter}>
        <View style={styles.cardFooterItem}>
          <Ionicons color={MUTED_TEXT_COLOR} name="calendar-outline" size={14} />
          <Text numberOfLines={1} style={styles.cardMeta}>
            {study.schedule === null ? '일정 조율 중' : formatDateTime(study.schedule.startAt)}
          </Text>
        </View>
        {study.schedule !== null ? (
          <Text style={styles.dDay}>{formatDDay(study.schedule.dDay)}</Text>
        ) : null}
        <Ionicons color={DARK_GREEN_COLOR} name="chevron-forward" size={17} />
      </View>
    </Pressable>
  );
}

function ReportCard({ onPress, report }: { onPress: () => void; report: ApartmentReport }) {
  return (
    <Pressable
      accessibilityLabel={`${report.title ?? '임장 리포트'} 상세 보기`}
      accessibilityRole="button"
      onPress={onPress}
      style={({ pressed }) => [styles.card, pressed && styles.cardPressed]}
    >
      <View style={styles.cardHeader}>
        <View style={styles.badgeRow}>
          {report.isAiGenerated ? (
            <View style={[styles.badge, styles.greenBadge]}>
              <Text style={[styles.badgeText, styles.greenBadgeText]}>AI 생성</Text>
            </View>
          ) : null}
          {report.analysisTags.slice(0, 2).map((tag) => (
            <View key={tag} style={[styles.badge, styles.neutralBadge]}>
              <Text numberOfLines={1} style={[styles.badgeText, styles.neutralBadgeText]}>
                {tag}
              </Text>
            </View>
          ))}
        </View>
        {report.completedAt !== null ? (
          <Text style={styles.cardMeta}>{formatDate(report.completedAt)}</Text>
        ) : null}
      </View>
      <Text numberOfLines={1} style={styles.cardTitle}>
        {report.title ?? '임장 리포트'}
      </Text>
      {report.summary !== null && report.summary.length > 0 ? (
        <Text numberOfLines={3} style={styles.cardBody}>
          {report.summary}
        </Text>
      ) : (
        <Text style={styles.cardBody}>공개된 요약 정보가 없습니다.</Text>
      )}
    </Pressable>
  );
}

export function ApartmentDetailListSheet({
  apartmentName,
  isError,
  isPending,
  items,
  onClose,
  onReportPress,
  onRetry,
  onStudyPress,
  panel,
  totalElements,
}: ApartmentDetailListSheetProps) {
  const insets = useSafeAreaInsets();
  const isStudies = panel === 'studies';
  const title = isStudies ? '함께 임장할 스터디' : '완료된 현장 리포트';
  const emptyMessage = isStudies
    ? '현재 모집 중인 임장 스터디가 없습니다.'
    : '아직 공개된 완료 리포트가 없습니다.';

  return (
    <Modal
      animationType="none"
      onRequestClose={onClose}
      statusBarTranslucent
      transparent
      visible={panel !== null}
    >
      <View style={styles.modalRoot}>
        <Pressable accessibilityLabel="목록 닫기" onPress={onClose} style={styles.scrim} />
        <View
          accessibilityViewIsModal
          style={[styles.sheet, { paddingBottom: Math.max(18, insets.bottom + 12) }]}
        >
          <View style={styles.handle} />
          <View style={styles.sheetHeader}>
            <View style={styles.sheetTitleWrap}>
              <Text numberOfLines={1} style={styles.sheetEyebrow}>
                {apartmentName}
              </Text>
              <View style={styles.sheetTitleRow}>
                <Text style={styles.sheetTitle}>{title}</Text>
                <View style={styles.countBadge}>
                  <Text style={styles.countBadgeText}>{totalElements}</Text>
                </View>
              </View>
            </View>
            <Pressable
              accessibilityLabel="목록 닫기"
              accessibilityRole="button"
              hitSlop={8}
              onPress={onClose}
              style={({ pressed }) => [styles.closeButton, pressed && styles.cardPressed]}
            >
              <Ionicons color={DARK_GREEN_COLOR} name="close" size={23} />
            </Pressable>
          </View>

          {isPending ? (
            <View style={styles.stateArea}>
              <ActivityIndicator color={PRIMARY_COLOR} />
              <Text style={styles.stateText}>목록을 불러오고 있어요.</Text>
            </View>
          ) : isError ? (
            <View style={styles.stateArea}>
              <Ionicons color={ERROR_COLOR} name="alert-circle-outline" size={28} />
              <Text style={styles.stateText}>목록을 불러오지 못했습니다.</Text>
              <Pressable
                accessibilityRole="button"
                onPress={onRetry}
                style={({ pressed }) => [styles.retryButton, pressed && styles.cardPressed]}
              >
                <Text style={styles.retryText}>다시 시도</Text>
              </Pressable>
            </View>
          ) : items.length === 0 ? (
            <View style={styles.stateArea}>
              <Ionicons color={MUTED_TEXT_COLOR} name="leaf-outline" size={29} />
              <Text style={styles.stateText}>{emptyMessage}</Text>
            </View>
          ) : (
            <ScrollView
              contentContainerStyle={styles.listContent}
              keyboardShouldPersistTaps="handled"
              showsVerticalScrollIndicator={false}
              style={styles.list}
            >
              {isStudies
                ? (items as ApartmentStudy[]).map((study) => (
                    <StudyCard
                      key={study.studyId}
                      onPress={() => onStudyPress(study.studyId)}
                      study={study}
                    />
                  ))
                : (items as ApartmentReport[]).map((report) => (
                    <ReportCard
                      key={report.reportId}
                      onPress={() => onReportPress(report.reportId)}
                      report={report}
                    />
                  ))}
              {totalElements > items.length ? (
                <View style={styles.limitNotice}>
                  <Ionicons color={MUTED_TEXT_COLOR} name="information-circle-outline" size={16} />
                  <Text style={styles.limitNoticeText}>
                    {`전체 ${totalElements}개 중 ${items.length}개를 표시하고 있어요.`}
                  </Text>
                </View>
              ) : null}
            </ScrollView>
          )}
        </View>
      </View>
    </Modal>
  );
}

const styles = StyleSheet.create({
  modalRoot: { flex: 1, justifyContent: 'flex-end' },
  scrim: {
    position: 'absolute',
    top: 0,
    right: 0,
    bottom: 0,
    left: 0,
    backgroundColor: MODAL_SCRIM_COLOR,
  },
  sheet: {
    maxHeight: '72%',
    minHeight: 320,
    overflow: 'hidden',
    borderTopLeftRadius: 28,
    borderTopRightRadius: 28,
    backgroundColor: SURFACE_COLOR,
    shadowColor: DARK_GREEN_COLOR,
    shadowOffset: { width: 0, height: -8 },
    shadowOpacity: 0.18,
    shadowRadius: 22,
    elevation: 16,
  },
  handle: {
    alignSelf: 'center',
    width: 42,
    height: 4,
    marginTop: 10,
    borderRadius: 2,
    backgroundColor: BORDER_COLOR,
  },
  sheetHeader: {
    flexDirection: 'row',
    alignItems: 'center',
    gap: 12,
    paddingTop: 13,
    paddingRight: 16,
    paddingBottom: 15,
    paddingLeft: 20,
    borderBottomWidth: 1,
    borderBottomColor: BORDER_COLOR,
  },
  sheetTitleWrap: { minWidth: 0, flex: 1 },
  sheetEyebrow: {
    fontSize: 11,
    lineHeight: 16,
    fontWeight: '600',
    color: MUTED_TEXT_COLOR,
  },
  sheetTitleRow: { flexDirection: 'row', alignItems: 'center', gap: 7 },
  sheetTitle: {
    flexShrink: 1,
    fontSize: 21,
    lineHeight: 28,
    fontWeight: '900',
    color: DARK_GREEN_COLOR,
    letterSpacing: -0.8,
  },
  countBadge: {
    minWidth: 25,
    height: 25,
    alignItems: 'center',
    justifyContent: 'center',
    paddingHorizontal: 7,
    borderRadius: 13,
    backgroundColor: SOFT_GREEN_COLOR,
  },
  countBadgeText: { fontSize: 12, fontWeight: '800', color: PRIMARY_COLOR },
  closeButton: {
    width: 44,
    height: 44,
    alignItems: 'center',
    justifyContent: 'center',
    borderRadius: 22,
    backgroundColor: SOFT_BACKGROUND_COLOR,
  },
  stateArea: {
    minHeight: 220,
    alignItems: 'center',
    justifyContent: 'center',
    gap: 12,
    paddingHorizontal: 28,
  },
  stateText: {
    fontSize: 14,
    lineHeight: 20,
    color: MUTED_TEXT_COLOR,
    textAlign: 'center',
  },
  retryButton: {
    minHeight: 44,
    justifyContent: 'center',
    paddingHorizontal: 20,
    borderRadius: 22,
    backgroundColor: SOFT_GREEN_COLOR,
  },
  retryText: { fontSize: 14, fontWeight: '800', color: PRIMARY_COLOR },
  list: { flexShrink: 1 },
  listContent: { gap: 10, padding: 16, paddingBottom: 8 },
  limitNotice: {
    minHeight: 44,
    flexDirection: 'row',
    alignItems: 'center',
    justifyContent: 'center',
    gap: 6,
    paddingHorizontal: 12,
  },
  limitNoticeText: { fontSize: 11, lineHeight: 16, color: MUTED_TEXT_COLOR },
  card: {
    gap: 7,
    padding: 15,
    borderRadius: 18,
    borderWidth: 1,
    borderColor: BORDER_COLOR,
    backgroundColor: SURFACE_COLOR,
    shadowColor: DARK_GREEN_COLOR,
    shadowOffset: { width: 0, height: 4 },
    shadowOpacity: 0.05,
    shadowRadius: 10,
    elevation: 1,
  },
  cardPressed: { opacity: 0.68 },
  cardHeader: {
    flexDirection: 'row',
    alignItems: 'center',
    justifyContent: 'space-between',
    gap: 8,
  },
  badgeRow: {
    minWidth: 0,
    flex: 1,
    flexDirection: 'row',
    alignItems: 'center',
    flexWrap: 'wrap',
    gap: 5,
  },
  badge: { maxWidth: 112, paddingHorizontal: 8, paddingVertical: 3, borderRadius: 9 },
  badgeText: { fontSize: 10, lineHeight: 14, fontWeight: '700' },
  greenBadge: { backgroundColor: SOFT_GREEN_COLOR },
  greenBadgeText: { color: PRIMARY_COLOR },
  urgentBadge: { backgroundColor: ERROR_BACKGROUND_COLOR },
  urgentBadgeText: { color: ERROR_COLOR },
  neutralBadge: { backgroundColor: SOFT_BACKGROUND_COLOR },
  neutralBadgeText: { color: MUTED_TEXT_COLOR },
  memberCount: { fontSize: 11, fontWeight: '700', color: MUTED_TEXT_COLOR },
  cardTitle: {
    fontSize: 16,
    lineHeight: 22,
    fontWeight: '800',
    color: TEXT_COLOR,
    letterSpacing: -0.5,
  },
  cardBody: { fontSize: 13, lineHeight: 19, color: LABEL_COLOR },
  cardFooter: {
    minWidth: 0,
    flexDirection: 'row',
    alignItems: 'center',
    gap: 6,
    marginTop: 2,
  },
  cardFooterItem: {
    minWidth: 0,
    flex: 1,
    flexDirection: 'row',
    alignItems: 'center',
    gap: 5,
  },
  cardMeta: { flexShrink: 1, fontSize: 11, lineHeight: 15, color: MUTED_TEXT_COLOR },
  dDay: { fontSize: 11, fontWeight: '800', color: PRIMARY_COLOR },
});
