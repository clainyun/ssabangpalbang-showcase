import { View, Text, StyleSheet } from 'react-native';

import { OptionButton } from '@/components/OptionButton';
import { TEXT_COLOR, PLACEHOLDER_COLOR } from '@/constants/colors';
import type { MaritalStatus } from '@/features/onboarding/api/saveOnboarding';

interface HouseholdStepProps {
  maritalStatus: MaritalStatus | null;
  onMaritalStatusChange: (value: MaritalStatus) => void;
  hasChildren: boolean | null;
  onHasChildrenChange: (value: boolean) => void;
  hasVehicle: boolean | null;
  onHasVehicleChange: (value: boolean) => void;
}

export function HouseholdStep({
  maritalStatus,
  onMaritalStatusChange,
  hasChildren,
  onHasChildrenChange,
  hasVehicle,
  onHasVehicleChange,
}: HouseholdStepProps) {
  return (
    <View style={styles.container}>
      <Text style={styles.title}>생활 조건을 알려주세요</Text>
      <Text style={styles.subtitle}>임장 조건에 맞는 아파트를 추천할 때 활용해요.</Text>

      <View style={styles.section}>
        <Text style={styles.sectionTitle}>혼인 상태</Text>
        <View style={styles.row}>
          <OptionButton
            label="미혼"
            selected={maritalStatus === 'SINGLE'}
            onPress={() => onMaritalStatusChange('SINGLE')}
            style={styles.rowItem}
          />
          <OptionButton
            label="기혼"
            selected={maritalStatus === 'MARRIED'}
            onPress={() => onMaritalStatusChange('MARRIED')}
            style={styles.rowItem}
          />
        </View>
      </View>

      <View style={styles.section}>
        <Text style={styles.sectionTitle}>미성년 자녀가 있으신가요?</Text>
        <View style={styles.row}>
          <OptionButton
            label="없음"
            selected={hasChildren === false}
            onPress={() => onHasChildrenChange(false)}
            style={styles.rowItem}
          />
          <OptionButton
            label="있음"
            selected={hasChildren === true}
            onPress={() => onHasChildrenChange(true)}
            style={styles.rowItem}
          />
        </View>
      </View>

      <View style={styles.section}>
        <Text style={styles.sectionTitle}>차량을 소유하고 계신가요?</Text>
        <View style={styles.row}>
          <OptionButton
            label="없음"
            selected={hasVehicle === false}
            onPress={() => onHasVehicleChange(false)}
            style={styles.rowItem}
          />
          <OptionButton
            label="있음"
            selected={hasVehicle === true}
            onPress={() => onHasVehicleChange(true)}
            style={styles.rowItem}
          />
        </View>
      </View>
    </View>
  );
}

const styles = StyleSheet.create({
  container: {
    gap: 8,
  },
  title: {
    fontSize: 22,
    fontWeight: '800',
    color: TEXT_COLOR,
  },
  subtitle: {
    fontSize: 14,
    color: PLACEHOLDER_COLOR,
    marginBottom: 24,
  },
  section: {
    marginTop: 20,
    gap: 12,
  },
  sectionTitle: {
    fontSize: 16,
    fontWeight: '700',
    color: TEXT_COLOR,
  },
  row: {
    flexDirection: 'row',
    gap: 12,
  },
  rowItem: {
    flex: 1,
  },
});
