// 챗봇 API 계약 타입.
// 엔드포인트: /api/v1/apartments/{apartmentId}/chatbot/conversations[...]
// 답변은 SSE 스트리밍이 아니라 "질문 접수(202) → 이력 조회 폴링" 방식이다.

export type ChatbotRole = 'USER' | 'ASSISTANT';

export type ChatbotMessageStatus =
  | 'PENDING'
  | 'PROCESSING'
  | 'COMPLETED'
  | 'FAILED';

/** 답변 근거 모드. USER 메시지는 항상 NONE. */
export type ChatbotBasisType = 'REPORT' | 'WEB' | 'NONE';

export interface ApiEnvelope<T> {
  success: boolean;
  code: string;
  message: string;
  data: T;
}

export class ChatbotApiError extends Error {
  code: string;
  /** 409/429 등 오류 응답의 부가 data(assistantMessageId, retryAfterSeconds 등). */
  data?: unknown;

  constructor(code: string, message: string, data?: unknown) {
    super(message);
    this.name = 'ChatbotApiError';
    this.code = code;
    this.data = data;
  }
}

export interface ChatbotApartment {
  apartmentId: number;
  name: string;
  /** 대화 시작 응답에만 포함. 이력 조회 응답에는 없다. */
  address?: string;
}

export interface ChatbotAvailableReport {
  reportId: number;
  publicId?: string;
  title: string;
}

/**
 * 답변 출처. sourceType에 따라 채워지는 필드가 다르다.
 *  - REPORT: sourceId, publicId, sectionLabel (url=null)
 *  - APARTMENT_TRANSACTION / WEB_PAGE: observedAt, url
 */
export interface ChatbotSource {
  sourceType: string;
  sourceId: number | null;
  publicId?: string | null;
  title: string;
  sectionLabel?: string | null;
  observedAt?: string | null;
  url: string | null;
}

/**
 * 대화 메시지 한 건. 이력 조회(content[])는 failReason·retryable을 항상 포함하고,
 * 질문 전송 응답의 userMessage/assistantMessage는 일부를 생략하므로 optional로 둔다.
 * ASSISTANT가 PENDING/PROCESSING인 동안 content는 null.
 */
export interface ChatbotMessage {
  messageId: number;
  role: ChatbotRole;
  content: string | null;
  status: ChatbotMessageStatus;
  basisType: ChatbotBasisType;
  basisLabel: string | null;
  sources: ChatbotSource[];
  failReason?: string | null;
  retryable?: boolean;
  createdAt: string;
  completedAt: string | null;
}

/** POST .../conversations — 대화 시작 */
export interface CreateChatbotConversationResult {
  conversationId: number;
  apartment: ChatbotApartment;
  /** 질문 전송 전 예상 근거 모드(안내용). 최종 basisType은 질문 처리 시 재결정. */
  preferredBasisType: ChatbotBasisType;
  preferredBasisLabel: string;
  availableReport: ChatbotAvailableReport | null;
  recommendedQuestions: string[];
  lastMessageAt: string | null;
  createdAt: string;
}

export interface ChatbotBasisPolicy {
  basisType: ChatbotBasisType;
  basisLabel: string | null;
  reportId: number | null;
}

/** POST .../messages — 질문 전송(202 Accepted) */
export interface SendChatbotMessageResult {
  conversationId: number;
  basisPolicy: ChatbotBasisPolicy;
  userMessage: ChatbotMessage;
  assistantMessage: ChatbotMessage;
  polling: {
    /** 이력 조회 API 경로. 이 경로를 recommendedIntervalMs 간격으로 폴링. */
    messageHistoryApi: string;
    recommendedIntervalMs: number;
  };
}

/** GET .../messages — 대화 이력 조회(폴링 대상) */
export interface ChatbotMessageList {
  conversationId: number;
  apartment: ChatbotApartment;
  /** 오름차순(과거 → 최신). */
  content: ChatbotMessage[];
  nextCursor: number | null;
  hasNext: boolean;
  lastMessageAt: string | null;
  /** PENDING/PROCESSING 답변이 있으면 true. false가 되면 폴링을 멈춘다. */
  hasResponseInProgress: boolean;
}
