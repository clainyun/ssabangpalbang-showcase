import { useCallback, useEffect, useRef, useState } from 'react';

import {
  AuthSessionChangedError,
  AuthSessionExpiredError,
} from '@/lib/authenticatedFetch';

import { createChatbotConversation } from './api/createChatbotConversation';
import { getChatbotMessages } from './api/getChatbotMessages';
import { sendChatbotMessage } from './api/sendChatbotMessage';
import { ChatbotApiError, type ChatbotMessage } from './api/types';

/** 폴링 기본 간격(서버 recommendedIntervalMs가 오면 그 값으로 교체). */
const DEFAULT_POLL_INTERVAL_MS = 2500;
/** 폴링 상한. 2.5s × 48 = 약 2분. 이후에도 미완이면 폴링을 멈춘다(서버가 별도 복구). */
const MAX_POLL_ATTEMPTS = 48;
/** 폴링 중 연속 네트워크 실패 허용 횟수. */
const MAX_POLL_FAILURES = 5;
/** 이력 조회 페이지 크기. 한 세션 대화는 짧으므로 최근 페이지 병합으로 충분. */
const HISTORY_PAGE_SIZE = 30;

export interface ChatbotConversationController {
  /** 오름차순(과거 → 최신) 메시지. */
  messages: ChatbotMessage[];
  /** 대화 생성 요청 중(첫 질문). */
  isCreating: boolean;
  /** 질문 전송 요청 중. */
  isSending: boolean;
  /** AI가 답변 생성 중 — 입력창을 잠근다. */
  isResponding: boolean;
  /** 표시할 오류 메시지(없으면 null). */
  error: string | null;
  /** 질문 전송(내부에서 필요 시 대화 생성까지 처리). */
  send: (content: string) => void;
  dismissError: () => void;
}

function isSessionError(error: unknown): boolean {
  return (
    error instanceof AuthSessionExpiredError ||
    error instanceof AuthSessionChangedError
  );
}

/** messageId 기준으로 병합하고 오름차순 정렬. 같은 id는 새 값으로 갱신(placeholder → 완료). */
function mergeMessages(
  previous: ChatbotMessage[],
  incoming: ChatbotMessage[],
): ChatbotMessage[] {
  const byId = new Map<number, ChatbotMessage>();
  for (const message of previous) byId.set(message.messageId, message);
  for (const message of incoming) byId.set(message.messageId, message);
  return [...byId.values()].sort((a, b) => a.messageId - b.messageId);
}

