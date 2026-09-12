import { useEffect, useMemo, useState } from 'react';
import { router, useLocalSearchParams } from 'expo-router';
import { File, Paths } from 'expo-file-system';
import { Image as ExpoImage } from 'expo-image';
import * as ImagePicker from 'expo-image-picker';
import * as ImageManipulator from 'expo-image-manipulator';
import * as MediaLibrary from 'expo-media-library';
import { LinearGradient } from 'expo-linear-gradient';
import {
  ActivityIndicator,
  FlatList,
  Image,
  Keyboard,
  Modal,
  Pressable,
  StyleSheet,
  Text,
  TextInput,
  View,
} from 'react-native';
import Animated, {
  KeyboardState,
  useAnimatedKeyboard,
  useAnimatedStyle,
} from 'react-native-reanimated';
import { useSafeAreaInsets } from 'react-native-safe-area-context';
import Svg, { Circle, Path } from 'react-native-svg';

import { appAlert } from '@/components/AppDialog';
import { explainBeforeRequest, showPermanentlyDeniedAlert } from '@/lib/permissions';
import { characterAvatarSource } from '@/features/character/characterAvatar';
import { ChatMemberSidebar } from '@/features/chat/ChatMemberSidebar';
import { useChatRoom } from '@/features/chat/useChatRoom';
import type { ChatMessage } from '@/features/chat/types';
import {
  DARK_GREEN_COLOR,
  MUTED_TEXT_COLOR,
  PLACEHOLDER_COLOR,
  PRIMARY_COLOR,
  TEXT_COLOR,
} from '@/constants/colors';
import { uploadMedia } from '@/features/media/api/mediaUpload';
import { GlassSurface } from '@/components/GlassSurface';
import { useAuthStore } from '@/store/authStore';

// 헤더와 메시지 영역 그라데이션 상단 색을 통일해서 경계가 안 보이게 이어지게 합니다.

// 말풍선도 살짝 그라데이션을 줘서 배경과 어우러지도록 합니다.
const BUBBLE_OTHER_GRADIENT = ['rgba(247, 248, 249, 0.85)', 'rgba(228, 231, 234, 0.85)'] as const;
const BUBBLE_MINE_GRADIENT = ['rgba(233, 251, 243, 0.85)', 'rgba(206, 240, 224, 0.85)'] as const;

// 딥링크 등으로 뒤로 갈 스택 자체가 없을 때 router.back()이 조용히 실패하는 경우가
// 있어서, 그럴 땐 홈 탭으로 대신 보냅니다.
function goBackOrHome() {
  if (router.canGoBack()) {
    router.back();
  } else {
    router.replace('/(app)/(tabs)/home');
  }
}

function formatDateLabel(iso: string): string {
  const date = new Date(iso);
  return `${date.getFullYear()}년 ${date.getMonth() + 1}월 ${date.getDate()}일`;
}

function formatTime(iso: string): string {
  const date = new Date(iso);
  const hours = date.getHours();
  const period = hours < 12 ? '오전' : '오후';
  const displayHour = hours % 12 === 0 ? 12 : hours % 12;
  const minutes = date.getMinutes().toString().padStart(2, '0');
  return `${period} ${displayHour}:${minutes}`;
}

type RenderItem =
  | { key: string; kind: 'date'; label: string }
  | { key: string; kind: 'message'; message: ChatMessage };

/** messages는 서버 응답과 동일한 최신순(DESC)입니다. 시간순으로 훑으며 날짜 구분선을
 * 끼워 넣은 뒤 다시 뒤집어서, <FlatList inverted /> 가 기대하는 최신순으로 되돌립니다. */
function buildRenderItems(messages: ChatMessage[]): RenderItem[] {
  const chronological = [...messages].reverse();
  const items: RenderItem[] = [];
  let lastDateKey: string | null = null;

  for (const message of chronological) {
    const key = message.createdAt.slice(0, 10);
    if (key !== lastDateKey) {
      items.push({ key: `date-${key}`, kind: 'date', label: formatDateLabel(message.createdAt) });
      lastDateKey = key;
    }
    items.push({ key: `message-${message.messageId}`, kind: 'message', message });
  }

  return items.reverse();
}

function BackIcon() {
  return (
    <Svg width={24} height={24} viewBox="0 0 24 24" fill="none" stroke={TEXT_COLOR} strokeWidth={2}>
      <Path d="M15 5l-7 7 7 7" strokeLinecap="round" strokeLinejoin="round" />
    </Svg>
  );
}

function MembersIcon() {
  return (
    <Svg width={22} height={22} viewBox="0 0 24 24" fill="none" stroke={TEXT_COLOR} strokeWidth={1.8}>
      <Circle cx={4} cy={6} r={1.3} fill={TEXT_COLOR} stroke="none" />
      <Path d="M9 6h11" strokeLinecap="round" />
      <Circle cx={4} cy={12} r={1.3} fill={TEXT_COLOR} stroke="none" />
      <Path d="M9 12h11" strokeLinecap="round" />
      <Circle cx={4} cy={18} r={1.3} fill={TEXT_COLOR} stroke="none" />
      <Path d="M9 18h11" strokeLinecap="round" />
    </Svg>
  );
}

