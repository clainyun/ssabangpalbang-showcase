package com.ssafy.ssabangpalbang.chat.controller;

import com.ssafy.ssabangpalbang.auth.security.AuthenticatedMember;
import com.ssafy.ssabangpalbang.chat.dto.ChatMessageSendRequest;
import com.ssafy.ssabangpalbang.chat.service.ChatMessageService;
import com.ssafy.ssabangpalbang.global.error.BusinessException;
import com.ssafy.ssabangpalbang.global.error.ErrorCode;
import lombok.RequiredArgsConstructor;
import org.springframework.messaging.handler.annotation.DestinationVariable;
import org.springframework.messaging.handler.annotation.MessageMapping;
import org.springframework.messaging.handler.annotation.Payload;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Controller;

import java.security.Principal;

/**
 * STOMP SEND(/pub/studies/{studyId}/chat/messages) 진입점이다.
 *
 * <p>ChatChannelInterceptor가 이미 CONNECT 인증과 스터디 SEND 권한
 * (멤버 여부·완료 스터디 여부)을 검사했으므로, 여기서는 인증된 회원 ID를
 * 꺼내 ChatMessageService에 payload 검증·저장·발행을 위임한다.</p>
 */
@Controller
@RequiredArgsConstructor
public class ChatMessageController {

    private final ChatMessageService chatMessageService;

    @MessageMapping("/studies/{studyId}/chat/messages")
    public void send(
            @DestinationVariable Long studyId,
            @Payload ChatMessageSendRequest request,
            Principal principal
    ) {
        Long senderId = resolveMemberId(principal);
        chatMessageService.handleSend(studyId, senderId, request);
    }

    private Long resolveMemberId(Principal principal) {
        if (principal instanceof Authentication authentication
                && authentication.getPrincipal() instanceof AuthenticatedMember authenticatedMember) {
            return authenticatedMember.memberId();
        }
        throw new BusinessException(ErrorCode.CHAT_FORBIDDEN);
    }
}
