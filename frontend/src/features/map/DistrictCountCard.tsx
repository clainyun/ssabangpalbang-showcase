import { memo } from 'react';
import { Pressable, StyleSheet, Text, View } from 'react-native';

interface DistrictCountCardProps {
  districtName: string;
  apartmentCount: number;
  onPress: () => void;
}

export const DistrictCountCard = memo(function DistrictCountCard({
  districtName,
  apartmentCount,
  onPress,
}: DistrictCountCardProps) {
  return (
    <Pressable
      accessibilityRole="button"
      accessibilityLabel={`${districtName} 단지 ${apartmentCount}개`}
      onPress={onPress}
    >
      <View style={styles.container}>
        <Text style={styles.districtName}>{districtName}</Text>
        <View style={styles.countRow}>
          <Text style={styles.countPrefix}>단지 </Text>
          <Text style={styles.count}>{apartmentCount.toLocaleString('ko-KR')}</Text>
        </View>
      </View>
    </Pressable>
  );
});

const styles = StyleSheet.create({
  container: {
    alignItems: 'center',
    backgroundColor: '#FFFFFF',
    borderRadius: 8,
    borderWidth: 0,
    elevation: 4,
    paddingHorizontal: 10,
    paddingVertical: 6,
    shadowColor: '#0D1A17',
    shadowOffset: { width: 0, height: 2 },
    shadowOpacity: 0.18,
    shadowRadius: 4,
  },
  districtName: {
    color: '#1F2937',
    fontSize: 13,
    fontWeight: '700',
  },
  countRow: {
    alignItems: 'center',
    flexDirection: 'row',
  },
  countPrefix: {
    color: '#6B7280',
    fontSize: 11,
  },
  count: {
    color: '#1F5F55',
    fontSize: 12,
    fontWeight: '700',
  },
});
