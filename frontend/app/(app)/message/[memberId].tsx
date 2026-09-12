import Ionicons from '@expo/vector-icons/Ionicons';
import { useMutation, useQueryClient } from '@tanstack/react-query';
import * as Crypto from 'expo-crypto';
import { Image, type ImageProps } from 'expo-image';
import { useLocalSearchParams, useRouter } from 'expo-router';
import { StatusBar } from 'expo-status-bar';
import { useRef, useState } from 'react';
import {
  ActivityIndicator,
  type ImageSourcePropType,
  Keyboard,
  KeyboardAvoidingView,
  Platform,
  Pressable,
  ScrollView,
  StyleSheet,
  Text,
  TextInput,
  View,
} from 'react-native';
import { useSafeAreaInsets } from 'react-native-safe-area-context';

import {
  BORDER_COLOR,
  BUTTON_BACKGROUND_COLOR,
  DARK_GREEN_COLOR,
  ERROR_BACKGROUND_COLOR,
  ERROR_COLOR,
  MUTED_TEXT_COLOR,
  PLACEHOLDER_COLOR,
  PRIMARY_COLOR,
  SCREEN_BACKGROUND_COLOR,
  SOFT_GREEN_COLOR,
  SURFACE_COLOR,
  TEXT_COLOR,
} from '@/constants/colors';
import {
  MEMBER_MESSAGE_MAX_LENGTH,
  MemberMessageApiError,
  sendMemberMessage,
  type MemberMessageSendResponse,
} from '@/features/member/api/message';
import type { CharacterId } from '@/features/member/api/myPage';

const CHARACTER_IMAGES: Record<CharacterId, ImageSourcePropType> = {
  PALBANG: require('../../../assets/images/characters/palbang.png'),
  PALBANG_DOG: require('../../../assets/images/characters/palbang_dog.png'),
  PALBANG_RABBIT: require('../../../assets/images/characters/palbang_rabbit.png'),
};

type PendingMessage = {
  content: string;
  clientMessageId: string;
};

type MessageRouteParams = {
  memberId?: string | string[];
  nickname?: string | string[];
  profileImageUrl?: string | string[];
  selectedCharacterId?: string | string[];
};

function firstParam(value: string | string[] | undefined): string | undefined {
  return Array.isArray(value) ? value[0] : value;
}

function isCharacterId(value: string | undefined): value is CharacterId {
  return value === 'PALBANG' || value === 'PALBANG_DOG' || value === 'PALBANG_RABBIT';
}

