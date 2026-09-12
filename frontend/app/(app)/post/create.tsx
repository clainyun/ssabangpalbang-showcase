import { Feather } from '@expo/vector-icons';
import * as Crypto from 'expo-crypto';
import { Image } from 'expo-image';
import * as ImageManipulator from 'expo-image-manipulator';
import * as ImagePicker from 'expo-image-picker';
import { useFocusEffect, useLocalSearchParams, useRouter } from 'expo-router';
import { useCallback, useEffect, useMemo, useRef, useState } from 'react';
import {
  ActivityIndicator,
  Keyboard,
  Pressable,
  ScrollView,
  StyleSheet,
  Text,
  TextInput,
  View,
} from 'react-native';
import { Gesture, GestureDetector } from 'react-native-gesture-handler';
import Animated, {
  useAnimatedKeyboard,
  useAnimatedStyle,
} from 'react-native-reanimated';
import { SafeAreaView, useSafeAreaInsets } from 'react-native-safe-area-context';

import { appAlert } from '@/components/AppDialog';
import {
  DIVIDER_COLOR,
  MUTED_TEXT_COLOR,
  PRIMARY_COLOR,
  SURFACE_COLOR,
} from '@/constants/colors';
import { useApartmentRegionSearch } from '@/features/apartment/api/useApartmentSearch';
import type { CommunityBoardType } from '@/features/community/api/types';
import { useCommunityPost } from '@/features/community/api/useCommunityPost';
import {
  useCreateCommunityPost,
  useUpdateCommunityPost,
} from '@/features/community/api/useCommunityPostMutations';
import { uploadMedia } from '@/features/media/api/mediaUpload';
import {
  AuthSessionChangedError,
  AuthSessionExpiredError,
} from '@/lib/authenticatedFetch';
import { useAuthStore } from '@/store/authStore';

// 커뮤니티 목록/상세와 동일한 디자인 언어(잉크 그린/소프트 입력창).
const INK_COLOR = '#17211C';
const BODY_TEXT_COLOR = '#2C3630';
const SOFT_INPUT_BG = '#F1F3F1';
const PILL_IDLE_TEXT = '#4A574F';

const MAX_PHOTOS = 10;

const CATEGORIES = [
  { label: '자유게시판', boardType: 'FREE' },
  { label: '정보게시판', boardType: 'INFORMATION' },
] as const satisfies readonly {
  label: string;
  boardType: CommunityBoardType;
}[];
const INVISIBLE_TEXT_PATTERN = /[\s\p{Cf}]/gu;
const SWIPE_DISTANCE_THRESHOLD = 80;
const SWIPE_VELOCITY_THRESHOLD = 700;
const SWIPE_FAST_DISTANCE_THRESHOLD = 36;
const SWIPE_DIRECTION_RATIO = 1.2;

type Category = (typeof CATEGORIES)[number]['label'];

// 사진 타일은 선택 즉시 업로드합니다(현장기록 RecordComposerView와 동일한 패턴).
// 업로드 중인 타일은 fileId가 아직 없으므로 임시 key만, 완료되면 서버 fileId를 갖습니다.
type PhotoItem =
  | { key: string; uri: string; status: 'uploading' }
  | { key: string; uri: string; status: 'ready'; fileId: number };

function hasVisibleText(value: string) {
  return value.replace(INVISIBLE_TEXT_PATTERN, '').length > 0;
}

function categoryForBoard(boardType: CommunityBoardType): Category {
  return boardType === 'INFORMATION' ? '정보게시판' : '자유게시판';
}

