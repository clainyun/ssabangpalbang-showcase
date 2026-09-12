import { Feather } from '@expo/vector-icons';
import { LinearGradient } from 'expo-linear-gradient';
import { useCallback, useEffect, useRef, useState } from 'react';
import {
  ActivityIndicator,
  BackHandler,
  Pressable,
  ScrollView,
  StyleSheet,
  Text,
  TextInput,
  View,
} from 'react-native';
import Animated, {
  cancelAnimation,
  Easing,
  interpolate,
  runOnJS,
  useAnimatedKeyboard,
  useAnimatedStyle,
  useSharedValue,
  withRepeat,
  withTiming,
} from 'react-native-reanimated';
import { useSafeAreaInsets } from 'react-native-safe-area-context';
import Svg, { Path } from 'react-native-svg';

import { GlassSurface } from '@/components/GlassSurface';
import { DARK_GREEN_COLOR } from '@/constants/colors';

import { SpriteSheetView } from './SpriteSheetView';
import { useChatbotConversation } from './useChatbotConversation';
import type { ChatbotMessage } from './api/types';

const MASCOT_SPRITE = require('../../../assets/chatbot-sprite.png');

const AnimatedLinearGradient = Animated.createAnimatedComponent(LinearGradient);

// 디자인(참고1.png) 톤 — 화이트 카드 + 민트 봇 말풍선 + 그린 유저 말풍선.
const INK_COLOR = '#17211C';
const BOT_BUBBLE_BG = '#E4F4EA';
const BOT_TEXT_COLOR = '#25352C';
const USER_BUBBLE_BG = '#13B26E';
const INPUT_BG = '#F1F3F1';
const MUTED = '#8A968F';
const BADGE_BG = '#D7EFDD';
const DIM_COLOR = 'rgba(10, 20, 15, 0.42)';

// 아이콘 위치(우측 하단)에서 통통 튀며 커지는 진입 이징.
const POP_EASING = Easing.bezier(0.34, 1.56, 0.64, 1);

interface ChatbotModalProps {
  open: boolean;
  onClose: () => void;
  apartmentId: number;
  /** 인사말에 넣을 아파트 이름. 없으면 이름 없이 안내한다. */
  apartmentName?: string;
}

