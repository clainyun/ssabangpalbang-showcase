import { Text, View } from 'react-native';

/** 소셜 로그인 리다이렉트 수신 지점 (§6.1) */
export default function CallbackScreen() {
  return (
    <View style={{ flex: 1, alignItems: 'center', justifyContent: 'center' }}>
      <Text>로그인 처리 중…</Text>
    </View>
  );
}
