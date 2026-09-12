import { Feather } from '@expo/vector-icons';
import { Image } from 'expo-image';
import { useState } from 'react';
import {
  Modal,
  type NativeScrollEvent,
  type NativeSyntheticEvent,
  Pressable,
  ScrollView,
  StyleSheet,
  Text,
  View,
} from 'react-native';
import { useSafeAreaInsets } from 'react-native-safe-area-context';

const INK_COLOR = '#17211C';
const FRAME_BACKGROUND = '#E8EDE9';

interface PostImageGalleryProps {
  /** 표시할 이미지 URL 목록(순서대로). 빈 배열이면 아무것도 렌더하지 않습니다. */
  images: string[];
  /** 접근성 라벨 접두어(보통 글 제목). */
  labelPrefix?: string;
  /** 좌우 패딩을 뚫고 풀 와이드로 보이기 위한 음수 마진 값(부모 가로 패딩과 동일하게). */
  bleed?: number;
}

function pageIndexFromScroll(event: NativeSyntheticEvent<NativeScrollEvent>, width: number) {
  if (width <= 0) {
    return 0;
  }
  return Math.round(event.nativeEvent.contentOffset.x / width);
}

/**
 * 커뮤니티 게시글의 첨부 이미지 갤러리.
 * - 인라인: 가로 스와이프로 여러 장을 모두 넘겨볼 수 있고, contentFit="contain"으로 잘리지 않게 표시.
 * - 탭: 검은 배경 전체보기 모달에서 크게 보고 좌우 스와이프로 넘길 수 있음(탭한 이미지부터 시작).
 */
export function PostImageGallery({ images, labelPrefix, bleed = 22 }: PostImageGalleryProps) {
  const [inlineWidth, setInlineWidth] = useState(0);
  const [inlineIndex, setInlineIndex] = useState(0);
  const [viewerStartIndex, setViewerStartIndex] = useState<number | null>(null);

  if (images.length === 0) {
    return null;
  }

  const total = images.length;
  const prefix = labelPrefix ?? '이미지';

  return (
    <View style={[styles.wrap, { marginHorizontal: -bleed }]}>
      <View
        style={styles.frame}
        onLayout={(event) => setInlineWidth(event.nativeEvent.layout.width)}
      >
        {inlineWidth > 0 && (
          <ScrollView
            horizontal
            pagingEnabled
            showsHorizontalScrollIndicator={false}
            scrollEnabled={total > 1}
            scrollEventThrottle={16}
            onScroll={(event) => {
              const next = pageIndexFromScroll(event, inlineWidth);
              setInlineIndex((current) => (next === current ? current : next));
            }}
          >
            {images.map((uri, index) => (
              <Pressable
                key={`${index}:${uri}`}
                accessibilityRole="imagebutton"
                accessibilityLabel={`${prefix} ${index + 1} / ${total}, 눌러서 크게 보기`}
                onPress={() => setViewerStartIndex(index)}
                style={{ width: inlineWidth, height: '100%' }}
              >
                <Image
                  source={{ uri, cacheKey: uri.split('?')[0] }}
                  style={StyleSheet.absoluteFill}
                  contentFit="contain"
                  // 첨부는 요청마다 서명·만료 쿼리가 바뀌는 Presigned GET URL이라, 전체
                  // URL 기반 캐시 키로는 refetch마다 캐시 미스 → 재로드 → 깜빡였다.
                  // 쿼리를 뗀 S3 객체 경로를 cacheKey·recyclingKey로 고정해 서명이 바뀌어도
                  // 캐시 적중되게 하고, transition을 없애 페이드 재생을 막는다.
                  recyclingKey={`inline:${uri.split('?')[0]}`}
                  cachePolicy="memory-disk"
                />
              </Pressable>
            ))}
          </ScrollView>
        )}

        {/* 우하단 뱃지 — 여러 장이면 현재/전체, 한 장이면 확대 안내. */}
        <View pointerEvents="none" style={styles.badge}>
          <Feather color={INK_COLOR} name="maximize-2" size={12} />
          <Text style={styles.badgeText}>
            {total > 1 ? `${inlineIndex + 1} / ${total}` : '크게 보기'}
          </Text>
        </View>

        {/* 하단 중앙 페이지 도트 — 여러 장일 때만. */}
        {total > 1 && (
          <View pointerEvents="none" style={styles.dots}>
            {images.map((_, index) => (
              <View
                key={index}
                style={[styles.dot, index === inlineIndex && styles.dotActive]}
              />
            ))}
          </View>
        )}
      </View>

      {/* startIndex 를 key 로 줘서 열릴 때마다 remount → 초기 페이지가 useState 초깃값으로 잡힘
          (effect 안에서 setState 하지 않도록). */}
      <FullscreenImageViewer
        key={viewerStartIndex ?? 'closed'}
        images={images}
        labelPrefix={prefix}
        startIndex={viewerStartIndex}
        onClose={() => setViewerStartIndex(null)}
      />
    </View>
  );
}

