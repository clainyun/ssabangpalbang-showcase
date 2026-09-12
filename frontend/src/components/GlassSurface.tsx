import { BlurView } from 'expo-blur';
import { LinearGradient } from 'expo-linear-gradient';
import { StyleSheet, View } from 'react-native';

type GlassSurfaceProps = {
  /** 유리에 입힐 틴트 색(hex). 민트/블랙 등. */
  tint: string;
  /** 블러 톤. 밝은 색 버튼은 'light', 어두운 색 버튼은 'dark'. */
  blurTint?: 'light' | 'dark' | 'default';
  /** 부모(버튼)의 borderRadius와 동일하게 맞춰 클립. */
  radius?: number;
  /** 틴트 반투명 정도(0~1). 낮을수록 더 투명. */
  tintOpacity?: number;
  intensity?: number;
  /** 유리 가장자리 흰색 rim 하이라이트 표시 여부. */
  showRim?: boolean;
};

function hexToRgba(hex: string, alpha: number): string {
  const h = hex.replace('#', '');
  const r = parseInt(h.slice(0, 2), 16);
  const g = parseInt(h.slice(2, 4), 16);
  const b = parseInt(h.slice(4, 6), 16);
  return `rgba(${r}, ${g}, ${b}, ${alpha})`;
}

/**
 * 하단 탭바(_layout.tsx GlassTabBarBackground)와 같은 유리(블러) 재질을 내는 재사용 배경.
 * 절대 위치로 부모를 꽉 채우며 부모의 borderRadius에 맞춰 클립됩니다. 색 틴트만 바꿔
 * 민트/블랙 등 원하는 톤의 반투명 유리를 만듭니다.
 * Android에서는 기본 no-blur 동작을 사용하고 tint·gradient·rim으로 질감을 유지합니다.
 * 버튼 컨텐츠(아이콘·텍스트)는 이 컴포넌트보다 "뒤"에 오도록 JSX에서 먼저 선언하세요.
 */
export function GlassSurface({
  tint,
  blurTint = 'light',
  radius = 16,
  tintOpacity = 0.42,
  intensity = 24,
  showRim = true,
}: GlassSurfaceProps) {
  const isDark = blurTint === 'dark';
  // ⚠️ Android에서 BlurView는 형제 뷰보다 위에 합성되므로, 틴트·광택·rim을 형제로 두면
  // 블러에 묻혀 색이 안 먹힘. 반드시 BlurView의 "자식"으로 넣어 블러 위에 얹는다.
  return (
    <BlurView
      pointerEvents="none"
      intensity={intensity}
      tint={blurTint}
      style={[styles.container, { borderRadius: radius }]}
    >
      {/* 색 틴트(반투명) — 투명한 유리 위에 은은한 색감. */}
      <View style={[StyleSheet.absoluteFill, { backgroundColor: hexToRgba(tint, tintOpacity) }]} />
      {/* 상단 스페큘러 하이라이트(광택) — 위 가장자리가 밝게. */}
      <LinearGradient
        colors={[isDark ? 'rgba(255,255,255,0.20)' : 'rgba(255,255,255,0.55)', 'rgba(255,255,255,0)']}
        start={{ x: 0, y: 0 }}
        end={{ x: 0, y: 1 }}
        style={styles.specular}
      />
      {/* 하단 음영(유리 두께/입체감). */}
      <LinearGradient
        colors={['rgba(255,255,255,0)', isDark ? 'rgba(0,0,0,0.18)' : 'rgba(24,54,42,0.10)']}
        locations={[0.5, 1]}
        start={{ x: 0, y: 0 }}
        end={{ x: 0, y: 1 }}
        style={StyleSheet.absoluteFill}
      />
      {/* 얇은 rim 하이라이트(유리 가장자리). */}
      {showRim ? (
        <View
          style={[
            styles.rim,
            { borderRadius: radius, borderColor: isDark ? 'rgba(255,255,255,0.16)' : 'rgba(255,255,255,0.5)' },
          ]}
        />
      ) : null}
    </BlurView>
  );
}

const styles = StyleSheet.create({
  container: {
    position: 'absolute',
    top: 0,
    left: 0,
    right: 0,
    bottom: 0,
    overflow: 'hidden',
  },
  specular: {
    position: 'absolute',
    top: 0,
    left: 0,
    right: 0,
    height: '55%',
  },
  rim: {
    position: 'absolute',
    top: 0,
    left: 0,
    right: 0,
    bottom: 0,
    borderWidth: 1,
  },
});
