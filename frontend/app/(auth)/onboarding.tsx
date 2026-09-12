import { useState } from 'react';
import { router } from 'expo-router';
import {
  ActivityIndicator,
  KeyboardAvoidingView,
  Platform,
  Pressable,
  ScrollView,
  StyleSheet,
  Text,
  View,
} from 'react-native';
import { LinearGradient } from 'expo-linear-gradient';
import { useSafeAreaInsets } from 'react-native-safe-area-context';
import Svg, { Path } from 'react-native-svg';

import {
  AuthSessionChangedError,
  AuthSessionExpiredError,
} from '@/lib/authenticatedFetch';
import { useMemberStore } from '@/store/memberStore';
import { takePostAuthRoute } from '@/features/navigation/pendingAppLinkStore';
import { useAuthStore } from '@/store/authStore';
import { ProgressBar } from '@/components/ProgressBar';
import {
  saveOnboarding,
  OnboardingError,
  type AgeGroup,
  type MaritalStatus,
  type Purpose,
  type Priority,
  type CharacterId,
  type OnboardingErrorField,
} from '@/features/onboarding/api/saveOnboarding';
import { AgeGroupStep } from '@/features/onboarding/steps/AgeGroupStep';
import { HouseholdStep } from '@/features/onboarding/steps/HouseholdStep';
import { PurposeStep } from '@/features/onboarding/steps/PurposeStep';
import { PrioritiesStep, MAX_PRIORITIES } from '@/features/onboarding/steps/PrioritiesStep';
import { CharacterStep } from '@/features/onboarding/steps/CharacterStep';
import {
  BUTTON_BACKGROUND_COLOR,
  ERROR_BACKGROUND_COLOR,
  ERROR_COLOR,
  PRIMARY_COLOR,
  TEXT_COLOR,
} from '@/constants/colors';

// 회원가입/로그인 화면과 톤앤매너 통일 (동일한 그라데이션 값)
const GRADIENT_COLORS = ['#E5F7ED', '#FFFFFF', '#FFFFFF'] as const;
const GRADIENT_LOCATIONS = [0, 0.9, 1] as const;
const TOTAL_STEPS = 5;

interface Answers {
  ageGroup: AgeGroup | null;
  ageGroupPublicAgreed: boolean | null;
  maritalStatus: MaritalStatus | null;
  hasChildren: boolean | null;
  hasVehicle: boolean | null;
  purpose: Purpose | null;
  priorities: Priority[];
  selectedCharacterId: CharacterId | null;
}

const INITIAL_ANSWERS: Answers = {
  ageGroup: null,
  ageGroupPublicAgreed: null,
  maritalStatus: null,
  hasChildren: null,
  hasVehicle: null,
  purpose: null,
  priorities: [],
  selectedCharacterId: null,
};

// 에러 응답의 field가 어느 단계에 속하는지 매핑합니다. 각 단계의 "다음" 버튼은
// 필수값을 다 고르기 전까진 비활성이라 정상 플로우에서는 거의 발생하지 않는
// 방어 코드지만, 발생하면 해당 단계로 돌아가서 에러를 보여줍니다.
const FIELD_TO_STEP: Record<OnboardingErrorField, number> = {
  ageGroup: 1,
  ageGroupPublicAgreed: 1,
  maritalStatus: 2,
  hasVehicle: 2,
  hasChildren: 2,
  purpose: 3,
  priorities: 4,
  selectedCharacterId: 5,
};

function BackButton({ onPress }: { onPress: () => void }) {
  return (
    <Pressable onPress={onPress} hitSlop={12} style={styles.backButton}>
      <Svg width={24} height={24} viewBox="0 0 24 24" fill="none" stroke={TEXT_COLOR} strokeWidth={2}>
        <Path d="M15 5l-7 7 7 7" strokeLinecap="round" strokeLinejoin="round" />
      </Svg>
    </Pressable>
  );
}

/** 온보딩 5단계 (§FE-002). 화면 전환 없이 이 화면 안에서 step 상태만 바꿔가며
 * 진행하고, 마지막 단계(캐릭터 선택)의 "완료" 버튼에서 한 번에 API를 호출합니다.
 * 이렇게 하면 단계 간 입력값이 이 컴포넌트의 로컬 state에 자연히 유지됩니다. */