function FullscreenImageViewer({
  images,
  labelPrefix,
  startIndex,
  onClose,
}: {
  images: string[];
  labelPrefix: string;
  startIndex: number | null;
  onClose: () => void;
}) {
  const insets = useSafeAreaInsets();
  const [viewerWidth, setViewerWidth] = useState(0);
  const initialIndex = startIndex ?? 0;
  const [index, setIndex] = useState(initialIndex);

  const visible = startIndex !== null;
  const total = images.length;

  return (
    <Modal
      visible={visible}
      transparent
      animationType="fade"
      statusBarTranslucent
      onRequestClose={onClose}
    >
      <View style={styles.viewerRoot}>
        <View
          style={styles.viewerBody}
          onLayout={(event) => setViewerWidth(event.nativeEvent.layout.width)}
        >
          {viewerWidth > 0 && (
            <ScrollView
              horizontal
              pagingEnabled
              showsHorizontalScrollIndicator={false}
              scrollEnabled={total > 1}
              scrollEventThrottle={16}
              // 열릴 때 탭한 이미지부터 보이도록 초기 오프셋 지정(마운트 시 1회 적용).
              contentOffset={{ x: initialIndex * viewerWidth, y: 0 }}
              onMomentumScrollEnd={(event) => {
                const next = pageIndexFromScroll(event, viewerWidth);
                setIndex((current) => (next === current ? current : next));
              }}
            >
              {images.map((uri, imageIndex) => (
                <View key={`${imageIndex}:${uri}`} style={{ width: viewerWidth }}>
                  <Image
                    accessibilityLabel={`${labelPrefix} ${imageIndex + 1} / ${total}`}
                    source={{ uri, cacheKey: uri.split('?')[0] }}
                    style={StyleSheet.absoluteFill}
                    contentFit="contain"
                    // 인라인 갤러리와 동일 — Presigned 서명 쿼리를 뗀 경로를 캐시 키로
                    // 고정해, 인라인에서 이미 받은 이미지를 뷰어가 재다운로드하지 않게 한다.
                    recyclingKey={`viewer:${uri.split('?')[0]}`}
                    cachePolicy="memory-disk"
                  />
                </View>
              ))}
            </ScrollView>
          )}
        </View>

        {/* 상단 오버레이 — 페이지 카운터 + 닫기. */}
        <View style={[styles.viewerTopBar, { top: insets.top + 8 }]}>
          <Text style={styles.viewerCounter}>{total > 1 ? `${index + 1} / ${total}` : ''}</Text>
          <Pressable
            accessibilityLabel="이미지 닫기"
            accessibilityRole="button"
            hitSlop={12}
            onPress={onClose}
            style={({ pressed }) => [styles.viewerClose, pressed && styles.viewerClosePressed]}
          >
            <Feather color="#FFFFFF" name="x" size={22} />
          </Pressable>
        </View>
      </View>
    </Modal>
  );
}

const styles = StyleSheet.create({
  wrap: {
    marginTop: 24,
  },
  frame: {
    width: '100%',
    aspectRatio: 1.3,
    position: 'relative',
    backgroundColor: FRAME_BACKGROUND,
  },
  badge: {
    position: 'absolute',
    right: 16,
    bottom: 16,
    flexDirection: 'row',
    alignItems: 'center',
    gap: 5,
    paddingHorizontal: 11,
    paddingVertical: 6,
    borderRadius: 16,
    backgroundColor: 'rgba(255, 255, 255, 0.86)',
  },
  badgeText: {
    color: INK_COLOR,
    fontSize: 12,
    fontFamily: 'IBMPlexSansKR_700Bold',
    letterSpacing: -0.2,
  },
  dots: {
    position: 'absolute',
    left: 0,
    right: 0,
    bottom: 14,
    flexDirection: 'row',
    alignItems: 'center',
    justifyContent: 'center',
    gap: 6,
  },
  dot: {
    width: 6,
    height: 6,
    borderRadius: 3,
    backgroundColor: 'rgba(255, 255, 255, 0.6)',
  },
  dotActive: {
    width: 18,
    backgroundColor: '#FFFFFF',
  },
  viewerRoot: {
    flex: 1,
    backgroundColor: '#000000',
  },
  viewerBody: {
    flex: 1,
  },
  viewerTopBar: {
    position: 'absolute',
    left: 0,
    right: 0,
    paddingHorizontal: 16,
    flexDirection: 'row',
    alignItems: 'center',
    justifyContent: 'space-between',
  },
  viewerCounter: {
    color: '#FFFFFF',
    fontSize: 14,
    fontFamily: 'IBMPlexSansKR_700Bold',
    letterSpacing: 0.2,
  },
  viewerClose: {
    width: 40,
    height: 40,
    borderRadius: 20,
    alignItems: 'center',
    justifyContent: 'center',
    backgroundColor: 'rgba(0, 0, 0, 0.4)',
  },
  viewerClosePressed: {
    opacity: 0.6,
  },
});
