import Ionicons from '@expo/vector-icons/Ionicons';
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query';
import { router, useLocalSearchParams } from 'expo-router';
import { ActivityIndicator, Pressable, StyleSheet, Switch, Text, View } from 'react-native';
import { useSafeAreaInsets } from 'react-native-safe-area-context';

import { appAlert } from '@/components/AppDialog';
import { ScreenGlowBackground } from '@/components/ScreenGlowBackground';
import {
  BORDER_COLOR,
  DARK_GREEN_COLOR,
  ERROR_COLOR,
  MUTED_TEXT_COLOR,
  PRIMARY_COLOR,
  SURFACE_COLOR,
  TEXT_COLOR,
} from '@/constants/colors';
import {
  getChatNotificationSetting,
  type ChatNotificationSetting,
  updateChatNotificationSetting,
} from '@/features/chat/api/chatNotificationSettings';
import { ChatApiError } from '@/features/chat/types';
import { useAuthStore } from '@/store/authStore';

function goBackOrStudy(studyId: number, isValidStudyId: boolean) {
  if (router.canGoBack()) router.back();
  else if (isValidStudyId) {
    router.replace({ pathname: '/(app)/study/[id]', params: { id: String(studyId) } });
  } else {
    router.replace('/(app)/(tabs)/home');
  }
}

export default function StudySettingsScreen() {
  const params = useLocalSearchParams<{ id: string }>();
  const studyId = Number(params.id);
  const isValidStudyId = Number.isSafeInteger(studyId) && studyId > 0;
  const insets = useSafeAreaInsets();
  const queryClient = useQueryClient();
  const sessionVersion = useAuthStore((state) => state.sessionVersion);
  const queryKey = ['study', studyId, 'chat-notification-settings', sessionVersion] as const;

  const settingQuery = useQuery({
    queryKey,
    queryFn: () => getChatNotificationSetting(studyId),
    enabled: isValidStudyId,
  });
  const settingMutation = useMutation({
    mutationFn: (pushEnabled: boolean) => updateChatNotificationSetting(studyId, pushEnabled),
    onMutate: async (pushEnabled) => {
      await queryClient.cancelQueries({ queryKey, exact: true });
      const previous = queryClient.getQueryData<ChatNotificationSetting>(queryKey);
      if (previous) queryClient.setQueryData(queryKey, { ...previous, pushEnabled });
      return { previous };
    },
    onError: (error, _pushEnabled, context) => {
      if (context?.previous) queryClient.setQueryData(queryKey, context.previous);
      appAlert(
        '설정을 저장하지 못했어요',
        error instanceof ChatApiError ? error.message : '잠시 후 다시 시도해 주세요.',
      );
    },
    onSuccess: (updated) => queryClient.setQueryData(queryKey, updated),
    onSettled: () => void queryClient.invalidateQueries({ queryKey, exact: true }),
  });

  const setting = settingQuery.data;
  const isBusy = settingQuery.isPending || settingMutation.isPending;

  return (
    <View style={styles.screen}>
      <ScreenGlowBackground />
      <View style={[styles.header, { paddingTop: insets.top + 8 }]}>
        <Pressable
          accessibilityLabel="스터디로 돌아가기"
          hitSlop={12}
          onPress={() => goBackOrStudy(studyId, isValidStudyId)}
          style={({ pressed }) => [styles.headerButton, pressed && styles.pressed]}
        >
          <Ionicons color={DARK_GREEN_COLOR} name="chevron-back" size={24} />
        </Pressable>
        <Text style={styles.headerTitle}>스터디 설정</Text>
        <View style={styles.headerButton} />
      </View>

      <View style={styles.content}>
        <View style={styles.introIcon}>
          <Ionicons color={PRIMARY_COLOR} name="chatbubbles-outline" size={25} />
        </View>
        <Text style={styles.title}>대화방 알림</Text>
        <Text style={styles.description}>이 스터디 대화방의 새 메시지 푸시만 따로 설정할 수 있어요.</Text>

        {!isValidStudyId ? (
          <View style={styles.errorCard}>
            <Ionicons color={ERROR_COLOR} name="alert-circle-outline" size={20} />
            <Text style={styles.errorText}>올바르지 않은 스터디 주소예요.</Text>
          </View>
        ) : settingQuery.isError && !setting ? (
          <Pressable
            accessibilityRole="button"
            onPress={() => void settingQuery.refetch()}
            style={({ pressed }) => [styles.errorCard, pressed && styles.pressed]}
          >
            <Ionicons color={ERROR_COLOR} name="alert-circle-outline" size={20} />
            <Text style={styles.errorText}>설정을 불러오지 못했어요.</Text>
            <Text style={styles.retryText}>다시 시도</Text>
          </Pressable>
        ) : (
          <Pressable
            accessibilityLabel={`대화방 푸시 알림 ${setting?.pushEnabled ? '켜짐' : '꺼짐'}`}
            accessibilityRole="switch"
            accessibilityState={{ checked: setting?.pushEnabled ?? false, disabled: isBusy }}
            disabled={!setting || isBusy}
            onPress={() => setting && settingMutation.mutate(!setting.pushEnabled)}
            style={({ pressed }) => [
              styles.settingCard,
              (!setting || isBusy) && styles.disabled,
              pressed && !isBusy && styles.pressed,
            ]}
          >
            <View style={styles.settingIcon}>
              <Ionicons color={DARK_GREEN_COLOR} name="notifications-outline" size={21} />
            </View>
            <View style={styles.settingCopy}>
              <Text style={styles.settingTitle}>푸시 알림</Text>
              <Text style={styles.settingCaption}>
                {setting?.pushEnabled ? '새 메시지를 푸시로 알려드려요.' : '이 방의 푸시를 받지 않아요.'}
              </Text>
            </View>
            {isBusy ? (
              <ActivityIndicator color={PRIMARY_COLOR} size="small" />
            ) : (
              <View
                accessible={false}
                importantForAccessibility="no-hide-descendants"
                pointerEvents="none"
              >
                <Switch
                  accessible={false}
                  ios_backgroundColor={BORDER_COLOR}
                  thumbColor={SURFACE_COLOR}
                  trackColor={{ false: BORDER_COLOR, true: PRIMARY_COLOR }}
                  value={setting?.pushEnabled ?? false}
                />
              </View>
            )}
          </Pressable>
        )}

        <View style={styles.note}>
          <Ionicons color={MUTED_TEXT_COLOR} name="information-circle-outline" size={17} />
          <Text style={styles.noteText}>
            앱 전체 서비스 알림이나 기기 알림 권한이 꺼져 있으면 푸시를 받을 수 없어요.
          </Text>
        </View>
      </View>
    </View>
  );
}

