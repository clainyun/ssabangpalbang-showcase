import Ionicons from '@expo/vector-icons/Ionicons';
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query';
import { StatusBar } from 'expo-status-bar';
import { useRouter } from 'expo-router';
import { type ReactNode, useMemo, useState } from 'react';
import {
  ActivityIndicator,
  Image,
  KeyboardAvoidingView,
  Platform,
  Pressable,
  ScrollView,
  StyleSheet,
  Text,
  View,
} from 'react-native';
import { useSafeAreaInsets } from 'react-native-safe-area-context';

import { appAlert } from '@/components/AppDialog';
import { FormField } from '@/components/FormField';
import {
  BORDER_COLOR,
  BUTTON_BACKGROUND_COLOR,
  DARK_GREEN_COLOR,
  ERROR_BACKGROUND_COLOR,
  ERROR_COLOR,
  MUTED_TEXT_COLOR,
  PLACEHOLDER_COLOR,
  PRIMARY_COLOR,
  SCREEN_BACKGROUND_COLOR,
  SOFT_BACKGROUND_COLOR,
  SOFT_GREEN_COLOR,
  SURFACE_COLOR,
  TEXT_COLOR,
} from '@/constants/colors';
import {
  getMyProfile,
  MyProfileUpdateError,
  updateMyProfile,
  type AgeGroup,
  type CharacterId,
  type MaritalStatus,
  type MyProfile,
  type MyProfileUpdateRequest,
  type MyProfileUpdateResponse,
  type Priority,
  type Purpose,
} from '@/features/member/api/myPage';
import { useAuthStore } from '@/store/authStore';
import { useMemberStore } from '@/store/memberStore';

const CHARACTER_OPTIONS: {
  value: CharacterId;
  label: string;
  image: number;
}[] = [
  {
    value: 'PALBANG',
    label: '기본 팔방이',
    image: require('../../assets/images/characters/palbang.png'),
  },
  {
    value: 'PALBANG_RABBIT',
    label: '토끼 팔방이',
    image: require('../../assets/images/characters/palbang_rabbit.png'),
  },
  {
    value: 'PALBANG_DOG',
    label: '강아지 팔방이',
    image: require('../../assets/images/characters/palbang_dog.png'),
  },
];

const AGE_GROUP_OPTIONS: { value: AgeGroup; label: string }[] = [
  { value: 'TEENS', label: '10대' },
  { value: 'TWENTIES', label: '20대' },
  { value: 'THIRTIES', label: '30대' },
  { value: 'FORTIES', label: '40대' },
  { value: 'FIFTIES', label: '50대' },
  { value: 'SIXTIES_PLUS', label: '60대 이상' },
];

const PURPOSE_OPTIONS: { value: Purpose; label: string; icon: string }[] = [
  { value: 'RESIDENCE', label: '실거주', icon: 'home-outline' },
  { value: 'INVESTMENT', label: '투자', icon: 'trending-up-outline' },
  { value: 'STUDY', label: '임장 학습', icon: 'search-outline' },
];

const PRIORITY_OPTIONS: { value: Priority; label: string; icon: string }[] = [
  { value: 'TRANSPORT', label: '교통', icon: '🚇' },
  { value: 'SAFETY', label: '안전·치안', icon: '🚨' },
  { value: 'EDUCATION', label: '교육환경', icon: '🏫' },
  { value: 'COMMERCIAL', label: '생활편의', icon: '🏪' },
  { value: 'WALKABILITY', label: '보행환경', icon: '🚶' },
  { value: 'GREEN_SPACE', label: '공원·녹지', icon: '🌳' },
  { value: 'PARKING', label: '주차환경', icon: '🅿️' },
  { value: 'NOISE', label: '소음환경', icon: '🔇' },
];

const MAX_PRIORITIES = 4;
const DEVELOPMENT_PREVIEW_PROFILE: MyProfile = {
  memberId: 0,
  email: 'preview@ssabangpalbang.dev',
  nickname: '임시 사용자',
  profileImageUrl: null,
  selectedCharacterId: 'PALBANG_DOG',
  ageGroup: 'THIRTIES',
  ageGroupPublicAgreed: true,
  serviceNotificationAgreed: false,
  adNotificationAgreed: false,
  preference: {
    purpose: 'RESIDENCE',
    maritalStatus: 'SINGLE',
    hasVehicle: true,
    hasChildren: false,
    priorities: ['TRANSPORT', 'SAFETY', 'WALKABILITY'],
  },
  onboardingCompleted: true,
  joinedDays: 128,
  fieldVisitCompletedCount: 0,
  summary: {
    studyCount: 0,
    reportCount: 0,
    followingCount: 0,
  },
  reviewSummary: {
    topTags: [],
    likeReceivedCount: 0,
    reviewCount: 0,
  },
  createdAt: '2026-03-23T00:00:00+09:00',
  updatedAt: '2026-07-29T00:00:00+09:00',
};

