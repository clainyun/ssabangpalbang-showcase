import { Link, Stack } from 'expo-router';
import { Text, View } from 'react-native';

export default function NotFoundScreen() {
  return (
    <>
      <Stack.Screen options={{ title: '없는 화면' }} />
      <View style={{ flex: 1, alignItems: 'center', justifyContent: 'center', gap: 12 }}>
        <Text>요청한 화면이 없습니다.</Text>
        <Link href="/">홈으로</Link>
      </View>
    </>
  );
}
