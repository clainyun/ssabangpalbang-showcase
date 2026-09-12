import Ionicons from '@expo/vector-icons/Ionicons';
import { useQuery, useQueryClient } from '@tanstack/react-query';
import { Image as ExpoImage } from 'expo-image';
import * as Location from 'expo-location';
import { LinearGradient } from 'expo-linear-gradient';
import { router, useFocusEffect, useLocalSearchParams } from 'expo-router';
import { useCallback, useMemo, useState } from 'react';
import {
  ActivityIndicator,
  Animated,
  Image,
  Modal,
  PanResponder,
  Pressable,
  ScrollView,
  Share,
  StyleSheet,
  Text,
  TextInput,
  View,
} from 'react-native';
import { useSafeAreaInsets } from 'react-native-safe-area-context';

import { appAlert } from '@/components/AppDialog';
import {
  GlassIconButton,
  GLASS_ICON_BUTTON_ICON_SIZE,
  GLASS_ICON_BUTTON_SIZE,
} from '@/components/GlassIconButton';
import { GlassSurface } from '@/components/GlassSurface';
import { OptionButton } from '@/components/OptionButton';
import { ScreenGlowBackground } from '@/components/ScreenGlowBackground';
import {
  BORDER_COLOR,
  DARK_GREEN_COLOR,
  LABEL_COLOR,
  MODAL_SCRIM_COLOR,
  MUTED_TEXT_COLOR,
  PLACEHOLDER_COLOR,
  PRIMARY_COLOR,
  SOFT_GREEN_COLOR,
  SUBTLE_BACKGROUND_COLOR,
  SURFACE_COLOR,
  TEXT_COLOR,
} from '@/constants/colors';
import { getApartmentDetail } from '@/features/apartment/api/apartmentDetail';
import { formatPurpose } from '@/features/apartment/format';
import { apartmentImageSource } from '@/lib/apartmentImage';
import { explainBeforeRequest, showPermanentlyDeniedAlert } from '@/lib/permissions';
import { refetchOnFocusNow } from '@/lib/refetchOnFocusIfStale';
import { homeSummaryQueryRoot } from '@/features/home/useHomeSummary';
import { useChatUnreadCount } from '@/features/chat/useChatUnreadCount';
import { myPageQueryKeys } from '@/features/member/api/myPageQueryKeys';
import { FieldVisitStartApiError, startFieldVisit } from '@/features/study/api/startFieldVisit';
import { StudyApiError, type StudyPurpose } from '@/features/study/api/types';
import type { StudyDetail } from '@/features/study/api/getStudyDetail';
import { formatScheduleTime, getScheduleDateParts } from '@/features/study/formatStudyDate';
import {
  flattenUniqueNotices,
  formatLoadedNoticeCount,
} from '@/features/study/studyNoticePagination';
import {
  studyDetailQueryKey,
  useApplyToStudy,
  useStudyDetail,
} from '@/features/study/useStudyDetail';
import {
  scheduleQueryKey,
  useLeaveStudy,
  useNotices,
  useReopenRecruitment,
} from '@/features/study/useStudyManagement';
import {
  isDeveloperLocationAvailable,
  useDeveloperLocationStore,
} from '@/store/developerLocationStore';

const APPLY_PURPOSES: readonly StudyPurpose[] = ['RESIDENCE', 'INVESTMENT', 'STUDY'];
const INTRO_MAX_LENGTH = 200;

const apartmentDetailQueryKey = (apartmentId: number) => ['apartment', apartmentId] as const;

const STATUS_LABELS: Record<StudyDetail['status'], string> = {
  RECRUITING: '모집 중',
  CLOSED: '진행 예정',
  IN_PROGRESS: '진행 중',
  COMPLETED: '완료',
  CANCELED: '취소됨',
};

type OverviewTab = 'GOAL' | 'NOTICE' | 'MEMBER';

class DeveloperLocationSetupError extends Error {
  constructor(message: string) {
    super(message);
    this.name = 'DeveloperLocationSetupError';
  }
}

function goBackOrHome() {
  if (router.canGoBack()) router.back();
  else router.replace('/(app)/(tabs)/home');
}

const MEMBER_FACE_SOURCES = {
  PALBANG: require('../../../assets/images/characters/face/plain_f_1.png'),
  PALBANG_RABBIT: require('../../../assets/images/characters/face/rabbit_f_1.png'),
  PALBANG_DOG: require('../../../assets/images/characters/face/dog_f_1.png'),
} as const;

function memberFaceSource(characterId: string) {
  return (
    MEMBER_FACE_SOURCES[characterId as keyof typeof MEMBER_FACE_SOURCES] ??
    MEMBER_FACE_SOURCES.PALBANG
  );
}

type SlideActionProps = {
  disabled: boolean;
  loading: boolean;
  label: string;
  onComplete: () => void;
  /** 비활성일 때 '밀어서 …' 대신 보여줄 안내 문구(예: 아직 시작 시간 아님). */
  disabledText?: string;
};

const SLIDE_HANDLE_SIZE = 56;
const SLIDE_HORIZONTAL_INSET = 8;