export function ChatbotModal({
  open,
  onClose,
  apartmentId,
  apartmentName,
}: ChatbotModalProps) {
  const insets = useSafeAreaInsets();
  const { messages, isSending, isResponding, error, send, dismissError } =
    useChatbotConversation(apartmentId);

  const [input, setInput] = useState('');
  const [rendered, setRendered] = useState(open);
  const scrollRef = useRef<ScrollView>(null);

  const progress = useSharedValue(open ? 1 : 0);
  const float = useSharedValue(0);
  const keyboard = useAnimatedKeyboard();
  // 키보드 높이는 '입력창이 실제로 포커스일 때'만 반영한다. 안드로이드에서 액티비티·모달
  // 전환 중 close inset 애니메이션 콜백이 유실되면 keyboard.state/height가 OPEN·CLOSING에
  // stuck될 수 있는데, 포커스는 JS에서 직접 제어하므로 게이트가 내려가 있으면 stuck된
  // 값이 카드를 밀어 올리지 못한다(글 작성 post/create.tsx와 동일 패턴).
  const [isInputFocused, setIsInputFocused] = useState(false);

  // open→true면 즉시 렌더(prop 변화에 따른 상태 조정 — 렌더 단계 setState 패턴).
  if (open && !rendered) {
    setRendered(true);
    // 모달이 닫혀도 useAnimatedKeyboard 구독은 유지된다. 다시 열리는 이 시점에 포커스
    // 게이트를 내려 두어, 이전에 stuck된 키보드 값이 카드를 밀어 올린 채 뜨지 않게 한다.
    // (닫힐 때가 아니라 열릴 때 초기화 — effect 안에서의 setState는 연쇄 렌더를 유발한다.)
    setIsInputFocused(false);
  }

  // 진입/퇴장 애니메이션. open이 false가 되면 축소 후 완료 콜백에서 언마운트한다.
  useEffect(() => {
    if (open) {
      progress.value = 0;
      progress.value = withTiming(1, { duration: 340, easing: POP_EASING });
    } else if (rendered) {
      progress.value = withTiming(
        0,
        { duration: 190, easing: Easing.in(Easing.quad) },
        (finished) => {
          if (finished) runOnJS(setRendered)(false);
        },
      );
    }
  }, [open, rendered, progress]);

  // 배경 민트 원이 천천히 떠다니게 하는 반복 드라이버.
  useEffect(() => {
    float.value = withRepeat(
      withTiming(1, { duration: 2600, easing: Easing.inOut(Easing.sin) }),
      -1,
      true,
    );
    return () => cancelAnimation(float);
  }, [float]);

  // Android 뒤로가기로 닫기(RN Modal이 아니라 화면 내부 오버레이라 직접 처리).
  useEffect(() => {
    if (!open) return;
    const subscription = BackHandler.addEventListener('hardwareBackPress', () => {
      onClose();
      return true;
    });
    return () => subscription.remove();
  }, [open, onClose]);

  const dimStyle = useAnimatedStyle(() => ({ opacity: progress.value }));
  const cardEntranceStyle = useAnimatedStyle(() => ({
    opacity: progress.value,
    transform: [{ scale: interpolate(progress.value, [0, 1], [0.15, 1]) }],
  }));
  // 키보드 닫힘: 네비게이션 바를 피하는 하단 여백. 키보드 열림: 키보드 높이만큼 밀어
  // 입력창이 키보드 위에 오게 한다. 둘 중 큰 값을 써 시스템 바에 가리지 않게 한다.
  const bottomSafeArea = insets.bottom + 12;
  const keyboardStyle = useAnimatedStyle(() => ({
    paddingBottom: Math.max(bottomSafeArea, isInputFocused ? keyboard.height.value : 0),
  }));
  // 흰색-민트 그라데이션이 카드 안에서 천천히 대각선으로 드리프트하며 계속 움직인다.
  const gradientStyle = useAnimatedStyle(() => ({
    transform: [
      { translateX: interpolate(float.value, [0, 1], [-18, 18]) },
      { translateY: interpolate(float.value, [0, 1], [115, -115]) },
    ],
  }));

  const canSend = input.trim().length > 0 && !isResponding && !isSending;
  const handleSend = useCallback(() => {
    if (!canSend) return;
    send(input);
    setInput('');
  }, [canSend, input, send]);

  if (!rendered) return null;

  return (
    <Animated.View style={styles.overlay}>
      <Animated.View style={[styles.dim, dimStyle]}>
        <Pressable
          accessibilityLabel="챗봇 닫기"
          onPress={onClose}
          style={StyleSheet.absoluteFill}
        />
      </Animated.View>

      <Animated.View
        pointerEvents="box-none"
        style={[
          styles.keyboardWrap,
          { paddingTop: insets.top + 8 },
          keyboardStyle,
        ]}
      >
        <Animated.View style={[styles.card, cardEntranceStyle]}>
          {/* 배경 — 흰색→민트 그라데이션이 천천히 움직인다. 카드 모서리에서 잘리도록
              clip(overflow hidden) 안에 두고, 이동해도 모서리가 드러나지 않게 넉넉히
              키운다. */}
          <View style={styles.clip} pointerEvents="none">
            <AnimatedLinearGradient
              colors={['#FFFFFF', '#FFFFFF', '#CDEEDD', '#EAF7F1']}
              locations={[0, 0.4, 0.75, 1]}
              end={{ x: 1, y: 1 }}
              start={{ x: 0, y: 0 }}
              style={[styles.gradient, gradientStyle]}
            />
            <View style={styles.dogWrap}>
              {/* 정렬된 시트라 36프레임 핑퐁 순환이 매끄럽다. edgeInset은 블리딩 방지. */}
              <SpriteSheetView
                columns={6}
                edgeInset={0.05}
                fps={9}
                pingPong
                rows={6}
                size={210}
                source={MASCOT_SPRITE}
              />
            </View>
          </View>

          {/* 헤더 */}
          <View style={styles.header}>
            <View style={styles.avatar}>
              <SpriteSheetView
                columns={6}
                edgeInset={0.08}
                fps={9}
                pingPong
                rows={6}
                size={38}
                source={MASCOT_SPRITE}
              />
            </View>
            <Text style={styles.headerTitle}>임장 도우미</Text>
            <Pressable
              accessibilityLabel="챗봇 닫기"
              accessibilityRole="button"
              hitSlop={8}
              onPress={onClose}
              style={({ pressed }) => [styles.closeButton, pressed && styles.pressed]}
            >
              <Feather color={INK_COLOR} name="x" size={20} />
            </Pressable>
          </View>

          {/* 메시지 리스트 (배경 강아지가 비치도록 투명) */}
          <ScrollView
            ref={scrollRef}
            contentContainerStyle={styles.messages}
            keyboardShouldPersistTaps="handled"
            onContentSizeChange={() =>
              scrollRef.current?.scrollToEnd({ animated: true })
            }
            showsVerticalScrollIndicator={false}
            style={styles.messageScroll}
          >
            <GreetingBubble apartmentName={apartmentName} />
            {messages.map((message) => (
              <MessageBubble key={message.messageId} message={message} />
            ))}
          </ScrollView>

          {error !== null && (
            <Pressable onPress={dismissError} style={styles.errorBanner}>
              <Text style={styles.errorText}>{error}</Text>
            </Pressable>
          )}

          {/* 입력창 — 스터디 채팅방과 동일한 톤(그라데이션 입력 + 글래스 보내기 버튼). */}
          <View style={styles.inputRow}>
            <View style={styles.textInputWrap}>
              <LinearGradient
                colors={['rgba(255, 255, 255, 0.9)', 'rgba(244, 248, 235, 0.9)']}
                start={{ x: 0, y: 0 }}
                end={{ x: 0, y: 1 }}
                style={StyleSheet.absoluteFill}
              />
              <TextInput
                accessibilityLabel="메시지 입력"
                editable={!isResponding}
                maxLength={1000}
                onBlur={() => setIsInputFocused(false)}
                onChangeText={setInput}
                onFocus={() => setIsInputFocused(true)}
                onSubmitEditing={handleSend}
                placeholder={isResponding ? '답변을 기다리는 중...' : '메시지 입력...'}
                placeholderTextColor={MUTED}
                returnKeyType="send"
                style={styles.input}
                value={input}
              />
            </View>
            <Pressable
              accessibilityLabel="전송"
              accessibilityRole="button"
              accessibilityState={{ disabled: !canSend }}
              disabled={!canSend}
              onPress={handleSend}
              style={[styles.sendButton, !canSend && styles.sendButtonDisabled]}
            >
              <GlassSurface tint="#DDF1E4" tintOpacity={0.28} radius={20} intensity={32} />
              <SendIcon />
            </Pressable>
          </View>
        </Animated.View>
      </Animated.View>
    </Animated.View>
  );
}

