import Ionicons from '@expo/vector-icons/Ionicons';
import { router, useLocalSearchParams } from 'expo-router';
import { useMemo, useState } from 'react';
import { ActivityIndicator, Pressable, ScrollView, StyleSheet, Text, View } from 'react-native';
import { useSafeAreaInsets } from 'react-native-safe-area-context';

import { ScreenGlowBackground } from '@/components/ScreenGlowBackground';
import {
  BORDER_COLOR,
  DARK_GREEN_COLOR,
  MUTED_TEXT_COLOR,
  PRIMARY_COLOR,
  SOFT_GREEN_COLOR,
  SURFACE_COLOR,
} from '@/constants/colors';
import { ChecklistListView } from '@/features/checklist/ChecklistListView';
import { RecordComposerView } from '@/features/checklist/RecordComposerView';
import { useChecklist } from '@/features/checklist/useChecklist';
import { useStudyDetail } from '@/features/study/useStudyDetail';

function goBack() {
  if (router.canGoBack()) {
    router.back();
    return;
  }
  router.replace('/(app)/(tabs)/home');
}

export default function FieldVisitRecordsScreen() {
  const { id } = useLocalSearchParams<{ id: string }>();
  const studyId = Number(id);
  const isValidStudyId = Number.isSafeInteger(studyId) && studyId >= 1;
  const insets = useSafeAreaInsets();
  const [openItemId, setOpenItemId] = useState<number | null>(null);
  const studyQuery = useStudyDetail(studyId);
  const study = studyQuery.data;
  const canViewRecords =
    isValidStudyId &&
    study?.isMember === true &&
    study.status !== 'CANCELED' &&
    study.fieldVisitStatus === 'ENDED' &&
    study.fieldSessionId !== null;
  const checklist = useChecklist(studyId, {
    enabled: canViewRecords,
    generateIfMissing: false,
  });
  const openItem = useMemo(
    () =>
      checklist.categories
        .flatMap((category) => category.items)
        .find((item) => item.checklistItemId === openItemId) ?? null,
    [checklist.categories, openItemId],
  );

  return (
    <View style={styles.screen}>
      <ScreenGlowBackground />
      <View style={[styles.header, { paddingTop: insets.top + 10 }]}>
        <Pressable
          accessibilityLabel="스터디 상세로 돌아가기"
          accessibilityRole="button"
          hitSlop={10}
          onPress={goBack}
          style={({ pressed }) => [styles.backButton, pressed && styles.pressed]}
        >
          <Ionicons color={DARK_GREEN_COLOR} name="chevron-back" size={24} />
        </Pressable>
        <View style={styles.headerCopy}>
          <Text style={styles.eyebrow}>종료된 임장</Text>
          <Text style={styles.title}>내 임장 기록</Text>
        </View>
      </View>

      <ScrollView
        contentContainerStyle={[styles.content, { paddingBottom: insets.bottom + 32 }]}
        keyboardShouldPersistTaps="handled"
        showsVerticalScrollIndicator={false}
      >
        {!isValidStudyId ? (
          <StateCard message="올바른 스터디 번호가 아니에요." />
        ) : studyQuery.isLoading ? (
          <ActivityIndicator color={PRIMARY_COLOR} style={styles.loader} size="large" />
        ) : studyQuery.isError ? (
          <StateCard
            actionLabel="다시 시도"
            message="스터디 정보를 불러오지 못했습니다."
            onAction={() => void studyQuery.refetch()}
          />
        ) : !canViewRecords ? (
          <StateCard message={recordsUnavailableMessage(study)} />
        ) : checklist.loadState === 'loading' ? (
          <ActivityIndicator color={PRIMARY_COLOR} style={styles.loader} size="large" />
        ) : checklist.loadState === 'error' ? (
          <StateCard
            actionLabel="다시 시도"
            message={checklist.errorMessage ?? '임장 기록을 불러오지 못했습니다.'}
            onAction={() => void checklist.reload()}
          />
        ) : openItem ? (
          <View style={styles.card}>
            <RecordComposerView
              checklistItemId={openItem.checklistItemId}
              draftText=""
              onBack={() => setOpenItemId(null)}
              onDraftSaved={() => undefined}
              onDraftTextChange={() => undefined}
              // readOnly 라 입력창·피커가 렌더되지 않으므로 포커스 게이트는 쓸 일이 없음.
              onInputFocusChange={() => undefined}
              onRecordCountChange={() => undefined}
              onSttRecordCountRefresh={async () => undefined}
              readOnly
              studyId={studyId}
              subtitle={openItem.subtitle}
              title={openItem.title}
            />
          </View>
        ) : checklist.categories.length === 0 ? (
          <StateCard message="저장된 체크리스트가 없어요." />
        ) : (
          <View style={styles.card}>
            <ChecklistListView
              categories={checklist.categories}
              closeVote={null}
              completedCount={checklist.completedCount}
              isGeneratingRoute={false}
              onFinishVisit={() => undefined}
              onGenerateRoute={() => undefined}
              onOpenRecord={setOpenItemId}
              onToggleItem={() => undefined}
              pendingItemIds={[]}
              readOnly
              route={null}
              totalCount={checklist.totalCount}
            />
          </View>
        )}
      </ScrollView>
    </View>
  );
}

