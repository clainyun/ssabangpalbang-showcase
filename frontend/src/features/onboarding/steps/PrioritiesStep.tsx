import { View, Text, StyleSheet } from 'react-native';

import { SelectableChip } from '@/components/SelectableChip';
import {
  TEXT_COLOR,
  PLACEHOLDER_COLOR,
  PRIMARY_COLOR,
  SOFT_GREEN_COLOR,
} from '@/constants/colors';
import type { Priority } from '@/features/onboarding/api/saveOnboarding';

export const MAX_PRIORITIES = 4;

const PRIORITIES: { value: Priority; label: string }[] = [
  { value: 'TRANSPORT', label: '🚇 교통' },
  { value: 'SAFETY', label: '🚨 안전·치안' },
  { value: 'EDUCATION', label: '🏫 교육환경' },
  { value: 'COMMERCIAL', label: '🏪 상권·생활편의' },
  { value: 'WALKABILITY', label: '🚶 보행환경' },
  { value: 'GREEN_SPACE', label: '🌳 공원·녹지' },
  { value: 'PARKING', label: '🅿️ 주차환경' },
  { value: 'NOISE', label: '🔇 소음환경' },
];

interface PrioritiesStepProps {
  priorities: Priority[];
  /** 배열 전체가 아니라 탭 한 번(토글 대상 하나)만 부모로 올립니다. 부모가
   * setState 함수형 업데이트로 처리해야 칩을 빠르게 연속으로 눌러도
   * 이전 탭의 상태 반영을 놓치지 않습니다 (prop으로 받은 배열은 렌더 시점의
   * 스냅샷이라, 여기서 직접 새 배열을 계산하면 연속 탭 시 유실될 수 있음). */
  onTogglePriority: (priority: Priority) => void;
}

export function PrioritiesStep({ priorities, onTogglePriority }: PrioritiesStepProps) {
  return (
    <View style={styles.container}>
      <View style={styles.titleRow}>
        <Text style={styles.title}>중요하게 보는 조건을 골라주세요</Text>
        {/* 선택 개수는 여기 한 곳에만 표시 — 칩마다 번호 배지를 붙이면 선택할 때
            칩 폭이 바뀌어 버튼들이 아래로 밀립니다 (마이페이지 편집과 동일한 방식). */}
        <View style={styles.countBadge}>
          <Text style={styles.countText}>
            {priorities.length}/{MAX_PRIORITIES}
          </Text>
        </View>
      </View>
      <Text style={styles.subtitle}>최대 {MAX_PRIORITIES}개까지 선택할 수 있어요.</Text>

      <View style={styles.chips}>
        {PRIORITIES.map((item) => {
          const selected = priorities.includes(item.value);
          return (
            <SelectableChip
              key={item.value}
              label={item.label}
              selected={selected}
              disabled={!selected && priorities.length >= MAX_PRIORITIES}
              onPress={() => onTogglePriority(item.value)}
            />
          );
        })}
      </View>
    </View>
  );
}

const styles = StyleSheet.create({
  container: {
    gap: 8,
  },
  titleRow: {
    flexDirection: 'row',
    alignItems: 'center',
    justifyContent: 'space-between',
    gap: 12,
  },
  title: {
    flex: 1,
    fontSize: 22,
    fontWeight: '800',
    color: TEXT_COLOR,
  },
  countBadge: {
    minWidth: 44,
    paddingHorizontal: 11,
    paddingVertical: 6,
    borderRadius: 14,
    alignItems: 'center',
    backgroundColor: SOFT_GREEN_COLOR,
  },
  countText: {
    fontSize: 13,
    fontWeight: '800',
    color: PRIMARY_COLOR,
  },
  subtitle: {
    fontSize: 14,
    color: PLACEHOLDER_COLOR,
    marginBottom: 24,
  },
  chips: {
    flexDirection: 'row',
    flexWrap: 'wrap',
    gap: 10,
  },
});
