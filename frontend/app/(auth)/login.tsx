import { useEffect, useRef, useState } from 'react';
import { Link, router, useLocalSearchParams } from 'expo-router';
import {
  ActivityIndicator,
  Image,
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

import { useAuthStore } from '@/store/authStore';
import { useSocialSignupStore } from '@/store/socialSignupStore';
import { login, LoginError, type LoginErrorField } from '@/features/auth/api/login';
import { socialLogin, SocialLoginError } from '@/features/auth/api/socialLogin';
import {
  authorizeWithProvider,
  OAuthCancelledError,
  OAuthProviderNotConfiguredError,
  type SocialProvider,
} from '@/features/auth/oauth';
import {
  takePostAuthRoute,
  usePendingAppLinkStore,
} from '@/features/navigation/pendingAppLinkStore';
import { FormField } from '@/components/FormField';
import {
  BUTTON_BACKGROUND_COLOR,
  ERROR_BACKGROUND_COLOR,
  ERROR_COLOR,
  PLACEHOLDER_COLOR,
  PRIMARY_COLOR,
  TEXT_COLOR,
} from '@/constants/colors';

// 공식 로그인 버튼 이미지 원본 비율 (가로/세로) — Image에 aspectRatio로 그대로 넘겨 찌그러지지 않게 합니다.
const KAKAO_BUTTON_ASPECT_RATIO = 600 / 90;
const NAVER_BUTTON_ASPECT_RATIO = 1472 / 224;

// 배경 그라데이션: 상단 0%는 연한 그린, 90% 지점부터는 완전한 흰색이라
// 실제로 색이 보이는 구간은 화면 위쪽 90%뿐이고 나머지는 흰 배경입니다.
// (회원가입 화면과 동일한 값 — 톤앤매너 통일)
const GRADIENT_COLORS = ['#E5F7ED', '#FFFFFF', '#FFFFFF'] as const;
const GRADIENT_LOCATIONS = [0, 0.9, 1] as const;

const EMAIL_REGEX = /^[^\s@]+@[^\s@]+\.[^\s@]+$/;

/** RN의 style aspectRatio + width:'100%' 조합이 일부 환경에서 레이아웃 전에 이미지 원본
 * 픽셀 크기를 그대로 써버리는 경우가 있어서, 실제 측정된 너비로 높이를 직접 계산합니다. */
function SocialLoginButtonImage({
  source,
  aspectRatio,
}: {
  source: number;
  aspectRatio: number;
}) {
  const [width, setWidth] = useState(0);

  return (
    <View onLayout={(e) => setWidth(e.nativeEvent.layout.width)}>
      {width > 0 && (
        <Image
          source={source}
          style={{ width, height: width / aspectRatio }}
          resizeMode="contain"
        />
      )}
    </View>
  );
}

type FieldErrors = Partial<Record<LoginErrorField, string>>;

/** 로그인 시에는 이메일 형식만 검사. 비밀번호 규칙 검사를 앞단에서 하면
 * "이메일 또는 비밀번호를 확인해주세요" 라는 통합 실패 메시지의 의미가 깨지므로 하지 않음. */
function validate(email: string): FieldErrors {
  const errors: FieldErrors = {};

  if (!email) {
    errors.email = '이메일을 입력해주세요.';
  } else if (!EMAIL_REGEX.test(email)) {
    errors.email = '올바른 이메일 형식이 아닙니다.';
  }

  return errors;
}

/** 로그인 화면. POST /api/v1/auth/login 호출 후 온보딩 완료 여부로 분기 */
export default function LoginScreen() {
  const params = useLocalSearchParams<{ returnToReportId?: string | string[] }>();
  const signIn = useAuthStore((s) => s.signIn);
  const setSocialSignupData = useSocialSignupStore((s) => s.set);
  const capturePendingPath = usePendingAppLinkStore((s) => s.capturePath);
  // 안드로이드 시스템 내비게이션 바(제스처 바 vs 3버튼 바) 높이에 맞춰
  // 하단 버튼이 가리지 않도록 실시간으로 반영 (탭 바와 동일한 패턴)
  const insets = useSafeAreaInsets();
  // 이메일 → 비밀번호 순으로 키보드의 "다음"을 눌러 포커스를 이동시키기 위한 ref
  const passwordInputRef = useRef<TextInput>(null);

  const [email, setEmail] = useState('');
  const [password, setPassword] = useState('');
  const [fieldErrors, setFieldErrors] = useState<FieldErrors>({});
  const [formError, setFormError] = useState<string | undefined>(undefined);
  // AUTH_PASSWORD_LOGIN_NOT_AVAILABLE(비밀번호가 없는 소셜 가입 계정)일 때만 true —
  // 간편 로그인으로 유도하는 안내 문구를 추가로 보여줌.
  const [showSocialLoginHint, setShowSocialLoginHint] = useState(false);
  const [isSubmitting, setIsSubmitting] = useState(false);
  // 두 버튼 중 어느 쪽이 진행 중인지 구분해서 로딩 표시를 해당 버튼에만 보여줍니다.
  const [socialLoadingProvider, setSocialLoadingProvider] = useState<SocialProvider | null>(null);

  useEffect(() => {
    const rawReportId = params.returnToReportId;
    const reportId = Array.isArray(rawReportId) ? rawReportId[0] : rawReportId;
    if (reportId) capturePendingPath(`/open/report/${reportId}`);
  }, [capturePendingPath, params.returnToReportId]);

  const handleSocialLogin = async (provider: SocialProvider) => {
    if (isSubmitting || socialLoadingProvider) return;

    setFormError(undefined);
    setShowSocialLoginHint(false);
    setSocialLoadingProvider(provider);
    try {
      const { authorizationCode, redirectUri, state } = await authorizeWithProvider(provider);
      const data = await socialLogin({
        provider,
        authorizationCode,
        redirectUri: provider === 'KAKAO' ? redirectUri : undefined,
        state: provider === 'NAVER' ? state : undefined,
      });

      if (!data.signupRequired) {
        // 일반 로그인 성공 처리와 동일: 토큰 저장 후 온보딩 완료 여부로 분기
        await signIn(data.accessToken, data.refreshToken, data.onboardingCompleted);
        router.replace(data.onboardingCompleted ? takePostAuthRoute() : '/(auth)/onboarding');
        return;
      }

      setSocialSignupData({
        socialSignupToken: data.socialSignupToken,
        provider: data.provider,
        email: data.email,
        emailRequired: data.emailRequired,
      });
      router.push('/(auth)/social-signup');
    } catch (err) {
      if (err instanceof OAuthCancelledError) {
        return; // 사용자가 직접 취소한 경우는 에러로 취급하지 않습니다.
      }
      if (err instanceof OAuthProviderNotConfiguredError || err instanceof SocialLoginError) {
        setFormError(err.message);
      } else {
        setFormError('간편 로그인에 실패했습니다. 잠시 후 다시 시도해주세요.');
      }
    } finally {
      setSocialLoadingProvider(null);
    }
  };

  const handleSubmit = async () => {
    if (isSubmitting || socialLoadingProvider) return;

    const validationErrors = validate(email);
    setFieldErrors(validationErrors);
    setFormError(undefined);
    setShowSocialLoginHint(false);
    if (Object.keys(validationErrors).length > 0) return;

    setIsSubmitting(true);
    try {
      const data = await login({ email, password });
      await signIn(data.accessToken, data.refreshToken, data.onboardingCompleted);
      router.replace(data.onboardingCompleted ? takePostAuthRoute() : '/(auth)/onboarding');
    } catch (err) {
      if (err instanceof LoginError && err.field) {
        setFieldErrors((prev) => ({ ...prev, [err.field as LoginErrorField]: err.message }));
      } else if (err instanceof LoginError) {
        setFormError(err.message);
        setShowSocialLoginHint(err.code === 'AUTH_PASSWORD_LOGIN_NOT_AVAILABLE');
      } else {
        setFormError('로그인에 실패했습니다. 잠시 후 다시 시도해주세요.');
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
          <View style={styles.headerRow}>
            <View>
              <Text style={styles.title}>로그인</Text>
              <Text style={styles.subtitle}>싸방팔방에서 임장을 이어가보세요.</Text>
            </View>
          </View>

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
            />
            <FormField
              ref={passwordInputRef}
              label="비밀번호"
              value={password}
              onChangeText={setPassword}
              error={fieldErrors.password}
              secureTextEntry
              placeholder="비밀번호"
              autoComplete="password"
              textContentType="password"
              returnKeyType="done"
              onSubmitEditing={handleSubmit}
            />

            <Link href="/(auth)/signup" style={styles.signupLink}>
              <Text style={styles.signupLinkText}>
                계정이 없으신가요? <Text style={styles.signupLinkAccent}>회원가입</Text>
              </Text>
            </Link>

            <Link href="/(auth)/password-reset" style={styles.signupLink}>
              <Text style={styles.signupLinkText}>
                비밀번호를 잊으셨나요? <Text style={styles.signupLinkAccent}>비밀번호 재설정</Text>
              </Text>
            </Link>

            <View style={styles.dividerRow}>
              <View style={styles.dividerLine} />
              <Text style={styles.dividerText}>간편 로그인</Text>
              <View style={styles.dividerLine} />
            </View>

            <View style={styles.socialButtons}>
              <Pressable
                onPress={() => handleSocialLogin('KAKAO')}
                disabled={!!socialLoadingProvider || isSubmitting}
                style={socialLoadingProvider === 'KAKAO' && styles.socialButtonLoading}
              >
                <SocialLoginButtonImage
                  source={require('../../assets/images/login/kakao_login_large_wide.png')}
                  aspectRatio={KAKAO_BUTTON_ASPECT_RATIO}
                />
              </Pressable>
              <Pressable
                onPress={() => handleSocialLogin('NAVER')}
                disabled={!!socialLoadingProvider || isSubmitting}
                style={socialLoadingProvider === 'NAVER' && styles.socialButtonLoading}
              >
                <SocialLoginButtonImage
                  source={require('../../assets/images/login/NAVER_login_Light_KR_green_wide_H56.png')}
                  aspectRatio={NAVER_BUTTON_ASPECT_RATIO}
                />
              </Pressable>
            </View>

            {!!formError && (
              <View style={styles.formErrorBox}>
                <Text style={styles.formErrorText}>{formError}</Text>
                {showSocialLoginHint && (
                  <Text style={styles.formErrorHint}>
                    네이버 또는 카카오 계정으로 가입한 회원일 수 있어요. 간편 로그인을 이용해 주세요.
                  </Text>
                )}
              </View>
            )}
          </View>
        </ScrollView>

        <View style={[styles.bottomBar, { paddingBottom: Math.max(24, insets.bottom + 8) }]}>
          <Pressable
            onPress={handleSubmit}
            disabled={isSubmitting || !!socialLoadingProvider}
            style={[styles.submitButton, isSubmitting && styles.submitButtonDisabled]}
          >
            {isSubmitting ? (
              <ActivityIndicator color="#FFFFFF" />
            ) : (
              <Text style={styles.submitButtonText}>로그인</Text>
            )}
          </Pressable>
        </View>
      </KeyboardAvoidingView>
    </LinearGradient>
  );
}

const styles = StyleSheet.create({
  flex: { flex: 1 },
  scroll: {
    flex: 1,
  },
  container: {
    padding: 24,
    paddingTop: 56,
  },
  headerRow: {
    flexDirection: 'row',
    justifyContent: 'space-between',
    alignItems: 'flex-start',
  },
  title: {
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
    gap: 6,
  },
  formErrorText: {
    fontSize: 13,
    color: ERROR_COLOR,
  },
  formErrorHint: {
    fontSize: 12,
    color: ERROR_COLOR,
  },
  // paddingBottom은 시스템 내비게이션 바 높이에 맞춰 화면에서 동적으로 덮어씁니다
  // (아래 24는 그 값이 더 작을 때의 최소 여백).
  bottomBar: {
    paddingHorizontal: 24,
    paddingTop: 12,
    paddingBottom: 24,
    backgroundColor: '#FFFFFF',
    gap: 16,
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
  signupLink: {
    alignSelf: 'center',
  },
  signupLinkText: {
    fontSize: 13,
    color: PLACEHOLDER_COLOR,
  },
  signupLinkAccent: {
    color: PRIMARY_COLOR,
    fontWeight: '700',
  },

  dividerRow: {
    flexDirection: 'row',
    alignItems: 'center',
    gap: 12,
    marginTop: 8,
  },
  dividerLine: {
    flex: 1,
    height: 1,
    backgroundColor: '#E5E7EB',
  },
  dividerText: {
    fontSize: 12,
    color: PLACEHOLDER_COLOR,
  },
  socialButtons: {
    gap: 10,
  },
  socialButtonLoading: {
    opacity: 0.6,
  },
});
