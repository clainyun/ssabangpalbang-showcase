import Ionicons from '@expo/vector-icons/Ionicons';
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query';
import { Stack, useLocalSearchParams, useRouter } from 'expo-router';
import { StatusBar } from 'expo-status-bar';
import { useState, type ReactNode } from 'react';
import { ActivityIndicator, Pressable, Share, StyleSheet, Text, View } from 'react-native';
import { useSafeAreaInsets } from 'react-native-safe-area-context';

import { appAlert } from '@/components/AppDialog';
import { GlassIconButton } from '@/components/GlassIconButton';
import { ChatbotModal } from '@/features/chatbot/ChatbotModal';

import {
  DARK_GREEN_COLOR,
  MUTED_TEXT_COLOR,
  PRIMARY_COLOR,
  SCREEN_BACKGROUND_COLOR,
  SOFT_GREEN_COLOR,
} from '@/constants/colors';
import { FloatingTabBar } from '@/components/FloatingTabBar';
import {
  getApartmentDetail,
  getApartmentReports,
  getApartmentStudies,
  getApartmentTransactions,
  type ApartmentDetail,
} from '@/features/apartment/api/apartmentDetail';
import {
  ApartmentDetailListSheet,
  type ApartmentDetailPanel,
} from '@/features/apartment/components/detail/ApartmentDetailListSheet';
import { ApartmentDetailScene } from '@/features/apartment/components/detail/ApartmentDetailScene';
import { favoriteApartment, unfavoriteApartment } from '@/features/member/api/favorites';
import { AuthSessionChangedError, AuthSessionExpiredError } from '@/lib/authenticatedFetch';

/** 화면 내 목록 시트에서 한 번에 노출할 최대 건수. API 허용 범위(1~100) 안입니다. */
const LIST_SIZE = 100;

export const apartmentDetailQueryKey = (apartmentId: number) => ['apartment', apartmentId] as const;

function isSessionTransition(error: unknown): boolean {
  return error instanceof AuthSessionExpiredError || error instanceof AuthSessionChangedError;
}

function DetailState({
  isLoading = false,
  message,
  onBack,
  onRetry,
}: {
  isLoading?: boolean;
  message: string;
  onBack: () => void;
  onRetry?: () => void;
}) {
  const insets = useSafeAreaInsets();

  return (
    <View style={styles.stateScene}>
      <View style={[styles.stateHeader, { top: insets.top + 8 }]}>
        <GlassIconButton accessibilityLabel="뒤로 가기" onPress={onBack}>
          <Ionicons color={DARK_GREEN_COLOR} name="chevron-back" size={25} />
        </GlassIconButton>
      </View>

      <View style={styles.stateCard}>
        {isLoading ? (
          <View style={styles.loadingMark}>
            <ActivityIndicator color={PRIMARY_COLOR} />
          </View>
        ) : (
          <Ionicons color={MUTED_TEXT_COLOR} name="alert-circle-outline" size={30} />
        )}
        <Text style={styles.stateMessage}>{message}</Text>
        {onRetry !== undefined ? (
          <Pressable
            accessibilityRole="button"
            onPress={onRetry}
            style={({ pressed }) => [styles.retryButton, pressed && styles.pressed]}
          >
            <Text style={styles.retryText}>다시 시도</Text>
          </Pressable>
        ) : null}
      </View>
    </View>
  );
}

