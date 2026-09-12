import { useEffect, useRef, useState } from 'react';
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
import { LinearGradient } from 'expo-linear-gradient';
import { useSafeAreaInsets } from 'react-native-safe-area-context';
import Svg, { Path } from 'react-native-svg';

import { appAlert } from '@/components/AppDialog';
import { useAuthStore } from '@/store/authStore';
import { useSocialSignupStore } from '@/store/socialSignupStore';
import {
  socialSignup,
  SocialSignupError,
  type SocialSignupErrorField,
} from '@/features/auth/api/socialSignup';
import { FormField } from '@/components/FormField';
import {
  SignupConsentFields,
  type SignupConsents,
} from '@/components/SignupConsentFields';
import {
  BUTTON_BACKGROUND_COLOR,
  ERROR_BACKGROUND_COLOR,
  ERROR_COLOR,
  PLACEHOLDER_COLOR,
  PRIMARY_COLOR,
  TEXT_COLOR,
} from '@/constants/colors';

// signup.tsx와 동일한 값(§14) — 회원가입/로그인 화면과 톤앤매너 통일
const GRADIENT_COLORS = ['#E5F7ED', '#FFFFFF', '#FFFFFF'] as const;
const GRADIENT_LOCATIONS = [0, 0.9, 1] as const;

// signup.tsx의 이메일 검사와 동일한 규칙
const EMAIL_REGEX = /^[^\s@]+@[^\s@]+\.[^\s@]+$/;

type FieldErrors = Partial<Record<SocialSignupErrorField, string>>;

/** signup.tsx의 닉네임 검사와 동일한 규칙(최대 50자) — 그 화면도 공백만 입력하는 경우는
 * 별도로 막지 않고 백엔드 검증에 맡기므로 여기서도 똑같이 맞춥니다. */
function validate(email: string, emailRequired: boolean, nickname: string): FieldErrors {
  const errors: FieldErrors = {};

  if (emailRequired) {
    if (!email) {
      errors.email = '이메일을 입력해주세요.';
    } else if (email.length > 255) {
      errors.email = '이메일은 최대 255자까지 입력할 수 있습니다.';
    } else if (!EMAIL_REGEX.test(email)) {
      errors.email = '올바른 이메일 형식이 아닙니다.';
    }
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
    <Pressable onPress={() => router.back()} hitSlop={12} style={styles.backButton}>
      <Svg width={24} height={24} viewBox="0 0 24 24" fill="none" stroke={TEXT_COLOR} strokeWidth={2}>
        <Path d="M15 5l-7 7 7 7" strokeLinecap="round" strokeLinejoin="round" />
      </Svg>
    </Pressable>
  );
}

/** 소셜 로그인 응답의 signupRequired:true 뒤에 오는 마무리 화면. socialSignupToken은
 * useSocialSignupStore에만 있고 화면을 벗어나면(완료·뒤로가기 어느 쪽이든) 폐기합니다. */
export default function SocialSignupScreen() {
  const signIn = useAuthStore((s) => s.signIn);
  const socialSignupToken = useSocialSignupStore((s) => s.socialSignupToken);
  const emailRequired = useSocialSignupStore((s) => s.emailRequired);
  const clearSocialSignup = useSocialSignupStore((s) => s.clear);
  const insets = useSafeAreaInsets();
  const emailInputRef = useRef<TextInput>(null);
  const nicknameInputRef = useRef<TextInput>(null);

  const [email, setEmail] = useState('');
  const [nickname, setNickname] = useState('');
  const [fieldErrors, setFieldErrors] = useState<FieldErrors>({});
  const [formError, setFormError] = useState<string | undefined>(undefined);
  const [isSubmitting, setIsSubmitting] = useState(false);
  const [consents, setConsents] = useState<SignupConsents>({ terms: false, privacy: false });
  const isAllConsented = consents.terms && consents.privacy;

  // 토큰 없이(딥링크, 새로고침 등) 이 화면에 직접 들어온 경우 로그인으로 돌려보냅니다.
  useEffect(() => {
    if (!socialSignupToken) {
      router.replace('/(auth)/login');
    }
  }, [socialSignupToken]);

  // 화면을 벗어나면 뒤로가기든 완료든 항상 폐기 — 완료 시엔 아래에서 먼저 clear()를 호출하니
  // 여기선 중복 호출이라 아무 일도 안 일어납니다.
  useEffect(() => {
    return () => clearSocialSignup();
  }, [clearSocialSignup]);

  if (!socialSignupToken) {
    return null;
  }

  const handleSubmit = async () => {
    if (isSubmitting || !isAllConsented) return;

    const validationErrors = validate(email, emailRequired, nickname);
    setFieldErrors(validationErrors);
    setFormError(undefined);
    if (Object.keys(validationErrors).length > 0) return;

    setIsSubmitting(true);
    try {
      const data = await socialSignup({
        socialSignupToken,
        nickname,
        email: emailRequired ? email : undefined,
      });

      // 신규 가입 직후라 온보딩은 항상 미완료 → onboardingRequired=true 로 설정
      await signIn(data.accessToken, data.refreshToken, false);
      clearSocialSignup();
      router.replace('/(auth)/onboarding');
    } catch (err) {
      if (err instanceof SocialSignupError && err.redirectToLogin) {
        clearSocialSignup();
        appAlert('간편 회원가입 실패', err.message, [
          { text: '확인', onPress: () => router.replace('/(auth)/login') },
        ]);
        return;
      }
      if (err instanceof SocialSignupError && err.field) {
        setFieldErrors((prev) => ({ ...prev, [err.field as SocialSignupErrorField]: err.message }));
      } else if (err instanceof SocialSignupError) {
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
          <BackButton />

          <Text style={styles.title}>거의 다 왔어요</Text>
          <Text style={styles.subtitle}>
            {emailRequired
              ? '서비스에서 사용할 이메일과 닉네임을 입력해주세요.'
              : '서비스에서 사용할 닉네임을 입력해주세요.'}
          </Text>

          <View style={styles.form}>
            {emailRequired && (
              <FormField
                ref={emailInputRef}
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
                onSubmitEditing={() => nicknameInputRef.current?.focus()}
              />
            )}
            <FormField
              ref={nicknameInputRef}
              label="닉네임"
              value={nickname}
              onChangeText={setNickname}
              error={fieldErrors.nickname}
              placeholder="최대 50자"
              returnKeyType="done"
              onSubmitEditing={handleSubmit}
            />

            {!!formError && (
              <View style={styles.formErrorBox}>
                <Text style={styles.formErrorText}>{formError}</Text>
              </View>
            )}
          </View>
          <SignupConsentFields consents={consents} onChange={setConsents} />
        </ScrollView>

        <View style={[styles.bottomBar, { paddingBottom: Math.max(24, insets.bottom + 8) }]}>
          <Pressable
            onPress={handleSubmit}
            disabled={isSubmitting || !isAllConsented}
            style={[
              styles.submitButton,
              (isSubmitting || !isAllConsented) && styles.submitButtonDisabled,
            ]}
          >
            {isSubmitting ? (
              <ActivityIndicator color="#FFFFFF" />
            ) : (
              <Text style={styles.submitButtonText}>가입 완료</Text>
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
  form: {
    marginTop: 32,
    gap: 14,
  },
  formErrorBox: {
    backgroundColor: ERROR_BACKGROUND_COLOR,
    borderRadius: 12,
    padding: 14,
  },
  formErrorText: {
    fontSize: 13,
    color: ERROR_COLOR,
  },
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