export default function CreatePostScreen() {
  const { postId: postIdParam } = useLocalSearchParams<{
    postId?: string | string[];
  }>();
  const rawPostId = Array.isArray(postIdParam) ? postIdParam[0] : postIdParam;
  const parsedPostId =
    rawPostId && /^\d+$/.test(rawPostId) ? Number(rawPostId) : undefined;
  const editPostId =
    parsedPostId !== undefined &&
    Number.isSafeInteger(parsedPostId) &&
    parsedPostId > 0
      ? parsedPostId
      : undefined;
  const isEditRequested = rawPostId !== undefined;
  const router = useRouter();
  const insets = useSafeAreaInsets();
  const bodyInputRef = useRef<TextInput>(null);
  const isNavigatingRef = useRef(false);
  const isSubmittingRef = useRef(false);
  const initializedPostIdRef = useRef<number | undefined>(undefined);
  // 수정 진입 시점의 기존 첨부 fileId 목록 — 사진을 하나도 안 건드렸으면 fileIds를
  // 아예 보내지 않아(백엔드는 fileIds 미포함 시 기존 첨부를 그대로 유지) 불필요한
  // 삭제·재생성을 피합니다.
  const initialFileIdsRef = useRef<number[]>([]);
  const postQuery = useCommunityPost(
    isEditRequested ? editPostId : undefined,
  );
  const createMutation = useCreateCommunityPost();
  const updateMutation = useUpdateCommunityPost(editPostId);

  const accessToken = useAuthStore((s) => s.accessToken);
  const sessionVersion = useAuthStore((s) => s.sessionVersion);

  const [category, setCategory] = useState<Category | null>(null);
  const [title, setTitle] = useState('');
  const [body, setBody] = useState('');
  const [photos, setPhotos] = useState<PhotoItem[]>([]);
  const [shouldGoBack, setShouldGoBack] = useState(false);
  // 정보게시판 글에만 붙이는 연결 아파트(선택). 자유게시판으로 바꾸면 초기화한다.
  const [selectedApartment, setSelectedApartment] = useState<{
    apartmentId: number;
    name: string;
  } | null>(null);
  const [apartmentQuery, setApartmentQuery] = useState('');

  const isInformationBoard = category === '정보게시판';
  const apartmentSearch = useApartmentRegionSearch({ keyword: apartmentQuery });
  const apartmentResults = apartmentSearch.data?.content ?? [];

  // edge-to-edge + New Arch에선 adjustResize가 창을 줄여주지 않아 키보드가 하단 바를 덮음.
  // reanimated useAnimatedKeyboard로 키보드 높이를 UI 스레드에서 구독 → 화면 전체를 그만큼 위로
  // 밀어 하단 등록 버튼이 키보드 위에 붙고 ScrollView 영역이 줄어 입력창까지 스크롤된다.
  // (커뮤니티 상세 PostDetailRedesign.tsx와 동일 패턴)
  const keyboard = useAnimatedKeyboard();
  // 키보드 높이는 '입력창이 실제로 포커스일 때'만 반영한다.
  // 사진 첨부 시 launchImageLibraryAsync가 별도 네이티브 액티비티로 전환되면 안드로이드에서
  // close inset 애니메이션 콜백이 유실돼 keyboard.state/height가 OPEN·CLOSING에 stuck될 수
  // 있다(state 기반 게이팅만으로는 못 막아 하단 등록 바가 화면 중앙으로 튀던 원인). 포커스는
  // JS에서 우리가 직접 제어하므로, 피커 진입 시 포커스를 내려 두면 stuck된 keyboard 값이
  // 화면을 밀지 못한다. 포커스는 onFocus/onBlur와 pickPhotos에서 갱신한다.
  const [isInputFocused, setIsInputFocused] = useState(false);
  const keyboardPushStyle = useAnimatedStyle(() => ({
    paddingBottom: isInputFocused ? keyboard.height.value : 0,
  }));
  const bottomBarPadStyle = useAnimatedStyle(() => ({
    paddingBottom:
      isInputFocused && keyboard.height.value > 0 ? 12 : Math.max(insets.bottom, 12),
  }));

  const isSubmitting = createMutation.isPending || updateMutation.isPending;
  const editPost = isEditRequested ? postQuery.data : undefined;
  const canRenderEditor =
    !isEditRequested ||
    (editPost !== undefined && editPost.permissions.canEdit);
  const isUploadingPhoto = photos.some((photo) => photo.status === 'uploading');
  const canSubmit = Boolean(
    canRenderEditor &&
      category &&
      hasVisibleText(title) &&
      hasVisibleText(body) &&
      !isUploadingPhoto &&
      !isSubmitting,
  );

  useEffect(() => {
    if (
      editPostId === undefined ||
      editPost === undefined ||
      !editPost.permissions.canEdit ||
      initializedPostIdRef.current === editPostId
    ) {
      return;
    }

    initializedPostIdRef.current = editPostId;
    setCategory(categoryForBoard(editPost.boardType));
    setTitle(editPost.title);
    setBody(editPost.content);
    setSelectedApartment(
      editPost.apartment
        ? { apartmentId: editPost.apartment.apartmentId, name: editPost.apartment.name }
        : null,
    );

    // 기존 이미지 첨부만(접근 가능·이미지 타입) 편집 가능한 사진 타일로 복원합니다.
    const existing = editPost.attachments.flatMap<{ fileId: number; uri: string }>(
      (attachment) =>
        attachment.available &&
        attachment.fileUrl !== null &&
        attachment.contentType?.startsWith('image/') === true
          ? [{ fileId: attachment.fileId, uri: attachment.fileUrl }]
          : [],
    );
    setPhotos(
      existing.map((item) => ({
        key: `existing-${item.fileId}`,
        uri: item.uri,
        status: 'ready',
        fileId: item.fileId,
      })),
    );
    initialFileIdsRef.current = existing.map((item) => item.fileId);
  }, [editPost, editPostId]);

  const uploadPhoto = useCallback(
    async (key: string, asset: ImagePicker.ImagePickerAsset) => {
      if (!accessToken) {
        setPhotos((current) => current.filter((photo) => photo.key !== key));
        appAlert('오류', '로그인 상태를 확인해 주세요.');
        return;
      }

      try {
        // 업로드 전에 항상 JPEG로 정규화 + 축소합니다.
        //  - 갤럭시 등에서 찍은 HEIC/HEIF처럼 허용 밖 포맷도 image/jpeg로 변환돼
        //    서버 허용 목록(jpeg/png/webp/pdf) 검증을 통과합니다.
        //  - 고해상도 원본(수 MB)을 가로 1600px·품질 0.7로 줄여 LTE 업로드를 빠르게 합니다.
        const shouldResize =
          typeof asset.width === 'number' && asset.width > 1600;
        const processed = await ImageManipulator.manipulateAsync(
          asset.uri,
          shouldResize ? [{ resize: { width: 1600 } }] : [],
          { compress: 0.7, format: ImageManipulator.SaveFormat.JPEG },
        );
        const contentType = 'image/jpeg';
        // putMediaFile이 실제로 PUT하는 바이트와 동일한 값을 발급 요청 sizeBytes로
        // 보내야 게이트웨이 verify의 크기 검증(MEDIA_SIZE_MISMATCH)을 통과합니다.
        // 변환 결과(processed.uri)의 blob 크기를 그대로 사용합니다.
        const sizeBytes = (await (await fetch(processed.uri)).blob()).size;

        const uploaded = await uploadMedia(
          accessToken,
          {
            fileUsage: 'POST_ATTACHMENT',
            contentType,
            sizeBytes,
            localUri: processed.uri,
            originalName: asset.fileName ?? undefined,
          },
          sessionVersion,
        );

        setPhotos((current) =>
          current.map((photo) =>
            photo.key === key
              ? { key, uri: photo.uri, status: 'ready', fileId: uploaded.fileId }
              : photo,
          ),
        );
      } catch (error) {
        setPhotos((current) => current.filter((photo) => photo.key !== key));
        appAlert(
          '사진 업로드 실패',
          error instanceof Error
            ? error.message
            : '사진을 업로드하지 못했습니다. 잠시 후 다시 시도해주세요.',
        );
      }
    },
    [accessToken, sessionVersion],
  );

  const pickPhotos = useCallback(async () => {
    // 피커(별도 액티비티)로 넘어가기 전에 키보드를 먼저 닫고 포커스 게이트를 내린다.
    // 전환 중 close 애니메이션이 유실돼 keyboard.state/height가 stuck되더라도, 포커스가
    // false면 화면을 밀지 않는다.
    Keyboard.dismiss();
    setIsInputFocused(false);
    const remaining = MAX_PHOTOS - photos.length;
    if (remaining <= 0) {
      appAlert('사진은 최대 10장까지', '먼저 첨부한 사진을 삭제한 뒤 다시 시도해주세요.');
      return;
    }
    if (!accessToken) {
      appAlert('오류', '로그인 상태를 확인해 주세요.');
      return;
    }

    const result = await ImagePicker.launchImageLibraryAsync({
      mediaTypes: ['images'],
      quality: 1,
      allowsMultipleSelection: true,
      selectionLimit: remaining,
    });
    if (result.canceled) {
      return;
    }

    for (const asset of result.assets) {
      const key = Crypto.randomUUID();
      setPhotos((current) => [...current, { key, uri: asset.uri, status: 'uploading' }]);
      void uploadPhoto(key, asset);
    }
  }, [accessToken, photos.length, uploadPhoto]);

  const removePhoto = useCallback((key: string) => {
    setPhotos((current) => current.filter((photo) => photo.key !== key));
  }, []);

  const goBack = useCallback(() => {
    if (isNavigatingRef.current) {
      return;
    }

    isNavigatingRef.current = true;

    if (router.canGoBack()) {
      router.back();
      return;
    }

    router.replace('/(app)/(tabs)/community');
  }, [router]);

  useFocusEffect(
    useCallback(() => {
      isNavigatingRef.current = false;
      setShouldGoBack(false);
    }, []),
  );

  useEffect(() => {
    if (!shouldGoBack) {
      return;
    }

    goBack();
  }, [goBack, shouldGoBack]);

  const communitySwipeGesture = useMemo(
    () =>
      Gesture.Pan()
        .activeOffsetX(22)
        .failOffsetX(-20)
        .failOffsetY([-18, 18])
        .runOnJS(true)
        .onEnd(({ translationX, translationY, velocityX }) => {
          const isClearlyHorizontal =
            Math.abs(translationX) > Math.abs(translationY) * SWIPE_DIRECTION_RATIO;
          const meetsDistance = translationX >= SWIPE_DISTANCE_THRESHOLD;
          const meetsVelocity =
            translationX >= SWIPE_FAST_DISTANCE_THRESHOLD && velocityX >= SWIPE_VELOCITY_THRESHOLD;

          if (isClearlyHorizontal && (meetsDistance || meetsVelocity)) {
            setShouldGoBack(true);
          }
        }),
    [],
  );

  const photoScrollGesture = useMemo(
    () => Gesture.Native().blocksExternalGesture(communitySwipeGesture),
    [communitySwipeGesture],
  );

  const submitPost = async () => {
    if (!canSubmit || isSubmittingRef.current) {
      return;
    }

    isSubmittingRef.current = true;

    const selectedCategory = CATEGORIES.find(
      (item) => item.label === category,
    );
    if (!selectedCategory) {
      isSubmittingRef.current = false;
      return;
    }

    const readyFileIds = photos.flatMap((photo) =>
      photo.status === 'ready' ? [photo.fileId] : [],
    );

    try {
      let result;
      if (isEditRequested && editPostId !== undefined) {
        // 사진을 하나도 안 바꿨으면 fileIds를 생략해 기존 첨부를 그대로 유지시킵니다.
        const initial = initialFileIdsRef.current;
        const unchanged =
          readyFileIds.length === initial.length &&
          readyFileIds.every((id, index) => id === initial[index]);
        result = await updateMutation.mutateAsync({
          boardType: selectedCategory.boardType,
          title: title.trim(),
          content: body.trim(),
          apartmentId: isInformationBoard
            ? (selectedApartment?.apartmentId ?? null)
            : null,
          ...(unchanged ? {} : { fileIds: readyFileIds }),
        });
      } else {
        result = await createMutation.mutateAsync({
          boardType: selectedCategory.boardType,
          title: title.trim(),
          content: body.trim(),
          ...(isInformationBoard && selectedApartment
            ? { apartmentId: selectedApartment.apartmentId }
            : {}),
          ...(readyFileIds.length > 0 ? { fileIds: readyFileIds } : {}),
        });
      }

      if (isNavigatingRef.current) {
        return;
      }
      isNavigatingRef.current = true;
      if (isEditRequested && router.canGoBack()) {
        router.back();
        return;
      }
      router.replace({
        pathname: '/(app)/post/[id]',
        params: { id: String(result.postId) },
      });
    } catch (error) {
      if (
        error instanceof AuthSessionExpiredError ||
        error instanceof AuthSessionChangedError
      ) {
        return;
      }

      appAlert(
        isEditRequested
          ? '게시글을 수정하지 못했어요'
          : '게시글을 등록하지 못했어요',
        error instanceof Error
          ? error.message
          : '잠시 후 다시 시도해주세요.',
      );
    } finally {
      isSubmittingRef.current = false;
    }
  };

  return (
    <GestureDetector gesture={communitySwipeGesture}>
      <SafeAreaView edges={['top']} style={styles.safeArea}>
        <Animated.View style={[styles.keyboardContainer, keyboardPushStyle]}>
          {/* 헤더 — 뒤로가기 · 게시판명 + 하단 구분선. (커뮤니티 상세와 동일) */}
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
            <Text style={styles.headerTitle}>
              {isEditRequested ? '게시글 수정' : '게시글 작성'}
            </Text>
            <View style={styles.headerSide} />
          </View>

          {isEditRequested && editPostId === undefined ? (
            <EditorState
              body="게시글 번호가 올바르지 않습니다."
              onBack={goBack}
              title="게시글을 수정할 수 없어요"
            />
          ) : isEditRequested && postQuery.isPending ? (
            <EditorState
              body="게시글을 불러오고 있어요."
              loading
              onBack={goBack}
              title="잠시만 기다려주세요"
            />
          ) : isEditRequested && (postQuery.isError || editPost === undefined) ? (
            <EditorState
              body={
                postQuery.error instanceof Error
                  ? postQuery.error.message
                  : '잠시 후 다시 시도해주세요.'
              }
              onBack={goBack}
              onRetry={() => void postQuery.refetch()}
              title="게시글을 불러오지 못했어요"
            />
          ) : isEditRequested && !editPost?.permissions.canEdit ? (
            <EditorState
              body="작성자만 이 게시글을 수정할 수 있습니다."
              onBack={goBack}
              title="수정 권한이 없어요"
            />
          ) : (
            <>
              <ScrollView
                style={styles.scroll}
                contentContainerStyle={styles.content}
                keyboardShouldPersistTaps="handled"
                showsVerticalScrollIndicator={false}
              >
                {/* 커뮤니티 목록의 대형 디스플레이 헤딩과 같은 결(Noto 900). */}
                <Text style={styles.displayHeading}>
                  {isEditRequested ? '이야기를\n다듬어 볼까요?' : '어떤 이야기를\n남겨볼까요?'}
                </Text>

                <View style={styles.section}>
                  <Text style={styles.label}>카테고리</Text>
                  <View style={styles.categoryList}>
                    {CATEGORIES.map((item) => {
                      const isSelected = category === item.label;

                      return (
                        <Pressable
                          accessibilityRole="button"
                          accessibilityState={{ selected: isSelected }}
                          key={item.label}
                          onPress={() => {
                            setCategory(item.label);
                            if (item.label !== '정보게시판') {
                              setSelectedApartment(null);
                              setApartmentQuery('');
                            }
                          }}
                          style={({ pressed }) => [
                            styles.categoryChip,
                            isSelected && styles.categoryChipSelected,
                            pressed && styles.pressed,
                          ]}
                        >
                          <Text
                            style={[
                              styles.categoryChipText,
                              isSelected && styles.categoryChipTextSelected,
                            ]}
                          >
                            {item.label}
                          </Text>
                        </Pressable>
                      );
                    })}
                  </View>
                </View>

                {isInformationBoard ? (
                  <View style={styles.section}>
                    <Text style={styles.label}>어떤 단지 이야기예요? (선택)</Text>
                    {selectedApartment ? (
                      <View style={styles.aptSelected}>
                        <Feather color={PRIMARY_COLOR} name="map-pin" size={16} />
                        <Text numberOfLines={1} style={styles.aptSelectedName}>
                          {selectedApartment.name}
                        </Text>
                        <Pressable
                          accessibilityLabel="아파트 선택 해제"
                          accessibilityRole="button"
                          hitSlop={8}
                          onPress={() => setSelectedApartment(null)}
                        >
                          <Feather color={MUTED_TEXT_COLOR} name="x" size={18} />
                        </Pressable>
                      </View>
                    ) : (
                      <>
                        <TextInput
                          accessibilityLabel="아파트 검색"
                          onBlur={() => {
                            setIsInputFocused(false);
                          }}
                          onChangeText={setApartmentQuery}
                          onFocus={() => {
                            setIsInputFocused(true);
                          }}
                          placeholder="아파트 이름으로 검색"
                          placeholderTextColor={MUTED_TEXT_COLOR}
                          returnKeyType="search"
                          style={styles.titleInput}
                          value={apartmentQuery}
                        />
                        {apartmentQuery.trim().length > 0 ? (
                          <View style={styles.aptResults}>
                            {apartmentSearch.isFetching && apartmentResults.length === 0 ? (
                              <Text style={styles.aptHint}>검색 중…</Text>
                            ) : apartmentResults.length === 0 ? (
                              <Text style={styles.aptHint}>검색 결과가 없어요.</Text>
                            ) : (
                              apartmentResults.slice(0, 8).map((apt) => (
                                <Pressable
                                  accessibilityRole="button"
                                  key={apt.apartmentId}
                                  onPress={() => {
                                    setSelectedApartment({
                                      apartmentId: apt.apartmentId,
                                      name: apt.name,
                                    });
                                    setApartmentQuery('');
                                  }}
                                  style={({ pressed }) => [
                                    styles.aptResultRow,
                                    pressed && styles.pressed,
                                  ]}
                                >
                                  <Text numberOfLines={1} style={styles.aptResultName}>
                                    {apt.name}
                                  </Text>
                                  <Text numberOfLines={1} style={styles.aptResultAddr}>
                                    {apt.address}
                                  </Text>
                                </Pressable>
                              ))
                            )}
                          </View>
                        ) : null}
                      </>
                    )}
                  </View>
                ) : null}

                <View style={styles.section}>
                  <View style={styles.labelRow}>
                    <Text style={styles.label}>제목</Text>
                    <Text style={styles.counter}>{title.length}/200</Text>
                  </View>
                  <TextInput
                    accessibilityLabel="게시글 제목"
                    autoCapitalize="sentences"
                    inputMode="text"
                    keyboardType="default"
                    maxLength={200}
                    onBlur={() => {
                      setIsInputFocused(false);
                    }}
                    onChangeText={setTitle}
                    onFocus={() => {
                      setIsInputFocused(true);
                    }}
                    onSubmitEditing={() => bodyInputRef.current?.focus()}
                    placeholder="제목을 입력해주세요"
                    placeholderTextColor={MUTED_TEXT_COLOR}
                    returnKeyType="next"
                    style={styles.titleInput}
                    value={title}
                  />
                </View>

                <View style={styles.section}>
                  <View style={styles.labelRow}>
                    <Text style={styles.label}>내용</Text>
                    <Text style={styles.counter}>
                      {body.length.toLocaleString()}/5,000
                    </Text>
                  </View>
                  <TextInput
                    accessibilityLabel="게시글 내용"
                    autoCapitalize="sentences"
                    inputMode="text"
                    keyboardType="default"
                    maxLength={5000}
                    multiline
                    onBlur={() => {
                      setIsInputFocused(false);
                    }}
                    onChangeText={setBody}
                    onFocus={() => {
                      setIsInputFocused(true);
                    }}
                    placeholder="임장 경험이나 유용한 정보를 자유롭게 나눠보세요"
                    placeholderTextColor={MUTED_TEXT_COLOR}
                    ref={bodyInputRef}
                    style={styles.bodyInput}
                    textAlignVertical="top"
                    value={body}
                  />
                </View>

                <View style={styles.section}>
                  <View style={styles.labelRow}>
                    <Text style={styles.label}>사진</Text>
                    <Text style={styles.counter}>{photos.length}/10</Text>
                  </View>
                  <GestureDetector gesture={photoScrollGesture}>
                    <ScrollView
                      contentContainerStyle={styles.photoList}
                      horizontal
                      nestedScrollEnabled
                      showsHorizontalScrollIndicator={false}
                    >
                      {photos.length < MAX_PHOTOS && (
                        <Pressable
                          accessibilityLabel="사진 추가"
                          accessibilityRole="button"
                          onPress={() => void pickPhotos()}
                          style={({ pressed }) => [
                            styles.addPhotoButton,
                            pressed && styles.pressed,
                          ]}
                        >
                          <Feather color={MUTED_TEXT_COLOR} name="camera" size={23} />
                          <Text style={styles.addPhotoText}>사진 추가</Text>
                        </Pressable>
                      )}

                      {photos.map((photo, index) => (
                        <View key={photo.key} style={styles.photoFrame}>
                          <Image
                            accessibilityLabel={`첨부 사진 ${index + 1}`}
                            contentFit="cover"
                            source={{ uri: photo.uri }}
                            style={styles.photo}
                          />
                          {photo.status === 'uploading' ? (
                            <View style={styles.photoOverlay}>
                              <ActivityIndicator color="#FFFFFF" size="small" />
                            </View>
                          ) : (
                            <Pressable
                              accessibilityLabel={`첨부 사진 ${index + 1} 삭제`}
                              accessibilityRole="button"
                              hitSlop={6}
                              onPress={() => removePhoto(photo.key)}
                              style={styles.photoRemove}
                            >
                              <Feather color="#FFFFFF" name="x" size={14} />
                            </Pressable>
                          )}
                        </View>
                      ))}
                    </ScrollView>
                  </GestureDetector>
                  <Text style={styles.photoHelper}>
                    사진은 최대 10장까지 첨부할 수 있어요.
                  </Text>
                </View>
              </ScrollView>

              {/* 하단 고정 등록 바 — 커뮤니티의 다크 라운드 CTA와 동일한 결. */}
              <Animated.View style={[styles.bottomBar, bottomBarPadStyle]}>
                <Pressable
                  accessibilityLabel={
                    isEditRequested ? '게시글 수정 완료' : '게시글 등록'
                  }
                  accessibilityRole="button"
                  accessibilityState={{ disabled: !canSubmit }}
                  disabled={!canSubmit}
                  onPress={() => void submitPost()}
                  style={({ pressed }) => [
                    styles.submitButton,
                    !canSubmit && styles.submitButtonDisabled,
                    pressed && canSubmit && styles.pressed,
                  ]}
                >
                  {isSubmitting ? (
                    <ActivityIndicator color={SURFACE_COLOR} size="small" />
                  ) : (
                    <Text
                      style={[
                        styles.submitButtonText,
                        !canSubmit && styles.submitButtonTextDisabled,
                      ]}
                    >
                      {isEditRequested ? '수정하기' : '등록하기'}
                    </Text>
                  )}
                </Pressable>
              </Animated.View>
            </>
          )}
        </Animated.View>
      </SafeAreaView>
    </GestureDetector>
  );
}

