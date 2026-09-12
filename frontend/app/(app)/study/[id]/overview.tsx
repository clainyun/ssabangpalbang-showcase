import Ionicons from '@expo/vector-icons/Ionicons';
import { useQueryClient } from '@tanstack/react-query';
import { LinearGradient } from 'expo-linear-gradient';
import { router, useFocusEffect, useLocalSearchParams } from 'expo-router';
import { useCallback, useMemo, useState } from 'react';
import {
  ActivityIndicator,
  Image,
  Pressable,
  ScrollView,
  StyleSheet,
  Text,
  TextInput,
  View,
} from 'react-native';
import { useSafeAreaInsets } from 'react-native-safe-area-context';

import { appAlert } from '@/components/AppDialog';
import { GlassSurface } from '@/components/GlassSurface';
import { ScreenGlowBackground } from '@/components/ScreenGlowBackground';
import {
  BORDER_COLOR,
  DARK_GREEN_COLOR,
  MUTED_TEXT_COLOR,
  PLACEHOLDER_COLOR,
  PRIMARY_COLOR,
  SOFT_GREEN_COLOR,
  SURFACE_COLOR,
  TEXT_COLOR,
} from '@/constants/colors';
import { formatDateTime } from '@/features/apartment/format';
import type { StudyMemberSummary } from '@/features/study/api/getStudyDetail';
import { StudyApiError, type StudyNotice } from '@/features/study/api/types';
import { flattenUniqueNotices } from '@/features/study/studyNoticePagination';
import { studyDetailQueryKey, useStudyDetail } from '@/features/study/useStudyDetail';
import {
  useCreateNotice,
  useDeleteNotice,
  useKickMember,
  useNotices,
  useUpdateStudyDetails,
  useUpdateNotice,
} from '@/features/study/useStudyManagement';
import { refetchOnFocusNow } from '@/lib/refetchOnFocusIfStale';

const NOTICE_MAX_LENGTH = 2000;
const STUDY_TITLE_MAX_LENGTH = 200;
const STUDY_GOAL_MAX_LENGTH = 300;
const STUDY_INTRO_MAX_LENGTH = 1000;
const INVISIBLE_TEXT_PATTERN = /[\s\p{Cf}]/gu;
type OverviewTab = 'GOAL' | 'NOTICE' | 'MEMBER';

const MEMBER_FACE_SOURCES = {
  PALBANG: require('../../../../assets/images/characters/face/plain_f_1.png'),
  PALBANG_RABBIT: require('../../../../assets/images/characters/face/rabbit_f_1.png'),
  PALBANG_DOG: require('../../../../assets/images/characters/face/dog_f_1.png'),
} as const;

function memberFaceSource(characterId: string) {
  return (
    MEMBER_FACE_SOURCES[characterId as keyof typeof MEMBER_FACE_SOURCES] ??
    MEMBER_FACE_SOURCES.PALBANG
  );
}

function parseInitialTab(value: string | undefined): OverviewTab {
  if (value === 'NOTICE' || value === 'MEMBER') return value;
  return 'GOAL';
}

function hasVisibleText(value: string): boolean {
  return value.replace(INVISIBLE_TEXT_PATTERN, '').length > 0;
}

function goBack() {
  if (router.canGoBack()) router.back();
  else router.replace('/(app)/(tabs)/home');
}

