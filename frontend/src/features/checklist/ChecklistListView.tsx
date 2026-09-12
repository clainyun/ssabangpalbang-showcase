import Feather from '@expo/vector-icons/Feather';
import Ionicons from '@expo/vector-icons/Ionicons';
import { useMemo } from 'react';
import { Pressable, StyleSheet, Text, View } from 'react-native';

import { GlossyFill } from '@/components/GlossyFill';
import { TAB_LABEL_FONT_BOLD } from '@/components/TabIcon';
import {
  BORDER_COLOR,
  BUTTON_BACKGROUND_COLOR,
  CALENDAR_SATURDAY_COLOR,
  DARK_GREEN_COLOR,
  MUTED_TEXT_COLOR,
  PLACEHOLDER_COLOR,
  PRIMARY_COLOR,
  SOFT_GREEN_COLOR,
} from '@/constants/colors';
import type { ChecklistCategory, ChecklistItem } from '@/features/checklist/api/checklist';
import type { FieldVisitCloseVoteStatus } from '@/features/checklist/api/fieldVisit';
import type { FieldVisitRoute } from '@/features/checklist/api/fieldVisitRoute';

/**
 * 화면에 그릴 한 묶음. 경로가 있으면 경유지 순서(sequence)대로, 없으면 카테고리대로
 * 만듭니다. sequence 가 null 인 묶음은 번호 배지를 달지 않습니다.
 */
interface ChecklistGroup {
  key: string;
  sequence: number | null;
  /** 카테고리·기타 묶음의 제목. 경유지 묶음은 번호만 쓰므로 보지 않습니다. */
  label: string;
  items: ChecklistItem[];
}

/**
 * 경유지 순서로 묶습니다.
 *
 * 경로 응답의 isCompleted·recordCount 는 조회 시점 스냅샷이라 체크를 눌러도 갱신되지
 * 않습니다. 그래서 경로에서는 "어느 경유지에 어떤 항목이 붙는지"(순서·묶음)만 쓰고,
 * 항목의 실제 상태는 항상 체크리스트 쪽 값을 씁니다.
 *
 * 경유지에 배정되지 않은 항목은 경로 응답에 아예 없으므로, 체크리스트 전체와 비교해
 * 남은 항목을 맨 아래 "기타"로 모읍니다.
 */
function buildGroups(categories: ChecklistCategory[], route: FieldVisitRoute | null): ChecklistGroup[] {
  const categoryGroups = categories.map((category) => ({
    key: `category:${category.category}`,
    sequence: null,
    label: category.category,
    items: category.items,
  }));

  if (route === null) return categoryGroups;

  const liveItems = categories.flatMap((category) => category.items);
  const byId = new Map(liveItems.map((item) => [item.checklistItemId, item]));
  const assigned = new Set<number>();

  const waypointGroups: ChecklistGroup[] = [...route.waypoints]
    .sort((left, right) => left.sequence - right.sequence)
    .map((waypoint) => {
      const items = waypoint.myItems
        .map((myItem) => byId.get(myItem.checklistItemId))
        .filter((item): item is ChecklistItem => item !== undefined);

      for (const item of items) assigned.add(item.checklistItemId);

      return {
        key: `waypoint:${waypoint.waypointId}`,
        sequence: waypoint.sequence,
        label: waypoint.name,
        items,
      };
    })
    .filter((group) => group.items.length > 0);

  // 경로는 있지만 내 항목이 배정된 경유지가 하나도 없으면(다른 참여자 몫만 있는 경우)
  // 전부 "기타"가 되어 카테고리 묶음보다 못해지므로 그냥 카테고리로 보여 줍니다.
  if (waypointGroups.length === 0) return categoryGroups;

  const leftover = liveItems.filter((item) => !assigned.has(item.checklistItemId));
  if (leftover.length > 0) {
    waypointGroups.push({
      key: 'unassigned',
      sequence: null,
      label: '기타',
      items: leftover,
    });
  }

  return waypointGroups;
}

