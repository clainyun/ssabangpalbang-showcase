import { Feather, Ionicons } from '@expo/vector-icons';
import { useRouter } from 'expo-router';
import { useState } from 'react';
import {
  Pressable,
  ScrollView,
  StyleSheet,
  Text,
  TextInput,
  View,
} from 'react-native';
import Animated, {
  useAnimatedKeyboard,
  useAnimatedStyle,
  useSharedValue,
  withSequence,
  withTiming,
} from 'react-native-reanimated';
import { SafeAreaView, useSafeAreaInsets } from 'react-native-safe-area-context';

import {
  DARK_GREEN_COLOR,
  DIVIDER_COLOR,
  MUTED_TEXT_COLOR,
  PRIMARY_COLOR,
  SOFT_GREEN_COLOR,
  SURFACE_COLOR,
  TEXT_COLOR,
} from '@/constants/colors';
import { FloatingTabBar } from '@/components/FloatingTabBar';

import { avatarColorFromNickname, avatarInitial } from './avatarColor';
import { PostImageGallery } from './PostImageGallery';
import {
  addDummyComment,
  boardLabelOf,
  deleteDummyComment,
  DUMMY_MY_NICKNAME,
  getDummyComments,
  getDummyPost,
  toggleDummyLike,
} from './dummyCommunityData';

/** "2026-07-30T08:00:00+09:00" → "2026.07.30" (날짜부는 이미 Seoul 로컬). */
function formatPostDate(iso: string): string {
  return iso.slice(0, 10).replace(/-/g, '.');
}

/**
 * 🟡 게시글 상세 리디자인(참고1~3). 지금은 더미 데이터(음수 postId) 전용.
 * 실 API 연동은 이후 단계에서 추가 예정(양수 id는 기존 화면이 담당).
 */