function sameArray<T>(left: T[], right: T[]): boolean {
  return left.length === right.length && left.every((value, index) => value === right[index]);
}

function SectionCard({
  children,
  description,
  title,
}: {
  children: ReactNode;
  description?: string;
  title: string;
}) {
  return (
    <View style={styles.sectionCard}>
      <Text style={styles.sectionTitle}>{title}</Text>
      {description ? <Text style={styles.sectionDescription}>{description}</Text> : null}
      <View style={styles.sectionBody}>{children}</View>
    </View>
  );
}

function ChoiceButton({
  label,
  onPress,
  selected,
}: {
  label: string;
  onPress: () => void;
  selected: boolean;
}) {
  return (
    <Pressable
      accessibilityRole="radio"
      accessibilityState={{ selected }}
      onPress={onPress}
      style={({ pressed }) => [
        styles.choiceButton,
        selected && styles.choiceButtonSelected,
        pressed && styles.pressed,
      ]}
    >
      <Text style={[styles.choiceButtonText, selected && styles.choiceButtonTextSelected]}>
        {label}
      </Text>
      {selected ? <Ionicons color={PRIMARY_COLOR} name="checkmark-circle" size={18} /> : null}
    </Pressable>
  );
}

function LoadingState() {
  return (
    <View style={styles.stateContainer}>
      <View style={styles.stateIcon}>
        <ActivityIndicator color={PRIMARY_COLOR} />
      </View>
      <Text style={styles.stateTitle}>프로필을 불러오는 중이에요</Text>
      <Text style={styles.stateDescription}>잠시만 기다려 주세요.</Text>
    </View>
  );
}

function ErrorState({ onRetry }: { onRetry: () => void }) {
  return (
    <View style={styles.stateContainer}>
      <View style={styles.stateIcon}>
        <Ionicons color={DARK_GREEN_COLOR} name="cloud-offline-outline" size={25} />
      </View>
      <Text style={styles.stateTitle}>프로필을 불러오지 못했어요</Text>
      <Text style={styles.stateDescription}>네트워크 연결을 확인하고 다시 시도해 주세요.</Text>
      <Pressable
        accessibilityRole="button"
        onPress={onRetry}
        style={({ pressed }) => [styles.retryButton, pressed && styles.pressed]}
      >
        <Text style={styles.retryButtonText}>다시 시도</Text>
      </Pressable>
    </View>
  );
}

function mergeUpdatedProfile(
  current: MyProfile | undefined,
  updated: MyProfileUpdateResponse,
): MyProfile | undefined {
  if (!current) return current;

  const hasPreference =
    updated.purpose !== null ||
    updated.maritalStatus !== null ||
    updated.hasVehicle !== null ||
    updated.hasChildren !== null ||
    updated.priorities.length > 0;

  return {
    ...current,
    nickname: updated.nickname,
    ageGroup: updated.ageGroup,
    ageGroupPublicAgreed: updated.ageGroupPublicAgreed,
    selectedCharacterId: updated.selectedCharacterId,
    preference: hasPreference
      ? {
          purpose: updated.purpose,
          maritalStatus: updated.maritalStatus,
          hasVehicle: updated.hasVehicle,
          hasChildren: updated.hasChildren,
          priorities: updated.priorities,
        }
      : null,
    onboardingCompleted: current.onboardingCompleted || hasPreference,
    updatedAt: updated.updatedAt,
  };
}