function DownloadIcon() {
  return (
    <Svg width={18} height={18} viewBox="0 0 24 24" fill="none" stroke="#FFFFFF" strokeWidth={2}>
      <Path d="M12 4v11m0 0l-4-4m4 4l4-4M5 19h14" strokeLinecap="round" strokeLinejoin="round" />
    </Svg>
  );
}

function PlusIcon() {
  return (
    <Svg width={22} height={22} viewBox="0 0 24 24" fill="none" stroke={PLACEHOLDER_COLOR} strokeWidth={2}>
      <Path d="M12 5v14M5 12h14" strokeLinecap="round" />
    </Svg>
  );
}

function SendIcon() {
  return (
    <Svg width={18} height={18} viewBox="0 0 24 24" fill="none" stroke={PLACEHOLDER_COLOR} strokeWidth={2}>
      <Path d="M5 12h14M13 6l6 6-6 6" strokeLinecap="round" strokeLinejoin="round" />
    </Svg>
  );
}

/** 채팅 화면. roomId를 studyId로 씁니다 — 스터디 채팅 외 다른 채팅 종류는 MVP에 없습니다.
 * studyName·participantCount는 스터디 상세 화면이 아직 없어서 라우트 파라미터로 받고,
 * 없으면 기본 문구로 대체합니다. */
