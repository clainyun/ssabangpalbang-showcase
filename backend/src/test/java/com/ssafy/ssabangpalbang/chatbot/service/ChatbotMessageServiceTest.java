package com.ssafy.ssabangpalbang.chatbot.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.ssafy.ssabangpalbang.apartment.domain.Apartment;
import com.ssafy.ssabangpalbang.apartment.repository.ApartmentRepository;
import com.ssafy.ssabangpalbang.chatbot.domain.ChatbotConversation;
import com.ssafy.ssabangpalbang.chatbot.domain.ChatbotMessage;
import com.ssafy.ssabangpalbang.chatbot.domain.ChatbotMessageRole;
import com.ssafy.ssabangpalbang.chatbot.domain.ChatbotMessageStatus;
import com.ssafy.ssabangpalbang.chatbot.dto.response.ChatbotMessageListResponse;
import com.ssafy.ssabangpalbang.chatbot.repository.ChatbotConversationRepository;
import com.ssafy.ssabangpalbang.chatbot.repository.ChatbotMessageRepository;
import com.ssafy.ssabangpalbang.global.error.BusinessException;
import com.ssafy.ssabangpalbang.global.error.ErrorCode;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.beans.BeanUtils;
import org.springframework.data.domain.Pageable;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.Instant;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ChatbotMessageServiceTest {

    private static final Long APARTMENT_ID = 15L;
    private static final Long CONVERSATION_ID = 41L;
    private static final Long MEMBER_ID = 7L;
    private static final Instant NOW = Instant.parse("2026-07-25T07:05:00Z");

    @Mock
    private ChatbotConversationRepository conversationRepository;
    @Mock
    private ChatbotMessageRepository messageRepository;
    @Mock
    private ApartmentRepository apartmentRepository;

    private ChatbotMessageService service;

    @BeforeEach
    void setUp() {
        service = new ChatbotMessageService(
                conversationRepository,
                messageRepository,
                apartmentRepository,
                new ObjectMapper()
        );
    }

    @Test
    void P1_메시지가_size보다_적으면_다음_페이지가_없다() {
        givenPage(messages(1, 5), false, null);

        ChatbotMessageListResponse response = getMessages(null, 20);

        assertThat(response.content()).hasSize(5);
        assertThat(response.hasNext()).isFalse();
        assertThat(response.nextCursor()).isNull();
    }

    @Test
    void P2_size보다_한_건_더_조회하고_size개로_자른다() {
        givenPage(messages(1, 21), false, null);
        ArgumentCaptor<Pageable> pageableCaptor =
                ArgumentCaptor.forClass(Pageable.class);

        ChatbotMessageListResponse response = getMessages(null, 20);

        assertThat(response.content()).hasSize(20);
        assertThat(response.hasNext()).isTrue();
        assertThat(response.nextCursor()).isEqualTo(20L);
        verify(messageRepository).findPage(
                eq(CONVERSATION_ID),
                eq(null),
                pageableCaptor.capture()
        );
        assertThat(pageableCaptor.getValue().getPageSize()).isEqualTo(21);
    }

    @Test
    void P3_nextCursor_재조회는_첫_페이지와_겹치지_않는다() {
        givenConversationAndApartment();
        when(messageRepository.findPage(
                eq(CONVERSATION_ID),
                eq(null),
                any(Pageable.class)
        )).thenReturn(messages(1, 21));
        when(messageRepository.findPage(
                eq(CONVERSATION_ID),
                eq(20L),
                any(Pageable.class)
        )).thenReturn(messages(21, 25));
        when(messageRepository.existsInProgress(CONVERSATION_ID))
                .thenReturn(false);

        ChatbotMessageListResponse first = getMessages(null, 20);
        ChatbotMessageListResponse second = getMessages(
                first.nextCursor(),
                20
        );

        assertThat(second.content()).hasSize(5);
        assertThat(second.hasNext()).isFalse();
        assertThat(second.nextCursor()).isNull();
        Set<Long> firstIds = new HashSet<>(first.content().stream()
                .map(ChatbotMessageListResponse.MessageBody::messageId)
                .toList());
        assertThat(second.content())
                .extracting(ChatbotMessageListResponse.MessageBody::messageId)
                .noneMatch(firstIds::contains);
    }

    @Test
    void P4_메시지가_size와_같으면_다음_페이지가_없다() {
        givenPage(messages(1, 20), false, null);

        ChatbotMessageListResponse response = getMessages(null, 20);

        assertThat(response.content()).hasSize(20);
        assertThat(response.hasNext()).isFalse();
        assertThat(response.nextCursor()).isNull();
    }

    @Test
    void P5_cursor는_Repository에_전달되고_cursor보다_큰_ID만_반환한다() {
        givenPage(messages(6, 8), false, 5L);

        ChatbotMessageListResponse response = getMessages(5L, 20);

        verify(messageRepository).findPage(
                eq(CONVERSATION_ID),
                eq(5L),
                any(Pageable.class)
        );
        assertThat(response.content())
                .extracting(ChatbotMessageListResponse.MessageBody::messageId)
                .containsExactly(6L, 7L, 8L);
    }

    @Test
    void 이력이_없으면_빈_배열을_반환한다() {
        givenPage(List.of(), false, null);

        ChatbotMessageListResponse response = getMessages(null, 20);

        assertThat(response.content()).isEmpty();
        assertThat(response.hasNext()).isFalse();
    }

    @Test
    void 메시지는_ID_오름차순_조회_결과를_그대로_반환한다() {
        givenPage(messages(3, 5), false, null);

        ChatbotMessageListResponse response = getMessages(null, 20);

        assertThat(response.content())
                .extracting(ChatbotMessageListResponse.MessageBody::messageId)
                .containsExactly(3L, 4L, 5L);
    }

    @Test
    void 진행_중_메시지가_있으면_hasResponseInProgress가_true다() {
        givenPage(List.of(message(
                1L, ChatbotMessageRole.ASSISTANT,
                ChatbotMessageStatus.PENDING, null, null
        )), true, null);

        assertThat(getMessages(null, 20).hasResponseInProgress()).isTrue();
    }

    @Test
    void 모두_완료면_hasResponseInProgress가_false다() {
        givenPage(messages(1, 2), false, null);

        assertThat(getMessages(null, 20).hasResponseInProgress()).isFalse();
    }

    @Test
    void PROCESSING_AI_메시지는_null_content와_상태를_유지한다() {
        givenPage(List.of(message(
                1L, ChatbotMessageRole.ASSISTANT,
                ChatbotMessageStatus.PROCESSING, null, null
        )), true, null);

        ChatbotMessageListResponse.MessageBody body =
                getMessages(null, 20).content().get(0);

        assertThat(body.content()).isNull();
        assertThat(body.status()).isEqualTo("PROCESSING");
    }

    @Test
    void FAILED_메시지만_retryable이다() {
        ChatbotMessage failed = message(
                1L, ChatbotMessageRole.ASSISTANT,
                ChatbotMessageStatus.FAILED, null, null
        );
        ReflectionTestUtils.setField(failed, "failReason", "일시 오류");
        givenPage(List.of(failed), false, null);

        ChatbotMessageListResponse.MessageBody body =
                getMessages(null, 20).content().get(0);

        assertThat(body.failReason()).isEqualTo("일시 오류");
        assertThat(body.retryable()).isTrue();
    }

    @Test
    void COMPLETED_메시지는_retryable이_false다() {
        givenPage(messages(1, 1), false, null);

        assertThat(getMessages(null, 20).content().get(0).retryable())
                .isFalse();
    }

    @Test
    void basisType이_null이면_NONE으로_반환한다() {
        givenPage(List.of(message(
                1L, ChatbotMessageRole.USER,
                ChatbotMessageStatus.COMPLETED, "질문", null
        )), false, null);

        ChatbotMessageListResponse.MessageBody body =
                getMessages(null, 20).content().get(0);

        assertThat(body.basisType()).isEqualTo("NONE");
        assertThat(body.basisLabel()).isNull();
    }

    @Test
    void sourcesJson이_null이면_빈_배열이다() {
        givenPage(messages(1, 1), false, null);

        assertThat(getMessages(null, 20).content().get(0).sources())
                .isEmpty();
    }

    @Test
    void sourcesJson을_정본_필드로_역직렬화한다() {
        ChatbotMessage message = message(
                1L, ChatbotMessageRole.ASSISTANT,
                ChatbotMessageStatus.COMPLETED, "답변", "REPORT"
        );
        ReflectionTestUtils.setField(message, "sourcesJson", """
                [{"sourceType":"REPORT","sourceId":48,"reportId":48,
                  "title":"임장 리포트","sectionLabel":"교통","url":null}]
                """);
        givenPage(List.of(message), false, null);

        ChatbotMessageListResponse.SourceBody source =
                getMessages(null, 20).content().get(0).sources().get(0);

        assertThat(source.sourceType()).isEqualTo("REPORT");
        assertThat(source.sourceId()).isEqualTo(48L);
        assertThat(source.sectionLabel()).isEqualTo("교통");
    }

    @Test
    void 깨진_sourcesJson은_예외_없이_빈_배열로_대체한다() {
        ChatbotMessage message = message(
                1L, ChatbotMessageRole.ASSISTANT,
                ChatbotMessageStatus.COMPLETED, "답변", "REPORT"
        );
        ReflectionTestUtils.setField(message, "sourcesJson", "{broken");
        givenPage(List.of(message), false, null);

        assertThatCode(() -> getMessages(null, 20)).doesNotThrowAnyException();
        assertThat(getMessages(null, 20).content().get(0).sources()).isEmpty();
    }

    @Test
    void lastMessageAt은_대화의_값을_서울_시각으로_반환한다() {
        givenPage(List.of(), false, null);

        assertThat(getMessages(null, 20).lastMessageAt())
                .isEqualTo("2026-07-25T16:05+09:00");
    }

    @Test
    void 대화가_없으면_CHATBOT_CONVERSATION_NOT_FOUND다() {
        when(conversationRepository.findById(CONVERSATION_ID))
                .thenReturn(Optional.empty());

        assertBusinessException(
                ErrorCode.CHATBOT_CONVERSATION_NOT_FOUND,
                () -> getMessages(null, 20)
        );
    }

    @Test
    void 다른_회원의_대화면_ACCESS_DENIED다() {
        when(conversationRepository.findById(CONVERSATION_ID))
                .thenReturn(Optional.of(conversation(99L, APARTMENT_ID)));

        assertBusinessException(
                ErrorCode.CHATBOT_CONVERSATION_ACCESS_DENIED,
                () -> getMessages(null, 20)
        );
    }

    @Test
    void 다른_아파트_경로면_CHATBOT_APARTMENT_MISMATCH다() {
        when(conversationRepository.findById(CONVERSATION_ID))
                .thenReturn(Optional.of(conversation(MEMBER_ID, 99L)));

        assertBusinessException(
                ErrorCode.CHATBOT_APARTMENT_MISMATCH,
                () -> getMessages(null, 20)
        );
    }

    @Test
    void 남의_대화이면서_아파트도_다르면_소유권을_먼저_검증한다() {
        when(conversationRepository.findById(CONVERSATION_ID))
                .thenReturn(Optional.of(conversation(99L, 99L)));

        assertBusinessException(
                ErrorCode.CHATBOT_CONVERSATION_ACCESS_DENIED,
                () -> getMessages(null, 20)
        );
        verify(apartmentRepository, never()).findById(any());
    }

    @Test
    void 아파트가_없으면_APARTMENT_NOT_FOUND다() {
        when(conversationRepository.findById(CONVERSATION_ID))
                .thenReturn(Optional.of(conversation(MEMBER_ID, APARTMENT_ID)));
        when(apartmentRepository.findById(APARTMENT_ID))
                .thenReturn(Optional.empty());

        assertBusinessException(
                ErrorCode.APARTMENT_NOT_FOUND,
                () -> getMessages(null, 20)
        );
    }

    private ChatbotMessageListResponse getMessages(Long cursor, int size) {
        return service.getMessages(
                APARTMENT_ID,
                CONVERSATION_ID,
                MEMBER_ID,
                cursor,
                size
        );
    }

    private void givenPage(
            List<ChatbotMessage> messages,
            boolean inProgress,
            Long cursor
    ) {
        givenConversationAndApartment();
        when(messageRepository.findPage(
                eq(CONVERSATION_ID),
                eq(cursor),
                any(Pageable.class)
        )).thenReturn(messages);
        when(messageRepository.existsInProgress(CONVERSATION_ID))
                .thenReturn(inProgress);
    }

    private void givenConversationAndApartment() {
        when(conversationRepository.findById(CONVERSATION_ID))
                .thenReturn(Optional.of(conversation(
                        MEMBER_ID,
                        APARTMENT_ID
                )));
        when(apartmentRepository.findById(APARTMENT_ID))
                .thenReturn(Optional.of(apartment()));
    }

    private ChatbotConversation conversation(Long memberId, Long apartmentId) {
        ChatbotConversation conversation = ChatbotConversation.start(
                memberId,
                apartmentId
        );
        ReflectionTestUtils.setField(
                conversation,
                "id",
                CONVERSATION_ID
        );
        ReflectionTestUtils.setField(conversation, "lastMessageAt", NOW);
        return conversation;
    }

    private Apartment apartment() {
        Apartment apartment = Apartment.create(
                "A15", "래미안", "주소", "11200", "성동구", "옥수동",
                "1120011300", 127.0, 37.0, 1000, "2016-11", 1200
        );
        ReflectionTestUtils.setField(apartment, "id", APARTMENT_ID);
        return apartment;
    }

    private List<ChatbotMessage> messages(int startId, int endId) {
        List<ChatbotMessage> result = new ArrayList<>();
        for (long id = startId; id <= endId; id++) {
            result.add(message(
                    id,
                    ChatbotMessageRole.USER,
                    ChatbotMessageStatus.COMPLETED,
                    "질문 " + id,
                    null
            ));
        }
        return result;
    }

    private ChatbotMessage message(
            Long id,
            ChatbotMessageRole role,
            ChatbotMessageStatus status,
            String content,
            String basisType
    ) {
        ChatbotMessage message = BeanUtils.instantiateClass(
                ChatbotMessage.class
        );
        ReflectionTestUtils.setField(message, "id", id);
        ReflectionTestUtils.setField(
                message,
                "conversationId",
                CONVERSATION_ID
        );
        ReflectionTestUtils.setField(message, "role", role);
        ReflectionTestUtils.setField(message, "status", status);
        ReflectionTestUtils.setField(message, "content", content);
        ReflectionTestUtils.setField(message, "basisType", basisType);
        ReflectionTestUtils.setField(message, "createdAt", NOW.plusSeconds(id));
        if (status == ChatbotMessageStatus.COMPLETED) {
            ReflectionTestUtils.setField(
                    message,
                    "completedAt",
                    NOW.plusSeconds(id)
            );
        }
        return message;
    }

    private void assertBusinessException(
            ErrorCode errorCode,
            Runnable invocation
    ) {
        assertThatThrownBy(invocation::run)
                .isInstanceOfSatisfying(
                        BusinessException.class,
                        exception -> assertThat(exception.getErrorCode())
                                .isEqualTo(errorCode)
                );
    }
}
