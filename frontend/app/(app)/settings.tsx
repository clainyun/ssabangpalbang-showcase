import Ionicons from '@expo/vector-icons/Ionicons';
import { useMutation, useQuery } from '@tanstack/react-query';
import * as WebBrowser from 'expo-web-browser';
import { StatusBar } from 'expo-status-bar';
import { useRouter } from 'expo-router';
import { useEffect, useRef, useState } from 'react';
import {
  ActivityIndicator,
  AppState,
  KeyboardAvoidingView,
  Linking,
  Modal,
  Platform,
  Pressable,
  ScrollView,
  StyleSheet,
  Switch,
  Text,
  TextInput,
  View,
} from 'react-native';
import { useSafeAreaInsets } from 'react-native-safe-area-context';

import { appAlert } from '@/components/AppDialog';
import {
  BORDER_COLOR,
  DARK_GREEN_COLOR,
  ERROR_BACKGROUND_COLOR,
  ERROR_COLOR,
  LABEL_COLOR,
  MODAL_SCRIM_COLOR,
  MUTED_TEXT_COLOR,
  PLACEHOLDER_COLOR,
  PRIMARY_COLOR,
  SCREEN_BACKGROUND_COLOR,
  SOFT_BACKGROUND_COLOR,
  SOFT_GREEN_COLOR,
  SURFACE_COLOR,
  TEXT_COLOR,
} from '@/constants/colors';
import { PRIVACY_POLICY_URL, TERMS_OF_SERVICE_URL } from '@/constants/legal';
import { logoutSession } from '@/features/auth/logoutSession';
import {
  getNotificationSettings,
  NotificationSettingsApiError,
  type NotificationSettings,
  type NotificationSettingsUpdateRequest,
  updateNotificationSettings,
} from '@/features/member/api/notificationSettings';
import type { MyProfile } from '@/features/member/api/myPage';
import { MemberWithdrawalError, withdrawMember } from '@/features/member/api/withdrawal';
import {
  isNotificationPermissionGranted,
  requestNotificationPermission,
  suspendFcmTokenRegistration,
} from '@/features/notification/fcmToken';
import { AuthSessionChangedError, AuthSessionExpiredError } from '@/lib/authenticatedFetch';
import { queryClient } from '@/lib/queryClient';
import { useAuthStore } from '@/store/authStore';
import { useMemberStore } from '@/store/memberStore';

type ConfirmationSheet = 'logout' | 'withdrawal' | null;
type NotificationField = keyof NotificationSettings;
type NotificationNotice =
  | { tone: 'success'; message: string }
  | { tone: 'error'; message: string; retryRequest: NotificationSettingsUpdateRequest };

const DEVELOPMENT_NOTIFICATION_SETTINGS: NotificationSettings = {
  serviceNotificationAgreed: true,
  adNotificationAgreed: false,
};

function SectionTitle({ children }: { children: string }) {
  return <Text style={styles.sectionTitle}>{children}</Text>;
}

function NotificationRow({
  description,
  icon,
  isDisabled,
  isLoading,
  onPress,
  title,
  value,
}: {
  description: string;
  icon: 'notifications-outline' | 'gift-outline';
  isDisabled: boolean;
  isLoading: boolean;
  onPress: () => void;
  title: string;
  value: boolean;
}) {
  return (
    <Pressable
      accessibilityLabel={`${title} ${value ? '켜짐' : '꺼짐'}`}
      accessibilityRole="switch"
      accessibilityState={{ checked: value, disabled: isDisabled }}
      disabled={isDisabled}
      onPress={onPress}
      style={({ pressed }) => [
        styles.settingRow,
        isDisabled && !isLoading && styles.disabledSettingRow,
        pressed && !isDisabled && styles.pressed,
      ]}
    >
      <View style={styles.settingIcon}>
        <Ionicons color={DARK_GREEN_COLOR} name={icon} size={20} />
      </View>
      <View style={styles.settingDescription}>
        <Text style={styles.settingTitle}>{title}</Text>
        <Text style={styles.settingCaption}>{description}</Text>
      </View>
      {isLoading ? (
        <ActivityIndicator color={PRIMARY_COLOR} size="small" />
      ) : (
        <View pointerEvents="none">
          <Switch
            ios_backgroundColor={BORDER_COLOR}
            thumbColor={SURFACE_COLOR}
            trackColor={{ false: BORDER_COLOR, true: PRIMARY_COLOR }}
            value={value}
          />
        </View>
      )}
    </Pressable>
  );
}

