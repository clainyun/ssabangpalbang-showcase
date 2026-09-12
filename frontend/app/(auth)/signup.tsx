import { useRef, useState } from 'react';
import { router } from 'expo-router';
import {
  ActivityIndicator,
  KeyboardAvoidingView,
  Platform,
  Pressable,
  ScrollView,
  StyleSheet,
  Text,
  TextInput,
  View,
} from 'react-native';
import Svg, { Path } from 'react-native-svg';
import { LinearGradient } from 'expo-linear-gradient';
import { useSafeAreaInsets } from 'react-native-safe-area-context';

import { useAuthStore } from '@/store/authStore';
import { signup, SignupError, type SignupErrorField } from '@/features/auth/api/signup';
import { takePostAuthRoute } from '@/features/navigation/pendingAppLinkStore';
import { FormField } from '@/components/FormField';
import {
  SignupConsentFields,
  type SignupConsents,
} from '@/components/SignupConsentFields';
import { PasswordRequirements } from '@/features/auth/PasswordRequirements';
import { PASSWORD_REGEX, passwordsMatch, validatePassword } from '@/features/auth/passwordValidation';
import { useAvailabilityCheck } from '@/features/auth/useAvailabilityCheck';
import {
  checkEmailAvailability,
  checkNicknameAvailability,
} from '@/features/auth/api/availability';
import {
  BUTTON_BACKGROUND_COLOR,
  ERROR_BACKGROUND_COLOR,
  ERROR_COLOR,
  PLACEHOLDER_COLOR,
  PRIMARY_COLOR,
  TEXT_COLOR,
} from '@/constants/colors';

// 배경 그라데이션: 상단 0%는 연한 그린, 90% 지점부터는 완전한 흰색이라
// 실제로 색이 보이는 구간은 화면 위쪽 90%뿐이고 나머지는 흰 배경입니다.
const GRADIENT_COLORS = ['#E5F7ED', '#FFFFFF', '#FFFFFF'] as const;
const GRADIENT_LOCATIONS = [0, 0.9, 1] as const;

const EMAIL_REGEX = /^[^\s@]+@[^\s@]+\.[^\s@]+$/;
// PASSWORD_REGEX(영문+숫자 포함 8~64자)는 재설정 화면과 공유하는 유틸에서 가져옵니다.

type FieldErrors = Partial<Record<SignupErrorField, string>>;

function validate(email: string, password: string, nickname: string): FieldErrors {
  const errors: FieldErrors = {};

  if (!email) {
    errors.email = '이메일을 입력해주세요.';
  } else if (email.length > 255) {
    errors.email = '이메일은 최대 255자까지 입력할 수 있습니다.';
  } else if (!EMAIL_REGEX.test(email)) {
    errors.email = '올바른 이메일 형식이 아닙니다.';
  }

  if (!password) {
    errors.password = '비밀번호를 입력해주세요.';
  } else if (password.length < 8 || password.length > 64) {
    errors.password = '비밀번호는 8~64자여야 합니다.';
  } else if (!PASSWORD_REGEX.test(password)) {
    errors.password = '비밀번호는 영문과 숫자를 포함해야 합니다.';
  }

  if (!nickname) {
    errors.nickname = '닉네임을 입력해주세요.';
  } else if (nickname.length > 50) {
    errors.nickname = '닉네임은 최대 50자까지 입력할 수 있습니다.';
  }

  return errors;
}

function BackButton() {
  return (
    <Pressable
      onPress={() => router.back()}
      hitSlop={12}
      style={styles.backButton}
    >
      <Svg width={24} height={24} viewBox="0 0 24 24" fill="none" stroke={TEXT_COLOR} strokeWidth={2}>
        <Path d="M15 5l-7 7 7 7" strokeLinecap="round" strokeLinejoin="round" />
      </Svg>
    </Pressable>
  );
}

