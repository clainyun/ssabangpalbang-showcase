import { forwardRef, type ReactNode } from 'react';
import { StyleSheet, Text, TextInput, View, type TextInputProps } from 'react-native';

import { ERROR_COLOR, LABEL_COLOR, PLACEHOLDER_COLOR, PRIMARY_COLOR, TEXT_COLOR } from '@/constants/colors';

/** 입력창 아래 보조 안내 텍스트의 톤(색상). error가 있으면 hint는 표시되지 않습니다. */
export type FormFieldHintTone = 'success' | 'error' | 'muted';

export interface FormFieldProps {
  label: string;
  value: string;
  onChangeText: (text: string) => void;
  error?: string;
  secureTextEntry?: boolean;
  keyboardType?: 'default' | 'email-address';
  placeholder?: string;
  autoComplete?: TextInputProps['autoComplete'];
  textContentType?: TextInputProps['textContentType'];
  returnKeyType?: TextInputProps['returnKeyType'];
  blurOnSubmit?: boolean;
  onSubmitEditing?: () => void;
  /** 에러가 없을 때 입력창 아래 보여줄 실시간 안내(예: "사용 가능한 이메일이에요"). */
  hint?: string;
  hintTone?: FormFieldHintTone;
  /** 입력창 우측에 붙는 부가 요소(예: 중복확인 로딩 스피너). */
  trailing?: ReactNode;
}

const HINT_COLORS: Record<FormFieldHintTone, string> = {
  success: PRIMARY_COLOR,
  error: ERROR_COLOR,
  muted: PLACEHOLDER_COLOR,
};

// ref는 "다음" 키보드 버튼을 눌렀을 때 다음 입력창으로 포커스를 옮기기 위해 필요
// (returnKeyType/onSubmitEditing과 함께 화면 컴포넌트에서 사용)
export const FormField = forwardRef<TextInput, FormFieldProps>(function FormField(
  {
    label,
    value,
    onChangeText,
    error,
    secureTextEntry,
    keyboardType,
    placeholder,
    autoComplete,
    textContentType,
    returnKeyType,
    blurOnSubmit,
    onSubmitEditing,
    hint,
    hintTone = 'muted',
    trailing,
  },
  ref,
) {
  return (
    <View style={styles.field}>
      <Text style={styles.label}>{label}</Text>
      <View style={[styles.inputBox, !!error && styles.inputBoxError]}>
        <TextInput
          ref={ref}
          value={value}
          onChangeText={onChangeText}
          secureTextEntry={secureTextEntry}
          keyboardType={keyboardType}
          autoCapitalize="none"
          autoCorrect={false}
          placeholder={placeholder}
          placeholderTextColor={PLACEHOLDER_COLOR}
          autoComplete={autoComplete}
          textContentType={textContentType}
          returnKeyType={returnKeyType}
          blurOnSubmit={blurOnSubmit}
          onSubmitEditing={onSubmitEditing}
          style={styles.input}
        />
        {!!trailing && <View style={styles.trailing}>{trailing}</View>}
      </View>
      {/* 에러가 우선. 에러가 없을 때만 실시간 hint(중복확인/비밀번호 일치 등)를 표시합니다. */}
      {error ? (
        <Text style={styles.fieldError}>{error}</Text>
      ) : hint ? (
        <Text style={[styles.hint, { color: HINT_COLORS[hintTone] }]}>{hint}</Text>
      ) : null}
    </View>
  );
});

const styles = StyleSheet.create({
  field: {
    gap: 8,
  },
  label: {
    fontSize: 13,
    fontWeight: '700',
    color: LABEL_COLOR,
  },
  inputBox: {
    flexDirection: 'row',
    alignItems: 'center',
    gap: 8,
    borderWidth: 1.5,
    borderColor: 'transparent',
    borderRadius: 14,
    paddingHorizontal: 16,
    paddingVertical: 16,
    backgroundColor: '#FFFFFF',
  },
  inputBoxError: {
    borderColor: ERROR_COLOR,
  },
  input: {
    flex: 1,
    padding: 0,
    fontSize: 15,
    color: TEXT_COLOR,
    textAlignVertical: 'center',
    includeFontPadding: false,
  },
  trailing: {
    alignItems: 'center',
    justifyContent: 'center',
  },
  fieldError: {
    fontSize: 12,
    lineHeight: 16,
    color: ERROR_COLOR,
  },
  hint: {
    fontSize: 12,
    lineHeight: 16,
  },
});
