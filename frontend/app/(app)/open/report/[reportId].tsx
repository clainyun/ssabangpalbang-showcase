import { Link, Redirect, Stack, useLocalSearchParams } from 'expo-router';
import { StyleSheet, Text, View } from 'react-native';

import { parsePendingReportId } from '@/features/navigation/pendingAppLinkStore';

export default function OpenReportLinkScreen() {
  const params = useLocalSearchParams<{ reportId?: string | string[] }>();
  const rawReportId = Array.isArray(params.reportId) ? params.reportId[0] : params.reportId;
  const reportId = rawReportId
    ? parsePendingReportId(`/open/report/${rawReportId}`)
    : null;

  if (!reportId) {
    return (
      <View style={styles.invalidContainer}>
        <Stack.Screen options={{ title: '잘못된 리포트 링크' }} />
        <Text style={styles.invalidTitle}>리포트 링크가 올바르지 않아요</Text>
        <Text style={styles.invalidDescription}>공유받은 주소를 다시 확인해 주세요.</Text>
        <Link href="/(app)/(tabs)/home" style={styles.homeLink}>
          홈으로 이동
        </Link>
      </View>
    );
  }

  return (
    <Redirect
      href={{
        pathname: '/(app)/report/[reportId]',
        params: { reportId },
      }}
    />
  );
}

const styles = StyleSheet.create({
  invalidContainer: {
    flex: 1,
    alignItems: 'center',
    justifyContent: 'center',
    gap: 12,
    padding: 24,
    backgroundColor: '#FFFFFF',
  },
  invalidTitle: {
    color: '#111827',
    fontSize: 20,
    fontWeight: '700',
  },
  invalidDescription: {
    color: '#4B5563',
    fontSize: 15,
  },
  homeLink: {
    marginTop: 8,
    color: '#087A59',
    fontSize: 16,
    fontWeight: '700',
  },
});