export function PostDetailRedesign({ postId }: { postId: number }) {
  const router = useRouter();
  const insets = useSafeAreaInsets();
  const post = getDummyPost(postId);
  const [liked, setLiked] = useState(post?.isLiked ?? false);
  const [likeCount, setLikeCount] = useState(post?.likeCount ?? 0);
  const heartScale = useSharedValue(1);
  const heartAnimStyle = useAnimatedStyle(() => ({ transform: [{ scale: heartScale.value }] }));
  const [comments, setComments] = useState(() => getDummyComments(postId));
  const [composerOpen, setComposerOpen] = useState(false);
  const [commentText, setCommentText] = useState('');

  // edge-to-edge + New Arch에선 adjustResize가 창을 줄여주지 않아 키보드가 하단 UI를 덮음.
  // reanimated의 useAnimatedKeyboard로 키보드 높이를 UI 스레드에서 프레임 단위로 구독 →
  // 키보드가 뜨는 순간부터 하단 바가 갭 없이 부드럽게 같이 올라온다.(keyboardDidShow 지연 제거)
  const keyboard = useAnimatedKeyboard();
  // 키보드 높이는 '입력창이 실제로 포커스일 때'만 반영한다. 안드로이드에서 액티비티/모달
  // 전환 중 close inset 애니메이션 콜백이 유실되면 keyboard.state/height가 OPEN·CLOSING에
  // stuck될 수 있는데, 포커스는 JS에서 우리가 직접 제어하므로 게이트가 내려가 있으면
  // stuck된 값이 화면을 밀지 못한다(글 작성 create.tsx와 동일 패턴).
  const [isInputFocused, setIsInputFocused] = useState(false);
  // 화면 전체를 키보드 높이만큼 위로: 하단 바가 키보드 위에 붙고 ScrollView 영역이 줄어 마지막 댓글까지 스크롤됨.
  const keyboardPushStyle = useAnimatedStyle(() => ({
    paddingBottom: isInputFocused ? keyboard.height.value : 0,
  }));
  // 컴포저 자체 하단 여백: 키보드 떠 있으면 홈 인디케이터 여백 불필요 → 12, 아니면 안전영역 확보.
  const composerPadStyle = useAnimatedStyle(() => ({
    paddingBottom:
      isInputFocused && keyboard.height.value > 0 ? 12 : Math.max(insets.bottom, 12),
  }));

  if (!post) {
    return (
      <SafeAreaView edges={['top']} style={styles.safeArea}>
        <View style={styles.header}>
          <Pressable hitSlop={8} onPress={() => router.back()} style={styles.headerSide}>
            <Feather color={TEXT_COLOR} name="chevron-left" size={24} />
          </Pressable>
          <Text style={styles.headerTitle}>게시글</Text>
          <View style={styles.headerSide} />
        </View>
        <View style={styles.notFound}>
          <Text style={styles.notFoundText}>글을 찾을 수 없어요.</Text>
        </View>
      </SafeAreaView>
    );
  }

  const apartment = post.connectedApartment;

  const toggleLike = () => {
    // 공유 더미를 직접 수정 → 목록 카드에도 반영. 로컬 상태는 즉시 피드백용.
    const result = toggleDummyLike(post.postId);
    setLiked(result.liked);
    setLikeCount(result.likeCount);
    // reanimated shared value 재할당(바운스) — react-hooks/immutability 오탐 억제.
    // eslint-disable-next-line react-hooks/immutability
    heartScale.value = withSequence(
      withTiming(1.3, { duration: 110 }),
      withTiming(1, { duration: 130 }),
    );
  };

  const openComposer = () => {
    setComposerOpen(true);
  };

  const submitComment = () => {
    const trimmed = commentText.trim();
    if (trimmed.length === 0) {
      return;
    }
    addDummyComment(post.postId, trimmed);
    setComments([...getDummyComments(post.postId)]);
    setCommentText('');
    setComposerOpen(false);
  };

  const removeComment = (commentId: number) => {
    deleteDummyComment(post.postId, commentId);
    setComments([...getDummyComments(post.postId)]);
  };

  return (
    <SafeAreaView edges={['top']} style={styles.safeArea}>
      {/* 키보드가 뜨면 이 래퍼의 paddingBottom이 키보드 높이만큼 애니메이션 → 하단 바가 키보드 위에
          붙고 ScrollView 영역이 줄어 마지막 댓글까지 스크롤된다. (edge-to-edge에서 adjustResize 미동작 대응) */}
      <Animated.View style={[styles.keyboardContainer, keyboardPushStyle]}>
      {/* ② 고정 헤더 — 뒤로가기 · 게시판명 + 하단 구분선. */}
      <View style={styles.header}>
        <Pressable
          accessibilityLabel="뒤로 가기"
          accessibilityRole="button"
          hitSlop={8}
          onPress={() => router.back()}
          style={({ pressed }) => [styles.headerSide, pressed && styles.pressed]}
        >
          <Feather color={TEXT_COLOR} name="chevron-left" size={24} />
        </Pressable>
        <Text numberOfLines={1} style={styles.headerTitle}>
          {boardLabelOf(post.boardType)}
        </Text>
        {/* 제목 가운데 정렬 유지용 스페이서(뒤로가기 버튼과 동일 폭). */}
        <View style={styles.headerSide} />
      </View>

      <ScrollView
        style={styles.scroll}
        contentContainerStyle={[
          styles.content,
          // 하단 고정 navbar(pill) 높이만큼 여백을 둬 마지막 댓글이 가려지지 않게.
          { paddingBottom: 70 + Math.max(18, insets.bottom + 8) + 16 },
        ]}
        keyboardShouldPersistTaps="handled"
        showsVerticalScrollIndicator={false}
      >
        {/* ③ 본문 상단 — (조회 상위) 뱃지 + 제목 + 작성자. */}
        {post.isHot ? <Text style={styles.hotLabel}>이번 주 많이 본 이야기</Text> : null}
        <Text style={styles.title}>{post.title}</Text>

        <View style={styles.authorRow}>
          <View style={[styles.avatar, { backgroundColor: avatarColorFromNickname(post.authorNickname) }]}>
            <Text style={styles.avatarText}>{avatarInitial(post.authorNickname)}</Text>
          </View>
          <View style={styles.authorInfo}>
            <Text style={styles.authorName}>{post.authorNickname}</Text>
            <Text style={styles.authorMeta}>
              {formatPostDate(post.createdAt)} · 조회 {post.viewCount}
            </Text>
          </View>
        </View>

        {/* ④ 연결된 아파트 카드 — 연결된 경우만. 터치 시 아파트 상세로. */}
        {apartment ? (
          <Pressable
            accessibilityRole="button"
            accessibilityLabel={`연결된 아파트 ${apartment.name}`}
            onPress={() =>
              router.push({
                pathname: '/(app)/apartment/[id]',
                params: { id: String(apartment.apartmentId) },
              })
            }
            style={({ pressed }) => [styles.aptCard, pressed && styles.aptCardPressed]}
          >
            <View style={styles.aptPin}>
              <Feather color={PRIMARY_COLOR} name="map-pin" size={17} />
            </View>
            <View style={styles.aptInfo}>
              <Text style={styles.aptLabel}>이 기록이 연결된 아파트</Text>
              <Text numberOfLines={1} style={styles.aptName}>
                {apartment.name}
              </Text>
            </View>
            <Feather color={MUTED_TEXT_COLOR} name="chevron-right" size={20} />
          </Pressable>
        ) : null}

        {/* ⑤ 첨부 이미지 — 여러 장이면 가로 스와이프, 탭하면 전체보기. */}
        <PostImageGallery images={post.images} labelPrefix={post.title} />

        {/* ⑥ 본문 텍스트 — 문단(빈 줄) 단위로 넉넉한 간격. */}
        <View style={styles.bodySection}>
          {post.content.split('\n\n').map((paragraph, index) => (
            <Text key={index} style={styles.bodyParagraph}>
              {paragraph}
            </Text>
          ))}
        </View>

        {/* ⑦ 액션 행 — 본문과 이어진 대화 사이. 좋아요 · 의견 남기기(누르면 하단 입력창). */}
        <View style={styles.inlineActions}>
          <Pressable
            accessibilityRole="button"
            accessibilityLabel="좋아요"
            accessibilityState={{ selected: liked }}
            hitSlop={6}
            onPress={toggleLike}
            style={styles.likeButton}
          >
            <Animated.View style={heartAnimStyle}>
              <Ionicons
                color={liked ? '#E0603E' : MUTED_TEXT_COLOR}
                name={liked ? 'heart' : 'heart-outline'}
                size={23}
              />
            </Animated.View>
            <Text style={[styles.likeCount, liked && styles.likeCountActive]}>{likeCount}</Text>
          </Pressable>

          <View style={styles.actionDivider} />

          <Pressable
            accessibilityRole="button"
            accessibilityLabel="의견 남기기"
            onPress={openComposer}
            style={styles.commentCta}
          >
            <Feather color={TEXT_COLOR} name="message-circle" size={18} />
            <Text style={styles.commentCtaText}>의견 남기기</Text>
          </Pressable>
        </View>

        {/* ⑧ 댓글(이어진 대화) 섹션. */}
        <View style={styles.commentSection}>
          <View style={styles.commentHeader}>
            <Text style={styles.commentTitle}>
              이어진 대화 <Text style={styles.commentCount}>{post.commentCount}</Text>
            </Text>
          </View>

          {comments.map((comment, index) => (
            <View key={comment.commentId} style={styles.commentRow}>
              <View style={styles.commentAvatarCol}>
                <View
                  style={[
                    styles.commentAvatar,
                    { backgroundColor: avatarColorFromNickname(comment.authorNickname) },
                  ]}
                >
                  <Text style={styles.commentAvatarText}>{avatarInitial(comment.authorNickname)}</Text>
                </View>
                {index < comments.length - 1 ? <View style={styles.commentLine} /> : null}
              </View>
              <View style={styles.commentBody}>
                <View style={styles.commentBodyHeader}>
                  <Text style={styles.commentNickname}>{comment.authorNickname}</Text>
                  {comment.authorNickname === DUMMY_MY_NICKNAME ? (
                    <Pressable
                      accessibilityLabel="내 댓글 삭제"
                      accessibilityRole="button"
                      hitSlop={6}
                      onPress={() => removeComment(comment.commentId)}
                      style={({ pressed }) => pressed && styles.pressed}
                    >
                      <Text style={styles.commentDeleteText}>삭제</Text>
                    </Pressable>
                  ) : null}
                </View>
                <Text style={styles.commentContent}>{comment.content}</Text>
                <Text style={styles.commentTime}>{comment.timeLabel}</Text>
              </View>
            </View>
          ))}
        </View>

      </ScrollView>

      {/* ⑨ 하단 고정 — 평소엔 앱 navbar, 의견 남기기 누르면 댓글 입력창(키보드 위, navbar는 숨김). */}
      {composerOpen ? (
        <Animated.View style={[styles.composerBar, composerPadStyle]}>
          <TextInput
            autoFocus
            multiline
            value={commentText}
            onChangeText={setCommentText}
            onFocus={() => setIsInputFocused(true)}
            onBlur={() => {
              setIsInputFocused(false);
              if (commentText.trim().length === 0) {
                setComposerOpen(false);
              }
            }}
            placeholder="따뜻한 의견을 남겨보세요"
            placeholderTextColor={MUTED_TEXT_COLOR}
            style={styles.composerInput}
          />
          <Pressable
            accessibilityRole="button"
            accessibilityLabel="댓글 등록"
            disabled={commentText.trim().length === 0}
            onPress={submitComment}
            style={({ pressed }) => [
              styles.composerSubmit,
              commentText.trim().length === 0 && styles.composerSubmitDisabled,
              pressed && styles.pressed,
            ]}
          >
            <Text style={styles.composerSubmitText}>등록</Text>
          </Pressable>
        </Animated.View>
      ) : (
        <FloatingTabBar activeTab="community" />
      )}
      </Animated.View>
    </SafeAreaView>
  );
}