function EditorState({
  body,
  loading = false,
  onBack,
  onRetry,
  title,
}: {
  body: string;
  loading?: boolean;
  onBack: () => void;
  onRetry?: () => void;
  title: string;
}) {
  return (
    <View accessibilityLiveRegion="polite" style={styles.editorState}>
      {loading ? (
        <ActivityIndicator color={PRIMARY_COLOR} size="small" />
      ) : (
        <Feather color={MUTED_TEXT_COLOR} name="alert-circle" size={30} />
      )}
      <Text style={styles.editorStateTitle}>{title}</Text>
      <Text style={styles.editorStateBody}>{body}</Text>
      <View style={styles.editorStateActions}>
        {onRetry && (
          <Pressable
            accessibilityRole="button"
            onPress={onRetry}
            style={({ pressed }) => [
              styles.editorStateButton,
              pressed && styles.pressed,
            ]}
          >
            <Text style={styles.editorStateButtonText}>다시 시도</Text>
          </Pressable>
        )}
        {!loading && (
          <Pressable
            accessibilityRole="button"
            onPress={onBack}
            style={({ pressed }) => [
              styles.editorStateButton,
              pressed && styles.pressed,
            ]}
          >
            <Text style={styles.editorStateButtonText}>돌아가기</Text>
          </Pressable>
        )}
      </View>
    </View>
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
  scroll: {
    flex: 1,
  },
  editorState: {
    flex: 1,
    paddingHorizontal: 22,
    alignItems: 'center',
    justifyContent: 'center',
  },
  editorStateTitle: {
    marginTop: 12,
    color: INK_COLOR,
    fontSize: 15,
    fontFamily: 'IBMPlexSansKR_700Bold',
    letterSpacing: -0.2,
  },
  editorStateBody: {
    marginTop: 6,
    textAlign: 'center',
    color: MUTED_TEXT_COLOR,
    fontSize: 12.5,
    fontFamily: 'IBMPlexSansKR_400Regular',
  },
  editorStateActions: {
    marginTop: 16,
    flexDirection: 'row',
    gap: 8,
  },
  editorStateButton: {
    minHeight: 44,
    paddingHorizontal: 20,
    borderRadius: 22,
    alignItems: 'center',
    justifyContent: 'center',
    backgroundColor: INK_COLOR,
  },
  editorStateButtonText: {
    color: '#FFFFFF',
    fontSize: 13,
    fontFamily: 'IBMPlexSansKR_700Bold',
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
  content: {
    paddingHorizontal: 22,
    paddingTop: 22,
    paddingBottom: 36,
    gap: 28,
  },
  displayHeading: {
    color: INK_COLOR,
    fontSize: 25,
    lineHeight: 33,
    // IBM Plex는 700(Bold)이 최대 → 대형 디스플레이는 Noto Sans KR Black(900). (커뮤니티 헤딩과 동일)
    fontFamily: 'NotoSansKR_900Black',
    letterSpacing: -0.6,
  },
  section: {
    gap: 12,
  },
  labelRow: {
    flexDirection: 'row',
    alignItems: 'center',
    justifyContent: 'space-between',
  },
  label: {
    color: INK_COLOR,
    fontSize: 15,
    fontFamily: 'IBMPlexSansKR_700Bold',
    letterSpacing: -0.3,
  },
  counter: {
    color: MUTED_TEXT_COLOR,
    fontSize: 11.5,
    fontFamily: 'IBMPlexSansKR_600SemiBold',
  },
  categoryList: {
    flexDirection: 'row',
    flexWrap: 'wrap',
    gap: 8,
  },
  // 커뮤니티 카테고리 pill과 동일 — 선택 시 다크 배경 + 흰 글씨.
  categoryChip: {
    height: 44,
    paddingHorizontal: 16,
    borderRadius: 22,
    alignItems: 'center',
    justifyContent: 'center',
    backgroundColor: '#FFFFFF',
    boxShadow: '0px 4px 10px rgba(16, 39, 30, 0.10)',
  },
  categoryChipSelected: {
    backgroundColor: INK_COLOR,
  },
  categoryChipText: {
    color: PILL_IDLE_TEXT,
    fontSize: 14,
    fontFamily: 'IBMPlexSansKR_600SemiBold',
    letterSpacing: -0.3,
  },
  categoryChipTextSelected: {
    color: '#FFFFFF',
  },
  titleInput: {
    minHeight: 52,
    paddingHorizontal: 16,
    paddingVertical: 12,
    borderRadius: 16,
    color: INK_COLOR,
    fontSize: 15,
    fontFamily: 'IBMPlexSansKR_600SemiBold',
    backgroundColor: SOFT_INPUT_BG,
  },
  aptSelected: {
    flexDirection: 'row',
    alignItems: 'center',
    gap: 10,
    minHeight: 52,
    paddingHorizontal: 16,
    paddingVertical: 12,
    borderRadius: 16,
    backgroundColor: SOFT_INPUT_BG,
  },
  aptSelectedName: {
    flex: 1,
    color: INK_COLOR,
    fontSize: 15,
    fontFamily: 'IBMPlexSansKR_700Bold',
    letterSpacing: -0.3,
  },
  aptResults: {
    gap: 6,
  },
  aptResultRow: {
    paddingHorizontal: 16,
    paddingVertical: 12,
    borderRadius: 14,
    backgroundColor: SOFT_INPUT_BG,
    gap: 3,
  },
  aptResultName: {
    color: INK_COLOR,
    fontSize: 15,
    fontFamily: 'IBMPlexSansKR_600SemiBold',
    letterSpacing: -0.2,
  },
  aptResultAddr: {
    color: MUTED_TEXT_COLOR,
    fontSize: 12.5,
    fontFamily: 'IBMPlexSansKR_400Regular',
    letterSpacing: -0.2,
  },
  aptHint: {
    paddingVertical: 8,
    color: MUTED_TEXT_COLOR,
    fontSize: 13.5,
    fontFamily: 'IBMPlexSansKR_400Regular',
  },
  bodyInput: {
    minHeight: 200,
    paddingHorizontal: 16,
    paddingVertical: 15,
    borderRadius: 18,
    color: BODY_TEXT_COLOR,
    fontSize: 15,
    lineHeight: 24,
    fontFamily: 'IBMPlexSansKR_400Regular',
    backgroundColor: SOFT_INPUT_BG,
  },
  photoList: {
    minHeight: 88,
    gap: 10,
  },
  addPhotoButton: {
    width: 88,
    height: 88,
    borderRadius: 16,
    alignItems: 'center',
    justifyContent: 'center',
    gap: 6,
    backgroundColor: SOFT_INPUT_BG,
  },
  addPhotoText: {
    color: MUTED_TEXT_COLOR,
    fontSize: 11.5,
    fontFamily: 'IBMPlexSansKR_600SemiBold',
  },
  photoFrame: {
    width: 88,
    height: 88,
    borderRadius: 16,
    overflow: 'hidden',
    backgroundColor: DIVIDER_COLOR,
  },
  photo: {
    width: '100%',
    height: '100%',
  },
  photoOverlay: {
    position: 'absolute',
    top: 0,
    left: 0,
    right: 0,
    bottom: 0,
    alignItems: 'center',
    justifyContent: 'center',
    backgroundColor: 'rgba(23, 33, 28, 0.42)',
  },
  photoRemove: {
    position: 'absolute',
    top: 4,
    right: 4,
    width: 22,
    height: 22,
    borderRadius: 11,
    alignItems: 'center',
    justifyContent: 'center',
    backgroundColor: 'rgba(23, 33, 28, 0.62)',
  },
  photoHelper: {
    color: MUTED_TEXT_COLOR,
    fontSize: 11.5,
    fontFamily: 'IBMPlexSansKR_400Regular',
  },
  bottomBar: {
    paddingHorizontal: 22,
    paddingTop: 12,
    borderTopWidth: StyleSheet.hairlineWidth,
    borderTopColor: DIVIDER_COLOR,
    backgroundColor: SURFACE_COLOR,
  },
  submitButton: {
    height: 54,
    borderRadius: 27,
    alignItems: 'center',
    justifyContent: 'center',
    backgroundColor: INK_COLOR,
    boxShadow: '0px 8px 20px rgba(16, 39, 30, 0.24)',
  },
  submitButtonDisabled: {
    backgroundColor: '#E7EAE8',
    boxShadow: '0px 0px 0px rgba(0, 0, 0, 0)',
  },
  submitButtonText: {
    color: '#FFFFFF',
    fontSize: 16,
    fontFamily: 'IBMPlexSansKR_700Bold',
    letterSpacing: -0.3,
  },
  submitButtonTextDisabled: {
    color: MUTED_TEXT_COLOR,
  },
  pressed: {
    opacity: 0.66,
  },
});
