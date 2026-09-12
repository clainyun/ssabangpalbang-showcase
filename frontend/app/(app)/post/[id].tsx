import { Feather, Ionicons } from '@expo/vector-icons';
import { Image } from 'expo-image';
import { useLocalSearchParams, useRouter } from 'expo-router';
import { type RefObject, useEffect, useRef, useState } from 'react';
import {
  ActivityIndicator,
  type NativeScrollEvent,
  type NativeSyntheticEvent,
  Pressable,
  ScrollView,
  StyleSheet,
  Text,
  TextInput,
  View,
  type ViewStyle,
} from 'react-native';
import Animated, {
  type AnimatedStyle,
  useAnimatedKeyboard,
  useAnimatedStyle,
  useSharedValue,
  withSequence,
  withTiming,
} from 'react-native-reanimated';
import { SafeAreaView, useSafeAreaInsets } from 'react-native-safe-area-context';

import { appAlert } from '@/components/AppDialog';
import {
  DARK_GREEN_COLOR,
  DIVIDER_COLOR,
  MUTED_TEXT_COLOR,
  PRIMARY_COLOR,
  SOFT_GREEN_COLOR,
  SURFACE_COLOR,
  TEXT_COLOR,
} from '@/constants/colors';
import { avatarColorFromNickname, avatarInitial } from '@/features/community/avatarColor';
import {
  CommunityApiError,
  type CommunityComment,
  type CommunityPostDetail,
} from '@/features/community/api/types';
import {
  useCreateCommunityComment,
  useDeleteCommunityComment,
  useUpdateCommunityComment,
} from '@/features/community/api/useCommunityCommentMutations';
import { useCommunityComments } from '@/features/community/api/useCommunityComments';
import { useCommunityPost } from '@/features/community/api/useCommunityPost';
import {
  useDeleteCommunityPost,
  useLikeCommunityPost,
  useUnlikeCommunityPost,
} from '@/features/community/api/useCommunityPostMutations';
import { isDummyPostId } from '@/features/community/dummyCommunityData';
import { PostDetailRedesign } from '@/features/community/PostDetailRedesign';
import { PostImageGallery } from '@/features/community/PostImageGallery';
import { FloatingTabBar } from '@/components/FloatingTabBar';
import { AuthSessionChangedError, AuthSessionExpiredError } from '@/lib/authenticatedFetch';
import { useAuthStore } from '@/store/authStore';

// 커뮤니티 목록/상세 리디자인과 동일한 잉크 그린 팔레트.
const INK_COLOR = '#17211C';
const BODY_TEXT_COLOR = '#2C3630';
const LIKE_ACTIVE_COLOR = '#E0603E';

const COMMENT_MAX_CODE_POINTS = 1000;
const COMMENT_EDGE_WHITESPACE_PATTERN = /^[\s​﻿]+|[\s​﻿]+$/gu;
const COMMENT_VISUALLY_BLANK_PATTERN = /^[\s\p{Cc}\p{Cf}]$/u;

function normalizeCommentContent(value: string) {
  return value.replace(COMMENT_EDGE_WHITESPACE_PATTERN, '');
}

function isVisuallyBlankComment(value: string) {
  return (
    value.length === 0 ||
    Array.from(value).every((character) => COMMENT_VISUALLY_BLANK_PATTERN.test(character))
  );
}

function getCommentCodePointLength(value: string) {
  return Array.from(value).length;
}

// 원형 아바타 — 프로필 이미지가 있으면 이미지, 없으면 닉네임 해시 색 + 이니셜(목록/상세 리디자인과 동일).
function Avatar({
  imageUrl,
  name,
  size,
  textSize,
}: {
  imageUrl: string | null;
  name: string;
  size: number;
  textSize: number;
}) {
  if (imageUrl) {
    return (
      <Image
        accessibilityLabel={`${name} 프로필 이미지`}
        contentFit="cover"
        // 프로필 이미지는 요청마다 서명·만료 쿼리가 바뀌는 Presigned GET URL이라, 쿼리를
        // 뗀 S3 객체 경로를 cacheKey·recyclingKey로 고정해 서명이 바뀌어도 캐시 적중되게
        // 한다(커뮤니티 목록·첨부 갤러리와 동일 패턴).
        source={{ uri: imageUrl, cacheKey: imageUrl.split('?')[0] }}
        recyclingKey={imageUrl.split('?')[0]}
        cachePolicy="memory-disk"
        style={{ width: size, height: size, borderRadius: size / 2 }}
      />
    );
  }

  return (
    <View
      accessible={false}
      accessibilityElementsHidden
      importantForAccessibility="no-hide-descendants"
      style={[
        styles.avatar,
        {
          width: size,
          height: size,
          borderRadius: size / 2,
          backgroundColor: avatarColorFromNickname(name),
        },
      ]}
    >
      <Text style={[styles.avatarText, { fontSize: textSize }]}>{avatarInitial(name)}</Text>
    </View>
  );
}

/** "2026-08-06T07:20:00+09:00" → "2026.08.06". */
function formatPostDate(value: string) {
  const iso = value.slice(0, 10);
  if (/^\d{4}-\d{2}-\d{2}$/.test(iso)) {
    return iso.replace(/-/g, '.');
  }
  return value;
}

function formatCommentTime(value: string) {
  const date = new Date(value);
  if (Number.isNaN(date.getTime())) {
    return value;
  }

  return date.toLocaleString('ko-KR', {
    month: 'long',
    day: 'numeric',
    hour: '2-digit',
    minute: '2-digit',
  });
}

export default function PostDetailScreen() {
  const { id } = useLocalSearchParams<{ id?: string | string[] }>();
  const rawPostId = Array.isArray(id) ? id[0] : id;
  const numericId = rawPostId && /^-?\d+$/.test(rawPostId) ? Number(rawPostId) : undefined;

  // 🟡 더미 글(음수 id)은 더미 전용 리디자인 화면으로. 실 API 글(양수)은 아래 실데이터 화면이 동일 디자인으로 렌더.
  if (numericId !== undefined && Number.isSafeInteger(numericId) && isDummyPostId(numericId)) {
    return <PostDetailRedesign postId={numericId} />;
  }

  return <RealPostDetailScreen rawPostId={rawPostId} />;
}