/** 회원가입 화면 (§14). POST /api/v1/auth/signup 호출 후 온보딩 완료 여부로 분기합니다. */
export default function SignupScreen() {
  const signIn = useAuthStore((s) => s.signIn);
  // 안드로이드 시스템 내비게이션 바(제스처 바 vs 3버튼 바) 높이에 맞춰
  // 하단 버튼이 가리지 않도록 실시간으로 반영 (탭 바와 동일한 패턴)
  const insets = useSafeAreaInsets();
  // 이메일 → 비밀번호 → 비밀번호 확인 → 닉네임 순으로 키보드 "다음"으로 포커스를 이동시키기 위한 ref
  const passwordInputRef = useRef<TextInput>(null);
  const passwordConfirmInputRef = useRef<TextInput>(null);
  const nicknameInputRef = useRef<TextInput>(null);

  const [email, setEmail] = useState('');
  const [password, setPassword] = useState('');
  const [passwordConfirm, setPasswordConfirm] = useState('');
  const [nickname, setNickname] = useState('');
  const [fieldErrors, setFieldErrors] = useState<FieldErrors>({});
  const [formError, setFormError] = useState<string | undefined>(undefined);
  const [isSubmitting, setIsSubmitting] = useState(false);
  const [consents, setConsents] = useState<SignupConsents>({ terms: false, privacy: false });
  const isAllConsented = consents.terms && consents.privacy;

  // 실시간 검증 파생값
  const emailFormatValid = EMAIL_REGEX.test(email) && email.length <= 255;
  const nicknameFormatValid = nickname.length > 0 && nickname.length <= 50;
  const passwordChecks = validatePassword(password);
  const confirmMatches = passwordsMatch(password, passwordConfirm);

  // 형식이 유효할 때만 debounce 후 중복확인을 호출합니다(경합/취소는 훅이 처리).
  const emailStatus = useAvailabilityCheck(email, {
    enabled: emailFormatValid,
    check: checkEmailAvailability,
  });
  const nicknameStatus = useAvailabilityCheck(nickname, {
    enabled: nicknameFormatValid,
    check: checkNicknameAvailability,
  });

  // 이메일 입력창 아래 실시간 안내(에러가 있으면 FormField가 에러를 우선 표시).
  const emailHint = (() => {
    if (!email) return undefined;
    if (!emailFormatValid) return { text: '올바른 이메일 형식을 입력해주세요.', tone: 'muted' as const };
    if (emailStatus === 'checking') return { text: '이메일 확인 중…', tone: 'muted' as const };
    if (emailStatus === 'available') return { text: '사용 가능한 이메일이에요.', tone: 'success' as const };
    if (emailStatus === 'taken') return { text: '이미 사용 중인 이메일이에요.', tone: 'error' as const };
    if (emailStatus === 'error') return { text: '이메일 확인에 실패했어요. 다시 시도해 주세요.', tone: 'muted' as const };
    return undefined;
  })();

  const nicknameHint = (() => {
    if (!nickname) return undefined;
    if (!nicknameFormatValid) return { text: '닉네임은 최대 50자까지 입력할 수 있어요.', tone: 'muted' as const };
    if (nicknameStatus === 'checking') return { text: '닉네임 확인 중…', tone: 'muted' as const };
    if (nicknameStatus === 'available') return { text: '사용 가능한 닉네임이에요.', tone: 'success' as const };
    if (nicknameStatus === 'taken') return { text: '이미 사용 중인 닉네임이에요.', tone: 'error' as const };
    if (nicknameStatus === 'error') return { text: '닉네임 확인에 실패했어요. 다시 시도해 주세요.', tone: 'muted' as const };
    return undefined;
  })();

  const confirmHint = passwordConfirm
    ? confirmMatches
      ? { text: '비밀번호가 일치해요.', tone: 'success' as const }
      : { text: '비밀번호가 일치하지 않습니다.', tone: 'error' as const }
    : undefined;

  // 가입 버튼 활성 조건: 약관 동의 + 비밀번호 규칙 충족 + 비밀번호 확인 일치 + 이메일/닉네임 사용 가능
  const canSubmit =
    isAllConsented &&
    passwordChecks.isValid &&
    confirmMatches &&
    emailStatus === 'available' &&
    nicknameStatus === 'available';

  const handleSubmit = async () => {
    if (isSubmitting || !canSubmit) return;

    const validationErrors = validate(email, password, nickname);
    setFieldErrors(validationErrors);
    setFormError(undefined);
    if (Object.keys(validationErrors).length > 0) return;

    setIsSubmitting(true);
    try {
      const data = await signup({ email, password, nickname });
      await signIn(data.accessToken, data.refreshToken, data.onboardingCompleted);
      router.replace(data.onboardingCompleted ? takePostAuthRoute() : '/(auth)/onboarding');
    } catch (err) {
      if (err instanceof SignupError && err.field) {
        setFieldErrors((prev) => ({ ...prev, [err.field as SignupErrorField]: err.message }));
      } else if (err instanceof SignupError) {
        setFormError(err.message);
      } else {
        setFormError('회원가입에 실패했습니다. 잠시 후 다시 시도해주세요.');
      }
    } finally {
      setIsSubmitting(false);
    }
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
          {/* 상단 헤더: 뒤로가기 + 타이틀 */}
          <BackButton />

          <Text style={styles.title}>회원가입</Text>
          <Text style={styles.subtitle}>싸방팔방에서 임장을 시작해보세요.</Text>

          {/* 입력 폼: 이메일 → 비밀번호 → 닉네임 순.
              textContentType="username"은 오타 아님 — 이메일 필드라도 iOS에서
              비밀번호(new-password)와 한 쌍으로 인식되게 하려면 emailAddress가 아니라
              username이어야 합니다. */}
          <View style={styles.form}>
            <FormField
              label="이메일"
              value={email}
              onChangeText={setEmail}
              error={fieldErrors.email}
              keyboardType="email-address"
              placeholder="example@email.com"
              autoComplete="email"
              textContentType="username"
              returnKeyType="next"
              blurOnSubmit={false}
              onSubmitEditing={() => passwordInputRef.current?.focus()}
              hint={emailHint?.text}
              hintTone={emailHint?.tone}
              trailing={
                emailStatus === 'checking' ? (
                  <ActivityIndicator size="small" color={PLACEHOLDER_COLOR} />
                ) : undefined
              }
            />
            {/* 새로 만드는 비밀번호이므로 new-password/newPassword를 사용합니다.
                (로그인 화면은 기존 비밀번호를 불러오는 것이라 password를 씁니다) */}
            <FormField
              ref={passwordInputRef}
              label="비밀번호"
              value={password}
              onChangeText={setPassword}
              error={fieldErrors.password}
              secureTextEntry
              placeholder="영문, 숫자 포함 8~64자"
              autoComplete="new-password"
              textContentType="newPassword"
              returnKeyType="next"
              blurOnSubmit={false}
              onSubmitEditing={() => passwordConfirmInputRef.current?.focus()}
            />
            {/* 비밀번호를 입력하기 시작하면 규칙을 개별 조건으로 실시간 표시합니다. */}
            {password.length > 0 && <PasswordRequirements password={password} />}
            <FormField
              ref={passwordConfirmInputRef}
              label="비밀번호 확인"
              value={passwordConfirm}
              onChangeText={setPasswordConfirm}
              secureTextEntry
              placeholder="비밀번호 다시 입력"
              autoComplete="new-password"
              textContentType="newPassword"
              returnKeyType="next"
              blurOnSubmit={false}
              onSubmitEditing={() => nicknameInputRef.current?.focus()}
              hint={confirmHint?.text}
              hintTone={confirmHint?.tone}
            />
            <FormField
              ref={nicknameInputRef}
              label="닉네임"
              value={nickname}
              onChangeText={setNickname}
              error={fieldErrors.nickname}
              placeholder="최대 50자"
              returnKeyType="done"
              onSubmitEditing={handleSubmit}
              hint={nicknameHint?.text}
              hintTone={nicknameHint?.tone}
              trailing={
                nicknameStatus === 'checking' ? (
                  <ActivityIndicator size="small" color={PLACEHOLDER_COLOR} />
                ) : undefined
              }
            />

            {/* 필드 에러(fieldErrors)로 표시되지 않는 공통 실패만 여기 표시됩니다 */}
            {!!formError && (
              <View style={styles.formErrorBox}>
                <Text style={styles.formErrorText}>{formError}</Text>
              </View>
            )}
          </View>
          <SignupConsentFields consents={consents} onChange={setConsents} />
        </ScrollView>

        {/* 하단 고정 영역: 스크롤과 별개로 화면 아래에 항상 붙어 있는 제출 버튼 */}
        <View style={[styles.bottomBar, { paddingBottom: Math.max(24, insets.bottom + 8) }]}>
          <Pressable
            onPress={handleSubmit}
            disabled={isSubmitting || !canSubmit}
            style={[
              styles.submitButton,
              (isSubmitting || !canSubmit) && styles.submitButtonDisabled,
            ]}
          >
            {isSubmitting ? (
              <ActivityIndicator color="#FFFFFF" />
            ) : (
              <Text style={styles.submitButtonText}>가입하기</Text>
            )}
          </Pressable>
        </View>
      </KeyboardAvoidingView>
    </LinearGradient>
  );
}

