import { BlurView } from 'expo-blur';
import { LinearGradient } from 'expo-linear-gradient';
import { StyleSheet, View } from 'react-native';

interface GlassTabBarBackgroundProps {
  /** 모서리 반경. 탭바는 완전한 알약(30)이고, 체크리스트 시트처럼 더 넓은 요소에 같은
   * 유리를 재사용할 때는 그 요소의 반경을 넘겨 모서리가 어긋나지 않게 합니다. */
  radius?: number;
}

/**
 * 탭바 "물방울 유리" 배경. (tabs)/_layout.tsx 와 field/[sessionId].tsx 의 떠 있는
 * 탭바, 그리고 체크리스트 시트가 접혀 있을 때(ChecklistSheet)가 함께 씁니다.
 *
 * @uginy/react-native-liquid-glass 의 실제 굴절이 이 기기에서 렌더되지 않아(1단계
 * 검증), 굴절 대신 expo-blur(BlurView) + LinearGradient 반투명톤으로 유리 질감을
 * 냅니다. Android에서는 기본 no-blur 동작을 사용하고 gradient 기반 질감을 유지합니다.
 */
export function GlassTabBarBackground({ radius = 30 }: GlassTabBarBackgroundProps) {
  return (
    <View style={[styles.glassBg, { borderRadius: radius }]}>
      <BlurView
        intensity={24}
        tint="light"
        style={[styles.glassBlur, { borderRadius: radius }]}
      />
      {/* 유리 바디 톤 — 투명하게 유지. */}
      <LinearGradient
        colors={['rgba(255,255,255,0.18)', 'rgba(255,255,255,0.02)']}
        locations={[0, 0.7]}
        start={{ x: 0, y: 0 }}
        end={{ x: 0, y: 1 }}
        style={StyleSheet.absoluteFill}
      />
      {/* 하단 은은한 음영 — 유리 두께/입체감(아래로 갈수록 살짝 어둡게). */}
      <LinearGradient
        colors={['rgba(255,255,255,0)', 'rgba(24,54,42,0.09)']}
        locations={[0.5, 1]}
        start={{ x: 0, y: 0 }}
        end={{ x: 0, y: 1 }}
        style={StyleSheet.absoluteFill}
      />
      {/* 상단 스페큘러 하이라이트 — 위 가장자리가 밝게 반짝이는 광택(부드럽게 사라짐). */}
      <LinearGradient
        colors={['rgba(255,255,255,0.6)', 'rgba(255,255,255,0)']}
        start={{ x: 0, y: 0 }}
        end={{ x: 0, y: 1 }}
        style={styles.glassSpecular}
      />
    </View>
  );
}

const styles = StyleSheet.create({
  // 유리 배경(BlurView+gradient)을 알약 모양으로 클립. 탭바 컨테이너를 꽉 채움.
  glassBg: {
    position: 'absolute',
    top: 0,
    left: 0,
    right: 0,
    bottom: 0,
    overflow: 'hidden',
    // 유리 바디 흰 톤 — 덜 투명하게(뒤 콘텐츠가 덜 비치도록) 불투명도 상향.
    backgroundColor: 'rgba(255, 255, 255, 0.5)',
  },
  // 블러 레이어를 알약 모양으로 클립(안드로이드 사각 블러 방지).
  glassBlur: {
    position: 'absolute',
    top: 0,
    left: 0,
    right: 0,
    bottom: 0,
    overflow: 'hidden',
  },
  // 상단 스페큘러 하이라이트(광택) — 위쪽 절반 정도만 덮되 아래로 완전히 사라져 밴드 안 생김.
  glassSpecular: {
    position: 'absolute',
    top: 0,
    left: 0,
    right: 0,
    height: '52%',
  },
});
