package com.ssafy.ssabangpalbang.chat.security;

import com.ssafy.ssabangpalbang.auth.security.AuthenticatedMember;
import com.ssafy.ssabangpalbang.auth.token.JwtTokenProvider;
import com.ssafy.ssabangpalbang.chat.port.StudyMembershipPort;
import com.ssafy.ssabangpalbang.global.error.BusinessException;
import com.ssafy.ssabangpalbang.global.error.ErrorCode;
import com.ssafy.ssabangpalbang.member.domain.Member;
import com.ssafy.ssabangpalbang.member.domain.MemberStatus;
import com.ssafy.ssabangpalbang.member.repository.MemberRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.messaging.Message;
import org.springframework.messaging.simp.stomp.StompCommand;
import org.springframework.messaging.simp.stomp.StompHeaderAccessor;
import org.springframework.messaging.support.MessageBuilder;
import org.springframework.messaging.support.MessageHeaderAccessor;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.test.util.ReflectionTestUtils;

import java.security.Principal;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ChatChannelInterceptorTest {

    private static final Long MEMBER_ID = 7L;
    private static final Long STUDY_ID = 42L;
    private static final String SUBSCRIBE_DESTINATION = "/sub/studies/42/chat";
    private static final String SEND_DESTINATION = "/pub/studies/42/chat/messages";

    @Mock
    private JwtTokenProvider jwtTokenProvider;

    @Mock
    private MemberRepository memberRepository;

    @Mock
    private StudyMembershipPort studyMembershipPort;

    private ChatChannelInterceptor interceptor;

    @BeforeEach
    void setUp() {
        interceptor = new ChatChannelInterceptor(
                jwtTokenProvider,
                memberRepository,
                studyMembershipPort
        );
    }

    @Test
    void CONNECT_토큰이_없으면_CHAT_CONNECTION_UNAUTHORIZED를_던진다() {
        Message<?> connectMessage = connectMessage(null);

        assertThatThrownBy(() -> interceptor.preSend(connectMessage, null))
                .isInstanceOf(BusinessException.class)
                .satisfies(exception -> assertThat(
                        ((BusinessException) exception).getErrorCode())
                        .isEqualTo(ErrorCode.CHAT_CONNECTION_UNAUTHORIZED));

        verifyNoInteractions(jwtTokenProvider, memberRepository);
    }

    @Test
    void CONNECT_Bearer_접두어가_없으면_CHAT_CONNECTION_UNAUTHORIZED를_던진다() {
        Message<?> connectMessage = connectMessage("access-token");

        assertThatThrownBy(() -> interceptor.preSend(connectMessage, null))
                .isInstanceOf(BusinessException.class)
                .satisfies(exception -> assertThat(
                        ((BusinessException) exception).getErrorCode())
                        .isEqualTo(ErrorCode.CHAT_CONNECTION_UNAUTHORIZED));

        verifyNoInteractions(jwtTokenProvider, memberRepository);
    }

    @Test
    void CONNECT_토큰이_유효하지_않으면_CHAT_CONNECTION_UNAUTHORIZED로_통일한다() {
        Message<?> connectMessage = connectMessage("Bearer invalid-token");
        when(jwtTokenProvider.parseAccessToken("invalid-token"))
                .thenThrow(new BusinessException(ErrorCode.AUTH_ACCESS_TOKEN_EXPIRED));

        assertThatThrownBy(() -> interceptor.preSend(connectMessage, null))
                .isInstanceOf(BusinessException.class)
                .satisfies(exception -> assertThat(
                        ((BusinessException) exception).getErrorCode())
                        .isEqualTo(ErrorCode.CHAT_CONNECTION_UNAUTHORIZED));

        verifyNoInteractions(memberRepository);
    }

    @Test
    void CONNECT_회원이_존재하지_않으면_CHAT_CONNECTION_UNAUTHORIZED를_던진다() {
        Message<?> connectMessage = connectMessage("Bearer good-token");
        when(jwtTokenProvider.parseAccessToken("good-token")).thenReturn(MEMBER_ID);
        when(memberRepository.findById(MEMBER_ID)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> interceptor.preSend(connectMessage, null))
                .isInstanceOf(BusinessException.class)
                .satisfies(exception -> assertThat(
                        ((BusinessException) exception).getErrorCode())
                        .isEqualTo(ErrorCode.CHAT_CONNECTION_UNAUTHORIZED));
    }

    @Test
    void CONNECT_회원이_WITHDRAWN_상태면_CHAT_CONNECTION_UNAUTHORIZED를_던진다() {
        Message<?> connectMessage = connectMessage("Bearer good-token");
        when(jwtTokenProvider.parseAccessToken("good-token")).thenReturn(MEMBER_ID);
        when(memberRepository.findById(MEMBER_ID))
                .thenReturn(Optional.of(member(MemberStatus.WITHDRAWN)));

        assertThatThrownBy(() -> interceptor.preSend(connectMessage, null))
                .isInstanceOf(BusinessException.class)
                .satisfies(exception -> assertThat(
                        ((BusinessException) exception).getErrorCode())
                        .isEqualTo(ErrorCode.CHAT_CONNECTION_UNAUTHORIZED));
    }

    @Test
    void CONNECT_정상_토큰과_ACTIVE_회원이면_Principal이_설정된다() {
        StompHeaderAccessor accessor = StompHeaderAccessor.create(StompCommand.CONNECT);
        accessor.setNativeHeader("Authorization", "Bearer good-token");
        accessor.setLeaveMutable(true);
        Message<?> connectMessage =
                MessageBuilder.createMessage(new byte[0], accessor.getMessageHeaders());

        when(jwtTokenProvider.parseAccessToken("good-token")).thenReturn(MEMBER_ID);
        when(memberRepository.findById(MEMBER_ID))
                .thenReturn(Optional.of(member(MemberStatus.ACTIVE)));

        interceptor.preSend(connectMessage, null);

        StompHeaderAccessor resultAccessor =
                MessageHeaderAccessor.getAccessor(connectMessage, StompHeaderAccessor.class);
        Principal user = resultAccessor.getUser();

        assertThat(user).isInstanceOf(UsernamePasswordAuthenticationToken.class);
        assertThat(((UsernamePasswordAuthenticationToken) user).getPrincipal())
                .isEqualTo(new AuthenticatedMember(MEMBER_ID));
    }

    @Test
    void SUBSCRIBE_사용자_개인큐_구독은_권한_검사를_거치지_않는다() {
        Message<?> subscribeMessage =
                authenticatedFrame(StompCommand.SUBSCRIBE, "/user/queue/errors");

        interceptor.preSend(subscribeMessage, null);

        verifyNoInteractions(studyMembershipPort);
    }

    @Test
    void SUBSCRIBE_경로_형식이_다르면_CHAT_FORBIDDEN을_던진다() {
        Message<?> subscribeMessage =
                authenticatedFrame(StompCommand.SUBSCRIBE, "/sub/invalid");

        assertThatThrownBy(() -> interceptor.preSend(subscribeMessage, null))
                .isInstanceOf(BusinessException.class)
                .satisfies(exception -> assertThat(
                        ((BusinessException) exception).getErrorCode())
                        .isEqualTo(ErrorCode.CHAT_FORBIDDEN));
    }

    @Test
    void SUBSCRIBE_스터디_멤버가_아니면_CHAT_FORBIDDEN을_던진다() {
        Message<?> subscribeMessage =
                authenticatedFrame(StompCommand.SUBSCRIBE, SUBSCRIBE_DESTINATION);
        when(studyMembershipPort.isStudyMember(STUDY_ID, MEMBER_ID)).thenReturn(false);

        assertThatThrownBy(() -> interceptor.preSend(subscribeMessage, null))
                .isInstanceOf(BusinessException.class)
                .satisfies(exception -> assertThat(
                        ((BusinessException) exception).getErrorCode())
                        .isEqualTo(ErrorCode.CHAT_FORBIDDEN));
    }

    @Test
    void SUBSCRIBE_스터디_멤버면_통과한다() {
        Message<?> subscribeMessage =
                authenticatedFrame(StompCommand.SUBSCRIBE, SUBSCRIBE_DESTINATION);
        when(studyMembershipPort.isStudyMember(STUDY_ID, MEMBER_ID)).thenReturn(true);

        Message<?> result = interceptor.preSend(subscribeMessage, null);

        assertThat(result).isSameAs(subscribeMessage);
    }

    @Test
    void SUBSCRIBE는_SEND_가능_여부를_확인하지_않는다() {
        Message<?> subscribeMessage =
                authenticatedFrame(StompCommand.SUBSCRIBE, SUBSCRIBE_DESTINATION);
        when(studyMembershipPort.isStudyMember(STUDY_ID, MEMBER_ID)).thenReturn(true);

        interceptor.preSend(subscribeMessage, null);

        verify(studyMembershipPort, never()).isStudyOpenForSend(any());
    }

    @Test
    void SEND_스터디_멤버가_아니면_CHAT_FORBIDDEN을_던진다() {
        Message<?> sendMessage =
                authenticatedFrame(StompCommand.SEND, SEND_DESTINATION);
        when(studyMembershipPort.isStudyMember(STUDY_ID, MEMBER_ID)).thenReturn(false);

        assertThatThrownBy(() -> interceptor.preSend(sendMessage, null))
                .isInstanceOf(BusinessException.class)
                .satisfies(exception -> assertThat(
                        ((BusinessException) exception).getErrorCode())
                        .isEqualTo(ErrorCode.CHAT_FORBIDDEN));
    }

    @Test
    void SEND_완료된_스터디면_CHAT_STUDY_COMPLETED를_던진다() {
        Message<?> sendMessage =
                authenticatedFrame(StompCommand.SEND, SEND_DESTINATION);
        when(studyMembershipPort.isStudyMember(STUDY_ID, MEMBER_ID)).thenReturn(true);
        when(studyMembershipPort.isStudyOpenForSend(STUDY_ID)).thenReturn(false);

        assertThatThrownBy(() -> interceptor.preSend(sendMessage, null))
                .isInstanceOf(BusinessException.class)
                .satisfies(exception -> assertThat(
                        ((BusinessException) exception).getErrorCode())
                        .isEqualTo(ErrorCode.CHAT_STUDY_COMPLETED));
    }

    @Test
    void SEND_스터디_멤버이고_SEND_가능한_상태면_통과한다() {
        Message<?> sendMessage =
                authenticatedFrame(StompCommand.SEND, SEND_DESTINATION);
        when(studyMembershipPort.isStudyMember(STUDY_ID, MEMBER_ID)).thenReturn(true);
        when(studyMembershipPort.isStudyOpenForSend(STUDY_ID)).thenReturn(true);

        Message<?> result = interceptor.preSend(sendMessage, null);

        assertThat(result).isSameAs(sendMessage);
    }

    private Message<?> connectMessage(String authorizationHeader) {
        StompHeaderAccessor accessor = StompHeaderAccessor.create(StompCommand.CONNECT);
        if (authorizationHeader != null) {
            accessor.setNativeHeader("Authorization", authorizationHeader);
        }
        accessor.setLeaveMutable(true);
        return MessageBuilder.createMessage(new byte[0], accessor.getMessageHeaders());
    }

    private Message<?> authenticatedFrame(StompCommand command, String destination) {
        StompHeaderAccessor accessor = StompHeaderAccessor.create(command);
        accessor.setDestination(destination);
        accessor.setUser(UsernamePasswordAuthenticationToken.authenticated(
                new AuthenticatedMember(MEMBER_ID), null, List.of()
        ));
        accessor.setLeaveMutable(true);
        return MessageBuilder.createMessage(new byte[0], accessor.getMessageHeaders());
    }

    private Member member(MemberStatus status) {
        Member member = new Member("member@example.com", "hash", "nickname");
        ReflectionTestUtils.setField(member, "id", MEMBER_ID);
        ReflectionTestUtils.setField(member, "status", status);
        return member;
    }
}
