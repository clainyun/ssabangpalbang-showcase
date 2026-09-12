import { useCallback, useEffect, useMemo, useRef, useState } from 'react';
import * as Crypto from 'expo-crypto';
import { Client, type IMessage, type StompHeaders } from '@stomp/stompjs';

import { env } from '@/lib/env';
import { decodeMemberIdFromAccessToken } from '@/lib/jwt';
import { useAuthStore } from '@/store/authStore';
import { deleteChatMessage } from '@/features/chat/api/deleteChatMessage';
import { editChatMessage } from '@/features/chat/api/editChatMessage';
import { getChatMessages } from '@/features/chat/api/getChatMessages';
import { markChatRead } from '@/features/chat/api/markChatRead';
import { ChatApiError, type ChatMessage } from '@/features/chat/types';

interface ChatFatalError {
  code: string;
  message: string;
}

export type ChatConnectionState =
  | 'connecting'
  | 'connected'
  | 'reconnecting'
  | 'disconnected';

export interface UseChatRoomOptions {
  /** 스터디 상세 화면에서 이미 알고 있으면 미리 넘겨줍니다. 모르면 서버가 SEND 시점에 알려줍니다. */
  isStudyCompleted?: boolean;
}

/**
 * DESC(최신순) 정렬을 유지한 채 messageId 기준으로 합치고 중복만 제거합니다.
 *
 * 같은 messageId가 양쪽에 있으면 b가 이기되, 두 가지 예외가 있습니다.
 * 삭제(deleted)는 되돌릴 수 없는 상태라 deleted=true인 버전이 항상 이기고,
 * 수정(editedAt)은 더 늦게 수정된 버전이 이깁니다. 이 규칙이 없으면
 * "삭제·수정 브로드캐스트 수신 → 재연결 이력 병합"처럼 합치는 순서에 따라
 * 지워지거나 고쳐진 메시지가 예전 원문으로 되살아날 수 있습니다.
 */
export function mergeMessagesDesc(a: ChatMessage[], b: ChatMessage[]): ChatMessage[] {
  const merged = new Map<number, ChatMessage>();
  for (const message of [...a, ...b]) {
    const existing = merged.get(message.messageId);
    if (existing !== undefined && !isNewerVersion(message, existing)) continue;
    merged.set(message.messageId, message);
  }
  return [...merged.values()].sort((x, y) => y.messageId - x.messageId);
}

/** candidate가 existing을 대체해야 하면 true. 동률이면 대체(b 우선 유지). */
function isNewerVersion(candidate: ChatMessage, existing: ChatMessage): boolean {
  if (existing.deleted === true) return candidate.deleted === true;
  if (candidate.deleted === true) return true;

  const candidateEditedAt = candidate.editedAt ? Date.parse(candidate.editedAt) : 0;
  const existingEditedAt = existing.editedAt ? Date.parse(existing.editedAt) : 0;

  return candidateEditedAt >= existingEditedAt;
}

function parseErrorPayload(body: string): ChatFatalError | null {
  try {
    return JSON.parse(body) as ChatFatalError;
  } catch {
    return null;
  }
}

/**
 * 스터디 채팅방 하나의 이력 로딩 + STOMP 실시간 송수신을 담당하는 훅입니다.
 * 화면(app/(app)/chat/[roomId].tsx)은 이 훅이 주는 상태만 그리면 됩니다.
 *
 * 메시지는 서버 응답과 동일하게 messageId 내림차순(최신이 배열 맨 앞)으로 유지합니다.
 * 화면에서 <FlatList inverted /> 로 그리면 그대로 "아래가 최신"인 채팅 UI가 됩니다.
 *
 * STOMP는 최초 REST 이력 조회(및 authenticatedFetch 토큰 재발급)가 성공한 뒤에만 시작합니다.
 */