const styles = StyleSheet.create({
  safeArea: {
    flex: 1,
    backgroundColor: SURFACE_COLOR,
  },
  keyboardContainer: {
    flex: 1,
  },
  header: {
    height: 52,
    paddingHorizontal: 14,
    flexDirection: 'row',
    alignItems: 'center',
    justifyContent: 'space-between',
    borderBottomWidth: StyleSheet.hairlineWidth,
    borderBottomColor: DIVIDER_COLOR,
  },
  headerSide: {
    width: 40,
    height: 40,
    alignItems: 'flex-start',
    justifyContent: 'center',
  },
  headerSideRight: {
    alignItems: 'flex-end',
  },
  pressed: {
    opacity: 0.5,
  },
  headerTitle: {
    flex: 1,
    textAlign: 'center',
    color: TEXT_COLOR,
    fontSize: 16,
    fontFamily: 'IBMPlexSansKR_700Bold',
    letterSpacing: -0.3,
  },
  scroll: {
    flex: 1,
  },
  content: {
    paddingHorizontal: 22,
    paddingTop: 22,
    paddingBottom: 40,
  },
  hotLabel: {
    marginBottom: 8,
    color: PRIMARY_COLOR,
    fontSize: 13,
    fontFamily: 'IBMPlexSansKR_700Bold',
    letterSpacing: -0.2,
  },
  title: {
    color: '#17211C',
    fontSize: 26,
    lineHeight: 34,
    fontFamily: 'IBMPlexSansKR_700Bold',
    letterSpacing: -0.6,
  },
  authorRow: {
    marginTop: 20,
    flexDirection: 'row',
    alignItems: 'center',
    gap: 12,
  },
  avatar: {
    width: 46,
    height: 46,
    borderRadius: 23,
    alignItems: 'center',
    justifyContent: 'center',
  },
  avatarText: {
    color: '#FFFFFF',
    fontSize: 16,
    fontFamily: 'IBMPlexSansKR_700Bold',
  },
  authorInfo: {
    flexShrink: 1,
  },
  authorName: {
    color: TEXT_COLOR,
    fontSize: 15,
    fontFamily: 'IBMPlexSansKR_700Bold',
    letterSpacing: -0.2,
  },
  authorMeta: {
    marginTop: 3,
    color: MUTED_TEXT_COLOR,
    fontSize: 12.5,
    fontFamily: 'IBMPlexSansKR_400Regular',
  },
  aptCard: {
    marginTop: 22,
    flexDirection: 'row',
    alignItems: 'center',
    gap: 12,
    paddingHorizontal: 16,
    paddingVertical: 13,
    borderRadius: 18,
    backgroundColor: SOFT_GREEN_COLOR,
  },
  aptCardPressed: {
    opacity: 0.7,
  },
  aptPin: {
    width: 40,
    height: 40,
    borderRadius: 20,
    alignItems: 'center',
    justifyContent: 'center',
    backgroundColor: '#D6EFDE',
  },
  aptInfo: {
    flex: 1,
    minWidth: 0,
  },
  aptLabel: {
    color: '#5C8A73',
    fontSize: 12,
    fontFamily: 'IBMPlexSansKR_600SemiBold',
    letterSpacing: -0.2,
  },
  aptName: {
    marginTop: 2,
    color: DARK_GREEN_COLOR,
    fontSize: 15.5,
    fontFamily: 'IBMPlexSansKR_700Bold',
    letterSpacing: -0.3,
  },
  imageWrap: {
    // 좌우 패딩을 뚫고 화면 끝까지 풀 와이드.
    marginTop: 24,
    marginHorizontal: -22,
    position: 'relative',
    backgroundColor: '#E8EDE9',
  },
  image: {
    width: '100%',
    aspectRatio: 1.3,
  },
  imageBadge: {
    position: 'absolute',
    right: 16,
    bottom: 16,
    paddingHorizontal: 12,
    paddingVertical: 7,
    borderRadius: 16,
    backgroundColor: 'rgba(255, 255, 255, 0.86)',
  },
  imageBadgeText: {
    color: '#17211C',
    fontSize: 12,
    fontFamily: 'IBMPlexSansKR_700Bold',
    letterSpacing: -0.2,
  },
  bodySection: {
    marginTop: 24,
    gap: 16,
  },
  bodyParagraph: {
    color: '#2C3630',
    fontSize: 16,
    lineHeight: 27,
    fontFamily: 'IBMPlexSansKR_400Regular',
    letterSpacing: -0.2,
  },
  commentSection: {
    marginTop: 24,
  },
  commentHeader: {
    flexDirection: 'row',
    alignItems: 'baseline',
    justifyContent: 'space-between',
    marginBottom: 20,
  },
  commentTitle: {
    color: '#17211C',
    fontSize: 18,
    fontFamily: 'IBMPlexSansKR_700Bold',
    letterSpacing: -0.3,
  },
  commentCount: {
    color: PRIMARY_COLOR,
    fontFamily: 'IBMPlexSansKR_700Bold',
  },
  commentRow: {
    flexDirection: 'row',
    gap: 12,
  },
  commentAvatarCol: {
    width: 36,
    alignItems: 'center',
  },
  commentAvatar: {
    width: 36,
    height: 36,
    borderRadius: 18,
    alignItems: 'center',
    justifyContent: 'center',
  },
  commentAvatarText: {
    color: '#FFFFFF',
    fontSize: 13,
    fontFamily: 'IBMPlexSansKR_700Bold',
  },
  commentLine: {
    width: 2,
    flex: 1,
    marginTop: 4,
    borderRadius: 1,
    backgroundColor: '#E1E6E2',
  },
  commentBody: {
    flex: 1,
    paddingBottom: 22,
  },
  commentBodyHeader: {
    flexDirection: 'row',
    alignItems: 'center',
    justifyContent: 'space-between',
  },
  commentNickname: {
    color: TEXT_COLOR,
    fontSize: 14,
    fontFamily: 'IBMPlexSansKR_700Bold',
    letterSpacing: -0.2,
  },
  commentDeleteText: {
    color: MUTED_TEXT_COLOR,
    fontSize: 12.5,
    fontFamily: 'IBMPlexSansKR_600SemiBold',
    letterSpacing: -0.2,
  },
  commentContent: {
    marginTop: 4,
    color: '#2C3630',
    fontSize: 14,
    lineHeight: 20,
    fontFamily: 'IBMPlexSansKR_400Regular',
    letterSpacing: -0.2,
  },
  commentTime: {
    marginTop: 8,
    color: MUTED_TEXT_COLOR,
    fontSize: 12,
    fontFamily: 'IBMPlexSansKR_400Regular',
  },
  inlineActions: {
    flexDirection: 'row',
    alignItems: 'center',
    marginTop: 20,
    paddingVertical: 4,
    borderTopWidth: StyleSheet.hairlineWidth,
    borderTopColor: DIVIDER_COLOR,
    borderBottomWidth: StyleSheet.hairlineWidth,
    borderBottomColor: DIVIDER_COLOR,
  },
  likeButton: {
    flexDirection: 'row',
    alignItems: 'center',
    gap: 7,
    paddingRight: 18,
  },
  likeCount: {
    color: MUTED_TEXT_COLOR,
    fontSize: 15,
    fontFamily: 'IBMPlexSansKR_700Bold',
  },
  likeCountActive: {
    color: '#E0603E',
  },
  actionDivider: {
    width: StyleSheet.hairlineWidth,
    height: 22,
    marginRight: 18,
    backgroundColor: DIVIDER_COLOR,
  },
  commentCta: {
    flex: 1,
    flexDirection: 'row',
    alignItems: 'center',
    justifyContent: 'center',
    gap: 8,
    height: 46,
  },
  commentCtaText: {
    color: TEXT_COLOR,
    fontSize: 15,
    fontFamily: 'IBMPlexSansKR_700Bold',
    letterSpacing: -0.2,
  },
  composerBar: {
    flexDirection: 'row',
    alignItems: 'flex-end',
    gap: 10,
    paddingTop: 12,
    paddingHorizontal: 16,
    borderTopWidth: StyleSheet.hairlineWidth,
    borderTopColor: DIVIDER_COLOR,
    backgroundColor: SURFACE_COLOR,
  },
  composerInput: {
    flex: 1,
    minHeight: 40,
    maxHeight: 120,
    paddingHorizontal: 14,
    paddingVertical: 10,
    borderRadius: 18,
    backgroundColor: '#F1F3F1',
    color: TEXT_COLOR,
    fontSize: 14,
    fontFamily: 'IBMPlexSansKR_400Regular',
  },
  composerSubmit: {
    height: 40,
    paddingHorizontal: 16,
    borderRadius: 20,
    alignItems: 'center',
    justifyContent: 'center',
    backgroundColor: PRIMARY_COLOR,
  },
  composerSubmitDisabled: {
    opacity: 0.4,
  },
  composerSubmitText: {
    color: '#FFFFFF',
    fontSize: 14,
    fontFamily: 'IBMPlexSansKR_700Bold',
  },
  notFound: {
    flex: 1,
    alignItems: 'center',
    justifyContent: 'center',
  },
  notFoundText: {
    color: MUTED_TEXT_COLOR,
    fontSize: 14,
    fontFamily: 'IBMPlexSansKR_400Regular',
  },
});
