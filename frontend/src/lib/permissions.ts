import { Linking } from 'react-native';

import { appAlert } from '@/components/AppDialog';

export type PermissionKind = 'location' | 'camera' | 'photo' | 'notification' | 'microphone';

const BEFORE_REQUEST_COPY: Record<PermissionKind, { title: string; message: string }> = {
  location: {
    title: '위치 권한이 필요해요',
    message:
      '주변 아파트를 지도에 표시하고, 임장 시작 시 대상 아파트 근처인지 확인하기 위해 위치 정보를 사용해요. 위치는 확인 목적으로만 사용하고 저장하지 않아요.',
  },
  camera: {
    title: '카메라 권한이 필요해요',
    message: '임장 현장 사진을 촬영해 기록으로 남기기 위해 카메라를 사용해요.',
  },
  photo: {
    title: '사진 권한이 필요해요',
    message: '기기에 저장된 사진을 임장 기록과 게시글에 첨부하기 위해 사진 접근이 필요해요.',
  },
  notification: {
    title: '알림을 받아보시겠어요?',
    message:
      '스터디 신청 결과, 새 채팅 메시지, 임장 일정을 알려드려요. 나중에 설정에서 바꿀 수 있어요.',
  },
  microphone: {
    title: '마이크 권한이 필요해요',
    message:
      '임장 현장에서 음성 메모를 녹음하기 위해 마이크를 사용해요. 녹음한 음성은 텍스트로 변환된 뒤 원본은 24시간 안에 삭제돼요.',
  },
};

const PERMANENTLY_DENIED_COPY: Record<PermissionKind, { title: string; message: string }> = {
  location: {
    title: '위치 권한이 꺼져 있어요',
    message: '기기 설정에서 싸방팔방의 위치 권한을 허용하면 지도와 임장 시작을 사용할 수 있어요.',
  },
  camera: {
    title: '카메라 권한이 꺼져 있어요',
    message: '기기 설정에서 싸방팔방의 카메라 권한을 허용하면 사진을 촬영할 수 있어요.',
  },
  photo: {
    title: '사진 권한이 꺼져 있어요',
    message: '기기 설정에서 싸방팔방의 사진 권한을 허용하면 사진을 첨부할 수 있어요.',
  },
  notification: {
    title: '알림 권한이 꺼져 있어요',
    message: '기기 설정에서 싸방팔방의 알림 권한을 허용하면 소식을 받을 수 있어요.',
  },
  microphone: {
    title: '마이크 권한이 꺼져 있어요',
    message: '기기 설정에서 싸방팔방의 마이크 권한을 허용하면 음성 메모를 녹음할 수 있어요.',
  },
};

export function explainBeforeRequest(kind: PermissionKind): Promise<boolean> {
  const copy = BEFORE_REQUEST_COPY[kind];

  return new Promise((resolve) => {
    appAlert(
      copy.title,
      copy.message,
      [
        { text: '취소', style: 'cancel', onPress: () => resolve(false) },
        { text: '계속', onPress: () => resolve(true) },
      ],
      { cancelable: true },
    );
  });
}

export async function openAppSettings(): Promise<void> {
  try {
    await Linking.openSettings();
  } catch {
    appAlert('기기 설정 열기 실패', '기기 설정에서 싸방팔방 권한을 확인해 주세요.');
  }
}

export function showPermanentlyDeniedAlert(kind: PermissionKind): void {
  const copy = PERMANENTLY_DENIED_COPY[kind];

  appAlert(copy.title, copy.message, [
    { text: '닫기', style: 'cancel' },
    { text: '기기 설정 열기', onPress: () => void openAppSettings() },
  ]);
}