function SlideAction({ disabled, loading, label, onComplete, disabledText }: SlideActionProps) {
  const [translateX] = useState(() => new Animated.Value(0));
  const [maxTranslate, setMaxTranslate] = useState(0);

  const resetHandle = useCallback(() => {
    Animated.spring(translateX, {
      toValue: 0,
      useNativeDriver: true,
      speed: 18,
      bounciness: 7,
    }).start();
  }, [translateX]);

  const finishSlide = useCallback(() => {
    if (disabled || loading) return;
    Animated.spring(translateX, {
      toValue: maxTranslate,
      useNativeDriver: true,
      speed: 22,
      bounciness: 0,
    }).start(() => {
      onComplete();
      resetHandle();
    });
  }, [disabled, loading, maxTranslate, onComplete, resetHandle, translateX]);

  const panResponder = useMemo(
    () =>
      PanResponder.create({
        onMoveShouldSetPanResponder: (_, gesture) =>
          !disabled &&
          !loading &&
          Math.abs(gesture.dx) > 4 &&
          Math.abs(gesture.dx) > Math.abs(gesture.dy),
        onPanResponderMove: (_, gesture) => {
          translateX.setValue(Math.max(0, Math.min(gesture.dx, maxTranslate)));
        },
        onPanResponderRelease: (_, gesture) => {
          if (gesture.dx >= maxTranslate * 0.72) finishSlide();
          else resetHandle();
        },
        onPanResponderTerminate: resetHandle,
      }),
    [disabled, finishSlide, loading, maxTranslate, resetHandle, translateX],
  );

  return (
    <View
      accessibilityActions={[{ name: 'activate', label }]}
      accessibilityLabel={`${label}. 오른쪽으로 밀어서 실행`}
      accessibilityRole="button"
      accessibilityState={{ disabled, busy: loading }}
      onAccessibilityAction={(event) => {
        if (event.nativeEvent.actionName === 'activate') finishSlide();
      }}
      onLayout={(event) => {
        setMaxTranslate(
          Math.max(
            0,
            event.nativeEvent.layout.width - SLIDE_HANDLE_SIZE - SLIDE_HORIZONTAL_INSET * 2,
          ),
        );
      }}
      style={[styles.slideAction, disabled && styles.buttonDisabled]}
    >
      <GlassSurface
        tint={disabled ? '#E4E7E5' : '#DDF6E9'}
        radius={28}
        tintOpacity={0.46}
        intensity={26}
      />
      <LinearGradient
        colors={
          disabled
            ? ['rgba(238,240,239,0.72)', 'rgba(220,223,221,0.64)']
            : ['rgba(255,255,255,0.62)', 'rgba(207,244,220,0.58)']
        }
        pointerEvents="none"
        style={StyleSheet.absoluteFill}
      />
      <View pointerEvents="none" style={styles.slideDirectionHint}>
        <Ionicons name="chevron-forward" size={14} color={disabled ? 'rgba(120,128,124,0.24)' : 'rgba(31,95,85,0.24)'} />
        <Ionicons name="chevron-forward" size={14} color={disabled ? 'rgba(120,128,124,0.42)' : 'rgba(31,95,85,0.42)'} />
        <Ionicons name="chevron-forward" size={14} color={disabled ? 'rgba(120,128,124,0.6)' : 'rgba(31,95,85,0.68)'} />
      </View>
      <Text style={styles.slideActionText}>
        {loading
          ? '연결 중...'
          : disabled && disabledText
            ? disabledText
            : `밀어서 ${label}`}
      </Text>
      <Animated.View
        {...panResponder.panHandlers}
        style={[styles.slideHandle, { transform: [{ translateX }] }]}
      >
        <GlassSurface
          tint={disabled ? '#E4E7E5' : '#DDF6E9'}
          radius={22}
          tintOpacity={0.34}
          intensity={32}
        />
        <LinearGradient
          colors={
            disabled
              ? ['rgba(245,246,245,0.92)', 'rgba(220,223,221,0.5)']
              : ['rgba(255,255,255,0.92)', 'rgba(198,244,216,0.48)']
          }
          pointerEvents="none"
          style={StyleSheet.absoluteFill}
        />
        {loading ? (
          <ActivityIndicator color={PRIMARY_COLOR} size="small" />
        ) : (
          <Ionicons name="arrow-forward" size={21} color={PRIMARY_COLOR} />
        )}
      </Animated.View>
    </View>
  );
}

function scheduleOverrideMessage(study: StudyDetail): string | null {
  if (study.canStartFieldVisit) return null;
  if (!study.nextSchedule) {
    return '예정된 임장 일정이 없습니다. 이대로 진행하시겠습니까?';
  }
  return '임장 일정 시간이 되지 않았어요. 이대로 진행하시겠습니까?';
}

function confirmScheduleOverride(message: string): Promise<boolean> {
  return new Promise((resolve) => {
    let settled = false;
    const settle = (confirmed: boolean) => {
      if (settled) return;
      settled = true;
      resolve(confirmed);
    };

    appAlert(
      '임장 일정 확인',
      message,
      [
        { text: '취소', style: 'cancel', onPress: () => settle(false) },
        { text: '진행', onPress: () => settle(true) },
      ],
      { cancelable: true },
    );
  });
}

