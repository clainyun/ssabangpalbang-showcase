import Ionicons from '@expo/vector-icons/Ionicons';
import * as Crypto from 'expo-crypto';
import { LinearGradient } from 'expo-linear-gradient';
import { useRouter } from 'expo-router';
import { useEffect, useState } from 'react';
import { ActivityIndicator, Pressable, ScrollView, StyleSheet, Text, View } from 'react-native';
import Animated, {
  useAnimatedStyle,
  useSharedValue,
  withDelay,
  withRepeat,
  withSequence,
  withTiming,
} from 'react-native-reanimated';
import { useSafeAreaInsets } from 'react-native-safe-area-context';

import { appAlert } from '@/components/AppDialog';
import {
  GlassIconButton,
  GLASS_ICON_BUTTON_ICON_SIZE,
} from '@/components/GlassIconButton';
import { GlassSurface } from '@/components/GlassSurface';
import { ScreenGlowBackground } from '@/components/ScreenGlowBackground';
import { TAB_LABEL_FONT_BOLD } from '@/components/TabIcon';
import {
  BORDER_COLOR,
  DARK_GREEN_COLOR,
  ERROR_COLOR,
  MUTED_TEXT_COLOR,
  PRIMARY_COLOR,
  SOFT_GREEN_COLOR,
  SURFACE_COLOR,
} from '@/constants/colors';
import { characterDeskAvatarSource } from '@/features/character/characterDeskAvatar';
import {
  cancelFieldVisitFinish,
  FieldVisitApiError,
  voteCloseFieldVisit,
  type FieldVisitParticipantStatus,
} from '@/features/checklist/api/fieldVisit';
import { useCloseVote } from '@/features/checklist/useCloseVote';
import { useFieldVisitParticipants } from '@/features/checklist/useFieldVisitParticipants';
import { useFieldVisitWaitingReport } from '@/features/checklist/useFieldVisitWaitingReport';
import { useMembers } from '@/features/study/useStudyManagement';

/** 다음 멤버 카드가 시작하는 간격 — 전부 같은 리듬으로 들썩이면 뻣뻣해 보여서 살짝 엇갑니다. */
const BOB_STAGGER_MS = 160;

interface FieldVisitWaitingScreenProps {
  studyId: number;
}

/**
 * "임장 끝내기"는 했지만 다른 참여자가 아직 안 끝나 세션이 안 끝난 동안 보여주는 전용
 * 대기 화면입니다. 예전에는 지도 위 작은 버블(FieldVisitWaitingBubble)로만 안내했지만,
 * 강제 종료 버튼을 여기로 옮기면서 화면으로 승격했습니다.
 *
 * 멤버별 진행 상태는 useFieldVisitParticipants(아직 백엔드 미연동, docs/API.md
 * "참여자별 임장 상태 조회" 참고)로 5초마다 폴링합니다. 응답이 오기 전(또는 아직
 * 배포 전)에는 인원수 없는 안내문·캐릭터는 흔들리기만 하는 기본 모습으로 대체되고,
 * 배포되면 코드 변경 없이 실시간 인원수·완료 배지가 채워집니다.
 */