export default function OnboardingScreen() {
  const setMemberProfile = useMemberStore((s) => s.setProfile);
  const completeOnboarding = useAuthStore((s) => s.completeOnboarding);
  const insets = useSafeAreaInsets();

  const [step, setStep] = useState(1);
  const [answers, setAnswers] = useState<Answers>(INITIAL_ANSWERS);
  const [stepError, setStepError] = useState<string | undefined>(undefined);
  const [isSubmitting, setIsSubmitting] = useState(false);

  const updateAnswers = (patch: Partial<Answers>) => {
    setAnswers((prev) => ({ ...prev, ...patch }));
  };

  // 함수형 업데이트로 처리해야 칩을 빠르게 연속으로 눌러도 매 탭이 그 시점의
  // 최신 상태를 기준으로 계산됩니다 (자세한 이유는 PrioritiesStep 참고).
  const handleTogglePriority = (priority: Priority) => {
    setAnswers((prev) => {
      const alreadySelectedIndex = prev.priorities.indexOf(priority);
      if (alreadySelectedIndex >= 0) {
        return { ...prev, priorities: prev.priorities.filter((value) => value !== priority) };
      }
      if (prev.priorities.length >= MAX_PRIORITIES) {
        return prev;
      }
      return { ...prev, priorities: [...prev.priorities, priority] };
    });
  };

  const goToStep = (nextStep: number) => {
    setStepError(undefined);
    setStep(nextStep);
  };

  const canProceed = (() => {
    switch (step) {
      case 1:
        return answers.ageGroup !== null && answers.ageGroupPublicAgreed !== null;
      case 2:
        return (
          answers.maritalStatus !== null &&
          answers.hasChildren !== null &&
          answers.hasVehicle !== null
        );
      case 3:
        return answers.purpose !== null;
      case 4:
        return answers.priorities.length >= 1;
      case 5:
        return answers.selectedCharacterId !== null;
      default:
        return false;
    }
  })();

  const handleSubmit = async () => {
    if (
      isSubmitting ||
      !answers.purpose ||
      !answers.maritalStatus ||
      answers.hasVehicle === null ||
      answers.hasChildren === null ||
      !answers.ageGroup ||
      answers.ageGroupPublicAgreed === null ||
      !answers.selectedCharacterId
    ) {
      return;
    }

    setIsSubmitting(true);
    setStepError(undefined);
    try {
      const data = await saveOnboarding({
        purpose: answers.purpose,
        maritalStatus: answers.maritalStatus,
        hasVehicle: answers.hasVehicle,
        hasChildren: answers.hasChildren,
        priorities: answers.priorities,
        ageGroup: answers.ageGroup,
        ageGroupPublicAgreed: answers.ageGroupPublicAgreed,
        selectedCharacterId: answers.selectedCharacterId,
      });

      setMemberProfile({
        selectedCharacterId: data.selectedCharacterId,
        ageGroupPublicAgreed: data.ageGroupPublicAgreed,
      });
      const postAuthRoute = takePostAuthRoute();
      // 온보딩 완료 → 플래그 해제. 이걸 안 하면 홈으로 이동해도 AuthLayout 이 다시
      // 온보딩으로 되돌립니다.
      completeOnboarding();
      router.replace(postAuthRoute);
    } catch (err) {
      // 로그아웃은 authenticatedFetch가 이미 처리했으므로 여기서는 이동만 합니다.
      if (err instanceof AuthSessionExpiredError || err instanceof AuthSessionChangedError) {
        router.replace('/(auth)/login');
        return;
      }
      if (err instanceof OnboardingError) {
        if (err.field) {
          goToStep(FIELD_TO_STEP[err.field]);
        }
        setStepError(err.message);
      } else {
        setStepError('온보딩 정보를 저장하지 못했습니다. 잠시 후 다시 시도해주세요.');
      }
    } finally {
      setIsSubmitting(false);
    }
  };

  const handleNext = () => {
    if (!canProceed) return;
    if (step < TOTAL_STEPS) {
      goToStep(step + 1);
      return;
    }
    void handleSubmit();
  };

  return (
    <LinearGradient colors={GRADIENT_COLORS} locations={GRADIENT_LOCATIONS} style={styles.flex}>
      <KeyboardAvoidingView
        style={styles.flex}
        behavior={Platform.OS === 'ios' ? 'padding' : undefined}
      >
        <ScrollView
          style={styles.scroll}
          contentContainerStyle={styles.container}
          keyboardShouldPersistTaps="handled"
        >
          <View style={styles.header}>
            {/* 회원가입 직후 진입하는 첫 페이지(step 1)에서는 뒤로가기를 숨긴다.
                (뒤로 가 봐야 회원가입으로 돌아가는 흐름이라 노출하지 않는다.)
                진행바 위치가 흔들리지 않도록 같은 크기의 빈 자리를 남긴다. */}
            {step > 1 ? (
              <BackButton onPress={() => goToStep(step - 1)} />
            ) : (
              <View style={styles.backButton} />
            )}
            <View style={styles.progressWrap}>
              <ProgressBar step={step} totalSteps={TOTAL_STEPS} />
            </View>
          </View>

          {step === 1 && (
            <AgeGroupStep
              ageGroup={answers.ageGroup}
              onAgeGroupChange={(value) => updateAnswers({ ageGroup: value })}
              ageGroupPublicAgreed={answers.ageGroupPublicAgreed}
              onAgeGroupPublicAgreedChange={(value) => updateAnswers({ ageGroupPublicAgreed: value })}
            />
          )}
          {step === 2 && (
            <HouseholdStep
              maritalStatus={answers.maritalStatus}
              onMaritalStatusChange={(value) => updateAnswers({ maritalStatus: value })}
              hasChildren={answers.hasChildren}
              onHasChildrenChange={(value) => updateAnswers({ hasChildren: value })}
              hasVehicle={answers.hasVehicle}
              onHasVehicleChange={(value) => updateAnswers({ hasVehicle: value })}
            />
          )}
          {step === 3 && (
            <PurposeStep
              purpose={answers.purpose}
              onPurposeChange={(value) => updateAnswers({ purpose: value })}
            />
          )}
          {step === 4 && (
            <PrioritiesStep
              priorities={answers.priorities}
              onTogglePriority={handleTogglePriority}
            />
          )}
          {step === 5 && (
            <CharacterStep
              selectedCharacterId={answers.selectedCharacterId}
              onSelectedCharacterIdChange={(value) => updateAnswers({ selectedCharacterId: value })}
            />
          )}

          {!!stepError && (
            <View style={styles.errorBox}>
              <Text style={styles.errorText}>{stepError}</Text>
            </View>
          )}
        </ScrollView>

        <View style={[styles.bottomBar, { paddingBottom: Math.max(24, insets.bottom + 8) }]}>
          <Pressable
            onPress={handleNext}
            disabled={!canProceed || isSubmitting}
            style={[styles.nextButton, (!canProceed || isSubmitting) && styles.nextButtonDisabled]}
          >
            {isSubmitting ? (
              <ActivityIndicator color={PRIMARY_COLOR} />
            ) : (
              <Text style={styles.nextButtonText}>{step === TOTAL_STEPS ? '완료' : '다음'}</Text>
            )}
          </Pressable>
        </View>
      </KeyboardAvoidingView>
    </LinearGradient>
  );
}

const styles = StyleSheet.create({
  flex: { flex: 1 },
  scroll: { flex: 1 },
  container: {
    padding: 24,
    paddingTop: 56,
  },
  header: {
    flexDirection: 'row',
    alignItems: 'center',
    gap: 12,
    marginBottom: 32,
  },
  backButton: {
    width: 40,
    height: 40,
    alignItems: 'center',
    justifyContent: 'center',
    marginLeft: -8,
  },
  progressWrap: {
    flex: 1,
  },
  errorBox: {
    marginTop: 24,
    backgroundColor: ERROR_BACKGROUND_COLOR,
    borderRadius: 12,
    padding: 14,
  },
  errorText: {
    fontSize: 13,
    color: ERROR_COLOR,
  },
  bottomBar: {
    paddingHorizontal: 24,
    paddingTop: 12,
    backgroundColor: '#FFFFFF',
  },
  nextButton: {
    height: 54,
    borderRadius: 16,
    backgroundColor: BUTTON_BACKGROUND_COLOR,
    alignItems: 'center',
    justifyContent: 'center',
  },
  nextButtonDisabled: {
    opacity: 0.5,
  },
  nextButtonText: {
    fontSize: 16,
    fontWeight: '700',
    color: PRIMARY_COLOR,
  },
});