// 스터디 채팅방(chat/[roomId].tsx)의 보내기 아이콘과 동일한 화살표.
function SendIcon() {
  return (
    <Svg
      fill="none"
      height={18}
      stroke={MUTED}
      strokeWidth={2}
      viewBox="0 0 24 24"
      width={18}
    >
      <Path
        d="M5 12h14M13 6l6 6-6 6"
        strokeLinecap="round"
        strokeLinejoin="round"
      />
    </Svg>
  );
}

function GreetingBubble({ apartmentName }: { apartmentName?: string }) {
  const name = apartmentName?.trim();
  return (
    <View style={[styles.bubbleRow, styles.rowLeft]}>
      <View style={[styles.bubble, styles.bubbleBot]}>
        <Text style={styles.botText}>
          안녕하세요! 임장 도우미{'\n'}팔방이에요🏠{'\n'}
          {name ? `지금 보고계시는 ${name}에 대해\n무엇이든 물어보세요!` : '무엇이든 물어보세요!'}
        </Text>
      </View>
    </View>
  );
}

function MessageBubble({ message }: { message: ChatbotMessage }) {
  const isUser = message.role === 'USER';
  const isPending =
    message.status === 'PENDING' || message.status === 'PROCESSING';
  const isFailed = message.status === 'FAILED';

  if (isUser) {
    return (
      <View style={[styles.bubbleRow, styles.rowRight]}>
        <View style={[styles.bubble, styles.bubbleUser]}>
          <Text style={styles.userText}>{message.content}</Text>
        </View>
      </View>
    );
  }

  return (
    <View style={[styles.bubbleRow, styles.rowLeft]}>
      <View style={[styles.bubble, styles.bubbleBot, isFailed && styles.bubbleFailed]}>
        {isPending ? (
          <ActivityIndicator color={DARK_GREEN_COLOR} size="small" />
        ) : (
          <>
            <Text style={[styles.botText, isFailed && styles.failText]}>
              {isFailed
                ? (message.failReason ?? '답변을 생성하지 못했어요. 다시 질문해 주세요.')
                : message.content}
            </Text>
            {!isFailed && message.basisLabel !== null && (
              <View style={styles.badge}>
                <Text style={styles.badgeText}>{message.basisLabel}</Text>
              </View>
            )}
          </>
        )}
      </View>
    </View>
  );
}

