import { useCallback, useEffect, useState, type ReactNode } from 'react';
import { BackHandler, Modal, Pressable, StyleSheet, Text, View } from 'react-native';

import {
  BORDER_COLOR,
  DARK_GREEN_COLOR,
  ERROR_COLOR,
  MUTED_TEXT_COLOR,
  PRIMARY_COLOR,
  SOFT_GREEN_COLOR,
  SURFACE_COLOR,
} from '@/constants/colors';

/**
 * 앱 톤앤매너 커스텀 다이얼로그.
 *
 * 기본 RN `Alert.alert` 은 OS 기본 팝업이라 앱 UI와 겉돈다. 이 모듈은 같은 시그니처의
 * `appAlert(title, message?, buttons?, options?)` 를 제공해, 호출부는 거의 그대로 두고
 * 화면만 앱 카드형으로 바꾼다. 컴포넌트가 아닌 `.ts`(permissions 등)에서도 부르므로
 * 모듈 레벨 핸들러 + 루트에 마운트한 Provider 조합으로 명령형 API를 만든다.
 */

export type AppAlertButtonStyle = 'default' | 'cancel' | 'destructive';

export interface AppAlertButton {
  text: string;
  onPress?: () => void;
  style?: AppAlertButtonStyle;
}

export interface AppAlertOptions {
  /** 배경 탭·뒤로가기로 닫을 수 있는지. 기본 true. cancel 버튼이 있으면 그 onPress 를 실행한다. */
  cancelable?: boolean;
}

interface DialogConfig {
  title: string;
  message?: string;
  buttons: AppAlertButton[];
  options?: AppAlertOptions;
}

type ShowHandler = (config: DialogConfig) => void;

let showHandler: ShowHandler | null = null;

/** `Alert.alert` 드롭인 대체. Provider 가 마운트돼 있어야 실제로 뜬다. */
export function appAlert(
  title: string,
  message?: string,
  buttons?: AppAlertButton[],
  options?: AppAlertOptions,
): void {
  const resolvedButtons =
    buttons && buttons.length > 0 ? buttons : [{ text: '확인', style: 'default' as const }];
  if (showHandler) {
    showHandler({ title, message, buttons: resolvedButtons, options });
  }
}

export function AppDialogProvider({ children }: { children: ReactNode }) {
  const [dialog, setDialog] = useState<DialogConfig | null>(null);

  useEffect(() => {
    showHandler = (config) => setDialog(config);
    return () => {
      showHandler = null;
    };
  }, []);

  const runButton = useCallback((button: AppAlertButton) => {
    setDialog(null);
    button.onPress?.();
  }, []);

  const dismiss = useCallback((current: DialogConfig) => {
    // cancelable(기본 true)일 때만 배경 탭/뒤로가기로 닫는다. cancel 버튼이 있으면 그 동작을 실행.
    if (current.options?.cancelable === false) {
      return;
    }
    const cancelButton = current.buttons.find((button) => button.style === 'cancel');
    setDialog(null);
    cancelButton?.onPress?.();
  }, []);

  // Android 하드웨어 뒤로가기 → dismiss.
  useEffect(() => {
    if (dialog === null) {
      return;
    }
    const subscription = BackHandler.addEventListener('hardwareBackPress', () => {
      dismiss(dialog);
      return true;
    });
    return () => subscription.remove();
  }, [dialog, dismiss]);

  // 2개면 가로, 그 외(1개·3개↑)는 세로 배치.
  const isHorizontal = dialog !== null && dialog.buttons.length === 2;

  return (
    <>
      {children}
      <Modal
        transparent
        visible={dialog !== null}
        animationType="fade"
        statusBarTranslucent
        onRequestClose={() => dialog && dismiss(dialog)}
      >
        {dialog && (
          <Pressable style={styles.backdrop} onPress={() => dismiss(dialog)}>
            {/* 카드 내부 탭이 배경으로 전파돼 닫히지 않도록 별도 Pressable 로 흡수. */}
            <Pressable style={styles.card} onPress={() => undefined}>
              <Text style={styles.title}>{dialog.title}</Text>
              {dialog.message ? <Text style={styles.message}>{dialog.message}</Text> : null}

              <View style={[styles.buttonRow, isHorizontal ? styles.buttonRowHorizontal : styles.buttonRowVertical]}>
                {dialog.buttons.map((button, index) => {
                  const isCancel = button.style === 'cancel';
                  const isDestructive = button.style === 'destructive';
                  return (
                    <Pressable
                      key={`${button.text}:${index}`}
                      accessibilityRole="button"
                      onPress={() => runButton(button)}
                      style={({ pressed }) => [
                        styles.button,
                        isHorizontal && styles.buttonFlex,
                        isCancel ? styles.buttonCancel : styles.buttonPrimary,
                        isDestructive && styles.buttonDestructive,
                        pressed && styles.buttonPressed,
                      ]}
                    >
                      <Text
                        style={[
                          styles.buttonText,
                          isCancel
                            ? styles.buttonTextCancel
                            : isDestructive
                              ? styles.buttonTextDestructive
                              : styles.buttonTextPrimary,
                        ]}
                      >
                        {button.text}
                      </Text>
                    </Pressable>
                  );
                })}
              </View>
            </Pressable>
          </Pressable>
        )}
      </Modal>
    </>
  );
}

const styles = StyleSheet.create({
  backdrop: {
    flex: 1,
    paddingHorizontal: 36,
    alignItems: 'center',
    justifyContent: 'center',
    backgroundColor: 'rgba(16, 39, 30, 0.45)',
  },
  card: {
    width: '100%',
    maxWidth: 360,
    paddingHorizontal: 24,
    paddingTop: 26,
    paddingBottom: 18,
    borderRadius: 24,
    backgroundColor: SURFACE_COLOR,
    borderWidth: 1,
    borderColor: BORDER_COLOR,
  },
  title: {
    fontSize: 19,
    fontWeight: '800',
    color: DARK_GREEN_COLOR,
    letterSpacing: -0.4,
  },
  message: {
    marginTop: 10,
    fontSize: 14,
    lineHeight: 21,
    color: MUTED_TEXT_COLOR,
  },
  buttonRow: {
    marginTop: 22,
    gap: 8,
  },
  buttonRowHorizontal: {
    flexDirection: 'row',
  },
  buttonRowVertical: {
    flexDirection: 'column',
  },
  button: {
    minHeight: 50,
    paddingHorizontal: 18,
    borderRadius: 16,
    alignItems: 'center',
    justifyContent: 'center',
  },
  buttonFlex: {
    flex: 1,
  },
  buttonPrimary: {
    backgroundColor: PRIMARY_COLOR,
  },
  buttonDestructive: {
    backgroundColor: ERROR_COLOR,
  },
  buttonCancel: {
    backgroundColor: SOFT_GREEN_COLOR,
  },
  buttonPressed: {
    opacity: 0.85,
  },
  buttonText: {
    fontSize: 15,
    fontWeight: '700',
    letterSpacing: -0.2,
  },
  buttonTextPrimary: {
    color: SURFACE_COLOR,
  },
  buttonTextDestructive: {
    color: SURFACE_COLOR,
  },
  buttonTextCancel: {
    color: DARK_GREEN_COLOR,
  },
});