/** 나브바 탭 라벨과 같은 글씨체·굵기. 색은 검정으로 바꿔 씁니다(핸들과는 다른 결정). */
const DETAIL_TITLE_FONT = TAB_LABEL_FONT_BOLD;
const DETAIL_TITLE_COLOR = '#000000';

interface ChecklistListViewProps {
  categories: ChecklistCategory[];
  completedCount: number;
  totalCount: number;
  readOnly: boolean;
  pendingItemIds: number[];
  onToggleItem: (checklistItemId: number, isCompleted: boolean) => void;
  onOpenRecord: (checklistItemId: number) => void;
  onFinishVisit: () => void;
  /** 과반수 종료 투표 현황. 조회 실패·비참여 상태면 null 이고 그 줄을 감춥니다.
   * 강제 종료는 이 목록이 아니라 전용 대기 화면(field-visit-waiting/[studyId].tsx)에서
   * 하므로, 여기서는 동의 인원만 정보로 보여주고 투표 버튼은 없습니다. */
  closeVote: FieldVisitCloseVoteStatus | null;
  /** 팔방이 추천 경로. 있으면 경유지 순서로, 없으면 카테고리로 묶습니다. */
  route: FieldVisitRoute | null;
  isGeneratingRoute: boolean;
  onGenerateRoute: () => void;
  /**
   * 지도에서 탭한 경유지. 값이 있으면 전체 목록 대신 그 경유지의 항목만 보여 줍니다.
   * (지도의 점 마커 → 그 지점 체크리스트 흐름.)
   */
  focusedWaypointId?: number | null;
  onCloseFocusedWaypoint?: () => void;
}