const styles = StyleSheet.create({
  // 화면 전체 레이아웃
  flex: { flex: 1 },
  scroll: {
    flex: 1,
  },
  container: {
    padding: 24,
    paddingTop: 56,
  },

  // 상단 헤더 (뒤로가기 + 타이틀)
  backButton: {
    width: 40,
    height: 40,
    alignItems: 'center',
    justifyContent: 'center',
    marginLeft: -8,
  },
  title: {
    marginTop: 24,
    fontSize: 24,
    fontWeight: '800',
    color: TEXT_COLOR,
  },
  subtitle: {
    marginTop: 8,
    fontSize: 14,
    color: PLACEHOLDER_COLOR,
  },

  // 입력 폼
  form: {
    marginTop: 32,
    gap: 14,
  },

  // 폼 레벨 에러 (필드에 못 붙이는 공통 실패 메시지)
  formErrorBox: {
    backgroundColor: ERROR_BACKGROUND_COLOR,
    borderRadius: 12,
    padding: 14,
  },
  formErrorText: {
    fontSize: 13,
    color: ERROR_COLOR,
  },

  // 하단 고정 영역 (제출 버튼). paddingBottom은 시스템 내비게이션 바 높이에 맞춰
  // 화면에서 동적으로 덮어씁니다 (아래 24는 그 값이 더 작을 때의 최소 여백).
  bottomBar: {
    paddingHorizontal: 24,
    paddingTop: 12,
    paddingBottom: 24,
    backgroundColor: '#FFFFFF',
  },
  submitButton: {
    height: 54,
    borderRadius: 16,
    backgroundColor: BUTTON_BACKGROUND_COLOR,
    alignItems: 'center',
    justifyContent: 'center',
  },
  submitButtonDisabled: {
    opacity: 0.6,
  },
  submitButtonText: {
    fontSize: 16,
    fontWeight: '700',
    color: PRIMARY_COLOR,
  },
});
