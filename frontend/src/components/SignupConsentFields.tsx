import Ionicons from '@expo/vector-icons/Ionicons';
import * as WebBrowser from 'expo-web-browser';
import { Pressable, StyleSheet, Text, View } from 'react-native';

import { appAlert } from '@/components/AppDialog';
import {
  BORDER_COLOR,
  MUTED_TEXT_COLOR,
  PRIMARY_COLOR,
  SURFACE_COLOR,
  TEXT_COLOR,
} from '@/constants/colors';
import { PRIVACY_POLICY_URL, TERMS_OF_SERVICE_URL } from '@/constants/legal';

export interface SignupConsents {
  terms: boolean;
  privacy: boolean;
}

interface SignupConsentFieldsProps {
  consents: SignupConsents;
  onChange: (next: SignupConsents) => void;
}

interface ConsentCheckboxProps {
  accessibilityLabel: string;
  checked: boolean;
  label: string;
  labelStyle?: object;
  onPress: () => void;
}

function ConsentCheckbox({
  accessibilityLabel,
  checked,
  label,
  labelStyle,
  onPress,
}: ConsentCheckboxProps) {
  return (
    <Pressable
      accessibilityLabel={accessibilityLabel}
      accessibilityRole="checkbox"
      accessibilityState={{ checked }}
      hitSlop={4}
      onPress={onPress}
      style={({ pressed }) => [styles.consentButton, pressed && styles.pressed]}
    >
      <View style={[styles.checkbox, checked && styles.checkboxChecked]}>
        {checked ? <Ionicons color={SURFACE_COLOR} name="checkmark" size={17} /> : null}
      </View>
      <Text style={[styles.label, labelStyle]}>{label}</Text>
    </Pressable>
  );
}

export function SignupConsentFields({ consents, onChange }: SignupConsentFieldsProps) {
  const isAllChecked = consents.terms && consents.privacy;

  const openLegalDocument = async (url: string, errorMessage: string) => {
    try {
      await WebBrowser.openBrowserAsync(url);
    } catch {
      appAlert('안내', errorMessage);
    }
  };

  return (
    <View style={styles.container}>
      <ConsentCheckbox
        accessibilityLabel="약관 전체 동의"
        checked={isAllChecked}
        label="약관 전체에 동의합니다"
        labelStyle={styles.allConsentLabel}
        onPress={() => onChange({ terms: !isAllChecked, privacy: !isAllChecked })}
      />

      <View style={styles.divider} />

      <View style={styles.itemRow}>
        <ConsentCheckbox
          accessibilityLabel="필수 서비스 이용약관 동의"
          checked={consents.terms}
          label="[필수] 서비스 이용약관에 동의합니다"
          onPress={() => onChange({ ...consents, terms: !consents.terms })}
        />
        <Pressable
          accessibilityLabel="이용약관 보기"
          accessibilityRole="link"
          hitSlop={8}
          onPress={() =>
            void openLegalDocument(
              TERMS_OF_SERVICE_URL,
              '이용약관을 열지 못했어요. 잠시 후 다시 시도해 주세요.',
            )
          }
          style={({ pressed }) => [styles.linkButton, pressed && styles.pressed]}
        >
          <Text style={styles.linkText}>보기</Text>
          <Ionicons color={MUTED_TEXT_COLOR} name="open-outline" size={15} />
        </Pressable>
      </View>

      <View style={styles.itemRow}>
        <ConsentCheckbox
          accessibilityLabel="필수 개인정보 수집·이용 동의"
          checked={consents.privacy}
          label="[필수] 개인정보 수집·이용에 동의합니다"
          onPress={() => onChange({ ...consents, privacy: !consents.privacy })}
        />
        <Pressable
          accessibilityLabel="개인정보 처리방침 보기"
          accessibilityRole="link"
          hitSlop={8}
          onPress={() =>
            void openLegalDocument(
              PRIVACY_POLICY_URL,
              '개인정보 처리방침을 열지 못했어요. 잠시 후 다시 시도해 주세요.',
            )
          }
          style={({ pressed }) => [styles.linkButton, pressed && styles.pressed]}
        >
          <Text style={styles.linkText}>보기</Text>
          <Ionicons color={MUTED_TEXT_COLOR} name="open-outline" size={15} />
        </Pressable>
      </View>
    </View>
  );
}

const styles = StyleSheet.create({
  container: {
    marginTop: 20,
    padding: 16,
    borderWidth: 1,
    borderColor: BORDER_COLOR,
    borderRadius: 14,
    backgroundColor: SURFACE_COLOR,
  },
  itemRow: {
    flexDirection: 'row',
    alignItems: 'center',
  },
  consentButton: {
    minHeight: 44,
    flex: 1,
    flexDirection: 'row',
    alignItems: 'center',
    gap: 10,
  },
  checkbox: {
    width: 24,
    height: 24,
    alignItems: 'center',
    justifyContent: 'center',
    borderWidth: 1,
    borderColor: BORDER_COLOR,
    borderRadius: 6,
    backgroundColor: SURFACE_COLOR,
  },
  checkboxChecked: {
    borderColor: PRIMARY_COLOR,
    backgroundColor: PRIMARY_COLOR,
  },
  label: {
    flex: 1,
    fontSize: 14,
    color: TEXT_COLOR,
    lineHeight: 20,
  },
  allConsentLabel: {
    fontWeight: '700',
  },
  divider: {
    height: 1,
    marginVertical: 8,
    backgroundColor: BORDER_COLOR,
  },
  linkButton: {
    minHeight: 44,
    flexDirection: 'row',
    alignItems: 'center',
    gap: 5,
    paddingLeft: 8,
  },
  linkText: {
    fontSize: 13,
    fontWeight: '600',
    color: PRIMARY_COLOR,
    textDecorationLine: 'underline',
  },
  pressed: {
    opacity: 0.7,
  },
});
