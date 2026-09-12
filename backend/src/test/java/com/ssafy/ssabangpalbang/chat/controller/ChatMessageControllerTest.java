package com.ssafy.ssabangpalbang.chat.controller;

import com.ssafy.ssabangpalbang.auth.security.AuthenticatedMember;
import com.ssafy.ssabangpalbang.chat.dto.ChatMessageSendRequest;
import com.ssafy.ssabangpalbang.chat.service.ChatMessageService;
import com.ssafy.ssabangpalbang.global.error.BusinessException;
import com.ssafy.ssabangpalbang.global.error.ErrorCode;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;

import java.security.Principal;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;

@ExtendWith(MockitoExtension.class)
class ChatMessageControllerTest {

    private static final Long STUDY_ID = 7L;
    private static final Long MEMBER_ID = 42L;

    @Mock
    private ChatMessageService chatMessageService;

    private ChatMessageController controller;

    @Test
    void 인증된_회원_ID로_ChatMessageService에_위임한다() {
        controller = new ChatMessageController(chatMessageService);
        ChatMessageSendRequest request = new ChatMessageSendRequest(
                "TEXT", "내용", null, "client-message-id"
        );
        Principal principal = authenticatedPrincipal(MEMBER_ID);

        controller.send(STUDY_ID, request, principal);

        verify(chatMessageService).handleSend(STUDY_ID, MEMBER_ID, request);
    }

    @Test
    void Principal이_AuthenticatedMember가_아니면_CHAT_FORBIDDEN을_던진다() {
        controller = new ChatMessageController(chatMessageService);
        ChatMessageSendRequest request = new ChatMessageSendRequest(
                "TEXT", "내용", null, "client-message-id"
        );

        assertThatThrownBy(() -> controller.send(STUDY_ID, request, () -> "anonymous"))
                .isInstanceOf(BusinessException.class)
                .satisfies(exception -> assertThat(
                        ((BusinessException) exception).getErrorCode())
                        .isEqualTo(ErrorCode.CHAT_FORBIDDEN));

        verifyNoInteractions(chatMessageService);
    }

    private Principal authenticatedPrincipal(Long memberId) {
        return UsernamePasswordAuthenticationToken.authenticated(
                new AuthenticatedMember(memberId), null, List.of()
        );
    }
}