export default function ChatRoomScreen() {
  const params = useLocalSearchParams<{
    roomId: string;
    studyName?: string;
    participantCount?: string;
  }>();
  const studyId = Number(params.roomId);
  const insets = useSafeAreaInsets();

  // edge-to-edge + New Arch에선 adjustResize/KeyboardAvoidingView가 창을 안 줄여 키보드가
  // 입력창을 덮는다. reanimated useAnimatedKeyboard로 화면 전체를 키보드 높이만큼 위로 밀어
  // 입력창이 키보드 위에 붙게 한다. (PostDetailRedesign.tsx와 동일 패턴)
  const keyboard = useAnimatedKeyboard();
  // 키보드 높이는 '입력창이 실제로 포커스일 때'만 반영한다. 사진 첨부(handleAttachImage)가
  // launchImageLibraryAsync로 별도 네이티브 액티비티로 전환되면 안드로이드에서 close inset
  // 애니메이션 콜백이 유실돼 keyboard.state/height가 OPEN·CLOSING에 stuck될 수 있어 상태
  // 게이팅만으로는 못 막는다. 포커스는 JS에서 우리가 직접 제어하므로 게이트가 내려가 있으면
  // stuck된 값이 화면을 밀지 못한다. (post/create.tsx와 동일 대응 — 상태 게이팅은 유지)
  const [isInputFocused, setIsInputFocused] = useState(false);
  const keyboardPushStyle = useAnimatedStyle(() => {
    const state = keyboard.state.value;
    const active =
      isInputFocused &&
      (state === KeyboardState.OPENING ||
        state === KeyboardState.OPEN ||
        state === KeyboardState.CLOSING);
    return { paddingBottom: active ? keyboard.height.value : 0 };
  });
  const inputBarPadStyle = useAnimatedStyle(() => {
    const state = keyboard.state.value;
    const active =
      isInputFocused &&
      (state === KeyboardState.OPENING ||
        state === KeyboardState.OPEN ||
        state === KeyboardState.CLOSING);
    return { paddingBottom: active ? 12 : Math.max(12, insets.bottom) };
  });

  const [inputText, setInputText] = useState('');
  const [isMemberSidebarOpen, setIsMemberSidebarOpen] = useState(false);

  const {
    messages,
    currentMemberId,
    isLoadingInitial,
    isLoadingMore,
    hasMoreHistory,
    loadMoreHistory,
    connectionState,
    isConnected,
    isStudyCompleted,
    fatalError,
    sendText,
    sendImage,
    deleteMessage,
    editMessage,
  } = useChatRoom(studyId);

  const accessToken = useAuthStore((s) => s.accessToken);
  const sessionVersion = useAuthStore((s) => s.sessionVersion);
  // 첨부한 사진: 선택 즉시 미리보기(uploading)로 올려두고, 업로드 끝나면 ready.
  // 실제 전송은 사용자가 보내기 버튼을 눌러야 일어난다.
  const [pendingImage, setPendingImage] = useState<{
    uri: string;
    status: 'uploading' | 'ready';
    fileId?: number;
  } | null>(null);
  // 채팅 사진을 탭하면 전체화면으로 크게 보는 뷰어.
  const [viewerImageUrl, setViewerImageUrl] = useState<string | null>(null);
  const [isSavingPhoto, setIsSavingPhoto] = useState(false);
  /** 수정 모드. 입력바가 이 메시지의 본문 편집기로 바뀐다(디스코드 방식). */
  const [editingMessage, setEditingMessage] = useState<{
    messageId: number;
    originalContent: string;
  } | null>(null);

  const startEditing = (message: ChatMessage) => {
    setEditingMessage({
      messageId: message.messageId,
      originalContent: message.content ?? '',
    });
    setInputText(message.content ?? '');
  };

  const cancelEditing = () => {
    setEditingMessage(null);
    setInputText('');
  };

  const confirmDelete = (message: ChatMessage) => {
    appAlert(
      '메시지를 삭제할까요?',
      message.messageType === 'IMAGE'
        ? '사진이 모두에게서 지워지고 되돌릴 수 없어요.'
        : '메시지가 모두에게서 지워지고 되돌릴 수 없어요.',
      [
        { text: '취소', style: 'cancel' },
        {
          text: '삭제하기',
          style: 'destructive',
          onPress: () => {
            // 지우려는 메시지를 편집 중이었다면 편집 상태도 함께 정리한다.
            setEditingMessage((current) =>
              current?.messageId === message.messageId ? null : current,
            );
            void deleteMessage(message.messageId).catch((error) => {
              appAlert(
                '삭제하지 못했어요',
                error instanceof Error ? error.message : '잠시 후 다시 시도해주세요.',
              );
            });
          },
        },
      ],
    );
  };

  /** 내 메시지 길게 누르기 → TEXT는 수정/삭제, IMAGE는 삭제만. 완료 스터디는 진입 자체가 막혀 있다. */
  const handleLongPressMessage = (message: ChatMessage) => {
    if (message.deleted) return;
    if (message.sender?.memberId !== currentMemberId) return;

    if (message.messageType !== 'TEXT') {
      confirmDelete(message);
      return;
    }

    appAlert('메시지 관리', undefined, [
      { text: '취소', style: 'cancel' },
      { text: '수정하기', onPress: () => startEditing(message) },
      { text: '삭제하기', style: 'destructive', onPress: () => confirmDelete(message) },
    ]);
  };

  /**
   * 뷰어의 사진을 갤러리에 저장한다. presigned URL이라 먼저 캐시에 내려받은 뒤
   * 갤러리에 넣고 임시 파일은 지운다(임장 요약 화면의 저장 흐름과 같은 권한 UX).
   */
  const handleSavePhoto = (imageUrl: string) => {
    void (async () => {
      if (isSavingPhoto) return;
      setIsSavingPhoto(true);
      try {
        // 쓰기 전용 권한만 요청한다 — 갤러리를 읽을 일이 없다.
        let permission = await MediaLibrary.getPermissionsAsync(true, ['photo']);
        if (!permission.granted) {
          const shouldRequest = await explainBeforeRequest('photo');
          if (!shouldRequest) return;

          permission = await MediaLibrary.requestPermissionsAsync(true, ['photo']);
          if (!permission.granted) {
            if (!permission.canAskAgain) showPermanentlyDeniedAlert('photo');
            return;
          }
        }

        // presigned URL의 서명 쿼리가 파일명에 섞이지 않게 고정 이름으로 받는다.
        const destination = new File(Paths.cache, `chat-photo-${Date.now()}.jpg`);
        if (destination.exists) destination.delete();
        const downloaded = await File.downloadFileAsync(imageUrl, destination);
        try {
          await MediaLibrary.Asset.create(downloaded.uri);
        } finally {
          // 갤러리에 복사됐으니 캐시 임시본은 지운다.
          try {
            downloaded.delete();
          } catch {
            // 임시 파일 정리는 실패해도 무방하다.
          }
        }
        appAlert('사진을 저장했어요', '갤러리에서 확인할 수 있어요.');
      } catch {
        appAlert('저장하지 못했어요', '잠시 후 다시 시도해주세요.');
      } finally {
        setIsSavingPhoto(false);
      }
    })();
  };

  useEffect(() => {
    if (!fatalError) return;

    const message =
      fatalError.code === 'CHAT_FORBIDDEN'
        ? '해당 스터디의 멤버만 이용할 수 있습니다.'
        : fatalError.code === 'STUDY_NOT_FOUND'
          ? '존재하지 않는 스터디입니다.'
          : fatalError.message;

    appAlert('채팅방 오류', message, [{ text: '확인', onPress: goBackOrHome }]);
  }, [fatalError]);

  const renderItems = useMemo(() => buildRenderItems(messages), [messages]);

  const hasOutgoingPayload =
    inputText.trim().length > 0 || pendingImage?.status === 'ready';
  // 수정 확정은 REST 호출이라 STOMP 연결 여부와 무관하다.
  const canSend = editingMessage
    ? !isStudyCompleted && inputText.trim().length > 0
    : isConnected && !isStudyCompleted && hasOutgoingPayload;

  const connectionHint =
    connectionState === 'connecting'
      ? '채팅을 연결하고 있어요'
      : connectionState === 'reconnecting'
        ? '채팅 연결을 다시 준비하고 있어요'
        : null;

  const handleSend = () => {
    // 수정 모드: 새 메시지 전송이 아니라 기존 메시지의 본문 교체다.
    if (editingMessage) {
      const trimmed = inputText.trim();
      if (!trimmed || isStudyCompleted) return;
      if (trimmed === editingMessage.originalContent.trim()) {
        cancelEditing();
        return;
      }
      const { messageId } = editingMessage;
      void editMessage(messageId, trimmed)
        .then(() => cancelEditing())
        .catch((error) => {
          appAlert(
            '수정하지 못했어요',
            error instanceof Error ? error.message : '잠시 후 다시 시도해주세요.',
          );
        });
      return;
    }

    if (!isConnected || isStudyCompleted) {
      return;
    }

    // 첨부 사진이 업로드 완료(ready)됐으면 먼저 이미지 메시지로 전송한다.
    // 이미지·텍스트 결과는 독립적으로 처리해 한쪽 실패가 다른 쪽을 지우지 않습니다.
    if (pendingImage?.status === 'ready' && pendingImage.fileId !== undefined) {
      if (sendImage(pendingImage.fileId)) {
        setPendingImage(null);
      }
    }
    const trimmed = inputText.trim();
    if (trimmed && sendText(trimmed)) {
      setInputText('');
    }
  };

  const openMemberProfile = (memberId: number) => {
    router.push({
      pathname: '/(app)/member/[memberId]',
      params: { memberId: String(memberId) },
    });
  };

  // 멀티라인 입력창은 기본적으로 엔터 = 줄바꿈이라, 새로 들어온 텍스트에 개행이
  // 생기면 그 줄바꿈은 버리고 곧바로 전송 처리합니다(모바일 채팅 앱들의 공통 동작).
  // 연결되지 않았거나 전송 실패면 줄바꿈만 제거하고 본문은 유지합니다.
  const handleChangeText = (text: string) => {
    if (!text.endsWith('\n')) {
      setInputText(text);
      return;
    }
    const withoutNewline = text.slice(0, -1);
    const trimmed = withoutNewline.trim();
    if (!trimmed) {
      setInputText('');
      return;
    }
    if (!isConnected || isStudyCompleted) {
      setInputText(withoutNewline);
      return;
    }
    if (sendText(trimmed)) {
      setInputText('');
    } else {
      setInputText(withoutNewline);
    }
  };

  const handleAttachImage = async () => {
    if (pendingImage) return; // 한 번에 한 장만 첨부
    // 피커(별도 액티비티)로 넘어가기 전에 키보드를 먼저 닫아, 백그라운드 전환 중
    // close 애니메이션이 유실되며 keyboard.height가 stuck되는 상황 자체를 예방한다.
    // 포커스 게이트도 함께 내려 stuck된 값이 입력 바를 밀지 못하게 한다(create.tsx와 동일).
    Keyboard.dismiss();
    setIsInputFocused(false);
    const result = await ImagePicker.launchImageLibraryAsync({ mediaTypes: ['images'] });
    if (result.canceled) return;
    const asset = result.assets[0];
    if (!asset) return;
    if (!accessToken) {
      appAlert('오류', '로그인 상태를 확인해 주세요.');
      return;
    }

    // 미리보기를 즉시 입력창에 올리고, 뒤에서 업로드한다. 완료되면 보내기 버튼 활성화.
    setPendingImage({ uri: asset.uri, status: 'uploading' });
    try {
      // 업로드 전 JPEG로 정규화 + 축소(포맷 호환·업로드 속도). create.tsx의 사진 첨부와 동일.
      const shouldResize = typeof asset.width === 'number' && asset.width > 1600;
      const processed = await ImageManipulator.manipulateAsync(
        asset.uri,
        shouldResize ? [{ resize: { width: 1600 } }] : [],
        { compress: 0.7, format: ImageManipulator.SaveFormat.JPEG },
      );
      const sizeBytes = (await (await fetch(processed.uri)).blob()).size;
      const uploaded = await uploadMedia(
        accessToken,
        {
          fileUsage: 'CHAT_IMAGE',
          // CHAT_IMAGE는 어느 스터디 것인지 studyId가 필수 — 서버가 그 스터디 ACTIVE
          // 멤버인지 검증한다(누락 시 MEDIA_STUDY_FORBIDDEN).
          studyId,
          contentType: 'image/jpeg',
          sizeBytes,
          localUri: processed.uri,
          originalName: asset.fileName ?? undefined,
        },
        sessionVersion,
      );
      // 업로드 중 사용자가 취소(X)했으면 다시 살리지 않는다.
      setPendingImage((current) =>
        current && current.status === 'uploading'
          ? { uri: asset.uri, status: 'ready', fileId: uploaded.fileId }
          : current,
      );
    } catch (error) {
      setPendingImage(null);
      appAlert(
        '사진 첨부 실패',
        error instanceof Error
          ? error.message
          : '사진을 첨부하지 못했습니다. 잠시 후 다시 시도해주세요.',
      );
    }
  };

  const renderRow = ({ item }: { item: RenderItem }) => {
    if (item.kind === 'date') {
      return (
        <View style={styles.centerBadgeRow}>
          {/* 스터디 상세 '대화방 열기' 버튼과 동일한 글래스 pill 디자인. */}
          <View style={styles.dateChip}>
            <GlassSurface tint="#DDF1E4" tintOpacity={0.28} radius={16} intensity={32} />
            <Text style={styles.dateChipText}>{item.label}</Text>
          </View>
        </View>
      );
    }

    const { message } = item;
    if (message.messageType === 'SYSTEM' || !message.sender) {
      return (
        <View style={styles.centerBadgeRow}>
          <Text style={styles.centerBadgeText}>{message.content}</Text>
        </View>
      );
    }

    const isMine = message.sender.memberId === currentMemberId;
    const timeLabel = (
      <View style={styles.messageMeta}>
        {message.editedAt && !message.deleted ? (
          <Text style={styles.editedLabel}>수정됨</Text>
        ) : null}
        <Text style={styles.messageTime}>{formatTime(message.createdAt)}</Text>
      </View>
    );

    return (
      <View style={[styles.messageRow, isMine && styles.messageRowMine]}>
        {!isMine && (
          <Pressable
            accessibilityLabel={`${message.sender.nickname} 공개 프로필 열기`}
            accessibilityRole="button"
            onPress={() => openMemberProfile(message.sender!.memberId)}
            style={({ pressed }) => pressed && styles.profileLinkPressed}
          >
            <Image
              source={characterAvatarSource(message.sender.selectedCharacterId)}
              style={styles.avatar}
            />
          </Pressable>
        )}
        <View style={[styles.messageBody, isMine && styles.messageBodyMine]}>
          {!isMine && (
            <Pressable
              accessibilityLabel={`${message.sender.nickname} 공개 프로필 열기`}
              accessibilityRole="button"
              hitSlop={4}
              onPress={() => openMemberProfile(message.sender!.memberId)}
              style={({ pressed }) => pressed && styles.profileLinkPressed}
            >
              <Text style={styles.senderName}>{message.sender.nickname}</Text>
            </Pressable>
          )}
          <View style={styles.bubbleRow}>
            {isMine && timeLabel}
            {message.deleted ? (
              <View style={[styles.bubble, styles.bubbleDeleted]}>
                <Text style={styles.bubbleDeletedText}>삭제된 메시지입니다</Text>
              </View>
            ) : message.messageType === 'IMAGE' && message.image?.imageUrl ? (
              <Pressable
                accessibilityRole="imagebutton"
                accessibilityLabel="사진 크게 보기"
                onPress={() => setViewerImageUrl(message.image?.imageUrl ?? null)}
                onLongPress={() => handleLongPressMessage(message)}
                delayLongPress={350}
              >
                <ExpoImage
                  source={{
                    uri: message.image.imageUrl,
                    // 채팅 사진은 요청마다 서명·만료 쿼리가 바뀌는 Presigned GET URL이라,
                    // 쿼리를 뗀 S3 객체 경로를 cacheKey로 고정해 서명이 바뀌어도 캐시
                    // 적중되게 한다(커뮤니티 목록과 동일 패턴).
                    cacheKey: message.image.imageUrl.split('?')[0],
                  }}
                  style={styles.imageBubble}
                  contentFit="cover"
                  recyclingKey={String(message.messageId)}
                  cachePolicy="memory-disk"
                />
              </Pressable>
            ) : (
              <Pressable
                onLongPress={() => handleLongPressMessage(message)}
                delayLongPress={350}
                // 내 메시지가 아니면 길게 눌러도 아무 일도 없으므로 터치 피드백도 주지 않는다.
                style={({ pressed }) => pressed && isMine && styles.bubblePressed}
              >
                <LinearGradient
                  colors={isMine ? BUBBLE_MINE_GRADIENT : BUBBLE_OTHER_GRADIENT}
                  style={[styles.bubble, isMine ? styles.bubbleMine : styles.bubbleOther]}
                >
                  <Text style={styles.bubbleText}>{message.content}</Text>
                </LinearGradient>
              </Pressable>
            )}
            {!isMine && timeLabel}
          </View>
        </View>
      </View>
    );
  };

  if (isLoadingInitial) {
    return (
      <View style={styles.loadingContainer}>
        <ActivityIndicator color={PRIMARY_COLOR} />
      </View>
    );
  }

  return (
    <Animated.View style={[styles.flex, keyboardPushStyle]}>
      {/* 화면 전체 배경: 옅은 노랑·초록 얼룩덜룩 그라데이션. 헤더·입력창까지 이 위에 얹혀
          투명 부분으로 배경이 비친다. */}
      <View style={[StyleSheet.absoluteFill, styles.chatBgBase]} />
      <LinearGradient
        colors={['rgba(249, 242, 183, 0.85)', 'rgba(249, 242, 183, 0)']}
        start={{ x: 0, y: 0 }}
        end={{ x: 0.9, y: 0.8 }}
        style={StyleSheet.absoluteFill}
      />
      <LinearGradient
        colors={['rgba(206, 234, 182, 0)', 'rgba(206, 234, 182, 0.85)']}
        start={{ x: 0.1, y: 0.25 }}
        end={{ x: 1, y: 1 }}
        style={StyleSheet.absoluteFill}
      />
      <LinearGradient
        colors={['rgba(226, 240, 197, 0)', 'rgba(226, 240, 197, 0.55)']}
        start={{ x: 1, y: 0 }}
        end={{ x: 0.2, y: 0.7 }}
        style={StyleSheet.absoluteFill}
      />
      <View style={[styles.header, { paddingTop: insets.top + 8 }]}>
        <Pressable onPress={goBackOrHome} hitSlop={12} style={styles.headerIconButton}>
          <BackIcon />
        </Pressable>

        <View style={styles.headerTitleWrap}>
          <Text style={styles.headerTitle} numberOfLines={1}>
            {params.studyName ?? '스터디 채팅'}
          </Text>
          {!!params.participantCount && (
            <Text style={styles.headerSubtitle}>참여 인원 {params.participantCount}명</Text>
          )}
        </View>

        <Pressable
          accessibilityLabel={
            isMemberSidebarOpen ? '채팅방 구성원 목록 닫기' : '채팅방 구성원 목록 열기'
          }
          accessibilityRole="button"
          accessibilityState={{ expanded: isMemberSidebarOpen }}
          onPress={() => {
            Keyboard.dismiss();
            setIsMemberSidebarOpen(true);
          }}
          hitSlop={12}
          style={styles.headerIconButton}
        >
          <MembersIcon />
        </Pressable>
      </View>

      <View style={styles.flex}>
        <FlatList
          style={styles.flex}
          contentContainerStyle={styles.listContent}
          data={renderItems}
          keyExtractor={(item) => item.key}
          renderItem={renderRow}
          inverted
          onEndReached={loadMoreHistory}
          onEndReachedThreshold={0.3}
          ListFooterComponent={
            hasMoreHistory && isLoadingMore ? (
              <ActivityIndicator style={styles.historyLoading} color={PLACEHOLDER_COLOR} />
            ) : null
          }
        />
      </View>

      {isStudyCompleted ? (
        <View style={[styles.completedBanner, { paddingBottom: Math.max(16, insets.bottom) }]}>
          <Text style={styles.completedBannerText}>완료된 스터디입니다</Text>
        </View>
      ) : (
        <Animated.View style={[styles.inputBar, inputBarPadStyle]}>
          {connectionHint ? (
            <Text style={styles.connectionHint} accessibilityLiveRegion="polite">
              {connectionHint}
            </Text>
          ) : null}
          {pendingImage ? (
            <View style={styles.pendingRow}>
              <View style={styles.pendingThumbWrap}>
                <Image source={{ uri: pendingImage.uri }} style={styles.pendingThumb} />
                {pendingImage.status === 'uploading' ? (
                  <View style={styles.pendingUploading}>
                    <ActivityIndicator size="small" color="#FFFFFF" />
                  </View>
                ) : null}
                <Pressable
                  onPress={() => setPendingImage(null)}
                  hitSlop={8}
                  style={styles.pendingRemove}
                  accessibilityRole="button"
                  accessibilityLabel="사진 첨부 취소"
                >
                  <Text style={styles.pendingRemoveText}>×</Text>
                </Pressable>
              </View>
            </View>
          ) : null}
          {editingMessage ? (
            <View style={styles.editingBanner}>
              <Text numberOfLines={1} style={styles.editingBannerText}>
                메시지 수정 중
              </Text>
              <Pressable
                accessibilityLabel="메시지 수정 취소"
                accessibilityRole="button"
                hitSlop={10}
                onPress={cancelEditing}
              >
                <Text style={styles.editingBannerClose}>×</Text>
              </Pressable>
            </View>
          ) : null}
          <View style={styles.inputRow}>
            <Pressable
              onPress={handleAttachImage}
              // 수정 모드에서는 새 사진 첨부가 의미 없으므로 함께 잠근다.
              disabled={pendingImage !== null || editingMessage !== null}
              hitSlop={12}
              style={styles.iconButton}
            >
              <GlassSurface tint="#DDF1E4" tintOpacity={0.28} radius={20} intensity={32} />
              <PlusIcon />
            </Pressable>
            <View style={styles.textInputWrap}>
              <LinearGradient
                colors={['rgba(255, 255, 255, 0.9)', 'rgba(244, 248, 235, 0.9)']}
                start={{ x: 0, y: 0 }}
                end={{ x: 0, y: 1 }}
                style={StyleSheet.absoluteFill}
              />
              <TextInput
                style={styles.textInput}
                value={inputText}
                onChangeText={handleChangeText}
                placeholder="메시지를 입력하세요"
                placeholderTextColor={PLACEHOLDER_COLOR}
                multiline
                returnKeyType="send"
                onFocus={() => setIsInputFocused(true)}
                onBlur={() => setIsInputFocused(false)}
              />
            </View>
            <Pressable
              onPress={handleSend}
              disabled={!canSend}
              style={[styles.iconButton, !canSend && styles.sendButtonDisabled]}
            >
              <GlassSurface tint="#DDF1E4" tintOpacity={0.28} radius={20} intensity={32} />
              <SendIcon />
            </Pressable>
          </View>
        </Animated.View>
      )}

      <ChatMemberSidebar
        onClose={() => setIsMemberSidebarOpen(false)}
        onSelectMember={openMemberProfile}
        studyId={studyId}
        visible={isMemberSidebarOpen}
      />

      {/* 사진 크게 보기 — 배경(또는 사진) 탭하면 닫힘. */}
      <Modal
        visible={viewerImageUrl !== null}
        transparent
        animationType="fade"
        onRequestClose={() => setViewerImageUrl(null)}
      >
        <Pressable style={styles.viewerBackdrop} onPress={() => setViewerImageUrl(null)}>
          {viewerImageUrl ? (
            <>
              <ExpoImage
                // 버블에서 이미 받은 이미지를 뷰어가 재다운로드하지 않도록 동일한
                // 쿼리 제거 경로를 cacheKey로 쓴다(PostImageGallery 뷰어와 동일).
                source={{ uri: viewerImageUrl, cacheKey: viewerImageUrl.split('?')[0] }}
                style={styles.viewerImage}
                contentFit="contain"
                cachePolicy="memory-disk"
              />
              <Pressable
                accessibilityRole="button"
                accessibilityLabel="사진 갤러리에 저장"
                disabled={isSavingPhoto}
                // 배경 Pressable(닫기)로 터치가 흘러가지 않게 자체 핸들러에서 소비한다.
                onPress={() => handleSavePhoto(viewerImageUrl)}
                style={({ pressed }) => [
                  styles.viewerSaveButton,
                  (pressed || isSavingPhoto) && styles.viewerSaveButtonPressed,
                ]}
              >
                {isSavingPhoto ? (
                  <ActivityIndicator color="#FFFFFF" size="small" />
                ) : (
                  <DownloadIcon />
                )}
                <Text style={styles.viewerSaveButtonText}>
                  {isSavingPhoto ? '저장 중...' : '갤러리에 저장'}
                </Text>
              </Pressable>
            </>
          ) : null}
        </Pressable>
      </Modal>
    </Animated.View>
  );
}