export default function StudyDetailScreen() {
  const params = useLocalSearchParams<{ id: string }>();
  const studyId = Number(params.id);
  const insets = useSafeAreaInsets();
  const queryClient = useQueryClient();
  const { data: study, isLoading, refetch } = useStudyDetail(studyId);
  const { unreadCount: chatUnreadCount, refetch: refetchChatUnreadCount } =
    useChatUnreadCount(studyId, study?.isMember === true);
  const [isStarting, setIsStarting] = useState(false);
  const [isMoreMenuOpen, setIsMoreMenuOpen] = useState(false);
  // 로드에 실패한 서버 대표 이미지의 '쿼리 제거 경로'. 서명이 회전해도 같은 이미지로 본다.
  const [failedServerImagePath, setFailedServerImagePath] = useState<string | null>(null);
  const [isApplyModalOpen, setIsApplyModalOpen] = useState(false);
  const [applyIntro, setApplyIntro] = useState('');
  const [applyPurpose, setApplyPurpose] = useState<StudyPurpose | null>(null);

  const developerLocationEnabled = useDeveloperLocationStore((state) => state.isEnabled);
  const initializeDeveloperSession = useDeveloperLocationStore((state) => state.initializeSession);
  const isDeveloperMode = isDeveloperLocationAvailable && developerLocationEnabled;

  const apartmentId = study?.apartment.apartmentId;
  const { data: apartmentDetail } = useQuery({
    queryKey: apartmentDetailQueryKey(apartmentId ?? -1),
    enabled: apartmentId !== undefined,
    queryFn: () => getApartmentDetail(apartmentId!),
  });
  const applyMutation = useApplyToStudy(studyId);
  const leaveMutation = useLeaveStudy(studyId);
  const reopenMutation = useReopenRecruitment(studyId);
  const noticesQuery = useNotices(studyId, study?.isMember ?? false);
  const canLeaveStudy =
    study?.isMember === true &&
    !study.isLeader &&
    (study.status === 'RECRUITING' || study.status === 'CLOSED');
  // 스터디장이 조기 마감(CLOSED)한 모집을 다시 열 수 있는 조건.
  const canReopenRecruitment = study?.isLeader === true && study.status === 'CLOSED';

  const handleOpenChat = useCallback(() => {
    if (!study) return;
    // 완료된 스터디는 대화방 입장을 막고 안내 팝업만 띄운다(다른 안내와 동일한 appAlert).
    if (study.status === 'COMPLETED') {
      appAlert('완료된 스터디예요', '완료된 스터디의 대화방은 더 이상 들어갈 수 없어요.');
      return;
    }
    router.push({
      pathname: '/(app)/chat/[roomId]',
      params: {
        roomId: String(study.studyId),
        studyName: study.title,
        participantCount: String(study.currentMemberCount),
      },
    });
  }, [study]);

  const handleShare = useCallback(() => {
    if (!study) return;
    void Share.share({
      message: `[싸방팔방] ${study.title}\n${study.apartment.name} · ${study.apartment.address}`,
    });
  }, [study]);

  const handleGoToApartment = useCallback(() => {
    if (!study) return;
    router.push({
      pathname: '/(app)/apartment/[id]',
      params: { id: String(study.apartment.apartmentId) },
    });
  }, [study]);

  const handleOpenOverview = useCallback(
    (tab: OverviewTab) => {
      if (!study) return;
      router.push({
        pathname: '/(app)/study/[id]/overview',
        params: { id: String(study.studyId), tab },
      });
    },
    [study],
  );

  const handleOpenStudyManagement = useCallback(() => {
    if (!study) return;
    setIsMoreMenuOpen(false);
    router.push({
      pathname: '/(app)/study/[id]/manage',
      params: { id: String(study.studyId) },
    });
  }, [study]);

  const handleOpenStudySettings = useCallback(() => {
    if (!study) return;
    setIsMoreMenuOpen(false);
    router.push({
      pathname: '/(app)/study/[id]/settings',
      params: { id: String(study.studyId) },
    });
  }, [study]);

  useFocusEffect(
    useCallback(() => {
      if (!Number.isFinite(studyId)) return;
      // 승인·모집 마감처럼 화면 밖에서 바뀐 상태가 바로 보여야 하는 화면이라, 상세는
      // 20초 게이트 없이 포커스마다 다시 조회합니다(채팅 안 읽음 수도 동일).
      refetchOnFocusNow(queryClient, studyDetailQueryKey(studyId));
      void refetchChatUnreadCount();
    }, [queryClient, refetchChatUnreadCount, studyId]),
  );

  const handleOpenScheduleManagement = useCallback(() => {
    if (!study) return;
    router.push({
      pathname: '/(app)/study/[id]/manage',
      params: { id: String(study.studyId), tab: 'SCHEDULE' },
    });
  }, [study]);

  const handleRequestLeaveStudy = useCallback(() => {
    if (!study || leaveMutation.isPending) return;
    setIsMoreMenuOpen(false);
    appAlert('스터디 나가기', '나가면 다시 참여할 수 없어요. 정말 나갈까요?', [
      { text: '취소', style: 'cancel' },
      {
        text: '나가기',
        style: 'destructive',
        onPress: () =>
          leaveMutation.mutate(undefined, {
            onSuccess: () => router.replace('/(app)/(tabs)/home'),
            onError: (error) =>
              appAlert(
                '스터디 나가기 실패',
                error instanceof StudyApiError
                  ? error.message
                  : '스터디에서 나가지 못했습니다.',
              ),
          }),
      },
    ]);
  }, [leaveMutation, study]);

  const handleRequestReopenRecruitment = useCallback(() => {
    if (!study || reopenMutation.isPending) return;
    appAlert('모집 마감 취소', '다시 스터디원을 모집할까요?', [
      { text: '취소', style: 'cancel' },
      {
        text: '모집 재개',
        onPress: () =>
          reopenMutation.mutate(undefined, {
            onSuccess: () => setIsMoreMenuOpen(false),
            onError: (error) =>
              appAlert(
                '모집 마감 취소 실패',
                error instanceof StudyApiError
                  ? error.message
                  : '모집 마감을 취소하지 못했습니다.',
              ),
          }),
      },
    ]);
  }, [reopenMutation, study]);

  const handleOpenApplyModal = useCallback(() => {
    setApplyIntro('');
    setApplyPurpose(null);
    setIsApplyModalOpen(true);
  }, []);

  const handleSubmitApply = useCallback(() => {
    if (applyIntro.trim().length === 0 || applyPurpose === null) return;
    applyMutation.mutate(
      { intro: applyIntro.trim(), purpose: applyPurpose },
      {
        onSuccess: () => setIsApplyModalOpen(false),
        onError: (error) =>
          appAlert(
            '신청 실패',
            error instanceof StudyApiError ? error.message : '신청하지 못했습니다.',
          ),
      },
    );
  }, [applyIntro, applyPurpose, applyMutation]);

  const handleStartFieldVisit = useCallback(async () => {
    if (!study || isStarting) return;
    setIsStarting(true);
    try {
      const refreshed = await refetch();
      if (refreshed.isError || !refreshed.data) {
        appAlert('일정을 확인하지 못했어요', '최신 임장 일정을 불러온 뒤 다시 시도해 주세요.');
        return;
      }
      const latestStudy = refreshed.data;

      const overrideMessage = scheduleOverrideMessage(latestStudy);
      let scheduleOverrideConfirmed = false;
      if (overrideMessage) {
        scheduleOverrideConfirmed = await confirmScheduleOverride(overrideMessage);
        if (!scheduleOverrideConfirmed) return;
      }

      if (!isDeveloperMode) {
        const currentPermission = await Location.getForegroundPermissionsAsync();
        let permission = currentPermission;
        if (!currentPermission.granted) {
          const shouldRequest = await explainBeforeRequest('location');
          if (!shouldRequest) return;
          permission = await Location.requestForegroundPermissionsAsync();
        }
        if (!permission.granted) {
          if (permission.canAskAgain) {
            appAlert('위치 권한 필요', '임장을 시작하려면 위치 권한을 허용해 주세요.');
          } else {
            showPermanentlyDeniedAlert('location');
          }
          return;
        }
      }

      let latitude: number;
      let longitude: number;

      if (isDeveloperMode) {
        const apartment = await getApartmentDetail(latestStudy.apartment.apartmentId);
        if (
          apartment.latitude === null ||
          apartment.longitude === null ||
          !Number.isFinite(apartment.latitude) ||
          !Number.isFinite(apartment.longitude)
        ) {
          throw new DeveloperLocationSetupError('대상 아파트에 사용할 수 있는 좌표가 없어요.');
        }
        const virtualCoordinate = initializeDeveloperSession([
          apartment.longitude,
          apartment.latitude,
        ]);
        longitude = virtualCoordinate[0];
        latitude = virtualCoordinate[1];
      } else {
        const position = await Location.getCurrentPositionAsync({});
        latitude = position.coords.latitude;
        longitude = position.coords.longitude;
      }

      const result = await startFieldVisit(
        latestStudy.studyId,
        latitude,
        longitude,
        scheduleOverrideConfirmed,
      );
      void Promise.all([
        queryClient.invalidateQueries({ queryKey: studyDetailQueryKey(latestStudy.studyId) }),
        queryClient.invalidateQueries({ queryKey: scheduleQueryKey(latestStudy.studyId) }),
        queryClient.invalidateQueries({ queryKey: myPageQueryKeys.studiesRoot }),
        queryClient.invalidateQueries({ queryKey: myPageQueryKeys.visitCalendarRoot }),
        queryClient.invalidateQueries({ queryKey: homeSummaryQueryRoot }),
      ]);
      router.push({
        pathname: '/(app)/field/[sessionId]',
        params: {
          sessionId: String(result.session.sessionId),
          studyId: String(latestStudy.studyId),
          apartmentId: String(result.apartmentId),
          apartmentName: latestStudy.apartment.name,
        },
      });
    } catch (error) {
      if (error instanceof DeveloperLocationSetupError) {
        appAlert('가상 위치를 준비하지 못했어요', error.message);
      } else if (isDeveloperMode && !(error instanceof FieldVisitStartApiError)) {
        appAlert(
          '가상 위치를 준비하지 못했어요',
          '대상 아파트 위치를 불러오지 못했습니다. 잠시 후 다시 시도해 주세요.',
        );
      } else if (
        error instanceof FieldVisitStartApiError &&
        error.code === 'FIELD_VISIT_OUT_OF_RANGE'
      ) {
        const data = error.data as { distanceMeters?: number; allowedRadiusMeters?: number } | null;
        appAlert(
          '위치를 확인해 주세요',
          data?.distanceMeters !== undefined && data?.allowedRadiusMeters !== undefined
            ? `대상 아파트에서 ${data.distanceMeters}m 떨어져 있어요. ${data.allowedRadiusMeters}m 이내에서 시작할 수 있어요.`
            : '대상 아파트 근처에서만 임장을 시작할 수 있어요.',
        );
      } else {
        appAlert(
          '오류',
          error instanceof FieldVisitStartApiError ? error.message : '임장을 시작하지 못했습니다.',
        );
      }
    } finally {
      setIsStarting(false);
    }
  }, [initializeDeveloperSession, isDeveloperMode, isStarting, queryClient, refetch, study]);

  if (isLoading) {
    return (
      <View style={styles.centerScreen}>
        <ActivityIndicator color={PRIMARY_COLOR} size="large" />
      </View>
    );
  }

  if (!study) {
    return (
      <View style={styles.centerScreen}>
        <Text style={styles.errorText}>스터디 정보를 불러오지 못했어요.</Text>
        <Pressable onPress={() => void refetch()} style={styles.retryButton}>
          <Text style={styles.retryButtonText}>다시 시도</Text>
        </Pressable>
      </View>
    );
  }

  const dateParts = study.nextSchedule ? getScheduleDateParts(study.nextSchedule.startAt) : null;
  const startButtonLabel =
    study.fieldVisitStatus === 'IN_PROGRESS' ? '임장 참여하기' : '임장 시작하기';
  const canAttemptFieldVisit =
    study.permissions.canUseFieldVisit &&
    (study.status === 'CLOSED' || study.status === 'IN_PROGRESS') &&
    study.fieldVisitStatus !== 'ENDED';
  // 비활성 슬라이드 버튼 안내 문구(우선순위). 모집 중이면 모집 안내, 임장이 이미
  // 끝났으면 종료 안내. (시작 처리 중은 SlideAction의 loading이 '연결 중...'로 처리.)
  const isRecruitingOpen = study.status === 'RECRUITING';
  const slideDisabledText = isRecruitingOpen
    ? '아직 스터디원 모집이 끝나지 않았어요'
    : study.fieldVisitStatus === 'ENDED'
      ? '이미 종료된 임장이에요'
      : undefined;
  const serverImageUrl = apartmentDetail?.imageUrl?.trim() || null;
  // Presigned GET URL은 요청마다 서명·만료 쿼리가 바뀌므로, 쿼리를 뗀 S3 객체 경로를
  // 캐시 키와 실패 비교 기준으로 쓴다. 전체 URL로 실패를 기억하면 서명이 회전한 같은
  // 이미지를 계속 새 이미지로 착각해 깨진 로드를 반복한다.
  const serverImagePath = serverImageUrl?.split('?')[0] ?? null;
  const hasServerImage = serverImageUrl !== null && failedServerImagePath !== serverImagePath;
  const heroImageSource = hasServerImage
    ? { uri: serverImageUrl, cacheKey: serverImagePath ?? undefined }
    : apartmentImageSource(study.apartment.apartmentId);
  const notices = flattenUniqueNotices(noticesQuery.data?.pages ?? []);
  const noticeCount = formatLoadedNoticeCount(notices.length, noticesQuery.hasNextPage);
  const unreadChatCount = chatUnreadCount ?? study.unreadChatCount ?? 0;

  return (
    <View style={styles.screen}>
      <LinearGradient
        colors={['#F9FBF7', '#EAF5EE', '#F8FCF9']}
        locations={[0, 0.55, 1]}
        pointerEvents="none"
        style={StyleSheet.absoluteFill}
      />
      <ScreenGlowBackground />

      <ScrollView
        alwaysBounceVertical={false}
        bounces={false}
        contentContainerStyle={[
          styles.scrollContent,
          {
            paddingTop: insets.top + 60,
            paddingBottom: study.isMember ? Math.max(120, insets.bottom + 104) : 44,
          },
        ]}
        overScrollMode="never"
        showsVerticalScrollIndicator={false}
      >
        <Pressable
          onPress={handleGoToApartment}
          style={({ pressed }) => [styles.apartmentCard, pressed && styles.pressedOpacity]}
        >
          <View style={styles.apartmentCardIcon}>
            <Ionicons name="business" size={18} color={PRIMARY_COLOR} />
          </View>
          <View style={styles.apartmentCardCopy}>
            <Text style={styles.apartmentCardEyebrow}>임장 아파트</Text>
            <Text style={styles.apartmentCardName} numberOfLines={1}>
              {study.apartment.name}
            </Text>
            <Text style={styles.apartmentCardAddress} numberOfLines={1}>
              {study.apartment.address}
            </Text>
          </View>
          <View style={styles.apartmentCardAction}>
            <Text style={styles.apartmentCardActionText}>단지 보기</Text>
            <Ionicons name="chevron-forward" size={15} color={PRIMARY_COLOR} />
          </View>
        </Pressable>

        {study.isMember && (
          <Pressable
            onPress={() => handleOpenOverview('MEMBER')}
            style={({ pressed }) => [styles.memberSummary, pressed && styles.pressedOpacity]}
          >
            <Image
              resizeMode="contain"
              source={memberFaceSource(study.leader.selectedCharacterId)}
              style={styles.memberSummaryAvatar}
            />
            <View style={styles.memberSummaryCopy}>
              <Text style={styles.memberSummaryTitle} numberOfLines={1}>
                {study.leader.nickname}
                {study.currentMemberCount > 1 ? ` 외 ${study.currentMemberCount - 1}명` : ''}
              </Text>
              <Text style={styles.memberSummaryMeta}>
                스터디장 · {STATUS_LABELS[study.status]} · {study.currentMemberCount}/
                {study.capacity}명
              </Text>
            </View>
            <Ionicons name="chevron-forward" size={16} color={PRIMARY_COLOR} />
          </Pressable>
        )}

        <View style={styles.heroStage}>
          <ExpoImage
            onError={hasServerImage ? () => setFailedServerImagePath(serverImagePath) : undefined}
            contentFit="contain"
            source={heroImageSource}
            style={styles.heroImage}
            cachePolicy="memory-disk"
          />
          <LinearGradient
            colors={['rgba(243,249,245,0)', 'rgba(243,249,245,0.42)', '#F3F9F5']}
            end={{ x: 1, y: 0.5 }}
            locations={[0.35, 0.68, 1]}
            pointerEvents="none"
            start={{ x: 0, y: 0.5 }}
            style={StyleSheet.absoluteFill}
          />
          <LinearGradient
            colors={['#F3F9F5', 'rgba(243,249,245,0)']}
            pointerEvents="none"
            style={styles.heroTopFade}
          />

          {study.isMember && (
            <View style={styles.scheduleBubbleFrame}>
              <View style={[styles.scheduleBubble, styles.scheduleBubbleShape]}>
                <GlassSurface
                  intensity={12}
                  radius={34}
                  showRim={false}
                  tint="#FFFFFF"
                  tintOpacity={0.16}
                />
                <Text style={styles.scheduleBubbleLabel}>다음 임장</Text>
                {study.nextSchedule && dateParts ? (
                  <>
                    <Text style={styles.scheduleBubbleDate}>
                      {String(dateParts.month).padStart(2, '0')}.
                      {String(dateParts.day).padStart(2, '0')}
                    </Text>
                    <Text style={styles.scheduleBubbleWeekday}>{dateParts.weekdayKo}요일</Text>
                    <View style={styles.scheduleBubbleDivider} />
                    <View style={styles.scheduleBubbleFact}>
                      <Ionicons name="time-outline" size={14} color={PRIMARY_COLOR} />
                      <Text style={styles.scheduleBubbleFactText}>
                        {formatScheduleTime(study.nextSchedule.startAt)}
                      </Text>
                    </View>
                    <View style={styles.scheduleBubbleFact}>
                      <Ionicons name="location-outline" size={14} color={PRIMARY_COLOR} />
                      <Text style={styles.scheduleBubblePlace} numberOfLines={2}>
                        {study.nextSchedule.meetingPlace}
                      </Text>
                    </View>
                  </>
                ) : (
                  <View style={styles.scheduleEmpty}>
                    <Ionicons name="calendar-outline" size={24} color={PRIMARY_COLOR} />
                    <Text style={styles.scheduleEmptyTitle}>일정 준비 중</Text>
                    <Text style={styles.scheduleEmptyText}>등록되면 알려드릴게요</Text>
                  </View>
                )}
                {study.permissions.canManageSchedule && (
                  <Pressable
                    onPress={handleOpenScheduleManagement}
                    style={({ pressed }) => [
                      styles.scheduleEditButton,
                      pressed && styles.pressedOpacity,
                    ]}
                  >
                    <Ionicons name="pencil" size={13} color={DARK_GREEN_COLOR} />
                    <Text style={styles.scheduleEditButtonText}>
                      {study.nextSchedule ? '일정 변경' : '일정 등록'}
                    </Text>
                  </Pressable>
                )}
              </View>
            </View>
          )}

          {study.isMember && (
            <Pressable
              accessibilityLabel={
                unreadChatCount > 0
                  ? `스터디 대화방 열기, 읽지 않은 채팅 ${unreadChatCount}개`
                  : '스터디 대화방 열기'
              }
              onPress={handleOpenChat}
              style={({ pressed }) => [styles.chatCardFrame, pressed && styles.pressedOpacity]}
            >
              <View style={[styles.chatCard, styles.chatCardShape]}>
                <GlassSurface
                  intensity={12}
                  radius={29}
                  showRim={false}
                  tint="#FFFFFF"
                  tintOpacity={0.16}
                />
                <View style={styles.chatCardIcon}>
                  <Ionicons name="chatbubble-ellipses-outline" size={19} color={PRIMARY_COLOR} />
                </View>
                <View style={styles.chatCardCopy}>
                  <Text style={styles.chatCardEyebrow}>스터디 대화</Text>
                  <Text style={styles.chatCardTitle}>대화방 열기</Text>
                </View>
                <Ionicons name="arrow-forward" size={17} color={DARK_GREEN_COLOR} />
              </View>
              {unreadChatCount > 0 && <View style={styles.chatUnreadPin} />}
            </Pressable>
          )}
        </View>

        {study.isMember ? (
          <View style={styles.overviewNavigation}>
            <Pressable
              onPress={() => handleOpenOverview('GOAL')}
              style={({ pressed }) => [styles.overviewButton, pressed && styles.pressedOpacity]}
            >
              <Ionicons name="flag-outline" size={19} color={PRIMARY_COLOR} />
              <Text style={styles.overviewButtonText}>목표</Text>
            </Pressable>
            <View style={styles.overviewDivider} />
            <Pressable
              onPress={() => handleOpenOverview('NOTICE')}
              style={({ pressed }) => [styles.overviewButton, pressed && styles.pressedOpacity]}
            >
              <Ionicons name="megaphone-outline" size={19} color={PRIMARY_COLOR} />
              <Text style={styles.overviewButtonText}>
                공지{noticeCount ? ` ${noticeCount}` : ''}
              </Text>
            </Pressable>
            <View style={styles.overviewDivider} />
            <Pressable
              onPress={() => handleOpenOverview('MEMBER')}
              style={({ pressed }) => [styles.overviewButton, pressed && styles.pressedOpacity]}
            >
              <Ionicons name="people-outline" size={20} color={PRIMARY_COLOR} />
              <Text style={styles.overviewButtonText}>멤버 {study.currentMemberCount}</Text>
            </Pressable>
          </View>
        ) : (
          <View style={styles.applicationCard}>
            {study.myParticipationStatus === 'PENDING' ? (
              <>
                <View style={styles.pendingBadge}>
                  <Text style={styles.pendingBadgeText}>승인 대기 중</Text>
                </View>
                <Text style={styles.applicationText}>스터디장의 승인을 기다리고 있어요.</Text>
              </>
            ) : study.canApply ? (
              <>
                <Text style={styles.applicationTitle}>이 단지를 함께 둘러볼까요?</Text>
                <Text style={styles.applicationText}>
                  한 줄 소개와 참여 목적을 남기면 스터디장에게 신청이 전달돼요.
                </Text>
                <Pressable
                  onPress={handleOpenApplyModal}
                  style={({ pressed }) => [styles.applyButton, pressed && styles.pressedOpacity]}
                >
                  <Text style={styles.applyButtonText}>참여 신청하기</Text>
                </Pressable>
              </>
            ) : study.myParticipationStatus === 'REJECTED' ? (
              <Text style={styles.applicationText}>
                이전 신청이 거절됐어요. 이 스터디에는 다시 신청할 수 없어요.
              </Text>
            ) : (
              <Text style={styles.applicationText}>모집이 마감된 스터디예요.</Text>
            )}
          </View>
        )}
      </ScrollView>

      <View style={[styles.appbar, { paddingTop: insets.top }]}>
        <LinearGradient
          colors={['rgba(249,252,249,0.99)', 'rgba(236,248,241,0.82)', 'rgba(236,248,241,0)']}
          locations={[0, 0.66, 1]}
          pointerEvents="none"
          style={styles.appbarGradient}
        />
        <View style={styles.appbarSide}>
          <GlassIconButton accessibilityLabel="뒤로 가기" onPress={goBackOrHome}>
            <Ionicons name="chevron-back" size={25} color={DARK_GREEN_COLOR} />
          </GlassIconButton>
        </View>
        <Text style={styles.appbarTitle} numberOfLines={1}>
          {study.title}
        </Text>
        <View style={[styles.appbarSide, styles.appbarActions]}>
          <GlassIconButton accessibilityLabel="스터디 공유하기" onPress={handleShare}>
            <Ionicons
              name="share-outline"
              size={GLASS_ICON_BUTTON_ICON_SIZE}
              color={DARK_GREEN_COLOR}
            />
          </GlassIconButton>
          {study.isMember && (
            <GlassIconButton
              accessibilityLabel="스터디 더보기"
              expanded={isMoreMenuOpen}
              onPress={() => setIsMoreMenuOpen(true)}
            >
              <Ionicons
                name="ellipsis-horizontal"
                size={GLASS_ICON_BUTTON_ICON_SIZE}
                color={DARK_GREEN_COLOR}
              />
            </GlassIconButton>
          )}
        </View>
      </View>

      {study.isMember && (
        <View style={[styles.footer, { paddingBottom: Math.max(16, insets.bottom) }]}>
          {study.readOnly ? (
            <Text style={styles.readOnlyNotice}>종료된 스터디입니다.</Text>
          ) : (
            <SlideAction
              disabled={!canAttemptFieldVisit || isStarting}
              disabledText={slideDisabledText}
              label={startButtonLabel}
              loading={isStarting}
              onComplete={() => void handleStartFieldVisit()}
            />
          )}
        </View>
      )}

      <Modal
        animationType="fade"
        onRequestClose={() => setIsMoreMenuOpen(false)}
        transparent
        visible={isMoreMenuOpen}
      >
        <Pressable style={styles.menuScrim} onPress={() => setIsMoreMenuOpen(false)}>
          <Pressable style={styles.menuSheet} onPress={(event) => event.stopPropagation()}>
            <View style={styles.menuHandle} />
            <Text style={styles.menuTitle}>스터디 메뉴</Text>
            <View style={styles.menuItemList}>
              <Pressable
                onPress={handleOpenStudySettings}
                style={({ pressed }) => [styles.menuItem, pressed && styles.pressedOpacity]}
              >
                <View style={styles.menuItemIcon}>
                  <Ionicons name="notifications-outline" size={20} color={PRIMARY_COLOR} />
                </View>
                <View style={styles.menuItemCopy}>
                  <Text style={styles.menuItemTitle}>스터디 대화방 알림 설정</Text>
                  <Text style={styles.menuItemDescription}>대화방 푸시 알림을 설정해요</Text>
                </View>
                <Ionicons name="chevron-forward" size={17} color={MUTED_TEXT_COLOR} />
              </Pressable>
              {study.isLeader ? (
                <Pressable
                  onPress={handleOpenStudyManagement}
                  style={({ pressed }) => [styles.menuItem, pressed && styles.pressedOpacity]}
                >
                  <View style={styles.menuItemIcon}>
                    <Ionicons name="settings-outline" size={20} color={PRIMARY_COLOR} />
                  </View>
                  <View style={styles.menuItemCopy}>
                    <Text style={styles.menuItemTitle}>스터디 운영</Text>
                    <Text style={styles.menuItemDescription}>신청자, 일정과 멤버를 관리해요</Text>
                  </View>
                  <Ionicons name="chevron-forward" size={17} color={MUTED_TEXT_COLOR} />
                </Pressable>
              ) : (
                <Pressable
                  disabled={!canLeaveStudy || leaveMutation.isPending}
                  onPress={handleRequestLeaveStudy}
                  style={({ pressed }) => [
                    styles.menuItem,
                    (!canLeaveStudy || leaveMutation.isPending) && styles.menuItemDisabled,
                    pressed && canLeaveStudy && styles.pressedOpacity,
                  ]}
                >
                  <View style={[styles.menuItemIcon, styles.menuItemIconDanger]}>
                    <Ionicons name="exit-outline" size={21} color="#D94B4B" />
                  </View>
                  <View style={styles.menuItemCopy}>
                    <Text style={styles.menuItemTitleDanger}>스터디 나가기</Text>
                    <Text style={styles.menuItemDescription}>
                      {leaveMutation.isPending
                        ? '나가는 중이에요'
                        : canLeaveStudy
                          ? '나가면 다시 참여할 수 없어요'
                          : '임장 시작 전까지만 나갈 수 있어요'}
                    </Text>
                  </View>
                  <Ionicons name="chevron-forward" size={17} color="#D94B4B" />
                </Pressable>
              )}
              {canReopenRecruitment && (
                <Pressable
                  disabled={reopenMutation.isPending}
                  onPress={handleRequestReopenRecruitment}
                  style={({ pressed }) => [
                    styles.menuItem,
                    reopenMutation.isPending && styles.menuItemDisabled,
                    pressed && !reopenMutation.isPending && styles.pressedOpacity,
                  ]}
                >
                  <View style={styles.menuItemIcon}>
                    <Ionicons name="megaphone-outline" size={20} color={PRIMARY_COLOR} />
                  </View>
                  <View style={styles.menuItemCopy}>
                    <Text style={styles.menuItemTitle}>모집 마감 취소</Text>
                    <Text style={styles.menuItemDescription}>
                      {reopenMutation.isPending
                        ? '모집을 다시 여는 중이에요'
                        : '다시 스터디원을 모집해요'}
                    </Text>
                  </View>
                  <Ionicons name="chevron-forward" size={17} color={MUTED_TEXT_COLOR} />
                </Pressable>
              )}
            </View>
            <Pressable
              onPress={() => setIsMoreMenuOpen(false)}
              style={({ pressed }) => [styles.menuCloseButton, pressed && styles.pressedOpacity]}
            >
              <Text style={styles.menuCloseButtonText}>닫기</Text>
            </Pressable>
          </Pressable>
        </Pressable>
      </Modal>

      <Modal
        animationType="fade"
        onRequestClose={() => setIsApplyModalOpen(false)}
        transparent
        visible={isApplyModalOpen}
      >
        <Pressable style={styles.modalScrim} onPress={() => setIsApplyModalOpen(false)}>
          <Pressable style={styles.modalCard} onPress={(event) => event.stopPropagation()}>
            <Text style={styles.modalTitle}>{study.title} 참여 신청</Text>
            <View style={styles.modalLabelRow}>
              <Text style={styles.modalFieldLabel}>한 줄 소개</Text>
              <Text style={styles.modalCounterText}>
                {applyIntro.length}/{INTRO_MAX_LENGTH}
              </Text>
            </View>
            <TextInput
              maxLength={INTRO_MAX_LENGTH}
              multiline
              onChangeText={setApplyIntro}
              placeholder="예: 옥수동에 실거주를 고려하고 있습니다."
              placeholderTextColor={PLACEHOLDER_COLOR}
              style={styles.modalTextInput}
              textAlignVertical="top"
              value={applyIntro}
            />
            <Text style={styles.modalFieldLabel}>참여 목적</Text>
            <View style={styles.modalPurposeRow}>
              {APPLY_PURPOSES.map((purpose) => (
                <OptionButton
                  key={purpose}
                  label={formatPurpose(purpose) ?? purpose}
                  onPress={() => setApplyPurpose(purpose)}
                  selected={applyPurpose === purpose}
                  style={styles.modalPurposeButton}
                />
              ))}
            </View>
            <Pressable
              disabled={
                applyIntro.trim().length === 0 || applyPurpose === null || applyMutation.isPending
              }
              onPress={handleSubmitApply}
              style={[
                styles.applyButton,
                styles.modalSubmitButton,
                (applyIntro.trim().length === 0 ||
                  applyPurpose === null ||
                  applyMutation.isPending) &&
                  styles.buttonDisabled,
              ]}
            >
              {applyMutation.isPending ? (
                <ActivityIndicator color={SOFT_GREEN_COLOR} size="small" />
              ) : (
                <Text style={styles.applyButtonText}>신청하기</Text>
              )}
            </Pressable>
          </Pressable>
        </Pressable>
      </Modal>
    </View>
  );
}

