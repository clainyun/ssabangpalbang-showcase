import { forwardRef, type ReactNode } from 'react';
import { Pressable, StyleSheet, View, type StyleProp, type ViewStyle } from 'react-native';

import { GlassSurface } from '@/components/GlassSurface';

/**
 * 앱 전체가 함께 쓰는 원형 유리 아이콘 버튼.
 *
 * 홈 헤더(알림·설정)의 재질을 기준으로 삼아, 지도·임장·커뮤니티·마이페이지·스터디
 * 상세·아파트 상세가 모두 같은 모양·크기·질감을 쓰도록 한곳에 모았습니다. 화면마다
 * 지름과 그림자를 따로 적어 두면 조금씩 어긋나기 때문에, 값은 여기서만 바꿉니다.
 *
 * ⚠️ GlassSurface 는 Android 에서 형제보다 위에 합성되므로 아이콘보다 **먼저**
 * 선언해야 합니다(GlassSurface 주석 참고).
 * ⚠️ 배경이 투명이라 elevation 을 주면 Android 가 검은 사각 그림자를 그립니다.
 * 떠 있는 느낌은 boxShadow 로만 냅니다.
 */

/** 기본 지름. 홈 헤더의 40 보다 약간 키워 터치 영역을 넉넉하게 잡았습니다. */
export const GLASS_ICON_BUTTON_SIZE = 46;
/** 위 지름에 어울리는 아이콘 크기. */
export const GLASS_ICON_BUTTON_ICON_SIZE = 22;

/** 켜짐 상태(지도 3D·위치추적·필터, 알림 열림)에서 입히는 민트 틴트. */
const ACTIVE_TINT = '#AEFBCF';
const IDLE_TINT = '#FFFFFF';

interface GlassIconButtonProps {
  accessibilityLabel: string;
  children: ReactNode;
  onPress: () => void;
  /** 토글 버튼이면 현재 켜짐 여부. 동작 버튼이면 넘기지 않습니다. */
  active?: boolean;
  /** 드롭다운·시트를 여는 버튼이면 펼침 여부(스크린리더용). */
  expanded?: boolean;
  disabled?: boolean;
  /** 지름. 특별한 이유가 없으면 기본값을 그대로 씁니다. */
  size?: number;
  /** 놓일 자리(위치·마진)만 지정합니다. 재질은 이 컴포넌트가 갖습니다. */
  style?: StyleProp<ViewStyle>;
  hitSlop?: number;
  testID?: string;
}

export const GlassIconButton = forwardRef<View, GlassIconButtonProps>(function GlassIconButton(
  {
    accessibilityLabel,
    children,
    onPress,
    active = false,
    expanded,
    disabled = false,
    size = GLASS_ICON_BUTTON_SIZE,
    style,
    hitSlop = 8,
    testID,
  },
  ref,
) {
  const radius = size / 2;

  return (
    <Pressable
      accessibilityLabel={accessibilityLabel}
      accessibilityRole="button"
      accessibilityState={{ selected: active, disabled, expanded }}
      disabled={disabled}
      hitSlop={hitSlop}
      onPress={onPress}
      ref={ref}
      style={({ pressed }) => [
        styles.button,
        { width: size, height: size, borderRadius: radius },
        style,
        disabled && styles.disabled,
        pressed && styles.pressed,
      ]}
      testID={testID}
    >
      <GlassSurface
        blurTint="light"
        radius={radius}
        showRim={false}
        tint={active ? ACTIVE_TINT : IDLE_TINT}
        tintOpacity={active ? 0.5 : 0.32}
      />
      {children}
    </Pressable>
  );
});

const styles = StyleSheet.create({
  button: {
    position: 'relative',
    alignItems: 'center',
    justifyContent: 'center',
    // 유리 재질은 GlassSurface 가 그리므로 배경은 투명이어야 블러가 보입니다.
    boxShadow: '0px 3px 10px rgba(16, 39, 30, 0.14)',
  },
  disabled: { opacity: 0.42 },
  pressed: { opacity: 0.72, transform: [{ scale: 0.94 }] },
});