export function FieldVisitWaitingScreen({ studyId }: FieldVisitWaitingScreenProps) {
  const insets = useSafeAreaInsets();
  const router = useRouter();
  const membersQuery = useMembers(studyId);
  const participantsData = useFieldVisitParticipants(studyId, true);
  const { closeVote, refreshCloseVote } = useCloseVote(studyId);
  const [isVotingClose, setIsVotingClose] = useState(false);
  const [isCancellingFinish, setIsCancellingFinish] = useState(false);
  const waitingReport = useFieldVisitWaitingReport(studyId, true);

  useEffect(() => {
    if (waitingReport === null) return;
    router.replace({
      pathname: '/(app)/report-generating/[reportId]',
      params: { reportId: String(waitingReport.reportId) },
    } as never);
  }, [router, waitingReport]);

  const handleOpenChat = () => {
    router.push({
      pathname: '/(app)/chat/[roomId]',
      params: { roomId: String(studyId) },
    });
  };

  // 임장 지도로 돌아가면 ChecklistSheet 가 다시 이 화면으로 되돌리므로(내 임장이
  // 이미 종료 상태라서), 지도가 아니라 스터디 상세로 내보냅니다.
  const handleLeave = () => {
    router.replace({
      pathname: '/(app)/study/[id]',
      params: { id: String(studyId) },
    });
  };

  // 세션이 이미 ENDED면(리포트 생성 시작) 서버가 409로 막아주므로, 여기서는 별도
  // 확인 절차 없이 바로 요청합니다 — 되돌리는 동작이라 "끝내기"만큼 무겁지 않습니다.
  const handleCancelFinish = () => {
    if (isCancellingFinish) return;
    void (async () => {
      setIsCancellingFinish(true);
      try {
        const result = await cancelFieldVisitFinish(studyId, Crypto.randomUUID());
        router.replace({
          pathname: '/(app)/field/[sessionId]',
          params: { sessionId: String(result.sessionId), studyId: String(studyId) },
        });
      } catch (error) {
        // 서버에 아직 이 API가 배포되지 않으면 라우트를 못 찾아 일반 404가 옵니다.
        // 그때 "리소스를 찾을 수 없다"는 원문은 사용자에게 아무 의미가 없어 바꿔 줍니다.
        const isNotDeployed =
          error instanceof FieldVisitApiError && error.code === 'COMMON_RESOURCE_NOT_FOUND';
        appAlert(
          '오류',
          isNotDeployed
            ? '아직 서버에 종료 취소 기능이 배포되지 않았어요.'
            : error instanceof FieldVisitApiError
              ? error.message
              : '임장 종료 취소를 처리하지 못했습니다.',
        );
      } finally {
        setIsCancellingFinish(false);
      }
    })();
  };

  // 강제 종료는 표를 한 장 던지는 것이고, 취소 API가 없어 되돌릴 수 없습니다.
  // 과반에 도달하면 그 순간 모두의 임장이 함께 끝나므로 미리 알리고 확인받습니다.
  // (checklist/ChecklistSheet.tsx 에 있던 같은 로직을 이 화면으로 옮겼습니다.)
  const handleForceClose = () => {
    const remaining =
      closeVote === null ? 0 : Math.max(0, closeVote.requiredVoteCount - closeVote.voteCount);

    appAlert(
      '전체 임장을 종료할까요?',
      remaining <= 1
        ? '내 동의로 과반이 채워져 모든 참여자의 임장이 함께 종료됩니다. 되돌릴 수 없어요.'
        : `동의가 ${remaining}명 더 모이면 모든 참여자의 임장이 함께 종료됩니다. 한 번 낸 동의는 취소할 수 없어요.`,
      [
        { text: '취소', style: 'cancel' },
        {
          text: '동의하기',
          style: 'destructive',
          onPress: () => {
            void (async () => {
              setIsVotingClose(true);
              try {
                const result = await voteCloseFieldVisit(studyId);
                await refreshCloseVote();

                if (result.sessionEnded) {
                  if (result.reportId) {
                    router.replace({
                      pathname: '/(app)/report-generating/[reportId]',
                      params: { reportId: String(result.reportId) },
                    } as never);
                    return;
                  }
                  appAlert('임장 종료', '과반수 동의로 전체 임장이 종료됐어요. 수고하셨습니다!');
                  return;
                }

                appAlert(
                  '종료 요청 완료',
                  `동의 ${result.voteCount}/${result.requiredVoteCount}명이에요. 과반이 되면 자동으로 종료됩니다.`,
                );
              } catch (error) {
                appAlert(
                  '오류',
                  error instanceof FieldVisitApiError
                    ? error.message
                    : '종료 요청을 보내지 못했습니다.',
                );
              } finally {
                setIsVotingClose(false);
              }
            })();
          },
        },
      ],
    );
  };

  // 참여자 API(useFieldVisitParticipants)가 아직 응답을 못 주면(연동 전·폴링 대기)
  // status 는 null 로 두고 캐릭터는 그냥 흔들리기만 하는 기본 모습으로 보여줍니다.
  // 응답이 오면 다음 렌더부터 완료 배지·인원수가 자동으로 채워집니다.
  const statusByMemberId = new Map(
    (participantsData?.participants ?? []).map((participant) => [
      participant.memberId,
      participant.status,
    ]),
  );
  const waitingMembers = (membersQuery.data?.members ?? []).map((member) => ({
    memberId: member.memberId,
    nickname: member.nickname,
    selectedCharacterId: member.selectedCharacterId,
    status: statusByMemberId.get(member.memberId) ?? null,
  }));
  const waitingCount = participantsData
    ? participantsData.inProgressCount + participantsData.notJoinedCount
    : null;

  return (
    <View style={styles.screen}>
      <ScreenGlowBackground color="#D8F6E5" />

      {/* 이 화면은 router.replace 로 진입해 뒤로 갈 스택이 없습니다. 팀원이 아무도
          종료하지 않으면 과반 투표도 성립하지 않아, 나갈 길이 없으면 앱 안에 갇힙니다.
          임장은 그대로 대기 상태로 두고 스터디 화면으로만 빠져나가게 합니다. */}
      <GlassIconButton
        accessibilityLabel="스터디 화면으로 나가기"
        onPress={handleLeave}
        style={[styles.leaveButton, { top: insets.top + 12 }]}
      >
        <Ionicons color={DARK_GREEN_COLOR} name="chevron-back" size={25} />
      </GlassIconButton>

      <GlassIconButton
        accessibilityLabel="스터디 채팅 열기"
        onPress={handleOpenChat}
        style={[styles.chatButton, { top: insets.top + 12 }]}
      >
        <Ionicons
          color={DARK_GREEN_COLOR}
          name="chatbubble-ellipses-outline"
          size={GLASS_ICON_BUTTON_ICON_SIZE}
        />
      </GlassIconButton>

      <ScrollView
        contentContainerStyle={[styles.content, { paddingTop: insets.top + 88 }]}
        style={styles.scrollArea}
      >
        <Text style={styles.title}>
          {waitingCount !== null && waitingCount > 0
            ? `다른 팀원 ${waitingCount}명을\n기다리고 있어요`
            : '다른 팀원을\n기다리고 있어요'}
        </Text>
        <Text style={styles.subtitle}>모두 임장을 종료해야 리포트가 생성돼요</Text>

        <View style={styles.memberGrid}>
          {membersQuery.isLoading ? (
            <ActivityIndicator color={PRIMARY_COLOR} style={styles.memberLoader} />
          ) : (
            waitingMembers.map((member, index) => (
              <MemberDeskCard bobDelay={index * BOB_STAGGER_MS} key={member.memberId} member={member} />
            ))
          )}
        </View>

        <View style={styles.infoCard}>
          <Ionicons color={PRIMARY_COLOR} name="time-outline" size={18} style={styles.infoIcon} />
          <Text style={styles.infoText}>
            {'팀원이 너무 안 오면 강제 종료를 눌러요.\n과반수가 강제 종료를 누르면 리포트가 생성됩니다.'}
          </Text>
        </View>
      </ScrollView>

      {/* 강제 종료·취소는 화면을 아무리 스크롤해도 항상 손닿는 자리에 있어야 하는
          핵심 동작이라, 스크롤 영역 밖 하단에 고정합니다. */}
      <View style={[styles.footer, { paddingBottom: insets.bottom + 20 }]}>
        {closeVote !== null && (
          <>
            <Pressable
              accessibilityLabel="임장 강제 종료 요청"
              accessibilityRole="button"
              accessibilityState={{ disabled: !closeVote.canVote || isVotingClose }}
              disabled={!closeVote.canVote || isVotingClose}
              onPress={handleForceClose}
              style={({ pressed }) => [
                styles.forceCloseButton,
                closeVote.hasVoted && styles.forceCloseButtonVoted,
                !closeVote.canVote && !closeVote.hasVoted && styles.disabled,
                pressed && styles.pressed,
              ]}
            >
              {!closeVote.hasVoted && (
                <>
                  {/* 파괴적 동작이라는 걸 색으로 분명히 — 옅은 코랄 그라데이션. */}
                  <GlassSurface radius={26} tint="#FDECEC" tintOpacity={0.5} intensity={26} />
                  <LinearGradient
                    colors={['rgba(255,255,255,0.62)', 'rgba(250,205,199,0.58)']}
                    pointerEvents="none"
                    style={StyleSheet.absoluteFill}
                  />
                </>
              )}
              {isVotingClose ? (
                <ActivityIndicator
                  color={closeVote.hasVoted ? MUTED_TEXT_COLOR : ERROR_COLOR}
                  size="small"
                />
              ) : (
                <>
                  <Ionicons
                    color={closeVote.hasVoted ? MUTED_TEXT_COLOR : ERROR_COLOR}
                    name="flag-outline"
                    size={16}
                  />
                  <Text
                    style={[
                      styles.forceCloseButtonText,
                      closeVote.hasVoted && styles.forceCloseButtonTextVoted,
                    ]}
                  >
                    {closeVote.hasVoted ? '종료 요청함' : '강제 종료 요청하기'}
                  </Text>
                </>
              )}
            </Pressable>
            <Text style={styles.voteCountText}>
              강제 종료 동의 {closeVote.voteCount}/{closeVote.requiredVoteCount}명
            </Text>
          </>
        )}

        {/* 되돌릴 수 있는 가벼운 동작이라 카드형 버튼 대신 텍스트 링크로 —
            밑줄로 누를 수 있는 동작임을 표시합니다. */}
        <Pressable
          accessibilityLabel="임장 종료 취소하고 돌아가기"
          accessibilityRole="button"
          accessibilityState={{ disabled: isCancellingFinish }}
          disabled={isCancellingFinish}
          hitSlop={8}
          onPress={handleCancelFinish}
          style={({ pressed }) => [styles.cancelFinishButton, pressed && styles.pressed]}
        >
          {isCancellingFinish ? (
            <ActivityIndicator color={DARK_GREEN_COLOR} size="small" />
          ) : (
            <>
              <Ionicons color={DARK_GREEN_COLOR} name="arrow-undo-outline" size={14} />
              <Text style={styles.cancelFinishText}>임장 종료 취소하고 돌아가기</Text>
            </>
          )}
        </Pressable>
      </View>
    </View>
  );
}

