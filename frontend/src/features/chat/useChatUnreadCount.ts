import { useCallback, useEffect, useRef, useState } from 'react';

import { getChatUnreadCount } from '@/features/chat/api/getChatUnreadCount';
import { waitForPendingChatReads } from '@/features/chat/api/markChatRead';

interface UnreadSnapshot {
  studyId: number;
  unreadCount: number;
}

async function getSynchronizedUnreadCount(studyId: number) {
  await waitForPendingChatReads(studyId);
  return getChatUnreadCount(studyId);
}

/** 스터디 목록·채팅 탭 뱃지 등 이 화면 밖에서도 재사용할 수 있도록 분리한 훅입니다. */
export function useChatUnreadCount(studyId: number, enabled = true) {
  const [snapshot, setSnapshot] = useState<UnreadSnapshot | null>(null);
  const [isLoading, setIsLoading] = useState(enabled);
  const requestSequenceRef = useRef(0);

  const refetch = useCallback(async () => {
    if (!enabled) return;

    const requestSequence = ++requestSequenceRef.current;
    setIsLoading(true);
    try {
      const result = await getSynchronizedUnreadCount(studyId);
      if (requestSequence !== requestSequenceRef.current) return;
      setSnapshot({ studyId, unreadCount: result.unreadCount });
    } catch {
      // 뱃지 조회 실패로 화면 전체를 막을 필요는 없어서 기존 값을 유지합니다.
    } finally {
      if (requestSequence === requestSequenceRef.current) setIsLoading(false);
    }
  }, [enabled, studyId]);

  useEffect(() => {
    if (!enabled) return;

    const requestSequence = ++requestSequenceRef.current;
    getSynchronizedUnreadCount(studyId)
      .then((result) => {
        if (requestSequence === requestSequenceRef.current) {
          setSnapshot({ studyId, unreadCount: result.unreadCount });
        }
      })
      .catch(() => {
        // 화면 진입 조회 실패 시에도 상세 화면 자체는 그대로 사용할 수 있습니다.
      })
      .finally(() => {
        if (requestSequence === requestSequenceRef.current) setIsLoading(false);
      });

    return () => {
      requestSequenceRef.current += 1;
    };
  }, [enabled, studyId]);

  const unreadCount = enabled && snapshot?.studyId === studyId ? snapshot.unreadCount : null;
  const isCurrentStudyLoading = snapshot?.studyId !== studyId || isLoading;
  return { unreadCount, isLoading: enabled && isCurrentStudyLoading, refetch };
}
