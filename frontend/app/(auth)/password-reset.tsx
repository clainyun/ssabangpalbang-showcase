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

import {
  confirmPasswordReset,
  PasswordResetError,
  requestPasswordResetCode,
  verifyPasswordResetCode,
  type PasswordResetErrorField,
} from '@/features/auth/api/passwordReset';
import { appAlert } from '@/components/AppDialog';
import { FormField } from '@/components/FormField';
import { PasswordRequirements } from '@/features/auth/PasswordRequirements';
import { PASSWORD_REGEX, passwordsMatch, validatePassword } from '@/features/auth/passwordValidation';
import {
  BUTTON_BACKGROUND_COLOR,
  ERROR_BACKGROUND_COLOR,
  ERROR_COLOR,
  PLACEHOLDER_COLOR,
  PRIMARY_COLOR,
  SOFT_GREEN_COLOR,
  TEXT_COLOR,
} from '@/constants/colors';

// 배경 그라데이션: 상단 0%는 연한 그린, 90% 지점부터는 완전한 흰색이라
// 실제로 색이 보이는 구간은 화면 위쪽 90%뿐이고 나머지는 흰 배경입니다.
// (로그인/회원가입 화면과 동일한 값 — 톤앤매너 통일)
const GRADIENT_COLORS = ['#E5F7ED', '#FFFFFF', '#FFFFFF'] as const;
const GRADIENT_LOCATIONS = [0, 0.9, 1] as const;

const EMAIL_REGEX = /^[^\s@]+@[^\s@]+\.[^\s@]+$/;
// PASSWORD_REGEX(영문+숫자 포함 8~64자)는 회원가입 화면과 공유하는 유틸에서 가져옵니다.

// 코드 확인 버튼의 진행 상태
type VerifyStatus = 'idle' | 'verifying' | 'success' | 'invalid' | 'error';

type Step = 'email' | 'confirm';
type FieldErrors = Partial<Record<PasswordResetErrorField, string>>;

function validateEmail(email: string): FieldErrors {
  const errors: FieldErrors = {};
  if (!email) {
    errors.email = '이메일을 입력해주세요.';
  } else if (!EMAIL_REGEX.test(email)) {
    errors.email = '올바른 이메일 형식이 아닙니다.';
  }
  return errors;
}

function validateConfirm(code: string, newPassword: string, newPasswordConfirm: string): FieldErrors {
  const errors: FieldErrors = {};

  if (!code) {
    errors.code = '코드를 입력해주세요.';
  }

  if (!newPassword) {
    errors.newPassword = '새 비밀번호를 입력해주세요.';
  } else if (newPassword.length < 8 || newPassword.length > 64) {
    errors.newPassword = '비밀번호는 8~64자여야 합니다.';
  } else if (!PASSWORD_REGEX.test(newPassword)) {
    errors.newPassword = '비밀번호는 영문과 숫자를 포함해야 합니다.';
  } else if (newPasswordConfirm && newPassword !== newPasswordConfirm) {
    errors.newPassword = '새 비밀번호가 일치하지 않습니다.';
  }

  return errors;
}

function BackButton({ onPress }: { onPress: () => void }) {
  return (
    <Pressable onPress={onPress} hitSlop={12} style={styles.backButton}>
      <Svg width={24} height={24} viewBox="0 0 24 24" fill="none" stroke={TEXT_COLOR} strokeWidth={2}>
        <Path d="M15 5l-7 7 7 7" strokeLinecap="round" strokeLinejoin="round" />
      </Svg>
    </Pressable>
  );
}

/**
 * 비밀번호 재설정 화면. 한 화면에서 2단계로 진행합니다.
 * step 'email'   → POST /api/v1/auth/password-reset/request (항상 200, 계정 열거 방지)
 * step 'confirm' → POST /api/v1/auth/password-reset/confirm
 */
