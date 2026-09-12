import { StyleSheet, useWindowDimensions } from 'react-native';
import Svg, { Defs, RadialGradient, Rect, Stop } from 'react-native-svg';

const DEFAULT_GLOW_COLOR = '#cef0e4';

interface ScreenGlowBackgroundProps {
  color?: string;
  /** 글로우 중심 위치(창 너비/높이 비율, 0~1). 기본은 커뮤니티 탭과 같은 우상단. */
  cxRatio?: number;
  cyRatio?: number;
  radiusRatio?: number;
}

/**
 * 흰 배경 위 우상단에서 은은하게 번지는 radial 글로우. 커뮤니티 탭(community.tsx)에서
 * 쓰던 걸 다른 화면에서도 같은 느낌으로 쓸 수 있게 공용 컴포넌트로 뽑았습니다.
 */
export function ScreenGlowBackground({
  color = DEFAULT_GLOW_COLOR,
  cxRatio = 0.74,
  cyRatio = 0.06,
  radiusRatio = 0.82,
}: ScreenGlowBackgroundProps) {
  const { width, height } = useWindowDimensions();

  return (
    <Svg pointerEvents="none" style={StyleSheet.absoluteFill} width={width} height={height}>
      <Defs>
        <RadialGradient
          id="screenGlow"
          cx={width * cxRatio}
          cy={height * cyRatio}
          r={width * radiusRatio}
          gradientUnits="userSpaceOnUse"
        >
          <Stop offset="0" stopColor={color} stopOpacity={0.85} />
          <Stop offset="0.5" stopColor={color} stopOpacity={0.3} />
          <Stop offset="1" stopColor={color} stopOpacity={0} />
        </RadialGradient>
      </Defs>
      <Rect width={width} height={height} fill="url(#screenGlow)" />
    </Svg>
  );
}