function recordsUnavailableMessage(study: ReturnType<typeof useStudyDetail>['data']): string {
  if (!study?.isMember) return '스터디 멤버만 임장 원문을 볼 수 있어요.';
  if (study.status === 'CANCELED') return '취소된 스터디의 임장 원문은 볼 수 없어요.';
  if (study.fieldVisitStatus !== 'ENDED') return '임장이 종료된 뒤 원문을 볼 수 있어요.';
  return '종료된 임장 정보를 찾지 못했습니다.';
}

function StateCard({
  message,
  actionLabel,
  onAction,
}: {
  message: string;
  actionLabel?: string;
  onAction?: () => void;
}) {
  return (
    <View style={[styles.card, styles.stateCard]}>
      <Text style={styles.stateText}>{message}</Text>
      {actionLabel && onAction ? (
        <Pressable
          accessibilityRole="button"
          onPress={onAction}
          style={({ pressed }) => [styles.retryButton, pressed && styles.pressed]}
        >
          <Text style={styles.retryButtonText}>{actionLabel}</Text>
        </Pressable>
      ) : null}
    </View>
  );
}

const styles = StyleSheet.create({
  screen: { flex: 1, backgroundColor: '#F7F8F2' },
  header: {
    flexDirection: 'row',
    alignItems: 'center',
    gap: 12,
    paddingHorizontal: 18,
    paddingBottom: 14,
  },
  backButton: {
    width: 44,
    height: 44,
    borderRadius: 22,
    alignItems: 'center',
    justifyContent: 'center',
    borderWidth: 1,
    borderColor: BORDER_COLOR,
    backgroundColor: SURFACE_COLOR,
  },
  headerCopy: { gap: 2 },
  eyebrow: { fontSize: 11, fontWeight: '800', color: PRIMARY_COLOR },
  title: { fontSize: 22, fontWeight: '900', color: DARK_GREEN_COLOR },
  content: { paddingHorizontal: 18, paddingTop: 6 },
  card: {
    padding: 18,
    borderRadius: 22,
    borderWidth: 1,
    borderColor: BORDER_COLOR,
    backgroundColor: SURFACE_COLOR,
  },
  loader: { paddingVertical: 64 },
  stateCard: { alignItems: 'center', gap: 14, paddingVertical: 42 },
  stateText: { fontSize: 14, color: MUTED_TEXT_COLOR, textAlign: 'center' },
  retryButton: {
    paddingHorizontal: 18,
    height: 40,
    borderRadius: 20,
    alignItems: 'center',
    justifyContent: 'center',
    backgroundColor: SOFT_GREEN_COLOR,
  },
  retryButtonText: { fontSize: 13, fontWeight: '800', color: PRIMARY_COLOR },
  pressed: { opacity: 0.8 },
});