export default function MemberMessageScreen() {
  const router = useRouter();
  const queryClient = useQueryClient();
  const insets = useSafeAreaInsets();
  const params = useLocalSearchParams<MessageRouteParams>();
  const memberId = Number(firstParam(params.memberId));
  const isValidMemberId = Number.isSafeInteger(memberId) && memberId > 0;
  const nickname = firstParam(params.nickname)?.trim() || '사용자';
  const profileImageUrl = firstParam(params.profileImageUrl)?.trim() || null;
  const characterParam = firstParam(params.selectedCharacterId);
  const selectedCharacterId: CharacterId = isCharacterId(characterParam)
    ? characterParam
    : 'PALBANG';
  // 원격 프로필 이미지는 Presigned 서명 쿼리가 요청마다 바뀌므로, 쿼리를 뗀 경로를
  // cacheKey로 고정해 서명이 바뀌어도 캐시 적중되게 한다(커뮤니티 목록과 동일 패턴).
  const avatarSource: ImageProps['source'] = profileImageUrl
    ? { uri: profileImageUrl, cacheKey: profileImageUrl.split('?')[0] }
    : CHARACTER_IMAGES[selectedCharacterId];
  const pendingMessageRef = useRef<PendingMessage | null>(null);
  const [content, setContent] = useState('');
  const [errorMessage, setErrorMessage] = useState<string | null>(
    isValidMemberId ? null : '쪽지를 받을 사용자 정보가 올바르지 않아요.',
  );
  const [sendUnavailable, setSendUnavailable] = useState(!isValidMemberId);
  const [sentMessage, setSentMessage] = useState<MemberMessageSendResponse | null>(null);

  const sendMutation = useMutation({
    mutationFn: (message: PendingMessage) => sendMemberMessage(memberId, message),
    onSuccess: (result) => {
      Keyboard.dismiss();
      pendingMessageRef.current = null;
      setSentMessage(result.data);
      void Promise.all([
        queryClient.invalidateQueries({ queryKey: ['member', 'me'] }),
        queryClient.invalidateQueries({ queryKey: ['member', 'public-profile'] }),
      ]);
    },
    onError: (error) => {
      if (error instanceof MemberMessageApiError) {
        setErrorMessage(error.message);
        if (
          error.code === 'MEMBER_MESSAGE_FOLLOW_REQUIRED' ||
          error.code === 'MEMBER_MESSAGE_SELF_NOT_ALLOWED' ||
          error.code === 'MEMBER_NOT_FOUND'
        ) {
          setSendUnavailable(true);
          void Promise.all([
            queryClient.invalidateQueries({ queryKey: ['member', 'me'] }),
            queryClient.invalidateQueries({ queryKey: ['member', 'public-profile'] }),
          ]);
        }
        return;
      }
      setErrorMessage('쪽지를 보내지 못했어요. 잠시 후 다시 시도해 주세요.');
    },
  });

  const goBack = () => {
    if (router.canGoBack()) {
      router.back();
      return;
    }
    router.replace('/(app)/(tabs)/my');
  };

  const openRecipientProfile = () => {
    if (!isValidMemberId) return;
    Keyboard.dismiss();
    router.push({
      pathname: '/(app)/member/[memberId]',
      params: { memberId: memberId.toString() },
    });
  };

  const handleContentChange = (nextContent: string) => {
    setContent(nextContent);
    setErrorMessage(null);
    if (pendingMessageRef.current?.content !== nextContent.trim()) {
      pendingMessageRef.current = null;
    }
  };

  const handleSend = () => {
    const trimmedContent = content.trim();

    if (!isValidMemberId) {
      setErrorMessage('쪽지를 받을 사용자 정보가 올바르지 않아요.');
      return;
    }
    if (!trimmedContent) {
      setErrorMessage('보낼 내용을 입력해 주세요.');
      return;
    }
    if (trimmedContent.length > MEMBER_MESSAGE_MAX_LENGTH) {
      setErrorMessage(`쪽지는 ${MEMBER_MESSAGE_MAX_LENGTH}자까지 입력할 수 있어요.`);
      return;
    }

    setErrorMessage(null);
    const pendingMessage =
      pendingMessageRef.current?.content === trimmedContent
        ? pendingMessageRef.current
        : { content: trimmedContent, clientMessageId: Crypto.randomUUID() };
    pendingMessageRef.current = pendingMessage;
    sendMutation.mutate(pendingMessage);
  };

  if (sentMessage) {
    return (
      <View style={[styles.screen, { paddingTop: insets.top }]}>
        <StatusBar style="dark" />
        <View style={styles.successHeader}>
          <Pressable
            accessibilityLabel="쪽지 화면 닫기"
            accessibilityRole="button"
            onPress={goBack}
            hitSlop={10}
            style={({ pressed }) => [styles.headerButton, pressed && styles.pressed]}
          >
            <Ionicons color={DARK_GREEN_COLOR} name="close" size={25} />
          </Pressable>
        </View>
        <View style={styles.successContent}>
          <View style={styles.successIcon}>
            <Ionicons color={PRIMARY_COLOR} name="checkmark" size={34} />
          </View>
          <Text style={styles.successTitle}>쪽지를 보냈어요</Text>
          <Text style={styles.successDescription}>
            {sentMessage.recipientNickname}님에게{`\n`}알림으로 안전하게 전달했어요.
          </Text>
          <View style={styles.successNote}>
            <Ionicons color={DARK_GREEN_COLOR} name="notifications-outline" size={19} />
            <Text style={styles.successNoteText}>
              쪽지는 한 번만 전달되며 대화방이나 메시지 기록은 만들어지지 않아요.
            </Text>
          </View>
        </View>
        <View style={[styles.bottomBar, { paddingBottom: Math.max(insets.bottom, 16) }]}>
          <Pressable
            accessibilityRole="button"
            onPress={goBack}
            style={({ pressed }) => [styles.primaryButton, pressed && styles.primaryButtonPressed]}
          >
            <Text style={styles.primaryButtonText}>프로필로 돌아가기</Text>
          </Pressable>
        </View>
      </View>
    );
  }

  const trimmedLength = content.trim().length;
  const canSend = trimmedLength > 0 && !sendUnavailable && !sendMutation.isPending;

  return (
    <KeyboardAvoidingView
      behavior={Platform.OS === 'ios' ? 'padding' : 'height'}
      keyboardVerticalOffset={0}
      style={styles.screen}
    >
      <StatusBar style="dark" />
      <View style={[styles.header, { paddingTop: insets.top }]}>
        <Pressable
          accessibilityLabel="뒤로 가기"
          accessibilityRole="button"
          onPress={goBack}
          hitSlop={10}
          style={({ pressed }) => [styles.headerButton, pressed && styles.pressed]}
        >
          <Ionicons color={DARK_GREEN_COLOR} name="chevron-back" size={26} />
        </Pressable>
        <Text style={styles.headerTitle}>쪽지 보내기</Text>
        <View style={styles.headerButtonPlaceholder} />
      </View>

      <ScrollView
        contentContainerStyle={styles.content}
        keyboardShouldPersistTaps="handled"
        showsVerticalScrollIndicator={false}
      >
        <Pressable
          accessibilityLabel={`${nickname}님 공개 프로필 열기`}
          accessibilityRole="button"
          disabled={!isValidMemberId}
          onPress={openRecipientProfile}
          style={({ pressed }) => [styles.recipientCard, pressed && styles.recipientCardPressed]}
        >
          <View style={styles.avatarFrame}>
            <Image contentFit="contain" source={avatarSource} style={styles.avatar} />
          </View>
          <View style={styles.recipientTextArea}>
            <Text style={styles.eyebrow}>받는 사람</Text>
            <Text numberOfLines={1} style={styles.nickname}>
              {nickname}
            </Text>
          </View>
          <View style={styles.recipientTrailing}>
            <View style={styles.followingChip}>
              <Ionicons color={PRIMARY_COLOR} name="checkmark" size={14} />
              <Text style={styles.followingChipText}>팔로잉</Text>
            </View>
            <Ionicons color={MUTED_TEXT_COLOR} name="chevron-forward" size={19} />
          </View>
        </Pressable>

        <View style={styles.guideCard}>
          <View style={styles.guideIcon}>
            <Ionicons color={DARK_GREEN_COLOR} name="mail-unread-outline" size={21} />
          </View>
          <View style={styles.guideTextArea}>
            <Text style={styles.guideTitle}>가볍게 한 번 전하는 쪽지예요</Text>
            <Text style={styles.guideDescription}>
              상대방에게 알림으로 전달되며, 대화방이나 쪽지함은 만들어지지 않아요.
            </Text>
          </View>
        </View>

        <View style={styles.composerSection}>
          <Text style={styles.inputLabel}>보낼 내용</Text>
          <View style={[styles.inputCard, errorMessage && styles.inputCardError]}>
            <TextInput
              accessibilityLabel={`${nickname}님에게 보낼 쪽지 내용`}
              editable={!sendMutation.isPending && !sendUnavailable}
              maxLength={MEMBER_MESSAGE_MAX_LENGTH}
              multiline
              onChangeText={handleContentChange}
              placeholder="함께 나누고 싶은 이야기를 적어 보세요."
              placeholderTextColor={PLACEHOLDER_COLOR}
              selectionColor={PRIMARY_COLOR}
              style={styles.textInput}
              textAlignVertical="top"
              value={content}
            />
            <Text
              accessibilityLabel={`${content.length}자 입력됨, 최대 ${MEMBER_MESSAGE_MAX_LENGTH}자`}
              style={styles.characterCount}
            >
              {content.length}/{MEMBER_MESSAGE_MAX_LENGTH}
            </Text>
          </View>
          {errorMessage ? (
            <View accessibilityLiveRegion="polite" style={styles.errorBox}>
              <Ionicons color={ERROR_COLOR} name="alert-circle-outline" size={18} />
              <Text style={styles.errorText}>{errorMessage}</Text>
            </View>
          ) : (
            <Text style={styles.inputHint}>개인정보나 민감한 내용은 보내지 않는 것이 좋아요.</Text>
          )}
        </View>
      </ScrollView>

      <View style={[styles.bottomBar, { paddingBottom: Math.max(insets.bottom, 16) }]}>
        <Pressable
          accessibilityLabel={sendMutation.isPending ? '쪽지 보내는 중' : '쪽지 보내기'}
          accessibilityRole="button"
          accessibilityState={{ disabled: !canSend }}
          disabled={!canSend}
          onPress={handleSend}
          style={({ pressed }) => [
            styles.primaryButton,
            !canSend && styles.primaryButtonDisabled,
            pressed && canSend && styles.primaryButtonPressed,
          ]}
        >
          {sendMutation.isPending ? (
            <ActivityIndicator color={SURFACE_COLOR} size="small" />
          ) : (
            <>
              <Ionicons color={SURFACE_COLOR} name="paper-plane-outline" size={19} />
              <Text style={styles.primaryButtonText}>쪽지 보내기</Text>
            </>
          )}
        </Pressable>
      </View>
    </KeyboardAvoidingView>
  );
}