export default function StudyOverviewScreen() {
  const params = useLocalSearchParams<{ id: string; tab?: string }>();
  const studyId = Number(params.id);
  const insets = useSafeAreaInsets();
  const queryClient = useQueryClient();
  const [tab, setTab] = useState<OverviewTab>(() => parseInitialTab(params.tab));
  const [noticeDraft, setNoticeDraft] = useState<
    { mode: 'create' } | { mode: 'edit'; noticeId: number } | null
  >(null);
  const [noticeContent, setNoticeContent] = useState('');
  const [isDetailsEditing, setIsDetailsEditing] = useState(false);
  const [titleDraft, setTitleDraft] = useState('');
  const [goalDraft, setGoalDraft] = useState('');
  const [introDraft, setIntroDraft] = useState('');

  const { data: study, isLoading, refetch } = useStudyDetail(studyId);
  const noticesQuery = useNotices(studyId, study?.isMember ?? false);
  const createNoticeMutation = useCreateNotice(studyId);
  const updateNoticeMutation = useUpdateNotice(studyId);
  const deleteNoticeMutation = useDeleteNotice(studyId);
  const updateStudyDetailsMutation = useUpdateStudyDetails(studyId);
  const kickMemberMutation = useKickMember(studyId);
  const notices = useMemo(
    () => flattenUniqueNotices(noticesQuery.data?.pages ?? []),
    [noticesQuery.data?.pages],
  );

  // 스터디장이 임장 시작 전 일반 멤버를 강퇴합니다(백엔드 DELETE /studies/{id}/members/{memberId}).
  // 노출 조건은 서버 권위 플래그 permissions.canManageMembers 를 그대로 따릅니다(스터디장 +
  // 임장 시작 전). 프로필 이동은 카드 탭으로 유지하고, 강퇴는 별도 버튼으로 둡니다.
  const handleKickMember = useCallback(
    (member: StudyMemberSummary) => {
      if (kickMemberMutation.isPending) return;
      appAlert(
        '멤버 강퇴',
        `${member.nickname}님을 스터디에서 내보낼까요? 되돌릴 수 없습니다.`,
        [
          { text: '취소', style: 'cancel' },
          {
            text: '강퇴',
            style: 'destructive',
            onPress: () =>
              kickMemberMutation.mutate(member.memberId, {
                onError: (error) =>
                  appAlert(
                    '강퇴하지 못했습니다',
                    error instanceof StudyApiError
                      ? error.message
                      : '잠시 후 다시 시도해 주세요.',
                  ),
              }),
          },
        ],
      );
    },
    [kickMemberMutation],
  );
  const isNoticeMutating =
    createNoticeMutation.isPending ||
    updateNoticeMutation.isPending ||
    deleteNoticeMutation.isPending;

  useFocusEffect(
    useCallback(() => {
      if (!Number.isFinite(studyId)) return;
      // 멤버·모집 상태가 바뀌면 바로 보여야 하므로 20초 게이트 없이 다시 조회합니다.
      refetchOnFocusNow(queryClient, studyDetailQueryKey(studyId));
    }, [queryClient, studyId]),
  );

  const handleOpenDetailsEdit = useCallback(() => {
    if (!study) return;
    setTitleDraft(study.title ?? '');
    setGoalDraft(study.goal ?? '');
    setIntroDraft(study.intro ?? '');
    setIsDetailsEditing(true);
  }, [study]);

  const handleCancelDetailsEdit = useCallback(() => {
    setIsDetailsEditing(false);
    setTitleDraft('');
    setGoalDraft('');
    setIntroDraft('');
  }, []);

  const isDetailsValid =
    hasVisibleText(titleDraft) &&
    titleDraft.trim().length <= STUDY_TITLE_MAX_LENGTH &&
    hasVisibleText(goalDraft);

  const handleSubmitDetails = useCallback(() => {
    const title = titleDraft.trim();
    const goal = goalDraft.trim();
    if (
      !hasVisibleText(title) ||
      title.length > STUDY_TITLE_MAX_LENGTH ||
      !hasVisibleText(goal) ||
      updateStudyDetailsMutation.isPending
    )
      return;

    updateStudyDetailsMutation.mutate(
      { title, goal, intro: introDraft.trim() },
      {
        onSuccess: handleCancelDetailsEdit,
        onError: (error) =>
          appAlert(
            '수정 실패',
            error instanceof StudyApiError
              ? error.message
              : '스터디 정보를 수정하지 못했습니다.',
          ),
      },
    );
  }, [goalDraft, handleCancelDetailsEdit, introDraft, titleDraft, updateStudyDetailsMutation]);

  const handleOpenNoticeCreate = useCallback(() => {
    setNoticeContent('');
    setNoticeDraft({ mode: 'create' });
  }, []);

  const handleOpenNoticeEdit = useCallback((notice: StudyNotice) => {
    setNoticeContent(notice.content);
    setNoticeDraft({ mode: 'edit', noticeId: notice.noticeId });
  }, []);

  const handleCancelNoticeDraft = useCallback(() => {
    setNoticeDraft(null);
    setNoticeContent('');
  }, []);

  const handleSubmitNotice = useCallback(() => {
    if (!noticeDraft || noticeContent.trim().length === 0) return;
    const content = noticeContent.trim();
    const onSuccess = () => {
      setNoticeDraft(null);
      setNoticeContent('');
    };
    const onError = (error: unknown) =>
      appAlert(
        noticeDraft.mode === 'create' ? '등록 실패' : '수정 실패',
        error instanceof StudyApiError ? error.message : '공지를 저장하지 못했습니다.',
      );

    if (noticeDraft.mode === 'create') {
      createNoticeMutation.mutate(content, { onSuccess, onError });
    } else {
      updateNoticeMutation.mutate(
        { noticeId: noticeDraft.noticeId, content },
        { onSuccess, onError },
      );
    }
  }, [createNoticeMutation, noticeContent, noticeDraft, updateNoticeMutation]);

  const handleDeleteNotice = useCallback(
    (notice: StudyNotice) => {
      appAlert('공지를 삭제할까요?', '삭제한 공지는 되돌릴 수 없어요.', [
        { text: '취소', style: 'cancel' },
        {
          text: '삭제하기',
          style: 'destructive',
          onPress: () => {
            deleteNoticeMutation.mutate(notice.noticeId, {
              onError: (error) =>
                appAlert(
                  '삭제 실패',
                  error instanceof StudyApiError ? error.message : '공지를 삭제하지 못했습니다.',
                ),
            });
          },
        },
      ]);
    },
    [deleteNoticeMutation],
  );

  if (isLoading) {
    return (
      <View style={styles.centerScreen}>
        <ActivityIndicator color={PRIMARY_COLOR} size="large" />
      </View>
    );
  }

  if (!study || !study.isMember) {
    return (
      <View style={styles.centerScreen}>
        <Text style={styles.errorText}>스터디 정보를 불러오지 못했어요.</Text>
        <Pressable onPress={() => void refetch()} style={styles.retryButton}>
          <Text style={styles.retryButtonText}>다시 시도</Text>
        </Pressable>
      </View>
    );
  }

  return (
    <View style={styles.screen}>
      <LinearGradient
        colors={['#F7F8F5', '#ECF8F1', '#FAFCFA']}
        locations={[0, 0.5, 1]}
        pointerEvents="none"
        style={StyleSheet.absoluteFill}
      />
      <ScreenGlowBackground />

      <View style={[styles.header, { paddingTop: insets.top }]}>
        <LinearGradient
          colors={['rgba(250,252,250,0.99)', 'rgba(236,248,241,0.8)', 'rgba(236,248,241,0)']}
          locations={[0, 0.68, 1]}
          pointerEvents="none"
          style={styles.headerGradient}
        />
        <Pressable onPress={goBack} style={styles.headerButton}>
          <GlassSurface intensity={34} radius={22} tint="#F7FFFA" tintOpacity={0.68} />
          <Ionicons name="chevron-back" size={21} color={DARK_GREEN_COLOR} />
        </Pressable>
        <Text style={styles.headerTitle}>스터디 상세</Text>
        <View style={styles.headerButtonPlaceholder} />
      </View>

      <View style={[styles.tabBar, { marginTop: insets.top + 56 }]}>
        {(
          [
            ['GOAL', '목표'],
            ['NOTICE', '공지'],
            ['MEMBER', `멤버 ${study.currentMemberCount}`],
          ] as const
        ).map(([value, label]) => (
          <Pressable key={value} onPress={() => setTab(value)} style={styles.tabButton}>
            <Text style={[styles.tabText, tab === value && styles.tabTextActive]}>{label}</Text>
            {tab === value && <View style={styles.tabUnderline} />}
          </Pressable>
        ))}
      </View>

      <ScrollView
        alwaysBounceVertical={false}
        bounces={false}
        contentContainerStyle={[
          styles.scrollContent,
          { paddingBottom: Math.max(36, insets.bottom + 24) },
        ]}
        keyboardShouldPersistTaps="handled"
        overScrollMode="never"
        showsVerticalScrollIndicator={false}
      >
        {tab === 'GOAL' && (
          <View>
            <View style={styles.sectionHeaderRow}>
              <View>
                <Text style={styles.sectionKicker}>이번 임장의 목표</Text>
                <Text style={styles.sectionTitle}>함께 확인할 것들</Text>
              </View>
              {study.isLeader && !study.readOnly && !isDetailsEditing && (
                <Pressable onPress={handleOpenDetailsEdit} style={styles.editDetailsButton}>
                  <Ionicons name="create-outline" size={16} color={PRIMARY_COLOR} />
                  <Text style={styles.editDetailsButtonText}>수정</Text>
                </Pressable>
              )}
            </View>

            {isDetailsEditing ? (
              <View style={styles.detailsEditor}>
                <View style={styles.detailsFieldHeader}>
                  <Text style={styles.detailsFieldLabel}>스터디 제목</Text>
                  <Text style={styles.detailsCounter}>
                    {titleDraft.length}/{STUDY_TITLE_MAX_LENGTH}
                  </Text>
                </View>
                <TextInput
                  accessibilityLabel="스터디 제목"
                  maxLength={STUDY_TITLE_MAX_LENGTH}
                  onChangeText={setTitleDraft}
                  placeholder="스터디 제목을 입력하세요."
                  placeholderTextColor={PLACEHOLDER_COLOR}
                  style={styles.detailsInput}
                  value={titleDraft}
                />

                <View style={[styles.detailsFieldHeader, styles.detailsFieldHeaderSpacing]}>
                  <Text style={styles.detailsFieldLabel}>스터디 목표</Text>
                  <Text style={styles.detailsCounter}>
                    {goalDraft.length}/{STUDY_GOAL_MAX_LENGTH}
                  </Text>
                </View>
                <TextInput
                  accessibilityLabel="스터디 목표"
                  maxLength={STUDY_GOAL_MAX_LENGTH}
                  multiline
                  onChangeText={setGoalDraft}
                  placeholder="함께 확인할 목표를 입력하세요."
                  placeholderTextColor={PLACEHOLDER_COLOR}
                  style={[styles.detailsInput, styles.detailsGoalInput]}
                  textAlignVertical="top"
                  value={goalDraft}
                />

                <View style={[styles.detailsFieldHeader, styles.detailsFieldHeaderSpacing]}>
                  <Text style={styles.detailsFieldLabel}>스터디 소개</Text>
                  <Text style={styles.detailsCounter}>
                    {introDraft.length}/{STUDY_INTRO_MAX_LENGTH}
                  </Text>
                </View>
                <TextInput
                  accessibilityLabel="스터디 소개"
                  maxLength={STUDY_INTRO_MAX_LENGTH}
                  multiline
                  onChangeText={setIntroDraft}
                  placeholder="스터디를 간단히 소개해 주세요."
                  placeholderTextColor={PLACEHOLDER_COLOR}
                  style={[styles.detailsInput, styles.detailsIntroInput]}
                  textAlignVertical="top"
                  value={introDraft}
                />

                <View style={styles.composerActionRow}>
                  <Pressable
                    disabled={updateStudyDetailsMutation.isPending}
                    onPress={handleCancelDetailsEdit}
                    style={styles.composerCancelButton}
                  >
                    <Text style={styles.composerCancelText}>취소</Text>
                  </Pressable>
                  <Pressable
                    disabled={!isDetailsValid || updateStudyDetailsMutation.isPending}
                    onPress={handleSubmitDetails}
                    style={[
                      styles.composerSubmitButton,
                      (!isDetailsValid || updateStudyDetailsMutation.isPending) && styles.disabled,
                    ]}
                  >
                    {updateStudyDetailsMutation.isPending ? (
                      <ActivityIndicator color={SURFACE_COLOR} size="small" />
                    ) : (
                      <Text style={styles.composerSubmitText}>수정 완료</Text>
                    )}
                  </Pressable>
                </View>
              </View>
            ) : (
              <>
                <LinearGradient colors={['#E1F7EA', '#F9FCFA']} style={styles.goalCard}>
                  <View style={styles.goalIcon}>
                    <Ionicons name="flag-outline" size={24} color={PRIMARY_COLOR} />
                  </View>
                  <View style={styles.goalCopy}>
                    <Text style={styles.goalLabel}>함께 확인할 핵심 포인트</Text>
                    <Text style={styles.goalText}>
                      {study.goal || '아직 등록된 목표가 없어요.'}
                    </Text>
                  </View>
                </LinearGradient>
                {!!study.intro && (
                  <View style={styles.introCard}>
                    <Text style={styles.introLabel}>스터디 소개</Text>
                    <Text style={styles.introText}>{study.intro}</Text>
                  </View>
                )}
              </>
            )}
          </View>
        )}

        {tab === 'NOTICE' && (
          <View>
            <View style={styles.sectionHeaderRow}>
              <View>
                <Text style={styles.sectionKicker}>스터디장이 전한 운영 안내</Text>
                <Text style={styles.sectionTitle}>스터디 공지</Text>
              </View>
              <Text style={styles.sortLabel}>최근 등록 순</Text>
            </View>

            {study.isLeader && noticeDraft && (
              <View style={styles.noticeComposer}>
                <View style={styles.composerCounterRow}>
                  <Text style={styles.composerLabel}>
                    {noticeDraft.mode === 'create' ? '새 공지' : '공지 수정'}
                  </Text>
                  <Text style={styles.composerCounter}>
                    {noticeContent.length}/{NOTICE_MAX_LENGTH}
                  </Text>
                </View>
                <TextInput
                  maxLength={NOTICE_MAX_LENGTH}
                  multiline
                  onChangeText={setNoticeContent}
                  placeholder="공지 내용을 입력하세요."
                  placeholderTextColor={PLACEHOLDER_COLOR}
                  style={styles.noticeInput}
                  textAlignVertical="top"
                  value={noticeContent}
                />
                <View style={styles.composerActionRow}>
                  <Pressable
                    disabled={isNoticeMutating}
                    onPress={handleCancelNoticeDraft}
                    style={styles.composerCancelButton}
                  >
                    <Text style={styles.composerCancelText}>취소</Text>
                  </Pressable>
                  <Pressable
                    disabled={noticeContent.trim().length === 0 || isNoticeMutating}
                    onPress={handleSubmitNotice}
                    style={[
                      styles.composerSubmitButton,
                      (noticeContent.trim().length === 0 || isNoticeMutating) && styles.disabled,
                    ]}
                  >
                    {isNoticeMutating ? (
                      <ActivityIndicator color={SURFACE_COLOR} size="small" />
                    ) : (
                      <Text style={styles.composerSubmitText}>
                        {noticeDraft.mode === 'create' ? '등록' : '수정 완료'}
                      </Text>
                    )}
                  </Pressable>
                </View>
              </View>
            )}

            {noticesQuery.isLoading ? (
              <ActivityIndicator color={PRIMARY_COLOR} style={styles.noticeLoader} />
            ) : noticesQuery.isError && noticesQuery.data === undefined ? (
              <View style={styles.noticeErrorState}>
                <Text style={styles.emptyText}>공지를 불러오지 못했어요.</Text>
                <Pressable onPress={() => void noticesQuery.refetch()} style={styles.retryButton}>
                  <Text style={styles.retryButtonText}>다시 시도</Text>
                </Pressable>
              </View>
            ) : notices.length === 0 ? (
              <View style={styles.emptyCard}>
                <Ionicons name="notifications-outline" size={25} color={PRIMARY_COLOR} />
                <Text style={styles.emptyTitle}>아직 등록된 공지가 없어요</Text>
                <Text style={styles.emptyDescription}>
                  새 공지가 등록되면 이곳에서 확인할 수 있어요.
                </Text>
              </View>
            ) : (
              <View style={styles.noticeList}>
                {notices.map((notice) => (
                  <View key={notice.noticeId} style={styles.noticeCard}>
                    <View style={styles.noticeTopRow}>
                      <View style={styles.noticeIcon}>
                        <Ionicons name="notifications" size={16} color="#B85B48" />
                      </View>
                      <Text style={styles.noticeDate}>{formatDateTime(notice.createdAt)}</Text>
                    </View>
                    <Text style={styles.noticeText}>{notice.content}</Text>
                    {(notice.canEdit || notice.canDelete) && (
                      <View style={styles.noticeActions}>
                        {notice.canEdit && (
                          <Pressable onPress={() => handleOpenNoticeEdit(notice)}>
                            <Text style={styles.noticeActionText}>수정</Text>
                          </Pressable>
                        )}
                        {notice.canDelete && (
                          <Pressable onPress={() => handleDeleteNotice(notice)}>
                            <Text style={styles.noticeDeleteText}>삭제</Text>
                          </Pressable>
                        )}
                      </View>
                    )}
                  </View>
                ))}
                {noticesQuery.isFetchNextPageError && (
                  <Text style={styles.loadMoreError}>다음 공지를 불러오지 못했어요.</Text>
                )}
                {(noticesQuery.hasNextPage || noticesQuery.isFetchNextPageError) && (
                  <Pressable
                    accessibilityLabel={
                      noticesQuery.isFetchNextPageError ? '공지 더 보기 다시 시도' : '공지 더 보기'
                    }
                    disabled={noticesQuery.isFetchingNextPage}
                    onPress={() => void noticesQuery.fetchNextPage()}
                    style={({ pressed }) => [
                      styles.loadMoreButton,
                      (pressed || noticesQuery.isFetchingNextPage) && styles.disabled,
                    ]}
                  >
                    {noticesQuery.isFetchingNextPage ? (
                      <ActivityIndicator color={PRIMARY_COLOR} size="small" />
                    ) : (
                      <Text style={styles.loadMoreButtonText}>
                        {noticesQuery.isFetchNextPageError ? '다시 시도' : '공지 더 보기'}
                      </Text>
                    )}
                  </Pressable>
                )}
              </View>
            )}
          </View>
        )}

        {tab === 'MEMBER' && (
          <View>
            <View style={styles.memberSummaryRow}>
              <View>
                <Text style={styles.memberCount}>멤버 {study.currentMemberCount}명</Text>
                <Text style={styles.memberCapacity}>정원 {study.capacity}명</Text>
              </View>
              {study.isLeader && (
                <Pressable
                  onPress={() =>
                    router.push({
                      pathname: '/(app)/study/[id]/manage',
                      params: { id: String(study.studyId) },
                    })
                  }
                  style={styles.manageShortcut}
                >
                  <Text style={styles.manageShortcutText}>신청자 관리</Text>
                  <Ionicons name="chevron-forward" size={16} color={PRIMARY_COLOR} />
                </Pressable>
              )}
            </View>

            <View style={styles.memberList}>
              {study.memberSummary.map((member) => (
                <View key={member.memberId} style={styles.memberCard}>
                  <Pressable
                    accessibilityLabel={`${member.nickname} 프로필 보기`}
                    onPress={() =>
                      router.push({
                        pathname: '/(app)/member/[memberId]',
                        params: { memberId: String(member.memberId) },
                      })
                    }
                    style={({ pressed }) => [styles.memberProfileLink, pressed && styles.pressed]}
                  >
                    <View style={styles.memberAvatar}>
                      <Image
                        resizeMode="contain"
                        source={memberFaceSource(member.selectedCharacterId)}
                        style={styles.memberAvatarImage}
                      />
                    </View>
                    <View style={styles.memberCopy}>
                      <View style={styles.memberNameRow}>
                        <Text style={styles.memberName} numberOfLines={1}>
                          {member.nickname}
                        </Text>
                        {member.role === 'LEADER' && (
                          <View style={styles.leaderBadge}>
                            <Text style={styles.leaderBadgeText}>스터디장</Text>
                          </View>
                        )}
                      </View>
                      <Text style={styles.memberRole}>
                        {member.role === 'LEADER'
                          ? '스터디를 이끌고 있어요'
                          : '함께 임장하는 멤버'}
                      </Text>
                    </View>
                  </Pressable>
                  {study.permissions.canManageMembers && member.role !== 'LEADER' && (
                    <Pressable
                      accessibilityLabel={`${member.nickname} 강퇴`}
                      accessibilityRole="button"
                      accessibilityState={{ disabled: kickMemberMutation.isPending }}
                      disabled={kickMemberMutation.isPending}
                      hitSlop={6}
                      onPress={() => handleKickMember(member)}
                      style={({ pressed }) => [
                        styles.kickButton,
                        (pressed || kickMemberMutation.isPending) && styles.disabled,
                      ]}
                    >
                      <Text style={styles.kickButtonText}>강퇴</Text>
                    </Pressable>
                  )}
                </View>
              ))}
            </View>
          </View>
        )}
      </ScrollView>

      {tab === 'NOTICE' && study.isLeader && !noticeDraft && (
        <Pressable
          accessibilityLabel="공지 작성"
          onPress={handleOpenNoticeCreate}
          style={({ pressed }) => [
            styles.noticeFab,
            { bottom: Math.max(24, insets.bottom + 20) },
            pressed && styles.pressed,
          ]}
        >
          <Ionicons name="create-outline" size={24} color={SURFACE_COLOR} />
        </Pressable>
      )}
    </View>
  );
}

