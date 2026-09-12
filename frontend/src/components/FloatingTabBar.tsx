import { useRouter } from 'expo-router';
import type { ReactNode } from 'react';
import { Pressable, StyleSheet, View } from 'react-native';
import { useSafeAreaInsets } from 'react-native-safe-area-context';

import { GlassTabBarBackground } from '@/components/GlassTabBarBackground';
import { TabIcon } from '@/components/TabIcon';
import {
  FLOATING_TAB_BAR_HEIGHT,
  getFloatingTabBarBottomMargin,
} from '@/components/floatingTabBarMetrics';
import {
  MapIcon,
  CommunityIcon,
  HomeIcon,
  WishlistIcon,
  MyIcon,
} from '@/components/icons/NavIcons';

export type FloatingTabBarKey = 'map' | 'community' | 'home' | 'wishlist' | 'my';

/**
 * (tabs) 그룹 밖의 화면(예: 임장 진행 화면)에 홈 화면과 똑같은 떠 있는 유리 탭바를
 * 그대로 놓기 위한 컴포넌트입니다. (tabs)/_layout.tsx 는 React Navigation 의
 * tabBarButton 슬롯에 묶여 있어 그 화면 밖에서는 못 쓰므로, 같은 배경(GlassTabBarBackground)
 * ·같은 아이콘(TabIcon)으로 겉모습을 그대로 복제하고 탭은 router.push 로 이동합니다.
 *
 * 실제 탭 화면이 아니므로 포커스는 React Navigation이 아니라 activeTab prop으로
 * 직접 지정합니다.
 */
const TABS: readonly {
  key: FloatingTabBarKey;
  label: string;
  href: '/(app)/(tabs)/map' | '/(app)/(tabs)/community' | '/(app)/(tabs)/home' | '/(app)/(tabs)/wishlist' | '/(app)/(tabs)/my';
  renderIcon: (color: string) => ReactNode;
}[] = [
  { key: 'map', label: '지도', href: '/(app)/(tabs)/map', renderIcon: (c) => <MapIcon color={c} /> },
  {
    key: 'community',
    label: '커뮤니티',
    href: '/(app)/(tabs)/community',
    renderIcon: (c) => <CommunityIcon color={c} />,
  },
  { key: 'home', label: '홈', href: '/(app)/(tabs)/home', renderIcon: (c) => <HomeIcon color={c} /> },
  {
    key: 'wishlist',
    label: '찜',
    href: '/(app)/(tabs)/wishlist',
    renderIcon: (c) => <WishlistIcon color={c} />,
  },
  { key: 'my', label: '마이', href: '/(app)/(tabs)/my', renderIcon: (c) => <MyIcon color={c} /> },
];

interface FloatingTabBarProps {
  activeTab: FloatingTabBarKey;
}

export function FloatingTabBar({ activeTab }: FloatingTabBarProps) {
  const router = useRouter();
  const insets = useSafeAreaInsets();

  return (
    <View style={[styles.tabBar, { marginBottom: getFloatingTabBarBottomMargin(insets.bottom) }]}>
      <GlassTabBarBackground />
      <View style={styles.row}>
        {TABS.map((tab) => (
          <Pressable
            accessibilityRole="button"
            accessibilityState={{ selected: tab.key === activeTab }}
            android_ripple={{ color: 'transparent' }}
            key={tab.key}
            onPress={() => router.push(tab.href)}
            style={styles.tabButton}
          >
            <TabIcon focused={tab.key === activeTab} label={tab.label} renderIcon={tab.renderIcon} />
          </Pressable>
        ))}
      </View>
    </View>
  );
}

const styles = StyleSheet.create({
  // (tabs)/_layout.tsx 의 tabBar 스타일과 동일하게 맞춥니다(치수·둥근 정도·그림자).
  tabBar: {
    position: 'absolute',
    left: 0,
    right: 0,
    bottom: 0,
    marginHorizontal: 14,
    marginTop: 6,
    height: FLOATING_TAB_BAR_HEIGHT,
    borderRadius: 30,
    backgroundColor: 'transparent',
    boxShadow: '0px 5px 16px rgba(16, 39, 30, 0.16)',
    elevation: 0,
  },
  row: {
    flex: 1,
    flexDirection: 'row',
    paddingHorizontal: 8,
  },
  tabButton: {
    flex: 1,
    alignItems: 'center',
    justifyContent: 'center',
  },
});