export default function PasswordResetScreen() {
  const insets = useSafeAreaInsets();
  // step confirm 에서 코드 → 새 비밀번호 → 새 비밀번호 확인 순으로 포커스를 옮기기 위한 ref
  const newPasswordInputRef = useRef<TextInput>(null);
  const newPasswordConfirmInputRef = useRef<TextInput>(null);

  const [step, setStep] = useState<Step>('email');

  const [email, setEmail] = useState('');
  const [code, setCode] = useState('');
  const [newPassword, setNewPassword] = useState('');
  const [newPasswordConfirm, setNewPasswordConfirm] = useState('');

  const [fieldErrors, setFieldErrors] = useState<FieldErrors>({});
  const [formError, setFormError] = useState<string | undefined>(undefined);
  const [isSubmitting, setIsSubmitting] = useState(false);

  // 코드 확인(verify) 상태. codeVerified 가 true 가 되어야 새 비밀번호 단계로 진행합니다.
  const [verifyStatus, setVerifyStatus] = useState<VerifyStatus>('idle');
  const codeVerified = verifyStatus === 'success';

  // 새 비밀번호 실시간 검증 파생값
  const passwordChecks = validatePassword(newPassword);
  const confirmMatches = passwordsMatch(newPassword, newPasswordConfirm);

  // 코드가 바뀌면 이전 인증 결과를 무효화합니다(재입력 시 다시 확인해야 함).
  const handleCodeChange = (text: string) => {
    setCode(text);
    setVerifyStatus('idle');
    setFieldErrors((prev) => ({ ...prev, code: undefined }));
  };

  // 코드 입력창 아래 실시간 안내(에러가 있으면 FormField가 에러를 우선 표시).
  const codeHint = (() => {
    if (verifyStatus === 'verifying') return { text: '코드 확인 중…', tone: 'muted' as const };
    if (verifyStatus === 'success') return { text: '인증되었습니다.', tone: 'success' as const };
    if (verifyStatus === 'invalid') return { text: '인증코드가 올바르지 않습니다.', tone: 'error' as const };
    if (verifyStatus === 'error')
      return { text: '코드 확인에 실패했어요. 잠시 후 다시 시도해 주세요.', tone: 'muted' as const };
    return undefined;
  })();

  const confirmHint = newPasswordConfirm
    ? confirmMatches
      ? { text: '비밀번호가 일치해요.', tone: 'success' as const }
      : { text: '비밀번호가 일치하지 않습니다.', tone: 'error' as const }
    : undefined;

  const handleBack = () => {
    if (step === 'confirm') {
      // 코드 입력 단계에서 뒤로가기는 이메일 단계로 되돌립니다(화면을 벗어나지 않음).
      setStep('email');
      setFieldErrors({});
      setFormError(undefined);
      setVerifyStatus('idle');
      return;
    }
    router.back();
  };

  const handleRequestCode = async () => {
    if (isSubmitting) return;

    const validationErrors = validateEmail(email);
    setFieldErrors(validationErrors);
    setFormError(undefined);
    if (Object.keys(validationErrors).length > 0) return;

    setIsSubmitting(true);
    try {
      await requestPasswordResetCode(email);
      // 성공(항상 200)하면 코드 입력 단계로 전환합니다. 새 코드이므로 인증 상태를 초기화합니다.
      setStep('confirm');
      setCode('');
      setVerifyStatus('idle');
      setFieldErrors({});
      setFormError(undefined);
    } catch (err) {
      if (err instanceof PasswordResetError && err.field) {
        setFieldErrors((prev) => ({ ...prev, [err.field as PasswordResetErrorField]: err.message }));
      } else if (err instanceof PasswordResetError) {
        setFormError(err.message);
      } else {
        setFormError('코드 요청에 실패했습니다. 잠시 후 다시 시도해주세요.');
      }
    } finally {
      setIsSubmitting(false);
    }
  };

  // 새 비밀번호 단계로 넘어가기 전, 코드가 맞는지 사전 확인합니다.
  const handleVerifyCode = async () => {
    if (verifyStatus === 'verifying' || !code) return;

    setVerifyStatus('verifying');
    setFieldErrors((prev) => ({ ...prev, code: undefined }));
    setFormError(undefined);
    try {
      const valid = await verifyPasswordResetCode({ email, code });
      setVerifyStatus(valid ? 'success' : 'invalid');
    } catch (err) {
      if (err instanceof PasswordResetError && err.code === 'AUTH_PASSWORD_RESET_CODE_INVALID') {
        // 시도 5회 초과 — 서버 메시지를 코드 필드 에러로 표시하고 재요청을 유도합니다.
        setVerifyStatus('idle');
        setFieldErrors((prev) => ({ ...prev, code: err.message }));
      } else if (err instanceof PasswordResetError && err.field === 'code') {
        setVerifyStatus('invalid');
      } else {
        setVerifyStatus('error');
      }
    }
  };

  const handleConfirm = async () => {
    // 코드 인증이 끝나야만 최종 확정을 진행합니다.
    if (isSubmitting || !codeVerified) return;

    const validationErrors = validateConfirm(code, newPassword, newPasswordConfirm);
    setFieldErrors(validationErrors);
    setFormError(undefined);
    if (Object.keys(validationErrors).length > 0) return;

    setIsSubmitting(true);
    try {
      await confirmPasswordReset({ email, code, newPassword });
      appAlert('비밀번호를 변경했어요', '새 비밀번호로 로그인해 주세요.', [
        { text: '확인', onPress: () => router.replace('/(auth)/login') },
      ]);
    } catch (err) {
      if (err instanceof PasswordResetError && err.field) {
        setFieldErrors((prev) => ({ ...prev, [err.field as PasswordResetErrorField]: err.message }));
      } else if (err instanceof PasswordResetError) {
        setFormError(err.message);
      } else {
        setFormError('비밀번호 변경에 실패했습니다. 잠시 후 다시 시도해주세요.');
      }
    } finally {
      setIsSubmitting(false);
    }
  };

  const isEmailStep = step === 'email';

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
          <BackButton onPress={handleBack} />

          <Text style={styles.title}>비밀번호 재설정</Text>
          <Text style={styles.subtitle}>
            {isEmailStep
              ? '가입한 이메일로 재설정 코드를 보내드려요.'
              : '이메일로 받은 코드와 새 비밀번호를 입력해 주세요.'}
          </Text>

          <View style={styles.form}>
            {isEmailStep ? (
              <FormField
                label="이메일"
                value={email}
                onChangeText={setEmail}
                error={fieldErrors.email}
                keyboardType="email-address"
                placeholder="example@email.com"
                autoComplete="email"
                textContentType="username"
                returnKeyType="send"
                onSubmitEditing={handleRequestCode}
              />
            ) : (
              <>
                {/* 어떤 이메일로 코드를 보냈는지 안내 (계정 존재 여부는 노출하지 않음) */}
                <View style={styles.noticeBox}>
                  <Text style={styles.noticeText}>
                    입력하신 이메일로 코드를 보냈어요.{'\n'}
                    <Text style={styles.noticeEmail}>{email}</Text>
                  </Text>
                </View>

                <FormField
                  label="재설정 코드"
                  value={code}
                  onChangeText={handleCodeChange}
                  error={fieldErrors.code}
                  placeholder="이메일로 받은 코드"
                  keyboardType="default"
                  autoComplete="one-time-code"
                  textContentType="oneTimeCode"
                  returnKeyType="done"
                  onSubmitEditing={handleVerifyCode}
                  hint={codeHint?.text}
                  hintTone={codeHint?.tone}
                  trailing={
                    verifyStatus === 'verifying' ? (
                      <ActivityIndicator size="small" color={PLACEHOLDER_COLOR} />
                    ) : undefined
                  }
                />
                {/* 코드 확인 버튼: 인증되면 새 비밀번호 단계가 열립니다. */}
                <Pressable
                  onPress={handleVerifyCode}
                  disabled={!code || verifyStatus === 'verifying' || codeVerified}
                  style={[
                    styles.verifyButton,
                    (!code || verifyStatus === 'verifying' || codeVerified) &&
                      styles.verifyButtonDisabled,
                  ]}
                >
                  <Text style={styles.verifyButtonText}>
                    {codeVerified ? '인증 완료' : '코드 확인'}
                  </Text>
                </Pressable>

                {/* 코드가 인증된 뒤에만 새 비밀번호 입력을 노출합니다.
                    새로 만드는 비밀번호이므로 new-password/newPassword를 사용합니다. */}
                {codeVerified && (
                  <>
                    <FormField
                      ref={newPasswordInputRef}
                      label="새 비밀번호"
                      value={newPassword}
                      onChangeText={setNewPassword}
                      error={fieldErrors.newPassword}
                      secureTextEntry
                      placeholder="영문, 숫자 포함 8~64자"
                      autoComplete="new-password"
                      textContentType="newPassword"
                      returnKeyType="next"
                      blurOnSubmit={false}
                      onSubmitEditing={() => newPasswordConfirmInputRef.current?.focus()}
                    />
                    {newPassword.length > 0 && <PasswordRequirements password={newPassword} />}
                    <FormField
                      ref={newPasswordConfirmInputRef}
                      label="새 비밀번호 확인"
                      value={newPasswordConfirm}
                      onChangeText={setNewPasswordConfirm}
                      secureTextEntry
                      placeholder="새 비밀번호 다시 입력"
                      autoComplete="new-password"
                      textContentType="newPassword"
                      returnKeyType="done"
                      onSubmitEditing={handleConfirm}
                      hint={confirmHint?.text}
                      hintTone={confirmHint?.tone}
                    />
                  </>
                )}
              </>
            )}

            {!!formError && (
              <View style={styles.formErrorBox}>
                <Text style={styles.formErrorText}>{formError}</Text>
              </View>
            )}
          </View>
        </ScrollView>

        <View style={[styles.bottomBar, { paddingBottom: Math.max(24, insets.bottom + 8) }]}>
          <Pressable
            onPress={isEmailStep ? handleRequestCode : handleConfirm}
            disabled={
              isSubmitting ||
              // 확인 단계에서는 코드 인증 + 비밀번호 규칙 충족 + 확인 일치가 모두 필요합니다.
              (!isEmailStep && (!codeVerified || !passwordChecks.isValid || !confirmMatches))
            }
            style={[
              styles.submitButton,
              (isSubmitting ||
                (!isEmailStep && (!codeVerified || !passwordChecks.isValid || !confirmMatches))) &&
                styles.submitButtonDisabled,
            ]}
          >
            {isSubmitting ? (
              <ActivityIndicator color="#FFFFFF" />
            ) : (
              <Text style={styles.submitButtonText}>
                {isEmailStep ? '재설정 코드 받기' : '비밀번호 변경'}
              </Text>
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
  noticeBox: {
    backgroundColor: SOFT_GREEN_COLOR,
    borderRadius: 12,
    padding: 14,
  },
  noticeText: {
    fontSize: 13,
    lineHeight: 20,
    color: TEXT_COLOR,
  },
  noticeEmail: {
    fontWeight: '700',
    color: PRIMARY_COLOR,
  },
  // 코드 확인 버튼(보조 버튼): 하단 고정 제출 버튼과 구분되도록 외곽선 스타일로 둡니다.
  verifyButton: {
    height: 46,
    borderRadius: 14,
    borderWidth: 1.5,
    borderColor: PRIMARY_COLOR,
    alignItems: 'center',
    justifyContent: 'center',
  },
  verifyButtonDisabled: {
    opacity: 0.5,
  },
  verifyButtonText: {
    fontSize: 15,
    fontWeight: '700',
    color: PRIMARY_COLOR,
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
  // paddingBottom은 시스템 내비게이션 바 높이에 맞춰 화면에서 동적으로 덮어씁니다
  // (아래 24는 그 값이 더 작을 때의 최소 여백).
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
