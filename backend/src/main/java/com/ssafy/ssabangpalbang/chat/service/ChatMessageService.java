package com.ssafy.ssabangpalbang.chat.service;

import com.ssafy.ssabangpalbang.chat.domain.MessageType;
import com.ssafy.ssabangpalbang.chat.dto.ChatImageResponse;
import com.ssafy.ssabangpalbang.chat.dto.ChatMessageSendRequest;
import com.ssafy.ssabangpalbang.chat.dto.ChatSenderResponse;
import com.ssafy.ssabangpalbang.chat.repository.ChatMessageRepository;
import com.ssafy.ssabangpalbang.global.error.BusinessException;
import com.ssafy.ssabangpalbang.global.error.ErrorCode;
import com.ssafy.ssabangpalbang.media.domain.FileMeta;
import com.ssafy.ssabangpalbang.media.service.MediaPresignedUrlProvider;
import com.ssafy.ssabangpalbang.member.domain.Member;
import com.ssafy.ssabangpalbang.member.repository.MemberRepository;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;

/**
 * 채팅 메시지 SEND 전체 흐름을 조율하는 Facade다.
 *
 * <p>이 클래스 자체는 트랜잭션을 열지 않는다(각 Repository 호출·ChatMessageWriter
 * 호출이 각자의 트랜잭션 경계를 갖는다). unique 위반(DataIntegrityViolationException)이
 * 발생한 실패 트랜잭션 안에서 재조회하지 않고, 별도의(자체 트랜잭션을 갖는)
 * Repository 호출로 기존 메시지를 재조회한다.</p>
 */
@Service
@RequiredArgsConstructor
public class ChatMessageService {

    private static final Logger log = LoggerFactory.getLogger(ChatMessageService.class);

    private final ChatMessageRepository chatMessageRepository;
    private final ChatMessageWriter chatMessageWriter;
    private final ChatImageValidator chatImageValidator;
    private final MemberRepository memberRepository;
    private final MediaPresignedUrlProvider mediaPresignedUrlProvider;

    /**
     * study 권한(멤버·SEND 가능 여부)은 ChatChannelInterceptor가 이미 검증했으므로
     * 여기서는 messageType·content·image 등 payload 자체만 검증한다.
     */
    public void handleSend(Long studyId, Long senderId, ChatMessageSendRequest request) {
        MessageType messageType = parseSendableMessageType(request.messageType());
        String clientMessageId = requireClientMessageId(request.clientMessageId());

        if (chatMessageRepository.findBySenderIdAndClientMessageId(senderId, clientMessageId)
                .isPresent()) {
            log.info("중복 clientMessageId 재전송을 무시합니다. senderId={}, clientMessageId={}",
                    senderId, clientMessageId);
            return;
        }

        Member sender = memberRepository.findById(senderId)
                .orElseThrow(() -> new BusinessException(ErrorCode.MEMBER_NOT_FOUND));
        ChatSenderResponse senderResponse = ChatSenderResponse.from(sender);

        try {
            if (messageType == MessageType.TEXT) {
                String content = requireContent(request.content());
                chatMessageWriter.saveText(studyId, senderId, content, clientMessageId, senderResponse);
            } else {
                FileMeta fileMeta = chatImageValidator.validate(studyId, senderId, request.imageFileId());
                ChatImageResponse image = buildImageResponse(fileMeta);
                chatMessageWriter.saveImage(
                        studyId, senderId, fileMeta.getId(), clientMessageId, senderResponse, image
                );
            }
        } catch (DataIntegrityViolationException exception) {
            handleConcurrentDuplicate(senderId, clientMessageId, exception);
        }
    }

    private void handleConcurrentDuplicate(
            Long senderId,
            String clientMessageId,
            DataIntegrityViolationException exception
    ) {
        // 실패한 저장 트랜잭션은 이미 롤백되었다. 별도의(새) 트랜잭션으로 재조회한다.
        chatMessageRepository.findBySenderIdAndClientMessageId(senderId, clientMessageId)
                .orElseThrow(() -> exception);
        log.info("동시 중복 전송 경쟁에서 유일성 제약으로 저장이 차단되어 무시합니다. "
                + "senderId={}, clientMessageId={}", senderId, clientMessageId);
    }

    private ChatImageResponse buildImageResponse(FileMeta fileMeta) {
        String imageUrl;
        try {
            imageUrl = mediaPresignedUrlProvider
                    .generateGetUrl(fileMeta.getId());
        } catch (BusinessException exception) {
            if (exception.getErrorCode()
                    == ErrorCode.MEDIA_FILE_NOT_FOUND
                    || exception.getErrorCode()
                    == ErrorCode.MEDIA_ACCESS_DENIED) {
                throw new BusinessException(
                        ErrorCode.CHAT_IMAGE_INVALID
                );
            }
            throw exception;
        }
        return new ChatImageResponse(
                fileMeta.getId(),
                imageUrl,
                fileMeta.getUploadStatus().name()
        );
    }

    private MessageType parseSendableMessageType(String messageType) {
        MessageType parsed;
        try {
            parsed = MessageType.valueOf(messageType);
        } catch (IllegalArgumentException | NullPointerException exception) {
            throw new BusinessException(ErrorCode.CHAT_MESSAGE_TYPE_INVALID);
        }
        if (parsed == MessageType.SYSTEM) {
            // 클라이언트는 SYSTEM 메시지를 직접 발행할 수 없다.
            throw new BusinessException(ErrorCode.CHAT_MESSAGE_TYPE_INVALID);
        }
        return parsed;
    }

    private String requireClientMessageId(String clientMessageId) {
        if (clientMessageId == null || clientMessageId.isBlank()) {
            throw new BusinessException(ErrorCode.INVALID_INPUT_VALUE);
        }
        return clientMessageId;
    }

    private String requireContent(String content) {
        if (content == null || content.isBlank()) {
            throw new BusinessException(ErrorCode.CHAT_CONTENT_REQUIRED);
        }
        return content;
    }
}