export function useChatRoom(studyId: number, options: UseChatRoomOptions = {}) {
  const accessToken = useAuthStore((state) => state.accessToken);
  const initialStudyCompleted = options.isStudyCompleted ?? false;

  const [messages, setMessages] = useState<ChatMessage[]>([]);
  const [isLoadingInitial, setIsLoadingInitial] = useState(true);
  const [isLoadingMore, setIsLoadingMore] = useState(false);
  const [hasMoreHistory, setHasMoreHistory] = useState(false);
  const [nextCursor, setNextCursor] = useState<number | null>(null);
  /** 최초 REST 이력 성공 후에만 true. STOMP Effect의 배리어입니다. */
  const [initialHistoryReady, setInitialHistoryReady] = useState(false);
  const [connectionState, setConnectionState] = useState<ChatConnectionState>(() =>
    accessToken ? 'connecting' : 'disconnected',
  );
  const [isStudyCompleted, setIsStudyCompleted] = useState(initialStudyCompleted);
  const [fatalError, setFatalError] = useState<ChatFatalError | null>(null);
  const [activeStudyId, setActiveStudyId] = useState(studyId);

  const clientRef = useRef<Client | null>(null);
  const studyIdRef = useRef(studyId);

  // studyId 변경 시 렌더 중 상태를 맞춰 이전 스터디 값이 남지 않게 합니다.
  // (https://react.dev/reference/react/useState#storing-information-from-previous-renders)
  if (activeStudyId !== studyId) {
    setActiveStudyId(studyId);
    setMessages([]);
    setIsLoadingMore(false);
    setHasMoreHistory(false);
    setNextCursor(null);
    setInitialHistoryReady(false);
    setIsLoadingInitial(true);
    setFatalError(null);
    setIsStudyCompleted(initialStudyCompleted);
    setConnectionState(accessToken ? 'connecting' : 'disconnected');
  }

  const currentMemberId = useMemo(
    () => (accessToken ? decodeMemberIdFromAccessToken(accessToken) : null),
    [accessToken],
  );

  const markReadSafely = useCallback(
    (lastReadMessageId: number) => {
      markChatRead(studyId, lastReadMessageId).catch(() => {
        // 읽음 처리 실패로 채팅 자체를 막을 이유는 없어서 조용히 무시합니다.
      });
    },
    [studyId],
  );

  useEffect(() => {
    studyIdRef.current = studyId;
  }, [studyId]);

  // 최초 이력 로딩. 성공(재발급 포함) 후에만 STOMP를 허용합니다.
  useEffect(() => {
    let cancelled = false;

    (async () => {
      try {
        const page = await getChatMessages(studyId, { size: 30 });
        if (cancelled || studyIdRef.current !== studyId) return;

        setMessages((prev) => mergeMessagesDesc(page.content, prev));
        setHasMoreHistory(page.hasNext);
        setNextCursor(page.nextCursor);
        if (page.content.length > 0) {
          markReadSafely(page.content[0]!.messageId);
        }
        // authenticatedFetch 재발급이 끝났다면 이후 렌더의 accessToken이 최신입니다.
        setInitialHistoryReady(true);
      } catch (error) {
        if (cancelled || studyIdRef.current !== studyId) return;
        setInitialHistoryReady(false);
        setFatalError(
          error instanceof ChatApiError
            ? { code: error.code, message: error.message }
            : { code: 'UNKNOWN', message: '채팅 이력을 불러오지 못했습니다.' },
        );
      } finally {
        if (!cancelled && studyIdRef.current === studyId) {
          setIsLoadingInitial(false);
        }
      }
    })();

    return () => {
      cancelled = true;
    };
  }, [studyId, markReadSafely]);

  const loadMoreHistory = useCallback(() => {
    if (isLoadingMore || !hasMoreHistory || nextCursor === null) return;

    const requestStudyId = studyId;
    setIsLoadingMore(true);
    getChatMessages(requestStudyId, { cursor: nextCursor, size: 30 })
      .then((page) => {
        if (studyIdRef.current !== requestStudyId) return;
        setMessages((prev) => mergeMessagesDesc(prev, page.content));
        setHasMoreHistory(page.hasNext);
        setNextCursor(page.nextCursor);
      })
      .catch(() => {
        // 실패해도 hasMoreHistory를 그대로 둬서 다음 스크롤에서 재시도되게 합니다.
      })
      .finally(() => {
        if (studyIdRef.current === requestStudyId) {
          setIsLoadingMore(false);
        }
      });
  }, [studyId, isLoadingMore, hasMoreHistory, nextCursor]);

  // STOMP는 최초 REST 성공 + 최신 accessToken이 확정된 뒤에만 시작합니다.
  useEffect(() => {
    if (!accessToken || !initialHistoryReady) {
      return;
    }

    let disposed = false;
    const connectedStudyId = studyId;
    const connectHeaders: StompHeaders = { Authorization: `Bearer ${accessToken}` };
    const client = new Client({
      brokerURL: env.wsUrl,
      connectHeaders,
      reconnectDelay: 3000,
      // RN의 WebSocket은 arraybuffer binaryType으로 텍스트 프레임을 받으면 onmessage가
      // 아예 안 오는 경우가 있어서, 발신 프레임을 바이너리로 강제해 우회합니다.
      forceBinaryWSFrames: true,
      appendMissingNULLonIncoming: true,
    });

    const isActiveClient = () =>
      !disposed && clientRef.current === client && studyIdRef.current === connectedStudyId;

    // WebSocket 구독 시작과 함께 연결 상태를 외부에 노출합니다.
    // eslint-disable-next-line react-hooks/set-state-in-effect -- STOMP Client 생명주기와 동기화
    setConnectionState('connecting');

    client.onConnect = () => {
      if (!isActiveClient()) return;

      // 연결 완료를 외부에 노출하기 전에 구독을 먼저 등록합니다.
      client.subscribe(`/sub/studies/${connectedStudyId}/chat`, (frame: IMessage) => {
        if (!isActiveClient()) return;
        try {
          const incoming = JSON.parse(frame.body) as ChatMessage;
          setMessages((prev) => mergeMessagesDesc([incoming], prev));
          markReadSafely(incoming.messageId);
        } catch {
          console.warn('[Chat] Failed to parse incoming chat frame.');
        }
      });

      // 발행 payload 자체의 검증 실패(CHAT_MESSAGE_TYPE_INVALID 등)는 연결을 끊지 않고
      // 이 큐로만 전달되므로, CONNECT 성공 직후 반드시 구독해둬야 합니다.
      client.subscribe('/user/queue/errors', (frame: IMessage) => {
        if (!isActiveClient()) return;
        const payload = parseErrorPayload(frame.body);
        if (payload?.code === 'CHAT_STUDY_COMPLETED') {
          setIsStudyCompleted(true);
        }
      });

      setConnectionState('connected');

      // 최초·재연결 모두: 초기 REST와 SUBSCRIBE 사이 공백을 catch-up으로 보완합니다.
      // (연결 전 미전송 입력 복구 기능이 아닙니다.)
      getChatMessages(connectedStudyId, { size: 30 })
        .then((page) => {
          if (!isActiveClient()) return;
          setMessages((prev) => mergeMessagesDesc(page.content, prev));
          if (page.content.length > 0) {
            markReadSafely(page.content[0]!.messageId);
          }
        })
        .catch(() => {
          // catch-up 실패는 이미 연결된 STOMP와 현재 화면을 종료시키지 않습니다.
        });
    };

    client.onWebSocketClose = (event) => {
      console.warn('[Chat] WebSocket closed.', { code: event.code, reason: event.reason });
      if (!isActiveClient()) return;
      // Client가 아직 활성(재연결 예정)이면 reconnecting, cleanup 중이면 무시합니다.
      setConnectionState('reconnecting');
    };

    client.onWebSocketError = (event) => {
      console.warn('[Chat] WebSocket error.', event);
    };

    // STOMP ERROR 프레임은 CONNECT·SUBSCRIBE·SEND 권한 문제일 때 오며, 규약상 연결이
    // 끊깁니다. CHAT_STUDY_COMPLETED는 발행만 막힌 것뿐이라 치명적 에러로 취급하지 않습니다.
    client.onStompError = (frame) => {
      if (!isActiveClient()) return;
      const payload = parseErrorPayload(frame.body);
      console.warn('[Chat] STOMP error frame.', payload ?? frame.headers);
      if (!payload) return;
      if (payload.code === 'CHAT_STUDY_COMPLETED') {
        setIsStudyCompleted(true);
        return;
      }
      setFatalError(payload);
    };

    client.activate();
    clientRef.current = client;

    return () => {
      disposed = true;
      setConnectionState('disconnected');
      void client.deactivate();
      if (clientRef.current === client) {
        clientRef.current = null;
      }
    };
  }, [studyId, accessToken, initialHistoryReady, markReadSafely]);

  const publish = useCallback(
    (body: {
      messageType: 'TEXT' | 'IMAGE';
      content: string | null;
      imageFileId: number | null;
    }): boolean => {
      const client = clientRef.current;
      if (!client?.connected) {
        return false;
      }
      try {
        client.publish({
          destination: `/pub/studies/${studyId}/chat/messages`,
          body: JSON.stringify({ ...body, clientMessageId: Crypto.randomUUID() }),
        });
        return true;
      } catch {
        console.warn('[Chat] Failed to publish STOMP message.');
        return false;
      }
    },
    [studyId],
  );

  const sendText = useCallback(
    (text: string): boolean =>
      publish({ messageType: 'TEXT', content: text, imageFileId: null }),
    [publish],
  );

  const sendImage = useCallback(
    (imageFileId: number): boolean =>
      publish({ messageType: 'IMAGE', content: null, imageFileId }),
    [publish],
  );

  /**
   * 내 메시지를 삭제합니다. 성공하면 서버가 구독자 전원에게 deleted=true를
   * 발행하지만, 본인 화면은 브로드캐스트를 기다리지 않고 즉시 톰스톤으로
   * 바꿉니다. 실패는 호출한 화면이 다이얼로그로 안내할 수 있게 던집니다.
   */
  const deleteMessage = useCallback(
    async (messageId: number): Promise<void> => {
      await deleteChatMessage(studyId, messageId);
      setMessages((prev) =>
        prev.map((message) =>
          message.messageId === messageId
            ? { ...message, deleted: true, content: null, image: null }
            : message,
        ),
      );
    },
    [studyId],
  );

  /**
   * 내 TEXT 메시지의 본문을 수정합니다. 성공하면 본인 화면을 즉시 갱신하고,
   * 다른 구독자는 서버 브로드캐스트로 갱신됩니다. 실패는 호출한 화면이 안내합니다.
   */
  const editMessage = useCallback(
    async (messageId: number, content: string): Promise<void> => {
      const result = await editChatMessage(studyId, messageId, content);
      setMessages((prev) =>
        prev.map((message) =>
          message.messageId === messageId
            ? { ...message, content: result.content, editedAt: result.editedAt }
            : message,
        ),
      );
    },
    [studyId],
  );

  return {
    messages,
    currentMemberId,
    isLoadingInitial,
    isLoadingMore,
    hasMoreHistory,
    loadMoreHistory,
    connectionState,
    isConnected: connectionState === 'connected',
    isStudyCompleted,
    fatalError,
    sendText,
    sendImage,
    deleteMessage,
    editMessage,
  };
}