function AccountRow({
  description,
  icon,
  onPress,
  title,
  tone = 'default',
}: {
  description: string;
  icon: 'document-text-outline' | 'log-out-outline' | 'person-remove-outline' | 'reader-outline';
  onPress: () => void;
  title: string;
  tone?: 'default' | 'danger';
}) {
  const isDanger = tone === 'danger';

  return (
    <Pressable
      accessibilityLabel={title}
      accessibilityRole="button"
      onPress={onPress}
      style={({ pressed }) => [styles.settingRow, pressed && styles.pressed]}
    >
      <View style={[styles.settingIcon, isDanger && styles.settingIconDanger]}>
        <Ionicons color={isDanger ? ERROR_COLOR : DARK_GREEN_COLOR} name={icon} size={20} />
      </View>
      <View style={styles.settingDescription}>
        <Text style={[styles.settingTitle, isDanger && styles.settingTitleDanger]}>{title}</Text>
        <Text style={styles.settingCaption}>{description}</Text>
      </View>
      <Ionicons
        color={isDanger ? ERROR_COLOR : MUTED_TEXT_COLOR}
        name="chevron-forward"
        size={19}
      />
    </Pressable>
  );
}

function SheetHeader({
  description,
  icon,
  title,
  tone,
}: {
  description: string;
  icon: 'log-out-outline' | 'warning-outline';
  title: string;
  tone: 'default' | 'danger';
}) {
  const isDanger = tone === 'danger';

  return (
    <>
      <View style={[styles.sheetIcon, isDanger && styles.sheetIconDanger]}>
        <Ionicons color={isDanger ? ERROR_COLOR : DARK_GREEN_COLOR} name={icon} size={25} />
      </View>
      <Text style={styles.sheetTitle}>{title}</Text>
      <Text style={styles.sheetDescription}>{description}</Text>
    </>
  );
}

