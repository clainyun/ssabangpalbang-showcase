import { LinearGradient } from 'expo-linear-gradient';
import { StyleSheet, View } from 'react-native';

interface GlossyFillProps {
  base: string;
  light: string;
  radius?: number;
  glossOpacity?: number;
}

/**
 * 버튼 배경을 구슬 질감(표면 그라데이션 + 상단 광택)으로 채우는 재사용 레이어.
 * 홈 화면(home.tsx)의 "스터디 찾기" 버튼에서 쓰던 걸 그대로 뽑아 공용화했습니다
 * (체크리스트 시트의 기록·촬영·임장 종료 버튼도 같은 질감을 씁니다).
 *
 * 버튼 콘텐츠(아이콘·텍스트)는 이 컴포넌트보다 "뒤"에 오도록 JSX에서 먼저 선언하세요.
 */
export function GlossyFill({ base, light, radius = 16, glossOpacity = 0.5 }: GlossyFillProps) {
  return (
    <View
      pointerEvents="none"
      style={[StyleSheet.absoluteFill, { borderRadius: radius, overflow: 'hidden', backgroundColor: base }]}
    >
      <LinearGradient
        colors={[light, base]}
        locations={[0, 0.9]}
        start={{ x: 0, y: 0 }}
        end={{ x: 0, y: 1 }}
        style={StyleSheet.absoluteFill}
      />
      <LinearGradient
        colors={[`rgba(255,255,255,${glossOpacity})`, 'rgba(255,255,255,0)']}
        start={{ x: 0, y: 0 }}
        end={{ x: 0, y: 1 }}
        style={styles.glossHighlight}
      />
    </View>
  );
}

const styles = StyleSheet.create({
  glossHighlight: {
    position: 'absolute',
    top: 0,
    left: 8,
    right: 8,
    height: '50%',
    borderRadius: 12,
  },
});
