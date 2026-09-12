import { Modal, Pressable, ScrollView, StyleSheet, Text, View } from 'react-native';
import { useSafeAreaInsets } from 'react-native-safe-area-context';

import {
  BORDER_COLOR,
  DARK_GREEN_COLOR,
  MUTED_TEXT_COLOR,
  PRIMARY_COLOR,
  SURFACE_COLOR,
  TEXT_COLOR,
} from '@/constants/colors';

interface PermissionNoticeModalProps {
  visible: boolean;
  onConfirm: () => void;
}

const PERMISSIONS = [
  ['위치', '주변 아파트를 지도에 표시하고, 임장 시작 시 대상 아파트 근처에 있는지 확인'],
  ['카메라', '임장 현장 사진 촬영'],
  ['사진·미디어', '기기에 저장된 사진을 임장 기록·게시글에 첨부'],
  ['마이크', '임장 현장 음성 메모 녹음'],
  ['알림', '스터디 신청 결과, 새 채팅 메시지, 임장 일정 안내'],
] as const;

export function PermissionNoticeModal({ visible, onConfirm }: PermissionNoticeModalProps) {
  const insets = useSafeAreaInsets();

  return (
    <Modal animationType="fade" onRequestClose={() => undefined} visible={visible}>
      <View
        style={[
          styles.container,
          { paddingTop: Math.max(insets.top, 24), paddingBottom: Math.max(insets.bottom, 24) },
        ]}
      >
        <ScrollView contentContainerStyle={styles.content} showsVerticalScrollIndicator={false}>
          <Text style={styles.title}>앱 접근 권한 안내</Text>
          <Text style={styles.lead}>싸방팔방은 필수 접근 권한을 요구하지 않습니다.</Text>
          <Text style={styles.description}>
            아래 권한은 모두 선택이며, 동의하지 않아도 앱을 이용할 수 있습니다.
          </Text>

          <View style={styles.permissionList}>
            {PERMISSIONS.map(([label, reason]) => (
              <View key={label} style={styles.permissionRow}>
                <Text style={styles.permissionLabel}>{label}</Text>
                <Text style={styles.permissionReason}>{reason}</Text>
              </View>
            ))}
          </View>

          <Text style={styles.notice}>
            권한은 해당 기능을 처음 사용하는 시점에 요청하며,{`\n`}
            거부하더라도 해당 기능을 제외한 다른 기능은 정상 동작합니다.
          </Text>
          <Text style={styles.notice}>
            기기 설정 → 애플리케이션 → 싸방팔방 → 권한 에서{`\n`}
            언제든지 변경할 수 있습니다.
          </Text>
        </ScrollView>

        <Pressable
          accessibilityRole="button"
          onPress={onConfirm}
          style={({ pressed }) => [styles.confirmButton, pressed && styles.confirmButtonPressed]}
        >
          <Text style={styles.confirmButtonText}>확인</Text>
        </Pressable>
      </View>
    </Modal>
  );
}

const styles = StyleSheet.create({
  container: {
    flex: 1,
    paddingHorizontal: 24,
    backgroundColor: SURFACE_COLOR,
  },
  content: {
    flexGrow: 1,
    justifyContent: 'center',
    paddingVertical: 24,
  },
  title: {
    fontSize: 26,
    fontWeight: '800',
    color: DARK_GREEN_COLOR,
    marginBottom: 24,
  },
  lead: {
    fontSize: 16,
    fontWeight: '700',
    color: TEXT_COLOR,
    lineHeight: 24,
  },
  description: {
    marginTop: 6,
    fontSize: 14,
    color: MUTED_TEXT_COLOR,
    lineHeight: 21,
  },
  permissionList: {
    marginVertical: 24,
    borderWidth: 1,
    borderColor: BORDER_COLOR,
    borderRadius: 16,
    overflow: 'hidden',
  },
  permissionRow: {
    flexDirection: 'row',
    gap: 14,
    paddingHorizontal: 16,
    paddingVertical: 13,
    borderBottomWidth: StyleSheet.hairlineWidth,
    borderBottomColor: BORDER_COLOR,
  },
  permissionLabel: {
    width: 78,
    fontSize: 14,
    fontWeight: '700',
    color: DARK_GREEN_COLOR,
  },
  permissionReason: {
    flex: 1,
    fontSize: 14,
    color: TEXT_COLOR,
    lineHeight: 20,
  },
  notice: {
    marginBottom: 14,
    fontSize: 13,
    color: MUTED_TEXT_COLOR,
    lineHeight: 20,
  },
  confirmButton: {
    minHeight: 52,
    alignItems: 'center',
    justifyContent: 'center',
    borderRadius: 14,
    backgroundColor: PRIMARY_COLOR,
  },
  confirmButtonPressed: {
    opacity: 0.8,
  },
  confirmButtonText: {
    fontSize: 16,
    fontWeight: '700',
    color: SURFACE_COLOR,
  },
});