export function ChecklistListView({
  categories,
  completedCount,
  totalCount,
  readOnly,
  pendingItemIds,
  onToggleItem,
  onOpenRecord,
  onFinishVisit,
  closeVote,
  route,
  focusedWaypointId,
  onCloseFocusedWaypoint,
}: ChecklistListViewProps) {
  const progress = totalCount === 0 ? 0 : completedCount / totalCount;
  const groups = useMemo(() => buildGroups(categories, route), [categories, route]);

  // 한 항목 줄(체크박스 + 제목/부제 + 기록 버튼). 전체 목록과 경유지 포커스 뷰가 같은
  // 모양을 쓰도록 여기서 한 번만 정의합니다.
  const renderItemRow = (item: ChecklistItem) => {
    const isPending = pendingItemIds.includes(item.checklistItemId);

    return (
      <View key={item.checklistItemId} style={styles.itemRow}>
        <Pressable
          accessibilityLabel={`${item.title} ${item.isCompleted ? '완료 취소' : '완료로 표시'}`}
          accessibilityRole="checkbox"
          accessibilityState={{ checked: item.isCompleted, disabled: readOnly }}
          disabled={readOnly}
          hitSlop={8}
          onPress={() => onToggleItem(item.checklistItemId, !item.isCompleted)}
          style={[styles.checkbox, item.isCompleted && styles.checkboxChecked]}
        >
          {item.isCompleted ? (
            // 완료: 옅은 민트 배경(checkboxChecked) 위에 브랜드 그린 체크.
            // 광택(GlossyFill) 없이 평평하게 채워 눈에 거슬리는 반사광을 없앤다.
            <Ionicons color={PRIMARY_COLOR} name="checkmark" size={19} />
          ) : (
            // 미체크 상태 힌트: 연한 회색 체크마크를 흐릿하게 얹어 "여기를 체크"임을
            // 알리되, 완료(민트 배경+초록 체크)와 확실히 구분되게 한다.
            <Ionicons color={PLACEHOLDER_COLOR} name="checkmark" size={19} />
          )}
        </Pressable>

        <View style={styles.itemTextColumn}>
          <View style={styles.itemTitleRow}>
            <Text style={styles.itemTitle}>{item.title}</Text>
            {/* 서버 저장이 네트워크 문제로 아직 못 나간 항목. 오프라인 처리(FE-015). */}
            {isPending && (
              <Ionicons
                color={MUTED_TEXT_COLOR}
                name="cloud-offline-outline"
                size={13}
                style={styles.pendingIcon}
              />
            )}
          </View>
          {item.subtitle ? <Text style={styles.itemSubtitle}>{item.subtitle}</Text> : null}
        </View>

        <Pressable
          accessibilityLabel={`${item.title} ${readOnly ? '기록 보기' : '기록 남기기'}`}
          accessibilityRole="button"
          onPress={() => onOpenRecord(item.checklistItemId)}
          style={({ pressed }) => [styles.recordButton, pressed && styles.pressed]}
        >
          {/* 체크(초록)와 겹치지 않는 파스텔 파랑 구슬 질감. */}
          <GlossyFill
            base="#DCE6FB"
            glossOpacity={0.6}
            light="#FFFFFF"
            radius={RECORD_BUTTON_SIZE / 2}
          />
          {readOnly ? (
            <Ionicons color={CALENDAR_SATURDAY_COLOR} name="eye-outline" size={15} />
          ) : (
            // 커뮤니티 글쓰기 버튼(community.tsx의 HeaderAction)과 같은 연필 아이콘.
            <Feather color={CALENDAR_SATURDAY_COLOR} name="edit-3" size={15} />
          )}
          {item.recordCount > 0 && (
            <View style={styles.recordCountBadge}>
              <Text style={styles.recordCountText}>{item.recordCount}</Text>
            </View>
          )}
        </Pressable>
      </View>
    );
  };

  // 지도에서 탭한 경유지 하나만 보여 주는 포커스 뷰. 경로가 있고 그 경유지에 내 항목이
  // 있을 때만 성립하므로, 못 찾으면(경로 갱신 중 등) 전체 목록으로 되돌아갑니다.
  const focusedGroup =
    focusedWaypointId == null
      ? null
      : (groups.find((group) => group.key === `waypoint:${focusedWaypointId}`) ?? null);

  if (focusedWaypointId != null && focusedGroup !== null) {
    const doneCount = focusedGroup.items.filter((item) => item.isCompleted).length;

    return (
      <View style={styles.container}>
        <View style={styles.focusedHeader}>
          <Pressable
            accessibilityLabel="전체 체크리스트로 돌아가기"
            accessibilityRole="button"
            hitSlop={8}
            onPress={onCloseFocusedWaypoint}
            style={({ pressed }) => [styles.backButton, pressed && styles.pressed]}
          >
            <Ionicons color={MUTED_TEXT_COLOR} name="chevron-back" size={20} />
          </Pressable>
          <View style={styles.focusedTitleColumn}>
            <Text numberOfLines={1} style={styles.focusedTitle}>
              {focusedGroup.label}
            </Text>
            <Text style={styles.focusedSubtitle}>
              이 지점 {doneCount}/{focusedGroup.items.length} 완료
            </Text>
          </View>
        </View>

        {readOnly && (
          <Text style={styles.readOnlyNotice}>
            종료된 임장입니다. 체크와 기록은 더 이상 바꿀 수 없어요.
          </Text>
        )}

        <View style={styles.focusedItems}>{focusedGroup.items.map(renderItemRow)}</View>
      </View>
    );
  }

  // 투표할 수도 없고 이미 던진 표도 없으면(= 참여자가 아니거나 세션이 안 돌고 있음)
  // 남의 임장에 대한 정보라 아예 감춥니다.
  const showCloseVoteInfo =
    !readOnly && closeVote !== null && (closeVote.canVote || closeVote.hasVoted);

  return (
    <View style={styles.container}>
      <View style={styles.progressHeader}>
        <Text style={styles.progressTitle}>
          오늘의 체크리스트{' '}
          <Text style={styles.progressCount}>
            {completedCount} / {totalCount} 완료
          </Text>
        </Text>
        {/* 종료된 임장은 이미 읽기 전용이라 다시 끝낼 필요가 없어 숨깁니다. */}
        {!readOnly && (
          <Pressable
            accessibilityLabel="임장 끝내기"
            accessibilityRole="button"
            onPress={onFinishVisit}
            style={({ pressed }) => [styles.finishButton, pressed && styles.pressed]}
          >
            {/* 홈 화면 "폭염주의보" 배지와 같은 계열이되, 이 목록 안에서 혼자 너무
                진하게 튀지 않도록 살짝 밝혔습니다. */}
            <GlossyFill base="#DE7D67" glossOpacity={0.45} light="#F2AC9A" radius={999} />
            <Ionicons color="#FFFFFF" name="flag-outline" size={12} />
            <Text style={styles.finishButtonText}>임장 끝내기</Text>
          </Pressable>
        )}
      </View>
      <View style={styles.progressTrack}>
        <View style={[styles.progressFill, { width: `${Math.round(progress * 100)}%` }]} />
      </View>

      {/* 강제 종료 "동의 인원"만 정보로 보여줍니다 — 투표 버튼은 내 임장을 끝내고
          다른 참여자를 기다릴 때 보이는 전용 화면(field-visit-waiting)에 있습니다. */}
      {showCloseVoteInfo && closeVote !== null && (
        <View style={styles.forceCloseRow}>
          <Ionicons color={MUTED_TEXT_COLOR} name="people-outline" size={12} />
          <Text style={styles.forceCloseCount}>
            강제 종료 동의 {closeVote.voteCount}/{closeVote.requiredVoteCount}명
          </Text>
        </View>
      )}

      {readOnly && (
        <Text style={styles.readOnlyNotice}>
          종료된 임장입니다. 체크와 기록은 더 이상 바꿀 수 없어요.
        </Text>
      )}

      {groups.map((group) => (
        <View key={group.key} style={styles.categorySection}>
          {/* 경로가 있으면 group.label 이 경유지 장소명, 없으면 카테고리명입니다.
              순서 번호 대신 장소명으로 묶음을 구분합니다(지도 마커도 이름표로 표시). */}
          <Text style={styles.categoryLabel}>{group.label}</Text>
          {group.items.map(renderItemRow)}
        </View>
      ))}
    </View>
  );
}