const styles = StyleSheet.create({
  screen: {
    flex: 1,
    backgroundColor: SCREEN_BACKGROUND_COLOR,
  },
  header: {
    minHeight: 58,
    paddingHorizontal: 14,
    flexDirection: 'row',
    alignItems: 'flex-end',
    justifyContent: 'space-between',
    paddingBottom: 10,
    backgroundColor: SURFACE_COLOR,
    borderBottomWidth: 1,
    borderBottomColor: BORDER_COLOR,
  },
  successHeader: {
    height: 58,
    paddingHorizontal: 14,
    flexDirection: 'row',
    alignItems: 'center',
    justifyContent: 'flex-end',
  },
  headerButton: {
    width: 40,
    height: 40,
    borderRadius: 20,
    alignItems: 'center',
    justifyContent: 'center',
  },
  headerButtonPlaceholder: {
    width: 40,
    height: 40,
  },
  headerTitle: {
    paddingBottom: 9,
    color: DARK_GREEN_COLOR,
    fontSize: 18,
    fontWeight: '800',
  },
  pressed: {
    opacity: 0.68,
  },
  content: {
    paddingHorizontal: 20,
    paddingTop: 22,
    paddingBottom: 32,
    gap: 16,
  },
  recipientCard: {
    flexDirection: 'row',
    alignItems: 'center',
    minHeight: 92,
    paddingHorizontal: 18,
    paddingVertical: 15,
    borderWidth: 1,
    borderColor: BORDER_COLOR,
    borderRadius: 24,
    backgroundColor: SURFACE_COLOR,
  },
  recipientCardPressed: {
    opacity: 0.72,
  },
  avatarFrame: {
    width: 58,
    height: 58,
    borderRadius: 29,
    alignItems: 'center',
    justifyContent: 'center',
    overflow: 'hidden',
    backgroundColor: SOFT_GREEN_COLOR,
  },
  avatar: {
    width: '100%',
    height: '100%',
  },
  recipientTextArea: {
    flex: 1,
    minWidth: 0,
    marginLeft: 14,
  },
  recipientTrailing: {
    flexDirection: 'row',
    alignItems: 'center',
    gap: 6,
  },
  eyebrow: {
    color: MUTED_TEXT_COLOR,
    fontSize: 12,
    fontWeight: '700',
    marginBottom: 4,
  },
  nickname: {
    color: DARK_GREEN_COLOR,
    fontSize: 19,
    fontWeight: '800',
  },
  followingChip: {
    flexDirection: 'row',
    alignItems: 'center',
    gap: 3,
    paddingHorizontal: 10,
    height: 31,
    borderRadius: 16,
    backgroundColor: SOFT_GREEN_COLOR,
  },
  followingChipText: {
    color: PRIMARY_COLOR,
    fontSize: 12,
    fontWeight: '800',
  },
  guideCard: {
    flexDirection: 'row',
    padding: 16,
    borderRadius: 20,
    backgroundColor: BUTTON_BACKGROUND_COLOR,
  },
  guideIcon: {
    width: 38,
    height: 38,
    borderRadius: 19,
    alignItems: 'center',
    justifyContent: 'center',
    marginRight: 12,
    backgroundColor: SURFACE_COLOR,
  },
  guideTextArea: {
    flex: 1,
    paddingTop: 1,
  },
  guideTitle: {
    color: DARK_GREEN_COLOR,
    fontSize: 14,
    fontWeight: '800',
    marginBottom: 5,
  },
  guideDescription: {
    color: TEXT_COLOR,
    fontSize: 13,
    lineHeight: 19,
  },
  composerSection: {
    marginTop: 3,
  },
  inputLabel: {
    color: TEXT_COLOR,
    fontSize: 15,
    fontWeight: '800',
    marginBottom: 10,
    marginLeft: 2,
  },
  inputCard: {
    minHeight: 210,
    borderWidth: 1.5,
    borderColor: BORDER_COLOR,
    borderRadius: 22,
    paddingHorizontal: 16,
    paddingTop: 15,
    paddingBottom: 13,
    backgroundColor: SURFACE_COLOR,
  },
  inputCardError: {
    borderColor: ERROR_COLOR,
  },
  textInput: {
    minHeight: 150,
    color: TEXT_COLOR,
    fontSize: 16,
    lineHeight: 24,
    padding: 0,
  },
  characterCount: {
    alignSelf: 'flex-end',
    color: MUTED_TEXT_COLOR,
    fontSize: 12,
    fontWeight: '700',
    marginTop: 10,
  },
  inputHint: {
    color: MUTED_TEXT_COLOR,
    fontSize: 12,
    lineHeight: 18,
    marginTop: 9,
    marginHorizontal: 3,
  },
  errorBox: {
    flexDirection: 'row',
    alignItems: 'flex-start',
    gap: 7,
    marginTop: 10,
    padding: 12,
    borderRadius: 14,
    backgroundColor: ERROR_BACKGROUND_COLOR,
  },
  errorText: {
    flex: 1,
    color: ERROR_COLOR,
    fontSize: 13,
    lineHeight: 18,
    fontWeight: '600',
  },
  bottomBar: {
    paddingTop: 12,
    paddingHorizontal: 20,
    borderTopWidth: 1,
    borderTopColor: BORDER_COLOR,
    backgroundColor: SURFACE_COLOR,
  },
  primaryButton: {
    minHeight: 54,
    borderRadius: 18,
    flexDirection: 'row',
    alignItems: 'center',
    justifyContent: 'center',
    gap: 8,
    backgroundColor: PRIMARY_COLOR,
  },
  primaryButtonDisabled: {
    backgroundColor: PLACEHOLDER_COLOR,
  },
  primaryButtonPressed: {
    opacity: 0.82,
  },
  primaryButtonText: {
    color: SURFACE_COLOR,
    fontSize: 16,
    fontWeight: '800',
  },
  successContent: {
    flex: 1,
    alignItems: 'center',
    justifyContent: 'center',
    paddingHorizontal: 28,
    paddingBottom: 50,
  },
  successIcon: {
    width: 76,
    height: 76,
    borderRadius: 38,
    alignItems: 'center',
    justifyContent: 'center',
    marginBottom: 22,
    backgroundColor: BUTTON_BACKGROUND_COLOR,
  },
  successTitle: {
    color: DARK_GREEN_COLOR,
    fontSize: 25,
    fontWeight: '900',
    marginBottom: 12,
  },
  successDescription: {
    color: TEXT_COLOR,
    fontSize: 16,
    lineHeight: 24,
    textAlign: 'center',
  },
  successNote: {
    width: '100%',
    flexDirection: 'row',
    alignItems: 'flex-start',
    gap: 10,
    marginTop: 30,
    padding: 16,
    borderRadius: 18,
    backgroundColor: SOFT_GREEN_COLOR,
  },
  successNoteText: {
    flex: 1,
    color: DARK_GREEN_COLOR,
    fontSize: 13,
    lineHeight: 19,
  },
});