function ProfileEditForm({
  bottomInset,
  isDevelopmentPreview,
  profile,
  queryKey,
}: {
  bottomInset: number;
  isDevelopmentPreview: boolean;
  profile: MyProfile;
  queryKey: readonly ['member', 'me', number];
}) {
  const router = useRouter();
  const queryClient = useQueryClient();
  const setMemberProfile = useMemberStore((state) => state.setProfile);
  const [nickname, setNickname] = useState(profile.nickname);
  const [selectedCharacterId, setSelectedCharacterId] = useState(profile.selectedCharacterId);
  const [ageGroup, setAgeGroup] = useState<AgeGroup | null>(profile.ageGroup);
  const [ageGroupPublicAgreed, setAgeGroupPublicAgreed] = useState(profile.ageGroupPublicAgreed);
  const [purpose, setPurpose] = useState<Purpose | null>(profile.preference?.purpose ?? null);
  const [priorities, setPriorities] = useState<Priority[]>(profile.preference?.priorities ?? []);
  const [maritalStatus, setMaritalStatus] = useState<MaritalStatus | null>(
    profile.preference?.maritalStatus ?? null,
  );
  const [hasChildren, setHasChildren] = useState<boolean | null>(
    profile.preference?.hasChildren ?? null,
  );
  const [hasVehicle, setHasVehicle] = useState<boolean | null>(
    profile.preference?.hasVehicle ?? null,
  );

  const updateMutation = useMutation({
    mutationFn: updateMyProfile,
    onSuccess: async (updated) => {
      queryClient.setQueryData<MyProfile>(queryKey, (current) =>
        mergeUpdatedProfile(current, updated),
      );
      setMemberProfile({
        selectedCharacterId: updated.selectedCharacterId,
        ageGroupPublicAgreed: updated.ageGroupPublicAgreed,
      });
      await queryClient.invalidateQueries({ queryKey });
      appAlert('프로필 저장 완료', '변경한 내 정보가 반영되었어요.', [
        { text: '확인', onPress: () => router.back() },
      ]);
    },
  });

  const request = useMemo<MyProfileUpdateRequest>(() => {
    const next: MyProfileUpdateRequest = {};
    const trimmedNickname = nickname.trim();
    const originalPriorities = profile.preference?.priorities ?? [];

    if (trimmedNickname !== profile.nickname) next.nickname = trimmedNickname;
    if (selectedCharacterId !== profile.selectedCharacterId) {
      next.selectedCharacterId = selectedCharacterId;
    }
    if (ageGroup !== null && ageGroup !== profile.ageGroup) next.ageGroup = ageGroup;
    if (ageGroupPublicAgreed !== profile.ageGroupPublicAgreed) {
      next.ageGroupPublicAgreed = ageGroupPublicAgreed;
    }
    if (purpose !== null && purpose !== (profile.preference?.purpose ?? null)) {
      next.purpose = purpose;
    }
    if (!sameArray(priorities, originalPriorities)) next.priorities = priorities;
    if (maritalStatus !== null && maritalStatus !== (profile.preference?.maritalStatus ?? null)) {
      next.maritalStatus = maritalStatus;
    }
    if (hasChildren !== null && hasChildren !== (profile.preference?.hasChildren ?? null)) {
      next.hasChildren = hasChildren;
    }
    if (hasVehicle !== null && hasVehicle !== (profile.preference?.hasVehicle ?? null)) {
      next.hasVehicle = hasVehicle;
    }

    return next;
  }, [
    ageGroup,
    ageGroupPublicAgreed,
    hasChildren,
    hasVehicle,
    maritalStatus,
    nickname,
    priorities,
    profile,
    purpose,
    selectedCharacterId,
  ]);

  const isDirty = Object.keys(request).length > 0;
  const nicknameLength = nickname.trim().length;
  const nicknameClientError =
    nicknameLength === 0
      ? '닉네임을 입력해 주세요.'
      : nicknameLength > 50
        ? '닉네임은 50자 이하로 입력해 주세요.'
        : undefined;
  const priorityClientError =
    request.priorities !== undefined && priorities.length === 0
      ? '우선순위는 1개 이상 선택해 주세요.'
      : undefined;
  const mutationError =
    updateMutation.error instanceof MyProfileUpdateError ? updateMutation.error : undefined;
  const nicknameError =
    nicknameClientError ??
    (mutationError?.field === 'nickname' ? mutationError.message : undefined);
  const formError =
    mutationError && mutationError.field !== 'nickname' ? mutationError.message : undefined;
  const canSubmit =
    isDirty && !nicknameClientError && !priorityClientError && !updateMutation.isPending;

  const clearServerError = () => {
    if (updateMutation.error) updateMutation.reset();
  };

  const togglePriority = (priority: Priority) => {
    clearServerError();
    setPriorities((current) => {
      if (current.includes(priority)) return current.filter((item) => item !== priority);
      if (current.length >= MAX_PRIORITIES) return current;
      return [...current, priority];
    });
  };

  const handleSubmit = () => {
    if (!canSubmit) return;
    if (isDevelopmentPreview) {
      appAlert(
        '개발용 미리보기',
        '실제 계정으로 로그인하면 변경사항이 프로필 수정 API로 저장됩니다.',
      );
      return;
    }
    updateMutation.mutate(request);
  };

  return (
    <KeyboardAvoidingView
      behavior={Platform.OS === 'ios' ? 'padding' : undefined}
      style={styles.flex}
    >
      <ScrollView
        contentContainerStyle={styles.formContent}
        keyboardShouldPersistTaps="handled"
        showsVerticalScrollIndicator={false}
      >
        <View style={styles.introCard}>
          <View style={styles.introIcon}>
            <Ionicons color={PRIMARY_COLOR} name="sparkles-outline" size={21} />
          </View>
          <View style={styles.introTextArea}>
            <Text style={styles.introTitle}>내 임장 기준을 최신으로 맞춰주세요</Text>
            <Text style={styles.introDescription}>
              선택한 정보는 맞춤 추천과 공개 프로필에 반영돼요.
            </Text>
          </View>
        </View>

        <View style={[styles.sectionCard, styles.identityCard]}>
          <Text style={styles.sectionTitle}>기본 정보</Text>
          <Text style={styles.sectionDescription}>다른 사용자에게 보여줄 이름이에요.</Text>
          <View style={styles.sectionBody}>
            <FormField
              error={nicknameError}
              label="닉네임"
              onChangeText={(value) => {
                clearServerError();
                setNickname(value);
              }}
              onSubmitEditing={handleSubmit}
              placeholder="닉네임을 입력해 주세요"
              returnKeyType="done"
              value={nickname}
            />
            <Text style={styles.characterCount}>{nicknameLength}/50</Text>
          </View>
        </View>

        <SectionCard description="마이페이지와 홈에서 함께할 팔방이를 선택해요." title="내 캐릭터">
          <View accessibilityRole="radiogroup" style={styles.characterRow}>
            {CHARACTER_OPTIONS.map((option) => {
              const selected = selectedCharacterId === option.value;

              return (
                <Pressable
                  accessibilityLabel={option.label}
                  accessibilityRole="radio"
                  accessibilityState={{ selected }}
                  key={option.value}
                  onPress={() => {
                    clearServerError();
                    setSelectedCharacterId(option.value);
                  }}
                  style={({ pressed }) => [
                    styles.characterCard,
                    selected && styles.characterCardSelected,
                    pressed && styles.pressed,
                  ]}
                >
                  <View style={styles.characterImageArea}>
                    <Image
                      resizeMode="contain"
                      source={option.image}
                      style={styles.characterImage}
                    />
                  </View>
                  <Text
                    numberOfLines={1}
                    style={[styles.characterLabel, selected && styles.characterLabelSelected]}
                  >
                    {option.label}
                  </Text>
                  {selected ? (
                    <View style={styles.characterCheck}>
                      <Ionicons color={SURFACE_COLOR} name="checkmark" size={12} />
                    </View>
                  ) : null}
                </Pressable>
              );
            })}
          </View>
        </SectionCard>

        <SectionCard description="연령대와 공개 여부는 따로 변경할 수 있어요." title="공개 프로필">
          <Text style={styles.fieldLabel}>연령대</Text>
          <View accessibilityRole="radiogroup" style={styles.choiceGrid}>
            {AGE_GROUP_OPTIONS.map((option) => (
              <ChoiceButton
                key={option.value}
                label={option.label}
                onPress={() => {
                  clearServerError();
                  setAgeGroup(option.value);
                }}
                selected={ageGroup === option.value}
              />
            ))}
          </View>
          <View style={styles.subsectionDivider} />
          <Text style={styles.fieldLabel}>다른 사용자에게 연령대 공개</Text>
          <View accessibilityRole="radiogroup" style={styles.binaryRow}>
            <ChoiceButton
              label="공개"
              onPress={() => {
                clearServerError();
                setAgeGroupPublicAgreed(true);
              }}
              selected={ageGroupPublicAgreed}
            />
            <ChoiceButton
              label="비공개"
              onPress={() => {
                clearServerError();
                setAgeGroupPublicAgreed(false);
              }}
              selected={!ageGroupPublicAgreed}
            />
          </View>
        </SectionCard>

        <SectionCard description="관심 조건을 최대 4개까지 선택해 주세요." title="임장 관심 조건">
          <Text style={styles.fieldLabel}>임장 목적</Text>
          <View accessibilityRole="radiogroup" style={styles.purposeRow}>
            {PURPOSE_OPTIONS.map((option) => {
              const selected = purpose === option.value;

              return (
                <Pressable
                  accessibilityRole="radio"
                  accessibilityState={{ selected }}
                  key={option.value}
                  onPress={() => {
                    clearServerError();
                    setPurpose(option.value);
                  }}
                  style={({ pressed }) => [
                    styles.purposeButton,
                    selected && styles.purposeButtonSelected,
                    pressed && styles.pressed,
                  ]}
                >
                  <Ionicons
                    color={selected ? PRIMARY_COLOR : MUTED_TEXT_COLOR}
                    name={option.icon as keyof typeof Ionicons.glyphMap}
                    size={21}
                  />
                  <Text
                    style={[styles.purposeButtonText, selected && styles.purposeButtonTextSelected]}
                  >
                    {option.label}
                  </Text>
                </Pressable>
              );
            })}
          </View>

          <View style={styles.subsectionDivider} />
          <View style={styles.fieldTitleRow}>
            <Text style={styles.fieldLabel}>우선순위</Text>
            <View style={styles.priorityCountBadge}>
              <Text style={styles.priorityCountText}>
                {priorities.length}/{MAX_PRIORITIES}
              </Text>
            </View>
          </View>
          <View style={styles.priorityWrap}>
            {PRIORITY_OPTIONS.map((option) => {
              const order = priorities.indexOf(option.value);
              const selected = order >= 0;
              const disabled = !selected && priorities.length >= MAX_PRIORITIES;

              return (
                <Pressable
                  accessibilityLabel={`${option.label}${selected ? ', 선택됨' : ''}`}
                  accessibilityRole="checkbox"
                  accessibilityState={{ checked: selected, disabled }}
                  disabled={disabled}
                  key={option.value}
                  onPress={() => togglePriority(option.value)}
                  style={({ pressed }) => [
                    styles.priorityChip,
                    selected && styles.priorityChipSelected,
                    disabled && styles.priorityChipDisabled,
                    pressed && styles.pressed,
                  ]}
                >
                  <Text style={styles.priorityIcon}>{option.icon}</Text>
                  <Text
                    style={[styles.priorityChipText, selected && styles.priorityChipTextSelected]}
                  >
                    {option.label}
                  </Text>
                </Pressable>
              );
            })}
          </View>
          {priorityClientError ? (
            <Text style={styles.fieldError}>{priorityClientError}</Text>
          ) : null}
        </SectionCard>

        <SectionCard description="아파트와 스터디 추천에만 활용해요." title="생활 조건">
          <Text style={styles.fieldLabel}>혼인 상태</Text>
          <View accessibilityRole="radiogroup" style={styles.binaryRow}>
            <ChoiceButton
              label="미혼"
              onPress={() => {
                clearServerError();
                setMaritalStatus('SINGLE');
              }}
              selected={maritalStatus === 'SINGLE'}
            />
            <ChoiceButton
              label="기혼"
              onPress={() => {
                clearServerError();
                setMaritalStatus('MARRIED');
              }}
              selected={maritalStatus === 'MARRIED'}
            />
          </View>

          <View style={styles.subsectionDivider} />
          <Text style={styles.fieldLabel}>미성년 자녀</Text>
          <View accessibilityRole="radiogroup" style={styles.binaryRow}>
            <ChoiceButton
              label="없음"
              onPress={() => {
                clearServerError();
                setHasChildren(false);
              }}
              selected={hasChildren === false}
            />
            <ChoiceButton
              label="있음"
              onPress={() => {
                clearServerError();
                setHasChildren(true);
              }}
              selected={hasChildren === true}
            />
          </View>

          <View style={styles.subsectionDivider} />
          <Text style={styles.fieldLabel}>차량</Text>
          <View accessibilityRole="radiogroup" style={styles.binaryRow}>
            <ChoiceButton
              label="없음"
              onPress={() => {
                clearServerError();
                setHasVehicle(false);
              }}
              selected={hasVehicle === false}
            />
            <ChoiceButton
              label="있음"
              onPress={() => {
                clearServerError();
                setHasVehicle(true);
              }}
              selected={hasVehicle === true}
            />
          </View>
        </SectionCard>

        {formError ? (
          <View style={styles.errorBanner}>
            <Ionicons color={ERROR_COLOR} name="alert-circle-outline" size={20} />
            <Text style={styles.errorBannerText}>{formError}</Text>
          </View>
        ) : null}
      </ScrollView>

      <View style={[styles.bottomBar, { paddingBottom: Math.max(bottomInset, 12) }]}>
        <Pressable
          accessibilityRole="button"
          accessibilityState={{ disabled: !canSubmit, busy: updateMutation.isPending }}
          disabled={!canSubmit}
          onPress={handleSubmit}
          style={({ pressed }) => [
            styles.saveButton,
            !canSubmit && styles.saveButtonDisabled,
            pressed && canSubmit && styles.saveButtonPressed,
          ]}
        >
          {updateMutation.isPending ? (
            <ActivityIndicator color={SURFACE_COLOR} />
          ) : (
            <Text style={[styles.saveButtonText, !canSubmit && styles.saveButtonTextDisabled]}>
              변경사항 저장
            </Text>
          )}
        </Pressable>
      </View>
    </KeyboardAvoidingView>
  );
}