const styles = StyleSheet.create({
  flex: { flex: 1 },
  chatBgBase: { backgroundColor: '#FBFBEC' },
  loadingContainer: {
    flex: 1,
    alignItems: 'center',
    justifyContent: 'center',
    backgroundColor: '#FFFFFF',
  },

  header: {
    flexDirection: 'row',
    alignItems: 'center',
    paddingHorizontal: 12,
    paddingBottom: 12,
    // 배경(노랑 그라데이션)이 비치도록 헤더도 투명.
    backgroundColor: 'transparent',
  },
  headerIconButton: {
    width: 36,
    height: 36,
    alignItems: 'center',
    justifyContent: 'center',
  },
  headerTitleWrap: {
    flex: 1,
    alignItems: 'center',
  },
  headerTitle: {
    fontSize: 16,
    fontWeight: '700',
    color: TEXT_COLOR,
  },
  headerSubtitle: {
    marginTop: 2,
    fontSize: 12,
    color: PLACEHOLDER_COLOR,
  },

  listContent: {
    paddingHorizontal: 16,
    paddingVertical: 12,
    gap: 12,
  },

  centerBadgeRow: {
    alignItems: 'center',
    marginVertical: 6,
  },
  // 스터디 상세 '대화방 열기' 버튼과 동일: 흰 rim + 반투명 + 소프트 그린 섀도우 + 글래스 틴트.
  dateChip: {
    overflow: 'hidden',
    borderRadius: 16,
    borderWidth: 1.5,
    borderColor: 'rgba(255, 255, 255, 0.92)',
    backgroundColor: 'rgba(255, 255, 255, 0.18)',
    boxShadow: '0px 8px 20px rgba(31, 95, 85, 0.15)',
    paddingHorizontal: 14,
    paddingVertical: 7,
  },
  dateChipText: {
    fontSize: 12.5,
    fontWeight: '700',
    letterSpacing: -0.2,
    color: DARK_GREEN_COLOR,
  },
  // 시스템 안내 메시지용(입장/퇴장 등) — 기존 옅은 회색 pill 유지.
  centerBadgeText: {
    fontSize: 12,
    color: '#6B7280',
    backgroundColor: '#EEF0F2',
    paddingHorizontal: 12,
    paddingVertical: 6,
    borderRadius: 999,
    overflow: 'hidden',
  },

  messageRow: {
    flexDirection: 'row',
    alignItems: 'flex-end',
    gap: 8,
  },
  messageRowMine: {
    justifyContent: 'flex-end',
  },
  avatar: {
    width: 32,
    height: 32,
    borderRadius: 16,
  },
  messageBody: {
    maxWidth: '75%',
    alignItems: 'flex-start',
  },
  messageBodyMine: {
    alignItems: 'flex-end',
  },
  senderName: {
    marginBottom: 4,
    marginLeft: 4,
    fontSize: 12,
    color: PLACEHOLDER_COLOR,
  },
  profileLinkPressed: {
    opacity: 0.58,
  },
  bubbleRow: {
    flexDirection: 'row',
    alignItems: 'flex-end',
    gap: 6,
  },
  bubble: {
    borderRadius: 16,
    paddingHorizontal: 14,
    paddingVertical: 10,
  },
  bubbleOther: {
    borderBottomLeftRadius: 4,
  },
  bubbleMine: {
    borderBottomRightRadius: 4,
  },
  bubbleText: {
    fontSize: 15,
    color: TEXT_COLOR,
    lineHeight: 20,
  },
  bubblePressed: {
    opacity: 0.7,
  },
  // 삭제된 메시지 톰스톤: 그라데이션 없이 옅은 회색 + 흐린 글씨로, 남은 대화보다
  // 시각적 무게가 낮게 가라앉힌다.
  bubbleDeleted: {
    backgroundColor: 'rgba(228, 231, 234, 0.55)',
    borderWidth: StyleSheet.hairlineWidth,
    borderColor: 'rgba(0, 0, 0, 0.06)',
  },
  bubbleDeletedText: {
    fontSize: 14,
    color: MUTED_TEXT_COLOR,
    fontStyle: 'italic',
    lineHeight: 20,
  },
  imageBubble: {
    width: 160,
    height: 160,
    borderRadius: 12,
    backgroundColor: '#F1F3F5',
  },
  viewerBackdrop: {
    flex: 1,
    // 사진이 없는 부분은 약간 어둡게(뒤가 살짝 비치는 딤).
    backgroundColor: 'rgba(0, 0, 0, 0.82)',
    alignItems: 'center',
    justifyContent: 'center',
    padding: 24,
  },
  viewerImage: {
    // 화면을 꽉 채우지 않고 조금 작게.
    width: '90%',
    height: '78%',
  },
  // 어두운 딤 배경 위 반투명 pill — 임장 지도의 글래스 버튼 톤을 다크 배경용으로 옮긴 것.
  viewerSaveButton: {
    flexDirection: 'row',
    alignItems: 'center',
    gap: 8,
    marginTop: 18,
    paddingHorizontal: 20,
    paddingVertical: 12,
    borderRadius: 24,
    backgroundColor: 'rgba(255, 255, 255, 0.16)',
    borderWidth: StyleSheet.hairlineWidth,
    borderColor: 'rgba(255, 255, 255, 0.35)',
  },
  viewerSaveButtonPressed: {
    backgroundColor: 'rgba(255, 255, 255, 0.28)',
  },
  viewerSaveButtonText: {
    fontSize: 15,
    fontWeight: '700',
    color: '#FFFFFF',
  },
  messageTime: {
    fontSize: 10,
    color: PLACEHOLDER_COLOR,
  },
  // 시간 라벨과 같은 열에 쌓이는 보조 메타(수정됨 + 시간).
  messageMeta: {
    alignItems: 'flex-end',
  },
  editedLabel: {
    fontSize: 9,
    color: PLACEHOLDER_COLOR,
  },
  // 입력바 위에 붙는 수정 모드 안내 줄. 첨부 미리보기와 같은 자리 문법을 따른다.
  editingBanner: {
    flexDirection: 'row',
    alignItems: 'center',
    justifyContent: 'space-between',
    marginHorizontal: 4,
    marginBottom: 6,
    paddingHorizontal: 12,
    paddingVertical: 7,
    borderRadius: 12,
    backgroundColor: 'rgba(221, 241, 228, 0.6)',
  },
  editingBannerText: {
    flex: 1,
    fontSize: 12,
    fontWeight: '700',
    color: DARK_GREEN_COLOR,
  },
  editingBannerClose: {
    fontSize: 16,
    fontWeight: '700',
    color: MUTED_TEXT_COLOR,
    paddingHorizontal: 4,
  },

  historyLoading: {
    paddingVertical: 12,
  },

  inputBar: {
    paddingHorizontal: 12,
    paddingTop: 8,
    // 배경(민트 그라데이션)이 비치도록 투명. 위쪽 얇은 구분선만 은은하게 남긴다.
    backgroundColor: 'transparent',
    borderTopWidth: 1,
    borderTopColor: 'rgba(0, 0, 0, 0.06)',
  },
  inputRow: {
    flexDirection: 'row',
    alignItems: 'flex-end',
    gap: 8,
  },
  pendingRow: {
    paddingBottom: 8,
    paddingLeft: 44,
  },
  pendingThumbWrap: {
    width: 64,
    height: 64,
  },
  pendingThumb: {
    width: 64,
    height: 64,
    borderRadius: 10,
    backgroundColor: '#F1F3F5',
  },
  pendingUploading: {
    position: 'absolute',
    top: 0,
    left: 0,
    right: 0,
    bottom: 0,
    borderRadius: 10,
    alignItems: 'center',
    justifyContent: 'center',
    backgroundColor: 'rgba(0, 0, 0, 0.35)',
  },
  pendingRemove: {
    position: 'absolute',
    top: -7,
    right: -7,
    width: 22,
    height: 22,
    borderRadius: 11,
    alignItems: 'center',
    justifyContent: 'center',
    backgroundColor: '#2C3630',
  },
  pendingRemoveText: {
    color: '#FFFFFF',
    fontSize: 15,
    lineHeight: 17,
    fontWeight: '700',
  },
  // + / 보내기 공용 글래스 원형 버튼(날짜 칩·대화방 열기와 동일 톤).
  iconButton: {
    width: 40,
    height: 40,
    borderRadius: 20,
    alignItems: 'center',
    justifyContent: 'center',
    overflow: 'hidden',
    borderWidth: 1,
    borderColor: 'rgba(255, 255, 255, 0.9)',
    backgroundColor: 'rgba(255, 255, 255, 0.22)',
    boxShadow: '0px 6px 16px rgba(31, 95, 85, 0.14)',
  },
  // 입력창은 은은한 세로 그라데이션 + 흰 rim 으로 글래스 톤과 어울리게.
  textInputWrap: {
    flex: 1,
    minHeight: 40,
    borderRadius: 18,
    overflow: 'hidden',
    borderWidth: 1,
    borderColor: 'rgba(255, 255, 255, 0.85)',
    justifyContent: 'center',
  },
  textInput: {
    maxHeight: 120,
    paddingHorizontal: 14,
    paddingVertical: 9,
    fontSize: 15,
    color: TEXT_COLOR,
  },
  sendButtonDisabled: {
    opacity: 0.5,
  },

  completedBanner: {
    paddingHorizontal: 16,
    paddingTop: 12,
    backgroundColor: '#F1F3F5',
  },
  connectionHint: {
    textAlign: 'center',
    fontSize: 12,
    color: MUTED_TEXT_COLOR,
    paddingBottom: 8,
  },
  completedBannerText: {
    textAlign: 'center',
    fontSize: 13,
    color: PLACEHOLDER_COLOR,
  },
});