function RealPostDetailScreen({ rawPostId }: { rawPostId?: string }) {
  const parsedPostId = rawPostId && /^\d+$/.test(rawPostId) ? Number(rawPostId) : undefined;
  const postId =
    parsedPostId !== undefined && Number.isSafeInteger(parsedPostId) && parsedPostId > 0
      ? parsedPostId
      : undefined;
  const router = useRouter();
  const sessionVersion = useAuthStore((state) => state.sessionVersion);
  const postQuery = useCommunityPost(postId);

  // 자동 생성된 리포트 게시글(정보게시판)은 본문이 스텁이므로, 열리면 곧장 해당
  // 리포트 상세로 보낸다. replace라 뒤로가기는 커뮤니티로 돌아간다. 리포트가
  // 접근 불가(reportAvailable=false)면 그대로 글 본문을 보여준다.
  const autoReport = postQuery.data?.isAutoReport ? postQuery.data.report : null;
  const redirectReportId =
    autoReport && autoReport.reportAvailable ? autoReport.reportId : undefined;
  useEffect(() => {
    if (redirectReportId === undefined) {
      return;
    }
    router.replace({
      pathname: '/(app)/report/[reportId]',
      params: { reportId: String(redirectReportId) },
    });
  }, [redirectReportId, router]);

  // 리다이렉트 확정 시엔 스텁 본문이 깜빡이지 않도록 로딩만 보여준다.
  if (redirectReportId !== undefined) {
    return (
      <SafeAreaView style={styles.redirectingScreen}>
        <ActivityIndicator color={PRIMARY_COLOR} size="small" />
      </SafeAreaView>
    );
  }

  return (
    <PostDetailContent
      key={`${sessionVersion}:${postId ?? 'invalid'}`}
      postId={postId}
      postQuery={postQuery}
    />
  );
}

