import { Tabs } from 'expo-router';
import { StyleSheet, Pressable, type GestureResponderEvent } from 'react-native';
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

// Tabs의 initialRouteName prop은 런타임(React Navigation)에만 적용됩니다.
// index.tsx가 없는 그룹의 맨 경로(/(app)/(tabs))가 라우트 매니페스트 단계에서도
// 정상 해석되려면 Expo Router 전용 unstable_settings로 따로 선언해야 합니다.
export const unstable_settings = {
  initialRouteName: 'home',
};

/**
 * React Navigation 의 기본 tabBarIcon 은 아이콘 전용 31x28 고정 박스에
 * focused/unfocused 아이콘을 겹쳐 그리는 방식이라, 배경 필+라벨까지 포함한
 * TabIcon 을 그 안에 넣으면 박스 크기와 실제 콘텐츠 크기가 어긋나 배경과
 * 아이콘 위치가 안 맞아 보입니다. tabBarButton 으로 완전히 대체해서
 * 그 고정 박스 자체를 우회합니다.
 */
type TabButtonProps = {
  onPress?: (e: GestureResponderEvent) => void;
  onLongPress?: ((e: GestureResponderEvent) => void) | null;
  // BottomTabItem 이 accessibilityState 가 아니라 aria-selected 로 focused 여부를 넘깁니다.
  'aria-selected'?: boolean;
  testID?: string;
};

function renderTabButton(label: string, renderIcon: (color: string) => React.ReactNode) {
  return function TabButton({ onPress, onLongPress, 'aria-selected': focused, testID }: TabButtonProps) {
    return (
      <Pressable
        onPress={onPress}
        onLongPress={onLongPress}
        testID={testID}
        android_ripple={{ color: 'transparent' }}
        style={styles.tabButton}
      >
        <TabIcon focused={!!focused} label={label} renderIcon={renderIcon} />
      </Pressable>
    );
  };
}

export default function TabsLayout() {
  const insets = useSafeAreaInsets();
  // 제스처 내비게이션(얇은 바)과 3버튼 내비게이션(두꺼운 바)의 높이 차이를
  // insets.bottom 으로 실시간 반영합니다.
  const tabBarStyle = {
    ...styles.tabBar,
    marginBottom: getFloatingTabBarBottomMargin(insets.bottom),
  };

  return (
    <Tabs
      initialRouteName="home"
      screenOptions={{
        headerShown: false,
        tabBarShowLabel: false,
        tabBarStyle,
        tabBarItemStyle: styles.tabBarItem,
        tabBarBackground: () => <GlassTabBarBackground />,
      }}
    >
      <Tabs.Screen
        name="map"
        options={{
          tabBarButton: renderTabButton('지도', (color) => <MapIcon color={color} />),
        }}
      />
      <Tabs.Screen
        name="community"
        options={{
          tabBarButton: renderTabButton('커뮤니티', (color) => <CommunityIcon color={color} />),
        }}
      />
      <Tabs.Screen
        name="home"
        options={{
          tabBarButton: renderTabButton('홈', (color) => <HomeIcon color={color} />),
        }}
      />
      <Tabs.Screen
        name="wishlist"
        options={{
          tabBarButton: renderTabButton('찜', (color) => <WishlistIcon color={color} />),
        }}
      />
      <Tabs.Screen
        name="my"
        options={{
          tabBarButton: renderTabButton('마이', (color) => <MyIcon color={color} />),
        }}
      />
    </Tabs>
  );
}

const styles = StyleSheet.create({
  tabBar: {
    position: 'absolute',
    marginHorizontal: 14,
    marginBottom: 18,
    marginTop: 6,
    height: FLOATING_TAB_BAR_HEIGHT,
    // 완전한 알약(pill) 형태. 실제 유리 표면은 tabBarBackground(GlassTabBarBackground)가
    // 그리므로 배경은 투명으로 두고 여기선 위치·그림자만 담당.
    borderRadius: 30,
    backgroundColor: 'transparent',
    paddingHorizontal: 8,
    paddingTop: 0,
    paddingBottom: 0,
    borderTopWidth: 0,
    // 아주 옅은 그림자. ⚠️ elevation은 사용 금지 — backgroundColor:'transparent' +
    // borderRadius 조합에서 Android가 둥근 outline을 못 잡아 사각 흰 박스 아티팩트가
    // 생기는 버그가 있음(삼성 One UI/Fabric). 대신 borderRadius를 존중하는 boxShadow
    // (RN 0.76+/New Arch)로 투명 배경에서도 안전하게 둥근 그림자를 렌더.
    boxShadow: '0px 5px 16px rgba(16, 39, 30, 0.16)',
    elevation: 0,
  },
  tabBarItem: {
    flex: 1,
    paddingHorizontal: 0, // 좌우 여백 최소화
  },
  tabButton: {
    flex: 1,
    alignItems: 'center',
    justifyContent: 'center',
  },
});