export default function ProfileEditScreen() {
  const router = useRouter();
  const insets = useSafeAreaInsets();
  const sessionVersion = useAuthStore((state) => state.sessionVersion);
  const accessToken = useAuthStore((state) => state.accessToken);
  // TODO: 로그인 화면의 임시 로그인 제거 시 개발용 편집 미리보기도 함께 삭제합니다.
  const isTemporaryDevelopmentSession = __DEV__ && accessToken === 'dummy-access-token';
  const queryKey = useMemo(() => ['member', 'me', sessionVersion] as const, [sessionVersion]);
  const profileQuery = useQuery({
    queryKey,
    queryFn: getMyProfile,
    enabled: !isTemporaryDevelopmentSession,
  });
  const profile = isTemporaryDevelopmentSession ? DEVELOPMENT_PREVIEW_PROFILE : profileQuery.data;

  return (
    <View style={styles.screen}>
      <StatusBar style="dark" />
      <View style={[styles.header, { paddingTop: insets.top + 8 }]}>
        <Pressable
          accessibilityLabel="마이페이지로 돌아가기"
          accessibilityRole="button"
          onPress={() => router.back()}
          style={({ pressed }) => [styles.headerButton, pressed && styles.pressed]}
        >
          <Ionicons color={TEXT_COLOR} name="chevron-back" size={25} />
        </Pressable>
        <Text style={styles.headerTitle}>프로필 편집</Text>
        <View style={styles.headerButton} />
      </View>

      {profileQuery.isPending && !isTemporaryDevelopmentSession ? <LoadingState /> : null}
      {profileQuery.isError && !profileQuery.data ? (
        <ErrorState onRetry={() => void profileQuery.refetch()} />
      ) : null}
      {profile ? (
        <ProfileEditForm
          bottomInset={insets.bottom}
          isDevelopmentPreview={isTemporaryDevelopmentSession}
          profile={profile}
          queryKey={queryKey}
        />
      ) : null}
    </View>
  );
}

