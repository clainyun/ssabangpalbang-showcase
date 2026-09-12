export type ChatMessageType = 'TEXT' | 'IMAGE' | 'SYSTEM';

export interface ChatSender {
  memberId: number;
  nickname: string;
  selectedCharacterId: string;
}

export interface ChatImage {
  fileId: number;
  imageUrl: string | null;
  uploadStatus: string;
}

export interface ChatMessage {
  messageId: number;
  messageType: ChatMessageType;
  content: string | null;
  image: ChatImage | null;
  /** SYSTEM 메시지는 null입니다. */
  sender: ChatSender | null;
  createdAt: string;
  /**
   * 소프트 삭제 여부. true면 content·image가 null로 내려오고 화면에는
   * "삭제된 메시지" 톰스톤을 그립니다. 구버전 서버 응답에는 없을 수 있어 optional.
   */
  deleted?: boolean;
  /**
   * 마지막 수정 시각(ISO). 수정된 적 없으면 null/누락. 있으면 "수정됨"을 표시하고,
   * 같은 메시지의 두 버전을 합칠 때 더 늦게 수정된 쪽을 채택하는 기준이 됩니다.
   */
  editedAt?: string | null;
}

/** 세 REST 엔드포인트와 STOMP 오류 payload가 공통으로 쓰는 {code, message} 에러. */
export class ChatApiError extends Error {
  code: string;

  constructor(code: string, message: string) {
    super(message);
    this.code = code;
  }
}