// 체크(완료 표시)가 더 중요한 동작이라 + 버튼보다 크게 둡니다.
const CHECKBOX_SIZE = 34;
const RECORD_BUTTON_SIZE = 26;

const styles = StyleSheet.create({
  container: { gap: 4 },
  progressHeader: {
    flexDirection: 'row',
    alignItems: 'baseline',
    justifyContent: 'space-between',
    gap: 8,
  },
  progressTitle: {
    fontSize: 15,
    fontFamily: DETAIL_TITLE_FONT,
    color: DETAIL_TITLE_COLOR,
  },
  progressCount: {
    fontSize: 13,
    fontWeight: '600',
    color: PRIMARY_COLOR,
  },
  // 배경은 GlossyFill(구슬 질감)이 그림.
  finishButton: {
    flexDirection: 'row',
    alignItems: 'center',
    gap: 4,
    paddingHorizontal: 12,
    paddingVertical: 6,
    borderRadius: 999,
    boxShadow: '0px 3px 10px rgba(16, 39, 30, 0.2)',
  },

  forceCloseRow: {
    flexDirection: 'row',
    alignItems: 'center',
    gap: 6,
    marginTop: 10,
  },
  forceCloseCount: {
    fontSize: 12,
    fontWeight: '600',
    color: MUTED_TEXT_COLOR,
  },

  finishButtonText: {
    fontSize: 11,
    fontWeight: '700',
    color: '#FFFFFF',
  },
  progressTrack: {
    marginTop: 8,
    height: 6,
    borderRadius: 3,
    backgroundColor: SOFT_GREEN_COLOR,
    overflow: 'hidden',
  },
  progressFill: {
    height: '100%',
    borderRadius: 3,
    backgroundColor: PRIMARY_COLOR,
  },
  readOnlyNotice: {
    marginTop: 10,
    fontSize: 12,
    color: MUTED_TEXT_COLOR,
  },
  categorySection: {
    marginTop: 18,
    gap: 10,
  },
  // 지도 점 → 경유지 포커스 뷰 헤더(뒤로가기 + 순서 배지 + 장소명/진행도).
  focusedHeader: {
    flexDirection: 'row',
    alignItems: 'center',
    gap: 10,
    marginBottom: 6,
  },
  backButton: {
    width: 30,
    height: 30,
    alignItems: 'center',
    justifyContent: 'center',
    marginLeft: -6,
  },
  focusedTitleColumn: { flex: 1 },
  focusedTitle: {
    fontSize: 16,
    fontFamily: DETAIL_TITLE_FONT,
    color: DETAIL_TITLE_COLOR,
  },
  focusedSubtitle: {
    marginTop: 2,
    fontSize: 12,
    fontWeight: '600',
    color: PRIMARY_COLOR,
  },
  focusedItems: {
    marginTop: 14,
    gap: 10,
  },
  categoryLabel: {
    fontSize: 13,
    fontWeight: '700',
    color: MUTED_TEXT_COLOR,
  },
  itemRow: {
    flexDirection: 'row',
    // 체크박스(34)·+ 버튼(26)이 제목+부제 두 줄짜리 텍스트보다 커서, flex-start로
    // 위쪽 기준 정렬하면 버튼들이 글씨보다 위로 붕 떠 보였습니다. 텍스트 블록
    // 높이 기준으로 가운데 정렬해 셋이 한 줄처럼 자연스럽게 맞습니다.
    alignItems: 'center',
    gap: 12,
  },
  checkbox: {
    width: CHECKBOX_SIZE,
    height: CHECKBOX_SIZE,
    borderRadius: CHECKBOX_SIZE / 2,
    borderWidth: 2,
    borderColor: BORDER_COLOR,
    alignItems: 'center',
    justifyContent: 'center',
  },
  // 완료 상태: 옅은 민트로 평평하게 채우고 테두리도 같은 톤. (광택 GlossyFill 제거)
  checkboxChecked: {
    backgroundColor: BUTTON_BACKGROUND_COLOR,
    borderColor: BUTTON_BACKGROUND_COLOR,
  },
  itemTextColumn: { flex: 1, gap: 2 },
  itemTitleRow: { flexDirection: 'row', alignItems: 'center', gap: 6 },
  itemTitle: {
    fontSize: 15,
    fontFamily: DETAIL_TITLE_FONT,
    color: DETAIL_TITLE_COLOR,
  },
  pendingIcon: { marginTop: 1 },
  itemSubtitle: {
    fontSize: 12,
    color: MUTED_TEXT_COLOR,
    lineHeight: 17,
  },
  // 배경은 GlossyFill(파스텔 파랑 구슬 질감)이 그림.
  recordButton: {
    width: RECORD_BUTTON_SIZE,
    height: RECORD_BUTTON_SIZE,
    borderRadius: RECORD_BUTTON_SIZE / 2,
    alignItems: 'center',
    justifyContent: 'center',
  },
  recordCountBadge: {
    position: 'absolute',
    top: -4,
    right: -4,
    minWidth: 16,
    height: 16,
    paddingHorizontal: 3,
    borderRadius: 8,
    backgroundColor: DARK_GREEN_COLOR,
    alignItems: 'center',
    justifyContent: 'center',
  },
  recordCountText: {
    fontSize: 10,
    fontWeight: '700',
    color: '#FFFFFF',
  },
  pressed: { opacity: 0.82, transform: [{ scale: 0.94 }] },
});