export function useChatbotConversation(
  apartmentId: number,
): ChatbotConversationController {
  const [messages, setMessages] = useState<ChatbotMessage[]>([]);
  const [isCreating, setIsCreating] = useState(false);
  const [isSending, setIsSending] = useState(false);
  const [error, setError] = useState<string | null>(null);

  const conversationIdRef = useRef<number | null>(null);
  const mountedRef = useRef(true);
  const isSendingRef = useRef(false);
  /** 답변 생성 중이면 true — 폴링 진행 여부와 전송 가드에 쓴다. */
  const respondingRef = useRef(false);
  const pollTimeoutRef = useRef<ReturnType<typeof setTimeout> | null>(null);
  const pollIntervalMsRef = useRef(DEFAULT_POLL_INTERVAL_MS);
  const pollAttemptsRef = useRef(0);
  const pollFailuresRef = useRef(0);
  // 재귀 폴링은 poll을 직접 참조(자기 참조 useCallback)하는 대신 ref를 거쳐 최신
  // 구현을 호출한다.
  const pollRef = useRef<() => Promise<void>>(async () => {});

  const clearPollTimer = useCallback(() => {
    if (pollTimeoutRef.current) {
      clearTimeout(pollTimeoutRef.current);
      pollTimeoutRef.current = null;
    }
  }, []);

  const scheduleNextPoll = useCallback((run: () => void) => {
    pollTimeoutRef.current = setTimeout(run, pollIntervalMsRef.current);
  }, []);

  const poll = useCallback(async () => {
    const conversationId = conversationIdRef.current;
    if (conversationId === null || !respondingRef.current) return;

    pollAttemptsRef.current += 1;

    try {
      const list = await getChatbotMessages(apartmentId, conversationId, {
        size: HISTORY_PAGE_SIZE,
      });
      if (!mountedRef.current) return;
      pollFailuresRef.current = 0;
      setMessages((previous) => mergeMessages(previous, list.content));

      if (
        list.hasResponseInProgress &&
        pollAttemptsRef.current < MAX_POLL_ATTEMPTS
      ) {
        scheduleNextPoll(() => void pollRef.current());
      } else {
        // 완료됐거나 상한에 도달 — 폴링 종료. (상한 도달 시 placeholder는 그대로 두고
        // 서버 복구를 기다린다. 사용자는 다시 질문할 수 있다.)
        respondingRef.current = false;
      }
    } catch (caught) {
      if (!mountedRef.current) return;
      if (isSessionError(caught)) {
        respondingRef.current = false;
        return;
      }
      pollFailuresRef.current += 1;
      if (
        pollFailuresRef.current >= MAX_POLL_FAILURES ||
        pollAttemptsRef.current >= MAX_POLL_ATTEMPTS
      ) {
        respondingRef.current = false;
        setError('답변을 확인하는 중 문제가 발생했어요. 잠시 후 다시 시도해주세요.');
        return;
      }
      scheduleNextPoll(() => void pollRef.current());
    }
  }, [apartmentId, scheduleNextPoll]);

  const startPolling = useCallback(() => {
    if (respondingRef.current && pollTimeoutRef.current !== null) return;
    respondingRef.current = true;
    pollAttemptsRef.current = 0;
    pollFailuresRef.current = 0;
    clearPollTimer();
    scheduleNextPoll(() => void pollRef.current());
  }, [clearPollTimer, scheduleNextPoll]);

  // pollRef가 항상 최신 poll을 가리키게 유지(재귀 폴링이 stale 클로저를 잡지 않도록).
  useEffect(() => {
    pollRef.current = poll;
  }, [poll]);

  const send = useCallback(
    (raw: string) => {
      const content = raw.trim();
      if (content.length === 0) return;
      if (isSendingRef.current || respondingRef.current) return;

      isSendingRef.current = true;
      setIsSending(true);
      setError(null);

      void (async () => {
        try {
          let conversationId = conversationIdRef.current;
          if (conversationId === null) {
            setIsCreating(true);
            const conversation = await createChatbotConversation(apartmentId);
            if (!mountedRef.current) return;
            conversationId = conversation.conversationId;
            conversationIdRef.current = conversationId;
            setIsCreating(false);
          }

          const result = await sendChatbotMessage(
            apartmentId,
            conversationId,
            content,
          );
          if (!mountedRef.current) return;

          pollIntervalMsRef.current =
            result.polling.recommendedIntervalMs > 0
              ? result.polling.recommendedIntervalMs
              : DEFAULT_POLL_INTERVAL_MS;
          setMessages((previous) =>
            mergeMessages(previous, [
              result.userMessage,
              result.assistantMessage,
            ]),
          );
          startPolling();
        } catch (caught) {
          if (!mountedRef.current) return;
          setIsCreating(false);
          if (isSessionError(caught)) return;

          if (
            caught instanceof ChatbotApiError &&
            caught.code === 'CHATBOT_RESPONSE_IN_PROGRESS'
          ) {
            // 이미 답변 생성 중 — 새 버블 추가 없이 폴링만 이어간다.
            startPolling();
            return;
          }

          setError(
            caught instanceof Error && caught.message.length > 0
              ? caught.message
              : '잠시 후 다시 시도해주세요.',
          );
        } finally {
          isSendingRef.current = false;
          if (mountedRef.current) setIsSending(false);
        }
      })();
    },
    [apartmentId, startPolling],
  );

  const dismissError = useCallback(() => setError(null), []);

  // 이 훅은 아파트별로 새로 마운트되는 챗봇 모달에서 사용한다(모달을 열 때만 렌더 →
  // 닫으면 언마운트되어 대화가 자연히 초기화된다). 따라서 apartmentId는 마운트 동안
  // 고정이라고 보고, 별도의 아파트 변경 리셋 로직을 두지 않는다. 아파트가 다른
  // 화면에서 재사용할 때는 소비 측에서 key={apartmentId}로 마운트를 분리한다.

  useEffect(() => {
    mountedRef.current = true;
    return () => {
      mountedRef.current = false;
      respondingRef.current = false;
      clearPollTimer();
    };
  }, [clearPollTimer]);

  const isResponding = messages.some(
    (message) =>
      message.role === 'ASSISTANT' &&
      (message.status === 'PENDING' || message.status === 'PROCESSING'),
  );

  return {
    messages,
    isCreating,
    isSending,
    isResponding,
    error,
    send,
    dismissError,
  };
}