export default function SettingsScreen() {
  const router = useRouter();
  const insets = useSafeAreaInsets();
  const sessionVersion = useAuthStore((state) => state.sessionVersion);
  const accessToken = useAuthStore((state) => state.accessToken);
  const [activeSheet, setActiveSheet] = useState<ConfirmationSheet>(null);
  const [withdrawalText, setWithdrawalText] = useState('');
  const [withdrawalError, setWithdrawalError] = useState<string | null>(null);
  const [isLoggingOut, setIsLoggingOut] = useState(false);
  const [isWithdrawing, setIsWithdrawing] = useState(false);
  const [pendingNotificationField, setPendingNotificationField] =
    useState<NotificationField | null>(null);
  const [notificationNotice, setNotificationNotice] = useState<NotificationNotice | null>(null);
  const [isDeviceNotificationPermissionGranted, setIsDeviceNotificationPermissionGranted] =
    useState<boolean | null>(null);
  const withdrawalRequestInFlight = useRef(false);
  const notificationRequestInFlight = useRef(false);
  const isTemporaryDevelopmentSession = __DEV__ && accessToken === 'dummy-access-token';
  const notificationSettingsQueryKey = [
    'member',
    'me',
    'notification-settings',
    sessionVersion,
  ] as const;
  const notificationSettingsQuery = useQuery({
    queryKey: notificationSettingsQueryKey,
    queryFn: ({ signal }) => getNotificationSettings(sessionVersion, signal),
    enabled: Boolean(accessToken) && !isTemporaryDevelopmentSession,
  });
  const notificationSettings = isTemporaryDevelopmentSession
    ? DEVELOPMENT_NOTIFICATION_SETTINGS
    : notificationSettingsQuery.data;
  const serviceNotificationAgreed = notificationSettings?.serviceNotificationAgreed ?? false;
  const adNotificationAgreed = notificationSettings?.adNotificationAgreed ?? false;
  const isNotificationQueryLoading =
    notificationSettingsQuery.isPending && !isTemporaryDevelopmentSession;
  const isWithdrawalConfirmationValid = withdrawalText.trim() === '회원탈퇴';
  const canSubmitWithdrawal = isWithdrawalConfirmationValid && !isWithdrawing;

  const openDeviceNotificationSettings = () => {
    void Linking.openSettings().catch(() => {
      appAlert('기기 설정 열기 실패', '기기 설정에서 싸방팔방 알림 권한을 확인해 주세요.');
    });
  };

  const openPrivacyPolicy = async () => {
    try {
      await WebBrowser.openBrowserAsync(PRIVACY_POLICY_URL);
    } catch {
      appAlert('안내', '개인정보 처리방침을 열지 못했어요. 잠시 후 다시 시도해 주세요.');
    }
  };

  const openTermsOfService = async () => {
    try {
      await WebBrowser.openBrowserAsync(TERMS_OF_SERVICE_URL);
    } catch {
      appAlert('안내', '이용약관을 열지 못했어요. 잠시 후 다시 시도해 주세요.');
    }
  };

  const refreshDeviceNotificationPermission = async (shouldRequest: boolean) => {
    try {
      const granted = shouldRequest
        ? await requestNotificationPermission()
        : await isNotificationPermissionGranted();
      setIsDeviceNotificationPermissionGranted(granted);

      if (!granted && shouldRequest) {
        appAlert(
          '기기 알림 권한이 꺼져 있어요',
          '서비스 알림 설정은 저장됐어요. 알림을 실제로 받으려면 기기 설정에서 권한을 허용해 주세요.',
          [
            { text: '나중에', style: 'cancel' },
            { text: '기기 설정 열기', onPress: openDeviceNotificationSettings },
          ],
        );
      }
    } catch {
      setIsDeviceNotificationPermissionGranted(null);
      if (shouldRequest) {
        appAlert(
          '기기 알림 권한을 확인해 주세요',
          '서비스 알림 설정은 저장됐어요. 기기 설정에서 싸방팔방 알림 권한을 확인해 주세요.',
          [
            { text: '확인', style: 'cancel' },
            { text: '기기 설정 열기', onPress: openDeviceNotificationSettings },
          ],
        );
      }
    }
  };

  useEffect(() => {
    let active = true;

    if (!notificationSettingsQuery.data?.serviceNotificationAgreed) {
      return () => {
        active = false;
      };
    }

    const refreshPermission = () => {
      void isNotificationPermissionGranted()
        .then((granted) => {
          if (active) setIsDeviceNotificationPermissionGranted(granted);
        })
        .catch(() => {
          if (active) setIsDeviceNotificationPermissionGranted(null);
        });
    };
    refreshPermission();
    const subscription = AppState.addEventListener('change', (state) => {
      if (state === 'active') refreshPermission();
    });

    return () => {
      active = false;
      subscription.remove();
    };
  }, [notificationSettingsQuery.data?.serviceNotificationAgreed]);

  const notificationSettingsMutation = useMutation({
    mutationFn: (request: NotificationSettingsUpdateRequest) =>
      updateNotificationSettings(request, sessionVersion),
    onMutate: async (request) => {
      await queryClient.cancelQueries({ queryKey: notificationSettingsQueryKey, exact: true });
      const previous = queryClient.getQueryData<NotificationSettings>(notificationSettingsQueryKey);

      if (previous) {
        queryClient.setQueryData<NotificationSettings>(notificationSettingsQueryKey, {
          ...previous,
          ...request,
        });
      }

      return { previous };
    },
    onSuccess: (updated, request) => {
      const nextSettings: NotificationSettings = {
        serviceNotificationAgreed: updated.serviceNotificationAgreed,
        adNotificationAgreed: updated.adNotificationAgreed,
      };
      queryClient.setQueryData(notificationSettingsQueryKey, nextSettings);
      queryClient.setQueryData<MyProfile>(['member', 'me', sessionVersion], (profile) =>
        profile
          ? {
              ...profile,
              ...nextSettings,
              updatedAt: updated.updatedAt,
            }
          : profile,
      );
      setNotificationNotice({
        tone: 'success',
        message:
          'serviceNotificationAgreed' in request
            ? `서비스 알림을 ${updated.serviceNotificationAgreed ? '켰어요.' : '껐어요.'}`
            : `혜택·광고성 알림을 ${updated.adNotificationAgreed ? '켰어요.' : '껐어요.'}`,
      });

      if (request.serviceNotificationAgreed === true) {
        void refreshDeviceNotificationPermission(true);
      }
    },
    onError: (error, request, context) => {
      if (context?.previous) {
        queryClient.setQueryData(notificationSettingsQueryKey, context.previous);
      }
      if (error instanceof AuthSessionExpiredError || error instanceof AuthSessionChangedError) {
        return;
      }

      setNotificationNotice({
        tone: 'error',
        message:
          error instanceof NotificationSettingsApiError
            ? error.message
            : '알림 설정을 저장하지 못했어요. 잠시 후 다시 시도해 주세요.',
        retryRequest: request,
      });
    },
    onSettled: () => {
      notificationRequestInFlight.current = false;
      setPendingNotificationField(null);
      void queryClient.invalidateQueries({
        queryKey: notificationSettingsQueryKey,
        exact: true,
      });
    },
  });

  const submitNotificationUpdate = (
    field: NotificationField,
    request: NotificationSettingsUpdateRequest,
  ) => {
    if (
      notificationRequestInFlight.current ||
      notificationSettingsMutation.isPending ||
      !notificationSettings
    ) {
      return;
    }

    if (isTemporaryDevelopmentSession) {
      appAlert('알림 설정', '실제 로그인 계정에서 알림 설정을 변경할 수 있어요.');
      return;
    }

    notificationRequestInFlight.current = true;
    setPendingNotificationField(field);
    setNotificationNotice(null);
    notificationSettingsMutation.mutate(request);
  };

  const toggleNotificationSetting = (field: NotificationField) => {
    if (!notificationSettings) return;

    const nextValue = !notificationSettings[field];
    const request: NotificationSettingsUpdateRequest = {
      [field]: nextValue,
    } as NotificationSettingsUpdateRequest;

    if (field === 'adNotificationAgreed' && nextValue) {
      appAlert(
        '혜택·광고성 알림을 받을까요?',
        '이벤트, 프로모션과 서비스 혜택 정보를 알림으로 보내드려요.',
        [
          { text: '취소', style: 'cancel' },
          {
            text: '동의하고 켜기',
            onPress: () => submitNotificationUpdate(field, request),
          },
        ],
      );
      return;
    }

    submitNotificationUpdate(field, request);
  };

  const closeSheet = () => {
    if (isLoggingOut || isWithdrawing || withdrawalRequestInFlight.current) return;
    setActiveSheet(null);
    setWithdrawalText('');
    setWithdrawalError(null);
  };

  const confirmLogout = async () => {
    if (isLoggingOut) return;

    setIsLoggingOut(true);
    await logoutSession();
  };

  const clearLocalMemberCache = () => {
    queryClient.clear();
    useMemberStore.setState({
      selectedCharacterId: undefined,
      ageGroupPublicAgreed: undefined,
    });
  };

  const clearLocalMemberSession = async (): Promise<boolean> => {
    suspendFcmTokenRegistration(sessionVersion);
    const signedOut = await useAuthStore.getState().signOut(sessionVersion);

    if (signedOut) clearLocalMemberCache();

    return signedOut;
  };

  const confirmWithdrawal = async () => {
    if (!canSubmitWithdrawal || isWithdrawing || withdrawalRequestInFlight.current) return;

    if (isTemporaryDevelopmentSession) {
      setWithdrawalError('개발용 임시 계정에서는 회원 탈퇴를 진행할 수 없어요.');
      return;
    }

    withdrawalRequestInFlight.current = true;
    setIsWithdrawing(true);
    setWithdrawalError(null);

    try {
      await withdrawMember(withdrawalText.trim());
      const signedOut = await clearLocalMemberSession();
      if (signedOut) {
        appAlert('회원 탈퇴 완료', '회원 탈퇴가 완료되었습니다.');
      }
    } catch (error) {
      if (error instanceof AuthSessionExpiredError) {
        clearLocalMemberCache();
        return;
      }

      if (
        error instanceof MemberWithdrawalError &&
        (error.code === 'MEMBER_ALREADY_WITHDRAWN' ||
          error.code === 'MEMBER_NOT_FOUND' ||
          error.code.startsWith('AUTH_REFRESH_TOKEN_'))
      ) {
        const signedOut = await clearLocalMemberSession();
        if (signedOut) {
          appAlert('로그인 정보 확인', `${error.message}\n다시 로그인해 주세요.`);
        }
        return;
      }

      setWithdrawalError(
        error instanceof Error
          ? error.message
          : '회원 탈퇴를 완료하지 못했어요. 잠시 후 다시 시도해 주세요.',
      );
    } finally {
      withdrawalRequestInFlight.current = false;
      setIsWithdrawing(false);
    }
  };

  return (
    <View style={styles.screen}>
      <StatusBar style="dark" />
      <View style={[styles.header, { paddingTop: insets.top + 10 }]}>
        <Pressable
          accessibilityLabel="마이페이지로 돌아가기"
          accessibilityRole="button"
          onPress={() => router.back()}
          style={({ pressed }) => [styles.headerButton, pressed && styles.pressed]}
        >
          <Ionicons color={TEXT_COLOR} name="chevron-back" size={25} />
        </Pressable>
        <Text style={styles.headerTitle}>설정</Text>
        <View style={styles.headerButton} />
      </View>

      <ScrollView
        contentContainerStyle={[
          styles.content,
          { paddingBottom: Math.max(insets.bottom, 20) + 28 },
        ]}
        showsVerticalScrollIndicator={false}
      >
        <View style={styles.intro}>
          <View style={styles.introIcon}>
            <Ionicons color={PRIMARY_COLOR} name="options-outline" size={22} />
          </View>
          <View style={styles.introTextArea}>
            <Text style={styles.introTitle}>필요한 설정만 간단하게</Text>
            <Text style={styles.introDescription}>알림과 계정 상태를 여기서 관리할 수 있어요.</Text>
          </View>
        </View>

        <SectionTitle>알림</SectionTitle>
        <View style={styles.card}>
          <NotificationRow
            description="스터디 일정과 활동 소식을 받아요."
            icon="notifications-outline"
            isDisabled={
              isNotificationQueryLoading ||
              notificationSettingsMutation.isPending ||
              !notificationSettings
            }
            isLoading={
              isNotificationQueryLoading || pendingNotificationField === 'serviceNotificationAgreed'
            }
            onPress={() => toggleNotificationSetting('serviceNotificationAgreed')}
            title="서비스 알림"
            value={serviceNotificationAgreed}
          />
          <View style={styles.divider} />
          <NotificationRow
            description="이벤트와 혜택 소식을 받아요."
            icon="gift-outline"
            isDisabled={
              isNotificationQueryLoading ||
              notificationSettingsMutation.isPending ||
              !notificationSettings
            }
            isLoading={
              isNotificationQueryLoading || pendingNotificationField === 'adNotificationAgreed'
            }
            onPress={() => toggleNotificationSetting('adNotificationAgreed')}
            title="혜택·광고성 알림"
            value={adNotificationAgreed}
          />
        </View>

        {notificationSettingsQuery.isError &&
        !notificationSettingsQuery.data &&
        !isTemporaryDevelopmentSession ? (
          <Pressable
            accessibilityRole="button"
            onPress={() => void notificationSettingsQuery.refetch()}
            style={({ pressed }) => [styles.inlineNotice, pressed && styles.pressed]}
          >
            <Ionicons color={ERROR_COLOR} name="alert-circle-outline" size={17} />
            <Text style={styles.inlineNoticeText}>알림 상태를 불러오지 못했어요.</Text>
            <Text style={styles.inlineNoticeAction}>다시 시도</Text>
          </Pressable>
        ) : null}

        {notificationNotice?.tone === 'success' ? (
          <View
            accessibilityLiveRegion="polite"
            style={[styles.inlineNotice, styles.successNotice]}
          >
            <Ionicons color={DARK_GREEN_COLOR} name="checkmark-circle-outline" size={17} />
            <Text style={[styles.inlineNoticeText, styles.successNoticeText]}>
              {notificationNotice.message}
            </Text>
          </View>
        ) : notificationNotice?.tone === 'error' ? (
          <Pressable
            accessibilityRole="button"
            onPress={() =>
              submitNotificationUpdate(
                'serviceNotificationAgreed' in notificationNotice.retryRequest
                  ? 'serviceNotificationAgreed'
                  : 'adNotificationAgreed',
                notificationNotice.retryRequest,
              )
            }
            style={({ pressed }) => [styles.inlineNotice, pressed && styles.pressed]}
          >
            <Ionicons color={ERROR_COLOR} name="alert-circle-outline" size={17} />
            <Text style={styles.inlineNoticeText}>{notificationNotice.message}</Text>
            <Text style={styles.inlineNoticeAction}>다시 시도</Text>
          </Pressable>
        ) : null}

        {serviceNotificationAgreed && isDeviceNotificationPermissionGranted === false ? (
          <Pressable
            accessibilityRole="button"
            onPress={openDeviceNotificationSettings}
            style={({ pressed }) => [styles.permissionNotice, pressed && styles.pressed]}
          >
            <View style={styles.permissionNoticeIcon}>
              <Ionicons color={DARK_GREEN_COLOR} name="notifications-off-outline" size={18} />
            </View>
            <View style={styles.permissionNoticeContent}>
              <Text style={styles.permissionNoticeTitle}>기기 알림 권한이 꺼져 있어요</Text>
              <Text style={styles.permissionNoticeText}>
                권한을 허용해야 알림을 받을 수 있어요.
              </Text>
            </View>
            <Text style={styles.permissionNoticeAction}>기기 설정</Text>
          </Pressable>
        ) : null}

        <SectionTitle>약관 및 정책</SectionTitle>
        <View style={styles.card}>
          <AccountRow
            description="서비스 이용 규칙을 확인해요."
            icon="reader-outline"
            onPress={() => void openTermsOfService()}
            title="서비스 이용약관"
          />
          <View style={styles.divider} />
          <AccountRow
            description="개인정보 수집·이용 내역을 확인해요."
            icon="document-text-outline"
            onPress={() => void openPrivacyPolicy()}
            title="개인정보 처리방침"
          />
        </View>

        <SectionTitle>계정</SectionTitle>
        <View style={styles.card}>
          <AccountRow
            description="현재 기기에서 안전하게 로그아웃해요."
            icon="log-out-outline"
            onPress={() => setActiveSheet('logout')}
            title="로그아웃"
          />
          <View style={styles.divider} />
          <AccountRow
            description="계정을 탈퇴 상태로 전환해요."
            icon="person-remove-outline"
            onPress={() => setActiveSheet('withdrawal')}
            title="회원 탈퇴"
            tone="danger"
          />
        </View>
      </ScrollView>

      <Modal
        animationType="fade"
        onRequestClose={closeSheet}
        statusBarTranslucent
        transparent
        visible={activeSheet !== null}
      >
        <KeyboardAvoidingView
          behavior={Platform.OS === 'ios' ? 'padding' : 'height'}
          style={styles.modalRoot}
        >
          <Pressable
            accessibilityLabel="확인창 닫기"
            disabled={isLoggingOut || isWithdrawing}
            onPress={closeSheet}
            style={styles.scrim}
          />
          <View style={[styles.sheet, { paddingBottom: Math.max(insets.bottom, 18) + 8 }]}>
            <View style={styles.sheetHandle} />

            {activeSheet === 'logout' ? (
              <>
                <SheetHeader
                  description="저장된 로그인 정보가 이 기기에서 삭제됩니다."
                  icon="log-out-outline"
                  title="로그아웃할까요?"
                  tone="default"
                />
                <View style={styles.sheetActions}>
                  <Pressable
                    accessibilityRole="button"
                    disabled={isLoggingOut}
                    onPress={closeSheet}
                    style={({ pressed }) => [styles.secondaryButton, pressed && styles.pressed]}
                  >
                    <Text style={styles.secondaryButtonText}>취소</Text>
                  </Pressable>
                  <Pressable
                    accessibilityRole="button"
                    disabled={isLoggingOut}
                    onPress={() => void confirmLogout()}
                    style={({ pressed }) => [styles.primaryButton, pressed && styles.pressed]}
                  >
                    {isLoggingOut ? (
                      <ActivityIndicator color={SURFACE_COLOR} size="small" />
                    ) : (
                      <Text style={styles.primaryButtonText}>로그아웃</Text>
                    )}
                  </Pressable>
                </View>
              </>
            ) : activeSheet === 'withdrawal' ? (
              <>
                <ScrollView
                  contentContainerStyle={styles.sheetScrollContent}
                  keyboardShouldPersistTaps="handled"
                  showsVerticalScrollIndicator={false}
                  style={styles.sheetScroll}
                >
                  <SheetHeader
                    description="탈퇴하기 전에 아래 내용을 꼭 확인해 주세요."
                    icon="warning-outline"
                    title="정말 탈퇴하시겠어요?"
                    tone="danger"
                  />

                  <View style={styles.warningBox}>
                    <View style={styles.warningRow}>
                      <Ionicons color={ERROR_COLOR} name="remove-circle-outline" size={17} />
                      <Text style={styles.warningText}>탈퇴한 계정은 다시 사용할 수 없어요.</Text>
                    </View>
                    <View style={styles.warningRow}>
                      <Ionicons color={ERROR_COLOR} name="remove-circle-outline" size={17} />
                      <Text style={styles.warningText}>
                        작성한 기록은 관계 유지를 위해 보존되고 작성자는 비식별화돼요.
                      </Text>
                    </View>
                    <View style={styles.warningRow}>
                      <Ionicons color={ERROR_COLOR} name="remove-circle-outline" size={17} />
                      <Text style={styles.warningText}>
                        운영 중인 스터디나 임장이 있으면 탈퇴가 제한될 수 있어요.
                      </Text>
                    </View>
                  </View>

                  <Text style={styles.withdrawalLabel}>
                    계속하려면 <Text style={styles.withdrawalKeyword}>회원탈퇴</Text>를 입력해
                    주세요.
                  </Text>
                  <TextInput
                    accessibilityLabel="회원 탈퇴 확인 문구"
                    autoCapitalize="none"
                    autoCorrect={false}
                    editable={!isWithdrawing}
                    onChangeText={(text) => {
                      setWithdrawalText(text);
                      setWithdrawalError(null);
                    }}
                    placeholder="회원탈퇴"
                    placeholderTextColor={PLACEHOLDER_COLOR}
                    returnKeyType="done"
                    style={[styles.withdrawalInput, withdrawalError && styles.withdrawalInputError]}
                    value={withdrawalText}
                  />
                  {withdrawalError ? (
                    <View accessibilityLiveRegion="polite" style={styles.withdrawalErrorBox}>
                      <Ionicons color={ERROR_COLOR} name="alert-circle-outline" size={17} />
                      <Text style={styles.withdrawalErrorText}>{withdrawalError}</Text>
                    </View>
                  ) : null}
                </ScrollView>

                <View style={styles.sheetActions}>
                  <Pressable
                    accessibilityRole="button"
                    disabled={isWithdrawing}
                    onPress={closeSheet}
                    style={({ pressed }) => [
                      styles.secondaryButton,
                      isWithdrawing && styles.disabledButton,
                      pressed && !isWithdrawing && styles.pressed,
                    ]}
                  >
                    <Text
                      style={[
                        styles.secondaryButtonText,
                        isWithdrawing && styles.disabledButtonText,
                      ]}
                    >
                      취소
                    </Text>
                  </Pressable>
                  <Pressable
                    accessibilityRole="button"
                    disabled={!canSubmitWithdrawal}
                    onPress={() => void confirmWithdrawal()}
                    style={({ pressed }) => [
                      styles.dangerButton,
                      !isWithdrawalConfirmationValid && styles.disabledButton,
                      pressed && canSubmitWithdrawal && styles.pressed,
                    ]}
                  >
                    {isWithdrawing ? (
                      <ActivityIndicator color={SURFACE_COLOR} size="small" />
                    ) : (
                      <Text
                        style={[
                          styles.dangerButtonText,
                          !isWithdrawalConfirmationValid && styles.disabledButtonText,
                        ]}
                      >
                        회원 탈퇴
                      </Text>
                    )}
                  </Pressable>
                </View>
              </>
            ) : null}
          </View>
        </KeyboardAvoidingView>
      </Modal>
    </View>
  );
}