export default function ApartmentDetailScreen() {
  const { id } = useLocalSearchParams<{ id?: string | string[] }>();
  const rawId = Array.isArray(id) ? id[0] : id;
  const apartmentId = Number(rawId);
  const isValidId = Number.isInteger(apartmentId) && apartmentId >= 1;
  const router = useRouter();
  const queryClient = useQueryClient();
  const [activePanel, setActivePanel] = useState<ApartmentDetailPanel | null>(null);
  const [isChatOpen, setIsChatOpen] = useState(false);

  const detailQuery = useQuery({
    queryKey: apartmentDetailQueryKey(apartmentId),
    enabled: isValidId,
    queryFn: () => getApartmentDetail(apartmentId),
  });

  const transactionArea = detailQuery.data?.latestTransaction?.exclusiveArea ?? undefined;
  const transactionsQuery = useQuery({
    queryKey: ['apartment', apartmentId, 'transactions', 'trend', transactionArea] as const,
    enabled: isValidId && transactionArea !== undefined,
    queryFn: () =>
      getApartmentTransactions(apartmentId, {
        exclusiveArea: transactionArea,
        page: 0,
        size: 100,
        sort: 'DEAL_DATE_DESC',
      }),
  });

  const studiesQuery = useQuery({
    queryKey: ['apartment', apartmentId, 'studies', LIST_SIZE] as const,
    enabled: isValidId && activePanel === 'studies',
    queryFn: () => getApartmentStudies(apartmentId, 0, LIST_SIZE),
  });

  const reportsQuery = useQuery({
    queryKey: ['apartment', apartmentId, 'reports', LIST_SIZE] as const,
    enabled: isValidId && activePanel === 'reports',
    queryFn: () => getApartmentReports(apartmentId, 0, LIST_SIZE),
  });

  const detail = detailQuery.data;

  const favoriteMutation = useMutation({
    mutationFn: () =>
      detail?.favoritedByMe === true
        ? unfavoriteApartment(apartmentId)
        : favoriteApartment(apartmentId),
    onSuccess: (result) => {
      queryClient.setQueryData<ApartmentDetail>(apartmentDetailQueryKey(apartmentId), (previous) =>
        previous === undefined ? previous : { ...previous, favoritedByMe: result.favoritedByMe },
      );
      void queryClient.invalidateQueries({
        queryKey: ['member', 'me', 'favorite-apartments'],
      });
    },
    onError: (error) => {
      // 공통 인증 계층이 로그인 화면 전환을 담당하므로 세션 전환 중에는 중복 경고를 띄우지 않습니다.
      if (isSessionTransition(error)) return;
      appAlert(
        '찜 상태를 바꾸지 못했어요',
        error instanceof Error ? error.message : '잠시 후 다시 시도해 주세요.',
      );
    },
  });

  const handleShare = async () => {
    if (detail === undefined) return;

    const message = [detail.name, detail.address].filter(Boolean).join('\n');
    try {
      await Share.share({ message, title: detail.name });
    } catch {
      appAlert('공유할 수 없어요', '잠시 후 다시 시도해 주세요.');
    }
  };

  const handleStudyPress = (studyId: number) => {
    setActivePanel(null);
    router.push({
      pathname: '/(app)/study/[id]',
      params: { id: String(studyId) },
    });
  };

  const handleReportPress = (reportId: number) => {
    setActivePanel(null);
    router.push({
      pathname: '/(app)/report/[reportId]',
      params: { reportId: String(reportId) },
    });
  };

  let content: ReactNode;

  if (!isValidId) {
    content = (
      <DetailState message="아파트 정보를 찾을 수 없습니다." onBack={() => router.back()} />
    );
  } else if (detailQuery.isPending) {
    content = (
      <DetailState
        isLoading
        message="단지 배치도와 정보를 불러오고 있어요."
        onBack={() => router.back()}
      />
    );
  } else if (detailQuery.isError || detail === undefined) {
    content = (
      <DetailState
        message={
          detailQuery.error instanceof Error
            ? detailQuery.error.message
            : '아파트 정보를 불러오지 못했습니다.'
        }
        onBack={() => router.back()}
        onRetry={() => void detailQuery.refetch()}
      />
    );
  } else {
    const isStudiesPanel = activePanel === 'studies';
    const activeQuery = isStudiesPanel ? studiesQuery : reportsQuery;
    const activeItems = activeQuery.data?.content ?? [];
    const totalElements =
      activeQuery.data?.totalElements ??
      (isStudiesPanel ? (detail.recruitingStudyCount ?? 0) : (detail.completedReportCount ?? 0));

    content = (
      <>
        <ApartmentDetailScene
          detail={detail}
          favoritePending={favoriteMutation.isPending}
          onBack={() => router.back()}
          onOpenChatbot={() => setIsChatOpen(true)}
          onOpenReports={() => setActivePanel('reports')}
          onOpenStudies={() => setActivePanel('studies')}
          onShare={() => void handleShare()}
          onToggleFavorite={() => favoriteMutation.mutate()}
          transactions={transactionsQuery.data?.content ?? []}
        />

        <ApartmentDetailListSheet
          apartmentName={detail.name}
          isError={activeQuery.isError}
          isPending={activeQuery.isPending}
          items={activeItems}
          onClose={() => setActivePanel(null)}
          onReportPress={handleReportPress}
          onRetry={() => void activeQuery.refetch()}
          onStudyPress={handleStudyPress}
          panel={activePanel}
          totalElements={totalElements}
        />
      </>
    );
  }

  return (
    <View style={styles.container}>
      <Stack.Screen options={{ headerShown: false }} />
      <StatusBar style="dark" />
      {content}
      <FloatingTabBar activeTab="map" />

      <ChatbotModal
        apartmentId={apartmentId}
        apartmentName={detail?.name}
        onClose={() => setIsChatOpen(false)}
        open={isChatOpen}
      />
    </View>
  );
}

const styles = StyleSheet.create({
  container: { flex: 1, backgroundColor: SCREEN_BACKGROUND_COLOR },
  stateScene: {
    flex: 1,
    alignItems: 'center',
    justifyContent: 'center',
    paddingHorizontal: 24,
    backgroundColor: '#E7EBE6',
  },
  stateHeader: { position: 'absolute', left: 16 },
  stateCard: {
    width: '100%',
    maxWidth: 320,
    alignItems: 'center',
    gap: 13,
    paddingHorizontal: 24,
    paddingVertical: 28,
    borderRadius: 24,
    borderWidth: 1,
    borderColor: 'rgba(255,255,255,0.82)',
    backgroundColor: 'rgba(255,255,255,0.78)',
  },
  loadingMark: {
    width: 52,
    height: 52,
    alignItems: 'center',
    justifyContent: 'center',
    borderRadius: 26,
    backgroundColor: SOFT_GREEN_COLOR,
  },
  stateMessage: {
    fontSize: 15,
    lineHeight: 22,
    fontWeight: '600',
    color: MUTED_TEXT_COLOR,
    textAlign: 'center',
  },
  retryButton: {
    minHeight: 44,
    justifyContent: 'center',
    paddingHorizontal: 21,
    borderRadius: 22,
    backgroundColor: SOFT_GREEN_COLOR,
  },
  retryText: { fontSize: 14, fontWeight: '800', color: PRIMARY_COLOR },
  pressed: { opacity: 0.68 },
});
