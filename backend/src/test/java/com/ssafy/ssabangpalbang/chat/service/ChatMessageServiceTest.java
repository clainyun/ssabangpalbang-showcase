package com.ssafy.ssabangpalbang.chat.service;

import com.ssafy.ssabangpalbang.chat.domain.ChatMessage;
import com.ssafy.ssabangpalbang.chat.dto.ChatImageResponse;
import com.ssafy.ssabangpalbang.chat.dto.ChatMessageSendRequest;
import com.ssafy.ssabangpalbang.chat.dto.ChatSenderResponse;
import com.ssafy.ssabangpalbang.chat.repository.ChatMessageRepository;
import com.ssafy.ssabangpalbang.global.error.BusinessException;
import com.ssafy.ssabangpalbang.global.error.ErrorCode;
import com.ssafy.ssabangpalbang.media.domain.FileMeta;
import com.ssafy.ssabangpalbang.media.domain.UploadStatus;
import com.ssafy.ssabangpalbang.media.service.MediaPresignedUrlProvider;
import com.ssafy.ssabangpalbang.member.domain.Member;
import com.ssafy.ssabangpalbang.member.repository.MemberRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.beans.BeanUtils;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ChatMessageServiceTest {

    private static final Long STUDY_ID = 7L;
    private static final Long SENDER_ID = 42L;
    private static final String CLIENT_MESSAGE_ID = "08a63b81-5a97-4dbc-93a1-d984f83c2e12";

    @Mock
    private ChatMessageRepository chatMessageRepository;

    @Mock
    private ChatMessageWriter chatMessageWriter;

    @Mock
    private ChatImageValidator chatImageValidator;

    @Mock
    private MemberRepository memberRepository;

    @Mock
    private MediaPresignedUrlProvider mediaPresignedUrlProvider;

    private ChatMessageService chatMessageService;

    @BeforeEach
    void setUp() {
        chatMessageService = new ChatMessageService(
                chatMessageRepository,
                chatMessageWriter,
                chatImageValidator,
                memberRepository,
                mediaPresignedUrlProvider
        );
        lenient().when(chatMessageRepository.findBySenderIdAndClientMessageId(SENDER_ID, CLIENT_MESSAGE_ID))
                .thenReturn(Optional.empty());
        lenient().when(memberRepository.findById(SENDER_ID)).thenReturn(Optional.of(member()));
    }

    @Test
    void TEXT_메시지를_저장한다() {
        ChatMessageSendRequest request = new ChatMessageSendRequest(
                "TEXT", "다들 몇 시에 모일까요?", null, CLIENT_MESSAGE_ID
        );

        chatMessageService.handleSend(STUDY_ID, SENDER_ID, request);

        verify(chatMessageWriter).saveText(
                eq(STUDY_ID), eq(SENDER_ID), eq("다들 몇 시에 모일까요?"),
                eq(CLIENT_MESSAGE_ID), any(ChatSenderResponse.class)
        );
        verifyNoInteractions(chatImageValidator);
    }

    @Test
    void 빈_TEXT_메시지는_CHAT_CONTENT_REQUIRED를_던진다() {
        ChatMessageSendRequest request = new ChatMessageSendRequest(
                "TEXT", "   ", null, CLIENT_MESSAGE_ID
        );

        assertThatThrownBy(() -> chatMessageService.handleSend(STUDY_ID, SENDER_ID, request))
                .isInstanceOf(BusinessException.class)
                .satisfies(exception -> assertThat(
                        ((BusinessException) exception).getErrorCode())
                        .isEqualTo(ErrorCode.CHAT_CONTENT_REQUIRED));

        verify(chatMessageWriter, never()).saveText(any(), any(), any(), any(), any());
    }

    @Test
    void 클라이언트가_SYSTEM_타입을_보내면_CHAT_MESSAGE_TYPE_INVALID를_던진다() {
        ChatMessageSendRequest request = new ChatMessageSendRequest(
                "SYSTEM", "시스템 메시지", null, CLIENT_MESSAGE_ID
        );

        assertThatThrownBy(() -> chatMessageService.handleSend(STUDY_ID, SENDER_ID, request))
                .isInstanceOf(BusinessException.class)
                .satisfies(exception -> assertThat(
                        ((BusinessException) exception).getErrorCode())
                        .isEqualTo(ErrorCode.CHAT_MESSAGE_TYPE_INVALID));

        verifyNoInteractions(chatMessageWriter);
    }

    @Test
    void 허용되지_않는_messageType은_CHAT_MESSAGE_TYPE_INVALID를_던진다() {
        ChatMessageSendRequest request = new ChatMessageSendRequest(
                "VOICE", null, null, CLIENT_MESSAGE_ID
        );

        assertThatThrownBy(() -> chatMessageService.handleSend(STUDY_ID, SENDER_ID, request))
                .isInstanceOf(BusinessException.class)
                .satisfies(exception -> assertThat(
                        ((BusinessException) exception).getErrorCode())
                        .isEqualTo(ErrorCode.CHAT_MESSAGE_TYPE_INVALID));
    }

    @Test
    void 동일한_clientMessageId가_이미_저장되어_있으면_재저장하지_않는다() {
        ChatMessage existing = ChatMessage.createText(STUDY_ID, SENDER_ID, "이미 저장됨", CLIENT_MESSAGE_ID);
        when(chatMessageRepository.findBySenderIdAndClientMessageId(SENDER_ID, CLIENT_MESSAGE_ID))
                .thenReturn(Optional.of(existing));
        ChatMessageSendRequest request = new ChatMessageSendRequest(
                "TEXT", "다들 몇 시에 모일까요?", null, CLIENT_MESSAGE_ID
        );

        chatMessageService.handleSend(STUDY_ID, SENDER_ID, request);

        verifyNoInteractions(chatMessageWriter);
        verifyNoInteractions(memberRepository);
    }

    @Test
    void IMAGE_메시지를_검증하고_저장한다() {
        FileMeta fileMeta = fileMeta();
        when(chatImageValidator.validate(STUDY_ID, SENDER_ID, 91L)).thenReturn(fileMeta);
        when(mediaPresignedUrlProvider.generateGetUrl(91L))
                .thenReturn("https://s3/presigned");
        ChatMessageSendRequest request = new ChatMessageSendRequest(
                "IMAGE", null, 91L, CLIENT_MESSAGE_ID
        );

        chatMessageService.handleSend(STUDY_ID, SENDER_ID, request);

        verify(chatMessageWriter).saveImage(
                eq(STUDY_ID), eq(SENDER_ID), eq(91L), eq(CLIENT_MESSAGE_ID),
                any(ChatSenderResponse.class), eq(new ChatImageResponse(91L, "https://s3/presigned", "COMPLETED"))
        );
    }

    @Test
    void IMAGE_URL_발급_중_미디어_검증_실패를_CHAT_IMAGE_INVALID로_변환한다() {
        FileMeta fileMeta = fileMeta();
        when(chatImageValidator.validate(
                STUDY_ID,
                SENDER_ID,
                91L
        )).thenReturn(fileMeta);
        when(mediaPresignedUrlProvider.generateGetUrl(91L))
                .thenThrow(new BusinessException(
                        ErrorCode.MEDIA_ACCESS_DENIED
                ));
        ChatMessageSendRequest request =
                new ChatMessageSendRequest(
                        "IMAGE",
                        null,
                        91L,
                        CLIENT_MESSAGE_ID
                );

        assertThatThrownBy(() -> chatMessageService.handleSend(
                STUDY_ID,
                SENDER_ID,
                request
        )).isInstanceOfSatisfying(
                BusinessException.class,
                exception -> assertThat(
                        exception.getErrorCode()
                ).isEqualTo(ErrorCode.CHAT_IMAGE_INVALID)
        );
        verify(chatMessageWriter, never())
                .saveImage(
                        any(),
                        any(),
                        any(),
                        any(),
                        any(),
                        any()
                );
    }

    @Test
    void 동시_중복_전송이_유일성_제약에_걸리면_재조회하여_무시한다() {
        ChatMessage existing = ChatMessage.createText(STUDY_ID, SENDER_ID, "이미 저장됨", CLIENT_MESSAGE_ID);
        DataIntegrityViolationException violation = new DataIntegrityViolationException("unique violation");
        when(chatMessageWriter.saveText(any(), any(), any(), any(), any())).thenThrow(violation);
        when(chatMessageRepository.findBySenderIdAndClientMessageId(SENDER_ID, CLIENT_MESSAGE_ID))
                .thenReturn(Optional.empty())
                .thenReturn(Optional.of(existing));
        ChatMessageSendRequest request = new ChatMessageSendRequest(
                "TEXT", "다들 몇 시에 모일까요?", null, CLIENT_MESSAGE_ID
        );

        chatMessageService.handleSend(STUDY_ID, SENDER_ID, request);

        verify(chatMessageRepository, times(2))
                .findBySenderIdAndClientMessageId(SENDER_ID, CLIENT_MESSAGE_ID);
        // writer.saveText는 실패한 최초 1회만 호출되고, 재조회 이후 재시도(재발행 시도)하지 않는다.
        // ChatMessageWriter 자체가 "저장 성공 시에만 이벤트 발행"을 보장하는지는
        // ChatMessageWriterTest에서 별도로 직접 검증한다.
        verify(chatMessageWriter, times(1)).saveText(any(), any(), any(), any(), any());
    }

    @Test
    void 유일성_제약_위반_후_재조회해도_없으면_원래_예외를_전파한다() {
        DataIntegrityViolationException violation = new DataIntegrityViolationException("unique violation");
        when(chatMessageWriter.saveText(any(), any(), any(), any(), any())).thenThrow(violation);
        when(chatMessageRepository.findBySenderIdAndClientMessageId(SENDER_ID, CLIENT_MESSAGE_ID))
                .thenReturn(Optional.empty());
        ChatMessageSendRequest request = new ChatMessageSendRequest(
                "TEXT", "다들 몇 시에 모일까요?", null, CLIENT_MESSAGE_ID
        );

        assertThatThrownBy(() -> chatMessageService.handleSend(STUDY_ID, SENDER_ID, request))
                .isSameAs(violation);
    }

    private Member member() {
        Member member = new Member("member@example.com", "hash", "루돌푸");
        ReflectionTestUtils.setField(member, "id", SENDER_ID);
        return member;
    }

    private FileMeta fileMeta() {
        FileMeta fileMeta = BeanUtils.instantiateClass(FileMeta.class);
        ReflectionTestUtils.setField(fileMeta, "id", 91L);
        ReflectionTestUtils.setField(fileMeta, "s3Key", "chat/91.jpg");
        ReflectionTestUtils.setField(fileMeta, "uploadStatus", UploadStatus.COMPLETED);
        return fileMeta;
    }
}