function PostDetailContent({
  postId,
  postQuery,
}: {
  postId: number | undefined;
  postQuery: ReturnType<typeof useCommunityPost>;
}) {
  const router = useRouter();
  const insets = useSafeAreaInsets();
  const inputRef = useRef<TextInput>(null);
  const commentUnavailableAlertedRef = useRef(false);
  const commentSubmitRef = useRef(false);
  const deleteMutation = useDeleteCommunityPost();
  const createCommentMutation = useCreateCommunityComment();
  const updateCommentMutation = useUpdateCommunityComment();
  const deleteCommentMutation = useDeleteCommunityComment();
  const likeMutation = useLikeCommunityPost();
  const unlikeMutation = useUnlikeCommunityPost();
  const commentsQuery = useCommunityComments(postId, postQuery.isSuccess);

  const [commentText, setCommentText] = useState('');
  const [editingComment, setEditingComment] = useState<CommunityComment | null>(null);
  const [composerOpen, setComposerOpen] = useState(false);

  // edge-to-edge + New Arch에선 adjustResize가 창을 안 줄여 키보드가 하단 바를 덮음.
  // reanimated useAnimatedKeyboard로 화면 전체를 키보드 높이만큼 위로 밀어 하단 바가 키보드 위에 붙는다.
  const keyboard = useAnimatedKeyboard();
  // 키보드 높이는 '댓글 입력창이 실제로 포커스일 때'만 반영한다. 안드로이드에서 액티비티/
  // 모달 전환 중 close inset 애니메이션 콜백이 유실되면 keyboard.state/height가 OPEN·CLOSING에
  // stuck될 수 있는데, 포커스는 JS에서 우리가 직접 제어하므로 게이트가 내려가 있으면
  // stuck된 값이 화면을 밀지 못한다(글 작성 create.tsx와 동일 패턴).
  const [isInputFocused, setIsInputFocused] = useState(false);
  const keyboardPushStyle = useAnimatedStyle(() => ({
    paddingBottom: isInputFocused ? keyboard.height.value : 0,
  }));
  const bottomBarPadStyle = useAnimatedStyle(() => ({
    paddingBottom:
      isInputFocused && keyboard.height.value > 0 ? 12 : Math.max(insets.bottom, 12),
  }));

  useEffect(() => {
    if (
      commentUnavailableAlertedRef.current ||
      !(commentsQuery.error instanceof CommunityApiError) ||
      commentsQuery.error.code !== 'POST_NOT_FOUND'
    ) {
      return;
    }

    commentUnavailableAlertedRef.current = true;
    appAlert('게시글을 확인할 수 없어요', '삭제되었거나 확인할 수 없는 게시글입니다.', [
      {
        text: '목록으로',
        onPress: () => router.replace('/(app)/(tabs)/community'),
      },
    ]);
  }, [commentsQuery.error, router]);

  const goBack = () => {
    if (router.canGoBack()) {
      router.back();
      return;
    }

    router.replace('/(app)/(tabs)/community');
  };

  const openComposer = () => {
    setComposerOpen(true);
    requestAnimationFrame(() => inputRef.current?.focus());
  };

  const closeComposerIfEmpty = () => {
    // 입력창이 blur되면 항상 포커스 게이트부터 내려 stuck된 키보드 값이 화면을 못 밀게 한다.
    setIsInputFocused(false);
    if (editingComment === null && normalizeCommentContent(commentText).length === 0) {
      setComposerOpen(false);
    }
  };

  const showCommentError = (action: '등록' | '수정' | '삭제', error: Error) => {
    if (error instanceof AuthSessionExpiredError || error instanceof AuthSessionChangedError) {
      return;
    }

    if (error instanceof CommunityApiError && error.code === 'POST_NOT_FOUND') {
      appAlert('게시글을 확인할 수 없어요', '삭제되었거나 확인할 수 없는 게시글입니다.', [
        {
          text: '목록으로',
          onPress: () => router.replace('/(app)/(tabs)/community'),
        },
      ]);
      return;
    }

    if (
      error instanceof CommunityApiError &&
      (error.code === 'COMMENT_ALREADY_DELETED' || error.code === 'COMMENT_NOT_FOUND')
    ) {
      if (action === '수정') {
        setEditingComment(null);
        setCommentText('');
      }
      void commentsQuery.refetch();
      appAlert(`댓글을 ${action}하지 못했어요`, '이미 삭제되었거나 확인할 수 없는 댓글입니다.');
      return;
    }

    if (
      error instanceof CommunityApiError &&
      (error.code === 'COMMENT_UPDATE_FORBIDDEN' || error.code === 'COMMENT_DELETE_FORBIDDEN')
    ) {
      if (action === '수정') {
        setEditingComment(null);
        setCommentText('');
      }
      void commentsQuery.refetch();
      appAlert(
        `댓글을 ${action}할 수 없어요`,
        '댓글 권한이 변경되었습니다. 최신 목록을 불러옵니다.',
      );
      return;
    }

    appAlert(`댓글을 ${action}하지 못했어요`, error.message || '잠시 후 다시 시도해주세요.');
  };

  const submitComment = (post: CommunityPostDetail) => {
    const content = normalizeCommentContent(commentText);
    const mutationPending = createCommentMutation.isPending || updateCommentMutation.isPending;
    if (
      commentSubmitRef.current ||
      mutationPending ||
      isVisuallyBlankComment(content) ||
      getCommentCodePointLength(content) > COMMENT_MAX_CODE_POINTS ||
      !post.permissions.canComment
    ) {
      return;
    }

    if (editingComment !== null) {
      if (!editingComment.canEdit) {
        return;
      }
      commentSubmitRef.current = true;
      updateCommentMutation.mutate(
        {
          commentId: editingComment.commentId,
          content,
          postId: post.postId,
        },
        {
          onSuccess: () => {
            setEditingComment(null);
            setCommentText('');
            setComposerOpen(false);
          },
          onError: (error) => {
            showCommentError('수정', error);
          },
          onSettled: () => {
            commentSubmitRef.current = false;
          },
        },
      );
      return;
    }

    commentSubmitRef.current = true;
    createCommentMutation.mutate(
      {
        content,
        postId: post.postId,
      },
      {
        onSuccess: () => {
          setCommentText('');
          setComposerOpen(false);
        },
        onError: (error) => {
          showCommentError('등록', error);
        },
        onSettled: () => {
          commentSubmitRef.current = false;
        },
      },
    );
  };

  const editComment = (comment: CommunityComment) => {
    if (
      !comment.canEdit ||
      createCommentMutation.isPending ||
      updateCommentMutation.isPending ||
      deleteCommentMutation.isPending
    ) {
      return;
    }

    setEditingComment(comment);
    setCommentText(comment.content);
    setComposerOpen(true);
    requestAnimationFrame(() => inputRef.current?.focus());
  };

  const cancelCommentEdit = () => {
    if (updateCommentMutation.isPending) {
      return;
    }
    setEditingComment(null);
    setCommentText('');
    setComposerOpen(false);
  };

  const confirmDeleteComment = (post: CommunityPostDetail, comment: CommunityComment) => {
    if (
      !comment.canDelete ||
      deleteCommentMutation.isPending ||
      createCommentMutation.isPending ||
      updateCommentMutation.isPending
    ) {
      return;
    }

    appAlert('댓글을 삭제할까요?', '삭제한 댓글은 다시 확인할 수 없습니다.', [
      {
        text: '취소',
        style: 'cancel',
      },
      {
        text: '삭제',
        style: 'destructive',
        onPress: () => {
          deleteCommentMutation.mutate(
            {
              commentId: comment.commentId,
              postId: post.postId,
            },
            {
              onSuccess: () => {
                if (editingComment?.commentId === comment.commentId) {
                  setEditingComment(null);
                  setCommentText('');
                  setComposerOpen(false);
                }
              },
              onError: (error) => {
                showCommentError('삭제', error);
              },
            },
          );
        },
      },
    ]);
  };

  const editPost = (post: CommunityPostDetail) => {
    if (!post.permissions.canEdit || deleteMutation.isPending) {
      return;
    }

    router.push({
      pathname: '/(app)/post/create',
      params: { postId: String(post.postId) },
    });
  };

  const confirmDeletePost = (post: CommunityPostDetail) => {
    if (!post.permissions.canDelete || deleteMutation.isPending) {
      return;
    }

    appAlert('게시글을 삭제할까요?', '삭제한 게시글은 다시 확인할 수 없습니다.', [
      {
        text: '취소',
        style: 'cancel',
      },
      {
        text: '삭제',
        style: 'destructive',
        onPress: () => {
          deleteMutation.mutate(post.postId, {
            onSuccess: () => {
              router.replace('/(app)/(tabs)/community');
            },
            onError: (error) => {
              if (
                error instanceof AuthSessionExpiredError ||
                error instanceof AuthSessionChangedError
              ) {
                return;
              }

              appAlert(
                '게시글을 삭제하지 못했어요',
                error instanceof Error ? error.message : '잠시 후 다시 시도해주세요.',
                error instanceof CommunityApiError && error.code === 'POST_NOT_FOUND'
                  ? [
                      {
                        text: '목록으로',
                        onPress: () => router.replace('/(app)/(tabs)/community'),
                      },
                    ]
                  : undefined,
              );
            },
          });
        },
      },
    ]);
  };

  const toggleLike = (post: CommunityPostDetail) => {
    if (!post.permissions.canLike || likeMutation.isPending || unlikeMutation.isPending) {
      return;
    }

    const onError = (error: Error) => {
      if (error instanceof AuthSessionExpiredError || error instanceof AuthSessionChangedError) {
        return;
      }

      if (error instanceof CommunityApiError && error.code === 'POST_NOT_FOUND') {
        appAlert('게시글을 확인할 수 없어요', '삭제되었거나 확인할 수 없는 게시글입니다.', [
          {
            text: '목록으로',
            onPress: () => router.replace('/(app)/(tabs)/community'),
          },
        ]);
        return;
      }

      appAlert(
        post.likedByMe ? '좋아요를 해제하지 못했어요' : '좋아요를 등록하지 못했어요',
        error.message || '잠시 후 다시 시도해주세요.',
      );
    };

    if (post.likedByMe) {
      unlikeMutation.mutate(post.postId, {
        onError,
      });
      return;
    }

    likeMutation.mutate(post.postId, {
      onError: (error) => {
        onError(error);
      },
    });
  };

  const managementActions =
    postQuery.data && (postQuery.data.permissions.canEdit || postQuery.data.permissions.canDelete)
      ? (
          <View style={styles.managementActions}>
            {postQuery.data.permissions.canEdit && (
              <Pressable
                accessibilityLabel="게시글 수정"
                accessibilityRole="button"
                disabled={deleteMutation.isPending}
                hitSlop={6}
                onPress={() => editPost(postQuery.data)}
                style={({ pressed }) => [styles.managementButton, pressed && styles.pressed]}
              >
                <Text style={styles.managementButtonText}>수정</Text>
              </Pressable>
            )}
            {postQuery.data.permissions.canDelete && (
              <Pressable
                accessibilityLabel="게시글 삭제"
                accessibilityRole="button"
                accessibilityState={{
                  busy: deleteMutation.isPending,
                  disabled: deleteMutation.isPending,
                }}
                disabled={deleteMutation.isPending}
                hitSlop={6}
                onPress={() => confirmDeletePost(postQuery.data)}
                style={({ pressed }) => [styles.managementButton, pressed && styles.pressed]}
              >
                <Text style={styles.deleteButtonText}>
                  {deleteMutation.isPending ? '삭제 중' : '삭제'}
                </Text>
              </Pressable>
            )}
          </View>
        )
      : (
          <View style={styles.headerSide} />
        );

  return (
    <SafeAreaView edges={['top']} style={styles.safeArea}>
      <Animated.View style={[styles.keyboardContainer, keyboardPushStyle]}>
        {/* 헤더 — 뒤로가기 · 게시판명 + (권한 시) 수정/삭제. 하단 구분선. */}
        <View style={styles.header}>
          <Pressable
            accessibilityLabel="커뮤니티로 돌아가기"
            accessibilityRole="button"
            hitSlop={8}
            onPress={goBack}
            style={({ pressed }) => [styles.headerSide, pressed && styles.pressed]}
          >
            <Feather color={INK_COLOR} name="chevron-left" size={24} />
          </Pressable>
          <Text numberOfLines={1} style={styles.headerTitle}>
            {postQuery.data
              ? postQuery.data.isAutoReport
                ? '리포트'
                : postQuery.data.boardType === 'FREE'
                  ? '자유게시판'
                  : '정보게시판'
              : '게시글'}
          </Text>
          {managementActions}
        </View>

        {postId === undefined ? (
          <DetailState
            body="게시글 번호가 올바르지 않습니다."
            icon="alert-circle"
            title="게시글을 열 수 없어요"
          />
        ) : postQuery.isPending ? (
          <DetailState body="게시글을 불러오고 있어요." loading title="잠시만 기다려주세요" />
        ) : postQuery.isError || !postQuery.data ? (
          <DetailState
            body={
              postQuery.error instanceof Error
                ? postQuery.error.message
                : '잠시 후 다시 시도해주세요.'
            }
            icon="alert-circle"
            onRetry={() => void postQuery.refetch()}
            title="게시글을 불러오지 못했어요"
          />
        ) : (
          <LoadedPostDetail
            bottomBarPadStyle={bottomBarPadStyle}
            commentText={commentText}
            commentsQuery={commentsQuery}
            composerOpen={composerOpen}
            deletingCommentId={
              deleteCommentMutation.isPending
                ? deleteCommentMutation.variables?.commentId
                : undefined
            }
            editingComment={editingComment}
            inputRef={inputRef}
            onBlurComposer={closeComposerIfEmpty}
            onFocusComposer={() => setIsInputFocused(true)}
            onCancelCommentEdit={cancelCommentEdit}
            onChangeComment={setCommentText}
            onDeleteComment={(comment) => confirmDeleteComment(postQuery.data, comment)}
            onEditComment={editComment}
            onLike={toggleLike}
            onOpenComposer={openComposer}
            onSubmitComment={submitComment}
            post={postQuery.data}
            submittingComment={createCommentMutation.isPending || updateCommentMutation.isPending}
            liking={likeMutation.isPending || unlikeMutation.isPending}
          />
        )}
      </Animated.View>
    </SafeAreaView>
  );
}

