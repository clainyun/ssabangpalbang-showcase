import Ionicons from '@expo/vector-icons/Ionicons';
import { StyleSheet, View } from 'react-native';
import Svg, { Defs, RadialGradient, Stop, Circle } from 'react-native-svg';

import { PRIMARY_COLOR, SOFT_GREEN_COLOR } from '@/constants/colors';

/**
 * 시트 핸들의 아이콘 배지. 나브바(TabIcon.tsx SoftGlow)와 같은 방식의 그라디언트를
 * 씁니다 — 중심이 밝고 가장자리로 갈수록 옅어지는 라디얼(원형) 그라디언트입니다.
 * 색은 그대로 흰색→SOFT_GREEN_COLOR 이지만, 이전엔 r(반경)이 65%라 바깥쪽 35%
 * 링이 이미 단색이 된 채로 잘려서 그라디언트가 잘 안 느껴졌습니다. r을 100%로
 * 늘려 원 전체에 걸쳐 흰색→민트 전환이 보이게 했습니다.
 *
 * 아이콘은 SVG 밖의 일반 RN 뷰로 그립니다 — react-native-svg 의 foreignObject 는
 * Ionicons 같은 RN 컴포넌트를 안정적으로 담지 못해서, SVG 원(배경)과 아이콘을
 * 절대위치로 겹치는 편이 더 안전합니다.
 *
 * 아이콘 색은 나브바가 선택된 탭에 쓰는 초록(PRIMARY_COLOR)과 같습니다.
 *
 * Ionicons 는 선 굵기(strokeWidth)를 조절하는 옵션이 없어서, 같은 아이콘을
 * 아주 살짝(0.5px) 어긋나게 4번 겹쳐 그려 굵어 보이게 만듭니다(흔히 쓰는
 * "페이크 볼드" 기법).
 */
const ICON_COLOR = PRIMARY_COLOR;
const BADGE_SIZE = 26;
const ICON_SIZE = 17;
/** 페이크 볼드 오프셋. 너무 크면 윤곽이 뭉개지고 겹쳐 보이므로 아주 작게만. */
const BOLD_OFFSETS: { x: number; y: number }[] = [
  { x: 0, y: 0 },
  { x: 0.6, y: 0 },
  { x: 0, y: 0.6 },
  { x: 0.6, y: 0.6 },
];

interface SheetHandleIconBadgeProps {
  iconName: keyof typeof Ionicons.glyphMap;
}

export function SheetHandleIconBadge({ iconName }: SheetHandleIconBadgeProps) {
  return (
    <View style={styles.badge}>
      <Svg height={BADGE_SIZE} style={StyleSheet.absoluteFill} width={BADGE_SIZE}>
        <Defs>
          <RadialGradient cx="50%" cy="42%" id="badgeGlow" r="100%">
            <Stop offset="0%" stopColor="#FFFFFF" stopOpacity={1} />
            <Stop offset="100%" stopColor={SOFT_GREEN_COLOR} stopOpacity={1} />
          </RadialGradient>
        </Defs>
        <Circle cx={BADGE_SIZE / 2} cy={BADGE_SIZE / 2} fill="url(#badgeGlow)" r={BADGE_SIZE / 2} />
      </Svg>
      <View style={styles.iconStack}>
        {BOLD_OFFSETS.map(({ x, y }) => (
          <Ionicons
            color={ICON_COLOR}
            key={`${x}-${y}`}
            name={iconName}
            size={ICON_SIZE}
            style={{ position: 'absolute', left: x, top: y }}
          />
        ))}
      </View>
    </View>
  );
}

const styles = StyleSheet.create({
  badge: {
    width: BADGE_SIZE,
    height: BADGE_SIZE,
    // 완전한 원 — 떠 있는 탭바(FloatingTabBar)의 알약 모양과 같은 둥근 정도로.
    borderRadius: BADGE_SIZE / 2,
    overflow: 'hidden',
    alignItems: 'center',
    justifyContent: 'center',
  },
  iconStack: {
    width: ICON_SIZE,
    height: ICON_SIZE,
  },
});