const styles = StyleSheet.create({
  screen: {
    flex: 1,
    backgroundColor: SCREEN_BACKGROUND_COLOR,
  },
  header: {
    minHeight: 66,
    paddingHorizontal: 12,
    backgroundColor: SCREEN_BACKGROUND_COLOR,
    flexDirection: 'row',
    alignItems: 'center',
    justifyContent: 'space-between',
  },
  headerButton: {
    width: 44,
    height: 44,
    borderRadius: 22,
    alignItems: 'center',
    justifyContent: 'center',
  },
  headerTitle: {
    color: TEXT_COLOR,
    fontSize: 19,
    fontWeight: '900',
    letterSpacing: -0.5,
  },
  content: {
    paddingHorizontal: 20,
    paddingTop: 12,
  },
  intro: {
    minHeight: 82,
    paddingHorizontal: 18,
    borderWidth: 1,
    borderColor: BORDER_COLOR,
    borderRadius: 22,
    backgroundColor: SURFACE_COLOR,
    flexDirection: 'row',
    alignItems: 'center',
  },
  introIcon: {
    width: 42,
    height: 42,
    marginRight: 13,
    borderRadius: 21,
    backgroundColor: SOFT_GREEN_COLOR,
    alignItems: 'center',
    justifyContent: 'center',
  },
  introTextArea: {
    minWidth: 0,
    flex: 1,
  },
  introTitle: {
    color: TEXT_COLOR,
    fontSize: 15,
    fontWeight: '900',
  },
  introDescription: {
    marginTop: 5,
    color: MUTED_TEXT_COLOR,
    fontSize: 12,
    fontWeight: '600',
    lineHeight: 17,
  },
  sectionTitle: {
    marginTop: 26,
    marginBottom: 10,
    marginLeft: 4,
    color: LABEL_COLOR,
    fontSize: 14,
    fontWeight: '900',
  },
  card: {
    borderWidth: 1,
    borderColor: BORDER_COLOR,
    borderRadius: 22,
    backgroundColor: SURFACE_COLOR,
    overflow: 'hidden',
  },
  settingRow: {
    minHeight: 82,
    paddingHorizontal: 16,
    paddingVertical: 13,
    flexDirection: 'row',
    alignItems: 'center',
  },
  disabledSettingRow: {
    opacity: 0.62,
  },
  settingIcon: {
    width: 42,
    height: 42,
    marginRight: 13,
    borderRadius: 15,
    backgroundColor: SOFT_GREEN_COLOR,
    alignItems: 'center',
    justifyContent: 'center',
  },
  settingIconDanger: {
    backgroundColor: ERROR_BACKGROUND_COLOR,
  },
  settingDescription: {
    minWidth: 0,
    flex: 1,
    paddingRight: 12,
  },
  settingTitle: {
    color: TEXT_COLOR,
    fontSize: 15,
    fontWeight: '900',
  },
  settingTitleDanger: {
    color: ERROR_COLOR,
  },
  settingCaption: {
    marginTop: 4,
    color: MUTED_TEXT_COLOR,
    fontSize: 12,
    fontWeight: '600',
    lineHeight: 17,
  },
  divider: {
    height: 1,
    marginLeft: 71,
    backgroundColor: BORDER_COLOR,
  },
  inlineNotice: {
    minHeight: 42,
    marginTop: 9,
    paddingHorizontal: 13,
    borderRadius: 14,
    backgroundColor: ERROR_BACKGROUND_COLOR,
    flexDirection: 'row',
    alignItems: 'center',
  },
  inlineNoticeText: {
    marginLeft: 7,
    color: ERROR_COLOR,
    fontSize: 12,
    fontWeight: '700',
  },
  inlineNoticeAction: {
    marginLeft: 'auto',
    color: ERROR_COLOR,
    fontSize: 12,
    fontWeight: '900',
  },
  successNotice: {
    backgroundColor: SOFT_GREEN_COLOR,
  },
  successNoticeText: {
    color: DARK_GREEN_COLOR,
  },
  permissionNotice: {
    minHeight: 66,
    marginTop: 9,
    paddingHorizontal: 13,
    paddingVertical: 11,
    borderWidth: 1,
    borderColor: BORDER_COLOR,
    borderRadius: 16,
    backgroundColor: SOFT_BACKGROUND_COLOR,
    flexDirection: 'row',
    alignItems: 'center',
  },
  permissionNoticeIcon: {
    width: 34,
    height: 34,
    marginRight: 10,
    borderRadius: 12,
    backgroundColor: SOFT_GREEN_COLOR,
    alignItems: 'center',
    justifyContent: 'center',
  },
  permissionNoticeContent: {
    minWidth: 0,
    flex: 1,
  },
  permissionNoticeTitle: {
    color: TEXT_COLOR,
    fontSize: 12,
    fontWeight: '900',
  },
  permissionNoticeText: {
    marginTop: 3,
    color: MUTED_TEXT_COLOR,
    fontSize: 11,
    fontWeight: '600',
  },
  permissionNoticeAction: {
    marginLeft: 8,
    color: PRIMARY_COLOR,
    fontSize: 12,
    fontWeight: '900',
  },
  modalRoot: {
    flex: 1,
    justifyContent: 'flex-end',
  },
  scrim: {
    position: 'absolute',
    top: 0,
    right: 0,
    bottom: 0,
    left: 0,
    backgroundColor: MODAL_SCRIM_COLOR,
  },
  sheet: {
    maxHeight: '94%',
    paddingHorizontal: 22,
    paddingTop: 10,
    borderTopLeftRadius: 30,
    borderTopRightRadius: 30,
    backgroundColor: SURFACE_COLOR,
    shadowColor: DARK_GREEN_COLOR,
    shadowOffset: { width: 0, height: -8 },
    shadowOpacity: 0.12,
    shadowRadius: 24,
    elevation: 14,
  },
  sheetHandle: {
    width: 42,
    height: 5,
    marginBottom: 19,
    borderRadius: 3,
    backgroundColor: BORDER_COLOR,
    alignSelf: 'center',
  },
  sheetScroll: {
    flexShrink: 1,
  },
  sheetScrollContent: {
    paddingBottom: 2,
  },
  sheetIcon: {
    width: 48,
    height: 48,
    marginBottom: 14,
    borderRadius: 24,
    backgroundColor: SOFT_GREEN_COLOR,
    alignItems: 'center',
    justifyContent: 'center',
  },
  sheetIconDanger: {
    backgroundColor: ERROR_BACKGROUND_COLOR,
  },
  sheetTitle: {
    color: TEXT_COLOR,
    fontSize: 22,
    fontWeight: '900',
    letterSpacing: -0.6,
  },
  sheetDescription: {
    marginTop: 8,
    color: MUTED_TEXT_COLOR,
    fontSize: 14,
    fontWeight: '600',
    lineHeight: 20,
  },
  warningBox: {
    marginTop: 18,
    padding: 15,
    borderRadius: 17,
    backgroundColor: ERROR_BACKGROUND_COLOR,
    gap: 10,
  },
  warningRow: {
    flexDirection: 'row',
    alignItems: 'flex-start',
    gap: 8,
  },
  warningText: {
    minWidth: 0,
    flex: 1,
    color: LABEL_COLOR,
    fontSize: 12,
    fontWeight: '700',
    lineHeight: 18,
  },
  withdrawalLabel: {
    marginTop: 18,
    color: LABEL_COLOR,
    fontSize: 13,
    fontWeight: '700',
    lineHeight: 19,
  },
  withdrawalKeyword: {
    color: ERROR_COLOR,
    fontWeight: '900',
  },
  withdrawalInput: {
    height: 52,
    marginTop: 10,
    paddingHorizontal: 15,
    borderWidth: 1,
    borderColor: BORDER_COLOR,
    borderRadius: 15,
    backgroundColor: SOFT_BACKGROUND_COLOR,
    color: TEXT_COLOR,
    fontSize: 15,
    fontWeight: '700',
  },
  withdrawalInputError: {
    borderColor: ERROR_COLOR,
  },
  withdrawalErrorBox: {
    minHeight: 44,
    marginTop: 9,
    paddingHorizontal: 13,
    paddingVertical: 11,
    borderRadius: 14,
    backgroundColor: ERROR_BACKGROUND_COLOR,
    flexDirection: 'row',
    alignItems: 'flex-start',
    gap: 7,
  },
  withdrawalErrorText: {
    minWidth: 0,
    flex: 1,
    color: ERROR_COLOR,
    fontSize: 12,
    fontWeight: '700',
    lineHeight: 18,
  },
  sheetActions: {
    marginTop: 24,
    flexDirection: 'row',
    gap: 10,
  },
  secondaryButton: {
    height: 52,
    borderRadius: 17,
    backgroundColor: SOFT_BACKGROUND_COLOR,
    flex: 1,
    alignItems: 'center',
    justifyContent: 'center',
  },
  secondaryButtonText: {
    color: LABEL_COLOR,
    fontSize: 15,
    fontWeight: '900',
  },
  primaryButton: {
    height: 52,
    borderRadius: 17,
    backgroundColor: DARK_GREEN_COLOR,
    flex: 1,
    alignItems: 'center',
    justifyContent: 'center',
  },
  primaryButtonText: {
    color: SURFACE_COLOR,
    fontSize: 15,
    fontWeight: '900',
  },
  dangerButton: {
    height: 52,
    borderRadius: 17,
    backgroundColor: ERROR_COLOR,
    flex: 1,
    alignItems: 'center',
    justifyContent: 'center',
  },
  dangerButtonText: {
    color: SURFACE_COLOR,
    fontSize: 15,
    fontWeight: '900',
  },
  disabledButton: {
    backgroundColor: SOFT_BACKGROUND_COLOR,
  },
  disabledButtonText: {
    color: PLACEHOLDER_COLOR,
  },
  pressed: {
    opacity: 0.66,
  },
});