function DetailState({
  body,
  icon,
  loading = false,
  onRetry,
  title,
}: {
  body: string;
  icon?: 'alert-circle';
  loading?: boolean;
  onRetry?: () => void;
  title: string;
}) {
  return (
    <View accessibilityLiveRegion="polite" style={styles.detailState}>
      {loading ? (
        <ActivityIndicator color={PRIMARY_COLOR} size="small" />
      ) : (
        icon && <Feather color={MUTED_TEXT_COLOR} name={icon} size={30} />
      )}
      <Text style={styles.detailStateTitle}>{title}</Text>
      <Text style={styles.detailStateBody}>{body}</Text>
      {onRetry && (
        <Pressable
          accessibilityRole="button"
          onPress={onRetry}
          style={({ pressed }) => [styles.retryButton, pressed && styles.pressed]}
        >
          <Text style={styles.retryButtonText}>다시 시도</Text>
        </Pressable>
      )}
    </View>
  );
}

function LoadedPostDetail({
  bottomBarPadStyle,
  commentText,
  commentsQuery,
  composerOpen,
  deletingCommentId,
  editingComment,
  inputRef,
  onBlurComposer,
  onFocusComposer,
  onCancelCommentEdit,
  onChangeComment,
  onDeleteComment,
  onEditComment,
  onLike,
  onOpenComposer,
  onSubmitComment,
  post,
  submittingComment,
  liking,
}: {
  bottomBarPadStyle: AnimatedStyle<ViewStyle>;
  commentText: string;
  commentsQuery: ReturnType<typeof useCommunityComments>;
  composerOpen: boolean;
  deletingCommentId: number | undefined;
  editingComment: CommunityComment | null;
  inputRef: RefObject<TextInput | null>;
  onBlurComposer: () => void;
  onFocusComposer: () => void;
  onCancelCommentEdit: () => void;
  onChangeComment: (value: string) => void;
  onDeleteComment: (comment: CommunityComment) => void;
  onEditComment: (comment: CommunityComment) => void;
  onLike: (post: CommunityPostDetail) => void;
  onOpenComposer: () => void;
  onSubmitComment: (post: CommunityPostDetail) => void;
  post: CommunityPostDetail;
  submittingComment: boolean;
  liking: boolean;
}) {
  const router = useRouter();
  const insets = useSafeAreaInsets();
  const heartScale = useSharedValue(1);
  const heartAnimStyle = useAnimatedStyle(() => ({ transform: [{ scale: heartScale.value }] }));

  const imageAttachments = [...post.attachments]
    .filter(
      (attachment) =>
        attachment.available &&
        attachment.contentType?.startsWith('image/') === true &&
        attachment.fileUrl !== null,
    )
    .sort((a, b) => a.displayOrder - b.displayOrder);
  const imageUrls = imageAttachments
    .map((attachment) => attachment.fileUrl)
    .filter((url): url is string => url !== null);
  const normalizedComment = normalizeCommentContent(commentText);
  const commentPages = commentsQuery.data?.pages ?? [];
  const initialCommentsUnavailable = commentsQuery.isError && commentPages.length === 0;
  const commentsReady = !commentsQuery.isPending && !initialCommentsUnavailable;
  const canSubmitComment =
    !submittingComment &&
    commentsReady &&
    post.permissions.canComment &&
    !isVisuallyBlankComment(normalizedComment) &&
    getCommentCodePointLength(normalizedComment) <= COMMENT_MAX_CODE_POINTS &&
    (editingComment === null || editingComment.canEdit);
  const comments = Array.from(
    new Map(
      commentPages.flatMap((page) => page.content).map((comment) => [comment.commentId, comment]),
    ).values(),
  ).sort((left, right) => {
    const timestampDifference =
      new Date(left.createdAt).getTime() - new Date(right.createdAt).getTime();
    return Number.isNaN(timestampDifference) || timestampDifference === 0
      ? left.commentId - right.commentId
      : timestampDifference;
  });
  const lastCommentPage =
    commentPages.length > 0 ? commentPages[commentPages.length - 1] : undefined;
  const commentCount = lastCommentPage?.totalCount ?? post.commentCount;
  const showComposer = composerOpen || editingComment !== null;
  const openMemberProfile = (memberId: number) => {
    router.push({
      pathname: '/(app)/member/[memberId]',
      params: { memberId: String(memberId) },
    });
  };

  const handleLike = () => {
    if (!post.permissions.canLike || liking) {
      return;
    }
    // reanimated shared value 재할당(바운스) — react-hooks/immutability 오탐 억제.
    // eslint-disable-next-line react-hooks/immutability
    heartScale.value = withSequence(
      withTiming(1.3, { duration: 110 }),
      withTiming(1, { duration: 130 }),
    );
    onLike(post);
  };

  const handleScroll = (event: NativeSyntheticEvent<NativeScrollEvent>) => {
    if (
      !commentsQuery.hasNextPage ||
      commentsQuery.isFetchingNextPage ||
      commentsQuery.isFetchNextPageError
    ) {
      return;
    }

    const { contentOffset, contentSize, layoutMeasurement } = event.nativeEvent;
    const remainingDistance = contentSize.height - (contentOffset.y + layoutMeasurement.height);
    if (remainingDistance < 220) {
      void commentsQuery.fetchNextPage({
        cancelRefetch: false,
      });
    }
  };

  return (
    <>
      <ScrollView
        style={styles.scroll}
        contentContainerStyle={[
          styles.content,
          // 하단 고정 navbar(pill) 높이만큼 여백을 둬 마지막 댓글이 가려지지 않게.
          { paddingBottom: 70 + Math.max(18, insets.bottom + 8) + 16 },
        ]}
        keyboardShouldPersistTaps="handled"
        onScroll={handleScroll}
        scrollEventThrottle={16}
        showsVerticalScrollIndicator={false}
      >
        {/* 본문 상단 — (조회 상위) 뱃지 + 제목 + 작성자. */}
        {post.isHot ? <Text style={styles.hotLabel}>이번 주 많이 본 이야기</Text> : null}
        <Text style={styles.title}>{post.title}</Text>

        <Pressable
          accessibilityLabel={
            post.author.memberId === null
              ? undefined
              : `${post.author.nickname} 공개 프로필 열기`
          }
          accessibilityRole={post.author.memberId === null ? undefined : 'button'}
          disabled={post.author.memberId === null}
          onPress={() => {
            if (post.author.memberId !== null) openMemberProfile(post.author.memberId);
          }}
          style={({ pressed }) => [styles.authorRow, pressed && styles.pressed]}
        >
          <Avatar
            imageUrl={post.author.profileImageUrl}
            name={post.author.nickname}
            size={46}
            textSize={16}
          />
          <View style={styles.authorInfo}>
            <Text style={styles.authorName}>{post.author.nickname}</Text>
            <Text style={styles.authorMeta}>
              {formatPostDate(post.createdAt)} · 조회 {post.viewCount}
            </Text>
          </View>
        </Pressable>

        {/* 연결된 아파트 카드 — 연결된 경우만. 터치 시 아파트 상세로. */}
        {post.apartment ? (
          <Pressable
            accessibilityRole="button"
            accessibilityLabel={`연결된 아파트 ${post.apartment.name}`}
            onPress={() => {
              if (!post.apartment) {
                return;
              }
              router.push({
                pathname: '/(app)/apartment/[id]',
                params: { id: String(post.apartment.apartmentId) },
              });
            }}
            style={({ pressed }) => [styles.aptCard, pressed && styles.pressed]}
          >
            <View style={styles.aptPin}>
              <Feather color={PRIMARY_COLOR} name="map-pin" size={17} />
            </View>
            <View style={styles.aptInfo}>
              <Text style={styles.aptLabel}>이 기록이 연결된 아파트</Text>
              <Text numberOfLines={1} style={styles.aptName}>
                {post.apartment.name}
              </Text>
            </View>
            <Feather color={MUTED_TEXT_COLOR} name="chevron-right" size={20} />
          </Pressable>
        ) : null}

        {/* 첨부 이미지 — 여러 장이면 가로 스와이프, 탭하면 전체보기. */}
        <PostImageGallery images={imageUrls} labelPrefix={post.title} />

        {/* 본문 텍스트 — 문단(빈 줄) 단위로 넉넉한 간격. */}
        <View style={styles.bodySection}>
          {post.content.split('\n\n').map((paragraph, index) => (
            <Text key={index} style={styles.bodyParagraph}>
              {paragraph}
            </Text>
          ))}
        </View>

        {/* 액션 행 — 본문과 이어진 대화 사이. 좋아요 · 의견 남기기(누르면 하단 입력창). */}
        <View style={styles.inlineActions}>
          <Pressable
            accessibilityLabel={post.likedByMe ? '좋아요 취소' : '좋아요'}
            accessibilityRole="button"
            accessibilityState={{
              busy: liking,
              disabled: liking || !post.permissions.canLike,
              selected: post.likedByMe,
            }}
            disabled={liking || !post.permissions.canLike}
            hitSlop={6}
            onPress={handleLike}
            style={styles.likeButton}
          >
            <Animated.View style={heartAnimStyle}>
              <Ionicons
                color={post.likedByMe ? LIKE_ACTIVE_COLOR : MUTED_TEXT_COLOR}
                name={post.likedByMe ? 'heart' : 'heart-outline'}
                size={23}
              />
            </Animated.View>
            <Text style={[styles.likeCount, post.likedByMe && styles.likeCountActive]}>
              {post.likeCount}
            </Text>
          </Pressable>

          <View style={styles.actionDivider} />

          <Pressable
            accessibilityRole="button"
            accessibilityLabel="의견 남기기"
            disabled={!post.permissions.canComment || !commentsReady}
            onPress={onOpenComposer}
            style={({ pressed }) => [styles.commentCta, pressed && styles.pressed]}
          >
            <Feather color={INK_COLOR} name="message-circle" size={18} />
            <Text style={styles.commentCtaText}>
              {post.permissions.canComment ? '의견 남기기' : '댓글을 남길 수 없어요'}
            </Text>
          </Pressable>
        </View>

        {/* 댓글(이어진 대화) 섹션. */}
        <View style={styles.commentSection}>
          <View style={styles.commentHeader}>
            <Text style={styles.commentTitle}>
              이어진 대화 <Text style={styles.commentCountText}>{commentCount}</Text>
            </Text>
          </View>

          {commentsQuery.isPending ? (
            <View accessibilityLiveRegion="polite" style={styles.commentState}>
              <ActivityIndicator color={PRIMARY_COLOR} size="small" />
              <Text style={styles.commentEmptyText}>댓글을 불러오고 있어요.</Text>
            </View>
          ) : initialCommentsUnavailable ? (
            <View accessibilityLiveRegion="polite" style={styles.commentState}>
              <Text style={styles.commentEmptyText}>
                {commentsQuery.error instanceof Error
                  ? commentsQuery.error.message
                  : '댓글을 불러오지 못했습니다.'}
              </Text>
              <Pressable
                accessibilityRole="button"
                onPress={() => void commentsQuery.refetch()}
                style={({ pressed }) => [styles.commentRetryButton, pressed && styles.pressed]}
              >
                <Text style={styles.commentRetryButtonText}>다시 시도</Text>
              </Pressable>
            </View>
          ) : comments.length === 0 ? (
            <View style={styles.commentEmpty}>
              <Text style={styles.commentEmptyText}>아직 이어진 대화가 없어요.</Text>
            </View>
          ) : (
            comments.map((comment, index) => (
              <CommentRow
                comment={comment}
                deleting={deletingCommentId === comment.commentId}
                disabled={submittingComment || deletingCommentId !== undefined}
                isLast={index === comments.length - 1}
                key={comment.commentId}
                onDelete={() => onDeleteComment(comment)}
                onEdit={() => onEditComment(comment)}
                onOpenProfile={openMemberProfile}
              />
            ))
          )}

          {commentsQuery.isFetchNextPageError ? (
            <View accessibilityLiveRegion="polite" style={styles.commentPageError}>
              <Text style={styles.commentEmptyText}>다음 댓글을 불러오지 못했습니다.</Text>
              <Pressable
                accessibilityRole="button"
                onPress={() =>
                  void commentsQuery.fetchNextPage({
                    cancelRefetch: false,
                  })
                }
                style={({ pressed }) => [styles.commentRetryButton, pressed && styles.pressed]}
              >
                <Text style={styles.commentRetryButtonText}>다시 시도</Text>
              </Pressable>
            </View>
          ) : commentsQuery.isFetchingNextPage ? (
            <View accessibilityLiveRegion="polite" style={styles.commentPageLoading}>
              <ActivityIndicator color={PRIMARY_COLOR} size="small" />
            </View>
          ) : null}
        </View>
      </ScrollView>

      {/* 하단 고정 — 평소엔 앱 navbar, 의견 남기기 누르면 댓글 입력창(키보드 위, navbar는 숨김). */}
      {showComposer ? (
        <Animated.View style={[styles.composerBar, bottomBarPadStyle]}>
          {editingComment !== null && (
            <View accessibilityLiveRegion="polite" style={styles.editingCommentBar}>
              <Text numberOfLines={1} style={styles.editingCommentText}>
                댓글 수정 중
              </Text>
              <Pressable
                accessibilityLabel="댓글 수정 취소"
                accessibilityRole="button"
                disabled={submittingComment}
                hitSlop={8}
                onPress={onCancelCommentEdit}
                style={({ pressed }) => [styles.editingCommentCancel, pressed && styles.pressed]}
              >
                <Text style={styles.editingCommentCancelText}>취소</Text>
              </Pressable>
            </View>
          )}
          <View style={styles.composerInputRow}>
            <TextInput
              accessibilityLabel={editingComment === null ? '댓글 입력' : '댓글 수정 입력'}
              autoFocus
              editable={post.permissions.canComment && commentsReady && !submittingComment}
              multiline
              onBlur={onBlurComposer}
              onFocus={onFocusComposer}
              onChangeText={onChangeComment}
              placeholder={
                editingComment === null ? '따뜻한 의견을 남겨보세요' : '댓글을 수정해보세요'
              }
              placeholderTextColor={MUTED_TEXT_COLOR}
              ref={inputRef}
              style={styles.composerInput}
              value={commentText}
            />
            <Pressable
              accessibilityLabel={editingComment === null ? '댓글 등록' : '댓글 수정 완료'}
              accessibilityRole="button"
              accessibilityState={{
                busy: submittingComment,
                disabled: !canSubmitComment,
              }}
              disabled={!canSubmitComment}
              onPress={() => onSubmitComment(post)}
              style={({ pressed }) => [
                styles.composerSubmit,
                !canSubmitComment && styles.composerSubmitDisabled,
                pressed && canSubmitComment && styles.pressed,
              ]}
            >
              {submittingComment ? (
                <ActivityIndicator color="#FFFFFF" size="small" />
              ) : (
                <Text style={styles.composerSubmitText}>
                  {editingComment === null ? '등록' : '완료'}
                </Text>
              )}
            </Pressable>
          </View>
        </Animated.View>
      ) : (
        <FloatingTabBar activeTab="community" />
      )}
    </>
  );
}

