import Ionicons from '@expo/vector-icons/Ionicons';
import { Pressable, StyleSheet, Switch, Text, View } from 'react-native';

import { FACILITY_CATEGORIES, type FacilityCategory } from '@/features/map/facilityFilters';

/**
 * 지도에 표시할 시설을 고르는 패널. 임장 지도와 일반 지도가 함께 씁니다.
 *
 * 화면마다 띄우는 위치가 달라서 바깥 컨테이너의 위치는 style 로 받습니다.
 */
/**
 * 임장 핵심 카드 설명. "교통·학군·의료..." 처럼 카테고리 이름만 나열하면 실제로 뭐가
 * 뜨는 건지 감이 안 와서, 대표 시설 이름을 직접 몇 개 적어 둡니다. 카테고리 구성
 * (facilityFilters.ts 의 CATEGORY_FACILITY_MAKI) 이 크게 바뀌면 이 문구도 같이
 * 손봐야 합니다.
 */
const CORE_FACILITIES_DESCRIPTION = '역·학교·병원·마트·공원·놀이터 등 (버스는 교통 선택 시)';

interface FacilityFilterPanelProps {
  expandedCategories: FacilityCategory[];
  showAllFacilities: boolean;
  /** 임장 핵심(카테고리를 하나도 안 골랐을 때의 기본 구성) 을 껐다 켤 수 있는 스위치. */
  showCoreFacilities: boolean;
  onToggleCategory: (category: FacilityCategory) => void;
  onChangeShowAllFacilities: (value: boolean) => void;
  onChangeShowCoreFacilities: (value: boolean) => void;
  onClose: () => void;
  /** 절대 위치 등 화면별 배치. */
  style?: object;
  subtitle?: string;
}

export function FacilityFilterPanel({
  expandedCategories,
  showAllFacilities,
  showCoreFacilities,
  onToggleCategory,
  onChangeShowAllFacilities,
  onChangeShowCoreFacilities,
  onClose,
  style,
  subtitle = '카테고리를 고르면 그것만 표시돼요',
}: FacilityFilterPanelProps) {
  return (
    <View style={[styles.panel, style]}>
      <View style={styles.header}>
        <View>
          <Text style={styles.title}>지도에 표시할 시설</Text>
          <Text style={styles.subtitle}>{subtitle}</Text>
        </View>
        <Pressable
          accessibilityLabel="지도 시설 필터 닫기"
          accessibilityRole="button"
          hitSlop={10}
          onPress={onClose}
        >
          <Ionicons color="#6B7D78" name="close" size={21} />
        </Pressable>
      </View>

      <View style={styles.coreModeRow}>
        {/* 켜짐/꺼짐은 오른쪽 스위치 하나로만 보여 줍니다. 왼쪽에 체크 원까지 두면
            같은 상태를 두 번 말하게 되고, 원이 눌리는 버튼처럼 보이기도 합니다. */}
        <View style={styles.coreModeText}>
          <Text style={styles.coreModeTitle}>임장 핵심</Text>
          <Text style={styles.coreModeDescription}>{CORE_FACILITIES_DESCRIPTION}</Text>
        </View>
        <Switch
          accessibilityLabel="임장 핵심 시설 표시"
          onValueChange={onChangeShowCoreFacilities}
          thumbColor="#FFFFFF"
          trackColor={{ false: '#DCE5E2', true: '#63E69A' }}
          value={showCoreFacilities}
        />
      </View>

      <View style={styles.categoryGrid}>
        {FACILITY_CATEGORIES.map((category) => {
          const selected = expandedCategories.includes(category.id);

          return (
            <Pressable
              accessibilityRole="button"
              accessibilityState={{ selected }}
              key={category.id}
              onPress={() => onToggleCategory(category.id)}
              style={({ pressed }) => [
                styles.categoryChip,
                selected && styles.categoryChipSelected,
                pressed && styles.pressed,
              ]}
            >
              <Ionicons color={selected ? '#FFFFFF' : '#1F5F55'} name={category.icon} size={16} />
              <Text style={[styles.categoryLabel, selected && styles.categoryLabelSelected]}>
                {category.label}
              </Text>
            </Pressable>
          );
        })}
      </View>

      <View style={styles.allFacilitiesRow}>
        <View style={styles.allFacilitiesText}>
          <Text style={styles.allFacilitiesTitle}>전체 시설 보기</Text>
          <Text style={styles.allFacilitiesDescription}>음식점·카페·일반 상점 포함</Text>
        </View>
        <Switch
          accessibilityLabel="전체 시설 보기"
          onValueChange={onChangeShowAllFacilities}
          thumbColor="#FFFFFF"
          trackColor={{ false: '#DCE5E2', true: '#63E69A' }}
          value={showAllFacilities}
        />
      </View>
    </View>
  );
}

const styles = StyleSheet.create({
  panel: {
    width: 244,
    padding: 16,
    borderRadius: 20,
    backgroundColor: 'rgba(255, 255, 255, 0.97)',
    shadowColor: '#173F38',
    shadowOffset: { width: 0, height: 6 },
    shadowOpacity: 0.18,
    shadowRadius: 14,
    elevation: 8,
  },
  header: { flexDirection: 'row', justifyContent: 'space-between', gap: 8 },
  title: { color: '#173F38', fontSize: 16, fontWeight: '800' },
  subtitle: { marginTop: 3, color: '#7C8E89', fontSize: 11 },
  coreModeRow: {
    marginTop: 14,
    padding: 11,
    borderRadius: 14,
    backgroundColor: '#EEF9F2',
    flexDirection: 'row',
    alignItems: 'center',
  },
  coreModeText: { flex: 1 },
  coreModeTitle: { color: '#1F5F55', fontSize: 13, fontWeight: '800' },
  coreModeDescription: { marginTop: 2, color: '#6F817C', fontSize: 10 },
  categoryGrid: { marginTop: 12, flexDirection: 'row', flexWrap: 'wrap', gap: 7 },
  categoryChip: {
    minWidth: 64,
    paddingHorizontal: 10,
    height: 34,
    borderRadius: 17,
    borderWidth: 1,
    borderColor: '#D8E5E1',
    backgroundColor: '#FFFFFF',
    flexDirection: 'row',
    alignItems: 'center',
    justifyContent: 'center',
    gap: 5,
  },
  categoryChipSelected: { borderColor: '#1F5F55', backgroundColor: '#1F5F55' },
  categoryLabel: { color: '#1F5F55', fontSize: 12, fontWeight: '700' },
  categoryLabelSelected: { color: '#FFFFFF' },
  allFacilitiesRow: {
    marginTop: 14,
    paddingTop: 13,
    borderTopWidth: StyleSheet.hairlineWidth,
    borderTopColor: '#DCE6E3',
    flexDirection: 'row',
    alignItems: 'center',
  },
  allFacilitiesText: { flex: 1 },
  allFacilitiesTitle: { color: '#173F38', fontSize: 13, fontWeight: '800' },
  allFacilitiesDescription: { marginTop: 2, color: '#82928E', fontSize: 10 },
  pressed: { opacity: 0.82, transform: [{ scale: 0.94 }] },
});