interface WaitingMember {
  memberId: number;
  nickname: string;
  selectedCharacterId: string;
  /** null 이면 아직 참여자 API 응답 전(연동 전 포함) — 완료 여부를 모르니 흔들리는
   * 기본 모습으로 둡니다. */
  status: FieldVisitParticipantStatus | null;
}

function MemberDeskCard({ member, bobDelay }: { member: WaitingMember; bobDelay: number }) {
  const isEnded = member.status === 'ENDED';
  const bob = useSharedValue(0);

  useEffect(() => {
    // 이미 끝낸 사람은 "대기 중"이 아니니 들썩임을 멈추고 가만히 둡니다.
    if (isEnded) {
      bob.set(withTiming(0, { duration: 200 }));
      return;
    }
    bob.set(
      withDelay(
        bobDelay,
        withRepeat(
          withSequence(withTiming(-6, { duration: 900 }), withTiming(0, { duration: 900 })),
          -1,
          true,
        ),
      ),
    );
  }, [bob, bobDelay, isEnded]);

  const bobStyle = useAnimatedStyle(() => ({ transform: [{ translateY: bob.get() }] }));

  return (
    <View style={[styles.memberCard, isEnded && styles.memberCardEnded]}>
      <Animated.Image
        resizeMode="contain"
        source={characterDeskAvatarSource(member.selectedCharacterId)}
        style={[styles.memberImage, bobStyle]}
      />
      {isEnded && (
        <View style={styles.memberDoneBadge}>
          <Ionicons color="#FFFFFF" name="checkmark" size={12} />
        </View>
      )}
      <Text numberOfLines={1} style={styles.memberName}>
        {member.nickname}
      </Text>
    </View>
  );
}