const styles = StyleSheet.create({
  screen: { flex: 1, backgroundColor: SURFACE_COLOR },
  centerScreen: {
    flex: 1,
    alignItems: 'center',
    justifyContent: 'center',
    gap: 12,
    backgroundColor: SURFACE_COLOR,
  },
  errorText: { fontSize: 13, color: MUTED_TEXT_COLOR },
  retryButton: {
    height: 38,
    paddingHorizontal: 18,
    borderRadius: 19,
    alignItems: 'center',
    justifyContent: 'center',
    backgroundColor: SOFT_GREEN_COLOR,
  },
  retryButtonText: { fontSize: 13, fontWeight: '800', color: PRIMARY_COLOR },
  pressedOpacity: { opacity: 0.7 },
  buttonDisabled: { opacity: 0.48 },

  scrollContent: { paddingHorizontal: 18, gap: 10 },
  appbar: {
    position: 'absolute',
    top: 0,
    left: 0,
    right: 0,
    height: 96,
    paddingHorizontal: 18,
    paddingBottom: 8,
    flexDirection: 'row',
    alignItems: 'center',
    overflow: 'visible',
    backgroundColor: 'transparent',
  },
  appbarGradient: {
    position: 'absolute',
    top: 0,
    left: 0,
    right: 0,
    height: 132,
  },
  // 좌우 폭을 같게 잡아 가운데 제목이 밀리지 않게 합니다(버튼 2개 + 간격).
  appbarSide: {
    width: GLASS_ICON_BUTTON_SIZE * 2 + 8,
    flexDirection: 'row',
    alignItems: 'center',
  },
  appbarActions: { justifyContent: 'flex-end', gap: 8 },
  appbarTitle: {
    flex: 1,
    paddingHorizontal: 8,
    textAlign: 'center',
    fontSize: 18,
    lineHeight: 24,
    fontWeight: '900',
    color: TEXT_COLOR,
  },

  apartmentCard: {
    minHeight: 76,
    paddingHorizontal: 14,
    paddingVertical: 11,
    borderRadius: 22,
    flexDirection: 'row',
    alignItems: 'center',
    gap: 11,
    backgroundColor: 'rgba(255,255,255,0.82)',
    borderWidth: 1,
    borderColor: 'rgba(39,111,80,0.11)',
  },
  apartmentCardIcon: {
    width: 42,
    height: 42,
    borderRadius: 15,
    alignItems: 'center',
    justifyContent: 'center',
    backgroundColor: '#E1F5E9',
  },
  apartmentCardCopy: { flex: 1, minWidth: 0 },
  apartmentCardEyebrow: { fontSize: 11, fontWeight: '800', color: PRIMARY_COLOR },
  apartmentCardName: { marginTop: 2, fontSize: 16, fontWeight: '900', color: TEXT_COLOR },
  apartmentCardAddress: { marginTop: 2, fontSize: 11, color: MUTED_TEXT_COLOR },
  apartmentCardAction: { flexDirection: 'row', alignItems: 'center', gap: 2 },
  apartmentCardActionText: { fontSize: 11, fontWeight: '800', color: PRIMARY_COLOR },

  memberSummary: {
    minHeight: 56,
    paddingHorizontal: 12,
    paddingVertical: 8,
    borderRadius: 20,
    flexDirection: 'row',
    alignItems: 'center',
    gap: 10,
    backgroundColor: 'rgba(255,255,255,0.68)',
    borderWidth: 1,
    borderColor: 'rgba(39,111,80,0.09)',
  },
  memberSummaryAvatar: { width: 48, height: 48 },
  memberSummaryCopy: { flex: 1, minWidth: 0 },
  memberSummaryTitle: { fontSize: 14, fontWeight: '900', color: TEXT_COLOR },
  memberSummaryMeta: { marginTop: 3, fontSize: 11, color: MUTED_TEXT_COLOR },

  heroStage: {
    height: 374,
    marginHorizontal: -18,
    overflow: 'hidden',
    backgroundColor: '#F3F9F5',
  },
  heroImage: {
    position: 'absolute',
    left: -20,
    bottom: -10,
    width: '79%',
    height: '96%',
  },
  heroTopFade: { position: 'absolute', left: 0, right: 0, top: 0, height: 64 },
  scheduleBubbleFrame: {
    position: 'absolute',
    top: 22,
    right: 16,
    width: 152,
    overflow: 'visible',
    shadowColor: '#16372B',
    shadowOffset: { width: 0, height: 7 },
    shadowOpacity: 0.04,
    shadowRadius: 10,
  },
  scheduleBubbleShape: {
    borderTopLeftRadius: 38,
    borderTopRightRadius: 32,
    borderBottomRightRadius: 42,
    borderBottomLeftRadius: 34,
  },
  scheduleBubble: {
    minHeight: 196,
    paddingHorizontal: 17,
    paddingVertical: 16,
    overflow: 'hidden',
    borderWidth: 1,
    borderColor: 'rgba(255,255,255,0.58)',
    backgroundColor: 'rgba(255,255,255,0.34)',
  },
  scheduleBubbleLabel: { fontSize: 11, fontWeight: '800', color: LABEL_COLOR },
  scheduleBubbleDate: {
    marginTop: 3,
    fontSize: 32,
    lineHeight: 37,
    letterSpacing: -1,
    fontWeight: '900',
    color: TEXT_COLOR,
  },
  scheduleBubbleWeekday: { fontSize: 12, fontWeight: '700', color: MUTED_TEXT_COLOR },
  scheduleBubbleDivider: {
    height: 1,
    marginVertical: 14,
    backgroundColor: 'rgba(27,72,54,0.1)',
  },
  scheduleBubbleFact: { flexDirection: 'row', alignItems: 'flex-start', gap: 6, marginBottom: 9 },
  scheduleBubbleFactText: { fontSize: 13, fontWeight: '800', color: TEXT_COLOR },
  scheduleBubblePlace: { flex: 1, fontSize: 13, lineHeight: 18, fontWeight: '800', color: TEXT_COLOR },
  scheduleEmpty: { flex: 1, minHeight: 150, alignItems: 'center', justifyContent: 'center' },
  scheduleEmptyTitle: { marginTop: 10, fontSize: 14, fontWeight: '900', color: TEXT_COLOR },
  scheduleEmptyText: { marginTop: 4, fontSize: 10, color: MUTED_TEXT_COLOR },
  scheduleEditButton: {
    height: 36,
    marginTop: 6,
    borderRadius: 18,
    flexDirection: 'row',
    alignItems: 'center',
    justifyContent: 'center',
    gap: 5,
    backgroundColor: 'rgba(255,255,255,0.34)',
    borderWidth: 1,
    borderColor: 'rgba(255,255,255,0.64)',
  },
  scheduleEditButtonText: { fontSize: 11, fontWeight: '900', color: DARK_GREEN_COLOR },
  chatCardFrame: {
    position: 'absolute',
    right: 16,
    bottom: 20,
    width: 188,
    height: 68,
    overflow: 'visible',
    shadowColor: '#16372B',
    shadowOffset: { width: 0, height: 7 },
    shadowOpacity: 0.04,
    shadowRadius: 10,
  },
  chatCardShape: {
    borderTopLeftRadius: 27,
    borderTopRightRadius: 32,
    borderBottomRightRadius: 26,
    borderBottomLeftRadius: 35,
  },
  chatCard: {
    width: '100%',
    height: '100%',
    paddingHorizontal: 13,
    overflow: 'hidden',
    flexDirection: 'row',
    alignItems: 'center',
    gap: 10,
    borderWidth: 1,
    borderColor: 'rgba(255,255,255,0.58)',
    backgroundColor: 'rgba(255,255,255,0.34)',
  },
  chatCardIcon: {
    width: 40,
    height: 40,
    borderRadius: 20,
    alignItems: 'center',
    justifyContent: 'center',
    backgroundColor: '#E2F6E9',
  },
  chatUnreadPin: {
    position: 'absolute',
    top: -5,
    right: -5,
    width: 15,
    height: 15,
    borderRadius: 8,
    borderWidth: 2,
    borderColor: SURFACE_COLOR,
    backgroundColor: '#E65353',
    zIndex: 2,
  },
  chatCardCopy: { flex: 1 },
  chatCardEyebrow: { fontSize: 10, color: MUTED_TEXT_COLOR },
  chatCardTitle: { marginTop: 2, fontSize: 14, fontWeight: '900', color: TEXT_COLOR },

  overviewNavigation: {
    minHeight: 68,
    paddingHorizontal: 8,
    borderRadius: 25,
    flexDirection: 'row',
    alignItems: 'center',
    backgroundColor: 'rgba(255,255,255,0.86)',
    borderWidth: 1,
    borderColor: 'rgba(39,111,80,0.1)',
  },
  overviewButton: { flex: 1, height: 62, gap: 5, alignItems: 'center', justifyContent: 'center' },
  overviewButtonText: { fontSize: 12, fontWeight: '800', color: TEXT_COLOR },
  overviewDivider: { width: 1, height: 25, backgroundColor: 'rgba(28,63,51,0.1)' },

  applicationCard: {
    padding: 20,
    gap: 10,
    borderRadius: 24,
    backgroundColor: 'rgba(255,255,255,0.84)',
    borderWidth: 1,
    borderColor: BORDER_COLOR,
  },
  applicationTitle: { fontSize: 17, fontWeight: '900', color: TEXT_COLOR },
  applicationText: { fontSize: 13, lineHeight: 20, color: MUTED_TEXT_COLOR },
  pendingBadge: {
    alignSelf: 'flex-start',
    paddingHorizontal: 10,
    paddingVertical: 5,
    borderRadius: 999,
    backgroundColor: SOFT_GREEN_COLOR,
  },
  pendingBadgeText: { fontSize: 11, fontWeight: '800', color: PRIMARY_COLOR },
  applyButton: {
    height: 50,
    borderRadius: 18,
    alignItems: 'center',
    justifyContent: 'center',
    backgroundColor: PRIMARY_COLOR,
  },
  applyButtonText: { fontSize: 14, fontWeight: '900', color: SURFACE_COLOR },

  footer: {
    position: 'absolute',
    left: 0,
    right: 0,
    bottom: 0,
    paddingTop: 12,
    paddingHorizontal: 18,
    backgroundColor: 'rgba(250,252,250,0.94)',
    borderTopWidth: StyleSheet.hairlineWidth,
    borderTopColor: 'rgba(28,63,51,0.1)',
  },
  slideAction: {
    height: 62,
    borderRadius: 31,
    overflow: 'hidden',
    justifyContent: 'center',
    borderWidth: 1,
    borderColor: 'rgba(255,255,255,0.82)',
  },
  slideDirectionHint: {
    position: 'absolute',
    right: 17,
    top: 0,
    bottom: 0,
    flexDirection: 'row',
    alignItems: 'center',
    gap: -4,
  },
  slideActionText: { textAlign: 'center', fontSize: 15, fontWeight: '900', color: TEXT_COLOR },
  slideHandle: {
    position: 'absolute',
    left: SLIDE_HORIZONTAL_INSET,
    width: SLIDE_HANDLE_SIZE,
    height: 46,
    borderRadius: 23,
    overflow: 'hidden',
    alignItems: 'center',
    justifyContent: 'center',
  },
  readOnlyNotice: { paddingVertical: 18, textAlign: 'center', color: MUTED_TEXT_COLOR },

  menuScrim: { flex: 1, justifyContent: 'flex-end', backgroundColor: MODAL_SCRIM_COLOR },
  menuSheet: {
    paddingHorizontal: 20,
    paddingTop: 10,
    paddingBottom: 26,
    borderTopLeftRadius: 30,
    borderTopRightRadius: 30,
    backgroundColor: SURFACE_COLOR,
  },
  menuHandle: {
    alignSelf: 'center',
    width: 40,
    height: 4,
    marginBottom: 18,
    borderRadius: 2,
    backgroundColor: '#D9E1DC',
  },
  menuTitle: { marginBottom: 14, fontSize: 18, fontWeight: '900', color: TEXT_COLOR },
  menuItemList: { gap: 10 },
  menuItem: {
    minHeight: 76,
    paddingHorizontal: 14,
    borderRadius: 20,
    flexDirection: 'row',
    alignItems: 'center',
    gap: 12,
    backgroundColor: SUBTLE_BACKGROUND_COLOR,
  },
  menuItemDisabled: { opacity: 0.45 },
  menuItemIcon: {
    width: 44,
    height: 44,
    borderRadius: 16,
    alignItems: 'center',
    justifyContent: 'center',
    backgroundColor: '#DFF4E7',
  },
  menuItemIconDanger: { backgroundColor: '#FDECEC' },
  menuItemCopy: { flex: 1 },
  menuItemTitle: { fontSize: 15, fontWeight: '900', color: TEXT_COLOR },
  menuItemTitleDanger: { fontSize: 15, fontWeight: '900', color: '#D94B4B' },
  menuItemDescription: { marginTop: 3, fontSize: 11, color: MUTED_TEXT_COLOR },
  menuCloseButton: {
    height: 50,
    marginTop: 20,
    borderRadius: 18,
    alignItems: 'center',
    justifyContent: 'center',
    backgroundColor: '#F2F5F3',
  },
  menuCloseButtonText: { fontSize: 14, fontWeight: '800', color: TEXT_COLOR },

  modalScrim: {
    flex: 1,
    padding: 20,
    justifyContent: 'center',
    backgroundColor: MODAL_SCRIM_COLOR,
  },
  modalCard: { padding: 22, borderRadius: 26, backgroundColor: SURFACE_COLOR },
  modalTitle: { marginBottom: 20, fontSize: 19, fontWeight: '900', color: TEXT_COLOR },
  modalLabelRow: { flexDirection: 'row', justifyContent: 'space-between', alignItems: 'center' },
  modalFieldLabel: { marginBottom: 8, fontSize: 13, fontWeight: '800', color: TEXT_COLOR },
  modalCounterText: { marginBottom: 8, fontSize: 11, color: MUTED_TEXT_COLOR },
  modalTextInput: {
    minHeight: 100,
    marginBottom: 18,
    padding: 14,
    borderRadius: 18,
    borderWidth: 1,
    borderColor: BORDER_COLOR,
    fontSize: 14,
    color: TEXT_COLOR,
    backgroundColor: SUBTLE_BACKGROUND_COLOR,
  },
  modalPurposeRow: { flexDirection: 'row', gap: 8 },
  modalPurposeButton: { flex: 1 },
  modalSubmitButton: { marginTop: 20 },
});