const styles = StyleSheet.create({
  overlay: {
    position: 'absolute',
    top: 0,
    left: 0,
    right: 0,
    bottom: 0,
    // 화면 트리 내부 오버레이라 다른 요소(탭바 등) 위에 오도록 최상단으로.
    zIndex: 100,
    elevation: 100,
  },
  dim: {
    position: 'absolute',
    top: 0,
    left: 0,
    right: 0,
    bottom: 0,
    backgroundColor: DIM_COLOR,
  },
  keyboardWrap: {
    flex: 1,
    paddingHorizontal: 14,
    justifyContent: 'flex-end',
  },
  card: {
    flex: 1,
    borderRadius: 26,
    backgroundColor: '#FFFFFF',
    transformOrigin: 'right bottom',
    boxShadow: '0px 18px 40px rgba(16, 39, 30, 0.28)',
  },
  clip: {
    position: 'absolute',
    top: 0,
    left: 0,
    right: 0,
    bottom: 0,
    borderRadius: 26,
    overflow: 'hidden',
  },
  gradient: {
    position: 'absolute',
    top: -130,
    left: -130,
    right: -130,
    bottom: -130,
  },
  dogWrap: {
    position: 'absolute',
    left: 0,
    right: 0,
    bottom: '20%',
    alignItems: 'center',
    opacity: 0.96,
  },
  header: {
    flexDirection: 'row',
    alignItems: 'center',
    gap: 10,
    paddingHorizontal: 18,
    paddingTop: 16,
    paddingBottom: 12,
  },
  avatar: {
    width: 38,
    height: 38,
    borderRadius: 19,
    overflow: 'hidden',
    backgroundColor: BOT_BUBBLE_BG,
  },
  headerTitle: {
    flex: 1,
    color: INK_COLOR,
    fontSize: 17,
    fontFamily: 'IBMPlexSansKR_700Bold',
    letterSpacing: -0.3,
  },
  closeButton: {
    width: 32,
    height: 32,
    borderRadius: 16,
    alignItems: 'center',
    justifyContent: 'center',
    backgroundColor: INPUT_BG,
  },
  messageScroll: { flex: 1 },
  messages: {
    paddingHorizontal: 16,
    paddingTop: 6,
    paddingBottom: 14,
    gap: 10,
  },
  bubbleRow: { flexDirection: 'row' },
  rowLeft: { justifyContent: 'flex-start' },
  rowRight: { justifyContent: 'flex-end' },
  bubble: {
    maxWidth: '82%',
    paddingHorizontal: 14,
    paddingVertical: 11,
    borderRadius: 18,
  },
  bubbleBot: {
    backgroundColor: BOT_BUBBLE_BG,
    borderTopLeftRadius: 6,
  },
  bubbleUser: {
    backgroundColor: USER_BUBBLE_BG,
    borderTopRightRadius: 6,
  },
  bubbleFailed: {
    backgroundColor: '#FDECEC',
  },
  botText: {
    color: BOT_TEXT_COLOR,
    fontSize: 14.5,
    lineHeight: 21,
    fontFamily: 'IBMPlexSansKR_400Regular',
  },
  userText: {
    color: '#FFFFFF',
    fontSize: 14.5,
    lineHeight: 21,
    fontFamily: 'IBMPlexSansKR_400Regular',
  },
  failText: { color: '#C4342B' },
  badge: {
    alignSelf: 'flex-start',
    marginTop: 8,
    paddingHorizontal: 9,
    paddingVertical: 3,
    borderRadius: 10,
    backgroundColor: BADGE_BG,
  },
  badgeText: {
    color: DARK_GREEN_COLOR,
    fontSize: 11,
    fontFamily: 'IBMPlexSansKR_600SemiBold',
  },
  errorBanner: {
    marginHorizontal: 16,
    marginBottom: 8,
    paddingHorizontal: 12,
    paddingVertical: 9,
    borderRadius: 12,
    backgroundColor: '#FDECEC',
  },
  errorText: {
    color: '#C4342B',
    fontSize: 12.5,
    fontFamily: 'IBMPlexSansKR_400Regular',
  },
  inputRow: {
    flexDirection: 'row',
    alignItems: 'flex-end',
    gap: 8,
    paddingHorizontal: 14,
    paddingTop: 8,
    paddingBottom: 14,
  },
  // 입력창은 은은한 세로 그라데이션 + 흰 rim (스터디 채팅방과 동일 톤).
  textInputWrap: {
    flex: 1,
    minHeight: 40,
    borderRadius: 18,
    overflow: 'hidden',
    borderWidth: 1,
    borderColor: 'rgba(255, 255, 255, 0.85)',
    justifyContent: 'center',
  },
  input: {
    maxHeight: 120,
    paddingHorizontal: 14,
    paddingVertical: 9,
    color: INK_COLOR,
    fontSize: 14.5,
    fontFamily: 'IBMPlexSansKR_400Regular',
  },
  // 보내기 버튼 — 글래스 원형(스터디 채팅방 iconButton과 동일 톤).
  sendButton: {
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
  sendButtonDisabled: {
    opacity: 0.5,
  },
  pressed: { opacity: 0.7 },
});