const styles = StyleSheet.create({
  screen: {
    flex: 1,
    backgroundColor: SURFACE_COLOR,
  },
  // 모양·크기·재질은 GlassIconButton 이 갖고, 여기서는 놓을 자리만 정합니다.
  leaveButton: { position: 'absolute', left: 16, zIndex: 2 },
  chatButton: { position: 'absolute', right: 16, zIndex: 2 },
  scrollArea: { flex: 1 },
  content: {
    paddingHorizontal: 24,
    paddingBottom: 24,
    alignItems: 'center',
  },
  // 강제 종료·취소 버튼 묶음. 스크롤 밖에 고정해 내용이 길어져도 항상 보이게 합니다.
  footer: {
    paddingHorizontal: 24,
    paddingTop: 16,
    alignItems: 'center',
    borderTopWidth: 1,
    borderTopColor: BORDER_COLOR,
    backgroundColor: SURFACE_COLOR,
  },
  title: {
    fontSize: 26,
    lineHeight: 34,
    fontFamily: TAB_LABEL_FONT_BOLD,
    textAlign: 'center',
    color: DARK_GREEN_COLOR,
  },
  subtitle: {
    marginTop: 8,
    fontSize: 14,
    color: MUTED_TEXT_COLOR,
  },
  memberGrid: {
    marginTop: 32,
    flexDirection: 'row',
    flexWrap: 'wrap',
    justifyContent: 'center',
    gap: 18,
  },
  memberLoader: { marginTop: 12 },
  memberCard: {
    width: 108,
    alignItems: 'center',
    gap: 6,
  },
  // 이미 끝낸 사람은 흐리게 낮춰서, 아직 흔들리고 있는(대기 중) 캐릭터와 한눈에
  // 구분되게 합니다.
  memberCardEnded: {
    opacity: 0.45,
  },
  memberImage: {
    width: 96,
    height: 96,
  },
  memberDoneBadge: {
    position: 'absolute',
    top: 2,
    right: 2,
    width: 20,
    height: 20,
    borderRadius: 10,
    alignItems: 'center',
    justifyContent: 'center',
    backgroundColor: PRIMARY_COLOR,
  },
  memberName: {
    fontSize: 13,
    fontWeight: '700',
    color: DARK_GREEN_COLOR,
  },
  infoCard: {
    marginTop: 36,
    width: '100%',
    flexDirection: 'row',
    gap: 10,
    padding: 16,
    borderRadius: 16,
    backgroundColor: SOFT_GREEN_COLOR,
  },
  infoIcon: { marginTop: 1 },
  infoText: {
    flex: 1,
    fontSize: 13,
    lineHeight: 19,
    color: DARK_GREEN_COLOR,
  },
  forceCloseButton: {
    width: '100%',
    height: 52,
    overflow: 'hidden',
    flexDirection: 'row',
    alignItems: 'center',
    justifyContent: 'center',
    gap: 8,
    borderRadius: 26,
    boxShadow: '0px 4px 14px rgba(16, 39, 30, 0.16)',
  },
  // 이미 표를 던진 뒤에는 광택을 빼고 테두리만 남겨 눌리지 않는 상태로 보이게 합니다.
  forceCloseButtonVoted: {
    borderWidth: 1,
    borderColor: BORDER_COLOR,
    boxShadow: 'none',
  },
  forceCloseButtonText: {
    fontSize: 14,
    fontWeight: '800',
    color: ERROR_COLOR,
  },
  forceCloseButtonTextVoted: {
    color: MUTED_TEXT_COLOR,
  },
  voteCountText: {
    marginTop: 10,
    fontSize: 12,
    fontWeight: '600',
    color: MUTED_TEXT_COLOR,
  },
  cancelFinishButton: {
    marginTop: 14,
    paddingVertical: 10,
    paddingHorizontal: 12,
    flexDirection: 'row',
    alignItems: 'center',
    justifyContent: 'center',
    gap: 6,
  },
  cancelFinishText: {
    fontSize: 14,
    fontWeight: '800',
    color: DARK_GREEN_COLOR,
    textDecorationLine: 'underline',
  },
  disabled: { opacity: 0.45 },
  pressed: { opacity: 0.82 },
});
