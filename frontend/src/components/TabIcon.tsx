import { View, Text, StyleSheet } from 'react-native';
import Svg, { Defs, RadialGradient, Stop, Rect } from 'react-native-svg';

// 선택된 탭: 진한 초록 글로우 위에 흰색 아이콘/라벨을 얹어 대비를 줍니다.
// 다른 화면(ChecklistSheet 등)에서 나브바와 글씨체·굵기·색을 맞출 때도 이 값을
// 그대로 가져다 쓰므로 export 합니다.
export const SELECTED_COLOR = '#FFFFFF';
// 유리(블러) 탭바 위에서 기존 PLACEHOLDER(#AEBAB2)가 흐릿해 대비가 부족 →
// 조금 더 진한 회녹색으로 조정해 비활성 아이콘/라벨 가독성 확보.
export const INACTIVE_COLOR = '#5F6E66';
export const TAB_LABEL_FONT_SEMIBOLD = 'IBMPlexSansKR_600SemiBold';
export const TAB_LABEL_FONT_BOLD = 'IBMPlexSansKR_700Bold';

// 아이콘 배치 영역 크기(레이아웃)와, 그보다 넓게 번지는 글로우 SVG 크기.
const ICON_BOX = 48;
const GLOW = 76; // 글로우 범위(넓게) — ICON_BOX보다 크게 해서 아이콘 밖으로 번짐.

/** 중심은 색, 가장자리는 완전 투명으로 부드럽게 퍼지는 원형 글로우(halo).
 * 딱딱한 테두리 없이 희미하게 강조만 합니다. 선택=초록, 나머지=옅은 회색. */
function SoftGlow({ focused }: { focused: boolean }) {
  const color = focused ? '#13B26E' : '#7C8A83';
  const centerOpacity = focused ? 0.58 : 0.14;
  // 선택 시엔 중앙 진한 영역을 조금 넓게 유지(흰 아이콘/라벨 대비 확보), 가장자리에서 부드럽게 사라지게.
  const midOffset = focused ? '58%' : '55%';
  const midOpacity = focused ? centerOpacity * 0.75 : centerOpacity * 0.45;
  return (
    <Svg width={GLOW} height={GLOW} style={styles.glow} pointerEvents="none">
      <Defs>
        <RadialGradient id="tabGlow" cx="50%" cy="50%" r="50%">
          <Stop offset="0%" stopColor={color} stopOpacity={centerOpacity} />
          <Stop offset={midOffset} stopColor={color} stopOpacity={midOpacity} />
          <Stop offset="100%" stopColor={color} stopOpacity={0} />
        </RadialGradient>
      </Defs>
      <Rect width={GLOW} height={GLOW} fill="url(#tabGlow)" />
    </Svg>
  );
}

type TabIconProps = {
  focused: boolean;
  label: string;
  renderIcon: (color: string) => React.ReactNode;
};

export function TabIcon({ focused, label, renderIcon }: TabIconProps) {
  const color = focused ? SELECTED_COLOR : INACTIVE_COLOR;

  return (
    <View style={styles.tabItem}>
      <SoftGlow focused={focused} />
      <View style={styles.iconWrapper}>{renderIcon(color)}</View>
      <Text numberOfLines={1} style={[styles.label, focused && styles.labelActive]}>
        {label}
      </Text>
    </View>
  );
}

const styles = StyleSheet.create({
  tabItem: {
    alignItems: 'center',
    justifyContent: 'center',
    gap: 2,
    width: '100%',
  },
  iconWrapper: {
    width: ICON_BOX,
    height: 28,
    alignItems: 'center',
    justifyContent: 'center',
  },
  // 글로우를 탭 아이템(아이콘+라벨 묶음) 정중앙에 앵커 → 탭바 세로 정중앙에 위치.
  // 묶음이 탭바 안에서 수직 중앙 정렬되므로, 묶음 중앙 = 탭바 중앙.
  glow: {
    position: 'absolute',
    top: '50%',
    left: '50%',
    marginTop: -GLOW / 2,
    marginLeft: -GLOW / 2,
  },
  label: {
    fontSize: 12,
    fontFamily: TAB_LABEL_FONT_SEMIBOLD,
    color: INACTIVE_COLOR,
    textAlign: 'center',
    includeFontPadding: false,
  },
  labelActive: {
    color: SELECTED_COLOR,
    fontFamily: TAB_LABEL_FONT_BOLD,
  },
});