function CommentRow({
  comment,
  deleting,
  disabled,
  isLast,
  onDelete,
  onEdit,
  onOpenProfile,
}: {
  comment: CommunityComment;
  deleting: boolean;
  disabled: boolean;
  isLast: boolean;
  onDelete: () => void;
  onEdit: () => void;
  onOpenProfile: (memberId: number) => void;
}) {
  const showActions = comment.isMine && (comment.canEdit || comment.canDelete);

  return (
    <View style={styles.commentRow}>
      <View style={styles.commentAvatarCol}>
        <Pressable
          accessibilityLabel={
            comment.author.memberId === null
              ? undefined
              : `${comment.author.nickname} 공개 프로필 열기`
          }
          accessibilityRole={comment.author.memberId === null ? undefined : 'button'}
          disabled={comment.author.memberId === null}
          onPress={() => {
            if (comment.author.memberId !== null) onOpenProfile(comment.author.memberId);
          }}
          style={({ pressed }) => pressed && styles.pressed}
        >
          <Avatar
            imageUrl={comment.author.profileImageUrl}
            name={comment.author.nickname}
            size={36}
            textSize={13}
          />
        </Pressable>
        {isLast ? null : <View style={styles.commentLine} />}
      </View>
      <View style={styles.commentBody}>
        <View style={styles.commentBodyHeader}>
          <View style={styles.commentNameRow}>
            <Pressable
              accessibilityLabel={
                comment.author.memberId === null
                  ? undefined
                  : `${comment.author.nickname} 공개 프로필 열기`
              }
              accessibilityRole={comment.author.memberId === null ? undefined : 'button'}
              disabled={comment.author.memberId === null}
              hitSlop={4}
              onPress={() => {
                if (comment.author.memberId !== null) onOpenProfile(comment.author.memberId);
              }}
              style={({ pressed }) => pressed && styles.pressed}
            >
              <Text style={styles.commentNickname}>{comment.author.nickname}</Text>
            </Pressable>
            {comment.isPostAuthor && (
              <View style={styles.commentAuthorBadge}>
                <Text style={styles.commentAuthorBadgeText}>작성자</Text>
              </View>
            )}
          </View>
          {showActions && (
            <View style={styles.commentActions}>
              {comment.canEdit && (
                <Pressable
                  accessibilityLabel={`${comment.author.nickname} 댓글 수정`}
                  accessibilityRole="button"
                  disabled={disabled}
                  hitSlop={6}
                  onPress={onEdit}
                  style={({ pressed }) => pressed && styles.pressed}
                >
                  <Text style={styles.commentEditText}>수정</Text>
                </Pressable>
              )}
              {comment.canDelete && (
                <Pressable
                  accessibilityLabel={`${comment.author.nickname} 댓글 삭제`}
                  accessibilityRole="button"
                  accessibilityState={{ busy: deleting, disabled }}
                  disabled={disabled}
                  hitSlop={6}
                  onPress={onDelete}
                  style={({ pressed }) => pressed && styles.pressed}
                >
                  <Text style={styles.commentDeleteText}>{deleting ? '삭제 중' : '삭제'}</Text>
                </Pressable>
              )}
            </View>
          )}
        </View>
        <Text style={styles.commentContent}>{comment.content}</Text>
        <Text style={styles.commentTime}>{formatCommentTime(comment.createdAt)}</Text>
      </View>
    </View>
  );
}