const styles = StyleSheet.create({
  flex: {
    flex: 1,
  },
  screen: {
    flex: 1,
    backgroundColor: SCREEN_BACKGROUND_COLOR,
  },
  header: {
    minHeight: 64,
    paddingHorizontal: 14,
    paddingBottom: 8,
    flexDirection: 'row',
    alignItems: 'center',
    justifyContent: 'space-between',
    backgroundColor: SCREEN_BACKGROUND_COLOR,
  },
  headerButton: {
    width: 44,
    height: 44,
    borderRadius: 22,
    alignItems: 'center',
    justifyContent: 'center',
  },
  headerTitle: {
    color: TEXT_COLOR,
    fontSize: 19,
    fontWeight: '900',
    letterSpacing: -0.5,
  },
  formContent: {
    paddingHorizontal: 20,
    paddingTop: 8,
    paddingBottom: 28,
    gap: 14,
  },
  introCard: {
    minHeight: 84,
    paddingHorizontal: 16,
    paddingVertical: 15,
    borderRadius: 20,
    flexDirection: 'row',
    alignItems: 'center',
    backgroundColor: SOFT_GREEN_COLOR,
  },
  introIcon: {
    width: 44,
    height: 44,
    marginRight: 12,
    borderRadius: 22,
    alignItems: 'center',
    justifyContent: 'center',
    backgroundColor: SURFACE_COLOR,
  },
  introTextArea: {
    flex: 1,
  },
  introTitle: {
    color: DARK_GREEN_COLOR,
    fontSize: 15,
    fontWeight: '900',
    letterSpacing: -0.3,
  },
  introDescription: {
    marginTop: 5,
    color: MUTED_TEXT_COLOR,
    fontSize: 12,
    fontWeight: '600',
    lineHeight: 18,
  },
  sectionCard: {
    paddingHorizontal: 17,
    paddingVertical: 18,
    borderWidth: 1,
    borderColor: BORDER_COLOR,
    borderRadius: 22,
    backgroundColor: SURFACE_COLOR,
  },
  identityCard: {
    backgroundColor: SOFT_BACKGROUND_COLOR,
  },
  sectionTitle: {
    color: TEXT_COLOR,
    fontSize: 18,
    fontWeight: '900',
    letterSpacing: -0.45,
  },
  sectionDescription: {
    marginTop: 5,
    color: MUTED_TEXT_COLOR,
    fontSize: 12,
    fontWeight: '600',
    lineHeight: 18,
  },
  sectionBody: {
    marginTop: 18,
  },
  characterCount: {
    marginTop: 6,
    color: PLACEHOLDER_COLOR,
    fontSize: 11,
    fontWeight: '700',
    textAlign: 'right',
  },
  characterRow: {
    flexDirection: 'row',
    gap: 8,
  },
  characterCard: {
    position: 'relative',
    flex: 1,
    minWidth: 0,
    paddingHorizontal: 4,
    paddingVertical: 12,
    borderWidth: 1.5,
    borderColor: BORDER_COLOR,
    borderRadius: 18,
    alignItems: 'center',
    backgroundColor: SURFACE_COLOR,
  },
  characterCardSelected: {
    borderColor: PRIMARY_COLOR,
    backgroundColor: SOFT_GREEN_COLOR,
  },
  characterImageArea: {
    width: 66,
    height: 66,
    alignItems: 'center',
    justifyContent: 'center',
  },
  characterImage: {
    width: 62,
    height: 62,
  },
  characterLabel: {
    marginTop: 7,
    color: MUTED_TEXT_COLOR,
    fontSize: 11,
    fontWeight: '800',
  },
  characterLabelSelected: {
    color: DARK_GREEN_COLOR,
  },
  characterCheck: {
    position: 'absolute',
    top: 8,
    right: 8,
    width: 18,
    height: 18,
    borderRadius: 9,
    alignItems: 'center',
    justifyContent: 'center',
    backgroundColor: PRIMARY_COLOR,
  },
  fieldLabel: {
    marginBottom: 10,
    color: TEXT_COLOR,
    fontSize: 14,
    fontWeight: '800',
  },
  fieldTitleRow: {
    marginBottom: 10,
    flexDirection: 'row',
    alignItems: 'center',
    justifyContent: 'space-between',
  },
  priorityCountBadge: {
    minWidth: 48,
    paddingHorizontal: 11,
    paddingVertical: 6,
    borderRadius: 14,
    alignItems: 'center',
    backgroundColor: SOFT_GREEN_COLOR,
  },
  priorityCountText: {
    color: PRIMARY_COLOR,
    fontSize: 12,
    fontWeight: '900',
  },
  choiceGrid: {
    flexDirection: 'row',
    flexWrap: 'wrap',
    gap: 8,
  },
  choiceButton: {
    minWidth: '31%',
    minHeight: 46,
    paddingHorizontal: 12,
    borderWidth: 1.5,
    borderColor: BORDER_COLOR,
    borderRadius: 15,
    flex: 1,
    flexDirection: 'row',
    alignItems: 'center',
    justifyContent: 'center',
    gap: 6,
    backgroundColor: SURFACE_COLOR,
  },
  choiceButtonSelected: {
    borderColor: PRIMARY_COLOR,
    backgroundColor: SOFT_GREEN_COLOR,
  },
  choiceButtonText: {
    color: MUTED_TEXT_COLOR,
    fontSize: 13,
    fontWeight: '800',
  },
  choiceButtonTextSelected: {
    color: DARK_GREEN_COLOR,
  },
  binaryRow: {
    flexDirection: 'row',
    gap: 9,
  },
  subsectionDivider: {
    height: 1,
    marginVertical: 20,
    backgroundColor: BORDER_COLOR,
  },
  purposeRow: {
    flexDirection: 'row',
    gap: 8,
  },
  purposeButton: {
    flex: 1,
    minWidth: 0,
    minHeight: 74,
    paddingHorizontal: 5,
    borderWidth: 1.5,
    borderColor: BORDER_COLOR,
    borderRadius: 17,
    alignItems: 'center',
    justifyContent: 'center',
    gap: 7,
  },
  purposeButtonSelected: {
    borderColor: PRIMARY_COLOR,
    backgroundColor: SOFT_GREEN_COLOR,
  },
  purposeButtonText: {
    color: MUTED_TEXT_COLOR,
    fontSize: 12,
    fontWeight: '800',
  },
  purposeButtonTextSelected: {
    color: DARK_GREEN_COLOR,
  },
  priorityWrap: {
    flexDirection: 'row',
    flexWrap: 'wrap',
    gap: 8,
  },
  priorityChip: {
    minHeight: 42,
    paddingHorizontal: 13,
    borderWidth: 1.5,
    borderColor: BORDER_COLOR,
    borderRadius: 21,
    flexDirection: 'row',
    alignItems: 'center',
    gap: 6,
    backgroundColor: SURFACE_COLOR,
  },
  priorityChipSelected: {
    borderColor: PRIMARY_COLOR,
    backgroundColor: SOFT_GREEN_COLOR,
  },
  priorityChipDisabled: {
    opacity: 0.42,
  },
  priorityIcon: {
    fontSize: 13,
  },
  priorityChipText: {
    color: MUTED_TEXT_COLOR,
    fontSize: 12,
    fontWeight: '800',
  },
  priorityChipTextSelected: {
    color: DARK_GREEN_COLOR,
  },
  fieldError: {
    marginTop: 9,
    color: ERROR_COLOR,
    fontSize: 12,
    fontWeight: '700',
  },
  errorBanner: {
    paddingHorizontal: 14,
    paddingVertical: 13,
    borderRadius: 15,
    flexDirection: 'row',
    alignItems: 'center',
    gap: 9,
    backgroundColor: ERROR_BACKGROUND_COLOR,
  },
  errorBannerText: {
    flex: 1,
    color: ERROR_COLOR,
    fontSize: 12,
    fontWeight: '700',
    lineHeight: 18,
  },
  bottomBar: {
    paddingTop: 10,
    paddingHorizontal: 20,
    borderTopWidth: 1,
    borderTopColor: BORDER_COLOR,
    backgroundColor: SURFACE_COLOR,
  },
  saveButton: {
    height: 54,
    borderRadius: 18,
    alignItems: 'center',
    justifyContent: 'center',
    backgroundColor: PRIMARY_COLOR,
  },
  saveButtonDisabled: {
    backgroundColor: SOFT_BACKGROUND_COLOR,
  },
  saveButtonPressed: {
    opacity: 0.78,
  },
  saveButtonText: {
    color: SURFACE_COLOR,
    fontSize: 15,
    fontWeight: '900',
  },
  saveButtonTextDisabled: {
    color: PLACEHOLDER_COLOR,
  },
  stateContainer: {
    flex: 1,
    paddingHorizontal: 30,
    alignItems: 'center',
    justifyContent: 'center',
  },
  stateIcon: {
    width: 54,
    height: 54,
    marginBottom: 15,
    borderRadius: 27,
    alignItems: 'center',
    justifyContent: 'center',
    backgroundColor: BUTTON_BACKGROUND_COLOR,
  },
  stateTitle: {
    color: TEXT_COLOR,
    fontSize: 17,
    fontWeight: '900',
  },
  stateDescription: {
    marginTop: 7,
    color: MUTED_TEXT_COLOR,
    fontSize: 13,
    fontWeight: '600',
    textAlign: 'center',
  },
  retryButton: {
    marginTop: 18,
    paddingHorizontal: 20,
    paddingVertical: 11,
    borderRadius: 16,
    backgroundColor: BUTTON_BACKGROUND_COLOR,
  },
  retryButtonText: {
    color: PRIMARY_COLOR,
    fontSize: 13,
    fontWeight: '900',
  },
  pressed: {
    opacity: 0.66,
  },
});