const styles = StyleSheet.create({
  screen: { flex: 1, backgroundColor: SURFACE_COLOR },
  centerScreen: {
    flex: 1,
    alignItems: 'center',
    justifyContent: 'center',
    gap: 12,
    backgroundColor: SURFACE_COLOR,
  },
  errorText: { fontSize: 13, color: MUTED_TEXT_COLOR },
  retryButton: {
    height: 38,
    paddingHorizontal: 18,
    alignItems: 'center',
    justifyContent: 'center',
    borderRadius: 19,
    backgroundColor: SOFT_GREEN_COLOR,
  },
  retryButtonText: { fontSize: 13, fontWeight: '800', color: PRIMARY_COLOR },
  header: {
    position: 'absolute',
    zIndex: 10,
    top: 0,
    left: 0,
    right: 0,
    minHeight: 68,
    paddingHorizontal: 18,
    paddingBottom: 10,
    flexDirection: 'row',
    alignItems: 'flex-end',
    overflow: 'visible',
    backgroundColor: 'transparent',
  },
  headerGradient: {
    position: 'absolute',
    top: 0,
    left: 0,
    right: 0,
    height: 104,
  },
  headerButton: {
    width: 44,
    height: 44,
    alignItems: 'center',
    justifyContent: 'center',
    overflow: 'hidden',
    borderRadius: 22,
    borderWidth: 1,
    borderColor: 'rgba(255,255,255,0.9)',
    boxShadow: '0px 8px 18px rgba(30,86,61,0.12)',
  },
  headerButtonPlaceholder: { width: 44, height: 44 },
  headerTitle: {
    flex: 1,
    paddingBottom: 11,
    textAlign: 'center',
    fontSize: 18,
    fontWeight: '900',
    color: DARK_GREEN_COLOR,
  },
  tabBar: {
    marginHorizontal: 18,
    height: 54,
    flexDirection: 'row',
    borderBottomWidth: 1,
    borderBottomColor: BORDER_COLOR,
  },
  tabButton: { flex: 1, alignItems: 'center', justifyContent: 'center', position: 'relative' },
  tabText: { fontSize: 14.5, fontWeight: '800', color: MUTED_TEXT_COLOR },
  tabTextActive: { color: DARK_GREEN_COLOR },
  tabUnderline: {
    position: 'absolute',
    left: 20,
    right: 20,
    bottom: -1,
    height: 3,
    borderRadius: 2,
    backgroundColor: PRIMARY_COLOR,
  },
  scrollContent: { paddingHorizontal: 18, paddingTop: 20 },
  sectionKicker: { fontSize: 11, fontWeight: '900', color: PRIMARY_COLOR },
  sectionTitle: { marginTop: 6, fontSize: 24, fontWeight: '900', color: DARK_GREEN_COLOR },
  sectionHeaderRow: {
    flexDirection: 'row',
    alignItems: 'flex-end',
    justifyContent: 'space-between',
  },
  sortLabel: { paddingBottom: 4, fontSize: 10.5, fontWeight: '700', color: MUTED_TEXT_COLOR },
  editDetailsButton: {
    height: 38,
    paddingHorizontal: 13,
    flexDirection: 'row',
    alignItems: 'center',
    gap: 5,
    borderRadius: 14,
    backgroundColor: SOFT_GREEN_COLOR,
  },
  editDetailsButtonText: { fontSize: 11.5, fontWeight: '900', color: PRIMARY_COLOR },
  detailsEditor: {
    marginTop: 18,
    padding: 17,
    borderRadius: 24,
    borderWidth: 1,
    borderColor: BORDER_COLOR,
    backgroundColor: 'rgba(255,255,255,0.86)',
  },
  detailsFieldHeader: { flexDirection: 'row', justifyContent: 'space-between' },
  detailsFieldHeaderSpacing: { marginTop: 16 },
  detailsFieldLabel: { fontSize: 12, fontWeight: '900', color: DARK_GREEN_COLOR },
  detailsCounter: { fontSize: 10.5, color: MUTED_TEXT_COLOR },
  detailsInput: {
    marginTop: 9,
    padding: 14,
    borderRadius: 16,
    backgroundColor: '#F2F7F4',
    fontSize: 13,
    lineHeight: 20,
    color: TEXT_COLOR,
  },
  detailsGoalInput: { minHeight: 112 },
  detailsIntroInput: { minHeight: 96 },
  goalCard: {
    marginTop: 18,
    padding: 18,
    flexDirection: 'row',
    alignItems: 'flex-start',
    gap: 14,
    borderRadius: 28,
    borderWidth: 1,
    borderColor: 'rgba(31,111,77,0.1)',
    boxShadow: '0px 11px 25px rgba(30,78,55,0.09)',
  },
  goalIcon: {
    width: 48,
    height: 48,
    alignItems: 'center',
    justifyContent: 'center',
    borderRadius: 17,
    backgroundColor: 'rgba(255,255,255,0.86)',
  },
  goalCopy: { flex: 1 },
  goalLabel: { fontSize: 11, fontWeight: '900', color: PRIMARY_COLOR },
  goalText: {
    marginTop: 9,
    fontSize: 18,
    lineHeight: 26,
    fontWeight: '900',
    color: DARK_GREEN_COLOR,
  },
  introCard: {
    marginTop: 14,
    padding: 19,
    borderRadius: 24,
    borderWidth: 1,
    borderColor: BORDER_COLOR,
    backgroundColor: 'rgba(255,255,255,0.78)',
  },
  introLabel: { fontSize: 11, fontWeight: '900', color: PRIMARY_COLOR },
  introText: { marginTop: 8, fontSize: 14.5, lineHeight: 22, color: TEXT_COLOR },
  noticeFab: {
    position: 'absolute',
    right: 22,
    width: 58,
    height: 58,
    alignItems: 'center',
    justifyContent: 'center',
    borderRadius: 29,
    borderWidth: 2,
    borderColor: 'rgba(255,255,255,0.92)',
    backgroundColor: PRIMARY_COLOR,
    boxShadow: '0px 9px 20px rgba(24, 116, 73, 0.28)',
  },
  noticeComposer: {
    marginTop: 18,
    padding: 16,
    borderRadius: 22,
    borderWidth: 1,
    borderColor: BORDER_COLOR,
    backgroundColor: 'rgba(255,255,255,0.86)',
  },
  composerCounterRow: { flexDirection: 'row', justifyContent: 'space-between', marginBottom: 10 },
  composerLabel: { fontSize: 12, fontWeight: '900', color: DARK_GREEN_COLOR },
  composerCounter: { fontSize: 10.5, color: MUTED_TEXT_COLOR },
  noticeInput: {
    minHeight: 110,
    padding: 14,
    borderRadius: 16,
    backgroundColor: '#F2F7F4',
    fontSize: 13,
    lineHeight: 20,
    color: TEXT_COLOR,
  },
  composerActionRow: { marginTop: 10, flexDirection: 'row', gap: 8 },
  composerCancelButton: {
    flex: 1,
    height: 44,
    alignItems: 'center',
    justifyContent: 'center',
    borderRadius: 14,
    borderWidth: 1,
    borderColor: BORDER_COLOR,
  },
  composerCancelText: { fontSize: 12, fontWeight: '800', color: MUTED_TEXT_COLOR },
  composerSubmitButton: {
    flex: 1,
    height: 44,
    alignItems: 'center',
    justifyContent: 'center',
    borderRadius: 14,
    backgroundColor: PRIMARY_COLOR,
  },
  composerSubmitText: { fontSize: 12, fontWeight: '900', color: SURFACE_COLOR },
  disabled: { opacity: 0.42 },
  noticeLoader: { marginTop: 40 },
  noticeErrorState: { marginTop: 24, alignItems: 'center', gap: 12 },
  emptyText: { marginTop: 40, textAlign: 'center', fontSize: 13, color: MUTED_TEXT_COLOR },
  emptyCard: {
    minHeight: 180,
    marginTop: 20,
    alignItems: 'center',
    justifyContent: 'center',
    borderRadius: 26,
    borderWidth: 1,
    borderColor: BORDER_COLOR,
    backgroundColor: 'rgba(255,255,255,0.8)',
  },
  emptyTitle: { marginTop: 12, fontSize: 14, fontWeight: '900', color: DARK_GREEN_COLOR },
  emptyDescription: { marginTop: 6, fontSize: 11, color: MUTED_TEXT_COLOR },
  noticeList: { marginTop: 18, gap: 12 },
  loadMoreError: { textAlign: 'center', fontSize: 12, color: MUTED_TEXT_COLOR },
  loadMoreButton: {
    height: 44,
    alignItems: 'center',
    justifyContent: 'center',
    borderRadius: 16,
    backgroundColor: SOFT_GREEN_COLOR,
  },
  loadMoreButtonText: { fontSize: 13, fontWeight: '900', color: PRIMARY_COLOR },
  noticeCard: {
    padding: 17,
    borderRadius: 23,
    borderWidth: 1,
    borderColor: BORDER_COLOR,
    backgroundColor: 'rgba(255,255,255,0.88)',
    boxShadow: '0px 8px 19px rgba(30,78,55,0.07)',
  },
  noticeTopRow: { flexDirection: 'row', alignItems: 'center', gap: 9 },
  noticeIcon: {
    width: 31,
    height: 31,
    alignItems: 'center',
    justifyContent: 'center',
    borderRadius: 11,
    backgroundColor: '#FFE9E2',
  },
  noticeDate: { fontSize: 10.5, fontWeight: '700', color: MUTED_TEXT_COLOR },
  noticeText: { marginTop: 12, fontSize: 13.5, lineHeight: 21, color: TEXT_COLOR },
  noticeActions: { marginTop: 13, flexDirection: 'row', justifyContent: 'flex-end', gap: 16 },
  noticeActionText: { fontSize: 11.5, fontWeight: '800', color: PRIMARY_COLOR },
  noticeDeleteText: { fontSize: 11.5, fontWeight: '800', color: '#E1483F' },
  memberSummaryRow: {
    flexDirection: 'row',
    alignItems: 'center',
    justifyContent: 'space-between',
    marginBottom: 18,
  },
  memberCount: { fontSize: 18, fontWeight: '900', color: DARK_GREEN_COLOR },
  memberCapacity: { marginTop: 4, fontSize: 10.5, color: MUTED_TEXT_COLOR },
  manageShortcut: {
    height: 42,
    paddingHorizontal: 12,
    flexDirection: 'row',
    alignItems: 'center',
    gap: 4,
    borderRadius: 14,
    backgroundColor: SOFT_GREEN_COLOR,
  },
  manageShortcutText: { fontSize: 11.5, fontWeight: '900', color: PRIMARY_COLOR },
  memberList: { gap: 12 },
  memberCard: {
    minHeight: 112,
    paddingHorizontal: 14,
    paddingVertical: 12,
    flexDirection: 'row',
    alignItems: 'center',
    gap: 13,
    borderRadius: 25,
    borderWidth: 1,
    borderColor: BORDER_COLOR,
    backgroundColor: 'rgba(255,255,255,0.9)',
    boxShadow: '0px 9px 22px rgba(30,78,55,0.07)',
  },
  memberProfileLink: {
    flex: 1,
    minWidth: 0,
    flexDirection: 'row',
    alignItems: 'center',
    gap: 13,
  },
  pressed: { opacity: 0.82 },
  memberAvatar: {
    width: 82,
    height: 82,
    alignItems: 'center',
    justifyContent: 'center',
    overflow: 'hidden',
    borderRadius: 22,
    backgroundColor: '#E7F7EE',
  },
  memberAvatarImage: { width: 78, height: 78 },
  memberCopy: { flex: 1, minWidth: 0 },
  memberNameRow: { flexDirection: 'row', alignItems: 'center', gap: 7 },
  memberName: { maxWidth: '65%', fontSize: 15, fontWeight: '900', color: DARK_GREEN_COLOR },
  leaderBadge: {
    paddingHorizontal: 8,
    paddingVertical: 4,
    borderRadius: 9,
    backgroundColor: PRIMARY_COLOR,
  },
  leaderBadgeText: { fontSize: 9, fontWeight: '900', color: SURFACE_COLOR },
  memberRole: { marginTop: 7, fontSize: 10.5, color: MUTED_TEXT_COLOR },
  kickButton: {
    height: 36,
    paddingHorizontal: 12,
    alignItems: 'center',
    justifyContent: 'center',
    borderRadius: 13,
    borderWidth: 1,
    borderColor: '#F0C4C4',
    backgroundColor: '#FCEDED',
  },
  kickButtonText: { fontSize: 13, fontWeight: '800', color: '#C0392B' },
});