const styles = StyleSheet.create({
  safeArea: {
    flex: 1,
    backgroundColor: SURFACE_COLOR,
  },
  redirectingScreen: {
    flex: 1,
    alignItems: 'center',
    justifyContent: 'center',
    backgroundColor: SURFACE_COLOR,
  },
  keyboardContainer: {
    flex: 1,
  },
  scroll: {
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
  headerTitle: {
    flex: 1,
    textAlign: 'center',
    color: INK_COLOR,
    fontSize: 16,
    fontFamily: 'IBMPlexSansKR_700Bold',
    letterSpacing: -0.3,
  },
  managementActions: {
    minWidth: 40,
    height: 40,
    flexDirection: 'row',
    alignItems: 'center',
    justifyContent: 'flex-end',
    gap: 6,
  },
  managementButton: {
    minHeight: 32,
    paddingHorizontal: 10,
    borderRadius: 16,
    alignItems: 'center',
    justifyContent: 'center',
    backgroundColor: SOFT_GREEN_COLOR,
  },
  managementButtonText: {
    color: PRIMARY_COLOR,
    fontSize: 12.5,
    fontFamily: 'IBMPlexSansKR_700Bold',
  },
  deleteButtonText: {
    color: '#C74646',
    fontSize: 12.5,
    fontFamily: 'IBMPlexSansKR_700Bold',
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
    color: INK_COLOR,
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
    alignItems: 'center',
    justifyContent: 'center',
    flexShrink: 0,
  },
  avatarText: {
    color: '#FFFFFF',
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
    color: INK_COLOR,
    fontSize: 12,
    fontFamily: 'IBMPlexSansKR_700Bold',
    letterSpacing: -0.2,
  },
  bodySection: {
    marginTop: 24,
    gap: 16,
  },
  bodyParagraph: {
    color: BODY_TEXT_COLOR,
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
    color: INK_COLOR,
    fontSize: 18,
    fontFamily: 'IBMPlexSansKR_700Bold',
    letterSpacing: -0.3,
  },
  commentCountText: {
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
    gap: 8,
  },
  commentNameRow: {
    flexShrink: 1,
    flexDirection: 'row',
    alignItems: 'center',
    gap: 7,
  },
  commentNickname: {
    color: TEXT_COLOR,
    fontSize: 14,
    fontFamily: 'IBMPlexSansKR_700Bold',
    letterSpacing: -0.2,
  },
  commentAuthorBadge: {
    paddingHorizontal: 7,
    paddingVertical: 3,
    borderRadius: 8,
    backgroundColor: SOFT_GREEN_COLOR,
  },
  commentAuthorBadgeText: {
    color: PRIMARY_COLOR,
    fontSize: 10,
    fontFamily: 'IBMPlexSansKR_700Bold',
  },
  commentActions: {
    flexDirection: 'row',
    alignItems: 'center',
    gap: 12,
  },
  commentEditText: {
    color: MUTED_TEXT_COLOR,
    fontSize: 12.5,
    fontFamily: 'IBMPlexSansKR_600SemiBold',
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
    color: BODY_TEXT_COLOR,
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
  commentEmpty: {
    minHeight: 88,
    alignItems: 'center',
    justifyContent: 'center',
  },
  commentState: {
    minHeight: 88,
    alignItems: 'center',
    justifyContent: 'center',
    gap: 10,
  },
  commentEmptyText: {
    color: MUTED_TEXT_COLOR,
    fontSize: 13,
    fontFamily: 'IBMPlexSansKR_400Regular',
  },
  commentRetryButton: {
    minHeight: 36,
    paddingHorizontal: 16,
    borderRadius: 18,
    alignItems: 'center',
    justifyContent: 'center',
    backgroundColor: SOFT_GREEN_COLOR,
  },
  commentRetryButtonText: {
    color: PRIMARY_COLOR,
    fontSize: 12.5,
    fontFamily: 'IBMPlexSansKR_700Bold',
  },
  commentPageLoading: {
    minHeight: 52,
    alignItems: 'center',
    justifyContent: 'center',
  },
  commentPageError: {
    minHeight: 72,
    alignItems: 'center',
    justifyContent: 'center',
    gap: 8,
  },
  detailState: {
    flex: 1,
    paddingHorizontal: 22,
    alignItems: 'center',
    justifyContent: 'center',
  },
  detailStateTitle: {
    marginTop: 12,
    color: INK_COLOR,
    fontSize: 15,
    fontFamily: 'IBMPlexSansKR_700Bold',
  },
  detailStateBody: {
    marginTop: 6,
    textAlign: 'center',
    color: MUTED_TEXT_COLOR,
    fontSize: 12.5,
    fontFamily: 'IBMPlexSansKR_400Regular',
  },
  retryButton: {
    minHeight: 44,
    marginTop: 16,
    paddingHorizontal: 20,
    borderRadius: 22,
    alignItems: 'center',
    justifyContent: 'center',
    backgroundColor: SOFT_GREEN_COLOR,
  },
  retryButtonText: {
    color: PRIMARY_COLOR,
    fontSize: 13,
    fontFamily: 'IBMPlexSansKR_700Bold',
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
    color: LIKE_ACTIVE_COLOR,
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
    color: INK_COLOR,
    fontSize: 15,
    fontFamily: 'IBMPlexSansKR_700Bold',
    letterSpacing: -0.2,
  },
  composerBar: {
    paddingTop: 12,
    paddingHorizontal: 16,
    borderTopWidth: StyleSheet.hairlineWidth,
    borderTopColor: DIVIDER_COLOR,
    backgroundColor: SURFACE_COLOR,
    gap: 8,
  },
  editingCommentBar: {
    minHeight: 26,
    flexDirection: 'row',
    alignItems: 'center',
    justifyContent: 'space-between',
    paddingHorizontal: 4,
  },
  editingCommentText: {
    flex: 1,
    color: PRIMARY_COLOR,
    fontSize: 12,
    fontFamily: 'IBMPlexSansKR_700Bold',
  },
  editingCommentCancel: {
    minWidth: 40,
    minHeight: 26,
    alignItems: 'flex-end',
    justifyContent: 'center',
  },
  editingCommentCancelText: {
    color: MUTED_TEXT_COLOR,
    fontSize: 12,
    fontFamily: 'IBMPlexSansKR_600SemiBold',
  },
  composerInputRow: {
    flexDirection: 'row',
    alignItems: 'flex-end',
    gap: 10,
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
  pressed: {
    opacity: 0.5,
  },
});