const styles = StyleSheet.create({
  screen: { flex: 1, backgroundColor: '#F6FAF7' },
  header: {
    minHeight: 64,
    paddingHorizontal: 16,
    paddingBottom: 12,
    flexDirection: 'row',
    alignItems: 'center',
    justifyContent: 'space-between',
  },
  headerButton: { width: 42, height: 42, alignItems: 'center', justifyContent: 'center' },
  headerTitle: { fontSize: 18, fontWeight: '900', color: TEXT_COLOR },
  content: { paddingHorizontal: 20, paddingTop: 30 },
  introIcon: {
    width: 52,
    height: 52,
    borderRadius: 18,
    alignItems: 'center',
    justifyContent: 'center',
    backgroundColor: '#E2F6E9',
  },
  title: { marginTop: 18, fontSize: 24, fontWeight: '900', color: TEXT_COLOR },
  description: { marginTop: 8, fontSize: 14, lineHeight: 21, color: MUTED_TEXT_COLOR },
  settingCard: {
    marginTop: 28,
    minHeight: 86,
    paddingHorizontal: 16,
    borderRadius: 24,
    borderWidth: 1,
    borderColor: BORDER_COLOR,
    backgroundColor: SURFACE_COLOR,
    flexDirection: 'row',
    alignItems: 'center',
    gap: 12,
  },
  settingIcon: {
    width: 42,
    height: 42,
    borderRadius: 15,
    alignItems: 'center',
    justifyContent: 'center',
    backgroundColor: '#EDF7F0',
  },
  settingCopy: { flex: 1 },
  settingTitle: { fontSize: 15, fontWeight: '800', color: TEXT_COLOR },
  settingCaption: { marginTop: 4, fontSize: 12, color: MUTED_TEXT_COLOR },
  note: { marginTop: 16, paddingHorizontal: 4, flexDirection: 'row', alignItems: 'flex-start', gap: 7 },
  noteText: { flex: 1, fontSize: 12, lineHeight: 18, color: MUTED_TEXT_COLOR },
  errorCard: {
    marginTop: 28,
    minHeight: 72,
    paddingHorizontal: 16,
    borderRadius: 20,
    borderWidth: 1,
    borderColor: '#F2CACA',
    backgroundColor: '#FFF7F7',
    flexDirection: 'row',
    alignItems: 'center',
    gap: 9,
  },
  errorText: { flex: 1, fontSize: 13, color: TEXT_COLOR },
  retryText: { fontSize: 12, fontWeight: '800', color: ERROR_COLOR },
  pressed: { opacity: 0.72 },
  disabled: { opacity: 0.62 },
});
